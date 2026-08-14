package com.webcash.iris.auth.api;

import com.webcash.iris.auth.domain.PasswordChangeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 비밀번호 초기화 엔드포인트. / Operator password reset endpoint.
 *
 * <p><b>이 엔드포인트가 ADR-LOGIN-011 교착을 실무적으로 해소한다.</b> 기존 계정은 모두
 * 레거시 SHA-256 해시만 보유하여 로그인이 fail-closed 되는데, 운영자 초기화는 Argon2id
 * 해시를 <b>처음으로</b> 생성하는 경로다. 결정 옵션 B(전면 초기화)에서는 주 수단이고,
 * 옵션 A(로그인 시 상향)에서는 예비 경로이므로, 어느 쪽으로 결정되어도 필요하다.</p>
 * <p><b>This endpoint resolves the ADR-LOGIN-011 deadlock in practice.</b> Every existing
 * account holds only the legacy SHA-256 hash and fails closed at login; an operator reset
 * is the path that creates an Argon2id hash <b>for the first time</b>. It is the primary
 * mechanism under option B and the fallback under option A, so it is needed either way.</p>
 *
 * <p>동시에 <b>가장 위험한 관리 기능</b>이다. 운영자 계정이 침해되면 이 엔드포인트로
 * 임의 계정의 비밀번호를 교체할 수 있다. OTP 초기화(TM-L008)와 조합하면 2요소 전체를
 * 재설정할 수 있으므로, 두 기능의 감사 기록은 함께 검토되어야 한다.</p>
 * <p>It is also the <b>most dangerous administrative function</b>: a compromised operator
 * account can replace any user's password here, and combined with OTP reset (TM-L008) can
 * re-establish both factors. The audit records of the two should be reviewed together.</p>
 *
 * // req: FR-PWD-007, ADR-LOGIN-011, TM-L008
 */
@RestController
@RequestMapping("/api/admin/password")
public class PasswordAdminController {

    private final PasswordChangeService passwords;

    /**
     * 컨트롤러 생성. / Creates the controller.
     *
     * @param passwords 비밀번호 변경 서비스 / the password change service
     */
    public PasswordAdminController(PasswordChangeService passwords) {
        this.passwords = passwords;
    }

    /**
     * 대상 계정의 비밀번호를 임시 비밀번호로 초기화한다.
     * Resets a target account's password to a generated temporary one.
     *
     * <p>응답 본문에 임시 비밀번호가 <b>한 번만</b> 담긴다. 저장되지 않으므로 재조회
     * 경로는 없다. 운영자는 별도 경로로 사용자에게 전달해야 하며, 이 응답을 그대로
     * 이메일이나 메신저로 전달하는 것은 안전하지 않다.</p>
     * <p>The response carries the temporary password <b>once</b>. It is not stored, so
     * there is no retrieval path. The operator must convey it out of band; forwarding this
     * response as-is by email or chat is not safe.</p>
     *
     * @param body      초기화 요청 / the reset request
     * @param principal 인증된 운영자 / the authenticated operator
     * @param request   HTTP 요청 / the HTTP request
     * @return 임시 비밀번호 / the temporary password
     */
    // req: FR-PWD-007, FR-LOGIN-015
    @PostMapping("/reset")
    @PreAuthorize("hasRole('OPERATOR')")
    public ResponseEntity<Map<String, String>> reset(@Valid @RequestBody ResetRequest body,
                                                     Principal principal,
                                                     HttpServletRequest request) {
        String temporary = passwords.resetByOperator(
                principal.getName(), body.email(), body.reason(), request.getRemoteAddr());

        return ResponseEntity.ok(Map.of(
                "temporaryPassword", temporary,
                "note", "사용자에게 별도 경로로 전달하세요. 다음 로그인 시 변경이 강제됩니다."));
    }

    /**
     * 비밀번호 초기화 요청. / The password reset request.
     *
     * @param email  대상 계정 이메일 / the target account email
     * @param reason 초기화 사유 / the stated reason
     */
    // req: FR-PWD-007, NFR-OPS-AUDIT-L01
    public record ResetRequest(
            @NotBlank @Email @Size(max = 50) String email,
            @NotBlank(message = "초기화 사유를 입력하세요.") @Size(max = 200) String reason
    ) {
    }
}
