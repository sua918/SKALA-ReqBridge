package com.sua.reqbridge.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PdfTextExtractorTests {
	private final PdfTextExtractor extractor = new PdfTextExtractor();

	@Test
	void extractsTextWithRealParser() throws Exception {
		assertThat(extractor.extract(PdfTestFiles.pdf("Requirement: 3000 users.")))
				.contains("Requirement: 3000 users.");
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "   "})
	void rejectsPdfWithoutUsableText(String text) throws Exception {
		byte[] bytes = PdfTestFiles.pdf(text);
		assertThatThrownBy(() -> extractor.extract(bytes)).isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "password"})
	void rejectsEncryptionEvenWithEmptyPassword(String password) throws Exception {
		byte[] bytes = PdfTestFiles.pdf("Secret", true, password);
		assertThatThrownBy(() -> extractor.extract(bytes)).isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = {"not a PDF", "%PDF-1.7 invalid structure"})
	void rejectsDisguisedOrCorruptPdf(String content) {
		assertThatThrownBy(() -> extractor.extract(content.getBytes(StandardCharsets.UTF_8)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsOversizeAndEmptyBytes() {
		assertThatThrownBy(() -> extractor.extract(new byte[0])).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> extractor.extract(new byte[PdfTextExtractor.MAX_FILE_BYTES + 1]))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void permitsExactlyTenMiB() throws Exception {
		byte[] bytes = PdfTestFiles.pdf("Boundary");
		byte[] padded = Arrays.copyOf(bytes, PdfTextExtractor.MAX_FILE_BYTES);
		Arrays.fill(padded, bytes.length, padded.length, (byte) ' ');
		assertThat(extractor.extract(padded)).contains("Boundary");
	}

	@Test
	void rejectsOversizedExtractedTextWithoutTruncation() throws Exception {
		byte[] bytes = PdfTestFiles.pdf("a".repeat(100_001));
		assertThatThrownBy(() -> extractor.extract(bytes)).isInstanceOf(IllegalArgumentException.class);
	}
}
