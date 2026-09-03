package com.sua.reqbridge.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.sua.reqbridge.contract.DocumentRegisteredEvent;

@Component
@ConditionalOnBean(DocumentAnalysisService.class)
public class DocumentAutoAnalysisListener {

	private static final Logger log = LoggerFactory.getLogger(DocumentAutoAnalysisListener.class);
	private final DocumentAnalysisService analysisService;

	public DocumentAutoAnalysisListener(DocumentAnalysisService analysisService) {
		this.analysisService = analysisService;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onDocumentRegistered(DocumentRegisteredEvent event) {
		try {
			log.info("Triggering automatic document analysis for documentId={}", event.documentId());
			analysisService.submit(event.documentId());
		}
		catch (Exception e) {
			log.warn("Automatic analysis trigger skipped or failed for documentId={}: {}",
					event.documentId(), e.getMessage());
		}
	}
}
