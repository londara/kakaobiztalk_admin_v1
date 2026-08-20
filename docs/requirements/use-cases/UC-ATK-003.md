# Use Case UC-ATK-003: Compose and send a batch of AlimTalk messages

> **REQ-ID**: UC-ATK-003
> **Version**: 1.0
> **Predecessor**: [REQUIREMENTS-SPEC-ALIMTALK.md](../REQUIREMENTS-SPEC-ALIMTALK.md)
> **Legacy origin**: screen 61 다건 발송 tab — `biztalk_admin_61.js` `addMsgData` / `validateMultiRequiredFields`; contract `IMO.ADV_KKO_AT_SEND_M` (**never called by any code in the repository**); legacy send loop `biztalk_admin_50_s001_act.jsp` (`> 1000` branch)

---

## 1. Scenario overview

| Item | Content |
|------|---------|
| **Primary user** | Internal operator |
| **Precondition** | Authenticated session with an operator role and send authorization |
| **Trigger** | Operator selects 다건 발송 |
| **Success outcome** | A batch conforming to `ADV_KKO_AT_SEND_M` — every item carrying an explicit `order` — is despatched through the batch interface, within the configured cap, and audited as one operation |
| **Failure outcome** | Rejected before despatch, or a partial outcome reported accurately as partial with the boundary named |
| **Related FR** | FR-ATC-004/005/006/007, FR-ATS-013/014, FR-ATS-005…011, FR-ATH-001…003 |
| **Related BR** | BR-002, BR-007, BR-016 |

> **Two structural defects meet in this use case.** The batch contract declares a mandatory `order` on every `msg_data` item, which screen 61 never emits (D-A3); and the batch contract has **never been called by any code in this repository** — screen 50's high-volume branch loops over the *single-send* interface with a mutated request object instead (D-A33). Each defect is the reason the other went unnoticed: no caller meant no validation, and no validation meant the missing field never surfaced.

## 2. Main flow

| Step | Actor | Action | System response |
|------|-------|--------|-----------------|
| 1 | Operator | Selects 다건 발송 | Session, operator role and send authorization verified (FR-AZ-A01, FR-AZ-A03) |
| 2 | Operator | Selects an 이용기관 and enters 거래고유번호 | Scope derived from the session (FR-AZ-A02); `tran_id` bounded at 10 (FR-ATC-005) |
| 3 | Operator | Adds a 메시지 데이터 item | Each item collects recipient, caller ID, template, message, buttons and fallback |
| 4 | Operator | Selects a template per item | From the institution's registry (FR-ATT-001, FR-ATT-004) |
| 5 | Operator | Optionally sets 예약발송시간 per item | Available for batch as well as single send (FR-ATC-007, AMB-A04) |
| 6 | Operator | Repeats for further items | Running item count shown against the configured cap (FR-ATS-014) |
| 7 | System | Assigns an explicit `order` to each item | Association never depends on array position across the interface (FR-ATC-004) |
| 8 | System | Validates every item | Contract lengths per item; recipients format-validated and de-duplicated (FR-ATC-005, FR-ATC-012) |
| 9 | System | Validates each message against its template | Per item, server-side (FR-ATV-001) |
| 10 | System | Enforces the batch cap | Server-side, with the limit stated in the UI (FR-ATS-014) |
| 11 | System | Presents a confirmation | Item count, distinct templates, distinct caller IDs, total recipients (NFR-USE-A04) |
| 12 | Operator | Confirms | — |
| 13 | System | Checks `(is_cd, tran_id)` for duplication | Batch treated as one operation for dedupe (FR-ATS-009, FR-ATS-010) |
| 14 | System | Despatches via the **batch interface** | `msg_data` + `order` through `ADV_KKO_AT_SEND_M` (FR-ATS-013) |
| 15 | System | Records and reports the outcome | Per-item outcomes rolled into one result; partial reported as partial (NFR-OPS-A02, FR-ATH-001) |
| 16 | System | Writes the audit record | One record for the batch, carrying item and recipient counts (FR-AZ-A04, NFR-OPS-AUDIT-A01) |

## 3. Alternative flows

### 3.1 A-1: Operator removes an item
- Branches at Step 6. `order` values are re-assigned so the sequence stays contiguous (FR-ATC-004).

### 3.2 A-2: The batch exceeds the cap
- At Step 10. Rejected with the cap stated, before despatch (FR-ATS-014). **Regression guard:** no cap existed — the legacy chunked at 1000 per vendor call as an implementation detail, invisible to the operator (NFR-SCALE-A01, AMB-A03).

### 3.3 A-3: Items share a template but differ in recipient
- At Step 4. Supported; each item carries its own recipient, caller ID and reservation time.

### 3.4 A-4: Partial success across items
- At Step 15. Reported as partial, naming which `order` values succeeded and which did not (NFR-OPS-A02).

## 4. Exception flows

### 4.1 E-1: An item is missing `order`
- At Step 7.
- Action: cannot occur — `order` is assigned by the system, and contract validation rejects a payload without it (FR-ATC-004, FR-ATC-001). **Regression guard:** the legacy composer emitted `msg_data` items with **no `order` at all**, though `ADV_KKO_AT_SEND_M` declares it on every item (D-A3).

### 4.2 E-2: The despatch uses the single-send interface in a loop
- At Step 14.
- Action: prevented; the batch interface is used (FR-ATS-013). **Regression guard:** screen 50's `> 1000` branch mutated and re-executed a single `imoIn` object against `ADV_KKO_AT_SEND` 1000 recipients at a time; `ADV_KKO_AT_SEND_M` was never called by any code in the repository (D-A33).

### 4.3 E-3: A batch aborts partway and reports a flat failure
- At Step 15.
- Action: prevented (NFR-OPS-A02). **Regression guard:** with more than 1000 recipients and a single malformed number, the legacy delivered batch 1, threw inside the loop so batches 2..n never ran, and reported an error — partial delivery presented as total failure, with no record of where it stopped (D-A26).

### 4.4 E-4: Batch reservation is requested
- At Step 5.
- Action: honoured per item (FR-ATC-007, FR-ATS-012). **Regression guard:** screen 61's batch tab collected **no `reqdate`** although the contract declares it on every `msg_data` item (D-A14), and screen 50 overwrote it with `now()` regardless (D-A32).

### 4.5 E-5: `receiver_number` shape differs between single and batch
- At Steps 7–8.
- Action: one canonical representation across both (FR-ATC-006). **Regression guard:** four shapes existed for one field — an array in screen 61 single send, a scalar string per batch item, a stringified array in screen 50's `<= 1000` branch, and a raw array in its `> 1000` branch — against a contract declaring one field of length 20000 (D-A10, AMB-A06).

### 4.6 E-6: A batch item's message diverges from its template
- At Step 9.
- Action: the batch is rejected with the offending `order` and divergence point named (FR-ATV-002).

### 4.7 E-7: Validation passes with an empty batch
- At Step 8.
- Action: rejected (FR-ATS-006). **Regression guard:** `validateMultiRequiredFields()` filtered items by CSS visibility (`item.style.display !== 'none'`) — tying a business validity rule to a presentation attribute — and the JSP's hidden seed item was bound to handlers at load but never used (D-A23).

### 4.8 E-8: Message-type options differ between single and batch forms
- At Step 3.
- Action: one identical option set (FR-ATC-008). **Regression guard:** the JSP seed markup offered AT/FT while the JS-injected template offered AT/FT/AI — two versions of one control (D-A19).

## 5. NFR mapping

| NFR-ID | Application |
|--------|-------------|
| NFR-PERF-A03 | Steps 10–14: a batch at the cap acknowledged within 5 s |
| NFR-SCALE-A01 | Step 10: volume bounded server-side; chunking is not a user-visible limit |
| NFR-SEC-AUTHZ-A01 | Steps 1–2: authorization server-side |
| NFR-SEC-PII-A01 | Step 11: recipients masked in the confirmation |
| NFR-SEC-PII-A02 | Step 14: recipients never logged |
| NFR-SEC-TX-A01 | Steps 14–16: integrity and non-repudiation per item |
| NFR-OPS-A01 | Step 14: consistency between history and despatch |
| NFR-OPS-A02 | Step 15: partial reported as partial |
| NFR-OPS-AUDIT-A01 | Step 16: one audit record for the batch |
| NFR-USE-A04 | Step 11: one confirmation view |

## 6. Screen sketch

```
┌─ 다건 발송 ───────────────────────────────────────────────┐
│ 이용기관 [▼ ○○기관 ]   거래고유번호 [A2608180002] 10자     │
│ 메시지 데이터                          3 / 1000건          │
│ ┌ #1  order=1 ───────────────────────────────────[삭제]─┐ │
│ │ 수신 010-****-1234  발신 [▼0212345678]                │ │
│ │ 템플릿 [▼TMPL_0001] ✔  메시지 [__________] ✔ 일치     │ │
│ │ 예약 [20260819140000]   대체전송 [LMS ▼]              │ │
│ └────────────────────────────────────────────────────────┘ │
│ ┌ #2  order=2 ───────────────────────────────────[삭제]─┐ │
│ │ …                                                      │ │
│ └────────────────────────────────────────────────────────┘ │
│ [ + 메시지 데이터 추가 ]                                   │
│                                    [ 검증 ] [ 발송 ]       │
└────────────────────────────────────────────────────────────┘
```

## 7. Test scenarios

| Test ID | Case | Input | Expected |
|---------|------|-------|----------|
| TC-A003-01 | Normal batch | 3 items | Despatched via `ADV_KKO_AT_SEND_M`; one audit record |
| TC-A003-02 | **D-A3 regression** | Any batch | Every `msg_data` item carries `order` |
| TC-A003-03 | **D-A3 regression** | Remove item #2 of 3 | Remaining `order` values contiguous |
| TC-A003-04 | **D-A33 regression** | Trace outbound calls for a 2500-recipient batch | Batch interface used; no loop over the single-send interface |
| TC-A003-05 | **D-A26 regression** | 2500 recipients, 1 malformed | All valid delivered; outcome names the exclusion; no batch skipped |
| TC-A003-06 | **D-A14 regression** | Batch item with `reqdate` | Reservation honoured per item |
| TC-A003-07 | **D-A10 regression** | Compare single and batch payloads | `receiver_number` has one identical shape |
| TC-A003-08 | **D-A19 regression** | Compare message-type options across forms | Identical option sets |
| TC-A003-09 | **D-A23 regression** | Submit with zero items added | Rejected; validity not derived from CSS visibility |
| TC-A003-10 | **D-A7 regression** | Item field at bound and bound+1 | Accepted then rejected, per item |
| TC-A003-11 | Cap | Cap + 1 items | Rejected server-side with the cap stated |
| TC-A003-12 | Cap | Cap enforced when the client is bypassed | Rejected |
| TC-A003-13 | Idempotency | Re-submit the same batch `tran_id` | Rejected as duplicate; no second despatch |
| TC-A003-14 | Template scope | One item using another institution's template | Whole batch rejected, offending `order` named |
| TC-A003-15 | Caller ID | One item with an unregistered caller ID | Whole batch rejected, offending `order` named |
| TC-A003-16 | Partial outcome | Vendor fails items 2 and 5 of 6 | Reported as partial naming `order` 2 and 5 |
| TC-A003-17 | Audit | Any batch | One record with item count and recipient count |
