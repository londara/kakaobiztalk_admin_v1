# Sprint S2a Log — 발신번호 등록 · 삭제 (write path)

> **Sprint**: S2a · **Date**: 2026-08-20
> **Plan**: [sprint-S2a-tasks.md](../design/sprint-S2a-tasks.md) (S2a-01…13) · [DEV-PLAN-SENDERNO.md](../design/DEV-PLAN-SENDERNO.md) v1.1
> **Predecessor**: Sprint S1 — list, authorization, tenant scope, paging, read audit
> **Status**: **COMPLETE — 12 of 13 tasks.** 7-dimension **91.4**. Backend 999 tests (1 pre-existing failure, §5); frontend 218 tests green

---

## 1. What shipped

Sprint S1 left 등록 and 삭제 on screen as disabled buttons. They now work — and the one that matters
**actually deletes**.

| Task | Deliverable | Evidence |
|------|-------------|----------|
| S2a-01 | **Not done — operator-team prerequisite.** See §4 | Blocks nothing in code; blocks applying the DDL |
| S2a-02 | [`BarredNumbers`](../../src/main/java/com/webcash/iris/biztalk/domain/BarredNumbers.java) + [`barred-numbers.txt`](../../src/main/resources/senderno/barred-numbers.txt) — loaded resource, fail-loud | `BarredNumbersTest` — 11 cases incl. empty / comment-only / non-numeric / missing |
| S2a-03 | [`V3__senderno_archive.sql`](../../src/main/resources/db/V3__senderno_archive.sql) — `KKB_DPNO_ARCV` via `LIKE`, unique index, determinism precheck | `SenderNumberMapperIntegrationTest` builds the same schema and exercises it |
| S2a-04 | `SenderNumberRegistration`, `SenderNumberDeletion`, `SenderNumberLimits`, three exceptions | `SenderNumberWriteServiceTest` — 26 cases |
| S2a-05 | [`SenderNumberWriteService#register`](../../src/main/java/com/webcash/iris/biztalk/domain/SenderNumberWriteService.java) — global uniqueness, ledger + history in one transaction | `$Register` (11 cases) |
| S2a-06 | `GET /context` + [`SenderNumberContextResponse`](../../src/main/java/com/webcash/iris/biztalk/api/SenderNumberContextResponse.java) — code and name only | `contextCarriesNoCredential` asserts `$.authKey`, `$.authKeyMasked`, `$.businessNumber` all absent |
| S2a-07 | `#delete` — archive → delete → per-number history, one transaction | `$Delete` (8 cases); `runsArchiveBeforeDelete` pins the order |
| S2a-08 | `SenderNumberNotLiveException` → 409 | `noLiveRowIsConflictNotSuccess` — **the sprint's acceptance criterion** |
| S2a-09 | Two audit actions, [`SenderNumberExceptionHandler`](../../src/main/java/com/webcash/iris/biztalk/api/SenderNumberExceptionHandler.java) | `auditCarriesNoNumber`, `auditCarriesCountNotNumbers`, `serverValidationNamesTheField` |
| S2a-10 | [`SenderNumberRegisterDialog`](../../src/main/frontend/src/features/biztalk/SenderNumberRegisterDialog.tsx) — legacy screen 12 layout | 10 component cases |
| S2a-11 | [`SenderNumberDeleteDialog`](../../src/main/frontend/src/features/biztalk/SenderNumberDeleteDialog.tsx) — enumerates every selected number | 9 component cases |
| S2a-12 | List wiring — enablement, selected count, post-write refresh, selection lifetime | 4 new page cases; the S1 placeholder test rewritten (§3, SS2a-01) |
| S2a-13 | Write-path regression, security and coexistence suites | 66 backend + 21 frontend cases added |

### 1.1 Defects closed

| Defect | How | Regression test |
|--------|-----|-----------------|
| **D-S1** | Deletion is a row move (`archive` → `deleteLive`), the row count of **every** statement is checked, and a zero-row match is a **409** — never a 200 | `noLiveRowIsConflictNotSuccess`, `failsWhenDeleteRemovesNothing`, `failsWhenNothingToArchive`, `aMaskedValueMatchesNothingAndReportsZero`, `aCommaJoinedListMatchesNothing`, `displayValueAsIdentifierIsRejected` |
| **D-S5** | The history row is built from the loop's single number; the mapper signature takes one `String`, so a list has nowhere to go | `writesOneHistoryRowPerNumber`, `threeDeletionsWriteThreeDistinctRows` |
| **D-S6** | One `@Transactional` boundary over archive + delete + history for every target | `runsArchiveBeforeDelete`, `failsWhenNothingToArchive` |
| **D-S7** | Each statement's row count checked at the point of the call, not the previous one's | `historyFailureFailsTheRegistration` |
| **D-S9** | `countAnywhere` has no `IS_CD` predicate, **and** a DB unique index binds `AOA_ADMIN` too | `countIsInstitutionBlind`, `databaseRefusesCrossInstitutionDuplicate`, `duplicateLookupIsInstitutionBlind` |
| **D-S11** | Every rule server-side; the register form duplicates none of them | `refusesNonNumeric`, `refusesBarredNumber`, `beanValidationIsFourHundred` |
| **D-S12** | The barred list exists, is loaded data, and an absent list **fails startup** | `BarredNumbersTest` (11), `rejectsBarredNumber` |
| **D-S13** | Digit check ahead of the length rules (ported in S1, now reachable from a write path) | `SenderNumberValidatorTest$Numeric` |
| **D-S15** | Lengths enforced at both entry points over one set of constants | `refusesOverLengthText` |
| **D-S16** | `RGSR_ID`/`UDT_ID` pass through `ENCRYPT()` — AMB-S09 ruling B | `actorIdIsEncrypted` |
| **D-S18** | The register form loads a two-field context endpoint, not the institution detail service | `contextCarriesNoCredential`, register-dialog `FR-SNDC-002` |
| **D-S2 / D-S3** *(write half)* | Three layers — routing, `@PreAuthorize`, service `requireOperator()` — each with a test asserting a **denial** | `SenderNumberWriteAuthorizationTest` (18) |

### 1.2 The absences

The sprint's design choices, recorded because each is easy to undo by accident. Every one is asserted
by a test, so removing it fails the build rather than passing review.

| Absent | Consequence |
|--------|-------------|
| `institution` in the register request record | The target has exactly one source; a body value cannot become a second answer (D-S3's write twin) |
| `authNo` in the register request record | Ownership verification is not built (AMB-S01), and a declared-but-dead input reads as a control that exists (D-S4) |
| `number` in the delete request | Only refs travel; a display value in the identifier position is unrepresentable (D-S1) |
| `IS_CD` in `countAnywhere` | That absence **is** FR-SNDC-004 |
| 발신번호 in every audit record | ADR-SND-019 applies to writes, not only reads (T-I4) |
| The holder's 기관코드 in the duplicate 409 | Registration is not an enumeration oracle |

---

## 2. Deviations from the plan

### 2.1 The mapper XML test was not written — the integration test superseded it

The plan followed I2a's precedent: Docker is prohibited, so mapper SQL is verified by **reading the
XML** (RISK-S13, TEST-PLAN §2 tier 3). That precedent is out of date. `io.zonky.test:embedded-postgres`
runs a real PostgreSQL **binary as a process** with no Docker, it is already in the `pom.xml`, and the
톡전송 slice proved it works here.

So this sprint wrote [`SenderNumberMapperIntegrationTest`](../../src/test/java/com/webcash/iris/biztalk/infra/db/SenderNumberMapperIntegrationTest.java)
instead — 14 cases against real SQL, the real mapper XML, and the V3 schema built by the same
statements. **Every shape assertion an XML test could have made is made behaviourally here**, so a
second file reading the same XML would add no evidence.

> **Why this matters more here than anywhere else in the programme.** D-S1 is a defect *between*
> layers: three individually correct pieces that delete nothing when composed. A mocked mapper cannot
> reproduce it — a double returns whatever row count the test chose. `aMaskedValueMatchesNothingAndReportsZero`
> executes the legacy's exact predicate against a real database and asserts it returns **zero without
> raising**. That is D-S1's premise, verified rather than described.

What the stand-in functions still cannot prove is stated in the test's own header: `ENCRYPT`'s real
determinism (spike S1-01) and `masking`'s real output format. Those remain open.

### 2.2 `MANDATORY` is structural, not merely tested

[ADR-SND-021](../design/adr/ADR-SND-021-barred-number-list.md) proposed keeping 112/114/119/1335
barred "by test, independent of configuration". `BarredNumbers.MANDATORY` unions them in on every
load instead. A configuration edit that makes `119` registrable is better made **unrepresentable**
than caught afterwards. The ADR's verification table still holds; the mechanism is stronger than it
specified.

### 2.3 The archive table is `CREATE TABLE … (LIKE KKB_DPNO_LDGR)`

The plan said "every ledger column plus deletion metadata", which invited hand-written types. Two of
those columns are `ENCRYPT()`'s return type, **and this repository does not have `ENCRYPT`'s
definition** (ADR-005 §4.3, unresolved). `LIKE` copies the definition the database already holds, so
no guess enters the DDL — and a restored row is structurally identical to the original by
construction, which is the archive's whole purpose.

### 2.4 The determinism spike became a precondition of the DDL

S1-01 (is `ENCRYPT` deterministic?) is still unanswered, and the unique index is only a real
constraint if it is. V3 §1 therefore asks the question itself and **raises** if the answer is no. An
open unknown became an enforced gate rather than an assumption: a unique index that enforces nothing
while being believed is worse than no index, and is D-S1's family.

### 2.5 `SenderNumberValidator.validate` now takes the barred list as an argument

Moving the list into data would have been undone by a static constant reading it back. The validator
stays a pure function and the list arrives as a parameter; the six existing test cases gained one
argument and still read the file that ships.

---

## 3. Findings raised

| ID | Finding | Severity | State |
|----|---------|----------|-------|
| **SS2a-01** | The S1 test `D-S8 — 구현되지 않은 동작에 눌리는 버튼을 두지 않는다` **passed for a new reason** after this sprint. It asserted both buttons disabled without selecting an institution; once enablement conditions landed it still passed — while no longer testing what its name claimed. An accidental pass is a test that has stopped working | Medium | **FIXED** — rewritten as `FR-SNDD-010`, now walking disabled → 등록 enabled → 삭제 enabled |
| **SS2a-02** | **Portal-written rows show a masked *email* where legacy rows show a masked *name*.** `RGSR_NM` is displayed via `masking(decrypt(RGSR_NM))` (FR-SND-008) and the portal's session carries only an email (`TenantPrincipal`), so 등록자 renders as `o*************m` rather than `김*수`. The institution slice set this precedent for `LSED_NM` (FR-INSTC-012); here it is *visible on a list column* | Medium | **OPEN** — PM/UX decision. Fix belongs to the 로그인 slice: add a display name to the session. Recorded rather than papered over |
| **SS2a-03** | **AMB-S09 ruling B has a cross-application consequence the ruling did not name.** Encrypting `RGSR_ID` means `AOA_ADMIN`'s 발신번호 상세 screen renders **ciphertext** in its 등록ID field for portal-written rows. Confirmed by reading `AOA_ADMIN/.../biztalk_admin_11_view.jsp` (it displays `RGSR_ID`) | Medium | **OPEN — needs PM acknowledgement.** Cosmetic, new rows only, in a console carrying all 21 defects (RISK-S05). Reversible in one mapper line |
| **SS2a-04** | **D-S8 is IRIS_ADMIN-specific.** The spec records screen 11 as unreachable dead code because `biztalk_admin_10_view.jsp` renders no `#btn_update`. `AOA_ADMIN`'s copy of the same screen **does** render it (line 96), so 상세/수정 is live in the parallel console | Low | **OPEN** — informational, changes S2b's inputs: the target behaviour can be observed rather than inferred |
| **SS2a-05** | `KKB_DPNO_HIS` and `KKB_DPNO_LDGR` DDL was never obtained; the integration test's schema is **derived from the IDO statements** (`IDO.KKB_DPNO_HIS_C001` etc.). Column *names* are exact — they are quoted in the ported SQL — but types and nullability are inferred | Medium | **OPEN** — DBA to confirm before deployment. Same class as ADR-005 §4.3 |
| **SS2a-06** | The `/context` endpoint returns any institution's name to any operator and writes **no audit record**. Consistent with the institution slice's detail endpoint, so not a regression | Low | **OPEN** — raise with the institution slice owner; a name is not a sender number, so ADR-SND-019 does not obviously apply |

> **On SS2a-03 and SS2a-04 together.** Both came from reading `AOA_ADMIN` rather than our own code,
> and neither was findable any other way. The consumer-survey discipline ADR-SND-017 established for
> *readers of a table* applies just as much to **readers of a column**.

---

## 4. Carried / not built

| Item | Requirement | Lands in |
|------|-------------|----------|
| **S2a-01 — duplicate reconciliation** | CONST-BIZ-D01, RISK-S02 | Operator team + DBA. **Prerequisite for applying V3**, not for writing it |
| Applying V3 (archive table + unique index) | CONST-DATA-D04 | **Blocked on G1** — CONFLICT-S01 |
| 상세 / 설명수정 (screen 11) | FR-SNDU-001…006, D-S8, D-S10 | Sprint S2b |
| A cap on numbers per institution | AMB-S07 | Not built by working assumption A |
| Cascade from institution deletion | CONST-BIZ-D04 | **Not built by PM ruling** (AMB-S08); RISK-S14 tracks the residual |
| Ownership verification | AMB-S01 | Not built by PM ruling; RESIDUAL-S01, threat T-S2 |
| Load measurement against NFR-PERF-D02/D03 | NFR-PERF-D02, NFR-PERF-D03 | S2b — see §6, the weakest dimension **again** |
| C-S01 / C-S05 coexistence tests against the real `KAKAOTALK` / `AOA_ADMIN` schema | FR-SNDD-003 | Blocked on staging (RISK-S10). Partially substituted: `databaseRefusesCrossInstitutionDuplicate` proves the constraint binds a caller that bypasses our code |

---

## 5. Blocked / degraded

| Item | Status | Effect |
|------|--------|--------|
| **G1 not approved** | BLOCKING deployment, not development | The DDL is written and exercised against embedded PostgreSQL; it is **not applied** anywhere. §2.4's precheck means it cannot be applied on an unverified assumption either |
| **Spike S1-01 unanswered** | Open (RISK-S07) | The unique index's *form* is right only if `ENCRYPT` is deterministic. V3 §1 refuses to proceed otherwise, so the risk is contained rather than closed |
| Real `ENCRYPT`/`decrypt`/`masking` definitions | Unavailable (ADR-005 §4.3) | The integration test uses same-name stand-ins. Statement shape is verified; the functions' behaviour is not |
| `CsrfIntegrationTest.echoingCookieValueInHeaderPasses` | **PRE-EXISTING FAILURE** | 1 of 999 backend tests. Documented as pre-existing in [SPRINT-I2a-LOG §5](SPRINT-I2a-LOG.md) and verified there against a clean `HEAD`. This sprint touched no security, cookie or CSRF file (`git status` confirms), so it is **not** attributable to S2a. Belongs to the 로그인 slice |
| RISK-S04 deletion reconciliation | PM / operator action | Still open from Skill 2 §6.4. **Unblocked by this sprint and more urgent because of it**: once deletion works, an operator may "re-delete" a number they believe is gone and get a 409 they cannot explain |

---

## 6. 7-dimension self-assessment

| Dimension | Weight | Score | Basis |
|-----------|--------|-------|-------|
| 완성도 | 20% | **92** | 12 of 13 tasks; S2a-01 is operator-team work by design. Not higher because the DDL is written and tested but **not applied** — the requirement is not satisfied in any environment yet |
| 추적성 | 15% | **96** | Every method carries `// req:` / `// source:`; 49 trace rows added (0 existed for this slice); the ported SQL quotes its legacy original inline. Deducted because two ADR statements were superseded by better mechanisms (§2.2, §2.3) and the ADRs were not amended in place |
| 보안 | 20% | **94** | Three-layer authz proven at the HTTP layer (the SI2a-02 lesson applied ahead of review), CSRF proven, D-S16 closed, no number in any audit record or log, duplicate 409 discloses no holder, no credential in the context response. Deducted for SS2a-03: a ruling was implemented without its cross-application cost being named until now |
| 성능 | 10% | **70** | **The weak dimension, for the second sprint running.** NFR-PERF-D02 (P95 < 1 s) and NFR-PERF-D03 (100-number delete < 5 s) are **unmeasured**. The 100-delete is 100 iterations × 3 statements in one transaction — plausible, and plausible is not measured. `DELETE_BATCH_MAX` bounds it but does not time it |
| 가독성 | 15% | **95** | Bilingual Javadoc on every public type; rationale rather than restatement; the SQL carries `FIX D-Sn:` markers on every altered line |
| 표준 준수 | 10% | **95** | ADRs present and followed, no file edited by two roles at once, no secret-bearing config read or written (SEC-001 — the barred list is a plain `.txt` for exactly this reason). Deducted because Conventional Commits is untested: **no commit was made** |
| 테스트 커버리지 | 10% | **93** | 66 backend + 21 frontend cases. The four critical defects have executable regressions against a real database, which S1 could not claim. Deducted because FR-SNDD-003's send-path half is verifiable only at cutover (RISK-S03) and the load tests are absent |

**Weighted total: 91.4 / 100** — above the 90 threshold, so no regeneration loop was entered.

> The honest reading: this sprint is stronger than I2a on exactly the dimension that mattered most —
> the headline defect now has an *executable* regression rather than a described one — and weaker on
> the same one I2a was weak on. **성능 has now been the lowest dimension twice in a row, both times for
> the same reason: no measurement was attempted.** That is a pattern, not an accident, and it is the
> first retro action.

---

## 7. DoD status

- [x] Sprint tasks complete or explicitly carried — **12 of 13**; §4 lists what was not in scope
- [x] 7-dimension ≥ 90 — **91.4**
- [x] Backend build + tests PASS — 999 run, 1 **pre-existing** failure (§5), 0 attributable to S2a
- [x] Frontend typecheck PASS; suite **218/218** green (was 197)
- [x] **A delete removes the number from the ledger, and a delete that matches nothing returns 409**
- [x] Deleting 3 numbers writes 3 history rows, each holding one number
- [x] A forced history-write failure rolls back both register and delete
- [x] Every rule the registration screen states is enforced server-side
- [x] 사유 refused when empty, on register as well as delete
- [x] A selection made on page 1 is enumerated and deleted while the operator is on page 3
- [x] No control on screen that cannot act; the S1 placeholders are gone
- [ ] `code-reviewer` verdict — **not run** (Skill 5)
- [ ] `security-auditor` verdict — **not run** (Skill 5)
- [x] Sprint retro written — [SPRINT-S2a-RETRO.md](SPRINT-S2a-RETRO.md)
- [ ] **PM sprint-gate approval** — pending
- [ ] G1 remains **PENDING**; implemented against approved-in-draft plans, following the I1/I2a precedent

## 8. Next

1. **PM**: acknowledge **SS2a-03** (ciphertext in `AOA_ADMIN`'s 등록ID for new rows) and decide
   **SS2a-02** (masked email where a masked name used to be). Both are consequences of rulings, not
   defects, and both are one line to reverse.
2. **G1** — the DDL cannot be applied without it, and the write path is not real until it is.
3. **S2a-01** — duplicate reconciliation. Raise with the operator team now; `CREATE UNIQUE INDEX`
   fails outright if any cross-institution duplicate remains.
4. **Spike S1-01** — one SQL statement, and V3 will not proceed without its answer.
5. **Measure the write path** (NFR-PERF-D02/D03). Two sprints have now declined to; the third should
   not be allowed to.
6. **RISK-S04** — the deletion reconciliation is now more urgent, not less: working deletion turns
   the old silent no-ops into confusing 409s.
7. Sprint S2b: 상세 / 설명수정 (D-S8, D-S10), informed by SS2a-04 — the screen is live in `AOA_ADMIN`
   and can be observed rather than inferred.
