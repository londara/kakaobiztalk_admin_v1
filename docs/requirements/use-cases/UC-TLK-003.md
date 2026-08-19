# Use Case UC-TLK-003: Read one message's full detail

> **REQ-ID**: UC-TLK-003
> **Version**: 1.0
> **Predecessor**: [REQUIREMENTS-SPEC-TALK.md](../REQUIREMENTS-SPEC-TALK.md)
> **Legacy origin**: screen 31 — `biztalk_admin_31_view.jsp` / `biztalk_admin_31.js` / `WSVC.biztalk_admin_31_l001` / `IDO.KKO_MSG_L002`, `KKO_MSG_LOG_L002`, `KKF_MSG_L002`, `KKF_MSG_LOG_L002`

---

## 1. Scenario overview

| Item | Content |
|------|---------|
| **Primary user** | Internal operator investigating a delivery |
| **Precondition** | UC-TLK-002 has listed the message |
| **Trigger** | User clicks a 메시지키 |
| **Success outcome** | The message's content, attachment and failback detail are shown read-only, with every field either populated or explicitly marked absent |
| **Failure outcome** | A message key belonging to another institution is not retrievable; a message that cannot be found produces an explicit message, not a blank form |
| **Related FR** | FR-AZ-T02, FR-AZ-T04, FR-AZ-T05, FR-TLKM-001…008 |
| **Related NFR** | NFR-PERF-T02, NFR-SEC-PII-T01, NFR-SEC-TENANT-T01, NFR-USE-T01 |
| **Related CONST** | CONST-DATA-T01, CONST-LEGAL-T01, CONST-BIZ-T01 |
| **Related BR** | BR-005, BR-006, BR-007 |

## 2. Main flow

| Step | Actor | Action | System response |
|------|-------|--------|-----------------|
| 1 | User | Clicks a 메시지키 in the transaction detail | The message detail opens |
| 2 | System | Resolves the key | The address includes the **owning institution**; a message key alone is not sufficient (FR-AZ-T04, CONST-BIZ-T01) |
| 3 | System | Selects the table | The channel's own live or archive table (FR-TLKM-006) |
| 4 | System | Executes the query | Keyed only on immutable columns (FR-TLKM-004) |
| 5 | System | Renders 메시지정보 | 프로필, 광고여부, 톡결과, 문자결과, 템플릿코드, 발신번호, 수신자번호, 요청·송신·통신사응답·결과수신 시간, 전송메시지 (FR-TLKM-001) |
| 6 | System | Renders 발신번호 / 수신자번호 | Populated and **masked** (FR-TLKM-002, NFR-SEC-PII-T01) |
| 7 | System | Renders timestamps | 4-digit year (FR-TLKM-003) |
| 8 | System | Renders 톡결과 / 문자결과 | The code always, plus its description when known, plus a 미등록 코드 marker when not (FR-TLKM-005, NFR-USE-T01) |
| 9 | User | Switches to 첨부 | 이미지경로, 이미지URL, 와이드 이미지 여부, 버튼JSON — populated or explicitly absent (FR-TLKM-001, FR-TLKM-007) |
| 10 | User | Switches to FailBack | 문자전송타입, 문자전송제목, 문자내이미지주소, 문자내용 — populated or explicitly absent, and not editable (FR-TLKM-007) |
| 11 | System | Writes an audit event | Actor, timestamp, message key, institution (FR-AZ-T05) |

## 3. Alternative flows

### 3.1 A-1: Archived message
- At Step 3. The archive table for the same channel is read; the presentation is identical (FR-TLKM-006).

### 3.2 A-2: 친구톡 message
- At Step 3. The 친구톡 tables are read (FR-TLKM-006).

### 3.3 A-3: Message whose status changed since the list was drawn
- At Step 4. The record is still retrieved, and the current status is what is shown (FR-TLKM-004).

## 4. Exception flows

### 4.1 E-1: Message key from another institution
- At Step 2, by crafting a request.
- Action: 404 (FR-AZ-T04, NFR-SEC-TENANT-T01). **Regression guard:** `IDO.KKO_MSG_L002` keyed on `REQDATE` + `STATUS` + `MSGKEY` with no institution, so a message key alone read another institution's message body, template code and phone numbers (D-T5).

### 4.2 E-2: Status advanced between list and click
- At Step 4.
- Action: the message is found (FR-TLKM-004). **Regression guard:** `AND STATUS = :STATUS` in the key returned zero rows and the popup rendered blank with no message (D-T19).

### 4.3 E-3: 친구톡 message opened
- At Step 3.
- Action: the 친구톡 table is queried (FR-TLKM-006). **Regression guard:** `KKB_FT_MSG_L001` labelled every row `'AT'`, and the detail action branched on that label, so a 친구톡 message was looked up in `KKO_MSG` and returned nothing (D-T7).

### 4.4 E-4: Result code absent from the error dictionary
- At Step 8.
- Action: the code is shown with a 미등록 코드 marker (FR-TLKM-005). **Regression guard:** `RSLT || '(' || (SELECT ERR_MSG …) || ')'` produced NULL for an unmatched code, blanking the field precisely for unrecognised failures (D-T20).

### 4.5 E-5: The message is not found
- At Step 4.
- Action: an explicit not-found message, not a blank form (FR-TLKM-007).

## 5. Postconditions

- No row is written (CONST-DATA-T01).
- An audit record exists for the message read (FR-AZ-T05).
- No unmasked number and no message body left the server outside the authorized scope (NFR-SEC-PII-T01, CONST-LEGAL-T01).

## 6. Regression guards summary

| Defect | Guard |
|--------|-------|
| D-T5 | E-1 — cross-institution message key returns 404 |
| D-T6 | Step 6 — numbers masked |
| D-T7 | E-3 — 친구톡 detail reads the 친구톡 table |
| D-T17 | Step 7 — 4-digit year |
| D-T18 | Step 6 — 발신번호 and 수신자번호 are non-empty on a fixture that has them |
| D-T19 | E-2 — status change between list and detail does not empty the popup |
| D-T20 | E-4 — unknown result code still displays |
| D-T33 | Step 10 — the FailBack textarea is not editable |
| D-T34 | Step 1 — the popup is titled for what it shows |
