package com.webcash.iris.auth.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link SecretCipher} 단위 테스트. / Unit tests for {@link SecretCipher}.
 *
 * <p>OTP 비밀키는 유출 시 <b>영구적으로</b> 유효한 코드 생성을 허용하고 사용자가 이를
 * 알 수 없다(TM-L005). 비밀번호와 달리 회전도 재등록 없이는 불가능하다. 따라서 저장
 * 암호화의 정확성은 이 모듈에서 가장 되돌리기 어려운 실패 지점 중 하나다.</p>
 * <p>A leaked OTP secret permits indefinite, undetectable code generation (TM-L005) and,
 * unlike a password, cannot be rotated without re-enrolment. The correctness of
 * encryption at rest is therefore one of the least recoverable failure points here.</p>
 *
 * // req: NFR-SEC-PII-L01, ADR-007, harness §10
 */
class SecretCipherTest {

    /** 테스트 전용 키. 운영 키는 환경에서 주입된다(ADR-007). */
    /** Test-only key; the production key is injected from the environment (ADR-007). */
    private static final String KEY_32_BYTES =
            Base64.getEncoder().encodeToString(new byte[32]);

    private static final String SECRET = "MFRGGZDFMZTWQ2LKNNWG23TPOBYXE43U";

    private final SecretCipher cipher = new SecretCipher(KEY_32_BYTES);

    @Test
    @DisplayName("암호화 후 복호화하면 원본이 나온다 / round-trips a value")
        // req: NFR-SEC-PII-L01
    void roundTrip() {
        String encrypted = cipher.encrypt(SECRET);
        assertThat(cipher.decrypt(encrypted)).isEqualTo(SECRET);
    }

    @Test
    @DisplayName("암호문은 평문을 포함하지 않는다 / the ciphertext does not contain the plaintext")
        // req: NFR-SEC-PII-L01
    void ciphertextDoesNotContainPlaintext() {
        assertThat(cipher.encrypt(SECRET)).doesNotContain(SECRET);
    }

    @Test
    @DisplayName("같은 값을 두 번 암호화하면 결과가 다르다 / encrypting twice yields different ciphertexts")
        // req: NFR-SEC-PII-L01
    void sameInputProducesDifferentCiphertext() {
        // IV 를 매번 새로 생성하기 때문이다. IV 를 재사용하면 GCM 의 보안이 무너진다.
        // Because a fresh IV is generated per call; reusing an IV breaks GCM's security.
        String first = cipher.encrypt(SECRET);
        String second = cipher.encrypt(SECRET);

        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo(SECRET);
        assertThat(cipher.decrypt(second)).isEqualTo(SECRET);
    }

    @Test
    @DisplayName("조작된 암호문은 예외가 된다 — 무결성 검증 / a tampered ciphertext raises rather than yielding wrong plaintext")
        // req: NFR-SEC-PII-L01
    void tamperedCiphertextIsRejected() {
        // GCM 을 택한 핵심 이유. CBC 라면 조작된 값이 조용히 잘못된 평문을 낼 수 있다.
        // The central reason for choosing GCM: under CBC a tampered value could silently
        // decrypt to wrong plaintext.
        byte[] raw = Base64.getDecoder().decode(cipher.encrypt(SECRET));
        raw[raw.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(raw);

        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("다른 키로는 복호화되지 않는다 / a different key cannot decrypt")
        // req: ADR-007
    void differentKeyCannotDecrypt() {
        byte[] otherKeyBytes = new byte[32];
        otherKeyBytes[0] = 0x7F;
        SecretCipher other = new SecretCipher(Base64.getEncoder().encodeToString(otherKeyBytes));

        String encrypted = cipher.encrypt(SECRET);
        assertThatThrownBy(() -> other.decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("잘린 암호문은 예외가 된다 / a truncated value raises")
    void truncatedValueRejected() {
        String tooShort = Base64.getEncoder().encodeToString(new byte[8]);
        assertThatThrownBy(() -> cipher.decrypt(tooShort))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-base64!!", "c2hvcnQ="})
    @DisplayName("잘못된 키 형식은 기동 시점에 실패한다 / a malformed key fails at construction")
        // req: ADR-007
    void malformedKeyFailsAtConstruction(String badKey) {
        // 첫 등록 시도까지 문제를 미루지 않는다 — 기동 시점에 드러나야 한다.
        // The problem must surface at startup, not at the first enrolment attempt.
        assertThatThrownBy(() -> new SecretCipher(badKey))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("32바이트가 아닌 키는 거절한다 / a key that is not 32 bytes is refused")
        // req: ADR-007, harness §10 (AES-256)
    void keyMustBe32Bytes() {
        assertThatThrownBy(() -> new SecretCipher(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    @DisplayName("예외 메시지에 평문이 노출되지 않는다 / exception messages never carry the plaintext")
        // req: NFR-SEC-LOG-L01
    void exceptionMessagesDoNotLeakPlaintext() {
        String encrypted = cipher.encrypt(SECRET);
        byte[] raw = Base64.getDecoder().decode(encrypted);
        raw[raw.length - 1] ^= 0x01;

        assertThatThrownBy(() -> cipher.decrypt(Base64.getEncoder().encodeToString(raw)))
                .hasMessageNotContaining(SECRET);
    }
}
