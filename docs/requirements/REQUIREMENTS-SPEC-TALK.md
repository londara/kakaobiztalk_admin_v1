# Requirements Specification — 톡전송 내역 (BizTalk Transaction History)

> **Version**: 1.0
> **Date**: 2026-08-19
> **Scope**: legacy screens **30 (BizTalk 내역)**, **32 (거래 상세내역)**, **31 (메시지 상세)** and the **30_spreadsheet** export
> **Predecessors**: [PROJECT-PROPOSAL.md](../planning/PROJECT-PROPOSAL.md), [BUSINESS-REQUIREMENTS.md](../planning/BUSINESS-REQUIREMENTS.md)
> **Sibling specs**: [REQUIREMENTS-SPEC.md](REQUIREMENTS-SPEC.md) (문자내역), [REQUIREMENTS-SPEC-LOGIN.md](REQUIREMENTS-SPEC-LOGIN.md) (로그인), [REQUIREMENTS-SPEC-INSTITUTION.md](REQUIREMENTS-SPEC-INSTITUTION.md) (이용기관관리), [REQUIREMENTS-SPEC-SENDERNO.md](REQUIREMENTS-SPEC-SENDERNO.md) (발신번호), [REQUIREMENTS-SPEC-REPORT.md](REQUIREMENTS-SPEC-REPORT.md) (이용기관 보고서)
> **Traceability matrix**: [requirements-matrix.csv](requirements-matrix.csv)
> **Question log**: [questions-log.md](questions-log.md) — Part 6
> **Status**: **APPROVED (G1)** — 2026-08-21, PM (AMB-T01…T05 작업 가정 수용 / working assumptions accepted)

---

## 1. Overview

This document specifies the 톡전송 내역 feature of the new IRIS BizTalk Portal, derived by static analysis of 21 legacy artifacts. As with the five earlier slices there is no runnable legacy environment (proposal RISK-001), so every requirement was recovered by reading source across five layers — view, client script, action, service contract, query — and cross-checking the layers against one another.

The slice is a three-level drill-down. Screen **30** lists the day's API transactions. Clicking a 거래고유번호 opens screen **32**, the messages dispatched under that transaction. Clicking a 메시지키 there opens screen **31**, one message's full content, attachment and failback detail. A 다운로드 button sits on screen 30.

Three properties set this slice apart from the five before it, and each drives a large part of what follows.

**The screen is not what its name says.** The menu reads 톡전송 내역 and the heading reads BizTalk 내역, but `IDO.KKB_APITR_HSTR_L001` selects from `FT_APITR_HSTR` — the shared Open-API transaction log for *every* fintech API — with no predicate restricting it to BizTalk, and the API selector is filled from `FT_OPENAPI_INFO` by an equally unrestricted query. A production screenshot supplied with this request shows rows whose API is `ADV_COM_GET_STATUS`, which is not a talk-send service at all. The gap between the label and the data is the origin of ruling **SCOPE-T01**, and it is the single decision that most changes this specification.

**It has no concept of an 이용기관.** The five earlier slices each had a tenant filter to argue about. This one has none: there is no institution input on screen 30, the query has no institution predicate, and every institution's transactions appear in one grid. Screens 32 and 31 do narrow — but 32 takes its institution from a hidden input the browser supplies, and 31 does not narrow at all. `FT_APITR_HSTR` also carries `FIN_ACNO`, `ACNO`, `CANO`, `TRAM`, `BRNO` and `RSPN_TLGR_CNTN`, so the table this screen reads holds account numbers, card numbers, transaction amounts and raw response telegrams for the whole API estate. The slice's queries select none of those columns today, and CONST-SEC-T01 makes that permanent rather than incidental.

**The 다운로드 button does not download what is on the screen.** It posts to `biztalk_admin_30_spreadsheet`, which runs `IDO.KKB_MSG_L001` against `KKO_MSG` / `KKF_MSG` and their `_LOG` archives — a different table set, a different grain, and no key in common with the grid. Worse, `fn_makeExcel()` gathers its filters from `#IS_LIST`, `#MSGKEY`, `#PHONE`, `#CALLBACK`, `#RSLT`, `#STATUS` and `#MSG_TYPE`, **none of which exist in `biztalk_admin_30_view.jsp`**. Every one resolves to the empty string, every `CASE WHEN :X = '' THEN 1=1` branch opens, and the file that arrives is every 알림톡 and 친구톡 message of every institution in the time window, with `decrypt(CALLBACK)` and `decrypt(PHONE)` and no masking anywhere. This is D-T1, the most serious finding in the slice.

**Authentication and session handling are not re-specified here.** They are settled in [REQUIREMENTS-SPEC-LOGIN.md](REQUIREMENTS-SPEC-LOGIN.md) and inherited unchanged. Unlike screens 20 and 40, every service in this slice already declares `<login>Y</login>`; what is missing is not authentication but **authorization** — which authenticated principal may see whose transactions — and that is what §2.1 specifies.

### 1.1 Legacy artifacts analyzed

| Layer | File |
|-------|------|
| View | `IRIS_ADMIN/web/view/jex/iris_admin/biztalk/biztalk_admin_30_view.jsp` (125 L) |
| View (거래 상세) | `…/biztalk_admin_32_view.jsp` (149 L) |
| View (메시지 상세) | `…/biztalk_admin_31_view.jsp` (247 L) |
| View (export) | `…/biztalk_admin_30_spreadsheet_view.jsp` (245 L) |
| Client logic | `IRIS_ADMIN_STATIC/…/biztalk/biztalk_admin_30.js` (508 L), `…_32.js` (161 L), `…_31.js` (99 L) |
| Client logic (export) | `…/biztalk_admin_30_spreadsheet.js` (64 L, **referenced by no view**) |
| Action | `biztalk_admin_30_l001_act.jsp` (목록, 67 L), `…_30_l002_act.jsp` (로그내역, 63 L), `…_30_l003_act.jsp` (API목록, 68 L), `…_30_spreadsheet_act.jsp` (다운로드, 77 L), `…_32_l001_act.jsp` (거래상세, 120 L), `…_31_l001_act.jsp` (메시지상세, 87 L) |
| Service contract | `WSVC.biztalk_admin_30`, `…_30_l001`, `…_30_l002`, `…_30_l003`, `…_30_spreadsheet`, `…_31`, `…_31_l001`, `…_32`, `…_32_l001` |
| Query (list) | `IDO.KKB_APITR_HSTR_L001` |
| Query (API combo) | `IDO.KKB_OPENAPI_INFO_L002` |
| Query (거래 상세) | `IDO.KKB_AT_MSG_L001`, `IDO.KKB_FT_MSG_L001` |
| Query (메시지 상세) | `IDO.KKO_MSG_L002`, `IDO.KKO_MSG_LOG_L002`, `IDO.KKF_MSG_L002`, `IDO.KKF_MSG_LOG_L002` |
| Query (export) | `IDO.KKB_MSG_L001` |
| Query (orphan) | `IDO.KKO_MSG_LOG_L001` — reached only by `biztalk_admin_30_l002`, which no screen calls |
| Cross-slice | `biztalk_admin_00_l001` (institution combo — `fn_getIsList()` present but its call site is commented out) |

### 1.2 Data model

| Datasource | Table | Role |
|------------|-------|------|
| `BIZTALK_DB` | `FT_APITR_HSTR` | **Open-API transaction log.** One row per API call. Key: `TRDD` + `FINTECH_ISCD` + `IS_TUNO`. Read by screen 30 |
| `BIZTALK_DB` | `FT_OPENAPI_INFO` | API master. Key: `API_CD`. Fills the API selector |
| `BIZTALK_DB` | `FT_FTIS_INFO` | Institution master, read-only, joined for 기관명 by correlated subquery. Key: `FINTECH_ISCD` |
| `BIZTALK_DB` | `KKO_MSG` / `KKO_MSG_LOG` | 알림톡 messages, live + archive. Read by screens 32 and 31 |
| `BIZTALK_DB` | `KKF_MSG` / `KKF_MSG_LOG` | 친구톡 messages, live + archive. Read by screens 32 and 31 |
| `BIZTALK_DB` | `KKB_ERRCD_INFO` | Error-code dictionary. Joined in screen 31 for 톡결과 / 문자결과 text |

`FT_APITR_HSTR`'s full column set, recovered from `IDO.FT_APITR_HSTR_C001`, is:

`TRDD, FINTECH_ISCD, IS_TUNO, FINTECH_APSNO, API_SVC_CD, API_CD, PRSU, TRPT, TXTM, FIN_ACNO, TRAM, BNCD, ACNO, FIN_CARD, CANO, INTT_DMND_TTNO, FINTECH_RSMS, FINTECH_RPCD, RSPN_TLGR_CNTN, RQTM, BANK_CPRT_DSNC, FEE_BOB_CD, BRNO, RGDT, LAST_AMDT`

Screen 30 selects nine of those twenty-five. The sixteen it does not select include every account, card, amount and telegram field. **CONST-SEC-T01** makes that exclusion a rule of the slice rather than an accident of the current SELECT list.

Two code systems coexist on one row and are easily confused, because the legacy screen uses one to filter and displays the other:

| Column | Meaning | Where it appears |
|--------|---------|------------------|
| `API_CD` | The registered API's code in `FT_OPENAPI_INFO` | The **filter** predicate, and the selector's option values |
| `API_SVC_CD` | The service code recorded on the transaction | The **displayed** API column, and the branch key in `biztalk_admin_32_l001_act.jsp` |

The two are not the same value, and nothing on the screen tells the user so (D-T15).

### 1.3 The BizTalk API code set

Ruling SCOPE-T01 restricts this slice to BizTalk APIs, which requires knowing which codes those are. A source scan of `IRIS_ADMIN` and `IRIS_ADMIN_ETC` yields exactly five literals:

`ADV_KKO_AT_SEND`, `ADV_KKO_AT_SEND2`, `ADV_KKO_AT_SEND_M`, `ADV_KKO_FT_SEND`, `ADV_KKO_FT_SEND_M`

Of these, `biztalk_admin_32_l001_act.jsp` handles only four — **`ADV_KKO_AT_SEND2` is absent from every branch** (contributing to D-T13). The authoritative set is nonetheless *data*, not code: `ADV_COM_GET_STATUS` appears in the production screenshot and in no source file, so codes exist in `FT_OPENAPI_INFO` that the codebase never names. FR-TLK-002 therefore makes the set **configuration-driven**, seeded from these five, and AMB-T03 asks the domain owner to confirm the authoritative classification.

### 1.4 Classification and priority

Per harness standard: `FR-` functional, `NFR-<area>-` non-functional, `CONST-<area>-` constraint, `UC-` use case. Priority is MoSCoW. Requirement families in this slice: `FR-AZ-T*` (access control), `FR-TLK-*` (거래내역 list, screen 30), `FR-TLKD-*` (거래 상세내역, screen 32), `FR-TLKM-*` (메시지 상세, screen 31), `FR-TLKX-*` (export).

### 1.5 PM rulings (2026-08-19)

Four questions were put to the PM before any requirement was written. All four changed the specification materially; the second is a conflict with an approved planning document and is recorded as CONFLICT-T01.

| ID | Question | Ruling |
|----|----------|--------|
| SCOPE-T01 | The screen is named for BizTalk but queries the whole Open-API log with no channel predicate | **BizTalk-only** — the list and the API selector are both restricted to BizTalk API codes. Non-talk rows visible today (e.g. `ADV_COM_GET_STATUS`) disappear; this is an accepted, visible behaviour change |
| CONFLICT-T01 | PROJECT-PROPOSAL §5.1 lists legacy 30 as a **[Tenant]** screen, but the screen has no institution filter, returns every institution's transactions, and reads a table holding account and card numbers | **Operator-only.** Screens 30/32/31 and the export are operator screens, as 이용기관 보고서 is. §5.1's `[Tenant]` label is corrected by this ruling |
| EXPORT-T01 | 다운로드 exports a different dataset than the grid, ignoring every on-screen filter and returning unmasked PII | **Export the grid's own result set** — same filters, same scope, same columns. The message-level export is not carried forward as an implicit side effect of this button |
| PII-T01 | Screens 31/32 return `decrypt()` output with no `masking()`, unlike 문자내역 | **Masked always**, in list, detail and export, for every role — matching 문자내역 and BR-007 |

### 1.6 Defect disposition

Thirty-four defects were confirmed. Consistent with AMB-01 ("fix all"), **all are fixed**. Each fix is an approved parity exception tracked in the matrix under `defect_ref`.

| ID | Sev | Defect | Disposition |
|----|-----|--------|-------------|
| D-T1 | Critical | **다운로드 exports the wrong dataset, unfiltered, with plaintext PII.** The button runs `IDO.KKB_MSG_L001` over `KKO_MSG`/`KKF_MSG` + archives — not the grid's `FT_APITR_HSTR`. `fn_makeExcel()` reads its filters from `#IS_LIST`, `#MSGKEY`, `#PHONE`, `#CALLBACK`, `#RSLT`, `#STATUS`, `#MSG_TYPE`, none of which exist in `biztalk_admin_30_view.jsp`; all resolve to `''`, every `CASE WHEN :X = ''` branch opens, and the workbook contains every institution's messages in the window with `decrypt(CALLBACK)`/`decrypt(PHONE)` and no masking | FIX → FR-TLKX-001…008 |
| D-T2 | Critical | **No institution scoping in the slice.** Screen 30 has no 이용기관 input and `KKB_APITR_HSTR_L001` has no institution predicate, so one grid shows every customer's transactions. Screen 32 takes `ID` from a hidden `pop_frm` input the browser supplies, and screen 31 takes none at all | FIX → FR-AZ-T02, FR-AZ-T03, FR-AZ-T04 |
| D-T3 | Critical | **A live, unreferenced PII endpoint.** `biztalk_admin_30_l002` is registered (`useYn=Y`, `actUseYn=Y`, `login=Y`) and runs `IDO.KKO_MSG_LOG_L001`, which returns `decrypt(CALLBACK)` and `decrypt(PHONE)` **unmasked** over an arbitrary `START_DT`–`END_DT` range, with the institution filter optional and no pagination. Its only client, `drawGrid2`/`getDat2`, is commented out — so the endpoint is reachable and exercised by nobody who would notice | FIX → FR-AZ-T06 |
| D-T4 | Critical | **Response-splitting in the export filename.** `startDt`/`endDt` reach `FILE_NM` unvalidated and `FILE_NM` is written into `Content-Disposition`. The non-IE branch only recodes the bytes (`getBytes("UTF-8"), "ISO-8859-1"`) — no encoding, no CR/LF rejection. The IE branch is safe only because it happens to call `URLEncoder.encode` | FIX → FR-TLKX-003, NFR-SEC-HDR-T01 |
| D-T5 | Critical | **The message-detail service is addressable without an institution.** `IDO.KKO_MSG_L002` keys on `REQDATE` + `STATUS` + `MSGKEY` only. Any authenticated caller holding or guessing a message key reads another institution's message body, template code, sender and recipient number | FIX → FR-AZ-T04 |
| D-T6 | High | **Detail screens return plaintext PII.** `KKB_AT_MSG_L001`, `KKB_FT_MSG_L001`, `KKO_MSG_L002` and siblings all apply `decrypt()` with no `masking()`. The 문자내역 slice applied `decrypt()` then `masking()` on the same columns — this slice is inconsistent with its own sibling and with BR-007 | FIX → FR-TLKD-008, FR-TLKM-002, NFR-SEC-PII-T01 |
| D-T7 | High | **친구톡 rows are labelled 알림톡.** `IDO.KKB_FT_MSG_L001` selects `'AT' AS MSG_TYPE` — copied from the AT query and never changed. The 유형 column is wrong on screen 32, and `biztalk_admin_31_l001_act.jsp` branches on that same value, so opening a 친구톡 message queries `KKO_MSG_L002` instead of `KKF_MSG_L002` and returns nothing | FIX → FR-TLKD-004, FR-TLKM-006 |
| D-T8 | High | **친구톡 detail filters are silently dropped.** `biztalk_admin_32_l001_act.jsp` builds an `IDODynamic` for 수신번호/상태/톡결과/문자결과 and puts it as `DYNAMIC_0`, but `IDO.KKB_FT_MSG_L001` has no `??` placeholder and no `DYNAMIC_0` in its `<in>`. For 친구톡 the four filters do nothing and the user is not told | FIX → FR-TLKD-002 |
| D-T9 | High | **The transaction-number lookup truncates.** The action passes `StringUtils.stripStart(serialNum, "0")` and `KKB_AT_MSG_L001` matches `SERIALNUM = LPAD(:SERIALNUM,10,'0')`. PostgreSQL's `lpad` **truncates** an input longer than the target width, so a 20-character 거래고유번호 such as `00000026081900142813` becomes `26081900142813`, then `2608190014` — matching the wrong row or none. The 친구톡 branch uses a bare `SERIALNUM = :SERIALNUM` against the stripped value, a third rule again | FIX → FR-TLKD-009, FR-TLK-009 |
| D-T10 | High | **Paged output has no deterministic order.** `ORDER BY RGDT DESC` with no tiebreaker, under server-side paging. The supplied screenshot shows eleven rows sharing `2026-08-19 11:25:04`, so ties are the normal case here, not an edge case: rows can repeat on one page and vanish from another | FIX → FR-TLK-006 |
| D-T11 | High | **The list returns no total count.** `biztalk_admin_30_l001_act.jsp` calls `DomainUtil.setIDOPageInfo` but never reads the result count back, and `WSVC.biztalk_admin_30_l001` declares only `REC1` in `<out>`. Its own sibling `biztalk_admin_32_l001_act.jsp` does exactly the right thing — `DomainUtil.getMaxResultCount` into a declared `TOT_CNT` — so the correct pattern existed in the same folder and was not applied | FIX → FR-TLK-005 |
| D-T12 | High | **The export is unbounded.** No pagination, no row cap, no period cap, `XSSFWorkbook` fully materialised before the first byte is written, over a four-way `UNION ALL` that calls `decrypt()` twice per row. An OOM whose likelihood scales directly with D-T1 and D-T24 | FIX → FR-TLKX-005, NFR-SCALE-T01 |
| D-T13 | High | **The 상세 link and the detail service disagree about which rows have detail.** The grid renders a link when `API_SVC_CD.indexOf("KKO") != -1 && PRSU == 1`; the action serves only four exact codes (`ADV_KKO_AT_SEND`, `_M`, `ADV_KKO_FT_SEND`, `_M`), omitting `ADV_KKO_AT_SEND2`. A link on any other KKO row opens a popup that renders an empty grid with no message. Conversely no link is offered for 처리중 or 오류 rows — the rows an operator investigating a failure most needs | FIX → FR-TLK-013, FR-TLKD-005 |
| D-T14 | High | **The export bypasses its own contract.** `WSVC.biztalk_admin_30_spreadsheet` declares twelve `<in>` items, and `biztalk_admin_30_spreadsheet_act.jsp` reads all ten of the ones it uses straight from `request.getParameter`, bypassing every declared length and `fullChar` rule — which is also how D-T4 becomes reachable | FIX → FR-TLKX-002 |
| D-T15 | Medium | **Filter and column are different code systems.** The predicate is `API_CD = :API_CD` and the selector's values come from `FT_OPENAPI_INFO.API_CD`, while the grid displays `API_SVC_CD`. A user cannot tell which value the filter matched | FIX → FR-TLK-010 |
| D-T16 | Medium | **The export view declares the wrong service.** `biztalk_admin_30_spreadsheet_view.jsp` carries `@JexDataInfo(id="biztalk_admin_20_spreadsheet", …)` — the 이용기관 보고서 export's contract, copied and not changed | FIX → FR-TLKX-010 |
| D-T17 | Medium | **Five-digit year.** `to_char(REQDATE,'YYYYY-MM-DD HH24:MI:SS')` in `KKO_MSG_L002` and `KKF_MSG_L002`, on all four timestamp fields of screen 31. Same class as D5 in 문자내역 | FIX → FR-TLKM-003 |
| D-T18 | Medium | **발신번호 and 수신자번호 never render on screen 31.** `KKO_MSG_L002`/`KKF_MSG_L002` select `decrypt(CALLBACK), decrypt(PHONE)` with **no aliases**, so PostgreSQL names both output columns `decrypt`, while the contract's `<out>` expects `CALLBACK` and `PHONE`. Two of the fields the popup exists to show are blank | FIX → FR-TLKM-002 |
| D-T19 | Medium | **The detail key includes a mutable column.** `AND STATUS = :STATUS` is part of screen 31's `WHERE`. A message whose status advanced between the list query and the click returns zero rows, and the popup renders blank with no message | FIX → FR-TLKM-004 |
| D-T20 | Medium | **NULL propagation in 톡결과 / 문자결과.** Both are built as `RSLT \|\| '(' \|\| (SELECT ERR_MSG …) \|\| ')'`. An error code absent from `KKB_ERRCD_INFO` makes the subquery NULL, which makes the whole concatenation NULL — so an *unrecognised* failure code displays as empty, exactly when the operator most needs to see it | FIX → FR-TLKM-005 |
| D-T21 | Medium | **수신번호 cannot be typed in full.** `biztalk_admin_32_view.jsp` sets `maxlength="10"` on `#PHONE_NUM`; Korean mobile numbers are 11 digits | FIX → FR-TLKD-003 |
| D-T22 | Medium | **The 실패 filter hides in-flight rows.** 톡결과=실패 becomes `AND RSLT != '0'`, and SQL three-valued logic excludes rows where `RSLT` is NULL — a message that has not yet reported is neither 성공 nor 실패 and appears under neither filter | FIX → FR-TLKD-006 |
| D-T23 | Medium | **The export has nowhere to land.** `fintech.common.submit($("#frm0"), "…", "ifrmFileProc")` only sets `target` and submits — it does not create a frame — and no view in the biztalk folder nor `inc_0001_01.jsp` declares `ifrmFileProc`. The browser opens a new top-level window instead, and `frm0` keeps `target="ifrmFileProc"` afterwards, so the next 상세 popup submit inherits it | FIX → FR-TLKX-006 |
| D-T24 | Medium | **No period cap and no server-side date validation.** The only check is a client-side seconds-of-day comparison in `getDat()`. Neither `WSVC.biztalk_admin_30_l001` nor the action validates `TRDD`, `START_TIME` or `END_TIME`, and a blank 종료시각 becomes the sentinel `999999` — not a valid time, and correct only because `RGDT` is compared as a character string | FIX → FR-TLK-007, FR-TLK-008, FR-TLK-014 |
| D-T25 | Medium | **거래일련번호 is normalised three different ways.** The list pads to 20 with `padStart(20,'0')` and matches exactly; the AT detail strips leading zeros then `LPAD(…,10,'0')`; the FT detail strips and matches raw. One identifier, three rules | FIX → FR-TLK-009 |
| D-T26 | Medium | **기관명 is a correlated subquery per row and fails silently.** `(SELECT B.ISNM FROM FT_FTIS_INFO B WHERE A.FINTECH_ISCD = B.FINTECH_ISCD)` runs once per result row and yields NULL for an unmatched code, rendering a blank cell with no indication the lookup failed. Same class as D-R12/D-R13 | FIX → FR-TLK-011, NFR-PERF-T01 |
| D-T27 | Medium | **The API selector over-fetches.** `KKB_OPENAPI_INFO_L002` returns 21 columns per API — including `RGSR_ID`, `RGSR_NM`, `LSED_ID`, `LSED_NM`, the operators who registered and last edited each API — to populate a dropdown that binds two of them | FIX → FR-TLK-012 |
| D-T28 | Medium | **The screen queries before its own selector has loaded.** `onload` calls `_me.getDat()` and only then `fn_fintechSvcSel()`. Same ordering as D-S19 and D-R23 | FIX → FR-TLK-015 |
| D-T29 | Medium | **The 상태 filter and the 상태 column come from different sources.** The radio group is generated by `CodeUtil.makeRadio(request, "PRSU", …)` from code group `PRSU`, while the grid renderer hardcodes `0`/`1`/`2`/`9` and renders anything else as `알수없음`. A code added to the table becomes filterable and unreadable in the same release | FIX → FR-TLK-004 |
| D-T30 | Low | `biztalk_admin_32_l001` computes and returns `TOT_CNT`, and `biztalk_admin_32.js` never reads it, using `_guPop32.getTotSize()` instead | FIX → FR-TLKD-007 |
| D-T31 | Low | **Dead code.** `drawGrid2`/`getDat2`/`_gu2`/`queTableStatus` commented out but their service (`30_l002`) left live (see D-T3); `fn_getIsList()` defined, its call site commented out, targeting a `#IS_LIST` element that does not exist — and it is the operator-only institution-enumeration endpoint; `fn_getLogDetail`, `getCntsList`, `cntsPreviewFn` unreferenced; `getDat()` reads `#STATUS` and `#MSG_TYPE`, absent from this view, into variables it never uses; `var num = new Number()` unused, twice; hidden `PARAM_4` never read; a full commented-out `gridColName`/`colDefs` block; `biztalk_admin_30_spreadsheet.js` referenced by no view | FIX → §2.8 |
| D-T32 | Low | `fn_makeExcel()` writes to `$("#frm0 #EXCEL_RSLT")`, but the element's id is `EXCEL_RLST`. The 결과 value is never set — one of the few filters that could have narrowed D-T1 | FIX → FR-TLKX-001 |
| D-T33 | Low | `biztalk_admin_31.js` calls `$("FAILED_MSG").prop("disabled", true)` — missing `#`, so the selector matches nothing and the FailBack textarea on a read-only detail popup stays editable, unlike its two siblings | FIX → FR-TLKM-007 |
| D-T34 | Low | Copy-paste labels and cosmetics: `biztalk_admin_31_view.jsp` is titled `기본 컨텐츠 관리` and its `@Javascript Path` header reads `biWztalk`; the export sets a content type four times inconsistently (page directive `application/vnd.ms-excel; name='excel',text/html`, then `application/vnd.ms-excel` on the non-IE branch only, then `application/download; UTF-8`, then `application/octet-stream`) for a file that is `.xlsx`; every export header cell is created twice at the same index; `fn_getDetail('…','"+datarow+"')` interpolates an object into an onclick attribute, yielding `[object Object]`, and the argument is unused; the 요청일자 row omits `</td>` and spans `colspan="4"` against a 6-column colgroup | FIX → FR-TLKX-004, FR-TLKM-008, §2.8 |

---

## 2. Functional Requirements

### 2.1 Access control and scope

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| FR-AZ-T01 | Every service in the slice — list, API selector, 거래 상세, 메시지 상세, export — rejects unauthenticated requests. The legacy already declares `<login>Y</login>` on all of them; this requirement makes it a tested property rather than a configuration value | Must | Security test (endpoint inventory) |
| FR-AZ-T02 | 톡전송 내역 and its two detail screens and export are **operator-role screens**. A tenant principal receives 403 on every service and no menu entry is rendered. Per CONFLICT-T01 | Must | Security test + E2E per role |
| FR-AZ-T03 | The 이용기관 of a 거래 상세 request is **re-derived on the server** from the selected transaction key (`TRDD` + `IS_TUNO`), never taken from the request body. Fixes D-T2, where `ID` came from a hidden `pop_frm` input | Must | Security test (tampered institution code must not widen the result) |
| FR-AZ-T04 | The 메시지 상세 service is addressed by a key that **includes the owning institution**. A message key alone is not sufficient to retrieve a message. Fixes D-T5 | Must | Security test (cross-institution message key returns 404) |
| FR-AZ-T05 | Every list query, every detail open and every export writes a business audit record carrying actor, timestamp, the filters applied, the institution scope and the row count returned. `mntLogYn=Y` yields only a Jex service-monitor record, which disappears with Jex (BR-002…005) | Must | Integration test |
| FR-AZ-T06 | The `biztalk_admin_30_l002` capability is **not carried forward**. No endpoint in the new application returns unmasked recipient or sender numbers over a date range. Fixes D-T3 | Must | Security test (endpoint inventory) + code review |

### 2.2 거래내역 list (screen 30)

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| FR-TLK-001 | Operators search transactions by 요청일자 (required), 요청시각 범위, 거래일련번호, 상태, API서비스, and 이용기관 | Must | E2E test |
| FR-TLK-002 | The list and the API selector are restricted to **BizTalk API codes**. The set is configuration-driven — seeded from `ADV_KKO_AT_SEND`, `ADV_KKO_AT_SEND2`, `ADV_KKO_AT_SEND_M`, `ADV_KKO_FT_SEND`, `ADV_KKO_FT_SEND_M` (§1.3) — and changing it requires no code change. Per SCOPE-T01 | Must | Integration test (a non-talk transaction must not appear) |
| FR-TLK-003 | The grid presents 9 columns: 일자, 기관코드, 기관명, 거래고유번호, API, 상태, 응답코드, 등록시각, 완료시각 | Must | E2E test |
| FR-TLK-004 | 상태 (`PRSU`) is displayed as `0`=처리중, `1`=처리완료, `2`=기처리, `9`=오류, with an unmapped value shown verbatim rather than as 알수없음. The label set and the filter's option set are **read from one source**, so they cannot diverge. Fixes D-T29; consistent with FR-MSG-005 | Must | Unit test (filter options and column labels enumerate identically) |
| FR-TLK-005 | The list is **server-side paginated** and the response carries the page slice plus a total count. Fixes D-T11 | Must | Integration + load test |
| FR-TLK-006 | Result order is **total and deterministic**: 등록시각 desc, then 거래고유번호 as a unique tiebreaker. No row may appear on two pages or on none. Fixes D-T10 | Must | Integration test (paginate a tied-timestamp fixture; the union of pages equals the full set exactly once) |
| FR-TLK-007 | 요청일자 is a **range** with a server-enforced cap of **31 days**, defaulting to a single day. The legacy predicate `TRDD = :TRDD` permits one day only. Cap value per AMB-T02 (working assumption, aligned with AMB-06) | Must | Integration test (32 days rejected) |
| FR-TLK-008 | 요청시각 bounds are real times; an omitted bound means the whole day. The `999999` sentinel is removed and range comparison does not depend on `RGDT` being stored as a character column. Fixes D-T24 | Must | Unit test |
| FR-TLK-009 | 거래일련번호 is normalised by **one rule**, applied identically in the list, both detail screens and the export: the value is zero-padded to the stored width and matched exactly; no strip-then-pad path exists. Fixes D-T25 (and, with FR-TLKD-009, D-T9) | Must | Unit test (padded, unpadded and over-length inputs) |
| FR-TLK-010 | The API filter and the API column are **the same code system**. Whichever code is displayed is the code the filter matches, and the selector's option values are that code. Fixes D-T15 | Must | Integration test (filter by the displayed value; the row is returned) |
| FR-TLK-011 | 기관명 is resolved by a join, and an unresolved 기관코드 renders the code with an explicit marker rather than a blank cell. Fixes D-T26 | Must | Unit test + integration test |
| FR-TLK-012 | The list response carries **only** the columns the grid binds. The API selector returns 코드 and 명 alone. Fixes D-T27 | Must | Contract test (response field set is exact) |
| FR-TLK-013 | 상세 availability is derived from the **same rule the server applies**: a row offers a detail link if and only if the detail service can serve it, and that includes 처리중 and 오류 rows. Fixes D-T13 | Must | E2E test (every rendered link returns rows or an explicit message; no served row lacks a link) |
| FR-TLK-014 | 시작시각 > 종료시각, a malformed date, and an over-cap range are all rejected **server-side**, not only in the browser. Fixes D-T24 | Must | Integration test (direct service call bypassing the UI) |
| FR-TLK-015 | The screen issues no query until the API selector has loaded and the caller's scope is known. Fixes D-T28 | Should | E2E test |

### 2.3 거래 상세내역 (screen 32)

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| FR-TLKD-001 | Selecting a transaction opens its message list, with 거래일자, 이용기관, 거래번호 and API shown as context and 14 columns beneath: 유형, 거래번호, 메시지키, 이용기관, 상태, 톡결과, 문자결과, 발송번호, 수신번호, 요청일자, 요청시간, 발송시간, 응답시간, 테이블 | Must | E2E test |
| FR-TLKD-002 | The 수신번호, 상태, 톡결과 and 문자결과 filters apply to **알림톡 and 친구톡 alike**. Fixes D-T8, where the 친구톡 query had no placeholder for them and dropped all four silently | Must | Integration test per channel |
| FR-TLKD-003 | The 수신번호 filter accepts a full-length recipient number (11 digits). Fixes D-T21 | Must | E2E test |
| FR-TLKD-004 | 유형 reflects the **actual channel**: 친구톡 rows are labelled FT. Fixes D-T7 | Must | Unit test per channel |
| FR-TLKD-005 | A transaction whose API service has no detail mapping produces an explicit "상세 조회를 지원하지 않는 거래" response, never a silently empty grid. Fixes D-T13 | Must | Integration test (`ADV_KKO_AT_SEND2` and an unmapped code) |
| FR-TLKD-006 | The 톡결과 / 문자결과 filters partition the result set completely: a row with no result yet is reachable under an explicit 미수신 option and is not silently excluded by 실패. Fixes D-T22 | Must | Unit test (null result code) |
| FR-TLKD-007 | The detail list is paginated with a server-supplied total count, and the client uses that count. Fixes D-T30 | Must | Integration test |
| FR-TLKD-008 | 발송번호 and 수신번호 are **masked** in the detail list for every role. Per PII-T01; fixes D-T6 | Must | Unit test + security test |
| FR-TLKD-009 | The transaction number → message serial lookup is **lossless** for identifiers of any length. Fixes D-T9, where `stripStart` followed by `LPAD(…,10,'0')` truncated a 20-character 거래고유번호 to ten | Must | Unit test (10-, 14- and 20-character serials) |

### 2.4 메시지 상세 (screen 31)

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| FR-TLKM-001 | The message detail presents three groups: **메시지정보** (프로필, 광고여부, 톡결과, 문자결과, 템플릿코드, 발신번호, 수신자번호, 요청시간, 송신시간, 통신사응답시간, 결과수신시간, 전송메시지), **첨부** (이미지경로, 이미지URL, 와이드 이미지 여부, 버튼JSON) and **FailBack** (문자전송타입, 문자전송제목, 문자내이미지주소, 문자내용) | Must | E2E test |
| FR-TLKM-002 | 발신번호 and 수신자번호 **render**, masked. Fixes D-T18 (two unaliased `decrypt()` columns collided, so both fields were always blank) and D-T6 | Must | Integration test + unit test |
| FR-TLKM-003 | All four timestamps render a 4-digit year. Fixes D-T17 | Must | Unit test |
| FR-TLKM-004 | The message is addressed by a **stable key** that contains no mutable column. Fixes D-T19, where `STATUS` was part of the key and a status change between list and click emptied the popup | Must | Integration test (change status between list and detail) |
| FR-TLKM-005 | 톡결과 and 문자결과 always display the **result code**, with the descriptive text appended when the code is known and an explicit "미등록 코드" marker when it is not. A missing dictionary entry never blanks the field. Fixes D-T20 | Must | Unit test (known code, unknown code, null code) |
| FR-TLKM-006 | The channel's own tables are queried — 친구톡 detail reads the 친구톡 tables. Fixes D-T7's downstream effect | Must | Integration test per channel × live/archive |
| FR-TLKM-007 | Every field in all three groups is either populated or explicitly marked absent, and no field on this read-only screen is editable. Fixes D-T33 | Must | E2E test |
| FR-TLKM-008 | The screen is titled for what it shows. Fixes D-T34 | Could | Review |

### 2.5 Export

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| FR-TLKX-001 | The export produces **the grid's own result set** — the same query, the same filters, the same institution scope and the same columns as the screen shows. Per EXPORT-T01; fixes D-T1 and D-T32 | Must | Integration test (export row set equals the paged list row set, filter for filter) |
| FR-TLKX-002 | Every export parameter passes through the declared service contract with its declared length and character rules. No parameter is read from the raw request. Fixes D-T14 | Must | Code review + security test |
| FR-TLKX-003 | No user-supplied value reaches a response header unencoded, and CR/LF is rejected outright. Fixes D-T4 | Must | Security test |
| FR-TLKX-004 | The response declares one content type, correct for the format produced. Fixes D-T34 | Must | Integration test |
| FR-TLKX-005 | The workbook is generated with **bounded memory** independent of row count, and the export is subject to the same period cap as the list plus an explicit row cap. Fixes D-T12 | Must | Load test |
| FR-TLKX-006 | Export failure produces a **visible error** to the user. Fixes D-T23, where the form targeted a browsing context no view declares | Must | Integration test (forced failure) |
| FR-TLKX-007 | Every export is audited, including the row count actually written | Must | Integration test |
| FR-TLKX-008 | Recipient and sender numbers are **masked in the exported file** exactly as on screen. Per PII-T01 | Must | Integration test (inspect the produced workbook) |
| FR-TLKX-009 | Each header cell is written once, and the title merge spans the produced table's column count. Fixes D-T34 | Could | Integration test (workbook geometry) |
| FR-TLKX-010 | The export declares its **own** service contract. Fixes D-T16, where it declared `biztalk_admin_20_spreadsheet` | Must | Code review |

### 2.6 Non-functional requirements

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| NFR-PERF-T01 | List query P95 < 3 s at the 31-day cap, 100 rows per page (AMB-08 precedent). The legacy resolves 기관명 by a correlated subquery per row (D-T26) | Must | Load test |
| NFR-PERF-T02 | 거래 상세 and 메시지 상세 P95 < 1 s (AMB-08 precedent) | Must | Load test |
| NFR-SEC-AUTHZ-T01 | No anonymous or unauthorized endpoint in the slice; the operator-role check is enforced server-side on every service, not by menu visibility | Must | Security test (endpoint inventory) |
| NFR-SEC-TENANT-T01 | No institution's transactions, messages or message bodies are reachable outside the FR-AZ-T02 scope by any parameter on any service in the slice | Must | Security test (tamper institution code and message key) |
| NFR-SEC-PII-T01 | 수신번호 and 발신번호 are masked in list, detail and export, and are never written unmasked to an application log. Per PII-T01 and BR-007 | Must | Unit test + security test + log inspection |
| NFR-SEC-HDR-T01 | No user-supplied value reaches a response header unencoded | Must | Security test + code review |
| NFR-SCALE-T01 | Export heap usage is bounded and independent of row count | Must | Load test (heap flat at 1k vs 100k rows) |
| NFR-OPS-AUDIT-T01 | Transaction-history read, detail-open and export events are retained for the statutory term with tamper-evident integrity | Must | Integration test + review |
| NFR-USE-T01 | Coded values (상태, 톡결과, 문자결과, 응답코드) are displayed as label **and** raw code, so an operator can quote the code to a provider without translating back | Should | E2E test |
| NFR-COMPAT-T01 | The exported workbook opens without repair in Excel 2016+, LibreOffice Calc and Google Sheets | Should | Manual verification per target |

### 2.7 Constraints

| REQ-ID | Constraint | Priority | Verification |
|--------|-----------|----------|--------------|
| CONST-DATA-T01 | `FT_APITR_HSTR`, `KKO_MSG`/`KKF_MSG` and their archives are written by the API gateway and the send pipeline. This slice is **read-only** and never writes, corrects or backfills a transaction or message row | Must | Code review (no write path) |
| CONST-DATA-T02 | No DDL, inheriting CONST-DATA-01 | Must | Review |
| CONST-SEC-T01 | `FT_APITR_HSTR` carries `FIN_ACNO`, `ACNO`, `CANO`, `FIN_CARD`, `TRAM`, `BRNO`, `INTT_DMND_TTNO` and `RSPN_TLGR_CNTN`. This slice's queries **must never select them**, and no response, export or log may contain them | Must | Code review + contract test (response field set is exact) |
| CONST-LEGAL-T01 | 수신번호 and 발신번호 are personal information under 개인정보보호법: masked in every rendering, protected at rest, and every access audited | Must | Security test + review |
| CONST-BIZ-T01 | The three levels are a strict hierarchy — 거래 (`FT_APITR_HSTR`) → 메시지 (`KKO_MSG`/`KKF_MSG`) → 메시지 본문. A detail level is reachable only through its parent, never by a bare key | Must | Security test |

### 2.8 Cleanups without a numbered requirement

The dead code inventoried in D-T31 and the cosmetic defects in D-T34 are removed rather than ported: the commented-out second grid and its service, `fn_getIsList`, `fn_getLogDetail`, `getCntsList`, `cntsPreviewFn`, the unread `#STATUS`/`#MSG_TYPE` reads, `PARAM_4`, the duplicated header-cell writes, the `[object Object]` onclick interpolation, and the unreferenced `biztalk_admin_30_spreadsheet.js`. `biztalk_admin_30_l002` is not merely unwired but **deleted** — see FR-AZ-T06.

---

## 3. Use Cases

| UC-ID | Title | Screens | Primary FR |
|-------|-------|---------|-----------|
| [UC-TLK-001](use-cases/UC-TLK-001.md) | 톡전송 거래내역 조회 | 30 | FR-AZ-T01/T02/T05, FR-TLK-001…015 |
| [UC-TLK-002](use-cases/UC-TLK-002.md) | 거래 상세내역 조회 | 32 | FR-AZ-T03, FR-TLKD-001…009 |
| [UC-TLK-003](use-cases/UC-TLK-003.md) | 메시지 상세 조회 | 31 | FR-AZ-T04, FR-TLKM-001…008 |
| [UC-TLK-004](use-cases/UC-TLK-004.md) | 거래내역 다운로드 | 30_spreadsheet | FR-TLKX-001…010 |

Every FR, NFR and CONST in this document maps to at least one use case; the matrix is the authority. Orphan count: **0**.

---

## 4. Traceability

`requirements-matrix.csv` carries one row per requirement with `REQ_ID, type, requirement, source, priority, verification, use_case, business_rule, defect_ref, status`. This slice adds **63 rows**: 6 `FR-AZ-T*`, 15 `FR-TLK-*`, 9 `FR-TLKD-*`, 8 `FR-TLKM-*`, 10 `FR-TLKX-*`, 10 `NFR-*-T*`, 5 `CONST-*-T*`. Every row names its legacy source artifact, and every defect-driven row names its `D-T*`.

---

## 5. Conflicts

### 5.1 CONFLICT-T01 — PROJECT-PROPOSAL §5.1 vs. what screen 30 is

**Conflict.** The approved proposal classifies legacy 30 (거래내역조회) as a **[Tenant]** screen, one of the four client-facing menus that carry the self-service value proposition. Static analysis shows the screen has no institution input, no institution predicate, and reads a table holding account numbers, card numbers and transaction amounts for the entire API estate. Building it as a tenant screen would mean inventing tenant scoping the legacy never had, on the most sensitive table in the slice, for an internet-facing audience.

**Raised** under harness §3 (충돌 식별 → PM 결재 의무) before any requirement was written.

**PM ruling (2026-08-19).** **Operator-only.** §5.1's `[Tenant]` label is corrected. Screens 30/32/31 and the export join 이용기관 보고서 as operator screens.

**Effect.** FR-AZ-T02 and NFR-SEC-AUTHZ-T01. This narrows the MVP's tenant-facing surface from four legacy menus to three (40, 50, 60); the proposal's §4 core scenario 3 (거래내역조회 / 문자내역) is served by the 문자내역 slice alone. AMB-02 as refined by CONFLICT-R01 is not overturned — it governs tenant principals, and this slice now has none.

### 5.2 CONFLICT-T02 — the screen's name vs. the screen's data

**Conflict.** Not a conflict between two documents but between a menu label and the query beneath it: 톡전송 내역 over the unrestricted Open-API transaction log. Left unresolved, "parity" is undefined — reproducing the query contradicts the name, and honouring the name changes the result set.

**PM ruling (2026-08-19).** **BizTalk-only** (SCOPE-T01). Rows visible today whose API is not a talk service will disappear from the rebuilt screen. This is an accepted, visible deviation from literal parity, tracked as such.

**Effect.** FR-TLK-002, and AMB-T03 to confirm the authoritative code set with the domain owner.

---

## 6. Risks

| ID | Risk | Likelihood | Impact | Mitigation |
|----|------|-----------|--------|-----------|
| RISK-T01 | The BizTalk API code set is data, not code (§1.3). A code the classification misses silently drops real transactions from an operator's view — a *quieter* failure than the over-inclusion it replaces | Medium | High | FR-TLK-002 makes the set configuration-driven; AMB-T03 confirms it with the domain owner; a reconciliation check reports transactions excluded by classification |
| RISK-T02 | D-T1's export has been reachable in production for the life of the screen. Files already extracted contain unmasked recipient numbers across all institutions | Medium | High | Retroactive remediation is outside this slice's remit — raised to 정보보호 as OI-T01 |
| RISK-T03 | `FT_APITR_HSTR` is shared with every other API module. A query-shape or index change made for this screen affects unrelated consumers | Medium | Medium | Read-only (CONST-DATA-T01); index proposals reviewed with the API platform owner before G2 |
| RISK-T04 | The 거래 → 메시지 join (`IS_TUNO` → `SERIALNUM`) is not a declared foreign key and its normalisation is inconsistent three ways (D-T9, D-T25). The correct rule is inferred, not documented | High | Medium | FR-TLK-009 fixes one rule; AMB-T04 asks the domain owner to confirm the true relationship before implementation |
| RISK-T05 | Removing non-talk rows (SCOPE-T01) may break an operator workflow that has quietly depended on this screen as a general API log | Medium | Medium | Communicated at cutover; if it materialises, the general log is a separate screen, not a widening of this one |

---

## 7. Open items

| ID | Question | Candidates | Working assumption | Owner | Needed by |
|----|----------|-----------|--------------------|-------|-----------|
| AMB-T01 | Should the message-level export dropped by EXPORT-T01 return as its own function? | A: no · B: yes, on screen 32 with its own filters and masking | A — not built unless requested | PM | Skill 3 |
| AMB-T02 | Period cap for the 요청일자 range | A: 31 days (AMB-06 precedent) · B: 92 days · C: keep single-day parity | A — 31 days | PM | Skill 3 |
| ~~AMB-T03~~ | ~~Which `API_CD` values constitute BizTalk?~~ | ~~A: the five literals · B: a classification column~~ | **RESOLVED at Skill 3** — configuration-held allow-list, startup-validated, plus a standing reconciliation report ([ADR-TLK-024](../design/adr/ADR-TLK-024-biztalk-api-classification.md), task T1-14). The DDL answer is recorded as this decision's successor | `architect` | ✅ closed 2026-08-19 |
| AMB-T04 | Is `FT_APITR_HSTR.IS_TUNO` genuinely the same identifier as `KKO_MSG.SERIALNUM`, and at what stored width? Three different normalisations exist in the legacy (D-T9, D-T25) | A: same identifier, one width · B: related but not equal — needs a mapping | A — **converted to a measurement**: task T1-01 fixes the widths and the join cardinality in week one ([ADR-TLK-025](../design/adr/ADR-TLK-025-transaction-message-identity.md)) | `data-model-designer` | Sprint T1 — **blocks FR-TLKD-009**, does not block G2 |
| ~~AMB-T05~~ | ~~Does 처리중 / 오류 detail exist to be shown?~~ | — | **DISSOLVED at Skill 3** — [ADR-TLK-026](../design/adr/ADR-TLK-026-detail-serviceability.md) renders both answers correctly, so the screen observes the fact rather than depending on it | `architect` | ✅ closed 2026-08-19 |
| AMB-T06 | What is the relationship between `KKO_MSG`/`KKF_MSG` and the 문자내역 slice's `KKO_SMS_MSG`/`KKO_MMS_MSG`/`KKF_SMS_MSG`/`KKF_MMS_MSG`? Design found the two slices read **disjoint** table families — twelve message tables, no overlap | A: failback relationship joined by `SERIALNUM` · B: independent record sets | A | Domain owner | Not blocking — needed only for a future cross-channel search ([ADR-TLK-027](../design/adr/ADR-TLK-027-sibling-reuse-boundary.md)) |
| OI-T01 | Historical exposure from D-T1 — files already extracted contain unmasked cross-institution PII | — | Raised to 정보보호; retroactive remediation is out of slice | 정보보호 | Before G3 (RISK-T02) |

Carried from Skill 01 and still open: **OI-02** (audit retention term) blocks NFR-OPS-AUDIT-T01.

---

## 8. Definition of Done

- [x] Every requirement carries a REQ-ID, a source, a priority and a verification method
- [x] Orphan requirements: 0 — every FR/NFR/CONST maps to ≥ 1 use case
- [x] Conflicts identified and ruled on before requirements were written (CONFLICT-T01, CONFLICT-T02)
- [x] `[AMBIGUOUS]` items either ruled (SCOPE-T01, CONFLICT-T01, EXPORT-T01, PII-T01) or recorded with a working assumption (AMB-T01…T05)
- [x] Traceability matrix updated — 63 rows
- [x] 금융권 점검 §7: NFR-SEC-PII-T01 (PII), NFR-SEC-AUTHZ-T01 / NFR-SEC-TENANT-T01 (인증·인가), NFR-OPS-AUDIT-T01 (감사 로그) written
- [x] **G1 분석 게이트 통과** — 2026-08-21, PM 결재. AMB-T01…T05 의 작업 가정을 그대로 **수용**한다. 구현(Sprint T1·T2)이 결재에 선행한 **사후 결재** / Approved 2026-08-21; the AMB-T01…T05 working assumptions are accepted as recorded. Retrospective — Sprints T1/T2 preceded this signature
