# ADR-LOGIN-013: Unit test location and agent write scope

> **Status**: ACCEPTED
> **Date**: 2026-08-14
> **Author**: `architect` (Skill 04, step [A])
> **Approver**: PM
> **Related**: PROJECT-STRUCTURE.md §5 decision 1

---

## 1. Context

The harness gives `backend-developer` a `write_dirs` of `src/main/` and
`mapping/port-log/` only. But skill 04 §1[C] requires implementing agents to write
**"코드 + 단위 테스트 동시 작성"** — code and unit tests together — and §1[D] depends on
that pairing for its self-correction loop.

Maven places unit tests in `src/test/java`, which **no agent's `write_dirs` covers**.
So the layout as declared makes the required behaviour impossible.

- **Requirements**: harness §1[C], §1[D], §3; TEST-PLAN-LOGIN §2
- **Constraint**: write isolation exists to prevent two agents editing the same file

## 2. Decision

> Add `src/test/` to `backend-developer`'s `write_dirs`. Unit tests live beside the
> code they test, per Maven convention. `qa-engineer` retains `tests/` for
> integration, security and E2E suites.

### Key choices
- `src/test/java` — unit tests, owned by `backend-developer`, run by `mvn verify`
- `tests/integration`, `tests/security`, `tests/e2e` — owned by `qa-engineer`
- The boundary is **test level**, not test authorship: a unit test that mocks its
  collaborators belongs with the code; a test that needs a database, a browser or the
  whole application context belongs to QA

## 3. Considered alternatives

| # | Alternative | Advantages | Disadvantages | Adopted |
|---|-------------|-----------|---------------|---------|
| A | Add `src/test/` to `backend-developer` | Maven convention preserved; §1[C] satisfied; the self-correction loop works | Requires editing an agent definition | **Adopted** |
| B | Point `testSourceDirectory` at `tests/java/` | Write isolation untouched as declared | `backend-developer` could not write a single unit test. Every one would be requested from `qa-engineer`, breaking §1[C] and adding a round trip to the §1[D] loop | Not adopted |
| C | Grant `qa-engineer` `src/test/` as well | Both could write there | Two agents owning one directory is the exact collision the isolation rule exists to prevent | Not adopted |

> Option B keeps the letter of the isolation rule while breaking the rule that code
> and its tests are written together. The isolation rule exists to prevent collisions;
> co-located unit tests do not cause any, because `qa-engineer` has no reason to enter
> `src/test/`.

## 4. Consequences

### 4.1 Positive
- Unit tests are written in the same task as the code, so a failing test is fixed by
  the agent that caused it without a hand-off
- Maven and every IDE work without configuration
- The unit/integration split maps onto the coverage gates already in `pom.xml`

### 4.2 Negative
- One agent definition diverges from the harness's example table — recorded here so
  the divergence is traceable rather than surprising
- The `src/test/` vs `tests/` boundary is a judgement call at the margins; the "does
  it need infrastructure?" test is the tie-breaker

### 4.3 Follow-up
- [x] Amend `.claude/agents/backend-developer.md` `write_dirs`
- [ ] Note the split in the Sprint L1 retro so the boundary is reviewed in practice

## 5. Verification

| Item | Method | Frequency | Threshold |
|------|--------|-----------|-----------|
| No agent writes outside its scope | Review of changed paths per task | Every sprint | 0 violations |
| Unit tests run in the build | `mvn verify` | Every PR | Executed and gated |

## 6. References

- harness §1[C], §1[D], §3 · HARNESS-PROCESS-STANDARD §4.4
- [PROJECT-STRUCTURE.md](../PROJECT-STRUCTURE.md) §5 decision 1

---

## Change history

| Date | Version | Change | Author |
|------|---------|--------|--------|
| 2026-08-14 | 1.0 | Initial | `architect` |

---

**Approval**

| Date | Approver | Comment | Status |
|------|----------|---------|--------|
| 2026-08-14 | PM | Adopted at skeleton generation; confirm at the Sprint L1 gate | PENDING |
