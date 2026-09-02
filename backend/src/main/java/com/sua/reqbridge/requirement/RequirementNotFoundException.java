package com.sua.reqbridge.requirement;

import com.sua.reqbridge.contract.ResourceNotFoundException;

public class RequirementNotFoundException extends ResourceNotFoundException {

	public RequirementNotFoundException(long requirementId) {
		super("Requirement not found: " + requirementId);
	}
}
