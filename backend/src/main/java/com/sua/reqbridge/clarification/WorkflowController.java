package com.sua.reqbridge.clarification;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sua.reqbridge.ambiguity.AmbiguityIssue;
import com.sua.reqbridge.analysis.Analysis;
import com.sua.reqbridge.contract.AnalysisKind;
import com.sua.reqbridge.contract.AnalysisStatus;
import com.sua.reqbridge.contract.RequirementStatus;
import com.sua.reqbridge.revision.RequirementRevision;

import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api")
@ConditionalOnBean(AnswerWorkflowService.class)
public class WorkflowController {

	private final AnswerWorkflowService service;
	private final ObjectMapper json;

	public WorkflowController(AnswerWorkflowService service, ObjectMapper json) {
		this.service = service;
		this.json = json;
	}

	@GetMapping("/requirements/{requirementId}/workflow")
	public Data<WorkflowView> workflow(@PathVariable long requirementId) {
		var workflow = service.workflow(requirementId);
		return new Data<>(new WorkflowView(requirementId, workflow.requirement().status(),
				workflow.requirement().contentVersion(), analysis(workflow.activeAnalysis()),
				workflow.issues().stream().map(IssueView::from).toList(),
				workflow.clarifications().stream().map(ClarificationView::from).toList(),
				workflow.revisions().stream().map(RevisionView::from).toList()));
	}

	@PostMapping("/clarifications/{clarificationId}/answers")
	public ResponseEntity<Data<AnswerReceiptView>> answer(@PathVariable long clarificationId,
			@RequestBody AnswerRequest request) {
		var receipt = service.submit(clarificationId, request.answerText(), request.expectedContentVersion());
		Data<AnswerReceiptView> body = new Data<>(new AnswerReceiptView(receipt.clarificationId(),
				receipt.requirementId(), receipt.contentVersion(), analysis(receipt.analysis())));
		if (receipt.analysis().getStatus() == AnalysisStatus.PENDING
				|| receipt.analysis().getStatus() == AnalysisStatus.PROCESSING) {
			return ResponseEntity.accepted()
					.location(URI.create("/api/analyses/" + receipt.analysis().getId())).body(body);
		}
		return ResponseEntity.ok(body);
	}

	private AnalysisView analysis(Analysis value) {
		if (value == null) {
			return null;
		}
		return new AnalysisView(value.getId(), value.getKind(), value.getStatus(), value.getDocumentId(),
				value.getRequirementId(), value.getClarificationId(), value.getInputContentVersion(),
				value.getRetryOfAnalysisId(), value.getCreatedAt(), value.getStartedAt(), value.getCompletedAt(),
				value.getResult() == null ? null : json.readTree(value.getResult()),
				value.getErrorCode() == null ? null : new Failure(value.getErrorCode(), value.getErrorMessage()));
	}

	record AnswerRequest(String answerText, long expectedContentVersion) {
		@JsonAnySetter
		void rejectUnknown(String name, Object value) {
			throw new IllegalArgumentException("정의되지 않은 필드입니다: " + name);
		}
	}

	record Data<T>(T data) {
	}

	record AnswerReceiptView(long clarificationId, long requirementId,
			long contentVersion, AnalysisView analysis) {
	}

	record WorkflowView(long requirementId, RequirementStatus status, long contentVersion,
			AnalysisView activeAnalysis, List<IssueView> issues,
			List<ClarificationView> clarifications, List<RevisionView> revisions) {
	}

	record IssueView(long id, long requirementId, Object type, String evidence, Object status) {
		static IssueView from(AmbiguityIssue issue) {
			return new IssueView(issue.getId(), issue.getRequirementId(), issue.getType(),
					issue.getEvidence(), issue.getStatus());
		}
	}

	record ClarificationView(long id, long requirementId, long issueId, int roundNo,
			String questionText, String answerText, Object status) {
		static ClarificationView from(Clarification clarification) {
			return new ClarificationView(clarification.getId(), clarification.getRequirementId(),
					clarification.getIssueId(), clarification.getRoundNo(), clarification.getQuestionText(),
					clarification.getAnswerText(), clarification.getStatus());
		}
	}

	record RevisionView(long id, long requirementId, int revisionNo, String text, Object status,
			long inputContentVersion, List<Long> basedOnClarificationIds,
			String rejectionReason, List<Object> acceptanceCriteria) {
		static RevisionView from(RequirementRevision revision) {
			return new RevisionView(revision.getId(), revision.getRequirementId(), revision.getRevisionNo(),
					revision.getText(), revision.getStatus(), revision.getInputContentVersion(),
					List.copyOf(revision.getBasedOnClarificationIds()), revision.getRejectionReason(), List.of());
		}
	}

	record Failure(String code, String message) {
	}

	record AnalysisView(long id, AnalysisKind kind, AnalysisStatus status, long documentId,
			Long requirementId, Long clarificationId, Long inputContentVersion, Long retryOfAnalysisId,
			Instant createdAt, Instant startedAt, Instant completedAt, Object result, Failure error) {
	}
}
