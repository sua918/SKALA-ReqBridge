package com.sua.reqbridge.contract.ai;

import java.util.List;

import com.sua.reqbridge.contract.AmbiguityType;

/** Internal analyzer contracts, not REST responses or JPA entities. */
public final class AnalyzerTypes {

	private AnalyzerTypes() {
	}

	public record DocumentResult(List<RequirementCandidate> requirements) {
	}

	public record RequirementCandidate(int sequenceNo, String originalText, List<IssueCandidate> issues) {
	}

	public record IssueCandidate(AmbiguityType type, String evidence, String questionText) {
	}

	public record Assessment(boolean sufficient, String reason, String nextQuestionText) {
	}

	public record RevisionProposal(String text) {
	}

	public record AnswerContext(long clarificationId, long issueId, int roundNo,
			String questionText, String answerText) {
	}

	public record AnswerAssessmentInput(long requirementId, String originalText, long contentVersion,
			AmbiguityType ambiguityType, String evidence, String questionText, String answerText,
			List<AnswerContext> answers) {
		public AnswerAssessmentInput {
			answers = List.copyOf(answers);
		}
	}

	public record RevisionGenerationInput(long requirementId, String originalText, long contentVersion,
			List<AnswerContext> answers, String previousText, String rejectionReason) {
		public RevisionGenerationInput {
			answers = List.copyOf(answers);
		}
	}
}
