package com.sua.reqbridge.requirement;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.sua.reqbridge.contract.CoreRequirementPort;
import com.sua.reqbridge.contract.DocumentSnapshot;
import com.sua.reqbridge.contract.RequirementSeed;
import com.sua.reqbridge.contract.RequirementSnapshot;
import com.sua.reqbridge.contract.RequirementStatus;
import com.sua.reqbridge.document.Document;
import com.sua.reqbridge.document.DocumentService;

@Component
public class CoreRequirementAdapter implements CoreRequirementPort {

	private final DocumentService documentService;
	private final RequirementCoreService requirementCoreService;

	public CoreRequirementAdapter(
			DocumentService documentService,
			RequirementCoreService requirementCoreService) {
		this.documentService = documentService;
		this.requirementCoreService = requirementCoreService;
	}

	@Override
	public DocumentSnapshot getDocument(long documentId) {
		return toSnapshot(documentService.get(documentId));
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public DocumentSnapshot lockDocument(long documentId) {
		return toSnapshot(documentService.lock(documentId));
	}

	@Override
	public List<RequirementSnapshot> createRequirements(
			long documentId,
			long analysisId,
			List<RequirementSeed> items) {
		List<NewRequirement> newRequirements = items == null
				? null
				: items.stream()
						.map(item -> item == null
								? null
								: new NewRequirement(item.sequenceNo(), item.originalText()))
						.toList();
		return requirementCoreService.createRequirements(documentId, analysisId, newRequirements).stream()
				.map(CoreRequirementAdapter::toSnapshot)
				.toList();
	}

	@Override
	public RequirementSnapshot getRequirement(long requirementId) {
		return toSnapshot(requirementCoreService.get(requirementId));
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public RequirementSnapshot lockRequirement(long requirementId) {
		return toSnapshot(requirementCoreService.lock(requirementId));
	}

	@Override
	public long advanceContentVersion(long requirementId, long expectedContentVersion) {
		return requirementCoreService.advanceContentVersion(requirementId, expectedContentVersion);
	}

	@Override
	public void changeStatus(
			long requirementId,
			long expectedContentVersion,
			RequirementStatus targetStatus) {
		requirementCoreService.changeStatus(requirementId, expectedContentVersion, targetStatus);
	}

	@Override
	public void confirmRequirement(
			long requirementId,
			long expectedContentVersion,
			long revisionId,
			String approvedText) {
		requirementCoreService.confirmRequirement(
				requirementId,
				expectedContentVersion,
				revisionId,
				approvedText);
	}

	@Override
	public List<RequirementSnapshot> listRequirements(long documentId) {
		return requirementCoreService.listByDocument(documentId).stream()
				.map(CoreRequirementAdapter::toSnapshot)
				.toList();
	}

	private static DocumentSnapshot toSnapshot(Document document) {
		return new DocumentSnapshot(
				document.getId(),
				document.getProjectId(),
				document.getTitle(),
				document.getContent(),
				document.getSourceType().name());
	}

	private static RequirementSnapshot toSnapshot(Requirement requirement) {
		return new RequirementSnapshot(
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
