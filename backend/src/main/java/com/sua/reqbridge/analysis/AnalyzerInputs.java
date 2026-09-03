package com.sua.reqbridge.analysis;

import java.util.List;

import com.sua.reqbridge.clarification.Clarification;
import com.sua.reqbridge.contract.RequirementSnapshot;
import com.sua.reqbridge.contract.RequirementStatus;
import com.sua.reqbridge.contract.StateConflictException;
import com.sua.reqbridge.contract.ai.AnalyzerTypes.AnswerContext;

/** Workflow-side mapping only; the shared analyzer contracts never depend on entities. */
public final class AnalyzerInputs {

	private AnalyzerInputs() {
	}

	public static List<AnswerContext> answers(List<Clarification> history) {
		return history.stream().filter(item -> item.getAnswerText() != null)
				.map(item -> new AnswerContext(item.getId(), item.getIssueId(), item.getRoundNo(),
						item.getQuestionText(), item.getAnswerText()))
				.toList();
	}

	public static void requireCurrentVersion(Analysis analysis, RequirementSnapshot requirement) {
		if (requirement.status() == RequirementStatus.CONFIRMED) {
			throw new StateConflictException("REQUIREMENT_CONFIRMED", "확정된 요구사항은 변경할 수 없습니다.");
		}
		if (analysis.getRequirementId() != requirement.id()
				|| analysis.getDocumentId() != requirement.documentId()
				|| analysis.getInputContentVersion() == null
				|| analysis.getInputContentVersion() != requirement.contentVersion()) {
			throw new StateConflictException("CONTENT_VERSION_CONFLICT", "요구사항 버전이 일치하지 않습니다.");
		}
	}
}
