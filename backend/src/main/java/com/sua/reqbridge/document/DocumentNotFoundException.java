package com.sua.reqbridge.document;

public class DocumentNotFoundException extends RuntimeException {

	public DocumentNotFoundException(long documentId) {
		super("Document not found: " + documentId);
	}
}
