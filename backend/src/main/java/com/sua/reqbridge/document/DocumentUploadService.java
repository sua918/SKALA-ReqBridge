package com.sua.reqbridge.document;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.sua.reqbridge.common.validation.TextRules;
import com.sua.reqbridge.document.storage.DocumentStorage;
import com.sua.reqbridge.project.ProjectService;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;

import com.sua.reqbridge.contract.DocumentRegisteredEvent;

@Service
public class DocumentUploadService {
	private static final Logger log = LoggerFactory.getLogger(DocumentUploadService.class);
	private final ProjectService projects;
	private final PdfTextExtractor extractor;
	private final DocumentStorage storage;
	private final DocumentFileWriter writer;
	private final ApplicationEventPublisher events;

	public DocumentUploadService(ProjectService projects, PdfTextExtractor extractor,
			DocumentStorage storage, DocumentFileWriter writer) {
		this(projects, extractor, storage, writer, null);
	}

	@org.springframework.beans.factory.annotation.Autowired
	public DocumentUploadService(ProjectService projects, PdfTextExtractor extractor,
			DocumentStorage storage, DocumentFileWriter writer,
			@Nullable ApplicationEventPublisher events) {
		this.projects = projects;
		this.extractor = extractor;
		this.storage = storage;
		this.writer = writer;
		this.events = events;
	}

	public Document upload(long projectId, String title, MultipartFile file) {
		projects.get(projectId);
		String normalizedTitle = TextRules.requiredTrimmed("Document title", title, 200);
		if (file == null || file.isEmpty() || file.getSize() > PdfTextExtractor.MAX_FILE_BYTES
				|| !"application/pdf".equalsIgnoreCase(file.getContentType())) {
			throw new IllegalArgumentException("비어 있지 않은 application/pdf 파일 한 개(최대 10MB)를 보내주세요.");
		}
		String filename = file.getOriginalFilename();
		if (filename == null || filename.isBlank() || filename.indexOf('\0') >= 0) {
			throw new IllegalArgumentException("원본 파일명이 필요합니다.");
		}
		byte[] bytes;
		try (InputStream input = file.getInputStream()) {
			bytes = input.readNBytes(PdfTextExtractor.MAX_FILE_BYTES + 1);
		} catch (IOException exception) {
			throw new DocumentUploadException();
		}
		String content = extractor.extract(bytes);
		String objectKey = "documents/" + projectId + "/" + UUID.randomUUID() + ".pdf";
		Document document = Document.fromPdf(projectId, normalizedTitle, content, objectKey, filename, bytes.length);
		storage.upload(objectKey, bytes);
		try {
			Document saved = writer.save(document);
			if (events != null) {
				events.publishEvent(new DocumentRegisteredEvent(saved.getId()));
			}
			return saved;
		} catch (RuntimeException databaseFailure) {
			log.error("FILE document DB write/commit failed (projectId={}, failureType={})",
					projectId, databaseFailure.getClass().getSimpleName());
			try {
				storage.delete(objectKey);
			} catch (RuntimeException cleanupFailure) {
				// Operational reconciliation ID only; no filename, SQL, credential or raw exception.
				log.error("FILE cleanup failed; reconcile projectId={} uploadId={}", projectId,
						objectKey.substring(objectKey.lastIndexOf('/') + 1));
			}
			throw new DocumentUploadException();
		}
	}
}
