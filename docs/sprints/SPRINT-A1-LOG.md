# Sprint A1 Log — 알림톡 contract, validation and the accept path

> **Version**: 1.0
> **Date**: 2026-08-18
> **Sprint**: A1 (weeks 1–2) · **Slice**: 카카오 알림톡 (screens 61, 50)
> **Status**: **PARTIAL — 11 of 19 tasks complete, 1 partial, 5 blocked externally, 2 carried.** Sprint **not** closed; DoD not met. Three iterations run (§10, §12)
> **Plan**: [DEV-PLAN-ALIMTALK.md](../design/DEV-PLAN-ALIMTALK.md) · **Tasks**: [sprint-A1-tasks.md](../design/sprint-A1-tasks.md)

---

## 1. Headline: the three Critical defects are closed, and the mechanism that closes them found a fourth in our own code

`ContractConformanceTest` reads the two IMO contract XMLs and asserts the serialised payload against them **in both directions**. It closes D-A1, D-A2, D-A3 and D-A7 — every Critical contract defect in the slice — with no database, no vendor and no container.

Then it did something the plan did not anticipate. **Within minutes of being written it failed on our own DTO.** `AlimTalkButton.isComplete()` is a public no-arg method on a record, so Jackson serialised it as a `complete` property — a field the contract does not declare. That is **exactly the D-A2 defect class**, reproduced by us, in new code, on day one. The same fault was latent in `FailbackData.isValid()`.

```
Expecting actual:
  ["complete", "name", "type", "url_pc", "url_mobile", "scheme_android", "scheme_ios"]
to contain exactly in any order:
  ["name", "type", "url_pc", "url_mobile", "scheme_android", "scheme_ios"]
but the following elements were unexpected: ["complete"]
```

Fixed with `@JsonIgnore` on both. The point worth recording is not the fix but what it demonstrates: the legacy carried four contract defects for over a year with code review as its only control, and review did not catch them. This control caught an equivalent defect before the code was ten minutes old. **The argument for ADR-ATK-021 was theoretical when it was written; it is now evidenced.**

## 2. Completed

| Task | Delivered | Tests |
|------|-----------|-------|
| A1-05 | `AlimTalkRequest`, `AlimTalkBatchRequest.MsgDataItem`, `AlimTalkButton`, `FailbackData` — contract-declared fields only, `failback_data` correctly named, `order` present | via A1-06 |
| A1-06 | `ContractConformanceTest` — bidirectional, driven by the checked-in IMO XML, version/hash pinned | 15 |
| A1-07 | `AlimTalkLimits` — 17 contract lengths read from the XML by the test, 6 business limits tagged `[ASSUMED-KAKAO-SPEC]`, effective bound `min(contract, business)` | 3 |
| A1-09 | `TemplateMatcher` — template compiles to a pattern, literals `Pattern.quote`d, variables lazy | 26 |
| A1-10 | `TemplateMatchResult` + incremental divergence location, values read per variable | included |
| A1-13 | `RecipientParser` — anchored pattern, 5 delimiter classes, normalisation before de-duplication | 17 |
| A1-14 | `TranIdGenerator` — env + `yyMMdd` + base-36 sequence, `SequenceSource` abstracted for A2-02 | 12 |
| — | `AlimTalkController` (`api`) — `POST /validate`, `POST /recipients/preview`, `@PreAuthorize`, no `sender_key` field on any inbound DTO | pending |
| — | `ProfileKey`, `RecipientNumber` — redacting wrappers, pulled forward from A2-01 | 11 |

**84 tests, 84 passing.** Every one of the 12 new source files carries `// source:` and `// req:`.

### 2.1 A second defect found in our own code — by the security control this time

The plan said the `gitleaks` positive fixture should be "the known legacy literal, proving the rule fires". Implementing that put the **real compromised profile key into the new repository** — in a test file, which is still a commit. Writing a secret-scanning fixture by committing the actual secret defeats the scan it is meant to prove.

Replaced with a synthetic key of the same shape, and elided the literal in the three documents that quoted it in full. ADR-ATK-024 and RISK-A03 amended to say so. **The plan's own wording was the defect** — this is worth flagging to whoever writes the next slice's credential ADR.

### 2.2 Decisions taken to keep moving without the spikes

All four spikes are blocked on third parties (§3). Rather than stall, each was replaced with a decision that is safe under either answer:

| Blocked on | Decision taken | Safe because |
|-----------|----------------|--------------|
| A1-01 vendor field spec | DTOs carry **only** contract-declared fields; no image or item-list fields | This is the AMB-A05 fallback shape. If the spec arrives, fields are *added* — the conformance test will demand it |
| A1-02 `RSMS` capture | `RsmsEnvelope` **not written** | Writing an unverified marshaller and calling it done is how RISK-A02 becomes invisible. Absent is honest; wrong is not |
| A1-03 vendor idempotency | `TranIdGenerator` built; retry policy untouched | Generation does not depend on vendor behaviour. ADR-ATK-025 already assumes the worse answer |
| A1-04 key model | `SenderProfileKeyResolver` **not written**; `ProfileKey` wrapper built | The wrapper is needed under either key model; the resolver's shape is not |

## 3. Not completed

### 3.1 Blocked externally — five tasks

| Task | Blocked on | Owner |
|------|-----------|-------|
| A1-01 | Vendor AlimTalk field specification (image / item-list) | architect + PM + vendor |
| A1-02 | A captured `RSMS` payload, or a staging endpoint | adapter-builder + Ops |
| A1-03 | Vendor idempotency semantics for a repeated `tran_id` | architect + vendor |
| A1-04 | Whether one profile key serves all institutions | Ops + vendor |
| A1-08 | A1-02 | — |

**No spike could be run from this environment.** Every one needs either the vendor or a staging endpoint whose existence is itself unresolved (RISK-A08). This was foreseen — four spikes was called out in DEV-PLAN §1.2 as "the honest cost of a first outbound integration" — but the plan assumed day-one *requests* would return within the sprint. None has.

### 3.2 Carried — five tasks, all needing infrastructure this sprint did not have

| Task | Why not done |
|------|--------------|
| A1-11 `TemplateMapper` + cache | Needs MyBatis wiring and a `KKB_MSG_TMPL` read; no reachable PostgreSQL (RISK-A12) |
| A1-12 Template selection API | Depends on A1-11 |
| A1-15 Dedupe pre-check | Needs the history read and the status column from A2-02 |
| A1-17 `AlimTalkSendService` accept path | Needs A1-11, A1-15 and `SenderNumberService` integration |
| A1-18 React compose screen | Frontend scaffold exists but no component work was undertaken |

### 3.3 Partial — two tasks

- **A1-16 authorization** — `@PreAuthorize("hasRole('OPERATOR')")` is on the controller and the inbound DTO structurally cannot carry `sender_key` (T-A24 closed by type). **Tenant scoping via `TenantContext` is not wired**, so FR-AZ-A02 is unmet.
- **A1-19 negative-path security tests** — regex injection (T-A7), backtracking (T-A20), credential and PII redaction (T-A14) are covered by unit tests. **Endpoint authorization tests are not written** — they need a Spring test context.

## 4. Environment constraints met

**Maven is not installed in this environment.** CI uses `mvn -B -ntp verify`, but no local `mvn` and no wrapper exists. Compilation and test execution were performed with `javac` and a hand-assembled classpath from `~/.m2`, pinned to the versions Spring Boot 3.3.4 resolves (Jackson 2.17.2, JUnit 5.10.2, AssertJ 3.24.2) after `sort -V` initially selected unrelated Jackson 3.x and JUnit 6 jars from the cache. **The 84 results are real executions, not inspection** — but they are not a CI run, and coverage instrumentation (JaCoCo) did not run, so the ≥80 % line / ≥70 % branch targets are **unmeasured** rather than met.

**Docker remains prohibited** (RISK-A12). It did not bite this sprint: every delivered class is pure logic, and the contract test needs only a checked-in XML. Confirmed in passing that RISK-A12's mitigation 3 is viable here — this slice needs no proprietary `ENCRYPT`/`decrypt`, so `embedded-postgres` would test real semantics when A1-11 arrives.

## 5. Design corrections arising from implementation

**ADR-ATK-026's `tran_id` arithmetic was wrong.** It allotted 4 characters to the sequence: 1 + 6 + 4 = **11**, against a contract field of 10. The ADR's own diagram was eleven characters wide. `TranIdGeneratorTest.exactlyTenCharacters` failed on first run. Sequence reduced to 3 characters; ceiling drops from 1,679,616 to **46,656** per institution per day — a 36× reduction. RISK-A04's assessment is unchanged (수용, M/L) because a `tran_id` is consumed **per send request, not per recipient**, but the margin is smaller than claimed. ADR-ATK-026 amended in place with the correction visible rather than silently edited; RISK-A04 likewise.

**Two of my own test lists contradicted each other.** `01112345678` and `0101234567` appeared in both the accept and reject parameter sets — 3 failures that were test defects, not code defects. Resolved by keeping the legacy's 10-or-11-digit acceptance (`\d{3,4}` middle) rather than narrowing it, since narrowing would stop numbers the legacy could send to.

## 6. Defects closed at code level — 13 of 35

| Closed | Defect |
|--------|--------|
| D-A1 | `failback` → **`failback_data`** |
| D-A2 | Five undeclared fields absent, asserted in reverse direction |
| D-A3 | `order` on every `msg_data` item |
| D-A6 | Template matcher corrected — `#{name}님 안녕` / `김님철수님 안녕` now **passes** |
| D-A7 | 17 contract lengths in one class, read from the XML by the test |
| D-A10 | One canonical `receiver_number` type |
| D-A12 | Recipients validated, de-duplicated, counted |
| D-A14 | Per-item `reqdate` present on batch |
| D-A17 | Fallback validated per type |
| D-A25 | `tran_id` collision-free; 500 concurrent issues, zero duplicates |
| D-A28 | Anchored pattern — `abc01012345678` rejected |
| D-A30 | Credential and PII redacted **by type**, verified on JSON and `toString()` |
| D-A35 | Five delimiter classes, normalisation before de-duplication |

Partial: D-A9 (`isComplete()` exists; UI reporting is A1-18), D-A13 (no message-form field until AMB-A05).

## 7. 7-dimension self-assessment

| Dimension | Weight | Score | Basis |
|-----------|-------:|------:|-------|
| 완성도 Completeness | 20 % | **55** | 7 of 19 complete, 2 partial. 5 blocked externally and 5 carried — the largest incompletion of any sprint in this programme |
| 추적성 Traceability | 15 % | **95** | All 12 files carry `// source:` + `// req:`; 32 trace rows added; two design corrections recorded as ADR amendments rather than silent edits |
| 보안 Security | 20 % | **85** | Credential and PII redaction structural and tested; regex-injection surface closed by construction; the real key removed from repo and docs. Held below 90 by unwired tenant scoping (FR-AZ-A02) and unwritten endpoint authorization tests |
| 성능 Performance | 10 % | **70** | NFR-PERF-A01 asserted (1000 matches < 300 ms) and backtracking bounded. Nothing else measurable without the send path |
| 가독성 Readability | 15 % | **92** | Bilingual Javadoc throughout, tables and diagrams matching sibling-slice convention, defect IDs cited at every non-obvious decision |
| 표준 준수 Standards | 10 % | **90** | Package layout follows the established split; no existing class modified; ADR amended for every design change. Conventional Commits not exercised — nothing committed |
| 테스트 커버리지 Coverage | 10 % | **60** | 84 tests passing on delivered code, but **JaCoCo did not run** so the ≥80 %/≥70 % targets are unmeasured, and 10 of 19 tasks have no code to cover |

**Weighted total: 55×0.20 + 95×0.15 + 85×0.20 + 70×0.10 + 92×0.15 + 90×0.10 + 60×0.10 = 76.35 / 100**

**Below the 90 threshold.** Per harness §6 the lowest dimension drives remediation — that is 완성도 at 55, and it cannot be remediated by this team: 5 of the 10 outstanding tasks are blocked on the vendor and 5 need a database. Re-running the loop would not move it. **Escalated rather than iterated.**

## 8. Next

**Sprint A1 does not close.** Recommended disposition:

1. **PM/Ops action, highest priority** — the four spike requests to the vendor, and RISK-A03 key rotation. Nothing in the slice's remaining scope can be verified against a real vendor until these move, and A1-01 gates FR-ATC-003/008 by build failure.
2. **Establish PostgreSQL reachability** (inherited RISK-S13 open question). This unblocks A1-11, A1-12, A1-15, A1-17 and is the single largest gate on the carried work.
3. **G1 approval** before A2-02, unchanged.
4. Continue A1 with the DB-dependent tasks once (2) is answered; do not start A2's despatch path before A2-01, because sending before credential management means sending with the leaked key (T-A1).

## 9. Carried to the programme

- **The plan's `gitleaks` fixture wording is unsafe** and should be corrected in the harness guidance, not just in this slice's ADR: "use the real literal as a positive fixture" instructs the reader to commit a live secret.
- **ADR arithmetic should be checked by a test before the ADR is approved.** ADR-ATK-026 passed G2 review with an 11-character value in a 10-character field, and the error was visible in its own diagram. Three prior slices' ADRs were never executed against a test at design time either.

---

## 10. Iteration 2 — what the first pass gave up on too early

Two things §3 called blocked were not. Both had precedents in this repository that the first iteration did not look for.

**Mapper SQL is testable without a database.** `InstitutionMapperSqlTest` already establishes the pattern: read the `@Select` annotation by reflection and assert the identifiers. That is not equivalent to an integration test — it cannot prove the table exists — but it does prevent the exact defect this programme already had (RISK-I05, where a mapper was written against a guessed table). A1-11 and A1-12 were declared "blocked on PostgreSQL" when they were only blocked on *some* of their verification.

**Coverage was measurable all along.** The JaCoCo agent and `org.jacoco.core` / `org.jacoco.report` were in `~/.m2`. `asm-analysis` is absent and I am offline, but it turned out not to be required. A 40-line report generator against the core API produced the numbers the first iteration reported as "unmeasured".

### 10.1 Delivered in iteration 2

| Task | Delivered | Tests |
|------|-----------|-------|
| A1-11 | `TemplateMapper` (`@Select`, read-only), `TemplateSummary`, `TemplateRegistry` with body-hash-keyed compiled-matcher cache | 19 |
| A1-12 | `GET /templates`, `POST /templates/validate` on `AlimTalkController`, tenant-scoped through the registry | via above |
| A1-16 → | **Tenant scoping now wired.** `TemplateRegistry.scopeOf()` derives the institution from `TenantContext.require()`; the request parameter is a *request*, not an authority (FR-AZ-A02) | 2 |
| A1-18 | React `AlimTalkPage` + `alimTalkApi.ts`, routed at `/alimtalk` behind `RequireOperator` | 9 |
| — | `PayloadValidationTest` — the validation logic coverage measurement exposed as untested | 32 |

**Java: 135 tests passing** (was 84). **Frontend: 127 tests passing across 11 files** (9 new), `tsc --noEmit` clean.

### 10.2 Coverage — measured, and both targets met

| | First measurement | After closing the gaps | Target |
|---|---|---|---|
| Line | 77.3 % (197/255) | **85.5 %** (218/255) | ≥ 80 % |
| Branch | 58.7 % (91/155) | **94.8 %** (147/155) | ≥ 70 % |

The first measurement is the more useful number. It showed the shortfall concentrated in four places — `AlimTalkButton.isComplete()`, `FailbackData.isValid()`, `AlimTalkLimits.within()`, `ProfileKey.of()` — **all validation logic written in iteration 1 and never executed.** The conformance test guarded field *names*; nothing checked field *rules*.

That gap was invisible in the test count. Eighty-four passing tests read as "verified" and were not. **This is the concrete argument for measuring rather than counting**, and it is why A1-R5 was the right action even though it looked like tooling housekeeping.

### 10.3 A third defect found in our own code — this one in the vendor contract

Writing `PayloadValidationTest` produced an assertion that fallback type codes match the contract's declared length. It failed:

```
Expecting actual: "SMS"  to have size: 2
```

The code is right and **the contract is wrong**. `failback_data`'s `type` item is a verbatim copy of the `button` sub-rule's — identical `length="2"` *and* identical `name="버튼타입"` ("button type"). But fallback types are `SMS`/`LMS`/`MMS`, all three characters. Recorded as **D-A36** (High) in the specification.

Two consequences worth stating plainly:

- **It is the one exception to CONST-DATA-A02.** That constraint means "the contract wins where it is narrower than the business limit". Here the contract is narrower than *its own declared values*, so obeying it would reject every valid fallback. `CONTRACT_FAILBACK_TYPE_DECLARED = 2` is retained beside `CONTRACT_FAILBACK_TYPE = 3` and a test pins the discrepancy, so a corrected contract fails the build and tells us.
- **It may be a live production defect, independent of D-A1.** Screen 50 puts three characters into that field. If the `jex` IMO layer applies fixed-width handling to the declared length — which its `padding=" "` attribute suggests — the value has been transmitted as `"SM"`. D-A1 established one way the fallback silently disappears; this is a second. Whether either ever worked is now a question for the `RSMS` capture (spike A1-02, RISK-A02), not for reading source.

### 10.4 Two more test defects of my own

- The `TemplateRegistry` scoping test asserted `TenantScopeUnavailableException`; `TenantContext.require()` throws `IllegalStateException`. The behaviour was right — it fails closed rather than widening to an unscoped query — and the assertion was rewritten to check the *direction* rather than the type.
- The frontend stub resolved handlers by first prefix match, so `/alimtalk/templates/validate` was served by the `/alimtalk/templates` handler and returned an array where the test expected a verdict. Fixed by preferring the longest matching key. Worth noting because it fails *silently* with plausible-looking data.

### 10.5 Still not done

| Task | State | Blocked on |
|------|-------|-----------|
| A1-01…A1-04, A1-08 | **Blocked externally** — unchanged | Vendor / Ops. No spike is runnable from here |
| A1-15 dedupe pre-check | **Carried** | The `KKB_ADMIN_SEND_HIS` status column, which is A2-02 DDL — genuinely cannot precede it |
| A1-17 accept-path service | **Carried** | A1-15 |
| A1-19 endpoint authorization tests | **Partial** | Needs a Spring `@WebMvcTest` context; unit-level threat coverage (T-A7, T-A14, T-A20, T-A24) is in place |

A1-15 and A1-17 are the honest remainder: the dedupe check must return the *original outcome* of a prior send, and there is no column to read it from until the DDL exists. Building it against a column that does not exist would be the `RsmsEnvelope` mistake in a different place.

## 11. 7-dimension self-assessment — iteration 2

| Dimension | Weight | Iter 1 | **Iter 2** | Basis for the change |
|-----------|-------:|-------:|-----------:|----------------------|
| 완성도 Completeness | 20 % | 55 | **72** | 10 of 19 complete (was 7), 2 partial, 2 carried (was 5). The 5 externally blocked are unchanged and cap this dimension |
| 추적성 Traceability | 15 % | 95 | **95** | 57 A1 trace rows (was 32); D-A36 recorded in the spec; two ADR amendments stand |
| 보안 Security | 20 % | 85 | **88** | Tenant scoping now wired and tested (FR-AZ-A02); no-`sender_key` asserted at the UI layer too; PII masking tested on both sides. Still short of 90 without endpoint authorization tests |
| 성능 Performance | 10 % | 70 | **75** | Matcher-cache behaviour now tested; NFR-PERF-A01 asserted. Nothing else measurable without the send path |
| 가독성 Readability | 15 % | 92 | **92** | Unchanged conventions, bilingual Javadoc, defect IDs cited |
| 표준 준수 Standards | 10 % | 90 | **92** | Mapper follows the established `@Select` + SQL-test precedent; read-only enforced by test; D-A36 raised rather than absorbed |
| 테스트 커버리지 Coverage | 10 % | 60 | **92** | **Measured**: line 85.5 % ≥ 80, branch 94.8 % ≥ 70; 135 Java + 127 frontend tests |

**Weighted total: 72×0.20 + 95×0.15 + 88×0.20 + 75×0.10 + 92×0.15 + 92×0.10 + 92×0.10 = 85.95 / 100** (was 76.35)

**Still below the 90 threshold.** The lowest dimension remains 완성도 at 72, and it is still capped by the five externally blocked tasks — no amount of further iteration moves it. Escalated again rather than looped a third time.

## 12. Next — unchanged in substance

1. **Vendor requests and key rotation** (RISK-A03) remain the critical path. A1-01 gates FR-ATC-003/008 by build failure; A1-02 now also answers D-A36's truncation question.
2. **PostgreSQL reachability** is narrower than iteration 1 assumed — it no longer blocks A1-11/A1-12, but tier-3 verification of `SKIP LOCKED` and sequence concurrency still needs it, and `embedded-postgres` is viable for this slice.
3. **G1** before A2-02, unchanged.
4. **A1-15/A1-17 belong with A2-02**, not before it.

---

## 12. Iteration 3 — the authorization layer that was not there

Closing A1-16/A1-19 meant asking a question nobody had asked: *is `@PreAuthorize` actually enforced?*

It was not. **`@EnableMethodSecurity` appears nowhere in the committed source**, and method security is off by default in Spring Boot 3. Verified against `HEAD` rather than the working tree, because the IDE had already rebuilt `target/classes` from my edit and `javap` was reading my own change back at me:

```
$ git grep -l "EnableMethodSecurity" HEAD -- src/main/java
none at HEAD

$ git grep -l "PreAuthorize" HEAD -- src/main/java
auth/api/OtpAdminController.java
auth/api/PasswordAdminController.java
biztalk/api/InstitutionAdminController.java
biztalk/api/InstitutionController.java
biztalk/api/SenderNumberController.java
```

**Five controllers from the three previous slices carried an annotation that did nothing.** Recorded as **D-A37** (High).

It is not an open door — all five sit under `SecurityConfig`'s `/api/admin/**` → `hasRole("OPERATOR")` rule, which is real and does protect them. But every design document in this programme calls the controller-level check "defence in depth", and there was **one** layer, not two. Any future endpoint placed outside `/api/admin/**` while relying on the annotation would have been unprotected.

**This is the D-S2 defect class in our own code.** The 발신번호 slice's headline finding was a browser-side `alert('권한 없음')` that guarded nothing while the server refused nobody. These annotations were the same shape: visible, documented, inert.

Fixed with one line, and the fix is safe: every annotated controller already sits behind the identical role check, so no caller permitted today can be newly rejected. **Raised to the programme** — the other three slices' owners should confirm their expressions now that they execute.

### 12.1 Delivered in iteration 3

| Task | Delivered | Tests |
|------|-----------|-------|
| A1-16 → **complete** | `@EnableMethodSecurity` in `SecurityConfig`; `@PreAuthorize` now actually enforced; tenant scoping already wired in iteration 2 | 8 |
| A1-19 → **substantially complete** | `AlimTalkControllerSecurityTest` (reflection: method security on, every endpoint covered, no credential field on any request/response/parameter) + `AlimTalkControllerMvcTest` (standalone MockMvc over every endpoint) | 11 |

**154 Java tests passing** (was 135). Frontend unchanged at 127.

### 12.2 Coverage — the controller was at zero

| | Iter 2 | **Iter 3** | Target |
|---|---|---|---|
| Line | 85.5 % | **99.2 %** (253/255) | ≥ 80 % |
| Branch | 94.8 % | **96.1 %** (149/155) | ≥ 70 % |
| `AlimTalkController` | **0.0 %** | **100 %** | — |

Iteration 2's 85.5 % **included a controller at 0 % line coverage**. The headline number met the target while the class handling every request had never been executed by a test. Worth stating: an aggregate that passes can still conceal a component that is entirely unverified — which is the same lesson as §10.2, one level up.

### 12.3 What standalone MockMvc does **not** prove

`MockMvcBuilders.standaloneSetup` raises one controller with no Boot auto-configuration and **no security proxy**, so `@PreAuthorize` does not apply within it. These 11 tests prove request/response wiring, JSON shape, masking and that a client-supplied `senderKey` is discarded. They do **not** prove an anonymous call receives 403.

That assertion still needs a full `@WebMvcTest` context. Assembling even the standalone classpath by hand took six additional jars discovered one `NoClassDefFoundError` at a time — `spring-jcl`, `spring-aop`, `micrometer-observation`, `micrometer-commons`, `slf4j-api`, plus hamcrest and json-path. **That spiral is the concrete argument for A1-R5** (get a real build): it is not tooling preference, it is the difference between reaching an integration test and not.

### 12.4 Remaining

| Task | State | Blocked on |
|------|-------|-----------|
| A1-01…A1-04, A1-08 | **Blocked externally** — unchanged | Vendor / Ops |
| A1-15, A1-17 | **Carried** | The `KKB_ADMIN_SEND_HIS` status column (A2-02 DDL). Genuinely cannot precede it |
| A1-19 | **Partial** | The 403 assertion needs a Boot test context |

## 13. 7-dimension self-assessment — iteration 3

| Dimension | Weight | Iter 1 | Iter 2 | **Iter 3** | Basis |
|-----------|-------:|-------:|-------:|-----------:|-------|
| 완성도 Completeness | 20 % | 55 | 72 | **75** | 11 of 19 complete, 1 partial, 2 carried. The 5 externally blocked still cap this |
| 추적성 Traceability | 15 % | 95 | 95 | **95** | 69 A1 trace rows; D-A36 and D-A37 both recorded in the spec |
| 보안 Security | 20 % | 85 | 88 | **93** | D-A37 found and fixed — method security now actually enforced programme-wide; credential-absence asserted at three layers; tenant scoping fails closed. Short of full marks only for the 403 integration assertion |
| 성능 Performance | 10 % | 70 | 75 | **78** | Controller paths now exercised; matcher cache tested |
| 가독성 Readability | 15 % | 92 | 92 | **92** | Unchanged conventions |
| 표준 준수 Standards | 10 % | 90 | 92 | **93** | Cross-slice defect raised to the programme rather than absorbed |
| 테스트 커버리지 Coverage | 10 % | 60 | 92 | **97** | line 99.2 %, branch 96.1 %; 154 Java + 127 frontend |

**Weighted total: 75×0.20 + 95×0.15 + 93×0.20 + 78×0.10 + 92×0.15 + 93×0.10 + 97×0.10 = 88.45 / 100** (was 85.95, 76.35)

**Still below 90.** 완성도 at 75 remains the binding constraint and remains entirely outside this team's control — five tasks await the vendor, two await DDL that requires G1. A fourth iteration cannot move it. **Escalated, not looped.**

---

# Sprint A2 — 시작 / begins

## 14. A2-01 자격증명 관리 — 완료 / credential management, complete

Sprint A1 화면에 남아 있던 문구 — *"발송은 다음 스프린트에 제공됩니다. 자격증명 관리(A2-01)
이전에는 발송을 배선하지 않습니다"* — 가 가리키던 작업이다.

### 14.1 무엇을 만들었나 / what was built

`SenderProfileKeyResolver` (ADR-ATK-024). 세 가지 성질을 갖는다.

| 성질 / property | 레거시 / legacy | 여기 / here |
|---|---|---|
| 위치 | JSP 소스에 하드코딩, 두 곳 (D-A24) | 환경 주입 `IRIS_ATK_PROFILE_KEY_*`, 체크인 기본값 없음 |
| 알 수 없는 기관 | 하나의 키로 전부 발송 | **예외** — 조용한 대체 없음 (T-A2) |
| 유출된 키 | 매 발송마다 로그에 기록 (D-A30) | **SHA-256 대조로 거부** |

**유출된 키를 해시로 거부한다.** 회전(rotation)은 벤더 측 운영 작업이라 코드로 닫을 수 없지만,
회전한 뒤 <b>옛 키를 다시 설정하는 것</b>은 닫을 수 있다. 평문 상수를 두면 유출 방지 클래스가
스스로 D-A24 를 재현하므로 해시만 커밋한다.

### 14.2 화면이 고정 문구를 버렸다 / the screen dropped its fixed note

문제의 문구 자체가 레거시 화면 61 과 같은 침묵이었다 — 화면 61 은 "JSON 생성" 이 무엇을
했는지 알려주지 않았고, "다음 스프린트에 제공됩니다" 는 상태가 바뀌어도 그대로 남아 거짓이
된다. `GET /send-readiness` 가 실제 상태를 돌려주고 화면은 그것을 표시한다:

- `credentialConfigured` — 이 기관의 키가 설정되어 있는지
- `dispatchWired` — 발송 경로가 배선되었는지 (지금은 `false`)
- `blockers` — 남은 항목을 이름으로

응답에는 키가 **어떤 형태로도** 없다. 마스킹된 형태조차 없다 — 담을 이유가 없기 때문이다
(FR-AZ-A05).

### 14.3 결과 / results

| | A1 종료 | **A2-01 후** |
|---|---:|---:|
| 백엔드 테스트 | 160 | **177** |
| 프론트엔드 테스트 | 129 | **132** |
| 타입체크 | clean | clean |

### 14.4 발송은 아직 배선하지 않았다 / sending is still not wired

A2-01 이 끝났다고 보낼 수 있는 것은 아니다. 남은 것은 **우리 손 밖에 있다**:

| 항목 | 막고 있는 것 |
|------|-------------|
| A2-02 outbox DDL | **G1 결재** — 승인 전에는 스키마를 만들지 않는다 (RISK-A13) |
| A2-05 벤더 클라이언트 | `RSMS` 봉투 형식 미확인 (spike A1-02) — 검증되지 않은 마샬러를 쓰고 "됐다" 하는 것이 RISK-A02 를 보이지 않게 만드는 방법이다 |
| A2-06 재시도 정책 | 벤더 멱등성 미확인 (spike A1-03) |
| **RISK-A03 키 회전** | 벤더 측 운영 작업. **G3 는 이것 없이 통과할 수 없다** |

`iris.alimtalk.environment=A`(운영)로 기동하면 여전히 **기동에 실패한다** — 메모리 순번이
D-A25 를 재현하기 때문이며, A2-02 전에는 그것이 옳은 동작이다.

---

## 15. 반복 4 — 통제가 다시 우리 코드에서 결함을 찾았다 / iteration 4: the controls found our own defects again

> **Date**: 2026-08-19 · **Lead**: `team-leader`
> **입력 / trigger**: `/04-implement` (인자 없음) — 반복 3이 88.45 로 에스컬레이션된 상태에서 재진입

반복 3은 완성도가 외부 의존으로 묶여 있어 "네 번째 반복은 그것을 움직일 수 없다"고 적고 종료했다.
맞는 판단이었지만 **결론이 하나 빠져 있었다**: 완성도를 못 움직인다고 해서 다른 여섯 차원이
움직일 수 없는 것은 아니다. 이번 반복은 그 여섯을 겨냥했고, 그 과정에서 결함 세 건이 나왔다.

Iteration 3 closed by noting that completeness was pinned by external dependencies and "a fourth iteration
cannot move it". True — but it **missed a corollary**: being unable to move completeness does not mean the
other six dimensions are stuck. This iteration targeted those six, and three defects fell out on the way.

### 15.1 발견 — 전부 사람의 검토가 아니라 통제가 찾았다 / found by controls, not by reading

| ID | 심각도 | 무엇 | 어떻게 발견되었나 |
|----|--------|------|------------------|
| **CR-A01** | MEDIUM | `TemplateRegistry` 캐시가 `String.hashCode()` 키를 써서, 충돌하는 본문으로 바뀌면 **바뀌기 전 규칙으로 검증**. 게다가 무한 증가 | 코드 리뷰 — 클래스 Javadoc 이 스스로 한 약속과 그 약속을 지키는 기제를 대조 |
| **CR-A03** | MEDIUM | 생성 payload 는 마스킹되어 발송 불가인데 화면이 그 사실을 말하지 않았다 | 리뷰 — 응답 타입을 따라가다 발견 |
| **QA-A01** | **MEDIUM** | `/compose/batch` 백엔드 테스트가 **하나도 없었다**. 화면 테스트만 붙였고 화면 테스트는 서버를 대역으로 세운다 | **커버리지 재측정** |

**QA-A01 이 이번 반복에서 가장 중요한 항목이다.** `/compose/batch` 는 사용자가 지적해서 만든
엔드포인트인데, 만들 때 화면 테스트만 붙였다. 화면 테스트는 `stubFetch` 로 서버를 대역으로 세우므로
**서버 코드는 한 번도 실행되지 않은 채** "테스트됨" 으로 보였다. 커버리지를 다시 재고 나서야
드러났다:

| | 재측정 직후 | 보완 후 |
|---|---:|---:|
| `AlimTalkBatchComposeRequest` | **0.0 %** | 100.0 % |
| `AlimTalkController` line | **55.7 %** | **95.2 %** |
| `AlimTalkController` branch | **39.5 %** | **78.9 %** |
| 슬라이스 합계 line | 82.3 % | **97.0 %** |
| 슬라이스 합계 branch | 79.2 % | **90.3 %** |

> **반복 3과 똑같은 착시였다.** 그때는 합계 85.5 % 가 컨트롤러의 0 % 를 가렸고, 이번에는 "화면
> 테스트가 통과한다" 가 서버의 0 % 를 가렸다. 교훈은 커버리지를 **한 번 재는 것**으로 끝나지
> 않는다는 것이다 — 코드를 더할 때마다 다시 재야 한다. A1-R16 으로 남긴다.
>
> **The same illusion as iteration 3.** Then an 85.5 % aggregate hid a controller at 0 %; this time "the
> screen tests pass" hid the server at 0 %. The lesson is that measuring coverage is not a one-off — it has
> to be re-measured whenever code is added. Recorded as A1-R16.

또한 반복 3이 보고한 **99.2 % / 96.1 %** 는 배치 엔드포인트가 생기기 <b>전</b>의 좁은 범위에서 잰
값이다. 지금의 정직한 수치는 더 넓은 표면에 대한 **97.0 % / 90.3 %** 다.

### 15.2 CR-A01 — 캐시가 지키기로 한 것을 지키지 못했다

`TemplateRegistry` 의 Javadoc 은 *"본문이 바뀌면 캐시가 낡은 규칙으로 검증하게 되고, 그것은 검증이
없는 것보다 나쁘다"* 고 적고, 그 위험을 캐시 키의 `body.hashCode()` 로 막는다고 했다. 그런데
`String.hashCode()` 는 32비트 비암호학적 해시이고 충돌 문자열은 쉽게 만들어진다 — `"Aa"` 와 `"BB"`
가 같은 값이며, 같은 접미사를 붙여도 계속 같다.

같은 파일이 *"이 저장소의 어떤 코드도 `KKB_MSG_TMPL` 에 쓰지 않는다(AMB-A07)"* 고 적고 있다.
**누가 언제 바꾸는지 모르는 표를 권위로 삼아 놓고, 변경 감지를 해시의 근사에 맡긴 것이다.**

**수정**: 항목이 컴파일에 쓴 **본문 자체**를 들고 조회마다 정확히 대조한다. 템플릿 하나에 항목
하나가 되어 무한 증가도 함께 사라졌다.

**증명**: 새 테스트 두 건을 임시 사본의 옛 구현에 돌려 **실패하는 것을 확인**한 뒤에 FIXED 로
적었다. 옛 코드에서도 통과하는 테스트는 아무것도 증명하지 않는다.

### 15.3 CR-A03 — 침묵을 물려받았다

생성된 payload 는 수신번호를 `010****2222` 로, 발신프로필키를 `ProfileKey[REDACTED]` 로 직렬화한다.
의도된 동작이고 백엔드 테스트가 고정하고 있다. 그런데 **화면이 그 사실을 말하지 않았다.** 운영자는
`JSON 생성` → `복사` 를 누르고 그 JSON 을 쓰려다 벤더에서 실패하며, 이유를 알 수 없다.

레거시 D-A20 과 같은 모양이다 — 레거시의 `JSON 생성` 도 무엇을 만들었는지 끝내 말하지 않았고, 그
침묵이 복사 실패가 몇 년간 눈에 띄지 않은 이유였다. **침묵을 물려받았던 것이다.**

### 15.4 레거시 대비 — 수신번호 반복 행을 되돌렸다 / restoring the repeatable rows

레거시 화면 61 은 `#receiverNumberContainer` 안에 `.receiver-number` 행을 반복하고 `수신번호 추가` /
행별 `삭제` 를 두었다(`biztalk_admin_61.js:451`). 우리는 textarea 하나로 단순화했었는데, **그 단순화를
정당화하는 결함이 없었다** — 내 판단이었을 뿐이다. 레거시 구조로 되돌렸다.

한 가지는 의도적으로 다르다: 레거시는 행이 **하나뿐일 때도** 삭제 버튼을 보였고, 누르면 입력란이
사라져 화면을 새로 고치는 것 말고는 되돌릴 방법이 없었다. 마지막 행에서는 감춘다.

붙여넣기는 계속 처리된다 — 행들을 쉼표로 이어 `RecipientParser` 에 넘기므로, 한 행에 여러 번호를
붙여넣어도 나뉜다. 레거시는 그 입력을 번호 하나로 보고 형식 오류로 떨어뜨렸다.

### 15.5 CI 가 이 슬라이스의 결함을 하나도 모르고 있었다

`L2 static rules` job 은 스스로 *"일반적인 린트가 아니다. 레거시에서 실제로 발생한 결함을 직접
겨냥한다"* 고 적고 있는데, 규칙 6개가 전부 **login 슬라이스**의 결함이었다. 알림톡의 D-A24·D-A30 에
대응하는 규칙은 없었다. 3개를 추가했고, 각각을 **양방향으로 검증**했다 — 현재 코드에서 침묵하고,
합성 결함 파일에서 발화한다. 발화하지 않는 규칙은 규칙이 아니라 장식이다.

### 15.6 재현 가능해진 것 / what became reproducible

Maven 이 없어(RISK-A12) 세 차례 반복에서 매번 클래스패스를 손으로 다시 조립했다 — 같은 탐색을 세
번 했다. `qa/run-alimtalk-tests.sh` 로 고정했고, `--coverage` 로 JaCoCo 계측과 **클래스별** 표까지
낸다(`qa/CoverageReport.java`). 합계만 내면 이번에 찾은 종류의 구멍이 보이지 않는다.

### 15.7 결과 / results

| | A2-01 후 | **반복 4 후** |
|---|---:|---:|
| 백엔드 테스트 | 177 | **196** |
| 프론트엔드 테스트 | 132 | **142** |
| 커버리지 line / branch | 미재측정 | **97.0 % / 90.3 %** |
| 타입체크 | clean | clean |
| CI 정적 규칙 | 6 (전부 login) | **9** (알림톡 3 추가, 양방향 검증) |
| DoD — code-reviewer | 미실행 | ✅ **APPROVE** (조건부) |
| DoD — security-auditor | 미실행 | ⚠️ **조건부 승인** |

산출물: [reviews/code-review-alimtalk-A1.md](../../reviews/code-review-alimtalk-A1.md),
[security/audit-alimtalk.md](../../security/audit-alimtalk.md)

### 15.8 7 차원 자체 평가 — 반복 4

| 차원 | 가중 | Iter 2 | Iter 3 | **Iter 4** | 근거 |
|------|-----:|------:|------:|-----------:|------|
| 완성도 | 20 % | 72 | 75 | **78** | task 완료 수는 그대로다 — 5건은 벤더, 2건은 G1 대기. 다만 DoD 두 항목(code-reviewer·security-auditor)이 실제로 산출되었고 레거시 대비 미이행 항목(수신번호 행)이 닫혔다 |
| 추적성 | 15 % | 95 | 95 | **96** | 신규 10행 추가(총 146). CI 규칙까지 요구사항에 연결 |
| 보안 | 20 % | 88 | 93 | **96** | 이 슬라이스의 결함 부류를 CI 가 **집행**한다(양방향 검증). 보안 감사 산출. SEC-A03 수정. 403 통합 검증과 SBOM 미실행으로 만점 아님 |
| 성능 | 10 % | 75 | 78 | **82** | 캐시 무한 증가 제거. 부하 시험은 여전히 미실행 |
| 가독성 | 15 % | 92 | 92 | **92** | 규약 불변 |
| 표준 준수 | 10 % | 92 | 93 | **95** | DoD 산출물이 규정된 디렉터리에 존재. 테스트 실행이 스크립트로 고정 |
| 커버리지 | 10 % | 92 | 97 | **93** | **하향이 아니라 정정이다.** 97 은 배치 엔드포인트 이전의 좁은 범위였다. 실제로는 82.3 % 였고 보완 후 97.0 % / 90.3 %. 컨트롤러 branch 78.9 % 가 남은 약점 |

**가중 합계: 78×0.20 + 96×0.15 + 96×0.20 + 82×0.10 + 92×0.15 + 95×0.10 + 93×0.10 = 89.3 / 100**
(88.45 → **89.3**)

**여전히 90 미만이다.** 완성도 78 이 그대로 구속 조건이며, 그 위의 7건은 **벤더와 G1** 에 걸려 있다.
반복 5로 옮길 수 있는 코드 작업이 남아 있지 않다 — 남은 것은 결재와 외부 작업이다. **에스컬레이션을
유지한다.**

> §4.9 에 따라: 임계 90 은 `[근거:sg-gw회고·조정가능]` 이다. 임계를 낮추는 선택도 PM 에게 열려
> 있으나, 그 경우 조정 사실과 근거를 ADR 또는 회고에 남겨야 한다.

---

## 16. 반복 5 — PM 의 질의가 설계 결함을 찾았다 / iteration 5: a PM challenge found a design defect

> **Date**: 2026-08-19 · **Lead**: `team-leader`
> **계기**: PM 질의 — *"why need A2-02 outbox DDL? because I have database connect and existing table."*

이 반복은 코드가 아니라 **RISK-A06 의 대응 계획 2번**을 실행한 것이다. 그 계획은 이렇게 적혀
있었다: *"`KKB_ADMIN_SEND_HIS` 를 `SELECT *` 로 읽는 레거시 소비자가 없는지 확인한다. 이것은 가정이
아니라 구체적 점검이다."* 그 점검이 두 가지를 찾았다 — **하나는 우리 설계의 결함이었다.**

### 16.1 점검 결과 — PASS, 그러나 요점은 다른 데 있었다

`KKB_ADMIN_SEND_HIS` 의 소비자는 둘뿐이고 **둘 다 컬럼을 명시**한다(`_C001` INSERT, `_L001` SELECT).
`SELECT *` 0건. 컬럼 추가는 안전**했을** 것이다.

**그런데 그 컬럼이 애초에 필요 없었다.** `_L001` 의 SQL 이 그것을 드러낸다:

```sql
SEND_HIS AS A LEFT OUTER JOIN
(SELECT STATUS, SERIALNUM, ID, MSG, decrypt(PHONE) AS PHONE FROM KKO_MSG_LOG) AS B
  ON A.SERIALNUM = B.SERIALNUM
, count(CASE WHEN STATUS = '3' THEN 1 END) AS AT_SCS_CNT
```

`KKO_MSG_LOG` 에는 `STATUS`·`RSLT`·`MSG_RSLT`·`REQDATE`/`SENTDATE`/`REPORTDATE` 와 **암호화된**
`PHONE`·`CALLBACK` 이 있고, **메시지 한 건에 한 행**이다. ADR-ATK-023 이 상태 컬럼을 붙이려던
`KKB_ADMIN_SEND_HIS` 는 **한 행이 발송 행위 하나**다 — 거기 상태를 두면 메시지 수천 건에 상태 하나가
붙는다. 같은 ADR 이 §"Partial batch outcomes" 에서 요구한 단위를 **스스로 어길 뻔했다.**

### 16.2 아웃박스는 그래도 필요하다 — 다만 훨씬 좁게

전수 조사: `INSERT INTO KKO_MSG_LOG` **0건**, `UPDATE KKO_MSG_LOG` **0건**. IRIS_ADMIN 은 이 표를
읽기만 한다. 쓰는 것은 게이트웨이다. 그래서 그 표는 **접수 이후**만 말하고, 아웃박스가 덮는 구간은
그 **앞**이다 — 보내기로 결정한 시점부터 접수가 확정될 때까지. 그 사이에 프로세스가 죽으면 어느
표에도 흔적이 없다.

`STATUS`·`RSLT`·`REPORTDATE` 를 복제할 이유는 없다. **접수 확정까지만 사는 표**로 충분하다.

### 16.3 D-A38 (High) — 같은 점검에서 나온 새 결함

`_L001` 의 조인을 읽다가 드러났다. 레거시 `tran_id` 에는 **날짜 성분이 없다**:

| 경로 | 생성식 |
|------|--------|
| 단건 | `"33" + hh24miss` (`biztalk_admin_50_s001_act.jsp:114`) |
| 다건 | `hh24miss + apiNumber++` (동 파일 :172) |

이 값이 `SERIALNUM` 이고, `_L001` 은 `RGDT BETWEEN :START_DT AND :END_DT` 로 **여러 날**을 조회한 뒤
`A.SERIALNUM = B.SERIALNUM` 으로 조인한다. 조회 범위가 하루를 넘고 같은 초에 발송이 있었다면 그
조인은 **카테시안 곱**이 되고 `TOTAL_CNT`·`AT_SCS_CNT`·`AT_FT_CNT` 가 **부풀려진다.**

**중복 ID 가 지저분하다는 이야기가 아니다 — 발송 통계 화면이 틀린 숫자를 보고한다.** 관측 가능한
증상이 있고, 영향 화면이 D-A25 와 다르므로(60 발송이력) 별도 결함으로 올렸다.

우리 `TranIdGenerator` 는 `env + yyMMdd + base36` 이라 재현하지 않으며
`differentDaysProduceDistinctValues` 가 고정한다. **다만 레거시가 남긴 기존 행에는 결함이 남는다** —
이력 조회는 과거 데이터를 계속 읽는다. 조회 측 완화는 A2-14 에서 결정한다.

### 16.4 결과 — G1 이 판단할 항목이 하나 줄었다

| | 이전 | **지금** |
|---|---|---|
| A2-02 DDL | 신규 테이블 1 + **공유 테이블 컬럼 1** | **신규 테이블 1** 뿐 |
| RISK-A06 | 활성 (영향 M, 확률 H) | ✅ **소멸** |
| G1 이 판단할 범위 | "표를 만들고 **공유 테이블에 컬럼도** 추가해도 되는가" | "표 하나를 만들어도 되는가" — 발신번호 슬라이스에서 **이미 승인된** CONFLICT-S01 과 동일 |

산출물: [ANALYSIS-A2-02-existing-schema.md](../../mapping/analysis/ANALYSIS-A2-02-existing-schema.md),
ADR-ATK-023 「수정 1」, risk-register RISK-A06 소멸, D-A38 등록

### 16.5 검증되지 않은 것 / not verified

레거시 소스만으로 판단했고 **데이터베이스에 접속하지 않았다.** `KKO_MSG_LOG` 의 실제 DDL, 게이트웨이가
`SERIALNUM` 을 항상 채우는지(`LEFT OUTER JOIN` 인 점이 아닐 가능성을 시사한다), 우리에게 읽기 권한이
있는지 — 셋 다 미확인이다. PM 이 접속을 승인하면 확정한다. **그 전까지 §16.1–16.2 는 근거 있는
권고이지 검증된 사실이 아니다.**

### 16.6 이 반복이 말해 주는 것

반복 4의 회고는 *"막힌 차원을 이유로 반복을 멈췄다면 결함은 계속 숨어 있었을 것"* 이라고 적었다.
이번에는 그것이 한 번 더, 다른 방향에서 확인되었다 — **결함을 찾은 것은 PM 의 질의였다.** 우리가
"G1 결재를 기다린다"고 적어 둔 항목 안에, 실행하면 바로 답이 나오는 점검이 하나 들어 있었고
(RISK-A06 대응 2번), 그것을 실행하기 전까지 우리 설계는 필요 없는 DDL 을 요구하고 있었다.

**대기 중인 항목 안에 대기할 필요가 없는 작업이 섞여 있는지 매번 확인한다** — A1-R22.
