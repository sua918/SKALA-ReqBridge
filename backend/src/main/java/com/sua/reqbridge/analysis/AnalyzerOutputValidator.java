package com.sua.reqbridge.analysis;

import java.util.HashSet;
import java.util.Set;

import com.sua.reqbridge.contract.ai.AnswerAssessment;
import com.sua.reqbridge.contract.ai.DocumentAnalysisResult;
import com.sua.reqbridge.contract.ai.IssueCandidate;
import com.sua.reqbridge.contract.ai.RequirementCandidate;
import com.sua.reqbridge.contract.ai.RevisionProposal;

public final class AnalyzerOutputValidator {

	private static final int MAX_PROPOSED_TEXT_LENGTH = 20_000;

	private AnalyzerOutputValidator() {
	}

	public static void validateDocumentResult(DocumentAnalysisResult output) {
		if (output == null) {
			throw new AiOutputInvalidException("분석 결과가 비어 있습니다.");
		}
		if (output.requirements() == null || output.requirements().isEmpty()) {
			throw new AiOutputInvalidException("분석기에서 추출된 요구사항이 없습니다.");
		}
		Set<Integer> sequenceNos = new HashSet<>();
		for (RequirementCandidate candidate : output.requirements()) {
			if (candidate == null) {
				throw new AiOutputInvalidException("요구사항 후보가 null입니다.");
			}
			if (candidate.sequenceNo() < 1) {
				throw new AiOutputInvalidException("요구사항 순번은 1 이상이어야 합니다: " + candidate.sequenceNo());
			}
			if (!sequenceNos.add(candidate.sequenceNo())) {
				throw new AiOutputInvalidException("중복된 요구사항 순번입니다: " + candidate.sequenceNo());
			}
			if (candidate.originalText() == null || candidate.originalText().isBlank()) {
				throw new AiOutputInvalidException("요구사항 원문이 비어 있습니다.");
			}
			if (candidate.issues() == null) {
				throw new AiOutputInvalidException("이슈 목록이 null입니다.");
			}
			for (IssueCandidate issue : candidate.issues()) {
				if (issue == null) {
					throw new AiOutputInvalidException("이슈 후보가 null입니다.");
				}
				if (issue.type() == null) {
					throw new AiOutputInvalidException("이슈 유형(AmbiguityType)이 지정되지 않았습니다.");
				}
				if (issue.evidence() == null || issue.evidence().isBlank()) {
					throw new AiOutputInvalidException("이슈 근거가 비어 있습니다.");
				}
				if (issue.questionText() == null || issue.questionText().isBlank()) {
					throw new AiOutputInvalidException("확인 질문 문구가 비어 있습니다.");
				}
			}
		}
	}

	public static void validateAnswerAssessment(AnswerAssessment assessment) {
		if (assessment == null) {
			throw new AiOutputInvalidException("답변 판정 결과가 비어 있습니다.");
		}
		if (assessment.reason() == null || assessment.reason().isBlank()) {
			throw new AiOutputInvalidException("답변 판정 사유가 비어 있습니다.");
		}
		if (!assessment.sufficient()) {
			if (assessment.nextQuestionText() == null || assessment.nextQuestionText().isBlank()) {
				throw new AiOutputInvalidException("불충분한 답변 판정 시 후속 질문이 필요합니다.");
			}
		}
		else {
			if (assessment.nextQuestionText() != null && !assessment.nextQuestionText().isBlank()) {
				throw new AiOutputInvalidException("충분한 답변 판정 시 후속 질문이 존재할 수 없습니다.");
			}
		}
	}

	public static void validateRevisionProposal(RevisionProposal proposal) {
		if (proposal == null) {
			throw new AiOutputInvalidException("수정안 생성 결과가 비어 있습니다.");
		}
		if (proposal.proposedText() == null || proposal.proposedText().isBlank()) {
			throw new AiOutputInvalidException("수정안 문구가 비어 있습니다.");
		}
		if (proposal.proposedText().codePointCount(0, proposal.proposedText().length()) > MAX_PROPOSED_TEXT_LENGTH) {
			throw new AiOutputInvalidException("수정안 문구 길이가 허용 한도를 초과했습니다.");
		}
	}
}
