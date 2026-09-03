package com.sua.reqbridge.contract.ai;

import java.util.List;

import com.sua.reqbridge.contract.AmbiguityType;

public record AnswerAssessmentInput(
		long requirementId,
		long contentVersion,
		String requirementText,
		AmbiguityType issueType,
		String evidence,
		long clarificationId,
		long issueId,
		int roundNo,
		String questionText,
		String answerText,
		List<ClarificationHistory> history) {

	public AnswerAssessmentInput {
		history = history == null ? List.of() : List.copyOf(history);
	}

	public record ClarificationHistory(
			long clarificationId,
			int roundNo,
			String questionText,
			String answerText) {
	}
}
