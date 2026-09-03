package com.sua.reqbridge.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.sua.reqbridge.contract.AmbiguityType;
import com.sua.reqbridge.contract.ai.AnalyzerTypes.*;

class AnalyzerOutputValidatorTests {

	@ParameterizedTest
	@MethodSource("invalidDocuments")
	void rejectsMalformedDocumentOutput(DocumentResult output) {
		assertThatThrownBy(() -> AnalyzerOutputValidator.document(output))
				.isInstanceOf(AiOutputInvalidException.class)
				.hasMessage("분석 결과 형식이 올바르지 않습니다.");
	}

	static Stream<DocumentResult> invalidDocuments() {
		return Stream.of(null, new DocumentResult(null), new DocumentResult(List.of()),
				new DocumentResult(Arrays.asList((RequirementCandidate) null)),
				new DocumentResult(List.of(new RequirementCandidate(0, "원문", List.of()))),
				new DocumentResult(List.of(new RequirementCandidate(2, "원문", List.of()))),
				new DocumentResult(List.of(new RequirementCandidate(1, "원문", List.of()),
						new RequirementCandidate(1, "원문", List.of()))),
				new DocumentResult(List.of(new RequirementCandidate(1, "\uFEFF\u3000", List.of()))),
				new DocumentResult(List.of(new RequirementCandidate(1, "원문", null))),
				documentWithIssue(null),
				documentWithIssue(new IssueCandidate(null, "근거", "질문")),
				documentWithIssue(new IssueCandidate(AmbiguityType.TERM_AMBIGUOUS, " ", "질문")),
				documentWithIssue(new IssueCandidate(AmbiguityType.TERM_AMBIGUOUS, "근거", null)));
	}

	private static DocumentResult documentWithIssue(IssueCandidate issue) {
		return new DocumentResult(List.of(new RequirementCandidate(1, "원문", Arrays.asList(issue))));
	}

	@ParameterizedTest
	@MethodSource("invalidAssessments")
	void rejectsMalformedAssessment(Assessment output) {
		assertThatThrownBy(() -> AnalyzerOutputValidator.assessment(output))
				.isInstanceOf(AiOutputInvalidException.class);
	}

	static Stream<Assessment> invalidAssessments() {
		return Stream.of(null, new Assessment(true, null, null), new Assessment(false, "근거", null),
				new Assessment(false, "근거", "\uFEFF"), new Assessment(true, "근거", "추가 질문"));
	}

	@ParameterizedTest
	@MethodSource("invalidRevisions")
	void rejectsMalformedRevision(RevisionProposal output) {
		assertThatThrownBy(() -> AnalyzerOutputValidator.revision(output))
				.isInstanceOf(AiOutputInvalidException.class);
	}

	static Stream<RevisionProposal> invalidRevisions() {
		return Stream.of(null, new RevisionProposal(null), new RevisionProposal("\u0085\uFEFF"),
				new RevisionProposal("a".repeat(100_001)));
	}

	@Test
	void acceptsValidOutputsAndPreservesOriginalTextAndUnorderedContiguousNumbers() {
		var output = new DocumentResult(List.of(new RequirementCandidate(2, "두 번째", List.of()),
				new RequirementCandidate(1, "  원문\n", List.of())));
		assertThat(AnalyzerOutputValidator.document(output)).isSameAs(output);
		assertThat(AnalyzerOutputValidator.revision(new RevisionProposal("😀".repeat(100_000))).text())
				.hasSize(200_000);
		assertThat(AnalyzerOutputValidator.assessment(new Assessment(false, "불충분", "다음 질문")))
				.isEqualTo(new Assessment(false, "불충분", "다음 질문"));
	}
}
