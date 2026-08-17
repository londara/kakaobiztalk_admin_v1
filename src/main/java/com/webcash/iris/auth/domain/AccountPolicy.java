package com.webcash.iris.auth.domain;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 계정 상태 정책 판정. / Evaluates account-state policy.
 *
 * <p>잠금·휴면·가입상태·비밀번호 주기 판정을 한곳에 모은다. 레거시는 이 판정들이
 * 로그인 처리 JSP 안에 순서대로 흩어져 있었고, 그 <b>순서</b> 자체가 보안 속성이었다.
 * 특히 잠금 판정은 자격증명 검증보다 <b>먼저</b> 수행되어야 한다 — 그렇지 않으면
 * 잠긴 계정이 비밀번호 정답 여부를 알려주는 오라클이 된다.</p>
 * <p>Collects the lockout, dormancy, status and password-age decisions in one place.
 * In the legacy these were scattered in order through the login JSP, and that
 * <b>order</b> is itself a security property: lockout must be evaluated
 * <b>before</b> any credential is verified, otherwise a locked account becomes an
 * oracle telling an attacker whether a guessed password was correct.</p>
 *
 * // source: apc_login_proc_act.jsp — LOGIN_ATTEMPT / OTP_FAIL_CNT / ADM_00004 / JNNG_STTS / LAST_CHNG_PWD_DT
 * // req: FR-LOGIN-003, FR-LOGIN-010, FR-LOGIN-012, FR-LOGIN-013, FR-LOGIN-014, FR-LOGIN-015
 */
@Component
public class AccountPolicy {

    /** 실패 허용 횟수. 초과 시 계정 잠금. / Failure attempts tolerated before lockout. */
    // source: apc_login_proc_act.jsp — `if (loginAttempt >= 5)`
    public static final int MAX_FAILURES = 5;

    private final Clock clock;
    private final int dormancyDays;
    private final int passwordMaxAgeDays;

    /**
     * 정책 임계값을 주입받아 생성한다. / Creates the policy with injected thresholds.
     *
     * @param clock              현재 시각 공급자 (테스트 대체 가능) / clock, replaceable in tests
     * @param dormancyDays       휴면 판정 일수 / days of inactivity before dormancy
     * @param passwordMaxAgeDays 비밀번호 최대 사용 일수 / maximum password age in days
     */
    public AccountPolicy(Clock clock,
                         @Value("${iris.auth.dormancy-days:90}") int dormancyDays,
                         @Value("${iris.auth.password-max-age-days:90}") int passwordMaxAgeDays) {
        this.clock = clock;
        this.dormancyDays = dormancyDays;
        this.passwordMaxAgeDays = passwordMaxAgeDays;
    }

    /**
     * 자격증명 검증 <b>이전</b>에 수행해야 하는 판정. 잠금 여부만 본다.
     * The check that must run <b>before</b> credential verification — lockout only.
     *
     * <p>비밀번호 실패 횟수와 OTP 실패 횟수를 각각 독립적으로 본다. 레거시도 두 카운터를
     * 따로 유지했으나 OTP 카운터 확인이 코드 검증 이후에 있었다.</p>
     * <p>Password and OTP failure counters are evaluated independently. The legacy
     * also kept two counters, but checked the OTP one only after verifying the code.</p>
     *
     * @param account 대상 계정 / the account
     * @throws AuthenticationException 잠금 상태일 때 / when the account is locked
     */
    // req: FR-LOGIN-003, FR-LOGIN-010
    public void assertNotLocked(UserAccount account) {
        if (account.loginAttempt() >= MAX_FAILURES || account.otpFailCount() >= MAX_FAILURES) {
            throw new AuthenticationException(AuthFailureReason.ACCOUNT_LOCKED);
        }
    }

    /**
     * 자격증명 검증 <b>이후</b>에 수행하는 계정 상태 판정.
     * Account-state checks performed <b>after</b> credentials verify.
     *
     * <p>휴면 → 가입상태 순으로 판정한다. 레거시와 동일한 순서를 유지하여 동일 입력에
     * 대해 동일한 사유가 나오도록 한다.</p>
     * <p>Dormancy then membership status, in the legacy's order, so that identical
     * input yields the identical reason — a spec-parity requirement.</p>
     *
     * @param account 대상 계정 / the account
     * @throws AuthenticationException 휴면 또는 상태 차단일 때 / when dormant or status-blocked
     */
    // req: FR-LOGIN-012, FR-LOGIN-013
    public void assertUsable(UserAccount account) {
        if (isDormant(account)) {
            throw new AuthenticationException(AuthFailureReason.ACCOUNT_DORMANT);
        }
        if (!account.status().loginPermitted()) {
            throw new AuthenticationException(AuthFailureReason.STATUS_BLOCKED);
        }
    }

    /**
     * 장기 미사용 여부를 판정한다. / Whether the account is dormant.
     *
     * <p>최종 로그인 일자가 없는 계정(최초 로그인)은 휴면으로 보지 않는다. 레거시도
     * {@code LAST_LOGIN_DT} 가 빈 값이면 검사를 건너뛰었다.</p>
     * <p>An account with no recorded last login — a first-ever login — is not dormant.
     * The legacy also skipped the check when {@code LAST_LOGIN_DT} was blank.</p>
     *
     * @param account 대상 계정 / the account
     * @return 휴면 여부 / true when inactive for the configured period
     */
    // source: apc_login_proc_act.jsp — 장기 미사용 체크, ADM_00004
    // req: FR-LOGIN-012
    public boolean isDormant(UserAccount account) {
        LocalDate lastLogin = account.lastLoginDate();
        if (lastLogin == null) {
            return false;
        }
        return ChronoUnit.DAYS.between(lastLogin, today()) >= dormancyDays;
    }

    /**
     * 비밀번호 변경을 강제해야 하는지 판정한다.
     * Whether a password change must be forced before access is granted.
     *
     * <p>초기 비밀번호이거나 변경 주기를 초과한 경우 강제한다. 두 조건은 레거시에서
     * 각각 별개의 분기였고 결과는 동일하게 {@code CHNG_PWD=Y} 였다.</p>
     * <p>Forced when the account still holds its initial password, or when the
     * password exceeds its maximum age. Both were separate branches in the legacy
     * producing the same {@code CHNG_PWD=Y} outcome.</p>
     *
     * @param account 대상 계정 / the account
     * @return 변경 강제 여부 / true when a password change is required first
     */
    // source: apc_login_proc_act.jsp — LAST_CHNG_PWD_DT / PWD_INIT_YN branches
    // req: FR-LOGIN-014, FR-LOGIN-015
    public boolean passwordChangeRequired(UserAccount account) {
        // PM 결정(2026-08-17): 비밀번호 변경 페이지는 <b>오직</b> 다음 두 조건이 모두
        // 참일 때만 표시한다 — PWD_INIT_YN='N' 이고 마지막 변경일이 90일 이상 경과.
        // 그 외(PWD_INIT_YN='Y', 변경 이력 없음, 90일 미만)는 정상 로그인으로 처리하여
        // InstitutionPage 로 진입한다.
        //
        // PM ruling (2026-08-17): the change page is shown ONLY when BOTH hold — PWD_INIT_YN='N'
        // AND the last change is at least 90 days old. Anything else (PWD_INIT_YN='Y', no change
        // history, or younger than 90 days) is a normal login that proceeds to InstitutionPage.
        //
        // account.initialPassword() 는 매퍼에서 (PWD_INIT_YN = 'N') 로 매핑된다.
        // account.initialPassword() maps to (PWD_INIT_YN = 'N') in the mapper.
        if (!account.initialPassword()) {
            return false;
        }
        LocalDate lastChange = account.lastPasswordChangeDate();
        if (lastChange == null) {
            // 변경 이력이 없으면 강제하지 않는다(사용자 요구: 90일 경과 조건 미충족).
            // No change history → not forced (the 90-day condition is not met).
            return false;
        }
        return ChronoUnit.DAYS.between(lastChange, today()) >= passwordMaxAgeDays;
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
