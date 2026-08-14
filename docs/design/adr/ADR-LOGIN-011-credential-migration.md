# ADR-LOGIN-011: Password hashing and credential migration

> **Status**: PROPOSED — **requires PM decision at G2**
> **Date**: 2026-08-14
> **Author**: `architect` (Skill 03) / reviewed by `security-auditor`
> **Approver**: PM
> **Related**: ADR-008 (partially superseded), ADR-LOGIN-010

---

## 1. Context

The legacy stores passwords as **unsalted SHA-256** (`JexMessageDigest.getHashString(SHA_256, pwd)` in `apc_login_proc_act.jsp`, defect L2). This is unfit for password storage: no per-user salt, so identical passwords produce identical hashes and rainbow tables apply; and no work factor, so offline guessing runs at hardware speed.

The system is also moving from an intranet console to an internet-facing portal, so the population of potential attackers changes completely.

> **Correction to the requirements spec.** `CONST-SEC-L01` states that existing hashes "cannot be migrated" and that every user must reset their password. That is true only for *offline* migration. It is **not** true in general: at login the plaintext password is in hand, so the system can verify against the legacy hash and immediately re-hash with Argon2id. This ADR exists because that distinction changes the cutover plan materially, and the earlier statement was too absolute.

- **Requirements**: FR-LOGIN-005, FR-PWD-003/005, CONST-SEC-L01
- **Threats**: TM-L002
- **Risks**: RISK-005

## 2. Decision (proposed)

> **Upgrade-on-login**: verify against the legacy unsalted SHA-256 when no Argon2id hash exists; on success, immediately re-hash the plaintext with **Argon2id** and discard the legacy hash. Set a **cutoff date** after which any account still holding only a legacy hash must reset via the operator path.

### Key choices
- Argon2id for all new and migrated credentials
- The legacy verification path is **read-only and one-way** — it can only be used to upgrade, never to store a new SHA-256 hash
- A per-account flag records which scheme is in force
- Cutoff date proposed at **90 days** after cutover, aligning with the existing 90-day password cycle (FR-LOGIN-014) — most active accounts migrate on their own before it
- After the cutoff, the legacy verification path is **deleted from the codebase**, not merely disabled

## 3. Considered alternatives

| # | Alternative | Advantages | Disadvantages | Adopted |
|---|-------------|-----------|---------------|---------|
| A | **Upgrade-on-login with a cutoff** | No user-facing disruption; active users migrate silently; no organisation-wide reset event to schedule and communicate | The legacy SHA-256 verification path exists in the codebase during the window — an auditor will ask about it, and it must be provably one-way | **Proposed** |
| B | **Forced reset for all at cutover** | Cleanest security posture — no legacy hash is ever verified by the new system; the old hashes become irrelevant immediately | A coordinated reset event for every user, with communication, support load, and a hard dependency on reaching people. For external client-company admins this is materially harder than for internal staff | Not proposed |
| C | Dual-scheme indefinitely | No deadline pressure | The legacy path never goes away — this is option A with the safety property removed | Rejected |

### The security argument, stated plainly

Option B is **strictly safer**. If the legacy hash database has already leaked, those passwords should be assumed known, and upgrade-on-login would re-bless a compromised credential. Option A's security therefore rests on an assumption: **that the existing password database has not been exposed.**

That assumption is not free. Defect **L1** places a live Kakao `sender_key` and three personal mobile numbers in application source — evidence that this codebase has not been handled as if it contained secrets. If the source tree has been shared with vendors or sits in an accessible repository, the same handling likely applied elsewhere.

**Recommendation: option A, conditional on a negative finding.** Before adopting it, confirm that the `USER_LDGR` password column has not been exposed in any database extract, backup, or vendor handover. If that cannot be established with confidence, take option B — the disruption is real but bounded, whereas silently migrating compromised credentials is neither.

## 4. Consequences

### 4.1 Positive (option A)
- No organisation-wide reset event; no dependency on reaching every external client admin
- Migration completes for active users within one login each
- The cutoff bounds the window rather than leaving it open

### 4.2 Negative (option A)
- Legacy verification code exists during the migration window and must be provably unreachable for storage
- Dormant accounts (FR-LOGIN-012 already blocks 90-day inactivity) will hit the cutoff — in practice the dormancy rule and the cutoff coincide, which is convenient but should be verified rather than assumed
- An auditor will require evidence of the deletion at cutoff

### 4.3 Follow-up
- [ ] **PM decision at G2: option A or B** — with the exposure check above as input
- [ ] If A: amend `CONST-SEC-L01` in the login spec, which currently mandates a universal reset
- [ ] If A: schedule the cutoff date and the code-deletion task explicitly in a sprint, so it cannot be forgotten
- [ ] Either way: confirm whether the password database has ever been exposed

## 5. Verification / monitoring

| Item | Method | Frequency | Threshold |
|------|--------|-----------|-----------|
| No new SHA-256 hash written | Integration test + static analysis | Every PR | 0 writes |
| Upgrade occurs on first login | Integration test with a legacy-hash fixture | Every PR | Hash replaced with Argon2id |
| Migration progress | Count of accounts on each scheme | Weekly | Trending to zero legacy |
| Legacy path removed at cutoff | Code review | At cutoff | Path absent from the codebase |

## 6. References

- Legacy: `apc_login_proc_act.jsp` (SHA-256 verification), `weauth/security/md5`
- Requirements: FR-LOGIN-005, CONST-SEC-L01 · Threats: TM-L002 · Risks: RISK-005, RISK-L05

---

## Change history

| Date | Version | Change | Author |
|------|---------|--------|--------|
| 2026-08-14 | 1.0 | Initial — corrects the "cannot be migrated" statement in CONST-SEC-L01 | `architect` |

---

**Approval**

| Date | Approver | Comment | Status |
|------|----------|---------|--------|
| 2026-08-14 | PM | Option A or B required before Sprint 1 credential work | **PENDING — decision required** |
