# Port Log — 로그인 모듈 (Sprint L1)

> **Date**: 2026-08-14 · **Agent**: `backend-developer`
> **Legacy root**: `D:\WORDSPACE26\HARNESS\TESTS\oldproject`
> **Requirement**: harness §10 — 운영 동작 변경은 ADR 또는 port-log 로 기록 의무

Every behavioural difference between the legacy and the port is recorded here. Where
the difference is deliberate it names the defect ID and the requirement that authorises
it; the legacy source is the only specification (RISK-001), so an unrecorded difference
is indistinguishable from a porting mistake.

---

## 1. File-level mapping

| Legacy artifact | New artifact | Note |
|-----------------|--------------|------|
| `ap/apc/apc_login_proc_act.jsp` | `auth/domain/AuthenticationService.java` | Orchestration; check order preserved |
| — (in the JSP inline) | `auth/domain/AccountPolicy.java` | Lockout, dormancy, status, password age extracted |
| `ap/apm/apm_0001_01_r001_act.jsp` | `auth/domain/PasswordPolicy.java` | **Strength check actually implemented** (L6) |
| `JexMessageDigest.getHashString(SHA_256, …)` | `auth/crypto/PasswordHasher.java` | Argon2id (L2) |
| `ap.com.cmo.UserSessionDAO/VO` | `auth/session/SessionRegistry.java`, `SessionRecord.java` | Newest-login-wins preserved |
| `IDO.USER_LDGR_R006` | `UserMapper.findByEmail` + `UserMapper.xml` | Near-verbatim SQL |
| `IDO.USER_LDGR_LOGIN_ATTEMPT_U001` | `UserMapper.incrementLoginAttempt` | **Atomic increment** — see §2.7 |
| `IDO.USER_LDGR_U006` | `UserMapper.incrementOtpFailCount` | Atomic increment |
| `IDO.USER_LDGR_U009` | `UserMapper.resetFailureCounters` | Unchanged |
| `IDO.USER_LDGR_U010` | `UserMapper.touchLastLogin` | Unchanged |
| `IDO.USER_GRP_JNNG_INFM_R001` | `UserMapper.isOperator` | Reduced to the boolean actually used — see §2.8 |
| `WSVC.*.xml` `mntLogYn=Y` | `common/audit/AuditService.java` | Runtime behaviour rebuilt (RISK-002) |
| `com.common.irisadmin.util.GoogleOTP` | *(Sprint L2)* | Replaced by a library, **not ported** (ADR-LOGIN-010) |
| `IDO.PTL_RSPR_INFM_R001` | **not ported** | Dead query — see §2.9 |
| `GoogleOTP.getQRBarcodeURL()` | **not ported** | Secret over plain HTTP — see §2.10 |

## 2. Behavioural differences

### 2.1 Password hashing — unsalted SHA-256 → Argon2id
- **Legacy**: `strHash = JexMessageDigest.getHashString(SHA_256, pwd)`, compared to `PWD`
- **New**: Argon2id with per-user salt and work factor
- **Authorised by**: FR-LOGIN-005, defect L2
- **Note**: written to a **new** `PWD_HASH` column; `PWD` is left untouched so the legacy keeps working and rollback stays possible (RISK-L04)

### 2.2 Unmigrated accounts cannot authenticate — *temporary*
- **Legacy**: every account authenticated against `PWD`
- **New**: an account without `PWD_HASH` fails closed
- **Reason**: ADR-LOGIN-011 (upgrade-on-login vs forced reset) is undecided, pending the exposure question in RISK-L01. `PasswordHasher.matchesLegacy` throws `UnsupportedOperationException` by design
- **This is a functional gap, not a finished behaviour.** It must be resolved before any real user logs in

### 2.3 Password strength — disabled → enforced
- **Legacy**: `kisalib.Cracklib` commented out; `PROC_CD` was `""` for every non-empty password, i.e. everything passed
- **New**: length, character classes, weak-list, id-inclusion, sequential runs, history of 3
- **Authorised by**: FR-PWD-003, defect L6
- **Consequence**: passwords the legacy accepted will now be rejected at change time

### 2.4 Password length cap — 15 → 12…128
- **Legacy**: `maxlength="15"` on the input
- **New**: minimum 12, maximum 128
- **Authorised by**: FR-PWD-005, defect L9

### 2.5 Unknown membership status — silently permitted → refused
- **Legacy**: string comparisons against `'0'`, `'2'`, `'8'`, `'9'`; any other value fell through and login proceeded
- **New**: `AccountStatus.fromCode` throws on an unrecognised code
- **Rationale**: not an identified defect, but the legacy's fall-through means a future status value added by another system would grant access silently. Failing closed is the safe direction
- **Flagged for**: PM awareness — this is a deliberate behavioural change with no requirement explicitly authorising it

### 2.6 Missing password-change date — permitted → change forced
- **Legacy**: `if(!StringUtil.isBlank(lastChngPwdDt))` — a blank date skipped the check entirely, granting indefinite access
- **New**: a null date forces a change
- **Rationale**: same reasoning as §2.5 — a password of unknown age is treated as needing a change

### 2.7 Failure counters — application-computed → atomic
- **Legacy**: `loginAttempt++` in Java, then `UPDATE … SET LOGIN_ATTEMPT = <value>`
- **New**: `SET LOGIN_ATTEMPT = COALESCE(LOGIN_ATTEMPT,0) + 1 … RETURNING`
- **Rationale**: concurrent attempts could lose an update under the legacy scheme, defeating the lockout entirely (TM-L010)

### 2.8 Group lookup reduced
- **Legacy**: iterated all groups, building `strGrpId`, `strUserDsnc`, `grpId` list and an `adminFlag`
- **New**: a boolean `isOperator` (GRP_0 present)
- **Reason**: only the operator distinction affects authorisation in this sprint. The full group list is needed when menu scoping arrives and will be reinstated then — **not** dropped permanently

### 2.9 `PTL_RSPR_INFM_R001` not ported
- **Legacy**: executed and its result assigned to `idoOut3`, which is never read
- **New**: omitted
- **Authorised by**: defect L10

### 2.10 QR helper not ported
- **Legacy**: `getQRBarcodeURL()` builds `http://chart.apis.google.com/chart?…secret=<OTP secret>`
- **New**: not reproduced in any form; Sprint L2 renders QR locally
- **Authorised by**: FR-OTP-004, defect L4
- **Note**: the method was unused in the legacy, but present and callable — a latent secret-disclosure path

### 2.11 Admin-login notification not ported in Sprint L1
- **Legacy**: on operator login, sends a Kakao 알림톡 to three hardcoded numbers with a hardcoded `sender_key`
- **New**: deferred to Sprint L2, and then configuration-driven
- **Authorised by**: FR-LOGIN-021, defect L1
- **Standing action**: the hardcoded key must be **rotated now**, independently of this project (RISK-L02)

### 2.12 Client IP resolution
- **Legacy**: `getClientIpAddress()` returned the first non-empty value from 11 request headers including `HTTP_VIA` and `HTTP_FORWARDED`
- **New**: resolved at the trusted proxy boundary; `forward-headers-strategy` defaults to `none`
- **Authorised by**: FR-LOGIN-019, defect L7

### 2.13 Debug logging removed
- **Legacy**: on duplicate-login detection, logged email, session id, source IP and login time at debug level
- **New**: logs only the displaced session's instance name
- **Authorised by**: NFR-SEC-LOG-L01

---

## 3. Not yet ported (Sprint L2)

| Legacy behaviour | Requirement |
|------------------|-------------|
| OTP verification (`GoogleOTP.checkCode`) with ±1 step tolerance | FR-LOGIN-011, defect L3 |
| OTP registration, 160-bit secret, local QR | FR-OTP-001…006, defects L4/L8 |
| Operator OTP reset | FR-OTP-007/008 |
| IP allowlist enforcement (`A_USER_IP_AUTHED_R001`) | FR-LOGIN-024, defect L5 |
| Rate limiting | FR-LOGIN-025 |
| Admin-login notification, config-driven | FR-LOGIN-021, defect L1 |
| Password change screen and flow | FR-PWD-001/002/006 |
| React login screen | FR-LOGIN-001/022 |

## 4. Open questions for the domain owner

1. **§2.5 / §2.6** — were the fall-through paths (unknown status, blank password date) intentional tolerances, or oversights? The port treats both as oversights.
2. Is `JNNG_STTS = '1'` the only value meaning "active"? The legacy never tested for it positively; it only excluded 0/2/8/9.
3. `PWD_INIT_YN` — confirmed that `'N'` means "still holding the initial password"? The column name suggests the opposite reading, and the port depends on it.
