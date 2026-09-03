package com.sua.reqbridge.document;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@WebMvcTest(DocumentUploadController.class)
class DocumentUploadControllerTests {
	@Autowired MockMvc mvc;
	@MockitoBean DocumentUploadService service;

	@Test
	void returnsExistingDocumentContractAndNoStorageMetadata() throws Exception {
		Document document = mock(Document.class);
		when(document.getId()).thenReturn(102L);
		when(document.getProjectId()).thenReturn(1L);
		when(document.getTitle()).thenReturn("Title");
		when(document.getSourceType()).thenReturn(DocumentSourceType.FILE);
		when(document.getContent()).thenReturn("PDF text");
		when(document.getCreatedAt()).thenReturn(Instant.parse("2026-09-03T00:00:00Z"));
		when(service.upload(eq(1L), eq("Title"), any())).thenReturn(document);
		mvc.perform(multipart("/api/projects/1/documents/upload").file(file()).param("title", "Title"))
				.andExpect(status().isCreated()).andExpect(header().string("Location", "/api/documents/102"))
				.andExpect(jsonPath("$.data.sourceType").value("FILE"))
				.andExpect(jsonPath("$.data.content").value("PDF text"))
				.andExpect(jsonPath("$.data.length()").value(6))
				.andExpect(jsonPath("$.data.storagePath").doesNotExist());
	}

	@Test
	void rejectsMissingUnknownOrRepeatedParts() throws Exception {
		var requests = java.util.List.of(
				multipart("/api/projects/1/documents/upload").param("title", "Title"),
				multipart("/api/projects/1/documents/upload").file(file()),
				multipart("/api/projects/1/documents/upload").file(file()).param("title", "Title").param("sourceType", "FILE"),
				multipart("/api/projects/1/documents/upload").file(file()).file(file()).param("title", "Title"),
				multipart("/api/projects/1/documents/upload").file(file()).param("title", "one", "two"),
				multipart("/api/projects/1/documents/upload").file(file()).param("title", "Title").param("extra", "x"));
		for (var request : requests) {
			mvc.perform(request).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
		}
		verifyNoInteractions(service);
	}

	@Test
	void mapsMultipartSizeFailureTo400Not413Or500() throws Exception {
		when(service.upload(eq(1L), any(), any())).thenThrow(new MaxUploadSizeExceededException(10_485_760));
		mvc.perform(multipart("/api/projects/1/documents/upload").file(file()).param("title", "Title"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}

	@Test
	void mapsStorageFailureToSanitized500() throws Exception {
		when(service.upload(eq(1L), any(), any())).thenThrow(new DocumentUploadException());
		mvc.perform(multipart("/api/projects/1/documents/upload").file(file()).param("title", "Title"))
				.andExpect(status().isInternalServerError()).andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.error.message").value("요청 처리 중 오류가 발생했습니다."));
	}

	private MockMultipartFile file() {
		return new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[] {1});
	}
}
