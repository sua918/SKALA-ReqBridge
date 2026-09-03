package com.sua.reqbridge.analysis;

import java.util.HashSet;
import java.util.Set;
import com.sua.reqbridge.common.validation.TextRules;
import com.sua.reqbridge.contract.ai.AnalyzerTypes.*;
import com.sua.reqbridge.contract.ai.WorkflowAnalyzer;

/** Validate every candidate before persistence; never expose raw provider output in errors. */
public final class AnalyzerOutputValidator {

	private AnalyzerOutputValidator() {
	}

	public static DocumentResult document(DocumentResult result) {
		if (result == null || result.requirements() == null || result.requirements().isEmpty()) {
			throw invalid();
		}
		Set<Integer> sequences = new HashSet<>();
		for (RequirementCandidate requirement : result.requirements()) {
			if (requirement == null || requirement.sequenceNo() < 1
					|| requirement.sequenceNo() > result.requirements().size()
					|| !sequences.add(requirement.sequenceNo())
					|| requirement.issues() == null) {
				throw invalid();
			}
			text(requirement.originalText(), 100_000);
			for (IssueCandidate issue : requirement.issues()) {
				if (issue == null || issue.type() == null) {
					throw invalid();
				}
				text(issue.evidence(), 100_000);
				text(issue.questionText(), 100_000);
			}
		}
		return result;
	}

	public static Assessment assessment(Assessment result) {
		if (result == null) {
			throw invalid();
		}
		text(result.reason(), 100_000);
		if (!result.sufficient()) {
			text(result.nextQuestionText(), 100_000);
		} else if (result.nextQuestionText() != null) {
			throw invalid();
		}
		return result;
	}

	public static RevisionProposal revision(RevisionProposal result) {
		if (result == null) {
			throw invalid();
		}
		text(result.text(), 100_000);
		return result;
	}

	public static void requireMatchingAdapter(Analysis analysis, WorkflowAnalyzer analyzer) {
		if (analysis.getAdapterType() != analyzer.adapterType()
				|| !analysis.getSchemaVersion().equals(analyzer.schemaVersion())) {
			// Configuration/execution failure, not a model output or a new public error code.
			throw new IllegalStateException("분석 기록과 실행할 Analyzer의 종류 또는 버전이 일치하지 않습니다.");
		}
	}

	private static void text(String value, int maxCodePoints) {
		try {
			TextRules.requiredPreserved("Analyzer output", value, maxCodePoints);
		} catch (IllegalArgumentException exception) {
			throw invalid();
		}
	}

	private static AiOutputInvalidException invalid() {
		return new AiOutputInvalidException("분석 결과 형식이 올바르지 않습니다.");
	}
}
