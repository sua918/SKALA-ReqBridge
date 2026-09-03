package com.sua.reqbridge.revision;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import com.sua.reqbridge.contract.RevisionStatus;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "requirement_revision", schema = "app")
public class RequirementRevision {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "requirement_id", nullable = false)
	private Long requirementId;

	@Column(name = "revision_no", nullable = false)
	private int revisionNo;

	@Column(name = "proposed_text", nullable = false)
	private String text;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private RevisionStatus status;

	@Column(name = "input_content_version", nullable = false)
	private Long inputContentVersion;

	@Column(name = "rejection_reason")
	private String rejectionReason;

	@ElementCollection(fetch = FetchType.LAZY)
	@CollectionTable(name = "revision_clarification", schema = "app",
			joinColumns = @JoinColumn(name = "revision_id"))
	@Column(name = "clarification_id", nullable = false)
	private Set<Long> basedOnClarificationIds = new LinkedHashSet<>();

	protected RequirementRevision() {
	}

	private RequirementRevision(long requirementId, int revisionNo, String text,
			long inputContentVersion, Collection<Long> basedOnClarificationIds) {
		if (revisionNo < 1 || inputContentVersion < 1) {
			throw new IllegalArgumentException("revisionNo와 inputContentVersion은 1 이상이어야 합니다.");
		}
		this.requirementId = requirementId;
		this.revisionNo = revisionNo;
		this.text = Objects.requireNonNull(text);
		this.status = RevisionStatus.PROPOSED;
		this.inputContentVersion = inputContentVersion;
		this.basedOnClarificationIds.addAll(Objects.requireNonNull(basedOnClarificationIds));
	}

	public static RequirementRevision proposed(long requirementId, int revisionNo, String text,
			long inputContentVersion, Collection<Long> basedOnClarificationIds) {
		return new RequirementRevision(requirementId, revisionNo, text,
				inputContentVersion, basedOnClarificationIds);
	}

	public void approve() {
		if (status != RevisionStatus.PROPOSED) {
			throw new IllegalStateException("PROPOSED 수정안만 승인할 수 있습니다.");
		}
		status = RevisionStatus.APPROVED;
	}

	public void reject(String rejectionReason) {
		if (status != RevisionStatus.PROPOSED) {
			throw new IllegalStateException("PROPOSED 수정안만 거절할 수 있습니다.");
		}
		this.rejectionReason = Objects.requireNonNull(rejectionReason);
		this.status = RevisionStatus.REJECTED;
	}

	public Long getId() {
		return id;
	}

	public Long getRequirementId() {
		return requirementId;
	}

	public int getRevisionNo() {
		return revisionNo;
	}

	public String getText() {
		return text;
	}

	public RevisionStatus getStatus() {
		return status;
	}

	public Long getInputContentVersion() {
		return inputContentVersion;
	}

	public String getRejectionReason() {
		return rejectionReason;
	}

	public Set<Long> getBasedOnClarificationIds() {
		return Set.copyOf(basedOnClarificationIds);
	}
}
