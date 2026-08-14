# Requirements Specification — 문자내역 (SMS/BizTalk Message History)

> **Version**: 1.0
> **Date**: 2026-08-14
> **Scope**: legacy screens **40 (문자내역조회)** and **41 (문자상세내역조회)** only — one feature slice of the biztalk module
> **Predecessors**: [PROJECT-PROPOSAL.md](../planning/PROJECT-PROPOSAL.md), [BUSINESS-REQUIREMENTS.md](../planning/BUSINESS-REQUIREMENTS.md)
> **Traceability matrix**: [requirements-matrix.csv](requirements-matrix.csv)
> **Question log**: [questions-log.md](questions-log.md)
> **Status**: **APPROVED (G1)** — 2026-08-14, PM

---

## 1. Overview

This document specifies the 문자내역 feature of the new IRIS BizTalk Portal, derived by static analysis of 10 legacy artifacts (no runnable legacy environment exists — see proposal RISK-001).

### 1.1 Legacy artifacts analyzed

| Layer | File |
|-------|------|
| View | `IRIS_ADMIN/web/view/jex/iris_admin/biztalk/biztalk_admin_40_view.jsp` (149 L) |
| View (detail) | `…/biztalk_admin_41_view.jsp` (215 L) |
| Client logic | `IRIS_ADMIN_STATIC/web/opportal/js/jex/iris_admin/biztalk/biztalk_admin_40.js` (249 L) |
| Client logic (detail) | `…/biztalk_admin_41.js` (101 L) |
| Service contract | `IRIS_ADMIN_ETC/xml/service/WSVC/WSVC.biztalk_admin_40.xml`, `…_40_l001.xml`, `…_41.xml`, `…_41_l001.xml` |
| Action | `IRIS_ADMIN/web/WEB-INF/action/jex/iris_admin/biztalk/biztalk_admin_40_l001_act.jsp`, `…_41_l001_act.jsp` |
| Query | `IRIS_ADMIN_ETC/xml/service/IDO/IDO.KKB_MSG_L002.xml` (list) |
| Query (detail) | `IDO.KKO_SMS_MSG_L001.xml`, `IDO.KKO_MMS_MSG_L001.xml`, `IDO.KKF_SMS_MSG_L001.xml`, `IDO.KKF_MMS_MSG_L001.xml` |
| Dependency | `biztalk_admin_00_l001` (이용기관 list, called by `fn_getIsList()`) |

### 1.2 Data sources

The list query unions **8 tables** — live plus `_LOG` archive for each of four message classes:

| MSG_TYPE | TABLE_TYPE | Live table | Archive table |
|----------|-----------|------------|---------------|
| `AT` (알림톡) | `MMS` | `KKO_MMS_MSG` | `KKO_MMS_MSG_LOG` |
| `AT` (알림톡) | `SMS` | `KKO_SMS_MSG` | `KKO_SMS_MSG_LOG` |
| `FT` (친구톡) | `MMS` | `KKF_MMS_MSG` | `KKF_MMS_MSG_LOG` |
| `FT` (친구톡) | `SMS` | `KKF_SMS_MSG` | `KKF_SMS_MSG_LOG` |

Target DB: `BIZTALK_DB` (PostgreSQL). Phone columns are stored encrypted and read through DB functions `decrypt()` then `masking()`.

### 1.3 Classification and priority

Per harness standard: `FR-` functional, `NFR-<area>-` non-functional, `CONST-<area>-` constraint, `UC-` use case. Priority is MoSCoW.

### 1.4 Defect disposition (PM ruling, 2026-08-14)

Nine defects were confirmed in the legacy implementation. PM ruled **fix all nine** (AMB-01), with CONFLICT-01 resolving D7 in favour of restoring pagination **plus** a range cap. Each fix is therefore an intentional, approved deviation from literal parity and is tracked in the matrix under `defect_ref`.

| ID | Defect | Disposition |
|----|--------|-------------|
| D1 | List service `<login>N</login>` — unauthenticated PII endpoint | FIX → FR-MSG-001 |
| D2 | `to_char(MSGKEY,'9')` yields `#`; MSGKEY search never matches | FIX → FR-MSG-008 |
| D3 | 발신번호/수신번호 form labels swapped vs. grid | FIX → FR-MSG-009 |
| D4 | `PHONE`, `RESULT_CD` sent but absent from contract and SQL (dead search boxes; `RESULT_CD` has no DOM element) | FIX → FR-MSG-010 |
| D5 | `to_char(…,'YYYYY-MM-DD …')` renders 5-digit year in 3 of 4 detail queries | FIX → FR-MSGD-005 |
| D6 | `KKO_MMS_MSG_LOG` uses inclusive `BETWEEN`; other 7 branches exclusive `<` | FIX → FR-MSG-011 |
| D7 | Server pagination commented out | FIX → FR-MSG-007 (per CONFLICT-01) |
| D8 | Period validation compares time-of-day only, rejecting valid multi-day ranges | FIX → FR-MSG-012 |
| D9 | Contract declares 19 output fields; IDOs return 8 | FIX → FR-MSGD-004 |

---

## 2. Functional Requirements

### 2.1 Authentication and tenant scoping

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| FR-MSG-001 | Both the list and detail services **reject unauthenticated requests**. Fixes D1, where the list service ran with `<login>N</login>` and returned masked phone numbers to anonymous callers | Must | Security test + E2E |
| FR-TEN-001 | The 이용기관 identifier used to filter results is **derived from the authenticated session on the server**. A client-supplied tenant identifier is ignored, never trusted | Must | Integration test + security test |
| FR-TEN-002 | A client-company user sees **only their own** 이용기관's records, with no institution selector rendered | Must | Integration test (cross-tenant attempt must return empty) |
| FR-TEN-003 | An operator may select any 이용기관, or all, via an explicit selector | Must | E2E test |
| FR-TEN-004 | The 이용기관 list endpoint (legacy `biztalk_admin_00_l001`, `USE_YN=ALL`) is **restricted to operator roles**. Tenant users must not be able to enumerate other client companies | Must | Security test |

### 2.2 문자내역 list query (screen 40)

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| FR-MSG-002 | Users search message history by: 요청일시 범위 (date + time, **required**), 메시지키, 발신번호, 수신번호, 상태, 유형, 문자타입, 결과코드, and 이용기관 (operator only) | Must | E2E test |
| FR-MSG-003 | Results are returned from the union of live and archive tables for all four message classes (§1.2) | Must | Integration test per table |
| FR-MSG-004 | The result grid presents 12 columns: 유형, 테이블, 메시지키, 이용기관, 상태, 톡결과, 발송번호, 수신번호, 요청일자, 요청시간, 발송시간, 응답시간 | Must | E2E test |
| FR-MSG-005 | 상태 (`STATUS`) is displayed as: `1`=미전송, `2`=전송완료, `3`=톡결과수신, `4`=문자결과수신, `6`=큐입력. An unmapped value is displayed verbatim rather than blank *(legacy renders nothing; `5` is unused — see AMB-05)* | Must | Unit test |
| FR-MSG-006 | 유형 (`MSG_TYPE`) accepts `AT` (알림톡) / `FT` (친구톡) / empty (전체); 문자타입 (`TABLE_TYPE`) accepts `SMS` / `MMS` / empty (전체) | Must | Unit test |
| FR-MSG-007 | The list query is **server-side paginated**; the response carries the page slice plus total count. Fixes D7 | Must | Integration + load test |
| FR-MSG-008 | 메시지키 search matches on the numeric key correctly for keys of any length. Fixes D2 — the legacy `to_char(MSGKEY,'9')` overflows to `#`, so every non-empty search returned zero rows | Must | Unit test (single- and multi-digit keys) |
| FR-MSG-009 | Search field labels match the underlying data: 발신번호 filters the sender column (`CALLBACK`), 수신번호 filters the recipient column (`PHONE`). Fixes D3 | Must | E2E test |
| FR-MSG-010 | 수신번호 and 결과코드 searches are functional — accepted by the service contract and applied in the query. Fixes D4 | Must | Integration test |
| FR-MSG-011 | The 요청일시 range is applied **identically across all 8 sources**: inclusive start, exclusive end. Fixes D6 | Must | Integration test (boundary rows in each table) |
| FR-MSG-012 | Range validation compares full **date-and-time** values, not time-of-day. `2026-01-01 18:00 ~ 2026-01-05 09:00` is valid and must be accepted. Fixes D8 | Must | Unit test |
| FR-MSG-013 | The search period may not exceed **31 days**; a longer range is rejected with a clear message *(cap value assumed — see AMB-06)* | Must | Unit test |
| FR-MSG-014 | Clicking 메시지키 in a row opens the 문자상세내역 view for that message | Must | E2E test |
| FR-MSG-015 | Empty search criteria other than the date range mean "no filter on that field", matching legacy `CASE WHEN :X = '' THEN 1=1` behavior | Must | Integration test |
| FR-MSG-016 | Rows whose 발신번호 is NULL are **not silently dropped** when a 발신번호 filter is absent. *(Legacy `CALLBACK LIKE '%'||:CALLBACK||'%'` excludes NULLs because `NULL LIKE …` is NULL — an unintended side effect of D4's sibling clause)* | Should | Integration test |

### 2.3 문자상세내역 detail view (screen 41)

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| FR-MSGD-001 | The detail service rejects unauthenticated requests and enforces the same tenant scoping as FR-TEN-001 — a user must not read another tenant's message by guessing its key | Must | Security test |
| FR-MSGD-002 | The detail query is selected by `MSG_TYPE` × `TABLE_TYPE`: `AT`+`SMS`→`KKO_SMS_MSG`, `AT`+`MMS`→`KKO_MMS_MSG`, `FT`+`SMS`→`KKF_SMS_MSG`, `FT`+`MMS`→`KKF_MMS_MSG`, each unioned with its `_LOG` archive | Must | Integration test (4 paths) |
| FR-MSGD-003 | `MSG_TYPE` and `TABLE_TYPE` are mandatory; a missing value is rejected with a validation error. Unlike the legacy, an **unrecognised** `MSG_TYPE` is also rejected rather than silently treated as `FT` | Must | Unit test |
| FR-MSGD-004 | The detail view presents all declared fields: `PROFILE_KEY`, `AD_FLAG`, `RSLT`, `TEMPLATE_CODE`, `CALLBACK`, `PHONE`, `REQDATE`, `SENTDATE`, `RSLTDATE`, `REPORTDATE`, `MSG`, `IMG_PATH`, `IMG_URL`, `WI_FLAG`, `BUTTON_JSON`, `FAILED_TYPE`, `FAILED_SUBJECT`, `FAILED_IMG`, `FAILED_MSG`. Fixes D9 — 11 of these are declared but never populated today | Must | Integration test per field |
| FR-MSGD-005 | Timestamps render as `YYYY-MM-DD HH:MM:SS`. Fixes D5 — three of four legacy queries use the `YYYYY` pattern, producing a 5-digit year | Must | Unit test |
| FR-MSGD-006 | Image, button and failure information are presented in dedicated sections. *(Legacy tabs `tbl2`/`tbl3` exist in markup but their handlers are commented out)* | Should | E2E test |
| FR-MSGD-007 | The message body (`MSG`), `BUTTON_JSON` and `FAILED_MSG` are displayed **read-only** | Must | E2E test |
| FR-MSGD-008 | A detail lookup that matches no record shows an explicit "not found" state rather than an empty form | Should | E2E test |

### 2.4 Export

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| FR-MSG-017 | 문자내역 search results are exportable. *(Screen 40 has no export in the legacy — screens 20/30 do, via `*_spreadsheet_view.jsp`. Included as Could pending confirmation — see AMB-07)* | Could | E2E test |

---

## 3. Non-Functional Requirements

### 3.1 NFR-PERF (performance)

| REQ-ID | Requirement | Measurement |
|--------|-------------|-------------|
| NFR-PERF-01 | List query response **P95 < 3 s** for a 31-day range at default page size *(assumed baseline — see AMB-08)* | Load test |
| NFR-PERF-02 | Default page size 50 rows; maximum 500 | Load test |
| NFR-PERF-03 | Detail query response **P95 < 1 s** | Load test |
| NFR-PERF-04 | Decryption is applied only to rows in the returned page wherever the query plan permits, not to every row of an 8-table union | Query plan review |

### 3.2 NFR-SEC (security)

| REQ-ID | Requirement | Verification |
|--------|-------------|--------------|
| NFR-SEC-AUTH | Every 문자내역 service requires an authenticated session. No service may be exposed with the legacy `<login>N</login>` posture | Security audit |
| NFR-SEC-TENANT | Tenant isolation is enforced server-side on every query path. A cross-tenant read is a release blocker | Security test + code review |
| NFR-SEC-PII | 발신번호 and 수신번호 are masked in all list and detail responses, preserving the legacy `masking(decrypt(...))` behavior | Security audit |
| NFR-SEC-PII-02 | Unmasked phone values are never returned to the client, logged, or written to export files | Static analysis + audit |
| NFR-SEC-LOG | No PII or secret appears in application logs. *(Legacy `util.getLogger().debug()` calls in the action JSPs log error payloads — must be reviewed)* | Static analysis |
| NFR-SEC-CHANNEL | All client traffic over TLS; the portal is internet-facing (proposal §3) | Security audit |
| NFR-SEC-INJ | All query parameters bound, never string-concatenated. *(Legacy uses named binds — preserve this)* | Static analysis |

### 3.3 NFR-OPS (operations)

| REQ-ID | Requirement | Verification |
|--------|-------------|--------------|
| NFR-OPS-AUDIT | Every 문자내역 service invocation writes an audit record, replacing the Jex `mntLogYn=Y` runtime behavior (BR-005). Both services carry `mntLogYn=Y` today | Operational audit |
| NFR-OPS-AUDIT-02 | Audit records are retained for the statutory term `[보류]` (OI-02) | Operational audit |
| NFR-OPS-TIME | Service time-window gating (BR-003) is enforced by the application. Both services currently set `tmUseYn=N` with a 24h window, so the feature is inactive but the mechanism must exist | Unit test |

### 3.4 NFR-COMPAT / NFR-USE

| REQ-ID | Requirement | Verification |
|--------|-------------|--------------|
| NFR-COMPAT-01 | Supported browsers: current Chrome, Edge, Safari. *(Legacy targets a jQuery-era baseline; modern-only is assumed — AMB-09)* | Compatibility test |
| NFR-USE-01 | Search criteria persist when returning from the detail view to the list | Should — E2E test |

---

## 4. Constraints

| REQ-ID | Constraint | Basis |
|--------|-----------|-------|
| CONST-TECH-01 | Java 17+ / Spring Boot 3.x, MyBatis, React SPA | ADR-001 (proposal §11) |
| CONST-DATA-01 | The existing `BIZTALK_DB` PostgreSQL schema is reused unchanged; no DDL migration in this scope | Proposal §6 |
| CONST-DATA-02 | The DB functions `decrypt()` and `masking()` are **existing database dependencies** and must remain the mechanism for phone data. Key material and masking policy live in the database, not the application | `IDO.KKB_MSG_L002` and the 4 detail IDOs |
| CONST-DATA-03 | `MSGKEY` is an integer column; the detail query casts it (`CAST(:MSGKEY AS INTEGER)`). Non-numeric input must be rejected before reaching the database | `IDO.KKO_SMS_MSG_L001` |
| CONST-LEGAL-01 | 수신번호/발신번호 are personal information — masked in UI, logs and exports | 개인정보보호법 (BR-007) |
| CONST-LEGAL-02 | Audit log retention per 전자금융감독규정 / ISMS-P, term `[보류]` | BR-016, OI-02 |
| CONST-SEC-01 | The system is internet-facing and multi-tenant; no endpoint may rely on network-perimeter protection as its access control | Proposal §3, RISK-006 |

---

## 5. Use Cases

| UC-ID | Scenario | Primary user | Related FR |
|-------|----------|--------------|------------|
| [UC-MSG-001](use-cases/UC-MSG-001.md) | Search 문자내역 and review results | Client-company admin | FR-MSG-001…017, FR-TEN-001…004 |
| [UC-MSG-002](use-cases/UC-MSG-002.md) | Open 문자상세내역 for one message | Client-company admin | FR-MSGD-001…008 |

Orphan check: every FR and NFR in this document maps to UC-MSG-001 or UC-MSG-002 — see [requirements-matrix.csv](requirements-matrix.csv). **Orphan count: 0.**

---

## 6. AMBIGUOUS / open items

| ID | Item | Candidates | PM response | Status |
|----|------|-----------|-------------|--------|
| AMB-01 | Parity vs. correctness for 9 defects | A: fix all / B: security+broken only / C: D1 only / D: case-by-case | **A — fix all 9** | RESOLVED |
| AMB-02 | Tenant scoping mechanism | A: server-enforced from session / B: + operator dropdown / C: keep client-supplied | **A — server-enforced** | RESOLVED |
| AMB-03 | Paging and range limits | A: paging+31d / B: paging+92d / C: paging only / D: keep as-is | **D**, then superseded by CONFLICT-01 | SUPERSEDED |
| AMB-04 | 11 unpopulated detail fields | A: implement / B: drop to 8 / C: failure fields only / D: 보류 | **A — implement all** | RESOLVED |
| CONFLICT-01 | AMB-01 (fix D7) vs AMB-03 (keep no paging) | A: A03 wins / B: paging, no cap / C: paging + cap | **C — paging + range cap** | RESOLVED |
| AMB-05 | `STATUS` value `5` is unused, and unmapped values render blank in the legacy grid. Is `5` a retired state or a gap? | A: display verbatim (assumed) / B: map to a label the domain owner supplies | Pending | **PENDING** — domain owner |
| AMB-06 | Maximum search period — CONFLICT-01 set "paging + cap" but no value | A: 31 days (assumed) / B: 92 days | **A — 31 days**, accepted at G1 | RESOLVED |
| AMB-07 | Does 문자내역 need Excel export? Screen 40 has none today; screens 20/30 do | A: no export (legacy parity) / B: add export | Pending | **PENDING** |
| AMB-08 | Response-time target for the list query | A: P95 < 3 s (assumed) / B: P95 < 1 s | **A — P95 < 3 s list / < 1 s detail**, accepted at G1 | RESOLVED |
| AMB-09 | Browser support baseline | A: modern evergreen only (assumed) / B: include legacy IE-era support | Pending | **PENDING** |

> Three items remain open after G1 (AMB-05, AMB-07, AMB-09), plus OI-02 (audit retention term) carried from Skill 01. None blocks design work: each carries a stated working assumption and is `Should`-or-below in impact. AMB-05 and AMB-07 need the domain owner / PM by Skill 3; OI-02 must close before the security ADR is finalised.

---

## 7. Change history

| Date | Version | Change | Author |
|------|---------|--------|--------|
| 2026-08-14 | 1.0 | Initial draft — 문자내역 slice, from static analysis of 10 legacy artifacts | Skill 02 |
| 2026-08-14 | 1.1 | **G1 approved.** AMB-06 (31-day cap) and AMB-08 (P95 targets) accepted as specified | Skill 02 |

---

**G1 approval (analysis gate)**

| Date | Approver | Comment | Status |
|------|----------|---------|--------|
| 2026-08-14 | PM | Approved. Scope: 문자내역 slice (screens 40/41). Approval carries acceptance of the two G1-flagged assumptions — AMB-06 (31-day search cap) and AMB-08 (P95 < 3 s list / < 1 s detail) | **APPROVED** |
