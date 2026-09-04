# README.md 피드백

## 1. README와 실제 구현 내용 일치 여부 확인 필요

현재 README와 실제 백엔드 구현, API 명세 간 일부 불일치가 있습니다.

README에는 다음과 같이 정리되어 있습니다.

- 최종 명세 업무 REST API: 20개
- P1 업무 API 18개
- P2 Preview API 2개
- `POST /api/requirements/{requirementId}/confirm` 구현 완료
- API 계약 버전: v0.5.0

하지만 현재 프로젝트 기준으로 확인하면 실제 Controller의 Endpoint 수는 다음과 같습니다.

| 구분 | 실제 코드 |
|---|---:|
| 업무 API | 19개 |
| Health API | 1개 |
| 전체 | 20개 |

현재 `RequirementController`에는 직접 승인 API인 아래 Endpoint가 존재하지 않습니다.

```http
POST /api/requirements/{requirementId}/confirm
```

또한 `docs/api/ReqBridge_API_Specification.md`에서는 다음과 같이 작성되어 있습니다.

- API 계약 버전: v0.4.0
- P1 업무 API 17개
- P2 Preview API 2개

따라서 다음 세 항목의 내용을 서로 일치시키는 작업이 필요합니다.

1. `README.md`
2. `docs/api/ReqBridge_API_Specification.md`
3. 실제 Spring Controller 구현

현재 상태를 그대로 유지한다면 README의 API 개수 및 버전 정보를 실제 구현 기준으로 수정해야 합니다.

반대로 직접 승인 API를 추가할 계획이라면 실제 Backend 구현과 API 명세를 README에 작성된 내용에 맞춰 갱신하는 것이 적절합니다.

---

## 2. README 전체 분량 조정 검토

현재 README는 다음과 같은 흐름으로 구성되어 있어 프로젝트를 처음 보는 사람이 전체 구조를 이해하기에는 좋습니다.

- Why
- Core Experience
- Workflow
- API 계약
- AI-Ready 구조
- Tech Stack
- Scope
- Team
- Repository
- Getting Started
- Verification
- Documentation

다만 `Getting Started` 부분은 실행 가이드를 넘어 상세 운영 문서 수준까지 포함되어 있어 README가 다소 길어질 수 있습니다.

예를 들어 현재 README에는 다음 내용까지 포함되어 있습니다.

- Supabase 프로젝트 생성
- Migration 수동 적용
- Storage 설정
- Session Pooler 설정
- OS별 환경변수 설정
- Backend 실행
- Frontend 실행
- Docker PostgreSQL 실행
- Troubleshooting

프로젝트 소개용 README로 유지하려면 다음 정도의 정보만 README에 남기고 상세 설정은 별도 문서로 분리하는 방법을 고려할 수 있습니다.

### README에 유지 권장

- 서비스 소개
- Before / After
- 핵심 기능
- Workflow
- AI-Ready 구조
- Tech Stack
- Scope
- Team
- 간단한 실행 방법

예시:

```bash
cd backend
./gradlew bootRun

cd frontend
npm ci
npm run dev
```

이후 상세 설정은 다음과 같이 별도 문서로 연결할 수 있습니다.

```text
상세 환경 설정은 docs/SETUP.md 참고
```

단, 교육 프로젝트에서 평가자가 직접 Clone 후 실행하는 것이 중요한 경우에는 현재처럼 상세한 실행 가이드를 유지하는 것도 충분히 타당합니다.

---

## 3. Core Experience에서 사용자 기능과 내부 구현 설명 분리 권장

현재 `Core Experience` 일부 항목에는 사용자 관점의 기능 설명과 내부 구현 설명이 함께 섞여 있습니다.

예를 들어 Analysis 단계에서 다음과 같은 구현 상세가 함께 설명됩니다.

- 문서 저장 커밋 이후 비동기 분석 작업 접수
- Analysis 상태 Polling
- 복구용 API

이 내용들은 기술적으로는 중요하지만 `Core Experience`에서는 사용자 경험 중심으로 표현하는 것이 더 자연스럽습니다.

예를 들어 다음처럼 단순화할 수 있습니다.

> **등록 직후 자동 분석**  
> 문서를 등록하면 분석이 자동으로 시작되며, 사용자는 화면에서 분석 진행 상태를 확인할 수 있습니다.

이후 다음과 같은 기술적 세부 내용은 Architecture 또는 API 관련 섹션에서 설명하는 구성이 더 명확합니다.

- Commit 이후 작업 접수
- Polling
- 복구 API
- 비동기 분석 처리

---

## 4. AI 관련 표현은 현재 구조 유지 권장

README에서는 프로젝트를 단순히 `AI 기반 서비스`라고 표현하기보다 `AI-Ready 서비스`라고 구분하고 있으며, 현재 구현 범위도 명확하게 설명하고 있습니다.

현재 구조는 다음과 같이 실제 구현과 향후 확장 범위를 구분합니다.

```text
WorkflowAnalyzer Contract
        ↓
Mock Adapter · Current
        ↓
LLM Adapter · Future
```

또한 현재 버전이 `MockWorkflowAnalyzer` 기반이며 실제 LLM Adapter는 향후 확장 항목이라는 점을 명확히 설명하고 있어, 실제 구현 범위를 과장하지 않으면서 AI 확장 구조를 전달할 수 있습니다.

따라서 이 부분은 현재 구조를 유지하는 것을 권장합니다.

---

## 5. 상단 숫자 지표의 의미 명확화

README 상단에는 다음과 같은 숫자 지표가 강조되어 있습니다.

- 20 — 최종 명세 업무 REST API
- 7 — Ambiguity Types
- 5 — Requirement States
- 17 / 17 — Core E2E Flow

`7 Ambiguity Types`와 `5 Requirement States`는 의미를 바로 이해할 수 있지만, `17 / 17 Core E2E Flow`는 무엇을 의미하는지 다소 불명확합니다.

예를 들어 다음 중 어떤 의미인지 명시하는 것이 좋습니다.

- E2E 테스트 시나리오 17개 통과
- 핵심 기능 17개 구현 완료
- E2E 검증 항목 17개 통과

만약 E2E 시나리오 17개가 모두 통과했다는 의미라면 다음처럼 표현하는 것이 더 명확합니다.

```text
17 / 17
E2E Scenarios Passed
```

또한 API 개수 지표는 앞서 언급한 실제 Backend 구현 및 API 명세와 먼저 일치시켜야 합니다.

---

## 6. Scope 표현 개선 권장

현재 Scope 영역의 다음 표현은 의미가 다소 불명확할 수 있습니다.

> 실제 Backend / Frontend Mock 모드

실제 Backend와 Frontend Mock의 관계를 조금 더 명확하게 표현하는 것을 권장합니다.

예시:

> Backend 연동 모드 / Frontend 독립 Mock 모드

또는

> 실제 Backend 연동 및 Frontend Mock 실행 지원

이렇게 수정하면 두 실행 방식을 구분해서 제공한다는 의미가 더 명확하게 전달됩니다.

---

## 7. Team 영역은 현재 방식 유지 권장

Team 영역에서는 단순히 역할명을 작성하는 것이 아니라 실제 담당 범위를 함께 설명하고 있습니다.

예를 들어 다음과 같이 구체적인 작업 범위를 보여주는 방식입니다.

> Frontend — Vue 기반 구조, Router·API Client, 공통 컴포넌트, 문서·분석 화면

팀 프로젝트에서는 프로젝트 전체 기능과 별개로 각 구성원이 어떤 범위를 담당했는지 확인하는 것이 중요하므로, 현재와 같이 실제 작업 범위를 구체적으로 작성하는 방식을 유지하는 것이 좋습니다.

---

## 우선 수정 권장 항목

현재 README에서 우선적으로 확인할 부분은 다음과 같습니다.

1. README의 API 개수와 실제 Controller Endpoint 개수 일치
2. README의 API 계약 버전과 `ReqBridge_API_Specification.md` 버전 일치
3. `POST /api/requirements/{requirementId}/confirm`의 실제 구현 여부 확인
4. `17 / 17 Core E2E Flow`의 의미 명확화
5. `실제 Backend / Frontend Mock 모드` 표현 개선
6. 필요 시 상세 실행 가이드를 별도 `docs/SETUP.md`로 분리
7. Core Experience에서는 사용자 경험 중심으로 설명하고 기술 구현 상세는 Architecture/API 영역으로 이동
