package com.sua.reqbridge.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.sua.reqbridge.ambiguity.AmbiguityIssueRepository;
import com.sua.reqbridge.clarification.AnswerWorkflowService;
import com.sua.reqbridge.clarification.ClarificationRepository;
import com.sua.reqbridge.contract.CoreRequirementPort;
import com.sua.reqbridge.contract.ai.WorkflowAnalyzer;
import com.sua.reqbridge.revision.RequirementRevisionRepository;
import com.sua.reqbridge.revision.RevisionWorkflowService;

import tools.jackson.databind.ObjectMapper;

class AnalysisConfigurationTests {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(AnalysisConfiguration.class)
			.withBean(CoreRequirementPort.class, () -> mock(CoreRequirementPort.class))
			.withBean(AnalysisRepository.class, () -> mock(AnalysisRepository.class))
			.withBean(AmbiguityIssueRepository.class, () -> mock(AmbiguityIssueRepository.class))
			.withBean(ClarificationRepository.class, () -> mock(ClarificationRepository.class))
			.withBean(RequirementRevisionRepository.class, () -> mock(RequirementRevisionRepository.class))
			.withBean(ObjectMapper.class, ObjectMapper::new);

	@Test
	void defaultsToMockForAllThreeServices() {
		runner.run(context -> {
			assertThat(context).hasNotFailed().hasSingleBean(WorkflowAnalyzer.class);
			WorkflowAnalyzer analyzer = context.getBean(WorkflowAnalyzer.class);
			assertThat(analyzer).isInstanceOf(MockWorkflowAnalyzer.class);
			assertThat(ReflectionTestUtils.getField(context.getBean(DocumentAnalysisService.class), "analyzer"))
					.isSameAs(analyzer);
			assertThat(ReflectionTestUtils.getField(context.getBean(AnswerWorkflowService.class), "analyzer"))
					.isSameAs(analyzer);
			assertThat(ReflectionTestUtils.getField(context.getBean(RevisionWorkflowService.class), "analyzer"))
					.isSameAs(analyzer);
		});
	}

	@Test
	void acceptsReplacementBeanWithoutChangingWorkflowServices() {
		WorkflowAnalyzer replacement = mock(WorkflowAnalyzer.class);
		runner.withBean(WorkflowAnalyzer.class, () -> replacement).run(context -> {
			assertThat(context).hasNotFailed().hasSingleBean(WorkflowAnalyzer.class);
			assertThat(context).doesNotHaveBean("mockWorkflowAnalyzer");
			assertThat(ReflectionTestUtils.getField(context.getBean(DocumentAnalysisService.class), "analyzer"))
					.isSameAs(replacement);
			assertThat(ReflectionTestUtils.getField(context.getBean(AnswerWorkflowService.class), "analyzer"))
					.isSameAs(replacement);
			assertThat(ReflectionTestUtils.getField(context.getBean(RevisionWorkflowService.class), "analyzer"))
					.isSameAs(replacement);
		});
	}
}
