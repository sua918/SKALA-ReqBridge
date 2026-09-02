package com.sua.reqbridge.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ContractCollectionImmutabilityTests {

	@Test
	void convertsNullCollectionsToEmptyLists() {
		assertThat(new WorkflowPreviewSnapshot(1L, null).requirements()).isEmpty();

		WorkflowRequirementSnapshot requirement = new WorkflowRequirementSnapshot(1L, null, null, null);
		assertThat(requirement.issues()).isEmpty();
		assertThat(requirement.questions()).isEmpty();

		ApprovedRevisionSnapshot revision = new ApprovedRevisionSnapshot(1L, 1, "approved", null, null);
		assertThat(revision.basedOnClarificationIds()).isEmpty();
		assertThat(revision.acceptanceCriteria()).isEmpty();
	}

	@Test
	void defensivelyCopiesCollections() {
		List<WorkflowRequirementSnapshot> requirements = new ArrayList<>();
		WorkflowPreviewSnapshot preview = new WorkflowPreviewSnapshot(1L, requirements);
		List<IssueSnapshot> issues = new ArrayList<>();
		List<QuestionSnapshot> questions = new ArrayList<>();
		WorkflowRequirementSnapshot requirement =
				new WorkflowRequirementSnapshot(1L, issues, questions, null);
		List<Long> clarificationIds = new ArrayList<>();
		List<AcceptanceCriterionSnapshot> acceptanceCriteria = new ArrayList<>();
		ApprovedRevisionSnapshot revision =
				new ApprovedRevisionSnapshot(1L, 1, "approved", clarificationIds, acceptanceCriteria);

		requirements.add(new WorkflowRequirementSnapshot(1L, List.of(), List.of(), null));
		issues.add(new IssueSnapshot(1L, AmbiguityType.TERM_AMBIGUOUS, "evidence", IssueStatus.OPEN));
		questions.add(new QuestionSnapshot(
				1L, 1L, 1L, 1, "question", null, ClarificationStatus.WAITING));
		clarificationIds.add(1L);
		acceptanceCriteria.add(new AcceptanceCriterionSnapshot("given", "when", "then"));

		assertThat(preview.requirements()).isEmpty();
		assertThat(requirement.issues()).isEmpty();
		assertThat(requirement.questions()).isEmpty();
		assertThat(revision.basedOnClarificationIds()).isEmpty();
		assertThat(revision.acceptanceCriteria()).isEmpty();
		assertThatThrownBy(() -> preview.requirements().add(null))
				.isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> requirement.issues().add(null))
				.isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> requirement.questions().add(null))
				.isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> revision.basedOnClarificationIds().add(null))
				.isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> revision.acceptanceCriteria().add(null))
				.isInstanceOf(UnsupportedOperationException.class);
	}
}
