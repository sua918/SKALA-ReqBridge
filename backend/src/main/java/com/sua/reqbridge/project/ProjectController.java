package com.sua.reqbridge.project;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sua.reqbridge.common.api.ApiResponse;
import com.sua.reqbridge.common.api.ItemList;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@Validated
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

	private final ProjectService projectService;

	public ProjectController(ProjectService projectService) {
		this.projectService = projectService;
	}

	@PostMapping
	public ResponseEntity<ApiResponse<ProjectView>> create(
			@Valid @RequestBody ProjectCreateRequest request) {
		Project created = projectService.create(request.name(), request.description());
		return ResponseEntity.created(URI.create("/api/projects/" + created.getId()))
				.body(ApiResponse.of(ProjectView.from(created)));
	}

	@GetMapping
	public ApiResponse<ItemList<ProjectView>> list() {
		List<ProjectView> projects = projectService.list().stream()
				.map(ProjectView::from)
				.toList();
		return ApiResponse.of(new ItemList<>(projects));
	}

	@GetMapping("/{projectId}")
	public ApiResponse<ProjectView> get(
			@Positive @Max(9_007_199_254_740_991L) @PathVariable long projectId) {
		return ApiResponse.of(ProjectView.from(projectService.get(projectId)));
	}

	public record ProjectCreateRequest(
			@NotBlank(message = "프로젝트 이름을 입력해주세요.") String name,
			String description) {
	}

	public record ProjectView(
			long id,
			String name,
			String description,
			Instant createdAt) {

		static ProjectView from(Project project) {
			return new ProjectView(
					project.getId(),
					project.getName(),
					project.getDescription(),
					project.getCreatedAt());
		}
	}
}
