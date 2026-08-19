import { Navigate, Route, Routes } from 'react-router-dom';
import { InstitutionPage } from '../features/biztalk/InstitutionPage';
import { SenderNumberPage } from '../features/biztalk/SenderNumberPage';
import { AppLayout } from './AppLayout';
import { RequireOperator, RequireSession } from './session';
import { LandingRoute } from './routes/LandingRoute';
import { LoginRoute } from './routes/LoginRoute';
import { MessageHistoryRoute } from './routes/MessageHistoryRoute';
import { OtpRegisterRoute } from './routes/OtpRegisterRoute';
import { PasswordChangeRoute } from './routes/PasswordChangeRoute';
import { ReportRoute } from './routes/ReportRoute';
import { TalkHistoryRoute } from './routes/TalkHistoryRoute';

/**
 * 경로 표. / The route table.
 *
 * req: FR-LOGIN-001, FR-LOGIN-014/015, FR-LOGIN-018, FR-OTP-001, FR-AZ-I01, FR-AZ-D01
 *
 * <pre>
 *   /login             로그인
 *   /otp-register      OTP 등록      — 이동 상태(email) 필요
 *   /password-change   비밀번호 변경  — 이동 상태(email) 필요
 *   ─ RequireSession ─ AppLayout
 *       /              역할별 진입 화면으로 이동
 *       /messages      문자내역
 *       ─ RequireOperator
 *           /institutions  이용기관 관리
 *           /senders       발신번호 관리
 *           /reports       이용기관 보고서
 *           /talk-history  톡전송 내역
 *   *                  / 로 되돌림
 * </pre>
 *
 * <h2>{@code createBrowserRouter} 의 loader 를 쓰지 않는 이유 / why no router loaders</h2>
 * <p>데이터를 가져오는 일은 TanStack Query 가 맡는다. loader 로도 같은 일을 할 수 있지만, 그러면
 * 같은 응답에 대해 라우터 캐시와 쿼리 캐시가 둘 다 존재하게 되고 "지금 화면의 값은 어느
 * 쪽인가" 가 모호해진다. 경로는 <b>무엇을 보여줄지</b>만 정하고, 그 데이터는 화면이 훅으로
 * 요청한다. 덤으로 이 경로 표는 {@code MemoryRouter} 안에 그대로 렌더링할 수 있어
 * 테스트에서 별도 라우터 구성이 필요하지 않다.</p>
 * <p>Fetching is TanStack Query's job. Loaders could do it too, but then one response lives in
 * both the router cache and the query cache and it stops being clear which one the screen is
 * showing. Routes decide <b>what</b> is displayed; the screen asks for its data through hooks. As
 * a bonus this table renders directly inside a {@code MemoryRouter}, so tests need no separate
 * router setup.</p>
 *
 * <h2>가드가 경로 표에 있는 이유 / why the guards are in the table</h2>
 * <p>가드를 화면 안에 두면 화면마다 반복되고, 새 화면을 추가할 때 빠뜨릴 수 있다. 여기서는
 * 부모 경로가 감싸므로 자식은 <b>통과한 뒤에만</b> 존재한다. 다시 강조하면 이것은 편의이며
 * 인가는 서버가 한다 — 레거시는 이 판정을 브라우저 alert 로만 했고(D-I2, D-S2), 서버는
 * 아무도 막지 않았다.</p>
 * <p>Guards inside screens repeat per screen and get forgotten when a screen is added. Here the
 * parent route wraps them, so a child exists only after the guard passed. Again, this is a
 * convenience and the server does the authorizing: the legacy made this call in a browser alert
 * alone (D-I2, D-S2) while the server refused nobody.</p>
 */
export function AppRoutes() {
  return (
    <Routes>
      {/* 세션 이전 단계 — 셸도 내비게이션도 없다 / pre-session steps: no shell, no navigation */}
      <Route path="/login" element={<LoginRoute />} />
      <Route path="/otp-register" element={<OtpRegisterRoute />} />
      <Route path="/password-change" element={<PasswordChangeRoute />} />

      <Route element={<RequireSession />}>
        <Route element={<AppLayout />}>
          <Route path="/" element={<LandingRoute />} />
          <Route path="/messages" element={<MessageHistoryRoute />} />

          {/* req: FR-AZ-I01, FR-AZ-D01 — {@code /api/admin/**} 은 OPERATOR 역할을 요구한다 */}
          <Route element={<RequireOperator />}>
            <Route path="/institutions" element={<InstitutionPage />} />
            <Route path="/senders" element={<SenderNumberPage />} />
            <Route path="/reports" element={<ReportRoute />} />
            <Route path="/talk-history" element={<TalkHistoryRoute />} />
          </Route>
        </Route>
      </Route>

      {/*
        알 수 없는 주소는 진입 경로로 되돌린다. 404 화면을 두지 않는 이유는, 미로그인 상태에서
        오타 난 주소가 "그런 화면은 없다" 보다 로그인 화면으로 가는 편이 쓸모 있기 때문이다.
        An unknown address returns to the index. There is no 404 screen because a mistyped address
        while signed out is more usefully answered by the login screen than by "no such page".
      */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
