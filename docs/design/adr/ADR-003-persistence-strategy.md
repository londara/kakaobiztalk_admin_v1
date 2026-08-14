# ADR-003: Persistence strategy

> **Status**: ACCEPTED
> **Date**: 2026-08-14
> **Author**: `architect` (Skill 03)
> **Approver**: PM
> **Related**: ADR-001, ADR-002, ADR-005

---

## 1. Context

The 문자내역 queries are the most complex SQL in the slice: an 8-way `UNION ALL` across live and archive tables, with database functions `decrypt()` and `masking()` applied per column, and conditional filters expressed as `CASE WHEN :param = '' THEN 1=1 ELSE …`.

Critically, **this SQL is the specification** — there is no runnable legacy environment and no documentation (RISK-001). Any rewrite of the queries is a rewrite of the requirements.

- **Requirements**: FR-MSG-003/007/008/011/015/016, FR-MSGD-002, CONST-DATA-01/02/03
- **Risks**: RISK-001, RISK-003

## 2. Decision

> **MyBatis** with the legacy SQL carried across in near-original form. Corrections are applied as **explicit, individually traceable edits** to the ported SQL, never as a rewrite. Tenant scoping is injected as a mandatory SQL predicate.

### Key choices
- One mapper interface per feature (`MessageHistoryMapper`) with XML statements mirroring the legacy IDO structure
- Each corrected line carries a comment referencing its defect ID (`-- FIX D6: was BETWEEN`), so a reviewer can diff intent against the legacy
- **Tenant predicate is added in SQL**, not applied to the result set in Java
- Paging via `LIMIT`/`OFFSET` plus a separate `COUNT` query over the same union
- `decrypt()` / `masking()` remain database-side (CONST-DATA-02, ADR-005)

## 3. Considered alternatives

| # | Alternative | Advantages | Disadvantages | Adopted |
|---|-------------|-----------|---------------|---------|
| A | MyBatis, SQL ported near-verbatim | Preserves the only surviving specification; corrections auditable line by line; handles UNION and DB functions naturally | Hand-written result maps; XML to maintain | **Adopted** |
| B | JPA / Hibernate with entities | Idiomatic Spring; compile-time type safety | Cannot express the 8-way union with DB function calls without native queries anyway — so it delivers its benefits nowhere it matters, while forcing a full rewrite of the specification | Not adopted |
| C | Spring Data JDBC / JdbcTemplate | No XML; explicit SQL | Same rewrite risk as B with fewer mapping facilities than A | Not adopted |
| D | Database views encapsulating the union | Simplifies application SQL | Requires DDL on a shared legacy database — violates CONST-DATA-01 | Not adopted |

## 4. Consequences

### 4.1 Positive
- The diff between legacy SQL and new SQL is small and reviewable — the single most effective control against RISK-001
- The 8-way union and DB functions work exactly as before; no behavioral surprises from an ORM's query generation
- Defect fixes are visible as annotated deltas rather than being lost inside a rewrite

### 4.2 Negative
- Hand-maintained result maps and DTOs
- The ported SQL carries the legacy's structural inefficiency (union-then-filter). Acceptable at <10k/day, but see NFR-PERF-04
- MyBatis XML is less discoverable to developers new to the codebase than annotated entities

### 4.3 Follow-up
- [ ] Establish the `-- FIX Dn:` comment convention in the Sprint 1 coding standard
- [ ] Review the paging query plan — `OFFSET` over a large union degrades on deep pages (NFR-PERF-04)
- [ ] Confirm the least-privilege DB account can execute `decrypt()` (ADR-007)

## 5. Verification

| Item | Method | Frequency | Threshold |
|------|--------|-----------|-----------|
| Ported SQL vs legacy IDO | Line-by-line review against `IDO.KKB_MSG_L002` | Sprint 1 | Every delta annotated with a defect ID or requirement |
| Tenant predicate present | Static check on every mapper statement | Every PR | 100% of statements touching message tables |
| Query plan | `EXPLAIN ANALYZE` on the 31-day worst case | Sprint end | Within NFR-PERF-01 |

## 6. References

- Legacy: `IDO.KKB_MSG_L002.xml`, `IDO.KKO_SMS_MSG_L001.xml`, `IDO.KKO_MMS_MSG_L001.xml`, `IDO.KKF_SMS_MSG_L001.xml`, `IDO.KKF_MMS_MSG_L001.xml`
- Requirements: FR-MSG-003/007/008/011, CONST-DATA-01/02/03 · Threats: TM-005, TM-008

---

## Change history

| Date | Version | Change | Author |
|------|---------|--------|--------|
| 2026-08-14 | 1.0 | Initial | `architect` |

---

**Approval**

| Date | Approver | Comment | Status |
|------|----------|---------|--------|
| 2026-08-14 | PM | | PENDING (G2) |
