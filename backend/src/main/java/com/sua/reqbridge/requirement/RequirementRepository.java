package com.sua.reqbridge.requirement;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface RequirementRepository extends JpaRepository<Requirement, Long> {

	List<Requirement> findByDocumentIdOrderBySequenceNoAsc(long documentId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from Requirement r where r.id = :requirementId")
	Optional<Requirement> findByIdForUpdate(@Param("requirementId") long requirementId);
}
