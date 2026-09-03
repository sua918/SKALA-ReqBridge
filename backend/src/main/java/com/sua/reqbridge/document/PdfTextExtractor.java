package com.sua.reqbridge.document;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import com.sua.reqbridge.common.validation.TextRules;

@Component
public class PdfTextExtractor {
	public static final int MAX_FILE_BYTES = 10 * 1024 * 1024;
	public static final int MAX_CONTENT_CODE_POINTS = 100_000;

	public String extract(byte[] bytes) {
		if (bytes.length == 0 || bytes.length > MAX_FILE_BYTES) {
			throw new IllegalArgumentException("PDF 파일은 0바이트 초과, 10MB 이하여야 합니다.");
		}
		if (bytes.length < 5 || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
			throw new IllegalArgumentException("PDF 파일만 업로드할 수 있습니다.");
		}
		try (PDDocument pdf = Loader.loadPDF(bytes)) {
			if (pdf.isEncrypted()) {
				throw new IllegalArgumentException("암호화된 PDF는 지원하지 않습니다.");
			}
			LimitedTextWriter writer = new LimitedTextWriter();
			new PDFTextStripper().writeText(pdf, writer);
			String text = TextRules.requiredPreserved("PDF content", writer.text(), MAX_CONTENT_CODE_POINTS);
			if (text.indexOf('\0') >= 0) {
				throw new IllegalArgumentException("PDF에 저장할 수 없는 문자가 있습니다.");
			}
			return text;
		} catch (IOException exception) {
			// Parser diagnostics may contain document contents; never expose them.
			throw new IllegalArgumentException("읽을 수 없는 PDF입니다. 암호화·손상 여부를 확인해주세요.");
		}
	}

	// Bound extracted output while parsing, including surrogate pairs across write calls.
	private static final class LimitedTextWriter extends Writer {
		private final StringBuilder text = new StringBuilder();
		private int codePoints;
		private char previous;

		@Override
		public void write(char[] chars, int offset, int length) {
			for (int i = offset; i < offset + length; i++) {
				char current = chars[i];
				if (!(Character.isHighSurrogate(previous) && Character.isLowSurrogate(current))) {
					if (++codePoints > MAX_CONTENT_CODE_POINTS) {
						throw new IllegalArgumentException("PDF 추출 텍스트는 100000 코드 포인트 이하여야 합니다.");
					}
				}
				text.append(current);
				previous = current;
			}
		}

		String text() { return text.toString(); }
		@Override public void flush() { }
		@Override public void close() { }
	}
}
