package com.webcash.iris.biztalk.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link SourceMerger} 검증 — TEST-PLAN §3.1 의 속성 시험.
 * Verification for {@link SourceMerger} — the property suite of TEST-PLAN §3.1.
 *
 * <p>이 병합은 이 슬라이스에서 <b>틀렸을 때 오류가 나지 않는 유일한 곳</b>이다. 순서가
 * 어긋나거나 경계가 잘못 잡히면 예외 대신 그럴듯한 숫자가 나온다. 그래서 예제 시험이 아니라
 * 생성된 데이터에 대한 속성 시험으로 검증한다 — 특히 <b>두 출처 모두에 있는 키 위로 페이지
 * 경계가 떨어지는 경우</b>가 이 설계가 가질 법한 결함이다.</p>
 * <p>This merge is the <b>one place in the slice where being wrong raises nothing</b>: a bad order
 * or a mishandled boundary yields plausible numbers, not an exception. It is therefore verified
 * with properties over generated data rather than examples — above all the case where <b>a page
 * boundary lands on a key present in both sources</b>, which is the defect this design could
 * plausibly have.</p>
 *
 * // req: FR-RPT-005, FR-RPT-006, FR-RPTS-003, ADR-RPT-021
 */
class SourceMergerTest {

    private static final int LIMIT = 10_000;

    // ── 시험 데이터 만들기 / building test data ────────────────────────────────────

    private static AggregateRow row(String tradeDate, String institution, long perChannel) {
        ChannelCounters counters =
                new ChannelCounters(perChannel * 4, perChannel * 2, perChannel, perChannel);
        return new AggregateRow(tradeDate, institution, "기관-" + institution,
                counters, counters, counters, counters, counters, counters, counters);
    }

    /** 표시 순서(일자 내림차순, 기관 오름차순)로 정렬한다. / Sorts into display order. */
    private static List<AggregateRow> ordered(List<AggregateRow> rows) {
        List<AggregateRow> copy = new ArrayList<>(rows);
        copy.sort(Comparator.comparing(AggregateRow::key, AggregateKey.DISPLAY_ORDER));
        return copy;
    }

    /**
     * 서로 다른 키 {@code count} 개를 만든다. / Builds {@code count} distinct keys.
     *
     * <p>가능한 키 공간을 <b>모두 만든 뒤 섞어서</b> 잘라낸다. 거절 표집으로 뽑으면 요청 수가
     * 공간보다 클 때 영원히 돌고, 같을 때도 쿠폰 수집가 문제가 되어 느려진다 — 시험이 실패가
     * 아니라 <b>멈춤</b>으로 나타나는 실수는 찾기 어렵다.</p>
     * <p>The whole key space is built, shuffled and truncated. Rejection sampling loops forever
     * when the requested count exceeds the space and degrades into the coupon-collector problem
     * when it equals it — and a test that <b>hangs</b> rather than fails is hard to diagnose.</p>
     */
    private static List<AggregateRow> generate(Random random, int count, int dayCount, int institutions) {
        List<AggregateKey> space = new ArrayList<>(dayCount * institutions);
        for (int day = 0; day < dayCount; day++) {
            for (int institution = 0; institution < institutions; institution++) {
                space.add(new AggregateKey(
                        String.format("2026%04d", 100 + day),
                        String.format("K%04d", institution)));
            }
        }
        if (count > space.size()) {
            throw new IllegalArgumentException(
                    "Requested " + count + " distinct keys from a space of " + space.size());
        }

        java.util.Collections.shuffle(space, random);
        Set<AggregateKey> chosen = new LinkedHashSet<>(space.subList(0, count));

        List<AggregateRow> rows = new ArrayList<>(count);
        for (AggregateKey key : chosen) {
            rows.add(row(key.tradeDate(), key.institutionCode(), 1 + random.nextInt(50)));
        }
        return ordered(rows);
    }

    @Nested
    @DisplayName("P-1..P-2 합집합과 합계 / union and summation")
    class UnionAndSum {

        /** P-1: 병합 행 수 = 두 키 집합의 합집합 크기. / merged count equals the key-set union size. */
        // req: FR-RPTS-003
        @Test
        @DisplayName("P-1 병합 행 수는 두 키 집합의 합집합 크기와 같다")
        void mergedCountEqualsKeyUnion() {
            Random random = new Random(20260818L);
            List<AggregateRow> api = generate(random, 120, 30, 12);
            List<AggregateRow> bulk = generate(random, 90, 30, 12);

            Set<AggregateKey> union = new LinkedHashSet<>();
            api.forEach(r -> union.add(r.key()));
            bulk.forEach(r -> union.add(r.key()));

            assertThat(SourceMerger.merge(api, bulk, LIMIT)).hasSize(union.size());
        }

        /** P-2: 각 건수는 그 키를 가진 출처들의 합. / each counter is the sum over holders of that key. */
        // req: FR-RPTS-003
        @Test
        @DisplayName("P-2 같은 키의 건수는 두 출처의 합이다")
        void countersAreSummedForSharedKeys() {
            List<AggregateRow> api = ordered(List.of(row("20260701", "K0001", 5)));
            List<AggregateRow> bulk = ordered(List.of(row("20260701", "K0001", 3)));

            List<ReportRow> merged = SourceMerger.merge(api, bulk, LIMIT);

            assertThat(merged).hasSize(1);
            ReportRow only = merged.get(0);
            assertThat(only.source()).isEqualTo(SendSource.ALL);
            ChannelCounters alimtalk = only.counters().get(MessageChannel.ALIMTALK);
            assertThat(alimtalk.total()).isEqualTo(5 * 4 + 3 * 4);
            assertThat(alimtalk.success()).isEqualTo(5 * 2 + 3 * 2);
            assertThat(alimtalk.failed()).isEqualTo(5 + 3);
            assertThat(alimtalk.inFlight()).isEqualTo(5 + 3);
        }

        /** P-5: 한쪽에만 있는 행은 한 번, 그대로. / a one-sided row appears once, unchanged. */
        // req: FR-RPTS-003
        @Test
        @DisplayName("P-5 한쪽 출처에만 있는 행은 그대로 한 번 나온다")
        void oneSidedRowsPassThroughUnchanged() {
            List<AggregateRow> api = ordered(List.of(row("20260702", "K0001", 7)));
            List<AggregateRow> bulk = ordered(List.of(row("20260701", "K0002", 9)));

            List<ReportRow> merged = SourceMerger.merge(api, bulk, LIMIT);

            assertThat(merged).hasSize(2);
            assertThat(merged.get(0).source()).isEqualTo(SendSource.API);
            assertThat(merged.get(0).counters().get(MessageChannel.SMS).total()).isEqualTo(28);
            assertThat(merged.get(1).source()).isEqualTo(SendSource.BULK);
            assertThat(merged.get(1).counters().get(MessageChannel.SMS).total()).isEqualTo(36);
        }
    }

    @Nested
    @DisplayName("P-3..P-4 페이징 / paging")
    class Paging {

        /**
         * P-3 · P-4: 임의의 페이지 크기로 끝까지 페이징한 결과가, 한 번에 읽은 결과와 같아야
         * 한다. 중복도 누락도 없어야 하며 <b>경계가 공유 키 위에 떨어지는 경우</b>가 핵심이다.
         * P-3 and P-4: paging to the end at an arbitrary size must equal one unpaginated read,
         * with no duplicates and no gaps — the case that matters being <b>a boundary landing on a
         * shared key</b>.
         */
        // req: FR-RPT-005, ADR-RPT-021
        @ParameterizedTest(name = "[{index}] 페이지 크기 {0}")
        @ValueSource(ints = {1, 2, 3, 5, 7, 13, 50})
        void pagingReproducesTheWholeResult(int pageSize) {
            Random random = new Random(4242L + pageSize);
            // 키 공간(10일 × 8기관 = 80)보다 두 출처의 합(115)이 크므로 공유 키가 흔하다 —
            // 페이지 경계가 공유 키 위에 떨어지는 경우를 노린다.
            // The two sources together (115) exceed the key space (10 days x 8 institutions = 80),
            // so shared keys are frequent — which is what puts a page boundary on one.
            List<AggregateRow> api = generate(random, 60, 10, 8);
            List<AggregateRow> bulk = generate(random, 55, 10, 8);

            List<ReportRow> whole = SourceMerger.merge(api, bulk, LIMIT);

            List<ReportRow> paged = new ArrayList<>();
            AggregateKey seek = null;
            for (int guard = 0; guard < 500; guard++) {
                List<AggregateRow> apiSlice = seekSlice(api, seek, pageSize + 1);
                List<AggregateRow> bulkSlice = seekSlice(bulk, seek, pageSize + 1);
                List<ReportRow> page = SourceMerger.merge(apiSlice, bulkSlice, pageSize);
                if (page.isEmpty()) {
                    break;
                }
                paged.addAll(page);
                seek = page.get(page.size() - 1).key();
            }

            assertThat(paged.stream().map(ReportRow::key).toList())
                    .describedAs("paged keys must equal the unpaginated keys, in order")
                    .isEqualTo(whole.stream().map(ReportRow::key).toList());

            assertThat(paged.stream().map(ReportRow::key).distinct().count())
                    .describedAs("no key may appear twice across pages")
                    .isEqualTo(paged.size());

            for (int i = 0; i < paged.size(); i++) {
                assertThat(paged.get(i).counters().get(MessageChannel.ALIMTALK))
                        .describedAs("counters must survive paging unchanged at index %d", i)
                        .isEqualTo(whole.get(i).counters().get(MessageChannel.ALIMTALK));
            }
        }

        /**
         * 매퍼의 이어보기 술어를 메모리에서 재현한다 — 정렬 방향이 섞여 있어 행 값 비교를
         * 쓸 수 없다는 사실까지 포함해서.
         * Reproduces the mapper's seek predicate in memory, including the fact that mixed sort
         * directions rule out row-value comparison.
         */
        private static List<AggregateRow> seekSlice(List<AggregateRow> rows,
                                                    AggregateKey seek,
                                                    int limit) {
            return rows.stream()
                    .filter(r -> seek == null || r.key().compareTo(seek) > 0)
                    .limit(limit)
                    .toList();
        }
    }

    @Nested
    @DisplayName("P-6 대칭성 / symmetry")
    class Symmetry {

        /** P-6: 어느 출처를 먼저 읽든 결과가 같아야 한다. / the result must not depend on read order. */
        // req: FR-RPTS-003
        @Test
        @DisplayName("P-6 출처를 읽는 순서가 결과를 바꾸지 않는다")
        void mergeIsSymmetricInItsSources() {
            Random random = new Random(777L);
            List<AggregateRow> api = generate(random, 40, 8, 8);
            List<AggregateRow> bulk = generate(random, 40, 8, 8);

            List<AggregateKey> forward = SourceMerger.merge(api, bulk, LIMIT)
                    .stream().map(ReportRow::key).toList();
            List<AggregateKey> reverse = SourceMerger.merge(bulk, api, LIMIT)
                    .stream().map(ReportRow::key).toList();

            assertThat(forward).isEqualTo(reverse);
        }
    }

    @Nested
    @DisplayName("순서 위반 감지 / order-violation detection")
    class OrderViolation {

        /**
         * D-R7 회귀이자 이 설계의 마지막 방어선. 정렬이 어긋나면 오류 없이 틀린 합계가
         * 나오므로, 커서가 지나가며 전제를 확인한다.
         * A D-R7 regression and this design's last line of defence: a bad order yields wrong sums
         * with no error, so the cursor checks the assumption as it passes.
         */
        // req: FR-RPT-006, D-R7
        @Test
        @DisplayName("입력이 표시 순서가 아니면 즉시 실패한다")
        void detectsAnUnorderedInput() {
            // 오름차순 — 매퍼가 ORDER BY TRDD ASC 로 되돌아간 상황을 흉내낸다.
            // Ascending: what a mapper reverting to ORDER BY TRDD ASC would produce.
            List<AggregateRow> ascending = List.of(
                    row("20260701", "K0001", 1),
                    row("20260702", "K0001", 1));

            assertThatThrownBy(() -> SourceMerger.merge(ascending, List.of(), LIMIT))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not in display order");
        }

        @Test
        @DisplayName("같은 키가 반복되면 실패한다")
        void detectsARepeatedKey() {
            List<AggregateRow> repeated = List.of(
                    row("20260701", "K0001", 1),
                    row("20260701", "K0001", 2));

            assertThatThrownBy(() -> SourceMerger.single(repeated, SendSource.API, LIMIT))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("한 날짜 안에서 기관코드가 내림차순이면 실패한다")
        void detectsWrongDirectionWithinADate() {
            List<AggregateRow> wrong = List.of(
                    row("20260701", "K0002", 1),
                    row("20260701", "K0001", 1));

            assertThatThrownBy(() -> SourceMerger.merge(wrong, List.of(), LIMIT))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("한 출처만 읽기 / single source")
    class Single {

        @Test
        @DisplayName("발송구분을 좁히면 그 출처만 나온다")
        void narrowedFilterYieldsOneSource() {
            List<AggregateRow> rows = ordered(List.of(
                    row("20260702", "K0001", 1),
                    row("20260701", "K0002", 2)));

            List<ReportRow> result = SourceMerger.single(rows, SendSource.BULK, LIMIT);

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(r -> r.source() == SendSource.BULK);
        }

        @Test
        @DisplayName("limit 을 넘어서 반환하지 않는다")
        void respectsTheLimit() {
            Random random = new Random(9L);
            List<AggregateRow> rows = generate(random, 30, 10, 6);

            assertThat(SourceMerger.single(rows, SendSource.API, 7)).hasSize(7);
            assertThat(SourceMerger.merge(rows, List.of(), 5)).hasSize(5);
        }
    }
}
