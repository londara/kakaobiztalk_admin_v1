# Sprint L1 Retrospective — 로그인 모듈

> **Sprint**: L1 · **Date**: 2026-08-14
> **Facilitator**: `team-leader` · **Log**: [SPRINT-L1-LOG.md](SPRINT-L1-LOG.md)
> **Template**: `templates/implementation/SPRINT-RETRO.template.md`

---

## 1. 잘된 점 / What went well

| # | Item | Why it mattered |
|---|------|-----------------|
| 1 | **결함 회귀 테스트를 코드와 함께 작성** — Regression tests written alongside the code | Each of the 3 test suites targets a specific legacy defect (L2, L6, L9). `producesDifferentHashForSamePasswordEachTime` fails against the legacy by construction, which is what makes it evidence rather than decoration |
| 2 | **추가 전용 DDL 설계** — Additive-only schema | Writing `PWD_HASH` as a new column instead of altering `PWD` solved three problems at once: legacy coexistence, RISK-L04 rollback, and migration progress tracking. The plan had treated rollback as a separate mitigation requiring extra work |
| 3 | **비활성 통제를 구조적으로 차단** — Disabled controls made structurally impossible | `AuditMapper` declares no delete; the DDL grants INSERT only; CI has 4 static rules each targeting a specific legacy defect. The legacy's failure was commented-out code that still looked correct from the call site — these cannot be commented out and still pass |
| 4 | **막힌 결정을 코드로 드러냄** — The blocked decision is visible in code | `PasswordHasher.matchesLegacy` throws with the ADR reference in its message. A developer who reaches it learns why, rather than finding a silent stub |

## 2. 아쉬운 점 / What did not

| # | Item | Impact |
|---|------|--------|
| 1 | **빌드 도구 부재를 사전에 확인하지 않음** — Build tooling was never verified | Maven is absent on this machine. ~50 test cases and 12 classes are **written but never executed**. This should have been the first check of step [A], before a line of code |
| 2 | **Sprint 범위가 용량을 초과** — Sprint scope exceeded capacity | 18.5 estimated person-days against a 10-day sprint. The cut order in the plan was followed (rate limiter, password change flow deferred), but 2 tasks still fell out entirely |
| 3 | **차단 항목이 sprint 시작 전에 해결되지 않음** — The blocker was not cleared before starting | ADR-LOGIN-011 was flagged as blocking in the sprint plan, and the sprint started anyway. Two tasks were dead on arrival |
| 4 | **7차원 루프가 적용 불가** — The self-assessment loop could not apply | Score 69.75, but the weakest dimensions are gated by an absent build tool and an undecided ADR. Regenerating code would consume all 5 cycles and move neither |

## 3. 배운 점 / What we learned

**환경 검증이 스켈레톤보다 먼저다.** Skill 04 step [A] lists directory tree, build script,
CI and hooks — but not "confirm the build actually runs". A build script that has never
been executed is a hypothesis. Verifying the toolchain is cheap and belongs before any
code is written.
*Environment verification precedes skeleton generation.*

**차단된 결정은 코드로 표현할 수 있다.** Rather than stubbing `matchesLegacy` to return
`false` — which would have quietly chosen "forced reset" — throwing with the ADR
reference keeps the decision where it belongs while still allowing everything around it
to be built and tested.
*A blocked decision can be represented in code without being made.*

**레거시의 결함 유형이 통제 설계를 결정한다.** The 문자내역 defects were gaps *between*
layers; the login defects were controls *disabled in place*. That difference is why this
sprint enforced append-only through DB privilege and CI rules rather than through code
review: the failure mode here is code that looks correct and does nothing.
*The shape of the legacy's defects should shape how controls are enforced.*

## 4. 개선 액션 / Improvement actions

| # | Action | Owner | Due | Standard impact |
|---|--------|-------|-----|-----------------|
| A1 | Install Maven; run `mvn verify`; record real coverage and correct the SPRINT-L1-LOG §4 scores | `backend-developer` | Next session, first action | — |
| A2 | Add **"toolchain verification"** as an explicit item in skill 04 step [A], before build-script generation | PM + `architect` | Before the next skeleton | **Harness change — HARNESS-PROCESS-STANDARD §4** |
| A3 | Rule on ADR-LOGIN-011, gated on the RISK-L01 exposure question | PM + 정보보호 | Before Sprint L2 | — |
| A4 | Rotate the legacy `sender_key` (RISK-L02) — independent of this project | PM + 정보보호 | Immediate | — |
| A5 | Add a rule to skill 04: **do not start a sprint with a task blocked by an open decision** — either resolve it or move the task out of scope | `team-leader` | Before Sprint L2 | **Harness change candidate** |
| A6 | Write the 24 negative-path SEC-L* tests (T-L1-18 remainder) | `security-auditor` | Sprint L2 | — |
| A7 | Carry T-L1-14 (rate limiter) and T-L1-17 (password change) into Sprint L2, and re-estimate before committing | `team-leader` | Sprint L2 planning | — |
| A8 | Review the `src/test/` vs `tests/` boundary in practice (ADR-LOGIN-013 §4.3) | `code-reviewer` | End of Sprint L2 | — |

> **A2 and A5 are proposed harness changes**, not just project actions. Both come from
> gaps in the standard itself rather than from how the team applied it: the standard has
> no toolchain-verification step, and nothing stops a sprint starting with a
> decision-blocked task in it.

## 5. 다음 Sprint 로 이월 / Carried into Sprint L2

| Task | Reason |
|------|--------|
| T-L1-03, T-L1-04, T-L1-19 | Blocked on ADR-LOGIN-011 |
| T-L1-14 (rate limiter) | Not started — capacity |
| T-L1-17 (password change flow) | Not started — capacity |
| T-L1-11, T-L1-13, T-L1-15, T-L1-18 remainder | Partial |
| Full Sprint L2 scope (OTP verification, registration, operator reset, React screen) | As planned |

Sprint L2 now carries roughly **1.5 sprints of work**. It needs re-planning before it
starts, not during — repeating this sprint's pattern would push the shortfall to L3.
