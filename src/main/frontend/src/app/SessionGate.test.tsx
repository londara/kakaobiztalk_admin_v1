import { screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { renderWithProviders } from '../test/renderWithProviders';
import { AppRoutes } from './AppRoutes';
import { SessionGate } from './SessionGate';

/**
 * 세션 복원 검증. / Session-restore verification.
 *
 * req: FR-LOGIN-018, ADR-001
 *
 * <p>여기서 확인하는 것은 <b>새로고침</b>이다. 새 탭에서 주소를 열거나 F5 를 누르면 자바스크립트
 * 상태는 전부 사라지지만 세션 쿠키는 남는다. 그 상황을 재현하는 방법은 간단하다 — 로그인 절차를
 * 거치지 않은 상태에서 앱을 세우고, 서버가 "세션 있음" 이라고 답하게 하는 것이다. 그것이 정확히
 * 새로고침 직후의 브라우저다.</p>
 * <p>What is verified here is a <b>refresh</b>: opening the address in a new tab or pressing F5
 * discards all JavaScript state while the session cookie survives. Reproducing it is simple — mount
 * the app without going through login and let the server answer "session present", which is exactly
 * what the browser looks like just after a refresh.</p>
 */
describe('SessionGate', () => {
  /** 세션 확인 응답을 정하는 스위치. / Decides how the probe is answered. */
  type Probe = { operator: boolean } | 'none' | 'unreachable';

  const institutionPage = {
    rows: [
      {
        code: 'K00001',
        name: '쿠콘_마이데이터사업1본부',
        englishName: 'COOCON_Business1',
        businessNumber: '1234567890',
        authKeyMasked: '****************ohVF',
        status: 'Y',
        statusLabel: '사용',
        description: 'TESTSET1',
        registeredAt: '20210401120000',
        lastModifiedAt: '20260721133000',
      },
    ],
    totalCount: 1,
    page: 0,
    size: 20,
    totalPages: 1,
  };

  function stubFetch(probe: Probe) {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);

      if (url.includes('/api/auth/session')) {
        if (probe === 'unreachable') {
          // 네트워크 자체가 실패한 경우 — fetch 는 응답이 아니라 거부를 돌려준다.
          // A genuine network failure: fetch rejects rather than answering.
          throw new TypeError('Failed to fetch');
        }
        if (probe === 'none') {
          // 미인증 요청은 403 이다 — 이 경로는 어떤 permitAll() 규칙에도 없다.
          // An unauthenticated request answers 403: the path is in no permitAll() rule.
          return { ok: false, status: 403, json: async () => ({}) } as Response;
        }
        return { ok: true, status: 200, json: async () => probe } as Response;
      }

      return { ok: true, status: 200, json: async () => institutionPage } as Response;
    });
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
  }

  /** 새로고침을 재현한다 — 로그인 절차 없이 앱을 세운다. / Reproduces a refresh. */
  function refreshAt(route: string, probe: Probe) {
    const fetchMock = stubFetch(probe);
    const result = renderWithProviders(
      <SessionGate>
        <AppRoutes />
      </SessionGate>,
      { route, withSessionProvider: false },
    );
    return { ...result, fetchMock };
  }

  beforeEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('확인이 끝나기 전에는 로그인 화면을 보여주지 않는다 / no login screen appears before the answer', () => {
    refreshAt('/messages', { operator: false });

    // 이 순간의 세션은 아직 알 수 없다. 로그인 화면을 먼저 띄우면 잠시 뒤 스스로 사라지는
    // 화면이 되고, 그 사이에 입력을 시작한 사용자에게는 화면이 갑자기 바뀐다.
    // The session is not yet known. Showing the login screen first makes it vanish a moment later,
    // and anyone who started typing watches the screen change under them.
    expect(screen.getByRole('status')).toHaveTextContent('세션을 확인하고 있습니다');
    expect(screen.queryByRole('heading', { name: 'IRIS BizTalk Portal' })).not.toBeInTheDocument();
  });

  it('세션이 살아 있으면 새로고침해도 그 화면에 머문다 / a live session keeps the screen across a refresh', async () => {
    refreshAt('/messages', { operator: false });

    expect(await screen.findByRole('heading', { name: '문자내역' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'IRIS BizTalk Portal' })).not.toBeInTheDocument();
  });

  it('조회 조건까지 함께 복원된다 / the criteria in the address survive too', async () => {
    const { fetchMock } = refreshAt('/institutions?name=%EC%BF%A0%EC%BD%98&page=1', {
      operator: true,
    });

    await screen.findByRole('heading', { name: '서비스 관리' });
    await waitFor(() => {
      const call = fetchMock.mock.calls
        .map(([u]) => String(u))
        .find((u) => u.includes('/institutions/search'));
      expect(call).toContain('name=%EC%BF%A0%EC%BD%98');
      expect(call).toContain('page=1');
    });
  });

  it('운영자 여부가 복원된다 / the operator role is restored', async () => {
    refreshAt('/institutions', { operator: true });

    // 운영자로 복원되었으므로 운영자 전용 화면이 열리고 메뉴도 모두 보인다.
    // Restored as an operator, so the operator-only screen opens and every menu item shows.
    expect(await screen.findByRole('heading', { name: '서비스 관리' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '발신번호 관리' })).toBeInTheDocument();
  });

  it('비운영자로 복원되면 운영자 화면은 열리지 않는다 / a restored non-operator still cannot open operator screens', async () => {
    const { fetchMock } = refreshAt('/institutions', { operator: false });

    expect(await screen.findByRole('heading', { name: '문자내역' })).toBeInTheDocument();
    // 권한이 복원되지 않아 운영자로 오인되면 이 조회가 일어나고 403 을 받는다.
    // Had the role come back wrong, this search would run and take a 403.
    expect(fetchMock.mock.calls.some(([u]) => String(u).includes('/institutions/search'))).toBe(
      false,
    );
  });

  it('세션이 없으면 로그인 화면으로 보낸다 / no session leads to the login screen', async () => {
    refreshAt('/messages', 'none');

    expect(
      await screen.findByRole('heading', { name: 'IRIS BizTalk Portal' }),
    ).toBeInTheDocument();
  });

  it('확인에 실패하면 미로그인으로 취급한다 / an unreachable probe counts as signed out', async () => {
    // 확신할 수 없을 때 로그인되어 있다고 가정하면, 로그인된 것처럼 보이는 화면에서 모든
    // 조회가 403 이 되는 상태가 만들어진다. 그보다 로그인 화면이 낫다(fail closed).
    // Assuming a session when unsure yields a screen that looks signed in while every search
    // returns 403; the login screen is the better answer.
    refreshAt('/messages', 'unreachable');

    expect(
      await screen.findByRole('heading', { name: 'IRIS BizTalk Portal' }),
    ).toBeInTheDocument();
  });

  it('세션 확인은 한 번만 한다 / the session is probed once', async () => {
    const { fetchMock } = refreshAt('/institutions', { operator: true });

    await screen.findByRole('heading', { name: '서비스 관리' });

    // 화면을 세우는 동안 여러 번 물으면 새로고침마다 요청이 쌓인다. 첫 답 이후의 진실은
    // 이 쿼리가 아니라 세션 컨텍스트가 들고 있다.
    // Probing repeatedly while the screen mounts piles up requests per refresh; after the first
    // answer the truth lives in the session context, not in this query.
    const probes = fetchMock.mock.calls.filter(([u]) => String(u).includes('/api/auth/session'));
    expect(probes).toHaveLength(1);
  });
});
