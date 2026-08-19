# ADR-TLK-024: Classifying which API transactions are BizTalk

> **상태**: ACCEPTED
> **일자**: 2026-08-19
> **작성자**: `architect`
> **결재자**: PM (G2)
> **관련 ADR**: [ADR-003](ADR-003-persistence-strategy.md), [ADR-006](ADR-006-audit-logging.md)
> **관련 요구사항**: FR-TLK-002, FR-TLK-010, FR-TLK-012, CONST-DATA-T02, CONST-SEC-T01
> **관련 위험**: RISK-T01, RISK-T05
> **관련 미해결 항목**: AMB-T03

---

## 1. 컨텍스트 (Context)

PM ruling SCOPE-T01 restricts 톡전송 내역 to BizTalk transactions. The legacy screen has no such restriction: `IDO.KKB_APITR_HSTR_L001` selects from `FT_APITR_HSTR` — the shared Open-API transaction log for every fintech API — with no channel predicate, and `IDO.KKB_OPENAPI_INFO_L002` fills the API selector with `WHERE 1=1`. The production screenshot shows `ADV_COM_GET_STATUS`, a status-polling API, sitting in a grid headed BizTalk 내역.

Implementing SCOPE-T01 therefore requires something the legacy never had: **a definition of which API codes are BizTalk.** Three facts constrain how that definition can be built.

**The set is data, not code.** A scan of `IRIS_ADMIN` and `IRIS_ADMIN_ETC` yields exactly five literals — `ADV_KKO_AT_SEND`, `ADV_KKO_AT_SEND2`, `ADV_KKO_AT_SEND_M`, `ADV_KKO_FT_SEND`, `ADV_KKO_FT_SEND_M`. `ADV_COM_GET_STATUS` appears in the screenshot and in no source file, so `FT_OPENAPI_INFO` holds codes the codebase never names. Any list derived only from source is provably incomplete as a description of production.

**Two code systems are involved and the legacy confuses them** (D-T15). `API_CD` is the registered API's key in `FT_OPENAPI_INFO` and is what the legacy filter matched; `API_SVC_CD` is the service code stamped on the transaction and is what the grid displayed. FR-TLK-010 requires the two to be reconciled, so the classification must be expressed in whichever one the screen commits to.

**Getting it wrong is quiet.** Over-inclusion is what the legacy does today and is visible — an operator sees a row that does not belong. Under-inclusion is invisible: a real BizTalk transaction simply does not appear, and nothing indicates a filter removed it. The rebuilt screen trades a loud failure for a silent one, which is exactly the failure shape this programme has now recorded in six consecutive slices.

**CONST-DATA-T02 forbids DDL**, which rules out the structurally cleanest answer.

## 2. 결정 (Decision)

> **A configuration-held allow-list of `API_SVC_CD` values, validated against `FT_OPENAPI_INFO` at startup, with a standing reconciliation report for codes the list does not cover.**

### 핵심 선택 사항

- **`API_SVC_CD` is the committed code system.** FR-TLK-010 requires filter and column to agree; `API_SVC_CD` is what the transaction actually carries, what the grid shows, and what `biztalk_admin_32_l001_act.jsp` already branches on for detail routing. Classifying on `API_CD` would mean joining `FT_OPENAPI_INFO` on every list query to translate — a second correlated lookup on the query D-T26 already burdens.
- **The list lives in application configuration**, seeded with the five source-derived literals, changeable without a code change or a release.
- **Startup validation.** Every configured code is checked to exist in `FT_OPENAPI_INFO`; a code that does not is logged at WARN with the code named. A typo in configuration silently narrows the screen otherwise, which is precisely the quiet failure this decision exists to avoid.
- **Standing reconciliation report.** A scheduled query counts, per `API_SVC_CD` **not** in the allow-list, transactions whose institution has BizTalk service registered. Any non-zero count is a candidate the list is missing. This converts under-inclusion from invisible to reported — it is the operational half of the decision and the reason the allow-list is safe to use.
- **No DDL.** `FT_OPENAPI_INFO` is not altered (CONST-DATA-T02). If the domain owner later adds a classification column, this design reads it instead of the config list and nothing else changes — the boundary is one interface, `BizTalkApiRegistry`.

## 3. 검토한 대안 (Considered Alternatives)

| # | 대안 | 장점 | 단점 | 채택 |
|---|------|------|------|------|
| A | **Hardcoded enum of the five literals** | Simplest; typed; testable | Provably incomplete — `ADV_COM_GET_STATUS` proves codes exist outside source. Every new talk API needs a release | 미채택 |
| B | **Prefix rule on `API_SVC_CD` (`ADV_KKO_*`)** | Zero maintenance; covers unknown codes automatically | Guesses a naming convention nobody has confirmed, and inherits whatever the convention does next. A non-talk API named `ADV_KKO_*` would be admitted silently — the same class of silent error, inverted | 미채택 |
| C | **Classification column on `FT_OPENAPI_INFO`, maintained by the domain owner** | Authoritative; single source of truth; survives this slice | **Requires DDL on a table shared with the whole API estate** — CONST-DATA-T02 forbids it, and the owner of that table is not this project | 미채택 (deferred successor) |
| D | **Config allow-list + startup validation + reconciliation report (chosen)** | No DDL; changeable without release; **under-inclusion becomes visible**; collapses to option C behind one interface if the column is ever added | Needs an owner for the list, and the reconciliation report is real work that produces no user-visible feature | **채택** |

**Scoring is not applied here.** The harness's six-dimension weighted comparison exists for technology selection; this is a correctness-and-observability decision between four shapes of the same mechanism, and the deciding factor — whether the failure mode is visible — is not one of the six dimensions.

## 4. 결과 (Consequences)

**AMB-T03 is answered provisionally and closed operationally, not by a document.** The working assumption is the five literals. The reconciliation report is what turns that assumption into knowledge, and it will do so during Sprint T1 against real data rather than at a review meeting. The domain owner is asked to confirm, but the design does not block on the confirmation — which is the same shape as R1-01 in the 이용기관 보고서 slice.

**Option C remains the right long-term answer and is recorded as this ADR's successor.** If the API platform owner adds a classification column, `BizTalkApiRegistry` reads it and the config list is deleted. Nothing above the interface changes.

**RISK-T05 is accepted and made visible.** Operators who have used this screen as a general API log will lose rows. The reconciliation report doubles as the evidence for that conversation: it says precisely which codes and how many transactions were excluded.

**One requirement is strengthened as a side effect.** FR-TLK-012 (return only bound columns) becomes easier to hold, because the API selector no longer needs `FT_OPENAPI_INFO`'s full row to decide what to offer — it offers the allow-list, joined to the master for display names only.

---

## 5. 구현 시점 수정 (Amendment, Sprint T1, 2026-08-19)

**Two registries became one, and the containment check became unnecessary.**

This ADR specified `BizTalkApiRegistry` (which codes are in scope) and [ADR-TLK-026](ADR-TLK-026-detail-serviceability.md) specified `TalkDetailRegistry` (what channel each is), with a **startup containment check** between them: a code with a channel but no scope entry was to fail fast.

Implementation collapsed them. One configuration entry carries `code`, `channel` and `label` **together**, so "a code with a channel but no scope entry" is **unrepresentable** — there is no second list for it to be missing from. The state the check guarded against is now excluded by the type, which is strictly better than excluding it by a check.

`channel` remains optional, so the distinction this ADR relied on survives: a code may be in scope with no message detail (a status-polling call). That is what made the merge possible without losing anything.

**What this changes in the plan.** Task T1-03 folded into T1-02. Test **TC-REG-02** (startup failure on an unmapped-but-classified code) is **retired as unreachable** rather than passing — a test for an unrepresentable state is not a passing test, and recording it as one would misstate the coverage. `BizTalkApiRegistryTest.LinkMatchesLookup` covers what it was for: the set equality between `detailAvailable` and `channelOf`.

**What this does not change.** The startup existence check against `FT_OPENAPI_INFO` — a configured code that matches nothing — is a *different* check and is **not** delivered by this merge. See the Sprint T1 log: it is superseded by the reconciliation report (T1-14), which answers the same question in both directions and against real transactions rather than against the API master.
