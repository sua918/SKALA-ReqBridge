package com.sua.reqbridge.contract;

import java.util.List;

public record WorkflowPreviewSnapshot(
		long documentId,
		List<WorkflowRequirementSnapshot> requirements) {

	public WorkflowPreviewSnapshot {
		requirements = requirements == null ? List.of() : List.copyOf(requirements);
	}
}
