package com.sua.reqbridge.analysis;

import java.time.Instant;
import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.sua.reqbridge.contract.AnalysisAdapterType;
import com.sua.reqbridge.contract.AnalysisKind;
import com.sua.reqbridge.contract.AnalysisStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "analysis", schema = "app")
public class Analysis {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(nullable = false, columnDefinition = "analysis_kind")
	private AnalysisKind kind;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(nullable = false, columnDefinition = "analysis_status")
	private AnalysisStatus status;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "adapter_type", nullable = false, columnDefinition = "analysis_adapter_type")
	private AnalysisAdapterType adapterType;

	@Column(name = "schema_version", nullable = false, length = 50)
	private String schemaVersion;

	@Column(name = "document_id", nullable = false)
	private Long documentId;

	@Column(name = "requirement_id")
	private Long requirementId;

	@Column(name = "clarification_id")
	private Long clarificationId;

	@Column(name = "input_content_version")
	private Long inputContentVersion;

	@Column(name = "retry_of_analysis_id")
	private Long retryOfAnalysisId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "input_snapshot", nullable = false, columnDefinition = "jsonb")
	private String inputSnapshot;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private String result;

	@Column(name = "error_code")
	private String errorCode;

	@Column(name = "error_message")
	private String errorMessage;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	protected Analysis() {
	}

	private Analysis(AnalysisKind kind, long documentId, Long requirementId,
			Long clarificationId, Long inputContentVersion, String inputSnapshot) {
		this(kind, documentId, requirementId, clarificationId, inputContentVersion,
				inputSnapshot, AnalysisAdapterType.MOCK, "1.0.0");
	}

	private Analysis(AnalysisKind kind, long documentId, Long requirementId,
			Long clarificationId, Long inputContentVersion, String inputSnapshot,
			AnalysisAdapterType adapterType, String schemaVersion) {
		this.kind = kind;
		this.status = AnalysisStatus.PENDING;
		this.documentId = documentId;
		this.requirementId = requirementId;
		this.clarificationId = clarificationId;
		this.inputContentVersion = inputContentVersion;
		this.inputSnapshot = Objects.requireNonNull(inputSnapshot);
		this.adapterType = Objects.requireNonNull(adapterType);
		this.schemaVersion = Objects.requireNonNull(schemaVersion);
		this.createdAt = Instant.now();
	}

	public static Analysis pendingDocument(long documentId, String inputSnapshot) {
		return pendingDocument(documentId, inputSnapshot, AnalysisAdapterType.MOCK, "1.0.0");
	}

	public static Analysis pendingDocument(long documentId, String inputSnapshot,
			AnalysisAdapterType adapterType, String schemaVersion) {
		return new Analysis(AnalysisKind.DOCUMENT, documentId, null, null, null,
				inputSnapshot, adapterType, schemaVersion);
	}

	public static Analysis pendingAnswer(long documentId, long requirementId,
			long clarificationId, long inputContentVersion, String inputSnapshot) {
		return pendingAnswer(documentId, requirementId, clarificationId, inputContentVersion,
				inputSnapshot, AnalysisAdapterType.MOCK, "1.0.0");
	}

	public static Analysis pendingAnswer(long documentId, long requirementId,
			long clarificationId, long inputContentVersion, String inputSnapshot,
			AnalysisAdapterType adapterType, String schemaVersion) {
		return new Analysis(AnalysisKind.ANSWER, documentId, requirementId,
				clarificationId, inputContentVersion, inputSnapshot, adapterType, schemaVersion);
	}

	public static Analysis pendingRevision(long documentId, long requirementId,
			long inputContentVersion, String inputSnapshot) {
		return pendingRevision(documentId, requirementId, inputContentVersion,
				inputSnapshot, AnalysisAdapterType.MOCK, "1.0.0");
	}

	public static Analysis pendingRevision(long documentId, long requirementId,
			long inputContentVersion, String inputSnapshot,
			AnalysisAdapterType adapterType, String schemaVersion) {
		return new Analysis(AnalysisKind.REVISION, documentId, requirementId,
				null, inputContentVersion, inputSnapshot, adapterType, schemaVersion);
	}

	public static Analysis retry(Analysis failedAnalysis) {
		if (failedAnalysis == null) {
			throw new IllegalArgumentException("재시도할 대상 분석 작업이 필요합니다.");
		}
		if (failedAnalysis.getStatus() != AnalysisStatus.FAILED) {
			throw new IllegalStateException("FAILED 작업만 재시도할 수 있습니다.");
		}
		Analysis retried = new Analysis(failedAnalysis.getKind(), failedAnalysis.getDocumentId(),
				failedAnalysis.getRequirementId(), failedAnalysis.getClarificationId(),
				failedAnalysis.getInputContentVersion(), failedAnalysis.getInputSnapshot(),
				failedAnalysis.getAdapterType(), failedAnalysis.getSchemaVersion());
		retried.retryOfAnalysisId = failedAnalysis.getId();
		return retried;
	}

	public void start(Instant startedAt) {
		if (status != AnalysisStatus.PENDING) {
			throw new IllegalStateException("PENDING 작업만 시작할 수 있습니다.");
		}
		this.status = AnalysisStatus.PROCESSING;
		this.startedAt = Objects.requireNonNull(startedAt);
	}

	public void complete(String result, Instant completedAt) {
		if (status != AnalysisStatus.PROCESSING) {
			throw new IllegalStateException("PROCESSING 작업만 완료할 수 있습니다.");
		}
		this.status = AnalysisStatus.COMPLETED;
		this.result = Objects.requireNonNull(result);
		this.completedAt = Objects.requireNonNull(completedAt);
	}

	public void fail(String errorCode, String errorMessage, Instant completedAt) {
		if (status != AnalysisStatus.PENDING && status != AnalysisStatus.PROCESSING) {
			throw new IllegalStateException("진행 전 또는 진행 중 작업만 실패 처리할 수 있습니다.");
		}
		this.status = AnalysisStatus.FAILED;
		this.errorCode = Objects.requireNonNull(errorCode);
		this.errorMessage = Objects.requireNonNull(errorMessage);
		this.completedAt = Objects.requireNonNull(completedAt);
	}

	public Long getId() {
		return id;
	}

	public AnalysisKind getKind() {
		return kind;
	}

	public AnalysisStatus getStatus() {
		return status;
	}

	public AnalysisAdapterType getAdapterType() {
		return adapterType;
	}

	public String getSchemaVersion() {
		return schemaVersion;
	}

	public Long getDocumentId() {
		return documentId;
	}

	public Long getRequirementId() {
		return requirementId;
	}

	public Long getClarificationId() {
		return clarificationId;
	}

	public Long getInputContentVersion() {
		return inputContentVersion;
	}

	public Long getRetryOfAnalysisId() {
		return retryOfAnalysisId;
	}

	public String getInputSnapshot() {
		return inputSnapshot;
	}

	public String getResult() {
		return result;
	}

	public String getErrorCode() {
		return errorCode;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}
}
