package com.sua.reqbridge.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.sua.reqbridge.ambiguity.AmbiguityIssue;
import com.sua.reqbridge.ambiguity.AmbiguityIssueRepository;
import com.sua.reqbridge.clarification.AnswerWorkflowService;
import com.sua.reqbridge.clarification.Clarification;
import com.sua.reqbridge.clarification.ClarificationRepository;
import com.sua.reqbridge.contract.*;
import com.sua.reqbridge.contract.ai.AnalyzerTypes.*;
import com.sua.reqbridge.contract.ai.WorkflowAnalyzer;
import com.sua.reqbridge.revision.RequirementRevision;
import com.sua.reqbridge.revision.RequirementRevisionRepository;
import com.sua.reqbridge.revision.RevisionWorkflowService;

import tools.jackson.databind.ObjectMapper;

/** A substitute implementing only the shared interface must work without service changes. */
class WorkflowAnalyzerContractTests {

	private final AnalysisRepository analyses = mock(AnalysisRepository.class);
	private final AmbiguityIssueRepository issues = mock(AmbiguityIssueRepository.class);
	private final ClarificationRepository questions = mock(ClarificationRepository.class);
	private final RequirementRevisionRepository revisions = mock(RequirementRevisionRepository.class);
	private final CoreRequirementPort core = mock(CoreRequirementPort.class);
	private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
	private final WorkflowAnalyzer analyzer = mock(WorkflowAnalyzer.class);
	private final ObjectMapper json = new ObjectMapper();
	private Analysis saved;
	private DocumentAnalysisService documents;
	private AnswerWorkflowService answers;
	private RevisionWorkflowService proposals;

	@BeforeEach
	void setUp() {
		// Test double, not a real LLM call.
		when(analyzer.adapterType()).thenReturn(AnalysisAdapterType.LLM);
		when(analyzer.schemaVersion()).thenReturn("test-2.0");
		when(analyses.save(any())).thenAnswer(invocation -> {
			saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 301L);
			return saved;
		});
		when(analyses.findById(301L)).thenAnswer(invocation -> Optional.of(saved));
		when(revisions.save(any())).thenAnswer(invocation -> {
			RequirementRevision revision = invocation.getArgument(0);
			ReflectionTestUtils.setField(revision, "id", 701L);
			return revision;
		});
		documents = new DocumentAnalysisService(analyses, issues, questions, core, events, analyzer, json);
		answers = new AnswerWorkflowService(analyses, issues, questions, revisions, core, events, analyzer, json);
		proposals = new RevisionWorkflowService(analyses, issues, questions, revisions, core, events, analyzer, json);
	}

	@Test
	void documentUsesSharedResultAndSavedInputWithProviderMetadata() {
		when(core.getDocument(101)).thenReturn(new DocumentSnapshot(101, 1, "제목", "저장할 원문", "TEXT"));
		when(analyzer.analyze(any())).thenReturn(new DocumentResult(
				List.of(new RequirementCandidate(1, "추출된 요구사항", List.of()))));
		when(core.createRequirements(anyLong(), anyLong(), any())).thenReturn(List.of(requirement(1)));
		documents.submit(101);
		assertMetadata();
		assertThat(json.readTree(saved.getInputSnapshot()).properties()).hasSize(3);
		// Execution uses the saved content, not a later read of mutable input.
		when(core.getDocument(101)).thenReturn(new DocumentSnapshot(101, 1, "제목", "다른 원문", "TEXT"));
		documents.executeDocument(301);
		var input = ArgumentCaptor.forClass(DocumentSnapshot.class);
		verify(analyzer).analyze(input.capture());
		assertThat(input.getValue().content()).isEqualTo("저장할 원문");
		assertThat(saved.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
		assertThat(saved.getResult()).isEqualTo("{\"requirementIds\":[401],\"issueIds\":[],"
				+ "\"clarificationIds\":[],\"revisionIds\":[],\"assessment\":null}");
	}

	@Test
	void invalidLaterCandidateIsRejectedBeforeAnyRequirementWrite() {
		when(core.getDocument(101)).thenReturn(new DocumentSnapshot(101, 1, "제목", "원문", "TEXT"));
		when(analyzer.analyze(any())).thenReturn(new DocumentResult(List.of(
				new RequirementCandidate(1, "유효한 원문", List.of()),
				new RequirementCandidate(2, "다음 원문", List.of(new IssueCandidate(null, "근거", "질문"))))));
		documents.submit(101);
		assertThatThrownBy(() -> documents.executeDocument(301)).isInstanceOf(AiOutputInvalidException.class);
		verify(core, never()).createRequirements(anyLong(), anyLong(), any());
		verifyNoInteractions(issues, questions);
	}

	@Test
	void answerPassesOriginalQuestionAndHistoryAndUsesGeneratedRevision() {
		prepareAnswer();
		when(analyzer.assess(any())).thenReturn(new Assessment(true, "명확함", null));
		when(analyzer.generateRevision(any())).thenReturn(new RevisionProposal("교체 분석기가 만든 수정안"));
		answers.submit(601, "고객 답변", 1);
		assertMetadata();
		assertThat(json.readTree(saved.getInputSnapshot()).properties()).hasSize(4);
		answers.executeAnswer(301);
		var assessed = ArgumentCaptor.forClass(AnswerAssessmentInput.class);
		verify(analyzer).assess(assessed.capture());
		assertThat(assessed.getValue().originalText()).isEqualTo("요구사항 원문");
		assertThat(assessed.getValue().questionText()).isEqualTo("정확한 기준은?");
		assertThat(assessed.getValue().answerText()).isEqualTo("고객 답변");
		assertThat(assessed.getValue().ambiguityType()).isEqualTo(AmbiguityType.QUANTITY_MISSING);
		assertThat(assessed.getValue().answers()).extracting(AnswerContext::answerText)
				.containsExactly("불충분했던 답변", "고객 답변");
		var generated = ArgumentCaptor.forClass(RevisionGenerationInput.class);
		verify(analyzer).generateRevision(generated.capture());
		assertThat(generated.getValue().answers()).hasSize(2);
		assertThat(generated.getValue().rejectionReason()).isNull();
		var revision = ArgumentCaptor.forClass(RequirementRevision.class);
		verify(revisions).save(revision.capture());
		assertThat(revision.getValue().getText()).isEqualTo("교체 분석기가 만든 수정안");
		assertThat(revision.getValue().getBasedOnClarificationIds()).containsExactly(600L, 601L);
		verify(core).changeStatus(401, 2, RequirementStatus.IN_REVIEW);
	}

	@Test
	void malformedAssessmentCannotResolveIssueOrGenerateRevision() {
		Clarification question = prepareAnswer();
		when(analyzer.assess(any())).thenReturn(new Assessment(false, "불충분", null));
		answers.submit(601, "고객 답변", 1);
		assertThatThrownBy(() -> answers.executeAnswer(301)).isInstanceOf(AiOutputInvalidException.class);
		assertThat(question.getStatus()).isEqualTo(ClarificationStatus.ANSWERED);
		assertThat(issues.findById(501L).orElseThrow().getStatus()).isEqualTo(IssueStatus.OPEN);
		verify(analyzer, never()).generateRevision(any());
		verify(revisions, never()).save(any());
	}

	@Test
	void staleAnswerIsNotPassedToAnalyzer() {
		prepareAnswer();
		answers.submit(601, "고객 답변", 1);
		when(core.lockRequirement(401)).thenReturn(requirement(3));
		assertThatThrownBy(() -> answers.executeAnswer(301)).isInstanceOfSatisfying(StateConflictException.class,
				error -> assertThat(error.getCode()).isEqualTo("CONTENT_VERSION_CONFLICT"));
		verify(analyzer, never()).assess(any());
	}

	@Test
	void revisionUsesRejectionSnapshotAndPreservesMetadataOnRetry() {
		when(core.lockRequirement(401)).thenReturn(requirement(5));
		when(revisions.existsByRequirementIdAndStatus(401, RevisionStatus.REJECTED)).thenReturn(true);
		RequirementRevision rejected = RequirementRevision.proposed(401, 1, "이전 수정안", 4, List.of(601L));
		rejected.reject("측정 조건을 구체화해주세요.");
		when(revisions.findByRequirementIdOrderByRevisionNoDesc(401)).thenReturn(List.of(rejected));
		when(revisions.findTopByRequirementIdOrderByRevisionNoDesc(401)).thenReturn(Optional.of(rejected));
		when(questions.findByRequirementIdOrderByIssueIdAscRoundNoAsc(401)).thenReturn(List.of(
				question(601, 1, "고객의 정량 답변")));
		when(analyzer.generateRevision(any())).thenReturn(new RevisionProposal("다른 생성 결과"));
		proposals.submitRevision(401, 5);
		assertMetadata();
		assertThat(json.readTree(saved.getInputSnapshot()).properties()).hasSize(4);
		String snapshot = saved.getInputSnapshot();
		saved.fail("AI_OUTPUT_INVALID", "실패", Instant.now());
		Analysis original = saved;
		saved = Analysis.retry(original);
		ReflectionTestUtils.setField(saved, "id", 301L);
		proposals.executeRevision(301);
		var input = ArgumentCaptor.forClass(RevisionGenerationInput.class);
		verify(analyzer).generateRevision(input.capture());
		assertThat(input.getValue().rejectionReason()).isEqualTo("측정 조건을 구체화해주세요.");
		assertThat(input.getValue().previousText()).isEqualTo("이전 수정안");
		assertThat(input.getValue().answers()).hasSize(1);
		assertThat(saved.getInputSnapshot()).isEqualTo(snapshot);
		assertThat(saved.getRetryOfAnalysisId()).isEqualTo(original.getId());
		assertMetadata();
		var revision = ArgumentCaptor.forClass(RequirementRevision.class);
		verify(revisions).save(revision.capture());
		assertThat(revision.getValue().getText()).isEqualTo("다른 생성 결과");
		assertThat(revision.getValue().getRevisionNo()).isEqualTo(2);
	}

	@Test
	void incompatibleAdapterOrSchemaCannotSilentlyExecuteOldJob() {
		for (Analysis pending : List.of(Analysis.pendingDocument(101, "{}"),
				Analysis.pendingDocument(101, "{}", AnalysisAdapterType.LLM, "old-schema"))) {
			saved = pending;
			assertThatThrownBy(() -> documents.executeDocument(301)).isInstanceOf(IllegalStateException.class);
			assertThat(saved.getStatus()).isEqualTo(AnalysisStatus.PENDING);
		}
		verify(analyzer, never()).analyze(any());
	}

	private Clarification prepareAnswer() {
		Clarification question = question(601, 2, null);
		when(questions.findById(601L)).thenReturn(Optional.of(question));
		AmbiguityIssue issue = AmbiguityIssue.open(401, AmbiguityType.QUANTITY_MISSING, "수량 없음");
		ReflectionTestUtils.setField(issue, "id", 501L);
		when(issues.findById(501L)).thenReturn(Optional.of(issue));
		when(core.lockRequirement(401)).thenReturn(requirement(1), requirement(2));
		when(core.advanceContentVersion(401, 1)).thenReturn(2L);
		when(questions.findByRequirementIdOrderByIssueIdAscRoundNoAsc(401))
				.thenReturn(List.of(question(600, 1, "불충분했던 답변"), question));
		return question;
	}

	private static Clarification question(long id, int round, String answer) {
		Clarification question = Clarification.waiting(401, 501, round, "정확한 기준은?");
		ReflectionTestUtils.setField(question, "id", id);
		if (answer != null) question.answer(answer);
		return question;
	}

	private static RequirementSnapshot requirement(long version) {
		return new RequirementSnapshot(401, 101, 301, 1, "요구사항 원문", RequirementStatus.CLARIFYING,
				version, null, null);
	}

	private void assertMetadata() {
		assertThat(saved.getAdapterType()).isEqualTo(AnalysisAdapterType.LLM);
		assertThat(saved.getSchemaVersion()).isEqualTo("test-2.0");
	}
}
