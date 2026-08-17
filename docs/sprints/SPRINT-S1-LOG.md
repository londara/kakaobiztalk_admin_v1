# Sprint S1 Log — 발신번호 foundation

> **Sprint**: S1 · **Date**: 2026-08-17
> **Plan**: [sprint-S1-tasks.md](../design/sprint-S1-tasks.md) · [DEV-PLAN-SENDERNO.md](../design/DEV-PLAN-SENDERNO.md)
> **Status**: **PARTIAL — 9 of 12 tasks complete, 2 blocked externally, 1 deferred.** Sprint **not** closed; DoD not met
>
> **Update 2026-08-17 (second pass).** Read path completed end to end — mapper, service, controller and React screen — on PM instruction to proceed without resolving DB access. Backend 274 tests, frontend 8 new tests, typecheck clean.

---

## 1. The unplanned task that came first

The sprint opened on a **red build**: 191 tests, 1 error and 1 failure, both in `CsrfIntegrationTest`.

The error was a real test defect. The class is a `@WebMvcTest` slice with `@MockBean MessageHistoryService`, never stubbed, so it returned `null` and `MessageHistoryResponse.from()` threw on `result.rows()`. The exception propagated out of MockMvc, which means **the test failed precisely when CSRF worked** — the inverse of its purpose. Stubbed to return an empty page; the error is gone.

The uncommitted `MessageHistoryMapper.xml` edit was suspected and cleared: its diff is comment-only. Both problems were pre-existing on `main`.

**The remaining failure was not fixed, and it may not be a test bug.** Two tests issue an identical request to the same endpoint:

| Test | Auth | Result |
|------|------|--------|
| `responseIssuesCsrfCookie` | anonymous | 403 **with** an `XSRF-TOKEN` cookie → passes |
| `echoingCookieValueInHeaderPasses` | `@WithMockUser` | 403 with **zero** cookies → fails |

Confirmed by direct inspection (`status=403 cookies=[] err=null`) and reproduced in isolation, so it is not test pollution. If the asymmetry holds in production, a logged-in SPA never receives a token and cannot make any state-changing request — **CR-01 recurring for authenticated sessions**, which is what ADR-014 and this very test exist to prevent.

Not claimed as confirmed: `@WithMockUser` in a `@WebMvcTest` slice does not fully model a real session, and the login flow may issue the cookie at authentication time. **Routed to the 로그인 slice owner as a carried defect** rather than fixed here, and the assertion was strengthened rather than relaxed.

## 2. Completed

| Task | Deliverable | Verification |
|------|-------------|--------------|
| **S1-02** | [`SenderNumberRef`](../../src/main/java/com/webcash/iris/biztalk/domain/SenderNumberRef.java) — opaque row identity, structurally separate from the displayed value | 17 tests |
| **S1-05** (part) | [`SenderNumberCriteria`](../../src/main/java/com/webcash/iris/biztalk/domain/SenderNumberCriteria.java) — paging normalisation, size clamp, overflow-safe offset, institution gate | 16 tests |
| **S2-03** (pulled forward) | [`SenderNumberValidator`](../../src/main/java/com/webcash/iris/biztalk/domain/SenderNumberValidator.java) — digits, length by prefix, barred special numbers, per-rule messages | 35 tests |
| **S1-04** | [`SenderNumberMapper`](../../src/main/java/com/webcash/iris/biztalk/infra/db/SenderNumberMapper.java) + [XML](../../src/main/resources/mybatis/mapper/biztalk/SenderNumberMapper.xml) — name-based mapping, aliases on both masked columns, phantom `ISNM` dropped, `ORDER BY`, `LIMIT`/`OFFSET` | SQL-shape only (tier 3) |
| **S1-06** | [`SenderNumberRow`](../../src/main/java/com/webcash/iris/biztalk/domain/SenderNumberRow.java) / [`SenderNumberEntity`](../../src/main/java/com/webcash/iris/biztalk/domain/SenderNumberEntity.java) — displayed fields only; `RGSR_ID`/`UDT_ID` absent by construction | covered via service tests |
| **S1-07/08/09** | [`SenderNumberService`](../../src/main/java/com/webcash/iris/biztalk/domain/SenderNumberService.java) + [`SenderNumberController`](../../src/main/java/com/webcash/iris/biztalk/api/SenderNumberController.java) — `@PreAuthorize` operator role, session-derived scope, read audit | 11 tests |
| **S1-11** | [`SenderNumberPage.tsx`](../../src/main/frontend/src/features/biztalk/SenderNumberPage.tsx) + [`senderNumberApi.ts`](../../src/main/frontend/src/api/senderNumberApi.ts) | 8 tests |

Backend suite: **191 → 274 tests**, errors **1 → 0**, failures **1 → 1** (§1). Frontend: 8 new tests, `tsc --noEmit` clean.

### 2.3 Decisions taken to keep moving without the spike

**The mapper uses `decrypt(DP_NO) = #{number}`, not `DP_NO = ENCRYPT(#{number})`.** The second form is indexable and is what ADR-SND-018 hopes for, but it is only correct if `ENCRYPT` is deterministic — unconfirmed (S1-01). Adopting it early would make lookups **silently return nothing** if it is not, which is D-S1's exact failure mode. The slow, correct form is used and the optimisation is left as a documented follow-up.

**`SenderNumberRow` carries no `RGSR_ID`/`UDT_ID`.** The legacy contract returned operator email addresses that the grid never rendered (D-S21). What the type does not carry, no upper layer can leak.

**The screen ships no 등록/수정/삭제 buttons.** Sprint S1 covers the list; those operations are S2. The legacy shipped a 수정 *handler* with no 수정 *button* (D-S8), and a test asserts the buttons are absent so the reverse — a button with no operation — cannot be introduced either.

**S2-03 was pulled forward deliberately.** With Docker prohibited, work that needs a database cannot be verified beyond tier 3 (§4). The validator needs none, carries no pending decision, and closes two defects, so it was worth more than starting S1 tasks that would sit half-verified.

### 2.1 A defect found in our own code, by our own test

The validator's first version used `Character.isDigit()`, and `SenderNumberValidatorTest` rejected it: that method returns `true` for full-width digits (U+FF10–U+FF19), so `０１０１２３４５６７８` validated as a legitimate number.

Worth recording because it is **the same failure class as D-S1**. Stored that way, the value could never match its ASCII form under `decrypt(DP_NO) = :DP_NO` — including in `KAKAOTALK`'s own send-path check. Storable but unmatchable, and silent. Fixed with an explicit ASCII range check and a comment naming the reason, so it is not "simplified" back later.

### 2.2 What `SenderNumberRef` is actually for

D-S1 arose because three individually correct layers composed into a broken system: the list masked the number, the grid passed the displayed value as the row identifier, and the delete matched on `decrypt(DP_NO)`. `SenderNumberRef` removes the structure rather than the symptom — **the value identifying a row is never the value shown to a human.**

The test that matters most is `identityIsStableAcrossDisplayChanges`: it asserts identity resolves correctly regardless of display format. That is the test that would have caught the October 2025 regression.

The token is explicitly **not** an authorization mechanism, and the Javadoc says so: it is unsigned and client-decodable. That is acceptable because the number is already displayed in full under ruling AMB-S04, so the token discloses nothing the response does not already carry, and institution scope is enforced separately by `TenantContext` (FR-AZ-D03).

## 3. Not completed

| Task | State |
|------|-------|
| S1-01 spike — `ENCRYPT` determinism | **Blocked externally.** Needs one SQL statement from someone with DB access (§4) |
| S1-03 — `BIZ_DB` / `BIZTALK_DB` alias check | **Blocked externally.** Requires datasource configuration; `application.yml` is covered by SEC-001 and was not read |
| S1-12 — negative-path security tests | **Deferred.** The read-path halves (T-E1, T-I1, T-I4, T-I6) are covered by `SenderNumberServiceTest`; the endpoint-level sweep needs a `@WebMvcTest` slice and is scheduled with S2's write endpoints so the whole surface is swept once |

**A note on the second pass.** S1-04 and S1-10 were originally sequenced behind S1-01's answer. They were completed anyway by choosing the lookup form that is correct under **both** branches of that answer (§2.3), so the spike now gates only an optimisation rather than the design.

## 4. Environment constraint — Docker prohibited

Docker is **not permitted** in this environment. Not pending installation — unavailable by policy. Consequences, recorded as **RISK-S13**:

- `org.testcontainers:postgresql` ([pom.xml:107](../../pom.xml)) is dead weight.
- Every requirement whose correctness depends on the real `ENCRYPT`/`decrypt`/`masking` functions has **no automated verification**: FR-SNDC-004 (uniqueness), FR-SNDD-001…003 (archive-on-delete), C-S01…C-S05 (coexistence), and the D-S1 regression itself.
- [TEST-PLAN §2](../design/TEST-PLAN-SENDERNO.md) was rewritten around a four-tier strategy. The affected requirements are marked **not covered** rather than counted — following the "substitute, not an equivalent" phrasing `InstitutionMapperSqlTest` already uses.

**S1-01 was re-specified.** It had been written against Testcontainers; that was over-specification on my part, since determinism is a property of the *production* function and stock PostgreSQL in a container could never have answered it authoritatively. It is now one statement for a DBA:

```sql
SELECT ENCRYPT('01012345678') = ENCRYPT('01012345678') AS is_deterministic;
```

The critical path is therefore a turnaround time, not a development task.

**PM decision on record:** asked whether to resolve DB access before continuing, the PM directed the team to skip it and proceed. Implementation continues on pure-logic components; the verification limitation stands and **must be declared at G3** rather than presented as coverage.

## 5. 7-dimension self-assessment

| Dimension | Weight | Score | Note |
|-----------|--------|-------|------|
| 완성도 Completeness | 20% | **75** | 9 of 12; 2 blocked externally, 1 deliberately deferred to S2 |
| 추적성 Traceability | 15% | **95** | Every method carries `// source:` or `// req:`; defect IDs cited throughout |
| 보안 Security | 20% | **85** | Server-side `@PreAuthorize` and session-derived scope in place with tests; audit excludes numbers by assertion. Held below 90 by the deferred endpoint sweep and the open CSRF defect |
| 성능 Performance | 10% | **65** | Paging, size clamp, overflow-safe offset, deterministic order. But the lookup is a deliberate full scan pending S1-01, and nothing can be measured without a DB |
| 가독성 Readability | 15% | **95** | Korean + English Javadoc throughout; rationale recorded at each non-obvious decision |
| 표준 준수 Standards | 10% | **90** | No directory violations; plan corrections recorded in the design docs rather than left stale |
| 테스트 커버리지 Coverage | 10% | **60** | Delivered classes thoroughly covered at unit level; **every DB-dependent path is tier 3** and the headline D-S1 regression cannot be executed at all |

**Weighted: 81 / 100 — below the 90 threshold** (was 68).

Not escalated as a quality failure, but the reason has changed. On the first pass the gap was completeness; now it is **verification depth**. Coverage and performance cannot rise above their current scores in this environment, because the two things holding them down — no executable DB and an unconfirmed `ENCRYPT` — are the same constraint. **This sprint cannot reach 90 without either a database or an explicit decision to lower the threshold**, and that is a PM call rather than something the team can close by writing more code.

## 6. Next

1. **S1-07 / S1-08 / S1-09** — authorization, tenant scope, read audit. No DB and no spike dependency; consumes `TenantContext` and `AuditService` unchanged.
2. **S1-06 + S1-11** — row model and list UI.
3. **Raise S1-01 and S1-03 with whoever holds DB and configuration access** — both are minutes of someone else's time and they gate the mapper, the service and all of S2.
4. **Route the CSRF finding** to the 로그인 slice owner.

## 7. Carried to the programme

| Item | Owner |
|------|-------|
| Authenticated-session CSRF cookie asymmetry (§1) | 로그인 slice |
| RISK-S13 — no DB-backed verification path | PM + architect |
| Institution Sprint I1 remains open — 7 tasks, including the authorization sweep (T-I1-10) and negative-path suite (T-I1-13) that overlap S1-07/08/12 | team-leader |
