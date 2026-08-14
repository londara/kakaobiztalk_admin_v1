# Risk Register — IRIS BizTalk Portal · 문자내역 slice

> **Version**: 1.0
> **Date**: 2026-08-14
> **Owner**: PM · **Maintained by**: `architect`
> **Source**: proposal §10 risks carried forward + design-stage risks identified in Skill 03

---

## Summary

| Severity | Count |
|----------|-------|
| High impact | 7 |
| Medium impact | 5 |
| Low impact | 1 |
| **Total** | **13** |

---

### RISK-001 — Legacy source is the only specification
- **영역**: 기술 · **영향**: H · **확률**: H · **전략**: 완화
- **대응 계획**: MyBatis near-verbatim SQL porting (ADR-003) keeps the diff reviewable; every ported statement annotated with defect IDs; domain owner signs off screen-by-screen; spec-parity replaces byte-parity (TEST-PLAN §7)
- **담당자**: `architect` + domain owner · **모니터링**: every sprint review
- **비고**: Mitigated from critical by the confirmed availability of a domain owner (Skill 01 Q12b). If that person becomes unavailable, this returns to critical immediately

### RISK-002 — Jex runtime behavior loss
- **영역**: 기술 · **영향**: H · **확률**: H · **전략**: 완화
- **대응 계획**: The five runtime behaviors (auth flag, time windows, usage caps, audit logging, plus new tenant scoping) are built as explicit components in Sprint 1 (architecture §2.1), not left implicit
- **담당자**: `backend-developer`, reviewed by `code-reviewer` · **모니터링**: Sprint 1 gate
- **비고**: Four of the five are dormant in this slice (`tmUseYn=N`, `maxUse=0`) — built anyway, because retrofitting a cross-cutting control later is what creates gaps

### RISK-003 — Multi-tenant isolation retrofit
- **영역**: 보안 · **영향**: H · **확률**: M · **전략**: 완화
- **대응 계획**: Tenant predicate injected in SQL, never applied post-fetch (ADR-003); static check that every mapper statement touching message tables carries it; cross-tenant integration test is a release gate
- **담당자**: `security-auditor` · **모니터링**: every PR
- **비고**: A single missed predicate is a reportable incident under 개인정보보호법. Detective control via audit logging (ADR-006) backs the preventive one

### RISK-004 — Scope vs capacity (project level)
- **영역**: 일정 · **영향**: H · **확률**: H · **전략**: 완화
- **대응 계획**: This slice is scoped to fit 3 sprints; the project-level gap (12–18 person-months of scope vs 6–12 available) remains open pending the OI-03 phasing decision
- **담당자**: PM · **모니터링**: every sprint review
- **비고**: Unresolved at project level. This slice does not close it, it defers it

### RISK-005 — Legacy credential scheme going internet-facing
- **영역**: 보안 · **영향**: H · **확률**: H · **전략**: 회피
- **대응 계획**: Argon2id adopted (ADR-008, FR-LOGIN-005). Credentials cannot be migrated — a user-facing password reset event is required (CONST-SEC-L01)
- **담당자**: `security-auditor` · **모니터링**: Sprint 1, then every PR (static analysis)
- **비고**: *(Corrected 2026-08-14 after the login-module analysis.)* Originally recorded as "MD5". `weauth/security/md5` does exist, but the actual admin login path hashes with **unsalted SHA-256** (`JexMessageDigest.getHashString(SHA_256, pwd)` in `apc_login_proc_act.jsp`). Both are unfit — no salt, no work factor — so the response is unchanged, but the algorithm named was wrong. The credential migration event still has no communication plan

### RISK-006 — 망분리 vs. external exposure
- **영역**: 보안/규제 · **영향**: M (this slice) / H (project) · **확률**: H · **전략**: 이관
- **대응 계획**: Deferred — this slice is tenant-facing and read-only, so it does not force the decision. The operator surface (screens 00/10/20 with 수수료) does. Architecture decision required before that work (OI-04)
- **담당자**: PM + `architect` · **모니터링**: before operator-surface design
- **비고**: Listed as OPEN residual in threat-model §4

### RISK-007 — Isolated source corruption
- **영역**: 기술 · **영향**: L · **확률**: M · **전략**: 수용
- **대응 계획**: Repair per file during porting (e.g. `type="tex<Hangul>t"` in `biztalk_admin_40_view.jsp`). Files are UTF-8; no bulk conversion needed
- **담당자**: `backend-developer` · **모니터링**: during porting
- **비고**: Corrected from the original Skill 01 entry, which wrongly described an EUC-KR conversion risk

### RISK-008 — Shared database with the running legacy
- **영역**: 기술/보안 · **영향**: M · **확률**: M · **전략**: 수용
- **대응 계획**: New application uses a least-privilege account with no write grants on business tables (ADR-007). Legacy retains broad rights; hardening it is out of scope
- **담당자**: `architect` + operations · **모니터링**: before release
- **비고**: TM-016, accepted residual. Also means legacy writes are immediately visible to the new read path — benign for a read-only slice, a real concern once both write

### RISK-009 — PII key custody remains in the database tier
- **영역**: 보안 · **영향**: M · **확률**: L · **전략**: 수용
- **대응 계획**: Retained deliberately (ADR-005) — migrating would require re-encrypting history and changing the legacy. Revisit if a KMS is adopted
- **담당자**: `security-auditor` · **모니터링**: annual security review
- **비고**: TM-015, requires 정보보호 acceptance at G2

### RISK-010 — Undocumented database dependencies
- **영역**: 기술 · **영향**: M · **확률**: M · **전략**: 완화
- **대응 계획**: `decrypt()` and `masking()` are project dependencies with definitions this project does not hold and cannot version. Obtain and archive their definitions in Sprint 1; a silent change to either alters output with no application-side signal
- **담당자**: `data-model-designer` · **모니터링**: Sprint 1
- **비고**: Newly identified in Skill 03. If `masking()` changes format, parity breaks invisibly

### RISK-011 — React capacity for a 1–2 person team
- **영역**: 인력 · **영향**: H · **확률**: M · **전략**: 완화
- **대응 계획**: ADR-001 adopted React against a higher-scoring server-rendered option. Objective re-open trigger defined: if Sprint 1 frontend tasks slip >50%, ADR-001 is revisited
- **담당자**: PM · **모니터링**: Sprint 1 and Sprint 2 reviews
- **비고**: The weighted evaluation scored 팀 숙련도 5/10 for this option. The decision is the PM's and recorded as an accepted deviation, but the cost it priced in is real

### RISK-012 — New frontend attack surface
- **영역**: 보안 · **영향**: M · **확률**: M · **전략**: 완화
- **대응 계획**: HttpOnly session cookie rather than browser-stored token (ADR-008); CSP; framework escaping; npm dependency scanning and SBOM added to L2 (TM-017, TM-018)
- **담당자**: `security-auditor` · **모니터링**: every PR
- **비고**: This surface did not exist in the server-rendered legacy — it is introduced by ADR-001

### RISK-013 — Slice is not independently releasable
- **영역**: 일정 · **영향**: H · **확률**: H · **전략**: 완화
- **대응 계획**: 문자내역 requires authentication, but the login module has not been through Skill 02. Sprint 1 builds auth infrastructure against a minimal credential store; production cutover requires the login module specified and built. OI-06 (account provisioning) blocks it
- **담당자**: PM · **모니터링**: G2, then Sprint 1
- **비고**: Newly identified in Skill 03. The slice is demonstrable and testable, but not shippable to end users on its own — this should be explicit before G2 approval

---

## Open decisions blocking risk closure

| ID | Decision | Blocks | Owner |
|----|----------|--------|-------|
| OI-03 | Phase 1 / Phase 2 split | RISK-004 | PM |
| OI-04 | 망분리 architecture approach | RISK-006 | PM + architect |
| OI-06 | Tenant account provisioning model | RISK-013, ADR-008 | PM |
| AMB-05 | `STATUS` value 5 meaning | Minor — display behavior | Domain owner |
| AMB-07 | Excel export for 문자내역 | FR-MSG-017 scope | PM |
| AMB-09 | Browser support baseline | NFR-COMPAT-01 | PM |
