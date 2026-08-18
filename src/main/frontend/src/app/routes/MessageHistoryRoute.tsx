import { MessageHistoryPage } from '../../features/biztalk/MessageHistoryPage';
import { useSession } from '../session';

/**
 * 문자내역 경로. / The message-history route.
 *
 * req: FR-MSG-001, FR-TEN-004
 *
 * <p>운영자 여부는 세션이 알고 화면은 받아 쓴다. 이 값은 이용기관 선택란을 보일지 정할
 * 뿐이며, 실제 테넌트 격리는 서버가 세션 기준으로 판정한다(FR-TEN-001).</p>
 * <p>The session knows the role and the screen consumes it. The flag only decides whether the
 * institution field is rendered; tenant isolation itself is decided by the server.</p>
 */
export function MessageHistoryRoute() {
  const { session } = useSession();

  // 이 경로는 RequireSession 아래에 있으므로 세션은 반드시 있다. 타입을 좁히기 위한 분기이며,
  // 도달했다면 가드가 빠진 것이다.
  // The route sits under RequireSession, so a session always exists; this narrows the type, and
  // reaching it would mean the guard is gone.
  if (!session) {
    return null;
  }

  return <MessageHistoryPage operator={session.operator} />;
}
