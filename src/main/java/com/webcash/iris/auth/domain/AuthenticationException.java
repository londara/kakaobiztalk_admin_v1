package com.webcash.iris.auth.domain;

/**
 * 인증 실패를 나타내는 예외. / Raised when authentication cannot proceed.
 *
 * <p>메시지에 자격증명·OTP 코드·세션 식별자를 포함하지 않는다. 이 예외는 로그와
 * 감사 기록으로 흘러가므로, 민감값이 섞이면 그대로 저장된다.</p>
 * <p>The message must never carry a credential, OTP code or session id. This
 * exception flows into logs and audit records, so anything embedded here is
 * persisted — which is exactly how the legacy leaked session ids at debug level.</p>
 *
 * // req: NFR-SEC-LOG-L01, NFR-OPS-AUDIT-L02
 */
public class AuthenticationException extends RuntimeException {

    private final AuthFailureReason reason;

    /**
     * 실패 사유로 예외를 생성한다. / Creates the exception for a failure reason.
     *
     * @param reason 실패 사유 / the failure reason
     */
    public AuthenticationException(AuthFailureReason reason) {
        super("Authentication failed: " + reason.name());
        this.reason = reason;
    }

    /**
     * 실패 사유를 반환한다. / Returns the failure reason.
     *
     * @return 실패 사유 / the failure reason
     */
    public AuthFailureReason reason() {
        return reason;
    }
}
