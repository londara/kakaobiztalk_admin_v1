# 개발계획서 — 이용기관관리 (Client Institution Management)

> **Version**: 1.0
> **Date**: 2026-08-14
> **Predecessor**: [REQUIREMENTS-SPEC-INSTITUTION.md](../requirements/REQUIREMENTS-SPEC-INSTITUTION.md) — **G1 PENDING**
> **Companion plans**: [DEV-PLAN.md](DEV-PLAN.md) (문자내역), [DEV-PLAN-LOGIN.md](DEV-PLAN-LOGIN.md)
> **Status**: DRAFT — awaiting G2

---

## 1. Overview

| Item | Content |
|------|---------|
| Module | 이용기관관리 — registry list/search, registration/edit, lifecycle, 인증키 handling |
| Duration | 4 weeks — 2 sprints × 2 weeks |
| Team | 1–2 developers + agent team |
| Requirements | 37 FR · 16 NFR · 5 CONST (58 matrix rows) |
| Legacy defects to close | 19 (16 in scope; 3 belong to the excluded 담당자관리 screen) |
| Standard | harness-standards v1.0 (Team-with-Leader) |

**Why this module matters more than its size suggests.** It is two screens and eight legacy services, but it issues the `IS_CD` every other slice filters on and the `ATK` client companies authenticate with. It is also the first slice that **writes control data a second running system depends on** — the legacy send runtime reads the same table. That boundary, not the CRUD, is where the design effort went ([ADR-INST-016](adr/ADR-INST-016-legacy-coexistence.md)).

> ⚠ **G1 not yet approved.** Written against a DRAFT specification, following the precedent of DEV-PLAN-LOGIN. Two of the four Skill 2 rulings are already reflected in accepted ADRs. **CONFLICT-I02 no longer blocks G1** — design found it rested on a false premise and dissolved it with no schema change (ADR-INST-014). G1 need only acknowledge RESIDUAL-I01.

### 1.1 Readiness

The foundation this module needs already exists:

| Dependency | Status |
|------------|--------|
| `ROLE_OPERATOR` + `/api/admin/**` rule | ✅ Delivered (로그인 slice) |
| `AuditService` with a DB-backed store | ✅ Delivered — reused unchanged for institution events |
| `PagedResult`, `TenantContext` | ✅ Delivered (문자내역 slice) |
| `InstitutionController` / `InstitutionMapper` | ⚠️ Exist but **query a table that does not exist** — see §4.3 |

로그인 stands at 55/59 with the remainder blocked on non-code matters; 문자내역 at 47/52 with nothing left closable by code. Neither blocks this slice.

## 2. Technology stack

Inherited from [ADR-001](adr/ADR-001-tech-stack.md) (ACCEPTED, programme-wide). No stack-level re-evaluation was warranted — this module introduces no new runtime concern, no new external channel and no new persistence technology. The new decisions are all module-level:

| Area | Selected | ADR |
|------|----------|-----|
| Language / framework | Java 17 / Spring Boot 3.x | ADR-001 (inherited) |
| Persistence | MyBatis on existing `FT_FTIS_INFO` | ADR-003 (inherited) |
| Authorization | Spring Security, `ROLE_OPERATOR` | ADR-008 (inherited) |
| **Lifecycle / logical delete** | **`IS_STTS='D'` + existing audit store — zero DDL** | [ADR-INST-014](adr/ADR-INST-014-lifecycle-state-model.md) |
| **인증키 handling** | **`SecureRandom` 160-bit; masked; plaintext at rest (residual)** | [ADR-INST-015](adr/ADR-INST-015-atk-credential-handling.md) |
| **Legacy coexistence** | **Portal is system of record; legacy enforces; gap tracked** | [ADR-INST-016](adr/ADR-INST-016-legacy-coexistence.md) |
| Frontend | React — list + edit modal | ADR-001 |

## 3. Architecture

See [architecture-overview-INSTITUTION.md](architecture-overview-INSTITUTION.md).

Core components: `InstitutionAdminController` · `InstitutionService` · `InstitutionLifecycleService` · `AtkGenerator` · `AtkMasker` · `InstitutionCacheNotifier` · `InstitutionAdminMapper`

## 4. Sprint plan

| Sprint | Weeks | Scope | DoD |
|--------|-------|-------|-----|
| **Sprint I1** | 1–2 | Read path + foundation correction — mapper fix, search, paging, masking, authorization, audit wiring, list UI | FR-INST-001…009, FR-AZ-I01…I04, FR-ATK-002, NFR-PERF-I01/I02. Closes D-I1(read side), D-I5, D-I10, D-I11, D-I12 |
| **Sprint I2** | 3–4 | Write path + lifecycle — create/update split, server validation, key generation/rotation/reveal, status transitions, logical delete, cache notifier, edit UI | FR-INSTC-\*, FR-INSTL-\*, FR-ATK-001/003…006. Closes D-I1, D-I3, D-I4, D-I6…D-I9, D-I16…D-I19. 7-dimension ≥ 90 |

### 4.1 Task DAG

```mermaid
flowchart TD
  i1a["I1: fix InstitutionMapper<br/>FT_FTIS_INFO + real columns"] --> i1b["I1: Institution domain<br/>+ InstitutionStatus enum"]
  i1b --> i1c["I1: search mapper<br/>LIMIT/OFFSET + COUNT"]
  i1c --> i1d["I1: LIKE escaping<br/>+ NULL-safe name filter"]
  i1b --> i1e["I1: AtkMasker<br/>last-4"]
  i1a --> i1f["I1: authorization sweep<br/>@PreAuthorize on every endpoint"]
  i1f --> i1g["I1: audit wiring<br/>institution.* actions"]
  i1c --> i1h["I1: InstitutionService<br/>+ search API"]
  i1d --> i1h
  i1e --> i1h
  i1h --> i1i["I1: React list screen"]
  i1g --> i1j["I1: negative-path security tests"]
  i1f --> i1j

  i1h --> i2a["I2: AtkGenerator<br/>SecureRandom 160-bit"]
  i1b --> i2b["I2: server-side validation<br/>code format, BRNO, required"]
  i2b --> i2c["I2: create — rejects duplicate"]
  i2b --> i2d["I2: update — never creates<br/>11 owned columns only"]
  i2c --> i2e["I2: availability endpoint<br/>boolean only"]
  i2a --> i2f["I2: key rotate + reveal<br/>audited"]
  i2d --> i2g["I2: status transitions<br/>disable / enable"]
  i2g --> i2h["I2: logical delete<br/>IS_STTS='D' + cascade, 1 tx"]
  i2h --> i2i["I2: dependent-count preview"]
  i2d --> i2j["I2: InstitutionCacheNotifier<br/>failure surfaced"]
  i2c --> i2k["I2: React edit modal"]
  i2f --> i2k
  i2h --> i2l["I2: hardening + QA + security audit"]
  i2i --> i2l
  i2j --> i2l
  i2k --> i2l
```

### 4.2 Why the mapper fix is task one

`InstitutionMapper` was delivered in the 문자내역 slice against a **guessed** table name — its Javadoc says so explicitly and asks for DBA confirmation. Skill 2's analysis of screen 00 supplies the answer, and **every identifier in that query is wrong**: `BIZTALK_INSTITUTION`→`FT_FTIS_INFO`, `IS_CD`→`FINTECH_ISCD`, `IS_NM`→`ISNM`, `USE_YN`→`IS_STTS`.

It fails against the real database, so the 문자내역 institution dropdown does not work outside tests. It is scheduled first because the rest of the read path builds on it, and because it is a **live defect in delivered code**, not new work.

### 4.3 Relationship to the other slices

- **문자내역** consumes `InstitutionController`. §4.2 fixes it; no other change is needed there.
- **로그인** supplies `ROLE_OPERATOR` and `AuditService`. Both are consumed unchanged — this module adds no authentication surface.
- **No sequencing conflict.** This slice can start immediately.

## 5. Team composition

| Team | Members | Leader | Write directories |
|------|---------|--------|-------------------|
| Build | `backend-developer`, `frontend-developer`, `code-reviewer` | `code-reviewer` | `src/`, `reviews/` |
| Validation | `security-auditor`, `qa-engineer` | `security-auditor` | `qa/`, `security/` |
| Ops | `docs-writer`, `architect` | `docs-writer` | `docs/` |

> `security-auditor` again carries weight: 8 of the 20 threats are High, and the two highest-severity defects (D-I2 broken access control, D-I3 credential disclosure by enumeration) are pure security work rather than feature work.

## 6. LLM model assignment

| Agent | Model | Reason |
|-------|-------|--------|
| `architect` | Opus | Coexistence boundary reasoning |
| `security-auditor` | Opus | **Critical** — credential handling, authorization sweep, enumeration surface |
| `backend-developer` | Sonnet | Implementation against a clear spec |
| `frontend-developer` | Sonnet | List + edit modal |
| `code-reviewer` | Opus | Defect detection |
| `qa-engineer` | Sonnet | Test construction |
| `docs-writer` | Haiku | Document assembly |

## 7. Staffing

| Role | Count | Responsibility |
|------|-------|----------------|
| PM | 1 (human) | Gate approval; **operational data audit for D-I1** (RISK-I01); cutover ownership of RISK-I02 |
| Developer | 1–2 (human) | Review agent output |
| DBA | 1 (human, consult) | Confirm `FT_FTIS_INFO` column semantics, `BSNN_STTS_CKYN` (AMB-I08) |
| 정보보호 | 1 (human, consult) | TM-I005 sign-off — plaintext credential storage |
| AI agents | ~8 | Implementation, test, review, audit |

## 8. Risk management

See [risk-register-INSTITUTION.md](risk-register-INSTITUTION.md) — 12 entries.

Top 3:
1. **RISK-I01 — institutions already in the D-I1 state.** The code fix does not repair existing data. Some institutions an operator believes are stopped may still be sending. Needs an operational data audit, not a commit
2. **RISK-I02 — send-API enforcement gap during coexistence.** We write the state; the legacy enforces it. Bounded by cutover, but D-I1 is proof this exact gap has already caused a silent failure once
3. **RISK-I03 — retained weak credentials.** RESIDUAL-I01: exposure paths close, entropy does not improve, and the two key populations are indistinguishable by query

## 9. Quality targets

| Metric | Target |
|--------|--------|
| Line coverage | ≥ 80% |
| Branch coverage | ≥ 70% |
| **`domain` (lifecycle, key handling) packages** | **≥ 95%** |
| 7-dimension self-assessment | ≥ 90 |
| CVSS ≥ 7.0 defects | 0 (release gate) |
| Defect regression tests | 100% passing (16 in-scope legacy defects) |
| Negative-path authorization tests | 1 per admin endpoint, each proving a **denial** |
| ADR count | 3 new (ADR-INST-014/015/016) |
| DDL statements | **0** — CONST-DATA-01 verified in CI |

## 10. Governance

| Gate | Timing | Approver | Artifact |
|------|--------|----------|----------|
| G1 Analysis | Skill 2 | PM | REQUIREMENTS-SPEC-INSTITUTION.md — **PENDING** (RESIDUAL-I01 only) |
| G2 Design | Skill 3 | PM + Architect | This document + TEST-PLAN + threat model + 3 ADRs |
| Sprint gate | Each sprint end | PM | SPRINT-LOG |
| G3 Release | Skill 5 | PM (+ 정보보호 **recommended**) | All verification reports |

> As with 로그인, a PM-only G3 is not the right check here. TM-I005 accepts plaintext storage of live customer credentials — a compliance judgement, not an engineering one.

## 11. Backup / rollback

- Legacy screens 00/01 remain available until this slice is verified, then are **disabled** (T-I2-12) to stop dual writes.
- Rollback = re-enable the legacy screens. **This is genuinely reversible**, unlike the 로그인 slice: no data format changes, no DDL, no credential rewriting. Institutions created or edited by the new portal are ordinary `FT_FTIS_INFO` rows the legacy reads natively.
- **One asymmetry:** institutions logically deleted under ADR-INST-014 carry `IS_STTS='D'`, which the legacy screens cannot display or reverse — they filter on `'Y'`/`'N'`. After rollback such institutions are invisible to the legacy UI though still present. Reversal requires a direct `UPDATE` to `'N'`. This is a small, documented, one-row manual operation, not a migration.

## 12. Financial-sector obligations

| Item | Applies | Note |
|------|---------|------|
| 전자금융감독규정 | Y | Administrative access records; operator-only registry control |
| ISMS-P | Y | Credential lifecycle in certified scope |
| PII encryption | Y | `RGSR_NM` in 발신번호 history stays encrypted (CONST-DATA-I03) |
| Key management | Y | ADR-INST-015 — **with a recorded exception**: `ATK` remains plaintext at rest (TM-I005) |
| Audit log | Y | Every state change and key reveal; retention per ADR-006 |

---

**G2 approval (design gate)**

| Date | Approver | Comment | Status |
|------|----------|---------|--------|
| 2026-08-14 | PM | TM-I005 and TM-I007 are blocking conditions; RISK-I01 needs an owner and a date | PENDING |
| 2026-08-14 | Architect | Design complete; 3 ADRs accepted; zero DDL confirmed. TM-I007 is not closable by code | PENDING |
