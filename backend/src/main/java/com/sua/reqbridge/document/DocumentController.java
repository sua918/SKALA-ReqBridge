package com.sua.reqbridge.document;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sua.reqbridge.common.api.ApiResponse;
import com.sua.reqbridge.common.api.ItemList;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Validated
@RestController
public class DocumentController {

	private final DocumentService documentService;

	public DocumentController(DocumentService documentService) {
		this.documentService = documentService;
	}

	@PostMapping("/api/projects/{projectId}/documents")
	public ResponseEntity<ApiResponse<DocumentView>> create(
			@Positive @Max(9_007_199_254_740_991L) @PathVariable long projectId,
			@Valid @RequestBody DocumentCreateRequest request) {
		if (request.sourceType() != DocumentSourceType.TEXT) {
			throw new IllegalArgumentException("Only TEXT documents are supported");
		}
		Document created = documentService.createTextDocument(projectId, request.title(), request.content());
		return ResponseEntity.created(URI.create("/api/documents/" + created.getId()))
				.body(ApiResponse.of(DocumentView.from(created)));
	}

	@GetMapping("/api/projects/{projectId}/documents")
	public ApiResponse<ItemList<DocumentSummaryView>> listByProject(
			@Positive @Max(9_007_199_254_740_991L) @PathVariable long projectId) {
		List<DocumentSummaryView> documents = documentService.listByProject(projectId).stream()
				.map(DocumentSummaryView::from)
				.toList();
		return ApiResponse.of(new ItemList<>(documents));
	}

	@GetMapping("/api/documents/{documentId}")
	public ApiResponse<DocumentView> get(
			@Positive @Max(9_007_199_254_740_991L) @PathVariable long documentId) {
		return ApiResponse.of(DocumentView.from(documentService.get(documentId)));
	}

	public record DocumentCreateRequest(
			@NotBlank(message = "문서 제목을 입력해주세요.") String title,
			@NotNull(message = "문서 입력 방식을 입력해주세요.") DocumentSourceType sourceType,
			@NotBlank(message = "문서 원문을 입력해주세요.") String content) {
	}

	public record DocumentSummaryView(
			long id,
			long projectId,
			String title,
			DocumentSourceType sourceType,
			Instant createdAt) {

		static DocumentSummaryView from(Document document) {
			return new DocumentSummaryView(
					document.getId(),
					document.getProjectId(),
					document.getTitle(),
					document.getSourceType(),
					document.getCreatedAt());
		}
	}

	public record DocumentView(
			long id,
			long projectId,
			String title,
			DocumentSourceType sourceType,
			String content,
			Instant createdAt) {

		static DocumentView from(Document document) {
			return new DocumentView(
					document.getId(),
					document.getProjectId(),
					document.getTitle(),
					document.getSourceType(),
					document.getContent(),
					document.getCreatedAt());
		}
	}
}
