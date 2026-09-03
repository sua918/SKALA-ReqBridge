package com.sua.reqbridge.report;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sua.reqbridge.contract.WorkflowPreviewPort;

@RestController
@RequestMapping("/api")
@ConditionalOnBean(WorkflowPreviewPort.class)
public class ReportController {

	private final ReportService service;

	public ReportController(ReportService service) {
		this.service = service;
	}

	@GetMapping("/documents/{documentId}/previews/customer")
	public ResponseEntity<Data<ReportService.CustomerPreview>> getCustomerPreview(@PathVariable long documentId) {
		return ResponseEntity.ok(new Data<>(service.getCustomerPreview(documentId)));
	}

	@GetMapping("/documents/{documentId}/previews/developer")
	public ResponseEntity<Data<ReportService.DeveloperPreview>> getDeveloperPreview(@PathVariable long documentId) {
		return ResponseEntity.ok(new Data<>(service.getDeveloperPreview(documentId)));
	}

	record Data<T>(T data) {
	}
}
