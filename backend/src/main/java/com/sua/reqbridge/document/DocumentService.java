package com.sua.reqbridge.document;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.sua.reqbridge.common.validation.TextRules;
import com.sua.reqbridge.project.ProjectNotFoundException;
import com.sua.reqbridge.project.ProjectRepository;

@Service
@Transactional(readOnly = true)
public class DocumentService {

	private final DocumentRepository documentRepository;
	private final ProjectRepository projectRepository;

	public DocumentService(DocumentRepository documentRepository, ProjectRepository projectRepository) {
		this.documentRepository = documentRepository;
		this.projectRepository = projectRepository;
	}

	@Transactional
	public Document createTextDocument(long projectId, String title, String content) {
		if (!projectRepository.existsById(projectId)) {
			throw new ProjectNotFoundException(projectId);
		}
		String normalizedTitle = TextRules.requiredTrimmed("Document title", title, 200);
		String preservedContent = TextRules.requiredPreserved("Document content", content, 100_000);

		return documentRepository.save(
				new Document(projectId, normalizedTitle, preservedContent, DocumentSourceType.TEXT));
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
