package com.webcash.iris.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * {@link AccountPolicy} 단위 테스트. / Unit tests for {@link AccountPolicy}.
 *
 * <p>이 테스트의 목적은 정책이 <b>거절한다</b>는 것을 증명하는 것이다.
 * TEST-PLAN-LOGIN §1.1: "통제가 무언가를 거절함을 증명하지 못하면 그 통제는 없는
 * 것으로 취급한다." 레거시는 강도 검사·IP 허용목록·OTP 시간 오차 허용을 모두
 * 코드로 갖고 있었으나 주석 처리되어 동작하지 않았고, 정상 경로 테스트만으로는
 * 그 상태가 그대로 통과한다.</p>
 * <p>These tests exist to prove the policy <b>denies</b>. Per TEST-PLAN-LOGIN §1.1, a
 * control that cannot be shown to refuse is treated as absent — the legacy had the
 * strength check, IP allowlist and OTP skew window all present in code but commented
 * out, and positive-path tests would have passed against every one of them.</p>
 *
 * // req: FR-LOGIN-003, FR-LOGIN-010, FR-LOGIN-012, FR-LOGIN-013, FR-LOGIN-014, FR-LOGIN-015
 */
class AccountPolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 14);
    private static final Clock FIXED =
            Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    private final AccountPolicy policy = new AccountPolicy(FIXED, 90, 90);

    private UserAccount account(int loginAttempt, int otpFail, AccountStatus status,
                                LocalDate lastLogin, LocalDate lastPwdChange, boolean initialPwd) {
        return new UserAccount("user@example.com", "$argon2id$hash", null, "SECRETKEY",
                loginAttempt, otpFail, status, lastLogin, lastPwdChange, initialPwd, false);
    }

    private UserAccount healthy() {
        return account(0, 0, AccountStatus.ACTIVE, TODAY.minusDays(1), TODAY.minusDays(1), false);
    }

    @Nested
    @DisplayName("잠금 판정 / lockout")
    class Lockout {

        @Test
        @DisplayName("정상 계정은 통과한다 / a healthy account passes")
        void healthyAccountPasses() {
            assertThatCode(() -> policy.assertNotLocked(healthy())).doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "비밀번호 실패 {0}회 → 잠금 {1}")
        @CsvSource({"0,false", "1,false", "4,false", "5,true", "6,true"})
        @DisplayName("비밀번호 실패 5회에서 잠긴다 / locks at the 5th password failure")
            // req: FR-LOGIN-003
        void locksAtFifthPasswordFailure(int attempts, boolean locked) {
            UserAccount acc = account(attempts, 0, AccountStatus.ACTIVE,
                    TODAY.minusDays(1), TODAY.minusDays(1), false);
            if (locked) {
                assertThatThrownBy(() -> policy.assertNotLocked(acc))
                        .isInstanceOf(AuthenticationException.class)
                        .extracting(e -> ((AuthenticationException) e).reason())
                        .isEqualTo(AuthFailureReason.ACCOUNT_LOCKED);
            } else {
                assertThatCode(() -> policy.assertNotLocked(acc)).doesNotThrowAnyException();
            }
        }

        @Test
        @DisplayName("OTP 실패 5회도 독립적으로 잠근다 / OTP failures lock independently")
            // req: FR-LOGIN-010
        void locksOnOtpFailuresIndependently() {
            UserAccount acc = account(0, 5, AccountStatus.ACTIVE,
                    TODAY.minusDays(1), TODAY.minusDays(1), false);
            assertThatThrownBy(() -> policy.assertNotLocked(acc))
                    .isInstanceOf(AuthenticationException.class);
        }
    }

    @Nested
    @DisplayName("휴면 판정 / dormancy")
    class Dormancy {

        @ParameterizedTest(name = "최종 로그인 {0}일 전 → 휴면 {1}")
        @CsvSource({"1,false", "89,false", "90,true", "365,true"})
        @DisplayName("90일 경계에서 휴면이 된다 / becomes dormant at the 90-day boundary")
            // req: FR-LOGIN-012
        void dormantAtNinetyDays(int daysAgo, boolean dormant) {
            UserAccount acc = account(0, 0, AccountStatus.ACTIVE,
                    TODAY.minusDays(daysAgo), TODAY.minusDays(1), false);
            assertThat(policy.isDormant(acc)).isEqualTo(dormant);
        }

        @Test
        @DisplayName("최초 로그인(이력 없음)은 휴면이 아니다 / a first-ever login is not dormant")
            // req: FR-LOGIN-012
        void firstEverLoginIsNotDormant() {
            UserAccount acc = account(0, 0, AccountStatus.ACTIVE, null, TODAY.minusDays(1), false);
            assertThat(policy.isDormant(acc)).isFalse();
        }

        @Test
        @DisplayName("휴면 계정은 거절된다 / a dormant account is refused")
            // req: FR-LOGIN-012
        void dormantAccountRefused() {
            UserAccount acc = account(0, 0, AccountStatus.ACTIVE,
                    TODAY.minusDays(120), TODAY.minusDays(1), false);
            assertThatThrownBy(() -> policy.assertUsable(acc))
                    .isInstanceOf(AuthenticationException.class)
                    .extracting(e -> ((AuthenticationException) e).reason())
                    .isEqualTo(AuthFailureReason.ACCOUNT_DORMANT);
        }
    }

    @Nested
    @DisplayName("가입상태 판정 / membership status")
    class Status {

        @ParameterizedTest
        @EnumSource(value = AccountStatus.class,
                names = {"AWAITING_APPROVAL", "APPLICATION_PENDING", "SUSPENDED", "TERMINATED"})
        @DisplayName("차단 상태는 모두 거절된다 / every blocking status is refused")
            // req: FR-LOGIN-013
        void blockingStatusesRefused(AccountStatus status) {
            UserAccount acc = account(0, 0, status, TODAY.minusDays(1), TODAY.minusDays(1), false);
            assertThatThrownBy(() -> policy.assertUsable(acc))
                    .isInstanceOf(AuthenticationException.class)
                    .extracting(e -> ((AuthenticationException) e).reason())
                    .isEqualTo(AuthFailureReason.STATUS_BLOCKED);
        }

        @Test
        @DisplayName("정상 상태만 통과한다 / only the active status passes")
        void activePasses() {
            assertThatCode(() -> policy.assertUsable(healthy())).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("알 수 없는 코드는 예외가 된다 — 기본 통과 금지 / an unknown code fails closed")
            // req: FR-LOGIN-013
        void unknownCodeFailsClosed() {
            assertThatThrownBy(() -> AccountStatus.fromCode("7"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("비밀번호 주기 / password age")
    class PasswordAge {

        @ParameterizedTest(name = "최종 변경 {0}일 전 → 변경 강제 {1}")
        @CsvSource({"1,false", "89,false", "90,true", "200,true"})
        @DisplayName("90일 경과 시 변경을 강제한다 / forces a change after 90 days")
            // req: FR-LOGIN-014
        void forcesChangeAfterNinetyDays(int daysAgo, boolean required) {
            UserAccount acc = account(0, 0, AccountStatus.ACTIVE,
                    TODAY.minusDays(1), TODAY.minusDays(daysAgo), false);
            assertThat(policy.passwordChangeRequired(acc)).isEqualTo(required);
        }

        @Test
        @DisplayName("초기 비밀번호는 즉시 변경을 강제한다 / an initial password forces a change")
            // req: FR-LOGIN-015
        void initialPasswordForcesChange() {
            UserAccount acc = account(0, 0, AccountStatus.ACTIVE,
                    TODAY.minusDays(1), TODAY.minusDays(1), true);
            assertThat(policy.passwordChangeRequired(acc)).isTrue();
        }

        @Test
        @DisplayName("변경 이력이 없으면 변경을 강제한다 / no change history forces a change")
            // req: FR-LOGIN-014
        void missingHistoryForcesChange() {
            UserAccount acc = account(0, 0, AccountStatus.ACTIVE, TODAY.minusDays(1), null, false);
            assertThat(policy.passwordChangeRequired(acc)).isTrue();
        }
    }
}
