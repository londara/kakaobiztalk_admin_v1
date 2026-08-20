# ADR-SND-021 — The barred special/emergency number list lives outside the code

> **Status**: ACCEPTED
> **Date**: 2026-08-20
> **Slice**: 발신번호 (screen 12) — Sprint S2a
> **Decides**: AMB-S06
> **Requirements**: FR-SNDC-006, FR-SNDC-013, CONST-BIZ-D03
> **Related**: [ADR-SND-020](ADR-SND-020-write-dialog-presentation.md), threat T-T4, RISK-S11

---

## Context

D-S12 is the smallest defect in the slice and the most instructive one. `biztalk_admin_12_view.jsp` tells the operator:

> 112, 114, 1335 과 같은 특수번호는 등록 불가능합니다.

and **no layer implements it** — not the client validation (which was vacuous anyway, D-S11), not `isValidDpNo()`, not the service contract, not the query. The screen stated a rule the system did not have, and the length check does not cover it: `112` is refused incidentally for being under 8 digits, while `1335` sails through.

Sprint S1 ported the rule into `SenderNumberValidator.BARRED_NUMBERS` as **14 hardcoded values, explicitly labelled a working assumption** in the Javadoc, with AMB-S06 left open for a domain owner. PM ruling AMB-S06 (2026-08-20) adopts those 14 as the initial set and asks that they be changeable without a release.

Two things make this more than a configuration chore. The list is **regulatory data owned outside this team** — its authority is KISA / KAIT, not us — and the rule is **stated to the operator on the screen**, which under FR-SNDC-013 means the screen and the enforcement must not be able to drift apart. D-S12 is exactly what drift looks like.

## Decision

**The list is loaded data, not compiled code, and an absent list is a startup failure.**

- A plain-text classpath resource, one number per line, `#` comments permitted — so each entry can carry *why* it is barred, which the current Javadoc does and a property value cannot.
- An optional external path property overrides the bundled resource, letting operations replace the list without a build.
- Bound once at startup into an immutable set behind `SenderNumberValidator`; the validator's signature and its `Result.BARRED` outcome do not change.
- **An empty, missing or unparseable list fails startup**, loudly, naming the resource.
- **112, 114, 119 and 1335 — the numbers the PM ruling names — cannot be removed by configuration at all.**

> **Amended 2026-08-20 (Sprint S2a).** This decision originally said those four values would be
> "asserted in the test suite, independent of what is configured". The implementation goes further:
> `BarredNumbers.MANDATORY` is unioned in on every load, so deleting them from the file does not
> un-bar them. A configuration edit that makes `119` registrable is better made **unrepresentable**
> than caught afterwards — and a test that catches it only catches it in an environment where the
> test runs. The suite still asserts the property; it is now asserting something the type guarantees.

> The list is deployment data and deliberately **not** a `*.properties`/`*.yml` file: a dedicated non-secret resource keeps it outside the class of files SEC-001 covers, and gets per-entry comments as a bonus.

## Alternatives considered

| Option | Verdict |
|--------|---------|
| **A — hardcoded `Set.of(...)` (the S1 state)** | Works, and is where the values are today. **Rejected** because a regulatory list owned by another body should not require a release train to correct, and because the ruling explicitly asked for the opposite |
| **B — loaded resource with an external override (chosen)** | **Accepted.** Change in minutes, keeps per-entry provenance, no new component |
| **C — a database table maintained through the portal** | **Rejected.** It adds a screen, a CRUD path, an audit surface and a migration for a 14-row list that changes perhaps yearly. Worse, it would make the rule editable **by the same role that registers numbers** — an operator who is barred from registering `1335` could delete the row that bars it. A control and the thing it constrains should not share an owner |
| **D — fetch a published KISA/KAIT feed at runtime** | **Rejected.** No stable published contract to bind to; it would put a network dependency, a timeout and a retry policy (CODE-002) in the path of a registration; and a feed outage would either open the rule or close registration. A list this small and this stable is a file |

## Consequences

**Positive.**
- FR-SNDC-013's bidirectional requirement becomes checkable: the screen's stated rules and the loaded set are both data, and one test compares them.
- Correcting the list after a regulatory change is a config change and a restart, not a release.
- Per-entry comments survive, so nobody has to re-derive why `182` is on the list.

**Negative.**
- Startup now depends on a resource being present and well-formed. That is deliberate — the alternative failure mode is a silently empty set, which is D-S12 reproduced by a typo. The failure is loud, immediate, and caught in every environment's boot.
- The list is set at startup; a change needs a restart. Acceptable for data that changes yearly, and reload would add a mutable control surface for no operational gain.

**What this does not fix.** Whether the 14 values are *complete* is unknown and unknowable from the code — RISK-S11 stays open, downgraded from "the list is unspecified" to "the list is now trivially correctable". The threat (T-T4) is mitigated by having enforcement at all; its residual is the list's coverage, which is a domain question.

## Verification

| Check | Test |
|-------|------|
| Each configured number is refused, with `Result.BARRED` | TC-S002-05/06, parameterised over the loaded set |
| 112, 114, 119, 1335 refused regardless of configuration | Fixed assertions, independent of the resource |
| Empty / missing / malformed list fails startup | TC-S002-29 |
| A number added to the list is barred after restart | TC-S002-30 |
| The screen's stated rules match the enforced rules | TC-S002-26 |
