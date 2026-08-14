# Use Case UC-LOGIN-002: Register Google OTP

> **REQ-ID**: UC-LOGIN-002
> **Version**: 1.0
> **Predecessor**: [REQUIREMENTS-SPEC-LOGIN.md](../REQUIREMENTS-SPEC-LOGIN.md)
> **Legacy origin**: `apm_1001_02_view.jsp`, `apm_1001_03_view.jsp`, `apm_1001_03_r001_act.jsp`, `apm_1001_02_c001_act.jsp`, `GoogleOTP.generate()`

---

## 1. Scenario overview

| Item | Content |
|------|---------|
| **Primary user** | A user whose account has no OTP key |
| **Precondition** | Account exists, membership status active, not dormant, `OTP_KEY` empty |
| **Trigger** | User clicks **OTP 등록** on the login screen, or is redirected there after a login attempt |
| **Success outcome** | An OTP secret is issued, verified once, and stored; the user can now log in |
| **Failure outcome** | Registration refused — already registered, dormant, or status-blocked |
| **Related FR** | FR-OTP-001…006, FR-OTP-009 |

## 2. Main flow

| Step | Actor | Action | System response |
|------|-------|--------|-----------------|
| 1 | User | Clicks OTP 등록 | Identity confirmed by email + password before any secret is issued |
| 2 | System | Checks eligibility | Dormancy and membership status verified (FR-OTP-009); `OTP_KEY` must be empty (FR-OTP-001) |
| 3 | System | Generates the secret | 160-bit secret from a secure RNG, Base32-encoded (FR-OTP-002) |
| 4 | System | Presents it | Base32 string plus a **locally generated** QR code in `otpauth://totp/` form (FR-OTP-003/004) |
| 5 | User | Scans or types it into Google Authenticator | — |
| 6 | User | Enters the 6-digit code the app now shows | Verified against the pending secret (FR-OTP-005) |
| 7 | System | Persists | Secret stored encrypted (NFR-SEC-PII-L01); registration audited |
| 8 | User | Proceeds to login | UC-LOGIN-001 |

> **The secret is only persisted after Step 6 succeeds.** A user who abandons registration midway leaves no half-configured account, and cannot be locked out by a secret they never captured.

## 3. Alternative flows

### 3.1 A-1: Redirected from a login attempt
- The legacy set a session marker (`_OTP_REG_STATUS`) and returned `ERR_CD = "O001"` with "OTP 미등록 계정입니다. OTP등록 화면으로 이동합니다". Same behaviour, without exposing an internal code to the client.

### 3.2 A-2: Re-registration after an operator reset
- `OTP_KEY` was cleared by UC-LOGIN-003, so the account is eligible again and the flow runs unchanged.

## 4. Exception flows

### 4.1 E-1: OTP already registered
- Step 2. Rejected (FR-OTP-006) — legacy `ADM_00026`. Recovery is operator-mediated only (UC-LOGIN-003).

### 4.2 E-2: Verification code wrong
- Step 6. The secret is **not** persisted; the user may retry or restart. Repeated failures are rate-limited.

### 4.3 E-3: Dormant or status-blocked account
- Step 2. Refused with the same messages as login (FR-LOGIN-012/013).

### 4.4 E-4: Registration abandoned
- Between Steps 4 and 6. The pending secret expires and is discarded.

## 5. NFR mapping

| NFR-ID | Application |
|--------|-------------|
| NFR-SEC-PII-L01 | Step 7: secret encrypted at rest, never re-exposed by any API |
| NFR-SEC-AUTH-L03 | Step 6: RFC 6238 verification |
| NFR-SEC-LOG-L01 | Steps 3–7: the secret never appears in logs |
| NFR-SEC-CHANNEL-L01 | Step 4: TLS — the secret crosses the network once |
| NFR-OPS-AUDIT-L01 | Step 7: registration audited |

## 6. Screen sketch

```
┌─────────── OTP 등록 ────────────┐
│ 1. Google Authenticator 앱 설치 │
│ 2. 아래 QR 스캔 또는 키 입력    │
│    ┌──────────┐                 │
│    │ QR (local)│  KEY: ABCD…XYZ │
│    └──────────┘                 │
│ 3. 앱에 표시된 6자리 입력       │
│    [ ______ ]      [ 등록 ]     │
└─────────────────────────────────┘
```

## 7. Test scenarios

| Test ID | Case | Input | Expected |
|---------|------|-------|----------|
| TC-LOGIN-002-01 | Normal registration | Valid code from the issued secret | Secret stored, login possible |
| TC-LOGIN-002-02 | **L8 regression** | Inspect the generated secret | 160-bit (20-byte) entropy, not 80-bit |
| TC-LOGIN-002-03 | **L4 regression** | Capture outbound traffic during registration | **No request to `chart.apis.google.com`**; no external transmission of the secret |
| TC-LOGIN-002-04 | **L4 regression** | Inspect the QR source | Generated locally; `otpauth://` payload never leaves the server over HTTP |
| TC-LOGIN-002-05 | Already registered | Account with an existing `OTP_KEY` | Rejected |
| TC-LOGIN-002-06 | Wrong verification code | Invalid 6 digits | Secret not persisted |
| TC-LOGIN-002-07 | Abandoned registration | Leave after Step 4 | No `OTP_KEY` stored |
| TC-LOGIN-002-08 | Dormant account | Last login 91 days ago | Refused |
| TC-LOGIN-002-09 | Status-blocked | `JNNG_STTS` = 8 | Refused |
| TC-LOGIN-002-10 | Secret at rest | Inspect storage | Encrypted, not plaintext Base32 |
| TC-LOGIN-002-11 | Secret not re-exposed | Call any API after registration | Secret never returned |
| TC-LOGIN-002-12 | Interoperability | Register in Google Authenticator | Generated codes verify successfully |
