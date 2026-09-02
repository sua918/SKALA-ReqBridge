package com.sua.reqbridge.contract;

public record IssueSnapshot(
		long id,
		AmbiguityType type,
		String evidence,
		IssueStatus status) {
}
