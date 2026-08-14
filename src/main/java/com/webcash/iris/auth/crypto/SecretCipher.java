package com.webcash.iris.auth.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * OTP 비밀키 저장용 암복호화. / Encryption for OTP secrets at rest.
 *
 * <p>OTP 비밀키가 유출되면 공격자는 <b>영구적으로</b> 유효한 코드를 생성할 수 있고,
 * 사용자는 그 사실을 알 수 없다(TM-L005). 비밀번호와 달리 회전도 사용자 재등록 없이는
 * 불가능하다. 따라서 저장 시 암호화가 필수다.</p>
 * <p>A leaked OTP secret lets an attacker generate valid codes <b>indefinitely and
 * undetectably</b> (TM-L005). Unlike a password it cannot be rotated without the user
 * re-enrolling a device. Encryption at rest is therefore not optional.</p>
 *
 * <p>하네스 §10 은 PII 컬럼에 <b>AES-256-GCM</b> 을 요구한다. 전화번호는 기존 DB 함수
 * ({@code decrypt()}/{@code masking()}, ADR-005)를 그대로 쓰지만, {@code OTP_KEY} 는
 * 레거시에서 암호화되지 않은 것으로 보이며 신규 컬럼도 아니므로 애플리케이션에서
 * 처리한다.</p>
 * <p>Harness §10 mandates <b>AES-256-GCM</b> for PII columns. Phone numbers keep using
 * the existing database functions (ADR-005), but {@code OTP_KEY} appears to be stored
 * unencrypted in the legacy, so it is handled in the application.</p>
 *
 * <p><b>GCM 을 택한 이유:</b> 인증 암호화(AEAD)이므로 복호화 시 무결성이 함께 검증된다.
 * 저장된 값이 조작되면 조용히 잘못된 평문을 내놓는 대신 예외가 발생한다 — OTP 비밀키에
 * 대해서는 이 차이가 중요하다.</p>
 * <p><b>Why GCM:</b> it is authenticated encryption, so integrity is verified on
 * decrypt. A tampered stored value raises an exception rather than silently yielding
 * wrong plaintext — a distinction that matters for an OTP secret.</p>
 *
 * // req: NFR-SEC-PII-L01, ADR-007, harness §10
 */
@Component
public class SecretCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;
    private static final int KEY_BYTES = 32; // AES-256

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    /**
     * 설정에서 키를 주입받아 생성한다. / Creates the cipher from the configured key.
     *
     * <p>키는 Base64 로 인코딩된 32바이트여야 한다. 소스나 기본값에 키를 두지 않으며
     * (ADR-007), 형식이 맞지 않으면 <b>기동 시점에</b> 실패한다 — 첫 등록 시도까지
     * 문제를 미루지 않는다.</p>
     * <p>The key must be 32 Base64-encoded bytes. No key lives in source or in a default
     * (ADR-007), and a malformed key fails <b>at startup</b> rather than deferring the
     * problem to the first registration attempt.</p>
     *
     * @param base64Key Base64 인코딩된 256비트 키 / the Base64-encoded 256-bit key
     */
    // req: ADR-007, NFR-SEC-SECRET-L01
    public SecretCipher(@Value("${iris.auth.otp.secret-key}") String base64Key) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "iris.auth.otp.secret-key is not valid Base64. Provide a 32-byte key "
                            + "from the environment or secret manager (ADR-007).", e);
        }
        if (raw.length != KEY_BYTES) {
            throw new IllegalStateException(
                    "iris.auth.otp.secret-key must decode to " + KEY_BYTES + " bytes (AES-256); got "
                            + raw.length + ".");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    /**
     * 평문을 암호화한다. / Encrypts a plaintext value.
     *
     * <p>IV 는 매 호출마다 새로 생성하여 결과 앞에 붙인다. IV 를 재사용하면 GCM 의
     * 보안이 무너지므로 저장 형식에 IV 를 포함시키는 편이 안전하다.</p>
     * <p>A fresh IV is generated per call and prefixed to the result. Reusing an IV
     * breaks GCM's security, so carrying it in the stored format is the safer design.</p>
     *
     * @param plaintext 평문 / the plaintext
     * @return Base64(IV ‖ ciphertext ‖ tag) / Base64 of IV, ciphertext and tag
     */
    // req: NFR-SEC-PII-L01
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            // 예외 메시지에 평문을 넣지 않는다 — 로그로 흘러간다.
            // The plaintext never enters the message: it would flow into logs.
            throw new IllegalStateException("Failed to encrypt the OTP secret.", e);
        }
    }

    /**
     * 암호문을 복호화한다. / Decrypts a stored value.
     *
     * @param stored 저장된 Base64 값 / the stored Base64 value
     * @return 평문 / the plaintext
     * @throws IllegalStateException 복호화 또는 무결성 검증 실패 시 / on failure or tampering
     */
    // req: NFR-SEC-PII-L01
    public String decrypt(String stored) {
        try {
            byte[] combined = Base64.getDecoder().decode(stored);
            if (combined.length <= IV_BYTES) {
                throw new IllegalStateException("Stored OTP secret is truncated.");
            }
            byte[] iv = new byte[IV_BYTES];
            byte[] ciphertext = new byte[combined.length - IV_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_BYTES);
            System.arraycopy(combined, IV_BYTES, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Failed to decrypt the OTP secret.", e);
        }
    }
}
