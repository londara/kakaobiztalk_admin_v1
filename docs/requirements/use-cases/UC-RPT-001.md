# Use Case UC-RPT-001: Query institution usage for a period

> **REQ-ID**: UC-RPT-001
> **Version**: 1.0
> **Predecessor**: [REQUIREMENTS-SPEC-REPORT.md](../REQUIREMENTS-SPEC-REPORT.md)
> **Legacy origin**: screen 20 — `biztalk_admin_20_view.jsp` / `biztalk_admin_20.js` / `WSVC.biztalk_admin_20_l001` / `IDO.KKB_APITR_SMTN_L001` + `IDO.BULK_KKB_APITR_SMTN_L001`

---

## 1. Scenario overview

| Item | Content |
|------|---------|
| **Primary user** | Internal operator (cross-institution) or tenant user (own institution only) |
| **Precondition** | Authenticated session with a role that grants report access (inherited from the 로그인 slice) |
| **Trigger** | User opens 이용기관 보고서 |
| **Success outcome** | Daily aggregate counts for the permitted scope are returned in a stable order, with the data's as-of date shown, and the read is audited |
| **Failure outcome** | Unauthenticated or unauthorized request rejected; invalid or over-long period rejected |
| **Related FR** | FR-AZ-R01, FR-AZ-R02, FR-AZ-R03, FR-AZ-R04, FR-AZ-R05, FR-RPT-001…016, FR-RPTS-001…005 |
| **Related CONST** | CONST-DATA-R01, CONST-DATA-R02, CONST-BIZ-R01, CONST-LEGAL-R01 |
| **Related BR** | BR-005, BR-006, BR-008, BR-009 |

## 2. Main flow

| Step | Actor | Action | System response |
|------|-------|--------|-----------------|
| 1 | User | Opens the screen | Session and role verified **server-side** (FR-AZ-R01, FR-AZ-R02) |
| 2 | System | Loads the 이용기관 selector | Only institutions the caller may see; 전체 offered only to operator roles (FR-AZ-R04) |
| 3 | System | — | **No query is issued yet** — not until the selector has loaded and the caller's scope is known (FR-RPT-015) |
| 4 | System | Displays the aggregation as-of date | The latest 일자 for which the batch has completed, per source (FR-RPT-013) |
| 5 | User | Selects 이용기관, 발송구분 and a 요청일자 range | 발송구분 defaults to 전체 (FR-RPTS-002) |
| 6 | User | Presses 조회 | — |
| 7 | System | Validates the request | Dates are 8-digit calendar dates (FR-RPT-004), 시작 ≤ 종료 (FR-RPT-003), span ≤ 366 days (FR-RPT-002) — all server-side |
| 8 | System | Resolves scope | Operator: the requested institution or 전체. Tenant: the session's institution; a body `IS_CD` is ignored (FR-AZ-R03) |
| 9 | System | Reads both sources | API (`BIZTALK_DB`) and 대량 (`BIZTALK_BULK_DB`) in **every** environment (FR-RPTS-001, FR-RPTS-004) |
| 10 | System | Merges | With 발송구분=전체, same 일자 + 기관 counters are **summed into one row** (FR-RPTS-003) |
| 11 | System | Returns the page slice | Server-side pagination with a total count (FR-RPT-005), ordered 일자 desc → 발송구분 → 기관코드 (FR-RPT-006) |
| 12 | System | Renders the grid | 구분, 기관명, 일자, and 전체/성공/실패/**처리중** per channel (FR-RPT-009) |
| 13 | System | Presents figures | NULL counters render 0 (FR-RPT-011); unresolved 기관명 shows the code plus a marker (FR-RPT-012) |
| 14 | System | Writes an audit event | Actor, timestamp, scope, range, 발송구분, row count (FR-AZ-R05) |
| 15 | User | Pages or re-queries | Order is identical across requests and across environments (FR-RPT-006, FR-RPT-007) |

## 3. Alternative flows

### 3.1 A-1: 발송구분 narrowed to API or 대량
- Branches at Step 5. Only that source is read at Step 9 and no merge occurs at Step 10 (FR-RPTS-003).

### 3.2 A-2: Operator selects 전체 institutions
- At Step 8. Permitted for operator roles only; the same period cap and pagination apply unchanged.

### 3.3 A-3: Tenant user opens the screen
- At Step 2. The selector shows the session's institution alone and 전체 is not offered (FR-AZ-R04).

### 3.4 A-4: Range extends past the aggregation as-of date
- At Step 11. Days beyond the watermark are marked **not yet aggregated**, distinct from zero (FR-RPT-013).

## 4. Exception flows

### 4.1 E-1: Unauthenticated call to the query service
- At Step 1, or by crafting a request directly.
- Action: reject with 401 (FR-AZ-R01, FR-AZ-R02). **Regression guard:** `WSVC.biztalk_admin_20_l001` declared `<login>N</login>` while the screen and export around it declared `Y` — the one service that returns the figures was the one requiring no session (D-R1).

### 4.2 E-2: Request for institutions the caller may not see
- At Step 8. A tenant user supplies another institution's `IS_CD`, or omits it to mean 전체.
- Action: ignore the supplied value and narrow to the session's scope (FR-AZ-R03). **Regression guard:** `IS_CD` went verbatim into the query and an empty value meant *all institutions*, so omitting one parameter dumped every customer's volumes (D-R2).

### 4.3 E-3: Period exceeds the cap, or dates are invalid
- At Step 7. A direct call with `START_DT=00000000&END_DT=99999999`, an inverted range, or a non-date string.
- Action: reject with 400 (FR-RPT-002/003/004). **Regression guard:** the only check was `Number(startDt) > Number(endDt)` in the browser; the contract declared neither length nor type and the action validated nothing (D-R9).

### 4.4 E-4: Result set is large
- At Step 11.
- Action: one page plus a total count is returned. **Regression guard:** `gridPaging` was wired in the JS while `PAGE_NO`/`INQ_TOTL_NCNT` were commented out, neither contract declared them, and neither IDO had `LIMIT`/`OFFSET` — the whole set was fetched and paged in the browser (D-R8, fourth occurrence of this class).

### 4.5 E-5: One datasource is unavailable
- At Step 9.
- Action: return the available source's figures and **state which source is incomplete** (FR-RPTS-005, NFR-OPS-R01). Never present a partial total as complete.

### 4.6 E-6: `TRDD` is null or non-numeric on an aggregate row
- At Step 11.
- Action: ordering tolerates it and the row is surfaced as a data-quality error. **Regression guard:** the production sort called `Integer.parseInt(b.getString("TRDD"))`, which throws on a null or blank value and fails the entire report (D-R7).

### 4.7 E-7: Empty result within the aggregated window
- At Step 12.
- Action: "조회된 내용이 없습니다", distinct from an error and from *not yet aggregated* (FR-RPT-014).

## 5. NFR mapping

| NFR-ID | Application |
|--------|-------------|
| NFR-PERF-R01 | Step 11: P95 < 3 s at 31 days, all institutions, 100 rows/page |
| NFR-PERF-R02 | Step 11: P95 < 5 s per page at the 366-day cap |
| NFR-SEC-AUTHZ-R01 | Step 1: no anonymous endpoint in the slice |
| NFR-SEC-TENANT-R01 | Step 8: scope not obtainable by any parameter |
| NFR-OPS-AUDIT-R01 | Step 14: read event retained per OI-02 |
| NFR-OPS-R01 | Step 9: single-source loss degrades explicitly, never silently |
| NFR-USE-R01 | Step 4: as-of date visible without asking |
| CONST-DATA-R01 | Steps 9–11: the slice reads the aggregate and never writes or recomputes it |
| CONST-DATA-R02 | Step 10: the merge is a read-time operation, no DDL (CONFLICT-R02) |
| CONST-BIZ-R01 | Step 12: 친구톡 일반이미지 derived as 전체이미지 − 와이드 |
| CONST-LEGAL-R01 | Steps 8, 12: aggregate-only data, disclosed only within the FR-AZ-R03 scope |

## 6. Screen sketch

```
┌─ 이용기관 보고서 ───────────────────────────────────────────────┐
│ 이용기관 [▼ 전체        ]  발송구분 [▼ 전체 ]                   │
│ 요청일자 [2026-07-01] ~ [2026-07-31]                  [ 조회 ]  │
├─────────────────────────────────────────────────────────────────┤
│ 집계 기준일: API 2026-08-14 · 대량 2026-08-14   (T-4)           │
│                                                     [ 다운로드 ] │
│ 구분   │ 기관명  │ 일자     │ 알림톡 전체/성공/실패/처리중 │ … │
│ 전체   │ ○○기관 │ 26-07-31 │  1,204 / 1,180 /  20 /   4   │ … │
│ 전체   │ △△기관 │ 26-07-31 │    311 /   309 /   2 /   0   │ … │
│                              ◀ 1 2 3 ▶   총 62건               │
└─────────────────────────────────────────────────────────────────┘
```

## 7. Test scenarios

| Test ID | Case | Input | Expected |
|---------|------|-------|----------|
| TC-R001-01 | Normal query | One institution, 31 days | Page 1 returned with a total count |
| TC-R001-02 | **D-R1 regression** | Query service called with no session | 401; no data returned |
| TC-R001-03 | **D-R2 regression** | Authenticated tenant user omits `IS_CD` | Scope narrowed to their own institution — not 전체 |
| TC-R001-04 | **D-R2 regression** | Tenant user enumerates 50 institution codes | No other institution's figures recovered from any response |
| TC-R001-05 | **D-R9 regression** | `START_DT=00000000`, `END_DT=99999999` | 400; no query executed |
| TC-R001-06 | **D-R9 regression** | 367-day range / inverted range / `20261332` | 400 in each case |
| TC-R001-07 | **D-R8 regression** | 366 days × all institutions | One page returned, not the full set |
| TC-R001-08 | **D-R7 regression** | Same query run twice in each environment | Identical row order every time |
| TC-R001-09 | **D-R7 regression** | Aggregate row with a blank `TRDD` | Report returns; row flagged, no exception |
| TC-R001-10 | **D-R6 regression** | Same query in dev, staging and production | Identical response shape and field types |
| TC-R001-11 | **D-R5 regression** | Inspect the response | Structured record list; no field requires client-side parsing (FR-RPT-008) |
| TC-R001-12 | **D-R5 regression** | 기관명 containing `"` and `\` | Rendered correctly, no parse failure |
| TC-R001-13 | **D-R14 regression** | Any row with in-flight messages | 전체 = 성공 + 실패 + 처리중 holds (FR-RPT-010) |
| TC-R001-14 | **D-R11 regression** | Row with NULL `FTIMGWI_CNT` | Cell shows 0; row total is correct, not blank |
| TC-R001-15 | **D-R12 regression** | `IS_CD` absent from `FT_FTIS_INFO` | Code plus unresolved marker, not a blank cell |
| TC-R001-16 | **D-R25 regression** | Open the screen with default dates | As-of date shown; today marked *not yet aggregated*, not "no data" |
| TC-R001-17 | **D-R23 regression** | Open the screen, select nothing | No query issued before the selector loads |
| TC-R001-18 | **D-R24 regression** | Inspect the response shape | `RGDT` and `FT_CNT` absent (FR-RPT-016) |
| TC-R001-19 | Merge | Day present in both sources, 발송구분=전체 | One summed row, not two |
| TC-R001-20 | Merge filter | Same day, 발송구분=API | API figures only |
| TC-R001-21 | **D-R4 regression** | Full flow in a non-production environment | Bulk source read; behaviour identical to production |
| TC-R001-22 | Partial source | Bulk datasource down | Result returned with an explicit incomplete-source notice |
| TC-R001-23 | Read audit | Any query | Audit record with actor, scope, range, row count |
| TC-R001-24 | Derived figure | Seeded `FTIMG`/`FTIMGWI` | 일반이미지 = 전체이미지 − 와이드 exactly |
| TC-R001-25 | No double counting | Seeded row | 총 건수 uses `FT_CNT` once; `FTTXT`/`FTIMG` not re-added (CONST-BIZ-R02) |
