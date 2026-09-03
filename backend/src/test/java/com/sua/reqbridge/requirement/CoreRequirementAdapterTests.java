package com.sua.reqbridge.requirement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sua.reqbridge.contract.DocumentSnapshot;
import com.sua.reqbridge.contract.RequirementSeed;
import com.sua.reqbridge.contract.RequirementSnapshot;
import com.sua.reqbridge.contract.RequirementStatus;
import com.sua.reqbridge.document.Document;
import com.sua.reqbridge.document.DocumentService;
import com.sua.reqbridge.document.DocumentSourceType;

class CoreRequirementAdapterTests {

	private final DocumentService documentService = mock(DocumentService.class);
	private final RequirementCoreService requirementCoreService = mock(RequirementCoreService.class);
	private final CoreRequirementAdapter adapter =
			new CoreRequirementAdapter(documentService, requirementCoreService);

	@Test
	void mapsCoreEntitiesToPublicContractSnapshots() {
		Document document = mock(Document.class);
		when(document.getId()).thenReturn(101L);
		when(document.getProjectId()).thenReturn(1L);
		when(document.getTitle()).thenReturn("요구사항");
		when(document.getContent()).thenReturn("고객 원문");
		when(document.getSourceType()).thenReturn(DocumentSourceType.TEXT);
		when(documentService.get(101L)).thenReturn(document);

		Requirement requirement = requirement(401L, RequirementStatus.AMBIGUOUS);
		when(requirementCoreService.get(401L)).thenReturn(requirement);

		DocumentSnapshot documentSnapshot = adapter.getDocument(101L);
		RequirementSnapshot requirementSnapshot = adapter.getRequirement(401L);

		assertThat(documentSnapshot.sourceType()).isEqualTo("TEXT");
		assertThat(requirementSnapshot.status()).isEqualTo(RequirementStatus.AMBIGUOUS);
		assertThat(requirementSnapshot.contentVersion()).isEqualTo(2L);
	}

	@Test
	void convertsPublicSeedsAndForwardsPublicStatus() {
		Requirement requirement = requirement(401L, RequirementStatus.EXTRACTED);
		when(requirementCoreService.createRequirements(anyLong(), anyLong(), anyList()))
				.thenReturn(List.of(requirement));

		List<RequirementSnapshot> created = adapter.createRequirements(
				101L,
				301L,
				List.of(new RequirementSeed(1, "고객 원문")));
		adapter.changeStatus(401L, 2L, RequirementStatus.CLARIFYING);

		assertThat(created).singleElement()
				.extracting(RequirementSnapshot::status)
				.isEqualTo(RequirementStatus.EXTRACTED);
		verify(requirementCoreService).changeStatus(401L, 2L, RequirementStatus.CLARIFYING);
	}

	private Requirement requirement(long id, RequirementStatus status) {
		Requirement requirement = mock(Requirement.class);
		when(requirement.getId()).thenReturn(id);
		when(requirement.getDocumentId()).thenReturn(101L);
		when(requirement.getAnalysisId()).thenReturn(301L);
		when(requirement.getSequenceNo()).thenReturn(1);
		when(requirement.getOriginalText()).thenReturn("고객 원문");
		when(requirement.getStatus()).thenReturn(status);
		when(requirement.getContentVersion()).thenReturn(2L);
		return requirement;
	}
}
