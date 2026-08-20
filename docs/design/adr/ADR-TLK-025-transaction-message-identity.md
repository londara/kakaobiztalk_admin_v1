# ADR-TLK-025: The transaction ↔ message identity, and where it is normalised

> **상태**: ACCEPTED
> **일자**: 2026-08-19
> **작성자**: `architect`
> **결재자**: PM (G2)
> **관련 ADR**: [ADR-003](ADR-003-persistence-strategy.md), [ADR-TLK-026](ADR-TLK-026-detail-serviceability.md)
> **관련 요구사항**: FR-TLK-009, FR-TLKD-009, FR-TLKM-004, CONST-BIZ-T01
> **관련 위험**: RISK-T04
> **관련 미해결 항목**: AMB-T04

---

## 1. 컨텍스트 (Context)

The slice is a three-level drill-down, and the join between level one and level two is undeclared, undocumented and normalised three different ways in the legacy.

`FT_APITR_HSTR.IS_TUNO` identifies a transaction; `KKO_MSG.SERIALNUM` identifies a message. The screens treat them as the same identifier. Nothing in the schema says so — there is no foreign key — and the three code paths that use the relationship disagree about its form:

| Path | Rule | Source |
|------|------|--------|
| List search | `padStart(20, '0')`, then `IS_TUNO = :IS_TUNO` exactly | `biztalk_admin_30.js` `getDat()` |
| 알림톡 detail | `StringUtils.stripStart(v, "0")`, then `SERIALNUM = LPAD(:SERIALNUM, 10, '0')` | `biztalk_admin_32_l001_act.jsp` + `IDO.KKB_AT_MSG_L001` |
| 친구톡 detail | `StringUtils.stripStart(v, "0")`, then `SERIALNUM = :SERIALNUM` raw | same action + `IDO.KKB_FT_MSG_L001` |

The 알림톡 rule is not merely inconsistent, it is **lossy**. PostgreSQL's `lpad(string, length, fill)` truncates when the input is longer than the target width. A 20-character 거래고유번호 such as `00000026081900142813` is stripped to `26081900142813` (14 characters) and then truncated by `lpad(…, 10, '0')` to `2608190014` — a value that matches the wrong message or none (D-T9). The 친구톡 rule avoids the truncation and introduces a different asymmetry instead: it matches a stripped value against a column the 알림톡 branch believes is zero-padded.

The screenshot shows real 거래고유번호 values are 20 characters. Whether `SERIALNUM` is 10, whether the relationship is equality at all, and whether one transaction maps to one message or many, are all inferred rather than known — which is AMB-T04, and it is the only open item that blocks a requirement.

## 2. 결정 (Decision)

> **One canonical `TransactionSerial` value object, normalised once at the domain boundary and used unchanged by list, both detail levels and the export — with a data probe (task T1-01) fixing the stored widths before any mapper is written.**

### 핵심 선택 사항

- **One type, one normalisation.** `TransactionSerial` parses a user- or row-supplied value, rejects non-numeric input, and renders exactly two forms: the storage form for `FT_APITR_HSTR.IS_TUNO` and the storage form for `KKO_MSG.SERIALNUM`. No caller pads, strips or trims; the three legacy rules become one type with two renderers.
- **Padding is applied in Java, never in SQL.** The truncation defect is what this removes.

  > **정정 2026-08-19 (부하 측정, 회고 액션 B1).** 이 항목은 원래 "`LPAD` 는 잘림 결함이면서 동시에 sargability 문제 — `SERIALNUM` 인덱스를 쓸 수 없게 만든다"고 적었다. **뒤의 절반은 거짓이다.** sargability 는 함수가 **컬럼**에 적용될 때 깨지고, 레거시는 함수를 **파라미터**에 적용했다(`SERIALNUM = LPAD(:SERIALNUM,10,'0')`). PostgreSQL 은 그것을 계획 시점에 상수로 접어 넣으므로 인덱스를 그대로 쓴다.
  >
  > **Correction 2026-08-19 (load measurement, retrospective action B1).** This bullet originally claimed `LPAD` was "both the truncation bug **and** a sargability problem". **The second half was false.** Sargability breaks when a function is applied to the **column**; the legacy applied it to the **parameter**, which PostgreSQL folds to a constant at plan time, so the index is used normally. `TalkHistoryLoadTest#lpadOnAParameterDoesNotPreventIndexUse` prints all three plans side by side: Java-padded, `LPAD` on the parameter (the legacy's actual shape — index used), and `LPAD` on the column (what this ADR wrongly described — sequential scan).
  >
  > **The decision does not change.** The truncation is real and `LpadTruncationTest` proves it by execution against a real PostgreSQL. What changes is that **one of the decision's two stated justifications was invented**, and that is recorded rather than quietly deleted — a plausible-sounding mechanism that nobody measured is exactly the defect class this slice exists to remove, and it appeared in the ADR written to remove it.
- **Widths come from measurement, not from the legacy's assumptions.** Task **T1-01** queries `max(length(IS_TUNO))`, `min/max(length(SERIALNUM))` and the cardinality of the join on a production-like dataset, and its result configures the type. This is the same gate shape as S1-03 (발신번호) and R1-01 (보고서).
- **The join's cardinality is asserted, not assumed.** T1-01 also answers whether one transaction yields one message or many. FR-TLKD-001 is written for many; if the data says one, the detail screen keeps its list shape and the assertion becomes a regression test rather than a redesign.
- **A round-trip property test** (`normalise(render(x)) == render(x)` for every observed length) is the standing guard, plus explicit cases at 10, 14 and 20 characters — the three lengths the legacy handles differently.

## 3. 검토한 대안 (Considered Alternatives)

| # | 대안 | 장점 | 단점 | 채택 |
|---|------|------|------|------|
| A | **Port the three rules as-is** | Literal parity | Ports a data-loss bug and two inconsistencies. AMB-01 ("fix all") forbids it, and the 알림톡 rule is provably wrong for the identifier lengths in production | 미채택 |
| B | **One canonical value object, widths probed first (chosen)** | One rule everywhere; SQL stays sargable; the unknown is resolved by measurement in week one | Costs a probe task before the mappers can be written | **채택** |
| C | **Store both forms — add a normalised column and index it** | Fastest queries; no padding at all | **DDL on shared tables** — CONST-DATA-T02, and `KKO_MSG` is written by the send pipeline, not by this slice | 미채택 |
| D | **Match with `LIKE '%' || :v` / trim-insensitive comparison** | Tolerant of every legacy form without knowing the widths | Non-sargable, and tolerance is the wrong property for an identifier — a suffix match can return a *different* transaction's messages, which is a cross-institution disclosure path under CONST-BIZ-T01 | 미채택 |

## 4. 결과 (Consequences)

**FR-TLKD-009 is marked `BLOCKED-AMB-T04` in the matrix and is unblocked by T1-01, not by a meeting.** The requirement's text ("lossless for identifiers of any length") is correct under every answer AMB-T04 can have; only the widths are unknown, and they are measurable.

**If T1-01 shows the relationship is not plain equality** — for example that one API transaction fans out to many messages keyed by a compound value — the type absorbs it. The alternative shape (B in AMB-T04) changes `TransactionSerial`'s two renderers into a lookup, and nothing above the domain boundary changes. This is why the probe gates the mappers rather than the design.

**A performance property comes free.** Removing `LPAD` from the predicate makes any existing index on `SERIALNUM` usable. NFR-PERF-T02 (detail P95 < 1 s) is measured against that, and the load test compares both forms so the improvement is recorded rather than assumed.

**One class of legacy defect is closed structurally.** D-T9 and D-T25 are the same defect seen twice, and both are consequences of normalisation living at three call sites. A type cannot have three rules.
