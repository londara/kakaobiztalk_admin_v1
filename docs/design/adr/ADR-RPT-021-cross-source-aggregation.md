# ADR-RPT-021 — Cross-source aggregation and pagination

> **Status**: ACCEPTED
> **Date**: 2026-08-18
> **Slice**: 이용기관 보고서 (screen 20)
> **Decides**: CONFLICT-R02; answers AMB-R04 provisionally
> **Requirements**: FR-RPT-005, FR-RPT-006, FR-RPT-011, FR-RPTS-001, FR-RPTS-003, FR-RPTS-005, NFR-PERF-R01, NFR-PERF-R02, CONST-DATA-R02
> **Supersedes**: nothing. **Related**: [ADR-003](ADR-003-persistence-strategy.md), [ADR-002](ADR-002-transaction-boundary.md), [ADR-RPT-023](ADR-RPT-023-export-generation.md)

---

## Context

Skill 2 raised CONFLICT-R02: three requirements appear mutually unsatisfiable.

- **FR-RPTS-003** — with 발송구분=전체, counters for the same 일자 + 기관 are summed across `BIZTALK_DB` (API 발송) and `BIZTALK_BULK_DB` (대량발송) into a single row.
- **FR-RPT-005** — pagination is server-side.
- **FR-RPT-006** — ordering is total and deterministic across the merged set.

Neither database can produce a merged, ordered, paginated result on its own. Skill 2 recorded three candidate shapes and a working assumption of the weakest one, gated on AMB-R04: are the two datasource aliases one physical database or two?

### AMB-R04 — what the source says

**SEC-002 applies to the authoritative answer.** `jex.iris_admin.xml` carries the datasource definitions and is declared under `JEX.config.file` in `jex.prop`, so it is not read here and the credentials it holds stay closed. The question is therefore answered by inference from source, and the inference is recorded as provisional.

Three observations, all from artifacts already in scope:

1. Exactly **four** IDOs target `BIZTALK_BULK_DB` — `BULK_KKB_APITR_SMTN_L001`/`_L002` and the batch's `KKB_APITR_SMTN_C011`/`_D011`. Sixty target `BIZTALK_DB`. The bulk datasource is a narrow, single-table surface.
2. **No bulk-targeted IDO references `FT_FTIS_INFO`.** The institution master is never queried through that alias.
3. The decisive one: the two aggregate queries resolve 기관명 **differently**. `KKB_APITR_SMTN_L001` resolves it inline —

   ```sql
   (SELECT B.ISNM FROM FT_FTIS_INFO B WHERE B.FINTECH_ISCD = A.IS_CD) AS IS_NM
   ```

   — while `BULK_KKB_APITR_SMTN_L001`, whose SQL is otherwise character-for-character the same query, selects `'' AS IS_NM` and has the name patched in afterwards by Java holding a `HashMap` built from a *`BIZTALK_DB`* query.

Observation 3 is hard to explain if both aliases resolve to one database. The author had a working correlated subquery in front of them, copied the entire rest of the statement, and replaced only that one expression with a literal — then wrote extra application code to undo the loss. The natural reading is that **`FT_FTIS_INFO` is not reachable from the bulk datasource**, which means the two aliases are two physical databases.

**Working assumption flips from A (one instance) to B (two instances).** This is inference, not proof, and it is confirmed empirically by task R1-01 before any code depends on it.

### Why the conflict is nonetheless not real

The three shapes Skill 2 listed all assumed the merge must happen either wholly in the application (option A, unbounded) or wholly in the database (options B and C, requiring infrastructure or DDL). There is a fourth shape, and it is bounded without needing either.

Both sources carry the **same table, the same primary key and the same sort key**. Two streams sorted by an identical key can be merged by a k-way merge that holds only the current head of each stream. Pagination then needs only a **seek key**, not an offset — and a seek key each source can apply independently, in SQL, using its own index.

## Decision

**Keyset-paginated streaming merge across the two datasources.**

**Sort key** `K = (TRDD DESC, IS_CD ASC)` — the aggregate's primary key, therefore unique within each source, therefore a total order (FR-RPT-006).

**Per-source query**, issued identically to each datasource:

```sql
SELECT TRDD, IS_CD, COALESCE(AT_CNT,0) AS AT_TOTAL, …
FROM KKB_APITR_SMTN
WHERE TRDD BETWEEN :startDt AND :endDt
  AND (:isCd IS NULL OR IS_CD = :isCd)
  -- 이어보기 술어 / the seek predicate, applied only when a seek key is supplied
  AND ( TRDD < :seekTrdd
        OR (TRDD = :seekTrdd AND IS_CD > :seekIsCd) )
ORDER BY TRDD DESC, IS_CD ASC
LIMIT :fetchSize
```

> **Corrected during Sprint R1 (2026-08-18).** This ADR first wrote the seek predicate as the
> row-value form `(TRDD, IS_CD) < (:seekTrdd, :seekIsCd)`. **That is wrong here.** Row-value
> comparison expresses a keyset seek only when every column sorts in the *same* direction; this
> sort mixes them (`TRDD DESC, IS_CD ASC`), so the row-value form would silently skip or repeat
> the rows sharing a date with the seek key. It is expanded into the explicit `OR` above.
>
> The error is worth recording rather than quietly fixing: it is the same class of defect this
> ADR is built to prevent — **a mistake that yields plausible rows instead of an exception.** It
> was caught by property test P-4 (page boundaries at arbitrary offsets), not by review.

**Merge**, in the service layer: advance whichever stream has the greater key; when both heads carry the same `K`, sum their counters into one row and advance both (FR-RPTS-003). With 발송구분 narrowed to API or 대량, one stream is simply not opened.

**Memory bound**: at most `2 × fetchSize` rows resident, independent of the date range. This is what makes FR-RPT-005 real rather than declared — the fourth occurrence of declared-but-absent pagination (D-R8) is closed by construction, not by remembering to add `LIMIT`.

**`COALESCE` is applied in SQL, per source, before the merge** (FR-RPT-011). This is not cosmetic: a NULL counter that survives into the merge would propagate through the summation and null an entire merged row, which is D-R11 re-created one layer higher.

**Total count** (FR-RPT-005 requires one). A count of the merged set cannot be `count(A) + count(B)` — days present in both sources would be counted twice. It is computed by fetching **only the two key columns** from each source over the range and taking the size of their union. At the 366-day cap that is `366 × |institutions|` pairs per source — tens of thousands of two-column rows, trivially cheap — and it is exact. A ceiling guards the pathological case: above `MAX_KEY_PROBE` (provisionally 500,000 keys) the response carries `hasMore` instead of an exact total, and says so.

**The order is load-bearing.** In the legacy, ordering was cosmetic and differed between environments (D-R7). Here the merge is *only correct* if both streams arrive in the same order, so FR-RPT-006 is promoted from a display property to a correctness precondition, and is asserted by test rather than assumed.

## Alternatives considered

| Option | Bounded? | Needs | Verdict |
|--------|----------|-------|---------|
| **A — fetch both sources fully, merge in the application** | **No** | nothing | **Rejected.** This is the unbounded fetch that D-R8 exists to remove, reinstated one layer up. At 전체 over 366 days it materialises the entire result to serve one page. It was Skill 2's working assumption only because the fourth option had not been identified |
| **B — federated query (postgres_fdw / dblink)** | Partly | a persistent DB→DB link, DBA provisioning | **Rejected.** Pushes the merge into SQL but buys it with a standing credentialed link between two production databases — a new trust boundary and a new attack surface (threat model T-R09) for a problem the application can solve with no new infrastructure. It also makes the report's correctness depend on a component this team does not operate |
| **C — consolidated read store fed by the batch** | Yes | DDL + batch ownership | **Rejected.** The cleanest shape on paper and genuinely better at scale, but it needs a new table (colliding with CONST-DATA-R02 and requiring the same G1 precedent ADR-SND-017 needed) *and* it needs the batch, which the PM has ruled out of this slice. Recorded as the natural successor if the report's volume ever outgrows option D |
| **D — keyset-paginated streaming merge (chosen)** | **Yes** | nothing | **Accepted** |

## Consequences

**The decision does not depend on AMB-R04.** This is the property that dissolves CONFLICT-R02 rather than merely resolving it. If R1-01 confirms two databases, the design above is what ships. If it finds one, the identical semantics collapse into a single statement —

```sql
SELECT TRDD, IS_CD, sum(…) FROM ( … UNION ALL … ) GROUP BY TRDD, IS_CD ORDER BY …
```

— behind the same service interface, the same sort key and the same page contract, and only the mapper layer changes. **No requirement, no test and no API shape is contingent on the answer.** CONFLICT-R02 is therefore closed here, and R1-01 becomes an optimisation question rather than a gate.

> This follows the CONFLICT-I02 precedent from the institution slice — a conflict escalated on an untested premise, then dissolved by one pass over what the premise actually rested on. The difference is instructive: there the premise was false (a status column already existed); here the premise was true (two databases) but the conclusion did not follow from it.

**No distributed transaction.** Both sources are read-only in this slice (CONST-DATA-R01), so the two datasources need no XA coordination and ADR-002's boundaries are unaffected. Two `SqlSessionFactory` beans, mappers bound per datasource, no shared transaction.

**Partial-source behaviour is explicit.** If one source is unreachable the merge degrades to the surviving stream and the response is marked incomplete, naming the missing source (FR-RPTS-005, NFR-OPS-R01). It never silently under-reports — which is the failure mode D-R27 already produces upstream.

**Costs accepted.** Keyset pagination cannot jump to an arbitrary page number; the UI offers next/previous and a total, not deep random access. This is a real reduction against the legacy's paging widget — which paged in the browser over an unbounded fetch and therefore never worked at scale anyway. Requires an index on `(TRDD, IS_CD)`; the primary key already provides it, so **no DDL** (CONST-DATA-R02 holds).
