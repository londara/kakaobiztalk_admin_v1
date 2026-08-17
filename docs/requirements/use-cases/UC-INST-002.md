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
| **Related FR** | FR-INSTC-001…009, FR-ATK-001/003/004/006, FR-AZ-I01…I04 |
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
- The form loads the existing record; **기관코드 is immutable** (FR-INSTC-002) and 중복검사 is unavailable.
- 저장 executes an update, not an upsert (FR-INSTC-004). Steps 6–10 apply unchanged.

### 3.2 A-2: 인증키 rotation during edit
- Branches at Step 4 in edit mode. Operator requests a new key.
- Result: FR-ATK-005 — rotation is an explicit operation with its own confirmation and audit record, distinct from an ordinary field edit, because it breaks the institution's live integration until the new key is distributed.

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
