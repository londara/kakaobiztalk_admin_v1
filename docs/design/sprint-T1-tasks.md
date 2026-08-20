# Sprint T1 Task List — 톡전송 내역 foundation, list and authorization

> **Version**: 1.0
> **Date**: 2026-08-19
> **Sprint**: T1 (weeks 1–2)
> **Predecessor**: [DEV-PLAN-TALK.md](DEV-PLAN-TALK.md)
> **Companion**: [TEST-PLAN-TALK.md](TEST-PLAN-TALK.md)

---

## Scope

Everything the list screen needs, plus the two registries and the identity type that Sprint T2's drill-down and export depend on.

**Closes 12 defects**: D-T2, D-T3, D-T9, D-T10, D-T11, D-T15, D-T24, D-T25, D-T26, D-T27, D-T28, D-T29, D-T31.

> **Correction (Sprint T1, 2026-08-19).** D-T32 was listed here in error: the `EXCEL_RSLT`/`EXCEL_RLST` id typo lives in the export path, which Sprint T2 builds. A defect cannot close in a sprint that does not build the code containing it. Moved to T2 — see [SPRINT-T1-RETRO.md](../sprints/SPRINT-T1-RETRO.md) action A7.

**Delivers**: FR-AZ-T01…T06, FR-TLK-001…015, NFR-SEC-AUTHZ-T01, NFR-SEC-TENANT-T01, NFR-PERF-T01.
**Unblocks**: FR-TLKD-009 (via T1-01b), and all of Sprint T2.

Sprint T2 owns the drill-down, the message detail, masking and the export.

## Tasks

### T1-01a — `lpad` truncation behaviour
**Owner** `qa-engineer` · **Est** 0.5d · **Blocks** T1-05
Reproduce the D-T9 mechanism as an executable test: `lpad('26081900142813', 10, '0')` returns `'2608190014'` — PostgreSQL truncates rather than refusing. Plain SQL, so `io.zonky.test:embedded-postgres` (Apache-2.0) reproduces it faithfully without Docker.
**Tests** TC-T002-09's mechanism half. **Why it splits from T1-01b** the *behaviour* is verifiable in this environment; the *widths* are not.

### T1-01b — Stored widths and join cardinality (needs a DBA)
**Owner** `data-model-designer` + DBA · **Est** 0.5d · **Blocks** the final `TransactionSerial` configuration
One query, run by someone with production-like read access: `max(length(IS_TUNO))` on `FT_APITR_HSTR`; `min/max(length(SERIALNUM))` on `KKO_MSG`, `KKF_MSG` and archives; the `IS_TUNO` → `SERIALNUM` join cardinality; whether any serial contains a non-digit.
**Output** a findings note that configures `TransactionSerial` and flips FR-TLKD-009 from `BLOCKED-AMB-T04` to `SPECIFIED`.
**Why it is a request, not access** Docker is prohibited (RISK-R01/RISK-T13) and no production-like dataset is reachable from this environment. The analogous task R1-01 in the 이용기관 보고서 sprint was **carried unconfirmed** for exactly this reason; splitting the task is what stops that repeating silently.
**Interim** `TransactionSerial` ships with the widths as **configuration**, defaulted from the observed 20-character 거래고유번호 and the legacy's 10-character `lpad` target, and logs at WARN when an input exceeds the configured width instead of truncating. A guess that announces itself is not the same defect as a guess that truncates.

### T1-02 — `BizTalkApiRegistry` + startup validation
**Owner** `backend-developer` · **Est** 1d · **Blocks** T1-06, T1-14
Config-held allow-list of `API_SVC_CD` values, seeded with the five source-derived literals. On startup, every configured code is checked against `FT_OPENAPI_INFO`; a missing one logs WARN naming the code.
**Tests** TC-REG-01, TC-REG-04. **Req** FR-TLK-002. **ADR** ADR-TLK-024.

### T1-03 — `TalkDetailRegistry` + containment check
**Owner** `backend-developer` · **Est** 0.5d · **Blocks** T1-06
Maps `API_SVC_CD` → `AT` | `FT` | `none`. Startup fails if a code has a channel but no `BizTalkApiRegistry` entry.
**Tests** TC-REG-02. **Req** FR-TLK-013, FR-TLKD-005, FR-TLKM-006. **ADR** ADR-TLK-026.

### T1-04 — Move `ReportScope` → `common.tenant.PrincipalScope`
**Owner** `architect` · **Est** 0.5d · **Blocks** T1-08 · **Gate**
Move the class, keep the semantics exactly, then **re-run the 이용기관 보고서 slice's authorization tests unchanged**.
**Exit condition** the 보고서 suite is green. If it is not, revert to a duplicated class and record the reason — an adjusted authorization rule on shipped code is not an acceptable outcome of a refactor.
**Risk** RISK-T06.

### T1-05 — `TransactionSerial`
**Owner** `backend-developer` · **Est** 0.5d · **Depends** T1-01
One type, two renderers (transaction form, message form). Rejects non-numeric input. **No padding in SQL.**
**Tests** TC-T001-09, TC-T002-10. **Req** FR-TLK-009. **ADR** ADR-TLK-025.

### T1-06 — `TalkHistoryMapper`
**Owner** `backend-developer` · **Est** 1.5d · **Depends** T1-02, T1-03, T1-05
Nine-column closed `resultMap`. 기관명 by **join**, not correlated subquery. API scope from the registry. No `SELECT *`.
**Tests** TC-T001-11, TC-T001-12, TC-T001-03. **Req** FR-TLK-003, FR-TLK-011, FR-TLK-012, CONST-SEC-T01.

### T1-07 — Keyset paging and total order
**Owner** `backend-developer` · **Est** 1d · **Depends** T1-06 · **Blocks** T2-05
Order `(RGDT DESC, IS_TUNO DESC)`; keyset pagination; separate count query for the pager.
**Tests** TC-T001-05, TC-T001-06 — the latter over a **100-row single-timestamp fixture**.
**Req** FR-TLK-005, FR-TLK-006. **Risk** RISK-T08.

### T1-08 — Authorization
**Owner** `backend-developer` + `security-auditor` · **Est** 1d · **Depends** T1-04
Operator role enforced server-side on every endpoint. Scope from `PrincipalScope`. Out-of-scope values ignored, never validated-then-rejected.
**Tests** TC-T001-01, TC-T001-02, TC-T001-15. **Req** FR-AZ-T01, FR-AZ-T02, NFR-SEC-AUTHZ-T01, NFR-SEC-TENANT-T01.

### T1-09 — Read audit
**Owner** `backend-developer` · **Est** 0.5d
`AuditService` event per query: actor, timestamp, filters, scope, row count.
**Req** FR-AZ-T05. **ADR** ADR-006.

### T1-10 — `TalkHistoryService` + API
**Owner** `backend-developer` · **Est** 1.5d · **Depends** T1-06…T1-09, T1-11 · **Blocks** all of T2
Two endpoints: list and API selector. Validated request record — **nothing read from the raw request**. `detailAvailable` computed per row from `TalkDetailRegistry`.
**Tests** TC-T001-10, TC-T001-13, TC-T001-16. **Req** FR-TLK-001, FR-TLK-010, FR-TLK-013.

### T1-11 — `TalkPeriodPolicy` (31 days)
**Owner** `backend-developer` · **Est** 0.5d · **Blocks** T1-10
Second configured instance of `PeriodPolicy`, cap 31 days (AMB-T02). Real time bounds; no `999999` sentinel; an omitted bound means the whole day. Server-side inversion and calendar checks.
**Tests** TC-T001-07, TC-T001-08. **Req** FR-TLK-007, FR-TLK-008, FR-TLK-014.

### T1-12 — React list screen
**Owner** `frontend-developer` · **Est** 2d · **Depends** T1-10
Filters, grid, pager. Link rendered **only** where `row.detailAvailable`. 상태 labels from the same source as the filter options. Label + raw code for coded values.
**Tests** TC-T001-04, TC-T001-16, E2E-1. **Req** FR-TLK-003, FR-TLK-004, FR-TLK-013, FR-TLK-015, NFR-USE-T01.

### T1-13 — Test fixtures
**Owner** `qa-engineer` · **Est** 1d · **Blocks** the T1 and T2 regression suites
Build deliberately: a 100-row tied-timestamp block; serials at 10, 14 and 20 characters; one transaction per registry outcome including `none` and `ADV_KKO_AT_SEND2`; an institution code absent from the master; a message with a null result code.
**Why a task** none of these appear naturally in staging, and eleven tests depend on them. **Risk** RISK-T10.

### T1-14 — Reconciliation report
**Owner** `backend-developer` · **Est** 1d · **Depends** T1-02
Scheduled query counting, per `API_SVC_CD` **not** in the allow-list, transactions whose institution has BizTalk service registered.
**Tests** TC-REG-03. **Why it exists** SCOPE-T01 makes under-inclusion possible and silent; this is the only thing that makes it visible. **Risk** RISK-T01.

### T1-15 — Endpoint inventory and `30_l002` retirement
**Owner** `security-auditor` · **Est** 0.5d
Assert against the application's full route table that no endpoint returns unmasked numbers over a date range. Separately, propose to operations that `WSVC.biztalk_admin_30_l002` be set `useYn=N` in the legacy — it has no caller, so disabling it carries near-zero operational risk and removes a CVSS 7.7 exposure before cutover.
**Tests** TC-T001-14. **Req** FR-AZ-T06. **Risk** RISK-T03.

### T1-16 — Negative-path security tests
**Owner** `qa-engineer` + `security-auditor` · **Est** 1d · **Depends** T1-10
Unauthenticated calls, tenant calls, tampered scope, over-cap and malformed ranges, direct service calls bypassing the UI.
**Tests** TC-T001-01, -02, -07, -15. **Threats** TM-T01, TM-T08, TM-T10.

## Dependency order

```
T1-01 ─► T1-05 ─┐
T1-02 ─┬────────┼─► T1-06 ─► T1-07 ─┐
T1-03 ─┘        │                   │
T1-04 ─► T1-08 ─┼───────────────────┼─► T1-10 ─┬─► T1-12 ─► E2E
T1-09 ──────────┤                   │          └─► T1-16
T1-11 ──────────┘                   │
T1-02 ─► T1-14                      │
T1-13 ─────────────────────────────►┘   (fixtures gate the suites)
T1-15  independent
```

Critical path: **T1-01 → T1-05 → T1-06 → T1-07 → T1-10 → T1-12**. T1-04 is a hard gate on T1-08 and must clear in week one.

## Sprint DoD

- [ ] 12 defects closed, each with a named regression test
- [ ] FR-AZ-T01…T06 and FR-TLK-001…015 implemented and traced in the matrix
- [ ] T1-01 complete; FR-TLKD-009 no longer `BLOCKED-AMB-T04`
- [ ] T1-04 complete; **보고서 authorization suite green against `PrincipalScope`**
- [ ] Startup validation live: unknown configured code warns; unmapped-but-classified code fails
- [ ] Paging correctness proven on a tied-timestamp fixture
- [ ] Contract test asserts the exact nine-field response; no `SELECT *` in the package
- [ ] TC-T001-14 asserts no unmasked-numbers-over-a-range endpoint exists
- [ ] Coverage ≥ 80% line / ≥ 70% branch; domain classes ≥ 95%
- [ ] T1-14 has produced at least one report against real data (feeds AMB-T03)
- [ ] T1-15 retirement proposal recorded with operations

## Handover to T2

T2 receives: the list iterator (T1-07) that the export must reuse, both registries, `TransactionSerial` with measured widths, `PrincipalScope`, and the fixture set.

T2's first task (T2-01, `TalkMessageMapper`) is where the slice's PII work begins — masking placement at the outermost projection, per ADR-005 and ADR-TLK-027 — and its riskiest is T2-05, the export, because that is where D-T1 is closed and the test that proves it is a set equality against T1-07's output.
