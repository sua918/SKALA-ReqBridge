package com.sua.reqbridge.clarification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import tools.jackson.databind.ObjectMapper;

import com.sua.reqbridge.ambiguity.AmbiguityIssue;
import com.sua.reqbridge.ambiguity.AmbiguityIssueRepository;
import com.sua.reqbridge.analysis.Analysis;
import com.sua.reqbridge.analysis.AnalysisRepository;
import com.sua.reqbridge.contract.AmbiguityType;
import com.sua.reqbridge.contract.AnalysisAdapterType;
import com.sua.reqbridge.contract.CoreRequirementPort;
import com.sua.reqbridge.contract.RequirementSnapshot;
import com.sua.reqbridge.contract.RequirementStatus;
import com.sua.reqbridge.contract.ai.AnswerAssessment;
import com.sua.reqbridge.contract.ai.AnswerAssessmentInput;
import com.sua.reqbridge.contract.ai.RevisionGenerationInput;
import com.sua.reqbridge.contract.ai.RevisionProposal;
import com.sua.reqbridge.contract.ai.WorkflowAnalyzer;
import com.sua.reqbridge.revision.RequirementRevision;
import com.sua.reqbridge.revision.RequirementRevisionRepository;
import com.sua.reqbridge.revision.RevisionWorkflowService;

class InputSnapshotExecutionTests {

	private AnalysisRepository analyses;
	private AmbiguityIssueRepository issues;
	private ClarificationRepository clarifications;
	private RequirementRevisionRepository revisions;
	private CoreRequirementPort core;
	private ApplicationEventPublisher events;
	private WorkflowAnalyzer analyzer;
	private ObjectMapper json;

	private AnswerWorkflowService answerService;
	private RevisionWorkflowService revisionService;

	@BeforeEach
	void setUp() {
		analyses = mock(AnalysisRepository.class);
		issues = mock(AmbiguityIssueRepository.class);
		clarifications = mock(ClarificationRepository.class);
		revisions = mock(RequirementRevisionRepository.class);
		core = mock(CoreRequirementPort.class);
		events = mock(ApplicationEventPublisher.class);
		analyzer = mock(WorkflowAnalyzer.class);
		json = new ObjectMapper();

		when(analyzer.adapterType()).thenReturn(AnalysisAdapterType.MOCK);
		when(analyzer.schemaVersion()).thenReturn("1.0.0");

		answerService = new AnswerWorkflowService(
				analyses, issues, clarifications, revisions, core, events, analyzer, json);
		revisionService = new RevisionWorkflowService(
				analyses, issues, clarifications, revisions, core, events, analyzer, json);
	}

	@Test
	@DisplayName("submitAnswer는 완전한 AnswerAssessmentInput을 inputSnapshot으로 저장하고 executeAnswer는 이를 역직렬화하여 사용한다")
	void submitAnswerStoresFullAnswerAssessmentInputInSnapshot() throws Exception {
		long reqId = 100L;
		long clarificationId = 200L;
		long issueId = 300L;

		Clarification clar = Clarification.waiting(reqId, issueId, 1, "동시 사용자는 몇 명인가요?");
		ReflectionTestUtils.setField(clar, "id", clarificationId);
		when(clarifications.findById(clarificationId)).thenReturn(Optional.of(clar));

		AmbiguityIssue issue = AmbiguityIssue.open(reqId, AmbiguityType.QUANTITY_MISSING, "수치 부재");
		ReflectionTestUtils.setField(issue, "id", issueId);
		when(issues.findById(issueId)).thenReturn(Optional.of(issue));

		RequirementSnapshot req = new RequirementSnapshot(
				reqId, 10L, 1L, 1, "동시 요청을 빠르게 처리한다.", RequirementStatus.CLARIFYING, 3L, null, null);
		when(core.lockRequirement(reqId)).thenReturn(req);
		when(core.advanceContentVersion(reqId, 3L)).thenReturn(4L);
		when(analyses.findFirstByClarificationIdAndKindOrderByIdDesc(clarificationId, com.sua.reqbridge.contract.AnalysisKind.ANSWER))
				.thenReturn(Optional.empty());

		ArgumentCaptor<Analysis> analysisCaptor = ArgumentCaptor.forClass(Analysis.class);
		when(analyses.save(analysisCaptor.capture())).thenAnswer(inv -> {
			Analysis a = inv.getArgument(0);
			ReflectionTestUtils.setField(a, "id", 500L);
			return a;
		});

		answerService.submit(clarificationId, "3,000명입니다.", 3L);

		Analysis savedAnalysis = analysisCaptor.getValue();
		assertThat(savedAnalysis.getInputSnapshot()).isNotNull();

		// snapshot이 실제 AnswerAssessmentInput으로 완전히 역직렬화되는지 확인
		AnswerAssessmentInput deserializedInput = json.readValue(
				savedAnalysis.getInputSnapshot(), AnswerAssessmentInput.class);
		assertThat(deserializedInput.requirementId()).isEqualTo(reqId);
		assertThat(deserializedInput.contentVersion()).isEqualTo(4L);
		assertThat(deserializedInput.requirementText()).isEqualTo("동시 요청을 빠르게 처리한다.");
		assertThat(deserializedInput.issueType()).isEqualTo(AmbiguityType.QUANTITY_MISSING);
		assertThat(deserializedInput.answerText()).isEqualTo("3,000명입니다.");

		// executeAnswer 실행 시 analyzer에 전달되는 객체가 snapshot과 동일한지 확인
		when(analyses.findById(500L)).thenReturn(Optional.of(savedAnalysis));
		when(analyzer.assessAnswer(any())).thenReturn(new AnswerAssessment(true, "충분한 수치", null));
		when(issues.countByRequirementIdAndStatus(reqId, com.sua.reqbridge.contract.IssueStatus.OPEN)).thenReturn(1L);

		answerService.executeAnswer(500L);

		ArgumentCaptor<AnswerAssessmentInput> inputCaptor = ArgumentCaptor.forClass(AnswerAssessmentInput.class);
		verify(analyzer).assessAnswer(inputCaptor.capture());
		AnswerAssessmentInput executedInput = inputCaptor.getValue();
		assertThat(executedInput.requirementId()).isEqualTo(deserializedInput.requirementId());
		assertThat(executedInput.answerText()).isEqualTo(deserializedInput.answerText());
		assertThat(executedInput.requirementText()).isEqualTo(deserializedInput.requirementText());
	}

	@Test
	@DisplayName("requestRevision은 완전한 RevisionGenerationInput을 inputSnapshot으로 저장하고 executeRevision은 이를 역직렬화하여 사용한다")
	void requestRevisionStoresFullRevisionGenerationInputInSnapshot() throws Exception {
		long reqId = 100L;
		long docId = 10L;

		RequirementSnapshot req = new RequirementSnapshot(
				reqId, docId, 1L, 1, "동시 요청을 빠르게 처리한다.", RequirementStatus.CLARIFYING, 5L, null, null);
		when(core.lockRequirement(reqId)).thenReturn(req);

		RequirementRevision rejectedRev = RequirementRevision.proposed(reqId, 1, "수정안 1", 4L, List.of(201L));
		rejectedRev.reject("성능 지표가 여전히 모호함");
		when(revisions.findByRequirementIdOrderByRevisionNoDesc(reqId)).thenReturn(List.of(rejectedRev));
		when(revisions.existsByRequirementIdAndStatus(reqId, com.sua.reqbridge.contract.RevisionStatus.REJECTED)).thenReturn(true);
		when(revisions.existsByRequirementIdAndStatus(reqId, com.sua.reqbridge.contract.RevisionStatus.PROPOSED)).thenReturn(false);

		Clarification clar = Clarification.waiting(reqId, 301L, 1, "질문");
		clar.answer("답변 완료");
		ReflectionTestUtils.setField(clar, "id", 201L);
		when(clarifications.findByRequirementIdOrderByIssueIdAscRoundNoAsc(reqId)).thenReturn(List.of(clar));

		ArgumentCaptor<Analysis> analysisCaptor = ArgumentCaptor.forClass(Analysis.class);
		when(analyses.save(analysisCaptor.capture())).thenAnswer(inv -> {
			Analysis a = inv.getArgument(0);
			ReflectionTestUtils.setField(a, "id", 600L);
			return a;
		});

		revisionService.submitRevision(reqId, 5L);

		Analysis savedAnalysis = analysisCaptor.getValue();
		assertThat(savedAnalysis.getInputSnapshot()).isNotNull();

		// snapshot이 RevisionGenerationInput으로 완전히 역직렬화되는지 확인
		RevisionGenerationInput deserializedInput = json.readValue(
				savedAnalysis.getInputSnapshot(), RevisionGenerationInput.class);
		assertThat(deserializedInput.requirementId()).isEqualTo(reqId);
		assertThat(deserializedInput.originalText()).isEqualTo("동시 요청을 빠르게 처리한다.");
		assertThat(deserializedInput.rejectionReason()).isEqualTo("성능 지표가 여전히 모호함");
		assertThat(deserializedInput.clarifications()).hasSize(1);
		assertThat(deserializedInput.clarifications().getFirst().answerText()).isEqualTo("답변 완료");

		// executeRevision 실행 시 analyzer에 전달되는 입력이 snapshot의 것과 일치하는지 확인
		when(analyses.findById(600L)).thenReturn(Optional.of(savedAnalysis));
		when(analyzer.generateRevision(any())).thenReturn(new RevisionProposal("개선된 최종 수정안"));
		when(revisions.save(any())).thenAnswer(inv -> {
			RequirementRevision r = inv.getArgument(0);
			ReflectionTestUtils.setField(r, "id", 700L);
			return r;
		});

		revisionService.executeRevision(600L);

		ArgumentCaptor<RevisionGenerationInput> revInputCaptor = ArgumentCaptor.forClass(RevisionGenerationInput.class);
		verify(analyzer).generateRevision(revInputCaptor.capture());
		RevisionGenerationInput executedInput = revInputCaptor.getValue();
		assertThat(executedInput.requirementId()).isEqualTo(deserializedInput.requirementId());
		assertThat(executedInput.originalText()).isEqualTo(deserializedInput.originalText());
		assertThat(executedInput.rejectionReason()).isEqualTo(deserializedInput.rejectionReason());
	}
}
