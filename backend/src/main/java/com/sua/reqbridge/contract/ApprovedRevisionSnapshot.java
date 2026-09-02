package com.sua.reqbridge.contract;

import java.util.List;

public record ApprovedRevisionSnapshot(
		long id,
		int revisionNo,
		String text,
		List<Long> basedOnClarificationIds,
		List<AcceptanceCriterionSnapshot> acceptanceCriteria) {

	public ApprovedRevisionSnapshot {
		basedOnClarificationIds = basedOnClarificationIds == null
				? List.of()
				: List.copyOf(basedOnClarificationIds);
		acceptanceCriteria = acceptanceCriteria == null
				? List.of()
				: List.copyOf(acceptanceCriteria);
	}
}
