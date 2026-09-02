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

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Document() {
	}

	public Document(long projectId, String title, String content, DocumentSourceType sourceType) {
		this.projectId = projectId;
		this.title = title;
		this.content = content;
		this.sourceType = sourceType;
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
