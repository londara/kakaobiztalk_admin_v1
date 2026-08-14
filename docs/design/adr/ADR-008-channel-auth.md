# ADR-008: Authentication, authorization and channel security

> **Status**: ACCEPTED — **partially superseded 2026-08-14**
> **Date**: 2026-08-14
> **Author**: `architect` (Skill 03) / reviewed by `security-auditor`
> **Approver**: PM
> **Related**: ADR-001, ADR-005, ADR-007 · **Superseded in part by**: ADR-LOGIN-010 (TOTP), ADR-LOGIN-011 (credential migration), ADR-LOGIN-012 (session)

> **Supersession note.** This ADR set the authentication approach before the login module had been specified. Three areas are now decided in detail elsewhere and those ADRs govern:
> - **TOTP / second factor** → [ADR-LOGIN-010](ADR-LOGIN-010-otp-authentication.md). Google OTP is the only second factor (PM decision AMB-L01)
> - **Password hashing and migration** → [ADR-LOGIN-011](ADR-LOGIN-011-credential-migration.md). This ADR's §4.2 claim that "existing user credentials cannot be migrated" is **too absolute** — upgrade-on-login is viable; ADR-LOGIN-011 carries the decision
> - **Session policy** → [ADR-LOGIN-012](ADR-LOGIN-012-session-management.md)
>
> The rest of this ADR — mandatory authentication with no per-endpoint opt-out, server-derived tenancy, role-based authorization, TLS — remains in force.

---

## 1. Context

This is the highest-stakes ADR in the slice. Three facts converge:

1. The legacy list service ran with **`<login>N</login>`** — anonymous callers could retrieve message history including phone numbers (defect D1)
2. The legacy authenticates with **MD5** password hashing (`weauth/security/md5`), alongside SEED and `JexOAuth2`
3. The system moves from an intranet admin console to an **internet-facing, multi-tenant** portal

Any one of these is serious; together they mean authentication cannot be ported, only replaced.

- **Requirements**: FR-MSG-001, FR-MSGD-001, FR-TEN-001…004, NFR-SEC-AUTH, NFR-SEC-TENANT, NFR-SEC-CHANNEL
- **Threats**: TM-001, TM-002, TM-003, TM-004, TM-014, TM-017
- **Risks**: RISK-005

## 2. Decision

> **Spring Security** with server-side session validation. Authentication is **mandatory for every endpoint with no per-service opt-out**. Tenancy is resolved server-side from the session and injected into every query. MD5 is eliminated and replaced with a modern password hash.

### Key choices
- **No `<login>N</login>` equivalent exists.** Endpoints are authenticated by default; exposing one anonymously would require deliberately removing a filter, not setting a flag
- **Password hashing**: Argon2id (or bcrypt where the runtime constrains) — MD5 removed entirely. Existing credentials must be re-established; they cannot be migrated, since MD5 hashes cannot be upgraded in place without the plaintext
- **Session transport**: HttpOnly + Secure + SameSite cookie, preferred over a browser-stored bearer token to reduce the XSS exfiltration surface introduced by the SPA (TM-017)
- **Tenancy**: `TenantContextFilter` resolves 이용기관 from the authenticated principal. A client-supplied tenant identifier is **ignored and the attempt audited** (TM-004)
- **Authorization**: role-based. Tenant role and operator role; operator-only endpoints (institution list, screens 00/10/20) checked server-side (TM-011, TM-014)
- **Transport**: TLS 1.2+ everywhere, HSTS at the proxy
- **Rate limiting** on login and on the search endpoint (TM-001, TM-012)

## 3. Considered alternatives

| # | Alternative | Advantages | Disadvantages | Adopted |
|---|-------------|-----------|---------------|---------|
| A | Spring Security + server-side session in HttpOnly cookie | Session revocable server-side; token not readable by JavaScript; CSRF handled by framework | Stateful — needs session storage if scaled horizontally | **Adopted** |
| B | Stateless JWT in browser storage | Scales without session storage; conventional for SPAs | Token readable by JavaScript (XSS exfiltration); revocation before expiry is hard; tenancy in a token payload invites TM-004 | Not adopted |
| C | Reuse `JexOAuth2` / legacy weauth | Continuity with existing accounts | Carries the MD5 scheme onto the internet; unmaintained proprietary code; contradicts RISK-005 | Not adopted |

> Option B's weakness is specific to this system: the value being protected is bulk PII, and the client is an SPA (ADR-001). Making the session token unreadable to JavaScript is worth the statefulness at this scale (<10k messages/day).

## 4. Consequences

### 4.1 Positive
- D1 becomes structurally impossible rather than merely fixed
- TM-004 (client-supplied tenancy) is closed at the framework level, not per endpoint
- Removing MD5 clears the most likely single finding at the G3 security gate

### 4.2 Negative
- **Existing user credentials cannot be migrated.** Every user must set a new password at first login, or accounts must be provisioned afresh. This is a user-facing migration event needing communication — and it is not optional
- Server-side sessions require session storage if more than one instance runs
- Account provisioning for tenant users remains unspecified (OI-06, open since Skill 01)

### 4.3 Follow-up
- [ ] **Resolve OI-06** — self-registration or operator-issued accounts? Blocks the login module design
- [ ] Define the credential migration/communication plan with the PM
- [ ] Confirm whether SSO (`sso.jsp`, `JexOAuth2`) must be preserved for operator users
- [ ] Decide session store (in-memory vs shared) once the deployment topology is fixed

## 5. Verification

| Item | Method | Frequency | Threshold |
|------|--------|-----------|-----------|
| Unauthenticated access | Security test on every endpoint | Every PR | 100% rejected |
| Cross-tenant via supplied id | Security test | Every PR | Ignored + audited |
| MD5 absent | Static analysis / dependency scan | Every PR | 0 occurrences |
| Role enforcement | E2E as tenant against operator endpoints | Every Sprint | 100% rejected |
| TLS / HSTS | Security scan | Every release | Pass |

## 6. References

- Legacy: `WSVC.biztalk_admin_40_l001.xml` (`<login>N</login>`), `IRIS_ADMIN/src/weauth/security/md5`, `.../seed`, `JexOAuth2.jar`
- Requirements: FR-MSG-001, FR-TEN-001…004, NFR-SEC-AUTH/TENANT/CHANNEL · Threats: TM-001…004, TM-014, TM-017

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
