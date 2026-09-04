package com.sua.reqbridge.analysis;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sua.reqbridge.contract.AnalysisAdapterType;
import com.sua.reqbridge.contract.ai.AnswerAssessment;
import com.sua.reqbridge.contract.ai.AnswerAssessmentInput;
import com.sua.reqbridge.contract.ai.DocumentAnalysisInput;
import com.sua.reqbridge.contract.ai.DocumentAnalysisResult;
import com.sua.reqbridge.contract.ai.RevisionGenerationInput;
import com.sua.reqbridge.contract.ai.RevisionProposal;
import com.sua.reqbridge.contract.ai.WorkflowAnalyzer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * OpenAI Chat Completions API(gpt-4o-mini 등)를 활용한 WorkflowAnalyzer 구현체.
 * JSON Mode(response_format: json_object)와 prompt-contract 명세를 준수하여 순수 JSON을 파싱합니다.
 */
public class OpenAiWorkflowAnalyzer implements WorkflowAnalyzer {

	private static final Logger log = LoggerFactory.getLogger(OpenAiWorkflowAnalyzer.class);
	private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
	private static final String SCHEMA_VERSION = "1.0.0";
	private static final Duration TIMEOUT = Duration.ofSeconds(60);

	private final String apiKey;
	private final String model;
	private final ObjectMapper json;
	private final HttpClient httpClient;

	public OpenAiWorkflowAnalyzer(String apiKey, String model, ObjectMapper json) {
		this(apiKey, model, json, HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_2)
				.connectTimeout(Duration.ofSeconds(10))
				.build());
	}

	public OpenAiWorkflowAnalyzer(String apiKey, String model, ObjectMapper json, HttpClient httpClient) {
		this.apiKey = apiKey != null ? apiKey.trim() : "";
		this.model = (model != null && !model.isBlank()) ? model.trim() : "gpt-4o-mini";
		this.json = json;
		this.httpClient = httpClient;
	}

	@Override
	public AnalysisAdapterType adapterType() {
		return AnalysisAdapterType.LLM;
	}

	@Override
	public String schemaVersion() {
		return SCHEMA_VERSION;
	}

	@Override
	public DocumentAnalysisResult analyzeDocument(DocumentAnalysisInput input) {
		validateInput(input, input != null ? input.content() : null);

		String systemPrompt = """
				You are an expert software requirement analyst in ReqBridge.
				Extract requirements and identify ambiguities according to 7 AmbiguityType standards:
				- QUANTITY_MISSING: Missing numerical, volume, count, or size criteria (e.g., 'many users' -> How many?)
				- PERFORMANCE_MISSING: Missing response time, throughput, latency, or deadline (e.g., 'respond quickly' -> What time limit?)
				- CONDITION_MISSING: Missing preconditions or specific environment conditions
				- ACTOR_MISSING: Missing or vague actor/subject
				- SUCCESS_CRITERIA_MISSING: Missing success/completion verification criteria
				- TERM_AMBIGUOUS: Vague, ambiguous, or multi-meaning terms
				- EXCEPTION_MISSING: Missing error, failure, or recovery handling procedures
				
				You MUST respond with a pure JSON object matching this exact schema:
				{
				  "requirements": [
				    {
				      "sequenceNo": 1,
				      "originalText": "string (extracted requirement sentence)",
				      "issues": [
				        {
				          "type": "QUANTITY_MISSING | PERFORMANCE_MISSING | CONDITION_MISSING | ACTOR_MISSING | SUCCESS_CRITERIA_MISSING | TERM_AMBIGUOUS | EXCEPTION_MISSING",
				          "evidence": "string (concise reason in Korean, 1 sentence under 50 chars)",
				          "questionText": "string (polite clarification question in Korean, 1 sentence under 60 chars)"
				        }
				      ]
				    }
				  ]
				}
				
				Rules:
				- sequenceNo starts at 1 and must be strictly sequential (1, 2, 3...).
				- If a requirement has no ambiguities, the issues array must be empty [].
				- Keep evidence and questionText concise, clear, and in natural Korean. Avoid redundant explanation.
				""";

		String userPrompt = "Analyze the following requirements document and output JSON:\n\n" + input.content();

		String rawJson = callOpenAi(systemPrompt, userPrompt, 2500);
		try {
			return json.readValue(rawJson, DocumentAnalysisResult.class);
		}
		catch (Exception e) {
			log.error("Failed to parse DocumentAnalysisResult from OpenAI response: {}", rawJson, e);
			throw new AiOutputInvalidException("OpenAI 문서 분석 응답 파싱 실패: " + e.getMessage(), e);
		}
	}

	@Override
	public AnswerAssessment assessAnswer(AnswerAssessmentInput input) {
		validateInput(input, input != null ? input.answerText() : null);

		String systemPrompt = """
				You are an expert requirement clarification judge in ReqBridge.
				Evaluate whether a customer's answer sufficiently resolves a specific ambiguity issue.
				
				Evaluation criteria:
				- If the customer provided concrete, measurable criteria (e.g., exact numbers, specific thresholds) that resolve the issue, sufficient = true.
				- If the answer is still vague or qualitative, sufficient = false, and generate a polite follow-up question.
				
				You MUST respond with a pure JSON object matching this exact schema:
				{
				  "sufficient": true,
				  "reason": "string (concise explanation in Korean, 1 sentence under 80 chars)",
				  "nextQuestionText": null
				}
				OR
				{
				  "sufficient": false,
				  "reason": "string (concise explanation in Korean, 1 sentence under 80 chars)",
				  "nextQuestionText": "string (polite follow-up question in Korean, 1 sentence under 60 chars)"
				}
				
				CRITICAL:
				- reason MUST be a single concise Korean sentence.
				- When sufficient is true, nextQuestionText MUST be null. When false, nextQuestionText MUST be 1 concise question.
				""";

		StringBuilder userPrompt = new StringBuilder();
		userPrompt.append("Requirement: ").append(input.requirementText()).append("\n");
		userPrompt.append("Ambiguity Type: ").append(input.issueType()).append("\n");
		userPrompt.append("Issue Evidence: ").append(input.evidence()).append("\n");
		userPrompt.append("Current Question (Round ").append(input.roundNo()).append("): ").append(input.questionText()).append("\n");
		userPrompt.append("Customer Answer: ").append(input.answerText()).append("\n");

		if (input.history() != null && !input.history().isEmpty()) {
			userPrompt.append("\nPrevious Q&A History:\n");
			for (AnswerAssessmentInput.ClarificationHistory h : input.history()) {
				userPrompt.append("- Round ").append(h.roundNo()).append(" Q: ").append(h.questionText()).append("\n");
				userPrompt.append("  A: ").append(h.answerText()).append("\n");
			}
		}

		String rawJson = callOpenAi(systemPrompt, userPrompt.toString(), 250);
		try {
			return json.readValue(rawJson, AnswerAssessment.class);
		}
		catch (Exception e) {
			log.error("Failed to parse AnswerAssessment from OpenAI response: {}", rawJson, e);
			throw new AiOutputInvalidException("OpenAI 답변 판정 응답 파싱 실패: " + e.getMessage(), e);
		}
	}

	@Override
	public RevisionProposal generateRevision(RevisionGenerationInput input) {
		validateInput(input, input != null ? input.originalText() : null);

		String systemPrompt = """
				You are an expert requirement specification engineer in ReqBridge.
				Rewrite a software requirement into a clear, measurable, and testable specification by integrating all clarified customer answers.
				
				Guidelines:
				- Combine the original intent with all specific criteria (numbers, latency, conditions, actors) resolved in the Q&A.
				- If a rejection reason is provided, incorporate the feedback directly into the revision.
				- Keep the style formal and declarative in Korean (e.g., '~해야 한다.').
				- Keep proposedText concise and focused without unnecessary preamble or filler.
				
				You MUST respond with a pure JSON object matching this exact schema:
				{
				  "proposedText": "string (the refined, testable requirement specification in Korean)"
				}
				""";

		StringBuilder userPrompt = new StringBuilder();
		userPrompt.append("Original Requirement: ").append(input.originalText()).append("\n");

		if (input.clarifications() != null && !input.clarifications().isEmpty()) {
			userPrompt.append("\nResolved Clarifications:\n");
			for (RevisionGenerationInput.ClarificationContext c : input.clarifications()) {
				userPrompt.append("- Question: ").append(c.questionText()).append("\n");
				userPrompt.append("  Customer Answer: ").append(c.answerText()).append("\n");
			}
		}

		if (input.rejectionReason() != null && !input.rejectionReason().isBlank()) {
			userPrompt.append("\nPrevious Revision Rejection Reason by PM:\n");
			userPrompt.append(input.rejectionReason()).append("\n");
		}

		String rawJson = callOpenAi(systemPrompt, userPrompt.toString(), 400);
		try {
			return json.readValue(rawJson, RevisionProposal.class);
		}
		catch (Exception e) {
			log.error("Failed to parse RevisionProposal from OpenAI response: {}", rawJson, e);
			throw new AiOutputInvalidException("OpenAI 수정안 생성 응답 파싱 실패: " + e.getMessage(), e);
		}
	}

	private void validateInput(Object input, String mainText) {
		if (input == null || mainText == null || mainText.isBlank()) {
			throw new AiOutputInvalidException("분석 입력 데이터가 유효하지 않습니다.");
		}
		if (this.apiKey.isBlank()) {
			throw new AiOutputInvalidException("OpenAI API 키가 설정되지 않았습니다. .env 파일의 OPENAI_API_KEY를 설정하세요.");
		}
	}

	private String callOpenAi(String systemPrompt, String userPrompt, int maxCompletionTokens) {
		Map<String, Object> requestBody = new HashMap<>();
		requestBody.put("model", this.model);
		requestBody.put("temperature", 0.1);
		requestBody.put("max_completion_tokens", maxCompletionTokens);
		requestBody.put("response_format", Map.of("type", "json_object"));

		List<Map<String, String>> messages = new ArrayList<>();
		messages.add(Map.of("role", "system", "content", systemPrompt));
		messages.add(Map.of("role", "user", "content", userPrompt));
		requestBody.put("messages", messages);

		String requestJson;
		try {
			requestJson = json.writeValueAsString(requestBody);
		}
		catch (Exception e) {
			throw new AiOutputInvalidException("OpenAI 요청 JSON 직렬화 실패: " + e.getMessage(), e);
		}

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(OPENAI_API_URL))
				.header("Authorization", "Bearer " + this.apiKey)
				.header("Content-Type", "application/json")
				.header("Accept", "application/json")
				.timeout(TIMEOUT)
				.POST(HttpRequest.BodyPublishers.ofString(requestJson))
				.build();

		HttpResponse<String> response;
		try {
			response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		}
		catch (IOException | InterruptedException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			log.error("OpenAI API communication error", e);
			throw new AiOutputInvalidException("OpenAI API 통신 오류: " + e.getMessage(), e);
		}

		if (response.statusCode() != 200) {
			log.error("OpenAI API error response: HTTP {} - {}", response.statusCode(), response.body());
			throw new AiOutputInvalidException(
					String.format("OpenAI API 호출 실패 (HTTP %d): %s", response.statusCode(), response.body()));
		}

		try {
			JsonNode root = json.readTree(response.body());
			JsonNode choices = root.path("choices");
			if (choices.isEmpty()) {
				throw new AiOutputInvalidException("OpenAI 응답에 choices가 없습니다.");
			}
			JsonNode message = choices.get(0).path("message");
			String content = message.path("content").asText();
			if (content == null || content.isBlank()) {
				throw new AiOutputInvalidException("OpenAI 응답 내용(content)이 비어 있습니다.");
			}
			return content;
		}
		catch (Exception e) {
			log.error("Failed to extract content from OpenAI response: {}", response.body(), e);
			throw new AiOutputInvalidException("OpenAI 응답 추출 실패: " + e.getMessage(), e);
		}
	}
}
