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
}
