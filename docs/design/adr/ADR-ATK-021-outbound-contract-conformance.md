# ADR-ATK-021 — Enforcing conformance to the outbound AlimTalk contract

> **Status**: ACCEPTED
> **Date**: 2026-08-18
> **Slice**: 알림톡 템플릿/발송 (screens 61, 50)
> **Decides**: how FR-ATC-001 is enforced; how D-A1, D-A2, D-A3 and D-A7 are prevented from recurring
> **Requirements**: FR-ATC-001…006, CONST-DATA-A01, CONST-DATA-A02, NFR-USE-A03
> **Related**: [ADR-003](ADR-003-persistence-strategy.md), [ADR-ATK-025](ADR-ATK-025-http-client-resilience.md), [CONFLICT-A02](../../requirements/REQUIREMENTS-SPEC-ALIMTALK.md)

---

## Context

Three of this slice's four Critical defects are the same defect: the composer's output does not match the contract it composes for.

- It emits `failback` where `IMO.ADV_KKO_AT_SEND` declares **`failback_data`** (D-A1) — silently dropping the SMS/LMS fallback.
- It emits `msg_type`, `kko_header`, `highlight`, `items` and `summary`, none of which the contract declares (D-A2).
- It never emits `order`, which `IMO.ADV_KKO_AT_SEND_M` declares on every `msg_data` item (D-A3).
- It enforces none of the contract's twelve declared field lengths (D-A7).

**The interesting fact is not that these exist; it is that nothing could have caught them.** `ADV_KKO_AT_SEND_M` is called by no code in the repository, and screen 61's output is consumed by no code at all — the operator copies it out by hand. A payload that was wrong in four ways for over a year had no path by which it could fail visibly.

That diagnosis constrains the fix. Writing correct DTOs by hand fixes today's four defects and reproduces the *conditions* that produced them: a hand-maintained representation of an external contract, with nothing comparing the two. The contract is also not ours — it is a `jex` IMO definition with declared lengths and nested `RECORD`/`GROUP` sub-rules, and it can change without us.

One further wrinkle: the request does not travel as this structure. `IMO.ADV_KKO_AT_SEND2` (`target=COOCON_ALERT`, `_IMO_APPEND_URL=/advising/kakao/at_send`) declares a **single** input field `RSMS`. How the bound request becomes that string is not recoverable from the analysed artifacts (AMB-A06, RISK-A02).

## Decision

**The contract is the test fixture, not a comment.** Conformance is enforced by a **contract test that reads the IMO XML definitions directly** and asserts the serialised payload against them. Three parts:

**1. Hand-written DTOs, machine-checked against the contract.** `AlimTalkRequest` / `AlimTalkBatchRequest` and their nested types are ordinary Java records with Jakarta Validation annotations. They are readable, debuggable and reviewable. What makes them trustworthy is not care but `ContractConformanceTest`, which parses `IMO.ADV_KKO_AT_SEND.xml` and `IMO.ADV_KKO_AT_SEND_M.xml` from a checked-in copy and asserts, per field:

- every `<item id="…">` in the contract has a corresponding serialised JSON property — **catches D-A3**;
- every serialised property corresponds to a contract `<item>` — **catches D-A1 and D-A2**, since `failback` and `msg_type` both fail this direction;
- every `length="…"` is reflected by a validation constraint on the DTO — **catches D-A7**;
- nested `RECORD` / `GROUP` sub-rules recurse through the same three checks.

The bidirectional check is the point. A one-directional "does the contract's field exist in our DTO" test passes on the legacy payload, because `failback` is an *extra* field, not a missing one.

**2. Validation constants are derived, not transcribed.** Field bounds come from `min(contract length, business limit)` per CONFLICT-A02, with the contract value read from the XML and the business limit declared in one `AlimTalkLimits` class tagged `[ASSUMED-KAKAO-SPEC]`. Transcribing twelve numbers by hand into annotations is exactly the error class the slice is fixing.

**3. The `RSMS` envelope is verified against a captured payload, not inferred.** Task A1-02 captures a real `RSMS` value from the legacy send path; `RsmsEnvelopeTest` asserts our marshalling reproduces it byte-for-byte for the same input. Until that capture exists the envelope is unverified and is treated as such (RISK-A02) — not assumed correct.

## Alternatives considered

| Option | Mechanism | Verdict |
|--------|-----------|---------|
| **A — hand-written DTOs, reviewed** | Careful implementation plus code review | **Rejected.** This is precisely what the legacy had. Review did not catch four defects in a year; there is no reason to expect it to catch the fifth |
| **B — generate DTOs from the IMO XML at build time** | An annotation processor or Maven plugin emits records from the contract | Rejected, though it is the theoretically cleanest answer. The `jex` IMO schema is proprietary, sparsely documented, and used by exactly two files in this slice. A generator is more code to own than the DTOs it would produce, and it makes the payload shape invisible in the source a reviewer reads |
| **C — hand-written DTOs + bidirectional contract test (chosen)** | The contract XML is a test resource; conformance is asserted in CI | **Accepted.** Keeps the readable DTOs of A and adds the one mechanism the legacy lacked |
| **D — JSON Schema validation at runtime** | Validate the outgoing payload against a schema before despatch | Rejected as the *primary* control — it moves detection from build time to production, and a rejected send is a customer notification that did not arrive. Retained as a cheap secondary assertion inside the outbox dispatcher |

## Consequences

**Positive.**
- All four contract defects become build failures rather than silent production behaviour. `ContractConformanceTest` is the mechanism this feature has never had.
- **Verifiable without Docker.** Contract conformance is pure serialisation against a checked-in XML file, so unlike the 발신번호 slice — where the headline defect was a DB-function interaction that RISK-S13 left unverifiable — this slice's three Critical defects are fully covered at tier 1. That is a material difference in G3 confidence.
- If the vendor contract changes, the test fails at the next build instead of at the next customer notification.

**Negative.**
- The checked-in contract copy can drift from the deployed IMO definition. Mitigated by keeping the copy's `<version>` and `<hash>` values in the test and asserting them, so a substituted contract is loud rather than silent — but a contract updated in the runtime and not in our repository still goes unnoticed until someone looks. Tracked as RISK-A02.
- The bidirectional check will reject the item-list and image fields (`kko_header`, `highlight`, `items`, `summary`) as soon as it is written, because the contract genuinely has nowhere to put them. **This is the test working**, and it makes AMB-A05 a build-blocking question rather than a documentation note — which is why the A1-01 spike is scheduled first.

**Deliberately unresolved.** Whether the vendor accepts fields beyond the IMO declaration. `COOCON_ALERT` may well support image and item-list parameters that this IMO contract simply never modelled. The contract test asserts conformance to *the interface we call*, which is the correct scope; extending the contract is AMB-A05's job.

## Verification

| Check | Test |
|-------|------|
| Fallback key is `failback_data` | TC-A001-02, `ContractConformanceTest` |
| No undeclared field is emitted | TC-A001-03, `ContractConformanceTest` (reverse direction) |
| `order` present on every batch item | TC-A003-02, `ContractConformanceTest` |
| Every contract length has a matching constraint | TC-A001-05, `ContractConformanceTest` |
| Contract version/hash unchanged | `ContractConformanceTest` |
| `RSMS` envelope reproduces a captured payload | `RsmsEnvelopeTest` (blocked on task A1-02) |
