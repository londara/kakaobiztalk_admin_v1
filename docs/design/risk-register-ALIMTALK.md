# Risk Register — 카카오 알림톡 (AlimTalk Compose · Send · Template Validation)

> **Version**: 1.0
> **Date**: 2026-08-18
> **Slice**: legacy screens 61, 50
> **Plan**: [DEV-PLAN-ALIMTALK.md](DEV-PLAN-ALIMTALK.md) · **Threats**: [threat-model-ALIMTALK.md](threat-model-ALIMTALK.md)

---

## Summary

Fourteen risks. The profile of this slice differs from the previous four in one respect that runs through most of them: **four risks are wholly or partly outside our control** (RISK-A01, A03, A07, A08), because this is the programme's first slice that depends on a third party. Prior slices could resolve their unknowns by reading more code; several of these cannot be resolved that way at all.

| ID | Title | Area | Impact | Prob. | Strategy |
|----|-------|------|--------|-------|----------|
| RISK-A03 | Leaked vendor profile key; rotation not ours | 보안 | **H** | **H** (realised) | 완화 |
| RISK-A07 | Vendor may not deduplicate a repeated `tran_id` | 외부 | **H** | M | 완화 |
| RISK-A01 | Vendor field spec may never arrive → two message forms descoped | 외부 | **H** | M | 회피 |
| RISK-A06 | DDL on a shared table widens the CONFLICT-S01 precedent | 기술 | M | **H** | 완화 |
| RISK-A08 | No staging vendor endpoint → integration/load untestable | 기술 | **H** | M | 완화 |
| RISK-A02 | `RSMS` envelope unknown; contract copy can drift | 기술 | M | M | 완화 |
| RISK-A13 | G1 unapproved while A2 commits to DDL and cutover | 일정 | M | **H** | 완화 |
| RISK-A05 | Screen 50 retirement needs retraining and a cutover window | 일정 | M | M | 완화 |
| RISK-A11 | Outbox backlog stalls silently | 운영 | **H** | L | 완화 |
| RISK-A04 | `tran_id` sequence exhaustion at 46,656/institution/day | 기술 | M | L | 수용 |
| RISK-A09 | CONFLICT-A02 unsigned → validation constants unfixed | 일정 | L | M | 완화 |
| RISK-A10 | Catastrophic regex backtracking in template matching | 기술 | M | L | 완화 |
| RISK-A12 | Docker prohibited; DB-backed verification constrained | 기술 | M | **H** (realised) | 완화 |
| RISK-A14 | Batch cap value unset → NFR-SCALE-A01 untestable | 일정 | L | M | 완화 |

---

## RISK-A03 — The vendor profile key is already leaked, and rotation is not ours

- **영역**: 보안 · **영향**: **H** · **발생 확률**: **H** (already realised) · **전략**: 완화
- **설명**: `sender_key = 17da29…（elided — rotate; see RISK-A03）…c2921` is committed in cleartext in `biztalk_admin_50_s001_act.jsp`, in two places, with a comment saying it is temporary — and is additionally serialised into the application log on **every** send (D-A24, D-A30). Possession of this key is authority to send AlimTalk messages as the institution. It must be assumed known outside the authorised set: anyone with repository read access, log access, or a screenshot of the legacy compose screen has held it.
- **왜 심각한가**: This is threat **T-A1**, the highest-severity item in the slice (CVSS ~9.1). An AlimTalk message arrives inside a channel the customer already trusts, from a registered number, on a registered template — the ideal phishing vehicle. **And no code we ship closes it.** ADR-ATK-024 prevents *future* leakage; it cannot un-leak what is already out.
- **대응 계획**:
  1. **Rotate the key at the vendor** — operational, immediate, independent of the migration. Does not wait for Sprint A1.
  2. Adopt ADR-ATK-024: environment-supplied resolution, `ProfileKey` redacting wrapper, `gitleaks` in CI with a **synthetic** key as the positive fixture — never the real one (A2-01).
  3. **Purge or restrict the historical logs** containing the key and recipient numbers. Rotation alone leaves the old key in the log store, which matters if it is ever reinstated or reused elsewhere.
  4. Ask the vendor whether any send has occurred from an unexpected source — the only way to establish whether the leak has been exploited.
- **담당자**: PM + security-auditor + Ops + vendor · **모니터링**: Sprint A1 day 1, then weekly until rotation confirmed
- **게이트**: **G3 must not pass before rotation is confirmed.** This is a go-live precondition, not a code deliverable.

## RISK-A07 — The vendor may not deduplicate a repeated `tran_id`

- **영역**: 외부 · **영향**: **H** · **발생 확률**: M · **전략**: 완화
- **설명**: ADR-ATK-023's outbox is **at-least-once**: a dispatcher crash after the vendor call but before the status write leaves a `PENDING` row that will be retried. Whether that retry produces a second customer message depends entirely on `COOCON_ALERT` honouring `(is_cd, tran_id)` as an idempotency key. **Nothing in the analysed artifacts states that it does** — and the legacy could never have discovered it, because its `tran_id` collided so freely (D-A25) that duplicate submissions were routine and unexamined.
- **왜 심각한가**: A duplicate financial notification is a customer-visible defect and, at volume, a regulatory one. It is also the risk the design deliberately accepts: the alternative to a possible duplicate is a possible **silent non-delivery**, which for a payment notification is worse.
- **대응 계획**:
  1. **Spike A1-03** — send the same `tran_id` twice against the staging endpoint and observe. Blocked by RISK-A08 if no staging endpoint exists.
  2. Failing a live test, obtain the vendor's written statement of idempotency semantics.
  3. **The design already assumes the worse answer.** ADR-ATK-025 retries only connect failures — where non-delivery is provable — and defers read timeouts and 5xx to the next dispatcher pass. A "no" costs no rework; it makes the conservative split permanent.
  4. If the vendor neither deduplicates nor can be tested, add a local "possibly dispatched" state requiring operator adjudication rather than automatic retry. Slower, but it never sends twice unattended.
- **담당자**: architect + adapter-builder · **모니터링**: Sprint A1 day 2; blocks A2-06 sign-off

## RISK-A01 — The vendor field specification may never arrive

- **영역**: 외부 · **영향**: **H** · **발생 확률**: M · **전략**: 회피 (scheduled fallback)
- **설명**: Neither `IMO.ADV_KKO_AT_SEND` nor `_M` declares any image field, `kko_header`, `highlight`, `items` or `summary`. The legacy composer emits five of them (D-A2, D-A8) and the contract has nowhere to put them. PM ruled AMB-A05a "implement properly", which requires field definitions only the vendor has.
- **왜 심각한가**: FR-ATC-003 and FR-ATC-008 cannot be completed without it — the only requirements in the slice with a hard external dependency. And ADR-ATK-021's bidirectional contract test will **reject these fields as soon as it is written**, so this is build-blocking rather than a documentation gap.
- **대응 계획**:
  1. Request the current `COOCON_ALERT` AlimTalk field specification on Sprint A1 day 1 (spike A1-01).
  2. If it arrives: extend the IMO contract copy, the DTOs and the limits table; build both forms.
  3. **If it has not arrived by the Sprint A1 gate, the fallback triggers automatically** — ship 기본 + 강조표기형 only, which the contract fully declares, and remove 이미지형 and 아이템리스트형 from the UI. This reverses AMB-A05a and needs PM acknowledgement at that gate.
  4. The decision point is **scheduled, not deferred** — which is the mitigation. The failure mode to avoid is discovering at week three that two forms cannot be built.
- **담당자**: architect + PM + vendor · **모니터링**: Sprint A1 day 1, decision at the A1 gate

## RISK-A06 — DDL on a shared table widens the CONFLICT-S01 precedent

> ## ✅ **소멸 / RETIRED — 2026-08-19**
>
> **이 리스크는 더 이상 존재하지 않는다.** 공유 테이블에 대한 DDL 이 설계에서 빠졌기 때문이다.
>
> 대응 계획 2번 — *"`SELECT *` 로 읽는 레거시 소비자가 없는지 확인한다"* — 를 실행했고, 그 과정에서
> 컬럼 추가 자체가 **불필요**하다는 것이 드러났다. 상태는 `KKO_MSG_LOG` 에 이미, 그리고 메시지
> 단위로(= 더 올바른 단위로) 존재한다. ADR-ATK-023 「수정 1」 로 요구를 철회했다.
>
> 점검 결과 자체도 기록해 둔다: `KKB_ADMIN_SEND_HIS` 의 소비자는 `_C001`(INSERT)과 `_L001`(SELECT)
> 둘뿐이고 **둘 다 컬럼을 명시**한다. `SELECT *` 0건, 위치 기반 읽기 0건. 즉 컬럼 추가는 안전**했을**
> 것이나, 애초에 필요하지 않다.
>
> **This risk no longer exists**: the shared-table DDL left the design. Executing mitigation step 2 revealed
> that the column was **unnecessary** — the status already exists in `KKO_MSG_LOG` at the per-message grain.
> The check itself: both consumers name their columns; no `SELECT *`, no positional read. The addition would
> have been safe, but is not needed.
>
> **G1 에 대한 효과**: G1 이 판단할 항목에서 이 건이 빠진다. 남는 것은 "새 표 하나를 만들어도 되는가"
> 이며, 이는 발신번호 슬라이스에서 **이미 승인된** CONFLICT-S01 과 동일한 범위다.
>
> **근거**: [ANALYSIS-A2-02-existing-schema.md](../../mapping/analysis/ANALYSIS-A2-02-existing-schema.md)

- **영역**: 기술 · **영향**: M · **발생 확률**: ~~**H**~~ **N/A** · **전략**: ~~완화~~ **소멸**
- **설명**: ADR-ATK-023 needs a new `KKB_ATK_SEND_OUTBOX` table **and a status column on `KKB_ADMIN_SEND_HIS`**, which screen 50 and potentially `AOA_ADMIN` also write. The 발신번호 slice took CONFLICT-S01 to G1 for permission to *add a table*; this asks additionally to *add a column to a shared one*.
- **왜 심각한가**: Not a technical hazard — legacy readers select named columns and an added column is invisible to them. It is a **governance** issue: the precedent granted becomes "this programme may add tables and additive columns", which is broader than what G1 was previously asked for. Slipping that through as an implementation detail would be the wrong way to obtain it.
- **대응 계획**:
  1. State the widened precedent explicitly in the G2 submission (DEV-PLAN §10) and obtain it deliberately.
  2. Verify no legacy reader uses `SELECT *` against `KKB_ADMIN_SEND_HIS` — a positional or wildcard read is the one case where an added column is not invisible. This is a concrete check, not an assumption (task A2-02).
  3. Keep all DDL additive and reversible; no existing column is altered.
  4. If the precedent is refused, the fallback is a second new table holding send outcomes keyed to `(IS_CD, SERIALNUM)` — more joins, no shared-table change.
- **담당자**: architect + data-model-designer + DBA · **모니터링**: before A2-02 starts

## RISK-A08 — No staging vendor endpoint may exist

- **영역**: 기술 · **영향**: **H** · **발생 확률**: M · **전략**: 완화
- **설명**: `IMO.ADV_KKO_AT_SEND2` names a production target (`COOCON_ALERT`, `/advising/kakao/at_send`). Whether a sandbox or staging equivalent exists is unknown. Without one, three spikes and two test tiers have no target: A1-02 (`RSMS` capture), A1-03 (idempotency), integration tests against real vendor behaviour, and the NFR-PERF-A02/A03 load tests.
- **왜 심각한가**: It compounds RISK-A07 — the idempotency question is the one thing the design cannot safely guess, and it is answerable only by observation. It would also mean the first real vendor call happens in production.
- **대응 계획**:
  1. Establish on Sprint A1 day 1 whether a staging endpoint exists and is reachable from the build environment.
  2. If not: `MockRestServiceServer` and a local stub server cover request shape, timeout, 5xx and breaker behaviour **fully in-process** (TEST-PLAN tier 2). This is the one place where Docker's absence does not hurt — an HTTP boundary needs no container.
  3. What a stub **cannot** provide is real vendor semantics: the `RSMS` envelope shape and idempotency. Those require either the endpoint, a captured production payload (A1-02), or a written vendor statement.
  4. Failing all of these, declare the affected behaviours **unverified at G3** rather than counting stub-based tests as evidence of vendor conformance. The 발신번호 slice's RISK-S13 established this precedent and it applies here.
- **담당자**: PM + Ops + architect · **모니터링**: Sprint A1 day 1 — gates A1-02, A1-03 and the load strategy

## RISK-A02 — The `RSMS` envelope is unknown and the contract copy can drift

- **영역**: 기술 · **영향**: M · **발생 확률**: M · **전략**: 완화
- **설명**: Two related gaps. `IMO.ADV_KKO_AT_SEND2` declares a **single** input field `RSMS`; how the `jex` runtime marshals a bound request into that string is not recoverable from source (AMB-A06). Separately, ADR-ATK-021's conformance test reads a **checked-in copy** of the IMO XML, which can drift from the deployed definition.
- **대응 계획**:
  1. Capture a real `RSMS` value (A1-02); assert our marshalling reproduces it byte-for-byte (`RsmsEnvelopeTest`).
  2. Assert the contract copy's `<version>` and `<hash>` in the test, so a substituted contract is loud rather than silent.
  3. Add a periodic check that the deployed IMO definition still matches the checked-in copy — the residual gap is a runtime contract updated without updating our repository.
  4. Until A1-02 completes, treat the envelope as **unverified** and label it so in the test report. Not as "probably fine".
- **담당자**: adapter-builder + architect · **모니터링**: Sprint A1 day 2, then per release

## RISK-A13 — G1 is unapproved while Sprint A2 commits to DDL and a cutover

- **영역**: 일정 · **영향**: M · **발생 확률**: **H** · **전략**: 완화
- **설명**: This plan is written against a DRAFT specification, following the precedent of the three previous slices. G1 must cover CONFLICT-A01 (screen 50 retirement), CONFLICT-A02 (limit reconciliation), RESIDUAL-A01 and RESIDUAL-A02. Sprint A2 commits to schema change *and* to retiring a production screen.
- **대응 계획**:
  1. **Sprint A1 carries no DDL and no cutover.** All 19 A1 tasks are safe under any G1 outcome, so the first two weeks proceed regardless.
  2. Obtain G1 before A2-02 starts — that is the first irreversible commitment.
  3. If CONFLICT-A01 is reversed to coexistence, A2-14 is dropped and the `tran_id` constraint from ADR-ATK-026 becomes permanent rather than transitional. The rest of the plan is unaffected — the design does not depend on screen 50 disappearing.
  4. If the RISK-A06 precedent is refused, use the fallback second table.
- **담당자**: PM + architect · **모니터링**: end of Sprint A1

## RISK-A05 — Screen 50 retirement needs retraining and a cutover window

- **영역**: 일정 · **영향**: M · **발생 확률**: M · **전략**: 완화
- **설명**: CONFLICT-A01's resolution retires a screen operators use today. The new screen behaves differently in ways that matter: 발송 no longer means "delivered" but "accepted" (ADR-ATK-023), `sender_key` is no longer entered by hand, `template_code` is selected rather than typed, and invalid recipients are surfaced *before* despatch as a decision rather than afterwards as an error.
- **왜 심각한가**: The accepted-vs-delivered distinction is the one most likely to be misread. An operator who reads "정상 처리" as "the customer has it" will not chase a backlog — which is a usability defect in the eventual-consistency design, not merely a training gap.
- **대응 계획**:
  1. NFR-USE-A04's confirmation step and the outcome view must say **accepted**, with delivery status shown separately once known (A2-13).
  2. Write the cutover runbook including the rollback security caveat: reverting to screen 50 reintroduces sending with the leaked key (A2-14).
  3. Retrain before cutover, with the four behavioural changes named explicitly.
  4. Keep screen 50 available but not advertised during a bounded window; the DB constraint protects data integrity throughout.
- **담당자**: PM + Ops + frontend-developer · **모니터링**: Sprint A2 week 2

## RISK-A11 — The outbox backlog stalls silently

- **영역**: 운영 · **영향**: **H** · **발생 확률**: L · **전략**: 완화
- **설명**: An outbox that stops draining produces no error — sends are accepted, rows accumulate, and nothing is delivered. This is threat T-A22 and it is the failure mode the design **trades for** the legacy's false-failure reporting. The legacy was loud and wrong; this is quiet and wrong.
- **대응 계획**:
  1. Micrometer metrics on backlog depth, oldest `PENDING` age, and dispatcher poll success, on the existing actuator surface (A2-12).
  2. Alert on oldest-pending age rather than on depth — depth is normal during a reserved-send burst, age is not.
  3. `FAILED` → `DEAD` after bounded attempts, with `DEAD` rows surfaced to operators rather than retried forever.
  4. A synthetic canary send on a schedule, so a stalled dispatcher is detected by absence of delivery rather than by a customer complaint.
- **담당자**: backend-developer + Ops · **모니터링**: continuous from A2-12

## RISK-A04 — `tran_id` sequence exhaustion

- **영역**: 기술 · **영향**: M · **발생 확률**: L · **전략**: 수용
- **설명**: ADR-ATK-026 fits a unique value into the contract's 10-character field as `env + yyMMdd + base-36 sequence`, giving 46,656 sends per institution per day. Exhausting it fails sends outright.
- **왜 수용인가**: A `tran_id` is consumed per **send request**, not per recipient — a single send to 1000 recipients spends one. Forty-six thousand send requests per institution per day is well above plausible volume, and the alternatives are worse: a random suffix makes collision probabilistic on a primary key, and a longer field is not available. Accepted with monitoring rather than mitigated.
- **Sprint A1 정정 / correction**: ADR-ATK-026 originally allotted **four** characters to the sequence and claimed 1,679,616/day. That was arithmetically wrong — 1 + 6 + 4 = **11**, one character over the contract's 10, and the ADR's own diagram was eleven characters wide. `TranIdGeneratorTest` caught it during implementation. The sequence is three characters and the ceiling is **46,656**, a 36× reduction from the figure this risk was first assessed against. The assessment does not change (still 수용, impact M, probability L) because the per-request rather than per-recipient consumption model leaves ample headroom, but **the margin is smaller than the original design claimed** and that is worth stating rather than quietly adjusting.
- **대응 계획**: Alert at 50 % daily consumption per institution. If ever approached, the environment discriminator can be repurposed to widen the sequence — at the cost of the staging/production safety it provides.
- **담당자**: backend-developer · **모니터링**: monthly volume review

## RISK-A09 — CONFLICT-A02 unsigned, so validation constants are unfixed

- **영역**: 일정 · **영향**: L · **발생 확률**: M · **전략**: 완화
- **설명**: PM ruled "Kakao published limits govern"; analysis then found the IMO contract declares its own, disagreeing in both directions (`msg` 4000 vs ~1000; `button.name` 28 vs ~14). Design adopts `min(contract, Kakao)` per field, pending G1 confirmation.
- **대응 계획**: `AlimTalkLimits` holds every bound in **one** class, with contract values read from the XML and business limits tagged `[ASSUMED-KAKAO-SPEC]`. A ruling change is a one-file edit plus test data, not a code change — this is the mitigation. Confirm at G1.
- **담당자**: architect + PM · **모니터링**: G1

## RISK-A10 — Catastrophic regex backtracking in template matching

- **영역**: 기술 · **영향**: M · **발생 확률**: L · **전략**: 완화
- **설명**: ADR-ATK-022 compiles `TEMPLATE_MSG` to a pattern with lazy `.+?` variables. A template with many adjacent variables against long non-matching content can backtrack exponentially — threat T-A20, a surface this design introduces that the legacy's single-pass scanner did not have.
- **대응 계획**:
  1. Match timeout, enforced by running the match under a bounded executor.
  2. Cap variable count per template.
  3. **Reject adjacent variables with no intervening literal** — they are ambiguous by definition and cannot be matched meaningfully, so rejecting them costs nothing and removes the pathological case.
  4. Test with 20 adjacent variables against 4 KB of non-matching content.
- **담당자**: backend-developer + security-auditor · **모니터링**: A1-09 code review

## RISK-A12 — Docker prohibited; DB-backed verification constrained

- **영역**: 기술 · **영향**: M · **발생 확률**: **H** (already realised) · **전략**: 완화
- **설명**: Docker is not permitted, so Testcontainers cannot be used (inherited RISK-S13). The declared `org.testcontainers:postgresql` dependency is dead weight and should be removed. Anything requiring a real PostgreSQL — `FOR UPDATE SKIP LOCKED` claim semantics, the sequence under concurrency, the unique-constraint violation path — has no container-based test.
- **왜 이 슬라이스에서는 덜 심각한가**: RISK-S13 was severe for the 발신번호 slice because its **headline defect was a DB-function interaction**. Here the three Critical defects are pure serialisation against a checked-in contract XML, so `ContractConformanceTest` covers them at tier 1 with no infrastructure at all. And the external boundary is HTTP, which stubs in-process. **The constrained area is narrower: outbox concurrency semantics, not the defects the slice exists to fix.**
- **대응 계획**:
  1. Remove the dead Testcontainers dependency.
  2. Establish whether a PostgreSQL dev instance is reachable (inherited from RISK-S13's open question) — this decides whether `SKIP LOCKED` and sequence concurrency get tier-1 coverage.
  3. If not, `io.zonky.test:embedded-postgres` is viable **here in a way it was not for the 발신번호 slice**: this slice needs no proprietary `ENCRYPT`/`decrypt` functions, only standard PostgreSQL features. That was the blocker there and it does not apply.
  4. Declare any residual gap unverified at G3 rather than counting it as passing.
- **담당자**: qa-engineer + architect · **모니터링**: Sprint A1 day 1

## RISK-A14 — The batch cap value is unset

- **영역**: 일정 · **영향**: L · **발생 확률**: M · **전략**: 완화
- **설명**: PM ruled a configurable cap (AMB-A03) with the value to follow. Until set, FR-ATS-014 has no threshold and NFR-SCALE-A01 / NFR-PERF-A03 have no load target.
- **대응 계획**: Working assumption **1000**, matching the legacy's existing chunk boundary, so the value is defensible and the load test has a target. Configurable, so a change is a property edit. Confirm with Ops before the A2 load test.
- **담당자**: PM / Ops · **모니터링**: Sprint A2 week 1
