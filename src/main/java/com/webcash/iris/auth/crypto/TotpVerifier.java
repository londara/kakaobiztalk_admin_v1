package com.webcash.iris.auth.crypto;

import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.time.TimeProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Google OTP (TOTP) 코드 검증. / Google OTP (TOTP) code verification.
 *
 * <p>ADR-LOGIN-010 에 따라 레거시 {@code GoogleOTP.java} 를 이식하지 않고 유지관리되는
 * 라이브러리를 사용한다. 알고리즘 자체(HMAC-SHA1, 30초 주기, 6자리)는 레거시도
 * RFC 6238 을 정확히 따랐으나, 그 주변 선택에 결함이 있었다.</p>
 * <p>Per ADR-LOGIN-010 this uses a maintained library rather than porting the legacy
 * {@code GoogleOTP.java}. The algorithm itself — HMAC-SHA1, 30-second step, 6 digits —
 * was correct in the legacy; the defects were all in the choices around it.</p>
 *
 * <h2>레거시 결함 L3 대응 / Fixes legacy defect L3</h2>
 * <p>레거시는 {@code int window = 0;} 으로 시간 오차 허용을 <b>0</b> 으로 두었고
 * ({@code int window = 3;} 이 주석 처리되어 있었다), 단말 시계가 조금이라도 어긋난
 * 사용자는 로그인할 수 없었다. RFC 6238 은 ±1 스텝을 권고한다.</p>
 * <p>The legacy set clock-skew tolerance to <b>zero</b> ({@code int window = 0;}, with
 * {@code int window = 3;} commented out just above it), so any device clock drift made
 * login impossible. RFC 6238 recommends ±1 step.</p>
 *
 * <p>±1 을 택하고 그 이상은 택하지 않는다. 창을 넓히면 관측된 코드의 재사용 가능
 * 시간이 함께 늘어난다(TM-L004). 90초는 허용 범위지만 필요 이상이다.</p>
 * <p>±1 and no wider: enlarging the window also enlarges the period in which an
 * observed code remains replayable (TM-L004). 90 seconds would be tolerable but is
 * more than the problem requires.</p>
 *
 * // source: com/common/irisadmin/util/GoogleOTP.java — checkCode(), `int window = 0`
 * // req: FR-LOGIN-009, FR-LOGIN-011, NFR-SEC-AUTH-L03, ADR-LOGIN-010
 */
@Component
public class TotpVerifier {

    /** 코드 자릿수. / Number of digits in a code. */
    public static final int CODE_DIGITS = 6;
    /** 시간 스텝(초). Google Authenticator 기본값. / Time step in seconds, the Authenticator default. */
    public static final int TIME_STEP_SECONDS = 30;
    /** 허용 시간 오차(스텝). RFC 6238 권고. / Allowed skew in steps, per RFC 6238. */
    public static final int ALLOWED_SKEW_STEPS = 1;

    private final CodeVerifier verifier;

    /**
     * 검증기를 구성한다. / Configures the verifier.
     *
     * @param timeProvider 시각 공급자 (테스트 대체 가능) / time provider, replaceable in tests
     * @param skewSteps    허용 시간 오차 / allowed skew in time steps
     */
    // req: FR-LOGIN-011, NFR-SEC-AUTH-L03
    public TotpVerifier(TimeProvider timeProvider,
                        @Value("${iris.auth.otp.allowed-skew-steps:1}") int skewSteps) {
        DefaultCodeVerifier delegate =
                new DefaultCodeVerifier(new DefaultCodeGenerator(HashingAlgorithm.SHA1), timeProvider);
        delegate.setTimePeriod(TIME_STEP_SECONDS);
        delegate.setAllowedTimePeriodDiscrepancy(skewSteps);
        this.verifier = delegate;
    }

    /**
     * 코드 형식이 유효한지 검사한다. / Whether the code is well-formed.
     *
     * <p>검증 <b>전에</b> 형식을 확인한다. 레거시는 {@code Integer.parseInt} 로 확인했고
     * 그 결과 {@code "012345"} 와 {@code "12345"} 가 같은 값으로 취급되어 5자리 입력이
     * 6자리 코드와 일치할 수 있었다. 여기서는 자릿수까지 정확히 요구한다.</p>
     * <p>Checked <b>before</b> verification. The legacy used {@code Integer.parseInt},
     * which made {@code "012345"} and {@code "12345"} equivalent, so a five-digit entry
     * could match a six-digit code. Here the digit count is exact.</p>
     *
     * @param code 입력 코드 / the submitted code
     * @return 형식 유효 여부 / true when the code is exactly six digits
     */
    // source: GoogleOTP.checkCode() — `long otpnum = Integer.parseInt(userCode)`
    // req: FR-LOGIN-009
    public boolean isWellFormed(String code) {
        if (code == null || code.length() != CODE_DIGITS) {
            return false;
        }
        for (int i = 0; i < code.length(); i++) {
            if (!Character.isDigit(code.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 코드를 검증한다. / Verifies a code against a secret.
     *
     * @param base32Secret Base32 로 인코딩된 OTP 비밀키 / the Base32-encoded OTP secret
     * @param code         입력 코드 / the submitted code
     * @return 일치 여부 / true when the code is valid for the current window
     */
    // req: FR-LOGIN-011, NFR-SEC-AUTH-L03
    public boolean verify(String base32Secret, String code) {
        if (base32Secret == null || base32Secret.isBlank() || !isWellFormed(code)) {
            return false;
        }
        return verifier.isValidCode(base32Secret, code);
    }
}
