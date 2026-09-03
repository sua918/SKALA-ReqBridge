package com.sua.reqbridge.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sua.reqbridge.project.ProjectNotFoundException;
import com.sua.reqbridge.project.ProjectRepository;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTests {

	@Mock
	private DocumentRepository documentRepository;

	@Mock
	private ProjectRepository projectRepository;

	@Test
	void createsTextDocumentForExistingProject() {
		when(projectRepository.existsById(11L)).thenReturn(true);
		when(documentRepository.save(any(Document.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		DocumentService service = new DocumentService(documentRepository, projectRepository);

		Document created = service.createTextDocument(11L, "  요구사항  ", "고객 원문");

		assertThat(created.getProjectId()).isEqualTo(11L);
		assertThat(created.getTitle()).isEqualTo("요구사항");
		assertThat(created.getContent()).isEqualTo("고객 원문");
		assertThat(created.getSourceType()).isEqualTo(DocumentSourceType.TEXT);
	}

	@Test
	void rejectsDocumentForMissingProject() {
		when(projectRepository.existsById(99L)).thenReturn(false);
		DocumentService service = new DocumentService(documentRepository, projectRepository);

		assertThatThrownBy(() -> service.createTextDocument(99L, "문서", "원문"))
				.isInstanceOf(ProjectNotFoundException.class);
	}
}
