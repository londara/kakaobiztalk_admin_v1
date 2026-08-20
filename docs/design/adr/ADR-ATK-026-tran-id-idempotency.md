# ADR-ATK-026 — `tran_id` generation and duplicate-send prevention

> **Status**: ACCEPTED
> **Date**: 2026-08-18
> **Slice**: 알림톡 템플릿/발송 (screens 61, 50)
> **Decides**: how FR-ATS-008/009/010 are satisfied within a 10-character field
> **Requirements**: FR-ATS-008/009/010, FR-ATC-005, CONST-DATA-A04, NFR-SEC-TX-A01
> **Related**: [ADR-009](ADR-009-retry-idempotency.md), [ADR-ATK-023](ADR-ATK-023-send-consistency-outbox.md), RESIDUAL-A02

---

## Context

`tran_id` does three jobs at once: it is the vendor's correlation handle, it is `KKB_ADMIN_SEND_HIS.SERIALNUM` — half of that table's primary key (CONST-DATA-A04) — and under PM ruling AMB-A01 it is the deduplication key.

The legacy generates it as `"33" + hh24miss` in one branch and `hh24miss + apiNumber++` in the other (D-A25). Both collide:

- Second precision means two sends in the same second produce the same value, colliding on a primary key.
- Neither carries a date, so today's value collides with yesterday's.
- Two different formats mean the two branches can collide with each other.

Three constraints make this awkward rather than routine.

**The field is 10 characters** (`IMO.ADV_KKO_AT_SEND`, `<item length="10" id="tran_id">`). Ten characters is not much room for a value that must be unique across institutions, across days, and under concurrency. A UUID does not fit; nor does a timestamp with enough precision plus a random suffix, if the timestamp is human-readable.

**PM declined server-side generation** (AMB-A02b option C). `tran_id` stays operator-supplied, and FR-ATS-008's uniqueness requirement therefore has to be met by something the operator can override — recorded as RESIDUAL-A02.

**The outbox is at-least-once** (ADR-ATK-023). Our own dispatcher may send the same `tran_id` twice by design, so the vendor's behaviour on a repeat matters as much as ours.

## Decision

**Uniqueness is enforced by the database; the value is operator-supplied with a generated default; deduplication is a distinct, explicit check.** Three separable mechanisms, because they answer three different questions.

**1. Enforcement: a database constraint.** `KKB_ADMIN_SEND_HIS` is already keyed `(IS_CD, SERIALNUM)`, so the constraint exists — the legacy simply violated it and, having no transaction (D-A27), never noticed the resulting failures. The application catches the violation and returns a clean duplicate response rather than a stack trace.

This mirrors ADR-SND-018's reasoning and for the same reason: **an application-level check cannot enforce this while screen 50 exists.** CONFLICT-A01's resolution retires screen 50 at cutover, but the constraint must hold *during* the coexistence window, and only the database binds both writers.

**2. Generation: a scheme that fits 10 characters.**

```
  A 2 6 0 8 1 8 0 0 1        ← exactly ten characters
  │ └──┬──┘ └─┬─┘
  │    │      └────── per-institution daily sequence, base-36, 3 chars (46,656/day)
  │    └───────────── yyMMdd, 6 chars
  └────────────────── environment discriminator (A=prod, T=staging)
```

> **AMENDED at Sprint A1 (2026-08-18).** This diagram originally allotted **four** characters to the
> sequence and claimed 1.6 M/day. That is 1 + 6 + 4 = **11 characters** — one over the contract's
> `length="10"` — and the diagram as drawn was itself eleven characters wide. The arithmetic was
> simply wrong, and `TranIdGeneratorTest.exactlyTenCharacters` failed the first time it ran. The
> sequence is **three** characters and the ceiling is **46,656 per institution per day**, a 36×
> reduction from the figure RISK-A04 was first assessed against.
>
> The ceiling still holds comfortably, because a `tran_id` is consumed **per send request, not per
> recipient** — a single send to 1000 recipients spends one. But the headroom is smaller than this
> ADR originally claimed, and the decision below stands only because of that consumption model, not
> because the original numbers were right. Recorded here rather than silently corrected: an ADR that
> quietly edits its own numbers is no longer a record of a decision.

A per-institution daily sequence rather than a timestamp: it is collision-free by construction instead of probabilistically, it makes duplicates impossible rather than unlikely, and it is legible to an operator reading a history row. The sequence comes from a database sequence per `(is_cd, date)`, so concurrency is handled where the constraint lives.

The environment discriminator exists because a staging send reaching the real vendor with a production-shaped `tran_id` is the kind of mistake that is obvious in hindsight.

**3. Deduplication: an explicit pre-check, not a caught exception.** Within the retention window, an incoming `(is_cd, tran_id)` is looked up first; a match returns the **original outcome** (FR-ATS-009), not a generic conflict. This is the difference between "your send was rejected" and "your send already happened, here is what it did" — and the second is the one that stops an operator retrying.

The window is AMB-A08, working assumption **B**: match `KKB_ADMIN_SEND_HIS` retention, so dedupe cannot outlive its own evidence. A dedupe check against records that have been purged silently becomes a no-op, which is worse than not having one.

**4. Operator override, guarded.** The field is pre-filled with a generated value and remains editable, per PM's ruling. A hand-entered value is validated for length and charset, then subject to the same constraint and the same dedupe check. Editing it is therefore safe — the worst outcome is a rejection, never a double send.

**5. Vendor-side idempotency is verified, not assumed.** Task A1-03 establishes what `COOCON_ALERT` does with a repeated `tran_id`. The answer sets ADR-ATK-025's retry policy: if the vendor deduplicates, read-timeout retries become safe; if not, the conservative split stands permanently. **This is the one assumption in ADR-ATK-023 that we cannot verify from source and cannot safely guess** (RISK-A07).

## Alternatives considered

| Option | Mechanism | Verdict |
|--------|-----------|---------|
| **A — timestamp with sub-second precision** | `yyMMddHHmmssSSS` truncated to fit | **Rejected.** Does not fit 10 characters with a date included, and truncating reintroduces exactly the collision class being fixed |
| **B — random / hash suffix** | 6 random base-36 characters after a date | Rejected as primary. Collision becomes improbable rather than impossible, and on a primary key "improbable" means a rare production failure nobody can reproduce. Random values are also opaque in a history row |
| **C — per-institution daily sequence (chosen)** | DB sequence per `(is_cd, date)` | **Accepted.** Collision-free by construction, legible, fits with room to spare |
| **D — application-level check only** | `SELECT` before insert, no constraint | Rejected. Racy under concurrency and bypassed by screen 50 during the coexistence window — ADR-SND-018's finding applied to a different table |
| **E — external idempotency store (Redis)** | Dedupe keys with TTL | Rejected. New infrastructure for a guarantee the existing primary key already provides, and it would place the authoritative record outside the transaction that writes history |

## Consequences

**Positive.**
- Collisions become structurally impossible rather than unlikely, on a value that is a primary-key component.
- The constraint binds screen 50 during coexistence, so the guarantee does not wait on cutover.
- A duplicate submission returns the original outcome, which is what stops the operator retrying — the behaviour that generated the duplicate customer messages described in specification §6.5.
- The generated value is readable, so an operator can correlate a history row with a vendor enquiry without a lookup table.

**Negative.**
- **46,656 sends per institution per day is a hard ceiling** (amended above). Well above plausible volume given per-request consumption, but it is a ceiling, and exhausting it fails sends outright. Monitored rather than mitigated.
- **RESIDUAL-A02 stands.** Because the field stays editable, an operator can still cause a *rejection* by reusing a value. Dedupe converts a data-integrity failure into a usability one, which is the right trade, but the failure is now operator-visible where before it was silent. If spurious duplicate rejections are reported, revisit PM's AMB-A02b decision.
- **A new status column on `KKB_ADMIN_SEND_HIS`** is required so dedupe can return the *original outcome* rather than merely detect a repeat. Shared-table DDL — the same additive-only precedent as ADR-ATK-023, and part of RISK-A06.
- Screen 50's existing rows carry legacy-format `tran_id`s that this scheme will never generate. Harmless — formats need not be uniform historically — but reconciliation queries must not assume the new shape (specification §6.5 action 2).

## Verification

| Check | Test |
|-------|------|
| Two sends in the same second get distinct values | TC-A002-03 |
| Same clock time on different days | TC-A002-04 |
| Concurrent sends under load produce no collision | New: 500 concurrent, assert zero duplicates |
| Duplicate submission returns the original outcome | TC-A002-16 |
| Duplicate after the window is accepted as new | TC-A002-17 |
| Batch and single share one scheme | TC-A003-13 |
| Constraint binds a writer outside the application | New: direct SQL insert of a duplicate fails |
| Hand-entered value validated for length and charset | TC-A001-04 |
| Vendor behaviour on a repeated `tran_id` | Task A1-03 (finding, not a test) |
| Sequence exhaustion fails loudly | New |
