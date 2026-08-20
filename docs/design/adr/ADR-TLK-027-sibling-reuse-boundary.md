# ADR-TLK-027: Where reuse of the sibling slices stops

> **상태**: ACCEPTED
> **일자**: 2026-08-19
> **작성자**: `architect`
> **결재자**: PM (G2)
> **관련 ADR**: [ADR-005](ADR-005-pii-encryption.md), [ADR-006](ADR-006-audit-logging.md), [ADR-RPT-023](ADR-RPT-023-export-generation.md)
> **관련 요구사항**: FR-TLKX-001…010, FR-TLKD-008, FR-TLKM-002, NFR-SEC-PII-T01, NFR-SCALE-T01
> **관련 위험**: RISK-T06
> **관련 미해결 항목**: AMB-T06

---

## 1. 컨텍스트 (Context)

This is the sixth slice, and by now the reflex is to reuse. Two candidates present themselves, and **they turn out to be opposite answers.**

### 1.1 The export — reuse is straightforwardly right

The 이용기관 보고서 slice built a streamed export in [ADR-RPT-023](ADR-RPT-023-export-generation.md): `SXSSFWorkbook` with a sliding window, rows pulled from the screen's own iterator, a hard row ceiling, `fetch` delivery so failures are visible, RFC 6266 filename encoding, one content type, audit with row count. Every one of those properties is a requirement here too, and each maps to a defect this slice must close: D-T12 (unbounded memory), D-T1 (export diverged from screen), D-T23 (invisible failure), D-T4 (header injection), D-T34 (four content types), D-T3's audit gap.

The 보고서 export produces two sheets of different shapes; 톡전송 내역 produces one flat nine-column grid. The mechanism is a strict superset of what this slice needs.

### 1.2 The message tables — reuse looked right and is wrong

The obvious assumption is that screens 31/32 read the same message tables as the 문자내역 slice, and that `MessageDetailMapper` can serve both. **It cannot, because they are disjoint table families.**

| Slice | Tables |
|-------|--------|
| 문자내역 (screens 40/41) | `KKO_SMS_MSG`, `KKO_MMS_MSG`, `KKF_SMS_MSG`, `KKF_MMS_MSG` + four `_LOG` |
| 톡전송 내역 (screens 31/32) | `KKO_MSG`, `KKF_MSG` + two `_LOG` |

Twelve tables, no overlap. The corroborating evidence is upstream: `IDO.KKB_APITR_SMTN_C001`, the daily aggregation batch, computes `AT_CNT` — the 알림톡 count — from `KKO_MSG` and `KKO_MSG_LOG`, and computes the SMS/LMS/MMS counters from elsewhere. `KKO_MSG` carries `TEMPLATE_CODE`, `PROFILE_KEY`, `AD_FLAG`, `BUTTON_JSON` and the `FAILED_*` group; `KKO_SMS_MSG` carries none of those. The two families are the **talk message** and its **SMS failback**, at different stages of one delivery.

Sharing a mapper between them would not be reuse — it would be the copy-and-diverge defect this slice is full of, performed deliberately.

## 2. 결정 (Decision)

> **Reuse the export mechanism wholesale and the cross-cutting components unchanged. Do not share the message read path: `TalkMessageMapper` is new, and it reuses the sibling's *conventions* rather than its *code*.**

### 핵심 선택 사항

**Reused unchanged** — consumed, not extended:

| Component | Source slice | Used for |
|-----------|--------------|----------|
| `TenantContext` / `TenantPrincipal` | 문자내역 | Principal and role resolution |
| `ReportScope` pattern | 보고서 | Operator/tenant branch — see below |
| `AuditService` / `AuditEvent` | shared (ADR-006) | FR-AZ-T05, FR-TLKX-007 |
| `PagedResult` | 문자내역 | FR-TLK-005, FR-TLKD-007 |
| `PeriodPolicy` | 보고서 | FR-TLK-007 — **new 31-day instance**, see below |
| `infra.excel` streamed writer | 보고서 (ADR-RPT-023) | FR-TLKX-001…010 |
| `InstitutionName` resolution | 보고서 | FR-TLK-011 |

**`ReportScope` is generalised, not copied.** It already encodes exactly the operator/tenant distinction CONFLICT-T01 needs, including the two properties that matter — a tenant's blank value does not mean "all", and a tenant's supplied value is *ignored* rather than validated-and-rejected, so the error message cannot become an institution-enumeration oracle. This slice is operator-only, so its use is the simpler branch, but the class moves to `common.tenant` as `PrincipalScope` and the 보고서 slice's alias points at it. **Two copies of an authorization rule is the one duplication this programme cannot afford.**

**`PeriodPolicy` gains a second configured instance, not a second class.** The 보고서 cap is 366 days because its row grain is 일자 × 기관; this slice's cap is 31 days (AMB-T02) because its grain is one transaction per row — the same reasoning the 문자내역 slice applied at AMB-06. The cap becomes a constructor parameter; the parsing, inversion and calendar-validity logic is one implementation.

**`TalkMessageMapper` is new.** It reads the four talk tables, applies `masking(decrypt(…))` at the outermost projection exactly as `MessageDetailMapper` does (ADR-005, and the same placement that satisfies FR-TLKD-008 / FR-TLKM-002), and follows the sibling's union-with-`TABLE_TYPE` shape. What is shared is the **pattern and the masking placement**; what is not shared is a line of SQL, because the columns differ.

**One deviation from ADR-RPT-023 is recorded.** Its option C (CSV) was rejected there because the report has two sheets of different shapes. That reason does not apply here — this grid is one flat table, and `CsvExporter` already exists. It is nonetheless rejected again, for a different reason: BR-011 is written against Excel, the 보고서 slice already ships xlsx, and shipping two export formats across two operator screens is a support cost with no user benefit. The reasoning is different, so it is stated rather than inherited.

## 3. 검토한 대안 (Considered Alternatives)

| # | 대안 | 장점 | 단점 | 채택 |
|---|------|------|------|------|
| A | **Share `MessageDetailMapper` across both slices** | Apparent reuse; one message read path | **Factually impossible** — disjoint tables, disjoint columns. Would require a union of twelve tables to serve two screens that each want four | 미채택 |
| B | **New `TalkMessageMapper`; reuse cross-cutting components and the export writer (chosen)** | Reuse where the thing is genuinely the same; new code where the data is genuinely different | Two mappers with a similar shape — a reviewer must be told why, which is what this ADR is for | **채택** |
| C | **New mapper *and* a new export path** | Full independence | Re-implements a solved problem and would re-open D-T12, D-T23, D-T4 and D-T34, each of which ADR-RPT-023 already closes | 미채택 |
| D | **Generalise `MessageDetailMapper` over a table-set parameter** | One class, two configurations | The column sets differ (`TEMPLATE_CODE`, `BUTTON_JSON`, `FAILED_*` exist in one family only), so the generalisation would be over the union — a type that is half-null for every caller | 미채택 |

## 4. 결과 (Consequences)

**AMB-T06 is raised and is not blocking.** The exact relationship between the two families — whether a `KKO_SMS_MSG` row is the SMS failback of a `KKO_MSG` row, and whether they share `SERIALNUM` — is a domain question. This slice does not need the answer: it reads its own four tables. It is raised because the two slices together are the first place in the programme where the question is visible, and because a future "message search across all channels" feature would need it.

**One claim in an approved document is qualified, not corrected.** REQUIREMENTS-SPEC.md §1.2 tabulates `MSG_TYPE=AT` against `KKO_SMS_MSG` / `KKO_MMS_MSG`. That is accurate for what screen 40 reads. It is **not** the complete set of 알림톡 message records, because `KKO_MSG` also holds them and screen 40 never touches it. No requirement in that slice changes; the question log records the qualification, and AMB-T06 asks the domain owner to state the relationship authoritatively.

**A programme-level component moves.** `PrincipalScope` in `common.tenant` becomes the single place the operator/tenant distinction is decided, consumed by 보고서 and 톡전송 내역 and available to every future operator screen. Task T1-04 performs the move and re-runs the 보고서 slice's authorization tests unchanged against it — if any of them fail, the generalisation is wrong and is reverted rather than adjusted.

**The export costs roughly a third of what it did in the 보고서 slice**, because one sheet replaces two and the writer already exists. That is why Sprint T2 is shorter than R2 despite this slice having more defects.

---

## 5. 구현 시점 정정 (Correction, Sprint T2, 2026-08-19)

**§2 stated that the 이용기관 보고서 slice's streamed export writer would be reused, and that "the 보고서 slice already ships xlsx". Both are false.**

`ADR-RPT-023` chose SXSSF streaming programme-wide, but the writer was scheduled for that slice's **Sprint R2, which has not run**. There is no `infra.excel` package, and Apache POI was not in the POM. The 보고서 slice ships no export at all yet.

This is the same error the ADR itself was written to catch, committed in the ADR that catches it: **I assumed a sibling's component existed because a plan said it would.** §1.2 found the message tables were not shared by checking; §1.1 asserted the export writer was shared without checking.

### What this changes

**The 톡전송 내역 slice builds the writer rather than reusing it.** `infra.excel.StreamingWorkbookWriter` is new code in this slice, and the 보고서 slice's Sprint R2 consumes it — the dependency direction reverses. Apache POI 5.2.5 is added to the POM with its Apache-2.0 licence and rationale recorded inline (CODE-004).

### The CSV question, re-decided rather than inherited

§2 rejected CSV partly on a false premise, so the decision is re-taken on the facts.

ADR-RPT-023 rejected CSV because *the report has two sheets with different shapes*, while noting CSV was "genuinely adequate for a single flat grid". **톡전송 내역 is a single flat nine-column grid**, so that reason does not transfer, and `CsvExporter` already exists and is tested. The honest position is that CSV would be cheaper and adequate.

xlsx is still chosen, on two grounds that survive scrutiny:

1. **BR-011 is a `Must` and names Excel.** Reading it as "any tabular file" is a reinterpretation this slice has no standing to make.
2. **The streaming and row-cap properties are required regardless of format** (FR-TLKX-005, NFR-SCALE-T01), and SXSSF is how ADR-RPT-023 specified achieving them. Building it here makes it available to 보고서 R2; choosing CSV here would leave that slice to build it anyway, later, alone.

The cost is stated rather than hidden: **this slice is the first consumer of new infrastructure**, which ADR-RPT-023 explicitly avoided for its own option D. That objection applied to an asynchronous job store, which needs a queue and a state machine. A streaming workbook writer is one class with one dependency.

### What §2's reuse table still gets right

Everything except the export row. `TenantContext`, `AuditService`, `PagedResult`, `PeriodPolicy`, `PrincipalScope` and `InstitutionName` were all verified to exist before being consumed, and all are consumed unmodified or generalised in place.
