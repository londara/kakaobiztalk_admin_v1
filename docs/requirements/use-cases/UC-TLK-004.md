# Use Case UC-TLK-004: Download the transaction history

> **REQ-ID**: UC-TLK-004
> **Version**: 1.0
> **Predecessor**: [REQUIREMENTS-SPEC-TALK.md](../REQUIREMENTS-SPEC-TALK.md)
> **Legacy origin**: `biztalk_admin_30_spreadsheet_view.jsp` / `…_spreadsheet_act.jsp` / `WSVC.biztalk_admin_30_spreadsheet` / `IDO.KKB_MSG_L001`, invoked by `fn_makeExcel()` in `biztalk_admin_30.js`

---

## 1. Scenario overview

| Item | Content |
|------|---------|
| **Primary user** | Internal operator |
| **Precondition** | UC-TLK-001 has produced a result the operator wants offline |
| **Trigger** | User presses 다운로드 |
| **Success outcome** | A workbook containing **exactly the grid's result set** for the current filters, masked, is delivered and the export is audited with its row count |
| **Failure outcome** | An over-cap range or over-cap row count is rejected; any failure is visible to the user |
| **Related FR** | FR-AZ-T02, FR-AZ-T05, FR-TLKX-001…010 |
| **Related NFR** | NFR-SEC-PII-T01, NFR-SEC-HDR-T01, NFR-SCALE-T01, NFR-OPS-AUDIT-T01, NFR-COMPAT-T01 |
| **Related CONST** | CONST-DATA-T01, CONST-SEC-T01, CONST-LEGAL-T01 |
| **Related BR** | BR-005, BR-006, BR-007, BR-011 |

## 2. Main flow

| Step | Actor | Action | System response |
|------|-------|--------|-----------------|
| 1 | User | Presses 다운로드 | Session and operator role verified server-side (FR-AZ-T02) |
| 2 | System | Reads the request | Every parameter through the **declared contract**, with its declared length and character rules (FR-TLKX-002) |
| 3 | System | Validates | The same period cap and date rules as the list, plus an explicit row cap (FR-TLKX-005) |
| 4 | System | Resolves scope | The same institution scope and the same BizTalk API restriction as the list (FR-TLKX-001) |
| 5 | System | Executes the query | **The list's own query** with the current filters — same tables, same grain, same columns (FR-TLKX-001) |
| 6 | System | Builds the workbook | Streamed, with heap bounded independently of row count (FR-TLKX-005, NFR-SCALE-T01); each header cell written once and the title merge spanning the table (FR-TLKX-009) |
| 7 | System | Masks | 수신번호 and 발신번호 masked in the file exactly as on screen (FR-TLKX-008) |
| 8 | System | Sets the filename | Encoded; CR/LF rejected outright (FR-TLKX-003, NFR-SEC-HDR-T01) |
| 9 | System | Sets the content type | One content type, correct for the format produced (FR-TLKX-004) |
| 10 | System | Delivers the file | Delivery and any failure are visible to the user (FR-TLKX-006) |
| 11 | System | Writes an audit event | Actor, timestamp, filters, scope, **row count actually written** (FR-TLKX-007, FR-AZ-T05) |

## 3. Alternative flows

### 3.1 A-1: Export with no filters beyond the required period
- At Step 4. The full permitted scope for that period is exported, still within the period and row caps (FR-TLKX-005).

### 3.2 A-2: Row count exceeds the cap
- At Step 3. The request is rejected with a message naming the cap, rather than being served partially and silently.

## 4. Exception flows

### 4.1 E-1: The exported file does not match the screen
- The legacy's normal behaviour, not an edge case.
- Action: the export runs the list's own query with the same filters (FR-TLKX-001). **Regression guard:** the button ran `IDO.KKB_MSG_L001` over `KKO_MSG`/`KKF_MSG` — a different table set at a different grain — and `fn_makeExcel()` read filters from `#IS_LIST`, `#MSGKEY`, `#PHONE`, `#CALLBACK`, `#RSLT`, `#STATUS`, `#MSG_TYPE`, none of which exist on the screen. All resolved to `''`, every `CASE WHEN :X = ''` branch opened, and the file contained every institution's messages in the window with `decrypt(CALLBACK)`/`decrypt(PHONE)` unmasked (D-T1). The **acceptance test asserts the exported row set equals the paged list row set, filter for filter.**

### 4.2 E-2: A filter value contains CR/LF
- At Step 8, by crafting a request.
- Action: rejected; no user value reaches a header unencoded (FR-TLKX-003, NFR-SEC-HDR-T01). **Regression guard:** `startDt`/`endDt` reached `FILE_NM` unvalidated and `FILE_NM` went into `Content-Disposition`; the non-IE branch only recoded the bytes (D-T4).

### 4.3 E-3: A parameter violates its declared length or character rule
- At Step 2.
- Action: rejected by the contract (FR-TLKX-002). **Regression guard:** all ten parameters were read via `request.getParameter`, bypassing the contract that declared them — the same gap that made E-2 reachable (D-T14).

### 4.4 E-4: The export fails mid-generation
- At Step 10.
- Action: a visible error (FR-TLKX-006). **Regression guard:** the form targeted `ifrmFileProc`, a browsing context no view in the slice declares and `fintech.common.submit` does not create, so the response landed in a new top-level window — and `frm0` kept the target for the next popup submit (D-T23).

### 4.5 E-5: A very large result set
- At Step 6.
- Action: the row cap applies and heap stays flat (FR-TLKX-005, NFR-SCALE-T01). **Regression guard:** no pagination, no row cap, no period cap, and `XSSFWorkbook` fully materialised over a four-way `UNION ALL` calling `decrypt()` twice per row (D-T12).

### 4.6 E-6: The file is opened in a spreadsheet application
- After Step 10.
- Action: opens without a repair prompt (NFR-COMPAT-T01, FR-TLKX-004). **Regression guard:** the content type was declared four times inconsistently, none of them correct for `.xlsx` (D-T34).

## 5. Postconditions

- No row is written to any business table (CONST-DATA-T01).
- An audit record exists carrying the row count actually written (FR-TLKX-007).
- The delivered file contains no unmasked number (FR-TLKX-008) and no account, card, amount or telegram column (CONST-SEC-T01).

## 6. Regression guards summary

| Defect | Guard |
|--------|-------|
| D-T1 | E-1 — exported row set equals the paged list row set |
| D-T4 | E-2 — CR/LF in a filter value is rejected |
| D-T12 | E-5 — row cap enforced, heap flat at 1k vs 100k rows |
| D-T14 | E-3 — contract-declared parameters only |
| D-T16 | Step 2 — the export declares its own service contract |
| D-T23 | E-4 — failure is visible |
| D-T32 | E-1 — no filter is silently unset by an id typo |
| D-T34 | E-6 — one correct content type; header cells written once |
