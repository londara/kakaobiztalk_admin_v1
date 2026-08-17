package com.webcash.iris.auth.crypto;

import com.webcash.iris.auth.domain.PasswordPolicy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
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
     * 레거시 SHA-256 해시와 평문을 비교한다. / Verifies against the legacy SHA-256 hash.
     *
     * <h2>ADR-LOGIN-011 결정 / the ruling</h2>
     * <p>PM 결정: 레거시 {@code PWD} 컬럼을 그대로 사용한다(상향 마이그레이션·강제 초기화
     * 모두 채택하지 않음). 따라서 레거시 알고리즘을 <b>바이트 단위로 재현</b>한다.</p>
     * <p>PM decision: use the legacy {@code PWD} column as-is (neither upgrade-on-login nor forced
     * reset). The legacy algorithm is therefore reproduced exactly.</p>
     *
     * <h2>레거시 알고리즘 (실측 확인) / the legacy algorithm, verified</h2>
     * <p>{@code JexMessageDigest.getHashString(SHA_256, pwd)} 를 {@code JexCore.0.3.0.jar}
     * 역어셈블로 확인했다:</p>
     * <pre>Base64( SHA-256( pwd.getBytes() ) )</pre>
     * <p>솔트 없음, 반복 없음, 표준 Base64(패딩 포함) — 저장값 길이 44자와 일치한다
     * (SHA-256 32바이트 → Base64 44자). {@code apc_login_proc_act.jsp} 은
     * {@code strHash.equals(oldPwd)} 로 단순 비교했다.</p>
     * <p>No salt, no iteration, standard Base64 with padding — consistent with the stored length
     * of 44 (32 bytes → 44 Base64 chars). The legacy compared with {@code strHash.equals(oldPwd)}.</p>
     *
     * <p><b>문자셋 가정 (AMB-L06)</b>: 레거시 {@code String.getBytes()} 는 플랫폼 기본
     * 문자셋을 사용했다. UTF-8 로 가정한다 — ASCII 비밀번호에는 영향이 없고, 비ASCII
     * 문자를 포함한 비밀번호에서만 문제가 된다. 대상 서버의 기본 문자셋 확인이 필요하다.</p>
     * <p><b>Charset assumption (AMB-L06)</b>: the legacy used the platform default charset. UTF-8
     * is assumed here — immaterial for ASCII passwords, relevant only for non-ASCII ones; the
     * target server's default charset needs confirming.</p>
     *
     * <p>이 방식은 레거시 결함 L2(솔트·작업계수 없음)를 <b>그대로 유지</b>한다. 보안상
     * 열등하나 PM 이 데이터 호환을 우선했다. 향후 상향 마이그레이션 여지를 위해 로그인
     * 성공 시 Argon2id 로 재해시하는 것은 별도 결정 사항이다.</p>
     * <p>This preserves legacy defect L2 (no salt, no work factor) by design; the PM prioritised
     * data compatibility. Re-hashing to Argon2id on successful login remains a separate decision.</p>
     *
     * @param raw        평문 비밀번호 / the raw password
     * @param legacyHash 레거시 해시 (Base64 SHA-256) / the legacy hash
     * @return 일치 여부 / true when they match
     */
    // source: JexCore.0.3.0.jar — JexMessageDigest.getHashString → Base64(SHA-256(bytes))
    // source: apc_login_proc_act.jsp:117,123 — strHash.equals(oldPwd)
    // req: ADR-LOGIN-011, FR-LOGIN-002
    public boolean matchesLegacy(String raw, String legacyHash) {
        if (!legacyVerificationEnabled) {
            throw new UnsupportedOperationException(
                    "Legacy hash verification is disabled (iris.auth.legacy-hash-verification.enabled).");
        }
        if (raw == null || legacyHash == null || legacyHash.isBlank()) {
            return false;
        }
        String computed = legacySha256Base64(raw);
        // 상수 시간 비교. 레거시의 String.equals 는 조기 반환으로 미세한 타이밍 차이를
        // 노출했다(이론적). MessageDigest.isEqual 로 그 여지를 없앤다.
        // Constant-time comparison; the legacy String.equals returned early. isEqual removes the
        // (theoretical) timing signal.
        return MessageDigest.isEqual(
                computed.getBytes(StandardCharsets.UTF_8),
                legacyHash.trim().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 레거시 해시를 계산한다. / Computes the legacy hash.
     *
     * @param raw 평문 / the raw password
     * @return {@code Base64(SHA-256(raw))}
     */
    // source: JexMessageDigest.getHashString(SHA_256, String)
    // req: ADR-LOGIN-011
    public static String legacySha256Base64(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 은 모든 JVM 에 존재한다. / SHA-256 exists on every JVM.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
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
