import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { renderWithProviders } from '../test/renderWithProviders';
import { AppRoutes } from './AppRoutes';

/**
 * 경로 표 검증. / Route-table verification.
 *
 * req: FR-LOGIN-014/015, FR-LOGIN-018, FR-OTP-001, FR-TEN-004, FR-AZ-I01, FR-AZ-D01
 *
 * <p>여기서 지키려는 성질은 두 가지다.</p>
 * <ol>
 *   <li>주소로 <b>도달할 수 있어야 하는 화면</b>은 주소로 열린다 — 조회 조건까지 포함해서.</li>
 *   <li>주소로 <b>도달해서는 안 되는 화면</b>은 열리지 않는다 — 라우터를 넣기 전에는
 *       "주소가 없으니 불가능" 이었던 것이, 이제는 가드가 지키는 성질이 되었기 때문에
 *       시험으로 고정해 둔다.</li>
 * </ol>
 * <p>Two properties are pinned here: screens that should be reachable by address are, criteria
 * included; and screens that should not be reachable by address are not. The second used to hold
 * because no address existed at all — now a guard holds it, so it gets a test.</p>
 */
describe('AppRoutes', () => {
  const institutionRow = {
    code: 'K00001',
    name: '쿠콘_마이데이터사업1본부',
    englishName: 'COOCON_Business1',
    businessNumber: '1234567890',
    authKeyMasked: '****************ohVF',
    status: 'Y',
    statusLabel: '사용',
    description: 'TESTSET1',
    registeredAt: '20210401120000',
    lastModifiedAt: '20260721133000',
  };

  /** 로그인 응답. / The login response. */
  let loginResponse = {
    passwordChangeRequired: false,
    operator: false,
    displacedSession: false,
  };

  /**
   * 경로별 응답을 돌려주는 fetch. / A fetch that answers per path.
   *
   * <p>화면 하나를 열면 그 화면의 조회도 함께 일어난다. 어떤 요청이 왔는지 확인할 수 있도록
   * 호출 기록을 그대로 돌려준다.</p>
   */
  function stubFetch() {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      const body = url.includes('/api/auth/login')
        ? loginResponse
        : url.includes('/institutions/search')
          ? { rows: [institutionRow], totalCount: 1, page: 0, size: 20, totalPages: 1 }
          : url.includes('/sender-numbers')
            ? { rows: [], totalCount: 0, page: 0, size: 20, totalPages: 0 }
            : {};
      return { ok: true, status: 200, json: async () => body } as Response;
    });
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
  }

  /**
   * 로그인 화면을 통해 실제로 로그인한다. / Signs in through the login screen.
   *
   * <p>세션을 테스트에서 직접 심지 않는다. 세션이 만들어지는 경로를 그대로 지나가야 로그인
   * 이후의 이동까지 함께 검증된다 — 심어 놓은 세션은 그 이동을 건너뛴다.</p>
   * <p>The session is not planted: going through the path that creates it is what also verifies
   * where login leads, which a planted session would skip.</p>
   */
  async function signIn(user: ReturnType<typeof userEvent.setup>) {
    await user.type(screen.getByLabelText('아이디 (이메일)'), 'user@example.com');
    await user.type(screen.getByLabelText('비밀번호'), 'Tr0ubled-Kettle!9');
    await user.type(screen.getByLabelText('OTP 코드'), '123456');
    await user.click(screen.getByRole('button', { name: '로그인' }));
  }

  beforeEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
    loginResponse = { passwordChangeRequired: false, operator: false, displacedSession: false };
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  // ---------------------------------------------------------------------------
  // 미로그인 진입 / entry without a session
  // ---------------------------------------------------------------------------

  it('미로그인 상태로 보호 경로에 들어오면 로그인 화면이 나온다 / a protected path without a session shows login', () => {
    stubFetch();

    renderWithProviders(<AppRoutes />, { route: '/messages' });

    expect(screen.getByRole('heading', { name: 'IRIS BizTalk Portal' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '문자내역' })).not.toBeInTheDocument();
  });

  it('알 수 없는 주소는 로그인 화면으로 되돌린다 / an unknown address falls back to login', () => {
    stubFetch();

    renderWithProviders(<AppRoutes />, { route: '/nope' });

    expect(screen.getByRole('heading', { name: 'IRIS BizTalk Portal' })).toBeInTheDocument();
  });

  it('로그인하면 원래 가려던 주소로 돌아간다 / login returns to the requested address', async () => {
    const user = userEvent.setup();
    // 발신번호 관리는 운영자 전용이므로 운영자로 로그인한다 — 그렇지 않으면 돌아가는 대상이
    // 인가 가드에 걸려, 이 테스트가 검증하려는 것과 다른 이유로 문자내역이 열린다.
    // Sender-number management is operator-only, so this signs in as one: otherwise the guard
    // intercepts the destination and the message history opens for a different reason than the
    // one under test.
    loginResponse = { passwordChangeRequired: false, operator: true, displacedSession: false };
    const fetchMock = stubFetch();

    // 발신번호 화면을 특정 기관으로 지목한 주소. 로그인 후 이 조건까지 살아 있어야 한다.
    // A sender-number address naming an institution; the criteria must survive the login.
    renderWithProviders(<AppRoutes />, { route: '/senders?institution=K00001&page=0' });
    await signIn(user);

    expect(
      await screen.findByRole('heading', { name: '이용기관 정보 관리' }),
    ).toBeInTheDocument();
    await waitFor(() =>
      expect(
        fetchMock.mock.calls.some(([u]) => String(u).includes('institution=K00001')),
      ).toBe(true),
    );
  });

  // ---------------------------------------------------------------------------
  // 역할별 진입과 인가 / landing and authorization by role
  // ---------------------------------------------------------------------------

  it('FR-LOGIN-018: 비운영자는 문자내역으로 진입한다 / a non-operator lands on the message history', async () => {
    const user = userEvent.setup();
    stubFetch();

    renderWithProviders(<AppRoutes />, { route: '/' });
    await signIn(user);

    expect(await screen.findByRole('heading', { name: '문자내역' })).toBeInTheDocument();
  });

  it('FR-LOGIN-018: 운영자는 이용기관 관리로 진입한다 / an operator lands on institution management', async () => {
    const user = userEvent.setup();
    loginResponse = { passwordChangeRequired: false, operator: true, displacedSession: false };
    stubFetch();

    renderWithProviders(<AppRoutes />, { route: '/' });
    await signIn(user);

    expect(await screen.findByRole('heading', { name: '서비스 관리' })).toBeInTheDocument();
  });

  it('FR-AZ-I01: 비운영자가 운영자 화면 주소로 들어오면 문자내역으로 되돌린다 / an operator-only path redirects a non-operator', async () => {
    const user = userEvent.setup();
    const fetchMock = stubFetch();

    renderWithProviders(<AppRoutes />, { route: '/institutions' });
    await signIn(user);

    expect(await screen.findByRole('heading', { name: '문자내역' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '서비스 관리' })).not.toBeInTheDocument();
    // 화면이 열리지 않았으므로 운영자 전용 조회도 일어나지 않는다. 서버도 403 으로 막지만,
    // 보내지 않는 쪽이 분명하다.
    // The screen never opened, so the operator-only search never ran either.
    expect(fetchMock.mock.calls.some(([u]) => String(u).includes('/institutions/search'))).toBe(
      false,
    );
  });

  it('FR-TEN-004: 운영자 전용 메뉴는 비운영자에게 보이지 않는다 / operator-only menu items are hidden', async () => {
    const user = userEvent.setup();
    stubFetch();

    renderWithProviders(<AppRoutes />, { route: '/' });
    await signIn(user);
    await screen.findByRole('heading', { name: '문자내역' });

    expect(screen.getByRole('link', { name: '문자내역' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: '이용기관 관리' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: '발신번호 관리' })).not.toBeInTheDocument();
    // 알림톡 발송은 발송 능력을 가진 화면이므로 비운영자에게 보여서는 안 된다(FR-AZ-A03).
    // AlimTalk can send, so it must not appear for a non-operator (FR-AZ-A03).
    expect(screen.queryByRole('link', { name: '템플릿 샘플 검증' })).not.toBeInTheDocument();
  });

  it('운영자에게는 네 메뉴가 모두 보인다 / an operator sees all four menu items', async () => {
    const user = userEvent.setup();
    loginResponse = { passwordChangeRequired: false, operator: true, displacedSession: false };
    stubFetch();

    renderWithProviders(<AppRoutes />, { route: '/' });
    await signIn(user);
    await screen.findByRole('heading', { name: '서비스 관리' });

    for (const label of ['이용기관 관리', '문자내역', '발신번호 관리', '템플릿 샘플 검증']) {
      expect(screen.getByRole('link', { name: label })).toBeInTheDocument();
    }
  });

  it('메뉴에서 템플릿 샘플 검증 화면으로 갈 수 있다 / the menu reaches the AlimTalk screen', async () => {
    // 이 경로는 두 번의 이터레이션 동안 AppRoutes 에 존재했지만 메뉴 항목이 없어 <b>주소를
    // 직접 입력하지 않으면 도달할 수 없었다</b>. 경로를 등록하는 것과 화면에 닿을 수 있게
    // 하는 것은 다른 일이며, 라우팅 테스트가 없으면 그 차이가 드러나지 않는다.
    // The route existed in AppRoutes for two iterations, but with no menu item it was
    // <b>unreachable without typing the address</b>. Registering a route and making a screen
    // reachable are different things, and without a routing test the difference does not surface.
    const user = userEvent.setup();
    loginResponse = { passwordChangeRequired: false, operator: true, displacedSession: false };
    stubFetch();

    renderWithProviders(<AppRoutes />, { route: '/' });
    await signIn(user);
    await screen.findByRole('heading', { name: '서비스 관리' });

    await user.click(screen.getByRole('link', { name: '템플릿 샘플 검증' }));

    expect(await screen.findByRole('heading', { name: '카카오 알림톡 템플릿' })).toBeInTheDocument();
  });

  it('FR-AZ-A03: 비운영자가 알림톡 주소로 들어오면 되돌린다 / a non-operator is redirected away from AlimTalk', async () => {
    // 메뉴를 숨기는 것은 편의이지 방어가 아니다. 주소를 직접 입력해도 RequireOperator 가
    // 막아야 하고, 그 뒤에는 서버의 @PreAuthorize(D-A37 이후 실제로 동작)와
    // /api/admin/** URL 규칙이 있다.
    // Hiding a menu is a convenience, not a control: typing the address must still be refused by
    // RequireOperator, behind which sit the server's @PreAuthorize (executing since D-A37) and the
    // /api/admin/** URL rule.
    const user = userEvent.setup();
    stubFetch();

    renderWithProviders(<AppRoutes />, { route: '/alimtalk' });
    await signIn(user);

    expect(await screen.findByRole('heading', { name: '문자내역' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '카카오 알림톡 템플릿' })).not.toBeInTheDocument();
  });

  it('메뉴로 화면을 옮길 수 있다 / the menu moves between screens', async () => {
    const user = userEvent.setup();
    loginResponse = { passwordChangeRequired: false, operator: true, displacedSession: false };
    stubFetch();

    renderWithProviders(<AppRoutes />, { route: '/' });
    await signIn(user);
    await screen.findByRole('heading', { name: '서비스 관리' });

    await user.click(screen.getByRole('link', { name: '문자내역' }));

    expect(await screen.findByRole('heading', { name: '문자내역' })).toBeInTheDocument();
  });

  // ---------------------------------------------------------------------------
  // 주소로 도달해서는 안 되는 화면 / screens that must not be reachable by address
  // ---------------------------------------------------------------------------

  it('비밀번호 변경 화면은 주소로 직접 열 수 없다 / the password-change screen cannot be opened by URL', () => {
    stubFetch();

    // 대상 계정 없이 이 화면을 열면 누구의 비밀번호를 바꾸는지 알 수 없다. 라우터 도입
    // 이전에는 주소가 없어 불가능했고, 이제는 가드가 같은 성질을 지킨다.
    // Opened without an account there is no way to know whose password is changing. Before the
    // router no address existed; now a guard holds the same property.
    renderWithProviders(<AppRoutes />, { route: '/password-change' });

    expect(screen.getByRole('heading', { name: 'IRIS BizTalk Portal' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '비밀번호 변경' })).not.toBeInTheDocument();
  });

  it('OTP 등록 화면은 주소로 직접 열 수 없다 / the OTP enrolment screen cannot be opened by URL', () => {
    stubFetch();

    // 등록 2단계는 서버 세션의 대기 비밀키에 의존한다(FR-OTP-005).
    // The second step depends on a pending secret in the server session (FR-OTP-005).
    renderWithProviders(<AppRoutes />, { route: '/otp-register' });

    expect(screen.getByRole('heading', { name: 'IRIS BizTalk Portal' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'OTP 등록' })).not.toBeInTheDocument();
  });

  it('FR-LOGIN-014: 비밀번호 변경이 필요하면 계정과 함께 그 화면으로 보낸다 / a forced change carries the account', async () => {
    const user = userEvent.setup();
    loginResponse = { passwordChangeRequired: true, operator: false, displacedSession: false };
    stubFetch();

    renderWithProviders(<AppRoutes />, { route: '/' });
    await signIn(user);

    expect(await screen.findByRole('heading', { name: '비밀번호 변경' })).toBeInTheDocument();
    // 계정은 주소가 아니라 이동 상태로 왔다 — 주소창에 계정이 남지 않는다.
    // The account arrived in the navigation state, not the address bar.
    expect(screen.getByText(/user@example\.com/)).toBeInTheDocument();
  });

  // ---------------------------------------------------------------------------
  // 조회 조건이 주소에서 복원되는가 / do criteria survive in the address
  // ---------------------------------------------------------------------------

  it('FR-INST-003: 이용기관 조회 조건이 주소에서 복원된다 / institution criteria are restored from the address', async () => {
    const user = userEvent.setup();
    loginResponse = { passwordChangeRequired: false, operator: true, displacedSession: false };
    const fetchMock = stubFetch();

    renderWithProviders(<AppRoutes />, { route: '/institutions?name=%EC%BF%A0%EC%BD%98&status=Y&page=2' });
    await signIn(user);
    await screen.findByRole('heading', { name: '서비스 관리' });

    await waitFor(() => {
      const call = fetchMock.mock.calls
        .map(([u]) => String(u))
        .find((u) => u.includes('/institutions/search'));
      expect(call).toBeDefined();
      expect(call).toContain('name=%EC%BF%A0%EC%BD%98');
      expect(call).toContain('status=Y');
      expect(call).toContain('page=2');
    });
    // 조건이 폼에도 복원되어야 한다 — 주소만 맞고 폼이 비어 있으면 사용자는 지금 무엇을
    // 보고 있는지 알 수 없다.
    // The form must show them too: a matching address with an empty form leaves the user unable
    // to tell what is on screen.
    expect(screen.getByLabelText('검색')).toHaveValue('쿠콘');
    expect(screen.getByLabelText('사용')).toBeChecked();
  });
});
