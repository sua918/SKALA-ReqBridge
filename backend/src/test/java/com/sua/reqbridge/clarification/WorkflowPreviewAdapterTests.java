package com.sua.reqbridge.clarification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.sua.reqbridge.ambiguity.AmbiguityIssue;
import com.sua.reqbridge.ambiguity.AmbiguityIssueRepository;
import com.sua.reqbridge.contract.AmbiguityType;
import com.sua.reqbridge.contract.CoreRequirementPort;
import com.sua.reqbridge.contract.IssueStatus;
import com.sua.reqbridge.contract.RequirementSnapshot;
import com.sua.reqbridge.contract.RequirementStatus;
import com.sua.reqbridge.contract.WorkflowPreviewSnapshot;
import com.sua.reqbridge.contract.WorkflowRequirementSnapshot;
import com.sua.reqbridge.revision.RequirementRevision;
import com.sua.reqbridge.revision.RequirementRevisionRepository;

class WorkflowPreviewAdapterTests {

	private CoreRequirementPort core;
	private AmbiguityIssueRepository issues;
	private ClarificationRepository clarifications;
	private RequirementRevisionRepository revisions;
	private WorkflowPreviewAdapter adapter;

	@BeforeEach
	void setUp() {
		core = mock(CoreRequirementPort.class);
		issues = mock(AmbiguityIssueRepository.class);
		clarifications = mock(ClarificationRepository.class);
		revisions = mock(RequirementRevisionRepository.class);
		adapter = new WorkflowPreviewAdapter(core, issues, clarifications, revisions);
	}

	@Test
	void getPreviewMapsAllWorkflowEntitiesCorrectly() {
		RequirementSnapshot req1 = new RequirementSnapshot(
				401L, 101L, 301L, 1, "orig1", RequirementStatus.CONFIRMED, 4L, 701L, "confirmed text");
		RequirementSnapshot req2 = new RequirementSnapshot(
				402L, 101L, 301L, 2, "orig2", RequirementStatus.CLARIFYING, 1L, null, null);
		when(core.listRequirements(101L)).thenReturn(List.of(req1, req2));

		AmbiguityIssue issue1 = AmbiguityIssue.open(401L, AmbiguityType.QUANTITY_MISSING, "정량 기준 누락");
		ReflectionTestUtils.setField(issue1, "id", 501L);
		issue1.resolve();
		when(issues.findByRequirementIdOrderByIdAsc(401L)).thenReturn(List.of(issue1));

		Clarification clar1 = Clarification.waiting(401L, 501L, 1, "질문 1?");
		ReflectionTestUtils.setField(clar1, "id", 601L);
		clar1.answer("답변 1");
		clar1.resolve();
		when(clarifications.findByRequirementIdOrderByIssueIdAscRoundNoAsc(401L)).thenReturn(List.of(clar1));

		RequirementRevision rev1 = RequirementRevision.proposed(401L, 1, "confirmed text", 4L, List.of(601L));
		ReflectionTestUtils.setField(rev1, "id", 701L);
		rev1.approve();
		when(revisions.findById(701L)).thenReturn(Optional.of(rev1));

		when(issues.findByRequirementIdOrderByIdAsc(402L)).thenReturn(List.of());
		when(clarifications.findByRequirementIdOrderByIssueIdAscRoundNoAsc(402L)).thenReturn(List.of());

		WorkflowPreviewSnapshot preview = adapter.getPreview(101L);

		assertThat(preview.documentId()).isEqualTo(101L);
		assertThat(preview.requirements()).hasSize(2);

		WorkflowRequirementSnapshot wfReq1 = preview.requirements().get(0);
		assertThat(wfReq1.requirementId()).isEqualTo(401L);
		assertThat(wfReq1.issues()).hasSize(1);
		assertThat(wfReq1.issues().get(0).status()).isEqualTo(IssueStatus.RESOLVED);
		assertThat(wfReq1.questions()).hasSize(1);
		assertThat(wfReq1.approvedRevision()).isNotNull();
		assertThat(wfReq1.approvedRevision().id()).isEqualTo(701L);
		assertThat(wfReq1.approvedRevision().text()).isEqualTo("confirmed text");

		WorkflowRequirementSnapshot wfReq2 = preview.requirements().get(1);
		assertThat(wfReq2.requirementId()).isEqualTo(402L);
		assertThat(wfReq2.issues()).isEmpty();
		assertThat(wfReq2.questions()).isEmpty();
		assertThat(wfReq2.approvedRevision()).isNull();
	}
}
