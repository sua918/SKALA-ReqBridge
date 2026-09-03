package com.sua.reqbridge.contract.ai;

public record AnswerAssessment(boolean sufficient, String reason, String nextQuestionText) {
}
