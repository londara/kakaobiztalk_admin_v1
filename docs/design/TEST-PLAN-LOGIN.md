# 테스트계획서 — 로그인 (Authentication module)

> **Version**: 1.0
> **Date**: 2026-08-14
> **Predecessors**: [REQUIREMENTS-SPEC-LOGIN.md](../requirements/REQUIREMENTS-SPEC-LOGIN.md), [DEV-PLAN-LOGIN.md](DEV-PLAN-LOGIN.md)
> **Status**: DRAFT — awaiting G2

---

## 1. Test strategy

### 1.1 The governing principle for this module

Three of the ten legacy defects are **security controls that were written and then disabled** — the strength check, the IP allowlist, the OTP clock window. Each leaves its call site looking correct. A test that merely asserts "login succeeds with a valid password" would pass against all three.

> **Every control must be tested by proving it DENIES something.** A control with only positive-path tests is treated as untested, and therefore as absent.

This shapes the whole plan: the security test suite is not a supplementary pass, it is the primary evidence that this module works.

### 1.2 Pyramid

```
        ┌────────────┐
        │   E2E 5%   │
       ┌┴────────────┴┐
       │  통합 25%    │
      ┌┴───────────────┴┐
      │   단위 70%      │
      └─────────────────┘
```

### 1.3 Test types

| Type | Tool | Owner | Frequency |
|------|------|-------|-----------|
| Unit | JUnit 5 + Mockito | `backend-developer` | Every commit |
| Integration | Testcontainers (PostgreSQL) + `@SpringBootTest` | `backend-developer` + `qa-engineer` | Every PR |
| **Security** | OWASP ZAP, custom negative-path suite | `security-auditor` | **Every PR** |
| E2E | Playwright | `qa-engineer` | Every sprint |
| Frontend component | Vitest + Testing Library | `frontend-developer` | Every commit |
| Load | k6 | `qa-engineer` | Sprint L2 |
| Secret scanning | gitleaks | CI (L1 hook) | Every commit |
| **TOTP interoperability** | Manual, real Google Authenticator | `qa-engineer` | Every sprint |

> Interoperability cannot be unit-tested away. A TOTP implementation can pass every internal test and still fail against the actual app if the secret encoding or time step is wrong — and that failure would surface as "no user can log in."

## 2. Coverage targets

| Metric | Target |
|--------|--------|
| Line | ≥ 80% |
| Branch | ≥ 70% |
| Method | ≥ 85% |
| **`crypto` package** (hashing, TOTP, QR) | **≥ 95%** |
| **`domain` package** (AuthenticationService, AccountPolicy) | **≥ 95%** |

## 3. Defect regression suite

| Defect | Test cases | Type | Assertion |
|--------|-----------|------|-----------|
| L1 — hardcoded secret + 3 phone numbers | TC-LOGIN-001-19 | gitleaks + review | No secret or phone number in source or config committed |
| L2 — unsalted SHA-256 | TC-LOGIN-001-05 | Security | Stored hash is Argon2id with per-user salt |
| L3 — OTP window = 0 | TC-LOGIN-001-02, -03, -04 | Unit | −1/0/+1 accepted; ±2 rejected |
| L4 — secret to `chart.apis.google.com` | TC-LOGIN-002-03, -04 | Integration | **Zero external requests** during registration |
| L5 — IP allowlist discarded | TC-LOGIN-001-18 | Security | Non-allowlisted source **actually denied** |
| L6 — strength check disabled | TC-PWD-01…04 | Unit | Weak passwords rejected |
| L7 — client-controlled IP | TC-LOGIN-001-17 | Security | Forged `X-Forwarded-For` does not reach the audit record |
| L8 — 80-bit secret | TC-LOGIN-002-02 | Unit | New secrets are 160-bit |
| L9 — 15-char password cap | TC-PWD-05 | Unit | ≥64-char password accepted |
| L10 — dead query | Code review | Review | `PTL_RSPR_INFM_R001` absent |

## 4. Negative-path security suite (the core of this plan)

Each row asserts a **denial**. All are release-gating.

| ID | Control | Test | Expected |
|----|---------|------|----------|
| SEC-L01 | Password lockout | 5 wrong passwords | Locked at the 5th |
| SEC-L02 | OTP lockout | 5 wrong codes | Locked at the 5th |
| SEC-L03 | Lockout precedes verification | Correct credentials on a locked account | Rejected without password verification |
| SEC-L04 | Rate limit | Rapid attempts beyond threshold | Throttled independently of lockout |
| SEC-L05 | Account enumeration | Unknown email vs wrong password | Identical response body and comparable timing |
| SEC-L06 | Session fixation | Compare session id before/after login | Regenerated |
| SEC-L07 | Cookie flags | Inspect the session cookie | HttpOnly, Secure, SameSite present |
| SEC-L08 | IP allowlist | Non-allowlisted operator source | Denied |
| SEC-L09 | Forged IP header | `X-Forwarded-For: 1.2.3.4` | Trusted value recorded, not the header |
| SEC-L10 | OTP replay | Reuse a code within its window | Second use rejected |
| SEC-L11 | OTP outside window | Code from ±2 steps | Rejected |
| SEC-L12 | OTP enrolment hijack | Enrol OTP with only a stolen password on an account without one | Requires password **and** is audited and notified (TM-L006) |
| SEC-L13 | OTP re-registration | Register when a key exists | Rejected |
| SEC-L14 | Reset privilege | Non-operator attempts an OTP reset | Rejected and logged |
| SEC-L15 | Self-reset | Operator resets own OTP | Rejected (AMB-L08) |
| SEC-L16 | Terminated account | `JNNG_STTS='9'` attempts login, registration, reset | All three rejected |
| SEC-L17 | Dormant account | 91 days inactive | Rejected |
| SEC-L18 | Password change without current password | Omit current password | Rejected |
| SEC-L19 | Secrets in logs | Full login cycle, inspect logs | No password, OTP code, secret or session id |
| SEC-L20 | Secret at rest | Inspect `OTP_KEY` storage | Encrypted, not plaintext Base32 |
| SEC-L21 | Secret not re-exposed | Every API after registration | Secret never returned |
| SEC-L22 | Counter tampering | Attempt counter manipulation via any client path | No path exists |
| SEC-L23 | Legacy hash write | Force upgrade path, inspect writes | No new SHA-256 hash ever written |
| SEC-L24 | Displaced session | Log in twice, use the first session | First session invalid |

## 5. 7-dimension self-assessment

Weights as standard (완성도 20 · 추적성 15 · 보안 20 · 성능 10 · 가독성 15 · 표준준수 10 · 테스트 10), threshold **90/100**.

> For this module the 보안 dimension is failed outright — regardless of total score — if any negative-path test in §4 is missing or skipped.

## 6. Security testing (3-stage hook)

| Stage | Tools | Timing |
|-------|-------|--------|
| L1 | gitleaks | pre-commit — **directly relevant to L1** |
| L2 | `security-auditor` + SAST + dependency scan + SBOM | CI / PR |
| L3 | prod-gate checklist | before production deploy |

### OWASP Top 10

| ID | Item | Verification | Note |
|----|------|--------------|------|
| A01 | Broken Access Control | SEC-L14/15/16 | Operator reset is the privileged path |
| A02 | **Cryptographic Failures** | SEC-L20/23, TC-LOGIN-001-05 | **Primary risk** — SHA-256 → Argon2id, secret storage |
| A03 | Injection | SAST + unit | Named binds |
| A04 | Insecure Design | [threat-model-LOGIN.md](threat-model-LOGIN.md) | 19 threats, 0 orphans |
| A05 | Security Misconfiguration | SEC-L07/08 | The disabled-controls pattern is exactly this |
| A06 | Vulnerable Components | Dependency scan | New TOTP library (ADR-LOGIN-010) |
| A07 | **Identification & Auth Failures** | Entire §4 | **The module's subject matter** |
| A08 | Data Integrity Failures | SEC-L22 | Counter tampering |
| A09 | Logging & Monitoring | SEC-L19, audit tests | Legacy logs session ids at debug |
| A10 | SSRF | TC-LOGIN-002-03 | The QR helper was effectively an outbound-request bug |

## 7. Load testing

| Scenario | Load | Duration | Pass criteria |
|----------|------|----------|---------------|
| Normal login | 10 logins/s | 15 min | P95 < 1 s (NFR-PERF-L01) |
| 2× peak | 20 logins/s | 10 min | P95 < 2 s, no errors |
| **Argon2id cost under flood** | 100 failed logins/s | 5 min | **CPU does not saturate; rate limiter engages before hashing** (TM-L016) |
| Session registry contention | 50 concurrent logins, same account | 5 min | Exactly one session survives |

> The third scenario is the one that matters. Argon2id is deliberately expensive, which turns an unauthenticated flood into a CPU-exhaustion vector unless rate limiting sits **in front of** hashing. This test verifies the ordering, not just the throughput.

## 8. Test environments

| Env | Purpose | Data |
|-----|---------|------|
| local | Development | Testcontainers + synthetic accounts |
| dev | Integration | Synthetic accounts only |
| staging | QA / load / security | Production-like config, synthetic accounts |
| prod | Live | Real |

> **No real password hash or OTP secret may be copied to a lower environment.** Stronger than the general PII rule: a copied `OTP_KEY` permanently defeats that user's second factor, and unlike a password it cannot be rotated without the user re-enrolling. Migration testing uses **synthetic** legacy SHA-256 fixtures.

## 9. Defect management

| Grade | CVSS | Handling |
|-------|------|----------|
| CRITICAL | ≥ 9.0 | 4 hours |
| HIGH | 7.0–8.9 | Within the sprint |
| MEDIUM | 4.0–6.9 | Next sprint |
| LOW | < 4.0 | Backlog |

## 10. Spec-parity

As with 문자내역, byte-parity is impossible — no runnable legacy (RISK-001). Parity is conformance to the extracted specification, with the **10 defect fixes as approved deviations**.

Two behaviours are deliberately **not** reproduced and need explicit sign-off:
1. The disabled controls are **enabled** — the new system will reject logins the legacy accepted (weak passwords, non-allowlisted IPs, skewed OTP codes)
2. Credential storage changes, so legacy and new hashes are not interchangeable (ADR-LOGIN-011, and see DEV-PLAN-LOGIN §11 on one-way rollback)

## 11. Financial-sector additional tests

| Test | Applies | Note |
|------|---------|------|
| Two-factor enforcement | **Yes** | NFR-SEC-AUTH-L01 — no single-factor path may exist |
| Audit completeness | **Yes** | Every outcome, 5 years |
| PII masking | **Yes** | `CLPH_NO`, `FLNM` in session and notifications |
| Key rotation | **Yes** | Session key; `sender_key` (RISK-L02) |
| Message integrity | No | No message exchange (ADR-004 §7) |
| Amount precision | No | No monetary field |

---

**G2 approval (design gate)**

| Date | Approver | Comment | Status |
|------|----------|---------|--------|
| 2026-08-14 | PM | | PENDING |
| 2026-08-14 | QA Leader | 24 negative-path security tests are release-gating; 보안 dimension fails if any is skipped | PENDING |
