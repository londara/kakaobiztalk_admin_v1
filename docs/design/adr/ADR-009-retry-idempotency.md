# ADR-009: Retry, idempotency and resource protection

> **Status**: ACCEPTED (scoped — see §7)
> **Date**: 2026-08-14
> **Author**: `architect` (Skill 03)
> **Approver**: PM
> **Related**: ADR-002, ADR-008

---

## 1. Context

Harness §8 requires a retry/idempotency ADR. In a read-only slice, idempotency is **inherent** — repeating a search changes nothing. What genuinely needs a decision is **resource protection**: the legacy query unions 8 tables (live plus archive), applies `decrypt()` per row, and ran with **no pagination and no date-range limit** (defect D7). A single wide search could consume the connection pool for every tenant.

- **Requirements**: FR-MSG-007, FR-MSG-013, NFR-PERF-01/02/04
- **Threats**: TM-012 (DoS via wide range), TM-013 (pool exhaustion)

## 2. Decision

> Queries are naturally idempotent and are **not retried automatically** on failure. Resource protection is provided by a **31-day range cap, server-side pagination, query timeouts, and per-session rate limiting**.

### Key choices
- **No automatic retry** on query failure — a failed read is surfaced to the user, who may retry deliberately. Automatic retry on a heavy query is an amplification vector, not a resilience measure
- **31-day range cap** (FR-MSG-013), rejected client-side and re-validated server-side
- **Server-side paging**, default 50 / max 500 rows (FR-MSG-007, NFR-PERF-02)
- **Statement timeout** on the data source; a query exceeding it is cancelled rather than left to occupy a connection
- **Rate limiting** per session on the search endpoint
- Bounded connection pool so one tenant's load cannot starve others

## 3. Considered alternatives

| # | Alternative | Advantages | Disadvantages | Adopted |
|---|-------------|-----------|---------------|---------|
| A | No retry + caps, paging, timeout, rate limit | Bounded worst case; no amplification; simple | A transient DB blip surfaces to the user | **Adopted** |
| B | Automatic retry with backoff on query failure | Hides transient failures | Retrying the most expensive query in the system multiplies load exactly when it is already failing | Not adopted |
| C | Caching of recent result sets | Reduces repeat load | Caching PII-bearing, tenant-scoped results adds a disclosure surface for modest benefit at this volume | Not adopted |

## 4. Consequences

### 4.1 Positive
- Worst-case query cost is bounded and calculable — the precondition for a meaningful load test (NFR-PERF-01)
- Removes the legacy's unbounded-query exposure (TM-012)

### 4.2 Negative
- Users needing more than 31 days must run several searches; if this proves painful in practice, the answer is an export feature (AMB-07), not a wider cap
- Transient database failures are user-visible

### 4.3 Follow-up
- [ ] Set the statement timeout value from the Sprint 1 load test, not from a guess
- [ ] Confirm rate-limit thresholds against real operator behaviour (operators query far more than tenants)

## 5. Verification

| Item | Method | Frequency | Threshold |
|------|--------|-----------|-----------|
| Range cap enforced server-side | Integration test bypassing the client | Every PR | 32-day request rejected |
| Page size cap | Integration test requesting 10,000 rows | Every PR | Capped at 500 |
| Timeout cancels long queries | Load test with a wide range | Sprint end | Connection released |
| Repeat search identical | Integration test | Every PR | Same result, no side effects |

## 6. References

- Requirements: FR-MSG-007/013, NFR-PERF-01/02/04 · Threats: TM-012, TM-013
- Legacy: `biztalk_admin_40_l001_act.jsp` (paging commented out), `IDO.KKB_MSG_L002.xml`

## 7. Scope limitation

**Idempotency in the meaningful sense — not sending the same message twice — belongs to the send path (screen 50), which is not in this slice.** That will require an idempotency key, duplicate-suppression, and a retry policy against the provider API, and must **supersede this ADR**.

---

## Change history

| Date | Version | Change | Author |
|------|---------|--------|--------|
| 2026-08-14 | 1.0 | Initial — read-only slice | `architect` |

---

**Approval**

| Date | Approver | Comment | Status |
|------|----------|---------|--------|
| 2026-08-14 | PM | | PENDING (G2) |
