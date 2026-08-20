import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { SenderNumberRow } from '../../api/senderNumberApi';
import { renderWithProviders } from '../../test/renderWithProviders';
import { SenderNumberDeleteDialog } from './SenderNumberDeleteDialog';

/**
 * {@link SenderNumberDeleteDialog} 컴포넌트 테스트. / Component tests.
 *
 * req: FR-SNDD-002, FR-SNDD-004, FR-SNDD-006, FR-SNDD-007, FR-SNDD-009, FR-SND-007
 * source: biztalk_admin_13_view.jsp, biztalk_admin_10.js — btn_delete
 *
 * 이 대화상자는 하나의 등식을 지킨다: <b>열거된 집합 = 지워지는 집합</b>(FR-SNDD-009). 서버
 * 페이징 때문에 선택된 행이 화면에 없을 수 있으므로, 그 등식은 자동으로 성립하지 않는다.
 *
 * This dialog keeps one equality: <b>the enumerated set equals the deleted set</b> (FR-SNDD-009).
 * With server-side paging a selected row may not be on screen, so the equality does not hold for free.
 */
describe('SenderNumberDeleteDialog', () => {
  function row(ref: string, number: string): SenderNumberRow {
    return {
      ref,
      institutionName: '○○기관',
      number,
      registeredBy: '김*수',
      registeredAt: '20260817090000',
      updatedBy: '김*수',
      updatedAt: '20260817090000',
      description: '대표번호',
    };
  }

  interface Call {
    url: string;
    method: string;
    body: unknown;
  }

  let calls: Call[];

  function stubFetch(response: { status: number; body: unknown } = {
    status: 200,
    body: { affected: 1, ref: null },
  }) {
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      calls.push({
        url,
        method: init?.method ?? 'GET',
        body: init?.body ? JSON.parse(init.body as string) : undefined,
      });
      return {
        ok: response.status < 400,
        status: response.status,
        json: async () => response.body,
      } as Response;
    });
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
  }

  beforeEach(() => {
    calls = [];
    vi.unstubAllGlobals();
  });

  it('FR-SNDD-007 — 지워질 번호를 전부 열거한다', () => {
    stubFetch();
    renderWithProviders(
      <SenderNumberDeleteDialog
        targets={[row('r1', '0212345678'), row('r2', '15881234')]}
        onClose={() => {}}
      />,
    );

    expect(screen.getByText('0212345678')).toBeInTheDocument();
    expect(screen.getByText('15881234')).toBeInTheDocument();
    expect(screen.getByTestId('senderno-delete-count')).toHaveTextContent('선택 2건');
  });

  it('FR-SNDD-009 — 지금 페이지에 없는 행도 열거하고 지운다', async () => {
    stubFetch({ status: 200, body: { affected: 2, ref: null } });
    const onDeleted = vi.fn();

    // 여기 넘어오는 행들은 <b>다른 페이지에서 고른 것</b>일 수 있다. 목록이 서버 페이징이므로
    // 이 대화상자는 현재 응답에 없는 번호도 알고 있어야 하며, 그것이 선택 상태에 행 전체를
    // 들고 있는 이유다. 레거시 그리드는 전체 결과를 브라우저에 들고 있었으므로(D-S14) 이
    // 구분이 존재할 수 없었다 — 서버 페이징으로 D-S14 를 고치면서 이 가능성이 생겼다.
    // The rows arriving here may have been <b>chosen on another page</b>. With server-side paging the
    // dialog must know numbers absent from the current response, which is why the selection holds
    // whole rows. The legacy grid held the entire result set (D-S14) so the distinction could not
    // exist: fixing D-S14 with paging created the possibility.
    renderWithProviders(
      <SenderNumberDeleteDialog
        targets={[row('page1-a', '0212345678'), row('page1-b', '15881234')]}
        onClose={() => {}}
        onDeleted={onDeleted}
      />,
    );

    await userEvent.type(screen.getByLabelText(/사유/), '해지');
    await userEvent.click(screen.getByRole('button', { name: '삭제' }));

    await waitFor(() => expect(onDeleted).toHaveBeenCalledWith(2));
    const post = calls.find((call) => call.method === 'POST');
    expect(post?.body).toMatchObject({ refs: ['page1-a', 'page1-b'], reason: '해지' });
  });

  it('D-S1 — 요청은 ref 를 담고 표시되는 번호를 담지 않는다', async () => {
    stubFetch();
    renderWithProviders(
      <SenderNumberDeleteDialog
        targets={[row('SzBBQkNEHzAyMTIzNDU2Nzg', '0212345678')]}
        onClose={() => {}}
      />,
    );

    await userEvent.type(screen.getByLabelText(/사유/), '해지');
    await userEvent.click(screen.getByRole('button', { name: '삭제' }));

    await waitFor(() => expect(calls.some((call) => call.method === 'POST')).toBe(true));
    const body = calls.find((call) => call.method === 'POST')?.body as {
      refs: string[];
      reason: string;
    };

    // 레거시는 그리드가 가진 값을 콤마로 이어 하나의 DP_NO 로 보냈고, 목록이 그 값을 마스킹하기
    // 시작한 뒤로는 아무 행도 지우지 못한 채 성공을 보고했다(D-S1). 요청 어디에도 표시값이
    // 없어야 한다.
    // The legacy joined the grid's values with commas into one DP_NO; once the list masked that value
    // the delete stopped matching anything while still reporting success (D-S1). No display value may
    // appear anywhere in the request.
    expect(body.refs).toEqual(['SzBBQkNEHzAyMTIzNDU2Nzg']);
    expect(JSON.stringify(body)).not.toContain('0212345678');
  });

  it('FR-SNDD-006 — 사유가 필수다', () => {
    stubFetch();
    renderWithProviders(
      <SenderNumberDeleteDialog targets={[row('r1', '0212345678')]} onClose={() => {}} />,
    );

    expect(screen.getByLabelText(/사유/)).toBeRequired();
  });

  it('FR-SNDD-002 / D-S1 — 409 를 성공으로 보고하지 않는다', async () => {
    stubFetch({
      status: 409,
      body: {
        code: 'NOT_LIVE',
        errors: [{ field: 'refs', message: '삭제 대상 발신번호를 찾을 수 없습니다. 목록을 다시 조회하세요.' }],
      },
    });
    const onClose = vi.fn();
    const onDeleted = vi.fn();
    renderWithProviders(
      <SenderNumberDeleteDialog
        targets={[row('r1', '0212345678')]}
        onClose={onClose}
        onDeleted={onDeleted}
      />,
    );

    await userEvent.type(screen.getByLabelText(/사유/), '해지');
    await userEvent.click(screen.getByRole('button', { name: '삭제' }));

    // 레거시는 이 경우에 "정상적으로 처리되었습니다" 를 보여 주고 팝업을 닫았다. 이 세 단정이
    // 이 화면의 수락 기준이다 — 오류를 보여 주고, 닫지 않고, 성공을 알리지 않는다.
    // The legacy showed a success sentence here and closed the popup. These three assertions are this
    // screen's acceptance criteria: show the error, do not close, do not report success.
    expect(await screen.findByRole('alert')).toHaveTextContent(/찾을 수 없습니다/);
    expect(onClose).not.toHaveBeenCalled();
    expect(onDeleted).not.toHaveBeenCalled();
  });

  it('T-D4 — 발송이 즉시 중단된다는 결과를 고지한다', () => {
    stubFetch();
    renderWithProviders(
      <SenderNumberDeleteDialog targets={[row('r1', '0212345678')]} onClose={() => {}} />,
    );

    // 레거시 화면에는 이 문장이 없었고, 실제로는 아무 일도 일어나지 않았으므로(D-S1) 경고할
    // 것도 없었다. 이제는 실제로 지워지므로 결과를 말한다.
    // The legacy screen said nothing, and since nothing actually happened (D-S1) there was nothing to
    // warn about. It now takes effect, so the consequence is stated.
    expect(screen.getByText(/즉시 발송할 수 없습니다/)).toBeInTheDocument();
  });

  it('Esc 로 닫힌다 / Escape closes the dialog', async () => {
    stubFetch();
    const onClose = vi.fn();
    renderWithProviders(
      <SenderNumberDeleteDialog targets={[row('r1', '0212345678')]} onClose={onClose} />,
    );

    await userEvent.keyboard('{Escape}');
    expect(onClose).toHaveBeenCalled();
  });

  it('모달로 표시된다 / is announced as a modal', () => {
    stubFetch();
    renderWithProviders(
      <SenderNumberDeleteDialog targets={[row('r1', '0212345678')]} onClose={() => {}} />,
    );

    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveAttribute('aria-modal', 'true');
    expect(dialog).toHaveAccessibleName('발신번호 제거');
  });
});
