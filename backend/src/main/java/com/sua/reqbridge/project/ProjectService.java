package com.sua.reqbridge.project;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sua.reqbridge.common.validation.TextRules;

@Service
@Transactional(readOnly = true)
public class ProjectService {

	private final ProjectRepository projectRepository;

	public ProjectService(ProjectRepository projectRepository) {
		this.projectRepository = projectRepository;
	}

	@Transactional
	public Project create(String name, String description) {
		String normalizedName = TextRules.requiredTrimmed("Project name", name, 100);
		String normalizedDescription = TextRules.optionalTrimmed("Project description", description, 2000);
		return projectRepository.save(new Project(normalizedName, normalizedDescription));
	}

	public Project get(long projectId) {
		return projectRepository.findById(projectId)
				.orElseThrow(() -> new ProjectNotFoundException(projectId));
	}

	public List<Project> list() {
		return projectRepository.findAllByOrderByIdDesc();
	}
}
