# ADR-RPT-022 — Aggregation freshness and the limits of what the report can assert

> **Status**: ACCEPTED
> **Date**: 2026-08-18
> **Slice**: 이용기관 보고서 (screen 20)
> **Decides**: AMB-R07; bounds OI-R01
> **Requirements**: FR-RPT-013, FR-RPT-014, FR-RPTS-005, NFR-USE-R01, NFR-OPS-R01, CONST-DATA-R01, CONST-DATA-R02
> **Supersedes**: nothing. **Related**: [ADR-RPT-021](ADR-RPT-021-cross-source-aggregation.md), [ADR-INST-016](ADR-INST-016-legacy-coexistence.md)

---

## Context

The report owns no data. `KKB_APITR_SMTN` is written solely by `BATCH_BIZTALK_DAILY` (BR-009), and Skill 2 found two defects in that batch that bound what any requirement here can honestly promise:

- **D-R26** — a parameterless run aggregates `LocalDate.now().minusDays(4)`: one day, four days back. A parameterised run requires `startDate.isBefore(endDate)` **strictly**, so `START_DT=END_DT` throws `INPUT ERROR` and a single day can never be re-aggregated on its own. The data is therefore at best **T-4**, and cannot be repaired a day at a time.
- **D-R27** — each day is delete-then-insert with no transaction. On `BIZTALK_DB` a failed insert throws and leaves the day deleted. On `BIZTALK_BULK_DB` the whole block sits inside `catch (JexBIZException e) { LOG.error(e); }`, so **the batch reports success while that day's bulk aggregate is missing**, and the report renders the absence as zero.

This is why the production screen opens empty: its date fields default to today, and today is three days inside a window the batch has never populated. The screen is not broken. It is silent about data that does not exist.

**PM ruling (2026-08-18, OI-R01).** The batch stays **out of this slice**. The report degrades honestly instead. No DDL, no batch change.

That ruling settles scope and creates this ADR's actual question: with no run-status record anywhere and no permission to create one, **what can the report truthfully say about how current its data is?**

## Decision

**The watermark is derived as `max(TRDD)` per source, and the report is explicit about the difference between "not aggregated" and "zero".**

```sql
SELECT max(TRDD) FROM KKB_APITR_SMTN
```

issued once per datasource per request, cached for a short TTL (provisionally 60 s). Index-served, constant cost, no DDL.

Three behaviours follow:

1. **The as-of date is displayed, unprompted** — `집계 기준일: API 2026-08-14 · 대량 2026-08-14` (FR-RPT-013, NFR-USE-R01). The user learns the data is batch-derived and how far behind it is without having to ask, and without having to infer it from an empty grid.
2. **Days above the watermark are labelled 미집계**, not `0` (FR-RPT-013). A range ending today renders its recent days as *not yet aggregated*.
3. **An empty result below the watermark is 조회된 내용이 없습니다** — a genuine zero, distinct from both an error and from 미집계 (FR-RPT-014).

**The limitation is documented, not papered over.** `max(TRDD)` proves the *latest* day carrying any row. It cannot prove an *interior* day was aggregated. A day that D-R27 deleted and failed to reinsert sits **below** the watermark and is indistinguishable from a genuinely quiet day. The report cannot detect it, and this ADR does not claim otherwise.

Within our reach there is one partial signal, and it is offered as a signal rather than a proof: where one source has rows for a day across the whole institution set and the other has none, the day is flagged as a **possible source gap** (FR-RPTS-005). It catches the wholesale case — an entire day's bulk aggregation lost — and misses the partial one. It is labelled as heuristic in the UI for exactly that reason.

Full closure needs the batch to record what it actually did, which is OI-R01 and belongs to the batch owner.

## Alternatives considered

| Option | Watermark quality | Needs | Verdict |
|--------|-------------------|-------|---------|
| **A — batch writes a run-status row per day per source** | **Exact.** Distinguishes aggregated-and-zero from never-aggregated, and records failures | DDL + batch change | **Rejected by PM ruling** (OI-R01). Technically the right answer and recorded as such: it is the only option that closes D-R27's blind spot, and it should be adopted when the batch is taken up. It also needs the same "this programme may add tables" G1 precedent ADR-SND-017 required |
| **B — derive `max(TRDD)` per source (chosen)** | Latest day only; blind to interior gaps | nothing | **Accepted.** Honest about a real limitation rather than silent about it |
| **C — read the batch scheduler's own run history** | **Actively misleading** | access to the scheduler | **Rejected, and the reason matters.** D-R27 means the batch **reports success on a run whose bulk aggregation failed**. Its own history therefore records a successful run for a day whose data is missing. Sourcing the watermark from it would not merely be imprecise — it would let the report assert freshness that is false, with the scheduler's authority behind it. An unreliable narrator is worse than an admitted gap |
| **D — portal recomputes aggregates from the raw send tables to verify** | Exact, and detects every gap | large read cost; duplicates the batch | **Rejected.** Violates CONST-DATA-01 (this slice is read-only over the aggregate) and re-implements BR-009 in a second place, so the two would drift. If recomputation is ever wanted, it belongs in the batch as a self-check, not in the report |

## Consequences

**The report becomes honest about a lag that predates it.** T-4 is not introduced here and is not fixed here; it is made *visible* here. RISK-R02 covers the possibility that operations consider T-4 normal and the display merely confirms what they already know — in which case FR-RPT-013 costs little and still prevents the next person from misreading an empty grid as an outage.

**One class of wrong number remains undetectable from this side.** Historical bulk aggregates may already contain silent zeros (RISK-R03), and after this ADR the report will render them as zeros with a watermark that vouches for the day. The reconciliation that would find them is a data exercise against raw send records, listed as an operational prerequisite in DEV-PLAN §4.4 — not a development task, and not something the watermark can substitute for.

**This is the third slice to end with the same shape.** D-I1 (institution disabled, still active), D-S1 (number deleted, still live), D-R27 (day aggregated, silently absent). In each, a system reported that an operation succeeded when its effect was missing, and in each the migration's honest move was to make absence visible rather than to assume the record is true. FR-RPT-013 and FR-RPTS-005 are that move for this slice.

**Cost accepted.** Two extra `max()` probes per request — index-served, cached, negligible against NFR-PERF-R01. The heuristic gap flag will produce occasional false positives on days when one channel genuinely had no traffic; it is worded as a possibility, not a fault, and test T-R2-11 asserts it does not fire on a legitimately quiet day for a single institution.
