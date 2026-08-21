# Test Plan — 이용기관 보고서 (Institution Usage Report)

> **Version**: 1.0
> **Date**: 2026-08-18
> **Predecessor**: [DEV-PLAN-REPORT.md](DEV-PLAN-REPORT.md), [REQUIREMENTS-SPEC-REPORT.md](../requirements/REQUIREMENTS-SPEC-REPORT.md)
> **Siblings**: [TEST-PLAN.md](TEST-PLAN.md), [-LOGIN](TEST-PLAN-LOGIN.md), [-INSTITUTION](TEST-PLAN-INSTITUTION.md), [-SENDERNO](TEST-PLAN-SENDERNO.md)
> **Status**: **APPROVED (G2)** — 2026-08-21, PM · 사후 결재(구현·검증 선행) / retrospective

---

## 1. Test strategy

The slice is read-only, holds no PII and writes nothing to its data sources. That removes most of what the previous three test plans spent their effort on — encryption round-trips, transaction rollback, history correctness, legacy write coexistence — and concentrates the risk in four places:

1. **Access control**, because D-R1 ∘ D-R2 is an unauthenticated dump of every customer's volumes and is the highest-severity finding in the programme so far (T-R10, CVSS ≈ 9.1).
2. **Merge correctness**, because ADR-RPT-021's k-way merge is the only place in the programme where a *correct-looking* result can be silently wrong — a mis-ordered stream produces plausible numbers, not an error.
3. **Boundedness**, because three separate defects (D-R8, D-R9, D-R15) all end in the same place: unbounded work reachable from a single request.
4. **Arithmetic honesty**, because FR-RPT-010's identities are the cheapest detector available for data problems this slice cannot otherwise see.

Everything else is conventional.

## 2. Coverage targets and the verification-depth problem

| Target | Value |
|--------|-------|
| Line coverage | ≥ 80% |
| Branch coverage | ≥ 70% |
| Defect regression | 1+ test per fixed defect — 25 defects |
| E2E core scenarios | TOP 5 (§12) |
| Load | 2× the NFR-PERF SLA |
| 7-dimension self-assessment | ≥ 90 / 100 |

**The no-Docker constraint applies here too, and this slice makes it worse.** RISK-S13 established that Testcontainers is permanently unavailable, so tests that need a real PostgreSQL degrade. This slice needs **two** PostgreSQL instances to exercise its central mechanism, which no previous slice required.

The tiering is therefore explicit, and the plan states which tier each mechanism actually reaches rather than reporting a coverage percentage that hides it:

| Tier | Environment | What it can prove |
|------|-------------|-------------------|
| **Tier 1** | Two reachable PostgreSQL schemas carrying the real aggregate tables | Everything, including cross-source merge against real data and index behaviour |
| **Tier 2** | One instance, two schemas standing in for two databases | Merge correctness, ordering, keyset paging, SQL shape. **Cannot** prove per-source timeout/degradation behaviour realistically |
| **Tier 3** | In-process `io.zonky.test:embedded-postgres` (Apache-2.0), two schemas | Same as tier 2 minus production index characteristics |
| **Tier 4** | Mapper unit tests + SQL-shape assertions | Query construction only |

**Tier 2 is sufficient for this slice's headline mechanism**, and that is the material difference from the 발신번호 slice. There, the headline requirement depended on a DB *function* (`ENCRYPT`/`decrypt`) that could not be replayed, so D-S1's fix was unverifiable. Here the merge depends only on `ORDER BY`, `LIMIT` and row-value comparison — standard SQL that any PostgreSQL reproduces faithfully. **Two schemas on one instance test the merge honestly.**

What tier 2 cannot prove is FR-RPTS-005's degradation behaviour, because two schemas on one instance fail together. That is covered by fault injection at the `DataSource` level (§5) and is labelled as such.

## 3. Defect regression suite

One test minimum per fixed defect. Full case list in [UC-RPT-001](../requirements/use-cases/UC-RPT-001.md) §7 and [UC-RPT-002](../requirements/use-cases/UC-RPT-002.md) §7.

| Defect | Regression assertion | Tier |
|--------|---------------------|------|
| D-R1 | Anonymous call to both endpoints → 401 | 4 |
| D-R2 | Tenant user omitting `IS_CD` is scoped to their own institution, not 전체 | 2 |
| D-R3 | CRLF payload in `START_DT` → 400; no injected header in the response | 4 |
| D-R4 | Export produces the same sheets and figures in every environment profile | 2 |
| D-R5 | Response is a structured record list; 기관명 containing `"` and `\` round-trips intact | 4 |
| D-R6 | Identical response shape and field types across all environment profiles | 4 |
| D-R7 | Identical row order across repeated queries **and** across environments; blank `TRDD` does not throw | 2 |
| D-R8 | 366-day × all-institution query returns one page, not the full set | 2 |
| D-R9 | `00000000`/`99999999`, inverted range, 367 days, `20261332` → 400 each | 4 |
| D-R10 | Export contract declares every input; no raw request read (code review + contract test) | 4 |
| D-R11 | Row with NULL `FTIMGWI_CNT` yields 0 in the cell and a correct row total | 2 |
| D-R12 | `IS_CD` absent from `FT_FTIS_INFO` renders code + unresolved marker, never blank | 2 |
| D-R13 | 기관명 resolved by join; query plan shows no per-row subquery | 1 |
| D-R14 | 전체 = 성공 + 실패 + 처리중 holds on every row; Excel summary sheet carries all four | 2 |
| D-R15 | Heap profile flat across 1k and 100k exported rows | 2 |
| D-R16 | Forced export failure surfaces a visible message to the user | 2 |
| D-R17 | Every query and export writes an audit event with actor, scope, range, row count | 2 |
| D-R18 | Sheet title merge width equals header column count on every sheet | 4 |
| D-R19 | Exactly one content type on the response, and it is the xlsx type | 4 |
| D-R20 | Filename and workbook title name the same report and the same range | 4 |
| D-R21 | Header cells created once per column | 4 |
| D-R22 | Dead files and dead handlers absent from the build (lint + review) | 4 |
| D-R23 | No query issued before the selector loads and scope is known | 2 |
| D-R24 | `RGDT` and `FT_CNT` absent from the response | 4 |
| D-R25 | Default view shows the as-of date and marks today 미집계, not "no data" | 2 |

### 3.1 The merge deserves a dedicated property test

D-R7's regression above asserts that ordering is stable. That is necessary and not sufficient, because **ADR-RPT-021 makes ordering a correctness precondition rather than a display property** — if the two streams disagree on order, the merge silently produces wrong sums instead of failing.

A property-based suite covers it directly, over generated pairs of source data sets:

| Property | Assertion |
|----------|-----------|
| P-1 | Merged row count = size of the union of both sources' `(TRDD, IS_CD)` key sets |
| P-2 | For every merged row, each counter = sum of that counter across the sources holding that key |
| P-3 | Paging the whole result set by keyset yields exactly the same multiset as one unpaginated fetch |
| P-4 | No row appears twice and none is skipped, across arbitrary page-boundary placements |
| P-5 | Rows present in only one source appear once, unmodified |
| P-6 | Result is byte-identical whether the bulk source is read first or second |
| P-7 | Reported total count equals actual merged row count, up to the `MAX_KEY_PROBE` ceiling |

P-3 and P-4 are the ones that matter: **page boundaries falling exactly on a key present in both sources** is the defect this design could plausibly have, and random page sizes over generated data is the way to find it.

## 4. Negative-path security suite

Derived from [threat-model-REPORT.md](threat-model-REPORT.md); every threat with a mitigation has a test.

| Test | Threat | Case | Expected |
|------|--------|------|----------|
| S-R01 | T-R01 | Both endpoints, no session | 401, no body |
| S-R02 | T-R04 | Tenant user supplies another institution's `IS_CD` | Scoped to own institution; no foreign rows |
| S-R03 | T-R04 | Tenant user supplies empty `IS_CD` (legacy "all") | Scoped to own institution |
| S-R04 | T-R10 | Enumerate 50 institution codes on **both** endpoints | No foreign data recovered from any response |
| S-R05 | T-R20 | Tenant user requests 전체 explicitly | 403 or narrowed; never all-institution data |
| S-R06 | T-R21 | Export called directly with parameters the query would reject | Rejected identically to the query |
| S-R07 | T-R07 | `START_DT` = `2026%0d%0aSet-Cookie:x=y` | 400; response carries no injected header |
| S-R08 | T-R07 | Filename source containing quotes and multi-byte characters | Correctly encoded header; file name intact |
| S-R09 | T-R05, T-R16 | `START_DT=00000000&END_DT=99999999` | 400; no query executed |
| S-R10 | T-R17 | Export at and beyond the row ceiling | Within: streamed file. Beyond: 400 with an actionable message, **never a truncated file** |
| S-R11 | T-R06 | Injection payloads in every parameter | Bound as literals; no SQL error, no data leak |
| S-R12 | T-R08, T-R09 | Successful and denied requests | Both audited, with actor and scope |
| S-R13 | T-R12 | Inspect both responses | `RGDT`, `FT_CNT` absent |
| S-R14 | T-R13 | Tenant user loads the institution selector | Only entitled institutions returned |
| S-R15 | T-R15 | Application logs during a full query and export | No aggregate figures in clear |
| S-R16 | T-R03 | Cross-origin export attempt | Blocked by `SameSite` + same-origin credential policy |

**S-R04 and S-R06 are the gate tests.** T-R10 is the highest-severity threat in the programme, and S-R06 exists because the legacy's export was the *less* protected of the two doors — the failure mode is not "we forgot authorization" but "we secured one entry point and not the other."

## 5. Source-degradation suite

FR-RPTS-005 and NFR-OPS-R01 are new obligations created by the two-datasource design. They cannot be tested by taking a schema offline when both schemas share an instance (§2), so they are tested by fault injection at the `DataSource` layer.

| Test | Case | Expected |
|------|------|----------|
| F-R01 | Bulk source unavailable | API figures returned; response marked incomplete, naming the bulk source |
| F-R02 | Bulk source times out | Same as F-R01 within the configured timeout; **no retry loop** (CODE-002) |
| F-R03 | API source unavailable | Symmetric behaviour; the design favours neither source |
| F-R04 | Both unavailable | Explicit error; never an empty result presented as zero |
| F-R05 | Bulk source has no rows for a day the API source covers, across all institutions | Day flagged as a **possible** source gap (ADR-RPT-022 heuristic) |
| F-R06 | Single institution genuinely has no bulk traffic for a day | **No gap flag** — the heuristic must not fire on a legitimately quiet day |
| F-R07 | Export while one source is down | Workbook marked incomplete, or refused; never silently partial |

F-R06 is the false-positive guard for the one heuristic in the design, and it is the test that keeps ADR-RPT-022's compromise honest.

## 6. Freshness and reconciliation suite

| Test | Requirement | Case | Expected |
|------|-------------|------|----------|
| W-R01 | FR-RPT-013 | Range ending today | Days above the watermark labelled 미집계, not 0 |
| W-R02 | FR-RPT-013 | Default screen load | As-of date displayed per source without user action |
| W-R03 | FR-RPT-014 | Empty result below the watermark | 조회된 내용이 없습니다 — distinct from 미집계 and from an error |
| W-R04 | FR-RPT-010 | Seeded rows across all channels | 전체 = 성공 + 실패 + 처리중 on every row |
| W-R05 | FR-RPT-010, CONST-BIZ-R01 | Seeded `FTIMG`/`FTIMGWI` | 일반이미지 = 전체이미지 − 와이드 exactly |
| W-R06 | CONST-BIZ-R02 | Seeded row | 총 건수 uses `FT_CNT` once; `FTTXT`/`FTIMG` not re-added |
| W-R07 | FR-RPT-010 | Row deliberately violating the identity | Reported as a data-quality error, **not rendered as fact** |

W-R07 is the assertion that FR-RPT-010 is a detector rather than a comment.

## 7. 7-dimension self-assessment

Threshold **≥ 90 / 100**, per harness default. Run by `team-leader` at the end of R2, maximum five iterations.

## 8. Security testing (3-stage hook)

| Stage | Scope |
|-------|-------|
| L1 pre-commit | Secret scan; no credential or connection string in source — **both** datasource configurations |
| L2 pre-merge | SAST; OWASP Top 10 automated checks; header-injection rules on the export path |
| L3 pre-release | Full negative-path suite (§4), dependency CVE scan, `security-auditor` review of both endpoints |

Header-injection rules are called out explicitly at L2 because D-R3 is the kind of defect that reappears the moment someone composes a filename by concatenation.

## 9. Load testing

| Test | Requirement | Load | Target |
|------|-------------|------|--------|
| L-R01 | NFR-PERF-R01 | 31 days, all institutions, 100 rows/page | P95 < 3 s |
| L-R02 | NFR-PERF-R02 | 366 days, all institutions, paged | P95 < 5 s per page |
| L-R03 | NFR-PERF-R03 | 92-day all-institution export | < 60 s |
| L-R04 | NFR-SCALE-R01 | Export at 1k / 10k / 100k rows | **Flat heap profile** |
| L-R05 | Harness §3 | All of the above at 2× SLA load | No degradation beyond target |
| L-R06 | ADR-RPT-021 | Total-count key probe at the 366-day cap | Within budget, or `hasMore` returned |

**L-R03 and L-R04 together set AMB-R05.** The row ceiling is not a guessed number; it is the largest export that holds NFR-PERF-R03 with a flat heap, measured and then recorded in the ADR.

## 10. Test environments

| Environment | Purpose | Constraint |
|-------------|---------|-----------|
| Local | Unit, mapper, property tests | Tier 3/4; embedded PostgreSQL, two schemas |
| CI | Full suite except tier 1 | No Docker (RISK-S13); tier 2 if a shared instance is reachable |
| Staging | E2E, load, security | Requires realistic volume in **both** sources — RISK-R06 |
| Production | Access-log review only | Operational action, not a test (DEV-PLAN §4.4) |

## 11. Spec parity

Parity is **intent parity**, per the AMB-01 ruling. Twenty-five behaviours deliberately differ from the legacy and each is an approved exception tracked in `defect_ref`.

Known, accepted output divergences from the legacy workbook, recorded so they are not raised as parity failures:

| Divergence | Reason |
|------------|--------|
| Two sheets instead of four | BULK folded into the 구분 column (FR-RPTS-003, AMB-R01) |
| Column widths differ | SXSSF cannot auto-size over a streamed window; widths come from a bounded sample ([ADR-RPT-023](adr/ADR-RPT-023-export-generation.md)) |
| Summary sheet carries 실패 and 처리중 | D-R14 fix, PM ruling AMB-R02 |
| Filename changed | D-R20 fix — the legacy name disagreed with the workbook's own title |
| Sort order fixed to 일자 desc | D-R7 fix — the legacy differed by environment |

## 12. E2E core scenarios (TOP 5)

| # | Scenario | Covers |
|---|----------|--------|
| E-R1 | Operator queries 전체 over a month, pages forward and back, figures reconcile | UC-RPT-001; FR-RPT-005/006/009/010, FR-RPTS-003 |
| E-R2 | Tenant user opens the screen and can reach only their own institution, with no 전체 option | FR-AZ-R03/R04; T-R04, T-R20 |
| E-R3 | Operator exports the current query; workbook matches the screen row for row | UC-RPT-002; FR-RPTX-001/007, D-R14 |
| E-R4 | Default screen load shows the as-of date and marks today 미집계 rather than empty | FR-RPT-013/014; D-R25 |
| E-R5 | Export beyond the ceiling is refused with an actionable message; a narrowed range succeeds | FR-RPTX-010 deferral, T-R17; D-R15, D-R16 |

## 13. Defect management

Findings are logged with severity, requirement, defect reference and threat ID. **CVSS ≥ 7.0 within our control blocks G3.** Threats listed as unclosable in threat-model §5 (T-R-X1…X4) are tracked as programme risks and do not block this slice's gate.
