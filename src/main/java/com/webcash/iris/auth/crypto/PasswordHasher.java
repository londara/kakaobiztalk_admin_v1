package com.webcash.iris.auth.crypto;

import com.webcash.iris.auth.domain.PasswordPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 비밀번호 해시 생성·검증. / Password hashing and verification.
 *
 * <p><b>레거시 결함 L2 대응.</b> 레거시는 솔트 없는 SHA-256
 * ({@code JexMessageDigest.getHashString(SHA_256, pwd)})으로 비밀번호를 저장했다.
 * 사용자별 솔트가 없어 동일 비밀번호가 동일 해시를 만들고, 작업 계수가 없어 오프라인
 * 추측이 하드웨어 속도로 수행된다. Argon2id 로 대체한다.</p>
 * <p><b>Fixes legacy defect L2.</b> The legacy stored unsalted SHA-256. With no
 * per-user salt, identical passwords produce identical hashes and rainbow tables
 * apply; with no work factor, offline guessing runs at hardware speed. Replaced
 * with Argon2id.</p>
 *
 * <h2>레거시 해시 검증 — ADR-LOGIN-011 미결정 / Legacy verification — pending ADR</h2>
 * <p>{@link #matchesLegacy} 는 <b>의도적으로 미구현</b>이다. 상향 마이그레이션
 * (upgrade-on-login) 채택 여부가 PM 결정 대기 중이며, 그 결정은 기존 해시 데이터가
 * 과거에 유출된 적이 있는지에 달려 있다(RISK-L01, TM-L002). 유출 이력이 있다면
 * 이미 알려진 자격증명을 새 스키마로 재승인하는 셈이 되므로, 전면 초기화가 옳다.
 * 결정 전에 구현하면 되돌리기 어려운 방향으로 굳는다.</p>
 * <p>{@link #matchesLegacy} is <b>deliberately unimplemented</b>. Whether to adopt
 * upgrade-on-login awaits a PM ruling, and that ruling depends on whether the
 * existing hash database has ever been exposed (RISK-L01, TM-L002). If it has,
 * upgrading silently re-blesses credentials that must be assumed known, and a
 * forced reset is the correct answer instead. Implementing before the ruling would
 * set the harder-to-reverse direction.</p>
 *
 * // source: apc_login_proc_act.jsp — JexMessageDigest.getHashString(SHA_256, pwd)
 * // req: FR-LOGIN-005, CONST-SEC-L01, ADR-LOGIN-011
 */
@Component
public class PasswordHasher implements PasswordPolicy.PasswordMatcher {

    private final PasswordEncoder encoder;
    private final boolean legacyVerificationEnabled;

    /**
     * Argon2id 인코더를 구성한다. / Configures the Argon2id encoder.
     *
     * <p>파라미터는 배포 환경에서 실측하여 조정해야 한다. 문서에서 값을 그대로
     * 옮겨오면 대상 하드웨어에서 과소·과대 비용이 된다(RISK-L07 — 해싱 비용은
     * 미인증 엔드포인트의 CPU 소모 벡터이기도 하다).</p>
     * <p>Parameters must be tuned against measured capacity on the target
     * environment. Copying values from documentation gives a cost that is either
     * too weak or, on an unauthenticated endpoint, a CPU-exhaustion vector
     * (RISK-L07).</p>
     *
     * @param saltLength                솔트 길이(바이트) / salt length in bytes
     * @param hashLength                해시 길이(바이트) / hash length in bytes
     * @param parallelism               병렬도 / parallelism
     * @param memoryKb                  메모리(KB) / memory cost in KB
     * @param iterations                반복 횟수 / iteration count
     * @param legacyVerificationEnabled 레거시 해시 검증 허용 여부 / whether legacy verification is enabled
     */
    public PasswordHasher(
            @Value("${iris.auth.argon2.salt-length:16}") int saltLength,
            @Value("${iris.auth.argon2.hash-length:32}") int hashLength,
            @Value("${iris.auth.argon2.parallelism:1}") int parallelism,
            @Value("${iris.auth.argon2.memory-kb:19456}") int memoryKb,
            @Value("${iris.auth.argon2.iterations:2}") int iterations,
            @Value("${iris.auth.legacy-hash-verification.enabled:false}") boolean legacyVerificationEnabled) {
        this.encoder = new Argon2PasswordEncoder(saltLength, hashLength, parallelism, memoryKb, iterations);
        this.legacyVerificationEnabled = legacyVerificationEnabled;
    }

    /**
     * 비밀번호를 Argon2id 로 해시한다. / Hashes a password with Argon2id.
     *
     * @param raw 평문 비밀번호 / the raw password
     * @return 저장용 해시 문자열 / the encoded hash for storage
     */
    // req: FR-LOGIN-005
    public String hash(String raw) {
        return encoder.encode(raw);
    }

    /**
     * 신규 스키마 해시와 평문을 비교한다. / Verifies a raw password against a modern hash.
     *
     * @param raw  평문 비밀번호 / the raw password
     * @param hash 저장된 Argon2id 해시 / the stored Argon2id hash
     * @return 일치 여부 / true when they match
     */
    // req: FR-LOGIN-005
    @Override
    public boolean matches(String raw, String hash) {
        if (raw == null || hash == null || hash.isBlank()) {
            return false;
        }
        return encoder.matches(raw, hash);
    }

    /**
     * 레거시 SHA-256 해시와 평문을 비교한다. <b>ADR-LOGIN-011 결정 대기로 미구현.</b>
     * Verifies against the legacy unsalted SHA-256 hash. <b>Unimplemented pending
     * the ADR-LOGIN-011 ruling.</b>
     *
     * @param raw        평문 비밀번호 / the raw password
     * @param legacyHash 레거시 해시 / the legacy hash
     * @return 일치 여부 / true when they match
     * @throws UnsupportedOperationException 항상 (결정 전) / always, until the ruling lands
     */
    // req: ADR-LOGIN-011, RISK-L01
    public boolean matchesLegacy(String raw, String legacyHash) {
        throw new UnsupportedOperationException(
                "Legacy hash verification is blocked pending the ADR-LOGIN-011 ruling "
                        + "(upgrade-on-login vs forced reset). See RISK-L01 / TM-L002: the choice "
                        + "depends on whether the legacy password database has ever been exposed.");
    }

    /**
     * 레거시 검증 경로가 활성화되어 있는지 반환한다.
     * Whether the legacy verification path is enabled by configuration.
     *
     * @return 활성 여부 / true when enabled
     */
    // req: ADR-LOGIN-011
    public boolean legacyVerificationEnabled() {
        return legacyVerificationEnabled;
    }
}
