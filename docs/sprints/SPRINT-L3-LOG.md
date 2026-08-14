# Sprint L3 Log — OTP 등록, 운영자 초기화, 실행 검증

> **Sprint**: L3 · **Date**: 2026-08-14
> **Lead**: `team-leader` · **Previous**: [SPRINT-L2-LOG.md](SPRINT-L2-LOG.md)
> **Status**: **PARTIAL** — 핵심 고리는 닫혔으나 잔여 범위 있음

---

## 1. Sprint 목표 / Sprint goal

Sprint L2 종료 시점의 진단: 로그인 경로는 완성되었으나 **아무도 로그인할 수 없다.**
로그인은 OTP 등록을 요구하는데 등록 경로가 없었기 때문이다(닫힌 고리).

**목표: 그 고리를 연다.** 부수적으로, Maven 없이도 실행 가능한 검증 수단을 만든다.

*Diagnosis at the end of L2: the login path was complete but nobody could log in, because
login requires OTP enrolment and no enrolment path existed. Goal: open that loop — and
find a way to actually execute something without Maven.*

## 2. 완료 항목 / Completed

| Task | Description | Requirements |
|------|-------------|--------------|
| L3-01 | `SecretCipher` — AES-256-GCM, IV per call, 기동 시 키 검증 | NFR-SEC-PII-L01, harness §10 |
| L3-02 | `QrRenderer` — 로컬 `otpauth://` URI, **외부 호출 없음** | FR-OTP-003/004, **fixes L4** |
| L3-03 | `SecretGenerator` 빈 — **160비트** | FR-OTP-002, **fixes L8** |
| L3-04 | `OtpRegistrationService#begin` — 신원확인 + 자격요건 + 비밀키 발급 | FR-OTP-001/002/003/009 |
| L3-05 | `OtpRegistrationService#complete` — 코드 검증 후에만 저장 | FR-OTP-005 |
| L3-06 | `OtpRegistrationService#resetByOperator` — 자기초기화 금지, 해지계정 차단 | FR-OTP-007/008, AMB-L08 |
| L3-07 | `OtpController` — begin/confirm, 대기 비밀키는 세션 보관 | FR-OTP-001…006 |
| L3-08 | `OtpAdminController` — 운영자 초기화, 사유 필수 | FR-OTP-007/008 |
| L3-09 | `UserMapper.updateOtpKey` / `clearOtpKey` + XML | FR-OTP-005/007 |
| L3-10 | `AuthExceptionHandler` — 신규 사유 2건 매핑 | FR-OTP-006 |
| L3-11 | `SecretCipherTest` — 11 케이스 | NFR-SEC-PII-L01 |
| L3-12 | **`qa/verify-without-maven.sh`** — 실행 검증 수단 | SPRINT-L1-RETRO A1 |

**신규 파일 8개 · 수정 7개 · Java 파일 총 34개**

## 3. 검증 — step [D] · **이번 sprint 의 성과**

> **처음으로 코드가 실제 실행되었다.** Maven 은 여전히 없지만, Spring 애노테이션을
> 제거하면 순수 JDK 로 컴파일·실행 가능한 부분집합이 있음을 확인하고 검증 하네스를 만들었다.
>
> *For the first time, code actually ran. Maven is still absent, but a subset compiles and
> executes on the bare JDK once Spring annotations are stripped.*

| Check | Result |
|-------|--------|
| **`SecretCipher` 실행 검증** | ✅ **12/12 PASS** — round-trip, IV 유일성, 조작 탐지, 다른 키 거부, 키 형식 검증 |
| **`AccountPolicy` 실행 검증** | ✅ **16/16 PASS** — 잠금 5회 경계, 휴면 89/90 경계, 상태 4종, 비밀번호 주기 89/90 경계 |
| **`PasswordPolicy` 실행 검증** | ✅ **13/13 PASS** — L6 회귀 5건, L9 회귀 1건, 이력 재사용, 메시지 비노출 |
| **합계** | ✅ **41 assertions 실행, 전부 통과** |
| CI 정적 규칙 5건 (로컬) | ✅ 전부 clean |
| provenance 주석 | ✅ 26/26 main 파일 |
| `mvn verify` | ❌ 미실행 — Maven 미설치 |
| Spring 컨텍스트 / MyBatis 매핑 / TOTP 라이브러리 API | ❌ 미검증 |

### 실행으로 확인된 사실 / What execution actually confirmed

- **경계값이 정확하다.** 휴면 89일=false / 90일=true, 비밀번호 89일=false / 90일=true.
  경계 오류(off-by-one)는 코드를 읽어서는 확인하기 어렵다
- **L6 회귀가 진짜로 거절한다.** 1종·2종 문자, 취약 목록, 아이디 포함, 연속 문자 모두 거절
- **L9 회귀 성립.** 70자 비밀번호 통과 (레거시는 15자 상한)
- **GCM 무결성이 동작한다.** 1비트 조작 → 예외, 평문 미노출
- **알 수 없는 상태 코드가 fail-closed.** 레거시는 통과시켰다

## 4. 이번 sprint 에서 발견·수정한 결함 / Defects found and fixed

| ID | Finding | Severity | Status |
|----|---------|----------|--------|
| SR-03 | **OTP 비밀키 암호화 도입으로 로그인이 깨질 상태였다.** `SecretCipher` 를 추가해 저장 시 암호화하도록 바꾸었으나, `AuthenticationService` 는 여전히 저장값을 그대로 `totp.verify()` 에 넘기고 있었다 — 암호문을 Base32 비밀키로 취급하므로 **모든 로그인이 실패**한다 | **HIGH** | ✅ FIXED — 검증 직전 `cipher.decrypt()` 삽입 |

**SR-03 의 성격.** 이것은 논리 오류가 아니라 **두 변경 사이의 접합부** 결함이다. 저장
경로(등록)와 소비 경로(로그인)를 각각 올바르게 바꾸었으나 표현 형식이 어긋났다.
문자내역 슬라이스의 9개 결함이 전부 계층 간 간극에 있었던 것과 같은 유형이며, 같은
방식으로 발견되었다 — 한쪽을 바꾼 뒤 반대쪽을 읽어본 것.

*SR-03 is not a logic error but a seam defect: the storage path (enrolment) and the
consumption path (login) were each changed correctly, but their representations diverged.
Same class as the nine 문자내역 defects, found the same way — by reading the other side
after changing one.*

## 5. 7 차원 자체 평가 — step [E]

| Dimension | Weight | Score | Δ vs L2 | Basis |
|-----------|--------|-------|---------|-------|
| 완성도 | 20% | **82** | +12 | 등록·초기화 완성으로 닫힌 고리 해소. 잔여: rate limiter, 비밀번호 변경 화면, React |
| 추적성 | 15% | **95** | — | 26/26 provenance; trace CSV 갱신 |
| 보안 | 20% | **88** | +6 | AES-256-GCM 저장 암호화, 비밀키 1회 노출, 자기초기화 금지, 사유 필수 감사 |
| 성능 | 10% | **40** | — | 부하 테스트 여전히 불가 |
| 가독성 | 15% | **92** | — | — |
| 표준 준수 | 10% | **95** | — | — |
| 테스트 커버리지 | 10% | **58** | **+18** | **41건 실행 확인.** 그러나 JUnit 미실행, 커버리지 미측정, 통합·E2E 없음 |

**가중 합계: 16.4 + 14.25 + 17.6 + 4.0 + 13.8 + 9.5 + 5.8 = 81.35 / 100**

### 결과: **81.35 — 임계 90 미달** (L1 69.75 → L2 75.95 → L3 81.35)

세 sprint 연속 미달이나 추세는 단조 증가다. 남은 격차의 대부분은 여전히 **성능(40)** 과
**테스트 커버리지(58)** 이며, 둘 다 Maven 설치 한 번으로 크게 움직인다. §6 재생성 루프는
이번에도 적용하지 않는다 — 코드를 다시 쓰는 것이 아니라 빌드 도구를 설치하는 것이
필요한 행동이다.

## 6. PM 차단 항목 / Blocking items

| # | Item | Blocks | Change |
|---|------|--------|--------|
| 1 | **Maven 미설치** | JUnit 93건 실행, 커버리지, 부하, Spring/MyBatis/TOTP API 검증 | 부분 완화 — 41건은 우회 검증됨 |
| 2 | **ADR-LOGIN-011 미결정** | 기존 계정 전원 로그인 불가 | 변화 없음 — **이제 유일한 실사용 차단 요인** |
| 3 | **`sender_key` 회전** | 운영 중 시스템의 실제 노출 | 변화 없음 |
| 4 | DDL 검토 (+ `PWD_HASH`·`OTP_KEY` 컬럼) | 세션·감사·이력 테이블 | 변화 없음 |
| 5 | `IRIS_OTP_SECRET_KEY` 발급 | 애플리케이션 기동 자체 | **신규** — 기본값 없음(의도적), 없으면 기동 실패 |

> **2번이 이제 유일한 실사용 차단 요인이다.** 신규 계정은 이제 OTP 를 등록하고 로그인할
> 수 있다(고리 해소). 그러나 **기존 계정은 전원** 레거시 SHA-256 해시만 보유하므로
> `verifyPassword` 에서 fail-closed 된다. ADR-LOGIN-011 결정 없이는 실사용자 0명이다.
>
> *Item 2 is now the only thing blocking real use. New accounts can enrol and log in. But
> every existing account holds only the legacy hash and fails closed. Zero real users
> until ADR-LOGIN-011 is ruled on.*

## 7. 남은 작업 / Remaining

| Item | Requirement | Note |
|------|-------------|------|
| Rate limiter (해싱 앞단) | FR-LOGIN-025, RISK-L07 | Argon2id 비용이 DoS 벡터 |
| 비밀번호 변경 화면·흐름 | FR-PWD-001/002 | 강제 변경 경로가 현재 막다른 길 |
| React 로그인·등록 화면 | FR-LOGIN-001/022, NFR-USE-L01 | 프론트엔드 미착수 |
| IP 허용목록 실제 차단 | FR-LOGIN-024, L5 | AMB-L03 미결 |
| 관리자 로그인 알림 (설정 기반) | FR-LOGIN-021, L1 | |
| 24개 SEC-L* 부정 경로 테스트 | TEST-PLAN-LOGIN §4 | Maven 필요 |
| 세션 reaper 스케줄러 | ADR-LOGIN-012 §4.3 | 쿼리는 작성됨 |
| 통합 테스트 (Testcontainers) | TEST-PLAN-LOGIN §1.3 | Maven 필요 |

---

**Sprint gate**

| Date | Approver | Comment | Status |
|------|----------|---------|--------|
| 2026-08-14 | PM | 7차원 81.35 < 90. 차단 5건, 2번이 실사용 유일 차단 | **PENDING** |
| 2026-08-14 | `code-reviewer` | 41 assertion 실행 확인은 진전이나 전체 컴파일 이력 없음 — APPROVE 보류 | **PENDING** |
| 2026-08-14 | `security-auditor` | AES-256-GCM 저장 암호화 실행 검증 완료, SR-03 종결. 조건부승인 | **CONDITIONAL** |
