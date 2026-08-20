# Business Requirements — IRIS BizTalk Portal (login + biztalk rebuild)

> **Version**: 1.0
> **Date**: 2026-08-14
> **Predecessor**: [PROJECT-PROPOSAL.md](PROJECT-PROPOSAL.md)
> **Status**: **APPROVED** — PM, 2026-08-19
> **Confidence legend**: `[확인]` confirmed by PM · `[추출]` extracted from legacy source, domain-owner sign-off required · `[보류]` open

---

## 1. Overview

This document states business-level requirements for rebuilding the **login** and **biztalk** modules of legacy `IRIS_ADMIN`. Technical specification follows in [REQUIREMENTS-SPEC.md](../requirements/REQUIREMENTS-SPEC.md) (Skill 2).

**A note on provenance that governs this entire document.** There is no runnable legacy environment and no Jex documentation or in-house expertise. Every rule marked `[추출]` was derived by reading the legacy source — chiefly the 49 `WSVC.*.xml` service contracts and the 31 action JSPs — and **none of them is authoritative until the domain owner signs it off**. That sign-off is the mechanism by which the project's parity KPI becomes measurable. Rules are stated here as candidates for validation, not as established fact.

## 2. Business domains

| Domain | Description | Owner |
|--------|-------------|-------|
| Authentication & account (weauth) | Tenant and operator login, session, credential management, SMS OTP | Operations / 정보보호 |
| Template management | 기본 컨텐츠 관리 — BizTalk/알림톡 message template registration and editing (screens 01/11/12/13/31/41) | Client company + operator |
| Message dispatch | 문자 전송 — single and bulk send via BizTalk and SMS channels (screen 50) | Client company |
| Delivery result | 문자내역 / 문자결과수신 — dispatch results and provider receipts (screens 40/61) | Client company |
| Transaction inquiry | 거래내역조회 — send/transaction history search and Excel export (screens 30/32/60) | Client company |
| Institution administration | 이용기관 관리, 담당자관리, 발신번호, 수수료 (screens 00/10) | Internal operator |
| Reporting & aggregation | 이용기관 보고서, 일간집계배치 (screen 20, `BATCH_BIZTALK_DAILY`) | Internal operator |

## 3. Business rules

| BR-ID | Rule | Source / evidence | Priority | Confidence |
|-------|------|-------------------|----------|------------|
| BR-001 | Messages may be sent **only from a registered 발신번호**. Unregistered sender numbers are rejected | `biztalk_admin_50_view.jsp` — on-screen notice "발신 번호는 등록된 번호로만 가능" | Must | `[추출]` |
| BR-002 | Each service declares whether authentication is required; services with `<login>Y</login>` reject unauthenticated calls | `WSVC.biztalk_sms_send.xml` `<login>Y</login>` | Must | `[추출]` |
| BR-003 | Services are callable **only inside their configured time window**, with separate windows for weekdays, Saturdays, Sundays and holidays. Calls outside the window are rejected with the service's configured message code | `WSVC` fields `tmUseYn`, `strTm`/`endTm`, `satStrTm`/`satEndTm`, `sunStrTm`/`sunEndTm`, `holStrTm`/`holEndTm`, `svcTmMsgCd` | Must | `[추출]` |
| BR-004 | Services may carry a **usage cap** (`maxUse`); `0` denotes unlimited. Calls beyond the cap are rejected | `WSVC` field `maxUse` | Must | `[추출]` |
| BR-005 | Services flagged for monitoring (`mntLogYn=Y`) must write a transaction audit record for every invocation | `WSVC` fields `mntLogYn`, `logLv` | Must | `[추출]` |
| BR-006 | A client-company user may access **only their own 이용기관's** data — templates, sends, history, receipts. Cross-tenant access is prohibited | New requirement from the self-service shift (proposal §3). **Not present in legacy**, which assumes operator-wide visibility | Must | `[확인]` |
| BR-007 | Recipient phone numbers (`CLPH_NO`, 수신번호) are personal information: masked in UI lists and in application logs, protected at rest, and every access recorded | `WSVC.biztalk_sms_send.xml` output rule `CLPH_NO 휴대폰번호 length=100`; 개인정보보호법 | Must | `[확인]` |
| BR-008 | Field-level input/output contracts (name, length, character-set constraint such as `fullChar`) are enforced per service | `WSVC` `<rule><in>/<out>` item definitions | Must | `[추출]` |
| BR-009 | Send/transaction records are aggregated daily by a batch job over a start/end date range, producing per-institution usage figures | `BATCH_BIZTALK_DAILY.java` (`BIZTALK(일간집계배치)`, params `START_DT`/`END_DT`) | Must | `[추출]` |
| BR-010 | 수신번호-based inquiry (screen 60) returns **completed transactions only**; in-flight sends are excluded, with users directed to the send-history screen for those | `biztalk_admin_60_view.jsp` — "완료된 거래만 확인", "보다 정확한 내역은 전송 내역에서 조회" | Should | `[추출]` |
| BR-011 | Search results on list screens are **exportable to Excel** (결과 추출 / 다운로드) | `*_spreadsheet_view.jsp` on screens 20/30; POI 3.9 in legacy | Must | `[추출]` |
| BR-012 | Each 이용기관 carries a **수수료** (fee rate) maintained by internal operators only; client-company users must never see or edit it | `biztalk_admin_10_view.jsp` — 수수료, 발신번호, 이용기관 fields | Must | `[추출]` |
| BR-013 | Message templates require registration before use; approval workflow before activation is **suspected but unverified** | 기본 컨텐츠 관리 screens (01/11/12/13); `_c001`/`_u001` action naming implies create/update flows | Must | `[보류]` — verify with domain owner |
| BR-014 | An SMS **인증번호전송 (OTP)** service exists and returns the target 휴대폰번호; it participates in authentication | `WSVC.biztalk_sms_send.xml` — `BIZTALK(인증번호전송)`, `trTp=U` | Should | `[추출]` |
| BR-015 | Credentials must **not** use MD5. The legacy MD5 scheme is replaced with a modern password hash; SEED usage is reviewed and replaced or justified | `weauth/security/md5`, `weauth/security/seed`; 전자금융감독규정. **Explicit approved exception to "preserve business behavior"** | Must | `[확인]` |
| BR-016 | Audit and transaction records are retained for the statutory term | 전자금융감독규정 / ISMS-P | Must | `[보류]` — term unset (OI-02) |

> **BR-002 through BR-005 are the highest-risk rules in this document.** They are enforced today by the *Jex runtime*, not by any biztalk source file. Discarding Jex removes them silently — nothing will fail to compile, and no test will fail, but the behavior disappears. Each must be rebuilt as explicit cross-cutting infrastructure in the new stack (proposal RISK-002).

## 4. Data lifecycle

| Data | Created | Modified | Deletion / disposal | Retention |
|------|---------|----------|---------------------|-----------|
| Message template | On registration by client admin | Edit before/after approval `[보류]` | Soft-delete assumed (`_d001` actions) | `[보류]` |
| Send request / transaction record | On dispatch | Status updated by delivery receipt | Not user-deletable | `[보류]` — statutory (OI-02) |
| Delivery receipt (문자결과수신) | On provider callback | Terminal on receipt | Follows transaction record | Same as transaction |
| Recipient phone number (PII) | With send request | Not modified | Disposed with parent record; masked throughout life | Same as transaction |
| Daily aggregate | By `BATCH_BIZTALK_DAILY` | Recomputable by date range | — | `[보류]` |
| 이용기관 / 담당자 master | Operator registration | Operator edit | Deactivation (사용 / 사용 안함 state on screen 00) | Contract term + statutory |
| Access / audit log | Every `mntLogYn=Y` invocation | Immutable | Disposal only after retention term | `[보류]` — statutory |

## 5. Roles and permissions

| Role | Permissions | Data scope |
|------|-------------|------------|
| Client-company admin (tenant) | View / create / edit templates; send; query history; export | **Own 이용기관 only** (BR-006) |
| Internal operator | All tenant functions plus 이용기관·담당자·발신번호·수수료 administration and reports | All institutions |
| System administrator | Deployment, configuration, monitoring | No business data access beyond operational need |
| Auditor | Read-only access to audit logs and transaction records | All institutions, read-only |

> Legacy screens 00/10/20 carry no tenant scoping because the legacy had no external users. The role model above is **new design, not extracted behavior** — it is the primary functional delta between old and new, and the main reason "parity" cannot mean literal reproduction.

## 6. External interfaces (business view)

| Party | Business function | Frequency | SLA |
|-------|-------------------|-----------|-----|
| Kakao BizTalk / 알림톡 provider | Message delivery + result callback | Real-time | Provider-defined `[보류]` |
| SMS gateway (문자중계사) | SMS delivery, 인증번호전송, 문자결과수신 | Real-time | Provider-defined `[보류]` |
| PostgreSQL (existing) | System of record — shared with legacy during coexistence | Continuous | Internal |
| Billing / settlement | **Out of scope** — aggregation batch remains in legacy | Daily | See OI-01 |

## 7. Compliance / regulation

| Regulation | Applies | Requirement |
|------------|---------|-------------|
| 개인정보보호법 | **Y** | PII masking in UI and logs, encryption at rest, access logging, disposal on term expiry (BR-007) |
| 전자금융감독규정 | **Y** | Access control, network separation, change control, cryptography standards. **Mandatory security ADR in Skill 3.** Conflict with external exposure recorded as RISK-006 |
| ISMS-P | **Y** | System is within certified scope; controls and audit evidence must be maintained |
| 신용정보법 | N | No credit information identified within biztalk scope |
| PCI-DSS | N | No card data identified within biztalk scope |

## 8. Operational policy

### 8.1 Availability
- Service window: 24h baseline (`strTm 000000` – `endTm 240000` in legacy WSVC), with per-service weekday/Saturday/Sunday/holiday overrides (BR-003)
- Maintenance window: `[보류]`

### 8.2 Backup / recovery
- Backup cycle: inherits existing PostgreSQL policy `[보류]`
- RTO: `[보류]` · RPO: `[보류]`

### 8.3 Monitoring
- Targets: send success/failure rate, provider API errors, unsent (미전송) backlog, login failure rate, batch completion
- Alert channels: `[보류]`

---

## Traceability seed (for Skill 2 `trace-mapper`)

| Business area | Legacy artifacts | Target |
|---------------|------------------|--------|
| Login | `src/weauth/**`, `src/pf/{gate,wac}`, `sso.jsp`, 2 WSVC + 1 IDO | New auth service |
| Templates | screens 01/11/12/13/31/41 + matching WSVC | Template API |
| Send | screen 50, `WSVC.biztalk_sms_send`, `WSVC.biztalk_admin_sms` | Dispatch API |
| Delivery result | screens 40/61 | Receipt API |
| Transaction inquiry | screens 30/32/60, `IDO.BIZTALK_APITR_HSTR_L001` | History API |
| Institution admin | screens 00/10 | Admin API (Phase 2 candidate) |
| Reporting | screen 20, `BATCH_BIZTALK_DAILY` | Report API + batch |

---

**Approval history**

| Date | Approver | Comment | Status |
|------|----------|---------|--------|
| 2026-08-14 | PM | Awaiting first review | PENDING |
| 2026-08-19 | PM | 승인 — Skill 1 산출물 확정. G1(요구사항 게이트)은 별건으로 남아 있다 / Approved; the Skill 1 outputs are settled. G1 remains separate | **APPROVED** |
