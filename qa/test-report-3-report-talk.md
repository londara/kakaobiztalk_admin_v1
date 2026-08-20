# QA 테스트 결과 리포트 #3 — 이용기관 보고서(R1) + 톡전송 내역(T1/T2)

> **Skill**: 05 step [A] · **Date**: 2026-08-20 · **Agent**: `qa-engineer`
> **대상**: Sprint R1 / T1 / T2 산출물 21개 백엔드 테스트 클래스 + 2개 프론트엔드 테스트 파일
> **범위 밖**: A1(alimtalk), 로그인, 기관관리, 발신번호 슬라이스 — 집계에서 분리했다
> **선행 리포트**: [test-report-2.md](test-report-2.md)

---

## 0. 이 리포트의 판독 규칙

이 리포트의 모든 수치는 **2026-08-20 09:13 에 끝난 `mvn -o test` 1회 실행**(로그:
`qa/mvn-test-R1T1T2.log`, 1,477행)과 **같은 날 09:13 에 실행한 vitest 1회**에서 나온 실측치다.
스프린트 로그가 인용한 수치는 **다른 실행의 수치**이며, 이 리포트에서 그것을 재사용한
곳은 없다. 재현되지 않은 경우는 §5 에 별도로 적었다.

측정하지 못한 것은 **측정 불가**라고 적었다. 추정치는 없다.

---

## 1. 실행된 검증 / What ran

| 종류 | 도구 | 결과 | 소요 |
|------|------|------|------|
| 백엔드 전체 (JUnit 5 / Surefire 3.2.5) | `mvn -o test` | **833 run · 1 failure · 3 errors · 0 skipped** — **BUILD FAILURE** | 19분 14초 |
| ├ 이번 대상 21개 클래스 | — | **224 passed · 0 failed · 3 class-level errors · 0 skipped** | — |
| 프론트엔드 (vitest 4.1.10) | `npx vitest run ReportPage TalkHistoryPage` | **33 / 33 passed · 0 skipped** | 20.3초 |
| 커버리지 (JaCoCo 0.8.12) | `mvn -o jacoco:report` (기존 `jacoco.exec` 재사용) | 아래 §4 | — |
| 접근성 (axe-core) | — | **미실행 — 대상 화면에 검사가 존재하지 않음** (§6) | — |
| Parity (바이트) | — | **해당 없음** — 이 두 슬라이스는 포팅이 아니라 재구축(intent parity) | — |

> **중요**: 백엔드는 `mvn test` 만 돌았다. `mvn verify` 는 실행되지 않았으므로 **pom.xml 에
> 선언된 JaCoCo `check` 게이트(BUNDLE line ≥ 80% / branch ≥ 70%)는 이번 사이클에서 한 번도
> 강제되지 않았다.** §4 의 커버리지 수치는 내가 기존 `jacoco.exec` 로부터 `report` 목표만
> 별도 실행해 얻은 것이다.

---

## 2. 테스트 클래스별 실측치 / Per-class measured results

### 2.1 대상 21개 클래스

| # | 클래스 | run | pass | fail | **error** | skip | 비고 |
|---|--------|----:|-----:|-----:|------:|-----:|------|
| 1 | `api.TalkExportControllerTest` | 12 | 12 | 0 | 0 | 0 | `@WebMvcTest`, 4 nested |
| 2 | `api.TalkHistoryAuthorizationTest` | 7 | 7 | 0 | 0 | 0 | `@WebMvcTest`, 3 nested |
| 3 | `api.TalkHistoryContractTest` | 8 | 8 | 0 | 0 | 0 | 리플렉션 기반, DB·컨텍스트 불필요 |
| 4 | `domain.BizTalkApiRegistryTest` | 11 | 11 | 0 | 0 | 0 | |
| 5 | `domain.ChannelCountersTest` | 10 | 10 | 0 | 0 | 0 | |
| 6 | `domain.PeriodPolicyTest` | 16 | 16 | 0 | 0 | 0 | 12건이 `@ParameterizedTest` 전개 |
| 7 | `domain.ReportScopeTest` | 7 | 7 | 0 | 0 | 0 | |
| 8 | `domain.ReportServiceTest` | 16 | 16 | 0 | 0 | 0 | 매퍼 2종 전부 Mockito mock |
| 9 | `domain.ReportWatermarkTest` | 7 | 7 | 0 | 0 | 0 | |
| 10 | `domain.SourceMergerTest` | 16 | 16 | 0 | 0 | 0 | P-1..P-6 속성 시험 |
| 11 | `domain.TalkApiReconciliationTest` | 7 | 7 | 0 | 0 | 0 | |
| 12 | `domain.TalkDetailServiceTest` | 15 | 15 | 0 | 0 | 0 | |
| 13 | `domain.TalkExportParityTest` | 13 | 13 | 0 | 0 | 0 | |
| 14 | `domain.TalkHistoryServiceTest` | 18 | 18 | 0 | 0 | 0 | |
| 15 | `domain.TalkPeriodPolicyTest` | 19 | 19 | 0 | 0 | 0 | |
| 16 | `domain.TransactionSerialTest` | 15 | 15 | 0 | 0 | 0 | |
| 17 | `infra.db.AggregateMapperXmlTest` | 7 | 7 | 0 | 0 | 0 | XML 문자열 검사(tier 4) |
| 18 | **`infra.db.LpadTruncationTest`** | 1 | 0 | 0 | **1** | 0 | **@Test 4건 중 0건 실행** |
| 19 | **`infra.db.TalkHistoryMapperIntegrationTest`** | 1 | 0 | 0 | **1** | 0 | **@Test 10건 중 0건 실행** |
| 20 | `infra.db.TalkHistoryMapperXmlTest` | 15 | 15 | 0 | 0 | 0 | XML 문자열 검사(tier 4) |
| 21 | **`infra.db.TalkMessageMapperIntegrationTest`** | 1 | 0 | 0 | **1** | 0 | **@Test 19건 중 0건 실행** |
| 22 | `perf.TalkHistoryLoadTest` | 5 | 5 | 0 | 0 | 0 | **기본 사이클에서 실행됨** (§5.1) |
| | **합계** | **227** | **224** | **0** | **3** | **0** | |

> 클래스 21종이라 했으나 `TalkHistoryLoadTest` 를 포함하면 22 항목이다. 지시서의 21종 목록과
> 동일한 파일들이며 번호만 다르다.

### 2.2 이번 대상 밖의 실패 1건

| 테스트 | 결과 | 판정 |
|--------|------|------|
| `auth.config.CsrfIntegrationTest.echoingCookieValueInHeaderPasses` | FAILURE — `Expecting actual not to be null` | **범위 밖 · 기존 결함(CR-02)**. 로그인 슬라이스 소유. test-report-2 §2 에 이미 기록 |

반면 스프린트 R1/T1/T2 로그가 "기존 실패 2건" 으로 함께 세었던
`common.logging.ExceptionHandlerOrderTest.authAdvicePrecedesGlobalAdvice` 는 **이번 실행에서
2 run / 0 failure 로 통과**했다. 즉 기존 실패는 이제 2건이 아니라 **1건**이다.

### 2.3 프론트엔드 실측치

| 파일 | tests | pass | fail | skip |
|------|------:|-----:|-----:|-----:|
| `features/biztalk/ReportPage.test.tsx` | 19 | 19 | 0 | 0 |
| `features/biztalk/TalkHistoryPage.test.tsx` | 14 | 14 | 0 | 0 |
| **합계** | **33** | **33** | **0** | **0** |

---

## 3. 통합 테스트는 skip 되지 않았다 — **3개 클래스가 ERROR 로 죽었다**

이것이 이번 사이클의 가장 중요한 발견이다.

`*MapperIntegrationTest` 와 `LpadTruncationTest` 는 조건부 skip(`@Disabled`, `assumeTrue`,
`@EnabledIf…`)을 **하나도 쓰지 않는다**. DB 가 없으면 조용히 건너뛰는 것이 아니라 **에러로
빌드를 깨뜨린다** — 설계로서는 정직하다. 그런데 이번 실행에서 실제로 깨졌다.

```
[ERROR] Errors:
[ERROR]   LpadTruncationTest.startPostgres:56 » IO Gave up waiting for server to start after 10000ms
[ERROR]   TalkHistoryMapperIntegrationTest.startDatabase:79 » IO Gave up waiting for server to start after 10000ms
[ERROR]   TalkMessageMapperIntegrationTest.startDatabase:73 » IO Gave up waiting for server to start after 10000ms
Caused by: org.postgresql.util.PSQLException: FATAL: the database system is starting up
```

### 3.1 기제

이 환경에서 `EmbeddedPostgres.start()` 의 `initdb` 가 **1분 22초 ~ 1분 28초** 걸린다(로그
실측). 그 뒤 zonky 라이브러리는 postmaster 기동을 **하드코딩된 `PT10S`** 동안만 기다리고
포기한다. 로그를 보면 포기 직후 다음 클래스의 로그 블록 첫 줄에 이전 인스턴스의
`database system is ready to accept connections` 가 찍힌다 — **PostgreSQL 은 정상 기동했고,
테스트가 기다려 주지 않았을 뿐이다.**

각 클래스가 **자기 `EmbeddedPostgres` 인스턴스를 따로 띄우기 때문에** 이 90초짜리 initdb 가
4번 반복된다(3개 통합 + 1개 부하). 그중 3번이 10초 창을 넘겼다.

### 3.2 실행되지 못한 시험 33건과 그것이 담고 있던 주장

| 클래스 | 미실행 @Test | 이 클래스만이 **실행으로** 증명하던 것 |
|--------|---:|---|
| `LpadTruncationTest` | 4 | D-T9 의 기제 — PostgreSQL `lpad` 가 14자리를 `'2608190014'` 로 잘라낸다는 사실 자체 |
| `TalkHistoryMapperIntegrationTest` | 10 | TC-T001-06 페이징 속성(데이터 위), D-T11 count/page 일치, D-T2 기관 격리, D-T26 NULL 통과, TC-REG-03/04, **CONST-SEC-T01 의 실행 강제**(9컬럼 스키마) |
| `TalkMessageMapperIntegrationTest` | 19 | D-T5 교차기관 키, **D-T6 마스킹 적용**, D-T7 채널별 테이블, D-T8 친구톡 필터, D-T17 4자리 연도, **D-T18 `decrypt` 별칭 매핑**, D-T19 상태 변경, D-T20 원값 보존, D-T22 미수신 분할, 20필드 존재 |
| **합계** | **33** | |

**결과적으로 이번 실행에서는 매퍼↔DB 경계가 단 한 줄도 검증되지 않았다.** SPRINT-T1 Addendum
과 SPRINT-T2 §3.2 가 "verified by execution" 으로 승격한 D-T5·D-T6·D-T9·D-T17·D-T18·D-T19 는
**이 실행 기준으로는 다시 verified-by-placement 로 내려간다.**

JaCoCo 로는 이 손실이 보이지 않는다는 점도 함께 기록한다. `TalkDetailService` 는 mock 기반
`TalkDetailServiceTest` 덕에 line 93.8% 를 유지하고, 잃어버린 것은 **매퍼 XML 안의 SQL** 인데
JaCoCo 는 XML 을 세지 않는다. **커버리지 숫자는 이 회귀에 대해 침묵한다.**

### 3.3 권고 (즉시)

1. `EmbeddedPostgres.builder().setServerStartupWait(Duration.ofSeconds(90)).start()` 로 대기창을
   넓힌다. 한 줄이고, 이번 실패 3건 모두를 없앤다.
2. 4개 클래스가 **하나의** 인스턴스를 공유하도록 JUnit 5 `@ExtendWith` + `CloseableResource`
   확장으로 묶는다. initdb 4회(약 6분)가 1회로 줄어든다.
3. **DB 미가용을 skip 으로 강등하지 말 것.** 지금의 ERROR 동작이 옳다. 조용한 skip 은 이
   슬라이스가 34번 고친 silent-success 와 같은 모양이다.

---

## 4. 커버리지 실측치 / Measured coverage

`mvn verify` 는 돌지 않았으므로 게이트는 강제되지 않았다. 아래는 `jacoco.exec`(531 KB,
09:12 생성)로부터 `report` 목표를 별도 실행해 얻은 값이다.

### 4.1 전체 및 대상 패키지

| 범위 | LINE | BRANCH | pom 게이트 | 판정 |
|------|------|--------|-----------|------|
| **BUNDLE 전체** | **67.5%** (2,347/3,476) | **64.4%** (959/1,489) | ≥ 80% / ≥ 70% | **게이트 미달** |
| `biztalk` (alimtalk 제외) | 68.7% (1,184/1,723) | 64.5% (450/698) | — | 참고 |

> **해석 주의 3가지.**
> ① BUNDLE 은 auth·alimtalk·기관관리·발신번호를 모두 포함하므로 이 값이 곧 R1/T1/T2 의
> 성적은 아니다. ② 이번 실행은 33건이 미실행이므로 값이 낮게 나온다. ③ 그럼에도 **80/70
> 게이트를 통과하지 못한다는 사실 자체는 실측**이며, `mvn verify` 를 돌리면 지금 빌드는
> 커버리지 단계에서도 실패한다.

### 4.2 대상 슬라이스 핵심 클래스 (실측)

| 클래스 | LINE | BRANCH |
|--------|------|--------|
| `SourceMerger` | **100.0%** (23/23) | **100.0%** (18/18) |
| `PeriodPolicy` | **100.0%** (27/27) | **100.0%** (10/10) |
| `BizTalkApiRegistry` | **100.0%** (25/25) | **100.0%** (16/16) |
| `TalkApiReconciliation` | **100.0%** (39/39) | **100.0%** (12/12) |
| `TalkHistoryService` | **100.0%** (54/54) | 91.7% (11/12) |
| `TalkPeriodPolicy` | **100.0%** (29/29) | 88.2% (15/17) |
| `TalkHistoryCriteria` | **100.0%** (19/19) | 92.9% (13/14) |
| `ReportScope` / `ReportWatermark` / `AggregateKey` | 100% | 94.7% / n/a |
| `StreamingWorkbookWriter` | **100.0%** (31/31) | 87.5% (7/8) |
| `TalkExportController` / `TalkHistoryController` | **100.0%** | n/a |
| `TalkDetailService` | 93.8% (106/113) | 88.9% (16/18) |
| `TransactionSerial` | 91.2% (31/34) | 75.0% (15/20) |
| `ReportService` | 90.6% (125/138) | 70.1% (61/87) |
| `TalkExportService` | 90.2% (37/41) | 100.0% (4/4) |
| `SourceAvailability` | 85.7% (12/14) | **65.0%** (13/20) |
| `ChannelCounters` | 100.0% (16/16) | **75.0%** (15/20) |
| **`ReportController`** | **0.0% (0/8)** | **n/a** |
| **`TalkDetailController`** | **0.0% (0/16)** | **n/a** |

**도메인 계층은 목표(line ≥ 80 / branch ≥ 70, 도메인 ≥ 95)를 대부분 넘긴다. 무너지는 곳은
컨트롤러 두 개다.**

`ReportController` 0/8 과 `TalkDetailController` 0/16 은 **해당 엔드포인트에 대한 테스트가
하나도 없다**는 뜻이다. 이것은 단순한 커버리지 구멍이 아니다:

- `ReportController` — TEST-PLAN-REPORT §4 의 **S-R01…S-R16 부정경로 보안 스위트 전체**가
  여기에 걸려 있다. 그중 **S-R04(50개 기관코드 열거)** 는 계획서가 "the gate test for T-R10"
  이라고 명시한 **CVSS ≈ 9.1 위협의 게이트 시험**이다. SPRINT-R1-LOG §7 DoD 도 이 항목을
  체크하지 않은 채 남겨 두었고, R2 가 실행되지 않았으므로 **여전히 없다**.
- `TalkDetailController` — 화면 31·32 의 엔드포인트. TEST-PLAN-TALK §3.2 의 TC-T001-01/02 는
  "다섯 엔드포인트 **전부**" 를 요구하는데, 실제로 인가가 단언된 엔드포인트는 목록·필터·
  내보내기 **3개뿐**이고 상세 2개는 단언되지 않았다.

**그리고 이 두 구멍은 "DB 가 없어서" 라는 이유를 댈 수 없다.** SPRINT-T1 Addendum A2 가 바로
그 추론을 반증했다 — `@WebMvcTest` 는 `DataSource` 없이 실제 시큐리티 필터 체인을 태운다.
그 교훈이 `TalkHistoryAuthorizationTest`·`TalkExportControllerTest` 에는 적용됐고
`ReportController`·`TalkDetailController` 에는 **적용되지 않았다.**

---

## 5. Parity / 부하 — 무엇을 재고 무엇을 못 쟀나

### 5.1 `TalkHistoryLoadTest` 는 기본 사이클에서 **제외되지 않는다**

SPRINT-T2-LOG Addendum B1 은 "`@Tag("load")`, excluded from the default cycle" 이라 적고,
표준 준수 점수를 94 → 95 로 올리며 그 근거로 "Load test tagged out of the default cycle" 을
들었다.

**실측: 그 제외는 설정되어 있지 않다.**

- `pom.xml` 에 `maven-surefire-plugin` 구성 자체가 없다(`excludedGroups` 없음).
- 저장소 어디에도 `junit-platform.properties` 가 없다.
- 그리고 로그가 증명한다 — `TalkHistoryLoadTest` 는 이번 `mvn test` 에서 **실행됐고**,
  **761.6초**를 썼다. 전체 빌드 19분 14초 중 **약 66%** 다. `PagingCost` 한 클래스만
  **636.7초**.

`@Tag` 는 그것을 걸러 줄 설정이 있어야 효력이 생긴다. 지금은 태그만 붙어 있고 필터가 없다.

### 5.2 부하 수치는 **재현되지 않았다**

`TalkHistoryLoadTest` 는 밀리초를 단언하지 않고 `System.out` 으로 보고한다(설계 의도이며
Javadoc 에 그 이유가 적혀 있다 — 옳은 판단이다). 그래서 실행마다 값이 달라도 **테스트는 항상
통과한다**. 이번 실행의 보고 수치를 T2 로그의 수치와 나란히 둔다.

| 측정 항목 | SPRINT-T2-LOG B1 (2026-08-19) | **이번 실행 (2026-08-20)** | 배수 |
|---|---:|---:|---:|
| 목록 첫 페이지 P95 | 305 ms | **2,514 ms** | 8.2× |
| 목록 마지막 페이지 P95 (offset 149,900) | 822 ms | **2,990 ms** | 3.6× |
| 건수 질의 P95 | 237 ms | **4,235 ms** | 17.9× |
| 내보내기 힙 1k / 100k | 39.8 MB / 30.8 MB | **5.2 MB / 18.3 MB** | — |
| **힙 비율 (100k ÷ 1k)** | **0.77** | **3.55** | — |
| 페이징 정확성 (150,000행, page=997) | 150,000 emitted / 150,000 unique | **150,000 / 150,000** | 일치 |

**읽는 법.**

1. **페이징 정확성만이 재현됐다.** 151페이지, 15만행, 중복 0, 누락 0. D-T10 과 TC-LOAD-04 는
   실측으로 **재확인**된다. 이것이 이 부하 테스트의 진짜 성과이며, 유일하게 하드웨어와
   무관한 결론이다.
2. **밀리초는 재현되지 않았다.** 같은 노트북, 같은 코드, 같은 20만행 픽스처인데 8~18배
   차이가 났다. T2 로그가 "The dev figures have headroom against the 3 s and 1 s targets" 라고
   적은 여유는 **이번 실행에는 존재하지 않는다** — 건수 질의 P95 4,235 ms 는 NFR-PERF-T01 의
   3초 목표를 개발 장비에서조차 넘어선다. (T2 로그는 이 수치를 SLA 로 주장하지 않았으므로
   **로그가 틀린 것은 아니다.** 다만 "여유가 있다" 는 서술은 1회 측정에 기댄 것이었다.)
3. **힙 비율 0.77 → 3.55.** T2 로그는 "The **ratio below 1.0** for a 100× row increase is the
   finding, and it is **unambiguous**" 라고 적었다. 이번 실행은 3.55 다. 100배 행에 3.55배
   힙이므로 **스트리밍이 동작한다는 결론(선형이 아님)은 유지된다.** 그러나 "0.77" 이라는
   구체 수치는 테스트 자신의 Javadoc 이 경고한 대로 JIT·POI 워밍업 잡음이었고, 로그가 그것을
   `unambiguous` 로 승격한 것은 과했다. **단언 임계값이 25배라서 두 결과 모두 통과한다** —
   즉 이 단언은 0.77 과 3.55 를 구분하지 못한다.

### 5.3 부하 테스트가 **측정하지 않은 것**

| 계획 | 계획서 요구 | 실제 | 판정 |
|------|------|------|------|
| TC-LOAD-01 | 목록 P95 < 3 s, **2× 동시 운영자** | `p95()` 는 단일 스레드 루프. 동시성 0 | **동시성 미측정** |
| TC-LOAD-02 | **상세 P95 < 1 s**, lpad 유무 비교 | **상세 질의(`TalkMessageMapper`)를 단 한 번도 호출하지 않는다.** lpad 비교는 `EXPLAIN` 플랜 비교이지 P95 가 아니다 | **NFR-PERF-T02 는 전혀 측정되지 않음** |
| TC-LOAD-03 | 내보내기 힙 1k→100k 평탄 | 측정됨(비율 3.55, §5.2) | 측정됨, 잡음 큼 |
| TC-LOAD-04 | 실제 동률 밀도에서 페이징 정확성 | 측정됨, 통과 | **재현됨** |
| L-R01…L-R06 | **이용기관 보고서** 부하 6종 | **보고서 슬라이스 부하 테스트가 존재하지 않는다** | **전무** |
| 하네스 §3 / TEST-PLAN §9 L-R05 | **SLA 2배 부하** | 어느 테스트도 2배 부하를 걸지 않는다 | **미실행** |

**추적표 정정 필요.** `mapping/trace/requirements-trace-biztalk.csv` 는
`NFR-PERF-T02,…,load,TalkHistoryLoadTest,T2,PARTIAL-DEV-HARDWARE` 로 적혀 있다. 이 상태값은
"개발 장비라서 SLA 판정만 못 한다" 는 뜻으로 읽히는데, **실제로는 상세 질의가 한 번도
계측되지 않았다.** `PARTIAL-DEV-HARDWARE` 가 아니라 `NOT-MEASURED` 가 정확하다.

### 5.4 Parity

바이트 parity 는 **해당 없음**이다. 두 슬라이스 모두 34개(TALK) / 25개(REPORT) 승인된
의도적 편차를 가진 재구축이며, 계획서가 parity 를 **intent parity** 로 정의했다
(TEST-PLAN-TALK §10, TEST-PLAN-REPORT §11).

`TalkExportParityTest` 는 이름과 달리 레거시 대조가 아니라 **내부 두 경로(화면 조회 ↔
내보내기) 사이의 집합 동일성**을 단언한다 — D-T1 회귀. 그 목적으로는 잘 설계됐다(§7 참조).

---

## 6. 프론트엔드 접근성 (WCAG 2.1 AA) — **대상 화면에 검사가 없다**

| 항목 | 실측 |
|------|------|
| `axe-core` 의존성 | `package.json:31` — `"axe-core": "^4.13.0"` 존재 |
| axe 검사가 있는 파일 | `src/features/auth/accessibility.test.tsx` **1개뿐** |
| 검사 대상 화면 | `LoginPage`, `PasswordChangePage`, `OtpRegisterPage` |
| **`ReportPage`** | **axe 검사 없음** |
| **`TalkHistoryPage`** | **axe 검사 없음** |
| `TalkTransactionDetailPanel` / `TalkMessageDetailPanel` | **axe 검사 없음** |

기존 `accessibility.test.tsx` 는 좋은 틀을 이미 갖고 있다 — `color-contrast`·`region` 을 jsdom
에서 무의미하다는 이유로 명시적으로 끄고, 그 한계를 Javadoc 에 적어 둔다. **그 틀을
`ReportPage`/`TalkHistoryPage`/두 상세 패널로 확장하는 것이 이번 사이클에서 가장 값싼
보완**이다. 두 화면 모두 데이터 그리드 + 폼이라 라벨 누락·중복 id·잘못된 role 중첩이 나기
쉬운 구조다.

**환송 판정: 접근성 항목은 미달.** frontend-developer 에게 `ReportPage`·`TalkHistoryPage`·
상세 패널 2종에 대한 axe 검사 추가를 요청한다.

---

## 7. 커버리지 갭 — 계획됐으나 존재하지 않는 테스트

계획서의 테스트 ID 와 실제 테스트를 **동작 기준으로** 대조했다(대부분의 테스트가 TC-ID 대신
결함 ID 를 문자열로 달고 있어, ID grep 이 아니라 DisplayName·단언 내용으로 대조했다).

범례: **○** 존재·실행·통과 / **△** 부분 또는 다른 계층에서만 / **▲** 존재하나 이번 실행에서
ERROR 로 미실행 / **✕** 존재하지 않음

### 7.1 이용기관 보고서 — 경계값 (지시서 §3)

| 요구사항 | 요구 내용 | 테스트 | 판정 |
|---|---|---|---|
| **FR-RPT-002** | 366일 상한 | `PeriodPolicyTest$Span#enforcesTheCapAtItsBoundary` — 366 허용 / 367 거부 **양쪽 다 단언** | **○** |
| **FR-RPT-003** | 시작 ≤ 종료 | `$Span#rejectsInvertedRange` + `#acceptsSingleDay`(경계 등호) | **○** |
| **FR-RPT-004** | YYYYMMDD 검증 | `$Format#rejectsNonCalendarDates`(`20261332`,`20260230`,`20260000`,`00000000`,`99999999`) + `#rejectsWrongLengthOrShape`(7·9자리, 하이픈, 문자, 공백) + `#rejectsNull` — 12 전개 | **○** |
| **FR-RPT-005** | 페이지네이션 | `SourceMergerTest$Paging#pagingReproducesTheWholeResult` — 페이지 크기 1·2·3·5·7·13·50 전개, 키공간(80)보다 두 출처 합(115)이 커서 **공유 키 위에 경계가 떨어지도록 설계** | **○** (설계 우수) |
| **FR-RPT-006** | 결정적 정렬 | `SourceMergerTest$OrderViolation` 3건 — 오름차순·키 중복·날짜 내 역방향을 각각 `IllegalStateException` 으로 검출 | **○** |
| FR-RPT-005 (SQL) | 매퍼 seek 술어 | `AggregateMapperXmlTest` — **XML 문자열 검사만**. 실행 검증 없음 | **△** |

**FR-RPT-002~006 다섯 항목은 모두 대응 테스트가 있고, 경계 양쪽을 단언한다.** 지시서가 물은
다섯 개는 전부 충족이다. 단 하나의 유보는 **SQL 계층**이다 — R1-LOG §4.1 이 keyset seek 술어의
행값(row-value) 형태가 **틀렸다**고 기록했고 그것을 잡은 것이 인메모리 속성 시험이었다.
지금도 그 술어가 실제 PostgreSQL 에서 무엇을 반환하는지는 **아무도 실행해 본 적이 없다**.

### 7.2 이용기관 보고서 — 계획됐으나 없는 테스트

| 계획 ID | 내용 | 판정 | 근거 |
|---|---|---|---|
| **S-R01…S-R16** (16건) | 부정경로 보안 스위트 전체 | **✕ 16/16 전무** | `ReportController` line coverage **0.0% (0/8)**. `@WebMvcTest` 클래스 없음 |
| **└ S-R04** | 50개 기관코드 열거 — **T-R10(CVSS≈9.1) 게이트 시험** | **✕** | R1-LOG §7 DoD 미체크, R2 미실행 |
| **└ S-R06** | 내보내기 직접 호출을 조회와 동일하게 거부 | **✕** | 보고서 내보내기(R2) 자체가 미구현 |
| F-R01 / F-R02 | 대량 출처 부재·타임아웃 → 부분 결과 | **○** | `ReportServiceTest$PartialResults` 2건 |
| F-R03 | **API 출처** 장애 시 대칭 동작 | **✕** | 대량 쪽만 시험. 설계가 어느 출처도 편애하지 않는다는 주장이 단언되지 않음 |
| F-R04 | 양쪽 모두 장애 → 명시적 오류(0 으로 위장 금지) | **✕** | |
| F-R05 / F-R06 | 출처 결손 휴리스틱과 **오탐 방지**(ADR-RPT-022) | **✕** | `ReportWatermarkTest` 의 "기준일 아래의 빠진 날은 감지하지 못한다 — 문서화된 한계" 가 휴리스틱 **미구현**을 명시. 시험 대상이 존재하지 않음 |
| F-R07 | 출처 장애 중 내보내기 | **✕** | 내보내기 미구현(R2) |
| W-R01 / W-R02 / W-R04 / W-R06 / W-R07 | 워터마크·항등식 | **○** | `ReportWatermarkTest`, `ChannelCountersTest`, 프론트 4건 |
| W-R03 | 빈 결과 ↔ 미집계 ↔ 오류 3자 구분 | **△** | 프론트 "빈 결과를 오류와 구분해 표시한다" 는 2자 구분. **미집계와의 구분은 단언되지 않음** |
| W-R05 | 일반이미지 = 전체이미지 − 와이드 **정확히** | **△** | `ChannelCountersTest` 는 그 식이 음수가 될 수 있음만 다룸. 등식 자체를 단언하는 시험 없음 |
| **P-7** | 총건수 = 실제 병합 행수 (MAX_KEY_PROBE 상한까지) | **△** | `ReportServiceTest$Merging` 이 합집합 크기는 단언. **상한 초과 시 "unknown"** 은 프론트에서만 단언 |
| **L-R01…L-R06** (6건) | 보고서 부하 6종 | **✕ 6/6 전무** | 보고서 슬라이스 부하 테스트 파일 자체가 없음 |
| **D-R13** | 기관명 조인, **실행 계획에 행별 서브쿼리 없음**(tier 1) | **✕** | XML 문자열 검사만. `EXPLAIN` 없음 |
| **D-R15** | 1k/100k 내보내기 힙 평탄 | **✕** | 보고서 내보내기 미구현 |
| E-R1…E-R5 | E2E TOP 5 | **✕ 5/5 전무** | 요청 수준 E2E 테스트 없음 |

### 7.3 톡전송 내역 — 계획됐으나 없거나 미실행인 테스트

| 계획 ID | 내용 | 판정 | 근거 |
|---|---|---|---|
| TC-T001-01 / 02 | **다섯 엔드포인트 전부** 401 / 403 | **△ 3/5** | 목록·필터(`TalkHistoryAuthorizationTest`), 내보내기(`TalkExportControllerTest`) 는 ○. **상세 2개는 ✕** — `TalkDetailController` 0.0% (0/16) |
| TC-T001-03 | 정적 검사: 패키지 내 `SELECT *` 없음 | **○** | `TalkHistoryMapperXmlTest$Projection` |
| TC-T001-05 / 06 | count↔page 일치 / 페이징 합집합 속성 | **▲** | `TalkHistoryMapperIntegrationTest` — **ERROR 로 미실행**. 부하쪽 `pagingStaysCorrectAtVolume` 가 15만행에서 동일 속성을 **○** 로 커버 |
| TC-T001-07 / 08 | 32일·역전·오형식·센티널 거부 | **○** | `TalkPeriodPolicyTest` 19건 |
| TC-T001-09 / TC-T002-09 / 10 | 10·14·20자리 일련번호 동치 | **△** | `TransactionSerialTest` 15건 **○**(도메인). DB 대조는 `TalkMessageMapperIntegrationTest` **▲** |
| TC-T001-12 | 응답 필드 집합이 **정확히** 9(+2) | **○** | `TalkHistoryContractTest$ResponseRow` — 순서까지 고정, 금지 이름 12종 스캔 |
| TC-T001-13 | `detailAvailable` ⟺ 상세 서비스 응답 가능 | **△** | `BizTalkApiRegistryTest$LinkMatchesLookup` **○**(도메인), 프론트 링크 **○**. **요청 수준 E2E 형태는 ✕**(T2-LOG §4 도 carried 로 기록) |
| **TC-T001-14** | 엔드포인트 인벤토리 — **전체 route table 대상** | **△ 매우 부분적** | `TalkHistoryContractTest$EndpointSurface` 는 `TalkHistoryController.class` **한 클래스만** 리플렉션한다. 계획서가 명시한 "asserted against the **full route table**, not a list of known routes" 를 만족하지 않는다. 실제로 `TalkDetailController` 는 이 검사에 걸리지 않는다 |
| TC-T001-15 | 범위 밖 값은 거부가 아니라 무시(TM-T10) | **○** | `TalkHistoryServiceTest$CriteriaAssembly` |
| TC-T002-01…07 | 상세 필터·분류·페이징 | **△** | `TalkDetailServiceTest` 15건이 도메인 계층에서 **○**. DB 계층은 **▲** |
| TC-T003-01 | 타 기관 메시지 키 → 404, **50키 열거** | **△** | `TalkDetailServiceTest` + `TalkMessageMapperIntegrationTest#crossInstitutionKey…`(**▲**). **50키 열거는 ✕** |
| TC-T003-02…06 | D-T18/17/19/20/7 | **▲ 전부** | 5건 모두 `TalkMessageMapperIntegrationTest` 소속 — 미실행 |
| TC-T003-07 / 08 | 상세 편집 불가 / 팝업 제목 | **△** | 컴포넌트 존재. 두 상세 패널에 대한 프론트 테스트 파일 없음(§8) |
| TC-T004-01 / 02 | 내보내기 ↔ 목록 집합 동일성, 모든 필터가 파일을 바꿈 | **○** | `TalkExportParityTest$SetEquality` 4건 — 9개 필터 조합 |
| TC-T004-03 | 모든 내보내기 입력이 길이·문자 규칙 위반 시 거부 | **△** | 일자만. 일련번호·상태·API 코드에 대한 길이/문자 규칙 시험 없음 |
| **TC-T004-04** | **CR/LF·제어문자가 검증에서 거부** (일자·일련번호 퍼징) | **✕ 실질적으로 없음** | §8 항목 W-1 참조 — 존재하는 시험이 검증기를 태우지 않는다 |
| TC-T004-05 / 06 / 08 / 10 | 콘텐츠 타입 1개 / 행 상한 / 감사 행수 / 워크북 기하 | **○** | `TalkExportControllerTest`, `TalkExportParityTest$CeilingAndAudit`·`$WorkbookContent` |
| TC-T004-07 | 서버 실패 시 UI 에 가시적 오류 | **○** | `TalkHistoryPage.test.tsx` "내보내기 실패가 화면에 보인다" |
| **TC-T004-09** | 계약 ID 불일치 시 **빌드 실패** | **✕** | 그런 검사 없음 |
| **TC-PII-01** | 다섯 엔드포인트 응답 직렬화 본문에 11자리 미마스킹 번호 없음(정규식) | **✕** | 이름 기반 검사(`TalkHistoryContractTest`)만. **값 기반 정규식 검사 없음** |
| **TC-PII-02** | **생성된 워크북 파일을 읽어** 미마스킹 번호 없음 확인 | **△(사실상 공허)** | `TalkExportService.HEADERS` 는 9열 = 일자·기관코드·기관명·거래고유번호·API·상태·응답코드·등록시각·완료시각 — **전화번호 열이 애초에 없다**. 구조적으로 안전하지만 계획이 요구한 "파일을 읽어 확인" 은 없음 |
| **TC-PII-03** | DEBUG 로그에 미마스킹 번호 없음 | **✕** | |
| **TC-PII-04** | 매퍼 XML 정적 검사 — `masking()` 이 **최외곽 프로젝션**에 | **✕** | `TalkMessageMapper.xml` 에 대한 XML 스캔 테스트가 **존재하지 않는다**. `TalkHistoryMapper.xml` 만 `TalkHistoryMapperXmlTest` 가 검사하는데 그 매퍼에는 PII 가 없다. **마스킹 배치는 오직 ERROR 난 통합 테스트에만 걸려 있다** |
| **TC-PII-05** | 마스킹이 검색을 깨지 않음 | **▲** | `TalkMessageMapperIntegrationTest#…검색은 실제 번호로 동작한다` — 미실행 |
| TC-REG-01 / 03 / 04 | 양방향 대조 · 건수 · 범위 밖 제외 | **△** | `TalkApiReconciliationTest` 7건 **○**(mock). TC-REG-03/04 의 실데이터 형태는 **▲** |
| TC-LOAD-01…04 | §5.3 참조 | **△** | 04 만 ○ |
| E2E-1…E2E-5 | E2E TOP 5 | **✕ 5/5 전무** | |

### 7.4 갭 집계

| 구분 | 계획 항목 | ✕ 전무 | ▲ 미실행 | △ 부분 | ○ 충족 |
|---|---:|---:|---:|---:|---:|
| 보고서 — 보안 S-R | 16 | **16** | 0 | 0 | 0 |
| 보고서 — 출처 장애 F-R | 7 | **5** | 0 | 0 | 2 |
| 보고서 — 워터마크 W-R | 7 | 0 | 0 | 2 | 5 |
| 보고서 — 부하 L-R | 6 | **6** | 0 | 0 | 0 |
| 보고서 — 병합 속성 P-1…P-7 | 7 | 0 | 0 | 1 | 6 |
| 보고서 — E2E E-R | 5 | **5** | 0 | 0 | 0 |
| 톡 — 회귀 TC-T00x | 33 | **3** | 8 | 11 | 11 |
| 톡 — PII TC-PII | 5 | **3** | 1 | 1 | 0 |
| 톡 — 레지스트리 TC-REG | 3 | 0 | 0 | 2 | 1 |
| 톡 — 부하 TC-LOAD | 4 | 0 | 0 | 3 | 1 |
| 톡 — E2E | 5 | **5** | 0 | 0 | 0 |
| **합계** | **98** | **43** | **9** | **20** | **26** |

**계획된 98개 시험 항목 중 43개가 존재하지 않고, 9개는 존재하나 이번 실행에서 미실행,
20개는 부분 충족이다. 완전 충족은 26개(26.5%).**

가장 무거운 덩어리 셋: **보안 부정경로 16건 전무**, **E2E 10건 전무**, **부하 6건 전무**.
세 덩어리 모두 스프린트 R2/T3 로 이월된 항목이며, R2 는 아직 실행되지 않았다.

---

## 8. 통과했지만 약한 테스트 / Passed but weak

통과 여부와 주장의 강도는 다른 문제다. 아래는 **초록불이지만 지켜 준다고 믿기 어려운**
시험들이다. 심각도 순.

### W-1 — `TalkExportControllerTest#crlfNeverReachesAHeader` (D-T4 / TC-T004-04)

**결함을 잡을 수 없는 구조다.** 두 겹으로 무력화되어 있다.

```java
given(historyService.criteriaFor(any()))
        .willThrow(new PeriodPolicy.InvalidPeriodException("시작일자는 YYYYMMDD 8자리여야 합니다."));

MvcResult result = mvc.perform(get(EXPORT).param("from", rawFrom))
        .andExpect(status().isBadRequest())
```

1. **검증기가 태워지지 않는다.** `criteriaFor` 를 무조건 던지도록 스텁했으므로, 400 을
   만드는 것은 `TalkPeriodPolicy` 가 아니라 **mock 이다**. 누군가 `TalkPeriodPolicy` 를
   고쳐 `"20260801\r\nX"` 를 통과시켜도 **이 시험은 여전히 통과한다.** 이 시험이 실제로
   증명하는 것은 "criteriaFor 가 던지면 컨트롤러가 헤더를 만들지 않는다" 뿐이다.
2. **입력에 CR/LF 가 들어 있지도 않다.** `@ValueSource` 값은
   `"20260801%0d%0aX-Injected:+1"` 로 **퍼센트 인코딩된 리터럴**이고, MockMvc 의
   `.param()` 은 URL 디코딩을 하지 않는다. 검증기에 도달하는 값은 실제 CR/LF 가 아니라
   29자짜리 평범한 문자열이다.
3. **백업도 없다.** `TalkPeriodPolicyTest#malformedDatesRefused` 의 `@ValueSource` 는
   `{"00000000","99999999","20261332","2026819","notadate"}` — **CR/LF·제어문자가 하나도
   없다.** `PeriodPolicyTest` 도 마찬가지다.

**결론: TC-T004-04(및 보고서 쪽 S-R07)의 "CR/LF 는 검증 단계에서 거부된다" 는 주장은
어느 테스트도 증명하지 않는다.** T2-LOG Addendum B2 가 "A CR/LF-bearing `from` is rejected
before a header exists at all: two layers stop it" 이라고 적은 것은 **코드 리뷰의 결론이지
실행된 단언이 아니다.**

**수정**: `TalkPeriodPolicyTest` 에 `"20260801\r\nX"`, `"2026080 1"`, `"20260801\n"` 을
`@ValueSource` 에 추가하고, `crlfNeverReachesAHeader` 에서 `criteriaFor` 스텁을 제거해
실제 검증기를 태울 것. 두 파일 합쳐 10줄.

### W-2 — `TalkHistoryLoadTest` 의 두 성능 단언 (NFR-PERF-T01, NFR-SCALE-T01)

```java
assertThat(last).isLessThan(Math.max(50L * Math.max(first, 1L), 50L));       // 깊은 오프셋
assertThat(large).isLessThan(Math.max(25L * Math.max(small, 1L), 64L*1024*1024)); // 힙
```

- 오프셋 단언: 임계 **50배**, 실측 **1.19배**. 42배의 여유.
- 힙 단언: 임계 **25배**, 실측 **3.55배**(어제는 0.77배). 두 값 모두 통과.

**두 단언 모두 회귀를 잡을 수 있는 대역 밖에 있다.** 깊은 오프셋 비용이 지금의 20배로
나빠져도 통과하고, 힙이 7배 늘어도 통과한다. 임계값을 넉넉히 잡은 **의도는 옳다**(다른
장비에서 깨지지 않아야 한다). 그러나 지금 폭이면 "스트리밍이 완전히 사라졌다" 급의 사고만
잡는다. 실측 3.55 를 기준으로 **10배** 정도로 좁히면 하드웨어 이식성을 유지하면서 의미 있는
회귀를 잡는다.

부수 결함: `heapForExport` 안의 `assertThat(source).isEmpty();` 는 **바로 위에서 `new
ArrayList<>()` 로 만들고 한 번도 건드리지 않은 리스트**를 검사한다. 항상 참인 공허한 단언이다.

### W-3 — `TalkHistoryAuthorizationTest` 의 익명 거부 2건

```java
mvc.perform(get(LIST).param("from","20260819")).andExpect(status().is4xxClientError());
```

TEST-PLAN-TALK §3.2 TC-T001-01 은 **401** 을 요구한다. `is4xxClientError()` 는 401·403·404·
400 을 모두 통과시킨다. 인증 필터가 사라지고 인가 필터만 남아 403 이 되어도, 라우팅이 깨져
404 가 되어도 초록불이다. `status().isUnauthorized()` 로 좁히면 된다.

(같은 클래스의 tenant 403 시험 2건은 `isForbidden()` 으로 정확히 좁혀져 있다 — 대비된다.)

### W-4 — `TalkExportParityTest$SetEquality` — 강한 테스트인데 이름이 과하다

이 클래스는 이번 대상 중 **가장 잘 설계된 시험 중 하나**다. 가짜 매퍼가 조건을 실제로
해석하고(고정 목록을 돌려주지 않는다), 집합 동일성만으로는 "둘 다 필터를 무시" 를 통과시킬
수 있음을 알고 `everyFilterChangesTheFile` 을 따로 둔다. 이 자각은 드물다.

다만 주장의 범위를 정확히 해 둘 필요가 있다. `listed()` 와 `exported()` 는 **둘 다**
`historyService.criteriaFor(request)` 를 거쳐 **같은 `mapper.findPage`** 를 호출한다. 즉 집합
동일성은 **현재 구조에서는 정의상 성립**한다. 이 시험이 지키는 것은 "지금 다르다" 가 아니라
**"앞으로 내보내기에 별도 경로를 만들면 즉시 깨진다"** 는 아키텍처 잠금(lock)이다. 그것은
D-T1 재발 방지로서 정확히 옳은 목적이지만, **레거시 대비 parity 를 증명하지는 않는다.**
클래스 이름이 `Parity` 라 그 오해를 부른다.

### W-5 — `ReportServiceTest` 의 mock 깊이 (16건 전부)

`ApiAggregateMapper` 와 `BulkAggregateMapper` 가 **모두 Mockito mock** 이다. 클래스 Javadoc 이
그 한계를 정직하게 적어 두었다("Docker 가 금지되어 실제 SQL 실행은 여기서 검증되지 않는다").

문제는 **그 Javadoc 이 이미 반증된 전제를 인용하고 있다**는 점이다. SPRINT-T1 Addendum A1 이
"RISK-T13 의 전제가 거짓 — embedded-postgres 는 Docker 없이 뜬다" 를 확인했고, 회고 액션 A9 가
"RISK-R01 과 RISK-S13 이 같은 추론을 담고 있어 재검토 중" 이라고 적었다. **재검토는 코드에
반영되지 않았다** — `src/test/java` 에서 `EmbeddedPostgres` 를 쓰는 파일 4개는 **전부 톡
슬라이스**이고, 보고서 매퍼(`ApiAggregateMapper.xml`, `bulk/BulkAggregateMapper.xml`)에 대한
실행 검증은 **하나도 없다.**

이것이 특히 아픈 이유: **R1-LOG §4.1 은 keyset seek 술어가 틀렸었다고 기록하고, §4.3a 는
`javaType="long"` 오류와 silent-success 오류 두 건이 "실행해 보고서야 드러났다" 고
기록한다.** 같은 슬라이스의 같은 로그가 "이 경계에서 결함이 나온다" 를 세 번 증명했는데,
그 경계를 태울 수 있게 된 뒤에도 아무도 돌아가지 않았다.

### W-6 — `TalkHistoryContractTest$EndpointSurface#onlyTwoReadEndpoints`

`TalkHistoryController.class` 하나만 리플렉션하고 "엔드포인트가 정확히 2개" 를 단언한다.
계획서 TC-T001-14 는 **"the full route table, not a list of known routes"** 를 명시했다.
지금 형태는 정확히 그 "list of known routes"(길이 1) 다. `TalkDetailController` 는 이
검사망 밖에 있고, 실제로 그 컨트롤러는 인가 테스트도 없다(§4.2). **검사가 놓친 곳과 결함이
있는 곳이 일치한다** — 이 검사 형태의 한계를 그대로 보여 준다.

### W-7 — `AggregateMapperXmlTest` / `TalkHistoryMapperXmlTest` — tier 4 의 구조적 한계

두 클래스 합쳐 22건이 통과한다. 이들은 **매퍼 XML 을 문자열로 읽어 정규식·부분문자열로
검사**한다. R1-LOG §4.3a 가 스스로 적었듯 "tier-4 substitute for the integration test" 이고
그 사실을 명시한 점은 정직하다.

그럼에도 남는 한계를 기록해 둔다: 이 방식은 **SQL 이 문법적으로 유효한지, 실행되는지,
무엇을 반환하는지에 대해 아무것도 말하지 않는다.** R1 이 겪은 `javaType="long"` 결함이 정확히
그 형태였다 — XML 은 완벽히 잘 생겼고, MyBatis 가 생성자를 못 찾아 런타임에 터졌다.
`AggregateMapperXmlTest#ConstructorTypes`(1건)가 그 특정 별칭을 이제 고정하지만, **다음
번의 같은 종류 결함은 잡지 못한다.**

### W-8 — 두 상세 패널에 프론트엔드 테스트 파일이 없다

`TalkTransactionDetailPanel`, `TalkMessageDetailPanel` 은 T2 에서 신설됐고 D-T21(`maxLength`),
D-T30(서버 총건수), D-T33(`readOnly`)의 가드로 T2-LOG 에 인용된다. 그러나
`src/main/frontend/src/features/biztalk/` 에 두 패널의 `.test.tsx` 파일이 없다. 인용된 가드는
**컴포넌트 코드 자체**이지 테스트가 아니다. `TalkHistoryPage.test.tsx` 가 링크 열림까지는
단언하지만(`거래고유번호 링크가 거래 상세내역을 연다`), 패널 내부의 읽기전용·자릿수·
페이징 표시는 단언되지 않는다.

---

## 9. 잘 되어 있는 것 — 기록해 둘 가치가 있는 것

비판만 쌓으면 다음 사이클이 무엇을 유지해야 하는지 모른다.

1. **`SourceMergerTest$Paging` 은 이 저장소에서 가장 좋은 시험이다.** 키 공간(80)보다 두
   출처의 합(115)이 크도록 데이터를 만들어 **공유 키 위에 페이지 경계가 떨어지도록 강제**
   한다. R1-LOG §4.1 의 잘못된 seek 술어를 잡은 것이 리뷰가 아니라 이 시험이었다. 그리고
   §4.2 의 "멈추는 시험" 문제를 거절 표집 → 전체 공간 셔플로 고치면서 **요청 수가 공간보다
   크면 던지도록** 만들었다 — 실패보다 멈춤이 나쁘다는 판단이 코드에 남아 있다.
2. **`LpadTruncationTest#ourRuleIsLossless` 의 boolean 읽기.** PostgreSQL 의 boolean 이
   텍스트로 `t`/`f` 라는 이유로 `getString` 대신 `getBoolean` 을 쓰고, 그 이유를 주석에
   적었다 — "이 시험이 존재하는 이유가 실제 DB 는 우리 가정과 다르다는 것이므로 여기서
   문자열을 가정하면 자기 교훈을 무시하는 것" 이라고. (이번 실행에서 미실행이지만 코드는
   옳다.)
3. **`TalkHistoryMapperIntegrationTest` 의 9컬럼 스키마.** 25컬럼 중 9개만 만들어 계좌·카드
   컬럼을 **물리적으로 부재**하게 한다. 프로젝션이 그것을 건드리는 순간 질의가 실패한다 —
   CONST-SEC-T01 을 리뷰가 아니라 실행이 강제한다. 설계로서 우수하다.
4. **`TalkHistoryLoadTest` 가 밀리초를 단언하지 않는 판단.** §5.2 가 보여 주듯 그 값은
   실행마다 8~18배 움직인다. 단언했다면 지금 그 테스트는 빨간불이었을 것이고, 아무도
   재현할 수 없는 실패를 쫓았을 것이다.
5. **`TalkExportParityTest` 의 자기 인식** — 집합 동일성만으로는 "둘 다 무시" 를 통과시킨다는
   것을 알고 `everyFilterChangesTheFile` 을 별도로 둔 것.
6. **`TalkHistoryContractTest` 의 순서 고정** — 필드 **순서까지** 고정해 컬럼 추가가 어디에
   끼어들든 실패하게 만든 것.

---

## 10. 미실행 / Not run

| 종류 | 상태 | 원인 |
|------|------|------|
| `mvn verify` (JaCoCo `check` 게이트) | ❌ | 이번 사이클은 `test` 만. **게이트가 강제된 적 없음** |
| 매퍼↔DB 통합 33건 | ❌ | §3 — embedded-postgres 기동 타임아웃 |
| 보고서 슬라이스 DB 통합 | ❌ **영구 부재** | 그런 테스트가 작성된 적 없음 (§8 W-5) |
| 부정경로 보안 S-R01…S-R16 | ❌ | 미작성 (R1-12 이월, R2 미실행) |
| E2E (E-R1…5, E2E-1…5) | ❌ | 미작성 |
| 2배 SLA 부하 | ❌ | 미작성 — 부하 테스트가 단일 스레드 |
| NFR-PERF-T02 상세 P95 | ❌ | 상세 질의가 계측되지 않음 (§5.3) |
| 접근성 axe — 보고서/톡 화면 | ❌ | 미작성 (§6) |
| SBOM / 의존성 CVE 스캔 | ❌ | `cyclonedx-maven-plugin` 은 pom 에 있으나 미실행 |
| 바이트 Parity | — | **해당 없음** (재구축, intent parity) |
| **JaCoCo 슬라이스별 정확 커버리지** | ⚠ **측정 불가** | BUNDLE 단위 규칙만 있어 R1/T1/T2 만의 line/branch 를 게이트 기준으로 판정할 수 없다. §4.2 의 클래스별 값이 얻을 수 있는 최대 해상도 |

---

## 11. 권고 — 우선순위

| # | 항목 | 근거 | 비용 |
|---|------|------|------|
| **1** | `EmbeddedPostgres` 대기창 90초로 확대 + 4개 클래스가 인스턴스 공유 | §3. 지금 **빌드가 깨져 있고**, 33건의 실행 증거가 사라져 있다 | 1시간 |
| **2** | `ReportController` `@WebMvcTest` 신설 — 최소 S-R01/S-R02/S-R04 | §4.2. **T-R10(CVSS≈9.1)의 게이트 시험이 없다.** DB 불필요가 이미 증명됨 | 3시간 |
| **3** | `TalkDetailController` 인가 테스트 신설 | §4.2. 다섯 문 중 두 개가 잠금 단언 없음 | 2시간 |
| **4** | CR/LF 를 **검증기에** 태우는 시험 (W-1) | TC-T004-04 / S-R07 의 주장이 현재 무근거 | 30분 |
| **5** | `TalkMessageMapper.xml` 마스킹 배치 XML 스캔 (TC-PII-04) | 마스킹 배치의 유일한 가드가 ERROR 난 통합 테스트뿐 | 1시간 |
| **6** | 보고서 매퍼 embedded-postgres 통합 테스트 | §8 W-5. 같은 슬라이스가 이 경계에서 결함 3건을 이미 냈다 | 1일 |
| **7** | `ReportPage`/`TalkHistoryPage`/상세 패널 2종 axe 검사 | §6. 기존 `accessibility.test.tsx` 틀 재사용 | 2시간 |
| **8** | surefire `excludedGroups=load` 설정 (또는 T2 로그의 주장 철회) | §5.1. 빌드 시간의 66%가 부하 테스트 | 15분 |
| **9** | 추적표 `NFR-PERF-T02` 를 `PARTIAL-DEV-HARDWARE` → `NOT-MEASURED` 로 정정 | §5.3. 상세 질의가 계측된 적 없음 | 5분 |
| **10** | 힙·오프셋 단언 임계 25×/50× → 10× 로 축소 | §8 W-2. 현재 회귀 대역 밖 | 15분 |

---

## 12. 게이트 판정

| 판정 항목 | 결과 |
|---|---|
| 빌드 통과 | **❌ FAIL** — 3 errors + 1 pre-existing failure |
| 대상 21종 통과율 | 224 / 227 (98.7%) — **단, 3개 클래스가 통째로 미실행** |
| 커버리지 게이트 (line ≥ 80 / branch ≥ 70) | **❌ FAIL** — BUNDLE 67.5% / 64.4% (게이트 자체는 미강제) |
| 결함 회귀 커버리지 | **⚠ 부분** — 계획 98항목 중 완전 충족 26 (26.5%) |
| CVSS ≥ 7.0 미결 결함 | **⚠ 검증 불가** — T-R10(CVSS≈9.1) 의 게이트 시험(S-R04)이 존재하지 않아 **닫혔다고 말할 근거가 없다** |
| 접근성 WCAG 2.1 AA | **❌ 미달** — 대상 화면 axe 검사 부재 |
| 부하 NFR-PERF SLA 2배 | **❌ 미실행** |

**G3 릴리즈 게이트 진입 권고: 보류.** 차단 사유는 두 가지이며 둘 다 코드 품질이 아니라
**증거의 부재**다 — (a) 빌드가 깨진 상태이고 매퍼↔DB 증거 33건이 사라져 있다, (b) 프로그램
최고 심각도 위협(T-R10, CVSS≈9.1)의 게이트 시험이 작성된 적이 없다.

권고 1~4 를 처리하면 (a)와 (b)의 핵심이 닫힌다. 추정 1.5일.

---

## 부록 A — 이 리포트가 스프린트 로그와 다르게 말하는 것

스프린트 로그들은 이 프로그램에서 드물게 정직한 문서다. 자기 오류를 세 번 기록하고
(RISK-T13 전제, ADR-TLK-027 §1.1, sargability 주장), 점수를 부풀리지 않았다. 아래는
**틀렸다**는 지적이 아니라, 1회 측정에 기댄 서술이 재현되지 않았다는 기록이다.

| 로그 서술 | 이번 실측 |
|---|---|
| T1 §1, T2 §1: "기존 실패 2건" | **1건.** `ExceptionHandlerOrderTest` 는 통과한다 |
| T2 B1: "`@Tag("load")`, excluded from the default cycle" | **제외되지 않는다.** surefire 설정 부재. 761.6초 실행됨 |
| T2 B1: "export heap … ratio **0.77** … it is **unambiguous**" | **3.55.** 결론(선형 아님)은 유지되나 수치는 잡음 |
| T2 B1: "dev figures have **headroom** against the 3 s and 1 s targets" | 첫 페이지 2,514 ms, **건수 질의 4,235 ms**. 이번 실행엔 여유 없음 |
| T1 A3, T2 §3.2: D-T5/6/9/17/18/19 "verified by execution" | **이번 실행에서는 미실행.** 해당 클래스 전부 ERROR |
| T1 A9: "RISK-R01 … being re-examined" | 재검토가 코드에 반영되지 않음. 보고서 매퍼 실행 검증 **0건** |
| 추적표: `NFR-PERF-T02 … PARTIAL-DEV-HARDWARE` | 상세 질의가 **한 번도 계측되지 않음**. `NOT-MEASURED` 가 정확 |

---

*작성: `qa-engineer` · 원본 로그: `qa/mvn-test-R1T1T2.log`, `target/site/jacoco/jacoco.csv`*
