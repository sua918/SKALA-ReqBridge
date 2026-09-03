package com.sua.reqbridge.analysis;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.sua.reqbridge.contract.AmbiguityType;
import com.sua.reqbridge.contract.ai.AnswerAssessment;
import com.sua.reqbridge.contract.ai.DocumentAnalysisResult;
import com.sua.reqbridge.contract.ai.IssueCandidate;
import com.sua.reqbridge.contract.ai.RequirementCandidate;
import com.sua.reqbridge.contract.ai.RevisionProposal;

class AnalyzerOutputValidatorTests {

	@Nested
	@DisplayName("문서 분석 결과 검증")
	class DocumentResultValidation {

		@Test
		void rejectsNullOutput() {
			assertThatThrownBy(() -> AnalyzerOutputValidator.validateDocumentResult(null))
					.isInstanceOf(AiOutputInvalidException.class)
					.hasMessageContaining("분석 결과가 비어 있습니다.");
		}

		@Test
		void rejectsEmptyRequirements() {
			assertThatThrownBy(() -> AnalyzerOutputValidator.validateDocumentResult(
					new DocumentAnalysisResult(List.of())))
					.isInstanceOf(AiOutputInvalidException.class)
					.hasMessageContaining("추출된 요구사항이 없습니다.");
		}

		@Test
		void rejectsNullCandidate() {
			assertThatThrownBy(() -> AnalyzerOutputValidator.validateDocumentResult(
					new DocumentAnalysisResult(java.util.Collections.singletonList(null))))
					.isInstanceOf(AiOutputInvalidException.class)
					.hasMessageContaining("요구사항 후보가 null입니다.");
		}

		@Test
		void rejectsInvalidSequenceNo() {
			var candidate = new RequirementCandidate(0, "원문", List.of());
			assertThatThrownBy(() -> AnalyzerOutputValidator.validateDocumentResult(
					new DocumentAnalysisResult(List.of(candidate))))
					.isInstanceOf(AiOutputInvalidException.class)
					.hasMessageContaining("순번은 1 이상이어야 합니다");
		}

		@Test
		void rejectsDuplicateSequenceNo() {
			var c1 = new RequirementCandidate(1, "원문 1", List.of());
			var c2 = new RequirementCandidate(1, "원문 2", List.of());
			assertThatThrownBy(() -> AnalyzerOutputValidator.validateDocumentResult(
					new DocumentAnalysisResult(List.of(c1, c2))))
					.isInstanceOf(AiOutputInvalidException.class)
					.hasMessageContaining("중복된 요구사항 순번");
		}

		@Test
		void rejectsBlankOriginalText() {
			var candidate = new RequirementCandidate(1, "   ", List.of());
			assertThatThrownBy(() -> AnalyzerOutputValidator.validateDocumentResult(
					new DocumentAnalysisResult(List.of(candidate))))
					.isInstanceOf(AiOutputInvalidException.class)
					.hasMessageContaining("원문이 비어 있습니다.");
		}

		@Test
		void rejectsNullIssueCandidate() {
			var candidate = new RequirementCandidate(1, "원문", java.util.Collections.singletonList(null));
			assertThatThrownBy(() -> AnalyzerOutputValidator.validateDocumentResult(
					new DocumentAnalysisResult(List.of(candidate))))
					.isInstanceOf(AiOutputInvalidException.class)
					.hasMessageContaining("이슈 후보가 null입니다.");
		}

		@Test
		void rejectsNullIssueType() {
			var issue = new IssueCandidate(null, "근거", "질문");
			var candidate = new RequirementCandidate(1, "원문", List.of(issue));
			assertThatThrownBy(() -> AnalyzerOutputValidator.validateDocumentResult(
					new DocumentAnalysisResult(List.of(candidate))))
					.isInstanceOf(AiOutputInvalidException.class)
					.hasMessageContaining("이슈 유형");
		}

		@Test
		void rejectsBlankIssueEvidence() {
			var issue = new IssueCandidate(AmbiguityType.QUANTITY_MISSING, "  ", "질문");
			var candidate = new RequirementCandidate(1, "원문", List.of(issue));
			assertThatThrownBy(() -> AnalyzerOutputValidator.validateDocumentResult(
					new DocumentAnalysisResult(List.of(candidate))))
					.isInstanceOf(AiOutputInvalidException.class)
					.hasMessageContaining("근거가 비어 있습니다.");
		}

		@Test
		void rejectsBlankQuestionText() {
			var issue = new IssueCandidate(AmbiguityType.QUANTITY_MISSING, "근거", "");
			var candidate = new RequirementCandidate(1, "원문", List.of(issue));
			assertThatThrownBy(() -> AnalyzerOutputValidator.validateDocumentResult(
					new DocumentAnalysisResult(List.of(candidate))))
					.isInstanceOf(AiOutputInvalidException.class)
					.hasMessageContaining("질문 문구가 비어 있습니다.");
		}

		@Test
		void acceptsValidDocumentResult() {
			var issue = new IssueCandidate(AmbiguityType.QUANTITY_MISSING, "근거", "질문?");
			var candidate = new RequirementCandidate(1, "원문", List.of(issue));
			assertThatCode(() -> AnalyzerOutputValidator.validateDocumentResult(
					new DocumentAnalysisResult(List.of(candidate))))
					.doesNotThrowAnyException();
		}
	}

	@Nested
	@DisplayName("답변 판정 결과 검증")
	class AnswerAssessmentValidation {

		@Test
		void rejectsNullAssessment() {
			assertThatThrownBy(() -> AnalyzerOutputValidator.validateAnswerAssessment(null))
					.isInstanceOf(AiOutputInvalidException.class)
					.hasMessageContaining("답변 판정 결과가 비어 있습니다.");
		}

		@Test
		void rejectsBlankReason() {
			assertThatThrownBy(() -> AnalyzerOutputValidator.validateAnswerAssessment(
					new AnswerAssessment(true, "  ", null)))
					.isInstanceOf(AiOutputInvalidException.class)
					.hasMessageContaining("답변 판정 사유가 비어 있습니다.");
		}

		@Test
		void rejectsInsufficientWithoutNextQuestion() {
			assertThatThrownBy(() -> AnalyzerOutputValidator.validateAnswerAssessment(
					new AnswerAssessment(false, "사유", "   ")))
					.isInstanceOf(AiOutputInvalidException.class)
					.hasMessageContaining("후속 질문이 필요합니다.");
		}

		@Test
		void rejectsSufficientWithNextQuestion() {
			assertThatThrownBy(() -> AnalyzerOutputValidator.validateAnswerAssessment(
					new AnswerAssessment(true, "사유", "불필요한 후속 질문")))
					.isInstanceOf(AiOutputInvalidException.class)
					.hasMessageContaining("충분한 답변 판정 시 후속 질문이 존재할 수 없습니다.");
		}

		@Test
		void acceptsValidAssessments() {
			assertThatCode(() -> AnalyzerOutputValidator.validateAnswerAssessment(
					new AnswerAssessment(true, "정상 확인", null)))
					.doesNotThrowAnyException();

			assertThatCode(() -> AnalyzerOutputValidator.validateAnswerAssessment(
					new AnswerAssessment(false, "숫자 필요", "얼마인가요?")))
					.doesNotThrowAnyException();
		}
	}

	@Nested
	@DisplayName("수정안 생성 결과 검증")
	class RevisionProposalValidation {

		@Test
		void rejectsNullProposal() {
			assertThatThrownBy(() -> AnalyzerOutputValidator.validateRevisionProposal(null))
					.isInstanceOf(AiOutputInvalidException.class)
					.hasMessageContaining("수정안 생성 결과가 비어 있습니다.");
		}

		@Test
		void rejectsBlankProposedText() {
			assertThatThrownBy(() -> AnalyzerOutputValidator.validateRevisionProposal(
					new RevisionProposal("   ")))
					.isInstanceOf(AiOutputInvalidException.class)
					.hasMessageContaining("수정안 문구가 비어 있습니다.");
		}

		@Test
		void rejectsExcessivelyLongProposedText() {
			String tooLong = "A".repeat(20_001);
			assertThatThrownBy(() -> AnalyzerOutputValidator.validateRevisionProposal(
					new RevisionProposal(tooLong)))
					.isInstanceOf(AiOutputInvalidException.class)
					.hasMessageContaining("길이가 허용 한도를 초과");
		}

		@Test
		void acceptsValidProposal() {
			assertThatCode(() -> AnalyzerOutputValidator.validateRevisionProposal(
					new RevisionProposal("시스템은 정상 동작해야 한다.")))
					.doesNotThrowAnyException();
		}
	}
}
