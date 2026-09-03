package com.sua.reqbridge.project;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProjectController.class)
class ProjectControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProjectService projectService;

	@Test
	void createsProjectWithContractResponseAndLocation() throws Exception {
		Project project = project(1L, "ReqBridge", "설명");
		when(projectService.create("ReqBridge", "설명")).thenReturn(project);

		mockMvc.perform(post("/api/projects")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"name":"ReqBridge","description":"설명"}
							"""))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/projects/1"))
				.andExpect(jsonPath("$.data.id").value(1))
				.andExpect(jsonPath("$.data.name").value("ReqBridge"));
	}

	@Test
	void listsProjectsInsideItemsWrapper() throws Exception {
		Project recentProject = project(2L, "최근", null);
		Project previousProject = project(1L, "이전", null);
		when(projectService.list()).thenReturn(List.of(recentProject, previousProject));

		mockMvc.perform(get("/api/projects"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].id").value(2))
				.andExpect(jsonPath("$.data.items[1].id").value(1));
	}

	@Test
	void rejectsUnknownRequestField() throws Exception {
		mockMvc.perform(post("/api/projects")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"name":"ReqBridge","unknown":true}
							"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}

	@Test
	void rejectsIdsOutsideTheJsonSafeIntegerRange() throws Exception {
		mockMvc.perform(get("/api/projects/9007199254740992"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.error.fieldErrors[0].field").value("projectId"));
	}

	@Test
	void returnsContractErrorForMalformedPathId() throws Exception {
		mockMvc.perform(get("/api/projects/not-a-number"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.error.fieldErrors[0].field").value("projectId"));
	}

	@Test
	void hidesUnexpectedExceptionDetails() throws Exception {
		when(projectService.list()).thenThrow(new RuntimeException("database password must stay hidden"));

		mockMvc.perform(get("/api/projects"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.error.message").value("요청 처리 중 오류가 발생했습니다."));
	}

	@Test
	void mapsMissingResourcesToTheContractResponse() throws Exception {
		when(projectService.get(99L)).thenThrow(new ProjectNotFoundException(99L));

		mockMvc.perform(get("/api/projects/99"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"))
				.andExpect(jsonPath("$.error.fieldErrors").isArray());
	}

	@Test
	void hidesDatabaseConstraintDetailsBehindStateConflict() throws Exception {
		when(projectService.create("ReqBridge", null))
				.thenThrow(new DataIntegrityViolationException("secret constraint name"));

		mockMvc.perform(post("/api/projects")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"name":"ReqBridge"}
							"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("STATE_CONFLICT"))
				.andExpect(jsonPath("$.error.message")
						.value("현재 데이터 상태에서는 요청을 처리할 수 없습니다."));
	}

	@Test
	void returnsContract404ForUnknownApiPath() throws Exception {
		mockMvc.perform(get("/api/not-existing"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
	}

	@Test
	void doesNotTurnUnsupportedMethodsIntoInternalErrors() throws Exception {
		mockMvc.perform(delete("/api/projects/1"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
	}

	private Project project(long id, String name, String description) {
		Project project = mock(Project.class);
		when(project.getId()).thenReturn(id);
		when(project.getName()).thenReturn(name);
		when(project.getDescription()).thenReturn(description);
		when(project.getCreatedAt()).thenReturn(Instant.parse("2026-09-02T06:00:00Z"));
		return project;
	}
}
