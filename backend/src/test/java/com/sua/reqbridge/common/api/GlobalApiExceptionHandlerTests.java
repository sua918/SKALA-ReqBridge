package com.sua.reqbridge.common.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.sua.reqbridge.analysis.ApiExceptionHandler;
import com.sua.reqbridge.clarification.AnswerWorkflowService;
import com.sua.reqbridge.clarification.WorkflowController;
import com.sua.reqbridge.contract.StateConflictException;
import com.sua.reqbridge.project.ProjectController;
import com.sua.reqbridge.project.ProjectService;

import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PessimisticLockException;
import tools.jackson.databind.ObjectMapper;

class GlobalApiExceptionHandlerTests {
	private ProjectService projects;
	private AnswerWorkflowService answers;
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		projects = mock(ProjectService.class);
		answers = mock(AnswerWorkflowService.class);
		mvc = MockMvcBuilders.standaloneSetup(new ProjectController(projects),
				new WorkflowController(answers, new ObjectMapper()), new RequiredInputController())
				// Deliberately register feature advice first: @Order must still select the common contract.
				.setControllerAdvice(new ApiExceptionHandler(), new GlobalApiExceptionHandler()).build();
	}

	@ParameterizedTest
	@ValueSource(strings = {"/api/projects/not-a-number", "/api/requirements/not-a-number/workflow"})
	void malformedIdsHaveFieldErrorsAcrossCoreAndWorkflow(String path) throws Exception {
		mvc.perform(get(path)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.error.fieldErrors[0].field")
						.value(path.contains("projects") ? "projectId" : "requirementId"))
				.andExpect(content().string(not(containsString("For input string"))));
	}

	@ParameterizedTest
	@ValueSource(strings = {"CONTENT_VERSION_CONFLICT", "REQUIREMENT_CONFIRMED", "ANALYSIS_IN_PROGRESS",
			"DOCUMENT_ALREADY_ANALYZED", "ANSWER_ALREADY_SUBMITTED", "OPEN_ISSUES_EXIST",
			"REVISION_ALREADY_PROPOSED", "REVISION_ALREADY_REVIEWED", "ANALYSIS_NOT_RETRYABLE",
			"PREVIEW_VERSION_CONFLICT", "STATE_CONFLICT"})
	void preservesEveryPublicConflictCodeAcrossBothAdvices(String code) throws Exception {
		when(projects.get(1L)).thenThrow(new StateConflictException(code, "업무 충돌"));
		when(answers.workflow(1L)).thenThrow(new StateConflictException(code, "업무 충돌"));
		for (String path : new String[]{"/api/projects/1", "/api/requirements/1/workflow"}) {
			mvc.perform(get(path)).andExpect(status().isConflict())
					.andExpect(jsonPath("$.error.code").value(code))
					.andExpect(jsonPath("$.error.fieldErrors").isArray());
		}
	}

	@ParameterizedTest
	@MethodSource("lockFailures")
	void databaseConcurrencyErrorsAreSafeConflicts(RuntimeException failure) throws Exception {
		when(projects.get(1L)).thenThrow(failure);
		mvc.perform(get("/api/projects/1")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("STATE_CONFLICT"))
				.andExpect(content().string(not(containsString("secret sql"))));
	}

	static Stream<RuntimeException> lockFailures() {
		return Stream.of(new OptimisticLockingFailureException("secret sql"),
				new CannotAcquireLockException("secret sql"), new OptimisticLockException("secret sql"),
				new PessimisticLockException("secret sql"), new LockTimeoutException("secret sql"));
	}

	@Test
	void unexpectedErrorsWithValidationCausesRemainSanitizedServerErrors() throws Exception {
		when(answers.workflow(1L)).thenThrow(new IllegalStateException("secret sql",
				new IllegalArgumentException("secret credential")));
		mvc.perform(get("/api/requirements/1/workflow")).andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
				.andExpect(content().string(not(containsString("secret"))));
	}

	@Test
	void malformedWorkflowJsonDoesNotExposeParserMessages() throws Exception {
		mvc.perform(post("/api/clarifications/1/answers").contentType(MediaType.APPLICATION_JSON).content("{"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.error.message").value("요청 JSON 형식 또는 값이 올바르지 않습니다."));
	}

	@Test
	void missingParameterIsAFieldValidationError() throws Exception {
		mvc.perform(get("/test/required")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fieldErrors[0].field").value("filter"));
	}

	@Test
	void missingMultipartPartIsAFieldValidationError() throws Exception {
		mvc.perform(multipart("/test/required-file")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fieldErrors[0].field").value("file"));
	}

	@RestController
	static class RequiredInputController {
		@GetMapping("/test/required")
		String query(@RequestParam String filter) { return filter; }

		@PostMapping("/test/required-file")
		String file(@RequestPart MultipartFile file) { return file.getName(); }
	}
}
