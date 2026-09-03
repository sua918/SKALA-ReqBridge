package com.sua.reqbridge.analysis;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sua.reqbridge.contract.AnalysisKind;
import com.sua.reqbridge.contract.AnalysisStatus;

public interface AnalysisRepository extends JpaRepository<Analysis, Long> {

	boolean existsByDocumentIdAndKindAndStatusIn(
			long documentId, AnalysisKind kind, Collection<AnalysisStatus> statuses);

	boolean existsByDocumentIdAndKindAndStatus(
			long documentId, AnalysisKind kind, AnalysisStatus status);

	List<Analysis> findByDocumentIdOrderByIdDesc(long documentId);

	List<Analysis> findByDocumentIdAndKindOrderByIdDesc(long documentId, AnalysisKind kind);

	Optional<Analysis> findFirstByClarificationIdAndKindOrderByIdDesc(
			long clarificationId, AnalysisKind kind);

	boolean existsByRequirementIdAndStatusIn(long requirementId, Collection<AnalysisStatus> statuses);

	Optional<Analysis> findFirstByRequirementIdAndStatusInOrderByIdDesc(
			long requirementId, Collection<AnalysisStatus> statuses);

	Optional<Analysis> findFirstByRetryOfAnalysisIdOrderByIdDesc(long retryOfAnalysisId);
}
