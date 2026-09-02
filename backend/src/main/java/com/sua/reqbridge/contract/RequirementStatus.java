package com.sua.reqbridge.contract;

/** 요구사항의 추출부터 검토 및 확정까지의 업무 상태. */
public enum RequirementStatus {
	EXTRACTED,
	AMBIGUOUS,
	CLARIFYING,
	IN_REVIEW,
	CONFIRMED
}
