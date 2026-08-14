# Sprint L5 Log — 속도 제한, OTP 재사용 방지, 정리 스케줄러

> **Sprint**: L5 · **Date**: 2026-08-14
> **Lead**: `team-leader` · **Previous**: [SPRINT-L4-LOG.md](SPRINT-L4-LOG.md)
> **Status**: **PARTIAL** — 백엔드 보안 통제 완료, 프론트엔드 미착수

---

## 0. ⚠ Sprint 시작 시 발견한 보안 회귀 / Security regression found at sprint start

| ID | Finding | Severity | Status |
|----|---------|----------|--------|
| SR-05 | **`application.yml` 에 AES-256 키가 하드코딩되었다.** `secret-key: ${IRIS_OTP_SECRET_KEY:E0lV/9B+...}` — 환경변수 미설정 시 사용되는 <b>기본값</b>으로 실제 키가 소스에 기입됨. 두 줄 위 주석은 "NO DEFAULT: the application must fail to start rather than encrypt credential material under a key that is published in source" 라고 명시한다 | **HIGH** | ⚠ **미해결 — PM 판단 필요** |

**위반 항목:** ADR-007 · CONST-SEC-L02 · NFR-SEC-SECRET-L01 · harness §5(시크릿 0) · §10(PII 키 관리)

**이것은 레거시 결함 L1 과 동일한 패턴이다.** `apc_login_proc_act.jsp` 의 하드코딩된
Kakao `sender_key` 를 제거하는 것이 이 프로젝트의 목표 중 하나인데, 같은 형태의 결함이
신규 코드에 들어왔다. 버전 관리에 포함된 키로 암호화된 OTP 비밀키는 실질적으로 평문
저장이다 — 저장소에 접근 가능한 누구든 복호화할 수 있다.

*This is the same pattern as legacy defect L1. Removing the hardcoded Kakao `sender_key`
is one of this project's goals, and a defect of identical shape entered the new code. An
OTP secret encrypted under a key held in version control is effectively stored in plaintext.*

**되돌리지 않았다** — 의도된 변경으로 통보받았기 때문이다. 다만 다음이 필요하다:

1. 로컬 기동 편의가 목적이라면 기본값은 `application-local.yml`(개발 프로파일)에만 두고
   기본 설정에서는 제거할 것
2. **해당 키가 커밋되었다면 침해된 것으로 간주**하고, 그 키로 암호화된 모든 OTP 비밀키를
   재발급해야 한다 (= 전 사용자 OTP 재등록)
3. gitleaks(L1 hook)가 이 값을 탐지하지 못한 이유를 확인할 것 — 탐지 규칙에 공백이 있다

## 1. 완료 항목 / Completed

| Task | Description | Requirements |
|------|-------------|--------------|
| L5-01 | `RateLimiter` — 슬라이딩 윈도우, 계정+출처 이중 한도 | **FR-LOGIN-025**, RISK-L07, TM-L016 |
| L5-02 | 로그인 컨트롤러에 **해싱 앞단** 배치 | RISK-L07 |
| L5-03 | `OtpReplayGuard` — 코드 단일 사용 강제 | **TM-L004** (§3 참조) |
| L5-04 | `AuthenticationService` — 검증 성공 후 소비 처리 | TM-L004 |
| L5-05 | `MaintenanceScheduler` — 인메모리 정리 + 세션 reaper | FR-LOGIN-025, ADR-LOGIN-012 §4.3 |
| L5-06 | `qa/drivers/LimiterDriver` — 20건 실행 검증 | TEST-PLAN-LOGIN §1.1 |

**신규 파일 4개 · 수정 3개 · Java 파일 총 36개**

## 2. 왜 속도 제한이 잠금과 별개인가 / Why rate limiting is not lockout

계정 잠금(FR-LOGIN-003/010)만 있으면 **credential stuffing 을 막지 못한다.** 공격자가
계정 1,000개에 각 4회씩 시도하면 어떤 잠금도 유발하지 않는다. 잠금은 <b>한 계정</b>에
대한 반복을, 속도 제한은 <b>한 출처</b>의 광범위 시도를 막는다.

실행 검증에서 이 구분을 직접 확인했다 — `src3 BLOCKED_differentAccounts`: 서로 다른 계정
3개로 시도해도 출처 한도에서 차단된다.

*Verified directly in execution: three attempts against three different accounts are still
blocked by the source limit.*

## 3. 위협 모델 불일치 해소 / Threat-model inconsistency closed

Sprint L4 §9 에서 보고한 문제를 해결했다. `threat-model-LOGIN.md` TM-L004 는 완화책으로
"단일 사용 강제"를 기재했으나 **구현되지 않은 상태**였다.

**기재를 지우는 대신 구현했다.** 문서가 존재하지 않는 통제를 주장하는 상태는 레거시의
실패 유형(L3 시간창 0, L5 IP 목록 무효, L6 강도검사 비활성 — 모두 주석 처리된 통제)과
정확히 같다. 우리 산출물에서 같은 패턴을 재생산하지 않는 것이 중요했다.

*Implemented rather than deleting the claim. A document asserting an absent control is
exactly the legacy's failure pattern; reproducing it in our own artifacts was the thing to
avoid.*

**남은 한계:** 인메모리 구현이므로 다중 인스턴스에서는 인스턴스 A 에서 쓴 코드를 B 에서
재사용할 수 있다. 위협 모델에 **잔여 위험으로 명시 필요** — 통제가 부분적임을 문서가
정확히 반영해야 한다.

## 4. 검증 — step [D]

| Check | Result |
|-------|--------|
| `SecretCipher` | ✅ 12/12 |
| `AccountPolicy` + `PasswordPolicy` | ✅ 29/29 |
| `TemporaryPasswordGenerator` × 정책 (20만 표본) | ✅ 위반율 측정·대응 확인 |
| **`RateLimiter` + `OtpReplayGuard`** | ✅ **20/20** |
| **누적 실행 검증** | ✅ **61 assertions + 20만 표본 통계** |
| CI 정적 규칙 5건 | ✅ clean |
| provenance | ✅ 36/36 |
| `mvn verify` | ❌ 미실행 |

### 실행으로 확인된 사실

- **슬라이딩 윈도우가 정확하다.** 61초 후 초기 항목이 창을 벗어나 재허용됨 —
  고정 버킷이면 경계에서 2배 버스트가 통과한다
- **대소문자 우회 차단.** `U@X.COM` 도 같은 한도에 귀속
- **재사용 거절 후 보존기간 경과 시 재허용.** 121초 후 같은 코드 재사용 가능 —
  메모리가 무한히 커지지 않으면서도 창 안에서는 막힌다
- **정리 동작 확인.** 만료 키 제거, `size()` 0 복귀

## 5. 7 차원 자체 평가 — step [E]

| Dimension | Weight | Score | Δ vs L4 | Basis |
|-----------|--------|-------|---------|-------|
| 완성도 | 20% | **92** | +2 | 52/61 요구사항. 잔여 6건 중 4건이 프론트엔드·TLS·부하 |
| 추적성 | 15% | **95** | — | 36/36 provenance |
| 보안 | 20% | **80** | **−10** | 통제는 강화(속도 제한·재사용 방지)되었으나 **SR-05 미해결**. 하드코딩 키는 §5 "시크릿 0" 직접 위반 |
| 성능 | 10% | **40** | — | 부하 테스트 불가 |
| 가독성 | 15% | **92** | — | — |
| 표준 준수 | 10% | **95** | — | — |
| 테스트 커버리지 | 10% | **66** | +4 | 실행 61건 누적 |

**가중 합계: 18.4 + 14.25 + 16.0 + 4.0 + 13.8 + 9.5 + 6.6 = 82.55 / 100**

### 결과: **82.55 — 전 sprint 대비 하락** (83.75 → 82.55)

**다섯 sprint 중 처음으로 점수가 내려갔다.** 원인은 구현 품질이 아니라 **SR-05** 다.
보안 차원(가중치 20%)을 90 → 80 으로 내렸다. 하드코딩된 암호화 키는 개별 통제를 얼마나
잘 만들었는지와 무관하게 그 통제가 보호하는 대상을 무력화한다.

**점수를 올리기 위해 SR-05 를 축소 평가하지 않았다.** 그렇게 하면 이 평가 자체가
무의미해진다.

*The first decline in five sprints, caused by SR-05 rather than by implementation quality.
A hardcoded encryption key defeats what the controls protect, regardless of how well the
controls are built — and understating it to protect the score would make the assessment
worthless.*

## 6. PM 차단 항목 / Blocking items

| # | Item | Blocks | Priority |
|---|------|--------|----------|
| 1 | **SR-05 — 하드코딩 AES 키** | 보안 차원, G3 게이트 | **최우선 (신규)** |
| 2 | **Maven 미설치** | JUnit·커버리지·부하·Spring/MyBatis/TOTP 검증 | 높음 |
| 3 | DDL 검토 | 4개 신규 테이블/컬럼 | 높음 |
| 4 | **`sender_key` 회전** | 운영 중 시스템 실제 노출 | 높음 |
| 5 | ADR-LOGIN-011 | 마이그레이션 비용 | 중간 |
| 6 | AMB-L03 | IP 허용목록 범위 (FR-LOGIN-024) | 중간 |

## 7. 남은 작업 / Remaining — 6건

| Requirement | Item | Note |
|-------------|------|------|
| FR-LOGIN-022, NFR-USE-L01 | **React 로그인·등록·변경 화면** | 프론트엔드 **0%** — 최대 잔여 항목 |
| FR-LOGIN-024 | IP 허용목록 실제 차단 | AMB-L03 미결 |
| FR-LOGIN-021 | 관리자 로그인 알림 (설정 기반) | L1 결함 대응 포함 |
| NFR-SEC-CHANNEL-L01 | TLS 강제 | 배포 구성 사안 |
| NFR-PERF-L01 | 부하 테스트 | Maven 필요 |

**백엔드 보안 통제는 이번 sprint 로 완료되었다.** 잔여 6건 중 4건은 프론트엔드·배포
구성·부하 테스트이며, 코드 작성이 아니라 환경과 결정에 달려 있다.

---

**Sprint gate**

| Date | Approver | Comment | Status |
|------|----------|---------|--------|
| 2026-08-14 | PM | 7차원 82.55, 전 sprint 대비 하락. SR-05 해소 전 승인 권장하지 않음 | **PENDING** |
| 2026-08-14 | `code-reviewer` | 61건 실행 검증 누적. 전체 컴파일 이력 없음 | **PENDING** |
| 2026-08-14 | `security-auditor` | 속도 제한·재사용 방지 실행 검증 완료. **SR-05 로 인해 조건부승인 철회 — REJECT** | **REJECT** |
