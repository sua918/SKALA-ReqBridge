package com.sua.reqbridge.ambiguity;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sua.reqbridge.contract.IssueStatus;

public interface AmbiguityIssueRepository extends JpaRepository<AmbiguityIssue, Long> {

	long countByRequirementIdAndStatus(long requirementId, IssueStatus status);

	List<AmbiguityIssue> findByRequirementIdOrderByIdAsc(long requirementId);
}
