package com.webcash.iris.biztalk.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link InstitutionStatus} 단위 테스트.
 * Unit tests for {@link InstitutionStatus}.
 *
 * // source: IDO.KKB_FT_FTIS_INFO_L001 — IS_STTS; biztalk_admin_00.js USE_YN renderer
 * // req: FR-INST-006, ADR-INST-014
 */
class InstitutionStatusTest {

    @Nested
    @DisplayName("코드 매핑 / code mapping")
    class CodeMapping {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({"Y,ACTIVE", "N,SUSPENDED", "D,DELETED"})
        @DisplayName("코드값이 상태로 매핑된다 / a code resolves to its status")
            // req: ADR-INST-014
        void resolvesKnownCodes(String code, InstitutionStatus expected) {
            assertThat(InstitutionStatus.fromCode(code)).isEqualTo(expected);
        }

        @ParameterizedTest
        @ValueSource(strings = {"X", "y", "", " ", "YY"})
        @DisplayName("알 수 없는 코드는 null 이다 / an unknown code resolves to null")
            // req: FR-INST-006
        void unknownCodeResolvesToNull(String code) {
            assertThat(InstitutionStatus.fromCode(code)).isNull();
        }

        @Test
        @DisplayName("null 코드는 null 이다 / a null code resolves to null")
        void nullCodeResolvesToNull() {
            assertThat(InstitutionStatus.fromCode(null)).isNull();
        }

        @Test
        @DisplayName("코드는 한 글자이며 중복되지 않는다 / codes are single characters and unique")
            // req: ADR-INST-014 — IS_STTS is a CHAR(1)-shaped column
        void codesAreDistinctSingleCharacters() {
            assertThat(InstitutionStatus.values())
                    .extracting(InstitutionStatus::code)
                    .allMatch(c -> c.length() == 1)
                    .doesNotHaveDuplicates();
        }
    }

    @Nested
    @DisplayName("표시 라벨 / display labels")
    class Labels {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({"Y,사용", "N,미사용", "D,삭제"})
        @DisplayName("알려진 코드는 라벨로 표시된다 / a known code renders as its label")
            // req: FR-INST-006
        void knownCodesRenderAsLabels(String code, String label) {
            assertThat(InstitutionStatus.labelOf(code)).isEqualTo(label);
        }

        @ParameterizedTest
        @ValueSource(strings = {"X", "9", "unexpected"})
        @DisplayName("알 수 없는 값은 원문 그대로 표시된다 / an unmapped value renders verbatim")
            // req: FR-INST-006
        void unmappedValueRendersVerbatim(String code) {
            // 레거시는 'Y' 가 아닌 모든 값을 '미사용' 으로 표시해 데이터 이상을 감췄다.
            // The legacy rendered anything but 'Y' as 미사용, hiding data anomalies.
            assertThat(InstitutionStatus.labelOf(code))
                    .isEqualTo(code)
                    .isNotEqualTo("미사용");
        }

        @Test
        @DisplayName("null 은 null 로 표시된다 / null renders as null")
            // req: FR-INST-006
        void nullRendersAsNull() {
            assertThat(InstitutionStatus.labelOf(null)).isNull();
        }
    }

    @Nested
    @DisplayName("레거시 호환 / legacy compatibility")
    class LegacyCompatibility {

        @Test
        @DisplayName("삭제 상태는 레거시의 Y/N 필터 어디에도 걸리지 않는다 / DELETED matches neither legacy filter")
            // req: ADR-INST-014, TM-I013
        void deletedMatchesNeitherLegacyFilter() {
            // 레거시 목록 쿼리는 IS_STTS = 'Y' 또는 'N' 으로 비교한다. 삭제 코드가 그 어느
            // 쪽과도 같지 않아야 논리삭제가 레거시에서 구조적으로 탈락한다 — 새 컬럼을
            // 추가했다면 레거시가 그 컬럼을 보지 못해 삭제된 기관이 계속 활성 상태로 보인다.
            // The legacy list query compares IS_STTS against 'Y' or 'N'. The deleted code must
            // equal neither, so a logical delete drops out of legacy results by construction —
            // a new column would have been invisible to the legacy, leaving deleted
            // institutions active there.
            assertThat(InstitutionStatus.DELETED.code())
                    .isNotEqualTo(InstitutionStatus.ACTIVE.code())
                    .isNotEqualTo(InstitutionStatus.SUSPENDED.code());
        }

        @Test
        @DisplayName("활성 코드는 레거시와 동일한 Y 다 / the active code is the legacy's Y")
            // req: ADR-INST-016 — column semantics preserved
        void activeCodeMatchesLegacy() {
            assertThat(InstitutionStatus.ACTIVE.code()).isEqualTo("Y");
            assertThat(InstitutionStatus.SUSPENDED.code()).isEqualTo("N");
        }

        @ParameterizedTest
        @ValueSource(strings = {"ACTIVE", "SUSPENDED", "DELETED"})
        @DisplayName("상수 이름이 고정된다 / constant names are stable")
            // req: ADR-INST-014 — names appear in audit detail and API payloads, so a rename
            //      would silently change stored history and client contracts
        void constantNamesAreStable(String name) {
            assertThat(InstitutionStatus.valueOf(name).name()).isEqualTo(name);
        }
    }
}
