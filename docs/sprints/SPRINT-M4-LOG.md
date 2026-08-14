# Sprint M4 Log — 문자내역 잔여 항목

> **Sprint**: M4 · **Date**: 2026-08-14
> **Lead**: `team-leader` · **Previous**: [SPRINT-M3-LOG.md](SPRINT-M3-LOG.md)
> **Status**: **47 / 52 (90%)** · 코드로 닫을 수 있는 항목 **0건 남음**

---

## 1. 완료 항목 / Completed

| Task | Description | Requirements |
|------|-------------|--------------|
| M4-01 | `CsvExporter` — CSV 수식 주입 방어 + 이스케이프 | **FR-MSG-017**, NFR-SEC-PII-02 |
| M4-02 | `MessageHistoryService.export()` — 5,000건 상한, 별도 감사 액션 | FR-MSG-017, NFR-OPS-AUDIT |
| M4-03 | `MessageHistoryMapper.export` + `<sql id="rowProjection">` 공유 | FR-MSG-017 |
| M4-04 | `POST /api/message-history/export` — 첨부 응답, `no-store` | FR-MSG-017 |
| M4-05 | `exportMessageHistory()` + 화면 버튼 | FR-MSG-017, NFR-USE-01 |
| M4-06 | `CsvExporterDriver` — **30건 실행** | FR-MSG-017 |
| M4-07 | 내보내기 프론트 테스트 **6건 실행** | FR-MSG-017 |
| M4-08 | `qa/load/message-history-load.js` — k6 4 시나리오 | NFR-PERF-01/03 |
| M4-09 | `qa/sql/explain-message-history.sql` — 쿼리 계획 + AMB 검증 | NFR-PERF-04, AMB-M01/M02 |

## 2. FR-MSG-017 은 이식이 아니라 신규 기능이다 / Not a port

레거시 **화면 40 에는 내보내기가 없었다**. `*_spreadsheet_view.jsp` 는 화면 20·30 에만
존재한다. AMB-07("문자내역에 내보내기가 필요한가")은 그래서 열려 있었고, 명세 우선순위도
`Could` 였다. PM 지시("to completed")를 근거로 구현했으나 **범위 결정이 뒤집히면 가장 먼저
제거될 대상**이며, 그 사실을 컨트롤러 Javadoc 에 남겼다.

*Legacy screen 40 had no export at all — only screens 20 and 30 did. This is new functionality
built on the PM's instruction, and the first thing to remove if that scope call is reversed.*

**Excel 대신 CSV.** 레거시는 POI 3.9(2012년, 지원 종료)를 썼다. xls 생성을 위해 의존성을
추가하면 SBOM 과 취약점 스캔 범위가 함께 늘어난다. CSV 는 의존성이 0이고 모든
스프레드시트가 연다.

## 3. 내보내기는 조회와 다른 위험 등급이다 / Export is a different risk class

내보내기를 "결과를 파일로 주는 것"으로만 보면 세 가지를 놓친다. 세 가지 모두 처리했다.

| # | 위험 | 대응 |
|---|------|------|
| 1 | **CSV 수식 주입** — 스프레드시트는 `=`·`+`·`-`·`@`·탭·개행으로 시작하는 셀을 수식으로 해석한다. 발송 내역에는 외부 입력이 포함되므로 파일을 여는 사람의 PC 에서 수식이 실행될 수 있다 | 선행 아포스트로피로 무해화. **값은 변경하지 않는다** |
| 2 | **대량 반출 감사** — 한 페이지 열람과 수천 건 파일 반출은 감사 관점에서 같은 사건이 아니다 | `ACTION_MESSAGE_HISTORY_EXPORT` 를 조회와 **분리**. 거절도 감사 |
| 3 | **무한 반출** — CSV 를 문자열로 조립하므로 31일 전체 요청이 힙을 소진시킨다 | 5,000건 상한. **잘라내지 않고 거절** |

3번의 판단이 중요하다. **조용히 잘라낸 파일은 완전한 것처럼 보인다.** 감사·정산에 쓰이면
잘못된 결론을 만들고, 파일을 받은 사람은 그것을 알 수 없다. 그래서 거절 메시지에 실제
건수를 담아 사용자가 기간을 얼마나 좁혀야 하는지 알 수 있게 했다.

*A silently truncated file looks complete; used for reconciliation it produces wrong conclusions
its recipient cannot detect. Hence refusal, with the actual count in the message.*

### 조회와 내보내기가 어긋나지 않게 한 세 지점

| 계층 | 공유 방식 |
|------|----------|
| SQL | `<sql id="rowProjection">` — `masking()` 이 한쪽에만 남으면 **파일에 평문이 들어간다** |
| 서비스 | 같은 `rebuildWithInstitution()` 테넌트 경로. 별도 경로는 D1·D4 가 생긴 방식이다 |
| 프론트 | `buildQuery()` 하나 — 테스트로 두 요청 본문의 동일성을 검증 |

## 4. 발견·수정한 결함 / Defects found and fixed

| ID | Finding | Severity | Status |
|----|---------|----------|--------|
| SR-08 | **드라이버 어서션이 틀렸다.** 상태코드 `"3"` 을 `전송완료` 로 단정했으나 실제로는 `톡결과수신`(`전송완료` 는 코드 `2`) | LOW (테스트 결함) | ✅ FIXED — 어서션 수정 |
| SR-09 | **테스트 질의가 다시 모호했다.** `role="alert"` 요소가 둘(감싼 영역 + `ul.violations`)이어서 `findByRole` 이 실패 | LOW (테스트 결함) | ✅ FIXED — 위반 목록 직접 지목 |

**SR-08 은 드라이버가 제 역할을 한 사례다.** 코드가 아니라 내 어서션이 틀렸고, 실행이
없었다면 잘못된 기대치가 그대로 남았을 것이다.

**SR-09 는 SR-07 과 같은 유형의 세 번째 발생이다.** 세 번 모두 "화면에 같은 문자열·역할이
둘 이상 존재하는데 단수형 질의를 썼다"였다. 이것은 우연이 아니라 **이 화면의 구조적
특징**이다 — 안내문과 위반 메시지가 같은 문장이고(의도된 설계), 접근성을 위해 `role="alert"`
가 중첩되어 있다. 다음 화면 테스트는 처음부터 `within()`/`findAllByRole` 로 시작해야 한다.

## 5. 검증 — step [D]

| Check | Result |
|-------|--------|
| 백엔드 JDK-only 하네스 | ✅ **196 assertions** (12+29+20+32+48+25+**30**) + 20만 표본 |
| 프론트엔드 `tsc --noEmit` | ✅ 0 errors |
| **프론트엔드 `vitest run`** | ✅ **56/56** (5 파일, +6) |
| 프론트엔드 `vite build` | ✅ 187.22 kB (gzip 61.54 kB) |
| CI 정적 규칙 | ✅ MD5 0 / 자격증명 SHA-256 0 / `${}` 보간 0 / 하드코딩 0 |
| provenance | ✅ **58/58** Java |
| `mvn verify` | ❌ 미실행 (Maven 없음) |
| k6 부하 테스트 | ❌ 미실행 (기동 불가) |
| `EXPLAIN` | ❌ 미실행 (PostgreSQL 없음) |

### 번들 크기가 다시 배선을 증명했다

186.23 → **187.22 kB**. M3 에서 화면이 import 되지 않아 크기가 변하지 않았던 것을 잡았고,
이번에는 증가를 확인했다 — 내보내기 코드가 실제로 도달 가능하다는 뜻이다. **M3 회고의
개선 액션이 다음 sprint 에서 실제로 작동한 첫 사례다.**

### CsvExporterDriver 30건이 잡은 것

가장 중요한 것은 **DDE 페이로드** `=cmd|'/c calc'!A1` 다. 두 가지를 함께 확인한다:
무해화되었는가(`'=` 로 시작), 그리고 **값이 보존되었는가**. 마스킹이 아니라 무해화이므로
원본 내용은 남아야 한다. 순서 검증(`"=a,b"` → `"'=a,b"`)도 포함했다 — 이스케이프를 먼저
적용하면 따옴표 안쪽의 선행 문자를 놓친다.

## 6. 7 차원 자체 평가 — step [E] · 문자내역

| Dimension | Weight | Score | Δ vs M3 | Basis |
|-----------|--------|-------|---------|-------|
| 완성도 | 20% | **90** | +2 | 47/52. **코드로 닫을 수 있는 항목 0건** |
| 추적성 | 15% | **95** | — | 58/58 provenance |
| 보안 | 20% | **80** | +2 | CSV 주입 방어·반출 감사 추가. **SR-05 + AMB-M01/M02 미해결** |
| 성능 | 10% | **55** | +15 | 부하·EXPLAIN 스크립트 작성. **실행 증거는 여전히 0** |
| 가독성 | 15% | **93** | — | — |
| 표준 준수 | 10% | **95** | — | — |
| 테스트 커버리지 | 10% | **78** | +6 | 백엔드 103건 + 프론트 17건 (문자내역 몫) |

**가중 합계: 18.0 + 14.25 + 16.0 + 5.5 + 13.95 + 9.5 + 7.8 = 85.0 / 100**

임계 90 미달이다. 최저 차원은 **성능(55)** 이며, 원인은 코드가 아니라 **실행 환경 부재**다.
5회 반복 루프로 개선되지 않는 종류이므로 **PM Escalation** 한다(§7).

## 7. 잔여 5건 — 전부 환경·결재 대기 / The remaining five

| Requirement | Status | 필요한 것 |
|-------------|--------|----------|
| NFR-PERF-01, NFR-PERF-03 | **SPECIFIED_NOT_RUN** | k6 스크립트 작성 완료. **기동된 애플리케이션** |
| NFR-PERF-04 | **PARTIAL** | `EXPLAIN` 스크립트 작성 완료. **운영 데이터 + DBA** |
| NFR-OPS-AUDIT-02, CONST-LEGAL-02 | **PENDING_DBA** | 감사 테이블 DDL 검토·적용 |

> **NOT_STARTED 가 0건이 되었다.** 남은 5건은 모두 산출물이 존재하고 실행·결재만 남았다.
> 이것이 이 환경에서 도달 가능한 최종 상태다.

## 8. PM Escalation — 7차원 85.0 < 90

| # | Item | 차단하는 것 | 필요 조치 |
|---|------|------------|----------|
| 1 | **AMB-M01** — 사용자→이용기관 매핑 컬럼 | **테넌트 격리의 근거** | `explain-message-history.sql` §0 실행 |
| 2 | **AMB-M02** — 상세 11개 컬럼 존재 | 상세 쿼리 런타임 실패 가능 | 같은 스크립트 §0 실행 |
| 3 | **Maven + PostgreSQL + DDL + 키** | 성능 차원(55) · 잔여 5건 | 환경 제공 |
| 4 | **SR-05** — 하드코딩 AES 키 | `security-auditor` REJECT | `application-local.yml` 로 이동, 키 폐기 |
| 5 | **AMB-07** 사후 확인 | FR-MSG-017 존속 여부 | 신규 기능 승인 또는 제거 지시 |

> 1·2 는 `explain-message-history.sql` §0 **한 번 실행으로 둘 다 답이 나온다**. 스크립트를
> 그렇게 구성했다 — 성능 검토와 전제 검증을 한 파일에 넣은 이유다.

## 9. 문자내역 최종 / Final state

| | M1 | M2 | M3 | **M4** |
|---|---|---|---|---|
| IMPLEMENTED | 21 | 34 | 46 | **47 / 52 (90%)** |
| NOT_STARTED | 27 | 14 | 3 | **0** |
| 백엔드 실행 검증 | 0 | 48 | 73 | **103** |
| 프론트 테스트 | 0 | 0 | 11 | **17** |
| 7차원 | 61.0 | 74.3 | 82.1 | **85.0** |

**두 모듈 합계: 102 / 111 (92%) · 백엔드 196 assertions + 프론트 56 테스트 실행 ·
레거시 결함 19건 전부 대응 · 자체 발견 결함 9건(SR-01…09).**

---

**Sprint gate**

| Date | Approver | Comment | Status |
|------|----------|---------|--------|
| 2026-08-14 | PM | 7차원 **85.0 < 90** — 성능 차원이 환경 부재로 막혀 있어 반복으로 해소 불가 | **ESCALATED** |
| 2026-08-14 | `code-reviewer` | 프론트 APPROVE(56/56). CSV 주입 방어·투영 공유 양호. 백엔드 컴파일 이력 없음 | **PARTIAL APPROVE** |
| 2026-08-14 | `security-auditor` | CSV 수식 주입 방어와 반출 감사 분리 확인. **AMB-M01 미확인 + SR-05 로 REJECT 유지** | **REJECT** |
