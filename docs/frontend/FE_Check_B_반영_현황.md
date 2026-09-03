# FE_Check 피드백 반영 현황 (B)

> **기준 문서:** `FE_Check.md` (프론트엔드 사용성 수정 요청서)  
> **담당:** B (이병주)  
> **브랜치:** `feat/fe-b-ux`  
> **작성일:** 2026-09-04  
> **범위:** 문서에 정리한 19단계 구현 결과와, `FE_Check.md` 기준 잔여 작업

이 문서는 `FE_Check.md`의 피드백을 **B 화면(프로젝트·문서 목록·문서 상세·공통 breadcrumb/상태 라벨)** 에 반영한 내용과, 아직 남은 일을 구분한다.

---

## 1. 한 줄 요약

| 구분 | 상태 |
| --- | --- |
| B 화면 사용성 (정보 구조, ID 숨김, PDF 흐름, 요구사항 목록, 직접 입력 빈 폼) | **반영** |
| C 화면 (workflow · Preview) | **미반영** (이번 브랜치에서 의도적으로 제외) |
| 공용 의존성 PrimeVue | **보류** (C와 합의 후) |
| 백엔드 `바로 승인` API (6절) | **FE에서 임의 확정하지 않음** (요청서 기준 유지) |

---

## 2. FE_Check 항목 ↔ 구현 여부

| FE_Check | 내용 | 담당 | 이번 반영 |
| --- | --- | --- | --- |
| 2.1 | 영문 화면명 제거 | 공용 / C 문구 일부 | **부분.** breadcrumb 한글화·프로젝트/문서명 표시는 반영. `Preview 열기`는 C 링크로 남겨 둠 |
| 2.2 | favicon · 상단 로고 | 공통 디자인 | **미반영.** 인계 범위 밖, C/디자인과 맞출 항목 |
| 2.3 | 개발용 문구·DB ID 제거 | B 목록 + C 상세 | **B 화면 반영.** workflow/Preview 내부 ID는 C |
| 3.1 | 프로젝트 진입 동선 | B | **반영** |
| 3.2 | 직접 입력 기본값 제거 | B | **반영** |
| 3.3 | PDF 등록 흐름 | B | **대부분 반영.** PrimeVue FileUpload만 보류 |
| 4.1 | 불명확 요구사항이 눈에 띄게 | B (문서 상세 목록) | **반영** |
| 4.2 | 상태 명칭 변경 | 공용 `statusLabels.js` | **반영** (C 화면도 같은 라벨을 씀) |
| 5.1 | 미리보기 용어·배치 | C | **미반영** |
| 6 | 불명확성 없는 요구사항 바로 승인 | 백엔드 → 이후 FE | **미반영** (API 없음. 프론트에서 확정 상태를 만들지 않음) |

---

## 3. 이번에 구현한 것 (19단계)

작업은 `feat/fe-b-ux`에서 진행했다. 주요 파일:

- `frontend/src/layouts/AppLayout.vue`
- `frontend/src/router/index.js`
- `frontend/src/composables/useBreadcrumbLabels.js`
- `frontend/src/views/ProjectListView.vue`
- `frontend/src/views/DocumentListView.vue`
- `frontend/src/views/DocumentDetailView.vue`
- `frontend/src/components/common/statusLabels.js`

### 2.1 · breadcrumb (단계: ID fallback)

이름 조회가 실패해도 `프로젝트 #1`, `문서 #101`을 쓰지 않는다. 일반명 `프로젝트` / `문서`로만 떨어진다. URL의 ID는 그대로 둔다.

- router `prefix: '문서 #'` → `fallback: '문서'`
- `AppLayout.vue`에서 `#${id}` 이어 붙이기를 제거

문서 목록 breadcrumb의 프로젝트 칸은 조회 성공 시 **프로젝트명**을 보여 준다 (2.1 `Documents` → 현재 프로젝트명).

`Requirement` → `요구사항 검토`, `Preview` → `문서 미리보기`는 router meta에 이미 한글 라벨이 있다.  
문서 상세의 **`Preview 열기` 링크 문구는 바꾸지 않았다** (C가 인계서 4.7로 넣은 자리).

### 2.3 B 화면 (단계 6, 15 등)

- 프로젝트 목록: `Mock: ON/OFF` 제거, `#id` 제거, 이름·설명만 표시
- 문서 목록: `#{{ doc.id }}` 제거, 제목만 표시
- `TEXT` / `FILE` enum을 `직접 입력` / `PDF`로 표시. 등록 안내의 `sourceType:` 문구 제거
- 문서 상세: `문서 #{{ documentId }}` 제거. 제목은 문서명, 옆에는 분석 상태 배지만

### 3.1 (단계: 프로젝트 열기, 문서 목록 프로젝트 정보)

- 프로젝트 카드: 이름 + 설명 + **`프로젝트 열기 →`**. 카드 전체 클릭은 유지
- 문서 목록: `getProject(projectId)`로 프로젝트명·설명을 제목 영역에 표시. `프로젝트 #id` 부제 제거

### 3.3 (단계 8~14)

반영한 것:

- PDF 패널을 직접 입력보다 **위**로 이동
- 섹션/버튼명: **`PDF 업로드 및 분석`**
- 선택한 파일명·크기 표시 (`formatFileSize`)
- 제목이 비어 있을 때만 PDF 파일명으로 자동 입력 (이미 적은 제목은 덮지 않음)
- 업로드 후 `startDocumentAnalysis()` 호출 → 문서 상세로 이동
- 상세의 `restoreActiveAnalysis()`가 polling을 이어받음 (목록에 polling 중복 없음)
- `uploading` 동안 버튼·제목·파일 입력 잠금, 문구는 `업로드 및 분석 중…`

보류한 것:

- **PrimeVue FileUpload** (`package.json` / `main.js` 변경). 기본 `<input type="file">` 유지. C와 합의 후 진행

### 4.1 (단계 16~19)

- `AMBIGUOUS` · `CLARIFYING`을 목록 **위**로 정렬. 같은 그룹은 `sequenceNo` 오름차순
- **`sequenceNo`는 재번호 매기지 않음** (5번이 위에 와도 `요구사항 5`)
- 액션: 검토 필요 → `불명확성 확인 →`, 그 외 → `요구사항 보기 →`
- 식별 줄 `요구사항 N` + 상태 / 본문 `originalText` 시각적 분리. `REQ-001` 같은 임의 코드는 만들지 않음
- 왼쪽 빨간 강조선은 넣지 않음 (정렬 · Badge · 액션 문구로만 강조)

### 4.2 상태 라벨

`statusLabels.js` 요구사항 상태:

| enum | 표시 |
| --- | --- |
| EXTRACTED | 추출 완료 |
| AMBIGUOUS | 불명확성 발견 |
| CLARIFYING | 보완 답변 필요 |
| IN_REVIEW | 수정안 승인 대기 |
| CONFIRMED | 확정 완료 |

공용 파일이라 B 문서 상세뿐 아니라 C의 workflow · Preview 배지도 같이 바뀐다.

---

## 4. 추가로 작업해야 할 일

### 4.1 B가 이어서 할 일

**없음.** 3.2 직접 입력 기본값 제거는 반영했다.

- 제목·원문: `ref('')`
- `DEMO_CONTENT` import 삭제. Mock 시드/PDF 대체 원문으로는 `fixtures.js`에만 남김
- placeholder: `문서 제목을 입력하세요` / `요구사항 원문을 입력하세요`

B Must 기능(5.1~5.12)도 이전 작업에서 완료된 상태다. 남은 B 관련 항목은 4.2의 PrimeVue 합의 정도다.

### 4.2 C와 맞춘 뒤 B가 할 수 있는 일

**PrimeVue FileUpload (3.3)**

- 의존성 추가 시 `package.json`, `package-lock.json`, `main.js`가 바뀐다
- C에게: 「PDF 업로드 UX 때문에 PrimeVue를 넣을지」 합의
- 합의 후 구현 위치는 여전히 `DocumentListView.vue` (B)

**문서 상세 `Preview 열기` → `미리보기` (2.1)**

- 링크는 C가 넣은 자리. 문구만 바꿀지 C에게 확인하거나 C PR로 처리

### 4.3 C 화면 잔여 (`FE_Check.md` 2.1 · 2.3 · 5.1)

이번 브랜치에서 손대지 않았다.

| 파일 | 예시 |
| --- | --- |
| `RequirementWorkflowView.vue` | 부제 `요구사항 #{{ requirementId }}`(DB ID), `문제 #`, `수정안 #` |
| `PreviewView.vue` | fallback 제목 `Preview`, `문서 #`, `질문 #{{ id }}`, 하단 `조회 기준 버전`의 `요구사항 #{{ requirementId }}` |

요청서에 적힌 **남은 항목** 그대로다.

- 미리보기 하단 `조회 기준 버전`의 DB 저장번호를 `sequenceNo`로 바꾸거나 화면에서 제거
- 질문 ID → `확인 질문`, 승인 근거 → `근거 답변`, 수정안은 `1차 수정안`처럼 회차
- 화면 제목 `문서 미리보기`, 탭 `고객 질문서` / `개발팀용`
- 미확정 요구사항의 상태·버전을 오른쪽 정보 묶음으로 정렬

### 4.4 공통 디자인 (2.2)

- 투명 배경 `reqbridge-mark.png` favicon
- Apple touch icon
- 상단바 로고

인계서·명세에 디자인 소유가 없고, C도 스타일을 이번 범위가 아니라고 적어 두었다. 와이어프레임/디자인 담당과 별도 진행.

### 4.5 백엔드 연동 (6절)

- 열린 문제가 없는 `추출 완료` 요구사항의 **바로 승인** API가 없다
- FE에서 확정 상태를 임의로 만들지 않는다
- API가 오면 목록/상세에 `바로 승인`을 연결하고, 목록과 개발팀용 Preview를 다시 조회한다
- 계약은 `백엔드-추가-수정요청사항.md`를 따른다

### 4.6 Mock 5-1 (기능, 사용성과 별개)

C가 재논의한 항목: 새로 분석한 문서에 질문이 없어 workflow가 비는 문제.  
C 제안 A(분석 Mock이 1회차 질문을 만들고 `CLARIFYING`으로 전환). `extractRequirements`는 B 영역이라 **B 동의 후 C가 PR**. 이번 `FE_Check` 19단계와는 별개다.

---

## 5. FE_Check §7 검증 체크리스트 (현재 코드 기준)

B 화면만 보면:

- [x] 영문 화면명과 개발용 문구가 B 목록/상세에서 노출되지 않는다 (`Preview 열기` 제외)
- [ ] 브라우저 탭과 상단바에 투명 배경 ReqBridge 심볼이 표시된다 (2.2)
- [x] 프로젝트와 문서를 내부 ID가 아닌 이름으로 구분할 수 있다
- [x] 직접 입력 필드가 빈 값으로 시작한다 (3.2)
- [x] PDF 업로드가 직접 입력보다 먼저 보인다
- [x] 선택한 PDF의 파일명과 크기를 확인할 수 있다
- [x] 한 번의 액션으로 PDF 업로드와 분석이 이어진다
- [x] 불명확한 요구사항을 목록에서 바로 찾을 수 있다
- [x] 불명확 요구사항이 여러 개여도 각각 선택할 수 있다
- [x] 상태 문구만 보고 분석 중, 답변 필요와 승인 대기를 구분할 수 있다 (4.2 라벨)
- [ ] 질문·문제·작업·수정안의 내부 ID가 주요 정보로 노출되지 않는다 (**C 화면**)
- [ ] 고객 질문서와 개발팀용 결과의 목적을 이름만 보고 구분할 수 있다 (**C**)
- [ ] 개발팀용 미확정 요구사항의 상태와 버전이 오른쪽에 함께 정렬된다 (**C**)

---

## 6. C에게 전달할 때 짧은 메모

1. B는 `FE_Check.md`의 프로젝트·문서·문서 상세 사용성을 `feat/fe-b-ux`에 반영했다. workflow/Preview ID·미리보기 배치는 손대지 않았다.
2. `statusLabels.js` 요구사항 한글 라벨을 요청서 4.2대로 바꿨다. C 화면 배지 문구가 같이 바뀐다.
3. PrimeVue는 아직 넣지 않았다. PDF 업로드에 FileUpload를 쓸지 회신 부탁한다.
4. 문서 상세 `Preview 열기` 문구는 C 링크로 남겨 두었다. `미리보기`로 바꿀지는 C PR이 자연스럽다.
5. Mock 5-1(새 문서 분석 시 질문 생성)은 이번 사용성 PR과 별건이다. A안 동의 여부를 따로 회신하겠다.

---

## 7. B 다음 액션 (우선순위)

1. 이 브랜치를 PR로 `main`에 올리기 전, 로컬에서 `npm run dev:mock`으로 프로젝트 → PDF 업로드 및 분석 → 문서 상세 목록 정렬·액션 문구 확인
2. C에게 PrimeVue / `Preview 열기` / 5-1 회신
3. C 화면(2.3 잔여 · 5.1)은 C 브랜치에서 처리
