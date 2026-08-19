# Use Case UC-RPT-002: Export the usage report to Excel

> **REQ-ID**: UC-RPT-002
> **Version**: 1.0
> **Predecessor**: [REQUIREMENTS-SPEC-REPORT.md](../REQUIREMENTS-SPEC-REPORT.md)
> **Legacy origin**: screen 20 다운로드 — `biztalk_admin_20.js:fn_downloadExcel()` / `WSVC.biztalk_admin_20_spreadsheet` / `biztalk_admin_20_spreadsheet_act.jsp` / `biztalk_admin_20_spreadsheet_view.jsp` / `IDO.KKB_APITR_SMTN_L001`+`_L002` and their `BULK_` counterparts

---

## 1. Scenario overview

| Item | Content |
|------|---------|
| **Primary user** | Internal operator (cross-institution) or tenant user (own institution only) |
| **Precondition** | Authenticated session with a role that grants report access; a query has been performed (UC-RPT-001) |
| **Trigger** | User presses 다운로드 |
| **Success outcome** | An `.xlsx` workbook of the current query is delivered, matching the on-screen figures, and the export is audited |
| **Failure outcome** | Unauthorized or invalid request rejected; a generation failure is shown to the user rather than swallowed |
| **Related FR** | FR-AZ-R01, FR-AZ-R02, FR-AZ-R03, FR-AZ-R04, FR-AZ-R05, FR-RPTX-001…013, FR-RPTS-001…005 |
| **Related CONST** | CONST-DATA-R01, CONST-BIZ-R01, CONST-BIZ-R02, CONST-LEGAL-R01 |
| **Related BR** | BR-005, BR-006, BR-008, BR-011 |

## 2. Main flow

| Step | Actor | Action | System response |
|------|-------|--------|-----------------|
| 1 | User | Presses 다운로드 | The current 이용기관, 발송구분 and 요청일자 range are submitted (FR-RPTX-001) |
| 2 | System | Verifies session and role | Same rules as the query — the export is a second entry point, not a trusted one (FR-RPTX-002, FR-AZ-R01, FR-AZ-R02) |
| 3 | System | Resolves scope | Operator: requested institution or 전체. Tenant: session's institution; a supplied `IS_CD` is ignored (FR-AZ-R03) |
| 4 | System | Validates inputs | Every input is read from the **declared contract's** validated input domain — never the raw request (FR-RPTX-003); dates and the 366-day cap re-checked (FR-RPTX-002) |
| 5 | System | Decides synchronous or asynchronous | Below the row threshold, generate inline; above it, queue and notify (FR-RPTX-010) |
| 6 | System | Reads both sources | API and 대량, in every environment, merged per 발송구분 (FR-RPTS-001, FR-RPTS-003, FR-RPTS-004) |
| 7 | System | Builds the workbook | **총합** sheet + **일자별 상세** sheet, both carrying 전체/성공/실패/처리중 for every channel (FR-RPTX-007) |
| 8 | System | Streams rows | Bounded memory regardless of row count (FR-RPTX-009, NFR-SCALE-R01) |
| 9 | System | Sets the response | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, set once (FR-RPTX-005) |
| 10 | System | Sets the filename | Generated server-side from validated values, RFC 6266 / 5987 encoded, consistent with the workbook title (FR-RPTX-004, FR-RPTX-006) |
| 11 | System | Writes an audit event | Actor, timestamp, scope, range, 발송구분 and the row count actually written (FR-RPTX-012, FR-AZ-R05) |
| 12 | User | Opens the file | Figures equal those on screen (FR-RPTX-001); opens without repair in the supported applications (NFR-COMPAT-R01) |

## 3. Alternative flows

### 3.1 A-1: Export exceeds the synchronous threshold
- Branches at Step 5. The workbook is generated asynchronously and the user is notified when it is ready (FR-RPTX-010). Threshold value is AMB-R05.

### 3.2 A-2: 발송구분 narrowed to API or 대량
- At Step 6. Only that source is read; the sheet set is unchanged.

### 3.3 A-3: Tenant user exports
- At Step 3. The workbook contains that institution's rows only.

## 4. Exception flows

### 4.1 E-1: Export called directly, unauthenticated or unauthorized
- At Step 2. A crafted request to the export endpoint.
- Action: reject with 401/403 (FR-RPTX-002). **Regression guard:** the export declared `<login>Y</login>` but no authorization of any kind, and the query beside it declared `<login>N</login>` (D-R1, D-R2).

### 4.2 E-2: CRLF payload in a date parameter
- At Step 10. `START_DT` carries `%0d%0aSet-Cookie:…`.
- Action: rejected at Step 4 by date validation, and unable to reach a header in any case (FR-RPTX-004, NFR-SEC-HDR-R01). **Regression guard:** `START_DT`/`END_DT` were read raw via `request.getParameter`, concatenated into the filename, and written to `Content-Disposition` with only a charset recode — the non-IE branch performed no encoding at all (D-R3).

### 4.3 E-3: Undeclared parameter used by the export
- At Step 4.
- Action: every input is contract-declared and validated (FR-RPTX-003). **Regression guard:** `WSVC.biztalk_admin_20_spreadsheet` declared only `START_DT` and `END_DT`; `IS_CD` was undeclared, so the action bypassed the input domain entirely and read all three from the raw request — which is what made D-R3 reachable (D-R10).

### 4.4 E-4: Export requested for a range beyond the cap
- At Step 4.
- Action: reject with 400 (FR-RPTX-002). **Regression guard:** the export inherited none of the query's (already absent) limits and would materialise the entire range in memory (D-R9 + D-R15).

### 4.5 E-5: Generation fails
- At Step 7 or 8.
- Action: the user is shown an explicit error (FR-RPTX-011). **Regression guard:** the download posted to a hidden iframe `ifrmFileProc`, so a `JexWebBIZException` rendered its error page invisibly and the user received no file and no message (D-R16).

### 4.6 E-6: Very large export
- At Step 8.
- Action: memory stays bounded (NFR-SCALE-R01) and the async path takes over above the threshold. **Regression guard:** `XSSFWorkbook` materialised up to four sheets fully before writing a byte, over an uncapped range (D-R15).

### 4.7 E-7: Export run in a non-production environment
- At Step 6/7.
- Action: identical sheet set and identical figures (FR-RPTX-008, FR-RPTS-004). **Regression guard:** the action populated `REC2`/`REC3` only when `TSTCL_DV=REAL`, while the view guarded those sheets with `if(true)` — off production it iterated record sets that were never created (D-R4).

## 5. NFR mapping

| NFR-ID | Application |
|--------|-------------|
| NFR-PERF-R03 | Step 5: 92-day all-institution export completes < 60 s synchronously |
| NFR-SCALE-R01 | Step 8: heap bounded and independent of row count |
| NFR-SEC-AUTHZ-R01 | Step 2: export is not an unprotected second door |
| NFR-SEC-TENANT-R01 | Step 3: scope not obtainable by any parameter |
| NFR-SEC-HDR-R01 | Step 10: no user value reaches a header unencoded |
| NFR-OPS-AUDIT-R01 | Step 11: export event retained per OI-02 |
| NFR-COMPAT-R01 | Step 12: opens without repair in Excel 2016+, LibreOffice, Google Sheets |
| CONST-DATA-R01 | Step 6: the export reads the aggregate and never writes or recomputes it |
| CONST-BIZ-R01 | Step 7: 친구톡 일반이미지 derived as 전체이미지 − 와이드 |
| CONST-BIZ-R02 | Step 7: 총 건수 uses `FT_CNT` once, no double counting |
| CONST-LEGAL-R01 | Steps 3, 12: aggregate-only data, scoped by FR-AZ-R03 |

## 6. Workbook structure

```
비즈톡_이용기관보고서_20260701-20260731.xlsx
├─ 총합            per 이용기관 over the whole period
│    기관명 · 기관코드 · 총건수 · [채널별] 전체 / 성공 / 실패 / 처리중
└─ 일자별 상세     per 일자 × 이용기관
     일자 · 구분 · 기관명 · 기관코드 · [채널별] 전체 / 성공 / 실패 / 처리중

채널: 알림톡 · 친구톡(txt) · 친구톡(img-일반) · 친구톡(img-와이드) · SMS · LMS · MMS
```

Two sheets, not four: the legacy's separate BULK sheets are folded into the 구분 column by FR-RPTS-003.

## 7. Test scenarios

| Test ID | Case | Input | Expected |
|---------|------|-------|----------|
| TC-R002-01 | Normal export | One institution, 31 days | Workbook delivered; figures equal the on-screen result |
| TC-R002-02 | **D-R1/D-R2 regression** | Export called with no session | 401; no workbook produced |
| TC-R002-03 | **D-R2 regression** | Tenant user exports with another institution's `IS_CD` | Scope narrowed to their own; no foreign rows in the file |
| TC-R002-04 | **D-R3 regression** | `START_DT=2026%0d%0aSet-Cookie:x=y` | 400; no injected header present in the response |
| TC-R002-05 | **D-R3 regression** | Multi-byte and quote characters in the filename source | Header correctly encoded; file name intact on download |
| TC-R002-06 | **D-R10 regression** | Inspect the export contract | `IS_CD` and 발송구분 declared; action reads no raw request parameter |
| TC-R002-07 | **D-R9 regression** | Export a 400-day range | 400; no generation attempted |
| TC-R002-08 | **D-R15 regression** | Export at the 366-day cap, all institutions | Heap stays within ceiling; no OOM |
| TC-R002-09 | **D-R15 regression** | Heap profile at 1k vs 100k rows | Flat — independent of row count |
| TC-R002-10 | **D-R16 regression** | Force a generation failure | Visible error message; not a silent empty response |
| TC-R002-11 | **D-R4 regression** | Export in a non-production environment | Same sheets, same columns, same figures as production |
| TC-R002-12 | **D-R14 regression** | Inspect the 총합 sheet | 실패 and 처리중 columns present, not only 전체/성공 |
| TC-R002-13 | **D-R19 regression** | Inspect response headers | Exactly one content type, and it is the xlsx type |
| TC-R002-14 | **D-R20 regression** | Compare filename and workbook title | Same report name and same range in both |
| TC-R002-15 | **D-R18 regression** | Inspect sheet geometry | Title merge width equals the header column count on every sheet (FR-RPTX-013) |
| TC-R002-16 | Async threshold | Export above the configured row count | Queued; user notified; file retrievable |
| TC-R002-17 | Export audit | Any export | Audit record with actor, scope, range, rows written |
| TC-R002-18 | Merge parity | 발송구분=전체 | Workbook rows equal UC-RPT-001's merged rows exactly |
| TC-R002-19 | Compatibility | Generated file | Opens without repair in Excel 2016+, LibreOffice, Google Sheets |
