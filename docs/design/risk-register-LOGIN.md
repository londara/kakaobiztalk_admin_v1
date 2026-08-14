# Risk Register — 로그인 (Authentication module)

> **Version**: 1.0
> **Date**: 2026-08-14
> **Owner**: PM · **Maintained by**: `architect`
> **Companion**: [risk-register.md](risk-register.md) (문자내역 slice)

---

## Summary

| Severity | Count |
|----------|-------|
| High impact | 7 |
| Medium impact | 4 |
| Low impact | 1 |
| **Total** | **12** |

---

### RISK-L01 — Password database exposure history unknown
- **영역**: 보안 · **영향**: H · **확률**: M · **전략**: 회피
- **대응 계획**: ADR-LOGIN-011's upgrade-on-login is only safe if the legacy hash database has never been exposed. Determine whether the `USER_LDGR` password column has appeared in any backup, extract, or vendor handover. If exposure cannot be ruled out, **ADR-LOGIN-011 option B (forced reset for all users) becomes mandatory**
- **담당자**: PM + 정보보호 · **모니터링**: **before Sprint L1 credential work**
- **비고**: **Blocks ADR-LOGIN-011.** Not an engineering question — it needs a factual answer about past data handling. Defect L1 (a live secret in source) is evidence that this codebase was not treated as containing secrets, which lowers confidence in a negative finding

### RISK-L02 — Live secret and personal data in application source
- **영역**: 보안 · **영향**: H · **확률**: H · **전략**: 회피
- **대응 계획**: `apc_login_proc_act.jsp` contains a Kakao `sender_key`, a sender number, and **three personal mobile numbers**. Treat the key as disclosed and rotate it **now**, independently of this project. Move recipients to configuration (FR-LOGIN-021); enable gitleaks (L1 hook)
- **담당자**: PM + 정보보호 · **모니터링**: immediate, then every commit
- **비고**: Present in the current running system, not just the migration target. If the source tree has ever left the organisation, the three individuals' numbers are disclosed too — a 개인정보보호법 matter, not only a secrets matter

### RISK-L03 — Disabled-controls pattern
- **영역**: 기술/보안 · **영향**: H · **확률**: H · **전략**: 완화
- **대응 계획**: Three controls exist but are commented out (strength check, IP allowlist, OTP skew). TEST-PLAN-LOGIN §4 requires 24 negative-path tests, each proving a **denial**; the 보안 dimension fails outright if any is skipped
- **담당자**: `security-auditor` · **모니터링**: every PR
- **비고**: The generalisable lesson: for the remaining screens, commented-out code is a finding to investigate, not noise. Reading call sites alone would have shown a system with password strength checking and IP restriction

### RISK-L04 — Rollback is partially one-way
- **영역**: 운영 · **영향**: H · **확률**: M · **전략**: 완화
- **대응 계획**: Once a password is upgraded to Argon2id, the legacy system cannot verify it — those users cannot log in to the legacy after a rollback. Mitigation: **retain the legacy hash for the rollback window** rather than discarding it at upgrade, then purge on a scheduled date
- **담당자**: `architect` + ops · **모니터링**: before cutover
- **비고**: Newly identified in Skill 03. Contradicts the "rollback = route back to legacy" assumption inherited from the 문자내역 plan, where no data changed. Retaining the old hash is itself a security cost — it is the very thing RISK-L01 worries about — so the window must be short and dated

### RISK-L05 — Existing 80-bit OTP secrets remain in use
- **영역**: 보안 · **영향**: M · **확률**: L · **전략**: 수용
- **대응 계획**: New secrets are 160-bit (FR-OTP-002); already-enrolled users keep their 80-bit secrets, since re-issuing would force every user to re-enrol. Proposal: re-issue at the next operator-initiated reset
- **담당자**: `security-auditor` · **모니터링**: annual review
- **비고**: TM-L005, accepted residual. 80 bits is not brute-forceable within a 30-second window; the exposure is offline attack against a stolen database

### RISK-L06 — Operator OTP reset is a sanctioned second-factor bypass
- **영역**: 보안 · **영향**: H · **확률**: M · **전략**: 완화
- **대응 계획**: Operator role required, every reset audited with actor/target/reason, user notified via a different channel, self-reset prohibited (AMB-L08)
- **담당자**: PM + `security-auditor` · **모니터링**: audit review, monthly
- **비고**: TM-L008. Its real strength is the out-of-band identity check, which is a **procedure outside the software**. If that procedure is weak, this is the cheapest route to taking over any account, including an operator's. It needs to be written down and owned

### RISK-L07 — Argon2id cost as a DoS vector
- **영역**: 기술/보안 · **영향**: M · **확률**: M · **전략**: 완화
- **대응 계획**: Rate limiting must sit **in front of** password hashing, not after. Work factor tuned against measured capacity; load scenario TM-L016 verifies the ordering
- **담당자**: `backend-developer` · **모니터링**: Sprint L2 load test
- **비고**: Introduced by the fix for L2 — a stronger hash is a larger per-request cost on an unauthenticated endpoint

### RISK-L08 — AMB-L03 unresolved (IP allowlist scope)
- **영역**: 규제/기술 · **영향**: M · **확률**: H · **전략**: 완화
- **대응 계획**: Design assumes **operators only**, since external client companies lack stable addresses. Confirm at G1/G2
- **담당자**: PM · **모니터링**: G2
- **비고**: Interacts with RISK-006 (망분리) — IP-restricting operators while leaving tenants open is effectively a network-tier split, which shapes the deployment topology

### RISK-L09 — Credential migration schedule slips past the cutoff
- **영역**: 일정 · **영향**: M · **확률**: M · **전략**: 완화
- **대응 계획**: ADR-LOGIN-011 sets a 90-day cutoff after which unmigrated accounts must reset via the operator path. Both the cutoff date **and** the legacy-code deletion must be explicit sprint tasks
- **담당자**: PM · **모니터링**: weekly migration count
- **비고**: The 90-day dormancy rule (FR-LOGIN-012) and the cutoff coincide conveniently — verify rather than assume that the overlap actually clears every account

### RISK-L10 — TOTP interoperability failure
- **영역**: 기술 · **영향**: H · **확률**: L · **전략**: 완화
- **대응 계획**: Manual verification against the real Google Authenticator app every sprint, not unit tests alone
- **담당자**: `qa-engineer` · **모니터링**: every sprint
- **비고**: A wrong secret encoding or time step passes internal tests and fails for every real user simultaneously — a total-outage failure mode with no partial signal

### RISK-L11 — Session registry availability becomes login availability
- **영역**: 운영 · **영향**: M · **확률**: L · **전략**: 수용
- **대응 계획**: The shared registry (ADR-LOGIN-012) sits on the login critical path. Monitor its availability; define behaviour when it is unreachable — **fail closed**
- **담당자**: `architect` + ops · **모니터링**: post-deployment
- **비고**: Inherited from legacy design, which had the same property

### RISK-L12 — G1 not approved for this module
- **영역**: 프로세스 · **영향**: L · **확률**: H · **전략**: 수용
- **대응 계획**: This design was produced against a DRAFT specification at PM instruction. If G1 review changes requirements, ADR-LOGIN-010/011/012 and both plans need revision
- **담당자**: PM · **모니터링**: G1 review
- **비고**: Six ambiguities remain open (AMB-L03…L08); five carry working assumptions, AMB-L03 does not

---

## Open decisions blocking risk closure

| ID | Decision | Blocks | Owner |
|----|----------|--------|-------|
| **ADR-LOGIN-011** | Upgrade-on-login (A) or forced reset (B) | RISK-L01, RISK-L04, Sprint L1 | PM + 정보보호 |
| **TM-L002** | Has the password database ever been exposed? | ADR-LOGIN-011 | PM + 정보보호 |
| **TM-L015** | Rotate the hardcoded `sender_key` | RISK-L02 | PM + 정보보호 |
| AMB-L03 | IP allowlist scope | RISK-L08, topology | PM |
| AMB-L04/L05 | Password history depth, session timeout | Minor | PM |
| AMB-L06 | Do dormancy and password cycle apply to tenants? | FR-LOGIN-012/014 | Domain owner |
| AMB-L07 | Retain the admin-login notification? | FR-LOGIN-021 | PM |
| AMB-L08 | May an operator reset their own OTP? | RISK-L06 | PM |
