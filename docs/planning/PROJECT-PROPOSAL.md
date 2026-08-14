# Project Proposal — IRIS BizTalk Portal (login + biztalk rebuild)

> **Version**: 1.0
> **Date**: 2026-08-14
> **Author**: PM (daralonsingle@gmail.com)
> **Approver**: PM (single-approver model — see §12)
> **Status**: DRAFT — awaiting PM first review
> **Source skill**: `01-plan-project` · **Next skill**: `02-define-requirements`

---

## 1. One-line definition

> Rebuild the **login** and **biztalk** modules of the legacy `IRIS_ADMIN` as a new **Spring Boot 3 + React** application for **client-company self-service**, preserving business behavior while discarding the Jex framework and the JSP layer entirely.

Scope is deliberately **2 of the ~40 modules** in IRIS_ADMIN — roughly 2% of its 1,088 JSP surface.

## 2. Business background / problem

### 2.1 Why now
Business and feature pressure on the biztalk module. The current Jex/JSP stack cannot deliver new capability at acceptable speed or cost. The specific capability driving this project is the shift identified in §3: moving client companies (이용기관) from **operator-mediated service to self-service**.

Notably, this is *not* primarily an EOL-remediation or vendor-lock-in project — although the legacy does carry unsupported dependencies (log4j 1.2.9, POI 3.9, `javaee.jar`), which the rebuild resolves as a side effect.

### 2.2 Problem being solved
Today every client-company request — template registration, send history lookup, delivery investigation — is routed through internal operators using an intranet-only admin console. This caps throughput at operator availability, and adds latency to every routine client action. Left unsolved, the operator team remains a permanent bottleneck on biztalk growth.

### 2.3 Core value proposition
Client companies serve themselves for the three highest-frequency actions (template management, send, history inquiry), while operators retain exclusive control of institution and fee administration. Internally, the biztalk module moves onto a mainstream stack that the organization can staff and change quickly.

## 3. Users / personas

| Class | Persona | Primary scenario |
|-------|---------|------------------|
| **Primary** | **Client-company admin (이용기관 담당자)** — external staff of a contracting company | Logs in, manages own message templates, sends messages, queries own send history and exports to Excel |
| Secondary | Internal operator (운영자) | Administers 이용기관 master data, 발신번호 registration, 수수료 rates, and institution reports (screens 00/10/20) |
| Operations | System administrator | Deployment, monitoring, audit-log custody, incident response |

> **Defining consequence.** The legacy IRIS_ADMIN is an **internal, perimeter-protected** console whose services assume "an operator sees everything." Rebuilding for external client self-service makes the system **internet-facing and multi-tenant**. Tenant scoping therefore becomes a *correctness* requirement, not a feature — see RISK-003.

## 4. Core scenarios (MVP scope)

| # | Scenario | User | Expected result |
|---|----------|------|-----------------|
| 1 | Register / edit message templates (기본 컨텐츠 관리) | Client admin | Template stored against the tenant, available for send after approval |
| 2 | Send messages, including bulk spreadsheet upload | Client admin | BizTalk/SMS dispatched via provider; result recorded and traceable |
| 3 | Query send history and export (거래내역조회 / 문자내역) | Client admin | Tenant-scoped results by period/status/recipient, exportable to Excel |
| — | Log in and hold a session | Client admin | *Enabling capability, not a headline scenario — in scope via the login module rebuild* |

Out of MVP: billing/settlement integration (see §6), operator screens if phasing is adopted (see §8).

## 5. Screen / menu structure

### 5.1 Menu tree (derived from legacy scan — all 7 groups in scope per Q5, role-gated)

```
IRIS BizTalk Portal
├─ Login / account            (weauth — rebuilt)
├─ [Operator] 담당자관리                 (legacy 00)
├─ [Operator] 이용기관 관리 · 발신번호 · 수수료   (legacy 10)
├─ [Operator] 이용기관 보고서              (legacy 20)
├─ [Tenant]   거래내역조회                (legacy 30)
├─ [Tenant]   문자내역 · 문자결과수신        (legacy 40)
├─ [Tenant]   문자 전송                  (legacy 50)
└─ [Tenant]   수신번호 전송내역 조회         (legacy 60)
```

Role-based menus per Q5: one codebase serving both audiences, with menu and data visibility scoped by role. See RISK-006 for the network-separation constraint this creates.

### 5.2 Wireframes
None supplied. Screen definitions will be reconstructed from the legacy `web/view/jex/iris_admin/biztalk/*_view.jsp` files and the corresponding `WSVC.*.xml` field rules during Skill 2.

## 6. External system integration

| # | System | Protocol | Direction | Note |
|---|--------|----------|-----------|------|
| 1 | Kakao BizTalk / 알림톡 provider | REST (assumed) | Outbound + receipt callback | Primary delivery channel |
| 2 | SMS gateway (문자중계사) | REST / provider SDK | Outbound + 문자결과수신 | Includes 인증번호전송 (OTP) path — `WSVC.biztalk_sms_send` |
| 3 | Existing PostgreSQL database | JDBC | Read/write | **Schema reused in place — no data migration.** Confirmed `jdbc:postgresql:` in legacy config |
| — | Billing / settlement | — | — | **Out of scope.** See open issue OI-01 |

**OI-01 — billing boundary.** Billing integration was excluded, but the legacy contains `WSVC.biztalk_api_bill`, 수수료 fields on screen 10, and `BATCH_BIZTALK_DAILY` (일간집계배치) feeding usage aggregation. Working assumption: **the aggregation batch stays in legacy and the new app does not push to billing.** Since screen 10 is in scope and carries fee data, this needs confirmation before Skill 2 closes.

## 7. Non-functional baseline (NFR)

| Area | Requirement | Unit |
|------|-------------|------|
| Throughput | **< 10,000 messages/day** | msg/day |
| Architecture consequence | Synchronous send with retry is sufficient — **no message broker required** | — |
| Performance | Screen/API response P95 < 1s (proposed — to be fixed in Skill 2) | ms |
| Availability | Service window per legacy WSVC config: `000000–240000` (24h), with per-service holiday/Sat/Sun windows configurable | uptime % |
| Security | 전자금융감독규정 + ISMS-P compliant; PII masked in UI and logs; MD5 credential scheme eliminated | regulation |
| Retention | Audit/transaction log retention obligation applies — **term `[보류]`**, commonly 5 years for 전자금융 records. Confirm before Skill 3 | years |
| Multi-tenancy | Every data access tenant-scoped; cross-tenant read = reportable incident | — |

The low throughput figure is the single biggest simplifier in this project: it removes queueing, backpressure, partitioning, and horizontal scaling from the design.

## 8. Constraints

| Class | Content |
|-------|---------|
| Schedule | **3–6 months** |
| Staffing | **1–2 developers** |
| Technology | Java 17+ / Spring Boot 3.x, React SPA, MyBatis, PostgreSQL (existing schema) |
| Regulatory | Four regimes apply (§7) — mandatory security ADR in Skill 3 |
| Legacy | **No runnable legacy environment** — source tree only |
| Operations | Legacy IRIS_ADMIN continues running; biztalk screens coexist until cutover |

> ### ⚠ Capacity assessment — PM decision required
>
> The declared scope is 7 screen groups (20 view JSPs + 31 action JSPs + 49 service contracts), a new authentication module, multi-tenancy retrofit, a React frontend, and four regulatory regimes' controls. Available capacity is **6–12 person-months**; this proposal sizes the work at **12–18 person-months**. The regulatory and tenant-isolation portions are the parts that cannot be cut.
>
> **Recommendation — phase the delivery:**
> - **Phase 1** — login + tenant surface (screens 30/40/50/60). Fits available capacity, delivers all three MVP scenarios, keeps legacy operator screens running untouched, and defers the 망분리 problem (RISK-006).
> - **Phase 2** — operator surface (screens 00/10/20, incl. 수수료), after Phase 1 cutover.
>
> Scope reduction is the PM's call, not the delivery team's. Recorded here for an explicit decision at G1.

## 9. Success KPI

| KPI | Target | Measurement method |
|-----|--------|--------------------|
| **Specification parity with legacy** | 100% of extracted behaviors implemented and signed off | Screen-by-screen sign-off by the domain owner against the traceability matrix (see §10, RISK-001) |
| *(recommended addition)* Delivery velocity | Time to ship a biztalk change vs. Jex stack | Lead time comparison post-cutover |

> **KPI caveat — must be read before G1.** Parity was selected as the sole KPI, but there is **no runnable legacy environment** (§8), so *behavioral* parity cannot be measured — there is nothing to execute the new system against. Parity is therefore redefined as **specification parity**: extracted from the 49 `WSVC.*.xml` contracts and 31 action JSPs, validated by the domain owner.
>
> Separately: parity measures fidelity, not the §2 business driver. A system at 100% parity delivers, by definition, no new capability. The recommended second KPI addresses this.

## 10. Assumptions and risks

### Assumptions
- The legacy source tree at `D:\WORDSPACE26\HARNESS\TESTS\oldproject` is complete and current.
- The existing PostgreSQL schema is reused in place; no data migration is performed.
- A domain owner is available to validate extracted specifications (confirmed at Q12b).
- Legacy IRIS_ADMIN remains in operation throughout, including for out-of-scope modules.
- Kakao BizTalk and SMS gateway provider contracts and credentials carry over unchanged.

### Risks (elaborated in Skill 3 `risk-register.md`)

| ID | Risk | Impact | Probability |
|----|------|--------|-------------|
| RISK-001 | **Source is the only specification.** No runnable legacy environment and no Jex expertise or documentation. Behavior must be reverse-engineered from 49 WSVC XMLs and 31 action JSPs. *Mitigated* by the confirmed availability of a domain owner and by making Skill 2 analysis the critical path | H | H |
| RISK-002 | **Jex runtime behavior loss.** Service time-window gating, per-service audit logging (`mntLogYn`), usage caps (`maxUse`) and auth flags (`<login>`) are enforced by the Jex runtime, not by application code. Discarding Jex silently drops them unless each is rebuilt explicitly | H | H |
| RISK-003 | **Multi-tenant isolation retrofit.** Legacy services assume an operator who sees all 이용기관. Every query needs tenant scoping added; a single miss exposes one client's data to another — reportable under §7 obligations | H | M |
| RISK-004 | **Scope vs. capacity gap.** 12–18 person-months of scope against 6–12 available (§8) | H | H |
| RISK-005 | **Credential scheme.** Legacy login uses MD5 (`weauth/security/md5`) and SEED (`weauth/security/seed`). MD5-based credential handling cannot pass a G3 security audit, and will now be internet-facing. Replacement is mandatory — an explicit, approved exception to "preserve business behavior" | H | H |
| RISK-006 | **망분리 vs. external exposure.** 전자금융감독규정 network-separation duties conflict with exposing one role-based application to external client companies while it also serves operator fee administration. Requires an architecture decision (separate deployments or strict network tiering), not a permissions setting | M | H |
| RISK-007 | **Isolated source corruption.** *(Corrected 2026-08-14 — originally recorded as an EUC-KR/CP949 conversion risk; the sources are in fact UTF-8, so no bulk conversion is needed.)* A few files contain stray corrupted bytes, e.g. `type="tex<Hangul>t"` inside an HTML attribute in `biztalk_admin_40_view.jsp`. Per-file repair during porting, not a systematic problem | L | M |

## 11. Preferred technology stack (reference — fixed by ADR-001 in Skill 3)

| Area | Preference | Rationale |
|------|-----------|-----------|
| Language / runtime | Java 17+ | Spring Boot 3 baseline |
| Backend framework | Spring Boot 3.x | Replaces Jex; mainstream staffing |
| Frontend | **React SPA + REST** | Fits tenant self-service direction; needs a grid library to replace the Jex grid |
| Persistence | **MyBatis** (retained) | Carrying existing SQL and IDO query definitions across minimises translation risk — decisive given RISK-001 |
| Database | PostgreSQL (existing instance and schema) | No migration |
| Auth | Spring Security; OAuth2 where `JexOAuth2` was used; SMS OTP via existing 인증번호전송 path | MD5 replaced (RISK-005) |
| CI/CD | `[보류]` — decide in Skill 3 | |

> Note: React introduces a second discipline for a 1–2 person team, compounding RISK-004. Thymeleaf was offered as the lower-cost alternative and not selected; recorded here so ADR-001 revisits the trade-off with evidence.

## 12. Approval / governance

### Gate mapping — single-approver model

| Gate | Timing | Approver |
|------|--------|----------|
| G1 Analysis | Skill 2 complete | PM |
| G2 Design | Skill 3 complete | PM |
| G3 Release | Skill 5 complete | PM |

> The harness default routes G3 through 정보보호 + 운영 as well. With four regulatory regimes in scope, a single-approver G3 concentrates compliance acceptance in the PM. Recorded as an accepted governance decision; revisit if an internal audit function requires separate sign-off.

### Reporting

| Cadence | Audience | Format |
|---------|----------|--------|
| Weekly | PM | md |
| Per gate | PM | md + summary |

---

## Appendix A. Glossary

| Term | Definition |
|------|-----------|
| **Jex** | Proprietary WebCash application framework (JexCore/JexWeb/JexBIZ/JexBatch). Provides service dispatch, data binding, time-window gating and audit logging. Being discarded |
| **WSVC** | Jex web-service definition XML. Declares service id, package, transaction type, auth requirement, time windows, and field-level I/O rules. **The de-facto interface specification** |
| **IDO** | Jex data-object definition (query/DAO layer), e.g. `IDO.BIZTALK_APITR_HSTR_L001.xml` |
| **이용기관** | Client company contracting for the messaging service — the tenant unit |
| **알림톡 / BizTalk** | Kakao business messaging channel |
| **발신번호** | Registered sender phone number; sending is permitted only from registered numbers |
| **거래내역조회** | Transaction/send history inquiry (screen 30) |
| **문자결과수신** | SMS delivery-result receipt (screen 40) |
| **수수료** | Per-institution fee rate (screen 10, operator-managed) |
| **SEED** | Korean national block cipher, used in `weauth/security/seed` |
| **CLPH_NO** | Mobile phone number field (휴대폰번호) — PII |

## Appendix B. References and evidence

**Legacy source**: `D:\WORDSPACE26\HARNESS\TESTS\oldproject`

| Evidence | Value |
|----------|-------|
| Modules | `IRIS_ADMIN` (46M), `IRIS_ADMIN_STATIC` (44M), `IRIS_ADMIN_ETC` (13M), `_CONFIG`, `_JEXSETTING` |
| Whole system | 132 Java files / ~32.7 KLOC · 1,088 JSP · 3,050 XML · 941 JS |
| **biztalk module** | **124 files** — 20 view JSP, 31 action JSP, 49 `WSVC` XML, 1 `IDO`, 1 BAT service, `BATCH_BIZTALK_DAILY.java`, 20 JS |
| **login module** | `weauth/{cmo,mng,provider,security/md5,security/seed,util}`, `pf/gate`, `pf/wac`, `sso.jsp`, 3 weAuth JS, 2 WSVC + 1 IDO |
| Database | PostgreSQL (`jdbc:postgresql:` in legacy config) |
| Copyright header | `@COPYRIGHT (c) 2009-2010 WebCash, Inc.`; batch file registered 2021-03-12 |

Key legacy paths:
- `IRIS_ADMIN/web/view/jex/iris_admin/biztalk/` — 20 screen definitions
- `IRIS_ADMIN/web/WEB-INF/action/jex/iris_admin/biztalk/` — 31 action handlers
- `IRIS_ADMIN_ETC/xml/service/WSVC/WSVC.biztalk_*.xml` — 49 service contracts
- `IRIS_ADMIN/src/batch/jex/iris_admin/biztalk/BATCH_BIZTALK_DAILY.java` — daily aggregation
- `IRIS_ADMIN/src/weauth/` — authentication module

## Appendix C. Skill 01 DoD checklist

- [x] All 12 standard questions answered (Q1–Q12; retention term and CI/CD marked `[보류]`)
- [x] §6 regulatory checklist completed — 4 of 5 items apply → **security ADR mandatory in Skill 3**
- [ ] PM first review of this proposal
- [x] `BUSINESS-REQUIREMENTS.md` annex written
- [ ] Ready to enter Skill 2

### §6 regulatory checklist result

| Check | Result | Evidence |
|-------|--------|----------|
| PII processing | **Y** | `CLPH_NO 휴대폰번호` in WSVC output rules; 수신번호 on screens 40/60 |
| Financial transaction processing (BigDecimal required) | **Y** | 수수료 on screen 10, `biztalk_api_bill`, 거래내역 |
| Regulated (전자금융감독규정 / ISMS-P / 신용정보법) | **Y** — 전자금융감독규정, ISMS-P (신용정보법 N) | Q7b |
| Audit log retention obligation | **Y** — term `[보류]` | Q7b; `mntLogYn=Y` in WSVC |
| PCI-DSS / KISA certification | **N** (no card data identified) | No card fields found in biztalk scope |

---

## Open issues carried into Skill 2

| ID | Issue | Owner | Needed by |
|----|-------|-------|-----------|
| OI-01 | Billing boundary — does the new app touch 수수료/`biztalk_api_bill`? | PM | Skill 2 |
| OI-02 | Audit retention term (3 / 5 / 7 years) | PM + 정보보호 | Skill 3 |
| OI-03 | Phase 1 / Phase 2 split decision (§8 capacity) | PM | G1 |
| OI-04 | 망분리 architecture approach (RISK-006) | PM + architect | G2 |
| OI-05 | Domain owner scheduling for spec sign-off sessions | PM | Skill 2 start |
| OI-06 | Tenant account provisioning — self-registration or operator-issued? | PM | Skill 2 |

---

**Approval history**

| Date | Approver | Comment | Status |
|------|----------|---------|--------|
| 2026-08-14 | PM | Awaiting first review | PENDING |
