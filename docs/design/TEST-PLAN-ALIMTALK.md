# Test Plan — 카카오 알림톡 (AlimTalk Compose · Send · Template Validation)

> **Version**: 1.0
> **Date**: 2026-08-18
> **Slice**: legacy screens 61, 50
> **Requirements**: [REQUIREMENTS-SPEC-ALIMTALK.md](../requirements/REQUIREMENTS-SPEC-ALIMTALK.md) · 72 scenarios across [UC-ATK-001…004](../requirements/use-cases/)
> **Plan**: [DEV-PLAN-ALIMTALK.md](DEV-PLAN-ALIMTALK.md) · **Threats**: [threat-model-ALIMTALK.md](threat-model-ALIMTALK.md)

---

## 1. Test strategy

Three properties of this slice determine the strategy, and they pull in opposite directions.

**The defects that matter most are the cheapest to test.** D-A1, D-A2 and D-A3 — the three Critical contract defects — are pure serialisation against a checked-in XML file. `ContractConformanceTest` closes all three with no database, no vendor and no container. **This is a sharp contrast with the 발신번호 slice**, where RISK-S13 left the headline defect (a DB-function interaction) unverifiable and the slice went to G3 with its central requirement uncovered. Here the central requirements are covered at tier 1, and that difference should be stated plainly at G3 rather than assumed.

**The boundary that matters most cannot be tested by us.** `COOCON_ALERT` is a third party. A stub can assert what we send; it cannot establish what the vendor does with a repeated `tran_id` (RISK-A07) or whether our `RSMS` envelope is the shape it expects (RISK-A02). Those are **findings, not tests**, and the plan says so rather than dressing stub-based passes as vendor conformance.

**One change is a deliberate behavioural break.** ADR-ATK-022 corrects the template matcher, so inputs the legacy rejected now pass. TC-A004-02 and TC-A004-03 assert the *new* behaviour. They must not be read as parity regressions and must not be "fixed" by restoring the old result.

### 1.1 Verification tiers

| Tier | Mechanism | What it can establish | Docker needed |
|------|-----------|----------------------|---------------|
| **1** | Pure unit + contract tests | Contract conformance, matcher correctness, limits, recipient parsing, `tran_id` shape, redaction | No |
| **2** | `MockRestServiceServer` + local stub server | Request shape, timeout, 5xx, 4xx, retry split, breaker, bulkhead | No |
| **3** | Spring context + PostgreSQL | Transaction boundaries, `SKIP LOCKED` claim, sequence concurrency, unique-constraint path | **Instance required** |
| **4** | Staging vendor endpoint | `RSMS` acceptance, vendor idempotency, real latency | Endpoint required |

Tiers 1 and 2 cover the slice's defect set almost entirely. Tier 3 is constrained by RISK-A12 and tier 4 by RISK-A08 — both tracked, neither hiding.

## 2. Coverage targets

| Target | Value | Applies to |
|--------|------:|-----------|
| Line coverage | ≥ 80 % | new `alimtalk` package |
| Branch coverage | ≥ 70 % | new `alimtalk` package |
| Defect regression tests | 1+ per defect | all 35 |
| Contract conformance | bidirectional, in CI | both IMO contracts |
| 7-dimension self-assessment | ≥ 90 / 100 | sprint gate |
| E2E core scenarios | TOP 5 | §12 |
| Load | 2× NFR-PERF SLA | §8 |
| Secret scan | clean, with a positive fixture | repository |
| Unmitigated CVSS ≥ 7.0 in our control | 0 | threat model |

**On tier 3.** The unresolved question inherited from RISK-S13 — is a PostgreSQL instance reachable? — decides whether outbox concurrency gets real coverage. **`io.zonky.test:embedded-postgres` is a genuine option here in a way it was not for the 발신번호 slice**: that slice needed the proprietary `ENCRYPT`/`decrypt`/`masking` functions, and replaying invented versions would have been actively misleading. This slice needs only standard PostgreSQL — `FOR UPDATE SKIP LOCKED`, sequences, unique constraints — so an in-process instance tests the real semantics. That is a material improvement in the verification position and it should be taken.

The dead `org.testcontainers:postgresql` declaration is removed (RISK-A12).

## 3. Defect regression suite

One test minimum per defect, named for it. Grouped by what they defend.

### 3.1 Contract conformance — D-A1, D-A2, D-A3, D-A7

| Defect | Test | Tier |
|--------|------|------|
| D-A1 `failback` vs `failback_data` | TC-A001-02 + `ContractConformanceTest` | 1 |
| D-A2 five undeclared fields | TC-A001-03 + conformance **reverse direction** | 1 |
| D-A3 missing `order` | TC-A003-02 + conformance | 1 |
| D-A7 no length enforced | TC-A001-04/05, every bounded field at bound and bound+1 | 1 |
| D-A10 four `receiver_number` shapes | TC-A003-07 | 1 |
| D-A13 `emphasis_type` never emitted | TC-A001-03 | 1 |
| D-A14 no batch `reqdate` | TC-A003-06 | 1 |

> **The reverse-direction assertion is the one that earns its keep.** A conformance test that only checks "every contract field is present in our DTO" passes on the legacy payload — `failback` and `msg_type` are extra, not missing. The bidirectional check is what makes D-A1 and D-A2 impossible to reintroduce.

### 3.2 Client behaviour — D-A4, D-A5, D-A6, D-A8, D-A9, D-A11, D-A12, D-A15…D-A23

| Defect | Test | Tier |
|--------|------|------|
| D-A4 broken 초기화 | TC-A001-06 — fill everything, reset, assert all cleared and **no console error** | 1 (E2E) |
| D-A5 cross-tab validation leak | TC-A001-07 | 1 |
| **D-A6 validator rejects valid content** | TC-A004-02, TC-A004-03 — **assert the fix, not parity** | 1 |
| D-A8 AI type unfillable | TC-A001-13 | 1 |
| D-A9 silent button drop | TC-A001-08 | 1 |
| D-A11 `reqdate` unvalidated | TC-A001-11 | 1 |
| D-A12 no recipient validation | TC-A001-14 | 1 |
| D-A15 free-text template code | TC-A001-12 | 1 |
| D-A16 manual template paste | TC-A004-13 — manual and automatic verdicts identical | 1 |
| D-A17 failback unvalidated | TC-A001-09 | 1 |
| D-A18 reset to wrong default | TC-A001-10 | 1 |
| D-A19 select mismatch | TC-A003-08 | 1 |
| D-A20 deprecated clipboard | E2E copy reports real success/failure | 1 |
| D-A21 `window.onload` clobber | E2E — no global handler reassigned, no fixed height | 1 |
| D-A22 no i18n, inline CSS | TC-A001-17 | 1 |
| D-A23 validity from CSS visibility | TC-A003-09 | 1 |

### 3.3 Send path — D-A24…D-A35

| Defect | Test | Tier |
|--------|------|------|
| **D-A24 hardcoded credential** | TC-A002-02 — `gitleaks` over the repo **and** log inspection | 1 + CI |
| **D-A25 colliding `tran_id`** | TC-A002-03, TC-A002-04, plus 500 concurrent accepts asserting zero duplicates | 1 + 3 |
| **D-A26 failure reported after success** | TC-A002-05, TC-A002-06 — and a structural test that no write precedes validation | 1 + 3 |
| D-A27 no transaction, no close | TC-A002-07, TC-A002-08 | 3 |
| D-A28 unanchored phone regex | TC-A002-09 — `abc01012345678` rejected | 1 |
| D-A29 Base64 length threshold | TC-A002-10 — 90 and 91 **decoded** bytes | 1 |
| D-A30 PII + key in logs | TC-A002-11 + `ProfileKey.toString()` + request `toString()` | 1 |
| D-A31 empty recipient list sent | TC-A002-12 | 1 |
| D-A32 `reqdate` overwritten | TC-A002-13 | 3 |
| D-A33 batch via single interface | TC-A003-04 — assert the batch contract is used | 2 |
| D-A34 hardcoded failback subject | TC-A002-14 — no `[쿠콘공지]` literal | 1 |
| D-A35 single-space split | TC-A002-15 — mixed delimiters | 1 |

### 3.4 The D-A26 regression deserves a structural test, not only a scenario

D-A26 is not a wrong value; it is a wrong **ordering**, and orderings regress quietly. TC-A002-05/06 cover the observable symptom, but the durable guard is an architectural assertion:

> **No write to `KKB_ADMIN_SEND_HIS` or the outbox may occur before recipient validation, template match and dedupe have all completed.**

Enforced by an ArchUnit rule (`archunit-junit5` is already a dependency) asserting that the accept path's write methods are unreachable from any code path that has not passed the validator, plus an integration test that injects a failing validator and asserts **zero** rows written anywhere.

The same reasoning covers the legacy's worse variant — the throw *inside* the chunk loop, where 2500 recipients and one bad number delivered chunk 1 and abandoned the rest. TC-A002-06 asserts all valid recipients are delivered and the exclusion is reported, so a partial-delivery-as-total-failure regression fails the build.

## 4. Negative-path security suite

Driven by the threat model; every threat within our control has a test.

| Threat | Test |
|--------|------|
| T-A1 credential exposure | `gitleaks` in CI with the known legacy literal as a **positive fixture** proving the rule fires |
| T-A2 foreign caller ID | TC-A002-18, TC-A002-19 |
| T-A3 foreign template | TC-A002-20, TC-A003-14 |
| T-A4 crafted `is_cd` | TC-A002-22 |
| T-A5 non-operator send | TC-A002-21 |
| T-A6 template tampering | TC-A004-14 |
| **T-A7 regex injection** | Template `가격 (1+1) 행사?` matches itself; metacharacter corpus |
| T-A9 silent field loss | `ContractConformanceTest` |
| T-A14 PII/key in logs | TC-A002-11, log sweep at every level |
| T-A16 unscoped registry read | TC-A001-15, TC-A004-12 |
| T-A17 value in error message | Assert no recipient or key in any 4xx/5xx body |
| **T-A20 backtracking** | 20 adjacent variables × 4 KB non-matching content, within timeout; adjacent-variable template rejected |
| **T-A24 client-supplied key** | Request body carrying `sender_key` — assert ignored/rejected, and that the inbound DTO has no such field |
| T-A25 compose-authz reused for send | Compose-authorized principal denied on send |
| T-A26 dispatcher privilege | Dispatcher performs no authorization decision — code review + test that scope is read from the row |

## 5. Vendor boundary tests (tier 2) and findings (tier 4)

**Tier 2 — fully in-process, no Docker:**

| Check | Mechanism |
|-------|-----------|
| Request shape and `RSMS` envelope | `MockRestServiceServer` request matcher |
| **Read timeout is not retried inline** | Stub delays past read timeout; assert **exactly one** call reaches the stub |
| Connect failure retries ×3 with backoff | Stub refuses connections; assert attempt count |
| 4xx never retried | Assert single call |
| Breaker opens under sustained failure | 20-call window at >50 % failure |
| Breaker half-opens and recovers | Probe after the wait duration |
| Bulkhead bounds concurrency | Concurrent dispatch beyond the limit |
| Metrics exposed | Actuator assertions |

> The read-timeout test is the most important test in this plan. Retrying a read timeout is the difference between one customer notification and two, and the naive resilience4j configuration — retry on any failure — would pass every other test here while failing this one.

**Tier 4 — findings, recorded as such:**

| Question | Outcome |
|----------|---------|
| Does the vendor deduplicate a repeated `tran_id`? | Spike A1-03. **A finding, not a test.** Sets the retry policy |
| Does the vendor accept our `RSMS` envelope? | Spike A1-02 capture comparison. Unverified until captured |
| Real vendor latency | NFR-PERF load target calibration |

If tier 4 is unavailable (RISK-A08), these are **declared unverified at G3** rather than counted as passing on stub evidence. This follows the precedent RISK-S13 set in the 발신번호 slice.

## 6. Legacy coexistence tests

Screen 50 keeps writing `KKB_ADMIN_SEND_HIS` until cutover (CONFLICT-A01), so the constraint must hold across both writers.

| ID | Check |
|----|-------|
| C-A01 | Direct SQL insert of a duplicate `(IS_CD, SERIALNUM)` fails — the constraint binds writers outside the application |
| C-A02 | A legacy-format `tran_id` (`"33"+hh24miss`) coexists with the new format; reconciliation queries do not assume the new shape |
| C-A03 | **No legacy reader uses `SELECT *` against `KKB_ADMIN_SEND_HIS`** — the one case where an added column is not invisible (RISK-A06) |
| C-A04 | New status column defaults such that legacy-written rows remain readable and interpretable |
| C-A05 | Concurrent write from screen 50 and the new path does not deadlock |

C-A03 is a grep over the legacy source, not a runtime test — but it is the check that decides whether RISK-A06's precedent is safe, so it is recorded here rather than left as an assumption.

## 7. Security testing (3-stage hook)

| Stage | Scope |
|-------|-------|
| L1 pre-commit | Secret scan (`gitleaks`) — **fails on the known legacy key literal** |
| L2 pre-merge | SAST, OWASP dependency check, authorization sweep across all endpoints, log-redaction assertions |
| L3 pre-release | Full negative-path suite, threat-model reconciliation, CVSS triage. **CVSS ≥ 7.0 within our control blocks** |

**A go-live gate that is not a code gate:** T-A1's residual — the already-leaked key — is closed by rotation, not by tests. L3 records rotation confirmation as an explicit checklist item.

## 8. Load testing

2× the NFR-PERF SLA, against a stub calibrated to real vendor latency where tier 4 allows.

| Target | SLA | Load |
|--------|-----|------|
| NFR-PERF-A01 contract validation + template match | P95 < 300 ms | 2× peak accept rate |
| NFR-PERF-A02 accept path end to end | P95 < 1 s | 2× peak |
| NFR-PERF-A03 batch at cap acknowledged | < 5 s | cap = 1000 (AMB-A03 assumption, RISK-A14) |
| NFR-SCALE-A01 dispatcher throughput | drain the cap without backlog growth | sustained |
| Backlog behaviour under vendor outage | backlog grows, no errors surfaced to operators, alert fires | stub returning 5xx for 10 min |

The last row is a load test of a **failure** mode, and it is the one that validates ADR-ATK-023's central claim: vendor downtime should degrade to delayed delivery with a visible backlog, not to operator-facing errors.

## 9. Test environments

| Environment | Purpose | Constraint |
|-------------|---------|-----------|
| Local / CI | Tiers 1–2, full defect suite | No Docker (RISK-A12) |
| CI + embedded PostgreSQL | Tier 3 — transactions, `SKIP LOCKED`, sequences | Viable here; needs no proprietary DB functions |
| Staging | Tier 3 against a real instance, E2E | PostgreSQL instance availability unresolved |
| Staging + vendor sandbox | Tier 4 | **May not exist** — RISK-A08 |

## 10. Defect management

Findings are triaged by CVSS; ≥ 7.0 within our control blocks G3. Each fix carries a regression test named for its defect ID. **A defect closed by a structural mechanism (ArchUnit rule, contract test, redacting type) is preferred over one closed by a scenario test**, because the mechanism prevents the class rather than the instance — which is the lesson of a composer that was wrong in four ways for a year with review as its only control.

## 11. Spec parity

Every requirement in the specification maps to at least one test; the matrix records requirement → task → test. Two deliberate non-parity items, both stated so QA does not file them as regressions:

1. **Template matching (D-A6)** — inputs the legacy rejected now pass (PM ruling AMB-A00b).
2. **`sender_key` removal** — a field operators can see and edit today disappears (FR-AZ-A05). Intentional, not a missing feature.

## 12. E2E core scenarios (TOP 5)

| # | Scenario | Covers |
|---|----------|--------|
| **E1** | Compose against a registered template, send to 3 recipients, message delivered, history and audit correct | UC-ATK-001, UC-ATK-002 — the happy path that has never worked end to end with a correct payload |
| **E2** | Batch of 3 items with distinct templates and caller IDs; vendor fails item 2; outcome reported as **partial** naming `order` 2 | UC-ATK-003, D-A26, NFR-OPS-A02 |
| **E3** | 5 recipients, 1 malformed — exclusion surfaced **before** despatch as a choice; operator proceeds; 4 delivered, 1 reported | UC-ATK-002, D-A26, D-A28 |
| **E4** | Message diverging from its registered template rejected before any vendor call; then corrected and sent | UC-ATK-004, T-A6 |
| **E5** | Double-click 발송 → one despatch, second returns the original outcome; then a reserved send fires at `due_at` | FR-ATS-009, FR-ATS-012, D-A25, D-A32 |

E2, E3 and E5 all exist because the legacy got them wrong in ways that reached customers: partial delivery reported as failure, valid sends reported as failed, and duplicate notifications from operators retrying what had already been sent.
