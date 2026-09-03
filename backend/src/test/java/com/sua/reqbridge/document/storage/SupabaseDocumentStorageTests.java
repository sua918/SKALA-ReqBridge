package com.sua.reqbridge.document.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;
import com.sua.reqbridge.document.DocumentUploadException;

class SupabaseDocumentStorageTests {
	private static final String KEY = "documents/1/11111111-1111-1111-1111-111111111111.pdf";
	private HttpServer server;
	private SupabaseDocumentStorage storage;
	private final List<String> methods = new ArrayList<>();
	private int responseStatus = 200;

	@BeforeEach
	void setup() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/storage/v1/object/private-documents/" + KEY, exchange -> {
			methods.add(exchange.getRequestMethod());
			assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer test-only-key");
			assertThat(exchange.getRequestHeaders().getFirst("apikey")).isEqualTo("test-only-key");
			if (exchange.getRequestMethod().equals("POST")) {
				assertThat(exchange.getRequestHeaders().getFirst("Content-Type")).isEqualTo("application/pdf");
				assertThat(exchange.getRequestHeaders().getFirst("x-upsert")).isEqualTo("false");
				assertThat(exchange.getRequestBody().readAllBytes()).isEqualTo("original bytes".getBytes(StandardCharsets.UTF_8));
			}
			byte[] body = "sensitive upstream error".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(responseStatus, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();
		storage = new SupabaseDocumentStorage("http://127.0.0.1:" + server.getAddress().getPort(),
				"private-documents", "test-only-key");
	}

	@AfterEach
	void stop() { server.stop(0); }

	@Test
	void sendsBytesWithoutUpsertAndDeletesExactObject() {
		storage.upload(KEY, "original bytes".getBytes(StandardCharsets.UTF_8));
		storage.delete(KEY);
		assertThat(methods).containsExactly("POST", "DELETE");
	}

	@Test
	void sanitizesRemoteFailure() {
		responseStatus = 403;
		assertThatThrownBy(() -> storage.upload(KEY, "original bytes".getBytes(StandardCharsets.UTF_8)))
				.isInstanceOf(DocumentUploadException.class).hasMessageNotContaining("sensitive").hasNoCause();
	}

	@Test
	void missingConfigurationFailsOnlyWhenStorageIsUsed() {
		DocumentStorage unconfigured = new SupabaseDocumentStorage("", "", "");
		assertThatThrownBy(() -> unconfigured.upload(KEY, new byte[] {1}))
				.isInstanceOf(DocumentUploadException.class);
	}

	@Test
	void rejectsPathTraversalBeforeHttpRequest() {
		assertThatThrownBy(() -> storage.delete("../other.pdf")).isInstanceOf(DocumentUploadException.class);
		assertThat(methods).isEmpty();
	}
}
