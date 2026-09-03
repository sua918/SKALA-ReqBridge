package com.sua.reqbridge.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTests {

	@Mock
	private ProjectRepository projectRepository;

	@Test
	void createsProjectWithNormalizedText() {
		when(projectRepository.save(any(Project.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		ProjectService service = new ProjectService(projectRepository);

		Project created = service.create("  ReqBridge  ", "  MVP  ");

		assertThat(created.getName()).isEqualTo("ReqBridge");
		assertThat(created.getDescription()).isEqualTo("MVP");
		verify(projectRepository).save(created);
	}

	@Test
	void rejectsBlankProjectName() {
		ProjectService service = new ProjectService(projectRepository);

		assertThatThrownBy(() -> service.create(" ", null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("name");
	}

	@Test
	void listsProjectsInRepositoryOrder() {
		List<Project> projects = List.of(new Project("A", null), new Project("B", null));
		when(projectRepository.findAllByOrderByIdDesc()).thenReturn(projects);
		ProjectService service = new ProjectService(projectRepository);

		assertThat(service.list()).containsExactlyElementsOf(projects);
	}

	@Test
	void rejectsBlankOptionalDescriptionWhenProvided() {
		ProjectService service = new ProjectService(projectRepository);

		assertThatThrownBy(() -> service.create("프로젝트", "  "))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("description");
	}

	@Test
	void appliesTheApiContractWhitespaceSet() {
		when(projectRepository.save(any(Project.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		ProjectService service = new ProjectService(projectRepository);

		Project created = service.create("\u00A0ReqBridge\uFEFF", null);

		assertThat(created.getName()).isEqualTo("ReqBridge");
		assertThatThrownBy(() -> service.create("\u00A0\uFEFF", null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("name");
	}
}
