package com.sua.reqbridge.analysis;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sua.reqbridge.contract.AnalysisKind;
import com.sua.reqbridge.contract.AnalysisStatus;

import tools.jackson.databind.ObjectMapper;

@RequestMapping("/api")
@RestController
@ConditionalOnBean(DocumentAnalysisService.class)
public class AnalysisController {

	private final DocumentAnalysisService service;
	private final ObjectMapper json;

	public AnalysisController(DocumentAnalysisService service, ObjectMapper json) {
		this.service = service;
		this.json = json;
	}

	@PostMapping("/documents/{documentId}/analyses")
	public ResponseEntity<Data<AnalysisView>> submit(@PathVariable long documentId) {
		Analysis analysis = service.submit(documentId);
		return ResponseEntity.accepted()
				.location(URI.create("/api/analyses/" + analysis.getId()))
				.body(new Data<>(view(analysis)));
	}

	@GetMapping("/documents/{documentId}/analyses")
	public Data<Items<AnalysisView>> list(@PathVariable long documentId,
			@RequestParam(required = false) AnalysisKind kind) {
		return new Data<>(new Items<>(service.list(documentId, kind).stream().map(this::view).toList()));
	}

	@GetMapping("/analyses/{analysisId}")
	public Data<AnalysisView> get(@PathVariable long analysisId) {
		return new Data<>(view(service.get(analysisId)));
	}

	@PostMapping("/analyses/{analysisId}/retries")
	public ResponseEntity<Data<AnalysisView>> retry(@PathVariable long analysisId) {
		Analysis analysis = service.retry(analysisId);
		Data<AnalysisView> body = new Data<>(view(analysis));
		if (analysis.getStatus() == AnalysisStatus.PENDING
				|| analysis.getStatus() == AnalysisStatus.PROCESSING) {
			return ResponseEntity.accepted()
					.location(URI.create("/api/analyses/" + analysis.getId()))
					.body(body);
		}
		return ResponseEntity.ok(body);
	}

	private AnalysisView view(Analysis analysis) {
		Object result = analysis.getResult() == null ? null : json.readTree(analysis.getResult());
		Failure error = analysis.getErrorCode() == null
				? null : new Failure(analysis.getErrorCode(), analysis.getErrorMessage());
		return new AnalysisView(analysis.getId(), analysis.getKind(), analysis.getStatus(),
				analysis.getDocumentId(), analysis.getRequirementId(), analysis.getClarificationId(),
				analysis.getInputContentVersion(), analysis.getRetryOfAnalysisId(), analysis.getCreatedAt(),
				analysis.getStartedAt(), analysis.getCompletedAt(), result, error);
	}

	record Data<T>(T data) {
	}

	record Items<T>(List<T> items) {
	}

	record Failure(String code, String message) {
	}

	record AnalysisView(long id, AnalysisKind kind, AnalysisStatus status, long documentId,
			Long requirementId, Long clarificationId, Long inputContentVersion, Long retryOfAnalysisId,
			Instant createdAt, Instant startedAt, Instant completedAt, Object result, Failure error) {
	}
}
