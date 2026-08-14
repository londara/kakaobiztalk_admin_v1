package com.webcash.iris.auth.domain;

import com.webcash.iris.auth.crypto.PasswordHasher;
import com.webcash.iris.auth.crypto.QrRenderer;
import com.webcash.iris.auth.crypto.SecretCipher;
import com.webcash.iris.auth.crypto.TotpVerifier;
import com.webcash.iris.auth.infra.db.UserMapper;
import com.webcash.iris.common.audit.AuditEvent;
import com.webcash.iris.common.audit.AuditService;
import dev.samstevens.totp.secret.SecretGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Google OTP 등록 및 운영자 초기화. / Google OTP enrolment and operator reset.
 *
 * <p>레거시 {@code apm_1001_03_r001_act.jsp} 는 {@code OTP_KEY} 가 이미 있으면
 * {@code ADM_00026} 을 던져 재등록을 막았고, <b>복구 경로를 정의하지 않았다.</b>
 * Google OTP 가 유일한 2요소인 상황에서 단말을 분실하면 계정이 영구히 사용 불가가 된다.
 * PM 결정(AMB-L02)에 따라 운영자 초기화를 유일한 복구 경로로 추가한다.</p>
 * <p>The legacy blocked re-registration when {@code OTP_KEY} existed and <b>defined no
 * recovery path</b>. With Google OTP as the only second factor, a lost device meant a
 * permanently unusable account. Per PM decision AMB-L02, operator reset is the single
 * recovery route.</p>
 *
 * // source: apm_1001_03_r001_act.jsp, apm_1001_02_c001_act.jsp
 * // req: FR-OTP-001…009
 */
@Service
public class OtpRegistrationService {

    private final UserMapper users;
    private final PasswordHasher hasher;
    private final TotpVerifier totp;
    private final SecretGenerator secretGenerator;
    private final SecretCipher cipher;
    private final QrRenderer qr;
    private final AccountPolicy accountPolicy;
    private final AuditService audit;

    /**
     * 서비스 생성. / Creates the service.
     *
     * @param users           사용자 매퍼 / the user mapper
     * @param hasher          비밀번호 해시기 / the password hasher
     * @param totp            OTP 검증기 / the TOTP verifier
     * @param secretGenerator 비밀키 생성기 / the secret generator
     * @param cipher          비밀키 암복호화 / the secret cipher
     * @param qr              otpauth URI 생성기 / the otpauth URI builder
     * @param accountPolicy   계정 정책 / the account policy
     * @param audit           감사 서비스 / the audit service
     */
    public OtpRegistrationService(UserMapper users,
                                  PasswordHasher hasher,
                                  TotpVerifier totp,
                                  SecretGenerator secretGenerator,
                                  SecretCipher cipher,
                                  QrRenderer qr,
                                  AccountPolicy accountPolicy,
                                  AuditService audit) {
        this.users = users;
        this.hasher = hasher;
        this.totp = totp;
        this.secretGenerator = secretGenerator;
        this.cipher = cipher;
        this.qr = qr;
        this.accountPolicy = accountPolicy;
        this.audit = audit;
    }

    /**
     * 등록을 시작하고 비밀키를 발급한다. 아직 저장하지 않는다.
     * Begins enrolment and issues a secret. Nothing is persisted yet.
     *
     * <p>비밀키를 <b>확인 코드 검증 후에만</b> 저장하는 것이 중요하다. 발급 즉시
     * 저장하면 등록을 중단한 사용자는 자신이 갖고 있지 않은 비밀키로 잠기게 되고,
     * 운영자 초기화 없이는 로그인할 수 없다.</p>
     * <p>Persisting the secret <b>only after</b> a confirmation code verifies matters: if
     * it were stored on issue, a user who abandoned enrolment would be locked out by a
     * secret they never captured, recoverable only by an operator reset.</p>
     *
     * <p>비밀번호를 요구하는 이유는 TM-L006 이다 — 비밀번호만 훔친 공격자가 OTP 미등록
     * 계정에 <b>자신의</b> 단말을 등록해 버리는 것을 막는다. 이 경로는 2요소를 우회하는
     * 것이 아니라 획득하는 경로이므로, 지식 요소 확인이 최소 방어선이다.</p>
     * <p>A password is required because of TM-L006: an attacker holding only a stolen
     * password could otherwise enrol <b>their own</b> device on an account that has no
     * OTP yet. This path acquires the second factor rather than bypassing it, so
     * verifying the knowledge factor is the minimum defence.</p>
     *
     * @param email       계정 이메일 / the account email
     * @param rawPassword 비밀번호 / the password
     * @param sourceIp    신뢰 가능한 출처 IP / trusted source address
     * @return 대기 중 등록 정보 / the pending enrolment
     * @throws AuthenticationException 자격증명 또는 자격요건 불충족 시 / on bad credentials or ineligibility
     */
    // req: FR-OTP-001, FR-OTP-002, FR-OTP-003, FR-OTP-009
    public PendingRegistration begin(String email, String rawPassword, String sourceIp) {
        UserAccount account = users.findByEmail(email);
        if (account == null || !account.hasModernHash()
                || !hasher.matches(rawPassword, account.passwordHash())) {
            audit.recordAuth(email, AuditEvent.ACTION_OTP_REGISTER, AuditEvent.Outcome.DENIED,
                    "bad-credentials", sourceIp, null);
            throw new AuthenticationException(AuthFailureReason.INVALID_CREDENTIALS);
        }

        // 등록도 로그인과 같은 자격요건을 적용한다. 해지·중지·휴면 계정이 등록으로
        // 되살아나는 우회로가 생기지 않도록 한다.
        // Enrolment applies the same eligibility rules as login, so that a terminated,
        // suspended or dormant account cannot come back to life through this path.
        accountPolicy.assertUsable(account);

        if (account.hasOtpRegistered()) {
            audit.recordAuth(email, AuditEvent.ACTION_OTP_REGISTER, AuditEvent.Outcome.DENIED,
                    "already-registered", sourceIp, null);
            throw new AuthenticationException(AuthFailureReason.OTP_ALREADY_REGISTERED);
        }

        // req: FR-OTP-002 — 160비트. 레거시는 10바이트(80비트)를 썼다(결함 L8).
        String secret = secretGenerator.generate();
        return new PendingRegistration(secret, qr.otpauthUri(email, secret));
    }

    /**
     * 확인 코드를 검증하고 비밀키를 저장한다. / Verifies the confirmation code and persists the secret.
     *
     * @param email        계정 이메일 / the account email
     * @param pendingSecret 발급된 비밀키 / the issued secret
     * @param code         사용자가 입력한 코드 / the code entered by the user
     * @param sourceIp     신뢰 가능한 출처 IP / trusted source address
     * @throws AuthenticationException 코드 불일치 시 / when the code does not verify
     */
    // req: FR-OTP-005, NFR-SEC-PII-L01
    @Transactional
    public void complete(String email, String pendingSecret, String code, String sourceIp) {
        if (!totp.verify(pendingSecret, code)) {
            audit.recordAuth(email, AuditEvent.ACTION_OTP_REGISTER, AuditEvent.Outcome.DENIED,
                    "confirmation-code-invalid", sourceIp, null);
            throw new AuthenticationException(AuthFailureReason.OTP_MISMATCH);
        }
        users.updateOtpKey(email, cipher.encrypt(pendingSecret));
        audit.recordAuth(email, AuditEvent.ACTION_OTP_REGISTER, AuditEvent.Outcome.OK,
                null, sourceIp, null);
    }

    /**
     * 운영자가 사용자의 OTP 등록을 초기화한다. / An operator resets a user's OTP enrolment.
     *
     * <p>이 메서드는 <b>2요소를 합법적으로 무력화하는 경로</b>다(TM-L008). 실질적 방어는
     * 소프트웨어 밖의 대면·유선 신원확인 절차이며, 코드가 할 수 있는 일은 권한 확인과
     * 빠짐없는 감사 기록이다.</p>
     * <p>This method is a <b>sanctioned way to remove someone's second factor</b>
     * (TM-L008). The real defence is the out-of-band identity check outside the software;
     * what the code can do is enforce the role and record the act completely.</p>
     *
     * @param operatorEmail 수행 운영자 / the acting operator
     * @param targetEmail   대상 계정 / the target account
     * @param reason        초기화 사유 / the stated reason
     * @param sourceIp      신뢰 가능한 출처 IP / trusted source address
     * @throws AuthenticationException 대상이 해지 상태일 때 / when the target is terminated
     */
    // req: FR-OTP-007, FR-OTP-008
    @Transactional
    public void resetByOperator(String operatorEmail, String targetEmail, String reason, String sourceIp) {
        // 자기 자신의 OTP 초기화 금지 (AMB-L08). 허용하면 운영자 한 명이 스스로의
        // 2요소를 제거할 수 있어 통제의 분리가 사라진다.
        // Self-reset is prohibited (AMB-L08): allowing it would let a single operator
        // remove their own second factor, collapsing the separation the control relies on.
        if (operatorEmail.equalsIgnoreCase(targetEmail)) {
            audit.record(new AuditEvent(java.time.Instant.now(), operatorEmail, targetEmail,
                    AuditEvent.ACTION_OTP_RESET, AuditEvent.Outcome.DENIED,
                    "self-reset-prohibited", sourceIp, null));
            throw new AuthenticationException(AuthFailureReason.OPERATION_NOT_PERMITTED);
        }

        UserAccount target = users.findByEmail(targetEmail);
        if (target == null) {
            throw new AuthenticationException(AuthFailureReason.INVALID_CREDENTIALS);
        }

        // 해지 계정은 초기화로 되살릴 수 없다 (UC-LOGIN-003 E-3).
        // A terminated account must not regain access through a reset.
        if (target.status() == AccountStatus.TERMINATED) {
            audit.record(new AuditEvent(java.time.Instant.now(), operatorEmail, targetEmail,
                    AuditEvent.ACTION_OTP_RESET, AuditEvent.Outcome.DENIED,
                    "target-terminated", sourceIp, null));
            throw new AuthenticationException(AuthFailureReason.STATUS_BLOCKED);
        }

        // OTP 실패 카운터를 함께 초기화한다. 그렇지 않으면 사용자가 재등록 직후
        // 잠금 상태에 걸린다 (UC-LOGIN-003 A-1).
        // The OTP failure counter is cleared alongside the key, or the user would hit the
        // lockout immediately after re-enrolling.
        users.clearOtpKey(targetEmail);
        users.resetFailureCounters(targetEmail);

        audit.record(new AuditEvent(java.time.Instant.now(), operatorEmail, targetEmail,
                AuditEvent.ACTION_OTP_RESET, AuditEvent.Outcome.OK, reason, sourceIp, null));
    }

    /**
     * 대기 중 등록 정보. / A pending enrolment.
     *
     * <p>{@code toString()} 을 재정의하여 비밀키가 로그로 흘러가지 않게 한다.</p>
     * <p>{@code toString()} is overridden so the secret cannot reach a log line.</p>
     *
     * @param secret      Base32 비밀키 / the Base32 secret
     * @param otpauthUri  등록용 URI / the enrolment URI
     */
    public record PendingRegistration(String secret, String otpauthUri) {

        /**
         * 비밀키를 제외한 표현을 반환한다. / Returns a representation excluding the secret.
         *
         * @return 마스킹된 문자열 / a masked string
         */
        // req: NFR-SEC-LOG-L01
        @Override
        public String toString() {
            return "PendingRegistration[secret=***, otpauthUri=***]";
        }
    }
}
