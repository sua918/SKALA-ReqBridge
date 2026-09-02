package com.sua.reqbridge.contract;

public interface WorkflowPreviewPort {

	WorkflowPreviewSnapshot getPreview(long documentId);
}
