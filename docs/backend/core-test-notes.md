# Backend Core 검증 기록

## 최신 검증: History·예외 처리 (2026-09-03)

기준: `feat/backend-core`, Workflow P1 병합 커밋 `d4d47b4` + 이번 Core 보강. 기존 아래 기록과 구분한다.

- DB 없이 기본 실행: 총 179개 중 169개 통과, 선택 실행 PostgreSQL 테스트 10개 생략.
- 로컬 PostgreSQL 17.10을 지정한 전체 실행: **179개 통과, 실패 0, 생략 0**.
- 이번에 추가한 테스트는 33개: Requirement 도메인 6개, 공통 HTTP 오류 22개, 실제 PostgreSQL History 통합 5개.
- 운영 Supabase와 Storage에는 접속하지 않았다. PDF 관련 기존 통합 테스트는 Storage를 mock으로 두고 DB·PDFBox·HTTP는 실제로 실행한다.
- `git diff --check` 통과. 소스 수정은 Core/Common과 담당 테스트·문서에 한정하며 Workflow/contract/공식 API 명세·DB DDL은 변경하지 않았다.

### 무엇을 실제로 검증했는가

1. 잘못된 Core/Workflow ID의 400 + fieldErrors, 원인 예외의 내부 메시지 비노출.
2. 명세의 11개 업무 409 코드 보존. Spring/JPA 잠금 오류는 안전한 409 STATE_CONFLICT.
3. malformed JSON·필수 parameter/part 누락 처리, 원인에 IllegalArgumentException이 포함된 예상 밖 서버 오류는 500 유지.
4. 원문·현재 버전·확정본 보존과 CONFIRMED 변경 차단, 오래된 버전/잘못된 버전 범위/증가 상한 방어.
5. HTTP 등록→실제 비동기 Mock 분석→불충분 답변→추가 회차→충분한 답변→수정안 거절→재생성→승인. 완료된 Analysis 결과·입력 및 질문/답변·거절 수정안이 반복 조회와 재전송 후에도 보존됨.
6. 승인 트랜잭션 안에서 강제로 후속 실패를 발생시키면 Revision 상태·검토 시각과 Requirement 확정 필드 모두 롤백됨. 독립적인 Core 확정 호출은 트랜잭션 부재로 거절됨.
7. 실패한 ANSWER의 재시도 입력 스냅샷/원본 연결 보존 및 버전 재증가 없음.
8. 서로 다른 Requirement의 Revision 연결은 실제 복합 FK가 차단하고 확정본 쓰기는 롤백됨.
9. 같은 expectedContentVersion으로 동시에 Core 쓰기를 시도하면 하나만 성공하고 다른 요청은 CONTENT_VERSION_CONFLICT, DB 버전은 한 번만 증가함.

동시 검토/재시도에 대한 모든 Workflow 경합 경우를 망라하거나 실제 LLM/Storage/클라우드 연결을 검증한 결과는 아니다. 이력에 대한 임의 SQL 변경/삭제를 차단하는 감사 로그 기능도 새로 도입하지 않는다.

### 재실행

일반 테스트는 `backend`에서 실행한다.

```bash
SPRING_PROFILES_ACTIVE=default bash gradlew test --rerun-tasks
```

실제 DB 검증은 **별도로 만든 폐기 가능한 로컬 PostgreSQL DB만** 지정한다. 테스트는 그 DB에 Flyway V1~V4를 적용하고 샘플 데이터를 추가한다. 공유 개발 DB나 운영 Supabase를 넣지 않는다. 테스트용 비밀번호도 Git에 저장하지 않는다.

```bash
SPRING_PROFILES_ACTIVE=default \
REQBRIDGE_TEST_POSTGRES_URL='jdbc:postgresql://localhost:5432/YOUR_DISPOSABLE_TEST_DB' \
REQBRIDGE_TEST_POSTGRES_USER='YOUR_TEST_DB_USER' \
REQBRIDGE_TEST_POSTGRES_PASSWORD='YOUR_TEST_DB_PASSWORD' \
bash gradlew test --rerun-tasks
```

테스트용 HTTP 서버/커넥션 풀은 종료되며 DB 자체는 테스트 코드가 삭제하지 않는다. 이번 검증용 `reqbridge_history_verify_20260903_01`은 접속 세션이 0개인 것을 확인하고 검증 후 삭제했다. 테스트 데이터는 재실행으로 만들 수 있고, 기존 개발 DB 데이터는 수정하지 않았다.

## 이전 기록: 초기 Core 자동 테스트

실행 명령:

```bash
cd backend
bash gradlew test
```

결과: 성공, 총 34개 테스트 통과.

- 기존 health Controller 계약
- Project 입력 정규화·목록·필수값
- TEXT Document 생성과 존재하지 않는 Project 거절
- Requirement 번호 정렬·연속성 검증
- 공용 5단계 RequirementStatus와 최초 EXTRACTED 상태
- CoreRequirementPort Adapter의 Snapshot·상태 매핑
- contentVersion 증가와 오래된 버전 거절
- IN_REVIEW가 아닌 요구사항의 승인 방지
- 확정 본문·revision ID 보존
- CONFIRMED 요구사항 재개방 방지
- 기본 API의 응답 래퍼·목록 래퍼·201 Location
- 요청의 미정의 JSON 필드와 JavaScript 안전 정수 범위 거절
- API 계약의 Unicode 공백 집합 정규화
- 숫자가 아닌 path ID 및 안전 정수 상한 초과 입력의 fieldErrors 응답
- 없는 리소스의 404 공통 응답
- DB 무결성 오류의 세부 정보 비노출 409 응답
- 예상하지 못한 예외의 세부 정보 비노출 500 응답
- 존재하지 않는 API 경로의 공통 404 및 미지원 HTTP 메서드의 405 응답
- Requirement 원문의 계약 공백-only 입력 거절

`git diff --check`도 통과했다.

## 이전 기록: 초기 PostgreSQL 통합 검증

아래는 초기 Core 스키마의 통합 검증 기록이다. 공용 5단계 RequirementStatus 반영 후에는 신규 빈 DB 또는 격리 스키마에서 V1 적용 검증을 다시 수행해야 한다.

실행 중이던 로컬 PostgreSQL에 Flyway 자동 적용용 DB와 Supabase 수동 SQL 적용용 DB를 각각 임시 생성해 다음 항목을 검증했다. 기존 `app` 데이터베이스는 변경하지 않았고, 두 검증 DB는 완료 후 삭제했다.

- PostgreSQL `17.10`
- Flyway V1 마이그레이션 성공
- `app` 스키마 생성 성공
- 업무 테이블 9개와 Flyway 이력 테이블 생성 확인
- Hibernate `ddl-auto=validate` 성공
- Supabase 프로필(Flyway 비활성)로 웹 ApplicationContext 시작 성공
- Project·Document 생성/목록 HTTP 호출 및 PostgreSQL native enum 저장 성공
- ID 상한 초과 HTTP 요청의 400 응답 확인
- 서로 다른 Issue의 각 round 1 저장, 다중 수정 근거 저장 성공
- 다른 Requirement의 답변 근거 연결 차단
- 동일 Requirement의 활성 ANSWER/REVISION 동시 생성 차단
- 중복 PROPOSED·AC 순번·직접 재시도 차단
- `docker compose config --quiet` 성공

Docker Desktop은 실행 중이 아니어서 Compose 컨테이너 자체는 기동하지 않았다. Docker 환경에서는 다음과 같이 같은 검증을 반복할 수 있다.

```bash
docker compose up -d postgres
cd backend
bash gradlew bootRun
```

애플리케이션 로그에서 Flyway V1 적용과 Hibernate validation 완료를 확인한 뒤 `/api/health`를 호출한다. 기존 데이터베이스에 V1을 수동 재실행하지 않는다.
