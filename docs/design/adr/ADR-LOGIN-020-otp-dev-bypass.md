# ADR-LOGIN-020 — Development-only OTP bypass

> **Status**: ACCEPTED
> **Date**: 2026-08-17
> **Slice**: 로그인 (authentication)
> **Requirements affected**: FR-LOGIN-010, NFR-SEC-AUTH-L01, CONST-SEC-01
> **Related**: [ADR-LOGIN-010](ADR-LOGIN-010-otp-authentication.md), [ADR-LOGIN-012](ADR-LOGIN-012-session-management.md)

---

## Context

Requesting a TOTP code on every local login is real friction: a developer restarting the
application dozens of times a day opens an authenticator app dozens of times a day, and any
automated browser test cannot open one at all.

The system had no way to relieve this. OTP verification in `AuthenticationService` was
unconditional, guarded by a dedicated test class (`AuthenticationServiceTest$SingleFactorPrevention`,
19 tests) asserting that no path completes authentication on a password alone. That was the
correct default and remains correct for every deployed environment.

**The PM asked for a local-development bypass.** This ADR records that decision, what was built,
and the cost.

## Decision

A bypass exists, controlled by `iris.auth.otp.dev-bypass-enabled` (default `false`), and it is
constrained by three mechanisms rather than one.

**1. It refuses to start.** Enabled outside the `local` profile, `OtpDevBypass.verifyConfiguration()`
throws and the application does not come up.

This is the design's centre of gravity. The obvious implementation — a flag that defaults to off —
is not sufficient here, and the codebase already contains the reason why: `require-https` defaults
to `true` precisely so that production cannot fail open through omission. By the same standard, an
off-by-default flag is one wrong line in `application-prod.yml` away from putting an
internet-facing portal (CONST-SEC-01) on single-factor authentication — and **nothing would fail,
so nothing would announce it.** Refusing to boot converts that silent vulnerability into a failed
deployment.

**2. It is re-checked on the authentication path.** `isActive()` re-evaluates the profile rather
than trusting that startup validation ran. An instance built by a test wiring, constructed by
hand, or living in a context that does not process `@PostConstruct` is closed here. With a single
interlock, any path that misses it is the vulnerability.

**3. It skips verification only, never enrolment.** The `hasOtpRegistered()` check still applies,
and the surrounding authentication steps — dormancy, membership status, forced password change, IP
allowlist, audit, last-login — all still run. The three OTP steps were extracted into
`verifySecondFactor()` specifically so the bypass could skip exactly those and nothing else.

An earlier draft returned early from the authentication method when the bypass was active. That
would have skipped the account-policy checks and the IP allowlist as well, making local behave
unlike production in ways that **hide** defects rather than surface them — the opposite of what a
development environment is for.

**4. It is loud.** A banner at startup and a `WARN` on every bypassed login, plus an audit record
with outcome detail `otp-bypassed-dev`, so a bypassed session is distinguishable in the audit trail
from a genuinely authenticated one.

## Alternatives considered

| Option | Verdict |
|--------|---------|
| **A — enrol once, use the authenticator app** | Still the recommended path for ordinary development, and it costs nothing. Rejected as the *only* option because it does not serve automated browser tests, which cannot scan a QR code |
| **B — a test-scope helper that computes valid TOTP codes** | Good for automated tests and touches no production code. Retained as a complement rather than a replacement: it does not remove the friction for interactive local development, which is what was asked for |
| **C — flag defaulting to `false`, no profile interlock** | **Rejected.** This is the shape that leaks. See decision point 1 |
| **D — bypass by returning early from `authenticate()`** | **Rejected.** Skips account policy and IP allowlist too; local stops resembling production |
| **E — no bypass** | The prior state. Rejected by the PM |

## Consequences

**Positive.** Local development and automated browser tests can authenticate without a TOTP device.
The interlock makes misconfiguration a startup failure. Bypassed logins are identifiable in the
audit trail after the fact.

**Negative — stated plainly.** This is a deliberate weakening of an authentication control. In any
environment where it is active, **FR-LOGIN-010 and NFR-SEC-AUTH-L01 do not hold.** The guarantee is
not "the bypass is safe"; it is "the bypass cannot be active anywhere except a local developer's
machine, and the application refuses to run if someone tries".

**The residual risk is a `local` profile in a shared environment.** Every protection here keys on
the profile name. A deployment that runs with `local` active — a misconfigured shared dev server,
a container image built with the wrong default — satisfies the interlock while not being a local
machine. If a shared environment ever legitimately needs the `local` profile, this ADR must be
revisited, because at that moment the interlock stops meaning what it says.

**Testing.** `SingleFactorPrevention` is unaffected: `AuthenticationServiceTest` injects an
*inactive* bypass, so those 19 assertions still verify the real rules. Had the bypass been wired in
active, they would have passed while asserting nothing.

## Verification

| Check | Test |
|-------|------|
| Enabled outside `local` fails startup (`prod`/`staging`/`dev`/`test`) | `OtpDevBypassTest.refusesToStartOutsideLocal` |
| Enabled with no active profile fails startup | `OtpDevBypassTest.refusesToStartWithNoProfile` |
| Inactive outside `local` even without startup validation | `OtpDevBypassTest.inactiveOutsideLocalEvenWithoutStartupCheck` |
| Inactive by default | `OtpDevBypassTest.inactiveByDefault` |
| Single-factor still impossible when the bypass is off | `AuthenticationServiceTest$SingleFactorPrevention` (19 tests) |

## Usage

```powershell
$env:SPRING_PROFILES_ACTIVE="local"
$env:IRIS_AUTH_OTP_DEV_BYPASS_ENABLED="true"
$env:IRIS_REQUIRE_HTTPS="false"
mvn spring-boot:run
```

Deliberately not added to `application.yml`: a value that lives only in a developer's environment
cannot be committed by accident.
