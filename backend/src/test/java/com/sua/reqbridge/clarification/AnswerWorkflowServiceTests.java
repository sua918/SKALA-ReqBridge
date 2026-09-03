package com.sua.reqbridge.clarification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;

import com.sua.reqbridge.ambiguity.AmbiguityIssue;
import com.sua.reqbridge.ambiguity.AmbiguityIssueRepository;
import com.sua.reqbridge.analysis.Analysis;
import com.sua.reqbridge.analysis.AnalysisRepository;
import com.sua.reqbridge.analysis.AiOutputInvalidException;
import com.sua.reqbridge.analysis.DocumentAnalysisService;
import com.sua.reqbridge.analysis.MockWorkflowAnalyzer;
import com.sua.reqbridge.contract.AmbiguityType;
import com.sua.reqbridge.contract.ClarificationStatus;
import com.sua.reqbridge.contract.CoreRequirementPort;
import com.sua.reqbridge.contract.RequirementSnapshot;
import com.sua.reqbridge.contract.RequirementStatus;
import com.sua.reqbridge.contract.StateConflictException;
import com.sua.reqbridge.revision.RequirementRevisionRepository;
import com.sua.reqbridge.revision.RequirementRevision;

import tools.jackson.databind.ObjectMapper;

class AnswerWorkflowServiceTests {

	private static final String CONTRACT_WHITESPACE = "\uFEFF\u3000\u205F\u202F\u2029\u2028"
			+ "\u200A\u2000\u1680\u00A0\u0085 \r\n\t";

	@Test
	void answerWorkerSeparatesUnexpectedExecutionFailure() {
		AnswerWorkflowService service = mock(AnswerWorkflowService.class);
		DocumentAnalysisService failures = mock(DocumentAnalysisService.class);
		doThrow(new IllegalStateException("database unavailable")).when(service).executeAnswer(302);

		new AnswerAnalysisWorker(service, failures).run(new AnswerAnalysisRequested(302));

		verify(failures).fail(302, "ANALYSIS_EXECUTION_FAILED", "분석 실행 중 오류가 발생했습니다.");
	}

	@Test
	void answerWorkerTreatsNonAiIllegalArgumentAsExecutionFailure() {
		AnswerWorkflowService service = mock(AnswerWorkflowService.class);
		DocumentAnalysisService failures = mock(DocumentAnalysisService.class);
		doThrow(new IllegalArgumentException("invalid database value")).when(service).executeAnswer(302);

		new AnswerAnalysisWorker(service, failures).run(new AnswerAnalysisRequested(302));

		verify(failures).fail(302, "ANALYSIS_EXECUTION_FAILED", "분석 실행 중 오류가 발생했습니다.");
	}

	@Test
	void answerWorkerRecordsInvalidAiOutput() {
		AnswerWorkflowService service = mock(AnswerWorkflowService.class);
		DocumentAnalysisService failures = mock(DocumentAnalysisService.class);
		doThrow(new AiOutputInvalidException("invalid output")).when(service).executeAnswer(302);

		new AnswerAnalysisWorker(service, failures).run(new AnswerAnalysisRequested(302));

		verify(failures).fail(302, "AI_OUTPUT_INVALID", "분석 결과 형식이 올바르지 않습니다.");
	}

	@Test
	void normalizesContractWhitespaceAndCrLfBeforeSaving() {
		SubmissionContext context = submissionContext();

		context.service().submit(601,
				CONTRACT_WHITESPACE + "많이 접속할 것 같습니다." + CONTRACT_WHITESPACE, 1);

		assertThat(context.clarification().getAnswerText()).isEqualTo("많이 접속할 것 같습니다.");
	}

	@Test
	void rejectsRawAnswerOverCodePointLimitBeforeCrLfNormalization() {
		SubmissionContext context = submissionContext();
		String raw = "a" + "\r\n".repeat(10_000) + "b";

		assertThatThrownBy(() -> context.service().submit(601, raw, 1))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("답변은 1~20000자여야 합니다.");
	}

	@Test
	void acceptsTwentyThousandUnicodeCodePoints() {
		SubmissionContext context = submissionContext();
		String answer = "😀".repeat(20_000);

		context.service().submit(601, answer, 1);

		assertThat(context.clarification().getAnswerText()).isEqualTo(answer);
	}

	@Test
	void reusesSameAnswerWithoutAdvancingVersionAgain() {
		SubmissionContext context = submissionContext();
		var first = context.service().submit(601, "많이 접속할 것 같습니다.", 1);
		when(context.analyses().findFirstByClarificationIdAndKindOrderByIdDesc(any(Long.class), any()))
				.thenReturn(Optional.of(first.analysis()));
		when(context.core().lockRequirement(401L)).thenReturn(new RequirementSnapshot(
				401, 101, 301, 1, "원문", RequirementStatus.CLARIFYING, 2, null, null));

		var duplicate = context.service().submit(601, "많이 접속할 것 같습니다.", 1);

		assertThat(duplicate.analysis()).isSameAs(first.analysis());
		assertThat(duplicate.contentVersion()).isEqualTo(2);
		verify(context.core(), times(1)).advanceContentVersion(401L, 1L);
	}

	@Test
	void rejectsDifferentAnswerAfterSubmission() {
		SubmissionContext context = submissionContext();
		context.service().submit(601, "많이 접속할 것 같습니다.", 1);

		assertThatThrownBy(() -> context.service().submit(601, "최대 3,000명입니다.", 1))
				.isInstanceOfSatisfying(StateConflictException.class,
						exception -> assertThat(exception.getCode()).isEqualTo("ANSWER_ALREADY_SUBMITTED"));
	}

	@Test
	void rejectsStaleContentVersion() {
		SubmissionContext context = submissionContext();

		assertThatThrownBy(() -> context.service().submit(601, "많이 접속할 것 같습니다.", 2))
				.isInstanceOfSatisfying(StateConflictException.class,
						exception -> assertThat(exception.getCode()).isEqualTo("CONTENT_VERSION_CONFLICT"));
	}

	@Test
	void rejectsNonPositiveExpectedContentVersionAsValidationError() {
		SubmissionContext context = submissionContext();

		assertThatThrownBy(() -> context.service().submit(601, "많이 접속할 것 같습니다.", 0))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsAnswerWhileAnalysisIsActive() {
		SubmissionContext context = submissionContext();
		when(context.analyses().existsByRequirementIdAndStatusIn(any(Long.class), any())).thenReturn(true);

		assertThatThrownBy(() -> context.service().submit(601, "많이 접속할 것 같습니다.", 1))
				.isInstanceOfSatisfying(StateConflictException.class,
						exception -> assertThat(exception.getCode()).isEqualTo("ANALYSIS_IN_PROGRESS"));
	}

	@Test
	void storesAnswerAndCreatesNextQuestionWhenInsufficient() {
		AnalysisRepository analyses = mock(AnalysisRepository.class);
		AmbiguityIssueRepository issues = mock(AmbiguityIssueRepository.class);
		ClarificationRepository clarifications = mock(ClarificationRepository.class);
		RequirementRevisionRepository revisions = mock(RequirementRevisionRepository.class);
		CoreRequirementPort core = mock(CoreRequirementPort.class);
		ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

		AmbiguityIssue issue = AmbiguityIssue.open(401, AmbiguityType.QUANTITY_MISSING,
				"많은 사용자의 정량 기준이 없다.");
		ReflectionTestUtils.setField(issue, "id", 501L);
		Clarification clarification = Clarification.waiting(
				401, 501, 1, "부하 시험의 최대 동시 사용자는 몇 명인가요?");
		ReflectionTestUtils.setField(clarification, "id", 601L);
		RequirementSnapshot requirement = new RequirementSnapshot(
				401, 101, 301, 1, "원문", RequirementStatus.CLARIFYING, 1, null, null);

		when(clarifications.findById(601L)).thenReturn(Optional.of(clarification));
		when(issues.findById(501L)).thenReturn(Optional.of(issue));
		when(core.lockRequirement(401L)).thenReturn(requirement);
		when(core.getRequirement(401L)).thenReturn(requirement);
		when(core.advanceContentVersion(401L, 1L)).thenReturn(2L);
		when(analyses.save(any())).thenAnswer(invocation -> {
			Analysis analysis = invocation.getArgument(0);
			ReflectionTestUtils.setField(analysis, "id", 302L);
			return analysis;
		});
		when(analyses.findFirstByClarificationIdAndKindOrderByIdDesc(any(Long.class), any()))
				.thenReturn(Optional.empty());
		when(clarifications.findTopByIssueIdOrderByRoundNoDesc(501L))
				.thenReturn(Optional.of(clarification));
		when(clarifications.save(any())).thenAnswer(invocation -> {
			Clarification saved = invocation.getArgument(0);
			if (saved.getId() == null) {
				ReflectionTestUtils.setField(saved, "id", 603L);
			}
			return saved;
		});

		AnswerWorkflowService service = new AnswerWorkflowService(analyses, issues, clarifications,
				revisions, core, events, new MockWorkflowAnalyzer(), new ObjectMapper());
		AnswerWorkflowService.AnswerReceipt receipt = service.submit(601, "많이 접속할 것 같습니다.", 1);
		when(analyses.findById(302L)).thenReturn(Optional.of(receipt.analysis()));

		service.executeAnswer(302);

		assertThat(clarification.getStatus()).isEqualTo(ClarificationStatus.ANSWERED);
		assertThat(receipt.contentVersion()).isEqualTo(2);
		assertThat(receipt.analysis().getResult()).contains(
				"\"sufficient\":false", "\"nextClarificationId\":603");
	}

	@Test
	void createsRevisionWhenLastIssueIsResolved() {
		AnalysisRepository analyses = mock(AnalysisRepository.class);
		AmbiguityIssueRepository issues = mock(AmbiguityIssueRepository.class);
		ClarificationRepository clarifications = mock(ClarificationRepository.class);
		RequirementRevisionRepository revisions = mock(RequirementRevisionRepository.class);
		CoreRequirementPort core = mock(CoreRequirementPort.class);
		AmbiguityIssue issue = AmbiguityIssue.open(401, AmbiguityType.PERFORMANCE_MISSING, "근거");
		ReflectionTestUtils.setField(issue, "id", 502L);
		Clarification clarification = Clarification.waiting(401, 502, 1, "질문");
		ReflectionTestUtils.setField(clarification, "id", 602L);
		clarification.answer("p95 응답 시간 2초 이하입니다.");
		Analysis analysis = Analysis.pendingAnswer(101, 401, 602, 4, "{}");
		ReflectionTestUtils.setField(analysis, "id", 304L);

		when(analyses.findById(304L)).thenReturn(Optional.of(analysis));
		when(clarifications.findById(602L)).thenReturn(Optional.of(clarification));
		when(issues.findById(502L)).thenReturn(Optional.of(issue));
		when(issues.countByRequirementIdAndStatus(401L, com.sua.reqbridge.contract.IssueStatus.OPEN))
				.thenReturn(0L);
		when(clarifications.findByRequirementIdOrderByIssueIdAscRoundNoAsc(401L))
				.thenReturn(java.util.List.of(clarification));
		when(revisions.findTopByRequirementIdOrderByRevisionNoDesc(401L)).thenReturn(Optional.empty());
		when(revisions.save(any())).thenAnswer(invocation -> {
			RequirementRevision revision = invocation.getArgument(0);
			ReflectionTestUtils.setField(revision, "id", 701L);
			return revision;
		});

		AnswerWorkflowService service = new AnswerWorkflowService(analyses, issues, clarifications,
				revisions, core, mock(ApplicationEventPublisher.class),
				new MockWorkflowAnalyzer(), new ObjectMapper());

		service.executeAnswer(304);

		ArgumentCaptor<RequirementRevision> saved = ArgumentCaptor.forClass(RequirementRevision.class);
		verify(revisions).save(saved.capture());
		assertThat(saved.getValue().getInputContentVersion()).isEqualTo(4);
		assertThat(analysis.getResult()).contains("\"revisionIds\":[701]");
	}

	@Test
	void keepsClarifyingWhenAnotherIssueIsOpen() {
		AnalysisRepository analyses = mock(AnalysisRepository.class);
		AmbiguityIssueRepository issues = mock(AmbiguityIssueRepository.class);
		ClarificationRepository clarifications = mock(ClarificationRepository.class);
		RequirementRevisionRepository revisions = mock(RequirementRevisionRepository.class);
		CoreRequirementPort core = mock(CoreRequirementPort.class);
		AmbiguityIssue issue = AmbiguityIssue.open(401, AmbiguityType.QUANTITY_MISSING, "근거");
		ReflectionTestUtils.setField(issue, "id", 501L);
		Clarification clarification = Clarification.waiting(401, 501, 1, "질문");
		ReflectionTestUtils.setField(clarification, "id", 601L);
		clarification.answer("최대 동시 사용자 3,000명입니다.");
		Analysis analysis = Analysis.pendingAnswer(101, 401, 601, 2, "{}");
		ReflectionTestUtils.setField(analysis, "id", 302L);

		when(analyses.findById(302L)).thenReturn(Optional.of(analysis));
		when(clarifications.findById(601L)).thenReturn(Optional.of(clarification));
		when(issues.findById(501L)).thenReturn(Optional.of(issue));
		when(issues.countByRequirementIdAndStatus(401L, com.sua.reqbridge.contract.IssueStatus.OPEN))
				.thenReturn(1L);
		AnswerWorkflowService service = new AnswerWorkflowService(analyses, issues, clarifications,
				revisions, core, mock(ApplicationEventPublisher.class),
				new MockWorkflowAnalyzer(), new ObjectMapper());

		service.executeAnswer(302);

		assertThat(analysis.getResult()).contains("\"revisionIds\":[]");
		verify(revisions, never()).save(any());
		verify(core, never()).changeStatus(any(Long.class), any(Long.class), any());
	}

	private static SubmissionContext submissionContext() {
		AnalysisRepository analyses = mock(AnalysisRepository.class);
		AmbiguityIssueRepository issues = mock(AmbiguityIssueRepository.class);
		ClarificationRepository clarifications = mock(ClarificationRepository.class);
		RequirementRevisionRepository revisions = mock(RequirementRevisionRepository.class);
		CoreRequirementPort core = mock(CoreRequirementPort.class);
		Clarification clarification = Clarification.waiting(401, 501, 1, "질문");
		ReflectionTestUtils.setField(clarification, "id", 601L);
		RequirementSnapshot requirement = new RequirementSnapshot(
				401, 101, 301, 1, "원문", RequirementStatus.CLARIFYING, 1, null, null);

		when(clarifications.findById(601L)).thenReturn(Optional.of(clarification));
		when(core.lockRequirement(401L)).thenReturn(requirement);
		when(core.advanceContentVersion(401L, 1L)).thenReturn(2L);
		when(analyses.findFirstByClarificationIdAndKindOrderByIdDesc(any(Long.class), any()))
				.thenReturn(Optional.empty());
		when(analyses.save(any())).thenAnswer(invocation -> {
			Analysis analysis = invocation.getArgument(0);
			ReflectionTestUtils.setField(analysis, "id", 302L);
			return analysis;
		});
		return new SubmissionContext(new AnswerWorkflowService(analyses, issues, clarifications,
				revisions, core, mock(ApplicationEventPublisher.class),
				new MockWorkflowAnalyzer(), new ObjectMapper()), clarification, analyses, core);
	}

	private record SubmissionContext(
			AnswerWorkflowService service, Clarification clarification,
			AnalysisRepository analyses, CoreRequirementPort core) {
	}
}
