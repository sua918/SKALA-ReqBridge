package com.sua.reqbridge.revision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.sua.reqbridge.ambiguity.AmbiguityIssueRepository;
import com.sua.reqbridge.analysis.AiOutputInvalidException;
import com.sua.reqbridge.analysis.Analysis;
import com.sua.reqbridge.analysis.AnalysisRepository;
import com.sua.reqbridge.analysis.DocumentAnalysisService;
import com.sua.reqbridge.clarification.Clarification;
import com.sua.reqbridge.clarification.ClarificationRepository;
import com.sua.reqbridge.contract.AnalysisKind;
import com.sua.reqbridge.contract.AnalysisStatus;
import com.sua.reqbridge.contract.ClarificationStatus;
import com.sua.reqbridge.contract.CoreRequirementPort;
import com.sua.reqbridge.contract.IssueStatus;
import com.sua.reqbridge.contract.RequirementSnapshot;
import com.sua.reqbridge.contract.RequirementStatus;
import com.sua.reqbridge.contract.RevisionSource;
import com.sua.reqbridge.contract.RevisionStatus;
import com.sua.reqbridge.contract.StateConflictException;

import tools.jackson.databind.ObjectMapper;

class RevisionWorkflowServiceTests {

	private AnalysisRepository analyses;
	private AmbiguityIssueRepository issues;
	private ClarificationRepository clarifications;
	private RequirementRevisionRepository revisions;
	private CoreRequirementPort core;
	private ApplicationEventPublisher events;
	private ObjectMapper json;
	private RevisionWorkflowService service;

	@BeforeEach
	void setUp() {
		analyses = mock(AnalysisRepository.class);
		issues = mock(AmbiguityIssueRepository.class);
		clarifications = mock(ClarificationRepository.class);
		revisions = mock(RequirementRevisionRepository.class);
		core = mock(CoreRequirementPort.class);
		events = mock(ApplicationEventPublisher.class);
		json = new ObjectMapper();
		service = new RevisionWorkflowService(analyses, issues, clarifications, revisions, core, events,
				new com.sua.reqbridge.analysis.MockWorkflowAnalyzer(), json);
	}

	@Test
	void submitRevisionRejectsInvalidVersion() {
		assertThatThrownBy(() -> service.submitRevision(401, 0))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("expectedContentVersion");
	}

	@Test
	void submitRevisionRejectsWhenRequirementIsConfirmed() {
		when(core.lockRequirement(401)).thenReturn(new RequirementSnapshot(
				401, 101, 301, 1, "text", RequirementStatus.CONFIRMED, 5, 701L, "confirmed"));

		assertThatThrownBy(() -> service.submitRevision(401, 5))
				.isInstanceOf(StateConflictException.class)
				.hasMessageContaining("확정된 요구사항");
	}

	@Test
	void submitRevisionRejectsWhenNotClarifying() {
		when(core.lockRequirement(401)).thenReturn(new RequirementSnapshot(
				401, 101, 301, 1, "text", RequirementStatus.IN_REVIEW, 4, null, null));

		assertThatThrownBy(() -> service.submitRevision(401, 4))
				.isInstanceOf(StateConflictException.class)
				.hasMessageContaining("CLARIFYING");
	}

	@Test
	void submitRevisionRejectsWhenOpenIssuesRemain() {
		when(core.lockRequirement(401)).thenReturn(new RequirementSnapshot(
				401, 101, 301, 1, "text", RequirementStatus.CLARIFYING, 5, null, null));
		when(issues.countByRequirementIdAndStatus(401, IssueStatus.OPEN)).thenReturn(1L);

		assertThatThrownBy(() -> service.submitRevision(401, 5))
				.isInstanceOf(StateConflictException.class)
				.hasMessageContaining("미해결된 불명확성 문제");
	}

	@Test
	void submitRevisionRejectsWhenActiveAnalysisExists() {
		when(core.lockRequirement(401)).thenReturn(new RequirementSnapshot(
				401, 101, 301, 1, "text", RequirementStatus.CLARIFYING, 5, null, null));
		when(issues.countByRequirementIdAndStatus(401, IssueStatus.OPEN)).thenReturn(0L);
		when(analyses.existsByRequirementIdAndStatusIn(eq(401L), any())).thenReturn(true);

		assertThatThrownBy(() -> service.submitRevision(401, 5))
				.isInstanceOf(StateConflictException.class)
				.hasMessageContaining("진행 중");
	}

	@Test
	void submitRevisionRejectsWhenProposedRevisionExists() {
		when(core.lockRequirement(401)).thenReturn(new RequirementSnapshot(
				401, 101, 301, 1, "text", RequirementStatus.CLARIFYING, 5, null, null));
		when(issues.countByRequirementIdAndStatus(401, IssueStatus.OPEN)).thenReturn(0L);
		when(analyses.existsByRequirementIdAndStatusIn(eq(401L), any())).thenReturn(false);
		when(revisions.existsByRequirementIdAndStatus(401, RevisionStatus.PROPOSED)).thenReturn(true);

		assertThatThrownBy(() -> service.submitRevision(401, 5))
				.isInstanceOf(StateConflictException.class)
				.hasMessageContaining("검토 대기 중인 수정안");
	}

	@Test
	void submitRevisionRejectsWhenNoRejectionHistory() {
		when(core.lockRequirement(401)).thenReturn(new RequirementSnapshot(
				401, 101, 301, 1, "text", RequirementStatus.CLARIFYING, 5, null, null));
		when(issues.countByRequirementIdAndStatus(401, IssueStatus.OPEN)).thenReturn(0L);
		when(analyses.existsByRequirementIdAndStatusIn(eq(401L), any())).thenReturn(false);
		when(revisions.existsByRequirementIdAndStatus(401, RevisionStatus.PROPOSED)).thenReturn(false);
		when(revisions.existsByRequirementIdAndStatus(401, RevisionStatus.REJECTED)).thenReturn(false);

		assertThatThrownBy(() -> service.submitRevision(401, 5))
				.isInstanceOf(StateConflictException.class)
				.hasMessageContaining("거절된 수정안이 있어야");
	}

	@Test
	void submitRevisionRejectsOnVersionMismatch() {
		when(core.lockRequirement(401)).thenReturn(new RequirementSnapshot(
				401, 101, 301, 1, "text", RequirementStatus.CLARIFYING, 5, null, null));
		when(issues.countByRequirementIdAndStatus(401, IssueStatus.OPEN)).thenReturn(0L);
		when(analyses.existsByRequirementIdAndStatusIn(eq(401L), any())).thenReturn(false);
		when(revisions.existsByRequirementIdAndStatus(401, RevisionStatus.PROPOSED)).thenReturn(false);
		when(revisions.existsByRequirementIdAndStatus(401, RevisionStatus.REJECTED)).thenReturn(true);

		assertThatThrownBy(() -> service.submitRevision(401, 4))
				.isInstanceOf(StateConflictException.class)
				.hasMessageContaining("버전이 일치하지 않습니다");
	}

	@Test
	void submitRevisionCreatesPendingRevisionAnalysisAndPublishesEvent() {
		when(core.lockRequirement(401)).thenReturn(new RequirementSnapshot(
				401, 101, 301, 1, "text", RequirementStatus.CLARIFYING, 5, null, null));
		when(issues.countByRequirementIdAndStatus(401, IssueStatus.OPEN)).thenReturn(0L);
		when(analyses.existsByRequirementIdAndStatusIn(eq(401L), any())).thenReturn(false);
		when(revisions.existsByRequirementIdAndStatus(401, RevisionStatus.PROPOSED)).thenReturn(false);
		when(revisions.existsByRequirementIdAndStatus(401, RevisionStatus.REJECTED)).thenReturn(true);

		RequirementRevision rejectedRev = RequirementRevision.proposed(401, 1, "rev1", 4, List.of(601L));
		rejectedRev.reject("동시 사용자 수를 최대치로 명확하게 표현해주세요.");
		when(revisions.findByRequirementIdOrderByRevisionNoDesc(401)).thenReturn(List.of(rejectedRev));

		Analysis savedAnalysis = Analysis.pendingRevision(101, 401, 5, "{}");
		ReflectionTestUtils.setField(savedAnalysis, "id", 307L);
		when(analyses.save(any(Analysis.class))).thenReturn(savedAnalysis);

		Analysis result = service.submitRevision(401, 5);

		assertThat(result.getId()).isEqualTo(307L);
		assertThat(result.getKind()).isEqualTo(AnalysisKind.REVISION);
		verify(events).publishEvent(new RevisionAnalysisRequested(307L));
	}

	@Test
	void executeRevisionCreatesProposedRevisionAndTransitionsToInReview() {
		Analysis analysis = Analysis.pendingRevision(101, 401, 5, "{}");
		ReflectionTestUtils.setField(analysis, "id", 307L);
		when(analyses.findById(307L)).thenReturn(Optional.of(analysis));

		RequirementRevision oldRev = RequirementRevision.proposed(401, 1, "rev1", 4, List.of(601L));
		when(revisions.findTopByRequirementIdOrderByRevisionNoDesc(401)).thenReturn(Optional.of(oldRev));

		Clarification c1 = Clarification.waiting(401, 501, 1, "q1");
		c1.answer("a1");
		ReflectionTestUtils.setField(c1, "id", 601L);
		when(clarifications.findByRequirementIdOrderByIssueIdAscRoundNoAsc(401)).thenReturn(List.of(c1));

		when(revisions.save(any(RequirementRevision.class))).thenAnswer(invocation -> {
			RequirementRevision r = invocation.getArgument(0);
			ReflectionTestUtils.setField(r, "id", 702L);
			return r;
		});

		service.executeRevision(307L);

		assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
		ArgumentCaptor<RequirementRevision> revCaptor = ArgumentCaptor.forClass(RequirementRevision.class);
		verify(revisions).save(revCaptor.capture());
		RequirementRevision created = revCaptor.getValue();
		assertThat(created.getRevisionNo()).isEqualTo(2);
		assertThat(created.getStatus()).isEqualTo(RevisionStatus.PROPOSED);
		assertThat(created.getInputContentVersion()).isEqualTo(5);

		verify(core).changeStatus(401, 5, RequirementStatus.IN_REVIEW);
	}

	@Test
	void reviewApprovesAndConfirmsRequirementWithoutBumpingVersion() {
		RequirementRevision revision = RequirementRevision.proposed(401, 1, "proposed text", 4, List.of(601L));
		ReflectionTestUtils.setField(revision, "id", 701L);
		when(revisions.findById(701L)).thenReturn(Optional.of(revision));

		RequirementSnapshot locked = new RequirementSnapshot(
				401, 101, 301, 1, "orig", RequirementStatus.IN_REVIEW, 4, null, null);
		RequirementSnapshot confirmed = new RequirementSnapshot(
				401, 101, 301, 1, "orig", RequirementStatus.CONFIRMED, 4, 701L, "proposed text");
		when(core.lockRequirement(401)).thenReturn(locked);
		when(core.getRequirement(401)).thenReturn(confirmed);
		when(issues.countByRequirementIdAndStatus(401, IssueStatus.OPEN)).thenReturn(0L);

		RevisionWorkflowService.ReviewResult result = service.review(701L, "APPROVE", null, 4);

		assertThat(result.revision().getStatus()).isEqualTo(RevisionStatus.APPROVED);
		assertThat(result.requirement().status()).isEqualTo(RequirementStatus.CONFIRMED);
		assertThat(result.requirement().contentVersion()).isEqualTo(4);
		verify(core).confirmRequirement(401, 4, 701L, "proposed text");
		verify(core, never()).advanceContentVersion(eq(401L), any(Long.class));
	}

	@Test
	void reviewRejectsAndBumpsVersionAndTransitionsToClarifying() {
		RequirementRevision revision = RequirementRevision.proposed(401, 1, "proposed text", 4, List.of(601L));
		ReflectionTestUtils.setField(revision, "id", 701L);
		when(revisions.findById(701L)).thenReturn(Optional.of(revision));

		RequirementSnapshot locked = new RequirementSnapshot(
				401, 101, 301, 1, "orig", RequirementStatus.IN_REVIEW, 4, null, null);
		RequirementSnapshot clarifying = new RequirementSnapshot(
				401, 101, 301, 1, "orig", RequirementStatus.CLARIFYING, 5, null, null);
		when(core.lockRequirement(401)).thenReturn(locked);
		when(core.advanceContentVersion(401, 4)).thenReturn(5L);
		when(core.getRequirement(401)).thenReturn(clarifying);
		when(issues.countByRequirementIdAndStatus(401, IssueStatus.OPEN)).thenReturn(0L);

		RevisionWorkflowService.ReviewResult result = service.review(
				701L, "REJECT", "동시 사용자 수를 최대치로 명확하게 표현해주세요.", 4);

		assertThat(result.revision().getStatus()).isEqualTo(RevisionStatus.REJECTED);
		assertThat(result.revision().getRejectionReason()).isEqualTo("동시 사용자 수를 최대치로 명확하게 표현해주세요.");
		assertThat(result.requirement().status()).isEqualTo(RequirementStatus.CLARIFYING);
		assertThat(result.requirement().contentVersion()).isEqualTo(5);
		verify(core).advanceContentVersion(401, 4);
		verify(core).changeStatus(401, 5, RequirementStatus.CLARIFYING);
	}

	@Test
	void reviewRejectsEmptyRejectionReason() {
		RequirementRevision revision = RequirementRevision.proposed(401, 1, "proposed text", 4, List.of(601L));
		ReflectionTestUtils.setField(revision, "id", 701L);

		assertThatThrownBy(() -> service.review(701L, "REJECT", "   ", 4))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("거절 사유");
	}

	@Test
	void reviewRejectsWhenApproveProvidesRejectionReason() {
		RequirementRevision revision = RequirementRevision.proposed(401, 1, "proposed text", 4, List.of(601L));
		ReflectionTestUtils.setField(revision, "id", 701L);

		assertThatThrownBy(() -> service.review(701L, "APPROVE", "사유", 4))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("승인 시에는 거절 사유를 입력할 수 없습니다");
	}

	@Test
	void reviewIdempotentlyReturnsApprovedRevision() {
		RequirementRevision revision = RequirementRevision.proposed(401, 1, "proposed text", 4, List.of(601L));
		ReflectionTestUtils.setField(revision, "id", 701L);
		revision.approve();
		when(revisions.findById(701L)).thenReturn(Optional.of(revision));

		RequirementSnapshot confirmed = new RequirementSnapshot(
				401, 101, 301, 1, "orig", RequirementStatus.CONFIRMED, 4, 701L, "proposed text");
		when(core.lockRequirement(401)).thenReturn(confirmed);

		RevisionWorkflowService.ReviewResult result = service.review(701L, "APPROVE", null, 4);

		assertThat(result.revision().getStatus()).isEqualTo(RevisionStatus.APPROVED);
		verify(core, never()).confirmRequirement(any(Long.class), any(Long.class), any(Long.class), any());
	}

	@Test
	void reviewThrowsWhenAttemptingToChangeApprovedDecision() {
		RequirementRevision revision = RequirementRevision.proposed(401, 1, "proposed text", 4, List.of(601L));
		ReflectionTestUtils.setField(revision, "id", 701L);
		revision.approve();
		when(revisions.findById(701L)).thenReturn(Optional.of(revision));

		RequirementSnapshot confirmed = new RequirementSnapshot(
				401, 101, 301, 1, "orig", RequirementStatus.CONFIRMED, 4, 701L, "proposed text");
		when(core.lockRequirement(401)).thenReturn(confirmed);

		assertThatThrownBy(() -> service.review(701L, "REJECT", "거절 사유", 4))
				.isInstanceOf(StateConflictException.class)
				.satisfies(e -> assertThat(((StateConflictException) e).getCode()).isEqualTo("REVISION_ALREADY_REVIEWED"));
	}

	@Test
	void reviewIdempotentlyReturnsRejectedRevisionOnSameReason() {
		RequirementRevision revision = RequirementRevision.proposed(401, 1, "proposed text", 4, List.of(601L));
		ReflectionTestUtils.setField(revision, "id", 701L);
		revision.reject("동일 거절 사유");
		when(revisions.findById(701L)).thenReturn(Optional.of(revision));

		RequirementSnapshot clarifying = new RequirementSnapshot(
				401, 101, 301, 1, "orig", RequirementStatus.CLARIFYING, 5, null, null);
		when(core.lockRequirement(401)).thenReturn(clarifying);

		RevisionWorkflowService.ReviewResult result = service.review(701L, "REJECT", "동일 거절 사유", 4);

		assertThat(result.revision().getStatus()).isEqualTo(RevisionStatus.REJECTED);
		verify(core, never()).advanceContentVersion(eq(401L), any(Long.class));
	}

	@Test
	void reviewThrowsWhenAttemptingToChangeRejectedReasonOrDecision() {
		RequirementRevision revision = RequirementRevision.proposed(401, 1, "proposed text", 4, List.of(601L));
		ReflectionTestUtils.setField(revision, "id", 701L);
		revision.reject("기존 사유");
		when(revisions.findById(701L)).thenReturn(Optional.of(revision));

		RequirementSnapshot clarifying = new RequirementSnapshot(
				401, 101, 301, 1, "orig", RequirementStatus.CLARIFYING, 5, null, null);
		when(core.lockRequirement(401)).thenReturn(clarifying);

		assertThatThrownBy(() -> service.review(701L, "REJECT", "다른 사유", 4))
				.isInstanceOf(StateConflictException.class)
				.satisfies(e -> assertThat(((StateConflictException) e).getCode()).isEqualTo("REVISION_ALREADY_REVIEWED"));

		assertThatThrownBy(() -> service.review(701L, "APPROVE", null, 4))
				.isInstanceOf(StateConflictException.class)
				.satisfies(e -> assertThat(((StateConflictException) e).getCode()).isEqualTo("REVISION_ALREADY_REVIEWED"));
	}

	@Test
	void revisionWorkerSeparatesUnexpectedExecutionFailure() {
		RevisionWorkflowService mockService = mock(RevisionWorkflowService.class);
		DocumentAnalysisService failures = mock(DocumentAnalysisService.class);
		doThrow(new IllegalStateException("db error")).when(mockService).executeRevision(307L);

		new RevisionAnalysisWorker(mockService, failures).run(new RevisionAnalysisRequested(307L));

		verify(failures).fail(307L, "ANALYSIS_EXECUTION_FAILED", "분석 실행 중 오류가 발생했습니다.");
	}

	@Test
	void revisionWorkerRecordsInvalidAiOutput() {
		RevisionWorkflowService mockService = mock(RevisionWorkflowService.class);
		DocumentAnalysisService failures = mock(DocumentAnalysisService.class);
		doThrow(new AiOutputInvalidException("bad format")).when(mockService).executeRevision(307L);

		new RevisionAnalysisWorker(mockService, failures).run(new RevisionAnalysisRequested(307L));

		verify(failures).fail(307L, "AI_OUTPUT_INVALID", "분석 결과 형식이 올바르지 않습니다.");
	}

	@Test
	void directConfirmSuccess() {
		long requirementId = 401L;
		long version = 1L;
		String originalText = "관리자는 보고서를 다운로드할 수 있어야 한다.";

		RequirementSnapshot extracted = new RequirementSnapshot(
				requirementId, 101L, 301L, 1, originalText, RequirementStatus.EXTRACTED, version, null, null);
		RequirementSnapshot confirmed = new RequirementSnapshot(
				requirementId, 101L, 301L, 1, originalText, RequirementStatus.CONFIRMED, version, 801L, originalText);

		when(core.lockRequirement(requirementId)).thenReturn(extracted);
		when(issues.countByRequirementIdAndStatus(requirementId, IssueStatus.OPEN)).thenReturn(0L);
		when(clarifications.countByRequirementIdAndStatus(requirementId, ClarificationStatus.WAITING)).thenReturn(0L);
		when(analyses.existsByRequirementIdAndStatusIn(eq(requirementId), any())).thenReturn(false);
		when(revisions.existsByRequirementIdAndStatus(requirementId, RevisionStatus.PROPOSED)).thenReturn(false);
		when(revisions.findTopByRequirementIdOrderByRevisionNoDesc(requirementId)).thenReturn(Optional.empty());

		when(revisions.save(any(RequirementRevision.class))).thenAnswer(inv -> {
			RequirementRevision r = inv.getArgument(0);
			ReflectionTestUtils.setField(r, "id", 801L);
			return r;
		});
		when(core.getRequirement(requirementId)).thenReturn(confirmed);

		RevisionWorkflowService.ReviewResult result = service.directConfirm(requirementId, version);

		assertThat(result.requirement().status()).isEqualTo(RequirementStatus.CONFIRMED);
		assertThat(result.requirement().approvedRevisionId()).isEqualTo(801L);
		assertThat(result.revision().getText()).isEqualTo(originalText);
		assertThat(result.revision().getSource()).isEqualTo(RevisionSource.MANUAL);
		assertThat(result.revision().getStatus()).isEqualTo(RevisionStatus.APPROVED);
		assertThat(result.revision().getBasedOnClarificationIds()).isEmpty();

		verify(core).changeStatus(requirementId, version, RequirementStatus.IN_REVIEW);
		verify(core).confirmRequirement(requirementId, version, 801L, originalText);
	}

	@Test
	void directConfirmRejectsInvalidVersion() {
		assertThatThrownBy(() -> service.directConfirm(401L, 0L))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("expectedContentVersion");
	}

	@Test
	void directConfirmRejectsVersionMismatch() {
		RequirementSnapshot extracted = new RequirementSnapshot(
				401L, 101L, 301L, 1, "text", RequirementStatus.EXTRACTED, 1L, null, null);
		when(core.lockRequirement(401L)).thenReturn(extracted);

		assertThatThrownBy(() -> service.directConfirm(401L, 2L))
				.isInstanceOf(StateConflictException.class)
				.satisfies(e -> assertThat(((StateConflictException) e).getCode()).isEqualTo("CONTENT_VERSION_CONFLICT"));
	}

	@Test
	void directConfirmRejectsWhenNotExtracted() {
		RequirementSnapshot clarifying = new RequirementSnapshot(
				401L, 101L, 301L, 1, "text", RequirementStatus.CLARIFYING, 1L, null, null);
		when(core.lockRequirement(401L)).thenReturn(clarifying);

		assertThatThrownBy(() -> service.directConfirm(401L, 1L))
				.isInstanceOf(StateConflictException.class)
				.satisfies(e -> assertThat(((StateConflictException) e).getCode()).isEqualTo("REQUIREMENT_NOT_DIRECTLY_CONFIRMABLE"));
	}

	@Test
	void directConfirmRejectsWhenOpenIssuesRemain() {
		RequirementSnapshot extracted = new RequirementSnapshot(
				401L, 101L, 301L, 1, "text", RequirementStatus.EXTRACTED, 1L, null, null);
		when(core.lockRequirement(401L)).thenReturn(extracted);
		when(issues.countByRequirementIdAndStatus(401L, IssueStatus.OPEN)).thenReturn(1L);

		assertThatThrownBy(() -> service.directConfirm(401L, 1L))
				.isInstanceOf(StateConflictException.class)
				.satisfies(e -> assertThat(((StateConflictException) e).getCode()).isEqualTo("OPEN_ISSUES_REMAIN"));
	}

	@Test
	void directConfirmRejectsWhenWaitingClarificationsRemain() {
		RequirementSnapshot extracted = new RequirementSnapshot(
				401L, 101L, 301L, 1, "text", RequirementStatus.EXTRACTED, 1L, null, null);
		when(core.lockRequirement(401L)).thenReturn(extracted);
		when(issues.countByRequirementIdAndStatus(401L, IssueStatus.OPEN)).thenReturn(0L);
		when(clarifications.countByRequirementIdAndStatus(401L, ClarificationStatus.WAITING)).thenReturn(1L);

		assertThatThrownBy(() -> service.directConfirm(401L, 1L))
				.isInstanceOf(StateConflictException.class)
				.satisfies(e -> assertThat(((StateConflictException) e).getCode()).isEqualTo("OPEN_ISSUES_REMAIN"));
	}

	@Test
	void directConfirmRejectsWhenAnalysisInProgress() {
		RequirementSnapshot extracted = new RequirementSnapshot(
				401L, 101L, 301L, 1, "text", RequirementStatus.EXTRACTED, 1L, null, null);
		when(core.lockRequirement(401L)).thenReturn(extracted);
		when(issues.countByRequirementIdAndStatus(401L, IssueStatus.OPEN)).thenReturn(0L);
		when(clarifications.countByRequirementIdAndStatus(401L, ClarificationStatus.WAITING)).thenReturn(0L);
		when(analyses.existsByRequirementIdAndStatusIn(eq(401L), any())).thenReturn(true);

		assertThatThrownBy(() -> service.directConfirm(401L, 1L))
				.isInstanceOf(StateConflictException.class)
				.satisfies(e -> assertThat(((StateConflictException) e).getCode()).isEqualTo("ANALYSIS_IN_PROGRESS"));
	}

	@Test
	void directConfirmRejectsWhenProposedRevisionExists() {
		RequirementSnapshot extracted = new RequirementSnapshot(
				401L, 101L, 301L, 1, "text", RequirementStatus.EXTRACTED, 1L, null, null);
		when(core.lockRequirement(401L)).thenReturn(extracted);
		when(issues.countByRequirementIdAndStatus(401L, IssueStatus.OPEN)).thenReturn(0L);
		when(clarifications.countByRequirementIdAndStatus(401L, ClarificationStatus.WAITING)).thenReturn(0L);
		when(analyses.existsByRequirementIdAndStatusIn(eq(401L), any())).thenReturn(false);
		when(revisions.existsByRequirementIdAndStatus(401L, RevisionStatus.PROPOSED)).thenReturn(true);

		assertThatThrownBy(() -> service.directConfirm(401L, 1L))
				.isInstanceOf(StateConflictException.class)
				.satisfies(e -> assertThat(((StateConflictException) e).getCode()).isEqualTo("REQUIREMENT_NOT_DIRECTLY_CONFIRMABLE"));
	}

	@Test
	void directConfirmIdempotentWhenAlreadyConfirmed() {
		RequirementSnapshot confirmed = new RequirementSnapshot(
				401L, 101L, 301L, 1, "text", RequirementStatus.CONFIRMED, 1L, 801L, "text");
		when(core.lockRequirement(401L)).thenReturn(confirmed);

		RequirementRevision approvedRevision = RequirementRevision.proposed(
				401L, 1, "text", 1L, List.of(), RevisionSource.MANUAL);
		ReflectionTestUtils.setField(approvedRevision, "id", 801L);
		approvedRevision.approve();

		when(revisions.findById(801L)).thenReturn(Optional.of(approvedRevision));

		RevisionWorkflowService.ReviewResult result = service.directConfirm(401L, 1L);

		assertThat(result.requirement().status()).isEqualTo(RequirementStatus.CONFIRMED);
		assertThat(result.revision().getId()).isEqualTo(801L);
		verify(revisions, never()).save(any());
	}

	@Test
	void directConfirmRejectsConfirmedWhenVersionMismatch() {
		RequirementSnapshot confirmed = new RequirementSnapshot(
				401L, 101L, 301L, 1, "text", RequirementStatus.CONFIRMED, 2L, 801L, "text");
		when(core.lockRequirement(401L)).thenReturn(confirmed);

		assertThatThrownBy(() -> service.directConfirm(401L, 1L))
				.isInstanceOf(StateConflictException.class)
				.satisfies(e -> assertThat(((StateConflictException) e).getCode()).isEqualTo("CONTENT_VERSION_CONFLICT"));
	}
}
