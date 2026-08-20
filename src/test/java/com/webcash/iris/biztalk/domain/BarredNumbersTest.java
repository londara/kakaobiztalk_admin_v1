package com.webcash.iris.biztalk.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * {@link BarredNumbers} 검증. / Verification for {@link BarredNumbers}.
 *
 * <p>이 클래스의 요점은 목록을 담는 것이 아니라 <b>목록이 없을 때 어떻게 되는지</b>다. 규칙을
 * 코드에서 데이터로 옮기면 "규칙이 사라질 수 있다" 는 새 실패 방식이 생기고, 그 실패가 조용하면
 * 그것이 바로 D-S12 다 — 화면은 특수번호를 막는다고 고지하는데 어느 계층도 막지 않는 상태.</p>
 * <p>The point of this class is not that it holds a list but <b>what happens when there is none</b>.
 * Moving a rule from code to data creates a new failure mode — the rule can go missing — and if that
 * failure is quiet it is D-S12 itself: the screen states that special numbers are barred while no
 * layer bars them.</p>
 *
 * // req: FR-SNDC-006, CONST-BIZ-D03, ADR-SND-021
 */
class BarredNumbersTest {

    @Nested
    @DisplayName("배포되는 목록 / the list that ships")
    class Bundled {

        @Test
        @DisplayName("PM 결정이 이름을 든 네 값을 담는다 / carries the four values the ruling names")
        // req: FR-SNDC-006, AMB-S06
        void carriesTheRuledNumbers() {
            BarredNumbers barred = BarredNumbers.bundled();
            assertThat(barred.contains("112")).isTrue();
            assertThat(barred.contains("114")).isTrue();
            assertThat(barred.contains("119")).isTrue();
            assertThat(barred.contains("1335")).isTrue();
        }

        @Test
        @DisplayName("S1 이 하드코딩했던 14개를 모두 담는다 / carries all 14 values S1 hardcoded")
        // req: FR-SNDC-006
        void carriesEveryValueTheHardcodedSetHad() {
            // Sprint S1 의 SenderNumberValidator.BARRED_NUMBERS 목록이다. 배포 자산으로 옮기면서
            // 값이 빠지지 않았는지 확인한다 — 이관에서 한 줄이 사라지면 그 번호가 등록 가능해지고,
            // 다른 어떤 시험도 그것을 알아채지 못한다.
            // The Sprint S1 hardcoded set. Asserted so the move to a deployment asset cannot have
            // dropped a value: one missing line makes that number registrable and no other test
            // would notice.
            BarredNumbers barred = BarredNumbers.bundled();
            for (String number : new String[]{
                    "112", "113", "114", "117", "118", "119", "120",
                    "125", "128", "129", "132", "182", "1335", "1339"}) {
                assertThat(barred.contains(number))
                        .as("%s must remain barred after the move to configuration", number)
                        .isTrue();
            }
        }

        @Test
        @DisplayName("보통 번호는 담지 않는다 / an ordinary number is not barred")
        // req: FR-SNDC-006
        void doesNotBarOrdinaryNumbers() {
            assertThat(BarredNumbers.bundled().contains("0212345678")).isFalse();
        }

        @Test
        @DisplayName("집합은 변경할 수 없다 / the set is unmodifiable")
        // req: CONST-BIZ-D03
        void setIsUnmodifiable() {
            assertThatThrownBy(() -> BarredNumbers.bundled().values().add("0212345678"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("설정이 지울 수 없는 값 / values configuration cannot remove")
    class Mandatory {

        @Test
        @DisplayName("파일에 없어도 차단은 유지된다 / still barred when absent from the file")
        // req: FR-SNDC-006, AMB-S06
        void mandatoryValuesSurviveAnEditThatRemovesThem() {
            // ADR-SND-021 은 이 성질을 시험으로 보장하기로 했다. 구조로 보장할 수 있으면 그편이
            // 낫다 — 설정 편집 한 번으로 119 가 발신번호가 될 수 있는 상태를 시험이 사후에 잡는
            // 것보다, 애초에 표현 불가능한 것이 낫다.
            // ADR-SND-021 proposed guaranteeing this by test; guaranteeing it structurally is better.
            // A configuration edit that makes 119 registrable is better made unrepresentable than
            // caught afterwards.
            BarredNumbers barred = BarredNumbers.of("0212345678\n", "test");

            assertThat(barred.contains("112")).isTrue();
            assertThat(barred.contains("114")).isTrue();
            assertThat(barred.contains("119")).isTrue();
            assertThat(barred.contains("1335")).isTrue();
        }
    }

    @Nested
    @DisplayName("요란한 실패 / loud failure")
    class LoudFailure {

        @Test
        @DisplayName("빈 목록은 예외다 / an empty list throws")
        // req: CONST-BIZ-D03
        void emptyListThrows() {
            // 조용히 빈 집합으로 기동하면 규칙 없는 상태가 정상처럼 보인다 — D-S12 그대로다.
            // Booting quietly with an empty set makes ruleless operation look normal: D-S12 exactly.
            assertThatThrownBy(() -> BarredNumbers.of("", "test"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("empty");
        }

        @Test
        @DisplayName("주석만 있는 목록도 빈 목록이다 / a comment-only list is an empty list")
        // req: CONST-BIZ-D03
        void commentOnlyListThrows() {
            assertThatThrownBy(() -> BarredNumbers.of("# 전부 주석\n# nothing else\n", "test"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("empty");
        }

        @Test
        @DisplayName("숫자가 아닌 줄은 예외다 / a non-numeric line throws")
        // req: CONST-BIZ-D03
        void nonNumericLineThrows() {
            // 건너뛰면 오타 하나가 조용히 한 번호의 차단을 해제하고, 그 상태는 정상 기동과
            // 구분되지 않는다.
            // Skipping it would let one typo quietly un-bar a number, in a state indistinguishable
            // from a healthy boot.
            assertThatThrownBy(() -> BarredNumbers.of("112\n11O\n", "test"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("line 2");
        }

        @Test
        @DisplayName("자원이 없으면 예외다 / a missing resource throws")
        // req: CONST-BIZ-D03
        void missingResourceThrows() {
            assertThatThrownBy(() -> new BarredNumbers(
                    new DefaultResourceLoader(), "classpath:senderno/does-not-exist.txt"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not found");
        }
    }

    @Nested
    @DisplayName("형식 / format")
    class Format {

        @Test
        @DisplayName("줄 끝 주석과 공백을 걷어낸다 / strips trailing comments and whitespace")
        // req: CONST-BIZ-D03
        void stripsCommentsAndWhitespace() {
            BarredNumbers barred = BarredNumbers.of("""
                    # 머리말 / header
                    0212345678    # 설명 / a comment

                       0312345678
                    """, "test");

            assertThat(barred.contains("0212345678")).isTrue();
            assertThat(barred.contains("0312345678")).isTrue();
        }

        @Test
        @DisplayName("출처를 기억한다 — 값이 아니라 / remembers its origin, not its values")
        // req: CONST-BIZ-D03
        void remembersOrigin() {
            // 기동 로그에 남기는 것은 출처와 건수다. 어떤 목록으로 기동했는지는 운영 질문이며,
            // 목록 자체를 로그에 쏟을 이유는 없다.
            // A boot log records the origin and the count: which list an instance started with is an
            // operational question; dumping the list is not needed to answer it.
            assertThat(BarredNumbers.of("112\n", "somewhere.txt").origin()).isEqualTo("somewhere.txt");
        }
    }
}
