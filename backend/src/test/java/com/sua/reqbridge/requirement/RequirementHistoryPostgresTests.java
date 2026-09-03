package com.sua.reqbridge.requirement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.sua.reqbridge.contract.CoreRequirementPort;
import com.sua.reqbridge.contract.RequirementSeed;
import com.sua.reqbridge.contract.RequirementStatus;
import com.sua.reqbridge.revision.RevisionWorkflowService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Opt-in only: real HTTP, async Mock workflow, JPA and PostgreSQL; never point at a shared DB. */
@EnabledIfEnvironmentVariable(named = "REQBRIDGE_TEST_POSTGRES_URL", matches = ".+")
@ActiveProfiles("default")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"spring.datasource.url=${REQBRIDGE_TEST_POSTGRES_URL}",
		"spring.datasource.username=${REQBRIDGE_TEST_POSTGRES_USER}",
		"spring.datasource.password=${REQBRIDGE_TEST_POSTGRES_PASSWORD:}",
		"spring.flyway.enabled=true",
		"reqbridge.storage.url=", "reqbridge.storage.bucket=", "reqbridge.storage.service-role-key="})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RequirementHistoryPostgresTests {
	private static final String ORIGINAL = "시스템은 많은 사용자의 동시 상품 조회 요청에 빠르게 응답해야 한다. "
			+ "부하 시험은 10분 동안 수행하며 성공 응답 비율은 99.9% 이상이어야 한다.";
	@LocalServerPort int port;
	@Autowired JdbcTemplate jdbc;
	@Autowired CoreRequirementPort core;
	@Autowired RevisionWorkflowService reviews;
	@Autowired PlatformTransactionManager transactions;
	@Autowired ObjectMapper json;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	@Test
	void historySurvivesAnswersRejectionRegenerationApprovalAndRepeatedReads() throws Exception {
		long documentId = createDocument();
		JsonNode initial = awaitAnalysis(post("/api/documents/" + documentId + "/analyses", null, 202).get("data"), "COMPLETED");
		long requirementId = initial.at("/result/requirementIds/0").asLong();
		JsonNode workflow = workflow(requirementId);
		long quantityIssue = find(workflow.get("issues"), "type", "QUANTITY_MISSING").get("id").asLong();
		long performanceIssue = find(workflow.get("issues"), "type", "PERFORMANCE_MISSING").get("id").asLong();
		long firstQuestion = question(workflow, quantityIssue, 1);
		long performanceQuestion = question(workflow, performanceIssue, 1);

		JsonNode insufficient = answer(firstQuestion, "많이 접속할 것 같습니다.", 1, 2);
		assertThat(insufficient.at("/result/assessment/sufficient").asBoolean()).isFalse();
		long nextQuestion = insufficient.at("/result/assessment/nextClarificationId").asLong();
		JsonNode duplicateAnswer = post("/api/clarifications/" + firstQuestion + "/answers",
				Map.of("answerText", "많이 접속할 것 같습니다.", "expectedContentVersion", 1), 200);
		assertThat(duplicateAnswer.at("/data/analysis/id").asLong()).isEqualTo(insufficient.get("id").asLong());
		assertThat(duplicateAnswer.at("/data/contentVersion").asLong()).isEqualTo(2);
		assertThat(post("/api/clarifications/" + nextQuestion + "/answers",
				Map.of("answerText", "최대 동시 사용자 3,000명입니다.", "expectedContentVersion", 1), 409)
				.at("/error/code").asString()).isEqualTo("CONTENT_VERSION_CONFLICT");

		answer(nextQuestion, "최대 동시 사용자 3,000명입니다.", 2, 3);
		answer(performanceQuestion, "p95 응답 시간 2초 이하입니다.", 3, 4);
		long firstRevision = workflow(requirementId).at("/revisions/0/id").asLong();
		String reason = "측정 조건을 명시해주세요.";
		JsonNode rejected = review(firstRevision, "REJECT", reason, 4, 200);
		assertThat(rejected.at("/data/requirement/contentVersion").asLong()).isEqualTo(5);
		assertThat(rejected.at("/data/revision/inputContentVersion").asLong()).isEqualTo(4);
		assertThat(review(firstRevision, "REJECT", reason, 4, 200).at("/data/requirement/contentVersion").asLong()).isEqualTo(5);
		Map<String, Object> rejectedRow = revisionRow(firstRevision);
		assertThat(rejectedRow.get("reviewed_at")).isNotNull();
		assertThat(rejectedRow.get("approved_at")).isNull();

		JsonNode regenerated = awaitAnalysis(post("/api/requirements/" + requirementId + "/revisions",
				Map.of("expectedContentVersion", 5), 202).get("data"), "COMPLETED");
		long secondRevision = regenerated.at("/result/revisionIds/0").asLong();
		assertThat(regenerated.get("inputContentVersion").asLong()).isEqualTo(5);
		assertThat(workflow(requirementId).at("/revisions/0/revisionNo").asInt()).isEqualTo(2);
		List<Map<String, Object>> analysisHistory = storedAnalysisHistory(documentId);

		// A failure after both writes must roll back Revision AND the Core confirmation snapshot.
		assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
			reviews.review(secondRevision, "APPROVE", null, 5);
			throw new IllegalStateException("deliberate transaction rollback");
		})).isInstanceOf(IllegalStateException.class);
		assertThat(core.getRequirement(requirementId).status()).isEqualTo(RequirementStatus.IN_REVIEW);
		assertThat(core.getRequirement(requirementId).approvedRevisionId()).isNull();
		assertThat(revisionRow(secondRevision).get("status")).isEqualTo("PROPOSED");
		assertThat(revisionRow(secondRevision).get("reviewed_at")).isNull();
		assertThat(confirmedAt(requirementId)).isNull();

		assertThat(review(secondRevision, "APPROVE", null, 4, 409).at("/error/code").asString())
				.isEqualTo("CONTENT_VERSION_CONFLICT");
		JsonNode approved = review(secondRevision, "APPROVE", null, 5, 200);
		assertThat(approved.at("/data/requirement/status").asString()).isEqualTo("CONFIRMED");
		assertThat(approved.at("/data/requirement/confirmedText"))
				.isEqualTo(approved.at("/data/revision/text"));
		assertThat(approved.at("/data/requirement/approvedRevisionId").asLong()).isEqualTo(secondRevision);
		String confirmationTime = confirmedAt(requirementId);
		Map<String, Object> approvedRow = revisionRow(secondRevision);
		assertThat(confirmationTime).isNotNull();
		assertThat(approvedRow.get("reviewed_at")).isNotNull();
		assertThat(approvedRow.get("approved_at")).isNotNull();
		review(secondRevision, "APPROVE", null, 5, 200);
		assertThat(review(secondRevision, "REJECT", reason, 5, 409).at("/error/code").asString())
				.isEqualTo("REVISION_ALREADY_REVIEWED");

		JsonNode history = workflow(requirementId);
		assertThat(history.get("clarifications").size()).isEqualTo(3);
		JsonNode originalAnswer = find(history.get("clarifications"), "id", Long.toString(firstQuestion));
		assertThat(originalAnswer.get("status").asString()).isEqualTo("ANSWERED");
		assertThat(originalAnswer.get("answerText").asString()).isEqualTo("많이 접속할 것 같습니다.");
		assertThat(question(history, quantityIssue, 2)).isEqualTo(nextQuestion);
		assertThat(history.at("/revisions/0/status").asString()).isEqualTo("APPROVED");
		assertThat(history.at("/revisions/1/status").asString()).isEqualTo("REJECTED");
		assertThat(ids(history.at("/revisions/0/basedOnClarificationIds")))
				.containsExactlyInAnyOrder(firstQuestion, nextQuestion, performanceQuestion);
		assertThat(history).isEqualTo(workflow(requirementId));
		assertThat(revisionRow(firstRevision)).isEqualTo(rejectedRow);
		assertThat(revisionRow(secondRevision)).isEqualTo(approvedRow);
		assertThat(storedAnalysisHistory(documentId)).isEqualTo(analysisHistory);
		assertThat(confirmedAt(requirementId)).isEqualTo(confirmationTime);
		assertThat(core.getRequirement(requirementId).contentVersion()).isEqualTo(5);
		assertThat(core.getRequirement(requirementId).originalText()).isEqualTo(ORIGINAL);
		assertThat(get("/api/requirements/" + requirementId).at("/data/confirmedText").asString())
				.isEqualTo(approved.at("/data/revision/text").asString());
		assertThat(get("/api/documents/" + documentId + "/analyses").at("/data/items").size()).isEqualTo(5);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM app.revision_clarification WHERE requirement_id=?",
				Long.class, requirementId)).isEqualTo(6L);
	}

	@Test
	void failedAnswerAndRetryKeepOriginalInputAndDoNotIncreaseTheVersionAgain() throws Exception {
		long documentId = createDocument();
		JsonNode initial = awaitAnalysis(post("/api/documents/" + documentId + "/analyses", null, 202).get("data"), "COMPLETED");
		long requirementId = initial.at("/result/requirementIds/0").asLong();
		long questionId = workflow(requirementId).at("/clarifications/0/id").asLong();
		JsonNode failed = awaitAnalysis(post("/api/clarifications/" + questionId + "/answers",
				Map.of("answerText", "Mock에 없는 답변", "expectedContentVersion", 1), 202).at("/data/analysis"), "FAILED");
		long failedId = failed.get("id").asLong();
		JsonNode retried = awaitAnalysis(post("/api/analyses/" + failedId + "/retries", null, 202).get("data"), "FAILED");
		assertThat(retried.get("retryOfAnalysisId").asLong()).isEqualTo(failedId);
		assertThat(inputSnapshot(retried.get("id").asLong())).isEqualTo(inputSnapshot(failedId));
		assertThat(core.getRequirement(requirementId).contentVersion()).isEqualTo(2);
		assertThat(workflow(requirementId).at("/clarifications/0/answerText").asString()).isEqualTo("Mock에 없는 답변");
		assertThat(workflow(requirementId).at("/clarifications/0/status").asString()).isEqualTo("ANSWERED");
		assertThat(get("/api/analyses/" + failedId).get("data")).isEqualTo(failed);
	}

	@Test
	void twoConcurrentCoreWritesCannotBothUseTheSameContentVersion() throws Exception {
		long requirementId = seedRequirement();
		CountDownLatch start = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var first = executor.submit(() -> advanceAfter(start, requirementId));
			var second = executor.submit(() -> advanceAfter(start, requirementId));
			start.countDown();
			assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
					.containsExactlyInAnyOrder("UPDATED", "CONTENT_VERSION_CONFLICT");
		}
		assertThat(core.getRequirement(requirementId).contentVersion()).isEqualTo(2);
	}

	@Test
	void confirmationCannotCommitWithoutTheWorkflowTransaction() {
		long requirementId = seedRequirement();
		assertThatThrownBy(() -> core.confirmRequirement(requirementId, 1, 1, "승인 본문"))
				.isInstanceOf(IllegalTransactionStateException.class);
		assertThat(core.getRequirement(requirementId).approvedRevisionId()).isNull();
		assertThat(confirmedAt(requirementId)).isNull();
	}

	@Test
	void wrongRequirementRevisionForeignKeyRollsBackTheWholeConfirmation() {
		long first = seedRequirement();
		long second = seedRequirement();
		long otherRevision = jdbc.queryForObject("""
				INSERT INTO app.requirement_revision(requirement_id,input_content_version,revision_no,proposed_text,source)
				VALUES (?,1,1,'다른 요구사항의 수정안','MANUAL') RETURNING id
				""", Long.class, second);
		core.changeStatus(first, 1, RequirementStatus.IN_REVIEW);
		assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(tx ->
				core.confirmRequirement(first, 1, otherRevision, "잘못된 연결")))
				.isInstanceOf(RuntimeException.class).hasMessageContaining("fk_requirement_approved_revision");
		assertThat(core.getRequirement(first).status()).isEqualTo(RequirementStatus.IN_REVIEW);
		assertThat(core.getRequirement(first).approvedRevisionId()).isNull();
		assertThat(core.getRequirement(first).confirmedText()).isNull();
		assertThat(confirmedAt(first)).isNull();
	}

	private String advanceAfter(CountDownLatch start, long id) throws InterruptedException {
		if (!start.await(5, TimeUnit.SECONDS)) { throw new AssertionError("Start barrier timed out"); }
		try {
			core.advanceContentVersion(id, 1);
			return "UPDATED";
		} catch (RequirementStateException conflict) {
			return conflict.getCode();
		}
	}

	private long seedRequirement() {
		long project = jdbc.queryForObject("INSERT INTO app.project(name) VALUES ('History test') RETURNING id", Long.class);
		long document = jdbc.queryForObject("INSERT INTO app.document(project_id,title,content,source_type) VALUES (?,'test','원문','TEXT') RETURNING id", Long.class, project);
		long analysis = jdbc.queryForObject("""
				INSERT INTO app.analysis(document_id,kind,adapter_type,schema_version,input_snapshot)
				VALUES (?,'DOCUMENT','MOCK','1.0.0','{}'::jsonb) RETURNING id
				""", Long.class, document);
		return core.createRequirements(document, analysis, List.of(new RequirementSeed(1, "원문"))).get(0).id();
	}

	private long createDocument() throws Exception {
		long projectId = post("/api/projects", Map.of("name", "History HTTP test"), 201).at("/data/id").asLong();
		return post("/api/projects/" + projectId + "/documents",
				Map.of("title", "성능 요건서", "content", ORIGINAL, "sourceType", "TEXT"), 201).at("/data/id").asLong();
	}

	private JsonNode answer(long question, String text, long version, long nextVersion) throws Exception {
		JsonNode receipt = post("/api/clarifications/" + question + "/answers",
				Map.of("answerText", text, "expectedContentVersion", version), 202);
		assertThat(receipt.at("/data/contentVersion").asLong()).isEqualTo(nextVersion);
		return awaitAnalysis(receipt.at("/data/analysis"), "COMPLETED");
	}

	private JsonNode review(long id, String decision, String reason, long version, int status) throws Exception {
		Map<String, Object> body = new java.util.HashMap<>(Map.of("decision", decision, "expectedContentVersion", version));
		if (reason != null) { body.put("rejectionReason", reason); }
		return post("/api/revisions/" + id + "/review", body, status);
	}

	private JsonNode workflow(long id) throws Exception { return get("/api/requirements/" + id + "/workflow").get("data"); }
	private JsonNode get(String path) throws Exception { return request(path, null, false, 200); }
	private JsonNode post(String path, Object body, int status) throws Exception { return request(path, body, true, status); }

	private JsonNode request(String path, Object body, boolean post, int status) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(10));
		if (post) {
			request.header("Content-Type", "application/json").POST(body == null ? HttpRequest.BodyPublishers.noBody()
					: HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
		}
		HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).as("%s: %s", path, response.body()).isEqualTo(status);
		return json.readTree(response.body());
	}

	private JsonNode awaitAnalysis(JsonNode receipt, String expectedStatus) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		JsonNode current;
		do {
			current = get("/api/analyses/" + receipt.get("id").asLong()).get("data");
			String state = current.get("status").asString();
			if (state.equals("COMPLETED") || state.equals("FAILED")) {
				assertThat(state).as("Analysis result: %s", current).isEqualTo(expectedStatus);
				return current;
			}
			Thread.sleep(25);
		} while (System.nanoTime() < deadline);
		throw new AssertionError("Analysis did not finish: " + current);
	}

	private JsonNode find(JsonNode array, String field, String value) {
		for (int i = 0; i < array.size(); i++) {
			if (value.equals(array.get(i).get(field).asString())) { return array.get(i); }
		}
		throw new AssertionError("Missing " + field + "=" + value);
	}

	private long question(JsonNode workflow, long issueId, int round) {
		JsonNode array = workflow.get("clarifications");
		for (int i = 0; i < array.size(); i++) {
			JsonNode question = array.get(i);
			if (question.get("issueId").asLong() == issueId && question.get("roundNo").asInt() == round) {
				return question.get("id").asLong();
			}
		}
		throw new AssertionError("Missing question for issue " + issueId + " round " + round);
	}

	private List<Long> ids(JsonNode array) {
		List<Long> ids = new ArrayList<>();
		for (int i = 0; i < array.size(); i++) { ids.add(array.get(i).asLong()); }
		return ids;
	}

	private String confirmedAt(long id) {
		return jdbc.queryForObject("SELECT confirmed_at::text FROM app.requirement WHERE id=?", String.class, id);
	}
	private String inputSnapshot(long id) {
		return jdbc.queryForObject("SELECT input_snapshot::text FROM app.analysis WHERE id=?", String.class, id);
	}
	private Map<String, Object> revisionRow(long id) {
		return jdbc.queryForMap("SELECT id,status::text,proposed_text,input_content_version,rejection_reason,approved_at,reviewed_at FROM app.requirement_revision WHERE id=?", id);
	}
	private List<Map<String, Object>> storedAnalysisHistory(long documentId) {
		return jdbc.queryForList("SELECT id,result::text,input_snapshot::text FROM app.analysis WHERE document_id=? ORDER BY id", documentId);
	}
}
