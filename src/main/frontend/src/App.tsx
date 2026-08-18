import { useState } from 'react';
import { QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import { AppRoutes } from './app/AppRoutes';
import { createQueryClient } from './app/queryClient';
import { SessionGate } from './app/SessionGate';

/**
 * 애플리케이션 루트. / The application root.
 *
 * req: CONST-TECH-L01, ADR-001, FR-LOGIN-018
 *
 * <h2>세 계층이 각각 무엇을 갖는가 / what each layer owns</h2>
 * <ul>
 *   <li><b>{@code BrowserRouter}</b> — 어떤 화면을 보여줄지. 주소가 화면을 정한다.</li>
 *   <li><b>{@code QueryClientProvider}</b> — 서버가 가진 값. 조회 결과와 그 캐시.</li>
 *   <li><b>{@code SessionGate}</b> — 서버가 알려준 사실(운영자 여부 등). 새로고침 직후에는
 *       그 사실을 서버에 한 번 물어 복원한 뒤 하위 트리를 세운다.</li>
 * </ul>
 * <p>화면 상태(입력 중인 조건, 선택된 행)는 어느 쪽에도 넣지 않고 컴포넌트에 둔다. 넷을 섞기
 * 시작하면 "지금 보이는 값의 주인이 누구인가" 를 추적할 수 없게 된다.</p>
 * <ul>
 *   <li><b>{@code BrowserRouter}</b>: which screen is shown — the address decides.</li>
 *   <li><b>{@code QueryClientProvider}</b>: what the server owns, and its cache.</li>
 *   <li><b>{@code SessionGate}</b>: what the server reported, such as the role — restored by
 *       asking the server once before the subtree is mounted.</li>
 * </ul>
 * <p>Screen state — criteria being typed, the selected row — goes in none of them and stays in the
 * component. Mixing all four is how it stops being possible to say who owns the value on screen.</p>
 *
 * <h2>{@code BrowserRouter} 를 쓰면 서버에 필요한 것 / what BrowserRouter needs from the server</h2>
 * <p>주소가 실제 경로이므로 {@code /messages} 를 새로고침하면 <b>서버로</b> 그 요청이 간다.
 * 개발 중에는 Vite 가 {@code index.html} 로 되돌려 주지만, 배포 환경에서는 SPA 경로를
 * {@code index.html} 로 포워딩하는 규칙이 없으면 404 가 된다. 해시 라우터를 쓰면 그 설정이
 * 필요 없지만 주소가 {@code /#/messages} 가 되고 접근 로그·북마크에 그대로 남는다 — 설정
 * 한 줄을 아끼려고 주소를 망치지 않는다.</p>
 * <p>Because the address is a real path, refreshing {@code /messages} sends that request to the
 * server. Vite rewrites it to {@code index.html} in development, but a deployment without a
 * forwarding rule for SPA paths answers 404. A hash router would avoid the configuration at the
 * cost of {@code /#/messages} in every log and bookmark — not a trade worth one line of config.</p>
 */
export default function App() {
  /*
    QueryClient 는 컴포넌트 밖에서 만들어도 되지만, 상태로 한 번 만들면 이 트리의 수명과
    캐시의 수명이 같아진다 — 모듈 수준 상수는 테스트나 HMR 에서 이전 응답을 다음 트리로
    옮긴다.
    The QueryClient could be a module constant, but holding it in state ties the cache's lifetime
    to this tree's; a module-level instance carries responses across trees under tests and HMR.
  */
  const [queryClient] = useState(createQueryClient);

  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        {/*
          세션 복원이 끝난 뒤에 경로가 판정된다. 순서가 반대면 새로고침마다 로그인 화면이
          한 번 깜빡인다 — 이유는 {@link SessionGate} 에 적어 두었다.
          Routes are decided after the session is restored; the other order flashes the login
          screen on every refresh, for the reason recorded in {@link SessionGate}.
        */}
        <SessionGate>
          <AppRoutes />
        </SessionGate>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
