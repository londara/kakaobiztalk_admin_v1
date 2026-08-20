# Sprint A1 Retrospective — 카카오 알림톡

> **Version**: 1.0
> **Date**: 2026-08-18
> **Sprint**: A1 · **Status**: PARTIAL, not closed · **7-dimension**: 76.35 → 85.95 → **88.45** / 100 (three iterations; §7, §8)
> **Log**: [SPRINT-A1-LOG.md](SPRINT-A1-LOG.md)

---

## 1. What went well

**The cheap control caught the expensive defect class — twice, in our own code.** `ContractConformanceTest` is roughly 300 lines of test that reads two XML files. It closed four defects the legacy carried for over a year, and then immediately failed on `AlimTalkButton.isComplete()` serialising as an undeclared `complete` property. A separate control — thinking about the `gitleaks` fixture — caught us about to commit the real leaked credential into the new repository. **Both defects were introduced by us, in new code, and both were caught within the same day.** The controls are not ceremony.

**Blocked spikes did not stall the sprint.** Four of the five blocked tasks had a decision available that is safe under either answer (log §2.2). Building the DTOs with contract-declared fields only, and *not* writing the unverified `RsmsEnvelope`, both keep the eventual answer cheap.

**Reuse worked as designed.** `SenderNumberService`, `AuditService` and `TenantContext` were consumed as intended with no modification. The 발신번호 slice's investment in making the ledger trustworthy is what FR-ATS-004 will spend, and nothing had to be reopened.

## 2. What went badly

**Completeness at 55 is the worst in the programme, and it was predictable.** DEV-PLAN §1.2 named four spikes as "the honest cost of a first outbound integration" and §7 called third-party responsiveness "the schedule's weakest assumption". Both were right. What the plan got wrong was assuming a day-one request would return **within** the sprint. It scheduled the decision point at the sprint gate but gave the vendor no earlier deadline.

**We planned around Docker's absence and were then blocked by the database anyway.** RISK-A12 was assessed as *narrower* for this slice than RISK-S13 was for 발신번호, and that assessment holds — the Critical defects were all verifiable. But five carried tasks need a `KKB_MSG_TMPL` read, and "is a PostgreSQL instance reachable?" is a question inherited **open** from the previous slice and still unanswered. We recorded it as someone else's open item and then planned work that depends on it.

**An ADR passed G2 with arithmetic that its own diagram disproved.** ADR-ATK-026 allotted 11 characters to a 10-character field. The diagram was eleven characters wide on the page. Design review, including my own, did not add 1 + 6 + 4.

## 3. What we learned

**A control that runs is worth more than a control that is argued for.** ADR-ATK-021's case was that the legacy had "no path by which the defect could fail visibly". That was a claim about the past. It became a claim about the present the moment the test failed on our own DTO — and the defect it caught was *the same class* the ADR was written about. Prefer mechanisms that execute over rules that must be remembered; this is now evidenced in this codebase, not just asserted.

**Plans can carry defects into implementation.** Two of this sprint's corrections came from the plan, not the legacy: the `gitleaks` fixture wording instructed committing a live secret, and ADR-ATK-026's sequence arithmetic was wrong. Skill 3 outputs get the same review as Skill 4 outputs but not the same *execution*, and execution is what found both.

**"Blocked externally" is a schedule design problem, not a status.** Five tasks blocked on third parties is not bad luck for a first outbound integration — it is the shape of the work. The plan should have set vendor response deadlines with named fallbacks per spike, in week one, rather than a single decision point at the gate.

## 4. Improvement actions

| # | Action | Owner | Due |
|---|--------|-------|-----|
| A1-R1 | Set an explicit **vendor response deadline** for each of A1-01…A1-04, each with its named fallback, and escalate at the deadline rather than at the sprint gate | PM | Sprint A2 day 1 |
| A1-R2 | Answer **"is a PostgreSQL instance reachable?"** — inherited open from RISK-S13, now blocking 5 carried tasks. If not, adopt `embedded-postgres` (viable here: no proprietary DB functions needed) | architect + Ops | Sprint A2 day 1 |
| A1-R3 | Correct the harness guidance on secret-scan fixtures: **never the real literal**, always a synthetic value of the same shape. This is a standard change, not a slice fix | security-auditor + PM | Sprint A2 |
| A1-R4 | Require that any ADR asserting a **numeric or size claim** carries a test asserting it, written before G2 approval. ADR-ATK-026 would have failed at design time | architect | Sprint A2 |
| A1-R5 | Establish a **runnable build** in this environment — install Maven or add a wrapper — so coverage is measured rather than unmeasured. The 84 results are real but JaCoCo never ran | qa-engineer | Sprint A2 day 2 |
| A1-R6 | Wire `TenantContext` into the controller and write the endpoint authorization tests, closing the A1-16/A1-19 partials before A2 despatch work begins | backend-developer + security-auditor | Sprint A2 week 1 |
| A1-R7 | Re-baseline Sprint A1's remaining 10 tasks into A2 with explicit dependency gates, rather than carrying them as an undifferentiated backlog | team-leader | Sprint A2 day 1 |

## 5. Standard-change candidates

Two actions above are candidates for the harness standard rather than this project, per §4.9's provision that adjustments be recorded:

- **A1-R3** — the secret-scan fixture wording appears in the skill guidance and would mislead any slice that follows it.
- **A1-R4** — "ADRs asserting numbers carry an executable assertion" would have caught ADR-ATK-026 at G2. Three previous slices' ADRs were never executed at design time either, so this is a gap in the process rather than a lapse in this sprint.

Both should be raised with the standard owner with this sprint as the evidence.

## 6. Metrics

| Metric | Value |
|--------|-------|
| Tasks complete / partial / blocked / carried | 7 / 2 / 5 / 5 |
| Tests written · passing | 84 · 84 |
| Defects closed at code level | 13 of 35 |
| Defects found **in our own new code** | 2 (undeclared `complete` property; real credential in a test fixture) |
| Design corrections raised as ADR amendments | 2 (ADR-ATK-026 arithmetic, ADR-ATK-024 fixture) |
| Source files delivered · with `// source:` + `// req:` | 12 · 12 |
| Trace rows added | 32 |
| 7-dimension score | 76.35 (threshold 90) |
| Coverage | **unmeasured** — JaCoCo did not run |

---

## 7. Iteration 2 addendum

### 7.1 The two things iteration 1 declared blocked and were not

**Both had precedents in this repository that I did not look for.** `InstitutionMapperSqlTest` already showed how to verify mapper SQL without a database, and the JaCoCo agent was sitting in `~/.m2`. A1-11, A1-12 and the coverage measurement were all reachable in the first pass.

The pattern is worth naming: **"blocked on X" was recorded after checking whether X was available, and not after checking whether X was actually required.** Mapper *correctness* needs a database; mapper *identifier regression* does not. Coverage needs an agent, not Maven.

### 7.2 Measuring changed the picture, and counting had hidden it

The first coverage measurement returned line 77.3 % / branch 58.7 % on code that already had 84 passing tests. The shortfall was concentrated in four validation methods written in iteration 1 and **never executed once** — `isComplete()`, `isValid()`, `within()`, `ProfileKey.of()`. The conformance test guarded field *names*; nothing checked field *rules*.

Eighty-four passing tests read as "verified" and were not. After closing the gap: line 85.5 %, branch 94.8 %.

### 7.3 A third defect in our own work — and this one is in the vendor contract

Writing the missing validation tests produced D-A36: the contract declares `failback_data.type` as `length="2"` while its only valid values (`SMS`/`LMS`/`MMS`) are three characters, because the sub-rule is a verbatim copy of `button`'s — `name="버튼타입"` included. It may mean the fallback has been transmitted truncated in production all along, **independently of D-A1**.

Three defects have now been found in our own new code or in the contract, each by a control rather than by review: the undeclared `complete` property, the real credential in a test fixture, and D-A36. **Review found none of them.**

### 7.4 Additional improvement actions

| # | Action | Owner | Due |
|---|--------|-------|-----|
| A1-R8 | When recording a task as blocked, state **which verification tier** is blocked rather than the whole task. Iteration 1 lost two tasks to an over-broad block | team-leader | Sprint A2 day 1 |
| A1-R9 | Run coverage **on the first day** of a sprint, not at its assessment. Its value here was diagnostic, not reporting — it found untested logic while there was still time to test it | qa-engineer | Sprint A2 day 1 |
| A1-R10 | Add D-A36's truncation question to spike A1-02's brief: the `RSMS` capture now answers **two** questions, and the second is whether the fallback has ever worked | architect | Sprint A2 day 1 |

### 7.5 Updated metrics

| Metric | Iter 1 | Iter 2 |
|--------|-------:|-------:|
| Tasks complete / partial / blocked / carried | 7 / 2 / 5 / 5 | **10 / 2 / 5 / 2** |
| Java tests written · passing | 84 · 84 | **135 · 135** |
| Frontend tests passing (whole suite) | — | **127 across 11 files** |
| Line coverage | unmeasured | **85.5 %** (target 80 %) |
| Branch coverage | unmeasured | **94.8 %** (target 70 %) |
| Defects closed at code level | 13 of 35 | **18 of 36** |
| Defects found in our own code / the contract | 2 | **3** (D-A36 added) |
| Trace rows added | 32 | **57** |
| 7-dimension score | 76.35 | **85.95** (threshold 90) |

---

## 8. Iteration 3 addendum

### 8.1 The defect was found by asking whether a control actually runs

Iteration 3 had one goal: close the A1-16/A1-19 authorization partials. Doing that required asking a question that had gone unasked for three slices — **is `@PreAuthorize` enforced?** It was not. `@EnableMethodSecurity` is absent from the committed source, so five controllers from earlier slices carried an annotation that did nothing (**D-A37**).

The programme has now hit this defect class **three times**: the legacy's browser-side `alert('권한 없음')` (D-S2), the legacy's composer that nothing validated (D-A1/A2/A3), and now our own inert annotations. Each time the shape is identical — *a control that is visible, documented, and not executing.*

### 8.2 A near-miss in the verification itself

My first check ran `javap` against `target/classes` and found the annotation present, appearing to refute the finding. The IDE had rebuilt that directory from my own edit minutes earlier. **I nearly reported a real defect as a false alarm because I verified against a mutable artefact instead of the committed baseline.** Re-running against `git show HEAD:` settled it.

Worth generalising: when a finding concerns the *absence* of something, verify against the baseline, not the working tree — the working tree may already contain your fix.

### 8.3 An aggregate that passes can hide a component at zero

Iteration 2 reported line coverage of 85.5 %, above target. That figure **included `AlimTalkController` at 0.0 %** — the class handling every request had never been executed by a test. Adding standalone MockMvc took it to 100 % and the package to 99.2 %.

This is §7.2's lesson one level up: counting tests hid untested logic, and now an aggregate coverage figure hid an untested class. **Per-component figures, not just totals.**

### 8.4 The classpath spiral is evidence, not an anecdote

Reaching standalone MockMvc by hand required six jars discovered one `NoClassDefFoundError` at a time. A full `@WebMvcTest` — which is what the remaining 403 assertion needs — is further still. **A1-R5 (get a runnable build) is not housekeeping; it is the boundary between reaching an integration test and not reaching one.** It should be re-prioritised above further feature work in A2.

### 8.5 Additional improvement actions

| # | Action | Owner | Due |
|---|--------|-------|-----|
| A1-R11 | **Notify the 로그인 / 이용기관관리 / 발신번호 slice owners of D-A37** and have each confirm their `@PreAuthorize` expressions now that they execute. Their design documents claim a defence-in-depth layer that did not exist | security-auditor + PM | **Sprint A2 day 1** |
| A1-R12 | Add an assertion to the shared test baseline that **method security is enabled**, so this cannot silently regress for any future slice | security-auditor | Sprint A2 |
| A1-R13 | Report coverage **per class**, not only as a total. Iteration 2's passing aggregate concealed a 0 % controller | qa-engineer | Sprint A2 |
| A1-R14 | When a finding is about something being **absent**, verify against the committed baseline rather than the working tree or build output | team-leader | standing |

### 8.6 Updated metrics

| Metric | Iter 1 | Iter 2 | Iter 3 |
|--------|-------:|-------:|-------:|
| Tasks complete / partial / blocked / carried | 7 / 2 / 5 / 5 | 10 / 2 / 5 / 2 | **11 / 1 / 5 / 2** |
| Java tests passing | 84 | 135 | **154** |
| Frontend tests passing | — | 127 | 127 |
| Line coverage | unmeasured | 85.5 % | **99.2 %** |
| Branch coverage | unmeasured | 94.8 % | **96.1 %** |
| Defects closed at code level | 13 / 35 | 18 / 36 | **19 / 37** |
| Defects found in our own code, the contract, or the programme | 2 | 3 | **4** (D-A37 added) |
| Trace rows added | 32 | 57 | **69** |
| 7-dimension score | 76.35 | 85.95 | **88.45** (threshold 90) |

---

## 반복 4 회고 / iteration 4 retrospective (2026-08-19)

### 개선 액션 / improvement actions

| ID | 액션 | 왜 | 담당 | 기한 |
|----|------|-----|------|------|
| **A1-R15** | Maven 부재 환경의 테스트 실행을 스크립트로 고정한다 (`qa/run-alimtalk-tests.sh`) | 세 차례 반복에서 매번 클래스패스를 손으로 다시 조립했다 — `NoClassDefFoundError` 를 하나씩 만나며 같은 탐색을 세 번 했다 | team-leader | ✅ **완료** |
| **A1-R16** | **코드를 더할 때마다 커버리지를 다시 잰다.** 엔드포인트 추가 시 백엔드 테스트 없이 화면 테스트만 붙이는 것을 금지한다 | `/compose/batch` 가 화면 테스트만 갖고 있었고, 화면 테스트는 서버를 대역으로 세운다 — 서버 코드가 **한 번도 실행되지 않은 채** "테스트됨" 으로 보였다 (0 %). 반복 3의 착시가 형태만 바꿔 재발했다 | qa-engineer | 매 반복 |
| **A1-R17** | 커버리지는 **클래스별로** 본다. 합계만 보고하지 않는다 | 합계 85.5 % 가 컨트롤러 0 % 를 가렸고(반복 3), 합계 82.3 % 가 배치 DTO 0 % 를 가렸다(반복 4). 같은 실패가 두 번 | qa-engineer | ✅ **완료** (`qa/CoverageReport.java`) |
| **A1-R18** | 클래스가 Javadoc 으로 **약속한 안전 성질**은 테스트로 고정한다 | `TemplateRegistry` 는 "낡은 규칙으로 검증하지 않는다"고 적어 두고 그것을 32비트 해시에 맡겼다. 문서의 주장이 기제보다 강했고, 그 격차를 아무도 검사하지 않았다 | code-reviewer | 다음 반복 |
| **A1-R19** | 새 회귀 테스트는 **옛 코드에서 실패하는지 확인**한 뒤에 FIXED 로 적는다 | 옛 코드에서도 통과하는 테스트는 아무것도 증명하지 않는다. CR-A01 은 임시 사본에 옛 구현을 되살려 2건 실패를 확인했다 | qa-engineer | 상시 |
| **A1-R20** | 슬라이스를 열 때 CI 정적 규칙에 **그 슬라이스의 결함 부류**를 추가한다 | `L2 static rules` 는 "실제로 발생한 결함을 겨냥한다"고 적혀 있었지만 6개 규칙이 전부 login 슬라이스 것이었다. 알림톡의 D-A24·D-A30 을 CI 가 모르고 있었다 | security-auditor | 슬라이스마다 |
| **A1-R21** | CI 규칙은 **발화하는지** 확인한다 — 합성 결함으로 양방향 검증 | 발화하지 않는 규칙은 규칙이 아니라 장식이고, 장식은 통과했다는 착각만 준다 | security-auditor | ✅ **완료** (3건) |

### 지표 / metrics

| | Iter 2 | Iter 3 | **Iter 4** |
|---|---:|---:|---:|
| Java tests passing | 135 | 154 | **196** |
| Frontend tests passing | 127 | 127 | **142** |
| Line coverage | 85.5 % | 99.2 %※ | **97.0 %** |
| Branch coverage | 94.8 % | 96.1 %※ | **90.3 %** |
| CI static rules | 6 | 6 | **9** |
| Trace rows | 57 | 69 | **79** (총 146행) |
| Defects found in our own code / contract / programme | 3 | 4 | **7** (CR-A01·CR-A03·QA-A01 추가) |
| 7-dimension score | 85.95 | 88.45 | **89.3** (임계 90) |

> ※ 반복 3의 99.2 % / 96.1 % 는 배치 엔드포인트가 생기기 **전**의 좁은 범위에서 잰 값이다.
> 같은 범위를 이번에 다시 재니 **82.3 % / 79.2 %** 였고, 보완 후 97.0 % / 90.3 % 다. 수치가 내려간
> 것이 아니라 **범위가 넓어진 뒤 정직해진 것**이다.
>
> ※ Iteration 3's 99.2 % / 96.1 % was measured over a narrower scope, before the batch endpoints existed.
> Re-measured over the current scope it was **82.3 % / 79.2 %**, and 97.0 % / 90.3 % after the gap was
> closed. The figure did not fall — it became honest about a wider surface.

### 이번 반복이 확인해 준 것 / what this iteration confirmed

반복 3은 "완성도가 외부에 묶여 있으니 네 번째 반복은 점수를 못 올린다"고 적고 종료했다. 그 판단은
맞았지만 **결론이 틀렸다** — 완성도를 못 움직이는 것과 아무것도 못 움직이는 것은 다르다. 실제로
이번 반복은 결함 3건을 더 찾았고, 그중 하나(QA-A01)는 배치 엔드포인트가 **서버에서 한 번도 실행된
적 없다**는 사실이었다. 막힌 차원을 이유로 반복을 멈췄다면 그것은 계속 숨어 있었을 것이다.

Iteration 3 closed by reasoning that a fourth pass could not raise the score because completeness was
externally pinned. The premise was right and the **conclusion was wrong**: being unable to move one
dimension is not the same as being unable to move any. This pass found three more defects, one of them the
fact that the batch endpoint had **never executed on the server**. Had the blocked dimension been taken as
a reason to stop, it would still be hidden.

---

## 반복 5 회고 / iteration 5 retrospective (2026-08-19)

| ID | 액션 | 왜 | 담당 | 기한 |
|----|------|-----|------|------|
| **A1-R22** | **"결재 대기" 로 묶인 항목 안에, 결재 없이 실행 가능한 점검이 섞여 있는지 매번 확인한다** | RISK-A06 의 대응 계획 2번(`SELECT *` 점검)은 G1 과 무관하게 실행 가능했는데 "A2-02 는 G1 대기" 라는 이유로 함께 묶여 있었다. 실행해 보니 설계가 요구하던 DDL 절반이 **불필요**했다 | team-leader | 매 반복 |
| **A1-R23** | 신규 표를 설계하기 전에 **기존 스키마가 이미 그 일을 하고 있는지** 조사한다 | `KKO_MSG_LOG` 는 이미 메시지 단위 상태·벤더 결과 코드·암호화 PII 를 갖고 있었다. 조사 없이 상태 컬럼을 추가했다면 **잘못된 단위**에 중복 상태를 만들었을 것이고, 그것을 되돌리는 비용은 만드는 비용보다 크다 | data-model-designer | 신규 DDL 마다 |
| **A1-R24** | ADR 이 스스로 세운 원칙을 **같은 ADR 안에서** 어기는지 점검한다 | ADR-ATK-023 은 §"Partial batch outcomes" 에서 *메시지 단위*를 요구해 놓고, §"New schema" 에서 *발송 행위 단위* 표에 상태를 두려 했다. 두 절 사이의 모순을 아무도 검사하지 않았다 | architect | ADR 작성 시 |

### 이번 반복의 성격 / what kind of iteration this was

코드를 거의 쓰지 않았다. 한 일은 **이미 계획에 적혀 있던 점검 하나를 실제로 실행한 것**이다.
그 결과 DDL 요구가 절반으로 줄고, 리스크 하나가 소멸했으며, 새 결함(D-A38)이 나왔다.

Almost no code was written. What happened was that **one check already written into the plan was actually
executed** — and the DDL requirement halved, a risk retired, and a new defect (D-A38) surfaced.

**출처가 PM 질의였다는 점이 기록될 만하다.** 세 번의 반복 동안 "A2-02 는 G1 결재 대기" 로 적어 두고
그 안을 다시 들여다보지 않았다. 밖에서 온 한 문장 — *"기존 테이블이 있는데 왜 필요한가"* — 이
그것을 열었다.

**That it came from a PM challenge is worth recording.** For three iterations "A2-02 awaits G1" was written
down and never re-opened. One sentence from outside — *why do you need it when the table already exists* —
opened it.
