package com.webcash.iris.auth.api;

import com.webcash.iris.auth.domain.AuthFailureReason;
import com.webcash.iris.auth.domain.AuthenticationException;
import com.webcash.iris.auth.domain.OtpRegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OTP 등록 엔드포인트. / OTP enrolment endpoints.
 *
 * <p>이 컨트롤러는 로그인 모듈의 <b>닫힌 고리를 여는</b> 부분이다. 로그인은 OTP 등록을
 * 요구하지만(FR-LOGIN-008), 등록 경로가 없으면 신규 계정은 영구히 진입할 수 없다.</p>
 * <p>This controller is what <b>opens the closed loop</b>: login requires a registered
 * OTP (FR-LOGIN-008), so without an enrolment path a new account can never get in.</p>
 *
 * <h2>2단계 구조와 대기 비밀키 보관 / Two-step flow and pending-secret custody</h2>
 * <p>발급({@code begin})과 확인({@code confirm})을 분리하고, 그 사이의 비밀키는
 * <b>HTTP 세션</b>에 보관한다. 클라이언트가 되돌려 보낸 값을 신뢰하면, 공격자가 자신이
 * 아는 비밀키를 제출해 계정에 심을 수 있다.</p>
 * <p>Issue and confirm are separate steps, and the secret in between is held in the
 * <b>HTTP session</b>. Trusting a client-returned value would let an attacker submit a
 * secret they already know and plant it on the account.</p>
 *
 * // source: apm_1001_02_view.jsp, apm_1001_03_view.jsp, apm_1001_02_c001_act.jsp
 * // req: FR-OTP-001…009
 */
@RestController
@RequestMapping("/api/auth/otp")
public class OtpController {

    /** 대기 중 비밀키 세션 키. / Session attribute holding the pending secret. */
    // source: apm_1001_03_r001_act.jsp — session attribute `_OTP_REG_STATUS`
    private static final String PENDING_SECRET = "otp.pending.secret";
    /** 대기 중 등록 대상 이메일. / Session attribute holding the enrolling account. */
    private static final String PENDING_EMAIL = "otp.pending.email";

    private final OtpRegistrationService registration;

    /**
     * 컨트롤러 생성. / Creates the controller.
     *
     * @param registration OTP 등록 서비스 / the enrolment service
     */
    public OtpController(OtpRegistrationService registration) {
        this.registration = registration;
    }

    /**
     * 등록을 시작하고 비밀키와 QR URI 를 반환한다.
     * Begins enrolment, returning the secret and QR URI.
     *
     * @param body    시작 요청 / the begin request
     * @param request HTTP 요청 / the HTTP request
     * @return 비밀키와 otpauth URI / the secret and otpauth URI
     */
    // req: FR-OTP-001, FR-OTP-002, FR-OTP-003, FR-OTP-004
    @PostMapping("/registration/begin")
    public ResponseEntity<Map<String, String>> begin(@Valid @RequestBody BeginRequest body,
                                                     HttpServletRequest request) {
        var pending = registration.begin(body.email(), body.password(), request.getRemoteAddr());

        HttpSession session = request.getSession(true);
        session.setAttribute(PENDING_SECRET, pending.secret());
        session.setAttribute(PENDING_EMAIL, body.email());

        // 비밀키는 여기서 <b>한 번만</b> 노출된다. 등록 후에는 어떤 API 도 반환하지 않는다
        // (NFR-SEC-PII-L01).
        // The secret is disclosed here once only; no API returns it after enrolment.
        return ResponseEntity.ok(Map.of(
                "secret", pending.secret(),
                "otpauthUri", pending.otpauthUri()));
    }

    /**
     * 확인 코드를 검증하여 등록을 완료한다. / Completes enrolment by verifying a code.
     *
     * @param body    확인 요청 / the confirm request
     * @param request HTTP 요청 / the HTTP request
     * @return 204 응답 / a 204 response
     */
    // req: FR-OTP-005, FR-OTP-006
    @PostMapping("/registration/confirm")
    public ResponseEntity<Void> confirm(@Valid @RequestBody ConfirmRequest body,
                                        HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            // 세션이 없거나 만료됨 — 처음부터 다시 시작해야 한다 (UC-LOGIN-002 E-4).
            // No pending enrolment, or it expired: the user restarts (UC-LOGIN-002 E-4).
            throw new AuthenticationException(AuthFailureReason.OPERATION_NOT_PERMITTED);
        }
        String secret = (String) session.getAttribute(PENDING_SECRET);
        String email = (String) session.getAttribute(PENDING_EMAIL);
        if (secret == null || email == null) {
            throw new AuthenticationException(AuthFailureReason.OPERATION_NOT_PERMITTED);
        }

        registration.complete(email, secret, body.code(), request.getRemoteAddr());

        // 성공 후 즉시 제거한다. 남겨두면 세션 안에 평문 비밀키가 계속 존재한다.
        // Removed immediately on success; leaving it would keep a plaintext secret in session.
        session.removeAttribute(PENDING_SECRET);
        session.removeAttribute(PENDING_EMAIL);

        return ResponseEntity.noContent().build();
    }

    /**
     * 등록 시작 요청. / The enrolment begin request.
     *
     * @param email    계정 이메일 / the account email
     * @param password 비밀번호 / the password
     */
    // req: FR-OTP-001, TM-L006
    public record BeginRequest(
            @NotBlank @Email @Size(max = 50) String email,
            @NotBlank @Size(max = 128) String password
    ) {
        /**
         * 비밀번호를 제외한 표현을 반환한다. / Returns a representation excluding the password.
         *
         * @return 마스킹된 문자열 / a masked string
         */
        // req: NFR-SEC-LOG-L01
        @Override
        public String toString() {
            return "BeginRequest[email=" + email + ", password=***]";
        }
    }

    /**
     * 등록 확인 요청. / The enrolment confirm request.
     *
     * @param code 6자리 확인 코드 / the six-digit confirmation code
     */
    // req: FR-OTP-005
    public record ConfirmRequest(
            @NotBlank @Pattern(regexp = "\\d{6}", message = "OTP 코드는 6자리 숫자입니다.") String code
    ) {
    }
}
