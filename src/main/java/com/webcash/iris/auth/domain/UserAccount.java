package com.webcash.iris.auth.domain;

import java.time.LocalDate;

/**
 * 인증에 필요한 사용자 계정 정보. / User account data required for authentication.
 *
 * <p>레거시 {@code USER_LDGR_R006} 조회 결과 중 인증 판정에 실제로 쓰이는 필드만
 * 담는다. 이름·휴대폰번호 등 개인정보는 인증 판정에 불필요하므로 이 모델에 넣지
 * 않는다 — 필요 없는 PII 를 메모리와 로그에 실어 보내지 않기 위한 것이다.</p>
 * <p>Holds only the fields from the legacy {@code USER_LDGR_R006} lookup that the
 * authentication decision actually uses. Personal fields such as name and mobile
 * number are deliberately excluded: they play no part in the decision, and
 * carrying PII that nothing needs is how it ends up in a log line.</p>
 *
 * @param email             로그인 이메일 / login email (EML)
 * @param passwordHash      Argon2id 해시 (신규 스키마) / Argon2id hash, new scheme
 * @param legacyPasswordHash 레거시 SHA-256 해시 / legacy unsalted SHA-256 hash
 * @param otpKey            Google OTP 비밀키, 미등록이면 null / OTP secret, null when unregistered
 * @param loginAttempt      비밀번호 실패 횟수 / consecutive password failures
 * @param otpFailCount      OTP 실패 횟수 / consecutive OTP failures
 * @param status            가입상태 / membership status
 * @param lastLoginDate     최종 로그인 일자 / last successful login date
 * @param lastPasswordChangeDate 최종 비밀번호 변경 일자 / last password change date
 * @param initialPassword   초기 비밀번호 여부 (PWD_INIT_YN='Y', PM 2026-08-17 반전) / still holding the initial password
 * @param operator          운영자 권한 보유 (GRP_0) / holds the operator group
 * @param institutionCode   소속 이용기관 코드 / the 이용기관 the user belongs to
 *
 * // source: apc_login_proc_act.jsp + IDO USER_LDGR_R006
 * // req: FR-LOGIN-003/005/010/012/013/014/015/018, FR-TEN-001
 */
public record UserAccount(
        String email,
        String passwordHash,
        String legacyPasswordHash,
        String otpKey,
        int loginAttempt,
        int otpFailCount,
        AccountStatus status,
        LocalDate lastLoginDate,
        LocalDate lastPasswordChangeDate,
        boolean initialPassword,
        boolean operator,
        String institutionCode
) {

    /**
     * 신규 스키마 해시를 보유하는지 반환한다.
     * Whether this account has already been migrated to the new hash scheme.
     *
     * @return Argon2id 해시 보유 여부 / true when an Argon2id hash exists
     */
    // req: FR-LOGIN-005, ADR-LOGIN-011
    public boolean hasModernHash() {
        return passwordHash != null && !passwordHash.isBlank();
    }

    /**
     * OTP 가 등록되어 있는지 반환한다. / Whether an OTP key is registered.
     *
     * @return 등록 여부 / true when an OTP secret is present
     */
    // req: FR-LOGIN-008
    public boolean hasOtpRegistered() {
        return otpKey != null && !otpKey.isBlank();
    }
}
