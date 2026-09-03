package com.sua.reqbridge.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.sua.reqbridge.contract.CoreRequirementPort;
import com.sua.reqbridge.document.storage.DocumentStorage;

// Opt-in: use ONLY a disposable database. Storage is mocked, PostgreSQL/Tomcat/PDFBox are real.
@EnabledIfEnvironmentVariable(named = "REQBRIDGE_TEST_POSTGRES_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"spring.datasource.url=${REQBRIDGE_TEST_POSTGRES_URL}",
		"spring.datasource.username=${REQBRIDGE_TEST_POSTGRES_USER}",
		"spring.datasource.password=${REQBRIDGE_TEST_POSTGRES_PASSWORD:}",
		"spring.flyway.enabled=true" })
class DocumentUploadPostgresTests {
	@LocalServerPort int port;
	@Autowired JdbcTemplate jdbc;
	@Autowired CoreRequirementPort core;
	@MockitoBean DocumentStorage storage;
	private long projectId;

	@BeforeEach
	void project() {
		projectId = jdbc.queryForObject("INSERT INTO app.project(name) VALUES ('PDF test') RETURNING id", Long.class);
	}

	@Test
	void storesMetadataAndExposesFileContentThroughUnchangedCorePort() throws Exception {
		HttpResponse<String> response = upload(PdfTestFiles.pdf("PDF requirements"));
		assertThat(response.statusCode()).isEqualTo(201);
		assertThat(response.body()).contains("\"sourceType\":\"FILE\"").doesNotContain("storagePath", "mimeType");
		long id = jdbc.queryForObject("SELECT id FROM app.document WHERE project_id=?", Long.class, projectId);
		assertThat(response.headers().firstValue("Location")).contains("/api/documents/" + id);
		assertThat(core.getDocument(id).content()).contains("PDF requirements");
		assertThat(core.getDocument(id).sourceType()).isEqualTo("FILE");
		assertThat(jdbc.queryForObject("SELECT storage_path FROM app.document WHERE id=?", String.class, id))
				.startsWith("documents/" + projectId + "/");
	}

	@Test
	void realMultipartContainerAcceptsExactLimitAndRejectsOverLimit() throws Exception {
		byte[] pdf = PdfTestFiles.pdf("Boundary");
		byte[] padded = Arrays.copyOf(pdf, PdfTextExtractor.MAX_FILE_BYTES);
		Arrays.fill(padded, pdf.length, padded.length, (byte) ' ');
		assertThat(upload(padded).statusCode()).isEqualTo(201);
		HttpResponse<String> rejected = upload(Arrays.copyOf(padded, padded.length + 1));
		assertThat(rejected.statusCode()).isEqualTo(400);
		assertThat(rejected.body()).contains("VALIDATION_ERROR");
		assertThat(rowCount()).isEqualTo(1);
	}

	@Test
	void actualForeignKeyFailureRollsBackRowAndCleansStorage() throws Exception {
		doAnswer(call -> {
			jdbc.update("DELETE FROM app.project WHERE id=?", projectId);
			return null;
		}).when(storage).upload(anyString(), any());
		HttpResponse<String> response = upload(PdfTestFiles.pdf("FK failure"));
		assertThat(response.statusCode()).isEqualTo(500);
		assertThat(response.body()).contains("INTERNAL_ERROR").doesNotContain("constraint", "INSERT", "documents/");
		assertThat(rowCount()).isZero();
		ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
		verify(storage).upload(key.capture(), any());
		verify(storage).delete(key.getValue());
	}

	@Test
	void storageFailureDoesNotCreateRow() throws Exception {
		doThrow(new DocumentUploadException()).when(storage).upload(anyString(), any());
		assertThat(upload(PdfTestFiles.pdf("Storage failure")).statusCode()).isEqualTo(500);
		assertThat(rowCount()).isZero();
	}

	@Test
	void textEndpointStillStoresTextWithNullMetadata() throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port
				+ "/api/projects/" + projectId + "/documents"))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString("{\"title\":\"Text\",\"sourceType\":\"TEXT\",\"content\":\"Original text\"}"))
				.build();
		assertThat(HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(201);
		assertThat(jdbc.queryForObject("SELECT storage_path FROM app.document WHERE project_id=?", String.class, projectId)).isNull();
		verifyNoInteractions(storage);
	}

	private int rowCount() {
		return jdbc.queryForObject("SELECT count(*) FROM app.document WHERE project_id=?", Integer.class, projectId);
	}

	private HttpResponse<String> upload(byte[] pdf) throws Exception {
		String boundary = "reqbridge-test-boundary";
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"title\"\r\n\r\nTitle\r\n--"
				+ boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"client.pdf\"\r\n"
				+ "Content-Type: application/pdf\r\n\r\n").getBytes(StandardCharsets.UTF_8));
		body.write(pdf);
		body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port
				+ "/api/projects/" + projectId + "/documents/upload"))
				.header("Content-Type", "multipart/form-data; boundary=" + boundary)
				.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
		return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
	}
}
