package com.sua.reqbridge.document;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentFileWriter {
	private final DocumentRepository repository;

	public DocumentFileWriter(DocumentRepository repository) {
		this.repository = repository;
	}

	// The proxy commits before returning to the upload service so commit failures also clean up Storage.
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Document save(Document document) {
		return repository.saveAndFlush(document);
	}
}
