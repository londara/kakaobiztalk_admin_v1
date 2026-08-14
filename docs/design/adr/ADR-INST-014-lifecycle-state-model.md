# ADR-INST-014: Institution lifecycle state model and logical delete

> **Status**: ACCEPTED
> **Date**: 2026-08-14
> **Deciders**: PM (AMB-I03, Skill 3 Q2), `architect`
> **Slice**: 이용기관관리
> **Supersedes**: nothing · **Related**: [ADR-003](ADR-003-persistence-strategy.md), [ADR-006](ADR-006-audit-logging.md), [ADR-INST-016](ADR-INST-016-legacy-coexistence.md)

---

## 1. Context

The legacy 삭제 path physically removes the institution row (`DELETE FROM FT_FTIS_INFO`) behind a "복구할 수 없습니다" warning, and separately deletes every 발신번호. Sender numbers leave a history record in `KKB_DPNO_HIS`; the institution deletion leaves none. 문자발송내역 rows keyed on `IS_CD` become orphaned — still present, no longer resolvable to an institution name.

PM ruled (AMB-I03) for **logical delete with history**.

The requirements spec initially assumed this needed new schema — a delete flag plus a deletion history table — and raised **CONFLICT-I02**, because [CONST-DATA-01](../../requirements/REQUIREMENTS-SPEC.md) constrains the programme to "the existing `BIZTALK_DB` schema is reused unchanged; no DDL migration in this scope".

Two facts, established during design, make that assumption wrong:

1. `FT_FTIS_INFO` **already has a status column**, `IS_STTS`, currently holding `'Y'` / `'N'`. It is a status column, not a boolean — the legacy list query compares it with `IS_STTS = :USE_YN`, so it already tolerates values beyond two.
2. A **DB-backed audit store already exists**, delivered by the 로그인 slice: `AuditService` writing `AuditEvent(occurredAt, actor, targetAccount, action, outcome, detail, sourceIp, correlationId)` through MyBatis. It is generic — nothing about it is authentication-specific.

Three constraints shape the decision:

- **The table is shared.** The legacy IRIS runtime reads `FT_FTIS_INFO` concurrently (send path, `FINInstitution` cache, AOA lookups) and will not be changed in this project (ADR-INST-016). Whatever we write must degrade safely for a reader that has never heard of it.
- **`IS_CD` must stay resolvable.** 문자발송내역, 발신번호 and 수수료 all key on it. A deleted institution's name must still be lookupable, or the history this decision exists to protect becomes unreadable anyway.
- **Deletion must be attributable** — actor, timestamp, before/after (FR-AZ-I04, FR-INSTL-004).

## 2. Decision

**Model the lifecycle as a single state on the existing `IS_STTS` column, and record transitions as audit events.**

| State | `IS_STTS` | Meaning | Appears in default list | Send API access |
|-------|-----------|---------|-------------------------|-----------------|
| 사용 | `'Y'` | Active | Yes | Permitted |
| 미사용 | `'N'` | Suspended, recoverable via 재사용 | Yes, shown as 미사용 | Denied |
| 삭제 | `'D'` | Logically deleted | No | Denied |

- **No DDL.** No column is added, no table is created, no row is removed.
- **Deletion history** is an `AuditEvent` with `action = "institution.delete"`, carrying the actor, the target `IS_CD`, and the prior state in `detail`. Status changes use `institution.disable` / `institution.enable` on the same mechanism.
- **The row is retained**, so `IS_CD` stays resolvable for 문자발송내역 and every other dependent lookup.
- **발신번호 are deactivated, not deleted**, and continue to write `KKB_DPNO_HIS` entries with `ACN='D'` exactly as the legacy did — that behaviour was correct and is preserved.
- **All of it in one transaction** (FR-INSTL-006), fixing the legacy's D-I8 where a failed history insert was tested against the wrong result object and committed anyway.

**CONFLICT-I02 is dissolved rather than resolved.** CONST-DATA-01 stands unmodified, and no precedent for programme-wide schema change is set.

### 2.1 Why `'D'` is safe for legacy readers

The legacy list query is:

```sql
WHERE CASE WHEN :USE_YN = 'ALL' THEN 1=1 ELSE IS_STTS = :USE_YN END
```

A `'D'` row matches neither `'Y'` nor `'N'`, so it drops out of both legacy filters naturally — which is the desired behaviour. It *does* still appear under `USE_YN='ALL'`, which the legacy 문자내역 screen used to populate its institution dropdown. That path is already replaced by `InstitutionController`, which filters explicitly.

This is a genuine advantage over an added `DEL_YN` column: a new column would be **invisible** to every legacy reader, so a deleted institution would remain fully active to the legacy send path. Reusing the status column means the legacy fails safe by construction rather than by remembering to check.

## 3. Considered alternatives

| # | Option | DDL | Legacy readers | Attributable | Verdict |
|---|--------|-----|----------------|--------------|---------|
| **A** | **`IS_STTS='D'` + existing `AuditService`** | **None** | **Fail safe — `'D'` matches neither filter** | Yes, via audit store | **SELECTED** |
| B | Add `DEL_YN` / `DEL_DT` columns + dedicated history table | Yes — 2 columns, 1 table | **Unsafe — legacy cannot see the flag, deleted institutions stay active** | Yes, self-documenting | Rejected |
| C | Move deleted rows to an `FT_FTIS_INFO_ARCH` archive table | Yes — 1 table | Safe (row is gone) | Yes | Rejected |
| D | Keep physical delete, add history only | Yes — 1 table | Safe | Partly | Rejected — contradicts AMB-I03 |

**Why not B.** It is the most self-documenting option and would be the obvious choice on a table we owned exclusively. We do not own it. A flag the legacy cannot see produces exactly the failure mode this slice exists to fix: D-I1, where an operator disabled an institution and the institution kept working. Repeating that shape for deletion would be a worse defect than the one being repaired, because deletion is the more consequential intent.

**Why not C.** It satisfies audit and keeps the master table clean, but it breaks `IS_CD` resolvability — the archived row is no longer where every dependent lookup expects it. 문자발송내역 for a deleted institution would show a code with no name, which is the orphaning problem restated rather than solved. It also needs DDL for no compensating benefit over A.

**Why not D.** Rejected by the PM ruling; recorded for completeness.

### 3.1 Trade-off accepted

Option A overloads a column whose name (`IS_STTS`, "institution status") happens to fit, but whose existing domain was binary in practice. A reader encountering `'D'` without this ADR may assume corrupt data. Mitigated by: a `InstitutionStatus` enum as the only write path, a comment on the mapper XML pointing here, and the value being self-evidently mnemonic.

The alternative — a correctly-named new column that the legacy silently ignores — trades a documentation problem for a correctness problem. That is the wrong trade.

## 4. Consequences

**Positive**

- Zero schema change; CONST-DATA-01 preserved intact and CONFLICT-I02 removed from the G1 gate.
- Legacy readers degrade safely without being modified.
- Deletion history reuses proven, already-tested audit infrastructure, inheriting its retention policy (ADR-006) at no extra cost.
- `IS_CD` stays resolvable, so 문자발송내역 remains attributable — the actual point of AMB-I03.

**Negative**

- `IS_STTS` becomes a three-valued domain that no schema comment declares. Anyone reading the DDL alone will not learn `'D'` exists.
- Deletion metadata (who, when, why) lives in the audit store rather than beside the row, so answering "when was this deleted" is a join across stores rather than a column read.
- If a future requirement needs *both* "suspended" and "deleted" simultaneously, a single column cannot express it. No current requirement does.

**Neutral**

- The legacy `KKB_FT_FTIS_INFO_D001` physical-delete IDO is **not ported**. It is superseded, not translated.

## 5. Verification / monitoring

| Check | Method | Requirement |
|-------|--------|-------------|
| Deleted institution absent from the default list | Integration test | FR-INSTL-005 |
| `IS_CD` still resolvable to a name after deletion | Integration test | FR-INSTL-004 |
| Deletion writes an audit event with actor and prior state | Integration test | FR-AZ-I04 |
| Whole delete rolls back on any sub-operation failure | Integration test, forced failure | FR-INSTL-006 |
| 발신번호 deactivated with `KKB_DPNO_HIS` `ACN='D'` retained | Integration test | FR-INSTL-005 |
| No `ALTER`/`CREATE` statement in any migration for this slice | CI check | CONST-DATA-01 |
| `IS_STTS` written only through the enum | Static analysis | — |

## 6. References

- [REQUIREMENTS-SPEC-INSTITUTION.md](../../requirements/REQUIREMENTS-SPEC-INSTITUTION.md) §2.4, §6.2 — AMB-I03, CONFLICT-I02
- [questions-log.md](../../requirements/questions-log.md) Part 3 §9–10
- Legacy: `IDO.KKB_FT_FTIS_INFO_D001`, `IDO.KKB_DPNO_LDGR_D002`, `IDO.KKB_DPNO_HIS_C001`, `biztalk_admin_00_d001_act.jsp`
- [ADR-006](ADR-006-audit-logging.md) — audit retention this decision inherits

## Change history

| Date | Version | Change | Author |
|------|---------|--------|--------|
| 2026-08-14 | 1.0 | Initial — selected option A, dissolving CONFLICT-I02 | `architect` |

---

**Approval**

| Date | Approver | Comment | Status |
|------|----------|---------|--------|
| 2026-08-14 | PM | Option A selected at Skill 3 | **ACCEPTED** |
| 2026-08-14 | Architect | No DDL required; CONST-DATA-01 intact | **ACCEPTED** |
