import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderWithProviders } from '../../test/renderWithProviders';
import { AlimTalkPage } from './AlimTalkPage';

/**
 * 알림톡 작성 화면 검증. / AlimTalk composition screen verification.
 *
 * req: FR-ATC-010, FR-ATC-012, FR-ATT-001, FR-ATT-002, FR-ATV-001, FR-ATV-003, FR-ATV-006,
 *      FR-ATS-007, FR-AZ-A05, NFR-SEC-PII-A01
 * source: biztalk_admin_61_view.jsp, biztalk_admin_61.js
 */

const institutionsResponse = {
  rows: [
    { code: 'K0ABCD', name: '○○기관', statusLabel: '사용' },
    { code: 'K0EFGH', name: '△△기관', statusLabel: '사용' },
  ],
  totalCount: 2,
  page: 0,
  size: 200,
  totalPages: 1,
};

const readinessResponse = {
  credentialConfigured: true,
  dispatchWired: false,
  blockers: ['발송 경로가 아직 배선되지 않았습니다 — outbox(A2-02) 및 벤더 클라이언트(A2-05).'],
};

const templatesResponse = [
  { templateCode: 'TMPL_0001', templateTitle: '결제 안내' },
  { templateCode: 'TMPL_0002', templateTitle: null },
];

function stubFetch(handlers: Record<string, unknown>) {
  // 두 번째 인자를 받는 이유: 요청 <b>본문</b>을 검사하는 테스트가 있다. 화면에 보이는
  // 결과만으로는 어떤 필드가 조용히 빠졌는지 알 수 없다.
  // The second parameter is declared because some tests inspect the request <b>body</b>: what the
  // screen renders cannot reveal which field was silently dropped.
  return vi.fn(async (input: RequestInfo | URL, _init?: RequestInit) => {
    const url = String(input);
    // 가장 긴(구체적인) 키를 고른다. 삽입 순서로 첫 일치를 쓰면
    // `/alimtalk/templates/validate` 가 `/alimtalk/templates` 핸들러에 걸린다 — 접두어가
    // 겹치는 경로에서 조용히 틀린 응답을 돌려주는 함정이다.
    // Pick the longest (most specific) key. Taking the first match in insertion order would route
    // `/alimtalk/templates/validate` to the `/alimtalk/templates` handler — a trap that returns the
    // wrong response silently when paths share a prefix.
    const key = Object.keys(handlers)
      .filter((k) => url.includes(k))
      .sort((a, b) => b.length - a.length)[0];
    if (!key) {
      throw new Error(`unstubbed request: ${url}`);
    }
    return {
      ok: true,
      status: 200,
      json: async () => handlers[key],
    } as Response;
  });
}

/**
 * 기관 목록이 도착한 뒤에 선택한다. / Selects only once the institution options have arrived.
 *
 * 이 대기가 필요한 이유: useInstitutionOptions 는 렌더 직후 비동기로 채워지므로, 기다리지
 * 않고 선택하면 옵션이 아직 없다. 화면 결함이 아니라 테스트의 경쟁 조건이다.
 * Why the wait is needed: useInstitutionOptions fills asynchronously after render, so selecting
 * without waiting finds no options. A race in the test, not a defect in the screen.
 */
async function selectInstitution(code: string) {
  await waitFor(() =>
    expect(screen.getByLabelText('이용기관코드*')).toHaveTextContent('○○기관'),
  );
  await userEvent.selectOptions(screen.getByLabelText('이용기관코드*'), code);
}

describe('AlimTalkPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('D-A24/FR-AZ-A05 — 발신프로필키 입력란이 존재하지 않는다 / has no sender-key input', async () => {
    // 레거시 화면 61 은 운영자에게 프로파일키를 직접 입력하게 했다. 그 키는 기관을 대신해
    // 발송할 권한 자체이므로, 입력란의 존재가 곧 자격증명 유통을 뜻했다.
    // Legacy screen 61 had the operator type the profile key in. That key is the authority to send on
    // the institution's behalf, so the input box itself meant the credential circulated.
    vi.stubGlobal('fetch', stubFetch({ '/api/admin/institutions': institutionsResponse }));
    renderWithProviders(<AlimTalkPage />);

    expect(screen.queryByLabelText(/발신프로필키/)).toBeNull();
    expect(screen.queryByLabelText(/sender.?key/i)).toBeNull();
    expect(document.body.textContent).not.toMatch(/sender_key/);
  });

  it('D-A15/FR-ATT-001 — 템플릿을 선택 목록에서 고른다 / picks a template from a list', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        '/api/admin/institutions': institutionsResponse,
        '/api/admin/alimtalk/send-readiness': readinessResponse,
        '/api/admin/alimtalk/templates': templatesResponse,
      }),
    );
    renderWithProviders(<AlimTalkPage />);

    // 기관을 고르기 전에는 템플릿 선택이 잠겨 있다 — 기관 없이 조회하면 범위가 없다.
    // The template select is disabled before an institution is chosen: without one there is no scope.
    expect(screen.getByLabelText('템플릿코드*')).toBeDisabled();

    await selectInstitution('K0ABCD');

    await waitFor(() => expect(screen.getByLabelText('템플릿코드*')).toBeEnabled());
    await userEvent.selectOptions(screen.getByLabelText('템플릿코드*'), 'TMPL_0001');

    // FR-ATT-002 — 선택하면 강조표기 제목이 채워진다.
    // FR-ATT-002 — selecting populates the emphasis title.
    expect(screen.getByTestId('emphasis-title')).toHaveTextContent('결제 안내');
  });

  it('FR-ATT-004 — 기관을 바꾸면 템플릿 선택을 버린다 / changing institution discards the template', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        '/api/admin/institutions': institutionsResponse,
        '/api/admin/alimtalk/send-readiness': readinessResponse,
        '/api/admin/alimtalk/templates': templatesResponse,
      }),
    );
    renderWithProviders(<AlimTalkPage />);

    await selectInstitution('K0ABCD');
    await waitFor(() => expect(screen.getByLabelText('템플릿코드*')).toBeEnabled());
    await userEvent.selectOptions(screen.getByLabelText('템플릿코드*'), 'TMPL_0001');

    await selectInstitution('K0EFGH');

    // 다른 기관의 코드는 이 기관에 등록되어 있지 않으므로, 남겨 두면 서버가 거절할 요청이 된다.
    // Another institution's code is not registered here, so keeping it builds a request the server
    // must reject.
    expect(screen.getByLabelText('템플릿코드*')).toHaveValue('');
  });

  it('FR-ATV-003 — 미등록 템플릿을 불일치와 구분해 보고한다 / reports unregistered distinctly', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        '/api/admin/institutions': institutionsResponse,
        '/api/admin/alimtalk/send-readiness': readinessResponse,
        '/api/admin/alimtalk/templates': templatesResponse,
        '/api/admin/alimtalk/templates/validate': {
          registered: false,
          permitsSend: false,
          variableValues: {},
          divergences: [{ position: 0, templatePart: 'TMPL_0001', reason: '등록되지 않음' }],
        },
      }),
    );
    renderWithProviders(<AlimTalkPage />);

    await selectInstitution('K0ABCD');
    await waitFor(() => expect(screen.getByLabelText('템플릿코드*')).toBeEnabled());
    await userEvent.selectOptions(screen.getByLabelText('템플릿코드*'), 'TMPL_0001');
    await userEvent.type(screen.getByLabelText('메시지*'), '아무 내용');
    await userEvent.click(screen.getByRole('button', { name: '템플릿 검증' }));

    await waitFor(() =>
      expect(screen.getByTestId('validation-result')).toHaveTextContent('등록되지 않은'),
    );
  });

  it('FR-ATV-006 — 불일치를 첫 건만이 아니라 모두 보여준다 / shows every divergence', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        '/api/admin/institutions': institutionsResponse,
        '/api/admin/alimtalk/send-readiness': readinessResponse,
        '/api/admin/alimtalk/templates': templatesResponse,
        '/api/admin/alimtalk/templates/validate': {
          registered: true,
          permitsSend: false,
          variableValues: {},
          divergences: [
            { position: 3, templatePart: '원 결제', reason: '고정 문구 불일치' },
            { position: 9, templatePart: '되었습니다', reason: '고정 문구 불일치' },
          ],
        },
      }),
    );
    renderWithProviders(<AlimTalkPage />);

    await selectInstitution('K0ABCD');
    await waitFor(() => expect(screen.getByLabelText('템플릿코드*')).toBeEnabled());
    await userEvent.selectOptions(screen.getByLabelText('템플릿코드*'), 'TMPL_0001');
    await userEvent.type(screen.getByLabelText('메시지*'), '틀린 내용');
    await userEvent.click(screen.getByRole('button', { name: '템플릿 검증' }));

    // 레거시는 네 개의 조기 반환 경로 모두에서 첫 오류만 돌려주었으므로 운영자가 시도마다
    // 하나씩 고쳐야 했다.
    // The legacy returned only the first error from all four exit paths, so an operator fixed one
    // per attempt.
    await waitFor(() => expect(screen.getByTestId('divergences').children).toHaveLength(2));
  });

  it('D-A26/FR-ATS-007 — 제외될 수신번호를 발송 전에 보여준다 / shows exclusions before sending', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        '/api/admin/institutions': institutionsResponse,
        '/api/admin/alimtalk/send-readiness': readinessResponse,
        '/api/admin/alimtalk/recipients/preview': {
          validCount: 2,
          duplicatesRemoved: 1,
          excluded: ['0212345678'],
          maskedRecipients: ['010****5678', '010****8888'],
          requiresConfirmation: true,
          tranId: 'A260818001',
        },
      }),
    );
    renderWithProviders(<AlimTalkPage />);

    await selectInstitution('K0ABCD');
    await userEvent.type(screen.getByLabelText('수신번호*'), '01011112222, 0212345678');
    await userEvent.click(screen.getByRole('button', { name: '수신번호 확인' }));

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('제외'));
    expect(screen.getByTestId('recipient-preview')).toHaveTextContent('발송 대상 2건');
    expect(screen.getByTestId('recipient-preview')).toHaveTextContent('중복 1건 제거');
    expect(screen.getByTestId('tran-id')).toHaveTextContent('A260818001');
  });

  /**
   * 레거시의 반복 행 구조. / the legacy's repeatable rows.
   *
   * source: biztalk_admin_61_view.jsp #receiverNumberContainer, biztalk_admin_61.js:451
   * req: FR-ATC-003, FR-ATC-010
   */
  it('FR-ATC-003 — 수신번호 행을 추가하면 모든 행이 함께 전송된다 / added rows are all submitted', async () => {
    const fetchMock = stubFetch({
      '/api/admin/institutions': institutionsResponse,
      '/api/admin/alimtalk/send-readiness': readinessResponse,
      '/api/admin/alimtalk/recipients/preview': {
        validCount: 2,
        duplicatesRemoved: 0,
        excluded: [],
        maskedRecipients: ['010****2222', '010****3333'],
        requiresConfirmation: false,
        tranId: null,
      },
    });
    vi.stubGlobal('fetch', fetchMock);
    renderWithProviders(<AlimTalkPage />);

    await selectInstitution('K0ABCD');
    await userEvent.type(screen.getByLabelText('수신번호 1'), '01011112222');
    await userEvent.click(screen.getByRole('button', { name: '수신번호 추가' }));
    await userEvent.type(screen.getByLabelText('수신번호 2'), '01011113333');
    await userEvent.click(screen.getByRole('button', { name: '수신번호 확인' }));

    await waitFor(() =>
      expect(screen.getByTestId('recipient-preview')).toHaveTextContent('발송 대상 2건'),
    );

    // 두 행이 모두 요청에 담겼는지 본다 — 화면에 보이는 것만으로는 두 번째 행이 조용히
    // 버려졌는지 알 수 없다. 그것이 레거시 다건 탭에서 실제로 일어난 종류의 결함이다.
    // Assert both rows reached the request. What the screen shows cannot tell us whether the second
    // row was silently dropped — the class of defect that actually occurred in the legacy batch tab.
    const previewCall = fetchMock.mock.calls.find((c) =>
      String(c[0]).includes('/recipients/preview'),
    );
    const body = JSON.parse(String((previewCall?.[1] as RequestInit | undefined)?.body ?? '{}'));
    expect(body.recipients).toBe('01011112222,01011113333');
  });

  it('FR-ATC-003 — 삭제는 해당 행만 지운다 / 삭제 removes only that row', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        '/api/admin/institutions': institutionsResponse,
        '/api/admin/alimtalk/send-readiness': readinessResponse,
      }),
    );
    renderWithProviders(<AlimTalkPage />);

    await selectInstitution('K0ABCD');
    await userEvent.type(screen.getByLabelText('수신번호 1'), '01011112222');
    await userEvent.click(screen.getByRole('button', { name: '수신번호 추가' }));
    await userEvent.type(screen.getByLabelText('수신번호 2'), '01011113333');
    await userEvent.click(screen.getByRole('button', { name: '수신번호 추가' }));
    await userEvent.type(screen.getByLabelText('수신번호 3'), '01011114444');

    await userEvent.click(screen.getByRole('button', { name: '수신번호 2 삭제' }));

    expect(screen.getByLabelText('수신번호 1')).toHaveValue('01011112222');
    expect(screen.getByLabelText('수신번호 2')).toHaveValue('01011114444');
    expect(screen.queryByLabelText('수신번호 3')).toBeNull();
  });

  /**
   * 레거시와 의도적으로 다른 점. / a deliberate divergence from the legacy.
   *
   * 레거시는 행이 하나뿐일 때도 삭제 버튼을 보였고, 누르면 입력란이 사라져 되돌릴 방법이
   * 없었다 — 화면을 새로 고치는 것 말고는.
   * The legacy showed 삭제 even on the only row; pressing it removed the input with no way back
   * short of reloading the screen.
   */
  it('FR-ATC-003 — 마지막 남은 행은 삭제할 수 없다 / the last remaining row cannot be deleted', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        '/api/admin/institutions': institutionsResponse,
        '/api/admin/alimtalk/send-readiness': readinessResponse,
      }),
    );
    renderWithProviders(<AlimTalkPage />);

    await selectInstitution('K0ABCD');
    expect(screen.queryByRole('button', { name: '수신번호 1 삭제' })).toBeNull();

    await userEvent.click(screen.getByRole('button', { name: '수신번호 추가' }));
    expect(screen.getByRole('button', { name: '수신번호 1 삭제' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: '수신번호 2 삭제' }));
    expect(screen.queryByRole('button', { name: '수신번호 1 삭제' })).toBeNull();
  });

  it('NFR-SEC-PII-A01 — 수신번호가 마스킹되어 표시된다 / recipients are displayed masked', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        '/api/admin/institutions': institutionsResponse,
        '/api/admin/alimtalk/send-readiness': readinessResponse,
        '/api/admin/alimtalk/recipients/preview': {
          validCount: 1,
          duplicatesRemoved: 0,
          excluded: [],
          maskedRecipients: ['010****5678'],
          requiresConfirmation: false,
          tranId: 'A260818002',
        },
      }),
    );
    renderWithProviders(<AlimTalkPage />);

    await selectInstitution('K0ABCD');
    await userEvent.type(screen.getByLabelText('수신번호*'), '01012345678');
    await userEvent.click(screen.getByRole('button', { name: '수신번호 확인' }));

    await waitFor(() =>
      expect(screen.getByTestId('masked-recipients')).toHaveTextContent('010****5678'),
    );
    // 마스킹은 서버가 한다. 화면이 평문을 받아 가린다면 이미 평문이 네트워크를 건넌 뒤다.
    // The server masks. If the screen received clear values and hid them, the clear value would
    // already have crossed the network.
    expect(screen.getByTestId('masked-recipients').textContent).not.toMatch(/01012345678/);
  });

  it('D-A4/FR-ATC-010 — 초기화가 모든 항목을 지운다 / reset clears everything', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        '/api/admin/institutions': institutionsResponse,
        '/api/admin/alimtalk/send-readiness': readinessResponse,
        '/api/admin/alimtalk/templates': templatesResponse,
        '/api/admin/alimtalk/recipients/preview': {
          validCount: 1,
          duplicatesRemoved: 0,
          excluded: [],
          maskedRecipients: ['010****5678'],
          requiresConfirmation: false,
          tranId: 'A260818003',
        },
      }),
    );
    renderWithProviders(<AlimTalkPage />);

    await selectInstitution('K0ABCD');
    await waitFor(() => expect(screen.getByLabelText('템플릿코드*')).toBeEnabled());
    await userEvent.selectOptions(screen.getByLabelText('템플릿코드*'), 'TMPL_0001');
    await userEvent.type(screen.getByLabelText('메시지*'), '결제되었습니다');
    await userEvent.type(screen.getByLabelText('수신번호*'), '01012345678');
    await userEvent.click(screen.getByRole('button', { name: '수신번호 확인' }));
    await waitFor(() => expect(screen.getByTestId('recipient-preview')).toBeInTheDocument());

    await userEvent.click(screen.getByRole('button', { name: '초기화' }));

    // 레거시 clearBtn 은 세 번째 문장에서 존재하지 않는 #receiver_number 를 참조해 예외를
    // 던졌고, 그 뒤의 모든 초기화가 실행되지 않았다 — 메시지·버튼·출력 JSON 이 남았다.
    // The legacy clearBtn threw on a non-existent #receiver_number in its third statement, so every
    // later reset never ran: message, buttons and output JSON survived.
    //
    // 레거시가 탭 구조였던 것처럼 이 화면도 탭이므로, 초기화가 <b>보이지 않는 탭까지</b>
    // 되돌렸는지 확인하려면 두 탭을 모두 봐야 한다. 눈에 보이는 것만 확인하면 D-A4 의
    // 본질(보이지 않는 곳이 남아 있었다)을 놓친다.
    // The screen is tabbed as the legacy was, so verifying that reset reached the <b>hidden tab too</b>
    // means looking at both. Checking only what is visible would miss the essence of D-A4 — that what
    // survived was the part nobody was looking at.
    expect(screen.getByLabelText('이용기관코드*')).toHaveValue('');
    expect(screen.getByLabelText('수신번호*')).toHaveValue('');
    expect(screen.queryByTestId('recipient-preview')).toBeNull();

    expect(screen.getByLabelText('메시지*')).toHaveValue('');
    expect(screen.queryByTestId('validation-result')).toBeNull();

    await userEvent.click(screen.getByRole('button', { name: '검증' }));
    expect(screen.getByLabelText('Template 입력')).toHaveValue('');
    expect(screen.getByLabelText('Content 입력')).toHaveValue('');
  });

  it('FR-ATV-007 — 레거시의 나란한 두 입력으로 직접 비교할 수 있다 / the legacy side-by-side comparison works', async () => {
    // 레거시 검증 탭은 Template 과 Content 를 나란히 놓아 눈으로 비교하게 했다. 그 배치는
    // 옳았으므로 유지하되, 판정기는 서버에 하나뿐이므로 이 결과와 등록 템플릿 검증 결과가
    // 어긋날 수 없다(FR-ATV-007).
    // The legacy tab set Template and Content side by side for visual comparison. The arrangement is
    // kept, but there is one matcher on the server, so this verdict and the registry-based one cannot
    // disagree.
    vi.stubGlobal(
      'fetch',
      stubFetch({
        '/api/admin/institutions': institutionsResponse,
        '/api/admin/alimtalk/validate': {
          conformant: true,
          variableValues: { name: '김님철수' },
          divergences: [],
          templateError: null,
        },
      }),
    );
    renderWithProviders(<AlimTalkPage />);

    await userEvent.click(screen.getByRole('button', { name: '검증' }));

    await userEvent.type(screen.getByLabelText('Template 입력'), '#{name}님 안녕');
    await userEvent.type(screen.getByLabelText('Content 입력'), '김님철수님 안녕');
    await userEvent.click(screen.getByRole('button', { name: '검증하기' }));

    // 레거시가 거절했던 입력이다(D-A6). 레거시 문구를 그대로 쓰되 결과는 반대다.
    // An input the legacy rejected (D-A6). The wording is the legacy's; the verdict is the opposite.
    await waitFor(() =>
      expect(screen.getByTestId('manual-result')).toHaveTextContent(
        '성공: 템플릿과 입력이 일치합니다.',
      ),
    );
  });

  it('FR-ATV-008 — 템플릿 자체의 문제를 내용 불일치와 구분한다 / a malformed template is distinct from a mismatch', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        '/api/admin/institutions': institutionsResponse,
        '/api/admin/alimtalk/validate': {
          conformant: false,
          variableValues: {},
          divergences: [],
          templateError: 'template uses ${...}; Kakao AlimTalk variables must be written #{...}',
        },
      }),
    );
    renderWithProviders(<AlimTalkPage />);

    await userEvent.click(screen.getByRole('button', { name: '검증' }));

    await userEvent.type(screen.getByLabelText('Template 입력'), '${name}님');
    await userEvent.type(screen.getByLabelText('Content 입력'), '김철수님');
    await userEvent.click(screen.getByRole('button', { name: '검증하기' }));

    // 레거시는 ${...} 를 조용히 받아들여 벤더에서만 실패하게 했다. 여기서는 무엇이
    // 잘못되었는지 화면이 말한다(NFR-USE-A03).
    // The legacy accepted ${...} silently and failed only at the vendor. Here the screen says what is
    // wrong.
    await waitFor(() =>
      expect(screen.getByTestId('manual-result')).toHaveTextContent('#{...}'),
    );
  });

  it('요청이 실패하면 이유를 화면에 남긴다 / a failed request leaves its reason on screen', async () => {
    // 이 테스트가 없어서 버튼이 "동작하지 않는" 것처럼 보였다. 요청이 실패해도 화면에
    // 아무것도 나타나지 않으면, 운영자에게는 고장 난 버튼과 구별되지 않는다 — 레거시가
    // "JSON 생성" 의 결과를 알려주지 않아 생긴 침묵과 같은 종류다.
    // Its absence is why the buttons looked "broken": a request that fails while the screen shows
    // nothing is indistinguishable from a dead button — the same silence as the legacy never
    // reporting what "JSON 생성" achieved.
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        if (url.includes('/api/admin/institutions')) {
          return { ok: true, status: 200, json: async () => institutionsResponse } as Response;
        }
        return {
          ok: false,
          status: 403,
          json: async () => ({ code: '403', message: 'denied' }),
        } as Response;
      }),
    );
    renderWithProviders(<AlimTalkPage />);

    await selectInstitution('K0ABCD');
    await userEvent.type(screen.getByLabelText('수신번호*'), '01011112222');
    await userEvent.click(screen.getByRole('button', { name: '수신번호 확인' }));

    await waitFor(() =>
      expect(screen.getByTestId('recipients-error')).toHaveTextContent('권한이 없습니다'),
    );
  });

  it('버튼이 잠긴 이유를 말한다 / a disabled button says why', async () => {
    // 조건이 맞지 않아 잠긴 버튼과 고장 난 버튼은 화면에서 같아 보인다. 이유를 적으면
    // 구별된다(NFR-USE-A03).
    // A button disabled by unmet conditions looks the same as a broken one; stating the reason
    // distinguishes them.
    vi.stubGlobal('fetch', stubFetch({ '/api/admin/institutions': institutionsResponse }));
    renderWithProviders(<AlimTalkPage />);

    expect(screen.getByRole('button', { name: '수신번호 확인' })).toBeDisabled();
    expect(screen.getByText('이용기관코드를 먼저 선택하세요.')).toBeInTheDocument();
    expect(screen.getByText('템플릿코드를 먼저 선택하세요.')).toBeInTheDocument();
  });

  it('D-A3 — 다건 탭에서 메시지 데이터를 추가하고 payload 를 만든다 / batch items compose a payload', async () => {
    // 이 탭은 한 번 "A2-10 에서 제공됩니다" 로 미뤄졌는데 잘못된 판단이었다. 미룰 이유로
    // 들었던 항목별 order 는 이미 DTO 에 있고 계약 테스트가 단언하며, FR-ATC-004 는 그것을
    // 시스템이 부여하도록 정한다 — 벤더에게 물을 것이 없다. A2-10 은 배치 <b>발송</b>이다.
    // This tab was once deferred to "A2-10", wrongly: the per-item order it cited is already in the
    // DTO and asserted by the contract test, and FR-ATC-004 has the system assign it. A2-10 is batch
    // <b>despatch</b>.
    vi.stubGlobal(
      'fetch',
      stubFetch({
        '/api/admin/institutions': institutionsResponse,
        '/api/admin/alimtalk/send-readiness': readinessResponse,
        '/api/admin/alimtalk/templates': templatesResponse,
        '/api/admin/alimtalk/compose/batch': {
          payload: '{\n  "is_cd" : "K0ABCD",\n  "msg_data" : [ { "order" : "1" } ]\n}',
          problems: [],
        },
      }),
    );
    renderWithProviders(<AlimTalkPage />);

    await userEvent.click(screen.getByRole('button', { name: '다건 발송' }));
    expect(screen.queryAllByTestId('msg-data-item')).toHaveLength(0);

    await userEvent.click(screen.getByRole('button', { name: '메시지 데이터 추가' }));
    await userEvent.click(screen.getByRole('button', { name: '메시지 데이터 추가' }));

    expect(screen.getAllByTestId('msg-data-item')).toHaveLength(2);
    // 순번은 화면에도 보인다 — 레거시에는 순번 개념 자체가 없었다.
    // The order is visible on screen; the legacy had no notion of it at all.
    expect(screen.getByText(/메시지 1 \(순번 1\)/)).toBeInTheDocument();
    expect(screen.getByText(/메시지 2 \(순번 2\)/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'JSON 생성' }));

    await waitFor(() =>
      expect((screen.getByTestId('output-json') as HTMLTextAreaElement).value).toContain('"order"'),
    );
  });

  /**
   * 출력이 마스킹되었다는 사실을 화면이 말한다. / the screen says the output is masked.
   *
   * 생성된 payload 는 수신번호와 발신프로필키를 가린 채 직렬화한다(백엔드
   * `previewCarriesNeitherCredentialNorClearNumber` 가 고정). 그것을 말하지 않으면 운영자는
   * `010****5678` 이 든 JSON 을 복사해 발송을 시도하고, 실패의 원인을 알 수 없다.
   *
   * The composed payload serialises the recipients and the profile key masked (pinned on the backend by
   * `previewCarriesNeitherCredentialNorClearNumber`). Unstated, an operator copies a JSON containing
   * `010****5678`, attempts a send, and cannot tell why it failed.
   */
  it('NFR-SEC-PII-A01 — 출력이 표본임을 화면이 알린다 / the screen states the output is a sample', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        '/api/admin/institutions': institutionsResponse,
        '/api/admin/alimtalk/send-readiness': readinessResponse,
        '/api/admin/alimtalk/templates': templatesResponse,
        '/api/admin/alimtalk/compose': {
          payload: '{\n  "receiver_number" : [ "010****2222" ],\n  "sender_key" : "ProfileKey[REDACTED]"\n}',
          problems: [],
        },
      }),
    );
    renderWithProviders(<AlimTalkPage />);

    // 생성 전에는 안내가 없다 — 빈 출력창에 대한 설명은 소음이다.
    // Nothing before composing: an explanation of an empty box is noise.
    expect(screen.queryByTestId('output-masking-note')).toBeNull();

    await selectInstitution('K0ABCD');
    await userEvent.click(screen.getByRole('button', { name: 'JSON 생성' }));

    await waitFor(() =>
      expect((screen.getByTestId('output-json') as HTMLTextAreaElement).value).toContain('REDACTED'),
    );
    expect(screen.getByTestId('output-masking-note')).toHaveTextContent('그대로 발송할 수 없습니다');
  });

  it('D-A14 — 다건 항목에 예약발송시간이 있다 / a batch item carries reqdate', async () => {
    // 계약(ADV_KKO_AT_SEND_M)은 항목마다 reqdate 를 선언하는데 레거시 다건 폼은 수집하지
    // 않았다 — 다건 예약 발송이 불가능했던 것은 설계가 아니라 누락이다.
    // The contract declares reqdate per item; the legacy batch form never collected it, so batch
    // reservation was impossible by omission rather than by design.
    vi.stubGlobal(
      'fetch',
      stubFetch({
        '/api/admin/institutions': institutionsResponse,
        '/api/admin/alimtalk/send-readiness': readinessResponse,
        '/api/admin/alimtalk/templates': templatesResponse,
      }),
    );
    renderWithProviders(<AlimTalkPage />);

    await userEvent.click(screen.getByRole('button', { name: '다건 발송' }));
    await userEvent.click(screen.getByRole('button', { name: '메시지 데이터 추가' }));

    expect(screen.getByLabelText('예약발송시간')).toBeInTheDocument();
  });

  it('T-A1 — 발송 버튼이 없다 / there is no send button', async () => {
    vi.stubGlobal('fetch', stubFetch({ '/api/admin/institutions': institutionsResponse }));
    renderWithProviders(<AlimTalkPage />);

    // A2-01(자격증명 관리)은 끝났지만 발송 경로(outbox A2-02, 벤더 클라이언트 A2-05)는
    // 아직 없다. 자격증명이 준비되었다고 해서 보낼 수 있는 것은 아니다.
    // A2-01 (credential management) is done, but the dispatch path (outbox A2-02, vendor client A2-05)
    // is not. A ready credential does not by itself make a send possible.
    expect(screen.queryByRole('button', { name: /^발송$/ })).toBeNull();
  });

  it('A2-01 — 자격증명이 준비되면 그렇게 표시한다 / a ready credential is reported as ready', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        '/api/admin/institutions': institutionsResponse,
        '/api/admin/alimtalk/send-readiness': readinessResponse,
      }),
    );
    renderWithProviders(<AlimTalkPage />);

    await selectInstitution('K0ABCD');

    // 고정 문구가 아니라 서버가 말한 상태다. 고정 문구는 상태가 바뀌어도 그대로 남아
    // 거짓이 된다 — 레거시 화면 61 이 "JSON 생성" 의 결과를 알려주지 않은 것과 같은 침묵.
    // The state comes from the server, not a fixed note. A fixed note survives the state changing and
    // becomes false — the same silence as legacy screen 61 never reporting what "JSON 생성" achieved.
    await waitFor(() =>
      expect(screen.getByTestId('credential-state')).toHaveTextContent('설정됨'),
    );
    expect(screen.getByTestId('send-blockers')).toHaveTextContent('outbox(A2-02)');
  });

  it('A2-01 — 키가 없는 기관은 발송할 수 없다고 알린다 / an unconfigured institution is told it cannot send', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        '/api/admin/institutions': institutionsResponse,
        '/api/admin/alimtalk/send-readiness': {
          credentialConfigured: false,
          dispatchWired: false,
          blockers: [
            '이 이용기관의 발신프로필키가 설정되어 있지 않습니다 (A2-01).',
            '발송 경로가 아직 배선되지 않았습니다 — outbox(A2-02) 및 벤더 클라이언트(A2-05).',
          ],
        },
      }),
    );
    renderWithProviders(<AlimTalkPage />);

    await selectInstitution('K0ABCD');

    // 조용히 아무 키나 쓰는 대신 보낼 수 없다고 말한다 — 다른 기관을 사칭해 고객에게
    // 메시지를 보내는 것이 대안이기 때문이다(T-A2).
    // It says a send is impossible rather than silently using any available key: the alternative is
    // impersonating another institution to a customer (T-A2).
    await waitFor(() =>
      expect(screen.getByTestId('credential-state')).toHaveTextContent('미설정'),
    );
    expect(screen.getByTestId('send-blockers').children).toHaveLength(2);
  });

  it('FR-AZ-A05 — 준비 상태에 키가 어떤 형태로도 나타나지 않는다 / no key material appears in readiness', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        '/api/admin/institutions': institutionsResponse,
        '/api/admin/alimtalk/send-readiness': readinessResponse,
      }),
    );
    renderWithProviders(<AlimTalkPage />);

    await selectInstitution('K0ABCD');
    await waitFor(() => expect(screen.getByTestId('send-readiness')).toBeInTheDocument());

    // "설정되어 있다" 는 운영자가 알아야 할 사실이고, 키 자체는 아니다.
    // "It is configured" is what an operator needs; the key is not.
    expect(document.body.textContent).not.toMatch(/REDACTED/);
    expect(document.body.textContent).not.toMatch(/profile.?key.*[0-9a-f]{16}/i);
  });
});
