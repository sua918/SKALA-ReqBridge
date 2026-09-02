package com.sua.reqbridge.clarification;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ClarificationRepository extends JpaRepository<Clarification, Long> {

	Optional<Clarification> findTopByIssueIdOrderByRoundNoDesc(long issueId);

	List<Clarification> findByRequirementIdOrderByIssueIdAscRoundNoAsc(long requirementId);
}
