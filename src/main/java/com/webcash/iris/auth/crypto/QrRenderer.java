package com.webcash.iris.auth.crypto;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * OTP 등록용 {@code otpauth://} URI 생성. / Builds the {@code otpauth://} URI for enrolment.
 *
 * <h2>레거시 결함 L4 대응 / Fixes legacy defect L4</h2>
 * <p>레거시 {@code GoogleOTP.getQRBarcodeURL()} 은 다음 URL 을 만들었다:</p>
 * <pre>http://chart.apis.google.com/chart?cht=qr&amp;chl=otpauth://totp/user@host%3Fsecret%3D&lt;SECRET&gt;</pre>
 * <p>즉 <b>OTP 공유 비밀키를 평문 HTTP 로 제3자(구글)에게 전송</b>했다. 레거시에서는
 * 이 메서드가 호출되지 않았지만 남아 있었고 호출 가능했다. 또한 해당 Google Charts
 * API 는 이미 종료되었다.</p>
 * <p>That is, it <b>transmitted the shared OTP secret in cleartext over HTTP to a third
 * party</b>. The method was unused in the legacy but present and callable — and that
 * Google Charts API has since been shut down.</p>
 *
 * <p>이 클래스는 URI <b>문자열만</b> 생성한다. 외부 호출은 하지 않는다. QR 이미지는
 * 사용자의 브라우저에서 렌더링되며, 비밀키는 TLS 로 보호된 응답에 한 번 실릴 뿐
 * 어떤 외부 호스트에도 도달하지 않는다.</p>
 * <p>This class builds the URI <b>string only</b> and makes no outbound call. The QR
 * image is rendered in the user's own browser, so the secret crosses the network once
 * inside a TLS-protected response and reaches no external host.</p>
 *
 * // source: com/common/irisadmin/util/GoogleOTP.java — getQRBarcodeURL()
 * // req: FR-OTP-003, FR-OTP-004, NFR-SEC-CHANNEL-L01
 */
@Component
public class QrRenderer {

    private final String issuer;

    /**
     * 발급자명을 주입받아 생성한다. / Creates the renderer with the issuer label.
     *
     * @param issuer OTP 앱에 표시될 발급자명 / the issuer shown in the authenticator app
     */
    public QrRenderer(@Value("${iris.auth.otp.issuer:IRIS BizTalk}") String issuer) {
        this.issuer = issuer;
    }

    /**
     * {@code otpauth://totp/...} URI 를 생성한다. / Builds the {@code otpauth://totp/...} URI.
     *
     * <p>파라미터를 명시적으로 모두 기입한다. 생략하면 앱의 기본값에 의존하게 되고,
     * 서버 검증 설정과 앱 설정이 어긋나면 사용자 전원이 동시에 로그인 불가가 된다
     * (RISK-L10 — 부분적 신호 없이 전면 장애가 되는 실패 유형).</p>
     * <p>Every parameter is stated explicitly. Omitting them defers to the app's
     * defaults, and any mismatch between app and server configuration locks out every
     * user at once — a failure mode with no partial signal (RISK-L10).</p>
     *
     * @param email        계정 이메일 / the account email
     * @param base32Secret Base32 인코딩된 비밀키 / the Base32-encoded secret
     * @return otpauth URI / the otpauth URI
     */
    // req: FR-OTP-003, FR-OTP-004, NFR-SEC-AUTH-L03
    public String otpauthUri(String email, String base32Secret) {
        String label = encode(issuer + ":" + email);
        return "otpauth://totp/" + label
                + "?secret=" + base32Secret
                + "&issuer=" + encode(issuer)
                + "&algorithm=SHA1"
                + "&digits=" + TotpVerifier.CODE_DIGITS
                + "&period=" + TotpVerifier.TIME_STEP_SECONDS;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
