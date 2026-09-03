package com.sua.reqbridge.analysis;

import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

public class DocumentAnalysisWorker {

	private final DocumentAnalysisService service;

	public DocumentAnalysisWorker(DocumentAnalysisService service) {
		this.service = service;
	}

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void run(DocumentAnalysisRequested request) {
		try {
			service.executeDocument(request.analysisId());
		}
		catch (RuntimeException exception) {
			service.fail(request.analysisId(), "AI_OUTPUT_INVALID", "분석 결과 형식이 올바르지 않습니다.");
		}
	}
}
