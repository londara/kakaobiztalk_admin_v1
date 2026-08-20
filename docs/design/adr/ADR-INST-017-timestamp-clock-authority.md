# ADR-INST-017 — Clock authority for `RGDT` / `LAST_AMDT`

> **Status**: ACCEPTED
> **Date**: 2026-08-20
> **Deciders**: `architect`, `security-auditor`
> **Supersedes**: nothing · **Related**: [ADR-INST-016](ADR-INST-016-legacy-coexistence.md) (coexistence), ADR-006 (audit logging), ADR-001 (stack)
> **Resolves**: CONFLICT-I03 · **Requirement**: FR-INSTC-013, FR-INSTC-006

---

## 1. Context

`FT_FTIS_INFO.RGDT` and `.LAST_AMDT` are not timestamps. They are **`VARCHAR` wall-clock strings** in the form `YYYYMMDDHH24MISS`, and every row now in the table was written by the legacy runtime through the database:

```sql
-- IDO.KKB_FT_FTIS_INFO_C001
LAST_AMDT = to_char(now(),'YYYYMMDD24MISS')   -- the missing HH is D-I9
```

Two facts collide.

| Fact | Where it comes from |
|------|--------------------|
| The programme's `Clock` bean is `Clock.systemUTC()` | Delivered in the 로그인 slice. Deliberate: audit and session times must be comparable across instances, so the application never depends on a server's local zone (ADR-006) |
| These two columns hold **local wall-clock** strings, written by a second live writer | `IDO.KKB_FT_FTIS_INFO_C001`, still in service under ADR-INST-016 |

Formatting these columns from the application clock would write `20260820`**`01`**`3000` where the legacy writes `20260820`**`10`**`3000` for the same instant. Both rows would sit in one column with no marker saying which epoch each belongs to. Nothing would fail: the strings are well-formed, the same length, and sort — **incorrectly** — against each other. The list screen would show a 수정일시 nine hours in the past, and an operator comparing two rows would draw the wrong conclusion about which was edited last.

This is not a defect in either decision. It is the ordinary cost of two writers on one column, and it stayed invisible while this slice was read-only.

## 2. Decision

**These two columns are written by the database clock, in SQL, and by nothing else.**

```sql
LAST_AMDT = to_char(now(),'YYYYMMDDHH24MISS')   -- the legacy expression, with HH restored
```

| Concern | Decision |
|---------|----------|
| `RGDT` / `LAST_AMDT` | `to_char(now(),'YYYYMMDDHH24MISS')` in the mapper XML. The application `Clock` is **not** used for them |
| Everything else in this slice — audit records, session times, scheduling | Unchanged: the injected UTC `Clock`, as everywhere else in the programme |
| The `HH` bug (D-I9) | Fixed here. `to_char(now(),'YYYYMMDD24MISS')` wrote a literal `24` in the hour position on every row the legacy ever created |
| Existing malformed values | **Left as they are.** They are displayed verbatim (FR-INST-008); repairing them at render time would hide the data problem again |

### 2.1 Why not convert in the application

`ZoneId.of("Asia/Seoul")` on the UTC clock would produce the same string as `now()` — while the two systems run in one deployment, in one zone, with one DST-free calendar. It would also add a **second place** where the zone of these columns is decided, and the two places would be free to disagree after any infrastructure change. The database already holds the authority for every existing row; leaving it there keeps the number of deciders at one.

The counter-argument is testability: an expression in SQL cannot be pinned by a unit test the way an injected `Clock` can. That is accepted, and the verification below is shaped around it.

### 2.2 What this does not decide

It says nothing about the audit trail. An audit record for the same edit is written on the **UTC** clock, by design, and the two values differ by the offset. That is correct: the audit store is this system's, ordered globally; the column is the legacy's, ordered locally. Anyone reconciling them needs to know the offset, which is why this ADR exists as a document rather than a comment.

## 3. Considered alternatives

| # | Option | Consistent with legacy rows | Deciders of the zone | Unit-testable | Verdict |
|---|--------|----------------------------|----------------------|---------------|---------|
| **A** | **`to_char(now(),…)` in SQL** | **Yes** | **1 (the database)** | No — asserted on the statement, not the value | **SELECTED** |
| B | Format in the service from the injected UTC `Clock` | **No — nine hours out** | 1 | Yes | Rejected. Correct-looking code producing wrong data is the worst of the three |
| C | Format in the service, converting to `Asia/Seoul` | Yes, while the assumption holds | 2 (app **and** DB, free to diverge) | Yes | Rejected — buys testability with a duplicated decision, on a column a system outside our boundary keeps writing |

> Option B is what a reviewer would expect to see, because it is what the rest of the codebase does. That is precisely why the decision is recorded: the consistent-looking choice is the wrong one **for these two columns and no others**.

## 4. Consequences

**Good**
- Portal-written and legacy-written rows are directly comparable in the column they share — which ADR-INST-016 requires for the whole coexistence window.
- D-I9 is fixed at the only place that writes the value.
- The application clock keeps its single, unambiguous meaning: UTC, for this system's own records.

**Bad / accepted**
- Two clocks are in play for one edit (DB for the row, UTC for the audit record). Documented here and in `InstitutionAdminMapper`.
- The value cannot be pinned in a unit test. Verified as a statement-shape assertion plus a data check (§5), the same substitute this environment already uses for mapper SQL (RISK-I09 — no Docker).
- If the portal is ever deployed in a different zone from the database, this becomes wrong. The mitigation is that it becomes wrong **visibly** — new rows would be offset from old ones in the same column, which is exactly what a data check detects.

## 5. Verification

| Check | Method | Requirement |
|-------|--------|-------------|
| The update statement uses `to_char(now(),'YYYYMMDDHH24MISS')` | Mapper XML assertion (`InstitutionAdminMapperXmlTest`) | FR-INSTC-013 |
| No `HH`-less pattern anywhere in the module | Same test, asserting `YYYYMMDD24MISS` is absent | D-I9 |
| The application `Clock` is not used for these columns | Code review + the update command carrying no timestamp field | FR-INSTC-013 |
| Portal-written rows sort correctly against legacy rows | Data check at cutover — one edited row compared with its neighbours | FR-INSTC-006 |

## 6. Change history

| Date | Version | Change | Author |
|------|---------|--------|--------|
| 2026-08-20 | 1.0 | Initial — resolves CONFLICT-I03, raised by the screen-01 gap pass | `architect` |

## 7. Approval

| Date | Approver | Comment | Status |
|------|----------|---------|--------|
| 2026-08-20 | Architect | Accepted. Narrow by construction: two named columns, one statement, one recorded reason | **ACCEPTED** |
| 2026-08-20 | Security | No credential or PII implication. Note that audit and row timestamps differ by the zone offset — reconciliation guidance is in §2.2 | **ACCEPTED** |
