package com.webcash.iris.auth.domain;

import com.webcash.iris.auth.config.OtpDevBypass;
import com.webcash.iris.auth.crypto.PasswordHasher;
import com.webcash.iris.auth.crypto.SecretCipher;
import com.webcash.iris.auth.crypto.TotpVerifier;
import com.webcash.iris.auth.infra.db.UserMapper;
import com.webcash.iris.common.audit.AuditEvent;
import com.webcash.iris.common.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 인증 처리. / Login authentication.
 *
 * <p>레거시 {@code apc_login_proc_act.jsp} 의 판정 순서를 보존하되, 순서 자체가
 * 보안 속성인 지점을 명시적으로 고정한다.</p>
 * <p>Preserves the decision order of the legacy {@code apc_login_proc_act.jsp},
 * while making explicit the points where that order <b>is</b> the security property.</p>
 *
 * <h2>판정 순서 / Order of checks</h2>
 * <ol>
 *   <li>계정 조회 — 없으면 일반 실패 / lookup, generic failure when absent</li>
 *   <li><b>잠금</b> — 자격증명 검증보다 먼저 / <b>lockout</b>, before any credential check</li>
 *   <li>비밀번호 / password</li>
 *   <li>OTP (Google OTP, ±1 스텝) / OTP, Google Authenticator, ±1 step</li>
 *   <li>카운터 초기화 / reset counters</li>
 *   <li>휴면 → 가입상태 / dormancy, then membership status</li>
 *   <li>비밀번호 주기 / password age</li>
 * </ol>
 *
 * <p>2번이 3번보다 앞에 오는 것이 핵심이다. 순서가 뒤집히면 잠긴 계정에 대해서도
 * 비밀번호 정답 여부가 응답 차이로 드러나 오라클이 된다.</p>
 * <p>Step 2 preceding step 3 is the crux. Reversed, a locked account would still
 * reveal whether a guessed password was correct, turning the lockout into an oracle.</p>
 *
 * // source: apc_login_proc_act.jsp
 * // req: FR-LOGIN-001…005, FR-LOGIN-012…015, FR-LOGIN-020
 */
@Service
public class AuthenticationService {

    private final UserMapper users;
    private final PasswordHasher hasher;
    private final TotpVerifier totp;
    private final SecretCipher cipher;
    private final OtpReplayGuard replayGuard;
    private final AccountPolicy accountPolicy;
    private final IpAllowlistPolicy ipAllowlist;
    private final AdminLoginNotifier notifier;
    private final AuditService audit;
    private final OtpDevBypass otpDevBypass;

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

    /**
     * OTP 비밀키가 저장 시 암호화되어 있는지 여부. / Whether the stored OTP key is encrypted at rest.
     *
     * <p>포트의 기본 보안 모델은 {@code true}(AES-256-GCM, NFR-SEC-PII-L01)다. 그러나
     * 레거시({@code GoogleOTP})는 {@code OTP_KEY} 를 <b>평문 Base32</b>로 저장했고, 실제
     * {@code a_user_ldgr.otp_key} 도 평문(길이 16)이다. ADR-LOGIN-011 결정("데이터를 그대로
     * 사용")과 동일한 취지로, 기존 평문 키에 대해서는 복호화를 건너뛴다(AMB-L07).</p>
     * <p>The port's default is {@code true} (AES-256-GCM). But the legacy stored {@code OTP_KEY}
     * as plaintext Base32 and the live column is plaintext (length 16), so — consistent with the
     * "use the data as-is" ruling — decryption is skipped for these keys.</p>
     *
     * <p>⚠ {@code false} 는 저장 시 암호화 통제를 포기하는 것이다. 이후 신규 등록 키를
     * 암호화하려면 마이그레이션(평문 → 암호문 재저장)이 필요하다.</p>
     * <p>{@code false} abandons encryption-at-rest; re-encrypting on a later migration is a
     * separate step.</p>
     *
     * // source: GoogleOTP.java — Base32-encoded 80-bit key stored plaintext
     * // req: ADR-LOGIN-011, AMB-L07, NFR-SEC-PII-L01
     */
    private final boolean otpKeyEncrypted;

    /**
     * 인증 서비스 생성. / Creates the authentication service.
     *
     * @param users         사용자 매퍼 / the user mapper
     * @param hasher        비밀번호 해시기 / the password hasher
     * @param totp          OTP 검증기 / the TOTP verifier
     * @param cipher        OTP 비밀키 암복호화 / the OTP secret cipher
     * @param replayGuard   OTP 재사용 방지 / the OTP replay guard
     * @param accountPolicy 계정 정책 / the account policy
     * @param ipAllowlist   접속 IP 허용목록 / the source-address allowlist
     * @param notifier      관리자 로그인 알림 / the administrator login notifier
     * @param audit         감사 서비스 / the audit service
     */
    public AuthenticationService(UserMapper users,
                                 PasswordHasher hasher,
                                 TotpVerifier totp,
                                 SecretCipher cipher,
                                 OtpReplayGuard replayGuard,
                                 AccountPolicy accountPolicy,
                                 IpAllowlistPolicy ipAllowlist,
                                 AdminLoginNotifier notifier,
                                 AuditService audit,
                                 @org.springframework.beans.factory.annotation.Value(
                                         "${iris.auth.otp.key-encrypted:true}") boolean otpKeyEncrypted,
                                 OtpDevBypass otpDevBypass) {
        this.users = users;
        this.hasher = hasher;
        this.totp = totp;
        this.cipher = cipher;
        this.replayGuard = replayGuard;
        this.accountPolicy = accountPolicy;
        this.ipAllowlist = ipAllowlist;
        this.notifier = notifier;
        this.audit = audit;
        this.otpKeyEncrypted = otpKeyEncrypted;
        this.otpDevBypass = otpDevBypass;
    }

    /**
     * 표시용 이름을 마스킹한다. / Masks a display name.
     *
     * <p>레거시는 {@code RegexNameMasking.maskName(FLNM)} 으로 실명을 마스킹했다. 이
     * 슬라이스의 {@link UserAccount} 는 실명을 담지 않으므로(불필요한 PII 를 싣지 않기
     * 위한 의도적 설계) 이메일 로컬파트를 마스킹한다.</p>
     * <p>The legacy masked the real name with {@code RegexNameMasking.maskName(FLNM)}. This
     * slice's {@link UserAccount} deliberately does not carry the real name — to avoid
     * transporting PII nothing needs — so the email local part is masked instead.</p>
     *
     * @param email 이메일 / the email
     * @return 마스킹된 표시 문자열 / a masked display string
     */
    // source: apc_login_proc_act.jsp — RegexNameMasking.maskName(idoOut1.getString("FLNM"))
    // req: NFR-SEC-PII-L02, FR-LOGIN-021
    private String maskName(String email) {
        int at = email.indexOf('@');
        String local = at > 0 ? email.substring(0, at) : email;
        if (local.length() <= 2) {
            return local.charAt(0) + "*";
        }
        return local.charAt(0) + "*".repeat(local.length() - 2) + local.charAt(local.length() - 1);
    }

    /**
     * 자격증명을 검증하고 인증 결과를 반환한다.
     * Verifies credentials and returns the authentication result.
     *
     * <p>세 입력값은 한 요청에 함께 제출된다. 레거시 화면도 같은 방식이었다 —
     * 이메일·비밀번호·OTP 를 한 번에 보내고 서버가 순서대로 판정한다.</p>
     * <p>All three inputs arrive in a single request, as they did in the legacy screen:
     * email, password and OTP submitted together, with the server deciding in order.</p>
     *
     * @param email         입력 이메일 / the submitted email
     * @param rawPassword   입력 비밀번호 / the submitted password
     * @param otpCode       입력 OTP 코드 (6자리) / the submitted OTP code, six digits
     * @param sourceIp      신뢰 가능한 출처 IP / trusted source address
     * @param correlationId 요청 상관 식별자 / request correlation id
     * @return 인증 결과 / the authentication result
     * @throws AuthenticationException 인증 실패 시 / when authentication fails
     */
    // req: FR-LOGIN-001, FR-LOGIN-002, FR-LOGIN-003, FR-LOGIN-004, FR-LOGIN-005,
    //      FR-LOGIN-009, FR-LOGIN-010, FR-LOGIN-011
    @Transactional
    public AuthResult authenticate(String email,
                                   String rawPassword,
                                   String otpCode,
                                   String sourceIp,
                                   String correlationId) {
        UserAccount account = users.findByEmail(email);

        // 1. 계정 미존재. 비밀번호 불일치와 동일한 사유·동일한 경로로 처리한다.
        //    Unknown account. Same reason and same code path as a wrong password, so
        //    the two cannot be distinguished by response content (FR-LOGIN-002).
        if (account == null) {
            denied(email, AuditEvent.ACTION_LOGIN, "unknown-account", sourceIp, correlationId);
            throw new AuthenticationException(AuthFailureReason.INVALID_CREDENTIALS);
        }

        // 2. 잠금 판정 — 자격증명 검증 전에 수행 (SEC-L03).
        //    Lockout, evaluated before any credential verification.
        try {
            accountPolicy.assertNotLocked(account);
        } catch (AuthenticationException e) {
            denied(email, AuditEvent.ACTION_LOGIN, "locked", sourceIp, correlationId);
            throw e;
        }

        // 3. 비밀번호 검증.
        //    Password verification.
        if (!verifyPassword(account, rawPassword)) {
            int attempts = users.incrementLoginAttempt(email);
            if (attempts >= AccountPolicy.MAX_FAILURES) {
                audit.recordAuth(email, AuditEvent.ACTION_LOCKOUT, AuditEvent.Outcome.DENIED,
                        "password-failure-threshold", sourceIp, correlationId);
            }
            denied(email, AuditEvent.ACTION_LOGIN, "bad-password", sourceIp, correlationId);
            throw new AuthenticationException(AuthFailureReason.INVALID_CREDENTIALS);
        }

        // 4. OTP 검증 — 두 요소 중 두 번째. 여기를 통과하지 못하면 인증은 완료되지 않는다.
        //    OTP verification — the second of two factors. Authentication cannot
        //    complete without passing this step.
        //
        //    OTP 미등록 계정은 등록 화면으로 유도한다(FR-LOGIN-008). 등록된 계정은
        //    반드시 코드를 검증한다 — 비밀번호만으로 통과하는 경로는 존재하지 않는다
        //    (NFR-SEC-AUTH-L01).
        //    An account without a registered OTP is directed to registration
        //    (FR-LOGIN-008). An account with one must verify a code: no path completes
        //    authentication on a password alone (NFR-SEC-AUTH-L01).
        if (!account.hasOtpRegistered()) {
            denied(email, AuditEvent.ACTION_LOGIN, "otp-not-registered", sourceIp, correlationId);
            throw new AuthenticationException(AuthFailureReason.OTP_NOT_REGISTERED);
        }
        // 개발 전용 우회 — 로컬 프로필에서만 활성화되며, 그 외에서는 애플리케이션이
        // 기동조차 하지 않는다(OtpDevBypass). 등록 요구(위 hasOtpRegistered 검사)는
        // 우회하지 않는다 — 등록 자체를 건너뛰면 로컬 환경이 운영과 다른 계정 상태를
        // 갖게 되어, 여기서만 재현되는 결함을 만들어 낸다.
        // Development-only bypass, active under the local profile alone; anywhere else the
        // application refuses to start (OtpDevBypass). The enrolment requirement above is
        // deliberately NOT bypassed: skipping registration would give local a different account
        // state from production and manufacture defects that reproduce only here.
        if (otpDevBypass.isActive()) {
            log.warn("OTP_BYPASSED dev bypass accepted a login without verifying the second "
                    + "factor — local profile only (ADR-LOGIN-020)");
            audit.recordAuth(email, AuditEvent.ACTION_LOGIN, AuditEvent.Outcome.OK,
                    "otp-bypassed-dev", sourceIp, correlationId);
        } else {
            // OTP 검증은 verifySecondFactor 로 추출되어 있다. 평문/암호화 OTP 키 처리
            // (otpKeyEncrypted, AMB-L07)는 그 메서드 안에서 수행한다.
            // OTP verification is extracted into verifySecondFactor; the plaintext/encrypted key
            // handling (otpKeyEncrypted, AMB-L07) lives inside that method.
            verifySecondFactor(account, email, otpCode, sourceIp, correlationId);
        }

        // 5. 카운터 초기화 — 값이 0 이 아닐 때만 쓰기.
        //    Reset counters, writing only when they are non-zero.
        if (account.loginAttempt() > 0 || account.otpFailCount() > 0) {
            users.resetFailureCounters(email);
        }

        // 6. 휴면 → 가입상태.
        //    Dormancy, then membership status.
        try {
            accountPolicy.assertUsable(account);
        } catch (AuthenticationException e) {
            denied(email, AuditEvent.ACTION_LOGIN, e.reason().name().toLowerCase(), sourceIp, correlationId);
            throw e;
        }

        // 7. 비밀번호 변경 강제 여부. 세션은 아직 확립하지 않는다.
        //    Whether a password change is required. The session is not established yet.
        if (accountPolicy.passwordChangeRequired(account)) {
            audit.recordAuth(email, AuditEvent.ACTION_LOGIN, AuditEvent.Outcome.OK,
                    "password-change-required", sourceIp, correlationId);
            return AuthResult.passwordChangeRequired(account);
        }

        boolean operator = users.isOperator(email);

        // 8. IP 허용목록 — 역할이 확정된 뒤에 판정한다. 운영자 전용 규칙이므로 인증
        //    이전에는 적용 대상을 구분할 수 없다 (FR-LOGIN-024, AMB-L03).
        //    IP allowlist, evaluated once the role is known: the rule is operators-only, so
        //    before authentication there is no way to tell whom it applies to.
        ipAllowlist.assertAllowed(email, operator, sourceIp);

        users.touchLastLogin(email);
        audit.recordAuth(email, AuditEvent.ACTION_LOGIN, AuditEvent.Outcome.OK,
                operator ? "operator" : "tenant", sourceIp, correlationId);

        // 9. 운영자 로그인 시 다른 관리자에게 알린다. 실패해도 로그인은 성립한다 —
        //    레거시는 알림 예외를 다시 던져 로그인 전체를 실패시켰다.
        //    Notify other administrators on operator login. A failure does not fail the
        //    login; the legacy rethrew and failed the whole authentication.
        if (operator) {
            notifier.notifyOperatorLogin(email, maskName(email), sourceIp);
        }

        return AuthResult.authenticated(account, operator);
    }

    /**
     * 비밀번호를 검증한다. 신규 스키마 우선, 레거시 경로는 결정 대기로 차단.
     * Verifies the password. New scheme first; the legacy path is blocked pending
     * the ADR-LOGIN-011 ruling.
     *
     * @param account     계정 / the account
     * @param rawPassword 입력 비밀번호 / the submitted password
     * @return 일치 여부 / true when the password matches
     */
    // req: FR-LOGIN-005, ADR-LOGIN-011
    private boolean verifyPassword(UserAccount account, String rawPassword) {
        if (account.hasModernHash()) {
            return hasher.matches(rawPassword, account.passwordHash());
        }
        if (!hasher.legacyVerificationEnabled()) {
            // ADR-LOGIN-011 미결정: 마이그레이션되지 않은 계정은 인증할 수 없다.
            // Pending ADR-LOGIN-011: an unmigrated account cannot be authenticated.
            // Failing closed here is deliberate — the alternative would be choosing
            // the migration policy by omission.
            return false;
        }
        return hasher.matchesLegacy(rawPassword, account.legacyPasswordHash());
    }

    /**
     * 두 번째 인증 요소를 검증한다. / Verifies the second authentication factor.
     *
     * <p>형식 검사 · 코드 검증 · 재사용 방지의 세 단계를 담는다. 별도 메서드로 분리한 이유는
     * 개발용 우회({@link com.webcash.iris.auth.config.OtpDevBypass})가 <b>이 세 단계만</b>
     * 건너뛰도록 하기 위해서다 — 우회가 인증 메서드 전체를 조기 반환시키면 휴면·가입상태
     * 확인, 비밀번호 변경 강제, IP 허용목록까지 함께 사라져 로컬 환경이 운영과 다른 방식으로
     * 동작하게 된다.</p>
     * <p>Holds the three steps — format, verification, replay prevention — as one unit so the
     * development bypass skips <b>only</b> those. Had the bypass returned early from the
     * authentication method instead, the dormancy and status checks, the forced password change
     * and the IP allowlist would have disappeared with it, and local would behave unlike
     * production in ways that hide defects rather than surface them.</p>
     *
     * @param account       대상 계정 / the account
     * @param email         이메일 / the email
     * @param otpCode       제출된 OTP 코드 / the submitted OTP code
     * @param sourceIp      출처 IP / the source address
     * @param correlationId 상관 식별자 / the correlation id
     */
    // req: FR-LOGIN-010, FR-LOGIN-011, NFR-SEC-AUTH-L01, NFR-SEC-AUTH-L03, TM-L004
    private void verifySecondFactor(UserAccount account,
                                    String email,
                                    String otpCode,
                                    String sourceIp,
                                    String correlationId) {
        if (!totp.isWellFormed(otpCode)) {
            // 형식 오류는 실패 횟수를 증가시키지 않는다. 레거시도 ADM_00023 을 던지고
            // 카운터를 건드리지 않았으며, 오타로 계정이 잠기는 것은 과도하다.
            // A malformed code does not increment the failure counter. The legacy threw
            // ADM_00023 without touching it either, and locking an account over a typo
            // is disproportionate.
            denied(email, AuditEvent.ACTION_LOGIN, "otp-malformed", sourceIp, correlationId);
            throw new AuthenticationException(AuthFailureReason.OTP_MALFORMED);
        }
        // 저장된 비밀키를 해석한다. 포트 기본값은 AES-256-GCM 복호화(NFR-SEC-PII-L01)이나,
        // 레거시 평문 Base32 키는 그대로 사용한다(AMB-L07, otpKeyEncrypted=false). 어느
        // 경로든 평문 비밀키는 이 메서드를 벗어나지 않는다.
        // Resolves the stored secret: AES-GCM decrypt by default, or the legacy plaintext Base32
        // key as-is (otpKeyEncrypted=false). Either way the plaintext never leaves this method.
        String otpSecret = otpKeyEncrypted ? cipher.decrypt(account.otpKey()) : account.otpKey();
        if (!totp.verify(otpSecret, otpCode)) {
            int otpFailures = users.incrementOtpFailCount(email);
            if (otpFailures >= AccountPolicy.MAX_FAILURES) {
                audit.recordAuth(email, AuditEvent.ACTION_LOCKOUT, AuditEvent.Outcome.DENIED,
                        "otp-failure-threshold", sourceIp, correlationId);
            }
            denied(email, AuditEvent.ACTION_LOGIN, "otp-mismatch", sourceIp, correlationId);
            throw new AuthenticationException(AuthFailureReason.OTP_MISMATCH);
        }

        // 단일 사용 강제 (TM-L004). 검증 성공 후에 소비 처리하는 순서가 중요하다 —
        // 검증 전에 소비하면 틀린 코드가 메모리를 채우고, 공격자가 임의 값으로 저장소를
        // 부풀릴 수 있다.
        // Single-use enforcement (TM-L004). Consuming after a successful verification is
        // the right order: consuming first would fill memory with wrong codes and let an
        // attacker inflate the store with arbitrary values.
        if (!replayGuard.tryConsume(email, otpCode)) {
            denied(email, AuditEvent.ACTION_LOGIN, "otp-replay", sourceIp, correlationId);
            throw new AuthenticationException(AuthFailureReason.OTP_MISMATCH);
        }
    }

    private void denied(String email, String action, String detail, String sourceIp, String correlationId) {
        audit.recordAuth(email, action, AuditEvent.Outcome.DENIED, detail, sourceIp, correlationId);
    }

    /**
     * 인증 결과. / The authentication result.
     *
     * @param account                계정 / the account
     * @param operator               운영자 여부 / whether the principal is an operator
     * @param passwordChangeRequired 비밀번호 변경 필요 여부 / whether a change is required first
     */
    public record AuthResult(UserAccount account, boolean operator, boolean passwordChangeRequired) {

        /**
         * 인증 성공 결과를 만든다. / Builds a successful result.
         *
         * @param account  계정 / the account
         * @param operator 운영자 여부 / whether the principal is an operator
         * @return 결과 / the result
         */
        public static AuthResult authenticated(UserAccount account, boolean operator) {
            return new AuthResult(account, operator, false);
        }

        /**
         * 비밀번호 변경이 필요한 결과를 만든다. / Builds a change-required result.
         *
         * @param account 계정 / the account
         * @return 결과 / the result
         */
        public static AuthResult passwordChangeRequired(UserAccount account) {
            return new AuthResult(account, false, true);
        }
    }
}
