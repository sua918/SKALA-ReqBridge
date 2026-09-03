package com.sua.reqbridge.document;

public class DocumentUploadException extends RuntimeException {
	public DocumentUploadException() {
		super("문서 파일 처리 중 오류가 발생했습니다.");
	}
}
