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
	void throwsAiOutputInvalidForUnsupportedContent() {
		MockWorkflowAnalyzer analyzer = new MockWorkflowAnalyzer();
		DocumentSnapshot unsupported = new DocumentSnapshot(4, 1, "unknown", "완전히 다른 내용의 요구사항", "TEXT");

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> analyzer.analyze(unsupported))
				.isInstanceOf(AiOutputInvalidException.class)
				.hasMessageContaining("지원하지 않는 Mock 문서");
	}
}
