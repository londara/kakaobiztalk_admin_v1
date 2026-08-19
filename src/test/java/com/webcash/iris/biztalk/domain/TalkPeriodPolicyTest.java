package com.webcash.iris.biztalk.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link TalkPeriodPolicy} 검증 — D-T24 의 회귀 테스트.
 * Verification for {@link TalkPeriodPolicy}: the regression test for D-T24.
 *
 * <p>레거시의 검증은 브라우저에서 시작·종료 시각을 초로 환산해 비교한 것이 전부였다. 계약은
 * {@code TRDD}/{@code START_TIME}/{@code END_TIME} 을 길이도 타입도 없이 선언했고 액션 JSP 는
 * 아무 검증도 하지 않았으므로, 서비스를 직접 호출하면 무엇이든 통과했다.</p>
 * <p>The legacy's validation was a seconds-of-day comparison in the browser. The contract declared the
 * three fields with neither length nor type and the action JSP validated nothing, so a direct service
 * call accepted anything.</p>
 *
 * // source: biztalk_admin_30.js — getDat(): endTime = "999999" when blank
 * // source: IDO.KKB_APITR_HSTR_L001 — WHERE TRDD = :TRDD AND RGDT BETWEEN :START_TIME AND :END_TIME
 * // req: FR-TLK-007, FR-TLK-008, FR-TLK-014, AMB-T02
 */
class TalkPeriodPolicyTest {

    @Nested
    @DisplayName("일자 범위 / date range")
    class DateRange {

        @Test
        @DisplayName("종료일자를 비우면 하루 조회다 — 레거시의 유일한 형태")
        void blankEndDateMeansOneDay() {
            // 레거시 술어는 TRDD = :TRDD 로 하루만 허용했다. 범위를 열되 기본값은 유지한다.
            // The legacy predicate TRDD = :TRDD allowed one day only; the range opens but the default
            // shape is preserved.
            TalkPeriodPolicy.TalkWindow window =
                    TalkPeriodPolicy.validate("20260819", null, null, null);

            assertThat(window.singleDay()).isTrue();
            assertThat(window.fromDateYyyymmdd()).isEqualTo("20260819");
            assertThat(window.toDateYyyymmdd()).isEqualTo("20260819");
        }

        @Test
        @DisplayName("31일은 허용하고 32일은 거부한다 — AMB-T02")
        void capIs31DaysInclusive() {
            assertThat(TalkPeriodPolicy.validate("20260801", "20260831", null, null)).isNotNull();

            assertThatThrownBy(() -> TalkPeriodPolicy.validate("20260801", "20260901", null, null))
                    .isInstanceOf(PeriodPolicy.InvalidPeriodException.class)
                    .hasMessageContaining("31");
        }

        @Test
        @DisplayName("역전된 일자는 거부된다")
        void invertedDatesRefused() {
            assertThatThrownBy(() -> TalkPeriodPolicy.validate("20260819", "20260818", null, null))
                    .isInstanceOf(PeriodPolicy.InvalidPeriodException.class);
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"00000000", "99999999", "20261332", "2026819", "notadate"})
        @DisplayName("형식이 잘못되거나 존재하지 않는 날짜는 거부된다 — D-T24")
        void malformedDatesRefused(String raw) {
            // 레거시는 START_DT=00000000&END_DT=99999999 를 그대로 바인딩해 테이블을 끝에서
            // 끝까지 훑었다. 8자리인지만 보지 않고 실재하는 날짜인지까지 확인한다.
            // The legacy bound START_DT=00000000&END_DT=99999999 verbatim and scanned end to end.
            // Being eight digits is not enough; the value must be a real calendar date.
            assertThatThrownBy(() -> TalkPeriodPolicy.validate(raw, raw, null, null))
                    .isInstanceOf(PeriodPolicy.InvalidPeriodException.class);
        }
    }

    @Nested
    @DisplayName("시각 경계 / time bounds")
    class TimeBounds {

        @Test
        @DisplayName("생략된 경계는 그 날의 처음과 끝이다 — FR-TLK-008")
        void omittedBoundsMeanWholeDay() {
            TalkPeriodPolicy.TalkWindow window =
                    TalkPeriodPolicy.validate("20260819", null, null, null);

            assertThat(window.fromTimestamp()).isEqualTo("20260819000000");
            assertThat(window.toTimestamp()).isEqualTo("20260819235959");
        }

        @Test
        @DisplayName("999999 센티널은 거부된다 — D-T24")
        void sentinel999999Refused() {
            // ⚠ 레거시가 종료시각을 비웠을 때 자바스크립트가 채운 값이다. 99시 99분 99초는
            // 시각이 아니며, 질의가 동작한 이유는 RGDT 가 문자 컬럼이어서 BETWEEN 이 사전순
            // 비교로 처리되었기 때문이다 — 컬럼 타입이 바뀌면 조용히 깨지는 암묵적 계약이었다.
            //
            // This is what the JavaScript sent when 종료시각 was blank. 99:99:99 is not a time, and the
            // query worked only because RGDT is a character column so BETWEEN compared lexically — an
            // implicit contract that breaks silently if the column type changes.
            assertThatThrownBy(() -> TalkPeriodPolicy.validate("20260819", null, null, "999999"))
                    .isInstanceOf(PeriodPolicy.InvalidPeriodException.class)
                    .hasMessageContaining("유효한 시각");
        }

        @Test
        @DisplayName("HHMM 네 자리도 받아들여 서버에서 정규화한다")
        void fourDigitTimeIsNormalised() {
            // 레거시 화면 기본값은 HHMM + "00" 으로 조립되었다. 네 자리 입력을 서버에서 채운다.
            // The legacy default was composed as HHMM plus "00"; four digits are padded server-side.
            TalkPeriodPolicy.TalkWindow window =
                    TalkPeriodPolicy.validate("20260819", null, "1124", "1129");

            assertThat(window.fromTimestamp()).isEqualTo("20260819112400");
            assertThat(window.toTimestamp()).isEqualTo("20260819112900");
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"11", "11245", "1124567", "11:24", "abcdef"})
        @DisplayName("형식이 잘못된 시각은 거부된다")
        void malformedTimesRefused(String raw) {
            assertThatThrownBy(() -> TalkPeriodPolicy.validate("20260819", null, raw, null))
                    .isInstanceOf(PeriodPolicy.InvalidPeriodException.class);
        }

        @Test
        @DisplayName("하루 조회에서 역전된 시각은 서버에서 거부된다 — FR-TLK-014")
        void invertedTimesRefusedOnSingleDay() {
            // 레거시는 이것을 브라우저에서만 검사했다. 서비스를 직접 호출하면 통과했다.
            // The legacy checked this in the browser only; a direct service call passed.
            assertThatThrownBy(() ->
                    TalkPeriodPolicy.validate("20260819", "20260819", "120000", "110000"))
                    .isInstanceOf(PeriodPolicy.InvalidPeriodException.class)
                    .hasMessageContaining("시작시각");
        }

        @Test
        @DisplayName("여러 날 조회에서는 시각 역전을 거부하지 않는다")
        void invertedTimesAllowedAcrossDays() {
            // 문자내역 D8 의 반대 실수를 피한다: 시각만 비교해 정상적인 여러 날 범위를 거부하면
            // 안 된다. 여러 날에서 09:00~08:00 은 각 날의 빈 구간을 뜻하며, 그것은 사용자의
            // 선택이지 오류가 아니다.
            // Avoiding the mirror image of 문자내역's D8: comparing times alone must not refuse a valid
            // multi-day range. Across days, 09:00–08:00 is an empty window per day — the user's choice,
            // not an error.
            TalkPeriodPolicy.TalkWindow window =
                    TalkPeriodPolicy.validate("20260818", "20260819", "120000", "110000");

            assertThat(window.singleDay()).isFalse();
            assertThat(window.fromTimestamp()).isEqualTo("20260818120000");
            assertThat(window.toTimestamp()).isEqualTo("20260819110000");
        }
    }

    @Nested
    @DisplayName("감사 설명 / audit description")
    class AuditDescription {

        @Test
        @DisplayName("구간을 감사 기록에 담을 수 있는 형태로 설명한다")
        void describesTheWindow() {
            assertThat(TalkPeriodPolicy.validate("20260819", null, "112400", "112959").describe())
                    .isEqualTo("20260819112400~20260819112959");
        }
    }
}
