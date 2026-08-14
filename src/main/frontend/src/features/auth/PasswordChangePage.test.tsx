import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PasswordChangePage } from './PasswordChangePage';

/**
 * {@link PasswordChangePage} 컴포넌트 테스트. / Component tests for {@link PasswordChangePage}.
 *
 * req: FR-PWD-001, FR-PWD-002, FR-PWD-003, FR-LOGIN-014, FR-LOGIN-015, TM-L018
 * source: apa_0010_04.act — 레거시 비밀번호 변경 팝업
 */
describe('PasswordChangePage', () => {
  const handlers = { onChanged: vi.fn(), onCancel: vi.fn() };
  const EMAIL = 'user@example.com';
  const CURRENT = 'Tr0ubled-Kettle!9';
  const NEXT = 'Windward-Lantern#42';

  beforeEach(() => {
    vi.restoreAllMocks();
    handlers.onChanged.mockReset();
    handlers.onCancel.mockReset();
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
    return render(<PasswordChangePage email={EMAIL} {...handlers} />);
  }

  async function fillForm(user: ReturnType<typeof userEvent.setup>, next = NEXT, confirm = NEXT) {
    await user.type(screen.getByLabelText('현재 비밀번호'), CURRENT);
    await user.type(screen.getByLabelText('OTP 코드'), '123456');
    await user.type(screen.getByLabelText('새 비밀번호'), next);
    await user.type(screen.getByLabelText('새 비밀번호 확인'), confirm);
  }

  it('현재 비밀번호와 OTP 를 함께 요구한다 / requires both the current password and an OTP', () => {
    // req: TM-L018 — 세션 탈취자가 비밀번호를 바꿔 지속성을 얻는 것을 막는다
    renderPage();
    expect(screen.getByLabelText('현재 비밀번호')).toBeRequired();
    expect(screen.getByLabelText('OTP 코드')).toBeRequired();
  });

  it('대상 계정을 화면에 표시한다 / displays the target account', () => {
    // 강제 변경 흐름에서 사용자가 어느 계정을 바꾸는지 알아야 한다
    renderPage();
    expect(screen.getByText(EMAIL)).toBeInTheDocument();
  });

  it('정상 변경 시 onChanged 를 호출한다 / calls onChanged on success', async () => {
    const user = userEvent.setup();
    stubFetch(204, {});
    renderPage();

    await fillForm(user);
    await user.click(screen.getByRole('button', { name: '변경' }));

    await waitFor(() => expect(handlers.onChanged).toHaveBeenCalled());
  });

  it('확인란 불일치는 서버에 보내지 않는다 / a confirmation mismatch is not sent to the server', async () => {
    // 왕복을 아끼고, 서버 API 계약에 확인란 개념을 넣지 않는다
    const user = userEvent.setup();
    const fetchMock = stubFetch(204, {});
    renderPage();

    await fillForm(user, NEXT, 'Different-Value!42');
    await user.click(screen.getByRole('button', { name: '변경' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('일치하지 않습니다');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('정책 위반 목록을 전부 표시한다 / shows every policy violation at once', async () => {
    // req: FR-PWD-003 — 사용자가 한 번에 모두 고칠 수 있어야 한다
    const user = userEvent.setup();
    stubFetch(400, {
      code: 'PASSWORD_POLICY_VIOLATION',
      violations: [
        '비밀번호는 최소 12자 이상이어야 합니다.',
        '영문 대문자·소문자·숫자·특수문자 중 3종류 이상을 포함해야 합니다.',
      ],
    });
    renderPage();

    await fillForm(user);
    await user.click(screen.getByRole('button', { name: '변경' }));

    await waitFor(() => {
      expect(screen.getByText('비밀번호는 최소 12자 이상이어야 합니다.')).toBeInTheDocument();
      expect(
        screen.getByText('영문 대문자·소문자·숫자·특수문자 중 3종류 이상을 포함해야 합니다.'),
      ).toBeInTheDocument();
    });
  });

  it('실패 후 OTP 만 비운다 / clears only the OTP after a failure', async () => {
    // req: TM-L004 — 코드는 1회용이므로 재사용할 수 없다
    const user = userEvent.setup();
    stubFetch(401, { code: 'OTP_INVALID', message: 'OTP 코드를 확인하세요.' });
    renderPage();

    await fillForm(user);
    await user.click(screen.getByRole('button', { name: '변경' }));

    await waitFor(() => expect(screen.getByLabelText('OTP 코드')).toHaveValue(''));
    expect(screen.getByLabelText('현재 비밀번호')).toHaveValue(CURRENT);
    expect(screen.getByLabelText('새 비밀번호')).toHaveValue(NEXT);
  });

  it('새 비밀번호 최소 길이를 12로 안내한다 / states the 12-character minimum', () => {
    // req: FR-PWD-005 — 레거시는 최대 15자였다(결함 L9)
    renderPage();
    const next = screen.getByLabelText('새 비밀번호');
    expect(next).toHaveAttribute('minlength', '12');
    expect(next).toHaveAttribute('maxlength', '128');
  });

  it('취소하면 onCancel 을 호출한다 / calls onCancel when cancelled', async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(screen.getByRole('button', { name: '취소' }));
    expect(handlers.onCancel).toHaveBeenCalled();
  });

  it('요청 본문에 자격증명 4개를 모두 담는다 / sends all four credential values', async () => {
    // req: FR-PWD-002 — 세션이 없으므로 요청 단위로 인증한다
    const user = userEvent.setup();
    const fetchMock = stubFetch(204, {});
    renderPage();

    await fillForm(user);
    await user.click(screen.getByRole('button', { name: '변경' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    const body = JSON.parse(String(init.body));
    expect(body).toEqual({
      email: EMAIL,
      currentPassword: CURRENT,
      otpCode: '123456',
      newPassword: NEXT,
    });
  });

  it('새 비밀번호 요구사항을 화면에 설명한다 / explains the policy on screen', () => {
    // req: NFR-USE-L02 — 시도 후 거절되는 대신 미리 알려준다
    renderPage();
    expect(screen.getByText(/12자 이상/)).toBeInTheDocument();
  });
});
