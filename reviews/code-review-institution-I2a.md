# Code Review — 이용기관관리 Sprint I2a (수정 팝업)

> **Reviewer**: `code-reviewer` · **Date**: 2026-08-20
> **Scope**: the 기관코드 → 이용기관 수정 path — detail read, update, 인증키 재발급, and the React modal
> **Plan**: [DEV-PLAN-INSTITUTION.md §4.4](../docs/design/DEV-PLAN-INSTITUTION.md) (T-I2a-01…11)
> **Verdict**: **APPROVE with two recorded conditions** (§6)

---

## 1. What was reviewed

| Layer | Artifacts |
|-------|-----------|
| Domain | `AtkGenerator`, `InstitutionWriteService`, `InstitutionLimits`, `InstitutionEdit`, `InstitutionNotFoundException`, `InstitutionValidationException`, `InstitutionService#findByCode` |
| API | `InstitutionAdminController` (+3 endpoints), `InstitutionUpdateRequest`, `AuthKeyResponse`, `InstitutionExceptionHandler` |
| Persistence | `InstitutionAdminMapper` (+3 statements), `InstitutionAdminMapper.xml` |
| Cross-cutting | `AuditEvent` (+2 action constants) |
| Frontend | `InstitutionEditDialog.tsx`, `InstitutionPage.tsx`, `institutionApi.ts`, `queries.ts`, `styles.css` |
| Tests | 70 backend + 22 frontend cases added |

## 2. Mandatory items (Skill 04 §5)

| Item | Result | Evidence |
|------|--------|----------|
| `// source:` or `// req:` on every method | **PASS** *(after correction)* | Nine methods across `AtkGenerator`, the two exceptions and `InstitutionUpdateRequest` were missing a tag and were annotated during review. Verified by grep over each new file |
| Korean + English Javadoc on public classes/methods | **PASS** | Every new public type carries both, with the rationale rather than a restatement of the signature |
| ADR for a design change | **PASS** | [ADR-INST-017](../docs/design/adr/ADR-INST-017-timestamp-clock-authority.md) records the clock decision, which is a boundary change rather than a coding preference |
| `BigDecimal` for money | **N/A** | This slice writes no monetary value. `GRAMT` exists on `FT_FTIS_INFO` and is **not** written (ADR-INST-016 rule 4) — asserted by `InstitutionAdminMapperXmlTest#doesNotTouchUnownedColumns` |
| PII masking in logs | **PASS** | No new log statement carries a field value. `InstitutionExceptionHandler` logs a field *name* and a code, never a submitted value |
| Secrets 0 | **PASS** *(after correction)* | See [audit-institution-I2a.md](../security/audit-institution-I2a.md) SI2a-01. gitleaks on `src` fell from 10 findings to 6, none in this slice |
| Conventional Commits | **NOT APPLICABLE YET** | No commit was made; the working tree is staged for PM review. The hook applies at commit time |

## 3. What the design gets right

**The three absences are the review's main finding, in the positive sense.** The update statement has no `ATK` column, the request record has no `authKey` field, and neither carries a `code`. Each of the slice's three highest-severity write-path defects therefore becomes *unrepresentable* rather than *guarded*:

| Legacy defect | Usual mitigation | What was done instead |
|---------------|------------------|----------------------|
| D-I6 — blind upsert overwrote an existing institution | "check for existence first" | The statement is an `UPDATE`; `hasNoInsert` asserts no `INSERT` exists in the file |
| Masked value written back as the credential (TM-I022) | "validate the field before saving" | There is no field. `updateCommandCarriesNoKeyField` asserts the record's components |
| Body-supplied 기관코드 changing the target (FR-INSTC-002) | "compare body against path" | The record has no `code`; the path is the only source |

This is the pattern the programme has been converging on and it is applied consistently here. `InstitutionLimits` deserves the same note: two validation entry points referencing one set of constants is the right answer to D-I19, where the form said 6 characters and the contract said 16.

**Statement-shape tests earn their place.** `InstitutionAdminMapperXmlTest` asserts what must *not* be in the SQL. Docker is still unavailable (RISK-I09), and the test records itself as a substitute rather than an equivalent — the same honesty as `TalkHistoryMapperXmlTest`.

## 4. Findings raised and resolved during review

| ID | Finding | Severity | Resolution |
|----|---------|----------|------------|
| CR-I2a-01 | Nine methods carried no `// req:`/`// source:` tag (Skill 04 §5 requires all) | Medium | Fixed in place; tags added |
| CR-I2a-02 | The dialog rendered **no visible close control** when the detail read failed — the action row lived inside the `form` block, which is not rendered without data. Escape worked; nothing on screen said so, and a backdrop click deliberately does not close | **Medium** | Fixed: a 닫기 row renders whenever the form is absent. Regression test `조회에 실패해도 닫을 수 있다` |
| CR-I2a-03 | Read-only values used `<output>`, whose implicit `role="status"` makes them live regions — a screen reader would announce the 기관코드 out of context | Low | Fixed: plain `<span>`, with the reason recorded inline |
| CR-I2a-04 | The API layer had **0% coverage** — no test exercised the controller, the request record or the 404 advice, so the `@PreAuthorize` annotations and the advice ordering were claims | **High** | Fixed: `InstitutionWriteAuthorizationTest`, 18 cases including CSRF refusal and an advice-ordering assertion. Controller now 78.6% (the uncovered method is Sprint I1's `search`) |
| CR-I2a-05 | A production-observed 인증키 was used as test data in five files | **High** | Fixed — see audit SI2a-01 |

CR-I2a-04 is the one worth dwelling on. `InstitutionWriteServiceTest#refusesNonOperator` proved the *service* refuses, and it is easy to read that as covering FR-AZ-I01. It does not: routing, `@PreAuthorize`, CSRF and the exception advice are only observable through the filter chain. Sprint I1's log recorded the same gap for `search` (T-I1-13) and it is **still open** — a control asserted by annotation and never by a test.

## 5. Deliberate deviations, accepted

| Deviation | Why it is accepted |
|-----------|--------------------|
| `InstitutionWriteService` is a new component the architecture's table did not name | The table assigned the read path and the lifecycle path and left 등록/수정 homeless. Recorded in DEV-PLAN §3 rather than invented silently |
| `LAST_AMDT` is written in SQL, not from the injected `Clock` | ADR-INST-017. The programme-wide UTC clock is right for audit records and wrong for a wall-clock column a second live system writes |
| `update` re-reads the row after writing (two extra queries per save) | The DB owns `LAST_AMDT`, so the response cannot be assembled from the request. NFR-PERF-I03 is P95 < 1 s on a table of hundreds of rows; three round trips are affordable and the alternative is a screen that disagrees with the database |
| Client-side validation is not duplicated | Length and format rules live only on the server; `maxLength` is a typing affordance. Duplicating them is how D-I19 became possible (form 6 vs contract 16) |

## 6. Verdict — APPROVE, with two conditions

**APPROVE.** All five review findings are resolved in the tree under review; the two conditions below are *carry-forward obligations*, not defects in this code.

| # | Condition | Owner | Due |
|---|-----------|-------|-----|
| 1 | **T-I1-13 remains open** — `GET /search` still has no HTTP-layer test proving it denies a non-operator. This slice added the suite for its own three endpoints; the fourth endpoint on the same controller is uncovered, and D-I2 is the slice's highest-severity defect | `qa-engineer` | Sprint I2b, before G3 |
| 2 | **Mapper SQL is unverified against a database** (RISK-I09). The three new statements are checked by reading the XML. `to_char(now(),'YYYYMMDDHH24MISS')`, the `IS_STTS <> 'D'` guard and the column names are **not** proven to execute | PM (Docker/embedded decision) | before G3 |

No REJECT criterion was met: no hardcoded secret survives, no method lacks traceability, no design change is undocumented, and no test was weakened to pass.
