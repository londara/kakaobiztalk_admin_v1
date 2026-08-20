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
import { InstitutionEditDialog } from './InstitutionEditDialog';
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
 * 아예 없었다(D-I13). 이 화면은 <b>조회</b>와 <b>수정</b>을 제공한다 — 기관코드를 누르면
 * 수정 팝업이 열린다(FR-INST-007). 등록·중지·재사용·삭제 버튼은 해당 기능이 구현되는
 * Sprint I2b 에서 함께 들어온다. 같은 이유로 행 선택 체크박스도 아직 두지 않는다:
 * 선택을 소비할 동작이 없다.</p>
 * <p>The legacy markup carried 담당자 추가/삭제 buttons with no event handlers at all (D-I13).
 * This screen offers <b>search</b> and <b>edit</b>: clicking a 기관코드 opens the edit popup
 * (FR-INST-007). The 등록/중지/재사용/삭제 buttons arrive with their operations in Sprint I2b,
 * and row-selection checkboxes wait for the same reason — nothing would consume the selection.</p>
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

/**
 * 이용기관 관리 화면 컴포넌트. / The 이용기관 management screen component.
 */
export function InstitutionPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const committed = useMemo(() => readCommitted(searchParams), [searchParams]);

  /*
    열려 있는 수정 팝업의 대상. 조회 조건과 달리 URL 에 두지 않는다 — 조건은 공유할 만한
    것이지만 "이 기관을 편집하던 중" 은 그렇지 않고, 주소를 공유받은 사람의 화면이 남의
    편집 상태로 열리게 된다. NFR-USE-I01(팝업에서 돌아와도 조회 조건이 유지된다)은 조건이
    URL 에 있으므로 그대로 만족된다.

    The target of the open edit popup. Unlike the search criteria it is not kept in the URL: the
    criteria are worth sharing, "mid-edit on this institution" is not, and a shared address would
    open on someone else's editing state. NFR-USE-I01 — criteria survive a return from the popup —
    holds anyway, because the criteria live in the URL.
  */
  const [editing, setEditing] = useState<string | null>(null);

  /** 저장 완료 안내 — 팝업이 닫힌 뒤 목록에서 보인다. / Post-save notice, shown on the list. */
  const [saved, setSaved] = useState<string | null>(null);

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
            <button
              type="button"
              className="link-button"
              onClick={() => setEditing(ctx.row.original.code)}
            >
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
        columnHelper.accessor('description', {
          header: '설명',
          /*
            설명은 한 줄로 자르고 넘치면 '…' 로 끝낸다(레거시와 같다). 자르는 일 자체는
            CSS 가 하고 여기서는 감싸는 요소만 둔다 — 문자열을 잘라 '…' 를 붙이면 잘린
            글자가 DOM 에서 사라져 스크린리더도 검색(Ctrl+F)도 원문을 잃는다. CSS 로
            자르면 보이는 모양만 짧아지고 원문은 그대로 남는다.

            title 로 전체 문장을 붙여 마우스 사용자가 확인할 수 있게 한다. title 은
            보조 수단이다 — 스크린리더는 셀의 텍스트를 끝까지 읽으므로 title 이 없어도
            정보를 잃지 않는다.

            The description is clipped to one line and ends in an ellipsis when it overflows, as
            in the legacy. CSS does the clipping and this only supplies the element to clip:
            truncating the string here would delete the tail from the DOM, losing it for screen
            readers and for Ctrl+F alike. Clipping in CSS shortens only the appearance.

            The title carries the whole sentence for pointer users. It is a convenience, not the
            mechanism: the cell's text is complete, so a screen reader reads all of it regardless.
          */
          cell: (ctx) => {
            const text = ctx.getValue() ?? '';
            return text === '' ? '' : <span title={text}>{text}</span>;
          },
          meta: { cellClassName: 'lg-cell-ellipsis' },
        }),
      ]),
    // setEditing 은 useState 가 주는 안정된 참조이므로 의존성이 비어 있다 — 컬럼 정의는
    // 화면 생애 동안 한 번만 만들어진다.
    // setEditing is the stable setter useState provides, so the dependency list is empty and the
    // column definitions are built once for the life of the screen.
    [],
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
    /*
      AppLayout 이 이미 <main> 을 렌더링한다. 여기서 또 <main> 을 쓰면 main 랜드마크가 둘이
      되어 스크린리더의 '본문으로 건너뛰기' 가 어느 쪽을 가리키는지 모호해진다.
      AppLayout already renders a <main>; a second one here would give the page two main
      landmarks and make "skip to content" ambiguous.
    */
    <section className="page-wrap" aria-labelledby="institution-heading">
      {/*
        제목 + breadcrumb — 레거시 title_wrpa. 문자내역·발신번호 화면과 같은 골격이다.
        Title with breadcrumb (legacy title_wrpa), the same shell as the other two screens.
      */}
      <div className="lg-title">
        <h1 id="institution-heading">서비스 관리</h1>
        <span className="lg-breadcrumb">
          BIZTALK <span aria-hidden="true">›</span> <strong>서비스 관리</strong>
        </span>
      </div>

      {/*
        탭이 하나뿐이므로 이것은 사실상 구역 제목이며, 그렇게 쓴다 — 레거시의 두 번째 탭
        '담당자관리' 는 마크업에만 있고 동작하지 않았다(D-I13). 없는 탭을 그려 두지 않는다.
        With a single tab this is really a section heading and is used as one: the legacy's second
        tab, 담당자관리, existed in the markup and did nothing (D-I13). A tab that leads nowhere
        is not drawn.
      */}
      <div className="lg-tabs">
        <span className="lg-tab">이용기관관리</span>
      </div>

      {/* 레거시 infoList01 */}
      <ul className="lg-info">
        <li>이용기관 관리</li>
      </ul>

      <form className="lg-form" onSubmit={submit} aria-label="이용기관 조회">
        <div className="lg-form-grid">
          <label htmlFor="institution-name">검색</label>
          <input
            id="institution-name"
            className="lg-form-wide"
            type="text"
            value={name}
            placeholder="이용기관 검색"
            onChange={(e) => setName(e.target.value)}
          />

          {/*
            fieldset/legend 대신 role="radiogroup" + aria-labelledby 를 쓴다. 그룹 이름이
            격자의 라벨 칸에, 선택지가 입력 칸에 놓여야 다른 행과 줄이 맞는데, legend 는
            flex/grid 컨테이너 안에서 브라우저마다 배치가 달라 그 정렬을 신뢰할 수 없다.
            접근성상 전달되는 것은 같다 — 스크린리더는 그룹 이름을 먼저 읽고 각 선택지를
            읽으며, 각 라디오는 자기 <label> 을 그대로 갖는다.

            role="radiogroup" with aria-labelledby replaces fieldset/legend. The group name has
            to sit in the grid's label column and the options in the field column to line up with
            the other rows, and a <legend> inside a flex or grid container is laid out
            inconsistently across browsers. What is conveyed is the same: the group name is
            announced ahead of the options, and each radio keeps its own <label>.
          */}
          <span className="lg-form-label" id="institution-status-label">
            상태
          </span>
          <div
            className="lg-form-wide lg-radio-group"
            role="radiogroup"
            aria-labelledby="institution-status-label"
          >
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
          </div>
        </div>

        <div className="lg-form-actions">
          <button type="submit" className="lg-btn lg-btn-primary" disabled={loading}>
            {loading ? '조회 중…' : '조회'}
          </button>
        </div>
      </form>

      {/*
        레거시 화면은 그리드 위에 등록·수정·중지·삭제 버튼 줄을 두었다. 수정은 기관코드
        링크가 대신하며(레거시도 같았다 — fn_getDetail), 나머지 셋은 기능과 함께 Sprint I2b
        에서 들어온다. 뒤에 동작이 없는 버튼을 두는 것이 레거시의 결함 D-I13 이었다(핸들러
        없는 담당자 추가·삭제 버튼). 같은 이유로 행 선택 체크박스도 아직 두지 않는다.
        The legacy put a 등록/수정/중지/삭제 button row above the grid. Edit is reached through the
        기관코드 link — as it was in the legacy, via fn_getDetail — and the other three arrive with
        their operations in Sprint I2b. A button with no operation behind it is precisely defect
        D-I13, which is also why row-selection checkboxes wait.
      */}

      {/*
        저장 결과는 <b>목록 화면에서</b> 알린다. 팝업 안에서 알리면 팝업이 닫히는 순간 함께
        사라져 아무도 읽지 못한다. aria-live 영역이므로 스크린리더도 알림을 받는다.
        The save outcome is announced <b>on the list</b>: announcing it inside the popup would take
        the message away with the popup before anyone could read it. It is a live region, so a
        screen reader is told too.
      */}
      {saved && (
        <p role="status" className="lg-notice">
          {saved} 이용기관이 저장되었습니다.
        </p>
      )}

      {error && (
        <p role="alert" className="field-error visible">
          {error}
        </p>
      )}

      {loading && (
        <p role="status" className="lg-loading">
          조회 중입니다.
        </p>
      )}

      {result && !loading && (
        <section aria-live="polite">
          {/*
            결과가 없어도 그리드는 머리글과 함께 그린다 — 표를 통째로 감추면 어떤 열이
            있는지조차 알 수 없다. 빈 사유는 본문 한 칸에 넣는다. 세 화면이 같은 규칙이다.
            다만 그리드가 나타나는 시점은 <b>조회가 끝난 뒤</b>다: 아직 아무것도 받지 못한
            상태에서 머리글만 떠 있으면 결과가 없는 것처럼 읽힌다.

            An empty result still draws the grid with its headers — hiding the table leaves the
            user unable to see which columns exist — and the reason goes in a single body cell,
            as on the other two screens. The grid appears only once the search has finished,
            though: headers alone before any response read as a result that came back empty.
          */}
          <DataTable
            table={table}
            caption="이용기관 목록"
            captionClassName="sr-only"
            className="lg-grid"
            wrapperClassName="lg-grid-wrap"
            emptyClassName="lg-empty"
            emptyContent="조회 결과가 없습니다."
          />

          {result.totalPages > 1 && (
            <nav className="lg-paging" aria-label="페이지">
              <button
                type="button"
                className="lg-btn"
                onClick={() => table.previousPage()}
                disabled={!table.getCanPreviousPage() || loading}
              >
                이전
              </button>
              <span>
                {committed.page + 1} / {result.totalPages}
              </span>
              <button
                type="button"
                className="lg-btn"
                onClick={() => table.nextPage()}
                disabled={!table.getCanNextPage() || loading}
              >
                다음
              </button>
            </nav>
          )}

          <p className="lg-result-line">총 {result.totalCount} 건</p>
        </section>
      )}

      {/*
        수정 팝업. 레거시는 별도 창({@code ap.openPop} → {@code biztalk_admin_01.act})이었고
        저장 후 {@code popCallbackFn} 으로 부모의 조회를 다시 실행했다. 여기서는 같은 문서
        안의 대화상자이며, 다시 조회하는 일은 쿼리 캐시 무효화가 대신한다 — 조회 조건은
        URL 에 남아 있으므로 팝업에서 돌아와도 그대로다(NFR-USE-I01).

        The edit popup. The legacy opened a separate window and re-ran the parent's search through a
        callback; this is a dialog in the same document, and the re-query is handled by cache
        invalidation. The criteria stay in the URL, so returning from the popup keeps them
        (NFR-USE-I01).
      */}
      {editing !== null && (
        <InstitutionEditDialog
          code={editing}
          onClose={() => setEditing(null)}
          onSaved={(row) => setSaved(row.code)}
        />
      )}
    </section>
  );
}
