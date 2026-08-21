# Requirements Specification — 이용기관 보고서 (Institution Usage Report)

> **Version**: 1.0
> **Date**: 2026-08-18
> **Scope**: legacy screen **20 (이용기관 보고서)** and its Excel export **20_spreadsheet**
> **Predecessors**: [PROJECT-PROPOSAL.md](../planning/PROJECT-PROPOSAL.md), [BUSINESS-REQUIREMENTS.md](../planning/BUSINESS-REQUIREMENTS.md)
> **Sibling specs**: [REQUIREMENTS-SPEC.md](REQUIREMENTS-SPEC.md) (문자내역), [REQUIREMENTS-SPEC-LOGIN.md](REQUIREMENTS-SPEC-LOGIN.md) (로그인), [REQUIREMENTS-SPEC-INSTITUTION.md](REQUIREMENTS-SPEC-INSTITUTION.md) (이용기관관리), [REQUIREMENTS-SPEC-SENDERNO.md](REQUIREMENTS-SPEC-SENDERNO.md) (발신번호)
> **Traceability matrix**: [requirements-matrix.csv](requirements-matrix.csv)
> **Question log**: [questions-log.md](questions-log.md) — Part 5
> **Status**: **APPROVED (G1)** — 2026-08-21, PM (CONFLICT-R02 는 AMB-R04 확인까지 이월 / CONFLICT-R02 carried pending the AMB-R04 check)

---

## 1. Overview

This document specifies the 이용기관 보고서 feature of the new IRIS BizTalk Portal, derived by static analysis of 14 legacy artifacts. As with the four earlier slices there is no runnable legacy environment (proposal RISK-001), so every requirement was recovered by reading source across five layers — view, client script, action, service contract, query — and cross-checking the layers against one another.

Screen 20 is a **read-only daily aggregate report**. An operator picks an 이용기관 (defaulting to 전체) and a 요청일자 range, and receives per-day counts of every message channel — 알림톡, 친구톡 (텍스트 / 일반이미지 / 와이드이미지), SMS, LMS, MMS — each broken into 전체 / 성공 / 실패. A 다운로드 button produces a multi-sheet Excel workbook of the same figures.

Two properties set this slice apart from the four before it, and both drive most of what follows.

**It is the first slice with no PII.** Nothing here is a phone number, a name, or a credential. Every field is a count. That removes the masking and encryption concerns that dominated 발신번호 and 문자내역 — and replaces them with a different sensitivity: the report discloses **each customer's business volume**, and today it discloses every customer's volume to anyone who can reach the endpoint (D-R1, D-R2). Commercial confidentiality, not personal privacy, is the control objective here.

**It does not own its data.** `KKB_APITR_SMTN` is written by `BATCH_BIZTALK_DAILY` (BR-009), not by this screen. The report can only be as correct, as complete and as current as that batch — and §7 shows the batch is none of those things in ways the screen never reveals. The most consequential requirement in this specification is therefore not a query requirement at all; it is FR-RPT-013, which makes the data's actual as-of date visible.

**Authentication and session handling are not re-specified here.** They are settled in [REQUIREMENTS-SPEC-LOGIN.md](REQUIREMENTS-SPEC-LOGIN.md) and inherited unchanged. What this document specifies is **authorization and scope** — which authenticated principal may see whose figures — because screen 20 is where the AMB-02 tenant ruling and the report's cross-institution purpose collide (CONFLICT-R01, §5.1).

### 1.1 Legacy artifacts analyzed

| Layer | File |
|-------|------|
| View | `IRIS_ADMIN/web/view/jex/iris_admin/biztalk/biztalk_admin_20_view.jsp` (106 L) |
| View (export) | `…/biztalk_admin_20_spreadsheet_view.jsp` (470 L) |
| Client logic | `IRIS_ADMIN_STATIC/…/biztalk/biztalk_admin_20.js` (159 L) |
| Client logic (export) | `…/biztalk_admin_20_spreadsheet.js` (64 L, unreferenced) |
| Action | `biztalk_admin_20_l001_act.jsp` (집계조회, 123 L), `biztalk_admin_20_spreadsheet_act.jsp` (집계다운로드, 162 L) |
| Service contract | `WSVC.biztalk_admin_20`, `WSVC.biztalk_admin_20_l001`, `WSVC.biztalk_admin_20_spreadsheet` |
| Query | `IDO.KKB_APITR_SMTN_L001` (daily detail), `_L002` (per-institution total) |
| Query (bulk) | `IDO.BULK_KKB_APITR_SMTN_L001`, `_L002` — identical SQL against `BIZTALK_BULK_DB` |
| Query (institution) | `IDO.KKB_FT_FTIS_INFO_L001` |
| Upstream batch | `IRIS_ADMIN/src/batch/…/BATCH_BIZTALK_DAILY.java`, `BAT.BATCH_BIZTALK_DAILY.xml`, `IDO.KKB_APITR_SMTN_C001`/`D001` (API), `_C011`/`_D011` (bulk) |
| Cross-slice | `biztalk_admin_00_l001` (institution combo) |

### 1.2 Data model

The report reads two targets, and the fact that there are two is the structural theme of the slice.

| Datasource | Table | Role |
|------------|-------|------|
| `BIZTALK_DB` | `KKB_APITR_SMTN` | Daily aggregate of **API 발송**. Key: `TRDD` + `IS_CD` |
| `BIZTALK_BULK_DB` | `KKB_APITR_SMTN` | Daily aggregate of **대량발송**. Same table name, same columns, separate datasource |
| `BIZTALK_DB` | `FT_FTIS_INFO` | Institution master — read-only, joined for 기관명 by correlated subquery. Key: `FINTECH_ISCD` |

Both aggregate tables are **written only by `BATCH_BIZTALK_DAILY`** and are read-only to this slice (CONST-DATA-R01).

`KKB_APITR_SMTN` carries **four counters per channel**, recovered from the union of both queries:

| Suffix | Meaning |
|--------|---------|
| `*_CNT` | 전체 — total requested |
| `*_SCS_CNT` | 성공 |
| `*_FAIL_CNT` | 실패 |
| `*_PCSNG_CNT` | **처리중** — in flight |

across the channel prefixes `AT_` (알림톡), `FT_` (친구톡 전체), `FTTXT_` (친구톡 텍스트), `FTIMG_` (친구톡 이미지, **inclusive of wide**), `FTIMGWI_` (친구톡 와이드 이미지), `SMS_`, `LMS_`, `MMS_`. Plus `TRDD`, `IS_CD`, `RGDT`.

Two derived figures are computed in SQL rather than stored, and both must be preserved:

- **친구톡 일반이미지** = `FTIMG_* − FTIMGWI_*` (CONST-BIZ-R01)
- **총 건수** = `AT_CNT + FT_CNT + MMS_CNT + SMS_CNT + LMS_CNT` (`KKB_APITR_SMTN_L002`)

The four-counter structure is the origin of D-R14: the UI displays three of the four, so 전체 ≠ 성공 + 실패 on every row that has anything in flight.

### 1.3 Classification and priority

Per harness standard: `FR-` functional, `NFR-<area>-` non-functional, `CONST-<area>-` constraint, `UC-` use case. Priority is MoSCoW. Requirement families in this slice: `FR-AZ-R*` (access control), `FR-RPT-*` (report query), `FR-RPTS-*` (source merge), `FR-RPTX-*` (Excel export).

### 1.4 PM rulings (2026-08-18)

Four questions were put to the PM before any requirement was written. All four changed the specification materially; the first was a conflict with a standing G1 decision and is recorded as CONFLICT-R01.

| ID | Question | Ruling |
|----|----------|--------|
| CONFLICT-R01 | The report's 전체 default vs AMB-02 ("scope is server-derived; client `IS_CD` is ignored") | **Operator role only** — operators may query 전체 or any institution; tenant users are forcibly narrowed. AMB-02 is refined, not overturned |
| AMB-R01 | Merging `BIZTALK_DB` and `BIZTALK_BULK_DB` | **Merge with a 발송구분 filter** (API / 대량 / 전체), default 전체 = summed. Environment-dependent behaviour removed |
| AMB-R02 | 처리중 counts fetched but never displayed | **Add 처리중 columns**, and make 전체 = 성공 + 실패 + 처리중 an asserted rule |
| AMB-R03 | Period cap | **366 days**, server-enforced. Server-side pagination becomes mandatory rather than optional |

### 1.5 Defect disposition

Twenty-seven defects were confirmed — twenty-five in the slice, two upstream in `BATCH_BIZTALK_DAILY` (§7). Consistent with AMB-01 ("fix all"), **all in-slice defects are fixed**. The two batch defects are **outside this slice's boundary**; they are specified as constraints on what the report may promise (FR-RPT-013, FR-RPTS-005) and raised for the batch owner.

| ID | Sev | Defect | Disposition |
|----|-----|--------|-------------|
| D-R1 | Critical | **The report data service is anonymous.** `WSVC.biztalk_admin_20_l001` declares `<login>N</login>`, while the screen (`_20`) and the export (`_20_spreadsheet`) both declare `Y`. The one service that actually returns the numbers is the one that requires no session | FIX → FR-AZ-R01 |
| D-R2 | Critical | **No authorization and no tenant isolation.** `IS_CD` is taken verbatim from the request and an empty value means *all institutions* (`AND (:IS_CD = '' OR IS_CD = :IS_CD)`). Combined with D-R1, every customer's transaction volume is readable by an unauthenticated caller who omits one parameter | FIX → FR-AZ-R02, FR-AZ-R03 |
| D-R3 | Critical | **Response-splitting in the export.** `START_DT`/`END_DT` are read unvalidated via `request.getParameter`, concatenated into `FILE_NM`, and written to `Content-Disposition`. The non-IE branch only recodes the bytes (`getBytes("UTF-8"), "ISO-8859-1"`) — no encoding, no CR/LF rejection. The IE branch happens to be safe because it calls `URLEncoder.encode` | FIX → FR-RPTX-004, NFR-SEC-HDR-R01 |
| D-R4 | High | **The export is broken off production.** `_spreadsheet_act.jsp` populates `REC2`/`REC3` only when `TSTCL_DV=REAL`, but `_spreadsheet_view.jsp` guards the BULK sheets with `if(true)` — `isRealSever` is computed, logged, and then never used. Off REAL the view iterates record sets the action never put | FIX → FR-RPTS-004, FR-RPTX-008 |
| D-R5 | High | **The result set is shipped as a Java array literal and parsed as JSON.** `result.put("REC2", Arrays.toString(concatStream.toArray()))`, then `JSON.parse(dat.REC2)` in the browser. This depends on `JexData.toString()` emitting strict JSON and breaks on any value containing a quote or backslash — 기관명 is free text. `WSVC.biztalk_admin_20_l001` compounds it by declaring `REC2` as a **scalar**, while `WSVC.biztalk_admin_20_spreadsheet` declares its own `REC2` as `type="RECORD"` | FIX → FR-RPT-007, FR-RPT-008 |
| D-R6 | High | **The response shape depends on the environment.** Off REAL the action returns `REC` (a record list) and no `REC2`; on REAL it returns `REC2` (a string) and no `REC`. The client branches on `dat.REC2 === undefined`. No test environment can exercise the production contract | FIX → FR-RPT-007, FR-RPTS-004 |
| D-R7 | High | **Sort order depends on the environment.** Off REAL the order is SQL `ORDER BY TRDD` (ascending). On REAL it is a Java comparator `Integer.parseInt(b.TRDD) - Integer.parseInt(a.TRDD)` (descending). Neither defines an order **within** a date, so rows for the same day are arbitrary in both. `Integer.parseInt` also throws on a null or blank `TRDD`, failing the whole report | FIX → FR-RPT-006 |
| D-R8 | High | **Paging is declared and never implemented** — fourth occurrence of this class (D7, D-I10, D-S14). `_gu.gridPaging()` is wired in the JS, `PAGE_NO`/`INQ_TOTL_NCNT` are commented out, neither contract declares them, and neither IDO has `LIMIT`/`OFFSET`. The entire result set is fetched and paged in the browser | FIX → FR-RPT-005 |
| D-R9 | High | **No period cap and no server-side date validation.** The only check is `Number(startDt) > Number(endDt)` in the browser. `WSVC.biztalk_admin_20_l001` declares `START_DT`/`END_DT` with no length and no type, and the action validates nothing. A direct call with `START_DT=00000000&END_DT=99999999` scans both tables end to end | FIX → FR-RPT-002, FR-RPT-003, FR-RPT-004 |
| D-R10 | Medium | **The export bypasses its own contract.** `WSVC.biztalk_admin_20_spreadsheet` declares only `START_DT` and `END_DT` in `<in>`; `IS_CD` is undeclared. The action therefore cannot read it from the input domain and pulls all three straight from `request.getParameter`, bypassing every declared length and `fullChar` rule — which is also how D-R3 becomes reachable | FIX → FR-RPTX-002, FR-RPTX-003 |
| D-R11 | Medium | **NULL propagation in every derived figure.** There is no `COALESCE` anywhere. `(FTIMG_CNT - FTIMGWI_CNT)` is NULL if either side is NULL, and `sum(AT_CNT + FT_CNT + MMS_CNT + SMS_CNT + LMS_CNT)` returns NULL for an institution's **entire total** if any one column is NULL on any one row. The cell renders blank, not zero | FIX → FR-RPT-011 |
| D-R12 | Medium | **기관명 is resolved two different ways and fails silently in both.** API rows use a correlated subquery that yields NULL for an unmatched code; BULK rows select `'' AS IS_NM` and are patched in Java from a `HashMap`, where `fin.get()` returns null for an unmatched code. Either path produces a row with a blank institution name and no indication that a lookup failed | FIX → FR-RPT-012 |
| D-R13 | Medium | 기관명 is fetched by a **correlated subquery evaluated once per result row** rather than a join | FIX → NFR-PERF-R01 |
| D-R14 | Medium | **전체 ≠ 성공 + 실패.** All four counters are stored and returned; the grid displays 전체/성공/실패 and the Excel summary sheet displays only 전체/성공. `*_PCSNG_CNT` is declared on both contracts and rendered nowhere, so the arithmetic never closes and the report looks wrong to anyone who adds it up | FIX → FR-RPT-009, FR-RPT-010 (PM ruling AMB-R02) |
| D-R15 | Medium | **The workbook is built entirely in memory.** `XSSFWorkbook` with up to four sheets over an uncapped range × all institutions, materialised before the first byte is written. No `SXSSF`, no row cap, no async path — an OOM whose likelihood scales directly with D-R9 | FIX → FR-RPTX-009, FR-RPTX-010, NFR-SCALE-R01 |
| D-R16 | Medium | **Export failures are invisible.** The download posts to a hidden iframe (`ifrmFileProc`). If the action throws, the Jex error page renders inside an invisible frame: the user gets no file, no message, and no indication anything happened | FIX → FR-RPTX-011 |
| D-R17 | Medium | **No business audit.** `mntLogYn=Y` yields a Jex service-monitor record, which disappears with Jex (BR-002…005). Nothing records who exported which institutions' volumes over which period — the most sensitive action on the screen | FIX → FR-AZ-R05, FR-RPTX-012, NFR-OPS-AUDIT-R01 |
| D-R18 | Low | Sheet title merges span the wrong width: `CellRangeAddress(0,1,0,14)` is 15 columns for a 17-column header; the detail sheet merges 18 for a 24-column header | FIX → FR-RPTX-013 |
| D-R19 | Low | **Content type is set four times, inconsistently** — page directive `application/vnd.ms-excel; name='excel',text/html`, then `setContentType("application/vnd.ms-excel")` (non-IE branch only), then `setContentType("application/download; UTF-8")`, then `setHeader("Content-Type", "application/octet-stream")`. The file is `.xlsx`, for which `application/vnd.ms-excel` is wrong in any case | FIX → FR-RPTX-005 |
| D-R20 | Low | The workbook is titled 카카오 비즈톡 집계 and the file is named 비즈톡거래내역 — two names for one artifact | FIX → FR-RPTX-006 |
| D-R21 | Low | Every header cell is created twice: `headRow.createCell(i).setCellValue(…)` immediately followed by a second `createCell(i)` at the same index, discarding the first. Repeated across all four sheets | FIX → §2.7 |
| D-R22 | Low | Dead and misleading code: `gridColName` declared twice in one scope; the entire ajax implementation of `fn_downloadExcel` left commented below the live one; `var testrec2` never assigned; `popCallbackFn()` never referenced (the screen has no popup); `var num = new Number()` unused; the `#IS_LIST` handler carries commented lines referencing a `#tmSel` element that does not exist here; `biztalk_admin_20_spreadsheet.js` is referenced by no view | FIX → §2.7 |
| D-R23 | Low | `getDat()` runs in `onload` **before** `fn_getIsList()` has populated the combo — the same ordering as D-S19. Harmless in effect, because an empty `IS_CD` means 전체 and that is the intended default, but the screen queries before the user can choose and before its own scope is known | FIX → FR-RPT-015 |
| D-R24 | Low | Over-fetch: both contracts return `RGDT`, `FT_CNT` and every `*_PCSNG_CNT` field; the grid binds 24 of ~33. After AMB-R02 the 처리중 fields become displayed, but `RGDT` and `FT_CNT` remain shipped and unused | FIX → FR-RPT-016 |
| D-R25 | Medium | **The screen defaults to a date range it can never have data for.** Both date inputs default to today, but `KKB_APITR_SMTN` is batch-populated and the batch's default run covers a **single day, four days ago** (§7, D-R26). So the default query on page load returns "조회된 내용이 없습니다" — which is exactly what the production screen shows. Nothing tells the user the data is batch-derived or how current it is | FIX → FR-RPT-013, FR-RPT-014 |
| D-R26 | High | **Upstream (batch).** `BATCH_BIZTALK_DAILY` with no parameters aggregates `LocalDate.now().minusDays(4)` — one day, four days back. With parameters it requires `startDate.isBefore(endDate)` **strictly**, so re-aggregating a single day is impossible: `START_DT=END_DT` throws `INPUT ERROR`. The report's data is therefore at best T-4 and cannot be repaired one day at a time | **OUT OF SLICE** → constrains FR-RPT-013; raised as OI-R01 |
| D-R27 | High | **Upstream (batch).** Aggregation is delete-then-insert per day with no transaction. For `BIZTALK_DB` a failed insert throws and the day is left **deleted and not replaced**. For `BIZTALK_BULK_DB` the whole block is wrapped in `catch(JexBIZException e){ LOG.error(e); }` — the batch **reports success** while that day's bulk aggregate is silently zero. The report then displays the zero as fact | **OUT OF SLICE** → constrains FR-RPTS-005; raised as OI-R01 |

> **On D-R1 and D-R2 together.** Neither is remarkable alone; the combination is. `<login>N</login>` on a list service has precedent in this codebase (D1, 문자내역). An `IS_CD` filter that treats empty as "all" is a convenience. Put them together and `biztalk_admin_20_l001.act` with `IS_CD=''` and a wide date range is an unauthenticated dump of **every customer's message volume by day and channel** — enough to infer each institution's customer base, campaign schedule and growth rate. This is the highest-value data disclosure found in any slice so far, and it is reachable with a single request and no credentials.

> **On D-R4, D-R6 and D-R7.** These are one defect wearing three hats: `TSTCL_DV` is consulted in four places (both actions, the export view, and the batch) to decide whether bulk data exists. The consequence is that **no non-production environment runs the production code path** — different response shape, different sort order, different sheet count, and an export that reads record sets the action never created. Whatever testing preceded a release, it did not exercise what shipped. AMB-R01's ruling removes the flag entirely rather than repairing its four call sites, which is why FR-RPTS-004 is written as a property of the system rather than as a fix.

---

## 2. Functional Requirements

### 2.1 Access control

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| FR-AZ-R01 | Every report service — query and export alike — requires an authenticated session. Authentication is inherited unchanged from the 로그인 slice (FR-LOGIN-*). Fixes D-R1 | Must | Security test per endpoint (anonymous call returns 401) |
| FR-AZ-R02 | Authorization is enforced **server-side on each service**, from the session's role. Fixes D-R2 | Must | Security test + code review |
| FR-AZ-R03 | Scope is role-dependent (PM ruling CONFLICT-R01): an **operator** role may request 전체 or any single 이용기관; a **tenant** user's scope is derived from the session and a client-supplied `IS_CD` is ignored, per AMB-02. Fixes D-R2 | Must | Security test (tenant user requests another institution's `IS_CD`, and requests 전체) |
| FR-AZ-R04 | The 이용기관 selector lists only institutions the caller is entitled to see, and offers 전체 only to roles entitled to it | Must | E2E test per role |
| FR-AZ-R05 | Every report query and every export writes an audit event carrying actor, timestamp, institution scope, date range, 발송구분 filter and row count. Fixes D-R17 | Must | Integration test |

> FR-AZ-R01…R05 are the whole of the protection on this screen. There is no PII to mask and no credential to rotate, so unlike the 발신번호 slice there is no second line of defence to fall back on — an authorization failure here is a full disclosure.

### 2.2 Report query

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| FR-RPT-001 | An operator selects an 이용기관 (or 전체), a 발송구분 and a 요청일자 range, and receives per-day aggregate counts by channel | Must | E2E test |
| FR-RPT-002 | The query period is capped at **366 days**, enforced server-side (PM ruling AMB-R03). Fixes D-R9 | Must | Integration test (367-day range rejected; 366 accepted) |
| FR-RPT-003 | 시작일자 ≤ 종료일자 is validated **server-side**. Fixes D-R9, where the only check was in the browser | Must | Integration test (direct service call with inverted dates) |
| FR-RPT-004 | Both dates are validated server-side as 8-digit `YYYYMMDD` calendar dates. Fixes D-R9, where the contract declared neither length nor type | Must | Integration test per malformed input |
| FR-RPT-005 | The result is **server-side paginated**; the response carries the page slice plus a total row count. Fixes D-R8 | Must | Integration test (366-day all-institution query returns one page, not the whole set) |
| FR-RPT-006 | The result has a **deterministic total order** — 일자 descending, then 발송구분, then 기관코드 — identical in every environment. Fixes D-R7, where order differed by environment and was undefined within a date | Must | Integration test (repeat query returns identical order; same assertion in every environment) |
| FR-RPT-007 | The response shape is **independent of environment and configuration**. One contract, one field set, one type per field. Fixes D-R6 | Must | Contract test executed in every environment |
| FR-RPT-008 | The response is a **structured record list**. No field carries a serialized collection requiring the client to parse it. Fixes D-R5 | Must | Contract test + code review |
| FR-RPT-009 | The grid presents 구분(발송구분), 기관명, 일자, and for each of 알림톡 · 친구톡(txt) · 친구톡(img-일반) · 친구톡(img-와이드) · SMS · LMS · MMS the four counters **전체 / 성공 / 실패 / 처리중** (PM ruling AMB-R02). Fixes D-R14 | Must | E2E test |
| FR-RPT-010 | Two arithmetic identities hold on every row and are asserted, not assumed: **전체 = 성공 + 실패 + 처리중** per channel, and **친구톡 일반이미지 = 친구톡 이미지 전체 − 친구톡 와이드** (CONST-BIZ-R01). A row violating either is reported as a data-quality error rather than displayed as fact | Must | Integration test over a seeded data set + a standing reconciliation check |
| FR-RPT-011 | A NULL counter is presented as **0**, and a NULL in one column never nullifies a row's total. Fixes D-R11 | Must | Unit test (row with NULL `FTIMGWI_CNT` yields a correct total) |
| FR-RPT-012 | If 기관명 cannot be resolved for an `IS_CD`, the row is rendered with the code and an explicit unresolved marker — never a blank cell. Fixes D-R12 | Must | Integration test (aggregate row for an `IS_CD` absent from `FT_FTIS_INFO`) |
| FR-RPT-013 | The screen displays the **aggregation as-of date** — the latest 일자 for which the batch has completed, per source — alongside the result. A range extending beyond that date is shown as *not yet aggregated*, distinctly from *zero*. Fixes D-R25; bounded by D-R26 | Must | E2E test + integration test (query a range ending today) |
| FR-RPT-014 | An empty result is presented as "조회된 내용이 없습니다", distinct from both an error and from *not yet aggregated* | Must | E2E test |
| FR-RPT-015 | No query is issued until the 이용기관 selector has loaded and the caller's permitted scope is known. Fixes D-R23 | Should | Integration test |
| FR-RPT-016 | Only fields the report presents are returned. `RGDT` and `FT_CNT` are not shipped to the browser. Fixes D-R24 | Should | Contract test |

### 2.3 Source merge (API 발송 / 대량발송)

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| FR-RPTS-001 | The report covers **both** sources — API 발송 (`BIZTALK_DB`) and 대량발송 (`BIZTALK_BULK_DB`) — in every environment (PM ruling AMB-R01) | Must | Integration test |
| FR-RPTS-002 | ~~A **발송구분** filter offers API / 대량 / 전체, defaulting to 전체~~ **DESCOPED (PM, 2026-08-19)** — the screen's criteria stay the legacy's two, 이용기관 and 요청일자, and no source picker is shown. The API still accepts `source`, so restoring the control needs no contract change. **FR-RPTS-003's merge is unaffected** and the 구분 column continues to identify each row's source | ~~Must~~ Could | E2E test (asserts the picker is absent) |
| FR-RPTS-003 | With 전체 selected, counters for the same 일자 + 기관 are **summed across both sources into a single row**, not concatenated as two rows (PM ruling AMB-R01). With API or 대량 selected, only that source is read | Must | Integration test (seeded day present in both sources yields one summed row) |
| FR-RPTS-004 | Which sources are read, the response shape, the sort order and the export's sheet composition are **identical in every environment**. No configuration flag — `TSTCL_DV` or any successor — varies them. Fixes D-R4, D-R6, D-R7 | Must | Contract test executed in every environment + code review (no environment branch in the read path) |
| FR-RPTS-005 | If one source is unavailable, or its aggregate is missing for part of the range, the report **states which source is incomplete**. It never presents a partial total as a complete one. Constrained by D-R27 | Must | Integration test (one datasource down; one day absent from one source) |

> FR-RPTS-005 exists because of D-R27. The bulk batch swallows its own failures, so "no bulk rows for 2026-08-14" and "bulk aggregation failed on 2026-08-14" are indistinguishable in the data. The report cannot tell them apart either — but it can refuse to present the first as if it were certain, which is what this requirement asks for. The underlying repair belongs to the batch (OI-R01).

### 2.4 Excel export

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| FR-RPTX-001 | The 다운로드 action exports the **current query** — same scope, same 발송구분, same period — as an `.xlsx` workbook (BR-011) | Must | E2E test (on-screen figures equal exported figures) |
| FR-RPTX-002 | The export re-applies **every** authorization and validation rule of the query (FR-AZ-R01…R03, FR-RPT-002…004). It is a second entry point to the same data, not a trusted one. Fixes D-R10 | Must | Security test (export called directly with another institution's `IS_CD`, with a 400-day range, and unauthenticated) |
| FR-RPTX-003 | Every export input — including 기관코드 and 발송구분 — is **declared on the service contract** and read from the validated input domain, never from the raw request. Fixes D-R10 | Must | Contract test + code review |
| FR-RPTX-004 | The download filename is generated server-side from validated values and encoded per RFC 6266 / RFC 5987. CR, LF and control characters can never reach a response header. Fixes D-R3 | Must | Security test (CRLF payload in `START_DT`) |
| FR-RPTX-005 | The response declares `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, set **once**. Fixes D-R19 | Must | Integration test (response headers) |
| FR-RPTX-006 | The filename names the report and its actual range consistently with the workbook's own title. Fixes D-R20 | Should | E2E test |
| FR-RPTX-007 | The workbook contains a **총합** sheet (per institution over the period) and a **일자별 상세** sheet, and both carry 전체 / 성공 / 실패 / 처리중 for every channel. Fixes D-R14, where the summary sheet showed only 전체 and 성공 | Must | Integration test (sheet and column inventory) |
| FR-RPTX-008 | Sheet composition does not vary by environment or configuration. Fixes D-R4 | Must | Contract test in every environment |
| FR-RPTX-009 | The workbook is generated with **bounded memory** — streamed rows, not a fully materialised model. Fixes D-R15 | Must | Load test (heap ceiling held at the maximum permitted range) |
| FR-RPTX-010 | Beyond a configured row threshold the export is produced **asynchronously** and the user is notified when it is ready, rather than held on a synchronous request. Threshold value is AMB-R05 | Should | Load test |
| FR-RPTX-011 | An export failure produces a **visible error** to the user. Fixes D-R16, where a failure rendered into a hidden iframe and the user saw nothing at all | Must | Integration test (forced failure surfaces a message) |
| FR-RPTX-012 | Every export is audited per FR-AZ-R05, including the row count actually written. Fixes D-R17 | Must | Integration test |
| FR-RPTX-013 | Sheet title merge ranges match the column count of the table beneath them. Fixes D-R18 | Could | Integration test (workbook geometry) |

### 2.5 Non-Functional Requirements

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| NFR-PERF-R01 | Report query **P95 < 3 s** for a 31-day, all-institution range at 100 rows per page — consistent with the AMB-08 list target. Requires 기관명 by join rather than per-row subquery (D-R13) | Must | Load test |
| NFR-PERF-R02 | Report query **P95 < 5 s** per page at the 366-day cap | Should | Load test |
| NFR-PERF-R03 | Synchronous export of a 92-day all-institution range completes in **< 60 s**; anything larger takes the asynchronous path (FR-RPTX-010) | Should | Load test |
| NFR-SCALE-R01 | Export heap usage is **bounded and independent of row count** | Must | Load test (heap profile flat across 1k / 100k rows) |
| NFR-SEC-AUTHZ-R01 | Every service in the slice is authenticated and authorized server-side; no endpoint is anonymous | Must | Security test (endpoint inventory) |
| NFR-SEC-TENANT-R01 | A tenant user cannot obtain another institution's figures by any parameter, on either endpoint | Must | Security test (enumerate 50 institution codes on query and export) |
| NFR-SEC-HDR-R01 | No user-supplied value reaches a response header unencoded | Must | Security test + code review |
| NFR-OPS-AUDIT-R01 | Report read and export events are retained for the statutory term (OI-02, still open) with tamper-evident integrity | Must | Integration test + review |
| NFR-USE-R01 | The data's as-of date and its batch-derived nature are visible without the user asking (FR-RPT-013) | Should | E2E test |
| NFR-OPS-R01 | Loss of one datasource degrades the report to an explicit partial result; it never fails the whole screen and never silently under-reports (FR-RPTS-005) | Must | Integration test (datasource down) |
| NFR-COMPAT-R01 | The workbook opens without repair in Excel 2016+, LibreOffice Calc and Google Sheets | Should | Manual verification per target |

### 2.6 Constraints

| REQ-ID | Constraint | Priority | Verification |
|--------|-----------|----------|--------------|
| CONST-DATA-R01 | `KKB_APITR_SMTN` is produced by `BATCH_BIZTALK_DAILY` (BR-009). This slice is **read-only**: it never writes, corrects or recomputes an aggregate. Discrepancies are reported, not repaired | Must | Code review (no write path to the aggregate tables) |
| CONST-DATA-R02 | No DDL, inheriting CONST-DATA-01. The cross-source merge (FR-RPTS-003) is a read-time operation — see CONFLICT-R02 | Must | Review |
| CONST-BIZ-R01 | 친구톡 일반이미지 is a **derived** figure (`FTIMG_* − FTIMGWI_*`), not a stored column. The derivation is preserved exactly | Must | Unit test |
| CONST-BIZ-R02 | 총 건수 = `AT_CNT + FT_CNT + MMS_CNT + SMS_CNT + LMS_CNT`. `FT_CNT` is the 친구톡 total and its `FTTXT`/`FTIMG` components are **not** added again | Must | Unit test (no double counting) |
| CONST-LEGAL-R01 | The report contains no personal information — every field is an aggregate count. It is nonetheless **commercially confidential**: per-institution volumes are disclosed only within the scope granted by FR-AZ-R03 | Must | Review + security test |

### 2.7 Excluded from scope

| Item | Reason |
|------|--------|
| `biztalk_admin_20_spreadsheet.js` | Referenced by no view. Dead file (D-R22) |
| The commented-out ajax `fn_downloadExcel` | Superseded by the live form-post implementation (D-R22) |
| `popCallbackFn()`, `testrec2`, `var num`, `#tmSel` handlers | Dead code; the screen has no popup and no `#tmSel` element (D-R22) |
| Duplicate header-cell creation | Removed as part of the export rewrite (D-R21) |
| Repair of `BATCH_BIZTALK_DAILY` | Out of slice — D-R26, D-R27 belong to the batch. Raised as OI-R01 |

---

## 3. Use Cases

| UC-ID | Title | Requirements |
|-------|-------|--------------|
| [UC-RPT-001](use-cases/UC-RPT-001.md) | Query institution usage for a period | FR-AZ-R01…R05, FR-RPT-001…016, FR-RPTS-001…005 |
| [UC-RPT-002](use-cases/UC-RPT-002.md) | Export the usage report to Excel | FR-AZ-R01…R05, FR-RPTX-001…013 |

Every FR, NFR and CONST in this document maps to at least one of the two — orphan count **0**, verified against [requirements-matrix.csv](requirements-matrix.csv).

---

## 4. Traceability

Full matrix: [requirements-matrix.csv](requirements-matrix.csv). Each row carries REQ-ID, type, source artifact, MoSCoW priority, verification method, use case, business rule and `defect_ref`.

Business rules exercised by this slice: **BR-005** (monitored services must audit — the basis for FR-AZ-R05), **BR-006** (tenant isolation — refined by CONFLICT-R01), **BR-008** (field contracts enforced per service — D-R5, D-R9, D-R10), **BR-009** (daily batch aggregation — CONST-DATA-R01), **BR-011** (Excel export — FR-RPTX-001).

---

## 5. Conflicts

### 5.1 CONFLICT-R01 — cross-institution report vs server-derived tenancy *(resolved)*

**Conflict.** AMB-02 was ruled at G1 as "institution scope is server-enforced from the session; a client-supplied `IS_CD` is ignored, not merely validated." Screen 20's purpose is a **cross-institution** report and its selector defaults to 전체. Under AMB-02 as written, the screen cannot exist.

**PM ruling (2026-08-18).** Scope is **role-dependent**. Operators may request 전체 or any institution; tenant users are narrowed from the session exactly as AMB-02 requires. AMB-02 is refined to state that it governs **tenant** principals — which was always its intent, since it was raised about external self-service.

**Effect.** FR-AZ-R03, FR-AZ-R04. AMB-02 is annotated in the question log rather than superseded.

### 5.2 CONFLICT-R02 — cross-source merge vs server-side pagination *(open, blocks Skill 3)*

**Conflict.** Three requirements cannot all hold if `BIZTALK_DB` and `BIZTALK_BULK_DB` are genuinely separate physical databases:

- FR-RPTS-003 — 전체 sums both sources into one row
- FR-RPT-005 — pagination is server-side
- FR-RPT-006 — ordering is total and deterministic across the merged set

A merged, ordered, paginated result cannot be produced by either database alone. The available shapes are: **(A)** fetch both fully and merge in the application — which reinstates the unbounded fetch D-R8 exists to remove; **(B)** a federated query (FDW / linked server) — which pushes the merge into the database but adds an infrastructure dependency; **(C)** a consolidated read store fed by the batch — the clean answer, requiring DDL and so colliding with CONST-DATA-R02.

**Status.** Raised under harness §3 (충돌 식별 → PM 결재 의무). **Gated on AMB-R04**: the two datasource aliases may already resolve to one physical database, in which case the conflict dissolves and the merge is an ordinary `UNION ALL` + `GROUP BY`. This is the same shape as the `BIZ_DB` vs `BIZTALK_DB` unknown carried out of the 발신번호 slice, and it should be settled by the same check.

**Working assumption.** Option A, with the 366-day cap and pagination applied per source before merging — correct for the common case where one institution is selected, and degrading only for 전체 over a wide range. Revisit once AMB-R04 is answered.

> Per the CONFLICT-I02 precedent, this was checked for reality before escalation: the two IDOs declare **different `<target>` values**, which is evidence of two configured datasources but not proof of two physical databases. That distinction is exactly what AMB-R04 asks, and it decides between three materially different designs — so it is escalated rather than assumed.

---

## 6. Risks

| ID | Risk | Impact | Mitigation |
|----|------|--------|------------|
| RISK-R01 | `BIZTALK_BULK_DB` and `BIZTALK_DB` are separate physical databases, forcing an application-side merge | High — CONFLICT-R02 resolves to the weakest option | AMB-R04 settled before Skill 3 task planning |
| RISK-R02 | The T-4 batch lag (D-R26) is not a defect but a deliberate operational choice nobody remembers making | Medium — FR-RPT-013 would display a lag the business considers normal | Confirm with the operations owner (OI-R01) |
| RISK-R03 | Historical bulk aggregates already contain silent zeros from swallowed batch failures (D-R27) | High — the migrated report presents wrong history as fact | Reconcile `KKB_APITR_SMTN` (bulk) against raw send records before cutover, as with D-I1 and D-S1 |
| RISK-R04 | D-R1 + D-R2 may have been exploited already | High — customer volume data disclosure | Review access logs for `biztalk_admin_20_l001` calls with an empty `IS_CD`; treat as a security incident if found |
| RISK-R05 | `AOA_ADMIN` carries the same screens against the same databases (per the 발신번호 slice), so D-R1/D-R2 stay reachable there after we ship | High | Programme-level; carried from RISK-S05 |

---

## 7. Upstream dependency — `BATCH_BIZTALK_DAILY`

The report has one data source and does not own it. Two defects in that batch bound what any requirement here can promise, and both are recorded so the report is not specified as if they did not exist.

**D-R26 — the aggregate is never current.** Invoked with no parameters, the batch aggregates `LocalDate.now().minusDays(4)` — a single day, four days back. Invoked with a range it requires `startDate.isBefore(endDate)` **strictly**, so `START_DT=END_DT` throws `INPUT ERROR` and one day cannot be re-aggregated on its own. The practical result is that today's, yesterday's and the day before's figures do not exist, which is why the screen — whose date fields default to today — opens on an empty report.

**D-R27 — a failed aggregation looks like a quiet day.** Each day is delete-then-insert with no transaction. On `BIZTALK_DB` a failed insert throws and leaves the day deleted. On `BIZTALK_BULK_DB` the entire block sits inside `catch(JexBIZException e){ LOG.error(e); }` — the batch completes successfully while that day's bulk aggregate is missing, and the report renders the absence as zero.

This is the fourth slice in a row to surface a **silent-success** defect — D-I1 (institution disabled, still active), D-S1 (number deleted, still live), and now an aggregation that reports success having deleted data it failed to replace. It is the strongest recurring signal in the programme.

Neither defect is fixed here. FR-RPT-013 makes the lag visible, FR-RPTS-005 makes the gap visible, and **OI-R01** carries the repair to whoever owns the batch.

---

## 8. Open items

Tracked in [questions-log.md](questions-log.md) Part 5, §21.

| ID | Question | Owner | Needed by |
|----|----------|-------|-----------|
| AMB-R04 | Are `BIZTALK_DB` and `BIZTALK_BULK_DB` one physical database or two? Decides CONFLICT-R02 | Architect | Skill 3 |
| AMB-R05 | Export row threshold above which generation becomes asynchronous (FR-RPTX-010) | PM | Skill 3 |
| AMB-R06 | May tenant users see 처리중 counts, or operators only? In-flight figures expose queue state | PM | Skill 3 |
| AMB-R07 | Where is the batch's completion watermark recorded, so FR-RPT-013 can read it? No such record exists in the batch today | Architect | Skill 3 |
| AMB-R08 | Must the merged 전체 view retain a per-source breakdown for billing reconciliation (BR-012 수수료)? | Domain owner | Skill 3 |
| OI-R01 | Ownership and repair of D-R26 / D-R27 in `BATCH_BIZTALK_DAILY` | PM | Before cutover |

Carried and still open: **OI-02** (audit retention term) blocks NFR-OPS-AUDIT-R01.

---

## 9. Definition of Done

- [x] Every requirement carries a REQ-ID — orphan count 0
- [x] Every requirement carries a verification method
- [x] MoSCoW priority assigned throughout
- [x] All 27 defects dispositioned (25 fixed, 2 raised out of slice)
- [x] Four PM rulings recorded (§1.4)
- [x] CONFLICT-R01 resolved; CONFLICT-R02 raised with candidates and a working assumption
- [x] **G1 analysis gate — PM approval** — 2026-08-21. CONFLICT-R02 는 해소되지 않은 채 이월된다: 두 datasource alias 가 동일 물리 DB 인지(AMB-R04) 확인되면 충돌이 소멸하고 평범한 `UNION ALL` + `GROUP BY` 가 된다. 발신번호 슬라이스의 `BIZ_DB` vs `BIZTALK_DB` 미확인 사항과 같은 점검으로 함께 닫는다 / Approved 2026-08-21 with CONFLICT-R02 **carried unresolved**, to be settled by the same AMB-R04 datasource check as the 발신번호 slice
