package com.sua.reqbridge.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.sua.reqbridge.ambiguity.AmbiguityIssueRepository;
import com.sua.reqbridge.clarification.AnswerAnalysisRequested;
import com.sua.reqbridge.clarification.ClarificationRepository;
import com.sua.reqbridge.contract.AnalysisKind;
import com.sua.reqbridge.contract.AnalysisStatus;
import com.sua.reqbridge.contract.CoreRequirementPort;
import com.sua.reqbridge.contract.RequirementSnapshot;
import com.sua.reqbridge.contract.RequirementStatus;
import com.sua.reqbridge.contract.StateConflictException;
import com.sua.reqbridge.revision.RevisionAnalysisRequested;

import tools.jackson.databind.ObjectMapper;

class AnalysisRetryTests {

	private AnalysisRepository analyses;
	private AmbiguityIssueRepository issues;
	private ClarificationRepository clarifications;
	private CoreRequirementPort core;
	private ApplicationEventPublisher events;
	private com.sua.reqbridge.contract.ai.WorkflowAnalyzer analyzer;
	private ObjectMapper json;
	private DocumentAnalysisService service;

	@BeforeEach
	void setUp() {
		analyses = mock(AnalysisRepository.class);
		issues = mock(AmbiguityIssueRepository.class);
		clarifications = mock(ClarificationRepository.class);
		core = mock(CoreRequirementPort.class);
		events = mock(ApplicationEventPublisher.class);
		analyzer = mock(com.sua.reqbridge.contract.ai.WorkflowAnalyzer.class);
		json = new ObjectMapper();
		service = new DocumentAnalysisService(analyses, issues, clarifications, core, events, analyzer, json);
	}

	@Test
	void retryRejectsNonFailedAnalysis() {
		Analysis analysis = Analysis.pendingDocument(101, "{}");
		ReflectionTestUtils.setField(analysis, "id", 301L);
		when(analyses.findById(301L)).thenReturn(Optional.of(analysis));

		assertThatThrownBy(() -> service.retry(301L))
				.isInstanceOf(StateConflictException.class)
				.hasMessageContaining("실패한 작업만");
	}

	@Test
	void retryReturnsExistingDirectRetryIdempotently() {
		Analysis failed = Analysis.pendingAnswer(101, 401, 601, 2, "{}");
		ReflectionTestUtils.setField(failed, "id", 302L);
		failed.fail("AI_OUTPUT_INVALID", "invalid", Instant.now());
		when(analyses.findById(302L)).thenReturn(Optional.of(failed));

		Analysis existingRetry = Analysis.retry(failed);
		ReflectionTestUtils.setField(existingRetry, "id", 305L);
		when(analyses.findFirstByRetryOfAnalysisIdOrderByIdDesc(302L)).thenReturn(Optional.of(existingRetry));

		Analysis result = service.retry(302L);

		assertThat(result.getId()).isEqualTo(305L);
		assertThat(result.getRetryOfAnalysisId()).isEqualTo(302L);
		verify(analyses, never()).save(any());
	}

	@Test
	void retryRejectsWhenRequirementIsConfirmed() {
		Analysis failed = Analysis.pendingAnswer(101, 401, 601, 2, "{}");
		ReflectionTestUtils.setField(failed, "id", 302L);
		failed.fail("AI_OUTPUT_INVALID", "invalid", Instant.now());
		when(analyses.findById(302L)).thenReturn(Optional.of(failed));
		when(analyses.findFirstByRetryOfAnalysisIdOrderByIdDesc(302L)).thenReturn(Optional.empty());

		RequirementSnapshot confirmed = new RequirementSnapshot(
				401, 101, 301, 1, "orig", RequirementStatus.CONFIRMED, 2, 701L, "confirmed");
		when(core.lockRequirement(401)).thenReturn(confirmed);

		assertThatThrownBy(() -> service.retry(302L))
				.isInstanceOf(StateConflictException.class)
				.hasMessageContaining("확정된 요구사항");
	}

	@Test
	void retryRejectsWhenVersionConflictOccurs() {
		Analysis failed = Analysis.pendingAnswer(101, 401, 601, 2, "{}");
		ReflectionTestUtils.setField(failed, "id", 302L);
		failed.fail("AI_OUTPUT_INVALID", "invalid", Instant.now());
		when(analyses.findById(302L)).thenReturn(Optional.of(failed));
		when(analyses.findFirstByRetryOfAnalysisIdOrderByIdDesc(302L)).thenReturn(Optional.empty());

		RequirementSnapshot req = new RequirementSnapshot(
				401, 101, 301, 1, "orig", RequirementStatus.CLARIFYING, 3, null, null);
		when(core.lockRequirement(401)).thenReturn(req);

		assertThatThrownBy(() -> service.retry(302L))
				.isInstanceOf(StateConflictException.class)
				.hasMessageContaining("버전이 일치하지 않습니다");
	}

	@Test
	void retryCreatesNewAnalysisAndPublishesEventForAnswer() {
		Analysis failed = Analysis.pendingAnswer(101, 401, 601, 2, "{\"snapshot\":true}");
		ReflectionTestUtils.setField(failed, "id", 302L);
		failed.fail("AI_OUTPUT_INVALID", "invalid", Instant.now());
		when(analyses.findById(302L)).thenReturn(Optional.of(failed));
		when(analyses.findFirstByRetryOfAnalysisIdOrderByIdDesc(302L)).thenReturn(Optional.empty());

		RequirementSnapshot req = new RequirementSnapshot(
				401, 101, 301, 1, "orig", RequirementStatus.CLARIFYING, 2, null, null);
		when(core.lockRequirement(401)).thenReturn(req);

		when(analyses.save(any(Analysis.class))).thenAnswer(invocation -> {
			Analysis a = invocation.getArgument(0);
			ReflectionTestUtils.setField(a, "id", 306L);
			return a;
		});

		Analysis retried = service.retry(302L);

		assertThat(retried.getId()).isEqualTo(306L);
		assertThat(retried.getRetryOfAnalysisId()).isEqualTo(302L);
		assertThat(retried.getKind()).isEqualTo(AnalysisKind.ANSWER);
		assertThat(retried.getStatus()).isEqualTo(AnalysisStatus.PENDING);
		assertThat(retried.getInputSnapshot()).isEqualTo("{\"snapshot\":true}");
		verify(events).publishEvent(new AnswerAnalysisRequested(306L));
	}

	@Test
	void retryCreatesNewAnalysisAndPublishesEventForRevision() {
		Analysis failed = Analysis.pendingRevision(101, 401, 5, "{\"snapshot\":true}");
		ReflectionTestUtils.setField(failed, "id", 307L);
		failed.fail("AI_OUTPUT_INVALID", "invalid", Instant.now());
		when(analyses.findById(307L)).thenReturn(Optional.of(failed));
		when(analyses.findFirstByRetryOfAnalysisIdOrderByIdDesc(307L)).thenReturn(Optional.empty());

		RequirementSnapshot req = new RequirementSnapshot(
				401, 101, 301, 1, "orig", RequirementStatus.CLARIFYING, 5, null, null);
		when(core.lockRequirement(401)).thenReturn(req);

		when(analyses.save(any(Analysis.class))).thenAnswer(invocation -> {
			Analysis a = invocation.getArgument(0);
			ReflectionTestUtils.setField(a, "id", 308L);
			return a;
		});

		Analysis retried = service.retry(307L);

		assertThat(retried.getId()).isEqualTo(308L);
		assertThat(retried.getRetryOfAnalysisId()).isEqualTo(307L);
		assertThat(retried.getKind()).isEqualTo(AnalysisKind.REVISION);
		verify(events).publishEvent(new RevisionAnalysisRequested(308L));
	}

	@Test
	void controllerRetryReturnsAcceptedWithLocationForPendingRetry() throws Exception {
		DocumentAnalysisService mockService = mock(DocumentAnalysisService.class);
		MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AnalysisController(mockService, json))
				.setControllerAdvice(new ApiExceptionHandler())
				.build();

		Analysis retryAnalysis = Analysis.pendingAnswer(101, 401, 601, 2, "{}");
		ReflectionTestUtils.setField(retryAnalysis, "id", 306L);
		when(mockService.retry(302L)).thenReturn(retryAnalysis);

		mockMvc.perform(post("/api/analyses/302/retries"))
				.andExpect(status().isAccepted())
				.andExpect(header().string("Location", "/api/analyses/306"))
				.andExpect(jsonPath("$.data.id").value(306))
				.andExpect(jsonPath("$.data.status").value("PENDING"));
	}

	@Test
	void controllerRetryReturnsOkForAlreadyFinishedDirectRetry() throws Exception {
		DocumentAnalysisService mockService = mock(DocumentAnalysisService.class);
		MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AnalysisController(mockService, json))
				.setControllerAdvice(new ApiExceptionHandler())
				.build();

		Analysis finishedRetry = Analysis.pendingAnswer(101, 401, 601, 2, "{}");
		ReflectionTestUtils.setField(finishedRetry, "id", 306L);
		finishedRetry.start(Instant.now());
		finishedRetry.complete("{}", Instant.now());
		when(mockService.retry(302L)).thenReturn(finishedRetry);

		mockMvc.perform(post("/api/analyses/302/retries"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(306))
				.andExpect(jsonPath("$.data.status").value("COMPLETED"));
	}

	@Test
	void retryPreservesAdapterTypeAndSchemaVersion() {
		Analysis failed = Analysis.pendingDocument(101, "{}", com.sua.reqbridge.contract.AnalysisAdapterType.LLM, "2.1.0");
		ReflectionTestUtils.setField(failed, "id", 301L);
		failed.start(Instant.now());
		failed.fail("AI_OUTPUT_INVALID", "fail", Instant.now());

		Analysis retried = Analysis.retry(failed);

		assertThat(retried.getAdapterType()).isEqualTo(com.sua.reqbridge.contract.AnalysisAdapterType.LLM);
		assertThat(retried.getSchemaVersion()).isEqualTo("2.1.0");
		assertThat(retried.getRetryOfAnalysisId()).isEqualTo(301L);
	}
}
