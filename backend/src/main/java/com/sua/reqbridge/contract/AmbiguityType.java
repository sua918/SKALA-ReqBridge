package com.sua.reqbridge.contract;

/** 요구사항 불명확성의 분류. */
public enum AmbiguityType {
	QUANTITY_MISSING,
	PERFORMANCE_MISSING,
	CONDITION_MISSING,
	ACTOR_MISSING,
	SUCCESS_CRITERIA_MISSING,
	TERM_AMBIGUOUS,
	EXCEPTION_MISSING
}
