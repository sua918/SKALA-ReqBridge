package com.sua.reqbridge.requirement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sua.reqbridge.document.DocumentRepository;

@ExtendWith(MockitoExtension.class)
class RequirementCoreServiceTests {

	@Mock
	private RequirementRepository requirementRepository;

	@Mock
	private DocumentRepository documentRepository;

	@Test
	void createsRequirementsInSequenceOrder() {
		when(documentRepository.existsById(1L)).thenReturn(true);
		when(requirementRepository.saveAll(anyList()))
				.thenAnswer(invocation -> invocation.getArgument(0));
		RequirementCoreService service = new RequirementCoreService(
				requirementRepository, documentRepository);

		List<Requirement> created = service.createRequirements(
				1L,
				10L,
				List.of(new NewRequirement(2, "두 번째"), new NewRequirement(1, "첫 번째")));

		assertThat(created).extracting(Requirement::getSequenceNo).containsExactly(1, 2);
		assertThat(created).allSatisfy(requirement -> {
			assertThat(requirement.getDocumentId()).isEqualTo(1L);
			assertThat(requirement.getAnalysisId()).isEqualTo(10L);
			assertThat(requirement.getContentVersion()).isEqualTo(1L);
			assertThat(requirement.getStatus()).isEqualTo(RequirementStatus.OPEN);
		});
	}

	@Test
	void rejectsSequenceWithGap() {
		when(documentRepository.existsById(1L)).thenReturn(true);
		RequirementCoreService service = new RequirementCoreService(
				requirementRepository, documentRepository);

		assertThatThrownBy(() -> service.createRequirements(
				1L,
				10L,
				List.of(new NewRequirement(1, "첫 번째"), new NewRequirement(3, "세 번째"))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("without gaps");
	}

	@Test
	void rejectsOriginalTextContainingOnlyContractWhitespace() {
		when(documentRepository.existsById(1L)).thenReturn(true);
		RequirementCoreService service = new RequirementCoreService(
				requirementRepository, documentRepository);

		assertThatThrownBy(() -> service.createRequirements(
				1L,
				10L,
				List.of(new NewRequirement(1, "\u00A0\uFEFF"))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("original text");
	}
}
