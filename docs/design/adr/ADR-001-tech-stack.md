# ADR-001: Technology stack for the IRIS BizTalk Portal

> **Status**: ACCEPTED
> **Date**: 2026-08-14
> **Author**: `architect` (Skill 03)
> **Approver**: PM
> **Related ADR**: ADR-003 (persistence), ADR-005 (PII), ADR-006 (audit)

---

## 1. Context

The 문자내역 slice (screens 40/41) is the first feature of the IRIS BizTalk Portal, replacing the legacy Jex/JSP implementation. The stack chosen here governs the whole application, not just this slice, so it is decided once and revisited only by superseding ADR.

- **Requirements**: CONST-TECH-01, CONST-DATA-01, CONST-DATA-02, NFR-PERF-01/03, NFR-SEC-*
- **Related risks**: RISK-002 (Jex runtime behavior loss), RISK-004 (scope vs capacity), RISK-006 (망분리)
- **Decision pressure**: 3–6 month schedule with 1–2 developers; four regulatory regimes; internet-facing multi-tenant exposure
- **Prior input**: PM selected Spring Boot 3 + React + MyBatis at Skill 01 Q11, recorded as CONST-TECH-01

The backend framework was not genuinely contested — Spring Boot 3 is the only mainstream option satisfying "discard Jex, staff it easily, Java 17+". The real choices were **frontend rendering strategy** and **persistence approach**.

## 2. Decision

> Java 17 + **Spring Boot 3.x** backend, **React SPA over REST**, **MyBatis** against the existing PostgreSQL `BIZTALK_DB` schema. Build with Maven; deploy backend and frontend as separate artifacts behind one reverse proxy.

### Key choices
- **React SPA + REST API** — adopted by PM decision, overriding the weighted score (see §3)
- **MyBatis retained** — the legacy SQL is the only surviving specification; re-expressing it in JPA would risk the very behavior we cannot otherwise verify
- **PostgreSQL schema reused unchanged** — no DDL migration in this scope (CONST-DATA-01)
- **DB-side `decrypt()` / `masking()` retained** — key material and masking policy stay in the database (CONST-DATA-02, ADR-005)

## 3. Considered alternatives

| # | Alternative | Advantages | Disadvantages | Adopted |
|---|-------------|-----------|---------------|---------|
| A | Spring Boot 3 + **React SPA** + MyBatis | Fits tenant self-service direction; strong ecosystem; clean API boundary reusable by future mobile/partner clients | Second discipline for a 1–2 person team; second build/deploy pipeline; token handling and XSS surface on an internet-facing app; npm supply chain | **Adopted (PM override)** |
| B | Spring Boot 3 + **Thymeleaf SSR** + MyBatis | Closest to the JSP model being replaced — lowest learning cost; one artifact, one deployment; CSRF and session handling built in; smallest attack surface | Less modern; server-coupled rendering; heavier interactivity costs more effort; no reusable API for future clients | Not adopted — **highest score** |
| C | Spring Boot 3 + React + **JPA/Hibernate** | Most idiomatic Spring Boot; better for greenfield domain modelling | Every legacy query must be re-expressed — unacceptable risk against a source-only specification (RISK-001); poor fit for the 8-way UNION and DB function calls | Not adopted |

### Weighted scoring (harness §2 dimensions)

| Dimension | Weight | A (React) | B (Thymeleaf) | C (React+JPA) |
|-----------|--------|-----------|---------------|---------------|
| 팀 숙련도 | 25% | 5 | 8 | 4 |
| 생태계 / 커뮤니티 | 15% | 9 | 7 | 9 |
| 라이선스 / 비용 | 15% | 10 | 10 | 10 |
| 성능 (vs NFR-PERF) | 15% | 8 | 8 | 6 |
| 보안 (취약점 이력) | 15% | 7 | 8 | 7 |
| 운영 / 모니터링 | 15% | 7 | 9 | 7 |
| **Weighted total** | | **7.40** | **8.30** | **6.85** |

**Scoring rationale.** 팀 숙련도 carries the heaviest weight (25%) and is where the options diverge most: the team is 1–2 developers whose background is the JSP/jQuery codebase being replaced. Thymeleaf also leads on operations (single artifact, single pipeline) and marginally on security (no browser-side token custody, framework-default CSRF).

### Deviation from the score — PM ruling

Option B scored highest by **0.90 points (10.8%)**, outside the harness's <10% mandatory tie-break band. Procedurally, B would be adopted.

**The PM ruled to adopt A (React).** The stated basis is strategic: the portal's purpose is the shift to client self-service, and a REST API with an SPA client is the shape that supports future partner and mobile access, whereas Thymeleaf optimises for a team composition that is expected to change.

This is recorded as an **accepted deviation, not an error** — the harness requires the score be documented, not that it be obeyed. The costs the score was signalling are real and are carried forward as RISK-011 and RISK-012 in the risk register.

## 4. Consequences

### 4.1 Positive
- Clean API boundary — the same REST surface serves the SPA, and later any partner or mobile client, without rework
- Frontend and backend deploy independently; UI changes do not require a backend release
- React's ecosystem supplies mature data-grid components to replace the proprietary Jex grid
- MyBatis keeps the legacy SQL close to its original form, which is the strongest available defence against RISK-001

### 4.2 Negative / costs accepted
- **Two disciplines for 1–2 developers.** Directly compounds RISK-004 (scope vs capacity). Frontend and backend work cannot proceed in parallel with this headcount
- **Larger attack surface.** Browser-side token custody, CORS configuration, and client-side XSS become live concerns on an internet-facing app; none existed in the server-rendered legacy
- **npm supply chain** enters the dependency and SBOM scope (TEST-PLAN §5, L2 hook)
- **Two build pipelines** to establish and maintain
- MyBatis means no automatic schema mapping — DTOs and result maps are written by hand

### 4.3 Follow-up
- [ ] Select a React data-grid library before Sprint 1 (must support server-side paging per FR-MSG-007)
- [ ] Decide token strategy — HttpOnly session cookie vs. JWT (→ ADR-008)
- [ ] Establish the frontend build pipeline and npm dependency scanning in CI
- [ ] Confirm reverse-proxy topology against the 망분리 constraint (RISK-006, → G2)

## 5. Verification / monitoring

| Item | Method | Frequency | Threshold |
|------|--------|-----------|-----------|
| List query response | Load test | Sprint end | P95 < 3 s (NFR-PERF-01) |
| Detail query response | Load test | Sprint end | P95 < 1 s (NFR-PERF-03) |
| Frontend dependency vulnerabilities | npm audit + SBOM in CI | Every PR | 0 High/Critical |
| Frontend delivery velocity vs plan | Sprint review | Sprint end | If Sprint 1 frontend tasks slip > 50%, re-open this ADR |

> The last row is deliberate: it is the objective trigger for revisiting the override, rather than leaving it to impression.

## 6. References

- Requirements: CONST-TECH-01, CONST-DATA-01/02/03, NFR-PERF-01/03
- Proposal §11 (stack preference), Skill 01 Q11 (PM selection)
- Risks: RISK-001, RISK-002, RISK-004, RISK-011, RISK-012
- Legacy evidence: `IDO.KKB_MSG_L002.xml` (8-way UNION with DB functions — the query that makes JPA unsuitable)

---

## Change history

| Date | Version | Change | Author |
|------|---------|--------|--------|
| 2026-08-14 | 1.0 | Initial — evaluation of 3 candidates, PM override of the score recorded | `architect` |

---

**Approval**

| Date | Approver | Comment | Status |
|------|----------|---------|--------|
| 2026-08-14 | PM | React adopted over the higher-scoring Thymeleaf option; deviation accepted on strategic grounds | **APPROVED** |
