/**
 * 톡전송 내역 화면. / The 톡전송 내역 screen.
 *
 * req: FR-TLK-001, FR-TLK-003, FR-TLK-004, FR-TLK-005, FR-TLK-006, FR-TLK-013, FR-TLK-015,
 *      NFR-USE-T01
 * source: biztalk_admin_30_view.jsp, biztalk_admin_30.js
 *
 * <h2>이 화면이 레거시와 눈에 띄게 다른 세 가지 / three visible differences from the legacy</h2>
 *
 * **1. BizTalk 거래만 나온다.** 레거시는 `FT_APITR_HSTR` 를 채널 술어 없이 조회해 <b>모든</b>
 * 핀테크 API 거래를 보여주었다 — 운영 화면 캡처의 `ADV_COM_GET_STATUS` 가 그 증거다. PM 결정
 * SCOPE-T01 로 그 행들은 사라진다. 의도된, 눈에 보이는 파리티 이탈이다(CONFLICT-T02).
 *
 * The legacy queried `FT_APITR_HSTR` with no channel predicate and showed <b>every</b> fintech API
 * transaction — `ADV_COM_GET_STATUS` in the production screenshot is the evidence. Under SCOPE-T01
 * those rows disappear: a deliberate, visible deviation from parity (CONFLICT-T02).
 *
 * **2. 조회 전에 선택지를 기다린다.** 레거시 `onload` 는 `getDat()` 를 먼저, `fn_fintechSvcSel()`
 * 을 나중에 불러 선택기가 비어 있는 상태로 질의가 나갔다(D-T28).
 *
 * The legacy `onload` called `getDat()` before `fn_fintechSvcSel()`, so the query left with the
 * selector empty (D-T28).
 *
 * **3. 상세 링크는 서버가 정한다.** 레거시 그리드는
 * `API_SVC_CD.indexOf("KKO") != -1 && PRSU == 1` 로 판단했고 서버는 네 개의 정확한 코드만
 * 처리했다 — `ADV_KKO_AT_SEND2` 는 링크가 걸리고 팝업이 비었으며, 처리중·오류 행에는 링크가
 * 아예 없었다(D-T13).
 *
 * The legacy grid decided with `API_SVC_CD.indexOf("KKO") != -1 && PRSU == 1` while the server handled
 * four exact codes: `ADV_KKO_AT_SEND2` was linked with an empty popup, and 처리중/오류 rows had no link
 * at all (D-T13).
 *
 * <h2>상세 패널이 없는 이유 / why there is no detail panel yet</h2>
 * 스프린트 T2 가 거래 상세(화면 32)와 메시지 상세(화면 31)를 만든다. 링크 자리는 지금
 * `detailAvailable` 에 따라 그려지되 비활성이며, 그 사실이 화면에 적혀 있다 — 눌렀는데 아무 일도
 * 일어나지 않는 것이 이 프로그램이 여섯 슬라이스 연속으로 만난 실패 양식이다.
 *
 * Sprint T2 builds the transaction detail (screen 32) and message detail (screen 31). The link is
 * rendered from `detailAvailable` but disabled, and the screen says so: a link that does nothing when
 * pressed is the failure mode this programme has met in six consecutive slices.
 */

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  fetchTalkFilterOptions,
  searchTalkHistory,
  type TalkHistoryQuery,
  type TalkHistoryRow,
} from '../../api/talkHistoryApi';
import { exportTalkHistory } from '../../api/talkDetailApi';
import { TalkTransactionDetailPanel } from './TalkTransactionDetailPanel';
import { TalkMessageDetailPanel } from './TalkMessageDetailPanel';

/** 그리드 컬럼 — 레거시 9개 컬럼과 같다. / The nine columns, as in the legacy grid. */
const COLUMNS = [
  '일자',
  '기관코드',
  '기관명',
  '거래고유번호',
  'API',
  '상태',
  '응답코드',
  '등록시각',
  '완료시각',
] as const;

function today(): string {
  const now = new Date();
  const pad = (value: number) => `${value}`.padStart(2, '0');
  return `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}`;
}

/** `YYYYMMDD` → `YYYY-MM-DD`. */
function formatDate(raw: string): string {
  if (!raw || raw.length < 8) {
    return raw ?? '';
  }
  return `${raw.slice(0, 4)}-${raw.slice(4, 6)}-${raw.slice(6, 8)}`;
}

/** `YYYYMMDDHHMMSS` → `YYYY-MM-DD HH:MM:SS`. */
function formatTimestamp(raw: string | null): string {
  if (!raw || raw.length < 14) {
    return raw ?? '';
  }
  return `${formatDate(raw)} ${raw.slice(8, 10)}:${raw.slice(10, 12)}:${raw.slice(12, 14)}`;
}

export function TalkHistoryPage() {
  const [fromDate, setFromDate] = useState(() => today());
  const [toDate, setToDate] = useState('');
  const [fromTime, setFromTime] = useState('');
  const [toTime, setToTime] = useState('');
  const [serial, setSerial] = useState('');
  const [status, setStatus] = useState('');
  const [apiService, setApiService] = useState('');
  const [page, setPage] = useState(0);
  const [submitted, setSubmitted] = useState<TalkHistoryQuery | null>(null);

  // 드릴다운 상태. 레거시는 별 창(ap.openPop)과 폼 제출로 값을 넘겼고, 그 형태가 D-T2 의
  // 경로였다 — 이용기관 코드가 숨은 입력으로 브라우저를 거쳤다. 여기서는 거래 키만 담는다.
  // Drill-down state. The legacy passed values through a separate window and a form submit, which was D-T2's
  // path: the institution code travelled through the browser in a hidden input. Here only the key is held.
  const [openTxn, setOpenTxn] = useState<TalkHistoryRow | null>(null);
  const [openMessage, setOpenMessage] = useState<{ messageKey: string; tableType: string } | null>(
    null,
  );
  const [exportError, setExportError] = useState<string | null>(null);
  const [exportNote, setExportNote] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);

  // 선택지를 먼저 받는다. 이 쿼리가 끝나기 전에는 조회 버튼을 누를 수 없다 — D-T28.
  // The options are fetched first and 조회 is disabled until they arrive — D-T28.
  const options = useQuery({
    queryKey: ['talk-history', 'filters'],
    queryFn: fetchTalkFilterOptions,
    staleTime: 5 * 60 * 1000,
  });

  const history = useQuery({
    queryKey: ['talk-history', 'search', submitted, page],
    queryFn: () => searchTalkHistory({ ...(submitted as TalkHistoryQuery), page }),
    enabled: submitted !== null,
  });

  const loading = history.isFetching || options.isLoading;

  function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    setPage(0);
    setSubmitted({
      from: fromDate,
      to: toDate || undefined,
      fromTime: fromTime || undefined,
      toTime: toTime || undefined,
      serial: serial || undefined,
      status: status || undefined,
      apiService: apiService || undefined,
    });
  }

  async function onDownload() {
    if (submitted === null) {
      return;
    }
    setExportError(null);
    setExportNote(null);
    setExporting(true);
    try {
      // 목록과 <b>같은 조건 객체</b>를 넘긴다. 레거시 fn_makeExcel() 은 화면에 없는 DOM 요소
      // 일곱 개에서 값을 읽어 모든 필터가 빈 문자열이 되었고, 파일에는 조건과 무관하게 모든
      // 기관의 모든 메시지가 평문 번호로 담겼다(D-T1).
      // Passes the <b>same criteria object</b> as the list. The legacy fn_makeExcel() read from seven DOM
      // elements the screen does not have, so every filter became empty and the file held every
      // institution's messages with plaintext numbers regardless of the criteria (D-T1).
      const result = await exportTalkHistory(submitted);
      const url = URL.createObjectURL(result.blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = result.filename;
      link.click();
      URL.revokeObjectURL(url);
      // 서버가 헤더로 알린 행 수를 그대로 보여준다. 감사 기록에도 같은 수가 남으므로 대조할 수
      // 있다 — 레거시는 내보내기 기록이 없었고, 파일이 몇 건인지 아무도 몰랐다(D-T3, FR-TLKX-007).
      // Shows the row count the server reported in a header. The same number is in the audit record, so the two
      // can be reconciled — the legacy recorded no export at all and nobody knew a file's size (FR-TLKX-007).
      setExportNote(`${result.rows}건을 내보냈습니다. / Exported ${result.rows} rows.`);
    } catch (error) {
      // 실패가 보인다. 레거시는 선언되지 않은 프레임을 대상으로 제출해 사용자에게 아무 표시도
      // 없었다(D-T23).
      // Failure is visible. The legacy submitted to a frame nobody declared, so the user saw nothing (D-T23).
      setExportError((error as Error).message);
    } finally {
      setExporting(false);
    }
  }

  const rows: TalkHistoryRow[] = history.data?.rows ?? [];
  const totalPages = history.data?.totalPages ?? 0;

  return (
    <main className="page-wrap">
      <div className="lg-title">
        <h1>BizTalk 내역</h1>
        <span className="lg-breadcrumb">BIZTALK &gt; 톡전송 내역</span>
      </div>

      <ul className="lg-info">
        <li>
          BizTalk 발송 API 거래만 조회됩니다 — 그 외 API 거래는 이 화면의 대상이 아닙니다.
          {' / '}
          Only BizTalk send-API transactions are listed; other API transactions are out of scope.
        </li>
        <li>조회 기간은 최대 31일입니다. / The query period is capped at 31 days.</li>
      </ul>

      <form className="lg-search" onSubmit={onSubmit}>
        <label>
          요청일자
          <input
            type="text"
            inputMode="numeric"
            value={fromDate}
            onChange={(e) => setFromDate(e.target.value)}
            placeholder="YYYYMMDD"
            aria-label="요청일자 시작"
            data-testid="talk-from-date"
          />
        </label>
        <span className="lg-range-sep" aria-hidden="true">
          ~
        </span>
        <label>
          <span className="sr-only">요청일자 종료</span>
          <input
            type="text"
            inputMode="numeric"
            value={toDate}
            onChange={(e) => setToDate(e.target.value)}
            placeholder="비우면 하루"
            aria-label="요청일자 종료"
            data-testid="talk-to-date"
          />
        </label>

        <label>
          요청시각
          <input
            type="text"
            inputMode="numeric"
            maxLength={6}
            value={fromTime}
            onChange={(e) => setFromTime(e.target.value)}
            placeholder="HHMMSS"
            aria-label="요청시각 시작"
            data-testid="talk-from-time"
          />
        </label>
        <span className="lg-range-sep" aria-hidden="true">
          ~
        </span>
        <label>
          <span className="sr-only">요청시각 종료</span>
          <input
            type="text"
            inputMode="numeric"
            maxLength={6}
            value={toTime}
            onChange={(e) => setToTime(e.target.value)}
            placeholder="HHMMSS"
            aria-label="요청시각 종료"
            data-testid="talk-to-time"
          />
        </label>

        <label>
          거래일련번호
          <input
            type="text"
            inputMode="numeric"
            value={serial}
            onChange={(e) => setSerial(e.target.value)}
            aria-label="거래일련번호"
            data-testid="talk-serial"
          />
        </label>

        {/*
          상태 선택지는 서버가 보낸 것을 그대로 쓴다. 레거시는 필터를 코드 테이블에서 만들고
          컬럼 라벨을 자바스크립트에 하드코딩해 둘이 갈라질 수 있었다(D-T29).
          The status options are the server's. The legacy built the filter from a code table and
          hardcoded the column labels in JavaScript, letting the two diverge (D-T29).
        */}
        <label>
          상태
          <select
            value={status}
            onChange={(e) => setStatus(e.target.value)}
            aria-label="상태"
            data-testid="talk-status"
          >
            <option value="">전체</option>
            {(options.data?.statuses ?? []).map((option) => (
              <option key={option.code} value={option.code}>
                {option.label}
              </option>
            ))}
          </select>
        </label>

        <label>
          API서비스
          <select
            value={apiService}
            onChange={(e) => setApiService(e.target.value)}
            aria-label="API서비스"
            data-testid="talk-api-service"
          >
            <option value="">전체</option>
            {(options.data?.apiServices ?? []).map((option) => (
              <option key={option.code} value={option.code}>
                {option.label}
              </option>
            ))}
          </select>
        </label>

        <span className="lg-search-actions">
          {/*
            선택지가 도착하기 전에는 조회할 수 없다 — D-T28.
            No query before the options arrive — D-T28.
          */}
          <button
            type="submit"
            className="lg-btn lg-btn-primary"
            disabled={loading || options.isLoading}
            data-testid="talk-search"
          >
            조회
          </button>
          <button
            type="button"
            className="lg-btn"
            onClick={onDownload}
            disabled={submitted === null || exporting || loading}
            title={
              submitted === null
                ? '조회 후 내보낼 수 있습니다 / export is available after a query'
                : '화면과 같은 조건으로 내보냅니다 / exports the same criteria as the screen'
            }
            data-testid="talk-download"
          >
            {exporting ? '내보내는 중…' : '다운로드'}
          </button>
        </span>
      </form>

      {options.isError && (
        <p role="alert" className="field-error visible" data-testid="talk-options-error">
          {(options.error as Error).message}
        </p>
      )}

      {exportError && (
        <p role="alert" className="field-error visible" data-testid="talk-export-error">
          {exportError}
        </p>
      )}

      {exportNote && (
        <p role="status" data-testid="talk-export-note">
          {exportNote}
        </p>
      )}

      {history.isError && (
        <p role="alert" className="field-error visible" data-testid="talk-error">
          {(history.error as Error).message}
        </p>
      )}

      <div className="lg-grid-wrap">
        <table className="lg-grid">
          <caption className="sr-only">톡전송 거래내역</caption>
          <thead>
            <tr>
              {COLUMNS.map((column) => (
                <th key={column} scope="col">
                  {column}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr>
                <td className="lg-empty" colSpan={COLUMNS.length}>
                  {submitted === null
                    ? '조회 조건을 입력한 뒤 조회를 누르세요.'
                    : '조회된 내용이 없습니다.'}
                </td>
              </tr>
            ) : (
              rows.map((row) => (
                <tr key={`${row.transactionDate}-${row.transactionNo}`}>
                  <td>{formatDate(row.transactionDate)}</td>
                  <td>{row.institutionCode}</td>
                  <td className="lg-cell-text">{row.institutionName}</td>
                  <td>
                    {/*
                      서버가 detailAvailable 을 보낸다. 브라우저는 규칙을 갖지 않는다 — 레거시의
                      두 규칙이 어긋나 링크가 걸린 행의 팝업이 비었다(D-T13).
                      The server sends detailAvailable; the browser holds no rule. The legacy's two
                      rules disagreed, so a linked row's popup was empty (D-T13).
                    */}
                    {row.detailAvailable ? (
                      <button
                        type="button"
                        className="lg-link"
                        onClick={() => {
                          setOpenMessage(null);
                          setOpenTxn(row);
                        }}
                        data-testid="talk-detail-link"
                      >
                        {row.transactionNo}
                      </button>
                    ) : (
                      row.transactionNo
                    )}
                  </td>
                  <td className="lg-cell-text">{row.apiServiceCode}</td>
                  {/*
                    라벨과 원값을 함께 보여준다. 운영자가 제공업체에 코드를 그대로 인용할 수
                    있어야 한다(NFR-USE-T01).
                    Label and raw code together, so an operator can quote the code to a provider
                    (NFR-USE-T01).
                  */}
                  <td>{`${row.statusLabel} (${row.statusCode})`}</td>
                  <td>{row.responseCode ?? ''}</td>
                  <td>{formatTimestamp(row.registeredAt)}</td>
                  <td>{formatTimestamp(row.completedAt)}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/*
        번호식 페이저는 총건수를 필요로 하고, 총건수는 D-T11 이 고친 것이다. 레거시는 페이지
        정보를 서버에 넘기고도 건수를 되 읽지 않아 진짜 페이지 수를 알 수 없었다.
        A numbered pager needs the total, and the total is what D-T11 fixed: the legacy passed page
        info to the server and never read the count back, so it could not know the real page count.
      */}
      {history.data && totalPages > 0 && (
        <nav className="lg-paging" aria-label="페이지 이동" data-testid="talk-paging">
          <button
            type="button"
            className="lg-btn"
            disabled={page === 0 || loading}
            onClick={() => setPage((current) => Math.max(0, current - 1))}
          >
            이전
          </button>
          <span data-testid="talk-page-indicator">
            {page + 1} / {totalPages} (총 {history.data.totalCount}건)
          </span>
          <button
            type="button"
            className="lg-btn"
            disabled={page + 1 >= totalPages || loading}
            onClick={() => setPage((current) => current + 1)}
          >
            다음
          </button>
        </nav>
      )}

      {openTxn && (
        <TalkTransactionDetailPanel
          transactionDate={openTxn.transactionDate}
          serial={openTxn.transactionNo}
          apiServiceCode={openTxn.apiServiceCode}
          institutionName={openTxn.institutionName}
          onClose={() => {
            setOpenTxn(null);
            setOpenMessage(null);
          }}
          onOpenMessage={(messageKey, tableType) => setOpenMessage({ messageKey, tableType })}
        />
      )}

      {openTxn && openMessage && (
        <TalkMessageDetailPanel
          transactionDate={openTxn.transactionDate}
          serial={openTxn.transactionNo}
          messageKey={openMessage.messageKey}
          tableType={openMessage.tableType}
          onClose={() => setOpenMessage(null)}
        />
      )}
    </main>
  );
}
