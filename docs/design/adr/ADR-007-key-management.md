# ADR-007: Key and secret management

> **Status**: ACCEPTED (with accepted residual risk)
> **Date**: 2026-08-14
> **Author**: `architect` (Skill 03) / reviewed by `security-auditor`
> **Approver**: PM
> **Related**: ADR-005, ADR-008

---

## 1. Context

Two distinct categories of secret exist in this slice:

1. **The PII encryption key** — held inside PostgreSQL and used by the `decrypt()` function. Not owned by this project (ADR-005, CONST-DATA-02)
2. **Application secrets** — database credentials, session signing key, and any provider credentials inherited later

The legacy stored configuration in `ota_config.xml` and property files inside the deployed application. That posture is not acceptable for an internet-facing system under 전자금융감독규정.

- **Requirements**: CONST-DATA-02, NFR-SEC-AUTH, CONST-LEGAL-01
- **Threats**: TM-015 (key custody), TM-016 (shared DB privileges)

## 2. Decision

> **PII key custody remains in the database tier, unchanged.** Application secrets are externalised from the artifact — supplied by environment or a secret manager, never committed to source, never baked into an image. The application uses a **least-privilege database account** distinct from the legacy's.

### Key choices
- No secret in the repository; enforced by gitleaks at the L1 hook (TEST-PLAN §5)
- Database account for the new application is granted only: `SELECT` on the eight message tables, `EXECUTE` on `decrypt()`/`masking()`, and `INSERT` on the audit store. **No `UPDATE`/`DELETE` on business tables** — this makes ADR-002's read-only intent enforceable by the database rather than by convention
- Session signing key rotatable without redeploying application code
- Configuration precedence: environment/secret manager → file → default; secrets never have a checked-in default

## 3. Considered alternatives

| # | Alternative | Advantages | Disadvantages | Adopted |
|---|-------------|-----------|---------------|---------|
| A | DB-tier PII key (as-is) + externalised app secrets + least-privilege account | No migration; immediate improvement over the legacy posture; DB enforces read-only | PII key custody unmodernised (TM-015) | **Adopted** |
| B | Move PII key to a KMS/HSM and decrypt in the application | Modern custody, rotation, and audit of key use | Requires re-encrypting historical data and modifying the legacy, which reads the same tables. Out of scope; would dominate the schedule | Not adopted |
| C | Keep the legacy pattern (secrets in config files) | Zero effort | Fails 전자금융감독규정 and would be a G3 blocker | Not adopted |

## 4. Consequences

### 4.1 Positive
- The least-privilege account converts "the application must not write" from a code convention into a database guarantee
- Secrets leave the artifact, so a leaked build no longer leaks credentials
- Achievable inside this slice's scope, unlike option B

### 4.2 Negative
- **TM-015 remains: a database compromise still exposes all historical PII.** Accepted residual risk, recorded in the threat model §4 for 정보보호 sign-off
- Key rotation for the PII key is outside this project's control
- Operational dependency on whatever secret mechanism the deployment environment provides — not yet chosen

### 4.3 Follow-up
- [ ] Choose the secret mechanism (environment variables vs. a secret manager) with the operations owner — Sprint 1
- [ ] Request creation of the least-privilege DB role and verify `decrypt()` execution rights
- [ ] Document the session-key rotation procedure in the runbook

## 5. Verification

| Item | Method | Frequency | Threshold |
|------|--------|-----------|-----------|
| Secrets in source | gitleaks (L1 hook) | Every commit | 0 findings |
| DB account privileges | Privilege audit query | Sprint end + before release | No write grants on business tables |
| Write attempt blocked | Integration test attempting an UPDATE | Every PR | Rejected by the database |

## 6. References

- Requirements: CONST-DATA-02, NFR-SEC-AUTH · Threats: TM-015, TM-016
- Legacy: `IRIS_ADMIN/src/ota_config.xml`, `*.prop` files

---

## Change history

| Date | Version | Change | Author |
|------|---------|--------|--------|
| 2026-08-14 | 1.0 | Initial | `architect` |

---

**Approval**

| Date | Approver | Comment | Status |
|------|----------|---------|--------|
| 2026-08-14 | PM | | PENDING (G2) — residual TM-015 requires 정보보호 acceptance |
