package com.sua.reqbridge.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.sua.reqbridge.contract.DocumentRegisteredEvent;
import com.sua.reqbridge.document.Document;
import com.sua.reqbridge.document.DocumentFileWriter;
import com.sua.reqbridge.document.DocumentRepository;
import com.sua.reqbridge.document.DocumentService;
import com.sua.reqbridge.document.DocumentUploadService;
import com.sua.reqbridge.document.PdfTextExtractor;
import com.sua.reqbridge.document.storage.DocumentStorage;
import com.sua.reqbridge.project.Project;
import com.sua.reqbridge.project.ProjectRepository;
import com.sua.reqbridge.project.ProjectService;

class DocumentAutoAnalysisListenerTests {

	@Test
	@DisplayName("DocumentRegisteredEvent 수신 시 DocumentAnalysisService.submit이 호출된다")
	void listenerSubmitsDocumentAnalysisOnEvent() {
		DocumentAnalysisService service = mock(DocumentAnalysisService.class);
		DocumentAutoAnalysisListener listener = new DocumentAutoAnalysisListener(service);

		listener.onDocumentRegistered(new DocumentRegisteredEvent(123L));

		verify(service).submit(123L);
	}

	@Test
	@DisplayName("DocumentUploadService는 PDF 업로드 완료 후 DocumentRegisteredEvent를 발행한다")
	void uploadServicePublishesDocumentRegisteredEvent() {
		ProjectService projects = mock(ProjectService.class);
		PdfTextExtractor extractor = mock(PdfTextExtractor.class);
		DocumentStorage storage = mock(DocumentStorage.class);
		DocumentFileWriter writer = mock(DocumentFileWriter.class);
		ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

		when(projects.get(1L)).thenReturn(new Project("Project A", "Desc"));
		when(extractor.extract(any())).thenReturn("Extracted requirements text");
		when(writer.save(any())).thenAnswer(inv -> {
			Document d = inv.getArgument(0);
			ReflectionTestUtils.setField(d, "id", 456L);
			return d;
		});

		DocumentUploadService service = new DocumentUploadService(projects, extractor, storage, writer, events);
		MockMultipartFile file = new MockMultipartFile(
				"file", "demo.pdf", "application/pdf", "dummy pdf content".getBytes());

		Document uploaded = service.upload(1L, "RFP Document", file);

		assertThat(uploaded.getId()).isEqualTo(456L);
		ArgumentCaptor<DocumentRegisteredEvent> eventCaptor = ArgumentCaptor.forClass(DocumentRegisteredEvent.class);
		verify(events).publishEvent(eventCaptor.capture());
		assertThat(eventCaptor.getValue().documentId()).isEqualTo(456L);
	}

	@Test
	@DisplayName("DocumentService는 텍스트 문서 등록 완료 후 DocumentRegisteredEvent를 발행한다")
	void textDocumentServicePublishesDocumentRegisteredEvent() {
		DocumentRepository docs = mock(DocumentRepository.class);
		ProjectRepository projects = mock(ProjectRepository.class);
		ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

		when(projects.existsById(1L)).thenReturn(true);
		when(docs.save(any())).thenAnswer(inv -> {
			Document d = inv.getArgument(0);
			ReflectionTestUtils.setField(d, "id", 789L);
			return d;
		});

		DocumentService service = new DocumentService(docs, projects, events);
		Document created = service.createTextDocument(1L, "Text Req", "Requirement content");

		assertThat(created.getId()).isEqualTo(789L);
		ArgumentCaptor<DocumentRegisteredEvent> eventCaptor = ArgumentCaptor.forClass(DocumentRegisteredEvent.class);
		verify(events).publishEvent(eventCaptor.capture());
		assertThat(eventCaptor.getValue().documentId()).isEqualTo(789L);
	}
}
