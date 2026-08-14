# Sprint L2 Log — OTP 검증 및 HTTP 표면

> **Sprint**: L2 of 2 · **Date**: 2026-08-14
> **Lead**: `team-leader` · **Previous**: [SPRINT-L1-LOG.md](SPRINT-L1-LOG.md)
> **Status**: **PARTIAL** — see §5

---

## 1. Sprint 목표 / Sprint goal

Sprint L1 이 남긴 두 공백을 메운다:

1. **HTTP 표면이 없었다.** 컨트롤러도 `SecurityConfig` 도 존재하지 않아 로그인은 도달
   불가능했다.
2. **SR-01 — 단일 요소 인증 경로.** OTP 가 등록된 계정이 코드 검증 없이 통과했다.

*Close the two gaps Sprint L1 left: there was no HTTP surface at all, and SR-01 allowed
authentication on a password alone.*

## 2. 완료 항목 / Completed

| Task | Description | Requirements |
|------|-------------|--------------|
| L2-01 | `TotpVerifier` — 라이브러리 기반 TOTP, **±1 스텝** | FR-LOGIN-009/011, NFR-SEC-AUTH-L03, **fixes L3** |
| L2-02 | `TotpConfig` — 교체 가능한 `TimeProvider` | FR-LOGIN-011 |
| L2-03 | `AuthenticationService` — OTP 검증 삽입, OTP 실패 카운터 | FR-LOGIN-010, **fixes SR-01** |
| L2-04 | `AuthenticationController` — `/api/auth/login`, `/api/auth/logout` | FR-LOGIN-001/023 |
| L2-05 | `LoginRequest` / `LoginResponse` — 검증 + `toString()` 마스킹 | FR-LOGIN-009, NFR-SEC-LOG-L01 |
| L2-06 | `AuthExceptionHandler` — 계정 열거 방지 응답 매핑 | FR-LOGIN-002, NFR-USE-L02 |
| L2-07 | `SecurityConfig` — **기본 인증 필수**, 예외는 명시 열거만 | NFR-SEC-AUTH-L01, **prevents D1** |
| L2-08 | 세션 고정 방지 — 인증 시 세션 ID 재생성 | NFR-SEC-SESSION-L01, TM-L003 |
| L2-09 | 신뢰 IP 해석 — 요청 헤더 직접 읽지 않음 | FR-LOGIN-019, **fixes L7** |
| L2-10 | `TotpVerifierTest` — 12 케이스, L3 회귀 포함 | FR-LOGIN-011 |
| L2-11 | `AuthenticationServiceTest` — 20 케이스, **SR-01 회귀 포함** | NFR-SEC-AUTH-L01 |
| L2-12 | CI 정적 규칙 5건 — 주석 오탐 수정 (§4 참조) | RISK-L03 |

**신규 파일 10개 · 수정 4개 · Java 파일 총 28개 (main 20 / test 5 + 3 suites)**

### 종료 경로 전수 검증 / All exit paths now covered

`AuthenticationServiceTest` 는 10개 종료 경로를 검증한다: 계정 미존재 · 잠금 · 비밀번호
불일치 · OTP 미등록 · OTP 형식 오류 · OTP 불일치 · 휴면 · 상태 차단 · 비밀번호 변경
강제 · 성공. 여기에 마이그레이션 미완 계정과 감사 기록 검증을 더한다.

## 3. 검증 — step [D]

| Check | Result |
|-------|--------|
| CI 정적 규칙 5건 (로컬 실행) | ✅ **전부 clean** — 주석 필터 적용 후 |
| `// source:` / `// req:` 주석 | ✅ 20/20 main 파일 통과 |
| **`mvn verify`** | ❌ **여전히 미실행 — Maven 미설치** |
| 단위 테스트 실행 | ❌ 미실행 |
| TOTP 라이브러리 API 검증 | ❌ 미실행 — **§5 참조** |

> Sprint L1 과 동일한 한계가 유지된다. 이번 sprint 는 테스트 32건을 추가했으나
> **한 건도 실행되지 않았다.**
>
> The same limitation as Sprint L1 persists: 32 test cases were added and **none was
> executed.**

## 4. 이번 sprint 에서 발견·수정한 결함 / Defects found and fixed this sprint

| ID | Finding | Severity | Status |
|----|---------|----------|--------|
| SR-01 | (L1 에서 발견) OTP 등록 계정이 코드 검증 없이 통과 | HIGH | ✅ **CLOSED** — `TotpVerifier` 삽입 + `SingleFactorPrevention` 테스트 2건 |
| SR-02 | **CI 정적 규칙이 Javadoc 을 오탐.** `PasswordHasher` 는 대체 대상을 설명하기 위해 `JexMessageDigest.getHashString(SHA_256, pwd)` 를 인용하는데, 규칙이 이를 실제 코드로 판정해 빌드를 깨뜨렸다 | MEDIUM | ✅ FIXED — 주석 라인 제외 필터 도입, 5개 규칙 전체 적용 |

**SR-02 는 이 프로젝트의 문서화 방식과 직접 충돌한 사례다.** 레거시 결함을 Javadoc 에
인용하는 관행은 추적성에 유용하지만, 같은 문자열을 찾는 정적 규칙과 부딪힌다. 규칙이
코드와 주석을 구분하지 못하면, 결함을 **설명하는** 문서가 결함으로 신고된다.

*SR-02 is a direct collision between this project's documentation practice and its own
tooling: quoting legacy defects in Javadoc aids traceability but trips static rules that
search for the same strings. A rule that cannot tell code from comment reports the
documentation of a defect as the defect.*

## 5. 7 차원 자체 평가 — step [E]

| Dimension | Weight | Score | Δ vs L1 | Basis |
|-----------|--------|-------|---------|-------|
| 완성도 | 20% | **70** | +15 | HTTP 표면 완성, OTP 경로 완성. 남은 항목: rate limiter, 비밀번호 변경 화면, OTP 등록, React |
| 추적성 | 15% | **95** | — | 20/20 파일 provenance 통과; trace CSV 갱신 |
| 보안 | 20% | **82** | +12 | 2요소 강제, 세션 고정 방지, 계정 열거 방지, 기본 인증 필수, 정적 규칙 5건. 남은 항목: 24개 SEC-L* 테스트 중 다수, rate limiter |
| 성능 | 10% | **40** | — | 부하 테스트 불가 (Maven 미설치) |
| 가독성 | 15% | **92** | +2 | 결정 지점마다 근거 주석 |
| 표준 준수 | 10% | **95** | — | write scope 준수, ADR 기록 |
| 테스트 커버리지 | 10% | **40** | +5 | 테스트 82건 작성, **실행 0건** |

**가중 합계: 14.0 + 14.25 + 16.4 + 4.0 + 13.8 + 9.5 + 4.0 = 75.95 / 100**

### 결과: **75.95 — 임계 90 미달** (L1 69.75 → +6.2)

L1 과 동일한 이유로 §6 재생성 루프를 적용하지 않는다. 최저 두 차원(성능 40,
테스트 커버리지 40)은 **Maven 부재**에 전적으로 묶여 있으며, 코드를 다시 써도 움직이지
않는다. 82건의 테스트를 한 번 실행하는 것이 이 두 점수를 동시에 올리는 유일한 행동이다.

*The regeneration loop is again inapplicable: the two lowest dimensions are entirely
gated on Maven's absence. Running the 82 tests once is the single action that moves both.*

## 6. PM 차단 항목 / Blocking items

| # | Item | Blocks | Change since L1 |
|---|------|--------|-----------------|
| 1 | **Maven 미설치** | 빌드·테스트·커버리지·부하 — 그리고 이제 **TOTP 라이브러리 API 검증** | 악화. `dev.samstevens.totp` API 사용이 미검증 상태로 남았다 |
| 2 | **ADR-LOGIN-011 미결정** | 마이그레이션 미완 계정은 로그인 불가 | 변화 없음 |
| 3 | **`sender_key` 회전** | 코드 아님 — 운영 중 시스템의 실제 노출 | 변화 없음 |
| 4 | DDL 검토 | 세션·감사 테이블 | 변화 없음 |
| 5 | AMB-L03 | IP 허용목록 범위 | 변화 없음 |

> **1번이 이번 sprint 에서 더 심각해졌다.** `TotpVerifier` 는 외부 라이브러리
> (`dev.samstevens.totp` 1.7.1) 의 API 를 문서 기준으로 사용했으며, 컴파일로 확인하지
> 못했다. 클래스명·메서드 시그니처가 실제와 다르면 컴파일 실패하고, 그 시점까지
> 알 수 없다.
>
> *Item 1 got worse this sprint: `TotpVerifier` uses the external library's API from
> documentation without a compile to confirm it. If a class name or method signature
> differs, it fails to compile — and there is no way to know until Maven runs.*

## 7. 남은 작업 / Remaining

| Item | Requirement | Sprint |
|------|-------------|--------|
| Rate limiter (해싱 앞단 배치) | FR-LOGIN-025, RISK-L07 | L3 |
| 비밀번호 변경 화면·흐름 | FR-PWD-001/002/006 | L3 |
| OTP 등록 (160비트, 로컬 QR) | FR-OTP-001…006, L4/L8 | L3 |
| 운영자 OTP 초기화 | FR-OTP-007/008 | L3 |
| IP 허용목록 실제 차단 | FR-LOGIN-024, L5 | L3 |
| 관리자 로그인 알림 (설정 기반) | FR-LOGIN-021, L1 | L3 |
| React 로그인 화면 | FR-LOGIN-001/022, NFR-USE-L01 | L3 |
| 24개 SEC-L* 부정 경로 테스트 잔여 | TEST-PLAN-LOGIN §4 | L3 |
| 세션 reaper 스케줄러 연결 | ADR-LOGIN-012 §4.3 | L3 |

**Sprint L2 는 계획된 범위를 완료하지 못했다.** 2 sprint 계획이 실질적으로 3 sprint 로
늘어났으며, L1 회고의 A7(재추정) 이 실행되지 않은 결과다.

---

**Sprint gate**

| Date | Approver | Comment | Status |
|------|----------|---------|--------|
| 2026-08-14 | PM | 7차원 75.95 < 90; 차단 항목 5건 유지, 1번 악화 | **PENDING** |
| 2026-08-14 | `code-reviewer` | APPROVE 불가 — 컴파일·실행 이력 없음 | **PENDING** |
| 2026-08-14 | `security-auditor` | SR-01 종결로 2요소 강제 확인. 단, 검증은 미실행 테스트에 근거함 — 조건부승인 | **CONDITIONAL** |
