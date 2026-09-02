package com.sua.reqbridge.document;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface DocumentRepository extends JpaRepository<Document, Long> {

	List<Document> findByProjectIdOrderByIdDesc(long projectId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select d from Document d where d.id = :documentId")
	Optional<Document> findByIdForUpdate(@Param("documentId") long documentId);
}
