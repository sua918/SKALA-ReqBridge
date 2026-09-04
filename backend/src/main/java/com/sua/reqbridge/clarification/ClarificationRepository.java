package com.sua.reqbridge.clarification;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sua.reqbridge.contract.ClarificationStatus;

public interface ClarificationRepository extends JpaRepository<Clarification, Long> {

	Optional<Clarification> findTopByIssueIdOrderByRoundNoDesc(long issueId);

	List<Clarification> findByRequirementIdOrderByIssueIdAscRoundNoAsc(long requirementId);

	long countByRequirementIdAndStatus(long requirementId, ClarificationStatus status);
}

