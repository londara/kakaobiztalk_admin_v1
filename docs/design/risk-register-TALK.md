# Risk Register — 톡전송 내역 (BizTalk Transaction History)

> **Version**: 1.0
> **Date**: 2026-08-19
> **Predecessor**: [REQUIREMENTS-SPEC-TALK.md](../requirements/REQUIREMENTS-SPEC-TALK.md)
> **Companion**: [DEV-PLAN-TALK.md](DEV-PLAN-TALK.md), [threat-model-TALK.md](threat-model-TALK.md)

---

## Summary

| ID | Title | Area | Impact | Likelihood | Strategy |
|----|-------|------|--------|-----------|----------|
| RISK-T01 | Under-inclusion by API misclassification is silent | 기술 | H | M | 완화 |
| RISK-T02 | The defective export may already have been used, and it leaves a file | 보안 | H | M | 완화 + 전가 |
| RISK-T03 | Legacy endpoints stay live through coexistence, including an unmasked-PII one | 보안 | H | M | 완화 |
| RISK-T04 | The transaction ↔ message relationship is inferred, not declared | 기술 | M | H | 완화 |
| RISK-T05 | Removing non-talk rows breaks an unrecorded operator workflow | 외부 | M | M | 수용 + 소통 |
| RISK-T06 | `PrincipalScope` refactor touches an already-approved slice | 기술 | M | L | 완화 |
| RISK-T07 | `FT_APITR_HSTR` is shared with the whole API estate | 외부 | M | M | 회피 |
| RISK-T08 | Tied timestamps make paging correctness load-dependent | 기술 | M | H | 완화 |
| RISK-T09 | The export row ceiling cannot be fixed until measured | 일정 | L | H | 완화 |
| RISK-T10 | Staging lacks realistic talk-message volume and variety | 일정 | M | M | 완화 |
| RISK-T11 | Audit volume outruns an undecided retention policy | 운영 | M | M | 전가 |
| RISK-T12 | `AOA_ADMIN` carries the same three screens on the same data | 외부 | M | H | 전가 |
| ~~RISK-T13~~ | ~~No Docker: the mapper↔DB boundary is not reachable~~ — **premise disproven, CLOSED** | 기술 | L | — | 완화 (완료) |

---

## RISK-T01 — Under-inclusion by API misclassification is silent

**영역** 기술 · **영향** H · **확률** M · **전략** 완화

SCOPE-T01 narrows the screen to BizTalk API codes, which requires a definition the legacy never had. A source scan yields five literals, but `ADV_COM_GET_STATUS` is in production and in no source file, so codes exist that the codebase cannot enumerate.

The asymmetry is what makes this the slice's leading risk. **Over-inclusion is what the legacy does and it is visible** — an operator sees a row that does not belong and says so. **Under-inclusion is invisible** — a real BizTalk transaction simply is not there, and nothing indicates a filter removed it. The rebuild trades a loud failure for a quiet one, which is the exact shape this programme has now found in six consecutive slices.

**대응 계획.** Config-held allow-list with startup validation (a configured code that does not exist in `FT_OPENAPI_INFO` logs at WARN, named), plus a **standing reconciliation report** counting transactions per unclassified `API_SVC_CD` whose institution has BizTalk service registered. Any non-zero count is a candidate the list is missing. Task T1-14; [ADR-TLK-024](adr/ADR-TLK-024-biztalk-api-classification.md).
**담당** `architect` + domain owner · **모니터링** T1-14 output weekly through both sprints, then monthly

## RISK-T02 — The defective export may already have been used, and it leaves a file

**영역** 보안 · **영향** H · **확률** M · **전략** 완화 + 전가

D-T1 has been reachable for the life of the screen: one button press returns every institution's 알림톡 and 친구톡 messages in the time window with `decrypt()`ed, unmasked phone numbers. CVSS 8.6 (TM-T02).

This differs from every previous slice's exposure in one respect that matters. The 보고서 slice's D-R1 was an unauthenticated *read* — the question was who looked. Here the output is **an .xlsx file on someone's disk**. Files are copied, mailed and kept. No control this project can build recalls one.

**대응 계획.** Within the slice: the export consumes the list's own scoped, masked iterator (FR-TLKX-001, FR-TLKX-008) and is audited with its row count (FR-TLKX-007). Outside it, **transferred to 정보보호 as OI-T01**: review access logs for `biztalk_admin_30_spreadsheet` invocations, and treat any finding as a disclosure event with its own process. Requesting that the legacy export be disabled ahead of this project's cutover is part of the same referral — see RISK-T03.
**담당** PM + 정보보호 · **모니터링** OI-T01 closure before G3

## RISK-T03 — Legacy endpoints stay live through coexistence, including an unmasked-PII one

**영역** 보안 · **영향** H · **확률** M · **전략** 완화

FR-AZ-T06 removes `biztalk_admin_30_l002` from the new application. It does not remove it from `IRIS_ADMIN`, where it remains registered, authenticated and reachable, returning unmasked recipient and sender numbers over an arbitrary date range with no institution requirement and no pagination (D-T3, CVSS 7.7). The same is true of the defective export.

Building the new screen does not reduce this exposure by one day. Only disabling the legacy services does.

**대응 계획.** `biztalk_admin_30_l002` **has no caller** — its only client was commented out — so setting `useYn=N` disables it at near-zero operational risk and needs no code change. That is proposed to operations as an action independent of this project's schedule. The export cannot be disabled the same way (it is in use), so it is bounded by RISK-T02's log review until cutover.
**담당** Operations + PM · **모니터링** decision recorded before Sprint T2 ends

## RISK-T04 — The transaction ↔ message relationship is inferred, not declared

**영역** 기술 · **영향** M · **확률** H · **전략** 완화

`FT_APITR_HSTR.IS_TUNO` and `KKO_MSG.SERIALNUM` are treated as the same identifier by three code paths that normalise it three different ways, one of them lossy. There is no foreign key and no documentation. Whether the join is equality at all, and whether one transaction maps to one message or many, are inferences.

**대응 계획.** Tasks **T1-01a** (the `lpad` truncation mechanism, executable here) and **T1-01b** (a DBA-run query) measure `max(length(IS_TUNO))`, the `SERIALNUM` width range and the join cardinality on production-like data before any mapper is written, and configures `TransactionSerial` from the result ([ADR-TLK-025](adr/ADR-TLK-025-transaction-message-identity.md)). FR-TLKD-009 is marked `BLOCKED-AMB-T04` in the matrix and is unblocked by that task, not by a meeting. If the relationship turns out not to be plain equality, the type absorbs it and nothing above the domain boundary changes.
**담당** `architect` + `data-model-designer` · **모니터링** T1-01a completes in week one; T1-01b is requested on day one and the matrix status flips on its result (see RISK-T13)

## RISK-T05 — Removing non-talk rows breaks an unrecorded operator workflow

**영역** 외부 · **영향** M · **확률** M · **전략** 수용 + 소통

The screen has returned every API's transactions for years. Some operator may use it as the general API log, because it is the one that exists.

**대응 계획.** Accepted by ruling SCOPE-T01. The reconciliation report (RISK-T01) doubles as the evidence for the conversation: it states precisely which codes and how many transactions the classification excludes, so the discussion is quantified rather than anecdotal. If the workflow proves real, the answer is a separate general-API screen — **not** a widening of this one, which would re-open the naming conflict CONFLICT-T02 settled.
**담당** PM + operator team lead · **모니터링** communicated at cutover; reviewed 30 days after

## RISK-T06 — `PrincipalScope` refactor touches an already-approved slice

**영역** 기술 · **영향** M · **확률** L · **전략** 완화

`ReportScope` moves from the 보고서 slice to `common.tenant`. That slice is G1-approved and its authorization behaviour is settled; a regression here is an authorization regression on shipped code.

**대응 계획.** Task T1-04 moves the class and **re-runs the 보고서 slice's authorization tests unchanged** against it. If any fails, the generalisation is wrong and is reverted rather than adjusted — the fallback is a duplicate class with a comment, which is worse code and no risk. The alternative to the move is two implementations of one authorization rule, which is the one duplication this programme should not accept.
**담당** `architect` + `security-auditor` · **모니터링** T1-04 gate; 보고서 suite green before T1-08 starts

## RISK-T07 — `FT_APITR_HSTR` is shared with the whole API estate

**영역** 외부 · **영향** M · **확률** M · **전략** 회피

The table this screen reads is the transaction log for every fintech API, owned by the API platform, not by this project. An index proposal or a query-shape change made for this screen affects consumers this team does not know about — and the table holds account and card columns for all of them.

**대응 계획.** Read-only by CONST-DATA-T01; nine of twenty-five columns projected by CONST-SEC-T01. Any index proposal arising from NFR-PERF-T01 is reviewed with the API platform owner **before G2**, not after a load test. No DDL (CONST-DATA-T02).
**담당** `architect` + DBA · **모니터링** at the NFR-PERF-T01 load test

## RISK-T08 — Tied timestamps make paging correctness load-dependent

**영역** 기술 · **영향** M · **확률** H · **전략** 완화

The production screenshot shows eleven consecutive rows sharing `2026-08-19 11:25:04`. Ties are not an edge case on this screen — bulk API calls arrive in the same second by design — so an untied sort under paging duplicates and drops rows routinely (D-T10). A test written against sparse staging data would never see it.

**대응 계획.** Total order `(RGDT DESC, IS_TUNO DESC)` with keyset pagination. The regression test uses a **deliberately tied fixture** — a hundred rows on one timestamp — and asserts the union of all pages equals the full set exactly once. This is a property test, not a sample.
**담당** `qa-engineer` · **모니터링** TC-T001-06; re-run at the load test with realistic tie density

## RISK-T09 — The export row ceiling cannot be fixed until measured

**영역** 일정 · **영향** L · **확률** H · **전략** 완화

FR-TLKX-005 requires a hard row cap; the correct number depends on row width and generation time, neither of which is known before the writer runs against real data.

**대응 계획.** Provisional 100,000 rows (the 보고서 slice's figure, and this slice's rows are narrower), fixed by the NFR-PERF-T02 load test before G3. Refusal is a 400 naming a range that would fit — **never a truncated file**, which would be the export form of the silent-success failure this programme has met six times.
**담당** `qa-engineer` · **모니터링** T2-09

## RISK-T10 — Staging lacks realistic talk-message volume and variety

**영역** 일정 · **영향** M · **확률** M · **전략** 완화

Three properties need real data to test at all: tied-timestamp density (RISK-T08), the serial-width distribution (RISK-T04), and the mix of API service codes that exercises `TalkDetailRegistry`'s `none` branch. Staging is unlikely to have any of them naturally.

**대응 계획.** Synthesise fixtures deliberately rather than hoping for coverage — a tied-timestamp block, serials at 10/14/20 characters, and at least one transaction per registry outcome including `none` and `ADV_KKO_AT_SEND2`. Fixture construction is an explicit task (T1-13), not a side effect of writing tests.
**담당** `qa-engineer` · **모니터링** T1-13 fixture review before T2 begins

## RISK-T11 — Audit volume outruns an undecided retention policy

**영역** 운영 · **영향** M · **확률** M · **전략** 전가

FR-AZ-T05 audits every list query, every detail open and every export. An operator working a support queue generates a steady stream of detail opens. NFR-OPS-AUDIT-T01's retention term is still **OI-02**, open since Skill 01.

**대응 계획.** Transferred: OI-02 is a PM/정보보호 decision, and the matrix records NFR-OPS-AUDIT-T01 as `BLOCKED-OI-02` rather than assuming a term. Estimated event volume is provided to that decision from the T2-09 load run so the choice is informed by a number.
**담당** PM + 정보보호 · **모니터링** OI-02 before G3

## RISK-T12 — `AOA_ADMIN` carries the same three screens on the same data

**영역** 외부 · **영향** M · **확률** H · **전략** 전가

`AOA_ADMIN` contains `biztalk_admin_30`, `_30_l001`, `_30_l002`, `_30_spreadsheet` and `_31` in its own `aoa_admin` package, against the same database. It does **not** carry screen 32 or the `_30_l003` API selector, so it is an older fork of the same screens rather than a copy of the current ones — which means its defects are this slice's defect set minus the 32-specific ones, plus whatever has since been fixed here and not there. Every defect fixed by this project — including D-T1 and D-T3, both of which are present — persists there untouched.

**대응 계획.** Out of scope, as in the 발신번호 and 보고서 slices. Raised explicitly so it is a recorded decision rather than an oversight: the defect list in REQUIREMENTS-SPEC-TALK §1.6 is handed to the `AOA_ADMIN` owner, with D-T1 and D-T3 flagged as the two that carry PII exposure. Whether they act is theirs to decide.
**담당** PM · **모니터링** handover recorded before G3

## RISK-T13 — ~~No Docker: the mapper↔DB boundary is not reachable~~ — PREMISE DISPROVEN 2026-08-19

**영역** 기술 · **영향** H → **L** · **확률** H → **CLOSED** · **전략** 완화 (완료)

> **CLOSED 2026-08-19.** This risk rested on an inference that turned out to be false: *Docker is prohibited, therefore no PostgreSQL is reachable.* The first clause is true; the second does not follow. `io.zonky.test:embedded-postgres` (Apache-2.0) launches a **real PostgreSQL binary as a process**, needs no Docker, and **starts successfully in this environment** — verified by `LpadTruncationTest` and `TalkHistoryMapperIntegrationTest`, both green.
>
> Everything below was written before that was checked. It is left in place unedited because the reasoning error is the finding: this risk, and RISK-R01 and RISK-S13 before it, **carried the inference across three slices without anyone testing the second clause**. The 이용기관 보고서 retrospective found two defects at exactly the boundary this risk declared unreachable. The tier cost one dependency line and about twenty minutes.
>
> **What remains true:** `decrypt()` and `masking()` are site-defined functions and still cannot be replayed here, so D-T6 and D-T18 remain verified-by-placement-and-boundary (TEST-PLAN-TALK §9). `lpad` is near-standard and **is** now executably verified. One of three headline data defects was genuinely blocked; two were not.
>
> **Programme action:** RISK-R01 and RISK-S13 should be re-examined on the same grounds before the two remaining slices inherit the inference a fourth time.

Docker is prohibited, so Testcontainers is permanently unavailable (inherited RISK-S13/RISK-R01). Two consequences bite this slice specifically, and both are already realised rather than prospective.

**The probe cannot run here.** T1-01b needs production-like data to fix `TransactionSerial`'s widths. Its direct analogue, R1-01 in the 이용기관 보고서 sprint, was **carried unconfirmed** for exactly this reason — and that sprint's retrospective recorded two defects living precisely at the mapper↔DB boundary that no reachable tier exercises. Splitting T1-01 into a behaviour half and a data half is what stops the same outcome arriving unannounced.

**Three of this slice's defects sit on DB functions this environment cannot execute.** `decrypt()` and `masking()` are database functions whose definitions are not available here; the 발신번호 slice established that they cannot be replayed. So D-T6 and D-T18 — plaintext PII and the unaliased-`decrypt` column collision — are verified by *placement* (a static assertion over the mapper XML) and at the *API, file and log boundaries*, not by executing the real functions.

`lpad`'s truncation is the exception and is worth stating: it is plain ANSI-adjacent SQL that any PostgreSQL reproduces, so D-T9's mechanism **is** executably verifiable via `io.zonky.test:embedded-postgres` (Apache-2.0, already this programme's fallback). The distinction matters — one of the three headline data defects is genuinely testable here and two are not.

**대응 계획.** (1) T1-01 split into T1-01a (behaviour, executable now) and T1-01b (widths, one DBA-run query). (2) `TransactionSerial` takes its widths from configuration and **logs at WARN when an input exceeds the configured width rather than truncating** — a guess that announces itself is not the same defect as a guess that truncates. (3) TC-PII-04 is written as a static mapper-XML assertion, and the masking suite asserts at the API, workbook and log boundaries. (4) At G3, declare D-T6 and D-T18 **verified-by-placement-and-boundary**, explicitly, rather than reporting them as fully verified.
**담당** `qa-engineer` + `architect` · **모니터링** T1-01a in week one; T1-01b request raised day one; G3 declaration drafted at T2-11
