package com.sua.reqbridge.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.sua.reqbridge.contract.ai.WorkflowAnalyzer;
import com.sua.reqbridge.contract.ai.AnalyzerTypes.*;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Real HTTP/async/JPA rollback checks. Use a disposable local PostgreSQL DB only. */
@EnabledIfEnvironmentVariable(named = "REQBRIDGE_TEST_POSTGRES_URL", matches = ".+")
@ActiveProfiles("default")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"spring.datasource.url=${REQBRIDGE_TEST_POSTGRES_URL}",
		"spring.datasource.username=${REQBRIDGE_TEST_POSTGRES_USER}",
		"spring.datasource.password=${REQBRIDGE_TEST_POSTGRES_PASSWORD:}",
		"spring.flyway.enabled=true",
		"reqbridge.storage.url=", "reqbridge.storage.bucket=", "reqbridge.storage.service-role-key="})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AnalyzerPostgresTests {
	private static final String ORIGINAL = "시스템은 많은 사용자의 동시 상품 조회 요청에 빠르게 응답해야 한다. "
			+ "부하 시험은 10분 동안 수행하며 성공 응답 비율은 99.9% 이상이어야 한다.";
	@LocalServerPort int port;
	@Autowired JdbcTemplate jdbc;
	@Autowired ObjectMapper json;
	@MockitoBean WorkflowAnalyzer analyzer;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	@BeforeEach
	void useDeterministicAdapter() {
		MockWorkflowAnalyzer mock = new MockWorkflowAnalyzer();
		when(analyzer.adapterType()).thenReturn(mock.adapterType());
		when(analyzer.schemaVersion()).thenReturn(mock.schemaVersion());
		doAnswer(call -> mock.analyze(call.getArgument(0))).when(analyzer).analyze(any());
		doAnswer(call -> mock.assess(call.getArgument(0))).when(analyzer).assess(any());
		doAnswer(call -> mock.generateRevision(call.getArgument(0))).when(analyzer).generateRevision(any());
	}

	@Test
	void invalidLaterDocumentCandidateLeavesNoPartialRequirements() throws Exception {
		long documentId = document();
		doReturn(new DocumentResult(List.of(
				new RequirementCandidate(1, "정상 후보", List.of()),
				new RequirementCandidate(2, "두 번째 후보", List.of(new IssueCandidate(null, "근거", "질문"))))))
				.when(analyzer).analyze(any());
		JsonNode failed = await(post("/api/documents/" + documentId + "/analyses", null, 202).get("data"), "FAILED");
		assertThat(failed.at("/error/code").asString()).isEqualTo("AI_OUTPUT_INVALID");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM app.requirement WHERE document_id=?",
				Long.class, documentId)).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM app.document WHERE id=?",
				Long.class, documentId)).isEqualTo(1L);
	}

	@Test
	void invalidGeneratedRevisionRollsBackIssueResolutionThenRetrySucceeds() throws Exception {
		long documentId = document();
		JsonNode analyzed = await(post("/api/documents/" + documentId + "/analyses", null, 202).get("data"), "COMPLETED");
		long requirementId = analyzed.at("/result/requirementIds/0").asLong();
		JsonNode workflow = get("/api/requirements/" + requirementId + "/workflow").get("data");
		long quantityQuestion = workflow.at("/clarifications/0/id").asLong();
		long performanceQuestion = workflow.at("/clarifications/1/id").asLong();
		long performanceIssue = workflow.at("/clarifications/1/issueId").asLong();
		await(answer(quantityQuestion, "최대 동시 사용자 3,000명입니다.", 1), "COMPLETED");
		doReturn(new RevisionProposal("\uFEFF ")).when(analyzer).generateRevision(any());
		JsonNode failed = await(answer(performanceQuestion, "p95 응답 시간 2초 이하입니다.", 2), "FAILED");
		assertThat(failed.at("/error/code").asString()).isEqualTo("AI_OUTPUT_INVALID");
		assertThat(jdbc.queryForObject("SELECT status::text FROM app.ambiguity_issue WHERE id=?",
				String.class, performanceIssue)).isEqualTo("OPEN");
		assertThat(jdbc.queryForObject("SELECT status::text FROM app.clarification WHERE id=?",
				String.class, performanceQuestion)).isEqualTo("ANSWERED");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM app.requirement_revision WHERE requirement_id=?",
				Long.class, requirementId)).isZero();
		assertThat(jdbc.queryForObject("SELECT status::text FROM app.requirement WHERE id=?",
				String.class, requirementId)).isEqualTo("CLARIFYING");
		assertThat(jdbc.queryForObject("SELECT content_version FROM app.requirement WHERE id=?",
				Long.class, requirementId)).isEqualTo(3);
		String originalInput = snapshot(failed.get("id").asLong());

		doReturn(new RevisionProposal("Analyzer가 반환한 수정 본문")).when(analyzer).generateRevision(any());
		JsonNode retried = await(post("/api/analyses/" + failed.get("id").asLong() + "/retries", null, 202)
				.get("data"), "COMPLETED");
		assertThat(snapshot(retried.get("id").asLong())).isEqualTo(originalInput);
		assertThat(jdbc.queryForObject("SELECT proposed_text FROM app.requirement_revision WHERE requirement_id=?",
				String.class, requirementId)).isEqualTo("Analyzer가 반환한 수정 본문");
		assertThat(jdbc.queryForObject("SELECT content_version FROM app.requirement WHERE id=?",
				Long.class, requirementId)).isEqualTo(3);
		assertThat(get("/api/analyses/" + failed.get("id").asLong()).at("/data/status").asString()).isEqualTo("FAILED");
	}

	@Test
	void providerMismatchUsesExistingExecutionFailureCodeWithoutInvokingAnalysis() throws Exception {
		long documentId = document();
		// Submission metadata uses 1.0.0; execution detects that the provider contract changed.
		when(analyzer.schemaVersion()).thenReturn("1.0.0", "2.0.0");
		JsonNode failed = await(post("/api/documents/" + documentId + "/analyses", null, 202).get("data"), "FAILED");
		assertThat(failed.at("/error/code").asString()).isEqualTo("ANALYSIS_EXECUTION_FAILED");
		org.mockito.Mockito.verify(analyzer, org.mockito.Mockito.never()).analyze(any());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM app.requirement WHERE document_id=?",
				Long.class, documentId)).isZero();
	}

	private long document() throws Exception {
		long projectId = post("/api/projects", Map.of("name", "Analyzer 검증"), 201).at("/data/id").asLong();
		return post("/api/projects/" + projectId + "/documents", Map.of("title", "원문", "sourceType", "TEXT",
				"content", ORIGINAL), 201).at("/data/id").asLong();
	}

	private JsonNode answer(long id, String text, long version) throws Exception {
		return post("/api/clarifications/" + id + "/answers",
				Map.of("answerText", text, "expectedContentVersion", version), 202).at("/data/analysis");
	}

	private String snapshot(long id) {
		return jdbc.queryForObject("SELECT input_snapshot::text FROM app.analysis WHERE id=?", String.class, id);
	}

	private JsonNode await(JsonNode receipt, String expected) throws Exception {
		long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
		JsonNode current;
		do {
			current = get("/api/analyses/" + receipt.get("id").asLong()).get("data");
			if (List.of("COMPLETED", "FAILED").contains(current.get("status").asString())) {
				assertThat(current.get("status").asString()).as(current.toString()).isEqualTo(expected);
				return current;
			}
			Thread.sleep(25);
		} while (System.nanoTime() < deadline);
		throw new AssertionError("분석 완료 대기 시간 초과: " + current);
	}

	private JsonNode get(String path) throws Exception {
		return request(path, null, false, 200);
	}

	private JsonNode post(String path, Object body, int status) throws Exception {
		return request(path, body, true, status);
	}

	private JsonNode request(String path, Object body, boolean post, int expectedStatus) throws Exception {
		var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
				.timeout(Duration.ofSeconds(10));
		if (post) {
			builder.header("Content-Type", "application/json").POST(body == null
					? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
		}
		var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
		return json.readTree(response.body());
	}
}
