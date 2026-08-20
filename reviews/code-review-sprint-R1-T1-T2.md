# 코드 리뷰 리포트 — Sprint R1 + T1 + T2

> **작성**: code-reviewer 에이전트
> **일자**: 2026-08-20
> **대상**: 이용기관 보고서(R1) · 톡전송 내역(T1/T2) 산출물 (commit `0a987dd`..`f8e7d47`)
> **판정**: **CONDITIONAL APPROVE**

---

## 1. 범위

| 항목 | 값 |
|------|-----|
| 변경 파일 수 | 92 (src/ 기준) — Java 55, MyBatis XML 4, TS/TSX 15, 테스트 18 |
| 변경 라인 수 | +17,394 / −7 |
| 대상 도메인 | `biztalk.domain` / `biztalk.api` / `biztalk.config` / `biztalk.infra.db` / `biztalk.infra.excel` / `common.{audit,logging,tenant}` / frontend `features/biztalk` |
| 핵심 ADR 적용 | ADR-RPT-021 (교차소스 집계), ADR-RPT-022 (집계 기준일), ADR-RPT-023 (SXSSF 내보내기), ADR-TLK-024 (API 분류), ADR-TLK-025 (거래일련번호 정규화), ADR-TLK-026 (상세 제공 가능성), ADR-TLK-027 (형제 슬라이스 재사용 경계), ADR-TLK-028 (페이징 전략) |
| 범위 밖 (미검토) | A1/alimtalk, login, institution, sender 슬라이스 |

리뷰 방식: 위 파일들을 **전부 읽고** 정적 분석했다. `./mvnw verify` 는 실행하지 않았다 —
본 리뷰는 정적 리뷰이며, 실행 검증은 QA 단계의 책임이다. 따라서 아래 결함 중 실행으로만
확인 가능한 것(HIGH-01 의 HTTP 상태 코드 등)은 **코드 경로 추적으로** 판정했고, 근거 경로를
결함마다 명시했다.

---

## 2. 평가 차원

| 차원 | 가중 | 점수 (0~100) | 코멘트 |
|------|-----:|------------:|--------|
| 네이밍 | 10% | **95** | `PrincipalScope`, `TransactionSerial`, `SourceMerger`, `OrderedCursor`, `SourceAvailability` — 도메인 용어가 그대로 타입 이름이다. `serialForMapper()` / `serialForLedger()` 처럼 **바인딩 대상별로 이름을 나눈** 접근자가 D-T25 형 비대칭을 이름 수준에서 막는다. 감점은 `TalkHistoryCriteria.serialForMapper()` 와 `TalkMessageCriteria.serialForMapper()` 가 같은 이름으로 **다른 폭**(20 / 10)을 반환하는 점 하나뿐 |
| 가독성 (Javadoc) | 15% | **90** | 한국어 + 영문 병기 표준을 전 파일이 준수. 모든 수정에 결함 ID(D-Tn / D-Rn)가 붙어 있고, 반박된 주장(LPAD sargability)을 조용히 지우지 않고 정정 노트로 남긴 것은 모범적이다. 감점 사유는 §3.2 MED-05 · §3.2 MED-03 — **Javadoc 이 코드와 어긋난 곳이 세 군데** 있고, 틀린 주석은 없는 주석보다 나쁘다 |
| 모듈 응집도 | 10% | **88** | 슬라이스 경계가 뚜렷하고 `ReportScope` → `PrincipalScope` 위임이 인가 규칙 단일화를 지킨다. `ReportService.query()` 가 범위·기간·두 출처 읽기·병합·건수·기준일·감사를 한 메서드(106~211행, 106줄)에서 수행하는 것은 분리 권고 |
| 결합도 | 10% | **86** | 매퍼가 요청 객체가 아니라 검증 타입만 받는 경계가 일관되게 지켜진다. `TalkExportService` 가 `TalkHistoryMapper` 를 직접 잡고 페이징 반복자를 내부 클래스로 두어 내보내기와 목록이 같은 매퍼를 공유하는 것은 의도된 결합(FR-TLKX-001). 감점은 `ReportDataSourceConfig` 가 별도 `SqlSessionFactory` 를 만들면서 트랜잭션 매니저를 함께 만들지 않아, 결합이 **선언되지 않은 채** 남은 점 |
| 스레드 안전성 | 15% | **90** | `StreamingWorkbookWriter` 는 무상태이고 호출마다 새 워크북을 만든다. `BizTalkApiRegistry` 는 `Collections.unmodifiableMap` 로 닫힌 불변 빈. `SourceMerger` 는 `final` 유틸리티, `OrderedCursor` 는 호출 스코프 지역. 공유 가변 상태 없음. 감점은 MED-07 (자원 해제 순서) |
| 거래 무결성 | 15% | **78** | 계층(거래 → 메시지 → 본문)이 **요청의 형태로** 강제되고 기관·채널이 원장에서 도출되는 설계는 옳다. 그러나 도출된 기관을 호출자 범위와 **교차 검증하지 않고**(HIGH-06), 상세 목록의 `UNION ALL` 페이징이 전순서가 아니며(HIGH-04), 대량 데이터소스에 트랜잭션 매니저가 없다(MED-02) |
| 예외 처리 | 10% | **72** | swallow 는 **한 건도 없다** — 모든 catch 가 감사 기록 후 재던지거나, 인프라 장애만 좁혀 판정하고 나머지는 그대로 올린다(`rethrowUnlessSourceUnavailable`). 이 부분은 모범적이다. 그러나 도메인 예외 다섯 종이 HTTP 매핑을 갖지 않아 전부 500 으로 나간다(HIGH-01) — 예외를 잘 던지고 응답에서 잃어버린다 |
| 테스트 가능성 | 15% | **92** | 생성자 주입 일관, `Clock` 주입, `criteriaFor()` 를 공개해 집합 동일성 시험을 가능하게 한 설계, `rowsFor()` 접근점, `Optional<BulkAggregateMapper>` 로 미설정 환경 표현 — 전부 시험을 염두에 둔 형태다 |

**가중 평균: 86.0**

---

## 3. 발견 사항

### 3.1 BLOCKER (즉시 차단)

| ID | 위치 | 내용 | 권장 조치 |
|----|------|------|---------|
| — | — | **없음.** §4 강제 룰 8 항목 전부 PASS | — |

REJECT 사유(주석 누락 / Javadoc 표준 위반 / 금액 부동소수 / PII 평문 로그 / 시크릿 하드코딩 /
`System.out`·`System.err` / 디렉터리 권한 위반)에 해당하는 것이 **하나도 없다**. 따라서 REJECT
권한은 행사하지 않는다.

---

### 3.2 HIGH (Sprint 내 수정)

#### HIGH-01 — 도메인 예외 5 종이 HTTP 매핑을 갖지 않아 전부 500 으로 나간다

**위치**
- `src/main/java/com/webcash/iris/biztalk/domain/TalkExportService.java:282`
- `src/main/java/com/webcash/iris/biztalk/domain/TalkDetailService.java:311`, `:326`, `:347`
- `src/main/java/com/webcash/iris/biztalk/domain/TransactionSerial.java:257`
- (판정 경로) `src/main/java/com/webcash/iris/common/logging/GlobalExceptionHandler.java:176-183`

**근거**

```java
// TalkExportService.java:282
public static class RowCeilingExceededException extends RuntimeException {
    public RowCeilingExceededException(int actual, int ceiling) {
        super("내보낼 건수가 상한을 초과했습니다 (" + actual + "건 / 상한 " + ceiling
                + "건). 조회 기간이나 조건을 좁혀 주세요. / ...");
```

같은 파일 276~279행의 Javadoc:

> 메시지가 **맞는 범위를 알려준다**. 상한을 넘었다는 사실만 알리면 사용자는 시행착오로 범위를 좁혀야 한다.

그런데 `GlobalExceptionHandler` 에는 이 타입을 받는 핸들러가 없고, `@ResponseStatus` 도 없다.
전체 소스에서 `@ExceptionHandler` 는 세 곳뿐이다 — `AuthExceptionHandler`(인증),
`MessageHistoryController:170`(문자내역 전용), `GlobalExceptionHandler`. 그리고
`GlobalExceptionHandler` 가 처리하는 타입은 `AccessDeniedException`,
`IllegalArgumentException`, `MissingServletRequestParameterException`,
`MethodArgumentTypeMismatchException`, `IllegalStateException`, `Exception` 이다.

다섯 예외의 상속 관계를 확인했다 — 전부 `extends RuntimeException` 이며
`IllegalArgumentException` 을 상속하지 않는다. 따라서 `handleUnexpected(Exception)` 로 떨어진다:

```java
// GlobalExceptionHandler.java:176-183
@ExceptionHandler(Exception.class)
public ResponseEntity<Map<String, String>> handleUnexpected(Exception e) {
    log.error("UNHANDLED an unexpected error escaped the application", e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(body("INTERNAL_ERROR", "요청을 처리할 수 없습니다."));
}
```

**결과** — 다섯 가지 정상적인 클라이언트 조건이 전부 `500 / "요청을 처리할 수 없습니다"` 가 된다:

| 예외 | 발생 조건 | 요구사항이 정한 동작 | 실제 |
|------|----------|-------------------|------|
| `RowCeilingExceededException` | 10만 행 초과 | 맞는 범위를 알리는 거부 (FR-TLKX-005) | 500, 메시지 소실 |
| `UnsupportedTransactionException` | 상세 미지원 API | **빈 결과와 구분되어야 함** (FR-TLKD-005, D-T13) | 500 |
| `TransactionNotFoundException` | 원장에 없는 거래 | 404 상당 (FR-AZ-T03) | 500 |
| `MessageNotFoundException` | 범위 밖 메시지 | 404 상당, 열거 방지 (FR-AZ-T04) | 500 |
| `InvalidSerialException` | 숫자 아닌 일련번호 | 400 (FR-TLK-014) | 500 |

이것은 스프린트 T1 이 CR-T01 로 **이미 한 번 고친 결함과 정확히 같은 형태**다.
`GlobalExceptionHandler:104-121` 의 Javadoc 이 그 사건을 서술한다:

> 클라이언트 오류가 서버 오류로 보고되고, 로그에는 `UNHANDLED` 로 남아 운영에 잡음을 만들며,
> 호출자는 어느 파라미터가 빠졌는지 알 수 없다.

`MissingServletRequestParameterException` 하나만 고치고 같은 형태의 나머지 다섯을 남겼다.
`UnsupportedTransactionException` 의 경우 D-T13("빈 그리드로 열려 운영자가 '메시지가 없다'고
결론지음")을 고치려고 만든 예외가, 응답 단계에서 다시 구분 불가능해진다 — 통제가 **한 층
아래에서만** 작동한다.

**시험 공백** — `TalkExportControllerTest` 는 `exportService` 를 `@MockBean` 으로 갈아끼우므로
(`:38`, `:61` `willReturn(42)`) 상한 초과 경로가 핸들러 체인을 타지 않는다. 다섯 예외 중 어느
것의 HTTP 상태도 단언하는 시험이 없다.

**권장 조치** — `biztalk.api` 에 `@RestControllerAdvice` 를 하나 두고 매핑한다:
`RowCeilingExceededException` → 413 또는 409 + 메시지 포함(이 메시지는 서버 상수와 숫자만
담으므로 노출 위험이 없다), `TransactionNotFoundException`/`MessageNotFoundException` → 404
(**동일 본문**으로 열거 방지 유지), `UnsupportedTransactionException` → 409 + 전용 코드,
`InvalidSerialException` → `IllegalArgumentException` 상속으로 변경하거나 400 매핑.
각 매핑마다 상태 코드를 단언하는 `@WebMvcTest` 를 추가할 것.

---

#### HIGH-02 — 내보내기가 워크북 전체를 힙에 담는다. 스트리밍 작성기의 존재 이유가 무효화된다

**위치** `src/main/java/com/webcash/iris/biztalk/api/TalkExportController.java:137-142`

**근거**

```java
ByteArrayOutputStream buffer = new ByteArrayOutputStream();
int written = exportService.export(criteria, buffer);

return ResponseEntity.ok()
        .headers(headers(criteria, written))
        .body(buffer.toByteArray());
```

`StreamingWorkbookWriter` 의 Javadoc(`:43-48`)이 선언하는 성질:

> `SXSSFWorkbook` 은 창(window) 밖의 행을 임시 파일로 내보내므로 힙 사용량이 **행 수와 무관**하다(NFR-SCALE-T01).

이 성질은 `SXSSFWorkbook.write(output)` 이 **소켓으로 흘러갈 때만** 성립한다. 여기서는
`output` 이 `ByteArrayOutputStream` 이므로 워크북 바이트 전체가 힙에 쌓이고,
`buffer.toByteArray()` 가 그것을 **한 번 더 복사**한다 — 최대 순간 점유는 파일 크기의 2배다.
`ROW_CEILING = 100_000`(TalkExportService.java:68) × 9컬럼 xlsx 를 대략 10~25 MB 로 보면,
동시 요청 N 건에 대해 20~50 MB × N 이 힙에 잡힌다. SXSSF 는 임시 파일까지 함께 쓰므로 이
경로는 **디스크 I/O 비용은 그대로 내고 메모리 이득만 버린다** — 레거시 `XSSFWorkbook`(D-T12)
대비 개선이 아니라 등가에 가깝다.

컨트롤러의 주석(`:133-136`)은 버퍼링을 "거부가 부분 파일을 남기지 않게" 하려는 것으로
설명하지만, **상한 검사는 이미 `TalkExportService.export():128-139` 에서 어떤 바이트도 쓰기
전에 끝난다.** 버퍼링은 그 성질에 기여하지 않는다.

**권장 조치** — `ResponseEntity<StreamingResponseBody>` 또는
`HttpServletResponse.getOutputStream()` 으로 직접 쓴다. `X-Talk-Export-Rows` 는 쓰기 후에야
확정되므로, 헤더로 유지하려면 (a) `countAll` 결과를 그대로 쓰거나 (b) 트레일러/본문 마지막
행으로 옮긴다. 힙 독립성을 단언하는 시험(`TalkExportParityTest` 에 이미 유사 패턴 존재)을
컨트롤러 레벨까지 끌어올릴 것.

---

#### HIGH-03 — 보고서 전체 건수 산정이 요청마다 최대 100만 개의 키를 힙에 만든다

**위치** `src/main/java/com/webcash/iris/biztalk/domain/ReportService.java:260-282`,
`src/main/resources/mybatis/mapper/biztalk/ApiAggregateMapper.xml:174-180`,
`src/main/resources/mybatis/mapper/biztalk/bulk/BulkAggregateMapper.xml:132-137`

**근거**

```java
// ReportService.java:262-268
Set<AggregateKey> keys = new LinkedHashSet<>();
if (criteria.source().readsApi()) {
    keys.addAll(apiMapper.findKeys(criteria, MAX_KEY_PROBE + 1));
}
if (criteria.source().readsBulk() && bulkMapper.isPresent()) {
    keys.addAll(bulkMapper.get().findKeys(criteria, MAX_KEY_PROBE + 1));
}
```

`MAX_KEY_PROBE = 500_000`(`:66`). 두 출처 각각 최대 500,001 행을 가져와 `LinkedHashSet` 에
넣는다 — **요청 1건당 최대 1,000,002 개의 `AggregateKey` 레코드 + 두 개의 `String`**.
객체 헤더·문자열 포함 대략 100~150 MB 규모이며, `ReportService.query()` 는 **페이지를 넘길
때마다**(2페이지, 3페이지…) 이것을 처음부터 다시 수행한다. 캐시도, 요청 간 재사용도 없다.

`ApiAggregateMapper.xml:170-171` 의 주석은 이렇게 적고 있다:

> 전체 건수 산정을 위한 키만 조회한다. 두 컬럼뿐이므로 366일 상한에서도 가볍다.

"두 컬럼"은 **행당 비용**을 말하는 것이고, 문제는 **행 수**다. `PeriodPolicy` 의 366일 상한과
기관 수의 곱이 이 값을 정한다. 운영자가 전체 기관 × 1년을 조회하면 (366 × 기관수) 행이 되고,
기관이 500개면 183,000 행 — 상한에 걸리지 않으므로 `null` 로 낮춰지지도 않고 전부
materialize 된다. 설계 문서가 D-R8(무한정 조회)을 없애려고 keyset 페이징을 도입했는데,
건수 산정이 그 무한정 조회를 한 층 위에서 되살린다. `ReportService:60-63` 의 Javadoc 이 그
위험을 정확히 서술해 놓고도(“건수 산정 자체가 이 설계가 없애려던 무한정 조회가 된다”) 상한을
50만으로 잡아 실질적으로 열어 두었다.

**권장 조치** — SQL 로 내려보낸다. 두 출처의 키 합집합 건수는
`SELECT count(*) FROM (SELECT TRDD, IS_CD FROM … UNION SELECT TRDD, IS_CD FROM …)` 로
계산할 수 없다(데이터베이스가 둘이다). 대안 두 가지:
(a) 각 출처에서 `count(DISTINCT (TRDD, IS_CD))` 를 받고, 교집합 크기만 별도 계산 —
    `|A ∪ B| = |A| + |B| − |A ∩ B|`, 교집합은 키를 **날짜 단위로 접어** 훨씬 작은 집합으로 추정;
(b) `MAX_KEY_PROBE` 를 페이지 크기의 상수배(예: 10,000)로 낮추고, 초과 시 `null`(더 있음)로
    낮춘다 — 화면이 이미 `null` 을 처리한다(`:271-275`).
어느 쪽이든 첫 페이지에서만 계산하고 이어보기 요청에서는 건너뛸 것.

---

#### HIGH-04 — 메시지 상세 목록의 `ORDER BY` 가 전순서가 아니다. 오프셋 페이징에서 행이 중복·소실된다

**위치** `src/main/resources/mybatis/mapper/biztalk/TalkMessageMapper.xml:298`
(원인은 `:230-251` 의 `messageSource`)

**근거**

```xml
<!-- messageSource (:230-251) -->
SELECT ID, MSGKEY, … 'QUE' AS TABLE_TYPE FROM KKO_MSG
UNION ALL
SELECT ID, MSGKEY, … 'LOG' AS TABLE_TYPE FROM KKO_MSG_LOG
```

```xml
<!-- findMessages (:298-299) -->
 ORDER BY A.REQDATE DESC, A.MSGKEY DESC
 LIMIT #{c.size} OFFSET #{c.offset}
```

바로 위 주석(`:291-296`)이 근거를 이렇게 적는다:

> 전순서. 메시지키는 한 거래 안에서 유일하므로 동시각에도 순서가 정의된다 … 오프셋 페이징에서는
> 표시 성질이 아니라 **정확성 전제**다.

그런데 정렬 대상은 `KKO_MSG` 한 테이블이 아니라 **활성 + 보관의 `UNION ALL`** 이다.
"한 거래 안에서 유일"한 것은 각 테이블 안에서의 `MSGKEY` 이고, 합집합에서는 아니다. 발송
파이프라인이 활성 → 보관으로 행을 옮기는 동안(복사 후 삭제, 또는 보존 기간 중 양쪽 존재)
같은 `(REQDATE, MSGKEY)` 짝이 `TABLE_TYPE='QUE'` 와 `'LOG'` 로 두 번 나타날 수 있고, 그
순간 `ORDER BY` 는 두 행의 상대 순서를 정의하지 못한다. `TABLE_TYPE` 은 정렬 키에 없다.

이것은 정확히 D-T10 이 `FT_APITR_HSTR` 에서 고친 결함의 재현이며, 같은 파일이 그 결함을
인용하면서 발생시킨다. 결과도 같다 — 페이지 경계에서 행이 중복되거나 사라지고, 오류는 나지
않는다.

부수 효과: `TalkMessageDetailKey.of()` 는 `tableType` 을 요청에서 받으므로(프론트엔드
`TalkTransactionDetailPanel.tsx:262` 가 목록 행의 `row.tableType` 을 그대로 되돌려 보낸다),
목록이 중복 행을 낸 상태에서 사용자가 어느 쪽을 눌렀는지에 따라 상세가 갈린다.

**권장 조치** — `ORDER BY A.REQDATE DESC, A.MSGKEY DESC, A.TABLE_TYPE ASC` 로 확장한다.
`countMessages` 는 영향 없다(정렬 무관). 그리고 D-T10 회귀 시험과 같은 형태로,
동일 `(REQDATE, MSGKEY)` 가 QUE/LOG 양쪽에 존재하는 픽스처에서 페이지 1·2 의 합집합이
전체 집합과 같음을 단언하는 시험을 추가할 것 —
`TalkMessageMapperIntegrationTest` 에 자리가 있다.

---

#### HIGH-05 — ADR-TLK-024 의 보상 통제인 API 분류 대조가 운영 코드에서 한 번도 호출되지 않는다

**위치** `src/main/java/com/webcash/iris/biztalk/domain/TalkApiReconciliation.java:98`, `:112`

**근거**

`TalkApiReconciliation` 는 `@Service` 로 등록되어 있으나, 전체 `src/main/java` 에서 이 타입을
참조하는 곳이 **하나도 없다**. 참조는 `src/test/java/.../TalkApiReconciliationTest.java` 뿐이다.
`@Scheduled` 도 없고(이 프로젝트의 유일한 `@Scheduled` 는 범위 밖
`alimtalk/config/OutboxDispatchScheduler.java:63`), 컨트롤러 엔드포인트도 없고,
`ApplicationRunner`/`@EventListener` 도 없다.

이 클래스의 Javadoc(`:38-43`)이 그 자신의 존재 이유를 이렇게 적는다:

> 이 대조가 그 조용한 양식을 **이전 것만큼 시끄럽게** 만든다. 그것이 허용 목록을 쓸 수 있게
> 하는 조건이며, ADR-TLK-024 결정의 절반이다 — 나머지 절반인 허용 목록만으로는 안전하지
> 않다(RISK-T01).

허용 목록(`BizTalkApiRegistry`)은 운영에서 매 요청 동작한다. 그 짝인 대조는 동작하지 않는다.
따라서 SCOPE-T01 이 만든 **과소 포함**(실제 BizTalk 거래가 화면에서 조용히 사라짐)은
현재 어떤 신호도 남기지 않는다 — ADR 이 "안전하지 않다"고 명시한 상태 그대로다.

스프린트 T1 로그는 TC-REG-03 을 완료로 보고하며(`SPRINT-T1-LOG.md:172` "TC-REG-03 done")
테스트 커버리지 74 → 90 상승 근거로 삼았다. 시험은 실재하지만 **시험이 검증하는 동작이
운영에서 일어나지 않는다** — 커버리지 점수가 실제 통제를 과대 대표한다.

**권장 조치** — 셋 중 하나로 배선한다:
(a) `@Scheduled(cron=…)` 일 1회 — 클래스가 이미 로그 보고(`report():145-173`)를 갖고 있어 그대로 쓸 수 있다;
(b) 운영자 전용 진단 엔드포인트(`@PreAuthorize("hasRole('OPERATOR')")`);
(c) 애플리케이션 기동 후 1회 + 운영 대시보드 지표.
어느 쪽이든 **호출 지점이 존재함을 단언하는 시험**을 함께 둘 것 — 지금의 공백은 유닛 테스트로는
잡히지 않는 종류다. 아울러 MED-14(질의가 기관 범위를 갖지 않음)를 배선 **전에** 해소할 것.

---

#### HIGH-06 — 상세 경로가 기관을 원장에서 도출하고도 호출자 범위와 교차 검증하지 않는다

**위치** `src/main/java/com/webcash/iris/biztalk/domain/TalkDetailService.java:70-106`, `:149-164`
/ `src/main/resources/mybatis/mapper/biztalk/TalkMessageMapper.xml:158-165`

**근거**

```java
// TalkDetailService.java:70-80
TenantContext.TenantPrincipal principal = TenantContext.require();
TalkTransactionKey key = TalkTransactionKey.of(request.transactionDate(), request.serial());

TalkMessageMapper.TransactionOwner owner = mapper.findTransactionOwner(key);
if (owner == null) { … throw new TransactionNotFoundException(key); }
```

```xml
<!-- TalkMessageMapper.xml:158-165 -->
<select id="findTransactionOwner" resultMap="transactionOwner">
  SELECT A.FINTECH_ISCD, A.API_SVC_CD
    FROM FT_APITR_HSTR A
   WHERE A.TRDD    = #{k.transactionDate}
     AND A.IS_TUNO = #{k.serialForLedger}
</select>
```

`principal` 은 `require()` 로 얻어 **감사 기록에만** 쓰인다(`:77`, `:90`, `:114`). 도출된
`owner.institutionCode()` 는 그대로 `TalkMessageCriteria`(`:100`)와
`TalkMessageDetailKey.of(…)`(`:163-164`)로 흘러가며, **`PrincipalScope` 와 비교되는 지점이
없다.** 목록 경로는 비교한다 — `TalkHistoryService.toCriteria():183` 이
`PrincipalScope.resolve(principal, …)` 를 부르고 그 결과가
`TalkHistoryMapper.xml:163-165` 의 술어가 된다. **두 경로가 서로 다른 규칙 위에 있다.**

`TalkDetailService` 의 Javadoc(`:25-30`)은 이렇게 주장한다:

> 여기서는 **모든 조회가 원장에서 시작**한다. … 요청이 둘 중 어느 것도 지목할 방법이 없다.

이 주장은 참이지만 **다른 것을 증명한다.** 기관을 도출하면 호출자가 기관을 *고를* 수 없다.
호출자가 다른 기관의 거래에 *도달할* 수 없다는 뜻은 아니다 — 거래일자와 거래고유번호만
알면(또는 20자리 숫자 공간을 탐색하면) 어느 기관의 거래든 원장에서 찾히고, 서비스는 그
기관으로 메시지를 돌려준다. "도출했으므로 인가되었다"는 성립하지 않는 추론이다.

**현재 노출 여부** — 세 상세 엔드포인트 모두
`@PreAuthorize("hasRole('OPERATOR')")`(`TalkDetailController.java:71`, `:109`, `:138`)이고
운영자에게 전체는 권한이므로 **지금은 새지 않는다.** 그러나:

1. `PrincipalScope` 는 이용기관 주체를 규율하기 위해 존재하고(`PrincipalScope.java:38-44`,
   "AMB-02 는 PM 결정으로 폐기가 아니라 정제되었다"), 목록 경로는 그 분기를 실제로 탄다.
2. 이 슬라이스에서 이용기관 주체 분기는 **운영 경로에서 도달 불가능**하다 — 즉 FR-AZ-T02/T03 의
   테넌트 격리는 현재 `@PreAuthorize` 한 줄에만 의존한다.
3. 그 한 줄이 완화되는 날, 목록은 계속 격리되고 상세는 격리되지 않는다. `TalkMessageDetailKey`
   Javadoc(`:64-67`)이 경계하는 "통제가 조용히 뒤집히는 경로"가 정확히 이 형태다.

**권장 조치** — `TalkDetailService.messages()` / `detail()` 에서 `owner` 를 얻은 직후
범위를 교차 검증한다:

```java
PrincipalScope scope = PrincipalScope.resolve(principal, null);
if (!scope.allInstitutions()
        && !scope.institutionCode().equals(owner.institutionCode())) {
    // 없는 것과 권한 없는 것을 같은 응답으로 — TM-T10 열거 방지를 유지
    audit.recordAuth(…, Outcome.DENIED, "outOfScope " + key.describe(), sourceIp, null);
    throw new TransactionNotFoundException(key);
}
```

`TransactionNotFoundException` 을 재사용하는 것이 중요하다 — 전용 예외를 만들면 응답이
"그 거래는 존재하지만 당신 것이 아니다"를 알리는 열거 창구가 된다(이 코드베이스가
`MessageNotFoundException:339-344` 에서 이미 채택한 원칙).
`TalkHistoryAuthorizationTest` 에 이용기관 주체가 타 기관 거래의 상세에 도달하지 못함을
단언하는 시험을 추가할 것.

---

### 3.3 MEDIUM (다음 Sprint)

| ID | 위치 | 내용 | 권장 조치 |
|----|------|------|---------|
| MED-01 | `TalkMessageMapper.xml:371` + `TalkMessageDetailKey.java:72-75` | `AND A.MSGKEY = CAST(#{k.messageKey} AS INTEGER)` — `messageKey` 는 `@PathVariable` 로 들어와 `of()` 가 **공백 여부만** 검사한다(`if (messageKey == null \|\| messageKey.isBlank())`). 숫자가 아닌 값이 오면 PostgreSQL 이 `invalid input syntax for type integer` 를 던지고 HIGH-01 경로로 500 이 된다. 또한 `'007'` 과 `'7'` 이 같은 행에 일치해 두 URL 이 한 자원을 가리킨다 — `TransactionSerial.parse():142` 가 일련번호에는 `allMatch(Character::isDigit)` 검사를 하는데 메시지키에는 하지 않는 비대칭 | `TalkMessageDetailKey.of()` 에서 숫자 검증 + 정규화(선행 0 제거)를 수행하고 SQL 의 `CAST` 를 제거. `TransactionSerial` 과 같은 규칙을 쓸 것 |
| MED-02 | `ReportDataSourceConfig.java:88-131` | 대량 데이터소스에 `SqlSessionFactory`·`SqlSessionTemplate`·`MapperFactoryBean` 은 만들지만 **`PlatformTransactionManager` 를 만들지 않는다.** `ReportService.query()` 의 `@Transactional(readOnly = true)`(`:105`)는 기본 트랜잭션 매니저에만 묶이므로, 대량 질의는 그 트랜잭션 **밖에서** 별도 커넥션·autocommit 으로 실행된다. `readOnly` 도 적용되지 않는다. 두 출처를 "같은 조회"로 합치는 설계인데 트랜잭션 경계는 하나만 존재 | `bulkTransactionManager` 빈 추가 + `ChainedTransactionManager` 또는 대량 읽기를 명시적으로 비트랜잭션으로 선언하고 그 선택을 Javadoc 에 기록 |
| MED-03 | `BulkAggregateMapper.xml:120`, `:134`, `:142` | 세 질의 모두 `FROM KKB_APITR_SMTN` — **API 매퍼와 완전히 같은 테이블 이름**이다(`ApiAggregateMapper.xml:153`, `:176`, `:190`). `ReportDataSourceConfig.java:113-118` 의 Javadoc 은 자동 스캔에 맡기면 "대량 질의가 API 데이터베이스로 날아가 같은 데이터가 두 번 더해지고, 오류는 나지 않는다 — 합계만 두 배가 된다"고 경고하면서 명시 등록으로 막았다고 적는다. 그러나 **그 오배선이 실제로 일어나도 탐지할 수단이 없다** — 테이블 이름이 같으므로 질의는 성공하고 합계만 조용히 두 배가 된다. 방어 논거가 배선 한 줄에만 의존 | 기동 시 대량 커넥션의 `current_database()`/JDBC URL 이 기본 데이터소스와 다름을 단언하는 검사를 추가. 실패 시 기동 거부 |
| MED-04 | `TalkHistoryMapper.xml:152-154` | `WHERE A.TRDD BETWEEN … AND A.RGDT BETWEEN …` — **서로 다른 두 컬럼**(거래일자 / 등록일시)의 범위를 AND 로 묶는다. 두 컬럼의 날짜 부분이 어긋나는 행(자정 경계에 등록된 거래 등)은 두 술어를 동시에 만족하지 못해 **조용히 빠진다**. 이 슬라이스가 가장 경계하는 "과소 포함"의 형태이며, 어느 주석도 두 컬럼의 날짜가 항상 일치한다는 전제를 명시하지 않는다 | 전제를 데이터로 확인(T1-01b 와 함께 DBA 질의)하고, 성립하면 주석으로 고정, 성립하지 않으면 `RGDT` 술어만 남기고 `TRDD` 는 파티션 프루닝 힌트로 격하 |
| MED-05 | `TalkPeriodPolicy.java:86-98` vs `TalkHistoryMapper.xml:154` | 시각 역전을 여러 날 조회에서 허용하는 근거로 주석이 이렇게 적는다: "여러 날에 걸친 조회에서 09:00~08:00 은 … 그냥 각 날의 09:00~08:00 이므로 빈 결과가 된다." **SQL 은 그렇게 동작하지 않는다** — `A.RGDT BETWEEN fromTimestamp AND toTimestamp` 는 일자+시각을 이어붙인 **하나의 연속 구간**이므로(`TalkWindow.fromTimestamp():190`, `toTimestamp():200`) 09:00~08:00 은 빈 결과가 아니라 정상적인 연속 범위다. 검증 정책이 **틀린 전제** 위에 서 있다 | 주석을 SQL 의 실제 의미(연속 구간)로 정정하고, 그 의미가 FR-TLK-008 의 의도와 맞는지 PM 확인. "일별 시간대" 의미가 필요하다면 SQL 을 바꿔야 한다 |
| MED-06 | `StreamingWorkbookWriter.java:124-131` | `finally { workbook.dispose(); workbook.close(); }` — `dispose()` 가 예외를 던지면 `close()` 가 실행되지 않아 하부 `XSSFWorkbook` 의 ZIP 패키지·파일 핸들이 남는다. POI 는 `dispose()` 실패(임시 파일 삭제 거부 등)를 예외로 알린다 | `try { workbook.close(); } finally { workbook.dispose(); }` 또는 두 호출을 각각 try/finally 로 감싸 한쪽 실패가 다른 쪽을 건너뛰지 않게 할 것 |
| MED-07 | `TalkExportService.java:128-143` | `countAll` 로 상한을 검사한 뒤 `PagedRowIterable` 이 페이지 단위로 다시 읽는다. 두 시점 사이에 행이 늘면 **상한을 넘는 파일이 만들어진다** — 반복자에는 하드 캡이 없다(`fetch():244-268`). readOnly 트랜잭션이 스냅샷을 보장한다는 전제는 격리 수준에 의존하며 어디에도 명시되지 않았다 | 반복자에 `ROW_CEILING` 하드 캡을 두고 초과 시 `RowCeilingExceededException` 을 던진다(부분 파일이 남지 않도록 HIGH-02 수정과 함께) |
| MED-08 | `TalkMessageMapper.xml:192` | `AND decrypt(A.PHONE) LIKE '%' \|\| #{c.recipient} \|\| '%'` — (a) 함수가 **컬럼**에 적용되어 `UNION ALL` 전체 행에 복호화가 걸린다. `TransactionSerial.java:42-56` 이 정확히 이 구분(파라미터 vs 컬럼)을 sargability 정정 노트로 남겨 놓고 여기서는 컬럼 쪽을 택했다. (b) `recipient` 의 `%`·`_` 가 이스케이프되지 않아 사용자가 의도치 않은 와일드카드를 넣을 수 있다(주입은 아님 — 바인딩됨) | (a) 실행 계획을 측정해 `TalkHistoryLoadTest` 와 같은 형태로 기록. 필요 시 복호화 인덱스 또는 후보 축소 후 필터. (b) `LIKE … ESCAPE` 로 메타문자 이스케이프 |
| MED-09 | `TalkHistoryMapper.xml:287-294` | `findObservedApiServices` 는 `WHERE A.TRDD BETWEEN … GROUP BY A.API_SVC_CD` 뿐 — **기관 술어도, BizTalk 범위 술어도 없다.** `FT_APITR_HSTR` 는 전체 핀테크 API 거래 로그이므로 이 질의는 전 기관·전 API 의 거래량 분포를 반환한다. 현재는 HIGH-05 때문에 호출되지 않아 노출되지 않지만, 배선하는 순간 이 형태가 그대로 살아난다 | HIGH-05 배선 **전에** 운영자 전용으로 제한하고 결과를 로그·지표로만 소비할 것(응답으로 내보내지 말 것). 대조는 코드 목록만 필요하므로 건수를 버킷팅하는 것도 방법 |
| MED-10 | `ReportService.java:437-441` | `LOG.error("{} aggregate query failed for a reason that is not a source outage; …", label);` — 예외 `e` 를 인자로 넘기지 않아 **스택트레이스가 이 로그에 남지 않는다.** 재던지므로 상위에서 잡히긴 하지만, 이 로그의 목적이 "우리 코드 결함을 인프라 장애로 위장하지 않기"인데 정작 무엇이 실패했는지는 여기서 알 수 없다 | `LOG.error("… {}", label, e)` |
| MED-11 | `TalkHistoryPage.tsx:158-169` | `URL.createObjectURL(result.blob)` 후 `link.click()` 바로 다음 줄에서 `URL.revokeObjectURL(url)` 을 동기 호출하고, 앵커를 문서에 붙이지 않는다. Firefox/Safari 에서 다운로드가 시작 전에 취소될 수 있다. 또 `click()` 이 던지면 `catch`(`:169`)로 빠져 revoke 가 실행되지 않아 객체 URL 이 페이지 수명 동안 남는다 | 앵커를 `document.body` 에 append → `click()` → `setTimeout(() => { remove(); revokeObjectURL(url); }, 0)`, revoke 는 `finally` 로 |
| MED-12 | `reportApi.ts:198`, `talkDetailApi.ts:186`/`:225`, `talkHistoryApi.ts:234`/`:254` | `return (await response.json()) as T;` — 응답 형태를 검증하지 않고 캐스트한다. 스키마 변경이나 JSON 으로 파싱된 오류 페이지가 렌더 시점 크래시가 된다 | 최소한 필수 필드 존재를 확인하는 타입 가드, 또는 zod 등 런타임 검증 도입 |
| MED-13 | `TalkTransactionDetailPanel.tsx:113` | `recipient` / `talkResult` / `smsResult` 가 queryKey 에 직접 들어가 **수신번호 한 글자마다 조회가 나간다.** `:209` 의 조회 버튼은 `applied` 만 올려 사실상 장식이다. 수신번호 검색은 서버에서 `UNION ALL` 전체에 `decrypt()` 를 거는 질의(MED-08)이므로 비용이 크다 | 목록 화면(`TalkHistoryPage.tsx:123` 의 `submitted` 패턴)과 동일하게 제출된 값만 queryKey 에 넣을 것 |
| MED-14 | `ReportPage.tsx:155` | `watermark.isError` 가 어디에도 렌더되지 않아 **기준일 조회 실패와 "아직 집계 안 됨"이 구분되지 않는다.** ADR-RPT-022 가 기준일을 D-R25 대응 통제로 두었는데, 그 통제의 실패가 조용하다 | `watermark.isError` 일 때 '알 수 없음' 대신 명시적 오류 표시 |

---

### 3.4 LOW (백로그)

| ID | 위치 | 내용 |
|----|------|------|
| LOW-01 | `TalkApiReconciliation.java:99-100` | `today.minusDays(7)` ~ `today` 는 경계 포함이므로 실제 구간이 **8일**이다. `DEFAULT_WINDOW_DAYS = 7` 과 어긋난다 |
| LOW-02 | `TalkHistoryService.java:166-169` | `criteriaFor()` 가 `@Transactional(readOnly = true)` 인데 DB 를 건드리지 않는다. 게다가 내보내기 경로에서 이 트랜잭션과 `export()` 의 트랜잭션이 **분리**되어, 조건 조립과 데이터 읽기가 다른 트랜잭션에서 일어난다 |
| LOW-03 | `TalkHistoryController.java:113`, `TalkDetailController.java:86`/`:119`, `ReportController.java:101` | `request.getRemoteAddr()` — 리버스 프록시 뒤에서는 프록시 IP 가 감사에 남는다. FR-AZ-T05/FR-AZ-R05 의 "출처"가 실질을 잃는다 |
| LOW-04 | `talkDetailApi.ts:278` | `Number(response.headers.get('X-Talk-Export-Rows') ?? '0')` — 헤더가 없으면 조용히 "0건을 내보냈습니다"가 된다. 헤더 부재는 오류로 다뤄야 한다 |
| LOW-05 | `reportApi.ts:208`, `talkHistoryApi.ts:184` (및 `talkDetailApi.ts` 동형) | 빈 `catch {}` — JSON 이 아닌 본문에 대한 의도된 폴백이지만 파싱 오류 자체가 어디에도 남지 않는다 |
| LOW-06 | `ReportPage.tsx:235`, `TalkHistoryPage.tsx:173`/`:341`/`:359`, `TalkMessageDetailPanel.tsx:85`, `TalkTransactionDetailPanel.tsx:222` | `(error as Error).message` — `unknown` 을 캐스트한다. Error 가 아닌 값이 던져지면 오류 영역이 빈 채로 렌더된다(`reportApi.ts:38-43` 가 경계한 바로 그 경험) |
| LOW-07 | `ReportDataSourceConfig.java:89-96` | `bulkSqlSessionFactory` 가 애플리케이션의 MyBatis 설정(타입 핸들러·`Configuration` 설정값)을 상속하지 않는다. 지금은 `application.yml:103-104` 가 `mapper-locations` 만 정하므로 무해하나, 설정이 추가되는 날 두 출처가 다르게 동작한다 |
| LOW-08 | `PrincipalScope.java:102` | `requested.equals(own)` — 대소문자 구분 비교다. 기관코드가 대소문자 무시 체계라면 `abc` vs `ABC` 요청이 `overrideAttempted` 로 기록되지 않는다(격리는 유지됨, 탐지만 누락) |
| LOW-09 | `TalkTransactionDetailPanel.tsx:139`, `TalkMessageDetailPanel.tsx` | 오버레이 패널이 `role="dialog"`/`aria-modal` 없이 평범한 `<section>` 이고 포커스 관리가 없다. 레거시 팝업 창을 대체하는 요소이므로 접근성 요구가 승계된다 |
| LOW-10 | `TalkMessageDetailPanel.tsx:120` | `<Field label="" value="" />` — 빈 `<th scope="row">` 를 만들어 스크린리더가 라벨 없는 행 헤더를 읽는다 |
| LOW-11 | `TransactionSerial.java:201-215` | `pad()` 의 Javadoc 에 `@param`/`@return` 이 없다(private 이므로 도구 검사에는 걸리지 않으나 이 코드베이스의 다른 private 메서드와 일관되지 않음) |

---

### 3.5 False Positive — 룰 위반처럼 보이나 정당한 것 (명시적 기록)

| ID | 위치 | 왜 위반이 아닌가 |
|----|------|----------------|
| FP-01 | `PrincipalScope.java:79-103` | **리뷰 지시 1번의 두 성질이 모두 보존되어 있다.** (a) 이용기관 주체의 빈 값: `principal.operator()` 가 거짓인 분기에서 요청 값과 무관하게 `principal.effectiveInstitutionCode(null)` 로 좁혀지고, 그것이 `null` 이면 예외로 **거부**한다(`:94-96`) — 빈 값이 "전체"가 되는 경로가 존재하지 않는다. `ApiAggregateMapper.xml:110-112` 의 `<if test="!criteria.scope.allInstitutions">` 도 `allInstitutions` 가 참인 경우는 운영자 분기(`:82-84`)에서만 생기므로 fail-closed. (b) 무시 vs 거부: `:102` 가 `overrideAttempted` 플래그만 세우고 예외를 던지지 않는다 — 오류 메시지가 기관 열거 오라클이 되지 않는다. 시도는 `ReportService:126-135` 와 `TalkHistoryCriteria.describe():120-124` 에서 감사에 남는다. **HIGH 아님** |
| FP-02 | `ApiAggregateMapper.xml:200-207` | `WHERE FINTECH_ISCD IN <foreach>` 에 빈 컬렉션 가드가 없어 `IN ()` 구문 오류가 날 것처럼 보이나, 유일한 호출자 `ReportService.resolveInstitutionNames():302-304` 가 `if (missing.isEmpty()) return rows;` 로 먼저 빠져나간다 |
| FP-03 | `ReportService.java:206` | `query()`(트랜잭션)가 같은 빈의 `watermark()`(역시 `@Transactional`)를 자기호출해 프록시를 우회한다. 그러나 바깥 트랜잭션이 이미 readOnly 로 열려 있으므로 동작이 동일하다 — 실질 결함 아님 |
| FP-04 | `ReportDataSourceConfig.java:74-77` | `@ConfigurationProperties(prefix = "iris.report.bulk")` 가 `enabled` 까지 포함하는 접두사를 `DataSource` 에 바인딩한다. `ignoreUnknownFields` 기본값이 true 이므로 기동 실패하지 않는다 |
| FP-05 | `SourceMerger.java:60-93` + `ReportCriteria.java:75-77` | 처음에는 "한 출처의 페이지가 먼저 소진되면 아직 안 읽은 그쪽 행을 건너뛴 채 이어보기 키가 전진해 행이 소실된다"고 판정했으나, 반복 1회가 소비하는 각 출처 행이 최대 1개이므로 `size` 개를 전부 소비하면 `merged.size()` 가 반드시 `limit` 에 도달해 루프가 끝난다. 즉 소진은 그 출처가 `LIMIT` 미만을 반환했을 때(=진짜 끝)만 일어난다. `fetchSize() = size + 1` 이 그 판정을 성립시킨다. **병합 산술과 이어보기 페이징은 정합적이다** |
| FP-06 | `ReportService.java:392-400` `moreBeyondPage` | `row.source() == SendSource.ALL ? 2 : 1` 이 임의 가중처럼 보이나, `ReportRow.merged():85` 가 병합된 행에만 `SendSource.ALL` 을 설정하므로 정확히 "소비한 입력 행 수"다. 산술 정확 |
| FP-07 | 4개 매퍼 XML 전부 | `${}` 문자열 보간이 **한 곳도 없다.** 동적 테이블 선택은 `TalkMessageMapper.xml:231-250`/`:359-364` 의 `<choose>` 로 리터럴 테이블명을 고르며, `IN` 절은 `<foreach>` + `#{}` 다. SQL 인젝션 표면 없음 |
| FP-08 | `TalkDetailController.java:108` / `talkDetailApi.ts:213` | 메시지 상세는 서버·클라이언트 양쪽에서 **부모 거래를 반드시 경유한다.** 서버는 `TalkTransactionKey` 로 원장을 먼저 읽고 그 결과로만 `TalkMessageDetailKey` 를 만들며(`TalkDetailService.java:150-164`), 클라이언트는 `/{trdd}/{serial}/messages/{messageKey}` 경로를 조립한다. 메시지키 단독 조회 함수가 API 모듈에 존재하지 않는다. **CONST-BIZ-T01 의 계층 요구는 충족** — 남은 문제는 계층이 아니라 범위 교차 검증(HIGH-06) |
| FP-09 | `TransactionSerial.java:206-211` | 로그 포맷 placeholder 6개 / 인자 6개로 개수·순서가 모두 맞는다. 일련번호 **길이**만 남기고 값은 남기지 않으므로 PII 우려 없음 |

---

## 4. 강제 룰 점검

| 룰 | 결과 | 근거 |
|----|------|------|
| `// source:` 또는 `// req:` 주석 누락 0 | **PASS** | 대상 Java 파일 전수 확인. 모든 클래스·공개 메서드에 태그 존재. 매퍼 XML 도 `req:` 주석 보유(`ApiAggregateMapper.xml:54`, `:106`, `:116` 등) |
| 한국어+영문 Javadoc 준수 | **PASS** | 55개 Java 파일 전부 한/영 병기. `@param`/`@return`/`@throws` 도 병기. 레코드 컴포넌트에 `@param` 누락 없음 |
| 금액에 `double`/`float` 사용 0 | **PASS** | `grep -rn "\bdouble\b\|\bfloat\b" src/main/java/com/webcash/iris/biztalk/` → 4건 전부 **영문 산문 안의 단어**("double send", "double the lookups", "double-counts", "double-count"). 건수는 전부 `long`(`ChannelCounters.java:23`), 금액 컬럼은 애초에 프로젝션에서 제외(`TalkHistoryMapper.xml:69-76`) |
| PII 평문 로그 0 | **PASS** | 대상 패키지의 모든 `log.*` 호출 확인 — 전화번호·이메일·본문이 인자로 들어가는 곳 없음. `TalkMessageCriteria.describe():119-123` 은 수신번호 검색어를 **해시조차 하지 않고 생략**하고 `recipientFilter=yes` 만 남긴다(ADR-006 준수). SQL 은 `masking(decrypt(...))` 를 최외곽에만 적용(`TalkMessageMapper.xml:280-281`, `:339-340`) |
| 시크릿 하드코딩 0 | **PASS** | `ReportDataSourceConfig` 는 `*.yml` 을 수정하지 않고 속성 키만 선언(SEC-001 준수, `:19-34`). 소스에 자격증명 리터럴 없음 |
| `System.out` / `System.err` 사용 0 | **PASS** | `grep` 결과 0건. `printStackTrace()` 도 0건 |
| Conventional Commits 준수 | **N/A** | 대상 머지 커밋 메시지가 `BizTalk 내역` 으로 Conventional Commits 형식이 아니다. 다만 커밋 규약은 코드 리뷰 범위 밖이며 리포지토리 전반이 동일하므로 이 스프린트의 회귀는 아님. **PM 보고 항목** |
| 디렉터리 권한 위반 0 | **PASS** | `domain` → `infra.db` 인터페이스만 참조, `infra` → `domain` 타입 참조(레코드 매핑), `api` → `domain` 만. 대량 매퍼가 `infra.db.bulk` 하위 패키지로 분리되어 XML 위치(`mybatis/mapper/biztalk/bulk/`)와 일치. 순환 없음 |

---

## 5. 7 차원 자체 평가 검증

Sprint 로그의 자체 평가와 본 리뷰의 **독립 재산출**을 비교한다. 본 리뷰의 점수는 로그를
읽기 전에 코드만으로 산출했고, 아래 표에서 처음 대조했다.

### 5.1 스프린트별 자체 점수 (로그 원문)

| 스프린트 | 자체 점수 | 출처 |
|---------|---------:|------|
| R1 (이용기관 보고서) | **90.4** | `SPRINT-R1-LOG.md:127` |
| T1 (톡전송 내역 · 초기) | 81.6 | `SPRINT-T1-LOG.md:98` |
| T1 (톡전송 내역 · Addendum 개정) | **89.7** | `SPRINT-T1-LOG.md:174` |
| T2 (내보내기 · 초기) | 90.6 | `SPRINT-T2-LOG.md:106` |
| T2 (내보내기 · 개정) | **93.0** | `SPRINT-T2-LOG.md:192` |

리뷰 지시가 지목한 **T1 = 89.7** 을 주 비교 기준으로, T2 최종 93.0 을 부 기준으로 둔다.

### 5.2 독립 재산출 (R1 + T1 + T2 통합 범위)

| 차원 | 가중 | T1 자체 (89.7) | T2 자체 (93.0) | **code-reviewer 독립** | vs T1 | vs T2 |
|------|-----:|--------------:|--------------:|----------------------:|------:|------:|
| 완성도 Completeness | 20% | 90 | 95 | **80** | −10 | −15 |
| 추적성 Traceability | 15% | 96 | 97 | **88** | −8 | −9 |
| 보안 Security | 20% | 89 | 94 | **85** | −4 | −9 |
| 성능 Performance | 10% | 72 | 84 | **68** | −4 | −16 |
| 가독성 Readability | 15% | 93 | 93 | **90** | −3 | −3 |
| 표준 준수 Standards | 10% | 94 | 95 | **94** | 0 | −1 |
| 테스트 커버리지 Coverage | 10% | 90 | 94 | **82** | −8 | −12 |
| **가중 합** | | **89.7** | **93.0** | **84.1** | **−5.6** | **−8.9** |

### 5.3 차원별 근거

- **완성도 80 (자체 90/95)** — 세 경로(목록·상세·내보내기·보고서)가 동작하는 것은 맞다. 그러나
  (a) HIGH-05: ADR-TLK-024 가 "허용 목록만으로는 안전하지 않다"며 필수로 규정한 보상 통제가
  **운영에서 실행되지 않는다**; (b) HIGH-01: FR-TLKX-005 / FR-TLKD-005 / FR-TLK-014 가 정한
  사용자 대면 동작이 응답 계층에서 소실된다; (c) HIGH-02: NFR-SCALE-T01 이 요구한 힙 독립성이
  컨트롤러에서 무효화된다. 셋 다 "클래스가 존재하고 시험도 통과하지만 요구사항이 전달되지
  않는" 형태다.
- **추적성 88 (자체 96/97)** — 태그·ADR 개정·정정 노트 관행은 이 프로그램에서 본 것 중 최상급이다.
  감점은 **Javadoc 이 코드와 어긋난 세 곳** 때문이다: `StreamingWorkbookWriter` 의 힙 독립성 주장
  (HIGH-02 가 무효화), `TalkPeriodPolicy:86-98` 의 다일 시각 의미론(MED-05 — SQL 과 반대),
  `ReportDataSourceConfig:113-118` 의 이중 계상 방어 주장(MED-03 — 탐지 수단 없음).
  추적 가능한 거짓 주장은 추적 불가능한 침묵보다 위험하다.
- **보안 85 (자체 89/94)** — 통제의 밀도와 일관성은 높다(FP-01, FP-07, §4 PASS 전항). 감점은
  HIGH-06(도출≠인가), MED-09(범위 없는 교차기관 집계 질의), MED-01(검증 없는 값이 SQL CAST 도달).
- **성능 68 (자체 72/84)** — T2 가 84 로 올린 근거는 목록 경로의 부하 측정이다. 그 측정은 유효하나
  **내보내기와 보고서 경로는 측정되지 않았고**, 두 경로 모두 요청당 수십~수백 MB 힙을 요구하는
  구조다(HIGH-02, HIGH-03). 여기에 MED-08(컬럼 함수 + UNION ALL)이 더해진다. 측정된 한 경로의
  점수를 측정되지 않은 두 경로에 확장한 것이 자체 평가와 가장 크게 갈리는 지점이다.
- **가독성 90 (자체 93)** — 근접. 다만 `ReportService.query()` 106줄, 일부 클래스에서 주석이 코드의
  5배를 넘는 밀도는 유지보수자가 "무엇이 참인지" 판단하기 어렵게 만든다(실제로 세 곳이 틀렸다).
- **표준 준수 94 (자체 94/95)** — 일치. 강제 룰 8항 중 7항 PASS, 1항 N/A.
- **테스트 커버리지 82 (자체 90/94)** — 21개 테스트 클래스, 부하 테스트, XML 계약 테스트,
  통합 테스트까지 형태는 충실하다. 감점은 **커버리지가 통제의 실재를 증명하지 않는** 세 공백:
  (a) 다섯 도메인 예외의 HTTP 상태를 단언하는 시험 0건, (b) `TalkApiReconciliation` 의 호출
  지점을 단언하는 시험 0건 — 유닛 테스트는 통과하는데 운영에서는 실행되지 않는다,
  (c) 대량 데이터소스의 트랜잭션 경계·데이터베이스 분리를 단언하는 시험 0건.

### 5.4 자기 합리화 판정

| 기준 | 값 | 판정 |
|------|---:|------|
| Leader 자체(T1) 89.7 vs 독립 84.1 | **−5.6** | 10점 미만 → **자기 합리화 의심 없음 / PASS** |
| Leader 자체(T2 최종) 93.0 vs 독립 84.1 | **−8.9** | 10점 미만이나 **경계값** |
| Leader 자체(R1) 90.4 vs 독립 84.1 | −6.3 | 10점 미만 → PASS |

**총점 기준으로는 임계값을 넘지 않으므로 PM 에스컬레이션 조건에 해당하지 않는다.**

다만 **차원 단위**에서는 두 곳이 10점 이상 벌어진다 — 완성도(−10 / −15)와 성능(−16, T2 기준).
그리고 이 두 차원의 격차 원인은 같다: **산출물의 존재와 통제의 작동을 동일시한 것.**
클래스가 있고 시험이 통과하면 완성으로, 한 경로를 측정하면 슬라이스 전체를 측정한 것으로
셈했다. 이것은 은폐가 아니라 측정 기준의 문제이며, 스프린트 로그가 스스로에 대해 이례적으로
정직하다는 점(T1 이 81.6 을 그대로 보고하고 PM 판단을 요청한 것, T2 가 반박된 주장을 삭제 대신
정정 노트로 남긴 것)을 고려하면 **의도적 합리화로 판정하지 않는다.**

**PM 보고 사항 (참고)**: 총점 임계는 통과했으나 완성도·성능 두 차원의 산정 기준을 다음 스프린트
자체 평가 전에 합의할 것을 권고한다 — "배선되어 실행되는가"와 "측정된 경로가 전부인가"를
완성도·성능의 명시적 채점 항목으로 둘 것.

---

## 6. 판정 및 권고

| 항목 | 판정 |
|------|------|
| Sprint 종합 판정 | **CONDITIONAL APPROVE** |
| REJECT 사유 해당 | **없음** (강제 룰 8항 중 위반 0) |
| 다음 Sprint 진입 조건 | HIGH-01 ~ HIGH-06 **6건 전부 수정 + 각각 회귀 시험 추가** |
| Skill 5 (security-auditor / 교차검증) 진입 가능 여부 | **Y** — 단, HIGH-06 과 MED-09 를 security-auditor 에 **명시적으로 인계**할 것 |
| G3 릴리즈 게이트 진입 | **N** — HIGH-01·02·03·04 미해결 상태로는 불가 |

### 6.1 수정 우선순위

| 순위 | ID | 이유 |
|-----:|----|------|
| 1 | **HIGH-04** | 유일하게 **현재 운영에서 잘못된 데이터를 반환**하는 결함. 페이지 경계에서 메시지가 중복·소실되며 오류가 나지 않는다 |
| 2 | **HIGH-01** | 다섯 가지 정상 조건이 500 으로 나간다. 사용자 영향이 즉각적이고 수정 비용이 가장 낮다(핸들러 하나) |
| 3 | **HIGH-06** | 지금은 `@PreAuthorize` 로 막혀 있으나, 목록과 상세가 서로 다른 인가 규칙 위에 있는 상태 자체가 위험 자산이다 |
| 4 | **HIGH-02 / HIGH-03** | 부하 하에서만 드러나며 G3 성능 게이트를 통과할 수 없다 |
| 5 | **HIGH-05** | ADR 이 필수로 규정한 통제의 부재. 배선 자체는 간단하나 MED-09 를 먼저 해소해야 한다 |

### 6.2 이 스프린트에서 특히 잘한 것 (기록)

리뷰어로서 명시적으로 기록할 가치가 있는 것들 — 이후 스프린트가 낮추지 말아야 할 기준선이다.

1. **swallow 가 한 건도 없다.** `ReportService.rethrowUnlessSourceUnavailable():431-442` 는
   "인프라 장애만 부분 결과로 낮추고 우리 코드의 결함은 500 으로 드러낸다"를 타입 판정으로
   구현했고, 그 필요를 자신이 겪은 실제 사고(`javaType="long"` vs `_long`)로 문서화했다.
2. **열거 오라클 회피가 일관된다.** `PrincipalScope:98-102`(기관), `TalkHistoryService:202-210`(API 코드),
   `TalkDetailService:168-175`(메시지 존재) — 세 곳이 같은 이유로 같은 선택을 하고,
   각각 TM-T10 을 인용한다.
3. **반박된 주장을 삭제하지 않고 정정으로 남겼다.** `TransactionSerial:42-56` 과
   `TalkHistoryMapper.xml:84-100` 의 LPAD sargability 정정. 자기 평가의 신뢰도를 높이는 관행이다.
4. **`SELECT *` 금지와 프로젝션 폐쇄.** `TalkHistoryMapper.xml:69-76` 이 `FT_APITR_HSTR` 의
   계좌·카드·금액·사업자번호 컬럼을 왜 선택하지 않는지 명시하고 9컬럼으로 닫았다.
5. **집합 동일성으로 검증 가능한 설계.** `TalkHistoryService.criteriaFor()` 를 공개해
   내보내기와 목록이 타입 수준에서 갈라질 수 없게 만든 것은 FR-TLKX-001 을 산문이 아닌
   성질로 바꾼다.

---

**리뷰어 서명**

| 일자 | 에이전트 | 비고 |
|------|---------|------|
| 2026-08-20 | code-reviewer | 자동 생성. 대상 파일 전수 정적 리뷰. `./mvnw verify` 미실행 |
| — | (인간 검토) | (미실시) |
