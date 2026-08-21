package com.webcash.iris.auth.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link QrRenderer} 단위 테스트. / Unit tests for {@link QrRenderer}.
 *
 * <h2>이 시험이 존재하는 이유 / why this exists</h2>
 * <p>레거시 {@code GoogleOTP.getQRBarcodeURL()} 은 OTP 공유 비밀키를 <b>평문 HTTP 로 구글에</b>
 * 보내는 URL 을 만들었다(결함 L4). 그 메서드는 호출되지 않았을 뿐 삭제되지 않았고 호출 가능했다.
 * 따라서 이 시험의 핵심 단정은 "URI 형식이 맞다"가 아니라 <b>출력에 외부 호스트가 없다</b>는
 * 것이다 — 형식 오류는 등록 실패로 즉시 드러나지만, 외부 전송은 아무 증상 없이 비밀키를 유출한다.</p>
 * <p>The legacy built a URL that shipped the shared OTP secret <b>to Google over cleartext HTTP</b>
 * (defect L4); the method was unused but present and callable. The load-bearing assertion here is
 * therefore not "the URI is well formed" but that <b>no external host appears in the output</b>: a
 * malformed URI fails enrolment visibly, whereas an outbound secret leaks in silence.</p>
 *
 * // source: com/common/irisadmin/util/GoogleOTP.java — getQRBarcodeURL()
 * // req: FR-OTP-003, FR-OTP-004, NFR-SEC-CHANNEL-L01, NFR-SEC-AUTH-L03
 */
class QrRendererTest {

    private static final String ISSUER = "IRIS BizTalk";
    private static final String EMAIL = "operator@webcash.co.kr";
    private static final String SECRET = "JBSWY3DPEHPK3PXP";

    private final QrRenderer renderer = new QrRenderer(ISSUER);

    @Test
    @DisplayName("otpauth 스킴으로 시작한다 / begins with the otpauth scheme")
    // req: FR-OTP-003
    void usesOtpauthScheme() {
        assertThat(renderer.otpauthUri(EMAIL, SECRET)).startsWith("otpauth://totp/");
    }

    @Test
    @DisplayName("외부 호스트를 포함하지 않는다 — 결함 L4 회귀 방지 / contains no external host (L4 regression)")
    // req: NFR-SEC-CHANNEL-L01, NFR-SEC-AUTH-L03
    void containsNoExternalHost() {
        String uri = renderer.otpauthUri(EMAIL, SECRET);

        // 이 단정이 L4 의 재발을 막는다. 비밀키가 제3자에게 도달하면 회전은 재등록 없이
        // 불가능하므로(TM-L005), 되돌릴 수 없는 실패다.
        // This is what prevents L4 from returning. Once the secret reaches a third party it cannot
        // be rotated without re-enrolment (TM-L005) — an unrecoverable failure.
        assertThat(uri)
                .as("the secret must never be handed to an external service")
                .doesNotContain("http://")
                .doesNotContain("https://")
                .doesNotContain("chart.apis.google.com")
                .doesNotContain("chart?cht=qr");
    }

    @Test
    @DisplayName("비밀키를 그대로 싣는다 / carries the secret verbatim")
    // req: FR-OTP-003
    void carriesSecretVerbatim() {
        // Base32 비밀키는 URL 인코딩 대상이 아니다. 인코딩하면 인증 앱이 다른 키를 저장하고,
        // 등록은 성공한 것처럼 보이지만 이후 모든 코드가 틀린다.
        // The Base32 secret must not be URL-encoded: encoding it makes the authenticator store a
        // different key, so enrolment appears to succeed and every subsequent code is wrong.
        assertThat(renderer.otpauthUri(EMAIL, SECRET)).contains("?secret=" + SECRET + "&");
    }

    @Test
    @DisplayName("공백은 %20 으로 인코딩한다 / encodes spaces as %20")
    // req: FR-OTP-003
    void encodesSpaceAsPercent20() {
        String uri = renderer.otpauthUri(EMAIL, SECRET);

        // URLEncoder 는 공백을 '+' 로 만들지만, otpauth 는 폼 인코딩이 아니라 URI 경로·질의이므로
        // '+' 는 문자 그대로 읽힌다. 발급자명이 "IRIS+BizTalk" 로 표시되는 결과가 된다.
        // URLEncoder emits '+' for a space, but otpauth is a URI rather than a form body, so '+'
        // is read literally and the issuer would display as "IRIS+BizTalk".
        assertThat(uri).contains("IRIS%20BizTalk");
        assertThat(uri).doesNotContain("IRIS+BizTalk");
        assertThat(uri).doesNotContain("+");
    }

    @Test
    @DisplayName("라벨은 발급자:이메일 형식이다 / the label is issuer:email")
    // req: FR-OTP-004
    void labelIsIssuerColonEmail() {
        // 인증 앱은 라벨로 계정을 구분한다. 이메일만 넣으면 여러 시스템의 항목이 구분되지 않는다.
        // Authenticator apps distinguish accounts by label; email alone collides across systems.
        assertThat(renderer.otpauthUri(EMAIL, SECRET))
                .contains("otpauth://totp/IRIS%20BizTalk%3A" + EMAIL.replace("@", "%40"));
    }

    @Test
    @DisplayName("알고리즘·자릿수·주기를 명시한다 / states algorithm, digits and period explicitly")
    // req: FR-OTP-003, FR-OTP-004
    void statesEveryParameterExplicitly() {
        String uri = renderer.otpauthUri(EMAIL, SECRET);

        assertThat(uri).contains("&issuer=IRIS%20BizTalk");
        assertThat(uri).contains("&algorithm=SHA1");
        // 서버 상수를 직접 참조한다. 상수만 바뀌고 URI 가 그대로 남으면 앱과 서버가 어긋나
        // 전체 사용자가 동시에 로그인 불가가 된다(RISK-L10) — 부분적 신호가 없는 실패다.
        // Referencing the server constants directly: if a constant changes and the URI does not,
        // app and server disagree and every user is locked out at once (RISK-L10), with no partial
        // signal to warn of it.
        assertThat(uri).contains("&digits=" + TotpVerifier.CODE_DIGITS);
        assertThat(uri).contains("&period=" + TotpVerifier.TIME_STEP_SECONDS);
    }

    @Test
    @DisplayName("발급자명은 주입값을 따른다 / the issuer comes from configuration")
    // req: FR-OTP-004
    void issuerIsConfigurable() {
        // 운영·검증 환경이 같은 인증 앱에 나란히 등록되는 경우를 구분하기 위한 값이다.
        // Distinguishes production from staging when both are enrolled in one authenticator app.
        QrRenderer staging = new QrRenderer("IRIS Staging");
        String uri = staging.otpauthUri(EMAIL, SECRET);

        assertThat(uri).contains("IRIS%20Staging");
        assertThat(uri).doesNotContain("BizTalk");
    }
}
