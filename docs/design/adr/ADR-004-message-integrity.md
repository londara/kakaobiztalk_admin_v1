# ADR-004: Message integrity

> **Status**: ACCEPTED (scoped — see §7)
> **Date**: 2026-08-14
> **Author**: `architect` (Skill 03)
> **Approver**: PM
> **Related**: ADR-006, ADR-008

---

## 1. Context

Harness §8 requires a message-integrity ADR for financial-domain systems. In the classic sense — signing or HMAC-protecting transaction messages exchanged with an external party — **this slice exchanges no such messages**. 문자내역 reads existing records; it neither sends messages to the provider nor receives delivery callbacks.

What *does* require integrity protection here:
1. The client ↔ API channel carrying PII
2. Session tokens that assert identity and tenancy
3. Audit records asserting who read what

- **Requirements**: NFR-SEC-CHANNEL, NFR-SEC-AUTH, NFR-OPS-AUDIT
- **Threats**: TM-003, TM-006, TM-017

## 2. Decision

> Integrity in this slice is provided by **TLS for the channel**, **signed and server-validated session tokens** for identity, and **integrity-protected append-only audit records**. No application-level message signing (HMAC) is introduced, because there is no message exchange to protect.

### Key choices
- TLS 1.2+ on every external hop; no plaintext fallback (NFR-SEC-CHANNEL)
- Session tokens integrity-protected and validated server-side; tenancy is **never** trusted from the token payload alone but re-resolved server-side (ADR-008, TM-004)
- Audit records integrity-protected at rest (ADR-006)
- The legacy `<hash>` element present in each WSVC XML is a **Jex tooling artifact** (a configuration checksum), not a runtime message signature. It has no counterpart in the new design and is deliberately not reproduced

## 3. Considered alternatives

| # | Alternative | Advantages | Disadvantages | Adopted |
|---|-------------|-----------|---------------|---------|
| A | TLS + signed sessions + audit integrity | Matches the actual threat surface of a read-only API | No protection against a compromised application tier — accepted, out of scope | **Adopted** |
| B | Add HMAC over API request/response bodies | Defence in depth if TLS terminates early | Solves no identified threat here; adds key distribution to browser clients, which is impractical | Not adopted |
| C | Reproduce the WSVC `<hash>` mechanism | Literal parity | It is a build-time config checksum for Jex tooling, not a security control — reproducing it would be cargo-culting | Not adopted |

## 4. Consequences

### 4.1 Positive
- Effort is spent on the boundaries that actually carry risk in a read-only slice
- Avoids a browser-side HMAC key distribution problem that would weaken, not strengthen, the design

### 4.2 Negative
- Integrity of data **at rest in the shared database** is not addressed here — the legacy retains write access to the same tables (TM-016, residual risk accepted)

### 4.3 Follow-up
- [ ] Verify TLS configuration and cipher policy at the reverse proxy before G3

## 5. Verification

| Item | Method | Frequency | Threshold |
|------|--------|-----------|-----------|
| TLS enforced, no plaintext | Security scan | Every release | 0 plaintext endpoints |
| Session token tampering rejected | Security test (altered token) | Every PR | 100% rejected |

## 6. References

- Requirements: NFR-SEC-CHANNEL, NFR-SEC-AUTH · Threats: TM-003, TM-006, TM-017
- Legacy: `<hash>` element in every `WSVC.*.xml`

## 7. Scope limitation

**This ADR does not cover the send path.** Dispatching to the Kakao BizTalk provider and receiving delivery-result callbacks are genuine external message exchanges requiring provider authentication, callback verification, and possibly signature validation. That work must **supersede this ADR**, not append to it.

---

## Change history

| Date | Version | Change | Author |
|------|---------|--------|--------|
| 2026-08-14 | 1.0 | Initial — read-only slice; no external message exchange | `architect` |

---

**Approval**

| Date | Approver | Comment | Status |
|------|----------|---------|--------|
| 2026-08-14 | PM | | PENDING (G2) |
