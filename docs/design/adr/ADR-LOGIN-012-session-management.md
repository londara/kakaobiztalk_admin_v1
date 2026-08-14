# ADR-LOGIN-012: Session management and concurrent-session policy

> **Status**: ACCEPTED
> **Date**: 2026-08-14
> **Author**: `architect` (Skill 03)
> **Approver**: PM
> **Related**: ADR-008 (partially superseded), ADR-006

---

## 1. Context

The legacy maintains sessions in two places: the servlet container's `HttpSession`, and a **database-backed session registry** (`UserSessionDAO`, `UserSessionVO`) recording session id, server name, client IP and user agent. On login it checks for an existing session for the same account and, if found, **force-terminates it** — the newest login wins (FR-LOGIN-016).

The database registry exists because the legacy runs on multiple servers (`SRVR_NO`, `TSTCL_DV` and the server-number suffix shown in the page title), and container sessions are per-instance. Concurrent-session control across instances requires shared state.

- **Requirements**: FR-LOGIN-016/017/023, NFR-SEC-SESSION-L01/L02
- **Threats**: TM-L003, TM-L008, TM-L009

## 2. Decision

> Retain the **shared session registry** and the **newest-login-wins** policy. Session identity lives in an **HttpOnly, Secure, SameSite cookie**; the session id is **regenerated on successful authentication**. Inactivity timeout **30 minutes**.

### Key choices
- Shared session store (database or equivalent), preserving the legacy's cross-instance behavior
- **Session id regenerated at login** — the legacy reuses `request.getSession()` without invalidating the pre-authentication session, leaving a session-fixation opening
- Concurrent sessions: one active session per account; a new login terminates the previous one, and the displaced session receives a clear message rather than a silent failure
- Logout invalidates both the container session and the registry entry (FR-LOGIN-023)
- Session records carry the **trusted** client IP (FR-LOGIN-019), not a client-supplied header

## 3. Considered alternatives

| # | Alternative | Advantages | Disadvantages | Adopted |
|---|-------------|-----------|---------------|---------|
| A | Shared registry + newest-wins (legacy policy) | Preserves established behavior; cross-instance control; a user noticing an unexpected logout is a genuine compromise signal | Shared store is a dependency on the login path | **Adopted** |
| B | Reject the *new* login while a session is active | Prevents an attacker from displacing a legitimate user | An abandoned session locks the real user out until it expires — a support burden, and it hands an attacker a trivial denial-of-service against a known account | Not adopted |
| C | Allow unlimited concurrent sessions | Simplest; no shared state | Loses a control the legacy deliberately had; weakens accountability for administrative actions under 전자금융감독규정 | Not adopted |

> Between A and B the trade is symmetrical — one lets an attacker kick out a user, the other lets an attacker lock one out. A is preferred because the displacement is **visible to the legitimate user**, whereas B's lockout looks like an ordinary system fault.

## 4. Consequences

### 4.1 Positive
- Session fixation closed by regeneration at login — an improvement over the legacy
- One authenticated session per account keeps administrative actions attributable
- Displacement is a detectable compromise signal for the user

### 4.2 Negative
- The shared session store is on the critical path for every login; its availability becomes login's availability
- A user legitimately working from two devices is displaced, which will generate support contacts
- Stale registry entries need reaping when an instance dies without a clean logout

### 4.3 Follow-up
- [ ] Choose the session store — reuse `BIZTALK_DB`, a separate schema, or an in-memory store with persistence
- [ ] Define the reaper for orphaned sessions after an unclean shutdown
- [ ] Confirm the 30-minute timeout (AMB-L05, still open)
- [ ] Give the displaced session an explicit message rather than a generic session-expired error

## 5. Verification / monitoring

| Item | Method | Frequency | Threshold |
|------|--------|-----------|-----------|
| Session id regenerated at login | Security test comparing pre/post ids | Every PR | Always different |
| Cookie flags | Security scan | Every PR | HttpOnly, Secure, SameSite all present |
| Concurrent login displaces the earlier session | Integration test | Every PR | First session invalid |
| Logout clears both container and registry | Integration test | Every PR | No reusable session |
| Inactivity timeout | Integration test | Every sprint | Expires at 30 min |

## 6. References

- Legacy: `apc_login_proc_act.jsp` (`UserSessionDAO.checkDuplicateLogin`, `removeSessionByUserId`, `registerSession`)
- Requirements: FR-LOGIN-016/017/019/023, NFR-SEC-SESSION-L01/L02

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
