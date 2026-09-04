package com.sua.reqbridge.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sua.reqbridge.contract.AmbiguityType;
import com.sua.reqbridge.contract.AnalysisAdapterType;
import com.sua.reqbridge.contract.ai.AnswerAssessmentInput;
import com.sua.reqbridge.contract.ai.DocumentAnalysisInput;
import com.sua.reqbridge.contract.ai.RevisionGenerationInput;

import tools.jackson.databind.ObjectMapper;

class OpenAiWorkflowAnalyzerTests {

	private ObjectMapper objectMapper;
	private HttpClient mockHttpClient;
	private HttpResponse<String> mockHttpResponse;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		objectMapper = new ObjectMapper();
		mockHttpClient = mock(HttpClient.class);
		mockHttpResponse = mock(HttpResponse.class);
	}

	@Test
	@DisplayName("기본 어댑터 타입과 스키마 버전을 확인한다")
	void checksAdapterTypeAndSchemaVersion() {
		OpenAiWorkflowAnalyzer analyzer = new OpenAiWorkflowAnalyzer("test-api-key", "gpt-4o-mini", objectMapper, mockHttpClient);

		assertThat(analyzer.adapterType()).isEqualTo(AnalysisAdapterType.LLM);
		assertThat(analyzer.schemaVersion()).isEqualTo("1.0.0");
	}

	@Test
	@DisplayName("API 키가 누락되었을 때 예외를 발생시킨다")
	void throwsExceptionWhenApiKeyIsMissing() {
		OpenAiWorkflowAnalyzer analyzer = new OpenAiWorkflowAnalyzer("", "gpt-4o-mini", objectMapper, mockHttpClient);

		assertThatThrownBy(() -> analyzer.analyzeDocument(new DocumentAnalysisInput(1L, "테스트 요구사항")))
				.isInstanceOf(AiOutputInvalidException.class)
				.hasMessageContaining("OpenAI API 키가 설정되지 않았습니다");
	}

	@Test
	@DisplayName("문서 분석 응답 JSON을 정상 파싱하고 Validator를 통과한다")
	@SuppressWarnings("unchecked")
	void parsesDocumentAnalysisResultSuccessfully() throws IOException, InterruptedException {
		String openAiResponseBody = """
				{
				  "choices": [
				    {
				      "message": {
				        "content": "{\\"requirements\\":[{\\"sequenceNo\\":1,\\"originalText\\":\\"시스템은 많은 사용자의 요청에 빠르게 응답해야 한다.\\",\\"issues\\":[{\\"type\\":\\"QUANTITY_MISSING\\",\\"evidence\\":\\"많은 사용자의 수량 누락\\",\\"questionText\\":\\"최대 동시 사용자는 몇 명인가요?\\"}]}]}"
				      }
				    }
				  ]
				}
				""";

		when(mockHttpResponse.statusCode()).thenReturn(200);
		when(mockHttpResponse.body()).thenReturn(openAiResponseBody);
		when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
				.thenReturn(mockHttpResponse);

		OpenAiWorkflowAnalyzer analyzer = new OpenAiWorkflowAnalyzer("sk-test", "gpt-4o-mini", objectMapper, mockHttpClient);
		var result = analyzer.analyzeDocument(new DocumentAnalysisInput(1L, "시스템은 많은 사용자의 요청에 빠르게 응답해야 한다."));

		assertThat(result.requirements()).hasSize(1);
		assertThat(result.requirements().getFirst().sequenceNo()).isEqualTo(1);
		assertThat(result.requirements().getFirst().issues()).hasSize(1);
		assertThat(result.requirements().getFirst().issues().getFirst().type()).isEqualTo(AmbiguityType.QUANTITY_MISSING);

		// 도메인 검증 계층 통과 여부
		AnalyzerOutputValidator.validateDocumentResult(result);
	}

	@Test
	@DisplayName("답변 판정 응답 JSON을 정상 파싱하고 Validator를 통과한다")
	@SuppressWarnings("unchecked")
	void parsesAnswerAssessmentSuccessfully() throws IOException, InterruptedException {
		String openAiResponseBody = """
				{
				  "choices": [
				    {
				      "message": {
				        "content": "{\\"sufficient\\":true,\\"reason\\":\\"3000명이라는 구체적인 정량 수치가 제시되었습니다.\\",\\"nextQuestionText\\":null}"
				      }
				    }
				  ]
				}
				""";

		when(mockHttpResponse.statusCode()).thenReturn(200);
		when(mockHttpResponse.body()).thenReturn(openAiResponseBody);
		when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
				.thenReturn(mockHttpResponse);

		OpenAiWorkflowAnalyzer analyzer = new OpenAiWorkflowAnalyzer("sk-test", "gpt-4o-mini", objectMapper, mockHttpClient);
		var assessment = analyzer.assessAnswer(new AnswerAssessmentInput(
				1L, 1L, "원문", AmbiguityType.QUANTITY_MISSING, "근거", 1L, 1L, 1, "질문", "3000명입니다", List.of()));

		assertThat(assessment.sufficient()).isTrue();
		assertThat(assessment.nextQuestionText()).isNull();

		// 도메인 검증 계층 통과 여부
		AnalyzerOutputValidator.validateAnswerAssessment(assessment);
	}

	@Test
	@DisplayName("수정안 생성 응답 JSON을 정상 파싱하고 Validator를 통과한다")
	@SuppressWarnings("unchecked")
	void parsesRevisionProposalSuccessfully() throws IOException, InterruptedException {
		String openAiResponseBody = """
				{
				  "choices": [
				    {
				      "message": {
				        "content": "{\\"proposedText\\":\\"시스템은 최대 동시 사용자 3,000명의 요청에 대해 p95 응답 시간 2초 이하로 처리해야 한다.\\"}"
				      }
				    }
				  ]
				}
				""";

		when(mockHttpResponse.statusCode()).thenReturn(200);
		when(mockHttpResponse.body()).thenReturn(openAiResponseBody);
		when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
				.thenReturn(mockHttpResponse);

		OpenAiWorkflowAnalyzer analyzer = new OpenAiWorkflowAnalyzer("sk-test", "gpt-4o-mini", objectMapper, mockHttpClient);
		var proposal = analyzer.generateRevision(new RevisionGenerationInput(1L, "원문", List.of(), null));

		assertThat(proposal.proposedText()).contains("3,000명");

		// 도메인 검증 계층 통과 여부
		AnalyzerOutputValidator.validateRevisionProposal(proposal);
	}

	@Test
	@DisplayName("OpenAI API가 401 Unauthorized를 반환할 때 적절한 예외를 발생시킨다")
	@SuppressWarnings("unchecked")
	void throwsExceptionOnHttpError() throws IOException, InterruptedException {
		when(mockHttpResponse.statusCode()).thenReturn(401);
		when(mockHttpResponse.body()).thenReturn("{\"error\": {\"message\": \"Incorrect API key provided\"}}");
		when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
				.thenReturn(mockHttpResponse);

		OpenAiWorkflowAnalyzer analyzer = new OpenAiWorkflowAnalyzer("sk-invalid", "gpt-4o-mini", objectMapper, mockHttpClient);

		assertThatThrownBy(() -> analyzer.analyzeDocument(new DocumentAnalysisInput(1L, "원문")))
				.isInstanceOf(AiOutputInvalidException.class)
				.hasMessageContaining("HTTP 401");
	}

	@Test
	@DisplayName("요청 헤더 및 HTTP POST 메서드가 올바르게 전송된다")
	@SuppressWarnings("unchecked")
	void verifiesHttpRequestConfiguration() throws IOException, InterruptedException {
		String openAiResponseBody = """
				{
				  "choices": [
				    {
				      "message": {
				        "content": "{\\"proposedText\\":\\"수정안\\"}"
				      }
				    }
				  ]
				}
				""";

		org.mockito.ArgumentCaptor<HttpRequest> requestCaptor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
		when(mockHttpResponse.statusCode()).thenReturn(200);
		when(mockHttpResponse.body()).thenReturn(openAiResponseBody);
		when(mockHttpClient.send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class)))
				.thenReturn(mockHttpResponse);

		OpenAiWorkflowAnalyzer analyzer = new OpenAiWorkflowAnalyzer("sk-test", "gpt-4o-mini", objectMapper, mockHttpClient);
		analyzer.generateRevision(new RevisionGenerationInput(1L, "원문", List.of(), null));

		HttpRequest capturedRequest = requestCaptor.getValue();
		assertThat(capturedRequest.headers().firstValue("Authorization")).contains("Bearer sk-test");
		assertThat(capturedRequest.method()).isEqualTo("POST");
		assertThat(capturedRequest.headers().firstValue("Content-Type")).contains("application/json");
	}
}

