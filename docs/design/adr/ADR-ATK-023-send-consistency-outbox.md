# ADR-ATK-023 — Send consistency: transactional outbox

> **Status**: ACCEPTED
> **Date**: 2026-08-18
> **Slice**: 알림톡 템플릿/발송 (screens 61, 50)
> **Decides**: how FR-ATH-003 and NFR-OPS-A01/A02 are satisfied across a boundary a transaction cannot span
> **Requirements**: FR-ATH-001…003, FR-ATS-002/007/012/013/014, NFR-OPS-A01/A02, NFR-PERF-A03, NFR-SCALE-A01
> **Related**: [ADR-002](ADR-002-transaction-boundary.md), [ADR-009](ADR-009-retry-idempotency.md), [ADR-ATK-026](ADR-ATK-026-tran-id-idempotency.md), [ADR-ATK-025](ADR-ATK-025-http-client-resilience.md)

---

## Context

FR-ATH-003 requires that a history record is never written for a send that did not occur, and never omitted for one that did. NFR-OPS-A01 requires history and despatch to stay consistent under failure. **A database transaction cannot span an HTTP call to a vendor**, so this cannot be satisfied by widening a transaction boundary — the usual answer under ADR-002.

The legacy demonstrates all three ways to get this wrong, in one file:

- `KKB_ADMIN_SEND_HIS` insert and the IMO despatch execute as independent statements with **no transaction and no rollback**, so they can disagree in either direction (D-A27).
- The recipient-validation exception is thrown **after** both succeeded, so the operator is told a send failed that in fact went out (D-A26).
- In the high-volume branch that throw sits *inside* the chunk loop: with 2500 recipients and one malformed number, chunk 1 is delivered, chunks 2–3 never run, and the operator sees a flat error with no record of where it stopped (D-A26).

Two other requirements independently need machinery this slice does not have. FR-ATS-012 requires reserved sends to despatch at `reqdate` — the legacy overwrote it with `now()` (D-A32), so reservation has never worked, and honouring it needs something that runs later. NFR-PERF-A03 requires a capped batch to be *acknowledged* within 5 s while permitting asynchronous despatch. Both point at a scheduler.

There is no HTTP client, no scheduler and no async infrastructure anywhere in `src/main/java` — this is the programme's first outbound integration.

## Decision

**A transactional outbox.** Accepting a send writes intent and history in one local transaction; a dispatcher then performs the vendor call and records the outcome.

```
┌─ ACCEPT (one local transaction, ADR-002) ──────────────────┐
│  validate → dedupe check (ADR-ATK-026)                     │
│  INSERT KKB_ATK_SEND_OUTBOX  (status=PENDING, due_at)      │
│  INSERT KKB_ADMIN_SEND_HIS   (status=ACCEPTED)             │
│  INSERT audit event          (AuditService)                │
└──────────────────────── COMMIT ───────────────────────────┘
                              │  operator sees "accepted", with counts
                              ▼
┌─ DISPATCH (separate transaction per row) ──────────────────┐
│  claim row (SKIP LOCKED, due_at <= now)                    │
│  RestClient → COOCON_ALERT   (ADR-ATK-025)                 │
│  record rsp_code / rsp_message, status=SENT|FAILED         │
└────────────────────────────────────────────────────────────┘
```

**Why the outbox is nearly free here.** Reservation (FR-ATS-012) and asynchronous batch acknowledgement (NFR-PERF-A03) each require a scheduled component regardless of which consistency model is chosen. Once a scheduler exists, `due_at` handles reservation and immediate sends identically — an immediate send is one with `due_at = now()`. The outbox is not extra machinery bolted on for consistency; it is the machinery two other requirements already demanded, used for consistency as well.

**Recipient validation moves ahead of acceptance (D-A26).** Nothing is written until validation has run and the operator has decided about invalid recipients (FR-ATS-007). The legacy's ordering — send, record, then throw — becomes structurally impossible: the throw site is now before the first write.

**Partial batch outcomes are representable (NFR-OPS-A02).** Each `msg_data` item is one outbox row carrying its `order`. Item 2 failing while items 1 and 3 succeed is three rows with two statuses, which is what "partial" means. The legacy could not express this, which is why it reported partial delivery as total failure.

**At-least-once, and therefore idempotency is mandatory.** A crash after the vendor call but before the status update leaves a `PENDING` row that will be retried. Correctness depends on `(is_cd, tran_id)` deduplication holding at the vendor — see ADR-ATK-026 and RISK-A07. This is the price of the design and it is stated plainly: **the alternative to a possible duplicate is a possible silent non-delivery**, and for a financial notification the former is the better failure.

**Multi-instance safety.** Claiming uses `SELECT … FOR UPDATE SKIP LOCKED` rather than application-level leader election, so two application instances cannot claim the same row and no coordination service is introduced. `FAILED` rows retry with backoff up to a bounded attempt count, then move to `DEAD` for operator attention — never retried indefinitely.

**New schema.** ~~`KKB_ATK_SEND_OUTBOX` is a new table, and `KKB_ADMIN_SEND_HIS` needs a status column it does not have.~~ **정정 2026-08-19 — 아래 「수정 1」 참조.** `KKB_ATK_SEND_OUTBOX` 신규 테이블만 필요하며, 공유 테이블에는 손대지 않는다. Additive only, per the CONFLICT-S01 precedent — see Consequences.

---

## 수정 1 (2026-08-19) — 상태 컬럼 요구를 철회한다 / Amendment 1: the status column is withdrawn

> **계기**: PM 질의 — *"why need A2-02 outbox DDL? because I have database connect and existing table."*
> 그 질의를 확인하려고 레거시 스키마를 조사했고, **질의가 옳았다.**
> **Trigger**: a PM challenge, which on investigation was **correct**.
> **근거 / evidence**: [ANALYSIS-A2-02-existing-schema.md](../../../mapping/analysis/ANALYSIS-A2-02-existing-schema.md)

### 무엇이 틀렸나 / what was wrong

이 ADR 은 `KKB_ADMIN_SEND_HIS` 에 상태 컬럼이 필요하다고 적었다. **상태는 이미 존재한다** —
`KKO_MSG_LOG` 에, 그리고 **더 올바른 단위로**. `IDO.KKB_ADMIN_SEND_HIS_L001` 이 그 표를 조인해
`STATUS = '3'` 으로 성공 건수를 세고 있다. 그 표에는 `RSLT`·`MSG_RSLT`·`SENTDATE`·`REPORTDATE` 와
암호화된 `PHONE`·`CALLBACK` 까지 있다.

단위가 결정적이다. `KKB_ADMIN_SEND_HIS` 는 **한 행이 발송 행위 하나**다(`SEND_ID`, `SEND_NM` — 누가
눌렀는가). 여기에 상태를 두면 메시지 수천 건에 상태 하나가 붙는다. `KKO_MSG_LOG` 는 **메시지 한 건에
한 행**이다. 이 ADR 이 §"Partial batch outcomes" 에서 요구한 바로 그 단위이며, 그 요구를 스스로
어길 뻔했다.

The grain is what decides it: `KKB_ADMIN_SEND_HIS` is **one row per send action**, so a status there would
attach one state to thousands of messages. `KKO_MSG_LOG` is **one row per message** — exactly the grain this
ADR demands under "Partial batch outcomes", which the original decision would have violated.

### 무엇이 그대로인가 / what still stands

아웃박스 테이블 자체는 여전히 필요하다. `KKO_MSG_LOG` **에 쓰는 주체가 우리가 아니기 때문이다** —
저장소 전체에 `INSERT INTO KKO_MSG_LOG` 도 `UPDATE KKO_MSG_LOG` 도 없다. 그 표는 게이트웨이가
**접수한 뒤**의 이야기만 하고, 아웃박스가 덮는 구간은 그 **앞**이다: 보내기로 결정한 시점부터 접수가
확정되는 시점까지. 그 구간에서 프로세스가 죽으면 어느 표에도 흔적이 없고, 재시도해도 되는지 알 수 없다.

다만 **범위는 줄어든다.** `STATUS`·`RSLT`·`REPORTDATE` 를 복제할 이유가 없다 — 접수 이후는 게이트웨이가
이미 기록한다. 아웃박스는 접수 여부가 확정될 때까지만 사는 표다.

### 결과 / consequence

**A2-02 는 공유 테이블을 전혀 건드리지 않는다.** 따라서 **RISK-A06 이 소멸한다**. G1 이 판단할 항목에서
"공유 테이블에 컬럼 추가" 가 빠지고, 남는 것은 "새 표 하나" — 발신번호 슬라이스에서 **이미 승인된**
CONFLICT-S01 과 같은 범위다.

### 미확인 / not yet confirmed

레거시 소스만으로 판단했고 데이터베이스에는 접속하지 않았다. `KKO_MSG_LOG` 의 실제 DDL, 게이트웨이가
`SERIALNUM` 을 항상 채우는지(`LEFT OUTER JOIN` 인 점이 아닐 가능성을 시사한다), 우리에게 읽기 권한이
있는지는 확인되지 않았다. **접속이 승인되면 확정한다.** 그 전까지 이 수정은 근거 있는 권고이지
검증된 사실이 아니다.

## Alternatives considered

| Option | Mechanism | Verdict |
|--------|-----------|---------|
| **A — send, then record** | The legacy's ordering | **Rejected.** This *is* D-A26/D-A27. A crash between the two leaves a delivered message with no record, and no way after the fact to tell which happened |
| **B — record PENDING, send inline, update status** | Synchronous; no dispatcher | Rejected. Materially simpler and genuinely tempting, but a crash mid-call still leaves `PENDING` rows needing operator reconciliation — so it does not remove the reconciliation, only the scheduler. And it does not remove the scheduler either, because FR-ATS-012 and NFR-PERF-A03 still need one. **It pays the same operational cost for a weaker guarantee** |
| **C — transactional outbox (chosen)** | Local transaction + polling dispatcher | **Accepted.** The only option under which "no delivery without a record" is true by construction rather than by care |
| **D — two-phase commit / XA across DB and vendor** | Distributed transaction | Rejected. The vendor exposes a plain REST endpoint with no transaction semantics; XA is not available even in principle |
| **E — message broker (Kafka / IBM MQ) as the outbox** | Publish intent, consumer despatches | Rejected for this slice. It solves the same problem but introduces a broker as programme infrastructure for one integration. Reconsider if further outbound channels arrive — the outbox table is a deliberate stepping stone, and the dispatcher interface is written so its source can be swapped |

## Consequences

**Positive.**
- NFR-OPS-A02 becomes structurally true. The operator cannot be told a send failed after it succeeded, because acceptance and despatch are separate observable states.
- FR-ATS-012 works for the first time — `due_at` is honoured rather than overwritten (D-A32).
- Partial batch outcomes are expressible per `order` (D-A26).
- Vendor downtime degrades to delayed delivery with a visible backlog rather than to operator-facing errors.
- The dispatcher is the natural home for the secondary payload assertion from ADR-ATK-021 option D.

**Negative.**
- **At-least-once means a duplicate customer message is possible.** Wholly dependent on vendor-side `tran_id` idempotency, which cannot be confirmed from source — RISK-A07, and task A1-03 exists to establish it. If the vendor does *not* deduplicate, the retry policy must narrow to "retry only on errors that prove the request was never accepted", which is a weaker but honest position.
- **New DDL: one table plus one column.** This re-raises the CONFLICT-S01 question that the 발신번호 slice took to G1. The same narrowing applies — additive only, no existing column altered, nothing an existing reader sees changes — with one difference worth stating: the new column on `KKB_ADMIN_SEND_HIS` is on a **shared** table, whereas ADR-SND-017 needed only a new one. Legacy readers of that table select named columns and are unaffected, but this is a slightly wider precedent than the one G1 was previously asked for. Tracked as RISK-A06.
- **Eventual consistency is operator-visible.** "Accepted" is not "delivered". The UI must say so, and NFR-USE-A04's confirmation step must not imply delivery. An operator who reads "정상 처리" as "the customer has it" is a usability defect in this design.
- A backlog needs monitoring. An outbox that stops draining is silent by nature — the failure mode this design trades for the legacy's.

## Verification

| Check | Test |
|-------|------|
| No delivery without a record | TC-A002-07, and a kill-between-phases integration test |
| No record without an attempt | TC-A002-07 |
| Failure never reported for a successful send | TC-A002-05 |
| Partial batch reported as partial | TC-A003-16, TC-A002-06 |
| Reserved send despatched at `due_at` | TC-A002-13, TC-A003-06 |
| Retry after a dispatcher crash sends once at the vendor | New: crash after call, before status write |
| Two instances do not double-claim | New: concurrent dispatchers over one backlog |
| `FAILED` → `DEAD` after bounded attempts | New |
| Batch acknowledged within 5 s at the cap | NFR-PERF-A03 load test |
| Connections released on every path | TC-A002-08 |

---

## 수정 2 (2026-08-19) — 아웃박스를 유지한다, 그리고 FR-ATS-002 와의 충돌을 남긴다
## Amendment 2: the outbox stays, and its conflict with FR-ATS-002 is left open

> **경위 / history**: PM 이 *"A2-02 I think this no need!"* 로 아웃박스의 필요성을 문제 삼았다.
> 조사한 결과 그 문제 제기에 <b>근거가 있었다</b>(아래 §1). 그럼에도 PM 이
> *"ok you can implement A2-02 now"* 로 유지를 지시했으므로 유지한다. 그 판단은 PM 의 것이고,
> 근거와 남는 위험을 여기 기록한다.
>
> The PM challenged whether the outbox is needed at all. Investigation found the challenge
> <b>well-founded</b> (§1). The PM then directed that it be implemented, so it stays. The decision is the
> PM's; the evidence and the residual risk are recorded here.

### 1. 문제 제기가 옳았던 부분 / where the challenge was right

| | 사실 |
|---|---|
| 요구사항이 아웃박스를 요구하는가 | **아니다.** 요구사항 명세에서 `outbox`·`retry`·`at-least-once`·`재시도`·`durab` 검색 결과 **0건**. 아웃박스는 이 ADR 의 결정이지 요구사항이 아니다 |
| **FR-ATS-002 와 충돌하는가** | **그렇다.** 그 요구는 *"응답이 벤더의 `rsp_code`·`rsp_message` 를 `tran_id` 에 대해 기록하고 **운영자에게 결과를 제시한다**"* — 동기적 읽기다. 아웃박스는 `202 Accepted` 를 돌려주므로 응답에 벤더 결과가 없다 |
| 예약 발송에 필요한가 | **아니다.** `reqdate` 는 <b>계약 필드</b>이므로 예약은 벤더가 수행한다. `DUE_AT` 은 이미 위임한 일을 다시 구현한 것이다 |
| FR-ATS-009 중복 거절에 필요한가 | 저장된 결과가 필요하지만, `KKB_ADMIN_SEND_HIS` + `KKO_MSG_LOG` 가 `SERIALNUM = tran_id` 로 이미 그것을 갖고 있다 |

**원안이 대안 B(동기 발송)를 기각한 논리도 불완전했다.** 기각 사유는 *"크래시 시 PENDING 행이 남아
운영자 조정이 필요하므로 조정을 없애지 못한다"* 였는데, 그것은 B 가 <b>완벽하지 않다</b>는 논증이고
B 가 <b>C 보다 나쁘다</b>는 논증이 아니다. 그리고 FR-ATS-002 를 전혀 언급하지 않았다 — 즉 아무도
요구하지 않은 성질("기록 없는 전달 없음")을 위해 <b>Must 요구사항</b>을 지불했다.

The original rejection of option B argued that B is imperfect, never that B is worse than C, and never
mentioned FR-ATS-002 — paying a **Must** requirement for a property nobody asked for.

### 2. 유지하면서 무엇을 보완했는가 / what was added to compensate

| 보완 | 무엇을 메우는가 |
|---|---|
| `GET /send-status?institution&tranId` | FR-ATS-002 의 "결과 제시" 를 <b>두 번째 요청</b>으로 제공한다 |
| `OutboxMapper.findByTranId` | 위 조회와 FR-ATS-009 중복 판정의 공통 기반 |
| 중복 접수 시 **409 + 원래 결과** | FR-ATS-009. `UNIQUE` 제약만으로는 "원래 결과" 를 알 수 없다 |

`payload` 는 조회 응답에 담지 않는다 — 안에 수신번호와 발신프로필키가 평문으로 있다.

### 3. 남는 것 — PM 결재 필요 / what remains, and needs a PM ruling

**FR-ATS-002 는 여전히 문자 그대로 충족되지 않는다.** 그 요구는 <b>응답이</b> 결과를 담는 것으로
읽히고, 지금은 두 번째 요청이 필요하다. 둘 중 하나를 골라야 한다:

1. **FR-ATS-002 를 개정한다** — "결과는 조회로 제시된다" 로 문구를 바꾼다. 아웃박스를 유지하는 한
   이것이 정합적이다.
2. **아웃박스를 버린다** — 동기 발송으로 돌아가 응답에 `rsp_code` 를 담는다.

**어느 쪽도 고르지 않으면 Must 요구사항이 미충족 상태로 G3 에 도달한다.** 그것이 이 수정의 요점이다.

Either FR-ATS-002 is amended to describe a lookup, or the outbox is dropped for a synchronous send. Left
unchosen, a **Must** requirement reaches G3 unsatisfied.
