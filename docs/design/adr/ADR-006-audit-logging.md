# ADR-006: Audit logging

> **Status**: ACCEPTED
> **Date**: 2026-08-14
> **Author**: `architect` (Skill 03) / reviewed by `security-auditor`
> **Approver**: PM
> **Related**: ADR-002, ADR-007

---

## 1. Context

Both 문자내역 services carry `mntLogYn=Y` in their WSVC contracts — the Jex runtime wrote an audit record for every invocation. **That behaviour is provided entirely by the runtime being discarded** (proposal RISK-002); nothing in the biztalk source performs it. If it is not rebuilt deliberately, it disappears silently.

The system is subject to 전자금융감독규정 and ISMS-P, and the data being read is PII. Audit evidence is therefore a regulatory obligation, not an operational nicety.

- **Requirements**: NFR-OPS-AUDIT, NFR-OPS-AUDIT-02, BR-005, BR-016, CONST-LEGAL-02
- **Threats**: TM-006 (repudiation), TM-008 (cross-tenant access needs evidence)
- **PM decision (2026-08-14)**: retention term = **5 years** (closes OI-02)

## 2. Decision

> An **`AuditLogAspect`** writes an append-only record for every invocation of an audited service, replacing the Jex `mntLogYn` mechanism. Records are retained **5 years**, integrity-protected, and contain **no PII values**.

### Record contents

| Field | Example | Note |
|-------|---------|------|
| Timestamp | `2026-08-14T09:12:33Z` | Server time, UTC |
| Actor | user id + 이용기관 id | Resolved from session, not from the request |
| Service | `message-history.search` | Maps to the legacy WSVC id |
| Criteria | date range, status, type, **hashed** phone search terms | Values that are themselves PII are hashed, never stored plain |
| Result | row count, page, outcome (OK / DENIED / ERROR) | |
| Correlation id | request id | Ties to application logs |
| Source | client IP, user agent | |

### Key choices
- **Append-only**; no update or delete path exists in the application
- Written in an independent transaction (ADR-002) so a failed query still leaves evidence
- **Denied and cross-tenant attempts are audited**, not just successes — this is the point of the control (TM-008)
- Search criteria containing phone numbers are **hashed** before storage, so the audit trail cannot become a secondary PII store
- Retention 5 years, then disposal per policy

## 3. Considered alternatives

| # | Alternative | Advantages | Disadvantages | Adopted |
|---|-------------|-----------|---------------|---------|
| A | AOP aspect → dedicated append-only audit store | Uniform, hard to bypass; separable retention and access control; survives app-tier tampering better | Extra infrastructure component | **Adopted** |
| B | Application log file with a structured audit marker | No new infrastructure | Logs rotate and are mutable; retention and integrity for 5 years are not credible; mixes PII-free audit with debug output | Not adopted |
| C | Explicit audit calls inside each service method | Fully controlled per call site | Every new endpoint is an opportunity to forget — exactly the failure mode this ADR exists to prevent | Not adopted |

## 4. Consequences

### 4.1 Positive
- Restores a control that would otherwise vanish with the Jex runtime — the concrete mitigation for RISK-002 in this slice
- Cross-tenant attempts are evidenced, giving TM-008 a detective control alongside its preventive one
- Uniform coverage: adding an endpoint does not require remembering to audit it

### 4.2 Negative
- Every request incurs an additional write — negligible at this volume, but it is on the request path
- 5 years of audit data requires a storage and disposal plan, and its own backup policy
- Hashing search terms means an investigator cannot read back which number was searched, only confirm a suspected one — an accepted trade against the audit store becoming a PII repository

### 4.3 Follow-up
- [ ] Select the audit store technology (separate schema vs separate database) — Sprint 1
- [ ] Define the disposal job for records older than 5 years
- [ ] Confirm 5 years satisfies 정보보호 for both 전자금융감독규정 and ISMS-P evidence

## 5. Verification

| Item | Method | Frequency | Threshold |
|------|--------|-----------|-----------|
| Audit record per invocation | Integration test on every endpoint | Every PR | 100% coverage |
| Denied access audited | Security test (cross-tenant attempt) | Every PR | Recorded with outcome DENIED |
| No PII in audit records | Static analysis + sample inspection | Sprint end | 0 plaintext phone values |
| Immutability | Attempt update/delete via app path | Sprint end | Not possible |

## 6. References

- Legacy: `mntLogYn=Y` / `logLv` in `WSVC.biztalk_admin_40_l001.xml`, `WSVC.biztalk_admin_41_l001.xml`
- Requirements: NFR-OPS-AUDIT/-02, BR-005, BR-016 · Threats: TM-006, TM-008
- Decision closing OI-02 (open since Skill 01)

---

## Change history

| Date | Version | Change | Author |
|------|---------|--------|--------|
| 2026-08-14 | 1.0 | Initial — retention fixed at 5 years by PM, closing OI-02 | `architect` |

---

**Approval**

| Date | Approver | Comment | Status |
|------|----------|---------|--------|
| 2026-08-14 | PM | 5-year retention confirmed | **APPROVED** (mechanism pending G2) |
