# ReqBridge FE Step 0 합의 보완

> **대상:** B(이병주) · C(최은주)  
> **기준:** API Spec 0.4.0, `docs/api/mock-scenarios.md`  
> **원문:** FE Step 0 합의 (Breadcrumb · `src/api/` 공용 · contentVersion)  
> **상태:** 제안 — C 확인 후 확정

---

## 왜 보완이 필요한가

PR 리뷰에서 지적된 5건을 고치면서, **원 합의안 3개 조항에 없던 결정**을 몇 가지 내렸다. 모두 B/C가 공유하는 코드에 영향을 주므로 문서로 남긴다. 원 합의안 3개 조항은 **변경하지 않는다.**

| 원 조항 | 상태 |
| --- | --- |
| 1. Breadcrumb = route meta | 변경 없음 |
| 2. `src/api/` 공용, workflow는 C 구현 | 변경 없음 |
| 3. contentVersion FE +1 금지 | 유지 + 아래 3-1 단서 추가 |

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

---

## 신규 5. Mock 데이터의 위치와 소유

| 항목 | 합의(안) |
| --- | --- |
| 위치 | `frontend/src/mocks/` (`fixtures.js`, `store.js`) |
| 역할 | **서버 대역**. 화면 로직이 아니라 API 응답을 대신 만든다 |
| 소유 | B. C가 workflow Mock을 추가할 때는 같은 store에 함수를 더한다 |
| 경계 | Mock 분기는 `src/api/*.js` 안에서만 한다. `.vue`에서 Mock을 직접 import하지 않는다 |
| 고정 ID | `mock-scenarios.md` fixture(문서 101, 요구사항 401, 이슈 501·502, 분석 301)를 유지한다 |

### 5-1. 새 문서 분석 Mock

- 새로 등록한 문서를 분석하면 **원문 문장 단위로 요구사항을 생성**하고, 모호 표현이 있으면 `AmbiguityIssue`를 만든다.
- 분류는 `mock-scenarios.md` 9절 표를 축약한 **키워드 규칙**이다. FE Mock 전용이며, 백엔드 Mock Analyzer의 판정과 같아야 할 의무는 없다.
- 질문(Clarification)은 workflow 범위라 FE Mock에서 만들지 않는다. 따라서 문제가 있는 요구사항의 상태는 `CLARIFYING`이 아니라 **`AMBIGUOUS`** 다 (Spec 4.1 상태 전이표).
- 분석 접수는 Spec 5.9대로 **`PENDING`으로 시작해 잠시 후 `COMPLETED`** 가 된다. 즉시 완료로 처리하지 않으므로 polling 경로가 Mock에서도 그대로 검증된다.

---

## 보완 3-1. contentVersion과 Mock

원 조항(“FE에서 +1 금지, 응답값만 사용”)은 그대로 둔다. 다만 적용 대상을 명확히 한다.

| 코드 | 규칙 |
| --- | --- |
| `.vue`, composable | 응답값만 사용. 직접 계산·증가 금지 |
| `src/mocks/store.js` | **서버 대역이므로 예외.** 서버가 하는 버전 증가를 흉내 낼 수 있다 |

C의 workflow Mock에서 답변·거절로 버전을 올릴 때도 store 안에서만 처리한다.

---

## 신규 6. 분석 상태 표시 규칙 (B·C 공통)

`useAnalysisPoller`와 분석 이력 API를 쓰는 모든 화면에 적용한다.

1. **새로고침 복원**: 진행 중(`PENDING`/`PROCESSING`) 작업이 있으면 그것을 polling한다. 없으면 **id가 가장 큰 작업**을 현재 상태로 삼는다. 상태별로 먼저 찾지 않는다. (재시도 성공 후에도 과거 실패가 표시되는 문제를 막는다)
2. **분석 요청 버튼**: 성공 이력이 있으면 비활성화(`분석 완료`). 실패 상태에서는 새 접수 대신 `POST /analyses/{id}/retries`만 쓴다(`재시도 필요` + 실패 배너). Spec 5.9·6.3의 409 조건을 화면에서 미리 막는다.
3. **실패 배너**: `HTTP 200 + status=FAILED`는 `AnalysisFailureBanner`, HTTP 오류는 `ErrorMessage`로 나눈다. (원 합의 유지)

---

## 신규 7. `useAnalysisPoller` 콜백 계약

| 항목 | 계약 |
| --- | --- |
| `onComplete` / `onFailed` | 동기 또는 **async 모두 허용**. poller가 `await`한다 |
| 콜백 내부 실패 | poller가 잡아 **`onError`로 전달**한다. unhandled rejection을 만들지 않는다 |
| 종료 | 상태가 `COMPLETED`/`FAILED`면 polling을 멈춘다. 화면 이탈 시 정리한다 (Spec 6.1) |

C의 답변·재생성 polling도 같은 composable을 그대로 쓴다.

---

## 참고: 프런트엔드 밖 파일 변경

이번 수정은 `frontend/` 안에서 끝나지 않고 아래 공용 파일을 함께 건드렸다. 내용은 FE 실행 방법 설명이지만 파일 소유는 팀 공용이므로 PM 확인이 필요하다.

| 파일 | 변경 |
| --- | --- |
| `README.md` | 프런트엔드 실행 모드 표 추가 |
| `.env.example` | `VITE_USE_MOCK` 주석 추가 |

백엔드 코드·DB·API 명세는 변경하지 않았다.

---

## 확인 요청 항목

- [ ] 4. 기본 실행은 실제 백엔드, Mock은 `npm run dev:mock`
- [ ] 5. Mock은 서버 대역이며 `src/api/*`에서만 분기
- [ ] 5-1. 질문 미생성 → 상태는 `AMBIGUOUS`
- [ ] 3-1. contentVersion 예외는 Mock store에 한정
- [ ] 6. 최신 분석 기준 복원 · 완료/실패 시 버튼 정책
- [ ] 7. poller 콜백 async 허용 및 오류 전달
