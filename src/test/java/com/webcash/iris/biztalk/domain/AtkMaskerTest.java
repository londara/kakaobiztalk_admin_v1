package com.webcash.iris.biztalk.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link AtkMasker} 단위 테스트 — 결함 D-I5 회귀.
 * Unit tests for {@link AtkMasker} — defect D-I5 regression.
 *
 * <p>레거시 목록 화면은 전 기관의 인증키를 평문으로 노출했다. 이 테스트들은 마스킹이
 * <b>실제로 값을 가리는지</b>를 검증한다 — 특히 짧은 키에서 마스킹이 무력해지지 않는지.</p>
 * <p>The legacy list screen exposed every institution's 인증키 in plaintext. These tests
 * verify the masking <b>actually hides the value</b>, with particular attention to short
 * keys where naive masking would disclose everything.</p>
 *
 * // source: biztalk_admin_00.js — drawGrid() colDef { key:"ATK" }
 * // req: FR-ATK-002, TM-I003
 */
class AtkMaskerTest {

    /**
     * 운영 데이터에서 관찰된 실제 인증키 길이. 레거시 생성기가 20자를 만든다.
     * The real 인증키 length observed in production data; the legacy generator emits 20.
     */
    private static final int LEGACY_KEY_LENGTH = 20;

    @Test
    @DisplayName("20자 레거시 키는 뒤 4자리만 남는다 / a 20-char legacy key keeps only its last four")
        // req: FR-ATK-002
    void masksLegacyLengthKey() {
        String key = "A".repeat(16) + "7f3a";

        String masked = AtkMasker.mask(key);

        assertThat(key).hasSize(LEGACY_KEY_LENGTH);
        assertThat(masked).isEqualTo("****************7f3a");
        assertThat(masked).hasSameSizeAs(key);
    }

    @Test
    @DisplayName("마스킹 결과에 원본 앞부분이 남지 않는다 / no leading part of the original survives")
        // req: FR-ATK-002, TM-I003
    void leaksNoLeadingCharacters() {
        String key = "6oG4mYDC6vrCLIyTzy8o";

        String masked = AtkMasker.mask(key);

        // 앞 16자는 어떤 형태로도 남아 있으면 안 된다.
        // None of the first 16 characters may survive in any form.
        assertThat(masked).doesNotContain(key.substring(0, key.length() - 4));
        assertThat(masked).endsWith(key.substring(key.length() - 4));
    }

    @ParameterizedTest(name = "길이 {0} → 전부 마스킹 / length {0} is fully masked")
    @CsvSource({"1,*", "2,**", "3,***", "4,****"})
    @DisplayName("4자 이하 키는 전부 마스킹한다 / a key of four or fewer characters is fully masked")
        // req: FR-ATK-002
    void masksShortKeysEntirely(int length, String expected) {
        String key = "abcd".substring(0, length);

        String masked = AtkMasker.mask(key);

        // 뒤 4자리를 남기는 규칙을 짧은 키에 그대로 적용하면 값 전체가 드러난다.
        // Applying the last-four rule to a short key would disclose the whole value.
        assertThat(masked).isEqualTo(expected);
        assertThat(masked).doesNotContain(key);
    }

    @Test
    @DisplayName("5자 키는 1자만 가려진다 / a five-char key hides exactly one character")
        // req: FR-ATK-002
    void masksBoundaryLengthKey() {
        assertThat(AtkMasker.mask("abcde")).isEqualTo("*bcde");
    }

    @Test
    @DisplayName("null 은 null 을 반환한다 / null returns null")
        // req: FR-ATK-002
    void nullReturnsNull() {
        assertThat(AtkMasker.mask(null)).isNull();
    }

    @Test
    @DisplayName("빈 문자열은 그대로 반환한다 / an empty string is returned unchanged")
        // req: FR-ATK-002
    void emptyReturnsEmpty() {
        assertThat(AtkMasker.mask("")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "6oG4mYDC6vrCLIyTzy8o",
            "89uJFb0wEm1N4MjXohVF",
            "aB3",
            "abcdefghijklmnopqrstuvwxyz0123456789"
    })
    @DisplayName("어떤 입력이든 길이는 보존된다 / length is preserved for any input")
        // req: FR-ATK-002
    void preservesLength(String key) {
        assertThat(AtkMasker.mask(key)).hasSameSizeAs(key);
    }
}
