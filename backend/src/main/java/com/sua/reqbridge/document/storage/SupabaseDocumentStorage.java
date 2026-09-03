package com.sua.reqbridge.document.storage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.sua.reqbridge.document.DocumentUploadException;

@Component
public class SupabaseDocumentStorage implements DocumentStorage {
	private final String baseUrl;
	private final String bucket;
	private final String serviceRoleKey;
	private final HttpClient client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NEVER).build();

	public SupabaseDocumentStorage(
			@Value("${reqbridge.storage.url:}") String baseUrl,
			@Value("${reqbridge.storage.bucket:}") String bucket,
			@Value("${reqbridge.storage.service-role-key:}") String serviceRoleKey) {
		this.baseUrl = baseUrl;
		this.bucket = bucket;
		this.serviceRoleKey = serviceRoleKey;
	}

	@Override
	public void upload(String objectKey, byte[] bytes) {
		send(request(objectKey).header("Content-Type", "application/pdf")
				.header("x-upsert", "false")
				.POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(), false);
	}

	@Override
	public void delete(String objectKey) {
		send(request(objectKey).DELETE().build(), true);
	}

	private HttpRequest.Builder request(String objectKey) {
		try {
			URI origin = URI.create(baseUrl);
			boolean local = "http".equals(origin.getScheme())
					&& ("localhost".equals(origin.getHost()) || "127.0.0.1".equals(origin.getHost()));
			if ((!"https".equals(origin.getScheme()) && !local) || origin.getHost() == null
					|| origin.getUserInfo() != null || origin.getQuery() != null || origin.getFragment() != null
					|| !(origin.getPath().isEmpty() || origin.getPath().equals("/"))
					|| !bucket.matches("[A-Za-z0-9_-]+") || serviceRoleKey.isBlank()
					|| !objectKey.matches("documents/[1-9][0-9]*/[0-9a-f-]{36}\\.pdf")) {
				throw new DocumentUploadException();
			}
			URI endpoint = origin.resolve("/storage/v1/object/" + bucket + "/" + objectKey);
			return HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(60))
					.header("Authorization", "Bearer " + serviceRoleKey)
					.header("apikey", serviceRoleKey);
		} catch (IllegalArgumentException exception) {
			throw new DocumentUploadException();
		}
	}

	private void send(HttpRequest request, boolean deleting) {
		try {
			// Discard remote response text: it may contain internal paths or credentials.
			int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
			if ((status < 200 || status >= 300) && !(deleting && status == 404)) {
				throw new DocumentUploadException();
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new DocumentUploadException();
		} catch (IOException exception) {
			throw new DocumentUploadException();
		}
	}
}
