# DB 수정 요청 1.1 완료 확인표 검토

검토 기준은 `ReqBridge_DB_Change_Request.md` 1.1이며, 대상은 신규 빈 DB에 적용하는 V1 SQL과 현재 Core 코드다. 실제 Supabase 운영 스키마와 기존 데이터 이전은 아직 사용자가 수행하지 않았으므로 별도로 표시한다.

| # | 상태 | 확인 결과 |
| ---: | --- | --- |
| 1 | 부분 충족 | V1과 초기 ERD 차이는 기록했다. 실제 Supabase DDL·기존 데이터의 이미 반영 항목 대조는 적용 후 필요하다. |
| 2 | 충족 | DocumentSourceType은 TEXT-only이며 상태·분류 enum과 ReviewDecision의 요청 전용 의미를 분리했다. |
| 3 | 부분 충족 | 신규 스키마에서 수정안의 근거 0개와 여러 개를 저장할 수 있고 여러 근거 저장을 검증했다. 기존 단일 FK 데이터 이전 대상은 아직 없다. |
| 4 | 충족 | 서로 다른 Issue 2개에 각각 roundNo=1을 저장하는 PostgreSQL 검증을 통과했다. |
| 5 | 부분 충족 | AC sequenceNo/UNIQUE와 confirmedAt을 반영했다. assessment JSON 의미 검증은 Workflow 구현이 필요하다. |
| 6 | 부분 충족 | kind별 참조·입력 버전과 상태별 result/error/시각 CHECK를 반영했다. result 내부 필수 키와 assessment 의미는 Workflow 검증 대상이다. |
| 7 | 부분 충족 | self retry 금지와 직접 재시도 UNIQUE를 반영·검증했다. FAILED 부모·동일 입력·최신 실패를 잇는 선형 재시도는 Workflow 서비스 검증이 필요하다. |
| 8 | 충족 | ANSWER/REVISION을 합친 requirement별 활성 작업 부분 UNIQUE를 반영·검증했다. |
| 9 | 충족 | PROPOSED 1개, 요구사항/질문/수정안/AC 순번 및 근거 중복 제약을 반영했다. 주요 중복 차단을 PostgreSQL에서 검증했다. |
| 10 | 부분 충족 | 복합 FK로 문서·요구사항·Issue·Clarification·Revision 소속을 보호하고 다른 요구사항의 답변 근거 연결 차단을 검증했다. 승인 Revision 상태와 confirmedText 일치는 Workflow 트랜잭션 검증이 필요하다. |
| 11 | 부분 충족 | Core의 버전 증가·상태 변경·확정 primitive와 테스트가 있다. 거절→재생성→승인 전체 흐름은 Workflow 통합 전이므로 미검증이다. |
| 12 | 미충족 | 새 승인 후 과거 동일 거절 재전송은 Workflow의 멱등 검토 구현과 통합 테스트가 필요하다. |
| 13 | 미충족 | 부분 저장 실패와 재시작 복구는 Workflow 작업 실행기 구현 후 격리 스키마에서 검증해야 한다. |
| 14 | 미충족 | API Markdown 0.2.0은 확인했지만 저장소에 `docs/api/openapi.yaml`과 최신 ERD가 없고 plan에도 새 분석 이력 Endpoint가 반영되지 않았다. |

현재 결론은 **4개 충족, 7개 부분 충족, 3개 미충족**이다. 스키마 단독으로 처리할 수 없는 항목을 완료로 표시하지 않는다.
