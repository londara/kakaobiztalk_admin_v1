package com.webcash.iris.biztalk.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.webcash.iris.biztalk.domain.SenderNumberValidator.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link SenderNumberValidator} 검증. / Verification for {@link SenderNumberValidator}.
 *
 * <p>이 테스트는 DB 를 필요로 하지 않는다 — 순수 규칙이다. Docker 사용이 허용되지 않아
 * DB 의존 검증이 tier 3 으로 내려간 환경에서(RISK-S13), 이런 순수 로직만이 온전한 자동
 * 검증을 받을 수 있다.</p>
 * <p>Needs no database — these are pure rules. In an environment where Docker is not permitted
 * and DB-dependent verification has dropped to tier 3 (RISK-S13), pure logic like this is the
 * part that can still be verified properly.</p>
 *
 * // req: FR-SNDC-005, FR-SNDC-006, FR-SNDC-010
 */
class SenderNumberValidatorTest {

    @Nested
    @DisplayName("D-S13 회귀 — 숫자만 허용 / digits only")
    class Numeric {

        @ParameterizedTest
        @ValueSource(strings = {"abcdefgh", "0101234a678", "010-1234-5678", "010 1234 5678", "０１０１２３４５６７８"})
        @DisplayName("숫자가 아닌 문자가 있으면 거절한다 / rejects anything containing a non-digit")
        // req: FR-SNDC-005
        void rejectsNonNumeric(String input) {
            // 레거시 isValidDpNo() 는 길이와 접두어만 보았으므로 abcdefgh 가 통과했다.
            // The legacy isValidDpNo() checked only length and prefix, so abcdefgh passed.
            assertThat(SenderNumberValidator.validate(input)).isEqualTo(Result.NOT_NUMERIC);
        }

        @Test
        @DisplayName("전각 숫자는 숫자가 아니다 / full-width digits are not digits")
        // req: FR-SNDC-005
        void rejectsFullWidthDigits() {
            // Character.isDigit 는 전각 숫자에 true 를 반환하므로 별도로 확인한다.
            // Character.isDigit returns true for full-width digits, so this is asserted explicitly.
            Result result = SenderNumberValidator.validate("０１０１２３４５６７８");
            assertThat(result).isNotEqualTo(Result.VALID);
        }
    }

    @Nested
    @DisplayName("D-S12 회귀 — 특수·긴급번호 / special and emergency numbers")
    class Barred {

        @ParameterizedTest
        @ValueSource(strings = {"112", "113", "114", "117", "118", "119", "1335", "1339", "182"})
        @DisplayName("특수번호는 거절한다 / rejects special numbers")
        // req: FR-SNDC-006
        void rejectsBarredNumbers(String input) {
            assertThat(SenderNumberValidator.validate(input)).isEqualTo(Result.BARRED);
        }

        @Test
        @DisplayName("1335 는 길이 규칙으로 걸러지지 않는다 / 1335 is not caught by the length rule")
        // req: FR-SNDC-006
        void barredCheckIsNotRedundantWithLength() {
            // 이 단정이 D-S12 의 핵심이다. 112 는 8자리 미만이라 우연히 거절되지만 1335 는
            // 4자리로 역시 8자리 미만 — 즉 길이 규칙이 특수번호를 "대체로" 막는 것처럼 보인다.
            // 그러나 그것은 우연이며, 8자리 이상인 특수번호가 목록에 추가되면 즉시 깨진다.
            // 검사가 BARRED 를 반환하는지 확인해 두면 그 의도가 코드에 남는다.
            //
            // This assertion is the point of D-S12. Both 112 and 1335 are under 8 digits, so the
            // length rule appears to cover special numbers — but only incidentally, and that
            // breaks the moment a barred number of 8+ digits is added. Asserting BARRED rather
            // than "rejected" keeps the intent in the code.
            assertThat(SenderNumberValidator.validate("1335")).isEqualTo(Result.BARRED);
            assertThat(SenderNumberValidator.validate("112")).isEqualTo(Result.BARRED);
        }
    }

    @Nested
    @DisplayName("FR-SNDC-010 — 길이 규칙 / length rules")
    class Length {

        @ParameterizedTest
        @CsvSource({
                // 일반 번호 / ordinary numbers
                "0212345678,  VALID",                  // 10 digits
                "01012345678, VALID",                  // 11 digits
                "021234567,   VALID",                  // 9 digits
                "02123456,    VALID",                  // 8 digits, minimum
                "0212345,     TOO_SHORT",              // 7 digits
                "021234567890, TOO_LONG",              // 12 digits, not 030/050
                // 030 · 050 — 12자리까지 / up to 12
                "050123456789, VALID",                 // 12 digits
                "0301234567,   VALID",                 // 10 digits
                "0501234567890, TOO_LONG",             // 13 digits
                // 15xx · 16xx — 정확히 8자리 / exactly 8
                "15881234, VALID",
                "16881234, VALID",
                "158812345, BAD_REPRESENTATIVE_LENGTH",  // 9 digits
                "1588123,   TOO_SHORT"                   // 7 digits — length floor applies first
        })
        @DisplayName("접두어별 길이 한도를 적용한다 / applies the per-prefix length limit")
        // req: FR-SNDC-010
        void appliesLengthRules(String input, Result expected) {
            assertThat(SenderNumberValidator.validate(input)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("입력 없음 / absent input")
    class Required {

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        @DisplayName("빈 값은 거절한다 / rejects blank input")
        // req: FR-SNDC-003
        void rejectsBlank(String input) {
            assertThat(SenderNumberValidator.validate(input)).isEqualTo(Result.REQUIRED);
        }

        @Test
        @DisplayName("null 은 거절한다 / rejects null")
        // req: FR-SNDC-003
        void rejectsNull() {
            // D-S11 회귀: 레거시 클라이언트 검사는 존재하지 않는 요소를 읽어 undefined 를 얻었고
            // undefined == "" 가 거짓이라 통과했다. 서버는 그런 여지를 남기지 않는다.
            // D-S11 regression: the legacy client read a missing element, got undefined, and
            // undefined == "" is false so the check passed. The server leaves no such gap.
            assertThat(SenderNumberValidator.validate(null)).isEqualTo(Result.REQUIRED);
        }

        @Test
        @DisplayName("앞뒤 공백은 제거한 뒤 판정한다 / trims before judging")
        // req: FR-SNDC-003
        void trimsBeforeValidating() {
            assertThat(SenderNumberValidator.validate("  0212345678  ")).isEqualTo(Result.VALID);
        }
    }

    @Nested
    @DisplayName("NFR-USE-D02 — 결과 메시지 / outcome messages")
    class Messages {

        @Test
        @DisplayName("모든 결과가 고유한 메시지를 갖는다 / every outcome carries its own message")
        // req: NFR-USE-D02
        void everyResultHasADistinctMessage() {
            // 레거시는 어떤 규칙을 어겼든 "등록중 오류 발생." 하나만 보여 주었다.
            // The legacy showed one sentence regardless of which rule was broken.
            long distinct = java.util.Arrays.stream(Result.values())
                    .map(Result::message)
                    .distinct()
                    .count();
            assertThat(distinct).isEqualTo(Result.values().length);
        }

        @Test
        @DisplayName("accepted() 는 VALID 에서만 참이다 / accepted() is true only for VALID")
        // req: FR-SNDC-003
        void acceptedOnlyForValid() {
            for (Result result : Result.values()) {
                assertThat(result.accepted()).isEqualTo(result == Result.VALID);
            }
        }
    }
}
