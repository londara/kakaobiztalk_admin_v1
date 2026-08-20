# 추적성 검증 — Sprint R1 / T1 / T2

> **대상 커밋**: `bdb6b1d` (머지 `f60ac13` 이후) · **검증일**: 2026-08-20
> **에이전트**: trace-mapper (Skill 05 지원)
> **입력**: `mapping/trace/requirements-trace-biztalk.csv` (305행) · `docs/requirements/requirements-matrix.csv` (434행)
> **검증 방식**: 파일 실측. 클래스·이너클래스·메서드·XML `id`·프런트 export·테스트 클래스/중첩클래스/메서드를 각각 소스에서 조회하여 대조.

---

## 0. 요약

| 범주 | 건수 | 판정 |
|------|-----:|------|
| 1. 미추적 요구사항 (매트릭스 O / 추적표 X) | **3** (+ 결정 ID 2건) | FAIL |
| 2. 깨진 참조 (실재하지 않는 아티팩트) | **0** (하드) / **6** (STALE·형식) | 조건부 PASS |
| 3. 상태 불일치 | **5** | FAIL |
| 4. 고아 코드 (요구사항 미연결 프로덕션 파일) | **16** | FAIL |
| 5. 머지 병합 손실 | **0** | **PASS** |

**Orphan 0 룰 위반**: 요구사항측 3건, 코드측 16건. Sprint 종료 매트릭스 갱신 의무 미이행.

---

## 1. 미추적 요구사항 — 매트릭스에 있으나 추적표에 없음

매트릭스의 R/T 계열 REQ_ID는 **118건**. 그중 추적표에 단 한 행도 없는 것 **3건**.

| REQ_ID | type | 요구 내용(요약) | 매트릭스 status | 추적표 | 판정 |
|--------|------|------------------|-----------------|--------|------|
| `NFR-OPS-AUDIT-T01` | Non-Functional | 조회·상세열람·내보내기 이벤트를 법정 보존기간 동안 위변조 탐지 가능하게 보존 | `BLOCKED-OI-02` | **없음** | **HIGH** — Must 등급 + 규제(전자금융/ISMS-P) 대상. 형제 요구 `NFR-OPS-AUDIT-R01`은 추적행이 있는데 T 계열만 누락 |
| `CONST-LEGAL-T01` | Constraint | 수신번호·발신번호는 개인정보 — 전 렌더링 마스킹, 저장 시 보호, 전 접근 감사 | `SPECIFIED` | **없음** | **HIGH** — Must. 인접 요구 `NFR-SEC-PII-T01`만 추적되어 있어, 감사 시 "마스킹은 있으나 접근감사 근거 없음" 상태 |
| `CONST-DATA-T02` | Constraint | DDL 금지 (`CONST-DATA-01` 상속) | `SPECIFIED` | **없음** | MEDIUM — R 계열 대응 `CONST-DATA-R01/R02`는 추적행 존재. 대칭 누락 |

### 1-b. 코드가 인용하지만 어느 문서에도 REQ_ID로 존재하지 않는 결정 ID

| ID | 코드 내 인용 위치 | 매트릭스 | 추적표 |
|----|-------------------|----------|--------|
| `CONFLICT-R01` | `PrincipalScope`, `ReportScope`, `ReportScopeTest`, `SPRINT-R1-LOG §1 (R1-03)` | REQ_ID 없음 (본문 인용만) | **0행** |
| `CONFLICT-T01` | `PrincipalScope`, `TalkHistoryController`, `TalkHistoryRoute.tsx`, `TalkHistoryAuthorizationTest`, `TalkExportControllerTest`, `TalkHistoryServiceTest` | REQ_ID 없음 (`FR-AZ-T02`·`NFR-SEC-AUTHZ-T01`의 source 컬럼에만 등장) | **0행** |

A 계열은 `CONFLICT-A01`/`CONFLICT-A02`가 추적표에 `decision` 타입 행으로 정식 등록되어 있다. R/T 계열만 이 관례가 적용되지 않았다. 두 ID 모두 **인가 규칙의 PM 결정**이므로, 감사 시 "운영자 전용 결정의 근거와 구현 위치"를 추적표만으로는 답할 수 없다.

### 1-c. 역방향

추적표 R/T 계열 REQ_ID 116건 중 매트릭스에 없는 것은 `CR-T01` 1건. 이는 코드리뷰 결함 ID(요구사항 아님)이므로 결함 아님. 단 결함 ID와 요구 ID가 같은 컬럼을 공유하는 점은 기록해 둔다.

---

## 2. 깨진 참조

### 2-a. 하드 참조 오류 — **0건**

전수 확인 결과 (R/T 계열 135행 / 고유 아티팩트 85개):

| 대상 | 확인 방법 | 결과 |
|------|-----------|------|
| Java 클래스 63개 | `src/main/java/**` 실파일 대조 | 전건 존재 |
| 이너클래스·메서드 참조 | 소스 본문 심볼 조회 | 전건 존재 |
| MyBatis XML 3개 / `id` 8종 | `ApiAggregateMapper.xml`·`TalkHistoryMapper.xml`·`TalkMessageMapper.xml`의 `id="…"` 대조 | 전건 존재 |
| 프런트 파일 5개 / export 1개 | `talkDetailApi.ts#exportTalkHistory` 등 | 전건 존재 |
| 테스트 클래스 22종 / 중첩·메서드 53종 | `src/test/**` + `*.test.tsx` 대조 | 전건 존재 |

### 2-b. STALE 참조 — 6건 (실재하지만 가리키는 곳이 틀렸거나 형식 결함)

| # | REQ_ID | 추적표 참조 | 실제 | 판정 |
|---|--------|-------------|------|------|
| B-1 | `FR-AZ-R03` | `biztalk.domain.ReportScope#resolve` | T1-04에서 규칙이 `common.tenant.PrincipalScope#resolve`로 이동. 현 `ReportScope#resolve`는 **3줄 위임 셸** | **STALE** — 인가 규칙 추적이 로직 없는 별칭을 가리킴 |
| B-2 | `FR-AZ-R04` | `biztalk.domain.ReportScope` | 동상 | **STALE** |
| B-3 | `NFR-SEC-TENANT-R01` | `biztalk.domain.ReportScope` | 동상. 테넌트 격리(Must, Critical 위협 T-R10)의 유일한 추적점이 위임 셸 | **STALE / HIGH** |
| B-4 | `FR-AZ-T03` | `common.tenant.PrincipalScope#resolve` / test `ReportScopeTest` | 아티팩트는 정확하나 **테스트가 보고서 슬라이스 클래스**(`biztalk/domain/ReportScopeTest.java`). `ReportScope` 폐기 시 T 계열 추적이 함께 끊김 | 교차 슬라이스 의존 |
| B-5 | `FR-AZ-T02` | `…TalkHistoryController#query ` | 아티팩트 필드 **후행 공백**. 문자열 대조 기반 자동화 파이프라인에서 미스매치 | 형식 결함 |
| B-6 | `NFR-PERF-T01` | test `TalkHistoryLoadTest$PagingCost;$QueryStructure` | `$QueryStructure`는 클래스 접두어가 생략된 이어쓰기. 단독으로는 해석 불가 (실체는 `TalkHistoryLoadTest$QueryStructure`, 존재함) | 형식 결함 |

> **B-1~B-3 배경**: `ReportScope`는 의도적으로 남긴 위임 별칭이며, 그 Javadoc이 "G1 통과 인가 테스트를 한 줄도 고치지 않는 것이 T1-04의 종료 조건"이라고 명시한다. 즉 **코드 결정은 옳다**. 결함은 추적표가 T1-04를 반영하지 않은 것이다. 규칙이 사는 곳(`PrincipalScope`)과 서명이 사는 곳(`ReportScope`) 둘 다를 가리키도록 행을 분리해야 한다.

---

## 3. 상태 불일치

| # | REQ_ID | 추적표 | 매트릭스 | 스프린트 로그 | 판정 |
|---|--------|--------|----------|----------------|------|
| S-1 | `CONST-DATA-R02` | `-,review,-,R1,`**`IMPLEMENTED`** | `OPEN-CONFLICT-R02` | `questions-log.md §433` — "CONFLICT-R02 — **dissolved, not resolved**" | **HIGH** — 아티팩트 `-`, 테스트 `-` 인데 IMPLEMENTED. 근거 0건. 매트릭스는 아직 OPEN. 세 문서가 서로 다른 말을 함 |
| S-2 | `NFR-OPS-AUDIT-R01` | `ReportService#recordRead`, `IMPLEMENTED` | `BLOCKED-OI-02` | `questions-log.md:392` — "OI-02(감사 보존기간) 여전히 open, NFR-OPS-AUDIT-R01 차단" | **HIGH** — 미해결 오픈이슈로 차단된 요구가 IMPLEMENTED. 형제 `NFR-OPS-AUDIT-T01`은 아예 추적행 없음(§1). 동일 요구군이 슬라이스별로 3가지로 처리됨 |
| S-3 | `FR-TLKD-009` | `IMPLEMENTED` | `BLOCKED-AMB-T04` | `SPRINT-T2-LOG §4` — "**FR-TLKD-009 is now `IMPLEMENTED`**" (T1 로그는 BLOCKED 유지였음) | **매트릭스 STALE** — 추적표가 옳고 매트릭스가 T2를 미반영. Sprint 종료 갱신 의무 미이행의 직접 증거 |
| S-4 | `FR-AZ-T04` | **두 행이 모순** — `-,code,-,T2,PLANNED` / `TalkMessageDetailKey,code,TalkMessageMapperIntegrationTest$Detail#crossInstitutionKeyReturnsNothing,T2,IMPLEMENTED` | `SPECIFIED` | `SPRINT-T2-LOG §2` — D-T5(Critical) 종결, 실행 검증됨 | **MEDIUM** — 합집합 병합이 낡은 PLANNED 행을 살려둠. R/T 전체에서 유일한 status 모순 REQ. PLANNED 행 삭제 필요 |
| S-5 | `FR-AZ-R01`·`FR-AZ-R02`·`FR-RPT-013` | `ReportController` 계열, 전부 `IMPLEMENTED`, test `-` | `SPECIFIED` | `SPRINT-R1-LOG §7 DoD` — R1-12 엔드포인트 스위트 미이행 | **HIGH** — `ReportController`를 참조하는 테스트가 **저장소 전체에 0개**. 그런데 T1 로그 §4는 같은 상황을 두고 "*An authorization control with no test asserting the refusal is a claim, not a control*"이라며 T 계열을 PARTIAL로 낮췄다. **동일 기준이 R 계열에 적용되지 않았다.** 게다가 T1 Addendum A2가 `@WebMvcTest`로 DB 없이 가능함을 입증했고 T2가 `TalkExportControllerTest`로 실행했으므로, R1-12의 차단 사유는 이미 소멸했다 |

### 3-b. 근거 없는 IMPLEMENTED (test = `-`)

R/T 계열 `IMPLEMENTED` 107행 중 **22행(20.6%)이 test `-`**. 위 S-5의 3행 외 주요 항목:

| REQ_ID | 아티팩트 | 비고 |
|--------|----------|------|
| `FR-AZ-R05` | `AuditEvent#ACTION_REPORT_QUERY` | 감사 이벤트 상수, 테스트 없음 |
| `FR-AZ-T01` | `TalkHistoryController` | `TalkHistoryAuthorizationTest`가 존재하나 `FR-AZ-T02`에만 연결 |
| `FR-AZ-T05` | `TalkHistoryService#search` | 동일 REQ의 다른 행에는 `TalkHistoryServiceTest$Audit`가 붙어 있음 — 한쪽만 갱신 |
| `FR-RPT-005/006/011` | `ApiAggregateMapper.xml#findPage` | `AggregateMapperXmlTest`(23 assertion)가 R1에서 작성되었으나 어느 행에도 연결 안 됨 |
| `FR-TLKX-010` | `TalkExportController` | T2 Addendum B2의 `TalkExportControllerTest` 12건이 있으나 미연결 |
| `FR-TLKD-003`·`FR-TLKM-007`·`FR-TLKM-008` | 상세 패널 `.tsx` | `TalkHistoryPage.test.tsx`가 존재 |

즉 다수는 **테스트가 실재하는데 추적표 test 컬럼이 비어 있는** 갱신 누락이다 (특히 `AggregateMapperXmlTest`, `TalkExportControllerTest`는 추적표 어디에서도 인용되지 않음).

### 3-c. 진실한 상태 (검증 통과)

| REQ_ID | 상태 | 근거 |
|--------|------|------|
| `NFR-SEC-AUTHZ-R01`, `NFR-SEC-TENANT-R01` | `PARTIAL` | `SPRINT-R1-LOG §2` R1-12 이월, §7 DoD 미체크 — 일치 |
| `NFR-PERF-R01`, `NFR-PERF-R02` | `UNVERIFIED` | `SPRINT-R1-LOG §6` "성능 80 — NFR-PERF-R01/R02 are unmeasured" — 일치 |
| `NFR-PERF-T01`, `NFR-PERF-T02` | `PARTIAL-DEV-HARDWARE` | `SPRINT-T2-LOG` Addendum B1 "These milliseconds are not asserted… headroom on a laptop is not a measurement of production" — 일치. 상태값 명명도 정확 |
| `NFR-SEC-PII-T01` | `PARTIAL` | `SPRINT-T2-LOG §3.2` "what the real `masking()` returns is still not verified" — 일치 |
| `NFR-COMPAT-T01` | `PARTIAL` | 매트릭스 verification이 "Manual verification per target" — 자동 검증 불가 항목, 일치 |
| `FR-RPTS-002` | `DESCOPED` | 매트릭스 `DESCOPED` + `REQUIREMENTS-SPEC-REPORT.md:169` PM 2026-08-19 결정 — 3자 일치 |
| `CR-T01` | `FIXED` | `SPRINT-T1-LOG` Addendum A4 — 일치 |
| `FR-RPTX-*` 13건 | `PLANNED`/`DEFERRED` (R2) | R2 미실행. 정당 |

---

## 4. 고아 코드 — R1/T1/T2가 추가했으나 추적표에 한 번도 등장하지 않는 프로덕션 파일

머지(`0a987dd`→`HEAD`)로 추가된 R/T 계열 프로덕션 파일 65개 중 **16개**가 추적표 전체(305행)에서 문자열조차 등장하지 않는다.

**중요**: 아래 16개 **전부가 소스에 `// req:` 마커를 보유**하고 있다. 즉 코드는 자기 요구사항을 선언했는데 추적표가 받아적지 않았다. 코드 변경 시 영향도 분석을 추적표 기준으로 수행하면 이 파일들은 통째로 누락된다.

| # | 파일 | 코드가 선언한 `// req:` | 영향 |
|---|------|--------------------------|------|
| O-1 | `src/main/java/com/webcash/iris/biztalk/api/TalkDetailController.java` | `FR-AZ-T02/T03/T04`, `FR-TLKD-001/002/003/006/007`, `FR-TLKM-001`, `NFR-USE-T01` | **HIGH** — 화면 31·32 드릴다운의 **HTTP 진입점 전체**. `FR-TLKD-*`·`FR-TLKM-*` 17개 요구가 서비스·매퍼·패널에만 연결되고 컨트롤러에는 한 행도 없음 |
| O-2 | `src/main/java/com/webcash/iris/biztalk/infra/db/bulk/BulkAggregateMapper.java` | `FR-RPTS-001`, `FR-RPTS-005`, `ADR-RPT-021` | **HIGH** — 제2 데이터소스(대량발송)의 매퍼. `FR-RPTS-001`은 `AggregateMapper`·`ReportDataSourceConfig`만 가리켜 **두 소스 중 하나가 추적에서 통째로 빠짐** |
| O-3 | `src/main/resources/mybatis/mapper/biztalk/bulk/BulkAggregateMapper.xml` | `FR-RPT-005/006/009/011/012/013`, `FR-RPTS-001/004`, `ADR-RPT-021/022` | **HIGH** — 위와 동일. `ApiAggregateMapper.xml`만 추적됨 |
| O-4 | `src/main/java/com/webcash/iris/biztalk/domain/TalkHistoryCriteria.java` | `FR-AZ-T05`, `FR-TLK-001/002/005/009/010/014` | HIGH — T2 목록↔내보내기 동치성(D-T1, Critical)의 **공유 기준 객체** |
| O-5 | `src/main/java/com/webcash/iris/biztalk/domain/ReportCriteria.java` | `FR-RPT-002/005`, `FR-AZ-R03`, `FR-RPTS-002` | HIGH — 보고서 슬라이스 대응물. `FR-AZ-R03` 인가 경로의 일부 |
| O-6 | `src/main/java/com/webcash/iris/biztalk/domain/TalkTransactionKey.java` | `FR-AZ-T03/T05`, `FR-TLKD-001/009`, `CONST-BIZ-T01` | HIGH — 기관 한정 키(D-T5 Critical 대응) |
| O-7 | `src/main/java/com/webcash/iris/biztalk/api/TalkMessageResponse.java` | `FR-TLKD-001/004/006/007/008`, `NFR-SEC-PII-T01` | MEDIUM — PII 노출면 |
| O-8 | `src/main/java/com/webcash/iris/biztalk/api/TalkMessageDetailResponse.java` | `FR-TLKM-001/002/005/007`, `NFR-SEC-PII-T01` | MEDIUM — PII 노출면 |
| O-9 | `src/main/java/com/webcash/iris/biztalk/config/TalkHistoryConfig.java` | `FR-TLK-002`, `FR-TLK-013`, `ADR-TLK-024` | MEDIUM — API 허용목록 설정 |
| O-10 | `src/main/java/com/webcash/iris/biztalk/domain/AggregateRow.java` | `FR-RPT-006/009/012`, `FR-RPTS-001/003` | MEDIUM |
| O-11 | `src/main/java/com/webcash/iris/biztalk/domain/TalkChannel.java` | `FR-TLKD-004`, `FR-TLKM-006`, `ADR-TLK-026` | MEDIUM |
| O-12 | `src/main/java/com/webcash/iris/biztalk/domain/MessageChannel.java` | `FR-RPT-009`, `CONST-BIZ-R01` | LOW |
| O-13 | `src/main/frontend/src/api/reportApi.ts` | `FR-RPT-001/005/007/008/009/013/014/015`, `FR-RPTS-002`, `FR-RPTX-011`, `NFR-USE-R01` | MEDIUM — R1 로그 §4.3이 여기서 발견된 결함을 기록했는데 추적행 없음 |
| O-14 | `src/main/frontend/src/api/talkHistoryApi.ts` | `FR-TLK-001/002/003/004/005/012/013/014/015` | MEDIUM |
| O-15 | `src/main/frontend/src/app/routes/ReportRoute.tsx` | `FR-AZ-R01`, `FR-AZ-R03`, `FR-AZ-R04` | MEDIUM — 라우트 수준 인가 |
| O-16 | `src/main/frontend/src/app/routes/TalkHistoryRoute.tsx` | `FR-AZ-T01`, `FR-AZ-T02`, `CONFLICT-T01` | MEDIUM — `CONFLICT-T01`의 유일한 UI 구현 위치 |

### 4-b. 추적표에 인용되지 않은 테스트 자산

프로덕션 코드는 아니지만 같은 갱신 누락:

| 테스트 | 규모 | 상태 |
|--------|------|------|
| `src/test/java/com/webcash/iris/biztalk/infra/db/AggregateMapperXmlTest.java` | 23 assertion (R1 §4.3a 신설) | 추적표 인용 **0** |
| `src/test/java/com/webcash/iris/biztalk/domain/TalkApiReconciliationTest.java` | 7 tests (T1 A3) | 추적표 인용 **0** |
| `src/main/frontend/src/features/biztalk/ReportPage.test.tsx` | 15 tests | `FR-RPTS-002`(DESCOPED), `FR-RPT-014` 2행만 |

---

## 5. 머지 병합 손실 점검 — **PASS**

머지 `f60ac13`의 두 부모: ours `0acfb39`, theirs `f8e7d47`, merge-base `0a987dd`.

### 5-a. 행 수 보존

| 파일 | base | ours | theirs | ours 증분 | theirs 증분 | 기대 합집합 | 실제 | 판정 |
|------|-----:|-----:|-------:|----------:|------------:|------------:|-----:|------|
| `mapping/trace/requirements-trace-biztalk.csv` | 60 | 169 | 195 | **+109** | **+135** | 304 | **304** | PASS |
| `docs/requirements/requirements-matrix.csv` | 235 | 315 | 353 | +80 | +118 | 433 | **433** | PASS |

(데이터 행 기준, 헤더 제외. ours 109 / theirs 135 라는 사전 정보와 정확히 일치.)

### 5-b. 내용 대조 (행 단위 집합 연산)

| 검사 | 추적표 | 매트릭스 |
|------|--------|----------|
| ours에 있으나 병합본에 없는 행 | **0** | **0** |
| theirs에 있으나 병합본에 없는 행 | **0** | **0** |
| 어느 쪽에도 없는데 병합본에만 있는 행 | **0** | **0** |
| 완전중복 행 | **0** | **0** |
| 중복 REQ_ID | — (다대다 정상) | **0** |
| 잔존 충돌 마커 (`<<<<<<<`/`>>>>>>>`) | **0** | **0** |
| 필드 수 이상 행 (≠6 / ≠10) | **0** | **0** |

**병합 자체는 무손실 합집합으로 정확히 수행되었다.** 저장소 전체(`node_modules` 제외)에도 잔존 충돌 마커 없음.

### 5-c. 다만 — 합집합의 부작용 1건

무손실 합집합은 **모순되는 행도 함께 보존한다.** `FR-AZ-T04`가 그 사례로, ours의 낡은 `PLANNED` 행과 theirs의 `IMPLEMENTED` 행이 나란히 살아남았다 (§3 S-4). 합집합 병합 후에는 **REQ_ID별 status 유일성 검사**가 반드시 뒤따라야 한다. R/T 계열 전체에서 이 모순은 1건뿐이므로 병합 판단 자체는 옳았다.

---

## 6. 권고 조치

### 즉시 (G3 게이트 전)

| # | 조치 | 대상 |
|---|------|------|
| A-1 | `FR-AZ-T04`의 `PLANNED` 행 삭제 | 추적표 |
| A-2 | `CONST-DATA-R02` — 아티팩트·테스트 없는 `IMPLEMENTED` 철회. `questions-log §433`(CONFLICT-R02 dissolved)을 근거로 매트릭스 status도 `OPEN-CONFLICT-R02` → 갱신 | 양쪽 |
| A-3 | `NFR-OPS-AUDIT-R01` `IMPLEMENTED` → `PARTIAL` (OI-02 미해결). `NFR-OPS-AUDIT-T01` 추적행 신규 등록 (동일 상태) | 양쪽 |
| A-4 | `FR-AZ-R01`/`R02`/`FR-RPT-013` `IMPLEMENTED` → `PARTIAL`. `ReportController` 테스트 0개 사실 반영. T1 A2가 입증한 `@WebMvcTest` 경로로 R1-12 재개 가능함을 Leader에 보고 | 추적표 + Leader 알람 |
| A-5 | `CONST-LEGAL-T01`, `CONST-DATA-T02` 추적행 신규 등록 | 추적표 |

### Sprint 갱신 의무 (T3 / R2 착수 전)

| # | 조치 |
|---|------|
| B-1 | 고아 파일 16건에 대해, 각 파일의 `// req:` 마커를 그대로 추적행으로 승격. 특히 O-1 `TalkDetailController`, O-2·O-3 `bulk/BulkAggregateMapper` (제2 데이터소스 전체 누락) |
| B-2 | `ReportScope` 참조 3행(B-1~B-3)을 `PrincipalScope`(규칙) + `ReportScope`(서명 별칭) 2행으로 분리. T1-04를 추적표에 반영 |
| B-3 | `FR-TLKD-009` 매트릭스 status를 `BLOCKED-AMB-T04` → T2 결과 반영 |
| B-4 | 실재하는데 미연결된 테스트 자산 연결: `AggregateMapperXmlTest`(23), `TalkExportControllerTest`(12), `TalkApiReconciliationTest`(7), `TalkHistoryAuthorizationTest`(7) |
| B-5 | `CONFLICT-R01`/`CONFLICT-T01`을 A 계열 관례(`decision` 타입 행)에 맞춰 등록 |
| B-6 | 후행 공백(`TalkHistoryController#query `) 및 `$QueryStructure` 축약 표기 정정 |

### 프로세스

| # | 조치 |
|---|------|
| C-1 | 합집합 병합 후 **REQ_ID별 status 유일성 검사**를 머지 절차에 추가 |
| C-2 | Sprint 종료 시 **매트릭스와 추적표를 동시 갱신**. 현재 `FR-TLKD-009`(추적표만 갱신)와 `NFR-OPS-AUDIT-R01`(매트릭스만 갱신)이 서로 반대 방향으로 어긋나 있다 |
| C-3 | `IMPLEMENTED` + test `-` 조합을 CI에서 경고 처리 (현재 22행 / 20.6%) |

---

## 부록 — 검증 커버리지

| 항목 | 수치 |
|------|-----:|
| 추적표 총 데이터 행 | 304 |
| 그중 R/T 계열 | 135 |
| R/T 고유 REQ_ID | 116 |
| 매트릭스 R/T 요구사항 | 118 |
| 실측 대조한 고유 아티팩트 | 85 |
| 실측 대조한 테스트 심볼 | 75 (클래스 22 + 중첩·메서드 53) |
| 검사한 신규 프로덕션 파일 | 65 |
