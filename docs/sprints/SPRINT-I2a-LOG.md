# Sprint I2a Log — 이용기관관리 edit path (기관코드 → 수정 팝업)

> **Sprint**: I2a · **Date**: 2026-08-20
> **Plan**: [DEV-PLAN-INSTITUTION.md §4.4](../design/DEV-PLAN-INSTITUTION.md) (T-I2a-01…11)
> **Predecessor**: [SPRINT-I1-LOG.md](SPRINT-I1-LOG.md) — **PARTIAL**, 11 of 18 tasks; seven still open
> **Status**: **COMPLETE — 11 of 11 tasks.** 7-dimension **90.7**. Backend 933 tests (1 pre-existing failure, §5); frontend 197 tests green

---

## 1. What shipped

The 기관코드 link in the list screen went nowhere. It now opens the 이용기관 수정 popup, ported from
`biztalk_admin_01_view.jsp` / `biztalk_admin_01.js`, with the write path behind it.

| Task | Deliverable | Evidence |
|------|-------------|----------|
| T-I2a-01 | [`AtkGenerator`](../../src/main/java/com/webcash/iris/biztalk/domain/AtkGenerator.java) — `SecureRandom`, 27 Base62 chars (≈160.7 bits) | 100% instruction / 100% branch; 6 cases incl. 1,000-key uniqueness |
| T-I2a-02 | `InstitutionAdminMapper#findByCode` + XML, excluding `IS_STTS='D'` | `InstitutionAdminMapperXmlTest$DetailRead` |
| T-I2a-03 | `update` statement — 8 columns, **no `ATK`**, `to_char(now(),'YYYYMMDDHH24MISS')` | `$UpdateStatement` (5 cases), `$Timestamps` (3) |
| T-I2a-04 | `GET /api/admin/institutions/{code}` returning the masked row | `detailReturnsMaskedKey` asserts `$.authKey` does not exist |
| T-I2a-05 | [`InstitutionWriteService`](../../src/main/java/com/webcash/iris/biztalk/domain/InstitutionWriteService.java) — server-side validation, audit with before/after | 32 cases; 97.4% instruction / 91.7% branch |
| T-I2a-06 | `rotateAuthKey` mapper + service method, own audit action | `neverAuditsKeyMaterial`; `$RotationStatement` asserts it is the only key writer |
| T-I2a-07 | `PUT /{code}`, `POST /{code}/key/rotate`, 404 advice above the catch-all | `InstitutionWriteAuthorizationTest` — 18 cases |
| T-I2a-08 | [`InstitutionEditDialog.tsx`](../../src/main/frontend/src/features/biztalk/InstitutionEditDialog.tsx) — labelled dialog, focus trap, Esc, per-field errors | 20 component cases + axe clean |
| T-I2a-09 | Rotation confirmation strip; new key shown once | `확인 없이는 재발급하지 않는다`, `취소하면 아무 일도 일어나지 않는다` |
| T-I2a-10 | Test suites — service, generator, mapper XML, modal, accessibility | 70 backend + 22 frontend cases added |
| T-I2a-11 | Negative-path suite — non-operator, CSRF, `status='D'`, body-supplied key/code | `$Unauthenticated`, `$WrongRole`, `$Csrf`, `$Validation` |

### 1.1 Defects closed

| Defect | How | Regression test |
|--------|-----|-----------------|
| **D-I20** *(discovered this sprint)* | The detail service returned the plaintext 인증키 and the popup put it in the DOM. Masking now happens on the single `AtkMasker` path, so no read path can emit it | `masksAuthKeyOnDetail`, `detailReturnsMaskedKey`, `평문 인증키가 어디에도 없다` |
| **D-I4** | Key generation moved from browser `Math.random()` to server `SecureRandom` | `AtkGeneratorTest` (6) |
| **D-I9** | `to_char(now(),'YYYYMMDD24MISS')` → `…HH24MISS`; the `24`-hour pattern asserted absent | `usesHh24`, `hasNoHhLessPattern` |
| **D-I19** | Every validation rule moved server-side, at two entry points over one set of constants | `refusesInvalidInput` (6 params), `$Validation` (6) |
| **D-I6** *(edit half)* | The update is an `UPDATE`; no `INSERT` exists in the file, and 0 rows raises instead of inserting | `hasNoInsert`, `doesNotInsertWhenTargetVanished` |
| **D-I2** *(write endpoints)* | Three layers — routing, `@PreAuthorize`, service `requireOperator()` — each with a test asserting a **denial** | `$Unauthenticated` (3), `$WrongRole` (3), `refusesNonOperator` |

### 1.2 The three absences

The sprint's design choice, recorded because it is easy to undo by accident:

| Absent | Consequence |
|--------|-------------|
| `ATK` in the update statement | A save cannot overwrite a credential — including with the mask the form holds (TM-I022) |
| `authKey` in the request record | The mask has nowhere to travel; only rotation writes a key |
| `code` in the request record | The path is the only source of the target (FR-INSTC-002) |

Each is asserted by a test, so removing an absence fails the build rather than passing review.

## 2. Deviations from the plan

### 2.1 `InstitutionCacheNotifier` was not built — PM ruling AMB-I11

FR-INSTC-008 named a cache that lives inside the IRIS_ADMIN process. A separate process cannot reach
it and the portal keeps none of its own. The requirement was rewritten to portal-side invalidation;
the legacy cache's staleness is tracked as RISK-I13. **A component that could not do what its name
claims is worse than its absence.**

### 2.2 `LAST_AMDT` is written by the database, not the injected `Clock` — [ADR-INST-017](../design/adr/ADR-INST-017-timestamp-clock-authority.md)

Raised as CONFLICT-I03 during the gap pass. The programme's `Clock` is `systemUTC()` — correct for
audit ordering across instances, and wrong for a wall-clock `VARCHAR` column that a second live
system also writes. Using it would have put every portal row **nine hours behind** the legacy rows
in the same column, with nothing failing.

> The consistent-looking choice was the wrong one for exactly two columns. That is why it is an ADR
> and not a comment.

### 2.3 Rotation commits on confirmation, not at 저장 — PM ruling AMB-I13

The legacy held a browser-generated key in the form until 저장, so 닫기 discarded it. Committing at
confirmation means the audit record can never name a key that was not stored. One consequence is
counter-intuitive enough to have its own test: **a confirmed rotation survives 닫기** (S-29).

### 2.4 `InstitutionWriteService` is a component the architecture did not name

The component table assigned the read path and the lifecycle path and left 등록/수정 homeless.
Recorded in DEV-PLAN §3 rather than introduced silently.

## 3. Findings raised

| ID | Finding | Severity | State |
|----|---------|----------|-------|
| **SI2a-01** | A **production-observed 인증키** (`6oG4…zy8o`, the value on the live K00000 screen) was test data in five files — two from Sprint I1, three added by this sprint. `ATK` is stored plaintext and verified directly by the legacy runtime, so a live value in the repository is a working credential. gitleaks flagged it; the L1 hook would have blocked the commit | **HIGH** | **FIXED** — synthetic values across the slice; gitleaks on `src` 10 → 6, none in this slice. Conditions 1–2 in the [audit](../../security/audit-institution-I2a.md) |
| **SI2a-02** | The API layer had **0% coverage** — controller, request record and 404 advice untested, so `@PreAuthorize` and the advice ordering were claims. The service-level denial test reads as if it covered FR-AZ-I01; it does not | **HIGH** | **FIXED** — `InstitutionWriteAuthorizationTest`, 18 cases incl. CSRF refusal and an advice-ordering assertion |
| **SI2a-03** | The dialog rendered **no visible close control** when the detail read failed: the action row lived inside the form block, and a backdrop click deliberately does not close. Escape worked; nothing said so | Medium | **FIXED** + regression test |
| **SI2a-04** | Nine methods carried no `// req:`/`// source:` tag (Skill 04 §5) | Medium | **FIXED** |
| **SI2a-05** | Read-only values used `<output>`, an implicit `role="status"` live region | Low | **FIXED** — plain `<span>` |
| **SI2a-06** | `mapping/trace/requirements-trace-biztalk.csv` had **no institution rows at all** — Sprint I1's 11 tasks were never traced there. I2a added 30 rows; **I1's are still missing** | Medium | **OPEN** — `trace-mapper`, Sprint I2b |
| **SI2a-07** | `post()` in `alimTalkApi.ts` omits `csrfHeader()`, so every 알림톡 POST should be answered 403. Not this slice; noticed while adding the header here | Medium | **OPEN** — 알림톡 slice owner |
| **SI2a-08** | Sprint I1's SI-05 (16 failing frontend tests) is **no longer reproducible** — the full frontend suite is 197/197 green | — | **CLOSED** by observation |

## 4. Carried / not built

| Item | Requirement | Lands in |
|------|-------------|----------|
| 등록 (create) + availability check | FR-INSTC-004/005 | I2b |
| 중지 / 재사용 / 삭제 | FR-INSTL-\* | I2b |
| Key reveal | FR-ATK-003 | I2b |
| Optimistic `LAST_AMDT` check against concurrent legacy edits | TM-I019 / RISK-I04 | I2b |
| **T-I1-13** — HTTP-layer denial test for `GET /search` | FR-AZ-I01 | I2b (carried from I1) |
| **T-I1-16** — load measurement | NFR-PERF-I01/I03 | I2b (carried from I1) |
| Write-path P95 measurement | NFR-PERF-I03 | I2b — see §6, the weakest dimension |

## 5. Blocked / degraded

| Item | Status | Effect |
|------|--------|--------|
| **Docker not installed** | BLOCKING (RISK-I09) | The three new SQL statements are verified by **reading the XML**. That is a substitute, not an equivalent: it cannot prove the columns exist, that `to_char` returns what we expect, or that `IS_STTS <> 'D'` behaves as read |
| `CsrfIntegrationTest.echoingCookieValueInHeaderPasses` | **PRE-EXISTING FAILURE** | 1 of 933 backend tests. Verified to fail identically on a clean `HEAD` worktree, so it is **not** attributable to I2a. It asserts that an authenticated request receives an `XSRF-TOKEN` cookie (CR-02) and belongs to the 로그인 slice |
| RISK-I01 data audit, RISK-I06 column semantics | PM / DBA action | Still open from I1; neither blocks the edit path |

## 6. 7-dimension self-assessment

| Dimension | Weight | Score | Basis |
|-----------|--------|-------|-------|
| 완성도 | 20% | **95** | 11 of 11 tasks. Not 100: T-I2a-02/03's verification is degraded to XML reading (RISK-I09) |
| 추적성 | 15% | **95** | Every method tagged, 30 trace rows, ADR-INST-017, D-I20 registered. Deducted because nine tags were missing until review caught them (SI2a-04) |
| 보안 | 20% | **90** | Three-layer authz proven, CSRF proven, D-I20 closed, no key in audit or logs. Deducted for SI2a-01 — this sprint **propagated** an observed credential into three new files before fixing it |
| 성능 | 10% | **70** | **The weak dimension.** NFR-PERF-I03 (P95 < 1 s per save) is unmeasured. The save is three round trips on a table of hundreds of rows, so it is plausible — but plausible is not measured |
| 가독성 | 15% | **95** | Bilingual Javadoc on every public type, rationale rather than restatement, tables in the docs |
| 표준 준수 | 10% | **92** | ADRs present, zero DDL, no file edited by two roles at once. Deducted for one stray artifact created and removed during the sprint (`n`), and because Conventional Commits is untested — no commit was made |
| 테스트 커버리지 | 10% | **90** | `InstitutionWriteService` 97.4%/91.7%, `AtkGenerator` and `InstitutionService` 100%, against a ≥95% domain target. Controller 78.6% — the uncovered method is I1's `search`. Mapper SQL substitute only |

**Weighted total: 90.7 / 100** — above the 90 threshold, so no regeneration loop was entered.

> The honest reading of that number: the sprint is strong on the dimensions a test can prove and
> weak on the one that needs a measurement. Two of the three HIGH findings were **self-inflicted**
> (a propagated credential, an untested API layer) and were caught by the review and audit steps
> rather than by the implementation — which is the argument for running those steps at all.

## 7. DoD status

- [x] Sprint tasks 100% complete or explicitly carried — **11 of 11**; §4 lists what was not in scope
- [x] 7-dimension ≥ 90 — **90.7**
- [x] Backend build + tests PASS — 933 run, 1 **pre-existing** failure (§5), 0 attributable to I2a
- [x] Frontend typecheck + build PASS; suite **197/197** green
- [x] `code-reviewer` verdict — **APPROVE** with two carry-forward conditions ([review](../../reviews/code-review-institution-I2a.md))
- [x] `security-auditor` verdict — **APPROVED WITH CONDITIONS**, no CVSS ≥ 7.0 open in slice ([audit](../../security/audit-institution-I2a.md))
- [x] Sprint retro written — [SPRINT-I2a-RETRO.md](SPRINT-I2a-RETRO.md)
- [ ] **PM sprint-gate approval** — pending
- [ ] G1 / G2 remain **PENDING** at PM level; this sprint was implemented against approved-in-draft plans, following the precedent set by I1

## 8. Next

1. **PM**: confirm whether the two 인증키 values in §3/SI2a-01 are live. If they are, source removal is
   necessary but not sufficient — they remain in git history and those institutions should be rotated
2. **T-I1-13** — the denial test for `GET /search`. Both the review and the audit carry it as a
   condition; D-I2 is the slice's highest-severity defect and one of the controller's four endpoints
   is still asserted only by annotation
3. Docker or embedded PostgreSQL — gates T-I2a-02/03 verification, T-I1-02, T-I1-15 and every mapper
   test in I2b
4. Sprint I2b: 등록 + availability, lifecycle (중지/재사용/삭제), key reveal, optimistic `LAST_AMDT`
5. `trace-mapper`: backfill Sprint I1's rows (SI2a-06)
6. Measure the write path against NFR-PERF-I03 — the one dimension this sprint cannot claim
