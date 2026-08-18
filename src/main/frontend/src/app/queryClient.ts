/**
 * 서버 상태 캐시. / Server-state cache.
 *
 * req: NFR-SEC-PII, FR-SND-011, NFR-USE-01
 *
 * TanStack Query 는 "서버가 가진 값" 만 담는다. 화면 상태(입력 중인 조회 조건, 선택된 행)는
 * 여전히 React 상태이며 캐시에 넣지 않는다. 둘을 섞으면 어느 쪽이 진실인지 알 수 없게 된다.
 *
 * TanStack Query holds only what the server owns. Screen state — criteria being typed, the
 * selected row — stays in React state and never enters the cache; mixing the two makes it
 * impossible to say which side is authoritative.
 *
 * <h2>기본값을 이렇게 정한 이유 / why these defaults</h2>
 * <ul>
 *   <li><b>{@code retry: false}</b> — 이 백엔드의 실패는 대부분 재시도로 낫지 않는다.
 *       미인증·권한없음(403)과 조회조건 위반(400)은 같은 요청을 다시 보내도 같은 답이
 *       온다. 게다가 조회는 서버에서 감사 기록된다(FR-SND-011) — 자동 재시도는 감사
 *       로그에 사용자가 하지 않은 조회를 남긴다.</li>
 *   <li><b>{@code refetchOnWindowFocus: false}</b> — 기본값이면 운영자가 다른 창을 보다
 *       돌아올 때마다 개인정보가 포함된 조회가 다시 실행되고, 그때마다 감사 기록이
 *       쌓인다. 사용자가 요청하지 않은 조회는 하지 않는다.</li>
 *   <li><b>{@code placeholderData} 를 쓰지 않는다</b> — {@code keepPreviousData} 를 쓰면
 *       새 조회가 실패해도 이전 행이 화면에 남는다. 그것은 이 화면들이 명시적으로 피해온
 *       동작이다(실패 후 남은 행은 조회에 성공한 것처럼 보인다).</li>
 * </ul>
 * <ul>
 *   <li><b>{@code retry: false}</b>: failures here are not transient. A 403 (no session, or no
 *       operator role) and a 400 (criteria violation) answer identically on a second attempt,
 *       and searches are audited server-side — an automatic retry writes an audit entry for a
 *       search the user never made.</li>
 *   <li><b>{@code refetchOnWindowFocus: false}</b>: the default would re-run PII-bearing
 *       searches every time the operator tabs back, each one audited. No search happens that the
 *       user did not ask for.</li>
 *   <li><b>no {@code placeholderData}</b>: {@code keepPreviousData} would leave the previous
 *       rows on screen when a new search fails — exactly the behaviour these screens were
 *       written to avoid, because stale rows read as a successful search.</li>
 * </ul>
 */

import { QueryClient } from '@tanstack/react-query';

/**
 * 애플리케이션용 QueryClient 를 만든다. / Creates the application's QueryClient.
 *
 * <p>싱글턴 상수가 아니라 팩토리다. 테스트는 매 케이스마다 새 인스턴스를 만들어야 한다 —
 * 캐시를 공유하면 앞 테스트의 응답이 뒤 테스트의 첫 렌더에 나타난다.</p>
 * <p>A factory rather than a shared constant: each test needs its own instance, since a shared
 * cache would let one test's response appear in the next test's first render.</p>
 *
 * @returns 새 QueryClient / a fresh QueryClient
 */
export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        refetchOnWindowFocus: false,
      },
      mutations: {
        retry: false,
      },
    },
  });
}
