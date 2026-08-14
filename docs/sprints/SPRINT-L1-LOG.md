# Sprint L1 Log — 로그인 모듈 자격증명 경로

> **Sprint**: L1 of 2 · **Date**: 2026-08-14
> **Lead**: `team-leader` · **Plan**: [sprint-L1-tasks.md](../design/sprint-L1-tasks.md)
> **Status**: **INCOMPLETE — carried forward**, see §4

---

## 1. Skeleton generation — step [A]

| Item | Result |
|------|--------|
| Directory tree | Created per [PROJECT-STRUCTURE.md](../design/PROJECT-STRUCTURE.md) §2 |
| Build script | `pom.xml` — Java 17, Spring Boot 3.3.4, MyBatis, Spring Security, Argon2 (BouncyCastle), TOTP library, JaCoCo, ArchUnit, Testcontainers |
| CI pipeline | `.github/workflows/ci.yml` — 4 jobs: secret scan, build+coverage, static rules, dependency scan + SBOM |
| L1 security hook | `hooks/pre-commit-gitleaks.sh` |
| Write-scope mapping | Applied; `src/test/` added to `backend-developer` per [ADR-LOGIN-013](../design/adr/ADR-LOGIN-013-unit-test-location.md) |

## 2. Task completion — steps [B]/[C]

| Task | Description | Status |
|------|-------------|--------|
| T-L1-01 | Spring Security skeleton, `USER_LDGR` mapper | ✅ DONE |
| T-L1-02 | `PasswordHasher` — Argon2id | ✅ DONE |
| T-L1-03 | Legacy SHA-256 verification path | ⛔ **BLOCKED** — ADR-LOGIN-011 |
| T-L1-04 | Upgrade-on-login | ⛔ **BLOCKED** — ADR-LOGIN-011 |
| T-L1-05 | `PasswordPolicy` — real strength validation | ✅ DONE |
| T-L1-06 | `AccountPolicy` — lockout before credential check | ✅ DONE |
| T-L1-07 | `AccountPolicy` — dormancy, status, password age | ✅ DONE |
| T-L1-08 | Counter increment/reset, atomic | ✅ DONE |
| T-L1-09 | Single generic-failure path | ✅ DONE |
| T-L1-10 | `SessionRegistry` — newest-wins | ✅ DONE |
| T-L1-11 | Session cookie flags, timeout | 🟡 PARTIAL — config only; id regeneration needs `SecurityConfig` |
| T-L1-12 | Logout invalidation | ✅ DONE (registry side) |
| T-L1-13 | Orphaned-session reaper | 🟡 PARTIAL — mapper query written, scheduler not wired |
| T-L1-14 | `RateLimiter` | ❌ NOT STARTED |
| T-L1-15 | Trusted client-IP resolution | 🟡 PARTIAL — config + CI rule; proxy config with ops outstanding |
| T-L1-16 | Authentication audit events | ✅ DONE |
| T-L1-17 | Password change flow | ❌ NOT STARTED |
| T-L1-18 | Negative-path security suite | 🟡 PARTIAL — 3 unit suites; the 24 SEC-L* tests not written |
| T-L1-19 | Synthetic legacy-hash fixtures | ⛔ BLOCKED — depends on T-L1-03 |
| T-L1-20 | gitleaks + static rules | ✅ DONE |
| T-L1-21 | Sprint log + assessment | ✅ DONE (this document) |

**11 done · 5 partial · 2 not started · 3 blocked**

### Artifacts produced

| Path | Files |
|------|-------|
| `src/main/java/com/webcash/iris/` | 12 Java classes |
| `src/main/resources/` | `application.yml`, 3 MyBatis mappers, 1 DDL script |
| `src/test/java/` | 3 unit test suites (~50 cases) |
| `mapping/port-log/` | `LOGIN-PORT-LOG.md` — 13 behavioural differences recorded |
| `mapping/trace/` | `requirements-trace.csv` — 60 requirement rows |
| `.github/workflows/`, `hooks/` | CI + L1 hook |

## 3. Verification — step [D]

| Check | Result |
|-------|--------|
| Dependency-free compile (`javac -encoding UTF-8`) | ✅ 6 classes compile clean |
| **Full build (`mvn verify`)** | ❌ **NOT RUN — Maven is not installed on this machine** |
| Unit test execution | ❌ NOT RUN — requires Maven |
| Coverage measurement | ❌ NOT RUN — requires Maven |
| CI pipeline | ❌ NOT RUN — no remote push |

> **This is the sprint's most important limitation.** The Sprint DoD requires "CI 빌드 +
> 테스트 PASS". Twelve classes and roughly 50 test cases have been written but **never
> compiled together or executed**. Only six dependency-free classes were verified.
> Everything touching Spring, MyBatis or JUnit is unproven — including the Argon2id
> configuration, every MyBatis result map, and all three test suites.
>
> Installing Maven and running `mvn verify` is the first action of the next session.
> Until then, treat the implementation as **written but unverified**.

## 4. 7-dimension self-assessment — step [E]

| Dimension | Weight | Score | Basis |
|-----------|--------|-------|-------|
| 완성도 | 20% | **55** | 11 of 21 tasks done; 2 core tasks blocked, 2 not started |
| 추적성 | 15% | **95** | Every class carries `// source:` or `// req:`; port log records 13 differences; trace CSV complete |
| 보안 | 20% | **70** | Argon2id, generic-failure path, atomic counters, append-only audit, gitleaks + 4 static rules. **But**: 24 negative-path SEC-L* tests unwritten, rate limiter absent |
| 성능 | 10% | **40** | No load test possible; Argon2id parameters are unvalidated starting points |
| 가독성 | 15% | **90** | Korean+English Javadoc throughout; design intent documented at decision points |
| 표준 준수 | 10% | **95** | Write scopes respected; ADR-LOGIN-013 raised for the one deviation; DDL review-gated |
| 테스트 커버리지 | 10% | **35** | Tests written but **not executed**; coverage unmeasured |

**Weighted total: 11.0 + 14.25 + 14.0 + 4.0 + 13.5 + 9.5 + 3.5 = 69.75 / 100**

### Result: **69.75 — below the 90 threshold**

Per skill §6 the loop would normally regenerate the weakest dimension. **It does not
apply here**, because the two weakest dimensions cannot be improved by regenerating code:

- **테스트 커버리지 (35)** and **성능 (40)** are low because **Maven is not installed**. No
  amount of rewriting raises them; installing a build tool does.
- **완성도 (55)** is capped by **ADR-LOGIN-011 being undecided**. Writing T-L1-03/04 anyway
  would mean choosing the credential-migration policy by implementation — the decision
  this sprint deliberately refused to make on the PM's behalf.

Regenerating would burn the 5 permitted cycles without moving either. Escalating to the
PM is the correct action, per §7 — and this is the escalation.

## 5. Blocking items for PM

| # | Item | Blocks | Needed |
|---|------|--------|--------|
| 1 | **Maven not installed** | Build, tests, coverage, the entire §3 verification | Install Maven, or confirm an alternative build environment |
| 2 | **ADR-LOGIN-011 undecided** | T-L1-03/04, T-L1-19; CONST-SEC-L01. `PasswordHasher.matchesLegacy` throws by design | Rule on upgrade-on-login vs forced reset — which needs the RISK-L01 exposure answer first |
| 3 | **`sender_key` rotation** | Nothing in code — it is a live exposure in the running legacy | Rotate now, independently (RISK-L02) |
| 4 | DDL review | `CONST-DATA-L01`, session and audit tables | DBA review of `V1__auth_session_audit.sql` |
| 5 | AMB-L03 | `IpAllowlistConfig` scope | Operators-only assumed |

## 6. Notable implementation decisions

Recorded here because they are behavioural choices a reviewer should challenge, not
mechanical porting:

1. **Unmigrated accounts fail closed** (`AuthenticationService.verifyPassword`). With
   ADR-LOGIN-011 open, an account holding only a legacy hash cannot authenticate. This
   is a functional gap, deliberately visible rather than papered over.
2. **OTP does not pass through** in Sprint L1. An account with a registered OTP is
   rejected rather than allowed in unverified, so an unfinished state cannot ship as
   single-factor authentication.
3. **Additive-only DDL.** `PWD_HASH`/`PWD_SCHEME` are new columns; `PWD` is untouched.
   The legacy keeps working against the same table, and the old hash survives for
   rollback — which addresses RISK-L04 more cheaply than the plan assumed.
4. **Unknown `JNNG_STTS` now fails closed.** The legacy fell through and granted access.
   No requirement authorises this change; flagged in the port log §2.5 for the domain owner.
5. **Atomic counter increments.** The legacy wrote an application-computed value, which
   loses updates under concurrent attempts and defeats the lockout (TM-L010).
6. **Append-only enforced structurally.** `AuditMapper` declares no update or delete,
   and the DDL grants `INSERT` only — a compromised application cannot erase its trail.

## 7. 자체 리뷰에서 발견한 결함 / Defect found in self-review

| ID | Finding | Severity | Status |
|----|---------|----------|--------|
| SR-01 | `AuthenticationService` step 4 refused only accounts **without** a registered OTP. An account **with** one passed through with no OTP verification — completing authentication on a password alone. §6 item 2 above claimed the opposite behaviour | **HIGH** — violates NFR-SEC-AUTH-L01 (two factors mandatory) | ✅ FIXED — gated on `iris.auth.otp.verification-implemented`, default `false`; both branches now refuse |

**Why this happened, and why it matters.** The intent was documented correctly in the
sprint log and in the code comment, but the code implemented only half of it. The
comment said "deliberately does not pass through"; the condition let the registered case
through. This is precisely the legacy's own failure pattern — code whose surrounding
documentation describes a control it does not actually apply (defects L5, L6, L3).

It also would not have been caught by the tests written this sprint: none of the three
suites covers `AuthenticationService`, whose unit tests were part of the unwritten
T-L1-18 remainder. **A control asserted only in prose is not a control** — which is the
principle TEST-PLAN-LOGIN §1.1 states and this sprint failed to apply to its own output.

Action: `AuthenticationServiceTest` covering all seven exit paths is now the first item
of T-L1-18 in Sprint L2, ahead of the 24 SEC-L* tests.

---

**Sprint gate**

| Date | Approver | Comment | Status |
|------|----------|---------|--------|
| 2026-08-14 | PM | 7-dimension 69.75 < 90; escalated per §7 with 5 blocking items | **PENDING** |
| 2026-08-14 | `code-reviewer` | Cannot issue APPROVE — the code has never been compiled or executed | **PENDING** |
| 2026-08-14 | `security-auditor` | 조건부승인 possible on design; the 24 negative-path tests are outstanding | **PENDING** |
