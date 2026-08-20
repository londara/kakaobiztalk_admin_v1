import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import axe from 'axe-core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { renderWithProviders } from '../../test/renderWithProviders';
import { InstitutionEditDialog } from './InstitutionEditDialog';

/**
 * {@link InstitutionEditDialog} 컴포넌트 테스트. / Component tests.
 *
 * req: FR-INSTC-001, FR-INSTC-002, FR-INSTC-003, FR-INSTC-010, FR-INSTC-011, FR-INSTC-015,
 *      FR-ATK-002, FR-ATK-005
 * source: biztalk_admin_01_view.jsp, biztalk_admin_01.js
 *
 * 이 팝업의 시험은 대부분 <b>무엇이 없는지</b>를 단언한다 — 평문 인증키, 저장 요청의 키 필드,
 * 확인 없는 재발급, 선택 가능한 '삭제' 상태. 레거시의 결함이 전부 그 자리에 있었다.
 *
 * Most of these cases assert <b>what is absent</b>: the plaintext key, a key field in the save
 * payload, a rotation without confirmation, a selectable 삭제 status. That is where the legacy's
 * defects were.
 */
describe('InstitutionEditDialog', () => {
  /*
    합성된 평문 인증키 표본 — 화면에 나타나면 D-I20 회귀다. 실제 값은 쓰지 않는다: 마스킹
    시험에 자격증명이 필요하지 않고, 저장소에 두면 그것이 곧 노출 경로다(SI2a-01).
    A synthetic plaintext sample; its appearance on screen is a D-I20 regression. Not a real value:
    testing the mask needs no credential, and keeping one in the repo is an exposure path (SI2a-01).
  */
  const RAW_KEY = 'SAMPLEsampleSAMPLE01';

  const detail = {
    code: 'K00001',
    name: '쿠콘_마이데이터사업1본부',
    englishName: 'COOCON_Business1',
    businessNumber: '1234567890',
    authKeyMasked: '****************LE01',
    status: 'Y',
    statusLabel: '사용',
    description: 'TESTSET1',
    registeredAt: '20210401120000',
    lastModifiedAt: '20260721133000',
  };

  interface Call {
    url: string;
    method: string;
    body: unknown;
  }

  let calls: Call[];

  /**
   * URL 과 메서드로 응답을 고르는 fetch 대역. / A fetch stub dispatching on URL and method.
   *
   * @param routes 경로별 응답 / responses by route
   */
  function stubFetch(
    routes: Partial<{
      detail: { status: number; body: unknown };
      update: { status: number; body: unknown };
      rotate: { status: number; body: unknown };
    }>,
  ) {
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      const method = init?.method ?? 'GET';
      calls.push({
        url,
        method,
        body: init?.body ? JSON.parse(init.body as string) : undefined,
      });

      const route = url.endsWith('/key/rotate')
        ? routes.rotate
        : method === 'PUT'
          ? routes.update
          : routes.detail;

      const answer = route ?? { status: 200, body: detail };
      return {
        ok: answer.status >= 200 && answer.status < 300,
        status: answer.status,
        json: async () => answer.body,
      } as Response;
    });
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
  }

  beforeEach(() => {
    calls = [];
    vi.restoreAllMocks();
    document.cookie = 'XSRF-TOKEN=test-token';
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function open(onClose = vi.fn(), onSaved = vi.fn()) {
    const rendered = renderWithProviders(
      <InstitutionEditDialog code="K00001" onClose={onClose} onSaved={onSaved} />,
    );
    return { ...rendered, onClose, onSaved };
  }

  it('상세를 조회해 폼을 채운다 / loads the record into the form', async () => {
    stubFetch({});
    open();

    expect(await screen.findByDisplayValue('쿠콘_마이데이터사업1본부')).toBeInTheDocument();
    expect(screen.getByLabelText(/이용기관영문명/)).toHaveValue('COOCON_Business1');
    expect(screen.getByLabelText(/사업자등록번호/)).toHaveValue('1234567890');
    expect(screen.getByLabelText(/사용 여부/)).toHaveValue('Y');
    expect(screen.getByLabelText('설명')).toHaveValue('TESTSET1');

    // 레거시는 팝업을 열 때 _l002 를 호출했다. 목록 행을 그대로 쓰지 않는 이유는 같다:
    // 지금의 값을 고쳐야 한다.
    // The legacy called its detail service on open, for the same reason: the edit must apply to
    // what is there now.
    expect(calls[0].url).toBe('/api/admin/institutions/K00001');
    expect(calls[0].method).toBe('GET');
  });

  it('이용기관코드는 편집할 수 없다 / the 기관코드 is not editable', async () => {
    stubFetch({});
    open();

    await screen.findByDisplayValue('쿠콘_마이데이터사업1본부');

    // 기관코드는 불변이다(FR-INSTC-002). 비활성 입력칸이 아니라 값으로 표시한다 —
    // 비활성 입력칸은 "고칠 수 있는 값" 으로 읽힌다.
    // The code is immutable (FR-INSTC-002) and is shown as a value, not a disabled input: a
    // disabled input reads as an editable value.
    expect(screen.queryByRole('textbox', { name: /이용기관코드/ })).not.toBeInTheDocument();
    expect(screen.getByText('K00001')).toBeInTheDocument();

    // 중복검사 버튼은 없다. 코드를 고칠 수 없으므로 그 버튼은 아무 일도 할 수 없다.
    // No 중복검사 button: with an immutable code it could never do anything.
    expect(screen.queryByRole('button', { name: '중복검사' })).not.toBeInTheDocument();
  });

  it('평문 인증키가 어디에도 없다 / no plaintext 인증키 anywhere', async () => {
    stubFetch({});
    const { container } = open();

    await screen.findByDisplayValue('쿠콘_마이데이터사업1본부');

    // D-I20 회귀. 레거시는 상세조회가 돌려준 평문을 disabled 입력칸의 value 로 넣었고,
    // 그 서비스는 로그인만 요구했다 — 기관코드를 아는 누구나 남의 키를 읽을 수 있었다.
    // D-I20 regression. The legacy put the plaintext from a login-only service into a disabled
    // input's value, so anyone who knew a code could read another institution's key.
    expect(container.innerHTML).not.toContain(RAW_KEY);
    expect(screen.getByText('****************LE01')).toBeInTheDocument();
  });

  it('사용여부에 삭제 선택지가 없다 / 삭제 is not an option for 사용여부', async () => {
    stubFetch({});
    open();

    const select = await screen.findByLabelText(/사용 여부/);
    const options = within(select).getAllByRole('option').map((o) => o.getAttribute('value'));

    // 'D' 는 논리 삭제 표식이다(ADR-INST-014). 수정 폼으로 그 값에 닿을 수 있으면 확인도
    // 감사 기록도 없는 삭제가 된다(FR-INSTC-015).
    // 'D' is the logical-delete marker; reaching it from the edit form would be a delete with no
    // confirmation and no deletion audit entry (FR-INSTC-015).
    expect(options).toEqual(['Y', 'N']);
  });

  it('저장 요청에 인증키와 기관코드가 없다 / the save payload carries no key and no code', async () => {
    stubFetch({ update: { status: 200, body: { ...detail, name: '새이름' } } });
    const { onSaved, onClose } = open();

    const nameInput = await screen.findByLabelText(/이용기관명/);
    await userEvent.clear(nameInput);
    await userEvent.type(nameInput, '새이름');
    await userEvent.click(screen.getByRole('button', { name: '저장' }));

    await waitFor(() => expect(onClose).toHaveBeenCalled());

    const put = calls.find((call) => call.method === 'PUT');
    expect(put?.url).toBe('/api/admin/institutions/K00001');
    // 화면이 가진 인증키는 마스킹된 값이다. 그것이 저장 요청에 실리면 별표가 자격증명이
    // 되어 고객사 연동이 끊긴다 — 필드가 없으므로 일어날 수 없다(TM-I022).
    // The key the screen holds is masked; sending it would make the asterisks the credential.
    // There is no field for it, so it cannot happen (TM-I022).
    expect(put?.body).toEqual({
      name: '새이름',
      englishName: 'COOCON_Business1',
      businessNumber: '1234567890',
      status: 'Y',
      description: 'TESTSET1',
    });
    expect(onSaved).toHaveBeenCalled();
  });

  it('CSRF 토큰을 함께 보낸다 / sends the CSRF token', async () => {
    const fetchMock = stubFetch({ update: { status: 200, body: detail } });
    open();

    await screen.findByLabelText(/이용기관명/);
    await userEvent.click(screen.getByRole('button', { name: '저장' }));

    await waitFor(() =>
      expect(
        fetchMock.mock.calls.some(
          ([, init]) =>
            (init as RequestInit | undefined)?.method === 'PUT' &&
            ((init as RequestInit).headers as Record<string, string>)['X-XSRF-TOKEN'] ===
              'test-token',
        ),
      ).toBe(true),
    );
  });

  it('서버 검증 오류를 해당 칸에 표시한다 / shows a server violation beside its field', async () => {
    stubFetch({
      update: {
        status: 400,
        body: {
          code: 'VALIDATION_FAILED',
          errors: [{ field: 'businessNumber', message: '사업자등록번호는 숫자 10자리여야 합니다.' }],
        },
      },
    });
    open();

    await screen.findByLabelText(/이용기관명/);
    await userEvent.click(screen.getByRole('button', { name: '저장' }));

    // 레거시는 alert() 을 연달아 띄웠고 규칙은 전부 브라우저에만 있었다(D-I19). 서버가
    // 판정하고 화면은 어느 칸인지 보여준다.
    // The legacy raised a chain of alert() calls with browser-only rules (D-I19). The server
    // decides; the screen shows which box.
    expect(
      await screen.findByText('사업자등록번호는 숫자 10자리여야 합니다.'),
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/사업자등록번호/)).toHaveAttribute('aria-invalid', 'true');
  });

  it('권한이 없으면 그 사실을 알린다 / reports a missing permission', async () => {
    stubFetch({ detail: { status: 403, body: { code: 'FORBIDDEN' } } });
    open();

    expect(await screen.findByText('이용기관 관리 권한이 없습니다.')).toBeInTheDocument();
  });

  it('조회에 실패해도 닫을 수 있다 / it can be closed even when the read failed', async () => {
    stubFetch({ detail: { status: 500, body: { code: 'INTERNAL_ERROR' } } });
    const { onClose } = open();

    // 채울 폼이 없어도 출구는 보여야 한다. 배경 클릭으로 닫지 않으므로(편집 내용 보호)
    // 이 버튼이 사라지면 Esc 를 아는 사용자만 나올 수 있다.
    // A visible exit is required even with no form to fill. A backdrop click does not close it, so
    // without this button only someone who knows about Escape could leave.
    await screen.findByRole('alert');
    await userEvent.click(screen.getByRole('button', { name: '닫기' }));

    expect(onClose).toHaveBeenCalled();
  });

  describe('인증키 재발급 / key rotation', () => {
    it('확인 없이는 재발급하지 않는다 / does not rotate without confirmation', async () => {
      stubFetch({});
      open();

      await screen.findByLabelText(/이용기관명/);
      await userEvent.click(screen.getByRole('button', { name: '키 재발급' }));

      // 확인 문구는 결과를 말한다 — 즉시 적용되고 현재 키의 발송이 실패한다는 것.
      // The confirmation states the consequence: it applies at once and current sends fail.
      expect(screen.getByText(/즉시 적용/)).toBeInTheDocument();
      expect(calls.some((call) => call.url.endsWith('/key/rotate'))).toBe(false);
    });

    it('취소하면 아무 일도 일어나지 않는다 / cancelling changes nothing', async () => {
      stubFetch({});
      open();

      await screen.findByLabelText(/이용기관명/);
      await userEvent.click(screen.getByRole('button', { name: '키 재발급' }));
      await userEvent.click(screen.getByRole('button', { name: '취소' }));

      expect(calls.some((call) => call.url.endsWith('/key/rotate'))).toBe(false);
      expect(screen.queryByText(/즉시 적용/)).not.toBeInTheDocument();
    });

    it('확인하면 서버가 발급한 키를 한 번 보여준다 / shows the server-issued key once', async () => {
      const issued = 'AbCdEfGhIjKlMnOpQrStUvWxYz9';
      stubFetch({ rotate: { status: 200, body: { authKey: issued } } });
      open();

      await screen.findByLabelText(/이용기관명/);
      await userEvent.click(screen.getByRole('button', { name: '키 재발급' }));
      await userEvent.click(screen.getByRole('button', { name: '재발급' }));

      // 레거시는 브라우저에서 Math.random() 으로 만들었다(D-I4). 이제 서버가 만들고,
      // 확인 즉시 확정된다(AMB-I13).
      // The legacy generated it in the browser with Math.random() (D-I4). Now the server does, and
      // it commits on confirmation (AMB-I13).
      expect(await screen.findByDisplayValue(issued)).toBeInTheDocument();
      expect(screen.getByText(/다시 표시되지/)).toBeInTheDocument();

      const rotate = calls.find((call) => call.url.endsWith('/key/rotate'));
      expect(rotate?.method).toBe('POST');
      // 요청은 키를 보내지 않는다 — 값은 서버가 만든다(FR-ATK-001).
      // The request sends no key: the server makes the value (FR-ATK-001).
      expect(rotate?.body).toBeUndefined();
    });

    it('재발급된 키가 저장 요청에 실리지 않는다 / the rotated key never joins the save payload', async () => {
      const issued = 'AbCdEfGhIjKlMnOpQrStUvWxYz9';
      stubFetch({
        rotate: { status: 200, body: { authKey: issued } },
        update: { status: 200, body: detail },
      });
      open();

      await screen.findByLabelText(/이용기관명/);
      await userEvent.click(screen.getByRole('button', { name: '키 재발급' }));
      await userEvent.click(screen.getByRole('button', { name: '재발급' }));
      await screen.findByDisplayValue(issued);
      await userEvent.click(screen.getByRole('button', { name: '저장' }));

      await waitFor(() => expect(calls.some((call) => call.method === 'PUT')).toBe(true));
      const put = calls.find((call) => call.method === 'PUT');
      expect(JSON.stringify(put?.body)).not.toContain(issued);
    });
  });

  describe('닫기 / closing', () => {
    it('변경이 없으면 바로 닫는다 / closes immediately when nothing changed', async () => {
      stubFetch({});
      const { onClose } = open();

      await screen.findByLabelText(/이용기관명/);
      await userEvent.click(screen.getByRole('button', { name: '닫기' }));

      expect(onClose).toHaveBeenCalled();
    });

    it('저장하지 않은 변경이 있으면 확인한다 / confirms when there are unsaved changes', async () => {
      stubFetch({});
      const { onClose } = open();

      const nameInput = await screen.findByLabelText(/이용기관명/);
      await userEvent.type(nameInput, '변경');
      await userEvent.click(screen.getByRole('button', { name: '닫기' }));

      // 레거시는 곧바로 닫아 입력을 잃게 했다.
      // The legacy closed at once and lost the input.
      expect(onClose).not.toHaveBeenCalled();
      expect(screen.getByText(/저장하지 않은 변경/)).toBeInTheDocument();

      await userEvent.click(within(screen.getByRole('alert')).getByRole('button', { name: '닫기' }));
      expect(onClose).toHaveBeenCalled();
    });

    it('Esc 로 닫는다 / Escape closes it', async () => {
      stubFetch({});
      const { onClose } = open();

      await screen.findByLabelText(/이용기관명/);
      await userEvent.keyboard('{Escape}');

      expect(onClose).toHaveBeenCalled();
    });
  });

  it('모달로 알려진다 / announces itself as a modal', async () => {
    stubFetch({});
    open();

    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveAttribute('aria-modal', 'true');
    // 제목이 대화상자의 이름이 된다 — 스크린리더가 무엇이 열렸는지 먼저 읽는다.
    // The heading names the dialog, so a screen reader announces what opened.
    expect(dialog).toHaveAccessibleName('이용기관 수정');
  });

  it('열릴 때 포커스가 팝업 안으로 들어온다 / focus enters the popup on open', async () => {
    stubFetch({});
    open();

    // 조회가 끝나면 첫 입력칸으로 들어간다. 그 전에도 대화상자 자체가 포커스를 받으므로
    // Esc 가 곧바로 동작한다.
    // Once the read finishes, focus lands on the first field. Before that the dialog itself holds
    // focus, which is what makes Escape work from the moment it opens.
    const nameInput = await screen.findByLabelText(/이용기관명/);
    await waitFor(() => expect(nameInput).toHaveFocus());
  });

  it('Tab 이 팝업 안에서 순환한다 / Tab cycles within the popup', async () => {
    stubFetch({});
    open();

    await screen.findByLabelText(/이용기관명/);

    // 마지막 요소에서 Tab 을 누르면 첫 요소로 돌아온다. 순환하지 않으면 포커스가 뒤의 목록
    // 화면으로 빠져나가 무엇을 조작하는지 알 수 없게 된다 — 모달이라고 말하면서 그렇게
    // 동작하지 않는 것이다.
    // Tab from the last element returns to the first. Without cycling, focus escapes to the list
    // behind and it stops being clear what is being operated — saying "modal" while not being one.
    const closeButton = screen.getByRole('button', { name: '닫기' });
    closeButton.focus();
    await userEvent.tab();

    expect(screen.getByLabelText(/이용기관명/)).toHaveFocus();
  });

  it('접근성 위반이 없다 / has no accessibility violations', async () => {
    stubFetch({});
    const { container } = open();

    await screen.findByLabelText(/이용기관명/);

    const results = await axe.run(container, {
      // jsdom 에서 의미 없는 규칙은 제외한다 — 통과 여부가 실제 접근성을 반영하지 않는다.
      // 색상 대비는 실제 렌더링이 없어 검사되지 않고, region 은 팝업만 떼어 검사하기
      // 때문에 의미가 없다.
      // Rules meaningless under jsdom are excluded: contrast needs real rendering, and the region
      // rule has no meaning when a popup is examined on its own.
      rules: { 'color-contrast': { enabled: false }, region: { enabled: false } },
    });

    expect(
      results.violations
        .map((v) => `${v.id} (${v.impact}): ${v.help} [${v.nodes.length} node(s)]`)
        .join('\n'),
    ).toBe('');
  });
});
