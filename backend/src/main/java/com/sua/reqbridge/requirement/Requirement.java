package com.sua.reqbridge.requirement;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.sua.reqbridge.contract.RequirementStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "requirement", schema = "app")
public class Requirement {
	private static final long MAX_CONTENT_VERSION = 9_007_199_254_740_991L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "document_id", nullable = false, updatable = false)
	private long documentId;

	@Column(name = "analysis_id", nullable = false, updatable = false)
	private long analysisId;

	@Column(name = "sequence_no", nullable = false, updatable = false)
	private int sequenceNo;

	@Column(name = "original_text", nullable = false, updatable = false, columnDefinition = "text")
	private String originalText;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(nullable = false, columnDefinition = "requirement_status")
	private RequirementStatus status;

	@Column(name = "content_version", nullable = false)
	private long contentVersion;

	@Column(name = "approved_revision_id")
	private Long approvedRevisionId;

	@Column(name = "confirmed_text", columnDefinition = "text")
	private String confirmedText;

	@Column(name = "confirmed_at")
	private Instant confirmedAt;

	@Version
	@Column(name = "lock_version", nullable = false)
	private long lockVersion;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Requirement() {
	}

	public Requirement(long documentId, long analysisId, int sequenceNo, String originalText) {
		this.documentId = documentId;
		this.analysisId = analysisId;
		this.sequenceNo = sequenceNo;
		this.originalText = originalText;
		this.status = RequirementStatus.EXTRACTED;
		this.contentVersion = 1;
	}

	public long advanceContentVersion(long expectedContentVersion) {
		verifyNotConfirmed();
		verifyContentVersion(expectedContentVersion);
		if (contentVersion >= MAX_CONTENT_VERSION) {
			throw new RequirementStateException("Requirement content version limit reached");
		}
		contentVersion++;
		return contentVersion;
	}

	public void changeStatus(long expectedContentVersion, RequirementStatus targetStatus) {
		verifyNotConfirmed();
		verifyContentVersion(expectedContentVersion);
		if (targetStatus == null || targetStatus == RequirementStatus.CONFIRMED) {
			throw new RequirementStateException("CONFIRMED requires an approved revision");
		}
		status = targetStatus;
	}

	public void confirm(
			long expectedContentVersion,
			long revisionId,
			String approvedText,
			Instant confirmationTime) {
		verifyNotConfirmed();
		verifyContentVersion(expectedContentVersion);
		if (status != RequirementStatus.IN_REVIEW) {
			throw new RequirementStateException("Only a requirement in review can be confirmed");
		}
		if (revisionId <= 0) {
			throw new IllegalArgumentException("Revision id must be positive");
		}
		if (approvedText == null || approvedText.isBlank()) {
			throw new IllegalArgumentException("Approved text must not be blank");
		}
		if (confirmationTime == null) {
			throw new IllegalArgumentException("Confirmation time must not be null");
		}
		approvedRevisionId = revisionId;
		confirmedText = approvedText;
		confirmedAt = confirmationTime;
		status = RequirementStatus.CONFIRMED;
	}

	private void verifyContentVersion(long expectedContentVersion) {
		if (expectedContentVersion < 1 || expectedContentVersion > MAX_CONTENT_VERSION) {
			throw new IllegalArgumentException("Expected content version must be a positive JSON-safe integer");
		}
		if (contentVersion != expectedContentVersion) {
			throw new RequirementStateException(
					"CONTENT_VERSION_CONFLICT",
					"Requirement content version mismatch: expected %d but was %d"
							.formatted(expectedContentVersion, contentVersion));
		}
	}

	private void verifyNotConfirmed() {
		if (status == RequirementStatus.CONFIRMED) {
			throw new RequirementStateException("REQUIREMENT_CONFIRMED",
					"A confirmed requirement cannot be reopened or changed in MVP");
		}
	}

	public Long getId() {
		return id;
	}

	public long getDocumentId() {
		return documentId;
	}

	public long getAnalysisId() {
		return analysisId;
	}

	public int getSequenceNo() {
		return sequenceNo;
	}

	public String getOriginalText() {
		return originalText;
	}

	public RequirementStatus getStatus() {
		return status;
	}

	public long getContentVersion() {
		return contentVersion;
	}

	public Long getApprovedRevisionId() {
		return approvedRevisionId;
	}

	public String getConfirmedText() {
		return confirmedText;
	}

	public Instant getConfirmedAt() {
		return confirmedAt;
	}

	public long getLockVersion() {
		return lockVersion;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
