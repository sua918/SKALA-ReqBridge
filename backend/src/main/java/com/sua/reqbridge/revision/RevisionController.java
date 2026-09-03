package com.sua.reqbridge.revision;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sua.reqbridge.analysis.Analysis;
import com.sua.reqbridge.contract.AnalysisKind;
import com.sua.reqbridge.contract.AnalysisStatus;
import com.sua.reqbridge.contract.RequirementSnapshot;
import com.sua.reqbridge.contract.RequirementStatus;
import com.sua.reqbridge.contract.RevisionSource;

import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api")
@ConditionalOnBean(RevisionWorkflowService.class)
public class RevisionController {

	private final RevisionWorkflowService service;
	private final ObjectMapper json;

	public RevisionController(RevisionWorkflowService service, ObjectMapper json) {
		this.service = service;
		this.json = json;
	}

	@PostMapping("/requirements/{requirementId}/revisions")
	public ResponseEntity<Data<AnalysisView>> submitRevision(
			@PathVariable long requirementId,
			@RequestBody RevisionRequest request) {
		Analysis analysis = service.submitRevision(requirementId, request.expectedContentVersion());
		Data<AnalysisView> body = new Data<>(analysisView(analysis));
		return ResponseEntity.accepted()
				.location(URI.create("/api/analyses/" + analysis.getId()))
				.body(body);
	}

	@PostMapping("/revisions/{revisionId}/review")
	public ResponseEntity<Data<ReviewResultView>> review(
			@PathVariable long revisionId,
			@RequestBody ReviewRequest request) {
		RevisionWorkflowService.ReviewResult result = service.review(
				revisionId, request.decision(), request.rejectionReason(), request.expectedContentVersion());
		return ResponseEntity.ok(new Data<>(new ReviewResultView(
				RevisionView.from(result.revision()),
				RequirementView.from(result.requirement()))));
	}

	@PostMapping("/requirements/{requirementId}/confirm")
	public ResponseEntity<Data<ConfirmResultView>> directConfirm(
			@PathVariable long requirementId,
			@RequestBody ConfirmRequest request) {
		RevisionWorkflowService.ReviewResult result = service.directConfirm(
				requirementId, request.expectedContentVersion());
		return ResponseEntity.ok(new Data<>(new ConfirmResultView(
				RequirementView.from(result.requirement()),
				RevisionView.from(result.revision()))));
	}

	private AnalysisView analysisView(Analysis value) {
		if (value == null) {
			return null;
		}
		return new AnalysisView(value.getId(), value.getKind(), value.getStatus(), value.getDocumentId(),
				value.getRequirementId(), value.getClarificationId(), value.getInputContentVersion(),
				value.getRetryOfAnalysisId(), value.getCreatedAt(), value.getStartedAt(), value.getCompletedAt(),
				value.getResult() == null ? null : json.readTree(value.getResult()),
				value.getErrorCode() == null ? null : new Failure(value.getErrorCode(), value.getErrorMessage()));
	}

	record RevisionRequest(long expectedContentVersion) {
		@JsonAnySetter
		void rejectUnknown(String name, Object value) {
			throw new IllegalArgumentException("정의되지 않은 필드입니다: " + name);
		}
	}

	record ReviewRequest(String decision, long expectedContentVersion, String rejectionReason) {
		@JsonAnySetter
		void rejectUnknown(String name, Object value) {
			throw new IllegalArgumentException("정의되지 않은 필드입니다: " + name);
		}
	}

	record ConfirmRequest(long expectedContentVersion) {
		@JsonAnySetter
		void rejectUnknown(String name, Object value) {
			throw new IllegalArgumentException("정의되지 않은 필드입니다: " + name);
		}
	}

	record Data<T>(T data) {
	}

	record Failure(String code, String message) {
	}

	record AnalysisView(long id, AnalysisKind kind, AnalysisStatus status, long documentId,
			Long requirementId, Long clarificationId, Long inputContentVersion, Long retryOfAnalysisId,
			Instant createdAt, Instant startedAt, Instant completedAt, Object result, Failure error) {
	}

	record RevisionView(long id, long requirementId, int revisionNo, String text,
			RevisionSource source, Object status,
			long inputContentVersion, List<Long> basedOnClarificationIds,
			String rejectionReason, List<Object> acceptanceCriteria) {
		static RevisionView from(RequirementRevision revision) {
			return new RevisionView(revision.getId(), revision.getRequirementId(), revision.getRevisionNo(),
					revision.getText(), revision.getSource(), revision.getStatus(), revision.getInputContentVersion(),
					List.copyOf(revision.getBasedOnClarificationIds()), revision.getRejectionReason(), List.of());
		}
	}

	record RequirementView(long id, long documentId, long analysisId, int sequenceNo,
			String originalText, RequirementStatus status, long contentVersion,
			Long approvedRevisionId, String confirmedText) {
		static RequirementView from(RequirementSnapshot snapshot) {
			return new RequirementView(snapshot.id(), snapshot.documentId(), snapshot.analysisId(),
					snapshot.sequenceNo(), snapshot.originalText(), snapshot.status(),
					snapshot.contentVersion(), snapshot.approvedRevisionId(), snapshot.confirmedText());
		}
	}

	record ReviewResultView(RevisionView revision, RequirementView requirement) {
	}

	record ConfirmResultView(RequirementView requirement, RevisionView revision) {
	}
}
