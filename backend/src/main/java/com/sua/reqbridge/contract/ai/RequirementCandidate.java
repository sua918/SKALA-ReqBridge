package com.sua.reqbridge.contract.ai;

import java.util.List;

public record RequirementCandidate(int sequenceNo, String originalText, List<IssueCandidate> issues) {

	public RequirementCandidate {
		issues = issues == null ? List.of() : List.copyOf(issues);
	}
}
