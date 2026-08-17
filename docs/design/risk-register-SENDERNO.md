# Risk Register — 발신번호 (Sender Number Management)

> **Version**: 1.0
> **Date**: 2026-08-17
> **Predecessor**: [DEV-PLAN-SENDERNO.md](DEV-PLAN-SENDERNO.md)
> **Siblings**: [risk-register.md](risk-register.md), [risk-register-LOGIN.md](risk-register-LOGIN.md), [risk-register-INSTITUTION.md](risk-register-INSTITUTION.md)
> **Count**: 13 (harness minimum 10)

---

## Summary

| ID | Title | Area | Impact | Prob. | Strategy |
|----|-------|------|--------|-------|----------|
| RISK-S01 | `BIZ_DB` and `BIZTALK_DB` may be different databases | 기술 | **H** | L | 회피 |
| RISK-S02 | Existing cross-institution duplicates block the unique index | 기술 | H | **H** | 완화 |
| RISK-S03 | One send path validates no sender number | 외부 | **H** | **H** | 수용 + 추적 |
| RISK-S04 | Numbers believed deleted are still live and sendable | 운영 | **H** | **H** | 완화 |
| RISK-S05 | `AOA_ADMIN` keeps the defective screens on the same data | 외부 | H | **H** | 수용 + 추적 |
| RISK-S06 | Registration without ownership proof (accepted) | 보안 | **H** | M | 수용 |
| RISK-S07 | `ENCRYPT` is non-deterministic, forcing a second key and a backfill | 기술 | M | M | 완화 |
| RISK-S08 | Uniqueness check is a full scan at production volume | 기술 | M | M | 완화 |
| RISK-S09 | Read auditing volume outruns an undecided retention policy | 운영 | M | M | 완화 |
| RISK-S10 | Staging lacks `KAKAOTALK`/`AOA_ADMIN`, blocking the coexistence suite | 일정 | M | M | 완화 |
| RISK-S11 | The special-number list is unspecified | 외부 | M | M | 완화 |
| RISK-S12 | G1 not approved while S2 commits to schema change | 일정 | M | L | 회피 |
| RISK-S13 | **Docker prohibited; no DB-backed verification path confirmed** | 기술 | **H** | **H** (realised) | 완화 |

---

## RISK-S01 — `BIZ_DB` and `BIZTALK_DB` may be different databases

- **영역**: 기술 · **영향**: H · **발생 확률**: L · **전략**: 회피
- **설명**: The admin consoles declare `<target>BIZTALK_DB</target>`; the `KAKAOTALK` send runtime declares `<target>BIZ_DB</target>` for the same table. These are datasource aliases and source alone cannot tell whether they resolve to the same physical database. **ADR-SND-017's entire mechanism assumes they do** — removing a row from the ledger only stops a send if the send path reads that row.
- **대응 계획**: Task S1-03 resolves both aliases against the deployed configuration and test C-S04 proves it empirically by writing through the portal and reading through the `BIZ_DB` alias. **If they differ, S2 stops and ADR-SND-017 is re-derived** before any delete work proceeds.
- **담당자**: architect · **모니터링**: before Sprint S2 starts — hard gate

## RISK-S02 — Existing cross-institution duplicates block the unique index

- **영역**: 기술 · **영향**: H · **발생 확률**: H · **전략**: 완화
- **설명**: `CREATE UNIQUE INDEX` fails outright if the data already violates it. Duplicates are *likely* — the legacy duplicate check was scoped to a single institution (D-S9), so nothing has ever prevented them. Magnitude is unknown until measured, and each collision needs a business decision about which institution keeps the number.
- **대응 계획**: Task S2-01 runs the reconciliation query first and reports counts before any code depends on the constraint. Resolutions are a scripted, auditable migration with a recorded before-state, not ad-hoc SQL. If the count is large, the resolution becomes a PM decision on policy rather than a developer decision per row.
- **담당자**: data-model-designer + operator team · **모니터링**: Sprint S2 day 1

## RISK-S03 — One send path validates no sender number

- **영역**: 외부 · **영향**: H · **발생 확률**: H · **전략**: 수용 + 추적
- **설명**: `ADV_KKO_FT_SEND_act.jsp` references `sender_number` three times and performs no ledger check, while its `_BULK` and `_M` siblings do. On that path a deleted, archived or never-registered number is accepted regardless of anything this project does. FR-SNDD-003 therefore holds for 5 of 6 send paths.
- **대응 계획**: Cannot be fixed here — the code is in `KAKAOTALK`. Documented as threat T-X1, asserted by test C-S06 (which asserts the gap *persists*, and inverts into a regression guard when it is closed), and raised as a named cutover checklist item for the `KAKAOTALK` owners. **G3 release evidence must state it explicitly.**
- **담당자**: PM (routing) + architect · **모니터링**: G3 and cutover

## RISK-S04 — Numbers believed deleted are still live and sendable

- **영역**: 운영 · **영향**: H · **발생 확률**: H · **전략**: 완화
- **설명**: D-S1 means deletion has silently failed since roughly the October 2025 masking release. Operators saw success; the numbers remained in the ledger and remained valid for sending. This is a **live production condition**, independent of the migration.
- **대응 계획**: Reconciliation before S2 ships — every number with an `ACN='D'` history row still present in the ledger. History rows whose `DP_NO` decrypts to a masked pattern (`01********8`) or to a comma-joined list identify the affected deletions directly. **The affected rows must not be "cleaned up" before the reconciliation runs; they are the evidence.**
- **담당자**: operator team + PM · **모니터링**: before S2 ships

## RISK-S05 — `AOA_ADMIN` keeps the defective screens on the same data

- **영역**: 외부 · **영향**: H · **발생 확률**: H · **전략**: 수용 + 추적
- **설명**: `AOA_ADMIN` carries the same four screens against the same `BIZTALK_DB`. After this slice ships, all 21 defects — including the broken delete and the absent authorization — remain reachable through that console on the same data. Separately, once the unique index exists, **`AOA_ADMIN` registrations that previously succeeded will begin failing** wherever they would create a cross-institution duplicate: correct behaviour, but a change reaching users who were never told.
- **대응 계획**: Notify the operator team before the index is created. Raise `AOA_ADMIN`'s disposition (decommission, or port the same fixes) as a programme-level decision — it is out of this slice's scope but not out of the programme's.
- **담당자**: PM · **모니터링**: before the index migration; programme planning
- **관련 발견 (2026-08-17)**: the consumer survey in [ADR-SND-017](adr/ADR-SND-017-senderno-lifecycle.md) also found `D:\workspace\kakaobiztalk_admin` — a **separate repository containing a parallel port of this same project** (`com.kosign.irisadmin`, distinct git history). It has no 발신번호 slice so it does not duplicate this work, but it does carry a handwritten `KKB_DPNO_LDGR_D002` mapper implementing a **hard** institution-cascade delete, which would contradict archive-on-delete if both ports ever ran against the same database. **Whether two ports of this system are intended to coexist is a programme-level question for the PM**, not a decision this slice can make.

## RISK-S06 — Registration without ownership proof (accepted)

- **영역**: 보안 · **영향**: H · **발생 확률**: M · **전략**: 수용
- **설명**: PM ruling AMB-S01 declined ownership verification. An authorized operator can register a number belonging to a third party provided nobody claimed it first, and messages will then carry that party's caller ID. Threat T-S2; the highest-severity threat in the slice.
- **대응 계획**: Compensating controls are server-side authorization (FR-AZ-D01…D05) and global uniqueness (FR-SNDC-004) — neither of which existed before, so the control environment is materially stronger than today even under this ruling. **Revisit before registration is exposed to client-company self-service**, at which point every compensating control's assumption (a vetted internal operator) fails.
- **담당자**: PM + security-auditor · **모니터링**: G1, G3, and any change to who may register

## RISK-S07 — `ENCRYPT` is non-deterministic

- **영역**: 기술 · **영향**: M · **발생 확률**: M · **전략**: 완화
- **설명**: If `ENCRYPT` returns different ciphertext for the same input, a unique index on `DP_NO` is meaningless and the design needs a blind-index column, an HMAC key managed under ADR-007, a backfill across every existing row, and a new threat (T-I7). Roughly a week of additional work and one more long-lived key.
- **대응 계획**: Spike S1-01, scheduled first, expected under a day. Both branches are already designed in ADR-SND-018 so the outcome selects a path rather than starting a design.
- **담당자**: architect + data-model-designer · **모니터링**: Sprint S1 day 1

## RISK-S08 — Uniqueness check is a full scan at production volume

- **영역**: 기술 · **영향**: M · **발생 확률**: M · **전략**: 완화
- **설명**: Every legacy lookup applies `decrypt()` to the column, which is unindexable. Global uniqueness widens that scan from one institution to the whole table on every registration.
- **대응 계획**: If the spike returns "deterministic", lookups rewrite to an indexable form and the risk disappears. Otherwise the blind index provides the index. Load-tested at 2× SLA either way (§8 of the test plan) before it is declared acceptable.
- **담당자**: backend-developer + qa-engineer · **모니터링**: Sprint S2 load test

## RISK-S09 — Read auditing volume outruns an undecided retention policy

- **영역**: 운영 · **영향**: M · **발생 확률**: M · **전략**: 완화
- **설명**: FR-SND-011 audits every list read on the module's landing screen. OI-02 (audit retention term) is still unresolved from Skill 01, and this slice adds a higher-volume event class than the login slice anticipated.
- **대응 계획**: ADR-SND-019 caps volume at request granularity rather than row granularity. The projected volume is supplied as input to the OI-02 decision instead of waiting on it. NFR-OPS-AUDIT-D02 stays OPEN in the matrix.
- **담당자**: architect + PM · **모니터링**: OI-02 resolution

## RISK-S13 — Docker prohibited; no DB-backed verification path confirmed

- **영역**: 기술 · **영향**: **H** · **발생 확률**: **H** (already realised) · **전략**: 완화
- **설명**: Docker is **not permitted** in this environment, so Testcontainers cannot be used — permanently, not pending installation. The declared `org.testcontainers:postgresql` dependency ([pom.xml:107](../../pom.xml)) is dead weight. Every requirement whose correctness depends on the real `ENCRYPT`/`decrypt`/`masking` functions therefore has **no automated verification**: the uniqueness constraint (FR-SNDC-004), the archive-on-delete mechanism (FR-SNDD-001…003), the legacy coexistence suite (C-S01…C-S05) and the D-S1 regression all reduce to tier-3 SQL-shape checks (TEST-PLAN §2).
- **왜 심각한가**: D-S1 — the defect this entire slice exists to fix — **is** an interaction between a DB function and application code. A test suite that cannot execute the DB function cannot demonstrate the fix. This is not a coverage-percentage problem; it is a "the headline requirement is unverified" problem. The institution slice already hit a weaker form of this (RISK-I09, T-I1-02 degraded, T-I1-15 unrunnable) and it has now recurred with more at stake.
- **대응 계획**:
  1. **Establish whether a PostgreSQL dev/test instance carrying the real functions is reachable** — this is the question that decides the slice's verification strategy and it is open (TEST-PLAN §2 tier 1).
  2. If not reachable directly, obtain **DBA-executed evidence** for the point-in-time questions (S1-01 determinism, S2-01 duplicate counts, C-S01 legacy query behaviour).
  3. If neither, obtain the **DDL for `ENCRYPT`/`decrypt`/`masking`** so an in-process PostgreSQL (`io.zonky.test:embedded-postgres`, Apache-2.0) can replay them. Without the real definitions this option is actively harmful — it would look like tier 1 while testing invented functions.
  4. Failing all three, **the affected requirements must be declared unverified at G3** rather than counted as passing.
- **담당자**: PM + architect + DBA · **모니터링**: Sprint S1 day 1 — gates the whole verification strategy

## RISK-S10 — Staging lacks `KAKAOTALK`/`AOA_ADMIN` for the coexistence suite

- **영역**: 일정 · **영향**: M · **발생 확률**: M · **전략**: 완화
- **설명**: C-S01 and C-S04 are the tests that prove ADR-SND-017 actually works, and they need the other applications' schema and configuration available. Without them, the delete design is verified only against our own assumptions.
- **대응 계획**: Confirm staging availability during S1, not S2. C-S01 can run as raw SQL against a shared schema if a full deployment is unavailable, which is a weaker but sufficient check; C-S04 cannot be substituted and gates S2.
- **담당자**: qa-engineer + infra · **모니터링**: Sprint S1

## RISK-S11 — The special-number list is unspecified

- **영역**: 외부 · **영향**: M · **발생 확률**: M · **전략**: 완화
- **설명**: FR-SNDC-006 bars special and emergency numbers. The legacy UI names 112, 114 and 1335 as examples and implements none of them; no authoritative list exists in code. Shipping a guessed list either blocks legitimate numbers or admits barred ones.
- **대응 계획**: AMB-S06 — adopt the published KISA/KAIT special-number list, held as configuration rather than compiled in so it can be updated without a release. Working assumption proceeds; domain owner confirms during S2.
- **담당자**: domain owner · **모니터링**: Sprint S2

## RISK-S12 — G1 not approved while S2 commits to schema change

- **영역**: 일정 · **영향**: M · **발생 확률**: L · **전략**: 회피
- **설명**: S2 creates a table and an index. CONFLICT-S01 (DDL vs CONST-DATA-01) is still awaiting explicit G1 sign-off. Building the write path before that decision risks rework of the delete mechanism and the constraint.
- **대응 계획**: S1 contains no DDL and no schema dependency, so the first two weeks are unaffected. G1 is needed **before S2-02**, not before the sprint starts. Design has already narrowed what G1 must approve: additive DDL only, no alteration of `KKB_DPNO_LDGR`'s meaning to existing readers, no legacy application changed.
- **담당자**: PM · **모니터링**: end of Sprint S1
