package com.sua.reqbridge.requirement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class RequirementTests {

	private static final Instant CONFIRMED_AT = Instant.parse("2026-09-02T06:00:00Z");

	@Test
	void advancesContentVersionOnlyWhenExpectedVersionMatches() {
		Requirement requirement = new Requirement(1L, 2L, 1, "원문");

		assertThat(requirement.advanceContentVersion(1L)).isEqualTo(2L);
		assertThatThrownBy(() -> requirement.advanceContentVersion(1L))
				.isInstanceOf(RequirementStateException.class)
				.hasMessageContaining("version mismatch");
	}

	@Test
	void confirmsOnlyRequirementInReview() {
		Requirement requirement = new Requirement(1L, 2L, 1, "원문");

		assertThatThrownBy(() -> requirement.confirm(1L, 7L, "확정 본문", CONFIRMED_AT))
				.isInstanceOf(RequirementStateException.class);

		requirement.changeStatus(1L, RequirementStatus.IN_REVIEW);
		requirement.confirm(1L, 7L, "확정 본문", CONFIRMED_AT);

		assertThat(requirement.getStatus()).isEqualTo(RequirementStatus.CONFIRMED);
		assertThat(requirement.getApprovedRevisionId()).isEqualTo(7L);
		assertThat(requirement.getConfirmedText()).isEqualTo("확정 본문");
		assertThat(requirement.getConfirmedAt()).isEqualTo(CONFIRMED_AT);
	}

	@Test
	void doesNotAllowConfirmedRequirementToReopen() {
		Requirement requirement = new Requirement(1L, 2L, 1, "원문");
		requirement.changeStatus(1L, RequirementStatus.IN_REVIEW);
		requirement.confirm(1L, 7L, "확정 본문", CONFIRMED_AT);

		assertThatThrownBy(() -> requirement.changeStatus(1L, RequirementStatus.OPEN))
				.isInstanceOf(RequirementStateException.class)
				.hasMessageContaining("cannot be reopened");
	}
}
