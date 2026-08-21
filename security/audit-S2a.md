# 보안 감사 리포트 — Sprint S2a (발신번호 등록 · 삭제)

> **Auditor**: security-auditor (Validation Team Leader) · **Skill**: 05 §1[C] / §9
> **Date**: 2026-08-21 · **Target**: Sprint S2a write path + 릴리즈 아티팩트 전체
> **Tree**: `main` @ `34b4254` + 미커밋 변경 1건 (`BarredNumbers.java`)
> **판정**: **REJECT (G3 릴리즈 게이트)** — CVSS ≥ 9.0 3건
> **단, 3건 모두 S2a 산출물이 아니다.** 라우팅은 §10 참조.

---

## 1. 감사 범위

| 포함 | 제외 |
|------|------|
| `SenderNumberController` / `WriteService` / `BarredNumbers` / 예외 핸들러 | 로그인 슬라이스 로직 (별도 감사) |
| `SenderNumberMapper.xml`, `V3__senderno_archive.sql` | 알림톡 발송 경로 (audit-alimtalk.md) |
| `application.yml`, `application-local.yml`, `.env`, `.gitignore` | 톡전송 내역 (audit-3-report-talk.md) |
| 보안 Hook L1 / L2 / L3 실효성 | |
| 프론트 등록·삭제 다이얼로그 | |

읽기 전용 감사. 감사 중 수정한 파일 없음.

### 1.1 CVSS 산정 원칙 — PR 값에 대하여

운영자 전용 엔드포인트는 **PR:H** 로 채점한다. CVSS v3.1 의 PR:H 정의는 "대상 구성요소에 대한 상당한(관리자급) 제어를 제공하는 권한" 이며, 이 콘솔에서 `OPERATOR` 롤이 곧 관리자다 — 설계상(FR-TEN-003) 모든 이용기관의 발신번호를 등록·삭제할 수 있다. 이를 PR:L 로 채점하면 위협모델이 **의도적으로 부여한** 권한 때문에 모든 쓰기 경로 결함이 약 1.5점 부풀려진다. PR 선택이 등급을 바꾸는 항목은 두 값을 함께 적었다.

프로세스·컴플라이언스 통제는 CVSS 로 표현할 수 없다. 해당 항목은 벡터를 만들어 붙이지 않고 **N/A** 로 적고 우선순위만 부여한다.

---

## 2. OWASP Top 10 (2021) 점검

| # | 항목 | 판정 | 근거 |
|---|------|------|------|
| A01 | Broken Access Control | **WARN** | 3계층 인가는 실재하고 각각 거부를 시험한다 — 라우팅 [`SecurityConfig.java:136`](../src/main/java/com/webcash/iris/auth/config/SecurityConfig.java#L136), 메서드 `@PreAuthorize` ([`SenderNumberController.java:153`](../src/main/java/com/webcash/iris/biztalk/api/SenderNumberController.java#L153), `:190`), 서비스 `requireOperator()`. **다만 4번째 계층은 무효** → SEC-S2a-06 |
| A02 | Cryptographic Failures | **FAIL** | SEC-S2a-01/02/03 (시크릿 커밋) + SEC-S2a-05 (키 관리 부재) |
| A03 | Injection | **PASS** | `src/main/resources/mybatis/` 전체에 `${}` **0건**. 발신번호 6개 statement 전부 `#{}` 바인딩. 동적 `ORDER BY`·테이블명·문자열 연결 없음. T-T3 완화 확인 |
| A04 | Insecure Design | **WARN** | SEC-S2a-05 (T-I7 방향 오류), SEC-S2a-10 (기관 존재 검증 없음) |
| A05 | Security Misconfiguration | **FAIL** | SEC-S2a-15 (`require-https` fail-open), SEC-S2a-12 (local 프로파일 PII DEBUG 로그) |
| A07 | Identification & Auth Failures | **FAIL** | SEC-S2a-03 — OTP 비밀을 보호하는 AES 키가 소스에 있으면 2차 인증이 무력화된다 |
| A08 | Software & Data Integrity | **PASS** | Mass assignment 구조적 차단. 등록 요청 레코드에 `institution`·`authNo`·`registeredBy`·`registeredAt` 없음, 삭제 요청에 `number` 없음. 덮어쓸 필드가 존재하지 않는다 |
| A09 | Logging & Monitoring Failures | **FAIL** | SEC-S2a-07 (감사 없는 읽기), SEC-S2a-08 (실패·거부 쓰기 무기록), SEC-S2a-16 (감사 불변성 미강제) |
| A10 | SSRF | **N/A** | 이 슬라이스에 외부 호출 없음 |

### 2.1 CSRF

`SecurityConfig.java:162-178` — 활성, `CookieCsrfTokenRepository.withHttpOnlyFalse()`, `POST /api/auth/login` 만 예외. 두 쓰기 엔드포인트 모두 `POST` 로 보호 범위 안. **구성은 정확하다.** 검증 공백은 SEC-S2a-17.

---

## 3. 시크릿 / 자격증명 점검 — **FAIL**

### SEC-S2a-01 · CRITICAL · CVSS **9.8** · `AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H`

**`.env` 가 git 에 추적되고 있으며, 파일 자신은 추적되지 않는다고 적혀 있다.**

독립 검증:

```
$ git ls-files --error-unmatch .env
.env                                  ← 추적됨
$ grep -n "env" .gitignore
(출력 없음)                            ← .gitignore 에 .env 항목 없음
$ head -6 .env
# ⚠ 이 파일은 커밋되지 않는다 (.gitignore). 실제 자격증명이 들어 있다.
#   Git-ignored; holds real credentials.
```

두 진술은 동시에 참일 수 없고, 거짓인 쪽은 첫 번째다. 파일은 `IRIS_DB_PASSWORD`(:28)와 `IRIS_OTP_SECRET_KEY`(:48)를 담고 있으며, **자기 헤더가 "실제 자격증명" 이라고 선언한다.**

`PR:N` 근거 — 자격증명 획득에 DB 권한이 전혀 필요 없다. 저장소 읽기 권한만 있으면 된다. 모든 clone·fork·CI 아티팩트·빌드 캐시가 이 값을 보유한다.

도입 시점: `0acfb39` (S2a 이전). **S2a 의 결함이 아니다.**

### SEC-S2a-02 · CRITICAL · CVSS **9.8** · `AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H`

**DB 자격증명 3줄이 하드코딩 기본값을 그대로 갖고 있다.**

| 줄 | 값 |
|----|-----|
| [`application.yml:61`](../src/main/resources/application.yml#L61) | `url: ${IRIS_DB_URL:jdbc:postgresql://136.85.16.4:5432/kakaobiztalkdev}` |
| `:62` | `username: ${IRIS_DB_USERNAME:biztalk_user}` |
| `:63` | `password: ${IRIS_DB_PASSWORD:biztalk123}` |

같은 파일 46-60행이 정반대를 상세히 적고 있다 — *"기본값 없음 / no defaults, deliberately… **2026-08-19 정정**: 이 세 줄에는 접속 주소와 자격증명이 그대로 박혀 있었다… 기본값을 두지 않으므로 환경변수가 없으면 **기동에 실패한다**. 그것이 의도다."* **정정은 문서화되었고 실행되지 않았다.**

자격증명 노출과 **별개인 두 번째 피해**가 있고, 그것이 주석이 지목한 바로 그 문제다: fallback 이 fail-closed 기동을 파괴한다. `IRIS_DB_PASSWORD` 를 빼고 배포하면 실패하지 않고 **조용히 `kakaobiztalkdev` 에 `biztalk_user` 로 붙는다.**

> **실측 증거.** 이 감사 중 환경변수 없이 `mvn spring-boot:run` 을 실행했다. 애플리케이션은 기동했고 `HikariPool-1 - Added connection` 을 남겼다. 즉 이 결함은 이론이 아니라 **관측되었다.**

`.env:28` 의 값과 **동일**함을 확인했다 — 회전 대상은 하나의 자격증명 집합이다.

### SEC-S2a-03 · CRITICAL · CVSS **9.1** · `AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:N`

**OTP 비밀을 보호하는 AES-256-GCM 키가 fallback 기본값으로 커밋되어 있다.**

[`application.yml:235`](../src/main/resources/application.yml#L235) — `secret-key: ${IRIS_OTP_SECRET_KEY:3NUSPSH/zY/…=}`

218-234행에 *"NO DEFAULT: the application must fail to start rather than encrypt credential material under a key that is published in source"* 라고 적고, 12줄 뒤에 *"2026-08-19 정정: 바로 위 세 줄이 'NO DEFAULT' 라고 적고 있는데도 기본값이 들어 있었다"* 라고 적은 다음, **여전히 기본값이 있다.**

`.env:48` 의 키와 **값이 다르다.** 즉 커밋된 AES 키는 **2개**이며 둘 다 회전 대상이다. 어느 쪽이 실제 사용되는지는 환경변수 로딩 순서에 달려 있고, 그 불확실성 자체가 사고 대응을 어렵게 한다.

영향은 2차 인증 무력화 — 이 키로 저장된 OTP 비밀을 복호화할 수 있으므로, 키와 DB 읽기 권한을 함께 가진 행위자는 임의 계정의 유효 TOTP 코드를 생성한다. **SEC-S2a-02 가 같은 파일에서 그 DB 읽기 권한을 제공한다.** `cat application.yml` 한 번으로 데이터베이스와 2차 인증 키가 함께 나온다.

### SEC-S2a-04 · **우선순위 HIGH** · CVSS N/A (프로세스 통제)

**3단계 시크릿 Hook 이 세 계층 전부에서 작동하지 않는다.** SEC-S2a-01~03 의 근본 원인이며, L1/L2 는 감사자 자신의 책임 영역이므로 명시한다.

| 계층 | 상태 | 증거 |
|------|------|------|
| **L1** pre-commit | **미설치** | `.git/hooks/pre-commit` 부재. `hooks/pre-commit-gitleaks.sh` 는 존재하지만 헤더가 요구하는 수동 symlink 를 아무도 만들지 않았다 |
| **L2** CI | **규칙은 정확, 미집행** | `ci.yml:236` "Reject secret values in configuration resources" 의 패턴 ②가 바로 `${VAR:default}` 를 노린다 |
| **L3** prod-gate | 체크리스트 존재, 항목 부족 | SEC-S2a-15 |

L2 규칙을 현재 트리에 그대로 실행한 결과:

```
src/main/resources/application.yml:63:    password: ${IRIS_DB_PASSWORD:biztalk123}
src/main/resources/application.yml:235:      secret-key: ${IRIS_OTP_SECRET_KEY:3NUSPSH/…=}
→ exit 1  ::error:: Secret value or defaulted secret in a configuration resource (SEC-A02)
```

**규칙은 옳고 위반은 실재한다. 따라서 `main` 은 자기 보안 게이트에서 RED 이거나, CI 가 `main` 에서 돌지 않고 있다.** (`gh` CLI 부재로 Actions 이력을 조회할 수 없어 둘 중 어느 쪽인지는 이 감사에서 확정하지 못했다. 로컬 `main` 은 `origin/main` 과 동일하므로 푸시는 되어 있다.)

**규칙 공백 (false negative — 감사자의 역할상 명시한다):**
- `ci.yml` 의 SEC-A02 규칙은 `find src/main/resources -name 'application*.yml'` 만 스캔한다. **저장소 루트의 `.env` 는 범위 밖이다.** 이것이 SEC-S2a-01 이 들어온 경로다.
- `hooks/gitleaks.toml` 은 `useDefault = true` 뿐이고 allowlist 가 없다. 기본 룰셋은 `biztalk123` 같은 저엔트로피 값을 신뢰성 있게 잡지 못한다.

**gitleaks 실측 — 탐지가 역전되어 있다.** `gitleaks detect --config hooks/gitleaks.toml` (22 커밋): **8건 검출, 전부 오탐, 실제 시크릿 0건 검출.**

| 검출 8건 | 판정 |
|---|---|
| `SecretCipherTest.java:31`, `TotpVerifierTest.java:33` — `MFRGGZDFMZTWQ2LKNNWG23TPOBYXE43U` | **오탐.** RFC 6238 공개 시험 벡터 (`"12345678901234567890"` 의 Base32) |
| `AtkMaskerTest`, `InstitutionServiceTest`, `InstitutionWriteAuthorizationTest`, `OtpRegisterPage.test.tsx`, `SecretCipherDriver.java`, `AuthenticationServiceTest` | **오탐.** 전부 테스트 픽스처 상수 |
| `.env`, `application.yml` 의 실제 자격증명 | **미검출** |

시험 벡터에 8번 경보하고 실제 자격증명 3건을 놓치는 구성은 통제로서 음의 가치를 가진다 — 운영자가 경보를 무시하도록 훈련시킨다.

**조치 (순서 불변, 어느 것도 선택 아님):**
1. **회전 먼저, 제거 나중.** 두 파일 모두 git 이력에 있으므로 삭제는 아무것도 고치지 못한다. 새 키 생성(`openssl rand -base64 32`) 후 저장된 모든 OTP 비밀 **재암호화**, DB 비밀번호 교체.
2. `git rm --cached .env`; `.gitignore` 에 `.env`, `.env.*` 추가; `.env.example` 을 키 이름만으로 제공.
3. `application.yml:61-63`, `:235` 의 기본값을 제거해 `${IRIS_DB_URL}` 형태로.
4. L1 을 저장소 관리 `core.hooksPath` 로 설치 (개발자별 symlink 금지).
5. L2 의 SEC-A02 파일 집합을 `.env*`, `*.properties`, 루트 YAML 로 확장. gitleaks 에 `IRIS_DB_PASSWORD=` / `IRIS_OTP_SECRET_KEY=` 전용 룰 추가 + 시험 벡터 allowlist. 게이트를 머지 필수로.
6. 이력 정리(`git filter-repo`) 또는 — 내부 전용 저장소이고 이력 재작성을 거부한다면 — 예외를 임원 결재로 기록하고 두 시크릿을 **영구 침해**로 취급.

---

## 4. PII / 민감정보 처리 — **PASS** (S2a 범위)

**발신번호는 감사 레코드·로그·예외 메시지·HTTP 오류 본문 어디에도 도달하지 않는다. ADR-SND-019 는 지켜진다.** 주장이 하중을 받는 항목이므로 경로별로 확인했다.

| 싱크 | 증거 |
|------|------|
| 감사 detail | `:151` `"1건 등록 … 사유 " + reason.length() + "자"`; `:249` `targets.size() + "건 삭제"` — 건수와 길이뿐 |
| 감사 actor/target | `principal.email()` + 기관코드 |
| 예외 메시지 | `SenderNumberDuplicateException:37`, `NotLiveException:42` 정적 문자열. 입력값 반향 없음 |
| HTTP 오류 본문 | `{field, message}` 만. 제출값 없음 |
| Bean validation 400 | `AuthExceptionHandler:131` 이 `getFieldErrors()` → `field` + `getDefaultMessage()` 로만 매핑. **누출 가능성이 있던 지점** — `MethodArgumentNotValidException.getMessage()` 는 `rejected value [01012345678]` 을 품으며, 이것이 `GlobalExceptionHandler:177` 의 `log.error("UNHANDLED", e)` 로 떨어졌다면 번호가 ERROR 로그에 남았을 것이다. 떨어지지 않는다 |
| 로그 | 핸들러는 필드명만 기록. `RequestLoggingFilter:90-104` 는 method + `getRequestURI()` — 쿼리스트링 제외. 삭제가 `POST` 인 이유가 이것이다 |
| 프론트 | `console.*` 없음, `dangerouslySetInnerHTML` 없음, `localStorage` 없음. 다이얼로그는 ref 만 전송 |

**구조적 위험 1건 (채점하지 않음)**: `SenderNumberRef` 는 `record` 이므로 생성된 `toString()` 에 원본 번호가 들어간다. 현재 이를 로깅하는 경로는 없으나 향후 `log.debug("… {}", ref)` 한 줄로 조용히 누출된다. `toString()` 을 토큰 반환으로 재정의 권고.

### SEC-S2a-12 · LOW · CVSS **3.3** · `AV:L/AC:L/PR:L/UI:N/S:U/C:L/I:N/A:N`

`application-local.yml:56-57` 의 `com.webcash.iris: DEBUG` 가 MyBatis 매퍼 로거를 포함하므로, **local 프로파일에서 등록·삭제되는 모든 발신번호가 평문으로 로그에 남는다** (`ENCRYPT(?)` 와 바인딩 파라미터가 함께 출력). ADR-005 §2, NFR-SEC-LOG-D01 위반. 개발 전용·프로파일 게이트이므로 `AV:L`·저점수지만, 커밋된 파일이라 다른 곳의 정본 템플릿처럼 읽힌다는 점이 문제다 — `application.yml:299-306` 은 바로 이 이유로 `com.webcash.iris.auth: INFO` 를 고정해 두었는데 local 프로파일이 패키지 전체에 대해 그 예방을 폐기한다.

**조치**: `com.webcash.iris.biztalk.api: DEBUG` 로 좁히거나 local 프로파일에 `com.webcash.iris.biztalk.infra.db: INFO` 추가. `*.infra.db` 로거의 DEBUG 를 거부하는 CI 규칙 신설.

### SEC-S2a-13 · LOW / 정보성 · CVSS **2.2** · `AV:N/AC:H/PR:H/UI:N/S:U/C:L/I:N/A:N`

D-S16 은 원장의 운영자 이메일을 `ENCRYPT()` 로 감싸는데, **같은 이메일이 보존기간이 더 긴 감사 테이블에는 평문으로** 들어간다 (`IRIS_AUTH_AUDIT.ACTOR`, 5년). 아마도 옳은 선택이다 — 행위자를 읽을 수 없는 감사 추적은 추적이 아니고, ADR-006 이 별도 접근통제를 가진 append-only 저장소를 고른 이유가 그것이다. 다만 T-I5 의 완화가 "일관된 신원 처리" 로 무조건 서술되어 있고 S2a 이후 두 저장소는 **의도적으로 불일치**한다. **ADR-006 또는 ADR-005 §4.3 에 예외를 명시**해, 다음 독자가 5년 저장소의 평문 이메일을 발견하고 D-S16 을 재개하지 않게 할 것.

---

## 5. 암호화 / 키 관리 — **FAIL**

### SEC-S2a-05 · MEDIUM 단독 **6.5** / **7.5 HIGH (연쇄 시)** · `AV:N/AC:L/PR:L→N/UI:N/S:U/C:H/I:N/A:N`

**지시받은 질문에 대한 직답: 키는 어디에서도 관리되지 않는다. `ENCRYPT` 는 미해결 DB 함수다.** ADR-005 §4.3 은 *"decrypt()/masking() 정의를 확보·보관 — 버전관리 밖의 프로젝트 의존성"* 을 여전히 미체크 상태로 두고 있고, ADR-007 §4.2 는 *"PII 키 회전은 이 프로젝트 통제 밖"* 을 수용한다. 발신번호·`RGSR_NM`·(S2a 에서 새로) 운영자 이메일을 보호하는 함수에 대해 **키 관리자·회전 주기·재키잉 절차·정의 자체가 저장소에 없다.**

**본질적 발견은 위협모델의 T-I7 방향이 뒤집혀 있고, S2a 의 DDL 이 그 뒤집힌 쪽에 의존한다는 것이다.**

`threat-model-SENDERNO.md` §3.4 는 T-I7(작은 전화번호 키스페이스에 대한 사전 공격)을 *"비결정적 분기를 택한 경우에만"* 으로 한정한다. 이는 반대다. 사전 공격은 **결정적** 암호화의 성질이며, `V3__senderno_archive.sql:56-65` 는 결정성을 **요구**하고 아니면 예외를 던져 진행을 거부한다 — `:163` 의 `CREATE UNIQUE INDEX … (DP_NO)` 가 의미를 가지려면 그래야 하기 때문이다.

S1-01 사전검증이 통과하면:

1. `ENCRYPT` 는 한국 번호 약 10⁸~10¹⁰ 도메인 위의 결정적 사상이 되고,
2. 애플리케이션 DB 계정이 임의 입력에 `ENCRYPT()` 를 호출할 수 있다 — `insertLedger` 가 `#{command.number}` 를 그대로 넘긴다 (`SenderNumberMapper.xml:191,194-199`). 즉 **선택 평문 오라클**,
3. 따라서 질의 권한을 가진 주체는 **키 없이** 레인보우 테이블로 원장·이력·신규 아카이브의 모든 `DP_NO` 를 복원하고,
4. unique index 는 암호문 동일성을 **기관 간 동일성 오라클**로 만든다.

위협모델 §3.4 의 자기 주석 — *"전화번호는 키스페이스가 작아 비키드 해시라면 자명하게 역산된다"* — 은 정확한 분석을 **틀린 분기에** 적용한 것이다. 작은 도메인 위의 결정적 암호화는 함수가 호출 가능한 한 키가 필요 없으므로 같은 약점을 가진다.

`PR:L` 은 `ENCRYPT()` 호출 가능한 저권한 DB 주체를 요구한다. **SEC-S2a-01/02 가 있으면 그 요구가 사라져** `PR:N` → **7.5** 가 된다.

**조치 — G1 이 V3 를 적용하기 전에, 후가 아니다:**
1. `threat-model-SENDERNO.md` §3.4 정정: T-I7 은 **결정적** 분기에 적용된다. 스파이크의 두 결과 **모두** 작업을 요구하며, 현재 설계는 결정성을 안전한 답으로 취급한다.
2. insert 경로 외에는 애플리케이션 계정에서 `ENCRYPT()` `EXECUTE` 회수, 또는 ADR-SND-018 의 대안 분기대로 keyed blind index(HMAC, ADR-007 하 애플리케이션 보관 키)로 유일성 이전.
3. DB 측 보관이더라도 `security/runbook-key-rotation.md` 에 PII 키의 **지정 관리자와 회전 주기**를 명기. "프로젝트 통제 밖" 은 리스크 수용이며, 전자금융감독규정 하의 리스크 수용에는 체크박스가 아니라 소유자와 날짜가 필요하다.

---

## 6. 감사 로그 (Audit Log)

### SEC-S2a-08 · LOW · CVSS **2.7** (PR:L → 4.2) · `AV:N/AC:L/PR:H/UI:N/S:U/C:N/I:L/A:N`

**실패·거부된 쓰기는 전혀 감사되지 않는다. T-R3 는 완화가 아니라 미완화다.**

`record(...)` 가 두 경로 모두에서 `return` 직전 마지막 문장이다. 모든 실패는 그보다 먼저 이탈하며 아무것도 쓰지 않는다.

| 실패 | 위치 | 감사 |
|------|------|------|
| 운영자 아님 | `:303` `AccessDeniedException` | 없음 |
| 검증 거부 (번호/사유/설명/금지번호) | `:380`, `:385`, `:410`, `:415`, `:441` | 없음 |
| 중복 409 | `:134` | 없음 |
| live 행 없음 409 | `:213`, `:218` | 없음 |
| 이력 insert 실패 → 전체 롤백 | `:281` | 없음 |

위협모델은 T-R3 에 대해 정반대를 주장한다 — *"실패한 쓰기가 시도 증거를 남기지 않음 → 감사를 `REQUIRES_NEW` 로 기록, 업무 롤백에서 생존"*. `AuditService.record` 는 실제로 `REQUIRES_NEW` 다(`AuditService.java:53`) — **전파 설정은 옳고 호출이 실패 경로에서 도달되지 않을 뿐이다.** ADR-SND-019 는 DENIED 이벤트를 읽기의 열거 탐지 신호로 규정하는데, 쓰기 경로에는 그 신호가 하나도 없다.

**조치**: `register`·`delete` 본문을 `catch` 로 감싸 재던지기 전에 `Outcome.DENIED` 기록. `REQUIRES_NEW` 가 이미 롤백 생존을 보장한다 — 그 전파를 고른 이유가 바로 이것이다.

### SEC-S2a-07 · LOW · CVSS **2.7** (PR:L → 4.3) · SS2a-06 재평가

심각도 **Low 에 동의하고 근거를 기각하며 조치를 상향한다.**

독립 평가: `/context` 는 `@RequestParam String institution` 을 받아 `institutions.findByCode()` 를 호출하고 `(code, name)` 을 돌려준다 — **컨트롤러에서 `TenantContext` 를 전혀 건드리지 않는 유일한 메서드다.** `C:L` — 노출 대상은 고객사 명부이며 상업적으로 민감하나 개인정보보호법상 개인정보는 아니다. `InstitutionAdminMapper.xml:145-159` 가 `IS_STTS <> 'D'` 로 필터하고 미스는 깔끔한 404 이므로, **200/404 가 기관 namespace 에 대한 깨끔한 존재 오라클**이 되며 스로틀도 기록도 없다.

스프린트는 *"이름은 발신번호가 아니므로 ADR-SND-019 가 자명히 적용되지 않는다"* 로 Low 를 정당화했다. 이는 틀린 규칙에서 추론한 것이다. ADR-SND-019 의 금지는 감사 레코드에 *번호를 쓰는* 것이고, 그 **결정**은 읽기를 요청 단위로 감사한다는 것이며, 명시된 목적은 탐지적이다 — *"기관코드를 훑는 DENIED 이벤트 시퀀스는 열거 시도이며, RESIDUAL-S01 이 인가만을 유일한 장벽으로 남긴 상황에서 이 슬라이스가 가장 볼 수 있어야 하는 신호"*. 지배 요구사항은 ADR-006 / NFR-OPS-AUDIT-D01 이고 결과는 구체적이다: **`/context` 는 이 화면에서 운영자가 도달할 수 있는 유일한 무기록 읽기다.** list·detail·두 쓰기 모두 감사한다. 침해된 운영자 계정의 행위자는 추적에 절대 나타나지 않는 엔드포인트로 기관 namespace 를 열거할 수 있고, 이는 슬라이스의 기밀성 태세 전체가 의존하는 보상통제의 맹점이다.

**조치**: 스프린트의 *"institution 슬라이스 소유자와 협의"* 는 불충분. **G3 전에 `ACTION_INSTITUTION_CONTEXT_VIEW` 감사 이벤트 추가.** 4줄이며 유일한 무기록 읽기를 닫는다.

### SEC-S2a-16 · MEDIUM · CVSS **4.3** · `AV:N/AC:H/PR:H/UI:N/S:U/C:N/I:H/A:N`

**감사 append-only 가 매퍼 statement 의 부재로만 강제된다.** 설계 의도는 옳고 잘 적혀 있다 — *"append-only 는 애플리케이션 코드가 아니라 DB 권한으로 강제해야 한다. 침해된 애플리케이션도 자기 추적을 지울 수 없도록"* (`V1:88-95`) — 그리고 그것을 할 권한은 주석 처리되어 있다(SEC-S2a-14). 남은 것은 `AuditMapper.xml` 에 `UPDATE`/`DELETE` 가 정의되지 않았다는 사실뿐이며, 이는 실수를 막고 공격자를 막지 못한다. 침해된 앱 계층은 살아 있는 DB 연결을 쥐고 임의 SQL 을 던진다. `I:H` — 감사 추적은 권한 부여형 쓰기 경로의 유일한 책임추적 통제(T-E3)이고, 그 무결성이 전자금융감독규정 로그 무결성이 요구하는 것이다.

---

## 7. 보안 Hook 3 단계 통합

§3 SEC-S2a-04 참조. 요약: **L1 미설치 · L2 규칙 정확하나 미집행 · L3 항목 부족.** 3계층 모두 실효 없음.

---

## 8. 규제 / 컴플라이언스 (§9)

| 규제 | 항목 | 판정 | 근거 |
|------|------|------|------|
| **전자금융감독규정** | 접근통제 | **WARN** | SEC-S2a-14 |
| | 암호화 (전송) | **WARN** | SEC-S2a-15 |
| | 암호화 (저장) | **FAIL** | SEC-S2a-05 |
| | 로그 보관 | **PASS (불일치 해소 필요)** | ADR-006 은 PM 결정(2026-08-14)으로 **5년**, 전자금융거래법 제22조 부합. 그런데 하네스 §9 는 **7년**을 적는다. 5년은 법상 방어 가능하지만 표준이 달리 말하고 있으므로 **G3 전 서면 정리 필수** — 감사인이 기록에 모순을 남길 수 없다 |
| | 로그 무결성 | **WARN** | SEC-S2a-16 |
| **개인정보보호법** | 수집·저장·파기 | **PASS** | §4 참조. 파기는 `KKB_DPNO_ARCV` + `DEL_DT`/`DEL_ID`/`REASON` — 행위자와 사유를 갖춘 적절한 파기 기록. SS2a-05(HIS/LDGR DDL 미확보, 타입 추론)는 DBA 선행조건으로 잔존 |
| **신용정보법** | — | **N/A** | 이 슬라이스에 신용정보 없음 |
| **ISMS-P** | 2.7.1 암호정책 / 2.7.2 암호키관리 | **FAIL** | SEC-S2a-05. 컬럼 암호화에 대한 키 관리 절차 부재 |
| | 2.9.4 로그 및 접속기록 점검 | **WARN** | SEC-S2a-07, -08 |
| **금융보안원 가이드** | 침해 대응 | **WARN** | 커밋된 시크릿에 대한 회전 런북 부재 |
| **PCI-DSS** | — | **N/A** | 카드 데이터 없음 |
| **공급망** | SBOM | **PASS** | CycloneDX 1.5, 128 컴포넌트, `target/classes/META-INF/sbom/application.cdx.json` 자동 생성 |
| | 금지 OSS 라이선스 | **WARN** | §8.1 |
| **메시지 무결성** | 전문 위변조 | **N/A** | 이 슬라이스는 전문을 다루지 않음 |
| **키 관리** | KMS / HSM | **FAIL** | SEC-S2a-05. KMS·HSM 미적용, 키 관리자 미지정 |

### 8.1 공급망 — 라이선스

128 컴포넌트 라이선스 분포: Apache-2.0 96 · MIT 11 · EPL-2.0 8 · BSD-3-Clause 6 · EPL-1.0 3 · **LGPL-2.1-or-later 1** · GPL-2.0-with-classpath-exception 1 · BSD-4-Clause 1 · BSD-2-Clause 1.

AGPL·SSPL·순수 GPL **0건** — 명백한 금지 라이선스는 없다. 다만 **금지 목록 자체가 이 저장소에 정의되어 있지 않아** "금지 OSS 0건" 을 기계적으로 판정할 수 없다. DoD 항목이 판정 기준 없이 존재한다.

- **LGPL-2.1-or-later 1건** — 동적 링크 시 통상 허용이나 다수 금융기관이 제약한다. 법무 확인 필요.
- **BSD-4-Clause 1건** — 광고 조항(advertising clause) 포함. 배포물 고지 의무가 발생한다.
- GPL-2.0-with-classpath-exception — Java 생태계 표준(classpath 예외), 문제 없음.

**조치**: `docs/design/` 에 금지 라이선스 목록을 ADR 로 확정하고, LGPL/BSD-4-Clause 2건에 대해 법무 판단을 기록. CI 에 라이선스 게이트 추가.

### SEC-S2a-14 · **우선순위 MEDIUM — G1 선행조건** · CVSS N/A (컴플라이언스/배포 통제)

**결합된 두 공백.** 첫째, ADR-007 의 권한 카탈로그는 쓰기 경로보다 앞서 작성되어 이제 코드와 모순한다 — ADR-007 §2 는 최소권한을 *"8개 메시지 테이블 `SELECT`, `decrypt()`/`masking()` `EXECUTE`, 감사 저장소 `INSERT`. **업무 테이블에 `UPDATE`/`DELETE` 없음**"* 으로 정의하는데, S2a 는 세 테이블 `INSERT` **와 `KKB_DPNO_LDGR` `DELETE`** 를 요구한다. ADR-002 의 읽기전용 의도를 DB 보증으로 만들기 위해 ADR-007 이 의도적으로 보류한 바로 그 권한이다. 그리고 `V3__senderno_archive.sql` 에는 **`GRANT` 문이 하나도 없다.**

둘째, `V1__auth_session_audit.sql:115-127` 의 권한 부여가 **전부 주석 처리**되어 있다 — `GRANT INSERT ON IRIS_AUTH_AUDIT` 와 `-- 명시적 금지: UPDATE, DELETE` 포함. V1 은 자동 적용 대상도 아니다. 즉 최소권한 태세는 모든 환경에서 **설정이 아니라 문서**다.

**조치**: ADR-007 §2 를 개정해 쓰기 경로의 필요 권한과 예외의 한계(세 테이블, 명명된 statement)를 명시. `GRANT`/`REVOKE` 블록을 V3 에 실행 가능한 DDL 로 추가 (`REVOKE UPDATE, DELETE ON IRIS_AUTH_AUDIT` 포함). G1 항목화 — 앱 계정이 아카이브에서도 `DELETE` 할 수 있다면 아카이브 테이블은 무의미하다.

### SEC-S2a-15 · **우선순위 MEDIUM — prod-gate 항목** · CVSS N/A (배포 구성)

`application.yml:282` `require-https: ${IRIS_REQUIRE_HTTPS:false}` — **fail-open 기본값.** local 개발에는 옳고 `SecurityConfig.java:206` 이 활성 시 `requiresSecure()` + HSTS 를 적용한다. 그러나 `IRIS_REQUIRE_HTTPS=true` 를 잊은 운영 배포는 로그인과 OTP 를 포함한 콘솔 전체를 평문으로 서비스하며 **기동 시 아무 불평도 하지 않는다.** 같은 파일이 훨씬 덜 심각한 실수에 대해 두 번(`alimtalk.environment` `:127-133`, `dispatch.enabled` `:139-154`) 의도적으로 fail-loud 를 택한 것과 대비된다. `.env:56-57` 이 커밋된 파일에서 `IRIS_REQUIRE_HTTPS=false`·`IRIS_COOKIE_SECURE=false` 를 설정한다는 점도 함께 기록한다.

**조치**: local 프로파일이 아니면 `require-https=true` 를 기동 시 단언 — 파일이 이미 두 번 쓴 fail-loud 패턴과 동일하게. `hooks/prod-gate-checklist.md` 에 "실행 프로세스에서 IRIS_REQUIRE_HTTPS=true 확인" 추가 (L3 책임).

---

## 9. 발견 사항 종합

| ID | 위치 | CVSS | 등급 | 요약 | S2a 산출물? |
|----|------|-----:|------|------|:---:|
| SEC-S2a-01 | `.env:26-28,48` | **9.8** | CRITICAL | 실제 자격증명 담은 `.env` 가 git 추적 중, 파일은 아니라고 주장 | ✗ |
| SEC-S2a-02 | `application.yml:61-63` | **9.8** | CRITICAL | DB 자격증명 하드코딩 기본값, fail-closed 기동 파괴 | ✗ |
| SEC-S2a-03 | `application.yml:235` | **9.1** | CRITICAL | OTP AES-256-GCM 키 커밋 (`.env` 와 합쳐 키 2개) | ✗ |
| SEC-S2a-05 | `V3:56-65,163` + mapper | 6.5 → **7.5** | MED → HIGH | 결정적 `ENCRYPT` 사전공격; T-I7 방향 오류; 키 관리 부재 | △ DDL |
| SEC-S2a-16 | `AuditMapper.xml`, `V1:88-95` | 4.3 | MEDIUM | 감사 불변성이 권한이 아닌 statement 부재로만 강제 | ✗ |
| SEC-S2a-04 | Hook L1/L2/L3 | N/A | **HIGH** | 3계층 시크릿 Hook 전부 미작동; gitleaks 탐지 역전 | ✗ |
| SEC-S2a-14 | `V3`, ADR-007 §2, `V1:115-127` | N/A | MEDIUM | GRANT 부재 + ADR-007 권한 카탈로그가 코드와 모순 | ✓ |
| SEC-S2a-15 | `application.yml:282`, `.env:56` | N/A | MEDIUM | `require-https` fail-open | ✗ |
| SEC-S2a-17 | `CsrfIntegrationTest:158-187` | N/A | MEDIUM | 실제 CSRF 토큰 경로 미검증 (빌드 RED) | ✗ |
| SEC-S2a-06 | `WriteService:326-345` | 2.7 (4.2) | LOW | 3번째 인가 계층이 항등함수, DENIED 분기 도달 불가 | ✓ |
| SEC-S2a-07 | `Controller:128-133` | 2.7 (4.3) | LOW | `/context` 무감사 읽기 + 존재 오라클 | ✓ |
| SEC-S2a-08 | `WriteService:150,248` | 2.7 (4.2) | LOW | 실패·거부 쓰기 무감사; T-R3 미완화 | ✓ |
| SEC-S2a-09 | `Mapper.xml:174-180` | 2.7 (4.3) | LOW | 중복 409 가 전국 번호공간 존재 오라클, 무기록·무제한 | ✓ |
| SEC-S2a-10 | `Controller:154` | 3.8 | LOW | 등록이 대상 기관의 존재·활성 여부를 검증하지 않음 | ✓ |
| SEC-S2a-11 | `WriteService:494-500` | 2.7 | LOW | 다기관 삭제가 `TARGET_ACCOUNT VARCHAR(50)` 초과 → 500 + 전체 롤백 | ✓ |
| SEC-S2a-12 | `application-local.yml:56` | 3.3 | LOW | local 프로파일에서 발신번호 평문 로그 | ✗ |
| SEC-S2a-13 | `WriteService:524` | 2.2 | LOW | 원장은 암호화, 감사는 평문 이메일 — T-I5 서술과 불일치 | ✓ |
| SEC-S2a-18 | `Controller:131` | N/A | 정보성 | `/context` 가 이름 반환을 위해 인증키를 JVM 으로 과다 fetch | ✓ |

**CVSS ≥ 7.0: 3건 (연쇄 포함 4건).**

### 9.1 오탐 / 확인 후 해제 (감사자 자체 규칙: 오탐은 암시하지 않고 명시한다)

- **SQL injection 없음** — `SenderNumberMapper.xml` 전문 정독 + 매퍼 트리 전체 grep. `src/main/resources/mybatis/` 에 `${}` **0건**, 유일한 텍스트 일치는 금지 이유를 설명하는 주석.
- **Mass assignment 없음.**
- **S2a 쓰기 경로의 감사·로그·예외·오류 본문에 PII 없음** — `MethodArgumentNotValidException` 경로 포함.
- **CSRF 구성 정확.**
- **gitleaks 검출 8건 전부 오탐** — RFC 6238 시험 벡터 및 테스트 픽스처.
- **"운영자가 타 기관 발신번호를 쓸 수 있는가?" → 예. 이는 IDOR 이 아니라 FR-TEN-003 의 의도된 동작.** 결함은 4번째 검사가 그것을 제약한다는 *주장* 뿐이다(SEC-S2a-06).
- D-S1 계열 결함은 **구조적으로** 닫혔다 — `SenderNumberRef`, 단일 `String` 이력 시그니처, archive-before-delete 는 올바른 형태의 수정이다.

---

## 10. 판정

### **REJECT — G3 릴리즈 게이트**

CVSS ≥ 9.0 3건. security-auditor REJECT 권한(시크릿 하드코딩 ≥ 9.0) 및 하네스 ≥ 7.0 차단 규정에 따라 **릴리즈 차단**, PM 통보는 본 리포트와 묶지 않고 **즉시**.

### 라우팅 — REJECT 발송 전에 반드시 읽을 것

**이 REJECT 를 S2a 구현자에게 반송하지 말 것.** ≥ 7.0 4건 중 S2a 에서 비롯한 것은 없다. `application.yml`·`.env`·`.gitignore` 를 `HEAD` 기준으로 검증했고, `git status` 상 S2a 작업트리가 건드린 것은 `BarredNumbers.java` 의 `@Autowired` 한 줄뿐이다. `git log -S` 로 `.env` 는 `0acfb39`, DB 비밀번호는 `0db4d0f`/`0acfb39` 도입 — **전부 S2a 이전**. 소유는 **로그인/인프라 슬라이스**와 **하네스 Hook 소유자**(L1/L2 는 감사자 자신 — SEC-S2a-04 는 작동하지 않은 내 통제다).

따라서 판정은 둘로 갈린다.

| 대상 | 판정 |
|------|------|
| **릴리즈 아티팩트 / G3** | **REJECT.** 세 시크릿 결함이 이 스프린트와 같은 아티팩트로 배포된다. 회전이 제거보다 먼저이며 필수다 — 두 파일 모두 git 이력에 있으므로 제거만으로는 아무것도 고쳐지지 않는다 |
| **Sprint S2a 산출물** | **조건부승인.** 조건 전부 Low/Medium 이고 전부 저비용 |

### S2a 조건부승인의 조건

1. **SEC-S2a-06** — 공허한 3번째 계층 해소: T-T8/T-T1 을 개정하거나 검사를 실재화. G3 증거 패키지가 작동한다고 credit 하는 통제는 작동하거나 credit 되지 않아야 한다.
2. **SEC-S2a-08** — 거부·실패 쓰기 감사. `REQUIRES_NEW` 는 이미 준비됨; `catch` 블록 하나.
3. **SEC-S2a-07** — `/context` 감사 이벤트 추가. 4줄, 슬라이스의 유일한 무기록 읽기를 닫음.
4. **SEC-S2a-05** — **G1 이 V3 를 적용하기 전에** 위협모델 T-I7 방향 정정. 가장 이행을 원하는 항목이다: `V3:56-65` 의 사전검증이 결정성을 스파이크 S1-01 의 *안전한* 결과로 취급하는데, 그것이 악용 가능한 쪽이다.
5. **SEC-S2a-10 / -11 / -12** — 각 1줄 수정.
6. **SEC-S2a-14 / -16** — GRANT 블록을 V3 에. 어느 쪽이든 G1 선행조건.
7. **SEC-S2a-17** — G3 전 CSRF 그린.
8. 로그 보존 **5년 vs 7년** 불일치 서면 정리.

---

## 11. 후속 권고

| 우선 | 조치 | 소유 | 기한 |
|:---:|------|------|------|
| **P0** | DB 비밀번호·OTP 키 **회전** → 모든 OTP 비밀 재암호화 | 인프라 + 정보보호 | 즉시 (4h) |
| **P0** | `.env` untrack + `.gitignore` 등재 + `.env.example` | 인프라 | 즉시 |
| **P0** | `application.yml:61-63`, `:235` 기본값 제거 | 인프라 | 즉시 |
| **P0** | L1 `core.hooksPath` 설치 · L2 파일집합 확장 · gitleaks 룰/allowlist 교정 · 게이트 머지 필수화 | 하네스 Hook 소유자 | 24h |
| **P1** | 위협모델 T-I7 방향 정정 + `ENCRYPT` `EXECUTE` 회수 또는 blind index | 아키텍트 + DBA | **G1 전** |
| **P1** | V3 에 GRANT/REVOKE 블록; ADR-007 §2 개정 | 아키텍트 + DBA | **G1 전** |
| **P1** | CSRF 테스트 그린 (`SecurityConfig:188` 필터 순서) | 로그인 슬라이스 | **G3 전** |
| **P2** | SEC-S2a-06/07/08 (인가 계층 정리, 감사 3종) | S2a 구현자 | S2b |
| **P2** | 금지 라이선스 목록 ADR 확정 + LGPL/BSD-4-Clause 법무 판단 | 아키텍트 + 법무 | S2b |
| **P3** | SEC-S2a-10/11/12/13, `SenderNumberRef.toString()` | S2a 구현자 | S2b |

> **§10 AI 작업 원칙 준수 기록**: 오탐 7종을 §9.1 에 명시했다. 외부 전송 데이터 통제(S1) — 교차검증에 시크릿·PII 를 담은 파일(`application.yml`, `.env`)은 **투입하지 않았고**, 로컬 모델만 사용해 외부 egress 는 0 이다. 상세는 [cross-validation-4.md](../reviews/cross-validation-4.md).
