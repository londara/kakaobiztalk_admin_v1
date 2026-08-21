# 테스트계획서 — IRIS BizTalk Portal · 문자내역 slice

> **Version**: 1.0
> **Date**: 2026-08-14
> **Predecessors**: [REQUIREMENTS-SPEC.md](../requirements/REQUIREMENTS-SPEC.md), [DEV-PLAN.md](DEV-PLAN.md)
> **Status**: **APPROVED (G2)** — 2026-08-21, PM

---

## 1. Test strategy

### 1.1 Pyramid

```
        ┌────────────┐
        │   E2E 5%   │
       ┌┴────────────┴┐
       │  통합 25%    │
      ┌┴───────────────┴┐
      │   단위 70%      │
      └─────────────────┘
```

### 1.2 The defining constraint — no legacy to compare against

The project's KPI is parity, but there is **no runnable legacy environment** (RISK-001). Conventional parity testing — same input to both systems, compare output — is impossible. §7 defines what replaces it. Every other section of this plan assumes the new system's own tests are the primary evidence of correctness, which raises the bar on unit and integration coverage.

### 1.3 Test types

| Type | Tool | Owner | Frequency |
|------|------|-------|-----------|
| Unit | JUnit 5 + Mockito | `backend-developer` | Every commit |
| Integration | Testcontainers (PostgreSQL) + `@SpringBootTest` | `backend-developer` + `qa-engineer` | Every PR |
| E2E | Playwright | `qa-engineer` | Every sprint |
| Frontend component | Vitest + Testing Library | `frontend-developer` | Every commit |
| Accessibility | axe-core — WCAG 2.1 AA | `frontend-developer` → QA | Every PR |
| Load | k6 | `qa-engineer` | Sprint end |
| Security | OWASP ZAP, gitleaks, dependency scan | `security-auditor` | Every PR + L2 hook |
| **Spec parity** | Review against traceability matrix | `qa-engineer` + domain owner | Every sprint |

> **Testcontainers matters more than usual here.** The queries depend on `decrypt()` and `masking()`, PostgreSQL-specific `to_char`/`to_timestamp` semantics, and an 8-way UNION. An in-memory database would not exercise any of it. Integration tests must run against real PostgreSQL with those functions installed.

## 2. Coverage targets

| Metric | Target |
|--------|--------|
| Line | ≥ 80% |
| Branch | ≥ 70% |
| Method | ≥ 85% |
| Core domain (`MessageHistoryService`, mappers, cross-cutting filters) | ≥ 95% |

Tool: JaCoCo.

## 3. Defect regression suite — the centre of this plan

Nine legacy defects were confirmed and all nine are being fixed (AMB-01). Each needs a test that **fails against legacy behavior and passes against the new**, so the fix cannot silently regress.

| Defect | Test cases | Type | Assertion |
|--------|-----------|------|-----------|
| D1 — unauthenticated list service | TC-MSG-001-11 | Security | Anonymous call → 401, no data |
| D2 — MSGKEY search never matches | TC-MSG-001-05, -06 | Unit + integration | Multi- and single-digit keys return correct rows |
| D3 — swapped 발신/수신번호 labels | TC-MSG-001-07 | E2E | 발신번호 filters `CALLBACK` |
| D4 — dead PHONE / RESULT_CD searches | TC-MSG-001-08, -09 | Integration | Both narrow the result set |
| D5 — 5-digit year (`YYYYY`) | TC-MSG-002-05, -06 | Unit | 4-digit year on all 4 detail paths |
| D6 — inconsistent date boundary | TC-MSG-001-10 | Integration | Boundary row excluded in `KKO_MMS_MSG_LOG` as elsewhere |
| D7 — no pagination | TC-MSG-001-12 | Integration + load | 10,000 matches → one page + accurate total |
| D8 — time-of-day range validation | TC-MSG-001-02, -03 | Unit | Multi-day range with later start time accepted |
| D9 — 11 unpopulated detail fields | TC-MSG-002-07…10 | Integration | All 19 fields populated |

**16 of the 40 specified test cases exist solely to prove a defect is gone.** They are the most valuable tests in the suite and must not be deleted as "redundant" during later refactoring.

## 4. 7-dimension self-assessment

| Dimension | Weight | Criterion |
|-----------|--------|-----------|
| 완성도 | 20% | Sprint tasks 100% complete |
| 추적성 | 15% | `// source:` comments, ADR references, `-- FIX Dn:` annotations on ported SQL |
| 보안 | 20% | No hardcoded secrets, PII masked, hooks passing |
| 성능 | 10% | NFR-PERF SLA met |
| 가독성 | 15% | Javadoc, diagrams, formatting |
| 표준 준수 | 10% | Directory isolation, no missing ADR |
| 테스트 커버리지 | 10% | Targets in §2 met |

Threshold **90/100**. Below → improve the weakest dimension → regenerate, max 5 cycles → PM escalation.

## 5. Security testing (3-stage hook)

| Stage | Tools | Timing |
|-------|-------|--------|
| L1 | gitleaks | pre-commit |
| L2 | `security-auditor` + SAST + dependency scan + **SBOM / OSS licence** | CI / PR |
| L3 | prod-gate checklist | before production deploy |

> npm dependency scanning is **new scope introduced by ADR-001** (React). It did not exist for the legacy and is now an L2 requirement (TM-018).

### OWASP Top 10

| ID | Item | Verification | Slice-specific note |
|----|------|--------------|---------------------|
| A01 | Broken Access Control | E2E + static | **Highest priority** — D1 and the tenant retrofit both live here (TM-002, TM-008) |
| A02 | Cryptographic Failures | Security audit | MD5 elimination (ADR-008); DB-tier PII key (ADR-005) |
| A03 | Injection | SAST + unit | Named binds only (NFR-SEC-INJ) |
| A04 | Insecure Design | Architecture review + threat model | [threat-model.md](threat-model.md) |
| A05 | Security Misconfiguration | Config review | TLS, CORS, CSP |
| A06 | Vulnerable Components | Dependency scan + SBOM | Legacy carried log4j 1.2.9, POI 3.9 — not inherited |
| A07 | Auth Failures | E2E | ADR-008 |
| A08 | Data Integrity Failures | Review | ADR-004 |
| A09 | Logging & Monitoring | Audit review | ADR-006 |
| A10 | SSRF | Static analysis | No outbound calls in this slice |

## 6. Load testing

| Scenario | Load | Duration | Pass criteria |
|----------|------|----------|---------------|
| List search, typical range (1 day) | 20 concurrent users | 30 min | P95 < 3 s, error < 0.1% |
| List search, worst case (31 days) | 20 concurrent users | 15 min | P95 < 3 s (NFR-PERF-01) |
| List search, 2× peak | 40 concurrent users | 10 min | No pool exhaustion, graceful degradation |
| Detail lookup | 40 concurrent users | 15 min | P95 < 1 s (NFR-PERF-03) |
| Deep paging | page 100 of a 31-day result | — | Response within SLA — validates the `OFFSET` concern in ADR-003 |

> Load figures are modest because throughput is <10k messages/day (Q7a). The risk here is **query cost, not request volume** — one 31-day search over 8 unioned tables with per-row `decrypt()` is the expensive operation, so scenarios are shaped around query width rather than RPS.

## 7. Spec-parity testing (replaces byte-parity)

| Item | Content |
|------|---------|
| Definition | Conformance of the implementation to the specification **extracted from legacy source**, not to legacy runtime output |
| Why | No runnable legacy environment exists (RISK-001) — byte-level comparison is impossible |
| Method | Each of the 52 matrix rows is verified by its stated verification method; the ported SQL is reviewed line-by-line against the legacy IDO with every delta annotated |
| Sign-off | **Domain owner** confirms screen behavior against the specification, per screen group |
| Owner | `qa-engineer` + domain owner |
| Pass criteria | 100% of matrix rows verified; all 9 defect deviations explicitly approved |

> `scripts/parity-check.sh` in the harness assumes two runnable systems and **is not usable here**. This section replaces it, and the substitution is itself a deviation the PM should be aware of.

## 8. Test environments

| Env | Purpose | Data |
|-----|---------|------|
| local | Development | Testcontainers + synthetic fixtures |
| dev | Integration | Anonymised data with **synthetic phone numbers** |
| staging | QA / load | Production-like configuration, synthetic PII |
| prod | Live | Real data |

> **Real PII must never reach dev or staging.** This is sharper than the harness default here: the tables hold recipient phone numbers, and `decrypt()` exists in the database. A copied production schema plus the key would reproduce the entire PII set in a lower environment. Anonymisation must occur at extract time, not after load.

## 9. Defect management

| Grade | CVSS | Handling |
|-------|------|----------|
| CRITICAL | ≥ 9.0 | Fix within 4 hours |
| HIGH | 7.0–8.9 | Fix within the sprint |
| MEDIUM | 4.0–6.9 | By next sprint |
| LOW | < 4.0 | Backlog |

## 10. Deliverables

| Artifact | Timing | Format |
|----------|--------|--------|
| Unit test results | Every CI run | xml + html |
| Integration report | Every PR | md |
| Sprint QA report | Sprint end | md |
| Load test results | Sprint 3 | md + charts |
| Spec-parity report | Every sprint | md + csv |
| Security audit report | Skill 5 | md |
| Cross-validation report | Skill 5 | md |

## 11. Financial-sector additional tests

| Test | Purpose | Applies to this slice |
|------|---------|----------------------|
| PII masking | Response, log and export masking | **Yes** — core |
| Audit log | 5-year retention + integrity | **Yes** — ADR-006 |
| Access control | Tenant isolation | **Yes** — the critical one |
| Transaction integrity (HMAC) | Message tamper prevention | No — no message exchange (ADR-004 §7) |
| Idempotency | No duplicate processing | Trivially satisfied — read-only (ADR-009 §7) |
| Amount precision (BigDecimal) | Rounding | No — no monetary field in 문자내역. **Applies to screen 10 (수수료), not this slice** |
| Key rotation | Expiry / replacement | Partial — session key only; PII key is DB-tier (ADR-007) |

---

**G2 approval (design gate)**

| Date | Approver | Comment | Status |
|------|----------|---------|--------|
| 2026-08-21 | PM | 테스트계획 결재. §7 사양 패리티(spec-parity) 대체를 **명시적으로 수용** — 실행 가능한 레거시 환경이 없어 바이트 패리티는 측정 불가(PROJECT-PROPOSAL §8 KPI caveat 와 동일 근거) / Test plan approved; the §7 substitution of spec-parity for byte-parity is **explicitly accepted**, on the same grounds as the proposal's KPI caveat — no runnable legacy environment exists | ✅ **APPROVED** |
| 2026-08-14 | QA Leader | Byte-parity replaced by spec-parity (§7) — requires explicit PM acceptance | ✅ 조건 충족 — 위 PM 수용으로 해소 / condition met by the PM acceptance above |
