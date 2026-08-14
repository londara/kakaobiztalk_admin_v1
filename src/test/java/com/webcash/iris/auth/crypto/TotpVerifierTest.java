package com.webcash.iris.auth.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import dev.samstevens.totp.time.TimeProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link TotpVerifier} 단위 테스트 — 레거시 결함 L3 회귀 방지.
 * Unit tests for {@link TotpVerifier} — regression guard for legacy defect L3.
 *
 * <p>레거시 {@code GoogleOTP.checkCode()} 는 {@code int window = 0;} 으로 시간 오차를
 * 전혀 허용하지 않았다(바로 위 줄에 {@code int window = 3;} 이 주석 처리되어 있었다).
 * 아래 {@code acceptsCodeFrom...} 테스트들은 레거시 구현에 대해 <b>반드시 실패</b>한다 —
 * 그것이 이 테스트의 존재 이유다.</p>
 * <p>The legacy {@code GoogleOTP.checkCode()} allowed no clock skew at all
 * ({@code int window = 0;}, with {@code int window = 3;} commented out directly above).
 * The {@code acceptsCodeFrom...} tests below <b>necessarily fail</b> against the legacy
 * implementation, which is exactly their purpose.</p>
 *
 * // source: com/common/irisadmin/util/GoogleOTP.java — checkCode()
 * // req: FR-LOGIN-009, FR-LOGIN-011, NFR-SEC-AUTH-L03
 */
class TotpVerifierTest {

    /** 20바이트(160비트) Base32 비밀키. / A 20-byte (160-bit) Base32 secret. */
    private static final String SECRET = "MFRGGZDFMZTWQ2LKNNWG23TPOBYXE43U";

    /** 고정 시각(초). 30으로 나누어떨어지는 값을 골라 경계 계산을 단순하게 한다. */
    /** Fixed time in seconds, chosen as a multiple of 30 to keep bucket maths obvious. */
    private static final long NOW_SECONDS = 1_786_000_020L;

    private final TimeProvider fixedTime = () -> NOW_SECONDS;

    private TotpVerifier verifier(int skewSteps) {
        return new TotpVerifier(fixedTime, skewSteps);
    }

    /**
     * 지정한 스텝 오프셋에 해당하는 유효 코드를 생성한다.
     * Generates a valid code for the given step offset from now.
     */
    private String codeForOffset(int stepOffset) throws CodeGenerationException {
        long bucket = Math.floorDiv(NOW_SECONDS, TotpVerifier.TIME_STEP_SECONDS) + stepOffset;
        return new DefaultCodeGenerator().generate(SECRET, bucket);
    }

    @Test
    @DisplayName("현재 스텝의 코드는 통과한다 / a code from the current step verifies")
        // req: FR-LOGIN-011
    void acceptsCurrentStep() throws Exception {
        assertThat(verifier(1).verify(SECRET, codeForOffset(0))).isTrue();
    }

    @Test
    @DisplayName("L3 회귀: 직전 스텝의 코드를 통과시킨다 / L3 regression: accepts a code from the previous step")
        // req: FR-LOGIN-011
    void acceptsCodeFromPreviousStep() throws Exception {
        // 레거시(window=0)에서는 실패했다. 단말 시계가 30초 늦은 사용자가 겪던 상황이다.
        // Failed under the legacy (window=0) — the situation of a user whose device clock
        // ran 30 seconds behind.
        assertThat(verifier(1).verify(SECRET, codeForOffset(-1))).isTrue();
    }

    @Test
    @DisplayName("L3 회귀: 직후 스텝의 코드를 통과시킨다 / L3 regression: accepts a code from the next step")
        // req: FR-LOGIN-011
    void acceptsCodeFromNextStep() throws Exception {
        assertThat(verifier(1).verify(SECRET, codeForOffset(1))).isTrue();
    }

    @ParameterizedTest(name = "스텝 오프셋 {0} → 거절")
    @CsvSource({"-3", "-2", "2", "3", "10"})
    @DisplayName("±2 스텝 이상은 거절한다 / rejects codes two or more steps away")
        // req: FR-LOGIN-011, TM-L004
    void rejectsCodesBeyondOneStep(int offset) throws Exception {
        // 창을 넓히면 관측된 코드의 재사용 가능 시간이 함께 늘어난다. ±1 이 상한이다.
        // A wider window lengthens the replay period for an observed code; ±1 is the cap.
        assertThat(verifier(1).verify(SECRET, codeForOffset(offset))).isFalse();
    }

    @Test
    @DisplayName("허용 오차가 0이면 직전 스텝을 거절한다 — 레거시 동작 재현 / with skew 0 the previous step is refused, reproducing the legacy")
        // req: FR-LOGIN-011
    void skewZeroReproducesLegacyBehaviour() throws Exception {
        // 이 테스트는 결함 L3 이 정확히 무엇이었는지를 문서화한다.
        // This test documents precisely what defect L3 was.
        TotpVerifier legacyEquivalent = verifier(0);
        assertThat(legacyEquivalent.verify(SECRET, codeForOffset(0))).isTrue();
        assertThat(legacyEquivalent.verify(SECRET, codeForOffset(-1))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "1234567", "abcdef", "12345a", "      ", ""})
    @DisplayName("형식이 잘못된 코드는 거절한다 / rejects malformed codes")
        // req: FR-LOGIN-009
    void rejectsMalformedCodes(String code) {
        assertThat(verifier(1).isWellFormed(code)).isFalse();
        assertThat(verifier(1).verify(SECRET, code)).isFalse();
    }

    @Test
    @DisplayName("null 코드는 거절한다 / rejects a null code")
        // req: FR-LOGIN-009
    void rejectsNullCode() {
        assertThat(verifier(1).isWellFormed(null)).isFalse();
        assertThat(verifier(1).verify(SECRET, null)).isFalse();
    }

    @Test
    @DisplayName("6자리 숫자는 형식상 유효하다 / six digits are well-formed")
        // req: FR-LOGIN-009
    void sixDigitsAreWellFormed() {
        assertThat(verifier(1).isWellFormed("000000")).isTrue();
        assertThat(verifier(1).isWellFormed("012345")).isTrue();
        assertThat(verifier(1).isWellFormed("999999")).isTrue();
    }

    @Test
    @DisplayName("선행 0이 있는 코드를 5자리 입력으로 통과시키지 않는다 / a five-digit entry never matches a zero-padded code")
        // source: GoogleOTP.checkCode() — Integer.parseInt made "012345" == "12345"
        // req: FR-LOGIN-009
    void fiveDigitEntryNeverMatchesZeroPaddedCode() {
        // 레거시는 Integer.parseInt 로 비교해 "012345" 와 "12345" 를 동일하게 취급했다.
        // The legacy compared via Integer.parseInt, treating the two as equal.
        assertThat(verifier(1).isWellFormed("12345")).isFalse();
    }

    @Test
    @DisplayName("비밀키가 없으면 거절한다 / rejects when the secret is absent")
        // req: FR-LOGIN-008
    void rejectsMissingSecret() throws Exception {
        String validCode = codeForOffset(0);
        assertThat(verifier(1).verify(null, validCode)).isFalse();
        assertThat(verifier(1).verify("", validCode)).isFalse();
        assertThat(verifier(1).verify("   ", validCode)).isFalse();
    }
}
