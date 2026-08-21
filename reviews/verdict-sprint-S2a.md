# 종합 판정 — Sprint S2a (발신번호 등록 · 삭제)

> **Skill**: 05 (품질/코드 리뷰 + 교차 검증) · **Date**: 2026-08-21
> **Lead**: security-auditor (Validation Team Leader) · **Support**: code-reviewer, qa-engineer, trace-mapper
> **Tree**: `main` @ `34b4254` + 미커밋 `BarredNumbers.java` (+2줄)
>
> ## 판정: **REJECT (G3 릴리즈 게이트)** · Sprint S2a 산출물은 **조건부승인**
>
> 두 판정이 갈리는 이유는 §2 라우팅에 있다. **차단 사유 4건 중 S2a 에서 비롯한 것은 0건이다.**

---

## 1. 한 줄 요약

이 스프린트가 쓴 발신번호 쓰기 경로는 좋은 작업이다. **자신이 쓰지 않은 설정 3줄 때문에 차단된다.** 그리고 스프린트 자신에게 속한 가장 큰 문제는 결함 목록이 아니라 **산출물이 기동하지 않았고 999개 그린 테스트 중 어느 것도 그것을 볼 수 없었다**는 사실이다.

---

## 2. 차단 결정과 라우팅

### 2.1 CVSS ≥ 7.0 — 4건

| ID | 위치 | CVSS | 벡터 | S2a? | 소유 |
|----|------|-----:|------|:----:|------|
| SEC-S2a-01 | `.env:26-28,48` | **9.8** | `AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H` | ✗ | 인프라 |
| SEC-S2a-02 | `application.yml:61-63` | **9.8** | `AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H` | ✗ | 인프라 |
| SEC-S2a-03 | `application.yml:235` | **9.1** | `AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:N` | ✗ | 인프라 |
| SEC-S2a-05 | `V3:56-65` + mapper (연쇄) | **7.5** | `AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:N/A:N` | △ | 아키텍트+DBA |

하네스 §2 의 CVSS ≥ 7.0 즉시 차단 규정 및 security-auditor 의 REJECT 권한(시크릿 하드코딩 ≥ 9.0)에 따라 **릴리즈 차단.** PM 통보는 본 리포트와 묶지 않고 **즉시** (§10 원칙: CVSS 9.0+ 발견 시 즉시 진행 중단).

### 2.2 라우팅 — REJECT 를 S2a 구현자에게 반송하지 말 것

`git log -S` 로 도입 시점을 확인했다.

| 결함 | 도입 커밋 | S2a 대비 |
|------|-----------|----------|
| `.env` 추적 + 자격증명 | `0acfb39` | **이전** |
| DB 비밀번호 기본값 | `0db4d0f` → `0acfb39` | **이전** |
| OTP 키 기본값 | `0acfb39` | **이전** |

`git status` 상 S2a 작업트리가 건드린 것은 `BarredNumbers.java` 의 `@Autowired` 한 줄뿐이다.

| 수신자 | 항목 |
|--------|------|
| **인프라 / 로그인 슬라이스** | SEC-S2a-01/02/03 (시크릿 회전 후 제거), SEC-S2a-15 (`require-https` fail-open), SEC-S2a-17 (CSRF 빌드 RED) |
| **하네스 Hook 소유자** | SEC-S2a-04 — L1 미설치·L2 미집행·gitleaks 탐지 역전. **감사자 자신의 통제가 작동하지 않았다** |
| **아키텍트 + DBA** | SEC-S2a-05 (T-I7 방향 오류), SEC-S2a-14 (GRANT 부재 + ADR-007 모순) |
| **S2a 구현자** | 아래 §5 조건 목록 — 전부 Low/Medium |

### 2.3 왜 이것이 "설정 실수" 이상인가

세 시크릿 결함은 **파일 자신이 자신을 반박하는** 형태다.

- `.env:5-6` — *"이 파일은 커밋되지 않는다 (.gitignore). 실제 자격증명이 들어 있다."* → `.gitignore` 에 `.env` 항목 없음, `git ls-files` 에 존재. 두 진술 중 거짓인 쪽이 첫 번째다.
- `application.yml:46-60` — *"2026-08-19 정정: 이 세 줄에는 자격증명이 그대로 박혀 있었다… 기본값을 두지 않으므로 환경변수가 없으면 기동에 실패한다"* → 기본값 3줄 그대로.
- `application.yml:218-234` — *"NO DEFAULT… 소스에 공표된 키로 자격증명을 암호화하기보다 기동에 실패해야 한다"* → 12줄 뒤에 기본값.

**정정은 문서화되었고 실행되지 않았다.** 그리고 프로젝트의 L2 CI 규칙(`ci.yml:236`)은 이 패턴을 정확히 노려 작성되어 있으며, 현재 트리에 그대로 실행하면 **발동한다**:

```
src/main/resources/application.yml:63:    password: ${IRIS_DB_PASSWORD:biztalk123}
src/main/resources/application.yml:235:      secret-key: ${IRIS_OTP_SECRET_KEY:3NUSPSH/…=}
→ exit 1  ::error:: Secret value or defaulted secret in a configuration resource (SEC-A02)
```

규칙은 옳고 위반은 실재한다. 따라서 **`main` 은 자기 보안 게이트에서 RED 이거나, CI 가 `main` 에서 돌지 않는다.** (`gh` CLI 부재로 Actions 이력 조회 불가 — 둘 중 어느 쪽인지 이 회차에서 확정하지 못했다. 로컬 `main` == `origin/main` 이므로 푸시는 되어 있다. **이 확정 자체가 P0 후속 조치다.**)

**실측 증거**: 환경변수 없이 `mvn spring-boot:run` 실행 → 기동 성공 → `HikariPool-1 - Added connection`. 즉 fail-closed 기동이 파괴되었다는 것은 이론이 아니라 관측되었다.

---

## 3. 이번 회차의 핵심 발견 — 기동하지 않는 산출물

이 리뷰는 사용자가 보고한 부트 실패에서 시작했다.

```
UnsatisfiedDependencyException: Error creating bean with name 'senderNumberController'
 → 'senderNumberWriteService' → 'barredNumbers':
   Failed to instantiate [BarredNumbers]: No default constructor found
```

`BarredNumbers`(S2a-02 산출물)는 `@Component` 이면서 선언된 생성자가 2개(public + private)였다. Spring 의 `AutowiredAnnotationBeanPostProcessor` 는 `getDeclaredConstructors()` 로 후보를 모으므로 private 생성자까지 세어 2개가 되고, `@Autowired` 가 없으면 "유일할 때만 자동 선택" 규칙에 걸리지 않아 후보를 반환하지 않는다. 기본 생성자로 폴백해 실패했다. `@Autowired` 한 줄로 수정 → 3.565초에 기동, Tomcat 8080, DB 연결 확인.

**전 저장소 스캔 결과 같은 형태(생성자 2개 + `@Autowired` 없음)의 빈은 `BarredNumbers` 유일이다.** 확산은 없다.

### 3.1 그러나 원인은 이 한 빈이 아니다

| 사실 | 증거 |
|------|------|
| 저장소의 어떤 테스트도 Spring 컨텍스트를 로드하지 않는다 | 71개 테스트 클래스, `@SpringBootTest` **어노테이션 0건**. 문자열은 `CsrfIntegrationTest:45` 의 *쓰지 않는 이유* 설명 Javadoc 뿐. `ApplicationContextRunner` 0건 |
| 수정이 어떤 테스트도 건드리지 않는다 | diff **+2줄**. 어노테이션을 다시 지워도 실패하는 테스트가 없다 — **신호 0으로 재도입 가능** |
| 테스트 계획이 바로 이 빈에 대해 기동 수준 시험을 요구했다 | `TEST-PLAN-SENDERNO.md:222` CONST-BIZ-D03 → TC-S002-29/30, 수준 "**Startup + unit**". `:234` — *"TC-S002-29 는 애플리케이션이 기동을 거부함을 단언한다"* |
| 그것이 선언 없이 단위 수준으로 강등되었다 | 실제 구현 `BarredNumbersTest:140` 은 직접 생성자 호출. **계획대로였다면 이 결함을 잡았다** |
| **동일 탈출이 한 슬라이스 앞에 이미 있었고 교훈이 기록되었다** | `AlimTalkDispatchConfigTest:29-36` 자기 Javadoc — *"`TranIdGenerator` 를 인터페이스 뒤로 미루며 `@Bean` 을 남기지 않아 **애플리케이션이 기동에 실패했고**, 단위 테스트는 생성자를 직접 부르므로 전부 통과했다. **실행되지 않는 것은 검증되지 않는다.**"* 당시 완화는 한 config 클래스 어노테이션 리플렉션으로 좁게 끝났다 |
| 같은 공백 뒤에 다른 잠재 부트 실패 3건이 있다 | `SecretCipher:64` (기본값 없는 `@Value` + 생성자 2회 throw), `IpAllowlistPolicy:55` (설정 검증 throw), `AlimTalkDispatchConfig:110` (빈 기본값 → 조용한 오설정) |

**순서 의존성 (놓치면 사고가 된다)**: `application.yml:235` 의 시크릿 기본값 제거(SEC-S2a-03, P0)는 `SecretCipher` 의 잠재 부트 실패를 **활성화한다** — 현재 그 기본값이 실패를 가리고 있다. **따라서 컨텍스트 스모크 테스트가 보안 수정보다 먼저 들어가야 한다.**

### 3.2 최소 처방

**Tier A** (~15줄, DB 불필요, 무조건 전 빌드) — `ApplicationContextRunner` 로 `BarredNumbers` 배선 검증 + 부재 자원 시 `hasFailed()` 단언. **이것이 명세대로의 TC-S002-29 다.**

**Tier B** (`@SpringBootTest` + `EmbeddedPostgres` + `@DynamicPropertySource` + `application-test.yml`, `@Tag` 게이트) — 잠재 결함 3건과 이 부류 전체를 영구히 닫는다. 인프라는 이미 `pom.xml:119-124` 에 있고 두 테스트가 이미 사용한다.

설계 상세: [qa/test-report-4-senderno-S2a.md](../qa/test-report-4-senderno-S2a.md) §2.3.

**DoD 에 항목 추가 권고**: *"테스트 통과"* 옆에 ***"애플리케이션 컨텍스트가 로드된다"***.

---

## 4. 실측 결과

### 4.1 빌드 · 테스트

| 항목 | 결과 | 로그 주장과 비교 |
|------|------|------------------|
| 백엔드 | **999 tests / 1 failure** → `BUILD FAILURE` | **주장 정확** |
| 프론트엔드 | **218 / 218 passed** (16 files) | **주장 정확** |
| 실패 1건 | `CsrfIntegrationTest.echoingCookieValueInHeaderPasses:174` | 선재 실패, S2a 귀속 아님 — **주장 정확** |

**그러나 R1 회차 verdict 가 이미 못을 박았다: *"조치: 인증 슬라이스 백로그. 단, 빌드는 여전히 RED 이므로 G3 전에 닫혀야 한다."*** 그 지시가 이행되지 않은 채 또 한 스프린트가 지났다. 근본 원인도 이미 규명되어 있다 — `SecurityConfig:188` 이 `CsrfCookieFilter` 를 `addFilterAfter(..., CsrfFilter.class)` 로 등록하고, `CsrfFilter` 는 거부 시 체인을 잇지 않으므로 쿠키를 발급해야 할 요청에서 발급 필터가 실행되지 않는다.

**QA 관점의 실질 피해는 귀속이 아니라 신호 소실이다.** 빌드가 상시 RED 이면 새 실패가 기존 실패와 구별되지 않는다. 933개 중 1개(I2a) → 999개 중 1개(S2a) 가 두 스프린트 동안 정상으로 취급되었고, 그 사이 §3 의 부트 실패가 통과했다.

### 4.2 커버리지 — 프로그램 최초 측정

`jacoco:check` 는 `verify` 단계 바인딩이라 `mvn test` 로는 실행되지 않는다. R1 verdict V-4 가 *"게이트 자체가 한 번도 강제된 적 없음"* 이라 기록한 이유다. 본 회차에서 `mvn verify` 로 **처음 측정**했다.

| 규칙 | 실측 | 기준 | 판정 |
|------|-----:|-----:|:---:|
| BUNDLE LINE | **70%** | 80% | **FAIL** |
| BUNDLE BRANCH | **66%** | 70% | **FAIL** |
| `auth.domain` LINE | **44%** | 95% | **FAIL** |
| `auth.crypto` LINE | **70%** | 95% | **FAIL** |

4건 전부 미달. `auth.domain` **44%** 가 가장 심각하다 — 95% 기준이 걸린 이유가 보안 핵심이기 때문이다. R1 측정치(67.5 / 64.4) 대비 소폭 개선이나 게이트 미달은 동일. **이제 실측되었으므로 "미측정" 으로 이연할 수 없다.**

### 4.3 공급망

| 항목 | 결과 |
|------|------|
| SBOM | **PASS** — CycloneDX 1.5, 128 컴포넌트, 빌드 시 자동 생성 |
| 라이선스 | **WARN** — AGPL/SSPL/순수 GPL **0건**. 단 LGPL-2.1-or-later 1건, BSD-4-Clause(광고조항) 1건 법무 확인 필요 |
| 금지 목록 | **미정의** — DoD 가 "금지 OSS 0건" 을 요구하나 **판정 기준이 저장소에 없다.** 기준 없는 DoD 항목 |

### 4.4 보안 Hook 3단계

| 계층 | 상태 |
|------|------|
| L1 pre-commit | **미설치** — `.git/hooks/pre-commit` 부재. 스크립트는 존재, 수동 symlink 를 아무도 만들지 않음 |
| L2 CI | **규칙 정확, 미집행** — §2.3 |
| L3 prod-gate | 체크리스트 존재, `IRIS_REQUIRE_HTTPS` 항목 부족 |

**gitleaks 실측 — 탐지가 역전되어 있다.** 8건 검출, **전부 오탐**(RFC 6238 시험 벡터 `MFRGGZDF…` 및 테스트 픽스처), **실제 시크릿 3건 미검출**. 원인: `ci.yml` SEC-A02 규칙이 `src/main/resources/application*.yml` 만 스캔하고 루트 `.env` 는 범위 밖. `hooks/gitleaks.toml` 은 `useDefault = true` 뿐이고 allowlist 없음.

시험 벡터에 8번 경보하고 실제 자격증명 3건을 놓치는 구성은 **통제로서 음의 가치**를 가진다 — 운영자가 경보를 무시하도록 훈련시킨다.

### 4.5 교차 검증 (DoD 1) — 충족

| 항목 | 값 |
|------|-----|
| 모델 | `qwen2.5-coder:7b` (Alibaba Qwen) — **다른 벤더** |
| 실행 | 로컬 Ollama, **외부 egress 0** |
| 결과 | 5건 제기 → **2건 채택, 3건 반박 (오탐률 60%)** |

**S1 데이터 통제**: 시크릿 보유 파일(`application.yml`, `.env`)은 투입 제외. 모델이 로컬 프로세스이므로 리전 문제가 성립하지 않는다 — 규제 데이터 환경에서 "승인된 사내 모델" 요건을 만족하는 가장 강한 형태다.

**이 회차의 성과는 결함 목록의 정확성이 아니라 수렴이다.** 파일 1개만 보고(위협모델·테스트·ADR·로그 미열람) S2a 자체 결함 2건에 독립 도달했다 — 죽은 인가 계층(XV4-02 ↔ CR-S2a-02 ↔ SEC-S2a-06, **3중 수렴**)과 등록의 check-then-act 경쟁 조건(XV4-04 ↔ CR-S2a-04). 오탐 3건은 §5 에 명시 기록(§10 원칙).

**구조적 한계**: 이번 릴리즈를 실제로 차단한 3건은 S1 통제상 교차검증 대상이 아니었다. 시크릿 결함은 교차검증으로 잡을 수 없고 그 층은 L1/L2 의 몫이며, 그 Hook 이 작동하지 않았다.

상세: [cross-validation-4.md](cross-validation-4.md)

### 4.6 추적성

**trace 데이터 자체는 이례적으로 양호하다** — `requirements-trace-biztalk.csv` 의 S2a 49행 전부 실제 코드·실제 테스트로 해석되고(멤버 앵커 26개, 중첩 테스트 클래스 14개 전수 확인), 12개 결함 종결 모두 양단에 마커가 있고, FR-SNDC-*/FR-SNDD-* 25개 중 **조용히 미커버된 요구사항은 없다**.

**로그가 자신에 대해 틀린 점 1건**: §6 추적성 감점 근거 *"ADR 이 제자리에서 개정되지 않았다"* — **두 ADR 모두 개정되어 있다.** `ADR-SND-021` 은 *"Amended 2026-08-20 (Sprint S2a)"* 블록을, `ADR-SND-017:46-51` 은 DDL 대응 블록을 갖고, 회고 A5 가 완료로 기록한다. 점수가 낡은 근거로 **과소** 평가되었다.

**새 HIGH 추적성 결함 2건:**

| ID | 결함 |
|----|------|
| **G2** | **원자성·롤백이 주장되고 단언되지 않는다.** DoD 는 *"강제된 이력 실패가 등록·삭제 양쪽을 롤백"* 을 체크하는데, 롤백을 단언하는 시험이 없다(`grep "rollback\|Transactional"` → 양 테스트 파일 0건). `historyFailureFailsTheRegistration` 은 목 매퍼에 `insertHistory→0` 을 스텁하고 예외만 단언한다 — **목에는 롤백할 상태가 없다.** FR-SNDC-008·FR-SNDD-005 는 검증 열에 "Integration test" 를 명시하는데 추적표는 단위 시험으로 충족 처리. 임베디드 PG 가 같은 파일에 이미 있으므로 Docker 제약에 의한 강요가 아니다. **D-S6/D-S7 자신의 전제를 서술로 남긴 것이며, 그 반대를 자축한 스프린트(§2.1)에서** |
| **G3** | **`FR-SND-012`(Must) 가 다른 요구사항을 단언하는 시험에 대해 IMPLEMENTED 로 표기.** trace 행이 `SenderNumberPage.test.tsx#FR-SNDC-012` 를 지목하는데 그 케이스(`:298`)는 등록 팝업의 기관 선택이지 쓰기 후 재조회·선택 해제가 아니다. `FR-SND-012` 는 5개 소스의 `req:` 헤더에 있고 **저장소의 어떤 테스트에도 없다.** **SS2a-01 과 정확히 같은 결함 부류가, SS2a-01 을 제기한 그 스프린트의 trace 파일 안에서 재발.** 회고 A2 는 이를 막으려 작성되었고 잡지 못했다 — A2 는 테스트를 보고 trace 행을 보지 않기 때문이다 |

**목적지 없는 OPEN 2건**: SS2a-05(Medium — HIS/LDGR DDL 미확보, 타입 추론; §4 carried 에도 §8 Next 에도 회고 액션에도 없음. **V3 의 다른 세 선행조건과 달리 기계화되지 않았다**), SS2a-06(Low).

**신규 결함 ID 부여**: 이 부트 실패는 프로젝트 관례상 **`SS2a-07`** 이다. `SS*`/`SI*`/`SR-*` 는 *우리 코드에서 우리가 스프린트 중/에 대해 발견한* 결함, `D-S1…D-S21` 은 레거시 정적분석 결함 레지스터(REQUIREMENTS-SPEC §1.2 소유). S2a 가 쓴 코드이므로 스프린트 접두사 + 다음 번호. **`D-S` 번호를 부여해서는 안 된다.**

trace 행이 추가되어야 할 산출물: `SPRINT-S2a-LOG.md` §3, `mapping/trace/requirements-trace-biztalk.csv`, `SPRINT-S2a-RETRO.md`(신규 액션 A7), `ADR-SND-021` Consequences. **단 행이 가리킬 테스트가 먼저 존재해야 한다** — `BarredNumbersTest` 는 Spring 을 거치지 않으므로 이를 회귀할 수 없고, 그러면 trace 행이 반증 불가능해진다.

### 4.7 Parity

**N/A.** S2a 는 레거시 화면 12(등록)·화면 10(삭제)의 **동작 대체**이며 바이트 단위 전문 동치성 대상이 아니다. 레거시가 구현하지 않은 규칙(D-S12 금지번호)을 새로 만들고, 레거시가 잘못한 동작(D-S1 삭제가 아무것도 지우지 않음)을 의도적으로 다르게 만든다 — **parity 를 주장하면 안 되는 슬라이스다.** 대체 증거는 `SenderNumberMapperIntegrationTest.aMaskedValueMatchesNothingAndReportsZero` 로, 레거시의 정확한 술어를 실제 DB 에 실행해 0행 반환을 단언한다.

---

## 5. 7차원 독립 재평가 (§4)

| 차원 | 가중 | Skill 4 (Leader) | Skill 5 (재평가) | Δ |
|------|-----:|-----------------:|-----------------:|---:|
| 완성도 | 20% | 92 | **80** | −12 |
| 추적성 | 15% | 96 | **90** | −6 |
| 보안 | 20% | 94 | **80** | −14 |
| 성능 | 10% | 70 | **65** | −5 |
| 가독성 | 15% | 95 | **95** | 0 |
| 표준 준수 | 10% | 95 | **85** | −10 |
| 테스트 커버리지 | 10% | 93 | **72** | −21 |
| **가중 총점** | | **91.4** | **81.9** | **−9.5** |

주요 감점 근거:
- **완성도 80** — 산출물이 기동하지 않았다. 12/13 태스크는 맞으나 배포 가능 상태가 아니었다.
- **추적성 90** — G2·G3 (HIGH 2건). 단 ADR 개정 감점은 **오히려 부당**했으므로(§4.6) 로그의 96 이 그 항목에서는 과소.
- **보안 80** — CR-S2a-02/-03 (문서화·시험된 죽은 통제 + 자기 전제를 스텁한 시험).
- **표준 준수 85** — 하네스 §3 이 의무화한 L1 Hook 미설치. 커밋 미실시.
- **테스트 커버리지 72** — §3 + 커버리지 게이트 4건 미달 + 부하 0/4 + E2E 인프라 부재. 산출 근거는 [test-report-4](../qa/test-report-4-senderno-S2a.md) §10.1.

### 5.1 자기 합리화 판정

**Δ 9.5 — 하네스 §4 임계(10점) 미달.** 자기 합리화 플래그 **없음.** (code-reviewer 단독 산정은 80.6, Δ 10.8 로 임계를 살짝 넘었으나, 추적성에서 로그가 자신을 과소평가한 항목을 반영한 통합 재평가는 81.9 로 임계 내에 든다.)

성격을 명확히 한다: **이는 Leader 가 결함을 은폐한 사례가 아니다.** 로그는 성능 70 을 정직하게 자책하고, SS2a-01~06 을 스스로 제기했으며, 사전 실패 시험과 미적용 DDL 을 명시했다. Δ 의 대부분은 **한 사건**에서 온다 — 기동하지 않는 산출물, 그리고 그것을 볼 수 있는 시험이 없다는 사실이 완성도·보안·테스트 커버리지 세 차원에 동시에 걸린 것. **그 사건은 스프린트 종료 후 발견되었으므로 평가 시점에는 알 수 없었다.**

**단, 재평가 81.9 는 90 임계 미달이다.** §4 의 환송 규칙("두 평가 모두 < 90")은 Skill 4 가 91.4 이므로 자동 발동하지 않으나, **§7 의 CVSS ≥ 7.0 → REJECT 는 발동한다.** 그리고 테스트 커버리지를 정직하게 72 로 놓으면 Skill 4 자신의 총점도 89.3 이 되어 **스프린트는 자기 인증 대신 재생성 루프에 진입했어야 했다.** 숫자가 아니라 그 임계 통과가 요점이다.

**7차원 평가를 한 칸 수정이 아니라 재실행할 것을 권고한다.**

---

## 6. DoD 점검 (§8)

| # | 항목 | 판정 | 근거 |
|:-:|------|:----:|------|
| 1 | 교차 검증 1회 이상 (다른 LLM 벤더) | **PASS** | [cross-validation-4.md](cross-validation-4.md) — qwen2.5-coder:7b, 로컬, egress 0. R1 회차 V-5 공백 종결 |
| 2 | CVSS ≥ 7.0 결함 0건 또는 리스크 수용 결재 | **FAIL** | 4건 (9.8/9.8/9.1/7.5) |
| 3 | 공급망 — SBOM + 금지 OSS 0건 | **PARTIAL** | SBOM PASS. 금지 목록 미정의, LGPL/BSD-4-Clause 법무 확인 필요 |
| 4 | 7차원 재평가 ≥ 90 | **FAIL** | 81.9 |
| 5 | 모든 리포트 작성 | **PASS** | §8 |
| 6 | PM 결재 G3 릴리즈 게이트 | **BLOCKED** | #2, #4 미충족 |

---

## 7. 판정

### 7.1 G3 릴리즈 게이트 — **REJECT**

CVSS ≥ 9.0 3건이 이 스프린트와 **같은 아티팩트**로 배포된다. **회전이 제거보다 먼저이며 필수다** — `.env` 와 `application.yml` 모두 git 이력에 있으므로 제거만으로는 아무것도 고쳐지지 않는다.

### 7.2 Sprint S2a 산출물 — **조건부승인**

REJECT 급 룰 위반 0건(매퍼 `${}` 0 · PII 평문 로그 0 · S2a 코드 시크릿 0 · `System.out` 0 · 금액 부동소수 0), 그리고 스프린트의 핵심 주장이 실재한다: 삭제는 진짜 행 이동이고, 중요한 행수는 각자의 호출 지점에서 검사되며, 무일치는 실제 PostgreSQL 에 대한 실행 가능한 회귀를 가진 409 다. D-S1 계열은 **구조적으로** 닫혔다 — `SenderNumberRef`, 단일 `String` 이력 시그니처, archive-before-delete.

**G3 전 종결 조건:**

| # | 조건 | 출처 |
|:-:|------|------|
| 1 | **컨텍스트 로드 시험 (Tier A + Tier B).** 보안 수정보다 **먼저** | §3.2, CR-S2a-01 |
| 2 | **CR-S2a-02 + CR-S2a-03 (한 쌍)** — 죽은 인가 계층과 그것을 스텁한 시험. 시험만 고치면 거짓 통과가 RED 로 바뀔 뿐 | code-review §3, SEC-S2a-06, XV4-02 |
| 3 | **CR-S2a-04 / XV4-04** — `DuplicateKeyException` → 409. FR-SNDC-004 는 현재 어디서도 강제되지 않고, 강제되면 500 을 반환 | code-review §3, cross-validation §4.1 |
| 4 | **커버리지 게이트 4건** — 특히 `auth.domain` 44% → 95% | §4.2 |
| 5 | **빌드 그린** — CSRF (R1 verdict 의 미이행 지시) | §4.1, SEC-S2a-17 |
| 6 | **SEC-S2a-07 / -08** — `/context` 감사 이벤트, 거부·실패 쓰기 감사 | audit §6 |
| 7 | **SEC-S2a-05** — 위협모델 T-I7 방향 정정. **G1 이 V3 를 적용하기 전에** | audit §5 |
| 8 | **SEC-S2a-14** — GRANT/REVOKE 블록을 V3 에; ADR-007 §2 개정. G1 선행조건 | audit §8 |
| 9 | **G2 / G3 추적성** — 롤백 통합시험, `FR-SND-012` trace 행 정정 | §4.6 |
| 10 | 로그 보존 **5년 vs 7년** 서면 정리 | audit §8 |

**S2b 이연 가능**: CR-S2a-05/-06/-07/-08/-09, SEC-S2a-10/-11/-12/-13, SS2a-05/-06 목적지 부여, 부하 시험, E2E 인프라 결정, 로그 정량 정정.

### 7.3 PM 결재 필요 사항

| # | 사안 | 결정 요청 |
|:-:|------|-----------|
| 1 | **CVSS 9.8 × 2 + 9.1 시크릿 노출** | 즉시 회전 승인 + 이력 정리(`git filter-repo`) 여부. 이력 재작성을 거부한다면 **두 시크릿을 영구 침해로 취급하는 예외를 임원 결재로** |
| 2 | **CI 상태 확정** | `main` 이 자기 보안 게이트에서 RED 인가, CI 가 돌지 않는가. 후자라면 왜인가 |
| 3 | **7차원 재실행** | 81.9 / (정직한 커버리지 반영 시 Skill 4 도 89.3) — 재생성 루프 진입 여부 |
| 4 | **금지 OSS 라이선스 목록 확정** | 기준 없는 DoD 항목. LGPL-2.1 / BSD-4-Clause 2건 판단 |
| 5 | **SS2a-03** | AMB-S09 ruling B 의 `AOA_ADMIN` 암호문 노출 — 스프린트가 요청한 PM 확인, 미이행 |
| 6 | **로그 보존 5년 vs 7년** | ADR-006 과 하네스 §9 의 모순 |

---

## 8. 산출물

| 산출물 | 경로 |
|--------|------|
| 코드 리뷰 리포트 | [reviews/code-review-sprint-S2a.md](code-review-sprint-S2a.md) |
| 교차 검증 리포트 (CVSS) | [reviews/cross-validation-4.md](cross-validation-4.md) |
| 보안 감사 리포트 | [security/audit-S2a.md](../security/audit-S2a.md) |
| QA 테스트 리포트 | [qa/test-report-4-senderno-S2a.md](../qa/test-report-4-senderno-S2a.md) |
| Parity 리포트 | **N/A** — §4.7 사유 |
| 종합 판정 | 본 문서 |

### 8.1 이 회차가 확인한 것 중 가장 중요한 한 가지

세 곳에서 **초록 신호가 증거로 채택되었다** — 애플리케이션을 부팅할 수 없는 스위트(§3), 아무도 단언하지 않는 롤백(G2), 다른 시험을 가리키는 trace 행(G3). 셋 다 이 스프린트가 자신의 대표 결함 부류로 명명한 것과 같은 실패다:

> **실제로 하지 않은 일에 대해 성공을 보고하는 동작.**

D-S1 은 아무것도 지우지 않고 200 을 반환했다. 이 스프린트는 그것을 코드에서 훌륭하게 고쳤고, 같은 형태가 **검증 층**에 남아 있다.
