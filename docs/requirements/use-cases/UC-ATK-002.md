# Use Case UC-ATK-002: Send a single AlimTalk message

> **REQ-ID**: UC-ATK-002
> **Version**: 1.0
> **Predecessor**: [REQUIREMENTS-SPEC-ALIMTALK.md](../REQUIREMENTS-SPEC-ALIMTALK.md)
> **Legacy origin**: `biztalk_admin_50_s001_act.jsp` (the real send path, in scope per AMB-A00); contracts `IMO.ADV_KKO_AT_SEND` → `IMO.ADV_KKO_AT_SEND2` (`COOCON_ALERT /advising/kakao/at_send`); history `IDO.KKB_ADMIN_SEND_HIS_C001`; ledger `KKB_DPNO_LDGR`

---

## 1. Scenario overview

| Item | Content |
|------|---------|
| **Primary user** | Internal operator |
| **Precondition** | A payload composed and contract-validated per UC-ATK-001 |
| **Trigger** | Operator confirms 발송 |
| **Success outcome** | The message is despatched exactly once, the vendor response is recorded against the `tran_id`, and the send is audited |
| **Failure outcome** | The send is rejected **before** any vendor call, or a partial outcome is reported accurately as partial |
| **Related FR** | FR-ATS-001…014, FR-AZ-A01…A05, FR-ATH-001…003, FR-ATV-001 |
| **Related BR** | BR-002, BR-007, BR-016 |

> **This use case has no legacy counterpart on screen 61.** Screen 61 could not send; it wrote JSON into a textarea. Its exception flows are therefore drawn from screen 50, which AMB-A00 brought into scope, and every regression guard below refers to that file.

## 2. Main flow

| Step | Actor | Action | System response |
|------|-------|--------|-----------------|
| 1 | Operator | Presses 발송 | Session and operator role re-verified; send authorization checked independently of compose authorization (FR-AZ-A01, FR-AZ-A03) |
| 2 | System | Presents a confirmation | Recipient count, template, caller ID and fallback type in one view (NFR-USE-A04) |
| 3 | Operator | Confirms | — |
| 4 | System | Validates the institution scope | Derived from the session, not from the body's `is_cd` (FR-AZ-A02) |
| 5 | System | Verifies the caller ID | `sender_number` must be registered to this institution in the ledger (FR-ATS-004) |
| 6 | System | Verifies the template | `template_code` must be registered to this institution (FR-ATT-004) |
| 7 | System | Validates the message against the template body | `KKB_MSG_TMPL.TEMPLATE_MSG`, server-side (FR-ATV-001) |
| 8 | System | Validates recipients | Anchored pattern; duplicates removed; count > 0 required (FR-ATS-005, FR-ATS-006) |
| 9 | System | Reports any invalid recipients **before despatch** | Operator decides: proceed with the valid subset, or cancel (FR-ATS-007) |
| 10 | System | Checks `(is_cd, tran_id)` for duplication | A repeat within the window is rejected and the original outcome returned (FR-ATS-009) |
| 11 | System | Resolves `sender_key` | From managed secret storage for the authorized institution (FR-ATS-003, NFR-SEC-CRED-A01) |
| 12 | System | Determines the fallback type | On the **decoded** message and its byte length; subject derived from institution or template (FR-ATS-011) |
| 13 | System | Records the send attempt and despatches | History write and vendor despatch kept consistent under failure (FR-ATH-001, FR-ATH-003, NFR-OPS-A01) |
| 14 | System | Records the vendor response | `rsp_code` and `rsp_message` against the `tran_id` (FR-ATS-002) |
| 15 | System | Reports the outcome | Success as success, failure as failure, partial as partial (NFR-OPS-A02) |
| 16 | System | Writes the audit record | Actor, timestamp, institution, `tran_id`, template, recipient count, outcome (FR-AZ-A04, NFR-OPS-AUDIT-A01) |

## 3. Alternative flows

### 3.1 A-1: Operator proceeds with a valid subset
- Branches at Step 9. The send continues with the accepted recipients; the rejected ones are named in the outcome and in the audit record. It is reported as a **partial send**, never as a failure.

### 3.2 A-2: Operator cancels at the confirmation
- Branches at Step 3. No vendor call, no history row, no `tran_id` consumed.

### 3.3 A-3: Reserved send
- At Step 12. `reqdate` is preserved and the despatch occurs at that time (FR-ATS-012). **Regression guard:** the legacy overwrote `reqdate` with `now()` in both branches, so reservation was unreachable (D-A32).

### 3.4 A-4: Operator re-submits the same `tran_id`
- At Step 10. Rejected as a duplicate; the original outcome is returned rather than a second send (FR-ATS-009).

## 4. Exception flows

### 4.1 E-1: The caller ID is not registered to the institution
- At Step 5.
- Action: rejected before any vendor call (FR-ATS-004). **Regression guard:** the legacy checked nothing here — `sender_number` was taken from `input.getString("DP_NO")` and passed straight to the vendor, and the `sender_key` that authorises it was hardcoded (D-A24). This is the step at which FR-SNDD-003 in the 발신번호 slice earns its controls.

### 4.2 E-2: Every recipient is invalid
- At Steps 8–9.
- Action: rejected before any vendor call (FR-ATS-006). **Regression guard:** the legacy built an empty `jArray`, took the `size() <= 1000` branch, **sent the request with an empty recipient list**, and only then threw (D-A31).

### 4.3 E-3: Some recipients are invalid
- At Step 9.
- Action: surfaced as a pre-despatch decision (FR-ATS-007). **Regression guard:** the legacy threw `JexWebBIZException` *after* the history insert and *after* the vendor send had both succeeded, so the operator was told the send failed when the messages had already gone out (D-A26).

### 4.4 E-4: A recipient value is malformed but passes the pattern
- At Step 8.
- Action: rejected (FR-ATS-005). **Regression guard:** `isPhoneNumber()` used `find()` against an unanchored `(01[016789]{1})(\d{3,4})\d{4}$`, so `abc01012345678` was accepted as a phone number (D-A28). Landline recipients were silently discarded and then reported as an error (AMB-A09).

### 4.5 E-5: Two sends occur within the same second
- At Step 10.
- Action: both receive distinct `tran_id`s and both are recorded (FR-ATS-008). **Regression guard:** the legacy built `tran_id` as `"33" + hh24miss`, so two sends in one second produced the same value — the primary key of `KKB_ADMIN_SEND_HIS` (CONST-DATA-A04) and the vendor's correlation handle. The batch branch used an entirely different scheme, `hh24miss + apiNumber++` (D-A25).

### 4.6 E-6: The history write succeeds and the despatch fails, or vice versa
- At Step 13.
- Action: the two are kept consistent; neither a delivery without a record nor a record without an attempt (NFR-OPS-A01, FR-ATH-003). **Regression guard:** the legacy ran the insert and the IMO call as independent statements with no transaction and no rollback, and never closed `idoCon` or `imoCon` on any path including the throwing ones (D-A27).

### 4.7 E-7: The message no longer matches its registered template
- At Step 7.
- Action: rejected with the divergence point identified (FR-ATV-002). This prevents a vendor-side template rejection that the legacy could only discover after despatch.

### 4.8 E-8: A message near the SMS/LMS boundary
- At Step 12.
- Action: the fallback type is chosen on the decoded text (FR-ATS-011). **Regression guard:** the legacy tested `input.getString("MSG").length() > 80` on the **Base64-encoded** value — roughly 33 % longer than the text — so the switch to LMS tripped at about 60 real characters and the 90-byte SMS limit was never the actual criterion (D-A29).

### 4.9 E-9: Logs are inspected after a send
- At Step 13.
- Action: no recipient number and no credential appears at any level (NFR-SEC-PII-A02, NFR-SEC-CRED-A01). **Regression guard:** the legacy wrote `util.getLogger().debug("[BIZTALK_50] " + imoIn.toJSONString())`, serialising every recipient number **and** the `sender_key` into the application log, and logged routine counts at `error` level (D-A30).

## 5. NFR mapping

| NFR-ID | Application |
|--------|-------------|
| NFR-PERF-A02 | Step 13: P95 < 1 s excluding vendor latency |
| NFR-SEC-CRED-A01 | Step 11: profile key from managed secret storage, never logged |
| NFR-SEC-PII-A01 | Steps 2, 15: recipients masked in confirmation and outcome views |
| NFR-SEC-PII-A02 | Step 13: recipients never logged |
| NFR-SEC-TX-A01 | Steps 13–16: integrity and non-repudiation toward the vendor |
| NFR-SEC-AUTHZ-A01 | Steps 1, 4: send authorization enforced server-side |
| NFR-SEC-CHANNEL-A01 | Step 13: TLS and an allowlisted vendor endpoint |
| NFR-OPS-A01 | Step 13: consistency and connection release |
| NFR-OPS-A02 | Step 15: no false success, no false failure |
| NFR-OPS-A03 | Step 13: routine sends not logged at `error` |
| NFR-OPS-AUDIT-A01 | Step 16: audit content |
| NFR-USE-A04 | Step 2: one confirmation view |

## 6. Screen sketch

```
┌─ 발송 확인 ───────────────────────────────────────────────┐
│ 이용기관   ○○기관 (K0****)                                │
│ 템플릿     TMPL_0001 · 결제안내      ✔ 본문 일치           │
│ 발신번호   0212345678                ✔ 등록 확인           │
│ 거래고유번호 A2608180001              ✔ 중복 없음           │
│ 수신번호   총 3건 유효 · 1건 형식 오류                     │
│            010-****-1234 / 010-****-5678 / 010-****-9012  │
│            ✘ 0212-000  ← 형식 오류                         │
│ 대체전송   LMS (본문 142바이트)                            │
│ 예약발송   즉시                                            │
│                                                            │
│ 1건은 형식 오류로 제외됩니다.                              │
│              [ 유효한 3건만 발송 ]  [ 취소 ]               │
└────────────────────────────────────────────────────────────┘
```

## 7. Test scenarios

| Test ID | Case | Input | Expected |
|---------|------|-------|----------|
| TC-A002-01 | Normal send | Valid payload, 3 recipients | Despatched once; `rsp_code` recorded; audited |
| TC-A002-02 | **D-A24 regression** | Secret-scan the repository and inspect logs | No `sender_key` literal anywhere |
| TC-A002-03 | **D-A25 regression** | Two concurrent sends in the same second | Distinct `tran_id`s; both history rows present |
| TC-A002-04 | **D-A25 regression** | Sends on two different days at the same clock time | Distinct `tran_id`s |
| TC-A002-05 | **D-A26 regression** | 5 recipients, 1 malformed | Reported **before** despatch as a choice; no post-send exception |
| TC-A002-06 | **D-A26 regression** | 2500 recipients, 1 malformed | All valid recipients delivered; outcome states the excluded one; no batch silently skipped |
| TC-A002-07 | **D-A27 regression** | Force a vendor failure after the history write | History and delivery consistent; connections released |
| TC-A002-08 | **D-A27 regression** | 200 sequential sends | Connection pool does not exhaust |
| TC-A002-09 | **D-A28 regression** | Recipient `abc01012345678` | Rejected |
| TC-A002-10 | **D-A29 regression** | Message of exactly 90 bytes, then 91 | SMS then LMS, decided on decoded bytes |
| TC-A002-11 | **D-A30 regression** | Inspect logs at every level after a send | No recipient number, no credential |
| TC-A002-12 | **D-A31 regression** | All recipients malformed | Rejected before any vendor call |
| TC-A002-13 | **D-A32 regression** | `reqdate` = now + 2 h | Despatched at `reqdate`, not immediately |
| TC-A002-14 | **D-A34 regression** | Two institutions, same fallback type | Subjects differ per institution/template; no `[쿠콘공지]` literal |
| TC-A002-15 | **D-A35 regression** | Recipients separated by comma, newline, double space | Count matches the operator's intent |
| TC-A002-16 | Idempotency | Double-click 발송 | One despatch; second rejected as duplicate |
| TC-A002-17 | Idempotency | Re-submit the same `tran_id` after the window | Accepted as a new send |
| TC-A002-18 | Caller ID | `sender_number` not in the ledger | 403/rejected before vendor call |
| TC-A002-19 | Caller ID | Ledger number belonging to another institution | Rejected |
| TC-A002-20 | Template scope | `template_code` of another institution | Rejected |
| TC-A002-21 | Authorization | Send endpoint called by a non-operator | 403; no despatch |
| TC-A002-22 | Tenant isolation | Craft `is_cd` of another institution | 403; no despatch |
| TC-A002-23 | Audit | Any send, duplicate rejection, validation rejection | Audit record present with full content |
