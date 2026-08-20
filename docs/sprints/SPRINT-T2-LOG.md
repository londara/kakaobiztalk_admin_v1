# Sprint T2 Log — 톡전송 내역 drill-down, masking and export

> **Sprint**: T2 · **Date**: 2026-08-19
> **Plan**: [DEV-PLAN-TALK.md](../design/DEV-PLAN-TALK.md) §4 · [sprint-T1-tasks.md](../design/sprint-T1-tasks.md) (T2 handover)
> **Spec**: [REQUIREMENTS-SPEC-TALK.md](../requirements/REQUIREMENTS-SPEC-TALK.md)
> **Predecessor**: [SPRINT-T1-LOG.md](SPRINT-T1-LOG.md) — read its Addendum first; it closed RISK-T13 and changed what this sprint could verify

---

## 1. What shipped

| Task | Outcome |
|------|---------|
| T2-01 `TalkMessageMapper` + XML | Four talk tables (live + archive per channel), `masking(decrypt(…))` at the outermost projection, shared predicates across channels, no write statement |
| T2-02 Transaction detail (screen 32) | `TalkDetailService.messages` — institution **re-derived from the ledger**, channel from the registry, paged with a server total |
| T2-03 Message detail (screen 31) | `TalkDetailService.detail` — institution-qualified key, no mutable column in the key, all 20 fields queried |
| T2-04 Drill-down UI | `TalkTransactionDetailPanel`, `TalkMessageDetailPanel`, wired into the list page |
| T2-05 Export on the list's own path | `TalkExportService` consuming the **same `TalkHistoryCriteria`** the list builds |
| T2-06 Header safety | `ContentDisposition` (RFC 6266 + 5987), filename composed only from a server constant and validated dates |
| T2-07 Row ceiling + audit | 100,000-row ceiling refused **before** any byte is written; audit carries the row count actually written |
| T2-08 Workbook↔screen parity | `TalkExportParityTest$SetEquality` — 9 filter combinations |
| — `StreamingWorkbookWriter` | **New** — see §3.1. SXSSF, 100-row window, temp files disposed |

**Tests.** Backend 548 run, 2 failures — **both pre-existing** (`CsrfIntegrationTest`, `ExceptionHandlerOrderTest`; verified independent of this slice in T1). Frontend 151 run, 0 failures. **New this sprint: 47 backend + 4 frontend.**

## 2. Defects closed

| Defect | Sev | Closed by | Guard |
|--------|-----|-----------|-------|
| **D-T1** | Critical | Export consumes the list's criteria and mapper method | `TalkExportParityTest$SetEquality` — set equality over 9 filter combinations, plus "every filter changes the file" |
| **D-T5** | Critical | `TalkMessageDetailKey` is institution-qualified | `TalkMessageMapperIntegrationTest#crossInstitutionKeyReturnsNothing` — **executed** against real PostgreSQL |
| **D-T4** | Critical | Filename from a server constant + validated dates, RFC-encoded | Code review; no user input reaches a header |
| **D-T6** | High | `masking(decrypt(…))` at the outermost projection | `$Masking#numbersAreMasked` — **executed** with stub crypto |
| **D-T7** | High | Channel from the registry; no channel literal in SQL | `#channelReadsItsOwnTable`, `TalkDetailServiceTest$ChannelRouting` |
| **D-T8** | High | Predicates shared across channels | `#filtersApplyToFriendtalk` — asserts the filter actually narrows |
| **D-T12** | High | SXSSF streaming + row ceiling, refused before writing | `$CeilingAndAudit#refusalWritesNothing` |
| **D-T14** | High | Every parameter through the declared contract | `TalkExportParityTest` |
| **D-T16** | Medium | Export declares its own controller and contract | Code review |
| **D-T17** | Medium | 4-digit year | `#yearIsFourDigits` — **executed** |
| **D-T18** | Medium | `decrypt()` calls aliased | `#numbersActuallyMap` — **executed**; see §3.2 |
| **D-T19** | Medium | Status removed from the key | `#statusChangeDoesNotHideTheMessage` — **executed**, status mutated mid-test |
| **D-T20** | Medium | Code and dictionary text returned separately | `$ResultCodes#unknownCodeSurvives` |
| **D-T21** | Medium | `maxLength={11}` | `TalkTransactionDetailPanel` |
| **D-T22** | Medium | 미수신 is an explicit third classification | `#failureFilterDoesNotSwallowPendingRows` — asserts the three **partition** the set |
| **D-T23** | Medium | `fetch` delivery; failure surfaces as a message | `TalkHistoryPage.test.tsx` — 내보내기 실패가 화면에 보인다 |
| **D-T30** | Low | Client uses the server's total | `TalkTransactionDetailPanel` paging indicator |
| **D-T32** | Low | No DOM-id typo path exists — filters travel as a typed object | `$SetEquality#everyFilterChangesTheFile` |
| **D-T33** | Low | `readOnly` on the message textarea | `TalkMessageDetailPanel` |
| **D-T34** | Low | One content type; header cells written once; screen titled for itself | `$WorkbookContent#headerMatchesTheScreenAndIsWrittenOnce` |

**20 of 21 planned defects closed.** D-T13's remaining half (the E2E form of link↔serviceability) is covered at the domain level in T1 and at the panel level here; the full E2E assertion is in §4.

**Slice total: 32 of 34 defects closed** across T1 and T2. The two outside: D-T3 (removed rather than fixed — the endpoint is not carried forward) and D-T24 (closed in T1).

## 3. Deviations from the plan

### 3.1 The export writer did not exist — [ADR-TLK-027 §5](../design/adr/ADR-TLK-027-sibling-reuse-boundary.md)

[ADR-TLK-027](../design/adr/ADR-TLK-027-sibling-reuse-boundary.md) §2 said this slice would **reuse** the 이용기관 보고서 slice's streamed export writer, and that "the 보고서 slice already ships xlsx". **Both were false.** ADR-RPT-023 chose SXSSF programme-wide, but the writer was scheduled for that slice's **Sprint R2, which has not run**. There is no `infra.excel` package and Apache POI was not in the POM.

This is the same error the ADR was written to catch, committed inside it: §1.2 discovered the message tables were not shared **by checking**; §1.1 asserted the export writer was shared **without checking**.

`infra.excel.StreamingWorkbookWriter` is therefore new code in this slice, and 보고서 R2 consumes it — the dependency direction reverses. POI 5.2.5 added with its Apache-2.0 licence recorded inline (CODE-004).

The CSV question was re-taken rather than inherited, because §2 rejected it partly on the false premise. ADR-RPT-023 rejected CSV because *the report has two sheets of different shapes*, which does not transfer to this single flat grid — so the honest position is that CSV would be cheaper and adequate. xlsx is still chosen on two grounds that survive: **BR-011 is a `Must` and names Excel**, and the streaming/ceiling properties are required regardless of format.

### 3.2 D-T18 moved from verified-by-placement to verified-by-execution

TEST-PLAN-TALK §9 recorded D-T6 and D-T18 as unverifiable here, because `decrypt()` and `masking()` are site-defined. That was **right about the crypto and wrong about D-T18**.

Defects in this family split in two. Those about what a function **computes** are unverifiable with a stub. Those about the **shape of the SQL around it** — column names, aliasing, projection, mapping — are verified *exactly* by a stub of the same name, because the defect is in the SQL rather than in the function body.

D-T18 is the second kind: unaliased `decrypt(CALLBACK), decrypt(PHONE)` makes PostgreSQL name both output columns `decrypt`, so MyBatis maps neither. A one-line stub reproduces that collision precisely. `#numbersActuallyMap` now **executes** it.

**What is still not verified by execution:** what the real `masking()` returns. `$Masking` asserts masking **was applied** (the value differs from the input and contains the mask), not that its format is correct. G3 must still record that distinction.

This finding is carried to the 발신번호 slice as an addendum to RISK-S13, whose D-S1 may contain the same split.

### 3.3 `TalkTransactionKey` gained `serialForLedger()`

MyBatis resolves record accessors but requires JavaBean getters on an ordinary class, and `TransactionSerial` is a final class rather than a record so it can define its own `equals`/`hashCode`. Rather than rename a domain accessor to satisfy a framework convention, the key record exposes one method and the mapper sees one record. Caught by the integration test on its first run.

## 4. Carried

| Item | State | Why |
|------|-------|-----|
| **NFR-PERF-T01/T02 load test** | Carried | The DB tier exists now, so this is finally possible — but it needs volume fixtures and a measurement run. The single remaining materially-short dimension |
| **NFR-SCALE-T01 heap profile** | Carried | The streaming writer is in place and the ceiling is enforced; the *flat-heap-at-100k-rows* measurement is part of the same load run |
| **FR-TLKX-003/004 header tests** | Partial | The properties hold structurally (a server constant plus validated dates; one content type set once) but no test asserts the emitted headers. Needs a `@WebMvcTest` on `TalkExportController` |
| **TC-T001-13 E2E form** | Carried | Domain-level set equality is green in T1 and the panel-level link is tested here; the full request-level E2E remains |
| **T1-01b DBA query** | Carried | Unchanged. `TransactionSerial` still warns rather than truncates; **FR-TLKD-009 is now `IMPLEMENTED`** because the lossless property is proven at 10/14/20 characters, but the *stored widths* remain an assumption |
| **`biztalk_admin_30_l002` retirement** | Carried | An operations decision, not a development task (RISK-T03) |

## 5. 7-dimension self-assessment

| Dimension | Weight | T1 (revised) | T2 | Basis |
|-----------|--------|--------------|-----|-------|
| 완성도 Completeness | 20% | 90 | **92** | 9 of 9 T2 tasks shipped; 20 of 21 planned defects closed; 32 of 34 across the slice. Carried: the load run and two header tests |
| 추적성 Traceability | 15% | 96 | **96** | 33 further trace rows; every method carries `// req:`/`// source:`; three deviations recorded as ADR amendments before the code was reported |
| 보안 Security | 20% | 89 | **91** | D-T1, D-T5, D-T4 closed — the three Criticals this sprint owned. Masking executed with stubs. Still no emitted-header test, and the real `masking()` output remains unverifiable |
| 성능 Performance | 10% | 72 | **74** | Still nothing measured. `LPAD` out of the predicate and the correlated subquery replaced are both verified structurally; the numbers are the load run |
| 가독성 Readability | 15% | 93 | **93** | Bilingual Javadoc throughout; every mapper change carries a `FIX D-Tn:` comment and a "deliberately not done" section |
| 표준 준수 Standards | 10% | 94 | **94** | Two dependencies added with licence and rationale inline; three deviations recorded; no ADR missing for a design change |
| 테스트 커버리지 Test coverage | 10% | 90 | **91** | 51 further tests. Two defects moved from placement-verified to execution-verified. Missing: the load run and the header assertions |

**Weighted total: 89.7 → 90.6 / 100 — above the 90 threshold.**

The threshold is met, and it is worth being precise about why it moved so little: **the score was never the constraint.** T1's addendum already showed that. What lifted it is closing three Critical defects with executable evidence, and what still holds it down is one load run that needs a measurement rather than a code change.

**성능 at 74 is the honest remainder.** It is a measurement, it is now possible, and it is Sprint T3's first task rather than something another pass of unit tests should paper over.

## 6. What this sprint learned

**The same mistake, one document later.** ADR-TLK-027 exists because I checked whether the message tables were shared and found they were not. In the same ADR I asserted the export writer *was* shared without checking. **Verification is not a habit you acquire once per document** — the ADR that recorded the lesson violated it three paragraphs earlier.

**"Unverifiable" deserved the same scrutiny "unavailable" got.** T1's addendum found that a risk saying a capability was unavailable had never been tested. This sprint found the adjacent error: a test plan saying two defects were unverifiable had not distinguished *what a function computes* from *the shape of the SQL around it*. One of the two was verifiable all along, and the distinction took ten minutes to find once the question was asked.

Both errors have the same shape as the defect class this slice was built to remove — **a statement recorded as fact and then cited rather than checked** — and both were in our own documents rather than the legacy's code.

---

# Addendum — actions B1 and B2 closed, 2026-08-19

## B2. Export header assertions — done

`TalkExportControllerTest`, 12 tests via `@WebMvcTest`. FR-TLKX-003 and FR-TLKX-004 move from `PARTIAL` to `IMPLEMENTED`.

What it asserts on the wire, rather than structurally: `Content-Disposition` carries `filename*=UTF-8''` (RFC 5987 — the only form a Korean filename survives); the filename contains only the server constant and the **normalised** dates; **no header contains CR or LF**, checked across every emitted header; exactly **one** `Content-Type`, equal to the xlsx media type, with all four of the legacy's wrong types asserted absent; `X-Talk-Export-Rows` matches what the service wrote. Plus authorization — anonymous refused, tenant 403, missing `from` 400, and in every refusal case `export` is never called, so no file is built.

A CR/LF-bearing `from` is rejected before a header exists at all: two layers stop it, `TalkPeriodPolicy` and a filename composed only from validated values.

## B1. Load and scale measurement — done, and it refuted one of our own claims

`TalkHistoryLoadTest` (`@Tag("load")`, excluded from the default cycle). 200,000 rows over 31 days, 150,000 of them in BizTalk scope, with **deliberate tied-timestamp density** — six rows per second, comparable to the production screenshot.

### Reference figures (dev hardware — **not** a production SLA)

| Measurement | Result |
|---|---|
| List, first page (100 rows, 31-day range) | P95 **305 ms** |
| List, last page (offset 149,900) | P95 **822 ms** |
| Count query | P95 **237 ms** |
| Paging correctness at 150,000 rows, page size 997 | 150,000 emitted, **150,000 unique** |
| Export heap, 1k vs 100k rows | 39.8 MB vs 30.8 MB — **ratio 0.77** |

**These milliseconds are not asserted.** Making a development machine's absolute timings a pass criterion produces a test that fails on someone else's laptop. What is asserted are three properties that hold or fail regardless of hardware: a selective predicate uses an index; the last page stays within 50× the first; export heap does not scale with row count.

### Finding 1 — our documented sargability claim was false

ADR-TLK-025 and the mapper XML both said the legacy's `SERIALNUM = LPAD(:SERIALNUM,10,'0')` was "**both** the truncation bug **and** a sargability problem". The plans:

| Shape | Plan |
|---|---|
| Java-padded constant | `Index Only Scan using ix_apitr_tuno` |
| `LPAD` on the **parameter** — the legacy's actual shape | `Index Only Scan using ix_apitr_tuno` |
| `LPAD` on the **column** — what we described | `Parallel Seq Scan` |

Sargability breaks when a function is applied to the **column**. The legacy applied it to the **parameter**, which PostgreSQL folds to a constant at plan time. **The performance half of our justification described a defect the legacy did not have.**

The decision is unchanged — the truncation is real and `LpadTruncationTest` proves it by execution — but one of its two stated reasons was invented. Corrected in [ADR-TLK-025](../design/adr/ADR-TLK-025-transaction-message-identity.md), `TransactionSerial`'s Javadoc and `TalkHistoryMapper.xml`, in each case by **recording the error rather than deleting the sentence**.

### Finding 2 — my own test asserted the wrong property

The first version of `predicateUsesAnIndex` asserted that **every** query uses an index, and failed. With 200,000 rows spanning exactly 31 days, a query filling the 31-day cap selects the whole table, where a sequential scan beats an index scan — the planner choosing one is *correct*. "Always an index" is superstition, not a performance property.

Rewritten to assert the right thing: a **selective** predicate (one day, ten-minute window) uses `ix_apitr_trdd_rgdt`, which it does.

### Finding 3 — one thing to re-measure at production volume

The 31-day full-range query plans a sequential scan here, legitimately. **At production volume that needs re-measuring**: dev data is 200,000 rows over 31 days and production is far larger, so a full scan per page could threaten NFR-PERF-T01 in a way this hardware cannot show. Recorded as a finding rather than a pass.

### What B1 does and does not establish

**Establishes:** the paging design is correct at volume with realistic tie density (D-T10 confirmed at 150,000 rows, not just 100); deep-offset cost is bounded at ~2.7× first-page cost, so [ADR-TLK-028](../design/adr/ADR-TLK-028-pagination-strategy.md)'s premise for choosing offset paging holds; export heap is independent of row count, which is all of NFR-SCALE-T01.

**Does not establish:** that NFR-PERF-T01/T02 meet their production SLA. That needs production-like hardware and concurrency. The dev figures have headroom against the 3 s and 1 s targets, and headroom on a laptop is not a measurement of production.

**Heap caveat stated plainly:** 39.8 MB for 1,000 rows is JIT and POI warm-up, not retention — the absolute numbers are noise. The **ratio below 1.0 for a 100× row increase** is the finding, and it is unambiguous: nothing linear in row count is being retained.

## Revised 7-dimension

| Dimension | Weight | T2 | Now | What changed |
|-----------|--------|-----|-----|--------------|
| 완성도 | 20% | 92 | **95** | B1 and B2 closed; carried list reduced to items needing production access or an operations decision |
| 추적성 | 15% | 96 | **97** | FR-TLKX-003/004 promoted; a false claim corrected in three places with the error recorded |
| 보안 | 20% | 91 | **94** | Header safety asserted on the wire, not argued structurally: no CR/LF in any emitted header, one content type, refusals build no file |
| 성능 | 10% | 74 | **84** | Measured. Index use, deep-offset bound and heap independence all verified. Not 95 because production-hardware SLA verification remains, and Finding 3 is open |
| 가독성 | 15% | 93 | 93 | Unchanged |
| 표준 준수 | 10% | 94 | **95** | Load test tagged out of the default cycle; a refuted claim corrected rather than quietly dropped |
| 테스트 커버리지 | 10% | 91 | **94** | 17 further tests. Paging correctness now proven at volume as well as at 100 rows |

**Weighted total: 90.6 → 93.0 / 100.**

성능 at 84 is the honest ceiling for this environment. The remaining 16 is production hardware and Finding 3 — neither is a code change, and neither should be scored as if it were done.
