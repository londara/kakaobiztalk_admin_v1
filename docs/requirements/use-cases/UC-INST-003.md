# Use Case UC-INST-003: Disable, re-enable and delete an 이용기관

> **REQ-ID**: UC-INST-003
> **Version**: 1.0
> **Predecessor**: [REQUIREMENTS-SPEC-INSTITUTION.md](../REQUIREMENTS-SPEC-INSTITUTION.md)
> **Legacy origin**: screen 00 — `biztalk_admin_00_u001_act.jsp` (중지) / `biztalk_admin_00_d001_act.jsp` (삭제) / `IDO.KKB_FT_FTIS_INFO_U001`, `…_D001`, `KKB_DPNO_LDGR_D002`, `KKB_DPNO_HIS_C001`

---

## 1. Scenario overview

| Item | Content |
|------|---------|
| **Primary user** | Internal operator |
| **Precondition** | Authenticated session with an operator role; exactly one institution selected in UC-INST-001 |
| **Trigger** | Operator clicks 중지, 재사용 or 삭제 |
| **Success outcome** | The institution's lifecycle state changes, the change is visible in the list, and a history record is written |
| **Failure outcome** | Authorization rejection, cancelled confirmation, or a fully rolled-back transaction |
| **Related FR** | FR-INSTL-001…009, FR-AZ-I01…I04, FR-ATK-005 |
| **Related BR** | BR-002, BR-005, BR-007, BR-016 |

## 2. Main flow — 중지 (disable)

| Step | Actor | Action | System response |
|------|-------|--------|-----------------|
| 1 | Operator | Selects one institution and clicks 중지 | Selection validated **first** (FR-INST-009, FR-INSTL-007) |
| 2 | System | Asks for confirmation | Dialog names the institution and states the effect on its API access |
| 3 | Operator | Confirms | Operator role verified server-side (FR-AZ-I01/I02) |
| 4 | System | Sets 사용여부 = `N` | Written to **`FT_FTIS_INFO`** — the record the list reads (FR-INSTL-001) |
| 5 | System | Writes a history record | Actor, timestamp, before/after (FR-INSTL-003, FR-AZ-I04) |
| 6 | System | Refreshes the list | The institution now shows 미사용 |
| 7 | System | Enforces the state | The institution can no longer authenticate to the send API (FR-INSTL-009) |

## 3. Alternative flows

### 3.1 A-1: 재사용 (re-enable)
- Branches at Step 1. Operator selects a 미사용 institution and clicks 재사용.
- Result: 사용여부 = `Y` with confirmation and history, mirroring Steps 2–7 (FR-INSTL-002). **This path does not exist in the legacy** — the screen only ever sent `N`, and re-enabling required the edit popup (D-I18).

### 3.2 A-2: 삭제 (logical delete)
- Branches at Step 1. Operator clicks 삭제.
- Step 2': the confirmation dialog shows the **dependent-record counts** — 발신번호 and 문자발송내역 — that will be affected (FR-INSTL-008).
- Step 4': the institution is **flagged deleted and retained**, not physically removed (FR-INSTL-004). Its 발신번호 are deactivated with `KKB_DPNO_HIS` entries (`ACN='D'`) preserved (FR-INSTL-005).
- Step 5': a deletion history record is written — something the legacy wrote for 발신번호 but never for the institution itself (D-I7).
- Step 6': the institution disappears from the default list; its 문자발송내역 remain intact and attributable.

### 3.3 A-3: Cancel at confirmation
- At Step 2 or 2'. Operator declines.
- Result: no write of any kind.

## 4. Exception flows

### 4.1 E-1: Disable appears to succeed but changes nothing
- At Step 4. **Regression guard for D-I1.**
- The legacy updated `FT_INST_INFO` while the list read `FT_FTIS_INFO`, so the operator saw "정상적으로 처리되었습니다" and the institution stayed 사용 — and, more seriously, kept its API access.
- Action: the new implementation writes the record the list reads, and TC-I003-01 asserts the round trip.

### 4.2 E-2: Confirmation shown before the selection is checked
- At Step 1. **Regression guard for D-I16.**
- The legacy called `confirm('정말 삭제하시겠습니까? …복구할 수 없습니다.')` *before* testing the selection, so an operator could confirm an irreversible delete and only then be told nothing was selected.
- Action: selection is validated first; the dialog appears only for a valid target.

### 4.3 E-3: Sub-operation fails during delete
- At Step 4'/5'. A history insert or a 발신번호 update fails.
- Action: the **entire** operation rolls back (FR-INSTL-006). **Regression guard:** the legacy loop tested `isError(idoOut1)` — the institution-delete result — instead of `idoOut2`, so a failed history insert was ignored and the transaction committed anyway (D-I8).

### 4.4 E-4: Non-operator calls the disable or delete service
- At Step 3, or by crafting a request.
- Action: reject with 403. **Regression guard:** the legacy gated both on `<login>Y</login>` alone, so **any authenticated user could delete any institution** (D-I2).

### 4.5 E-5: Disabled institution attempts to send
- After Step 7.
- Action: the send API rejects the institution's 인증키 (FR-INSTL-009). This is the control 사용여부 exists to provide, and D-I1 meant it was unenforced from this screen.

### 4.6 E-6: Delete of an institution with live dependents
- At Step 2'.
- Action: counts are shown before confirmation (FR-INSTL-008). Retention and blocking policy per AMB-I05 — history retained, new activity blocked.

## 5. NFR mapping

| NFR-ID | Application |
|--------|-------------|
| NFR-PERF-I03 | Steps 4–6: P95 < 1 s |
| NFR-SEC-AUTHZ-I01 | Step 3: operator role enforced server-side |
| NFR-SEC-PII-I01 | Step 5': `RGSR_NM` in 발신번호 history remains encrypted |
| NFR-OPS-AUDIT-I01 | Steps 5 / 5': audit record with before/after |
| NFR-OPS-AUDIT-I02 | Retention per CONST-LEGAL-02 (term `[보류]`, OI-02) |
| NFR-OPS-I01/I02 | Step 6: runtime cache reflects the state change or alerts |

## 6. Screen sketch

```
┌─ 확인 ──────────────────────────────────────────────────────┐
│  이용기관 'ABC사 (K0A123)' 을(를) 삭제하시겠습니까?          │
│                                                              │
│  · 등록된 발신번호        3 건 → 사용 중지 처리              │
│  · 문자발송내역      12,480 건 → 보존 (조회 가능)            │
│                                                              │
│  삭제 후 해당 기관은 발송 API 를 사용할 수 없습니다.         │
│                              [ 삭제 ]   [ 취소 ]             │
└─────────────────────────────────────────────────────────────┘
```

## 7. Test scenarios

| Test ID | Case | Input | Expected |
|---------|------|-------|----------|
| TC-I003-01 | **D-I1 regression** | Disable an institution, then re-query the list | 사용여부 reads `N`. The legacy left it `Y` because the update hit `FT_INST_INFO` |
| TC-I003-02 | **D-I1 regression** | Disable, then call the send API with that institution's 인증키 | Rejected (FR-INSTL-009) |
| TC-I003-03 | **D-I18 regression** | Re-enable a 미사용 institution from the list | 사용여부 returns to `Y` without opening the edit popup |
| TC-I003-04 | **D-I16 regression** | Click 삭제 with no row selected | No confirmation dialog appears; the action is simply unavailable |
| TC-I003-05 | **D-I7 regression** | Delete an institution | Record retained and flagged, not physically removed |
| TC-I003-06 | **D-I7 regression** | Delete, then query 문자발송내역 for that 기관코드 | History intact and still attributable — not orphaned |
| TC-I003-07 | **D-I7 regression** | Delete, then read the deletion history | An institution-level deletion record exists with actor and timestamp |
| TC-I003-08 | **D-I8 regression** | Force the 발신번호 history insert to fail during delete | Whole transaction rolled back; the institution is **not** deleted |
| TC-I003-09 | **D-I2 regression** | Delete service called by a non-operator | 403; nothing deleted |
| TC-I003-10 | **D-I2 regression** | Disable service called by a non-operator | 403; no state change |
| TC-I003-11 | Confirmation content | Click 삭제 on an institution with 발신번호 and 문자내역 | Dialog shows both dependent counts (FR-INSTL-008) |
| TC-I003-12 | Cancel | Decline the confirmation | No write of any kind |
| TC-I003-13 | Sender-number cascade | Delete an institution with 3 발신번호 | All 3 deactivated; 3 `KKB_DPNO_HIS` rows with `ACN='D'` |
| TC-I003-14 | Encrypted history | Inspect the `KKB_DPNO_HIS` rows written | `DP_NO` and `RGSR_NM` encrypted at rest (NFR-SEC-PII-I01) |
| TC-I003-15 | Soft-delete exclusion | Delete, then run the default list query | Institution absent (FR-INSTL-005) |
| TC-I003-16 | Disable confirmation | Click 중지 | Confirmation required — the legacy disabled with no prompt at all (D-I16) |
| TC-I003-17 | Audit content | Disable, re-enable and delete | Three audit records, each with actor, timestamp, before/after |
| TC-I003-18 | Idempotence | Disable an already-disabled institution | Handled cleanly; no duplicate or contradictory history |
