# Use Case UC-ATK-004: Validate message content against its registered template

> **REQ-ID**: UC-ATK-004
> **Version**: 1.0
> **Predecessor**: [REQUIREMENTS-SPEC-ALIMTALK.md](../REQUIREMENTS-SPEC-ALIMTALK.md)
> **Legacy origin**: screen 61 검증 tab — `biztalk_admin_61.js` `validateTemplateStrict()` (lines 1041–1150); registry `IDO.KKB_MSG_TMPL_L002` (`TEMPLATE_MSG`)

---

## 1. Scenario overview

| Item | Content |
|------|---------|
| **Primary user** | Internal operator |
| **Precondition** | Authenticated session with an operator role |
| **Trigger** | The operator composes a message (automatic, per UC-ATK-001 step 9 and UC-ATK-002 step 7), or opens the 검증 tab for a template not yet registered |
| **Success outcome** | The message is confirmed to conform to its registered template, correctly — including cases the legacy validator rejected in error |
| **Failure outcome** | Every divergence is reported with its position |
| **Related FR** | FR-ATV-001…008, FR-ATT-001/003 |
| **Related BR** | BR-002 |

> **Two changes of substance.** First, validation moves from a manual tab to the send path: `KKB_MSG_TMPL.TEMPLATE_MSG` already stores the template body against exactly the `(IS_CD, TEMPLATE_CODE)` the composer collects, so the comparison the operator performed by copy-and-paste can run automatically where it prevents a vendor rejection (D-A16). Second, the matching algorithm is **corrected, not ported** (PM ruling AMB-A00b) — so this use case's regression tests assert the **new** behaviour, and TC-A004-02 is a test the legacy would fail by design.

## 2. Main flow

| Step | Actor | Action | System response |
|------|-------|--------|-----------------|
| 1 | System | Receives a message and its `(is_cd, template_code)` | From composition or from send validation |
| 2 | System | Retrieves the registered template body | `KKB_MSG_TMPL.TEMPLATE_MSG`, scoped to the operator's entitlement (FR-ATV-001, FR-ATT-003) |
| 3 | System | Compiles the template to a match pattern | Literal segments matched exactly; `#{...}` variables match minimally (FR-ATV-004, FR-ATV-008) |
| 4 | System | Matches the message against the pattern | Each variable must consume at least one character (FR-ATV-005) |
| 5 | System | Reports the result | Conformant, or every divergence with its position (FR-ATV-002, FR-ATV-006) |
| 6 | Operator | Corrects the message if required | Re-validated on change |

## 3. Alternative flows

### 3.1 A-1: Manual validation for an unregistered template
- Branches at Step 2. The operator supplies the template body directly in the 검증 tab. The **same implementation** performs the match (FR-ATV-007), so a manual result and an automatic result can never disagree.

### 3.2 A-2: Validation invoked during composition
- At UC-ATK-001 step 9. The result is advisory during composition and blocking at send (FR-ATV-002).

### 3.3 A-3: A template contains no variables
- At Step 3. The comparison degenerates to an exact literal match.

## 4. Exception flows

### 4.1 E-1: A variable's value contains the next literal character
- At Step 4.
- Action: **matches correctly** (FR-ATV-004).
- **This is the defect that drove PM ruling AMB-A00b.** `validateTemplateStrict()` advanced to the *first occurrence* of the next literal character after each variable:
  ```
  while (idxContent < content.length && content[idxContent] !== nextFixedChar) idxContent++;
  ```
  With template `#{name}님 안녕` and content `김님철수님 안녕`, the scan stops at index 1 — inside the substituted value — and the following comparison of `님 안녕` against `님철수님 안녕` fails at the space. **Valid content was reported as invalid** (D-A6). The corrected matcher accepts it.
- **Not a parity regression.** QA must not file this as a behavioural break to be reverted; the break is the fix.

### 4.2 E-2: A variable is substituted with nothing
- At Step 4.
- Action: reported as a mismatch (FR-ATV-005). **Regression guard:** the legacy accepted a zero-length substitution, so template `#{a}b` matched content `b`.

### 4.3 E-3: A message diverges in several places
- At Step 5.
- Action: all divergences reported (FR-ATV-006). **Regression guard:** the legacy returned after the first error in all four of its exit paths (`early-end`, `mismatch`, final-literal `mismatch`, `extra-content`), so an operator fixed one error per attempt.

### 4.4 E-4: The template uses `${...}` rather than `#{...}`
- At Step 3.
- Action: variable syntax is stated in the specification and applied consistently (FR-ATV-008). **Regression guard:** the legacy pattern `/(\#\{[^}]+\}|\$\{[^}]+\})/g` accepted both, though only `#{...}` is Kakao's — so a template written with `${...}` validated locally and would be rejected downstream.

### 4.5 E-5: No template is registered for the code
- At Step 2.
- Action: reported as *unregistered*, distinctly from a content mismatch (FR-ATV-003). **Regression guard:** the legacy had no registry lookup at all — the operator pasted whatever body they believed to be current, with nothing to confirm it.

### 4.6 E-6: The operator requests a template outside their entitlement
- At Step 2.
- Action: rejected (FR-ATT-003). **Regression guard:** `KKB_MSG_TMPL_L003` returns every active institution's templates in a single query with no operator scoping.

### 4.7 E-7: Message content is longer than the message field allows
- At Step 4.
- Action: rejected against `min(contract, Kakao)` per CONFLICT-A02 — the contract bound for `msg` is 4000, Kakao's business limit is lower (FR-ATC-005, CONST-DATA-A02). **Regression guard:** the validator checked no length at all; neither did the composer (D-A7).

## 5. NFR mapping

| NFR-ID | Application |
|--------|-------------|
| NFR-PERF-A01 | Steps 3–4: P95 < 300 ms |
| NFR-SEC-AUTHZ-A01 | Step 2: registry read authorized server-side |
| NFR-SEC-INJ-A01 | Step 2: `template_code` bound as a parameter |
| NFR-USE-A03 | Step 5: divergence named with its position, not a generic pass/fail banner |

## 6. Screen sketch

```
┌─ 검증 ────────────────────────────────────────────────────┐
│ 템플릿   [▼ TMPL_0001 · 결제안내 ]   또는 [ 직접 입력 ]     │
│ ┌ Template ──────────────┐ ┌ Content ────────────────────┐ │
│ │ #{고객명}님,           │ │ 김님철수님,                  │ │
│ │ #{금액}원이            │ │ 50,000원이                   │ │
│ │ 결제되었습니다.        │ │ 결제되었습니다.              │ │
│ └────────────────────────┘ └──────────────────────────────┘ │
│                        [ 검증하기 ]                        │
│ ┌──────────────────────────────────────────────────────────┐│
│ │ ✔ 성공: 템플릿과 입력이 일치합니다.                      ││
│ │   (#{고객명} = "김님철수" — 값에 '님'이 포함되어도 정상) ││
│ └──────────────────────────────────────────────────────────┘│
└────────────────────────────────────────────────────────────┘
```

## 7. Test scenarios

| Test ID | Case | Input | Expected |
|---------|------|-------|----------|
| TC-A004-01 | Normal match | `#{a}원 결제` / `50,000원 결제` | Conformant |
| TC-A004-02 | **D-A6 — corrected behaviour** | `#{name}님 안녕` / `김님철수님 안녕` | **Conformant.** The legacy reported a mismatch at the space; this test asserts the fix, not parity |
| TC-A004-03 | **D-A6 — corrected behaviour** | `#{a}-#{b}-끝` / `1-2-3-4-끝` | Conformant; a value containing the delimiter does not break the match |
| TC-A004-04 | **D-A6 regression** | Genuine mismatch `#{a}원 결제` / `50,000원 취소` | Non-conformant, divergence located at `취소` |
| TC-A004-05 | Empty substitution | `#{a}b` / `b` | Non-conformant (FR-ATV-005) |
| TC-A004-06 | Multiple divergences | Content differing in three places | All three reported (FR-ATV-006) |
| TC-A004-07 | Extra trailing content | `#{a}원` / `50,000원입니다` | Non-conformant, `extra-content` located |
| TC-A004-08 | Truncated content | `#{a}원 결제되었습니다` / `50,000원 결제` | Non-conformant, `early-end` located |
| TC-A004-09 | No variables | Literal template, identical content | Conformant |
| TC-A004-10 | Variable syntax | Template using `${a}` | Handled per FR-ATV-008, consistently with the registry |
| TC-A004-11 | Unregistered code | `template_code` with no `TEMPLATE_MSG` | Reported as unregistered, distinct from a mismatch |
| TC-A004-12 | Entitlement | Another institution's `template_code` | 403; no template body returned |
| TC-A004-13 | Shared implementation | Same input through the manual tab and the send path | Identical verdict (FR-ATV-007) |
| TC-A004-14 | Send-time blocking | Non-conformant message submitted to 발송 | Rejected before any vendor call (FR-ATV-002) |
| TC-A004-15 | Length | Message exceeding `min(contract, Kakao)` for `msg` | Rejected (CONFLICT-A02) |
| TC-A004-16 | Newline and space handling | Template and content differing only in a newline | Non-conformant, position counted consistently |
