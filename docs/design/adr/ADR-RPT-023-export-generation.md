# ADR-RPT-023 — Excel export generation, delivery and failure surfacing

> **Status**: ACCEPTED
> **Date**: 2026-08-18
> **Slice**: 이용기관 보고서 (screen 20 다운로드)
> **Decides**: AMB-R05; defers FR-RPTX-010
> **Requirements**: FR-RPTX-001…013, NFR-PERF-R03, NFR-SCALE-R01, NFR-SEC-HDR-R01, NFR-COMPAT-R01
> **Supersedes**: nothing. **Related**: [ADR-RPT-021](ADR-RPT-021-cross-source-aggregation.md), [ADR-006](ADR-006-audit-logging.md)

---

## Context

The legacy export is a second, separately implemented data path with its own contract, its own parameter handling and five defects of its own. Four of them decide this ADR:

- **D-R15** — `XSSFWorkbook` materialises up to four sheets fully in memory before writing a byte, over a range with no cap. An OOM whose likelihood scales directly with D-R9.
- **D-R16** — the download posts a form to a hidden iframe (`ifrmFileProc`). When the action throws, the Jex error page renders **inside an invisible frame**: no file, no message, no indication anything happened.
- **D-R3** — `START_DT`/`END_DT` are read raw via `request.getParameter`, concatenated into the filename and written to `Content-Disposition`. The non-IE branch performs no encoding at all — response splitting.
- **D-R10** — `WSVC.biztalk_admin_20_spreadsheet` declares only `START_DT` and `END_DT`; `IS_CD` is undeclared, which is *why* the action reads the raw request, which is *how* D-R3 becomes reachable.

D-R16 is the constraint that shapes the delivery mechanism, and it is worth stating why: **you cannot show an error from a hidden-iframe form post.** The browser has no handle on the response, so a failure is structurally invisible. FR-RPTX-011 is therefore not satisfiable by adding error handling to the existing pattern — the pattern has to go.

**PM ruling (2026-08-18).** Enforce the cap, defer async. The programme has no job store, no queue and no notification channel, and the first consumer of that infrastructure should not be a `Should`-priority requirement.

## Decision

**Streamed synchronous generation with a hard row ceiling, delivered over `fetch` so failures are visible.**

**Generation.** Apache POI `SXSSFWorkbook` with a sliding window (100 rows) and compressed temp files. Rows are pulled from **the same keyset-merge iterator the screen uses** (ADR-RPT-021) — one query path, two renderers. Export and screen cannot disagree, which is what makes FR-RPTX-001 verifiable rather than aspirational.

Two sheets, not the legacy's four: **총합** and **일자별 상세**, both carrying 전체 / 성공 / 실패 / **처리중** for every channel (FR-RPTX-007). The separate BULK sheets are folded into the 구분 column by FR-RPTS-003, and the sheet set does not vary by environment (FR-RPTX-008) because nothing in the read path consults one.

**Ceiling — this is AMB-R05's answer.** A hard limit on merged rows, above which the request is refused with an actionable message naming the range that would fit. The value is **set by measurement, not by guess**: the provisional figure is 100,000 rows, and the NFR-PERF-R03 load test (92-day all-institution export under 60 s) fixes the final number before G3. Refusal is a 400 with a specific message, never a truncated file — a silently short workbook is the export equivalent of a silent success, and this programme has met that failure mode three times already.

**Delivery.** The client issues `fetch` with credentials, receives either a binary body or a JSON error, and saves the blob. This replaces the hidden iframe and is the whole of FR-RPTX-011: an error status becomes a visible message because the caller can now see it.

**Headers.** Filename composed server-side from values that have already passed validation (FR-RPTX-003), encoded per RFC 6266 with the RFC 5987 `filename*` form; CR, LF and control characters are rejected at validation and cannot reach a header in any case (FR-RPTX-004, NFR-SEC-HDR-R01). Exactly one content type — `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` — set once (FR-RPTX-005), replacing the legacy's four conflicting declarations, none of which named the format actually produced.

**Contract.** Every input — 기관코드, 발송구분, both dates — is declared on the export API and read from the validated request object. The export re-runs authorization and validation in full (FR-RPTX-002); it is a second entry point to the same data, and Skill 2's finding is that it was the *less* protected of the two.

**Audit.** Every export writes an audit event with actor, scope, range, 발송구분 and **the row count actually written** (FR-RPTX-012), via the existing `AuditService` (ADR-006). Row count matters: it is the difference between knowing someone opened the report and knowing someone took 90,000 rows of customer volume data off the system.

**Licensing (CODE-004).** Apache POI is **Apache-2.0** — permissive, no copyleft obligation, already present in the legacy stack. No new licence exposure.

## Alternatives considered

| Option | Memory | Errors visible? | Verdict |
|--------|--------|-----------------|---------|
| **A — `XSSFWorkbook`, synchronous (status quo)** | Unbounded | No | **Rejected.** This is D-R15 and D-R16 unchanged |
| **B — `SXSSFWorkbook` + ceiling + `fetch` delivery (chosen)** | Bounded (window × row width) | Yes | **Accepted** |
| **C — CSV instead of xlsx** | Bounded | Yes | **Rejected.** Cheapest and genuinely adequate for a single flat grid, but the report has two sheets with different shapes (총합 and 일자별 상세) and BR-011 is written against Excel. Losing the summary sheet to save engineering the streaming path is a bad trade |
| **D — asynchronous job + notification** | Bounded | Yes | **Deferred by PM ruling.** The right answer once the programme has a job store; introducing one here would make a `Should` requirement the first consumer of new infrastructure. Recorded as the successor to option B, and FR-RPTX-010 stays `Should` and unbuilt rather than being quietly downgraded |

## Consequences

**FR-RPTX-010 is deferred, explicitly.** It remains in the specification at `Should` with this ADR named as the reason it is unbuilt. The ceiling is what stands in for it: a user who would have received an async job instead receives a clear refusal and a smaller range. That is a worse experience and a correct system, and it is recorded as such rather than presented as equivalent.

**Export and screen share one query path.** This is the structural fix for a whole class of legacy defect — the export diverged from the screen because it was a separate implementation, and every divergence (D-R3, D-R4, D-R10, and the summary sheet's missing 실패/처리중 columns in D-R14) traces to that. Test TC-R002-18 asserts the two agree row for row.

**The download is the one place data leaves the trust boundary as a file.** Once saved, the workbook carries per-institution volumes with no further control — no expiry, no watermark, no DLP. This is threat T-R11 and it is an accepted residual: the mitigation available to us is the audit record of who took what, not control of the file afterwards.

**Costs accepted.** SXSSF's sliding window means arbitrary backward cell access is unavailable, so column auto-sizing cannot inspect every row; column widths are set from a bounded sample plus fixed minimums. The legacy called `autoSizeColumn` per sheet, which is part of why it held everything in memory. Cosmetic width differences against the legacy output are expected and are not parity failures — TEST-PLAN §11 records this as a known, accepted divergence.

---

## Addendum — the writer now exists, built elsewhere (2026-08-19)

**`infra.excel.StreamingWorkbookWriter` is implemented, in the 톡전송 내역 slice's Sprint T2, not here.**

This ADR chose SXSSF streaming programme-wide and scheduled the writer for this slice's Sprint R2, which has not run. The 톡전송 내역 slice planned to reuse it ([ADR-TLK-027](ADR-TLK-027-sibling-reuse-boundary.md) §2 asserted, wrongly, that it already existed and that this slice "already ships xlsx"), discovered on contact that neither was true, and built it. The correction is recorded in [ADR-TLK-027 §5](ADR-TLK-027-sibling-reuse-boundary.md).

**The dependency direction is therefore reversed from what this ADR assumed.** Sprint R2 consumes the writer rather than producing it.

**What it provides**, all of which this ADR specified: `SXSSFWorkbook` with a 100-row sliding window, compressed temp files disposed in a `finally`, header cells written exactly once (this ADR's D-R21 and the talk slice's D-T34 are the same defect), and rows accepted as an `Iterable` so a caller cannot materialise the whole result first — which is the property that makes the streaming meaningful.

**What Sprint R2 still owns.** Two sheets of different shapes (총합 and 일자별 상세), which is the reason this ADR rejected CSV. The writer takes one sheet per call, so the report calls it twice or the writer gains a multi-sheet entry point — a small extension, and a decision for R2 rather than a gap. Apache POI 5.2.5 is now in the POM with its Apache-2.0 licence recorded inline, so R2 adds no dependency.

**One incidental finding worth carrying.** POI 5.2.5 calls `UnsynchronizedByteArrayOutputStream.builder()`, added in commons-io 2.15, while `spring-boot-starter-parent` manages an older version — it **compiles** and fails at runtime with `NoSuchMethodError`. Only an integration test finds that class of fault; `TalkExportParityTest`'s workbook assertions caught it on their first run. `commons-io` is pinned to 2.16.1 in the POM as a result.
