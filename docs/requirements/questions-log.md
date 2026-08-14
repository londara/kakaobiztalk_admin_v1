# Question Log — 문자내역 requirements (Skill 02)

> **Date**: 2026-08-14
> **Scope**: legacy screens 40 / 41
> **Related**: [REQUIREMENTS-SPEC.md](REQUIREMENTS-SPEC.md)

---

## 1. Resolved

### AMB-01 — Parity vs. correctness
**Question.** The KPI is "preserve legacy behavior", but static analysis found 9 defects. Reproducing them faithfully means shipping known-broken behavior; fixing them means deviating from parity.
**Candidates.** A: fix all 9 · B: security + broken only · C: D1 only · D: case-by-case
**PM response.** **A — fix all 9.**
**Effect.** Each fix is an approved parity exception, tracked in `defect_ref` of the matrix. Parity is measured as *intent parity*, not literal reproduction. 20 regression tests in UC-MSG-001/UC-MSG-002 exist specifically to prove each defect is gone.

### AMB-02 — Tenant scoping
**Question.** 이용기관 is currently an optional client-supplied filter, with a dropdown listing **all** institutions loaded via `biztalk_admin_00_l001` with `USE_YN=ALL`. For external self-service this both leaks the customer list and permits cross-tenant reads.
**Candidates.** A: server-enforced from session · B: + operator dropdown · C: keep client-supplied
**PM response.** **A — server-enforced from session.**
**Effect.** FR-TEN-001…004, NFR-SEC-TENANT. Client-supplied tenant identifiers are ignored, not merely validated.

### AMB-03 — Paging and range limits *(superseded)*
**Question.** Pagination is commented out and no maximum period exists; the query unions 8 tables and decrypts every row before filtering.
**Candidates.** A: paging + 31d · B: paging + 92d · C: paging only · D: keep as-is
**PM response.** **D — keep as-is**, later superseded by CONFLICT-01.

### AMB-04 — Unpopulated detail fields
**Question.** The detail contract declares 19 output fields; the four IDOs return 8. Eleven fields (`TEMPLATE_CODE`, `PROFILE_KEY`, `AD_FLAG`, `IMG_PATH`, `IMG_URL`, `WI_FLAG`, `BUTTON_JSON`, `FAILED_TYPE`, `FAILED_SUBJECT`, `FAILED_IMG`, `FAILED_MSG`) are never populated, and the tabs meant to display them are commented out.
**Candidates.** A: implement properly · B: drop to the 8 that work · C: failure fields only · D: 보류
**PM response.** **A — implement all 19.**
**Effect.** FR-MSGD-004, FR-MSGD-006. This is genuinely new capability, not a port: template code and failure reason are the fields support staff need most.

### CONFLICT-01 — AMB-01 vs AMB-03
**Conflict.** AMB-01 ruled "fix all 9", which includes D7 (paging disabled). AMB-03 ruled "keep no paging, no limit". Both cannot hold. Raised under harness §3 (충돌 식별 → PM 결재 의무) before any requirement was written.
**Candidates.** A: A03 wins, D7 stays unfixed · B: paging restored, no cap · C: paging + range cap
**PM response.** **C — A01 wins fully: paging plus a range cap.**
**Effect.** FR-MSG-007, FR-MSG-013, NFR-PERF-01/02. AMB-03 marked SUPERSEDED.

---

## 2. Open

| ID | Question | Candidates | Working assumption | Owner | Needed by |
|----|----------|-----------|--------------------|-------|-----------|
| AMB-05 | `STATUS` value `5` appears in no mapping, and the legacy grid renders unmapped values as blank. Is `5` a retired state, or a gap in the renderer? | A: display verbatim · B: map to a label the domain owner supplies | A — display verbatim so data is never silently hidden | Domain owner | Skill 3 |
| ~~AMB-06~~ | ~~Cap value for the search period~~ | ~~A: 31 days · B: 92 days~~ | **RESOLVED at G1 — 31 days** | PM | ✅ closed 2026-08-14 |
| AMB-07 | Should 문자내역 have Excel export? Screen 40 has none; screens 20/30 do | A: no export (parity) · B: add export | A — specified as `Could`, not built unless confirmed | PM | Skill 3 |
| ~~AMB-08~~ | ~~Response-time targets~~ | ~~A: P95 < 3 s list / < 1 s detail · B: tighter~~ | **RESOLVED at G1 — option A** | PM | ✅ closed 2026-08-14 |
| AMB-09 | Browser support baseline | A: modern evergreen only · B: include legacy IE-era | A | PM | Skill 3 |

Carried from Skill 01 and still open: **OI-02** (audit retention term) blocks NFR-OPS-AUDIT-02 and CONST-LEGAL-02.

---

## 3. Corrections to earlier analysis

| Item | Correction |
|------|-----------|
| Proposal RISK-007 | Recorded as "legacy sources are EUC-KR/CP949, conversion required". **Wrong** — the files are UTF-8. The mojibake seen during the Skill 01 scan was a reading error on my side. RISK-007 has been rewritten to describe the actual issue: isolated byte corruption in a few files (e.g. `type="tex<Hangul>t"` in `biztalk_admin_40_view.jsp` line 96), which is a per-file repair, not a systematic conversion |

---

## 4. Method note

No runnable legacy environment exists, so every requirement in this slice was derived by reading source. Confidence is highest for the service contracts (`WSVC.*.xml`) and SQL (`IDO.*.xml`), which are declarative and complete, and lower for intent — *why* a rule exists cannot be recovered from code.

The nine defects were found by cross-checking layers against each other: the form against the JS, the JS against the service contract, the contract against the SQL. Every one of them sits in a **gap between layers** — a field the UI sends that the contract does not declare, a label that disagrees with the column it filters, a contract field no query populates. None would be visible from reading any single file, which is worth knowing for the remaining screens: the same cross-layer method should be applied to each.

---

# Part 2 — 로그인 (Authentication module), 2026-08-14

> Spec: [REQUIREMENTS-SPEC-LOGIN.md](REQUIREMENTS-SPEC-LOGIN.md) · Scope: `apm_0001_01`, `apc_login_proc`, `apm_1001_02/03`, `GoogleOTP.java`

## 5. Resolved

### AMB-L01 — Meaning of "Login with GoogleOTP only"
**Question.** The instruction admitted two readings: Google OTP as the *only permitted second factor* (login remains 이메일 + 비밀번호 + OTP), or *passwordless* login using ID + Google OTP alone. The difference determines whether the module has one factor or two, and whether the entire password subsystem (hashing, strength, 90-day cycle, forced change) exists at all.
**Candidates.** A: Google OTP is the only OTP method · B: passwordless · C: A + mandatory OTP on every account
**PM response.** **A — two factors retained; Google OTP is the only second factor.**
**Effect.** FR-LOGIN-001, NFR-SEC-AUTH-L01/L02. The biztalk `인증번호전송` SMS path is explicitly excluded from login. Option B would have removed L2, L6 and L9 outright, but would have left a single possession factor — below what 전자금융감독규정 expects for administrative access.

### AMB-L02 — OTP recovery for a lost device
**Question.** `apm_1001_03_r001_act.jsp` throws `ADM_00026` when `OTP_KEY` already exists, so re-registration is impossible. The legacy defines no recovery path; with Google OTP as the only second factor, a lost phone means a permanently unusable account.
**Candidates.** A: operator reset · B: self-service backup codes · C: keep legacy · D: 보류
**PM response.** **A — operator resets, user re-registers.**
**Effect.** FR-OTP-007/008, new use case [UC-LOGIN-003](use-cases/UC-LOGIN-003.md). Note this makes operator reset the sanctioned way to bypass a user's second factor, so the out-of-band identity check that guards it is a real control — and it lives outside the software.

## 6. Open

| ID | Question | Candidates | Working assumption | Owner | Needed by |
|----|----------|-----------|--------------------|-------|-----------|
| AMB-L03 | IP allowlist scope now that the portal is internet-facing — an allowlist suits internal operators but not external client companies | A: operators only · B: all users · C: disable | — none; genuinely undecided | PM | **G1** |
| AMB-L04 | Password history depth (FR-PWD-004) | A: 3 · B: 5 · C: none | A — 3 | PM | Skill 3 |
| AMB-L05 | Session inactivity timeout | A: 30 min · B: 15 min · C: 60 min | A — 30 min | PM | Skill 3 |
| AMB-L06 | Do the 90-day dormancy and 90-day password cycle apply to external tenant users, or only internal operators? | A: all · B: operators only · C: different thresholds | A — all | Domain owner | Skill 3 |
| AMB-L07 | Retain the admin-login notification at all? | A: retain, config-driven · B: replace with audit alerting · C: drop | A | PM | Skill 3 |
| AMB-L08 | May an operator reset their **own** OTP? | A: prohibited, second operator required · B: permitted | A — prohibited | PM | Skill 3 |

> **AMB-L03 is the one that should not slip past G1.** It interacts with RISK-006 (망분리): restricting operators by IP while leaving tenants open is effectively a network-tier split, which shapes the deployment topology decided at G2.

## 7. Corrections to earlier analysis

| Item | Correction |
|------|-----------|
| RISK-005 / ADR-008 — "legacy uses MD5" | **Partially wrong.** `weauth/security/md5` exists, but the actual admin login path (`apc_login_proc_act.jsp`) hashes with **unsalted SHA-256** via `JexMessageDigest.getHashString(SHA_256, pwd)`. Both are unfit for password storage — unsalted SHA-256 is a fast hash with no per-user salt and no work factor — so the conclusion (replace, cannot migrate) is unchanged, but the stated algorithm was incorrect. RISK-005 has been updated |

## 8. Method note

The same cross-layer method used for 문자내역 found ten defects here, but with a different signature: where the 문자내역 defects sat in **gaps between layers**, most of the login defects are **deliberately disabled controls** — security code that was written, then commented out and left in place.

- `kisalib.Cracklib` password strength — commented out (L6)
- `response.sendRedirect("/error.jsp")` for the IP allowlist — commented out (L5)
- `int window = 3` for OTP clock tolerance — commented out, replaced by `window = 0` (L3)
- `getQRBarcodeURL()` — retained but unused, still carrying the secret-over-HTTP flaw (L4)

Each leaves the surrounding code looking correct: the strength service is still called, the allowlist is still queried, the OTP still verifies. Reading the call sites alone would show a system with password strength checking and IP restriction. Only reading the implementations shows that neither does anything. **For the remaining screens, commented-out code should be treated as a finding to investigate, not as noise to skip.**
