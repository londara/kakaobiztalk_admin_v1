# Sprint T1 Log — 톡전송 내역 foundation, list and authorization

> **Sprint**: T1 · **Date**: 2026-08-19
> **Plan**: [sprint-T1-tasks.md](../design/sprint-T1-tasks.md) · [DEV-PLAN-TALK.md](../design/DEV-PLAN-TALK.md)
> **Spec**: [REQUIREMENTS-SPEC-TALK.md](../requirements/REQUIREMENTS-SPEC-TALK.md)
> **Status**: list path complete. §1–§6 report the sprint as first assessed (7-dimension **81.6**); the **Addendum** at the end supersedes §4 and §5 — RISK-T13's premise proved false, 54 further tests landed, and the revised score is **89.7**. Read the addendum before §4.

---

## 1. Completed tasks

| Task | Outcome |
|------|---------|
| T1-02 `BizTalkApiRegistry` | Config-held allow-list, five source-derived defaults, duplicate codes refused. **Absorbed T1-03** — see §3.1. 11 tests |
| T1-04 `PrincipalScope` | Moved from `ReportScope` to `common.tenant`; `ReportScope` is now a thin delegate. **The 이용기관 보고서 authorization suite passes unedited** — T1-04's exit condition |
| T1-05 `TransactionSerial` | One canonical form, two renderers, no padding in SQL. Over-width values are logged at WARN and **not truncated**. 15 tests |
| T1-06 `TalkHistoryMapper` | Nine-column closed `resultMap`, 기관명 by `LEFT JOIN`, no `SELECT *`, no write statements |
| T1-07 Ordering and paging | Total order `(RGDT DESC, IS_TUNO DESC)`; separate count query sharing one `<sql>` predicate fragment. **Offset, not keyset** — see §3.2 |
| T1-09 Read audit | `AuditService` event per query with actor, scope, criteria and row count; failure path audited separately |
| T1-10 `TalkHistoryService` + API | Two endpoints, validated request record, `detailAvailable` attached from the registry |
| T1-11 `TalkPeriodPolicy` | 31-day cap, real time bounds, `999999` sentinel refused, server-side inversion check. 19 tests |
| T1-12 React list screen | `TalkHistoryPage`, route, nav entry. 10 tests |
| T1-14 Reconciliation | `TalkApiReconciliation` — bidirectional, over a bounded window |

**Test results.** Backend 447 run, 2 failures — **both pre-existing** (`CsrfIntegrationTest.echoingCookieValueInHeaderPasses`, `ExceptionHandlerOrderTest.authAdvicePrecedesGlobalAdvice`). Verified by stashing this sprint's changes and re-running: both still fail. Neither touches anything this sprint changed. Frontend 147 run, 0 failures.

**New tests this sprint:** 27 backend (`TransactionSerialTest` 15, `TalkPeriodPolicyTest` 19 across nested classes, `BizTalkApiRegistryTest` 11, `TalkHistoryMapperXmlTest` 13) + 10 frontend.

## 2. Defects closed

| Defect | Closed by | Guard |
|--------|-----------|-------|
| D-T2 | Institution predicate + operator-only authorization | `TalkHistoryMapperXmlTest#institutionPredicateExists`, `#emptyScopeMeansNoRows` |
| D-T9 | `TransactionSerial` — no `LPAD` in a predicate, over-width warns rather than truncates | `TransactionSerialTest#overWidthIsNotTruncated`, `#noLpadInPredicate` |
| D-T10 | Total order with `IS_TUNO` tiebreaker | `TalkHistoryMapperXmlTest#orderByHasATiebreaker` |
| D-T11 | `countAll` sharing the page's predicate fragment | `#countStatementExists`, `#pageAndCountShareOnePredicate` |
| D-T13 | One registry answers link and lookup; `detailAvailable` server-computed | `BizTalkApiRegistryTest$LinkMatchesLookup`, `TalkHistoryPage.test.tsx` |
| D-T15 | Scope predicate on `API_SVC_CD`, matching the displayed column | `#scopeUsesApiServiceCode` |
| D-T24 | 31-day cap, real times, sentinel refused, all server-side | `TalkPeriodPolicyTest$DateRange`, `$TimeBounds` |
| D-T25 | One normalisation rule everywhere | `TransactionSerialTest#paddingDoesNotChangeIdentity` |
| D-T26 | `LEFT JOIN` + explicit unresolved marker, no `COALESCE` hiding it | `#usesJoinNotCorrelatedSubquery`, `#doesNotHideUnresolvedNames` |
| D-T27 | Selector options carry two fields | `BizTalkApiRegistryTest$SelectorOptions` |
| D-T28 | 조회 disabled until the options resolve | `TalkHistoryPage.test.tsx` |
| D-T29 | One source for filter options and column labels | `TalkStatus`, `TalkHistoryPage.test.tsx` |
| D-T31 | No `CASE WHEN :x = '' THEN 1=1`; dead code not ported | `#noNeutralisedPredicateForm` |

**12 of the 13 planned defects closed.** D-T32 (the `EXCEL_RSLT`/`EXCEL_RLST` id typo) belongs to the export path and moves to T2 with it — it was listed under T1 in error, since the code that contains it is not written until T2.

## 3. Deviations from the plan

Three, each recorded as an ADR or an ADR amendment rather than left as a difference between plan and code.

### 3.1 Two registries became one — T1-03 absorbed into T1-02

The plan specified `BizTalkApiRegistry` and `TalkDetailRegistry` with a **startup containment check** between them. One configuration entry carrying `code` + `channel` + `label` together makes the guarded state — *a code with a channel but no scope entry* — **unrepresentable**, so the check has nothing to check.

`channel` stays optional, so the distinction the ADRs relied on survives: a code may be in scope with no message detail.

**Test TC-REG-02 is retired as unreachable, not recorded as passing.** A test for an unrepresentable state is not a passing test, and counting it would misstate coverage. Amendments appended to [ADR-TLK-024 §5](../design/adr/ADR-TLK-024-biztalk-api-classification.md) and [ADR-TLK-026 §5](../design/adr/ADR-TLK-026-detail-serviceability.md).

### 3.2 Offset pagination, not keyset — [ADR-TLK-028](../design/adr/ADR-TLK-028-pagination-strategy.md)

T1-07 said keyset, inherited from the 이용기관 보고서 slice. The reasoning does not transfer: keyset was forced there by a **cross-datasource merge**, and this slice reads one database. Meanwhile the production screenshot shows a numbered pager (`1 2 3`), which keyset cannot serve — the report slice accepted that loss as RISK-R08, and accepting it here would remove a control the legacy screen has today.

Offset is only safe under a total order, which FR-TLK-006 now supplies. That promotes FR-TLK-006 from a display property to a **correctness precondition** — the same promotion FR-RPT-006 underwent for the same structural reason.

### 3.3 `PeriodPolicy` gained a cap parameter rather than being duplicated

`TalkPeriodPolicy` needs a 31-day cap where the report needs 366. Rather than a second class, `PeriodPolicy.validate` gained a three-argument overload and the existing two-argument form delegates with 366. Date parsing, inversion and calendar validity remain one implementation — which is what [ADR-TLK-027](../design/adr/ADR-TLK-027-sibling-reuse-boundary.md) asked for. The time-of-day bounds are genuinely new and live in `TalkPeriodPolicy`.

## 4. Carried tasks

Four carried and three partial. Each says what is missing and why, because a task list that reports 16 of 16 while three of them are half-built is the reporting equivalent of the silent-success defect this slice is full of.

| Task | State | Why, and what unblocks it |
|------|-------|---------------------------|
| **T1-01a** `lpad` truncation, executable | **Partial** | The *guard* exists — `TransactionSerialTest#overWidthIsNotTruncated` proves our code does not truncate. The *demonstration* that PostgreSQL's `lpad` truncates is **not** written: it needs `io.zonky.test:embedded-postgres` added as a test dependency, which is a build change this sprint did not make. Carried to T2 alongside the first mapper integration test |
| **T1-01b** stored widths, join cardinality | **Carried** | Needs one query run by someone with production-like read access (RISK-T13). `TransactionSerial` ships with configurable widths and a WARN, so the guess announces itself. **FR-TLKD-009 stays `BLOCKED-AMB-T04`** |
| **T1-02** startup existence check | **Partial** | The allow-list and duplicate refusal are done. The check that each configured code **exists in `FT_OPENAPI_INFO`** is not — and on reflection it is the weaker of the two checks, because it validates against the API master rather than against transactions. `TalkApiReconciliation` answers the same question in both directions against real data. Recorded as a **deliberate substitution**, and TC-REG-01 is rewritten accordingly in T2 rather than reported as passing |
| **T1-08** authorization | **Partial** | `@PreAuthorize("hasRole('OPERATOR')")` on both endpoints under the existing `/api/admin/**` rule. The **negative-path tests are not written** (T1-16), so FR-AZ-T02 and NFR-SEC-AUTHZ-T01 are `PARTIAL` in the trace matrix, not `IMPLEMENTED`. An authorization control with no test asserting the refusal is a claim, not a control |
| **T1-13** test fixtures | **Carried** | Tied-timestamp block, three serial lengths, one transaction per registry outcome. No DB tier exists here to hold them (RISK-T13), so they are built with the first integration tier in T2. **This is why TC-T001-06's paging property is not yet proven on data** — only the ordering clause is asserted, in the XML |
| **T1-14** scheduling and test | **Partial** | `TalkApiReconciliation` is implemented and logs both directions at the right levels. It is **not scheduled** and **has no test** (TC-REG-03) — both need a DB tier |
| **T1-15** endpoint inventory + `30_l002` retirement | **Carried** | TC-T001-14 asserts over the application's route table and needs a Spring context test. The legacy retirement proposal is written up in DEV-PLAN §4.4 but is **an operations decision, not a task this sprint could complete** |
| **T1-16** negative-path security tests | **Carried** | Depends on T1-13's fixtures and a context test harness. This is the largest carried item and the one that most affects §5's 보안 score |

## 5. 7-dimension self-assessment

| Dimension | Weight | Score | Basis |
|-----------|--------|-------|-------|
| 완성도 Completeness | 20% | **78** | 10 of 16 tasks complete, 3 partial, 4 carried (one task, T1-01, split into two). 12 of 13 planned defects closed. The list path works end to end; the verification around it does not yet |
| 추적성 Traceability | 15% | **95** | Every method carries `// req:` or `// source:`; 25 new trace rows; three deviations recorded as ADR or amendment before the code was reported |
| 보안 Security | 20% | **72** | Controls are in place — operator-only, server-derived scope, closed nine-column projection, audit on every query, no write path. But **the negative-path tests that prove the refusals are not written** (T1-16), and the endpoint inventory (T1-15) is carried. Controls without their refusal tests score as claims |
| 성능 Performance | 10% | **70** | Nothing measured. `LPAD` removed from the predicate so an index on `SERIALNUM` is usable, and the correlated subquery replaced by a join — both should help, and neither is verified. NFR-PERF-T01 is a T2 load test |
| 가독성 Readability | 15% | **93** | Bilingual Javadoc throughout; every non-obvious choice states what the legacy did and why it was wrong. The mapper XML carries a `FIX D-Tn:` comment per altered line and a "deliberately not done" section |
| 표준 준수 Standards | 10% | **92** | No directory-isolation violation; four ADRs plus two amendments plus one new ADR for the pagination deviation; no ADR missing for a design change. `SELECT *` prohibited and asserted |
| 테스트 커버리지 Test coverage | 10% | **74** | 37 new tests, all passing, and the domain classes are well covered. But three test classes the plan named are absent (TC-REG-03, TC-T001-14, TC-T001-06's data half), and the mapper↔DB boundary is untested by construction (RISK-T13) |

**Weighted total: 81.6 / 100** — below the 90 threshold.

### Why this is reported rather than iterated to 90

The harness allows up to five improvement loops. Running them here would not move the score honestly, because **the three lowest dimensions are all blocked on the same missing thing: a database tier.** 보안 at 72, 테스트 커버리지 at 74 and 성능 at 70 are each held down by tests that cannot run in an environment without Docker (RISK-T13) — not by code that could be improved in another pass.

Writing more unit tests would raise the *number* while leaving the *gap* exactly where it is, which is the failure mode this slice exists to eliminate. The 이용기관 보고서 sprint scored 완성도 85 and its retrospective found two defects at precisely this boundary; inflating a score across the same gap would repeat that with a better-looking report.

**Escalated to PM as a scope question, not a quality failure:** either a PostgreSQL tier (embedded or otherwise) is made available for T2, or T2 ships with the same three dimensions capped and G3 records the slice as verified-by-placement-and-boundary for D-T6, D-T9 and D-T18. The second option is defensible; it is not a decision this team should make silently.

## 6. What this sprint learned

**The plan asked for two registries and the code wanted one.** The containment check between them was a check against a state that a better data shape makes impossible. That is worth generalising: *a startup validation between two collections is often a sign the collections should be one.* The check was in the plan because the ADR reasoned about two lists; nothing in the requirement asked for two.

**One carried task changed its own justification.** T1-02's startup existence check against `FT_OPENAPI_INFO` went from "not done" to "deliberately substituted" once the reconciliation query existed — because validating a configured code against the API master is weaker than validating it against transactions that actually happened. The right response was to say so, not to build both.

**The split of T1-01 paid for itself immediately.** T1-01a's guard (we do not truncate) is in the suite and passing; T1-01b (what the widths actually are) is a DBA request. Had they stayed one task it would have been carried whole, as R1-01 was, and `TransactionSerial` would have shipped with a silent guess instead of a WARN.

---

# Addendum — 2026-08-19, after §5's escalation

The escalation in §5 asked whether a PostgreSQL tier could be made available. **Checking that question answered it: the tier was always available, and §5's premise was wrong.**

## A1. RISK-T13's premise was false

The risk read: *Docker is prohibited, therefore no PostgreSQL is reachable, therefore the mapper↔DB boundary cannot be tested.* The first clause is true. **The second does not follow.** `io.zonky.test:embedded-postgres` (Apache-2.0, so CODE-004 raises nothing) launches a real PostgreSQL binary **as a process** — no Docker involved — and it starts on this machine. One dependency line, about twenty minutes.

RISK-T13 is closed with the reasoning error recorded rather than tidied away, because the error is the finding: **RISK-S13 → RISK-R01 → RISK-T13 carried the same inference across three slices and nobody tested the second clause.** The 이용기관 보고서 retrospective found two defects at precisely the boundary those risks declared unreachable.

## A2. Two more claims in §4 were also wrong

Re-examining the carried list against the same standard:

- **T1-16 and T1-15 never needed a database.** They need a Spring web context, and `@WebMvcTest` provides one without a `DataSource` — the pattern was already in this repository, in `CsrfIntegrationTest`, with a Javadoc paragraph explaining exactly that. I recorded them as blocked while a working example sat in the same source tree.
- **T1-14's test never needed a database.** With the mapper mocked, the whole reconciliation is verifiable.

## A3. What that produced

| Item | Tests |
|------|-------|
| T1-16 negative-path authorization | `TalkHistoryAuthorizationTest` — 7 tests: anonymous refused on both endpoints, tenant 403 on both, operator 200, missing parameter 400 |
| T1-15 endpoint surface + contract | `TalkHistoryContractTest` — 7 tests: exact field set and order, forbidden-name scan over response and mapper rows, two read endpoints only, no write endpoint, every endpoint carries `@PreAuthorize(OPERATOR)` |
| T1-14 reconciliation | `TalkApiReconciliationTest` — 7 tests: unclassified codes with counts, configured-but-unseen, clean case, bounded window |
| T1-10 service behaviour | `TalkHistoryServiceTest` — 15 tests: `detailAvailable` attachment, criteria assembly, scope resolution, audit on success/override/failure |
| **T1-01a** `lpad` truncation, executable | `LpadTruncationTest` — 4 tests against real PostgreSQL: `lpad('26081900142813',10,'0')` → `'2608190014'`, and the full legacy path shown to point at a different transaction |
| **T1-13 + TC-T001-06** paging property on data | `TalkHistoryMapperIntegrationTest` — 10 tests against real PostgreSQL and the **real mapper XML**: 100 rows on one timestamp paged at size 7, union equals the full set exactly once |

**54 new tests. Backend total 501, same 2 pre-existing failures** (`CsrfIntegrationTest`, `ExceptionHandlerOrderTest` — both verified independent of this sprint).

## A4. One defect in our own code, found by the new suite — CR-T01

`TalkHistoryAuthorizationTest#missingFromIsRejected` failed on its first run: omitting a required `@RequestParam` returned **500**, not 400. `MissingServletRequestParameterException` fell through to `GlobalExceptionHandler#handleUnexpected`, so a client error was reported as a server error and logged as `UNHANDLED`.

**This was never specific to this slice.** Every endpoint with a required parameter was affected, including `ReportController#query`'s `from` and `to` — the 이용기관 보고서 slice has shipped with it. Fixed by a `handleBindingFailure` handler returning 400 and naming the offending parameter. Recorded as **CR-T01** in the trace matrix.

Two things about this are worth stating. It is a defect in **our** code, not the legacy's — the first this slice has produced. And it was found by the test suite that §4 recorded as blocked.

## A5. The schema in the integration test is itself a control

`TalkHistoryMapperIntegrationTest` creates **nine columns**, not `FT_APITR_HSTR`'s twenty-five. The account, card, amount and telegram columns are absent from the test schema, so a projection that tries to select one **fails to execute**. CONST-SEC-T01 is now enforced by execution as well as by the XML scan and the contract test.

The integration test also refuted one of my own assumptions on its first run — I expected the first page's institution name to resolve, and the row that sorts first is the one whose institution is absent from the master. That is the value of the tier in one sentence.

## A6. Revised 7-dimension assessment

| Dimension | Weight | Was | Now | What changed |
|-----------|--------|-----|-----|--------------|
| 완성도 Completeness | 20% | 78 | **90** | T1-01a, T1-13, T1-14, T1-15, T1-16 delivered. Carried: T1-01b (DBA query) and the human half of T1-15 |
| 추적성 Traceability | 15% | 95 | **96** | CR-T01 recorded; 5 statuses promoted from `PARTIAL`; **0 `PARTIAL` rows remain** |
| 보안 Security | 20% | 72 | **89** | Refusals now asserted, not claimed: 7 authorization tests, endpoint surface asserted structurally, a real defect found and fixed. Still no masking suite — screen 30 carries no PII, so it arrives with T2's detail |
| 성능 Performance | 10% | 70 | **72** | Still nothing measured. The tier now exists to measure on, but NFR-PERF-T01 needs volume fixtures and is genuinely T2 |
| 가독성 Readability | 15% | 93 | 93 | Unchanged |
| 표준 준수 Standards | 10% | 92 | **94** | One dependency added with its licence and rationale in the POM; RISK-T13 closed with reasoning recorded |
| 테스트 커버리지 Test coverage | 10% | 74 | **90** | 54 new tests. TC-T001-06 proven on data; TC-REG-03 done; TC-T001-14's structural half done; D-T9's mechanism executably proven; mapper↔DB boundary now covered |

**Weighted total: 81.6 → 89.7 / 100.**

Still 0.3 short, and it is not worth another loop: the gap is 성능 at 72, which needs load testing against volume fixtures. That is Sprint T2 work with a number attached (NFR-PERF-T01: list P95 < 3 s at the 31-day cap), not something another pass of unit tests should be allowed to paper over. **PM decision requested: accept 89.7 with 성능 explicitly deferred to T2's load test, or hold the sprint open.** Recommending the former — the dimension is honestly scored and the work is scheduled.

## A7. What this addendum says about the sprint

§5 reported a blocked score and escalated the blocker. **The blocker was not real, and checking took less time than writing the escalation.** Three risks had carried the same untested inference across three slices, and one of them was mine.

The standing check this adds is in the retrospective: *when a risk says a capability is unavailable, ask what was actually tried.* An inference recorded as a constraint stops being questioned — which is the same shape as the silent-success defects this slice was built to remove, applied to a risk register instead of to code.
