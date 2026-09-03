package com.sua.reqbridge.analysis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import com.sua.reqbridge.ambiguity.AmbiguityIssueRepository;
import com.sua.reqbridge.clarification.ClarificationRepository;
import com.sua.reqbridge.clarification.AnswerAnalysisWorker;
import com.sua.reqbridge.clarification.AnswerWorkflowService;
import com.sua.reqbridge.contract.CoreRequirementPort;
import com.sua.reqbridge.revision.RequirementRevisionRepository;

import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AnalysisConfiguration {

	@Bean
	@ConditionalOnBean(CoreRequirementPort.class)
	MockWorkflowAnalyzer mockWorkflowAnalyzer() {
		return new MockWorkflowAnalyzer();
	}

	@Bean
	@ConditionalOnBean(CoreRequirementPort.class)
	DocumentAnalysisService documentAnalysisService(AnalysisRepository analyses,
			AmbiguityIssueRepository issues,
			ClarificationRepository clarifications,
			CoreRequirementPort core,
			ApplicationEventPublisher events,
			MockWorkflowAnalyzer analyzer,
			ObjectMapper json) {
		return new DocumentAnalysisService(
				analyses, issues, clarifications, core, events, analyzer, json);
	}

	@Bean
	@ConditionalOnBean(DocumentAnalysisService.class)
	DocumentAnalysisWorker documentAnalysisWorker(DocumentAnalysisService service) {
		return new DocumentAnalysisWorker(service);
	}

	@Bean
	@ConditionalOnBean(CoreRequirementPort.class)
	AnswerWorkflowService answerWorkflowService(AnalysisRepository analyses,
			AmbiguityIssueRepository issues, ClarificationRepository clarifications,
			RequirementRevisionRepository revisions, CoreRequirementPort core,
			ApplicationEventPublisher events, MockWorkflowAnalyzer analyzer, ObjectMapper json) {
		return new AnswerWorkflowService(
				analyses, issues, clarifications, revisions, core, events, analyzer, json);
	}

	@Bean
	@ConditionalOnBean(AnswerWorkflowService.class)
	AnswerAnalysisWorker answerAnalysisWorker(
			AnswerWorkflowService service, DocumentAnalysisService failures) {
		return new AnswerAnalysisWorker(service, failures);
	}

	@Bean
	@ConditionalOnBean(CoreRequirementPort.class)
	com.sua.reqbridge.revision.RevisionWorkflowService revisionWorkflowService(
			AnalysisRepository analyses,
			AmbiguityIssueRepository issues,
			ClarificationRepository clarifications,
			RequirementRevisionRepository revisions,
			CoreRequirementPort core,
			ApplicationEventPublisher events,
			ObjectMapper json) {
		return new com.sua.reqbridge.revision.RevisionWorkflowService(
				analyses, issues, clarifications, revisions, core, events, json);
	}

	@Bean
	@ConditionalOnBean(com.sua.reqbridge.revision.RevisionWorkflowService.class)
	com.sua.reqbridge.revision.RevisionAnalysisWorker revisionAnalysisWorker(
			com.sua.reqbridge.revision.RevisionWorkflowService service,
			DocumentAnalysisService failures) {
		return new com.sua.reqbridge.revision.RevisionAnalysisWorker(service, failures);
	}

	@Bean
	@ConditionalOnBean(CoreRequirementPort.class)
	com.sua.reqbridge.contract.WorkflowPreviewPort workflowPreviewPort(
			CoreRequirementPort core,
			AmbiguityIssueRepository issues,
			ClarificationRepository clarifications,
			RequirementRevisionRepository revisions) {
		return new com.sua.reqbridge.clarification.WorkflowPreviewAdapter(
				core, issues, clarifications, revisions);
	}
}
