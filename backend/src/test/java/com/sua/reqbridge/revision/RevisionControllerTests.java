package com.sua.reqbridge.revision;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.sua.reqbridge.analysis.Analysis;
import com.sua.reqbridge.analysis.ApiExceptionHandler;
import com.sua.reqbridge.contract.RequirementSnapshot;
import com.sua.reqbridge.contract.RequirementStatus;

import tools.jackson.databind.ObjectMapper;

class RevisionControllerTests {

	private RevisionWorkflowService service;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		service = mock(RevisionWorkflowService.class);
		mockMvc = MockMvcBuilders.standaloneSetup(new RevisionController(service, new ObjectMapper()))
				.setControllerAdvice(new ApiExceptionHandler())
				.build();
	}

	@Test
	void submitRevisionReturnsAcceptedAndLocationHeader() throws Exception {
		Analysis analysis = Analysis.pendingRevision(101, 401, 5, "{}");
		ReflectionTestUtils.setField(analysis, "id", 307L);
		when(service.submitRevision(401, 5)).thenReturn(analysis);

		mockMvc.perform(post("/api/requirements/401/revisions")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"expectedContentVersion\":5}"))
				.andExpect(status().isAccepted())
				.andExpect(header().string("Location", "/api/analyses/307"))
				.andExpect(jsonPath("$.data.id").value(307))
				.andExpect(jsonPath("$.data.kind").value("REVISION"))
				.andExpect(jsonPath("$.data.status").value("PENDING"))
				.andExpect(jsonPath("$.data.inputContentVersion").value(5));
	}

	@Test
	void submitRevisionRejectsUnknownFields() throws Exception {
		mockMvc.perform(post("/api/requirements/401/revisions")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"expectedContentVersion\":5,\"unknownField\":true}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}

	@Test
	void reviewApproveReturnsOkAndApprovedResult() throws Exception {
		RequirementRevision rev = RequirementRevision.proposed(401, 1, "proposed text", 4, List.of(601L, 602L));
		ReflectionTestUtils.setField(rev, "id", 701L);
		rev.approve();

		RequirementSnapshot req = new RequirementSnapshot(
				401, 101, 301, 1, "orig text", RequirementStatus.CONFIRMED, 4, 701L, "proposed text");

		when(service.review(eq(701L), eq("APPROVE"), eq(null), eq(4L)))
				.thenReturn(new RevisionWorkflowService.ReviewResult(rev, req));

		mockMvc.perform(post("/api/revisions/701/review")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"decision":"APPROVE","expectedContentVersion":4}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.revision.id").value(701))
				.andExpect(jsonPath("$.data.revision.status").value("APPROVED"))
				.andExpect(jsonPath("$.data.requirement.status").value("CONFIRMED"))
				.andExpect(jsonPath("$.data.requirement.approvedRevisionId").value(701))
				.andExpect(jsonPath("$.data.requirement.contentVersion").value(4));
	}

	@Test
	void reviewRejectReturnsOkAndRejectedResultWithIncrementedVersion() throws Exception {
		RequirementRevision rev = RequirementRevision.proposed(401, 1, "proposed text", 4, List.of(601L, 602L));
		ReflectionTestUtils.setField(rev, "id", 701L);
		rev.reject("동시 사용자 수를 최대치로 명확하게 표현해주세요.");

		RequirementSnapshot req = new RequirementSnapshot(
				401, 101, 301, 1, "orig text", RequirementStatus.CLARIFYING, 5, null, null);

		when(service.review(eq(701L), eq("REJECT"), anyString(), eq(4L)))
				.thenReturn(new RevisionWorkflowService.ReviewResult(rev, req));

		mockMvc.perform(post("/api/revisions/701/review")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
							"decision":"REJECT",
							"expectedContentVersion":4,
							"rejectionReason":"동시 사용자 수를 최대치로 명확하게 표현해주세요."
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.revision.id").value(701))
				.andExpect(jsonPath("$.data.revision.status").value("REJECTED"))
				.andExpect(jsonPath("$.data.revision.rejectionReason").value("동시 사용자 수를 최대치로 명확하게 표현해주세요."))
				.andExpect(jsonPath("$.data.requirement.status").value("CLARIFYING"))
				.andExpect(jsonPath("$.data.requirement.contentVersion").value(5));
	}

	@Test
	void reviewRejectsUnknownFields() throws Exception {
		mockMvc.perform(post("/api/revisions/701/review")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"decision":"APPROVE","expectedContentVersion":4,"extra":"invalid"}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}
}
