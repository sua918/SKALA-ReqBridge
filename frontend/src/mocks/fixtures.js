import {
  AmbiguityType,
  AnalysisKind,
  AnalysisStatus,
  DocumentSourceType,
  IssueStatus,
  RequirementStatus,
} from '@/types/api'

/** mock-scenarios.md · Spec §8 고정 Fixture */

export const DEMO_CONTENT =
  '시스템은 많은 사용자의 동시 상품 조회 요청에 빠르게 응답해야 한다. 부하 시험은 10분 동안 수행하며 성공 응답 비율은 99.9% 이상이어야 한다.'

export const seedProject = {
  id: 1,
  name: 'ReqBridge 데모 프로젝트',
  description: '고객 요구사항 확인 및 확정',
  createdAt: '2026-09-02T06:00:00Z',
}

export const seedDocument = {
  id: 101,
  projectId: 1,
  title: '상품 조회 서비스 요구사항',
  sourceType: DocumentSourceType.TEXT,
  content: DEMO_CONTENT,
  createdAt: '2026-09-02T06:00:00Z',
}

export const seedAnalysisCompleted = {
  id: 301,
  kind: AnalysisKind.DOCUMENT,
  status: AnalysisStatus.COMPLETED,
  documentId: 101,
  requirementId: null,
  clarificationId: null,
  inputContentVersion: null,
  retryOfAnalysisId: null,
  createdAt: '2026-09-02T06:00:00Z',
  startedAt: '2026-09-02T06:00:00Z',
  completedAt: '2026-09-02T06:00:00Z',
  result: {
    requirementIds: [401],
    issueIds: [501, 502],
    clarificationIds: [601, 602],
    revisionIds: [],
    assessment: null,
  },
  error: null,
}

export const seedRequirement = {
  id: 401,
  documentId: 101,
  analysisId: 301,
  sequenceNo: 1,
  originalText: DEMO_CONTENT,
  status: RequirementStatus.CLARIFYING,
  contentVersion: 1,
  approvedRevisionId: null,
  confirmedText: null,
}

/** B 화면 Ambiguity 표시용 (목록 API에는 없음 · workflow/확장용 fixture) */
export const seedIssues = [
  {
    id: 501,
    requirementId: 401,
    type: AmbiguityType.QUANTITY_MISSING,
    evidence: '많은 사용자의 정량 기준이 없다.',
    status: IssueStatus.OPEN,
  },
  {
    id: 502,
    requirementId: 401,
    type: AmbiguityType.PERFORMANCE_MISSING,
    evidence: '빠르게의 측정 가능한 응답 시간 기준이 없다.',
    status: IssueStatus.OPEN,
  },
]
