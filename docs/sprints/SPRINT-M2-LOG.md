# Sprint M2 Log — 문자내역 목록 조회 배선

> **Sprint**: M2 · **Date**: 2026-08-14
> **Lead**: `team-leader` · **Previous**: Sprint M1 (domain foundation)
> **Status**: **목록 조회 경로 완성 · 상세(화면 41) 미착수**

---

## 1. Sprint 목표 / Sprint goal

M1 종료 시점: 도메인 모델과 SQL 은 완성되었으나 **매퍼 인터페이스가 없어 SQL 을 호출할
수 없었다.** 로그인 모듈 Sprint L1 과 같은 상태 — 검증된 조각, 배선 없음.

**목표: 목록 조회를 도달 가능하게 만든다.**

*At the end of M1 the domain model and SQL existed but no mapper interface, so nothing could
call the SQL — the same state login was in after Sprint L1.*

## 2. 완료 항목 / Completed

| Task | Description | Requirements |
|------|-------------|--------------|
| M2-01 | `MessageHistoryMapper` 인터페이스 — XML 과 연결 | FR-MSG-003/007 |
| M2-02 | 매퍼 파라미터를 `criteria` 단일 객체로 통일 | — |
| M2-03 | `TenantContextFilter` — 세션에서 테넌트 도출, `finally` 정리 | **FR-TEN-001**, NFR-SEC-TENANT |
| M2-04 | `MessageHistoryService` — 테넌트 범위 강제 + 감사 | **FR-TEN-001/002**, NFR-OPS-AUDIT |
| M2-05 | `MessageHistoryController` + 요청·응답 DTO | FR-MSG-001/002/004 |
| M2-06 | 로그인 세션에 이용기관·운영자 여부 저장 | FR-TEN-001 |
| M2-07 | `UserAccount` 에 `institutionCode` 추가 + 조회 | FR-TEN-001 |
| M2-08 | 감사 기록의 전화번호 검색어 해시 처리 | ADR-006, NFR-SEC-LOG |
| M2-09 | CI 정적 규칙 정밀화 (§4 SR-06) | RISK-L03 |

**신규 파일 4개 · 수정 6개 · Java 49개 (biztalk 9개) · TS/TSX 11개**

## 3. 레거시 재확인 — D3·D4 의 실제 결합 / What re-reading the legacy confirmed

PM 지시로 `biztalk_admin_40_*` 를 다시 읽어 필드↔컬럼 대응을 확인했다. 결과는 Skill 02
분석보다 <b>한 단계 더 나쁘다</b>:

| 화면 라벨 | 전송 필드 | 실제 컬럼 의미 | 서버 사용 여부 |
|-----------|-----------|----------------|----------------|
| 발신번호 | `PHONE` | **수신**번호 | ❌ 사용 안 함 (D4) |
| 수신번호 | `CALLBACK` | **발신**번호 | ✅ 사용 |

그리드는 `CALLBACK`=발송번호, `PHONE`=수신번호로 올바르게 표시했다. 즉 **DB 의 진실은
`CALLBACK`=발신, `PHONE`=수신**인데 입력 폼이 반대로 연결되어 있었고(D3), 서버는
`PHONE` 을 무시했으므로(D4) — 사용자가 **"수신번호"에 입력한 값이 실제로는 발신번호를
필터링**했다. 두 결함이 겹쳐 "틀린 결과"가 아니라 "다른 질문에 대한 답"을 반환한 셈이다.

*The DB truth is `CALLBACK`=sender, `PHONE`=recipient; the form was wired inversely (D3) and the
server ignored `PHONE` (D4), so a value typed into 수신번호 filtered the **sender** column. The
two defects compound into answering a different question, not merely answering wrongly.*

신규 구현은 필드명이 컬럼 의미를 그대로 반영한다: `senderNumber`→`CALLBACK`,
`recipientNumber`→`PHONE`. DTO Javadoc 에 위 표를 그대로 남겨 두었다.

## 4. 발견·수정한 결함 / Defects found and fixed

| ID | Finding | Severity | Status |
|----|---------|----------|--------|
| SR-06 | **CI 정적 규칙이 정당한 SHA-256 사용을 차단했다.** `MessageHistoryService` 는 감사 기록용으로 전화번호 검색어를 해시하는데(ADR-006 요구사항), 규칙이 이를 비밀번호 해싱으로 판정해 빌드를 깨뜨렸다 | MEDIUM | ✅ FIXED |

### SR-06 은 SR-02 와 같은 계열이다

같은 규칙이 **두 번째로** 오탐했다:

| | 오탐 대상 | 원인 |
|---|-----------|------|
| SR-02 (L5) | `PasswordHasher` 의 Javadoc | 결함을 **설명하는 문서**를 결함으로 판정 |
| SR-06 (M2) | `MessageHistoryService` 의 감사 해시 | **정당한 비신용 해싱**을 자격증명 해싱으로 판정 |

**교훈: 키워드 기반 정적 규칙은 자신의 결함을 문서화하고 여러 목적으로 같은 primitive 를
쓰는 코드베이스에서 반복적으로 오탐한다.** 규칙을 두 개로 분리했다:
- **MD5 는 용도 무관 전면 차단** — 이 코드베이스에 정당한 용도가 없다
- **SHA-256 은 자격증명 문맥에서만 차단** — 같은 줄에 `password`/`pwd`/`credential` 이 있을 때

그리고 규칙 변경 후 **의도적 위반 샘플로 여전히 잡히는지 확인**했다:
`MessageDigest.getInstance("SHA-256").digest(password.getBytes())` → CAUGHT.
규칙을 좁힐 때 "통과하게 만드는 것"과 "여전히 잡는 것"을 함께 확인하지 않으면 규칙이
무력화된다.

*A rule narrowed without re-proving it still catches the real thing is a rule quietly disabled.*

## 5. 미확인 사항 — AMB-M01 / Unverified assumption

`UserAccount.institutionCode` 를 `USER_LDGR.IS_CD` 에서 읽도록 작성했으나,
**레거시 소스에서 사용자→이용기관 매핑이 어디에 있는지 확인하지 못했다.**

| 근거 | 내용 |
|------|------|
| 문자내역 목록 | 이용기관을 컬럼 `ID` 로 다룬다 |
| 담당자관리(화면 00) | `IS_CD` / `IS_NM` 을 쓴다 |
| `USER_LDGR_R006` | 조회 컬럼 목록에 이용기관 관련 필드가 확인되지 않음 |

**이것은 추측으로 넘길 항목이 아니다.** 잘못되면 (a) 테넌트 범위가 적용되지 않거나
(b) 잘못된 범위가 적용된다 — 둘 다 NFR-SEC-TENANT 위반이다. XML 에 경고 주석을 남기고
DBA·도메인 담당자 확인 항목으로 등록했다.

*Getting this wrong means either no tenant scoping or the wrong scope — both violate
NFR-SEC-TENANT. Marked in the XML and raised for DBA and domain-owner confirmation.*

## 6. 검증 — step [D]

| Check | Result |
|-------|--------|
| 백엔드 JDK-only 하네스 | ✅ **141 assertions** (12+29+20+32+48) + 20만 표본 |
| **하네스가 통합 파괴를 탐지** | ✅ `UserAccount` 필드 추가로 드라이버 컴파일 실패 → 수정 |
| CI 정적 규칙 6건 | ✅ clean (규칙 정밀화 후) |
| 규칙 유효성 역검증 | ✅ 의도적 위반 샘플 CAUGHT |
| provenance | ✅ 49/49 |
| 프론트엔드 (변경 없음) | ✅ 39/39 유지 |
| `mvn verify` | ❌ 미실행 |

> **하네스가 실제로 회귀를 잡았다.** `UserAccount` 에 필드를 추가하자 `PolicyDriver` 가
> 컴파일되지 않았다 — Maven 이 없는 상태에서도 크로스 모듈 변경의 파급을 즉시 알려주는
> 안전망이 동작한다는 증거다.

## 7. 7 차원 자체 평가 — step [E] · 문자내역

| Dimension | Weight | Score | Basis |
|-----------|--------|-------|-------|
| 완성도 | 20% | **63** | 33/52 요구사항. 목록 완성, 상세(8건)·프론트(2건)·성능(3건) 미착수 |
| 추적성 | 15% | **95** | 49/49 provenance, biztalk 전용 trace CSV 신설 |
| 보안 | 20% | **78** | 테넌트 격리·마스킹·감사 해시 완료. **SR-05 미해결 + AMB-M01 미확인** |
| 성능 | 10% | **35** | 페이징·상한 구현. 부하 테스트 불가 |
| 가독성 | 15% | **93** | SQL 델타에 `-- FIX Dn:` 주석, DTO 에 D3 대응표 |
| 표준 준수 | 10% | **95** | write scope 준수 |
| 테스트 커버리지 | 10% | **58** | 48건 실행. 통합·상세 테스트 없음 |

**가중 합계: 12.6 + 14.25 + 15.6 + 3.5 + 13.95 + 9.5 + 5.8 = 75.2 / 100**

문자내역은 아직 초기 단계이므로 낮은 점수가 정상이다. 로그인 모듈도 L1 시점 69.75 에서
시작해 L8 에 87.1 이 되었다.

## 8. 남은 작업 / Remaining — 15건

| Requirement | Item | Sprint |
|-------------|------|--------|
| FR-MSGD-001…008 | **상세 조회 (화면 41)** — 4개 매퍼, 19 필드, D5·D9 대응 | M3 |
| FR-MSG-014 | 메시지키 클릭 → 상세 이동 | M3 |
| FR-TEN-004 | 이용기관 목록 API 운영자 전용 | M3 |
| NFR-USE-01, NFR-COMPAT-01 | React 조회 화면 (12컬럼 그리드) | M3 |
| NFR-PERF-01/03 | 부하 테스트 | Maven |
| NFR-OPS-TIME | 서비스 시간대 게이팅 | M3 |
| FR-MSG-017 | Excel 내보내기 (Could, AMB-07 미결) | 보류 |

## 9. PM 차단 항목 / Blocking items

| # | Item | Impact |
|---|------|--------|
| 1 | **AMB-M01 — 사용자→이용기관 매핑 컬럼 미확인** | **테넌트 격리의 근거.** 잘못되면 격리 실패 |
| 2 | **Maven 미설치** | 141 assertions 는 우회 검증. JUnit·통합 테스트 미실행 |
| 3 | **SR-05 — 하드코딩 AES 키** | `security-auditor` REJECT 유지 |
| 4 | DDL 검토 | 감사 테이블 (NFR-OPS-AUDIT-02, CONST-LEGAL-02) |
| 5 | AMB-07 | Excel 내보내기 범위 |

---

**Sprint gate**

| Date | Approver | Comment | Status |
|------|----------|---------|--------|
| 2026-08-14 | PM | 7차원 75.2. 목록 조회 배선 완료. **AMB-M01 확인 필요** | **PENDING** |
| 2026-08-14 | `code-reviewer` | SQL 델타 주석 양호. 컴파일 이력 없음 | **PENDING** |
| 2026-08-14 | `security-auditor` | 테넌트 격리 SQL 주입 방식 확인(후처리 아님). **AMB-M01 미확인 + SR-05 로 REJECT** | **REJECT** |
