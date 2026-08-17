# Use Case UC-SND-003: Review sender-number detail and edit its description

> **REQ-ID**: UC-SND-003
> **Version**: 1.0
> **Predecessor**: [REQUIREMENTS-SPEC-SENDERNO.md](../REQUIREMENTS-SPEC-SENDERNO.md)
> **Legacy origin**: screen 11 — `biztalk_admin_11_view.jsp` / `biztalk_admin_11.js` / `WSVC.biztalk_admin_11_l001`, `…_11_u001` / `IDO.KKB_DPNO_LDGR_L001`, `…_U001`

---

## 1. Scenario overview

| Item | Content |
|------|---------|
| **Primary user** | Internal operator |
| **Precondition** | Authenticated operator session; a sender number selected in UC-SND-001 |
| **Trigger** | Operator opens a row's detail, or selects a row and clicks 수정 |
| **Success outcome** | Detail is displayed; a description change is saved with actor, timestamp and a history record |
| **Failure outcome** | Unauthorized access rejected, validation rejection, or a rolled-back transaction |
| **Related FR** | FR-SNDU-001…006, FR-SNDH-001…003, FR-AZ-D01…D05 |
| **Related BR** | BR-002, BR-007 |

> **Scope note.** This entire screen is **unreachable in the legacy system.** `biztalk_admin_10.js` binds a `#btn_update` handler and toggles that button's visibility in its tab logic, but `biztalk_admin_10_view.jsp` renders only 등록 and 삭제 — no such element exists. Screen 11 and the service `biztalk_admin_11_u001` are therefore dead code, and 설명 has never been editable after creation (D-S8). This use case specifies intended behaviour recovered from the dead code, not observed behaviour.

## 2. Main flow — 상세 조회 (view detail)

| Step | Actor | Action | System response |
|------|-------|--------|-----------------|
| 1 | Operator | Opens a row's detail from the list | Operator role and institution scope verified server-side (FR-AZ-D01…D03) |
| 2 | System | Resolves the row | Identified by a server-resolvable key, never the displayed string (FR-SND-007) |
| 3 | System | Returns the record | 기관명, 발신번호, 등록시간, 수정시간, 등록자, 등록ID, 수정자, 수정ID, 설명 (FR-SNDU-002) |
| 4 | System | Renders the detail | 발신번호 shown in full, matching the list exactly (FR-SND-006) |
| 5 | Operator | Reviews | 발신번호 and 이용기관 are read-only (FR-SNDU-003) |

## 3. Main flow — 설명 수정 (edit description)

| Step | Actor | Action | System response |
|------|-------|--------|-----------------|
| 6 | Operator | Edits 설명 and clicks 수정 | Server validates length ≤ 200 (FR-SNDU-006) |
| 7 | System | Updates the ledger | 설명 changed; 수정자, 수정ID and 수정일시 updated from the session (FR-SNDU-005) |
| 8 | System | Writes a history record | Its own action code, distinct from `C` and `D` (FR-SNDU-004, FR-SNDH-001) |
| 9 | System | Commits | Update and history in one transaction (NFR-OPS-D01) |
| 10 | System | Writes the audit record | Actor, timestamp, target, before/after 설명 (FR-AZ-D05) |
| 11 | System | Closes the popup | The list refreshes (UC-SND-001) |

## 4. Alternative flows

### 4.1 A-1: View only
- Branches at Step 5. Operator clicks 닫기 without editing. No write; no history record.

### 4.2 A-2: Description cleared
- At Step 6. Operator empties 설명.
- Result: permitted — 설명 is optional. The change is still recorded in history like any other.

## 5. Exception flows

### 5.1 E-1: Attempt to change 발신번호 through the edit service
- At Step 6. A crafted request supplies a different `DP_NO` in the update payload.
- Action: rejected (FR-SNDU-003). Changing a number is register-plus-delete, not an edit. **Design note:** `KKB_DPNO_LDGR_U001` updates `SET DSCP` only and locates the row by `IS_CD` + `decrypt(DP_NO)`, so the legacy could not change a number here — but that safety was incidental to the query text rather than an enforced rule.

### 5.2 E-2: Description change leaves stale audit fields
- At Step 7.
- Action: 수정자/수정ID/수정일시 are updated. **Regression guard:** `KKB_DPNO_LDGR_U001` sets `DSCP` alone, so `UDT_NM`, `UDT_ID` and `UDDT` would have kept the values written at registration — the record would show a description nobody appeared to have changed (part of D-S10).

### 5.3 E-3: Description change leaves no trace
- At Step 8.
- Action: a history record is written. **Regression guard:** `biztalk_admin_11_u001_act.jsp` calls `KKB_DPNO_LDGR_U001` and nothing else — `KKB_DPNO_HIS.ACN` is only ever `C` or `D`, so description changes were entirely untraceable (D-S10).

### 5.4 E-4: Detail requested for another institution's number
- At Step 1. A crafted request supplies another institution's `IS_CD` and a number.
- Action: reject with 403 (FR-AZ-D03). **Regression guard:** `biztalk_admin_11_l001` passed the body straight through to `KKB_DPNO_LDGR_L001` with no scope check, and returned the number **unmasked** — so the detail service was a direct read of any institution's sender number for any authenticated user (D-S3).

### 5.5 E-5: Over-length description
- At Step 6. A direct call supplies 5,000 characters.
- Action: rejected (FR-SNDU-006). **Regression guard:** `WSVC.biztalk_admin_11_u001` declares no length for `DSCP`; only the textarea's `maxlength` limited it (D-S15).

### 5.6 E-6: Update fails mid-transaction
- At Step 9.
- Action: rolled back; neither the ledger nor the history reflects a partial change.

## 6. NFR mapping

| NFR-ID | Application |
|--------|-------------|
| NFR-PERF-D02 | Steps 6–11: P95 < 1 s |
| NFR-SEC-AUTHZ-D01 | Step 1: operator role enforced server-side |
| NFR-SEC-TENANT-D01 | Steps 1–2: institution scope validated server-side |
| NFR-SEC-PII-D01 | Step 3: 등록ID/수정ID handled consistently with 등록자명 (D-S16) |
| NFR-SEC-LOG-D01 | Steps 3, 7: 발신번호 never logged in clear |
| NFR-OPS-D01 | Step 9: explicit transaction with rollback |
| NFR-OPS-AUDIT-D01 | Step 10: before/after recorded |

## 7. Screen sketch

```
┌─ 발신번호 상세 ─────────────────────────────────────────────┐
│ 기관이름 [○○기관        ]   번호     [0212345678         ] │
│ 등록시간 [2025-10-17 14:22]  수정시간 [2025-10-18 09:05   ] │
│ 등록자   [김*수          ]   등록ID   [k***@example.com   ] │
│ 수정자   [이*            ]   수정ID   [l***@example.com   ] │
│ 설명     [                                                ] │
│                      [ 수정 ]  [ 닫기 ]                     │
└─────────────────────────────────────────────────────────────┘
```

## 8. Test scenarios

| Test ID | Case | Input | Expected |
|---------|------|-------|----------|
| TC-S003-01 | **D-S8 regression** | Open the list and look for a route to detail | A working control exists and opens the detail view |
| TC-S003-02 | Normal detail | Select any row | All nine fields populated |
| TC-S003-03 | List/detail consistency | Compare 발신번호 in list and detail | Identical values (FR-SND-006) |
| TC-S003-04 | Normal edit | Change 설명 and save | Saved; visible on re-query |
| TC-S003-05 | **D-S10 regression** | Change 설명, then read the history | A history record exists with an action code distinct from `C` and `D` |
| TC-S003-06 | **D-S10 regression** | Change 설명, then read the ledger row | 수정자, 수정ID, 수정일시 all updated |
| TC-S003-07 | **D-S15 regression** | 설명 of 5,000 chars via a direct call | 400 validation error |
| TC-S003-08 | Immutable number | Update payload carrying a different 발신번호 | Rejected; the stored number is unchanged |
| TC-S003-09 | Immutable institution | Update payload carrying a different `IS_CD` | Rejected |
| TC-S003-10 | **D-S3 regression** | Detail requested for another institution's number | 403; no data returned |
| TC-S003-11 | **D-S2 regression** | Update service called by a non-operator | 403; nothing changed |
| TC-S003-12 | Session-derived actor | Update with a forged 수정자 in the body | Session identity stored; body value ignored |
| TC-S003-13 | Empty description | Clear 설명 and save | Accepted; change recorded in history |
| TC-S003-14 | Rollback | DB error during save | Neither ledger nor history changed |
| TC-S003-15 | Audit content | Any description change | Audit record holds actor, timestamp, target, before/after |
