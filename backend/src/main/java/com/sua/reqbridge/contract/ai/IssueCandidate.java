package com.sua.reqbridge.contract.ai;

import com.sua.reqbridge.contract.AmbiguityType;

public record IssueCandidate(AmbiguityType type, String evidence, String questionText) {
}
