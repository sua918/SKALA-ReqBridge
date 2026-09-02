package com.sua.reqbridge.requirement;

public class RequirementNotFoundException extends RuntimeException {

	public RequirementNotFoundException(long requirementId) {
		super("Requirement not found: " + requirementId);
	}
}
