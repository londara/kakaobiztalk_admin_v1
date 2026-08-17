import { afterEach, describe, expect, it, vi } from 'vitest';
import { csrfHeader, readCsrfToken } from './csrf';
import { login } from './authApi';
import { exportMessageHistory, searchMessageHistory } from './messageHistoryApi';

/**
 * CSRF 토큰 전달 테스트. / CSRF token transport tests.
 *
 * req: NFR-SEC-CSRF, CR-01 · ADR: ADR-014
 *
 * <b>이 파일이 존재하는 이유</b>: CR-01(인증 후 전면 403)은 컴포넌트 테스트 56건이 전부
 * `fetch` 를 stub 했기 때문에 발견되지 않았다. stub 을 쓰는 것은 정상이지만, stub 에
 * 전달된 <b>헤더를 단정하지 않은 것</b>이 결함을 통과시켰다. 여기서는 헤더 자체를 검증한다.
 *
 * These tests exist because CR-01 slipped through: the 56 component tests stub `fetch`, which is
 * correct, but none asserted the headers passed to it. This file asserts the header itself.
 */
describe('CSRF token transport', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
  });

  function setCookie(value: string) {
    document.cookie = `XSRF-TOKEN=${value}; path=/`;
  }

  function stubFetch() {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Headers(),
      json: async () => ({ rows: [], totalCount: 0, page: 0, size: 50, totalPages: 0 }),
      blob: async () => new Blob(['x']),
    } as unknown as Response);
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
  }

  function headersOf(fetchMock: ReturnType<typeof stubFetch>, call = 0) {
    return (fetchMock.mock.calls[call][1] as RequestInit).headers as Record<string, string>;
  }

  // ---- 토큰 읽기 / reading the token ----

  it('쿠키에서 토큰을 읽는다 / reads the token from the cookie', () => {
    setCookie('abc123');
    expect(readCsrfToken()).toBe('abc123');
  });

  it('URL 인코딩된 토큰을 복원한다 / decodes a percent-encoded token', () => {
    setCookie(encodeURIComponent('a+b/c=='));
    expect(readCsrfToken()).toBe('a+b/c==');
  });

  it('쿠키가 없으면 null 이다 / returns null when absent', () => {
    expect(readCsrfToken()).toBeNull();
  });

  it('유사한 이름의 쿠키에 걸리지 않는다 / does not match a similarly named cookie', () => {
    // 부분 문자열로 찾으면 OTHER-XSRF-TOKEN 의 값을 잘못 읽는다.
    document.cookie = 'OTHER-XSRF-TOKEN=wrong; path=/';
    expect(readCsrfToken()).toBeNull();
    document.cookie = 'OTHER-XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
  });

  it('여러 쿠키 중에서 찾아낸다 / finds it among several cookies', () => {
    document.cookie = 'JSESSIONID=zzz; path=/';
    setCookie('tok');
    expect(readCsrfToken()).toBe('tok');
    document.cookie = 'JSESSIONID=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
  });

  // ---- 헤더 생성 / header construction ----

  it('토큰이 있으면 헤더를 만든다 / builds the header when a token exists', () => {
    setCookie('tok');
    expect(csrfHeader()).toEqual({ 'X-XSRF-TOKEN': 'tok' });
  });

  it('토큰이 없으면 빈 객체다 / yields an empty object without a token', () => {
    // 클라이언트가 요청을 미리 차단하지 않는다 — 서버의 403 이 정확한 신호다.
    expect(csrfHeader()).toEqual({});
  });

  // ---- 실제 API 호출에 실리는지 / does it reach the wire ----

  it('로그인 요청에 토큰이 실린다 / the login request carries the token', async () => {
    setCookie('login-tok');
    const fetchMock = stubFetch();
    await login('a@b.c', 'p', '123456').catch(() => {});
    expect(headersOf(fetchMock)['X-XSRF-TOKEN']).toBe('login-tok');
  });

  it('문자내역 조회에 토큰이 실린다 / the search request carries the token', async () => {
    setCookie('search-tok');
    const fetchMock = stubFetch();
    await searchMessageHistory({ from: '2026-08-13T00:00:00', to: '2026-08-14T00:00:00' });
    expect(headersOf(fetchMock)['X-XSRF-TOKEN']).toBe('search-tok');
  });

  it('내보내기에도 토큰이 실린다 / the export request carries it too', async () => {
    // 내보내기는 공용 post 를 우회하는 별도 경로다. CR-01 이 재발할 수 있는 지점이므로
    // 별도로 단정한다.
    // Export bypasses the shared `post`, so it is asserted separately.
    setCookie('export-tok');
    const fetchMock = stubFetch();
    // URL 전체를 교체하면 안 된다. jsdom 의 쿠키 파서가 `new URL(...)` 을 사용하므로
    // 생성자를 잃으면 document.cookie 읽기가 깨진다 — 두 메서드만 덧붙인다.
    // The whole URL global must not be replaced: jsdom's cookie parser needs the constructor, so
    // losing it breaks document.cookie. Only the two methods are patched.
    const url = URL as unknown as Record<string, unknown>;
    url.createObjectURL = () => 'blob:x';
    url.revokeObjectURL = () => {};
    const realClick = HTMLAnchorElement.prototype.click;
    HTMLAnchorElement.prototype.click = function () {};

    await exportMessageHistory({ from: '2026-08-13T00:00:00', to: '2026-08-14T00:00:00' });

    expect(headersOf(fetchMock)['X-XSRF-TOKEN']).toBe('export-tok');
    HTMLAnchorElement.prototype.click = realClick;
  });

  it('Content-Type 을 덮어쓰지 않는다 / does not clobber Content-Type', async () => {
    setCookie('tok');
    const fetchMock = stubFetch();
    await searchMessageHistory({ from: '2026-08-13T00:00:00', to: '2026-08-14T00:00:00' });
    const headers = headersOf(fetchMock);
    expect(headers['Content-Type']).toBe('application/json');
    expect(headers['X-XSRF-TOKEN']).toBe('tok');
  });

  it('토큰이 없어도 요청은 전송된다 / the request is still sent without a token', async () => {
    const fetchMock = stubFetch();
    await searchMessageHistory({ from: '2026-08-13T00:00:00', to: '2026-08-14T00:00:00' });
    expect(fetchMock).toHaveBeenCalled();
    expect(headersOf(fetchMock)['X-XSRF-TOKEN']).toBeUndefined();
  });
});
