import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderWithProviders } from '../../test/renderWithProviders';
import { SenderNumberRegisterDialog } from './SenderNumberRegisterDialog';

/**
 * {@link SenderNumberRegisterDialog} 컴포넌트 테스트. / Component tests.
 *
 * req: FR-SNDC-001, FR-SNDC-002, FR-SNDC-011, FR-SNDC-012, FR-SNDC-013, FR-SNDC-014
 * source: biztalk_admin_12_view.jsp, biztalk_admin_12.js
 *
 * 이 팝업의 시험 중 절반은 <b>무엇이 없는지</b>를 단언한다 — 편집 가능한 이용기관, 인증번호 칸,
 * 요청 본문의 기관 코드. 레거시의 결함이 전부 그 자리에 있었다.
 *
 * Half of these cases assert <b>what is absent</b>: an editable institution, an 인증번호 field, an
 * institution code in the request body. That is where the legacy's defects were.
 */
describe('SenderNumberRegisterDialog', () => {
  const INSTITUTION = 'K0ABCD';

  interface Call {
    url: string;
    method: string;
    body: unknown;
  }

  let calls: Call[];

  /**
   * URL 과 메서드로 응답을 고르는 fetch 대역. / A fetch stub dispatching on URL and method.
   *
   * @param register 등록 응답 / the registration response
   */
  function stubFetch(register: { status: number; body: unknown } = {
    status: 200,
    body: { affected: 1, ref: 'SzBBQkNEHzAyMTIzNDU2Nzg' },
  }) {
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      const method = init?.method ?? 'GET';
      calls.push({
        url,
        method,
        body: init?.body ? JSON.parse(init.body as string) : undefined,
      });

      if (url.includes('/context')) {
        return {
          ok: true,
          status: 200,
          json: async () => ({ institution: INSTITUTION, institutionName: '○○기관' }),
        } as Response;
      }
      return {
        ok: register.status < 400,
        status: register.status,
        json: async () => register.body,
      } as Response;
    });
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
  }

  async function fill(number: string, reason: string) {
    await userEvent.type(screen.getByRole('textbox', { name: /발신번호/ }), number);
    await userEvent.type(screen.getByRole('textbox', { name: /사유/ }), reason);
  }

  beforeEach(() => {
    calls = [];
    vi.unstubAllGlobals();
  });

  it('FR-SNDC-002 / D-S18 — 이용기관 문맥은 좁은 엔드포인트에서 온다', async () => {
    stubFetch();
    renderWithProviders(
      <SenderNumberRegisterDialog institution={INSTITUTION} onClose={() => {}} />,
    );

    expect(await screen.findByText('○○기관')).toBeInTheDocument();

    // 레거시는 이름을 채우려고 이용기관 <b>상세조회</b>(biztalk_admin_01_l002)를 불렀고, 그
    // 서비스는 기관 레코드 전체를 평문 인증키와 함께 반환했다(D-S18). 여기서 부르는 것은
    // 코드와 이름만 돌려주는 전용 엔드포인트다.
    // The legacy called the institution *detail* service to fill in a name, and it returned the
    // whole record including the plaintext key (D-S18). What is called here is a dedicated endpoint
    // returning only the code and the name.
    const contextCall = calls.find((call) => call.url.includes('/context'));
    expect(contextCall).toBeDefined();
    expect(contextCall?.url).not.toContain('/institutions/');
  });

  it('FR-SNDC-013 / D-S12 — 화면이 고지하는 세 규칙이 그대로 표시된다', async () => {
    stubFetch();
    renderWithProviders(
      <SenderNumberRegisterDialog institution={INSTITUTION} onClose={() => {}} />,
    );

    // 문구는 레거시 infoList01 그대로다(PM 지시). 세 규칙 모두 서버가 적용한다 — 레거시는
    // 두 번째 규칙을 어느 계층에도 구현하지 않았다(D-S12).
    // The copy is the legacy infoList01 verbatim (PM directive). All three are enforced server-side;
    // the legacy implemented the second in no layer at all (D-S12).
    expect(screen.getByText(/8~11자리 번호여야 합니다/)).toBeInTheDocument();
    expect(screen.getByText(/112,114,1335 와 같은 특수번호는 등록 불가능합니다/)).toBeInTheDocument();
    expect(screen.getByText(/15xx, 16xx 같은 대표번호/)).toBeInTheDocument();
  });

  it('FR-SNDC-012 — 이용기관은 읽기 전용이며 본문에 담기지 않는다', async () => {
    stubFetch();
    renderWithProviders(
      <SenderNumberRegisterDialog institution={INSTITUTION} onClose={() => {}} />,
    );
    await screen.findByText('○○기관');

    // 값은 이름을 갖고 보이지만 <b>입력 요소가 아니다</b>. 레거시는 disabled 입력으로 두었는데,
    // 비활성 입력도 "고칠 수 있는 값" 으로 읽힌다 — 그래서 텍스트로 표시하고 라벨만 연결한다.
    // The value is named and visible but is <b>not a form control</b>. The legacy used a disabled
    // input, and even a disabled input reads as an editable value, so this is text with a label
    // association instead.
    expect(screen.getByLabelText('이용기관코드')).toHaveTextContent(INSTITUTION);
    expect(screen.queryByRole('textbox', { name: '이용기관코드' })).not.toBeInTheDocument();
    expect(screen.queryByRole('textbox', { name: '이용기관명' })).not.toBeInTheDocument();

    await fill('0212345678', '고객사 요청');
    await userEvent.click(screen.getByRole('button', { name: '등록' }));

    await waitFor(() => expect(calls.some((call) => call.method === 'POST')).toBe(true));
    const post = calls.find((call) => call.method === 'POST');

    // 대상 기관은 질의 문자열로 가고 서버가 세션 권한으로 다시 판정한다. 본문에 담으면
    // "어느 기관인가" 에 두 개의 답이 생기고, 그중 하나는 신뢰할 수 없다.
    // The target travels in the query string and the server re-decides it. In the body it would be
    // a second answer to "which institution", and one of the two would be untrusted.
    expect(post?.url).toContain(`institution=${INSTITUTION}`);
    expect(post?.body).not.toHaveProperty('institution');
    expect(post?.body).not.toHaveProperty('institutionName');
  });

  it('D-S4 — 인증번호 칸이 없다', async () => {
    stubFetch();
    renderWithProviders(
      <SenderNumberRegisterDialog institution={INSTITUTION} onClose={() => {}} />,
    );
    await screen.findByText('○○기관');

    // 소유 인증은 구현하지 않는다(PM 결정 AMB-S01, RESIDUAL-S01). 레거시 JSP 에는 주석 처리된
    // 칸과 '인증번호전송' 버튼이 남아 있었고 계약에는 AUTH_NO 가 선언되어 있었다 — 선언만 남은
    // 입력은 있는 통제로 오해된다(D-S4). 없으면 오해가 없다.
    // Ownership verification is not built (AMB-S01, RESIDUAL-S01). The legacy JSP kept a commented-out
    // field and an 인증번호전송 button while the contract declared AUTH_NO: a declared-but-dead input
    // reads as a control that exists (D-S4). Absent, it misleads nobody.
    expect(screen.queryByLabelText(/인증번호/)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /인증번호전송/ })).not.toBeInTheDocument();
  });

  it('FR-SNDC-011 / AMB-S10 — 사유가 필수로 표시된다', async () => {
    stubFetch();
    renderWithProviders(
      <SenderNumberRegisterDialog institution={INSTITUTION} onClose={() => {}} />,
    );
    await screen.findByText('○○기관');

    // 레거시에도 칸은 있었으나 강제되지 않았다(D-S11 로 클라이언트 검증이 무력했다).
    // The legacy had the field but never enforced it (its client validation was vacuous, D-S11).
    expect(screen.getByRole('textbox', { name: /사유/ })).toBeRequired();
  });

  it('FR-SNDC-014 — 서버가 거절하면 폼이 열린 채 입력이 남는다', async () => {
    stubFetch({
      status: 400,
      body: {
        code: 'VALIDATION_FAILED',
        errors: [{ field: 'number', message: '특수번호 및 긴급번호는 발신번호로 등록할 수 없습니다.' }],
      },
    });
    const onClose = vi.fn();
    renderWithProviders(
      <SenderNumberRegisterDialog institution={INSTITUTION} onClose={onClose} />,
    );
    await screen.findByText('○○기관');

    await fill('1335', '고객사 요청');
    await userEvent.click(screen.getByRole('button', { name: '등록' }));

    // 메시지가 해당 칸 옆에 나온다. 레거시는 규칙과 무관하게 jex.alert('등록중 오류 발생.')
    // 한 문장만 보여 주었다(NFR-USE-D02).
    // The message appears beside its field; the legacy showed one sentence regardless of the rule.
    expect(await screen.findByRole('alert')).toHaveTextContent(/특수번호/);

    // 닫히지 않고, 입력도 남는다. 11자리 번호를 다시 입력하게 만드는 것은 받아들일 만한
    // 결과가 아니다.
    // It does not close and the input survives: re-typing an 11-digit number is not an acceptable
    // outcome.
    expect(onClose).not.toHaveBeenCalled();
    expect(screen.getByRole('textbox', { name: /발신번호/ })).toHaveValue('1335');
    expect(screen.getByRole('textbox', { name: /사유/ })).toHaveValue('고객사 요청');
  });

  it('FR-SNDC-004 — 중복(409)도 같은 방식으로 표시된다', async () => {
    stubFetch({
      status: 409,
      body: {
        code: 'DUPLICATE',
        errors: [{ field: 'number', message: '이미 등록된 발신번호입니다.' }],
      },
    });
    renderWithProviders(
      <SenderNumberRegisterDialog institution={INSTITUTION} onClose={() => {}} />,
    );
    await screen.findByText('○○기관');

    await fill('0212345678', '고객사 요청');
    await userEvent.click(screen.getByRole('button', { name: '등록' }));

    // 화면이 해야 할 일은 400 과 같다 — 칸 옆에 문장을 보여 주고 폼을 열어 둔다. 어느 기관이
    // 그 번호를 갖고 있는지는 서버가 말해 주지 않는다.
    // The screen's job is the same as for a 400. The server does not say which institution holds it.
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(/이미 등록된 발신번호/);
    expect(alert.textContent).not.toMatch(/K0/);
  });

  it('성공하면 닫고 상위에 알린다 / closes and notifies on success', async () => {
    stubFetch();
    const onClose = vi.fn();
    const onRegistered = vi.fn();
    renderWithProviders(
      <SenderNumberRegisterDialog
        institution={INSTITUTION}
        onClose={onClose}
        onRegistered={onRegistered}
      />,
    );
    await screen.findByText('○○기관');

    await fill('0212345678', '고객사 요청');
    await userEvent.click(screen.getByRole('button', { name: '등록' }));

    await waitFor(() => expect(onRegistered).toHaveBeenCalled());
    expect(onClose).toHaveBeenCalled();
  });

  it('Esc 로 닫힌다 / Escape closes the dialog', async () => {
    stubFetch();
    const onClose = vi.fn();
    renderWithProviders(
      <SenderNumberRegisterDialog institution={INSTITUTION} onClose={onClose} />,
    );
    await screen.findByText('○○기관');

    await userEvent.keyboard('{Escape}');
    expect(onClose).toHaveBeenCalled();
  });

  it('모달로 표시된다 / is announced as a modal', async () => {
    stubFetch();
    renderWithProviders(
      <SenderNumberRegisterDialog institution={INSTITUTION} onClose={() => {}} />,
    );

    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveAttribute('aria-modal', 'true');
    expect(dialog).toHaveAccessibleName('발신번호 등록');
  });
});
