# 교차 검증 — 동일 벤더 내 독립 에이전트 (3차)

**이 검증은 동일 벤더(Anthropic) 내 독립 에이전트로 수행되었다. 스킬 §2 가 요구하는 타 벤더 LLM 교차검증이 아니며 DoD 항목 1 을 충족하지 않는다. 별도로 로컬 모델(ollama) 검증이 진행 중이다.**

> **검증자**: 독립 적대적 검증 에이전트 (Claude, 동일 벤더) · **일자**: 2026-08-20
> **입력 문서**: `reviews/verdict-sprint-R1-T1-T2.md`, `security/audit-3-report-talk.md`, `reviews/code-review-sprint-R1-T1-T2.md`
> **방법**: 문서 주장은 근거로 채택하지 않는다. 코드·매퍼 XML·테스트를 직접 읽고, 파일:줄 인용이 있을 때만 판정을 확정한다. 반증을 기본 자세로 삼는다.

---

## 0. 총괄 판정표

| 항목 | 판정 | 한 줄 근거 |
|---|---|---|
| A | **PARTIALLY CONFIRMED** | 메커니즘은 논리적으로 유효하나 전제(QUE/LOG MSGKEY 동시 중복)가 코드로 검증·반증 불가 — "실제 오동작"이라는 code-review 의 확언은 과장 |
| B | **CONFIRMED** | 5개 예외 전부 `RuntimeException` 직계 상속, 전용 핸들러 0건, 전부 500 |
| C | **CONFIRMED** | `findDetail` 술어에 `SERIALNUM` 없음. 단 기관 술어는 있어 교차 기관 노출은 아님 — 원 감사(SEC-RT-01, CVSS 3.8)가 정확 |
| D | **REFUTED (신규 CVSS≥7 미발견)**, 단 SEC-RT-04/SEC-RT-03 의 "PR:H 상한" 가정 자체는 유효 | 적대적 재계산으로도 CVSS≥7.0 결함을 찾지 못함. 원 감사의 "0건" 결론은 유지되나, 근거의 재현 가능성만 별도로 확인함 |
| E | **CONFIRMED** | `TalkExportService` 는 `TalkHistoryMapper.findPage`/`countAll` 을 목록과 문자 그대로 공유. 9열에 PHONE/CALLBACK 부재 — 원 감사의 단서("PII 없음 뿐")도 코드로 확인됨 |
| F | **CONFIRMED** | `StreamingWorkbookWriter.java:116` 에 선행문자 무력화 없음. CVSS 5.8 산식 재계산 결과 일치 |
| 신규(미확인 영역) | **결함 미발견**, 단 1건의 원 감사 결함 반박(REFUTED) | `BizTalkApiRegistry` 는 부팅(빈 생성) 시 중복 코드를 fail-fast 로 거부 — "부팅 시 설정 검증 부재" 가설은 성립하지 않음 |

---

## 1. 주장 A — `findMessages` ORDER BY 와 QUE/LOG 중복·소실 (최우선)

**대상**: `src/main/resources/mybatis/mapper/biztalk/TalkMessageMapper.xml:230-251`(messageSource, QUE∪LOG UNION ALL), `:253-300`(findMessages, `ORDER BY A.REQDATE DESC, A.MSGKEY DESC` + `LIMIT/OFFSET`), `:291-297`(전순서라는 설계 주석)

### 핵심 쟁점 재확인

쟁점은 정확히 제시된 대로다: **같은 `(REQDATE, MSGKEY)` 짝이 QUE 와 LOG 양쪽에 동시에 존재할 수 있는가.** `messageWhere`(`:173-224`)가 이미 `A.ID = #{c.institutionCode} AND A.SERIALNUM = #{c.serialForMapper}` 로 결과를 **한 거래**로 좁히므로(`:174-175`), 그 범위 안에서 `MSGKEY` 가 유일하면 `MSGKEY DESC` 단독으로도 전순서이고 `TABLE_TYPE` 은 필요 없다. 결함이 성립하려면 **같은 거래의 같은 MSGKEY 값이 QUE 테이블과 LOG 테이블에 동시에 존재**해야 한다.

### 코드가 실제로 뭐라고 하는가

- 매퍼 자신의 주석(`:291-297`)은 "메시지키는 한 거래 안에서 유일하므로 동시각에도 순서가 정의된다"고 **주장**한다. 이것은 검증된 사실이 아니라 **설계 가정**으로 적혀 있다.
- `TalkMessageMapperIntegrationTest.java:166-182` 의 시드 데이터는 `KKO_MSG`에 1001-1004, `KKO_MSG_LOG`에 1005 를 넣는다 — **QUE 와 LOG 에 겹치는 MSGKEY 를 넣는 시험이 존재하지 않는다.** 즉 이 코드베이스는 "겹칠 수 없다"를 증명하지도, "겹칠 수 있다"를 반증하지도 않는다.
- `mapping/analysis/ANALYSIS-A2-02-existing-schema.md:69-73, 137-143` 은 관련 테이블(`KKO_MSG_LOG`)이 **IRIS_ADMIN 이 쓰지 않는 외부 게이트웨이 소유 테이블**이며, "`KKO_MSG_LOG` 의 실제 DDL — 컬럼 타입, 인덱스, 보존 기간"을 **"질의로부터 역추론했을 뿐"**이라고 스스로 밝힌다. 즉 아카이빙이 원자적 이동(delete+insert)인지, 일정 기간 양쪽에 겹쳐 존재하는 복제/지연-삭제 패턴인지는 **저장소 안에서 확인할 수 없다.**
- `ADR-TLK-025`, `ADR-TLK-026` 은 이 계열에서 반복적으로 "측정되지 않은 그럴듯한 메커니즘"(LPAD sargability 오류)을 사후에 정정한 전례를 스스로 기록하고 있다 — 이 코드베이스는 검증되지 않은 주장을 결정적 사실처럼 적는 것을 **자기 결함 사례**로 취급한다.

### 판정

**PARTIALLY CONFIRMED.** `code-review HIGH-04`(→ V-1)가 이를 "실제 오동작"으로 단정한 것은 **전제가 증명되지 않았다는 점에서 과장**이다. 논리 구조는 맞다 — `TABLE_TYPE` 이 정렬 키에 없고 QUE/LOG 가 겹칠 수 있다면 오프셋 페이징에서 행 중복·소실이 발생하는 것은 SQL 상 사실이다. 그러나 "겹칠 수 있는가"라는 유일한 전제 조건은 **DDL·운영 데이터 없이는 반증도 확증도 불가능**하다. `security-auditor`의 원 리포트(`SEC-RT-01`, `TalkMessageMapper.xml:370-371`)가 오히려 같은 종류의 불확실성("그 진술이 참이면… 완화 요인… `MSGKEY` 가 실제로 전역 유일하면 두 번째 문제는 소멸한다")을 정직하게 LOW 로 남긴 것과 대조된다. **권고**: T1-01(또는 신규 probe task)이 QUE/LOG 아카이빙이 원자적 이동인지 실측하기 전까지, 이 항목은 "차단 사유"가 아니라 "미해소 가정"으로 재분류해야 한다. 안전 조치로 `TABLE_TYPE` 을 정렬 키에 추가하는 것 자체는 비용이 거의 없고 전제가 거짓이어도 해가 없으므로, 수정 자체는 여전히 권고할 만하다.

**countMessages 와 findMessages 의 소스 집합 일치 여부**: `:302-309` 의 `countMessages` 는 `messageSource`(`:230-251`)와 `messageWhere`(`:173-224`)를 `findMessages` 와 **동일하게 `<include>`** 한다. 두 문장이 다른 조건을 볼 수 없는 구조이므로 이 점은 **CONFIRMED — 결함 없음**.

---

## 2. 주장 B — 도메인 예외 5종이 전부 HTTP 500

**대상**: `TalkExportService.java:282`(`RowCeilingExceededException`), `TalkDetailService.java:311`(`UnsupportedTransactionException`), `:326`(`TransactionNotFoundException`), `:347`(`MessageNotFoundException`), `TransactionSerial.java:257`(`InvalidSerialException`)

`GlobalExceptionHandler.java` 를 전수 확인했다(`:69,92,132-133,162,176`). 등록된 `@ExceptionHandler` 는 `AccessDeniedException`(69), `IllegalArgumentException`(92), `{MissingServletRequestParameterException, MethodArgumentTypeMismatchException}`(132-133), `IllegalStateException`(162), 그리고 catch-all `Exception`(176) 뿐이다. 저장소 전체에서 `@RestControllerAdvice`/`@ExceptionHandler` 를 가진 클래스는 이것과 `AuthExceptionHandler.java`(인증 예외 전용), `MessageHistoryController.java:170`(자기 컨트롤러 내부의 `CriteriaException` 전용 핸들러, 다른 슬라이스)뿐이다.

5개 예외 전부가 `extends RuntimeException` 을 직접 상속하고(`UnsupportedTransactionException`, `TransactionNotFoundException`, `MessageNotFoundException`, `RowCeilingExceededException`, `InvalidSerialException` 모두 확인), 이를 잡는 특정 핸들러가 없으므로 전부 `handleUnexpected(Exception e)`(`:176-183`)로 떨어져 `INTERNAL_ERROR` + HTTP 500 이 된다.

**판정: CONFIRMED.** 부가로 확인한 점: `FR-AZ-T04` 의 검증 문언("cross-institution message key returns 404")은 `MessageNotFoundException` 이 500 이 되므로 **코드상 달성 불가**하다는 원 감사(SEC-RT-06)의 결론도 재확인됨. `PeriodPolicy.InvalidPeriodException`(다른 슬라이스, `IllegalArgumentException` 상속)만 올바르게 400 이 되어, 슬라이스 안에서 예외 계층 규칙이 **일관되지 않는다**는 지적도 코드로 확인됨.

---

## 3. 주장 C — `TalkDetailService.detail()` 이 목록과 다른 인가 규칙을 쓴다

**대상**: `TalkMessageMapper.xml:370-371`(`findDetail` WHERE 절), `TalkMessageDetailKey.java`(전체), `TalkDetailService.java:147-181`(`detail()`)

`TalkMessageDetailKey` 레코드(`TalkMessageDetailKey.java:37-42`)는 `institutionCode, messageKey, channel, tableType` 네 필드만 갖는다 — **`serial`/`SERIALNUM` 필드가 존재하지 않는다.** `findDetail` 의 WHERE 절(`:370-371`)은 `A.ID = #{k.institutionCode} AND A.MSGKEY = CAST(#{k.messageKey} AS INTEGER)` 뿐이다. 형제 문장 `messageWhere`(`:174-175`, `findMessages` 가 사용)는 `AND A.ID = … AND A.SERIALNUM = …` 두 조건을 모두 건다. **같은 파일 안에서 두 문장이 다른 계층 결속 규칙을 쓰는 것은 사실이다.**

단, `TalkMessageDetailKey.of()`(`:61-88`)가 `institutionCode` 를 필수로 강제하고, 그 값은 `TalkDetailService.detail()`(`:147-181`)에서 요청이 아니라 `mapper.findTransactionOwner(txnKey)`(`:153`)로 **원장에서 도출**된다 — 클라이언트가 기관을 지정할 방법이 없다. 따라서 이 결함은 **기관 경계를 넘는 것이 아니라, 같은 기관 안에서 다른 거래(SERIALNUM)의 메시지가 MSGKEY 충돌 시 섞일 수 있는가**의 문제다. `MSGKEY` 가 "한 거래 안에서 유일"(주장 A 의 동일 설계 가정)일 뿐이라면, 같은 기관의 다른 거래끼리 MSGKEY 가 겹칠 가능성이 이론상 남는다.

**판정: CONFIRMED (원 감사 SEC-RT-01, CVSS 3.8 과 부합).** 반증 시도: 운영자는 이미 전 기관 데이터에 `PR:H` 로 접근 가능하므로(SecurityConfig.java:136 + 메서드 `@PreAuthorize`), 이 결함이 운영자에게 주는 추가 이득은 "같은 기관의 다른 거래 메시지를 보게 됨" 정도이며 기관 경계를 넘지 못한다 — **원 감사가 이미 정확히 이 지점("완화 요인… 실질 기밀성 증분이 작다")까지 밝혀 두었다.** 신규로 격상시킬 근거를 찾지 못했다.

---

## 4. 주장 D — "CVSS ≥ 7.0 결함 0건" 반박 시도 (최우선)

세 방향으로 반박을 시도했다.

### 4.1 "정당한 운영자가 자기 권한을 넘는다" — 기관 간 열람

`SecurityConfig.java:136` + 전 엔드포인트 메서드 `@PreAuthorize("hasRole('OPERATOR')")` 구조상, 운영자 역할 자체가 전 기관 데이터에 대한 `PR:H` 를 이미 부여한다. 코드에서 **기관 단위로 세분화된 운영자 역할(예: "기관 X 담당 운영자")이 존재하는 흔적을 찾지 못했다** — `TenantContext`, `PrincipalScope`, `SecurityConfig` 어디에도 운영자 내부의 기관별 서브스코프가 없다. 따라서 "운영자가 자기 권한(전체 기관)을 넘는다"는 시나리오 자체가 **이 코드베이스에는 성립할 상태가 없다** — 권한이 이미 전역이므로 넘을 경계가 없다. 이는 결함이 아니라 **원 감사가 반복해서 짚은 구조적 사실**("점수가 낮은 것이 통제가 강해서가 아니라 공격자 자격이 이미 높아서")과 일치한다.

### 4.2 감사 로그 회피

`AuditService.recordAuth`(`AuditService.java:87-96`)는 메서드 자체에 `@Transactional(propagation = REQUIRES_NEW)` 가 붙어 있어(self-invocation 문제 없음 — 호출자는 항상 주입된 빈을 통해 부른다), 업무 트랜잭션이 롤백되어도 감사 기록은 독립적으로 커밋된다. `TalkDetailService.messages()/detail()`, `TalkExportService.export()` 를 확인한 결과 **DENIED/OK/ERROR 세 경로 모두에서 예외를 던지기 전에 `audit.recordAuth` 가 먼저 실행**된다(`TalkDetailService.java:77-79, 90-94, 114-117, 122-124, 172-174, 177-178`). 예외가 500 으로 뭉개지는 것(주장 B)은 **클라이언트 응답의 문제이며 감사 기록 자체를 없애지는 않는다.** 감사 기록을 우회하는 코드 경로를 찾지 못했다. `AuditMapper` 인터페이스(`AuditService.java:108-116`)는 `insert` 만 선언해 update/delete 자체가 애플리케이션에서 호출 불가능하다 — 구조적으로 강한 통제다.

남는 취약점은 원 감사가 이미 짚은 것과 동일하다: `TalkExportController.export`(`TalkExportController.java:114-143`)가 `HttpServletRequest request` 를 받고도(`:123`) `exportService.export(criteria, buffer)`(`:138`)에 전달하지 않아 export 감사에 접속지 IP 가 빠진다(SEC-RT-05 재확인, CVSS 2.7 — 감사 회피가 아니라 **감사 필드 누락**이며 기록 자체는 남는다).

### 4.3 PII 대량 반출

`TalkExportService.ROW_CEILING = 100_000`(`TalkExportService.java:68`)이 상한이고 초과 시 잘라내지 않고 거부한다(`:129-139`). export 대상 9열에 PII 가 없음(주장 E 참조)을 재확인했으므로, export 경로로의 "PII 대량 반출"은 **성립하지 않는다** — 반출되는 것은 거래 메타데이터(기관코드/기관명/거래번호/API/상태/응답코드/시각)뿐이다. 메시지 상세(전화번호 포함) 경로는 건별 조회만 가능하고 일괄 export 가 없다. 검색 필드를 이용한 마스킹 자릿수 복원(SEC-RT-10, `TalkMessageMapper.xml:192`)은 "대량"이 아니라 "건별, 반복 필요"이며 원 감사가 이미 `AC:H`로 반영했다 — 재계산해도 CVSS 는 2.2 부근에 머문다.

### 판정

**REFUTED — 신규 CVSS≥7.0 결함을 발견하지 못했다.** 원 감사의 "0건" 결론에 동의한다. 단, 이는 "결함이 없다"보다는 **"모든 잠재 취약면이 PR:H(운영자 이미 전권)로 상한이 걸려 있다"**는 구조적 사실 때문이며, 이 상한 자체가 무너지는 시나리오(예: 미래에 기관별 운영자 서브스코프가 도입되거나, `masking()` 이 재정의되는 SEC-RT-09 시나리오)에서는 재평가가 필요하다는 원 감사의 조건부 문구에 동의한다.

**확인 불가**: `masking()`/`decrypt()` 의 운영 정의, `IRIS_AUTH_AUDIT` 의 실제 DB 권한 부여 상태 — 저장소 밖의 사실이며 본 검증도 접근할 수 없다.

---

## 5. 주장 E — export 가 화면 마스킹을 우회하지 않는다

`TalkExportController.export`(`:129-131`) → `historyService.criteriaFor(...)` → `TalkExportService.export`(`TalkExportService.java:124-160`) → `PagedRowIterable.fetch()`(`:244-268`) → `mapper.findPage(pageCriteria)`(`:250`). 이 `mapper` 는 생성자(`:103-111`)에서 주입되는 `TalkHistoryMapper` 이며, **목록 서비스가 쓰는 것과 같은 인터페이스·같은 메서드**다.

`TalkHistoryMapper.xml:211-256`(`findPage`)의 프로젝션은 `TRDD, FINTECH_ISCD, ISNM, IS_TUNO, API_SVC_CD, PRSU, FINTECH_RPCD, RGDT, LAST_AMDT` 9개뿐이다(`:114-127`, resultMap 확인). `PHONE`/`CALLBACK` 컬럼은 이 매퍼에 **존재하지 않는다** — `TalkMessageMapper.xml` 의 별개 매퍼에만 있다. `TalkExportService.HEADERS`(`:86-88`)도 정확히 이 9개와 1:1 대응한다(`일자/기관코드/기관명/거래고유번호/API/상태/응답코드/등록시각/완료시각`).

**판정: CONFIRMED.** 원 감사가 스스로 단서를 단 대로 "export 9열에 PII 가 없다"는 사실이 유일한 근거이며, 코드로 재확인된다. 이 근거의 취약성(향후 컬럼 추가 시 무효화)도 원 감사의 서술이 정확하다 — 반박할 지점을 찾지 못했다.

---

## 6. 주장 F — 엑셀 수식 주입

`StreamingWorkbookWriter.java:116`: `bodyRow.createCell(i).setCellValue(value == null ? "" : value);` — 선행 `=`, `+`, `-`, `@`, TAB, CR 에 대한 무력화(apostrophe-prefix 등) 가 **전무**함을 확인. 셀에 들어가는 값 중 `TalkExportService.cellsOf`(`:182-197`)의 `institutionDisplay()`(`:188`, 기관명)와 `responseCode()`(`:194`, `FINTECH_RPCD` — 상위 핀테크 게이트웨이가 적재)는 애플리케이션이 생성하지 않는 상류 데이터다.

CVSS 벡터 재계산: `CVSS:3.1/AV:N/AC:H/PR:N/UI:R/S:C/C:L/I:L/A:L`
- Exploitability = 8.22 × 0.85(N) × 0.44(H) × 0.85(N) × 0.62(R) ≈ 1.620
- ISC_Base = 1−(1−0.22)(1−0.22)(1−0.22) ≈ 0.5254
- Scope Changed Impact ≈ 7.52×(0.5254−0.029) − 3.25×(0.5254×0.9731−0.02)^13 ≈ 3.734
- BaseScore = Roundup(1.08×(3.734+1.620)) = Roundup(5.782) = **5.8**

**판정: CONFIRMED**, CVSS 산식도 재계산 결과 일치. 반박 시도: PR:N 이 맞는가? — 엔드포인트는 `@PreAuthorize("hasRole('OPERATOR')")` 이므로 **호출 자체는 PR:H** 인데, CVSS 벡터의 `PR:N` 은 **가해자 관점**(응답코드를 상류에 주입하는 제3자 — 핀테크 파트너/가맹점)이 인증을 요구하지 않는다는 뜻으로 쓰인 것으로 판단되며, `S:C`(취약 컴포넌트=서버, 영향 컴포넌트=운영자 데스크톱)와 결합하면 그 해석이 일관된다. 벡터 표기가 두 행위자(주입자/피해자)를 섞어 쓰고 있어 **오해 소지가 있다는 점만 지적**하나, 점수 자체는 반박하지 않는다.

---

## 7. 아무도 보지 않은 영역 — 재조사 결과

### 7.1 `BizTalkApiRegistry` 부팅 시 설정 검증 — **REFUTED (결함 없음)**

`BizTalkApiRegistry.java:100-117`(생성자)은 중복 코드가 있으면 `IllegalArgumentException` 을 던진다(`:110-114`). 이 생성자는 `TalkHistoryConfig.bizTalkApiRegistry(TalkHistoryProperties properties)`(`TalkHistoryConfig.java:67-81`)라는 `@Bean` 메서드에서 호출되며, Spring 컨텍스트 초기화(부팅) 시점에 실행된다. 설정 항목이 비어 있으면 `DEFAULT_ENTRIES` 로 대체되고 그 사실이 `log.info`(`:74-78`)로 남는다. `ApiServiceProperty.toEntry()`(`TalkHistoryConfig.java:195-214`)는 `code` 누락이나 알 수 없는 `channel` 값(`AT`/`FT` 외)을 **역시 부팅 시점에 예외로 거부**한다(`:196-199`, `:207-209`).

즉 "부팅 시 설정 검증 부재"라는 가설은 **코드와 반대다** — 오히려 fail-fast 로 설계되어 있다. 새로 찾은 결함 없음.

### 7.2 `ReportWatermark` / `SourceAvailability` — "거짓 0" 문제 — **이미 설계로 해소, 결함 없음**

`ReportWatermark.isNotYetAggregated()`(`:119-122`)는 기준일을 모르면(`asOf == null`) `false` 를 반환하도록 명시적으로 설계되어 있다 — "알 수 없음"을 "미집계"로 단정하지 않는다(코멘트 `:109-112`). `SourceAvailability.incompleteNotes()`(`:102-115`)는 부분 실패를 사용자에게 명시적으로 알린다. 두 클래스 모두 ADR-RPT-022 가 스스로 인정한 한계("`max(TRDD)` 는 중간 날짜의 소거·재적재 실패를 구분하지 못한다", `ReportWatermark.java:22-30`)를 **숨기지 않고 문서화**하고 있다. 이 한계는 배치 실행 이력이 없으면 근본적으로 해소 불가능하다는 점까지 스스로 밝혀, "거짓 0 을 사실로 표시"라는 가설이 겨냥하는 실제 결함(조용한 은폐)은 발견하지 못했다.

### 7.3 `ReportDataSourceConfig` / `TalkHistoryConfig` — 트랜잭션 경계·커넥션 풀 — **결함 없음**

`ReportDataSourceConfig`(전체)는 `@ConditionalOnProperty` 로 게이트되어 있고, 전용 `DataSource`/`SqlSessionFactory`/`SqlSessionTemplate`/`MapperFactoryBean` 을 **명시적으로** 구성해 자동 스캔에 맡기지 않는다(`:110-131`, 코멘트가 그 이유를 "합계가 조용히 두 배가 된다"로 설명). 두 데이터소스 간 분리 결함을 찾지 못했다. 커넥션 풀 크기·타임아웃 설정은 `application.yml` 에 있을 것으로 추정되나 SEC-001 규칙상 이 슬라이스도 본 검증도 그 파일을 열지 않았다 — **확인 불가**로 남긴다.

### 7.4 공유 가변 상태(동시성) — **결함 없음**

`TalkExportService`, `TalkDetailService`, `BizTalkApiRegistry`, `AuditService` 를 대상으로 인스턴스 필드를 확인한 결과, 전부 생성자 주입 후 불변(`final`)이거나 요청마다 새로 만들어지는 로컬 상태(`PagedRowIterable` 의 내부 반복자)뿐이다. `static` 가변 필드, 공유 `Map`/캐시, `SimpleDateFormat` 류의 비-스레드-세이프 공유 객체를 찾지 못했다.

---

## 8. 결론

앞선 리뷰어(security-auditor, code-reviewer)의 결론 대부분은 **코드로 재확인되며 유지된다.** 이견은 다음 한 가지뿐이다: 주장 A 를 "실제 오동작"으로 단정한 code-review 의 표현은, 그 유일한 성립 조건(QUE/LOG 사이 MSGKEY 동시 중복)이 저장소 안에서 검증 불가능하다는 점에서 **과신**이다 — security-auditor 자신의 SEC-RT-01 서술이 오히려 이 불확실성을 정확히 반영하고 있다. CVSS≥7.0 결함은 본 적대적 재검토로도 발견되지 않았고, 그 이유(운영자 권한이 이미 전역이라 상한이 걸림)는 원 감사가 제시한 구조적 설명과 일치한다.

**확인 불가로 명시할 항목**: `masking()`/`decrypt()` 운영 정의, `IRIS_AUTH_AUDIT` DB 권한 실제 부여 상태, `KKO_MSG`/`KKO_MSG_LOG` 등 4개 테이블의 실제 DDL·인덱스·아카이빙 트랜잭션 경계, `application.yml` 의 커넥션 풀 설정값. 이들은 저장소 밖의 사실이며 어떤 정적 코드 검증으로도 닫을 수 없다.
