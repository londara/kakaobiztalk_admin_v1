import { useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  createColumnHelper,
  rowPaginationFeature,
  rowSelectionFeature,
  tableFeatures,
  useTable,
  type PaginationState,
  type RowSelectionState,
} from '@tanstack/react-table';
import { formatTimestamp } from '../../api/institutionApi';
import type { SenderNumberRow } from '../../api/senderNumberApi';
import { DataTable, type GridColumnMeta } from '../../components/DataTable';
import { SenderNumberDeleteDialog } from './SenderNumberDeleteDialog';
import { SenderNumberRegisterDialog } from './SenderNumberRegisterDialog';
import { useInstitutionOptions, useSenderNumbers } from './queries';

/**
 * 이용기관 정보 관리 — 발신번호 화면. / 이용기관 정보 관리 — the sender-number screen.
 *
 * req: FR-SND-001, FR-SND-002, FR-SND-003, FR-SND-005, FR-SND-006, FR-SND-008,
 *      FR-SND-009, FR-SND-010, FR-SND-012, FR-SNDD-009, FR-SNDD-010, FR-SNDD-011, FR-AZ-D01
 * source: biztalk_admin_10_view.jsp (이용기관 선택 + 그리드), biztalk_admin_10.js (drawGrid 7컬럼,
 *         btn_register, btn_delete)
 *
 * <h2>레거시 대비 변경점 / changes from the legacy</h2>
 * <ul>
 *   <li><b>발신번호가 전체로 표시된다</b> — 레거시는 목록에서만 이름용 마스킹 함수를
 *       적용하고 상세에서는 원본을 보여 주었다. 그 불일치가 삭제를 망가뜨렸다(D-S1, AMB-S04)</li>
 *   <li><b>서버 페이징과 고정 정렬</b> — 레거시는 LIMIT 도 ORDER BY 도 없이 전량을
 *       받아 브라우저에서 잘랐고, 페이징 위젯은 숨겨져 있었다(D-S14)</li>
 *   <li><b>기관을 고르기 전에는 조회하지 않는다</b> — 레거시는 페이지 로드마다 빈
 *       IS_CD 로 한 번 조회했다(D-S19)</li>
 *   <li><b>행 식별은 ref 로 한다</b> — 표시값을 식별자로 쓰는 구조가 D-S1 의 원인이었다</li>
 *   <li><b>수수료 탭이 없다</b> — 레거시 JSP 에서 주석 처리되어 있었고 서비스 계약도
 *       쿼리도 존재하지 않는다(§2.7)</li>
 * </ul>
 *
 * <h2>선택한 기관이 URL 에 있는 이유 / why the chosen institution lives in the URL</h2>
 * <p>기관 코드와 페이지 번호는 개인정보가 아니므로 URL 에 두어도 무방하고, 두면 특정 기관의
 * 발신번호 화면을 그대로 공유할 수 있다. 표시되는 발신번호 자체는 URL 에 넣지 않는다 —
 * 화면에 보이는 것과 주소창에 남는 것은 다른 문제다(FR-SND-011 의 조회 감사는 서버가 한다).</p>
 * <p>An institution code and a page number are not personal data, so the URL can carry them and a
 * given institution's screen becomes shareable. The sender numbers themselves never enter the
 * URL: what is displayed and what is left in the address bar are different questions.</p>
 *
 * <h2>동작하지 않는 버튼을 두지 않는다 / no buttons that do nothing</h2>
 * <p>레거시 화면에는 '등록'·'삭제' 버튼이 있었고 JS 에는 '수정' 핸들러까지 있었지만,
 * <b>수정 버튼 자체가 마크업에 없어</b> 상세·수정 화면은 도달 불가능한 죽은 코드였다(D-S8).
 * Sprint S1 은 등록·삭제를 <b>비활성</b>으로 두었고, Sprint S2a 가 그 둘을 연결한다.
 * 상세·수정(화면 11)은 Sprint S2b 다.</p>
 * <p>The legacy had 등록/삭제 buttons and even a 수정 handler in JS — but no 수정 button in the
 * markup, so the detail screen was unreachable dead code (D-S8). Sprint S1 left 등록 and 삭제
 * disabled; Sprint S2a wires them. Detail and edit (screen 11) are Sprint S2b.</p>
 *
 * <p>두 버튼에는 <b>활성 조건</b>이 있다(FR-SNDD-010): 등록은 이용기관을 골라야, 삭제는 행을
 * 선택해야 눌릴 수 있다. 레거시는 조건 없이 팝업을 열었고, 삭제의 경우 빈 목록으로 요청을
 * 보냈다 — D-S1 때문에 그것마저 성공으로 보고되었다.</p>
 * <p>Both controls have <b>enablement conditions</b> (FR-SNDD-010): 등록 needs an institution and
 * 삭제 needs a selection. The legacy opened its popups regardless, and for delete sent a request with
 * an empty list — which, thanks to D-S1, also reported success.</p>
 *
 * <h2>선택은 페이지를 넘어 유지된다 / the selection outlives the page</h2>
 * <p>서버 페이징(FR-SND-003)은 <b>선택된 행</b>과 <b>보이는 행</b>을 분리한다. 레거시 그리드는
 * 전체 결과를 브라우저에 들고 있었으므로(D-S14) 그 둘이 갈라질 수 없었다. 100건 삭제를 상정하는
 * NFR-PERF-D03 을 위해 선택은 페이지를 넘어 유지되며, 대신 <b>선택된 행 자체</b>를 들고 있어야
 * 한다 — 확인 화면이 지금 페이지에 없는 번호까지 열거해야 하기 때문이다(FR-SNDD-009). 그래서
 * 아래 상태는 식별자 집합이 아니라 {@code ref → 행} 의 사전이다.</p>
 * <p>Server-side paging (FR-SND-003) separates <b>selected</b> from <b>visible</b>; the legacy grid
 * held the whole result set (D-S14) so the two could not diverge. The selection is kept across pages
 * for the 100-number delete NFR-PERF-D03 contemplates, which means the <b>rows themselves</b> must be
 * held: the confirmation has to enumerate numbers that are not on the current page (FR-SNDD-009).
 * Hence the state below is a {@code ref → row} dictionary rather than a set of identifiers.</p>
 */

const PAGE_SIZE = 20;

/**
 * 안정적인 빈 배열. / A stable empty array.
 *
 * <p>매 렌더 새 배열을 넘기면 데이터가 바뀐 것으로 보여 테이블 모델이 계속 다시 계산된다.</p>
 * <p>A fresh array each render looks like new data and rebuilds the table model every time.</p>
 */
const NO_ROWS: SenderNumberRow[] = [];

const features = tableFeatures({
  rowPaginationFeature,
  rowSelectionFeature,
  columnMeta: {} as GridColumnMeta,
});

const columnHelper = createColumnHelper<typeof features, SenderNumberRow>();

interface Props {
  /**
   * 행을 선택했을 때의 콜백 — Sprint S2 의 상세 화면이 소비한다. `ref` 를 넘긴다.
   * Called when a row is chosen; Sprint S2's detail screen consumes it. Passes `ref`.
   */
  onSelect?: (row: SenderNumberRow) => void;
}

/**
 * 발신번호 관리 화면 컴포넌트. / The sender-number management screen component.
 */
export function SenderNumberPage({ onSelect }: Props) {
  const [searchParams, setSearchParams] = useSearchParams();
  const institution = searchParams.get('institution') ?? '';
  const pageParam = Number(searchParams.get('page'));
  const page = Number.isInteger(pageParam) && pageParam > 0 ? pageParam : 0;

  /*
    선택 상태는 `ref` 로 관리한다 — 표시되는 발신번호가 아니다. 레거시 그리드가 표시값을
    그대로 선택·삭제에 사용한 것이 D-S1 의 직접 원인이었다(FR-SND-007). 아래 `getRowId` 가
    행 식별자를 `ref` 로 못박으므로, 이 상태의 키는 언제나 `ref` 다.
    Selection is keyed by `ref`, never by the displayed number: the legacy grid carrying the
    displayed value into selection and deletion is the direct cause of D-S1 (FR-SND-007). The
    `getRowId` below pins the row identifier to `ref`, so this state's keys are always refs.

    값으로 <b>행 전체</b>를 들고 있는 이유는 서버 페이징이다. 3페이지에 있는 동안 1페이지에서
    고른 행을 삭제 확인 화면이 열거해야 하고(FR-SNDD-009), 그 행은 현재 응답에 없다. 식별자만
    들고 있으면 열거할 번호가 없으므로 "선택 2건" 같은 요약으로 후퇴하게 되는데, 그러면 운영자가
    무엇을 지우는지 확인할 수 없다 — 보지 못한 것에 조작이 걸리는 D-S1 의 계열이다.
    The value is the <b>whole row</b> because of server-side paging: while on page 3 the delete
    confirmation must enumerate rows chosen on page 1 (FR-SNDD-009), and those rows are not in the
    current response. Holding identifiers alone would leave nothing to enumerate and force a retreat
    to "2 selected" — which is D-S1's family again: an operation on something unseen.
  */
  const [selected, setSelected] = useState<Record<string, SenderNumberRow>>({});

  /** 열려 있는 대화상자. / The dialog currently open. */
  const [dialog, setDialog] = useState<'register' | 'delete' | null>(null);

  /** 방금 끝난 조작의 결과 안내. / The outcome notice for the operation just completed. */
  const [notice, setNotice] = useState<string | null>(null);

  // 이용기관 목록. 레거시는 USE_YN=ALL 로 전 기관을 받아 사용 여부를 구분 없이 나열했다.
  // 여기서는 상태를 함께 보여 준다(FR-SND-010).
  // The institution list. The legacy fetched every institution with USE_YN=ALL and listed them
  // without distinguishing status; here the status is shown alongside (FR-SND-010).
  const institutions = useInstitutionOptions();

  const senderNumbers = useSenderNumbers(institution, page, PAGE_SIZE);

  /*
    오류 상태에서는 캐시에 남아 있는 직전 성공 응답을 읽지 않는다 — 실패한 조회 뒤에 남은
    행은 조회에 성공한 것처럼 보인다.
    While the query is in error the previous successful response in the cache is not read: rows
    left over from a failed search read as a successful one.
  */
  const result = senderNumbers.isError ? undefined : senderNumbers.data;
  const loading = senderNumbers.isFetching;
  const error = senderNumbers.isError
    ? senderNumbers.error instanceof Error
      ? senderNumbers.error.message
      : '발신번호를 조회할 수 없습니다.'
    : null;

  const pagination = useMemo<PaginationState>(
    () => ({ pageIndex: page, pageSize: PAGE_SIZE }),
    [page],
  );

  /*
    테이블이 요구하는 형태({ref: true})는 위 사전에서 파생한다. 두 상태를 나란히 두면 어긋날 수
    있고, 어긋난 쪽이 삭제 요청에 실리는 순간 확인 화면과 지워지는 집합이 달라진다.
    The shape the table wants is derived from the dictionary above. Two parallel states could
    disagree, and the moment the wrong one reaches the delete request the confirmation and the
    deleted set differ.
  */
  const rowSelection = useMemo<RowSelectionState>(
    () => Object.fromEntries(Object.keys(selected).map((ref) => [ref, true])),
    [selected],
  );

  const selectedRows = useMemo(() => Object.values(selected), [selected]);

  const columns = useMemo(
    () =>
      columnHelper.columns([
        columnHelper.display({
          id: 'select',
          header: (ctx) => {
            const rows = ctx.table.getRowModel().rows;
            return (
              <input
                type="checkbox"
                aria-label="전체 선택"
                checked={rows.length > 0 && rows.every((row) => row.getIsSelected())}
                disabled={rows.length === 0}
                onChange={ctx.table.getToggleAllRowsSelectedHandler()}
              />
            );
          },
          cell: (ctx) => (
            <input
              type="checkbox"
              aria-label={`${ctx.row.original.number} 선택`}
              checked={ctx.row.getIsSelected()}
              onChange={ctx.row.getToggleSelectedHandler()}
            />
          ),
        }),
        columnHelper.accessor('institutionName', { header: '기관명' }),
        columnHelper.accessor('number', {
          header: '발신번호',
          cell: (ctx) =>
            onSelect ? (
              // 레거시 그리드는 행 전체를 클릭 대상으로 삼았다. 버튼을 쓰면 키보드로 접근
              // 가능하고 스크린리더가 조작 가능한 요소로 인식한다(WCAG 2.1.1).
              // The legacy made the whole row clickable. A button is keyboard reachable and
              // announced as operable (WCAG 2.1.1).
              <button
                type="button"
                className="link-button"
                onClick={() => onSelect(ctx.row.original)}
              >
                {ctx.getValue()}
              </button>
            ) : (
              ctx.getValue()
            ),
        }),
        columnHelper.accessor('registeredBy', { header: '등록자', cell: (ctx) => ctx.getValue() ?? '' }),
        columnHelper.accessor('registeredAt', {
          header: '등록일자',
          cell: (ctx) => formatTimestamp(ctx.getValue()),
        }),
        columnHelper.accessor('updatedBy', { header: '수정자', cell: (ctx) => ctx.getValue() ?? '' }),
        columnHelper.accessor('updatedAt', {
          header: '수정일자',
          cell: (ctx) => formatTimestamp(ctx.getValue()),
        }),
        columnHelper.accessor('description', {
          header: '설명',
          cell: (ctx) => ctx.getValue() ?? '',
          meta: { cellClassName: 'lg-cell-text' },
        }),
      ]),
    [onSelect],
  );

  const table = useTable({
    features,
    columns,
    data: result?.rows ?? NO_ROWS,
    // 행 식별자는 `ref` 다. 이 한 줄이 선택·후속 동작이 표시값을 잡지 못하게 막는다(D-S1).
    // The row identifier is `ref`; this single line keeps selection and any follow-up action
    // from ever taking hold of a displayed value (D-S1).
    getRowId: (row) => row.ref,
    manualPagination: true,
    pageCount: result?.totalPages ?? 0,
    state: { pagination, rowSelection },
    /*
      선택이 바뀌면 사전을 다시 만든다. 참인 키마다 행을 찾는데, <b>현재 페이지 먼저, 없으면
      기존 사전</b>에서 찾는다 — 그래야 다른 페이지에서 고른 행이 페이지를 넘겨도 남는다.
      테이블은 현재 페이지의 행만 알고 있으므로 그 순서가 중요하다.
      A selection change rebuilds the dictionary: for each true key the row is looked up in <b>the
      current page first, then the existing dictionary</b>, which is what keeps rows chosen on
      another page alive across a page move. The table only knows the current page, so that order
      matters.
    */
    onRowSelectionChange: (updater) => {
      const next = typeof updater === 'function' ? updater(rowSelection) : updater;
      const visible = new Map((result?.rows ?? NO_ROWS).map((row) => [row.ref, row]));
      setSelected((previous) => {
        const merged: Record<string, SenderNumberRow> = {};
        for (const [ref, isSelected] of Object.entries(next)) {
          if (!isSelected) {
            continue;
          }
          const row = visible.get(ref) ?? previous[ref];
          if (row) {
            merged[ref] = row;
          }
        }
        return merged;
      });
    },
    onPaginationChange: (updater) => {
      const next = typeof updater === 'function' ? updater(pagination) : updater;
      setSearchParams((previous) => {
        const params = new URLSearchParams(previous);
        params.set('page', String(next.pageIndex));
        return params;
      });
    },
  });

  const onInstitutionChange = (value: string) => {
    // 기관이 바뀌면 페이지를 처음으로 되돌린다. 3페이지를 보다 기관을 바꿨을 때
    // 3페이지가 유지되면 결과가 없는 것처럼 보인다.
    // Changing institution resets to the first page: keeping page 3 after a switch would look
    // like an empty result.
    const params = new URLSearchParams();
    if (value) {
      params.set('institution', value);
    }
    params.set('page', '0');
    setSearchParams(params);

    // 선택도 함께 비운다(FR-SNDD-011). 선택은 지금 보고 있는 기관의 행에 대한 것이며, 보이지
    // 않는 기관의 행을 선택된 채로 들고 있으면 삭제가 사용자가 보지 못한 행에 걸린다 — D-S1 과
    // 같은 종류의 사고다. 페이지 이동은 선택을 비우지 않는다: 그쪽은 100건 삭제를 조립하는
    // 정상 경로이며(NFR-PERF-D03), 확인 화면이 전부 열거하므로 보지 못한 행이 남지 않는다.
    // The selection is cleared (FR-SNDD-011): it refers to the institution now on screen, and
    // keeping rows from one no longer displayed would apply the delete to rows the user never saw —
    // a D-S1-shaped accident. Paging does not clear it: that is the normal way a 100-number delete
    // is assembled (NFR-PERF-D03), and the confirmation enumerates every one, so nothing stays
    // unseen.
    setSelected({});
    setNotice(null);
  };

  /**
   * 쓰기가 끝난 뒤의 정리. / Tidy-up after a write.
   *
   * 목록 무효화는 mutation 이 한다(FR-SND-012). 여기서는 화면 상태만 정리한다 — 선택은 이제
   * 존재하지 않는(또는 방금 생긴) 행을 가리키므로 비운다.
   * The list invalidation is the mutation's job (FR-SND-012); this clears screen state only. The
   * selection now points at rows that no longer exist — or has just become stale — so it goes.
   */
  const afterWrite = (message: string) => {
    setSelected({});
    setNotice(message);
  };

  return (
    <main className="page-wrap">
      {/* 제목 + breadcrumb — 레거시 title_wrpa / legacy title_wrpa */}
      <div className="lg-title">
        <h1>이용기관 정보 관리</h1>
        <span className="lg-breadcrumb">
          BIZTALK <span aria-hidden="true">›</span> <strong>이용기관 정보 관리</strong>
        </span>
      </div>

      {/* 레거시 infoList01 */}
      <ul className="lg-info">
        <li>이용기관을 선택하면 해당 기관에 등록된 발신번호가 조회됩니다.</li>
      </ul>

      {/* 레거시 tb_srchBtn */}
      <form
        className="lg-search"
        aria-label="발신번호 조회"
        onSubmit={(e) => {
          e.preventDefault();
          // 조건이 그대로이므로 URL 도 캐시 키도 바뀌지 않는다. '조회' 는 "지금 다시
          // 조회하라" 는 뜻이므로 명시적으로 다시 요청한다.
          // Nothing about the criteria changed, so neither the URL nor the cache key moves.
          // 조회 means "run it again now", so the refetch is explicit.
          void senderNumbers.refetch();
        }}
      >
        <label htmlFor="senderno-institution">이용기관</label>
        <select
          id="senderno-institution"
          value={institution}
          onChange={(e) => onInstitutionChange(e.target.value)}
        >
          <option value="">-</option>
          {(institutions.data?.rows ?? []).map((row) => (
            <option key={row.code} value={row.code}>
              {row.name ?? row.code}
              {row.statusLabel ? ` (${row.statusLabel})` : ''}
            </option>
          ))}
        </select>

        <span className="lg-search-actions">
          <button type="submit" className="lg-btn lg-btn-primary" disabled={loading || !institution}>
            조회
          </button>
        </span>
      </form>

      {error ? (
        <p role="alert" className="field-error visible">
          {error}
        </p>
      ) : null}

      {/*
        결과 안내. 레거시는 삭제가 <b>아무 행도 지우지 않았을 때도</b>
        "정상적으로 처리되었습니다" 를 보여 주었다(D-S1). 여기서 건수를 말하는 것은 그 문장이
        무의미했기 때문이다 — 서버는 0건이면 거절하므로(FR-SNDD-002) 이 안내는 실제로 바뀐
        행 수를 말한다.
        The outcome notice. The legacy said "processed normally" even when the delete removed
        nothing (D-S1). The count is stated here because that sentence meant nothing: the server
        refuses a zero-row delete (FR-SNDD-002), so this notice reports rows that actually changed.
      */}
      {notice ? (
        <p role="status" className="lg-notice">
          {notice}
        </p>
      ) : null}

      {/*
        레거시 tabType02. 탭은 하나뿐이다 — 수수료 탭은 레거시 JSP 에서 주석 처리되어
        있었고 서비스 계약도 쿼리도 없다(§2.7). 하나뿐인 탭은 사실상 구획 제목이며,
        여기서는 그 역할로만 쓴다.
        Legacy tabType02. There is one tab: the 수수료 tab was commented out of the legacy JSP
        and has neither a service contract nor a query (§2.7). A lone tab is really a section
        heading, and it is used as one here.
      */}
      <div className="lg-tabs">
        <span className="lg-tab">발신번호</span>
        <span className="lg-tab-actions">
          {/*
            활성 조건이 있는 버튼이다(FR-SNDD-010). 레거시는 조건 없이 팝업을 열었고, 삭제는
            선택이 없어도 빈 목록으로 요청을 보냈다 — D-S1 때문에 그것마저 성공으로 보고되었다.
            비활성 이유를 title 로 말해 준다: 눌리지 않는 버튼보다 나쁜 것은 왜 눌리지 않는지
            모르는 버튼이다.
            Buttons with enablement conditions (FR-SNDD-010). The legacy opened its popups
            regardless, and delete sent an empty list when nothing was selected — which, thanks to
            D-S1, also reported success. The reason is given in the title: worse than a button that
            cannot be pressed is one that does not say why.
          */}
          <button
            type="button"
            className="lg-btn"
            disabled={!institution}
            title={institution ? undefined : '이용기관을 먼저 선택하세요'}
            onClick={() => setDialog('register')}
          >
            등록
          </button>
          <button
            type="button"
            className="lg-btn"
            disabled={selectedRows.length === 0}
            title={selectedRows.length === 0 ? '삭제할 발신번호를 선택하세요' : undefined}
            onClick={() => setDialog('delete')}
          >
            삭제
          </button>
          {/*
            선택 건수는 <b>항상</b> 보인다(FR-SNDD-009). 3페이지를 보는 동안 1페이지에서 고른
            선택이 화면에서 사라지면, 운영자는 자기가 무엇을 들고 있는지 모른 채 삭제를 누른다.
            The selected count is <b>always</b> visible (FR-SNDD-009): if a selection made on page 1
            disappears from view while page 3 is displayed, the operator presses 삭제 without knowing
            what they are holding.
          */}
          {selectedRows.length > 0 && (
            <span className="lg-selected-count" data-testid="senderno-selected-count">
              선택 {selectedRows.length}건
            </span>
          )}
        </span>
      </div>

      <section aria-live="polite">
        {/*
          그리드는 <b>항상</b> 머리글과 함께 렌더링한다. 표 자체를 숨기면 어떤 열이 있는지조차
          조회 전에는 알 수 없다 — 레거시도 빈 상태에서 머리글을 갖춘 그리드를 보여 주고
          본문에만 "조회된 내용이 없습니다" 를 넣었다.
          The grid is <b>always</b> rendered with its headers. Hiding the table leaves the user
          unable to see even which columns exist before querying; the legacy showed a
          full-header grid and put "조회된 내용이 없습니다" in the body.
        */}
        <DataTable
          table={table}
          caption="발신번호 목록"
          captionClassName="sr-only"
          className="lg-grid"
          wrapperClassName="lg-grid-wrap"
          emptyClassName="lg-empty"
          emptyContent={
            loading
              ? '조회 중입니다.'
              : institution
                ? '조회된 내용이 없습니다.'
                : '이용기관을 선택하세요.'
          }
        />

        <nav className="lg-paging" aria-label="페이지 이동">
          <button
            type="button"
            className="lg-btn"
            disabled={!table.getCanPreviousPage() || loading}
            onClick={() => table.previousPage()}
          >
            이전
          </button>
          <span>
            {result && result.totalPages > 0 ? page + 1 : 0} /{' '}
            {Math.max(result?.totalPages ?? 0, 1)} · 총{' '}
            {(result?.totalCount ?? 0).toLocaleString()}건
          </span>
          <button
            type="button"
            className="lg-btn"
            disabled={loading || !table.getCanNextPage()}
            onClick={() => table.nextPage()}
          >
            다음
          </button>
        </nav>
      </section>

      {/*
        대화상자는 목록과 <b>같은 문서</b>에 있다(ADR-SND-020). 레거시는 window.open 이었지만
        그것이 실제로 보장한 세 가지 — 목록이 고른 기관 없이는 열리지 않는다, 그 기관은 폼에서
        읽기 전용이다, 완료되면 목록이 다시 조회된다 — 는 모달이 그대로 갖는다. window.open 은
        그 셋 중 어느 것도 SPA 에서 지키지 못한다(opener.getDat() 가 없다).
        The dialogs live in the <b>same document</b> as the list (ADR-SND-020). The legacy used
        window.open, but the three things it actually guaranteed — cannot open without the
        institution the list selected, that institution read-only, the list re-queried on completion
        — are exactly what the modal keeps. window.open keeps none of them in an SPA: there is no
        opener.getDat() to call.
      */}
      {dialog === 'register' && institution && (
        <SenderNumberRegisterDialog
          institution={institution}
          onClose={() => setDialog(null)}
          onRegistered={() => afterWrite('발신번호가 등록되었습니다.')}
        />
      )}

      {dialog === 'delete' && selectedRows.length > 0 && (
        <SenderNumberDeleteDialog
          // 선택된 <b>모든</b> 행을 넘긴다 — 지금 페이지에 없는 행까지(FR-SNDD-009).
          // <b>Every</b> selected row is passed, including those not on the current page.
          targets={selectedRows}
          onClose={() => setDialog(null)}
          onDeleted={(affected) => afterWrite(`발신번호 ${affected}건이 제거되었습니다.`)}
        />
      )}
    </main>
  );
}
