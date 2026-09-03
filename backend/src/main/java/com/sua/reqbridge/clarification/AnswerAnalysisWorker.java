package com.sua.reqbridge.clarification;

import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.sua.reqbridge.analysis.DocumentAnalysisService;

public class AnswerAnalysisWorker {

	private final AnswerWorkflowService service;
	private final DocumentAnalysisService failures;

	public AnswerAnalysisWorker(AnswerWorkflowService service, DocumentAnalysisService failures) {
		this.service = service;
		this.failures = failures;
	}

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void run(AnswerAnalysisRequested request) {
		try {
			service.executeAnswer(request.analysisId());
		}
		catch (RuntimeException exception) {
			failures.fail(request.analysisId(), "AI_OUTPUT_INVALID", "분석 결과 형식이 올바르지 않습니다.");
		}
	}
}
