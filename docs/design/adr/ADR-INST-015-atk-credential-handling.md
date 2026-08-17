# ADR-INST-015: 인증키 (ATK) generation, storage and exposure

> **Status**: ACCEPTED (with recorded residual risk)
> **Date**: 2026-08-14
> **Deciders**: PM (AMB-I04), `security-auditor`, `architect`
> **Slice**: 이용기관관리
> **Related**: [ADR-007](ADR-007-key-management.md), [ADR-008](ADR-008-channel-auth.md), [ADR-INST-016](ADR-INST-016-legacy-coexistence.md)

---

## 1. Context

`ATK` is the credential a client company presents when calling the BizTalk send API. It is stored on `FT_FTIS_INFO.ATK`. The legacy handling has four independent defects:

| Defect | Detail |
|--------|--------|
| **D-I4** | Generated **in the browser** by `Math.random()` — 20 characters from a 62-character alphabet, in a handler named "generate random 32 byte". `Math.random()` is not a CSPRNG; V8's xorshift128+ state is recoverable from a short output sequence |
| **D-I5** | Rendered **unmasked in the list grid** for every institution, and declared in the `biztalk_admin_00_l001` contract's out-rule |
| **D-I3** | Returned **in full by the duplicate-check endpoint** for any existing 기관코드 — 6 characters with a fixed `K0` prefix, so 4 unknown characters to enumerate |
| **D-I6** | A create call with an existing 기관코드 silently **overwrites** the institution, replacing its `ATK` and breaking that customer's integration |

The constraint that shapes everything: **these keys are live**. Client companies hold them in their own configuration. Changing a value breaks that customer's integration until they are notified and redeploy.

PM ruled (AMB-I04): **preserve existing keys, close every exposure path, move generation server-side.**

## 2. Decision

Treat `ATK` as a credential in every respect **except rotation**, which is made possible but not performed.

| Concern | Decision | Requirement |
|---------|----------|-------------|
| **Generation** | Server-side `SecureRandom`, 160 bits of entropy, Base62-encoded to 27 characters. Never generated on the client | FR-ATK-001 |
| **Storage** | **Plaintext, unchanged.** The send API must present the key for comparison and that path is not ours to change (ADR-INST-016) | CONST-BIZ-I01 |
| **List exposure** | Masked to the last 4 characters (`••••••••7f3a`). The full value never enters a list response payload | FR-ATK-002 |
| **Reveal** | A separate, explicitly authorized, individually audited endpoint — not a field on the list | FR-ATK-003 |
| **Duplicate check** | Returns a boolean availability result and nothing else | FR-ATK-004, FR-INSTC-005 |
| **Logging / export** | Never logged, never exported. Enforced by static analysis in CI | FR-ATK-004, NFR-SEC-LOG-I01 |
| **Rotation** | A first-class, audited per-institution operation. Available from day one, invoked at operational discretion | FR-ATK-005 |
| **Migration** | Existing values copied byte-identically | FR-ATK-006 |

### 2.1 Why storage stays plaintext

Hashing is the correct answer for a credential you only ever *verify*. `ATK` is verified by the legacy send runtime, which compares it directly and which this project does not modify (ADR-INST-016). Hashing it here would break every send immediately.

This is recorded plainly rather than presented as a considered position: **plaintext storage of a live credential is a defect we are choosing not to fix in this slice**, because fixing it requires changing a system outside our boundary. It is carried as RESIDUAL-I01 and TM-I005, and FR-ATK-005 exists specifically so that the fix becomes an operational decision later rather than a development project.

### 2.2 Why 160 bits and Base62

The legacy column is `maxlength="32"` in the form. 160 bits Base62-encodes to 27 characters, fitting comfortably while exceeding the 128-bit floor in NFR-SEC-CRED-I01. Base62 avoids URL-encoding and shell-quoting hazards for customers embedding the key in configuration — the same reason the legacy alphabet was alphanumeric, which was the one thing it got right.

## 3. Considered alternatives

### 3.1 Overall posture (the PM decision)

| # | Option | Entropy fixed | Exposure closed | Customer impact | Verdict |
|---|--------|---------------|-----------------|-----------------|---------|
| **A** | **Keep keys, close exposure, CSPRNG for new** | Only for new keys | **Yes** | **None** | **SELECTED** |
| B | Reissue every key, store hashed | Yes | Yes | **Every integration breaks** — needs notice, transition window, and a legacy send-path change | Rejected by PM |
| C | Harden new institutions only | Only for new | **No** | None | Rejected |

Option B is the only one that actually eliminates the weak-entropy problem, and it should be recorded that the security-correct answer was not chosen. The PM's reasoning is sound — an unplanned outage across every client company is a larger, more certain harm than a theoretical key-recovery attack — but the residual is real and is tracked, not dismissed.

Option C was rejected because it leaves the enumeration path (D-I3) open, which is the defect most likely to be exploited: it needs no cryptanalysis, only a loop over 4 characters.

### 3.2 Masking format

| # | Option | Verdict |
|---|--------|---------|
| **A** | **Last 4 characters visible** | **SELECTED** — enough for an operator to confirm which key a customer is quoting, which is the actual support use case |
| B | Fully masked | Rejected — an operator diagnosing "the customer says their key doesn't work" then has no way to compare without invoking reveal every time |
| C | First 4 visible | Rejected — leading characters are marginally more predictable in some encodings |

AMB-I09 is closed by this sub-decision.

## 4. Consequences

**Positive**

- All four exposure paths close: list masked, duplicate check reduced to a boolean, reveal separately gated, logs and exports scrubbed.
- No customer is disrupted; migration is a byte-identical copy.
- New institutions get sound keys immediately.
- Rotation exists, so a future reissue campaign is scheduling, not development.

**Negative**

- **Existing keys retain browser-`Math.random()` entropy.** Closing the exposure paths reduces the chance an attacker obtains one, but does not raise the cost of guessing it. Carried as RESIDUAL-I01 / TM-I005.
- Plaintext at rest means a database read yields live credentials for every institution. `FT_FTIS_INFO` therefore needs the same access-control posture as a credential store, which is not how it is currently treated.
- Two key populations coexist (legacy-generated and CSPRNG-generated) with no way to distinguish them from the value alone, so "how many weak keys remain" cannot be answered by query. A rotation campaign would have to rotate all of them.

**Neutral**

- The legacy client-side generator is deleted, not ported. `btn_generate_code` calls the server.

## 5. Verification / monitoring

| Check | Method | Requirement |
|-------|--------|-------------|
| Keys generated server-side only | Code review — no key generation in frontend bundles | FR-ATK-001 |
| ≥ 128 bits entropy | Unit test over 1,000 generated keys; no repeats, uniform distribution | NFR-SEC-CRED-I01 |
| List responses carry no full key | Integration test asserting payload contents | FR-ATK-002 |
| Duplicate check leaks nothing | Security test — enumerate 100 codes, assert no institution data returned | FR-ATK-004 |
| Reveal is audited | Integration test | FR-ATK-003 |
| No key in logs or exports | gitleaks + log-scan in CI | NFR-SEC-LOG-I01 |
| Migration is byte-identical | Migration verification against a snapshot | FR-ATK-006 |
| Rotation works end to end | Integration test | FR-ATK-005 |

## 6. References

- [REQUIREMENTS-SPEC-INSTITUTION.md](../../requirements/REQUIREMENTS-SPEC-INSTITUTION.md) §2.5, §6.2 RESIDUAL-I01
- Legacy: `biztalk_admin_01.js` lines 47–58 (generator), `biztalk_admin_01_l001_act.jsp` (duplicate check), `WSVC.biztalk_admin_00_l001` out-rule
- [ADR-007](ADR-007-key-management.md) — programme key-management posture this specialises

## Change history

| Date | Version | Change | Author |
|------|---------|--------|--------|
| 2026-08-14 | 1.0 | Initial — option A per AMB-I04; AMB-I09 closed by §3.2 | `architect`, `security-auditor` |

---

**Approval**

| Date | Approver | Comment | Status |
|------|----------|---------|--------|
| 2026-08-14 | PM | Option A — no customer disruption | **ACCEPTED** |
| 2026-08-14 | Architect | Accepted with RESIDUAL-I01 recorded | **ACCEPTED** |
| — | 정보보호 | Plaintext credential storage and retained weak keys warrant a security sign-off, not only a PM one | **RECOMMENDED before G3** |
