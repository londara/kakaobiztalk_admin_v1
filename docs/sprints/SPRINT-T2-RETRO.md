# Sprint T2 Retrospective — 톡전송 내역 (slice complete)

> **Sprint**: T2 · **Date**: 2026-08-19
> **Log**: [SPRINT-T2-LOG.md](SPRINT-T2-LOG.md)
> **7-dimension**: 90.6 / 100 — above threshold
> **Slice status**: 32 of 34 defects closed; 63 of 63 requirements implemented or explicitly partial

---

## 1. The slice, in one table

| | T1 | T2 | Slice |
|---|---|---|---|
| Defects closed | 12 | 20 | **32 of 34** |
| Backend tests added | 81 | 47 | 128 |
| Frontend tests added | 10 | 4 | 14 |
| 7-dimension | 81.6 → 89.7 | **90.6** | — |
| Deviations recorded as ADR/amendment | 3 | 3 | 6 |

The two defects not "closed" by a fix: **D-T3** was *removed* — `biztalk_admin_30_l002`'s capability is not carried forward, so there is nothing to fix — and **D-T24** closed in T1.

## 2. What went well

**The three Criticals this sprint owned closed with executable evidence.** D-T1 by a set equality over nine filter combinations, D-T5 by a cross-institution lookup returning null against real PostgreSQL, D-T4 by there being no path from user input to a header. None of the three rests on prose.

**One decision closed eleven defects across both sprints.** The copy-and-diverge class — D-T7, D-T8, D-T13, D-T15, D-T18, D-T29 and more — was one decision implemented two or three times. `BizTalkApiRegistry`, `TalkStatus`, `TalkResult` and the shared `TalkHistoryCriteria` each replaced N implementations with one and N consumers. Not one of those defects was fixed by fixing a copy.

**The set-equality test is the right shape for a compositional defect, and it is worth naming as a technique.** D-T1 had no layer disagreeing with its neighbour: a valid query, a working function, a contract declaring twelve parameters — and a composition that extracted every institution's phone numbers in plaintext. Only an assertion about *meaning* finds that. `exported(filters) == listed(filters)` is one line and it makes the requirement unfalsifiable-by-drift.

## 3. What did not

**I made the same mistake one document later, and the document was the one that recorded the lesson.** ADR-TLK-027 exists because I checked whether the two slices' message tables were shared and found they were not. Three paragraphs earlier in the same ADR I asserted the export writer *was* shared — and it did not exist. Apache POI was not even in the POM.

That is not a lapse of attention. It is evidence that **verification is per-claim, not per-document**: having just been rewarded for checking one assumption made me less likely to check the adjacent one, not more.

**"Unverifiable" got the treatment "unavailable" got in T1, one sprint late.** TEST-PLAN-TALK §9 recorded D-T6 and D-T18 as verifiable only by placement. That conflated two different things — what a function *computes* versus the *shape of the SQL around it*. D-T18 is a column-naming collision and was always executable with a one-line stub. Ten minutes, once the question was asked.

T1's addendum found a risk register citing an untested inference. This sprint found a test plan doing the same. **Both documents were mine, written in the last two days, in a slice whose entire subject is statements recorded as fact and then cited rather than checked.**

## 4. Improvement actions

| # | Action | Owner | Due |
|---|--------|-------|-----|
| B1 | **NFR-PERF-T01/T02 load run + NFR-SCALE-T01 heap profile.** The one materially-short dimension (성능 74). The DB tier exists; this needs volume fixtures and a measurement | `qa-engineer` | Before G3 |
| B2 | **`@WebMvcTest` on `TalkExportController`** asserting the emitted `Content-Disposition` and the single content type. FR-TLKX-003/004 are `PARTIAL` until then — the properties hold structurally but nothing asserts the bytes on the wire | `qa-engineer` | Before G3 |
| B3 | **Carry the stub-function technique to the 발신번호 slice.** RISK-S13's D-S1 may split the same way: if any part of it is a SQL-shape property rather than a cryptographic one, it is executable now. Addendum already recorded in that register | `architect` | Next slice planning |
| B4 | **T1-01b DBA query.** Still outstanding. FR-TLKD-009 is `IMPLEMENTED` because losslessness is proven at 10/14/20 characters, but the *stored widths* remain an assumption that announces itself via WARN | `data-model-designer` | Before G3 |
| B5 | **`biztalk_admin_30_l002` retirement decision.** A live CVSS 7.7 endpoint with no caller. Unchanged since T1; it is an operations call | Operations + PM | Before cutover |
| B6 | **Update TEST-PLAN-TALK §9** to record what moved from placement-verified to execution-verified, and state precisely what remains unverifiable (the real `masking()` output) | `qa-engineer` | With B2 |
| B7 | **Update ADR-RPT-023 and DEV-PLAN-REPORT** to note that `infra.excel.StreamingWorkbookWriter` now exists, built here. 보고서 R2 consumes it rather than building it | `architect` | Next slice planning |

## 5. Standing checks — the slice's contribution

Six slices have each added one. This slice adds two, and both are about our own documents rather than the legacy's code — which is itself the finding.

**Verification is per-claim, not per-document.** A document that correctly checks one assumption gives no assurance about the next sentence. ADR-TLK-027 proved that within three paragraphs of itself.

**Treat "cannot" with the same suspicion as "does".** The programme already asks what a branch does and whether anyone ran it. Add: when a document says a thing is unavailable, unverifiable or impossible, ask what was actually tried, and ask whether the claim is really about one thing or two. Three risk registers and one test plan each failed this in the last two days.

Carried and still holding:

- **문자내역** — defects sit in gaps between layers.
- **로그인** — check for deliberately disabled controls.
- **이용기관관리** — check for controls that exist only in the browser.
- **발신번호** — for every value that leaves the server and comes back, ask whether it came back in the same form.
- **이용기관 보고서** — for every branch on an environment or configuration property, ask what the other branch does and whether anyone has run it.
- **톡전송 내역 (spec)** — for every artifact, ask which screen it was written for; for every commented-out client, ask whether its service was retired with it.
- **톡전송 내역 (T1)** — a startup validation between two collections often means the collections should be one; when a risk says a capability is unavailable, ask what was actually tried.

## 6. Gate position

**Ready for G3 entry with two conditions**, both measurements rather than code:

1. B1 — the load run. 성능 cannot honestly rise without it.
2. B2 — the header assertions. FR-TLKX-003/004 stay `PARTIAL` until the wire is asserted.

**Not blocking G3, and needs saying at it:** the real `masking()` output remains unverifiable in this environment, so D-T6 ships **verified-at-the-boundary-and-by-placement**. That is a smaller residual than the plan expected — D-T9 and D-T18 were recovered — but it is a residual, and G3 should record it as one rather than inherit the plan's original wording.

G1 and G2 both remain unsigned. Six slices have now shipped ahead of their gates; that is a programme-level pattern worth a decision rather than a sixth repetition.
