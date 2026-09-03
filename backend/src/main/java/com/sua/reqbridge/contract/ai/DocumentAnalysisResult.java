package com.sua.reqbridge.contract.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record DocumentAnalysisResult(List<RequirementCandidate> requirements) {

	public DocumentAnalysisResult {
		requirements = requirements == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(requirements));
	}
}
