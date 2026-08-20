/**
 * {@link ReportPage} 검증. / Verification for {@link ReportPage}.
 *
 * req: FR-RPT-009, FR-RPT-010, FR-RPT-012, FR-RPT-013, FR-RPT-014, FR-RPTS-002, FR-RPTS-005
 *
 * 이 파일의 시험은 대부분 회귀 방지다. 화면이 "무엇을 보여주는가" 보다 <b>레거시가 보여주지
 * 않아 문제가 되었던 것을 보여주는가</b>를 확인한다.
 * Most of these are regression guards: less about what the screen shows than about whether it
 * shows the things whose absence was the defect.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { renderWithProviders } from '../../test/renderWithProviders';
import { ReportPage } from './ReportPage';

const WATERMARK = { apiAsOf: '2026-08-14', bulkAsOf: '2026-08-14', effectiveAsOf: '2026-08-14' };

const COLUMNS = [
  { key: 'ALIMTALK', label: '알림톡' },
  { key: 'SMS', label: 'sms' },
];

function counters(total: number, success: number, failed: number, inFlight: number) {
  return { total, success, failed, inFlight };
}

function page(overrides: Record<string, unknown> = {}) {
  return {
    rows: [
      {
        source: '전체',
        tradeDate: '20260731',
        institutionCode: 'K0001',
        institutionName: '○○기관',
        counters: {
          ALIMTALK: counters(1204, 1180, 20, 4),
          SMS: counters(50, 49, 1, 0),
        },
        grandTotal: 1254,
        reconciles: true,
      },
    ],
    columns: COLUMNS,
    nextSeek: null,
    totalCount: 1,
    hasMore: false,
    watermark: WATERMARK,
    incompleteNotes: [],
    ...overrides,
  };
}

/** 세 엔드포인트를 구분해 응답한다. / Answers the three endpoints separately. */
function stubFetch(reportBody: unknown, ok = true) {
  return vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    const json = (body: unknown, status: number) =>
      new Response(JSON.stringify(body), {
        status,
        headers: { 'Content-Type': 'application/json' },
      });

    if (url.includes('/watermark')) {
      return json(WATERMARK, 200);
    }
    // 이용기관 선택 목록. 보고서 응답을 그대로 돌려주면 code/name 이 없는 항목이 되어
    // 드롭다운이 조용히 비어 보이므로 별도로 답한다.
    // The institution picker: returning the report body here would yield options with no
    // code/name and a silently blank dropdown, so it is answered separately.
    if (url.includes('/institutions/search')) {
      return json({ rows: [{ code: 'K0001', name: '○○기관' }], totalCount: 1, page: 0, size: 200 }, 200);
    }
    return json(reportBody, ok ? 200 : 400);
  });
}

describe('ReportPage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', stubFetch(page()));
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  describe('집계 기준일 / the watermark', () => {
    /**
     * D-R25 회귀. 레거시 화면은 기본 조회 범위가 오늘이었고 배치는 T-4 까지만 채웠으므로,
     * 사용자가 화면을 열자마자 마주친 것은 빈 그리드였다 — 그리고 그 이유는 어디에도 적혀
     * 있지 않았다.
     * D-R25 regression: the legacy defaulted to today while the batch reached only T-4, so users
     * met an empty grid on open — with the reason written nowhere.
     */
    it('집계 기준일을 묻지 않아도 보여준다', async () => {
      renderWithProviders(<ReportPage />);

      // 껍데기는 즉시 렌더링되고 값은 뒤따라 온다(레거시와 같은 구조). findBy* 는 첫
      // 렌더에서 곧바로 해소되므로, 값이 도착한 상태를 기다려야 한다.
      // The shell renders at once and the values follow, as in the legacy. findBy* resolves on
      // that first render, so the settled state has to be waited for explicitly.
      const banner = await screen.findByTestId('report-watermark');
      await waitFor(() => expect(banner).toHaveTextContent('2026-08-14'));
      expect(banner).toHaveTextContent('집계되지 않았습니다');
    });

    it('기본 조회 범위를 오늘로 두지 않는다', async () => {
      renderWithProviders(<ReportPage />);

      const to = (await screen.findByLabelText('종료일자')) as HTMLInputElement;
      const today = new Date().toISOString().slice(0, 10);

      // 기본 종료일자가 오늘이면 화면은 열자마자 미집계 구간을 조회한다 — D-R25 의 원인.
      // A default end date of today queries an un-aggregated window on open — D-R25's cause.
      expect(to.value).not.toBe(today);
      expect(to.value < today).toBe(true);
    });
  });

  describe('컬럼 / columns', () => {
    /**
     * D-R14 회귀. 레거시 그리드는 전체·성공·실패만 보여, 처리중이 있는 행에서
     * 전체 ≠ 성공 + 실패 였다.
     * D-R14 regression: the legacy grid showed only 전체/성공/실패, so on any row with in-flight
     * messages 전체 ≠ 성공 + 실패.
     */
    it('처리중 컬럼을 채널마다 보여준다', async () => {
      renderWithProviders(<ReportPage />);

      await screen.findByTestId('report-row');
      // 레거시 그리드와 같은 평면 머리글이므로 채널명이 앞에 붙는다.
      // The legacy's flat header prefixes each counter with its channel name.
      for (const column of COLUMNS) {
        expect(
          screen.getByRole('columnheader', { name: `${column.label}처리중` }),
        ).toBeInTheDocument();
      }
    });

    it('레거시 그리드의 고정 3열을 그대로 쓴다', async () => {
      renderWithProviders(<ReportPage />);

      await screen.findByTestId('report-row');
      for (const label of ['구분', '기관명', '일자']) {
        expect(screen.getByRole('columnheader', { name: label })).toBeInTheDocument();
      }
    });

    /**
     * 조회 전에도 어떤 열이 있는지 보여야 한다 — 레거시도 빈 상태에서 머리글을 갖춘
     * 그리드를 보여 주고 본문에만 안내를 넣었다.
     * The columns must be visible before a query; the legacy showed a full-header grid and put
     * the message in the body.
     */
    it('결과가 없어도 그리드 머리글은 남는다', async () => {
      vi.stubGlobal('fetch', stubFetch(page({ rows: [], totalCount: 0 })));

      renderWithProviders(<ReportPage />);

      await screen.findByTestId('report-empty');
      expect(screen.getByRole('columnheader', { name: '구분' })).toBeInTheDocument();
    });

    it('한 행에서 전체 = 성공 + 실패 + 처리중 이 눈으로 확인된다', async () => {
      renderWithProviders(<ReportPage />);

      const row = await screen.findByTestId('report-row');
      // 1204 = 1180 + 20 + 4
      expect(row).toHaveTextContent('1,204');
      expect(row).toHaveTextContent('1,180');
    });
  });

  describe('기관명 미해결 / unresolved institution', () => {
    /**
     * D-R12 회귀. 레거시는 기관명 조회에 실패하면 빈칸을 그렸고, 실패했다는 사실은 어디에도
     * 남지 않았다.
     * D-R12 regression: a failed name lookup drew a blank cell and left no trace.
     */
    it('기관명이 없으면 코드와 미확인 표시를 그린다', async () => {
      const body = page();
      (body.rows[0] as Record<string, unknown>).institutionName = null;
      vi.stubGlobal('fetch', stubFetch(body));

      renderWithProviders(<ReportPage />);

      const marker = await screen.findByTestId('unresolved-institution');
      expect(marker).toHaveTextContent('K0001');
      expect(marker).toHaveTextContent('기관명 미확인');
    });
  });

  describe('산술 불일치 / reconciliation', () => {
    /**
     * FR-RPT-010. 집계를 만든 배치가 실패를 삼키고 성공을 보고하므로(D-R27), 항등식이 깨진
     * 행은 사실로 표시하지 않는다.
     * FR-RPT-010. Because the batch swallows failures while reporting success (D-R27), a row
     * whose identity fails is not presented as fact.
     */
    it('항등식이 깨진 행에 경고를 표시한다', async () => {
      const body = page();
      (body.rows[0] as Record<string, unknown>).reconciles = false;
      vi.stubGlobal('fetch', stubFetch(body));

      renderWithProviders(<ReportPage />);

      expect(await screen.findByTestId('reconciliation-warning')).toBeInTheDocument();
    });

    it('정상 행에는 경고가 없다', async () => {
      renderWithProviders(<ReportPage />);

      await screen.findByTestId('report-row');
      expect(screen.queryByTestId('reconciliation-warning')).not.toBeInTheDocument();
    });
  });

  describe('부분 결과 / partial results', () => {
    /**
     * FR-RPTS-005. 조용한 부분 보고는 이 프로그램이 네 슬라이스 연속으로 만난 실패 방식이며,
     * 이 화면에서는 고객사 발송량이 실제보다 적게 보이는 결과가 된다.
     * FR-RPTS-005. Silent partial reporting is the failure mode met in four consecutive slices;
     * here it would under-report a customer's volume.
     */
    it('대량 집계를 못 읽으면 눈에 띄게 알린다', async () => {
      vi.stubGlobal(
        'fetch',
        stubFetch(
          page({
            incompleteNotes: ['대량발송 집계를 읽지 못했습니다. 표시된 수치는 불완전합니다.'],
          }),
        ),
      );

      renderWithProviders(<ReportPage />);

      const note = await screen.findByTestId('incomplete-note');
      expect(note).toHaveTextContent('대량발송');
      expect(note).toHaveAttribute('role', 'alert');
    });

    it('완전한 결과에는 안내가 없다', async () => {
      renderWithProviders(<ReportPage />);

      await screen.findByTestId('report-row');
      expect(screen.queryByTestId('incomplete-note')).not.toBeInTheDocument();
    });
  });

  describe('빈 결과와 오류 / empty and error', () => {
    /** FR-RPT-014: 빈 결과·오류·미집계는 서로 다른 상태다. */
    it('빈 결과를 오류와 구분해 표시한다', async () => {
      vi.stubGlobal('fetch', stubFetch(page({ rows: [], totalCount: 0 })));

      renderWithProviders(<ReportPage />);

      const empty = await screen.findByTestId('report-empty');
      await waitFor(() => expect(empty).toHaveTextContent('조회된 내용이 없습니다'));
      expect(screen.queryByTestId('report-error')).not.toBeInTheDocument();
    });

    /** D-R9: 기간 상한 초과는 서버가 거부하고, 화면은 그 설명을 그대로 보여준다. */
    it('서버가 거부하면 그 설명을 보여준다', async () => {
      vi.stubGlobal(
        'fetch',
        stubFetch({ message: '조회 기간은 최대 366일입니다 (요청: 400일).' }, false),
      );

      renderWithProviders(<ReportPage />);

      await waitFor(async () => {
        expect(await screen.findByTestId('report-error')).toHaveTextContent('366일');
      });
    });
  });

  describe('조회 조건 / the criteria', () => {
    /**
     * 레거시와 같이 조회 조건은 두 가지뿐이다. 발송구분은 노출하지 않는다.
     * The criteria are the legacy's two; no source picker is shown.
     */
    it('이용기관과 요청일자 둘만 조회 조건으로 둔다', async () => {
      renderWithProviders(<ReportPage />);

      expect(await screen.findByLabelText('이용기관')).toBeInTheDocument();
      expect(screen.getByLabelText('요청일자')).toBeInTheDocument();
      expect(screen.getByLabelText('종료일자')).toBeInTheDocument();
      expect(screen.queryByLabelText('발송구분')).not.toBeInTheDocument();
    });

    it('이용기관은 전체를 포함한 선택 목록이다', async () => {
      renderWithProviders(<ReportPage />);

      const select = (await screen.findByLabelText('이용기관')) as HTMLSelectElement;
      expect(select.value).toBe('');
      await waitFor(() =>
        expect([...select.options].map((option) => option.textContent)).toEqual([
          '전체',
          '○○기관',
        ]),
      );
    });

    /**
     * 발송구분을 노출하지 않아도 합산은 그대로다 — 어느 출처에서 온 행인지는 그리드
     * 첫 열 `구분` 이 보여준다(FR-RPTS-003).
     * Dropping the picker does not drop the merge; the grid's `구분` column still shows which
     * source a row came from (FR-RPTS-003).
     */
    it('합산 결과의 구분은 그리드에 그대로 남는다', async () => {
      renderWithProviders(<ReportPage />);

      const row = await screen.findByTestId('report-row');
      expect(row).toHaveTextContent('전체');
      expect(screen.getByRole('columnheader', { name: '구분' })).toBeInTheDocument();
    });
  });

  describe('페이징 / paging', () => {
    /** ADR-RPT-021: 결과가 두 데이터베이스에 걸쳐 있어 페이지 번호가 아니라 이어보기를 쓴다. */
    it('다음 페이지가 없으면 다음 버튼이 비활성이다', async () => {
      renderWithProviders(<ReportPage />);

      const paging = await screen.findByTestId('report-paging');
      const next = paging.querySelector('button:last-of-type') as HTMLButtonElement;
      expect(next).toBeDisabled();
    });

    it('전체 건수를 표시한다', async () => {
      renderWithProviders(<ReportPage />);

      const total = await screen.findByTestId('report-total');
      await waitFor(() => expect(total).toHaveTextContent('총 1건'));
    });

    /** 상한을 넘으면 정확한 값을 아는 척하지 않는다(ADR-RPT-021). */
    it('건수 상한을 넘으면 정확한 수를 주장하지 않는다', async () => {
      vi.stubGlobal('fetch', stubFetch(page({ totalCount: null, hasMore: true })));

      renderWithProviders(<ReportPage />);

      expect(await screen.findByTestId('report-total')).toHaveTextContent('많음');
    });
  });
});
