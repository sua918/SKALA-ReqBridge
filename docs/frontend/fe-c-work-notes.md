# ReqBridge FE C 작업 노트 (workflow · Preview)

> **작성:** C (최은주)
> **브랜치:** `feat/fe-c` → `main`
> **기준:** API Spec 0.4.0, `docs/api/mock-scenarios.md`, `fe-common-handoff-B-to-C.md`
> **상태:** **C Must P1·P2 완료** · 확인 항목 회신은 `fe-step0-합의-보완.md`

---

## 0. 한 줄 요약

| 구분 | 명세 | 상태 |
| --- | --- | --- |
| **C P1** | Spec 5.13~5.16 요구사항 workflow | **완료** |
| **C P2** | Spec 5.17~5.18 Preview | **완료** |
| **확인 항목** | `fe-step0-합의-보완.md` 12개 | **회신 완료** (11 동의 · 5-1 재논의) |

B가 남긴 stub과 placeholder를 모두 실제 구현으로 바꿨다. FE에 미구현 항목은 없다.

---

## 1. 무엇이 들어갔나

| 파일 | 상태 | 내용 |
| --- | --- | --- |
| `src/api/workflow.js` | stub → 구현 | `getWorkflow` · `submitAnswer` · `recreateRevision` · `reviewRevision` |
| `src/api/previews.js` | 신규 | `getCustomerPreview` · `getDeveloperPreview` |
| `src/mocks/fixtures.js` | 추가 | 질문 601·602, 재판정 표, 수정안 본문 |
| `src/mocks/store.js` | 390 → 1007줄 | workflow·Preview Mock 함수 |
| `src/views/RequirementWorkflowView.vue` | placeholder → 구현 | 문제·질문·답변·수정안 검토 |
| `src/views/PreviewView.vue` | placeholder → 구현 | 고객 질문서 · 개발팀용 두 탭 |
| `src/views/DocumentDetailView.vue` | 링크 추가 | Preview 진입 (인계서 4.7) |
| `scripts/verify-workflow-mock.mjs` | 신규 | 단언 123개 |

### 1.1 인계서 §6 체크리스트 대조

| 항목 | 결과 |
| --- | --- |
| `workflow.js` stub 교체 (5.13~5.16) | 완료. Mock 분기는 이 파일 안에서만 |
| `RequirementWorkflowView.vue` (Spec 8) | 완료 |
| 공통 재사용 7종 | 완료. `isContentVersionConflict` 포함 |
| 409 시 workflow 재조회 + 입력 보존 | 완료 |
| Preview API (5.17~5.18) | 완료 |
| `PreviewView.vue` | 완료 |
| 문서 상세에서 진입 링크 | 완료 |
| 확인 항목 회신 | 완료 |

---

## 2. B 코드를 건드린 곳

합의 4번(「B Must 화면 핵심 로직은 가급적 유지」)에 따라 최소로 제한했다.

| 파일 | 변경 | 이유 |
| --- | --- | --- |
| `mocks/store.js` `settlePendingAnalysis` | 함수 맨 앞에 `kind` 분기 3줄 추가 | ANSWER·REVISION을 workflow settle로 보낸다. DOCUMENT 경로는 그대로다 |
| `mocks/fixtures.js` | 질문 601·602 추가 | seed의 `clarificationIds: [601, 602]`가 가리키는 레코드가 없었다 |
| `views/DocumentDetailView.vue` | 헤더에 Preview 링크 | 인계서 4.7이 C 몫으로 남긴 자리 |

그 외 B 화면·공통 컴포넌트·`style.css`·`vite.config.js`·`package.json`은
한 줄도 바꾸지 않았다.

---

## 3. 명세를 지키려고 따로 신경 쓴 지점

### 3.1 contentVersion — 화면에서 절대 +1 하지 않는다

화면이 들고 있는 값은 언제나 **직전 응답이 준 값**이고, 그대로 되돌려 보낸다.

| 값의 출처 | 쓰는 곳 |
| --- | --- |
| workflow 응답의 `data.contentVersion` | 답변·검토·재생성 요청 |
| 답변 응답의 `data.contentVersion` | 다음 요청 |
| 검토 응답의 `data.requirement.contentVersion` | 다음 요청 |

Mock store는 서버 대역이라 store 안에서만 버전을 올린다 (보완 3-1).

### 3.2 Spec 6.2 검증 순서

동일 요청 판정을 **버전·상태 검증보다 먼저** 한다.
버전이 이미 진행됐다는 이유만으로 정상 재전송을 실패시키지 않기 위함이다.

```
(1) 형식·필수값  →  (2) 대상 조회·소속  →  (3) 동일 요청인가?
                                            └ 예: 저장된 작업/결정 반환, 부수 효과 없음
                                            └ 아니오 ↓
(4) CONFIRMED · 활성 작업 · 버전 · 상태  →  (5) 저장
```

`submitAnswerMock` · `reviewRevisionMock` 모두 이 순서다.

### 3.3 Spec 2.1 공백 집합

답변·거절 사유 정규화(CRLF→LF + 앞뒤 공백 제거)에 `\s`를 쓰지 않았다.
JS의 `\s`는 U+FEFF를 포함하고 U+0085를 빼서 명세가 고정한 집합과 다르다.
서버와 집합이 어긋나면 「같은 답변」 판정이 양쪽에서 갈린다.

```js
// mocks/store.js
const TRIM_CLASS =
  '[\u0009-\u000D\u0020\u0085\u00A0\u1680\u2000-\u200A\u2028\u2029\u202F\u205F\u3000\uFEFF]'
```

길이 검사도 `.length`가 아니라 코드 포인트 기준이다 (`codePointLength`).

### 3.4 요청 본문에 미정의 필드를 싣지 않는다

APPROVE에는 `rejectionReason`을 **아예 넣지 않는다**. `null`이라도 실으면
Spec 2절의 「미정의 필드는 400」에 걸린다.

### 3.5 Preview의 버전 일관성 (Spec 6.4)

- `basis`는 일부가 아니라 **조회 당시 모든 요구사항**의 버전이다
- 근거 답변은 배열 위치가 아니라 `basedOnClarificationIds`의 **ID로 대응**시킨다
- 확정본과 승인 수정안이 어긋나면 서로 다른 버전을 섞지 않고
  409 `PREVIEW_VERSION_CONFLICT`를 낸다

### 3.6 지원하지 않는 입력을 성공으로 처리하지 않는다

`mock-scenarios.md` 서문 규칙이다. 표에 없는 답변은 「충분한 답변」으로 넘기지 않고,
같은 문제의 다음 회차를 열며 문제는 OPEN으로 둔다.

### 3.7 GET은 새로 만들지 않는다

`getWorkflow`·Preview 두 개 모두 저장된 것을 조합해 돌려줄 뿐,
질문·수정안을 생성하지 않는다 (Spec 2절).

---

## 4. 화면에서 내린 판단

### 4.1 작업 중이면 요구사항 전체를 잠근다

Spec 8절: 「다른 질문의 작업이 실행 중이면 해당 요구사항 전체 답변 입력/검토를
잠시 비활성화한다」. 판정 결과가 상태를 바꾸기 때문이다.
`activeAnalysis`가 있거나 polling 중이면 답변창·승인·거절·재생성을 모두 막는다.

### 4.2 409면 재조회하되 입력은 지우지 않는다

`answerDrafts[id] = ''`는 접수 성공 뒤에만 실행된다. 요청이 튕기면 초안이 남는다.
사용자가 쓴 답변을 화면이 지워버리면 처음부터 다시 타이핑해야 한다.

버전 충돌만 별도 안내를 띄운다. 다른 409는 「지금은 안 된다」지만,
버전 충돌은 「보던 화면이 낡았다」라서 사용자가 할 일이 다르다.

### 4.3 polling 완료가 오류 표시를 지우지 않는다

B의 `useAnalysisPoller` 사용 예시는 `onComplete`에서 `clearError()`를 부른다.
C 화면은 답변·검토·재생성이 동시에 오갈 수 있어서, 작업 하나가 끝났다고
방금 튕긴 409 메시지까지 지워지면 사용자가 원인을 놓친다.
C 화면에서만 뺐고 공통 composable은 건드리지 않았다.

### 4.4 고객용 Preview가 비었다고 「끝」이 아니다

Spec 9절 `CustomerPreview`: 「requirements가 비어 있어도 분석 미실행·처리 중·
실패·검토 대기일 수 있으므로 확정으로 해석하지 않는다」.
`summary`로 상황을 갈라 안내 문구를 바꾼다.

| 조건 | 문구 |
| --- | --- |
| `totalRequirements === 0` | 아직 추출된 요구사항이 없습니다 |
| 전부 확정 | 고객에게 더 물을 것이 없습니다 |
| 그 외 | 분석 중이거나 검토를 기다리는 중일 수 있습니다 |

---

## 5. 검증

### 5.1 자동 검증

```bash
cd frontend
node scripts/verify-workflow-mock.mjs    # 123/123
node scripts/verify-analysis-poller.mjs  # 4/4 (B 작성, 회귀)
npm run build
```

`verify-workflow-mock.mjs`는 `@/` 별칭을 상대 경로로 바꾼 사본을 임시 디렉터리에
만들어 Node로 직접 돌린다. 브라우저 없이 상태·버전·ID 규칙을 확인할 수 있다.

| 범위 | 확인 내용 |
| --- | --- |
| mock-scenarios 2절 | seed 상태, 질문 601·602 WAITING, 정렬 |
| 3절 | 불충분한 답변 → v2, 601 ANSWERED, 603 생성(roundNo 2), 501 OPEN 유지 |
| 4절 | 충분한 답변 → v3, 501·603 RESOLVED, 502 OPEN이라 수정안 없음 |
| 5절 | 전부 해결 → v4, 701 PROPOSED, 근거 `[601, 603, 602]`, IN_REVIEW |
| 6절 | 거절 v4→v5, 701 `inputContentVersion` 4 유지, 재생성 → 702(revisionNo 2, 입력 5) |
| 7절 | 승인 → CONFIRMED, v5 유지, `approvedRevisionId`·`confirmedText` |
| 8절 | 동일 답변 재전송(버전 증가 없음), 다른 답변 409, stale 409, 결정 변경 409, 진행 중 409 |
| 서문 | 미지원 답변을 성공 처리하지 않음 |
| 7절 재시도 | `INVALID_OUTPUT` → FAILED, kind·`inputContentVersion` 유지, 중복 재시도 안 만듦 |
| Spec 5.17 | 질문 필터, 요약, basis, 질문 필드 집합 |
| Spec 5.18 | 확정/미확정 분리, 근거 답변 ID 대응, 미확정 이력 전량 |
| Spec 6.4 | 확정본 불일치 시 `PREVIEW_VERSION_CONFLICT` |

### 5.2 브라우저 확인 (`npm run dev:mock`)

Spec 8절 「거절 → 재생성 → 승인 분기」 표 7행을 화면에서 그대로 재현했다.

| 단계 | 결과 |
| --- | --- |
| 601 답변 | v2 · 2회차 질문 생성 |
| 603 답변 | v3 · 501 해결, 502 미해결 |
| 602 답변 | v4 · 수정안 701, 검토 중 |
| 701 거절 | v5 · 확인 중, 701 입력 버전 4 유지 |
| 재생성 | 702(2차, 입력 버전 5) · 검토 중 |
| 702 승인 | **확정** v5 · 확정본 저장 |

그 밖에 확인한 것.

- 409(`ANALYSIS_IN_PROGRESS`) 발생 시 메시지 표시 + **입력 초안 보존**
- `INVALID_OUTPUT` 답변 → `AI_OUTPUT_INVALID` 배너 → 재시도 → 성공
- 버전 충돌 매핑: `CONTENT_VERSION_CONFLICT`만 `isContentVersionConflict` true
- Preview: 문서 상세 링크 → 고객용 질문 2건 → 확정 후 개발팀용 확정본·근거 601·603·602

---

## 6. 실BE 연동 시 확인할 것

Mock으로만 검증했다. 백엔드가 준비되면 아래를 실제 응답으로 다시 본다.

1. `POST /clarifications/{id}/answers`의 202/200 구분 — 화면은 둘 다 `analysis`를
   받아 polling하므로 동작은 같지만, 서버가 기존 작업을 재사용하는지 확인
2. 같은 답변 재전송 시 버전이 오르지 않는지
3. `PREVIEW_VERSION_CONFLICT` 실제 발생 조건
4. `basedOnClarificationIds` 순서 — C는 위치가 아니라 ID로 대응시키므로
   순서가 달라도 화면은 정상이어야 한다

---

## 7. 남은 것

- **5-1 재논의** — 새로 분석한 문서에 질문이 생기지 않는 문제.
  `fe-step0-합의-보완.md`의 C-1 참고. B 동의 시 C가 PR로 반영한다.
- 디자인·스타일은 이번 범위가 아니다. 인계서·명세 어디에도 없다.
