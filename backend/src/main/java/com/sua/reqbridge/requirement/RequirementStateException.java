package com.sua.reqbridge.requirement;

import com.sua.reqbridge.contract.StateConflictException;

public class RequirementStateException extends StateConflictException {

	public RequirementStateException(String message) {
		super(message);
	}
}
