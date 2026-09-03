# ReqBridge FE Step 0 합의 보완

> **대상:** B(이병주) · C(최은주)  
> **기준:** API Spec 0.4.0, `docs/api/mock-scenarios.md`  
> **원문:** FE Step 0 합의 (Breadcrumb · `src/api/` 공용 · contentVersion)  
> **상태:** B 측 **구현 반영 완료** · 아래 항목은 **C 확인 후 확정**

---

## 구현 현황 (최종)

| 구분 | 범위 | 상태 |
| --- | --- | --- |
| **B Must** | Spec 5.1~5.12, 8절 프로젝트·문서 화면, 공통·Mock(문서 분석) | ✅ 완료 |
| **C Must (P1)** | Spec 5.13~5.16, 요구사항 workflow 화면 | ❌ 미착수 (stub · placeholder) |
| **C (P2)** | Spec 5.17~5.18 Preview | ❌ 미착수 |

원 합의 3개 조항은 **변경하지 않았다.** 아래 4~12는 구현 중 내린 결정을 문서로 고정한 것이다. C가 확인란에 회신하면 팀 합의로 확정한다.

| 원 조항 | 상태 |
| --- | --- |
| 1. Breadcrumb = route meta | 유지 · 이름 조회·`documentProject`는 신규 10·12 |
| 2. `src/api/` 공용, workflow는 C 구현 | 변경 없음 |
| 3. contentVersion FE +1 금지 | 유지 + 아래 3-1 단서 |

상세 인계·C 작업 목록: `docs/frontend/fe-common-handoff-B-to-C.md`

---

## 왜 보완이 필요한가

PR 리뷰·명세 대조에서 드러난 공백(실행 모드, Mock 경계, Ambiguity 표시, PDF, breadcrumb 이름 등)을 채우면서 **원 합의안에 없던 결정**을 내렸다. 모두 B/C가 공유하는 코드에 영향을 주므로 남긴다.

---

## 신규 4. 실행 모드와 Mock 스위치

기존에는 Mock 사용 여부에 대한 합의가 없었고, 코드 기본값이 Mock이라 **백엔드를 전혀 호출하지 않는 실행이 기본**이었다.

| 명령 | 데이터 원본 |
| --- | --- |
| `npm run dev` | 실제 백엔드 (`/api` → `localhost:8080` 프록시) |
| `npm run dev:mock` | 프런트엔드 메모리 Mock |

- 코드 기본값은 **실제 백엔드**다. (`useMock = VITE_USE_MOCK === 'true'`)
- Mock은 `--mode mock` 또는 `VITE_USE_MOCK=true`로만 켠다.
- 판정은 `frontend/vite.config.js` 한 곳에서 한다. 화면·API 함수는 `@/api/config`의 `useMock`만 본다.
- `.env.development`는 gitignore 대상이라 팀에 공유되지 않으므로 사용하지 않는다.

**B 구현:** 반영됨 (`README.md` 실행 모드 표 · `package.json`의 `dev:mock`). Mock 안내는 `.env.example`에 두지 않고 `npm run dev:mock`으로 통일한다.

---

## 신규 5. Mock 데이터의 위치와 소유

| 항목 | 합의(안) |
| --- | --- |
| 위치 | `frontend/src/mocks/` (`fixtures.js`, `store.js`) |
| 역할 | **서버 대역**. 화면 로직이 아니라 API 응답을 대신 만든다 |
| 소유 | B(문서 분석까지). C가 workflow Mock을 추가할 때는 **같은 store**에 함수를 더한다 |
| 경계 | Mock **API 분기**는 `src/api/*.js` 안에서만 한다 |
| 고정 ID | `mock-scenarios.md` fixture(문서 101, 요구사항 401, 이슈 501·502, 분석 301)를 유지한다 |
| 예외 | `DocumentListView`가 TEXT 폼 **기본 문구**로 `DEMO_CONTENT`만 fixtures에서 import한다 (스위치와 무관) |

### 5-1. 새 문서 분석 Mock

- 새로 등록한 문서를 분석하면 **원문 문장 단위로 요구사항을 생성**하고, 모호 표현이 있으면 `AmbiguityIssue`를 만든다.
- 분류는 `mock-scenarios.md` 9절 표를 축약한 **키워드 규칙**이다. FE Mock 전용이며, 백엔드 Mock Analyzer와 동일할 의무는 없다.
- 질문(Clarification)은 workflow 범위라 FE Mock에서 만들지 않는다. 문제가 있는 요구사항 상태는 `CLARIFYING`이 아니라 **`AMBIGUOUS`** (Spec 4.1).
- 분석 접수는 Spec 5.9대로 **`PENDING`으로 시작해 잠시 후 종료**한다. polling 경로가 Mock에서도 검증된다.

**B 구현:** 반영됨.

---

## 보완 3-1. contentVersion과 Mock

원 조항(“FE에서 +1 금지, 응답값만 사용”)은 그대로 둔다. 적용 대상만 명확히 한다.

| 코드 | 규칙 |
| --- | --- |
| `.vue`, composable | 응답값만 사용. 직접 계산·증가 금지 |
| `src/mocks/store.js` | **서버 대역이므로 예외.** 서버가 하는 버전 증가를 흉내 낼 수 있다 |

C의 workflow Mock에서 답변·거절로 버전을 올릴 때도 store 안에서만 처리한다.

---

## 신규 6. 분석 상태 표시 규칙 (B·C 공통)

`useAnalysisPoller`와 분석 이력 API를 쓰는 모든 화면에 적용한다.

1. **새로고침 복원**: 진행 중(`PENDING`/`PROCESSING`)이 있으면 그것을 polling한다. 없으면 **id가 가장 큰 작업**을 현재 상태로 삼는다.
2. **분석 요청 버튼**: 성공 이력이 있으면 비활성화(`분석 완료`). 실패 시 새 접수 대신 `POST /analyses/{id}/retries`만 (`재시도 필요` + 실패 배너). Spec 5.9·6.3.
3. **실패 배너**: `HTTP 200 + status=FAILED` → `AnalysisFailureBanner`, HTTP 오류 → `ErrorMessage`.

**B 구현:** 문서 상세에 반영됨. C의 ANSWER/REVISION도 동일 규칙을 쓴다.

---

## 신규 7. `useAnalysisPoller` 콜백 계약

| 항목 | 계약 |
| --- | --- |
| `onComplete` / `onFailed` | 동기 또는 **async 허용**. poller가 `await`한다 |
| 콜백 내부 실패 | poller가 잡아 **`onError`로 전달**한다 |
| 종료 | `COMPLETED`/`FAILED`면 중단. 화면 이탈 시 정리 (Spec 6.1) |

**B 구현:** 반영됨.

---

## 신규 8. 프로젝트 생성 화면 담당

프로젝트 생성은 원래 B/C 어디에도 명시되지 않았다. `/projects`가 B 소유이므로 **B 담당**으로 확정·구현했다.

| 항목 | 합의(안) |
| --- | --- |
| 담당 | **B** |
| 화면 | 프로젝트 목록 안 생성 폼 → 생성 후 해당 프로젝트 문서 목록으로 이동 |
| API | `POST /api/projects` (Spec 5.1). `name` 필수 최대 100자, `description` 선택·공백이면 `null` |

C 담당(`/requirements/:requirementId`, Preview)은 변하지 않는다.

**B 구현:** 반영됨.

---

## 신규 9. Ambiguity 표시 경계

역할 문서의 「Ambiguity 표시」와 달리, **명세는 Issue를 B가 쓰는 목록/상세 응답에 주지 않는다.**

| 응답 | Issue 포함 |
| --- | --- |
| 5.7 · 5.8 (B) | 없음 (`status`만) |
| 5.13 workflow (C) | `issues[]` |
| 5.17 · 5.18 Preview (C, P2) | 포함 |

**합의: 명세 8절.**

| 화면 | 표시 범위 | 상태 |
| --- | --- | --- |
| 문서 상세 (B) | `status` 배지 + **문제 건수** (`issueIds.length`) | ✅ |
| 요구사항 화면 (C) | workflow `type` · `evidence` · `status` | ❌ C |

`ambiguityLabels.js` + `resolveAmbiguityTypeLabel()`은 공통. `StatusBadge`에 `kind="ambiguity"`는 **없다.**

---

## 신규 10. PDF 업로드와 breadcrumb 이름 · 409 복구

| 항목 | 내용 | 상태 |
| --- | --- | --- |
| PDF 업로드 | `uploadPdfDocument()` + 문서 목록 폼 (Spec 5.4.1) | ✅ B |
| 업로드 검증 | 제목 200자, PDF만, 10MB 이하 → `validationError` | ✅ B |
| Mock PDF | 추출 미재현. 파일명 + 데모 원문을 `content`로 저장 | ✅ B |
| breadcrumb 이름 | `useBreadcrumbLabels` + meta `resolve` | ✅ B |
| 409 복구 | `DOCUMENT_ALREADY_ANALYZED` · `ANALYSIS_IN_PROGRESS` → 이력 복원 | ✅ B |

---

## 신규 11. Mock 분석 실패 재현

재시도 UI(Spec 5.12)를 Mock으로 확인하기 위해 mock-scenarios **7절** `INVALID_OUTPUT`을 따른다.

| 항목 | 내용 |
| --- | --- |
| 실패 조건 | 문서 원문에 `INVALID_OUTPUT` 포함 |
| 실패 결과 | `FAILED` · `result=null` · `AI_OUTPUT_INVALID` · 요구사항/문제 미저장 |
| 재시도 | 새 ID · `PENDING` · `kind` 유지 · `retryOfAnalysisId`. 동일 실패 ID 재요청 시 기존 작업 반환 |
| 재시도 결과 | Mock은 **한 번 실패 후 재시도는 성공** (데모 막힘 방지) |
| 그 외 | FAILED가 아니면 `409 ANALYSIS_NOT_RETRYABLE` |

`AnalysisFailureCode`는 HTTP `ApiErrorCode`와 분리한다.

**B 구현:** 반영됨. C의 ANSWER/REVISION Mock도 같은 표시·패턴을 쓰면 된다.

---

## 신규 12. breadcrumb의 프로젝트 항목

문서·요구사항 route에는 `projectId`가 없다. 문서 응답의 `projectId`로 상위 프로젝트를 조회해 breadcrumb에 넣는다.

- meta: `{ resolve: 'documentProject' }`
- 실패 시 `프로젝트 #1` 표기. 문서 조회 실패 시 해당 crumb만 생략
- 빠른 라우트 전환 시 늦은 응답이 덮어쓰지 않도록 **요청 순번**으로 무시

경로 예: `프로젝트 > (프로젝트명) > 문서명` · 요구사항 화면은 그 뒤에 `요구사항 #`.

**B 구현:** 반영됨. C 추가 작업 없음.

---

## 참고: 프런트엔드 밖 파일 변경

| 파일 | 변경 |
| --- | --- |
| `README.md` | 프런트엔드 실행 모드 표 (`dev` / `dev:mock`) |

`.env.example`에는 `VITE_USE_MOCK`을 적지 않는다. 혼동을 피하기 위해 Mock은 `npm run dev:mock`만 안내한다.

백엔드 코드·DB·API 명세는 변경하지 않았다. `README.md` 소유는 팀 공용이므로 PM 확인이 필요하다.

---

## C에게 남은 FE 작업 (요약)

인계 문서 §6과 동일하다.

1. `workflow.js` 구현 + workflow Mock (Spec 5.13~5.16)
2. `RequirementWorkflowView.vue` (P1)
3. Preview API + `PreviewView.vue` + 진입 링크 (P2)
4. 본 문서 확인 항목 회신

---

## 확인 요청 항목 (C 회신용)

구현은 B가 반영했다. C는 내용에 동의하면 체크한다.

- [x] 4. 기본 실행은 실제 백엔드, Mock은 `npm run dev:mock`
- [x] 5. Mock은 서버 대역이며 API 분기는 `src/api/*`에서만
- [ ] 5-1. 질문 미생성 → 상태는 `AMBIGUOUS` — **아래 C-1 참고, 재논의 요청**
- [x] 3-1. contentVersion 예외는 Mock store에 한정
- [x] 6. 최신 분석 기준 복원 · 완료/실패 시 버튼 정책
- [x] 7. poller 콜백 async 허용 및 오류 전달
- [x] 8. 프로젝트 생성 화면은 B 담당
- [x] 9. Ambiguity 상세는 C workflow, B는 건수까지
- [x] 10. PDF 업로드 · breadcrumb 이름 · 409 복구
- [x] 11. Mock 실패는 `INVALID_OUTPUT` · 재시도는 성공
- [x] 12. breadcrumb `documentProject` 항목
- [x] (전체) B Must 완료로 보고 C는 workflow·Preview만 진행

---

## C 회신 (최은주)

> **회신일:** 2026-09-03
> **브랜치:** `feat/fe-c`
> **범위:** P1 workflow (Spec 5.13~5.16) · P2 Preview (Spec 5.17~5.18) 구현 완료

12개 중 11개 동의한다. 5-1만 재논의를 요청한다.

### C-1. 5-1 재논의 요청 — 새 문서를 분석하면 답할 질문이 없다

**동의하는 부분.** 질문(Clarification)이 workflow 범위이고, 질문이 없는 동안
요구사항 상태가 `CLARIFYING`이 아니라 `AMBIGUOUS`라는 판단은 Spec 4.1과 맞다.
`CLARIFYING`은 「확인 중」이므로 물어본 것이 있어야 성립한다.

**문제.** 그 결과로 **새로 등록·분석한 문서의 요구사항은 workflow 화면에서 할 일이
없다.** 문제(Issue)는 보이는데 답할 질문이 하나도 없어서, 화면이 열려도 답변 입력이
불가능하다. Mock으로 workflow를 처음부터 시연하려면 seed 문서 101로만 가능하다.

`mock-scenarios.md` 2절은 최초 분석의 저장 기대값을 이렇게 정하고 있다.

- Clarification 601·602: WAITING, roundNo 1
- Analysis 301: clarificationIds `[601, 602]`
- Requirement 401: `EXTRACTED → AMBIGUOUS → CLARIFYING`

즉 **문서 분석이 질문까지 만드는 것이 시나리오 문서의 기대값**이다.
현재 Mock은 문서 101 seed에만 이 값이 반영돼 있고, 새로 분석한 문서에는 없다.

**seed의 불일치도 함께 있었다.** `seedAnalysisCompleted.result.clarificationIds`는
이미 `[601, 602]`를 가리키는데 정작 질문 레코드가 store에 없었다. 요구사항 401이
`CLARIFYING`인데 답할 질문이 하나도 없는 상태였다.
질문이 workflow 범위라 비어 있던 것이므로, C가 `fixtures.js`에 601·602를 채웠다
(mock-scenarios 1·2절 문구 그대로). 이 부분은 B 결정과 충돌하지 않는다.

**제안 (택1).**

| 안 | 내용 | 영향 |
| --- | --- | --- |
| **A** | 문서 분석 Mock이 문제마다 roundNo 1 질문을 만들고 `CLARIFYING`으로 전환 | `store.js`의 `extractRequirements` 수정. mock-scenarios 2절과 일치. Mock 한정이며 실BE 계약은 그대로 |
| **B** | 현행 유지 (`AMBIGUOUS`, 질문 없음) | workflow 시연은 seed 문서 101로만 가능. Mock과 시나리오 문서가 어긋난 채로 남음 |

C는 **A**를 제안한다. 다만 `extractRequirements`는 B Must 영역이라
합의 4번에 따라 임의로 바꾸지 않았다. B가 A에 동의하면 C가 PR로 반영하겠다.

### C-2. 참고 — 구현하며 확인한 것들

합의를 바꾸지 않은 사항이며 기록만 남긴다.

1. **`src/api/` 신규 파일** — Preview는 인계서 2절의 「workflow.js와 같이 또는 별도
   파일」 중 별도 파일을 골라 `api/previews.js`로 두었다. 두 Preview는 워크플로우를
   진행시키지 않는 읽기 전용 조회라 성격이 다르다.
2. **Spec 6.2 검증 순서** — 동일 요청 판정을 버전·상태 검증보다 먼저 한다.
   버전이 이미 진행됐다는 이유만으로 정상 재전송을 실패시키지 않는다.
3. **Spec 2.1 공백 집합** — 답변·거절 사유 정규화에 `\s`를 쓰지 않는다.
   JS의 `\s`는 U+FEFF를 포함하고 U+0085를 빼서 서버와 「같은 답변」 판정이 갈린다.
   명세가 고정한 집합을 그대로 문자 클래스로 적었다.
4. **APPROVE 요청 본문** — `rejectionReason`을 아예 싣지 않는다.
   Spec 2절이 미정의 필드를 400으로 규정한다.
5. **`isContentVersionConflict`** — 인계서 6절 재사용 항목이라 반영했다.
   409 전체는 workflow 재조회로 복구하고, 그중 버전 충돌만 별도 안내를 띄운다.
   다른 409는 「지금은 안 된다」지만 버전 충돌은 「보던 화면이 낡았다」라서
   사용자가 할 일이 다르다.
6. **`useAnalysisPoller`의 `onComplete`에서 `clearError()`를 부르지 않았다** —
   작업이 끝난 것과 사용자 요청이 튕긴 것은 별개인데, 방금 뜬 409 메시지까지
   지워졌다. C 화면에서만 뺐고 공통 composable은 건드리지 않았다.
7. **미지원 답변** — `mock-scenarios.md` 서문의 「지원하지 않는 입력을 임의 성공
   처리하지 않는다」에 따라, 표에 없는 답변은 충분한 답변으로 처리하지 않고
   다음 회차를 열며 문제는 OPEN으로 둔다.

### C-3. 검증

- `frontend/scripts/verify-workflow-mock.mjs` — 단언 123개 통과
  (mock-scenarios 2~8절, Spec 5.13~5.18·6.4·8절)
- `frontend/scripts/verify-analysis-poller.mjs` — 기존 4/4 회귀 통과
- 브라우저 E2E (`npm run dev:mock`): v1 → 답변 → v2(2회차 생성) → v3 →
  v4(수정안 701) → 거절 v5 → 재생성 702 → 승인 확정. Spec 8절 분기표 7행 일치
- 409(`ANALYSIS_IN_PROGRESS`) 발생 시 입력 초안 보존 확인
- `INVALID_OUTPUT` → `AI_OUTPUT_INVALID` 배너 → 재시도 성공 확인
- Preview: 문서 상세 진입 링크 → 고객용 질문 2건 → 확정 후 개발팀용에서
  확정본과 근거 답변 601·603·602 확인
