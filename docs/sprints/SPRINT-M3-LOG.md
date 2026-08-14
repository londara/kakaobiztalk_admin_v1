# Sprint M3 Log — 문자내역 완료

> **Sprint**: M3 · **Date**: 2026-08-14
> **Lead**: `team-leader` · **Previous**: [SPRINT-M2-LOG.md](SPRINT-M2-LOG.md)
> **Status**: **46 / 52 (88%) · 코드로 닫을 수 있는 항목 전부 완료**

---

## 1. 완료 항목 / Completed

| Task | Description | Requirements |
|------|-------------|--------------|
| M3-01 | `MessageDetail` — **19개 필드 전부** | **FR-MSGD-004**, fixes **D9** |
| M3-02 | `MessageDetailKey` — 4-way 라우팅 키, 미인식 값 거절 | FR-MSGD-002/003 |
| M3-03 | `MessageDetailMapper` + XML — 4개 테이블 쌍, `YYYY` 포맷 | FR-MSGD-002/005, fixes **D5** |
| M3-04 | `MessageDetailService` — 테넌트 소유 검증, 열거 방지 | **FR-MSGD-001/008**, TM-009 |
| M3-05 | `MessageDetailController` — 404 로 없음/권한없음 통합 | FR-MSGD-008 |
| M3-06 | `InstitutionController` + 매퍼 — **운영자 전용** | **FR-TEN-004**, TM-011 |
| M3-07 | `MessageHistoryPage` — 검색 폼 + 12컬럼 그리드 + 서버 페이징 | FR-MSG-002/004/005/007/014, NFR-USE-01 |
| M3-08 | `MessageDetailPanel` — 19필드, 조건부 섹션 | FR-MSGD-004/006/007 |
| M3-09 | `App.tsx` 배선 — 인증 후 문자내역 진입 | FR-MSG-001 |
| M3-10 | `ServiceWindow` — 요일별 시간대 게이팅 | **NFR-OPS-TIME**, BR-003 |
| M3-11 | `MessageHistoryPage.test.tsx` — 11건 실행 | FR-MSG-004/005/007/009 |
| M3-12 | `ServiceWindowDriver` — 25건 실행 | NFR-OPS-TIME |
| M3-13 | 문자내역 CSS — 표 스크롤·sticky 헤더·sr-only | NFR-COMPAT-01 |

**신규 파일 10개 · Java 56개(biztalk 16) · TS/TSX 16개**

## 2. 배선 누락을 빌드 크기로 잡았다 / A wiring gap caught by bundle size

React 화면을 작성하고 빌드했더니 번들 크기가 **177.08 kB 그대로**였다. 새 화면이
어디에서도 import 되지 않았다는 뜻 — 즉 **도달 불가 코드**였다. `App.tsx` 에 연결한 뒤
186.23 kB 로 증가했다.

*The bundle stayed at exactly 177.08 kB after adding two screens, meaning nothing imported
them — unreachable code. Wiring them into `App.tsx` moved it to 186.23 kB.*

이 프로젝트에서 <b>세 번째</b> 같은 유형이다: 로그인 L4 의 `PasswordPolicy`(호출자 없음),
문자내역 M1 의 SQL(매퍼 인터페이스 없음), 그리고 이번 프론트엔드. **"작성했다"와
"연결했다"는 다른 사건이며, 후자를 확인하는 관찰 가능한 지표가 필요하다** — 백엔드는
호출자 grep, 프론트엔드는 번들 크기가 그 역할을 했다.

## 3. 발견·수정한 결함 / Defects found and fixed

| ID | Finding | Severity | Status |
|----|---------|----------|--------|
| SR-07 | **테스트 질의가 모호했다.** `getByText('전송완료')` 가 상태 `<select>` 의 `<option>` 과 표 셀 양쪽에 일치했고, 기간 상한 위반 메시지가 field-help 안내문과 <b>같은 문자열</b>이어서 역시 중복 일치했다 | LOW (테스트 결함) | ✅ FIXED — `within()` 으로 범위 한정 |

**SR-07 은 코드 결함이 아니라 테스트 결함이다.** 다만 두 번째 케이스는 의도된 설계를
드러낸다 — 안내문("조회 기간은 최대 31일까지 가능합니다")과 위반 메시지가 동일한 문장인
것은 우연이 아니라 <b>같은 규칙을 사전과 사후에 각각 알리는 것</b>이다. 테스트는 그
중복을 인지하고 범위를 좁혀야 한다.

## 4. 검증 — step [D]

| Check | Result |
|-------|--------|
| 백엔드 JDK-only 하네스 | ✅ **166 assertions** (12+29+20+32+48+25) + 20만 표본 |
| 프론트엔드 `tsc --noEmit` | ✅ 0 errors |
| 프론트엔드 `vite build` | ✅ 186.23 kB (gzip 61.22 kB) |
| **프론트엔드 `vitest run`** | ✅ **50/50** (5 파일) |
| CI 정적 규칙 6건 | ✅ clean |
| provenance | ✅ 56/56 |
| `mvn verify` | ❌ 미실행 |

### ServiceWindow 25건이 잡은 것 / What the ServiceWindow assertions caught

가장 중요한 검증은 **레거시 `endTm=240000` 관용구**다. `LocalTime` 은 24시를 표현할 수
없어 자정으로 정규화되는데, 이를 "폭 0인 창"으로 읽으면 `000000~240000` 설정이
<b>모든 요청을 거절</b>한다 — 그리고 레거시 WSVC 설정 <b>전체가 그 형태</b>다.

실행으로 확인: `000000~240000` 이 00:00 · 12:00 · 23:59 모두 허용. 그 밖에도
경계(09:00 포함 / 18:00 제외), 요일별 창, 공휴일 우선순위, 일요일 미설정 시 평일 대체,
설정 누락 시 허용(장애가 아니라 설정 오류로 처리)을 검증했다.

*The critical case is the legacy `endTm=240000` idiom: read as a zero-width window it would
refuse everything, and every legacy WSVC configuration has that shape.*

## 5. 9개 레거시 결함 최종 상태 / Final state of the nine legacy defects

| Defect | 내용 | 대응 | 검증 |
|--------|------|------|------|
| D1 | 목록 서비스 미인증(`<login>N</login>`) | `SecurityConfig` 기본 인증 필수 | 구조적 |
| D2 | `to_char(MSGKEY,'9')` → 메시지키 검색 항상 0건 | 숫자 비교 | SQL 델타 주석 |
| D3 | 발신/수신 라벨이 컬럼과 반대 | 필드명이 컬럼 의미 반영 | ✅ 프론트 테스트 |
| D4 | `PHONE`·`RESULT_CD` 검색이 무동작 | 조건 생성 | SQL 델타 주석 |
| D5 | `YYYYY` → 5자리 연도 (4개 중 3개) | `YYYY` | SQL 델타 주석 |
| D6 | 8개 분기 중 1개만 inclusive 경계 | 전부 통일 | SQL 델타 주석 |
| D7 | 서버 페이징 주석 처리 | LIMIT/OFFSET + COUNT | ✅ 프론트 테스트 |
| D8 | 시각만 비교 → 정상 다일 범위 거절 | 날짜+시각 비교 | ✅ 48건 실행 |
| D9 | 19필드 선언, 8필드 반환 | 19필드 전부 | 상세 패널 |

**9건 전부 대응 완료.** 다만 SQL 델타 4건(D2·D4·D5·D6)은 <b>실행 검증되지 않았다</b> —
DB 없이 SQL 을 실행할 수 없다. `-- FIX Dn:` 주석으로 리뷰 가능하게 남겼을 뿐이다.

## 6. 7 차원 자체 평가 — step [E] · 문자내역

| Dimension | Weight | Score | Δ vs M2 | Basis |
|-----------|--------|-------|---------|-------|
| 완성도 | 20% | **88** | +25 | 46/52. 잔여 6건 중 5건이 DB·기동 의존 |
| 추적성 | 15% | **95** | — | 56/56 provenance |
| 보안 | 20% | **78** | — | 테넌트 격리 완료(목록+상세). **SR-05 + AMB-M01/M02 미해결** |
| 성능 | 10% | **40** | +5 | 페이징·상한 구현, 부하 테스트 불가 |
| 가독성 | 15% | **93** | — | SQL 델타 주석, D3 대응표 |
| 표준 준수 | 10% | **95** | — | — |
| 테스트 커버리지 | 10% | **72** | +14 | 백엔드 73건 + 프론트 11건 실행 |

**가중 합계: 17.6 + 14.25 + 15.6 + 4.0 + 13.95 + 9.5 + 7.2 = 82.1 / 100**

## 7. 잔여 6건 — 코드로 닫히지 않는다 / The remaining six

| Requirement | Status | 차단 원인 |
|-------------|--------|----------|
| NFR-PERF-01, NFR-PERF-03 | NOT_STARTED | **애플리케이션 기동 불가** (Maven·DDL·키·PostgreSQL) |
| NFR-PERF-04 | PARTIAL | 쿼리 계획 검토 필요 — 실제 DB 필요 |
| NFR-OPS-AUDIT-02, CONST-LEGAL-02 | PENDING_DBA | 감사 테이블 DDL 검토 |
| FR-MSG-017 | NOT_STARTED | **AMB-07 미결** — Excel 내보내기 여부는 PM 결정 (spec 상 `Could`) |

## 8. PM 차단 항목 / Blocking items

| # | Item | Impact |
|---|------|--------|
| 1 | **AMB-M01 — 사용자→이용기관 매핑 컬럼** | **테넌트 격리의 근거.** 틀리면 격리 실패 또는 오범위 |
| 2 | **AMB-M02 — 상세 11개 컬럼 존재 여부** | 존재하지 않으면 상세 쿼리가 실행 시점에 실패 |
| 3 | **Maven + PostgreSQL + DDL + 키** | 잔여 5건, 성능·커버리지 차원 |
| 4 | **SR-05 — 하드코딩 AES 키** | `security-auditor` REJECT |
| 5 | AMB-07 | FR-MSG-017 범위 |

> **1·2 는 둘 다 "레거시 소스에서 확인할 수 없었다"는 같은 원인이다.** RISK-001(소스가
> 유일한 명세)의 구체적 발현이며, 도메인 담당자·DBA 확인 없이는 해소되지 않는다.
> 추측으로 넘기지 않고 XML 주석과 이 로그에 명시했다.

## 9. 두 모듈 최종 비교 / The two modules

| | login | 문자내역 |
|---|---|---|
| 요구사항 | **55 / 59 (93%)** | **46 / 52 (88%)** |
| 엔드포인트 | 7 | 3 |
| 프론트 화면 | 4 | 2 |
| 레거시 결함 대응 | 10 / 10 | 9 / 9 |
| 자체 발견 결함 | 7 (SR-01…07) | — |
| 실행 검증 | 93 assertions | 73 assertions |
| 7차원 | 87.1 | 82.1 |

**합계: 101 / 111 요구사항 (91%) · 백엔드 166 assertions + 프론트 50 테스트 실행 ·
레거시 결함 19건 전부 대응.**

---

**Sprint gate**

| Date | Approver | Comment | Status |
|------|----------|---------|--------|
| 2026-08-14 | PM | 7차원 82.1. 코드 가능 항목 완료. **AMB-M01/M02 확인 필요** | **PENDING** |
| 2026-08-14 | `code-reviewer` | 프론트엔드 APPROVE(50/50). SQL 델타 주석 양호. 백엔드 컴파일 이력 없음 | **PARTIAL APPROVE** |
| 2026-08-14 | `security-auditor` | 테넌트 격리 목록·상세 모두 SQL 주입 방식 확인. **AMB-M01 미확인 + SR-05 로 REJECT** | **REJECT** |
