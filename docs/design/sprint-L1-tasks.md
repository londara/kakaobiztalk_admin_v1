# Sprint L1 Task List — Credential path, account policy, session

> **Sprint**: L1 of 2 · **Duration**: 2 weeks
> **Goal**: A credential subsystem in which **every account policy provably denies something** — lockout, dormancy, status, password age and strength each rejecting a request in a test.
> **Predecessor**: [DEV-PLAN-LOGIN.md](DEV-PLAN-LOGIN.md) · **Status**: DRAFT — PM + Leader agreement required

---

## Sprint goal rationale

The legacy has all of these controls; three of them do nothing because the code that enforced them was commented out. This sprint's exit criterion is therefore not "login works" but **"each control has been observed to refuse."** Positive-path tests alone would have passed against the broken legacy.

## Blocking decision

> ⚠ **T-L1-03 and T-L1-04 cannot start until the PM rules on [ADR-LOGIN-011](adr/ADR-LOGIN-011-credential-migration.md)** — upgrade-on-login (A) or forced reset for all (B). The choice depends on whether the legacy password database has ever been exposed (RISK-L01, TM-L002). Everything else in this sprint proceeds regardless.

## Task list

| ID | Task | Requirements | Owner | Depends on | Est. |
|----|------|--------------|-------|------------|------|
| **T-L1-01** | Spring Security skeleton; `USER_LDGR` MyBatis mapper with the fields from `USER_LDGR_R006` | CONST-TECH-L01, CONST-DATA-L01 | `backend-developer` | — | 1 d |
| **T-L1-02** | `PasswordHasher` — Argon2id, parameters tuned against measured capacity | FR-LOGIN-005 | `backend-developer` | T-L1-01 | 1 d |
| **T-L1-03** | ⚠ Legacy SHA-256 verification path, **write-disabled by construction** | ADR-LOGIN-011 (A) | `backend-developer` | ADR-LOGIN-011 ruling | 0.5 d |
| **T-L1-04** | ⚠ Upgrade-on-login — re-hash and replace on successful legacy verification; per-account scheme flag; retain legacy hash for the rollback window | ADR-LOGIN-011 (A), RISK-L04 | `backend-developer` | T-L1-03 | 1 d |
| **T-L1-05** | `PasswordPolicy` — **real** strength validation (fixes L6), min 12 / max ≥64 (fixes L9), history of 3 | FR-PWD-003/004/005 | `backend-developer` | T-L1-02 | 1 d |
| **T-L1-06** | `AccountPolicy` — lockout at 5 for both counters, evaluated **before** credential verification | FR-LOGIN-003/010, SEC-L03 | `backend-developer` | T-L1-01 | 1 d |
| **T-L1-07** | `AccountPolicy` — 90-day dormancy, `JNNG_STTS` 0/2/8/9, 90-day password age, `PWD_INIT_YN` | FR-LOGIN-012/013/014/015 | `backend-developer` | T-L1-06 | 1 d |
| **T-L1-08** | Counter increment / reset logic; no client-influenced path | FR-LOGIN-003/004/010, SEC-L22 | `backend-developer` | T-L1-06 | 0.5 d |
| **T-L1-09** | Single generic-failure response path for unknown-account and wrong-password, with comparable timing | FR-LOGIN-002, SEC-L05 | `backend-developer` | T-L1-02 | 0.5 d |
| **T-L1-10** | `SessionRegistry` — shared store, newest-login-wins, displaced-session message | FR-LOGIN-016/017, ADR-LOGIN-012 | `backend-developer` | T-L1-01 | 1.5 d |
| **T-L1-11** | Session id regeneration at login; cookie HttpOnly/Secure/SameSite; 30-min inactivity | NFR-SEC-SESSION-L01/L02, SEC-L06/L07 | `backend-developer` | T-L1-10 | 0.5 d |
| **T-L1-12** | Logout — invalidate container session and registry entry | FR-LOGIN-023, SEC-L24 | `backend-developer` | T-L1-10 | 0.5 d |
| **T-L1-13** | Orphaned-session reaper for unclean shutdown | ADR-LOGIN-012 §4.3 | `backend-developer` | T-L1-10 | 0.5 d |
| **T-L1-14** | `RateLimiter` — per account and per source, positioned **before** password hashing | FR-LOGIN-025, RISK-L07 | `backend-developer` | T-L1-01 | 1 d |
| **T-L1-15** | Trusted client-IP resolution at the proxy boundary; raw headers ignored (fixes L7) | FR-LOGIN-019, SEC-L09 | `backend-developer` + ops | T-L1-01 | 0.5 d |
| **T-L1-16** | Authentication audit events — success, failure, lockout, password change; no credential material | NFR-OPS-AUDIT-L01/L02, SEC-L19 | `backend-developer` | T-L1-01 | 1 d |
| **T-L1-17** | Password change flow, current password required | FR-PWD-001/002/006, SEC-L18 | `backend-developer` | T-L1-05 | 1 d |
| **T-L1-18** | **Negative-path security suite** — SEC-L01…L07, L09, L16…L19, L22…L24 | TEST-PLAN-LOGIN §4 | `security-auditor` | T-L1-11, T-L1-16 | 2 d |
| **T-L1-19** | Synthetic legacy-hash fixtures for migration testing — **no production hash copied** | TEST-PLAN-LOGIN §8 | `qa-engineer` | T-L1-03 | 0.5 d |
| **T-L1-20** | gitleaks in CI; static rules for MD5/SHA-256 password use and credential logging | RISK-L02/L03, SEC-L19/L23 | `security-auditor` | T-L1-01 | 1 d |
| **T-L1-21** | Sprint log + 7-dimension self-assessment | DEV-PLAN-LOGIN §9 | `docs-writer` | all | 0.5 d |

**Estimated total: ~18.5 person-days** (17 if ADR-LOGIN-011 option B removes T-L1-03/04 and adds a reset-flow task instead).

## Definition of Done

- [ ] Argon2id hashing in place; **no code path writes a SHA-256 hash** (SEC-L23)
- [ ] **Every control in §4 of the test plan demonstrably denies a request** — this is the sprint's real exit criterion
- [ ] Lockout evaluated before credential verification (SEC-L03)
- [ ] Unknown-account and wrong-password responses indistinguishable (SEC-L05)
- [ ] Session id regenerated at login; cookie flags correct (SEC-L06/L07)
- [ ] Forged `X-Forwarded-For` cannot reach the audit record (SEC-L09)
- [ ] Rate limiting engages **before** hashing (RISK-L07)
- [ ] No credential material in logs (SEC-L19)
- [ ] gitleaks green; no secret in source
- [ ] `crypto` and `domain` coverage ≥ 95%
- [ ] 7-dimension ≥ 90, with 보안 not failed on any missing negative-path test

## Capacity note

18.5 person-days against 10–20 available is at the limit. Cut order if it slips:

1. **T-L1-13** (session reaper) — an operational nicety; stale entries can be cleared manually short-term
2. **T-L1-17** (password change flow) — defer to Sprint L2 **only if** ADR-LOGIN-011 option A is chosen, since option B makes password reset the cutover mechanism and it cannot slip
3. **T-L1-05** password history (keep strength validation; defer the history-of-3 part)

**Do not cut** T-L1-18 or T-L1-20. They are the tests and scans that distinguish this implementation from the legacy's — where the controls existed but did nothing.

## Sprint risks

| Risk | Mitigation |
|------|-----------|
| **ADR-LOGIN-011 ruling not received** (RISK-L01) | Blocks T-L1-03/04 only. Escalate on day 1; sequence other tasks first. If undecided by mid-sprint, proceed with option B, which needs no exposure determination |
| Argon2id parameter tuning needs capacity data | Measure on the target environment; do not copy parameters from documentation |
| Shared session store not yet chosen (ADR-LOGIN-012 §4.3) | Decide in the first two days; blocks T-L1-10 |
| Trusted IP resolution depends on proxy configuration owned by ops (T-L1-15) | Request on day 1 |

---

**Agreement**

| Date | Role | Name | Status |
|------|------|------|--------|
| 2026-08-14 | PM | | PENDING |
| 2026-08-14 | Build Team Leader | `code-reviewer` | PENDING |
