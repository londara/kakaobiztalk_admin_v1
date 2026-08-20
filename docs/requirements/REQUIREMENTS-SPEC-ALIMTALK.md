# Requirements Specification — 카카오 알림톡 템플릿 / 발송 (AlimTalk Compose · Send · Template Validation)

> **Version**: 1.0
> **Date**: 2026-08-18
> **Scope**: legacy screen **61 (카카오 알림톡 템플릿)** — payload composer and template validator — together with the send path it composes for, **screen 50 (BIZTALK 전송)**, which enters scope by PM ruling AMB-A00
> **Predecessors**: [PROJECT-PROPOSAL.md](../planning/PROJECT-PROPOSAL.md), [BUSINESS-REQUIREMENTS.md](../planning/BUSINESS-REQUIREMENTS.md)
> **Sibling specs**: [REQUIREMENTS-SPEC.md](REQUIREMENTS-SPEC.md) (문자내역), [REQUIREMENTS-SPEC-LOGIN.md](REQUIREMENTS-SPEC-LOGIN.md) (로그인), [REQUIREMENTS-SPEC-INSTITUTION.md](REQUIREMENTS-SPEC-INSTITUTION.md) (이용기관관리), [REQUIREMENTS-SPEC-SENDERNO.md](REQUIREMENTS-SPEC-SENDERNO.md) (발신번호)
> **Traceability matrix**: [requirements-matrix.csv](requirements-matrix.csv)
> **Question log**: [questions-log.md](questions-log.md) — Part 5
> **Status**: **APPROVED — G1, PM, 2026-08-19** (see §6.4)

---

## 1. Overview

This document specifies the 알림톡 (AlimTalk) composition, sending and template-validation feature of the new IRIS BizTalk Portal, derived by static analysis of legacy screen 61 and the interface contracts it composes against. As with the previous four slices there is no runnable legacy environment (proposal RISK-001), so every requirement was recovered by reading source.

Legacy screen 61 is registered as `BIZTALK(템플릿 샘플)` — a *template sample*. Read on its own it looks trivial: three tabs, no server call, `actUseYn=N`, and a "JSON 생성" button that writes into a read-only textarea for the operator to copy by hand. It sends nothing and stores nothing.

**Read against its consumer, it is something else entirely.** The JSON it composes is the request body of `IMO.ADV_KKO_AT_SEND` — the outbound AlimTalk interface toward the vendor endpoint `COOCON_ALERT /advising/kakao/at_send`. That contract is authoritative, declares every field and every field length, and **the composer does not match it.** The failback block is emitted under the wrong key, five emitted fields do not exist in the contract at all, one mandatory contract field is never emitted, and not one of the contract's declared lengths is enforced anywhere in the screen. A payload built by this screen and pasted into the send path is not a valid request; it is a request that loses its SMS fallback silently.

This finding is the direct application of the method note closing the 발신번호 slice: *when a table is written by this system and read by another, the other system's queries are part of the specification.* The generalisation this slice adds is narrower and sharper — **when a screen's only output is a payload for another system, that system's contract is the screen's specification, and a composer that cannot be validated against it has no correctness criterion at all.** Screen 61 has been maintained for over a year (JS stamped `20250428`) with no mechanism that could have detected the mismatch, because nothing consumes its output automatically.

PM has ruled (AMB-A00) that the new screen keeps the composer but **wires 발송 to the real send path** with server-side validation, `tran_id` deduplication and audit logging. That ruling promotes the payload contract from a copy-paste convenience to an enforced interface, and it necessarily pulls screen 50 — today's only real send path — into scope, because two independent writers against one vendor contract is not a design, it is an accident waiting to be repeated. §6.2 CONFLICT-A01 puts that choice to G1.

**Authentication and session handling are not re-specified here.** They are settled in [REQUIREMENTS-SPEC-LOGIN.md](REQUIREMENTS-SPEC-LOGIN.md) and inherited unchanged. Sender-number entitlement is settled in [REQUIREMENTS-SPEC-SENDERNO.md](REQUIREMENTS-SPEC-SENDERNO.md) and is *depended upon* here: FR-ATS-004 requires the caller ID to be a number the institution has registered, which is the point at which the 발신번호 ledger earns its controls.

### 1.1 Legacy artifacts analyzed

| Layer | File |
|-------|------|
| View | `IRIS_ADMIN/web/view/jex/iris_admin/biztalk/biztalk_admin_61_view.jsp` (25 KB — markup plus all inline CSS) |
| Client logic | `IRIS_ADMIN_STATIC/web/opportal/js/jex/iris_admin/biztalk/biztalk_admin_61.js` (1183 L — all behaviour) |
| Service contract | `WSVC.biztalk_admin_61` — `login=Y`, `viewUseYn=Y`, **`actUseYn=N`**, `mntLogYn=Y`, `trTp=U`, `maxUse=0`, name `BIZTALK(템플릿 샘플)` |
| **Consumer contract (single)** | `IMO.ADV_KKO_AT_SEND` — `target=KAKAOBIZTALK`, name `BIZTALK(알림톡전송내부내용)`, version `20210615` |
| **Consumer contract (batch)** | `IMO.ADV_KKO_AT_SEND_M` — declares `msg_data` RECORD with a mandatory `order` |
| **Vendor endpoint** | `IMO.ADV_KKO_AT_SEND2` — `target=COOCON_ALERT`, `_IMO_APPEND_URL=/advising/kakao/at_send`, single field `RSMS` in / `CSTM_RSMS` out |
| **Real send path** | `biztalk_admin_50_s001_act.jsp` (in scope per AMB-A00) |
| Template registry | `IDO.KKB_MSG_TMPL_L001` (code + title per institution), `_L002` (**`TEMPLATE_MSG` — the template body**), `_L003` (all active institutions) |
| Send history | `IDO.KKB_ADMIN_SEND_HIS_C001` — insert-only: `IS_CD`, `SERIALNUM`, `SEND_ID`, `SEND_NM`, `RGDT` |
| Related | `biztalk_admin_50_l001/l002_act.jsp` (template lookup), `biztalk_admin_60.js` (장애문자 전송내역, reads `ADV_KKO_AT_SEND` results), `biztalk_admin_32_l001_act.jsp` |
| Absent | **No `biztalk_admin_61_act` exists** in any layer — consistent with `actUseYn=N`. The screen has no server side whatsoever |

> **Artifact-count caveat.** Unlike the 발신번호 slice, no `IDO` query and no action JSP belongs to screen 61. Its behaviour is recoverable *only* from the two client files; everything about correctness comes from the consumer contracts. Where this document states a length or a field name as authoritative, the source is the IMO contract, not the screen.

### 1.2 Data model

There is **no persistence in screen 61.** No table is read and none is written; `outputJson` is a textarea. The data model below is therefore an *interface* model, recovered from `IMO.ADV_KKO_AT_SEND` and `IMO.ADV_KKO_AT_SEND_M`, plus the two tables the send path touches.

**Single send — `ADV_KKO_AT_SEND` in:**

| Field | Contract length | Emitted by screen 61 | Note |
|-------|-----------------|----------------------|------|
| `is_cd` | 6 | ✔ | 이용기관코드 |
| `tran_id` | 10 | ✔ | 거래고유번호 — operator free text today |
| `sender_number` | 24 | ✔ | 콜백송신자번호 |
| `receiver_number` | 20000 | ✔ (as array) | 수신폰번호 — also declared as `rule_Sub_0` RECORD; three shapes exist in practice (D-A10) |
| `reqdate` | 14 | ✔ (optional) | 발송일시 — `yyyyMMddHHmmss` |
| `msg` | 4000 | ✔ | 발송메시지 |
| `sender_key` | 100 | ✔ | 프로파일키 — **a credential** |
| `template_code` | 30 | ✔ | 템플릿코드 |
| `template_title` | 200 | ✔ (emphasize only) | 강조표기제목 |
| `button` (RECORD) | `name` 28, `type` 2, `url_pc` / `url_mobile` / `scheme_android` / `scheme_ios` 240 each | ✔ | |
| **`failback_data`** (GROUP) | `type` 2, `subject` 50, `msg` 4000, `img_id` 256 | **✘ — emitted as `failback`** | **D-A1** |
| — | — | ✘ extra: `msg_type`, `kko_header`, `highlight`, `items`, `summary` | **D-A2** — no contract field exists |

**Out:** `tran_id` (10), `rsp_code` (10), `rsp_message` (400).

**Batch send — `ADV_KKO_AT_SEND_M` in:** `is_cd` (6), `tran_id` (10), `msg_data` RECORD. Each `msg_data` item declares **`order`** (순번, 6 — **never emitted, D-A3**), `receiver_number` (20000), `sender_number` (24), **`reqdate` (14 — present in the contract, absent from the screen, D-A14)**, `msg` (4000), `sender_key` (100), `template_code` (30), `template_title` (200), `failback_data` GROUP, `button` RECORD.

**Tables:**

| Table | Role | Key |
|-------|------|-----|
| `KKB_MSG_TMPL` | Template registry — `IS_CD`, `TEMPLATE_CODE`, `TEMPLATE_TITLE`, **`TEMPLATE_MSG`** | `IS_CD` + `TEMPLATE_CODE` |
| `KKB_ADMIN_SEND_HIS` | Send history, insert-only — `IS_CD`, `SERIALNUM`, `SEND_ID`, `SEND_NM`, `RGDT` | `IS_CD` + `SERIALNUM` |
| `KKB_DPNO_LDGR` | Sender-number ledger — **read-only here**, per FR-ATS-004 | `IS_CD` + `DP_NO` |

> **`TEMPLATE_MSG` is the finding that reshapes the 검증 tab.** The registry already stores the template body, keyed by exactly the two values the composer collects. Screen 61 nonetheless asks the operator to paste the template in by hand into a textarea and compare it against content pasted into a second textarea. The comparison it then performs is one the server could do automatically, on the real message, at send time — which is where it would actually prevent a vendor rejection. §2.4 respecifies validation on that basis; the manual tab survives only as a diagnostic (FR-ATV-007).

### 1.3 Classification and priority

Per harness standard: `FR-` functional, `NFR-<area>-` non-functional, `CONST-<area>-` constraint, `UC-` use case. Priority is MoSCoW. Requirement families in this slice: `FR-AZ-A*` (access control), `FR-ATC-*` (compose), `FR-ATS-*` (send), `FR-ATV-*` (template validation), `FR-ATT-*` (template registry), `FR-ATH-*` (audit and history).

### 1.4 Defect disposition (PM ruling, 2026-08-18)

Thirty-five defects were confirmed — **twenty-three in screen 61 (D-A1…D-A23)** and **twelve in the send path screen 50 (D-A24…D-A35)**, which AMB-A00 brought into scope. All are fixed. Two require action independent of the migration (§6.5).

#### Screen 61 — contract conformance

| ID | Sev | Defect | Disposition |
|----|-----|--------|-------------|
| D-A1 | Critical | **The failback block is emitted under the wrong key.** The composer writes `failback`; `ADV_KKO_AT_SEND` and `_M` both declare **`failback_data`**. A payload from this screen therefore carries no fallback the interface can bind — and because failback is what delivers the message as SMS/LMS when AlimTalk fails, the loss is **silent and only visible as an undelivered notification** | FIX → FR-ATC-002, CONST-DATA-A01 |
| D-A2 | Critical | **Five emitted fields exist in no contract**: `msg_type`, `kko_header`, `highlight`, `items`, `summary`. The entire 아이템리스트형 UI — header, highlight, item list, summary — composes data `ADV_KKO_AT_SEND` cannot accept. An operator can fill in every field of that form and produce a payload in which none of it reaches the vendor | FIX → FR-ATC-003, AMB-A05 |
| D-A3 | Critical | **`order` (순번) is never emitted.** `ADV_KKO_AT_SEND_M` declares it on every `msg_data` item. Batch payloads from this screen are missing a mandatory field, and ordering — which determines which recipient gets which message — is left to array position across a system boundary | FIX → FR-ATC-004 |
| D-A7 | High | **Not one contract length is enforced.** No `maxlength`, no client check, no server check for any of: `is_cd` 6, `tran_id` 10, `sender_number` 24, `msg` 4000, `sender_key` 100, `template_code` 30, `template_title` 200, `button.name` 28, URL/scheme 240, `failback_data.subject` 50, `img_id` 256, `order` 6. Values over length are truncated or rejected at the boundary, not at input | FIX → FR-ATC-005, CONST-DATA-A02 |
| D-A10 | Medium | **`receiver_number` has three incompatible shapes.** Screen 61 emits a JSON **array** in single send and a **scalar string** per `msg_data` item in batch; the contract declares **one field of length 20000** (and, contradictorily, also a `rule_Sub_0` RECORD); screen 50 emits `jArray.toString()` — an array *stringified* — in one branch and a raw array in the other. Four shapes across three files for one field | FIX → FR-ATC-006, AMB-A06 |
| D-A13 | Medium | `emphasis_type` drives the whole form but is never emitted, so the message form must be inferred downstream from which optional fields happen to be present | FIX → FR-ATC-003 |
| D-A14 | Medium | **Batch send omits `reqdate` although the contract declares it per `msg_data` item.** Reservation is unavailable for batch sends not by design but by omission | FIX → FR-ATC-007 (AMB-A04) |
| D-A15 | Medium | `template_code` is free text, though `KKB_MSG_TMPL` is a registry keyed by exactly `(IS_CD, TEMPLATE_CODE)` and screen 50 already queries it | FIX → FR-ATT-001…003 |
| D-A16 | Medium | The 검증 tab requires the operator to paste the template body by hand, though `KKB_MSG_TMPL.TEMPLATE_MSG` stores it against the code the same form already collects | FIX → FR-ATV-001…003 |

#### Screen 61 — behaviour

| ID | Sev | Defect | Disposition |
|----|-----|--------|-------------|
| D-A4 | High | **초기화 is broken and clears almost nothing.** The handler's third statement is `getElementById('receiver_number').value = ''`, but no element has that ID — 수신번호 inputs carry the *class* `receiver-number`. The handler throws on `null.value`, so only `is_cd` and `tran_id` are ever cleared: the message, the buttons, the item list and the generated JSON all survive a reset. `getElementById('itemsTab')` a few lines later is a second non-existent element. The same failure class as D-S11 in the 발신번호 slice — client code addressing elements the markup does not contain | FIX → FR-ATC-010 |
| D-A5 | High | **Validation leaks across tabs.** `validateRequiredFields()` selects `.receiver-number` document-wide rather than within `#single-panel`, so a recipient typed into a *batch* message item satisfies the *single-send* required check. A single send can pass validation with its own recipient field empty | FIX → FR-ATC-011 |
| D-A6 | High | **The template validator reports valid content as invalid.** After each `#{...}`, `validateTemplateStrict()` advances to the **first occurrence** of the next literal character. When the substituted value contains that character the scan stops inside the value and the following comparison fails. Template `#{name}님 안녕` with content `김님철수님 안녕` is reported as a mismatch. It also accepts a zero-length substitution and reports only the first error | FIX → FR-ATV-004…006 (PM ruling AMB-A00b — corrected, **not** ported) |
| D-A8 | Medium | `msg_type=AI` (이미지형) is selectable and auto-syncs with `emphasis_type=image`, but **no image field exists anywhere in the screen** — so the option can only ever produce a payload with no image. `msg_type` is not a contract field either (D-A2), so the vendor's image parameters are entirely unrepresented | FIX → FR-ATC-008 (AMB-A05) |
| D-A9 | Medium | Buttons whose name is blank are dropped by `.filter(button => button.name)` with **no warning** — the operator sees a configured button in the form and no button in the payload | FIX → FR-ATC-009 |
| D-A11 | Medium | `reqdate` is free text with no format, timezone, or future-time validation. The contract's length of 14 implies `yyyyMMddHHmmss`; nothing in the screen says so or checks it | FIX → FR-ATC-007 |
| D-A12 | Medium | **No recipient validation of any kind** — no format check, no duplicate removal, no count cap. Screen 50 does validate format (badly, D-A28); screen 61 does not validate at all | FIX → FR-ATC-012, FR-ATS-005…007 |
| D-A17 | Low | Failback fields are never validated. Selecting SMS/LMS/MMS and leaving the message empty yields a failback object of `{type}` alone; `subject` for LMS/MMS and `img_id` for MMS are likewise unchecked | FIX → FR-ATC-002 |
| D-A18 | Low | 초기화 sets `failback_type` to `SMS` while the markup's default option is `NO` — a reset moves the form to a state it never starts in (unreachable today behind D-A4) | FIX → FR-ATC-010 |
| D-A19 | Low | The batch 메시지타입 select offers AT/FT in the JSP seed markup and AT/FT/AI in the JS-injected template — two versions of one control | FIX → FR-ATC-008 |
| D-A20 | Low | 복사 uses the deprecated `document.execCommand('copy')` with no Clipboard API path and no failure handling, yet reports success unconditionally via `alert` | FIX → FR-ATC-013 |
| D-A21 | Low | `window.onload` is reassigned *inside* `_thisPage.onload()`, clobbering any other global handler, to hardcode the containing iframe to `2000px` | FIX → NFR-USE-A02 |
| D-A22 | Low | No i18n. Only `<title>` carries `data-jxln="ml_1"`; every other label, placeholder and error string is hardcoded Korean, and all styling is inline in the JSP — both against the pattern the sibling screens follow | FIX → NFR-USE-A01, NFR-COMPAT-A02 |
| D-A23 | Low | Dead markup and dead bindings: the JSP's seed `.msg-data-item` is `display:none`, is filtered out of both validation and generation, and yet `.remove-msg-data` handlers are bound to it at load | FIX → §2.7 |

#### Send path (screen 50) — in scope per AMB-A00

| ID | Sev | Defect | Disposition |
|----|-----|--------|-------------|
| D-A24 | Critical | **A live vendor credential is hardcoded in source.** `biztalk_admin_50_s001_act.jsp` sets `sender_key` to `17da29…2921` in both branches, commented `조회하면 가능하지만 우선 임시로 넣어둔다` ("could look it up, but putting it in temporarily for now"). The profile key authorises sending as the institution; it is in a source repository, in cleartext, in two places | FIX → FR-ATS-003, NFR-SEC-CRED-A01 + **credential rotation, §6.5** |
| D-A25 | Critical | **`tran_id` collides by construction.** It is `"33" + hh24miss` — second precision only — so two sends in the same second produce the same 거래고유번호, the key of `KKB_ADMIN_SEND_HIS` and the vendor's correlation handle. The batch branch uses an entirely different scheme, `hh24miss + apiNumber++`. Neither is unique across days, and both undercut PM's AMB-A01 ruling that dedupe be keyed on `(is_cd, tran_id)` | FIX → FR-ATS-008…010 |
| D-A26 | Critical | **Failure is reported after the messages have been sent.** The `unmatchedPhoneNumber` check throws `JexWebBIZException` *after* the history insert and *after* the IMO send have both succeeded, so the operator is told the send failed when it did not. In the batch branch the throw sits **inside the loop**: with more than 1000 recipients and a single malformed number, batch 1 is delivered, batches 2..n never run, and the operator sees an error — partial delivery presented as failure, with no record of where it stopped | FIX → FR-ATS-005…007, NFR-OPS-A02 |
| D-A27 | High | **No transaction and no connection release.** The `KKB_ADMIN_SEND_HIS` insert and the IMO send execute independently with no rollback, so history and delivery can disagree in either direction; `idoCon` and `imoCon` are never closed on any path, including the throwing ones. Same class as D-S6 | FIX → NFR-OPS-A01 |
| D-A28 | High | **The recipient check is unanchored.** `isPhoneNumber()` applies `(01[016789]{1})(\d{3,4})\d{4}$` with `find()` and no `^`, so `abc01012345678` passes. It is also mobile-only, silently discarding any landline recipient — and a discarded recipient becomes an *error message*, not a warning (D-A26) | FIX → FR-ATS-005 |
| D-A29 | High | **The SMS/LMS threshold is measured on the wrong string.** `input.getString("MSG").length() > 80` tests the **Base64-encoded** message, which is ~33 % longer than the text; the switch to LMS therefore trips at roughly 60 real characters, and the 90-byte SMS limit is never actually the criterion. The decoded value is available two lines above | FIX → FR-ATS-011 |
| D-A30 | High | **Recipient phone numbers and the vendor credential are written to logs.** `util.getLogger().debug("[BIZTALK_50] " + imoIn.toJSONString())` serialises the whole request — every recipient number and the `sender_key` — into the application log. Normal-flow messages are additionally logged at `error` level (`receiverNumbers.length`, `jArray.size()`), so routine sends generate error-level noise | FIX → NFR-SEC-PII-A02, NFR-SEC-CRED-A01, NFR-OPS-A03 |
| D-A31 | Medium | **An empty recipient list is still sent.** If every supplied number fails `isPhoneNumber`, `jArray` is empty, `size() <= 1000` holds, and the request goes to the vendor with an empty recipient array — followed by the D-A26 exception | FIX → FR-ATS-006 |
| D-A32 | Medium | `reqdate` is unconditionally overwritten with `now()` in both branches, so **reservation is unreachable through the send path** even though both contracts declare the field and screen 61 collects it | FIX → FR-ATS-012 |
| D-A33 | Medium | The batch branch **mutates and re-executes a single `imoIn` object** in a loop against the *single-send* interface, 1000 recipients at a time. `ADV_KKO_AT_SEND_M` — the batch contract, with its `msg_data` and `order` — is **never used by any code in the repository**, which is why D-A3 was never noticed | FIX → FR-ATS-013 |
| D-A34 | Medium | The failback subject is hardcoded `"[쿠콘공지]"` regardless of institution, template or message | FIX → FR-ATS-011 |
| D-A35 | Medium | `RECEIVER_NUMBER` is split on a single space with no trimming and no handling of empty tokens or other delimiters, so formatting of the operator's input silently changes the recipient count | FIX → FR-ATS-005 |

#### Found during implementation (Sprint A1)

| ID | Sev | Defect | Disposition |
|----|-----|--------|-------------|
| **D-A37** | **High** | **`@PreAuthorize` was inert across the entire programme.** Method security is **off by default** in Spring Boot 3 and requires `@EnableMethodSecurity`; that annotation appears **nowhere** in the committed source, yet **five controllers from the three previous slices** carry `@PreAuthorize` (`OtpAdminController`, `PasswordAdminController`, `InstitutionAdminController`, `InstitutionController`, `SenderNumberController`). Every design document in this programme describes controller-level authorization as "defence in depth" — **that layer did not exist.** Not an open door: all five sit under `SecurityConfig`'s `/api/admin/**` → `hasRole("OPERATOR")` rule, which is real. But the documented second barrier was absent, and any future endpoint placed outside `/api/admin/**` while relying on the annotation would be unprotected. **This is the D-S2 defect class recurring in our own code** — the legacy's browser-side `alert('권한 없음')` guarded nothing; so did these annotations | FIX → `@EnableMethodSecurity` added to `SecurityConfig`, asserted by `AlimTalkControllerSecurityTest`. **Cross-slice** — see note below |
| **D-A36** | **High** | **The contract declares `failback_data.type` as `length="2"`, but its only valid values are three characters.** The `failback_data` sub-rule is a **verbatim copy** of the `button` sub-rule's `type` item — not only `length="2"` but also `name="버튼타입"` ("button type"). Fallback types are `SMS` / `LMS` / `MMS`. **Enforcing the declared length would reject every valid fallback**, and if the `jex` IMO layer applies fixed-width handling to the declared length (as its `padding=" "` attribute suggests), the value may **have been transmitted truncated to `"SM"` all along** — which would mean the fallback never worked, *independently of D-A1*. Found by `PayloadValidationTest` when an assertion written against the declared length failed | FIX → `AlimTalkLimits.CONTRACT_FAILBACK_TYPE` = 3, with the declared 2 retained as `CONTRACT_FAILBACK_TYPE_DECLARED` and the discrepancy pinned by a test. **Truncation question routed to spike A1-02** (RISK-A02) |

| **D-A38** | **High** | **`tran_id` has no date component, and it is the join key for the send-history screen.** Both legacy paths derive it from the time of day alone — `"33" + hh24miss` for single send (`biztalk_admin_50_s001_act.jsp:114`) and `hh24miss + apiNumber++` for batch (:172) — so **both repeat every day**. That value is written to `KKB_ADMIN_SEND_HIS.SERIALNUM`, and `IDO.KKB_ADMIN_SEND_HIS_L001` selects `WHERE RGDT BETWEEN :START_DT AND :END_DT` — a **multi-day range** — before joining `A.SERIALNUM = B.SERIALNUM` against `KKO_MSG_LOG`. When the range spans a day boundary and two sends share a second-of-day, that join becomes a **cartesian product** and `count(1) AS TOTAL_CNT`, `AT_SCS_CNT` and `AT_FT_CNT` are **inflated**. This is not "duplicate ids are untidy": **the send-statistics screen reports wrong numbers**, with an observable symptom. Recorded separately from D-A25 because the affected screen differs (60 발송이력, not the send path) | FIX → our `TranIdGenerator` is `env(1) + yyMMdd(6) + base-36 seq(3)` and cannot reproduce it. **Legacy rows already in the table keep the defect**, since history queries continue to read them — read-side mitigation is a decision for A2-14 (screen 50 retirement) |

> **D-A38 was found by executing a mitigation step, not by reading code.** RISK-A06 required verifying that no legacy consumer reads `KKB_ADMIN_SEND_HIS` with `SELECT *`. Running that check surfaced the join in `_L001`, and the join made the consequence of the legacy `tran_id` scheme concrete. See [ANALYSIS-A2-02-existing-schema.md](../../mapping/analysis/ANALYSIS-A2-02-existing-schema.md).

> **D-A37 is not this slice's defect to own alone.** The fix is one line in `SecurityConfig`, and enabling it is safe — every annotated controller already sits behind the identical `hasRole("OPERATOR")` URL rule, so no caller permitted today can be newly rejected. But it changes the enforcement posture of **five controllers belonging to the 로그인, 이용기관관리 and 발신번호 slices**, whose owners should confirm their own authorization expressions now that the annotations actually execute. Raised to the programme rather than closed silently inside this slice.
>
> It also qualifies a claim those slices' documents make. `DEV-PLAN-SENDERNO` and `SenderNumberController`'s Javadoc both describe the controller-level `@PreAuthorize` as defence in depth alongside the URL rule. Until now there was **one** layer, not two.

> **D-A36 is the one exception to CONST-DATA-A02.** That constraint says contract lengths are inviolable, meaning "the contract wins where it is narrower than the business limit". Here the contract is narrower than **its own declared values**, so following it would stop the feature working. The exception is deliberate and recorded rather than silently applied.
>
> It also matters for D-A1's severity. That defect established one way the fallback silently disappears (wrong key name). D-A36 is a **second, independent** mechanism. Whether either has ever worked in production is now a question for the `RSMS` capture, not for source reading.

> **On D-A1 and D-A33 together.** These two defects explain each other. The batch contract has never been called by any code in this repository, and the composer's output has never been consumed by any code at all — so neither the missing `order` nor the misnamed `failback_data` had any path by which it could fail visibly. The composer has been maintained against a contract nothing checked it against. **This is the argument for FR-ATC-001:** validating the composed payload against the contract server-side is not a nicety here, it is the first mechanism in this feature's history capable of detecting that it is wrong.

---

## 2. Functional Requirements

### 2.1 Access control

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| FR-AZ-A01 | Composing, validating and sending all require an authenticated session **and** an operator role, enforced server-side. Authentication is inherited from the 로그인 slice (FR-LOGIN-*) | Must | Security test per endpoint |
| FR-AZ-A02 | The institution scope of every request is derived from the session's entitlements; `is_cd` in the request body is never trusted on its own. Extends FR-AZ-D03 | Must | Security test (send as another institution) |
| FR-AZ-A03 | Sending is authorized at least as strictly as composing. A screen that only builds a payload may be broadly available; one that despatches messages to customers may not | Must | Security test |
| FR-AZ-A04 | Every send records actor identity, timestamp, `is_cd`, `tran_id`, recipient **count**, template code and outcome. Legacy `KKB_ADMIN_SEND_HIS` captures actor and serial only | Must | Integration test + audit review |
| FR-AZ-A05 | The vendor profile key (`sender_key`) is never selectable, guessable or supplied by the browser; it is resolved server-side from the authorized institution. Fixes D-A24 | Must | Security test + code review |

> Legacy screen 61 carried `login=Y` and nothing else — which was adequate only because it could not act. FR-AZ-A03 exists because AMB-A00 removes that protection: the same screen becomes able to send messages to customers, and its access control has to be re-derived from scratch rather than inherited from a page that was safe by impotence.

### 2.2 Compose (payload construction)

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| FR-ATC-001 | The composed payload is **validated server-side against the interface contract** before despatch — field names, presence, types and lengths. A payload that does not conform is rejected with the offending field named. Root-cause fix for D-A1/A2/A3/A7 | Must | Contract test + integration test |
| FR-ATC-002 | The fallback block is emitted as **`failback_data`**, matching `ADV_KKO_AT_SEND`. When a fallback type is selected its message is mandatory; `subject` applies to LMS/MMS and `img_id` to MMS, and each is validated. Fixes D-A1, D-A17 | Must | Contract test + unit test |
| FR-ATC-003 | The payload carries **only fields the contract declares**. The message form (기본 / 강조표기형 / 아이템리스트형 / 이미지형) is represented by a field the contract accepts, not inferred downstream from which optional fields are present. Fixes D-A2, D-A13 — scope of the item-list fields pending AMB-A05 | Must | Contract test |
| FR-ATC-004 | Every batch `msg_data` item carries an explicit **`order`**; recipient-to-message association never depends on array position across the interface. Fixes D-A3 | Must | Contract test |
| FR-ATC-005 | Every input is length-validated at entry against the contract (§1.2 table), and again server-side. No value is silently truncated. Fixes D-A7 | Must | Unit test per field + integration test |
| FR-ATC-006 | `receiver_number` has **one canonical representation** across compose, send and batch paths. Fixes D-A10; representation pending AMB-A06 | Must | Contract test |
| FR-ATC-007 | `reqdate` is validated as `yyyyMMddHHmmss`, must be in the future by a configured minimum margin, and is available on **both** single and batch sends. Fixes D-A11, D-A14 | Should | Unit test + E2E test |
| FR-ATC-008 | 이미지형 is either fully supported with its required image fields validated, or absent from the UI — never selectable-but-unfillable. Both message-type selects offer one identical option set. Fixes D-A8, D-A19; field set pending AMB-A05 | Must | E2E test |
| FR-ATC-009 | An incompletely configured button is reported to the operator, never silently discarded. Fixes D-A9 | Must | E2E test |
| FR-ATC-010 | 초기화 returns **every** field, repeated group and generated output to the form's documented initial state. Fixes D-A4, D-A18 | Must | E2E test |
| FR-ATC-011 | Validation for a given mode reads only that mode's inputs. Fixes D-A5 | Must | Unit test |
| FR-ATC-012 | Recipients are format-validated, de-duplicated and counted at entry, with the count shown before despatch. Fixes D-A12 | Must | Unit test + E2E test |
| FR-ATC-013 | Copy-to-clipboard uses a supported API and reports actual success or failure. Fixes D-A20 | Could | E2E test |

### 2.3 Send

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| FR-ATS-001 | An authorized operator sends a composed AlimTalk message to one or more recipients | Must | E2E test |
| FR-ATS-002 | The response records the vendor's `rsp_code` and `rsp_message` against the `tran_id` and presents the outcome to the operator | Must | Integration test |
| FR-ATS-003 | `sender_key` is resolved server-side for the authorized institution and is never present in client code, source or configuration in cleartext. Fixes D-A24 | Must | Code review + security test |
| FR-ATS-004 | `sender_number` must be a 발신번호 currently registered to the sending institution, verified server-side against the ledger. *(This is the control the 발신번호 slice exists to make trustworthy — see FR-SNDD-003.)* | Must | Integration test + security test |
| FR-ATS-005 | Recipients are validated with a correctly anchored pattern covering the numbers the business actually sends to; input parsing tolerates the delimiters operators use. Fixes D-A28, D-A35 | Must | Unit test (incl. `abc01012345678` rejected) |
| FR-ATS-006 | A send with **zero valid recipients is rejected before any vendor call**. Fixes D-A31 | Must | Integration test |
| FR-ATS-007 | Invalid recipients are surfaced **before** despatch, as a decision point — proceed with the valid subset, or cancel. They are never reported as a failure after delivery. Fixes D-A26 | Must | E2E test |
| FR-ATS-008 | `tran_id` is unique per `(is_cd, tran_id)` with sufficient entropy and no dependence on wall-clock second precision. Fixes D-A25 | Must | Unit test + concurrency test |
| FR-ATS-009 | A repeat of an already-accepted `(is_cd, tran_id)` within the retention window is **rejected as a duplicate** and returns the original outcome rather than sending again (PM ruling AMB-A01). Window pending AMB-A08 | Must | Integration test (double submit) |
| FR-ATS-010 | One `tran_id` scheme applies to single and batch sends alike. Fixes D-A25 | Must | Code review + integration test |
| FR-ATS-011 | Fallback type selection is decided on the **decoded** message and its byte length, per the applicable SMS/LMS thresholds; the fallback subject is derived from institution or template, not hardcoded. Fixes D-A29, D-A34 | Must | Unit test (boundary lengths) |
| FR-ATS-012 | A reserved send is despatched at `reqdate`, which is not overwritten with the current time. Fixes D-A32 | Should | Integration test |
| FR-ATS-013 | Batch sends use the **batch interface** (`msg_data` + `order`), not a loop over the single-send interface with a mutated request object. Fixes D-A33 | Must | Contract test + code review |
| FR-ATS-014 | Batch size is capped at a **configurable maximum**, enforced server-side, with the limit stated in the UI (PM ruling AMB-A03). Value pending AMB-A03 | Must | Integration test |

### 2.4 Template validation

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| FR-ATV-001 | The message is validated against the **registered template body** (`KKB_MSG_TMPL.TEMPLATE_MSG`) for `(is_cd, template_code)`, server-side, **before despatch**. Fixes D-A16 at its root — the check runs where it can prevent a rejection | Must | Integration test |
| FR-ATV-002 | A message that does not conform to its registered template is rejected with the divergence point identified | Must | Integration test |
| FR-ATV-003 | A `template_code` with no registered body is reported as such, distinctly from a content mismatch | Must | Integration test |
| FR-ATV-004 | Template matching is **correct by construction** — the template compiles to a pattern in which literal segments are matched exactly and variables match minimally, so a substituted value containing a following literal character does not break the match. Fixes D-A6 (PM ruling AMB-A00b). **Behavioural break from legacy:** inputs the legacy rejected now pass. Regression tests assert the new behaviour, not parity | Must | Unit test (incl. `#{name}님 안녕` / `김님철수님 안녕`) |
| FR-ATV-005 | A variable must match at least one character; an empty substitution is a mismatch | Should | Unit test |
| FR-ATV-006 | All divergences are reported, not only the first | Should | Unit test |
| FR-ATV-007 | The manual 검증 tab is retained as an operator diagnostic for templates not yet registered, sharing **one implementation** with FR-ATV-001 | Should | E2E test |
| FR-ATV-008 | Variable syntax is stated explicitly in the specification and consistent between validator and template registry. *(Legacy accepts both `#{...}` and `${...}`; only the former is Kakao's.)* | Must | Unit test + code review |

### 2.5 Template registry

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| FR-ATT-001 | `template_code` is **selected from the templates registered to the institution**, not typed free-form. Fixes D-A15 | Must | E2E test |
| FR-ATT-002 | Selecting a template populates 강조표기제목 from `TEMPLATE_TITLE` and shows `TEMPLATE_MSG` as the composition guide | Should | E2E test |
| FR-ATT-003 | The registry read is scoped to institutions the operator is entitled to. *(Legacy `KKB_MSG_TMPL_L003` returns every active institution's templates in one query with no operator scoping.)* | Must | Security test |
| FR-ATT-004 | A send whose `template_code` is not registered to the sending institution is rejected server-side | Must | Integration test |

### 2.6 Audit and history

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| FR-ATH-001 | Every send attempt — accepted, rejected or duplicate — is recorded with actor, timestamp, institution, `tran_id`, template code, recipient count and outcome | Must | Integration test |
| FR-ATH-002 | History records the recipient **count**; individual numbers are stored only where required and never in application logs (NFR-SEC-PII-A02) | Must | Integration test + log inspection |
| FR-ATH-003 | A history record is never written for a send that did not occur, and never omitted for one that did. Fixes the D-A27 divergence | Must | Integration test |

### 2.7 Excluded from this slice

- **Template registration, editing, approval and versioning.** `KKB_MSG_TMPL` is **read-only** here (FR-ATT-001…004). PM ruled against the full template-management option (AMB-A00); no legacy screen writes this table, so how templates arrive in it is unresolved and out of scope — raised as AMB-A07.
- **친구톡 (FT) and its send paths.** `msg_type` is not a contract field (D-A2) and no FT interface appears in the analyzed artifacts.
- **장애문자 전송내역 (screen 60)** and 전송내역 조회 — covered by the 문자내역 slice.
- **Dead code** per D-A23: the hidden seed `.msg-data-item`, its bindings, and the inline-CSS block are not carried forward.
- **The `RSMS` envelope of `ADV_KKO_AT_SEND2`.** How `KAKAOBIZTALK` marshals the bound request into the vendor's single `RSMS` field is not recoverable from these artifacts; it is a design-time question (AMB-A06).

---

## 3. Non-Functional Requirements

### 3.1 NFR-PERF (performance)

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| NFR-PERF-A01 | Contract validation and template matching of a single message: P95 < 300 ms | Should | Load test |
| NFR-PERF-A02 | Single send end to end, excluding vendor latency: P95 < 1 s | Should | Load test |
| NFR-PERF-A03 | A batch at the FR-ATS-014 cap is accepted and acknowledged within 5 s; despatch itself may be asynchronous | Should | Load test |

### 3.2 NFR-SEC (security)

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| NFR-SEC-CRED-A01 | The vendor profile key is held in managed secret storage, never in source, client code or logs, and is rotatable without a code change. Fixes D-A24, D-A30 | Must | Code review + secret scan + log inspection |
| NFR-SEC-PII-A01 | Recipient numbers are masked in the UI beyond the entry field, and in every list, export and audit view (PM ruling AMB-A02b) | Must | E2E test |
| NFR-SEC-PII-A02 | Recipient numbers are **never written to application logs**, at any level. Fixes D-A30 — the legacy logs the entire request payload at `debug` | Must | Log inspection + code review |
| NFR-SEC-PII-A03 | Only fields the screen displays are returned to the browser | Should | Response-shape test |
| NFR-SEC-TX-A01 | Send requests carry message integrity and non-repudiation toward the vendor: the request is auditable after the fact and attributable to an actor. *(Skill 7 mandatory card — 금융 거래)* | Must | Integration test + audit review |
| NFR-SEC-AUTHZ-A01 | Send, compose and registry-read authorization enforced server-side on every endpoint; no control depends on a hidden element or a client-side check | Must | Security test |
| NFR-SEC-CHANNEL-A01 | TLS on portal traffic and on the vendor channel; the vendor endpoint is allowlisted. *(Skill 7 mandatory card — 외부 채널; inherits CONST-SEC-01)* | Must | Configuration review |
| NFR-SEC-INJ-A01 | Operator-supplied values are bound as parameters into the registry query and encoded into the vendor request; never concatenated | Must | Code review + security test |

### 3.3 NFR-OPS (operations)

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| NFR-OPS-A01 | History write and vendor despatch are consistent under failure — no delivery without a record, no record without a delivery attempt — and connections are released on every path including exceptional ones. Fixes D-A27 | Must | Integration test + connection-pool test |
| NFR-OPS-A02 | **No operation reports failure for work that succeeded, or success for work that did not.** Partial batch outcomes are reported as partial, naming what was delivered and what was not. Fixes D-A26 | Must | Integration test |
| NFR-OPS-A03 | Log levels reflect severity: routine sends are not logged at `error`. Fixes D-A30 | Should | Log inspection |
| NFR-OPS-AUDIT-A01 | Audit records for send, duplicate-rejection and validation-rejection carry actor, timestamp, institution, `tran_id`, template and outcome | Must | Integration test |
| NFR-OPS-AUDIT-A02 | Audit retention per CONST-LEGAL-02; term `[보류]` — carried open item OI-02 | Must | Configuration review |

### 3.4 NFR-SCALE / NFR-COMPAT / NFR-USE

| REQ-ID | Requirement | Priority | Verification |
|--------|-------------|----------|--------------|
| NFR-SCALE-A01 | Recipient volume per send is bounded by FR-ATS-014 and enforced server-side; the legacy 1000-per-call chunking is an implementation detail, not a user-visible limit | Must | Load test |
| NFR-COMPAT-A01 | Chrome / Edge current and one prior major version *(inherits the programme baseline)* | Must | Cross-browser test |
| NFR-COMPAT-A02 | Presentation follows the portal design system; no screen-local inline stylesheet. Fixes D-A22 | Should | Code review |
| NFR-USE-A01 | All operator-facing text is externalised for i18n, consistent with the sibling screens. Fixes D-A22 | Should | Code review |
| NFR-USE-A02 | The screen sizes itself within its container without reassigning global handlers or hardcoded pixel heights. Fixes D-A21 | Should | E2E test |
| NFR-USE-A03 | Validation failures name the field and the rule violated — including the contract length breached — rather than a generic alert listing field names | Should | Usability review |
| NFR-USE-A04 | Before despatch the operator sees recipient count, template, caller ID and fallback type in one confirmation step | Must | Usability review |

---

## 4. Constraints

| REQ-ID | Constraint | Basis |
|--------|-----------|-------|
| CONST-DATA-A01 | `IMO.ADV_KKO_AT_SEND` and `IMO.ADV_KKO_AT_SEND_M` are the **authoritative** field names for the outbound AlimTalk request — notably `failback_data`, not `failback`, and `order` on every batch item | The two IMO contracts; D-A1, D-A3 |
| CONST-DATA-A02 | Contract field lengths (§1.2) are hard bounds at the interface boundary. Any business limit adopted under AMB-A02 must be **at or below** them | The two IMO contracts; D-A7 |
| CONST-DATA-A03 | `KKB_MSG_TMPL` is the authoritative template registry, keyed `(IS_CD, TEMPLATE_CODE)`, and holds the template body in `TEMPLATE_MSG`. It is read-only in this slice | `KKB_MSG_TMPL_L001/L002/L003` |
| CONST-DATA-A04 | `KKB_ADMIN_SEND_HIS` is insert-only and keyed `(IS_CD, SERIALNUM)`, where `SERIALNUM` is the `tran_id`. Uniqueness of `tran_id` is therefore a **data-integrity** requirement, not only an idempotency one. Reinforces FR-ATS-008 | `KKB_ADMIN_SEND_HIS_C001`; D-A25 |
| CONST-BIZ-A01 | A send requires a `template_code` **registered to the sending institution** and a `sender_number` **registered in the ledger**. Neither is verified today | FR-ATT-004, FR-ATS-004 |
| CONST-BIZ-A02 | The vendor channel is `COOCON_ALERT` at `/advising/kakao/at_send`, reached via a single `RSMS` request field. The portal is not a direct Kakao client; Kakao's published rules apply **through** this vendor, which is why AMB-A02 is a genuine question and not a lookup | `IMO.ADV_KKO_AT_SEND2` |
| CONST-TECH-01 | Java 17+ / Spring Boot 3.x, MyBatis, React SPA | ADR-001 (proposal §11) |
| CONST-LEGAL-01 | Personal data masked in UI, logs and exports | 개인정보보호법 (BR-007) |
| CONST-LEGAL-02 | Audit log retention per 전자금융감독규정 / ISMS-P, term `[보류]` | BR-016, OI-02 |
| CONST-SEC-01 | Internet-facing and multi-tenant; no endpoint may rely on network-perimeter protection as its access control | Proposal §3, RISK-006 |

---

## 5. Use Cases

| UC-ID | Scenario | Primary user | Related FR |
|-------|----------|--------------|------------|
| [UC-ATK-001](use-cases/UC-ATK-001.md) | Compose a single AlimTalk message against a registered template | Operator | FR-ATC-001…013, FR-ATT-001…003 |
| [UC-ATK-002](use-cases/UC-ATK-002.md) | Send a single AlimTalk message | Operator | FR-ATS-001…012, FR-AZ-A01…A05, FR-ATH-001…003 |
| [UC-ATK-003](use-cases/UC-ATK-003.md) | Compose and send a batch | Operator | FR-ATC-004/006/007, FR-ATS-013/014 |
| [UC-ATK-004](use-cases/UC-ATK-004.md) | Validate message content against its registered template | Operator | FR-ATV-001…008 |

Orphan check: every FR, NFR and CONST in this document maps to at least one of UC-ATK-001…004 — see [requirements-matrix.csv](requirements-matrix.csv). **Orphan count: 0.**

---

## 6. AMBIGUOUS / open items

### 6.1 Resolved by PM (2026-08-18)

| ID | Item | Candidates | PM response | Status |
|----|------|-----------|-------------|--------|
| AMB-A00 | Migration scope. The screen is registered as `BIZTALK(템플릿 샘플)` and sends nothing; a separate crude send path exists in screen 50 | A: utility parity · B: utility + real send · C: full template management · D: drop | **B — keep the composer, wire 발송 to the real send path with server-side validation, dedupe and audit** | RESOLVED → CONFLICT-A01 |
| AMB-A00b | The template validator's skip-ahead algorithm demonstrably reports valid content as invalid (D-A6) | A: correct it · B: byte-for-byte parity · C: adopt Kakao's published rules | **A — correct it; the behavioural break is accepted** | RESOLVED → FR-ATV-004 |
| AMB-A01 | Duplicate-send protection, given `tran_id` stays operator-supplied | A: server dedupe on `(is_cd, tran_id)` · B: UI guard only · C: none | **A — server dedupe** | RESOLVED → FR-ATS-009 |
| AMB-A02 | Which limit set governs — Kakao's published business limits, or the IMO contract's declared lengths | A: Kakao spec values, tagged `[ASSUMED-KAKAO-SPEC]` · B: PM supplies values · C: advisory only · D: none | **A — Kakao spec values** | RESOLVED → **superseded in part, CONFLICT-A02** |
| AMB-A03 | Batch size cap; none exists today | A: configurable cap, value later · B: no cap | **A — configurable cap, value to follow** | RESOLVED → open value, §6.3 |
| AMB-A05a | 이미지형 (`msg_type=AI`) is selectable with no image fields anywhere (D-A8) | A: implement properly · B: remove from UI · C: keep, reject server-side | **A — implement properly** | RESOLVED → field set open, §6.3 AMB-A05 |
| AMB-A02b | PII handling for recipient numbers | A: mask + audit the send · B: also audit clipboard egress · C: server-generated `tran_id` · D: none | **A only** — B and C explicitly **declined** | RESOLVED → NFR-SEC-PII-A01, FR-AZ-A04, RESIDUAL-A01/A02 |

### 6.2 Conflicts and residual risks requiring G1 acknowledgement

| ID | Item | Detail |
|----|------|--------|
| CONFLICT-A01 | **AMB-A00 (this screen sends for real) vs. an existing send path in screen 50** | AMB-A00 was answered on the understanding that screen 61 sends nothing today. Analysis then found that `biztalk_admin_50_s001_act.jsp` **already sends**, against the same `ADV_KKO_AT_SEND` contract, carrying twelve defects of its own including a hardcoded credential and a colliding `tran_id` (D-A24…D-A35). Wiring screen 61 to send therefore creates a **second writer on one vendor contract** unless screen 50's path is replaced. Two writers means the FR-ATS-008/009 uniqueness and dedupe guarantees hold only for requests that go through the new path — and screen 50's `"33"+hh24miss` scheme would keep minting colliding `tran_id`s into the same `KKB_ADMIN_SEND_HIS` key. **This is the D-A33 lesson at programme level:** a contract with no single owner accumulates divergent callers. <br><br>**Recommendation:** the new send path **replaces** screen 50's, which is retired at cutover. **Needs explicit PM sign-off at G1** — it widens the slice from one screen to two. Precisely the shape of RISK-S05 in the 발신번호 slice, where `AOA_ADMIN` remained a second writer after ship; here the second writer is inside our own scope and can still be closed |
| CONFLICT-A02 | **AMB-A02 (Kakao published limits govern) vs. the IMO contract's declared lengths** | The ruling assumed Kakao's limits were simply missing from the code. They are not missing — the contract declares its own, and they **disagree with Kakao's in both directions**: `msg` 4000 vs Kakao's ~1000 characters; `template_title` 200 vs ~50; `button.name` 28 vs ~14; `failback_data.subject` 50. Where the contract is *looser*, adopting Kakao's value is a safe business rule. Where it is *tighter*, Kakao's value is unreachable and the contract wins. And per CONST-BIZ-A02 the portal is not a direct Kakao client — it goes through `COOCON_ALERT`, whose own validation is not in these artifacts. **Resolution adopted for this draft:** enforce `min(contract, Kakao)` per field, tag every Kakao-derived bound `[ASSUMED-KAKAO-SPEC]`, and treat the contract lengths as inviolable (CONST-DATA-A02). **G1 must confirm this reading**, since it partially supersedes AMB-A02 as answered |
| RESIDUAL-A01 | **Accepted: clipboard egress of recipient numbers is not audited** | PM declined that control under AMB-A02b. The composer's copy function remains a path by which an operator can extract recipient phone numbers with no record. Compensating controls are NFR-SEC-PII-A01 (masking beyond the entry field) and FR-AZ-A01/A03 (only authorized operators reach the screen). **Residual exposure:** an authorized operator can copy a composed payload containing unmasked recipient numbers, undetected. Narrower than it was under the legacy design — where copy-paste was the *only* way to send — because sending no longer requires it |
| RESIDUAL-A02 | **Accepted: `tran_id` remains operator-supplied** | PM declined server-generated `tran_id` under AMB-A02b, resolving idempotency by dedupe instead (AMB-A01). Given CONST-DATA-A04 makes `tran_id` a primary-key component, an operator can still cause a *rejection* by reusing a value — dedupe converts a data-integrity failure into a usability one, which is the right trade — but FR-ATS-008's uniqueness requirement now has to be met by a **client-side** scheme the operator can override. Should be revisited if operators report spurious duplicate rejections |

### 6.3 Open

| ID | Item | Candidates | Working assumption | Owner | Needed by |
|----|------|-----------|--------------------|-------|-----------|
| AMB-A03 | The batch-size cap value (FR-ATS-014). Legacy chunks at 1000 per vendor call with no user-visible cap | A: cap = legacy chunk (1000) · B: a higher cap with async despatch · C: an ops-supplied figure | A — 1000, matching the existing chunk boundary | Domain owner / Ops | Skill 3 |
| AMB-A04 | Reservation for batch sends. `ADV_KKO_AT_SEND_M` declares `reqdate` per item, so the contract supports it; PM chose neither "add it" nor "keep single-only" | A: enable for batch, the contract already allows it · B: single-only | **A** — the asymmetry is a screen omission (D-A14), not a design decision | PM | Skill 3 |
| AMB-A05 | The image-type field set (FR-ATC-008). Neither IMO contract declares any image field, so 이미지형 cannot be specified from these artifacts. Same gap covers `kko_header` / `highlight` / `items` / `summary` (D-A2) — the composer emits them, the contract has nowhere to put them | A: obtain the vendor's current spec and extend the contract · B: drop 이미지형 and the item-list form until the vendor spec is available | A — but **B is the fallback**, and it is a scope reduction G1 should be aware of | Architect + vendor | **Skill 3, blocking FR-ATC-003/008** |
| AMB-A06 | The canonical `receiver_number` representation (FR-ATC-006), and how the bound request is marshalled into `ADV_KKO_AT_SEND2`'s single `RSMS` field. Four shapes exist today (D-A10) | A: JSON array, matching screen 50's ≤1000 branch · B: delimited string, matching the declared length of 20000 | A — with the marshalling confirmed against a live `RSMS` capture before Sprint 1 closes | Architect | Skill 3 |
| AMB-A07 | How templates enter `KKB_MSG_TMPL`. No screen in this repository writes it, yet FR-ATT-001/004 make it authoritative for sending | A: an external/vendor process owns it, portal reads only · B: template management is a later slice | A | Domain owner | Skill 3 |
| AMB-A08 | The `tran_id` dedupe retention window (FR-ATS-009) | A: 24 h · B: match `KKB_ADMIN_SEND_HIS` retention | B — one window, so dedupe cannot outlive its evidence | PM | Skill 3 |
| AMB-A09 | Whether landline recipients are in scope. `isPhoneNumber` is mobile-only (D-A28) and silently discards the rest; AlimTalk itself is mobile-only, but the SMS/LMS fallback is not | A: mobile-only, rejected explicitly rather than silently · B: accept landlines for fallback-only sends | A | Domain owner | Skill 3 |

Carried and still open: **OI-02** (audit retention term) blocks NFR-OPS-AUDIT-A02 and CONST-LEGAL-02.

> Seven items remain open (AMB-A03…A09). **AMB-A05 is the only one that blocks a requirement** — FR-ATC-003 and FR-ATC-008 cannot be completed without the vendor's image and item-list field definitions.
> **G1 approval must explicitly cover CONFLICT-A01, CONFLICT-A02, RESIDUAL-A01 and RESIDUAL-A02.**

### 6.4 G1 결재 기록 / G1 approval record

> **결재**: PM · **일자**: 2026-08-19 · **대상**: 이 명세 전체
> **선행**: [PROJECT-PROPOSAL.md](../planning/PROJECT-PROPOSAL.md), [BUSINESS-REQUIREMENTS.md](../planning/BUSINESS-REQUIREMENTS.md) — 둘 다 2026-08-19 결재

§6.2 는 G1 이 네 항목을 <b>명시적으로</b> 다루도록 요구한다. PM 은 명세 전체를 승인했다.
각 항목에 대해 적용되는 판정을 아래에 기록한다 — 승인 문구가 항목별로 나뉘지 않았으므로,
<b>이미 문서화된 작업 가정</b>을 판정으로 확정한다. 그 가정이 PM 의 의도와 다르면 이 표를
고쳐야 하며, 표가 곧 구현의 근거다.

§6.2 requires G1 to cover four items <b>explicitly</b>. The PM approved the specification as a whole.
The ruling recorded for each item below is the <b>working assumption already documented</b>, since the
approval was not itemised. If any differs from the PM's intent this table must be corrected — it is the
basis the implementation rests on.

| ID | 판정 / ruling | 근거 / where it already sits | 상태 |
|----|--------------|------------------------------|------|
| **CONFLICT-A01** | 화면 50 은 <b>컷오버 시 폐기</b>한다. 공존 기간에는 두 경로가 같은 계약을 쓰지만 새 경로만 서버측 검증을 갖는다 | DEV-PLAN DAG 의 <b>A2-14 (screen 50 retirement + cutover runbook)</b> 가 이미 이 전제로 계획되어 있다 | ✅ 확정 |
| **CONFLICT-A02** | 유효 한계는 <b>{ min(계약, 카카오)}</b>. 계약 길이는 넘을 수 없고, 카카오가 더 엄격하면 카카오를 따른다 | `AlimTalkLimits` 가 이미 그렇게 구현되어 있다 — `MSG = min(4000, 1000)`, `TEMPLATE_TITLE = min(200, 50)`, `BUTTON_NAME = min(28, 14)` | ✅ 확정 |
| **RESIDUAL-A01** | 클립보드 유출이 감사되지 않는 잔여 위험을 <b>수용</b>한다 | AMB-A02b 에서 PM 이 해당 통제를 거절했다. 보상 통제는 NFR-SEC-PII-A01 마스킹과 FR-AZ-A01/A03 접근 제한 | ✅ 수용 |
| **RESIDUAL-A02** | `tran_id` 를 운영자 입력으로 <b>유지</b>한다. 멱등성은 중복 거절로 해결한다 | AMB-A02b·AMB-A01. FR-ATS-009 로 구현됨 — 재요청은 409 와 원래 결과를 돌려준다 | ✅ 수용 |

**A01·A02 는 실질적 선택이었고 A01·A02 잔여는 확인이었다.** 앞의 둘에 대해 위 판정이 PM 의 의도와
다르면 알려 주기를 바란다 — A01 은 컷오버 계획(A2-14)을, A02 는 `AlimTalkLimits` 상수를 바꾼다.

#### G1 이 열어 주는 것 / what G1 unblocks

**A2-02 (아웃박스 DDL)** 의 결재 조건이 충족되었다. 다만 적용 전에 두 가지가 더 필요하다 —
G1 이 해결하지 않는 항목이다:

| 항목 | 왜 |
|------|-----|
| DBA 검토 | 대상 DB 는 운영 중인 레거시와 공유된다. V1·V2 모두 자동 적용을 금지한다 |
| `PAYLOAD` 컬럼 암호화 결정 | 수신번호가 평문으로 저장된다. `KKO_MSG_LOG` 는 `PHONE` 을 암호화하며, harness §10 은 PII 컬럼에 AES-256-GCM 을 요구한다 |

#### G1 이 열어 주지 <b>않는</b> 것 / what G1 does not unblock

| 항목 | 막고 있는 것 |
|------|-------------|
| A2-05 실제 발송 | **AMB-A10** — OAuth 클라이언트 자격증명이 `FINChannel` DB 테이블에 있고 그 스키마를 모른다 |
| FR-ATC-003 / FR-ATC-008 | **AMB-A05** — 벤더의 이미지·아이템 필드 정의 없음 |
| G3 | **RISK-A03** — 유출된 발신프로필키 회전. 벤더·Ops 작업 |
| FR-ATS-002 충족 | ADR-ATK-023 수정 2 — 아웃박스와 동기 결과 제시가 충돌한다. 요구사항 개정 또는 설계 변경 필요 |

### 6.5 Operational actions arising independently of the migration

**1. Rotate the vendor profile key (D-A24).** `sender_key = 17da29…（elided — rotate; see RISK-A03）…c2921` is committed in cleartext in `biztalk_admin_50_s001_act.jsp` and, per D-A30, is additionally written to application logs on every send. It must be treated as compromised: rotate it at the vendor, then adopt NFR-SEC-CRED-A01 before the new path ships. Rotation is independent of, and should not wait for, the migration.

**2. Reconcile send history against actual delivery (D-A25, D-A26).** Two legacy behaviours make `KKB_ADMIN_SEND_HIS` an unreliable record. Colliding `tran_id`s mean sends within the same second share a `(IS_CD, SERIALNUM)` key, so history is either short of rows or carries duplicate-key failures. And because failure is reported after delivery, operators may have re-sent messages they believed had failed — so **customers may have received duplicates that the history does not show as duplicates.** Before cutover, sends recorded within the same second for one institution should be reviewed against the vendor's own delivery records. The same shape as the D-S1 finding in the 발신번호 slice: an operation reported an outcome that did not match what the system did, and the discrepancy is only visible from outside.

---

## 7. Change history

| Date | Version | Change | Author |
|------|---------|--------|--------|
| 2026-08-18 | 1.0 | Initial specification of the 알림톡 템플릿/발송 slice (screen 61, plus screen 50's send path per AMB-A00) from static analysis of 12 legacy artifacts; 35 defects recorded; AMB-A00…A02b resolved by PM; CONFLICT-A01/A02 raised | trace-mapper / docs-writer |
