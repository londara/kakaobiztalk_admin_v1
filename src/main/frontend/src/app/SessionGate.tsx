import type { ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { fetchSession } from '../api/authApi';
import { SessionProvider } from './session';

/**
 * 세션 복원 관문. / The session-restore gate.
 *
 * req: FR-LOGIN-018, ADR-001
 *
 * <h2>이 컴포넌트가 해결하는 문제 / the problem this solves</h2>
 * <p>새로고침하면 자바스크립트 상태는 전부 사라지지만 세션 쿠키와 서버 세션은 그대로 남는다.
 * 쿠키는 {@code HttpOnly} 라 JS 가 읽을 수 없으므로(ADR-LOGIN-012), 클라이언트는 "지금
 * 로그인되어 있는가" 를 스스로 알 수 없다. 그래서 앱을 세우기 전에 서버에 한 번 묻는다.</p>
 * <p>A refresh discards all JavaScript state while the session cookie and the server session
 * survive. The cookie is {@code HttpOnly} and unreadable from JS, so the client cannot tell whether
 * it is signed in — hence one question to the server before the app starts.</p>
 *
 * <h2>확인이 끝나기 전에는 아무 화면도 보여주지 않는 이유 / why nothing renders until it answers</h2>
 * <p>확인 중에 앱을 렌더링하면 그 순간의 세션은 아직 {@code null} 이므로 경로 가드가 로그인
 * 화면으로 보낸다. 잠시 뒤 확인이 끝나 원래 화면으로 다시 이동하면, 사용자에게는 로그인
 * 화면이 한 번 깜빡였다가 사라지는 것으로 보인다 — 로그인해야 하는 줄 알고 입력을 시작한
 * 사용자에게는 화면이 스스로 바뀌는 것이 된다. 답을 받은 <b>뒤에</b> 마운트하면 그 중간
 * 상태가 존재하지 않는다.</p>
 * <p>Rendering the app during the probe means the session is still {@code null} at that moment, so
 * the route guard sends the user to the login screen; when the answer arrives and the app navigates
 * back, the login screen has flashed and vanished — and a user who started typing watches the
 * screen change under them. Mounting <b>after</b> the answer removes that intermediate state.</p>
 *
 * <h2>확인에 실패하면 미로그인으로 본다 / a failed probe counts as signed out</h2>
 * <p>{@code fetchSession} 은 세션이 없거나 판별할 수 없을 때 {@code null} 을 돌려준다. 확신할
 * 수 없을 때 로그인되어 있다고 가정하면, 화면은 로그인된 것처럼 보이는데 모든 조회가 403 이
 * 되는 상태가 만들어진다. 그보다는 로그인 화면이 낫다.</p>
 * <p>{@code fetchSession} answers {@code null} when there is no session or it cannot be determined.
 * Assuming a session when unsure yields a screen that looks signed in while every search returns
 * 403; the login screen is the better answer.</p>
 */

/** 세션 확인 쿼리 키. / The session probe's query key. */
export const sessionQueryKey = ['auth', 'session'] as const;

/**
 * 서버 확인이 끝난 뒤 세션 제공자를 세운다. / Establishes the session provider once the server answers.
 *
 * @param children 하위 트리 / the subtree
 */
export function SessionGate({ children }: { children: ReactNode }) {
  const probe = useQuery({
    queryKey: sessionQueryKey,
    queryFn: fetchSession,
    /*
      한 번만 묻는다. 마운트마다 다시 물으면 화면을 옮길 때마다 요청이 하나씩 늘고, 정작
      이후의 진실은 이 값이 아니라 세션 컨텍스트가 들고 있다 — 로그인·로그아웃은 컨텍스트를
      갱신하며 이 쿼리를 거치지 않는다.
      Asked once. Re-asking on every mount would add a request per navigation, and after the first
      answer the truth lives in the session context rather than here: login and logout update the
      context without going through this query.
    */
    staleTime: Infinity,
    refetchOnMount: false,
  });

  if (probe.isLoading) {
    return (
      <p role="status" className="session-probe">
        세션을 확인하고 있습니다…
      </p>
    );
  }

  return (
    <SessionProvider
      initialSession={
        probe.data
          ? {
              operator: probe.data.operator,
              /*
                기존 세션 종료 경고는 복원하지 않는다. 그것은 로그인이라는 사건에 대한
                사실이고 이미 그 시점에 알렸다 — 새로고침마다 다시 띄우면 사용자는 매번
                새로운 침해가 일어난 것으로 읽는다(FR-LOGIN-016).
                The displacement warning is not restored: it is a fact about the act of logging in
                and was already reported then. Repeating it on every refresh would read as a fresh
                compromise each time.
              */
              displacedSession: false,
            }
          : null
      }
    >
      {children}
    </SessionProvider>
  );
}
