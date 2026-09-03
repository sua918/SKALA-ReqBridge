package com.sua.reqbridge.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.sua.reqbridge.ambiguity.AmbiguityIssue;
import com.sua.reqbridge.ambiguity.AmbiguityIssueRepository;
import com.sua.reqbridge.clarification.Clarification;
import com.sua.reqbridge.clarification.ClarificationRepository;
import com.sua.reqbridge.contract.DocumentSnapshot;
import com.sua.reqbridge.contract.RequirementSnapshot;
import com.sua.reqbridge.contract.RequirementStatus;
import com.sua.reqbridge.contract.CoreRequirementPort;

import tools.jackson.databind.ObjectMapper;

class DocumentAnalysisServiceTests {

	private static final String CONTENT = "시스템은 많은 사용자의 동시 상품 조회 요청에 빠르게 응답해야 한다. "
			+ "부하 시험은 10분 동안 수행하며 성공 응답 비율은 99.9% 이상이어야 한다.";

	@Test
	void completesDocumentAnalysisWithStoredIds() {
		AnalysisRepository analyses = mock(AnalysisRepository.class);
		AmbiguityIssueRepository issues = mock(AmbiguityIssueRepository.class);
		ClarificationRepository clarifications = mock(ClarificationRepository.class);
		CoreRequirementPort core = mock(CoreRequirementPort.class);
		ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
		ObjectMapper json = new ObjectMapper();
		Analysis analysis = Analysis.pendingDocument(101, "{\"documentId\":101}");
		ReflectionTestUtils.setField(analysis, "id", 301L);
		when(analyses.findById(301L)).thenReturn(java.util.Optional.of(analysis));
		when(core.getDocument(101L)).thenReturn(new DocumentSnapshot(101, 1, "성능 요구", CONTENT, "TEXT"));
		when(core.createRequirements(any(Long.class), any(Long.class), any())).thenReturn(List.of(
				new RequirementSnapshot(401, 101, 301, 1, CONTENT,
						RequirementStatus.EXTRACTED, 1, null, null)));

		AtomicLong issueId = new AtomicLong(500);
		when(issues.save(any())).thenAnswer(invocation -> {
			AmbiguityIssue issue = invocation.getArgument(0);
			ReflectionTestUtils.setField(issue, "id", issueId.incrementAndGet());
			return issue;
		});
		AtomicLong clarificationId = new AtomicLong(600);
		when(clarifications.save(any())).thenAnswer(invocation -> {
			Clarification clarification = invocation.getArgument(0);
			ReflectionTestUtils.setField(clarification, "id", clarificationId.incrementAndGet());
			return clarification;
		});

		DocumentAnalysisService service = new DocumentAnalysisService(
				analyses, issues, clarifications, core, events, new MockWorkflowAnalyzer(), json);

		service.executeDocument(301);

		assertThat(analysis.getStatus().name()).isEqualTo("COMPLETED");
		assertThat(analysis.getResult()).isEqualTo(
				"{\"requirementIds\":[401],\"issueIds\":[501,502],\"clarificationIds\":[601,602],"
						+ "\"revisionIds\":[],\"assessment\":null}");
	}

	@Test
	void workerTreatsNonAiIllegalArgumentAsExecutionFailure() {
		DocumentAnalysisService service = mock(DocumentAnalysisService.class);
		doThrow(new IllegalArgumentException("invalid database value")).when(service).executeDocument(301);

		new DocumentAnalysisWorker(service).run(new DocumentAnalysisRequested(301));

		verify(service).fail(301, "ANALYSIS_EXECUTION_FAILED", "분석 실행 중 오류가 발생했습니다.");
	}

	@Test
	void workerRecordsInvalidAiOutput() {
		DocumentAnalysisService service = mock(DocumentAnalysisService.class);
		doThrow(new AiOutputInvalidException("invalid output")).when(service).executeDocument(301);

		new DocumentAnalysisWorker(service).run(new DocumentAnalysisRequested(301));

		verify(service).fail(301, "AI_OUTPUT_INVALID", "분석 결과 형식이 올바르지 않습니다.");
	}

	@Test
	void workerSeparatesUnexpectedExecutionFailure() {
		DocumentAnalysisService service = mock(DocumentAnalysisService.class);
		doThrow(new IllegalStateException("database unavailable")).when(service).executeDocument(301);

		new DocumentAnalysisWorker(service).run(new DocumentAnalysisRequested(301));

		verify(service).fail(301, "ANALYSIS_EXECUTION_FAILED", "분석 실행 중 오류가 발생했습니다.");
	}
}
