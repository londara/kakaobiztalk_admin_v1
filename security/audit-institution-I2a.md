# Security Audit — 이용기관관리 Sprint I2a (수정 팝업)

> **Auditor**: `security-auditor` · **Date**: 2026-08-20
> **Scope**: the write path added in I2a — `GET /{code}`, `PUT /{code}`, `POST /{code}/key/rotate`, and the React edit modal
> **Threat model**: [threat-model-INSTITUTION.md](../docs/design/threat-model-INSTITUTION.md) v1.1 (TM-I021…I024 added by this pass)
> **Verdict**: **APPROVED WITH CONDITIONS** (§5) — no CVSS ≥ 7.0 defect open in this slice

---

## 1. Why this slice needed an audit rather than a review

The two highest-severity defects in the module are security defects, not feature defects: D-I2 (any authenticated user could overwrite any institution) and D-I3 (the duplicate check returned another institution's 인증키). This sprint touches the write path where both lived, and it introduces the **only endpoint in the programme that returns a plaintext credential** (`POST /{code}/key/rotate`).

## 2. Findings

### SI2a-01 — a production-observed 인증키 was test data · **HIGH** · FIXED

`6oG4mYDC6vrCLIyTzy8o` — the 인증키 visible on the live 이용기관 수정 screen for `K00000` — was used as a literal in five test files. Two of those files predate this sprint (`AtkMaskerTest`, `InstitutionServiceTest`, from Sprint I1); this sprint propagated the value into three more. A second observed value, `89uJFb0wEm1N4MjXohVF`, was present in the same way.

Three things make this a finding rather than untidiness:

1. **`ATK` is stored plaintext and verified directly by the legacy send runtime** (ADR-INST-015 §2.1). If the value is live, the repository contains a working credential for a client company.
2. **The L1 hook would have blocked the commit.** `gitleaks protect --staged` flags both values under `generic-api-key`; the hook exists precisely because live credentials were once found in legacy source (defect L1).
3. **The comment claimed the opposite of the truth** — *"An 인증키 shaped like the ones seen in production"* — so a reader would not have known to treat it as sensitive.

**Fix.** Both values were replaced with `SAMPLEsampleSAMPLE01` / `…02` across the slice's five test files, together with the masked expectations derived from them. The fixture comments now state that the values are synthetic and why. Testing a mask requires no real credential: every assertion still holds, because they are all about *shape*.

| gitleaks on `src/` | Before | After |
|--------------------|--------|-------|
| Findings | 10 | **6** |
| In this slice | 4 | **0** |

**Not fixed, and out of this slice** (recorded for the owning slices):

| File | Assessment |
|------|-----------|
| `AggregateRow.java:37`, `AggregateMapper.java:19` | **False positives** — the flagged string is the `// source: IDO.KKB_APITR_SMTN_L001, IDO.BULK_…` traceability comment. A narrowly-scoped allow rule in `hooks/gitleaks.toml` would be appropriate; disabling the rule would not |
| `SecretCipherTest`, `TotpVerifierTest`, `AuthenticationServiceTest`, `OtpRegisterPage.test.tsx` | Test OTP secrets in the 로그인 slice. Probably synthetic vectors, **not verified by this audit**. Any commit touching those four files will be blocked by the L1 hook until they are confirmed or replaced |

### SI2a-02 — the credential-returning endpoint · **ACCEPTED, controlled** · FR-ATK-001/004

`POST /{code}/key/rotate` returns a plaintext key in its response body. Reviewed against ADR-INST-015 and accepted, on these controls:

| Control | Evidence |
|---------|----------|
| Operator-only, enforced at three layers (routing rule, `@PreAuthorize`, service-level `requireOperator()`) | `InstitutionWriteAuthorizationTest$Unauthenticated`, `$WrongRole`; `InstitutionWriteServiceTest#refusesNonOperatorRotation` |
| CSRF token required | `InstitutionWriteAuthorizationTest$Csrf#rotationWithoutTokenIsRefused` |
| `POST`, so the credential never enters a URL or browser history | Contract |
| Value generated server-side by `SecureRandom`, 27 Base62 chars (≈160.7 bits ≥ the 128-bit floor) | `AtkGeneratorTest`; closes D-I4 |
| Never logged, never audited, not cached client-side | `InstitutionWriteServiceTest#neverAuditsKeyMaterial` asserts the audit detail contains neither the new key, nor the old, nor a masked fragment. `useAuthKeyRotation` does not cache the response |
| A dedicated response type, so the disclosure path is greppable | `AuthKeyResponse` — the only response type in the programme carrying a plaintext credential, and its Javadoc says so |

**Residual**: the operator sees the value once and reveal (FR-ATK-003) is not built until I2b, so a lost value is recoverable only by rotating again — RISK-I15. Accepted; it shrinks to nothing when reveal ships.

### SI2a-03 — D-I20, closed · **HIGH (was)** · FIXED

The defect this sprint discovered: the legacy detail service returned `ATK` in plaintext to any authenticated caller and the popup wrote it into the DOM. Closed on both sides:

- Server: `InstitutionService#findByCode` masks through the same `AtkMasker` path as the list, so no read path can emit the plaintext (`InstitutionServiceTest#masksAuthKeyOnDetail`, `InstitutionWriteAuthorizationTest#detailReturnsMaskedKey` asserts `$.authKey` does not exist in the payload).
- Client: `InstitutionEditDialog.test.tsx` asserts the rendered DOM contains no plaintext key.

### SI2a-04 — the edit form is not a delete path · **MEDIUM** · MITIGATED

`IS_STTS='D'` is the logical-delete marker (ADR-INST-014), and the edit form writes `IS_STTS`. Reaching `'D'` through this form would be a delete with no confirmation, no dependent-record preview and no deletion audit entry (TM-I023). Refused at both entry points — `@Pattern(regexp = "[YN]")` on the request and `InstitutionStatus` narrowing in the service — with tests at both (`deletedStatusIsRejected` ×2).

### SI2a-05 — enumeration surface · **LOW** · MITIGATED

Sprint I1 raised SI-04: 기관코드 values are sequential, so enumeration needs ~60 guesses. The new endpoints answer a missing institution with `404 {"code":"NOT_FOUND"}` and **do not echo the code**; a deleted institution is indistinguishable from an absent one. Asserted by `missingInstitutionIsNotFound`. Note this is an operator-only surface, so the enumeration concern is smaller here than on the availability endpoint that I2b will add — that one is where FR-INSTC-005 has to hold.

### SI2a-06 — audit completeness · **PASS** · FR-AZ-I04

Both state changes write an audit record with actor (session email), target 기관코드, action, source IP and correlation id, in a `REQUIRES_NEW` transaction so the evidence survives a business rollback. Update records a before→after diff of the changed fields only; 설명 records *that* it changed, never its content, so the audit store does not become a content repository. A no-change save is still recorded (`auditsNoChange`) — pressing 저장 is itself the audited event.

## 3. Regulatory checks

| Item | Result |
|------|--------|
| 전자금융감독규정 — administrative access records | PASS. Two new audited actions; append-only store |
| ISMS-P — credential lifecycle | PASS for generation, masking and rotation. **Storage remains plaintext** — RESIDUAL-I01 / TM-I005, unchanged by this sprint and outside its boundary |
| 개인정보보호법 | N/A. This slice handles no personal data. 기관명 and 사업자등록번호 are corporate identifiers; `LSED_ID`/`LSED_NM` hold the operator's work email, which the legacy also stored |
| NFR-SEC-LOG-I01 | PASS. No new log line carries a field value or a credential |
| NFR-SEC-INJ-I01 | PASS. Every new statement uses named binds; no concatenation |
| NFR-SEC-CSRF | PASS, and now **proven** — previously the client sent the header with no test that the server required it |

## 4. What this audit could not verify

| Item | Why |
|------|-----|
| That the SQL executes as read | Docker unavailable (RISK-I09). The three statements are verified by reading the XML — a substitute, not an equivalent |
| That the legacy runtime honours a portal-written `IS_STTS` | Outside the boundary (RISK-I02, TM-I013). Unchanged by this sprint |
| Rate limiting on rotation | None exists. One operator can rotate repeatedly; TM-I024 accepts this with no dual control |
| Whether the 로그인 slice's four flagged test secrets are synthetic | Not this slice's files; recorded in §2 |

## 5. Verdict — APPROVED WITH CONDITIONS

No CVSS ≥ 7.0 issue is open in this slice. SI2a-01 was HIGH and is fixed in the tree under review.

| # | Condition | Owner | Due |
|---|-----------|-------|-----|
| 1 | Confirm whether `6oG4mYDC6vrCLIyTzy8o` and `89uJFb0wEm1N4MjXohVF` are **live** keys. If so, removing them from source is necessary but not sufficient — they remain in git history, and the affected institutions should be rotated (FR-ATK-005 now makes that an operational action) | PM + 정보보호 | **before G3** |
| 2 | Resolve the four 로그인-slice gitleaks findings, or add narrowly-scoped rules with a stated reason. Until then the L1 hook blocks commits touching those files | 로그인 slice owner | before G3 |
| 3 | Add the two `// source:`-comment false positives to `hooks/gitleaks.toml` as a scoped allow rule | `security-auditor` | Sprint I2b |
| 4 | T-I1-13 — `GET /search` still has no HTTP-layer denial test (see code review condition 1) | `qa-engineer` | Sprint I2b |
