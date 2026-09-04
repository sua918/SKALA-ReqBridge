package com.sua.reqbridge.report;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sua.reqbridge.contract.AmbiguityType;
import com.sua.reqbridge.contract.ApprovedRevisionSnapshot;
import com.sua.reqbridge.contract.ClarificationStatus;
import com.sua.reqbridge.contract.CoreRequirementPort;
import com.sua.reqbridge.contract.DocumentSnapshot;
import com.sua.reqbridge.contract.IssueSnapshot;
import com.sua.reqbridge.contract.IssueStatus;
import com.sua.reqbridge.contract.QuestionSnapshot;
import com.sua.reqbridge.contract.RequirementSnapshot;
import com.sua.reqbridge.contract.RequirementStatus;
import com.sua.reqbridge.contract.StateConflictException;
import com.sua.reqbridge.contract.WorkflowPreviewPort;
import com.sua.reqbridge.contract.WorkflowPreviewSnapshot;
import com.sua.reqbridge.contract.WorkflowRequirementSnapshot;

@Service
public class ReportService {

	private final CoreRequirementPort core;
	private final WorkflowPreviewPort workflowPreviewPort;

	public ReportService(CoreRequirementPort core, WorkflowPreviewPort workflowPreviewPort) {
		this.core = core;
		this.workflowPreviewPort = workflowPreviewPort;
	}

	@Transactional(readOnly = true)
	public CustomerPreview getCustomerPreview(long documentId) {
		DocumentSnapshot document = core.getDocument(documentId);
		List<RequirementSnapshot> sortedRequirements = core.listRequirements(documentId).stream()
				.sorted(Comparator.comparingInt(RequirementSnapshot::sequenceNo))
				.toList();

		WorkflowPreviewSnapshot workflow = workflowPreviewPort.getPreview(documentId);
		Map<Long, WorkflowRequirementSnapshot> workflowMap = workflow.requirements().stream()
				.collect(Collectors.toMap(WorkflowRequirementSnapshot::requirementId, w -> w));

		PreviewSummary summary = computeSummary(sortedRequirements, workflow);
		List<PreviewBasis> basis = sortedRequirements.stream()
				.map(r -> new PreviewBasis(
						r.id(), r.sequenceNo(), r.contentVersion(), r.approvedRevisionId()))
				.toList();

		List<CustomerRequirement> customerReqs = new ArrayList<>();
		for (RequirementSnapshot req : sortedRequirements) {
			WorkflowRequirementSnapshot wfReq = workflowMap.get(req.id());
			if (wfReq == null) {
				continue;
			}

			Map<Long, IssueSnapshot> openIssueMap = wfReq.issues().stream()
					.filter(i -> i.status() == IssueStatus.OPEN)
					.collect(Collectors.toMap(IssueSnapshot::id, i -> i));

			List<CustomerQuestion> waitingQuestions = wfReq.questions().stream()
					.filter(q -> q.status() == ClarificationStatus.WAITING && openIssueMap.containsKey(q.issueId()))
					.sorted(Comparator.comparingLong(QuestionSnapshot::issueId)
							.thenComparingInt(QuestionSnapshot::roundNo))
					.map(q -> {
						IssueSnapshot issue = openIssueMap.get(q.issueId());
						return new CustomerQuestion(
								q.id(), q.issueId(), issue.type(), issue.evidence(),
								q.roundNo(), q.questionText());
					})
					.toList();

			if (!waitingQuestions.isEmpty()) {
				customerReqs.add(new CustomerRequirement(
						req.id(), req.sequenceNo(), req.originalText(),
						req.contentVersion(), waitingQuestions));
			}
		}

		return new CustomerPreview(document.id(), document.title(), Instant.now(), summary, basis, customerReqs);
	}

	@Transactional(readOnly = true)
	public DeveloperPreview getDeveloperPreview(long documentId) {
		DocumentSnapshot document = core.getDocument(documentId);
		List<RequirementSnapshot> sortedRequirements = core.listRequirements(documentId).stream()
				.sorted(Comparator.comparingInt(RequirementSnapshot::sequenceNo))
				.toList();

		WorkflowPreviewSnapshot workflow = workflowPreviewPort.getPreview(documentId);
		Map<Long, WorkflowRequirementSnapshot> workflowMap = workflow.requirements().stream()
				.collect(Collectors.toMap(WorkflowRequirementSnapshot::requirementId, w -> w));

		PreviewSummary summary = computeSummary(sortedRequirements, workflow);
		List<PreviewBasis> basis = sortedRequirements.stream()
				.map(r -> new PreviewBasis(
						r.id(), r.sequenceNo(), r.contentVersion(), r.approvedRevisionId()))
				.toList();

		List<ConfirmedRequirement> confirmedList = new ArrayList<>();
		List<UnconfirmedRequirement> unconfirmedList = new ArrayList<>();

		for (RequirementSnapshot req : sortedRequirements) {
			WorkflowRequirementSnapshot wfReq = workflowMap.get(req.id());

			if (req.status() == RequirementStatus.CONFIRMED) {
				if (req.approvedRevisionId() == null || wfReq == null || wfReq.approvedRevision() == null) {
					throw new StateConflictException("PREVIEW_VERSION_CONFLICT", "확정된 요구사항의 승인 수정안 정보를 찾을 수 없습니다.");
				}
				ApprovedRevisionSnapshot approvedRev = wfReq.approvedRevision();
				if (approvedRev.id() != req.approvedRevisionId()) {
					throw new StateConflictException("PREVIEW_VERSION_CONFLICT", "확정된 요구사항의 승인 수정안 ID가 일치하지 않습니다.");
				}
				if (!Objects.equals(approvedRev.text(), req.confirmedText())) {
					throw new StateConflictException("PREVIEW_VERSION_CONFLICT", "확정된 요구사항의 본문과 수정안 텍스트가 일치하지 않습니다.");
				}

				RevisionDetail revisionDetail = new RevisionDetail(
						approvedRev.id(), req.id(), approvedRev.revisionNo(),
						approvedRev.text(), "APPROVED", req.contentVersion(),
						approvedRev.basedOnClarificationIds(), null, List.of());

				List<EvidenceClarification> evidenceAnswers = wfReq.questions().stream()
						.filter(q -> approvedRev.basedOnClarificationIds().contains(q.id()))
						.sorted(Comparator.comparingLong(QuestionSnapshot::issueId)
								.thenComparingInt(QuestionSnapshot::roundNo))
						.map(q -> new EvidenceClarification(
								q.id(), q.requirementId(), q.issueId(), q.roundNo(),
								q.questionText(), q.answerText(), q.status()))
						.toList();

				confirmedList.add(new ConfirmedRequirement(
						req.id(), req.sequenceNo(), req.originalText(),
						req.contentVersion(), revisionDetail, evidenceAnswers));
			}
			else {
				List<IssueDetail> issueDetails = wfReq == null ? List.of() : wfReq.issues().stream()
						.sorted(Comparator.comparingLong(IssueSnapshot::id))
						.map(i -> new IssueDetail(i.id(), req.id(), i.type(), i.evidence(), i.status()))
						.toList();

				List<ClarificationDetail> clarificationDetails = wfReq == null ? List.of() : wfReq.questions().stream()
						.sorted(Comparator.comparingLong(QuestionSnapshot::issueId)
								.thenComparingInt(QuestionSnapshot::roundNo))
						.map(q -> new ClarificationDetail(
								q.id(), q.requirementId(), q.issueId(), q.roundNo(),
								q.questionText(), q.answerText(), q.status()))
						.toList();

				unconfirmedList.add(new UnconfirmedRequirement(
						req.id(), req.sequenceNo(), req.originalText(),
						req.status(), req.contentVersion(), issueDetails, clarificationDetails));
			}
		}

		return new DeveloperPreview(
				document.id(), document.title(), Instant.now(),
				summary, basis, confirmedList, unconfirmedList);
	}

	private static PreviewSummary computeSummary(
			List<RequirementSnapshot> requirements, WorkflowPreviewSnapshot workflow) {
		int totalRequirements = requirements.size();
		int confirmedRequirements = (int) requirements.stream()
				.filter(r -> r.status() == RequirementStatus.CONFIRMED)
				.count();

		int openIssueCount = (int) workflow.requirements().stream()
				.flatMap(w -> w.issues().stream())
				.filter(i -> i.status() == IssueStatus.OPEN)
				.count();

		int waitingQuestionCount = (int) workflow.requirements().stream()
				.flatMap(w -> w.questions().stream())
				.filter(q -> q.status() == ClarificationStatus.WAITING)
				.count();

		return new PreviewSummary(
				totalRequirements, confirmedRequirements, openIssueCount, waitingQuestionCount);
	}

	public record PreviewSummary(
			int totalRequirements,
			int confirmedRequirements,
			int openIssueCount,
			int waitingQuestionCount) {
	}

	public record PreviewBasis(
			long requirementId,
			int sequenceNo,
			long contentVersion,
			Long approvedRevisionId) {
	}

	public record CustomerQuestion(
			long id,
			long issueId,
			AmbiguityType type,
			String evidence,
			int roundNo,
			String questionText) {
	}

	public record CustomerRequirement(
			long requirementId,
			int sequenceNo,
			String originalText,
			long contentVersion,
			List<CustomerQuestion> questions) {
	}

	public record CustomerPreview(
			long documentId,
			String documentTitle,
			Instant generatedAt,
			PreviewSummary summary,
			List<PreviewBasis> basis,
			List<CustomerRequirement> requirements) {
	}

	public record RevisionDetail(
			long id,
			long requirementId,
			int revisionNo,
			String text,
			String status,
			long inputContentVersion,
			List<Long> basedOnClarificationIds,
			String rejectionReason,
			List<Object> acceptanceCriteria) {
	}

	public record EvidenceClarification(
			long id,
			long requirementId,
			long issueId,
			int roundNo,
			String questionText,
			String answerText,
			ClarificationStatus status) {
	}

	public record IssueDetail(
			long id,
			long requirementId,
			AmbiguityType type,
			String evidence,
			IssueStatus status) {
	}

	public record ClarificationDetail(
			long id,
			long requirementId,
			long issueId,
			int roundNo,
			String questionText,
			String answerText,
			ClarificationStatus status) {
	}

	public record ConfirmedRequirement(
			long requirementId,
			int sequenceNo,
			String originalText,
			long contentVersion,
			RevisionDetail approvedRevision,
			List<EvidenceClarification> evidenceAnswers) {
	}

	public record UnconfirmedRequirement(
			long requirementId,
			int sequenceNo,
			String originalText,
			RequirementStatus status,
			long contentVersion,
			List<IssueDetail> issues,
			List<ClarificationDetail> questions) {
	}

	public record DeveloperPreview(
			long documentId,
			String documentTitle,
			Instant generatedAt,
			PreviewSummary summary,
			List<PreviewBasis> basis,
			List<ConfirmedRequirement> confirmedRequirements,
			List<UnconfirmedRequirement> unconfirmedRequirements) {
	}
}
