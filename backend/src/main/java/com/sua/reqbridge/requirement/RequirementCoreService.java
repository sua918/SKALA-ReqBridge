package com.sua.reqbridge.requirement;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.sua.reqbridge.common.validation.TextRules;
import com.sua.reqbridge.contract.RequirementStatus;
import com.sua.reqbridge.document.DocumentNotFoundException;
import com.sua.reqbridge.document.DocumentRepository;

@Service
@Transactional(readOnly = true)
public class RequirementCoreService {

	private final RequirementRepository requirementRepository;
	private final DocumentRepository documentRepository;
	private final Clock clock;

	@Autowired
	public RequirementCoreService(
			RequirementRepository requirementRepository,
			DocumentRepository documentRepository) {
		this(requirementRepository, documentRepository, Clock.systemUTC());
	}

	RequirementCoreService(
			RequirementRepository requirementRepository,
			DocumentRepository documentRepository,
			Clock clock) {
		this.requirementRepository = requirementRepository;
		this.documentRepository = documentRepository;
		this.clock = clock;
	}

	@Transactional
	public List<Requirement> createRequirements(
			long documentId,
			long analysisId,
			List<NewRequirement> items) {
		if (!documentRepository.existsById(documentId)) {
			throw new DocumentNotFoundException(documentId);
		}
		if (analysisId <= 0) {
			throw new IllegalArgumentException("Analysis id must be positive");
		}
		validateItems(items);

		List<Requirement> requirements = items.stream()
				.map(item -> new Requirement(documentId, analysisId, item.sequenceNo(), item.originalText()))
				.toList();
		return requirementRepository.saveAll(requirements).stream()
				.sorted(Comparator.comparingInt(Requirement::getSequenceNo))
				.toList();
	}

	public Requirement get(long requirementId) {
		return requirementRepository.findById(requirementId)
				.orElseThrow(() -> new RequirementNotFoundException(requirementId));
	}

	public List<Requirement> listByDocument(long documentId) {
		if (!documentRepository.existsById(documentId)) {
			throw new DocumentNotFoundException(documentId);
		}
		return requirementRepository.findByDocumentIdOrderBySequenceNoAsc(documentId);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public Requirement lock(long requirementId) {
		return requirementRepository.findByIdForUpdate(requirementId)
				.orElseThrow(() -> new RequirementNotFoundException(requirementId));
	}

	@Transactional
	public long advanceContentVersion(long requirementId, long expectedContentVersion) {
		return lock(requirementId).advanceContentVersion(expectedContentVersion);
	}

	@Transactional
	public void changeStatus(
			long requirementId,
			long expectedContentVersion,
			RequirementStatus targetStatus) {
		lock(requirementId).changeStatus(expectedContentVersion, targetStatus);
	}

	@Transactional
	public void confirmRequirement(
			long requirementId,
			long expectedContentVersion,
			long revisionId,
			String approvedText) {
		lock(requirementId).confirm(
				expectedContentVersion,
				revisionId,
				approvedText,
				Instant.now(clock));
	}

	private void validateItems(List<NewRequirement> items) {
		if (items == null || items.isEmpty()) {
			throw new IllegalArgumentException("At least one requirement is required");
		}

		Set<Integer> sequenceNumbers = new HashSet<>();
		for (NewRequirement item : items) {
			if (item == null) {
				throw new IllegalArgumentException("Requirement original text must not be blank");
			}
			TextRules.requiredPreserved("Requirement original text", item.originalText(), 100_000);
			if (item.sequenceNo() <= 0 || !sequenceNumbers.add(item.sequenceNo())) {
				throw new IllegalArgumentException("Requirement sequence numbers must be positive and unique");
			}
		}

		for (int sequenceNo = 1; sequenceNo <= items.size(); sequenceNo++) {
			if (!sequenceNumbers.contains(sequenceNo)) {
				throw new IllegalArgumentException("Requirement sequence numbers must start at 1 without gaps");
			}
		}
	}
}
