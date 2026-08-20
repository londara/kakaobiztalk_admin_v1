# ADR-ATK-024 — Vendor profile key (`sender_key`) management

> **Status**: ACCEPTED
> **Date**: 2026-08-18
> **Slice**: 알림톡 템플릿/발송 (screens 61, 50)
> **Decides**: how FR-ATS-003, FR-AZ-A05 and NFR-SEC-CRED-A01 are satisfied
> **Requirements**: FR-ATS-003, FR-AZ-A05, NFR-SEC-CRED-A01, NFR-SEC-PII-A02, NFR-OPS-A03
> **Related**: [ADR-007](ADR-007-key-management.md), [ADR-008](ADR-008-channel-auth.md), [ADR-005](ADR-005-pii-encryption.md)

---

## Context

`biztalk_admin_50_s001_act.jsp` contains this, twice:

```java
imoIn.put("sender_key", "17da29…（elided — rotate; see RISK-A03）…c2921");
//                       조회하면 가능하지만 우선 임시로 넣어둔다
```

"Could look it up, but putting it in temporarily for now." The comment is dated by the file's 2021 registration. The `sender_key` is the Kakao **발신프로필키** — it authorises sending as the institution, and it is a bearer credential in the sense that matters: possession is authority.

Two consequences make this worse than a hardcoded string usually is.

**It is in the logs.** `util.getLogger().debug("[BIZTALK_50] " + imoIn.toJSONString())` serialises the entire request on every send — the credential and every recipient phone number (D-A30). So the key is in the source repository, in the log store, and in whatever ships logs onward.

**Screen 61 asks the operator for it.** The composer has a `sender_key` text input. An operator composing a payload must obtain the key from somewhere and type it in — meaning the credential circulates among people as a copy-paste string. That is why FR-AZ-A05 says it must never be *selectable*, not merely never hardcoded.

A single hardcoded value also implies something about the data model: either all institutions share one profile key, or the legacy has been sending on behalf of every institution using one institution's key. Source cannot distinguish these, and they have different remediations. No table in the analysed artifacts stores a profile key.

`SecretCipher` exists but is bound to `@Value("${iris.auth.otp.secret-key}")` — it is the OTP secret's cipher, not a general secret store.

## Decision

**`sender_key` is resolved server-side, per institution, from configuration held outside the source tree, and never crosses the API boundary in either direction.**

**1. Removed from the client entirely.** The `sender_key` input disappears from the composer. It is not a field an operator sees, types, or receives in any response. `AlimTalkRequest` — the outbound DTO — carries it; `AlimTalkComposeRequest` — the inbound API DTO — does not. Two types rather than one, precisely so that the credential cannot be supplied from outside by a client that sets a field.

**2. Resolved by institution.** `SenderProfileKeyResolver` maps `is_cd` → profile key, from Spring configuration properties supplied by the environment (`IRIS_ATK_PROFILE_KEY_<IS_CD>`), not from a checked-in file. This deliberately keeps the key out of the database: ADR-005's `ENCRYPT`/`decrypt` place key material *in* the database, and adding a vendor credential to a table that `AOA_ADMIN` also reads (per the 발신번호 slice's finding) would widen its exposure rather than narrow it.

**3. Redaction is enforced by type, not by discipline.** `ProfileKey` is a wrapper whose `toString()` returns `ProfileKey[REDACTED]` and which has no getter returning the raw value except the one the HTTP client calls. A future `log.debug(request)` therefore cannot reproduce D-A30 even if someone writes it — the request's own `toString()` cannot render the key. Recipient numbers are wrapped the same way for NFR-SEC-PII-A02. **The legacy defect was one careless log line; the fix is making that line harmless rather than forbidding it.**

**4. A secret scan runs in CI.** `gitleaks` over the repository, failing the build on a match. **The positive fixture uses a *synthetic* key of the same shape, not the real one** — a Sprint A1 correction: the original wording here said "the known legacy literal", but committing the real compromised credential to the new repository to prove a scan works would reintroduce D-A24 in the very act of testing for it.

**5. The existing key is treated as compromised.** It is committed in cleartext, in a repository, and logged on every send. Rotation is an operational prerequisite, not a development task — §6.5 of the specification, RISK-A03.

## Alternatives considered

| Option | Mechanism | Verdict |
|--------|-----------|---------|
| **A — keep it in application config, in the repository** | `application.yml` | **Rejected.** Moves the literal without changing its exposure; still in version control |
| **B — environment-supplied properties + typed redaction (chosen)** | `IRIS_ATK_PROFILE_KEY_*`, `ProfileKey` wrapper, CI secret scan | **Accepted.** Meets NFR-SEC-CRED-A01 with no new infrastructure, and the redaction is structural |
| **C — store in the database, encrypted per ADR-005** | A new column or table holding the key | Rejected. Key material would sit in a database a second application (`AOA_ADMIN`) can read, and ADR-005's key already lives in that database — so an attacker with DB access gets both. It also makes the credential editable through a data path, which FR-AZ-A05 argues against |
| **D — a managed secret store (Vault / cloud KMS)** | External secret service with lease and rotation | Rejected **for this slice, not on merit.** It is the right long-term answer and ADR-007 anticipates it. Introducing it here would make one integration depend on new programme infrastructure, and option B's interface — a `SenderProfileKeyResolver` with one method — is deliberately the shape a Vault-backed implementation would take. Revisit when rotation frequency justifies it |

## Consequences

**Positive.**
- The credential leaves the source tree, the client, and the log store.
- Typed redaction makes the D-A30 log defect structurally unreachable rather than merely fixed, and the same mechanism covers recipient PII.
- The resolver interface leaves the door open to option D without a caller change.

**Negative.**
- **Per-institution keys require knowing which key belongs to which institution — and nobody does yet.** Source shows one key used for everything. Until this is established (task A1-04), the resolver has a single-entry configuration that behaves exactly like the legacy, with a startup warning. **This ADR does not fix that ambiguity; it makes it visible and configurable instead of invisible and compiled in.** Tracked as RISK-A03.
- Environment-supplied secrets shift the burden to deployment. A missing key is a startup failure rather than a runtime one, which is the correct trade but requires the ops runbook to be updated before the first deploy.
- The composer loses a field operators can see today. Deliberate: it was never information they should have held.

**Out of scope.** Rotation *cadence* and the vendor-side rotation procedure belong to ADR-007 and the operator team. This ADR ensures rotation requires no code change.

## Verification

| Check | Test |
|-------|------|
| No `sender_key` in any client traffic or page source | TC-A001-16 |
| No key literal in the repository | TC-A002-02, CI `gitleaks` stage |
| Key absent from logs at every level | TC-A002-11, TC-A002-02 |
| `ProfileKey.toString()` redacts | New unit test |
| Serialising a request object does not expose the key | New: assert `request.toString()` contains no key material |
| Recipient numbers redacted by the same mechanism | TC-A002-11 |
| Inbound API DTO cannot carry a profile key | New: request with a `sender_key` field is rejected/ignored |
| Missing configuration fails at startup | New |
