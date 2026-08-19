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
2. **The ledger cannot be trusted to reflect operator intent.** Numbers believed deleted are probably live and still valid for sending. §6.4 of the spec raises the reconciliation — this is the second slice in a row to produce one (D-I1 was the first), and both have the same shape: an operator withdrew something, the system said yes, and nothing happened.
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
