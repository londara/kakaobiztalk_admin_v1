# Requirements Specification — 로그인 (Authentication module)

> **Version**: 1.0
> **Date**: 2026-08-14
> **Scope**: legacy login module — `apm_0001_01` (로그인), `apc_login_proc` (인증 처리), `apm_1001_02/03` (OTP 등록), forced password change
> **Predecessors**: [PROJECT-PROPOSAL.md](../planning/PROJECT-PROPOSAL.md), [BUSINESS-REQUIREMENTS.md](../planning/BUSINESS-REQUIREMENTS.md)
> **Sibling spec**: [REQUIREMENTS-SPEC.md](REQUIREMENTS-SPEC.md) (문자내역 slice, G1 approved 2026-08-14)
> **Traceability matrix**: [requirements-matrix.csv](requirements-matrix.csv) — shared, LOGIN rows appended
> **Question log**: [questions-log.md](questions-log.md)
> **Status**: **APPROVED (G1)** — 2026-08-21, PM

---

## 1. Overview

This document specifies the authentication module of the new IRIS BizTalk Portal. It closes **RISK-013** — 문자내역 requires an authenticated session, but no login requirements existed.

Derived by static analysis of 12 legacy artifacts; no runnable legacy environment exists (RISK-001).

### 1.1 Legacy artifacts analyzed

| Layer | File |
|-------|------|
| Login view | `IRIS_ADMIN/web/view/jex/iris_admin/ap/apm/apm_0001_01_view.jsp` (103 L) |
| Login client logic | `IRIS_ADMIN_STATIC/web/opportal/js/jex/iris_admin/ap/apm/apm_0001_01.js` |
| **Login processing** | `IRIS_ADMIN/web/WEB-INF/action/jex/iris_admin/ap/apc/apc_login_proc_act.jsp` — the core |
| Service contracts | `WSVC.apc_login_proc.xml`, `WSVC.apm_0001_01.xml`, `WSVC.apm_0001_01_r001.xml` |
| Password strength | `IRIS_ADMIN/web/WEB-INF/action/jex/iris_admin/ap/apc/apm_0001_01_r001_act.jsp` |
| **OTP implementation** | `IRIS_ADMIN/src/com/common/irisadmin/util/GoogleOTP.java` |
| OTP registration | `apm_1001_02_view.jsp`, `apm_1001_03_view.jsp` + `_r001_act.jsp`, `apm_1001_02_c001_act.jsp` |
| Localisation | `IRIS_ADMIN_ETC/xml/lang/LANG.apm_0001_01.json` |
| Message codes | `IRIS_ADMIN_ETC/xml/code/RSPS_CD/CD_ADM_0000*.xml` |

### 1.2 Data dependencies (IDO)

| IDO | Purpose |
|-----|---------|
| `USER_LDGR_R006` | User lookup by email — returns `PWD`, `OTP_KEY`, `LOGIN_ATTEMPT`, `OTP_FAIL_CNT`, `JNNG_STTS`, `LAST_LOGIN_DT`, `LAST_CHNG_PWD_DT`, `PWD_INIT_YN`, `FLNM`, `CLPH_NO`, `CMNM` … |
| `USER_LDGR_LOGIN_ATTEMPT_U001` | Increment password failure count |
| `USER_LDGR_U006` | Increment OTP failure count |
| `USER_LDGR_U009` | Reset both failure counters |
| `USER_LDGR_U010` | Update last-login timestamp |
| `USER_GRP_JNNG_INFM_R001` | Group membership → role (`GRP_0`=admin, `GRP_1`=user) |
| `USER_LGN_PRHS_C001` | Insert login history |
| `A_USER_IP_AUTHED_R001` | IP allowlist check (**result currently discarded** — L5) |
| `PTL_RSPR_INFM_R001` | Executed but **never read** (L10) |

### 1.3 Authentication model (PM decision, 2026-08-14)

> **이메일 + 비밀번호 + Google OTP (TOTP).** Google OTP is the **only** permitted second factor — no SMS OTP, no hardware token, no alternative vendor. The biztalk `인증번호전송` SMS path is explicitly **not** used for login.

Two factors are retained (knowledge + possession), which is what 전자금융감독규정 expects for administrative access.

### 1.4 Defect disposition

Ten defects were confirmed. Disposition follows the precedent set for 문자내역 (AMB-01, "fix all"), and each fix is an approved deviation from literal parity.

| ID | Defect | Disposition |
|----|--------|-------------|
| L1 | Hardcoded `sender_key`, `sender_number` and **three personal mobile numbers** in source | FIX → FR-LOGIN-021, CONST-SEC-L02 |
| L2 | Unsalted SHA-256 password hashing | FIX → FR-LOGIN-005 |
| L3 | OTP clock-skew window = 0 (`window = 3` commented out) | FIX → FR-LOGIN-011 |
| L4 | `getQRBarcodeURL()` sends the OTP secret to `chart.apis.google.com` over **plain HTTP** | FIX → FR-OTP-004 |
| L5 | IP allowlist queried, redirect commented out — result discarded | FIX → FR-LOGIN-024 |
| L6 | Password strength check disabled (`kisalib.Cracklib` commented out) — returns `""` always | FIX → FR-PWD-003 |
| L7 | `getClientIpAddress()` trusts client-controlled headers | FIX → FR-LOGIN-019 |
| L8 | 80-bit OTP secret (10 bytes) vs RFC 6238's 160-bit recommendation | FIX → FR-OTP-002 |
| L9 | Password `maxlength="15"` caps entropy | FIX → FR-PWD-005 |
| L10 | `PTL_RSPR_INFM_R001` executed, result unused | FIX → removed |

---

## 2. Functional Requirements

### 2.1 Login

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| FR-LOGIN-001 | A user authenticates with **이메일 + 비밀번호 + 6-digit Google OTP** submitted together in one request | Must | E2E test |
| FR-LOGIN-002 | A nonexistent email and a wrong password produce the **same** error response, with no timing or content difference that distinguishes them | Must | Security test |
| FR-LOGIN-003 | Five consecutive password failures lock the account; the counter is persisted | Must | Integration test |
| FR-LOGIN-004 | A successful login resets both the password-failure and OTP-failure counters to zero | Must | Integration test |
| FR-LOGIN-005 | Passwords are stored using **Argon2id** (bcrypt permitted where the runtime constrains). Fixes L2 — legacy used unsalted SHA-256. Existing hashes **cannot be migrated**; see §4 CONST-SEC-L01 | Must | Security audit + code review |
| FR-LOGIN-008 | An account without a registered OTP key cannot log in and is directed to OTP registration | Must | E2E test |
| FR-LOGIN-009 | The OTP code must be exactly 6 numeric digits; non-numeric or wrong-length input is rejected before verification | Must | Unit test |
| FR-LOGIN-010 | Five consecutive OTP failures lock the account | Must | Integration test |
| FR-LOGIN-011 | OTP verification accepts the current time step **±1** (±30 s) to tolerate device clock drift. Fixes L3 — legacy used `window = 0` | Must | Unit test |
| FR-LOGIN-012 | An account with no login for **90 days or more** is treated as dormant and cannot log in until reactivated | Must | Integration test |
| FR-LOGIN-013 | Membership status (`JNNG_STTS`) is enforced: `0`=승인대기, `2`=신청대기, `8`=중지, `9`=해지 all block login with distinct messages | Must | Integration test |
| FR-LOGIN-014 | A password unchanged for **90 days or more** forces a password change before access is granted | Must | Integration test |
| FR-LOGIN-015 | An account still holding its initial password (`PWD_INIT_YN = 'N'`) is forced to change it before access | Must | Integration test |
| FR-LOGIN-016 | A second concurrent login for the same account **terminates the earlier session**; the newest login wins | Must | Integration test |
| FR-LOGIN-017 | A successful login registers the session (session id, server, client IP, user agent) in the session store | Must | Integration test |
| FR-LOGIN-018 | Group membership determines role: `GRP_0` → operator/admin, `GRP_1` → general user | Must | Integration test |
| FR-LOGIN-019 | Login history records the client IP taken from a **trusted source** — the reverse proxy's verified header or the socket address, never an arbitrary client-supplied header. Fixes L7 | Must | Security test |
| FR-LOGIN-020 | The last-login timestamp is updated on every successful login | Must | Integration test |
| FR-LOGIN-021 | Administrator logins notify other administrators. Recipients, sender key and template are **configuration, not source**. Fixes L1 | Should | Integration test |
| FR-LOGIN-022 | The login screen offers 아이디저장; when enabled the email is stored client-side. **The password and OTP are never stored** | Should | E2E test |
| FR-LOGIN-023 | A user can log out, invalidating the session both server-side and in the session store | Must | E2E test |
| FR-LOGIN-024 | The IP allowlist check (`A_USER_IP_AUTHED_R001`) is **enforced** — a non-allowlisted address is denied. Fixes L5, where the result was queried and discarded. *(Applicability per audience — see AMB-L03)* | Must | Security test |
| FR-LOGIN-025 | Failed logins are rate-limited per account and per source address, independently of the lockout counter | Must | Security test |

### 2.2 OTP registration

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| FR-OTP-001 | Only an account **without** a registered OTP key may register one | Must | Integration test |
| FR-OTP-002 | The OTP secret is generated with a cryptographically secure RNG at **160 bits (20 bytes)**, Base32-encoded. Fixes L8 — legacy used 80 bits | Must | Code review + unit test |
| FR-OTP-003 | Registration presents the Base32 secret and a QR code in `otpauth://totp/` format for Google Authenticator | Must | E2E test |
| FR-OTP-004 | The QR code is generated **locally**. The secret must never be transmitted to any third-party service. Fixes L4 — the legacy helper built a `http://chart.apis.google.com` URL embedding the secret in cleartext | Must | Security audit + code review |
| FR-OTP-005 | Registration completes only after the user submits a valid code from the newly issued secret | Must | E2E test |
| FR-OTP-006 | An attempt to register when a key already exists is rejected | Must | Integration test |
| FR-OTP-007 | An **operator may reset** a user's OTP registration after out-of-band identity verification, allowing the user to register a new device. This is the sole recovery path for a lost device | Must | E2E test |
| FR-OTP-008 | Every OTP reset records who performed it, for whom, and when | Must | Integration test |
| FR-OTP-009 | Registration is available only to accounts passing the dormancy and membership-status checks (FR-LOGIN-012/013) | Must | Integration test |

### 2.3 Password management

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| FR-PWD-001 | A user can change their password; the forced-change flow (FR-LOGIN-014/015) reuses the same screen | Must | E2E test |
| FR-PWD-002 | Changing a password requires the current password | Must | Integration test |
| FR-PWD-003 | Password strength is **actually validated** and weak passwords rejected. Fixes L6 — the legacy check returned an empty result for every input because the library call was commented out | Must | Unit test |
| FR-PWD-004 | A new password may not repeat the previous *N* passwords *(N assumed 3 — see AMB-L04)* | Should | Integration test |
| FR-PWD-005 | Password length: minimum 12, maximum **at least 64** characters. Fixes L9 — legacy capped at 15 | Must | Unit test |
| FR-PWD-006 | A successful password change updates `LAST_CHNG_PWD_DT` and clears `PWD_INIT_YN` | Must | Integration test |

---

## 3. Non-Functional Requirements

### 3.1 NFR-SEC

| REQ-ID | Requirement | Verification |
|--------|-------------|--------------|
| NFR-SEC-AUTH-L01 | Two factors mandatory for every account: password (knowledge) + Google OTP (possession). No single-factor path exists | Security audit |
| NFR-SEC-AUTH-L02 | Google OTP is the **only** second factor. No SMS OTP, hardware token, or alternative provider is implemented | Security audit |
| NFR-SEC-AUTH-L03 | TOTP conforms to RFC 6238: HMAC-SHA1, 30-second step, 6 digits, ±1 step tolerance | Unit test |
| NFR-SEC-PII-L01 | The OTP secret is stored encrypted at rest and never returned by any API after registration | Security audit |
| NFR-SEC-PII-L02 | `CLPH_NO`, `FLNM` and other personal fields held in session are masked wherever displayed or logged | Security audit |
| NFR-SEC-LOG-L01 | No credential, OTP code, OTP secret, session id, or personal field appears in logs. **The legacy logs email, session id and IP at debug level** — not carried across | Static analysis |
| NFR-SEC-SESSION-L01 | Session cookie is HttpOnly, Secure, SameSite; the session id is regenerated on successful authentication | Security test |
| NFR-SEC-SESSION-L02 | Sessions expire after 30 minutes of inactivity *(assumed — see AMB-L05)* | Integration test |
| NFR-SEC-CHANNEL-L01 | All authentication traffic over TLS 1.2+; no plaintext fallback | Security audit |
| NFR-SEC-SECRET-L01 | No secret, key, phone number or recipient list appears in source. Fixes L1 | gitleaks + code review |

### 3.2 NFR-PERF / NFR-OPS / NFR-USE

| REQ-ID | Requirement | Verification |
|--------|-------------|--------------|
| NFR-PERF-L01 | Login response P95 < 1 s including OTP verification | Load test |
| NFR-OPS-AUDIT-L01 | Every authentication event — success, failure, lockout, OTP reset, password change — is audited and retained **5 years** (ADR-006) | Operational audit |
| NFR-OPS-AUDIT-L02 | Audit records contain no credential material | Static analysis |
| NFR-USE-L01 | Login completes in a single screen and one submission | E2E test |
| NFR-USE-L02 | Error messages are actionable without disclosing whether an account exists (FR-LOGIN-002) | Review |

---

## 4. Constraints

| REQ-ID | Constraint | Basis |
|--------|-----------|-------|
| CONST-SEC-L01 | **Existing password hashes cannot be migrated.** Unsalted SHA-256 cannot be upgraded in place without the plaintext, so every user must establish a new password at cutover. A communication plan is required | L2, FR-LOGIN-005 |
| CONST-SEC-L02 | No secret or personal contact detail in source or version control | L1 |
| CONST-DATA-L01 | The existing user schema (`USER_LDGR` and related) is reused; `PWD` and `OTP_KEY` column formats change with the new algorithms and require a migration plan | CONST-DATA-01 |
| CONST-TECH-L01 | Java 17+ / Spring Boot 3.x / Spring Security | ADR-001, ADR-008 |
| CONST-LEGAL-L01 | 전자금융감독규정 — administrative access requires two factors; access records retained | §6 checklist |
| CONST-LEGAL-L02 | 개인정보보호법 — `CLPH_NO`, `FLNM`, `EML` are personal information | BR-007 |

---

## 5. Use Cases

| UC-ID | Scenario | Primary user | Related FR |
|-------|----------|--------------|------------|
| [UC-LOGIN-001](use-cases/UC-LOGIN-001.md) | Log in with email, password and Google OTP | All users | FR-LOGIN-001…025 |
| [UC-LOGIN-002](use-cases/UC-LOGIN-002.md) | Register Google OTP for the first time | New user | FR-OTP-001…006, 009 |
| [UC-LOGIN-003](use-cases/UC-LOGIN-003.md) | Operator resets OTP after a lost device | Operator | FR-OTP-007, 008 |

Orphan check: every FR and NFR maps to at least one use case. **Orphan count: 0.**

---

## 6. AMBIGUOUS / open items

| ID | Item | Candidates | PM response | Status |
|----|------|-----------|-------------|--------|
| AMB-L01 | Meaning of "Login with GoogleOTP only" | A: Google OTP is the only OTP method / B: passwordless / C: + mandatory for all accounts | **A — 2FA retained, Google OTP the only second factor** | RESOLVED |
| AMB-L02 | OTP recovery when a device is lost | A: operator reset / B: backup codes / C: keep legacy (no path) / D: 보류 | **A — operator resets, user re-registers** | RESOLVED |
| AMB-L03 | IP allowlist scope — the portal is now internet-facing with external tenants. An allowlist is workable for internal operators but not for client companies | A: operators only / B: all users / C: disable entirely | Pending | **PENDING** — needed at G1 |
| AMB-L04 | Password history depth for FR-PWD-004 | A: 3 (assumed) / B: 5 / C: none | A | **PENDING** |
| AMB-L05 | Session inactivity timeout | A: 30 min (assumed) / B: 15 min / C: 60 min | A | **PENDING** |
| AMB-L06 | Are the 90-day dormancy and 90-day password cycle correct for external tenant users, or only for internal operators? | A: apply to all / B: operators only / C: different thresholds | Pending | **PENDING** — domain owner |
| AMB-L07 | Admin-login notification (FR-LOGIN-021) — retain at all? It messages three hardcoded individuals today | A: retain, config-driven / B: replace with audit-log alerting / C: drop | A assumed | **PENDING** |

> **AMB-L03 is the one to settle before design.** It interacts directly with RISK-006 (망분리): if operators are IP-restricted and tenants are not, that is effectively a network-tier split and materially shapes the deployment topology.

---

## 7. Change history

| Date | Version | Change | Author |
|------|---------|--------|--------|
| 2026-08-14 | 1.0 | Initial draft — login module, from static analysis of 12 legacy artifacts | Skill 02 |

---

**G1 approval (analysis gate)**

| Date | Approver | Comment | Status |
|------|----------|---------|--------|
| 2026-08-21 | PM | 결재. AMB-L03(IP allowlist 범위)은 **미결 상태로 이월** — 인터넷 노출 구성에서 이용기관까지 allowlist 를 적용할 수 없으므로 별도 결정이 필요하다. 구현(Sprint L1~L4)이 결재에 선행한 **사후 결재** / Approved. AMB-L03 (IP allowlist scope) is **carried forward unresolved** and still needs a ruling. Recorded as a **retrospective** approval — Sprints L1–L4 preceded this signature | ✅ **APPROVED** |
