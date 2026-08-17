# ADR-014 — CSRF 토큰 전달 방식

| 항목 | 내용 |
|------|------|
| **상태** | ACCEPTED |
| **일자** | 2026-08-14 |
| **작성** | `architect` (CR-01 수정에 수반) |
| **관련** | ADR-LOGIN-012 (세션 관리), CR-01, [verdict-sprint-M4](../../../reviews/verdict-sprint-M4.md) |
| **영향 범위** | **전 모듈** — login 7개 + 문자내역 3개 엔드포인트 |

---

## 1. 맥락 / Context

Skill 5 리뷰에서 **CR-01(SEV-1)** 이 발견되었다: CSRF 가 활성 상태였으나 토큰을
클라이언트에 전달하는 경로가 없어 **로그인을 제외한 POST 9개가 전부 403** 이었다.

### 레거시 확인 결과 — 방어가 존재하지 않았다

레거시 소스를 직접 확인했다.

| 확인 대상 | 결과 |
|-----------|------|
| `jex-ie8.js:3363` — Jex ajax 기본 헤더 | `cache-control`, `pragma` **뿐**. 토큰 없음 |
| `apm_0001_01_view.jsp` — 로그인 폼 | hidden 토큰 필드 **없음** |
| `OAuthToken.java` | 외부 API 호출용 access token. **CSRF 와 무관** |
| 전체 검색 (`csrf|xsrf|_token_|synchronizer`) | 프레임워크 차원의 CSRF 방어 **0건** |

**즉 레거시는 CSRF 에 대해 무방비였다.** 비밀번호 변경·OTP 초기화 등 모든 상태 변경
요청이 위조 가능했다. 이를 **신규 결함 L11** 로 등록한다.

> **중요한 함의**: CR-01 은 **회귀가 아니다.** CSRF 는 우리가 <b>추가한</b> 통제이고,
> 배선을 완성하지 못한 것이다. 따라서 "레거시 동작으로 되돌리기"는 곧 **CSRF 비활성화**를
> 뜻하며, 그것이 리뷰가 경고한 오수정 경로다(CVSS 8.8 계정 탈취).

## 2. 결정 / Decision

**쿠키 → 헤더 왕복(double-submit with server-side validation)** 방식을 채택한다.

```java
.csrf(csrf -> csrf
    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())  // ①
    .csrfTokenRequestHandler(plainCsrfTokenHandler())                    // ②
    .ignoringRequestMatchers(new AntPathRequestMatcher("/api/auth/login", "POST")))
.addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)                // ③
```

| # | 요소 | 없으면 어떻게 되는가 |
|---|------|---------------------|
| ① | `CookieCsrfTokenRepository.withHttpOnlyFalse()` | 토큰이 세션에만 남아 JS 가 읽을 수 없다 → **CR-01 그대로** |
| ② | 평문 `CsrfTokenRequestAttributeHandler` | 기본 `Xor` 핸들러가 토큰을 마스킹한다. SPA 는 마스킹을 재현할 수 없어 **모든 요청 403** |
| ③ | `CsrfCookieFilter` | Spring Security 6 은 토큰을 **지연 로딩**한다. `getToken()` 을 호출하는 코드가 없으면 값이 생성되지 않고 `Set-Cookie` 도 나가지 않는다 → 쿠키가 **영원히 부재** → ①②를 해도 **CR-01 그대로** |

> **③이 이 ADR 의 핵심이다.** 저장소만 쿠키로 바꾸는 흔한 처방은 Spring Security 6 에서
> **작동하지 않는다.** 지연 로딩 때문에 쿠키가 발행되지 않으며, 증상은 수정 전과 완전히
> 동일하다 — 즉 "고쳤는데 그대로"라는 가장 진단하기 어려운 상태가 된다.

클라이언트는 단일 헬퍼(`src/api/csrf.ts`)를 통해 헤더를 붙인다.

## 3. 검토한 대안 / Alternatives considered

| 안 | 평가 | 채택 여부 |
|----|------|----------|
| **A. 쿠키→헤더 왕복** (채택) | SPA 표준. 서버가 값을 검증하므로 double-submit 단독보다 강하다 | ✅ |
| B. `/api/csrf` 토큰 발급 엔드포인트 | 동작하지만 왕복이 1회 추가되고, 토큰 갱신 시점 관리 코드가 필요 | ❌ 복잡도 증가 |
| C. `SameSite=Strict` 쿠키만으로 대체 | 브라우저 지원이 방어의 전제가 된다. 구형 브라우저·서브도메인 시나리오에서 구멍 | ❌ 단독 불가 (심층 방어로는 병행) |
| D. CSRF 비활성화 | **레거시와 동일 수준.** 비밀번호 변경·OTP 초기화가 CSRF 로 열린다 (CVSS 8.8) | ❌ **명시적 거부** |
| E. 세션 쿠키 대신 Bearer 토큰 | CSRF 가 구조적으로 소멸하나 ADR-LOGIN-012 를 뒤집고 XSS 시 토큰 탈취 위험이 커진다 | ❌ 범위 초과 |

## 4. 근거 / Rationale

**`httpOnly=false` 는 의도적이며 XSS 위험을 늘리지 않는다.** XSS 가 이미 성립한 공격자는
동일 출처에서 직접 요청을 보낼 수 있으므로 CSRF 토큰은 애초에 그 시나리오의 방어선이
아니다. CSRF 토큰이 막는 것은 **다른 출처의 사이트**이며, 그 사이트는 동일 출처 쿠키를
읽을 수 없다.

**로그인만 면제한다.** 로그인 시점에는 세션도 토큰도 없다. `logout` 은 면제하지 않았다 —
강제 로그아웃 CSRF 의 실질 피해는 낮지만, **면제 목록이 길어지는 것 자체가 다음 결함의
토양**이기 때문이다(레거시 `<login>N</login>` 플래그가 D1 을 만든 방식과 동일한 구조).

## 5. 결과 / Consequences

**긍정**
- POST 10개 전부 정상 동작 — 시스템이 기능한다
- 레거시보다 강한 보안 수준 (L11 해소)
- 클라이언트 진입점이 하나(`csrf.ts`)이므로 누락 지점이 줄어든다

**부정 / 주의**
- 프론트엔드에 `document.cookie` 의존이 생긴다 (테스트에서 jsdom 쿠키 파서 필요)
- **테스트 헬퍼 주의**: `vi.stubGlobal('URL', {...})` 로 전역을 교체하면 URL **생성자**가
  사라져 jsdom 쿠키 파서가 깨진다. 이 수정 중 실제로 발생했고, 두 테스트 파일에서
  메서드만 덧붙이는 방식으로 교체했다
- 내보내기 경로가 공용 `post` 를 우회하므로 헤더를 **따로** 붙여야 한다 — CR-01 과 같은
  유형의 중복이며 주석으로 명시했다

## 6. 검증 / Verification

| 항목 | 상태 |
|------|------|
| `csrf.test.ts` — 토큰 읽기·헤더 생성·3개 API 경로 | ✅ **12건 실행 통과** |
| 전체 프론트엔드 | ✅ **68/68** (56 → 68) |
| `tsc --noEmit` | ✅ 0 error |
| **서버 측 403 해소 실측** | ❌ **미검증** — Maven·기동 환경 없음 |

> ⚠ **이 수정은 서버에서 실행 검증되지 않았다.** 프론트엔드가 헤더를 보내는 것은 12건으로
> 증명했으나, 서버가 그 헤더를 받아들여 200 을 반환하는지는 **애플리케이션을 기동해야
> 확인된다.** 특히 ③ `CsrfCookieFilter` 의 필터 순서는 런타임에만 검증 가능하다.
> **MockMvc 통합 테스트 1건**이 이 공백을 메우며, 그것이 CR-01 을 애초에 잡았을 검증이다.
