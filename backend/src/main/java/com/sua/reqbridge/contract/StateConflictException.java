package com.sua.reqbridge.contract;

public class StateConflictException extends RuntimeException {

	private final String code;

	public StateConflictException(String message) {
		this("STATE_CONFLICT", message);
	}

	public StateConflictException(String code, String message) {
		super(message);
		this.code = code;
	}

	public String getCode() {
		return code;
	}
}
