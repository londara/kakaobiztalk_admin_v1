# Use Case UC-SND-002: Register a new sender number

> **REQ-ID**: UC-SND-002
> **Version**: 1.1 — write-path pass, 2026-08-20
> **Predecessor**: [REQUIREMENTS-SPEC-SENDERNO.md](../REQUIREMENTS-SPEC-SENDERNO.md) §1.5
> **Legacy origin**: screen 12 — `biztalk_admin_12_view.jsp` / `biztalk_admin_12.js` / `WSVC.biztalk_admin_12_c001` / `IDO.KKB_DPNO_LDGR_C001`, `IDO.KKB_DPNO_HIS_C001`

---

## 1. Scenario overview

| Item | Content |
|------|---------|
| **Primary user** | Internal operator |
| **Precondition** | Authenticated operator session; an institution selected in UC-SND-001. **Until one is, 등록 is unavailable** (FR-SNDD-010) |
| **Trigger** | Operator clicks 등록 |
| **Success outcome** | A sender number is registered to the institution, with actor, timestamp and 사유 recorded in history within one transaction |
| **Failure outcome** | Validation rejection, duplicate rejection, or a rolled-back transaction — **with the form still open and the input intact** (FR-SNDC-014) |
| **Related FR** | FR-SNDC-001…014, FR-SND-012, FR-SNDH-001…003, FR-AZ-D01…D05 |
| **Related BR** | BR-002, BR-007 |

> **Scope note.** Under PM ruling AMB-S01 this flow contains **no ownership verification**. The number is registered on the operator's assertion alone. See RESIDUAL-S01 — the compensating controls are the authorization steps below and the global-uniqueness check at Step 6.

## 2. Main flow

| Step | Actor | Action | System response |
|------|-------|--------|-----------------|
| 1 | Operator | Clicks 등록 | Operator role verified server-side (FR-AZ-D01/D02); institution scope verified (FR-AZ-D03) |
| 2 | System | Loads institution context | Returns **only** 기관코드 and 기관명 (FR-SNDC-002). Both are read-only on the form and come from the list's selection, never from operator input (FR-SNDC-012) |
| 3 | System | States the rules | The three rules the legacy screen displayed, and only rules that are actually enforced (FR-SNDC-013) |
| 4 | Operator | Enters 발신번호, 설명, 사유 | Client-side checks are for responsiveness only. **사유 is mandatory** (FR-SNDC-011) |
| 5 | Operator | Clicks 등록 | **Server** validates every rule (FR-SNDC-003) |
| 6 | System | Validates the number | Numeric (FR-SNDC-005); 8–11 digits, 12 for 030/050, exactly 8 for 15xx/16xx (FR-SNDC-010); not a special or emergency number (FR-SNDC-006, from configuration per CONST-BIZ-D03) |
| 7 | System | Checks uniqueness | Rejected if the number exists for **any** institution (FR-SNDC-004) |
| 8 | System | Validates required fields and lengths | 사유 present (FR-SNDC-011); 설명 ≤ 200, 사유 ≤ 100 (FR-SNDC-007) |
| 9 | System | Writes ledger and history | One transaction; 등록자/최종수정자 from the session (FR-SNDC-009), stored encrypted per AMB-S09 ruling B; history `ACN='C'` with 사유 (FR-SNDH-001) |
| 10 | System | Commits | A history-write failure fails the whole registration (FR-SNDC-008) |
| 11 | System | Writes the audit record | Actor, timestamp, institution, number (FR-AZ-D05) |
| 12 | System | Closes the form | The list re-queries at the current 이용기관 and page, and the selection is cleared (FR-SND-012) |

## 3. Alternative flows

### 3.1 A-1: Re-register a previously deleted number
- Branches at Step 7. The number exists only in `KKB_DPNO_ARCV`, not in the ledger.
- Result: permitted. A new live record is created and the earlier deletion remains visible in history (FR-SNDD-008). The uniqueness check sees only live rows, which is what makes this work without a special case (ADR-SND-017).

### 3.2 A-2: Cancel
- At any step. Operator clicks 닫기. No write; the list is unchanged and its selection is untouched.

## 4. Exception flows

### 4.1 E-1: Validation bypassed by a direct service call
- At Step 5. A crafted request supplies an empty or malformed 발신번호.
- Action: rejected server-side (FR-SNDC-003). **Regression guard:** the legacy's client validation tested `#ATK`, `#BRNO`, `#IS_ENGNM` and `#AUTH_NO` — **none of which exist in the JSP**. `$(missing).val()` returns `undefined`, and `undefined == ""` is false, so every check passed silently. `validationEngine` was initialised in `onload` but never invoked, so the declared `notNull,number,htmlTag` rules never ran either (D-S11).

### 4.2 E-2: Non-numeric sender number
- At Step 6. Input `abcdefgh`.
- Action: rejected. **Regression guard:** `isValidDpNo()` checked only length and prefix, never that the value was numeric, so an 8-character alphabetic string was accepted and stored (D-S13).

### 4.3 E-3: Special or emergency number
- At Step 6. Input `112`, `114`, `119` or `1335`.
- Action: rejected. **Regression guard:** the registration screen tells the user these are barred, and the rule exists in **no** layer — not the JS, not `isValidDpNo()`, not the contract, not the query (D-S12). Note `112` is also shorter than the 8-digit minimum, but `1335` is not, so the length rule does not cover this.

### 4.4 E-4: Number already registered to another institution
- At Step 7.
- Action: rejected as a duplicate. **Regression guard:** `KKB_DPNO_LDGR_L001` filters on `IS_CD` **and** `DP_NO`, so the legacy duplicate check saw only the requesting institution's own numbers and permitted cross-institution duplicates (D-S9). Under RESIDUAL-S01 this check is the primary defence against one institution claiming another's number.

### 4.5 E-5: History write fails after the ledger insert
- At Step 10.
- Action: the transaction rolls back; no orphan ledger row. **Regression guard:** the legacy checked `idoOut1` — the *previous* statement's result — instead of `idoOut2` after the history insert, so a failed history write was silently swallowed and the registration still committed (D-S7).

### 4.6 E-6: Over-length description or reason
- At Step 8. A direct call supplies 5,000 characters.
- Action: rejected. **Regression guard:** `maxlength` in HTML was the only limit; the contract declared no length for `DSCP` or `REASON` (D-S15).

### 4.7 E-7: Non-operator calls the create service
- At Step 1.
- Action: reject with 403 (FR-AZ-D01/D02). **Regression guard:** the legacy role check ran in the browser and the service itself carried `<login>Y</login>` only (D-S2).

### 4.8 E-8: 사유 omitted **(v1.1)**
- At Step 8. The form leaves 사유 empty, or a direct call omits it.
- Action: rejected (FR-SNDC-011).
- **Not a regression guard — a deliberate parity break.** The legacy screen rendered the field and enforced nothing, because its client validation tested elements that did not exist (D-S11), so an empty 사유 was accepted and stored. Under the AMB-S10 ruling it is refused. Recorded so QA does not file the rejection as a parity defect.

### 4.9 E-9: Registration aimed at an institution the form did not select **(v1.1)**
- At Step 2. The form's 이용기관 fields are read-only, so this can only arrive as a crafted request body.
- Action: the institution is resolved from the session, not the body; a mismatch is rejected with 403 and the attempt is audited (FR-SNDC-012, FR-AZ-D03).
- **Why it is worth its own flow.** The legacy popup received `IS_CD` from its opener and put it straight into the insert — the write-path twin of D-S3, which was recorded against the read path only.

### 4.10 E-10: Rejected registration loses the operator's input **(v1.1)**
- At Steps 6–8, on any rejection.
- Action: the form stays open with every entered value intact and the offending field named (FR-SNDC-014, NFR-USE-D02). Re-entering an 11-digit number because the 사유 was too long is not an acceptable outcome.

## 5. NFR mapping

| NFR-ID | Application |
|--------|-------------|
| NFR-PERF-D02 | Steps 5–12: P95 < 1 s |
| NFR-SEC-AUTHZ-D01 | Step 1: operator role enforced server-side |
| NFR-SEC-TENANT-D01 | Steps 1–2: institution scope validated server-side |
| NFR-SEC-INJ-D01 | Steps 7, 9: bound parameters |
| NFR-SEC-LOG-D01 | Step 9: 발신번호 never logged in clear |
| NFR-SEC-PII-D01 | Step 9: actor identity stored consistently — `ENCRYPT`ed email per AMB-S09 ruling B (D-S16) |
| NFR-OPS-D01 | Steps 9–10: explicit transaction with rollback |
| NFR-OPS-D02 | Step 10: no success reported without a committed change |
| NFR-USE-D02 | Steps 6–8: errors name the field and the rule, not "등록중 오류 발생" |

## 6. Screen sketch

Field set, order and the three stated rules are the legacy screen 12 layout, unchanged. What changes is the container — a modal dialog rather than a `window.open` popup ([ADR-SND-020](../../design/adr/ADR-SND-020-write-dialog-presentation.md)) — and that 사유 is now marked and enforced as mandatory.

```
┌─ 발신번호 등록 ─────────────────────────────────────────────┐
│ 8~11자리 번호여야 합니다. (030, 050 은 12자리까지)          │
│ 112, 114, 1335 과 같은 특수번호는 등록할 수 없습니다.       │
│ 15xx, 16xx 대표번호는 8자리인 경우에만 등록 가능합니다.     │
├─────────────────────────────────────────────────────────────┤
│ 이용기관코드 [K0xxxx  (읽기전용)]  이용기관명 [○○기관     ] │
│ 발신번호*    [____________]                                 │
│ 설명         [                                            ] │
│ 사유*        [                                            ] │
│                      [ 등록 ]  [ 닫기 ]                     │
└─────────────────────────────────────────────────────────────┘
```

Every one of the three lines above is now enforced server-side (FR-SNDC-013). In the legacy, the middle line was true of no layer at all (D-S12) — the screen told the operator a rule the system did not have.

## 7. Test scenarios

| Test ID | Case | Input | Expected |
|---------|------|-------|----------|
| TC-S002-01 | Normal register | Valid unused 10-digit number | Registered; visible in UC-SND-001 |
| TC-S002-02 | **D-S11 regression** | Direct call with an empty 발신번호 | 400 validation error |
| TC-S002-03 | **D-S11 regression** | Direct call omitting 발신번호 entirely | 400 validation error |
| TC-S002-04 | **D-S13 regression** | 발신번호 = `abcdefgh` | 400 validation error |
| TC-S002-05 | **D-S12 regression** | 발신번호 = `1335` | Rejected as a special number |
| TC-S002-06 | **D-S12 regression** | 발신번호 = `114`, `112`, `119` | Rejected for each |
| TC-S002-07 | **D-S9 regression** | Register a number already held by **another** institution | Rejected as duplicate |
| TC-S002-08 | Duplicate, same institution | Register a number the institution already holds | Rejected as duplicate |
| TC-S002-09 | **D-S7 regression** | Force the history insert to fail | Registration rolled back; no ledger row |
| TC-S002-10 | **D-S15 regression** | 설명 of 5,000 chars via a direct call | 400 validation error |
| TC-S002-11 | **D-S15 regression** | 사유 of 5,000 chars via a direct call | 400 validation error |
| TC-S002-12 | **D-S18 regression** | Inspect the institution-context response | Contains 기관코드 and 기관명 only; **no 인증키** |
| TC-S002-13 | **D-S2 regression** | Create service called by a non-operator | 403; nothing created |
| TC-S002-14 | **D-S3 regression** | Create against another institution's `IS_CD` | 403; nothing created |
| TC-S002-15 | Length rule 030/050 | `05012345678901` (14 digits) | Rejected; `050123456789` (12) accepted |
| TC-S002-16 | Length rule 15xx/16xx | `15881234` (8) accepted; `158812345` (9) rejected | As stated |
| TC-S002-17 | Length rule general | 7 digits rejected; 12 digits rejected for a 02 number | As stated |
| TC-S002-18 | Session-derived actor | Create with a forged 등록자 in the body | Session identity stored; body value ignored |
| TC-S002-19 | History content | Any registration | One history row, `ACN='C'`, correct number, actor, 사유 |
| TC-S002-20 | Re-register after delete | Register a logically deleted number | Accepted; prior deletion still in history |
| TC-S002-21 | Rollback | DB error mid-save | No partial record persisted |
| TC-S002-22 | Connection release | 200 consecutive registrations | No connection leak; pool stable |
| TC-S002-23 | **AMB-S10 / parity break** | Register with 사유 empty, via form and via direct call | 400 naming 사유. **Asserts a rejection the legacy accepted** |
| TC-S002-24 | FR-SNDC-012 | 등록 with no 이용기관 selected | The control is unavailable; no request is issued |
| TC-S002-25 | FR-SNDC-012 | Direct call whose body names another institution | 403; nothing created; the attempt audited |
| TC-S002-26 | FR-SNDC-013 | Compare the rules the screen states with the rules the server enforces | Sets are identical in both directions |
| TC-S002-27 | FR-SNDC-014 | Submit an 11-digit number with a 5,000-char 사유, then read the form | Form still open, 발신번호 still present, error names 사유 |
| TC-S002-28 | FR-SND-012 | Register while on page 2 of the list | List re-queries at 이용기관 + page 2; selection empty |
| TC-S002-29 | CONST-BIZ-D03 | Start with an empty or unreadable barred-number list | **Startup fails.** The rule is never silently absent (D-S12's failure mode) |
| TC-S002-30 | CONST-BIZ-D03 | Add a number to the configured list without rebuilding | It is barred on the next start; 112/114/119/1335 remain barred regardless of configuration |
