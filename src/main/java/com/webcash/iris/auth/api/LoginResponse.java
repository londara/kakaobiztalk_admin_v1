package com.webcash.iris.auth.api;

/**
 * 로그인 응답. / Login response.
 *
 * <p>레거시는 {@code CHNG_PWD} 한 필드만 반환하고 나머지는 세션에 담았다. 같은 방식을
 * 유지한다 — 응답 본문에 개인정보나 권한 상세를 싣지 않는다.</p>
 * <p>The legacy returned a single {@code CHNG_PWD} field and kept everything else in
 * the session. That approach is retained: the response body carries no personal data
 * and no authorization detail.</p>
 *
 * @param passwordChangeRequired 비밀번호 변경 필요 여부 / whether a password change is required first
 * @param operator               운영자 여부 / whether the principal is an operator
 * @param displacedSession       기존 세션이 종료되었는지 / whether an earlier session was terminated
 *
 * // source: WSVC.apc_login_proc.xml — out rule: CHNG_PWD
 * // req: FR-LOGIN-001, FR-LOGIN-014, FR-LOGIN-016, FR-LOGIN-018
 */
public record LoginResponse(
        boolean passwordChangeRequired,
        boolean operator,
        boolean displacedSession
) {
}
