import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MessageHistoryPage } from './MessageHistoryPage';

/**
 * {@link MessageHistoryPage} 컴포넌트 테스트. / Component tests.
 *
 * req: FR-MSG-002, FR-MSG-004, FR-MSG-005, FR-MSG-007, FR-MSG-009, FR-TEN-003/004
 * source: biztalk_admin_40_view.jsp, biztalk_admin_40.js
 */
describe('MessageHistoryPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  const row = {
    messageType: 'AT',
    messageTypeLabel: '알림톡',
    tableType: 'SMS',
    messageKey: 12345,
    institutionCode: 'IS001',
    status: '2',
    statusLabel: '전송완료',
    resultCode: '0',
    senderNumber: '1588-****',
    recipientNumber: '010-****-**64',
    requestDate: '20260814091233',
    requestTime: '091233',
    sentTime: '091235',
    reportTime: '091241',
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

  function page(rows = [row], totalCount = 1) {
    return { rows, totalCount, page: 0, size: 50, totalPages: 1 };
  }

  it('12개 그리드 컬럼을 표시한다 / renders the twelve grid columns', async () => {
    // req: FR-MSG-004 — source: biztalk_admin_40.js gridColName
    const user = userEvent.setup();
    stubFetch(200, page());
    render(<MessageHistoryPage operator={false} />);

    await user.click(screen.getByRole('button', { name: '조회' }));

    await waitFor(() => expect(screen.getByRole('table')).toBeInTheDocument());
    const headers = screen.getAllByRole('columnheader').map((h) => h.textContent);
    expect(headers).toEqual([
      '유형', '테이블', '메시지키', '이용기관', '상태', '톡결과',
      '발송번호', '수신번호', '요청일자', '요청시간', '발송시간', '응답시간',
    ]);
  });

  it('D3 회귀: 발송번호·수신번호 라벨이 올바른 컬럼을 필터링한다 / D3 regression: labels filter the right columns', async () => {
    // 레거시: 발신번호→PHONE(수신 컬럼), 수신번호→CALLBACK(발신 컬럼)
    const user = userEvent.setup();
    const fetchMock = stubFetch(200, page());
    render(<MessageHistoryPage operator={false} />);

    await user.type(screen.getByLabelText('발송번호'), '15883987');
    await user.type(screen.getByLabelText('수신번호'), '01089136864');
    await user.click(screen.getByRole('button', { name: '조회' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    const body = JSON.parse(String((fetchMock.mock.calls[0] as [string, RequestInit])[1].body));
    expect(body.senderNumber).toBe('15883987');
    expect(body.recipientNumber).toBe('01089136864');
  });

  it('FR-TEN-004: 이용기관 선택은 운영자에게만 보인다 / the 이용기관 field renders for operators only', () => {
    // 레거시는 모든 사용자에게 전체 고객사 명단을 드롭다운으로 제공했다 (TM-011)
    const { unmount } = render(<MessageHistoryPage operator={false} />);
    expect(screen.queryByLabelText('이용기관')).not.toBeInTheDocument();
    unmount();

    render(<MessageHistoryPage operator />);
    expect(screen.getByLabelText('이용기관')).toBeInTheDocument();
  });

  it('이용기관 담당자는 이용기관을 전송하지 않는다 / a tenant user does not send an institution code', async () => {
    // req: FR-TEN-001 — 서버가 무시하지만 보내지 않는 것이 의도를 분명히 한다
    const user = userEvent.setup();
    const fetchMock = stubFetch(200, page());
    render(<MessageHistoryPage operator={false} />);

    await user.click(screen.getByRole('button', { name: '조회' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    const body = JSON.parse(String((fetchMock.mock.calls[0] as [string, RequestInit])[1].body));
    expect(body.institutionCode).toBeUndefined();
  });

  it('상태 라벨을 표시한다 / renders the status label', async () => {
    // req: FR-MSG-005
    const user = userEvent.setup();
    stubFetch(200, page());
    render(<MessageHistoryPage operator={false} />);

    await user.click(screen.getByRole('button', { name: '조회' }));

    // 표 안으로 범위를 좁힌다. 상태 <select> 의 <option> 에도 같은 문자열이 있으므로
    // 전역 조회는 모호하다 — 라벨이 실제로 <b>셀에</b> 표시되는지가 검증 대상이다.
    // Scoped to the table: the status <select> contains the same strings, so a global query is
    // ambiguous — what matters is that the label appears in the cell.
    await waitFor(() => expect(screen.getByRole('table')).toBeInTheDocument());
    const table = within(screen.getByRole('table'));
    expect(table.getByText('전송완료')).toBeInTheDocument();
    expect(table.getByText('알림톡')).toBeInTheDocument();
  });

  it('조회 조건 위반을 전부 표시한다 / shows every criteria violation', async () => {
    // req: FR-MSG-012, FR-MSG-013 — 레거시는 alert 하나로 첫 항목만 알렸다(D8)
    const user = userEvent.setup();
    stubFetch(400, {
      code: 'INVALID_CRITERIA',
      violations: ['조회 기간은 최대 31일까지 가능합니다.', '시작일시가 종료일시보다 이후일 수 없습니다.'],
    });
    render(<MessageHistoryPage operator={false} />);

    await user.click(screen.getByRole('button', { name: '조회' }));

    // 위반 목록(ul.violations) 안으로 범위를 좁힌다. 기간 상한 안내문이 field-help 에도
    // 같은 문자열로 존재하므로 전역 조회는 모호하다 — 두 곳에 같은 문장이 있는 것은
    // 의도적이다(안내문은 규칙을 미리 알리고, 위반 메시지는 그 규칙을 어겼음을 알린다).
    // Scoped to the violations list: the same sentence appears in the help text, deliberately —
    // the help states the rule in advance, the violation reports breaking it.
    await waitFor(() => {
      const list = document.querySelector('ul.violations');
      expect(list).not.toBeNull();
      const items = within(list as HTMLElement);
      expect(items.getByText('조회 기간은 최대 31일까지 가능합니다.')).toBeInTheDocument();
      expect(items.getByText('시작일시가 종료일시보다 이후일 수 없습니다.')).toBeInTheDocument();
    });
  });

  it('빈 결과를 오류와 구분하여 표시한다 / distinguishes an empty result from an error', async () => {
    // req: FR-MSG-020 — 레거시는 둘 다 빈 그리드로 표시했다
    const user = userEvent.setup();
    stubFetch(200, { rows: [], totalCount: 0, page: 0, size: 50, totalPages: 0 });
    render(<MessageHistoryPage operator={false} />);

    await user.click(screen.getByRole('button', { name: '조회' }));

    await waitFor(() => expect(screen.getByText('조회 결과가 없습니다.')).toBeInTheDocument());
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('메시지키가 조작 가능한 button 이다 / the message key is an actionable button', async () => {
    // 레거시는 <a onclick> 이었다 — 키보드 접근 불가 (WCAG 2.1.1)
    const user = userEvent.setup();
    stubFetch(200, page());
    render(<MessageHistoryPage operator={false} />);

    await user.click(screen.getByRole('button', { name: '조회' }));

    await waitFor(() => expect(screen.getByRole('button', { name: '12345' })).toBeInTheDocument());
  });

  it('서버 페이징을 사용한다 / uses server-side paging', async () => {
    // req: FR-MSG-007 — 레거시는 전량을 받아 클라이언트에서 페이징했다(D7)
    const user = userEvent.setup();
    const fetchMock = stubFetch(200, { rows: [row], totalCount: 120, page: 0, size: 50, totalPages: 3 });
    render(<MessageHistoryPage operator={false} />);

    await user.click(screen.getByRole('button', { name: '조회' }));
    await waitFor(() => expect(screen.getByText(/총/)).toBeInTheDocument());

    const nav = screen.getByRole('navigation', { name: '페이지 이동' });
    expect(within(nav).getByRole('button', { name: '이전' })).toBeDisabled();
    expect(within(nav).getByRole('button', { name: '다음' })).toBeEnabled();

    await user.click(within(nav).getByRole('button', { name: '다음' }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    const secondBody = JSON.parse(String((fetchMock.mock.calls[1] as [string, RequestInit])[1].body));
    expect(secondBody.page).toBe(1);
  });

  it('메시지키 입력은 숫자만 받는다 / the message key accepts digits only', async () => {
    // req: FR-MSG-008, CONST-DATA-03
    const user = userEvent.setup();
    render(<MessageHistoryPage operator={false} />);
    const input = screen.getByLabelText('메시지키');
    await user.type(input, '12a34b');
    expect(input).toHaveValue('1234');
  });

  it('요청은 POST 이며 세션 쿠키를 포함한다 / posts with credentials', async () => {
    // req: NFR-SEC-PII — 조회 조건에 전화번호가 포함될 수 있어 GET 을 쓰지 않는다
    const user = userEvent.setup();
    const fetchMock = stubFetch(200, page());
    render(<MessageHistoryPage operator={false} />);

    await user.click(screen.getByRole('button', { name: '조회' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/message-history/search');
    expect(init.method).toBe('POST');
    expect(init.credentials).toBe('same-origin');
  });
});
