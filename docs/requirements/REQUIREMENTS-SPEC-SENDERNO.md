# Requirements Specification — 이용기관 정보 관리 / 발신번호 (Sender Number Management)

> **Version**: 1.0
> **Date**: 2026-08-17
> **Scope**: legacy screen **10 (이용기관 정보 관리)** and its three popups — **11 (발신번호 상세/수정)**, **12 (발신번호 등록)**, **13 (발신번호 제거)**
> **Predecessors**: [PROJECT-PROPOSAL.md](../planning/PROJECT-PROPOSAL.md), [BUSINESS-REQUIREMENTS.md](../planning/BUSINESS-REQUIREMENTS.md)
> **Sibling specs**: [REQUIREMENTS-SPEC.md](REQUIREMENTS-SPEC.md) (문자내역), [REQUIREMENTS-SPEC-LOGIN.md](REQUIREMENTS-SPEC-LOGIN.md) (로그인), [REQUIREMENTS-SPEC-INSTITUTION.md](REQUIREMENTS-SPEC-INSTITUTION.md) (이용기관관리)
> **Traceability matrix**: [requirements-matrix.csv](requirements-matrix.csv)
> **Question log**: [questions-log.md](questions-log.md) — Part 4
> **Status**: **DRAFT — awaiting G1**

---

## 1. Overview

This document specifies the 발신번호 (sender number) management feature of the new IRIS BizTalk Portal, derived by static analysis of 24 legacy artifacts. As with the earlier slices there is no runnable legacy environment (proposal RISK-001), so every requirement was recovered by reading source across four layers and cross-checking the layers against one another.

Screen 10 is titled 이용기관 정보 관리, but its only implemented function is the **발신번호 ledger for a selected institution**. A 수수료 tab exists in the JavaScript and is commented out of the JSP; it carries no server contract and is excluded (§2.7). The screen sits downstream of the 이용기관관리 slice — it consumes the `IS_CD` that screen 00 issues — and upstream of message sending, because a number registered here is what the send path accepts as a caller ID.

That last point sets the risk profile for the whole slice. A sender number is not merely a display field: registering one asserts the right to send messages that appear to originate from it. Every control in this specification exists to make that assertion trustworthy.

**Authentication and session handling are not re-specified here.** They are already settled in [REQUIREMENTS-SPEC-LOGIN.md](REQUIREMENTS-SPEC-LOGIN.md) (FR-LOGIN-*, FR-OTP-*, NFR-SEC-SESSION-L01/L02) and this slice inherits them unchanged. What this document specifies is **authorization** — which authenticated operator may act on which institution's numbers — because that does not exist today in any form.

### 1.1 Legacy artifacts analyzed

| Layer | File |
|-------|------|
| View | `IRIS_ADMIN/web/view/jex/iris_admin/biztalk/biztalk_admin_10_view.jsp` (114 L) |
| View (popups) | `…/biztalk_admin_11_view.jsp` (123 L), `…/biztalk_admin_12_view.jsp` (118 L), `…/biztalk_admin_13_view.jsp` (94 L) |
| Client logic | `IRIS_ADMIN_STATIC/…/biztalk/biztalk_admin_10.js` (298 L) |
| Client logic (popups) | `…/biztalk_admin_11.js` (69 L), `…/biztalk_admin_12.js` (117 L), `…/biztalk_admin_13.js` (72 L) |
| Action | `biztalk_admin_10_l001_act.jsp` (발신번호조회), `_10_d001` (발신번호삭제), `_11_l001` (상세조회), `_11_u001` (설명수정), `_12_c001` (등록) |
| Service contract | `WSVC.biztalk_admin_10`, `…_10_l001`, `…_10_d001`, `…_11`, `…_11_l001`, `…_11_u001`, `…_12`, `…_12_c001`, `…_13` |
| Query | `IDO.KKB_DPNO_LDGR_L001` (detail), `_L002` (list), `_C001` (insert), `_U001` (description update), `_D001` (delete one), `_D002` (delete all for an institution) |
| Query (related) | `IDO.KKB_DPNO_HIS_C001` (history), `IDO.KKB_MNGR_LDGR_L002` (manager count) |
| Utility | `ap.com.util.RegexNameMasking` |
| Cross-slice | `biztalk_admin_00_l001` (institution combo), `_00_l003` (manager check), `_01_l002` (institution detail) |

### 1.2 Data model

Target DB: `BIZTALK_DB` (PostgreSQL).

| Table | Role | Key |
|-------|------|-----|
| `KKB_DPNO_LDGR` | Sender-number ledger — authoritative | `IS_CD` + `DP_NO` |
| `KKB_DPNO_HIS` | Change history; `ACN` is `C` (create) or `D` (delete) | `IS_CD` + `DP_NO` + `RGDT` |
| `FT_FTIS_INFO` | Institution master — **read-only in this slice**, joined by correlated subquery for 기관명 | `FINTECH_ISCD` |
| `KKB_MNGR_LDGR` | Manager roster — read-only, used by the browser-side permission check | `MNGR_MAIL` |

`KKB_DPNO_LDGR` columns, recovered from the union of all six queries: `IS_CD`, `DP_NO`, `RGDT`, `RGSR_ID`, `RGSR_NM`, `UDDT`, `UDT_ID`, `UDT_NM`, `DSCP`. **There is no status column** — a fact that drives CONFLICT-S01.

`DP_NO`, `RGSR_NM` and `UDT_NM` are stored through the DB functions `ENCRYPT()` / `decrypt()`. `RGSR_ID` and `UDT_ID` hold the operator's **email address in plaintext** (D-S16).

### 1.3 Classification and priority

Per harness standard: `FR-` functional, `NFR-<area>-` non-functional, `CONST-<area>-` constraint, `UC-` use case. Priority is MoSCoW. Requirement families in this slice: `FR-AZ-D*` (access control), `FR-SND-*` (list), `FR-SNDC-*` (create), `FR-SNDU-*` (detail and edit), `FR-SNDD-*` (delete).

### 1.4 Defect disposition (PM ruling, 2026-08-17)

Twenty-one defects were confirmed. Consistent with the precedent set on the two previous slices, **all are fixed except D-S4**, which the PM ruled to carry forward deliberately (§6.2, RESIDUAL-S01).

| ID | Sev | Defect | Disposition |
|----|-----|--------|-------------|
| D-S1 | Critical | **Deletion does not work and reports success.** The list masks `DP_NO` via `RegexNameMasking.maskName()`; the grid ships that masked value to the delete service; `KKB_DPNO_LDGR_D001` matches on `decrypt(DP_NO) = :DP_NO` and so deletes **zero rows** without raising an error, while a history row is still written | FIX → FR-SNDD-001…003 + operational data audit (AMB-S04) |
| D-S2 | Critical | No server-side authorization on any of the six services — `<login>Y</login>` only. The manager check runs **in the browser** (`biztalk_admin_00_l003` + `alert('권한 없음')`) and guards only 등록/수정; delete has no check at all, not even client-side | FIX → FR-AZ-D01…D05 |
| D-S3 | Critical | No tenant isolation. `IS_CD` is taken verbatim from the request body, so any authenticated user can list or delete any institution's sender numbers | FIX → FR-AZ-D03 |
| D-S4 | Critical | Ownership verification (ARS/SMS OTP) is commented out in `12_c001_act.jsp` and `10_d001_act.jsp`, and its UI fields are commented out of the JSPs — yet `AUTH_NO` is still a declared input on both contracts | **ACCEPT** (PM ruling AMB-S01) → RESIDUAL-S01 |
| D-S5 | Critical | Multi-row delete corrupts history. The loop deletes each number `t`, but the history insert uses `putAll(input)` where `DP_NO` is the **comma-joined list** — so three deletions write three rows each containing the whole CSV string as one encrypted "number" | FIX → FR-SNDD-004 |
| D-S6 | High | No transaction anywhere in the slice. Statements execute individually; a failure mid-loop leaves some numbers deleted, some not, and history inconsistent with both | FIX → FR-SNDD-005, NFR-OPS-D01 |
| D-S7 | High | The error check after the history insert tests `idoOut1` — the **previous** statement's result — instead of `idoOut2`. History-write failures are silently swallowed. The identical copy-paste bug appears in `12_c001_act.jsp` | FIX → FR-SNDC-008, FR-SNDD-004 |
| D-S8 | High | 수정 is unreachable. `biztalk_admin_10.js` binds `#btn_update` and toggles its visibility, but `biztalk_admin_10_view.jsp` renders no such element — so screen 11 and the service `biztalk_admin_11_u001` are dead code, and 설명 can never be edited after creation | FIX → FR-SNDU-001…005 |
| D-S9 | High | The duplicate check is scoped to one institution (`KKB_DPNO_LDGR_L001` filters `IS_CD` **and** `DP_NO`), so the same number can be registered by several institutions | FIX → FR-SNDC-004 (PM ruling AMB-S03) |
| D-S10 | High | 설명 updates write no history row. `KKB_DPNO_HIS.ACN` is only ever `C` or `D`; `biztalk_admin_11_u001` writes nothing at all | FIX → FR-SNDU-004 |
| D-S11 | High | Registration's client-side validation is **vacuous**. It tests `#ATK`, `#BRNO`, `#IS_ENGNM` and `#AUTH_NO`, none of which exist in `biztalk_admin_12_view.jsp`; `$(missing).val()` returns `undefined`, and `undefined == ""` is false, so every check passes. `validationEngine` is initialised but never invoked, so the declared `notNull,number,htmlTag` rules never run either | FIX → FR-SNDC-003 |
| D-S12 | Medium | The registration screen states "112, 114, 1335 와 같은 특수번호는 등록 불가능합니다" as a user-facing rule. It is implemented **nowhere** — not in `isValidDpNo()`, not in the contract, not in the query | FIX → FR-SNDC-006 |
| D-S13 | Medium | `isValidDpNo()` checks length and prefix but never that the value is numeric, so an 8-character alphabetic string is accepted server-side | FIX → FR-SNDC-005 |
| D-S14 | Medium | Paging is declared and sent (`PAGE_NO`, `INQ_TOTL_NCNT`) but absent from the contract and the SQL. `KKB_DPNO_LDGR_L002` has no `LIMIT`/`OFFSET` and **no `ORDER BY`**, so the full list is fetched every time in nondeterministic order and paged in the browser — over a widget the JSP hides | FIX → FR-SND-003, FR-SND-004 |
| D-S15 | Medium | No server-side length validation on 설명 (200) or 사유 (100); `maxlength` in HTML is the only limit and the contract declares no length | FIX → FR-SNDC-007 |
| D-S16 | Medium | Inconsistent PII handling: `RGSR_NM`/`UDT_NM` are encrypted at rest **and** masked on read, while `RGSR_ID`/`UDT_ID` store the operator's email address in plaintext | FIX → NFR-SEC-PII-D01 |
| D-S17 | Medium | Contract/response mismatch. `WSVC.biztalk_admin_10_l001` declares both `ISNM` and `IS_NM`; the query returns only `IS_NM`, so `ISNM` is permanently empty. `KKB_DPNO_LDGR_L002` omits aliases on its two masked columns, leaving output binding **positional** | FIX → CONST-DATA-D03 |
| D-S18 | Medium | The registration popup loads institution data through `biztalk_admin_01_l002`, which (per D-I3 in the institution slice) returns the full institution record **including 인증키** — a live credential pulled into the browser by a screen that needs only the name | FIX → FR-SNDC-002, inherits FR-ATK-004 |
| D-S19 | Low | On load, `getDat()` runs before `fn_getIsList()` populates the combo, so every page load issues a query with an empty `IS_CD` | FIX → FR-SND-002 |
| D-S20 | Low | Dead and contradictory code: the 수수료 tab is commented out of the JSP but its handler survives in JS and calls an undefined `fn_updateChart()`; `grid2`/`getDat2`/`biztalk_admin_10_l002` are commented out; hidden `IS_CD`/`PARAM_4` are hardcoded to `1` then overwritten; `13.js` binds `#btn_auth_send` to an element that does not exist | FIX → §2.7 |
| D-S21 | Low | Over-fetching: `RGSR_ID` and `UDT_ID` are returned to the browser but never displayed | FIX → NFR-SEC-PII-D02 |

> **On D-S1.** The masking IDO versions are stamped `20251017`, four years after the delete logic (2021). The most likely reading is that read-masking was added in the October 2025 release and broke deletion at that moment. Because the failure is silent — the operator sees "정상적으로 처리되었습니다" and the number simply reappears on the next query — it may have gone unnoticed since. **The migration must not assume the ledger reflects operators' intent:** numbers believed deleted are probably still live and still valid for sending. AMB-S04 raises the data audit.

---

## 2. Functional Requirements

### 2.1 Access control

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| FR-AZ-D01 | Every 발신번호 service requires an authenticated session **and** an operator role. Authentication itself is inherited unchanged from the 로그인 slice (FR-LOGIN-*). Fixes D-S2 | Must | Security test per endpoint |
| FR-AZ-D02 | Authorization is enforced **server-side on each service**. A browser-side role query followed by `alert('권한 없음')` is not an access control. Fixes D-S2 | Must | Security test (direct endpoint call as non-operator) + code review |
| FR-AZ-D03 | The institution scope of every request is derived from the authenticated session's permissions and validated server-side; `IS_CD` supplied in the request body is never trusted on its own. Fixes D-S3, extends FR-TEN-004 | Must | Security test (request another institution's `IS_CD`) |
| FR-AZ-D04 | Delete is authorized at least as strictly as register. Fixes D-S2, where register had a browser-side manager check and delete had none | Must | Security test |
| FR-AZ-D05 | Every state-changing operation records actor identity, timestamp, target `IS_CD` + 발신번호, and before/after values | Must | Integration test + operational audit |

> Because ownership verification is not being implemented (AMB-S01), FR-AZ-D01…D05 are the **only** barrier between an authenticated user and a sender number they have no right to. They are not defence in depth here; they are the defence.

### 2.2 Sender-number list (screen 10)

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| FR-SND-001 | An operator selects an 이용기관 and views its registered 발신번호 list | Must | E2E test |
| FR-SND-002 | No query is issued until an institution is selected. Fixes D-S19 | Should | Integration test |
| FR-SND-003 | The list is **server-side paginated**; the response carries the page slice plus a total count. Fixes D-S14 | Must | Integration test |
| FR-SND-004 | The list has a **deterministic sort order** (등록일시 descending by default) so that pagination is stable. Fixes D-S14, where the query had no `ORDER BY` | Must | Integration test (repeat query returns identical order) |
| FR-SND-005 | The grid presents 기관명, 발신번호, 등록자, 등록일자, 수정자, 수정일자, 설명 | Must | E2E test |
| FR-SND-006 | 발신번호 is displayed **in full**, not masked (PM ruling AMB-S04). List and detail are consistent with each other | Must | E2E test |
| FR-SND-007 | Row identity for any subsequent action is carried by a value the server can resolve unambiguously — never by a display-formatted string. Fixes the root cause of D-S1 | Must | Integration test + code review |
| FR-SND-008 | 등록자 and 수정자 names are masked for display, consistent with CONST-LEGAL-01 | Must | Unit test |
| FR-SND-009 | 등록일자 and 수정일자 display date **and** time | Should | E2E test |
| FR-SND-010 | The institution selector lists institutions the operator is entitled to see, and indicates 사용여부 rather than silently mixing active and suspended institutions. *(Legacy calls `biztalk_admin_00_l001` with `USE_YN=ALL`.)* | Should | E2E test |
| FR-SND-011 | Reading a 발신번호 list is recorded as an audit event (PM ruling AMB-S04 — full display is compensated by read auditing) | Must | Integration test |

### 2.3 Registration (screen 12)

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| FR-SNDC-001 | An operator registers a 발신번호 against a selected institution with 발신번호, 설명 and 사유 | Must | E2E test |
| FR-SNDC-002 | The registration form obtains the institution's 기관코드 and 기관명 from a service that returns **only those fields**. Fixes D-S18 — the legacy pulled the institution's 인증키 into the browser | Must | Security test (response contains no credential) |
| FR-SNDC-003 | All validation is enforced **server-side**. Fixes D-S11, where every client check tested a non-existent element and therefore always passed | Must | Integration test per rule (direct service call) |
| FR-SNDC-004 | A 발신번호 is **globally unique** — registration is rejected if the number exists for *any* institution, not merely the requesting one (PM ruling AMB-S03). Fixes D-S9 | Must | Integration test (register a number held by another institution) |
| FR-SNDC-005 | 발신번호 must be numeric. Fixes D-S13 | Must | Unit test (alphabetic input rejected) |
| FR-SNDC-006 | Special and emergency numbers (112, 114, 119, 1335 and the like) are rejected. Fixes D-S12 — the rule is stated to the user but implemented nowhere. The authoritative list is an open item (AMB-S06) | Must | Unit test per listed number |
| FR-SNDC-007 | 설명 (≤ 200) and 사유 (≤ 100) lengths are validated server-side. Fixes D-S15 | Must | Integration test |
| FR-SNDC-008 | Registration and its history record are written in **one transaction**; a history-write failure fails the registration rather than being swallowed. Fixes D-S7 | Must | Integration test (forced history failure) |
| FR-SNDC-009 | 등록자 and 최종수정자 are taken from the authenticated session, never from the request body | Must | Integration test |
| FR-SNDC-010 | Length rules are preserved: 8–11 digits, extended to 12 for numbers beginning 030 or 050, and exactly 8 for 15xx/16xx representative numbers | Must | Unit test per branch |

### 2.4 Detail and description edit (screen 11)

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| FR-SNDU-001 | The 발신번호 detail view is **reachable from the list**. Fixes D-S8 — the legacy bound a 수정 handler to a button that was never rendered, making this screen dead | Must | E2E test |
| FR-SNDU-002 | Detail shows 기관명, 발신번호, 등록시간, 수정시간, 등록자, 등록ID, 수정자, 수정ID and 설명 | Must | E2E test |
| FR-SNDU-003 | 설명 is editable; 발신번호 and 이용기관 are immutable. Changing a number is register-plus-delete, not an edit | Must | Integration test (update attempt on 발신번호 rejected) |
| FR-SNDU-004 | A description change writes a history record with its own action code, distinct from `C` and `D`. Fixes D-S10 | Must | Integration test |
| FR-SNDU-005 | A description change updates 수정자 and 수정일시. *(The legacy `KKB_DPNO_LDGR_U001` sets `DSCP` alone, leaving `UDT_NM`/`UDT_ID`/`UDDT` stale.)* | Must | Integration test |
| FR-SNDU-006 | 설명 length is validated server-side (≤ 200) | Must | Integration test |

### 2.5 Deletion (screen 13)

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| FR-SNDD-001 | Deletion is **logical**: the number is marked deleted and retained, not physically removed (PM ruling AMB-S02). Fixes D-S1 and preserves referential integrity for send history | Must | Integration test |
| FR-SNDD-002 | A delete request that matches no live row **fails** with an explicit error. It must never report success. Fixes D-S1, the defect's most damaging property | Must | Integration test (delete a non-existent number) |
| FR-SNDD-003 | A logically deleted 발신번호 is excluded from the list, from duplicate checks as a live number, and from acceptance by the send path (see CONFLICT-S02) | Must | Integration test + cutover verification |
| FR-SNDD-004 | Each deleted number produces **its own** history record containing that number alone. Fixes D-S5, where a multi-select delete wrote the entire comma-joined list into every history row | Must | Integration test (delete 3 numbers, assert 3 distinct history rows) |
| FR-SNDD-005 | A multi-number deletion is atomic — all succeed or none do. Fixes D-S6 | Must | Integration test (forced mid-loop failure) |
| FR-SNDD-006 | 사유 is mandatory on deletion and stored in the history record | Must | Integration test |
| FR-SNDD-007 | Deletion requires explicit confirmation showing exactly which numbers will be removed | Should | E2E test |
| FR-SNDD-008 | Re-registering a previously deleted number is permitted and creates a new live record, with the earlier deletion still visible in history | Should | Integration test |

### 2.6 History

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| FR-SNDH-001 | Every create, description change and delete writes a `KKB_DPNO_HIS` record with actor, timestamp, action code, target number and 사유 | Must | Integration test |
| FR-SNDH-002 | History records are **append-only**; no operation in this slice updates or deletes one | Must | Code review + integration test |
| FR-SNDH-003 | The history stored for an operation reflects **the operation actually performed**. Fixes D-S5 and the D-S1 case where history recorded a masked string as though it were a number | Must | Integration test |

### 2.7 Excluded from this slice

| Item | Reason |
|------|--------|
| 수수료 (fee) tab | Commented out of `biztalk_admin_10_view.jsp`; its JS handler survives and calls an undefined `fn_updateChart()`; `biztalk_admin_10_l002` and `grid2` are commented out; **no service contract or query exists**. There is no behaviour to port (D-S20) |
| Sender-number ownership verification | PM ruling AMB-S01 — carried forward as not implemented. See RESIDUAL-S01 |
| Authentication / session management | Already specified in [REQUIREMENTS-SPEC-LOGIN.md](REQUIREMENTS-SPEC-LOGIN.md); inherited unchanged |
| Institution master maintenance | Specified in [REQUIREMENTS-SPEC-INSTITUTION.md](REQUIREMENTS-SPEC-INSTITUTION.md); read-only here |

---

## 3. Non-Functional Requirements

### 3.1 NFR-PERF (performance)

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| NFR-PERF-D01 | 발신번호 list P95 < 1 s at 100 rows per page *(small table — consistent with NFR-PERF-I03)* | Should | Load test |
| NFR-PERF-D02 | Register, edit and delete P95 < 1 s | Should | Load test |
| NFR-PERF-D03 | A multi-number delete of 100 numbers completes within 5 s as a single transaction | Could | Load test |

### 3.2 NFR-SEC (security)

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| NFR-SEC-AUTHZ-D01 | Operator role enforced server-side on all six services; no endpoint relies on a hidden button or a client-side alert | Must | Security test |
| NFR-SEC-TENANT-D01 | Institution scope validated server-side on every request, including list, detail, register, edit and delete | Must | Security test |
| NFR-SEC-PII-D01 | Operator identity is handled consistently: if 등록자명 is encrypted at rest, 등록자ID must not be a plaintext email address. Fixes D-S16 | Must | Code review + data check |
| NFR-SEC-PII-D02 | Only fields the screen displays are returned to the browser. Fixes D-S21 | Should | Response-shape test |
| NFR-SEC-INJ-D01 | All queries use bound parameters; 발신번호 is never concatenated into SQL | Must | Code review + security test |
| NFR-SEC-LOG-D01 | 발신번호 is never written to application logs in clear | Must | Log inspection |
| NFR-SEC-CHANNEL-D01 | TLS on all portal traffic *(inherits CONST-SEC-01)* | Must | Configuration review |

### 3.3 NFR-OPS (operations)

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| NFR-OPS-D01 | Every multi-statement operation runs in an explicit transaction with rollback on failure; connections are released on every path. Fixes D-S6 | Must | Integration test + connection-pool test |
| NFR-OPS-D02 | A failed write is surfaced to the operator as a failure. **No operation may report success without having changed state.** Fixes D-S1 | Must | Integration test |
| NFR-OPS-AUDIT-D01 | Audit records for register, edit, delete and list-read carry actor, timestamp, target and outcome | Must | Integration test |
| NFR-OPS-AUDIT-D02 | Audit retention per CONST-LEGAL-02; term `[보류]` — carried open item OI-02 | Must | Configuration review |

### 3.4 NFR-COMPAT / NFR-USE

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| NFR-COMPAT-D01 | Chrome / Edge current and one prior major version *(inherits the programme baseline)* | Must | Cross-browser test |
| NFR-USE-D01 | Deleting requires no more than two steps from the list: select, then confirm with 사유 | Should | Usability review |
| NFR-USE-D02 | Validation failures name the offending field and the rule violated, rather than a generic "등록중 오류 발생" | Should | Usability review |

---

## 4. Constraints

| REQ-ID | Constraint | Basis |
|--------|-----------|-------|
| CONST-DATA-D01 | `KKB_DPNO_LDGR` is the authoritative sender-number ledger; `KKB_DPNO_HIS` is its append-only history | 6 of 6 legacy queries |
| CONST-DATA-D02 | The DB functions `ENCRYPT()` / `decrypt()` remain the mechanism for 발신번호 and 등록자명; key material stays in the database *(consistent with CONST-DATA-I03)* | `KKB_DPNO_LDGR_C001`, `KKB_DPNO_HIS_C001` |
| CONST-DATA-D03 | `KKB_DPNO_LDGR_L002` maps two masked columns to output fields **positionally** (no SQL alias), and `WSVC.biztalk_admin_10_l001` declares an `ISNM` field the query never returns. The port must map explicitly by name and drop the phantom field. Fixes D-S17; same hazard as CONST-DATA-I04 | `IDO.KKB_DPNO_LDGR_L002`, `WSVC.biztalk_admin_10_l001` |
| CONST-DATA-D04 | Logical delete (FR-SNDD-001) requires **new schema** on `KKB_DPNO_LDGR`, which has no status column. This narrows CONST-DATA-01 and requires explicit G1 sign-off — see CONFLICT-S01 | AMB-S02; §1.2 |
| CONST-BIZ-D01 | A 발신번호 is globally unique across institutions. Existing cross-institution duplicates must be identified and resolved before migration | AMB-S03 |
| CONST-BIZ-D02 | Sender-number registration carries **no proof of ownership**. First registration claims the number | AMB-S01, RESIDUAL-S01 |
| CONST-TECH-01 | Java 17+ / Spring Boot 3.x, MyBatis, React SPA | ADR-001 (proposal §11) |
| CONST-LEGAL-01 | Personal data masked in UI, logs and exports | 개인정보보호법 (BR-007) |
| CONST-LEGAL-02 | Audit log retention per 전자금융감독규정 / ISMS-P, term `[보류]` | BR-016, OI-02 |
| CONST-SEC-01 | Internet-facing and multi-tenant; no endpoint may rely on network-perimeter protection as its access control | Proposal §3, RISK-006 |

---

## 5. Use Cases

| UC-ID | Scenario | Primary user | Related FR |
|-------|----------|--------------|------------|
| [UC-SND-001](use-cases/UC-SND-001.md) | Select an institution and review its sender numbers | Operator | FR-SND-001…011, FR-AZ-D01…D05 |
| [UC-SND-002](use-cases/UC-SND-002.md) | Register a new sender number | Operator | FR-SNDC-001…010, FR-SNDH-001…003 |
| [UC-SND-003](use-cases/UC-SND-003.md) | Review detail and edit the description | Operator | FR-SNDU-001…006, FR-SNDH-001…003 |
| [UC-SND-004](use-cases/UC-SND-004.md) | Delete one or more sender numbers | Operator | FR-SNDD-001…008, FR-SNDH-001…003 |

Orphan check: every FR, NFR and CONST in this document maps to at least one of UC-SND-001…004 — see [requirements-matrix.csv](requirements-matrix.csv). **Orphan count: 0.**

---

## 6. AMBIGUOUS / open items

### 6.1 Resolved by PM (2026-08-17)

| ID | Item | Candidates | PM response | Status |
|----|------|-----------|-------------|--------|
| AMB-S01 | Sender-number ownership verification is designed into the contracts, UI and server code but entirely commented out (D-S4) | A: re-enable OTP / B: document-based approval / C: keep as-is | **C — keep as-is, no verification** | RESOLVED → RESIDUAL-S01 |
| AMB-S02 | Deletion is a hard `DELETE`; the institution slice set a logical-delete precedent (D-S1, D-S5) | A: logical delete with DDL / B: hard delete + corrected history / C: suspend only | **A — logical delete, adding schema** | RESOLVED → CONFLICT-S01 |
| AMB-S03 | The duplicate check is per-institution, so one number can be registered by several institutions (D-S9) | A: globally unique / B: unique per institution / C: allow with approval | **A — globally unique** | RESOLVED |
| AMB-S04 | 발신번호 masked on the list by a name-masking function, unmasked on detail — the direct cause of D-S1 | A: show in full + audit reads / B: phone-specific masking rule / C: mask by role | **A — show in full, audit read events** | RESOLVED |

### 6.2 Conflicts and residual risks requiring G1 acknowledgement

| ID | Item | Detail |
|----|------|--------|
| CONFLICT-S01 | **AMB-S02 (logical delete) vs. CONST-DATA-01 ("schema reused unchanged; no DDL migration in this scope")** | Unlike CONFLICT-I02 in the institution slice — which dissolved once analysis found an existing `IS_STTS` column — **this conflict is real.** `KKB_DPNO_LDGR` has nine columns and none of them carries state (§1.2), so logical delete cannot be implemented without new schema. **Needs explicit PM sign-off at G1.** <br><br>**NARROWED at Skill 3 (2026-08-17).** Design confirmed it cannot be dissolved, but reduced its scope: the DDL is **one new table plus one index, additive only**. `KKB_DPNO_LDGR` is never altered in a way that changes what an existing reader sees, and no legacy application is modified. The precedent G1 is asked to set is *"this programme may add tables"*, not *"this programme may alter shared schema"*. Required before task S2-02; Sprint S1 carries no DDL. See [ADR-SND-017](../design/adr/ADR-SND-017-senderno-lifecycle.md) |
| CONFLICT-S02 | **A new status column is invisible to legacy readers** | This is the D-I1 failure mode waiting to happen. If the legacy send path validates a caller ID by selecting from `KKB_DPNO_LDGR`, it will not filter on a column added by this project — so a number an operator has "deleted" stays valid for sending. Logical delete must therefore be paired with either a change to the legacy read path or a representation the legacy already honours. **Raised before the requirement was written**; resolution belongs to Skill 3 (AMB-S05) |
| RESIDUAL-S01 | **Accepted: sender numbers can be registered without proving ownership** | Recorded as a deliberate PM trade-off, not an oversight. A sender number asserts the origin of outbound messages, and 전기통신사업법 / KISA 발신번호 사전등록제 expect the registering party to demonstrate a right to it. Under this ruling the compensating controls are FR-AZ-D01…D05 (only authorized operators may register) and FR-SNDC-004 (global uniqueness, so a number already claimed cannot be taken). **The residual exposure is "first claim wins":** an authorized operator can register a number belonging to a third party, provided nobody has claimed it first. **Should be revisited before the portal is exposed to client-company self-service** — the controls above hold only while registration stays in internal operators' hands |

### 6.3 Open

| ID | Item | Candidates | Working assumption | Owner | Needed by |
|----|------|-----------|--------------------|-------|-----------|
| AMB-S05 | How logical delete is represented so that the legacy send path honours it (CONFLICT-S02) | A: new status column + change the legacy read / B: a representation the legacy already rejects / C: portal writes state, gap tracked as a cutover risk | **B — resolved at Skill 3, 2026-08-17.** Deletion moves the row to a new archive table; the legacy send path already rejects a number absent from the ledger, so it fails safe with no legacy change. See [ADR-SND-017](../design/adr/ADR-SND-017-senderno-lifecycle.md) | Architect | ✅ closed |
| AMB-S06 | The authoritative list of special/emergency numbers barred from registration. The UI names 112, 114 and 1335 as examples; no complete list exists in code (FR-SNDC-006) | A: adopt the KISA/KAIT published special-number list / B: an internally maintained list | A | Domain owner | Skill 3 |
| AMB-S07 | Whether a maximum number of 발신번호 per institution applies. No limit exists today | A: no limit, monitor / B: a configurable cap | A | Domain owner | Skill 3 |
| AMB-S08 | Cascade behaviour when an institution is logically deleted (institution slice AMB-I05). `KKB_DPNO_LDGR_D002` hard-deletes every number for an `IS_CD`; that path contradicts FR-SNDD-001 | A: institution delete blocks the numbers without removing them / B: cascade the logical delete | A | Domain owner | Skill 3 |
| AMB-S09 | Whether `RGSR_ID`/`UDT_ID` should hold an internal user ID rather than an email address, and how existing rows are migrated (NFR-SEC-PII-D01) | A: internal user ID, email retained only in the user master / B: keep email, encrypt at rest | A | Architect | Skill 3 |

Carried and still open: **OI-02** (audit retention term) blocks NFR-OPS-AUDIT-D02 and CONST-LEGAL-02.

> Five items remain open (AMB-S05…S09), none blocking: each carries a stated working assumption.
> **G1 approval must explicitly cover CONFLICT-S01 and RESIDUAL-S01.**

### 6.4 Operational action arising from D-S1

Independent of the migration, deletion has been silently failing in production — probably since the October 2025 release. Before or during migration, the operator team should reconcile `KKB_DPNO_LDGR` against `KKB_DPNO_HIS`: any number with an `ACN='D'` history record that is still present in the ledger was believed deleted and is still live and still valid for sending. History rows whose `DP_NO` decrypts to a masked pattern (`01********8`) or to a comma-joined list identify the affected deletions directly. This mirrors the data audit raised for D-I1 and is the same class of problem: an operator withdrew something and the system did not act on it.

---

## 7. Change history

| Date | Version | Change | Author |
|------|---------|--------|--------|
| 2026-08-17 | 1.0 | Initial specification of the 발신번호 slice (screens 10/11/12/13) from static analysis of 24 legacy artifacts; 21 defects recorded; AMB-S01…S04 resolved by PM | trace-mapper / docs-writer |
