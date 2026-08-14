package com.webcash.iris.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 로그인 요청. / Login request.
 *
 * <p>레거시 화면과 동일하게 이메일·비밀번호·OTP 를 한 번에 받는다.</p>
 * <p>Takes email, password and OTP together, as the legacy screen did.</p>
 *
 * <p><b>{@code toString()} 을 재정의하지 않는다.</b> record 의 기본 구현은 모든 필드를
 * 출력하므로, 이 객체가 로그·예외 메시지에 들어가면 비밀번호와 OTP 코드가 그대로
 * 남는다. 아래 재정의는 그 사고를 구조적으로 막는다(NFR-SEC-LOG-L01).</p>
 * <p>The record's default {@code toString()} prints every field, so this object
 * reaching a log line or exception message would expose the password and OTP code.
 * The override below removes that possibility rather than relying on care.</p>
 *
 * @param email    로그인 이메일 / the login email
 * @param password 비밀번호 / the password
 * @param otpCode  OTP 코드 6자리 / the six-digit OTP code
 *
 * // source: apm_0001_01_view.jsp — EML / PWD / OTP_CD inputs
 * // req: FR-LOGIN-001, FR-LOGIN-009, NFR-SEC-LOG-L01
 */
public record LoginRequest(

        @NotBlank(message = "아이디를 입력하세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 50)
        String email,

        // 최대 길이만 검증한다. 최소 길이·강도는 변경 시점에 PasswordPolicy 가 판정하며,
        // 로그인 시점에 강도를 따지면 기존 계정이 로그인조차 못 하게 된다.
        // Only the maximum is validated here. Minimum length and strength are enforced
        // at change time by PasswordPolicy; applying them at login would lock out
        // existing accounts that predate the policy.
        @NotBlank(message = "비밀번호를 입력하세요.")
        @Size(max = 128)
        String password,

        // req: FR-LOGIN-009 — exactly six digits, rejected before verification
        @NotBlank(message = "OTP 코드를 입력하세요.")
        @Pattern(regexp = "\\d{6}", message = "OTP 코드는 6자리 숫자입니다.")
        String otpCode
) {

    /**
     * 자격증명을 제외한 표현을 반환한다. / Returns a representation excluding credentials.
     *
     * @return 이메일만 포함한 문자열 / a string containing only the email
     */
    // req: NFR-SEC-LOG-L01
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=***, otpCode=***]";
    }
}
