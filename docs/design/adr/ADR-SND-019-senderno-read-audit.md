# ADR-SND-019 — Read auditing for unmasked sender numbers

> **Status**: ACCEPTED
> **Date**: 2026-08-17
> **Slice**: 발신번호 (screens 10/11/12/13)
> **Decides**: how FR-SND-011 is implemented without an audit-volume problem
> **Requirements**: FR-SND-006, FR-SND-011, NFR-OPS-AUDIT-D01, NFR-OPS-AUDIT-D02, NFR-PERF-D01
> **Related**: [ADR-006](ADR-006-audit-logging.md), [ADR-002](ADR-002-transaction-boundary.md)

---

## Context

AMB-S04 ruled that sender numbers display **in full**, with read events audited as the compensating control. The masking was removed for a good reason — it was applied by a name-masking utility to a phone number, list and detail disagreed, and the disagreement broke deletion (D-S1). But removing it means every operator session now displays unmasked numbers for whichever institution they select, and the audit trail is what makes that accountable.

Taken naively, "audit every read" means one audit row per list request. The 발신번호 list is the screen's landing view and re-queries on every institution change and every page turn. That is a high-frequency, low-value event stream that would dominate the audit store — and an audit store nobody can search is not a control, it is storage cost. NFR-OPS-AUDIT-D02 also carries an unresolved retention term (OI-02), so volume decisions cannot be deferred to "we will trim it later".

The existing `AuditService` (로그인 slice) already provides the right transactional shape: `REQUIRES_NEW`, so the evidence of an attempt survives a rolled-back business transaction, and denials are recorded as well as successes.

## Decision

**Audit the read at request granularity, not row granularity, and record what was exposed rather than the values exposed.**

One audit event per list or detail request, carrying:

- actor, timestamp, source IP, correlation id (existing `AuditEvent` shape)
- action — `senderno.list` / `senderno.detail`
- target — the institution code whose numbers were read
- detail — **the count of numbers returned and the page requested. Never the numbers themselves.**
- outcome — `SUCCESS` or `DENIED`

**Denied reads are audited too**, and matter more than successful ones: a sequence of `DENIED` events across institution codes is an enumeration attempt, and it is the signal this slice most needs to be able to see, given that authorization is the only barrier RESIDUAL-S01 leaves standing.

Writing the sender numbers into the audit detail is **prohibited**. It would move the very data the control protects into a second store with a longer retention period and a different access model — a net increase in exposure achieved in the name of reducing it. NFR-SEC-LOG-D01 already forbids it for application logs; this extends the same rule to the audit store.

## Alternatives considered

| Option | Volume | Verdict |
|--------|--------|---------|
| **A — one event per row read** | ~1 per number per page view | Rejected. Highest fidelity, unusable in practice, and drags the numbers themselves into the audit store |
| **B — one event per request (chosen)** | ~1 per page view | **Accepted.** Answers "who looked at which institution's numbers, and when" — the question the control exists to answer |
| **C — audit detail reads only, not list reads** | Very low | Rejected. The list is where the numbers are actually exposed in bulk; auditing only the detail view audits the narrower exposure and misses the wider one |
| **D — sampled auditing** | Configurable | Rejected. A sampled access log cannot answer a question about a specific incident, which is the only time anyone reads it |

## Consequences

**Positive.**
- Bounded, predictable volume proportional to operator activity rather than data size.
- Enumeration attempts are visible as a pattern of `DENIED` events — directly useful given FR-AZ-D03.
- Reuses `AuditService` unchanged; no new infrastructure.

**Negative.**
- The trail records that an operator read an institution's numbers, not which specific numbers were on screen. Accepted deliberately: recording the numbers is the outcome this decision exists to prevent, and the institution plus timestamp is enough to reconstruct the set from the ledger and archive if an investigation needs it.
- Adds a write to the read path. Kept off the critical path — the audit write is `REQUIRES_NEW` and must not extend the P95 of NFR-PERF-D01; verified by load test rather than assumed.

**Open.** Retention (OI-02) is still unresolved and now applies to a higher-volume event class than the login slice anticipated. The volume estimate belongs in that decision; §NFR-OPS-AUDIT-D02 remains OPEN in the matrix.

## Verification

| Check | Test |
|-------|------|
| One event per list request, with institution and count | TC-S001-12 |
| Denied read produces a `DENIED` event | TC-S001-03, TC-S001-04 |
| No sender number appears in any audit row | New: audit-store scan after a full E2E run |
| Audit write does not breach the list P95 | NFR-PERF-D01 load test with auditing enabled |
