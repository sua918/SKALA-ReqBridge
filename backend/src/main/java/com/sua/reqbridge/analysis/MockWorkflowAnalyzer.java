package com.sua.reqbridge.analysis;

import java.util.List;

import com.sua.reqbridge.contract.AmbiguityType;
import com.sua.reqbridge.contract.DocumentSnapshot;
import com.sua.reqbridge.contract.AnalysisAdapterType;
import com.sua.reqbridge.contract.ai.WorkflowAnalyzer;
import com.sua.reqbridge.contract.ai.AnalyzerTypes.*;

public class MockWorkflowAnalyzer implements WorkflowAnalyzer {

	private static final String CONTENT = "시스템은 많은 사용자의 동시 상품 조회 요청에 빠르게 응답해야 한다. "
			+ "부하 시험은 10분 동안 수행하며 성공 응답 비율은 99.9% 이상이어야 한다.";
	private static final String PROPOSED_TEXT = "시스템은 최대 동시 사용자 3,000명의 상품 조회 부하 시험을 10분간 수행할 때 "
			+ "p95 응답 시간 2초 이하, 성공 응답 비율 99.9% 이상을 만족해야 한다.";

	@Override
	public DocumentResult analyze(DocumentSnapshot document) {
		if (document == null || document.content() == null) {
			throw new AiOutputInvalidException("지원하지 않는 Mock 문서입니다.");
		}
		String normalizedDoc = normalize(document.content());
		String normalizedContent = normalize(CONTENT);
		if (!normalizedDoc.contains(normalizedContent)) {
			throw new AiOutputInvalidException("지원하지 않는 Mock 문서입니다.");
		}
		return new DocumentResult(List.of(new RequirementCandidate(1, CONTENT, List.of(
				new IssueCandidate(AmbiguityType.QUANTITY_MISSING,
						"많은 사용자의 정량 기준이 없다.",
						"부하 시험의 최대 동시 사용자는 몇 명인가요?"),
				new IssueCandidate(AmbiguityType.PERFORMANCE_MISSING,
						"빠르게의 측정 가능한 응답 시간 기준이 없다.",
						"부하 시험에서 목표 응답 시간과 측정 지표는 무엇인가요?")))));
	}

	private String normalize(String text) {
		return text.replaceAll("\\s+", " ").trim();
	}

	@Override
	public Assessment assess(AnswerAssessmentInput input) {
		if (input == null || input.answerText() == null) {
			throw new AiOutputInvalidException("지원하지 않는 Mock 답변입니다.");
		}
		return switch (input.answerText()) {
			case "많이 접속할 것 같습니다." -> new Assessment(false,
					"최대 동시 사용자 수가 숫자로 제시되지 않았습니다.",
					"부하 시험의 최대 동시 사용자 수를 숫자로 알려주세요.");
			case "최대 동시 사용자 3,000명입니다." -> new Assessment(true,
					"정량 기준이 확인되었습니다.", null);
			case "p95 응답 시간 2초 이하입니다." -> new Assessment(true,
					"성능 기준이 확인되었습니다.", null);
			default -> throw new AiOutputInvalidException("지원하지 않는 Mock 답변입니다.");
		};
	}

	@Override
	public RevisionProposal generateRevision(RevisionGenerationInput input) {
		if (input == null) {
			throw new AiOutputInvalidException("수정안 생성 입력이 필요합니다.");
		}
		// Deliberately deterministic: semantic rewriting belongs to a future real adapter.
		return new RevisionProposal(PROPOSED_TEXT);
	}

	@Override
	public AnalysisAdapterType adapterType() {
		return AnalysisAdapterType.MOCK;
	}

	@Override
	public String schemaVersion() {
		return "1.0.0";
	}
}
