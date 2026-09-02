package com.sua.reqbridge.contract;

import java.util.List;

public interface CoreRequirementPort {

	DocumentSnapshot getDocument(long documentId);

	/**
	 * 호출자의 트랜잭션에 참여하며, 반환 시점에도 문서 잠금을 유지한다.
	 */
	DocumentSnapshot lockDocument(long documentId);

	/**
	 * 반환 결과의 {@code sequenceNo}로 각 입력 항목과 생성된 요구사항을 대응한다.
	 */
	List<RequirementSnapshot> createRequirements(
			long documentId, long analysisId, List<RequirementSeed> items);

	RequirementSnapshot getRequirement(long requirementId);

	/**
	 * 호출자의 트랜잭션에 참여하며, 반환 시점에도 요구사항 잠금을 유지한다.
	 */
	RequirementSnapshot lockRequirement(long requirementId);

	/**
	 * 업무 입력 변경 버전인 {@code contentVersion}이 예상값과 일치하면 1 증가시킨다.
	 */
	long advanceContentVersion(long requirementId, long expectedContentVersion);

	/**
	 * 일반 상태 변경인 {@code OPEN}과 {@code IN_REVIEW} 사이의 전이만 수행한다.
	 * 최종 확정에는 {@link #confirmRequirement(long, long, long, String)}를 사용한다.
	 */
	void changeStatus(
			long requirementId, long expectedContentVersion, RequirementStatus targetStatus);

	/**
	 * 승인 처리와 확정본 반영이 같은 트랜잭션에서 수행되도록 호출되어야 한다.
	 */
	void confirmRequirement(
			long requirementId, long expectedContentVersion, long revisionId, String approvedText);

	List<RequirementSnapshot> listRequirements(long documentId);
}
