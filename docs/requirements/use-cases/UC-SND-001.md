# Use Case UC-SND-001: Select an institution and review its sender numbers

> **REQ-ID**: UC-SND-001
> **Version**: 1.0
> **Predecessor**: [REQUIREMENTS-SPEC-SENDERNO.md](../REQUIREMENTS-SPEC-SENDERNO.md)
> **Legacy origin**: screen 10 — `biztalk_admin_10_view.jsp` / `biztalk_admin_10.js` / `WSVC.biztalk_admin_10_l001` / `IDO.KKB_DPNO_LDGR_L002`

---

## 1. Scenario overview

| Item | Content |
|------|---------|
| **Primary user** | Internal operator |
| **Precondition** | Authenticated session with an operator role (inherited from the 로그인 slice) |
| **Trigger** | Operator opens 이용기관 정보 관리 |
| **Success outcome** | The selected institution's sender numbers are listed in full, in a stable order, and the read is audited |
| **Failure outcome** | Unauthorized access rejected, or no institution selected |
| **Related FR** | FR-SND-001…011, FR-AZ-D01…D05 |
| **Related BR** | BR-002, BR-007 |

## 2. Main flow

| Step | Actor | Action | System response |
|------|-------|--------|-----------------|
| 1 | Operator | Opens the screen | Session and operator role verified server-side (FR-AZ-D01/D02) |
| 2 | System | Loads the institution selector | Only institutions the operator is entitled to see; 사용여부 indicated (FR-SND-010) |
| 3 | System | — | **No query is issued yet.** The list stays empty until an institution is chosen (FR-SND-002) |
| 4 | Operator | Selects an institution | — |
| 5 | System | Validates the requested scope | The institution is checked against the session's permissions; the body's `IS_CD` is not trusted alone (FR-AZ-D03) |
| 6 | System | Returns the page slice | Server-side pagination with a total count (FR-SND-003), ordered 등록일시 descending (FR-SND-004) |
| 7 | System | Renders the grid | 기관명, 발신번호, 등록자, 등록일자, 수정자, 수정일자, 설명 (FR-SND-005) |
| 8 | System | Displays 발신번호 in full | Not masked (FR-SND-006); 등록자/수정자 names are masked (FR-SND-008) |
| 9 | System | Writes an audit event | Actor, timestamp, institution, rows read (FR-SND-011, NFR-OPS-AUDIT-D01) |
| 10 | Operator | Pages or re-queries | Order is identical across requests (FR-SND-004) |

## 3. Alternative flows

### 3.1 A-1: Operator changes the selected institution
- Branches at Step 4. Pagination resets to page 1 and Steps 5–9 repeat for the new scope.

### 3.2 A-2: Institution has no registered numbers
- At Step 6. An empty result is presented as "등록된 발신번호가 없습니다", distinct from an error.

## 4. Exception flows

### 4.1 E-1: Non-operator calls the list service
- At Step 1 or by crafting a request.
- Action: reject with 403 (FR-AZ-D01/D02). **Regression guard:** the legacy service carried `<login>Y</login>` only, and the sole role check was a browser-side call to `biztalk_admin_00_l003` followed by `alert('권한 없음')` — which guarded the register button and nothing else (D-S2).

### 4.2 E-2: Request for another institution's numbers
- At Step 5. A crafted request supplies an `IS_CD` the operator has no rights to.
- Action: reject with 403 (FR-AZ-D03). **Regression guard:** the legacy passed `IS_CD` straight from the request body into `KKB_DPNO_LDGR_L002`, so any authenticated user could read any institution's sender numbers (D-S3).

### 4.3 E-3: Page load before an institution is chosen
- At Step 3.
- Action: no query issued. **Regression guard:** the legacy ran `getDat()` in `onload` before `fn_getIsList()` had populated the combo, issuing a query with an empty `IS_CD` on every page load (D-S19).

### 4.4 E-4: Repeated paging returns duplicated or missing rows
- At Step 10.
- Action: prevented by the deterministic sort. **Regression guard:** `KKB_DPNO_LDGR_L002` had no `ORDER BY` and no `LIMIT`/`OFFSET`; the client sent `PAGE_NO` and `INQ_TOTL_NCNT` that the contract never declared, so the whole list was fetched in nondeterministic order and paged in the browser (D-S14).

## 5. NFR mapping

| NFR-ID | Application |
|--------|-------------|
| NFR-PERF-D01 | Step 6: P95 < 1 s at 100 rows |
| NFR-SEC-AUTHZ-D01 | Step 1: operator role enforced server-side |
| NFR-SEC-TENANT-D01 | Step 5: institution scope validated server-side |
| NFR-SEC-PII-D02 | Step 7: only displayed fields returned — `RGSR_ID`/`UDT_ID` are not shipped to the browser (D-S21) |
| NFR-SEC-LOG-D01 | Step 9: 발신번호 not written to application logs in clear |
| NFR-OPS-AUDIT-D01 | Step 9: read event audited |

## 6. Screen sketch

```
┌─ 이용기관 정보 관리 ────────────────────────────────────────┐
│ 이용기관 [▼ 선택                    ]            [ 조회 ]   │
├─────────────────────────────────────────────────────────────┤
│ 발신번호                                  [ 등록 ] [ 삭제 ] │
│ ☐ 기관명 │ 발신번호    │ 등록자 │ 등록일자 │ 수정자 │ 설명  │
│ ☐ ○○기관 │ 0212345678  │ 김*수  │ 25-10-17 │ 김*수  │ …    │
│ ☐ ○○기관 │ 15881234    │ 이*    │ 25-10-18 │ –      │ …    │
│                          ◀ 1 2 3 ▶   총 27건               │
└─────────────────────────────────────────────────────────────┘
```

## 7. Test scenarios

| Test ID | Case | Input | Expected |
|---------|------|-------|----------|
| TC-S001-01 | Normal list | Institution with 27 numbers | Page 1 returned with total count 27 |
| TC-S001-02 | **D-S2 regression** | List service called by a non-operator | 403; no data returned |
| TC-S001-03 | **D-S3 regression** | Authenticated user requests another institution's `IS_CD` | 403; no data returned |
| TC-S001-04 | **D-S3 regression** | Enumerate 50 institution codes against the list service | No sender numbers recovered from any response |
| TC-S001-05 | **D-S14 regression** | Request page 2 twice | Identical rows both times |
| TC-S001-06 | **D-S14 regression** | Institution with 500 numbers, page size 100 | Response carries 100 rows, not 500 |
| TC-S001-07 | **D-S19 regression** | Open the screen, select nothing | No list query issued |
| TC-S001-08 | **D-S17 regression** | Inspect the list response shape | No empty `ISNM` field; every column bound by name, not position |
| TC-S001-09 | **D-S21 regression** | Inspect the list response shape | `RGSR_ID` and `UDT_ID` absent |
| TC-S001-10 | Full display | Any row | 발신번호 shown in full, matching the detail view exactly (FR-SND-006) |
| TC-S001-11 | Name masking | Any row | 등록자/수정자 masked per CONST-LEGAL-01 |
| TC-S001-12 | Read audit | Any list query | Audit record with actor, timestamp, institution |
| TC-S001-13 | Empty institution | Institution with no numbers | Empty-state message, not an error |
| TC-S001-14 | Row identity | Select a row for deletion | The identifier sent is server-resolvable, not the displayed string (FR-SND-007) |
