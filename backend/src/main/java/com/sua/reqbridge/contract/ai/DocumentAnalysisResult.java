package com.sua.reqbridge.contract.ai;

import java.util.List;

public record DocumentAnalysisResult(List<RequirementCandidate> requirements) {

	public DocumentAnalysisResult {
		requirements = requirements == null ? List.of() : List.copyOf(requirements);
	}
}
