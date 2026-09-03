package com.sua.reqbridge.contract.ai;

import com.sua.reqbridge.contract.AnalysisAdapterType;
import com.sua.reqbridge.contract.DocumentSnapshot;
import com.sua.reqbridge.contract.ai.AnalyzerTypes.*;

/** Produces proposals only. Persistence, versions and human approval belong to the workflow. */
public interface WorkflowAnalyzer {

	DocumentResult analyze(DocumentSnapshot document);

	Assessment assess(AnswerAssessmentInput input);

	RevisionProposal generateRevision(RevisionGenerationInput input);

	AnalysisAdapterType adapterType();

	String schemaVersion();
}
