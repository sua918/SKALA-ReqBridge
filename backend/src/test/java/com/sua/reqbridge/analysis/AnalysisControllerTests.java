package com.sua.reqbridge.analysis;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.sua.reqbridge.contract.AnalysisKind;

import tools.jackson.databind.ObjectMapper;

class AnalysisControllerTests {

	private DocumentAnalysisService service;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		service = mock(DocumentAnalysisService.class);
		mockMvc = MockMvcBuilders.standaloneSetup(new AnalysisController(service, new ObjectMapper()))
				.setControllerAdvice(new ApiExceptionHandler())
				.build();
	}

	@Test
	void acceptsDocumentAnalysisAndReturnsLocation() throws Exception {
		Analysis analysis = Analysis.pendingDocument(101, "{}");
		ReflectionTestUtils.setField(analysis, "id", 301L);
		when(service.submit(101)).thenReturn(analysis);

		mockMvc.perform(post("/api/documents/101/analyses"))
				.andExpect(status().isAccepted())
				.andExpect(header().string("Location", "/api/analyses/301"))
				.andExpect(jsonPath("$.data.id").value(301))
				.andExpect(jsonPath("$.data.kind").value("DOCUMENT"))
				.andExpect(jsonPath("$.data.result").value((Object) null))
				.andExpect(jsonPath("$.data.error").value((Object) null));
	}

	@Test
	void filtersDocumentHistoryByKind() throws Exception {
		Analysis analysis = Analysis.pendingDocument(101, "{}");
		ReflectionTestUtils.setField(analysis, "id", 301L);
		when(service.list(101, AnalysisKind.DOCUMENT)).thenReturn(List.of(analysis));

		mockMvc.perform(get("/api/documents/101/analyses").queryParam("kind", "DOCUMENT"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].id").value(301));
	}
}
