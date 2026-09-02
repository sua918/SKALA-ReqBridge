package com.sua.reqbridge.project;

public class ProjectNotFoundException extends RuntimeException {

	public ProjectNotFoundException(long projectId) {
		super("Project not found: " + projectId);
	}
}
