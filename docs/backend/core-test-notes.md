# Backend Core 검증 기록

## 자동 테스트

실행 명령:

```bash
cd backend
bash gradlew test
```

결과: 성공, 총 28개 테스트 통과.

- 기존 health Controller 계약
- Project 입력 정규화·목록·필수값
- TEXT Document 생성과 존재하지 않는 Project 거절
- Requirement 번호 정렬·연속성 검증
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

## PostgreSQL 통합 검증

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
