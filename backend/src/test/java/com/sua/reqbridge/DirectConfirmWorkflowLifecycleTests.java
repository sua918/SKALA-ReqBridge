package com.sua.reqbridge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.sua.reqbridge.ambiguity.AmbiguityIssue;
import com.sua.reqbridge.ambiguity.AmbiguityIssueRepository;
import com.sua.reqbridge.analysis.Analysis;
import com.sua.reqbridge.analysis.AnalysisRepository;
import com.sua.reqbridge.analysis.MockWorkflowAnalyzer;
import com.sua.reqbridge.clarification.Clarification;
import com.sua.reqbridge.clarification.ClarificationRepository;
import com.sua.reqbridge.contract.AnalysisStatus;
import com.sua.reqbridge.contract.ClarificationStatus;
import com.sua.reqbridge.contract.CoreRequirementPort;
import com.sua.reqbridge.contract.DocumentSnapshot;
import com.sua.reqbridge.contract.IssueStatus;
import com.sua.reqbridge.contract.RequirementSnapshot;
import com.sua.reqbridge.contract.RequirementStatus;
import com.sua.reqbridge.contract.RevisionSource;
import com.sua.reqbridge.contract.RevisionStatus;
import com.sua.reqbridge.contract.StateConflictException;
import com.sua.reqbridge.report.ReportService;
import com.sua.reqbridge.revision.RequirementRevision;
import com.sua.reqbridge.revision.RequirementRevisionRepository;
import com.sua.reqbridge.revision.RevisionWorkflowService;

import tools.jackson.databind.ObjectMapper;

class DirectConfirmWorkflowLifecycleTests {

	private AnalysisRepository analyses;
	private AmbiguityIssueRepository issues;
	private ClarificationRepository clarifications;
	private RequirementRevisionRepository revisions;
	private CoreRequirementPort core;
	private ApplicationEventPublisher events;
	private MockWorkflowAnalyzer analyzer;
	private ObjectMapper json;

	private RevisionWorkflowService revisionService;
	private ReportService reportService;

	private final AtomicLong idSequence = new AtomicLong(1000);
	private final Map<Long, Analysis> analysisStore = new HashMap<>();
	private final Map<Long, AmbiguityIssue> issueStore = new HashMap<>();
	private final Map<Long, Clarification> clarificationStore = new HashMap<>();
	private final Map<Long, RequirementRevision> revisionStore = new HashMap<>();

	private RequirementSnapshot currentRequirement;

	@BeforeEach
	void setUp() {
		analyses = mock(AnalysisRepository.class);
		issues = mock(AmbiguityIssueRepository.class);
		clarifications = mock(ClarificationRepository.class);
		revisions = mock(RequirementRevisionRepository.class);
		core = mock(CoreRequirementPort.class);
		events = mock(ApplicationEventPublisher.class);
		analyzer = new MockWorkflowAnalyzer();
		json = new ObjectMapper();

		setupInMemoRepositories();

		revisionService = new RevisionWorkflowService(
				analyses, issues, clarifications, revisions, core, events, analyzer, json);

		com.sua.reqbridge.contract.WorkflowPreviewPort previewPort = new com.sua.reqbridge.clarification.WorkflowPreviewAdapter(
				core, issues, clarifications, revisions);
		reportService = new ReportService(core, previewPort);
	}

	@Test
	@DisplayName("불명확성이 없는 요구사항은 EXTRACTED 상태에서 원문 그대로 직접 승인되어 DeveloperPreview 확정 목록에 포함된다")
	void directConfirmLifecycleAndPreviewRegression() {
		// 1. Initial State: EXTRACTED requirement without issues/clarifications
		long documentId = 101L;
		long requirementId = 401L;
		String originalText = "관리자는 회원 목록을 Excel 파일로 내보낼 수 있어야 한다.";

		when(core.getDocument(documentId)).thenReturn(new DocumentSnapshot(
				documentId, 1L, "관리자 기능 정의서", originalText, "TEXT"));

		currentRequirement = new RequirementSnapshot(
				requirementId, documentId, 301L, 1, originalText, RequirementStatus.EXTRACTED, 1L, null, null);

		// Verify Preview BEFORE Direct Confirm
		ReportService.DeveloperPreview initialDevPreview = reportService.getDeveloperPreview(documentId);
		assertThat(initialDevPreview.unconfirmedRequirements()).hasSize(1);
		assertThat(initialDevPreview.unconfirmedRequirements().get(0).requirementId()).isEqualTo(requirementId);
		assertThat(initialDevPreview.unconfirmedRequirements().get(0).status()).isEqualTo(RequirementStatus.EXTRACTED);
		assertThat(initialDevPreview.confirmedRequirements()).isEmpty();

		ReportService.CustomerPreview initialCustPreview = reportService.getCustomerPreview(documentId);
		assertThat(initialCustPreview.requirements()).isEmpty();

		// 2. Perform Direct Confirm
		RevisionWorkflowService.ReviewResult confirmResult = revisionService.directConfirm(requirementId, 1L);

		// Assertions on confirm result
		assertThat(confirmResult.requirement().id()).isEqualTo(requirementId);
		assertThat(confirmResult.requirement().status()).isEqualTo(RequirementStatus.CONFIRMED);
		assertThat(confirmResult.requirement().contentVersion()).isEqualTo(1L);
		assertThat(confirmResult.requirement().confirmedText()).isEqualTo(originalText);
		assertThat(confirmResult.requirement().approvedRevisionId()).isNotNull();

		RequirementRevision createdRev = confirmResult.revision();
		assertThat(createdRev.getRequirementId()).isEqualTo(requirementId);
		assertThat(createdRev.getRevisionNo()).isEqualTo(1);
		assertThat(createdRev.getText()).isEqualTo(originalText);
		assertThat(createdRev.getSource()).isEqualTo(RevisionSource.MANUAL);
		assertThat(createdRev.getStatus()).isEqualTo(RevisionStatus.APPROVED);
		assertThat(createdRev.getBasedOnClarificationIds()).isEmpty();
		assertThat(createdRev.getApprovedAt()).isNotNull();
		assertThat(createdRev.getReviewedAt()).isNotNull();

		// 3. Verify Preview AFTER Direct Confirm
		ReportService.DeveloperPreview postDevPreview = reportService.getDeveloperPreview(documentId);
		assertThat(postDevPreview.unconfirmedRequirements()).isEmpty();
		assertThat(postDevPreview.confirmedRequirements()).hasSize(1);

		ReportService.ConfirmedRequirement confirmedReq = postDevPreview.confirmedRequirements().get(0);
		assertThat(confirmedReq.requirementId()).isEqualTo(requirementId);
		assertThat(confirmedReq.originalText()).isEqualTo(originalText);
		assertThat(confirmedReq.approvedRevision().id()).isEqualTo(createdRev.getId());
		assertThat(confirmedReq.approvedRevision().text()).isEqualTo(originalText);
		assertThat(confirmedReq.approvedRevision().status()).isEqualTo("APPROVED");
		assertThat(confirmedReq.evidenceAnswers()).isEmpty();

		ReportService.CustomerPreview postCustPreview = reportService.getCustomerPreview(documentId);
		assertThat(postCustPreview.requirements()).isEmpty();

		// 4. Idempotency test: duplicate direct confirm returns 200 OK without creating another revision
		int revCountBefore = revisionStore.size();
		RevisionWorkflowService.ReviewResult idempotentResult = revisionService.directConfirm(requirementId, 1L);
		assertThat(idempotentResult.requirement().status()).isEqualTo(RequirementStatus.CONFIRMED);
		assertThat(idempotentResult.revision().getId()).isEqualTo(createdRev.getId());
		assertThat(revisionStore.size()).isEqualTo(revCountBefore);

		// 5. Version conflict on already confirmed requirement
		assertThatThrownBy(() -> revisionService.directConfirm(requirementId, 2L))
				.isInstanceOf(StateConflictException.class)
				.satisfies(e -> assertThat(((StateConflictException) e).getCode()).isEqualTo("CONTENT_VERSION_CONFLICT"));
	}

	@Test
	@DisplayName("열린 이슈나 대기 질문, 진행 중 작업이 있는 경우 직접 승인이 거부된다")
	void directConfirmRejectsWhenConditionNotMet() {
		long requirementId = 402L;
		String originalText = "시스템은 빠르게 응답해야 한다.";
		currentRequirement = new RequirementSnapshot(
				requirementId, 101L, 301L, 1, originalText, RequirementStatus.EXTRACTED, 1L, null, null);

		// Open issue exists
		AmbiguityIssue openIssue = AmbiguityIssue.open(requirementId, com.sua.reqbridge.contract.AmbiguityType.PERFORMANCE_MISSING, "빠르게");
		ReflectionTestUtils.setField(openIssue, "id", idSequence.incrementAndGet());
		issueStore.put(openIssue.getId(), openIssue);

		assertThatThrownBy(() -> revisionService.directConfirm(requirementId, 1L))
				.isInstanceOf(StateConflictException.class)
				.satisfies(e -> assertThat(((StateConflictException) e).getCode()).isEqualTo("OPEN_ISSUES_REMAIN"));

		// Resolve issue but waiting clarification exists
		openIssue.resolve();
		Clarification waitingClarification = Clarification.waiting(
				requirementId, openIssue.getId(), 1, "응답 기준은 무엇인가요?");
		ReflectionTestUtils.setField(waitingClarification, "id", idSequence.incrementAndGet());
		clarificationStore.put(waitingClarification.getId(), waitingClarification);

		assertThatThrownBy(() -> revisionService.directConfirm(requirementId, 1L))
				.isInstanceOf(StateConflictException.class)
				.satisfies(e -> assertThat(((StateConflictException) e).getCode()).isEqualTo("OPEN_ISSUES_REMAIN"));

		// Answer clarification but analysis in progress
		ReflectionTestUtils.setField(waitingClarification, "status", ClarificationStatus.ANSWERED);
		Analysis activeAnalysis = Analysis.pendingRevision(101L, requirementId, 1L, "{}");
		ReflectionTestUtils.setField(activeAnalysis, "id", idSequence.incrementAndGet());
		analysisStore.put(activeAnalysis.getId(), activeAnalysis);

		assertThatThrownBy(() -> revisionService.directConfirm(requirementId, 1L))
				.isInstanceOf(StateConflictException.class)
				.satisfies(e -> assertThat(((StateConflictException) e).getCode()).isEqualTo("ANALYSIS_IN_PROGRESS"));

		// Analysis complete but proposed revision exists
		ReflectionTestUtils.setField(activeAnalysis, "status", AnalysisStatus.COMPLETED);
		RequirementRevision proposedRev = RequirementRevision.proposed(requirementId, 1, "개선안", 1L, List.of());
		ReflectionTestUtils.setField(proposedRev, "id", idSequence.incrementAndGet());
		revisionStore.put(proposedRev.getId(), proposedRev);

		assertThatThrownBy(() -> revisionService.directConfirm(requirementId, 1L))
				.isInstanceOf(StateConflictException.class)
				.satisfies(e -> assertThat(((StateConflictException) e).getCode()).isEqualTo("REQUIREMENT_NOT_DIRECTLY_CONFIRMABLE"));
	}

	private void setupInMemoRepositories() {
		when(core.lockRequirement(any(Long.class))).thenAnswer(inv -> currentRequirement);
		when(core.getRequirement(any(Long.class))).thenAnswer(inv -> currentRequirement);
		when(core.listRequirements(any(Long.class))).thenAnswer(inv -> List.of(currentRequirement));

		org.mockito.Mockito.doAnswer(inv -> {
			RequirementStatus target = inv.getArgument(2);
			currentRequirement = new RequirementSnapshot(
					currentRequirement.id(), currentRequirement.documentId(), currentRequirement.analysisId(),
					currentRequirement.sequenceNo(), currentRequirement.originalText(), target,
					currentRequirement.contentVersion(), currentRequirement.approvedRevisionId(), currentRequirement.confirmedText());
			return null;
		}).when(core).changeStatus(any(Long.class), any(Long.class), any(RequirementStatus.class));

		org.mockito.Mockito.doAnswer(inv -> {
			long revId = inv.getArgument(2);
			String approvedText = inv.getArgument(3);
			currentRequirement = new RequirementSnapshot(
					currentRequirement.id(), currentRequirement.documentId(), currentRequirement.analysisId(),
					currentRequirement.sequenceNo(), currentRequirement.originalText(), RequirementStatus.CONFIRMED,
					currentRequirement.contentVersion(), revId, approvedText);
			return null;
		}).when(core).confirmRequirement(any(Long.class), any(Long.class), any(Long.class), any());

		when(analyses.save(any(Analysis.class))).thenAnswer(inv -> {
			Analysis a = inv.getArgument(0);
			if (a.getId() == null) {
				ReflectionTestUtils.setField(a, "id", idSequence.incrementAndGet());
			}
			analysisStore.put(a.getId(), a);
			return a;
		});
		when(analyses.findById(any(Long.class))).thenAnswer(inv -> Optional.ofNullable(analysisStore.get(inv.getArgument(0))));
		when(analyses.existsByRequirementIdAndStatusIn(any(Long.class), any())).thenAnswer(inv -> {
			long reqId = inv.getArgument(0);
			List<AnalysisStatus> statuses = List.of(AnalysisStatus.PENDING, AnalysisStatus.PROCESSING);
			return analysisStore.values().stream()
					.anyMatch(a -> a.getRequirementId() != null && a.getRequirementId().equals(reqId)
							&& statuses.contains(a.getStatus()));
		});

		when(issues.save(any(AmbiguityIssue.class))).thenAnswer(inv -> {
			AmbiguityIssue i = inv.getArgument(0);
			if (i.getId() == null) {
				ReflectionTestUtils.setField(i, "id", idSequence.incrementAndGet());
			}
			issueStore.put(i.getId(), i);
			return i;
		});
		when(issues.findById(any(Long.class))).thenAnswer(inv -> Optional.ofNullable(issueStore.get(inv.getArgument(0))));
		when(issues.countByRequirementIdAndStatus(any(Long.class), eq(IssueStatus.OPEN))).thenAnswer(inv ->
				issueStore.values().stream().filter(i -> i.getStatus() == IssueStatus.OPEN).count());
		when(issues.findByRequirementIdOrderByIdAsc(any(Long.class))).thenAnswer(inv ->
				issueStore.values().stream().sorted(Comparator.comparingLong(AmbiguityIssue::getId)).toList());

		when(clarifications.save(any(Clarification.class))).thenAnswer(inv -> {
			Clarification c = inv.getArgument(0);
			if (c.getId() == null) {
				ReflectionTestUtils.setField(c, "id", idSequence.incrementAndGet());
			}
			clarificationStore.put(c.getId(), c);
			return c;
		});
		when(clarifications.findById(any(Long.class))).thenAnswer(inv -> Optional.ofNullable(clarificationStore.get(inv.getArgument(0))));
		when(clarifications.countByRequirementIdAndStatus(any(Long.class), eq(ClarificationStatus.WAITING))).thenAnswer(inv ->
				clarificationStore.values().stream().filter(c -> c.getStatus() == ClarificationStatus.WAITING).count());
		when(clarifications.findByRequirementIdOrderByIssueIdAscRoundNoAsc(any(Long.class))).thenAnswer(inv ->
				clarificationStore.values().stream().toList());

		when(revisions.save(any(RequirementRevision.class))).thenAnswer(inv -> {
			RequirementRevision r = inv.getArgument(0);
			if (r.getId() == null) {
				ReflectionTestUtils.setField(r, "id", idSequence.incrementAndGet());
			}
			revisionStore.put(r.getId(), r);
			return r;
		});
		when(revisions.findById(any(Long.class))).thenAnswer(inv -> Optional.ofNullable(revisionStore.get(inv.getArgument(0))));
		when(revisions.findTopByRequirementIdOrderByRevisionNoDesc(any(Long.class))).thenAnswer(inv ->
				revisionStore.values().stream().max(Comparator.comparingInt(RequirementRevision::getRevisionNo)));
		when(revisions.findByRequirementIdOrderByRevisionNoDesc(any(Long.class))).thenAnswer(inv ->
				revisionStore.values().stream().sorted((r1, r2) -> Integer.compare(r2.getRevisionNo(), r1.getRevisionNo())).toList());
		when(revisions.existsByRequirementIdAndStatus(any(Long.class), eq(RevisionStatus.PROPOSED))).thenAnswer(inv ->
				revisionStore.values().stream().anyMatch(r -> r.getStatus() == RevisionStatus.PROPOSED));
	}
}
