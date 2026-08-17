package com.webcash.iris.biztalk.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link SenderNumberRef} 검증 — D-S1 회귀. / Verification for {@link SenderNumberRef} — D-S1.
 *
 * // req: FR-SND-007, FR-SNDD-002
 */
class SenderNumberRefTest {

    @Nested
    @DisplayName("왕복 / round-trip")
    class RoundTrip {

        @Test
        @DisplayName("토큰은 원래 값으로 복원된다 / a token restores to its original values")
        // req: FR-SND-007
        void roundTrips() {
            SenderNumberRef original = new SenderNumberRef("K0ABCD", "01012345678");
            assertThat(SenderNumberRef.fromToken(original.token())).isEqualTo(original);
        }

        @ParameterizedTest
        @ValueSource(strings = {"02123456", "050123456789", "15881234", "01012345678"})
        @DisplayName("길이가 다른 번호도 왕복한다 / round-trips numbers of differing length")
        // req: FR-SND-007
        void roundTripsVariousLengths(String number) {
            SenderNumberRef ref = new SenderNumberRef("K0ABCD", number);
            assertThat(SenderNumberRef.fromToken(ref.token()).number()).isEqualTo(number);
        }
    }

    @Nested
    @DisplayName("D-S1 핵심 성질 — 식별자와 표시값의 분리 / identity is not display")
    class IdentityIsNotDisplay {

        @Test
        @DisplayName("토큰은 표시되는 번호와 결코 같지 않다 / the token never equals the rendered number")
        // req: FR-SND-007
        void tokenNeverEqualsTheNumber() {
            // 이것이 D-S1 을 가능하게 했던 성질의 반대다. 레거시 그리드는 표시된 값을 그대로
            // 식별자로 삼았고, 그래서 표시 형식이 바뀌자 식별이 깨졌다.
            //
            // This is the inverse of the property that enabled D-S1: the legacy grid used the
            // displayed value as the identifier, so identification broke when display changed.
            SenderNumberRef ref = new SenderNumberRef("K0ABCD", "01012345678");
            assertThat(ref.token()).isNotEqualTo(ref.number());
        }

        @Test
        @DisplayName("표시 형식이 바뀌어도 식별자는 그대로다 / identity survives a display-format change")
        // req: FR-SND-007
        void identityIsStableAcrossDisplayChanges() {
            // 이 테스트가 원래 결함을 잡았을 테스트다. 2025-10 에 목록 조회가 마스킹을
            // 도입했을 때 삭제가 조용히 깨졌다. 식별자가 표시와 무관하다면 그런 변경은
            // 삭제 경로에 아무 영향도 주지 못한다.
            //
            // This is the test that would have caught the original defect. Deletion broke silently
            // when the list introduced masking in 2025-10. If identity is independent of display,
            // such a change cannot affect the delete path at all.
            String number = "01012345678";
            String maskedForDisplay = "01********8";

            SenderNumberRef ref = new SenderNumberRef("K0ABCD", number);

            assertThat(SenderNumberRef.fromToken(ref.token()).number())
                    .as("identity must resolve to the real number regardless of how it is shown")
                    .isEqualTo(number)
                    .isNotEqualTo(maskedForDisplay);
        }
    }

    @Nested
    @DisplayName("D-S1 회귀 방지 — 표시값 거절 / rejecting display values")
    class RejectsDisplayValues {

        @ParameterizedTest
        @ValueSource(strings = {"01********8", "0*", "02****78"})
        @DisplayName("마스킹된 값을 식별자로 인식하지 않는다 / a masked value is recognised as display")
        // req: FR-SND-007, FR-SNDD-002
        void detectsMaskedValues(String masked) {
            assertThat(SenderNumberRef.looksLikeDisplayValue(masked)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"01012345678,0212345678", "1,2,3"})
        @DisplayName("콤마로 이어붙인 목록을 인식한다 / a comma-joined list is recognised")
        // req: FR-SNDD-004
        void detectsCommaJoinedLists(String joined) {
            // D-S5: 레거시 삭제는 콤마로 이어붙인 전체 목록을 각 이력 행에 그대로 기록했다.
            // D-S5: the legacy delete wrote the whole comma-joined list into every history row.
            assertThat(SenderNumberRef.looksLikeDisplayValue(joined)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"01012345678", "15881234", "02123456"})
        @DisplayName("정상 번호는 표시값으로 오인하지 않는다 / a plain number is not flagged")
        // req: FR-SND-007
        void doesNotFlagPlainNumbers(String number) {
            assertThat(SenderNumberRef.looksLikeDisplayValue(number)).isFalse();
        }
    }

    @Nested
    @DisplayName("손상된 입력 / malformed input")
    class Malformed {

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        @DisplayName("빈 토큰은 예외다 / a blank token throws")
        // req: FR-SNDD-002
        void blankThrows(String token) {
            assertThatThrownBy(() -> SenderNumberRef.fromToken(token))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("null 토큰은 예외다 / a null token throws")
        // req: FR-SNDD-002
        void nullThrows() {
            assertThatThrownBy(() -> SenderNumberRef.fromToken(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("구분자가 없는 토큰은 예외다 / a token without the separator throws")
        // req: FR-SNDD-002
        void missingSeparatorThrows() {
            String noSeparator = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("K0ABCD01012345678".getBytes(java.nio.charset.StandardCharsets.UTF_8));

            assertThatThrownBy(() -> SenderNumberRef.fromToken(noSeparator))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Base64 가 아닌 토큰은 예외다 / a non-Base64 token throws")
        // req: FR-SNDD-002
        void nonBase64Throws() {
            assertThatThrownBy(() -> SenderNumberRef.fromToken("!!! not base64 !!!"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("손상된 토큰은 조용히 빈 결과가 되지 않는다 / a malformed token never degrades to an empty result")
        // req: FR-SNDD-002
        void neverDegradesToEmpty() {
            // 이 단정이 FR-SNDD-002 의 본질이다. 복원 실패가 예외가 아니라 빈 값이라면,
            // 삭제는 "일치하는 행 없음"으로 진행되어 0건을 지우고 성공을 보고한다 — D-S1.
            //
            // This assertion is the essence of FR-SNDD-002. If a failed restore produced a blank
            // rather than an exception, a delete would proceed as "no matching row", remove
            // nothing, and report success — D-S1.
            assertThatThrownBy(() -> SenderNumberRef.fromToken("Zm9v"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
