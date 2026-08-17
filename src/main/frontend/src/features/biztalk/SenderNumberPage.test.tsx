import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
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

    render(<SenderNumberPage />);

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

    render(<SenderNumberPage />);
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

    render(<SenderNumberPage />);
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

    render(<SenderNumberPage />);
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

    render(<SenderNumberPage />);
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

    render(<SenderNumberPage />);

    expect(await screen.findByRole('option', { name: /△△기관 \(미사용\)/ })).toBeInTheDocument();
  });

  it('D-S8 — 동작하지 않는 버튼을 두지 않는다', async () => {
    vi.stubGlobal(
      'fetch',
      mockFetch((url) =>
        url.includes('/institutions') ? institutionsResponse : senderNumbersResponse,
      ),
    );

    render(<SenderNumberPage />);
    await screen.findByRole('option', { name: /○○기관/ });

    // 레거시는 JS 에 수정 핸들러가 있었으나 버튼이 마크업에 없었다. Sprint S1 은 조회만
    // 제공하므로, 아직 구현되지 않은 동작의 버튼을 먼저 두지 않는다.
    // The legacy had a 수정 handler in JS but no button in the markup. Sprint S1 covers the
    // list only, so no button appears for an operation that does not yet exist.
    expect(screen.queryByRole('button', { name: '등록' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '수정' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '삭제' })).not.toBeInTheDocument();
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

    render(<SenderNumberPage />);
    await screen.findByRole('option', { name: /○○기관/ });
    await userEvent.selectOptions(screen.getByLabelText('이용기관'), 'K0ABCD');

    expect(await screen.findByRole('alert')).toHaveTextContent('발신번호 관리 권한이 없습니다.');
  });
});
