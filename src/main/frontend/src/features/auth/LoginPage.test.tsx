import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { LoginPage } from './LoginPage';

/**
 * {@link LoginPage} 컴포넌트 테스트. / Component tests for {@link LoginPage}.
 *
 * req: FR-LOGIN-001, FR-LOGIN-022, NFR-SEC-LOG-L01, NFR-USE-L01/L02
 * source: apm_0001_01_view.jsp, apm_0001_01.js
 *
 * <p>백엔드와 달리 이 테스트들은 <b>실제로 실행된다</b>. Maven 부재로 Java 테스트 93건이
 * 미실행 상태인 반면, 프론트엔드는 npm 으로 검증 가능하다.</p>
 * <p>Unlike the backend, these tests <b>actually run</b>: 93 Java cases remain unexecuted
 * for want of Maven, whereas the frontend is verifiable with npm.</p>
 */
describe('LoginPage', () => {
  const handlers = {
    onAuthenticated: vi.fn(),
    onPasswordChangeRequired: vi.fn(),
    onNeedOtpRegistration: vi.fn(),
  };

  beforeEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
    handlers.onAuthenticated.mockReset();
    handlers.onPasswordChangeRequired.mockReset();
    handlers.onNeedOtpRegistration.mockReset();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function stubFetch(status: number, body: unknown) {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: status >= 200 && status < 300,
      status,
      json: async () => body,
    } as Response);
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
  }

  function renderPage() {
    return render(<LoginPage {...handlers} />);
  }

  it('세 개의 자격증명 입력란을 제공한다 / renders all three credential fields', () => {
    // req: FR-LOGIN-001 — 이메일·비밀번호·OTP 를 한 화면에서 제출한다
    renderPage();
    expect(screen.getByLabelText('아이디 (이메일)')).toBeInTheDocument();
    expect(screen.getByLabelText('비밀번호')).toBeInTheDocument();
    expect(screen.getByLabelText('OTP 코드')).toBeInTheDocument();
  });

  it('L9 회귀: 비밀번호 입력란이 15자로 제한되지 않는다 / L9 regression: the password field is not capped at 15', () => {
    // source: apm_0001_01_view.jsp — maxlength="15"
    renderPage();
    expect(screen.getByLabelText('비밀번호')).toHaveAttribute('maxlength', '128');
  });

  it('OTP 입력란은 6자리로 제한된다 / the OTP field is limited to six characters', () => {
    // req: FR-LOGIN-009
    renderPage();
    const otp = screen.getByLabelText('OTP 코드');
    expect(otp).toHaveAttribute('maxlength', '6');
    expect(otp).toHaveAttribute('inputmode', 'numeric');
  });

  it('OTP 입력에서 숫자가 아닌 문자를 제거한다 / strips non-digits from OTP input', async () => {
    // req: FR-LOGIN-009 — 서버도 거절하지만 입력 단계에서 걸러 왕복을 줄인다
    const user = userEvent.setup();
    renderPage();
    const otp = screen.getByLabelText('OTP 코드');
    await user.type(otp, '12a3b4');
    expect(otp).toHaveValue('1234');
  });

  it('정상 로그인 시 onAuthenticated 를 호출한다 / calls onAuthenticated on success', async () => {
    const user = userEvent.setup();
    stubFetch(200, { passwordChangeRequired: false, operator: true, displacedSession: false });
    renderPage();

    await user.type(screen.getByLabelText('아이디 (이메일)'), 'user@example.com');
    await user.type(screen.getByLabelText('비밀번호'), 'Tr0ubled-Kettle!9');
    await user.type(screen.getByLabelText('OTP 코드'), '123456');
    await user.click(screen.getByRole('button', { name: '로그인' }));

    await waitFor(() => expect(handlers.onAuthenticated).toHaveBeenCalledWith(true, false));
  });

  it('비밀번호 변경이 필요하면 해당 화면으로 넘긴다 / routes to password change when required', async () => {
    // req: FR-LOGIN-014, FR-LOGIN-015 — 강제 변경 경로. 레거시 CHNG_PWD='Y' 에 대응
    const user = userEvent.setup();
    stubFetch(200, { passwordChangeRequired: true, operator: false, displacedSession: false });
    renderPage();

    await user.type(screen.getByLabelText('아이디 (이메일)'), 'user@example.com');
    await user.type(screen.getByLabelText('비밀번호'), 'Tr0ubled-Kettle!9');
    await user.type(screen.getByLabelText('OTP 코드'), '123456');
    await user.click(screen.getByRole('button', { name: '로그인' }));

    await waitFor(() =>
      expect(handlers.onPasswordChangeRequired).toHaveBeenCalledWith('user@example.com'),
    );
    expect(handlers.onAuthenticated).not.toHaveBeenCalled();
  });

  it('OTP 미등록이면 등록 화면으로 넘긴다 / routes to enrolment when OTP is unregistered', async () => {
    // req: FR-LOGIN-008 — 오류가 아니라 유도다
    const user = userEvent.setup();
    stubFetch(409, { code: 'OTP_NOT_REGISTERED', message: 'OTP 미등록 계정입니다.' });
    renderPage();

    await user.type(screen.getByLabelText('아이디 (이메일)'), 'user@example.com');
    await user.type(screen.getByLabelText('비밀번호'), 'Tr0ubled-Kettle!9');
    await user.type(screen.getByLabelText('OTP 코드'), '123456');
    await user.click(screen.getByRole('button', { name: '로그인' }));

    await waitFor(() =>
      expect(handlers.onNeedOtpRegistration).toHaveBeenCalledWith('user@example.com'),
    );
  });

  it('인증 실패 메시지를 role=alert 로 알린다 / announces failure through role=alert', async () => {
    // req: NFR-USE-L02, WCAG 3.3.1 — 오류가 스크린리더에 전달되어야 한다
    const user = userEvent.setup();
    stubFetch(401, { code: 'INVALID_CREDENTIALS', message: '아이디 또는 비밀번호를 확인하세요.' });
    renderPage();

    await user.type(screen.getByLabelText('아이디 (이메일)'), 'user@example.com');
    await user.type(screen.getByLabelText('비밀번호'), 'wrong-password-1!');
    await user.type(screen.getByLabelText('OTP 코드'), '123456');
    await user.click(screen.getByRole('button', { name: '로그인' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('아이디 또는 비밀번호를 확인하세요.');
  });

  it('실패 후 OTP 만 비우고 아이디·비밀번호는 유지한다 / clears only the OTP after a failure', async () => {
    // req: TM-L004 — 코드는 1회용이므로 재사용할 수 없다. 나머지를 지우면 사용성만 나빠진다
    const user = userEvent.setup();
    stubFetch(401, { code: 'INVALID_CREDENTIALS', message: '실패' });
    renderPage();

    await user.type(screen.getByLabelText('아이디 (이메일)'), 'user@example.com');
    await user.type(screen.getByLabelText('비밀번호'), 'Tr0ubled-Kettle!9');
    await user.type(screen.getByLabelText('OTP 코드'), '123456');
    await user.click(screen.getByRole('button', { name: '로그인' }));

    await waitFor(() => expect(screen.getByLabelText('OTP 코드')).toHaveValue(''));
    expect(screen.getByLabelText('아이디 (이메일)')).toHaveValue('user@example.com');
    expect(screen.getByLabelText('비밀번호')).toHaveValue('Tr0ubled-Kettle!9');
  });

  it('아이디저장 체크 시 이메일만 저장한다 / stores only the email when remember is checked', async () => {
    // req: FR-LOGIN-022 — 비밀번호·OTP 는 절대 저장하지 않는다 (NFR-SEC-LOG-L01)
    const user = userEvent.setup();
    stubFetch(200, { passwordChangeRequired: false, operator: false, displacedSession: false });
    renderPage();

    await user.type(screen.getByLabelText('아이디 (이메일)'), 'user@example.com');
    await user.type(screen.getByLabelText('비밀번호'), 'Tr0ubled-Kettle!9');
    await user.type(screen.getByLabelText('OTP 코드'), '123456');
    await user.click(screen.getByLabelText('아이디저장'));
    await user.click(screen.getByRole('button', { name: '로그인' }));

    await waitFor(() => expect(handlers.onAuthenticated).toHaveBeenCalled());

    const stored = JSON.stringify(localStorage);
    expect(stored).toContain('user@example.com');
    // 이것이 이 테스트의 핵심 — 자격증명이 저장되지 않았음을 증명한다
    expect(stored).not.toContain('Tr0ubled-Kettle!9');
    expect(stored).not.toContain('123456');
  });

  it('아이디저장 해제 시 저장된 값을 제거한다 / removes the stored email when unchecked', async () => {
    // req: FR-LOGIN-022
    const user = userEvent.setup();
    localStorage.setItem('iris.auth.rememberedEmail', 'old@example.com');
    stubFetch(200, { passwordChangeRequired: false, operator: false, displacedSession: false });
    renderPage();

    // 저장된 아이디가 복원되고 체크박스가 켜져 있다
    expect(screen.getByLabelText('아이디 (이메일)')).toHaveValue('old@example.com');
    const remember = screen.getByLabelText('아이디저장');
    expect(remember).toBeChecked();

    await user.click(remember);
    await user.type(screen.getByLabelText('비밀번호'), 'Tr0ubled-Kettle!9');
    await user.type(screen.getByLabelText('OTP 코드'), '123456');
    await user.click(screen.getByRole('button', { name: '로그인' }));

    await waitFor(() => expect(localStorage.getItem('iris.auth.rememberedEmail')).toBeNull());
  });

  it('제출 중에는 버튼을 비활성화한다 / disables the button while submitting', async () => {
    // 중복 제출 방지. OTP 가 1회용이므로 두 번째 제출은 재사용으로 거절된다(TM-L004)
    const user = userEvent.setup();
    let resolve: (v: unknown) => void = () => {};
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(() => new Promise((r) => { resolve = r; })),
    );
    renderPage();

    await user.type(screen.getByLabelText('아이디 (이메일)'), 'user@example.com');
    await user.type(screen.getByLabelText('비밀번호'), 'Tr0ubled-Kettle!9');
    await user.type(screen.getByLabelText('OTP 코드'), '123456');
    await user.click(screen.getByRole('button', { name: '로그인' }));

    await waitFor(() => expect(screen.getByRole('button', { name: '로그인 중…' })).toBeDisabled());

    resolve({ ok: true, status: 200, json: async () => ({ passwordChangeRequired: false, operator: false, displacedSession: false }) });
  });

  it('세션 쿠키를 포함하여 요청한다 / sends the request with credentials', async () => {
    // req: NFR-SEC-SESSION-L01 — HttpOnly 쿠키가 전송되지 않으면 세션이 성립하지 않는다
    const user = userEvent.setup();
    const fetchMock = stubFetch(200, { passwordChangeRequired: false, operator: false, displacedSession: false });
    renderPage();

    await user.type(screen.getByLabelText('아이디 (이메일)'), 'user@example.com');
    await user.type(screen.getByLabelText('비밀번호'), 'Tr0ubled-Kettle!9');
    await user.type(screen.getByLabelText('OTP 코드'), '123456');
    await user.click(screen.getByRole('button', { name: '로그인' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(init.credentials).toBe('same-origin');
  });
});
