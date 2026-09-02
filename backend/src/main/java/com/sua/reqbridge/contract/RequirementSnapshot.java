package com.sua.reqbridge.contract;

public record RequirementSnapshot(
		long id,
		long documentId,
		long analysisId,
		int sequenceNo,
		String originalText,
		RequirementStatus status,
		long contentVersion,
		Long approvedRevisionId,
		String confirmedText) {
}
