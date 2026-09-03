package com.sua.reqbridge.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sua.reqbridge.contract.AmbiguityType;
import com.sua.reqbridge.contract.ApprovedRevisionSnapshot;
import com.sua.reqbridge.contract.ClarificationStatus;
import com.sua.reqbridge.contract.CoreRequirementPort;
import com.sua.reqbridge.contract.DocumentSnapshot;
import com.sua.reqbridge.contract.IssueSnapshot;
import com.sua.reqbridge.contract.IssueStatus;
import com.sua.reqbridge.contract.QuestionSnapshot;
import com.sua.reqbridge.contract.RequirementSnapshot;
import com.sua.reqbridge.contract.RequirementStatus;
import com.sua.reqbridge.contract.ResourceNotFoundException;
import com.sua.reqbridge.contract.StateConflictException;
import com.sua.reqbridge.contract.WorkflowPreviewPort;
import com.sua.reqbridge.contract.WorkflowPreviewSnapshot;
import com.sua.reqbridge.contract.WorkflowRequirementSnapshot;

class ReportServiceTests {

	private CoreRequirementPort core;
	private WorkflowPreviewPort workflowPort;
	private ReportService service;

	@BeforeEach
	void setUp() {
		core = mock(CoreRequirementPort.class);
		workflowPort = mock(WorkflowPreviewPort.class);
		service = new ReportService(core, workflowPort);
	}

	@Test
	void getCustomerPreviewFiltersOnlyWaitingQuestionsOnOpenIssues() {
		DocumentSnapshot doc = new DocumentSnapshot(
				101L, 1L, "테스트 문서", "내용", "TEXT");
		when(core.getDocument(101L)).thenReturn(doc);

		RequirementSnapshot req1 = new RequirementSnapshot(
				401L, 101L, 301L, 1, "원문 1", RequirementStatus.CLARIFYING, 1L, null, null);
		RequirementSnapshot req2 = new RequirementSnapshot(
				402L, 101L, 301L, 2, "원문 2", RequirementStatus.CONFIRMED, 4L, 701L, "확정 2");
		when(core.listRequirements(101L)).thenReturn(List.of(req2, req1)); // unsorted order to check sorting

		IssueSnapshot issue1 = new IssueSnapshot(501L, AmbiguityType.QUANTITY_MISSING, "근거 1", IssueStatus.OPEN);
		IssueSnapshot issue2 = new IssueSnapshot(502L, AmbiguityType.PERFORMANCE_MISSING, "근거 2", IssueStatus.RESOLVED);
		QuestionSnapshot q1 = new QuestionSnapshot(
				601L, 401L, 501L, 1, "질문 1?", null, ClarificationStatus.WAITING);
		QuestionSnapshot q2 = new QuestionSnapshot(
				602L, 401L, 502L, 1, "질문 2?", "답변 2", ClarificationStatus.RESOLVED);

		WorkflowRequirementSnapshot wfReq1 = new WorkflowRequirementSnapshot(
				401L, List.of(issue1, issue2), List.of(q1, q2), null);
		WorkflowRequirementSnapshot wfReq2 = new WorkflowRequirementSnapshot(
				402L, List.of(), List.of(), new ApprovedRevisionSnapshot(701L, 1, "확정 2", List.of(), List.of()));

		when(workflowPort.getPreview(101L)).thenReturn(new WorkflowPreviewSnapshot(101L, List.of(wfReq1, wfReq2)));

		ReportService.CustomerPreview preview = service.getCustomerPreview(101L);

		assertThat(preview.documentId()).isEqualTo(101L);
		assertThat(preview.documentTitle()).isEqualTo("테스트 문서");
		assertThat(preview.summary().totalRequirements()).isEqualTo(2);
		assertThat(preview.summary().confirmedRequirements()).isEqualTo(1);
		assertThat(preview.summary().openIssueCount()).isEqualTo(1);
		assertThat(preview.summary().waitingQuestionCount()).isEqualTo(1);

		// Basis includes all requirements sorted by sequenceNo
		assertThat(preview.basis()).hasSize(2);
		assertThat(preview.basis().get(0).requirementId()).isEqualTo(401L);
		assertThat(preview.basis().get(1).requirementId()).isEqualTo(402L);

		// Only req1 has WAITING question on OPEN issue
		assertThat(preview.requirements()).hasSize(1);
		ReportService.CustomerRequirement customerReq = preview.requirements().get(0);
		assertThat(customerReq.requirementId()).isEqualTo(401L);
		assertThat(customerReq.sequenceNo()).isEqualTo(1);
		assertThat(customerReq.questions()).hasSize(1);
		assertThat(customerReq.questions().get(0).id()).isEqualTo(601L);
		assertThat(customerReq.questions().get(0).type()).isEqualTo(AmbiguityType.QUANTITY_MISSING);
	}

	@Test
	void getDeveloperPreviewSplitsConfirmedAndUnconfirmedRequirements() {
		DocumentSnapshot doc = new DocumentSnapshot(
				101L, 1L, "테스트 문서", "내용", "TEXT");
		when(core.getDocument(101L)).thenReturn(doc);

		RequirementSnapshot req1 = new RequirementSnapshot(
				401L, 101L, 301L, 1, "원문 1", RequirementStatus.CONFIRMED, 4L, 701L, "확정 텍스트 1");
		RequirementSnapshot req2 = new RequirementSnapshot(
				402L, 101L, 301L, 2, "원문 2", RequirementStatus.CLARIFYING, 2L, null, null);
		when(core.listRequirements(101L)).thenReturn(List.of(req1, req2));

		IssueSnapshot issue1 = new IssueSnapshot(501L, AmbiguityType.QUANTITY_MISSING, "근거 1", IssueStatus.RESOLVED);
		QuestionSnapshot q1 = new QuestionSnapshot(
				601L, 401L, 501L, 1, "질문 1?", "답변 1", ClarificationStatus.RESOLVED);
		ApprovedRevisionSnapshot rev1 = new ApprovedRevisionSnapshot(
				701L, 1, "확정 텍스트 1", List.of(601L), List.of());
		WorkflowRequirementSnapshot wfReq1 = new WorkflowRequirementSnapshot(
				401L, List.of(issue1), List.of(q1), rev1);

		IssueSnapshot issue2 = new IssueSnapshot(502L, AmbiguityType.CONDITION_MISSING, "근거 2", IssueStatus.OPEN);
		QuestionSnapshot q2 = new QuestionSnapshot(
				602L, 402L, 502L, 1, "질문 2?", null, ClarificationStatus.WAITING);
		WorkflowRequirementSnapshot wfReq2 = new WorkflowRequirementSnapshot(
				402L, List.of(issue2), List.of(q2), null);

		when(workflowPort.getPreview(101L)).thenReturn(new WorkflowPreviewSnapshot(101L, List.of(wfReq1, wfReq2)));

		ReportService.DeveloperPreview preview = service.getDeveloperPreview(101L);

		assertThat(preview.confirmedRequirements()).hasSize(1);
		ReportService.ConfirmedRequirement confirmed = preview.confirmedRequirements().get(0);
		assertThat(confirmed.requirementId()).isEqualTo(401L);
		assertThat(confirmed.approvedRevision().id()).isEqualTo(701L);
		assertThat(confirmed.approvedRevision().status()).isEqualTo("APPROVED");
		assertThat(confirmed.evidenceAnswers()).hasSize(1);
		assertThat(confirmed.evidenceAnswers().get(0).id()).isEqualTo(601L);

		assertThat(preview.unconfirmedRequirements()).hasSize(1);
		ReportService.UnconfirmedRequirement unconfirmed = preview.unconfirmedRequirements().get(0);
		assertThat(unconfirmed.requirementId()).isEqualTo(402L);
		assertThat(unconfirmed.status()).isEqualTo(RequirementStatus.CLARIFYING);
		assertThat(unconfirmed.issues()).hasSize(1);
		assertThat(unconfirmed.questions()).hasSize(1);
	}

	@Test
	void getDeveloperPreviewThrowsPreviewVersionConflictWhenRevisionTextDiffers() {
		DocumentSnapshot doc = new DocumentSnapshot(
				101L, 1L, "테스트 문서", "내용", "TEXT");
		when(core.getDocument(101L)).thenReturn(doc);

		RequirementSnapshot req1 = new RequirementSnapshot(
				401L, 101L, 301L, 1, "원문 1", RequirementStatus.CONFIRMED, 4L, 701L, "확정 텍스트");
		when(core.listRequirements(101L)).thenReturn(List.of(req1));

		ApprovedRevisionSnapshot revDifferentText = new ApprovedRevisionSnapshot(
				701L, 1, "불일치 텍스트", List.of(), List.of());
		WorkflowRequirementSnapshot wfReq1 = new WorkflowRequirementSnapshot(
				401L, List.of(), List.of(), revDifferentText);

		when(workflowPort.getPreview(101L)).thenReturn(new WorkflowPreviewSnapshot(101L, List.of(wfReq1)));

		assertThatThrownBy(() -> service.getDeveloperPreview(101L))
				.isInstanceOf(StateConflictException.class)
				.satisfies(e -> assertThat(((StateConflictException) e).getCode()).isEqualTo("PREVIEW_VERSION_CONFLICT"));
	}

	@Test
	void getCustomerPreviewThrowsWhenDocumentNotFound() {
		when(core.getDocument(999L)).thenThrow(new ResourceNotFoundException("문서를 찾을 수 없습니다."));

		assertThatThrownBy(() -> service.getCustomerPreview(999L))
				.isInstanceOf(ResourceNotFoundException.class);
	}
}
