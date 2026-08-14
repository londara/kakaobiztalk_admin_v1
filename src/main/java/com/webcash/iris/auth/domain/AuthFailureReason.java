package com.webcash.iris.auth.domain;

/**
 * 인증 실패 사유. / Authentication failure reason.
 *
 * <p>{@link #INVALID_CREDENTIALS} 는 계정 미존재와 비밀번호 불일치를 <b>같은 사유</b>로
 * 취급한다. 이는 계정 존재 여부 노출(account enumeration)을 막기 위한 의도적 설계이며,
 * 두 경우가 서로 다른 응답을 내지 않도록 단일 경로로 강제한다.</p>
 * <p>{@link #INVALID_CREDENTIALS} deliberately covers <b>both</b> "no such account"
 * and "wrong password". Keeping them indistinguishable is the point: separate
 * reasons would let an attacker enumerate valid accounts, so the two cases share
 * one code path and one response.</p>
 *
 * // source: apc_login_proc_act.jsp — WCI00018 used for both cases
 * // req: FR-LOGIN-002, NFR-USE-L02
 */
public enum AuthFailureReason {

    /** 이메일 또는 비밀번호 불일치 / email unknown or password mismatch — legacy WCI00018. */
    INVALID_CREDENTIALS,
    /** 계정 잠금 / account locked after 5 failures — legacy WCI00600. */
    ACCOUNT_LOCKED,
    /** 장기 미사용 / dormant for 90 days or more — legacy ADM_00004. */
    ACCOUNT_DORMANT,
    /** 가입상태로 인한 차단 / blocked by membership status — legacy WCI00019/20/21/78. */
    STATUS_BLOCKED,
    /** OTP 미등록 / no OTP key registered — legacy ADM_00001. */
    OTP_NOT_REGISTERED,
    /** OTP 이미 등록됨 — 재등록 불가 / OTP already registered, re-enrolment refused — legacy ADM_00026. */
    OTP_ALREADY_REGISTERED,
    /** 허용되지 않은 작업 / the operation is not permitted for this principal. */
    OPERATION_NOT_PERMITTED,
    /** OTP 코드 형식 오류 / OTP code malformed — legacy ADM_00023. */
    OTP_MALFORMED,
    /** OTP 코드 불일치 / OTP code mismatch — legacy ADM_00003. */
    OTP_MISMATCH,
    /** 요청 한도 초과 / rate limit exceeded. */
    RATE_LIMITED,
    /** 허용되지 않은 접속 IP / source address not allowlisted. */
    IP_NOT_ALLOWED
}
