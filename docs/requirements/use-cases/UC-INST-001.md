# Use Case UC-INST-001: Search and review the 이용기관 registry

> **REQ-ID**: UC-INST-001
> **Version**: 1.0
> **Predecessor**: [REQUIREMENTS-SPEC-INSTITUTION.md](../REQUIREMENTS-SPEC-INSTITUTION.md)
> **Legacy origin**: screen 00 — `biztalk_admin_00_view.jsp` / `biztalk_admin_00.js` / `WSVC.biztalk_admin_00_l001` / `IDO.KKB_FT_FTIS_INFO_L001`

---

## 1. Scenario overview

| Item | Content |
|------|---------|
| **Primary user** | Internal operator |
| **Secondary user** | — (client-company users have no access — FR-AZ-I03) |
| **Precondition** | Authenticated session with an operator role |
| **Success outcome** | A paginated list of 이용기관 matching the criteria, with 인증키 masked |
| **Failure outcome** | Authorization rejection, empty result set, or an explicit error state |
| **Related FR** | FR-INST-001…009, FR-AZ-I01…I04, FR-ATK-002 |
| **Related BR** | BR-002, BR-005, BR-007 |

## 2. Main flow

| Step | Actor | Action | System response |
|------|-------|--------|-----------------|
| 1 | Operator | Opens 이용기관관리 | Session verified and **operator role verified server-side** (FR-AZ-I01, FR-AZ-I02) |
| 2 | System | Initialises the form | 검색어 empty, 상태 defaults to 전체 |
| 3 | Operator | Enters a 기관명 fragment and/or selects 상태 (전체 / 사용 / 사용안함) | Client-side format validation only; the server revalidates (FR-INSTC-003) |
| 4 | Operator | Clicks 조회 | Query executed with bound parameters (NFR-SEC-INJ-I01) |
| 5 | System | Executes the query | Partial match on 기관명 with `%`/`_` escaped (FR-INST-005); NULL 기관명 rows retained (FR-INST-004); logically deleted rows excluded (FR-INSTL-005) |
| 6 | System | Returns the page | Page slice plus total count (FR-INST-003); 인증키 masked (FR-ATK-002); audit record written (NFR-OPS-AUDIT-I01) |
| 7 | System | Renders the grid | 8 columns (FR-INST-002); 사용여부 as 사용/미사용 (FR-INST-006); 등록/수정일자 with time (FR-INST-008); 기관코드 rendered as an escaped link (FR-INST-007) |
| 8 | Operator | Pages through results | Each page is a fresh server request preserving criteria |
| 9 | Operator | Selects a row | 수정 / 중지 / 재사용 / 삭제 become available for that single institution (FR-INST-009) |

## 3. Alternative flows

### 3.1 A-1: Filter by 사용 only
- Branches at Step 3. 상태 = 사용.
- Result: only institutions with 사용여부 = `Y`. **Regression guard:** an institution disabled through UC-INST-003 must disappear from this filter — under the legacy it did not (D-I1).

### 3.2 A-2: Empty search
- Branches at Step 3. No 기관명, 상태 = 전체.
- Result: the full registry, paginated. **Regression guard:** institutions with a NULL 기관명 must appear (FR-INST-004).

### 3.3 A-3: Drill into an institution
- Branches at Step 7. Operator clicks 기관코드.
- Result: UC-INST-002 begins in edit mode, carrying `IS_CD`.

## 4. Exception flows

### 4.1 E-1: Non-operator calls the service directly
- At Step 1, or by crafting a request to the list endpoint.
- Action: reject with 403. **Regression guard:** the legacy gated on `<login>Y</login>` alone, so any authenticated user could read the whole registry including every 인증키 (D-I2, D-I5).

### 4.2 E-2: Unauthenticated or expired session
- At any step.
- Action: reject with 401 and redirect to login.

### 4.3 E-3: Wildcard characters in the search
- At Step 3. Operator types `%` or `_`.
- Action: treated as literal characters (FR-INST-005), not as wildcards matching everything.

### 4.4 E-4: Action attempted with no selection
- At Step 9.
- Action: the action is unavailable or rejected before any confirmation dialog (FR-INST-009, FR-INSTL-007).

### 4.5 E-5: Database unavailable
- At Step 5.
- Action: no partial results; error state shown, error logged without credentials or personal data (NFR-SEC-LOG-I01).

### 4.6 E-6: No matching records
- At Step 6.
- Action: empty grid with an explicit "조회 결과가 없습니다" state — distinguishable from an error.

## 5. NFR mapping

| NFR-ID | Application |
|--------|-------------|
| NFR-PERF-I01 | Step 5–6: P95 < 1 s |
| NFR-PERF-I02 | Step 6: default 20 rows/page, max 200 |
| NFR-SEC-AUTHZ-I01 | Step 1: operator role enforced server-side |
| NFR-SEC-CRED-I01/I02 | Step 6–7: 인증키 masked, never disclosed to a non-operator |
| NFR-SEC-INJ-I01 | Step 5: bound parameters; Step 7: escaped output |
| NFR-OPS-AUDIT-I01 | Step 6: audit record per invocation |

## 6. Screen sketch

```
┌─ 서비스 관리 › 이용기관 관리 ───────────────────────────────┐
│ 검색 [이용기관 검색_______________]                          │
│ 상태 (•) 전체  ( ) 사용  ( ) 사용 안함           [ 조회 ]    │
├─────────────────────────────────────────────────────────────┤
│         [등록] [수정] [중지] [재사용] [삭제]                 │
├─────────────────────────────────────────────────────────────┤
│기관코드│기관명│영문명│사용여부│인증키   │등록일시│수정일시│설명│
│ K0A123 │ABC사 │ ABC  │ 사용   │••••••7f3│…      │…      │…   │
├─────────────────────────────────────────────────────────────┤
│                   ◀ 1 2 3 … ▶   총 128 건                   │
└─────────────────────────────────────────────────────────────┘
```

## 7. Test scenarios

| Test ID | Case | Input | Expected |
|---------|------|-------|----------|
| TC-I001-01 | Normal search | 기관명 fragment | Matching institutions, paginated |
| TC-I001-02 | Empty search | No criteria, 상태 전체 | Full registry, paginated |
| TC-I001-03 | **D-I11 regression** | Row with NULL 기관명, empty search | Row **is** returned |
| TC-I001-04 | **D-I11 regression** | 기관명 search = `%` | Treated literally; does not match everything |
| TC-I001-05 | **D-I10 regression** | Registry with 500 institutions | One page returned, total count accurate, `LIMIT`/`OFFSET` applied server-side |
| TC-I001-06 | **D-I2 regression** | List endpoint called by a non-operator | 403, no data |
| TC-I001-07 | **D-I2 regression** | List endpoint called with no session | 401, no data |
| TC-I001-08 | **D-I5 regression** | Any result row | 인증키 masked; the full value is absent from the response payload |
| TC-I001-09 | **D-I1 regression** | Disable an institution (UC-INST-003), then filter 상태 = 사용 | The institution is **absent** — the legacy still showed it as 사용 |
| TC-I001-10 | **D-I12 regression** | Institution whose 기관코드 contains `'` or `<script>` | Rendered escaped; no script execution, no broken handler |
| TC-I001-11 | **D-I9 regression** | Newly created institution | 등록일시 shows a real hour, not `24` |
| TC-I001-12 | Status filter | 상태 = 사용안함 | Only 사용여부 = `N` |
| TC-I001-13 | Status rendering | Rows with `Y` and `N` | 사용 / 미사용 |
| TC-I001-14 | Unmapped status | Row with an unexpected 사용여부 value | Displayed verbatim, not blank (FR-INST-006) |
| TC-I001-15 | Soft-delete exclusion | Institution deleted via UC-INST-003 | Absent from the default list (FR-INSTL-005) |
| TC-I001-16 | Selection required | 수정 clicked with no row selected | Action unavailable; no dialog shown |
| TC-I001-17 | Empty result | 기관명 matching nothing | Explicit empty state, not an error |
| TC-I001-18 | Timestamp display | Any row | 등록일시/수정일시 show date **and** time (FR-INST-008) |
