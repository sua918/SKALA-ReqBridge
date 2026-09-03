package com.sua.reqbridge.analysis;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import com.sua.reqbridge.ambiguity.AmbiguityIssue;
import com.sua.reqbridge.ambiguity.AmbiguityIssueRepository;
import com.sua.reqbridge.clarification.Clarification;
import com.sua.reqbridge.clarification.ClarificationRepository;
import com.sua.reqbridge.contract.AnalysisKind;
import com.sua.reqbridge.contract.AnalysisStatus;
import com.sua.reqbridge.contract.CoreRequirementPort;
import com.sua.reqbridge.contract.DocumentSnapshot;
import com.sua.reqbridge.contract.RequirementSeed;
import com.sua.reqbridge.contract.RequirementSnapshot;
import com.sua.reqbridge.contract.RequirementStatus;
import com.sua.reqbridge.contract.ResourceNotFoundException;
import com.sua.reqbridge.contract.StateConflictException;
import com.sua.reqbridge.contract.ai.WorkflowAnalyzer;
import com.sua.reqbridge.contract.ai.AnalyzerTypes.DocumentResult;
import com.sua.reqbridge.contract.ai.AnalyzerTypes.RequirementCandidate;
import com.sua.reqbridge.contract.ai.AnalyzerTypes.IssueCandidate;

import tools.jackson.databind.ObjectMapper;

import com.sua.reqbridge.clarification.AnswerAnalysisRequested;
import com.sua.reqbridge.revision.RevisionAnalysisRequested;

public class DocumentAnalysisService {

	private static final List<AnalysisStatus> ACTIVE = List.of(
			AnalysisStatus.PENDING, AnalysisStatus.PROCESSING);

	private final AnalysisRepository analyses;
	private final AmbiguityIssueRepository issues;
	private final ClarificationRepository clarifications;
	private final CoreRequirementPort core;
	private final ApplicationEventPublisher events;
	private final WorkflowAnalyzer analyzer;
	private final ObjectMapper json;

	public DocumentAnalysisService(AnalysisRepository analyses,
			AmbiguityIssueRepository issues,
			ClarificationRepository clarifications,
			CoreRequirementPort core,
			ApplicationEventPublisher events,
			WorkflowAnalyzer analyzer,
			ObjectMapper json) {
		this.analyses = analyses;
		this.issues = issues;
		this.clarifications = clarifications;
		this.core = core;
		this.events = events;
		this.analyzer = analyzer;
		this.json = json;
	}

	@Transactional
	public Analysis submit(long documentId) {
		DocumentSnapshot document = core.getDocument(documentId);
		if (analyses.existsByDocumentIdAndKindAndStatusIn(documentId, AnalysisKind.DOCUMENT, ACTIVE)) {
			throw new StateConflictException("ANALYSIS_IN_PROGRESS", "문서 분석이 진행 중입니다.");
		}
		if (analyses.existsByDocumentIdAndKindAndStatus(
				documentId, AnalysisKind.DOCUMENT, AnalysisStatus.COMPLETED)) {
			throw new StateConflictException("DOCUMENT_ALREADY_ANALYZED", "이미 분석을 완료한 문서입니다.");
		}
		Analysis saved = analyses.save(Analysis.pendingDocument(documentId,
				json.writeValueAsString(new DocumentInput(document.id(), document.sourceType(), document.content())),
				analyzer.adapterType(), analyzer.schemaVersion()));
		events.publishEvent(new DocumentAnalysisRequested(saved.getId()));
		return saved;
	}

	@Transactional
	public void executeDocument(long analysisId) {
		Analysis analysis = get(analysisId);
		AnalyzerOutputValidator.requireMatchingAdapter(analysis, analyzer);
		analysis.start(Instant.now());
		DocumentSnapshot document = core.getDocument(analysis.getDocumentId());
		DocumentInput input = json.readValue(analysis.getInputSnapshot(), DocumentInput.class);
		if (input.documentId() != document.id()) {
			throw new IllegalStateException("분석 입력의 문서 ID가 일치하지 않습니다.");
		}
		DocumentResult output = AnalyzerOutputValidator.document(analyzer.analyze(new DocumentSnapshot(
				document.id(), document.projectId(), document.title(), input.content(), input.sourceType())));
		List<RequirementSnapshot> created = core.createRequirements(document.id(), analysisId,
				output.requirements().stream()
						.map(item -> new RequirementSeed(item.sequenceNo(), item.originalText()))
						.toList());

		List<Long> requirementIds = new ArrayList<>();
		List<Long> issueIds = new ArrayList<>();
		List<Long> clarificationIds = new ArrayList<>();
		for (RequirementCandidate candidate : output.requirements()) {
			RequirementSnapshot requirement = created.stream()
					.filter(item -> item.sequenceNo() == candidate.sequenceNo())
					.findFirst()
					.orElseThrow(() -> new AiOutputInvalidException("분석 결과와 생성 요구사항이 일치하지 않습니다."));
			requirementIds.add(requirement.id());
			if (!candidate.issues().isEmpty()) {
				core.changeStatus(requirement.id(), requirement.contentVersion(), RequirementStatus.AMBIGUOUS);
			}
			for (IssueCandidate candidateIssue : candidate.issues()) {
				AmbiguityIssue issue = issues.save(AmbiguityIssue.open(
						requirement.id(), candidateIssue.type(), candidateIssue.evidence()));
				Clarification clarification = clarifications.save(Clarification.waiting(
						requirement.id(), issue.getId(), 1, candidateIssue.questionText()));
				issueIds.add(issue.getId());
				clarificationIds.add(clarification.getId());
			}
			if (!candidate.issues().isEmpty()) {
				core.changeStatus(requirement.id(), requirement.contentVersion(), RequirementStatus.CLARIFYING);
			}
		}

		analysis.complete(json.writeValueAsString(new AnalysisResult(
				requirementIds, issueIds, clarificationIds, List.of(), null)), Instant.now());
	}

	@Transactional
	public void fail(long analysisId, String code, String message) {
		get(analysisId).fail(code, message, Instant.now());
	}

	@Transactional(readOnly = true)
	public Analysis get(long analysisId) {
		return analyses.findById(analysisId)
				.orElseThrow(() -> new ResourceNotFoundException("분석 작업을 찾을 수 없습니다."));
	}

	@Transactional(readOnly = true)
	public List<Analysis> list(long documentId, AnalysisKind kind) {
		core.getDocument(documentId);
		return kind == null
				? analyses.findByDocumentIdOrderByIdDesc(documentId)
				: analyses.findByDocumentIdAndKindOrderByIdDesc(documentId, kind);
	}

	@Transactional
	public Analysis retry(long analysisId) {
		Analysis original = get(analysisId);
		if (original.getStatus() != AnalysisStatus.FAILED) {
			throw new StateConflictException("ANALYSIS_NOT_RETRYABLE", "실패한 작업만 재시도할 수 있습니다.");
		}
		var existingRetry = analyses.findFirstByRetryOfAnalysisIdOrderByIdDesc(analysisId);
		if (existingRetry.isPresent()) {
			return existingRetry.get();
		}

		if (original.getRequirementId() != null) {
			RequirementSnapshot requirement = core.lockRequirement(original.getRequirementId());
			if (requirement.status() == RequirementStatus.CONFIRMED) {
				throw new StateConflictException("REQUIREMENT_CONFIRMED", "확정된 요구사항의 작업은 재시도할 수 없습니다.");
			}
			if (analyses.existsByRequirementIdAndStatusIn(requirement.id(), ACTIVE)) {
				throw new StateConflictException("ANALYSIS_IN_PROGRESS", "요구사항 분석이 진행 중입니다.");
			}
			if (original.getInputContentVersion() != null
					&& requirement.contentVersion() != original.getInputContentVersion()) {
				throw new StateConflictException("CONTENT_VERSION_CONFLICT", "요구사항 버전이 일치하지 않습니다.");
			}
		}
		else {
			if (analyses.existsByDocumentIdAndKindAndStatusIn(original.getDocumentId(), AnalysisKind.DOCUMENT, ACTIVE)) {
				throw new StateConflictException("ANALYSIS_IN_PROGRESS", "문서 분석이 진행 중입니다.");
			}
			if (analyses.existsByDocumentIdAndKindAndStatus(
					original.getDocumentId(), AnalysisKind.DOCUMENT, AnalysisStatus.COMPLETED)) {
				throw new StateConflictException("DOCUMENT_ALREADY_ANALYZED", "이미 분석을 완료한 문서입니다.");
			}
		}

		Analysis retryAnalysis = analyses.save(Analysis.retry(original));
		switch (retryAnalysis.getKind()) {
			case DOCUMENT -> events.publishEvent(new DocumentAnalysisRequested(retryAnalysis.getId()));
			case ANSWER -> events.publishEvent(new AnswerAnalysisRequested(retryAnalysis.getId()));
			case REVISION -> events.publishEvent(new RevisionAnalysisRequested(retryAnalysis.getId()));
		}
		return retryAnalysis;
	}

	record DocumentInput(long documentId, String sourceType, String content) {
	}

	record AnalysisResult(List<Long> requirementIds, List<Long> issueIds,
			List<Long> clarificationIds, List<Long> revisionIds, Object assessment) {
	}
}

record DocumentAnalysisRequested(long analysisId) {
}
