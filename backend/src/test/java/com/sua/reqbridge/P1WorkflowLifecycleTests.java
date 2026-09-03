package com.sua.reqbridge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.sua.reqbridge.ambiguity.AmbiguityIssue;
import com.sua.reqbridge.ambiguity.AmbiguityIssueRepository;
import com.sua.reqbridge.analysis.Analysis;
import com.sua.reqbridge.analysis.AnalysisRepository;
import com.sua.reqbridge.analysis.DocumentAnalysisService;
import com.sua.reqbridge.analysis.MockWorkflowAnalyzer;
import com.sua.reqbridge.clarification.AnswerWorkflowService;
import com.sua.reqbridge.clarification.Clarification;
import com.sua.reqbridge.clarification.ClarificationRepository;
import com.sua.reqbridge.contract.AnalysisKind;
import com.sua.reqbridge.contract.AnalysisStatus;
import com.sua.reqbridge.contract.ClarificationStatus;
import com.sua.reqbridge.contract.CoreRequirementPort;
import com.sua.reqbridge.contract.DocumentSnapshot;
import com.sua.reqbridge.contract.IssueStatus;
import com.sua.reqbridge.contract.RequirementSeed;
import com.sua.reqbridge.contract.RequirementSnapshot;
import com.sua.reqbridge.contract.RequirementStatus;
import com.sua.reqbridge.contract.RevisionStatus;
import com.sua.reqbridge.revision.RequirementRevision;
import com.sua.reqbridge.revision.RequirementRevisionRepository;
import com.sua.reqbridge.revision.RevisionWorkflowService;

import tools.jackson.databind.ObjectMapper;

class P1WorkflowLifecycleTests {

	private AnalysisRepository analyses;
	private AmbiguityIssueRepository issues;
	private ClarificationRepository clarifications;
	private RequirementRevisionRepository revisions;
	private CoreRequirementPort core;
	private ApplicationEventPublisher events;
	private MockWorkflowAnalyzer analyzer;
	private ObjectMapper json;

	private DocumentAnalysisService documentService;
	private AnswerWorkflowService answerService;
	private RevisionWorkflowService revisionService;
	private com.sua.reqbridge.report.ReportService reportService;

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

		documentService = new DocumentAnalysisService(
				analyses, issues, clarifications, core, events, analyzer, json);
		answerService = new AnswerWorkflowService(
				analyses, issues, clarifications, revisions, core, events, analyzer, json);
		revisionService = new RevisionWorkflowService(
				analyses, issues, clarifications, revisions, core, events, json);

		com.sua.reqbridge.contract.WorkflowPreviewPort previewPort = new com.sua.reqbridge.clarification.WorkflowPreviewAdapter(
				core, issues, clarifications, revisions);
		reportService = new com.sua.reqbridge.report.ReportService(core, previewPort);
	}

	@Test
	void completeP1WorkflowFromDocumentAnalysisToRejectionRegenerationAndApproval() {
		// 1. Initial State: Document 101 available
		String documentContent = "시스템은 많은 사용자의 동시 상품 조회 요청에 빠르게 응답해야 한다. "
				+ "부하 시험은 10분 동안 수행하며 성공 응답 비율은 99.9% 이상이어야 한다.";
		when(core.getDocument(101L)).thenReturn(new DocumentSnapshot(
				101L, 1L, "성능 요건서", documentContent, "TEXT"));

		currentRequirement = new RequirementSnapshot(
				401L, 101L, 301L, 1, documentContent, RequirementStatus.EXTRACTED, 1L, null, null);

		when(core.createRequirements(eq(101L), any(Long.class), any())).thenAnswer(inv -> {
			List<RequirementSeed> seeds = inv.getArgument(2);
			return List.of(new RequirementSnapshot(
					401L, 101L, inv.getArgument(1), seeds.get(0).sequenceNo(),
					seeds.get(0).originalText(), RequirementStatus.EXTRACTED, 1L, null, null));
		});

		// 2. Submit & Execute Document Analysis
		Analysis docAnalysis = documentService.submit(101L);
		documentService.executeDocument(docAnalysis.getId());

		assertThat(analysisStore.get(docAnalysis.getId()).getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
		assertThat(currentRequirement.status()).isEqualTo(RequirementStatus.CLARIFYING);
		assertThat(currentRequirement.contentVersion()).isEqualTo(1L);
		assertThat(issueStore.values()).hasSize(2);
		assertThat(clarificationStore.values()).hasSize(2);

		AmbiguityIssue issue501 = issueStore.values().stream()
				.filter(i -> i.getEvidence().contains("정량 기준")).findFirst().orElseThrow();
		Clarification clar601 = clarificationStore.values().stream()
				.filter(c -> c.getIssueId().equals(issue501.getId())).findFirst().orElseThrow();

		AmbiguityIssue issue502 = issueStore.values().stream()
				.filter(i -> i.getEvidence().contains("응답 시간")).findFirst().orElseThrow();
		Clarification clar602 = clarificationStore.values().stream()
				.filter(c -> c.getIssueId().equals(issue502.getId())).findFirst().orElseThrow();

		// Verify P2 Customer Preview at initial CLARIFYING stage
		com.sua.reqbridge.report.ReportService.CustomerPreview initialCustomerPreview =
				reportService.getCustomerPreview(101L);
		assertThat(initialCustomerPreview.summary().openIssueCount()).isEqualTo(2);
		assertThat(initialCustomerPreview.summary().waitingQuestionCount()).isEqualTo(2);
		assertThat(initialCustomerPreview.requirements()).hasSize(1);
		assertThat(initialCustomerPreview.requirements().get(0).questions()).hasSize(2);

		// 3. Insufficient Answer for issue 501 ("많이 접속할 것 같습니다.")
		AnswerWorkflowService.AnswerReceipt answerReceipt1 = answerService.submit(
				clar601.getId(), "많이 접속할 것 같습니다.", 1L);
		assertThat(currentRequirement.contentVersion()).isEqualTo(2L);
		answerService.executeAnswer(answerReceipt1.analysis().getId());

		assertThat(issue501.getStatus()).isEqualTo(IssueStatus.OPEN);
		// A new round clarification was generated
		Clarification clar603 = clarificationStore.values().stream()
				.filter(c -> c.getIssueId().equals(issue501.getId()) && c.getRoundNo() == 2)
				.findFirst().orElseThrow();
		assertThat(clar603.getStatus()).isEqualTo(ClarificationStatus.WAITING);

		// 4. Resolve issue 501 ("최대 동시 사용자 3,000명입니다.")
		AnswerWorkflowService.AnswerReceipt answerReceipt2 = answerService.submit(
				clar603.getId(), "최대 동시 사용자 3,000명입니다.", 2L);
		assertThat(currentRequirement.contentVersion()).isEqualTo(3L);
		answerService.executeAnswer(answerReceipt2.analysis().getId());

		assertThat(issue501.getStatus()).isEqualTo(IssueStatus.RESOLVED);
		assertThat(issue502.getStatus()).isEqualTo(IssueStatus.OPEN);
		assertThat(currentRequirement.status()).isEqualTo(RequirementStatus.CLARIFYING);

		// 5. Resolve issue 502 ("p95 응답 시간 2초 이하입니다.") -> Last issue resolved -> Revision proposed!
		AnswerWorkflowService.AnswerReceipt answerReceipt3 = answerService.submit(
				clar602.getId(), "p95 응답 시간 2초 이하입니다.", 3L);
		assertThat(currentRequirement.contentVersion()).isEqualTo(4L);
		answerService.executeAnswer(answerReceipt3.analysis().getId());

		assertThat(issue502.getStatus()).isEqualTo(IssueStatus.RESOLVED);
		assertThat(revisionStore).hasSize(1);
		RequirementRevision rev1 = revisionStore.values().stream()
				.filter(r -> r.getRevisionNo() == 1).findFirst().orElseThrow();
		assertThat(rev1.getStatus()).isEqualTo(RevisionStatus.PROPOSED);
		assertThat(rev1.getInputContentVersion()).isEqualTo(4L);
		assertThat(currentRequirement.status()).isEqualTo(RequirementStatus.IN_REVIEW);

		// 6. Review REJECT rev1 (v4 -> v5)
		RevisionWorkflowService.ReviewResult rejectResult = revisionService.review(
				rev1.getId(), "REJECT", "동시 사용자와 지연 시간 기준을 더 엄격히 해주세요.", 4L);

		assertThat(rejectResult.revision().getStatus()).isEqualTo(RevisionStatus.REJECTED);
		assertThat(rejectResult.revision().getRejectionReason()).isEqualTo("동시 사용자와 지연 시간 기준을 더 엄격히 해주세요.");
		assertThat(currentRequirement.status()).isEqualTo(RequirementStatus.CLARIFYING);
		assertThat(currentRequirement.contentVersion()).isEqualTo(5L); // incremented!

		// 7. Regenerate Revision at v5
		Analysis regenAnalysis = revisionService.submitRevision(401L, 5L);
		assertThat(regenAnalysis.getKind()).isEqualTo(AnalysisKind.REVISION);
		assertThat(regenAnalysis.getStatus()).isEqualTo(AnalysisStatus.PENDING);
		assertThat(regenAnalysis.getInputContentVersion()).isEqualTo(5L);

		revisionService.executeRevision(regenAnalysis.getId());

		assertThat(revisionStore).hasSize(2);
		RequirementRevision rev2 = revisionStore.values().stream()
				.filter(r -> r.getRevisionNo() == 2).findFirst().orElseThrow();
		assertThat(rev2.getStatus()).isEqualTo(RevisionStatus.PROPOSED);
		assertThat(rev2.getInputContentVersion()).isEqualTo(5L);
		assertThat(currentRequirement.status()).isEqualTo(RequirementStatus.IN_REVIEW);
		assertThat(currentRequirement.contentVersion()).isEqualTo(5L);

		// 8. Review APPROVE rev2 at v5
		RevisionWorkflowService.ReviewResult approveResult = revisionService.review(
				rev2.getId(), "APPROVE", null, 5L);

		assertThat(approveResult.revision().getStatus()).isEqualTo(RevisionStatus.APPROVED);
		assertThat(currentRequirement.status()).isEqualTo(RequirementStatus.CONFIRMED);
		assertThat(currentRequirement.approvedRevisionId()).isEqualTo(rev2.getId());
		assertThat(currentRequirement.confirmedText()).isEqualTo(rev2.getText());
		assertThat(currentRequirement.contentVersion()).isEqualTo(5L); // preserved!

		// 9. Verify P2 Developer Preview at final CONFIRMED stage
		com.sua.reqbridge.report.ReportService.DeveloperPreview devPreview =
				reportService.getDeveloperPreview(101L);
		assertThat(devPreview.summary().confirmedRequirements()).isEqualTo(1);
		assertThat(devPreview.summary().openIssueCount()).isEqualTo(0);
		assertThat(devPreview.summary().waitingQuestionCount()).isEqualTo(0);
		assertThat(devPreview.confirmedRequirements()).hasSize(1);
		assertThat(devPreview.confirmedRequirements().get(0).approvedRevision().id()).isEqualTo(rev2.getId());
		assertThat(devPreview.confirmedRequirements().get(0).evidenceAnswers()).hasSize(3);
		assertThat(devPreview.unconfirmedRequirements()).isEmpty();

		// Customer preview at final CONFIRMED stage has no pending questions
		com.sua.reqbridge.report.ReportService.CustomerPreview finalCustomerPreview =
				reportService.getCustomerPreview(101L);
		assertThat(finalCustomerPreview.requirements()).isEmpty();
	}

	private void setupInMemoRepositories() {
		when(core.lockRequirement(401L)).thenAnswer(inv -> currentRequirement);
		when(core.getRequirement(401L)).thenAnswer(inv -> currentRequirement);
		when(core.listRequirements(101L)).thenAnswer(inv -> List.of(currentRequirement));

		when(core.advanceContentVersion(eq(401L), any(Long.class))).thenAnswer(inv -> {
			long expected = inv.getArgument(1);
			assertThat(currentRequirement.contentVersion()).isEqualTo(expected);
			long next = expected + 1;
			currentRequirement = new RequirementSnapshot(
					currentRequirement.id(), currentRequirement.documentId(), currentRequirement.analysisId(),
					currentRequirement.sequenceNo(), currentRequirement.originalText(), currentRequirement.status(),
					next, currentRequirement.approvedRevisionId(), currentRequirement.confirmedText());
			return next;
		});

		org.mockito.Mockito.doAnswer(inv -> {
			RequirementStatus target = inv.getArgument(2);
			currentRequirement = new RequirementSnapshot(
					currentRequirement.id(), currentRequirement.documentId(), currentRequirement.analysisId(),
					currentRequirement.sequenceNo(), currentRequirement.originalText(), target,
					currentRequirement.contentVersion(), currentRequirement.approvedRevisionId(), currentRequirement.confirmedText());
			return null;
		}).when(core).changeStatus(eq(401L), any(Long.class), any(RequirementStatus.class));

		org.mockito.Mockito.doAnswer(inv -> {
			long revId = inv.getArgument(2);
			String approvedText = inv.getArgument(3);
			currentRequirement = new RequirementSnapshot(
					currentRequirement.id(), currentRequirement.documentId(), currentRequirement.analysisId(),
					currentRequirement.sequenceNo(), currentRequirement.originalText(), RequirementStatus.CONFIRMED,
					currentRequirement.contentVersion(), revId, approvedText);
			return null;
		}).when(core).confirmRequirement(eq(401L), any(Long.class), any(Long.class), any());

		when(analyses.save(any(Analysis.class))).thenAnswer(inv -> {
			Analysis a = inv.getArgument(0);
			if (a.getId() == null) {
				ReflectionTestUtils.setField(a, "id", idSequence.incrementAndGet());
			}
			analysisStore.put(a.getId(), a);
			return a;
		});
		when(analyses.findById(any(Long.class))).thenAnswer(inv -> Optional.ofNullable(analysisStore.get(inv.getArgument(0))));

		when(issues.save(any(AmbiguityIssue.class))).thenAnswer(inv -> {
			AmbiguityIssue i = inv.getArgument(0);
			if (i.getId() == null) {
				ReflectionTestUtils.setField(i, "id", idSequence.incrementAndGet());
			}
			issueStore.put(i.getId(), i);
			return i;
		});
		when(issues.findById(any(Long.class))).thenAnswer(inv -> Optional.ofNullable(issueStore.get(inv.getArgument(0))));
		when(issues.countByRequirementIdAndStatus(eq(401L), eq(IssueStatus.OPEN))).thenAnswer(inv ->
				issueStore.values().stream().filter(i -> i.getStatus() == IssueStatus.OPEN).count());
		when(issues.findByRequirementIdOrderByIdAsc(eq(401L))).thenAnswer(inv ->
				issueStore.values().stream()
						.sorted((i1, i2) -> Long.compare(i1.getId(), i2.getId()))
						.toList());

		when(clarifications.save(any(Clarification.class))).thenAnswer(inv -> {
			Clarification c = inv.getArgument(0);
			if (c.getId() == null) {
				ReflectionTestUtils.setField(c, "id", idSequence.incrementAndGet());
			}
			clarificationStore.put(c.getId(), c);
			return c;
		});
		when(clarifications.findById(any(Long.class))).thenAnswer(inv -> Optional.ofNullable(clarificationStore.get(inv.getArgument(0))));
		when(clarifications.findByRequirementIdOrderByIssueIdAscRoundNoAsc(eq(401L))).thenAnswer(inv ->
				new ArrayList<>(clarificationStore.values()));
		when(clarifications.findTopByIssueIdOrderByRoundNoDesc(any(Long.class))).thenAnswer(inv -> {
			long issueId = inv.getArgument(0);
			return clarificationStore.values().stream()
					.filter(c -> c.getIssueId().equals(issueId))
					.max((c1, c2) -> Integer.compare(c1.getRoundNo(), c2.getRoundNo()));
		});

		when(revisions.save(any(RequirementRevision.class))).thenAnswer(inv -> {
			RequirementRevision r = inv.getArgument(0);
			if (r.getId() == null) {
				ReflectionTestUtils.setField(r, "id", idSequence.incrementAndGet());
			}
			revisionStore.put(r.getId(), r);
			return r;
		});
		when(revisions.findById(any(Long.class))).thenAnswer(inv -> Optional.ofNullable(revisionStore.get(inv.getArgument(0))));
		when(revisions.findByRequirementIdOrderByRevisionNoDesc(eq(401L))).thenAnswer(inv ->
				revisionStore.values().stream()
						.sorted((r1, r2) -> Integer.compare(r2.getRevisionNo(), r1.getRevisionNo()))
						.toList());
		when(revisions.findTopByRequirementIdOrderByRevisionNoDesc(eq(401L))).thenAnswer(inv ->
				revisionStore.values().stream()
						.max((r1, r2) -> Integer.compare(r1.getRevisionNo(), r2.getRevisionNo())));
		when(revisions.existsByRequirementIdAndStatus(eq(401L), any(RevisionStatus.class))).thenAnswer(inv -> {
			RevisionStatus st = inv.getArgument(1);
			return revisionStore.values().stream().anyMatch(r -> r.getStatus() == st);
		});
	}
}
