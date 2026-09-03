package com.sua.reqbridge.revision;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RequirementRevisionRepository extends JpaRepository<RequirementRevision, Long> {

	Optional<RequirementRevision> findTopByRequirementIdOrderByRevisionNoDesc(long requirementId);

	List<RequirementRevision> findByRequirementIdOrderByRevisionNoDesc(long requirementId);

	boolean existsByRequirementIdAndStatus(long requirementId, com.sua.reqbridge.contract.RevisionStatus status);
}
