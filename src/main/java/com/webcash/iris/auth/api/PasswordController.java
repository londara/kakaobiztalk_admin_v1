package com.webcash.iris.auth.api;

import com.webcash.iris.auth.domain.PasswordChangeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 비밀번호 변경 엔드포인트. / Password change endpoint.
 *
 * <p>이 컨트롤러가 없어서 강제 변경 흐름이 막다른 길이었다. 로그인이
 * {@code passwordChangeRequired = true} 를 반환해도 사용자가 갈 곳이 없었고, 어떤
 * 계정도 Argon2id 해시를 획득할 수 없었다.</p>
 * <p>The absence of this controller is what made the forced-change flow a dead end: login
 * returned {@code passwordChangeRequired = true} with nowhere for the user to go, and no
 * account could obtain an Argon2id hash.</p>
 *
 * <p><b>미인증 접근을 허용한다.</b> 강제 변경 시점에는 세션이 확립되지 않았기 때문이다.
 * 대신 요청 자체에 이메일·현재 비밀번호·OTP 를 모두 요구하여 로그인과 동일한 2요소를
 * 적용한다 — 인증을 건너뛰는 것이 아니라 요청 단위로 수행한다.</p>
 * <p><b>Anonymous access is permitted</b> because no session exists at forced-change time.
 * In exchange the request itself carries email, current password and OTP, applying the
 * same two factors as a login — authentication is performed per request, not skipped.</p>
 *
 * // source: apa_0010_04.act — the legacy password-change popup
 * // req: FR-PWD-001, FR-PWD-002, FR-LOGIN-014, FR-LOGIN-015
 */
@RestController
@RequestMapping("/api/auth/password")
public class PasswordController {

    private final PasswordChangeService passwords;

    /**
     * 컨트롤러 생성. / Creates the controller.
     *
     * @param passwords 비밀번호 변경 서비스 / the password change service
     */
    public PasswordController(PasswordChangeService passwords) {
        this.passwords = passwords;
    }

    /**
     * 비밀번호를 변경한다. / Changes the password.
     *
     * <p>정책 위반은 400 과 위반 목록으로 응답한다. 예외로 처리하면 첫 번째 위반만
     * 전달되어 사용자가 여러 번 시도해야 한다.</p>
     * <p>Policy violations return 400 with the full list. Throwing on the first one would
     * force the user through repeated attempts.</p>
     *
     * @param body    변경 요청 / the change request
     * @param request HTTP 요청 / the HTTP request
     * @return 204 또는 위반 목록이 담긴 400 / 204, or 400 with the violations
     */
    // req: FR-PWD-001, FR-PWD-002, FR-PWD-003
    @PostMapping("/change")
    public ResponseEntity<Map<String, Object>> change(@Valid @RequestBody ChangeRequest body,
                                                     HttpServletRequest request) {
        List<String> violations = passwords.change(
                body.email(), body.currentPassword(), body.otpCode(),
                body.newPassword(), request.getRemoteAddr());

        if (!violations.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("code", "PASSWORD_POLICY_VIOLATION", "violations", violations));
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * 비밀번호 변경 요청. / The password change request.
     *
     * @param email           계정 이메일 / the account email
     * @param currentPassword 현재 비밀번호 / the current password
     * @param otpCode         OTP 코드 / the OTP code
     * @param newPassword     새 비밀번호 / the new password
     */
    // req: FR-PWD-002, TM-L018
    public record ChangeRequest(
            @NotBlank @Email @Size(max = 50) String email,
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Pattern(regexp = "\\d{6}", message = "OTP 코드는 6자리 숫자입니다.") String otpCode,
            @NotBlank @Size(max = 128) String newPassword
    ) {
        /**
         * 자격증명을 제외한 표현을 반환한다. / Returns a representation excluding credentials.
         *
         * @return 마스킹된 문자열 / a masked string
         */
        // req: NFR-SEC-LOG-L01
        @Override
        public String toString() {
            return "ChangeRequest[email=" + email + ", currentPassword=***, otpCode=***, newPassword=***]";
        }
    }
}
