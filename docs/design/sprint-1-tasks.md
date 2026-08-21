# Sprint 1 Task List — Skeleton, cross-cutting block, authentication

> **Sprint**: 1 of 3 · **Duration**: 2 weeks
> **Goal**: A running Spring Boot 3 application in which **no endpoint can be reached anonymously and no query can escape its tenant** — the two properties the legacy got wrong.
> **Predecessor**: [DEV-PLAN.md](DEV-PLAN.md) · **Status**: DRAFT — PM + Leader agreement required

---

## Sprint goal rationale

Sprint 1 delivers no user-visible feature deliberately. It builds the controls that the discarded Jex runtime used to provide (RISK-002) and the tenant boundary that never existed (RISK-003). Both are cross-cutting: retrofitting them after the query layer exists is how gaps are created, and every subsequent sprint depends on them being right.

## Task list

| ID | Task | Requirements | Owner | Depends on | Est. |
|----|------|--------------|-------|------------|------|
| **T1-01** | Project skeleton — Maven multi-module, Spring Boot 3.x, package structure per architecture §5 | CONST-TECH-01 | `backend-developer` | — | 0.5 d |
| **T1-02** | React application skeleton, build pipeline, proxy configuration to the API | CONST-TECH-01 | `frontend-developer` | T1-01 | 1 d |
| **T1-03** | CI pipeline — build, test, JaCoCo, gitleaks (L1), npm audit + SBOM (L2) | TEST-PLAN §5 | `backend-developer` | T1-01 | 1 d |
| **T1-04** | Request least-privilege DB role; verify `SELECT` on the 8 tables and `EXECUTE` on `decrypt()`/`masking()`; verify writes are rejected | ADR-007, CONST-DATA-02 | `backend-developer` + ops | — | 0.5 d |
| **T1-05** | **Obtain and archive `decrypt()` / `masking()` definitions** into the repo as reference documentation | RISK-010 | `data-model-designer` | T1-04 | 0.5 d |
| **T1-06** | Testcontainers PostgreSQL harness with the two functions and representative fixture data across all 8 tables | TEST-PLAN §1.3 | `qa-engineer` | T1-05 | 1.5 d |
| **T1-07** | `AuthenticationFilter` — mandatory, no per-endpoint opt-out | FR-MSG-001, FR-MSGD-001, NFR-SEC-AUTH | `backend-developer` | T1-01 | 1 d |
| **T1-08** | Minimal credential store with Argon2id hashing — **no MD5 path exists** | ADR-008, RISK-005 | `backend-developer` | T1-07 | 1 d |
| **T1-09** | Session handling — HttpOnly/Secure/SameSite cookie, server-side validation, rate limit on login | ADR-008, TM-001/003 | `backend-developer` | T1-08 | 1 d |
| **T1-10** | `TenantContextFilter` — resolve 이용기관 from session, bind to request scope, **ignore and audit any client-supplied tenant id** | FR-TEN-001, TM-004 | `backend-developer` | T1-09 | 1 d |
| **T1-11** | Role model — tenant vs operator; operator-only endpoint guard | FR-TEN-003/004, TM-011/014 | `backend-developer` | T1-10 | 0.5 d |
| **T1-12** | Audit store — append-only schema, no update/delete path | ADR-006, NFR-OPS-AUDIT | `data-model-designer` | T1-04 | 1 d |
| **T1-13** | `AuditLogAspect` — record per invocation, PII search terms hashed, DENIED outcomes recorded, independent transaction | ADR-002, ADR-006, TM-006 | `backend-developer` | T1-12 | 1.5 d |
| **T1-14** | `ServiceWindowInterceptor` — config-driven, inactive by default (`tmUseYn=N`) | NFR-OPS-TIME, BR-003 | `backend-developer` | T1-07 | 0.5 d |
| **T1-15** | `UsageQuotaInterceptor` — config-driven, unlimited by default (`maxUse=0`) | BR-004 | `backend-developer` | T1-07 | 0.5 d |
| **T1-16** | Global error handling — no stack traces or PII to the client, no PII in logs | NFR-SEC-LOG, TM-010 | `backend-developer` | T1-01 | 0.5 d |
| **T1-17** | Security tests: anonymous access rejected on every endpoint; supplied tenant id ignored; role enforcement | TC-MSG-001-11, -13 | `security-auditor` | T1-11 | 1 d |
| **T1-18** | Static-analysis rules — no MD5, no logging of `PHONE`/`CALLBACK`, tenant predicate present on message-table statements | RISK-003, RISK-005 | `security-auditor` | T1-03 | 1 d |
| **T1-19** | Coding standard: `// source:` provenance comments and the `-- FIX Dn:` SQL annotation convention | ADR-003, 추적성 dimension | `code-reviewer` | — | 0.5 d |
| **T1-20** | Sprint 1 log + 7-dimension self-assessment | DEV-PLAN §9 | `docs-writer` | all | 0.5 d |

**Estimated total: ~17 person-days.** At 1–2 developers over 10 working days this is tight-to-over — see §Capacity below.

## Definition of Done

- [ ] Application starts, connects to `BIZTALK_DB` with the least-privilege account
- [ ] **Every endpoint rejects anonymous access** — verified by test, not inspection
- [ ] **A client-supplied tenant id is ignored and the attempt audited** — verified by test
- [ ] Audit record written for every invocation, including denials
- [ ] No MD5 anywhere in the codebase or dependency tree
- [ ] Testcontainers integration harness runs against real PostgreSQL with `decrypt()`/`masking()`
- [ ] CI green: build, unit tests, coverage, gitleaks, dependency scan, SBOM
- [ ] `decrypt()`/`masking()` definitions archived in the repo
- [ ] 7-dimension self-assessment ≥ 90

## Capacity note

17 person-days against 10–20 available days is at or over the limit. If it slips, cut in this order:

1. **T1-14, T1-15** (window and quota interceptors) — both are dormant in this slice (`tmUseYn=N`, `maxUse=0`). Deferring them is the cheapest cut, but it re-opens RISK-002 and they must land before any screen that uses them
2. **T1-02** (React skeleton) — can move to Sprint 2 without blocking backend work

**Do not cut** T1-07 through T1-13, T1-17 or T1-18. Those are the authentication, tenant-isolation and audit controls this sprint exists to establish; every one of them corresponds to a confirmed legacy defect or an open threat.

## Risks specific to this sprint

| Risk | Mitigation |
|------|-----------|
| DB role provisioning depends on an external team (T1-04) and blocks T1-05, T1-06, T1-12 | Request on day 1; if delayed, develop against a local PostgreSQL with stub functions and reconcile later |
| `decrypt()`/`masking()` definitions may not be obtainable (RISK-010) | Escalate to PM immediately — without them, integration tests cannot reproduce production behavior and spec-parity claims weaken |
| Credential migration (RISK-005) has no plan yet | Sprint 1 needs only a minimal store; the migration plan is required before release, not before Sprint 2 |

---

**Agreement**

| Date | Role | Name | Status |
|------|------|------|--------|
| 2026-08-21 | PM | | ✅ **AGREED** — G2 결재와 동시 승인 / approved together with G2 |
| 2026-08-14 | Build Team Leader | `code-reviewer` | **미기록** — 아래 참조 / not recorded, see below |

> **Leader 합의 (VS-009).** Sprint 단위 합의는 표준상 **Leader 자율 처리 항목**이며(§게이트표: "Sprint 단위 결재는
> Leader 자율"), PM 게이트 결재로 대체되지 않는다. 이 행은 Sprint 1 실행 당시 기록되지 않았고, **스프린트가 이미 완료된
> 지금 소급 서명하는 것은 의미가 없다** — 합의는 착수 전 조정 장치이지 사후 증적이 아니다. 따라서 공란을 유지하고
> 누락 사실 자체를 기록한다. 향후 스프린트는 착수 시점에 기록한다.
>
> **Leader agreement (VS-009).** Sprint-level agreement is the Leader's own call under the standard and is not
> substituted by the PM's gate approval. It was never recorded for Sprint 1, and back-signing it now would be
> meaningless — the agreement is a coordination step before work starts, not an after-the-fact artifact. The blank is
> therefore kept and the omission recorded. Future sprints record it at kickoff.
