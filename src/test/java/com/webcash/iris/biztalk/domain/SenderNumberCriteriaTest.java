package com.webcash.iris.biztalk.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link SenderNumberCriteria} 검증 — D-S14 회귀.
 * Verification for {@link SenderNumberCriteria} — D-S14.
 *
 * // req: FR-SND-002, FR-SND-003, NFR-PERF-D01
 */
class SenderNumberCriteriaTest {

    @Nested
    @DisplayName("페이징 정규화 / paging normalisation")
    class Paging {

        @ParameterizedTest
        @CsvSource({
                "0,   20",
                "-1,  20",
                "5,   20"
        })
        @DisplayName("음수 페이지는 0 으로 내린다 / a negative page floors to zero")
        // req: FR-SND-003
        void negativePageFloorsToZero(int page, int size) {
            SenderNumberCriteria criteria = SenderNumberCriteria.of("K0ABCD", page, size);
            assertThat(criteria.page()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("페이지 크기를 상한으로 제한한다 / clamps the page size to the maximum")
        // req: NFR-PERF-D01
        void clampsSize() {
            // T-D1: 레거시에는 LIMIT 자체가 없어 전체 목록이 매 요청 직렬화되었다.
            // T-D1: the legacy had no LIMIT at all, serialising the whole list every request.
            SenderNumberCriteria criteria = SenderNumberCriteria.of("K0ABCD", 0, 10_000);
            assertThat(criteria.size()).isEqualTo(SenderNumberCriteria.MAX_SIZE);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -5})
        @DisplayName("0 이하 크기는 기본값이 된다 / a non-positive size becomes the default")
        // req: FR-SND-003
        void nonPositiveSizeBecomesDefault(int size) {
            assertThat(SenderNumberCriteria.of("K0ABCD", 0, size).size())
                    .isEqualTo(SenderNumberCriteria.DEFAULT_SIZE);
        }

        @Test
        @DisplayName("null 은 기본값이 된다 / nulls become defaults")
        // req: FR-SND-003
        void nullsBecomeDefaults() {
            SenderNumberCriteria criteria = SenderNumberCriteria.of("K0ABCD", null, null);
            assertThat(criteria.page()).isZero();
            assertThat(criteria.size()).isEqualTo(SenderNumberCriteria.DEFAULT_SIZE);
        }
    }

    @Nested
    @DisplayName("오프셋 계산 / offset arithmetic")
    class Offset {

        @ParameterizedTest
        @CsvSource({
                "0, 20, 0",
                "1, 20, 20",
                "5, 50, 250"
        })
        @DisplayName("오프셋은 page * size 다 / the offset is page times size")
        // req: FR-SND-003
        void computesOffset(int page, int size, long expected) {
            assertThat(SenderNumberCriteria.of("K0ABCD", page, size).offset()).isEqualTo(expected);
        }

        @Test
        @DisplayName("큰 페이지에서도 오버플로하지 않는다 / does not overflow on a large page index")
        // req: FR-SND-003
        void doesNotOverflow() {
            // int 산술이라면 Integer.MAX_VALUE 근처에서 음수로 감싸고, 음수 오프셋은 예외가
            // 아니라 조용히 잘못된 페이지를 낳는다.
            //
            // With int arithmetic this wraps negative near Integer.MAX_VALUE, and a negative
            // offset is not an error — it is a silently wrong page.
            SenderNumberCriteria criteria =
                    SenderNumberCriteria.of("K0ABCD", Integer.MAX_VALUE, SenderNumberCriteria.MAX_SIZE);

            assertThat(criteria.offset())
                    .as("offset must stay positive at extreme page indexes")
                    .isPositive()
                    .isEqualTo((long) Integer.MAX_VALUE * SenderNumberCriteria.MAX_SIZE);
        }
    }

    @Nested
    @DisplayName("D-S19 회귀 — 기관 미선택 / no institution selected")
    class InstitutionRequired {

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        @DisplayName("빈 기관코드는 조회 대상이 아니다 / a blank institution is not queryable")
        // req: FR-SND-002
        void blankInstitutionIsNotQueryable(String code) {
            assertThat(SenderNumberCriteria.of(code, 0, 20).hasInstitution()).isFalse();
        }

        @Test
        @DisplayName("null 기관코드는 조회 대상이 아니다 / a null institution is not queryable")
        // req: FR-SND-002
        void nullInstitutionIsNotQueryable() {
            // 레거시는 onload 에서 getDat() 를 먼저 호출했고 콤보는 그 뒤에 채워졌으므로
            // 매 페이지 로드마다 빈 IS_CD 로 한 번씩 조회했다.
            //
            // The legacy called getDat() in onload before the combo was populated, so every page
            // load issued one query with a blank IS_CD.
            assertThat(SenderNumberCriteria.of(null, 0, 20).hasInstitution()).isFalse();
        }

        @Test
        @DisplayName("지정된 기관코드는 조회 대상이다 / a supplied institution is queryable")
        // req: FR-SND-001
        void suppliedInstitutionIsQueryable() {
            assertThat(SenderNumberCriteria.of("K0ABCD", 0, 20).hasInstitution()).isTrue();
        }
    }

    @Nested
    @DisplayName("기관코드는 손대지 않는다 / the institution code is untouched")
    class CodePreserved {

        @Test
        @DisplayName("기관코드를 그대로 보존한다 / preserves the institution code verbatim")
        // req: FR-AZ-D03
        void preservesCodeVerbatim() {
            // 신뢰 판단은 TenantContext 의 몫이다. 여기서 조용히 손질하면 그 대조가 무엇을
            // 검사하는지 흐려진다.
            //
            // Deciding whether to trust it belongs to TenantContext; adjusting it here would blur
            // what that reconciliation checks.
            assertThat(SenderNumberCriteria.of("  k0abcd  ", 0, 20).institutionCode())
                    .isEqualTo("  k0abcd  ");
        }
    }
}
