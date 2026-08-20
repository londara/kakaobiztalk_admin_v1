# ADR-TLK-028: Offset pagination, not keyset

> **상태**: ACCEPTED
> **일자**: 2026-08-19
> **작성자**: `backend-developer` · **검토**: `architect`
> **결재자**: PM (G2)
> **관련 ADR**: [ADR-RPT-021](ADR-RPT-021-cross-source-aggregation.md), [ADR-TLK-025](ADR-TLK-025-transaction-message-identity.md)
> **관련 요구사항**: FR-TLK-005, FR-TLK-006, NFR-PERF-T01
> **관련 위험**: RISK-T08

---

## 1. 컨텍스트 (Context)

`sprint-T1-tasks.md` task T1-07 specified "keyset pagination", carried over from the 이용기관 보고서 slice where [ADR-RPT-021](ADR-RPT-021-cross-source-aggregation.md) established it. Implementation found the reasoning does not transfer, so this ADR records the change rather than letting the code quietly differ from the plan.

**Why keyset was right there.** The report merges two physical databases. A result set spanning two datasources cannot be cut by `OFFSET` — neither side knows how many rows the other contributed — so keyset seeking, with each source seeking independently on its own index, was the only bounded shape available.

**Why it is wrong here.** Three facts, none of which hold in the report slice:

1. **One datasource.** `FT_APITR_HSTR` is on `BIZTALK_DB` alone. The constraint that forced keyset is absent.
2. **The pager is numbered.** The production screenshot shows `1 2 3` page links. Keyset paging cannot jump to page 3 — it can only advance from a known key. The report slice accepted that loss and recorded it as RISK-R08; accepting it here would remove a control the legacy screen has today, on a screen whose users page through a day's transactions looking for one.
3. **A total count is required anyway.** FR-TLK-005 mandates it, closing D-T11. Once the count query exists, `OFFSET` costs nothing extra in round trips.

There is also a correctness point that cuts the other way and must be answered: offset paging is only safe under a **total order**, and the legacy had none. The production screenshot shows eleven consecutive rows sharing `2026-08-19 11:25:04`, so ties are the ordinary case on this screen and untied offset paging duplicates and drops rows (D-T10, RISK-T08).

## 2. 결정 (Decision)

> **Offset pagination over the total order `(RGDT DESC, IS_TUNO DESC)`, with the offset computed in Java and the total count from a separate statement sharing the same predicate fragment.**

### 핵심 선택 사항

- **The total order is the precondition, not a nicety.** `IS_TUNO` is unique within a transaction day, so the pair is total. FR-TLK-006 is therefore load-bearing for pagination correctness, exactly as FR-RPT-006 became in the report slice — and TC-T001-06 tests it as a property over a 100-row single-timestamp fixture, not as a sample.
- **The offset is computed in Java** (`TalkHistoryCriteria.offset()`), not as `OFFSET #{page} * #{size}` in SQL. Pushing arithmetic into the statement makes it depend on how the driver and database handle multiplication of bind parameters — the same class of implicit contract that made the legacy's `RGDT BETWEEN '…999999'` work only because the column happened to be character-typed (D-T24).
- **The page and the count share one `<sql>` fragment.** A condition present in one and absent from the other breaks the pager silently, which is a variant of the failure mode this programme has met in six consecutive slices.
- **Deep-offset cost is bounded by the period cap.** The 31-day cap (FR-TLK-007) bounds the result set, so the worst-case offset is bounded too. NFR-PERF-T01's load test measures the last page of a full 31-day range, which is the expensive case; if it fails the target, the mitigation is a keyset "next page" path *alongside* the numbered pager, not instead of it.

## 3. 검토한 대안 (Considered Alternatives)

| # | 대안 | 번호 페이저 | 깊은 오프셋 비용 | Verdict |
|---|------|------------|-----------------|---------|
| A | **Keyset, as T1-07 specified** | 불가 / no | 없음 / none | **미채택.** Removes a control the legacy screen has, to solve a cross-datasource problem this slice does not have |
| B | **Offset over a total order (chosen)** | 가능 / yes | 기간 상한으로 유계 / bounded by the period cap | **채택** |
| C | **Offset over the legacy's single-column order** | 가능 / yes | 유계 / bounded | **미채택.** This is D-T10. Ties are the normal case here, so rows would duplicate and vanish — the status quo, and the defect |
| D | **Keyset for next/prev plus a count for the numbered pager** | 부분적 / partial | 없음 / none | **미채택 for now, recorded as the successor.** The right answer if NFR-PERF-T01 fails at the 31-day cap. Building it pre-emptively adds two code paths to serve a performance problem not yet observed |

## 4. 결과 (Consequences)

**T1-07's task text is superseded by this ADR.** The sprint log records the deviation; the requirement (FR-TLK-005) is unchanged, because it specifies a page plus a total count and says nothing about the mechanism.

**RISK-T08 is unchanged in substance and sharper in consequence.** Under keyset a missing tiebreaker degrades gracefully; under offset it duplicates and drops rows. The mitigation is the same property test, and it is now guarding pagination correctness rather than display tidiness.

**Option D is the named successor.** If the load test shows the last page of a 31-day range missing NFR-PERF-T01, keyset next/prev is added beside the numbered pager. That is a strictly additive change: the order, the count query and the criteria type all stay.

**The report slice is not revisited.** ADR-RPT-021's keyset merge remains correct for its own constraints. Two slices reaching different pagination answers from different constraints is the intended outcome of recording constraints rather than copying mechanisms — which is the same lesson [ADR-TLK-027](ADR-TLK-027-sibling-reuse-boundary.md) drew about the message tables.
