package com.webcash.iris.biztalk.domain;

import java.util.List;

/**
 * 이어보기 방식의 보고서 한 페이지. / One page of the report, keyset-paginated.
 *
 * <p>{@link PagedResult} 와 달리 페이지 <b>번호</b>가 없다. 두 데이터베이스에 걸친 결과를
 * offset 으로 자를 수는 없기 때문이다 — 어느 쪽도 상대 쪽에 몇 행이 앞서는지 모른다. 대신
 * 각 출처가 자기 인덱스로 독립 적용할 수 있는 <b>이어보기 키</b>를 쓴다(ADR-RPT-021).</p>
 * <p>Unlike {@link PagedResult} this carries no page <b>number</b>: a result spanning two
 * databases cannot be cut by offset, because neither side knows how many rows precede it on the
 * other. A <b>seek key</b> each source can apply with its own index is used instead
 * (ADR-RPT-021).</p>
 *
 * @param rows          이 페이지의 행 / the rows on this page
 * @param nextSeek      다음 페이지 요청에 쓸 키. 마지막 페이지면 null / the key for the next page, null when last
 * @param totalCount    전체 건수. 상한 초과 시 null / the exact total, null when the probe ceiling was hit
 * @param hasMore       다음 페이지 존재 여부 / whether a further page exists
 * @param watermark     출처별 집계 기준일 / the aggregation watermark per source
 * @param availability  출처별 가용성 / per-source availability
 *
 * // req: FR-RPT-005, FR-RPT-013, FR-RPTS-005, ADR-RPT-021
 */
public record ReportPage(
        List<ReportRow> rows,
        AggregateKey nextSeek,
        Long totalCount,
        boolean hasMore,
        ReportWatermark watermark,
        SourceAvailability availability) {

    /**
     * 결과가 없는지 반환한다. / Whether the page is empty.
     *
     * <p>빈 결과와 오류, 그리고 <b>미집계</b>는 서로 다른 상태다. 레거시 화면은 셋을 모두
     * 같은 빈 그리드로 보여줬고, 기본 조회 조건이 오늘이었으므로 사용자가 늘 마주친 것은
     * 미집계였다(D-R25).</p>
     * <p>Empty, error and <b>not yet aggregated</b> are three different states. The legacy screen
     * showed all three as the same empty grid, and since its default range was today, what users
     * actually met was the third (D-R25).</p>
     *
     * @return 비어 있으면 true / true when empty
     */
    // req: FR-RPT-014
    public boolean isEmpty() {
        return rows.isEmpty();
    }

    /**
     * 산술 항등식을 위반한 행이 있는지 반환한다.
     * Whether any row on this page fails its arithmetic identity.
     *
     * @return 위반 존재 여부 / true when at least one row fails
     */
    // req: FR-RPT-010
    public boolean hasReconciliationFailures() {
        return rows.stream().anyMatch(row -> !row.reconciliationFailures().isEmpty());
    }
}
