package com.sua.reqbridge.document;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.sua.reqbridge.common.validation.TextRules;
import com.sua.reqbridge.project.ProjectNotFoundException;
import com.sua.reqbridge.project.ProjectRepository;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;

import com.sua.reqbridge.contract.DocumentRegisteredEvent;

@Service
@Transactional(readOnly = true)
public class DocumentService {

	private final DocumentRepository documentRepository;
	private final ProjectRepository projectRepository;
	private final ApplicationEventPublisher events;

	public DocumentService(DocumentRepository documentRepository, ProjectRepository projectRepository) {
		this(documentRepository, projectRepository, null);
	}

	public DocumentService(DocumentRepository documentRepository, ProjectRepository projectRepository,
			@Nullable ApplicationEventPublisher events) {
		this.documentRepository = documentRepository;
		this.projectRepository = projectRepository;
		this.events = events;
	}

	@Transactional
	public Document createTextDocument(long projectId, String title, String content) {
		if (!projectRepository.existsById(projectId)) {
			throw new ProjectNotFoundException(projectId);
		}
		String normalizedTitle = TextRules.requiredTrimmed("Document title", title, 200);
		String preservedContent = TextRules.requiredPreserved("Document content", content, 100_000);

		Document saved = documentRepository.save(
				new Document(projectId, normalizedTitle, preservedContent, DocumentSourceType.TEXT));
		if (events != null) {
			events.publishEvent(new DocumentRegisteredEvent(saved.getId()));
		}
		return saved;
	}

	public Document get(long documentId) {
		return documentRepository.findById(documentId)
				.orElseThrow(() -> new DocumentNotFoundException(documentId));
	}

	public List<Document> listByProject(long projectId) {
		if (!projectRepository.existsById(projectId)) {
			throw new ProjectNotFoundException(projectId);
		}
		return documentRepository.findByProjectIdOrderByIdDesc(projectId);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public Document lock(long documentId) {
		return documentRepository.findByIdForUpdate(documentId)
				.orElseThrow(() -> new DocumentNotFoundException(documentId));
	}
}
