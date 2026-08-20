/**
 * {@link TalkHistoryPage} 검증. / Verification for {@link TalkHistoryPage}.
 *
 * req: FR-TLK-003, FR-TLK-004, FR-TLK-005, FR-TLK-011, FR-TLK-013, FR-TLK-015, NFR-USE-T01
 *
 * 이 파일의 시험은 대부분 회귀 방지다. 화면이 "무엇을 보여주는가" 보다 <b>레거시가 보여주지
 * 않아 문제가 되었던 것을 보여주는가</b>를 확인한다.
 * Most of these are regression guards: less about what the screen shows than about whether it shows
 * the things whose absence was the defect.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '../../test/renderWithProviders';
import { TalkHistoryPage } from './TalkHistoryPage';

const FILTERS = {
  apiServices: [
    { code: 'ADV_KKO_AT_SEND', label: '알림톡 발송' },
    { code: 'ADV_KKO_AT_SEND2', label: '알림톡 발송 (2)' },
  ],
  statuses: [
    { code: '0', label: '처리중' },
    { code: '1', label: '처리완료' },
    { code: '2', label: '기처리' },
    { code: '9', label: '오류' },
  ],
};

function row(overrides: Record<string, unknown> = {}) {
  return {
    transactionDate: '20260819',
    institutionCode: 'K00011',
    institutionName: '비즈플레이_법인카드',
    transactionNo: '00000026081900142813',
    apiServiceCode: 'ADV_KKO_AT_SEND',
    statusCode: '0',
    statusLabel: '처리중',
    responseCode: null,
    registeredAt: '20260819112504',
    completedAt: '20260819112504',
    detailAvailable: true,
    ...overrides,
  };
}

function page(overrides: Record<string, unknown> = {}) {
  return { rows: [row()], totalCount: 1, page: 0, size: 100, totalPages: 1, ...overrides };
}

/** 두 엔드포인트를 구분해 응답한다. / Answers the two endpoints separately. */
function stubFetch(historyBody: unknown, ok = true) {
  return vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    const json = (body: unknown, status: number) =>
      new Response(JSON.stringify(body), {
        status,
        headers: { 'Content-Type': 'application/json' },
      });
    if (url.includes('/filters')) {
      return json(FILTERS, 200);
    }
    return json(historyBody, ok ? 200 : 400);
  });
}

describe('TalkHistoryPage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', stubFetch(page()));
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('선택지가 도착하기 전에는 조회할 수 없다 — D-T28', async () => {
    // 레거시 onload 는 getDat() 를 먼저, fn_fintechSvcSel() 을 나중에 불러 선택기가 비어 있는
    // 상태로 질의가 나갔다. 여기서는 버튼이 비활성이므로 그 순서가 재현될 수 없다.
    // The legacy onload called getDat() before fn_fintechSvcSel(), so the query left with the
    // selector empty. Here the button is disabled, so that ordering cannot recur.
    // 대기 중인 응답을 밖에서 풀 수 있도록 deferred 를 만든다. 클로저 안에서 대입하면 TS 가
    // 변수를 never 로 좁히므로, resolver 를 객체 필드로 두어 좁혀지지 않게 한다.
    // A deferred so the pending response can be resolved from outside. Assigning inside the closure lets TS
    // narrow the variable to never, so the resolver is held as an object field instead.
    const deferred: { resolve: (value: Response) => void } = { resolve: () => undefined };
    const filtersPending = new Promise<Response>((resolve) => {
      deferred.resolve = resolve;
    });

    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        if (String(input).includes('/filters')) {
          return filtersPending;
        }
        return new Response(JSON.stringify(page()), { status: 200 });
      }),
    );

    renderWithProviders(<TalkHistoryPage />);

    expect(screen.getByTestId('talk-search')).toBeDisabled();

    deferred.resolve(
      new Response(JSON.stringify(FILTERS), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    await waitFor(() => expect(screen.getByTestId('talk-search')).toBeEnabled());
  });

  it('상태 선택지를 서버에서 받아 그린다 — D-T29', async () => {
    // 레거시는 필터를 코드 테이블에서 만들고 컬럼 라벨을 자바스크립트에 하드코딩해, 코드가
    // 하나 추가되면 같은 릴리스에서 필터는 가능해지고 컬럼은 알수없음 이 되었다.
    // The legacy built the filter from a code table and hardcoded the column labels, so adding one
    // code made it filterable and rendered it as 알수없음 in the same release.
    renderWithProviders(<TalkHistoryPage />);

    await waitFor(() => expect(screen.getByTestId('talk-search')).toBeEnabled());

    const status = screen.getByTestId('talk-status');
    expect(status).toHaveTextContent('처리중');
    expect(status).toHaveTextContent('처리완료');
    expect(status).toHaveTextContent('기처리');
    expect(status).toHaveTextContent('오류');
  });

  it('API 선택지에 레거시가 빠뜨린 코드가 포함된다 — D-T13', async () => {
    renderWithProviders(<TalkHistoryPage />);

    await waitFor(() => expect(screen.getByTestId('talk-search')).toBeEnabled());

    expect(screen.getByTestId('talk-api-service')).toHaveTextContent('알림톡 발송 (2)');
  });

  it('상태를 라벨과 원값으로 함께 보여준다 — NFR-USE-T01', async () => {
    // 운영자가 제공업체에 코드를 그대로 인용할 수 있어야 한다. 레거시는 라벨만 보여주었고,
    // 미인식 코드는 알수없음 으로 덮어 값의 존재 자체를 숨겼다.
    // An operator must be able to quote the code to a provider. The legacy showed the label alone and
    // masked an unrecognised value as 알수없음, hiding even that a value existed.
    renderWithProviders(<TalkHistoryPage />);

    await waitFor(() => expect(screen.getByTestId('talk-search')).toBeEnabled());
    await userEvent.click(screen.getByTestId('talk-search'));

    await waitFor(() => expect(screen.getByText('처리중 (0)')).toBeInTheDocument());
  });

  it('상세 링크는 서버의 detailAvailable 을 따른다 — D-T13', async () => {
    // 레거시 그리드는 API_SVC_CD.indexOf("KKO") != -1 && PRSU == 1 로 판단했고 서버는 네 개의
    // 정확한 코드만 처리했다. 이 화면은 규칙을 갖지 않으므로 두 판단이 어긋날 수 없다.
    // The legacy grid decided with a substring match and a status check while the server handled four
    // exact codes. This screen holds no rule, so the two cannot disagree.
    vi.stubGlobal(
      'fetch',
      stubFetch(page({ rows: [row(), row({ transactionNo: '999', detailAvailable: false })] })),
    );

    renderWithProviders(<TalkHistoryPage />);
    await waitFor(() => expect(screen.getByTestId('talk-search')).toBeEnabled());
    await userEvent.click(screen.getByTestId('talk-search'));

    await waitFor(() => expect(screen.getAllByTestId('talk-detail-link')).toHaveLength(1));
    // 링크가 없는 행은 번호를 평문으로 보여준다 — 조작할 수 없다는 사실이 드러난다.
    // A row without a link shows the number as plain text, making its unavailability visible.
    expect(screen.getByText('999')).toBeInTheDocument();
  });

  it('총건수와 페이지 수를 보여준다 — D-T11', async () => {
    // 레거시는 페이지 정보를 서버에 넘기고도 건수를 되 읽지 않아 진짜 페이지 수를 알 수 없었다.
    // The legacy passed page info to the server and never read the count back, so it could not know
    // the real page count.
    vi.stubGlobal('fetch', stubFetch(page({ totalCount: 250, totalPages: 3 })));

    renderWithProviders(<TalkHistoryPage />);
    await waitFor(() => expect(screen.getByTestId('talk-search')).toBeEnabled());
    await userEvent.click(screen.getByTestId('talk-search'));

    await waitFor(() =>
      expect(screen.getByTestId('talk-page-indicator')).toHaveTextContent('1 / 3 (총 250건)'),
    );
  });

  it('미해석 기관은 서버가 보낸 표식을 그대로 보여준다 — D-T26', async () => {
    // 레거시는 상관 서브쿼리가 NULL 을 반환하면 빈 칸을 그렸다 — 조회가 실패한 사실이 어디에도
    // 남지 않았다. 빈 칸과 "이름 없는 기관" 은 구분되어야 한다.
    // The legacy drew a blank when the correlated subquery returned NULL, recording nothing about the
    // failed lookup. A blank and "an institution with no name" must be distinguishable.
    vi.stubGlobal(
      'fetch',
      stubFetch(page({ rows: [row({ institutionName: 'K99999 (미등록 기관)' })] })),
    );

    renderWithProviders(<TalkHistoryPage />);
    await waitFor(() => expect(screen.getByTestId('talk-search')).toBeEnabled());
    await userEvent.click(screen.getByTestId('talk-search'));

    await waitFor(() =>
      expect(screen.getByText('K99999 (미등록 기관)')).toBeInTheDocument(),
    );
  });

  it('서버 오류 문구를 그대로 보여준다 — FR-TLK-014', async () => {
    // 기간 상한 위반은 사용자가 고칠 수 있다. "요청이 실패했습니다" 로 덮어쓰면 고칠 방법을
    // 숨긴다 — 레거시 다운로드가 숨은 iframe 으로 실패해 아무 메시지도 없던 것과 같은 종류다.
    // A period-cap violation is fixable by the user; replacing the message hides how. The same class
    // as the legacy download failing into a hidden frame with no message at all.
    vi.stubGlobal(
      'fetch',
      stubFetch({ message: '조회 기간은 최대 31일입니다 (요청: 92일).' }, false),
    );

    renderWithProviders(<TalkHistoryPage />);
    await waitFor(() => expect(screen.getByTestId('talk-search')).toBeEnabled());
    await userEvent.click(screen.getByTestId('talk-search'));

    await waitFor(() =>
      expect(screen.getByTestId('talk-error')).toHaveTextContent('최대 31일'),
    );
  });

  it('조회 전에는 빈 그리드와 안내를 보여준다', async () => {
    // 빈 결과와 "아직 조회하지 않음" 은 다른 상태다. 레거시는 둘을 같은 빈 그리드로 보여주었다.
    // An empty result and "not queried yet" are different states; the legacy showed both as one
    // empty grid.
    renderWithProviders(<TalkHistoryPage />);

    await waitFor(() => expect(screen.getByTestId('talk-search')).toBeEnabled());
    expect(screen.getByText('조회 조건을 입력한 뒤 조회를 누르세요.')).toBeInTheDocument();
  });

  it('조회 전에는 다운로드가 비활성이며 그 이유를 밝힌다', async () => {
    // 눌렀는데 아무 일도 일어나지 않는 버튼은 이 프로그램이 여섯 슬라이스 연속으로 만난 실패
    // 양식이다. 조회 전에는 내보낼 조건이 없으므로 비활성이고, title 이 이유를 말한다.
    // A button that does nothing when pressed is the failure mode this programme has met in six consecutive
    // slices. Before a query there are no criteria to export, so it is disabled and the title says why.
    renderWithProviders(<TalkHistoryPage />);

    const download = screen.getByTestId('talk-download');
    expect(download).toBeDisabled();
    expect(download).toHaveAttribute('title', expect.stringContaining('조회 후'));
  });

  it('다운로드가 조회와 같은 조건으로 요청된다 — D-T1', async () => {
    // ⚠ 이것이 D-T1 의 클라이언트 쪽 회귀 테스트다. 레거시 fn_makeExcel() 은 화면에 없는 DOM
    // 요소 일곱 개(#IS_LIST, #MSGKEY, #PHONE, #CALLBACK, #RSLT, #STATUS, #MSG_TYPE)에서 값을
    // 읽었으므로 모든 필터가 빈 문자열이 되었고, 파일에는 조건과 무관하게 모든 기관의 모든
    // 메시지가 담겼다. 여기서는 내보내기 요청의 쿼리스트링이 조회와 같은 값을 담아야 한다.
    //
    // This is D-T1's client-side regression test. The legacy fn_makeExcel() read from seven DOM elements the
    // screen does not have, so every filter became the empty string and the file held every institution's
    // messages regardless of the criteria. Here the export request's query string must carry the same values
    // as the query.
    const calls: string[] = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        calls.push(url);
        if (url.includes('/filters')) {
          return new Response(JSON.stringify(FILTERS), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          });
        }
        if (url.includes('/export')) {
          return new Response(new Blob(['x']), {
            status: 200,
            headers: {
              'Content-Disposition': "attachment; filename*=UTF-8''%ED%86%A1.xlsx",
              'X-Talk-Export-Rows': '7',
            },
          });
        }
        return new Response(JSON.stringify(page()), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        });
      }),
    );
    // jsdom 은 createObjectURL 을 제공하지 않는다. / jsdom provides no createObjectURL.
    vi.stubGlobal('URL', {
      ...URL,
      createObjectURL: vi.fn(() => 'blob:stub'),
      revokeObjectURL: vi.fn(),
    });

    renderWithProviders(<TalkHistoryPage />);
    await waitFor(() => expect(screen.getByTestId('talk-search')).toBeEnabled());

    await userEvent.clear(screen.getByTestId('talk-serial'));
    await userEvent.type(screen.getByTestId('talk-serial'), '142813');
    await userEvent.selectOptions(screen.getByTestId('talk-status'), '9');
    await userEvent.click(screen.getByTestId('talk-search'));

    await waitFor(() => expect(screen.getByTestId('talk-download')).toBeEnabled());
    await userEvent.click(screen.getByTestId('talk-download'));

    await waitFor(() => {
      const exportCall = calls.find((url) => url.includes('/export'));
      expect(exportCall).toBeDefined();
      // 화면에 걸린 두 조건이 내보내기 요청에 그대로 실려야 한다.
      // Both filters set on the screen must ride on the export request.
      expect(exportCall).toContain('serial=142813');
      expect(exportCall).toContain('status=9');
    });
  });

  it('내보낸 행 수를 사용자에게 알린다 — FR-TLKX-007', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        if (url.includes('/filters')) {
          return new Response(JSON.stringify(FILTERS), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          });
        }
        if (url.includes('/export')) {
          return new Response(new Blob(['x']), {
            status: 200,
            headers: { 'X-Talk-Export-Rows': '250' },
          });
        }
        return new Response(JSON.stringify(page()), { status: 200 });
      }),
    );
    vi.stubGlobal('URL', {
      ...URL,
      createObjectURL: vi.fn(() => 'blob:stub'),
      revokeObjectURL: vi.fn(),
    });

    renderWithProviders(<TalkHistoryPage />);
    await waitFor(() => expect(screen.getByTestId('talk-search')).toBeEnabled());
    await userEvent.click(screen.getByTestId('talk-search'));
    await waitFor(() => expect(screen.getByTestId('talk-download')).toBeEnabled());
    await userEvent.click(screen.getByTestId('talk-download'));

    // 감사 기록에 남는 수와 대조할 수 있어야 한다. 레거시는 내보내기 기록이 아예 없었다.
    // Must be reconcilable against the audit record; the legacy recorded no export at all.
    await waitFor(() =>
      expect(screen.getByTestId('talk-export-note')).toHaveTextContent('250건'),
    );
  });

  it('내보내기 실패가 화면에 보인다 — D-T23', async () => {
    // ⚠ 레거시는 frm0 을 ifrmFileProc 라는 대상으로 제출했는데, 그 이름의 프레임은 어떤 뷰에도
    // 없고 fintech.common.submit 이 만들어 주지도 않는다. 실패하면 사용자에게 파일도, 메시지도,
    // 아무 일이 일어났다는 표시조차 없었다.
    // The legacy submitted frm0 to a target named ifrmFileProc, a frame no view declares and which
    // fintech.common.submit does not create. On failure the user got no file, no message and no indication
    // that anything had happened at all.
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        if (url.includes('/filters')) {
          return new Response(JSON.stringify(FILTERS), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          });
        }
        if (url.includes('/export')) {
          return new Response(
            JSON.stringify({ message: '내보낼 건수가 상한을 초과했습니다 (150000건 / 상한 100000건).' }),
            { status: 400, headers: { 'Content-Type': 'application/json' } },
          );
        }
        return new Response(JSON.stringify(page()), { status: 200 });
      }),
    );

    renderWithProviders(<TalkHistoryPage />);
    await waitFor(() => expect(screen.getByTestId('talk-search')).toBeEnabled());
    await userEvent.click(screen.getByTestId('talk-search'));
    await waitFor(() => expect(screen.getByTestId('talk-download')).toBeEnabled());
    await userEvent.click(screen.getByTestId('talk-download'));

    // 서버 문구를 그대로 올린다 — 상한 초과는 사용자가 범위를 좁혀 고칠 수 있다.
    // The server's message is surfaced: an exceeded ceiling is fixable by narrowing the range.
    await waitFor(() =>
      expect(screen.getByTestId('talk-export-error')).toHaveTextContent('상한'),
    );
  });

  it('거래고유번호 링크가 거래 상세내역을 연다 — D-T13', async () => {
    // 레거시는 링크는 걸고 팝업은 비웠다(ADV_KKO_AT_SEND2). 이제 서버가 detailAvailable 을
    // 계산하고 링크는 실제로 조회되는 패널을 연다.
    // The legacy linked and left the popup empty (ADV_KKO_AT_SEND2). The server now computes
    // detailAvailable, and the link opens a panel that actually queries.
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        if (url.includes('/filters')) {
          return new Response(JSON.stringify(FILTERS), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          });
        }
        if (url.includes('/messages')) {
          return new Response(
            JSON.stringify({ rows: [], totalCount: 0, page: 0, size: 10, totalPages: 0 }),
            { status: 200, headers: { 'Content-Type': 'application/json' } },
          );
        }
        return new Response(JSON.stringify(page()), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        });
      }),
    );

    renderWithProviders(<TalkHistoryPage />);
    await waitFor(() => expect(screen.getByTestId('talk-search')).toBeEnabled());
    await userEvent.click(screen.getByTestId('talk-search'));
    await waitFor(() => expect(screen.getByTestId('talk-detail-link')).toBeInTheDocument());

    await userEvent.click(screen.getByTestId('talk-detail-link'));

    await waitFor(() =>
      expect(screen.getByTestId('talk-txn-detail')).toBeInTheDocument(),
    );
  });
});
