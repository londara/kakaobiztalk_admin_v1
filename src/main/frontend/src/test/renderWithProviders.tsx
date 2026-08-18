import type { ReactElement, ReactNode } from 'react';
import { render } from '@testing-library/react';
import { QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { createQueryClient } from '../app/queryClient';
import { SessionProvider } from '../app/session';

/**
 * 제공자와 함께 렌더링한다. / Renders inside the application's providers.
 *
 * req: TEST-PLAN-LOGIN §1.3
 *
 * <p>{@link App} 와 같은 제공자를 세워 준다 — 다만 라우터는 주소를 메모리에 두는 것으로
 * 바꾼다. 화면은 라우터(질의 문자열로 조회 조건을 읽는 화면이 있다), 쿼리 캐시, 그리고
 * 세션을 필요로 한다.
 * 케이스마다 <b>새</b> QueryClient 를 만드는 것이 핵심이다 — 캐시를 공유하면 앞
 * 테스트의 응답이 뒤 테스트의 첫 렌더에 나타나고, 그 실패는 실행 순서에 따라 나타나거나
 * 사라져 원인을 찾기 어렵다.</p>
 * <p>The screens need a router — some read their criteria from the query string — and a query
 * cache. Creating a fresh QueryClient per case is the point: a shared cache lets one
 * test's response appear in the next test's first render, and that failure comes and goes with
 * execution order.</p>
 *
 * <p>{@code withSessionProvider: false} 는 {@code SessionGate} 를 시험할 때 쓴다 — 그쪽은
 * 서버 확인을 마친 뒤 <b>자기가</b> 제공자를 세우므로, 여기서 미리 세우면 제공자가 겹쳐
 * 무엇이 유효한지 읽기 어려워진다.</p>
 * <p>{@code withSessionProvider: false} is for testing {@code SessionGate}, which establishes the
 * provider itself once the server has answered; establishing one here too would nest them and make
 * it hard to read which is in effect.</p>
 *
 * @param ui    렌더링할 요소 / the element to render
 * @param route 초기 주소 / the initial address
 * @param withSessionProvider 세션 제공자를 세울지 / whether to establish the session provider
 * @returns 렌더 결과와 이 케이스의 QueryClient / the render result and this case's QueryClient
 */
export function renderWithProviders(
  ui: ReactElement,
  {
    route = '/',
    withSessionProvider = true,
  }: { route?: string; withSessionProvider?: boolean } = {},
) {
  const queryClient = createQueryClient();

  function Providers({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[route]}>
          {withSessionProvider ? <SessionProvider>{children}</SessionProvider> : children}
        </MemoryRouter>
      </QueryClientProvider>
    );
  }

  return { ...render(ui, { wrapper: Providers }), queryClient };
}
