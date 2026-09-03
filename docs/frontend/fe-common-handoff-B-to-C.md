# ReqBridge FE common 인계 (B → C)

> **작성:** B (이병주)  
> **대상:** C (최은주)  
> **브랜치:** `feat/fe-common` → `main`  
> **기준:** API Spec 0.4.0, B/C Routing·공통 Component 합의  
> **상태:** common + B Must 완료 · C 화면은 placeholder

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
| Router / Layout | `frontend/src/router/index.js`, `AppLayout` (breadcrumb = `route.meta`) |
| API + Mock | `frontend/src/api/*`, `frontend/src/mocks/*` (`npm run dev:mock`일 때만 Mock) |
| Types | `frontend/src/types/api.js` (enum) |
| 공통 컴포넌트 | `StatusBadge`, `ErrorMessage`, `AnalysisFailureBanner`, labels |
| Composables | `useApiError`, `useAnalysisPoller`, `useActiveAnalysisLock` |
| B Must 화면 | 프로젝트 목록, 문서 목록, 문서 상세(분석·Ambiguity·요구사항 목록) |
| C placeholder | `/requirements/:id`, Preview — 「준비 중」 |
| Workflow API | `frontend/src/api/workflow.js` — **stub** (C가 구현) |

---

## 3. 폴더 지도

```
frontend/src/
├── api/                 # 전부 공용 (B/C 공유)
│   ├── client.js        # apiGet / apiPost / …
│   ├── config.js        # VITE_USE_MOCK 등
│   ├── projects.js
│   ├── documents.js
│   ├── analyses.js
│   ├── requirements.js
│   └── workflow.js      # ← C 구현 대상 (현재 stub)
├── types/api.js         # enum
├── mocks/               # fixtures + in-memory store
├── components/common/   # StatusBadge, ErrorMessage, …
├── composables/
├── layouts/AppLayout.vue
└── views/
    ├── ProjectListView.vue          # B
    ├── DocumentListView.vue         # B
    ├── DocumentDetailView.vue       # B
    ├── RequirementWorkflowView.vue  # C
    └── PreviewView.vue              # C (P2)
```

---

## 4. 공통 사용법

### 4.1 HTTP / API 함수

화면에서는 axios를 직접 쓰지 말고, `src/api/` 함수를 호출한다.

```js
import { getRequirement } from '@/api/requirements'
import { /* C가 추가할 함수 */ } from '@/api/workflow'

const req = await getRequirement(requirementId)
// breadcrumb용 documentId → req.documentId
```

- client: `frontend/src/api/client.js`
- 기본 실행 `npm run dev` — 실제 백엔드 (`/api` → `localhost:8080` 프록시)
- 백엔드 없이 화면만 볼 때 `npm run dev:mock` (또는 `VITE_USE_MOCK=true`)

### 4.2 Enum

```js
import { RequirementStatus, AmbiguityType /* … */ } from '@/types/api'
```

스펙 문자열과 동일하게 맞춰 두었다. 임의 문자열을 새로 만들지 않는다.

### 4.3 StatusBadge

```vue
<StatusBadge kind="requirement" :value="requirement.status" />
<StatusBadge kind="analysis" :value="analysis.status" />
<StatusBadge kind="ambiguity" :value="issue.type" />
```

- `kind`: 어떤 enum 계열인지
- `value`: API에서 받은 상태/타입 문자열
- 라벨 정의: `statusLabels.js`, `ambiguityLabels.js`

### 4.4 에러 표시

| 상황 | 사용 |
|------|------|
| HTTP / 필드 검증 에러 | `useApiError` + `<ErrorMessage />` |
| 분석 작업 실패 (status `FAILED`) | `<AnalysisFailureBanner />` |

`DocumentDetailView.vue`에 B 쪽 사용 예시가 있다.

### 4.5 contentVersion (중요)

- FE에서 **직접 +1 하지 않는다.**
- 요청 시에는 **직전 API 응답에 있던 값**만 보낸다.
- 구현·검증은 C workflow 화면에서 담당.

### 4.6 Breadcrumb

- `AppLayout`이 `route.meta`를 읽는다.
- requirement 화면에서 documentId가 필요하면  
  `GET /requirements/{id}` 응답의 `documentId`를 사용한다. (합의 그대로)

---

## 5. 합의 규칙 요약

1. **`src/api/` 전부 공용** — 수정 시 상호 PR review
2. **contentVersion** — API 응답값만 사용, FE +1 금지
3. **Routing** — B: 문서·목록 / C: `/requirements/:id`, Preview
4. B Must 화면 핵심 로직은 가급적 유지 — 필요 시 PR로 제안

---

## 6. C Must 체크리스트

- [ ] `frontend/src/api/workflow.js` 구현 (stub 교체)
- [ ] `RequirementWorkflowView.vue` — 질문·답변·수정안 승인/거절
- [ ] `PreviewView.vue` (P2) — 고객/개발 Preview
- [ ] 공통 컴포넌트·`requirements.js`·`client.js` 재사용
- [ ] Mock 시나리오와 API Spec 0.4.0 맞춤

참고 문서:

- `docs/api/ReqBridge_API_Specification.md`
- `docs/api/mock-scenarios.md`

---

## 7. 로컬 확인

```bash
cd frontend
npm install
npm run dev        # 실제 백엔드
npm run dev:mock   # 백엔드 없이 Mock
```

흐름: 프로젝트 → 문서 목록 → 문서 상세 → 요구사항 클릭 시 C 라우트(현재 placeholder).

```bash
npm run build
```

---

## 8. 연락

질문·api 계약 변경·common props 수정이 필요하면 B와 짧게 맞춘 뒤 PR로 반영한다.
