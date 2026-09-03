package com.sua.reqbridge.contract.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record RequirementCandidate(int sequenceNo, String originalText, List<IssueCandidate> issues) {

	public RequirementCandidate {
		issues = issues == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(issues));
	}
}
