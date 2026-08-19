package com.webcash.iris.biztalk.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link ChannelCounters} 와 {@link ReportRow} 의 산술 검증.
 * Arithmetic verification for {@link ChannelCounters} and {@link ReportRow}.
 *
 * <p>D-R11(NULL 전파)과 D-R14(전체 ≠ 성공 + 실패)의 회귀 방지가 목적이다.</p>
 * <p>Guards the D-R11 (NULL propagation) and D-R14 (전체 ≠ 성공 + 실패) regressions.</p>
 *
 * // req: FR-RPT-010, FR-RPT-011, CONST-BIZ-R01, CONST-BIZ-R02
 */
class ChannelCountersTest {

    @Nested
    @DisplayName("NULL 처리 / NULL handling")
    class Nulls {

        /**
         * D-R11 회귀: 레거시 SQL 에는 COALESCE 가 한 군데도 없어, 컬럼 하나가 NULL 이면
         * 그 기관의 총계 전체가 NULL 이 되고 셀이 빈칸으로 보였다.
         * D-R11 regression: with no COALESCE anywhere, one NULL column nulled an institution's
         * entire total and the cell rendered blank.
         */
        // req: FR-RPT-011
        @Test
        @DisplayName("NULL 건수는 0 이 된다")
        void nullBecomesZero() {
            ChannelCounters counters = ChannelCounters.of(null, null, null, null);

            assertThat(counters).isEqualTo(ChannelCounters.ZERO);
            assertThat(counters.total()).isZero();
        }

        @Test
        @DisplayName("일부만 NULL 이어도 나머지는 보존된다")
        void partialNullsDoNotDestroyTheRest() {
            ChannelCounters counters = ChannelCounters.of(10L, null, 3L, null);

            assertThat(counters.total()).isEqualTo(10);
            assertThat(counters.success()).isZero();
            assertThat(counters.failed()).isEqualTo(3);
            assertThat(counters.inFlight()).isZero();
        }

        @Test
        @DisplayName("NULL 을 더해도 값이 사라지지 않는다")
        void plusToleratesNull() {
            ChannelCounters counters = new ChannelCounters(4, 3, 1, 0);

            assertThat(counters.plus(null)).isEqualTo(counters);
        }
    }

    @Nested
    @DisplayName("산술 항등식 / the arithmetic identity")
    class Identity {

        /**
         * D-R14 회귀: 레거시는 처리중을 조회하고도 표시하지 않아 이 등식이 성립하지 않았다.
         * D-R14 regression: the legacy queried the in-flight count and never displayed it, so
         * this identity did not hold.
         */
        // req: FR-RPT-010, AMB-R02
        @Test
        @DisplayName("전체 = 성공 + 실패 + 처리중 이면 성립한다")
        void identityHoldsWhenAllFourAgree() {
            assertThat(new ChannelCounters(10, 6, 3, 1).reconciles()).isTrue();
        }

        @Test
        @DisplayName("처리중을 빼면 등식이 깨진다 — 레거시가 보여주던 상태")
        void identityFailsWhenInFlightIsIgnored() {
            // 레거시 그리드가 실제로 보여주던 세 값: 10 / 6 / 3. 사용자가 더해 보면 1 이 빈다.
            // The three values the legacy grid actually showed: 10 / 6 / 3. Add them up and one
            // is missing.
            assertThat(new ChannelCounters(10, 6, 3, 0).reconciles()).isFalse();
        }

        /**
         * 친구톡 일반 이미지는 {@code FTIMG − FTIMGWI} 이므로 음수가 될 수 있다 — 배치가 만든
         * 데이터가 스스로 모순된다는 신호다.
         * The normal-image figure is {@code FTIMG − FTIMGWI} and so can go negative, signalling
         * that the batch's own data contradicts itself.
         */
        // req: FR-RPT-010, CONST-BIZ-R01
        @Test
        @DisplayName("음수 건수를 감지한다")
        void detectsNegatives() {
            assertThat(new ChannelCounters(-1, 0, 0, 0).hasNegative()).isTrue();
            assertThat(new ChannelCounters(5, 5, 0, 0).hasNegative()).isFalse();
        }
    }

    @Nested
    @DisplayName("행 단위 검증 / row-level checks")
    class Rows {

        private static ReportRow rowWith(ChannelCounters alimtalk) {
            java.util.EnumMap<MessageChannel, ChannelCounters> counters =
                    new java.util.EnumMap<>(MessageChannel.class);
            for (MessageChannel channel : MessageChannel.values()) {
                counters.put(channel, ChannelCounters.ZERO);
            }
            counters.put(MessageChannel.ALIMTALK, alimtalk);
            return new ReportRow(SendSource.ALL, "20260701", "K0001", "기관", counters);
        }

        // req: FR-RPT-010
        @Test
        @DisplayName("항등식을 위반한 채널을 지목한다")
        void namesTheOffendingChannel() {
            ReportRow row = rowWith(new ChannelCounters(10, 6, 3, 0));

            assertThat(row.reconciliationFailures()).containsExactly(MessageChannel.ALIMTALK);
        }

        @Test
        @DisplayName("모두 성립하면 위반이 없다")
        void noFailuresWhenEverythingReconciles() {
            assertThat(rowWith(new ChannelCounters(10, 6, 3, 1)).reconciliationFailures()).isEmpty();
        }

        /**
         * 총 건수는 채널을 <b>한 번씩만</b> 더한다. 레거시 SQL 은 저장된 {@code FT_CNT} 를
         * 쓰고 그 하위 채널을 다시 더하지 않았다 — 여기서도 이중 계상하지 않는다.
         * The grand total adds each channel <b>once</b>. The legacy SQL used the stored
         * {@code FT_CNT} without re-adding its components, and neither does this.
         */
        // req: CONST-BIZ-R02
        @Test
        @DisplayName("총 건수는 채널을 이중 계상하지 않는다")
        void grandTotalDoesNotDoubleCount() {
            java.util.EnumMap<MessageChannel, ChannelCounters> counters =
                    new java.util.EnumMap<>(MessageChannel.class);
            for (MessageChannel channel : MessageChannel.values()) {
                counters.put(channel, new ChannelCounters(10, 10, 0, 0));
            }
            ReportRow row = new ReportRow(SendSource.API, "20260701", "K0001", "기관", counters);

            // 7 채널 × 10 = 70. 친구톡 하위 채널을 다시 더했다면 이보다 커진다.
            // Seven channels x 10 = 70; re-adding the friend-talk components would exceed it.
            assertThat(row.grandTotal()).isEqualTo(70);
        }

        // req: FR-RPT-012
        @Test
        @DisplayName("기관명이 비면 미해결로 표시한다")
        void marksAnUnresolvedInstitution() {
            java.util.EnumMap<MessageChannel, ChannelCounters> counters =
                    new java.util.EnumMap<>(MessageChannel.class);
            counters.put(MessageChannel.ALIMTALK, ChannelCounters.ZERO);

            assertThat(new ReportRow(SendSource.API, "20260701", "K0001", null, counters)
                    .institutionUnresolved()).isTrue();
            assertThat(new ReportRow(SendSource.API, "20260701", "K0001", "  ", counters)
                    .institutionUnresolved()).isTrue();
            assertThat(new ReportRow(SendSource.API, "20260701", "K0001", "기관", counters)
                    .institutionUnresolved()).isFalse();
        }
    }
}
