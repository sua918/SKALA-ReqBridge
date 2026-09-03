package com.sua.reqbridge.document.storage;

public interface DocumentStorage {
	void upload(String objectKey, byte[] bytes);
	void delete(String objectKey);
}
