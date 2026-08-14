# 코드 리뷰 리포트 — Sprint M4 (전체 산출물)

> **Skill**: 05 step [B] · **Date**: 2026-08-14 · **Agent**: `code-reviewer`
> **대상**: 58 Java · 16 TS/TSX · 3 MyBatis XML · 8 QA 드라이버
> **판정**: **REJECT** — SEV-1 결함 1건 (전면 기능 정지)

---

## CR-01 · SEV-1 · CSRF 토큰이 전 계층에서 누락되어 인증 후 모든 요청이 403 이 된다

**위치**
- [SecurityConfig.java:106](../src/main/java/com/webcash/iris/auth/config/SecurityConfig.java#L106)
- [messageHistoryApi.ts](../src/main/frontend/src/api/messageHistoryApi.ts) · [authApi.ts](../src/main/frontend/src/api/authApi.ts)

**사실 관계**

| 확인 항목 | 결과 |
|-----------|------|
| CSRF 활성 여부 | **활성** (의도적 — ADR-LOGIN-012 세션 쿠키 인증) |
| 토큰 저장소 | **미지정** → 기본값 `HttpSessionCsrfTokenRepository` |
| 토큰을 클라이언트에 노출하는 경로 | **없음** (쿠키 아님 / meta 태그 없음 / 전용 엔드포인트 없음) |
| 프론트엔드가 보내는 토큰 헤더 | **없음** — `grep -r "XSRF\|csrf" src/main/frontend/src` 결과 0건 |
| CSRF 면제 엔드포인트 | `/api/auth/login` **1건뿐** |

**결과**: POST 엔드포인트 10개 중 **9개가 403**. 로그인은 성공하고 그 이후 아무것도 동작하지
않는다.

```
/api/auth/login              면제 → 200  ✅
/api/auth/logout             403  ← 로그아웃 불가
/api/auth/password/change    403
/api/auth/password/reset     403
/api/otp/registration/begin  403
/api/otp/registration/confirm 403
/api/otp/reset               403
/api/message-history/search  403  ← 문자내역 전체 기능
/api/message-history/detail  403
/api/message-history/export  403
```

**왜 통과했는가** — 세 가지 검증이 각각 이 결함을 볼 수 없는 위치에 있었다.

1. 프론트엔드 테스트 56건은 `fetch` 를 stub 한다 → 실제 헤더가 검증되지 않는다
2. 백엔드는 **한 번도 컴파일·기동되지 않았다** (Maven 없음) → 403 을 관측할 기회가 없었다
3. `qa/verify-without-maven.sh` 는 Spring 애노테이션을 **제거**한다 → 보안 설정은 검증 대상 밖

세 검증 모두 "각자 정상"이었고, 결함은 그 사이의 빈 공간에 있었다. **문자내역 D1–D9 가
계층 사이의 틈에서 발생한 것과 같은 구조다** — 내가 그 패턴을 문서화하면서 같은 패턴을
만들었다.

**CVSS**: **N/A**. 이것은 취약점이 아니라 **fail-closed 기능 정지**다. 공격자가 유발할 수
없고, 권한이 확대되지도 않는다. CVSS 는 잘못된 계측기다.

> ⚠ **그러나 잘못된 수정이 진짜 취약점을 만든다.** 403 을 마주한 개발자의 가장 빠른 해결은
> `.csrf(csrf -> csrf.disable())` 이다. 그 순간 비밀번호 변경·OTP 초기화가 CSRF 로 열리고
> **계정 탈취(CVSS 8.8, `AV:N/AC:L/PR:N/UI:R/S:U/C:H/I:H/A:H`)** 가 성립한다.
> 따라서 이 결함은 **처방과 함께** 환송해야 한다.

**처방 (정확히 이것만)**

```java
// SecurityConfig — 토큰을 JS 가 읽을 수 있는 쿠키로 내보낸다
.csrf(csrf -> csrf
    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())  // SPA: BREACH 대응 불필요
    .ignoringRequestMatchers(new AntPathRequestMatcher("/api/auth/login", "POST"))
)
```

```ts
// api 계층 — 쿠키에서 읽어 헤더로 보낸다
function csrfHeader(): Record<string, string> {
  const m = /(?:^|;\s*)XSRF-TOKEN=([^;]+)/.exec(document.cookie);
  return m ? { 'X-XSRF-TOKEN': decodeURIComponent(m[1]) } : {};
}
```

`logout` 은 면제하지 **않는다** — 강제 로그아웃 CSRF 는 실질 피해가 낮지만, 면제 목록이
길어지는 것 자체가 다음 결함의 토양이다.

---

## CR-02 · MEDIUM · 대량 반출 엔드포인트에 속도 제한이 없다

**위치** [MessageHistoryController.java](../src/main/java/com/webcash/iris/biztalk/api/MessageHistoryController.java) `/export`

`RateLimiter` 는 존재하지만 **인증 경로에만** 적용된다(`grep RateLimiter src/main/java/.../biztalk/` → 0건).
탈취된 세션 하나로 5,000건 × N회 반복 반출이 가능하다. 건당 상한은 있으나 **빈도 상한이 없다**.

**CVSS 4.3** (`AV:N/AC:L/PR:L/UI:N/S:U/C:L/I:N/A:L`) — MEDIUM, 다음 Sprint.
**처방**: 세션당 `export` 를 시간당 N회로 제한. 감사 액션이 이미 분리되어 있으므로
(`ACTION_MESSAGE_HISTORY_EXPORT`) 탐지는 가능하다 — 없는 것은 예방이다.

---

## CR-03 · LOW · `revokeObjectURL` 을 `click()` 직후 동기 호출한다

**위치** [messageHistoryApi.ts](../src/main/frontend/src/api/messageHistoryApi.ts) — `exportMessageHistory`

```ts
anchor.click();
document.body.removeChild(anchor);
URL.revokeObjectURL(url);   // ← 동기 호출
```

일부 브라우저(Safari·구 Firefox)는 다운로드가 시작되기 전에 blob 이 해제되어 **파일이
저장되지 않는다**. 내 테스트는 `HTMLAnchorElement.prototype.click` 을 stub 했으므로 이
경로를 검증하지 못했다 — CR-01 과 같은 원인이다.

**처방**: `setTimeout(() => URL.revokeObjectURL(url), 0)` 또는 `requestAnimationFrame`.

---

## CR-04 · LOW · 내보내기 감사가 전송 성공보다 먼저 기록된다

**위치** [MessageHistoryService.java](../src/main/java/com/webcash/iris/biztalk/domain/MessageHistoryService.java) `export()`

`audit.recordAuth(..., OK, ...)` 이 CSV 직렬화·전송 **이전**에 실행된다. 클라이언트가 중단하거나
직렬화가 실패하면 "반출 성공"이 기록되지만 파일은 존재하지 않는다. 감사 기록의 방향이
과대(over-report)이므로 유출 탐지 관점에서는 안전한 쪽이나, 5년 보존 기록의 정확성 문제다.

**처방**: 현 상태 유지 + 주석으로 방향성 명시, 또는 `OK` 를 전송 완료 후로 이동.
**판단 근거**: 과소 기록(누락)이 과대 기록보다 위험하므로 우선순위 낮음.

---

## CR-05 · LOW · CSV 수식 방어가 선행 공백을 고려하지 않는다

**위치** [CsvExporter.java](../src/main/java/com/webcash/iris/biztalk/domain/CsvExporter.java) `cell()`

`" =1+1"`(선행 공백)은 `FORMULA_TRIGGERS` 검사를 통과한다. Excel 은 공백 선행 값을 수식으로
해석하지 않으나 **다른 스프레드시트 구현에서의 동작은 확인하지 못했다**. 근거 없이
"취약하다"고 단정하지 않고 하드닝 권고로 남긴다.

**처방**: `safe.stripLeading()` 의 첫 문자로 검사.

---

## 잘 된 점 / What holds up

이 항목들은 실제로 검증했고 문제를 찾지 못했다.

| 항목 | 확인 내용 |
|------|----------|
| `TenantContextFilter` 순서 | `@Order(20)` > Security chain(`-100`) → **인증 뒤 실행이 맞다.** 주석의 주장이 정확 |
| SQL 주입 | 3개 매퍼 전체에 `${}` 보간 **0건**. `<choose>` 로 식별자 고정 |
| `masking()` 위치 | `rowProjection` 공유로 search·export 가 분기 불가 — 구조적으로 우회 불가 |
| 세션 고정 | `changeSessionId()` + 컨트롤러 재생성 이중 |
| `MessageDetailService` 열거 방지 | 없음·미소유 모두 404 |
| 상태 전이 순서 | `AuthenticationService` 가 `assertNotLocked` → 자격증명 → OTP → replay guard 순 (SR-01 수정 후 유지됨) |
| provenance | 58/58 |

## 요약

| ID | 등급 | CVSS | 조치 시점 |
|----|------|------|----------|
| CR-01 | **SEV-1 기능** | N/A (오수정 시 8.8) | **즉시 · Skill 4 환송** |
| CR-02 | MEDIUM | 4.3 | 다음 Sprint |
| CR-03 | LOW | — | 다음 Sprint |
| CR-04 | LOW | — | 백로그 |
| CR-05 | LOW | — | 백로그 |

**판정: REJECT** (§7 — SEV-1 존재)
