# Test Plan — 톡전송 내역 (BizTalk Transaction History)

> **Version**: 1.0
> **Date**: 2026-08-19
> **Author**: `qa-engineer`
> **Predecessor**: [REQUIREMENTS-SPEC-TALK.md](../requirements/REQUIREMENTS-SPEC-TALK.md)
> **Companion**: [DEV-PLAN-TALK.md](DEV-PLAN-TALK.md), [threat-model-TALK.md](threat-model-TALK.md)

---

## 1. Test strategy

Read-only slice, one datasource, no outbound call, no write path. That removes whole categories — no transaction-boundary tests, no idempotency, no retry, no compensating action.

What replaces them is a verification problem this slice has more of than any predecessor: **most of its defects are invisible to any single-layer check.** Eleven of thirty-four are one decision implemented twice and drifted apart; the worst (D-T1) is a button whose every individual component works correctly and whose composition is a plaintext PII extraction. No mapper test, contract test or component test finds that. Only a test that asserts *what the feature means* does.

The plan therefore leans on three test shapes that the earlier slices used sparingly:

- **Set equalities** rather than property lists — "the export's rows equal the list's rows" (TC-T004-01), "link presence equals server serviceability" (TC-T001-13).
- **Property tests over deliberately hostile fixtures** — tied timestamps, three serial lengths, one transaction per registry outcome.
- **Endpoint inventory as a test** — the absence of a capability (FR-AZ-T06) is asserted, not assumed.

## 2. Coverage targets

| Level | Target | Notes |
|-------|--------|-------|
| Unit — line | ≥ 80% | |
| Unit — branch | ≥ 70% | |
| Domain classes (`TransactionSerial`, registries, `TalkPeriodPolicy`, `PrincipalScope`) | ≥ 95% line | These carry the correctness of the slice |
| Mappers | 100% of statements exercised | Against real PostgreSQL, not H2 — `decrypt()` / `masking()` / `lpad` semantics are the point |
| Defect regression | 34 / 34 defects, ≥ 1 named test each | |
| Threat coverage | 16 / 16 STRIDE threats | |

**Coverage is necessary and not sufficient here, and the plan says so.** A suite that covers every line of `fn_makeExcel`'s Java equivalent would still have passed on the legacy: each statement executes, each returns a value, and the composed behaviour is the defect. §3.1 exists for exactly this reason.

## 3. Defect regression suite

One test per defect, named for it. Grouped by what the test has to be able to see.

### 3.1 Compositional — the test must assert meaning, not structure

| Test | Defect | Assertion |
|------|--------|-----------|
| TC-T004-01 | D-T1 | For each of 8 filter combinations, the exported row set **equals** the list's row set for the same filters — same rows, same order, same columns, same masking |
| TC-T004-02 | D-T1, D-T32 | Every filter on the screen changes the exported file. Parameterised over all filters — a filter that is silently ignored fails here |
| TC-T001-13 | D-T13 | Over a representative day, `row.detailAvailable == true` **iff** the detail service returns a non-error response for that row. Includes `ADV_KKO_AT_SEND2` and an unmapped code |
| TC-T002-05 | D-T13 | An unmapped API service returns an explicit unsupported response — **asserted to be distinguishable from an empty result** |
| TC-T003-06 | D-T7 | A 친구톡 message opened from the detail list returns 친구톡 data. Fails if the channel is routed from the row's own `MSG_TYPE` |

### 3.2 Access control

| Test | Defect | Assertion |
|------|--------|-----------|
| TC-T001-01 | — | All five endpoints reject an unauthenticated request (401) |
| TC-T001-02 | D-T2 | All five endpoints reject a tenant principal (403); no menu entry rendered |
| TC-T002-01 | D-T2 | A tampered institution code in the detail request does not widen or shift the result |
| TC-T003-01 | D-T5 | A message key belonging to another institution returns 404 — enumerated over 50 keys |
| TC-T001-14 | D-T3 | **Endpoint inventory**: no endpoint in the application returns unmasked numbers over a date range. Asserted against the full route table, not a list of known routes |
| TC-T001-15 | — | Out-of-scope values are ignored, not rejected — the error body is identical whether the institution exists (TM-T10) |

### 3.3 Identity and normalisation

| Test | Defect | Assertion |
|------|--------|-----------|
| TC-T001-09 | D-T25 | Serial entered padded, unpadded and part-padded matches the same transaction |
| TC-T002-09 | D-T9 | Detail lookup at 10, 14 and 20 characters returns that transaction's messages. **The 20-character case fails on the legacy rule** |
| TC-T002-10 | D-T9 | Property: `normalise(render(x)) == render(x)` over every serial length observed by T1-01 |

### 3.4 Ordering, paging and query shape

| Test | Defect | Assertion |
|------|--------|-----------|
| TC-T001-06 | D-T10 | Over a 100-row fixture **on a single timestamp**, the union of all pages equals the full set exactly once |
| TC-T001-05 | D-T11 | The list response carries a total count matching a `count(*)` on the same predicate |
| TC-T001-07 | D-T24 | 32-day range, inverted range, malformed date and `00000000`/`99999999` are each rejected server-side with 400 |
| TC-T001-08 | D-T24 | An omitted time bound means the whole day; no `999999` value reaches the query |
| TC-T001-10 | D-T15 | Filtering by the API value displayed in the grid returns that row |
| TC-T001-11 | D-T26 | An unresolved institution code renders the code with a marker, never blank |
| TC-T001-12 | D-T27, CONST-SEC-T01 | Contract test: the list response's field set is **exactly** the nine bound fields; the selector's is exactly two |
| TC-T001-16 | D-T28 | No query is issued before the API selector resolves |
| TC-T001-04 | D-T29 | The filter's option set and the column's label set enumerate identically |
| TC-T001-03 | D-T31, D-T32 | Static check: no `SELECT *` in the package; no unreferenced service registered |

### 3.5 Detail behaviour

| Test | Defect | Assertion |
|------|--------|-----------|
| TC-T002-02 | D-T8 | All four filters narrow the result for **both** channels — parameterised over channel × filter |
| TC-T002-03 | D-T21 | An 11-digit recipient number is accepted and matches |
| TC-T002-04 | D-T7 | 친구톡 rows report FT in the 유형 column |
| TC-T002-06 | D-T22 | A row with a null result code is reachable under 미수신 and is excluded by neither 성공 nor 실패 silently |
| TC-T002-07 | D-T30 | The client's page count derives from the server's total |
| TC-T003-02 | D-T18 | 발신번호 and 수신자번호 are **non-empty** on a fixture that has them. Fails on the unaliased-`decrypt` legacy query |
| TC-T003-03 | D-T17 | All four timestamps render a 4-digit year |
| TC-T003-04 | D-T19 | Changing a message's status between list and detail does not empty the popup |
| TC-T003-05 | D-T20 | An unknown result code displays the code with a marker; a known one displays code + text; a null one displays a null marker |
| TC-T003-07 | D-T33 | No field on the detail popup is editable, across all three tabs |
| TC-T003-08 | D-T34 | Popup title matches the screen |

### 3.6 Export

| Test | Defect | Assertion |
|------|--------|-----------|
| TC-T004-03 | D-T14 | Every export input is rejected when it violates its declared length or character rule |
| TC-T004-04 | D-T4 | CR, LF and control characters in any filter are rejected at validation and cannot reach a header. Fuzzed over the date and serial fields |
| TC-T004-05 | D-T34 | Exactly one `Content-Type` header, naming the xlsx media type |
| TC-T004-06 | D-T12 | Row ceiling enforced; over-ceiling requests are refused with 400 naming a range that fits — **never a truncated file** |
| TC-T004-07 | D-T23 | A forced server failure produces a visible error in the UI |
| TC-T004-08 | — | Export audit event carries the row count actually written |
| TC-T004-09 | D-T16 | The export declares its own contract; a contract-id mismatch fails the build |
| TC-T004-10 | D-T34 | Workbook geometry: one header cell per column, title merge spans the column count |

## 4. PII masking suite

Separate from the regression suite because it must hold at **every** boundary, and the legacy failed it at all of them.

| Test | Assertion |
|------|-----------|
| TC-PII-01 | No API response in the slice contains an 11-digit unmasked number — asserted by regex over the serialised body of every endpoint, parameterised over all five |
| TC-PII-02 | The exported workbook contains no unmasked number — asserted by reading the produced file, not the source rows |
| TC-PII-03 | No application log line contains an unmasked number, under DEBUG |
| TC-PII-04 | Mapper-level: `masking()` is applied at the outermost projection of every talk-family query. Static assertion over the mapper XML |
| TC-PII-05 | Search against a full recipient number still matches — masking must not break filtering (the reason for the outermost placement) |

## 5. Registry and classification suite

| Test | Assertion |
|------|-----------|
| TC-REG-01 | **Rewritten 2026-08-19.** The allow-list is reconciled against `API_SVC_CD` values **observed in transactions**, in both directions: observed-but-unclassified (with counts) and configured-but-unseen. This replaces the startup check against `FT_OPENAPI_INFO`, which validated against the API master rather than against transactions that actually happened — the weaker of the two signals. `TalkApiReconciliationTest` |
| ~~TC-REG-02~~ | ~~Startup failure on an unmapped-but-classified code~~ — **RETIRED as unreachable 2026-08-19.** One config entry carries code and channel together, so the state is unrepresentable rather than merely guarded, and a test for an unrepresentable state is not a passing test. Covered instead by `BizTalkApiRegistryTest$LinkMatchesLookup` (ADR-TLK-024 §5, ADR-TLK-026 §5) |
| TC-REG-03 | The reconciliation report counts transactions per unclassified `API_SVC_CD`; a seeded excluded transaction appears in it |
| TC-REG-04 | A non-BizTalk transaction (`ADV_COM_GET_STATUS`) does not appear in the list |

## 6. 7-dimension self-assessment

Threshold **≥ 90 / 100**, harness default, unchanged.

## 7. Security testing (3-stage hook)

| Stage | Scope |
|-------|-------|
| L1 pre-commit | Secret scan; `SELECT *` prohibition in the package |
| L2 pre-merge | SAST; OWASP Top 10 automated; the §4 masking suite; endpoint inventory (TC-T001-14) |
| L3 pre-release | `security-auditor` review against threat-model-TALK §3; CVSS scoring of anything new; confirmation that no unmitigated threat ≥ 7.0 remains |

## 8. Load testing

2× the NFR SLA, per harness default.

| Test | Target | 2× load |
|------|--------|---------|
| TC-LOAD-01 | NFR-PERF-T01 — list P95 < 3 s at the 31-day cap, 100 rows/page | 2× concurrent operators |
| TC-LOAD-02 | NFR-PERF-T02 — detail P95 < 1 s | 2× concurrency; run **with and without** `lpad` in the predicate so ADR-TLK-025's sargability claim is measured, not asserted |
| TC-LOAD-03 | NFR-SCALE-T01 — export heap flat from 1k to 100k rows | Heap profile, not wall clock |
| TC-LOAD-04 | RISK-T08 — paging correctness at realistic tie density | Ties are the normal case on this screen |

TC-LOAD-01 also fixes the export row ceiling (RISK-T09) before G3.

## 9. Test environments

| Environment | Purpose |
|-------------|---------|
| Local | Unit + domain, no DB |
| Integration | **Real PostgreSQL** via `io.zonky.test:embedded-postgres` (Apache-2.0) — Docker is prohibited (RISK-T13), so Testcontainers is unavailable. H2 cannot reproduce `lpad`'s truncation, which carries D-T9 |
| Staging | E2E, load, masking suite against synthesised fixtures (RISK-T10) |

**What this environment can and cannot verify, stated once (RISK-T13 — CLOSED).**

> **Corrected 2026-08-19.** This section previously said no PostgreSQL tier was reachable because Docker is prohibited. **That inference was wrong.** `io.zonky.test:embedded-postgres` (Apache-2.0) runs a real PostgreSQL binary as a process, needs no Docker, and starts in this environment. `LpadTruncationTest` and `TalkHistoryMapperIntegrationTest` are green against it, so the mapper↔DB boundary **is** covered. RISK-T13 is closed with its reasoning error recorded; RISK-R01 and RISK-S13 carry the same inference and are being re-examined (retrospective action A9).

**Verified executably.** `lpad` truncation (D-T9), the ordering and paging property on tied timestamps (D-T10, TC-T001-06), the count/page predicate agreement (D-T11), the institution join and its NULL passthrough (D-T26), scope exclusion (SCOPE-T01, TC-REG-04) and the reconciliation query (TC-REG-03) all run against real PostgreSQL and the real mapper XML.

**Refined again 2026-08-19 (Sprint T2).** The claim above split into two on contact with the code, and only half of it survived.
>
> Defects around a site-defined function are of two kinds. Those about **what the function computes** — what `masking()` actually returns — cannot be verified without its real definition. Those about the **shape of the SQL around it** — column names, aliasing, projection, mapping — are verified *exactly* by a **stub of the same name**, because the defect lives in the SQL rather than in the function body.
>
> **D-T18 is the second kind and is now verified by execution.** Unaliased `decrypt(CALLBACK), decrypt(PHONE)` makes PostgreSQL name both output columns `decrypt`, so MyBatis maps neither — a property of the names, independent of what the function computes. `TalkMessageMapperIntegrationTest#numbersActuallyMap` executes it against a one-line stub.
>
> **D-T6 is partly recovered.** `#numbersAreMasked` executes the query and asserts that masking **was applied** — the value differs from the input and carries the mask. What it does not assert is that the real `masking()` **output format** is correct, which needs the production function.
>
> **What G3 must record.** D-T9 and D-T18: **fully verified by execution.** D-T6: **verified at the boundary and by placement** — masking is provably applied, its output format is not verified here. That is a smaller residual than this section originally claimed, and the difference was found by asking whether "unverifiable" meant one thing or two.

**Fixture construction is an explicit task (T1-13), not a byproduct of writing tests.** Three properties will not appear naturally in staging: tied-timestamp blocks, serials at 10/14/20 characters, and at least one transaction per registry outcome including `none` and `ADV_KKO_AT_SEND2`. Every test in §3.1, §3.3 and §3.4 depends on fixtures that must be built deliberately.

## 10. Spec parity

Not byte-parity — this is a rebuild with 34 approved deviations, not a port. Parity is **intent parity**, and the 34 regression tests in §3 are its definition: each asserts the defect is gone, and together they are the difference between the legacy's behaviour and the specified behaviour.

Two deviations are visible to users and are called out so they are not filed as bugs:

- **SCOPE-T01** — non-talk API rows no longer appear (TC-REG-04 asserts this deliberately).
- **CONFLICT-T01** — the screen is operator-only (TC-T001-02 asserts the tenant 403).

## 11. E2E core scenarios (TOP 5)

| # | Scenario | Requirements |
|---|----------|--------------|
| E2E-1 | Operator queries a day, pages through tied-timestamp results, opens a transaction, opens a message | FR-TLK-001…015, FR-TLKD-001, FR-TLKM-001 |
| E2E-2 | Operator exports the current filtered view and the file matches the screen | FR-TLKX-001…010 |
| E2E-3 | Tenant principal is refused at every entry point and sees no menu item | FR-AZ-T02, NFR-SEC-AUTHZ-T01 |
| E2E-4 | Operator investigates a failed send: filters 오류, opens the transaction, reads the failure code and the failback message | FR-TLK-013, FR-TLKD-005/006, FR-TLKM-005 |
| E2E-5 | Operator searches a 20-character 거래고유번호 and drills to its messages | FR-TLK-009, FR-TLKD-009 |

## 12. Defect management

Findings are logged with a `D-T*` reference where they are regressions and a new id where they are not. CVSS v3.1 scoring for anything security-relevant; **≥ 7.0 blocks the gate**, per harness §5.
