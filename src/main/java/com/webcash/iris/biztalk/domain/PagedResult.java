package com.webcash.iris.biztalk.domain;

import java.util.List;

/**
 * 페이지 단위 조회 결과. / A page of results.
 *
 * <p>레거시는 전체 행을 반환하고 클라이언트가 그리드에서 페이징했다(서버 페이징이 주석
 * 처리되어 있었다 — 결함 D7). 서버 페이징을 복원하면 <b>전체 건수를 함께 반환해야</b>
 * 클라이언트가 페이지 수를 계산할 수 있다.</p>
 * <p>The legacy returned every row and paged client-side, server paging having been commented
 * out (defect D7). Restoring it means the <b>total count must accompany the page</b> so the
 * client can compute the page count.</p>
 *
 * @param <T>        행 타입 / the row type
 * @param rows       이 페이지의 행 / the rows on this page
 * @param totalCount 조건에 일치하는 전체 건수 / the total matching count
 * @param page       0부터 시작하는 페이지 번호 / the zero-based page number
 * @param size       페이지 크기 / the page size
 *
 * // req: FR-MSG-007, NFR-PERF-02
 */
public record PagedResult<T>(List<T> rows, int totalCount, int page, int size) {

    /**
     * 전체 페이지 수를 반환한다. / Returns the total number of pages.
     *
     * @return 페이지 수 / the page count
     */
    // req: FR-MSG-007
    public int totalPages() {
        if (size <= 0) {
            return 0;
        }
        return (totalCount + size - 1) / size;
    }

    /**
     * 다음 페이지가 있는지 반환한다. / Whether a further page exists.
     *
     * @return 존재 여부 / true when another page follows
     */
    public boolean hasNext() {
        return (page + 1) < totalPages();
    }

    /**
     * 결과가 없는지 반환한다. / Whether the result set is empty.
     *
     * <p>빈 결과와 오류는 구분되어야 한다(FR-MSG-020: "조회 결과가 없습니다" 상태).
     * 레거시는 둘을 같은 빈 그리드로 표시했다.</p>
     * <p>An empty result and an error must be distinguishable; the legacy showed both as the
     * same empty grid.</p>
     *
     * @return 비어 있으면 true / true when empty
     */
    public boolean isEmpty() {
        return rows.isEmpty();
    }
}
