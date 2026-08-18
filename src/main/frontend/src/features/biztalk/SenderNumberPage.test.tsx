import { screen, waitFor } from '@testing-library/react';
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

  it('D-S8 — 구현되지 않은 동작에 눌리는 버튼을 두지 않는다', async () => {
    vi.stubGlobal(
      'fetch',
      mockFetch((url) =>
        url.includes('/institutions') ? institutionsResponse : senderNumbersResponse,
      ),
    );

    renderWithProviders(<SenderNumberPage />);
    await screen.findByRole('option', { name: /○○기관/ });

    // 레거시 배치를 맞추기 위해 등록·삭제 자리는 두되, 동작이 구현되기 전까지는
    // <b>비활성</b>이어야 한다. 단정하는 대상은 "버튼의 부재" 가 아니라 "눌리는 버튼의
    // 부재" 다 — 레거시는 수정 핸들러만 두고 버튼을 두지 않았고(D-S8), 그 반대인
    // "버튼만 있고 동작이 없는" 상태도 같은 결함이다.
    //
    // The 등록/삭제 slots exist so the layout matches the legacy, but must stay <b>disabled</b>
    // until the operations land. What is asserted is not the absence of a button but the absence
    // of a *pressable* one: the legacy had a 수정 handler with no button (D-S8), and the reverse
    // — a button with no operation — is the same defect.
    expect(screen.getByRole('button', { name: '등록' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '삭제' })).toBeDisabled();
    expect(screen.queryByRole('button', { name: '수정' })).not.toBeInTheDocument();
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
