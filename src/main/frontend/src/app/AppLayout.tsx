import { NavLink, Outlet } from 'react-router-dom';
import { useSession } from './session';

/**
 * 로그인 후 셸 — 좌측 내비게이션 바 + 콘텐츠 영역.
 * Post-login shell: a left-hand navigation bar plus the content area.
 *
 * req: FR-LOGIN-016, FR-LOGIN-018, FR-MSG-001, FR-SND-001, FR-TEN-004, FR-AZ-R04, FR-AZ-A03
 *
 * <p>운영자 전용 화면(이용기관·발신번호 관리, 이용기관 보고서, 톡전송 내역, 템플릿 샘플 검증)은 {@code /api/admin/**} 을 호출하므로
 * 운영자에게만 메뉴를 노출한다 — 비운영자에게 보여주면 403 화면이 될 뿐이다. 메뉴를 감추는
 * 것은 편의이고 실제 차단은 서버가 한다(D-I2, D-S2 가 정확히 그 구분을 놓쳤던 결함이다).</p>
 * <p>The operator-only screens call {@code /api/admin/**}, so their menu items appear only for
 * operators; showing them to a non-operator would just render a 403. Hiding a menu item is a
 * convenience — the server does the refusing, which is exactly the distinction D-I2 and D-S2
 * missed.</p>
 *
 * <p>메뉴 항목은 {@code NavLink} 다. 버튼이 아니라 링크이므로 새 탭으로 열 수 있고, 주소가
 * 화면을 결정하므로 뒤로가기가 이전 화면으로 돌아간다.</p>
 * <p>The menu items are {@code NavLink}s: links rather than buttons, so they can be opened in a
 * new tab, and because the address decides the screen, the back button returns to the previous
 * one.</p>
 */

/** 내비게이션 항목. / A navigation entry. */
interface NavItem {
  /** 경로 / the path */
  to: string;
  /** 표시 이름 / the label */
  label: string;
  /** 운영자 전용 여부 / whether the operator role is required */
  operatorOnly: boolean;
}

const NAV_ITEMS: readonly NavItem[] = [
  { to: '/institutions', label: '이용기관 관리', operatorOnly: true },
  { to: '/senders', label: '이용기관 정보 관리', operatorOnly: true },

  // 레거시 메뉴에서도 이용기관 정보 관리 바로 다음에 놓였다. 운영자 전용인 이유는
  // 이 화면이 기관 간 비교를 목적으로 하기 때문이며, 이용기관 주체에게는 전체 조회
  // 권한이 없다(FR-AZ-R03, FR-AZ-R04 — CONFLICT-R01 의 결정).
  // Placed straight after 발신번호 관리, as in the legacy menu. Operator-only because the
  // screen exists for cross-institution comparison and a tenant principal has no 전체 scope
  // (FR-AZ-R03/R04, the CONFLICT-R01 ruling).
  { to: '/reports', label: '이용기관 보고서', operatorOnly: true },
  { to: '/talk-history', label: '톡전송 내역', operatorOnly: true },
  { to: '/messages', label: '문자내역', operatorOnly: false },

  // req: FR-AZ-A03 — 발송 능력을 가진 화면이므로 운영자 전용이다.
  //
  // 레거시 화면 61 은 login=Y 하나만 걸려 있었고, 그것으로 충분했던 이유는 actUseYn=N 이라
  // 아무것도 보낼 수 없었기 때문이다. 발송이 붙는 순간 그 보호는 사라지므로, 메뉴 노출도
  // 인가를 따른다 — 다만 메뉴를 숨기는 것은 편의이지 방어가 아니다. 실제 방어는 서버의
  // @PreAuthorize(D-A37 이후 실제로 동작한다)와 /api/admin/** URL 규칙이다.
  //
  // Legacy screen 61 carried login=Y alone, adequate only because actUseYn=N made it inert. That
  // protection disappears the moment it can send, so menu visibility follows authorization too —
  // though hiding a menu is a convenience, not a control. The real barriers are the server's
  // @PreAuthorize (which actually executes since D-A37) and the /api/admin/** URL rule.
  { to: '/alimtalk', label: '템플릿 샘플 검증', operatorOnly: true },
];

/**
 * 로그인 후 셸 컴포넌트. / The post-login shell component.
 */
export function AppLayout() {
  const { session } = useSession();
  const operator = session?.operator ?? false;
  const visible = NAV_ITEMS.filter((item) => operator || !item.operatorOnly);

  return (
    <div className="app-frame">
      {/*
        레거시 상단 띠 — 청록 바탕에 서비스 이름 하나. 레거시 화면에서 이 띠는 "지금 어느
        시스템에 있는가" 를 말하는 유일한 표시였고, 여기서도 같은 자리에 같은 역할로 둔다.
        The legacy top band: one service name on teal. In the legacy screens this band was the only
        thing saying which system you were in, and it keeps that place and that job here.
      */}
      <header className="app-header">
        <span className="app-brand">IRIS ADMIN</span>
      </header>

      <div className="app-shell">
        <main className="app-content">
          {session?.displacedSession && (
            // req: FR-LOGIN-016 — 기존 세션 종료를 사용자에게 알린다.
            // Displacement is surfaced: if the user did not initiate the other session,
            // this is a compromise signal and must not pass silently.
            <p role="alert" className="field-error visible session-banner">
              다른 기기에서 로그인되어 있던 세션이 종료되었습니다. 본인이 로그인한 것이
              아니라면 즉시 비밀번호를 변경하고 운영자에게 알리세요.
            </p>
          )}
          <Outlet />
        </main>

        {/* 좌측 내비게이션 바. / Left-hand navigation bar. */}
        <nav className="app-nav" aria-label="주 메뉴">
          <ul>
            {visible.map((item) => (
              <li key={item.to}>
                <NavLink
                  to={item.to}
                  className={({ isActive }) => (isActive ? 'nav-item active' : 'nav-item')}
                >
                  {/*
                    레거시 메뉴의 삼각 표식. 장식이므로 스크린리더에서 감춘다 — 항목마다
                    "오른쪽 삼각형" 을 읽어 주면 일곱 개 메뉴가 열네 번 읽힌다.
                    The legacy menu's triangle marker. It is decoration, so it is hidden from
                    screen readers: announcing it would read seven items as fourteen things.
                  */}
                  <span className="nav-marker" aria-hidden="true" />
                  {item.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
      </div>
    </div>
  );
}
