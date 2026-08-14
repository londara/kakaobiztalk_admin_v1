package com.webcash.iris.auth.api;

import com.webcash.iris.auth.domain.OtpRegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 OTP 초기화 엔드포인트. / Operator OTP reset endpoint.
 *
 * <p>단말 분실 시의 <b>유일한 복구 경로</b>다(AMB-L02). 동시에 <b>2요소를 합법적으로
 * 제거하는 경로</b>이므로(TM-L008), 이 시스템에서 가장 민감한 관리 기능이다.</p>
 * <p>The <b>sole recovery path</b> for a lost device (AMB-L02) and simultaneously a
 * <b>sanctioned way to remove someone's second factor</b> (TM-L008) — making it the most
 * sensitive administrative function in the system.</p>
 *
 * <p>경로를 {@code /api/admin/**} 아래 두어 {@code SecurityConfig} 의 운영자 역할 규칙이
 * 적용되게 한다. 컨트롤러 수준 {@code @PreAuthorize} 는 이중 방어이며, 라우팅 규칙이
 * 실수로 완화되어도 남는다.</p>
 * <p>Placed under {@code /api/admin/**} so the operator-role rule in
 * {@code SecurityConfig} applies. The controller-level {@code @PreAuthorize} is defence
 * in depth that survives an accidental loosening of the routing rule.</p>
 *
 * // req: FR-OTP-007, FR-OTP-008, UC-LOGIN-003
 */
@RestController
@RequestMapping("/api/admin/otp")
public class OtpAdminController {

    private final OtpRegistrationService registration;

    /**
     * 컨트롤러 생성. / Creates the controller.
     *
     * @param registration OTP 등록 서비스 / the enrolment service
     */
    public OtpAdminController(OtpRegistrationService registration) {
        this.registration = registration;
    }

    /**
     * 대상 계정의 OTP 등록을 초기화한다. / Resets a target account's OTP enrolment.
     *
     * <p>사유를 <b>필수</b>로 요구한다. 감사 기록에 "누가 누구를 언제"만 남고 "왜"가
     * 없으면, 사후에 정당한 초기화와 침해를 구분할 수 없다(FR-OTP-008).</p>
     * <p>A reason is <b>required</b>: an audit record with who, whom and when but not why
     * cannot afterwards distinguish a legitimate reset from a compromise (FR-OTP-008).</p>
     *
     * @param body      초기화 요청 / the reset request
     * @param principal 인증된 운영자 / the authenticated operator
     * @param request   HTTP 요청 / the HTTP request
     * @return 204 응답 / a 204 response
     */
    // req: FR-OTP-007, FR-OTP-008, AMB-L08
    @PostMapping("/reset")
    @PreAuthorize("hasRole('OPERATOR')")
    public ResponseEntity<Void> reset(@Valid @RequestBody ResetRequest body,
                                      Principal principal,
                                      HttpServletRequest request) {
        registration.resetByOperator(
                principal.getName(), body.email(), body.reason(), request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    /**
     * OTP 초기화 요청. / The OTP reset request.
     *
     * @param email  대상 계정 이메일 / the target account email
     * @param reason 초기화 사유 / the stated reason
     */
    // req: FR-OTP-008
    public record ResetRequest(
            @NotBlank @Email @Size(max = 50) String email,
            @NotBlank(message = "초기화 사유를 입력하세요.") @Size(max = 200) String reason
    ) {
    }
}
