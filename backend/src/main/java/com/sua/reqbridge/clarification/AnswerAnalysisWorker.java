package com.sua.reqbridge.clarification;

import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.sua.reqbridge.analysis.DocumentAnalysisService;
import com.sua.reqbridge.analysis.AiOutputInvalidException;

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
		catch (AiOutputInvalidException exception) {
			failures.fail(request.analysisId(), "AI_OUTPUT_INVALID", "분석 결과 형식이 올바르지 않습니다.");
		}
		catch (RuntimeException exception) {
			failures.fail(request.analysisId(), "ANALYSIS_EXECUTION_FAILED", "분석 실행 중 오류가 발생했습니다.");
		}
	}
}
