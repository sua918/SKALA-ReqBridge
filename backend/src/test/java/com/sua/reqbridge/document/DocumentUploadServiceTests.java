package com.sua.reqbridge.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.sua.reqbridge.document.storage.DocumentStorage;
import com.sua.reqbridge.project.ProjectNotFoundException;
import com.sua.reqbridge.project.ProjectService;

@ExtendWith(MockitoExtension.class)
class DocumentUploadServiceTests {
	@Mock ProjectService projects;
	@Mock DocumentStorage storage;
	@Mock DocumentFileWriter writer;
	private DocumentUploadService service;

	@BeforeEach
	void setup() {
		service = new DocumentUploadService(projects, new PdfTextExtractor(), storage, writer);
	}

	@Test
	void storesOriginalBytesAndExtractedContentInOrder() throws Exception {
		byte[] bytes = PdfTestFiles.pdf("Requirement text");
		when(writer.save(any())).thenAnswer(call -> call.getArgument(0));
		Document result = service.upload(1, "  title  ", file("../../client.pdf", "application/pdf", bytes));
		assertThat(result.getTitle()).isEqualTo("title");
		assertThat(result.getContent()).contains("Requirement text");
		assertThat(result.getSourceType()).isEqualTo(DocumentSourceType.FILE);
		assertThat(result.getOriginalFilename()).isEqualTo("../../client.pdf");
		assertThat(result.getStoragePath()).matches("documents/1/[0-9a-f-]{36}\\.pdf");
		assertThat(result.getFileSizeBytes()).isEqualTo(bytes.length);
		assertThat(result.getMimeType()).isEqualTo("application/pdf");
		var order = inOrder(projects, storage, writer);
		order.verify(projects).get(1);
		order.verify(storage).upload(result.getStoragePath(), bytes);
		order.verify(writer).save(result);
	}

	@Test
	void missingProjectDoesNotUpload() {
		when(projects.get(99)).thenThrow(new ProjectNotFoundException(99));
		assertThatThrownBy(() -> service.upload(99, "Title", null))
				.isInstanceOf(ProjectNotFoundException.class);
		verifyNoInteractions(storage, writer);
	}

	@Test
	void rejectsInvalidFilesBeforeStorageOrDb() throws Exception {
		for (MockMultipartFile file : new MockMultipartFile[] {
				file("a.pdf", "application/pdf", new byte[0]),
				file("a.pdf", "text/plain", PdfTestFiles.pdf("Hello")),
				file("a.pdf", "application/pdf", new byte[PdfTextExtractor.MAX_FILE_BYTES + 1]),
				file("a.pdf", "application/pdf", "fake".getBytes()),
				file("a.pdf", "application/pdf", PdfTestFiles.pdf("")) }) {
			assertThatThrownBy(() -> service.upload(1, "Title", file)).isInstanceOf(IllegalArgumentException.class);
		}
		verifyNoInteractions(storage, writer);
	}

	@Test
	void validatesUnicodeTitleBeforeUploading() throws Exception {
		MockMultipartFile file = file("a.pdf", "application/pdf", PdfTestFiles.pdf("Hello"));
		assertThatThrownBy(() -> service.upload(1, "😀".repeat(201), file)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.upload(1, "\u00a0\ufeff", file)).isInstanceOf(IllegalArgumentException.class);
		verifyNoInteractions(storage, writer);
		when(writer.save(any())).thenAnswer(call -> call.getArgument(0));
		assertThat(service.upload(1, "😀".repeat(200), file).getTitle()).isEqualTo("😀".repeat(200));
	}

	@Test
	void storageFailureNeverInsertsRow() throws Exception {
		doThrow(new DocumentUploadException()).when(storage).upload(anyString(), any());
		MockMultipartFile file = file("a.pdf", "application/pdf", PdfTestFiles.pdf("Hello"));
		assertThatThrownBy(() -> service.upload(1, "Title", file)).isInstanceOf(DocumentUploadException.class);
		verifyNoInteractions(writer);
	}

	@Test
	void dbOrCommitFailureDeletesOnlyJustUploadedObject() throws Exception {
		when(writer.save(any())).thenThrow(new IllegalStateException("secret SQL details"));
		MockMultipartFile file = file("a.pdf", "application/pdf", PdfTestFiles.pdf("Hello"));
		assertThatThrownBy(() -> service.upload(1, "Title", file))
				.isInstanceOf(DocumentUploadException.class).hasMessageNotContaining("secret");
		ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
		verify(storage).upload(key.capture(), any());
		verify(storage).delete(key.getValue());
	}

	@Test
	void cleanupFailureDoesNotReplaceDbFailureOrExposeDetails() throws Exception {
		when(writer.save(any())).thenThrow(new IllegalStateException("DB secret"));
		doThrow(new IllegalStateException("Storage secret")).when(storage).delete(anyString());
		MockMultipartFile file = file("a.pdf", "application/pdf", PdfTestFiles.pdf("Hello"));
		assertThatThrownBy(() -> service.upload(1, "Title", file))
				.isInstanceOf(DocumentUploadException.class).hasMessageNotContaining("secret").hasNoCause();
	}

	private MockMultipartFile file(String name, String type, byte[] bytes) {
		return new MockMultipartFile("file", name, type, bytes);
	}
}
