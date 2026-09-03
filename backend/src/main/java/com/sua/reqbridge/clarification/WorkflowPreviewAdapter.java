package com.sua.reqbridge.clarification;

import java.util.ArrayList;
import java.util.List;

import org.springframework.transaction.annotation.Transactional;

import com.sua.reqbridge.ambiguity.AmbiguityIssue;
import com.sua.reqbridge.ambiguity.AmbiguityIssueRepository;
import com.sua.reqbridge.contract.ApprovedRevisionSnapshot;
import com.sua.reqbridge.contract.CoreRequirementPort;
import com.sua.reqbridge.contract.IssueSnapshot;
import com.sua.reqbridge.contract.QuestionSnapshot;
import com.sua.reqbridge.contract.RequirementSnapshot;
import com.sua.reqbridge.contract.WorkflowPreviewPort;
import com.sua.reqbridge.contract.WorkflowPreviewSnapshot;
import com.sua.reqbridge.contract.WorkflowRequirementSnapshot;
import com.sua.reqbridge.revision.RequirementRevision;
import com.sua.reqbridge.revision.RequirementRevisionRepository;

public class WorkflowPreviewAdapter implements WorkflowPreviewPort {

	private final CoreRequirementPort core;
	private final AmbiguityIssueRepository issues;
	private final ClarificationRepository clarifications;
	private final RequirementRevisionRepository revisions;

	public WorkflowPreviewAdapter(
			CoreRequirementPort core,
			AmbiguityIssueRepository issues,
			ClarificationRepository clarifications,
			RequirementRevisionRepository revisions) {
		this.core = core;
		this.issues = issues;
		this.clarifications = clarifications;
		this.revisions = revisions;
	}

	@Override
	@Transactional(readOnly = true)
	public WorkflowPreviewSnapshot getPreview(long documentId) {
		List<RequirementSnapshot> requirementSnapshots = core.listRequirements(documentId);
		List<WorkflowRequirementSnapshot> workflowReqs = new ArrayList<>();

		for (RequirementSnapshot req : requirementSnapshots) {
			long requirementId = req.id();
			List<IssueSnapshot> issueSnapshots = issues.findByRequirementIdOrderByIdAsc(requirementId).stream()
					.map(WorkflowPreviewAdapter::toIssueSnapshot)
					.toList();

			List<QuestionSnapshot> questionSnapshots = clarifications.findByRequirementIdOrderByIssueIdAscRoundNoAsc(requirementId).stream()
					.map(WorkflowPreviewAdapter::toQuestionSnapshot)
					.toList();

			ApprovedRevisionSnapshot approvedRevSnapshot = null;
			if (req.approvedRevisionId() != null) {
				approvedRevSnapshot = revisions.findById(req.approvedRevisionId())
						.map(WorkflowPreviewAdapter::toApprovedRevisionSnapshot)
						.orElse(null);
			}

			workflowReqs.add(new WorkflowRequirementSnapshot(
					requirementId, issueSnapshots, questionSnapshots, approvedRevSnapshot));
		}

		return new WorkflowPreviewSnapshot(documentId, workflowReqs);
	}

	private static IssueSnapshot toIssueSnapshot(AmbiguityIssue issue) {
		return new IssueSnapshot(
				issue.getId(),
				issue.getType(),
				issue.getEvidence(),
				issue.getStatus());
	}

	private static QuestionSnapshot toQuestionSnapshot(Clarification clarification) {
		return new QuestionSnapshot(
				clarification.getId(),
				clarification.getRequirementId(),
				clarification.getIssueId(),
				clarification.getRoundNo(),
				clarification.getQuestionText(),
				clarification.getAnswerText(),
				clarification.getStatus());
	}

	private static ApprovedRevisionSnapshot toApprovedRevisionSnapshot(RequirementRevision revision) {
		return new ApprovedRevisionSnapshot(
				revision.getId(),
				revision.getRevisionNo(),
				revision.getText(),
				List.copyOf(revision.getBasedOnClarificationIds()),
				List.of());
	}
}
