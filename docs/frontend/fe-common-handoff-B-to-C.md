# ReqBridge FE common 인계 (B → C)

> **작성:** B (이병주)  
> **대상:** C (최은주)  
> **브랜치:** `feat/fe-common` → `main`  
> **기준:** API Spec 0.4.0, B/C Routing·공통 Component 합의  
> **상태:** **B Must 완료** · FE 잔여 작업은 C만 (workflow P1 · Preview P2)

---

## 0. 한 줄 요약

| 구분 | 명세 | FE 상태 |
| --- | --- | --- |
| **B** | Spec 5.1~5.12 + 8절 「프로젝트·문서/요구사항 목록」화면 | **완료** (API·화면·Mock·공통) |
| **C** | Spec 5.13~5.16 + 8절 「요구사항 상세·검토」 | **미구현** (`workflow.js` stub · placeholder 화면) |
| **C (P2)** | Spec 5.17~5.18 Preview | **미구현** (placeholder · Preview API 함수 없음) |

C는 아래 **§6 체크리스트**만 하면 된다. B 화면·분석 polling·breadcrumb은 이미 붙어 있다.

---

## 1. 시작 방법

1. `feat/fe-common` PR이 **main에 merge**된 것을 확인한다.
2. 최신 `main`을 pull 한다.
3. **`feat/fe-c`** 를 main에서 분기해 작업한다.

```bash
git checkout main
git pull origin main
git checkout -b feat/fe-c
```

---

## 2. 무엇이 들어갔나 (Summary)

| 영역 | 내용 |
|------|------|
| Router / Layout | `frontend/src/router/index.js`, `AppLayout` (breadcrumb = `route.meta` + `useBreadcrumbLabels`) |
| API + Mock | `frontend/src/api/*`, `frontend/src/mocks/*` (`npm run dev:mock`일 때만 Mock) |
| Types | `frontend/src/types/api.js` (Spec §4 enum · §7 `ApiErrorCode` · `AnalysisFailureCode`) |
| 공통 컴포넌트 | `StatusBadge`, `ErrorMessage`, `AnalysisFailureBanner`, `statusLabels`, `ambiguityLabels` |
| Composables | `useApiError`, `useAnalysisPoller`, `useActiveAnalysisLock`, `useBreadcrumbLabels` |
| **B Must 화면** | 프로젝트 목록·생성, 문서 목록·TEXT 등록·PDF 업로드, 문서 상세(원문·분석·polling·재시도·요구사항 목록·문제 건수) |
| **C placeholder** | `/requirements/:id`, `/documents/:id/preview` — 「준비 중」 |
| Workflow API | `frontend/src/api/workflow.js` — **stub** (C가 구현) |
| Preview API | **없음** — C가 `workflow.js`와 같이 또는 별도 파일로 추가 |

### 2.1 B Must ↔ Spec 대조 (완료)

| Spec | Endpoint / 규칙 | FE |
| --- | --- | --- |
| 5.1~5.3 | 프로젝트 생성·목록·상세 | `projects.js` + `ProjectListView` |
| 5.4 · 5.4.1 | TEXT·PDF 등록 | `documents.js` + `DocumentListView` |
| 5.5 · 5.6 | 문서 목록·원문 | 문서 목록 / 문서 상세 |
| 5.7 · 5.8 | 요구사항 목록·상세 | 문서 상세 목록 · breadcrumb용 `getRequirement` |
| 5.9~5.12 | 분석 접수·이력·조회·재시도 | `analyses.js` + 문서 상세 |
| 6.1 · 8 | 1초 polling · F5 복구 · 이탈 시 정리 | `useAnalysisPoller` · `restoreActiveAnalysis` |
| 6.3 | 409 시 이력 재조회 | `DOCUMENT_ALREADY_ANALYZED` · `ANALYSIS_IN_PROGRESS` |
| 8 Ambiguity | B는 목록에 Issue 없음 → **건수만** | `AnalysisResult.issueIds.length` |

---

## 3. 폴더 지도

```
frontend/src/
├── api/                 # 전부 공용 (B/C 공유)
│   ├── client.js        # axios 인스턴스 · unwrap · validationError
│   ├── config.js        # useMock 스위치
│   ├── mockError.js     # Mock 전용 오류 생성
│   ├── projects.js      # B (C는 breadcrumb 정도)
│   ├── documents.js     # B
│   ├── analyses.js      # B·C 공용 (ANSWER/REVISION polling·재시도)
│   ├── requirements.js  # B 목록 · C 진입/breadcrumb
│   └── workflow.js      # ← C 구현 대상 (현재 stub)
├── types/api.js         # enum
├── mocks/               # fixtures + in-memory store (C가 workflow Mock 추가)
├── components/common/   # StatusBadge, ErrorMessage, …
├── composables/         # useApiError, useAnalysisPoller, useBreadcrumbLabels, …
├── layouts/AppLayout.vue
└── views/
    ├── ProjectListView.vue          # B ✅
    ├── DocumentListView.vue         # B ✅
    ├── DocumentDetailView.vue       # B ✅
    ├── RequirementWorkflowView.vue  # C ❌ placeholder
    └── PreviewView.vue              # C (P2) ❌ placeholder
```

---

## 4. 공통 사용법

### 4.1 HTTP / API 함수

화면에서는 axios를 직접 쓰지 말고, `src/api/` 함수를 호출한다.

```js
import { getRequirement } from '@/api/requirements'
import { getWorkflow /* C가 구현 */ } from '@/api/workflow'

const req = await getRequirement(requirementId)
// breadcrumb용 documentId → req.documentId
```

`client.js`가 제공하는 것.

| export | 용도 |
| --- | --- |
| `api` | `baseURL: '/api'` axios 인스턴스. 응답 오류를 `error.apiError`로 정규화 |
| `unwrap(response)` | Spec 성공 래퍼 `{ data }`의 안쪽만 반환 |
| `validationError(fieldErrors)` | 서버에 보내기 전 막은 입력 오류를 400과 같은 형태로 reject |
| `isContentVersionConflict(error)` | 409 `CONTENT_VERSION_CONFLICT` 판별 (workflow에서 사용) |

- 기본 실행 `npm run dev` — 실제 백엔드 (`/api` → `localhost:8080` 프록시)
- 백엔드 없이 화면만 볼 때 `npm run dev:mock` (또는 `VITE_USE_MOCK=true`)
- Mock **API 분기**는 `src/api/*.js` 안에서만 한다
- 예외: `DocumentListView`가 TEXT 폼 **기본 문구**로 `mocks/fixtures.js`의 `DEMO_CONTENT`만 import한다 (Mock 스위치와 무관)

### 4.2 Enum

```js
import { RequirementStatus, AmbiguityType, AnalysisFailureCode /* … */ } from '@/types/api'
```

스펙 문자열과 동일하게 맞춰 두었다. 임의 문자열을 새로 만들지 않는다.

- HTTP 오류 code → `ApiErrorCode`
- 비동기 작업 `data.error.code` → `AnalysisFailureCode` (`AI_OUTPUT_INVALID` 등). HTTP와 섞지 않는다

### 4.3 StatusBadge

```vue
<StatusBadge kind="requirement" :value="requirement.status" />
<StatusBadge kind="analysis" :value="analysis.status" />
<StatusBadge kind="issue" :value="issue.status" />
<StatusBadge kind="clarification" :value="clarification.status" />
<StatusBadge kind="revision" :value="revision.status" />
```

- `kind`: `requirement | analysis | clarification | issue | revision` (상태 enum 전용)
- `value`: API에서 받은 상태 문자열
- 라벨 정의: `statusLabels.js`

`AmbiguityType`은 상태가 아니라 분류라서 StatusBadge를 쓰지 않는다. 라벨은 함수로 가져온다.

```js
import { resolveAmbiguityTypeLabel } from '@/components/common/ambiguityLabels.js'

resolveAmbiguityTypeLabel(issue.type) // QUANTITY_MISSING → '수량 누락'
```

### 4.4 에러 표시

| 상황 | 사용 |
|------|------|
| HTTP / 필드 검증 에러 | `useApiError` + `<ErrorMessage />` |
| 분석 작업 실패 (status `FAILED`) | `<AnalysisFailureBanner />` |

`DocumentDetailView.vue`에 B 쪽 사용 예시가 있다. C도 답변·재생성 실패에 같은 패턴을 쓰면 된다.

### 4.5 Ambiguity 표시 경계

명세 5.7 요구사항 목록 응답에는 `issues`가 없다. 문제의 `type`·`evidence`는 **workflow 응답에만** 있다.

| 화면 | 표시 |
| --- | --- |
| 문서 상세 (B) ✅ | 요구사항 `status` 배지 + 분석 결과의 문제 **건수** |
| 요구사항 화면 (C) ❌ | workflow의 문제 상세 (`type`, `evidence`, `status`) |

B 화면에서 요구사항마다 workflow를 호출하지 않는다(N+1 방지).

Mock으로 개발할 때는 store에 이미 문제 데이터가 있다.

```js
import { listIssuesByRequirementMock } from '@/mocks/store.js'
```

workflow Mock을 만들 때 이 함수와 store의 issues를 재사용하면 된다. **분기 import는 `src/api/workflow.js` 안에서만.**

### 4.6 contentVersion (중요)

- FE에서 **직접 +1 하지 않는다.**
- 요청 시에는 **직전 API 응답에 있던 값**만 보낸다 (`expectedContentVersion`).
- 구현·검증은 C workflow 화면에서 담당.
- Mock store는 서버 대역이라 store 안에서만 버전을 올릴 수 있다 (`fe-step0-합의-보완.md` 3-1).

### 4.7 Breadcrumb

- `AppLayout`이 `route.meta.breadcrumb`을 읽는다. route 추가·변경은 상호 PR review.
- 이름 조회는 `useBreadcrumbLabels`가 담당한다. meta 항목에 `resolve`를 지정한다.

| `resolve` | 조회 |
| --- | --- |
| `project` | `GET /projects/{id}` → 프로젝트명 (route에 `projectId`가 있을 때) |
| `documentProject` | 문서 응답의 `projectId` → 프로젝트명. **문서 목록으로 돌아가는 링크** |
| `document` | `GET /documents/{id}` → 문서 제목 |
| `requirementDocument` | `GET /requirements/{id}` → `documentId` → 문서 제목 |

- 조회 전에는 skeleton, 실패하면 `문서 #101` 같은 ID 표기로 되돌아간다. breadcrumb 오류가 화면을 막지 않는다.
- `/requirements/:requirementId`는 「프로젝트 > (프로젝트명) > 문서명 > 요구사항 #」까지 **이미 연결**돼 있다. C가 breadcrumb용으로 따로 처리할 것은 없다.
- Preview 진입 링크는 B 문서 상세에 **넣지 않았다** (P2·C 화면). C가 Preview 화면을 만들 때 문서 상세 또는 탭에서 링크하면 된다.

### 4.8 분석 polling (C도 동일)

- `useAnalysisPoller` — Spec 6.1 (약 1초, `COMPLETED`/`FAILED`에서 중단, unmount 시 정리)
- `useActiveAnalysisLock` — `PENDING`/`PROCESSING`이면 입력·재요청 잠금
- `retryAnalysis` — FAILED만. 완료 문서에 새 `POST .../analyses` 금지

---

## 5. 합의 규칙 요약

1. **`src/api/` 전부 공용** — 수정 시 상호 PR review
2. **contentVersion** — API 응답값만 사용, FE +1 금지 (Mock store는 서버 대역이라 예외)
3. **Routing** — B: 프로젝트·문서 / C: `/requirements/:id`, Preview
4. B Must 화면 핵심 로직은 가급적 유지 — 필요 시 PR로 제안
5. **분석 상태** — 최신 분석 기준으로 복원, 완료/실패 시 재접수 대신 retries

원 합의 3개 조항 + 보완 내용은 `docs/frontend/fe-step0-합의-보완.md`에 있다. **C 확인 체크리스트**가 그 문서 마지막 절에 있다.

---

## 6. C Must 체크리스트 (FE 잔여 = 이 목록)

### P1

- [ ] `frontend/src/api/workflow.js` stub 교체 — Spec **5.13~5.16**
  - `getWorkflow` · `submitAnswer` · `recreateRevision` · `reviewRevision`
  - Mock 분기는 이 파일 안에서, store 함수 추가
- [ ] `RequirementWorkflowView.vue` — 문제·질문·답변·수정안 승인/거절 (Spec 8)
- [ ] 공통 재사용: `useApiError`, `ErrorMessage`, `AnalysisFailureBanner`, `StatusBadge`, `ambiguityLabels`, `analyses.js`(polling·재시도), `isContentVersionConflict`
- [ ] 409 시 workflow 재조회 + 사용자 입력 보존 (Spec 6.3 · 8)
- [ ] `docs/frontend/fe-step0-합의-보완.md` 확인 항목 회신

### P2

- [ ] Preview API (`GET .../previews/customer` · `developer`) — Spec **5.17~5.18**
- [ ] `PreviewView.vue` 구현
- [ ] 문서 상세(또는 탭)에서 Preview 진입 링크 연결

B 범위(Spec 5.1~5.12)는 **더 이상 FE 미완 항목이 없다.**

참고 문서:

- `docs/api/ReqBridge_API_Specification.md`
- `docs/api/mock-scenarios.md`
- `docs/frontend/fe-step0-합의-보완.md`

---

## 7. 로컬 확인

```bash
cd frontend
npm install
npm run dev        # 실제 백엔드
npm run dev:mock   # 백엔드 없이 Mock
npm run build
```

**B 검증 흐름:** 프로젝트 생성 → TEXT 또는 PDF 등록 → 분석 요청 → polling → 요구사항 목록·문제 건수 → 요구사항 클릭 시 C 라우트(현재 placeholder).

**Mock 실패·재시도:** 문서 원문에 `INVALID_OUTPUT`을 넣어 등록한다. 분석이 `AI_OUTPUT_INVALID`로 실패하고, 재시도하면 성공한다 (mock-scenarios §7). ANSWER·REVISION Mock도 같은 패턴으로 붙이면 된다.

---

## 8. 연락

질문·api 계약 변경·common props 수정이 필요하면 B와 짧게 맞춘 뒤 PR로 반영한다.
