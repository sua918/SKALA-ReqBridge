package com.sua.reqbridge.clarification;

import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import com.sua.reqbridge.ambiguity.AmbiguityIssue;
import com.sua.reqbridge.ambiguity.AmbiguityIssueRepository;
import com.sua.reqbridge.analysis.Analysis;
import com.sua.reqbridge.analysis.AnalysisRepository;
import com.sua.reqbridge.analysis.MockWorkflowAnalyzer;
import com.sua.reqbridge.contract.AnalysisKind;
import com.sua.reqbridge.contract.AnalysisStatus;
import com.sua.reqbridge.contract.ClarificationStatus;
import com.sua.reqbridge.contract.CoreRequirementPort;
import com.sua.reqbridge.contract.IssueStatus;
import com.sua.reqbridge.contract.RequirementSnapshot;
import com.sua.reqbridge.contract.RequirementStatus;
import com.sua.reqbridge.contract.ResourceNotFoundException;
import com.sua.reqbridge.contract.StateConflictException;
import com.sua.reqbridge.revision.RequirementRevisionRepository;
import com.sua.reqbridge.revision.RequirementRevision;

import tools.jackson.databind.ObjectMapper;

public class AnswerWorkflowService {

	private static final String PROPOSED_TEXT = "시스템은 최대 동시 사용자 3,000명의 상품 조회 부하 시험을 10분간 수행할 때 "
			+ "p95 응답 시간 2초 이하, 성공 응답 비율 99.9% 이상을 만족해야 한다.";

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
	private final MockWorkflowAnalyzer analyzer;
	private final ObjectMapper json;

	public AnswerWorkflowService(AnalysisRepository analyses, AmbiguityIssueRepository issues,
			ClarificationRepository clarifications, RequirementRevisionRepository revisions,
			CoreRequirementPort core, ApplicationEventPublisher events,
			MockWorkflowAnalyzer analyzer, ObjectMapper json) {
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
	public AnswerReceipt submit(long clarificationId, String rawAnswer, long expectedVersion) {
		if (expectedVersion < 1 || expectedVersion > 9_007_199_254_740_991L) {
			throw new IllegalArgumentException("expectedContentVersion은 유효한 양수여야 합니다.");
		}
		String answer = normalize(rawAnswer);
		Clarification clarification = clarification(clarificationId);
		RequirementSnapshot requirement = core.lockRequirement(clarification.getRequirementId());
		var existing = analyses.findFirstByClarificationIdAndKindOrderByIdDesc(
				clarificationId, AnalysisKind.ANSWER);
		if (clarification.getAnswerText() != null) {
			if (clarification.getAnswerText().equals(answer) && existing.isPresent()) {
				return new AnswerReceipt(clarificationId, requirement.id(),
						requirement.contentVersion(), existing.get());
			}
			throw new StateConflictException("ANSWER_ALREADY_SUBMITTED", "이미 제출한 답변은 변경할 수 없습니다.");
		}
		if (requirement.status() == RequirementStatus.CONFIRMED) {
			throw new StateConflictException("REQUIREMENT_CONFIRMED", "확정된 요구사항은 변경할 수 없습니다.");
		}
		if (analyses.existsByRequirementIdAndStatusIn(requirement.id(), ACTIVE)) {
			throw new StateConflictException("ANALYSIS_IN_PROGRESS", "요구사항 분석이 진행 중입니다.");
		}
		if (requirement.contentVersion() != expectedVersion) {
			throw new StateConflictException("CONTENT_VERSION_CONFLICT", "요구사항 버전이 일치하지 않습니다.");
		}
		if (clarification.getStatus() != ClarificationStatus.WAITING) {
			throw new StateConflictException("STATE_CONFLICT", "WAITING 질문에만 답변할 수 있습니다.");
		}

		clarification.answer(answer);
		long version = core.advanceContentVersion(requirement.id(), expectedVersion);
		Analysis analysis = analyses.save(Analysis.pendingAnswer(requirement.documentId(), requirement.id(),
				clarificationId, version, json.writeValueAsString(
						new AnswerInput(clarificationId, clarification.getIssueId(), answer, version))));
		events.publishEvent(new AnswerAnalysisRequested(analysis.getId()));
		return new AnswerReceipt(clarificationId, requirement.id(), version, analysis);
	}

	@Transactional
	public void executeAnswer(long analysisId) {
		Analysis analysis = analyses.findById(analysisId)
				.orElseThrow(() -> new ResourceNotFoundException("분석 작업을 찾을 수 없습니다."));
		analysis.start(Instant.now());
		Clarification clarification = clarification(analysis.getClarificationId());
		AmbiguityIssue issue = issues.findById(clarification.getIssueId())
				.orElseThrow(() -> new ResourceNotFoundException("불명확성 문제를 찾을 수 없습니다."));
		MockWorkflowAnalyzer.Assessment assessed = analyzer.assess(clarification.getAnswerText());
		Long nextId = null;
		List<Long> revisionIds = List.of();
		if (assessed.sufficient()) {
			clarification.resolve();
			issue.resolve();
			if (issues.countByRequirementIdAndStatus(
					analysis.getRequirementId(), IssueStatus.OPEN) == 0) {
				int revisionNo = revisions.findTopByRequirementIdOrderByRevisionNoDesc(
						analysis.getRequirementId()).map(RequirementRevision::getRevisionNo).orElse(0) + 1;
				List<Long> evidenceIds = clarifications
						.findByRequirementIdOrderByIssueIdAscRoundNoAsc(analysis.getRequirementId()).stream()
						.filter(item -> item.getAnswerText() != null)
						.map(Clarification::getId)
						.toList();
				RequirementRevision revision = revisions.save(RequirementRevision.proposed(
						analysis.getRequirementId(), revisionNo, PROPOSED_TEXT,
						analysis.getInputContentVersion(), evidenceIds));
				core.changeStatus(analysis.getRequirementId(),
						analysis.getInputContentVersion(), RequirementStatus.IN_REVIEW);
				revisionIds = List.of(revision.getId());
			}
		}
		else {
			int nextRound = clarifications.findTopByIssueIdOrderByRoundNoDesc(issue.getId())
					.map(Clarification::getRoundNo).orElse(0) + 1;
			nextId = clarifications.save(Clarification.waiting(
					analysis.getRequirementId(), issue.getId(), nextRound, assessed.nextQuestionText())).getId();
		}
		Assessment result = new Assessment(issue.getId(), assessed.sufficient(), assessed.reason(), nextId);
		analysis.complete(json.writeValueAsString(new AnalysisResult(
				List.of(analysis.getRequirementId()), List.of(issue.getId()),
				nextId == null ? List.of() : List.of(nextId), revisionIds, result)), Instant.now());
	}

	@Transactional(readOnly = true)
	public Workflow workflow(long requirementId) {
		RequirementSnapshot requirement = core.getRequirement(requirementId);
		return new Workflow(requirement, analyses
				.findFirstByRequirementIdAndStatusInOrderByIdDesc(requirementId, ACTIVE).orElse(null),
				issues.findByRequirementIdOrderByIdAsc(requirementId),
				clarifications.findByRequirementIdOrderByIssueIdAscRoundNoAsc(requirementId),
				revisions.findByRequirementIdOrderByRevisionNoDesc(requirementId));
	}

	private Clarification clarification(long id) {
		return clarifications.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("확인 질문을 찾을 수 없습니다."));
	}

	private static String normalize(String value) {
		if (value == null) {
			throw new IllegalArgumentException("답변을 입력해주세요.");
		}
		if (value.codePointCount(0, value.length()) > 20_000) {
			throw new IllegalArgumentException("답변은 1~20000자여야 합니다.");
		}
		String normalized = EDGE_WHITESPACE_PATTERN.matcher(value.replace("\r\n", "\n")).replaceAll("");
		if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > 20_000) {
			throw new IllegalArgumentException("답변은 1~20000자여야 합니다.");
		}
		return normalized;
	}

	public record AnswerReceipt(long clarificationId, long requirementId,
			long contentVersion, Analysis analysis) {
	}

	public record Workflow(RequirementSnapshot requirement, Analysis activeAnalysis,
			List<AmbiguityIssue> issues, List<Clarification> clarifications,
			List<RequirementRevision> revisions) {
	}

	record AnswerInput(long clarificationId, long issueId, String answerText, long contentVersion) {
	}

	record Assessment(long issueId, boolean sufficient, String reason, Long nextClarificationId) {
	}

	record AnalysisResult(List<Long> requirementIds, List<Long> issueIds,
			List<Long> clarificationIds, List<Long> revisionIds, Assessment assessment) {
	}
}
