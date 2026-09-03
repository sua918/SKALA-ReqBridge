package com.sua.reqbridge.contract.ai;

public record AnswerAssessmentInput(
		long clarificationId,
		long issueId,
		String questionText,
		String answerText) {
}
