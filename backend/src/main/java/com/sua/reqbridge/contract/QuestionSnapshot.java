package com.sua.reqbridge.contract;

public record QuestionSnapshot(
		long id,
		long requirementId,
		long issueId,
		int roundNo,
		String questionText,
		String answerText,
		ClarificationStatus status) {
}
