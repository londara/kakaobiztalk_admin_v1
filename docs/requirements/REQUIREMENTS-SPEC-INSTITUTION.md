# Requirements Specification — 이용기관관리 (Client Institution Management)

> **Version**: 1.0
> **Date**: 2026-08-14
> **Scope**: legacy screen **00 (이용기관관리)** and its registration/edit popup **01 (이용기관 등록/수정)** — one feature slice of the biztalk module
> **Predecessors**: [PROJECT-PROPOSAL.md](../planning/PROJECT-PROPOSAL.md), [BUSINESS-REQUIREMENTS.md](../planning/BUSINESS-REQUIREMENTS.md)
> **Sibling specs**: [REQUIREMENTS-SPEC.md](REQUIREMENTS-SPEC.md) (문자내역), [REQUIREMENTS-SPEC-LOGIN.md](REQUIREMENTS-SPEC-LOGIN.md) (로그인)
> **Traceability matrix**: [requirements-matrix.csv](requirements-matrix.csv)
> **Question log**: [questions-log.md](questions-log.md) — Part 3
> **Status**: **DRAFT — awaiting G1**

---

## 1. Overview

This document specifies the 이용기관관리 feature of the new IRIS BizTalk Portal, derived by static analysis of 19 legacy artifacts. No runnable legacy environment exists (proposal RISK-001), so every requirement below was recovered by reading source across four layers and cross-checking them against each other.

Screen 00 is the **tenant registry for the entire platform**. Every other screen in the module — 문자내역, 발신번호, 수수료, 템플릿 — is keyed on the `IS_CD` this screen issues, and the `ATK` it generates is the credential client companies present when calling the send API. Its blast radius is larger than its 127-line JSP suggests.

### 1.1 Legacy artifacts analyzed

| Layer | File |
|-------|------|
| View | `IRIS_ADMIN/web/view/jex/iris_admin/biztalk/biztalk_admin_00_view.jsp` (127 L) |
| View (popup) | `…/biztalk_admin_01_view.jsp` (131 L) |
| Client logic | `IRIS_ADMIN_STATIC/web/opportal/js/jex/iris_admin/biztalk/biztalk_admin_00.js` (283 L) |
| Client logic (popup) | `…/biztalk_admin_01.js` (200 L) |
| Action | `biztalk_admin_00_l001_act.jsp` (기관조회), `_l002` (매니저조회), `_l003` (매니저여부), `_u001` (기관사용중지), `_d001` (이용기관삭제) |
| Action (popup) | `biztalk_admin_01_c001_act.jsp` (등록/수정), `_l001` (중복검사), `_l002` (상세조회) |
| Service contract | `WSVC.biztalk_admin_00.xml`, `…_00_l001`, `…_00_l002`, `…_00_l003`, `…_00_u001`, `…_00_d001`, `…_01_c001`, `…_01_l001`, `…_01_l002` |
| Query | `IDO.KKB_FT_FTIS_INFO_L001` (list), `_L002` (detail), `_C001` (upsert), `_U001` (status), `_D001` (delete) |
| Query (related) | `IDO.KKB_MNGR_LDGR_L001/L002`, `IDO.KKB_DPNO_LDGR_D002`, `IDO.KKB_DPNO_HIS_C001` |

### 1.2 Data model

Target DB: `BIZTALK_DB` (PostgreSQL).

| Table | Role | Key |
|-------|------|-----|
| `FT_FTIS_INFO` | Institution master — **authoritative** (§AMB-I01) | `FINTECH_ISCD` |
| `FT_INST_INFO` | Written only by the 중지 path; read by nothing in this module — see D-I1 | `FINTECH_ISCD` |
| `KKB_DPNO_LDGR` | Sender numbers owned by an institution, `DP_NO` encrypted | `IS_CD` |
| `KKB_DPNO_HIS` | Sender-number change history, `DP_NO` and `RGSR_NM` encrypted | `IS_CD` |
| `KKB_MNGR_LDGR` | Platform manager roster — read-only, no write path exists | `MNGR_MAIL` |

Column naming differs between table and contract: `FINTECH_ISCD`→`IS_CD`, `ISNM`→`IS_NM`, `ISENGNM`→`IS_ENGNM`, `IS_STTS`→`USE_YN`. The legacy IDO relies on **positional** mapping between the `SELECT` list and the `<out>` rule to bridge this, which is a porting hazard worth naming explicitly (CONST-DATA-I04).

### 1.3 Classification and priority

Per harness standard: `FR-` functional, `NFR-<area>-` non-functional, `CONST-<area>-` constraint, `UC-` use case. Priority is MoSCoW.

### 1.4 Defect disposition (PM ruling, 2026-08-14)

Nineteen defects were confirmed. Consistent with the AMB-01 precedent set on the 문자내역 slice, **all are fixed**; four required an explicit PM ruling because they change scope or affect external integrations rather than merely correcting broken code (§6).

| ID | Sev | Defect | Disposition |
|----|-----|--------|-------------|
| D-I1 | Critical | 중지 updates `FT_INST_INFO`; list/detail/create/delete all use `FT_FTIS_INFO`. Disable reports success and changes nothing the portal reads | FIX → FR-INSTL-001 + operational data audit (AMB-I01) |
| D-I2 | Critical | No authorization on any of the 8 services — `<login>Y</login>` only. Any authenticated user can delete or overwrite any institution | FIX → FR-AZ-I01…I04 |
| D-I3 | Critical | 중복검사 (`biztalk_admin_01_l001`) runs `KKB_FT_FTIS_INFO_L002` and returns the full record **including `ATK`** for any guessed 기관코드 (`K0` + 4 chars) | FIX → FR-INSTC-005, FR-ATK-004 |
| D-I4 | High | `ATK` generated in the browser by `Math.random()` — not a CSPRNG; 20 chars from a handler named "generate random 32 byte" | FIX → FR-ATK-001 |
| D-I5 | High | `ATK` rendered unmasked in the list grid for every institution and declared in the `_l001` out-rule | FIX → FR-ATK-002 |
| D-I6 | High | Duplicate check enforced only by the JS flag `DUP_CHECK_YN`; server IDO is a blind UPSERT on `FINTECH_ISCD`, so a direct call with an existing code **silently overwrites** that institution and its `ATK` | FIX → FR-INSTC-004 |
| D-I7 | High | Hard `DELETE` with no institution-level history; message history keyed on `IS_CD` orphaned | FIX → FR-INSTL-004/005 (AMB-I03) |
| D-I8 | Medium | `biztalk_admin_00_d001_act.jsp:87` tests `isError(idoOut1)` inside the history loop instead of `idoOut2` — a failed history insert is ignored and the transaction still commits | FIX → FR-INSTL-006 |
| D-I9 | Medium | `to_char(now(),'YYYYMMDD24MISS')` in `_C001` omits `HH`, so `RGDT`/`LAST_AMDT` carry a literal `24` where the hour belongs. Masked because the grid renders only `substring(0,8)`. `KKB_DPNO_HIS_C001` uses the correct `HH24`, proving it a typo | FIX → FR-INSTC-006 |
| D-I10 | Medium | Pagination declared but absent: `PAGE_NO`/`INQ_TOTL_NCNT` are in the contract and sent by the JS, but the SQL has no `LIMIT`/`OFFSET` and returns no total. The contract item id is also malformed — `id="PAGE_NO⟨tab⟩"` — so it could never bind | FIX → FR-INST-003 |
| D-I11 | Medium | `ISNM LIKE '%'||:IS_NM||'%'` silently drops rows with NULL 기관명 even on an empty search; `%` and `_` in input are unescaped | FIX → FR-INST-004/005 |
| D-I12 | Medium | Grid renderer concatenates a DB value into an inline `onclick` unescaped: `onclick="fn_getDetail('"+value+"');"`. Reachable because 기관코드 format is client-enforced only (D-I19) | FIX → FR-INST-007 |
| D-I13 | Medium | 담당자관리 is a non-functional stub: tab commented out, 추가/삭제 buttons have no handlers, `fn_checkManager()` empty, no create/delete IDO exists. Permission gate is a client-side `alert('권한 없음')` while `_l002` stays directly callable | OUT OF SCOPE (AMB-I02); endpoints not ported |
| D-I14 | Low | grid2 headers misaligned — `gridColName` has 4 entries but colDefs reference `[1]`,`[3]`,`[4]`; index 4 is `undefined` | N/A — screen excluded (D-I13) |
| D-I15 | Low | grid2 reads key `RGSR_ID`; contract and IDO return `RGDR_ID` → column always blank | N/A — screen excluded (D-I13) |
| D-I16 | Low | 삭제 `confirm()` fires **before** the selection check, so the user confirms an irreversible delete then learns nothing was selected. 중지 has no confirmation at all | FIX → FR-INSTL-007 |
| D-I17 | Low | `biztalk_admin_01_c001_act.jsp` calls `commit()` but never `endTransaction()` (unlike `_d001`); `FINInstitution…reload()` sits in `catch(Throwable){printStackTrace();}`, so a cache-refresh failure after a committed change is swallowed | FIX → FR-INSTC-008, NFR-OPS-I02 |
| D-I18 | Low | No 재사용 path — `_u001` accepts `USE_YN` but the screen only ever sends `N`; re-enabling requires the edit popup | FIX → FR-INSTL-002 |
| D-I19 | Medium | All validation is client-side: 기관코드 length/`K0` prefix, 사업자등록번호 digits, required fields. The contract declares `IS_CD` length 16 while the UI enforces 6 | FIX → FR-INSTC-003 |

> **Pattern note.** The 문자내역 defects lived in *gaps between layers*; the 로그인 defects were *deliberately disabled controls*. This slice shows a third signature: **controls that exist only in the browser** — duplicate check, permission gate, key generation, and every format rule. Each looks enforced from the UI and none is enforced by the server.

---

## 2. Functional Requirements

### 2.1 Access control

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| FR-AZ-I01 | Every 이용기관관리 service requires an authenticated session **and** an operator role. Fixes D-I2, where `<login>Y</login>` was the only gate on delete, disable and upsert | Must | Security test per endpoint |
| FR-AZ-I02 | Authorization is enforced **server-side on each service**. Hiding a button or raising a client-side `권한 없음` alert is not an access control. Fixes D-I2 | Must | Security test (direct endpoint call as non-operator) + code review |
| FR-AZ-I03 | Client-company (tenant) users cannot reach this module at all, and cannot enumerate other client companies through it. Extends FR-TEN-004 | Must | Security test |
| FR-AZ-I04 | Every state-changing operation records actor identity, timestamp, target 기관코드 and before/after values | Must | Integration test + operational audit |

### 2.2 Institution list and search (screen 00)

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| FR-INST-001 | Users search institutions by 기관명 (partial match) and 상태 (전체 / 사용 / 사용안함) | Must | E2E test |
| FR-INST-002 | The grid presents 기관코드, 기관명, 영문명, 사용여부, 인증키 (masked — FR-ATK-002), 등록일자, 수정일자, 설명 | Must | E2E test |
| FR-INST-003 | The list is **server-side paginated**; the response carries the page slice plus a total count. Fixes D-I10 | Must | Integration test |
| FR-INST-004 | An institution whose 기관명 is NULL is **not silently dropped** from results. Fixes D-I11, where `ISNM LIKE '%'||:IS_NM||'%'` excluded NULLs even with an empty search box | Must | Integration test (row with NULL 기관명) |
| FR-INST-005 | `%` and `_` in the 기관명 search are treated as literal characters, not wildcards. Fixes D-I11 | Must | Unit test |
| FR-INST-006 | 사용여부 renders as 사용 / 미사용; an unmapped value renders verbatim rather than blank, consistent with FR-MSG-005 | Must | Unit test |
| FR-INST-007 | Clicking 기관코드 opens that institution's edit view. All values are rendered through escaping — no markup or handler is built by string concatenation. Fixes D-I12 | Must | Security test (stored-XSS payload in 기관코드) |
| FR-INST-008 | 등록일자 and 수정일자 display date **and** time. *(Legacy truncates to `substring(0,8)`, which is what hid D-I9 for four years)* | Should | E2E test |
| FR-INST-009 | Row actions (수정 / 중지 / 재사용 / 삭제) operate on exactly one selected institution; with none selected the action is unavailable | Must | E2E test |

### 2.3 Registration and edit (screen 01)

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| FR-INSTC-001 | An operator registers an institution with 기관코드, 기관명, 영문명, 사업자등록번호, 인증키, 사용여부 and 설명 | Must | E2E test |
| FR-INSTC-002 | 기관코드 is immutable once created | Must | Integration test (update attempt must be rejected) |
| FR-INSTC-003 | All validation rules are enforced **server-side**: required fields, 기관코드 format, 사업자등록번호 digits, field lengths. Fixes D-I19, where every rule lived only in the browser | Must | Integration test per rule (direct service call bypassing the UI) |
| FR-INSTC-004 | 등록 and 수정 are **distinct operations**. 등록 with an existing 기관코드 is rejected with a duplicate error and **never** overwrites the existing record. Fixes D-I6, where the server was a blind UPSERT gated only by a JS flag | Must | Integration test (direct call with an existing code must not mutate it) |
| FR-INSTC-005 | The duplicate check returns **only an availability result** — never another institution's record, and never its 인증키. Fixes D-I3 | Must | Security test |
| FR-INSTC-006 | 등록일시 and 최종수정일시 are stored as a full `YYYYMMDDHH24MISS` timestamp. Fixes D-I9 — the legacy pattern omits `HH`, writing a literal `24` as the hour on every record | Must | Unit test + data check |
| FR-INSTC-007 | 등록자 and 최종수정자 are taken from the authenticated session, never from the request body | Must | Integration test |
| FR-INSTC-008 | After a successful change the institution cache is refreshed; a refresh failure is surfaced as an error and alerted, not swallowed. Fixes D-I17 | Must | Integration test (simulated refresh failure) |
| FR-INSTC-009 | 사업자등록번호 is validated as a 10-digit number *(length assumed — see AMB-I06)* | Should | Unit test |

### 2.4 Lifecycle — disable, re-enable, delete

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| FR-INSTL-001 | 중지 sets 사용여부 = `N` on **the same record the list reads** (`FT_FTIS_INFO`). Fixes D-I1, where the update targeted `FT_INST_INFO` and the operation was a silent no-op | Must | Integration test (disable, then re-query the list) |
| FR-INSTL-002 | 재사용 restores 사용여부 = `Y` from the list screen. Fixes D-I18 — the capability existed server-side but had no UI path | Must | E2E test |
| FR-INSTL-003 | A status change requires explicit confirmation and writes a history record (FR-AZ-I04) | Must | E2E test + integration test |
| FR-INSTL-004 | 삭제 is a **logical delete** — the record is flagged deleted and retained with a deletion history entry. Fixes D-I7 (AMB-I03) | Must | Integration test |
| FR-INSTL-005 | A logically deleted institution is excluded from the default list, and its 발신번호 are deactivated with `KKB_DPNO_HIS` entries (`ACN='D'`) preserved as today | Must | Integration test |
| FR-INSTL-006 | Deletion executes as **one transaction**; any failure in any sub-operation rolls back the whole thing. Fixes D-I8, where a failed history insert was tested against the wrong result object and committed anyway | Must | Integration test (forced history-insert failure) |
| FR-INSTL-007 | The confirmation dialog appears only after a valid selection. Fixes D-I16 | Must | E2E test |
| FR-INSTL-008 | Before deletion the operator is shown the dependent-record counts (발신번호, 문자발송내역) that will be affected | Should | E2E test |
| FR-INSTL-009 | An institution that is 미사용 or logically deleted **cannot authenticate to the send API**. *(This is what 사용여부 is for; D-I1 means it is currently unenforced from this screen)* | Must | Integration test |

### 2.5 인증키 (ATK) handling

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| FR-ATK-001 | 인증키 is generated **server-side** using a cryptographically secure RNG. Fixes D-I4 — browser `Math.random()` is not a CSPRNG and its output is predictable | Must | Code review + security test |
| FR-ATK-002 | 인증키 is **masked** in the list grid and in every list response. Fixes D-I5 | Must | Security test |
| FR-ATK-003 | Revealing a full 인증키 is an explicit, separately authorized and audited action | Must | Security test + audit review |
| FR-ATK-004 | 인증키 is never returned by the duplicate check, never written to a log, and never included in an export. Fixes D-I3 | Must | Static analysis + security test |
| FR-ATK-005 | Per-institution key **rotation** is a first-class operation, so a future mass reissue or a move to hashed storage needs no code change. *(Addresses the residual risk accepted in AMB-I04)* | Should | Integration test |
| FR-ATK-006 | Existing 인증키 values are **preserved unchanged** through migration — client-company integrations must not break. Per AMB-I04 | Must | Migration verification |

### 2.6 Excluded from this slice

| Item | Reason |
|------|--------|
| 담당자관리 (Manager management) — tab, 추가/삭제, and services `biztalk_admin_00_l002` / `_l003` | Non-functional stub with no write path in the legacy (D-I13). Excluded per AMB-I02; to be re-planned as its own requirement. **The `_l002` endpoint must not be ported in its current open form**, since it currently exposes the full manager roster to any authenticated caller |
| Institution list export | No export exists on this screen; consistent with the AMB-07 disposition on 문자내역 |

---

## 3. Non-Functional Requirements

### 3.1 NFR-PERF (performance)

| REQ-ID | Requirement | Measurement |
|--------|-------------|-------------|
| NFR-PERF-I01 | Institution list response **P95 < 1 s** at default page size. *(Tighter than the 문자내역 target because this table holds hundreds of rows, not millions — see AMB-I07)* | Load test |
| NFR-PERF-I02 | Default page size 20 rows; maximum 200 | Load test |
| NFR-PERF-I03 | Registration, edit, status change and delete each complete **P95 < 1 s** | Load test |

### 3.2 NFR-SEC (security)

| REQ-ID | Requirement | Verification |
|--------|-------------|--------------|
| NFR-SEC-AUTHZ-I01 | Role-based authorization is enforced server-side on every endpoint in this module. A privilege check that exists only in the client is treated as a release blocker | Security audit + code review |
| NFR-SEC-CRED-I01 | 인증키 is handled as a **credential**: CSPRNG-generated with ≥ 128 bits of entropy, masked in transit to the UI, never logged | Security audit |
| NFR-SEC-CRED-I02 | No endpoint discloses credential material in response to an unauthenticated or merely-authenticated (non-operator) request | Security test |
| NFR-SEC-INJ-I01 | All query parameters are bound, never concatenated; all output is escaped at render time | Static analysis |
| NFR-SEC-PII-I01 | Person names in history records (`RGSR_NM`) remain encrypted at rest, preserving the existing `ENCRYPT()` behavior | Security audit |
| NFR-SEC-LOG-I01 | No credential, encrypted value or personal data appears in application logs. *(Legacy `util.getLogger().debug()` calls emit error payloads — must be reviewed)* | Static analysis |
| NFR-SEC-CHANNEL-I01 | All client traffic over TLS; the portal is internet-facing (proposal §3) | Security audit |

### 3.3 NFR-OPS (operations)

| REQ-ID | Requirement | Verification |
|--------|-------------|--------------|
| NFR-OPS-AUDIT-I01 | Every create, update, status change and delete writes an audit record with actor, timestamp and before/after values | Operational audit |
| NFR-OPS-AUDIT-I02 | Audit records are retained for the statutory term `[보류]` (OI-02, carried from Skill 01) | Operational audit |
| NFR-OPS-I01 | An institution change is reflected in the runtime institution cache, or the operation reports failure | Integration test |
| NFR-OPS-I02 | A cache-refresh failure raises an operational alert. Fixes D-I17, where it was swallowed by `catch(Throwable){printStackTrace();}` | Integration test |

### 3.4 NFR-COMPAT / NFR-USE

| REQ-ID | Requirement | Verification |
|--------|-------------|--------------|
| NFR-COMPAT-I01 | Supported browsers per AMB-09 (modern evergreen, assumed) | Compatibility test |
| NFR-USE-I01 | Search criteria persist when returning from the edit popup to the list | Should — E2E test |

---

## 4. Constraints

| REQ-ID | Constraint | Basis |
|--------|-----------|-------|
| CONST-DATA-I01 | `FT_FTIS_INFO` is the authoritative institution master. `FT_INST_INFO` is written by no requirement in this spec; its status is settled by the operational data audit (AMB-I01) | AMB-I01; 5 of 6 legacy queries |
| CONST-DATA-I02 | Logical delete (FR-INSTL-004) is implemented **without any DDL** — deletion is the existing `IS_STTS` column set to `'D'`, with history written to the existing audit store. CONST-DATA-01 ("schema reused unchanged, no DDL migration in scope") therefore holds unchanged — CONFLICT-I02 dissolved | AMB-I03; ADR-INST-014 |
| CONST-DATA-I03 | The DB functions `ENCRYPT()` / `decrypt()` remain the mechanism for 발신번호 and 등록자명. Key material lives in the database, not the application | `KKB_DPNO_HIS_C001`, `KKB_DPNO_LDGR_D002`; matches CONST-DATA-02 |
| CONST-DATA-I04 | The legacy IDO maps `SELECT` columns to output fields **positionally**, bridging the `FINTECH_ISCD`→`IS_CD` naming gap. The port must map explicitly by name; a silent positional port would misalign columns without failing | `IDO.KKB_FT_FTIS_INFO_L001` |
| CONST-BIZ-I01 | Existing 인증키 values are preserved unchanged — they are live credentials in client-company integrations | AMB-I04 |
| CONST-TECH-01 | Java 17+ / Spring Boot 3.x, MyBatis, React SPA | ADR-001 (proposal §11) |
| CONST-LEGAL-01 | Personal data masked in UI, logs and exports | 개인정보보호법 (BR-007) |
| CONST-LEGAL-02 | Audit log retention per 전자금융감독규정 / ISMS-P, term `[보류]` | BR-016, OI-02 |
| CONST-SEC-01 | Internet-facing and multi-tenant; no endpoint may rely on network-perimeter protection as its access control | Proposal §3, RISK-006 |

---

## 5. Use Cases

| UC-ID | Scenario | Primary user | Related FR |
|-------|----------|--------------|------------|
| [UC-INST-001](use-cases/UC-INST-001.md) | Search and review the institution registry | Operator | FR-INST-001…009, FR-AZ-I01…I04, FR-ATK-002 |
| [UC-INST-002](use-cases/UC-INST-002.md) | Register a new institution and edit an existing one | Operator | FR-INSTC-001…009, FR-ATK-001/003/004/006 |
| [UC-INST-003](use-cases/UC-INST-003.md) | Disable, re-enable and delete an institution | Operator | FR-INSTL-001…009, FR-ATK-005 |

Orphan check: every FR, NFR and CONST in this document maps to at least one of UC-INST-001…003 — see [requirements-matrix.csv](requirements-matrix.csv). **Orphan count: 0.**

---

## 6. AMBIGUOUS / open items

### 6.1 Resolved by PM (2026-08-14)

| ID | Item | Candidates | PM response | Status |
|----|------|-----------|-------------|--------|
| AMB-I01 | 중지 targets `FT_INST_INFO` while everything else uses `FT_FTIS_INFO` (D-I1) | A: fix + audit live data / B: fix code only / C: `FT_INST_INFO` is correct | **A — fix, and raise an operational task to find institutions believed stopped but still active** | RESOLVED |
| AMB-I02 | 담당자관리 is an unfinished stub (D-I13) | A: exclude from scope / B: build out fully / C: port read-only | **A — exclude, re-plan separately** | RESOLVED |
| AMB-I03 | Hard delete, no institution history, orphaned message history (D-I7) | A: soft delete / B: hard delete + history / C: as-is | **A — logical delete with history** | RESOLVED |
| AMB-I04 | 인증키 is weak, plaintext, exposed in the grid and disclosed by the duplicate check (D-I3…I6) | A: stop exposure, keep keys / B: reissue all / C: harden new only | **A — preserve existing keys, close every exposure path, move generation server-side** | RESOLVED |

### 6.2 Conflicts requiring G1 acknowledgement

| ID | Conflict | Resolution |
|----|----------|-----------|
| CONFLICT-I02 | **AMB-I03 (logical delete) vs. CONST-DATA-01** — the 문자내역 spec constrains the programme to "the existing schema reused unchanged; no DDL migration in this scope", but logical delete was assumed to require a delete flag and a deletion history table | **DISSOLVED at Skill 3 (2026-08-14).** Design analysis found the conflict rested on a false premise. `FT_FTIS_INFO` already carries a status column (`IS_STTS`), and a DB-backed `AuditService` already exists from the 로그인 slice. Deletion is therefore recorded as `IS_STTS='D'` plus an audit event — **no DDL at all**. CONST-DATA-01 stands unmodified and no precedent for schema change is set. See [ADR-INST-014](../design/adr/ADR-INST-014-lifecycle-state-model.md) |
| RESIDUAL-I01 | **AMB-I04 accepts a known-weak credential.** Preserving existing 인증키 values keeps integrations alive but leaves keys that were generated by `Math.random()` in the browser in production use. Exposure paths close; the underlying entropy does not improve | Accepted by PM as a deliberate trade-off. Mitigated by FR-ATK-005 (rotation as a first-class operation) so a reissue campaign becomes an operational decision rather than a development project. **Should be revisited once the portal goes internet-facing** — it interacts with CONST-SEC-01 |

### 6.3 Open

| ID | Item | Candidates | Working assumption | Owner | Needed by |
|----|------|-----------|--------------------|-------|-----------|
| AMB-I05 | Cascade scope for logical delete — which `IS_CD`-keyed data (발신번호, 수수료, 템플릿, 문자발송내역) must be blocked, retained or archived | A: block new activity, retain all history / B: per-table policy | A — retain everything, block new activity | Domain owner | Skill 3 |
| AMB-I06 | Canonical 기관코드 format. The UI enforces exactly 6 characters with a `K0` prefix; the service contract declares length 16. 사업자등록번호 length is likewise unstated | A: 6 chars `K0`+4, BRNO 10 digits (assumed) / B: contract's 16 is canonical | A | Domain owner | Skill 3 |
| AMB-I07 | Response-time target for the institution list | A: P95 < 1 s (assumed, small table) / B: reuse the 3 s 문자내역 target | A | PM | Skill 3 |
| AMB-I08 | `BSNN_STTS_CKYN` gates `KKB_FT_FTIS_INFO_L003`, which selects institutions for what appears to be a business-status check. Its relationship to 사용여부 is unrecoverable from code — two independent enable flags may exist | A: independent of 사용여부, out of scope here / B: must be reconciled | A | Domain owner | Skill 3 |
| AMB-I09 | 인증키 masking format and who may reveal it (FR-ATK-002/003) | A: show last 4, reveal restricted to a senior operator role / B: never revealable, rotation only | A | PM | Skill 3 |

| AMB-I10 | Enforcement of 사용여부 / deleted state at the **send API**, which is the legacy IRIS runtime and not this portal (FR-INSTL-009) | A: portal writes state, legacy enforces, gap tracked as a cutover risk · B: change the legacy send path · C: new gate owned by this portal | **A — resolved at Skill 3, 2026-08-14.** This portal writes and verifies the state; enforcement remains with the legacy runtime and the residual gap is tracked as RISK-I02 | PM | ✅ closed |

> Five items remain open (AMB-I05…I09), plus OI-02 (audit retention) carried from Skill 01. None blocks design: each carries a stated working assumption.
>
> **CONFLICT-I02 no longer blocks G1** — Skill 3 found it rested on a false premise and dissolved it without any schema change (§6.2). G1 approval therefore needs to cover only RESIDUAL-I01.

---

## 7. Change history

| Date | Version | Change | Author |
|------|---------|--------|--------|
| 2026-08-14 | 1.0 | Initial draft — 이용기관관리 slice, from static analysis of 19 legacy artifacts; 19 defects identified, 4 PM rulings incorporated | Skill 02 |
| 2026-08-14 | 1.1 | **CONFLICT-I02 dissolved** during Skill 3 design — logical delete needs no DDL (ADR-INST-014), so CONST-DATA-01 stands unchanged. CONST-DATA-I02 rewritten. **AMB-I10 added and closed** — send-API enforcement stays with the legacy runtime (RISK-I02) | Skill 03 |

---

**G1 approval (analysis gate)**

| Date | Approver | Comment | Status |
|------|----------|---------|--------|
| — | PM | Pending. CONFLICT-I02 is **no longer a condition** — dissolved at Skill 3 with no schema change. Approval need only acknowledge **RESIDUAL-I01** (weak credentials retained for integration compatibility) | **PENDING** |
