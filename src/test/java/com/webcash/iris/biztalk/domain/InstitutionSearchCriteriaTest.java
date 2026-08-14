package com.webcash.iris.biztalk.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link InstitutionSearchCriteria} 단위 테스트 — 결함 D-I10 / D-I11 회귀.
 * Unit tests for {@link InstitutionSearchCriteria} — D-I10 / D-I11 regression.
 *
 * // source: biztalk_admin_00_view.jsp, IDO.KKB_FT_FTIS_INFO_L001
 * // req: FR-INST-003, FR-INST-005, NFR-PERF-I02
 */
class InstitutionSearchCriteriaTest {

    @Nested
    @DisplayName("페이징 정규화 / paging normalisation")
    class Paging {

        @Test
        @DisplayName("기본 페이지 크기는 20 이다 / the default page size is 20")
            // req: NFR-PERF-I02
        void defaultsToTwenty() {
            assertThat(InstitutionSearchCriteria.of(null, null, null, null).size()).isEqualTo(20);
        }

        @ParameterizedTest
        @ValueSource(ints = {201, 1_000, 100_000, Integer.MAX_VALUE})
        @DisplayName("페이지 크기는 200 으로 제한된다 / the page size is clamped to 200")
            // req: NFR-PERF-I02, TM-I011
        void clampsOversizedPage(int requested) {
            // 레거시에는 LIMIT 자체가 없어 전체 레지스트리가 매 요청마다 직렬화되었다.
            // The legacy had no LIMIT at all, serialising the whole registry per request.
            assertThat(InstitutionSearchCriteria.of(null, null, 0, requested).size()).isEqualTo(200);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, -100})
        @DisplayName("0 이하 크기는 기본값으로 대체된다 / a non-positive size falls back to the default")
        void nonPositiveSizeFallsBack(int requested) {
            assertThat(InstitutionSearchCriteria.of(null, null, 0, requested).size()).isEqualTo(20);
        }

        @ParameterizedTest
        @ValueSource(ints = {-1, -50})
        @DisplayName("음수 페이지는 0 으로 보정된다 / a negative page is corrected to zero")
        void negativePageBecomesZero(int requested) {
            assertThat(InstitutionSearchCriteria.of(null, null, requested, null).page()).isZero();
        }

        @ParameterizedTest(name = "page {0} size {1} → offset {2}")
        @CsvSource({"0,20,0", "1,20,20", "3,50,150", "2,200,400"})
        @DisplayName("offset 이 페이지와 크기로 계산된다 / offset derives from page and size")
            // req: FR-INST-003
        void computesOffset(int page, int size, int expected) {
            assertThat(InstitutionSearchCriteria.of(null, null, page, size).offset()).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("상태 필터 / status filter")
    class Status {

        @ParameterizedTest
        @ValueSource(strings = {"ALL", "", "  "})
        @DisplayName("전체는 필터 없음으로 정규화된다 / 전체 normalises to no filter")
            // req: FR-INST-001
        void allMeansNoFilter(String status) {
            assertThat(InstitutionSearchCriteria.of(null, status, null, null).status()).isNull();
        }

        @Test
        @DisplayName("null 상태는 필터 없음이다 / a null status means no filter")
        void nullMeansNoFilter() {
            assertThat(InstitutionSearchCriteria.of(null, null, null, null).status()).isNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {"Y", "N"})
        @DisplayName("구체적인 상태는 보존된다 / a specific status is preserved")
        void specificStatusPreserved(String status) {
            assertThat(InstitutionSearchCriteria.of(null, status, null, null).status()).isEqualTo(status);
        }
    }

    @Nested
    @DisplayName("검색어와 LIKE 패턴 / search term and LIKE pattern")
    class NamePattern {

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "\t"})
        @DisplayName("공백 검색어는 필터 없음이다 / a blank term means no filter")
            // req: FR-INST-004 — 검색어가 없으면 LIKE 절 자체가 생기지 않아 NULL 기관명도 남는다
        void blankNameMeansNoFilter(String name) {
            var criteria = InstitutionSearchCriteria.of(name, null, null, null);

            assertThat(criteria.name()).isNull();
            // 패턴이 null 이면 매퍼가 LIKE 절을 생성하지 않는다 — 레거시는 빈 검색어에도
            // LIKE 를 걸어 ISNM 이 NULL 인 행을 조용히 떨어뜨렸다.
            // A null pattern means the mapper emits no LIKE clause; the legacy applied one even
            // for an empty term, silently dropping rows with a NULL ISNM.
            assertThat(criteria.namePattern()).isNull();
        }

        @Test
        @DisplayName("검색어는 앞뒤 공백이 제거된다 / the term is trimmed")
        void trimsName() {
            assertThat(InstitutionSearchCriteria.of("  쿠콘  ", null, null, null).name()).isEqualTo("쿠콘");
        }

        @Test
        @DisplayName("일반 검색어는 양쪽 와일드카드로 감싼다 / an ordinary term is wrapped in wildcards")
        void wrapsInWildcards() {
            assertThat(InstitutionSearchCriteria.of("쿠콘", null, null, null).namePattern())
                    .isEqualTo("%쿠콘%");
        }

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({
                "100%,        %100\\%%",
                "a_b,         %a\\_b%",
                "%,           %\\%%",
                "_,           %\\_%"
        })
        @DisplayName("와일드카드 문자는 이스케이프된다 / wildcard characters are escaped")
            // req: FR-INST-005, D-I11
        void escapesWildcards(String input, String expected) {
            assertThat(InstitutionSearchCriteria.of(input, null, null, null).namePattern())
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("백슬래시가 먼저 이스케이프된다 / the backslash is escaped first")
            // req: FR-INST-005
        void escapesBackslashFirst() {
            // 백슬래시를 나중에 치환하면 % 치환이 만든 백슬래시까지 다시 이스케이프되어
            // 패턴이 깨진다. 순서가 정확한지 확인한다.
            // Replacing the backslash later would re-escape the ones introduced by the %
            // replacement, corrupting the pattern. This pins the order.
            assertThat(InstitutionSearchCriteria.of("a\\%b", null, null, null).namePattern())
                    .isEqualTo("%a\\\\\\%b%");
        }

        @Test
        @DisplayName("단독 % 검색은 전체를 매칭하지 않는다 / a lone % does not match everything")
            // req: FR-INST-005, D-I11
        void lonePercentIsLiteral() {
            String pattern = InstitutionSearchCriteria.of("%", null, null, null).namePattern();

            // 이스케이프되지 않았다면 "%%" 가 되어 모든 행에 매칭된다.
            // Unescaped this would be "%%", matching every row.
            assertThat(pattern).isEqualTo("%\\%%").isNotEqualTo("%%");
        }

        @Test
        @DisplayName("과도하게 긴 검색어는 잘린다 / an over-long term is truncated")
            // req: FR-INST-001 — 계약상 IS_NM 은 100자
        void truncatesOverLongName() {
            String longName = "가".repeat(150);

            assertThat(InstitutionSearchCriteria.of(longName, null, null, null).name())
                    .hasSize(InstitutionSearchCriteria.MAX_NAME_LENGTH);
        }
    }
}
