# ReqBridge API 명세서

- 관리 책임자: 신형섭
- API 계약 버전: 0.4.0
- 개정일: 2026-09-03
- 적용 범위: P1 업무 API 17개와 P2 Preview API 2개를 사용하는 백엔드·프론트엔드의 외부 HTTP 계약
- 검토: 프론트엔드 담당자, 한형준
- 기준: ReqBridge 백엔드 협업 계획, ReqBridge 팀 주제 제안서, Mini-project 교육 자료 및 이후 사용자 결정
- 상태: 공식 API 명세이자 팀 공통 구현·검증 기준. 모든 Endpoint의 구현 또는 배포가 완료되었다는 뜻은 아니다.

이 Markdown은 처리 의미·업무 규칙·예시를, `docs/api/openapi.yaml`은 구조·타입·필수/null 조건을 정의하며 두 문서는 일치해야 한다. 현재 저장소에는 OpenAPI YAML이 제공되지 않아 Markdown만 공식 경로로 정리했다. YAML을 확보하면 계약을 추정하지 말고 이 명세와 차이를 검토한 뒤 함께 관리한다.

## 1. 범위와 적용 우선순위

**로그인·회원가입·인증 토큰은 없다.** 적용 범위는 로컬·접근이 제한된 내부 데모이며, 외부 공개는 별도 접근 통제를 마련한 뒤 진행한다. PM이 고객 답변을 대신 입력하고 수정안을 승인한다. `userId`, `reviewerId`, `Authorization`, Supabase Auth는 이번 계약에 넣지 않는다. 승인 이력은 사람의 검토 행위 기록이며 검증된 승인자 신원이나 고객 본인의 승인 증명이 아니다.

구조는 **Vue → Spring Boot API → JPA/JDBC → Supabase 클라우드 PostgreSQL**이다. 프론트가 Supabase DB/API에 직접 접속하지 않는다. DB 연결 정보는 외부 응답에 포함하지 않는다. Supabase 사용은 기존 로컬 DB 필수 실행 전제를 대체하며, API의 업무 계약은 유지한다.

| 구분 | 포함 범위 |
| --- | --- |
| P1 MVP | 프로젝트, TEXT 직접 입력, PDF 파일 업로드, Mock 분석, 요구사항 조회, 불명확성 7종, 질문·답변·재판정·추가 회차, 수정안 승인·거절·재생성 |
| P2 | 고객 질문서·개발팀용 JSON Preview |
| P3 | Acceptance Criteria 생성·저장·조회, 수동 질문·요구사항 편집. 이번 Endpoint에 추가하지 않으며 `acceptanceCriteria`는 빈 배열 |
| 추후 확장 | PDF/Word/CSV 다운로드, 실제 LLM, 단방향 외부 알림, 고객 계정 |
| 파일 입력 | PDF 원본은 Supabase Storage에 저장하고, 추출한 텍스트는 PostgreSQL `document.content`에 저장 |
| 이번 범위 제외 | DOCX, OCR, 이미지 PDF, 여러 파일 동시 업로드, 파일 교체·삭제·다운로드, PostgreSQL binary 저장 |

plan의 구체적인 7.2 절에 따라 P1에는 요구사항 수동 생성·PATCH·삭제를 공개하지 않는다. 포괄적으로 적힌 ‘기본 수정’보다 이 제한을 우선한다. `/api/health`는 기존 실제 계약을 확인한 후 별도로 기재한다. 현재 구현을 확인하지 않은 상태에서 응답을 새로 정의하거나 변경하지 않는다.

### 공식 계약으로 구체화한 구현 기준

아래는 프론트 연동을 위해 구체화한 공식 계약이다. 기존 내부 Port 서명과 상태·분류 enum 체계를 유지하고 DocumentSourceType(TEXT/FILE), ReviewDecision(APPROVE/REJECT)를 명시한다. RequirementStatus는 0.3.0에서 첨부된 개정 DBML 수정안 기준 5값으로 변경했다.

1. `GET /documents/{documentId}/analyses` 추가: 새로고침 시 모든 종류의 진행 작업 및 실패 이력을 복구한다.
2. 외부 ID는 JSON 정수이고 최대 `9,007,199,254,740,991`로 제한한다. DB/Java는 기존 BIGINT/Long을 유지한다. 이 범위를 넘어야 할 때는 외부 ID를 문자열로 바꾸는 계약 변경을 먼저 한다.
3. 쓰기 입력에는 `expectedContentVersion`을 사용해 오래된 화면의 답변·검토를 감지한다. 중복 요청은 아래 우선순위로 판정한다.
4. `AnalysisResult`, 재판정의 `Assessment`, 외부 목록·Preview 필드, 문자열 길이, 정렬을 명시했다. 생성/갱신한 ID와 판정 근거는 분석 결과로 저장하며 조회 중 재생성하지 않는다.
5. `retryOfAnalysisId`를 외부에 노출하고 직접 재시도 관계를 서버에 보존한다. P0 스키마에 없다면 신형섭이 요청하고 한형준이 반영한다. 동일 실패 작업당 직접 재시도는 최대 1개다.
6. 공통 응답 래퍼·HTTP 예외 매핑은 한형준이 구현한다. 전체 명세 변경권은 신형섭에게 있으며, 외부 DTO를 공용 내부 Snapshot과 같은 클래스로 강제하지 않는다.

## 2. 공통 규칙

| 항목 | 계약 |
| --- | --- |
| Base path | `/api` (OpenAPI의 servers에 포함, paths에는 중복하지 않음) |
| 형식 | 요청·응답 `application/json`, UTF-8, camelCase. 단, PDF 업로드 요청은 `multipart/form-data` |
| 성공 | `{ "data": ... }` |
| 목록 | `{ "data": { "items": [] } }`; P1 페이지네이션 없음 |
| 오류 | `{ "error": { "code": "...", "message": "...", "fieldErrors": [] } }` |
| ID·버전 | 양수 JSON 정수. ID는 DB 생성, 버전은 1부터 |
| 시간 | UTC ISO-8601, `2026-09-02T06:00:00Z` |
| 빈 값 | 목록은 `[]`; 아직 없는 단일 객체·본문은 명시적인 `null` |
| 필드 | 응답은 스키마 필드를 모두 반환. 요청은 명시한 필드만 허용하며 미정의 필드는 400 |
| 문자열 | 필수 문자열은 공백만 허용하지 않음. 길이는 서버·프론트 모두 Unicode 코드 포인트 기준으로 계산 |
| 답변·거절 사유 | CRLF→LF 변환과 앞뒤 공백 제거 후 저장·동일성 비교 |
| 이름·설명·제목 | 앞뒤 공백 제거. 문서 원문 content는 입력 그대로 보존 |
| HTTP | 생성 201, 새 비동기 접수/기존 진행 작업 202, 조회·검토·기존 종료 작업 200 |
| Location | 201은 생성 리소스 URL, 202는 `/api/analyses/{id}` |
| GET | 저장 데이터 조회만 수행하며 새 분석·질문 생성 없음 |

프로젝트 생성·문서 등록은 멱등성을 제공하지 않는다. 네트워크 응답이 유실되면 목록을 확인한 뒤 재등록한다. 분석·답변·승인 중복 처리는 6절의 규칙을 따른다.

### 2.1 문자열 정규화·검증

길이와 공백-only 검사는 JSON 원문 및 정규화 결과에 적용한다. 길이는 Unicode 코드 포인트 기준이다. 앞뒤 공백은 U+0009~U+000D, U+0020, U+0085, U+00A0, U+1680, U+2000~U+200A, U+2028, U+2029, U+202F, U+205F, U+3000, U+FEFF로 고정한다. 답변·거절 사유는 CRLF→LF 후 위 공백을 제거한다. 이름·설명·제목은 위 공백만 제거한다. content는 보존하되 공백-only는 거절한다. 선택적 description은 생략/null이면 null, 문자열이면 1~2000자이며 공백-only는 400이다.

Java의 일반 length/@Size와 JavaScript length만으로 코드 포인트 제한을 구현하지 않는다. 양쪽에서 같은 공백 집합과 코드 포인트 검증을 사용한다. `sequenceNo`, `roundNo`, `revisionNo`는 1~2,147,483,647이고, ID·업무 버전은 1~9,007,199,254,740,991이다.

## 3. Endpoint 전체 목록

아래 경로는 모두 `/api`를 포함한다. P2는 P1 이후 구현하며 계획에 들어 있다는 이유로 현재 호출 가능한 것으로 표시하지 않는다.

| 단계 | Method | Endpoint | 기능 | 구현 담당 |
| --- | --- | --- | --- | --- |
| P1 | POST | `/api/projects` | 프로젝트 생성 | 한형준 |
| P1 | GET | `/api/projects` | 프로젝트 목록 | 한형준 |
| P1 | GET | `/api/projects/{projectId}` | 프로젝트 상세 | 한형준 |
| P1 | POST | `/api/projects/{projectId}/documents` | 텍스트 문서 등록 | 한형준 |
| P1 | POST | `/api/projects/{projectId}/documents/upload` | PDF 문서 등록 | 한형준 |
| P1 | GET | `/api/projects/{projectId}/documents` | 문서 목록 | 한형준 |
| P1 | GET | `/api/documents/{documentId}` | 문서 원문 조회 | 한형준 |
| P1 | GET | `/api/documents/{documentId}/requirements` | 요구사항 목록 | 한형준 |
| P1 | GET | `/api/requirements/{requirementId}` | 요구사항 기본 상세·확정본 | 한형준 |
| P1 | POST | `/api/documents/{documentId}/analyses` | 최초 문서 분석 접수 | 신형섭 |
| P1 | GET | `/api/documents/{documentId}/analyses` | 문서 분석 이력 조회 | 신형섭 |
| P1 | GET | `/api/analyses/{analysisId}` | 분석 상태·결과·오류 조회 | 신형섭 |
| P1 | POST | `/api/analyses/{analysisId}/retries` | 실패 작업 재시도 | 신형섭 |
| P1 | GET | `/api/requirements/{requirementId}/workflow` | 문제·질문·수정안 전체 조회 | 신형섭 |
| P1 | POST | `/api/clarifications/{clarificationId}/answers` | 답변 저장·재판정 접수 | 신형섭 |
| P1 | POST | `/api/requirements/{requirementId}/revisions` | 거절 이후 수정안 재생성 접수 | 신형섭 |
| P1 | POST | `/api/revisions/{revisionId}/review` | 수정안 승인·거절 | 신형섭 |
| P2 | GET | `/api/documents/{documentId}/previews/customer` | 고객 질문서 Preview | 한형준 |
| P2 | GET | `/api/documents/{documentId}/previews/developer` | 개발팀용 Preview | 한형준 |

## 4. 공통 enum 계약

| 구분 | 값 |
| --- | --- |
| DocumentSourceType | `TEXT`, `FILE` |
| ReviewDecision | `APPROVE`, `REJECT` |
| AnalysisKind | `DOCUMENT`, `ANSWER`, `REVISION` |
| AnalysisStatus | `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED` |
| RequirementStatus | `EXTRACTED`, `AMBIGUOUS`, `CLARIFYING`, `IN_REVIEW`, `CONFIRMED` |
| IssueStatus | `OPEN`, `RESOLVED` |
| ClarificationStatus | `WAITING`, `ANSWERED`, `RESOLVED` |
| RevisionStatus | `PROPOSED`, `APPROVED`, `REJECTED` |
| AmbiguityType | `QUANTITY_MISSING`, `PERFORMANCE_MISSING`, `CONDITION_MISSING`, `ACTOR_MISSING`, `SUCCESS_CRITERIA_MISSING`, `TERM_AMBIGUOUS`, `EXCEPTION_MISSING` |

`EXTRACTED`는 최초 분석으로 요구사항만 추출된 상태, `AMBIGUOUS`는 불명확성 문제가 열린 상태, `CLARIFYING`은 질문·답변·재판정 진행 또는 거절 후 수정안 재생성 대기 상태다. `IN_REVIEW`는 제안 수정안 검토 대기, `CONFIRMED`는 담당자 승인 완료다. 작업 `COMPLETED`가 요구사항 `CONFIRMED`를 의미하지 않는다. 처음부터 명확한 요구사항도 수정안을 제안한 뒤 `IN_REVIEW`로 보낸다.

불충분한 답변의 질문은 `ANSWERED`로 남고 같은 문제에 다음 회차 `WAITING` 질문이 생긴다. 충분한 답변의 질문과 문제만 `RESOLVED`가 된다. 모든 문제 해결 시 수정안을 만들지만 자동 승인하지 않는다.


### 4.1 계층별 적용과 상태 전이

- DB 저장 문자열, Java enum, Mock 분류값, API JSON, 프론트 타입은 위 값을 정확히 일치시킨다. 대소문자 보정·숫자 ordinal·임의 fallback을 허용하지 않는다. 클라이언트 enum 입력 오류는 400 `VALIDATION_ERROR`; Mock의 알 수 없는 분류값은 작업 FAILED/`AI_OUTPUT_INVALID`다.
- `ReviewDecision`은 요청 전용 명령이다. `APPROVE → APPROVED`, `REJECT → REJECTED`로 변환한다. 이를 저장하기 위한 별도 DB 컬럼은 필요하지 않다.
- `DocumentSourceType.TEXT`는 사용자가 문자열 원문을 직접 등록할 때 사용한다. 기존 `POST /api/projects/{projectId}/documents`는 `TEXT`만 허용하며 생략·null·`FILE`·`text`·숫자·알 수 없는 값은 400이다.
- `DocumentSourceType.FILE`은 PDF 업로드로 생성된 문서에 서버가 지정한다. 업로드 요청에는 `sourceType`을 보내지 않으며 대소문자 자동 보정은 하지 않는다.
- 재시도는 원본 `AnalysisKind`를 유지하고 `retryOfAnalysisId`로 연결한다. `RETRY`나 `SUCCESS`를 추가하지 않는다.
- `ClarificationStatus.ANSWERED`는 답변 저장 상태다. 판정 대기·실패·불충분한 답변도 포함하며, 충분하다고 판정한 답변만 `RESOLVED`다. 이전 불충분한 답변 이력은 ANSWERED를 유지한다.
- DB의 출처 필드(adapter/source)는 상태 enum과 별개이며 외부 응답에 새로 추가하지 않는다.

| 사건 | Requirement 상태 | contentVersion | Revision 상태 |
| --- | --- | --- | --- |
| 최초 추출 | EXTRACTED | 1 | 없음 |
| 확인할 문제 분류 완료 | EXTRACTED → AMBIGUOUS | 유지 | 없음 |
| 질문 발송·답변 대기 | CLARIFYING | 유지 | 없음 |
| 처음부터 명확하거나 모든 문제 해결 후 제안 | IN_REVIEW | 현재 값 유지 | PROPOSED |
| 최초 답변 등록·재판정 접수 | CLARIFYING | +1 | 없음 |
| 최초 APPROVE | IN_REVIEW → CONFIRMED | 유지 | APPROVED |
| 최초 REJECT | IN_REVIEW → CLARIFYING | +1 | REJECTED |
| 동일 검토 재전송 | 현재 상태 유지 | 추가 증가 없음 | 기존 결정 유지 |

모든 문제가 해결됐어도 수정안 거절 후 새 제안을 기다리는 동안 `CLARIFYING`이다. 열린 문제가 남아 다시 확인해야 하는 경우에는 `AMBIGUOUS`를 사용한다.

## 5. Endpoint별 요청·응답

JSON은 설명용 필드 생략 없이 제시했다. 각 API 예시는 해당 API가 호출되는 시점의 독립 스냅샷이다. 이어지는 전체 흐름의 버전·ID 연결은 8절을 따른다. 공통 오류 본문은 7절을 참고한다.


### 5.1. 프로젝트 생성

`POST /api/projects` · 한형준 · P1

로그인 없이 공용 데모 프로젝트를 생성한다. 동일 본문을 다시 POST하면 별도 프로젝트가 생성된다.

요청 본문:

```json
{
  "name": "ReqBridge 데모 프로젝트",
  "description": "고객 요구사항 확인 및 확정"
}
```

성공 응답 `201` · `Project`:

```json
{
  "data": {
    "id": 1,
    "name": "ReqBridge 데모 프로젝트",
    "description": "고객 요구사항 확인 및 확정",
    "createdAt": "2026-09-02T06:00:00Z"
  }
}
```

### 5.2. 프로젝트 목록

`GET /api/projects` · 한형준 · P1

id 내림차순. 페이지네이션 없음.

요청 본문: 없음.

성공 응답 `200` · `ProjectList`:

```json
{
  "data": {
    "items": [
      {
        "id": 1,
        "name": "ReqBridge 데모 프로젝트",
        "description": "고객 요구사항 확인 및 확정",
        "createdAt": "2026-09-02T06:00:00Z"
      }
    ]
  }
}
```

### 5.3. 프로젝트 상세

`GET /api/projects/{projectId}` · 한형준 · P1

프로젝트 기본 정보를 반환한다.

요청 본문: 없음.

성공 응답 `200` · `Project`:

```json
{
  "data": {
    "id": 1,
    "name": "ReqBridge 데모 프로젝트",
    "description": "고객 요구사항 확인 및 확정",
    "createdAt": "2026-09-02T06:00:00Z"
  }
}
```

### 5.4. 텍스트 문서 등록

`POST /api/projects/{projectId}/documents` · 한형준 · P1

문서만 저장한다. 분석은 별도 POST로 요청한다. 기존 `application/json` 계약을 유지하며 sourceType은 TEXT만 허용한다. FILE은 PDF 업로드 전용 Endpoint를 사용한다. 동일 본문 재전송은 새 문서를 생성한다.

요청 본문:

```json
{
  "title": "상품 조회 서비스 요구사항",
  "sourceType": "TEXT",
  "content": "시스템은 많은 사용자의 동시 상품 조회 요청에 빠르게 응답해야 한다. 부하 시험은 10분 동안 수행하며 성공 응답 비율은 99.9% 이상이어야 한다."
}
```

성공 응답 `201` · `Document`:

```json
{
  "data": {
    "id": 101,
    "projectId": 1,
    "title": "상품 조회 서비스 요구사항",
    "sourceType": "TEXT",
    "content": "시스템은 많은 사용자의 동시 상품 조회 요청에 빠르게 응답해야 한다. 부하 시험은 10분 동안 수행하며 성공 응답 비율은 99.9% 이상이어야 한다.",
    "createdAt": "2026-09-02T06:00:00Z"
  }
}
```

### 5.4.1. PDF 문서 등록

`POST /api/projects/{projectId}/documents/upload` · 한형준 · P1

`multipart/form-data` 요청으로 PDF 한 개를 등록한다. `title`과 `file`은 필수이며 `sourceType`은 보내지 않는다. `title`은 기존 문서 제목과 같은 정규화 규칙을 적용하고 최대 200 코드 포인트다. `file`은 비어 있지 않은 PDF만 허용하며 최대 크기는 10MB다.

서버는 다음 순서로 처리한다.

1. 프로젝트 존재 확인
2. 파일 형식과 크기 검증
3. PDF 텍스트 추출
4. 추출 텍스트의 공백-only 여부와 기존 `Document.content` 제한 검증
5. 원본 PDF를 Supabase Storage에 저장
6. PostgreSQL에 `sourceType=FILE`, `content=추출된 텍스트`인 Document 저장
7. 기존 `Document` 응답 반환

성공 응답 `201` · `Document`

`Location: /api/documents/{documentId}`

```json
{
  "data": {
    "id": 102,
    "projectId": 1,
    "title": "고객 요구사항",
    "sourceType": "FILE",
    "content": "PDF에서 추출된 요구사항 원문...",
    "createdAt": "2026-09-03T00:00:00Z"
  }
}
```

`storagePath`, `bucketName`, `storageObjectKey`, Supabase URL, service role key와 credential은 외부 응답에 포함하지 않는다. PDF 원본 binary는 PostgreSQL에 저장하지 않는다.

### 5.5. 문서 목록

`GET /api/projects/{projectId}/documents` · 한형준 · P1

id 내림차순. 원문 content는 상세에서 조회한다.

요청 본문: 없음.

성공 응답 `200` · `DocumentSummaryList`:

```json
{
  "data": {
    "items": [
      {
        "id": 101,
        "projectId": 1,
        "title": "상품 조회 서비스 요구사항",
        "sourceType": "TEXT",
        "createdAt": "2026-09-02T06:00:00Z"
      }
    ]
  }
}
```

### 5.6. 문서 원문 조회

`GET /api/documents/{documentId}` · 한형준 · P1

저장한 원문을 반환한다. GET은 분석을 시작하지 않는다.

요청 본문: 없음.

성공 응답 `200` · `Document`:

```json
{
  "data": {
    "id": 101,
    "projectId": 1,
    "title": "상품 조회 서비스 요구사항",
    "sourceType": "TEXT",
    "content": "시스템은 많은 사용자의 동시 상품 조회 요청에 빠르게 응답해야 한다. 부하 시험은 10분 동안 수행하며 성공 응답 비율은 99.9% 이상이어야 한다.",
    "createdAt": "2026-09-02T06:00:00Z"
  }
}
```

### 5.7. 요구사항 목록

`GET /api/documents/{documentId}/requirements` · 한형준 · P1

sequenceNo 오름차순. 문서 분석 미완료라면 items=[]. 분석 실패/진행 여부는 분석 이력으로 확인한다.

요청 본문: 없음.

성공 응답 `200` · `RequirementList`:

```json
{
  "data": {
    "items": [
      {
        "id": 401,
        "documentId": 101,
        "analysisId": 301,
        "sequenceNo": 1,
        "originalText": "시스템은 많은 사용자의 동시 상품 조회 요청에 빠르게 응답해야 한다. 부하 시험은 10분 동안 수행하며 성공 응답 비율은 99.9% 이상이어야 한다.",
        "status": "CLARIFYING",
        "contentVersion": 1,
        "approvedRevisionId": null,
        "confirmedText": null
      }
    ]
  }
}
```

### 5.8. 요구사항 기본 상세·확정본

`GET /api/requirements/{requirementId}` · 한형준 · P1

확정 전 approvedRevisionId/confirmedText는 null. 상세 Workflow는 별도 조회한다.

요청 본문: 없음.

성공 응답 `200` · `Requirement`:

```json
{
  "data": {
    "id": 401,
    "documentId": 101,
    "analysisId": 301,
    "sequenceNo": 1,
    "originalText": "시스템은 많은 사용자의 동시 상품 조회 요청에 빠르게 응답해야 한다. 부하 시험은 10분 동안 수행하며 성공 응답 비율은 99.9% 이상이어야 한다.",
    "status": "CLARIFYING",
    "contentVersion": 1,
    "approvedRevisionId": null,
    "confirmedText": null
  }
}
```

### 5.9. 최초 문서 분석 접수

`POST /api/documents/{documentId}/analyses` · 신형섭 · P1

요청 본문 없음. DB에 작업을 PENDING으로 저장한 뒤 202. 활성 DOCUMENT 작업 또는 성공 이력이 있으면 409. 실패 이후에는 retries를 사용한다.

요청 본문: 없음.

성공 응답 `202` · `Analysis`:

```json
{
  "data": {
    "id": 301,
    "kind": "DOCUMENT",
    "status": "PENDING",
    "documentId": 101,
    "requirementId": null,
    "clarificationId": null,
    "inputContentVersion": null,
    "retryOfAnalysisId": null,
    "createdAt": "2026-09-02T06:00:00Z",
    "startedAt": null,
    "completedAt": null,
    "result": null,
    "error": null
  }
}
```

### 5.10. 문서 분석 이력 조회

`GET /api/documents/{documentId}/analyses` · 신형섭 · P1

이번 계약에 포함된 Endpoint. 문서에 속한 DOCUMENT/ANSWER/REVISION 작업을 id 내림차순으로 조회한다. kind를 생략하면 전체, 지정하면 해당 종류만. 새로고침 시 활성 작업 복구와 실패 재시도에 사용한다.

선택 query: `kind` = `DOCUMENT | ANSWER | REVISION`. 생략 시 전체.

요청 본문: 없음.

성공 응답 `200` · `AnalysisList`:

```json
{
  "data": {
    "items": [
      {
        "id": 301,
        "kind": "DOCUMENT",
        "status": "COMPLETED",
        "documentId": 101,
        "requirementId": null,
        "clarificationId": null,
        "inputContentVersion": null,
        "retryOfAnalysisId": null,
        "createdAt": "2026-09-02T06:00:00Z",
        "startedAt": "2026-09-02T06:00:00Z",
        "completedAt": "2026-09-02T06:00:00Z",
        "result": {
          "requirementIds": [
            401
          ],
          "issueIds": [
            501,
            502
          ],
          "clarificationIds": [
            601,
            602
          ],
          "revisionIds": [],
          "assessment": null
        },
        "error": null
      }
    ]
  }
}
```

### 5.11. 분석 상태·결과·오류 조회

`GET /api/analyses/{analysisId}` · 신형섭 · P1

PENDING/PROCESSING/COMPLETED/FAILED 모두 HTTP 200. 실패 자체는 data.error로 표현한다. 작업 ID 없음만 404. 완료 결과는 저장값을 반환하며 조회할 때 AI를 호출하지 않는다.

요청 본문: 없음.

성공 응답 `200` · `Analysis`:

```json
{
  "data": {
    "id": 301,
    "kind": "DOCUMENT",
    "status": "COMPLETED",
    "documentId": 101,
    "requirementId": null,
    "clarificationId": null,
    "inputContentVersion": null,
    "retryOfAnalysisId": null,
    "createdAt": "2026-09-02T06:00:00Z",
    "startedAt": "2026-09-02T06:00:00Z",
    "completedAt": "2026-09-02T06:00:00Z",
    "result": {
      "requirementIds": [
        401
      ],
      "issueIds": [
        501,
        502
      ],
      "clarificationIds": [
        601,
        602
      ],
      "revisionIds": [],
      "assessment": null
    },
    "error": null
  }
}
```

### 5.12. 실패 작업 재시도

`POST /api/analyses/{analysisId}/retries` · 신형섭 · P1

요청 본문 없음. FAILED 작업의 동일 입력으로 새 작업을 만든다. 기존 직접 재시도가 있으면 그 작업 반환(진행 중 202, 종료 200); 재시도가 다시 실패하면 반환된 새 ID의 retries를 호출한다. 입력 버전이 달라졌거나 새 실행이 불가능한 상태라면 409.

요청 본문: 없음.

성공 응답 `202` · `Analysis`:

```json
{
  "data": {
    "id": 306,
    "kind": "ANSWER",
    "status": "PENDING",
    "documentId": 101,
    "requirementId": 401,
    "clarificationId": 601,
    "inputContentVersion": 2,
    "retryOfAnalysisId": 302,
    "createdAt": "2026-09-02T06:00:00Z",
    "startedAt": null,
    "completedAt": null,
    "result": null,
    "error": null
  }
}
```

동일 요청의 기존 종료 결과 `200` 예시:

```json
{
  "data": {
    "id": 306,
    "kind": "ANSWER",
    "status": "FAILED",
    "documentId": 101,
    "requirementId": 401,
    "clarificationId": 601,
    "inputContentVersion": 2,
    "retryOfAnalysisId": 302,
    "createdAt": "2026-09-02T06:00:00Z",
    "startedAt": "2026-09-02T06:00:00Z",
    "completedAt": "2026-09-02T06:00:00Z",
    "result": null,
    "error": {
      "code": "AI_OUTPUT_INVALID",
      "message": "분석 결과 형식이 올바르지 않습니다."
    }
  }
}
```

### 5.13. 문제·질문·수정안 전체 조회

`GET /api/requirements/{requirementId}/workflow` · 신형섭 · P1

현재 상태·업무 버전과 활성 작업, 모든 문제·질문 회차·수정안을 함께 반환한다. issues=id 오름차순, clarifications=issueId/roundNo 오름차순, revisions=revisionNo 내림차순. 실패 작업 확인은 분석 이력 API를 사용한다.

요청 본문: 없음.

성공 응답 `200` · `Workflow`:

```json
{
  "data": {
    "requirementId": 401,
    "status": "CLARIFYING",
    "contentVersion": 1,
    "activeAnalysis": null,
    "issues": [
      {
        "id": 501,
        "requirementId": 401,
        "type": "QUANTITY_MISSING",
        "evidence": "많은 사용자의 정량 기준이 없다.",
        "status": "OPEN"
      },
      {
        "id": 502,
        "requirementId": 401,
        "type": "PERFORMANCE_MISSING",
        "evidence": "빠르게의 측정 가능한 응답 시간 기준이 없다.",
        "status": "OPEN"
      }
    ],
    "clarifications": [
      {
        "id": 601,
        "requirementId": 401,
        "issueId": 501,
        "roundNo": 1,
        "questionText": "부하 시험의 최대 동시 사용자는 몇 명인가요?",
        "answerText": null,
        "status": "WAITING"
      },
      {
        "id": 602,
        "requirementId": 401,
        "issueId": 502,
        "roundNo": 1,
        "questionText": "부하 시험에서 목표 응답 시간과 측정 지표는 무엇인가요?",
        "answerText": null,
        "status": "WAITING"
      }
    ],
    "revisions": []
  }
}
```

### 5.14. 답변 저장·재판정 접수

`POST /api/clarifications/{clarificationId}/answers` · 신형섭 · P1

WAITING 질문에 답변을 저장하고 contentVersion을 1 증가시킨 뒤 ANSWER 작업을 같은 트랜잭션에 등록한다. 답변은 PM 대리 입력이며 입력자 신원 검증은 없다. 동일 답변 재제출은 원래 Analysis를 반환한다(진행 중 202, 종료 200). 원 작업 실패 시 retries로 재처리한다.

요청 본문:

```json
{
  "answerText": "많이 접속할 것 같습니다.",
  "expectedContentVersion": 1
}
```

성공 응답 `202` · `AnswerReceipt`:

```json
{
  "data": {
    "clarificationId": 601,
    "requirementId": 401,
    "contentVersion": 2,
    "analysis": {
      "id": 302,
      "kind": "ANSWER",
      "status": "PENDING",
      "documentId": 101,
      "requirementId": 401,
      "clarificationId": 601,
      "inputContentVersion": 2,
      "retryOfAnalysisId": null,
      "createdAt": "2026-09-02T06:00:00Z",
      "startedAt": null,
      "completedAt": null,
      "result": null,
      "error": null
    }
  }
}
```

동일 요청의 기존 종료 결과 `200` 예시:

```json
{
  "data": {
    "clarificationId": 601,
    "requirementId": 401,
    "contentVersion": 2,
    "analysis": {
      "id": 302,
      "kind": "ANSWER",
      "status": "COMPLETED",
      "documentId": 101,
      "requirementId": 401,
      "clarificationId": 601,
      "inputContentVersion": 2,
      "retryOfAnalysisId": null,
      "createdAt": "2026-09-02T06:00:00Z",
      "startedAt": "2026-09-02T06:00:00Z",
      "completedAt": "2026-09-02T06:00:00Z",
      "result": {
        "requirementIds": [
          401
        ],
        "issueIds": [
          501
        ],
        "clarificationIds": [
          603
        ],
        "revisionIds": [],
        "assessment": {
          "issueId": 501,
          "sufficient": false,
          "reason": "최대 동시 사용자 수가 숫자로 제시되지 않았습니다.",
          "nextClarificationId": 603
        }
      },
      "error": null
    }
  }
}
```

### 5.15. 거절 이후 수정안 재생성 접수

`POST /api/requirements/{requirementId}/revisions` · 신형섭 · P1

CLARIFYING, 모든 문제 RESOLVED, 활성 작업/PROPOSED 수정안 없음, 거절 이력 있음일 때 접수한다. 거절 시 증가한 현재 contentVersion(예: 5)과 거절 사유·답변 이력을 서버가 조회해 반영한다. 새 Analysis와 Revision의 inputContentVersion은 5다. 같은 요청 중 활성 작업이 있으면 409. 성공하면 새 revisionNo를 만들고 IN_REVIEW로 전환한다.

요청 본문:

```json
{
  "expectedContentVersion": 5
}
```

성공 응답 `202` · `Analysis`:

```json
{
  "data": {
    "id": 307,
    "kind": "REVISION",
    "status": "PENDING",
    "documentId": 101,
    "requirementId": 401,
    "clarificationId": null,
    "inputContentVersion": 5,
    "retryOfAnalysisId": null,
    "createdAt": "2026-09-02T06:00:00Z",
    "startedAt": null,
    "completedAt": null,
    "result": null,
    "error": null
  }
}
```

### 5.16. 수정안 승인·거절

`POST /api/revisions/{revisionId}/review` · 신형섭 · P1

승인과 확정본 저장은 한 트랜잭션. 미해결(OPEN) 문제/활성 작업/오래된 버전/현재 제안 아님은 409. APPROVE에는 rejectionReason을 보내지 않는다. REJECT에는 사유 필수. 최초 거절은 사유 저장·CLARIFYING 전환·contentVersion 1 증가를 같은 트랜잭션에서 수행한다. APPROVE는 버전을 유지한다. 같은 결정·동일 사유 재전송은 기존 결정 반환, 결정/사유 변경은 409. 현재 Requirement와 해당 Revision을 응답한다.

요청 본문:

```json
{
  "decision": "APPROVE",
  "expectedContentVersion": 4
}
```

성공 응답 `200` · `ReviewResult`:

```json
{
  "data": {
    "revision": {
      "id": 701,
      "requirementId": 401,
      "revisionNo": 1,
      "text": "시스템은 최대 동시 사용자 3,000명의 상품 조회 부하 시험을 10분간 수행할 때 p95 응답 시간 2초 이하, 성공 응답 비율 99.9% 이상을 만족해야 한다.",
      "status": "APPROVED",
      "inputContentVersion": 4,
      "basedOnClarificationIds": [
        601,
        603,
        602
      ],
      "rejectionReason": null,
      "acceptanceCriteria": []
    },
    "requirement": {
      "id": 401,
      "documentId": 101,
      "analysisId": 301,
      "sequenceNo": 1,
      "originalText": "시스템은 많은 사용자의 동시 상품 조회 요청에 빠르게 응답해야 한다. 부하 시험은 10분 동안 수행하며 성공 응답 비율은 99.9% 이상이어야 한다.",
      "status": "CONFIRMED",
      "contentVersion": 4,
      "approvedRevisionId": 701,
      "confirmedText": "시스템은 최대 동시 사용자 3,000명의 상품 조회 부하 시험을 10분간 수행할 때 p95 응답 시간 2초 이하, 성공 응답 비율 99.9% 이상을 만족해야 한다."
    }
  }
}
```

거절 요청:

```json
{
  "decision": "REJECT",
  "expectedContentVersion": 4,
  "rejectionReason": "동시 사용자 수를 최대치로 명확하게 표현해주세요."
}
```

거절 성공은 동일 ReviewResult 구조로 `revision.status=REJECTED`, `requirement.status=CLARIFYING`, `approvedRevisionId=null`, `confirmedText=null`을 반환한다. 거절 사유가 새 입력이므로 requirement.contentVersion은 5로 증가한다. 거절된 revision.inputContentVersion은 생성 당시 값 4를 유지한다. 동일 거절 재전송은 추가 증가 없이 현재 Requirement를 반환한다.

거절 성공 응답 `200` 전체 예시:

```json
{
  "data": {
    "revision": {
      "id": 701,
      "requirementId": 401,
      "revisionNo": 1,
      "text": "시스템은 최대 동시 사용자 3,000명의 상품 조회 부하 시험을 10분간 수행할 때 p95 응답 시간 2초 이하, 성공 응답 비율 99.9% 이상을 만족해야 한다.",
      "status": "REJECTED",
      "inputContentVersion": 4,
      "basedOnClarificationIds": [
        601,
        603,
        602
      ],
      "rejectionReason": "동시 사용자 수를 최대치로 명확하게 표현해주세요.",
      "acceptanceCriteria": []
    },
    "requirement": {
      "id": 401,
      "documentId": 101,
      "analysisId": 301,
      "sequenceNo": 1,
      "originalText": "시스템은 많은 사용자의 동시 상품 조회 요청에 빠르게 응답해야 한다. 부하 시험은 10분 동안 수행하며 성공 응답 비율은 99.9% 이상이어야 한다.",
      "status": "CLARIFYING",
      "contentVersion": 5,
      "approvedRevisionId": null,
      "confirmedText": null
    }
  }
}
```

### 5.17. 고객 질문서 Preview

`GET /api/documents/{documentId}/previews/customer` · 한형준 · P2

P2 구현. 현재 OPEN 문제의 WAITING 질문만 제공한다. 질문 없는 요구사항은 requirements에서 제외한다. basis는 모든 요구사항의 읽기 버전. summary는 해당 문서 전체 기준이다. REPEATABLE_READ로 동일 스냅샷에서 조합한다.

요청 본문: 없음.

성공 응답 `200` · `CustomerPreview`:

```json
{
  "data": {
    "documentId": 101,
    "documentTitle": "상품 조회 서비스 요구사항",
    "generatedAt": "2026-09-02T06:00:00Z",
    "summary": {
      "totalRequirements": 1,
      "confirmedRequirements": 0,
      "openIssueCount": 2,
      "waitingQuestionCount": 2
    },
    "basis": [
      {
        "requirementId": 401,
        "contentVersion": 1,
        "approvedRevisionId": null
      }
    ],
    "requirements": [
      {
        "requirementId": 401,
        "sequenceNo": 1,
        "originalText": "시스템은 많은 사용자의 동시 상품 조회 요청에 빠르게 응답해야 한다. 부하 시험은 10분 동안 수행하며 성공 응답 비율은 99.9% 이상이어야 한다.",
        "contentVersion": 1,
        "questions": [
          {
            "id": 601,
            "issueId": 501,
            "type": "QUANTITY_MISSING",
            "evidence": "많은 사용자의 정량 기준이 없다.",
            "roundNo": 1,
            "questionText": "부하 시험의 최대 동시 사용자는 몇 명인가요?"
          },
          {
            "id": 602,
            "issueId": 502,
            "type": "PERFORMANCE_MISSING",
            "evidence": "빠르게의 측정 가능한 응답 시간 기준이 없다.",
            "roundNo": 1,
            "questionText": "부하 시험에서 목표 응답 시간과 측정 지표는 무엇인가요?"
          }
        ]
      }
    ]
  }
}
```

### 5.18. 개발팀용 Preview

`GET /api/documents/{documentId}/previews/developer` · 한형준 · P2

P2 구현. 승인된 수정안과 근거 답변만 확정 목록에 포함한다. 미확정 요구사항은 별도 배열이다. issues는 해당 요구사항의 모든 Issue 이력(ID 오름차순), questions는 모든 Clarification 이력(issueId → roundNo 오름차순)이다. 각 요구사항 배열은 sequenceNo 오름차순. basis는 모든 요구사항. REPEATABLE_READ 조회 및 approvedRevisionId/본문 일치 검증. 파일 다운로드와 AI 재생성 없음.

요청 본문: 없음.

성공 응답 `200` · `DeveloperPreview`:

```json
{
  "data": {
    "documentId": 101,
    "documentTitle": "상품 조회 서비스 요구사항",
    "generatedAt": "2026-09-02T06:00:00Z",
    "summary": {
      "totalRequirements": 1,
      "confirmedRequirements": 1,
      "openIssueCount": 0,
      "waitingQuestionCount": 0
    },
    "basis": [
      {
        "requirementId": 401,
        "contentVersion": 4,
        "approvedRevisionId": 701
      }
    ],
    "confirmedRequirements": [
      {
        "requirementId": 401,
        "sequenceNo": 1,
        "originalText": "시스템은 많은 사용자의 동시 상품 조회 요청에 빠르게 응답해야 한다. 부하 시험은 10분 동안 수행하며 성공 응답 비율은 99.9% 이상이어야 한다.",
        "contentVersion": 4,
        "approvedRevision": {
          "id": 701,
          "requirementId": 401,
          "revisionNo": 1,
          "text": "시스템은 최대 동시 사용자 3,000명의 상품 조회 부하 시험을 10분간 수행할 때 p95 응답 시간 2초 이하, 성공 응답 비율 99.9% 이상을 만족해야 한다.",
          "status": "APPROVED",
          "inputContentVersion": 4,
          "basedOnClarificationIds": [
            601,
            603,
            602
          ],
          "rejectionReason": null,
          "acceptanceCriteria": []
        },
        "evidenceAnswers": [
          {
            "id": 601,
            "requirementId": 401,
            "issueId": 501,
            "roundNo": 1,
            "questionText": "부하 시험의 최대 동시 사용자는 몇 명인가요?",
            "answerText": "많이 접속할 것 같습니다.",
            "status": "ANSWERED"
          },
          {
            "id": 603,
            "requirementId": 401,
            "issueId": 501,
            "roundNo": 2,
            "questionText": "부하 시험의 최대 동시 사용자 수를 숫자로 알려주세요.",
            "answerText": "최대 동시 사용자 3,000명입니다.",
            "status": "RESOLVED"
          },
          {
            "id": 602,
            "requirementId": 401,
            "issueId": 502,
            "roundNo": 1,
            "questionText": "부하 시험에서 목표 응답 시간과 측정 지표는 무엇인가요?",
            "answerText": "p95 응답 시간 2초 이하입니다.",
            "status": "RESOLVED"
          }
        ]
      }
    ],
    "unconfirmedRequirements": []
  }
}
```

## 6. 비동기·동시성·중복 처리

### 6.1 작업 조회

프론트는 `202`의 `data.id` 또는 답변 응답의 `data.analysis.id`를 저장하고 `GET /analyses/{id}`를 약 1초 간격으로 조회한다. 진행 상태가 `COMPLETED` 또는 `FAILED`이면 polling을 멈춘다. 화면 이탈 시 polling을 정리한다. HTTP 일시 오류는 재조회할 수 있으나, 이것을 새 분석 POST나 서버 작업 FAILED로 간주하지 않는다.

| Analysis 상태 | result | error | 프론트 동작 |
| --- | --- | --- | --- |
| PENDING | null | null | 처리 중 표시, 조회 계속 |
| PROCESSING | null | null | 처리 중 표시, 조회 계속 |
| COMPLETED | AnalysisResult | null | 관련 요구사항·Workflow 재조회 |
| FAILED | null | AnalysisFailure | 실패 표시, 재시도 버튼 제공 |

`createdAt`은 항상 있고 `startedAt`은 실행 시작 후 값이 있다. `completedAt`은 성공·실패 종료 시각이다. 실행 전 재시작으로 실패한 작업은 startedAt=null일 수 있다. result의 ID들은 **그 작업의 완료 결과**이며 현재 요구사항 상태를 뜻하지 않는다. 승인 후에도 최초 분석의 result는 변하지 않는다.

### 6.2 변경 요청 검증 순서

1. JSON 형식, 필수 필드, ID, 문자열 검증.
2. 문서 또는 요구사항 잠금 확보 및 대상 소속 검증.
3. 기존 요청과 정확히 동일한 답변·결정·직접 재시도인지 확인. 동일하면 저장된 작업/결정을 재사용한다. 버전이 이미 진행됐다는 이유만으로 정상 재전송을 실패시키지 않는다.
4. 새 처리인 경우 CONFIRMED 변경 제한, 활성 작업 여부, expectedContentVersion과 현재 버전, 질문/수정안 상태 검증.
5. 같은 트랜잭션에서 저장. 작업 실행은 커밋 이후. 실패 기록은 결과 반영 롤백 이후 별도 트랜잭션에서 남긴다.

### 6.3 기능별 규칙

| 상황 | 결과 |
| --- | --- |
| 동일 문서에 활성 DOCUMENT 작업 또는 성공 이력 | 분석 새 접수 409. 이력 API에서 기존 ID 확인 |
| 같은 요구사항에 활성 ANSWER/REVISION 작업 | 새 답변·재생성·검토 409. 다른 요구사항은 독립 처리 |
| 같은 질문에 동일 답변 재제출 | 원래 답변이 생성한 작업 반환. 진행 중 202, 종료 200. 버전 추가 증가 없음 |
| 같은 질문에 다른 답변 재제출 | 409 ANSWER_ALREADY_SUBMITTED. 기존 답변 수정 없음 |
| 실패한 작업 재시도 | 같은 입력/버전의 새 ID, retryOfAnalysisId 기록. 직접 재시도가 이미 있으면 그 ID 재사용 |
| 재시도도 실패 | 실패한 최신 작업 ID의 retries 호출. 이전 실패 ID에 반복 POST해 형제 작업을 만들지 않음 |
| stale 버전·조건 불일치로 새 재시도 불가 | 409 CONTENT_VERSION_CONFLICT 또는 STATE_CONFLICT |
| 같은 revision의 동일 결정·동일 거절 사유 | 200. 저장된 해당 revision과 현재 Requirement 반환. 부수 효과 없음 |
| 이전 결정 또는 거절 사유 변경 | 409 REVISION_ALREADY_REVIEWED |
| 모든 문제 해결·새 수정안 제안 | IN_REVIEW. PROPOSED 최대 1개 |
| 승인 | Revision APPROVED + Requirement CONFIRMED + approvedRevisionId/confirmedText 동일 트랜잭션. contentVersion 유지 |
| 거절 | Revision REJECTED + Requirement CLARIFYING + 거절 사유 저장 + contentVersion 1 증가를 같은 트랜잭션으로 처리. 문제를 자동 재개방하지 않음 |
| 승인 없는 확정 요청·확정 후 수정 | 제공하지 않음. 일반 CRUD로 우회 금지 |

현재 workflow 조회는 기본 상태와 Workflow 데이터를 같은 읽기 스냅샷으로 구성한다. 기본 상세와 workflow를 별도로 호출한 사이 값이 바뀌면 프론트는 workflow의 최신 버전으로 다시 조회하고, 서버의 409 검증을 따른다. 요청의 버전은 권한 증명이 아니다.

### 6.4 Preview의 버전 일관성

Report는 기존 CoreRequirementPort와 WorkflowPreviewPort를 사용한다. 같은 read-only REPEATABLE_READ 트랜잭션에서 원문·요구사항·질문·승인 수정안을 읽는다. `basis`는 조회 당시 모든 요구사항의 contentVersion/approvedRevisionId이다. 승인된 본문은 Requirement.confirmedText 및 approvedRevisionId에 해당하는 Revision.text와 같아야 한다. Preview의 approvedRevision은 기존 ApprovedRevisionSnapshot과 RequirementSnapshot을 조합한다. requirementId는 소속 요구사항, status는 APPROVED, rejectionReason은 null, inputContentVersion은 승인 시 일치가 검증된 contentVersion으로 채운다. 불일치하면 409 PREVIEW_VERSION_CONFLICT이며 서로 다른 버전을 혼합하지 않는다.

PreviewSummary는 기존 Core/Workflow Port에서 읽은 요구사항·문제·질문으로 계산한다. 분석 진행/실패 여부는 프론트가 분석 이력 API에서 따로 표시한다. 한형준이 Workflow Repository를 직접 조회하는 구현은 추가하지 않는다.

개발팀용 미확정 목록의 `issues`는 모든 문제 이력을 ID 오름차순, `questions`는 모든 질문·답변 이력을 issueId → roundNo 오름차순으로 반환한다. 고객용 필터를 이 배열에 재사용하지 않는다. `basis`·요구사항 배열은 sequenceNo 오름차순, `evidenceAnswers`는 issueId → roundNo 오름차순이다. `basedOnClarificationIds`는 근거 집합이며 배열 위치에 의존하지 않고 ID로 대응한다.

## 7. 오류 응답

오류 코드는 프론트 분기에 사용하고 message는 사용자 안내에 사용한다. SQL·스택·원시 AI 출력·비밀 값은 보내지 않는다. 부모 리소스가 없으면 해당 부모의 목록 조회는 404이며, 존재하지만 비어 있는 목록은 200 + items=[]다.

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "요청 값을 확인해주세요.",
    "fieldErrors": [
      {
        "field": "answerText",
        "message": "답변을 입력해주세요."
      }
    ]
  }
}
```

| HTTP | code | 의미 |
| --- | --- | --- |
| 400 | VALIDATION_ERROR | JSON 형식·필수값·길이·enum·정수 범위·미정의 필드 오류. PDF 업로드에서는 file 누락·빈 파일·PDF가 아닌 파일·제목 검증 실패·추출 텍스트가 비어 있거나 공백-only·10MB 초과 포함 |
| 404 | RESOURCE_NOT_FOUND | 프로젝트/문서/요구사항/질문/작업/수정안 없음 |
| 409 | ANALYSIS_IN_PROGRESS | 같은 문서/요구사항의 활성 작업 존재 |
| 409 | DOCUMENT_ALREADY_ANALYZED | 문서 최초 분석 성공 이력 존재 |
| 409 | ANSWER_ALREADY_SUBMITTED | 이미 제출한 답변을 다른 내용으로 덮어쓰기 시도 |
| 409 | CONTENT_VERSION_CONFLICT | expected/inputContentVersion 불일치 |
| 409 | REQUIREMENT_CONFIRMED | 확정된 요구사항의 신규 변경 시도 |
| 409 | OPEN_ISSUES_EXIST | 미해결 문제가 있어 제안·승인 불가 |
| 409 | REVISION_ALREADY_PROPOSED | 검토할 기존 PROPOSED 존재 |
| 409 | REVISION_ALREADY_REVIEWED | 검토 결정·거절 사유 변경 시도 |
| 409 | ANALYSIS_NOT_RETRYABLE | FAILED가 아니고 기존 직접 재시도도 없는 대상 |
| 409 | PREVIEW_VERSION_CONFLICT | 확정본·승인 수정안 간 불일치 |
| 409 | STATE_CONFLICT | 위 코드로 설명되지 않는 업무 전제 위반 |
| 500 | INTERNAL_ERROR | API 요청 처리 자체의 서버 오류. 예상하지 못한 Storage 또는 PDF 처리 오류 포함 |

비동기 실패 코드는 HTTP 오류와 구분한다. 작업 조회는 HTTP 200이며 `data.status=FAILED`, `data.error.code`에 `AI_OUTPUT_INVALID`, `ANALYSIS_EXECUTION_FAILED`, `ANALYSIS_INTERRUPTED`, `CONTENT_VERSION_CONFLICT` 중 원인을 저장한다. 프론트는 알 수 없는 실패 코드도 message와 일반 재시도 안내로 처리한다. 클라우드 DB를 여러 프로세스가 공유할 때 다른 서버의 정상 작업을 재시작 복구 대상으로 처리하지 않는다. 개발 환경 격리 또는 작업 소유 범위는 DB/실행 설정에서 관리한다.

## 8. 프론트 화면과 연결 순서

| 화면 | 초기 조회 | 주요 동작 |
| --- | --- | --- |
| 프로젝트·문서/요구사항 목록 | 프로젝트 → 문서 목록 → 문서 원문 + 분석 이력 + 요구사항 목록 | 문서 등록, 분석 시작, 작업 polling, 요구사항 선택 |
| 요구사항 상세·검토 | 요구사항 기본 상세 + workflow | 답변 입력, 추가 회차 확인, 수정안 승인·거절, 실패 재시도 |
| 결과 Preview (P2 패널/탭) | customer 또는 developer Preview | 현재 질문서/확정 요구사항 표시; 다운로드 버튼 미제공 |

문서 등록 후 분석은 별도 버튼으로 시작할 수 있다. 모든 작업 완료 뒤 영향받은 목록·기본 상세·workflow·열린 Preview를 다시 조회한다. 새로고침 시 문서 분석 이력의 PENDING/PROCESSING을 찾아 polling을 재개한다. 질문 선택 시 자신의 WAITING 상태와 requirement의 activeAnalysis=null을 확인한다. 다른 질문의 작업이 실행 중이면 해당 요구사항 전체 답변 입력/검토를 잠시 비활성화한다.

FILE Document도 생성 이후에는 TEXT Document와 동일하게 `POST /api/documents/{documentId}/analyses`로 분석한다. Workflow는 sourceType별 분석 로직을 만들지 않고 `CoreRequirementPort.DocumentSnapshot.content`의 추출 텍스트를 기존과 동일하게 사용한다. 이후 Analysis → Requirement → AmbiguityIssue → Clarification → RequirementRevision 흐름과 상태·버전 규칙은 변경하지 않는다.

### 공통 Mock E2E 시나리오

아래 ID는 설명용이며 실제 클라이언트는 서버가 반환한 ID를 사용한다. AI/Mock이 DB ID나 APPROVED/CONFIRMED를 결정하지 않는다.

| 순서 | 요청/결과 | 상태·버전 |
| --- | --- | --- |
| 1 | 프로젝트 1 생성, 문서 101 등록 | 분석 전 요구사항 없음 |
| 2 | 문서 분석 301 접수 → 완료 | 요구사항 401 EXTRACTED → AMBIGUOUS → CLARIFYING v1, 문제 501/502 OPEN, 질문 601/602 WAITING |
| 3 | 질문 601에 ‘많이 접속할 것 같습니다.’, expectedVersion=1 | 작업 302, 요구사항 CLARIFYING v2, 질문 601 ANSWERED |
| 4 | 작업 302: insufficient | 요구사항 CLARIFYING, 문제 501 OPEN, 추가 질문 603 roundNo=2 WAITING, revisionIds=[] |
| 5 | 질문 603에 ‘최대 동시 사용자 3,000명입니다.’, expectedVersion=2 | 작업 303, CLARIFYING v3; 501/603 RESOLVED, 문제 502 OPEN |
| 6 | 질문 602에 ‘p95 응답 시간 2초 이하입니다.’, expectedVersion=3 | 작업 304, v4; 502/602 RESOLVED, 수정안 701 PROPOSED, Requirement IN_REVIEW |
| 7 | 수정안 701 APPROVE, expectedVersion=4 | 701 APPROVED + 401 CONFIRMED, confirmedText 복사, v4 유지 |
| 8 | Preview 조회 | 고객 questions 없음; 개발팀 확정본 701과 근거 답변 601/603/602 |

표의 expectedVersion은 설명 약칭이며 실제 JSON 필드는 항상 `expectedContentVersion`이다. 3,000명·p95 2초는 Mock 시나리오의 고객 답변에서 나온다. 원문의 10분·99.9% 조건을 보존하며, 답변에 없는 기준을 AI가 임의로 만들어 확정하지 않는다.

분기 검증: (a) 두 문제 중 하나만 해결하면 수정안을 만들지 않음, (b) 답변 작업 실패 후 같은 버전으로 재시도, (c) 701 거절로 v4→v5, expectedContentVersion=5로 307 REVISION 작업과 revisionNo=2/inputContentVersion=5 생성, (d) 같은 승인 재전송 시 중복 반영 없음, (e) 진행 작업·stale 버전이면 409. Mock은 임의 입력까지 이해하는 실제 AI로 표시하지 않으며, 지원 샘플 이외 입력을 무조건 성공으로 처리하지 않는다.

### 거절 → 재생성 → 승인 분기

| 순서 | 요청 또는 결과 | 요구사항 버전 | 수정안 입력 버전 |
| --- | --- | --- | --- |
| 1 | 701 PROPOSED, IN_REVIEW | 4 | 701: 4 |
| 2 | 701 REJECT, expectedContentVersion=4 | 5, CLARIFYING | 701: 4 유지 |
| 3 | 같은 거절 재전송(expectedContentVersion=4) | 5 유지, 새 처리 없음 | 701: 4 |
| 4 | 재생성 expectedContentVersion=5, Analysis 307 | 5 | 작업 입력 5 |
| 5 | 307 완료, 새 수정안 702/revisionNo=2 | 5, IN_REVIEW | 702: 5 |
| 6 | 702 APPROVE, expectedContentVersion=5 | 5, CONFIRMED | 702: 5 |
| 7 | 과거 701의 동일 REJECT 재전송 | 5, CONFIRMED 유지 | 701은 REJECTED 유지 |

프론트는 버전을 직접 증가시키지 않는다. 답변 응답의 `data.contentVersion`, 검토 응답의 `data.requirement.contentVersion`, Workflow 응답의 `data.contentVersion`을 사용한다. 409이면 최신 상태를 재조회하고 사용자 입력을 보존한다. 과거 검토 재전송 응답은 과거 revision과 **현재** requirement의 조합이다.

## 9. 필드 사전

모든 응답 필드는 필수이며 nullable은 명시적인 null을 반환한다. 요청의 선택 필드만 생략할 수 있다. 조건부 업무 제약은 4·6절과 YAML 설명을 함께 적용한다. ReviewRequest는 ApproveRequest/RejectRequest의 oneOf이며 두 가지를 혼합하지 않는다.

### ProjectCreate

길이와 공백-only 검사는 JSON 원문 및 정규화 결과에 적용한다. 길이는 Unicode 코드 포인트 기준이다. 앞뒤 공백은 U+0009~U+000D, U+0020, U+0085, U+00A0, U+1680, U+2000~U+200A, U+2028, U+2029, U+202F, U+205F, U+3000, U+FEFF로 고정한다. 답변·거절 사유는 CRLF→LF 후 위 공백을 제거한다. 이름·설명·제목은 위 공백만 제거한다. content는 보존하되 공백-only는 거절한다. 선택적 description은 생략/null이면 null, 문자열이면 1~2000자이며 공백-only는 400이다.

| 필드 | 타입 | 필수 | null | 조건·의미 |
| --- | --- | --- | --- | --- |
| `name` | string | 필수 | 불가 | 프로젝트 이름 최대 100자 |
| `description` | string | 선택 | 허용 | 설명; 생략 시 null 최대 2000자 |

### Project

| 필드 | 타입 | 필수 | null | 조건·의미 |
| --- | --- | --- | --- | --- |
| `id` | integer | 필수 | 불가 | DB에서 생성한 양수 ID. JSON number; 본 명세는 JavaScript 안전 정수 범위로 제한한다. |
| `name` | string | 필수 | 불가 | - |
| `description` | string | 필수 | 허용 | - |
| `createdAt` | string | 필수 | 불가 | UTC ISO-8601, 예: 2026-09-02T06:00:00Z |

### DocumentCreate

길이와 공백-only 검사는 JSON 원문 및 정규화 결과에 적용한다. 길이는 Unicode 코드 포인트 기준이다. 앞뒤 공백은 U+0009~U+000D, U+0020, U+0085, U+00A0, U+1680, U+2000~U+200A, U+2028, U+2029, U+202F, U+205F, U+3000, U+FEFF로 고정한다. 답변·거절 사유는 CRLF→LF 후 위 공백을 제거한다. 이름·설명·제목은 위 공백만 제거한다. content는 보존하되 공백-only는 거절한다. 선택적 description은 생략/null이면 null, 문자열이면 1~2000자이며 공백-only는 400이다.

| 필드 | 타입 | 필수 | null | 조건·의미 |
| --- | --- | --- | --- | --- |
| `title` | string | 필수 | 불가 | 문서 제목 최대 200 코드 포인트 |
| `sourceType` | DocumentSourceType | 필수 | 불가 | `TEXT`만 허용 |
| `content` | string | 필수 | 불가 | 등록 후 원문 수정 없음. 최대 100000 코드 포인트 |

### DocumentUpload

`multipart/form-data` 전용 요청이다. `sourceType`은 요청 필드가 아니며 서버가 `FILE`로 결정한다.

| 필드 | 타입 | 필수 | null | 조건·의미 |
| --- | --- | --- | --- | --- |
| `title` | string | 필수 | 불가 | 기존 문서 제목 규칙과 동일. 최대 200 코드 포인트 |
| `file` | binary | 필수 | 불가 | 비어 있지 않은 PDF 한 개. 최대 10MB. DOCX·OCR·이미지 PDF는 지원하지 않음 |

### DocumentSummary

| 필드 | 타입 | 필수 | null | 조건·의미 |
| --- | --- | --- | --- | --- |
| `id` | integer | 필수 | 불가 | DB에서 생성한 양수 ID. JSON number; 본 명세는 JavaScript 안전 정수 범위로 제한한다. |
| `projectId` | integer | 필수 | 불가 | DB에서 생성한 양수 ID. JSON number; 본 명세는 JavaScript 안전 정수 범위로 제한한다. |
| `title` | string | 필수 | 불가 | - |
| `sourceType` | DocumentSourceType | 필수 | 불가 | - |
| `createdAt` | string | 필수 | 불가 | UTC ISO-8601, 예: 2026-09-02T06:00:00Z |

### Document

| 필드 | 타입 | 필수 | null | 조건·의미 |
| --- | --- | --- | --- | --- |
| `id` | integer | 필수 | 불가 | DB에서 생성한 양수 ID. JSON number; 본 명세는 JavaScript 안전 정수 범위로 제한한다. |
| `projectId` | integer | 필수 | 불가 | DB에서 생성한 양수 ID. JSON number; 본 명세는 JavaScript 안전 정수 범위로 제한한다. |
| `title` | string | 필수 | 불가 | - |
| `sourceType` | DocumentSourceType | 필수 | 불가 | - |
| `createdAt` | string | 필수 | 불가 | UTC ISO-8601, 예: 2026-09-02T06:00:00Z |
| `content` | string | 필수 | 불가 | TEXT는 사용자가 등록한 원문, FILE은 서버가 PDF에서 추출한 텍스트. 최대 100000 코드 포인트 |

### Requirement

| 필드 | 타입 | 필수 | null | 조건·의미 |
| --- | --- | --- | --- | --- |
| `id` | integer | 필수 | 불가 | DB에서 생성한 양수 ID. JSON number; 본 명세는 JavaScript 안전 정수 범위로 제한한다. |
| `documentId` | integer | 필수 | 불가 | DB에서 생성한 양수 ID. JSON number; 본 명세는 JavaScript 안전 정수 범위로 제한한다. |
| `analysisId` | integer | 필수 | 불가 | DB에서 생성한 양수 ID. JSON number; 본 명세는 JavaScript 안전 정수 범위로 제한한다. |
| `sequenceNo` | integer | 필수 | 불가 | 최초 문서 분석 안의 순번 |
| `originalText` | string | 필수 | 불가 | - |
| `status` | RequirementStatus | 필수 | 불가 | - |
| `contentVersion` | integer | 필수 | 불가 | 수정안 생성 당시 불변 버전. 예: v4 수정안을 거절해 요구사항이 v5가 되어도 기존 수정안은 4 유지. |
| `approvedRevisionId` | integer | 필수 | 허용 | DB에서 생성한 양수 ID. JSON number; 본 명세는 JavaScript 안전 정수 범위로 제한한다. |
| `confirmedText` | string | 필수 | 허용 | - |

### Issue

| 필드 | 타입 | 필수 | null | 조건·의미 |
| --- | --- | --- | --- | --- |
| `id` | integer | 필수 | 불가 | DB에서 생성한 양수 ID. JSON number; 본 명세는 JavaScript 안전 정수 범위로 제한한다. |
| `requirementId` | integer | 필수 | 불가 | DB에서 생성한 양수 ID. JSON number; 본 명세는 JavaScript 안전 정수 범위로 제한한다. |
| `type` | AmbiguityType | 필수 | 불가 | - |
| `evidence` | string | 필수 | 불가 | 원문에서 판단한 불명확성 근거 |
| `status` | IssueStatus | 필수 | 불가 | - |

### Clarification

| 필드 | 타입 | 필수 | null | 조건·의미 |
| --- | --- | --- | --- | --- |
| `id` | integer | 필수 | 불가 | DB에서 생성한 양수 ID. JSON number; 본 명세는 JavaScript 안전 정수 범위로 제한한다. |
| `requirementId` | integer | 필수 | 불가 | DB에서 생성한 양수 ID. JSON number; 본 명세는 JavaScript 안전 정수 범위로 제한한다. |
| `issueId` | integer | 필수 | 불가 | DB에서 생성한 양수 ID. JSON number; 본 명세는 JavaScript 안전 정수 범위로 제한한다. |
| `roundNo` | integer | 필수 | 불가 | 동일 issue 안에서 1부터 증가 |
| `questionText` | string | 필수 | 불가 | - |
| `answerText` | string | 필수 | 허용 | - |
| `status` | ClarificationStatus | 필수 | 불가 | - |

### AcceptanceCriterion

P3 예약 구조. P1/P2 응답에서는 acceptanceCriteria=[]를 반환한다.

| 필드 | 타입 | 필수 | null | 조건·의미 |
| --- | --- | --- | --- | --- |
| `given` | string | 필수 | 불가 | - |
| `when` | string | 필수 | 불가 | - |
| `then` | string | 필수 | 불가 | - |

### Revision

| 필드 | 타입 | 필수 | null | 조건·의미 |
| --- | --- | --- | --- | --- |
| `id` | integer | 필수 | 불가 | DB에서 생성한 양수 ID. JSON number; 본 명세는 JavaScript 안전 정수 범위로 제한한다. |
| `requirementId` | integer | 필수 | 불가 | DB에서 생성한 양수 ID. JSON number; 본 명세는 JavaScript 안전 정수 범위로 제한한다. |
| `revisionNo` | integer | 필수 | 불가 | 요구사항 안의 수정안 순번 |
| `text` | string | 필수 | 불가 | - |
| `status` | RevisionStatus | 필수 | 불가 | - |
| `inputContentVersion` | integer | 필수 | 불가 | 수정안 생성 당시 불변 버전. 예: v4 수정안을 거절해 요구사항이 v5가 되어도 기존 수정안은 4 유지. |
| `basedOnClarificationIds` | array<integer> | 필수 | 불가 | - |
| `rejectionReason` | string | 필수 | 허용 | - |
| `acceptanceCriteria` | array<AcceptanceCriterion> | 필수 | 불가 | - |

### Assessment

| 필드 | 타입 | 필수 | null | 조건·의미 |
| --- | --- | --- | --- | --- |
| `issueId` | integer | 필수 | 불가 | DB에서 생성한 양수 ID. JSON number; 본 명세는 JavaScript 안전 정수 범위로 제한한다. |
| `sufficient` | boolean | 필수 | 불가 | - |
| `reason` | string | 필수 | 불가 | 이번 답변의 충분성 판정 근거 |
| `nextClarificationId` | integer | 필수 | 허용 | DB에서 생성한 양수 ID. JSON number; 본 명세는 JavaScript 안전 정수 범위로 제한한다. |

### AnalysisResult

완료 시 저장한 불변 결과 참조. requirementIds는 대상/생성 요구사항, issueIds는 생성/평가 문제, clarificationIds·revisionIds는 이번 작업에서 새로 생성한 항목이다. 전체 현재 상태는 각 조회 API로 읽는다. DOCUMENT/REVISION의 assessment는 null; ANSWER는 필수 객체다.

| 필드 | 타입 | 필수 | null | 조건·의미 |
| --- | --- | --- | --- | --- |
| `requirementIds` | array<integer> | 필수 | 불가 | - |
| `issueIds` | array<integer> | 필수 | 불가 | - |
| `clarificationIds` | array<integer> | 필수 | 불가 | - |
| `revisionIds` | array<integer> | 필수 | 불가 | - |
| `assessment` | object | 필수 | 허용 | - |

### AnalysisFailure

| 필드 | 타입 | 필수 | null | 조건·의미 |
| --- | --- | --- | --- | --- |
| `code` | string | 필수 | 불가 | - |
| `message` | string | 필수 | 불가 | - |

### Analysis

DOCUMENT의 requirementId/clarificationId/inputContentVersion은 null. ANSWER는 모두 값이 있으며 REVISION의 clarificationId만 null. result는 COMPLETED에서만 객체; error는 FAILED에서만 객체. completedAt은 COMPLETED/FAILED에서 값이 있다.

| 필드 | 타입 | 필수 | null | 조건·의미 |
| --- | --- | --- | --- | --- |
| `id` | integer | 필수 | 불가 | DB에서 생성한 양수 ID. JSON number; 본 명세는 JavaScript 안전 정수 범위로 제한한다. |
| `kind` | AnalysisKind | 필수 | 불가 | - |
| `status` | AnalysisStatus | 필수 | 불가 | - |
| `documentId` | integer | 필수 | 불가 | DB에서 생성한 양수 ID. JSON number; 본 명세는 JavaScript 안전 정수 범위로 제한한다. |
| `requirementId` | integer | 필수 | 허용 | DB에서 생성한 양수 ID. JSON number; 본 명세는 JavaScript 안전 정수 범위로 제한한다. |
| `clarificationId` | integer | 필수 | 허용 | DB에서 생성한 양수 ID. JSON number; 본 명세는 JavaScript 안전 정수 범위로 제한한다. |
| `inputContentVersion` | integer | 필수 | 허용 | 생성/접수 당시의 불변 업무 입력 버전. ANSWER는 답변 저장으로 증가한 버전, REVISION은 거절 후 현재 버전. DOCUMENT 작업은 null. |
| `retryOfAnalysisId` | integer | 필수 | 허용 | DB에서 생성한 양수 ID. JSON number; 본 명세는 JavaScript 안전 정수 범위로 제한한다. |
| `createdAt` | string | 필수 | 불가 | UTC ISO-8601, 예: 2026-09-02T06:00:00Z |
| `startedAt` | string | 필수 | 허용 | UTC ISO-8601, 예: 2026-09-02T06:00:00Z |
| `completedAt` | string | 필수 | 허용 | UTC ISO-8601, 예: 2026-09-02T06:00:00Z |
| `result` | object | 필수 | 허용 | 완료 시 저장한 불변 결과 참조. requirementIds는 대상/생성 요구사항, issueIds는 생성/평가 문제, clarificationIds·revisionIds는 이번 작업에서 새로 생성한 항목이다. 전체 현재 상태는 각 조회 API로 읽는다. DOCUMENT/REVISION의 assessment는 null; ANSWER는 필수 객체다. |
| `error` | object | 필수 | 허용 | - |

### Workflow

| 필드 | 타입 | 필수 | null | 조건·의미 |
| --- | --- | --- | --- | --- |
| `requirementId` | integer | 필수 | 불가 | DB에서 생성한 양수 ID. JSON number; 본 명세는 JavaScript 안전 정수 범위로 제한한다. |
| `status` | RequirementStatus | 필수 | 불가 | - |
| `contentVersion` | integer | 필수 | 불가 | 수정안 생성 당시 불변 버전. 예: v4 수정안을 거절해 요구사항이 v5가 되어도 기존 수정안은 4 유지. |
| `activeAnalysis` | object | 필수 | 허용 | DOCUMENT의 requirementId/clarificationId/inputContentVersion은 null. ANSWER는 모두 값이 있으며 REVISION의 clarificationId만 null. result는 COMPLETED에서만 객체; error는 FAILED에서만 객체. completedAt은 COMPLETED/FAILED에서 값이 있다. |
| `issues` | array<Issue> | 필수 | 불가 | - |
| `clarifications` | array<Clarification> | 필수 | 불가 | - |
| `revisions` | array<Revision> | 필수 | 불가 | - |

### AnswerSubmit

길이와 공백-only 검사는 JSON 원문 및 정규화 결과에 적용한다. 길이는 Unicode 코드 포인트 기준이다. 앞뒤 공백은 U+0009~U+000D, U+0020, U+0085, U+00A0, U+1680, U+2000~U+200A, U+2028, U+2029, U+202F, U+205F, U+3000, U+FEFF로 고정한다. 답변·거절 사유는 CRLF→LF 후 위 공백을 제거한다. 이름·설명·제목은 위 공백만 제거한다. content는 보존하되 공백-only는 거절한다. 선택적 description은 생략/null이면 null, 문자열이면 1~2000자이며 공백-only는 400이다.

| 필드 | 타입 | 필수 | null | 조건·의미 |
| --- | --- | --- | --- | --- |
| `answerText` | string | 필수 | 불가 | 공백만으로 구성할 수 없다. 최대 20000자 |
| `expectedContentVersion` | integer | 필수 | 불가 | 수정안 생성 당시 불변 버전. 예: v4 수정안을 거절해 요구사항이 v5가 되어도 기존 수정안은 4 유지. |

### AnswerReceipt

contentVersion은 현재 요구사항 버전. analysis.inputContentVersion은 해당 작업 접수 당시 버전. 중복 제출에서는 서로 다를 수 있다.

| 필드 | 타입 | 필수 | null | 조건·의미 |
| --- | --- | --- | --- | --- |
| `clarificationId` | integer | 필수 | 불가 | DB에서 생성한 양수 ID. JSON number; 본 명세는 JavaScript 안전 정수 범위로 제한한다. |
| `requirementId` | integer | 필수 | 불가 | DB에서 생성한 양수 ID. JSON number; 본 명세는 JavaScript 안전 정수 범위로 제한한다. |
| `contentVersion` | integer | 필수 | 불가 | 수정안 생성 당시 불변 버전. 예: v4 수정안을 거절해 요구사항이 v5가 되어도 기존 수정안은 4 유지. |
| `analysis` | Analysis | 필수 | 불가 | - |

### RevisionGenerate

| 필드 | 타입 | 필수 | null | 조건·의미 |
| --- | --- | --- | --- | --- |
| `expectedContentVersion` | integer | 필수 | 불가 | 수정안 생성 당시 불변 버전. 예: v4 수정안을 거절해 요구사항이 v5가 되어도 기존 수정안은 4 유지. |

### ApproveRequest

| 필드 | 타입 | 필수 | null | 조건·의미 |
| --- | --- | --- | --- | --- |
| `decision` | ReviewDecision | 필수 | 불가 | APPROVE만 허용. rejectionReason 필드 전송 금지. |
| `expectedContentVersion` | integer | 필수 | 불가 | 수정안 생성 당시 불변 버전. 예: v4 수정안을 거절해 요구사항이 v5가 되어도 기존 수정안은 4 유지. |

### RejectRequest

길이와 공백-only 검사는 JSON 원문 및 정규화 결과에 적용한다. 길이는 Unicode 코드 포인트 기준이다. 앞뒤 공백은 U+0009~U+000D, U+0020, U+0085, U+00A0, U+1680, U+2000~U+200A, U+2028, U+2029, U+202F, U+205F, U+3000, U+FEFF로 고정한다. 답변·거절 사유는 CRLF→LF 후 위 공백을 제거한다. 이름·설명·제목은 위 공백만 제거한다. content는 보존하되 공백-only는 거절한다. 선택적 description은 생략/null이면 null, 문자열이면 1~2000자이며 공백-only는 400이다.

| 필드 | 타입 | 필수 | null | 조건·의미 |
| --- | --- | --- | --- | --- |
| `decision` | ReviewDecision | 필수 | 불가 | REJECT만 허용. rejectionReason 필수. |
| `expectedContentVersion` | integer | 필수 | 불가 | 수정안 생성 당시 불변 버전. 예: v4 수정안을 거절해 요구사항이 v5가 되어도 기존 수정안은 4 유지. |
| `rejectionReason` | string | 필수 | 불가 | 거절 사유 최대 2000자 |

### ReviewResult

| 필드 | 타입 | 필수 | null | 조건·의미 |
| --- | --- | --- | --- | --- |
| `revision` | Revision | 필수 | 불가 | - |
| `requirement` | Requirement | 필수 | 불가 | - |

### CustomerQuestion

| 필드 | 타입 | 필수 | null | 조건·의미 |
| --- | --- | --- | --- | --- |
| `id` | integer | 필수 | 불가 | DB에서 생성한 양수 ID. JSON number; 본 명세는 JavaScript 안전 정수 범위로 제한한다. |
| `issueId` | integer | 필수 | 불가 | DB에서 생성한 양수 ID. JSON number; 본 명세는 JavaScript 안전 정수 범위로 제한한다. |
| `type` | AmbiguityType | 필수 | 불가 | - |
| `evidence` | string | 필수 | 불가 | - |
| `roundNo` | integer | 필수 | 불가 | - |
| `questionText` | string | 필수 | 불가 | - |

### CustomerRequirement

| 필드 | 타입 | 필수 | null | 조건·의미 |
| --- | --- | --- | --- | --- |
| `requirementId` | integer | 필수 | 불가 | DB에서 생성한 양수 ID. JSON number; 본 명세는 JavaScript 안전 정수 범위로 제한한다. |
| `sequenceNo` | integer | 필수 | 불가 | - |
| `originalText` | string | 필수 | 불가 | - |
| `contentVersion` | integer | 필수 | 불가 | 수정안 생성 당시 불변 버전. 예: v4 수정안을 거절해 요구사항이 v5가 되어도 기존 수정안은 4 유지. |
| `questions` | array<CustomerQuestion> | 필수 | 불가 | - |

### PreviewSummary

| 필드 | 타입 | 필수 | null | 조건·의미 |
| --- | --- | --- | --- | --- |
| `totalRequirements` | integer | 필수 | 불가 | - |
| `confirmedRequirements` | integer | 필수 | 불가 | - |
| `openIssueCount` | integer | 필수 | 불가 | - |
| `waitingQuestionCount` | integer | 필수 | 불가 | - |

### PreviewBasis

| 필드 | 타입 | 필수 | null | 조건·의미 |
| --- | --- | --- | --- | --- |
| `requirementId` | integer | 필수 | 불가 | DB에서 생성한 양수 ID. JSON number; 본 명세는 JavaScript 안전 정수 범위로 제한한다. |
| `contentVersion` | integer | 필수 | 불가 | 수정안 생성 당시 불변 버전. 예: v4 수정안을 거절해 요구사항이 v5가 되어도 기존 수정안은 4 유지. |
| `approvedRevisionId` | integer | 필수 | 허용 | DB에서 생성한 양수 ID. JSON number; 본 명세는 JavaScript 안전 정수 범위로 제한한다. |

### CustomerPreview

OPEN 문제의 WAITING 질문만 포함. requirements가 비어 있어도 분석 미실행·처리 중·실패·검토 대기일 수 있으므로 확정으로 해석하지 않는다.

| 필드 | 타입 | 필수 | null | 조건·의미 |
| --- | --- | --- | --- | --- |
| `documentId` | integer | 필수 | 불가 | DB에서 생성한 양수 ID. JSON number; 본 명세는 JavaScript 안전 정수 범위로 제한한다. |
| `documentTitle` | string | 필수 | 불가 | - |
| `generatedAt` | string | 필수 | 불가 | UTC ISO-8601, 예: 2026-09-02T06:00:00Z |
| `summary` | PreviewSummary | 필수 | 불가 | - |
| `basis` | array<PreviewBasis> | 필수 | 불가 | - |
| `requirements` | array<CustomerRequirement> | 필수 | 불가 | - |

### ConfirmedRequirement

| 필드 | 타입 | 필수 | null | 조건·의미 |
| --- | --- | --- | --- | --- |
| `requirementId` | integer | 필수 | 불가 | DB에서 생성한 양수 ID. JSON number; 본 명세는 JavaScript 안전 정수 범위로 제한한다. |
| `sequenceNo` | integer | 필수 | 불가 | - |
| `originalText` | string | 필수 | 불가 | - |
| `contentVersion` | integer | 필수 | 불가 | 수정안 생성 당시 불변 버전. 예: v4 수정안을 거절해 요구사항이 v5가 되어도 기존 수정안은 4 유지. |
| `approvedRevision` | Revision | 필수 | 불가 | - |
| `evidenceAnswers` | array<Clarification> | 필수 | 불가 | - |

### UnconfirmedRequirement

issues: 해당 요구사항의 모든 Issue 이력, id 오름차순. questions: 모든 Clarification 이력, issueId/roundNo 오름차순. 상태별 필터링하지 않는다.

| 필드 | 타입 | 필수 | null | 조건·의미 |
| --- | --- | --- | --- | --- |
| `requirementId` | integer | 필수 | 불가 | DB에서 생성한 양수 ID. JSON number; 본 명세는 JavaScript 안전 정수 범위로 제한한다. |
| `sequenceNo` | integer | 필수 | 불가 | - |
| `originalText` | string | 필수 | 불가 | - |
| `status` | RequirementStatus | 필수 | 불가 | `EXTRACTED`, `AMBIGUOUS`, `CLARIFYING`, `IN_REVIEW` |
| `contentVersion` | integer | 필수 | 불가 | 수정안 생성 당시 불변 버전. 예: v4 수정안을 거절해 요구사항이 v5가 되어도 기존 수정안은 4 유지. |
| `issues` | array<Issue> | 필수 | 불가 | - |
| `questions` | array<Clarification> | 필수 | 불가 | - |

### DeveloperPreview

confirmedRequirements에는 APPROVED 수정안만 포함. evidenceAnswers는 해당 수정안의 basedOnClarificationIds와 정확히 대응한다.

| 필드 | 타입 | 필수 | null | 조건·의미 |
| --- | --- | --- | --- | --- |
| `documentId` | integer | 필수 | 불가 | DB에서 생성한 양수 ID. JSON number; 본 명세는 JavaScript 안전 정수 범위로 제한한다. |
| `documentTitle` | string | 필수 | 불가 | - |
| `generatedAt` | string | 필수 | 불가 | UTC ISO-8601, 예: 2026-09-02T06:00:00Z |
| `summary` | PreviewSummary | 필수 | 불가 | - |
| `basis` | array<PreviewBasis> | 필수 | 불가 | - |
| `confirmedRequirements` | array<ConfirmedRequirement> | 필수 | 불가 | - |
| `unconfirmedRequirements` | array<UnconfirmedRequirement> | 필수 | 불가 | - |

### FieldError

| 필드 | 타입 | 필수 | null | 조건·의미 |
| --- | --- | --- | --- | --- |
| `field` | string | 필수 | 불가 | - |
| `message` | string | 필수 | 불가 | - |

### ErrorDetail

| 필드 | 타입 | 필수 | null | 조건·의미 |
| --- | --- | --- | --- | --- |
| `code` | string | 필수 | 불가 | - |
| `message` | string | 필수 | 불가 | - |
| `fieldErrors` | array<FieldError> | 필수 | 불가 | - |

## 10. 구현 담당과 적용

- **신형섭:** 전체 명세·외부 Workflow DTO·Mock 시나리오·분석 결과 저장·재시도 관계·검토 흐름·공용 enum 계약. 계약 변경은 공용 명세에 먼저 기록한다.
- **한형준:** Core/Preview 외부 DTO·Controller, 공통 응답/HTTP 예외 처리, DB 스키마·Supabase 연결. Workflow 스키마는 요청 내용을 반영해 적용한다.
- **프론트:** 반환 ID/업무 버전 사용, 비동기 상태 polling, null/빈 배열/409 처리, 최신 상세 재조회. DB용 snake_case나 내부 Port DTO에 직접 의존하지 않는다.

이 문서는 기존 plan의 Endpoint/상태/소유권을 구체화한 공식 외부 계약이다. 로그인 도입, 삭제, 수동 편집, 다운로드를 구현 범위에 추가하지 않는다. 실제 기존 health 계약과 오류 구현은 저장소를 확인해 맞추며, 이번 개정의 조회/필드/재시도 저장 구조·enum·거절 버전 규칙을 공용 명세와 코드에 함께 반영한다.

검증 범위: OpenAPI 문법·참조·요청/응답 예시와 JSON 스키마 일치 여부를 검사한다. Spring Boot 실행·Supabase 접속·실제 HTTP 응답·업무 트랜잭션의 통합 테스트는 구현 후 별도 검증 대상이다.

## 11. 개정 내역과 검증 기준

0.4.0은 기존 Analysis·Requirement·Workflow Endpoint의 경로·JSON 구조·상태·버전 규칙을 유지하면서 PDF 파일 업로드를 추가했다. `DocumentSourceType`에 `FILE`을 추가하고 기존 TEXT 입력과 병행한다. PDF 원본은 Supabase Storage에, 추출 텍스트는 기존 `Document.content`에 저장하며 Storage 내부 정보와 PostgreSQL binary는 외부 계약에 포함하지 않는다.

0.3.0은 0.2.0의 Endpoint·HTTP 메서드·JSON 구조·담당자를 유지하면서 RequirementStatus를 첨부된 개정 DBML 수정안 기준 5값으로 변경했다. `OPEN`을 제거하고 `EXTRACTED`, `AMBIGUOUS`, `CLARIFYING`을 추가했으며 관련 상태 전이와 예시를 함께 수정했다.

0.2.0은 0.1.0을 대체하며 다음을 반영했다.

- 거절 시 버전 증가, 거절·재생성·승인 JSON과 연속 시나리오 보정.
- 9개 공통 enum 명시, ReviewDecision oneOf 연결, DocumentSourceType(TEXT) 연결.
- Analysis의 result/retry/error/input 보존을 DB 수정 요청과 연결.
- 문자열 정규화, 목록 정렬, Developer Preview 전체 이력, 개인 문서 경로 수정.
- 검토 시각 reviewed_at·확정 시각 confirmed_at은 DB 보완 권장 항목이며 외부 Revision 응답 필드에는 이번에 추가하지 않는다.

API의 구조 제약은 [OpenAPI 3.0.3 명세](https://spec.openapis.org/oas/v3.0.3.html)를 따른다. 스키마 검증과 별개로 상태 전이·소속·동시성·중복 처리는 백엔드 업무 검증이 필요하다. DB 적용 기준인 `ReqBridge_DB_Change_Request.md`는 현재 저장소에 제공되지 않았으며, 내부 개발 계획은 `ReqBridge_Backend_Collaboration_Plan.md`를 따른다.

추가 DB 수정 문서의 회차 제약 교체·확정 시각·AC 순서 제안은 DB 요청 1.1에 통합했다. Preview P2·AC P3를 유지하며, 회차는 Issue별로 증가한다. 재질문 사유는 기존 Analysis.result.assessment에 저장하므로 별도 외부 필드를 추가하지 않는다.

과거 0.2.0 작성 당시 검증 기록: 18개 Endpoint·담당자와 9개 enum을 당시 API/plan/DB 요청에 대조했고, 당시 제공된 OpenAPI의 문법·참조, YAML 예시 85개와 Markdown JSON 27개를 검증했다. 이는 이번 0.3.0 공식화 작업에서 OpenAPI나 DB 요청을 다시 검증했다는 뜻이 아니다. 실제 Spring Boot·DB·HTTP 통합 테스트는 수행하지 않았다.

0.3.0 문서 검증 결과(2026-09-02): Markdown의 JSON 코드 블록 28개를 파싱했고, 공식화 전 문서와 비교해 필드·타입 구조가 유지됐음을 확인했다. Endpoint는 P1 16개·P2 2개이며 HTTP 메서드·경로·담당자가 유지됐다. JSON의 `OPEN` 값은 IssueStatus에만 남아 있다. OpenAPI YAML과 DB 수정 요청서는 저장소에 없어 문법·참조·스키마 일치 및 `/api` 중복 여부를 검증하지 않았다. 애플리케이션·DB 테스트는 문서 작업 범위 밖이라 실행하지 않았다.

0.4.0 문서 검증 결과(2026-09-03): Markdown JSON 코드 블록 파싱, P1 17개·P2 2개 Endpoint, `DocumentSourceType`의 `TEXT | FILE`, PDF 업로드의 입력·오류·10MB 제한과 Spring multipart 설정 일치를 검사한다. OpenAPI YAML과 `contract-changes.md`는 저장소에 없어 수정하거나 새로 만들지 않았다. 실제 PDF 추출·Storage·DB·HTTP 통합 테스트는 구현 후 별도 검증 대상이다.
