package com.sua.reqbridge.requirement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.sua.reqbridge.contract.RequirementStatus;

class RequirementTests {

	private static final Instant CONFIRMED_AT = Instant.parse("2026-09-02T06:00:00Z");

	@Test
	void usesThePublicFiveStageStatusContract() {
		assertThat(RequirementStatus.values()).containsExactly(
				RequirementStatus.EXTRACTED,
				RequirementStatus.AMBIGUOUS,
				RequirementStatus.CLARIFYING,
				RequirementStatus.IN_REVIEW,
				RequirementStatus.CONFIRMED);
	}

	@Test
	void advancesContentVersionOnlyWhenExpectedVersionMatches() {
		Requirement requirement = new Requirement(1L, 2L, 1, "원문");
		assertThat(requirement.getStatus()).isEqualTo(RequirementStatus.EXTRACTED);

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

		assertThatThrownBy(() -> requirement.changeStatus(1L, RequirementStatus.CLARIFYING))
				.isInstanceOf(RequirementStateException.class)
				.hasMessageContaining("cannot be reopened");
	}

	@Test
	void supportsFiveStageWorkflowWithoutDirectConfirmation() {
		Requirement requirement = new Requirement(1L, 2L, 1, "원문");

		requirement.changeStatus(1L, RequirementStatus.AMBIGUOUS);
		requirement.changeStatus(1L, RequirementStatus.CLARIFYING);
		requirement.changeStatus(1L, RequirementStatus.IN_REVIEW);

		assertThat(requirement.getStatus()).isEqualTo(RequirementStatus.IN_REVIEW);
		assertThatThrownBy(() -> requirement.changeStatus(1L, RequirementStatus.CONFIRMED))
				.isInstanceOf(RequirementStateException.class)
				.hasMessageContaining("approved revision");
	}

	@Test
	void staleWritesReturnTheContractCodeAndLeaveTheSnapshotUntouched() {
		Requirement requirement = new Requirement(1L, 2L, 1, "고객 원문");
		requirement.changeStatus(1L, RequirementStatus.IN_REVIEW);
		requirement.advanceContentVersion(1L);
		assertVersionConflict(() -> requirement.advanceContentVersion(1L));
		assertVersionConflict(() -> requirement.changeStatus(1L, RequirementStatus.CLARIFYING));
		assertVersionConflict(() -> requirement.confirm(1L, 7L, "수정안", CONFIRMED_AT));
		assertThat(requirement.getContentVersion()).isEqualTo(2L);
		assertThat(requirement.getStatus()).isEqualTo(RequirementStatus.IN_REVIEW);
		assertThat(requirement.getOriginalText()).isEqualTo("고객 원문");
		assertThat(requirement.getApprovedRevisionId()).isNull();
		assertThat(requirement.getConfirmedText()).isNull();
		assertThat(requirement.getConfirmedAt()).isNull();
	}

	@Test
	void confirmedHistoryCannotBeAdvancedReopenedOrOverwritten() {
		Requirement requirement = new Requirement(1L, 2L, 1, "고객 원문");
		requirement.changeStatus(1L, RequirementStatus.IN_REVIEW);
		requirement.confirm(1L, 7L, "승인 본문", CONFIRMED_AT);
		assertConfirmedConflict(() -> requirement.advanceContentVersion(1L));
		assertConfirmedConflict(() -> requirement.changeStatus(1L, RequirementStatus.CLARIFYING));
		assertConfirmedConflict(() -> requirement.confirm(1L, 8L, "덮어쓰기", CONFIRMED_AT.plusSeconds(1)));
		assertThat(requirement.getContentVersion()).isEqualTo(1L);
		assertThat(requirement.getStatus()).isEqualTo(RequirementStatus.CONFIRMED);
		assertThat(requirement.getApprovedRevisionId()).isEqualTo(7L);
		assertThat(requirement.getConfirmedText()).isEqualTo("승인 본문");
		assertThat(requirement.getConfirmedAt()).isEqualTo(CONFIRMED_AT);
		assertThat(requirement.getOriginalText()).isEqualTo("고객 원문");
	}

	@ParameterizedTest
	@ValueSource(longs = {0L, -1L, 9_007_199_254_740_992L})
	void rejectsInvalidExpectedVersionWithoutChangingData(long version) {
		Requirement requirement = new Requirement(1L, 2L, 1, "원문");
		assertThatThrownBy(() -> requirement.advanceContentVersion(version)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> requirement.changeStatus(version, RequirementStatus.IN_REVIEW))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> requirement.confirm(version, 7L, "확정본", CONFIRMED_AT))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(requirement.getContentVersion()).isEqualTo(1L);
		assertThat(requirement.getStatus()).isEqualTo(RequirementStatus.EXTRACTED);
	}

	@Test
	void doesNotOverflowThePublicVersionRange() {
		Requirement requirement = new Requirement(1L, 2L, 1, "원문");
		ReflectionTestUtils.setField(requirement, "contentVersion", 9_007_199_254_740_991L);
		assertThatThrownBy(() -> requirement.advanceContentVersion(9_007_199_254_740_991L))
				.isInstanceOf(RequirementStateException.class);
		assertThat(requirement.getContentVersion()).isEqualTo(9_007_199_254_740_991L);
	}

	private static void assertVersionConflict(ThrowingCallable action) {
		assertThatThrownBy(action).isInstanceOfSatisfying(RequirementStateException.class,
				exception -> assertThat(exception.getCode()).isEqualTo("CONTENT_VERSION_CONFLICT"));
	}

	private static void assertConfirmedConflict(ThrowingCallable action) {
		assertThatThrownBy(action).isInstanceOfSatisfying(RequirementStateException.class,
				exception -> assertThat(exception.getCode()).isEqualTo("REQUIREMENT_CONFIRMED"));
	}
}
