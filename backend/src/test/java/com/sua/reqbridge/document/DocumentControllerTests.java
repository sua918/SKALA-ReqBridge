package com.sua.reqbridge.document;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DocumentController.class)
class DocumentControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private DocumentService documentService;

	@Test
	void createsTextDocument() throws Exception {
		Document document = document(101L, 1L, "요구사항", "원문");
		when(documentService.createTextDocument(1L, "요구사항", "원문")).thenReturn(document);

		mockMvc.perform(post("/api/projects/1/documents")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"title":"요구사항","sourceType":"TEXT","content":"원문"}
							"""))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/documents/101"))
				.andExpect(jsonPath("$.data.sourceType").value("TEXT"))
				.andExpect(jsonPath("$.data.content").value("원문"));
	}

	@Test
	void rejectsUnsupportedFileSource() throws Exception {
		mockMvc.perform(post("/api/projects/1/documents")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"title":"파일","sourceType":"FILE","content":"원문"}
							"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}

	@Test
	void listsDocumentSummariesWithoutContent() throws Exception {
		Document document = document(101L, 1L, "요구사항", "숨김");
		when(documentService.listByProject(1L)).thenReturn(List.of(document));

		mockMvc.perform(get("/api/projects/1/documents"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].id").value(101))
				.andExpect(jsonPath("$.data.items[0].content").doesNotExist());
	}

	private Document document(long id, long projectId, String title, String content) {
		Document document = mock(Document.class);
		when(document.getId()).thenReturn(id);
		when(document.getProjectId()).thenReturn(projectId);
		when(document.getTitle()).thenReturn(title);
		when(document.getContent()).thenReturn(content);
		when(document.getSourceType()).thenReturn(DocumentSourceType.TEXT);
		when(document.getCreatedAt()).thenReturn(Instant.parse("2026-09-02T06:00:00Z"));
		return document;
	}
}
