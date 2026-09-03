package com.sua.reqbridge.revision;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.sua.reqbridge.contract.RevisionSource;
import com.sua.reqbridge.contract.RevisionStatus;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
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
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(nullable = false, columnDefinition = "revision_status")
	private RevisionStatus status;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "source", nullable = false, columnDefinition = "revision_source")
	private RevisionSource source;

	@Column(name = "input_content_version", nullable = false)
	private Long inputContentVersion;

	@Column(name = "rejection_reason")
	private String rejectionReason;

	@ElementCollection(fetch = FetchType.LAZY)
	@CollectionTable(name = "revision_clarification", schema = "app",
			joinColumns = @JoinColumn(name = "revision_id"))
	private Set<ClarificationRef> basedOnClarifications = new LinkedHashSet<>();

	protected RequirementRevision() {
	}

	private RequirementRevision(long requirementId, int revisionNo, String text,
			long inputContentVersion, Collection<Long> basedOnClarificationIds) {
		this(requirementId, revisionNo, text, inputContentVersion, basedOnClarificationIds, RevisionSource.AI);
	}

	private RequirementRevision(long requirementId, int revisionNo, String text,
			long inputContentVersion, Collection<Long> basedOnClarificationIds, RevisionSource source) {
		if (revisionNo < 1 || inputContentVersion < 1) {
			throw new IllegalArgumentException("revisionNo와 inputContentVersion은 1 이상이어야 합니다.");
		}
		this.requirementId = requirementId;
		this.revisionNo = revisionNo;
		this.text = Objects.requireNonNull(text);
		this.status = RevisionStatus.PROPOSED;
		this.source = Objects.requireNonNull(source);
		this.inputContentVersion = inputContentVersion;
		for (Long clarificationId : Objects.requireNonNull(basedOnClarificationIds)) {
			this.basedOnClarifications.add(new ClarificationRef(clarificationId, requirementId));
		}
	}

	public static RequirementRevision proposed(long requirementId, int revisionNo, String text,
			long inputContentVersion, Collection<Long> basedOnClarificationIds) {
		return new RequirementRevision(requirementId, revisionNo, text,
				inputContentVersion, basedOnClarificationIds, RevisionSource.AI);
	}

	public static RequirementRevision proposed(long requirementId, int revisionNo, String text,
			long inputContentVersion, Collection<Long> basedOnClarificationIds, RevisionSource source) {
		return new RequirementRevision(requirementId, revisionNo, text,
				inputContentVersion, basedOnClarificationIds, source);
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

	public RevisionSource getSource() {
		return source;
	}

	public Set<Long> getBasedOnClarificationIds() {
		return basedOnClarifications.stream()
				.map(ClarificationRef::getClarificationId)
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	public Set<ClarificationRef> getBasedOnClarifications() {
		return Set.copyOf(basedOnClarifications);
	}

	@Embeddable
	public static class ClarificationRef {

		@Column(name = "clarification_id", nullable = false)
		private Long clarificationId;

		@Column(name = "requirement_id", nullable = false)
		private Long requirementId;

		protected ClarificationRef() {
		}

		public ClarificationRef(Long clarificationId, Long requirementId) {
			this.clarificationId = Objects.requireNonNull(clarificationId);
			this.requirementId = Objects.requireNonNull(requirementId);
		}

		public Long getClarificationId() {
			return clarificationId;
		}

		public Long getRequirementId() {
			return requirementId;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			ClarificationRef that = (ClarificationRef) o;
			return Objects.equals(clarificationId, that.clarificationId)
					&& Objects.equals(requirementId, that.requirementId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(clarificationId, requirementId);
		}
	}
}
