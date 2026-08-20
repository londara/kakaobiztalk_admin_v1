package com.webcash.iris.biztalk.domain;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * 두 출처의 정렬된 스트림을 하나로 합친다. / Merges two sorted source streams into one.
 *
 * <h2>이 클래스가 존재하는 이유 / why this class exists</h2>
 * <p>CONFLICT-R02 는 "두 데이터베이스를 합치면서 서버 페이징과 전순서를 동시에 만족할 수
 * 없다"로 제기되었고, 작업 가정은 셋 중 가장 나쁜 안 — <b>양쪽을 전부 읽어 애플리케이션에서
 * 합치기</b> — 이었다. 그것은 D-R8 이 없애려던 무한정 조회를 한 층 위에서 되살리는 일이다.</p>
 * <p>CONFLICT-R02 was raised as "merging two databases, server-side paging and a total order
 * cannot all hold", with the worst of three options as the working assumption: <b>read both
 * sources fully and merge in the application</b> — the unbounded fetch D-R8 exists to remove,
 * reinstated one layer up.</p>
 *
 * <p>네 번째 형태가 있다. 두 출처는 같은 테이블·같은 기본키·같은 정렬 키를 쓰므로, 같은
 * 순서로 정렬된 두 스트림은 <b>각 스트림의 머리만 들고</b> 합칠 수 있다. 페이징은 offset 이
 * 아니라 <b>이어보기 키</b>로 하며, 그 키는 각 출처가 자기 인덱스로 독립 적용한다
 * (ADR-RPT-021).</p>
 * <p>There is a fourth shape. Both sources carry the same table, primary key and sort key, so two
 * identically ordered streams can be combined while holding <b>only each stream's head</b>.
 * Pagination uses a <b>seek key</b> rather than an offset, and each source applies it
 * independently using its own index (ADR-RPT-021).</p>
 *
 * <h2>실패 방식 / how this fails</h2>
 * <p>두 스트림의 정렬이 어긋나면 예외가 아니라 <b>그럴듯한 틀린 합계</b>가 나온다. 그래서
 * {@link AggregateKey#DISPLAY_ORDER} 는 매퍼의 {@code ORDER BY} 와 반드시 일치해야 하고,
 * 이 클래스에는 순서 위반을 즉시 드러내는 검사가 들어 있다.</p>
 * <p>If the two streams disagree on order the result is not an exception but <b>plausible wrong
 * sums</b>. {@link AggregateKey#DISPLAY_ORDER} must therefore match the mappers'
 * {@code ORDER BY} exactly, and this class carries a check that surfaces a violation at once.</p>
 *
 * // req: FR-RPT-005, FR-RPT-006, FR-RPTS-003, ADR-RPT-021
 */
public final class SourceMerger {

    private SourceMerger() {
    }

    /**
     * 두 출처의 행을 합쳐 최대 {@code limit} 개의 보고서 행을 만든다.
     * Merges both sources into at most {@code limit} report rows.
     *
     * <p>같은 키를 가진 두 행은 합산되어 <b>한 행</b>이 된다(FR-RPTS-003). 레거시는
     * {@code Stream.concat} 으로 이어 붙이기만 해서 한 기관이 하루에 두 행으로 나타났다.</p>
     * <p>Rows sharing a key are summed into <b>one</b> row (FR-RPTS-003). The legacy used
     * {@code Stream.concat}, so one institution appeared twice for one day.</p>
     *
     * @param apiRows  API 집계 행, 표시 순서로 정렬됨 / API rows, in display order
     * @param bulkRows 대량 집계 행, 표시 순서로 정렬됨 / bulk rows, in display order
     * @param limit    최대 행 수 / the maximum number of rows
     * @return 병합된 행 / the merged rows
     * @throws IllegalStateException 입력이 표시 순서가 아닐 때 / when an input is not in display order
     */
    // req: FR-RPTS-003, FR-RPT-006
    public static List<ReportRow> merge(List<AggregateRow> apiRows,
                                        List<AggregateRow> bulkRows,
                                        int limit) {

        List<ReportRow> merged = new ArrayList<>(Math.min(limit, 1024));

        OrderedCursor api = new OrderedCursor(apiRows, SendSource.API);
        OrderedCursor bulk = new OrderedCursor(bulkRows, SendSource.BULK);

        while (merged.size() < limit && (api.hasNext() || bulk.hasNext())) {

            if (!bulk.hasNext()) {
                merged.add(ReportRow.of(api.next(), SendSource.API));
                continue;
            }
            if (!api.hasNext()) {
                merged.add(ReportRow.of(bulk.next(), SendSource.BULK));
                continue;
            }

            int order = api.peek().key().compareTo(bulk.peek().key());
            if (order == 0) {
                // 같은 일자·기관 — 두 출처의 건수를 더해 한 행으로 만든다.
                // Same 일자 + 기관: sum both sources' counters into one row.
                merged.add(ReportRow.merged(api.next(), bulk.next()));
            } else if (order < 0) {
                merged.add(ReportRow.of(api.next(), SendSource.API));
            } else {
                merged.add(ReportRow.of(bulk.next(), SendSource.BULK));
            }
        }

        return merged;
    }

    /**
     * 한 출처만 읽을 때의 변환. / The single-source case.
     *
     * <p>발송구분이 API 나 대량으로 좁혀지면 병합할 것이 없다. 그래도 순서 검사는 그대로
     * 수행한다 — 이 화면에서 순서는 표시가 아니라 정확성이기 때문이다.</p>
     * <p>With the filter narrowed to one source there is nothing to merge. The order check still
     * runs, because on this screen ordering is correctness rather than presentation.</p>
     *
     * @param rows   집계 행 / the aggregate rows
     * @param source 출처 / the source
     * @param limit  최대 행 수 / the maximum number of rows
     * @return 보고서 행 / the report rows
     */
    // req: FR-RPTS-003, FR-RPT-006
    public static List<ReportRow> single(List<AggregateRow> rows, SendSource source, int limit) {
        OrderedCursor cursor = new OrderedCursor(rows, source);
        List<ReportRow> result = new ArrayList<>(Math.min(limit, rows.size()));
        while (result.size() < limit && cursor.hasNext()) {
            result.add(ReportRow.of(cursor.next(), source));
        }
        return result;
    }

    /**
     * 한 칸 미리 보는 커서이자 <b>정렬 검증기</b>.
     * A one-step lookahead cursor that doubles as an <b>order validator</b>.
     *
     * <p>병합의 정확성은 입력이 정렬되어 있다는 전제 위에 서 있다. 그 전제가 깨지면 조용히
     * 틀리므로, 커서가 지나가는 자리에서 전제를 확인한다 — 비용은 비교 한 번이고, 대가는
     * "그럴듯한 틀린 숫자"를 즉시 드러내는 것이다.</p>
     * <p>The merge's correctness rests on the inputs being ordered. A broken assumption fails
     * silently, so the cursor checks it in passing: one comparison, in exchange for surfacing
     * plausible-wrong-numbers immediately.</p>
     */
    private static final class OrderedCursor implements Iterator<AggregateRow> {

        private final Iterator<AggregateRow> delegate;
        private final SendSource source;
        private AggregateRow head;
        private AggregateKey previousKey;

        private OrderedCursor(List<AggregateRow> rows, SendSource source) {
            this.delegate = rows.iterator();
            this.source = source;
            advance();
        }

        @Override
        public boolean hasNext() {
            return head != null;
        }

        private AggregateRow peek() {
            if (head == null) {
                throw new NoSuchElementException("No further rows from " + source);
            }
            return head;
        }

        @Override
        public AggregateRow next() {
            AggregateRow current = peek();
            advance();
            return current;
        }

        private void advance() {
            if (head != null) {
                previousKey = head.key();
            }
            head = delegate.hasNext() ? delegate.next() : null;

            if (head != null && previousKey != null) {
                int order = previousKey.compareTo(head.key());
                if (order >= 0) {
                    // 같은 키가 두 번 나오는 것도 위반이다 — 집계의 기본키가 (TRDD, IS_CD)
                    // 이므로 한 출처 안에서 중복될 수 없다. 중복이 보인다면 쿼리가 잘못
                    // 되었거나 정렬이 우리 비교자와 다르다.
                    // A repeated key is also a violation: (TRDD, IS_CD) is the aggregate's
                    // primary key, so one source cannot repeat it. Seeing one means the query is
                    // wrong or its ordering differs from our comparator.
                    throw new IllegalStateException(
                            "Rows from " + source + " are not in display order: "
                                    + previousKey + " was followed by " + head.key()
                                    + ". The mapper's ORDER BY must match "
                                    + "AggregateKey.DISPLAY_ORDER (TRDD DESC, IS_CD ASC) — "
                                    + "a mismatch makes the merge silently wrong (D-R7).");
                }
            }
        }
    }
}
