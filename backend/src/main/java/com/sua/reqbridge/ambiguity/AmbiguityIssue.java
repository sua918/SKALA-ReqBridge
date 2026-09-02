package com.sua.reqbridge.ambiguity;

import java.util.Objects;

import com.sua.reqbridge.contract.AmbiguityType;
import com.sua.reqbridge.contract.IssueStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ambiguity_issue", schema = "app")
public class AmbiguityIssue {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "requirement_id", nullable = false)
	private Long requirementId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AmbiguityType type;

	@Column(nullable = false)
	private String evidence;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private IssueStatus status;

	protected AmbiguityIssue() {
	}

	private AmbiguityIssue(long requirementId, AmbiguityType type, String evidence) {
		this.requirementId = requirementId;
		this.type = Objects.requireNonNull(type);
		this.evidence = Objects.requireNonNull(evidence);
		this.status = IssueStatus.OPEN;
	}

	public static AmbiguityIssue open(long requirementId, AmbiguityType type, String evidence) {
		return new AmbiguityIssue(requirementId, type, evidence);
	}

	public void resolve() {
		status = IssueStatus.RESOLVED;
	}

	public Long getId() {
		return id;
	}

	public Long getRequirementId() {
		return requirementId;
	}

	public AmbiguityType getType() {
		return type;
	}

	public String getEvidence() {
		return evidence;
	}

	public IssueStatus getStatus() {
		return status;
	}
}
