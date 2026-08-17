package com.webcash.iris.auth.domain;

import com.webcash.iris.auth.crypto.PasswordHasher;
import com.webcash.iris.auth.crypto.SecretCipher;
import com.webcash.iris.auth.crypto.TemporaryPasswordGenerator;
import com.webcash.iris.auth.crypto.TotpVerifier;
import com.webcash.iris.auth.infra.db.UserMapper;
import com.webcash.iris.common.audit.AuditEvent;
import com.webcash.iris.common.audit.AuditService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 변경 및 운영자 초기화. / Password change and operator reset.
 *
 * <p>이 서비스는 로그인 모듈의 <b>마지막 막다른 길을 해소</b>한다. Sprint L1~L3 에서
 * {@link PasswordPolicy} 와 해시 저장 SQL 은 완성·검증되었으나 <b>호출자가 없었고</b>,
 * 그 결과 어떤 계정도 Argon2id 해시를 획득할 수 없어 실사용자가 0명이었다.</p>
 * <p>This service resolves the <b>last dead end</b>: {@link PasswordPolicy} and the
 * hash-writing SQL were complete and verified through Sprints L1–L3 but had <b>no
 * caller</b>, so no account could ever obtain an Argon2id hash and the system had zero
 * usable accounts.</p>
 *
 * <h2>ADR-LOGIN-011 과의 관계 / Relationship to ADR-LOGIN-011</h2>
 * <p>{@link #resetByOperator} 는 <b>두 선택지 모두에서 필요하다</b>. 옵션 B(전면 초기화)
 * 에서는 마이그레이션의 주 수단이고, 옵션 A(로그인 시 상향)에서는 휴면·잠금 계정을 위한
 * 예비 경로다. 따라서 ADR 결정 <b>전에</b> 구현해도 어느 방향으로도 낭비가 아니며,
 * 결정 없이도 계정을 사용 가능 상태로 만들 수 있다.</p>
 * <p>{@link #resetByOperator} is <b>required under both options</b>: it is the primary
 * migration mechanism under option B (forced reset) and the fallback for dormant or
 * locked accounts under option A (upgrade-on-login). Building it before the ruling is
 * therefore wasted under neither — and it makes accounts usable without the ruling.</p>
 *
 * // source: apa_0010_04.act (password change popup), apm_0001_01_r001_act.jsp
 * // req: FR-PWD-001…007, FR-LOGIN-015
 */
@Service
public class PasswordChangeService {

    /**
     * 정책을 만족하는 임시 비밀번호를 얻기 위한 최대 시도 횟수.
     * Maximum attempts to obtain a policy-compliant temporary password.
     */
    private static final int MAX_TEMPORARY_ATTEMPTS = 20;

    private final UserMapper users;
    private final PasswordHasher hasher;
    private final TotpVerifier totp;
    private final SecretCipher cipher;
    private final PasswordPolicy passwordPolicy;
    private final AccountPolicy accountPolicy;
    private final TemporaryPasswordGenerator temporaryPasswords;
    private final AuditService audit;

    /**
     * 서비스 생성. / Creates the service.
     *
     * @param users              사용자 매퍼 / the user mapper
     * @param hasher             비밀번호 해시기 / the password hasher
     * @param totp               OTP 검증기 / the TOTP verifier
     * @param cipher             OTP 비밀키 암복호화 / the OTP secret cipher
     * @param passwordPolicy     비밀번호 정책 / the password policy
     * @param accountPolicy      계정 정책 / the account policy
     * @param temporaryPasswords 임시 비밀번호 생성기 / the temporary password generator
     * @param audit              감사 서비스 / the audit service
     */
    public PasswordChangeService(UserMapper users,
                                 PasswordHasher hasher,
                                 TotpVerifier totp,
                                 SecretCipher cipher,
                                 PasswordPolicy passwordPolicy,
                                 AccountPolicy accountPolicy,
                                 TemporaryPasswordGenerator temporaryPasswords,
                                 AuditService audit) {
        this.users = users;
        this.hasher = hasher;
        this.totp = totp;
        this.cipher = cipher;
        this.passwordPolicy = passwordPolicy;
        this.accountPolicy = accountPolicy;
        this.temporaryPasswords = temporaryPasswords;
        this.audit = audit;
    }

    /**
     * 사용자가 자신의 비밀번호를 변경한다. / A user changes their own password.
     *
     * <p><b>현재 비밀번호와 OTP 를 함께 요구한다.</b> 현재 비밀번호만으로 변경을 허용하면
     * 세션을 탈취한 공격자가 비밀번호를 바꿔 지속성을 확보할 수 있고(TM-L018), 비밀번호만
     * 훔친 공격자가 계정을 완전히 장악할 수 있다. 자격증명을 바꾸는 작업에는 로그인과
     * 동일한 2요소를 적용한다.</p>
     * <p><b>Both the current password and an OTP are required.</b> Allowing a change on the
     * current password alone would let a session hijacker establish persistence (TM-L018)
     * and let someone holding only a stolen password take the account over outright. A
     * credential-changing operation carries the same two factors as a login.</p>
     *
     * <p>강제 변경 흐름(FR-LOGIN-014/015)도 이 메서드를 사용한다. 그 시점에는 세션이
     * 확립되지 않았으므로 자격증명을 다시 제출받는다.</p>
     * <p>The forced-change flow uses this same method. No session exists at that point, so
     * the credentials are submitted again.</p>
     *
     * @param email       계정 이메일 / the account email
     * @param currentPassword 현재 비밀번호 / the current password
     * @param otpCode     OTP 코드 / the OTP code
     * @param newPassword 새 비밀번호 / the new password
     * @param sourceIp    신뢰 가능한 출처 IP / trusted source address
     * @return 정책 위반 목록. 비어 있으면 변경 완료 / policy violations; empty means changed
     * @throws AuthenticationException 자격증명 검증 실패 시 / when credentials do not verify
     */
    // req: FR-PWD-001, FR-PWD-002, FR-PWD-003, FR-PWD-004, FR-PWD-005, FR-PWD-006
    @Transactional
    public List<String> change(String email,
                               String currentPassword,
                               String otpCode,
                               String newPassword,
                               String sourceIp) {
        UserAccount account = users.findByEmail(email);
        if (account == null) {
            denied(email, "unknown-account", sourceIp);
            throw new AuthenticationException(AuthFailureReason.INVALID_CREDENTIALS);
        }

        // 잠금은 자격증명 검증보다 먼저 — 로그인과 동일한 순서를 유지한다.
        // Lockout before credential verification, matching the login order.
        accountPolicy.assertNotLocked(account);

        if (!account.hasModernHash() || !hasher.matches(currentPassword, account.passwordHash())) {
            denied(email, "bad-current-password", sourceIp);
            throw new AuthenticationException(AuthFailureReason.INVALID_CREDENTIALS);
        }

        if (!account.hasOtpRegistered()) {
            denied(email, "otp-not-registered", sourceIp);
            throw new AuthenticationException(AuthFailureReason.OTP_NOT_REGISTERED);
        }
        if (!totp.isWellFormed(otpCode) || !totp.verify(cipher.decrypt(account.otpKey()), otpCode)) {
            denied(email, "otp-invalid", sourceIp);
            throw new AuthenticationException(AuthFailureReason.OTP_MISMATCH);
        }

        // 정책 위반은 예외가 아니라 목록으로 반환한다 — 사용자가 고칠 것을 한 번에 알도록.
        // Violations are returned as a list rather than thrown, so the user learns
        // everything to fix at once.
        List<String> violations = passwordPolicy.validate(
                newPassword, email,
                users.findRecentPasswordHashes(email, 10),
                hasher);
        if (!violations.isEmpty()) {
            denied(email, "policy-violation", sourceIp);
            return violations;
        }

        // PM 결정(2026-08-17): 레거시 PWD(Base64 SHA-256)만 사용한다. Argon2 가 아니라
        // 레거시 알고리즘으로 해시하여 PWD 에 저장해야 로그인 검증(PWD)과 일치한다.
        // Uses the legacy PWD (Base64 SHA-256) only; hashing with the legacy algorithm keeps the
        // stored value consistent with the PWD-based login check (not Argon2).
        String newHash = PasswordHasher.legacySha256Base64(newPassword);
        users.updatePasswordHash(email, newHash);
        users.insertPasswordHistory(email, newHash);

        audit.recordAuth(email, AuditEvent.ACTION_PASSWORD_CHANGE, AuditEvent.Outcome.OK,
                "self-service", sourceIp, null);
        return List.of();
    }

    /**
     * 운영자가 사용자의 비밀번호를 임시 비밀번호로 초기화한다.
     * An operator resets a user's password to a generated temporary one.
     *
     * <p>반환된 임시 비밀번호는 <b>이 호출에서 한 번만</b> 노출된다. 저장되지 않으며
     * 다시 조회할 수 없다. 운영자는 이를 별도 경로로 사용자에게 전달한다.</p>
     * <p>The returned temporary password is disclosed <b>once, in this call only</b>. It is
     * not stored and cannot be retrieved again; the operator conveys it out of band.</p>
     *
     * <p>초기화된 계정은 다음 로그인에서 비밀번호 변경을 강제받는다(FR-LOGIN-015).
     * 운영자가 아는 자격증명이 계속 유효한 상태를 남기지 않는다.</p>
     * <p>The account is forced to change at next login (FR-LOGIN-015), leaving no state in
     * which a credential the operator knows remains valid.</p>
     *
     * @param operatorEmail 수행 운영자 / the acting operator
     * @param targetEmail   대상 계정 / the target account
     * @param reason        초기화 사유 / the stated reason
     * @param sourceIp      신뢰 가능한 출처 IP / trusted source address
     * @return 임시 비밀번호 (1회 노출) / the temporary password, disclosed once
     * @throws AuthenticationException 권한·대상 상태 문제 시 / on privilege or target-state problems
     */
    // req: FR-PWD-007, FR-LOGIN-015, ADR-LOGIN-011
    @Transactional
    public String resetByOperator(String operatorEmail, String targetEmail, String reason, String sourceIp) {
        // 자기 자신의 비밀번호 초기화 금지. OTP 자기초기화 금지(AMB-L08)와 같은 논리다 —
        // 운영자 한 명이 스스로의 자격증명을 임의로 교체할 수 있으면 통제의 분리가 사라진다.
        // Self-reset is prohibited, by the same logic as OTP self-reset (AMB-L08): a single
        // operator able to replace their own credential collapses the separation of control.
        if (operatorEmail.equalsIgnoreCase(targetEmail)) {
            auditAdmin(operatorEmail, targetEmail, AuditEvent.Outcome.DENIED,
                    "self-reset-prohibited", sourceIp);
            throw new AuthenticationException(AuthFailureReason.OPERATION_NOT_PERMITTED);
        }

        UserAccount target = users.findByEmail(targetEmail);
        if (target == null) {
            throw new AuthenticationException(AuthFailureReason.INVALID_CREDENTIALS);
        }
        if (target.status() == AccountStatus.TERMINATED) {
            auditAdmin(operatorEmail, targetEmail, AuditEvent.Outcome.DENIED,
                    "target-terminated", sourceIp);
            throw new AuthenticationException(AuthFailureReason.STATUS_BLOCKED);
        }

        // 생성된 임시 비밀번호가 정책을 통과하는지 확인한 뒤 사용한다.
        //
        // 왜 검증이 필요한가: 생성기는 문자 종류만 보장하고 연속 문자열(예: "abcd") 검사는
        // 하지 않는다. 16자를 무작위로 뽑으면 낮은 확률로 연속 4자가 포함될 수 있고, 그런
        // 임시 비밀번호를 발급하면 사용자가 강제 변경 화면에서 그것을 입력해야 하는데
        // 정책이 거절하는 모순이 생긴다 — 재현이 어렵고 원인 파악도 어려운 유형의 장애다.
        //
        // Why this check exists: the generator guarantees character classes but does not
        // test for sequential runs such as "abcd". Drawing 16 characters at random can
        // include one with low probability, and issuing such a temporary password creates a
        // contradiction — the user must enter it on the forced-change screen, where the
        // policy rejects it. That is an intermittent, hard-to-diagnose failure.
        String temporary = null;
        for (int attempt = 0; attempt < MAX_TEMPORARY_ATTEMPTS; attempt++) {
            String candidate = temporaryPasswords.generate();
            if (passwordPolicy.validate(candidate, targetEmail, List.of(), hasher).isEmpty()) {
                temporary = candidate;
                break;
            }
        }
        if (temporary == null) {
            // 정책과 생성기가 구조적으로 어긋난 상태다. 조용히 약한 비밀번호를 쓰는 대신
            // 실패한다 — 설정 오류를 감추면 나중에 더 비싸다.
            // The policy and generator are structurally misaligned. Fail rather than
            // silently issue a non-compliant password: hiding a misconfiguration costs
            // more later.
            throw new IllegalStateException(
                    "Could not generate a policy-compliant temporary password in "
                            + MAX_TEMPORARY_ATTEMPTS + " attempts. "
                            + "TemporaryPasswordGenerator and PasswordPolicy are misaligned.");
        }

        // PM 결정(2026-08-17): 레거시 PWD(Base64 SHA-256)만 사용한다. / Legacy PWD only.
        String hash = PasswordHasher.legacySha256Base64(temporary);
        users.resetPasswordHash(targetEmail, hash);
        users.insertPasswordHistory(targetEmail, hash);

        auditAdmin(operatorEmail, targetEmail, AuditEvent.Outcome.OK, reason, sourceIp);
        return temporary;
    }

    private void denied(String email, String detail, String sourceIp) {
        audit.recordAuth(email, AuditEvent.ACTION_PASSWORD_CHANGE,
                AuditEvent.Outcome.DENIED, detail, sourceIp, null);
    }

    private void auditAdmin(String actor, String target, AuditEvent.Outcome outcome,
                            String detail, String sourceIp) {
        audit.record(new AuditEvent(Instant.now(), actor, target,
                AuditEvent.ACTION_PASSWORD_RESET, outcome, detail, sourceIp, null));
    }
}
