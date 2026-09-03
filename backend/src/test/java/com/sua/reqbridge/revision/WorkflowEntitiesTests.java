package com.sua.reqbridge.revision;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sua.reqbridge.ambiguity.AmbiguityIssue;
import com.sua.reqbridge.clarification.Clarification;
import com.sua.reqbridge.contract.AmbiguityType;
import com.sua.reqbridge.contract.ClarificationStatus;
import com.sua.reqbridge.contract.IssueStatus;
import com.sua.reqbridge.contract.RevisionStatus;

class WorkflowEntitiesTests {

	@Test
	void resolvesAQuestionAndItsIssueWithoutLosingTheAnswer() {
		AmbiguityIssue issue = AmbiguityIssue.open(
				401L, AmbiguityType.QUANTITY_MISSING, "정량 기준이 없다.");
		Clarification question = Clarification.waiting(
				401L, 501L, 1, "최대 동시 사용자는 몇 명인가요?");

		question.answer("최대 3,000명입니다.");
		question.resolve();
		issue.resolve();

		assertThat(question.getAnswerText()).isEqualTo("최대 3,000명입니다.");
		assertThat(question.getStatus()).isEqualTo(ClarificationStatus.RESOLVED);
		assertThat(issue.getStatus()).isEqualTo(IssueStatus.RESOLVED);
	}

	@Test
	void rejectedRevisionKeepsItsCreationVersionAndAllEvidence() {
		RequirementRevision revision = RequirementRevision.proposed(
				401L, 1, "수정안", 4L, List.of(601L, 603L, 602L));

		revision.reject("최대치로 표현해주세요.");

		assertThat(revision.getStatus()).isEqualTo(RevisionStatus.REJECTED);
		assertThat(revision.getInputContentVersion()).isEqualTo(4L);
		assertThat(revision.getBasedOnClarificationIds())
				.containsExactlyInAnyOrder(601L, 603L, 602L);
		assertThat(revision.getRejectionReason()).isEqualTo("최대치로 표현해주세요.");
	}
}
