# ADR-TLK-026: One registry decides both the link and the lookup

> **상태**: ACCEPTED
> **일자**: 2026-08-19
> **작성자**: `architect`
> **결재자**: PM (G2)
> **관련 ADR**: [ADR-TLK-024](ADR-TLK-024-biztalk-api-classification.md), [ADR-TLK-025](ADR-TLK-025-transaction-message-identity.md)
> **관련 요구사항**: FR-TLK-013, FR-TLKD-004, FR-TLKD-005, FR-TLKM-006
> **관련 위험**: RISK-T01
> **관련 미해결 항목**: AMB-T05

---

## 1. 컨텍스트 (Context)

Three separate places in the legacy decide, independently, what kind of thing a transaction is — and they disagree.

**The grid decides whether to offer a link:**
```js
if (datarow.API_SVC_CD.indexOf("KKO") != -1 && datarow.PRSU == 1) { …render link… }
```

**The detail action decides whether it can serve one:**
```java
if (apiSvcCd.equals("ADV_KKO_AT_SEND") || apiSvcCd.equals("ADV_KKO_AT_SEND_M")) { … }
else if (apiSvcCd.equals("ADV_KKO_FT_SEND") || apiSvcCd.equals("ADV_KKO_FT_SEND_M")) { … }
// no else — idoIn1 stays null
```

**The message-detail action decides which table to read:**
```java
if (msgType.equals("AT")) { … } else { … }   // msgType comes from the row
```

The three disagree in both directions and each disagreement is silent.

`ADV_KKO_AT_SEND2` contains `"KKO"`, so the grid links it; the action has no branch for it, leaves the IDO handle null, throws nothing, and returns a result domain with no `REC1`. **The popup opens and renders an empty grid** — the operator concludes the transaction has no messages (D-T13). This is the silent-success shape, now confirmed in six consecutive slices.

In the other direction, a 처리중 (`PRSU=0`) or 오류 (`PRSU=9`) transaction gets no link at all, so the rows an operator investigating a failure most needs are the ones they cannot open.

The third decision inherits a defect from a fourth place: `IDO.KKB_FT_MSG_L001` selects `'AT' AS MSG_TYPE` (copied from the 알림톡 query and never changed), so a 친구톡 row reports itself as 알림톡 and the message-detail action queries `KKO_MSG` instead of `KKF_MSG` (D-T7). **One unchanged literal makes two screens wrong.**

## 2. 결정 (Decision)

> **A single `TalkDetailRegistry` maps `API_SVC_CD` → channel, and every one of the three decisions is derived from it. Serviceability is computed on the server and shipped as a field on the row.**

### 핵심 선택 사항

- **One mapping, four consumers.** The registry answers `channel(apiSvcCd)` → `AT` | `FT` | `none`. The list projection uses it to set `detailAvailable`; the transaction-detail service uses it to pick the channel query; the message-detail service uses it to pick the table family; and `BizTalkApiRegistry` (ADR-TLK-024) is defined as its non-`none` domain — so a code that is classified as BizTalk but has no channel mapping is a **startup error**, not a runtime empty grid.
- **`detailAvailable` is a server-computed field on the list row** (FR-TLK-013). The browser renders a link when the server says the server can serve it. The client is no longer allowed to have an opinion, which is what made the two disagree.
- **Channel comes from the registry, never from the row's own claim.** The 친구톡 query's `'AT'` literal is deleted, but more importantly the message-detail service stops trusting `MSG_TYPE` as routing input at all. The transaction knows its API service; the API service determines the channel; the row's self-description is display data (FR-TLKD-004) and nothing branches on it (FR-TLKM-006). D-T7's second-order effect cannot recur even if the first-order literal comes back.
- **`none` is an explicit, tested outcome.** A transaction whose API service has no mapping returns a 200 with an explicit "상세 조회를 지원하지 않는 거래" body (FR-TLKD-005) — never an empty list. The list does not offer a link for it, so reaching this state requires a crafted request, and the response says so plainly rather than looking like "no messages".
- **처리중 and 오류 transactions get links** (FR-TLK-013). Whether messages exist for them is AMB-T05 and is a data question, not a design one: the detail screen shows what exists and states plainly when nothing does. A row with no messages and a row that cannot be queried are different answers and are rendered differently.

## 3. 검토한 대안 (Considered Alternatives)

| # | 대안 | 장점 | 단점 | 채택 |
|---|------|------|------|------|
| A | **Keep the client rule, fix the server's branch list** | Smallest change | Leaves two rules that must be kept in step by hand. They already drifted once, over `ADV_KKO_AT_SEND2`, and nothing would prevent the next drift | 미채택 |
| B | **Always render the link; let the popup explain** | One rule; no server field | Every unsupported row costs a popup and a round-trip to learn it is unsupported. Also loses the affordance — the grid stops telling the operator anything | 미채택 |
| C | **Server-computed `detailAvailable` from one shared registry (chosen)** | The two decisions **cannot** disagree — they are one decision. Adding a channel is one registry entry. Unmapped-but-classified is caught at startup | One extra boolean per row; the registry needs an owner alongside the ADR-TLK-024 allow-list | **채택** |
| D | **Derive the channel from the message tables at query time** (look for rows in each family) | No mapping to maintain | Two extra queries per row to answer a routing question, and it cannot distinguish "no messages" from "wrong table" — which is the exact ambiguity this ADR exists to remove | 미채택 |

## 4. 결과 (Consequences)

**Two registries, one owner.** `BizTalkApiRegistry` (which codes are in scope) and `TalkDetailRegistry` (what channel each is) are related but not identical: a code can be in scope and have no detail (a status-polling call), but a code with a channel and no scope entry is a configuration error. Startup validates the containment in that direction and fails fast, so the two lists cannot drift into the state that produced D-T13.

**AMB-T05 stops blocking anything.** The question — do 처리중/오류 transactions have messages? — becomes an observation the screen makes rather than a premise the design needs. Both answers render correctly.

**The test that proves this is a set equality, not a case list.** TC-T001-13 asserts that for every row in a representative day, `detailAvailable` is true **if and only if** the detail service returns a non-error response. Case-by-case tests would have passed on the legacy for all four mapped codes and missed `ADV_KKO_AT_SEND2`.

**This is the same structural move as ADR-RPT-023's shared query path**, applied to routing instead of to data: where the legacy had two implementations of one decision, the rebuild has one implementation with two consumers. That is now the programme's standard answer to the copy-and-diverge defect class, and this slice is where the class is most concentrated.

---

## 5. 구현 시점 수정 (Amendment, Sprint T1, 2026-08-19)

**`TalkDetailRegistry` was not built as a separate class. Its behaviour lives in `BizTalkApiRegistry`.**

This ADR's decision — *one mapping, several consumers* — is delivered in full and is if anything more literal than specified: there is now one registry rather than two, so the containment check between them is unnecessary. See [ADR-TLK-024 §5](ADR-TLK-024-biztalk-api-classification.md) for the reasoning.

Every property this ADR argued for holds in the code:

| This ADR required | Delivered as |
|---|---|
| One mapping answering all three decisions | `BizTalkApiRegistry.channelOf(String)` |
| `detailAvailable` computed server-side, shipped on the row | `TalkHistoryService` sets it; `TalkHistoryRow.detailAvailable` carries it |
| Channel from the registry, never from the row's own claim | `TalkHistoryService` passes `apiServiceCode`; nothing reads `MSG_TYPE` |
| `none` as an explicit, tested outcome | `channelOf` returns `Optional.empty()`; `detailAvailable` is false |
| 처리중 / 오류 rows get links | `detailAvailable(String)` takes no status argument — a test asserts its arity, so the coupling cannot return |

**The set-equality test moved earlier than planned.** TC-T001-13 was specified as an E2E assertion over a representative day. `BizTalkApiRegistryTest.LinkMatchesLookup` now pins the same property at the domain level, where it is exhaustive over the registry rather than sampled over a day's data. The E2E form is still wanted in Sprint T2 once the detail endpoint exists — the domain test proves the two decisions are one decision; the E2E test proves the wire carries it.
