# 보안 감사 리포트 — Sprint R1 (이용기관 보고서) + Sprint T1/T2 (톡전송 내역)

> **작성**: `security-auditor` (Validation Team Leader, Skill 05 단계 [C])
> **일자**: 2026-08-20
> **대상 커밋 범위**: `0a987dd..f8e7d47` — `git diff --name-only $(git merge-base 0acfb39 f8e7d47) f8e7d47 -- src/`
> **판정**: ⚠️ **조건부 승인 / APPROVED WITH CONDITIONS** — CVSS ≥ 7.0 결함 0 건이므로 **G3 를 차단하지 않는다.**
> **조건**: SEC-RT-09(마스킹 함수 증적)와 SEC-RT-11(감사 보존기간 5년 vs 7년 충돌)은 **G3 결재 전 운영 증적 또는 PM 재결정**이 필요하다. 코드로 닫을 수 없다.

---

## 0. 이 감사의 한계 / limitation

이 감사는 **저장소 안의 아티팩트만** 근거로 한다. 다음 세 가지는 저장소에서 확인할 수 없었고, 확인할 수 없다는 사실 자체를 결함으로 기록했다.

1. `masking()` / `decrypt()` 는 **사이트 정의 PostgreSQL 함수**다. 정의가 저장소에 없다 → SEC-RT-09.
2. `IRIS_AUTH_AUDIT` 의 append-only 성질은 **DB 권한**으로 보장한다고 ADR-007 이 주석으로 선언하지만, 그 권한을 부여·검증하는 마이그레이션이 없다 → SEC-RT-11.
3. 의존성 취약점 스캔(OWASP Dependency-Check)은 **이번 감사에서 실행하지 않았다.** A06 은 PASS 가 아니라 **미검증**이다.

문서가 "구현했다"고 말한 것은 근거로 채택하지 않았다. **문서 주장과 코드가 어긋난 곳은 그 자체를 결함으로 올렸다** — SEC-RT-04, SEC-RT-06, SEC-RT-07, SEC-RT-08 이 그것이다.

---

## 1. 감사 범위

| 구분 | 대상 |
|------|------|
| 백엔드 | `com.webcash.iris.biztalk.**` — `api/` 8, `domain/` 27, `infra/db/` 5, `infra/excel/` 1, `config/` 2 |
| 공통 | `common/audit/AuditEvent.java`, `common/logging/GlobalExceptionHandler.java`, `common/tenant/PrincipalScope.java` |
| MyBatis XML | `TalkHistoryMapper.xml`, `TalkMessageMapper.xml`, `ApiAggregateMapper.xml`, `bulk/BulkAggregateMapper.xml` |
| 프론트엔드 | `features/biztalk/{ReportPage,TalkHistoryPage,TalkMessageDetailPanel,TalkTransactionDetailPanel}.tsx`, `api/{reportApi,talkDetailApi,talkHistoryApi}.ts` |
| 경계 밖이나 참조로 읽음 | `auth/config/SecurityConfig.java`, `common/tenant/TenantContext.java`, `common/audit/AuditService.java`, `db/V1__auth_session_audit.sql`, `pom.xml` |
| **제외** | A1/A2 알림톡 슬라이스 (`biztalk/alimtalk/**`) — `security/audit-alimtalk.md` 소관 |

**전 CVSS 점수에 걸리는 구조적 사실**: `SecurityConfig.java:136` 이 `/api/admin/**` 를 `hasRole("OPERATOR")` 로 닫고, 이 슬라이스의 **모든** 엔드포인트가 추가로 메서드 단위 `@PreAuthorize("hasRole('OPERATOR')")` 를 단다. 따라서 이 슬라이스에서 도달 가능한 모든 결함은 **PR:H** 이며, 운영자는 이미 전 기관 데이터에 정당하게 접근한다. 이것이 아래 점수가 전반적으로 낮은 이유이고, **점수가 낮은 것이 통제가 강해서가 아니라 공격자 자격이 이미 높아서**라는 점을 판정에 반영해야 한다.

---

## 2. OWASP Top 10 (2021) 점검

| ID | 항목 | 결과 | 근거 |
|----|------|------|------|
| A01 | Broken Access Control | **PASS (주의)** | `SecurityConfig:136` + 메서드 `@PreAuthorize` 이중 게이트. `TalkHistoryAuthorizationTest` 가 익명 401 / 테넌트 403 / 운영자 200 을 실제로 단언. **단** 보고서 엔드포인트에는 동종 테스트가 없다 → SEC-RT-08 |
| A02 | Cryptographic Failures | **CONDITIONAL** | PHONE/CALLBACK 은 DB 에서 암호화 저장되고 `masking(decrypt(...))` 로만 나간다. 알고리즘·키 관리가 저장소 밖 → SEC-RT-09 |
| A03 | Injection | **PASS** | MyBatis 매퍼 전수 조사 결과 `${}` 문자열 보간 **0 건**(유일한 매치는 "쓰지 않는다"는 주석). 동적 테이블 선택은 `<choose>` + 리터럴. 동적 정렬·기간·기관 필터 전부 `#{}` 바인딩 |
| A04 | Insecure Design | **PARTIAL** | 인가 규칙 단일화(`PrincipalScope`)는 좋은 설계. 그러나 상세 계층 결속이 매퍼 두 곳에서 불일치 → SEC-RT-01 |
| A05 | Security Misconfiguration | **PASS** | 하드코딩 자격증명 0. `DataSourceBuilder` + `@ConfigurationProperties(prefix="iris.report.bulk")` 로 외부화 |
| A06 | Vulnerable Components | **미검증** | POI 5.2.5 신규 도입(`pom.xml:143-145`). 의존성 스캔 미실행 — §11 참조 |
| A07 | Identification & Auth Failures | **범위 밖** | 로그인 슬라이스 소관. 이 범위에서 세션 처리 변경 없음 |
| A08 | Data Integrity Failures | **FAIL(경미)** | 감사 로그에 해시 체인·서명 없음 → SEC-RT-11 |
| A09 | Logging & Monitoring Failures | **FAIL(경미)** | export 감사에 접속지 IP·상관 ID 누락 → SEC-RT-05. 감사 상세가 비정형 텍스트 → SEC-RT-11 |
| A10 | SSRF | **해당 없음** | 이 범위에 아웃바운드 요청 없음. `imageUrl` 은 서버가 fetch 하지 않고 문자열로만 렌더 |

**A03 전수 결과 (감사 요청 항목 5)**

```
grep -ro '\${' src/main/resources/mybatis/                      →  1 (주석: "동적 식별자 보간은 쓰지 않는다")
grep -rn '@SelectProvider|@Select.*\+' biztalk/{api,domain,infra} →  0
```

`TalkMessageMapper.xml` 의 4개 테이블 분기(`KKO_MSG` / `KKO_MSG_LOG` / `KKF_MSG` / `KKF_MSG_LOG`)는 채널 문자열 조립이 아니라 `<choose>` 로 이름을 문자 그대로 쓴다. `LIMIT #{c.size} OFFSET #{c.offset}` 도 바인딩이다. 동적 정렬은 존재하지 않는다 — `ORDER BY` 가 전부 리터럴 고정이다. **SQL 인젝션 결함 0 건.**

---

## 3. 시크릿 / 자격증명 점검

| 항목 | 검증 방법 | 결과 |
|------|---------|------|
| 하드코딩 시크릿 | 범위 내 96 파일 정규식 스캔 (`password\|secret\|api[_-]?key\|token\|BEGIN .* PRIVATE\|AKIA[0-9A-Z]{16}`) | **0 건** (매치는 전부 `passwordChangeRequired` 필드명·`auth.password.change` 액션 상수) |
| 환경변수 외부화 | `ReportDataSourceConfig.java` | **PASS** — `DataSourceBuilder.create().build()` + `@ConfigurationProperties("iris.report.bulk")`. URL/계정 리터럴 없음 |
| 커넥션 정보 노출 | `SourceAvailability.incompleteNotes` | **FAIL** → SEC-RT-02 (DB 예외 메시지가 응답 본문으로 나간다) |
| 키 회전 | 이 범위에 신규 키 없음 | 해당 없음 (알림톡 SEC-A01 은 별건, 여전히 OPEN) |

---

## 4. PII / 민감정보 처리

### 4.1 CONST-SEC-T01 — 금지 컬럼 전수 대조 (감사 요청 항목 3)

`FT_APITR_HSTR` 는 전체 핀테크 API 거래 로그이며 25 컬럼 중 다음 8 개를 **절대 select 하면 안 된다**: `FIN_ACNO`, `ACNO`, `CANO`, `FIN_CARD`, `TRAM`, `BRNO`, `INTT_DMND_TTNO`, `RSPN_TLGR_CNTN`.

**매퍼 4종을 실제로 읽어 프로젝션을 축출한 결과:**

| 매퍼 / 문장 | 실제 select 컬럼 | 금지 컬럼 |
|---|---|---|
| `TalkHistoryMapper.findPage` | `TRDD, FINTECH_ISCD, ISNM, IS_TUNO, API_SVC_CD, PRSU, FINTECH_RPCD, RGDT, LAST_AMDT` (9) | **0** |
| `TalkHistoryMapper.countAll` | `count(1)` | **0** |
| `TalkHistoryMapper.findObservedApiServices` | `API_SVC_CD, count(1)` | **0** |
| `TalkMessageMapper.findTransactionOwner` | `FINTECH_ISCD, API_SVC_CD` (2) | **0** |
| `TalkMessageMapper.findMessages` | `SERIALNUM, MSGKEY, ID, STATUS, RSLT, RSLT_TEXT, MSG_RSLT, MSG_RSLT_TEXT, CALLBACK*, PHONE*, REQDATE, REQTIME, SENTTIME, REPORTTIME, TABLE_TYPE` (15) | **0** |
| `TalkMessageMapper.findDetail` | 25 컬럼, `CALLBACK*`/`PHONE*` 포함 | **0** |
| `ApiAggregateMapper` 4문 | `TRDD, IS_CD, IS_NM` + 채널 카운터 28 | **0** |
| `BulkAggregateMapper` 3문 | 동일 형태 | **0** |

`*` = `masking(decrypt(...))` 로 감쌈.

> **CONST-SEC-T01 판정: PASS.** `SELECT *` 0 건, 금지 8 컬럼 등장 0 건, 쓰기 문장 0 건. 결정적으로 **금지 컬럼을 담은 테이블(`FT_APITR_HSTR`)을 읽는 문장은 4 개뿐이고 그 프로젝션이 각각 9/1/2/2 컬럼으로 닫혀 있다** — 실수로 넓어질 여지가 구조적으로 작다. CRITICAL 사유 없음.

### 4.2 마스킹 경로 — 화면 · API · **export** (감사 요청 항목 2)

| 렌더링 경로 | 수신번호 / 발신번호 | 근거 |
|---|---|---|
| 거래내역 목록 (화면 30) | **PII 자체가 없음** | 9 컬럼 프로젝션에 전화번호 컬럼이 존재하지 않음 |
| **엑셀 export** | **PII 자체가 없음** | `TalkExportService.HEADERS` = 일자/기관코드/기관명/거래고유번호/API/상태/응답코드/등록시각/완료시각 — 목록과 동일 9 열 |
| 거래 상세 (화면 32) | `masking(decrypt(...))` | `TalkMessageMapper.xml:280-281` |
| 메시지 상세 (화면 31) | `masking(decrypt(...))` | `TalkMessageMapper.xml:339-340` |
| 프론트 렌더 | `senderMasked` / `recipientMasked` | `TalkTransactionDetailPanel.tsx:278-279`, `TalkMessageDetailPanel.tsx:129-132` |
| 애플리케이션 로그 | 번호를 로깅하는 문장 **0 건** | `Talk*.java` 의 `log.` 전수 — 전부 API 코드·건수·기간만 |

> **⚠ 감사 요청 항목 2의 핵심 우려 — export 가 화면 마스킹을 우회하는가 — 는 성립하지 않는다. 그 이유가 중요하다.**
>
> SPRINT-T2 의 알려진 위험은 "목록 화면과 export 가 서로 다른 테이블을 조회한다"였다. **코드는 그렇지 않다.** `TalkExportController.export` → `historyService.criteriaFor(...)` → `TalkExportService.export` → `PagedRowIterable.fetch()` → **`mapper.findPage(pageCriteria)`** 로, 목록과 **문자 그대로 같은 매퍼 문장**을 페이지 크기 1,000 으로 반복 호출한다. 스코프 술어·기간 술어·API 화이트리스트가 같은 `<sql id="whereClause">` 조각에서 나오므로 갈라질 수 없다.
>
> `TalkExportParityTest` 가 이것을 의미 수준에서 단언한다 — `unfilteredSetsAreEqual`, `everyFilterCombinationAgrees`(전 필터 조합), `everyFilterChangesTheFile`(필터가 실제로 좁히는지 역검증), `outOfScopeApiIsAbsentFromBoth`. **통제가 있다고 주장만 한 것이 아니라 증명되어 있다.**
>
> 다만 결론의 진짜 근거는 파리티가 아니라 **export 대상 9 열에 PII 가 애초에 없다**는 사실이다. 향후 export 컬럼이 추가되면 이 논거는 즉시 무효가 된다.

### 4.3 `AtkMasker` 검증 (감사 요청 항목 2)

**`AtkMasker` 는 이 슬라이스 어디에도 쓰이지 않는다.** 유일한 호출부는 `InstitutionService.java:84` 이며 마스킹 대상은 전화번호가 아니라 **기관 인증키(`authKey`)** 다. 다른 슬라이스 소관이다.

즉 이 슬라이스의 PII 마스킹에는 **애플리케이션 계층 구현이 존재하지 않으며, 전량 DB 함수 `masking()` 에 위임되어 있다.** 이것이 SEC-RT-09 의 실체다.

### 4.4 PII 컬럼 카탈로그 (이 범위)

| 컬럼 | 테이블 | 분류 | 저장 | 출력 | 감사 액션 |
|------|--------|------|------|------|------|
| `PHONE` (수신번호) | `KKO_MSG(_LOG)`, `KKF_MSG(_LOG)` | 개인정보 | 암호화(`decrypt()` 필요) | `masking(decrypt())` | `biztalk.talk-history.detail.view` / `.message.view` |
| `CALLBACK` (발신번호) | 동일 | 개인정보 | 암호화 | `masking(decrypt())` | 동일 |
| `MSG` (본문) | 동일 | 준민감 | 평문 | 평문 반환 | 동일 |
| `FIN_ACNO`·`ACNO`·`CANO`·`FIN_CARD`·`TRAM`·`BRNO`·`INTT_DMND_TTNO`·`RSPN_TLGR_CNTN` | `FT_APITR_HSTR` | 민감/금융 | — | **select 금지 (준수 확인)** | — |

---

## 5. 인가 / 테넌시 (감사 요청 항목 1)

### 5.1 `PrincipalScope` — 빈 IS_CD 해석과 무시-vs-거부

`common/tenant/PrincipalScope.java:74-104` 를 실제로 읽고 두 성질을 각각 확인했다.

**(가) 테넌트의 빈 IS_CD 가 "전체"로 해석되는가 → 아니다. PASS.**

```java
String own = principal.effectiveInstitutionCode(null);              // line 93
if (own == null) { throw new TenantScopeUnavailableException(...); } // 94-96
```

`effectiveInstitutionCode` 는 (`TenantContext.java:143-170`) 비운영자이고 `institutionCode` 가 null/blank 면 **예외로 거부**한다. null 을 반환하면 매퍼의 `<if test="c.scope.institutionCode != null">` 이 술어를 만들지 않아 전 기관이 나가는데, 그 경로가 구조적으로 닫혀 있다. 주석이 "운영 스키마에 사용자→기관 매핑이 실제로 없어 이 분기가 활성 경로"라고 밝힌 점도 fail-closed 설계로 옳다.

**(나) 요청 IS_CD 가 검증-후-거부되어 기관 열거 오라클이 되는가 → 아니다. PASS.**

```java
boolean overrideAttempted = requested != null && !requested.equals(own);  // line 102
return new PrincipalScope(own, false, overrideAttempted);                 // line 103
```

거부하지 않고 **무시**한 뒤 기록만 남긴다(`ACTION_TENANT_OVERRIDE_ATTEMPT`, `ReportService.java:130-135`). 오류 메시지가 "그 기관은 존재한다/않는다"를 알려주지 않는다 — TM-T10 대응 확인.

**(다) CONFLICT-R01(운영자만 전체)이 코드로 강제되는가 → 그렇다. PASS.**

`allInstitutions=true` 는 `if (principal.operator())` 블록 안에서만 생성된다(79-85). 테넌트 분기는 `allInstitutions` 를 **문법적으로 참으로 만들 수 없다.** 매퍼의 술어 생략은 오직 이 플래그에 걸려 있다(`ApiAggregateMapper.xml:110`, `BulkAggregateMapper.xml:82`). 두 경로가 한 문장에서 갈라지므로 한쪽만 고쳐질 수 없다.

`ReportScope` 는 로직 없는 위임 별칭(`ReportScope.java:52-54`)이므로 규칙 사본이 아니다 — RISK-T06 대응 확인.

### 5.2 FR-AZ-T01~T04 / FR-AZ-R03~R04

| REQ | 요구 | 코드 | 판정 |
|---|---|---|---|
| FR-AZ-T01 | 전 서비스 미인증 거부 | `SecurityConfig:136` + 5 엔드포인트 `@PreAuthorize` | PASS (`TalkHistoryAuthorizationTest`) |
| FR-AZ-T02 | 톡 화면은 운영자 전용, 테넌트 403 | 동일 | PASS (`tenantIsForbiddenOnList/Filters`) |
| FR-AZ-T03 | 상세의 이용기관은 **서버가 원장에서 재도출** | `TalkDetailService:75,153` → `mapper.findTransactionOwner(key)` → `owner.institutionCode()` 를 criteria 에 주입. 요청 본문에 institution 파라미터 자체가 없음 | PASS |
| FR-AZ-T04 | 메시지 상세 키에 소유 기관 포함, 메시지키 단독 불가 | `TalkMessageDetailKey.of` 가 institutionCode blank 를 거부(61-71), 매퍼 `WHERE A.ID = #{k.institutionCode} AND A.MSGKEY = ...` | **부분 PASS** → SEC-RT-01 |
| FR-AZ-T05 | 조회·상세·export 마다 업무 감사 | §7 | PASS (단 SEC-RT-05/11) |
| FR-AZ-R03/R04 | 보고서는 **역할 의존** — 테넌트도 자기 기관 조회 | `ReportController` 두 메서드 모두 `hasRole('OPERATOR')` | **FAIL(부적합)** → SEC-RT-07 |

### 5.3 CONST-BIZ-T01 — 거래 → 메시지 → 본문 계층 (감사 요청 항목 4)

**부모 없이 MSGKEY 단독으로는 상세에 도달할 수 없다.** URL 이 `/{trdd}/{serial}/messages/{messageKey}` 이고, `TalkDetailService.detail:150-158` 이 먼저 `findTransactionOwner(trdd, serial)` 로 원장을 조회해 없으면 거부한다. **감사 요청 항목 4 의 HIGH 조건은 성립하지 않는다.**

**그러나 계층 결속이 기관 수준에서 끊긴다.** `findDetail` 의 술어는 `A.ID` 와 `A.MSGKEY` 뿐이고 **`SERIALNUM` 이 없다** — 형제인 `findMessages` 는 `messageWhere` 에서 `AND A.SERIALNUM = #{c.serialForMapper}` 를 건다. → SEC-RT-01.

---

## 6. 응답 헤더 / 파일명 (감사 요청 항목 7) — **PASS**

`TalkExportController.headers()` (156-176):

```java
String filename = FILENAME_PREFIX + "_"
        + criteria.window().fromDateYyyymmdd() + "-"
        + criteria.window().toDateYyyymmdd() + ".xlsx";
headers.setContentDisposition(ContentDisposition.attachment()
        .filename(filename, StandardCharsets.UTF_8).build());
```

**방어가 두 겹이다.**

1. `fromDateYyyymmdd()` 는 `LocalDate.format(BASIC_ISO_DATE)` 의 반환값이다(`TalkPeriodPolicy.TalkWindow:164-175`). 사용자 문자열이 아니라 **파싱을 통과한 `LocalDate` 를 서버가 재직렬화한 8 자리 숫자**다. CR/LF 는 `PeriodPolicy.validate` 단계에서 이미 400 으로 떨어진다.
2. `ContentDisposition.filename(String, Charset)` 이 RFC 5987 `filename*=UTF-8''` 퍼센트 인코딩을 적용한다 — CR/LF 가 도달해도 인코딩된다.

`TalkExportControllerTest` 가 `crlfNeverReachesAHeader`(파라미터화), `noHeaderContainsCrlf`(전 헤더 검사), `filenameCarriesOnlyValidatedDates`, `exactlyOneCorrectContentType`, `legacyMediaTypesAreAbsent` 로 이를 **와이어 수준에서** 단언한다. D-T4 / D-R3 / NFR-SEC-HDR-T01 **닫힘.** 결함 없음.

**프론트 XSS 면**: `dangerouslySetInnerHTML` · `innerHTML` · 데이터 유래 `href`/`src` **0 건**. `imageUrl` · `buttonJson` · `failedImage` 는 `<Field>` 로 **문자열 렌더만** 한다(`TalkMessageDetailPanel.tsx:173-194`) — `javascript:` URL 이나 오픈 리다이렉트 표면이 없다. URL 조립은 전부 `encodeURIComponent` / `URLSearchParams`.

---

## 7. 감사 로그 (감사 요청 항목 6)

| 항목 | 검증 위치 | 결과 |
|------|------|------|
| 조회마다 기록 | `TalkHistoryService:99-102`, `TalkDetailService:114,177`, `ReportService:363-371` | **PASS** (성공·실패 양쪽 경로) |
| export 마다 기록 | `TalkExportService:134,149,156` | **PASS** (거부·성공·오류 3 경로) |
| actor | `principal.email()` | PASS |
| scope | `criteria.describe()` / `scope.describe()` | PASS (텍스트) |
| range | `window.describe()` = `fromTimestamp~toTimestamp` | PASS (텍스트) |
| 발송구분 | `ReportService:355` `", source=" + criteria.source()` | PASS (보고서만 해당) |
| 행수 | `" rows=" + n + " total=" + t`, export 는 `" written=" + n` | PASS (텍스트) |
| **접속지 IP** | export 만 `null` | **FAIL** → SEC-RT-05 |
| **구조화** | 전부 `DETAIL VARCHAR(512)` 자유 텍스트 | **FAIL** → SEC-RT-11 |
| **보존기간** | DDL 5년 vs prod-gate 7년 | **충돌** → SEC-RT-11 |
| **무결성(해시체인)** | 없음 | **FAIL** → SEC-RT-11 |
| 접근통제 | INSERT-only 권한을 주석으로 선언, 마이그레이션 없음 | **미검증** → SEC-RT-11 |

> `AuditEvent` 는 요구된 필드를 **담고는 있다.** 단 `scope`/`range`/`rows` 를 별도 컬럼이 아니라 한 개의 문자열에 이어 붙인다. "3월에 누가 X 기관 데이터를 내보냈나"를 **질의로 답할 수 없고** 512 자에서 조용히 잘리는 컬럼을 문자열 매칭해야 한다. FR-AZ-R05/T05 의 문언은 충족하나 감사 목적은 충족하지 못한다.

---

## 8. 규제 / 컴플라이언스

| 규제 | 적용 | 결과 | 비고 |
|------|------|------|------|
| 전자금융감독규정 (접근통제) | Y | **PASS** | 역할 기반 서버측 인가, 전 엔드포인트 이중 게이트 |
| 전자금융감독규정 (기록 보관) | Y | **CONDITIONAL** | 보존기간 5년/7년 이중 기준 미해소 → SEC-RT-11 |
| 개인정보보호법 (수집·이용) | Y | PASS | 신규 수집 없음. 기존 데이터 조회 전용 |
| 개인정보보호법 (안전성 확보조치 §8 접속기록) | Y | **FAIL(경미)** | export 접속기록에 접속지 정보 누락 → SEC-RT-05 |
| 개인정보보호법 (마스킹·암호화) | Y | **CONDITIONAL** | DB 함수 위임, 증적 부재 → SEC-RT-09 |
| 신용정보법 | N | 해당 없음 | 신용정보 컬럼 select 0 (§4.1) |
| PCI-DSS | N | 해당 없음 | `CANO`/`FIN_CARD` select 0 (§4.1) |
| ISMS-P (2.6 접근통제 / 2.9 로그관리) | Y | **CONDITIONAL** | 2.6 PASS. 2.9 는 무결성·보존 미해소 |
| 금융보안원 가이드 | Y | PASS | 응답분할·인젝션·계층우회 주요 항목 방어 확인 |

**규제 위반으로 우회를 시도한 흔적 없음.** SEC-RT-05/09/11 은 은폐가 아니라 미완이며, SEC-RT-09 는 소스 주석과 테스트가 한계를 **스스로 명시**하고 있다.

---

## 9. 발견 사항

### 9.1 CRITICAL (CVSS ≥ 9.0) — **0 건**

| ID | 위치 | 내용 |
|----|------|------|
| — | — | 없음 |

### 9.2 HIGH (CVSS 7.0 ~ 8.9) — **0 건**

| ID | 위치 | 내용 |
|----|------|------|
| — | — | 없음 |

---

### 9.3 MEDIUM (CVSS 4.0 ~ 6.9) — **2 건**

#### SEC-RT-03 — 엑셀 수식 주입 (CWE-1236) · **CVSS 5.8**

`CVSS:3.1/AV:N/AC:H/PR:N/UI:R/S:C/C:L/I:L/A:L`

**위치** `src/main/java/com/webcash/iris/biztalk/infra/excel/StreamingWorkbookWriter.java:116`

```java
bodyRow.createCell(i).setCellValue(value == null ? "" : value);
```

셀 값의 선행 `=`, `+`, `-`, `@`, TAB, CR 에 대한 무력화가 없다. `TalkExportService.cellsOf` 가 셀에 넣는 값 중 **기관명(`ISNM`)** 과 **응답코드(`FINTECH_RPCD`)** 는 이 애플리케이션이 생성하지 않는다 — 후자는 API 게이트웨이가 **상위 핀테크 응답에서 받아 적재**하는 값이다. 조작된 값이 한 번 적재되면 export 를 여는 운영자 워크스테이션에서 수식/DDE 가 평가될 수 있다.

`S:C` — 취약 컴포넌트는 서버측 export 이고 영향 컴포넌트는 운영자 데스크톱 Excel 이다. `UI:R` — 파일을 열고 Excel 경고를 통과해야 한다. `AC:H` — 주입 값이 상류 적재 경로를 먼저 통과해야 한다.

**권장 조치** `StreamingWorkbookWriter` 에서 `= + - @ \t \r` 로 시작하는 문자열 앞에 `'`(아포스트로피)를 삽입하거나, `setCellValue` 전에 선행 문자를 이스케이프한다. 쓰기 지점이 **한 곳**이므로 수정 비용이 낮고, 이 writer 는 보고서 슬라이스 Sprint R2 export 에서도 재사용될 예정이므로 **지금 고치는 것이 가장 싸다.**

---

#### SEC-RT-04 — export 응답 전량 힙 적재 · NFR-SCALE-T01 증적이 운영 경로를 덮지 않음 · **CVSS 4.9**

`CVSS:3.1/AV:N/AC:L/PR:H/UI:N/S:U/C:N/I:N/A:H`

**위치** `src/main/java/com/webcash/iris/biztalk/api/TalkExportController.java:137-142`

```java
ByteArrayOutputStream buffer = new ByteArrayOutputStream();
int written = exportService.export(criteria, buffer);
return ResponseEntity.ok().headers(headers(criteria, written)).body(buffer.toByteArray());
```

`SXSSFWorkbook(WINDOW_ROWS=100)` 이 행을 임시파일로 흘려보내는 이점이 **컨트롤러 경계에서 전부 소멸한다.** `workbook.write(output)` 이 zip 전체를 `ByteArrayOutputStream` 에 쌓고, `toByteArray()` 가 이를 한 번 더 복제한다(피크 ≈ 워크북 2배 + BAOS 배증 성장). 상한은 `ROW_CEILING = 100,000` 행이며 동시 export 에 대한 제한이 없다.

> **문서 주장과 코드의 불일치 (감사 요청 명시 항목).**
>
> `docs/sprints/SPRINT-T2-LOG.md:146` 은 "export heap does not scale with row count" 를 단언한다. NFR 표의 **NFR-SCALE-T01 은 Must** 이며 "Export heap usage is bounded and independent of row count" 다.
>
> 그 근거인 `TalkHistoryLoadTest.heapForExport` 는 `:474` 에서 **`OutputStream.nullOutputStream()`** 에 쓴다. 즉 **컨트롤러를 전혀 지나가지 않는다.** 실제 배포 경로에서 이 Must-NFR 은 **증명되지 않았고, 코드상 성립하지 않는다.**

**권장 조치** `ResponseEntity<StreamingResponseBody>` 로 전환해 `response.getOutputStream()` 에 직접 쓴다. 헤더는 본문 이전에 확정되어야 하므로 `X-Talk-Export-Rows` 는 `countAll` 값으로 선기록하거나 트레일러로 옮긴다. 부하 테스트를 컨트롤러 경계(MockMvc/`@SpringBootTest`)로 끌어올려야 NFR-SCALE-T01 증적이 실제 경로를 덮는다.

---

### 9.4 LOW (CVSS 0.1 ~ 3.9) — **7 건**

#### SEC-RT-01 — `findDetail` 이 메시지를 부모 거래에 결속하지 않음 (CONST-BIZ-T01 부적합) · **CVSS 3.8**

`CVSS:3.1/AV:N/AC:L/PR:H/UI:N/S:U/C:L/I:N/A:L`

**위치** `src/main/resources/mybatis/mapper/biztalk/TalkMessageMapper.xml:370-371` · `src/main/java/com/webcash/iris/biztalk/domain/TalkDetailService.java:163-166`

```xml
 WHERE A.ID     = #{k.institutionCode}
   AND A.MSGKEY = CAST(#{k.messageKey} AS INTEGER)
```

`SERIALNUM` 술어가 없다. 형제 문장 `messageWhere` 는 `AND A.SERIALNUM = #{c.serialForMapper}` 를 건다 — **같은 파일 안에서 두 문장이 다른 계층 규칙을 쓴다.** `trdd`/`serial` 은 소유 기관을 도출하는 데만 쓰이고 술어로 내려가지 않으므로, 기관 X 의 **아무 거래 키** + 기관 X 의 **아무 메시지키** 조합이 성립한다.

**두 번째 문제 — 키가 유일하지 않을 수 있다.** 같은 파일 `:294` 의 설계 주석은 `MSGKEY` 의 유일성을 **"한 거래 안에서"**로 한정한다. 그 진술이 참이면 `(ID, MSGKEY)` 는 유일 키가 아니며, 다중 매치 시 MyBatis 가 `TooManyResultsException` 을 던지거나(→ 500, SEC-RT-06) 임의 행을 반환한다. 코드베이스가 스스로 밝힌 유일성 보장과 실제 사용된 키가 어긋난다.

CONST-BIZ-T01 은 **Must** 이고 검증 방법이 "Security test" 인데, 그 성질을 강제하는 술어가 없으므로 검증 자체가 불가능하다.

**완화 요인** 운영자 전용 화면이고 운영자는 이미 전 기관 열람 권한이 있어 실질 기밀성 증분이 작다. `MSGKEY` 가 실제로 전역 유일하면 두 번째 문제는 소멸한다.

**권장 조치** `TalkMessageDetailKey` 에 `serialForMapper` 를 추가하고 `findDetail` 술어에 `AND A.SERIALNUM = #{k.serialForMapper}` 를 건다. 동시에 `MSGKEY` 의 실제 유일성 범위를 DDL/인덱스로 확정하고 `:294` 주석을 정정한다.

---

#### SEC-RT-02 — DB 예외 메시지가 응답 본문으로 유출 (CWE-209) · **CVSS 2.7**

`CVSS:3.1/AV:N/AC:L/PR:H/UI:N/S:U/C:L/I:N/A:N`

**위치** `ReportService.java:444-450` → `SourceAvailability.java:66,78,102-115` → `ReportResponse.java:66`

```java
private static String shortReason(RuntimeException e) {
    String message = e.getMessage();
    ...
    return message.length() <= 120 ? message : message.substring(0, 117) + "...";
}
```

```java
notes.add("API발송 집계를 읽지 못했습니다"
        + (apiFailure == null ? "" : " (" + apiFailure + ")")
        + ". 표시된 수치는 불완전합니다.");
```

`DataAccessResourceFailureException` / `CannotCreateTransactionException` 의 메시지 앞 120 자가 **HTTP 200 본문**으로 브라우저에 도달한다. 이 계열 메시지는 통상 JDBC URL, DB 호스트명, 포트, 드라이버 클래스를 포함한다(예: `Connection to biztalk-bulk.prod.internal:5432 refused`).

**설계 모순** `GlobalExceptionHandler` 는 모든 예외를 "요청을 처리할 수 없습니다"로 일반화하도록 신중히 작성되어 있다. 이 경로는 예외를 **삼켜서 정상 응답에 실어** 그 통제를 우회한다.

**권장 조치** 사용자 대면 문구는 고정 상수(예: "일시적으로 조회할 수 없습니다")로 두고, `shortReason` 결과는 응답이 아니라 `LOG.warn` 과 상관 ID 에만 남긴다.

---

#### SEC-RT-05 — 톡전송 export 감사에 접속지 IP · 상관 ID 누락 · **CVSS 2.7**

`CVSS:3.1/AV:N/AC:L/PR:H/UI:N/S:U/C:N/I:L/A:N`

**위치** `TalkExportService.java:134-137, 149-151, 156-157` · `TalkExportController.java:114-143`

```java
audit.recordAuth(principal.email(), AuditEvent.ACTION_TALK_HISTORY_EXPORT,
        AuditEvent.Outcome.OK, criteria.describe() + " written=" + written, null, null);
//                                                                          ^^^^  ^^^^
//                                                                    sourceIp  correlationId
```

`TalkHistoryService.search` 와 `TalkDetailService` 는 `request.getRemoteAddr()` 를 넘긴다. **export 만 넘기지 않는다.** 컨트롤러는 `HttpServletRequest request` 를 파라미터로 받고도 `exportService.export(criteria, buffer)` 에 전달하지 않는다. 세 감사 경로(DENIED/OK/ERROR) 모두 동일하다.

화면에서 가장 민감한 행위(최대 10 만 건 일괄 반출)의 접속기록에 **접속지 정보가 없다.** 개인정보의 안전성 확보조치 기준 §8 및 전자금융감독규정 접근통제 로그 요건에 미달한다. `correlationId` 도 null 이라 정작 추적이 필요한 행위가 애플리케이션 로그와 대조되지 않는다.

**권장 조치** `export(TalkHistoryCriteria, OutputStream, String sourceIp)` 로 시그니처를 넓히고 `CorrelationId.current()` 를 함께 넣는다. `ReportService.recordRead` 가 이미 올바른 형태다.

---

#### SEC-RT-06 — 도메인 예외가 HTTP 500 으로 귀결, FR-AZ-T04 의 "404" 검증이 성립 불가 · **CVSS 2.7**

`CVSS:3.1/AV:N/AC:L/PR:H/UI:N/S:U/C:N/I:N/A:L`

**위치** `GlobalExceptionHandler.java:176-183` · `TalkDetailService.java:311,326,347` · `TalkExportService.java:282` · `TransactionSerial.java:257` · `TenantContext.java:113`

다음 예외가 **모두 `RuntimeException` 을 직접 상속**하며, `GlobalExceptionHandler` · `AuthExceptionHandler` 어디에도 전용 핸들러가 없다.

| 예외 | 의도한 응답 | 실제 응답 |
|---|---|---|
| `TransactionNotFoundException` | 404 | **500** |
| `MessageNotFoundException` | 404 (FR-AZ-T04 검증 문언) | **500** |
| `UnsupportedTransactionException` | 400/409 | **500** |
| `RowCeilingExceededException` | 400/413 | **500** |
| `TransactionSerial.InvalidSerialException` | 400 | **500** |
| `TenantContext.TenantScopeUnavailableException` | 403 | **500** |

전부 `handleUnexpected(Exception)` 으로 떨어져 `INTERNAL_ERROR` 와 전체 스택트레이스 ERROR 로깅을 유발한다.

- **결과 1** FR-AZ-T04 의 명시된 검증 기준 "cross-institution message key returns **404**" 는 코드상 달성 불가다.
- **결과 2** `RowCeilingExceededException` 이 정성껏 작성한 사용자 안내("조회 기간이나 조건을 좁혀 주세요")가 **사용자에게 도달하지 않는다** — 브라우저는 "요청을 처리할 수 없습니다"만 받는다.
- **결과 3** 잘못된 경로변수(비숫자 serial, `CAST(... AS INTEGER)` 를 통과 못 하는 messageKey)마다 500 + 스택트레이스가 쌓여 ERROR 로그가 오염된다.

**대조** 같은 슬라이스의 `PeriodPolicy.InvalidPeriodException` 은 `IllegalArgumentException` 을 상속하여 **올바르게 400** 이 된다(`PeriodPolicy.java:179`). 즉 규칙이 슬라이스 안에서 일관되지 않다.

**권장 조치** 슬라이스 전용 `@RestControllerAdvice` 를 두어 not-found 계열 → 404, 검증 계열 → 400, 상한 초과 → 413, 스코프 불가 → 403 으로 매핑한다. 본문 문구는 계속 일반화하되 상태코드는 구분한다.

---

#### SEC-RT-10 — 마스킹 표시 vs 평문 검색 — 마스크 복원 오라클 · **CVSS 2.2**

`CVSS:3.1/AV:N/AC:H/PR:H/UI:N/S:U/C:L/I:N/A:N`

**위치** `TalkMessageMapper.xml:192`

```xml
AND decrypt(A.PHONE) LIKE '%' || #{c.recipient} || '%'
```

화면은 마스킹된 번호를 보여주지만 필터는 **복호화된 평문**에 부분 일치한다. 운영자는 부분 문자열을 바꿔가며 행이 반환되는지 관찰하는 것만으로 가려진 자릿수를 자릿수 단위로 복원할 수 있다.

이는 **의도된 절충**이다 — 소스 주석(`:185-191`, `:81-85`)이 "masking() 을 내부에 두면 사용자가 아는 번호로 찾을 수 없다"며 ADR-005 의 배치를 따랐다고 밝힌다. 검색 기능성과 마스킹 강도 사이의 선택이며, 그 결과 CONST-LEGAL-T01("masked in every rendering")은 문언상 충족되지만 **마스킹이 목적하는 기밀성 성질은 결정적 운영자에게 성립하지 않는다.**

**권장 조치** 코드 수정보다 **명시적 리스크 수용**이 맞다. 다만 (a) 수신번호 검색 사용 자체를 별도 감사 액션으로 기록하고, (b) 검색어 최소 길이(예: 4 자리 이상)를 강제해 1 자리씩 훑는 탐색을 비싸게 만들 것을 권고한다. 개인정보 최소열람 원칙 관점에서 PM 결재 대상이다.

---

#### SEC-RT-11 — 감사 로그: 비정형 상세 · 보존기간 기준 충돌 · 무결성 부재 · **CVSS 2.2**

`CVSS:3.1/AV:N/AC:H/PR:H/UI:N/S:U/C:N/I:L/A:N`

**위치** `common/audit/AuditEvent.java:34-43` · `src/main/resources/db/V1__auth_session_audit.sql:85, 93-104` · `hooks/prod-gate-checklist.md:60`

**(가) 비정형 상세.** scope·range·행수·발송구분이 전부 `DETAIL VARCHAR(512)` 한 컬럼의 자유 텍스트다(`TalkHistoryService:101`, `ReportService:352-361`). 질의 가능한 필드가 아니며 512 자에서 조용히 잘린다.

**(나) 보존기간이 두 개다.**

- `V1__auth_session_audit.sql:85` — `보존 5년 (PM 결정 2026-08-14, OI-02 종결)`
- `hooks/prod-gate-checklist.md:60` — `감사 로그 보존 정책 운영 적용 (7년)`

**두 권위가 서로 다른 숫자를 말하고, 실제로 실행되는 것은 DDL 쪽이다.** G3 체크리스트를 그대로 결재하면 존재하지 않는 통제를 승인하게 된다.

**(다) 무결성.** NFR-OPS-AUDIT-T01 은 "tamper-evident integrity" 를 요구한다. `IRIS_AUTH_AUDIT` 에는 해시 체인·서명·순번 무결성 컬럼이 **없다**. append-only 는 ADR-007 주석으로 "DB 권한으로 보장한다"고 선언되나 **그 권한을 부여·검증하는 마이그레이션이 저장소에 없다.** append-only ≠ tamper-evident — DBA 의 삭제는 탐지되지 않는다.

**권장 조치** (가) `SCOPE`/`FROM_TS`/`TO_TS`/`ROW_COUNT`/`SOURCE_FILTER` 를 컬럼으로 승격. (나) PM 이 5년/7년 중 하나로 재결정하고 두 문서를 동시에 정정 — **G3 전 필수**. (다) 최소한 `PREV_HASH`/`ROW_HASH` 체인 또는 WORM 스토리지 이관, 그리고 `GRANT INSERT ... REVOKE UPDATE, DELETE` 를 마이그레이션으로 명문화.

---

#### SEC-RT-12 — 보고서 총건수 산정의 요청당 최대 100 만 키 적재 · **CVSS 2.7**

`CVSS:3.1/AV:N/AC:L/PR:H/UI:N/S:U/C:N/I:N/A:L`

**위치** `ReportService.java:66, 260-282`

```java
static final int MAX_KEY_PROBE = 500_000;
...
keys.addAll(apiMapper.findKeys(criteria, MAX_KEY_PROBE + 1));
keys.addAll(bulkMapper.get().findKeys(criteria, MAX_KEY_PROBE + 1));
```

건수 하나를 얻으려고 요청마다 최대 1,000,002 개의 `AggregateKey` 를 `LinkedHashSet` 에 적재한다. 기간 상한이 366 일이고 엔드포인트에 rate limit 이 없다. 반복 호출로 힙 압박을 만들 수 있다.

`A:L` — `findKeys` 가 `LIMIT` 으로 상한을 두고 있어 무한 증가는 아니다.

**권장 조치** 두 소스에서 각각 집계 count 를 수행하고 교집합만 보정하거나, 총건수를 "≥ N" 근사로 낮춘다. ADR-RPT-021 의 병합 제약과 함께 재검토 대상.

---

### 9.5 부적합 (CVSS 0.0 — 취약점이 아니라 요구사항·증적 결손) — **3 건**

#### SEC-RT-07 — 보고서가 운영자 전용이어서 FR-AZ-R03/R04 에 부적합, `PrincipalScope` 테넌트 분기가 도달 불능 · **CVSS 0.0**

`CVSS:3.1/AV:N/AC:L/PR:L/UI:N/S:U/C:N/I:N/A:N`

**위치** `ReportController.java:83, 119` · `auth/config/SecurityConfig.java:136`

FR-AZ-R03 은 **Must** 이며 "an **operator** role may request 전체 or any single 이용기관; a **tenant** user's scope is derived from the session and a client-supplied `IS_CD` is ignored" 를 요구한다. FR-AZ-R04 는 선택기가 전체를 자격 있는 역할에만 제공할 것을 요구한다. CONFLICT-R01 의 PM 결정도 **테넌트 주체의 존재를 전제**한다("AMB-02 는 폐기가 아니라 정제되었다 — 그 규칙은 이용기관 주체를 규율한다").

그러나 `/api/admin/**` 가 `hasRole("OPERATOR")` 이고 `ReportController` 의 두 메서드가 다시 `@PreAuthorize("hasRole('OPERATOR')")` 다. **테넌트는 보고서에 도달할 수 없다.**

방향이 fail-closed 이므로 데이터 노출은 없고 따라서 CVSS 는 0.0 이다. **위험은 다른 데 있다.**

> `PrincipalScope.resolve` 의 테넌트 분기(`:88-103`)와 `ReportScope` 전체, `overrideAttempted` 플래그, `ACTION_TENANT_OVERRIDE_ATTEMPT` 감사 액션이 **이 배포의 어떤 운영 경로에서도 실행되지 않는다.** 슬라이스에서 가장 보안 결정적인 분기가 운영 중 한 번도 통과되지 않으므로, 나중에 역할 게이트를 완화하는 순간 **검증된 적 없는 인가 로직이 조용히 활성화된다.**

문서(`REQUIREMENTS-SPEC-REPORT.md:137-138`)와 코드가 어긋나는 지점이며, 어느 쪽이 옳은지는 PM 이 정해야 한다.

**권장 조치** 둘 중 하나를 택하고 다른 하나를 정정한다.

- (a) 보고서를 테넌트에 개방하고 `@PreAuthorize` 를 완화 — 이 경우 SEC-RT-08 의 테스트가 **선행되어야** 한다.
- (b) 보고서도 운영자 전용으로 확정하고 FR-AZ-R03/R04 를 CONFLICT-T01 과 같은 방식으로 개정한 뒤, `ReportScope` 의 테넌트 분기를 삭제하거나 도달 불능임을 명시한다.

**분기를 남긴 채 문서만 고치는 것이 가장 나쁘다.**

---

#### SEC-RT-08 — 보고서 엔드포인트에 보안 테스트가 없음 · **CVSS 0.0**

**위치** `src/test/java/com/webcash/iris/biztalk/api/` — `TalkExportControllerTest`, `TalkHistoryAuthorizationTest`, `TalkHistoryContractTest` **3 개뿐**

`/api/admin/reports/usage` 를 호출하는 테스트가 저장소 전체에 **없다**(`grep -rln "reports/usage" src/test` → 0). FR-AZ-R01 의 검증 방법은 "Security test per endpoint (anonymous call returns 401)", FR-AZ-R02 는 "Security test + code review" 로 명시되어 있다. **둘 다 미충족이다.**

톡 슬라이스는 정확히 이 테스트를 갖췄다(`listRefusesAnonymous`, `filtersRefuseAnonymous`, `tenantIsForbiddenOnList`, `tenantIsForbiddenOnFilters`, `operatorMayList`). 즉 팀은 방법을 알고 있으며 보고서 슬라이스에 적용하지 않았을 뿐이다.

현재 보고서의 인가는 `SecurityConfig` 한 줄과 애노테이션 두 개에 걸려 있고 **회귀 방지 장치가 없다.** 누군가 `@PreAuthorize` 를 지우거나 매처 경로를 바꾸면 아무 테스트도 실패하지 않는다.

**권장 조치** `TalkHistoryAuthorizationTest` 를 본떠 `ReportAuthorizationTest` 를 추가한다 — 익명 401, 테넌트 403(또는 SEC-RT-07 결정에 따라 200 + 자기 기관 한정), 운영자 200, `/watermark` 포함. **SEC-RT-07 을 (a) 로 결정할 경우 이 테스트는 선행 조건이다.**

---

#### SEC-RT-09 — PII 마스킹 통제가 인도된 산출물로 검증 불가 · **CVSS 0.0 (전제 붕괴 시 4.9)**

전제 붕괴 시: `CVSS:3.1/AV:N/AC:L/PR:H/UI:N/S:U/C:H/I:N/A:N` = **4.9**

**위치** `TalkMessageMapper.xml:280-281, 339-340` · `src/test/java/.../TalkMessageMapperIntegrationTest.java:35-44, 90-96, 109`

CONST-LEGAL-T01 과 NFR-SEC-PII-T01(둘 다 **Must**)을 충족하는 유일한 기제는 DB 함수 `masking(decrypt(...))` 다. 그런데 —

1. `masking()` / `decrypt()` 는 **사이트 정의 함수**이며 정의가 저장소에 없다. 통합 테스트는 `decrypt` 를 항등 함수로, `masking` 을 "가운데 네 자리 가리기" 스텁으로 **직접 생성해서** 쓴다(`:109`). 테스트 주석이 스스로 밝힌다 — *"asserts that masking **was applied** and that the projection maps, **not that the masking format is correct**."*
2. **애플리케이션 계층 마스킹이 전혀 없다.** `AtkMasker` 는 이 경로에 쓰이지 않는다(§4.3). `TalkMessageDetail.display()` 는 null/blank 를 `ABSENT` 로 바꿀 뿐 마스킹하지 않는다(`:129-131`).
3. 따라서 **2 선 방어가 없다.** 운영에서 `masking()` 이 재정의되거나, `search_path` 문제로 다른 함수가 결정되거나, 마이그레이션 시 누락되면 **평문 수신번호·발신번호가 그대로 브라우저로 나가며, 실패하는 테스트가 하나도 없다.** 이것이 정확히 D-T6 가 레거시에서 발생한 방식이다.

인도된 코드만으로는 위반을 단정할 수 없으므로 as-delivered CVSS 는 0.0 이다. 그러나 **통제가 존재한다는 증적도 없다.**

**권장 조치 (G3 조건)**

- (필수) 운영 DB 의 `masking()` 정의를 추출해 감사 증적으로 첨부하고, 실제 출력 형식이 수신번호 마스킹 정책을 만족함을 확인한다.
- (권장) 응답 조립 계층(`TalkMessageResponse.from` / `TalkMessageDetailResponse.from`)에 **애플리케이션 마스킹을 2 선으로 추가**한다. 이미 필드명이 `senderMasked`/`recipientMasked` 로 계약을 선언하고 있으므로, 그 이름을 실제로 강제하는 코드가 그 자리에 있어야 한다.
- (권장) 운영 함수 정의를 대상으로 하는 계약 테스트를 별도 태그로 두어 배포 파이프라인에서 1 회 실행한다.

---

## 10. 통과한 통제 / controls verified

문서가 주장했고 **코드로 확인된** 것들이다. 이 목록은 위 결함들과 같은 무게로 읽혀야 한다.

| 통제 | 확인 근거 |
|------|---------|
| SQL 인젝션 방어 | `${}` 0 건. 동적 테이블명 `<choose>` + 리터럴. `LIMIT`/`OFFSET` 도 바인딩. `ORDER BY` 전부 리터럴 고정 |
| CONST-SEC-T01 (금지 8 컬럼) | 매퍼 4종 프로젝션 전수 대조 — 등장 0 건 (§4.1) |
| 응답 분할 (D-T4 / D-R3) | 파일명이 검증된 `LocalDate` 재직렬화 + RFC 5987 인코딩. 와이어 수준 테스트 5 종 |
| 목록·export 파리티 | 같은 매퍼 문장 + `TalkExportParityTest` 전 필터 조합 단언 (§4.2) |
| 빈 IS_CD ≠ 전체 | `TenantContext:163-169` fail-closed 예외 |
| 열거 오라클 방지 | `PrincipalScope:98-103` 무시-후-기록, 거부하지 않음 |
| CONFLICT-R01 강제 | `allInstitutions=true` 가 `operator()` 블록 안에서만 생성 |
| FR-AZ-T03 (기관 서버 재도출) | 요청에 institution 파라미터 자체가 없음. 원장 조회로만 결정 |
| 채널 위조 방지 (D-T7) | 채널은 레지스트리가 API 코드로 결정, 메시지 행의 자기 보고 미사용 |
| LPAD 절단 (D-T9) | 술어에서 제거, `TransactionSerial` 이 자바에서 패딩. 초과 폭은 절단 대신 경고+원값 |
| 인증 게이트 | 익명 401 / 테넌트 403 / 운영자 200 을 톡 슬라이스에서 실제 단언 |
| 프론트 XSS | `dangerouslySetInnerHTML` 0, 데이터 유래 `href`/`src` 0, URL 전량 인코딩 |
| 예외 메시지 일반화 | `GlobalExceptionHandler` 가 사용자 대면 문구를 상수화 (단 SEC-RT-02 가 이를 우회) |
| 시크릿 외부화 | 하드코딩 0, `@ConfigurationProperties` 위임 |
| export 행수 상한 | `ROW_CEILING = 100,000`, 초과 시 절단이 아니라 거부 + 감사 |
| 대조 질의 노출 없음 | `findObservedApiServices`(전 기관 무스코프)는 `TalkApiReconciliation` 내부 전용, 웹 엔드포인트 미노출 |

---

## 11. 보안 Hook 3 단계 통합

| 단계 | 도구 | 결과 |
|------|------|------|
| L1 (pre-commit) | `hooks/pre-commit-gitleaks.sh` + `hooks/gitleaks.toml` | **PASS** — 범위 내 시크릿 0 건 (정규식 재확인) |
| L2 (CI) | `hooks/ci-security-auditor.yml` — 본 에이전트 | **실행 완료** — 본 문서 |
| L2 (CI) | OWASP Dependency Check | **미실행** — POI 5.2.5 신규 도입, §13 후속 필수 |
| L2 (CI) | SAST (CodeQL / SpotBugs) | **미실행** — 본 감사는 수동 정적 리뷰 |
| L3 (prod-gate) | `hooks/prod-gate-checklist.md` | **PENDING** — SEC-RT-11(나) 로 인해 60 행("보존 7년")을 **현 상태로 체크할 수 없다** |

---

## 12. 판정

| 항목 | 결과 |
|------|------|
| CVSS ≥ 9.0 (CRITICAL) | **0 건** |
| CVSS 7.0 ~ 8.9 (HIGH) | **0 건** |
| CVSS 4.0 ~ 6.9 (MEDIUM) | **2 건** — SEC-RT-03 (5.8), SEC-RT-04 (4.9) |
| CVSS 0.1 ~ 3.9 (LOW) | **7 건** — SEC-RT-01 (3.8), SEC-RT-02 (2.7), SEC-RT-05 (2.7), SEC-RT-06 (2.7), SEC-RT-12 (2.7), SEC-RT-10 (2.2), SEC-RT-11 (2.2) |
| 부적합 (CVSS 0.0) | **3 건** — SEC-RT-07, SEC-RT-08, SEC-RT-09 |
| 규제 위반 (차단성) | **0 건** — 미해소 3 건은 조건부 |
| **G3 차단 여부** | **차단하지 않음 (NOT BLOCKING)** |
| **종합 판정** | **⚠️ 조건부 승인 / APPROVED WITH CONDITIONS** |

### G3 진입 조건 (결재 전 완료)

1. **SEC-RT-11(나)** — 감사 보존기간 **5년 / 7년** 중 하나로 PM 재결정. `V1__auth_session_audit.sql:85` 와 `hooks/prod-gate-checklist.md:60` 을 동시에 정정. **코드로 닫을 수 없다.**
2. **SEC-RT-09** — 운영 `masking()` 함수 정의 증적 첨부. 미제출 시 CONST-LEGAL-T01 / NFR-SEC-PII-T01 은 **미검증**으로 G3 체크리스트에 명기.
3. **SEC-RT-07** — 보고서의 테넌트 접근 가부를 PM 이 확정. (a) 개방을 택하면 SEC-RT-08 이 선행 조건.

### Sprint R2 / T3 이월 (G3 비차단)

4. SEC-RT-03 — `StreamingWorkbookWriter` 수식 무력화. **R2 export 재사용 전에 고치는 것이 가장 싸다.**
5. SEC-RT-04 — `StreamingResponseBody` 전환 + 부하 테스트를 컨트롤러 경계로 상향. NFR-SCALE-T01 증적 재취득.
6. SEC-RT-01 — `findDetail` 에 `SERIALNUM` 술어 추가. `MSGKEY` 유일성 범위 DDL 확정.
7. SEC-RT-06 — 슬라이스 전용 `@RestControllerAdvice`. FR-AZ-T04 의 404 검증 가능화.
8. SEC-RT-02 / SEC-RT-05 / SEC-RT-12 — 각 결함 항의 권장 조치.
9. SEC-RT-10 — 리스크 수용 결재 또는 검색어 최소 길이 강제.

---

## 13. 미실행 / not run

정직을 위해 남긴다. 아래는 이 감사가 **하지 않은** 것이며, PASS 로 읽어서는 안 된다.

| 항목 | 사유 |
|------|------|
| OWASP Dependency-Check | 미실행. POI 5.2.5 를 포함한 의존성 취약점 판단 근거 없음 |
| CodeQL / SpotBugs | 미실행. 본 감사는 수동 정적 리뷰 |
| 동적 점검 (DAST / 침투) | 미실행. 실행 환경 없음 |
| `masking()` / `decrypt()` 운영 정의 | 저장소 밖 (SEC-RT-09) |
| `IRIS_AUTH_AUDIT` 운영 권한 상태 | 저장소 밖 (SEC-RT-11 다) |
| 로그 실물 검사 (NFR-SEC-PII-T01 "log inspection") | 실행 로그 없음. 코드상 로깅 문장 부재만 확인 |
| 알림톡 슬라이스 | 범위 외 — `security/audit-alimtalk.md` 소관. **SEC-A01(키 회전)은 여전히 OPEN 이며 G3 를 별도로 막는다** |

---

## 14. 후속 권고

- [ ] CI 에 OWASP Dependency-Check 상시 배선 (POI 신규 도입으로 공급망 표면 확대)
- [ ] `StreamingWorkbookWriter` 수식 무력화를 **공통 통제**로 승격 — 보고서·문자내역 export 가 같은 writer 를 쓸 예정
- [ ] 감사 로그 무결성(해시 체인 또는 WORM) 설계를 ADR 로 확정
- [ ] `@PreAuthorize` 애노테이션 인벤토리 테스트 도입 — 엔드포인트 신설 시 인가 누락을 구조적으로 탐지
- [ ] 분기별 외부 침투 테스트 / 매월 의존성 패치

---

**security-auditor 서명**

| 일자 | 에이전트 / 인간 | 비고 |
|------|-----------------|------|
| 2026-08-20 | `security-auditor` | 자동 감사 — 범위 내 소스 직접 판독. 문서 주장은 근거로 미채택 |
| | 정보보호 책임자 | **결재 필요** — §12 G3 진입 조건 1·2·3 |
| | PM | **결재 필요** — SEC-RT-11(보존기간), SEC-RT-07(보고서 역할), SEC-RT-10(리스크 수용) |
