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

/** 두 엔드포인트를 구분해 응답한다. / Answers the two endpoints separately. */
function stubFetch(reportBody: unknown, ok = true) {
  return vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    if (url.includes('/watermark')) {
      return new Response(JSON.stringify(WATERMARK), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    }
    return new Response(JSON.stringify(reportBody), {
      status: ok ? 200 : 400,
      headers: { 'Content-Type': 'application/json' },
    });
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

      const banner = await screen.findByTestId('report-watermark');
      expect(banner).toHaveTextContent('2026-08-14');
      expect(banner).toHaveTextContent('집계되지 않았습니다');
    });

    it('기본 조회 범위를 오늘로 두지 않는다', async () => {
      renderWithProviders(<ReportPage />);

      const to = (await screen.findByLabelText('종료일자')) as HTMLInputElement;
      const today = new Date().toISOString().slice(0, 10).replace(/-/g, '');

      // 기본 종료일자가 오늘이면 화면은 열자마자 미집계 구간을 조회한다 — D-R25 의 원인.
      // A default end date of today queries an un-aggregated window on open — D-R25's cause.
      expect(to.value).not.toBe(today);
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
      expect(screen.getAllByText('처리중')).toHaveLength(COLUMNS.length);
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

      expect(await screen.findByTestId('report-empty')).toHaveTextContent('조회된 내용이 없습니다');
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

  describe('발송구분 / source filter', () => {
    it('전체·API발송·대량발송을 고를 수 있고 기본은 전체다', async () => {
      renderWithProviders(<ReportPage />);

      const select = (await screen.findByLabelText('발송구분')) as HTMLSelectElement;
      expect(select.value).toBe('ALL');
      expect([...select.options].map((o) => o.textContent)).toEqual([
        '전체',
        'API발송',
        '대량발송',
      ]);
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

      expect(await screen.findByTestId('report-total')).toHaveTextContent('총 1건');
    });

    /** 상한을 넘으면 정확한 값을 아는 척하지 않는다(ADR-RPT-021). */
    it('건수 상한을 넘으면 정확한 수를 주장하지 않는다', async () => {
      vi.stubGlobal('fetch', stubFetch(page({ totalCount: null, hasMore: true })));

      renderWithProviders(<ReportPage />);

      expect(await screen.findByTestId('report-total')).toHaveTextContent('많음');
    });
  });
});
