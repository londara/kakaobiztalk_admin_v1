# 코드 리뷰 리포트 — Sprint S2a (발신번호 등록 · 삭제)

> **Reviewer**: code-reviewer · **Skill**: 05 §1[B] · **Date**: 2026-08-21
> **Tree**: `main` @ `34b4254` + 미커밋 `BarredNumbers.java`
> **판정**: **CONDITIONAL APPROVE** (조건 4건, G3 전 종결)

---

## 1. 범위

| 대상 | 파일 |
|------|------|
| 도메인 | `SenderNumberWriteService`, `BarredNumbers`, `SenderNumberRegistration`, `SenderNumberDeletion`, `SenderNumberLimits`, `SenderNumberRef`, `SenderNumberAction`, 예외 3종 |
| API | `SenderNumberController`, `SenderNumberContextResponse`, `SenderNumberWriteResponse`, 요청 레코드 2종, `SenderNumberExceptionHandler` |
| 데이터 | `SenderNumberMapper.xml`, `V3__senderno_archive.sql` |
| 프론트 | `SenderNumberRegisterDialog.tsx`, `SenderNumberDeleteDialog.tsx`, `SenderNumberPage.tsx` |

읽기 전용 정적 리뷰. 리뷰 중 수정한 파일 없음.

---

## 2. 평가 차원

| 차원 | 판정 | 요약 |
|------|------|------|
| 네이밍 | **PASS** | `countAnywhere`·`deleteLive`·`archive`·`looksLikeDisplayValue`·`SenderNumberNotLiveException` 은 자신이 고치는 결함을 이름에 담는다. 약어 표류·`data`/`info`/`manager` 류 없음. 흠 1건: `requireScopedInstitution` 이 수행하지 않는 스코핑을 약속한다 (CR-S2a-02) |
| 예외 처리 | **WARN** | 도메인 예외 전부 매핑됨. 공백 1건 — `DuplicateKeyException` 미처리 (CR-S2a-04) |
| 스레드 안전성 | **PASS** | §2.1 |
| 거래 무결성 | **WARN** | 업무 트랜잭션은 진짜 하나의 경계. 문제는 감사 저장소(CR-S2a-05)와 `archive` 행수 검사(CR-S2a-07) |
| Javadoc 준수 | **PASS** | §2.2 |

### 2.1 스레드 안전성 — 깨끗하다

- `BarredNumbers`: 두 필드 모두 `final`, `parse` 가 생성자 안에서만 만든 집합을 `Collections.unmodifiableSet` 으로 반환(`:250`), 가변 공개 없음 (`BarredNumbersTest:72` 가 단언).
- `SenderNumberLimits`: `int` 상수 + private 생성자(`:296`).
- `SenderNumberValidator`: 상태 없는 `final` 클래스, private 생성자, 금지목록을 인자로 받는 순수 static `validate`(`:42`, `:68`).
- `SenderNumberWriteService`: `final` 협력자 4개, 가변 필드 없음.
- `TenantContext`: `ThreadLocal` 을 필터 `finally` 에서 정리.

**동시 이중 삭제는 실제로 안전하며 기록할 값이 있다.** 두 트랜잭션이 같은 번호를 삭제하면 각자 자기 스냅샷에서 archive 하고, 두 번째는 행 락에서 대기한 뒤 0 행을 삭제하며, `deleted == 0`(`:217`)이 자기 archive insert 를 롤백한다. **유일한 동시성 결함은 등록의 check-then-act** (CR-S2a-04).

### 2.2 Javadoc / `// req:` — 깨끗하다

S2a Java 산출물의 모든 public 타입·메서드가 한국어→영어 Javadoc + `// req:` 를 갖고, 레거시 원본이 있으면 `// source:` 를 갖는다. 멤버 단위로 확인: `SenderNumberWriteService`(public 4 + private 10), `BarredNumbers`, `SenderNumberLimits`, `SenderNumberRegistration`, `SenderNumberDeletion`, `SenderNumberRef`, `SenderNumberAction`, 예외 3종, `SenderNumberController`, `SenderNumberContextResponse`, `SenderNumberWriteResponse`, 요청 레코드 2종, `SenderNumberExceptionHandler`, 매퍼 `record` 2종. SQL 파일은 `req:` 헤더 + 변경 줄마다 `FIX D-Sn:` 마커.

미문서 멤버는 private enum 생성자(`SenderNumberAction:165`, `SenderNumberValidator.Result:149`)와 `BarredNumbers` 의 private 위임 생성자(`:106`) — public API 아님.

---

## 3. 발견 사항

| ID | 위치 | 등급 | 요약 |
|----|------|------|------|
| **CR-S2a-01** | `src/test/**` (부재) | **CRITICAL** | 저장소의 어떤 테스트도 Spring 컨텍스트를 로드하지 않는다 |
| **CR-S2a-02** | `WriteService:332-343` | **HIGH** | 테넌트 오버라이드 분기가 도달 불가능한 죽은 코드 |
| **CR-S2a-03** | `WriteAuthorizationTest:370-380` | **HIGH** | 증명하려는 동작을 스텁으로 만들어 놓고 단언 |
| **CR-S2a-04** | `WriteService:133-141` | **HIGH** | check-then-act + `DuplicateKeyException` 핸들러 부재 → 409/500 분기 |
| **CR-S2a-05** | `WriteService:150`, `:248` | MEDIUM | `Outcome.OK` 감사가 업무 커밋보다 먼저 독립 커밋된다 |
| **CR-S2a-06** | `WriteService:494-500` | MEDIUM | 다기관 삭제가 `TARGET_ACCOUNT VARCHAR(50)` 초과 → 500 + 전체 롤백 |
| **CR-S2a-07** | `WriteService:204-214` | MEDIUM | `archive` 만 행수를 `== 0` 으로만 검사 (형제는 모두 정확 개수) |
| **CR-S2a-08** | `SenderNumberExceptionHandler:66` | LOW | `@Order` 동값 충돌 — 선언한 불변식이 지켜지지 않는다 |
| **CR-S2a-09** | `V3:92-97` vs `:99`,`:163` | LOW | `INCLUDING` 생략 근거가 사실과 다르다 (결정은 옳음) |
| **CR-S2a-10** | `SecretCipher:64`, `AlimTalkDispatchConfig:110` | LOW | 기본값 없는 `@Value` 2건 — CR-S2a-01 부류, S2a 소관 아님 |

### CR-S2a-01 · CRITICAL — 부트 실패의 체계적 원인

`src/test` 전체에서 `@SpringBootTest` **어노테이션** grep 결과 0건. 문자열이 나타나는 유일한 위치는 `CsrfIntegrationTest.java:45` 의 Javadoc 문단이며, 거기서 *쓰지 않는 이유*를 설명한다(해당 클래스는 `@MockBean` Clock/AuditService 를 가진 `@WebMvcTest`). `ApplicationContextRunner` 도 없고 `contextLoads` 테스트도 없다.

**이것이 이미 확인된 `BarredNumbers` 부트 실패의 직접 원인이며, `@Autowired` 추가는 *인스턴스*를 고쳤을 뿐 *부류*를 고치지 않았다.** 백엔드 999개 그린 테스트는 기동 가능한 애플리케이션과 기동 불가능한 애플리케이션을 구별하지 못한다.

> **실측**: `mvn spring-boot:run` 이 `No default constructor found` 로 죽었고, `@Autowired` 한 줄 추가 후 3.565초에 기동해 Tomcat 8080 + HikariPool 연결까지 도달했다. 그 사이 테스트 결과는 **변하지 않았다** — 999개 중 단 하나도 이 차이에 반응하지 않는다.

**조치**: `@SpringBootTest(webEnvironment = NONE)` 스모크 테스트 1건. `io.zonky.test:embedded-postgres` 가 이미 `pom.xml` 에 있고 `SenderNumberMapperIntegrationTest` 가 사용하므로 Docker 불필요. CI 게이트에 편입. 상세 설계는 [qa/test-report-4-senderno-S2a.md](../qa/test-report-4-senderno-S2a.md) §2.2.

### CR-S2a-02 · HIGH — 문서화되고 시험되었으나 죽은 보안 통제

`WriteService:332-343` 의 테넌트 오버라이드 분기는 `register:113` 과 `delete:193` 에서만 도달하며, 둘 다 `requireOperator()`(`:112`, `:183`) **뒤**다. `requireOperator()` 는 비운영자를 전부 던져낸다(`:301-307`). 운영자에 대해 `TenantPrincipal.effectiveInstitutionCode` 는 비어 있지 않은 요청값을 그대로 반환하고 비면 `null` 을 반환한다. 따라서 `scoped` 는 `null`(`:329-331` 이 잡음) 이거나 `== requested` 이며, `!requested.equals(scoped)` 는 **항상 false**.

코드가 주장하는 세 가지가 존재하지 않는다:
- `ACTION_TENANT_OVERRIDE_ATTEMPT` 는 어떤 쓰기 경로에서도 기록되지 않는다.
- `:342` 의 `AccessDeniedException` 은 발생할 수 없다.
- `:310-318`/`:188-192` 의 Javadoc, 로그의 T-T8 주장("crafted list 는 세션 스코프로 필터된다"), `SenderNumberDeleteRequest.java:201-212` 가 모두 아무것도 제한하지 않는 ref 별 재판정을 서술한다.

유일한 실제 통제는 OPERATOR 롤이다. **스프린트 자신의 D-S4 기준("선언되었으나 죽은 입력은 존재하는 통제처럼 읽힌다")으로 이것이 그 결함이다** — 보안 통제에서, 쓰기 경로에서, 소유권 검증이 거부된 상태(RESIDUAL-S01)에서.

교차검증(XV4-02)과 보안감사(SEC-S2a-06)가 독립적으로 같은 지점에 수렴했다.

**조치**: 무엇이 참인지 결정. 운영자가 정당하게 전역 스코프를 갖는다면 분기를 삭제하고 Javadoc·T-T8 주장을 정정. FR-AZ-D03 이 Javadoc 대로라면 요청 대상 기관과 다른 ref 를 거부.

### CR-S2a-03 · HIGH — 자기 전제를 스텁으로 만든 시험

`WriteAuthorizationTest:370-380` 의 `outOfScopeInstitutionIsForbidden` 은 `willThrow(new AccessDeniedException(…)).given(writeService).register(…)` 로 스텁한 뒤 `// req: FR-AZ-D03, FR-SNDC-012, NFR-SEC-TENANT-D01` 아래에서 403 을 단언한다.

이는 **advice 가 `AccessDeniedException` → 403 으로 매핑함**을 증명한다. 서비스가 그것을 생성하는지에 대해서는 아무것도 증명하지 않으며, CR-S2a-02 에 따르면 생성할 수 없다. `grep TENANT_OVERRIDE src/test` → 0건, 즉 해당 분기에는 시험이 전혀 없다.

**스프린트 자신의 SS2a-01("새로운 이유로 통과한 시험")과 같은 부류다.**

**조치**: FR-AZ-D03 req 태그 제거(advice 시험임). 서비스 수준 오버라이드 시험 추가 — 실패할 것이며, 그것이 CR-S2a-02 가 드러나는 방식이다.

### CR-S2a-04 · HIGH — FR-SNDC-004 는 현재 아무 곳에서도 강제되지 않는다

상세는 [cross-validation-4.md](cross-validation-4.md) §4.1 XV4-04 참조 (동일 결함, 교차검증에서 독립 확인).

요지: `countAnywhere` → `insertLedger` 는 check-then-act. 실제 장벽은 `UX_KKB_DPNO_LDGR_01`(`V3:163`)뿐이고 SPRINT-S2a-LOG §5 에 따르면 **어떤 환경에도 적용되지 않았다.** 그리고 `grep -rn "DuplicateKeyException\|DataIntegrityViolation" src/main/java` → **0건** — 인덱스 적용 후에는 위반이 `handleUnexpected` → **500** 이 되고, 같은 업무 조건을 count 로 잡으면 **409** 다. `SenderNumberMapperIntegrationTest:369-370` 은 예외가 raw 임을 문서화하면서(메시지에 `ux_kkb_dpno_ldgr_01` 포함 단언) 의도적으로 서비스를 우회하므로 번역 공백이 보이지 않는다.

**조치**: `@ExceptionHandler(DuplicateKeyException.class)` 로 동일 409 본문, 또는 `register` 에서 잡아 `SenderNumberDuplicateException` 으로 재던지기.

### CR-S2a-05 · MEDIUM — 감사 저장소가 업무 트랜잭션보다 먼저 커밋된다

`WriteService:150`, `:248` 의 `record(…, Outcome.OK, …)` 가 `@Transactional` **안**에서 실행되고 `AuditService.record` 는 `REQUIRES_NEW`(`AuditService.java:53-56`)다 — 감사 행이 독립적으로 즉시 커밋된다.

이후 외부 커밋이 실패하면 감사 저장소는 아무것도 지우지 않은 삭제에 대해 *"3건 삭제 / 3 numbers deleted"* + outcome OK 를 단언한다. **D-S1 의 형태가 감사 추적으로 이전된 것** — 변경되지 않은 데이터베이스와 공존하는 성공 기록.

**조치**: OK 는 `TransactionSynchronization.afterCommit` 에서 기록(또는 ATTEMPTED 선기록 → OK 후기록). 거부·실패 기록은 롤백 생존이 목적이므로 `REQUIRES_NEW` 유지.

### CR-S2a-06 · MEDIUM — 다기관 삭제가 감사 컬럼을 넘친다

`scopeOf`(`:494-500`)가 다기관 삭제에 대해 `String.join(",", codes)` 를 반환하고 이것이 `TARGET_ACCOUNT VARCHAR(50)`(`V1__auth_session_audit.sql:99`)에 들어간다. `IS_CD` 는 `VARCHAR(6)`(`V2__alimtalk_outbox.sql:62`)이므로 코드당 7자, **8개 기관 = 55자**에서 insert 가 실패한다. 요청은 ref 100개까지 담을 수 있고, CR-S2a-02 에 따라 운영자의 코드는 그대로 수용된다.

감사 insert 가 `audit.record` 안에서 던지고 `delete` 밖으로 전파되어 업무 트랜잭션을 롤백하며 운영자는 500 을 받는다. **삭제와 그 감사 기록이 모두 소실된다.** 어떤 시험도 보지 못한다 — 모든 서비스 시험이 `AuditService` 를 목으로 대체하고, 통합 시험은 감사 경로를 건드리지 않는다.

**조치**: 값을 제한 — 단일 코드, 아니면 `"multi:" + codes.size()` — 또는 컬럼 확장. 그리고 기관 간 삭제를 허용하는지 자체를 정리 (CR-S2a-02 의 질문).

### CR-S2a-07 · MEDIUM — 이 클래스가 없애려는 D-S7 패턴

`archive` 의 행수는 `== 0` 만 검사된다(`:204-214`). 형제는 모두 정확 개수를 본다: `insertLedger` `!= 1`(`:143`), `insertHistory` `!= 1`(`:280`), `deleteLive` 는 0 과 `> 1` 양쪽(`:217-229`).

unique index 미적용 상태에서 중복 live 행이 가능하고, `archive` 는 N 행을 `KKB_DPNO_ARCV` 로 복사한 뒤 **다음** 문장의 `deleted > 1` 검사만이 중단시킨다. 트랜잭션이 롤백되므로 손실은 없으나, "이 문장의 이상을 다음 문장의 행수가 탐지한다" 는 이 클래스가 제거하려 존재하는 바로 그 D-S7 패턴이다.

**조치**: `if (archived != 1) throw …`, `> 1` 케이스는 `deleteLive` 가 이미 하듯 FR-SNDC-004 를 명명.

### CR-S2a-08 · LOW · CR-S2a-09 · LOW · CR-S2a-10 · LOW

- **CR-S2a-08**: advice 가 `@Order(HIGHEST_PRECEDENCE + 10)` 을 설정하고 `:46-54` 에서 *"명시적 순서가 없으면 둘이 동값이 되어 Spring 이 승자를 보장하지 않는다"* 고 주장하는데, `InstitutionExceptionHandler` 는 **범위 지정 없는** `@RestControllerAdvice` 로 **같은 값**이다. 실제 충돌은 없으나(예외 타입 분리) 선언한 불변식이 지켜지지 않는다. 구체적 결과: `GET /context`(`Controller:131` → `InstitutionService:92-97`)의 `InstitutionNotFoundException` 이 발신번호 엔드포인트에서 *institution* 슬라이스의 본문 형태를 반환해, 이 advice 자신의 "클라이언트당 한 형태" 근거와 모순한다.
- **CR-S2a-09**: `INCLUDING` 생략 근거가 *"§3 의 unique index 를 복사하면 같은 번호를 두 번 삭제할 수 없게 된다"* 인데, §3 의 `CREATE UNIQUE INDEX` 는 `:163` 으로 `CREATE TABLE … (LIKE …)`(`:99`) **뒤**다 — 어차피 복사될 수 없었다. **결정은 옳고 정당화가 틀렸으며, 이 파일은 DBA 가 G1 에서 읽는 파일이다.** (주석은 PostgreSQL `LIKE` 가 NOT NULL 은 복사하고 default·CHECK 는 복사하지 않는다는 점도 구분하지 않는다.)
- **CR-S2a-10**: 기본값 없는 `@Value` 2건 — `iris.auth.otp.secret-key`, `iris.alimtalk.vendor.base-url`. 둘 다 `application.yml`(`:235`, `:180`)에서 충족되므로 현재 부트 위험은 아니다. CR-S2a-01 부류로만 기록. **단, `:235` 의 충족 방식 자체가 CRITICAL 보안 결함이다** — audit-S2a §3 SEC-S2a-03 참조.

---

## 4. 강제 룰 점검

| 룰 | 결과 |
|----|------|
| 매퍼 `${}` | **0건** — `src/main/resources/mybatis/` 전체. 유일한 텍스트 일치는 금지 이유 설명 주석(`MessageDetailMapper.xml:28`) |
| PII 평문 로그 | **0건** — 싱크별 검증은 audit-S2a §4 |
| 소스 내 시크릿 | **S2a 코드 0건.** 금지목록은 정확히 이 이유로 평문 `.txt` (SEC-001). **단 릴리즈 아티팩트에는 3건** — audit-S2a §3, S2a 소관 아님 |
| `System.out` / `printStackTrace` | **0건** (`src/main/java`), 프론트 `console.*` **0건** |
| 금액 부동소수 | **0건** — 이 슬라이스에 금액 없음 |
| Javadoc / `// req:` / `// source:` | **위반 0건** — §2.2 |

**REJECT 급 룰 위반 0건.**

---

## 5. 7차원 자체 평가 검증

| 차원 | 가중 | Leader | code-reviewer | Δ | 근거 |
|------|-----:|-------:|--------------:|---:|------|
| 완성도 | 20% | 92 | **80** | −12 | 산출물이 **기동하지 않았다.** 12/13 태스크는 맞으나 배포 가능 상태가 아니었다 |
| 추적성 | 15% | 96 | **96** | 0 | trace-mapper 가 49/49 행 해석 확인. 로그의 감점 근거(ADR 미개정)는 오히려 **사실이 아님** — 두 ADR 모두 개정되어 있다 |
| 보안 | 20% | 94 | **80** | −14 | CR-S2a-02/-03: 문서화·시험된 죽은 통제 + 자기 전제를 스텁한 시험 |
| 성능 | 10% | 70 | **65** | −5 | 정직하게 보고됨. §8 시나리오 **4개 중 0개 스크립트 존재** (측정 미실시보다 나쁨) |
| 가독성 | 15% | 95 | **95** | 0 | 이의 없음 |
| 표준 준수 | 10% | 95 | **85** | −10 | 하네스 §3 이 의무화한 L1 Hook 미설치. 커밋 미실시 |
| 테스트 커버리지 | 10% | 93 | **72** | −21 | CR-S2a-01 + 실측 커버리지 게이트 미달 (§5.1) |

**가중 총점: Leader 91.4 → code-reviewer 80.6 (Δ 10.8)**

**하네스 §4 임계 초과 (> 10점) → Leader 자기 합리화 의심 구간 → PM 결재 필요.**

다만 성격을 명확히 한다: 이는 Leader 가 결함을 은폐한 사례가 아니다. 로그는 성능 70 을 정직하게 자책하고, SS2a-01~06 을 스스로 제기했으며, 사전 실패 시험을 문서화했다. Δ 의 대부분은 **한 사건**에서 온다 — 기동하지 않는 산출물, 그리고 그것을 볼 수 있는 시험이 없다는 사실이 완성도·보안·테스트 커버리지 세 차원에 동시에 걸린 것이다. 그 사건은 스프린트 종료 **후** 발견되었으므로 평가 시점에는 알 수 없었다.

### 5.1 실측 커버리지 — 프로그램 최초 측정

`verdict-sprint-R1-T1-T2.md` V-4 는 *"게이트 자체가 `mvn verify` 미실행으로 한 번도 강제된 적 없음"* 이라 기록했다. 본 리뷰에서 `mvn verify` 를 실행해 **처음으로 측정**했다.

| 규칙 | 실측 | 기준 | 판정 |
|------|-----:|-----:|:---:|
| BUNDLE LINE | **70%** | 80% | **FAIL** |
| BUNDLE BRANCH | **66%** | 70% | **FAIL** |
| `com.webcash.iris.auth.domain` LINE | **44%** | 95% | **FAIL** |
| `com.webcash.iris.auth.crypto` LINE | **70%** | 95% | **FAIL** |

4개 규칙 전부 미달. `auth.domain` 44% 는 95% 기준이 걸린 보안 핵심 패키지다. `jacoco:check` 가 `verify` 단계에 바인딩되어 있어 `mvn test` 로는 절대 실행되지 않는다 — 게이트가 존재하면서 한 번도 발동하지 않은 이유다.

---

## 6. 판정 및 권고

### **CONDITIONAL APPROVE**

REJECT 아님. REJECT 급 룰 위반이 없고(§4), 스프린트의 핵심 주장이 실재한다 — 삭제는 진짜 행 이동이고, 중요한 행수는 각자의 호출 지점에서 검사되며, 무일치는 실제 PostgreSQL 에 대한 실행 가능한 회귀를 가진 409 다. 선행 스프린트보다 실질적으로 강한 위치다.

**G3 전 종결 조건 4건:**

1. **CR-S2a-01** — 부트 실패의 체계적 원인이 손대지 않은 상태다. `@Autowired` 는 한 빈을 고쳤고, 다음 배선 결함은 동일하게 배포된다. 스모크 테스트 1건이면 되고 인프라는 이미 POM 에 있다.
2. **CR-S2a-02 + CR-S2a-03 (한 쌍)** — 문서화·스텁시험된 죽은 보안 통제. 쌍으로 해소해야 한다. 시험만 고치면 거짓 통과가 RED 빌드로 바뀔 뿐이며, 그것이 옳은 다음 상태이되 최종 상태는 아니다.
3. **CR-S2a-04** — FR-SNDC-004 가 현재 어디에서도 강제되지 않고, 강제되기 시작하면 잘못된 상태코드를 반환한다.
4. **커버리지 게이트 4건 미달** (§5.1) — 실측되었으므로 이제 "미측정" 으로 이연할 수 없다.

**수정 우선순위**: CR-S2a-01 → CR-S2a-02+03 → CR-S2a-04 → CR-S2a-06 → CR-S2a-05 → CR-S2a-07 → CR-S2a-08/09

**PM 결재 필요 사항**: 7차원 Δ 10.8 (§5) — 하네스 §4 임계 초과.
