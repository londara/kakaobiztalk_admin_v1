import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  createColumnHelper,
  rowPaginationFeature,
  tableFeatures,
  useTable,
  type PaginationState,
} from '@tanstack/react-table';
import {
  formatTimestamp,
  type InstitutionQuery,
  type InstitutionRow,
  type InstitutionStatusFilter,
} from '../../api/institutionApi';
import { DataTable, type GridColumnMeta } from '../../components/DataTable';
import { useInstitutionSearch } from './queries';

/**
 * 이용기관 관리 화면. / 이용기관 management screen.
 *
 * req: FR-INST-001, FR-INST-002, FR-INST-003, FR-INST-006, FR-INST-007, FR-INST-008,
 *      FR-ATK-002, FR-AZ-I03
 * source: biztalk_admin_00_view.jsp (검색 폼·그리드), biztalk_admin_00.js (drawGrid 8컬럼)
 *
 * <h2>레거시 대비 변경점 / changes from the legacy</h2>
 * <ul>
 *   <li><b>인증키가 마스킹된다</b> — 레거시는 전 기관의 키를 평문 컬럼으로 노출했다(D-I5)</li>
 *   <li><b>서버 페이징</b> — 레거시는 전량을 받아 클라이언트에서 잘랐다(D-I10)</li>
 *   <li><b>등록일시·수정일시에 시각이 표시된다</b> — 레거시는 날짜만 보여 D-I9 을 가렸다</li>
 *   <li><b>매핑되지 않는 상태값이 원문으로 표시된다</b> — 레거시는 전부 '미사용' 으로
 *       뭉개 데이터 이상을 감췄다(FR-INST-006)</li>
 *   <li><b>담당자관리 탭이 없다</b> — 레거시에서도 주석 처리되어 동작하지 않았고,
 *       범위에서 제외되었다(AMB-I02, D-I13)</li>
 * </ul>
 *
 * <h2>조회 조건이 URL 에 있는 이유 / why the criteria live in the URL</h2>
 * <p>조건은 기관명과 상태, 그리고 페이지 번호뿐이며 개인정보가 아니다 — API 계층이 이
 * 화면만 {@code GET} 을 쓰는 이유와 같다. URL 에 두면 조회 결과를 그대로 공유·북마크할 수
 * 있고, 뒤로가기가 이전 조회로 돌아간다. 문자내역은 조건에 전화번호가 들어갈 수 있으므로
 * 같은 선택을 하지 않는다(NFR-SEC-PII).</p>
 * <p>The criteria are a name, a status and a page number — no personal data, the same reason the
 * API layer uses {@code GET} for this screen alone. Keeping them in the URL makes a result
 * shareable and bookmarkable and makes the back button return to the previous search. The message
 * history does not do this: its criteria can contain phone numbers.</p>
 *
 * <h2>동작하지 않는 버튼을 두지 않는다 / no buttons that do nothing</h2>
 * <p>레거시 화면에는 담당자 '추가'·'삭제' 버튼이 마크업에 존재했지만 이벤트 핸들러가
 * 아예 없었다(D-I13). 이 화면은 Sprint I1 범위인 <b>조회</b>만 제공하며, 등록·수정·중지·
 * 삭제 버튼은 해당 기능이 구현되는 Sprint I2 에서 함께 추가한다.</p>
 * <p>The legacy markup carried 담당자 추가/삭제 buttons with no event handlers at all (D-I13).
 * This screen offers only the <b>search</b> that Sprint I1 covers.</p>
 */

/** 상태 라디오 선택지 — 레거시와 동일한 3분류. / Status radio options, the legacy's three. */
const STATUS_OPTIONS: ReadonlyArray<{ value: InstitutionStatusFilter; label: string }> = [
  { value: 'ALL', label: '전체' },
  { value: 'Y', label: '사용' },
  { value: 'N', label: '사용 안함' },
];

/**
 * 서버 기본 페이지 크기. / The server's default page size.
 *
 * <p>요청에는 {@code size} 를 싣지 않는다 — 레거시와 같이 서버 기본값을 따른다. 이 값은
 * 첫 응답이 오기 전 페이지 모델을 세우기 위한 것이며, 응답이 오면 서버가 알려준 값을 쓴다.</p>
 * <p>No {@code size} is sent; the server's default applies. This only seeds the pagination model
 * before the first response, after which the server's own value is used.</p>
 */
const DEFAULT_PAGE_SIZE = 20;

/**
 * 안정적인 빈 배열. / A stable empty array.
 *
 * <p>매 렌더 새 배열을 넘기면 데이터가 바뀐 것으로 보여 테이블 모델이 계속 다시 계산된다.</p>
 * <p>A fresh array each render looks like new data and rebuilds the table model every time.</p>
 */
const NO_ROWS: InstitutionRow[] = [];

const features = tableFeatures({
  rowPaginationFeature,
  columnMeta: {} as GridColumnMeta,
});

const columnHelper = createColumnHelper<typeof features, InstitutionRow>();

/** 확정된 조회 조건 — URL 이 진실이다. / The committed criteria; the URL is the truth. */
interface Committed {
  name: string;
  status: InstitutionStatusFilter;
  page: number;
}

/**
 * URL 질의 문자열을 조회 조건으로 읽는다. / Reads the criteria out of the query string.
 *
 * <p>URL 은 사용자가 손으로 고칠 수 있는 입력이다. 상태값이 셋 중 하나가 아니면 '전체' 로,
 * 페이지가 수가 아니면 첫 페이지로 되돌린다 — 잘못된 값을 그대로 서버에 보내는 것보다
 * 화면이 정의된 상태로 열리는 편이 낫다.</p>
 * <p>The URL is user-editable input: an unknown status falls back to 전체 and a non-numeric page
 * to the first, so the screen opens in a defined state instead of forwarding nonsense.</p>
 *
 * @param params 질의 문자열 / the query string
 * @returns 확정된 조건 / the committed criteria
 */
function readCommitted(params: URLSearchParams): Committed {
  const status = params.get('status');
  const page = Number(params.get('page'));
  return {
    name: params.get('name') ?? '',
    status: status === 'Y' || status === 'N' ? status : 'ALL',
    page: Number.isInteger(page) && page > 0 ? page : 0,
  };
}

/** 두 조건이 같은 조회인지. / Whether two criteria describe the same search. */
function sameSearch(a: Committed, b: Committed): boolean {
  return a.name === b.name && a.status === b.status && a.page === b.page;
}

interface Props {
  /**
   * 이용기관을 선택했을 때의 콜백 — Sprint I2 의 수정 화면이 소비한다.
   * Called when an institution is chosen; Sprint I2's edit screen consumes it.
   */
  onSelect?: (row: InstitutionRow) => void;
}

/**
 * 이용기관 관리 화면 컴포넌트. / The 이용기관 management screen component.
 */
export function InstitutionPage({ onSelect }: Props) {
  const [searchParams, setSearchParams] = useSearchParams();
  const committed = useMemo(() => readCommitted(searchParams), [searchParams]);

  // 폼은 아직 확정되지 않은 입력이므로 화면 상태다. URL 이 바뀌면(뒤로가기 포함) 확정된
  // 조건으로 되돌린다 — 그리드가 보여 주는 것과 폼이 말하는 것이 어긋나지 않게 한다.
  // The form holds input that has not been committed yet, so it is screen state. When the URL
  // changes — including via the back button — it returns to the committed criteria, so the form
  // never disagrees with the rows on screen.
  const [name, setName] = useState(committed.name);
  const [status, setStatus] = useState<InstitutionStatusFilter>(committed.status);
  useEffect(() => {
    setName(committed.name);
    setStatus(committed.status);
  }, [committed]);

  const query: InstitutionQuery = useMemo(
    () => ({
      name: committed.name || undefined,
      status: committed.status,
      page: committed.page,
    }),
    [committed],
  );

  const search = useInstitutionSearch(query);

  /*
    실패했을 때 이전 결과를 남겨두면 조회에 성공한 것처럼 보인다. 같은 조건으로 다시 조회해
    실패하면 캐시에는 직전 성공 응답이 그대로 남아 있으므로, 오류 상태에서는 그 값을
    <b>읽지 않는다</b>.
    Leaving stale rows on screen after a failure would look like a successful search. Re-running
    the same criteria and failing leaves the previous successful response in the cache, so it is
    not read at all while the query is in error.
  */
  const result = search.isError ? undefined : search.data;
  const loading = search.isFetching;
  const error = search.isError
    ? search.error instanceof Error
      ? search.error.message
      : '이용기관을 조회할 수 없습니다.'
    : null;

  const pagination = useMemo<PaginationState>(
    () => ({ pageIndex: committed.page, pageSize: result?.size ?? DEFAULT_PAGE_SIZE }),
    [committed.page, result?.size],
  );

  const columns = useMemo(
    () =>
      columnHelper.columns([
        columnHelper.accessor('code', {
          header: '기관코드',
          cell: (ctx) => (
            /*
              레거시는 이 링크를 문자열 연결로 만들어 DB 값을 인라인 onclick 에 그대로
              넣었다(D-I12). React 는 값을 텍스트로 렌더링하므로 같은 구멍이 생기지 않는다.
              The legacy built this link by string concatenation, dropping a DB value into an
              inline onclick (D-I12). React renders values as text, so the same hole cannot open.
            */
            <button type="button" onClick={() => onSelect?.(ctx.row.original)}>
              {ctx.getValue()}
            </button>
          ),
        }),
        columnHelper.accessor('name', { header: '기관명' }),
        columnHelper.accessor('englishName', { header: '영문명' }),
        columnHelper.accessor('statusLabel', { header: '사용여부' }),
        columnHelper.accessor('authKeyMasked', { header: '인증키' }),
        columnHelper.accessor('registeredAt', {
          header: '등록일시',
          cell: (ctx) => formatTimestamp(ctx.getValue()),
        }),
        columnHelper.accessor('lastModifiedAt', {
          header: '수정일시',
          cell: (ctx) => formatTimestamp(ctx.getValue()),
        }),
        columnHelper.accessor('description', { header: '설명' }),
      ]),
    [onSelect],
  );

  const table = useTable({
    features,
    columns,
    data: result?.rows ?? NO_ROWS,
    getRowId: (row) => row.code,
    // 페이징은 서버가 한다. 페이지 번호의 주인은 URL 이므로 테이블은 그것을 읽고 쓰기만 한다.
    // Paging is the server's; the URL owns the page number and the table only reads and writes it.
    manualPagination: true,
    pageCount: result?.totalPages ?? 0,
    state: { pagination },
    onPaginationChange: (updater) => {
      const next = typeof updater === 'function' ? updater(pagination) : updater;
      setSearchParams((previous) => {
        const params = new URLSearchParams(previous);
        params.set('page', String(next.pageIndex));
        return params;
      });
    },
  });

  function submit(event: React.FormEvent) {
    event.preventDefault();
    // 조건이 바뀌면 첫 페이지로 돌아간다 — 3페이지에서 조건을 바꾸면 결과가 3페이지보다
    // 짧아 빈 화면이 나올 수 있다.
    // A criteria change returns to page one: changing filters while on page three can leave the
    // user past the end of a shorter result set.
    const next: Committed = { name, status, page: 0 };

    // 조건이 그대로인데 '조회' 를 누른 것은 "지금 다시 조회하라" 는 뜻이다. URL 이 바뀌지
    // 않으면 캐시 키도 그대로이므로, 이 경우에만 명시적으로 다시 요청한다.
    // Pressing 조회 without changing anything means "run it again now". The URL — and with it the
    // cache key — is unchanged, so this is the one case that needs an explicit refetch.
    if (sameSearch(next, committed)) {
      void search.refetch();
      return;
    }

    const params = new URLSearchParams();
    if (next.name) {
      params.set('name', next.name);
    }
    params.set('status', next.status);
    params.set('page', '0');
    setSearchParams(params);
  }

  return (
    <section aria-labelledby="institution-heading">
      <h1 id="institution-heading">서비스 관리</h1>
      <p>이용기관 관리</p>

      <form onSubmit={submit} aria-label="이용기관 조회">
        <div>
          <label htmlFor="institution-name">검색</label>
          <input
            id="institution-name"
            type="text"
            value={name}
            placeholder="이용기관 검색"
            onChange={(e) => setName(e.target.value)}
          />
        </div>

        <fieldset>
          <legend>상태</legend>
          {STATUS_OPTIONS.map((option) => (
            <label key={option.value} htmlFor={`status-${option.value}`}>
              <input
                id={`status-${option.value}`}
                type="radio"
                name="status"
                value={option.value}
                checked={status === option.value}
                onChange={() => setStatus(option.value)}
              />
              {option.label}
            </label>
          ))}
        </fieldset>

        <button type="submit" disabled={loading}>
          조회
        </button>
      </form>

      {error && <p role="alert">{error}</p>}

      {loading && <p role="status">조회 중입니다.</p>}

      {result && !loading && (
        <>
          <DataTable table={table} caption="이용기관 목록" />

          {result.rows.length === 0 && <p>조회 결과가 없습니다.</p>}

          {result.totalPages > 1 && (
            <nav aria-label="페이지">
              <button
                type="button"
                onClick={() => table.previousPage()}
                disabled={!table.getCanPreviousPage()}
              >
                이전
              </button>
              <span>
                {committed.page + 1} / {result.totalPages}
              </span>
              <button
                type="button"
                onClick={() => table.nextPage()}
                disabled={!table.getCanNextPage()}
              >
                다음
              </button>
            </nav>
          )}

          <p>총 {result.totalCount} 건</p>
        </>
      )}
    </section>
  );
}
