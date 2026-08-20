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
> **Refined 2026-08-18 (CONFLICT-R01, Part 5).** As written this ruling is too broad: it was raised about external self-service and cannot govern internal operator screens, of which 이용기관 보고서 (screen 20) is the first encountered. It governs **tenant principals**. An operator role may request 전체 or any institution; tenant users are narrowed from the session exactly as ruled here. No requirement written under AMB-02 changes — 문자내역 is a tenant-facing screen and is unaffected. See FR-AZ-R03/R04.

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

---

# Part 3 — 이용기관관리 (Client Institution Management), 2026-08-14

> Spec: [REQUIREMENTS-SPEC-INSTITUTION.md](REQUIREMENTS-SPEC-INSTITUTION.md) · Scope: screen `biztalk_admin_00` and its registration/edit popup `biztalk_admin_01`

## 9. Resolved

### AMB-I01 — 중지 writes a table nothing reads
**Question.** `IDO.KKB_FT_FTIS_INFO_U001` updates `FT_INST_INFO`, while list (`_L001`), detail (`_L002`), upsert (`_C001`) and delete (`_D001`) all use `FT_FTIS_INFO`. Clicking 중지 shows "정상적으로 처리되었습니다" and the grid still reports 사용. Either the disable button has never worked, or four other queries are pointed at the wrong table.
**Candidates.** A: fix + audit live data · B: fix code only · C: `FT_INST_INFO` is correct
**PM response.** **A — fix, and raise an operational task to find institutions believed stopped but still active.**
**Effect.** FR-INSTL-001, FR-INSTL-009, CONST-DATA-I01. This is the highest-consequence finding in the slice: 사용여부 is what stops an institution from calling the send API, so a disable that silently does nothing means institutions may have kept API access after an operator deliberately withdrew it. The data audit is not a nice-to-have — it is the only way to find out whether that happened.

### AMB-I02 — 담당자관리 is a stub
**Question.** The tab is commented out in the JSP (line 61), `#btn_mngr_register` / `#btn_mngr_delete` have no event handlers, `fn_checkManager()` is an empty function, and `KKB_MNGR_LDGR` has only `L001` (list) and `L002` (count) — no create or delete IDO exists anywhere in the codebase. Is this abandoned or unfinished-and-wanted?
**Candidates.** A: exclude from scope · B: build out fully · C: port read-only
**PM response.** **A — exclude, re-plan separately.**
**Effect.** §2.6. There is no behaviour to port: the feature was never finished. Building it would mean designing a permission model from scratch, which is a requirements exercise, not a migration. **One caveat carried forward:** `biztalk_admin_00_l002` is a live, directly-callable endpoint that returns the full manager roster to any authenticated user, gated only by a client-side `alert('권한 없음')`. Excluding the screen must not mean quietly porting that endpoint as-is.

### AMB-I03 — Hard delete with no institution history
**Question.** 삭제 physically removes the institution and all its 발신번호 behind a "복구할 수 없습니다" warning. Sender numbers get a `KKB_DPNO_HIS` record; the institution deletion itself gets none, and 문자발송내역 keyed on `IS_CD` is orphaned.
**Candidates.** A: logical delete · B: hard delete + history · C: as-is
**PM response.** **A — logical delete with history.**
**Effect.** FR-INSTL-004…008, CONST-DATA-I02. Note this ruling has a structural consequence — see CONFLICT-I02.

### AMB-I04 — 인증키 is weak, plaintext and exposed
**Question.** `ATK` is ① generated in the browser by `Math.random()` (20 chars, from a handler named "generate random 32 byte"), ② stored in plaintext, ③ rendered unmasked in the list grid for every institution, and ④ returned in full by the duplicate-check endpoint for any guessed 기관코드 — a 6-character value with a fixed `K0` prefix, so 4 unknown characters. It is a live credential client companies present when calling the send API, so changing it breaks their integrations.
**Candidates.** A: stop exposure, keep keys · B: reissue all · C: harden new only
**PM response.** **A — preserve existing keys, close every exposure path, move generation server-side.**
**Effect.** FR-ATK-001…006, NFR-SEC-CRED-I01/I02. Option B is the only one that actually fixes the entropy problem, and the PM's reasoning — no unplanned outage for every client company — is sound. But it should be recorded plainly that this accepts a known-weak credential rather than eliminating it; see RESIDUAL-I01.

## 10. Conflicts raised

### CONFLICT-I02 — logical delete vs. "no DDL in scope"
**Conflict.** AMB-I03 rules for logical delete, which needs a delete flag and a deletion history table. CONST-DATA-01 in the 문자내역 spec constrains the programme to "the existing `BIZTALK_DB` schema is reused unchanged; no DDL migration in this scope". Both cannot hold.
**Raised under** harness §3 (충돌 식별 → PM 결재 의무), before the requirement was written.
**Working assumption.** The soft-delete ruling logically entails the DDL, so a **scoped DDL change for this module** is assumed approved, narrowing CONST-DATA-01 to "no DDL *for the 문자내역 slice*".
**Status.** **Needs explicit PM sign-off at G1.** This is not a formality: it sets the precedent for whether later slices may add schema, and CONST-DATA-01 was written as a programme-wide constraint. Approving it silently would leave the programme with two contradictory constraints on the record.

### RESIDUAL-I01 — accepted weak credentials
**Not a conflict, a recorded trade-off.** AMB-I04 closes every path by which 인증키 leaks, but the keys themselves were generated by a browser PRNG and remain in production use. Exposure goes to zero; entropy does not improve.
**Mitigation.** FR-ATK-005 makes per-institution rotation a first-class operation, so a future reissue campaign is an operational decision rather than a development project.
**Revisit.** Before the portal is exposed to the internet — this interacts directly with CONST-SEC-01 ("no endpoint may rely on network-perimeter protection as its access control").

## 11. Open

| ID | Question | Candidates | Working assumption | Owner | Needed by |
|----|----------|-----------|--------------------|-------|-----------|
| AMB-I05 | Cascade scope for logical delete — which `IS_CD`-keyed data (발신번호, 수수료, 템플릿, 문자발송내역) is blocked, retained or archived | A: block new activity, retain all history · B: per-table policy | A | Domain owner | Skill 3 |
| AMB-I06 | Canonical 기관코드 format. The UI enforces exactly 6 characters with a `K0` prefix; the service contract declares length 16. 사업자등록번호 length is unstated anywhere | A: 6 chars `K0`+4, BRNO 10 digits · B: the contract's 16 is canonical | A | Domain owner | Skill 3 |
| AMB-I07 | Response-time target for the institution list | A: P95 < 1 s (small table) · B: reuse the 3 s 문자내역 target | A | PM | Skill 3 |
| AMB-I08 | `BSNN_STTS_CKYN` gates `KKB_FT_FTIS_INFO_L003`, selecting institutions for what looks like a business-status check. Its relationship to 사용여부 cannot be recovered from code — the system may carry two independent enable flags | A: independent, out of scope here · B: must be reconciled | A | Domain owner | Skill 3 |
| AMB-I09 | 인증키 masking format, and who may reveal a full key (FR-ATK-002/003) | A: show last 4, reveal restricted to a senior operator role · B: never revealable, rotation only | A | PM | Skill 3 |

Carried and still open: **OI-02** (audit retention term) blocks NFR-OPS-AUDIT-I02 and CONST-LEGAL-02.

## 12. Method note

Nineteen defects, found by the same cross-layer reading used on the previous two slices — but with a third distinct signature.

- 문자내역 defects sat in **gaps between layers** (a field the UI sends that no contract declares).
- 로그인 defects were **deliberately disabled controls** (security code written, then commented out).
- 이용기관관리 defects are **controls that exist only in the browser**.

Every safeguard on this screen is implemented in JavaScript and in nothing else: the duplicate check is a JS variable (`DUP_CHECK_YN`), the permission gate is `alert('권한 없음')`, the 기관코드 format rule and the 사업자등록번호 digit rule are `fn_save()` validations, and the 인증키 itself is generated by `Math.random()` in the page. The server side of each is either absent or actively contradicts the client: while the JS is careful to block a duplicate 기관코드, the underlying IDO is a `WITH UPSERT … INSERT … WHERE NOT EXISTS` that will happily overwrite an existing institution — including its credential — for anyone who calls it directly.

The practical consequence is that **reading the JavaScript of a Jex screen tells you the intended business rules and nothing about the enforced ones.** For the remaining screens the rule should be: treat every client-side check as an unimplemented server requirement until the contract or the IDO proves otherwise.

Two findings also confirm that a defect class can repeat across slices, which is worth checking for directly rather than rediscovering:
- **`to_char` pattern typos.** D5 in 문자내역 was `YYYYY`; D-I9 here is `YYYYMMDD24MISS` with `HH` omitted, so every institution record carries a literal `24` as its hour. Both were invisible because the UI truncated the value before display. Every `to_char` pattern in the remaining IDOs should be swept.
- **Declared-but-absent pagination.** D7 in 문자내역 was commented-out paging; D-I10 here is paging declared in the contract, sent by the client, and simply never implemented in SQL — with the contract item id `PAGE_NO⟨tab⟩` carrying a stray tab character that would have prevented binding regardless.

## 13. Design-time resolutions (Skill 3, 2026-08-14)

Two questions were put to the PM during design. Both changed the plan materially.

### AMB-I10 — who enforces 사용여부 at the send API
**Question.** FR-INSTL-009 requires that a 미사용 or deleted institution cannot authenticate to the send API. But the send API is the legacy IRIS runtime, not this portal: it reads `FT_FTIS_INFO` and its `FINInstitution` cache directly. The portal can write the state; it cannot stop a send.
**Candidates.** A: portal writes state, legacy enforces, gap tracked · B: also change the legacy send path · C: new gate owned by this portal
**PM response.** **A — portal writes state; the gap is tracked.**
**Effect.** [ADR-INST-016](../design/adr/ADR-INST-016-legacy-coexistence.md), RISK-I02, TM-I013. FR-INSTL-009 is verified **to our boundary only**, and the test plan says so explicitly (C-06) rather than leaving a gap that looks covered. A named cutover action confirms the legacy honours non-`'Y'` status before decommissioning. This matters because **D-I1 is this exact failure mode already realised once** — an operator disabled an institution, believed it stopped, and it did not.

### CONFLICT-I02 — dissolved, not resolved
**Original conflict.** AMB-I03 ruled for logical delete, which was assumed to need a delete flag and a history table; CONST-DATA-01 forbids DDL. Skill 2 flagged it as requiring explicit G1 sign-off.
**What design found.** The premise was false. `FT_FTIS_INFO` **already has** a status column (`IS_STTS`), and a DB-backed `AuditService` **already exists** from the 로그인 slice. Deletion is `IS_STTS='D'` plus an audit event — **no DDL at all**.
**Candidates put to the PM.** A: `IS_STTS='D'` + existing audit · B: new columns + history table · C: archive table
**PM response.** **A.**
**Effect.** [ADR-INST-014](../design/adr/ADR-INST-014-lifecycle-state-model.md). CONST-DATA-01 stands unmodified, no precedent for schema change is set, and **CONFLICT-I02 is removed as a G1 condition.**

The reason A is better rather than merely cheaper is worth recording: option B's new column would be **invisible to every legacy reader**, so a deleted institution would stay fully active to the legacy send path. That is precisely the shape of D-I1. Reusing the status column makes the legacy fail safe by construction — `'D'` matches neither its `'Y'` nor its `'N'` filter — instead of by remembering to check.

> **Method note.** Both resolutions came from re-reading what already existed rather than from new legacy analysis. CONFLICT-I02 had been carried as a blocking governance item on the strength of an assumption nobody had tested against the schema. Worth generalising: **a conflict between two constraints deserves one pass to check whether it is real before it is escalated.**

---

# Part 4 — 이용기관 정보 관리 / 발신번호 (Sender Number Management), 2026-08-17

> Spec: [REQUIREMENTS-SPEC-SENDERNO.md](REQUIREMENTS-SPEC-SENDERNO.md) · Scope: screen `biztalk_admin_10` and its three popups `biztalk_admin_11` (상세/수정), `biztalk_admin_12` (등록), `biztalk_admin_13` (제거)

## 14. Resolved

### AMB-S01 — ownership verification is designed but disabled
**Question.** Sender-number ownership verification exists in every layer and works in none. `AUTH_NO` is a declared input on `WSVC.biztalk_admin_10_d001` and `…_12_c001`; the input fields are present but commented out of `biztalk_admin_12_view.jsp` and `…_13_view.jsp`; `sendMMSMessage()` and the session `DP_NO` check are commented out of both action JSPs; and `13.js` still binds a 인증번호전송 handler to an element that no longer exists. So registration and deletion currently proceed with no proof that the registering party has any right to the number. Is verification in scope for the new system?
**Candidates.** A: re-enable OTP · B: document-based approval workflow · C: keep as-is
**PM response.** **C — keep as-is, no verification.**
**Effect.** CONST-BIZ-D02, RESIDUAL-S01, §2.7. This is the one defect in the slice not being fixed, and it should be read as a scope decision rather than a technical one: the legacy already contains most of an OTP implementation, so option A was closer to "finish it" than "build it". The ruling stands and the work proceeds on it — but see RESIDUAL-S01 for what now carries the load instead, and for the condition under which it should be revisited.

### AMB-S02 — hard delete
**Question.** `IDO.KKB_DPNO_LDGR_D001` is a physical `DELETE`. A number removed this way cannot be recovered, and any send history referencing it loses its master record. The institution slice ruled for logical delete on the same question (AMB-I03).
**Candidates.** A: logical delete with DDL · B: hard delete + corrected history · C: suspend only
**PM response.** **A — logical delete, adding schema.**
**Effect.** FR-SNDD-001…003, FR-SNDD-008, CONST-DATA-D04. Two consequences follow that the ruling itself does not settle — CONFLICT-S01 and CONFLICT-S02 below. Neither was raised to block the decision; both need an answer before the requirement can be built.

### AMB-S03 — duplicate check is scoped to one institution
**Question.** `biztalk_admin_12_c001_act.jsp` checks for duplicates with `KKB_DPNO_LDGR_L001`, whose `WHERE` clause is `IS_CD = :IS_CD AND decrypt(DP_NO) = :DP_NO`. The check therefore only ever sees the requesting institution's own numbers, and the same 발신번호 can be registered by any number of institutions.
**Candidates.** A: globally unique · B: unique per institution · C: allow with warning and approval
**PM response.** **A — globally unique.**
**Effect.** FR-SNDC-004, CONST-BIZ-D01. This ruling gained weight after AMB-S01: with ownership verification declined, the uniqueness check is the only mechanism preventing one institution from registering a number that belongs to another. Migration must identify and resolve existing cross-institution duplicates before the constraint can be enforced — a data question, not a code one.

### AMB-S04 — sender numbers masked on the list, unmasked on detail
**Question.** `biztalk_admin_10_l001_act.jsp` passes every `DP_NO` through `RegexNameMasking.maskName()` — a utility written for personal names, whose English branch returns first-two-plus-last-one, so `01012345678` renders as `01********8`. The detail service `biztalk_admin_11_l001` returns the same number unmasked. Which is correct?
**Candidates.** A: show in full + audit reads · B: phone-specific masking rule · C: mask by role
**PM response.** **A — show in full, audit read events.**
**Effect.** FR-SND-006, FR-SND-011, NFR-OPS-AUDIT-D01. This question was raised as a display-policy question and turned out to be the slice's most serious defect — see the method note (§17) and D-S1.

## 15. Conflicts raised

### CONFLICT-S01 — logical delete vs. "no DDL in scope"
**Conflict.** AMB-S02 rules for logical delete. `KKB_DPNO_LDGR` has nine columns — `IS_CD`, `DP_NO`, `RGDT`, `RGSR_ID`, `RGSR_NM`, `UDDT`, `UDT_ID`, `UDT_NM`, `DSCP` — recovered from the union of all six queries against it. **None of them carries state.** CONST-DATA-01 constrains the programme to "the existing `BIZTALK_DB` schema is reused unchanged; no DDL migration in this scope". Both cannot hold.
**Raised under** harness §3 (충돌 식별 → PM 결재 의무), before the requirement was written.
**Checked first.** The institution slice's CONFLICT-I02 was an identical-looking conflict that **dissolved** once someone checked the schema — `FT_FTIS_INFO` already had `IS_STTS`. That precedent made checking mandatory here rather than escalating on the pattern. It was checked. There is no status column, no spare column that could carry state without overloading a business field, and no existing audit store that could stand in for one the way the 로그인 slice's `AuditService` did. **This conflict is real.**
**Working assumption.** A scoped DDL change for this module is assumed approved.
**Status.** **Needs explicit PM sign-off at G1.** Substantively rather than procedurally: CONFLICT-I02 was resolved the way it was specifically to avoid setting a precedent for schema change, and this ruling sets it. Approving it silently would leave two contradictory records on the programme's position.

### CONFLICT-S02 — a new status column is invisible to the legacy
**Conflict.** If logical delete is implemented as a status column this project adds, the legacy send path will not filter on it. A number an operator has deleted stays valid as a caller ID.
**Why this is not hypothetical.** It is D-I1 exactly — an operator withdrew something, the system reported success, and the thing kept working — and ADR-INST-014 chose its representation specifically to make the legacy fail safe by construction rather than by remembering to check. The same reasoning has to be applied here, and a new column is the one option that fails it.
**Raised** before the requirement was written; recorded as FR-SNDD-003 with cutover verification rather than assumed.
**Status.** Resolution belongs to Skill 3 — carried as AMB-S05.

### RESIDUAL-S01 — accepted: registration without proof of ownership
**Not a conflict, a recorded trade-off.** Under AMB-S01 a sender number is registered on an operator's assertion alone. A sender number determines what recipients see as the origin of a message, and 전기통신사업법 / KISA 발신번호 사전등록제 expect the registering party to demonstrate a right to it.
**What carries the load instead.** FR-AZ-D01…D05 (only authorized operators can register, enforced server-side) and FR-SNDC-004 (global uniqueness, so a number already claimed cannot be taken). Neither existed before this specification — the legacy had no server-side authorization at all and a per-institution duplicate check — so the control environment is materially stronger than today even under option C.
**Residual exposure.** **First claim wins.** An authorized operator can register a number belonging to a third party, provided nobody claimed it first. Nothing in this specification detects that.
**Revisit.** Before registration is exposed to client-company self-service. Every compensating control above assumes the actor is a vetted internal operator; the moment that assumption goes, so does the mitigation. Also interacts with CONST-SEC-01.

## 16. Open

| ID | Question | Candidates | Working assumption | Owner | Needed by |
|----|----------|-----------|--------------------|-------|-----------|
| AMB-S05 | How logical delete is represented so the legacy send path honours it (CONFLICT-S02) | A: new column + change the legacy read · B: a representation the legacy already rejects · C: portal writes state, gap tracked as a cutover risk | A | Architect | Skill 3 |
| AMB-S06 | The authoritative list of special/emergency numbers barred from registration. The UI names 112, 114 and 1335 as examples; no complete list exists in code | A: adopt the KISA/KAIT published list · B: internally maintained list | A | Domain owner | Skill 3 |
| AMB-S07 | Whether a cap applies on 발신번호 per institution. None exists today | A: no limit, monitor · B: configurable cap | A | Domain owner | Skill 3 |
| AMB-S08 | Cascade when an institution is logically deleted. `KKB_DPNO_LDGR_D002` hard-deletes every number for an `IS_CD`, contradicting FR-SNDD-001. Overlaps institution-slice AMB-I05 | A: institution delete blocks the numbers without removing them · B: cascade the logical delete | A | Domain owner | Skill 3 |
| AMB-S09 | Whether `RGSR_ID`/`UDT_ID` should hold an internal user ID rather than an email address, and how existing rows migrate | A: internal user ID, email only in the user master · B: keep email, encrypt at rest | A | Architect | Skill 3 |

Carried and still open: **OI-02** (audit retention term) blocks NFR-OPS-AUDIT-D02 and CONST-LEGAL-02.

## 17. Method note

Twenty-one defects. The signature of this slice is different again from the three before it, and the difference is worth stating because it changes what analysis has to look for.

- 문자내역 defects sat in **gaps between layers**.
- 로그인 defects were **deliberately disabled controls**.
- 이용기관관리 defects were **controls that exist only in the browser**.
- 발신번호 defects are **layers that were each changed correctly and are now wrong together.**

D-S1 is the clearest case and the most serious finding in the slice. Read-masking was added to the list service — a defensible privacy change, correct in isolation. Deletion matches rows on the decrypted number — correct in isolation, and correct at the time it was written in 2021. The grid passes whatever the list gave it to whatever the delete takes — correct in isolation. Compose the three and deletion matches nothing, `DELETE` affecting zero rows raises no error, the history insert still writes a row, and the operator is told "정상적으로 처리되었습니다". Every layer is defensible; the system is broken. The IDO version stamps (`20251017`) put the masking change four years after the delete logic, so this most likely broke on a release in October 2025 and has been silently failing since.

Three practical consequences:

1. **A per-file review would not have found it.** Nothing is wrong in `biztalk_admin_10_l001_act.jsp`, or in `KKB_DPNO_LDGR_D001`, or in `biztalk_admin_10.js`. The defect exists only in the path between them, and only after a specific date. Defects of this class are found by tracing a value end-to-end, which is now a standing check for the remaining screens: **for every value that leaves the server and comes back, ask whether it came back in the same form it left.**
2. **The ledger cannot be trusted to reflect operator intent.** Numbers believed deleted are probably live and still valid for sending. §6.5 of the spec raises the reconciliation — this is the second slice in a row to produce one (D-I1 was the first), and both have the same shape: an operator withdrew something, the system said yes, and nothing happened.
3. **Silent success is the property to design against, not the specific bug.** NFR-OPS-D02 and FR-SNDD-002 are written to make any zero-effect write an explicit failure, because the masking/decrypt mismatch is one way to reach that state and there is no reason to think it is the only one.

Two other observations:

**A question about display policy uncovered the defect.** AMB-S04 was raised as "which of these two screens is right about masking?" — a UI-consistency question. Following it into the delete path is what surfaced D-S1. Worth generalising: **an inconsistency between two screens showing the same field is a signal that something downstream consumes one of them.**

**Repeat defect classes confirmed again.** Both patterns flagged in the institution slice recur here, which supports checking for them directly rather than rediscovering them:
- **Declared-but-absent pagination** — third occurrence (D7, D-I10, now D-S14). Here the client sends `PAGE_NO`/`INQ_TOTL_NCNT`, the contract declares neither, the SQL has no `LIMIT` and no `ORDER BY`, and the JSP hides the paging widget.
- **Copy-paste error checking** — `biztalk_admin_10_d001_act.jsp` and `biztalk_admin_12_c001_act.jsp` both test `idoOut1` after executing `idoIn2`, so history-write failures are swallowed in two of the slice's three write paths (D-S7). Every action JSP with more than one `execute()` should be swept for this.

One finding is a plain reminder that dead code is not always harmless. `biztalk_admin_10.js` binds a 수정 handler, toggles that button's visibility, and opens a detail popup — for a button the JSP never renders (D-S8). Reading the JavaScript would tell you 발신번호 descriptions are editable. They have never been editable. As with the institution slice: **the JavaScript of a Jex screen describes intent, not behaviour.**

## 18. Design-time resolutions (Skill 3, 2026-08-17)

One open item closed, one conflict narrowed, and three findings that came from reading applications outside this repository. All three changed the plan rather than confirming it.

### AMB-S05 — how logical delete is represented so the legacy honours it
**Question.** CONFLICT-S02 warned that a status column this project adds would be invisible to the legacy send path, leaving "deleted" numbers sendable. Skill 2 raised it as a suspicion and carried it to design.
**What design found.** It is fact, with the code to prove it. The send runtime is `KAKAOTALK`; five of its send actions validate the caller ID with `select dp_no from kkb_dpno_ldgr where is_cd = :is_cd and decrypt(dp_no) = :dp_no` and reject when nothing returns (`ADV_KKO_AT_SEND_act.jsp:100-103`). **It selects no status of any kind** — a row is present or absent, and there is no third state it can observe. So a status flag would have made the legacy fail *open*.
**Candidates put to design.** A: status column + change `KAKAOTALK` · B: archive-on-delete · C: hard delete, history only · D: status column, portal-only
**Resolution.** **B — the row moves to a new archive table `KKB_DPNO_ARCV`.** [ADR-SND-017](../design/adr/ADR-SND-017-senderno-lifecycle.md).
**Effect.** FR-SNDD-001…003, FR-SNDD-008. Absence is already the legacy's rejection condition, so the legacy fails safe **by construction with no legacy change at all** — the ADR-INST-014 principle, reached by the opposite mechanism. There, an existing status column made `'D'` safe; here, the absence of one made removal the only safe representation.

### CONFLICT-S01 — narrowed, not dissolved
**Original conflict.** Logical delete vs CONST-DATA-01 ("no DDL in scope"). Skill 2 flagged that it sets the schema-change precedent CONFLICT-I02 was resolved specifically to avoid.
**Checked first, per the CONFLICT-I02 precedent.** `KKB_DPNO_LDGR` has nine columns — none carries state, none can be overloaded without corrupting a business field, and no existing audit store substitutes the way the 로그인 slice's `AuditService` did for the institution slice. **It does not dissolve.** Some DDL is unavoidable.
**But the shape changed.** ADR-SND-017 needs one *new table*; ADR-SND-018 needs one *index*. `KKB_DPNO_LDGR` is never altered in a way that changes what an existing reader sees, and no legacy application is modified.
**Status.** **Still needs explicit G1 sign-off, on narrower terms:** the precedent is *"this programme may add tables"*, not *"this programme may alter shared schema"*. Needed before task S2-02; Sprint S1 carries no DDL, so the first two weeks proceed either way.

### New finding — `AOA_ADMIN` is a second writer on the same database
`AOA_ADMIN` carries the same four screens against the same `<target>BIZTALK_DB</target>`. Two consequences:
- **Global uniqueness cannot be enforced in application code.** `AOA_ADMIN` would bypass any check we write, so the constraint moves into the database ([ADR-SND-018](../design/adr/ADR-SND-018-encrypted-number-uniqueness.md)). This ruled out an otherwise attractive no-DDL option (advisory lock + application check) that would have been correct if ours were the only writer.
- **All 21 defects stay reachable through that console after we ship**, on the same data — RISK-S05. Its disposition is a programme-level question this slice cannot answer.

### New finding — one send path validates nothing
`ADV_KKO_FT_SEND_act.jsp` references `sender_number` three times and performs **no** ledger check, while its `_BULK` and `_M` siblings do. (The three `FU`/친구톡 actions use no sender number at all, so their lack of a check is correct rather than a gap.) **FR-SNDD-003 therefore holds for five of six send paths and cannot be made to hold for the sixth from here.** Recorded as threat T-X1 and RISK-S03, and asserted by test C-S06 — a test written to confirm the gap *persists*, which inverts into a regression guard once `KAKAOTALK` closes it.

### New unknown — `BIZ_DB` vs `BIZTALK_DB`
The admin consoles declare `BIZTALK_DB`; `KAKAOTALK` declares `BIZ_DB` for the same table. These are datasource aliases and source cannot settle whether they resolve to one physical database. **ADR-SND-017's whole mechanism assumes they do.** Task S1-03 and test C-S04 resolve it before Sprint S2 starts; if they differ, the delete design is re-derived. RISK-S01.

> **Method note.** Every one of these came from reading `KAKAOTALK` and `AOA_ADMIN` — applications outside this repository that were never listed as inputs to the slice. The requirements analysis had correctly identified *that* a coexistence question existed (CONFLICT-S02); it could not have answered it, because the answer was not in the artifacts under analysis.
>
> Worth generalising for the remaining slices: **when a table is written by this system and read by another, the other system's queries are part of the specification.** The institution slice learned the same lesson at design time (AMB-I10, ADR-INST-016) and it has now recurred with a sharper edge — here the external reader's query did not merely need accommodating, it *selected between two designs*, and the one that reads more naturally on paper was the wrong one.

---

# Part 5 — 이용기관 보고서 (Institution Usage Report), 2026-08-18

> **Slice**: legacy screen 20 and its Excel export. Specification: [REQUIREMENTS-SPEC-REPORT.md](REQUIREMENTS-SPEC-REPORT.md).
> Twenty-seven defects — twenty-five in the slice, two upstream in `BATCH_BIZTALK_DAILY`. Four questions put to the PM, one of them a conflict with a standing G1 ruling. All four answered 2026-08-18.

## 19. Resolved

### CONFLICT-R01 — a cross-institution report vs server-derived tenancy
**Conflict.** AMB-02 was ruled at G1 as "institution scope is server-enforced from the session; a client-supplied `IS_CD` is ignored, not merely validated." Screen 20 exists to compare institutions and its selector defaults to 전체. Under AMB-02 as written the screen cannot exist at all. Raised under harness §3 (충돌 식별 → PM 결재 의무) before any requirement was written.
**Candidates.** A: operator role sees 전체 and any institution, tenant users narrowed from session · B: apply AMB-02 unchanged, drop 전체, build a separate operator report · C: keep legacy behaviour, waive AMB-02 here
**PM response.** **A — operator role only.**
**Effect.** FR-AZ-R03, FR-AZ-R04. AMB-02 is **refined, not superseded**: it governs *tenant* principals, which was always its intent — it was raised about external self-service, and screen 20 is an internal operator tool. The dropdown is filtered by role, and 전체 is a permission rather than a default.

### AMB-R01 — two databases, and a report that only reads both in production
**Question.** The report reads `BIZTALK_DB` (API 발송) and `BIZTALK_BULK_DB` (대량발송). The legacy queries the bulk source **only when `TSTCL_DV=REAL`**, then concatenates the two row sets under a 구분 column and writes them to separate Excel sheets. Should the new system merge them, and what happens to the environment flag?
**Candidates.** A: merge with a 발송구분 filter, flag removed · B: keep rows separate, remove only the flag · C: exclude bulk entirely
**PM response.** **A — merge, with an API / 대량 / 전체 filter defaulting to the summed total.**
**Effect.** FR-RPTS-001…005. The `TSTCL_DV` branch is removed rather than repaired, which is the point: it appears in **four** places (both actions, the export view, and the batch) and each one silently changes what the system does. FR-RPTS-004 is therefore written as a property — *no configuration flag varies the read path* — instead of as four fixes.
**Consequence not anticipated when the question was asked.** Merging plus server-side pagination plus a total order cannot all hold across two physical databases. That is CONFLICT-R02, below.

### AMB-R02 — a fourth counter that nothing displays
**Question.** `KKB_APITR_SMTN` stores four counters per channel — 전체 / 성공 / 실패 / **처리중**. The grid shows three, the Excel summary sheet shows two. `*_PCSNG_CNT` is declared on both service contracts, returned by both queries, and rendered nowhere. So 전체 ≠ 성공 + 실패 on any row with messages in flight, and a reader who adds the columns up finds the report wrong.
**Candidates.** A: add 처리중 columns and assert the identity · B: keep the display, add an explanatory note · C: add a success-rate metric instead
**PM response.** **A — add 처리중.**
**Effect.** FR-RPT-009, FR-RPT-010, FR-RPTX-007. FR-RPT-010 goes further than the ruling and makes the identity an **asserted** rule with a standing reconciliation check, so a row that fails it is reported as a data-quality error rather than displayed as fact. That is deliberate: §7 shows the aggregate can be wrong in ways nothing currently detects, and the arithmetic identity is the cheapest detector available.

### AMB-R03 — no cap on the query period
**Question.** The only period check is `Number(startDt) > Number(endDt)` in the browser. The contract declares `START_DT`/`END_DT` with neither length nor type and the action validates nothing, so a direct call scans both tables end to end. The 문자내역 slice was capped at 31 days (AMB-06), but this is a daily-aggregate report with monthly and yearly comparison needs.
**Candidates.** A: 366 days · B: 92 days · C: 31 days, consistent with AMB-06
**PM response.** **A — 366 days.**
**Effect.** FR-RPT-002, NFR-PERF-R02. The cap is larger than 문자내역's because the row grain is different — one row per 일자 × 기관 rather than one per message — so a year here is smaller than a month there. **Server-side pagination stops being optional**: at the cap, 전체 over 366 days is on the order of 366 × the institution count, which is exactly the unbounded fetch D-R8 already permits.

## 20. Conflicts raised

### CONFLICT-R02 — cross-source merge vs server-side pagination *(open, blocks Skill 3)*
**Conflict.** Three requirements cannot all hold if `BIZTALK_DB` and `BIZTALK_BULK_DB` are separate physical databases: FR-RPTS-003 (전체 sums both sources into one row), FR-RPT-005 (pagination is server-side), FR-RPT-006 (ordering is total and deterministic across the merged set). Neither database can produce a merged, ordered, paginated result alone.
**Candidates.** A: fetch both fully and merge in the application — reinstates the unbounded fetch D-R8 exists to remove · B: federated query (FDW / linked server) — pushes the merge into the database, adds an infrastructure dependency · C: consolidated read store fed by the batch — clean, but needs DDL and collides with CONST-DATA-R02
**Working assumption.** A, with the cap and pagination applied per source before merging — correct whenever a single institution is selected, degrading only for 전체 over a wide range.
**Status.** **Gated on AMB-R04.** Checked for reality before escalation, per the CONFLICT-I02 precedent: the two IDOs declare different `<target>` values, which proves two configured *datasources*, not two physical *databases*. If they resolve to one instance the conflict dissolves into an ordinary `UNION ALL` + `GROUP BY`. This is the same unknown, in the same shape, as the `BIZ_DB` vs `BIZTALK_DB` question carried out of the 발신번호 slice — and it should be settled by the same check rather than twice.

## 21. Open

| ID | Question | Candidates | Working assumption | Owner | Needed by |
|----|----------|-----------|--------------------|-------|-----------|
| ~~AMB-R04~~ | ~~One physical database or two?~~ **RESOLVED at design — assumed two (inference, SEC-002 blocks the authoritative file); CONFLICT-R02 dissolved regardless, §24** | A: one instance, merge in SQL · B: two instances, merge in the application | A — same working assumption as the `BIZ_DB` question, and settled by the same check | Architect | Skill 3 |
| ~~AMB-R05~~ | ~~Async export threshold~~ **RESOLVED at design — cap enforced, async deferred (ADR-RPT-023); value set by load test L-R03/L-R04, §24** | A: 50,000 rows · B: derived from the measured 60 s target (NFR-PERF-R03) | B — set from measurement rather than guessed | PM | Skill 3 |
| AMB-R06 | May tenant users see 처리중 counts, or operators only? In-flight figures expose queue state and therefore backlog | A: visible to both · B: operators only | A — the tenant's own in-flight count is their own data | PM | Skill 3 |
| ~~AMB-R07~~ | ~~Where is the batch watermark recorded?~~ **RESOLVED at design — derived as max(TRDD) per source; batch run history rejected as untrustworthy per D-R27 (ADR-RPT-022), §24** | A: batch writes a run-status row (needs DDL — see CONST-DATA-R02) · B: derive from `max(TRDD)` per source | B until AMB-R04 and the DDL question settle; B cannot distinguish "aggregated, genuinely zero" from "not aggregated" | Architect | Skill 3 |
| AMB-R08 | Must the merged 전체 view retain a per-source breakdown for billing reconciliation (BR-012 수수료)? | A: 발송구분 filter is sufficient · B: breakdown always retained in the export | A | Domain owner | Skill 3 |
| OI-R01 | Ownership and repair of D-R26 / D-R27 in `BATCH_BIZTALK_DAILY`, and whether the T-4 lag is deliberate | A: batch slice takes both · B: T-4 confirmed as intended, only D-R27 repaired | A | PM | Before cutover |

Carried and still open: **OI-02** (audit retention term) blocks NFR-OPS-AUDIT-R01.

## 22. Corrections to earlier analysis

| Item | Correction |
|------|-----------|
| AMB-02 (Part 1) | Recorded as "institution scope is server-enforced from the session" without qualification. **Too broad as written** — it was raised about external self-service and cannot govern internal operator screens, of which screen 20 is the first encountered. Refined by CONFLICT-R01 to apply to tenant principals. No requirement written under it changes; the 문자내역 slice is a tenant-facing screen and is unaffected |
| BR-011 (Skill 01) | Recorded as "search results on list screens are exportable to Excel … screens 20/30". Accurate, but it does not convey that on screen 20 the export is a **second, separately-implemented data path** with its own contract, its own parameter handling and its own defects (D-R3, D-R4, D-R10). Export is not a rendering of the list; it is a parallel service that must carry every rule the list carries |

## 23. Method note

Twenty-seven defects, and a fifth distinct signature.

- 문자내역 defects sat in **gaps between layers**.
- 로그인 defects were **deliberately disabled controls**.
- 이용기관관리 defects were **controls that exist only in the browser**.
- 발신번호 defects were **layers that were each changed correctly and are now wrong together**.
- 이용기관 보고서 defects are **behaviour that differs between environments** — so the system that was tested is not the system that shipped.

`JexConst.getProperty("TSTCL_DV")` is consulted in four places in this slice: both action JSPs, the export view, and the daily batch. Each one decides whether bulk data exists. The consequences compound rather than repeat:

- Off production the response carries `REC` (a record list); on production it carries `REC2` (a string built by `Arrays.toString` and parsed with `JSON.parse`). **Two different contracts under one service id** (D-R5, D-R6).
- Off production rows are ordered by SQL `ORDER BY TRDD` ascending; on production by a Java comparator descending. **Two different orderings** (D-R7).
- Off production the export view iterates `REC2`/`REC3` that the action never created, because the view's guard is `if(true)` while the action's is the flag. **The export is broken everywhere except production** (D-R4).

The practical consequence is stark and worth stating plainly: **no test environment can exercise the production code path of this screen.** Whatever verification preceded any release, it did not verify what shipped. This is why the AMB-R01 ruling removes the flag rather than correcting its four call sites, and why FR-RPTS-004 is phrased as a property of the system — *no configuration flag varies the read path* — which a code review can check once instead of four times.

**A new standing check for the remaining screens.** The 발신번호 slice produced "for every value that leaves the server and comes back, ask whether it came back in the same form it left." This slice adds: **for every branch on an environment or configuration property, ask what the other branch does — and whether anyone has ever run it.** A flag consulted in one place is a feature toggle; a flag consulted in four is a second implementation nobody maintains.

**The silent-success class is now confirmed in four consecutive slices.** D-I1 (institution disabled, still active), D-S1 (number deleted, still live), and now D-R27: the bulk aggregation deletes a day, fails to reinsert it, catches its own exception, logs it, and **reports success**. The report then renders the missing day as zero, which is indistinguishable from a genuinely quiet day. Three different mechanisms, one shape: *the system reports that an operation succeeded when its effect is absent.* It is no longer reasonable to treat these as separate findings — the pattern should be assumed present in every remaining slice until a specific check disproves it.

**Two findings came from outside the slice's declared inputs.** `BATCH_BIZTALK_DAILY` was not listed as an artifact of screen 20 — it is a batch, and screen 20 is a report. But it is the *only* writer of the only table the report reads, and reading it explained something no amount of screen analysis could: why the production screenshot shows "조회된 내용이 없습니다" for the default date range. The batch's default run aggregates one day, four days back (D-R26), so the screen's own defaults — today to today — target a window that by construction contains nothing. The screen is not broken; it is honest about data that does not exist yet, and it fails to say so.

This continues the pattern the 발신번호 slice recorded at design time: **when a table is written by one system and read by another, the other system is part of the specification.** There it was a downstream reader that selected between two designs. Here it is an upstream writer that bounds what the report can truthfully promise — which is why FR-RPT-013 exists at all, and why it is a Must rather than a nicety.

**One observation on severity.** This is the first slice with **no PII**, and it would be easy to read that as lower risk. The opposite is true. D-R1 and D-R2 combine into an unauthenticated, single-request dump of every customer's message volume by day and channel — enough to infer each institution's customer base, campaign calendar and growth rate. No slice so far has produced a disclosure of comparable commercial value, and none has made it this easy to obtain. **Absence of personal data is not absence of confidentiality**, and RISK-R04 asks for the access logs to be reviewed on the assumption that it may already have been taken.

## 24. Design-time resolutions (Skill 3, 2026-08-18)

One conflict dissolved, one open item answered without reading protected configuration, and two PM rulings that set scope. The first is the significant one: it removed work rather than adding it.

### CONFLICT-R02 — dissolved, not resolved
**Original conflict.** Merging both sources (FR-RPTS-003), server-side pagination (FR-RPT-005) and a total deterministic order (FR-RPT-006) appeared mutually unsatisfiable across two physical databases. Skill 2 escalated it as blocking Skill 3, with three candidate shapes and a working assumption of the weakest — fetch both sources fully and merge in the application, which is the unbounded fetch D-R8 exists to remove.
**What design found.** A fourth shape none of the three considered. Both sources carry the same table, the same primary key and the same sort key, so two identically ordered streams can be **merge-joined with keyset pagination**: each source seeks independently in SQL using its own index, and the application holds at most two page-buffers. No DDL, no federated link, no new infrastructure.
**Candidates put to design.** A: fetch both fully · B: postgres_fdw / dblink · C: consolidated read store fed by the batch · D: keyset-paginated streaming merge
**Resolution.** **D.** [ADR-RPT-021](../design/adr/ADR-RPT-021-cross-source-aggregation.md).
**Why it dissolves rather than resolves.** **The design is correct under both answers to AMB-R04.** Two databases → the merge ships as designed. One database → identical semantics collapse to `UNION ALL … GROUP BY` behind the same interface, same sort key, same page contract, and only the mapper changes. No requirement, test or API shape is contingent on the answer, so the conflict has no remaining decision content and does not reach G2 as a condition.
**A side effect worth recording.** FR-RPT-006 is promoted from a display property to a **correctness precondition** — the merge is only right if both streams arrive in the same order. In the legacy, ordering was cosmetic and differed by environment (D-R7). It is now load-bearing, and the property suite tests it as such.

### AMB-R04 — answered by inference, because the authoritative answer is protected
**Question.** Are `BIZTALK_DB` and `BIZTALK_BULK_DB` one physical database or two?
**Why it was not simply looked up.** The datasource definitions live in `jex.iris_admin.xml`, which is declared under `JEX.config.file` in `jex.prop`. **SEC-002 applies and the file was not read.**
**What source alone shows.** Four IDOs target the bulk datasource against one table; sixty target `BIZTALK_DB`. None of the four references `FT_FTIS_INFO`. Decisively, the bulk aggregate query is character-for-character identical to the non-bulk one **except** that it replaces the correlated 기관명 subquery with `'' AS IS_NM` — and then Java patches the names back in from a `BIZTALK_DB` query. Nobody writes that if the master table is reachable from the bulk datasource.
**Resolution.** **Working assumption flips from A (one instance) to B (two).** Confirmed empirically by task R1-01, which asks the DBA rather than reading protected configuration.
**Effect.** None on design, by construction (above). R1-01 decides whether the merge iterator is built or deleted — simplicity, not viability. This is the material difference from RISK-S01 in the 발신번호 slice, where the equivalent unknown was a hard gate that could have forced ADR-SND-017 to be re-derived.

### OI-R01 — the batch stays out of slice
**Question.** D-R26 (data at best T-4; a single day cannot be re-aggregated) and D-R27 (bulk failures swallowed while the batch reports success) belong to `BATCH_BIZTALK_DAILY`. FR-RPT-013 also needs a completion watermark the batch records nowhere. Does the report slice take the batch?
**Candidates.** A: report-side only, degrade honestly · B: take the batch, add a run-status table (DDL) and fix both defects · C: watermark only, defer the fixes
**PM response.** **A — report-side only.**
**Effect.** [ADR-RPT-022](../design/adr/ADR-RPT-022-aggregation-freshness.md). Watermark derived as `max(TRDD)` per source; days above it are 미집계 rather than 0; a wholesale source gap is flagged heuristically (FR-RPTS-005). **The blind spot is documented rather than papered over**: `max(TRDD)` cannot detect an *interior* day that D-R27 deleted and failed to reinsert, because it sits below the watermark and is indistinguishable from a quiet day.
**One rejected option is worth recording.** Sourcing the watermark from the batch's own run history was considered and rejected **because D-R27 means that history lies** — it records success for runs whose bulk aggregation failed. An unreliable narrator is worse than an admitted gap, and this is the first time in the programme that an existing record was rejected as a data source specifically because a known defect makes it untrustworthy.

### AMB-R05 / FR-RPTX-010 — enforce the cap, defer async
**Question.** FR-RPTX-010 (Should) wants oversized exports generated asynchronously with notification. The programme has no job store, no queue and no notification channel.
**Candidates.** A: streamed synchronous export with a hard row ceiling, async deferred · B: build async infrastructure now · C: synchronous with no ceiling, relying on the 366-day cap alone
**PM response.** **A.**
**Effect.** [ADR-RPT-023](../design/adr/ADR-RPT-023-export-generation.md). SXSSF streaming, bounded window, hard ceiling; oversize requests refused with an actionable message naming a range that fits — **never a truncated file**, since a silently short workbook is the export form of the silent-success failure this programme has now met four times. **AMB-R05's value is measured, not decided**: the ceiling is the largest export holding NFR-PERF-R03 with a flat heap, fixed by load tests L-R03/L-R04. RISK-R07 asks for those tests early in R2 so a low result leaves room to reopen the async decision.
**FR-RPTX-010 stays in the specification at `Should`, unbuilt, with the ADR named as the reason** — not quietly downgraded.

### New finding — the export could not have reported its own failure
Not an open question, but it changed the delivery mechanism and is worth recording. D-R16 was written up in Skill 2 as "export failures are invisible — the download posts to a hidden iframe." Design found that this is **not fixable by adding error handling**: a form posted to an invisible iframe gives the browser no handle on the response, so a server error is structurally unreachable by the page. FR-RPTX-011 therefore forced the delivery mechanism to change to `fetch` + blob, which was not anticipated in the requirement.

Worth generalising: **a requirement that says "surface the error" is a requirement about the transport, not about the handler**, whenever the existing transport discards the response.

### Method note
Two of the four resolutions above came from re-reading what the slice already had rather than from new investigation — CONFLICT-R02 dissolved by noticing that two sorted streams with a shared key admit a merge-join, and the watermark's rejected option identified by noticing that a defect already recorded in Skill 2 disqualifies a data source Skill 3 wanted to use.

That is now the third slice where escalating a conflict and then re-examining its premise produced a materially better answer than the escalation's own candidates (CONFLICT-I02, CONFLICT-S01 narrowed, now CONFLICT-R02 dissolved). The institution slice's note said **"a conflict between two constraints deserves one pass to check whether it is real before it is escalated."** This slice refines it: the premise here was *true* — there really are two databases — but the conclusion did not follow from it. **Check the inference, not only the premise.**


---

# Part 6 — 톡전송 내역 (BizTalk Transaction History), 2026-08-19

> **Slice**: legacy screens 30, 32, 31 and the 30_spreadsheet export. Specification: [REQUIREMENTS-SPEC-TALK.md](REQUIREMENTS-SPEC-TALK.md).
> Thirty-four defects, five of them Critical — the highest count and the highest severity of any slice so far. Four questions put to the PM, one of them a conflict with an approved planning document. All four answered 2026-08-19.

## 25. Resolved

### SCOPE-T01 — what this screen actually is
**Question.** The menu reads 톡전송 내역 and the heading reads BizTalk 내역, but `IDO.KKB_APITR_HSTR_L001` selects from `FT_APITR_HSTR` — the shared Open-API transaction log for every fintech API — with no predicate restricting it to BizTalk, and `IDO.KKB_OPENAPI_INFO_L002` fills the API selector from `FT_OPENAPI_INFO` with `WHERE 1=1`. The production screenshot supplied with the request shows rows whose API is `ADV_COM_GET_STATUS`, which is not a talk-send service. Until this is settled, "parity" has no definition: reproducing the query contradicts the name, and honouring the name changes the result set.
**Candidates.** A: BizTalk-only, filter to talk APIs · B: general API log, rename the menu · C: one screen with an 업무구분 selector
**PM response.** **A — BizTalk-only.**
**Effect.** FR-TLK-002. Rows visible today whose API is not a talk service disappear from the rebuilt screen — an accepted, visible deviation from literal parity, recorded as CONFLICT-T02 and mitigated by RISK-T05. It also creates a new problem the legacy did not have: the BizTalk code set must be *defined*, and it is data rather than code (AMB-T03).

### CONFLICT-T01 — PROJECT-PROPOSAL §5.1 vs. what screen 30 is
**Conflict.** The approved proposal classifies legacy 30 (거래내역조회) as a **[Tenant]** screen — one of the four client-facing menus carrying the self-service value proposition. Static analysis shows the screen has no institution input, no institution predicate, and reads `FT_APITR_HSTR`, whose columns include `FIN_ACNO`, `ACNO`, `CANO`, `FIN_CARD`, `TRAM`, `BRNO` and `RSPN_TLGR_CNTN`. Building it as a tenant screen would mean inventing tenant scoping the legacy never had, on the most sensitive table in the slice, for an internet-facing audience. Raised under harness §3 before any requirement was written.
**Candidates.** A: operator-only · B: both, tenant-scoped from session · C: tenant-only
**PM response.** **A — operator-only.**
**Effect.** FR-AZ-T02, NFR-SEC-AUTHZ-T01. §5.1's `[Tenant]` label is corrected. The MVP's tenant-facing surface narrows from four legacy menus to three (40, 50, 60); the proposal's §4 core scenario 3 is served by the 문자내역 slice alone. AMB-02 as refined by CONFLICT-R01 is not overturned — it governs tenant principals, and this slice now has none.

### EXPORT-T01 — the download button
**Question.** 다운로드 does not export the grid. It posts to `biztalk_admin_30_spreadsheet`, which runs `IDO.KKB_MSG_L001` over `KKO_MSG`/`KKF_MSG` and their archives — a different table set at a different grain, sharing no key with the list. `fn_makeExcel()` gathers its filters from `#IS_LIST`, `#MSGKEY`, `#PHONE`, `#CALLBACK`, `#RSLT`, `#STATUS` and `#MSG_TYPE`, none of which exist in `biztalk_admin_30_view.jsp`; all resolve to `''`, every `CASE WHEN :X = ''` branch opens, and the file is every institution's messages in the window with `decrypt(CALLBACK)`/`decrypt(PHONE)` and no masking.
**Candidates.** A: export the grid's own result set · B: keep both as two labelled buttons · C: drop export from the slice
**PM response.** **A — export the grid's own result set.**
**Effect.** FR-TLKX-001…008. The message-level export is not carried forward as an implicit side effect of this button; whether it returns as its own function is AMB-T01. The acceptance test asserts the exported row set equals the paged list row set, filter for filter — a test the legacy could never have passed.

### PII-T01 — masking in the detail screens
**Question.** `KKB_AT_MSG_L001`, `KKB_FT_MSG_L001`, `KKO_MSG_L002`, `KKF_MSG_L002` and `KKO_MSG_LOG_L001` all apply `decrypt()` with no `masking()`. The 문자내역 slice applied `decrypt()` then `masking()` on the same columns of the same tables. BR-007 requires masking in UI lists.
**Candidates.** A: masked always · B: masked with an audited unmask action · C: plaintext for operators
**PM response.** **A — masked always.**
**Effect.** FR-TLKD-008, FR-TLKM-002, FR-TLKX-008, NFR-SEC-PII-T01, CONST-LEGAL-T01. Consistency with 문자내역 is restored, and the slice has no unmasked path to defend at audit.

---

## 26. Conflicts raised

| ID | Conflict | Status |
|----|----------|--------|
| CONFLICT-T01 | PROJECT-PROPOSAL §5.1 `[Tenant]` vs. a screen with no tenant concept, over a table holding account and card numbers | **Resolved 2026-08-19** — operator-only. §5.1 corrected |
| CONFLICT-T02 | The menu's name vs. the query's scope | **Resolved 2026-08-19** — BizTalk-only (SCOPE-T01). Accepted visible deviation from literal parity |

---

## 27. Open

| ID | Question | Candidates | Working assumption | Owner | Needed by |
|----|----------|-----------|--------------------|-------|-----------|
| AMB-T01 | Should the message-level export dropped by EXPORT-T01 return as its own function? | A: no · B: yes, on screen 32 with its own filters and masking | A — not built unless requested | PM | Skill 3 |
| AMB-T02 | Period cap for the 요청일자 range | A: 31 days (AMB-06 precedent) · B: 92 days · C: keep single-day parity | A — 31 days | PM | Skill 3 |
| AMB-T03 | Which `API_CD` values constitute BizTalk? A source scan yields five literals (`ADV_KKO_AT_SEND`, `_SEND2`, `_SEND_M`, `ADV_KKO_FT_SEND`, `_SEND_M`), but `FT_OPENAPI_INFO` holds codes no source file names — `ADV_COM_GET_STATUS` appears only in the production screenshot | A: the five literals · B: a classification column on `FT_OPENAPI_INFO` the domain owner maintains | B, seeded from A | Domain owner | Skill 3 |
| AMB-T04 | Is `FT_APITR_HSTR.IS_TUNO` genuinely the same identifier as `KKO_MSG.SERIALNUM`, and at what stored width? Three different normalisations exist in the legacy | A: same identifier, one width · B: related but not equal — needs a mapping | A | Domain owner | Skill 3 — **blocks FR-TLKD-009** |
| AMB-T05 | Does 처리중 / 오류 detail exist to be shown? FR-TLK-013 offers a link for those rows; whether the message tables hold rows for a failed API call is unverified | A: show what exists, with an explicit empty state · B: keep them unlinked | A | Domain owner | Skill 3 |
| OI-T01 | Historical exposure from D-T1 — files already extracted contain unmasked cross-institution recipient numbers | — | Raised to 정보보호; retroactive remediation is out of slice | 정보보호 | — |

Carried from Skill 01 and still open: **OI-02** (audit retention term) blocks NFR-OPS-AUDIT-T01.

---

## 28. Corrections to earlier analysis

| Item | Correction |
|------|-----------|
| PROJECT-PROPOSAL §5.1 | Lists legacy 30 as `[Tenant] 거래내역조회`. Both halves are wrong: the screen is not tenant-scoped in any sense, and 거래내역조회 describes the API transaction log, not a customer-facing send history. Corrected by CONFLICT-T01 (audience) and SCOPE-T01 (content). The proposal is not edited retroactively; this log and the specification are the authority |
| BR-011 (Skill 01) | Recorded as "search results on list screens are exportable to Excel … screens 20/30". Part 5 already noted that the export is a *parallel data path* rather than a rendering of the list. Screen 30 shows the end state of that pattern: the export is not merely a second path with its own defects, it queries **different tables** than the list it sits on. BR-011's "results are exportable" is not satisfied by the presence of a download button |
| Part 1 §1.2 (문자내역 data sources) | Recorded `KKO_MSG_LOG` etc. as archive tables read by screen 40. They are also read by screens 30/31/32 and by the screen-30 export, through five further IDOs the 문자내역 analysis did not enumerate. The tables have more readers than any single slice's artifact list suggests — which is why CONST-SEC-T01 is written as a property of *this slice's queries* rather than of the tables |

---

## 29. Method note

Thirty-four defects, five Critical, and a sixth distinct signature.

- 문자내역 defects sat in **gaps between layers**.
- 로그인 defects were **deliberately disabled controls**.
- 이용기관관리 defects were **controls that exist only in the browser**.
- 발신번호 defects were **layers that were each changed correctly and are now wrong together**.
- 이용기관 보고서 defects were **behaviour that differs between environments**.
- 톡전송 내역 defects are **code that was copied from a neighbouring screen and never adapted**.

The copy signature is visible at every layer and is the direct cause of eleven findings:

- `biztalk_admin_30_spreadsheet_view.jsp` declares `@JexDataInfo(id="biztalk_admin_20_spreadsheet")` — screen 20's contract, in screen 30's export (D-T16).
- `IDO.KKB_FT_MSG_L001` is `KKB_AT_MSG_L001` with the table names changed and `'AT' AS MSG_TYPE` left behind, so every 친구톡 row identifies as 알림톡 — and the *next* screen branches on that value, so 친구톡 detail queries the 알림톡 table (D-T7). One unchanged literal, two screens wrong.
- The same copy dropped the `??` dynamic placeholder, so four filters silently do nothing for one of the two channels (D-T8).
- `biztalk_admin_31_view.jsp` is titled `기본 컨텐츠 관리`; its header comment reads `biWztalk` (D-T34).
- `fn_makeExcel()` is screen 40's export function, kept whole on a screen that has none of its input fields (D-T1).

The practical consequence is that **the layer-cross-check that found defects in the five earlier slices is not sufficient here.** Cross-checking view against contract against query finds disagreements; it does not find a query that agrees with its own contract perfectly and describes the wrong screen. The check that works is different: **for every artifact, ask which screen it was written for.** A file's own header comment, its `@JexDataInfo` id, and its literal constants are three independent statements of provenance, and on this screen they disagree with each other.

**The 다운로드 finding deserves separate comment.** D-T1 is not a defect in the export; it is a *different feature* wearing the export's button. Every individual piece is locally reasonable — `KKB_MSG_L001` is a valid query, `fn_makeExcel` is a working function, the contract declares its twelve parameters — and the composition is a single-click, single-request extraction of every institution's recipient phone numbers in plaintext. No layer disagrees with its neighbour. The only way to see it is to ask what the button *means* and compare that to what it *does*, which no structural check performs. This is why FR-TLKX-001's verification is written as an equality between two result sets rather than as a list of properties: the assertion has to be about meaning, and an equality is the only form of that a test can hold.

**The dead-but-live class is new and worth naming.** `biztalk_admin_30_l002` is registered, authenticated, monitored and reachable, and returns unmasked recipient and sender numbers over an arbitrary date range with no institution requirement and no pagination. Its only client is commented out. Commenting out `getDat2()` removed the *use* and left the *capability* — and removed, with it, any user who would have noticed the endpoint behaving badly. The 발신번호 slice's standing check was "for every value that leaves the server and comes back, ask whether it came back in the same form it left." This slice adds: **for every commented-out client, ask whether its service was retired with it.** A service with no caller is not dormant; it is unobserved.

**The silent-success class holds for a sixth consecutive slice**, in a new shape. D-T13: clicking a 상세 link on a KKO transaction the action does not map leaves `idoIn1` null, so the action puts no `REC1`, throws nothing, and calls `setResult` on an empty domain. The popup opens, the grid renders empty, and the operator concludes the transaction has no messages. Nothing failed; nothing was reported; the answer is wrong. Six slices, six mechanisms, one shape — the assumption is now that this pattern is present in any remaining screen until a specific check disproves it.

**One observation on severity.** The five Critical findings are not five independent problems. D-T2 (no institution scoping) makes D-T1 (the export's true blast radius) and D-T5 (message keys addressable without an institution) matter; D-T3 provides a second, quieter route to the same data; D-T4 turns the export endpoint into a header-injection primitive. Taken together they describe a screen where any authenticated principal can obtain, in one request, every customer's recipient phone numbers in plaintext. RISK-T02 and OI-T01 ask 정보보호 to proceed on the assumption that this may already have happened, because unlike the 이용기관 보고서 exposure, this one leaves a file behind.

## 30. Design-time resolutions (Skill 3, 2026-08-19)

Three of the five open items were answered by design rather than by asking, and one new item was raised. The pattern is now consistent across the last three slices: **an open item that can be measured should be a task, not an agenda item.**

### AMB-T03 — answered operationally, not documentarily
**Original question.** Which `API_CD` values constitute BizTalk? A source scan yields five literals; `ADV_COM_GET_STATUS` is in production and in no source file, so the set is data the codebase cannot enumerate.
**What design found.** The question has no answer that a document can hold, because the authoritative list lives in `FT_OPENAPI_INFO` and changes without a release. Asking the domain owner produces a snapshot that is stale the next time an API is registered.
**Resolution.** Config-held allow-list, startup-validated, plus a **standing reconciliation report** counting transactions per unclassified `API_SVC_CD` whose institution has BizTalk service registered ([ADR-TLK-024](../design/adr/ADR-TLK-024-biztalk-api-classification.md), task T1-14).
**Why this is the answer and not a workaround.** SCOPE-T01 introduces a failure mode the legacy did not have. Over-inclusion — the legacy's behaviour — is visible: an operator sees a row that does not belong. Under-inclusion is invisible: a real transaction is absent and nothing says a filter removed it. The reconciliation report exists to make the new failure mode as loud as the old one. **The domain owner is still asked, but the design does not wait**, and when the answer arrives it is checked against the report rather than trusted.
**Successor recorded.** A classification column on `FT_OPENAPI_INFO` is the structurally correct answer and is blocked only by CONST-DATA-T02 (no DDL on a table this project does not own). If it is ever added, `BizTalkApiRegistry` reads it and the config list is deleted; nothing above that interface changes.

### AMB-T04 — converted to a measurement (task T1-01)
**Original question.** Is `FT_APITR_HSTR.IS_TUNO` the same identifier as `KKO_MSG.SERIALNUM`, and at what width? Three legacy code paths normalise it three different ways, one of them lossy.
**Resolution.** Task **T1-01** measures the widths and the join cardinality on production-like data before any mapper is written, and configures a single `TransactionSerial` type from the result ([ADR-TLK-025](../design/adr/ADR-TLK-025-transaction-message-identity.md)). FR-TLKD-009 stays `BLOCKED-AMB-T04` in the matrix and is unblocked by that task.
**Why it is not blocking G2.** The requirement's text — lossless for identifiers of any length — is correct under every answer the question can have. Only the widths are unknown, and they are measurable in half a day. If the relationship turns out not to be plain equality, the type's two renderers become a lookup and nothing above the domain boundary changes.
**A performance property came free.** Removing `lpad` from the predicate makes an index on `SERIALNUM` usable. TC-LOAD-02 measures both forms so the improvement is recorded rather than claimed.

### AMB-T05 — dissolved
**Original question.** Do 처리중 / 오류 transactions have messages to show? FR-TLK-013 offers them a detail link and the legacy did not.
**What design found.** The question was a premise the design did not need. Once `TalkDetailRegistry` decides serviceability and the detail service distinguishes *no messages* from *cannot be queried* ([ADR-TLK-026](../design/adr/ADR-TLK-026-detail-serviceability.md)), both answers render correctly and neither requires knowing in advance which one is true. The screen observes it.
**Resolution.** Closed. Not an open item at G2.

### AMB-T06 — new, raised by design, not blocking
**Question.** What is the relationship between `KKO_MSG` / `KKF_MSG` and `KKO_SMS_MSG` / `KKO_MMS_MSG` / `KKF_SMS_MSG` / `KKF_MMS_MSG`?
**Why it was raised.** Design set out to reuse `MessageDetailMapper` from the 문자내역 slice and discovered the two slices read **disjoint table families** — twelve message tables, no overlap. The corroboration is upstream: `IDO.KKB_APITR_SMTN_C001`, the daily aggregation batch, computes `AT_CNT` (알림톡) from `KKO_MSG` + `KKO_MSG_LOG`, and the column sets differ exactly as that reading implies — `TEMPLATE_CODE`, `PROFILE_KEY`, `BUTTON_JSON` and the `FAILED_*` group exist only in the talk family. The working interpretation is that `KKO_MSG` holds the talk message and `KKO_SMS_MSG`/`KKO_MMS_MSG` hold its SMS/MMS failback, which is what a `FAILED_TYPE` column on the former implies.
**Candidates.** A: failback relationship, joined by `SERIALNUM` · B: independent record sets with no join
**Working assumption.** A.
**Not blocking.** This slice reads only its own four tables. The question matters for a future cross-channel message search, and for anyone who reads the two specifications side by side.
**Owner** Domain owner · **Needed by** whenever a cross-channel search is proposed.

### A qualification to an approved document — not a correction
REQUIREMENTS-SPEC.md §1.2 tabulates `MSG_TYPE=AT` against `KKO_SMS_MSG` / `KKO_MMS_MSG`. **That is accurate for what screen 40 reads**, and no requirement in that slice changes. It is not, however, the complete set of 알림톡 message records: `KKO_MSG` also holds them and screen 40 never touches it. Recorded here rather than edited there, because the statement was true about its own scope and only reads as incomplete once this slice exists.

### One programme-level change: `ReportScope` becomes `common.tenant.PrincipalScope`
Not an open item, but a design decision that reaches into an already-approved slice and therefore belongs in this log. `ReportScope` already encodes exactly the operator/tenant distinction CONFLICT-T01 needs, including the two properties that carry the security: a tenant's blank value does not mean "all", and a tenant's supplied value is **ignored rather than validated-then-rejected**, so the error message cannot become an institution-enumeration oracle. Copying it would put two implementations of one authorization rule in the codebase.

Task T1-04 moves it and **re-runs the 보고서 slice's authorization tests unchanged**. The exit condition is that suite going green; if it does not, the move is reverted to a duplicated class with a comment. An authorization rule on shipped code is not something a refactor gets to adjust.

### Method note — what changed about how open items are handled
Six slices in, three of the last five open items were closed by design without asking anyone, and the reason each time was the same: **the question was phrased as something a person knows, when it was actually something the system can be asked.** AMB-R04 (two databases or one) became a probe. AMB-T04 (identifier widths) became a probe. AMB-T03 (which APIs) became a standing report, because unlike the other two its answer changes over time and a one-off measurement would have been stale immediately.

The residual — AMB-T06 — is genuinely a person's knowledge, and it is the one that stays open. That is the distinction worth carrying to the two remaining slices: **before escalating an open item, ask whether the database already knows.**

---

# Part 7 — 카카오 알림톡 템플릿 / 발송 (AlimTalk Compose · Send · Template Validation), 2026-08-18

**Slice**: legacy screen 61, plus screen 50's send path (pulled in by AMB-A00)
**Spec**: [REQUIREMENTS-SPEC-ALIMTALK.md](REQUIREMENTS-SPEC-ALIMTALK.md)
**Defects**: D-A1…D-A35 (23 in screen 61, 12 in screen 50)

## 31. Resolved

### AMB-A00 — migration scope for a screen registered as a "sample"
**Question.** `WSVC.biztalk_admin_61` is named `BIZTALK(템플릿 샘플)`, declares `actUseYn=N`, and has no action class in any layer. The screen composes a JSON payload into a read-only textarea for the operator to copy by hand. Is it migrated as a developer utility, upgraded into a real send screen, promoted to full template management, or dropped?
**Candidates.** A: utility parity · B: utility + real send · C: full template management · D: drop
**PM response.** **B** — keep the composer, wire 발송 to the real send API with server-side validation, `tran_id` dedupe and audit logging.
**Effect.** FR-ATS-001…014, FR-AZ-A01…A05, FR-ATH-001…003. Also **widened the slice**: see CONFLICT-A01.

### AMB-A00b — the template validator reports valid content as invalid
**Question.** `validateTemplateStrict()` advances to the *first occurrence* of the next literal character after each `#{...}`. When the substituted value contains that character the scan stops inside the value and the following comparison fails — template `#{name}님 안녕` with content `김님철수님 안녕` is reported as a mismatch (D-A6). Port as-is for parity, correct it, or replace with Kakao's published rules?
**Candidates.** A: correct it · B: byte-for-byte parity · C: Kakao official rules
**PM response.** **A — correct it.** The behavioural break is accepted.
**Effect.** FR-ATV-004…006. **Recorded for QA:** inputs the legacy rejected now pass. TC-A004-02 and TC-A004-03 assert the fix; they are not parity regressions and must not be reverted as such.

### AMB-A01 — duplicate-send protection with an operator-supplied tran_id
**Question.** AMB-A02b declined server-generated `tran_id`, but AMB-A00 makes the screen able to send. How is a double-send prevented?
**Candidates.** A: server dedupe on `(is_cd, tran_id)` · B: UI guard only · C: none
**PM response.** **A — server dedupe**, returning the original outcome rather than sending again.
**Effect.** FR-ATS-009. Window open as AMB-A08. Note that `KKB_ADMIN_SEND_HIS` is keyed `(IS_CD, SERIALNUM)` where `SERIALNUM` *is* the `tran_id` (CONST-DATA-A04), so uniqueness was already a data-integrity requirement before idempotency was raised — the legacy simply violated it (D-A25).

### AMB-A02 — which limit set governs
**Question.** No length or count limit exists anywhere in screen 61. Should Kakao's published business limits be adopted?
**Candidates.** A: Kakao spec values tagged `[ASSUMED-KAKAO-SPEC]` · B: PM supplies values · C: advisory warnings · D: none
**PM response.** **A — Kakao spec values.**
**Effect.** FR-ATC-005, CONST-DATA-A02. **Partially superseded by analysis** — the question was asked on the belief that no limits existed in the code. The IMO contract declares its own, and they disagree with Kakao's in both directions. See CONFLICT-A02.

### AMB-A03 — batch size cap
**Question.** `msg_data` has no maximum; screen 50 chunks at 1000 per vendor call as an invisible implementation detail.
**Candidates.** A: configurable cap, value supplied later · B: no cap
**PM response.** **A.**
**Effect.** FR-ATS-014, NFR-SCALE-A01. Value open as AMB-A03 in §33.

### AMB-A05a — 이미지형 is selectable but unfillable
**Question.** `msg_type=AI` auto-syncs with `emphasis_type=image`, yet no image field exists anywhere in the screen, so the option can only produce a payload with no image (D-A8).
**Candidates.** A: implement properly · B: remove from the UI · C: keep, reject server-side
**PM response.** **A — implement properly.**
**Effect.** FR-ATC-008. **Cannot be completed from these artifacts** — neither IMO contract declares any image field. Field set open as AMB-A05, which is the only open item blocking a requirement.

### AMB-A02b — PII handling for recipient numbers
**Question.** Recipient phone numbers are handled in plaintext in the browser and copied to the clipboard. Skill §7 makes NFR-SEC-PII / NFR-OPS-AUDIT mandatory once PII is in scope.
**Candidates (multi-select).** A: mask on display + audit the send · B: also audit clipboard/export egress · C: server-generated `tran_id` with idempotency · D: none
**PM response.** **A only.** B and C explicitly declined.
**Effect.** NFR-SEC-PII-A01, FR-AZ-A04. The two declines are recorded as accepted risks, not deferrals: RESIDUAL-A01 (unaudited clipboard egress) and RESIDUAL-A02 (operator-supplied `tran_id`).

## 32. Conflicts raised

### CONFLICT-A01 — a second writer on one vendor contract
**Origin.** AMB-A00 was answered on the stated premise that screen 61 sends nothing. That premise was correct about screen 61 and incomplete about the system: `biztalk_admin_50_s001_act.jsp` **already sends** against the same `IMO.ADV_KKO_AT_SEND`, carrying twelve defects of its own (D-A24…D-A35) including a hardcoded vendor credential and a `tran_id` that collides by construction.
**The conflict.** Wiring screen 61 to send creates two independent writers on one vendor contract. FR-ATS-008/009 would then hold only for traffic through the new path, while screen 50 kept minting colliding `tran_id`s into the same `KKB_ADMIN_SEND_HIS` primary key.
**Recommendation.** The new path **replaces** screen 50's, which is retired at cutover.
**Status.** **Needs explicit PM sign-off at G1** — it widens the slice from one screen to two. Structurally identical to RISK-S05 in the 발신번호 slice, where `AOA_ADMIN` stayed a second writer after ship; the difference is that this second writer is inside our own scope and can still be closed.

### CONFLICT-A02 — Kakao's published limits vs the contract's declared lengths
**Origin.** AMB-A02 assumed Kakao's limits were missing from the code. They are not missing; a different set is declared, and the two disagree **in both directions**: `msg` 4000 vs Kakao's ~1000 characters; `template_title` 200 vs ~50; `button.name` 28 vs ~14; `failback_data.subject` 50.
**Why it is a real question.** Per CONST-BIZ-A02 the portal is not a direct Kakao client — it reaches Kakao through `COOCON_ALERT` at `/advising/kakao/at_send`, and that vendor's own validation appears in none of the analyzed artifacts. So "adopt Kakao's limits" is not a lookup; it is a choice about which of three validators to mirror.
**Resolution adopted for this draft.** Enforce the **lower** of contract and Kakao per field; tag every Kakao-derived bound `[ASSUMED-KAKAO-SPEC]`; treat the contract lengths as inviolable (CONST-DATA-A02).
**Status.** **G1 must confirm**, since it partially supersedes AMB-A02 as answered.

### RESIDUAL-A01 — accepted: clipboard egress is not audited
PM declined option B of AMB-A02b. Copy remains a path by which an operator can extract recipient numbers with no record. Compensating controls: NFR-SEC-PII-A01 (masking beyond the entry field), FR-AZ-A01/A03 (authorized operators only). **Narrower than under the legacy design**, where copy-paste was the *only* way to send anything at all.

### RESIDUAL-A02 — accepted: tran_id remains operator-supplied
PM declined option C of AMB-A02b, resolving idempotency by dedupe instead. Because CONST-DATA-A04 makes `tran_id` a primary-key component, an operator can still trigger a *rejection* by reusing a value — dedupe converts a data-integrity failure into a usability one, which is the right trade. But FR-ATS-008's uniqueness requirement now depends on a client-side scheme the operator can override. Revisit if operators report spurious duplicate rejections.

## 33. Open

| ID | Item | Candidates | Working assumption | Owner | Needed by |
|----|------|-----------|--------------------|-------|-----------|
| AMB-A03 | The batch-size cap **value** (FR-ATS-014) | A: 1000, the existing chunk boundary · B: higher, with async despatch · C: an ops-supplied figure | A | Domain owner / Ops | Skill 3 |
| AMB-A04 | Reservation for batch sends. `ADV_KKO_AT_SEND_M` declares `reqdate` per `msg_data` item, so the contract already supports it; PM selected neither "add it" nor "keep single-only" | A: enable for batch · B: single-only | **A** — the single/batch asymmetry is a screen omission (D-A14), not a design decision | PM | Skill 3 |
| AMB-A05 | The image-type field set (FR-ATC-008), and equally `kko_header` / `highlight` / `items` / `summary` (D-A2). Neither IMO contract declares any of them | A: obtain the vendor's current spec and extend the contract · B: drop 이미지형 and the item-list form until the spec is available | A, with **B as the fallback** — a scope reduction G1 should be aware of | Architect + vendor | **Skill 3 — blocks FR-ATC-003 and FR-ATC-008** |
| AMB-A06 | The canonical `receiver_number` representation (FR-ATC-006), and how the bound request is marshalled into `ADV_KKO_AT_SEND2`'s single `RSMS` field | A: JSON array, matching screen 50's ≤1000 branch · B: delimited string, matching the declared length of 20000 | A, with the marshalling confirmed against a live `RSMS` capture before Sprint 1 closes | Architect | Skill 3 |
| AMB-A07 | How templates enter `KKB_MSG_TMPL`. No screen in this repository writes it, yet FR-ATT-001/004 make it authoritative for sending | A: an external or vendor process owns it, portal reads only · B: template management is a later slice | A | Domain owner | Skill 3 |
| AMB-A08 | The `tran_id` dedupe retention window (FR-ATS-009) | A: 24 h · B: match `KKB_ADMIN_SEND_HIS` retention | B — one window, so dedupe cannot outlive its evidence | PM | Skill 3 |
| AMB-A09 | Whether landline recipients are in scope. `isPhoneNumber` is mobile-only and discards the rest silently (D-A28); AlimTalk is mobile-only but the SMS/LMS fallback is not | A: mobile-only, rejected explicitly rather than silently · B: accept landlines for fallback-only sends | A | Domain owner | Skill 3 |

Carried and still open: **OI-02** (audit retention term) blocks NFR-OPS-AUDIT-A02 and CONST-LEGAL-02.

## 34. Corrections to earlier analysis

- **"Screen 61 sends nothing" was true of the screen and misleading about the feature.** It was the premise on which AMB-A00 was put to PM, and it held — `actUseYn=N`, no action class, no AJAX call. But the *feature* has a send path, in screen 50, and the question would have been better framed as "which of the two send paths survives". The answer PM gave is unaffected; the scope implication was not surfaced until CONFLICT-A01. **The framing error was asking about a screen when the unit of decision was a capability.**
- **"No limits exist in the legacy" was wrong.** Stated while raising AMB-A02, on the basis of screen 61 and screen 50 alone. The limits were in the interface contract the whole time — twelve of them, per field. CONFLICT-A02 exists because the ruling was sought before the contract had been read.
- **The reservation asymmetry was misdiagnosed as a possible design decision.** It was put to PM as a choice between enabling batch reservation and deliberately keeping it single-only. The contract settles it: `ADV_KKO_AT_SEND_M` declares `reqdate` on every `msg_data` item, so batch reservation was always available and screen 61 simply never collected it (D-A14). PM selected neither option, and the working assumption in AMB-A04 now rests on the contract rather than on a preference.

## 35. Method note

Screen 61 has three client-side files and no server side, so a conventional four-layer read (view → client → action → query) terminates after two layers with almost nothing to say. Everything of consequence in this slice came from reading **what consumes the screen's output**:

- `IMO.ADV_KKO_AT_SEND` gave the authoritative field names, exposing `failback` vs **`failback_data`** (D-A1) and five fields the contract cannot accept (D-A2).
- `IMO.ADV_KKO_AT_SEND_M` gave the mandatory **`order`** the composer never emits (D-A3) — and, incidentally, the `reqdate` that settled AMB-A04.
- Both gave the twelve field lengths that make FR-ATC-005 specifiable at all (D-A7) and that produced CONFLICT-A02.
- `IDO.KKB_MSG_TMPL_L002` revealed that **`TEMPLATE_MSG` — the template body — is already stored**, keyed by exactly the two values the composer collects. This moved template validation from a manual copy-and-paste tab to an automatic server-side check at send time (FR-ATV-001), which is the one place it can prevent a vendor rejection.
- `biztalk_admin_50_s001_act.jsp` supplied twelve further defects and CONFLICT-A01.

> **The generalisation this slice adds.** The 발신번호 slice concluded that *when a table is written by this system and read by another, the other system's queries are part of the specification.* The sharper form here: **when a screen's only output is a payload for another system, that system's contract is the screen's specification — and a composer nothing validates has no correctness criterion at all.**
>
> That is not an abstraction. `ADV_KKO_AT_SEND_M` is **called by no code in this repository**, and screen 61's output is **consumed by no code at all**. Neither the missing `order` nor the misnamed `failback_data` had any path by which it could ever fail visibly, which is precisely why both survived into a file stamped `20250428`. The three Critical contract defects in this slice were not hard to find; they were impossible to find *from inside the screen*, and nothing outside the screen was looking.
>
> **Practical consequence for the remaining slices:** for any screen whose output crosses a system boundary, read the boundary contract *before* putting scope or limit questions to the PM. Both corrections in §34 trace to having asked first and read second.

---

# Part 3b — 이용기관관리 screen 01, gap pass (Skill 02, 2026-08-20)

**Slice**: legacy screen **01** (이용기관 등록/수정 popup) — the write half of Part 3
**Spec**: [REQUIREMENTS-SPEC-INSTITUTION.md](REQUIREMENTS-SPEC-INSTITUTION.md) v1.2
**Trigger**: implementation of the 기관코드 → popup path. Specifying the popup field by field surfaced one defect, one conflict and three decisions the existing spec did not carry.

## 36. Resolved

### AMB-I11 — a Must requirement with nothing in this system to satisfy it
**Question.** FR-INSTC-008 requires the institution cache to be refreshed after a save. The legacy did that with `FINInstitution.getInstance().getManager().reload()` — an **in-process Jex cache inside IRIS_ADMIN**. The portal is a separate process: it cannot reach that cache, and it keeps no server-side institution cache of its own. How is the requirement closed?
**Candidates.** A: rewrite as portal-side cache invalidation, legacy cache tracked as an out-of-boundary gap · B: build a notifier calling a new IRIS_ADMIN reload endpoint · C: downgrade to Should and defer
**PM response.** **A.**
**Effect.** FR-INSTC-008 rewritten. The portal invalidates the view it owns; the legacy runtime's cache stays with RISK-I02 / ADR-INST-016. Option B was declined for the reason ADR-INST-016 already gives: this project does not add call paths into a system it does not own, and the endpoint it would call does not exist.
**Note.** D-I17 is still closed by this, but by a different mechanism than the spec assumed. The legacy defect was a *swallowed* failure (`catch(Throwable){printStackTrace();}`); there is now nothing to swallow, because the only failure on the path is the save itself and that reaches the operator.

### AMB-I12 — validation vs. data that predates the validation
**Question.** FR-INSTC-009 validates 사업자등록번호 as 10 digits. Stored rows may violate it. If an operator edits an unrelated field — a 사용여부 change, say — on such a row, does the save fail?
**Candidates.** A: enforce on every save · B: enforce only when the field changed · C: warn, allow, audit
**PM response.** **A — enforce on every save.**
**Effect.** FR-INSTC-016. The operator has the offending value on screen, so bad data is corrected by attrition rather than accumulating. **Recorded for QA:** this is a deliberate behavioural break. An edit that the legacy would have accepted can now be refused, and the refusal is about a field the operator did not touch. TC-I002-23 asserts it and is not a parity regression.

### AMB-I13 — when 인증키 재발급 takes effect
**Question.** The legacy filled `#ATK` in the browser and persisted it on 저장, so 닫기 discarded it. UC-INST-002 §3.2 requires rotation to be explicit and separately audited but never states the commit point.
**Candidates.** A: commit on its own confirmation, separate from 저장 · B: stage in the form and commit with 저장
**PM response.** **A — commit on confirmation, with its own audit record.**
**Effect.** FR-INSTC-011. Two consequences worth stating: the audit record and the row can no longer disagree (with B, an audited "rotation" abandoned at 닫기 would name a key that was never stored), and the 저장 payload now carries **no credential material at all** — which is what lets the update statement omit `ATK` entirely and makes a masked-value-written-back accident unrepresentable rather than guarded.

## 37. New finding — D-I20

**A second, unrecorded instance of D-I3.** The Part 3 analysis recorded credential disclosure through 중복검사 (`biztalk_admin_01_l001`, D-I3). The **detail service is the same hole**: `WSVC.biztalk_admin_01_l002` declares `ATK` in its `<out>` rule, `biztalk_admin_01.js:loadData()` writes it into `$("#ATK")`, and the service carries `<login>Y</login>` and nothing else (D-I2). Any authenticated caller can post any 기관코드 to `biztalk_admin_01_l002.act` and read that institution's plaintext credential; for an operator using the screen normally, the key sits in the popup's DOM.

Severity High, disposition FIX → FR-INSTC-010 (the form shows the masked value; no form-populating endpoint returns the plaintext).

> **Why the first pass missed it.** D-I3 and D-I5 were both found by asking *which responses contain `ATK`* — of the **list** and **duplicate-check** services. The detail service was read for its field list, to establish what the edit form loads, and its `ATK` was noted as "what populates 인증키" rather than as an exposure. The disclosure is identical; only the framing differed. **Enumerate exposure paths per credential, not per screen** — `grep` the `<out>` rules of every service in the slice for the credential name, then ask who can call each.

## 38. Conflict raised

### CONFLICT-I03 — the application clock is right, and wrong for these two columns
`RGDT` and `LAST_AMDT` are `YYYYMMDDHH24MISS` **wall-clock strings**, written until now by the legacy through the database's `now()`. The portal's `Clock` bean is deliberately `Clock.systemUTC()` — chosen in the 로그인 slice so audit ordering survives a multi-instance deployment, and correct for that. Formatting these two columns from it would place every portal-written timestamp **nine hours behind** every legacy-written one **in the same column**, and ADR-INST-016 keeps the legacy writing that column. Sorting and comparison would silently interleave two epochs.

**Resolved at this pass → FR-INSTC-013.** These two columns are written by `to_char(now(),'YYYYMMDDHH24MISS')` in the mapper — the legacy expression with `HH` restored (D-I9). The application clock stays UTC and stays out of them. Escalated to Skill 3 for an ADR because it is a boundary decision rather than a coding preference.

> Neither side of this conflict is a defect. It is the ordinary cost of two writers on one column, and it was invisible while the slice was read-only.

## 39. Closed

**AMB-I06** — canonical 기관코드 format, and 사업자등록번호 length. Closed by FR-INSTC-014 with the rule made explicit: where the legacy's three sources disagree (form `maxlength`, contract `length`, DB column), **the narrowest governs**. 기관코드 is 6 characters, `K0` + 4 alphanumerics — the contract's `length="16"` is a transport maximum, not the domain format. Enforcement point per AMB-I12.

**AMB-I09** — half-closed. FR-INSTC-010 settles what the *edit form* shows (masked). Who may reveal a full key, and under what audit, remains open.

## 40. Method note

The three questions in §36 have one shape in common: **each is a requirement whose subject does not exist in the new architecture.** FR-INSTC-008 names a cache that lives in another process. FR-INSTC-007 names a 최종수정자 name the portal's session has no field for (→ FR-INSTC-012). FR-INSTC-006 names a timestamp whose clock the programme had already chosen differently, for good reasons, elsewhere (→ CONFLICT-I03).

None was findable by reading the legacy, and none was findable by reading the spec. All three appeared at the moment the requirement had to be **bound to a specific line of code** — which is late, but is also why the earlier slices' `// req:` convention earns its cost: the binding is where the mismatch becomes visible.

> **For the remaining write-path slices:** before implementing a requirement, name the component in *this* system that satisfies it. If the answer is a legacy component, the requirement needs re-writing, not implementing.

---

# Part 4b — 발신번호 등록·삭제, write-path gap pass (Skill 02, 2026-08-20)

> Spec: [REQUIREMENTS-SPEC-SENDERNO.md](REQUIREMENTS-SPEC-SENDERNO.md) v1.1 · Scope: the two controls Sprint S1 left disabled — 등록 (legacy popup `biztalk_admin_12`) and 삭제 (legacy popup `biztalk_admin_13`)
> Trigger: PM request, "when click 등록 is biztalk_admin_12, and select row for delete 삭제", followed by the directive **"please follow old logic"**

Sprint S1 shipped the list and deliberately rendered 등록 and 삭제 as **disabled** buttons rather than omitting them ([SenderNumberPage.tsx](../../src/main/frontend/src/features/biztalk/SenderNumberPage.tsx#L309-L314)) — the reasoning recorded there was that a button which does nothing when pressed is D-S8 with the sides reversed. This pass wires them, and it is a **gap pass rather than a new specification**: FR-SNDC-001…010 and FR-SNDD-001…008 already existed and are unchanged. What was missing was everything between the button and the service.

## 41. PM directive — the legacy logic is the baseline

**Directive.** "Please follow old logic."

Read literally against a slice whose reason for existing is twenty-one recorded defects, that directive would reinstate a delete that deletes nothing. It is read instead as what it plainly governs — **flow, field set and business rule** — and the interpretation is written down here so nobody has to re-derive it:

| The directive governs | Ported unchanged from |
|-----------------------|----------------------|
| 등록 is reached from the list, scoped to the 이용기관 already chosen there; the form's 이용기관코드/기관명 are read-only | `biztalk_admin_10.js` `btn_register` → popup 12 with the opener's `IS_CD` |
| Field set and order: 이용기관코드, 이용기관명, 발신번호, 설명, 사유; actions [등록] [닫기] | `biztalk_admin_12_view.jsp` |
| The three rules stated to the operator on the registration screen, verbatim | `biztalk_admin_12_view.jsp` `infoList01` |
| 삭제 acts on the rows selected in the grid; the confirmation lists those numbers and takes a 사유; actions [삭제] [닫기] | `biztalk_admin_10.js` `btn_delete` → popup 13, `_gu.getCheckData()` |
| Length and prefix branches: 8–11 digits, 12 for 030/050, exactly 8 for 15xx/16xx | `biztalk_admin_12_c001_act.jsp` `isValidDpNo()` |
| 등록자/수정자 taken from the session, not the form | `SessionManager.getFlnm()/getEml()` |
| On success the form closes and the list re-queries | `opener.getDat()` |

| The directive does **not** reopen | Because |
|-----------------------------------|---------|
| D-S1, D-S5, D-S6, D-S7 — delete matching, per-number history, transaction, result-variable check | Already ruled FIX; AMB-S02 and §6.4 of the spec |
| D-S9 global uniqueness, D-S11 server-side validation, D-S12 barred numbers, D-S13 digits-only, D-S15 lengths | Already ruled FIX; AMB-S03 |
| D-S18 — no 인증키 in the registration form's context load | Already ruled FIX; inherits FR-ATK-004 |
| Authorization and tenant scope (FR-AZ-D01…D05) | Delivered in S1; the legacy had none |
| `window.open` popup → modal dialog | A transport, not a logic. See [ADR-SND-020](../design/adr/ADR-SND-020-write-dialog-presentation.md) |

**One place where the directive and a ruling given the same day meet.** AMB-S10 below made 사유 mandatory on registration; the legacy screen had the field and enforced nothing (its client validation was vacuous — D-S11 — so an empty 사유 passed). The explicit answer to the explicit question is taken as governing, and the divergence is recorded here rather than smoothed over. **It is a deliberate behavioural break from the legacy, and it is one edit to reverse** (FR-SNDC-011).

## 42. Resolved this pass

### AMB-S10 — is 사유 mandatory on registration?
**Question.** FR-SNDD-006 makes 사유 mandatory on deletion. FR-SNDC-001 lists 사유 as a registration field and FR-SNDC-007 caps it at 100 characters, but nothing in the spec says whether it may be empty. The legacy accepted empty by accident, not by decision.
**Candidates.** A: mandatory, symmetric with deletion · B: optional, matching observed legacy behaviour
**PM response.** **A — mandatory.**
**Effect.** FR-SNDC-011. Every `KKB_DPNO_HIS` row in the slice now carries a *why*, for `ACN='C'` as well as `ACN='D'`. This matters more here than symmetry usually would: under RESIDUAL-S01 registration carries no proof of ownership, so the 사유 is the only record of the operator's basis for claiming a number. **Recorded for QA as a deliberate parity break** — TC-S002-23 asserts a rejection the legacy would have accepted.

### AMB-S06 — the authoritative list of barred special and emergency numbers
**Question.** The registration screen names 112, 114 and 1335 as examples (D-S12). No complete list exists in any legacy layer. `SenderNumberValidator.BARRED_NUMBERS` currently holds 14 values as a stated working assumption.
**Candidates.** A: adopt the current 14 as configuration, replaceable without a release · B: block registration until an authoritative KISA/KAIT list is obtained
**PM response.** **A.**
**Effect.** CONST-BIZ-D03 and [ADR-SND-021](../design/adr/ADR-SND-021-barred-number-list.md). The set leaves compiled code and becomes a loaded resource. Two properties are load-bearing rather than incidental: an empty or unreadable list **fails startup** instead of silently disabling the rule — that failure mode is D-S12 exactly — and the four numbers the PM ruling names (112, 114, 119, 1335) are additionally fixed in the test suite, so no future configuration edit can quietly drop them.
**RISK-S11 downgraded, not closed.** The list is now changeable in minutes; what remains open is whether it is *complete*, which no amount of design settles.

### AMB-S08 — cascade when an 이용기관 is logically deleted
**Question.** The institution slice logically deletes an institution (`IS_STTS='D'`, ADR-INST-014). Legacy `KKB_DPNO_LDGR_D002` hard-deletes every number for an `IS_CD`, which contradicts FR-SNDD-001.
**Candidates.** A: institution state bars sending; the numbers are left in the ledger · B: cascade the deletion into `KKB_DPNO_ARCV`
**PM response.** **A.**
**Effect.** CONST-BIZ-D04. It is the cheaper answer *and* the better-founded one: enforcement already exists one level up. `KAKAOTALK` validates the institution before it validates the caller ID, so a `'D'` institution is refused by ADR-INST-014's mechanism without the number rows moving at all. Cascading would also couple this slice to an in-flight sprint (I2a) for no gain in safety.
**Two consequences recorded rather than assumed away.**
1. **Orphan-by-design.** Numbers survive their institution in the ledger. Harmless while the 기관코드 is dead — but a 기관코드 later **re-issued to a different institution** would inherit them, since the ledger keys on `IS_CD` and nothing else. Whether 기관코드 is ever reused is an institution-slice property, not ours, so it is raised as **RISK-S14** rather than mitigated here.
2. The separate `kakaobiztalk_admin` repository still carries a handwritten hard-cascade `KKB_DPNO_LDGR_D002`. Ruling A does not make that safe; it makes it *divergent*. Stays on RISK-S05 as a programme coordination item.

## 43. Gaps found by this pass

Four, none of which is a legacy defect — all four are things the legacy never had to decide because a popup window decided them for it.

| Gap | Why it exists | Requirement |
|-----|---------------|-------------|
| **Selection can outlive the page it was made on.** S1 clears the selection when the 이용기관 changes but not when the page does, and the grid renders one page. An operator could select on page 1, move to page 3, and press 삭제 | The legacy grid held the entire result set in the browser (D-S14), so "selected" and "visible" never diverged. Server-side paging separated them | **FR-SNDD-009** — deleted set ≡ enumerated set, selection retained across pages, count always visible |
| **Neither button had an enablement rule.** 등록 needs an institution; 삭제 needs a selection | The legacy popups opened regardless and failed later, or in 삭제's case sent an empty list | **FR-SNDD-010** |
| **Nothing specified the refresh after a write.** | `opener.getDat()` — an implicit property of window ownership that does not survive into an SPA | **FR-SND-012** |
| **Nothing specified what a rejected form does.** The legacy showed `등록중 오류 발생.` and left the operator to guess | NFR-USE-D02 stated the principle but no requirement bound it to the register flow | **FR-SNDC-014** |

The first is the one worth naming as a class. It is the **same shape as D-S1** — an operation acting on something other than what the operator saw — arriving by a different route: not a masked value this time, but a stale one. The 발신번호 slice's standing check was *"for every value that leaves the server and comes back, ask whether it came back in the same form it left."* This pass adds: **for every set an operation acts on, ask whether the operator could still see all of it when they pressed the button.**

## 44. Still open

| ID | Item | Disposition |
|----|------|-------------|
| AMB-S07 | Maximum 발신번호 per institution | **Open, non-blocking.** Working assumption A (no cap) stands and S2a implements no cap. Adding one later is a validation rule against a count query — no schema, no data migration — so deferring costs nothing |
| AMB-S09 | Whether `RGSR_ID`/`UDT_ID` hold an internal user ID rather than an email | **Resolved at Skill 3, option B** — see §45 |
| OI-02 | Audit retention term | Carried, unchanged. Blocks NFR-OPS-AUDIT-D02 |

## 45. Design-time resolution (Skill 3, 2026-08-20)

### AMB-S09 — operator identity in `RGSR_ID` / `UDT_ID`
**Question.** `RGSR_NM`/`UDT_NM` are encrypted at rest while `RGSR_ID`/`UDT_ID` hold the operator's email in plaintext (D-S16). NFR-SEC-PII-D01 requires the two to be treated consistently.
**Candidates.** A: an internal user ID, with the email held only in the user master · B: keep the email and encrypt it at rest
**Architect resolution.** **B.**
**Why.** The requirement asks for *consistency*, and B delivers it in this slice with no dependency outside it. A is the better end state but is not a sender-number change: it needs a user master with stable internal IDs, a backfill of every historical row in this table **and** in `KKB_DPNO_HIS`, and — decisively — `AOA_ADMIN` keeps writing emails into the same columns after we ship (RISK-S05). A would therefore produce a column holding two incompatible identifier kinds, which is worse than the inconsistency it set out to fix.
**Effect.** Register and delete write `ENCRYPT(<session email>)` through the same mechanism as `RGSR_NM` (CONST-DATA-D02, ADR-005). D-S16 closes on the consistency test. **Option A is recorded as the programme-level target, owned by the login slice, not deferred silently.**
**One thing this does not fix.** Rows written by `AOA_ADMIN` after cutover still carry plaintext. Reads must tolerate both forms, which is a mapper concern flagged for S2a-05 and not a requirement change.

## 46. Method note

This pass produced no new defect and one new class of finding, and that ratio is itself the observation. The slice had been read four times — Skill 2, Skill 3, Sprint S1, and a security audit — and the four gaps in §43 survived all four **because none of them is visible in the legacy source.** They are gaps in the *new* architecture: properties a popup window supplied for free that a single-document SPA has to state.

The institution slice's note said to name the component in *this* system that satisfies a requirement. This one is the converse: **name the property the legacy got for free from its runtime, and ask who supplies it now.** `opener.getDat()`, "selected implies visible", and "the popup cannot open without an `IS_CD`" were all runtime guarantees, not code. Two of them became requirements here; the third became a defect class.
