/**
 * 이용기관 보고서 화면. / The institution usage report screen.
 *
 * req: FR-RPT-001, FR-RPT-009, FR-RPT-010, FR-RPT-012, FR-RPT-013, FR-RPT-014, FR-RPT-015,
 *      FR-RPTS-002, FR-RPTS-005, FR-AZ-R04
 * source: biztalk_admin_20_view.jsp, biztalk_admin_20.js
 *
 * <h2>레거시와 달라진 네 가지 / four differences from the legacy</h2>
 * 1. **처리중 컬럼이 있다.** 레거시는 네 건수 중 셋만 보여 전체 ≠ 성공 + 실패 였다(D-R14).
 * 2. **집계 기준일이 보인다.** 기본 조회 범위가 오늘이었고 배치는 4일 전까지만 채웠으므로,
 *    사용자가 늘 마주친 것은 빈 화면이었다(D-R25).
 * 3. **부분 결과를 부분이라고 말한다.** 대량 집계를 못 읽으면 그 사실을 알린다(FR-RPTS-005).
 * 4. **페이지 번호 대신 이어보기.** 결과가 두 데이터베이스에 걸쳐 있어 offset 으로 자를 수
 *    없다(ADR-RPT-021).
 *
 * 1. **An in-flight column exists** — the legacy showed three of four counters, so
 *    전체 ≠ 성공 + 실패 (D-R14).
 * 2. **The watermark is visible** — its default range was today while the batch only reached
 *    T-4, so what users met was an empty grid (D-R25).
 * 3. **A partial result says so** (FR-RPTS-005).
 * 4. **Seek paging, not page numbers** — the result spans two databases (ADR-RPT-021).
 */

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  fetchReport,
  fetchReportWatermark,
  type ReportQuery,
  type ReportRow,
  type ReportSeek,
  type SendSource,
} from '../../api/reportApi';

/** 오늘로부터 n일 전을 YYYYMMDD 로 반환한다. / Returns n days ago as YYYYMMDD. */
function daysAgo(n: number): string {
  const date = new Date();
  date.setDate(date.getDate() - n);
  return date.toISOString().slice(0, 10).replace(/-/g, '');
}

/** YYYYMMDD 를 YYYY-MM-DD 로 표시한다. / Formats YYYYMMDD for display. */
function formatDate(value: string): string {
  if (value.length !== 8) return value;
  return `${value.slice(0, 4)}-${value.slice(4, 6)}-${value.slice(6, 8)}`;
}

const SOURCES: { value: SendSource; label: string }[] = [
  { value: 'ALL', label: '전체' },
  { value: 'API', label: 'API발송' },
  { value: 'BULK', label: '대량발송' },
];

export function ReportPage() {
  // 기본 조회 범위를 오늘로 두지 않는다. 배치가 T-4 까지만 채우므로 오늘을 기본값으로 두면
  // 화면이 열리자마자 미집계 구간을 조회하게 된다 — 그것이 D-R25 의 사용자 경험이었다.
  // The default range is not today: the batch reaches only T-4, so defaulting to today would
  // query an un-aggregated window on open — the user experience D-R25 describes.
  const [from, setFrom] = useState(() => daysAgo(34));
  const [to, setTo] = useState(() => daysAgo(4));
  const [institution, setInstitution] = useState('');
  const [source, setSource] = useState<SendSource>('ALL');
  const [seek, setSeek] = useState<ReportSeek | null>(null);
  const [history, setHistory] = useState<ReportSeek[]>([]);

  const watermark = useQuery({
    queryKey: ['report', 'watermark', source],
    queryFn: () => fetchReportWatermark(source),
  });

  const query: ReportQuery = {
    from,
    to,
    source,
    institution: institution || undefined,
    seekDate: seek?.tradeDate,
    seekInstitution: seek?.institutionCode,
    size: 100,
  };

  const report = useQuery({
    queryKey: ['report', 'usage', query],
    queryFn: () => fetchReport(query),
    // 조건이 바뀌면 첫 페이지부터 다시 본다. 이어보기 키를 남겨 두면 새 조건의 결과 한가운데로
    // 떨어진다. / Criteria changes restart at page one; a stale seek key would drop the user into
    // the middle of a different result set.
    placeholderData: (previous) => previous,
  });

  const page = report.data;
  const columns = page?.columns ?? [];

  function resetPaging() {
    setSeek(null);
    setHistory([]);
  }

  return (
    <section aria-labelledby="report-heading">
      <h1 id="report-heading">이용기관 보고서</h1>

      {/* ── 집계 기준일 / the watermark ─────────────────────────────────────────── */}
      {watermark.data && (
        <p data-testid="report-watermark" className="infoBanner">
          집계 기준일: API {watermark.data.apiAsOf ?? '알 수 없음'} · 대량{' '}
          {watermark.data.bulkAsOf ?? '알 수 없음'}
          <span className="hint">
            {' '}
            — 이 보고서는 일간 집계 배치가 만든 값을 읽습니다. 기준일 이후 날짜는 아직
            집계되지 않았습니다.
          </span>
        </p>
      )}

      {/* ── 조회 조건 / the criteria ────────────────────────────────────────────── */}
      <form
        className="tb_srchBtn"
        onSubmit={(event) => {
          event.preventDefault();
          resetPaging();
          void report.refetch();
        }}
      >
        <label htmlFor="report-institution">이용기관</label>
        <input
          id="report-institution"
          value={institution}
          placeholder="전체"
          onChange={(event) => {
            setInstitution(event.target.value);
            resetPaging();
          }}
        />

        <label htmlFor="report-source">발송구분</label>
        <select
          id="report-source"
          value={source}
          onChange={(event) => {
            setSource(event.target.value as SendSource);
            resetPaging();
          }}
        >
          {SOURCES.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>

        <label htmlFor="report-from">요청일자</label>
        <input
          id="report-from"
          value={from}
          onChange={(event) => {
            setFrom(event.target.value);
            resetPaging();
          }}
        />
        <span aria-hidden="true"> ~ </span>
        <input
          id="report-to"
          aria-label="종료일자"
          value={to}
          onChange={(event) => {
            setTo(event.target.value);
            resetPaging();
          }}
        />

        <button type="submit">조회</button>
      </form>

      {/* ── 부분 결과 안내 / partial-result notes ───────────────────────────────── */}
      {page?.incompleteNotes.map((note) => (
        <p key={note} role="alert" className="warnBanner" data-testid="incomplete-note">
          {note}
        </p>
      ))}

      {report.isError && (
        <p role="alert" data-testid="report-error">
          {(report.error as Error).message}
        </p>
      )}

      {/* ── 결과 / the result ───────────────────────────────────────────────────── */}
      {page && page.rows.length === 0 && !report.isError && (
        <p data-testid="report-empty">조회된 내용이 없습니다.</p>
      )}

      {page && page.rows.length > 0 && (
        <>
          <table>
            <caption className="srOnly">
              이용기관별 일자별 발송 집계. 채널마다 전체·성공·실패·처리중 네 건수를 표시합니다.
            </caption>
            <thead>
              <tr>
                <th scope="col">구분</th>
                <th scope="col">기관명</th>
                <th scope="col">일자</th>
                {columns.map((column) => (
                  <th key={column.key} scope="col" colSpan={4}>
                    {column.label}
                  </th>
                ))}
              </tr>
              <tr>
                <th scope="col" aria-hidden="true" />
                <th scope="col" aria-hidden="true" />
                <th scope="col" aria-hidden="true" />
                {columns.flatMap((column) =>
                  ['전체', '성공', '실패', '처리중'].map((label) => (
                    <th key={`${column.key}-${label}`} scope="col">
                      {label}
                    </th>
                  )),
                )}
              </tr>
            </thead>
            <tbody>
              {page.rows.map((row) => (
                <ReportTableRow key={`${row.tradeDate}-${row.institutionCode}-${row.source}`} row={row} columnKeys={columns.map((c) => c.key)} />
              ))}
            </tbody>
          </table>

          {/* ── 이어보기 / seek paging ──────────────────────────────────────────── */}
          <nav aria-label="페이지 이동" data-testid="report-paging">
            <button
              type="button"
              disabled={history.length === 0}
              onClick={() => {
                const previous = [...history];
                previous.pop();
                setSeek(previous.length === 0 ? null : previous[previous.length - 1]);
                setHistory(previous);
              }}
            >
              이전
            </button>
            <span data-testid="report-total">
              {page.totalCount == null ? '총 건수 많음' : `총 ${page.totalCount}건`}
            </span>
            <button
              type="button"
              disabled={!page.hasMore || page.nextSeek == null}
              onClick={() => {
                if (page.nextSeek) {
                  setHistory([...history, page.nextSeek]);
                  setSeek(page.nextSeek);
                }
              }}
            >
              다음
            </button>
          </nav>
        </>
      )}
    </section>
  );
}

/** 보고서 1행. / One report row. */
function ReportTableRow({ row, columnKeys }: { row: ReportRow; columnKeys: string[] }) {
  return (
    <tr data-testid="report-row">
      <td>{row.source}</td>
      <td>
        {/*
          기관명이 해결되지 않으면 코드와 미해결 표시를 함께 그린다. 레거시는 빈칸을 그렸고,
          조회가 실패했다는 사실은 어디에도 남지 않았다(D-R12).
          An unresolved name draws the code plus a marker; the legacy drew a blank cell and
          recorded nothing about the failed lookup (D-R12).
        */}
        {row.institutionName ?? (
          <span data-testid="unresolved-institution">
            {row.institutionCode} <em>(기관명 미확인)</em>
          </span>
        )}
      </td>
      <td>
        {formatDate(row.tradeDate)}
        {/*
          산술 항등식이 깨진 행은 사실로 표시하지 않는다. 이 슬라이스는 집계를 고칠 수
          없으므로(CONST-DATA-R01) 고치는 대신 알린다(FR-RPT-010).
          A row whose identity fails is not presented as fact. This slice cannot repair the
          aggregate (CONST-DATA-R01), so it reports instead (FR-RPT-010).
        */}
        {!row.reconciles && (
          <span role="alert" data-testid="reconciliation-warning" title="전체 ≠ 성공 + 실패 + 처리중">
            {' '}⚠ 수치 불일치
          </span>
        )}
      </td>
      {columnKeys.flatMap((key) => {
        const counters = row.counters[key] ?? { total: 0, success: 0, failed: 0, inFlight: 0 };
        return [
          <td key={`${key}-total`}>{counters.total.toLocaleString()}</td>,
          <td key={`${key}-success`}>{counters.success.toLocaleString()}</td>,
          <td key={`${key}-failed`}>{counters.failed.toLocaleString()}</td>,
          <td key={`${key}-inflight`}>{counters.inFlight.toLocaleString()}</td>,
        ];
      })}
    </tr>
  );
}
