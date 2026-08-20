package com.webcash.iris.biztalk.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link AtkGenerator} 단위 테스트 — 결함 D-I4 회귀.
 * Unit tests for {@link AtkGenerator} — defect D-I4 regression.
 *
 * <h2>이 테스트가 증명할 수 있는 것과 없는 것 / what it can and cannot prove</h2>
 * <p>"CSPRNG 인가" 는 출력만 보고 판정할 수 없다 — 그것은 코드 리뷰의 몫이며, 여기서는
 * {@code SecureRandom} 을 쓴다는 사실이 그 근거다. 테스트가 지킬 수 있는 것은 <b>형태</b>다:
 * 길이, 문자 집합, 그리고 1,000개 표본에서 중복이 없다는 것. 레거시 결함(D-I4)은 정확히
 * 형태의 문제이기도 했다 — 20자였고 알파벳은 62자였으며, 무엇보다 <b>브라우저에서</b>
 * 만들어졌다.</p>
 * <p>Whether a source is a CSPRNG cannot be decided from its output; that is code review's job,
 * and the use of {@code SecureRandom} is the evidence. What a test can hold is the <b>shape</b>:
 * length, alphabet, and no repeat across 1,000 samples. The legacy defect was partly a shape
 * problem too — 20 characters over a 62-character alphabet, and generated <b>in the browser</b>.</p>
 *
 * // source: biztalk_admin_01.js — randomGenerator(20) over Math.random()
 * // req: FR-ATK-001, NFR-SEC-CRED-I01, TC-I002-06, D-I4
 */
class AtkGeneratorTest {

    /** 표본 수 — TEST-PLAN 의 1,000개. / Sample size, the 1,000 of the TEST-PLAN. */
    private static final int SAMPLES = 1_000;

    private final AtkGenerator generator = new AtkGenerator();

    @Test
    @DisplayName("27자를 발급한다 — 160비트 이상 / issues 27 characters, carrying over 160 bits")
        // req: FR-ATK-001, NFR-SEC-CRED-I01
    void issuesTwentySevenCharacters() {
        // 27 × log2(62) ≈ 160.7 비트. NFR-SEC-CRED-I01 의 하한은 128 비트이며, 레거시의
        // 20자(≈119비트)는 그 하한에 미치지 못했다 — 난수원 문제와 별개의 결함이다.
        // 27 × log2(62) ≈ 160.7 bits against the 128-bit floor in NFR-SEC-CRED-I01. The legacy's
        // 20 characters (≈119 bits) fell below it — a defect independent of the entropy source.
        assertThat(generator.generate()).hasSize(AtkGenerator.LENGTH);
        assertThat(AtkGenerator.LENGTH).isEqualTo(27);
    }

    @Test
    @DisplayName("영숫자만 사용한다 / uses alphanumerics only")
        // req: FR-ATK-001, ADR-INST-015 §2.2
    void usesAlphanumericsOnly() {
        // 고객사가 설정 파일이나 URL 에 이 값을 넣으므로 인코딩·인용이 필요한 문자를 쓰지
        // 않는다. 레거시 알파벳이 유일하게 옳게 한 선택이다.
        // Customers embed this in configuration and URLs, so no character needing encoding or
        // quoting is used — the one thing the legacy alphabet got right.
        for (int i = 0; i < SAMPLES; i++) {
            assertThat(generator.generate()).matches("[A-Za-z0-9]{27}");
        }
    }

    @Test
    @DisplayName("1,000개 표본에 중복이 없다 / no repeat across 1,000 samples")
        // req: FR-ATK-001, TC-I002-06
    void producesNoDuplicates() {
        Set<String> issued = new HashSet<>();
        for (int i = 0; i < SAMPLES; i++) {
            issued.add(generator.generate());
        }
        assertThat(issued).hasSize(SAMPLES);
    }

    @Test
    @DisplayName("62개 문자가 모두 나타난다 / every character of the alphabet appears")
        // req: FR-ATK-001
    void coversTheWholeAlphabet() {
        // 알파벳 일부만 나오면 실효 엔트로피가 계산보다 낮다. 1,000 × 27 = 27,000 문자
        // 표본에서 62개 문자가 모두 나오지 않을 확률은 무시할 수 있다.
        // If only part of the alphabet appears the effective entropy is lower than calculated.
        // Across 1,000 × 27 = 27,000 characters, missing any of the 62 is negligibly unlikely.
        Set<Character> seen = new HashSet<>();
        for (int i = 0; i < SAMPLES; i++) {
            for (char c : generator.generate().toCharArray()) {
                seen.add(c);
            }
        }
        assertThat(seen).hasSize(62);
    }

    @Test
    @DisplayName("마스킹된 값을 형식 위반으로 판정한다 / a masked value fails the shape check")
        // req: FR-ATK-002, TM-I022
    void rejectsMaskedValues() {
        // 화면은 인증키를 마스킹된 상태로 갖는다. 그 문자열이 쓰기 경로로 돌아오면 별표가
        // 자격증명이 되어 고객사 연동이 끊긴다. 별표는 알파벳에 없으므로 여기서 걸린다.
        // The screen holds the key masked; if that string returned on a write path the asterisks
        // would become the credential. Asterisks are not in the alphabet, so they fail here.
        assertThat(AtkGenerator.isWellFormed("***************************")).isFalse();
        assertThat(AtkGenerator.isWellFormed("***********************LE01")).isFalse();
        assertThat(AtkGenerator.isWellFormed(null)).isFalse();
    }

    @Test
    @DisplayName("레거시 길이의 키는 형식 위반이다 / a legacy-length key fails the shape check")
        // req: FR-ATK-006, RESIDUAL-I01
    void rejectsLegacyLengthKeys() {
        // 운영에서 관찰된 레거시 키는 20자다. 형식 검사는 <b>새로 발급된 값</b>에만
        // 적용된다 — 기존 키는 그대로 보존되며(FR-ATK-006) 이 검사를 통과하지 않는다.
        // 그것이 정상이다: 두 집단을 값만으로 구분할 수 없다는 것이 RESIDUAL-I01 이며,
        // 이 메서드는 저장된 키를 판정하는 데 쓰이지 않는다.
        // A production legacy key is 20 characters. This check applies only to <b>newly issued</b>
        // values: existing keys are preserved unchanged (FR-ATK-006) and do not pass it. That is
        // correct — RESIDUAL-I01 is precisely that the two populations are indistinguishable by
        // value, and this method is never used to judge a stored key.
        assertThat(AtkGenerator.isWellFormed("SAMPLEsampleSAMPLE01")).isFalse();
    }
}
