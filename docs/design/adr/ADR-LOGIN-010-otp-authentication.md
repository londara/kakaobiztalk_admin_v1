# ADR-LOGIN-010: TOTP implementation for Google OTP authentication

> **Status**: ACCEPTED
> **Date**: 2026-08-14
> **Author**: `architect` (Skill 03) / reviewed by `security-auditor`
> **Approver**: PM
> **Related**: ADR-008 (partially superseded), ADR-LOGIN-011, ADR-LOGIN-012

---

## 1. Context

PM decision AMB-L01 fixes **Google OTP (TOTP) as the only permitted second factor**. The legacy implements it in `GoogleOTP.java` — a hand-written TOTP with two confirmed defects and one latent vulnerability:

| Defect | Detail |
|--------|--------|
| L3 | `int window = 0` — no clock-skew tolerance. Any device drift fails login |
| L4 | `getQRBarcodeURL()` embeds the shared secret in a `http://chart.apis.google.com` URL — plaintext transmission of the secret to a third party over unencrypted HTTP. Currently unused, but present and callable |
| L8 | 80-bit secret (10 bytes) vs RFC 6238's 160-bit recommendation for HMAC-SHA1 |

The algorithm itself (HMAC-SHA1, 30-second step, 6 digits, Base32) is correct and RFC 6238-conformant. The defects are all in the surrounding choices, not the maths — which is exactly the pattern that makes hand-rolled crypto expensive over time.

- **Requirements**: FR-LOGIN-001/009/011, FR-OTP-002/003/004/005, NFR-SEC-AUTH-L02/L03
- **Threats**: TM-L001, TM-L004, TM-L007

## 2. Decision

> Adopt a **maintained TOTP library** rather than porting `GoogleOTP.java`. Secrets are **160-bit**, generated with `SecureRandom`, stored encrypted, and rendered as a QR code **generated server-side locally** with no external service involved. Verification tolerates **±1 time step**.

### Key choices
- Library-based TOTP (RFC 6238), configured: HMAC-SHA1, 30 s step, 6 digits, ±1 window
- HMAC-SHA1 retained — required for Google Authenticator interoperability, and RFC 6238-sanctioned. This is a compatibility constraint, not a weakness in context
- Secret length 160 bits (20 bytes)
- QR code produced locally as an `otpauth://totp/` payload; **no request to any external host at any point**
- The secret is displayed exactly once, at registration, and never returned by any API afterwards
- `getQRBarcodeURL()` is **not ported in any form**

## 3. Considered alternatives

| # | Alternative | Advantages | Disadvantages | Adopted |
|---|-------------|-----------|---------------|---------|
| A | Port `GoogleOTP.java` with the three defects fixed | Familiar; no new dependency; behavior known | Hand-maintained crypto — the same class already shipped a disabled skew window and a secret-leaking URL builder. Nobody owns it; no upstream fixes | Not adopted |
| B | Maintained TOTP library | Audited implementation, upstream maintenance, standard configuration surface, constant-time comparison out of the box | New dependency to track in the SBOM | **Adopted** |
| C | External IdP (Keycloak or similar) with TOTP | Full IAM feature set; offloads credential handling entirely | New infrastructure to deploy, secure and operate — disproportionate for 1–2 developers; migrating the existing `USER_LDGR` account model into it is a project of its own | Not adopted |

### Weighted scoring

| Dimension | Weight | A (port custom) | B (library) | C (external IdP) |
|-----------|--------|-----------------|-------------|------------------|
| 팀 숙련도 | 25% | 8 | 7 | 3 |
| 생태계 / 커뮤니티 | 15% | 3 | 8 | 9 |
| 라이선스 / 비용 | 15% | 10 | 10 | 9 |
| 성능 (vs NFR-PERF-L01) | 15% | 8 | 8 | 7 |
| 보안 (취약점 이력) | 15% | 5 | 9 | 9 |
| 운영 / 모니터링 | 15% | 6 | 8 | 5 |
| **Weighted total** | | 6.80 | **8.20** | 6.60 |

Option B leads the runner-up by 1.40 (17%), outside the <10% tie-break band — **no PM tie-break required**. The decisive dimension is security: option A scores 5 because this specific hand-rolled class has a demonstrated history of exactly the failure mode the score is meant to capture.

## 4. Consequences

### 4.1 Positive
- L3, L4 and L8 are resolved by construction, not by remembering to fix them
- Constant-time code comparison comes from the library rather than being something we must get right
- Upstream maintenance for any future TOTP-related advisory

### 4.2 Negative
- One more dependency in the SBOM and dependency-scanning scope
- Existing 80-bit secrets remain valid for already-registered users — **the secret length improvement applies only to new registrations**. Re-issuing every existing secret would force every user to re-enrol their device. See §4.3
- Library configuration must be verified explicitly; a default of ±3 windows would be too permissive

### 4.3 Follow-up
- [ ] Select the specific library and pin it; record the choice in the SBOM
- [ ] Verify configuration: 6 digits, 30 s, ±1 window, HMAC-SHA1
- [ ] **Decide the fate of existing 80-bit secrets** — leave as-is, or require re-enrolment at a defined date (proposed: leave, and re-issue at the next operator-initiated reset). 80-bit TOTP secrets are weaker than recommended but not practically brute-forceable within a 30-second window; the exposure is offline attack against a stolen database
- [ ] Confirm interoperability against the real Google Authenticator app, not just unit tests

## 5. Verification / monitoring

| Item | Method | Frequency | Threshold |
|------|--------|-----------|-----------|
| ±1 window tolerance | Unit test at step −1, 0, +1, −2, +2 | Every PR | −1/0/+1 accept; ±2 reject |
| No external call during registration | Integration test with outbound traffic capture | Every PR | 0 external requests |
| New secret entropy | Unit test | Every PR | 160 bits |
| Google Authenticator interop | Manual test with the real app | Every sprint | Codes verify |
| Library advisories | Dependency scan | Every PR | 0 High/Critical |

## 6. References

- Legacy: `IRIS_ADMIN/src/com/common/irisadmin/util/GoogleOTP.java`
- Requirements: FR-LOGIN-011, FR-OTP-002/004, NFR-SEC-AUTH-L03
- RFC 6238 (TOTP), RFC 4226 (HOTP)

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
