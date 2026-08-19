package com.webcash.iris.biztalk.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link PeriodPolicy} 검증. / Verification for {@link PeriodPolicy}.
 *
 * <p>이 클래스의 시험 대부분은 D-R9 회귀 방지다. 레거시의 유일한 검사는 브라우저의
 * {@code Number(startDt) > Number(endDt)} 한 줄이었고, 서비스 계약은 두 일자를 길이도 타입도
 * 없이 선언했으며 액션 JSP 는 아무것도 검증하지 않았다.</p>
 * <p>Most of these are D-R9 regression guards. The legacy's only check was one line of browser
 * JavaScript; the contract declared both dates with neither length nor type, and the action JSP
 * validated nothing.</p>
 *
 * // req: FR-RPT-002, FR-RPT-003, FR-RPT-004
 */
class PeriodPolicyTest {

    @Nested
    @DisplayName("형식 검증 / format validation")
    class Format {

        @Test
        @DisplayName("유효한 YYYYMMDD 쌍을 통과시킨다")
        void acceptsValidPair() {
            PeriodPolicy.ReportPeriod period = PeriodPolicy.validate("20260701", "20260731");

            assertThat(period.fromYyyymmdd()).isEqualTo("20260701");
            assertThat(period.toYyyymmdd()).isEqualTo("20260731");
            assertThat(period.spanDays()).isEqualTo(31);
        }

        /**
         * D-R9 회귀: 8자리 숫자지만 존재하지 않는 날짜.
         * D-R9 regression: eight digits that are not a date.
         */
        // req: FR-RPT-004
        @ParameterizedTest(name = "[{index}] {0} 은 달력 일자가 아니다")
        @ValueSource(strings = {"20261332", "20260230", "20260000", "00000000", "99999999"})
        void rejectsNonCalendarDates(String value) {
            assertThatThrownBy(() -> PeriodPolicy.validate(value, "20260731"))
                    .isInstanceOf(PeriodPolicy.InvalidPeriodException.class);
        }

        /**
         * D-R9 회귀: 계약이 길이를 선언하지 않아 어떤 문자열이든 바인딩되었다.
         * D-R9 regression: the contract declared no length, so any string bound.
         */
        // req: FR-RPT-004
        @ParameterizedTest(name = "[{index}] {0} 은 8자리가 아니다")
        @ValueSource(strings = {"2026070", "202607011", "2026-07-01", "abcdefgh", " "})
        void rejectsWrongLengthOrShape(String value) {
            assertThatThrownBy(() -> PeriodPolicy.validate(value, "20260731"))
                    .isInstanceOf(PeriodPolicy.InvalidPeriodException.class);
        }

        @Test
        @DisplayName("null 은 거부된다")
        void rejectsNull() {
            assertThatThrownBy(() -> PeriodPolicy.validate(null, "20260731"))
                    .isInstanceOf(PeriodPolicy.InvalidPeriodException.class);
            assertThatThrownBy(() -> PeriodPolicy.validate("20260701", null))
                    .isInstanceOf(PeriodPolicy.InvalidPeriodException.class);
        }
    }

    @Nested
    @DisplayName("순서와 기간 / ordering and span")
    class Span {

        /**
         * D-R9 회귀: 브라우저에서만 검사하던 순서 규칙을 서버가 강제한다.
         * D-R9 regression: the ordering rule the browser alone enforced is now server-side.
         */
        // req: FR-RPT-003
        @Test
        @DisplayName("시작일자가 종료일자보다 늦으면 거부한다")
        void rejectsInvertedRange() {
            assertThatThrownBy(() -> PeriodPolicy.validate("20260801", "20260731"))
                    .isInstanceOf(PeriodPolicy.InvalidPeriodException.class)
                    .hasMessageContaining("종료일자");
        }

        @Test
        @DisplayName("같은 날 하루 조회는 허용된다")
        void acceptsSingleDay() {
            assertThat(PeriodPolicy.validate("20260701", "20260701").spanDays()).isEqualTo(1);
        }

        /**
         * PM 결정 AMB-R03 의 경계. 366 은 허용, 367 은 거부.
         * The AMB-R03 boundary: 366 allowed, 367 refused.
         */
        // req: FR-RPT-002
        @Test
        @DisplayName("366일은 허용하고 367일은 거부한다")
        void enforcesTheCapAtItsBoundary() {
            // 2026-01-01 .. 2026-12-31 은 양 끝 포함 365일 — 여기에 하루를 더해 366 을 만든다.
            // 2026-01-01..2026-12-31 is 365 inclusive; one more day makes 366.
            assertThatCode(() -> PeriodPolicy.validate("20260101", "20270101"))
                    .doesNotThrowAnyException();

            assertThatThrownBy(() -> PeriodPolicy.validate("20260101", "20270102"))
                    .isInstanceOf(PeriodPolicy.InvalidPeriodException.class)
                    .hasMessageContaining("366");
        }

        /**
         * D-R9 회귀: 이 한 줄이 두 테이블을 끝에서 끝까지 훑던 요청이다.
         * D-R9 regression: this single request scanned both tables end to end.
         */
        // req: FR-RPT-002, T-R16
        @Test
        @DisplayName("레거시의 전체 스캔 요청을 거부한다")
        void rejectsTheLegacyFullScanRequest() {
            assertThatThrownBy(() -> PeriodPolicy.validate("00000000", "99999999"))
                    .isInstanceOf(PeriodPolicy.InvalidPeriodException.class);
        }
    }
}
