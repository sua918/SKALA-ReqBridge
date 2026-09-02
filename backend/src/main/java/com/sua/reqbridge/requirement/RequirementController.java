package com.sua.reqbridge.requirement;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.sua.reqbridge.common.api.ApiResponse;
import com.sua.reqbridge.common.api.ItemList;
import com.sua.reqbridge.contract.RequirementStatus;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;

@Validated
@RestController
public class RequirementController {

	private final RequirementCoreService requirementCoreService;

	public RequirementController(RequirementCoreService requirementCoreService) {
		this.requirementCoreService = requirementCoreService;
	}

	@GetMapping("/api/documents/{documentId}/requirements")
	public ApiResponse<ItemList<RequirementView>> listByDocument(
			@Positive @Max(9_007_199_254_740_991L) @PathVariable long documentId) {
		List<RequirementView> requirements = requirementCoreService.listByDocument(documentId).stream()
				.map(RequirementView::from)
				.toList();
		return ApiResponse.of(new ItemList<>(requirements));
	}

	@GetMapping("/api/requirements/{requirementId}")
	public ApiResponse<RequirementView> get(
			@Positive @Max(9_007_199_254_740_991L) @PathVariable long requirementId) {
		return ApiResponse.of(RequirementView.from(requirementCoreService.get(requirementId)));
	}

	public record RequirementView(
			long id,
			long documentId,
			long analysisId,
			int sequenceNo,
			String originalText,
			RequirementStatus status,
			long contentVersion,
			Long approvedRevisionId,
			String confirmedText) {

		static RequirementView from(Requirement requirement) {
			return new RequirementView(
					requirement.getId(),
					requirement.getDocumentId(),
					requirement.getAnalysisId(),
					requirement.getSequenceNo(),
					requirement.getOriginalText(),
					requirement.getStatus(),
					requirement.getContentVersion(),
					requirement.getApprovedRevisionId(),
					requirement.getConfirmedText());
		}
	}
}
