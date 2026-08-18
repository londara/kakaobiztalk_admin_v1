import { useMemo, useState } from 'react';
import {
  createColumnHelper,
  rowPaginationFeature,
  tableFeatures,
  useTable,
  type PaginationState,
} from '@tanstack/react-table';
import { CriteriaError, type MessageHistoryQuery, type MessageHistoryRow } from '../../api/messageHistoryApi';
import { DataTable, type GridColumnMeta } from '../../components/DataTable';
import { MessageDetailPanel } from './MessageDetailPanel';
import { useMessageExport, useMessageHistorySearch } from './queries';

/**
 * 문자내역 조회 화면. / 문자내역 search screen.
 *
 * req: FR-MSG-002, FR-MSG-004, FR-MSG-005, FR-MSG-007, FR-MSG-014, FR-MSG-017, NFR-USE-01
 * source: biztalk_admin_40_view.jsp (검색 폼), biztalk_admin_40.js (그리드 12컬럼)
 *
 * <h2>레거시 대비 변경점 / changes from the legacy</h2>
 * <ul>
 *   <li><b>발신/수신 라벨이 실제 컬럼과 일치한다</b> — 레거시는 반대였다(D3)</li>
 *   <li><b>수신번호·결과코드 검색이 실제로 동작한다</b> — 레거시는 전송했으나 서버가
 *       무시했다(D4)</li>
 *   <li><b>이용기관 드롭다운이 운영자에게만 표시된다</b> — 레거시는 모든 사용자에게 전체
 *       고객사 명단을 채워 보여줬다(TM-011)</li>
 *   <li><b>서버 페이징</b> — 레거시는 전량을 받아 클라이언트에서 페이징했다(D7)</li>
 * </ul>
 *
 * <h2>이 화면만 조회 조건을 URL 에 두지 않는 이유 / why this screen keeps criteria out of the URL</h2>
 * <p>이용기관·발신번호 화면은 조건을 질의 문자열에 두어 공유·북마크가 가능하다. 여기서는
 * 그렇게 하지 않는다 — 조건에 전화번호가 들어갈 수 있고, URL 은 브라우저 히스토리와 접근
 * 로그, 그리고 화면 공유에 그대로 남는다. API 계층이 이 조회만 {@code POST} 를 쓰는 이유와
 * 정확히 같은 이유다(NFR-SEC-PII).</p>
 * <p>The institution and sender-number screens put their criteria in the query string so a result
 * can be shared or bookmarked. This one does not: the criteria can contain phone numbers, and a
 * URL persists in browser history, access logs and anything shared on screen — the same reason
 * the API layer uses {@code POST} for this search alone.</p>
 */

interface Props {
  /** 운영자 여부 — 이용기관 선택 가능 여부를 결정한다 / whether the user is an operator */
  operator: boolean;
}

/** 기본 조회 기간(일). 레거시는 당일이 기본이었다. / Default window; the legacy defaulted to today. */
const DEFAULT_DAYS = 1;

/**
 * 서버 기본 페이지 크기. / The server's default page size.
 *
 * <p>요청에 {@code size} 를 싣지 않으므로 첫 응답 전까지만 쓰이는 씨앗값이다. 페이지 수는
 * 서버가 준 {@code totalPages} 를 그대로 쓴다.</p>
 * <p>No {@code size} is sent, so this only seeds the model before the first response; the page
 * count itself comes from the server's {@code totalPages}.</p>
 */
const DEFAULT_PAGE_SIZE = 50;

/**
 * 안정적인 빈 배열. / A stable empty array.
 *
 * <p>매 렌더 새 배열을 넘기면 데이터가 바뀐 것으로 보여 테이블 모델이 계속 다시 계산된다.</p>
 * <p>A fresh array each render looks like new data and rebuilds the table model every time.</p>
 */
const NO_ROWS: MessageHistoryRow[] = [];

const features = tableFeatures({
  rowPaginationFeature,
  columnMeta: {} as GridColumnMeta,
});

const columnHelper = createColumnHelper<typeof features, MessageHistoryRow>();

function todayIso(offsetDays = 0): string {
  const d = new Date();
  d.setDate(d.getDate() + offsetDays);
  return `${d.toISOString().slice(0, 10)}T00:00`;
}

/**
 * 두 조회 조건이 같은지. / Whether two criteria are the same search.
 *
 * <p>{@code buildQuery} 가 항상 같은 순서로 필드를 채우므로 직렬화 비교로 충분하다. 값이
 * 없는 필드는 {@code undefined} 라 양쪽 모두에서 빠진다.</p>
 * <p>{@code buildQuery} always fills the fields in the same order, so comparing the serialised
 * form is enough; absent fields are {@code undefined} and drop out of both sides.</p>
 */
function sameQuery(a: MessageHistoryQuery, b: MessageHistoryQuery): boolean {
  return JSON.stringify(a) === JSON.stringify(b);
}

/**
 * 문자내역 조회 화면 컴포넌트. / The 문자내역 screen component.
 */
export function MessageHistoryPage({ operator }: Props) {
  const [from, setFrom] = useState(todayIso(-DEFAULT_DAYS));
  const [to, setTo] = useState(todayIso(1));
  const [institutionCode, setInstitutionCode] = useState('');
  const [messageKey, setMessageKey] = useState('');
  const [senderNumber, setSenderNumber] = useState('');
  const [recipientNumber, setRecipientNumber] = useState('');
  const [status, setStatus] = useState('');
  const [messageType, setMessageType] = useState('');
  const [tableType, setTableType] = useState('');
  const [resultCode, setResultCode] = useState('');

  /*
    확정된 조회 조건. 폼에 입력 중인 값과 구분한다 — 화면에 보이는 행은 <b>확정된</b> 조건의
    답이며, 사용자가 폼을 계속 고치는 동안에도 그 사실은 바뀌지 않는다. null 은 "아직 조회하지
    않았다" 는 뜻이고, 그동안 조회는 실행되지 않는다.
    The committed criteria, kept apart from what is being typed: the rows on screen answer the
    committed criteria and stay that answer while the user keeps editing the form. null means no
    search has been run yet, and no request is issued while it holds.
  */
  const [criteria, setCriteria] = useState<MessageHistoryQuery | null>(null);
  const [selected, setSelected] = useState<MessageHistoryRow | null>(null);

  const search = useMessageHistorySearch(criteria);
  const exportCsv = useMessageExport();

  /*
    오류 상태에서는 캐시에 남은 직전 성공 응답을 읽지 않는다 — 실패 후에도 이전 행이 남아
    있으면 조회에 성공한 것처럼 보인다.
    The previous successful response is not read while the query is in error: rows surviving a
    failure read as a successful search.
  */
  const result = search.isError ? undefined : search.data;
  const loading = search.isFetching;
  const exporting = exportCsv.isPending;

  /*
    조회 실패와 내보내기 실패는 한 자리에 표시한다. 두 실패가 겹칠 수 없기 때문이다 —
    내보내기 버튼은 조회에 성공했을 때만 눌리고, 새 조회를 시작할 때 이전 내보내기 오류는
    지운다.
    Search and export failures share one slot because they cannot overlap: export is only
    pressable after a successful search, and starting a new search clears any export failure.
  */
  const failure = search.error ?? exportCsv.error ?? null;
  const violations = failure instanceof CriteriaError ? failure.violations : [];
  const error =
    failure && !(failure instanceof CriteriaError)
      ? search.error
        ? '조회 중 오류가 발생했습니다. 잠시 후 다시 시도하세요.'
        : '내보내기에 실패했습니다. 잠시 후 다시 시도하세요.'
      : null;

  /**
   * 폼에 입력된 값으로 조회 조건을 만든다. / Builds criteria from what is in the form.
   *
   * @param page 페이지 번호 / the zero-based page
   */
  function buildQuery(page = 0): MessageHistoryQuery {
    return {
      from,
      to,
      // 운영자가 아니면 이용기관을 보내지 않는다. 서버는 어차피 무시하지만,
      // 보내지 않는 것이 의도를 분명히 하고 감사 기록에 불필요한 시도를 남기지 않는다.
      // Not sent unless operator: the server ignores it anyway, but omitting it states intent
      // and avoids logging a pointless override attempt.
      institutionCode: operator && institutionCode ? institutionCode : undefined,
      messageKey: messageKey.trim() === '' ? undefined : Number(messageKey),
      senderNumber: senderNumber || undefined,
      recipientNumber: recipientNumber || undefined,
      status: status || undefined,
      messageType: messageType || undefined,
      tableType: tableType || undefined,
      resultCode: resultCode || undefined,
      page,
    };
  }

  const pagination = useMemo<PaginationState>(
    () => ({ pageIndex: criteria?.page ?? 0, pageSize: result?.size ?? DEFAULT_PAGE_SIZE }),
    [criteria?.page, result?.size],
  );

  const columns = useMemo(
    () =>
      columnHelper.columns([
        columnHelper.accessor('messageTypeLabel', { header: '유형' }),
        columnHelper.accessor('tableType', { header: '테이블' }),
        columnHelper.accessor('messageKey', {
          header: '메시지키',
          cell: (ctx) => (
            /*
              레거시는 <a onclick> 이었다. button 을 쓰면 키보드로 접근 가능하고 스크린리더가
              조작 가능한 요소로 인식한다(WCAG 2.1.1).
              The legacy used an anchor with onclick; a button is keyboard-reachable and
              announced as actionable.
            */
            <button type="button" className="link-button" onClick={() => setSelected(ctx.row.original)}>
              {ctx.getValue()}
            </button>
          ),
        }),
        columnHelper.accessor('institutionCode', { header: '이용기관' }),
        columnHelper.accessor('statusLabel', { header: '상태' }),
        columnHelper.accessor('resultCode', { header: '톡결과' }),
        columnHelper.accessor('senderNumber', { header: '발송번호' }),
        columnHelper.accessor('recipientNumber', { header: '수신번호' }),
        columnHelper.accessor('requestDate', {
          header: '요청일자',
          // 표시는 8자리로 줄이지만 행이 들고 있는 값은 원본 14자리다 — 상세 조회가 그것을
          // 필요로 한다(FR-MSG-014).
          // Displayed as 8 digits while the row keeps the raw 14, which the detail lookup needs.
          cell: (ctx) => ctx.getValue()?.slice(0, 8),
        }),
        columnHelper.accessor('requestTime', { header: '요청시간' }),
        columnHelper.accessor('sentTime', { header: '발송시간' }),
        columnHelper.accessor('reportTime', { header: '응답시간' }),
      ]),
    [],
  );

  const table = useTable({
    features,
    columns,
    data: result?.rows ?? NO_ROWS,
    getRowId: (row) => `${row.messageType}-${row.tableType}-${row.messageKey}-${row.requestDate}`,
    manualPagination: true,
    pageCount: result?.totalPages ?? 0,
    state: { pagination },
    onPaginationChange: (updater) => {
      const next = typeof updater === 'function' ? updater(pagination) : updater;
      // 페이지 이동은 폼의 현재 값이 아니라 <b>확정된</b> 조건을 그대로 들고 간다. 폼을
      // 고쳐 둔 채 '다음' 을 누르면 다른 조건의 2페이지가 나오는 것이 레거시의 혼란이었다.
      // Paging carries the committed criteria, not what is currently in the form: pressing 다음
      // after editing the form used to produce page two of a different search.
      setCriteria((previous) => (previous ? { ...previous, page: next.pageIndex } : previous));
    },
  });

  function runSearch(event: React.FormEvent) {
    event.preventDefault();
    // 이전 내보내기 실패 메시지를 지운다 — 새 조회의 결과와 나란히 남아 있으면 무엇이
    // 실패한 것인지 알 수 없다.
    // Clears a previous export failure: left beside a new result it becomes unclear what failed.
    exportCsv.reset();

    const next = buildQuery(0);
    // 조건이 그대로면 캐시 키도 그대로다. 그때의 '조회' 는 "지금 다시 조회하라" 는 뜻이다.
    // An unchanged criteria means an unchanged cache key; 조회 then means "run it again now".
    if (criteria && sameQuery(next, criteria)) {
      void search.refetch();
      return;
    }
    setCriteria(next);
  }

  /**
   * 조회 결과를 CSV 로 내려받는다. / Downloads the result as CSV.
   *
   * req: FR-MSG-017
   *
   * <p>폼의 현재 값이 아니라 <b>확정된</b> 조건으로 내보낸다. 조회 후 폼만 고친 상태에서
   * 내보내면 화면에 보이는 것과 파일의 내용이 달라지고, 파일을 받은 사람은 그 불일치를
   * 발견할 수 없다.</p>
   * <p>Exports the committed criteria, not the form's current values: editing the form after a
   * search and then exporting would produce a file that disagrees with the screen, and whoever
   * receives the file cannot detect the discrepancy.</p>
   *
   * <p>상한(5,000건) 초과는 잘라내지 않고 거절되며, 위반 메시지에 실제 건수가 담겨 온다.</p>
   * <p>Exceeding the 5,000-row cap is refused rather than truncated, and the message carries the
   * actual count so the user knows how much to narrow the window.</p>
   */
  function runExport() {
    if (!criteria) {
      return;
    }
    exportCsv.mutate({ ...criteria, page: 0 });
  }

  return (
    <main className="page-wrap">
      <h1>문자내역</h1>

      <form className="search-panel" onSubmit={runSearch} noValidate>
        <fieldset>
          <legend>조회 조건</legend>

          <div role="alert" aria-live="assertive" className={error ? 'field-error visible' : 'field-error'}>
            {error}
          </div>
          {violations.length > 0 && (
            <ul role="alert" aria-live="assertive" className="violations">
              {violations.map((v) => (
                <li key={v}>{v}</li>
              ))}
            </ul>
          )}

          <div className="search-grid">
            {/*
              이용기관 선택은 운영자에게만 렌더링한다. 레거시는 모든 사용자에게
              전체 고객사 명단을 드롭다운으로 제공했다(TM-011, FR-TEN-004).
              Rendered for operators only; the legacy showed every customer to everyone.
            */}
            {operator && (
              <div className="field">
                <label htmlFor="mh-institution">이용기관</label>
                <input
                  id="mh-institution"
                  type="text"
                  value={institutionCode}
                  onChange={(e) => setInstitutionCode(e.target.value)}
                  placeholder="전체"
                />
              </div>
            )}

            <div className="field">
              <label htmlFor="mh-from">요청일시 (시작)</label>
              <input
                id="mh-from"
                type="datetime-local"
                required
                value={from}
                onChange={(e) => setFrom(e.target.value)}
              />
            </div>

            <div className="field">
              <label htmlFor="mh-to">요청일시 (종료)</label>
              <input
                id="mh-to"
                type="datetime-local"
                required
                value={to}
                onChange={(e) => setTo(e.target.value)}
                aria-describedby="mh-range-help"
              />
              <p id="mh-range-help" className="field-help">
                조회 기간은 최대 31일까지 가능합니다.
              </p>
            </div>

            <div className="field">
              <label htmlFor="mh-msgkey">메시지키</label>
              <input
                id="mh-msgkey"
                type="text"
                inputMode="numeric"
                value={messageKey}
                onChange={(e) => setMessageKey(e.target.value.replace(/\D/g, ''))}
              />
            </div>

            {/*
              라벨과 컬럼이 일치한다. 레거시: 발신번호→PHONE(수신 컬럼), 수신번호→CALLBACK(발신 컬럼).
              Labels match columns. Legacy: 발신번호→PHONE (recipient), 수신번호→CALLBACK (sender).
            */}
            <div className="field">
              <label htmlFor="mh-sender">발송번호</label>
              <input
                id="mh-sender"
                type="text"
                value={senderNumber}
                onChange={(e) => setSenderNumber(e.target.value)}
              />
            </div>

            <div className="field">
              <label htmlFor="mh-recipient">수신번호</label>
              <input
                id="mh-recipient"
                type="text"
                value={recipientNumber}
                onChange={(e) => setRecipientNumber(e.target.value)}
              />
            </div>

            <div className="field">
              <label htmlFor="mh-status">상태</label>
              <select id="mh-status" value={status} onChange={(e) => setStatus(e.target.value)}>
                <option value="">전체</option>
                <option value="1">미전송</option>
                <option value="2">전송완료</option>
                <option value="3">톡결과수신</option>
                <option value="4">문자결과수신</option>
                <option value="6">큐입력</option>
              </select>
            </div>

            <div className="field">
              <label htmlFor="mh-type">유형</label>
              <select
                id="mh-type"
                value={messageType}
                onChange={(e) => setMessageType(e.target.value)}
              >
                <option value="">전체</option>
                <option value="AT">알림톡</option>
                <option value="FT">친구톡</option>
              </select>
            </div>

            <div className="field">
              <label htmlFor="mh-table">문자타입</label>
              <select id="mh-table" value={tableType} onChange={(e) => setTableType(e.target.value)}>
                <option value="">전체</option>
                <option value="SMS">SMS</option>
                <option value="MMS">MMS</option>
              </select>
            </div>

            <div className="field">
              <label htmlFor="mh-result">결과코드</label>
              <input
                id="mh-result"
                type="text"
                value={resultCode}
                onChange={(e) => setResultCode(e.target.value)}
              />
            </div>
          </div>

          <div className="form-actions">
            <button type="submit" className="primary" disabled={loading}>
              {loading ? '조회 중…' : '조회'}
            </button>
            {/*
              type="button" 이 필수다. 기본 type 은 submit 이므로 생략하면 내보내기 버튼이
              폼을 제출해 조회가 함께 실행된다.
              type="button" is required: the default is submit, so omitting it would make the
              export button also run a search.

              조회 결과가 없으면 비활성화한다 — 조건만 입력한 상태에서 내보내면 사용자가
              무엇을 받게 되는지 알 수 없다.
              Disabled until there are results: exporting before searching gives the user a file
              whose contents they have not seen.
            */}
            <button
              type="button"
              className="secondary"
              onClick={runExport}
              disabled={exporting || loading || !result || result.totalCount === 0}
            >
              {exporting ? '내보내는 중…' : 'CSV 내보내기'}
            </button>
          </div>
        </fieldset>
      </form>

      {result && (
        <section aria-live="polite">
          <p className="result-summary">
            총 <strong>{result.totalCount.toLocaleString()}</strong>건 · {result.page + 1} /{' '}
            {Math.max(result.totalPages, 1)} 페이지
          </p>

          {result.rows.length === 0 ? (
            /*
              빈 결과와 오류를 구분한다. 레거시는 둘 다 같은 빈 그리드로 표시했다.
              An empty result is distinguished from an error; the legacy showed both identically.
            */
            <p className="empty-state">조회 결과가 없습니다.</p>
          ) : (
            <>
              <DataTable
                table={table}
                caption="문자내역 조회 결과"
                captionClassName="sr-only"
                wrapperClassName="table-scroll"
              />

              <nav className="paging" aria-label="페이지 이동">
                <button
                  type="button"
                  disabled={!table.getCanPreviousPage() || loading}
                  onClick={() => table.previousPage()}
                >
                  이전
                </button>
                <button
                  type="button"
                  disabled={!table.getCanNextPage() || loading}
                  onClick={() => table.nextPage()}
                >
                  다음
                </button>
              </nav>
            </>
          )}
        </section>
      )}

      {selected && <MessageDetailPanel row={selected} onClose={() => setSelected(null)} />}
    </main>
  );
}
