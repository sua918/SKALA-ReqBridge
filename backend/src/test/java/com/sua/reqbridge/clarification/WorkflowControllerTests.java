package com.sua.reqbridge.clarification;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.sua.reqbridge.analysis.Analysis;
import com.sua.reqbridge.analysis.ApiExceptionHandler;

import tools.jackson.databind.ObjectMapper;

class WorkflowControllerTests {

	private AnswerWorkflowService service;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		service = mock(AnswerWorkflowService.class);
		mockMvc = MockMvcBuilders.standaloneSetup(new WorkflowController(service, new ObjectMapper()))
				.setControllerAdvice(new ApiExceptionHandler())
				.build();
	}

	@Test
	void acceptsAnswerAndReturnsAnalysisLocation() throws Exception {
		Analysis analysis = Analysis.pendingAnswer(101, 401, 601, 2, "{}");
		ReflectionTestUtils.setField(analysis, "id", 302L);
		when(service.submit(601, "최대 동시 사용자 3,000명입니다.", 1))
				.thenReturn(new AnswerWorkflowService.AnswerReceipt(601, 401, 2, analysis));

		mockMvc.perform(post("/api/clarifications/601/answers")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"answerText":"최대 동시 사용자 3,000명입니다.","expectedContentVersion":1}
						"""))
				.andExpect(status().isAccepted())
				.andExpect(header().string("Location", "/api/analyses/302"))
				.andExpect(jsonPath("$.data.contentVersion").value(2))
				.andExpect(jsonPath("$.data.analysis.kind").value("ANSWER"));
	}

	@Test
	void returnsCompletedDuplicateWithOk() throws Exception {
		Analysis analysis = Analysis.pendingAnswer(101, 401, 601, 2, "{}");
		ReflectionTestUtils.setField(analysis, "id", 302L);
		analysis.start(Instant.parse("2026-09-03T00:00:00Z"));
		analysis.complete("{\"requirementIds\":[401]}", Instant.parse("2026-09-03T00:00:01Z"));
		when(service.submit(601, "최대 동시 사용자 3,000명입니다.", 1))
				.thenReturn(new AnswerWorkflowService.AnswerReceipt(601, 401, 2, analysis));

		mockMvc.perform(post("/api/clarifications/601/answers")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"answerText":"최대 동시 사용자 3,000명입니다.","expectedContentVersion":1}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.analysis.status").value("COMPLETED"));
	}

	@Test
	void returnsValidationEnvelopeForMissingAnswer() throws Exception {
		doThrow(new IllegalArgumentException("답변을 입력해주세요."))
				.when(service).submit(anyLong(), isNull(), anyLong());

		mockMvc.perform(post("/api/clarifications/601/answers")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"expectedContentVersion\":1}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.error.fieldErrors").isArray());
	}

	@Test
	void rejectsUnknownRequestField() throws Exception {
		mockMvc.perform(post("/api/clarifications/601/answers")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"answerText":"답변","expectedContentVersion":1,"unknown":true}
						"""))
				.andExpect(status().isBadRequest());
	}
}
