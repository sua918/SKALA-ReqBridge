package com.sua.reqbridge.analysis;

import java.util.List;

import com.sua.reqbridge.contract.AmbiguityType;
import com.sua.reqbridge.contract.AnalysisAdapterType;
import com.sua.reqbridge.contract.DocumentSnapshot;
import com.sua.reqbridge.contract.ai.AnswerAssessment;
import com.sua.reqbridge.contract.ai.AnswerAssessmentInput;
import com.sua.reqbridge.contract.ai.DocumentAnalysisInput;
import com.sua.reqbridge.contract.ai.DocumentAnalysisResult;
import com.sua.reqbridge.contract.ai.IssueCandidate;
import com.sua.reqbridge.contract.ai.RequirementCandidate;
import com.sua.reqbridge.contract.ai.RevisionGenerationInput;
import com.sua.reqbridge.contract.ai.RevisionProposal;
import com.sua.reqbridge.contract.ai.WorkflowAnalyzer;

public class MockWorkflowAnalyzer implements WorkflowAnalyzer {

	public static final String CONTENT = "시스템은 많은 사용자의 동시 상품 조회 요청에 빠르게 응답해야 한다. "
			+ "부하 시험은 10분 동안 수행하며 성공 응답 비율은 99.9% 이상이어야 한다.";

	public static final String PROPOSED_TEXT = "시스템은 최대 동시 사용자 3,000명의 상품 조회 부하 시험을 10분간 수행할 때 "
			+ "p95 응답 시간 2초 이하, 성공 응답 비율 99.9% 이상을 만족해야 한다.";

	private static final String SCHEMA_VERSION = "1.1.0";

	@Override
	public AnalysisAdapterType adapterType() {
		return AnalysisAdapterType.MOCK;
	}

	@Override
	public String schemaVersion() {
		return SCHEMA_VERSION;
	}

	@Override
	public DocumentAnalysisResult analyzeDocument(DocumentAnalysisInput input) {
		if (input == null || input.content() == null) {
			throw new AiOutputInvalidException("지원하지 않는 Mock 문서입니다.");
		}
		String normalizedDoc = normalize(input.content());
		String normalizedContent = normalize(CONTENT);
		if (!normalizedDoc.contains(normalizedContent)) {
			throw new AiOutputInvalidException("지원하지 않는 Mock 문서입니다.");
		}
		if (!normalizedDoc.contains("SFR-001") || !normalizedDoc.contains("SEC-001")) {
			return new DocumentAnalysisResult(List.of(performanceRequirement(1)));
		}
		return new DocumentAnalysisResult(List.of(
				candidate(1, "SFR-001 — 원재료 이력 데이터 수집 및 블록체인 등록",
						"원재료 생산·가공·유통 단계의 이력 데이터를 수집하고 위변조 방지를 위해 블록체인 원장에 등록해야 한다."),
				candidate(2, "SFR-002 — 시험성적서 및 이력 데이터 수정 관리",
						"시험성적서와 이력 데이터의 변경 내역을 추적하고 승인된 수정 사항을 관리해야 한다."),
				candidate(3, "SFR-003 — 대국민 바이오 상품 이력 조회 포털",
						"국민이 바이오 상품의 생산·시험·유통 이력을 조회할 수 있는 서비스를 제공해야 한다."),
				candidate(4, "PER-001 — 시스템 응답 성능 일반",
						"사용자가 주요 기능을 이용할 때 안정적인 응답 성능을 제공해야 한다."),
				performanceRequirement(5),
				candidate(6, "PER-003 — 서비스 가용성 및 무중단 운영 보장",
						"서비스 장애를 최소화하고 안정적인 가용성과 무중단 운영 체계를 보장해야 한다."),
				candidate(7, "INR-001 — 외부 유통망 및 시험기관 연계 API 제공",
						"외부 유통망과 시험기관이 표준 방식으로 데이터를 연계할 수 있는 API를 제공해야 한다."),
				candidate(8, "DAR-001 — 공공 데이터 표준 관리 및 원장 데이터 구조화",
						"공공 데이터 표준에 따라 정보를 관리하고 블록체인 원장 데이터를 구조화해야 한다."),
				candidate(9, "SEC-001 — 플랫폼 정보보호 체계 및 이력 데이터 보안",
						"플랫폼과 이력 데이터의 기밀성·무결성을 보호하는 정보보호 체계를 마련해야 한다.")));
	}

	private RequirementCandidate candidate(int sequenceNo, String title, String text) {
		return new RequirementCandidate(sequenceNo, title + "\n" + text, List.of());
	}

	private RequirementCandidate performanceRequirement(int sequenceNo) {
		return new RequirementCandidate(sequenceNo, "PER-002 — 대용량 상품 조회 성능 보장\n" + CONTENT, List.of(
				new IssueCandidate(AmbiguityType.QUANTITY_MISSING,
						"많은 사용자의 정량 기준이 없다.",
						"부하 시험의 최대 동시 사용자는 몇 명인가요?"),
				new IssueCandidate(AmbiguityType.PERFORMANCE_MISSING,
						"빠르게의 측정 가능한 응답 시간 기준이 없다.",
						"부하 시험에서 목표 응답 시간과 측정 지표는 무엇인가요?")));
	}

	@Override
	public AnswerAssessment assessAnswer(AnswerAssessmentInput input) {
		if (input == null || input.answerText() == null) {
			throw new AiOutputInvalidException("지원하지 않는 Mock 답변입니다.");
		}
		return switch (input.answerText()) {
			case "많이 접속할 것 같습니다." -> new AnswerAssessment(false,
					"최대 동시 사용자 수가 숫자로 제시되지 않았습니다.",
					"부하 시험의 최대 동시 사용자 수를 숫자로 알려주세요.");
			case "최대 동시 사용자 3,000명입니다." -> new AnswerAssessment(true,
					"정량 기준이 확인되었습니다.", null);
			case "p95 응답 시간 2초 이하입니다." -> new AnswerAssessment(true,
					"성능 기준이 확인되었습니다.", null);
			default -> throw new AiOutputInvalidException("지원하지 않는 Mock 답변입니다.");
		};
	}

	@Override
	public RevisionProposal generateRevision(RevisionGenerationInput input) {
		if (input == null) {
			throw new AiOutputInvalidException("수정안 생성을 위한 입력이 필요합니다.");
		}
		return new RevisionProposal(PROPOSED_TEXT);
	}

	// 기존 호출 코드 및 테스트 호환용 메서드
	public DocumentResult analyze(DocumentSnapshot document) {
		if (document == null) {
			throw new AiOutputInvalidException("지원하지 않는 Mock 문서입니다.");
		}
		DocumentAnalysisResult result = analyzeDocument(new DocumentAnalysisInput(document.id(), document.content()));
		return new DocumentResult(result.requirements().stream()
				.map(r -> new RequirementCandidateLegacy(r.sequenceNo(), r.originalText(),
						r.issues().stream()
								.map(i -> new IssueCandidateLegacy(i.type(), i.evidence(), i.questionText()))
								.toList()))
				.toList());
	}

	public Assessment assess(String answerText) {
		AnswerAssessment result = assessAnswer(new AnswerAssessmentInput(
				0L, 1L, "", null, null, 0L, 0L, 1, null, answerText, List.of()));
		return new Assessment(result.sufficient(), result.reason(), result.nextQuestionText());
	}

	private String normalize(String text) {
		return text.replaceAll("\\s+", " ").trim();
	}

	// Legacy records for backward compatibility with existing tests
	public record DocumentResult(List<RequirementCandidateLegacy> requirements) {
	}

	public record RequirementCandidateLegacy(
			int sequenceNo, String originalText, List<IssueCandidateLegacy> issues) {
	}

	public record IssueCandidateLegacy(
			AmbiguityType type, String evidence, String questionText) {
	}

	public record Assessment(boolean sufficient, String reason, String nextQuestionText) {
	}
}
