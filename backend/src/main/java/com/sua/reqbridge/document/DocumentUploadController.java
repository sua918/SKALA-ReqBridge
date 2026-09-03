package com.sua.reqbridge.document;

import java.net.URI;
import java.util.Set;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import com.sua.reqbridge.common.api.ApiResponse;
import com.sua.reqbridge.document.DocumentController.DocumentView;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;

@Validated
@RestController
public class DocumentUploadController {
	private final DocumentUploadService service;

	public DocumentUploadController(DocumentUploadService service) {
		this.service = service;
	}

	@PostMapping(value = "/api/projects/{projectId}/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<ApiResponse<DocumentView>> upload(
			@Positive @Max(9_007_199_254_740_991L) @PathVariable long projectId,
			MultipartHttpServletRequest request) {
		if (!request.getParameterMap().keySet().equals(Set.of("title"))
				|| request.getParameterValues("title").length != 1
				|| !request.getMultiFileMap().keySet().equals(Set.of("file"))
				|| request.getFiles("file").size() != 1) {
			throw new IllegalArgumentException("title과 file을 각각 한 개만 보내주세요. 다른 필드는 허용하지 않습니다.");
		}
		Document created = service.upload(projectId, request.getParameter("title"), request.getFile("file"));
		return ResponseEntity.created(URI.create("/api/documents/" + created.getId()))
				.body(ApiResponse.of(DocumentView.from(created)));
	}
}
