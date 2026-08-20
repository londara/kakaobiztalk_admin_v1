# Use Case UC-ATK-001: Compose a single AlimTalk message against a registered template

> **REQ-ID**: UC-ATK-001
> **Version**: 1.0
> **Predecessor**: [REQUIREMENTS-SPEC-ALIMTALK.md](../REQUIREMENTS-SPEC-ALIMTALK.md)
> **Legacy origin**: screen 61 — `biztalk_admin_61_view.jsp` / `biztalk_admin_61.js` / `WSVC.biztalk_admin_61` (`actUseYn=N`); consumer contract `IMO.ADV_KKO_AT_SEND`; registry `IDO.KKB_MSG_TMPL_L001/L002`

---

## 1. Scenario overview

| Item | Content |
|------|---------|
| **Primary user** | Internal operator |
| **Precondition** | Authenticated session with an operator role (inherited from the 로그인 slice) |
| **Trigger** | Operator opens 카카오 알림톡 발송 and selects 단건 발송 |
| **Success outcome** | A payload that conforms to `ADV_KKO_AT_SEND` — correct field names, all lengths within contract bounds — is composed and ready to send |
| **Failure outcome** | Composition is blocked with the offending field and violated rule named |
| **Related FR** | FR-ATC-001…013, FR-ATT-001…003, FR-AZ-A01/A02 |
| **Related BR** | BR-002, BR-007 |

## 2. Main flow

| Step | Actor | Action | System response |
|------|-------|--------|-----------------|
| 1 | Operator | Opens the screen | Session and operator role verified server-side (FR-AZ-A01) |
| 2 | System | Loads the institution selector | Only institutions the operator is entitled to (FR-AZ-A02) |
| 3 | Operator | Selects an 이용기관 | — |
| 4 | System | Loads that institution's templates | From `KKB_MSG_TMPL`, scoped to the operator's entitlement (FR-ATT-001, FR-ATT-003) |
| 5 | Operator | Selects a 템플릿 | 강조표기제목 populated from `TEMPLATE_TITLE`; `TEMPLATE_MSG` shown as the composition guide (FR-ATT-002) |
| 6 | Operator | Enters 거래고유번호 | Length-validated against the contract bound of 10 (FR-ATC-005) |
| 7 | Operator | Enters recipients | Each format-validated, duplicates removed, running count displayed (FR-ATC-012) |
| 8 | Operator | Selects a 발신번호 | Offered from the institution's registered numbers; free text is not accepted (FR-ATS-004) |
| 9 | Operator | Composes 메시지 | Validated against the registered template as it is written (FR-ATV-001) |
| 10 | Operator | Adds buttons | Type-appropriate fields shown; each length-bounded at 28 / 240 (FR-ATC-005) |
| 11 | Operator | Selects 실패 시 대체 전송 | The fallback message becomes mandatory; 제목 for LMS/MMS, 이미지 ID for MMS (FR-ATC-002) |
| 12 | Operator | Optionally sets 예약발송시간 | Validated as `yyyyMMddHHmmss` and required to be future-dated (FR-ATC-007) |
| 13 | System | Assembles the payload | Emitted as **`failback_data`**, with no field the contract does not declare (FR-ATC-002, FR-ATC-003) |
| 14 | System | Validates server-side against the contract | Names, presence, types, lengths (FR-ATC-001) |
| 15 | System | Presents the composed payload | Ready for UC-ATK-002 |

## 3. Alternative flows

### 3.1 A-1: Operator changes the selected template
- Branches at Step 5. 강조표기제목 and the guide refresh; the message is re-validated against the new template body (FR-ATV-001).

### 3.2 A-2: Operator changes the institution mid-composition
- Branches at Step 3. The template list and sender-number list both re-scope; a template no longer valid for the new institution is cleared rather than carried over (FR-ATT-004).

### 3.3 A-3: Operator resets the form
- At any step. **Every** field, repeated group and the generated output return to the documented initial state (FR-ATC-010).

### 3.4 A-4: Operator copies the payload instead of sending
- At Step 15. Copy reports actual success or failure (FR-ATC-013). Recorded exposure: this path is not audited (RESIDUAL-A01).

## 4. Exception flows

### 4.1 E-1: A value exceeds its contract length
- At Steps 6, 9, 10 or 11.
- Action: rejected at entry, naming the field and the bound (FR-ATC-005, NFR-USE-A03). **Regression guard:** the legacy screen enforced **no length at all** — not one `maxlength` attribute and not one client check — for any of the twelve bounded contract fields (D-A7).

### 4.2 E-2: A fallback type is selected with no fallback message
- At Step 11.
- Action: blocked (FR-ATC-002). **Regression guard:** the legacy emitted a failback object containing only `type`, having stripped the empty `msg` in its clean-up replacer (D-A17).

### 4.3 E-3: A button is left incompletely configured
- At Step 10.
- Action: reported to the operator (FR-ATC-009). **Regression guard:** the legacy dropped any button with a blank name via `.filter(button => button.name)`, so a button visible in the form was silently absent from the payload (D-A9).

### 4.4 E-4: The composed payload does not conform to the contract
- At Step 14.
- Action: rejected, naming the field (FR-ATC-001). **Regression guard:** this step has no legacy counterpart. The legacy emitted its fallback under `failback` rather than the contract's `failback_data` (D-A1) and emitted five fields the contract does not declare — `msg_type`, `kko_header`, `highlight`, `items`, `summary` (D-A2) — and **nothing in the system could detect either**, because nothing consumed the composer's output automatically.

### 4.5 E-5: Single-send validation passes with no recipient of its own
- At Step 7.
- Action: prevented; mode-scoped validation (FR-ATC-011). **Regression guard:** the legacy selected `.receiver-number` document-wide, so a recipient typed into a *batch* message item satisfied the *single-send* required check (D-A5).

### 4.6 E-6: 예약발송시간 is malformed or in the past
- At Step 12.
- Action: rejected (FR-ATC-007). **Regression guard:** the legacy accepted any free text in `reqdate` with no format, timezone or future-time check (D-A11).

## 5. NFR mapping

| NFR-ID | Application |
|--------|-------------|
| NFR-PERF-A01 | Step 14: contract validation and template match P95 < 300 ms |
| NFR-SEC-AUTHZ-A01 | Step 1: operator role enforced server-side |
| NFR-SEC-PII-A01 | Step 7: recipients masked beyond the entry field |
| NFR-SEC-CRED-A01 | Step 13: `sender_key` never reaches the browser (FR-AZ-A05) |
| NFR-USE-A01 | All labels externalised for i18n (D-A22) |
| NFR-USE-A02 | Step 1: no global-handler reassignment, no hardcoded iframe height (D-A21) |
| NFR-USE-A03 | Steps 6–12: failures name field and rule |
| NFR-COMPAT-A02 | Presentation from the design system, not a screen-local inline stylesheet |

## 6. Screen sketch

```
┌─ 카카오 알림톡 발송 ─────────────────────────────────────────┐
│ [ 단건 발송 ] 다건 발송   검증                                │
├──────────────────────────────────────────────────────────────┤
│ 이용기관   [▼ 선택            ]  거래고유번호 [______] 10자   │
│ 템플릿     [▼ TMPL_0001 · 결제안내        ]                   │
│ ┌ 템플릿 본문 ────────────────────────────────────────────┐  │
│ │ #{고객명}님, #{금액}원이 결제되었습니다.                 │  │
│ └──────────────────────────────────────────────────────────┘  │
│ 발신번호   [▼ 0212345678 (등록됨) ]                           │
│ 수신번호   [010-****-1234        ] [+ 추가]      총 3건       │
│ 메시지     [__________________________________]  ✔ 템플릿 일치│
│ 버튼       [웹링크 ▼] 버튼명[______] 28자  URL[______] 240자  │
│ 대체전송   [LMS ▼]  제목[______] 50자  메시지[__________]     │
│ 예약발송   [20260819140000]  (yyyyMMddHHmmss)                 │
│                                    [ 검증 ] [ 발송 ] [ 초기화 ]│
└──────────────────────────────────────────────────────────────┘
```

## 7. Test scenarios

| Test ID | Case | Input | Expected |
|---------|------|-------|----------|
| TC-A001-01 | Normal compose | Valid single-send form | Payload conforms to `ADV_KKO_AT_SEND` |
| TC-A001-02 | **D-A1 regression** | Any fallback selected | Payload key is `failback_data`, not `failback` |
| TC-A001-03 | **D-A2 regression** | Item-list form fully filled | No `msg_type` / `kko_header` / `highlight` / `items` / `summary` outside the contract |
| TC-A001-04 | **D-A7 regression** | `tran_id` of 11 characters | Rejected at entry naming the 10-char bound |
| TC-A001-05 | **D-A7 regression** | Each of the 12 bounded fields, at bound and bound+1 | Accepted at bound, rejected above it |
| TC-A001-06 | **D-A4 regression** | Fill every field, add 2 buttons and 2 items, press 초기화 | All fields, groups and output cleared; no console error |
| TC-A001-07 | **D-A5 regression** | Batch item has a recipient, single-send recipient empty | Single-send validation fails |
| TC-A001-08 | **D-A9 regression** | Button with blank name | Reported, not silently dropped |
| TC-A001-09 | **D-A17 regression** | Fallback LMS with empty message | Blocked |
| TC-A001-10 | **D-A18 regression** | Press 초기화 | 대체전송 returns to `NO`, the markup default |
| TC-A001-11 | **D-A11 regression** | `reqdate` = `어제`, then `2026-08-19`, then `20260819140000` | First two rejected, third accepted |
| TC-A001-12 | **D-A15 regression** | Type an unregistered `template_code` | Not possible — selection only; server rejects if forced |
| TC-A001-13 | **D-A19 regression** | Compare message-type options, single vs batch | Identical option sets |
| TC-A001-14 | **D-A12 regression** | Enter the same recipient three times | De-duplicated to one; count shows 1 |
| TC-A001-15 | Tenant scope | Craft a request for another institution's templates | 403; no templates returned |
| TC-A001-16 | **D-A24 regression** | Inspect all client traffic and page source | `sender_key` absent |
| TC-A001-17 | **D-A22 regression** | Inspect rendered markup | No screen-local inline stylesheet; text externalised |
