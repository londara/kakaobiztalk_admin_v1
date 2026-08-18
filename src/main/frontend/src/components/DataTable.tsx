/**
 * 공용 그리드 렌더러. / Shared grid renderer.
 *
 * req: FR-INST-002, FR-MSG-004, FR-SND-005, NFR-USE-01
 *
 * <p>TanStack Table 은 <b>헤드리스</b>다 — 행·열·페이지 모델만 계산하고 마크업은 만들지
 * 않는다. 마크업을 여기 한 곳에 두는 이유는 세 화면이 같은 접근성 계약을 지켜야 하기
 * 때문이다: {@code <caption>} 이 있고, 머리글 셀은 {@code scope="col"} 이며, 빈 결과도
 * 머리글과 함께 표시된다. 화면마다 표를 다시 쓰면 그중 하나만 어긋나도 알아채기 어렵다.</p>
 *
 * <p>TanStack Table is headless: it computes row, column and page models and emits no markup.
 * The markup lives here because the three screens must keep the same accessibility contract —
 * a {@code <caption>}, {@code scope="col"} on header cells, and an empty result still rendered
 * with its headers. Rewriting the table per screen makes a single drifting copy hard to spot.</p>
 *
 * <p>정렬 기능은 등록하지 않는다. 페이징이 서버에서 이루어지므로 클라이언트 정렬은 현재
 * 페이지 안에서만 동작하고, 사용자에게는 전체가 정렬된 것처럼 보인다 — 3페이지짜리 결과의
 * 1페이지만 정렬해 보여 주는 것은 정렬이 아니라 오해다. 정렬은 서버가 {@code ORDER BY} 를
 * 지원할 때 함께 넣는다.</p>
 * <p>No sorting feature is registered. Paging happens on the server, so client-side sorting would
 * order only the current page while appearing to order the whole result. Sorting arrives when the
 * server supports it.</p>
 */

import type { ReactNode } from 'react';
import type { ReactTable, RowData, TableFeatures, TableState } from '@tanstack/react-table';

/**
 * 컬럼 정의에 붙일 수 있는 표시 정보. / Presentation hints carried on a column definition.
 *
 * <p>{@code tableFeatures({ columnMeta: {} as GridColumnMeta })} 로 화면별로 타입을 등록한다.</p>
 */
export interface GridColumnMeta {
  /** 셀({@code td})에 붙일 클래스 / class applied to the cell */
  cellClassName?: string;
}

interface DataTableProps<TFeatures extends TableFeatures, TData extends RowData> {
  /** {@code useTable} 이 돌려준 테이블 / the table returned by {@code useTable} */
  table: ReactTable<TFeatures, TData, TableState<TFeatures>>;
  /** 표 제목 — 스크린리더가 표의 목적을 읽는 유일한 통로 / the table's purpose, for screen readers */
  caption: string;
  /** 제목에 붙일 클래스 (시각적으로 감출 때 {@code sr-only}) / class for the caption */
  captionClassName?: string;
  /** 표에 붙일 클래스 / class for the table element */
  className?: string;
  /** 표를 감싸는 요소의 클래스, 없으면 감싸지 않는다 / wrapper class; no wrapper when absent */
  wrapperClassName?: string;
  /** 행이 없을 때 본문에 넣을 내용 / content for the body when there are no rows */
  emptyContent?: ReactNode;
  /** 빈 상태 셀의 클래스 / class for the empty-state cell */
  emptyClassName?: string;
}

/**
 * 테이블 모델을 표 마크업으로 렌더링한다. / Renders a table model as table markup.
 *
 * @param props 렌더 옵션 / render options
 */
export function DataTable<TFeatures extends TableFeatures, TData extends RowData>({
  table,
  caption,
  captionClassName,
  className,
  wrapperClassName,
  emptyContent,
  emptyClassName,
}: DataTableProps<TFeatures, TData>) {
  const rows = table.getRowModel().rows;
  const showEmptyRow = rows.length === 0 && emptyContent !== undefined;

  const element = (
    <table className={className}>
      <caption className={captionClassName}>{caption}</caption>
      <thead>
        {table.getHeaderGroups().map((group) => (
          <tr key={group.id}>
            {group.headers.map((header) => (
              <th key={header.id} scope="col">
                {header.isPlaceholder ? null : <table.FlexRender header={header} />}
              </th>
            ))}
          </tr>
        ))}
      </thead>
      <tbody>
        {showEmptyRow ? (
          <tr>
            <td colSpan={table.getAllLeafColumns().length} className={emptyClassName}>
              {emptyContent}
            </td>
          </tr>
        ) : (
          rows.map((row) => (
            <tr key={row.id}>
              {row.getAllCells().map((cell) => {
                /*
                  meta 는 화면이 등록한 타입이므로 여기서는 제네릭 뒤에 가려져 있다. 좁히는
                  캐스트를 한 곳에만 두고, 값은 선택적으로만 읽는다.
                  The meta type is registered per screen and hidden behind the generic here; the
                  narrowing cast is kept to this one place and read optionally.
                */
                const meta = cell.column.columnDef.meta as GridColumnMeta | undefined;
                return (
                  <td key={cell.id} className={meta?.cellClassName}>
                    <table.FlexRender cell={cell} />
                  </td>
                );
              })}
            </tr>
          ))
        )}
      </tbody>
    </table>
  );

  return wrapperClassName ? <div className={wrapperClassName}>{element}</div> : element;
}
