# Test Plan — 발신번호 (Sender Number Management)

> **Version**: 1.0
> **Date**: 2026-08-17
> **Predecessor**: [REQUIREMENTS-SPEC-SENDERNO.md](../requirements/REQUIREMENTS-SPEC-SENDERNO.md), [DEV-PLAN-SENDERNO.md](DEV-PLAN-SENDERNO.md)
> **Siblings**: [TEST-PLAN.md](TEST-PLAN.md), [TEST-PLAN-LOGIN.md](TEST-PLAN-LOGIN.md), [TEST-PLAN-INSTITUTION.md](TEST-PLAN-INSTITUTION.md)

---

## 1. Test strategy

Three properties of this slice set the strategy, and each one changes what "tested" has to mean.

**Every defect here was invisible from inside its own file.** D-S1 is the extreme case: the list action, the delete query and the grid code are each individually correct, and the system is broken only in the path between them. A test suite organised per-class would have passed against the broken system. **Integration tests that trace a value end-to-end are therefore the primary instrument, not a supplement to unit tests.**

**The most important assertion is a negative one.** The legacy's failure mode was not an error — it was success. A delete that matched nothing reported "정상적으로 처리되었습니다"; a registration whose validation was structurally vacuous accepted anything. So the suite has to assert that operations **fail when they should**, and that they do not report success without changing state. TC-S004-02 is the single most important test in this plan.

**Correctness depends on applications outside the repository.** `KAKAOTALK` decides whether a deleted number can still be used, and `AOA_ADMIN` writes the same table. Some requirements can only be verified against those systems, and one (FR-SNDD-003 on the `FT_SEND` path) cannot be satisfied at all. §5 covers this honestly rather than declaring coverage we do not have.

## 2. Coverage targets

| Level | Target | Notes |
|-------|--------|-------|
| Unit — line | ≥ 80% | Validator, identity model, row mapping |
| Unit — branch | ≥ 70% | Number-format rules have many branches (030/050, 15xx/16xx, special numbers) |
| Integration | Every service endpoint × authorized / unauthorized / cross-tenant | Bypassing the UI entirely — the legacy's controls all lived there |
| E2E | TOP 5 scenarios (§12) | Playwright against the real API |
| Regression | ≥ 1 test per fixed defect | 20 fixed defects; D-S4 accepted, not tested as fixed |
| Security | OWASP Top 10 automated, SAST, secret scan | Per harness §3 |
| Load | 2× NFR-PERF SLA | §8 |
| 7-dimension self-assessment | ≥ 90 / 100 | Per harness |

**Mock policy — revised 2026-08-17.** The original plan called for Testcontainers PostgreSQL with the real `ENCRYPT`/`decrypt`/`masking` functions. **Docker is not permitted in this environment**, so that is unavailable — not delayed, unavailable. The revision matters because the argument for it still stands: D-S1 is a defect in the interaction between a DB function and application code, and no mock reproduces it.

What replaces it, in descending order of strength:

| Tier | Method | What it proves | What it cannot |
|------|--------|----------------|----------------|
| 1 | **A real PostgreSQL dev/test instance carrying the actual `ENCRYPT`/`decrypt`/`masking` functions**, reached over the network | Everything the container would have | — |
| 2 | **DBA-executed SQL** against such an instance, results returned as written evidence | Determinism (S1-01), duplicate counts (S2-01), the legacy send query's behaviour (C-S01) | Continuous regression — it is a point-in-time answer, not a CI gate |
| 3 | **SQL-shape tests** — reflection/resource assertions over mapper SQL, per the established `InstitutionMapperSqlTest` pattern | Identifier and structural regressions, which is the D-S17 and RISK-I05 defect class | That the table exists, that column types match, that `decrypt()` behaves as assumed |
| 4 | Unit tests over pure domain logic (validator, identity, paging arithmetic) | Business rules | Anything touching the database |

**Tier 1 availability is unresolved and is the single largest open question in this test plan** — RISK-S13. Until it is answered, the DB-dependent requirements below are verified at tier 3, and this plan does **not** claim they are covered. `InstitutionMapperSqlTest` already documents itself as "a substitute, not an equivalent"; that phrasing is adopted here deliberately rather than softened.

An in-process PostgreSQL such as `io.zonky.test:embedded-postgres` (Apache-2.0, no GPL entanglement per CODE-004) would restore tier 1 without Docker — **but only if the DDL for the custom `ENCRYPT`/`decrypt`/`masking` functions can be obtained and replayed.** Against stock PostgreSQL it would test functions we invented, which is worse than tier 3 because it looks like tier 1.

`KAKAOTALK` and `AOA_ADMIN` are exercised as SQL against the shared schema rather than as running applications (§5), and therefore sit at tier 2.

## 3. Defect regression suite

One test minimum per fixed defect. The suite is the primary evidence at G3.

| Defect | Severity | Test | Asserts |
|--------|----------|------|---------|
| D-S1 | Critical | TC-S004-01/02/03/04 | Delete actually removes; a non-matching delete **fails**; a display-formatted value is rejected as an identifier; history holds the real number |
| D-S2 | Critical | TC-S001-02, TC-S002-13, TC-S003-11, TC-S004-09 | Every endpoint rejects a non-operator server-side |
| D-S3 | Critical | TC-S001-03/04, TC-S002-14, TC-S003-10, TC-S004-10 | Cross-institution access denied on read and write; enumeration recovers nothing |
| D-S4 | Critical | **Not tested as fixed** — accepted per AMB-S01 | Recorded in the threat model as T-S2 |
| D-S5 | Critical | TC-S004-05/06 | 3 deletions produce 3 distinct history rows; none contains a comma-joined list |
| D-S6 | High | TC-S004-07 | Mid-batch failure leaves nothing deleted |
| D-S7 | High | TC-S002-09, TC-S004-08 | Forced history-write failure rolls the operation back |
| D-S8 | High | TC-S003-01 | A working route to the detail view exists |
| D-S9 | High | TC-S002-07 | A number held by another institution is rejected |
| D-S10 | High | TC-S003-05/06 | Description change writes history and updates 수정자/수정일시 |
| D-S11 | High | TC-S002-02/03 | Direct calls with empty and omitted 발신번호 are rejected |
| D-S12 | Medium | TC-S002-05/06 | 112, 114, 119, 1335 rejected |
| D-S13 | Medium | TC-S002-04 | Alphabetic input rejected |
| D-S14 | Medium | TC-S001-05/06 | Repeat paging is stable; page size is honoured |
| D-S15 | Medium | TC-S002-10/11, TC-S003-07 | Over-length 설명/사유 rejected server-side |
| D-S16 | Medium | New: identity-consistency check | 등록자ID is not a plaintext email while 등록자명 is encrypted |
| D-S17 | Medium | TC-S001-08 | No phantom `ISNM`; binding is by name, not position |
| D-S18 | Medium | TC-S002-12 | The institution-context response contains no 인증키 |
| D-S19 | Low | TC-S001-07 | No query issued before an institution is selected |
| D-S20 | Low | Code review + build | Dead handlers, undefined functions and hardcoded hidden fields absent |
| D-S21 | Low | TC-S001-09 | `RGSR_ID`/`UDT_ID` absent from the list response |

### 3.1 The D-S1 regression deserves a dedicated harness

A single test asserting "delete removes the row" would pass against a system that has merely swapped one broken identity mechanism for another. The regression is therefore a small suite around one property:

> **The value used to identify a row and the value shown to a human are never the same value.**

- TC-S004-03 sends a display-formatted number as an identifier and requires rejection.
- A property test registers numbers, lists them, and asserts the identifier in the response never equals the rendered number.
- The masking policy is changed in a test fixture and the delete suite must still pass — **if a display change can break deletion, the defect class is still present.**

That last test is the one that would have caught the original defect, and it is the reason the regression is written as a property rather than a case.

## 4. Negative-path security suite

Derived from [threat-model-SENDERNO.md](threat-model-SENDERNO.md); every threat with a mitigation has a test.

| Threat | Test |
|--------|------|
| T-S1 | Unauthenticated call to each of the 6 endpoints → 401 |
| T-S3 | Register with a forged 등록자 in the body → session identity stored |
| T-T1 | Register against an institution outside the session's scope → 403 |
| T-T2 | Delete another institution's numbers → 403, nothing deleted |
| T-T3 | SQL metacharacters in 발신번호, 설명, 사유 → bound, no injection |
| T-T4 | 112 / 114 / 1335 / `abcdefgh` / 7 digits / 13 digits → all rejected |
| T-T5 | **Direct SQL insert of a duplicate, bypassing the application** → constraint violation |
| T-T6 | 5,000-character 설명 and 사유 → rejected |
| T-R1 | Multi-delete → per-number history, verified by decrypting each row |
| T-R3 | Force a business rollback → the audit record survives |
| T-R4 | Attempt to update or delete a history row through the application → no such capability exists |
| T-I1 | Enumerate 50 institution codes → nothing returned, 50 `DENIED` audit events |
| T-I2 | Institution-context response → no 인증키 |
| T-I3 | Full E2E run → application logs contain no sender number |
| T-I4 | Full E2E run → **audit store contains no sender number** |
| T-I6 | List response shape → no `RGSR_ID`/`UDT_ID` |
| T-D1 | Request a 10,000-row page → bounded |
| T-D3 | 200 consecutive operations → pool stable |
| T-E1 | Each endpoint called with a non-operator role → 403 |
| T-E2 | Client-company user reaches the module → 403 |

T-T5 and T-I4 are the two worth highlighting. T-T5 tests the constraint rather than the code path, which is the only way to verify a rule that must bind `AOA_ADMIN` too. T-I4 tests that a control did not create the exposure it was meant to reduce.

## 5. Legacy coexistence tests

The slice's correctness depends on two applications this project does not own. These tests run against the shared schema.

| ID | Test | Method | Expected |
|----|------|--------|----------|
| C-S01 | **A deleted number is rejected by the send path** | Register, delete, then execute `KAKAOTALK`'s exact validation SQL (`select dp_no from kkb_dpno_ldgr where is_cd=:c and decrypt(dp_no)=:n`) | Returns nothing → the send is rejected |
| C-S02 | An archived number is fully recoverable | Delete, then restore from `KKB_DPNO_ARCV` | All original column values match |
| C-S03 | The legacy read is unaffected by the new schema | Run every legacy `KKB_DPNO_LDGR` query against the migrated schema | Identical results to pre-migration |
| C-S04 | `BIZ_DB` and `BIZTALK_DB` resolve to the same database | Write through the portal, read through the `BIZ_DB` alias | The row is visible. **Blocks S2 if it fails** — RISK-S01 |
| C-S05 | An `AOA_ADMIN` duplicate insert is blocked | Insert a cross-institution duplicate via the `AOA_ADMIN` code path | Constraint violation |
| C-S06 | **`ADV_KKO_FT_SEND_act.jsp` accepts an unregistered number** | Execute that action's logic with a number absent from the ledger | **Expected to succeed — this test documents the gap** (RISK-S03, T-X1) |

C-S06 is a test that asserts a defect still exists. It is written deliberately: FR-SNDD-003 does not hold on that path, and a suite that silently omitted it would read as though it did. When the gap is closed in `KAKAOTALK`, this test inverts and becomes a regression guard.

## 6. 7-dimension self-assessment

Threshold 90/100 per harness. The dimension most at risk in this slice is **integration correctness**, since three of the four critical defects live between components rather than inside them. The assessment must explicitly score cross-component tracing rather than per-class coverage.

## 7. Security testing (3-stage hook)

| Stage | Scope |
|-------|-------|
| L1 pre-commit | Secret scan — the blind-index HMAC key, if the spike forces that branch, must never enter the repository |
| L2 pre-merge | SAST, dependency CVE scan, authorization annotation sweep across all 6 endpoints |
| L3 pre-release | OWASP Top 10 automated suite, negative-path suite (§4), coexistence suite (§5), audit-content scan |

CVSS ≥ 7.0 unmitigated within this project's control blocks G3. T-S2 (accepted) and T-X1 (external) are declared in the release evidence rather than treated as blockers — see the threat model §6.

## 8. Load testing

| Scenario | Target | 2× load |
|----------|--------|---------|
| List, 100 rows/page | P95 < 1 s | 2× concurrent operators |
| Register | P95 < 1 s | Includes the global uniqueness check |
| Delete, 100 numbers | < 5 s, one transaction | — |
| **List with read auditing enabled** | P95 < 1 s | The audit write must not extend the read path — ADR-SND-019 |

The uniqueness check is the scenario to watch. If spike S1-01 returns "non-deterministic" and the blind index is not in place, every registration is a full scan of `KKB_DPNO_LDGR`; the load test is what determines whether that is acceptable at production volume.

## 9. Test environments

| Environment | Purpose |
|-------------|---------|
| Local | Unit + Testcontainers PostgreSQL with the real DB functions |
| CI | Full suite except load |
| Staging | Coexistence suite (§5) against a `KAKAOTALK` and `AOA_ADMIN` deployment; load tests |

Staging is the only place C-S01 and C-S04 can run meaningfully, which makes staging availability a schedule dependency for S2 rather than a convenience.

## 10. Defect management

Severity follows the programme scale. Two slice-specific rules:

- **Any defect where an operation reports success without changing state is Critical regardless of blast radius.** That is the D-S1 class and it is the failure mode this slice exists to eliminate.
- **Any defect found in `KAKAOTALK` or `AOA_ADMIN` is logged and routed, not fixed here.** It is outside the boundary; silently patching another application's behaviour from this one is how coexistence gaps become invisible.

## 11. Spec parity

Every FR/NFR/CONST in the requirements spec maps to at least one test in this plan. Traceability is maintained in [requirements-matrix.csv](../requirements/requirements-matrix.csv) by `trace-mapper`. **Two requirements are deliberately not fully verifiable:**

- **FR-SNDD-003** — verified on 5 of 6 send paths (C-S01); the 6th is documented by C-S06.
- **NFR-OPS-AUDIT-D02** — retention term unresolved (OI-02); configuration review only.

Both are marked as such rather than counted as passing coverage.

## 12. E2E core scenarios (TOP 5)

| # | Scenario | Covers |
|---|----------|--------|
| 1 | Select an institution, page through its numbers, verify order stability and full display | FR-SND-001…011 |
| 2 | Register a valid number, then attempt the same number from a second institution | FR-SNDC-001…010, CONST-BIZ-D01 |
| 3 | Open detail, edit 설명, verify history and 수정자 | FR-SNDU-001…006 |
| 4 | **Multi-select delete, verify each number is gone, each history row is correct, and the archive holds the full rows** | FR-SNDD-001…008, FR-SNDH-001…003 |
| 5 | Attempt every operation as a non-operator and against another institution | FR-AZ-D01…D05 |

Scenario 4 is the acceptance test for the slice. If it passes end-to-end — including the archive contents and the per-number history — the four critical defects are closed.
