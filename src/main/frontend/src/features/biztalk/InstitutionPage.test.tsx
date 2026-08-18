import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { renderWithProviders } from '../../test/renderWithProviders';
import { InstitutionPage } from './InstitutionPage';
import { formatTimestamp } from '../../api/institutionApi';

/**
 * {@link InstitutionPage} 컴포넌트 테스트. / Component tests.
 *
 * req: FR-INST-001, FR-INST-002, FR-INST-003, FR-INST-006, FR-INST-008, FR-ATK-002, FR-AZ-I03
 * source: biztalk_admin_00_view.jsp, biztalk_admin_00.js
 */
describe('InstitutionPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  /** 운영 화면에서 관찰된 형태의 행. / A row shaped like the ones seen in production. */
  const row = {
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

  function stubFetch(status: number, body: unknown) {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: status >= 200 && status < 300,
      status,
      json: async () => body,
    } as Response);
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
  }

  function page(rows = [row], totalCount = 1, pageIndex = 0, totalPages = 1) {
    return { rows, totalCount, page: pageIndex, size: 20, totalPages };
  }

  it('진입 시 목록을 조회한다 / searches on entry', async () => {
    const fetchMock = stubFetch(200, page());

    renderWithProviders(<InstitutionPage />);

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    expect(await screen.findByText('쿠콘_마이데이터사업1본부')).toBeInTheDocument();
  });

  it('레거시 그리드 8컬럼을 표시한다 / renders the legacy grid\'s eight columns', async () => {
    stubFetch(200, page());

    renderWithProviders(<InstitutionPage />);

    const table = await screen.findByRole('table');
    const headers = within(table).getAllByRole('columnheader').map((h) => h.textContent);
    expect(headers).toEqual([
      '기관코드',
      '기관명',
      '영문명',
      '사용여부',
      '인증키',
      '등록일시',
      '수정일시',
      '설명',
    ]);
  });

  // ---------------------------------------------------------------------------
  // D-I5 회귀 — 인증키 노출 / D-I5 regression — 인증키 exposure
  // ---------------------------------------------------------------------------

  it('D-I5: 마스킹된 인증키만 표시한다 / shows only the masked 인증키', async () => {
    stubFetch(200, page());

    renderWithProviders(<InstitutionPage />);

    // 레거시 화면은 전 기관의 인증키를 평문 컬럼으로 렌더링했다 — 화면 캡처 한 장이면
    // 모든 고객사 키가 함께 나간다.
    // The legacy rendered every institution's key in a plaintext column: one screenshot took
    // the whole set with it.
    expect(await screen.findByText('****************ohVF')).toBeInTheDocument();
    expect(screen.queryByText(/89uJFb0wEm1N4MjXohVF/)).not.toBeInTheDocument();
  });

  it('D-I5: 문서 어디에도 평문 키 형태가 없다 / no plaintext key shape appears in the document', async () => {
    stubFetch(200, page());

    renderWithProviders(<InstitutionPage />);
    await screen.findByRole('table');

    // 20자 연속 영숫자 = 레거시 생성기가 만든 키의 형태.
    // Twenty consecutive alphanumerics is the shape the legacy generator produced.
    expect(document.body.textContent ?? '').not.toMatch(/\b[A-Za-z0-9]{20}\b/);
  });

  // ---------------------------------------------------------------------------
  // FR-INST-006 — 상태 표시 / status rendering
  // ---------------------------------------------------------------------------

  it('상태 라벨을 표시한다 / renders the status label', async () => {
    stubFetch(200, page([{ ...row, status: 'N', statusLabel: '미사용' }]));

    renderWithProviders(<InstitutionPage />);

    expect(await screen.findByText('미사용')).toBeInTheDocument();
  });

  it('FR-INST-006: 매핑되지 않는 상태를 원문으로 표시한다 / renders an unmapped status verbatim', async () => {
    stubFetch(200, page([{ ...row, status: 'X', statusLabel: 'X' }]));

    renderWithProviders(<InstitutionPage />);

    const table = await screen.findByRole('table');
    // 레거시는 'Y' 가 아닌 모든 값을 '미사용' 으로 표시해 데이터 이상을 감췄다.
    // The legacy showed anything but 'Y' as 미사용, hiding the anomaly.
    expect(within(table).getByText('X')).toBeInTheDocument();
    expect(within(table).queryByText('미사용')).not.toBeInTheDocument();
  });

  // ---------------------------------------------------------------------------
  // FR-INST-008 / D-I9 — 시각 표시 / timestamp rendering
  // ---------------------------------------------------------------------------

  it('FR-INST-008: 등록·수정일시에 시각까지 표시한다 / shows the time, not only the date', async () => {
    stubFetch(200, page());

    renderWithProviders(<InstitutionPage />);

    // 레거시는 substring(0,8) 로 날짜만 표시했고, 그 절단이 D-I9 을 가렸다.
    // The legacy displayed substring(0,8), date only, and that truncation hid D-I9.
    expect(await screen.findByText('2021-04-01 12:00:00')).toBeInTheDocument();
    expect(screen.getByText('2026-07-21 13:30:00')).toBeInTheDocument();
  });

  it('D-I9: 잘못된 시각을 교정하지 않고 그대로 노출한다 / a malformed hour is shown, not corrected', async () => {
    // 레거시 등록 SQL 의 to_char(now(),'YYYYMMDD24MISS') 는 HH 가 빠져 시 자리에 리터럴
    // 24 를 기록한다. 표시 단계에서 고치면 데이터 문제가 다시 숨는다.
    // The legacy insert writes a literal 24 where the hour belongs; repairing it at render time
    // would hide the data problem again.
    expect(formatTimestamp('20210401241500')).toBe('2021-04-01 24:15:00');
  });

  // ---------------------------------------------------------------------------
  // FR-INST-003 — 서버 페이징 / server-side paging
  // ---------------------------------------------------------------------------

  it('FR-INST-003: 페이지 이동이 서버 요청을 발생시킨다 / paging issues a server request', async () => {
    const fetchMock = stubFetch(200, page([row], 55, 0, 3));

    renderWithProviders(<InstitutionPage />);
    await screen.findByRole('table');

    await userEvent.click(screen.getByRole('button', { name: '다음' }));

    // 레거시는 전량을 받아 클라이언트에서 잘랐다(D-I10). 페이지 이동이 요청을 만들지
    // 않는다면 서버 페이징이 아니다.
    // The legacy fetched everything and sliced on the client (D-I10). If moving pages issues no
    // request, the paging is not server-side.
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    expect(String(fetchMock.mock.calls[1][0])).toContain('page=1');
  });

  it('첫 페이지에서는 이전 버튼이 비활성이다 / the previous button is disabled on page one', async () => {
    stubFetch(200, page([row], 55, 0, 3));

    renderWithProviders(<InstitutionPage />);
    await screen.findByRole('table');

    expect(screen.getByRole('button', { name: '이전' })).toBeDisabled();
  });

  it('전체 건수를 표시한다 / shows the total count', async () => {
    stubFetch(200, page([row], 55, 0, 3));

    renderWithProviders(<InstitutionPage />);

    expect(await screen.findByText(/총 55 건/)).toBeInTheDocument();
  });

  it('페이지가 하나면 페이지 네비게이션을 감춘다 / hides navigation for a single page', async () => {
    stubFetch(200, page());

    renderWithProviders(<InstitutionPage />);
    await screen.findByRole('table');

    expect(screen.queryByRole('navigation', { name: '페이지' })).not.toBeInTheDocument();
  });

  // ---------------------------------------------------------------------------
  // 조회 조건 / search criteria
  // ---------------------------------------------------------------------------

  it('검색어와 상태를 전송한다 / sends the term and status', async () => {
    const fetchMock = stubFetch(200, page());

    renderWithProviders(<InstitutionPage />);
    await screen.findByRole('table');

    await userEvent.type(screen.getByLabelText('검색'), '쿠콘');
    await userEvent.click(screen.getByLabelText('사용 안함'));
    await userEvent.click(screen.getByRole('button', { name: '조회' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    const url = String(fetchMock.mock.calls[1][0]);
    expect(url).toContain('name=%EC%BF%A0%EC%BD%98');
    expect(url).toContain('status=N');
  });

  it('조건을 바꾸면 첫 페이지로 돌아간다 / a criteria change returns to page one', async () => {
    const fetchMock = stubFetch(200, page([row], 55, 0, 3));

    renderWithProviders(<InstitutionPage />);
    await screen.findByRole('table');
    await userEvent.click(screen.getByRole('button', { name: '다음' }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));

    await userEvent.click(screen.getByRole('button', { name: '조회' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(3));
    expect(String(fetchMock.mock.calls[2][0])).toContain('page=0');
  });

  // ---------------------------------------------------------------------------
  // 오류 경로 / failure paths
  // ---------------------------------------------------------------------------

  it('D-I2: 권한이 없으면 오류를 표시한다 / shows an error when the operator role is missing', async () => {
    stubFetch(403, {});

    renderWithProviders(<InstitutionPage />);

    // 레거시의 '권한 없음' 은 브라우저 안의 alert 였고 서버는 아무도 막지 않았다.
    // 이제 서버가 거부하고 화면은 그 결과를 보고할 뿐이다.
    // The legacy's 권한 없음 was a browser alert while the server refused nobody; now the server
    // refuses and the screen merely reports it.
    expect(await screen.findByRole('alert')).toHaveTextContent('이용기관 관리 권한이 없습니다.');
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('오류 시 이전 결과를 남기지 않는다 / stale rows are cleared on failure', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => page() } as Response)
      .mockResolvedValueOnce({ ok: false, status: 500, json: async () => ({}) } as Response);
    vi.stubGlobal('fetch', fetchMock);

    renderWithProviders(<InstitutionPage />);
    expect(await screen.findByText('쿠콘_마이데이터사업1본부')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: '조회' }));

    // 실패 후에도 이전 행이 남아 있으면 조회에 성공한 것처럼 보인다.
    await waitFor(() => expect(screen.queryByText('쿠콘_마이데이터사업1본부')).not.toBeInTheDocument());
    expect(screen.getByRole('alert')).toBeInTheDocument();
  });

  it('결과가 없으면 빈 상태를 명시한다 / states the empty case explicitly', async () => {
    stubFetch(200, page([], 0));

    renderWithProviders(<InstitutionPage />);

    expect(await screen.findByText('조회 결과가 없습니다.')).toBeInTheDocument();
  });

  // ---------------------------------------------------------------------------
  // 범위 제외 확인 / scope exclusions
  // ---------------------------------------------------------------------------

  it('긴 설명은 CSS 로만 잘린다 / a long description is clipped by CSS, never by the string', async () => {
    /*
      설명 열은 한 줄로 잘려 '…' 로 끝나지만, 그것은 <b>표시</b>일 뿐이다. 문자열을 잘라
      '…' 를 이어 붙이면 잘린 부분이 DOM 에서 사라져 스크린리더도 Ctrl+F 도 원문을 잃는다.
      이 테스트는 셀이 원문 전체를 그대로 들고 있는지만 확인한다 — jsdom 은 레이아웃을
      계산하지 않으므로 '…' 자체는 여기서 관찰할 수 없고, 자르는 일은 CSS 몫이다.

      The 설명 column is clipped to one line ending in an ellipsis, but that is presentation only.
      Truncating the string and appending '…' would drop the tail out of the DOM, losing it for
      screen readers and Ctrl+F. This asserts the cell still carries the whole text; the ellipsis
      itself is not observable here because jsdom does not lay out, and clipping is CSS's job.
    */
    const long =
      '휴대폰 판매 관련 알림톡 전송용도 영업담당자 : 황동섭 부장님 페일백 문자메시지 발송 포함 ' +
      '2022-07-11 수정 / 최초등록 : 비즈플레이 비폰제로페이';
    stubFetch(200, page([{ ...row, description: long }]));

    renderWithProviders(<InstitutionPage />);
    const table = await screen.findByRole('table');

    // 원문이 통째로 남아 있다 / the whole text survives
    const cell = within(table).getByText(long);
    expect(cell).toBeInTheDocument();
    expect(cell.textContent).toBe(long);

    // 마우스 사용자를 위한 전체 문장 / the full sentence for pointer users
    expect(cell).toHaveAttribute('title', long);

    // 자르기는 이 클래스를 통해 CSS 가 한다 / the clipping hook CSS relies on
    expect(cell.closest('td')).toHaveClass('lg-cell-ellipsis');
  });

  it('D-I13: 담당자관리 탭이 없다 / there is no 담당자관리 tab', async () => {
    stubFetch(200, page());

    renderWithProviders(<InstitutionPage />);
    await screen.findByRole('table');

    expect(screen.queryByText('담당자관리')).not.toBeInTheDocument();
  });

  it('D-I13: 동작하지 않는 버튼을 두지 않는다 / no button exists without an operation behind it', async () => {
    stubFetch(200, page());

    renderWithProviders(<InstitutionPage />);
    await screen.findByRole('table');

    // 레거시는 핸들러가 없는 '추가'·'삭제' 버튼을 마크업에 남겨두었다. Sprint I1 은
    // 조회만 제공하므로 쓰기 버튼도 두지 않는다 — Sprint I2 에서 기능과 함께 추가한다.
    // The legacy left 추가/삭제 buttons in the markup with no handlers. Sprint I1 provides only
    // search, so no write buttons appear; they arrive in I2 with the operations behind them.
    for (const label of ['등록', '수정', '중지', '삭제', '재사용']) {
      expect(screen.queryByRole('button', { name: label })).not.toBeInTheDocument();
    }
  });
});
