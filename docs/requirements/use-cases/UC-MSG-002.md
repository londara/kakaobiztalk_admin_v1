# Use Case UC-MSG-002: View 문자상세내역 (message detail)

> **REQ-ID**: UC-MSG-002
> **Version**: 1.0
> **Predecessor**: [REQUIREMENTS-SPEC.md](../REQUIREMENTS-SPEC.md)
> **Legacy origin**: screen 41 — `biztalk_admin_41_view.jsp` / `biztalk_admin_41.js` / `WSVC.biztalk_admin_41_l001` / `IDO.KKO_SMS_MSG_L001`, `KKO_MMS_MSG_L001`, `KKF_SMS_MSG_L001`, `KKF_MMS_MSG_L001`

---

## 1. Scenario overview

| Item | Content |
|------|---------|
| **Primary user** | Client-company admin (이용기관 담당자) |
| **Secondary user** | Internal operator |
| **Precondition** | UC-MSG-001 completed; a result row selected |
| **Trigger** | User clicks 메시지키 in the 문자내역 grid |
| **Success outcome** | Full detail of one message — content, delivery timestamps, template/profile data, and failure information where applicable |
| **Failure outcome** | Validation error, not-found state, or access denial |
| **Related FR** | FR-MSGD-001…008 |
| **Related BR** | BR-002, BR-005, BR-006, BR-007 |

## 2. Main flow

| Step | Actor | Action | System response |
|------|-------|--------|-----------------|
| 1 | User | Clicks 메시지키 in UC-MSG-001 Step 7 | Detail view opens with `MSGKEY`, `ID`, `TABLE_TYPE`, `MSG_TYPE`, `REQDATE`, `STATUS` |
| 2 | System | Validates the request | Session checked (FR-MSGD-001); `MSG_TYPE` and `TABLE_TYPE` present and recognised (FR-MSGD-003); `MSGKEY` numeric (CONST-DATA-03) |
| 3 | System | Selects the query | `AT`+`SMS`→`KKO_SMS_MSG`, `AT`+`MMS`→`KKO_MMS_MSG`, `FT`+`SMS`→`KKF_SMS_MSG`, `FT`+`MMS`→`KKF_MMS_MSG`, each unioned with its `_LOG` archive (FR-MSGD-002) |
| 4 | System | Executes | Matched on `REQDATE` + `STATUS` + `MSGKEY`; tenant ownership verified (FR-MSGD-001) |
| 5 | System | Renders detail | All 19 fields (FR-MSGD-004); timestamps as `YYYY-MM-DD HH:MM:SS` (FR-MSGD-005); phone columns masked (NFR-SEC-PII); audit record written |
| 6 | System | Renders sections | Message content; image/button section for MMS/친구톡; failure section when the send failed (FR-MSGD-006). Content fields read-only (FR-MSGD-007) |
| 7 | User | Closes | Returns to UC-MSG-001 with criteria preserved (NFR-USE-01) |

## 3. Alternative flows

### 3.1 A-1: 알림톡 (AT) message
- Branches at Step 3 → `KKO_*` tables. `TEMPLATE_CODE` and `PROFILE_KEY` are meaningful and displayed.

### 3.2 A-2: 친구톡 (FT) message
- Branches at Step 3 → `KKF_*` tables. `AD_FLAG` (advertising) and `WI_FLAG` are relevant; image and button content more likely present.

### 3.3 A-3: MMS message
- Branches at Step 3 → `*_MMS_MSG`. `IMG_PATH` / `IMG_URL` populated; image section shown.

### 3.4 A-4: Failed send
- Branches at Step 6. `FAILED_TYPE`, `FAILED_SUBJECT`, `FAILED_IMG`, `FAILED_MSG` populated.
- Result: failure section displayed. **This is new capability** — these fields were declared but never populated in the legacy (D9), and the tab intended to show them was commented out.

### 3.5 A-5: Record found only in archive
- Branches at Step 4. Row resides in a `_LOG` table.
- Result: returned identically — the union covers both.

## 4. Exception flows

### 4.1 E-1: Missing MSG_TYPE or TABLE_TYPE
- At Step 2.
- Action: reject with a validation error. *(Legacy threw `JexWebBIZException("Input Parameter Error")` — preserved, with a user-facing message.)*

### 4.2 E-2: Unrecognised MSG_TYPE
- At Step 2. Value is neither `AT` nor `FT`.
- Action: **reject.** Deviation from legacy, which routed any non-`AT` value to the `KKF` (친구톡) tables via a bare `else`, silently querying the wrong table for bad input (FR-MSGD-003).

### 4.3 E-3: Non-numeric MSGKEY
- At Step 2.
- Action: reject before the database. The legacy `CAST(:MSGKEY AS INTEGER)` would raise a DB-level error (CONST-DATA-03).

### 4.4 E-4: Cross-tenant access attempt
- At Step 4. A user requests a message key belonging to another tenant.
- Action: treated as not-found; no field values disclosed. Logged as a security event.

### 4.5 E-5: No matching record
- At Step 4.
- Action: explicit "해당 내역을 찾을 수 없습니다" state (FR-MSGD-008). *(Legacy rendered an empty form.)*

### 4.6 E-6: Unauthenticated session
- At Step 2. Action: 401 and redirect to login.

## 5. NFR mapping

| NFR-ID | Application |
|--------|-------------|
| NFR-PERF-03 | Step 4: P95 < 1 s |
| NFR-SEC-AUTH | Step 2: session required |
| NFR-SEC-TENANT | Step 4: ownership verified before disclosure |
| NFR-SEC-PII | Step 5: 발신/수신번호 masked |
| NFR-OPS-AUDIT | Step 5: audit record per invocation |
| NFR-USE-01 | Step 7: list criteria preserved |

## 6. Screen sketch

```
┌─ 문자상세내역 ─────────────────────────────────────────────┐
│ 메시지키 12345      유형 알림톡(AT)     테이블 SMS         │
│ 상태 전송완료       톡결과 0                               │
├─ 발송 정보 ────────────────────────────────────────────────┤
│ 발신번호 010-**-**      수신번호 010-**-**                 │
│ 요청일시 2026-08-14 09:12:33   발송일시 2026-08-14 09:12:35│
│ 결과일시 2026-08-14 09:12:40   응답일시 2026-08-14 09:12:41│
├─ 템플릿 ───────────────────────────────────────────────────┤
│ TEMPLATE_CODE  PROFILE_KEY   AD_FLAG   WI_FLAG             │
├─ 메시지 내용 ──────────────────────────────────────────────┤
│ [ read-only body                                        ]  │
├─ 이미지 / 버튼 ────────────────────────────────────────────┤
│ IMG_URL   BUTTON_JSON (read-only)                          │
├─ 실패 정보 (실패 시) ──────────────────────────────────────┤
│ FAILED_TYPE  FAILED_SUBJECT  FAILED_MSG  FAILED_IMG        │
│                                                  [ 닫기 ]  │
└────────────────────────────────────────────────────────────┘
```

## 7. Test scenarios

| Test ID | Case | Input | Expected |
|---------|------|-------|----------|
| TC-002-01 | AT + SMS | Valid key | Detail from `KKO_SMS_MSG` |
| TC-002-02 | AT + MMS | Valid key | Detail from `KKO_MMS_MSG` |
| TC-002-03 | FT + SMS | Valid key | Detail from `KKF_SMS_MSG` |
| TC-002-04 | FT + MMS | Valid key | Detail from `KKF_MMS_MSG` |
| TC-002-05 | **D5 regression** | Any record | Year is 4 digits — `2026-08-14`, never `20266-08-14` |
| TC-002-06 | **D5 regression** | AT+SMS, FT+SMS, FT+MMS | All three formerly-broken paths render correctly |
| TC-002-07 | **D9 regression** | 알림톡 with a template | `TEMPLATE_CODE`, `PROFILE_KEY` populated, not blank |
| TC-002-08 | **D9 regression** | Failed send | `FAILED_TYPE`/`SUBJECT`/`MSG` populated and shown |
| TC-002-09 | **D9 regression** | MMS with image | `IMG_URL`/`IMG_PATH` populated; image section rendered |
| TC-002-10 | **D9 regression** | Message with buttons | `BUTTON_JSON` populated and shown read-only |
| TC-002-11 | Missing MSG_TYPE | `MSG_TYPE=""` | Validation error |
| TC-002-12 | **Deviation** | `MSG_TYPE="XX"` | Rejected — legacy would query `KKF` tables |
| TC-002-13 | Non-numeric key | `MSGKEY="abc"` | Rejected before the DB |
| TC-002-14 | Cross-tenant | Another tenant's key | Not-found; no data disclosed |
| TC-002-15 | Archive record | Key only in `_LOG` | Returned |
| TC-002-16 | Not found | Nonexistent key | Explicit not-found state |
| TC-002-17 | Unauthenticated | No session | 401 |
| TC-002-18 | Masking | Any record | Phone fields masked |
| TC-002-19 | Read-only | Attempt to edit `MSG` | Not editable, not submittable |
| TC-002-20 | Return to list | Close after search | UC-MSG-001 criteria preserved |
