package com.sua.reqbridge.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.sua.reqbridge.contract.AnalysisKind;
import com.sua.reqbridge.contract.AnalysisStatus;

class AnalysisTests {

	@Test
	void completesAnAnswerAnalysisWithItsImmutableInput() {
		Analysis analysis = Analysis.pendingAnswer(101L, 401L, 601L, 2L, "{\"answerText\":\"답변\"}");
		Instant startedAt = Instant.parse("2026-09-02T06:00:00Z");
		Instant completedAt = Instant.parse("2026-09-02T06:00:01Z");

		analysis.start(startedAt);
		analysis.complete("{\"assessment\":{}}", completedAt);

		assertThat(analysis.getKind()).isEqualTo(AnalysisKind.ANSWER);
		assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
		assertThat(analysis.getDocumentId()).isEqualTo(101L);
		assertThat(analysis.getRequirementId()).isEqualTo(401L);
		assertThat(analysis.getClarificationId()).isEqualTo(601L);
		assertThat(analysis.getInputContentVersion()).isEqualTo(2L);
		assertThat(analysis.getInputSnapshot()).isEqualTo("{\"answerText\":\"답변\"}");
		assertThat(analysis.getResult()).isEqualTo("{\"assessment\":{}}");
		assertThat(analysis.getErrorCode()).isNull();
		assertThat(analysis.getStartedAt()).isEqualTo(startedAt);
		assertThat(analysis.getCompletedAt()).isEqualTo(completedAt);
	}

	@Test
	void failedAnalysisHasErrorInsteadOfResult() {
		Analysis analysis = Analysis.pendingDocument(101L, "{\"content\":\"원문\"}");

		analysis.fail("AI_OUTPUT_INVALID", "분석 결과 형식이 올바르지 않습니다.",
				Instant.parse("2026-09-02T06:00:01Z"));

		assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.FAILED);
		assertThat(analysis.getResult()).isNull();
		assertThat(analysis.getErrorCode()).isEqualTo("AI_OUTPUT_INVALID");
		assertThat(analysis.getErrorMessage()).isEqualTo("분석 결과 형식이 올바르지 않습니다.");
	}

	@Test
	void cannotCompleteWithoutStarting() {
		Analysis analysis = Analysis.pendingRevision(101L, 401L, 5L, "{}");

		assertThatThrownBy(() -> analysis.complete("{}", Instant.now()))
				.isInstanceOf(IllegalStateException.class);
	}
}
