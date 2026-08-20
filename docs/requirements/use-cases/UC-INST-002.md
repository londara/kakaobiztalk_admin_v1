# Use Case UC-INST-002: Register and edit an 이용기관

> **REQ-ID**: UC-INST-002
> **Version**: 1.0
> **Predecessor**: [REQUIREMENTS-SPEC-INSTITUTION.md](../REQUIREMENTS-SPEC-INSTITUTION.md)
> **Legacy origin**: screen 01 — `biztalk_admin_01_view.jsp` / `biztalk_admin_01.js` / `WSVC.biztalk_admin_01_c001`, `…_01_l001`, `…_01_l002` / `IDO.KKB_FT_FTIS_INFO_C001`, `…_L002`

---

## 1. Scenario overview

| Item | Content |
|------|---------|
| **Primary user** | Internal operator |
| **Precondition** | Authenticated session with an operator role; UC-INST-001 in progress |
| **Trigger** | Operator clicks 등록, or clicks 수정 / a 기관코드 link on a selected row |
| **Success outcome** | A new institution is created, or an existing one updated, with actor and timestamp recorded and the runtime cache refreshed |
| **Failure outcome** | Validation error, duplicate-code rejection, or a rolled-back transaction |
| **Related FR** | FR-INSTC-001…016, FR-ATK-001/003/004/005/006, FR-AZ-I01…I04 |
| **Related BR** | BR-002, BR-005 |

## 2. Main flow — 등록 (create)

| Step | Actor | Action | System response |
|------|-------|--------|-----------------|
| 1 | Operator | Clicks 등록 | Operator role verified server-side (FR-AZ-I01); the form opens empty |
| 2 | Operator | Enters 기관코드 | Format checked client-side for responsiveness only |
| 3 | Operator | Clicks 중복검사 | Server returns **only an availability result** — available or already in use (FR-INSTC-005) |
| 4 | Operator | Clicks 키 생성 | The server generates 인증키 with a CSPRNG and returns it once for confirmation (FR-ATK-001, FR-ATK-003) |
| 5 | Operator | Enters 기관명, 영문명, 사업자등록번호, 사용여부, 설명 | — |
| 6 | Operator | Clicks 저장 | **Server** validates every rule: required fields, 기관코드 format, 사업자등록번호 digits, lengths (FR-INSTC-003) |
| 7 | System | Creates the record | Rejects a 기관코드 that already exists — never overwrites (FR-INSTC-004). Writes 등록자/최종수정자 from the session (FR-INSTC-007) and a full `YYYYMMDDHH24MISS` timestamp (FR-INSTC-006) |
| 8 | System | Refreshes the institution cache | On failure the operation reports an error and raises an alert (FR-INSTC-008, NFR-OPS-I02) |
| 9 | System | Writes the audit record | Actor, timestamp, target 기관코드, before/after (FR-AZ-I04) |
| 10 | System | Closes the popup | The list refreshes (UC-INST-001) |

## 3. Alternative flows

### 3.1 A-1: 수정 (edit)
- Branches at Step 1. Operator selects a row and clicks 수정, or clicks the 기관코드 link.
- The form loads the existing record; **기관코드 is immutable** (FR-INSTC-002) and 중복검사 is unavailable — the button is not rendered rather than rendered inert.
- **인증키 is shown masked** (FR-INSTC-010). The plaintext is never sent to populate the form; the legacy sent it and put it in the DOM (D-I20). 키 생성 becomes 키 재발급 and behaves as A-2, not as a field edit.
- 저장 executes an update, not an upsert (FR-INSTC-004). Steps 6–10 apply unchanged.

### 3.2 A-2: 인증키 rotation during edit
- Branches at Step 4 in edit mode. Operator requests a new key.
- Result: FR-ATK-005 — rotation is an explicit operation with its own confirmation and audit record, distinct from an ordinary field edit, because it breaks the institution's live integration until the new key is distributed.
- **It commits on that confirmation, not on 저장** (FR-INSTC-011, PM ruling AMB-I13). The new key is returned once for distribution; 닫기 afterwards does not undo it, and the 저장 payload carries no key material at all. The legacy did the opposite — the key sat in the form until 저장, so 닫기 discarded it and an abandoned popup left no record of the attempt.

### 3.3 A-3: Cancel
- At any step. Operator clicks 닫기.
- Result: no write; the list is unchanged.

## 4. Exception flows

### 4.1 E-1: Duplicate 기관코드 on create
- At Step 7.
- Action: reject with a duplicate error; the existing record is **not modified**. **Regression guard:** the legacy server was a blind UPSERT gated only by the JS flag `DUP_CHECK_YN`, so a direct call with an existing code silently overwrote that institution *and its 인증키* (D-I6).

### 4.2 E-2: Validation bypassed by a direct service call
- At Step 6. A crafted request omits 기관명 or supplies a malformed 기관코드.
- Action: rejected server-side (FR-INSTC-003). **Regression guard:** every legacy rule — 6-character `K0` prefix, digits-only 사업자등록번호, required fields — existed only in the browser (D-I19).

### 4.3 E-3: Duplicate check used to harvest credentials
- At Step 3. An attacker enumerates 기관코드 values (`K0` + 4 characters).
- Action: only availability is returned. **Regression guard:** the legacy duplicate check ran `KKB_FT_FTIS_INFO_L002` and returned the target institution's full record **including 인증키** (D-I3).

### 4.4 E-4: Non-operator calls the create/update service
- At Step 1 or by crafting a request.
- Action: reject with 403 (FR-AZ-I01/I02). **Regression guard:** the legacy service carried `<login>Y</login>` only (D-I2).

### 4.5 E-5: Cache refresh fails after a committed change
- At Step 8.
- Action: the operator is told the change is saved but not yet active, and an operational alert is raised. **Regression guard:** the legacy wrapped the reload in `catch(Throwable){printStackTrace();}`, so the change silently failed to take effect (D-I17).

### 4.6 E-6: Database error mid-save
- At Step 7.
- Action: transaction rolled back; no partial record. The connection is released (D-I17 — the legacy committed without `endTransaction()`).

## 5. NFR mapping

| NFR-ID | Application |
|--------|-------------|
| NFR-PERF-I03 | Steps 6–10: P95 < 1 s |
| NFR-SEC-AUTHZ-I01 | Step 1: operator role enforced server-side |
| NFR-SEC-CRED-I01 | Step 4: CSPRNG, ≥ 128 bits entropy |
| NFR-SEC-CRED-I02 | Step 3: no credential disclosed by the duplicate check |
| NFR-SEC-INJ-I01 | Step 7: bound parameters |
| NFR-SEC-LOG-I01 | Steps 4–9: 인증키 never logged |
| NFR-OPS-AUDIT-I01 | Step 9: audit record with before/after |
| NFR-OPS-I01/I02 | Step 8: cache refreshed or failure alerted |

## 6. Screen sketch

```
┌─ 이용기관 등록 ─────────────────────────────────────────────┐
│ 이용기관코드* [K0____] [중복검사]  이용기관명* [__________] │
│ 이용기관영문명*[__________]        사업자등록번호*[________] │
│ 인증키*       [(서버 생성)] [키 생성]  사용 여부* [▼ 사용  ] │
│ 설명          [                                           ] │
│                      [ 저장 ]  [ 닫기 ]                     │
└─────────────────────────────────────────────────────────────┘
```

## 7. Test scenarios

| Test ID | Case | Input | Expected |
|---------|------|-------|----------|
| TC-I002-01 | Normal create | All required fields, unused 기관코드 | Institution created; visible in UC-INST-001 |
| TC-I002-02 | **D-I6 regression** | Direct call to the create service with an **existing** 기관코드 | Rejected as duplicate; the existing record and its 인증키 are **unchanged** |
| TC-I002-03 | **D-I6 regression** | Create request that never invoked 중복검사 | Still rejected if the code exists — enforcement is server-side, not flag-based |
| TC-I002-04 | **D-I3 regression** | 중복검사 for an existing 기관코드 | Returns availability only; response contains no 인증키, 기관명 or 사업자등록번호 |
| TC-I002-05 | **D-I3 regression** | Enumerate 100 candidate 기관코드 values | No institution data recovered from any response |
| TC-I002-06 | **D-I4 regression** | Generate 1,000 인증키 | Server-side CSPRNG; no value predictable from another; ≥ 128 bits entropy |
| TC-I002-07 | **D-I19 regression** | Direct call omitting 기관명 | 400 validation error |
| TC-I002-08 | **D-I19 regression** | Direct call with a malformed 기관코드 (wrong length or prefix) | 400 validation error |
| TC-I002-09 | **D-I19 regression** | 사업자등록번호 containing letters | 400 validation error |
| TC-I002-10 | **D-I9 regression** | Create, then read 등록일시 | Full `YYYYMMDDHH24MISS` with a real hour — not a literal `24` |
| TC-I002-11 | **D-I9 regression** | Edit, then read 최종수정일시 | Full timestamp with a real hour |
| TC-I002-12 | **D-I2 regression** | Create service called by a non-operator | 403; nothing created |
| TC-I002-13 | **D-I17 regression** | Force a cache-refresh failure after commit | Operator informed, alert raised; failure not swallowed |
| TC-I002-14 | **D-I17 regression** | 200 consecutive saves | No connection leak; pool stable |
| TC-I002-15 | Immutable code | Edit attempting to change 기관코드 | Rejected (FR-INSTC-002) |
| TC-I002-16 | Session-derived actor | Create with a forged 등록자 in the body | The session identity is stored, the body value ignored (FR-INSTC-007) |
| TC-I002-17 | Audit content | Any create and any edit | Audit record holds actor, timestamp, 기관코드, before/after |
| TC-I002-18 | Rollback | DB error during save | No partial record persisted |
| TC-I002-19 | Key rotation | Rotate 인증키 on an existing institution | New key issued, separately confirmed and audited (FR-ATK-005) |
| TC-I002-20 | Migration parity | Institutions migrated from the legacy | 인증키 values byte-identical to the legacy (FR-ATK-006) |

---

## 8. Additions from the 2026-08-20 gap pass

Specifying the popup field by field added seven requirements (FR-INSTC-010…016) and one defect (D-I20). The flows above are amended in place; the cases they add are listed here so the change is reviewable rather than buried.

### 8.1 Exception flows

#### 4.7 E-7: A stored 사업자등록번호 that predates the validation
- At Step 6, on 수정.
- The row holds a value that is not 10 digits — written before FR-INSTC-009 existed.
- Action: the save is **refused** until the value is corrected, even when the operator edited something else entirely (FR-INSTC-016, PM ruling AMB-I12). The message names the field, so the correction is obvious rather than mysterious.
- **This is a deliberate behavioural break**, not a parity defect: the legacy accepted the save.

#### 4.8 E-8: A masked 인증키 arriving on the save path
- At Step 6, from a crafted request rather than the UI.
- The form holds `••••••••7f3a`; a client that echoed it into a save payload would make the asterisks the credential and break the customer's integration at once.
- Action: **unrepresentable.** The update statement has no `ATK` column and the request record has no key field (FR-INSTC-011). A key can only be changed through the rotation operation, which generates the value server-side and never accepts one from a caller (FR-ATK-001).

#### 4.9 E-9: 사용여부 = `D` submitted from the edit form
- At Step 6, from a crafted request.
- Action: rejected (FR-INSTC-015). `IS_STTS='D'` is the logical-delete marker (ADR-INST-014); reaching it through the edit form would be a delete with no confirmation, no dependent-record preview and no deletion audit entry.

### 8.2 Test scenarios

| Test ID | Case | Input | Expected |
|---------|------|-------|----------|
| TC-I002-21 | **D-I20 regression** | Read the detail of an institution as an operator | Response carries the masked 인증키 only; no plaintext key anywhere in the payload |
| TC-I002-22 | **D-I20 regression** | Rendered edit popup, inspected DOM | No plaintext 인증키 in any attribute or node |
| TC-I002-23 | AMB-I12 enforcement | 수정 changing only 사용여부, on a row whose 사업자등록번호 is 12 digits | Save refused, message names 사업자등록번호 |
| TC-I002-24 | FR-INSTC-011 | Confirm 키 재발급, then 닫기 without 저장 | The new key is persisted and audited; the rotation is not undone |
| TC-I002-25 | FR-INSTC-011 | 수정 save payload, any field change | Payload contains no 인증키 field; `ATK` column untouched by the update |
| TC-I002-26 | FR-INSTC-013 | 수정, then read `LAST_AMDT` | Full `YYYYMMDDHH24MISS` on the **database** clock — comparable with legacy-written rows in the same column, not nine hours behind |
| TC-I002-27 | FR-INSTC-012 | 수정 as `a@b.com` | `LSED_ID` and `LSED_NM` both hold `a@b.com`; no previous editor's name is left beside the new id |
| TC-I002-28 | FR-INSTC-015 | Direct update call with `status='D'` | 400; `IS_STTS` unchanged |
| TC-I002-29 | FR-INSTC-002 | Direct update call whose body carries a different 기관코드 | The path code governs; no row's `FINTECH_ISCD` changes |
| TC-I002-30 | FR-INSTC-008 | 수정, then return to the list | The list shows the new values without a manual re-search |
