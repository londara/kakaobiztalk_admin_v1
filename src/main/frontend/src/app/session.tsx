/**
 * 세션 컨텍스트와 경로 가드. / Session context and route guards.
 *
 * req: FR-LOGIN-016, FR-LOGIN-018, FR-TEN-004, FR-AZ-I01, FR-AZ-D01
 *
 * <h2>세션을 메모리에만 두는 이유 / why the session lives only in memory</h2>
 * <p>진짜 세션은 서버가 가진 것이고, 브라우저 쪽 증거는 HttpOnly 쿠키뿐이라 JS 로는 읽을
 * 수 없다(ADR-LOGIN-012). 여기 담기는 값은 <b>서버가 알려준 사실</b>(운영자 여부, 기존 세션
 * 종료 여부)이며 자격증명이 아니다.</p>
 * <p>이 값을 {@code sessionStorage} 에 보관하지 <b>않는다</b>. 보관하면 새로고침 후에도 화면이
 * 열리지만, 쿠키가 이미 만료된 경우 "로그인된 것처럼 보이는 화면 + 모든 요청 403" 이라는
 * 상태가 만들어지고 클라이언트는 그것을 스스로 알아낼 수 없다. 새로고침 후의 복원은 대신
 * {@link SessionGate} 가 서버에 물어서 한다({@code GET /api/auth/session}) — 답이 서버에서
 * 오므로 거짓말이 될 수 없다.</p>
 *
 * <p>The real session is the server's; the only browser-side evidence is an HttpOnly cookie that
 * JS cannot read. What is held here is what the server reported — operator role, whether another
 * session was displaced — and not a credential.</p>
 * <p>It is deliberately <b>not</b> persisted to {@code sessionStorage}: that would survive a
 * refresh, but with an expired cookie it produces a screen that looks signed in while every
 * request returns 403, and the client cannot detect that on its own. Restoring after a refresh is
 * {@link SessionGate}'s job instead, by asking the server — an answer that cannot lie.</p>
 */

import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';

/** 로그인 응답이 알려준 세션 사실. / What the login response reported about the session. */
export interface Session {
  /** 운영자 여부 / whether the user holds the operator role */
  operator: boolean;
  /** 기존 세션이 종료되었는지 / whether an existing session was displaced */
  displacedSession: boolean;
}

interface SessionContextValue {
  /** 현재 세션, 미로그인이면 null / the current session, or null when signed out */
  session: Session | null;
  /** 로그인 성공을 기록한다 / records a successful sign-in */
  signIn: (session: Session) => void;
  /** 세션을 버린다 / discards the session */
  signOut: () => void;
}

const SessionContext = createContext<SessionContextValue | null>(null);

/**
 * 세션 제공자. / The session provider.
 *
 * <p>{@code initialSession} 은 <b>최초 상태</b>일 뿐이다. React 의 {@code useState} 규칙대로,
 * 나중에 다른 값을 넘겨도 이미 세워진 상태는 바뀌지 않는다. {@link SessionGate} 는 서버
 * 확인이 <b>끝난 뒤에</b> 이 제공자를 마운트하므로 그 성질이 문제가 되지 않는다 — 반대로
 * 확인 중에 마운트해 두고 나중에 값을 넘기면 아무 일도 일어나지 않는다.</p>
 * <p>{@code initialSession} is only the <b>initial</b> state: per React's {@code useState} rules a
 * later value does not replace state already established. {@link SessionGate} mounts this provider
 * only <b>after</b> the server answers, so that never bites — whereas mounting it during the probe
 * and passing the value later would silently do nothing.</p>
 *
 * @param children       하위 트리 / the subtree
 * @param initialSession 서버가 알려준 최초 세션 / the initial session as reported by the server
 */
export function SessionProvider({
  children,
  initialSession = null,
}: {
  children: ReactNode;
  initialSession?: Session | null;
}) {
  const [session, setSession] = useState<Session | null>(initialSession);

  const signIn = useCallback((next: Session) => setSession(next), []);
  const signOut = useCallback(() => setSession(null), []);

  const value = useMemo<SessionContextValue>(
    () => ({ session, signIn, signOut }),
    [session, signIn, signOut],
  );

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

/**
 * 세션 컨텍스트를 읽는다. / Reads the session context.
 *
 * <p>제공자 밖에서 부르면 던진다. 조용히 {@code null} 을 돌려주면 가드가 항상 통과하는
 * 상태가 되고, 그것은 눈에 띄지 않는 인가 구멍이다.</p>
 * <p>Throws outside a provider: silently returning {@code null} would make the guards
 * always-pass, which is an authorization hole that shows no symptom.</p>
 *
 * @returns 세션 컨텍스트 / the session context
 */
export function useSession(): SessionContextValue {
  const value = useContext(SessionContext);
  if (!value) {
    throw new Error('useSession must be used inside a <SessionProvider>');
  }
  return value;
}

/**
 * 로그인 직후 진입할 경로. / The path to land on right after login.
 *
 * req: FR-LOGIN-018 — 운영자는 이용기관 관리로, 그 외에는 문자내역으로 진입한다.
 *      Operators land on institution management; everyone else on the message history.
 *
 * @param session 세션 / the session
 * @returns 경로 / the path
 */
export function landingPath(session: Session): string {
  return session.operator ? '/institutions' : '/messages';
}

/**
 * 세션이 있어야 통과하는 가드. / Guard that requires a session.
 *
 * <p>원래 가려던 경로를 {@code state.from} 으로 넘겨 로그인 후 그 자리로 돌려보낸다.
 * 쿼리 문자열까지 포함한다 — 조회 조건이 URL 에 있는 화면은 경로만으로는 복원되지 않는다.</p>
 * <p>The requested path travels in {@code state.from} so login can return the user to it, query
 * string included: on screens whose criteria live in the URL, the path alone does not restore
 * what the user was looking at.</p>
 *
 * <p>이 가드는 편의 장치일 뿐 인가 수단이 아니다. 실제 차단은 서버가 한다 —
 * {@code /api/admin/**} 은 OPERATOR 역할을 요구하고, 그 외 API 는 인증을 요구한다.</p>
 * <p>The guard is a convenience, not an authorization mechanism: the server does the refusing.</p>
 */
export function RequireSession() {
  const { session } = useSession();
  const location = useLocation();

  if (!session) {
    return (
      <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}` }} />
    );
  }
  return <Outlet />;
}

/**
 * 운영자만 통과하는 가드. / Guard that requires the operator role.
 *
 * req: FR-AZ-I01, FR-AZ-D01 — 이용기관·발신번호 관리는 {@code /api/admin/**} 을 호출한다.
 *
 * <p>차단 대신 문자내역으로 되돌린다. 비운영자에게 403 화면을 보여 주는 것보다, 그가 실제로
 * 쓸 수 있는 화면으로 보내는 편이 낫다 — 메뉴에도 이 항목들은 나타나지 않는다.</p>
 * <p>Non-operators are redirected to the message history rather than shown a 403: the menu does
 * not offer these screens either, so landing on one means a stale link, not an attempt.</p>
 */
export function RequireOperator() {
  const { session } = useSession();

  if (!session?.operator) {
    return <Navigate to="/messages" replace />;
  }
  return <Outlet />;
}
