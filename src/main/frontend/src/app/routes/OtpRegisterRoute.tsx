import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { OtpRegisterPage } from '../../features/auth/OtpRegisterPage';

/**
 * OTP 등록 경로. / The OTP enrolment route.
 *
 * req: FR-OTP-001…006
 *
 * <p>등록 2단계는 서버 세션에 보관된 대기 비밀키에 의존한다. URL 로 직접 들어오면 그 대기
 * 상태도, 어떤 계정을 등록하는지도 없다 — 그래서 이동 상태가 없으면 로그인으로 되돌린다.</p>
 * <p>The second step depends on a pending secret held in the server session. Entering by URL has
 * neither that pending state nor an account, so an absent navigation state returns to login.</p>
 */
export function OtpRegisterRoute() {
  const location = useLocation();
  const navigate = useNavigate();
  const email = (location.state as { email?: string } | null)?.email;

  if (email === undefined) {
    return <Navigate to="/login" replace />;
  }

  return (
    <OtpRegisterPage
      initialEmail={email}
      onBackToLogin={() => navigate('/login', { replace: true })}
    />
  );
}
