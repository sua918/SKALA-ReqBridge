package com.sua.reqbridge.report;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.sua.reqbridge.common.api.GlobalApiExceptionHandler;
import com.sua.reqbridge.contract.AmbiguityType;
import com.sua.reqbridge.contract.RequirementStatus;
import com.sua.reqbridge.contract.ResourceNotFoundException;
import com.sua.reqbridge.contract.StateConflictException;

class ReportControllerTests {

	private ReportService service;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		service = mock(ReportService.class);
		ReportController controller = new ReportController(service);
		mockMvc = MockMvcBuilders.standaloneSetup(controller)
				.setControllerAdvice(new GlobalApiExceptionHandler())
				.build();
	}

	@Test
	void getCustomerPreviewReturnsOkWithDataEnvelope() throws Exception {
		ReportService.PreviewSummary summary = new ReportService.PreviewSummary(1, 0, 1, 1);
		ReportService.PreviewBasis basis = new ReportService.PreviewBasis(401L, 1L, null);
		ReportService.CustomerQuestion q = new ReportService.CustomerQuestion(
				601L, 501L, AmbiguityType.QUANTITY_MISSING, "정량 기준 누락", 1, "질문 1?");
		ReportService.CustomerRequirement req = new ReportService.CustomerRequirement(
				401L, 1, "원문", 1L, List.of(q));
		ReportService.CustomerPreview preview = new ReportService.CustomerPreview(
				101L, "테스트 문서", Instant.now(), summary, List.of(basis), List.of(req));

		when(service.getCustomerPreview(101L)).thenReturn(preview);

		mockMvc.perform(get("/api/documents/101/previews/customer"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.documentId").value(101))
				.andExpect(jsonPath("$.data.documentTitle").value("테스트 문서"))
				.andExpect(jsonPath("$.data.summary.totalRequirements").value(1))
				.andExpect(jsonPath("$.data.basis[0].requirementId").value(401))
				.andExpect(jsonPath("$.data.requirements[0].questions[0].id").value(601));
	}

	@Test
	void getDeveloperPreviewReturnsOkWithDataEnvelope() throws Exception {
		ReportService.PreviewSummary summary = new ReportService.PreviewSummary(1, 1, 0, 0);
		ReportService.PreviewBasis basis = new ReportService.PreviewBasis(401L, 4L, 701L);
		ReportService.RevisionDetail rev = new ReportService.RevisionDetail(
				701L, 401L, 1, "수정안 텍스트", "APPROVED", 4L, List.of(601L), null, List.of());
		ReportService.ConfirmedRequirement confirmed = new ReportService.ConfirmedRequirement(
				401L, 1, "원문", 4L, rev, List.of());
		ReportService.DeveloperPreview preview = new ReportService.DeveloperPreview(
				101L, "테스트 문서", Instant.now(), summary, List.of(basis), List.of(confirmed), List.of());

		when(service.getDeveloperPreview(101L)).thenReturn(preview);

		mockMvc.perform(get("/api/documents/101/previews/developer"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.documentId").value(101))
				.andExpect(jsonPath("$.data.confirmedRequirements[0].approvedRevision.id").value(701))
				.andExpect(jsonPath("$.data.confirmedRequirements[0].approvedRevision.status").value("APPROVED"));
	}

	@Test
	void getCustomerPreviewReturnsNotFoundWhenDocumentDoesNotExist() throws Exception {
		when(service.getCustomerPreview(999L)).thenThrow(new ResourceNotFoundException("문서를 찾을 수 없습니다."));

		mockMvc.perform(get("/api/documents/999/previews/customer"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
	}

	@Test
	void getDeveloperPreviewReturnsConflictWhenVersionConflicts() throws Exception {
		when(service.getDeveloperPreview(101L))
				.thenThrow(new StateConflictException("PREVIEW_VERSION_CONFLICT", "버전 불일치"));

		mockMvc.perform(get("/api/documents/101/previews/developer"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("PREVIEW_VERSION_CONFLICT"));
	}
}
