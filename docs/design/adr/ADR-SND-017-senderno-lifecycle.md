# ADR-SND-017 — Sender-number lifecycle and legacy send-path coexistence

> **Status**: ACCEPTED
> **Date**: 2026-08-17
> **Slice**: 발신번호 (screens 10/11/12/13)
> **Decides**: AMB-S05, CONFLICT-S02; narrows CONFLICT-S01
> **Requirements**: FR-SNDD-001, FR-SNDD-002, FR-SNDD-003, FR-SNDD-008, CONST-DATA-D04
> **Supersedes**: nothing. **Related**: [ADR-INST-014](ADR-INST-014-lifecycle-state-model.md), [ADR-INST-016](ADR-INST-016-legacy-coexistence.md), [ADR-002](ADR-002-transaction-boundary.md)

---

## Context

Skill 2 ruled deletion logical (AMB-S02) and raised CONFLICT-S02 against it: a status column this project adds would be invisible to the legacy send path, so a number an operator deleted would keep working. That was raised as a suspicion. **Design confirmed it as fact.**

The send runtime is the `KAKAOTALK` application. Five of its send actions validate the caller ID against this slice's ledger, using `IDO.KKB_DPNO_LDGR_L001`:

```sql
select dp_no from kkb_dpno_ldgr
where is_cd = :is_cd and decrypt(dp_no) = :dp_no
```

and reject the send when nothing comes back:

```java
// ADV_KKO_AT_SEND_act.jsp:100-103
if (idoOut0.getString("dp_no") == null) {
    throw new JexWebBIZException(FinCommCodeUtil.SYS_ERR_KM010_CD, …);
}
```

Two properties of that query decide this ADR:

1. **It selects no status of any kind.** A row is either present or absent; there is no third state the legacy can observe. Any column we add is invisible to it.
2. **Absence is already a rejection.** The legacy's failure mode for an unknown number is exactly the behaviour we want for a deleted one.

This is the same situation ADR-INST-014 faced and the same principle applies — but the conclusion inverts. There, an existing `IS_STTS` column meant a status value (`'D'`) the legacy matched against neither `'Y'` nor `'N'` made it fail safe. Here there is no status column at all and the legacy reads none, so a status column would make it fail **open**.

## Decision

**Deletion moves the row out of the ledger into an archive table.**

- `KKB_DPNO_LDGR` — live numbers only. A row's presence means "this number may be used".
- `KKB_DPNO_ARCV` (new) — the complete deleted row plus deletion metadata: who, when, 사유, and the originating operation.
- `KKB_DPNO_HIS` — unchanged, continues to record the event (`ACN='D'`).

Delete is `INSERT INTO KKB_DPNO_ARCV … ; DELETE FROM KKB_DPNO_LDGR …` inside one transaction with the history write (ADR-002). Restoration is the reverse move and is a supported operation, not a DBA recovery exercise.

**This is a logical delete at the system level and a physical delete at the ledger level.** Nothing is lost, restoration is supported, and the ledger means exactly one thing to every reader — including readers we do not control.

## Alternatives considered

| Option | Legacy send path behaviour | Verdict |
|--------|---------------------------|---------|
| **A — status column on `KKB_DPNO_LDGR`** | Row still present, status not read → **deleted numbers remain sendable** | **Rejected.** This is D-I1 rebuilt deliberately: an operator withdraws something, the system reports success, and it keeps working. Closing it would require changing `KAKAOTALK`, which is outside this project's boundary |
| **B — archive-on-delete (chosen)** | Row absent → send rejected by existing code, unchanged | **Accepted** |
| **C — hard delete, history as the only record** | Row absent → send rejected | Satisfies the legacy requirement but not the PM's: `KKB_DPNO_HIS` records the *event*, not the row, so 설명 and the original registration metadata are unrecoverable. This is the option AMB-S02 declined |
| **D — status column + change `KAKAOTALK`** | Correct, if the change ships | Rejected for scope. Requires modifying a second production application, its release train, and the four other send actions — to reach a state option B reaches with no legacy change at all |

## Consequences

**Positive.**
- The legacy fails safe **by construction rather than by remembering to check** — the property ADR-INST-014 established as the standard for this programme.
- No change to `KAKAOTALK`, no coordinated release, no dependency on another team's schedule.
- **The DDL is purely additive.** One new table; `KKB_DPNO_LDGR` is not altered, so no existing reader's behaviour changes. This materially narrows CONFLICT-S01 — see below.
- Restoration and re-registration (FR-SNDD-008) both fall out naturally.

**Negative.**
- Reading a deleted number's full record means querying a second table. Accepted: it is an audit-and-recovery path, not an operational one.
- The archive can drift from the ledger if a future writer deletes without archiving. Mitigated by making the move the only delete path in the application and by TC-S004-11.

**Narrowing of CONFLICT-S01.** Skill 2 escalated logical delete against CONST-DATA-01 ("existing schema reused unchanged; no DDL migration in this scope") and flagged that it sets the schema-change precedent CONFLICT-I02 was resolved to avoid. It cannot be dissolved — `KKB_DPNO_LDGR` has nine columns and none carries state, so *some* DDL is unavoidable. But the precedent this sets is **"this programme may add new tables"**, not "this programme may alter tables the legacy shares". No legacy reader is affected by a table it does not know about. G1 should approve it on those narrower terms.

## Residual gap — one send path is not covered

Nine send actions exist. Six use `sender_number`; **five validate it against the ledger and one does not.** `ADV_KKO_FT_SEND_act.jsp` references `sender_number` three times and performs no ledger check, while its `_BULK` and `_M` siblings do. (The three `FU`/친구톡 actions use no sender number at all, so their lack of a check is correct, not a gap.)

For that one path, **no ledger representation can help** — deleted, archived or absent, it never asks. FR-SNDD-003 is therefore verified against five of six paths by test and the sixth is tracked as **RISK-S03** with a named cutover action, following the ADR-INST-016 pattern: the portal owns the state, the legacy owns enforcement, and the residual gap is recorded rather than assumed closed.

## Verification

| Check | Test |
|-------|------|
| Deleted number rejected by the send path | TC-S004-13 (integration, against the real `KAKAOTALK` check for the five covered actions) |
| Ledger row absent after delete | TC-S004-11 |
| Complete row recoverable from the archive | TC-S004-11, TC-S004-17 |
| Delete + archive + history atomic | TC-S004-07, TC-S004-08 |
| `ADV_KKO_FT_SEND_act.jsp` gap | Not closable here — RISK-S03, cutover checklist item |

## Consumer survey — closed 2026-08-17

This decision rests on knowing **every** reader of `KKB_DPNO_LDGR`: a consumer that read a status column, or that treated absence as something other than "not registered", would invalidate it. All workspace projects were searched.

| Project | Relationship |
|---------|-------------|
| `IRIS_ADMIN` | Legacy admin replaced by this slice |
| `AOA_ADMIN` | Parallel legacy admin against the same `BIZTALK_DB` — RISK-S05 |
| `KAKAOTALK` | Send runtime; the consumer this ADR is derived from |
| `kakaobiztalk_admin` | A **separate repository** holding a parallel port of this same project (`com.kosign.irisadmin`). Covers 문자내역 and 이용기관관리 only — **no 발신번호 slice**, so it does not duplicate this work. It touches the ledger solely through a handwritten `KKB_DPNO_LDGR_D002` mapper for the institution-cascade delete, which implements a **hard** cascade and would conflict with archive-on-delete if both ever ran against the same database. A coordination question for the programme, not a design blocker here — noted under RISK-S05 |
| `cooadm`, `cooptl`, `Coocon-Admin`, `plugin_admin`, `COOPTL_MOBILE` | No references |

**No consumer reads a status column, and every reader treats absence as "not registered".** Archive-on-delete is therefore correct for all of them.
