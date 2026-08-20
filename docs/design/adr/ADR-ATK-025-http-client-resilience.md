# ADR-ATK-025 — Outbound HTTP client and resilience policy

> **Status**: ACCEPTED
> **Date**: 2026-08-18
> **Slice**: 알림톡 템플릿/발송 (screens 61, 50)
> **Decides**: the stack addition for the programme's first outbound integration; retry, timeout and circuit-breaker policy
> **Requirements**: FR-ATS-001/002, NFR-PERF-A02/A03, NFR-SEC-CHANNEL-A01, NFR-OPS-A01/A03
> **Related**: [ADR-001](ADR-001-tech-stack.md), [ADR-008](ADR-008-channel-auth.md), [ADR-009](ADR-009-retry-idempotency.md), [ADR-ATK-023](ADR-ATK-023-send-consistency-outbox.md)
> **Extends ADR-001** — this is the first slice to add a technology to the programme stack

---

## Context

Every prior slice recorded "no external channel added" under ADR-008. This one adds the first: `IMO.ADV_KKO_AT_SEND2` targets `COOCON_ALERT` at `/advising/kakao/at_send`. The evidence that this is genuinely new is in the source tree — `grep -rE "RestTemplate|WebClient|HttpClient|RestClient|Feign" src/main/java` returns nothing, and `pom.xml` carries no resilience library, no scheduler and no HTTP client beyond what `spring-boot-starter-web` provides for *inbound* traffic.

So ADR-001, settled for the programme and not reopened by three slices, genuinely reopens here — narrowly. The stack question is not "which framework"; it is "which client and which resilience mechanism", and it is a real ≥2-candidate decision under harness §2.

The requirements constrain it more than the volume does. `ADR-ATK-023` makes despatch asynchronous inside a polling dispatcher, so the call site is a background thread with no user waiting — this removes the usual argument for reactive clients. But at-least-once despatch makes **retry semantics safety-critical**: a retry after a request the vendor actually accepted produces a duplicate customer notification unless `tran_id` deduplication holds (RISK-A07). Retry policy is therefore not a resilience nicety here; it is the control that decides whether a customer gets one message or two.

## Decision

**Spring `RestClient` (already present in `spring-boot-starter-web`) plus `resilience4j-spring-boot3` as the single new dependency.**

**Client.** One `RestClient` bean per external target, configured with explicit connect and read timeouts, TLS trust from the platform store, and the `COOCON_ALERT` host taken from configuration so the endpoint is allowlistable (NFR-SEC-CHANNEL-A01). `RestClient` is synchronous, which matches the dispatcher's thread-per-row model and the blocking MyBatis code around it.

**Resilience policy — deliberately conservative on retry:**

| Concern | Policy | Reasoning |
|---------|--------|-----------|
| Connect timeout | 3 s | A vendor that has not accepted a connection has not received the request |
| Read timeout | 10 s | Must exceed the vendor's own processing; a read timeout is the **ambiguous** case — the request may have been accepted |
| Retry — **connect** failures | Up to 3, exponential backoff + jitter | Safe: the request provably never arrived |
| Retry — **read timeouts and 5xx** | **Not retried inline.** The row stays `PENDING` for the dispatcher's next pass | Unsafe to retry blindly: the vendor may have accepted it. Deferring gives A1-03's idempotency finding somewhere to land without a code change |
| Retry — 4xx | Never | A rejected request is rejected; retrying is noise |
| Circuit breaker | Open on 50 % failure over a 20-call window; half-open probe after 60 s | Prevents a vendor outage from consuming the dispatcher pool. Rows accumulate as backlog, which is the intended degradation under ADR-ATK-023 |
| Bulkhead | Bounded concurrent calls | One vendor cannot exhaust application threads |

The retry split is the substance of this ADR. The naive configuration — "retry on any failure, 3 attempts" — is what most resilience4j examples show and would be **wrong here**, because the read-timeout case is exactly the one where the vendor may already have sent the message. Distinguishing *provably-not-delivered* from *possibly-delivered* is the whole design.

**Observability.** resilience4j publishes retry, circuit-breaker and bulkhead metrics through Micrometer to the existing actuator surface, giving the outbox-backlog monitoring that ADR-ATK-023 identifies as its main operational risk. Log level is `INFO` for state transitions and `WARN` for breaker-open — never `ERROR` for routine outcomes, per NFR-OPS-A03 and D-A30.

**Testing without Docker.** `MockRestServiceServer` (in `spring-boot-starter-test`) covers request-shape assertions in-process; timeout, 5xx and connection-failure behaviour are driven through it and through a stub server on a local port. **No container is required** (RISK-S13), so unlike the 발신번호 slice's DB-function problem, this slice's external boundary is fully testable.

## Alternatives considered — weighted comparison (harness §2)

| Dimension | Weight | **A — RestClient + resilience4j** | **B — JDK HttpClient, hand-rolled** | **C — WebClient / WebFlux** |
|-----------|-------:|----------------------------------:|------------------------------------:|----------------------------:|
| 팀 숙련도 | 25 % | 90 | 70 | 45 |
| 생태계 / 커뮤니티 | 15 % | 90 | 60 | 85 |
| 라이선스 / 비용 | 15 % | 95 | 100 | 95 |
| 성능 (NFR 대비) | 15 % | 80 | 80 | 90 |
| 보안 (취약점 이력) | 15 % | 85 | 65 | 85 |
| 운영 / 모니터링 | 15 % | 90 | 50 | 80 |
| **Weighted total** | | **88.5** | **70.75** | **76.5** |

**Runner-up gap: 88.5 → 76.5 = 12.0 points, 13.6 % relative.** Above the 10 % threshold, so **no PM tie-break approval is required** under harness §2. (PM was consulted on this decision regardless and selected option A.)

**Notes on the scores that decide it:**

- **B scores 100 on licence** — `java.net.http` is in the JDK, zero new dependencies, which is genuinely attractive in a regulated codebase. It loses on 보안 (65) and 운영 (50) for one reason: the retry/timeout/circuit-breaker logic above would be **our** code. Given that the retry split is the safety-critical decision in this ADR, hand-rolling it puts the duplicate-notification risk in bespoke, lightly-exercised code. That is the wrong place for it.
- **C scores highest on performance** (90) and it is not close on 팀 숙련도 (45). The codebase is blocking end to end — MyBatis, `spring-boot-starter-web` MVC, `AuditService`. Adding `webflux` introduces a second concurrency paradigm for one integration whose call site is already a background thread that nobody is waiting on. The performance advantage buys nothing the requirements ask for.
- **A wins on 팀 숙련도 and 운영** — `RestClient` needs no new dependency at all, and resilience4j's Micrometer integration supplies the backlog observability ADR-ATK-023 needs.

Also considered and rejected without scoring: **Feign / OpenFeign** (declarative, but the vendor has no OpenAPI definition and the `RSMS` envelope from ADR-ATK-021 is not a shape declarative binding helps with) and **Spring Retry** instead of resilience4j (retry only — no circuit breaker or bulkhead, both of which ADR-ATK-023's backlog model relies on).

## Consequences

**Positive.**
- One new dependency, Apache-2.0, widely deployed, with a maintained CVE process.
- The retry policy encodes the delivered/not-delivered distinction explicitly, so A1-03's finding about vendor idempotency changes configuration rather than code.
- Circuit-breaker and backlog metrics land on the existing actuator surface.
- Fully testable in-process; no Docker dependency.

**Negative.**
- **ADR-001's "no new technology" posture for the programme ends here.** It is a small addition, but it is a precedent, and G2 should record it as one rather than let it pass as an implementation detail.
- Deferring read-timeout retries to the next dispatcher pass means a vendor read timeout adds latency equal to the poll interval. Acceptable — reservation already makes delivery time a scheduled property, not an immediate one.
- resilience4j configuration is annotation- and property-driven, so a misconfiguration is silent (the annotation simply does nothing if the instance name does not match). Mitigated by a startup assertion that every named instance resolves, and by tests that assert breaker-open behaviour rather than trusting configuration.

## Verification

| Check | Test |
|-------|------|
| Request shape matches the contract | `ContractConformanceTest`, `RsmsEnvelopeTest` (ADR-ATK-021) |
| Connect failure retries and eventually defers | New: stub refuses connections |
| **Read timeout is not retried inline** | New: stub delays past the read timeout; assert exactly one call reaches the stub |
| 4xx is not retried | New |
| Breaker opens under sustained failure | New |
| Breaker half-opens and recovers | New |
| Bulkhead bounds concurrency | New |
| Metrics exposed on actuator | New |
| No credential or recipient number in client logs | TC-A002-11 (ADR-ATK-024) |
| TLS enforced, host from configuration | Configuration review, NFR-SEC-CHANNEL-A01 |
| End-to-end latency budget | NFR-PERF-A02/A03 load test |
