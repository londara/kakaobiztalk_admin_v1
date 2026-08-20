package com.webcash.iris.biztalk.alimtalk.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link RecipientParser} 검증. / Verification for {@link RecipientParser}.
 *
 * <p>레거시의 세 결함을 한곳에서 회귀 검증한다 — D-A12(검증 없음), D-A28(고정되지 않은 정규식),
 * D-A35(공백 한 칸 분리).</p>
 * <p>Regression coverage for three legacy defects in one place: D-A12, D-A28 and D-A35.</p>
 *
 * // source: biztalk_admin_50_s001_act.jsp — isPhoneNumber(), RECEIVER_NUMBER.split(" ")
 * // req: FR-ATC-012, FR-ATS-005, FR-ATS-006, FR-ATS-007
 */
class RecipientParserTest {

    @Nested
    @DisplayName("D-A28 — 정규식 고정 / anchoring")
    class Anchoring {

        @Test
        @DisplayName("TC-A002-09: 앞에 문자가 붙은 번호를 거절한다 / rejects a prefixed number")
        // req: FR-ATS-005
        void rejectsPrefixedNumber() {
            // 레거시는 (01[016789]{1})(\d{3,4})\d{4}$ 를 find() 로 적용했다. 끝에는 $ 가 있으나
            // 앞에 ^ 가 없고 find() 는 부분 일치를 찾으므로 이 값이 통과했다.
            // The legacy applied its pattern with find(): a trailing $ but no leading ^, and find()
            // seeks a substring, so this value passed.
            RecipientParser.Result result = RecipientParser.parse("abc01012345678");

            assertThat(result.accepted()).isEmpty();
            assertThat(result.rejected()).containsExactly("abc01012345678");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "abc01012345678", "01012345678abc", "010123456789012",
                "02012345678", "1012345678", "0101234567a", "010123456"
        })
        @DisplayName("형식에 맞지 않는 값을 모두 거절한다 / rejects every malformed value")
        // req: FR-ATS-005
        void rejectsMalformed(String input) {
            // 10자리(010+7)와 11자리(010+8)는 모두 유효하므로 이 목록에 넣지 않는다. 레거시
            // 정규식도 (\d{3,4}) 로 가운데 3~4자리를 허용했고, 마이그레이션에서 그 범위를
            // 좁히면 기존에 보낼 수 있었던 번호를 못 보내게 된다.
            // Both ten digits (010+7) and eleven (010+8) are valid, so neither appears here. The legacy
            // pattern also allowed 3-4 middle digits via (\d{3,4}); narrowing that range in a migration
            // would stop numbers that could previously be sent to.
            assertThat(RecipientParser.parse(input).accepted()).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"01012345678", "01112345678", "01612345678", "01712345678",
                "01812345678", "01912345678", "0101234567"})
        @DisplayName("유효한 휴대전화번호를 받는다 / accepts valid mobile numbers")
        // req: FR-ATS-005
        void acceptsValidMobile(String input) {
            // 011/016~019 는 01[016789] 에 포함된다. 0101234567 은 10자리로 유효하다.
            // 011 and 016-019 fall within 01[016789]; 0101234567 is a valid ten-digit number.
            assertThat(RecipientParser.parse(input).accepted()).hasSize(1);
        }

        @Test
        @DisplayName("AMB-A09 — 지역번호는 명시적으로 거절한다 / landlines are rejected explicitly")
        // req: FR-ATS-005
        void landlinesRejectedExplicitly() {
            // 레거시도 지역번호를 받지 않았으나 조용히 버렸고, 그 버림이 발송 이후의 예외로
            // 나타났다(D-A26). 여기서는 결과에 남아 발송 전에 보고된다.
            // The legacy also refused landlines, but discarded them silently and surfaced the discard as
            // a post-send exception (D-A26). Here they remain in the result and are reported before sending.
            RecipientParser.Result result = RecipientParser.parse("0212345678");

            assertThat(result.accepted()).isEmpty();
            assertThat(result.rejected()).containsExactly("0212345678");
            assertThat(result.requiresConfirmation()).isTrue();
        }
    }

    @Nested
    @DisplayName("D-A35 — 구분자 / delimiters")
    class Delimiters {

        @Test
        @DisplayName("TC-A002-15: 쉼표·개행·중복 공백이 섞여도 건수가 맞는다 / mixed delimiters count correctly")
        // req: FR-ATS-005, FR-ATC-012
        void mixedDelimitersCountCorrectly() {
            // 레거시는 split(" ") 이었으므로 이 입력은 잘못된 번호 한 개로 해석되었다.
            // With split(" ") the legacy parsed this input as one malformed number.
            RecipientParser.Result result = RecipientParser.parse(
                    "01011112222,01033334444\n01055556666;  01077778888\t01099990000");

            assertThat(result.count()).isEqualTo(5);
            assertThat(result.rejected()).isEmpty();
        }

        @Test
        @DisplayName("연속 구분자와 앞뒤 공백은 오류가 아니다 / repeated delimiters and padding are not errors")
        // req: FR-ATS-005
        void repeatedDelimitersAreNotErrors() {
            RecipientParser.Result result = RecipientParser.parse("  01011112222,,,  01033334444 \n ");

            assertThat(result.count()).isEqualTo(2);
            assertThat(result.rejected()).isEmpty();
        }

        @Test
        @DisplayName("하이픈·괄호 표기를 정규화한다 / normalises presentational formatting")
        // req: FR-ATS-005
        void normalisesPresentation() {
            RecipientParser.Result result = RecipientParser.parse("010-1111-2222, (010)3333-4444");

            assertThat(result.count()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("D-A12 — 중복과 건수 / duplicates and counting")
    class Duplicates {

        @Test
        @DisplayName("TC-A001-14: 같은 번호를 세 번 넣으면 한 건이 된다 / three of the same become one")
        // req: FR-ATC-012
        void duplicatesCollapse() {
            RecipientParser.Result result = RecipientParser.parse(
                    "01011112222,01011112222,01011112222");

            assertThat(result.count()).isEqualTo(1);
            assertThat(result.duplicates()).isEqualTo(2);
        }

        @Test
        @DisplayName("표기가 달라도 같은 번호는 중복이다 / differently formatted duplicates collapse")
        // req: FR-ATC-012
        void formattingDoesNotDefeatDeduplication() {
            // 정규화를 중복 제거보다 앞에 두는 이유. 순서를 바꾸면 이 둘이 다른 번호로 보인다.
            // Why normalisation precedes de-duplication: reversed, these would look like two numbers.
            RecipientParser.Result result = RecipientParser.parse("010-1111-2222, 01011112222");

            assertThat(result.count()).isEqualTo(1);
            assertThat(result.duplicates()).isEqualTo(1);
        }

        @Test
        @DisplayName("입력 순서가 유지된다 / input order is preserved")
        // req: FR-ATC-012
        void inputOrderPreserved() {
            RecipientParser.Result result = RecipientParser.parse("01033334444,01011112222");

            assertThat(result.accepted().get(0).exposeForVendorCall()).isEqualTo("01033334444");
        }
    }

    @Nested
    @DisplayName("D-A31 — 빈 수신자 / empty recipients")
    class Empty {

        @Test
        @DisplayName("TC-A002-12: 모두 형식 불일치면 발송 대상이 없다 / all malformed means nobody to send to")
        // req: FR-ATS-006
        void allMalformedMeansNoRecipients() {
            // 레거시는 이 경우 빈 배열로 벤더를 호출한 뒤 예외를 던졌다.
            // The legacy called the vendor with an empty array and then threw.
            RecipientParser.Result result = RecipientParser.parse("abc, def, 123");

            assertThat(result.hasRecipients()).isFalse();
            assertThat(result.rejected()).hasSize(3);
        }

        @Test
        @DisplayName("빈 입력과 null 은 조용히 빈 결과가 된다 / blank and null yield an empty result")
        // req: FR-ATS-006
        void blankAndNullAreEmpty() {
            assertThat(RecipientParser.parse(null).hasRecipients()).isFalse();
            assertThat(RecipientParser.parse("   ").hasRecipients()).isFalse();
            assertThat(RecipientParser.parse("   ").rejected()).isEmpty();
        }
    }

    @Nested
    @DisplayName("FR-ATS-007 — 발송 전 결정 / a pre-despatch decision")
    class Confirmation {

        @Test
        @DisplayName("일부만 유효하면 확인이 필요하다 / a partial result requires confirmation")
        // req: FR-ATS-007
        void partialRequiresConfirmation() {
            // 이 결과가 예외가 아니라 값이라는 점이 D-A26 대응의 핵심이다. 호출부가 발송 전에
            // 운영자에게 물을 수 있다.
            // That this is a value rather than an exception is the core of the D-A26 fix: the caller can
            // ask the operator before sending.
            RecipientParser.Result result = RecipientParser.parse("01011112222, 0212345678");

            assertThat(result.hasRecipients()).isTrue();
            assertThat(result.requiresConfirmation()).isTrue();
            assertThat(result.count()).isEqualTo(1);
            assertThat(result.rejected()).containsExactly("0212345678");
        }

        @Test
        @DisplayName("전부 유효하면 확인이 필요 없다 / a clean result needs no confirmation")
        // req: FR-ATS-007
        void cleanResultNeedsNoConfirmation() {
            assertThat(RecipientParser.parse("01011112222,01033334444").requiresConfirmation()).isFalse();
        }
    }

    @Nested
    @DisplayName("NFR-SEC-PII — 마스킹 / masking")
    class Masking {

        @Test
        @DisplayName("toString 이 번호를 가린다 / toString masks the number")
        // req: NFR-SEC-PII-A01, NFR-SEC-PII-A02
        void toStringMasks() {
            RecipientNumber number = RecipientParser.parse("01012345678").accepted().get(0);

            assertThat(number.toString()).isEqualTo("010****5678");
            assertThat(number.toString()).doesNotContain("1234");
        }

        @Test
        @DisplayName("목록 출력에도 평문이 없다 / a list dump carries no clear number")
        // req: NFR-SEC-PII-A02
        void listDumpCarriesNoClearNumber() {
            // 레거시의 D-A30 은 요청 객체 전체를 문자열로 만든 한 줄이었다. 컬렉션 출력도
            // 같은 경로이므로 함께 확인한다.
            // D-A30 was one line stringifying the whole request object. A collection dump is the same
            // path, so it is checked too.
            String dump = RecipientParser.parse("01012345678,01099998888").accepted().toString();

            assertThat(dump).doesNotContain("01012345678").doesNotContain("01099998888");
            assertThat(dump).contains("010****5678");
        }

        @Test
        @DisplayName("짧은 번호도 마스킹된다 / short numbers are masked too")
        // req: NFR-SEC-PII-A01
        void shortNumbersAreMasked() {
            assertThat(RecipientNumber.of("1234").toString()).isEqualTo("****");
            assertThat(RecipientNumber.of("12345678").toString()).isEqualTo("****5678");
        }
    }
}
