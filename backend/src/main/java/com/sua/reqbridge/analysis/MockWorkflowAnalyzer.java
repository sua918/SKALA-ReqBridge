package com.sua.reqbridge.analysis;

import java.util.List;

import com.sua.reqbridge.contract.AmbiguityType;
import com.sua.reqbridge.contract.DocumentSnapshot;

public class MockWorkflowAnalyzer {

	private static final String CONTENT = "시스템은 많은 사용자의 동시 상품 조회 요청에 빠르게 응답해야 한다. "
			+ "부하 시험은 10분 동안 수행하며 성공 응답 비율은 99.9% 이상이어야 한다.";

	public DocumentResult analyze(DocumentSnapshot document) {
		if (!"TEXT".equals(document.sourceType()) || !CONTENT.equals(document.content())) {
			throw new IllegalArgumentException("지원하지 않는 Mock 문서입니다.");
		}
		return new DocumentResult(List.of(new RequirementCandidate(1, CONTENT, List.of(
				new IssueCandidate(AmbiguityType.QUANTITY_MISSING,
						"많은 사용자의 정량 기준이 없다.",
						"부하 시험의 최대 동시 사용자는 몇 명인가요?"),
				new IssueCandidate(AmbiguityType.PERFORMANCE_MISSING,
						"빠르게의 측정 가능한 응답 시간 기준이 없다.",
						"부하 시험에서 목표 응답 시간과 측정 지표는 무엇인가요?")))));
	}

	public Assessment assess(String answerText) {
		return switch (answerText) {
			case "많이 접속할 것 같습니다." -> new Assessment(false,
					"최대 동시 사용자 수가 숫자로 제시되지 않았습니다.",
					"부하 시험의 최대 동시 사용자 수를 숫자로 알려주세요.");
			case "최대 동시 사용자 3,000명입니다." -> new Assessment(true,
					"정량 기준이 확인되었습니다.", null);
			case "p95 응답 시간 2초 이하입니다." -> new Assessment(true,
					"성능 기준이 확인되었습니다.", null);
			default -> throw new IllegalArgumentException("지원하지 않는 Mock 답변입니다.");
		};
	}

	public record DocumentResult(List<RequirementCandidate> requirements) {
	}

	public record RequirementCandidate(
			int sequenceNo, String originalText, List<IssueCandidate> issues) {
	}

	public record IssueCandidate(
			AmbiguityType type, String evidence, String questionText) {
	}

	public record Assessment(boolean sufficient, String reason, String nextQuestionText) {
	}
}
