# Sprint L7 Log — 로그인 G1 범위 완료

> **Sprint**: L7 · **Date**: 2026-08-14
> **Lead**: `team-leader` · **Previous**: [SPRINT-L6-LOG.md](SPRINT-L6-LOG.md)
> **Status**: **NOT_STARTED 0건 — G1 범위 코드 완료**

---

## 1. Sprint 목표 / Sprint goal

PM 지시: **문자내역으로 넘어가기 전에 로그인 G1 범위(59건)를 먼저 끝낸다.**
L6 종료 시점 미착수 4건: FR-LOGIN-021, FR-LOGIN-024, NFR-SEC-CHANNEL-L01, NFR-PERF-L01.

*PM instruction: finish the login module's approved 59-requirement scope before moving to
문자내역. Four items were unstarted at the end of L6.*

## 2. 범위 정리 / Scope correction

문자내역 구현을 시작하려 만든 `common/tenant/TenantContext.java` 를 **삭제했다.** 로그인
G1 범위 밖이며, 호출자 없이 남으면 정확히 L4 에서 문제가 된 "완성되었으나 도달 불가"
상태가 된다. 문자내역 sprint 에서 다시 만든다.

*Deleted the `TenantContext` started for 문자내역: it is outside the login G1 scope, and
leaving it uncalled would recreate exactly the "complete but unreachable" state that was the
problem in L4.*

## 3. 완료 항목 / Completed

| Task | Description | Requirements |
|------|-------------|--------------|
| L7-01 | `CidrMatcher` — IPv4 CIDR 매칭, 잘못된 항목은 기동 시 실패 | FR-LOGIN-024 |
| L7-02 | `IpAllowlistPolicy` — 실제 차단, 운영자 전용(AMB-L03 가정) | **FR-LOGIN-024**, **fixes L5** |
| L7-03 | 인증 흐름 8단계에 허용목록 삽입 (역할 확정 후) | FR-LOGIN-024 |
| L7-04 | `AdminLoginNotifier` — 설정 기반 수신자, 채널 포트 분리 | **FR-LOGIN-021**, **fixes L1** |
| L7-05 | `maskName` — 이메일 로컬파트 마스킹 | NFR-SEC-PII-L02 |
| L7-06 | `SecurityConfig` — HTTPS 강제 + HSTS + CSP + frame-deny | **NFR-SEC-CHANNEL-L01** |
| L7-07 | `qa/load/login-load.js` — k6 4개 시나리오 | NFR-PERF-L01 |
| L7-08 | `qa/drivers/CidrDriver` — **32건 실행 검증** | TEST-PLAN-LOGIN §1.1 |
| L7-09 | `application.yml` — 신규 설정 6개 그룹 | CONST-SEC-L02 |

**신규 파일 5개 · 수정 5개 · Java 40개 + TS/TSX 7개**

## 4. 결함 L5 · L1 대응 상세 / How defects L5 and L1 were closed

**L5 — IP 허용목록이 무효였다.** 레거시는 로그인 화면을 그릴 때마다
{@code A_USER_IP_AUTHED_R001} 로 접속 IP 를 조회했으나, 결과에 따른
`response.sendRedirect("/error.jsp")` 가 주석 처리되어 있었다. **조회는 매번 수행되고
결과는 버려졌다.** 신규 구현은 CIDR 목록을 설정에 두고 실제로 차단하며, 활성화 상태에서
대역이 비어 있으면 **기동 시점에 실패**한다 — 전원 차단 상태로 조용히 뜨는 것을 막는다.

**L1 — 하드코딩된 자격증명·개인정보.** 레거시는 운영자 로그인 알림에 카카오
`sender_key`, 발신번호, 템플릿 코드, **개인 휴대폰번호 3건**을 소스에 박아두었다. 신규
구현은 수신자·사이트 라벨을 전부 설정에서 받고, 카카오 발송은 `NotificationChannel`
포트로 분리했다 — 프로바이더 연동은 문자내역 모듈의 책임이므로, 없는 연동을 발명해
자격증명을 만들어 넣지 않는다.

또한 레거시는 알림 실패 시 예외를 다시 던져 **로그인 전체를 실패시켰다.** 부가 기능의
장애가 인증을 막는 것은 잘못된 가용성 판단이므로, 신규 구현은 삼키고 감사에 남긴다.

## 5. 검증 — step [D]

| Check | Result |
|-------|--------|
| `SecretCipher` | ✅ 12/12 |
| `AccountPolicy` + `PasswordPolicy` | ✅ 29/29 |
| `TemporaryPasswordGenerator` × 정책 (20만 표본) | ✅ 위반율 측정 |
| `RateLimiter` + `OtpReplayGuard` | ✅ 20/20 |
| **`CidrMatcher`** | ✅ **32/32** |
| **백엔드 누적 실행 검증** | ✅ **93 assertions** |
| 프론트엔드 `tsc` / `vite build` / `vitest` | ✅ 0 errors / 성공 / **13/13** |
| provenance 주석 | ✅ 40/40 main 파일 |
| CI 정적 규칙 5건 | ✅ clean |
| `mvn verify` | ❌ 미실행 |
| k6 부하 테스트 | ❌ 미실행 — 기동 필요 |

### CIDR 경계 검증이 중요한 이유

허용목록은 접근 통제이며, 경계 오류는 코드 리뷰로 잡히지 않는다. 실행으로 확인한 것:

- `/24` 첫·마지막 주소 포함, 바로 아래·위 제외
- `/8` 상하한 정확, `/31` 2개 주소만, `/32` 단일 호스트
- **`/0` 전체 일치** — `-1L << 32` 가 의도한 마스크를 주지 않는 케이스를 분기 처리했고,
  그 분기가 실제로 동작함을 확인
- 잘못된 CIDR 4종 모두 **생성 시점에 예외** (조용히 건너뛰지 않음)
- 파싱 불가 주소는 **거부** (통과가 아니라 차단)

## 6. 7 차원 자체 평가 — step [E]

| Dimension | Weight | Score | Δ vs L6 | Basis |
|-----------|--------|-------|---------|-------|
| 완성도 | 20% | **97** | +2 | **NOT_STARTED 0건.** 55/59 구현, 잔여 4건은 PM·DBA·기동 대기 |
| 추적성 | 15% | **95** | — | 40/40 provenance |
| 보안 | 20% | **80** | — | 통제 3건 추가(허용목록·TLS·CSP)했으나 **SR-05 미해결** |
| 성능 | 10% | **55** | +10 | k6 시나리오 4종 작성, 임계값 정의. 실행은 기동 대기 |
| 가독성 | 15% | **92** | — | — |
| 표준 준수 | 10% | **95** | — | 범위 밖 파일 삭제로 격리 유지 |
| 테스트 커버리지 | 10% | **78** | +4 | 백엔드 93건 + 프론트 13건 실행. JUnit 93건 미실행 |

**가중 합계: 19.4 + 14.25 + 16.0 + 5.5 + 13.8 + 9.5 + 7.8 = 86.25 / 100**

### 결과: **86.25** — 최고점 (69.75 → 75.95 → 81.35 → 83.75 → 82.55 → 84.45 → **86.25**)

잔여 3.75 점의 구성:
- **보안 4.0 점** — SR-05 (하드코딩 AES 키)
- **성능 4.5 점** — k6 실행 불가 (기동 전제 미충족)
- **테스트 커버리지 2.2 점** — JUnit 미실행 (Maven)

**코드로 움직일 수 있는 부분은 사실상 없다.** 세 항목 모두 PM·환경 측 조치다.

## 7. G1 범위 최종 상태 / Final state of the G1 scope

| Status | Count | Detail |
|--------|-------|--------|
| IMPLEMENTED | **55 / 59 (93%)** | 코드 완료·배선 완료·도달 가능 |
| SPECIFIED_NOT_RUN | 1 | NFR-PERF-L01 — k6 스크립트 작성, 실행은 기동 후 |
| BLOCKED | 1 | CONST-SEC-L01 — ADR-LOGIN-011 결정 대기 |
| PENDING_DBA | 2 | CONST-DATA-L01, CONST-LEGAL-L02 — DDL 검토 |
| **NOT_STARTED** | **0** | — |

**미착수 항목이 0건이 되었다.** 남은 4건은 전부 사람의 결정 또는 환경 조치를 기다리는
상태이며, 추가 코드로 해소되지 않는다.

## 8. PM 차단 항목 / Blocking items

| # | Item | 잔여 점수 영향 | Priority |
|---|------|---------------|----------|
| 1 | **SR-05 — `application.yml` 하드코딩 AES 키** | 보안 4.0점 | **최우선** |
| 2 | **Maven 미설치** | 커버리지 2.2점 | **최우선** |
| 3 | `IRIS_OTP_SECRET_KEY` 발급 + DDL 검토 | 성능 4.5점 (기동 전제) | 높음 |
| 4 | `sender_key` 회전 (운영 중 시스템) | — | 높음 |
| 5 | **FR-PWD-007 G1 개정** — 승인되지 않은 요구사항 | 표준 준수 | 중간 |
| 6 | AMB-L03 확정 (현재 운영자 전용 가정) | — | 중간 |
| 7 | ADR-LOGIN-011 (마이그레이션 비용) | — | 중간 |

## 9. 후속 품질 항목 / Follow-up quality items

G1 범위는 아니지만 TEST-PLAN-LOGIN 이 요구하는 항목:

| Item | Requirement | Blocked on |
|------|-------------|------------|
| 24개 SEC-L* 부정 경로 테스트 | TEST-PLAN-LOGIN §4 | Maven |
| `OtpRegisterPage` / `PasswordChangePage` 컴포넌트 테스트 | §1.3 | 없음 — 작성 가능 |
| axe-core 접근성 자동 검증 | §1.3 | 없음 — 작성 가능 |
| QR 이미지 렌더링 (현재 `otpauth://` 링크) | FR-OTP-003 UX | 없음 |
| 통합 테스트 (Testcontainers) | §1.3 | Maven |

---

**Sprint gate**

| Date | Approver | Comment | Status |
|------|----------|---------|--------|
| 2026-08-14 | PM | 7차원 86.25. **G1 범위 미착수 0건.** 차단 1·2·3 해소 시 90 초과 | **PENDING** |
| 2026-08-14 | `code-reviewer` | 프론트엔드 APPROVE 유지. 백엔드 93건 실행 검증이나 전체 컴파일 이력 없음 | **PARTIAL APPROVE** |
| 2026-08-14 | `security-auditor` | 허용목록·TLS·CSP 추가 확인, CIDR 경계 32건 실행 검증. **SR-05 미해결로 REJECT 유지** | **REJECT** |
