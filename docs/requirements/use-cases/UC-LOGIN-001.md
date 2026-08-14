# Use Case UC-LOGIN-001: Log in with email, password and Google OTP

> **REQ-ID**: UC-LOGIN-001
> **Version**: 1.0
> **Predecessor**: [REQUIREMENTS-SPEC-LOGIN.md](../REQUIREMENTS-SPEC-LOGIN.md)
> **Legacy origin**: `apm_0001_01_view.jsp` / `apm_0001_01.js` / `WSVC.apc_login_proc` / `apc_login_proc_act.jsp` / `GoogleOTP.java`

---

## 1. Scenario overview

| Item | Content |
|------|---------|
| **Primary user** | Any portal user — client-company admin or internal operator |
| **Precondition** | Account exists, membership status active, OTP registered |
| **Trigger** | User submits the login form |
| **Success outcome** | Session established, role resolved, user lands on the portal main page |
| **Failure outcome** | Rejection with a non-disclosing message; failure counters incremented; possible lockout |
| **Related FR** | FR-LOGIN-001…025 |
| **Related BR** | BR-002, BR-006, BR-015 |

## 2. Main flow

| Step | Actor | Action | System response |
|------|-------|--------|-----------------|
| 1 | User | Opens the login screen | Form presented: 이메일, 비밀번호, OTP. If 아이디저장 was previously set, the email is prefilled (FR-LOGIN-022) |
| 2 | User | Enters email, password and the 6-digit code from Google Authenticator | Client-side format validation |
| 3 | User | Submits | Rate limit checked (FR-LOGIN-025); IP allowlist checked where applicable (FR-LOGIN-024) |
| 4 | System | Looks up the account | Not found → generic failure (FR-LOGIN-002) |
| 5 | System | Checks lockout | `LOGIN_ATTEMPT ≥ 5` or `OTP_FAIL_CNT ≥ 5` → locked (FR-LOGIN-003/010) |
| 6 | System | Verifies password | Argon2id comparison (FR-LOGIN-005). Mismatch → E-1 |
| 7 | System | Verifies OTP | 6 numeric digits (FR-LOGIN-009); TOTP verified with ±1 step tolerance (FR-LOGIN-011). Mismatch → E-2 |
| 8 | System | Resets counters | Both failure counters set to 0 (FR-LOGIN-004) |
| 9 | System | Checks dormancy | Last login ≥ 90 days → E-3 (FR-LOGIN-012) |
| 10 | System | Checks membership status | `JNNG_STTS` ∈ {0,2,8,9} → E-4 (FR-LOGIN-013) |
| 11 | System | Checks password age | ≥ 90 days, or `PWD_INIT_YN='N'` → A-1 (FR-LOGIN-014/015) |
| 12 | System | Handles duplicate login | Existing session for the account is terminated (FR-LOGIN-016) |
| 13 | System | Establishes session | Session id regenerated; session registered with server, trusted IP, user agent (FR-LOGIN-017/019) |
| 14 | System | Resolves role | `GRP_0` → operator, `GRP_1` → user (FR-LOGIN-018) |
| 15 | System | Records | Login history inserted, last-login timestamp updated, audit event written (FR-LOGIN-020, NFR-OPS-AUDIT-L01) |
| 16 | System | Notifies (admin only) | Other administrators notified via configured recipients (FR-LOGIN-021) |
| 17 | User | Arrives at the portal | Main page, menus scoped by role |

## 3. Alternative flows

### 3.1 A-1: Forced password change
- Branches at Step 11. Password ≥ 90 days old, or still the initial password.
- Action: session **not** fully established; user directed to the password-change screen (UC-LOGIN-003 shares the screen).
- Result: after a successful change, login resumes from Step 12.

### 3.2 A-2: OTP not yet registered
- Branches at Step 7. `OTP_KEY` is empty.
- Action: login denied, user directed to OTP registration (FR-LOGIN-008).
- Result: UC-LOGIN-002 begins.

### 3.3 A-3: 아이디저장 enabled
- Branches at Step 17. Email stored client-side; **password and OTP never stored** (FR-LOGIN-022).

### 3.4 A-4: Operator login
- Branches at Step 14. Role resolves to operator — wider menu scope, and the notification in Step 16 fires.

## 4. Exception flows

### 4.1 E-1: Wrong password
- Step 6. Increment `LOGIN_ATTEMPT`; at 5 the account locks (FR-LOGIN-003).
- Message identical to "account not found" (FR-LOGIN-002).

### 4.2 E-2: Wrong OTP code
- Step 7. Increment `OTP_FAIL_CNT`; at 5 the account locks (FR-LOGIN-010).
- **Regression guard:** a code from the immediately preceding or following 30-second step must be **accepted** (FR-LOGIN-011). The legacy rejected it (`window = 0`, defect L3).

### 4.3 E-3: Dormant account
- Step 9. No login for ≥ 90 days → denied with a reactivation message.

### 4.4 E-4: Membership status blocks login
- Step 10. `0`=승인대기, `2`=신청대기, `8`=중지, `9`=해지 — each with its own message.

### 4.5 E-5: Account locked
- Step 5. Locked accounts are rejected before any credential is verified, so a locked account cannot be used as a password oracle.

### 4.6 E-6: Rate limit exceeded
- Step 3. Requests throttled per account and per source address (FR-LOGIN-025), independent of the lockout counter.

### 4.7 E-7: Non-allowlisted source address
- Step 3. Denied where the allowlist applies (FR-LOGIN-024).
- **Regression guard:** the legacy queried the allowlist and then **discarded the result** (defect L5) — a denial must actually occur.

## 5. NFR mapping

| NFR-ID | Application |
|--------|-------------|
| NFR-PERF-L01 | Steps 4–15: P95 < 1 s |
| NFR-SEC-AUTH-L01/L02 | Steps 6–7: two factors, Google OTP the only second factor |
| NFR-SEC-AUTH-L03 | Step 7: RFC 6238 conformance |
| NFR-SEC-SESSION-L01 | Step 13: cookie flags, session id regeneration |
| NFR-SEC-LOG-L01 | All steps: no credential, OTP code or session id in logs |
| NFR-OPS-AUDIT-L01 | Steps 8, 15: every outcome audited |

## 6. Screen sketch

```
┌──────────── Fin Admin ────────────┐
│           [  logo  ]              │
│  ┌─────────── 로그인 ───────────┐ │
│  │ [ 아이디 입력            ]   │ │
│  │ [ 비밀번호 입력          ]   │ │
│  │ [ OTP 입력 (6자리)       ]   │ │
│  │        [   Login   ]         │ │
│  │  ☐ 아이디저장 | OTP 등록     │ │
│  └──────────────────────────────┘ │
└───────────────────────────────────┘
```

## 7. Test scenarios

| Test ID | Case | Input | Expected |
|---------|------|-------|----------|
| TC-LOGIN-001-01 | Normal login | Valid email + password + current OTP | Session established, main page |
| TC-LOGIN-001-02 | **L3 regression** | OTP from the previous 30-second step | **Accepted** (legacy rejected) |
| TC-LOGIN-001-03 | **L3 regression** | OTP from the next 30-second step | Accepted |
| TC-LOGIN-001-04 | OTP two steps old | Code from 90 s ago | Rejected |
| TC-LOGIN-001-05 | **L2 regression** | Inspect stored credential | Argon2id hash with per-user salt; no SHA-256 |
| TC-LOGIN-001-06 | Wrong password ×5 | 5 attempts | Account locked at the 5th |
| TC-LOGIN-001-07 | Wrong OTP ×5 | 5 attempts | Account locked at the 5th |
| TC-LOGIN-001-08 | Counter reset | Fail 3, then succeed | Both counters zero |
| TC-LOGIN-001-09 | User enumeration | Nonexistent email vs wrong password | Identical message and comparable timing |
| TC-LOGIN-001-10 | Non-numeric OTP | `"abcdef"` | Rejected before verification |
| TC-LOGIN-001-11 | Wrong-length OTP | `"12345"` | Rejected |
| TC-LOGIN-001-12 | Dormant | Last login 91 days ago | Denied, reactivation message |
| TC-LOGIN-001-13 | Membership status | `JNNG_STTS` = 0 / 2 / 8 / 9 | Each denied with its own message |
| TC-LOGIN-001-14 | Password expiry | `LAST_CHNG_PWD_DT` 91 days ago | Forced password change |
| TC-LOGIN-001-15 | Initial password | `PWD_INIT_YN='N'` | Forced password change |
| TC-LOGIN-001-16 | Duplicate login | Log in twice | First session terminated |
| TC-LOGIN-001-17 | **L7 regression** | Forged `X-Forwarded-For` header | Recorded IP is the trusted value, not the header |
| TC-LOGIN-001-18 | **L5 regression** | Login from a non-allowlisted address | Actually denied |
| TC-LOGIN-001-19 | **L1 regression** | Scan source and config | No hardcoded phone numbers or sender key |
| TC-LOGIN-001-20 | No OTP registered | `OTP_KEY` empty | Directed to OTP registration |
| TC-LOGIN-001-21 | Locked account | Correct credentials, locked account | Rejected before credential verification |
| TC-LOGIN-001-22 | Session fixation | Session id before vs after login | Regenerated |
| TC-LOGIN-001-23 | Logout | Log out then reuse the session | Session invalid server-side and in the store |
| TC-LOGIN-001-24 | Audit | Every outcome above | Audit record written, no credential material |
