# ADR-INST-016: Legacy coexistence on the shared institution table

> **Status**: ACCEPTED
> **Date**: 2026-08-14
> **Deciders**: PM (Skill 3 Q1), `architect`
> **Slice**: 이용기관관리
> **Related**: [ADR-002](ADR-002-transaction-boundary.md), [ADR-003](ADR-003-persistence-strategy.md), [ADR-INST-014](ADR-INST-014-lifecycle-state-model.md), [ADR-INST-015](ADR-INST-015-atk-credential-handling.md)

---

## 1. Context

Every earlier slice was **read-mostly against tables the legacy also read**. 문자내역 queries message history; 로그인 reads and updates `USER_LDGR`, which the legacy admin console also uses but which no runtime component caches.

이용기관관리 is the first slice that **writes the control data another running system depends on**. `FT_FTIS_INFO` is read by:

| Legacy consumer | What it reads | How |
|-----------------|---------------|-----|
| Send API / BizTalk runtime | `ATK` for authentication, `IS_STTS` for entitlement | Direct query |
| `FINInstitution` cache | Whole-table snapshot | In-memory, refreshed by `FINInstitution.getInstance().getManager().reload()` |
| AOA institution lookup | `FINTECH_ISCD`, `ISNM`, `ATK` | `KKB_FT_FTIS_INFO_L003`, gated on `BSNN_STTS_CKYN` |
| Legacy admin screens 00/01 | Everything | The screens this slice replaces |

Two consequences follow, and both were surfaced as questions at Skill 3.

**First**, FR-INSTL-009 says a 미사용 or deleted institution cannot authenticate to the send API. This portal can *write* that state but cannot *enforce* it — the check happens inside the legacy runtime.

**Second**, the legacy caches the table in memory. `biztalk_admin_01_c001_act.jsp` calls `reload()` after committing, wrapped in `catch(Throwable){printStackTrace();}` (D-I17), so a refresh failure is silent and the change simply does not take effect. Our writes face the identical problem, and we do not own the cache.

## 2. Decision

**Adopt an explicit split of responsibility: this portal is the system of record for institution state; the legacy runtime remains the enforcement point. The gap between them is documented, tested to our boundary, and tracked as a risk with an owner.**

| Responsibility | Owner | Note |
|----------------|-------|------|
| Writing 사용여부 / 삭제 state | **New portal** | `IS_STTS` per ADR-INST-014 |
| Writing institution attributes and `ATK` | **New portal** | ADR-INST-015 |
| Enforcing entitlement at send time | **Legacy runtime** | Unchanged; out of this project's boundary |
| `ATK` verification at send time | **Legacy runtime** | Why storage stays plaintext (ADR-INST-015 §2.1) |
| Invalidating the `FINInstitution` cache | **Shared** — portal triggers, legacy owns | See §2.2 |

### 2.1 Compatibility rules for every write

1. **Write only values a legacy reader degrades safely on.** This is why deletion is `IS_STTS='D'` and not a new column (ADR-INST-014 §2.1): `'D'` drops out of the legacy's `IS_STTS = 'Y'` and `= 'N'` filters by construction, whereas a column the legacy cannot see would leave a deleted institution fully operational.
2. **Never remove a row.** `IS_CD` must stay resolvable for the legacy's joins as well as ours.
3. **Preserve column semantics.** `FINTECH_ISCD`, `ISNM`, `ISENGNM`, `IS_STTS`, `ATK`, `BRNO`, `CMOP` keep their existing meaning and format, including the `YYYYMMDDHH24MISS` timestamp shape — corrected per D-I9, but not restructured.
4. **Do not write columns this slice does not own.** `FT_FTIS_INFO` has 40+ columns (`IDO.KKB_FT_FTIS_INFO_L004`); this slice writes 11. The rest — `SRVR_IP`, `GRAMT`, `BSNN_STTS_CKYN` and others — are left untouched, including on update.
5. **No DDL.** Per CONST-DATA-01 and ADR-INST-014.

Rule 4 matters more than it looks: the legacy upsert `KKB_FT_FTIS_INFO_C001` uses `INSERT … SELECT` with an explicit column list, so unlisted columns take defaults on insert. A naive port using `INSERT` with a full column list would **null out** 30 columns of operational configuration.

### 2.2 Cache coherence

The portal calls the same refresh path the legacy uses after a committed change, but unlike the legacy (D-I17) a failure is **surfaced, not swallowed**: the operator is told the change is saved but not yet active, and an operational alert is raised (FR-INSTC-008, NFR-OPS-I02).

We cannot guarantee the legacy cache is coherent — a second legacy instance, or one that missed the trigger, will serve stale data until its own refresh cycle. This is a **known, accepted staleness window**, recorded as TM-I012 and RISK-I03.

### 2.3 The enforcement gap

FR-INSTL-009 is verified **to our boundary**: tests assert that the state is written correctly and is readable by a legacy-shaped query. They do not, and cannot, assert that the send API refuses the institution.

That residual is RISK-I02, owned by the PM, with a named cutover action: before the legacy send path is decommissioned, confirm it honours `IS_STTS` values other than `'Y'`.

This is stated rather than glossed because D-I1 is exactly this failure mode in miniature — an operator disabled an institution, believed it stopped, and it did not. Declaring FR-INSTL-009 "done" on the strength of a green portal-side test would reproduce the defect at the system level.

## 3. Considered alternatives

| # | Option | Scope | Gap closed | Verdict |
|---|--------|-------|-----------|---------|
| **A** | **Portal writes state; legacy enforces; gap tracked** | Smallest | No — tracked as RISK-I02 | **SELECTED** |
| B | Also modify the legacy send path to honour the new state | Cross-repository, separate release | Yes | Rejected for this slice |
| C | New authorization gate in front of the send API, owned by this portal | New runtime component | Yes | Rejected |

**Why not B.** It is the correct end state and should happen at cutover. It is rejected *now* because it means changing a system outside this repository on a different release train — coupling this slice's delivery to a legacy release, for a gap that only matters while both systems run.

**Why not C.** A new gate in the send path is a significant new runtime component with its own availability and latency budget, introduced into a revenue path, to bridge a temporary coexistence window. The cost is out of proportion.

**Why A is honest rather than merely cheap.** A leaves a real gap. It is chosen because the gap is *bounded* (it closes at cutover), *known* (RISK-I02, TM-I013), and *owned*. Options B and C close it earlier at a cost the PM judged disproportionate. Recording it beats implying full enforcement.

## 4. Consequences

**Positive**

- This slice ships without a legacy release dependency.
- `IS_STTS='D'` makes the legacy fail safe for deletion without any legacy change.
- Rule 4 prevents a whole class of silent data-loss defect on update.

**Negative**

- **FR-INSTL-009 is not fully enforced during coexistence.** A disabled institution may keep sending until the legacy notices, bounded by its cache refresh.
- Two systems write the same table. Nothing prevents a legacy admin screen from editing an institution concurrently; we have no lock spanning both.
- The staleness window is real and unmeasured — we do not know the legacy's refresh cadence.

**Neutral**

- The legacy screens 00/01 should be **disabled** once this slice ships, to reduce dual-write surface. That is a deployment action, not a code change (Sprint I2 task T-I2-12).

## 5. Verification / monitoring

| Check | Method | Requirement |
|-------|--------|-------------|
| Update writes only the 11 owned columns | Integration test asserting the other columns unchanged | Rule 4 |
| `'D'` invisible to a legacy-shaped `IS_STTS='Y'`/`'N'` query | Integration test issuing the legacy SQL verbatim | ADR-INST-014 §2.1 |
| No row physically removed | Integration test | Rule 2 |
| Cache refresh failure surfaces and alerts | Integration test, simulated failure | FR-INSTC-008, NFR-OPS-I02 |
| Timestamps written in `YYYYMMDDHH24MISS` | Unit test | FR-INSTC-006 |
| **Legacy send path honours non-`'Y'` status** | **Manual verification at cutover — not automatable from here** | RISK-I02 |

## 6. References

- [REQUIREMENTS-SPEC-INSTITUTION.md](../../requirements/REQUIREMENTS-SPEC-INSTITUTION.md) §2.4 FR-INSTL-009, §6.3 AMB-I10
- Legacy: `IDO.KKB_FT_FTIS_INFO_L003`/`L004`, `biztalk_admin_01_c001_act.jsp` (`FINInstitution` reload)
- [threat-model-INSTITUTION.md](../threat-model-INSTITUTION.md) — TM-I012, TM-I013

## Change history

| Date | Version | Change | Author |
|------|---------|--------|--------|
| 2026-08-14 | 1.0 | Initial — option A per Skill 3 Q1 (AMB-I10) | `architect` |

---

**Approval**

| Date | Approver | Comment | Status |
|------|----------|---------|--------|
| 2026-08-14 | PM | Option A — portal writes state, gap tracked | **ACCEPTED** |
| 2026-08-14 | Architect | Accepted; RISK-I02 must have a named cutover owner | **ACCEPTED** |
