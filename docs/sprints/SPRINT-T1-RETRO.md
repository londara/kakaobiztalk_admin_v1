# Sprint T1 Retrospective — 톡전송 내역

> **Sprint**: T1 · **Date**: 2026-08-19
> **Log**: [SPRINT-T1-LOG.md](SPRINT-T1-LOG.md)
> **7-dimension**: 81.6 / 100 — below threshold, escalated as a scope question (log §5)

---

## 1. What went well

**The copy-and-diverge defect class collapsed under one mechanism.** Eleven of this slice's thirty-four defects were one decision implemented two or three times. Three of them — D-T13, D-T15, D-T29 — were closed by making the decision exist once, not by fixing each copy. `BizTalkApiRegistry` answers link availability and channel routing; `TalkStatus` answers filter options and column labels. Neither can drift, because there is nothing to drift from.

**Splitting T1-01 was the highest-value half-hour of the sprint.** The analogous task in the 보고서 sprint (R1-01) was carried whole and unconfirmed. Separating the *behaviour* (we must not truncate — testable here) from the *data* (what the widths are — needs a DBA) meant `TransactionSerial` shipped with a WARN instead of a silent guess. A guess that announces itself is a different thing from a guess that truncates.

**Three deviations were recorded before the code was reported, not after.** Offset instead of keyset got its own ADR; the registry merge got amendments to the two ADRs it contradicted. Reviewing this sprint means reading four documents that already agree with the code, rather than reconstructing intent from a diff.

## 2. What did not

**The score is held down by one missing thing, and it is not code.** 보안 72, 테스트 커버리지 74, 성능 70 — all three are blocked on a database tier. Without Docker (RISK-T13) the negative-path suite, the reconciliation test, the paging property on data, and every performance number are unavailable. The controls exist; the tests that prove the refusals do not.

This was foreseen — RISK-T13 was written into the plan before the sprint — but foreseeing it did not reduce it. What the sprint did instead was refuse to paper over it: FR-AZ-T02 and NFR-SEC-AUTHZ-T01 are `PARTIAL` in the trace matrix rather than `IMPLEMENTED`, and TC-REG-02 is recorded as **retired-as-unreachable** rather than as passing.

**One task was in the wrong sprint.** D-T32 (the `EXCEL_RSLT`/`EXCEL_RLST` typo) was listed under T1, but the code containing it is not written until T2. Planning error, not an implementation miss — a defect cannot be closed in a sprint that does not build the thing it lives in.

**One plan item turned out to be the weaker of two options.** T1-02's startup check against `FT_OPENAPI_INFO` validates a configured code against the API master; the reconciliation query validates it against transactions that actually occurred. The second is strictly better and was already being built. Carrying the first would have been busywork.

## 3. Improvement actions

| # | Action | Owner | Due |
|---|--------|-------|-----|
| A1 | **Decide the DB tier question.** Either provision a PostgreSQL tier for T2 (embedded-postgres is Apache-2.0 and already this programme's fallback) or accept that D-T6, D-T9 and D-T18 ship verified-by-placement-and-boundary, recorded explicitly at G3 | PM + `architect` | Before T2 starts |
| A2 | **Raise the T1-01b query with a DBA** — three `length()` aggregates and one join-cardinality count. Unblocks FR-TLKD-009 | `data-model-designer` | T2 week 1, day 1 |
| A3 | **Add `io.zonky.test:embedded-postgres` as a test dependency** and land T1-01a's executable `lpad` demonstration with the first mapper integration test | `qa-engineer` | T2 week 1 |
| A4 | **Build the T1-13 fixture set** as the first task of the integration tier, before the tests that need it | `qa-engineer` | T2 week 1 |
| A5 | **Write the negative-path suite (T1-16) and endpoint inventory (T1-15) first in T2**, ahead of new feature code — they gate FR-AZ-T02's promotion from `PARTIAL` | `qa-engineer` + `security-auditor` | T2 week 1 |
| A6 | **Rewrite TC-REG-01** against transactions rather than the API master, and retire TC-REG-02 in TEST-PLAN-TALK to match ADR-TLK-024 §5 | `qa-engineer` | With A5 |
| A7 | **Move D-T32 to the T2 defect list** and correct sprint-T1-tasks.md so the record does not claim a defect that was never in scope | `team-leader` | Immediately |

## 4. Standing checks — what this sprint adds

The programme has accumulated one check per slice. This slice adds two, both about the relationship between a plan and the code that satisfies it.

**A startup validation between two collections often means the collections should be one.** The containment check between the two registries guarded a state that a better data shape makes unrepresentable. The check existed because the ADR reasoned about two lists — nothing in the requirement asked for two. Before writing a consistency check between two structures, ask whether one structure would make the inconsistency impossible.

**When a plan item and a thing already being built answer the same question, say which is stronger and drop the other.** T1-02's startup check and T1-14's reconciliation both ask "is this configured code real?". One asks the API master, the other asks the transaction log. Building both would have looked like thoroughness and delivered redundancy plus a weaker signal.

Carried from previous slices and still holding in this one:

- **문자내역** — defects sit in gaps between layers.
- **로그인** — check for deliberately disabled controls.
- **이용기관관리** — check for controls that exist only in the browser.
- **발신번호** — for every value that leaves the server and comes back, ask whether it came back in the same form.
- **이용기관 보고서** — for every branch on an environment or configuration property, ask what the other branch does and whether anyone has run it.
- **톡전송 내역 (spec)** — for every artifact, ask which screen it was written for; and for every commented-out client, ask whether its service was retired with it.

**The silent-success pattern held for a seventh time**, and this sprint met it as a *reporting* temptation rather than a code defect: five improvement loops would have raised 81.6 toward 90 without closing the gap the score is measuring. Reporting the number and escalating the cause is the same discipline the code applies — a wrong answer that announces itself beats a wrong answer that looks right.

---

# Addendum — the improvement actions, revisited the same day

Action A1 asked the PM to decide whether a PostgreSQL tier could be provisioned. **Attempting the check answered it, and disproved the risk the action was built on.**

## Actions now closed

| # | Action | Outcome |
|---|--------|---------|
| A1 | Decide the DB tier question | **CLOSED — no decision needed.** `io.zonky.test:embedded-postgres` (Apache-2.0) runs a real PostgreSQL as a process without Docker, and starts here. RISK-T13 closed with its reasoning error recorded |
| A3 | Add embedded-postgres, land T1-01a's executable `lpad` demonstration | **DONE** — `LpadTruncationTest`, 4 tests. `lpad('26081900142813',10,'0')` → `'2608190014'` proven against a real database |
| A4 | Build the T1-13 fixture set | **DONE** — built inside `TalkHistoryMapperIntegrationTest`: a 100-row tied-timestamp block, an out-of-scope API row, an institution absent from the master |
| A5 | Write T1-16 and T1-15 | **DONE** — `TalkHistoryAuthorizationTest` (7) and `TalkHistoryContractTest` (7). Neither needed a database, which was the second wrong claim |
| A6 | Rewrite TC-REG-01, retire TC-REG-02 | **DONE in code** — `TalkApiReconciliationTest` (7) covers the transaction-based check; TEST-PLAN-TALK still needs the text edit |
| A7 | Move D-T32 to T2 | **DONE** — correction noted in `sprint-T1-tasks.md` |

## Still open

| # | Action | Owner | Due |
|---|--------|-------|-----|
| A2 | The T1-01b DBA query — three `length()` aggregates and one join-cardinality count. Unblocks FR-TLKD-009 | `data-model-designer` | T2 week 1, day 1 |
| A8 | **NFR-PERF-T01 load test** against volume fixtures. The only dimension still materially short (성능 72) | `qa-engineer` | T2 |
| A9 | **Re-examine RISK-R01 and RISK-S13 on the same grounds.** Both carry the inference RISK-T13 got wrong, and two slices remain to inherit it | `architect` | Before T2 |
| A10 | Update TEST-PLAN-TALK §9 to match what is now verified executably (`lpad` yes; `decrypt`/`masking` still no) | `qa-engineer` | With A6 |

## What actually went wrong, and it was mine

The retrospective's §2 said the score was "held down by one missing thing, and it is not code." That was **wrong in a specific and instructive way**: the thing was not missing, and two of the three items I attributed to it never depended on it at all.

- `@WebMvcTest` runs without a `DataSource`. The pattern was already in this repository, in `CsrfIntegrationTest`, **with a Javadoc paragraph saying exactly that**. I recorded T1-16 and T1-15 as blocked while a working example sat in the same source tree.
- A mocked mapper verifies the whole reconciliation. T1-14's test needed nothing.
- `embedded-postgres` needs no Docker. One dependency line.

And the suite I had recorded as blocked found a real defect on its first run — **CR-T01**, a 500 where a 400 belonged, affecting every endpoint in the application with a required parameter, including one the 이용기관 보고서 slice has already shipped.

**Three risks carried the same untested inference across three slices.** RISK-S13 → RISK-R01 → RISK-T13, each restating *Docker is prohibited, therefore no PostgreSQL is reachable.* The first clause was always true; nobody tested the second. The 이용기관 보고서 retrospective then found two defects at exactly the boundary those risks declared unreachable — which is the cost of the inference, already paid once.

## The standing check this replaces

The main retrospective's §4 offered two checks about plans and code. This addendum adds one that matters more, and it supersedes the framing of §2:

**When a risk register says a capability is unavailable, ask what was actually tried.** A risk is written once and then cited; citation is not verification. An inference recorded as a constraint stops being questioned, and the register starts protecting the assumption instead of the project.

That is the same shape as the silent-success defect class this slice was built to remove — *the system reports that something is so, and nobody checks* — applied to our own documents rather than to the legacy's code. Six slices of finding it in `IRIS_ADMIN` did not stop us writing it into three risk registers.
