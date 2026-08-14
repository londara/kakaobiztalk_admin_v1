# Use Case UC-MSG-001: Search 문자내역 (message history)

> **REQ-ID**: UC-MSG-001
> **Version**: 1.0
> **Predecessor**: [REQUIREMENTS-SPEC.md](../REQUIREMENTS-SPEC.md)
> **Legacy origin**: screen 40 — `biztalk_admin_40_view.jsp` / `biztalk_admin_40.js` / `WSVC.biztalk_admin_40_l001` / `IDO.KKB_MSG_L002`

---

## 1. Scenario overview

| Item | Content |
|------|---------|
| **Primary user** | Client-company admin (이용기관 담당자) |
| **Secondary user** | Internal operator |
| **Precondition** | Authenticated session established; user's 이용기관 resolved server-side |
| **Trigger** | User opens 문자내역 and clicks 조회 |
| **Success outcome** | A paginated, tenant-scoped list of BizTalk/SMS messages matching the criteria, with masked phone numbers |
| **Failure outcome** | Validation error (invalid range), empty result set, or an explicit error state |
| **Related FR** | FR-MSG-001…017, FR-TEN-001…004 |
| **Related BR** | BR-002, BR-003, BR-005, BR-006, BR-007 |

## 2. Main flow

| Step | Actor | Action | System response |
|------|-------|--------|-----------------|
| 1 | User | Opens 문자내역 | Session verified (FR-MSG-001); tenant resolved from session (FR-TEN-001). Tenant users see no institution selector; operators see one populated with 이용기관 (FR-TEN-003) |
| 2 | System | Initialises the form | 요청일자 start and end default to today. Time fields empty |
| 3 | User | Enters criteria — date/time range, plus any of 메시지키, 발신번호, 수신번호, 상태, 유형, 문자타입, 결과코드 | Client-side validation of formats |
| 4 | User | Clicks 조회 | Range validated as full date-time (FR-MSG-012) and against the 31-day cap (FR-MSG-013). Empty time defaults to `000000` start / `235959` end |
| 5 | System | Executes the query | Unions the 8 live+archive sources (FR-MSG-003), applies the tenant filter server-side (FR-TEN-002), applies optional filters (FR-MSG-015), boundaries uniform across sources (FR-MSG-011) |
| 6 | System | Returns the page | Page slice plus total count (FR-MSG-007); phone columns masked (NFR-SEC-PII); audit record written (NFR-OPS-AUDIT) |
| 7 | System | Renders the grid | 12 columns (FR-MSG-004); 상태 rendered as label (FR-MSG-005); 메시지키 rendered as a link (FR-MSG-014) |
| 8 | User | Pages through results | Each page is a fresh server request preserving criteria |

## 3. Alternative flows

### 3.1 A-1: Operator queries across institutions
- Branches at Step 1. The operator selects a specific 이용기관 or 전체.
- Result: FR-TEN-003 permits the wider scope; a tenant user attempting the same parameter is ignored (FR-TEN-001).

### 3.2 A-2: No filters beyond the date range
- Branches at Step 3. Only the range is supplied.
- Result: all message classes returned for the tenant within the range (FR-MSG-015).

### 3.3 A-3: Message-class narrowing
- Branches at Step 3. 유형 `AT` and 문자타입 `SMS` selected.
- Result: effectively `KKO_SMS_MSG` + `KKO_SMS_MSG_LOG` only (FR-MSG-006).

### 3.4 A-4: Drill into a message
- Branches at Step 7. User clicks 메시지키.
- Result: UC-MSG-002 begins, carrying `MSGKEY`, `ID`, `TABLE_TYPE`, `MSG_TYPE`, `REQDATE`, `STATUS`.

## 4. Exception flows

### 4.1 E-1: Range exceeds the cap
- At Step 4. Period > 31 days.
- Action: reject with a message naming the limit (FR-MSG-013). No query issued.

### 4.2 E-2: Start later than end
- At Step 4. Full date-time comparison (FR-MSG-012).
- Action: reject with 시작일시가 종료일시보다 클 수 없습니다. **Regression guard:** `2026-01-01 18:00 ~ 2026-01-05 09:00` must be *accepted* — the legacy rejected it (D8).

### 4.3 E-3: Unauthenticated or expired session
- At any step.
- Action: reject with 401 and redirect to login. **Regression guard:** the legacy list service was callable anonymously (D1); an unauthenticated call must never return data.

### 4.4 E-4: Cross-tenant attempt
- At Step 5. A crafted request supplies another tenant's 이용기관 identifier.
- Action: the supplied value is ignored; the session tenant is used (FR-TEN-001). The attempt is logged as a security event.

### 4.5 E-5: Database unavailable
- At Step 5.
- Action: no partial results; error state shown, error logged without PII (NFR-SEC-LOG).

### 4.6 E-6: No matching records
- At Step 6.
- Action: empty grid with an explicit "조회 결과가 없습니다" state — distinguishable from an error.

## 5. NFR mapping

| NFR-ID | Application |
|--------|-------------|
| NFR-PERF-01 | Step 5–6: P95 < 3 s over a 31-day range |
| NFR-PERF-02 | Step 6: default 50 rows/page |
| NFR-PERF-04 | Step 5: decryption limited to returned rows where the plan allows |
| NFR-SEC-AUTH | Step 1: session required |
| NFR-SEC-TENANT | Step 5: server-side tenant filter |
| NFR-SEC-PII | Step 6–7: 발신번호/수신번호 masked |
| NFR-OPS-AUDIT | Step 6: audit record per invocation |

## 6. Screen sketch

```
┌─ 문자내역 ─────────────────────────────────────────────────┐
│ 이용기관 [▼ 전체        ]   (operator only)                │
│ 요청일자 [2026-08-14][000000] ~ [2026-08-14][235959]       │
│ 메시지키 [________]  발신번호 [________]  수신번호 [_______]│
│ 상태     [▼ 전체 ]   유형     [▼ 전체 ]   문자타입[▼ 전체 ]│
│ 결과코드 [________]                            [ 조회 ]    │
├────────────────────────────────────────────────────────────┤
│유형│테이블│메시지키│이용기관│상태│톡결과│발송번호│수신번호│…│
│ AT │ SMS  │ 12345  │ ABC사  │전송완료│ 0  │010-**-**│010-**-**│ │
├────────────────────────────────────────────────────────────┤
│                    ◀ 1 2 3 … ▶   총 1,234 건               │
└────────────────────────────────────────────────────────────┘
```

## 7. Test scenarios

| Test ID | Case | Input | Expected |
|---------|------|-------|----------|
| TC-001-01 | Normal search | Today's range, no other filters | Tenant's rows returned, paginated |
| TC-001-02 | **D8 regression** | `2026-01-01 18:00 ~ 2026-01-05 09:00` | **Accepted** and executed |
| TC-001-03 | Start after end | `2026-01-05 09:00 ~ 2026-01-01 18:00` | Validation error |
| TC-001-04 | Range cap | 32-day range | Rejected, limit stated |
| TC-001-05 | **D2 regression** | 메시지키 `12345` for an existing row | Row returned (legacy returned none) |
| TC-001-06 | **D2 regression** | 메시지키 single digit `7` | Correct row(s) only |
| TC-001-07 | **D3 regression** | 발신번호 search | Filters the sender column, not the recipient |
| TC-001-08 | **D4 regression** | 수신번호 search | Applied server-side and narrows results |
| TC-001-09 | **D4 regression** | 결과코드 search | Applied server-side |
| TC-001-10 | **D6 regression** | Row at exactly `END_DT` in `KKO_MMS_MSG_LOG` | Excluded — same as the other 7 sources |
| TC-001-11 | **D1 regression** | Call the list API with no session | 401, no data |
| TC-001-12 | **D7 regression** | Range with 10,000 matches | Only one page returned, total count accurate |
| TC-001-13 | Tenant isolation | Tenant A requests tenant B's institution id | Only tenant A's rows |
| TC-001-14 | Status rendering | Rows with STATUS 1,2,3,4,6 | 미전송/전송완료/톡결과수신/문자결과수신/큐입력 |
| TC-001-15 | Unmapped status | Row with STATUS `5` | Displayed verbatim, not blank (AMB-05) |
| TC-001-16 | PII masking | Any result row | 발신/수신번호 masked; unmasked value absent from the payload |
| TC-001-17 | **D-side effect** | Rows with NULL 발신번호, no 발신번호 filter | Rows still returned (FR-MSG-016) |
| TC-001-18 | Archive coverage | Message present only in a `_LOG` table | Returned |
| TC-001-19 | All four classes | AT/FT × SMS/MMS rows in range | All returned with correct 유형·테이블 |
| TC-001-20 | Empty result | Range with no messages | Explicit empty state, not an error |
