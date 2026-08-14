# Use Case UC-LOGIN-003: Operator resets OTP after a lost device

> **REQ-ID**: UC-LOGIN-003
> **Version**: 1.0
> **Predecessor**: [REQUIREMENTS-SPEC-LOGIN.md](../REQUIREMENTS-SPEC-LOGIN.md)
> **Origin**: **New requirement.** The legacy has no recovery path — `apm_1001_03_r001_act.jsp` throws `ADM_00026` when `OTP_KEY` already exists, leaving a user with a lost device permanently unable to log in

---

## 1. Scenario overview

| Item | Content |
|------|---------|
| **Primary user** | Internal operator (`GRP_0`) |
| **Secondary** | The affected user |
| **Precondition** | Operator authenticated; the user's identity verified out of band |
| **Trigger** | A user reports a lost, replaced or wiped OTP device |
| **Success outcome** | `OTP_KEY` cleared; the user can register a new device via UC-LOGIN-002 |
| **Failure outcome** | Reset refused — insufficient privilege or unknown account |
| **Related FR** | FR-OTP-007, FR-OTP-008 |

> **Why this exists.** With Google OTP as the only second factor (AMB-L01) and self-service re-registration blocked (FR-OTP-006), a lost phone would otherwise mean a permanently unusable account. This use case is the sole recovery path, which also makes it a privileged operation worth auditing carefully — it is, by design, the way to bypass someone's second factor.

## 2. Main flow

| Step | Actor | Action | System response |
|------|-------|--------|-----------------|
| 1 | User | Reports the lost device through an out-of-band channel | *(Outside the system — see §3 note)* |
| 2 | Operator | Verifies the user's identity by the organisation's procedure | *(Outside the system)* |
| 3 | Operator | Locates the account in the user administration screen | Account shown with OTP registration status |
| 4 | Operator | Requests an OTP reset | Confirmation prompt stating the security consequence |
| 5 | Operator | Confirms | `OTP_KEY` cleared; any active session for that user terminated |
| 6 | System | Audits | Records operator identity, target account, timestamp, and reason (FR-OTP-008) |
| 7 | System | Notifies | The affected user is notified through a channel **other than** the reset one |
| 8 | User | Registers a new device | UC-LOGIN-002 |

## 3. Alternative flows

### 3.1 A-1: Reset with the account also locked
- Branches at Step 5. If `OTP_FAIL_CNT ≥ 5`, the counter is cleared alongside the key so the user is not blocked immediately after re-registering.

### 3.2 A-2: Operator resets their own OTP
- Should be prohibited — an operator resetting their own second factor removes the separation the control depends on. Recorded as **AMB-L08**; assumed prohibited, requiring a second operator.

> **Note on Steps 1–2.** Out-of-band identity verification is the control that actually protects this flow; the software cannot enforce it. If that procedure is weak, this use case becomes the easiest route to compromising any account — including an operator's. The procedure needs to be written down and owned outside this project.

## 4. Exception flows

### 4.1 E-1: Non-operator attempts the reset
- Step 4. Rejected by role check and logged as a security event (FR-LOGIN-018).

### 4.2 E-2: Target account has no OTP registered
- Step 4. No-op with a clear message; still audited.

### 4.3 E-3: Reset attempted on a 해지 (`JNNG_STTS='9'`) account
- Step 4. Refused — a terminated account should not regain access through a reset.

## 5. NFR mapping

| NFR-ID | Application |
|--------|-------------|
| NFR-OPS-AUDIT-L01 | Step 6: privileged operation, audited with actor and target, retained 5 years |
| NFR-SEC-AUTH-L01 | Step 4: operator role required |
| NFR-SEC-LOG-L01 | Step 6: no credential material recorded |

## 6. Screen sketch

```
┌──── 사용자 관리 — OTP 초기화 ────┐
│ 사용자  hong@example.com         │
│ OTP 등록 상태  등록됨            │
│ 사유     [ 단말 분실           ] │
│ ⚠ 초기화 시 사용자는 새 기기로   │
│   재등록해야 로그인할 수 있습니다│
│              [ 취소 ] [ 초기화 ] │
└──────────────────────────────────┘
```

## 7. Test scenarios

| Test ID | Case | Input | Expected |
|---------|------|-------|----------|
| TC-LOGIN-003-01 | Normal reset | Operator resets a registered account | `OTP_KEY` cleared; re-registration possible |
| TC-LOGIN-003-02 | Audit | Any reset | Operator, target, timestamp, reason recorded |
| TC-LOGIN-003-03 | Privilege | Non-operator attempts a reset | Rejected and logged |
| TC-LOGIN-003-04 | Session termination | User active during the reset | Session terminated |
| TC-LOGIN-003-05 | Locked account | `OTP_FAIL_CNT = 5` at reset | Counter cleared with the key |
| TC-LOGIN-003-06 | No OTP registered | Target has no key | Clear message, still audited |
| TC-LOGIN-003-07 | Terminated account | `JNNG_STTS = 9` | Refused |
| TC-LOGIN-003-08 | Re-registration | After reset | UC-LOGIN-002 completes normally |
| TC-LOGIN-003-09 | Self-reset | Operator targets their own account | Refused (pending AMB-L08) |
| TC-LOGIN-003-10 | Notification | After reset | User notified via a different channel |
