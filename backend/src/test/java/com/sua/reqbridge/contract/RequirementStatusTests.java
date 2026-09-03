package com.sua.reqbridge.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RequirementStatusTests {

	@Test
	void parsesTheFiveApiContractValuesAndRejectsLegacyOpen() {
		assertThat(RequirementStatus.valueOf("EXTRACTED").name()).isEqualTo("EXTRACTED");
		assertThat(RequirementStatus.valueOf("AMBIGUOUS").name()).isEqualTo("AMBIGUOUS");
		assertThat(RequirementStatus.valueOf("CLARIFYING").name()).isEqualTo("CLARIFYING");
		assertThat(RequirementStatus.valueOf("IN_REVIEW").name()).isEqualTo("IN_REVIEW");
		assertThat(RequirementStatus.valueOf("CONFIRMED").name()).isEqualTo("CONFIRMED");
		assertThatThrownBy(() -> RequirementStatus.valueOf("OPEN"))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
