package com.sua.reqbridge.revision;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import com.sua.reqbridge.ambiguity.AmbiguityIssueRepository;
import com.sua.reqbridge.analysis.Analysis;
import com.sua.reqbridge.analysis.AnalysisRepository;
import com.sua.reqbridge.analysis.AnalyzerInputs;
import com.sua.reqbridge.analysis.AnalyzerOutputValidator;
import com.sua.reqbridge.contract.ai.WorkflowAnalyzer;
import com.sua.reqbridge.contract.ai.AnalyzerTypes.RevisionGenerationInput;
import com.sua.reqbridge.clarification.ClarificationRepository;
import com.sua.reqbridge.contract.AnalysisStatus;
import com.sua.reqbridge.contract.CoreRequirementPort;
import com.sua.reqbridge.contract.IssueStatus;
import com.sua.reqbridge.contract.RequirementSnapshot;
import com.sua.reqbridge.contract.RequirementStatus;
import com.sua.reqbridge.contract.ResourceNotFoundException;
import com.sua.reqbridge.contract.RevisionStatus;
import com.sua.reqbridge.contract.StateConflictException;

import tools.jackson.databind.ObjectMapper;

public class RevisionWorkflowService {

	private static final List<AnalysisStatus> ACTIVE = List.of(
			AnalysisStatus.PENDING, AnalysisStatus.PROCESSING);
	private static final String EDGE_WHITESPACE = "[\\x{0009}-\\x{000D}\\x{0020}\\x{0085}\\x{00A0}"
			+ "\\x{1680}\\x{2000}-\\x{200A}\\x{2028}\\x{2029}\\x{202F}\\x{205F}\\x{3000}\\x{FEFF}]";
	private static final Pattern EDGE_WHITESPACE_PATTERN = Pattern.compile(
			"^" + EDGE_WHITESPACE + "+|" + EDGE_WHITESPACE + "+$");

	private final AnalysisRepository analyses;
	private final AmbiguityIssueRepository issues;
	private final ClarificationRepository clarifications;
	private final RequirementRevisionRepository revisions;
	private final CoreRequirementPort core;
	private final ApplicationEventPublisher events;
	private final WorkflowAnalyzer analyzer;
	private final ObjectMapper json;

	public RevisionWorkflowService(AnalysisRepository analyses, AmbiguityIssueRepository issues,
			ClarificationRepository clarifications, RequirementRevisionRepository revisions,
			CoreRequirementPort core, ApplicationEventPublisher events, WorkflowAnalyzer analyzer, ObjectMapper json) {
		this.analyses = analyses;
		this.issues = issues;
		this.clarifications = clarifications;
		this.revisions = revisions;
		this.core = core;
		this.events = events;
		this.analyzer = analyzer;
		this.json = json;
	}

	@Transactional
	public Analysis submitRevision(long requirementId, long expectedVersion) {
		if (expectedVersion < 1 || expectedVersion > 9_007_199_254_740_991L) {
			throw new IllegalArgumentException("expectedContentVersion은 유효한 양수여야 합니다.");
		}
		RequirementSnapshot requirement = core.lockRequirement(requirementId);
		if (requirement.status() == RequirementStatus.CONFIRMED) {
			throw new StateConflictException("REQUIREMENT_CONFIRMED", "확정된 요구사항은 변경할 수 없습니다.");
		}
		if (requirement.status() != RequirementStatus.CLARIFYING) {
			throw new StateConflictException("STATE_CONFLICT", "CLARIFYING 상태에서만 수정안을 재생성할 수 있습니다.");
		}
		if (issues.countByRequirementIdAndStatus(requirementId, IssueStatus.OPEN) > 0) {
			throw new StateConflictException("STATE_CONFLICT", "미해결된 불명확성 문제가 남아 있습니다.");
		}
		if (analyses.existsByRequirementIdAndStatusIn(requirementId, ACTIVE)) {
			throw new StateConflictException("ANALYSIS_IN_PROGRESS", "요구사항 분석이 진행 중입니다.");
		}
		if (revisions.existsByRequirementIdAndStatus(requirementId, RevisionStatus.PROPOSED)) {
			throw new StateConflictException("STATE_CONFLICT", "이미 검토 대기 중인 수정안이 존재합니다.");
		}
		if (!revisions.existsByRequirementIdAndStatus(requirementId, RevisionStatus.REJECTED)) {
			throw new StateConflictException("STATE_CONFLICT", "거절된 수정안이 있어야 재생성할 수 있습니다.");
		}
		if (requirement.contentVersion() != expectedVersion) {
			throw new StateConflictException("CONTENT_VERSION_CONFLICT", "요구사항 버전이 일치하지 않습니다.");
		}

		List<RequirementRevision> revisionList = revisions.findByRequirementIdOrderByRevisionNoDesc(requirementId);
		String latestRejectionReason = revisionList.stream()
				.filter(r -> r.getStatus() == RevisionStatus.REJECTED)
				.map(RequirementRevision::getRejectionReason)
				.findFirst()
				.orElse(null);

		String inputSnapshot = json.writeValueAsString(new RevisionInput(
				requirementId, requirement.documentId(), expectedVersion, latestRejectionReason));

		Analysis analysis = analyses.save(Analysis.pendingRevision(
				requirement.documentId(), requirementId, expectedVersion, inputSnapshot,
				analyzer.adapterType(), analyzer.schemaVersion()));
		events.publishEvent(new RevisionAnalysisRequested(analysis.getId()));
		return analysis;
	}

	@Transactional
	public void executeRevision(long analysisId) {
		Analysis analysis = analyses.findById(analysisId)
				.orElseThrow(() -> new ResourceNotFoundException("분석 작업을 찾을 수 없습니다."));
		AnalyzerOutputValidator.requireMatchingAdapter(analysis, analyzer);
		analysis.start(Instant.now());
		long requirementId = analysis.getRequirementId();
		RequirementSnapshot requirement = core.lockRequirement(requirementId);
		AnalyzerInputs.requireCurrentVersion(analysis, requirement);
		RevisionInput input = json.readValue(analysis.getInputSnapshot(), RevisionInput.class);
		if (input.requirementId() != requirementId || input.documentId() != requirement.documentId()
				|| input.contentVersion() != requirement.contentVersion()) {
			throw new IllegalStateException("수정안 입력의 요구사항 또는 버전이 일치하지 않습니다.");
		}
		int revisionNo = revisions.findTopByRequirementIdOrderByRevisionNoDesc(requirementId)
				.map(RequirementRevision::getRevisionNo).orElse(0) + 1;
		var answers = AnalyzerInputs.answers(
				clarifications.findByRequirementIdOrderByIssueIdAscRoundNoAsc(requirementId));
		String previousText = revisions.findByRequirementIdOrderByRevisionNoDesc(requirementId).stream()
				.filter(item -> item.getStatus() == RevisionStatus.REJECTED)
				.map(RequirementRevision::getText).findFirst().orElse(null);
		var proposal = AnalyzerOutputValidator.revision(analyzer.generateRevision(new RevisionGenerationInput(
				requirementId, requirement.originalText(), requirement.contentVersion(),
				answers, previousText, input.rejectionReason())));
		List<Long> evidenceIds = answers.stream().map(item -> item.clarificationId()).toList();
		RequirementRevision revision = revisions.save(RequirementRevision.proposed(
				requirementId, revisionNo, proposal.text(), analysis.getInputContentVersion(), evidenceIds));
		core.changeStatus(requirementId, analysis.getInputContentVersion(), RequirementStatus.IN_REVIEW);
		analysis.complete(json.writeValueAsString(new AnalysisResult(
				List.of(requirementId), List.of(), List.of(), List.of(revision.getId()), null)), Instant.now());
	}

	@Transactional
	public ReviewResult review(long revisionId, String decision, String rejectionReason, long expectedVersion) {
		if (expectedVersion < 1 || expectedVersion > 9_007_199_254_740_991L) {
			throw new IllegalArgumentException("expectedContentVersion은 유효한 양수여야 합니다.");
		}
		if (decision == null || (!decision.equals("APPROVE") && !decision.equals("REJECT"))) {
			throw new IllegalArgumentException("decision은 APPROVE 또는 REJECT여야 합니다.");
		}
		String normalizedReason = null;
		if (decision.equals("REJECT")) {
			normalizedReason = normalize(rejectionReason);
		}
		else {
			if (rejectionReason != null && !rejectionReason.isBlank()) {
				throw new IllegalArgumentException("승인 시에는 거절 사유를 입력할 수 없습니다.");
			}
		}

		RequirementRevision revision = revisions.findById(revisionId)
				.orElseThrow(() -> new ResourceNotFoundException("수정안을 찾을 수 없습니다."));
		RequirementSnapshot requirement = core.lockRequirement(revision.getRequirementId());

		// 멱등성 / 기검토 처리
		if (revision.getStatus() == RevisionStatus.APPROVED) {
			if (decision.equals("APPROVE")) {
				return new ReviewResult(revision, requirement);
			}
			throw new StateConflictException("REVISION_ALREADY_REVIEWED", "이미 승인된 수정안은 거절할 수 없습니다.");
		}
		if (revision.getStatus() == RevisionStatus.REJECTED) {
			if (decision.equals("REJECT") && Objects.equals(revision.getRejectionReason(), normalizedReason)) {
				return new ReviewResult(revision, requirement);
			}
			throw new StateConflictException("REVISION_ALREADY_REVIEWED", "이미 거절된 수정안의 결정을 번복하거나 사유를 변경할 수 없습니다.");
		}

		if (revision.getStatus() != RevisionStatus.PROPOSED) {
			throw new StateConflictException("STATE_CONFLICT", "PROPOSED 상태의 수정안만 검토할 수 있습니다.");
		}
		if (requirement.status() == RequirementStatus.CONFIRMED) {
			throw new StateConflictException("REQUIREMENT_CONFIRMED", "확정된 요구사항은 변경할 수 없습니다.");
		}
		if (issues.countByRequirementIdAndStatus(requirement.id(), IssueStatus.OPEN) > 0) {
			throw new StateConflictException("STATE_CONFLICT", "미해결된 불명확성 문제가 남아 있습니다.");
		}
		if (analyses.existsByRequirementIdAndStatusIn(requirement.id(), ACTIVE)) {
			throw new StateConflictException("ANALYSIS_IN_PROGRESS", "요구사항 분석이 진행 중입니다.");
		}
		if (requirement.contentVersion() != expectedVersion) {
			throw new StateConflictException("CONTENT_VERSION_CONFLICT", "요구사항 버전이 일치하지 않습니다.");
		}

		if (decision.equals("APPROVE")) {
			revision.approve();
			core.confirmRequirement(requirement.id(), expectedVersion, revision.getId(), revision.getText());
			RequirementSnapshot updated = core.getRequirement(requirement.id());
			return new ReviewResult(revision, updated);
		}
		else {
			revision.reject(normalizedReason);
			long newVersion = core.advanceContentVersion(requirement.id(), expectedVersion);
			core.changeStatus(requirement.id(), newVersion, RequirementStatus.CLARIFYING);
			RequirementSnapshot updated = core.getRequirement(requirement.id());
			return new ReviewResult(revision, updated);
		}
	}

	private static String normalize(String value) {
		if (value == null) {
			throw new IllegalArgumentException("거절 사유를 입력해주세요.");
		}
		if (value.codePointCount(0, value.length()) > 20_000) {
			throw new IllegalArgumentException("거절 사유는 1~20000자여야 합니다.");
		}
		String normalized = EDGE_WHITESPACE_PATTERN.matcher(value.replace("\r\n", "\n")).replaceAll("");
		if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > 20_000) {
			throw new IllegalArgumentException("거절 사유는 1~20000자여야 합니다.");
		}
		return normalized;
	}

	public record ReviewResult(RequirementRevision revision, RequirementSnapshot requirement) {
	}

	record RevisionInput(long requirementId, long documentId, long contentVersion, String rejectionReason) {
	}

	record AnalysisResult(List<Long> requirementIds, List<Long> issueIds,
			List<Long> clarificationIds, List<Long> revisionIds, Object assessment) {
	}
}
