package com.webcash.iris.biztalk.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link ReportWatermark} 검증. / Verification for {@link ReportWatermark}.
 *
 * <p>D-R25 회귀 방지가 목적이다 — 운영 화면이 열자마자 "조회된 내용이 없습니다"를 보여주는
 * 이유는 데이터가 없어서가 아니라 <b>아직 집계되지 않았기 때문</b>이고, 그 둘은 사용자에게
 * 다른 사실이다.</p>
 * <p>Guards the D-R25 regression: the production screen opens on "조회된 내용이 없습니다" not
 * because there is no data but because it has <b>not been aggregated yet</b>, and to a user those
 * are different facts.</p>
 *
 * // req: FR-RPT-013, FR-RPT-014, ADR-RPT-022
 */
class ReportWatermarkTest {

    @Nested
    @DisplayName("해석 / parsing")
    class Parsing {

        @Test
        @DisplayName("YYYYMMDD 를 해석한다")
        void parsesBothSources() {
            ReportWatermark watermark = ReportWatermark.of("20260814", "20260813");

            assertThat(watermark.apiAsOf()).isEqualTo(LocalDate.of(2026, 8, 14));
            assertThat(watermark.bulkAsOf()).isEqualTo(LocalDate.of(2026, 8, 13));
        }

        /**
         * 집계 테이블에 형식이 깨진 TRDD 가 있어도 화면 전체를 실패시키지 않는다.
         * A malformed TRDD in the aggregate must not fail the whole screen.
         */
        // req: FR-RPT-013
        @Test
        @DisplayName("형식이 깨진 값은 '알 수 없음'이 된다")
        void malformedValuesBecomeUnknown() {
            assertThat(ReportWatermark.of("not-a-date", null).apiAsOf()).isNull();
            assertThat(ReportWatermark.of("20261332", null).apiAsOf()).isNull();
            assertThat(ReportWatermark.of("", null).apiAsOf()).isNull();
            assertThat(ReportWatermark.of(null, null)).isEqualTo(ReportWatermark.UNKNOWN);
        }
    }

    @Nested
    @DisplayName("적용 기준일 / the effective watermark")
    class Effective {

        /**
         * 전체 조회에서는 <b>이른 쪽</b>이 기준이다. 늦은 쪽으로 말하면 합산 결과가 실제보다
         * 최신인 것처럼 보인다.
         * For 전체 the <b>earlier</b> of the two applies; quoting the later one would make the
         * merged figures look fresher than they are.
         */
        // req: FR-RPT-013, FR-RPTS-003
        @Test
        @DisplayName("전체 조회는 두 출처 중 이른 쪽을 쓴다")
        void allUsesTheEarlierWatermark() {
            ReportWatermark watermark = ReportWatermark.of("20260814", "20260810");

            assertThat(watermark.effectiveAsOf(SendSource.ALL)).isEqualTo(LocalDate.of(2026, 8, 10));
            assertThat(watermark.effectiveAsOf(SendSource.API)).isEqualTo(LocalDate.of(2026, 8, 14));
            assertThat(watermark.effectiveAsOf(SendSource.BULK)).isEqualTo(LocalDate.of(2026, 8, 10));
        }

        @Test
        @DisplayName("한쪽만 알 수 있으면 그쪽을 쓴다")
        void fallsBackToTheKnownSide() {
            assertThat(ReportWatermark.of("20260814", null).effectiveAsOf(SendSource.ALL))
                    .isEqualTo(LocalDate.of(2026, 8, 14));
            assertThat(ReportWatermark.of(null, "20260810").effectiveAsOf(SendSource.ALL))
                    .isEqualTo(LocalDate.of(2026, 8, 10));
        }
    }

    @Nested
    @DisplayName("미집계 판정 / not-yet-aggregated")
    class NotYetAggregated {

        /**
         * D-R25 회귀. 화면의 기본 조회 범위는 오늘이었고, 배치의 기본 실행은 4일 전 하루만
         * 처리했다. 사용자가 늘 마주친 것은 "데이터 없음"이 아니라 <b>미집계</b>였다.
         * D-R25 regression. The screen's default range was today; the batch's default run covered
         * a single day four days back. What users met was not "no data" but <b>not aggregated</b>.
         */
        // req: FR-RPT-013, FR-RPT-014
        @Test
        @DisplayName("기준일보다 뒤의 날짜는 미집계로 본다")
        void datesBeyondTheWatermarkAreNotYetAggregated() {
            ReportWatermark watermark = ReportWatermark.of("20260814", "20260814");

            assertThat(watermark.isNotYetAggregated(LocalDate.of(2026, 8, 18), SendSource.ALL))
                    .describedAs("today, four days past the batch's reach")
                    .isTrue();
            assertThat(watermark.isNotYetAggregated(LocalDate.of(2026, 8, 15), SendSource.ALL))
                    .isTrue();
            assertThat(watermark.isNotYetAggregated(LocalDate.of(2026, 8, 14), SendSource.ALL))
                    .describedAs("the watermark itself is aggregated")
                    .isFalse();
            assertThat(watermark.isNotYetAggregated(LocalDate.of(2026, 8, 1), SendSource.ALL))
                    .isFalse();
        }

        /**
         * 기준일을 모르면 미집계로 단정하지 않는다 — 그렇게 하면 정상적인 빈 결과까지 전부
         * 미집계로 표시된다.
         * An unknown watermark must not imply "not aggregated", or every legitimately empty
         * result would be labelled so.
         */
        // req: FR-RPT-013, FR-RPT-014
        @Test
        @DisplayName("기준일을 모르면 미집계로 단정하지 않는다")
        void unknownWatermarkAssertsNothing() {
            assertThat(ReportWatermark.UNKNOWN
                    .isNotYetAggregated(LocalDate.of(2026, 8, 18), SendSource.ALL)).isFalse();
        }

        /**
         * ADR-RPT-022 의 명시된 한계: 기준일 <b>아래</b>의 빠진 날은 감지되지 않는다.
         * 배치가 지우고 다시 넣지 못한 날(D-R27)은 조용한 날과 구분되지 않는다.
         * The stated limitation of ADR-RPT-022: a gap <b>below</b> the watermark is undetectable.
         * A day the batch deleted and failed to reinsert (D-R27) is indistinguishable from a
         * quiet one.
         */
        // req: ADR-RPT-022, D-R27
        @Test
        @DisplayName("기준일 아래의 빠진 날은 감지하지 못한다 — 문서화된 한계")
        void interiorGapsAreNotDetectable() {
            ReportWatermark watermark = ReportWatermark.of("20260814", "20260814");

            // 8월 5일이 배치 실패로 통째로 비어 있어도 기준일 아래이므로 미집계로 보이지 않는다.
            // Even if 5 August is entirely missing through a failed batch run, it lies below the
            // watermark and is not reported as un-aggregated.
            assertThat(watermark.isNotYetAggregated(LocalDate.of(2026, 8, 5), SendSource.ALL))
                    .describedAs("documented blind spot — closing it requires OI-R01")
                    .isFalse();
        }
    }
}
