package com.webcash.iris.auth.domain;

import java.util.Arrays;

/**
 * 가입상태 코드 (JNNG_STTS). / Membership status code.
 *
 * <p>레거시는 문자열 리터럴을 직접 비교했다. 코드값과 의미를 열거형으로 고정하여
 * 알 수 없는 값이 조용히 통과하지 못하게 한다.</p>
 * <p>The legacy compared raw string literals inline. Fixing the code values in an
 * enum means an unrecognised status cannot silently pass the check — it fails
 * closed instead.</p>
 *
 * // source: apc_login_proc_act.jsp — 가입상태 확인 block
 * // req: FR-LOGIN-013
 */
public enum AccountStatus {

    /** 승인대기 / awaiting approval — legacy WCI00020. */
    AWAITING_APPROVAL("0", false),
    /** 정상 / active. */
    ACTIVE("1", true),
    /** 신청대기 / application pending — legacy WCI00019. */
    APPLICATION_PENDING("2", false),
    /** 중지 / suspended — legacy WCI00078. */
    SUSPENDED("8", false),
    /** 해지 / terminated — legacy WCI00021. */
    TERMINATED("9", false);

    private final String code;
    private final boolean loginPermitted;

    AccountStatus(String code, boolean loginPermitted) {
        this.code = code;
        this.loginPermitted = loginPermitted;
    }

    /**
     * DB 컬럼값을 반환한다. / Returns the database column value.
     *
     * @return JNNG_STTS 코드 / the JNNG_STTS code
     */
    public String code() {
        return code;
    }

    /**
     * 이 상태에서 로그인이 허용되는지 반환한다. / Whether login is permitted in this state.
     *
     * @return 허용 여부 / true when login may proceed
     */
    public boolean loginPermitted() {
        return loginPermitted;
    }

    /**
     * 코드값을 열거형으로 변환한다. 알 수 없는 코드는 로그인 불가로 취급한다.
     * Resolves a code to its enum constant. An unknown code is rejected rather
     * than defaulted to active — failing closed is the safe direction here.
     *
     * @param code JNNG_STTS 컬럼값 / the JNNG_STTS column value
     * @return 해당 상태 / the matching status
     * @throws IllegalArgumentException 알 수 없는 코드일 때 / when the code is unrecognised
     */
    // req: FR-LOGIN-013
    public static AccountStatus fromCode(String code) {
        return Arrays.stream(values())
                .filter(s -> s.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown JNNG_STTS: " + code));
    }
}
