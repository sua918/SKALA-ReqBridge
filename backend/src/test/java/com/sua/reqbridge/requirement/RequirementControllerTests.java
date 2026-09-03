package com.sua.reqbridge.requirement;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.sua.reqbridge.contract.RequirementStatus;

@WebMvcTest(RequirementController.class)
class RequirementControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RequirementCoreService requirementCoreService;

	@Test
	void listsRequirementsInItemsWrapper() throws Exception {
		Requirement requirement = requirement(401L);
		when(requirementCoreService.listByDocument(101L)).thenReturn(List.of(requirement));

		mockMvc.perform(get("/api/documents/101/requirements"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].id").value(401))
				.andExpect(jsonPath("$.data.items[0].status").value("EXTRACTED"))
				.andExpect(jsonPath("$.data.items[0].approvedRevisionId").isEmpty())
				.andExpect(jsonPath("$.data.items[0].confirmedText").isEmpty());
	}

	private Requirement requirement(long id) {
		Requirement requirement = mock(Requirement.class);
		when(requirement.getId()).thenReturn(id);
		when(requirement.getDocumentId()).thenReturn(101L);
		when(requirement.getAnalysisId()).thenReturn(301L);
		when(requirement.getSequenceNo()).thenReturn(1);
		when(requirement.getOriginalText()).thenReturn("고객 원문");
		when(requirement.getStatus()).thenReturn(RequirementStatus.EXTRACTED);
		when(requirement.getContentVersion()).thenReturn(1L);
		when(requirement.getApprovedRevisionId()).thenReturn(null);
		return requirement;
	}
}
