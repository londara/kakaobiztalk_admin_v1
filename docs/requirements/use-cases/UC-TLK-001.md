# Use Case UC-TLK-001: Query BizTalk transaction history for a period

> **REQ-ID**: UC-TLK-001
> **Version**: 1.0
> **Predecessor**: [REQUIREMENTS-SPEC-TALK.md](../REQUIREMENTS-SPEC-TALK.md)
> **Legacy origin**: screen 30 — `biztalk_admin_30_view.jsp` / `biztalk_admin_30.js` / `WSVC.biztalk_admin_30_l001` + `…_30_l003` / `IDO.KKB_APITR_HSTR_L001` + `IDO.KKB_OPENAPI_INFO_L002`

---

## 1. Scenario overview

| Item | Content |
|------|---------|
| **Primary user** | Internal operator |
| **Precondition** | Authenticated session with the operator role (inherited from the 로그인 slice) |
| **Trigger** | User opens 톡전송 내역 |
| **Success outcome** | BizTalk API transactions for the requested period are returned in a stable order, paged with a total count, and the read is audited |
| **Failure outcome** | Unauthenticated or non-operator request rejected; invalid, inverted or over-long period rejected |
| **Related FR** | FR-AZ-T01, FR-AZ-T02, FR-AZ-T05, FR-AZ-T06, FR-TLK-001…015 |
| **Related NFR** | NFR-PERF-T01, NFR-SEC-AUTHZ-T01, NFR-SEC-TENANT-T01, NFR-USE-T01 |
| **Related CONST** | CONST-DATA-T01, CONST-DATA-T02, CONST-SEC-T01, CONST-BIZ-T01 |
| **Related BR** | BR-005, BR-006, BR-008 |

## 2. Main flow

| Step | Actor | Action | System response |
|------|-------|--------|-----------------|
| 1 | User | Opens the screen | Session **and operator role** verified server-side (FR-AZ-T01, FR-AZ-T02) |
| 2 | System | Loads the API selector | Only BizTalk API codes are offered, from configuration (FR-TLK-002, FR-TLK-012) |
| 3 | System | Loads the 이용기관 selector | Operator scope; the selector returns 코드 and 명 only (FR-TLK-012) |
| 4 | System | — | **No query is issued yet** — not until the selector has loaded (FR-TLK-015) |
| 5 | User | Sets 요청일자 범위, 요청시각, 거래일련번호, 상태, API서비스, 이용기관 | 요청일자 defaults to today; an omitted 시각 bound means the whole day (FR-TLK-001, FR-TLK-008) |
| 6 | User | Presses 조회 | — |
| 7 | System | Validates the request | Dates are calendar dates, 시작 ≤ 종료, span ≤ 31 days, 시작시각 ≤ 종료시각 — all **server-side** (FR-TLK-007, FR-TLK-014) |
| 8 | System | Normalises 거래일련번호 | One padding rule, the same one the detail and export paths use (FR-TLK-009) |
| 9 | System | Executes the query | Restricted to BizTalk API codes (FR-TLK-002); 기관명 by join (FR-TLK-011); only the nine bound columns selected (FR-TLK-012, CONST-SEC-T01) |
| 10 | System | Returns the page slice | Server-side pagination with a total count (FR-TLK-005), ordered 등록시각 desc then 거래고유번호 (FR-TLK-006) |
| 11 | System | Renders the grid | 일자, 기관코드, 기관명, 거래고유번호, API, 상태, 응답코드, 등록시각, 완료시각 (FR-TLK-003) |
| 12 | System | Presents coded values | 상태 label plus raw code, from the same source as the filter's options (FR-TLK-004, NFR-USE-T01); an unresolved 기관코드 shows the code with a marker (FR-TLK-011) |
| 13 | System | Marks drillable rows | A 상세 link appears exactly on rows the detail service can serve, 처리중 and 오류 included (FR-TLK-013) |
| 14 | System | Writes an audit event | Actor, timestamp, filters, institution scope, row count (FR-AZ-T05) |
| 15 | User | Pages or re-queries | Order is identical across requests; no row is duplicated or skipped (FR-TLK-006) |

## 3. Alternative flows

### 3.1 A-1: Query narrowed to one API service
- Branches at Step 5. The predicate matches the same code the grid displays, so filtering by a value read off the screen returns that row (FR-TLK-010).

### 3.2 A-2: Query narrowed to one 이용기관
- Branches at Step 5. Institution scope is applied server-side; the operator may also query all institutions (FR-AZ-T02).

### 3.3 A-3: Single-day query
- The default. A range of one day is the legacy's only capability and remains the default shape (FR-TLK-007).

### 3.4 A-4: Search by 거래일련번호
- At Step 8. A value entered with or without leading zeros matches the same transaction (FR-TLK-009).

## 4. Exception flows

### 4.1 E-1: Non-operator principal reaches the service
- At Step 1, or by crafting a request directly.
- Action: reject with 403 (FR-AZ-T02). **Regression guard:** the legacy screen had no institution filter and no role check beyond `<login>Y</login>`, so any authenticated principal could read every customer's transactions (D-T2).

### 4.2 E-2: Period exceeds the cap, or dates are invalid
- At Step 7. A direct call with an inverted range, a non-date string, or a 90-day span.
- Action: reject with 400 (FR-TLK-007, FR-TLK-014). **Regression guard:** the only validation was a client-side seconds-of-day comparison, and a blank 종료시각 became the sentinel `999999` (D-T24).

### 4.3 E-3: Two rows share an 등록시각 across a page boundary
- At Step 10.
- Action: the tiebreaker makes the order total, so each row appears exactly once across the paged set (FR-TLK-006). **Regression guard:** `ORDER BY RGDT DESC` alone, with the production screenshot showing eleven rows on one timestamp (D-T10).

### 4.4 E-4: A transaction's 기관코드 is not in the institution master
- At Step 12.
- Action: render the code with an explicit unresolved marker (FR-TLK-011). **Regression guard:** the correlated subquery returned NULL and the cell rendered blank (D-T26).

### 4.5 E-5: A non-BizTalk API transaction exists in the window
- At Step 9.
- Action: it is not returned (FR-TLK-002, ruling SCOPE-T01). **Note:** this is a deliberate, visible change from the legacy screen, which returned every API's transactions including `ADV_COM_GET_STATUS`.

### 4.6 E-6: A caller invokes the retired log-history service
- Any time.
- Action: no such endpoint exists (FR-AZ-T06). **Regression guard:** `biztalk_admin_30_l002` was registered and live, returning unmasked recipient and sender numbers over an arbitrary range with no pagination, while no screen called it (D-T3).

## 5. Postconditions

- No row is written to any table by this use case (CONST-DATA-T01).
- An audit record exists for the query (FR-AZ-T05).
- No account, card, amount or telegram column left the database (CONST-SEC-T01).

## 6. Regression guards summary

| Defect | Guard |
|--------|-------|
| D-T2 | E-1 — non-operator receives 403; institution scope applied server-side |
| D-T3 | E-6 — the endpoint does not exist |
| D-T10 | E-3 — paged union equals the full set exactly once on a tied-timestamp fixture |
| D-T11 | Step 10 — the response carries a total count |
| D-T13 | Step 13 — link presence equals server serviceability |
| D-T15 | A-1 — filtering by the displayed code returns the row |
| D-T24 | E-2 — server-side rejection of invalid and over-cap ranges |
| D-T25 | A-4 — padded and unpadded inputs match the same transaction |
| D-T26 | E-4 — unresolved institution renders the code, not a blank |
| D-T27 | Step 3 — the selector response carries two fields |
| D-T28 | Step 4 — no query before the selector has loaded |
| D-T29 | Step 12 — filter options and column labels enumerate identically |
