package com.webcash.iris.biztalk.alimtalk.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link TemplateMatcher} 검증. / Verification for {@link TemplateMatcher}.
 *
 * <p><b>QA 주의 — 이 테스트 일부는 의도적으로 레거시와 다른 결과를 단언한다.</b> PM 은
 * AMB-A00b 로 "고친다"를 결정했으므로, 레거시가 <b>거절했던</b> 입력이 이제 통과한다.
 * {@code correctedBehaviour} 그룹의 단언은 parity 회귀가 아니며, 레거시 결과로 되돌리는 방식으로
 * "고쳐서는" 안 된다.</p>
 * <p><b>Note for QA — some of these tests deliberately assert a different result from the legacy.</b>
 * PM ruled "correct it" (AMB-A00b), so inputs the legacy <b>rejected</b> now pass. The assertions in
 * {@code correctedBehaviour} are not parity regressions and must not be "fixed" by restoring the old
 * result.</p>
 *
 * // source: biztalk_admin_61.js:1041-1150 — validateTemplateStrict()
 * // req: FR-ATV-004, FR-ATV-005, FR-ATV-006, FR-ATV-008
 */
class TemplateMatcherTest {

    @Nested
    @DisplayName("D-A6 — 고쳐진 동작 / corrected behaviour")
    class CorrectedBehaviour {

        @Test
        @DisplayName("TC-A004-02: 변수 값이 다음 고정 문자를 포함해도 일치한다 / value containing the next literal")
        // req: FR-ATV-004
        void valueContainingNextLiteralMatches() {
            // 레거시는 이 조합을 공백에서 불일치로 보고했다. 스캔이 idx=1 의 '님' 에서 멈춰
            // '님 안녕' 과 '님철수님 안녕' 을 비교했기 때문이다.
            // The legacy reported a mismatch at the space: the scan halted on the '님' at index 1 and
            // compared '님 안녕' against '님철수님 안녕'.
            TemplateMatchResult result = TemplateMatcher.compile("#{name}님 안녕").match("김님철수님 안녕");

            assertThat(result.matched()).isTrue();
            assertThat(result.variableValues()).containsEntry("name", "김님철수");
        }

        @Test
        @DisplayName("TC-A004-03: 값이 구분자를 포함해도 일치한다 / value containing the delimiter")
        // req: FR-ATV-004
        void valueContainingDelimiterMatches() {
            TemplateMatchResult result = TemplateMatcher.compile("#{a}-#{b}-끝").match("1-2-3-4-끝");

            assertThat(result.matched()).isTrue();
            // 최소 일치이므로 a 가 가장 짧게 잡힌다. 모호함은 이 설계가 만든 것이 아니라
            // 템플릿 자체에 내재한다 — 어떤 알고리즘도 저자의 의도를 복원할 수 없다.
            // Lazy matching takes the shortest a. The ambiguity is inherent to the template rather than
            // introduced here: no algorithm can recover the author's intent.
            assertThat(result.variableValues()).containsEntry("a", "1");
        }

        @ParameterizedTest
        @CsvSource({
                "'#{금액}원이 결제되었습니다.','50,000원이 결제되었습니다.'",
                "'#{고객명}님의 #{상품}이 발송되었습니다.','김이가님의 이가은는이 발송되었습니다.'",
                "'[#{기관}] #{내용}','[○○은행] 이용 안내'"
        })
        @DisplayName("조사·특수문자가 섞인 실제 사례 / realistic Korean cases with particles")
        // req: FR-ATV-004
        void realisticKoreanCases(String template, String content) {
            assertThat(TemplateMatcher.compile(template).match(content).matched()).isTrue();
        }
    }

    @Nested
    @DisplayName("진짜 불일치는 여전히 잡는다 / genuine mismatches are still caught")
    class GenuineMismatch {

        @Test
        @DisplayName("TC-A004-04: 고정 문구가 다르면 불일치 / differing literal text")
        // req: FR-ATV-002
        void differingLiteralIsMismatch() {
            TemplateMatchResult result = TemplateMatcher.compile("#{a}원 결제").match("50,000원 취소");

            assertThat(result.matched()).isFalse();
            assertThat(result.divergences()).isNotEmpty();
            assertThat(result.firstDivergence().templatePart()).contains("원 결제");
        }

        @Test
        @DisplayName("TC-A004-07: 뒤에 여분의 내용이 있으면 불일치 / extra trailing content")
        // req: FR-ATV-002
        void extraTrailingContentIsMismatch() {
            TemplateMatchResult result = TemplateMatcher.compile("#{a}원").match("50,000원입니다");

            assertThat(result.matched()).isFalse();
            assertThat(result.firstDivergence().reason()).contains("여분");
        }

        @Test
        @DisplayName("TC-A004-08: 내용이 잘렸으면 불일치 / truncated content")
        // req: FR-ATV-002
        void truncatedContentIsMismatch() {
            TemplateMatchResult result = TemplateMatcher.compile("#{a}원 결제되었습니다").match("50,000원 결제");

            assertThat(result.matched()).isFalse();
            assertThat(result.divergences()).isNotEmpty();
        }

        @Test
        @DisplayName("TC-A004-05: 빈 치환은 불일치 / an empty substitution is a mismatch")
        // req: FR-ATV-005
        void emptySubstitutionIsMismatch() {
            // 레거시는 #{a}b 와 b 를 일치로 보았다 — 변수가 아무것도 소비하지 않아도 되었다.
            // The legacy matched #{a}b against b: a variable was allowed to consume nothing.
            assertThat(TemplateMatcher.compile("#{a}b").match("b").matched()).isFalse();
        }

        @Test
        @DisplayName("TC-A004-06: 모든 불일치를 보고한다 / reports divergences rather than only the first")
        // req: FR-ATV-006
        void reportsDivergences() {
            TemplateMatchResult result = TemplateMatcher.compile("가: #{a} / 나: #{b} / 끝")
                    .match("가: 1 / 다: 2 / 끝");

            assertThat(result.matched()).isFalse();
            assertThat(result.divergences()).isNotEmpty();
            assertThat(result.firstDivergence().position()).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("T-A7 — 정규식 주입 / regex injection")
    class RegexInjection {

        @Test
        @DisplayName("템플릿의 정규식 메타문자는 문자 그대로 취급된다 / metacharacters are literal")
        // req: FR-ATV-004, NFR-SEC-INJ-A01
        void metacharactersAreLiteral() {
            // 템플릿 본문은 운영자가 작성한 텍스트다. Pattern.quote 가 없으면 이 문자열이
            // 패턴의 의미를 바꾼다 — 검증을 서버로 옮기며 새로 생긴 표면(T-A7).
            // Template bodies are operator-authored. Without Pattern.quote this string would change the
            // pattern's meaning — a surface created by moving validation server-side (T-A7).
            String template = "가격 (1+1) 행사? [특가] 50%*";

            assertThat(TemplateMatcher.compile(template).match(template).matched()).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {".*", "^$", "(?:x)", "a{2,3}", "[a-z]+", "\\d"})
        @DisplayName("메타문자만으로 된 템플릿도 자기 자신과만 일치한다 / matches only itself")
        // req: NFR-SEC-INJ-A01
        void metacharacterOnlyTemplateMatchesOnlyItself(String template) {
            assertThat(TemplateMatcher.compile(template).match(template).matched()).isTrue();
            assertThat(TemplateMatcher.compile(template).match("xyz").matched()).isFalse();
        }
    }

    @Nested
    @DisplayName("T-A20 — 역추적 경계 / backtracking bounds")
    class Backtracking {

        @Test
        @Timeout(value = 5)
        @DisplayName("인접 변수 20개 × 4KB 비일치 내용이 시간 안에 끝난다 / bounded on a pathological input")
        // req: FR-ATV-004
        void pathologicalInputIsBounded() {
            // 인접 변수는 컴파일 단계에서 거절되므로 병리적 패턴 자체가 만들어지지 않는다.
            // 이것이 상한을 두는 것보다 강한 보장이다.
            // Adjacent variables are rejected at compile time, so the pathological pattern is never
            // built. That is a stronger guarantee than bounding its execution.
            StringBuilder adjacent = new StringBuilder();
            for (int i = 0; i < 20; i++) {
                adjacent.append("#{v").append(i).append('}');
            }
            assertThatThrownBy(() -> TemplateMatcher.compile(adjacent.toString()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("adjacent variables");
        }

        @Test
        @Timeout(value = 10)
        @DisplayName("고정 문구로 분리된 변수 20개 × 4KB 도 시간 안에 끝난다 / separated variables stay bounded")
        // req: FR-ATV-004, NFR-PERF-A01
        void separatedVariablesStayBounded() {
            StringBuilder template = new StringBuilder();
            for (int i = 0; i < 20; i++) {
                template.append("#{v").append(i).append("}|");
            }
            String content = "x".repeat(4096);

            TemplateMatchResult result = TemplateMatcher.compile(template.toString()).match(content);

            assertThat(result.matched()).isFalse();
        }

        @Test
        @DisplayName("변수 수 상한을 넘으면 컴파일이 거절된다 / the variable cap is enforced")
        // req: FR-ATV-004
        void variableCapIsEnforced() {
            StringBuilder template = new StringBuilder();
            for (int i = 0; i <= TemplateMatcher.MAX_VARIABLES; i++) {
                template.append("#{v").append(i).append("}-");
            }
            assertThatThrownBy(() -> TemplateMatcher.compile(template.toString()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exceeding the cap");
        }
    }

    @Nested
    @DisplayName("FR-ATV-008 — 변수 구문 / variable syntax")
    class Syntax {

        @Test
        @DisplayName("TC-A004-10: ${...} 는 거절하고 무엇을 써야 하는지 알려준다 / rejects ${...} explicitly")
        // req: FR-ATV-008
        void rejectsDollarSyntax() {
            // 레거시는 ${...} 를 받아들였다. 카카오는 #{...} 만 쓰므로 로컬에서 통과하고
            // 벤더에서 거절되는 템플릿이 만들어졌다 — 통과시키는 쪽이 더 위험하다.
            // The legacy accepted ${...}. Only #{...} is Kakao's, so such a template passed locally and
            // was rejected by the vendor: accepting it is the more dangerous behaviour.
            assertThatThrownBy(() -> TemplateMatcher.compile("${name}님 안녕"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("#{...}");
        }

        @Test
        @DisplayName("TC-A004-09: 변수가 없는 템플릿은 정확 비교다 / a variable-free template is an exact match")
        // req: FR-ATV-004
        void variableFreeTemplateIsExact() {
            String template = "이용해 주셔서 감사합니다.";

            assertThat(TemplateMatcher.compile(template).match(template).matched()).isTrue();
            assertThat(TemplateMatcher.compile(template).match(template + " ").matched()).isFalse();
        }

        @Test
        @DisplayName("TC-A004-16: 개행 차이도 불일치다 / a newline difference is a mismatch")
        // req: FR-ATV-004
        void newlineDifferenceIsMismatch() {
            assertThat(TemplateMatcher.compile("가\n#{a}").match("가 1").matched()).isFalse();
            assertThat(TemplateMatcher.compile("가\n#{a}").match("가\n1").matched()).isTrue();
        }

        @Test
        @DisplayName("변수가 여러 줄 값을 받을 수 있다 / a variable may span lines")
        // req: FR-ATV-004
        void variableMaySpanLines() {
            // DOTALL 을 켠 이유. 알림톡 본문에는 여러 줄 값이 들어간다.
            // Why DOTALL is enabled: AlimTalk bodies carry multi-line values.
            assertThat(TemplateMatcher.compile("내역:\n#{list}\n끝").match("내역:\n가\n나\n끝").matched()).isTrue();
        }

        @Test
        @DisplayName("null 본문·내용은 방어된다 / null template and content are handled")
        // req: FR-ATV-002
        void nullsAreHandled() {
            assertThatThrownBy(() -> TemplateMatcher.compile(null)).isInstanceOf(IllegalArgumentException.class);
            assertThat(TemplateMatcher.compile("#{a}").match(null).matched()).isFalse();
        }
    }

    @Nested
    @DisplayName("NFR-PERF-A01 — 컴파일 비용 / compilation cost")
    class Performance {

        @Test
        @DisplayName("전형적 템플릿 1000회 일치가 300ms 이내 / 1000 matches within the budget")
        // req: NFR-PERF-A01
        void typicalTemplateIsFast() {
            TemplateMatcher matcher = TemplateMatcher.compile("#{고객명}님, #{금액}원이 #{일시}에 결제되었습니다.");
            String content = "김철수님, 50,000원이 2026-08-18 14:00에 결제되었습니다.";

            long start = System.nanoTime();
            for (int i = 0; i < 1000; i++) {
                matcher.match(content);
            }
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

            // 캐시된 매처를 재사용하는 것이 설계의 전제다(ADR-ATK-022).
            // Reusing a cached matcher is the design's premise (ADR-ATK-022).
            assertThat(elapsed).isLessThan(Duration.ofMillis(300));
        }
    }
}
