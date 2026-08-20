# Use Case UC-TLK-002: Inspect the messages dispatched under one transaction

> **REQ-ID**: UC-TLK-002
> **Version**: 1.0
> **Predecessor**: [REQUIREMENTS-SPEC-TALK.md](../REQUIREMENTS-SPEC-TALK.md)
> **Legacy origin**: screen 32 — `biztalk_admin_32_view.jsp` / `biztalk_admin_32.js` / `WSVC.biztalk_admin_32_l001` / `IDO.KKB_AT_MSG_L001` + `IDO.KKB_FT_MSG_L001`

---

## 1. Scenario overview

| Item | Content |
|------|---------|
| **Primary user** | Internal operator |
| **Precondition** | UC-TLK-001 has returned a transaction the detail service can serve (FR-TLK-013) |
| **Trigger** | User clicks a 거래고유번호 in the transaction grid |
| **Success outcome** | The messages dispatched under that transaction are listed, paged, masked, with 유형 and result codes correct for the actual channel |
| **Failure outcome** | Unauthorized request rejected; an unsupported API service produces an explicit message, never an empty grid |
| **Related FR** | FR-AZ-T02, FR-AZ-T03, FR-AZ-T05, FR-TLKD-001…009 |
| **Related NFR** | NFR-PERF-T02, NFR-SEC-PII-T01, NFR-SEC-TENANT-T01, NFR-USE-T01 |
| **Related CONST** | CONST-DATA-T01, CONST-LEGAL-T01, CONST-BIZ-T01 |
| **Related BR** | BR-005, BR-006, BR-007, BR-008 |

## 2. Main flow

| Step | Actor | Action | System response |
|------|-------|--------|-----------------|
| 1 | User | Clicks a 거래고유번호 | The detail opens with 거래일자, 이용기관, 거래번호 and API as read-only context (FR-TLKD-001) |
| 2 | System | Resolves scope | The 이용기관 is **re-derived on the server** from `TRDD` + `IS_TUNO`; any institution code in the request body is ignored (FR-AZ-T03) |
| 3 | System | Selects the channel query | By the transaction's API service; a service with no mapping is handled at E-2 (FR-TLKD-005) |
| 4 | System | Normalises the transaction number | The same rule as the list, without loss for identifiers of any length (FR-TLKD-009, FR-TLK-009) |
| 5 | System | Executes the query | Live and archive tables for the resolved channel, both branches |
| 6 | System | Returns the page slice | Paged with a server-supplied total count, which the client uses (FR-TLKD-007) |
| 7 | System | Renders the grid | 유형, 거래번호, 메시지키, 이용기관, 상태, 톡결과, 문자결과, 발송번호, 수신번호, 요청일자, 요청시간, 발송시간, 응답시간, 테이블 (FR-TLKD-001) |
| 8 | System | Presents 유형 | The **actual** channel — 친구톡 rows read FT (FR-TLKD-004) |
| 9 | System | Presents numbers | 발송번호 and 수신번호 **masked**, for every role (FR-TLKD-008, NFR-SEC-PII-T01) |
| 10 | System | Presents result codes | Label plus raw code (NFR-USE-T01) |
| 11 | User | Filters by 수신번호, 상태, 톡결과, 문자결과 | Applied for 알림톡 **and** 친구톡 alike (FR-TLKD-002); the 수신번호 field accepts 11 digits (FR-TLKD-003) |
| 12 | System | Writes an audit event | Actor, timestamp, transaction key, filters, row count (FR-AZ-T05) |
| 13 | User | Clicks a 메시지키 | Proceeds to UC-TLK-003 |

## 3. Alternative flows

### 3.1 A-1: 친구톡 transaction
- At Step 3. The 친구톡 tables are queried, 유형 reads FT, and all four filters at Step 11 apply (FR-TLKD-002, FR-TLKD-004).

### 3.2 A-2: 미수신 filter
- At Step 11. A message with no result yet is reachable under an explicit 미수신 option rather than falling outside both 성공 and 실패 (FR-TLKD-006).

### 3.3 A-3: Transaction from a 처리중 or 오류 row
- Reached from FR-TLK-013. Whatever messages exist are listed; if none do, the empty state is explicit (FR-TLKD-005). See AMB-T05.

## 4. Exception flows

### 4.1 E-1: Request carries another institution's code
- At Step 2, by tampering with the popup form.
- Action: the supplied value is ignored and the server-derived scope applies (FR-AZ-T03). **Regression guard:** `ID` came from a hidden `pop_frm` input the browser supplied, so a tampered value changed the query's institution (D-T2).

### 4.2 E-2: API service has no detail mapping
- At Step 3. `ADV_KKO_AT_SEND2`, or any code the mapping does not cover.
- Action: return an explicit "상세 조회를 지원하지 않는 거래" (FR-TLKD-005). **Regression guard:** the action left the IDO handle null, put no `REC1` in the result, threw nothing, and the popup rendered an empty grid with no message (D-T13).

### 4.3 E-3: Transaction number longer than the legacy's assumed width
- At Step 4. A 20-character 거래고유번호.
- Action: matched without loss (FR-TLKD-009). **Regression guard:** `stripStart(…, "0")` followed by `LPAD(:SERIALNUM,10,'0')` truncated in PostgreSQL, so `00000026081900142813` was matched as `2608190014` (D-T9).

### 4.4 E-4: 친구톡 transaction with filters applied
- At Step 11.
- Action: the filters narrow the result (FR-TLKD-002). **Regression guard:** the 친구톡 query had no `??` placeholder and no `DYNAMIC_0` input, so all four filters were dropped without notice (D-T8).

### 4.5 E-5: Result code is null
- At Step 11 under a 실패 filter.
- Action: the row is reachable under 미수신 and is not silently lost (FR-TLKD-006). **Regression guard:** `AND RSLT != '0'` excluded NULL by three-valued logic (D-T22).

### 4.6 E-6: An 11-digit recipient number is searched
- At Step 11.
- Action: accepted in full (FR-TLKD-003). **Regression guard:** `maxlength="10"` on the input (D-T21).

## 5. Postconditions

- No row is written (CONST-DATA-T01).
- An audit record exists for the detail open (FR-AZ-T05).
- No unmasked recipient or sender number left the server (NFR-SEC-PII-T01, CONST-LEGAL-T01).

## 6. Regression guards summary

| Defect | Guard |
|--------|-------|
| D-T2 | E-1 — tampered institution code does not widen the result |
| D-T6 | Step 9 — numbers masked in the response, verified at the API boundary |
| D-T7 | A-1 — 친구톡 rows read FT |
| D-T8 | E-4 — filters narrow the 친구톡 result |
| D-T9 | E-3 — 20-character serial matches its own transaction |
| D-T13 | E-2 — explicit unsupported-service message |
| D-T21 | E-6 — 11-digit input accepted |
| D-T22 | E-5 — null result code reachable |
| D-T30 | Step 6 — the client uses the server's total count |
