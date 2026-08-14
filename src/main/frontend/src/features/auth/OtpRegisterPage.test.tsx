import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { OtpRegisterPage } from './OtpRegisterPage';

/**
 * {@link OtpRegisterPage} 컴포넌트 테스트. / Component tests for {@link OtpRegisterPage}.
 *
 * req: FR-OTP-001…006, NFR-SEC-PII-L01, NFR-SEC-LOG-L01
 * source: apm_1001_03_view.jsp (회원정보 확인), apm_1001_02_view.jsp (내 키 · 코드 입력)
 *
 * <p>가장 중요한 테스트는 <b>비밀키가 외부로 나가지 않는다</b>는 것이다. 레거시 결함 L4 는
 * QR 생성을 위해 비밀키를 평문 HTTP 로 구글에 보냈고, 그 부류의 회귀를 막는 테스트가
 * 필요하다.</p>
 * <p>The most important assertion is that the secret never leaves the browser. Legacy defect
 * L4 sent it to Google in cleartext to render a QR code, and that class of regression needs a
 * test.</p>
 */
describe('OtpRegisterPage', () => {
  const onBackToLogin = vi.fn();

  beforeEach(() => {
    vi.restoreAllMocks();
    onBackToLogin.mockReset();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  const SECRET = 'MFRGGZDFMZTWQ2LKNNWG23TPOBYXE43U';
  const URI = `otpauth://totp/IRIS%20BizTalk:user@example.com?secret=${SECRET}&issuer=IRIS%20BizTalk&algorithm=SHA1&digits=6&period=30`;

  function stubFetchSequence(responses: Array<{ status: number; body: unknown }>) {
    const fetchMock = vi.fn();
    responses.forEach(({ status, body }) => {
      fetchMock.mockResolvedValueOnce({
        ok: status >= 200 && status < 300,
        status,
        json: async () => body,
      } as Response);
    });
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
  }

  function renderPage(initialEmail = 'user@example.com') {
    return render(<OtpRegisterPage initialEmail={initialEmail} onBackToLogin={onBackToLogin} />);
  }

  async function completeIdentityStep(user: ReturnType<typeof userEvent.setup>) {
    await user.type(screen.getByLabelText('비밀번호'), 'Tr0ubled-Kettle!9');
    await user.click(screen.getByRole('button', { name: '다음' }));
  }

  it('1단계는 회원정보 확인이다 / step one is identity confirmation', () => {
    // req: FR-OTP-001, TM-L006 — 비밀번호 없이 비밀키를 발급하지 않는다
    renderPage();
    expect(screen.getByRole('group', { name: '회원정보 확인' })).toBeInTheDocument();
    expect(screen.getByLabelText('비밀번호')).toBeInTheDocument();
    // 아직 비밀키는 화면에 없다
    expect(screen.queryByLabelText('내 키')).not.toBeInTheDocument();
  });

  it('전달받은 이메일을 미리 채운다 / prefills the email carried from login', () => {
    renderPage('carried@example.com');
    expect(screen.getByLabelText('아이디 (이메일)')).toHaveValue('carried@example.com');
  });

  it('신원 확인 후 비밀키와 QR 을 표시한다 / shows the secret and QR after identity confirmation', async () => {
    // req: FR-OTP-002, FR-OTP-003
    const user = userEvent.setup();
    stubFetchSequence([{ status: 200, body: { secret: SECRET, otpauthUri: URI } }]);
    renderPage();

    await completeIdentityStep(user);

    await waitFor(() => expect(screen.getByLabelText('내 키')).toHaveValue(SECRET));
    expect(screen.getByRole('img', { name: 'OTP 등록용 QR 코드' })).toBeInTheDocument();
  });

  it('L4 회귀: 외부 호스트로 요청하지 않는다 / L4 regression: makes no request to an external host', async () => {
    // source: GoogleOTP.getQRBarcodeURL() — http://chart.apis.google.com/chart?...secret=...
    const user = userEvent.setup();
    const fetchMock = stubFetchSequence([{ status: 200, body: { secret: SECRET, otpauthUri: URI } }]);
    renderPage();

    await completeIdentityStep(user);
    await waitFor(() => expect(screen.getByLabelText('내 키')).toHaveValue(SECRET));

    // 모든 요청이 상대 경로(자기 오리진)여야 한다
    const urls = fetchMock.mock.calls.map((c) => String(c[0]));
    expect(urls.length).toBeGreaterThan(0);
    urls.forEach((url) => {
      expect(url.startsWith('/api/')).toBe(true);
      expect(url).not.toContain('chart.apis.google.com');
      expect(url).not.toMatch(/^https?:\/\//);
    });
  });

  it('비밀키 입력란은 읽기 전용이다 / the secret field is read-only', async () => {
    // 사용자는 복사해야 하지만 편집하면 안 된다. disabled 면 일부 브라우저에서 복사 불가
    const user = userEvent.setup();
    stubFetchSequence([{ status: 200, body: { secret: SECRET, otpauthUri: URI } }]);
    renderPage();

    await completeIdentityStep(user);

    await waitFor(() => expect(screen.getByLabelText('내 키')).toHaveAttribute('readonly'));
    expect(screen.getByLabelText('내 키')).not.toBeDisabled();
  });

  it('등록 완료 후 비밀키를 화면에서 제거한다 / drops the secret from the screen after enrolment', async () => {
    // req: NFR-SEC-PII-L01 — 등록 후에는 어떤 경로로도 비밀키를 볼 수 없어야 한다
    const user = userEvent.setup();
    stubFetchSequence([
      { status: 200, body: { secret: SECRET, otpauthUri: URI } },
      { status: 204, body: {} },
    ]);
    renderPage();

    await completeIdentityStep(user);
    await waitFor(() => expect(screen.getByLabelText('내 키')).toHaveValue(SECRET));

    await user.type(screen.getByLabelText('인증 코드'), '123456');
    await user.click(screen.getByRole('button', { name: '등록' }));

    await waitFor(() => expect(screen.getByText('등록이 완료되었습니다')).toBeInTheDocument());
    // 비밀키가 DOM 어디에도 남아 있지 않다
    expect(document.body.innerHTML).not.toContain(SECRET);
  });

  it('이미 등록된 계정은 거절 메시지를 보여준다 / shows the refusal for an already-enrolled account', async () => {
    // req: FR-OTP-006 — 레거시 ADM_00026. 복구는 운영자 초기화만 가능하다
    const user = userEvent.setup();
    stubFetchSequence([
      {
        status: 409,
        body: {
          code: 'OTP_ALREADY_REGISTERED',
          message: '이미 OTP가 등록되어 있습니다. 단말을 분실한 경우 운영자에게 초기화를 요청하세요.',
        },
      },
    ]);
    renderPage();

    await completeIdentityStep(user);

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('운영자에게 초기화를 요청');
    // 비밀키 단계로 진행하지 않는다
    expect(screen.queryByLabelText('내 키')).not.toBeInTheDocument();
  });

  it('확인 코드가 틀리면 비밀키를 유지하고 코드만 비운다 / keeps the secret and clears only the code on a wrong confirmation', async () => {
    // 비밀키를 잃으면 사용자가 앱에 등록한 항목이 무효가 되어 처음부터 다시 해야 한다
    const user = userEvent.setup();
    stubFetchSequence([
      { status: 200, body: { secret: SECRET, otpauthUri: URI } },
      { status: 401, body: { code: 'OTP_INVALID', message: 'OTP 코드를 확인하세요.' } },
    ]);
    renderPage();

    await completeIdentityStep(user);
    await waitFor(() => expect(screen.getByLabelText('내 키')).toHaveValue(SECRET));

    await user.type(screen.getByLabelText('인증 코드'), '999999');
    await user.click(screen.getByRole('button', { name: '등록' }));

    await waitFor(() => expect(screen.getByLabelText('인증 코드')).toHaveValue(''));
    expect(screen.getByLabelText('내 키')).toHaveValue(SECRET);
  });

  it('확인 코드는 6자리 숫자만 받는다 / the confirmation code accepts six digits only', async () => {
    // req: FR-OTP-005, FR-LOGIN-009
    const user = userEvent.setup();
    stubFetchSequence([{ status: 200, body: { secret: SECRET, otpauthUri: URI } }]);
    renderPage();

    await completeIdentityStep(user);
    await waitFor(() => expect(screen.getByLabelText('인증 코드')).toBeInTheDocument());

    const code = screen.getByLabelText('인증 코드');
    await user.type(code, '12ab34');
    expect(code).toHaveValue('1234');
    expect(code).toHaveAttribute('maxlength', '6');
  });

  it('이전 페이지로 돌아갈 수 있다 / can return to the login screen', async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(screen.getByRole('button', { name: '이전 페이지' }));
    expect(onBackToLogin).toHaveBeenCalled();
  });
});
