import {
  AmbiguityType,
  AnalysisKind,
  AnalysisStatus,
  ClarificationStatus,
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

/**
 * 질문 601·602 (mock-scenarios.md §1·§2).
 *
 * seedAnalysisCompleted.result.clarificationIds가 이미 `[601, 602]`를 가리키는데
 * 정작 질문 레코드가 store에 없었다. 질문은 workflow 범위라 B가 만들지 않았고,
 * 그래서 요구사항 401은 CLARIFYING인데 답할 질문이 하나도 없는 상태였다.
 * workflow를 맡은 C가 채운다.
 */
export const seedClarifications = [
  {
    id: 601,
    requirementId: 401,
    issueId: 501,
    roundNo: 1,
    questionText: '부하 시험의 최대 동시 사용자는 몇 명인가요?',
    answerText: null,
    status: ClarificationStatus.WAITING,
  },
  {
    id: 602,
    requirementId: 401,
    issueId: 502,
    roundNo: 1,
    questionText: '부하 시험에서 목표 응답 시간과 측정 지표는 무엇인가요?',
    answerText: null,
    status: ClarificationStatus.WAITING,
  },
]

/**
 * Mock 재판정 표 (mock-scenarios.md §3~§5).
 *
 * `answer`는 **정규화된** 답변과 정확히 일치할 때만 쓴다.
 * 표에 없는 답변을 임의로 「충분」 처리하지 않는다 — mock-scenarios 서문의
 * 「지원하지 않는 입력을 임의 성공 처리하지 않는다」가 이 표의 존재 이유다.
 */
export const ANSWER_ASSESSMENTS = [
  {
    answer: '많이 접속할 것 같습니다.',
    sufficient: false,
    reason: '최대 동시 사용자 수가 숫자로 제시되지 않았습니다.',
    nextQuestionText: '부하 시험의 최대 동시 사용자 수를 숫자로 알려주세요.',
  },
  {
    answer: '최대 동시 사용자 3,000명입니다.',
    sufficient: true,
    reason: '정량 기준이 확인되었습니다.',
    nextQuestionText: null,
  },
  {
    answer: 'p95 응답 시간 2초 이하입니다.',
    sufficient: true,
    reason: '목표 응답 시간과 측정 지표가 확인되었습니다.',
    nextQuestionText: null,
  },
]

/** 표에 없는 답변. 성공으로 넘기지 않고 같은 문제의 다음 회차를 연다. */
export const UNSUPPORTED_ASSESSMENT = {
  sufficient: false,
  reason:
    'Mock은 mock-scenarios.md의 예시 답변만 판정합니다. 지원하지 않는 입력은 충분한 답변으로 처리하지 않습니다.',
  nextQuestionText: '시나리오 문서의 예시 답변을 그대로 입력해주세요.',
}

/** 수정안 본문 (mock-scenarios.md §5·§6). */
export const REVISION_TEXTS = {
  FIRST:
    '시스템은 최대 동시 사용자 3,000명의 상품 조회 부하 시험을 10분간 수행할 때 p95 응답 시간 2초 이하, 성공 응답 비율 99.9% 이상을 만족해야 한다.',
  REGENERATED:
    '시스템은 최대 동시 사용자 수 3,000명 조건의 상품 조회 부하 시험을 10분간 수행할 때 p95 응답 시간 2초 이하, 성공 응답 비율 99.9% 이상을 만족해야 한다.',
}
