# Use Case UC-SND-004: Delete one or more sender numbers

> **REQ-ID**: UC-SND-004
> **Version**: 1.1 — write-path pass, 2026-08-20
> **Predecessor**: [REQUIREMENTS-SPEC-SENDERNO.md](../REQUIREMENTS-SPEC-SENDERNO.md) §1.5
> **Legacy origin**: screen 13 — `biztalk_admin_13_view.jsp` / `biztalk_admin_13.js` / `WSVC.biztalk_admin_10_d001` / `IDO.KKB_DPNO_LDGR_D001`, `IDO.KKB_DPNO_HIS_C001`

---

## 1. Scenario overview

| Item | Content |
|------|---------|
| **Primary user** | Internal operator |
| **Precondition** | Authenticated operator session; one or more sender numbers selected in UC-SND-001. **Until at least one is, 삭제 is unavailable** (FR-SNDD-010) |
| **Trigger** | Operator clicks 삭제 |
| **Success outcome** | Every selected number is logically deleted in one transaction, each with its own history record carrying the 사유 |
| **Failure outcome** | Unauthorized rejection, a number that no longer matches, or a rolled-back transaction — **never a silent no-op reported as success** |
| **Related FR** | FR-SNDD-001…011, FR-SND-012, FR-SNDH-001…003, FR-AZ-D01…D05 |
| **Related BR** | BR-002, BR-007 |

> **Scope note.** This flow contains the slice's most serious defect. In production today, deletion **matches nothing, changes nothing, and reports success** (D-S1) — see E-1. It also has no ownership verification, by PM ruling AMB-S01.

## 2. Main flow

| Step | Actor | Action | System response |
|------|-------|--------|-----------------|
| 1 | Operator | Selects one or more rows and clicks 삭제 | Operator role verified server-side (FR-AZ-D01/D02/D04); institution scope verified (FR-AZ-D03) |
| 2 | System | Opens the confirmation view | Lists **exactly** which numbers will be deleted — every selected row, including rows selected on a page no longer displayed (FR-SNDD-007, FR-SNDD-009) |
| 3 | Operator | Enters 사유 and confirms | 사유 is mandatory (FR-SNDD-006) |
| 4 | System | Resolves each selected row | By a server-resolvable key, never the displayed string (FR-SND-007) |
| 5 | System | Verifies every number matches a live row | A request matching nothing **fails with an explicit error** (FR-SNDD-002) |
| 6 | System | Marks each number deleted | Logical delete; the row is retained (FR-SNDD-001) |
| 7 | System | Writes one history record **per number** | Each carries that number alone, plus actor, timestamp, `ACN='D'` and 사유 (FR-SNDD-004, FR-SNDH-003) |
| 8 | System | Commits | All numbers succeed or none do (FR-SNDD-005, NFR-OPS-D01) |
| 9 | System | Writes the audit record | Actor, timestamp, institution, every number affected (FR-AZ-D05) |
| 10 | System | Closes the confirmation | The list re-queries at the current 이용기관 and page and the selection is cleared; deleted numbers no longer appear (FR-SND-012, FR-SNDD-003) |

## 3. Alternative flows

### 3.1 A-1: Single-number delete
- Steps 1–10 unchanged with one selection. Note this is the only path the legacy handled without corrupting history (D-S5).

### 3.2 A-2: Cancel
- At Step 3. Operator clicks 닫기. No write; the list is unchanged.

### 3.3 A-3: Re-register a deleted number later
- After Step 10. Covered by UC-SND-002 A-1 and FR-SNDD-008: permitted, with the earlier deletion still visible in history.

## 4. Exception flows

### 4.1 E-1: Delete matches nothing
- At Step 5.
- Action: **fail with an explicit error** (FR-SNDD-002).
- **Regression guard — the defect this requirement exists for (D-S1).** In the legacy, `biztalk_admin_10_l001_act.jsp` applies `RegexNameMasking.maskName()` to `DP_NO` before returning the list, so the grid holds `01********8` rather than the real number. `_gu.getCheckData()` reads that masked value, the delete request carries it, and `KKB_DPNO_LDGR_D001` runs `DELETE … WHERE IS_CD = :IS_CD AND decrypt(DP_NO) = :DP_NO`. Nothing matches. `DELETE` affecting zero rows is not an error, so no exception is raised, the history insert still runs, and the operator is shown "정상적으로 처리되었습니다". **The number remains live and remains valid for sending.**

### 4.2 E-2: Multi-number delete corrupts history
- At Step 7. Three numbers selected.
- Action: three history records, each holding one number. **Regression guard:** the legacy loop deleted each number `t` individually but built the history insert with `putAll(input)`, where `DP_NO` is still the **comma-joined list** the client sent. Every history row therefore stored `ENCRYPT("010…,010…,010…")` as though the whole CSV string were a single number (D-S5).

### 4.3 E-3: Failure part-way through a multi-number delete
- At Step 8. The third of five deletions fails.
- Action: the whole transaction rolls back. **Regression guard:** the legacy executed each statement independently with no transaction, so a mid-loop failure left some numbers deleted, some not, and history matching neither (D-S6).

### 4.4 E-4: History write fails
- At Step 7.
- Action: the transaction rolls back. **Regression guard:** after the history insert the legacy checked `idoOut1` — the *delete* statement's result — instead of `idoOut2`, so a failed history write was swallowed and the deletion still committed (D-S7).

### 4.5 E-5: Non-operator calls the delete service
- At Step 1.
- Action: reject with 403 (FR-AZ-D01/D04). **Regression guard:** delete was the **least** guarded operation in the slice — register at least made a browser-side manager check via `biztalk_admin_00_l003`; the delete button made none at all, and the service carried `<login>Y</login>` only (D-S2).

### 4.6 E-6: Delete against another institution's numbers
- At Step 1. A crafted request supplies another institution's `IS_CD` and numbers.
- Action: reject with 403 (FR-AZ-D03). **Regression guard:** `IS_CD` and the number list came straight from the request body into the delete loop with no scope check (D-S3).

### 4.7 E-7: 사유 omitted
- At Step 3. A direct call omits `REASON`.
- Action: rejected (FR-SNDD-006). The 사유 is the only record of *why* a number was withdrawn.

### 4.8 E-8b: Selected rows are no longer on screen **(v1.1)**
- At Steps 1–2. The operator selects two numbers on page 1, pages to page 3, and presses 삭제.
- Action: the confirmation enumerates **both** selected numbers, not the rows currently displayed, and the deletion acts on exactly that enumerated set (FR-SNDD-009).
- **Why this flow exists.** It is D-S1's defect class reached by a different route. D-S1 deleted against a *masked* value; server-side paging — which this project introduced to fix D-S14 — makes it possible to delete against a *stale* selection. In the legacy the whole result set lived in the browser, so "selected" and "visible" could never diverge. **A fix for one defect created the opportunity for another, and the requirement closes it explicitly rather than by convention.**

### 4.9 E-9b: 삭제 pressed with nothing selected **(v1.1)**
- At Step 1.
- Action: the control is unavailable; no request is issued (FR-SNDD-010). **Regression guard:** the legacy button opened popup 13 regardless, so an empty selection produced a delete request with an empty number list — which, given D-S1, also reported success.

### 4.8 E-8: Deleted number still accepted by the send path
- After Step 10.
- Action: the number must be rejected as a caller ID (FR-SNDD-003). **This is an open design risk, not a solved one** — see CONFLICT-S02 and AMB-S05. A status column added by this project is invisible to the legacy send path, which would keep honouring the number. Verified at cutover, not assumed.

## 5. NFR mapping

| NFR-ID | Application |
|--------|-------------|
| NFR-PERF-D02 | Steps 4–10: P95 < 1 s |
| NFR-PERF-D03 | 100-number delete within 5 s as one transaction |
| NFR-SEC-AUTHZ-D01 | Step 1: operator role enforced server-side |
| NFR-SEC-TENANT-D01 | Step 1: institution scope validated server-side |
| NFR-SEC-INJ-D01 | Steps 5–7: bound parameters |
| NFR-SEC-LOG-D01 | Steps 6–7: 발신번호 never logged in clear |
| NFR-OPS-D01 | Step 8: explicit transaction with rollback |
| NFR-OPS-D02 | Step 5: **no success reported without a state change** — the D-S1 guarantee |
| NFR-OPS-AUDIT-D01 | Step 9: actor, timestamp, target, outcome |
| NFR-USE-D01 | Steps 1–3: two steps from list to confirmed deletion |

## 6. Screen sketch

Legacy screen 13's layout, in a modal dialog rather than a `window.open` popup ([ADR-SND-020](../../design/adr/ADR-SND-020-write-dialog-presentation.md)). The 삭제번호 block is the enumeration FR-SNDD-009 requires — it is the operator's last chance to see the whole set, so it lists every selected number and never abbreviates to a count.

```
┌─ 발신번호 제거 ─────────────────────────────────────────────┐
│ 이용기관코드 [K0xxxx  (읽기전용)]                           │
│ 삭제번호     [0212345678                                  ] │
│  (선택 2건)  [15881234                                    ] │
│ 사유*        [                                            ] │
│                      [ 삭제 ]  [ 닫기 ]                     │
└─────────────────────────────────────────────────────────────┘
```

The list screen carries the same count next to 삭제 while rows are selected, so a selection made three pages ago is never invisible (FR-SNDD-009).

## 7. Test scenarios

| Test ID | Case | Input | Expected |
|---------|------|-------|----------|
| TC-S004-01 | **D-S1 regression** | Delete one number selected from the list, then re-query | The number is **gone from the list** — the deletion actually took effect |
| TC-S004-02 | **D-S1 regression** | Delete a number that does not exist | Explicit error; **never** a success message |
| TC-S004-03 | **D-S1 regression** | Delete via a request carrying a display-formatted value | Rejected; no history record written |
| TC-S004-04 | **D-S1 regression** | Inspect the history after any delete | The stored number is the real number, not a masked pattern |
| TC-S004-05 | **D-S5 regression** | Delete 3 numbers at once | 3 distinct history records, each holding exactly one number |
| TC-S004-06 | **D-S5 regression** | Inspect each history row from TC-S004-05 | No row contains a comma-joined list |
| TC-S004-07 | **D-S6 regression** | Force a failure on the 3rd of 5 deletions | All 5 remain; no partial deletion; no orphan history |
| TC-S004-08 | **D-S7 regression** | Force the history insert to fail | Deletion rolled back |
| TC-S004-09 | **D-S2 regression** | Delete service called by a non-operator | 403; nothing deleted |
| TC-S004-10 | **D-S3 regression** | Delete another institution's numbers | 403; nothing deleted |
| TC-S004-11 | Logical delete | Delete a number, then inspect the ledger | The row is retained and marked deleted, not physically removed |
| TC-S004-12 | Exclusion | After deletion | The number is absent from the list and does not block re-registration as a live duplicate |
| TC-S004-13 | **CONFLICT-S02** | After deletion, attempt a send using that number as caller ID | Rejected. Verified at cutover against the legacy send path |
| TC-S004-14 | Mandatory reason | Direct call omitting `REASON` | 400 validation error |
| TC-S004-15 | Reason stored | Any deletion | 사유 present in the history record |
| TC-S004-16 | Confirmation content | Select 2 numbers and click 삭제 | The confirmation lists exactly those 2 numbers |
| TC-S004-17 | Re-registration | Register a previously deleted number | Accepted; prior deletion still visible in history |
| TC-S004-18 | Bulk performance | Delete 100 numbers | Completes within 5 s as one transaction |
| TC-S004-19 | Connection release | 200 consecutive delete operations | No connection leak; pool stable |
| TC-S004-20 | Audit content | Any deletion | Audit record holds actor, timestamp, institution, every number affected |
| TC-S004-21 | **FR-SNDD-009 — cross-page selection** | Select 1 row on page 1, page to 3, press 삭제 | The confirmation lists the page-1 number; exactly that number is deleted |
| TC-S004-22 | **FR-SNDD-009 — enumerated set is the deleted set** | Select 3, delete, then diff the confirmation's list against the history rows | Identical sets, no member added or dropped |
| TC-S004-23 | FR-SNDD-010 | Nothing selected | 삭제 unavailable; no request issued |
| TC-S004-24 | FR-SNDD-011 | Select rows, then change 이용기관 | Selection cleared; the count returns to 0 |
| TC-S004-25 | FR-SNDD-011 | Select rows, then page within the same institution | Selection retained; the count is unchanged |
| TC-S004-26 | FR-SND-012 | Delete while on page 2 | List re-queries at page 2; selection empty |
| TC-S004-27 | CONST-BIZ-D04 | Logically delete an institution, then read its numbers | Rows remain in the ledger; sending is refused by the institution check, not by their absence |
