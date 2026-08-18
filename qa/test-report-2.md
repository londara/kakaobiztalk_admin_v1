# QA 테스트 결과 리포트 #2 — 로그인 통합 사이클

> **Skill**: 05 step [A] · **Date**: 2026-08-17 · **Agent**: `qa-engineer`

---

## 1. 실행된 검증 / What ran

| 종류 | 도구 | 결과 |
|------|------|------|
| 백엔드 단위·통합 (JUnit) | `mvn test` (Maven 부트스트랩됨) | **302 실행 / 1 실패** |
| 프론트엔드 컴포넌트 | vitest | **95 / 95** ✅ |
| 프론트엔드 타입 | `tsc --noEmit` | 0 error |
| 프론트엔드 빌드 | vite | 194.44 kB |
| **실 DB 로그인 E2E (수동)** | curl + 실 TOTP | ✅ daralonsingle 로그인 성공, 세션·역할 확립 |
| **로그인 후 권한(/api/admin) E2E** | curl 세션 | ✅ 200 (이전 403) |
| 정적 규칙 | MD5 0 / SQL `${}` 0(주석 제외) | ✅ |
| provenance | 79/79 Java | ✅ |

> **중대 변화**: 지난 리뷰에서 "백엔드는 한 번도 컴파일·실행되지 않았다"였다. 이번 사이클에
> Maven 을 부트스트랩하여 **처음으로 백엔드가 컴파일·기동·실 DB 로그인까지 실행**되었다.
> 그 과정에서 컴파일·런타임에서만 드러나는 결함 다수가 발견·수정되었다(아래 §3).

## 2. 실패 테스트 / Failing

| 테스트 | 원인 | 등급 |
|--------|------|------|
| `CsrfIntegrationTest.echoingCookieValueInHeaderPasses` | 인증된 요청에 XSRF-TOKEN 쿠키가 재발급되지 않음 — CookieCsrfTokenRepository 는 신규 토큰 생성 시에만 Set-Cookie. SPA 는 최초 응답 쿠키를 계속 쓰므로 실사용엔 영향 적으나 테스트 단언과 불일치 | LOW |

## 3. 이번 사이클에 실행으로 발견·수정된 결함 (실행 없이는 안 보였던 것들)

| # | 결함 | 어떻게 드러났나 | 상태 |
|---|------|----------------|------|
| 1 | 매퍼 XML 주석 안 `--` (XML 파싱 불가) | 컨텍스트 기동 시 SAXParseException | ✅ |
| 2 | `USER_LDGR`/`USER_GRP_JNNG` 등 잘못된 테이블명 | BadSqlGrammar | ✅ |
| 3 | `LOGIN_ATTEMPT` 가 varchar (COALESCE int 실패) | 실 쿼리 | ✅ |
| 4 | 생성자 arg `int`→`Integer` 불일치 | 첫 조회 시 NoSuchMethod | ✅ |
| 5 | `AccountStatus` 코드값 매핑(타입 핸들러 필요) | No enum constant '1' | ✅ |
| 6 | `changeSessionId()` 세션 없이 호출 | 최초 로그인 성공 시 예외 | ✅ |
| 7 | OTP 키 평문인데 decrypt 시도 | OTP 검증 실패 | ✅ (key-encrypted=false) |
| 8 | 감사 롤백(실패 로그인 미기록) | audit rows=0 | ✅ REQUIRES_NEW |
| 9 | **로그인 후 전면 403 (SecurityContext 미설정)** | /api/admin 403 | ✅ ROLE 확립 |
| 10 | PWD_HASH 컬럼 부재(실 스키마) | column does not exist | ✅ PWD 전용 |
| 11 | **계정 잠금 카운터 롤백** | login_attempt=0 | ❌ **미해결(SEC-03)** |

**교훈**: 지난 두 리뷰가 지적한 "실행되지 않은 검증"의 공백이 이번에 대량으로 현실화됐다.
단위 테스트(목 사용)는 이 중 어느 것도 잡지 못했다 — 목은 롤백하지 않고, 실 스키마·실
컨텍스트를 태우지 않기 때문이다.

## 4. 미실행 / Not run

| 종류 | 상태 | 원인 |
|------|------|------|
| JaCoCo 커버리지 | ❌ | 이번엔 `mvn test` 만; `verify` 미실행 |
| k6 부하 | ❌ | 기동 앱 대상 미수행 |
| SBOM / 의존성 스캔 | ❌ | Maven 가용해졌으므로 <b>이제 실행 가능</b> — 후속 |
| 바이트 Parity | ❌ 영구 불가 | [parity-report-1.md](../parity/parity-report-1.md) |
| UC 회귀(TC-LOGIN-001-*) | ⚠️ 부분 | TC-05·TC-15 는 코드가 UC 와 다름(TRACE-01), TC-06/07 은 SEC-03 로 실패 |

## 5. 권고

1. **SEC-03(잠금 롤백)** 을 최우선 — 통합 테스트 1건이면 재발 방지 가능.
2. Maven 가용해졌으니 **SBOM + 의존성 스캔**을 이번 게이트 전에 실행할 것.
3. `CsrfIntegrationTest` 실패는 설계 결정(쿠키 재발급 여부)으로 정리 후 테스트 정합.
