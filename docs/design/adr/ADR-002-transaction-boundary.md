# ADR-002: Transaction boundary model

> **Status**: ACCEPTED (scoped — see §7)
> **Date**: 2026-08-14
> **Author**: `architect` (Skill 03)
> **Approver**: PM
> **Related**: ADR-003, ADR-006

---

## 1. Context

Harness §8 requires a transaction-boundary ADR for financial-domain systems. The 문자내역 slice is **entirely read-only** — both services carry `trTp=U` in their WSVC contracts, but neither writes business data. The only write in the slice is the audit record.

- **Requirements**: FR-MSG-003/007, FR-MSGD-002, NFR-OPS-AUDIT
- **Constraint**: the database is shared with the still-running legacy (CONST-DATA-01)

## 2. Decision

> Business queries run in **read-only transactions** at the service layer. The audit write runs in a **separate transaction that commits independently** of the query outcome. No distributed or compensating transactions exist in this slice.

### Key choices
- `@Transactional(readOnly = true)` on `MessageHistoryService` methods — lets the driver and PostgreSQL optimise, and makes accidental writes fail loudly
- Audit records written in `REQUIRES_NEW` so that a failed or rolled-back query still leaves evidence that it was attempted
- Transaction boundary at the **service** layer, never the controller or mapper
- No cross-resource transaction — the audit store is written through its own path (ADR-006)

## 3. Considered alternatives

| # | Alternative | Advantages | Disadvantages | Adopted |
|---|-------------|-----------|---------------|---------|
| A | Read-only tx + independent audit tx | Audit survives query failure; simple; no 2PC | Audit and query can theoretically diverge (audit says attempted, query rolled back) — which is the desired semantics | **Adopted** |
| B | Single transaction spanning query + audit | Perfectly consistent pair | A failed query erases its own audit trail — unacceptable under 전자금융감독규정 | Not adopted |
| C | No explicit transactions (autocommit) | Simplest | Multi-statement detail lookups lose consistency; no read-only optimisation; accidental writes silent | Not adopted |

## 4. Consequences

### 4.1 Positive
- An attempted cross-tenant read is recorded even if the query itself fails or returns nothing (supports TM-006, TM-008)
- Read-only marking turns any accidental write into an immediate error rather than silent corruption of a shared database

### 4.2 Negative
- Two transactions per request — negligible at this volume (<10k messages/day), but real
- The "audit says attempted, no rows returned" state must be understood by anyone reading the audit trail; documented in ADR-006

### 4.3 Follow-up
- [ ] Confirm the audit store's transactional semantics once its technology is fixed (ADR-006)

## 5. Verification

| Item | Method | Frequency | Threshold |
|------|--------|-----------|-----------|
| No writes on business tables from the new app | Integration test with a read-only DB role | Every PR | 0 write attempts |
| Audit record present after a failed query | Integration test (forced failure) | Every PR | 100% |

## 6. References

- Requirements: FR-MSG-003, NFR-OPS-AUDIT · Threats: TM-006
- Legacy: `WSVC.biztalk_admin_40_l001.xml` (`trTp=U`), `biztalk_admin_40_l001_act.jsp`

## 7. Scope limitation

**This ADR covers the read-only 문자내역 slice only.** The send path (screen 50), template management, and the daily aggregation batch introduce genuine write transactions, provider calls inside or outside transaction scope, and possible compensating actions. Those require this ADR to be **superseded, not extended**, before that work begins.

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
