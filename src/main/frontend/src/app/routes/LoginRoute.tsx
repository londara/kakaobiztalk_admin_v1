import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { LoginPage } from '../../features/auth/LoginPage';
import { landingPath, useSession } from '../session';

/**
 * 로그인 경로. / The login route.
 *
 * req: FR-LOGIN-001, FR-LOGIN-014/015, FR-OTP-001
 *
 * <p>{@link LoginPage} 는 콜백만 받는다 — 화면은 "무슨 일이 일어났는가" 를 알리고, 어디로
 * 갈지는 이 경로가 정한다. 그래서 화면 자체는 라우터 없이도 테스트할 수 있다.</p>
 * <p>{@link LoginPage} takes only callbacks: the screen reports what happened and this route
 * decides where that leads, which is also why the screen stays testable without a router.</p>
 *
 * <p>로그인 성공 시의 이동은 {@code navigate()} 호출이 아니라 <b>렌더 시 판정</b>으로 한다.
 * 세션 기록과 이동을 한 핸들러에서 동시에 하면 두 이동 지시가 경쟁하고 어느 쪽이 이기는지가
 * 렌더 순서에 달리게 된다 — 그러면 {@code from} 으로 돌아가야 할 때 기본 화면으로 가는 일이
 * 생긴다. 비밀번호 변경·OTP 등록으로의 이동은 세션을 만들지 않으므로 그 경쟁이 없다.</p>
 * <p>The post-login redirect is decided at render time rather than by calling {@code navigate()}
 * in the success handler: recording the session and navigating in one handler races two
 * redirects and lets render order pick the winner, which is how a user meant to return to
 * {@code from} ends up on the default screen. The password-change and OTP paths create no
 * session, so they have no such race.</p>
 */
export function LoginRoute() {
  const { session, signIn } = useSession();
  const location = useLocation();
  const navigate = useNavigate();

  // 가드가 넘겨준 원래 목적지. 없으면 역할에 맞는 기본 화면으로 간다.
  // Where the guard came from; without it, the role's default screen.
  const from = (location.state as { from?: string } | null)?.from;

  if (session) {
    return <Navigate to={from ?? landingPath(session)} replace />;
  }

  return (
    <LoginPage
      onAuthenticated={(operator, displacedSession) => signIn({ operator, displacedSession })}
      /*
        이메일은 URL 이 아니라 이동 상태({@code location.state})로 넘긴다. 주소창에 계정이
        남지 않고, 그 값 없이는 화면에 도달할 수 없다 — 아래 두 경로의 가드가 그것을 확인한다.
        The email travels in the navigation state, not the URL: no account appears in the address
        bar, and the screen cannot be reached without it — the guards on both routes check that.
      */
      onPasswordChangeRequired={(email) => navigate('/password-change', { state: { email } })}
      onNeedOtpRegistration={(email) => navigate('/otp-register', { state: { email } })}
    />
  );
}
