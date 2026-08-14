# Sprint I1 Log — 이용기관관리 read path + foundation correction

> **Sprint**: I1 · **Date**: 2026-08-14
> **Plan**: [sprint-I1-tasks.md](../design/sprint-I1-tasks.md) · [DEV-PLAN-INSTITUTION.md](../design/DEV-PLAN-INSTITUTION.md)
> **Status**: **PARTIAL — 11 of 18 tasks complete.** Backend 185 tests green; frontend 19 new tests green. Sprint **not** closed; DoD not met

---

## 1. The unplanned task that came first

The sprint opened by discovering the **test suite had never compiled**.

```
mvn test → BUILD FAILURE at maven-compiler-plugin:testCompile
```

`AuthenticationService` had gained four constructor parameters (`SecretCipher`, `OtpReplayGuard`, `IpAllowlistPolicy`, `AdminLoginNotifier`) and `UserAccount` a twelfth field (`institutionCode`), and neither test was updated. Both the tests and the production code were last touched in the **same commit** (`70b5ba1`), so these tests did not compile even when they were written.

What that means for the record:

| Claim on file | Actual |
|---------------|--------|
| Sprint L8: 55/59 requirements complete | Backed by tests that could not run |
| Sprint M4: 47/52 complete | `biztalk` package had **0%** coverage |
| DEV-PLAN targets: ≥80% line / ≥70% branch | **No coverage report had ever been produced** |

This was repaired before anything else, because every remaining task in the sprint would otherwise have produced unverifiable code.

**Repair** — [AuthenticationServiceTest.java](../../src/test/java/com/webcash/iris/auth/domain/AuthenticationServiceTest.java), [AccountPolicyTest.java](../../src/test/java/com/webcash/iris/auth/domain/AccountPolicyTest.java): added the four missing mocks, wired an identity `cipher.decrypt` and a passing `replayGuard.tryConsume` in setup so every existing assertion kept its original meaning, and supplied the twelfth `UserAccount` argument. No test intent was changed or weakened.

### 1.1 First coverage baseline

| Metric | At sprint start | Now |
|--------|-----------------|-----|
| Backend tests executing | **0** | **185** |
| Instruction | 21.9% | 27.1% |
| Branch | 27.4% | 33.3% |

The baseline is far below the ≥80%/≥70% the three DEV-PLANs assert. `com.webcash.iris.biztalk` — the 문자내역 slice reported at 47/52 — was at **0%** and is now covered only where this sprint touched it.

## 2. Completed tasks

| ID | Task | Evidence |
|----|------|----------|
| — | **Test-compile repair** (unplanned) | 145 tests execute; was 0 |
| **T-I1-01** | **`InstitutionMapper` corrected** | `BIZTALK_INSTITUTION`→`FT_FTIS_INFO`, `IS_CD`→`FINTECH_ISCD`, `IS_NM`→`ISNM`, `USE_YN`→`IS_STTS` |
| T-I1-02 | Verification for T-I1-01 | **DEGRADED** — see §4 |
| T-I1-03 | `InstitutionStatus` enum | 100% instruction, 100% branch |
| T-I1-04 | Name-based column mapping in `InstitutionAdminMapper.xml` with `FIX D-In:` annotations | Ported SQL in XML per convention |
| T-I1-05 | Search mapper — `LIMIT`/`OFFSET` + `COUNT(*)` | `InstitutionSearchCriteria` 100% |
| T-I1-06 | `LIKE` escaping + NULL-safe name filter | 12 pattern tests |
| T-I1-07 | `AtkMasker` | 100% instruction, 100% branch |
| T-I1-08 | `InstitutionService` — search, masking, status labelling | 100% |
| T-I1-09 | `GET /api/admin/institutions/search` with `@PreAuthorize` | — |
| T-I1-12 | React 이용기관 list screen | 19 component tests |
| T-I1-17 | Zero-DDL confirmed | No migration added; ADR-INST-014 needs none |

**Backend coverage on everything this sprint wrote: 100%.**

| Class | Instruction |
|-------|-------------|
| `AtkMasker` | 100% |
| `InstitutionStatus` | 100% |
| `InstitutionSearchCriteria` | 100% |
| `InstitutionService` | 100% |
| `InstitutionRow` | 100% |

### 2.1 T-I1-01 — the correction

`InstitutionMapper` was delivered in the 문자내역 slice against a **guessed** table. Its Javadoc recorded the guess honestly and asked for DBA confirmation; nothing forced that confirmation before merge. Skill 2's analysis of screen 00 supplied the answer and **all four identifiers were wrong**.

The root cause is worth keeping: the mapper was written from `biztalk_admin_40.js`, which uses **service-contract field names**, not table column names. The legacy IDO bridges the two by mapping `SELECT` order to `<out>` order **positionally**, so the gap is invisible unless the IDO SQL is read directly.

**Consequence:** before this fix the query could not run against the real database — the 문자내역 institution dropdown does not populate outside tests. Any other mapper written from JS alone shares this exposure.

### 2.2 T-I1-03 / T-I1-07

`InstitutionStatus` implements ADR-INST-014's three-valued lifecycle and is the sole write path to `IS_STTS`. `LegacyCompatibility` tests assert the property the ADR depends on: `'D'` equals neither `'Y'` nor `'N'`, so a logical delete drops out of legacy filters by construction.

`AtkMasker` implements FR-ATK-002. Tests assert that no leading character survives and that a key of ≤ 4 characters is masked **entirely** — the last-four rule applied naively to a short key would disclose the whole value.

### 2.3 Frontend — T-I1-12

[InstitutionPage.tsx](../../src/main/frontend/src/features/biztalk/InstitutionPage.tsx) and
[institutionApi.ts](../../src/main/frontend/src/api/institutionApi.ts), with 19 passing component tests.

Five deliberate departures from the legacy screen, each covered by a test:

| Change | Legacy behaviour |
|--------|------------------|
| 인증키 arrives masked; the plaintext never reaches the client | Every institution's key rendered in a plaintext column (D-I5) |
| Server paging — page changes issue a request | Whole registry fetched, sliced client-side (D-I10) |
| 등록일시/수정일시 show **date and time** | `substring(0,8)`, date only — the truncation that hid D-I9 |
| Unmapped status renders verbatim | Anything but `'Y'` shown as 미사용, hiding data anomalies |
| No 담당자관리 tab | Tab present in markup but commented out (D-I13) |

**No write buttons were added.** The legacy left 추가/삭제 buttons in the markup with no event
handlers at all (D-I13); shipping 등록/수정/중지/삭제 before Sprint I2 implements them would
repeat that exact defect. A test asserts their absence, so adding one without its operation
fails the suite.

`D-I9` has its own frontend test: `formatTimestamp('20210401241500')` must render
`2021-04-01 24:15:00` — the malformed hour is **displayed, not corrected**. Repairing it at
render time would hide the data problem the way the legacy truncation did.

## 3. Not started — 7 tasks

T-I1-02 (degraded, §4), T-I1-10, T-I1-11, T-I1-13, T-I1-14, T-I1-15, T-I1-16.

That is the authorization sweep, audit wiring, and the security, regression, coexistence and
load suites. **The endpoint carries `@PreAuthorize("hasRole('OPERATOR')")` but no test yet
proves it denies a non-operator** — under TEST-PLAN §1.1 that control is asserted, not
demonstrated. Closing T-I1-10 and T-I1-13 is the highest-value work remaining.

## 4. Blocked / degraded

| Item | Status | Effect |
|------|--------|--------|
| **Docker not installed** | **BLOCKING** (RISK-I09) | Testcontainers cannot start. T-I1-02 degraded to [InstitutionMapperSqlTest](../../src/test/java/com/webcash/iris/biztalk/infra/db/InstitutionMapperSqlTest.java), a reflection-based identifier guard. It prevents the RISK-I05 regression but **cannot** verify the table exists or that column types match. T-I1-15 (coexistence C-01) cannot run at all |
| No PostgreSQL | BLOCKING | Same |
| RISK-I01 data audit | PM action | Needed before Sprint I2 |
| RISK-I06 column semantics, `BSNN_STTS_CKYN` | DBA | Needed before Sprint I2 write path |
| RISK-I10 기관코드 format | **CLOSED** | Live screen confirms 6 chars, `K0` + 4 digits (AMB-I06 option A) |

> The substitute test is recorded as a substitute. It is not equivalent to the integration test the plan calls for, and the sprint should not be read as having verified the mapper against a database.

## 5. Findings raised

| ID | Finding | Severity |
|----|---------|----------|
| SI-01 | Test suite never compiled; all prior completion percentages are unbacked by executed tests | **HIGH** |
| SI-02 | `OtpReplayGuard`, `IpAllowlistPolicy`, `AdminLoginNotifier` are wired into `AuthenticationService` but have **no unit tests** — three controls with no evidence they deny anything. Belongs to the 로그인 slice, not I1 | **MEDIUM** |
| SI-03 | Live legacy screenshot confirms D-I4 exactly: 인증키 values are 20 characters over the `Math.random()` alphabet | — |
| SI-04 | 기관코드 values are **sequential** (`K00000`, `K00001`, …), so the D-I3 duplicate-check disclosure needs ~60 guesses, not a brute-force campaign. **TM-I002 should be re-rated upward** | **HIGH** |
| SI-05 | **16 pre-existing frontend tests fail** — all of `LoginPage.test.tsx` (13) and 3 of `accessibility.test.tsx`, every one at `LoginPage.tsx:51` with `TypeError: localStorage.clear is not a function`. The vitest config is correct (jsdom + setup file), so this looks like a jsdom 29 / vitest 4 environment issue, not a code defect. Untouched by this sprint and **not investigated further** — it belongs to the 로그인 slice. Consequence: the frontend suite has never been fully green either | **MEDIUM** |

## 6. 7-dimension self-assessment

**Not performed.** The assessment presumes a completed sprint; with 12 of 18 tasks unstarted the completeness dimension alone caps the score well below 90. Recording a number here would misrepresent the state.

## 7. DoD status

- [ ] Sprint tasks 100% complete or explicitly carried — **11 of 18**
- [ ] 7-dimension ≥ 90 — not performed
- [x] Backend build + tests PASS — 185 tests, 0 failures
- [x] Frontend typecheck PASS; 19 new tests PASS
- [ ] Frontend suite fully green — **16 pre-existing failures remain** (SI-05)
- [ ] code-reviewer APPROVE — not run
- [ ] security-auditor approval — not run
- [ ] Sprint retro — deferred until the sprint closes
- [ ] PM sprint-gate approval

## 8. Next

1. **T-I1-10 + T-I1-13** — the authorization sweep and negative-path suite. The endpoint is
   annotated but no test proves it denies a non-operator, and D-I2 is the highest-severity
   defect in the slice
2. Install Docker, or amend TEST-PLAN-INSTITUTION §5 to an embedded/shared PostgreSQL. **This
   decision gates T-I1-02, T-I1-15 and every mapper test in Sprint I2**
3. T-I1-11 audit wiring, T-I1-14 regression suite, T-I1-16 load test
4. Re-rate TM-I002 per SI-04
5. SI-05 — decide whether the 로그인 frontend suite is repaired now or carried
6. PM: RISK-I01 data audit
