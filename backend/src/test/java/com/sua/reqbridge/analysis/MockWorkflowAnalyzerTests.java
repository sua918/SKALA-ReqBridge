package com.sua.reqbridge.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.sua.reqbridge.contract.DocumentSnapshot;

class MockWorkflowAnalyzerTests {

	private static final String CONTENT = "시스템은 많은 사용자의 동시 상품 조회 요청에 빠르게 응답해야 한다. "
			+ "부하 시험은 10분 동안 수행하며 성공 응답 비율은 99.9% 이상이어야 한다.";

	@Test
	void analyzesSameExtractedContentForTextAndFile() {
		MockWorkflowAnalyzer analyzer = new MockWorkflowAnalyzer();

		var text = analyzer.analyze(new DocumentSnapshot(1, 1, "text", CONTENT, "TEXT"));
		var file = analyzer.analyze(new DocumentSnapshot(2, 1, "file", CONTENT, "FILE"));

		assertThat(file).isEqualTo(text);
	}

	@Test
	void analyzesPdfContentWithLineBreaksAndIrregularWhitespace() {
		MockWorkflowAnalyzer analyzer = new MockWorkflowAnalyzer();
		String pdfContent = "   [시스템 요구사항 명세서] \r\n\r\n"
				+ "시스템은   많은 사용자의  동시 상품 조회 요청에\n"
				+ "빠르게 응답해야 한다.\r\n"
				+ "부하 시험은 10분 동안 수행하며   성공 응답 비율은 99.9% 이상이어야 한다. \n\n";

		var result = analyzer.analyze(new DocumentSnapshot(3, 1, "pdfDoc", pdfContent, "FILE"));

		assertThat(result.requirements()).hasSize(1);
		assertThat(result.requirements().getFirst().issues()).hasSize(2);
	}

	@Test
	void extractsNineRequirementsFromRfpDemoDocument() {
		MockWorkflowAnalyzer analyzer = new MockWorkflowAnalyzer();
		String rfpContent = "SFR-001 원재료 이력 데이터 수집 및 블록체인 등록\n"
				+ CONTENT + "\nSEC-001 플랫폼 정보보호 체계 및 이력 데이터 보안";

		var result = analyzer.analyze(new DocumentSnapshot(5, 1, "rfp", rfpContent, "FILE"));

		assertThat(result.requirements()).hasSize(9);
		assertThat(result.requirements()).extracting(MockWorkflowAnalyzer.RequirementCandidateLegacy::sequenceNo)
				.containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9);
		assertThat(result.requirements().get(4).originalText()).startsWith("PER-002");
		assertThat(result.requirements().get(4).issues()).hasSize(2);
	}

	@Test
	void throwsAiOutputInvalidForUnsupportedContent() {
		MockWorkflowAnalyzer analyzer = new MockWorkflowAnalyzer();
		DocumentSnapshot unsupported = new DocumentSnapshot(4, 1, "unknown", "완전히 다른 내용의 요구사항", "TEXT");

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> analyzer.analyze(unsupported))
				.isInstanceOf(AiOutputInvalidException.class)
				.hasMessageContaining("지원하지 않는 Mock 문서");
	}

	@Test
	void implementsWorkflowAnalyzerContract() {
		com.sua.reqbridge.contract.ai.WorkflowAnalyzer analyzer = new MockWorkflowAnalyzer();

		assertThat(analyzer.adapterType()).isEqualTo(com.sua.reqbridge.contract.AnalysisAdapterType.MOCK);
		assertThat(analyzer.schemaVersion()).isEqualTo("1.1.0");

		var docResult = analyzer.analyzeDocument(new com.sua.reqbridge.contract.ai.DocumentAnalysisInput(1L, CONTENT));
		assertThat(docResult.requirements()).hasSize(1);
		assertThat(docResult.requirements().getFirst().issues()).hasSize(2);

		var sufficientAssessment = analyzer.assessAnswer(new com.sua.reqbridge.contract.ai.AnswerAssessmentInput(
				100L, 1L, CONTENT, com.sua.reqbridge.contract.AmbiguityType.QUANTITY_MISSING, "근거",
				10L, 20L, 1, "질문", "최대 동시 사용자 3,000명입니다.", java.util.List.of()));
		assertThat(sufficientAssessment.sufficient()).isTrue();
		assertThat(sufficientAssessment.nextQuestionText()).isNull();

		var insufficientAssessment = analyzer.assessAnswer(new com.sua.reqbridge.contract.ai.AnswerAssessmentInput(
				100L, 1L, CONTENT, com.sua.reqbridge.contract.AmbiguityType.QUANTITY_MISSING, "근거",
				10L, 20L, 1, "질문", "많이 접속할 것 같습니다.", java.util.List.of()));
		assertThat(insufficientAssessment.sufficient()).isFalse();
		assertThat(insufficientAssessment.nextQuestionText()).isNotNull();

		var proposal = analyzer.generateRevision(new com.sua.reqbridge.contract.ai.RevisionGenerationInput(
				100L, CONTENT, java.util.List.of(), null));
		assertThat(proposal.proposedText()).isEqualTo(MockWorkflowAnalyzer.PROPOSED_TEXT);
	}
}
