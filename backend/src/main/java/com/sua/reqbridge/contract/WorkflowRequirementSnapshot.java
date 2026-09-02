package com.sua.reqbridge.contract;

import java.util.List;

public record WorkflowRequirementSnapshot(
		long requirementId,
		List<IssueSnapshot> issues,
		List<QuestionSnapshot> questions,
		ApprovedRevisionSnapshot approvedRevision) {

	public WorkflowRequirementSnapshot {
		issues = issues == null ? List.of() : List.copyOf(issues);
		questions = questions == null ? List.of() : List.copyOf(questions);
	}
}
