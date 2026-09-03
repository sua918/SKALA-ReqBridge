package com.sua.reqbridge.common.api;

import java.util.List;

public record ErrorDetail(String code, String message, List<FieldErrorDetail> fieldErrors) {

	public ErrorDetail {
		fieldErrors = List.copyOf(fieldErrors);
	}
}
