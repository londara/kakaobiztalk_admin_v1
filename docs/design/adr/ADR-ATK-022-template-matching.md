# ADR-ATK-022 — Template matching algorithm

> **Status**: ACCEPTED
> **Date**: 2026-08-18
> **Slice**: 알림톡 템플릿/발송 (screen 61)
> **Decides**: how FR-ATV-004…008 are implemented; where validation runs
> **Requirements**: FR-ATV-001…008, FR-ATT-002, CONST-DATA-A03
> **Related**: [ADR-ATK-021](ADR-ATK-021-outbound-contract-conformance.md), PM ruling AMB-A00b

---

## Context

The legacy validator is wrong, and wrong in a way that matters: it reports **valid** content as invalid.

`validateTemplateStrict()` walks the template, and after each `#{…}` variable it advances the content cursor to the *first occurrence* of the next literal character:

```js
while (idxContent < content.length && content[idxContent] !== nextFixedChar) idxContent++;
```

When the substituted value itself contains that character, the scan halts inside the value and the following literal comparison fails. Template `#{name}님 안녕` with content `김님철수님 안녕` is reported as a mismatch at the space (D-A6). Korean honorific and particle characters — `님`, `이`, `가`, `은`, `는` — are common inside names and equally common as the literal immediately following a variable, so this is a routine case, not a corner one.

It also accepts a zero-length substitution (`#{a}b` matches `b`), reports only the first divergence, and accepts both `#{…}` and `${…}` though only the former is Kakao's.

PM ruled **AMB-A00b: correct it.** The behavioural break is accepted: inputs the legacy rejected will now pass.

Two facts reframe where this belongs. First, `KKB_MSG_TMPL.TEMPLATE_MSG` **already stores the template body**, keyed by `(IS_CD, TEMPLATE_CODE)` — the two values the composer collects. The operator has been pasting in by hand something the database already holds (D-A16). Second, the check has value only where it can prevent something: at send time, before the vendor rejects the message.

## Decision

**The template compiles to a regular expression; matching is delegated to a correct matcher rather than hand-walked.**

```
TEMPLATE_MSG  ──▶  tokenize  ──▶  [LITERAL, VARIABLE, LITERAL, …]  ──▶  Pattern
                                        │
                     LITERAL  ──▶  Pattern.quote(text)
                     VARIABLE ──▶  (?<name>.+?)      ← lazy, one-or-more
```

Three properties follow directly, none of which the legacy had:

- **Correctness on E-1.** A lazy `.+?` followed by a quoted literal backtracks. `(?<name>.+?)님\ 안녕` against `김님철수님 안녕` first tries `김`, fails to match `님 안녕` at `님철수…`, and backtracks until `name = 김님철수`. The legacy's single forward scan cannot backtrack, which is the entire defect.
- **FR-ATV-005 for free.** `+?` rather than `*?` makes an empty substitution a non-match by construction.
- **Named groups give the operator the extracted values**, so a divergence report can say *what* it read for each variable — the diagnostic the legacy's character position never provided (NFR-USE-A03).

`Pattern.quote()` on every literal segment is not incidental: template bodies are operator-authored text containing `.`, `(`, `[`, `*` and `?`, any of which would silently change the pattern's meaning. This is the injection surface of the design and it is closed by construction.

**All divergences, not the first (FR-ATV-006).** On a whole-pattern mismatch the matcher re-runs incrementally over the token list, matching prefix-by-prefix to locate every point at which the content stops conforming, and reports each with its token and offset.

**One implementation, two entry points (FR-ATV-007).** `TemplateMatcher` is a pure domain class. `AlimTalkSendService` calls it with a body loaded from `KKB_MSG_TMPL`; the manual 검증 tab calls the same class through a validate-only endpoint with an operator-supplied body. A manual result and an automatic result cannot disagree, because there is one matcher.

**Variable syntax (FR-ATV-008): `#{…}` only.** `${…}` is rejected with an explicit message naming the correct form. Silently accepting a syntax the vendor does not is how a template validates locally and fails downstream.

**Compiled patterns are cached** by `(IS_CD, TEMPLATE_CODE, body hash)`. Compilation is not free and the same template is matched on every send.

## Alternatives considered

| Option | Mechanism | Verdict |
|--------|-----------|---------|
| **A — port the legacy walk, bug included** | Byte-for-byte parity | **Rejected by PM (AMB-A00b).** Preserves a validator that rejects valid Korean messages |
| **B — fix the legacy walk in place** | Add backtracking to the hand-written scanner | Rejected. Adding backtracking to a hand-rolled character scanner *is* writing a regex engine, with our bugs instead of the JDK's, in a file whose original author already got the single-pass version wrong |
| **C — compile to a regex (chosen)** | Tokenize, quote literals, lazy-match variables | **Accepted.** Correct by construction, and correctness is delegated to a matcher with two decades of testing behind it |
| **D — token-by-token parser with explicit backtracking** | A small recursive matcher over the token list | Rejected as the primary matcher — it is option C's semantics reimplemented. **Retained for the multi-divergence report**, where incremental prefix matching genuinely needs token-level control that a single `Pattern` cannot express |
| **E — diff-based (LCS) comparison** | Report a character diff between template and content | Rejected. Produces a plausible-looking diff for content that does not conform at all, because it has no concept of a variable |

## Consequences

**Positive.**
- Valid Korean messages stop being rejected. The failing case is a name containing a particle character, which is ordinary.
- Validation moves to where it prevents a vendor rejection (FR-ATV-001), and the operator stops hand-copying a body the database already holds.
- The regex-injection surface in operator-authored template text is closed by `Pattern.quote()`.

**Negative.**
- **Catastrophic backtracking is a real hazard.** A template of many adjacent variables (`#{a}#{b}#{c}…`) against a long non-matching content can blow up exponentially. Mitigated by a match timeout, a cap on variable count per template, and a rejection of *adjacent* variables with no intervening literal — which are ambiguous by definition and cannot be matched meaningfully anyway. Tracked as RISK-A10.
- **Ambiguity is inherent, not introduced.** `#{a}-#{b}` against `1-2-3` has three valid readings; lazy matching picks `a=1, b=2-3` deterministically. That is a defensible choice, not a correct one — no algorithm can recover the author's intent from the template alone.
- Inputs the legacy rejected now pass. **QA must not file TC-A004-02/03 as parity regressions**; they assert the fix.

## Verification

| Check | Test |
|-------|------|
| Variable value containing the next literal | TC-A004-02 (`#{name}님 안녕` / `김님철수님 안녕`) |
| Value containing the delimiter | TC-A004-03 |
| Genuine mismatch still detected | TC-A004-04 |
| Empty substitution rejected | TC-A004-05 |
| All divergences reported | TC-A004-06 |
| `${…}` handled per FR-ATV-008 | TC-A004-10 |
| Regex metacharacters in literal text | New: template `가격 (1+1) 행사?` matches itself |
| Backtracking bounded | New: 20 adjacent variables against 4 KB of non-matching content completes within the timeout |
| Manual and automatic agree | TC-A004-13 |
| Blocking at send | TC-A004-14 |
