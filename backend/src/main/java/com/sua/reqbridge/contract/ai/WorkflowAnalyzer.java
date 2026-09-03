package com.sua.reqbridge.contract.ai;

import com.sua.reqbridge.contract.AnalysisAdapterType;

public interface WorkflowAnalyzer {

	DocumentAnalysisResult analyzeDocument(DocumentAnalysisInput input);

	AnswerAssessment assessAnswer(AnswerAssessmentInput input);

	RevisionProposal generateRevision(RevisionGenerationInput input);

	AnalysisAdapterType adapterType();

	String schemaVersion();
}
