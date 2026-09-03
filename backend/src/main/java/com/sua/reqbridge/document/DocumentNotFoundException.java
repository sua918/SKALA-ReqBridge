package com.sua.reqbridge.document;

import com.sua.reqbridge.contract.ResourceNotFoundException;

public class DocumentNotFoundException extends ResourceNotFoundException {

	public DocumentNotFoundException(long documentId) {
		super("Document not found: " + documentId);
	}
}
