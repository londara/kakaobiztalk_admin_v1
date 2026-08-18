import { Navigate } from 'react-router-dom';
import { landingPath, useSession } from '../session';

/**
 * 진입 경로. / The index route.
 *
 * req: FR-LOGIN-018
 *
 * <p>{@code /} 자체는 화면이 아니라 갈림길이다. 운영자는 이용기관 관리로, 그 외에는
 * 문자내역으로 보낸다 — 비운영자에게 이용기관 관리를 열어 주면 403 만 보게 된다.</p>
 * <p>{@code /} is a fork rather than a screen: operators go to institution management, everyone
 * else to the message history, since opening the former for a non-operator only shows a 403.</p>
 */
export function LandingRoute() {
  const { session } = useSession();

  if (!session) {
    return null;
  }
  return <Navigate to={landingPath(session)} replace />;
}
