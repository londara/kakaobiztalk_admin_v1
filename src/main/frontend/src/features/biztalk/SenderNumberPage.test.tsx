import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderWithProviders } from '../../test/renderWithProviders';
import { SenderNumberPage } from './SenderNumberPage';

/**
 * 발신번호 화면 검증. / Sender-number screen verification.
 *
 * req: FR-SND-001, FR-SND-002, FR-SND-005, FR-SND-006, FR-SND-007, FR-SND-009
 */

const institutionsResponse = {
  rows: [
    { code: 'K0ABCD', name: '○○기관', statusLabel: '사용' },
    { code: 'K0EFGH', name: '△△기관', statusLabel: '미사용' },
  ],
  totalCount: 2,
  page: 0,
  size: 200,
  totalPages: 1,
};

const senderNumbersResponse = {
  rows: [
    {
      ref: 'SzBBQkNEXzAxMDEyMzQ1Njc4',
      institutionName: '○○기관',
      number: '01012345678',
      registeredBy: '김*수',
      registeredAt: '20260817090000',
      updatedBy: '김*수',
      updatedAt: '20260817090000',
      description: '대표번호',
    },
  ],
  totalCount: 1,
  page: 0,
  size: 20,
  totalPages: 1,
};

function mockFetch(handler: (url: string) => unknown) {
  return vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    return {
      ok: true,
      status: 200,
      json: async () => handler(url),
    } as Response;
  });
}

describe('SenderNumberPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('D-S19 회귀 — 기관을 고르기 전에는 발신번호를 조회하지 않는다', async () => {
    const fetchMock = mockFetch((url) =>
      url.includes('/institutions') ? institutionsResponse : senderNumbersResponse,
    );
    vi.stubGlobal('fetch', fetchMock);

    renderWithProviders(<SenderNumberPage />);

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());

    // 레거시는 onload 에서 목록 조회를 먼저 부르고 콤보를 나중에 채웠다.
    // The legacy called the list query in onload and populated the combo afterwards.
    const senderCalls = fetchMock.mock.calls.filter(([u]) =>
      String(u).includes('/sender-numbers'),
    );
    expect(senderCalls).toHaveLength(0);
  });

  it('기관을 고르면 발신번호를 조회한다', async () => {
    const fetchMock = mockFetch((url) =>
      url.includes('/institutions') ? institutionsResponse : senderNumbersResponse,
    );
    vi.stubGlobal('fetch', fetchMock);

    renderWithProviders(<SenderNumberPage />);
    await screen.findByRole('option', { name: /○○기관/ });

    await userEvent.selectOptions(screen.getByLabelText('이용기관'), 'K0ABCD');

    await waitFor(() =>
      expect(
        fetchMock.mock.calls.some(([u]) => String(u).includes('/sender-numbers')),
      ).toBe(true),
    );
  });

  it('AMB-S04 — 발신번호를 마스킹하지 않고 전체로 표시한다', async () => {
    vi.stubGlobal(
      'fetch',
      mockFetch((url) =>
        url.includes('/institutions') ? institutionsResponse : senderNumbersResponse,
      ),
    );

    renderWithProviders(<SenderNumberPage />);
    await screen.findByRole('option', { name: /○○기관/ });
    await userEvent.selectOptions(screen.getByLabelText('이용기관'), 'K0ABCD');

    // 레거시 목록은 01********8 로 보여 주었고 상세는 원본을 보여 주었다. 그 불일치가
    // 삭제를 망가뜨렸다(D-S1).
    // The legacy list showed 01********8 while the detail showed the original; that
    // inconsistency broke deletion (D-S1).
    expect(await screen.findByText('01012345678')).toBeInTheDocument();
    expect(screen.queryByText(/\*{2,}/)).not.toBeInTheDocument();
  });

  it('FR-SND-007 — 식별자는 화면에 표시하지 않는다', async () => {
    vi.stubGlobal(
      'fetch',
      mockFetch((url) =>
        url.includes('/institutions') ? institutionsResponse : senderNumbersResponse,
      ),
    );

    renderWithProviders(<SenderNumberPage />);
    await screen.findByRole('option', { name: /○○기관/ });
    await userEvent.selectOptions(screen.getByLabelText('이용기관'), 'K0ABCD');

    await screen.findByText('01012345678');
    expect(screen.queryByText(senderNumbersResponse.rows[0].ref)).not.toBeInTheDocument();
  });

  it('FR-SND-009 — 등록일자에 시각까지 표시한다', async () => {
    vi.stubGlobal(
      'fetch',
      mockFetch((url) =>
        url.includes('/institutions') ? institutionsResponse : senderNumbersResponse,
      ),
    );

    renderWithProviders(<SenderNumberPage />);
    await screen.findByRole('option', { name: /○○기관/ });
    await userEvent.selectOptions(screen.getByLabelText('이용기관'), 'K0ABCD');

    expect(await screen.findAllByText('2026-08-17 09:00:00')).not.toHaveLength(0);
  });

  it('FR-SND-010 — 이용기관 선택지에 사용 여부를 함께 보여준다', async () => {
    vi.stubGlobal(
      'fetch',
      mockFetch((url) =>
        url.includes('/institutions') ? institutionsResponse : senderNumbersResponse,
      ),
    );

    renderWithProviders(<SenderNumberPage />);

    expect(await screen.findByRole('option', { name: /△△기관 \(미사용\)/ })).toBeInTheDocument();
  });

  it('FR-SNDD-010 / D-S8 — 동작할 수 없는 버튼은 눌리지 않는다', async () => {
    vi.stubGlobal(
      'fetch',
      mockFetch((url) =>
        url.includes('/institutions') ? institutionsResponse : senderNumbersResponse,
      ),
    );

    renderWithProviders(<SenderNumberPage />);
    await screen.findByRole('option', { name: /○○기관/ });

    // Sprint S1 은 이 두 버튼을 "구현 전이므로 비활성" 으로 두었다. S2a 에서 둘 다 연결되었고,
    // 단정의 근거가 <b>바뀌었다</b> — 이제 비활성인 이유는 미구현이 아니라 <b>활성 조건</b>이다
    // (FR-SNDD-010). 등록은 이용기관을, 삭제는 선택을 필요로 한다.
    //
    // 레거시는 조건 없이 팝업을 열었고, 삭제는 선택이 없어도 빈 목록으로 요청을 보냈다 —
    // D-S1 때문에 그것마저 성공으로 보고되었다. 그리고 여전히 '수정' 버튼은 없다: 상세·수정은
    // Sprint S2b 이며, 핸들러만 두고 버튼을 두지 않았던 D-S8 의 반대도 같은 결함이다.
    //
    // Sprint S1 left both buttons disabled *because the operations did not exist*. S2a wires both, so
    // the reason has <b>changed</b>: they are disabled by their <b>enablement conditions</b>
    // (FR-SNDD-010) — 등록 needs an institution, 삭제 needs a selection. The legacy opened its popups
    // regardless and sent an empty list for delete, which D-S1 then reported as success. There is
    // still no 수정 button: detail and edit are Sprint S2b.
    expect(screen.getByRole('button', { name: '등록' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '삭제' })).toBeDisabled();
    expect(screen.queryByRole('button', { name: '수정' })).not.toBeInTheDocument();

    // 기관을 고르면 등록만 열린다. 삭제는 선택이 없으므로 여전히 닫혀 있다.
    // Choosing an institution opens 등록 only; 삭제 stays closed for want of a selection.
    await userEvent.selectOptions(screen.getByLabelText('이용기관'), 'K0ABCD');
    await waitFor(() =>
      expect(screen.getByRole('button', { name: '등록' })).toBeEnabled(),
    );
    expect(screen.getByRole('button', { name: '삭제' })).toBeDisabled();

    // 행을 고르면 삭제가 열린다.
    // Choosing a row opens 삭제.
    await userEvent.click(await screen.findByLabelText('01012345678 선택'));
    expect(screen.getByRole('button', { name: '삭제' })).toBeEnabled();
  });

  it('FR-SNDD-009 — 선택은 페이지를 넘어도 유지되고 확인 화면이 전부 열거한다', async () => {
    // 두 페이지짜리 결과. 각 페이지에 한 행씩 있고 서로 다른 번호다.
    // A two-page result with one row per page and different numbers.
    const pageOne = {
      ...senderNumbersResponse,
      totalCount: 2,
      page: 0,
      totalPages: 2,
    };
    const pageTwo = {
      rows: [
        {
          ...senderNumbersResponse.rows[0],
          ref: 'cGFnZS10d28',
          number: '15881234',
        },
      ],
      totalCount: 2,
      page: 1,
      size: 20,
      totalPages: 2,
    };

    vi.stubGlobal(
      'fetch',
      mockFetch((url) => {
        if (url.includes('/institutions')) {
          return institutionsResponse;
        }
        return url.includes('page=1') ? pageTwo : pageOne;
      }),
    );

    renderWithProviders(<SenderNumberPage />);
    await screen.findByRole('option', { name: /○○기관/ });
    await userEvent.selectOptions(screen.getByLabelText('이용기관'), 'K0ABCD');

    // 1페이지에서 한 건을 고른다.
    // One row is chosen on page 1.
    await userEvent.click(await screen.findByLabelText('01012345678 선택'));
    expect(screen.getByTestId('senderno-selected-count')).toHaveTextContent('선택 1건');

    // 2페이지로 넘어간다. 선택은 유지된다 — 100건 삭제를 조립하는 정상 경로다(NFR-PERF-D03).
    // Move to page 2. The selection survives: this is how a 100-number delete is assembled.
    await userEvent.click(screen.getByRole('button', { name: '다음' }));
    await screen.findByLabelText('15881234 선택');
    expect(screen.getByTestId('senderno-selected-count')).toHaveTextContent('선택 1건');

    // 2페이지에서도 한 건을 고른다.
    // A second row is chosen on page 2.
    await userEvent.click(screen.getByLabelText('15881234 선택'));
    expect(screen.getByTestId('senderno-selected-count')).toHaveTextContent('선택 2건');

    // 확인 화면은 <b>둘 다</b> 열거한다. 1페이지의 번호는 지금 화면에 없다 — 그것을 열거하지
    // 못하면 운영자는 자기가 무엇을 지우는지 확인할 수 없고, 삭제는 보지 못한 것에 걸린다.
    // 이것이 D-S1 의 계열이 서버 페이징을 통해 다시 들어오는 경로다.
    //
    // The confirmation enumerates <b>both</b>. The page-1 number is not on screen: unable to
    // enumerate it, the operator cannot verify what is being deleted and the delete applies to
    // something unseen — D-S1's family re-entering through server-side paging.
    await userEvent.click(screen.getByRole('button', { name: '삭제' }));

    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveAccessibleName('발신번호 제거');
    expect(within(dialog).getByText('01012345678')).toBeInTheDocument();
    expect(within(dialog).getByText('15881234')).toBeInTheDocument();
    expect(within(dialog).getByTestId('senderno-delete-count')).toHaveTextContent('선택 2건');
  });

  it('FR-SNDD-011 — 이용기관을 바꾸면 선택이 비워진다', async () => {
    vi.stubGlobal(
      'fetch',
      mockFetch((url) =>
        url.includes('/institutions') ? institutionsResponse : senderNumbersResponse,
      ),
    );

    renderWithProviders(<SenderNumberPage />);
    await screen.findByRole('option', { name: /○○기관/ });
    await userEvent.selectOptions(screen.getByLabelText('이용기관'), 'K0ABCD');

    await userEvent.click(await screen.findByLabelText('01012345678 선택'));
    expect(screen.getByTestId('senderno-selected-count')).toHaveTextContent('선택 1건');

    // 보이지 않는 기관의 행을 선택된 채로 들고 있으면 삭제가 사용자가 보지 못한 행에 걸린다.
    // 페이지 이동과 달리 여기서는 확인 화면이 그 사실을 드러내 주지도 못한다 — 기관명이
    // 뒤섞인 목록을 운영자가 알아채기를 기대할 수 없다.
    // Keeping rows from an institution no longer displayed would apply the delete to rows the user
    // never saw. Unlike a page move, the confirmation could not reveal it either: expecting an
    // operator to notice a mixed-institution list is not a control.
    await userEvent.selectOptions(screen.getByLabelText('이용기관'), 'K0EFGH');
    await waitFor(() =>
      expect(screen.queryByTestId('senderno-selected-count')).not.toBeInTheDocument(),
    );
    expect(screen.getByRole('button', { name: '삭제' })).toBeDisabled();
  });

  it('FR-SNDC-012 — 등록 팝업은 목록이 고른 이용기관으로 열린다', async () => {
    vi.stubGlobal(
      'fetch',
      mockFetch((url) => {
        if (url.includes('/institutions')) {
          return institutionsResponse;
        }
        if (url.includes('/context')) {
          return { institution: 'K0ABCD', institutionName: '○○기관' };
        }
        return senderNumbersResponse;
      }),
    );

    renderWithProviders(<SenderNumberPage />);
    await screen.findByRole('option', { name: /○○기관/ });
    await userEvent.selectOptions(screen.getByLabelText('이용기관'), 'K0ABCD');

    await waitFor(() => expect(screen.getByRole('button', { name: '등록' })).toBeEnabled());
    await userEvent.click(screen.getByRole('button', { name: '등록' }));

    // 레거시 팝업은 부모 창의 IS_CD 를 받아 열렸다. 그 성질은 유지되지만 이제 타입이 그것을
    // 강제한다 — 대화상자는 이용기관 없이 존재할 수 없다(ADR-SND-020).
    // The legacy popup opened with the opener's IS_CD. That property is kept, and now the type
    // enforces it: the dialog cannot exist without an institution (ADR-SND-020).
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveAccessibleName('발신번호 등록');
    expect(within(dialog).getByText('K0ABCD')).toBeInTheDocument();
  });

  it('FR-SND-007 — 행 선택은 표시값이 아니라 ref 로 관리한다', async () => {
    vi.stubGlobal(
      'fetch',
      mockFetch((url) =>
        url.includes('/institutions') ? institutionsResponse : senderNumbersResponse,
      ),
    );

    renderWithProviders(<SenderNumberPage />);
    await screen.findByRole('option', { name: /○○기관/ });
    await userEvent.selectOptions(screen.getByLabelText('이용기관'), 'K0ABCD');

    const rowCheckbox = await screen.findByLabelText('01012345678 선택');
    expect(rowCheckbox).not.toBeChecked();

    await userEvent.click(rowCheckbox);
    expect(rowCheckbox).toBeChecked();

    // 전체 선택이 함께 반영된다 — 행이 하나뿐이므로.
    // Select-all reflects it too, there being a single row.
    expect(screen.getByLabelText('전체 선택')).toBeChecked();
  });

  it('403 이면 권한 메시지를 보여준다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        if (url.includes('/institutions')) {
          return { ok: true, status: 200, json: async () => institutionsResponse } as Response;
        }
        return {
          ok: false,
          status: 403,
          json: async () => ({ code: 'FORBIDDEN' }),
        } as Response;
      }),
    );

    renderWithProviders(<SenderNumberPage />);
    await screen.findByRole('option', { name: /○○기관/ });
    await userEvent.selectOptions(screen.getByLabelText('이용기관'), 'K0ABCD');

    expect(await screen.findByRole('alert')).toHaveTextContent('발신번호 관리 권한이 없습니다.');
  });
});
