/**
 * CSRF 토큰 전달. / CSRF token transport.
 *
 * req: NFR-SEC-CSRF, CR-01
 * ADR: ADR-014-csrf-token-transport
 *
 * 서버는 CSRF 토큰을 `XSRF-TOKEN` 쿠키로 내보낸다(`CookieCsrfTokenRepository.withHttpOnlyFalse()`).
 * 클라이언트는 그 값을 읽어 `X-XSRF-TOKEN` 헤더로 되돌려 보낸다. 쿠키는 동일 출처에서만
 * 읽을 수 있으므로, 다른 사이트가 위조 요청을 보내도 헤더를 채울 수 없다 — 이것이 방어의
 * 원리다.
 *
 * The server emits the token as an `XSRF-TOKEN` cookie; the client reads it and echoes it in the
 * `X-XSRF-TOKEN` header. A cookie is readable only same-origin, so a forged cross-site request
 * cannot populate the header — that is the whole mechanism.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * 레거시에는 이 방어가 존재하지 않았다 / the legacy had no such defence
 * ─────────────────────────────────────────────────────────────────────────────
 * 레거시 확인 결과(`jex-ie8.js:3363`), Jex ajax 계층의 기본 헤더는 `cache-control` 과
 * `pragma` 뿐이고 CSRF 토큰은 어디에도 없다. 로그인 JSP 에도 hidden 토큰 필드가 없다.
 * `OAuthToken.java` 는 외부 API 호출용 토큰으로 무관하다.
 *
 * 즉 CSRF 방어는 <b>이식된 기능이 아니라 신규 통제</b>다. 이 파일이 없으면 신규 시스템은
 * 레거시와 같은 수준(무방비)이 되고, 있으면 레거시보다 강해진다.
 *
 * Verified in the legacy: the Jex ajax defaults set only `cache-control` and `pragma`, there is no
 * token anywhere, and the login JSP has no hidden token field. CSRF protection is therefore a new
 * control, not a port.
 */

/** 서버가 토큰을 내보내는 쿠키 이름. / Cookie the server writes the token to. */
const COOKIE_NAME = 'XSRF-TOKEN';

/** 서버가 기대하는 헤더 이름. / Header the server expects. */
const HEADER_NAME = 'X-XSRF-TOKEN';

/**
 * 쿠키에서 CSRF 토큰을 읽는다. / Reads the CSRF token from the cookie.
 *
 * 쿠키 이름은 경계를 맞춰 찾는다. `XSRF-TOKEN` 을 부분 문자열로 찾으면 `OTHER-XSRF-TOKEN`
 * 같은 이름에도 걸린다.
 * The name is matched at a boundary; a substring search would also match `OTHER-XSRF-TOKEN`.
 *
 * @returns 토큰, 없으면 null / the token, or null when absent
 */
export function readCsrfToken(): string | null {
  const match = new RegExp(`(?:^|;\\s*)${COOKIE_NAME}=([^;]*)`).exec(document.cookie);
  if (!match) {
    return null;
  }
  return decodeURIComponent(match[1]);
}

/**
 * 상태 변경 요청에 붙일 CSRF 헤더를 만든다.
 * Builds the CSRF header for a state-changing request.
 *
 * 토큰이 없으면 <b>빈 객체를 반환</b>한다. 요청을 막지 않는 이유는 두 가지다:
 *   1) 로그인은 CSRF 면제이므로 토큰이 없는 것이 정상이다
 *   2) 토큰 누락 시 서버가 403 으로 거절하는 것이 정확한 동작이며, 클라이언트가 미리
 *      차단하면 그 신호가 감춰진다
 *
 * Returns an empty object when the token is absent rather than blocking the request: login is
 * exempt so its absence is normal there, and letting the server issue the 403 keeps the signal
 * visible instead of hiding it behind a client-side guard.
 *
 * @returns 헤더 객체 / a headers object
 */
export function csrfHeader(): Record<string, string> {
  const token = readCsrfToken();
  return token ? { [HEADER_NAME]: token } : {};
}
