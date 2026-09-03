package com.sua.reqbridge.contract.ai;

import java.util.List;

public record RevisionGenerationInput(
		long requirementId,
		String originalText,
		List<ClarificationContext> clarifications,
		String rejectionReason) {

	public RevisionGenerationInput {
		clarifications = clarifications == null ? List.of() : List.copyOf(clarifications);
	}

	public record ClarificationContext(
			long clarificationId,
			long issueId,
			String questionText,
			String answerText) {
	}
}
