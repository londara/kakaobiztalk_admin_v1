package com.webcash.iris.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.webcash.iris.auth.config.OtpDevBypass;
import com.webcash.iris.auth.crypto.PasswordHasher;
import com.webcash.iris.auth.crypto.SecretCipher;
import com.webcash.iris.auth.crypto.TotpVerifier;
import com.webcash.iris.auth.infra.db.UserMapper;
import com.webcash.iris.common.audit.AuditEvent;
import com.webcash.iris.common.audit.AuditService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.mock.env.MockEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * {@link AuthenticationService} 단위 테스트 — 7개 종료 경로 전수.
 * Unit tests for {@link AuthenticationService} — all exit paths.
 *
 * <p><b>이 테스트가 없어서 결함 SR-01 이 통과했다.</b> Sprint L1 에서
 * {@code AuthenticationService} 에 대한 단위 테스트가 작성되지 않았고, 그 결과 OTP 가
 * 등록된 계정이 코드 검증 없이 통과하는 단일 요소 인증 경로가 리뷰를 통과했다.
 * 산문으로만 주장된 통제는 통제가 아니다(TEST-PLAN-LOGIN §1.1).</p>
 * <p><b>The absence of this test is why defect SR-01 survived.</b> Sprint L1 wrote no
 * unit test for {@code AuthenticationService}, so a single-factor path — an account with
 * a registered OTP passing without code verification — went through review. A control
 * asserted only in prose is not a control (TEST-PLAN-LOGIN §1.1).</p>
 *
 * // source: apc_login_proc_act.jsp
 * // req: FR-LOGIN-001…005, FR-LOGIN-009…015, FR-LOGIN-020, NFR-SEC-AUTH-L01
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthenticationServiceTest {

    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "Tr0ubled-Kettle!9";
    private static final String HASH = "$argon2id$v=19$m=19456,t=2,p=1$stub";
    private static final String OTP_SECRET = "MFRGGZDFMZTWQ2LKNNWG23TPOBYXE43U";
    private static final String OTP_CODE = "123456";
    private static final String IP = "10.1.2.3";
    private static final String CORRELATION = "corr-1";
    private static final String INSTITUTION = "K00001";

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 14);
    private static final Clock FIXED =
            Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    @Mock private UserMapper users;
    @Mock private PasswordHasher hasher;
    @Mock private TotpVerifier totp;
    @Mock private SecretCipher cipher;
    @Mock private OtpReplayGuard replayGuard;
    @Mock private IpAllowlistPolicy ipAllowlist;
    @Mock private AdminLoginNotifier notifier;
    @Mock private AuditService audit;

    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        // 개발용 OTP 우회는 <b>비활성</b> 인스턴스를 주입한다. 이 테스트 클래스가 검증하는
        // 것은 우회가 없을 때의 인증 규칙이며, 특히 SingleFactorPrevention 은 비밀번호만으로
        // 통과하는 경로가 없다는 것을 단정한다 — 우회가 켜진 채로는 그 단정이 무의미해진다.
        // 우회 자체의 동작과 안전장치는 OtpDevBypassTest 가 검증한다.
        // An <b>inactive</b> dev bypass is injected. This class verifies the authentication rules
        // as they stand without it — SingleFactorPrevention in particular asserts that no path
        // completes on a password alone, which the bypass would render vacuous. The bypass's own
        // behaviour and its safety interlock are verified by OtpDevBypassTest.
        service = new AuthenticationService(
                users, hasher, totp, cipher, replayGuard,
                new AccountPolicy(FIXED, 90, 90), ipAllowlist, notifier, audit,
                new OtpDevBypass(false, new MockEnvironment()));

        // 저장된 OTP 비밀키는 암호화되어 있고 서비스가 검증 직전에 복호화한다. 테스트에서는
        // 항등 복호화를 사용해 각 테스트가 OTP_SECRET 을 그대로 다루게 한다 — 암호화 자체는
        // SecretCipherTest 가 검증하며, 여기서 중복 검증할 대상이 아니다.
        // The stored OTP secret is encrypted and the service decrypts it just before
        // verification. Tests use an identity decrypt so each case can keep working with
        // OTP_SECRET directly; the cipher itself is covered by SecretCipherTest and is not
        // what these tests are exercising.
        when(cipher.decrypt(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        // 재사용 방지는 기본 통과. 재생 시나리오는 해당 테스트에서 개별로 뒤집는다.
        // Replay protection passes by default; the replay scenario overrides it locally.
        when(replayGuard.tryConsume(anyString(), anyString())).thenReturn(true);
    }

    private UserAccount account(int loginAttempt, int otpFail, AccountStatus status,
                                LocalDate lastLogin, LocalDate lastPwdChange,
                                boolean initialPwd, String otpKey, String modernHash) {
        return new UserAccount(EMAIL, modernHash, "legacy-sha256", otpKey,
                loginAttempt, otpFail, status, lastLogin, lastPwdChange, initialPwd, false,
                INSTITUTION);
    }

    private UserAccount healthy() {
        return account(0, 0, AccountStatus.ACTIVE,
                TODAY.minusDays(1), TODAY.minusDays(1), false, OTP_SECRET, HASH);
    }

    /** 정상 경로가 성립하도록 협력자를 준비한다. / Arranges collaborators for the happy path. */
    private void arrangeHappyPath(UserAccount account) {
        when(users.findByEmail(EMAIL)).thenReturn(account);
        when(hasher.matches(PASSWORD, HASH)).thenReturn(true);
        when(totp.isWellFormed(OTP_CODE)).thenReturn(true);
        when(totp.verify(OTP_SECRET, OTP_CODE)).thenReturn(true);
        when(users.isOperator(EMAIL)).thenReturn(false);
    }

    private AuthFailureReason reasonOf(Throwable t) {
        return ((AuthenticationException) t).reason();
    }

    // -------------------------------------------------------------------------
    // 성공 경로 / success paths
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("정상 인증이 성공한다 / a valid login succeeds")
        // req: FR-LOGIN-001
    void successfulLogin() {
        arrangeHappyPath(healthy());

        var result = service.authenticate(EMAIL, PASSWORD, OTP_CODE, IP, CORRELATION);

        assertThat(result.passwordChangeRequired()).isFalse();
        assertThat(result.account().email()).isEqualTo(EMAIL);
        verify(users).touchLastLogin(EMAIL);
        verify(audit).recordAuth(eq(EMAIL), eq(AuditEvent.ACTION_LOGIN),
                eq(AuditEvent.Outcome.OK), anyString(), eq(IP), eq(CORRELATION));
    }

    @Test
    @DisplayName("운영자 여부가 결과에 반영된다 / the operator flag reaches the result")
        // req: FR-LOGIN-018
    void operatorFlagPropagates() {
        arrangeHappyPath(healthy());
        when(users.isOperator(EMAIL)).thenReturn(true);

        assertThat(service.authenticate(EMAIL, PASSWORD, OTP_CODE, IP, CORRELATION).operator())
                .isTrue();
    }

    @Test
    @DisplayName("실패 카운터가 0이 아니면 초기화한다 / resets counters when non-zero")
        // req: FR-LOGIN-004
    void resetsCountersWhenNonZero() {
        arrangeHappyPath(account(2, 1, AccountStatus.ACTIVE,
                TODAY.minusDays(1), TODAY.minusDays(1), false, OTP_SECRET, HASH));

        service.authenticate(EMAIL, PASSWORD, OTP_CODE, IP, CORRELATION);

        verify(users).resetFailureCounters(EMAIL);
    }

    @Test
    @DisplayName("카운터가 0이면 불필요한 쓰기를 하지 않는다 / does not write when counters are already zero")
        // req: FR-LOGIN-004
    void skipsResetWhenCountersZero() {
        arrangeHappyPath(healthy());

        service.authenticate(EMAIL, PASSWORD, OTP_CODE, IP, CORRELATION);

        verify(users, never()).resetFailureCounters(anyString());
    }

    // -------------------------------------------------------------------------
    // 단일 요소 인증 방지 — SR-01 회귀 / single-factor prevention — SR-01 regression
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("SR-01 회귀: 비밀번호만으로는 인증되지 않는다 / SR-01: a password alone never authenticates")
    class SingleFactorPrevention {

        @Test
        @DisplayName("비밀번호가 맞고 OTP 가 틀리면 실패한다 / correct password with wrong OTP fails")
            // req: NFR-SEC-AUTH-L01, FR-LOGIN-010
        void correctPasswordWrongOtpFails() {
            when(users.findByEmail(EMAIL)).thenReturn(healthy());
            when(hasher.matches(PASSWORD, HASH)).thenReturn(true);
            when(totp.isWellFormed(OTP_CODE)).thenReturn(true);
            when(totp.verify(OTP_SECRET, OTP_CODE)).thenReturn(false);
            when(users.incrementOtpFailCount(EMAIL)).thenReturn(1);

            assertThatThrownBy(() -> service.authenticate(EMAIL, PASSWORD, OTP_CODE, IP, CORRELATION))
                    .isInstanceOf(AuthenticationException.class)
                    .extracting(AuthenticationServiceTest.this::reasonOf)
                    .isEqualTo(AuthFailureReason.OTP_MISMATCH);

            // 인증이 완료되지 않았으므로 최종 로그인 시각을 갱신하지 않는다.
            // Authentication did not complete, so last-login must not be touched.
            verify(users, never()).touchLastLogin(anyString());
        }

        @Test
        @DisplayName("OTP 검증기가 반드시 호출된다 / the OTP verifier is always consulted")
            // req: NFR-SEC-AUTH-L01
        void otpVerifierIsAlwaysConsulted() {
            arrangeHappyPath(healthy());

            service.authenticate(EMAIL, PASSWORD, OTP_CODE, IP, CORRELATION);

            // 이 검증이 SR-01 의 본질이다 — 예전 구현은 이 호출을 건너뛰었다.
            // This assertion is the heart of SR-01: the earlier implementation skipped it.
            verify(totp).verify(OTP_SECRET, OTP_CODE);
        }
    }

    // -------------------------------------------------------------------------
    // 실패 경로 / failure paths
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("계정이 없으면 일반 실패로 처리한다 / an unknown account yields the generic failure")
        // req: FR-LOGIN-002
    void unknownAccountYieldsGenericFailure() {
        when(users.findByEmail(EMAIL)).thenReturn(null);

        assertThatThrownBy(() -> service.authenticate(EMAIL, PASSWORD, OTP_CODE, IP, CORRELATION))
                .isInstanceOf(AuthenticationException.class)
                .extracting(this::reasonOf)
                .isEqualTo(AuthFailureReason.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("비밀번호 불일치는 계정 미존재와 같은 사유를 낸다 / a wrong password yields the same reason as an unknown account")
        // req: FR-LOGIN-002 — account enumeration prevention
    void wrongPasswordIsIndistinguishableFromUnknownAccount() {
        when(users.findByEmail(EMAIL)).thenReturn(healthy());
        when(hasher.matches(PASSWORD, HASH)).thenReturn(false);
        when(users.incrementLoginAttempt(EMAIL)).thenReturn(1);

        assertThatThrownBy(() -> service.authenticate(EMAIL, PASSWORD, OTP_CODE, IP, CORRELATION))
                .isInstanceOf(AuthenticationException.class)
                .extracting(this::reasonOf)
                .isEqualTo(AuthFailureReason.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("비밀번호 실패 시 카운터를 증가시킨다 / increments the counter on a password failure")
        // req: FR-LOGIN-003
    void incrementsCounterOnPasswordFailure() {
        when(users.findByEmail(EMAIL)).thenReturn(healthy());
        when(hasher.matches(PASSWORD, HASH)).thenReturn(false);
        when(users.incrementLoginAttempt(EMAIL)).thenReturn(3);

        assertThatThrownBy(() -> service.authenticate(EMAIL, PASSWORD, OTP_CODE, IP, CORRELATION))
                .isInstanceOf(AuthenticationException.class);

        verify(users).incrementLoginAttempt(EMAIL);
    }

    @Test
    @DisplayName("5회째 실패에서 잠금을 감사 기록한다 / audits the lockout at the fifth failure")
        // req: FR-LOGIN-003, NFR-OPS-AUDIT-L01
    void auditsLockoutAtThreshold() {
        when(users.findByEmail(EMAIL)).thenReturn(healthy());
        when(hasher.matches(PASSWORD, HASH)).thenReturn(false);
        when(users.incrementLoginAttempt(EMAIL)).thenReturn(5);

        assertThatThrownBy(() -> service.authenticate(EMAIL, PASSWORD, OTP_CODE, IP, CORRELATION))
                .isInstanceOf(AuthenticationException.class);

        verify(audit).recordAuth(eq(EMAIL), eq(AuditEvent.ACTION_LOCKOUT),
                eq(AuditEvent.Outcome.DENIED), anyString(), eq(IP), eq(CORRELATION));
    }

    @Test
    @DisplayName("잠긴 계정은 비밀번호 검증 전에 거절한다 / a locked account is refused before the password is verified")
        // req: FR-LOGIN-003, SEC-L03
    void lockedAccountRefusedBeforePasswordCheck() {
        when(users.findByEmail(EMAIL)).thenReturn(account(5, 0, AccountStatus.ACTIVE,
                TODAY.minusDays(1), TODAY.minusDays(1), false, OTP_SECRET, HASH));

        assertThatThrownBy(() -> service.authenticate(EMAIL, PASSWORD, OTP_CODE, IP, CORRELATION))
                .isInstanceOf(AuthenticationException.class)
                .extracting(this::reasonOf)
                .isEqualTo(AuthFailureReason.ACCOUNT_LOCKED);

        // 잠긴 계정이 비밀번호 정답 여부를 알려주는 오라클이 되어서는 안 된다.
        // A locked account must not act as an oracle for whether a password was correct.
        verify(hasher, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("OTP 미등록 계정은 등록으로 유도한다 / an account without OTP is directed to registration")
        // req: FR-LOGIN-008
    void otpNotRegistered() {
        when(users.findByEmail(EMAIL)).thenReturn(account(0, 0, AccountStatus.ACTIVE,
                TODAY.minusDays(1), TODAY.minusDays(1), false, null, HASH));
        when(hasher.matches(PASSWORD, HASH)).thenReturn(true);

        assertThatThrownBy(() -> service.authenticate(EMAIL, PASSWORD, OTP_CODE, IP, CORRELATION))
                .isInstanceOf(AuthenticationException.class)
                .extracting(this::reasonOf)
                .isEqualTo(AuthFailureReason.OTP_NOT_REGISTERED);
    }

    @Test
    @DisplayName("형식 오류 OTP 는 카운터를 증가시키지 않는다 / a malformed OTP does not increment the counter")
        // req: FR-LOGIN-009
    void malformedOtpDoesNotIncrementCounter() {
        when(users.findByEmail(EMAIL)).thenReturn(healthy());
        when(hasher.matches(PASSWORD, HASH)).thenReturn(true);
        when(totp.isWellFormed("12345")).thenReturn(false);

        assertThatThrownBy(() -> service.authenticate(EMAIL, PASSWORD, "12345", IP, CORRELATION))
                .isInstanceOf(AuthenticationException.class)
                .extracting(this::reasonOf)
                .isEqualTo(AuthFailureReason.OTP_MALFORMED);

        // 오타로 계정이 잠기는 것은 과도하다. 레거시도 ADM_00023 에서 카운터를 건드리지 않았다.
        // Locking an account over a typo is disproportionate; the legacy also left the
        // counter alone at ADM_00023.
        verify(users, never()).incrementOtpFailCount(anyString());
    }

    @Test
    @DisplayName("휴면 계정은 거절한다 / a dormant account is refused")
        // req: FR-LOGIN-012
    void dormantAccountRefused() {
        arrangeHappyPath(account(0, 0, AccountStatus.ACTIVE,
                TODAY.minusDays(120), TODAY.minusDays(1), false, OTP_SECRET, HASH));

        assertThatThrownBy(() -> service.authenticate(EMAIL, PASSWORD, OTP_CODE, IP, CORRELATION))
                .isInstanceOf(AuthenticationException.class)
                .extracting(this::reasonOf)
                .isEqualTo(AuthFailureReason.ACCOUNT_DORMANT);
    }

    @Test
    @DisplayName("해지 계정은 거절한다 / a terminated account is refused")
        // req: FR-LOGIN-013
    void terminatedAccountRefused() {
        arrangeHappyPath(account(0, 0, AccountStatus.TERMINATED,
                TODAY.minusDays(1), TODAY.minusDays(1), false, OTP_SECRET, HASH));

        assertThatThrownBy(() -> service.authenticate(EMAIL, PASSWORD, OTP_CODE, IP, CORRELATION))
                .isInstanceOf(AuthenticationException.class)
                .extracting(this::reasonOf)
                .isEqualTo(AuthFailureReason.STATUS_BLOCKED);
    }

    // -------------------------------------------------------------------------
    // 비밀번호 변경 강제 / forced password change
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("비밀번호 만료 시 세션을 확립하지 않는다 / an expired password does not establish a session")
        // req: FR-LOGIN-014
    void expiredPasswordRequiresChangeWithoutSession() {
        arrangeHappyPath(account(0, 0, AccountStatus.ACTIVE,
                TODAY.minusDays(1), TODAY.minusDays(120), false, OTP_SECRET, HASH));

        var result = service.authenticate(EMAIL, PASSWORD, OTP_CODE, IP, CORRELATION);

        assertThat(result.passwordChangeRequired()).isTrue();
        verify(users, never()).touchLastLogin(anyString());
    }

    @Test
    @DisplayName("초기 비밀번호는 변경을 강제한다 / an initial password forces a change")
        // req: FR-LOGIN-015
    void initialPasswordRequiresChange() {
        arrangeHappyPath(account(0, 0, AccountStatus.ACTIVE,
                TODAY.minusDays(1), TODAY.minusDays(1), true, OTP_SECRET, HASH));

        assertThat(service.authenticate(EMAIL, PASSWORD, OTP_CODE, IP, CORRELATION)
                .passwordChangeRequired()).isTrue();
    }

    // -------------------------------------------------------------------------
    // ADR-LOGIN-011 — 마이그레이션되지 않은 계정 / unmigrated accounts
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Argon2id 해시가 없는 계정은 인증되지 않는다 / an account without an Argon2id hash cannot authenticate")
        // req: ADR-LOGIN-011, RISK-L01
    void unmigratedAccountCannotAuthenticate() {
        // ADR-LOGIN-011 결정 전까지 레거시 해시 경로는 차단된다. 이는 기능 공백이며,
        // 조용히 통과시키는 것보다 명시적으로 실패하는 편이 낫다.
        // The legacy hash path is blocked until ADR-LOGIN-011 is ruled on. This is a
        // functional gap, and failing explicitly beats passing silently.
        when(users.findByEmail(EMAIL)).thenReturn(account(0, 0, AccountStatus.ACTIVE,
                TODAY.minusDays(1), TODAY.minusDays(1), false, OTP_SECRET, null));
        when(hasher.legacyVerificationEnabled()).thenReturn(false);
        when(users.incrementLoginAttempt(EMAIL)).thenReturn(1);

        assertThatThrownBy(() -> service.authenticate(EMAIL, PASSWORD, OTP_CODE, IP, CORRELATION))
                .isInstanceOf(AuthenticationException.class)
                .extracting(this::reasonOf)
                .isEqualTo(AuthFailureReason.INVALID_CREDENTIALS);

        verify(hasher, never()).matchesLegacy(anyString(), any());
    }

    // -------------------------------------------------------------------------
    // 감사 / auditing
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("모든 거절이 감사 기록된다 / every denial is audited")
        // req: NFR-OPS-AUDIT-L01
    void everyDenialIsAudited() {
        when(users.findByEmail(EMAIL)).thenReturn(null);

        assertThatThrownBy(() -> service.authenticate(EMAIL, PASSWORD, OTP_CODE, IP, CORRELATION))
                .isInstanceOf(AuthenticationException.class);

        verify(audit).recordAuth(eq(EMAIL), eq(AuditEvent.ACTION_LOGIN),
                eq(AuditEvent.Outcome.DENIED), anyString(), eq(IP), eq(CORRELATION));
    }
}
