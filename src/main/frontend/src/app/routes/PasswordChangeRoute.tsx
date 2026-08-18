import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { PasswordChangePage } from '../../features/auth/PasswordChangePage';

/**
 * 비밀번호 변경 경로. / The password-change route.
 *
 * req: FR-LOGIN-014/015, FR-PWD-001/002
 *
 * <p>이 화면은 <b>URL 로 직접 진입할 수 없다</b>. 대상 계정은 로그인 화면이 이동 상태로
 * 넘겨주며, 그 값이 없으면 어떤 계정의 비밀번호를 바꾸는지 알 수 없다. 라우터를 쓰면서도
 * 그 성질을 유지하기 위해 상태가 없으면 로그인으로 되돌린다 — 빈 화면이나 "이메일을 입력
 * 하세요" 같은 우회로를 만들지 않는다.</p>
 * <p>The screen is not reachable by URL: the account arrives in the navigation state from the
 * login screen, and without it there is no way to know whose password is being changed. Keeping
 * that property under a router means redirecting to login when the state is absent, rather than
 * inventing a blank screen or an "enter your email" detour.</p>
 */
export function PasswordChangeRoute() {
  const location = useLocation();
  const navigate = useNavigate();
  const email = (location.state as { email?: string } | null)?.email;

  if (!email) {
    return <Navigate to="/login" replace />;
  }

  // 변경 후에는 자동 로그인하지 않고 로그인 화면으로 돌린다. 새 비밀번호로 실제 로그인이
  // 되는지 사용자가 즉시 확인하는 편이 낫고, 서버도 변경 응답으로 세션을 만들지 않는다.
  // After a change the user returns to login rather than being signed in: it confirms the new
  // password actually works, and the server does not create a session from the change response.
  const backToLogin = () => navigate('/login', { replace: true });

  return <PasswordChangePage email={email} onChanged={backToLogin} onCancel={backToLogin} />;
}
