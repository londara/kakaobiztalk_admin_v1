# QA 테스트 리포트 — Sprint S2a (발신번호 등록 · 삭제)

> **QA**: qa-engineer · **Skill**: 05 §1[A] · **Date**: 2026-08-21
> **Tree**: `main` @ `34b4254` + 미커밋 `BarredNumbers.java`
> **판정**: **CONDITIONAL** — 빌드 RED, 커버리지 게이트 4건 미달, 부하 0/4 스크립트

---

## 1. 요약

| 항목 | 결과 |
|------|------|
| 백엔드 | **999 tests / 1 failure** → `BUILD FAILURE` |
| 프론트엔드 | **218 tests / 218 passed** (16 files, 49.75s) |
| 커버리지 | LINE **70%** / BRANCH **66%** — 게이트 4건 전부 미달 |
| E2E | **인프라 자체가 없음** |
| 부하 | 발신번호 시나리오 **4개 중 0개** 스크립트 존재 |
| 실행 환경 | JDK 17.0.20 (Microsoft), Maven 3.9.16, Node 24.14.1, npm 11.11.0 |

스프린트 로그의 정량 주장 검증 결과: **테스트 존재 여부는 대체로 정확(19개 중 17개 이름 일치), 개수 주장은 6건 중 4건 오류.**

---

## 2. 커버리지

### 2.1 실측 — 프로그램 최초

`jacoco:report`/`check` 는 **`verify` 단계에 바인딩**되어 있어 `mvn test` 로는 실행되지 않는다. `verdict-sprint-R1-T1-T2.md` V-4 가 *"게이트 자체가 `mvn verify` 미실행으로 한 번도 강제된 적 없음"* 이라 기록한 이유다. 본 리포트에서 `mvn verify -Dmaven.test.failure.ignore=true` 로 **처음 측정**했다.

| 규칙 | 요소 | 실측 | 기준 | 판정 |
|------|------|-----:|-----:|:---:|
| BUNDLE | LINE | **70%** | 80% | **FAIL** (−10pt) |
| BUNDLE | BRANCH | **66%** | 70% | **FAIL** (−4pt) |
| PACKAGE `com.webcash.iris.auth.domain` | LINE | **44%** | 95% | **FAIL** (−51pt) |
| PACKAGE `com.webcash.iris.auth.crypto` | LINE | **70%** | 95% | **FAIL** (−25pt) |

```
[WARNING] Rule violated for bundle iris-portal: lines covered ratio is 0.70, but expected minimum is 0.80
[WARNING] Rule violated for bundle iris-portal: branches covered ratio is 0.66, but expected minimum is 0.70
[WARNING] Rule violated for package com.webcash.iris.auth.domain: lines covered ratio is 0.44, but expected minimum is 0.95
[WARNING] Rule violated for package com.webcash.iris.auth.crypto: lines covered ratio is 0.70, but expected minimum is 0.95
[ERROR] Coverage checks have not been met.
```

`auth.domain` **44%** 가 가장 심각하다 — 95% 기준이 걸린 이유가 보안 핵심 패키지이기 때문이다. R1 회차 측정치(LINE 67.5% / BRANCH 64.4%)와 비교하면 소폭 개선(+2.5 / +1.8)이나 여전히 게이트 미달이다.

> 주의: `-Dmaven.test.failure.ignore=true` 로 CSRF 실패를 무시해 `verify` 가 jacoco 까지 도달하게 했다. 커버리지 수치는 실행된 테스트 기준이므로 유효하다.

### 2.2 커버리지의 빈 축 — 컨텍스트 로드

**저장소의 어떤 테스트도 Spring 애플리케이션 컨텍스트를 로드하지 않는다.**

- `grep -rn "@SpringBootTest" src/test/java` → **1건, 그것도 Javadoc 문단 안** (`CsrfIntegrationTest.java:45`, 전체 컨텍스트를 *쓰지 않는 이유* 설명)
- `grep -rn "ApplicationContextRunner" src/test/java` → **0건**
- 71개 테스트 클래스, 컨텍스트 로드 0건

이 공백이 만든 결과가 이번 스프린트의 확인된 탈출이다. `BarredNumbers` 가 생성자 2개 + `@Autowired` 없는 `@Component` 여서 `No default constructor found` 로 **애플리케이션 전체가 기동하지 못했는데**, 999개 테스트는 그린이었고 7차원은 91.4였다.

**탈출을 세 배로 심각하게 만드는 사실:**

**(a) 수정이 테스트를 하나도 건드리지 않는다.** `BarredNumbers.java` 의 diff 는 **+2줄**(import + `@Autowired`)이다. 999개 중 어느 것도 바뀌지 않았고, 어노테이션을 다시 지워도 **실패하는 테스트가 없다.** 결함은 오늘 신호 없이 재도입 가능하다.

**(b) 테스트 계획이 바로 이 빈에 대해 기동 수준 시험을 명시적으로 요구했고, 조용히 단위 수준으로 구현되었다.** `docs/design/TEST-PLAN-SENDERNO.md:222` 는 CONST-BIZ-D03 을 TC-S002-29/30 에 "**Startup + unit**" 수준으로 매핑하고, `:234` 는 못을 박는다 — *"**TC-S002-29 는 애플리케이션이 기동을 거부함을 단언한다.** 기대 결과가 부트 실패인 시험은 대안을 명명하기 전까지 이상해 보인다…"*. 실제 구현 `BarredNumbersTest.java:140` 은 `assertThatThrownBy(() -> new BarredNumbers(new DefaultResourceLoader(), "classpath:senderno/does-not-exist.txt"))` — 직접 생성자 호출이다. **계획된 시험 수준이 선언 없이 강등되었고, 로그는 해당 케이스를 완료로 계상했다.** TC-S002-29 가 명세대로 구현되었다면 `BarredNumbers` 를 포함한 컨텍스트를 부팅해 즉시 실패했을 것이다 — **계획된 시험이 정확히 부트를 깨뜨린 그 빈을 겨누고 있었다.**

**(c) 이미 문서화된 탈출의 반복이며, 교훈이 기록된 뒤 적용되지 않았다.** `AlimTalkDispatchConfigTest.java:29-36` 자신의 Javadoc: *"배선을 테스트하는 이유는 이 슬라이스에서 이미 한 번 데었기 때문이다. `TranIdGenerator` 를 인터페이스 뒤로 미루면서 `@Bean` 을 남기지 않아 **애플리케이션이 기동에 실패했고**, 단위 테스트는 생성자를 직접 부르므로 전부 통과했다. **실행되지 않는 것은 검증되지 않는다.**"* 동일 실패 양식, 동일 원인, 한 슬라이스 앞. 당시 채택된 완화는 좁았다 — 한 config 클래스의 어노테이션에 대한 리플렉션(`:90-97`) — 이며 다른 어떤 빈의 배선 실패도 탐지할 수 없다. 체계적 수정은 끝내 취해지지 않았다.

환경적 기여 요인도 있다: `qa/load/message-history-load.js:10-14` 가 *"애플리케이션을 기동할 수 없다"* 고 기록한다. 즉 AlimTalk 탈출과 이번 탈출 사이에 CI 도, 수동 경로도 부트를 한 번도 실행하지 않았다.

### 2.3 최소 시험 설계 — 2단계

**Tier A — 인프라 0, 이 결함 부류를 오늘 잡는다.** 신규 `src/test/java/com/webcash/iris/biztalk/domain/BarredNumbersWiringTest.java`:

```java
new ApplicationContextRunner()
    .withUserConfiguration(BarredNumbers.class)
    .run(ctx -> assertThat(ctx).hasSingleBean(BarredNumbers.class));
```

`ApplicationContextRunner` 는 실제 생성자 선택과 실제 `@Value` 해석을 수행하므로 `spring-boot:run` 과 **동일하게** `No default constructor found` 로 실패한다 — DB·웹서버 없이 밀리초 단위로. 두 번째 `run(...)` 에 `.withPropertyValues("biztalk.senderno.barred-numbers=classpath:does-not-exist.txt")` 를 주고 `assertThat(ctx).hasFailed()` 를 단언하면 **그것이 계획이 실제로 명세한 TC-S002-29** 다. **저장소에서 빠진 가장 가치 높은 시험이며 약 15줄이다.**

**Tier B — 진짜 컨텍스트 스모크, 모든 빈에 대해 부류 전체를 잡는다.** 신규 `src/test/java/com/webcash/iris/ApplicationContextSmokeTest.java`:

```java
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@ActiveProfiles("test")
class ApplicationContextSmokeTest {
    @Test void contextLoads() { }
}
```

DataSource 반론은 저장소 안에서 이미 해결되어 있다. `pom.xml:119-124` 가 `io.zonky.test:embedded-postgres` 2.0.7 을 test scope 로 선언하고, `SenderNumberMapperIntegrationTest:8` 과 `TalkHistoryLoadTest:12` 가 이미 `@BeforeAll` 에서 `EmbeddedPostgres` 를 띄운다. static holder + `@DynamicPropertySource` 로 연결:

```java
static EmbeddedPostgres pg;
@BeforeAll static void start() throws IOException { pg = EmbeddedPostgres.start(); }
@DynamicPropertySource static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", () -> pg.getJdbcUrl("postgres", "postgres"));
    r.add("spring.datasource.username", () -> "postgres");
    r.add("spring.datasource.password", () -> "");
}
```

`src/test/resources/application-test.yml`(현재 `src/test/resources/` 에는 `contracts/imo/` 뿐)에 합성 32바이트 키와 datasource 자리표시자를 넣어야 한다. 이 요구 자체가 진단적이다 — `spring.datasource.*` 는 설계상 기본값이 없어야 하고(SEC-A02), `iris.auth.otp.secret-key` 는 던지는 생성자에 도달한다.

**권고: 둘 다 추가.** Tier A 는 무조건·전 빌드 포함. Tier B 는 `TalkHistoryLoadTest` 처럼 `@Tag` 게이트.

### 2.4 같은 공백 뒤에 숨은 다른 배선 결함 — 3건

생성자 2개 형태(이미 스캔 완료, `BarredNumbers` 유일)를 제외한 **다른 형태**로 확인된 잠재 부트 실패:

| 빈 | 형태 | 위치 | 위험 |
|---|---|---|---|
| `SecretCipher` | `@Component`, 기본값 없는 `@Value` + 생성자가 `IllegalStateException` **2회** 던짐 (Base64 오류, 키 길이 오류) | `auth/crypto/SecretCipher.java:64-79` | **높음.** 자기 Javadoc: *"형식이 맞지 않으면 기동 시점에 실패한다"*. `IRIS_OTP_SECRET_KEY` 미설정·오형식 배포는 빈 생성에서 사망 |
| `IpAllowlistPolicy` | `@Component`, 생성자가 설정 검증 후 `enabled=true` + 빈 CIDR 목록이면 던짐 | `auth/domain/IpAllowlistPolicy.java:55-72` | **높음, 설정 조건부.** `application.yml:262` 가 블록을 정의; CIDR 없이 켜면 설계상 부트 실패, 컨텍스트 시험 없음 |
| `AlimTalkDispatchConfig#alimTalkVendorClient` | `@Bean` + 기본값 없는 `@Value`, `@ConditionalOnProperty` 가드 | `alimtalk/config/AlimTalkDispatchConfig.java:110` | **중간.** `application.yml:180` 이 `${IRIS_ATK_VENDOR_BASE_URL:}` — **빈** 기본값이므로 실패가 아니라 빈 URL 로 기동. 부트 실패보다 조용한 오설정 위험. 기존 시험은 어노테이션만 리플렉션 |

형태 목록에 있었으나 해제: `@ConfigurationProperties` 2건(`ReportDataSourceConfig:74`, `TalkHistoryConfig:88`) — `@Validated` 가 저장소 전체에 **0건**이므로 바인드 시점 검증이 실패할 수 없다. `@PostConstruct` 1건(`OtpDevBypass:82`)은 `OtpDevBypassTest` 로 커버됨. `TemplateRegistry`·`AtkGenerator` 생성자는 작업하지 않음.

**결론: 3건이 이 공백 뒤에 있고, 3건 모두 동일한 Tier-B 시험 하나로 소멸한다.**

> `SecretCipher` 관련: `application.yml:235` 의 fallback 기본값이 현재 이 부트 실패를 **가리고 있다**. 그 기본값 자체가 CRITICAL 보안 결함이다 — [security/audit-S2a.md](../security/audit-S2a.md) §3 SEC-S2a-03. **보안 수정(기본값 제거)이 이 잠재 부트 실패를 활성화하므로, Tier B 시험은 그 수정보다 먼저 들어가야 한다.**

---

## 3. 단위 테스트 결과 — 주장 검증

| # | 로그 주장 | 검증 | 증거 |
|---|-----------|:----:|------|
| 1 | 프론트 218 tests | **정확** | 16 files, 218 passed — 실행 확인 |
| 2 | 백엔드 999 tests | **정확** | `Tests run: 999, Failures: 1` — 실행 확인 |
| 3 | `BarredNumbersTest` 11 cases | **정확** | `@Test` 정확히 11 |
| 4 | …empty / comment-only / non-numeric / missing 포함 | **정확** | `:107`, `:118`, `:127`, `:140` — 넷 다 `IllegalStateException` 단언. **다만 "missing" 케이스가 §2.2 의 탈출 지점** |
| 5 | `runsArchiveBeforeDelete` 가 순서를 고정 | **정확 — 진짜로** | `SenderNumberWriteServiceTest:276` 실제 `Mockito.inOrder(mapper)` + 3연속 `verify` |
| 6 | `noLiveRowIsConflictNotSuccess` | **정확, 범위 유의** | `WriteAuthorizationTest:325` 가 `isConflict()` + `$.code == NOT_LIVE` 단언. **단 `@WebMvcTest` + 서비스 스텁 throw** — HTTP 매핑을 증명하고 서비스가 0행에서 던지는 것은 증명하지 않는다. 그 절반은 `WriteServiceTest:313`(목 매퍼)과 `MapperIntegrationTest:442`(실 DB)에 있다. 사슬은 덮이지만 **한 시험이 전 구간을 잇지는 않는다** — 그 하나를 "acceptance criterion" 이라 부르는 것은 과장 |
| 7 | `SenderNumberWriteServiceTest` **26 cases** | **오류 — 실제 23** | `@Test` 23 (Register 11 + Delete 9 + Authorization 3). **3 과대** |
| 8 | `$Register` 11 cases | **정확** | |
| 9 | `$Delete` **8 cases** | **오류 — 실제 9** | `archiveAndDeleteSucceed`(`:268`)가 `@BeforeEach` 이며 오산의 기원으로 보임. **1 과소** |
| 10 | `WriteAuthorizationTest` **18** | **오류 — 실제 19** | **1 과소** |
| 11 | S2a-13 "**66 backend** cases added" | **오류 — 실제 67** | 11+23+19+14=67. 로그의 66 은 자신의 틀린 부분합(11+23+18+14)과만 일관 — +3/−1 오차가 부분 상쇄 |
| 12 | `MapperIntegrationTest` 14 cases | **정확** | |
| 13 | "**21 frontend** cases added" | **총량 일관, 태스크별 불일치** | 197→218 = 21. 그러나 태스크별 합이 맞지 않음: S2a-10 주장 10(실제 10 ✓), S2a-11 주장 9(실제 **8** ✗), S2a-12 주장 4 → 10+8+4=22 ≠ 21 |
| 14 | `contextCarriesNoCredential` | **정확** | `WriteAuthorizationTest:248` |
| 15 | `auditCarriesNoNumber` 등 3건 | **정확** | `WriteServiceTest:240`, `:403`; `WriteAuthorizationTest:289` |
| 16 | D-S9 관련 3건 | **정확** | `MapperIntegrationTest:339`, `:352`; `WriteServiceTest:129` |
| 17 | D-S1 관련 4건 | **정확, 4건 전부** | `MapperIntegrationTest:442`, `:469`; `WriteAuthorizationTest:345`; `WriteServiceTest:313` — **스프린트의 가장 강한 부분이며 실재한다** |
| 18 | D-S11 `refusesNonNumeric`, `refusesBarredNumber` | **이름 오류** | 실제는 `rejectsNonNumeric`(`:141`), `rejectsBarredNumber`(`:152`). 전제는 단언됨 |
| 19 | SS2a-01 개선 `FR-SNDD-010` 3단 walk | **정확 — 진짜로** | `SenderNumberPage.test.tsx:158` 3단 walk + `queryByRole('수정')).not.toBeInTheDocument()`. 실재한 accidental-pass 결함의 좋은 개선 |

**요약: 이름 존재 19건 중 17건 정확(2건 개명), 단언 실질 표본 3건 전부 이름대로 단언. 정량 주장 6건 중 4건 오류(−3, +1, +1, +1).**

---

## 4. 통합 테스트 결과

`SenderNumberMapperIntegrationTest` 14 cases — 실제 PostgreSQL(`io.zonky.test:embedded-postgres`, Docker 불필요), 실제 매퍼 XML, V3 와 동일 문장으로 구축한 스키마. 전부 통과.

**§2.1 의 계획 이탈은 정당하다.** 계획은 I2a 선례(Docker 금지 → XML 정독)를 따랐고, 그 선례가 낡았다. 실 DB 통합 시험이 XML 정독이 할 수 있는 모든 형태 단언을 행위적으로 수행하므로 두 번째 파일은 증거를 더하지 않는다. 특히 `aMaskedValueMatchesNothingAndReportsZero` 는 레거시의 정확한 술어를 실제 DB 에 실행해 **예외 없이 0을 반환**함을 단언한다 — D-S1 의 전제를 서술이 아니라 검증으로.

**미해결**: `ENCRYPT` 의 실제 결정성(스파이크 S1-01), `masking` 의 실제 출력 형식. 시험은 동명 대역 함수를 쓴다. 시험 자기 헤더가 이를 명시한다.

### 4.1 다만 — 롤백은 주장되고 단언되지 않았다

DoD 는 *"[x] 강제된 이력 쓰기 실패가 등록·삭제 양쪽을 롤백한다"* 를 체크한다. **롤백을 단언하는 시험은 없다.** `grep -n "rollback\|Transactional"` 이 `SenderNumberWriteServiceTest`·`SenderNumberMapperIntegrationTest` 양쪽에서 0건. `historyFailureFailsTheRegistration`(`:198`)은 `insertHistory→0` 을 스텁하고 `IllegalStateException` 이 던져지는 것만 단언한다 — **목 매퍼에는 롤백할 상태가 존재하지 않는다.** `@Transactional`(`WriteService:107`, `:180`)은 정확하나 미실행 검증이다.

FR-SNDC-008 과 FR-SNDD-005 는 검증 열에 **"Integration test (강제 이력 실패 / 강제 루프 중단)"** 을 명시하는데, 추적표에서는 둘 다 단위 시험으로 충족 처리되어 있다. 임베디드 PostgreSQL 이 **같은 파일에서 이미 사용 중**이므로 이 대체는 Docker 제약에 의해 강요된 것이 아니다 — §2.1 의 대체와 달리 정당화되지 않는다.

**이것이 D-S6/D-S7 자신의 전제를 서술로 남긴 것이며, 그 반대를 자축한 스프린트(§2.1)에서 일어났다.**

---

## 5. E2E 테스트 결과 — **인프라 부재**

`playwright.config.*` 없음, `e2e/` 디렉터리 없음, 양쪽 `package.json` 에 `playwright` 의존성 없음.

그런데 TEST-PLAN §2 는 *"E2E | TOP 5 scenarios (§12) | Playwright against the real API"* 를 커버리지 목표로 올리고, §13.1 은 TC-S002-24/25/27/28 과 TC-S004-21…25 를 "E2E" 로 매핑한다. **9개 TC 행이 커버리지로 읽히며 미구현이다.**

FR-SNDD-009/010/011 은 `SenderNumberPage.test.tsx` 에 실제 컴포넌트 수준 커버리지가 있으므로 **덮여 있다** — 다만 계획이 기록한 것보다 **한 단계 아래**이며 그 강등이 선언되지 않았다. TC-S002-29 와 동일한 패턴이다: **선언되지 않은 시험 수준 강등을, 추적표에는 계획 수준으로 표기.** 이것이 탈출 뒤의 체계적 발견이며 일회성이 아니다.

---

## 6. 부하 테스트 — **발신번호 시나리오 0/4**

`qa/load/` 에는 정확히 두 파일이 있고 둘 다 선재하며 이 슬라이스용이 아니다: `login-load.js`(202줄), `message-history-load.js`(217줄). 둘 다 **SPECIFIED_NOT_RUN** 을 자기 선언한다. `src/test/java/.../perf/` 에는 `TalkHistoryLoadTest.java` 뿐.

즉 발신번호에 대해서는 로그의 "미측정" 보다 나쁘다 — **스크립트가 존재하지 않는다.** TEST-PLAN §8 은 4개 시나리오를 명세한다(100행 목록 P95<1s; 등록 P95<1s 전역 유일성 검사 포함; **100번호 삭제 <5s**; 읽기 감사 포함 목록 P95<1s) — **4개 중 0개 스크립트.**

**"환경이 실행할 수 없다" 는 방어는 NFR-PERF-D03 에는 성립하지 않는다.** `TalkHistoryLoadTest` 가 저장소 내 직접 선례다: 5 cases, `EmbeddedPostgres`, `EXPLAIN` 을 읽어 인덱스 사용을 증명하고 export heap 이 행수 독립임을 단언하며, 운영 SLA 등가성을 부인하는 신중한 헤더(`:38-46`)와 함께 **구조적** 속성을 확립한다. 100번호 삭제는 정확히 그 종류의 주장이다 — 한 트랜잭션 안 100회 × 3문장, 스케일링 거동은 하드웨어와 무관하게 성립하거나 실패한다 — 그리고 `SenderNumberMapperIntegrationTest` 는 **이미 V3 스키마에 임베디드 PG 를 띄운다.** 이미 존재하는 픽스처에 삭제 스케일링 케이스를 더하는 한계비용은 0 에 가까웠고, 두 스프린트 연속 쓰이지 않았다.

**릴리즈 게이트 판정: 미달.** TEST-PLAN §2 는 "Load | 2× NFR-PERF SLA | §8" 을 **커버리지 목표**로 올린다(선택 사항이 아니다). 수치 수락 기준을 가진 NFR 2건, 이제 실제 삭제를 수행하는 쓰기 경로, 재사용 가능한 하네스가 같은 테스트 트리에 있는 상태에서 4개 중 0개는 게이트 가능한 상태가 아니다. §9 가 부하를 Staging 에 배정한 것은 D-02 의 동시 P95 를 정당하게 이연하나, **staging 도 동시성도 필요 없는 D-03 을 이연하지 않는다.**

**최소 통과선**: `TalkHistoryLoadTest` 패턴으로 `src/test/java/.../perf/SenderNumberDeleteLoadTest` 추가(임베디드 PG 대상 100번호 삭제 측정) + D-02 용 `qa/load/senderno-load.js` 를 SPECIFIED_NOT_RUN 으로 작성. 한 NFR 을 미측정→구조적 측정, 다른 하나를 미작성→staging 대기로 전환한다.

---

## 7. 회귀 테스트

S2a-13 이 추가한 쓰기 경로 회귀·보안·공존 스위트: **백엔드 67 + 프론트 21 (로그 주장 66+21).** 전부 통과.

**D-S1 계열 회귀는 실재하며 예외적으로 강하다** (§3 #17). `aMaskedValueMatchesNothingAndReportsZero`, `aCommaJoinedListMatchesNothing`, `displayValueAsIdentifierIsRejected`, `failsWhenDeleteRemovesNothing` 네 건이 각각 다른 계층에서 D-S1 의 전제를 공격한다.

### 7.1 빌드 RED — `CsrfIntegrationTest.echoingCookieValueInHeaderPasses`

```
[ERROR] CsrfIntegrationTest.echoingCookieValueInHeaderPasses:174
  [an authenticated request must also receive an XSRF-TOKEN cookie;
   without one a logged-in SPA has no token to echo — see CR-02]
```

**로그의 "선재 실패, S2a 귀속 아님" 주장은 사실이다** — `verdict-sprint-R1-T1-T2.md` §2 가 근본 원인까지 규명해 두었다: `SecurityConfig:188` 이 `CsrfCookieFilter` 를 `addFilterAfter(..., CsrfFilter.class)` 로 등록하는데, `CsrfFilter` 는 토큰 없는 요청을 거부할 때 체인을 잇지 않으므로 **쿠키를 발급해야 할 바로 그 요청에서 발급 필터가 실행되지 않는다.** merge-base 워크트리에서 동일 실패 확인됨.

**그러나 그 verdict 자신이 못을 박았다: *"조치: 인증 슬라이스 백로그. 단, 빌드는 여전히 RED 이므로 G3 전에 닫혀야 한다."*** 그 지시가 이행되지 않은 채 또 한 스프린트가 지났다.

**QA 관점의 실질 피해는 귀속이 아니라 신호 소실이다.** 빌드가 항상 RED 이면 **새 실패가 기존 실패와 구별되지 않는다.** 999개 중 1개 실패라는 상태가 두 스프린트(I2a 933개 중 1개 → S2a 999개 중 1개) 동안 정상으로 취급되었고, 그 사이 이 리포트가 다루는 부트 실패가 통과했다. 상시 RED 기준선은 회귀 탐지 능력 자체를 잠식한다.

또한 `SecurityConfig` 는 S2a 가 건드리지 않았지만, **두 신규 쓰기 엔드포인트에 대해 실제 CSRF 토큰 경로가 미검증**이라는 사실은 남는다. 실패 방향은 fail-closed(정상 요청이 거부됨)이므로 CSRF 우회 취약점은 아니다 — [audit-S2a.md](../security/audit-S2a.md) SEC-S2a-17 참조.

---

## 8. 결함 통계

| 출처 | CRITICAL | HIGH | MEDIUM | LOW | 정보성 |
|------|---------:|-----:|-------:|----:|-------:|
| `code-reviewer` | 1 | 3 | 3 | 3 | — |
| `security-auditor` (CVSS) | **3** | 1 (연쇄 1) | 3 | 9 | 2 |
| `trace-mapper` | — | 3 | 2 | 5 | — |
| 교차검증 (qwen2.5-coder) | — | — | 1 | 1 | 오탐 3 |
| **QA (본 리포트)** | 1 (§2.2) | 3 (§4.1, §5, §6) | 1 (§7.1) | 1 (§3 개수 오류) | — |

**CVSS ≥ 7.0: 4건** (시크릿 3 + 연쇄 1) — 전부 S2a 소관 아님. [audit-S2a.md](../security/audit-S2a.md) §10 라우팅 참조.

**강제 룰 위반 0건** — 매퍼 `${}` 0 · PII 평문 로그 0 · S2a 코드 시크릿 0 · `System.out` 0 · 금액 부동소수 0.

---

## 9. 환경

| 항목 | 값 |
|------|-----|
| JDK | 17.0.20 (Microsoft) |
| Maven | 3.9.16 |
| Node / npm | 24.14.1 / 11.11.0 |
| DB (통합시험) | `io.zonky.test:embedded-postgres` 2.0.7 (Docker 미사용) |
| DB (기동 확인) | PostgreSQL @ 136.85.16.4:5432 — **환경변수 없이 연결됨**, SEC-S2a-02 참조 |
| gitleaks | 8.18.0 |
| SBOM | CycloneDX 1.5, 128 컴포넌트 |
| 교차검증 모델 | `qwen2.5-coder:7b` (로컬 Ollama, egress 0) |

---

## 10. 후속 조치

| 우선 | 조치 | 근거 | 기한 |
|:---:|------|------|------|
| **P0** | Tier A `ApplicationContextRunner` 시험 (~15줄, DB 불필요) — **이것이 명세대로의 TC-S002-29** | §2.2 (b), §2.3 | 즉시 |
| **P0** | Tier B `ApplicationContextSmokeTest` + `application-test.yml`. **`application.yml` 시크릿 기본값 제거보다 먼저** | §2.3, §2.4 | G3 전 |
| **P0** | CSRF 테스트 그린 — 빌드 RED 종결 (R1 verdict 의 미이행 지시) | §7.1 | G3 전 |
| **P1** | 커버리지 게이트 4건 — 특히 `auth.domain` 44%→95% | §2.1 | G3 전 |
| **P1** | `SenderNumberDeleteLoadTest` (NFR-PERF-D03, 임베디드 PG) | §6 | S2b |
| **P1** | 롤백 통합시험 — FR-SNDC-008 / FR-SNDD-005 를 명세된 수준으로 | §4.1 | S2b |
| **P2** | `qa/load/senderno-load.js` SPECIFIED_NOT_RUN 작성 | §6 | S2b |
| **P2** | E2E 인프라 결정 — Playwright 도입 또는 TEST-PLAN §2/§13.1 을 실제 수준으로 정정 | §5 | S2b |
| **P2** | 추적표는 구현이 도달하지 않는 시험 수준을 기재할 수 없다는 규칙 제정 — **빌드 가시적 불일치**로 | §2.2(b), §5 | S2b |
| **P3** | 로그 정량 주장 정정 (26→23, 8→9, 18→19, 66→67, DeleteDialog 9→8) + 테스트 이름 2건 | §3 | S2b |

### 10.1 테스트 커버리지 차원 재점수

**93 은 방어할 수 없다. 제안: 72 / 100.**

| 조정 | Δ | 근거 |
|------|---:|------|
| 주장 기준선 | 93 | |
| 컨텍스트 로드 커버리지 0, 확인된 P1 탈출 | **−13** | 스위트가 애플리케이션이 기동하지 않음을 탐지할 수 없다. 커버리지 차원에서 이는 얇은 지점이 아니라 **없는 축**이다. 세 가지로 가중 — (i) 수정이 +2줄이고 **어떤 테스트도 바뀌지 않아** 신호 0으로 재도입 가능, (ii) AlimTalk 탈출의 **반복**이며 교훈이 기록된 뒤 일반화되지 않음, (iii) **계획이 그 시험을 명세**했고 생성자 호출로 강등 출시 |
| 같은 공백 뒤 잠재 배선 결함 3건 | **−3** | `SecretCipher:64`, `IpAllowlistPolicy:55`, `AlimTalkDispatchConfig:110` |
| 부하 §8 시나리오 4개 중 0개 | **−4** | §2 가 부하를 커버리지 목표로 규정. 미측정이 아니라 **미작성**, 두 스프린트 연속, 작동하는 선례와 픽스처가 이미 존재 |
| E2E 목표 표기 vs 인프라 부재 | **−2** | 9개 TC 행이 존재하지 않는 계층에 매핑 |
| 자기보고 정량 6건 중 4건 오류 | **−1** | 개별로는 사소하나, 차원이 자기 주요 지표를 오보하는 것은 그 차원의 존재 이유에 걸린다 |
| **가산 — tier-1 D-S1 회귀는 실재** | **+2** | 진정으로 예외적이며 스프린트가 자기 최강 자산으로 옳게 식별. 19개 중 17개 이름 존재, 실질 표본 3건 전부 이름대로 단언 |
| **정정** | **72** | |

**거버넌스 결과**: 테스트 커버리지 가중 10% → 93→72 는 가중 총점을 **91.4 → 89.3** 으로 옮긴다. **90 임계 미달** — 즉 스프린트는 자기 인증 대신 **재생성 루프에 진입했어야 했다.** 숫자가 아니라 그 임계 통과가 요점이다.

**완성도 92 도 유지하기 어렵다** — 산출물이 기동하지 않은 스프린트다. 완성도가 조금이라도 내려가면 총점은 더 떨어진다. **7차원 평가를 한 칸 수정이 아니라 재실행할 것을 권고한다.**
