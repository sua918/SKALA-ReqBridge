package com.sua.reqbridge.document;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "document", schema = "app")
public class Document {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "project_id", nullable = false)
	private long projectId;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(nullable = false, columnDefinition = "text")
	private String content;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "source_type", nullable = false, columnDefinition = "document_source_type")
	private DocumentSourceType sourceType;

	@Column(name = "storage_path", columnDefinition = "text")
	private String storagePath;

	@Column(name = "original_filename", columnDefinition = "text")
	private String originalFilename;

	@Column(name = "mime_type", length = 100)
	private String mimeType;

	@Column(name = "file_size_bytes")
	private Long fileSizeBytes;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Document() {
	}

	public Document(long projectId, String title, String content, DocumentSourceType sourceType) {
		if (sourceType != DocumentSourceType.TEXT) {
			throw new IllegalArgumentException("FILE documents require file metadata");
		}
		this.projectId = projectId;
		this.title = title;
		this.content = content;
		this.sourceType = sourceType;
	}

	public static Document fromPdf(long projectId, String title, String content,
			String storagePath, String originalFilename, long fileSizeBytes) {
		if (storagePath == null || storagePath.isBlank() || originalFilename == null
				|| originalFilename.isBlank() || fileSizeBytes <= 0
				|| fileSizeBytes > PdfTextExtractor.MAX_FILE_BYTES) {
			throw new IllegalArgumentException("Invalid PDF metadata");
		}
		Document document = new Document(projectId, title, content, DocumentSourceType.TEXT);
		document.sourceType = DocumentSourceType.FILE;
		document.storagePath = storagePath;
		document.originalFilename = originalFilename;
		document.mimeType = "application/pdf";
		document.fileSizeBytes = fileSizeBytes;
		return document;
	}

	public String getStoragePath() {
		return storagePath;
	}

	public String getOriginalFilename() {
		return originalFilename;
	}

	public String getMimeType() {
		return mimeType;
	}

	public Long getFileSizeBytes() {
		return fileSizeBytes;
	}

	public Long getId() {
		return id;
	}

	public long getProjectId() {
		return projectId;
	}

	public String getTitle() {
		return title;
	}

	public String getContent() {
		return content;
	}

	public DocumentSourceType getSourceType() {
		return sourceType;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
