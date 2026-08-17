# ADR-SND-018 — Global uniqueness and lookup on an encrypted sender number

> **Status**: ACCEPTED (with a spike gating the final form)
> **Date**: 2026-08-17
> **Slice**: 발신번호 (screens 10/11/12/13)
> **Decides**: how FR-SNDC-004 is enforced; how FR-SND-007 row identity is carried
> **Requirements**: FR-SNDC-004, FR-SND-007, CONST-BIZ-D01, CONST-DATA-D02, NFR-PERF-D01
> **Related**: [ADR-005](ADR-005-pii-encryption.md), [ADR-SND-017](ADR-SND-017-senderno-lifecycle.md)

---

## Context

AMB-S03 ruled sender numbers **globally unique**. Under RESIDUAL-S01 (no ownership verification) this check is not a data-hygiene rule — it is the primary control preventing one institution from claiming another's number. It has to actually hold.

Enforcing it is harder than it looks, for three reasons.

**1. The column is encrypted.** `DP_NO` is stored as `ENCRYPT(:DP_NO)` (CONST-DATA-D02, key material in the database per ADR-005). Every legacy lookup is therefore:

```sql
WHERE decrypt(DP_NO) = :DP_NO
```

A function applied to the column makes the predicate unindexable — every lookup is a full scan, and no plain unique index on `DP_NO` means anything unless the encryption is deterministic.

**2. Whether `ENCRYPT` is deterministic is unknown from source.** Nothing in the analysed artifacts settles it. The legacy's choice of `decrypt(DP_NO) = :DP_NO` over the indexable `DP_NO = ENCRYPT(:DP_NO)` is weak evidence for non-determinism, but it is equally consistent with the author simply not considering it. This single fact decides the design, and it is cheap to establish.

**3. This application is not the only writer.** `AOA_ADMIN` carries the same four screens against the same `<target>BIZTALK_DB</target>`, writing the same `KKB_DPNO_LDGR`. **An application-level uniqueness check in our portal therefore cannot enforce a global constraint** — `AOA_ADMIN` will keep inserting duplicates through a code path we do not own. Only a database constraint binds every writer.

That third point is decisive and rules out the otherwise attractive no-DDL option.

## Decision

**Uniqueness is enforced by a database constraint, not by an application check.** The application check remains as a means of returning a clean validation error rather than a constraint-violation stack trace, but it is not the enforcement.

The constraint's form is settled by **spike S1-01**, which establishes whether `ENCRYPT` is deterministic:

| Spike result | Form |
|--------------|------|
| **Deterministic** | `CREATE UNIQUE INDEX … ON KKB_DPNO_LDGR (DP_NO)`. No new column. Lookups rewrite to `DP_NO = ENCRYPT(:DP_NO)`, which is indexable — this also removes the full scan from every lookup in the slice |
| **Non-deterministic** | Add a blind-index column `DP_NO_IDX` = HMAC-SHA256(number, key held with the application, not the DB) with a unique index on it. Lookups and uniqueness both go through `DP_NO_IDX`; `DP_NO` remains the encrypted value of record |

Both forms are additive DDL consistent with ADR-SND-017. Neither changes what existing readers see: a legacy `select dp_no … where decrypt(dp_no) = :dp_no` continues to work untouched in both cases.

**Row identity (FR-SND-007)** uses the same mechanism. The API exposes `institutionCode` + an opaque number reference — the deterministic ciphertext or the blind index, never the display string. This is structural, not incidental: **D-S1 happened precisely because a display-formatted value was used as an identifier.** Once identity and display are different values, masking policy can change without breaking anything downstream, which is the property the legacy lacked.

## Alternatives considered

| Option | Enforcement | Verdict |
|--------|-------------|---------|
| **A — application check only** (`SELECT … WHERE decrypt(DP_NO) = :n`, no `IS_CD` filter) | None. Racy between concurrent registrations, and **bypassed entirely by `AOA_ADMIN`** | **Rejected.** Cannot enforce a constraint the PM ruled must hold globally |
| **B — application check + `pg_advisory_xact_lock`** | Closes the race within our application only; `AOA_ADMIN` still bypasses it | Rejected for the same reason. Worth noting it *would* be the right answer if ours were the only writer, and it needs no DDL |
| **C — DB unique constraint (chosen)** | Binds every writer including `AOA_ADMIN` | **Accepted** |
| **D — decrypt the column and store in clear** | Trivial uniqueness and indexing | Rejected. Violates CONST-DATA-D02 and ADR-005 |

## Consequences

**Positive.**
- Uniqueness holds against every writer, which is what CONST-BIZ-D01 actually requires.
- If the spike returns "deterministic", indexable lookups remove the slice's only unbounded scan and NFR-PERF-D01 stops depending on table size.
- Display and identity become separate values, closing the D-S1 defect class structurally.

**Negative — and this one needs an operational decision before the migration runs.**
- **`AOA_ADMIN` registrations that previously succeeded will start failing** once the constraint exists, wherever they would create a cross-institution duplicate. This is the constraint working as ruled, but it is a behaviour change in an application outside this project, reaching users who were never told. Tracked as **RISK-S05**; the operator team must be notified before the index is created.
- **Existing duplicates block index creation.** `CREATE UNIQUE INDEX` fails if the data already violates it. The reconciliation is a migration prerequisite, not a follow-up — task S2-01.
- The non-deterministic branch introduces a second key (the HMAC key) with its own management obligation under ADR-007, and a backfill over every existing row.

**Unknown until the spike.** Which branch applies. The spike is scheduled first in Sprint S1 for that reason; roughly a day of work separates two designs that differ in cost by considerably more.

## Verification

| Check | Test |
|-------|------|
| Cross-institution duplicate rejected | TC-S002-07 |
| Same-institution duplicate rejected | TC-S002-08 |
| Concurrent registration of the same number | New: two simultaneous requests, exactly one succeeds |
| Constraint binds a writer outside the application | New: direct SQL insert of a duplicate fails |
| Identity is not the display string | TC-S001-14, TC-S004-03 |
| Lookup performance at production volume | NFR-PERF-D01 load test |
