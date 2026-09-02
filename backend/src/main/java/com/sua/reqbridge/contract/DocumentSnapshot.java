package com.sua.reqbridge.contract;

public record DocumentSnapshot(
		long id,
		long projectId,
		String title,
		String content,
		String sourceType) {
}
