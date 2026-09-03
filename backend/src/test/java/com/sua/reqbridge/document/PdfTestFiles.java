package com.sua.reqbridge.document;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

final class PdfTestFiles {
	private PdfTestFiles() { }

	static byte[] pdf(String text) throws IOException {
		return pdf(text, false, "");
	}

	static byte[] pdf(String text, boolean encrypted, String password) throws IOException {
		try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			PDPage page = new PDPage();
			pdf.addPage(page);
			if (!text.isEmpty()) {
				try (PDPageContentStream stream = new PDPageContentStream(pdf, page)) {
					stream.beginText();
					stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
					stream.newLineAtOffset(30, 700);
					stream.showText(text);
					stream.endText();
				}
			}
			if (encrypted) {
				pdf.protect(new StandardProtectionPolicy("owner-password", password, new AccessPermission()));
			}
			pdf.save(output);
			return output.toByteArray();
		}
	}
}
