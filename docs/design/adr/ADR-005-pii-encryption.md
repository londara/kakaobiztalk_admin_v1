# ADR-005: PII encryption and masking

> **Status**: ACCEPTED
> **Date**: 2026-08-14
> **Author**: `architect` (Skill 03) / reviewed by `security-auditor`
> **Approver**: PM
> **Related**: ADR-003, ADR-007

---

## 1. Context

Recipient and sender phone numbers (`PHONE`, `CALLBACK`) are personal information under 개인정보보호법. In the legacy they are **stored encrypted** and read through two database functions applied in sequence:

```sql
masking(decrypt(PHONE)) AS PHONE          -- detail queries
masking(CALLBACK) AS CALLBACK             -- list query, over an inner decrypt()
```

Encryption, key custody, and masking policy therefore all live **inside PostgreSQL**, not in application code. The new application inherits this arrangement because the schema is reused unchanged (CONST-DATA-01) and the data is already encrypted under the existing key.

- **Requirements**: NFR-SEC-PII, NFR-SEC-PII-02, NFR-SEC-LOG, CONST-DATA-02, CONST-LEGAL-01
- **Threats**: TM-007, TM-010, TM-015

## 2. Decision

> **Retain database-side encryption and masking unchanged.** The application never receives unmasked phone values, never decrypts, and never holds key material. Masking is applied by the database before the value crosses into the application tier.

### Key choices
- All queries select `masking(decrypt(col))` — the application has no code path to an unmasked value
- No application-level crypto is introduced for these columns
- PII must not appear in application logs, exception messages, or exports (NFR-SEC-LOG); the legacy's `logger.debug()` on error payloads is not carried across
- Search on 발신번호 is performed against the decrypted value inside the query (as the legacy does), so the plaintext exists only transiently inside the database engine

## 3. Considered alternatives

| # | Alternative | Advantages | Disadvantages | Adopted |
|---|-------------|-----------|---------------|---------|
| A | Retain DB-side `decrypt()` + `masking()` | Zero migration; application cannot leak what it never holds; consistent with the legacy still reading the same tables | Key custody stays in the DB tier; masking policy is invisible to application code review | **Adopted** |
| B | Move decryption into the application (e.g. AES-256-GCM in Java, KMS-held key) | Key management modernised; policy visible in code; aligns with the harness default | Requires re-encrypting all historical data **and** changing the legacy, which still reads the same tables — far outside this slice's scope | Not adopted |
| C | Decrypt in the app, mask in the app | Fine-grained control over who sees what | Puts unmasked PII in application memory and one bug away from a log line or an API response | Not adopted |

## 4. Consequences

### 4.1 Positive
- The strongest possible guarantee against TM-007: unmasked values are structurally unreachable from application code
- No data migration, no coordination with the legacy system
- Masking behaviour is automatically identical between old and new — a genuine parity win

### 4.2 Negative
- **Key custody remains in the database tier** (TM-015, accepted residual risk). A DB compromise exposes all historical PII
- Masking policy cannot be reviewed in the application repository — it lives in a DB function this project does not own
- If masking rules must change per role (e.g. operators seeing more than tenants), the current design cannot express it without DB changes

### 4.3 Follow-up
- [ ] Obtain and archive the definitions of `decrypt()` and `masking()` — they are project dependencies with no version control here
- [ ] Confirm masking output format with the domain owner (how many digits are revealed)
- [ ] Static-analysis rule in CI: no logging of fields named `PHONE`/`CALLBACK`

## 5. Verification

| Item | Method | Frequency | Threshold |
|------|--------|-----------|-----------|
| Unmasked PII in API responses | Integration test asserting mask pattern | Every PR | 0 occurrences |
| PII in logs | Static analysis + log inspection | Every PR | 0 occurrences |
| Masking parity with legacy | Compare against legacy SQL output format | Sprint 1 | Identical format |

## 6. References

- Legacy: `IDO.KKB_MSG_L002.xml`, `IDO.KKO_SMS_MSG_L001.xml` (masking/decrypt usage)
- Requirements: NFR-SEC-PII, NFR-SEC-PII-02, CONST-DATA-02 · Threats: TM-007, TM-010, TM-015

---

## Change history

| Date | Version | Change | Author |
|------|---------|--------|--------|
| 2026-08-14 | 1.0 | Initial | `architect` |

---

**Approval**

| Date | Approver | Comment | Status |
|------|----------|---------|--------|
| 2026-08-14 | PM | | PENDING (G2) |
