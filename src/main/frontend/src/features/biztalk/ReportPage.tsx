/**
 * 이용기관 보고서 화면. / The institution usage report screen.
 *
 * req: FR-RPT-001, FR-RPT-009, FR-RPT-010, FR-RPT-012, FR-RPT-013, FR-RPT-014, FR-RPT-015,
 *      FR-RPTS-003, FR-RPTS-005, FR-AZ-R04
 * source: biztalk_admin_20_view.jsp, biztalk_admin_20.js — drawGrid() colDefs
 *
 * <p><b>FR-RPTS-002 는 이 화면이 제공하지 않는다.</b> 조회 조건은 레거시와 같이 이용기관과
 * 요청일자 둘뿐이며, 발송구분 선택은 노출하지 않는다. 합산 동작(FR-RPTS-003)과 서버의
 * {@code source} 파라미터는 그대로이므로 되살리는 데 계약 변경이 필요하지 않다.</p>
 * <p><b>FR-RPTS-002 is not delivered by this screen.</b> The criteria are the legacy's two —
 * 이용기관 and 요청일자 — and no source picker is shown. The merge (FR-RPTS-003) and the server's
 * {@code source} parameter are unchanged, so restoring the control needs no contract change.</p>
 *
 * <h2>레거시 화면 구조를 그대로 따른다 / the legacy screen structure is preserved</h2>
 * 제목 + breadcrumb(`title_wrpa`), 안내 상자(`infoList01`), 조회 상자(`tb_srchBtn`),
 * 다운로드 버튼 줄(`functionBtn_wrap`), 그리드, 페이지 이동 — 앞선 슬라이스가 세워 둔
 * `lg-*` 클래스를 그대로 쓴다. 빈 상태는 표를 숨기지 않고 <b>그리드 안에</b> 둔다.
 * Title with breadcrumb (`title_wrpa`), note box (`infoList01`), search box (`tb_srchBtn`), the
 * download button row (`functionBtn_wrap`), the grid and the pager — reusing the `lg-*` classes
 * the earlier slices established. The empty state lives <b>inside</b> the grid rather than
 * replacing it.
 *
 * <h2>레거시와 달라진 네 가지 / four differences from the legacy</h2>
 * 1. **처리중 컬럼이 채널마다 있다.** 레거시는 네 건수 중 셋만 보여 전체 ≠ 성공 + 실패 였다(D-R14).
 * 2. **집계 기준일이 보인다.** 기본 조회 범위가 오늘이었고 배치는 T-4 까지만 채웠으므로,
 *    사용자가 늘 마주친 것은 빈 화면이었다(D-R25).
 * 3. **부분 결과를 부분이라고 말한다**(FR-RPTS-005).
 * 4. **페이지 번호 대신 이어보기.** 결과가 두 데이터베이스에 걸쳐 있어 offset 으로 자를 수
 *    없다(ADR-RPT-021).
 *
 * 1. **An in-flight column per channel** — the legacy showed three of four counters (D-R14).
 * 2. **The watermark is visible** (D-R25).
 * 3. **A partial result says so** (FR-RPTS-005).
 * 4. **Seek paging, not page numbers** (ADR-RPT-021).
 */

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  fetchReport,
  fetchReportWatermark,
  type ReportQuery,
  type ReportRow,
  type SendSource,
} from '../../api/reportApi';
import { useInstitutionOptions } from './queries';

/** 오늘로부터 n일 전을 YYYY-MM-DD 로 반환한다. / Returns n days ago as YYYY-MM-DD. */
function daysAgo(n: number): string {
  const date = new Date();
  date.setDate(date.getDate() - n);
  return date.toISOString().slice(0, 10);
}

/** YYYY-MM-DD → YYYYMMDD (서버 계약 형식) / to the server's contract format. */
function toCompact(isoDate: string): string {
  return isoDate.replace(/-/g, '');
}

/** YYYYMMDD → YYYY-MM-DD (표시 형식) / to the display format. */
function toDisplayDate(compact: string): string {
  if (compact.length !== 8) return compact;
  return `${compact.slice(0, 4)}-${compact.slice(4, 6)}-${compact.slice(6, 8)}`;
}

/**
 * 이 화면이 사용하는 발송 구분. / The send-source filter this screen uses.
 *
 * 조회 조건은 레거시와 같이 **이용기관과 요청일자 둘뿐**이므로, 발송구분은 화면에 노출하지
 * 않고 항상 전체(합산)로 조회한다. 두 출처를 합산하는 동작 자체는 그대로다(FR-RPTS-003) —
 * 어느 출처에서 온 행인지는 그리드 첫 열 `구분` 이 그대로 보여준다.
 * The criteria are the legacy's two — 이용기관 and 요청일자 — so the source filter is not exposed
 * and every query runs merged. The merge itself is unchanged (FR-RPTS-003); which source a row
 * came from is still shown by the grid's first column, `구분`.
 *
 * 서버는 `source` 파라미터를 계속 받는다. 필요해지면 컨트롤을 되살리는 것으로 충분하며,
 * API 계약을 다시 바꿀 일은 없다.
 * The server still accepts the `source` parameter, so restoring the control later needs no
 * change to the API contract.
 */
const SOURCE: SendSource = 'ALL';

/** 채널마다 반복되는 네 건수의 이름. / The four counter names repeated per channel. */
const COUNTER_SUFFIXES = ['전체', '성공', '실패', '처리중'] as const;

/** 고정 컬럼 — 구분, 기관명, 일자. / The fixed columns. */
const FIXED_COLUMNS = ['구분', '기관명', '일자'] as const;

export function ReportPage() {
  // 기본 조회 범위를 오늘로 두지 않는다. 배치가 T-4 까지만 채우므로 오늘을 기본값으로 두면
  // 화면이 열리자마자 미집계 구간을 조회하게 된다 — 그것이 D-R25 의 사용자 경험이었다.
  // The default range is not today: the batch reaches only T-4, so defaulting to today would
  // query an un-aggregated window on open — the user experience D-R25 describes.
  const [from, setFrom] = useState(() => daysAgo(34));
  const [to, setTo] = useState(() => daysAgo(4));
  const [institution, setInstitution] = useState('');
  const [seek, setSeek] = useState<{ tradeDate: string; institutionCode: string } | null>(null);
  const [history, setHistory] = useState<{ tradeDate: string; institutionCode: string }[]>([]);

  const institutions = useInstitutionOptions();

  const watermark = useQuery({
    queryKey: ['report', 'watermark', SOURCE],
    queryFn: () => fetchReportWatermark(SOURCE),
  });

  const query: ReportQuery = {
    from: toCompact(from),
    to: toCompact(to),
    source: SOURCE,
    institution: institution || undefined,
    seekDate: seek?.tradeDate,
    seekInstitution: seek?.institutionCode,
    size: 100,
  };

  const report = useQuery({
    queryKey: ['report', 'usage', query],
    queryFn: () => fetchReport(query),
    placeholderData: (previous) => previous,
  });

  const page = report.data;
  const channels = page?.columns ?? [];
  const loading = report.isFetching;

  // 빈 상태 칸이 표 전체를 가로질러야 한다. 채널마다 네 칸이므로 고정 3 + 채널 × 4.
  // The empty cell must span the whole table: three fixed columns plus four per channel.
  const columnCount = FIXED_COLUMNS.length + channels.length * COUNTER_SUFFIXES.length;

  function resetPaging() {
    setSeek(null);
    setHistory([]);
  }

  return (
    <main className="page-wrap">
      {/* 제목 + breadcrumb — 레거시 title_wrpa / legacy title_wrpa */}
      <div className="lg-title">
        <h1>이용기관 보고서</h1>
        <span className="lg-breadcrumb">
          BIZTALK <span aria-hidden="true">›</span> <strong>이용기관 보고서</strong>
        </span>
      </div>

      {/* 레거시 infoList01 — 레거시는 화면 이름만 반복했다. 여기에는 이 화면이 무엇을 읽는지
          적는다. 집계가 배치 산출물이라는 사실을 모르면 빈 결과를 장애로 오해한다(D-R25).
          The legacy repeated the screen's own name here; this states what the screen reads,
          because an empty result reads as an outage to anyone who does not know the figures come
          from a batch (D-R25). */}
      <ul className="lg-info">
        <li>이용기관 보고서</li>
        <li data-testid="report-watermark">
          집계 기준일: API {watermark.data?.apiAsOf ?? '알 수 없음'} · 대량{' '}
          {watermark.data?.bulkAsOf ?? '알 수 없음'} — 일간 집계 배치가 만든 값을 읽으며,
          기준일 이후 날짜는 아직 집계되지 않았습니다.
        </li>
      </ul>

      {/* 레거시 tb_srchBtn */}
      <form
        className="lg-search"
        aria-label="이용기관 보고서 조회"
        onSubmit={(event) => {
          event.preventDefault();
          resetPaging();
          void report.refetch();
        }}
      >
        <label htmlFor="report-institution">이용기관</label>
        <select
          id="report-institution"
          value={institution}
          onChange={(event) => {
            setInstitution(event.target.value);
            resetPaging();
          }}
        >
          {/* 전체는 운영자에게만 의미가 있으며, 서버가 세션으로 범위를 다시 판정한다.
              이 목록에 무엇이 담기든 인가는 서버가 한다(FR-AZ-R03). */}
          <option value="">전체</option>
          {(institutions.data?.rows ?? []).map((row) => (
            <option key={row.code} value={row.code}>
              {row.name ?? row.code}
            </option>
          ))}
        </select>

        <label htmlFor="report-from">요청일자</label>
        <span className="lg-range">
          <input
            id="report-from"
            type="date"
            value={from}
            onChange={(event) => {
              setFrom(event.target.value);
              resetPaging();
            }}
          />
          <span className="lg-range-sep" aria-hidden="true">
            ~
          </span>
          <input
            id="report-to"
            type="date"
            aria-label="종료일자"
            value={to}
            onChange={(event) => {
              setTo(event.target.value);
              resetPaging();
            }}
          />
        </span>

        <span className="lg-search-actions">
          <button type="submit" className="lg-btn lg-btn-primary" disabled={loading}>
            조회
          </button>
        </span>
      </form>

      {/* 부분 결과 안내 — 조용한 부분 보고는 이 프로그램이 네 슬라이스 연속으로 만난 실패
          방식이며, 이 화면에서는 고객사 발송량이 실제보다 적게 보이는 결과가 된다.
          Silent partial reporting is the failure mode met in four consecutive slices; here it
          would under-report a customer's volume (FR-RPTS-005). */}
      {page?.incompleteNotes.map((note) => (
        <p key={note} role="alert" className="field-error visible" data-testid="incomplete-note">
          {note}
        </p>
      ))}

      {report.isError ? (
        <p role="alert" className="field-error visible" data-testid="report-error">
          {(report.error as Error).message}
        </p>
      ) : null}

      {/* 레거시 functionBtn_wrap — 다운로드는 스프린트 R2 에서 제공된다(ADR-RPT-023). */}
      <div className="lg-tab-actions">
        <button type="button" className="lg-btn" disabled title="Sprint R2 에서 제공됩니다">
          다운로드
        </button>
      </div>

      <section aria-live="polite">
        {/*
          그리드는 항상 머리글과 함께 렌더링한다. 표 자체를 숨기면 어떤 열이 있는지조차
          조회 전에는 알 수 없다 — 레거시도 빈 상태에서 머리글을 갖춘 그리드를 보여 주고
          본문에만 "조회된 내용이 없습니다" 를 넣었다.
          The grid always renders with its headers: hiding the table leaves the user unable to see
          which columns exist. The legacy did the same and put the message in the body.
        */}
        <div className="lg-grid-wrap">
          <table className="lg-grid">
            <caption className="sr-only">
              이용기관별 일자별 발송 집계. 채널마다 전체·성공·실패·처리중 네 건수를 표시합니다.
            </caption>
            <thead>
              <tr>
                {FIXED_COLUMNS.map((label) => (
                  <th key={label} scope="col">
                    {label}
                  </th>
                ))}
                {/* 레거시 그리드와 같은 평면 머리글 — "알림톡전체", "친구(txt)성공" 처럼
                    채널명과 건수 이름을 붙여 쓴다.
                    The legacy's flat header, concatenating channel and counter names. */}
                {channels.flatMap((channel) =>
                  COUNTER_SUFFIXES.map((suffix) => (
                    <th key={`${channel.key}-${suffix}`} scope="col">
                      {channel.label}
                      {suffix}
                    </th>
                  )),
                )}
              </tr>
            </thead>
            <tbody>
              {page && page.rows.length > 0 ? (
                page.rows.map((row) => (
                  <ReportTableRow
                    key={`${row.tradeDate}-${row.institutionCode}-${row.source}`}
                    row={row}
                    channelKeys={channels.map((channel) => channel.key)}
                  />
                ))
              ) : (
                <tr>
                  <td className="lg-empty" colSpan={Math.max(columnCount, FIXED_COLUMNS.length)}>
                    <span data-testid="report-empty">
                      {loading ? '조회 중입니다.' : '조회된 내용이 없습니다.'}
                    </span>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        {/* 페이지 이동 — 이어보기 방식이므로 페이지 번호가 아니라 이전/다음이다. */}
        <nav className="lg-paging" aria-label="페이지 이동" data-testid="report-paging">
          <button
            type="button"
            className="lg-btn"
            disabled={history.length === 0 || loading}
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
            {page?.totalCount == null
              ? '총 건수 많음'
              : `총 ${page.totalCount.toLocaleString()}건`}
          </span>
          <button
            type="button"
            className="lg-btn"
            disabled={loading || !page?.hasMore || page?.nextSeek == null}
            onClick={() => {
              if (page?.nextSeek) {
                setHistory([...history, page.nextSeek]);
                setSeek(page.nextSeek);
              }
            }}
          >
            다음
          </button>
        </nav>
      </section>
    </main>
  );
}

/** 보고서 1행. / One report row. */
function ReportTableRow({ row, channelKeys }: { row: ReportRow; channelKeys: string[] }) {
  return (
    <tr data-testid="report-row">
      <td>{row.source}</td>
      <td className="lg-cell-text">
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
        {toDisplayDate(row.tradeDate)}
        {/*
          산술 항등식이 깨진 행은 사실로 표시하지 않는다. 이 슬라이스는 집계를 고칠 수
          없으므로(CONST-DATA-R01) 고치는 대신 알린다(FR-RPT-010).
          A row whose identity fails is not presented as fact. This slice cannot repair the
          aggregate (CONST-DATA-R01), so it reports instead (FR-RPT-010).
        */}
        {!row.reconciles && (
          <span
            role="alert"
            data-testid="reconciliation-warning"
            title="전체 ≠ 성공 + 실패 + 처리중"
          >
            {' '}
            ⚠
          </span>
        )}
      </td>
      {channelKeys.flatMap((key) => {
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
