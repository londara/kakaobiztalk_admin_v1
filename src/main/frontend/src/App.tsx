import { useState } from 'react';
import { LoginPage } from './features/auth/LoginPage';
import { OtpRegisterPage } from './features/auth/OtpRegisterPage';
import { PasswordChangePage } from './features/auth/PasswordChangePage';
import { InstitutionPage } from './features/biztalk/InstitutionPage';
import { MessageHistoryPage } from './features/biztalk/MessageHistoryPage';
import { SenderNumberPage } from './features/biztalk/SenderNumberPage';

/**
 * 인증 화면 흐름. / Authentication screen flow.
 *
 * req: FR-LOGIN-001, FR-LOGIN-014/015, FR-OTP-001…006
 *
 * 라우터 라이브러리를 쓰지 않는 이유 / why no router library:
 *   인증 흐름은 4개 상태이며 URL 로 직접 진입해서는 안 되는 것들이다. 비밀번호 변경
 *   화면에 URL 로 진입하면 이메일 컨텍스트가 없고, OTP 등록 2단계는 서버 세션의 대기
 *   비밀키에 의존한다. 상태로 관리하면 그 잘못된 진입 자체가 불가능해진다.
 *
 *   The flow has four states, none of which should be reachable by URL: the password
 *   change screen has no email context when entered directly, and OTP enrolment's second
 *   step depends on a pending secret in the server session. Holding the flow in state
 *   makes those invalid entries impossible rather than merely discouraged.
 */

type View =
  | { name: 'login' }
  | { name: 'otp-register'; email: string }
  | { name: 'password-change'; email: string }
  | { name: 'authenticated'; operator: boolean; displacedSession: boolean };

/**
 * 애플리케이션 루트. / The application root.
 */
export default function App() {
  const [view, setView] = useState<View>({ name: 'login' });

  switch (view.name) {
    case 'login':
      return (
        <LoginPage
          onAuthenticated={(operator, displacedSession) =>
            setView({ name: 'authenticated', operator, displacedSession })
          }
          onPasswordChangeRequired={(email) => setView({ name: 'password-change', email })}
          onNeedOtpRegistration={(email) => setView({ name: 'otp-register', email })}
        />
      );

    case 'otp-register':
      return (
        <OtpRegisterPage
          initialEmail={view.email}
          onBackToLogin={() => setView({ name: 'login' })}
        />
      );

    case 'password-change':
      return (
        <PasswordChangePage
          email={view.email}
          // 변경 후에는 자동 로그인하지 않고 로그인 화면으로 돌린다. 새 비밀번호로
          // 실제 로그인이 되는지 사용자가 즉시 확인하는 편이 낫고, 서버도 변경
          // 응답으로 세션을 만들지 않는다.
          // After a change the user returns to login rather than being signed in: it
          // confirms the new password actually works, and the server does not create a
          // session from the change response either.
          onChanged={() => setView({ name: 'login' })}
          onCancel={() => setView({ name: 'login' })}
        />
      );

    case 'authenticated':
      return (
        <AuthenticatedShell
          operator={view.operator}
          displacedSession={view.displacedSession}
        />
      );
  }
}

/** 로그인 후 화면 종류. / The pages reachable after login. */
type Page = 'institution' | 'messages' | 'senders';

/**
 * 로그인 후 셸 — 좌측 내비게이션 바 + 콘텐츠 영역.
 * Post-login shell: a left-hand navigation bar plus the content area.
 *
 * <p>라우터를 쓰지 않으므로 활성 화면을 자체 상태로 관리한다(App 의 인증 흐름과 동일한
 * 이유). 운영자 전용 화면(이용기관·발신번호 관리)은 {@code /api/admin/**} 을 호출하므로
 * 운영자에게만 메뉴를 노출한다 — 비운영자에게 보여주면 403 화면이 될 뿐이다.</p>
 * <p>No router, so the active page is held in state. The operator-only screens call
 * {@code /api/admin/**}, so their menu items appear only for operators; showing them to a
 * non-operator would just render a 403.</p>
 *
 * req: FR-LOGIN-018, FR-MSG-001, FR-SND-001, FR-TEN-004
 */
function AuthenticatedShell({
  operator,
  displacedSession,
}: {
  operator: boolean;
  displacedSession: boolean;
}) {
  // 운영자는 이용기관 관리로, 비운영자는 문자내역으로 진입한다.
  // Operators land on institution management; others on the message history.
  const [page, setPage] = useState<Page>(operator ? 'institution' : 'messages');

  const items: { id: Page; label: string; operatorOnly: boolean }[] = [
    { id: 'institution', label: '이용기관 관리', operatorOnly: true },
    { id: 'messages', label: '문자내역', operatorOnly: false },
    { id: 'senders', label: '발신번호 관리', operatorOnly: true },
  ];
  const visible = items.filter((it) => operator || !it.operatorOnly);

  return (
    <div className="app-shell">
      <main className="app-content">
        {displacedSession && (
          // req: FR-LOGIN-016 — 기존 세션 종료를 사용자에게 알린다.
          // Displacement is surfaced: if the user did not initiate the other session,
          // this is a compromise signal and must not pass silently.
          <p role="alert" className="field-error visible session-banner">
            다른 기기에서 로그인되어 있던 세션이 종료되었습니다. 본인이 로그인한 것이
            아니라면 즉시 비밀번호를 변경하고 운영자에게 알리세요.
          </p>
        )}
        {page === 'institution' && <InstitutionPage />}
        {page === 'messages' && <MessageHistoryPage operator={operator} />}
        {page === 'senders' && <SenderNumberPage />}
      </main>

      {/* 좌측 내비게이션 바. / Left-hand navigation bar. */}
      <nav className="app-nav" aria-label="주 메뉴">
        <ul>
          {visible.map((it) => (
            <li key={it.id}>
              <button
                type="button"
                className={page === it.id ? 'nav-item active' : 'nav-item'}
                aria-current={page === it.id ? 'page' : undefined}
                onClick={() => setPage(it.id)}
              >
                {it.label}
              </button>
            </li>
          ))}
        </ul>
      </nav>
    </div>
  );
}
