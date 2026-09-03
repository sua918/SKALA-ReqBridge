package com.sua.reqbridge.project;

import com.sua.reqbridge.contract.ResourceNotFoundException;

public class ProjectNotFoundException extends ResourceNotFoundException {

	public ProjectNotFoundException(long projectId) {
		super("Project not found: " + projectId);
	}
}
