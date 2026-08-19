# Risk Register — 이용기관 보고서 (Institution Usage Report)

> **Version**: 1.0
> **Date**: 2026-08-18
> **Predecessor**: [DEV-PLAN-REPORT.md](DEV-PLAN-REPORT.md)
> **Siblings**: [risk-register.md](risk-register.md), [-LOGIN](risk-register-LOGIN.md), [-INSTITUTION](risk-register-INSTITUTION.md), [-SENDERNO](risk-register-SENDERNO.md)
> **Count**: 12 (harness minimum 10)

---

## Summary

| ID | Title | Area | Impact | Prob. | Strategy |
|----|-------|------|--------|-------|----------|
| RISK-R01 | No Docker, and this slice needs two databases to verify its core mechanism | 기술 | **H** | **H** (realised) | 완화 |
| RISK-R02 | The T-4 batch lag may be deliberate, or may be an unnoticed failure | 운영 | M | M | 완화 |
| RISK-R03 | Historical bulk aggregates already contain silent zeros | 운영 | **H** | **H** | 완화 |
| RISK-R04 | The unauthenticated read path may already have been exploited | 보안 | **H** | M | 완화 |
| RISK-R05 | `AOA_ADMIN` keeps the same defective screens on the same data | 외부 | **H** | **H** | 수용 + 추적 |
| RISK-R06 | Staging lacks realistic volume in both sources, so load targets are unproven | 일정 | M | **H** | 완화 |
| RISK-R07 | The row ceiling (AMB-R05) cannot be set until late, and may force a design change | 기술 | M | M | 완화 |
| RISK-R08 | Keyset pagination removes deep page jumps that users may expect | 사용성 | L | M | 수용 |
| RISK-R09 | The source-gap heuristic produces false positives and erodes trust | 운영 | M | M | 완화 |
| RISK-R10 | Bulk datasource credentials and topology are unknown to this team (SEC-002) | 기술 | M | M | 완화 |
| RISK-R11 | Report figures may feed 수수료 billing, raising the cost of any under-report | 외부 | **H** | L | 완화 |
| RISK-R12 | Audit volume from read/export logging outruns an undecided retention policy | 운영 | M | M | 완화 |

---

## RISK-R01 — No Docker, and this slice needs two databases to verify its core mechanism

- **영역**: 기술 · **영향**: **H** · **발생 확률**: **H** (already realised) · **전략**: 완화
- **설명**: Docker is not permitted, so Testcontainers is permanently unavailable (inherited from RISK-S13). This slice raises the bar: ADR-RPT-021's merge is a cross-**datasource** mechanism, and no previous slice required two PostgreSQL instances to test anything.
- **왜 이번에는 덜 심각한가**: The 발신번호 slice could not verify its headline fix at all, because D-S1 depended on DB functions (`ENCRYPT`/`decrypt`) that cannot be replayed without their real definitions. **This slice's headline mechanism depends only on `ORDER BY`, `LIMIT` and row-value comparison** — standard SQL any PostgreSQL reproduces faithfully. Two schemas on one instance therefore test the merge honestly (TEST-PLAN §2, tier 2).
- **무엇이 여전히 검증 불가**: FR-RPTS-005's degradation behaviour — two schemas on one instance fail together, so genuine per-source outage cannot be reproduced. Covered by `DataSource`-level fault injection (TEST-PLAN §5) and labelled as a substitute rather than counted as equivalent.
- **대응 계획**: (1) confirm whether a PostgreSQL instance is reachable in CI; (2) if so, run tier 2 with two schemas as the default; (3) fall back to `io.zonky.test:embedded-postgres` (Apache-2.0) in process; (4) declare FR-RPTS-005 verified-by-injection-only at G3, explicitly, rather than reporting it as fully verified.
- **담당자**: qa-engineer + architect · **모니터링**: Sprint R1 day 1

## RISK-R02 — The T-4 batch lag may be deliberate, or may be an unnoticed failure

- **영역**: 운영 · **영향**: M · **발생 확률**: M · **전략**: 완화
- **설명**: `BATCH_BIZTALK_DAILY` with no parameters aggregates a single day four days back (D-R26). Nobody in the current team knows whether that is an intentional settling period for late delivery receipts or an unnoticed regression. FR-RPT-013 will display it either way, prominently.
- **왜 중요한가**: If it is deliberate, the display costs nothing and prevents the next person misreading an empty grid as an outage. If it is not, then the business has been three days blind for an unknown period and OI-R01 becomes urgent rather than routine.
- **대응 계획**: One question to the operations owner during Sprint R1 (DEV-PLAN §4.4). The answer changes OI-R01's priority, not this slice's design.
- **담당자**: PM + operations owner · **모니터링**: Sprint R1

## RISK-R03 — Historical bulk aggregates already contain silent zeros

- **영역**: 운영 · **영향**: **H** · **발생 확률**: **H** · **전략**: 완화
- **설명**: D-R27 — bulk aggregation is delete-then-insert with the whole block inside `catch (JexBIZException e) { LOG.error(e); }`. A failed run leaves the day deleted, the batch reports success, and the report renders the gap as zero. Any such day in history is now indistinguishable from a genuinely quiet day, and **the new report will display it with a watermark that appears to vouch for it**.
- **왜 심각한가**: This is the fourth consecutive slice to find the same shape — D-I1, D-S1, now D-R27 — and here the wrong number is *presented as a fact by the system we are building*. The watermark cannot detect it (ADR-RPT-022, stated limitation); the heuristic in FR-RPTS-005 catches only the wholesale case.
- **대응 계획**: Reconciliation of `KKB_APITR_SMTN` (bulk) against raw send records before cutover, as an operational data exercise (DEV-PLAN §4.4) — the same shape of reconciliation D-I1 and D-S1 each produced. Repair of the batch itself is OI-R01.
- **담당자**: operator team + DBA · **모니터링**: before cutover — not before G2

## RISK-R04 — The unauthenticated read path may already have been exploited

- **영역**: 보안 · **영향**: **H** · **발생 확률**: M · **전략**: 완화
- **설명**: `WSVC.biztalk_admin_20_l001` declares `<login>N</login>` (D-R1) and an empty `IS_CD` means all institutions (D-R2). One request, no credentials, returns every customer's message volume by day and channel. Pre-fix CVSS ≈ **9.1** (T-R10).
- **왜 지금 제기하는가**: The fix ships with this slice, but the fix is not the whole response. If it has been exploited, that is a disclosure event with its own process and its own notification obligations, and it is not discovered by shipping a patch.
- **대응 계획**: Review production access logs for calls to `biztalk_admin_20_l001` carrying an empty `IS_CD`, particularly with wide date ranges (DEV-PLAN §4.4). Note the log may not retain enough history to be conclusive — the Jex service-monitor record predates any business audit. If evidence is found, escalate as a security incident rather than a defect.
- **담당자**: PM + security-auditor · **모니터링**: Sprint R1 — independent of the code change

## RISK-R05 — `AOA_ADMIN` keeps the same defective screens on the same data

- **영역**: 외부 · **영향**: **H** · **발생 확률**: **H** · **전략**: 수용 + 추적
- **설명**: `AOA_ADMIN` carries the same screens against the same databases (established in the 발신번호 slice). After we ship, T-R01, T-R04 and T-R10 remain **fully reachable** through that console — including the unauthenticated path. Fixing screen 20 here does not remove the exposure; it removes one of two doors to it.
- **대응 계획**: Programme-level, carried from RISK-S05. This slice's contribution is to name it precisely: the exposure is not "an old console still exists" but "a CVSS 9.1 disclosure path stays open after its fix ships." Decommissioning or patching `AOA_ADMIN` belongs to the programme plan.
- **담당자**: PM · **모니터링**: programme milestone review

## RISK-R06 — Staging lacks realistic volume in both sources

- **영역**: 일정 · **영향**: M · **발생 확률**: **H** · **전략**: 완화
- **설명**: NFR-PERF-R01/R02 and NFR-SCALE-R01 are only meaningful against production-scale data in **both** aggregates. Staging volume is unknown and, for the bulk source, may not exist at all — the legacy queries it only when `TSTCL_DV=REAL`, so non-production environments have never populated it.
- **왜 이번 슬라이스 고유의 문제인가**: The environment flag that caused D-R4/D-R6/D-R7 also means **staging has probably never held bulk data**. The absence is not an oversight in test setup; it is a consequence of the defect being removed.
- **대응 계획**: Generate synthetic aggregates at production shape — 366 days × the real institution count — in both sources during R1, so the R2 load tests have something to run against. Cheap to generate: the aggregate is one row per day per institution, with no PII to anonymise.
- **담당자**: qa-engineer · **모니터링**: Sprint R1 exit

## RISK-R07 — The row ceiling cannot be set until late

- **영역**: 기술 · **영향**: M · **발생 확률**: M · **전략**: 완화
- **설명**: AMB-R05's answer is a measured number, not a decision — the ceiling is the largest export that holds NFR-PERF-R03 with a flat heap (TEST-PLAN §9, L-R03 + L-R04). Those tests run in R2. If the measured ceiling is uncomfortably low, the deferral of async export (FR-RPTX-010) becomes a worse trade than it looks today.
- **대응 계획**: Run L-R03/L-R04 against synthetic data **early in R2**, not at the end, so a low result leaves time to respond. If the ceiling lands below a useful working range — a single institution over a full year — reopen the async decision with the PM rather than shipping a ceiling users hit routinely.
- **담당자**: qa-engineer + architect · **모니터링**: Sprint R2 week 1 — deliberately early

## RISK-R08 — Keyset pagination removes deep page jumps

- **영역**: 사용성 · **영향**: L · **발생 확률**: M · **전략**: 수용
- **설명**: ADR-RPT-021 gives next/previous plus an exact total, not arbitrary page-number jumps. Users accustomed to the legacy's numbered paging widget may perceive a regression.
- **왜 수용하는가**: The legacy widget paged **in the browser over an unbounded fetch** (D-R8), so at any real volume it either worked by loading everything or did not work at all. There is no working behaviour being removed.
- **대응 계획**: None beyond UI clarity — show the total and the current position. Revisit only if users report it against real usage.
- **담당자**: frontend-developer · **모니터링**: post-release feedback

## RISK-R09 — The source-gap heuristic produces false positives

- **영역**: 운영 · **영향**: M · **발생 확률**: M · **전략**: 완화
- **설명**: FR-RPTS-005's gap flag fires when one source has rows for a day across the whole institution set and the other has none. A genuinely quiet day for bulk sending looks identical. Too many false alarms and operators learn to ignore the flag — at which point it is worse than absent, because it looks like coverage.
- **대응 계획**: Scope the heuristic to the wholesale case only (all institutions, one source empty, other populated), word it as a possibility rather than a fault, and assert the false-positive guard by test F-R06 — a legitimately quiet day for a single institution must **not** fire it. Review the fire rate after one month of real use.
- **담당자**: backend-developer + operations owner · **모니터링**: one month post-release

## RISK-R10 — Bulk datasource topology is unknown to this team

- **영역**: 기술 · **영향**: M · **발생 확률**: M · **전략**: 완화
- **설명**: The authoritative datasource definitions live in `jex.iris_admin.xml`, declared under `JEX.config.file` in `jex.prop` — **SEC-002 applies and it has not been read**. AMB-R04 is therefore answered by inference (ADR-RPT-021), not by configuration.
- **왜 낮은 등급인가**: ADR-RPT-021 is correct under both answers, so a wrong inference costs an unnecessary abstraction, not a redesign. This is the material difference from RISK-S01 in the 발신번호 slice, where the equivalent unknown was a hard gate.
- **대응 계획**: Task R1-01 — the DBA or platform owner confirms the topology directly, without this team reading protected configuration. If one database, simplify the mapper layer and delete the merge iterator. Also confirms connection limits, timeouts and whether the bulk source has its own maintenance window.
- **담당자**: architect + DBA · **모니터링**: Sprint R1 day 1

## RISK-R11 — Report figures may feed 수수료 billing

- **영역**: 외부 · **영향**: **H** · **발생 확률**: L · **전략**: 완화
- **설명**: BR-012 establishes a per-institution 수수료. AMB-R08 asks whether the merged 전체 view must retain a per-source breakdown for billing reconciliation and is open. If these figures are an input to invoicing, then RISK-R03's silent zeros are an under-billing problem, and the merge's summation semantics become financially material rather than presentational.
- **대응 계획**: Settle AMB-R08 with the domain owner during R1. If billing does consume these numbers, escalate RISK-R03's reconciliation from "before cutover" to a blocking prerequisite, and require the per-source breakdown in the export.
- **담당자**: PM + domain owner · **모니터링**: Sprint R1

## RISK-R12 — Audit volume outruns an undecided retention policy

- **영역**: 운영 · **영향**: M · **발생 확률**: M · **전략**: 완화
- **설명**: FR-AZ-R05 audits every query **and** every export, including denials. A report screen is queried far more often than a maintenance screen, so this slice generates more audit volume than the three before it. OI-02 (retention term) remains open across the whole programme and now has a larger consumer.
- **대응 계획**: Record the projected event rate from the R2 load tests and supply it to the OI-02 decision, so the retention term is chosen against a real number. Audit content stays minimal — actor, scope, range, counts — and never the figures themselves (T-R15).
- **담당자**: architect + PM · **모니터링**: Sprint R2 exit; OI-02 remains open
