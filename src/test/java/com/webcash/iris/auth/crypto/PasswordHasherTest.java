package com.webcash.iris.auth.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link PasswordHasher} 단위 테스트 — 레거시 결함 L2 회귀 방지.
 * Unit tests for {@link PasswordHasher} — regression guard for legacy defect L2.
 *
 * <p>레거시는 솔트 없는 SHA-256 을 사용했다. 그 결과 <b>동일한 비밀번호가 언제나
 * 동일한 해시</b>를 만들어 레인보우 테이블이 통했고, 작업 계수가 없어 오프라인 추측이
 * 하드웨어 속도로 수행됐다. 아래 {@code producesDifferentHash...} 테스트는 레거시
 * 구현에 대해 반드시 실패한다 — 그 점이 이 테스트의 목적이다.</p>
 * <p>The legacy used unsalted SHA-256, so <b>the same password always produced the
 * same hash</b> (rainbow tables apply) with no work factor (offline guessing runs at
 * hardware speed). The {@code producesDifferentHash...} test necessarily fails
 * against the legacy implementation, which is precisely its purpose.</p>
 *
 * <p>Argon2id 파라미터는 테스트 속도를 위해 최소값을 쓴다. 운영 값은 배포 환경에서
 * 실측하여 조정해야 한다(RISK-L07).</p>
 * <p>Minimal Argon2id parameters are used for test speed; production values must be
 * tuned against measured capacity (RISK-L07).</p>
 *
 * // source: apc_login_proc_act.jsp — JexMessageDigest.getHashString(SHA_256, pwd)
 * // req: FR-LOGIN-005, ADR-LOGIN-011
 */
class PasswordHasherTest {

    private static final String PASSWORD = "Tr0ubled-Kettle!9";

    /** 테스트 속도를 위한 최소 파라미터. / Minimal parameters, for test speed only. */
    private PasswordHasher hasher(boolean legacyEnabled) {
        return new PasswordHasher(16, 32, 1, 1024, 1, legacyEnabled);
    }

    @Test
    @DisplayName("해시 후 검증이 통과한다 / a hashed password verifies")
        // req: FR-LOGIN-005
    void hashThenMatch() {
        PasswordHasher hasher = hasher(false);
        String hash = hasher.hash(PASSWORD);
        assertThat(hasher.matches(PASSWORD, hash)).isTrue();
    }

    @Test
    @DisplayName("L2 회귀: 같은 비밀번호가 매번 다른 해시를 만든다 / L2 regression: the same password yields a different hash each time")
        // req: FR-LOGIN-005
    void producesDifferentHashForSamePasswordEachTime() {
        PasswordHasher hasher = hasher(false);
        String first = hasher.hash(PASSWORD);
        String second = hasher.hash(PASSWORD);

        // 솔트가 있으므로 두 해시는 달라야 한다. 레거시(솔트 없는 SHA-256)에서는 같았다.
        // With a salt the two hashes must differ. Under the legacy scheme they were equal.
        assertThat(first).isNotEqualTo(second);
        assertThat(hasher.matches(PASSWORD, first)).isTrue();
        assertThat(hasher.matches(PASSWORD, second)).isTrue();
    }

    @Test
    @DisplayName("해시 형식이 Argon2id 임을 확인한다 / the hash advertises Argon2id")
        // req: FR-LOGIN-005
    void hashIsArgon2id() {
        assertThat(hasher(false).hash(PASSWORD)).startsWith("$argon2id$");
    }

    @Test
    @DisplayName("틀린 비밀번호는 거절한다 / rejects a wrong password")
    void rejectsWrongPassword() {
        PasswordHasher hasher = hasher(false);
        String hash = hasher.hash(PASSWORD);
        assertThat(hasher.matches("Tr0ubled-Kettle!8", hash)).isFalse();
    }

    @Test
    @DisplayName("null 또는 빈 해시는 불일치로 처리한다 / null or blank input does not match")
    void nullAndBlankDoNotMatch() {
        PasswordHasher hasher = hasher(false);
        assertThat(hasher.matches(null, "$argon2id$whatever")).isFalse();
        assertThat(hasher.matches(PASSWORD, null)).isFalse();
        assertThat(hasher.matches(PASSWORD, "  ")).isFalse();
    }

    @Test
    @DisplayName("레거시 검증 경로는 기본 비활성이다 / the legacy verification path is disabled by default")
        // req: ADR-LOGIN-011
    void legacyVerificationDisabledByDefault() {
        assertThat(hasher(false).legacyVerificationEnabled()).isFalse();
    }

    @Test
    @DisplayName("레거시 검증은 활성 시 올바른 비밀번호를 통과시킨다 / legacy verification accepts the right password when enabled")
        // req: ADR-LOGIN-011 (PM 결정: 레거시 PWD 사용), FR-LOGIN-002
    void legacyVerificationAcceptsRightPassword() {
        // PM 결정(2026-08-17): matchesLegacy 는 이제 Base64(SHA-256(pwd)) 를 검증한다.
        // matchesLegacy now verifies Base64(SHA-256(pwd)).
        String legacyHash = PasswordHasher.legacySha256Base64(PASSWORD);
        assertThat(hasher(true).matchesLegacy(PASSWORD, legacyHash)).isTrue();
        assertThat(hasher(true).matchesLegacy("Tr0ubled-Kettle!8", legacyHash)).isFalse();
    }

    @Test
    @DisplayName("레거시 검증이 비활성이면 예외를 던진다 / legacy verification throws when disabled")
        // req: ADR-LOGIN-011
    void legacyVerificationThrowsWhenDisabled() {
        String legacyHash = PasswordHasher.legacySha256Base64(PASSWORD);
        assertThatThrownBy(() -> hasher(false).matchesLegacy(PASSWORD, legacyHash))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("레거시 해시는 44자 Base64(SHA-256)이다 / the legacy hash is 44-char Base64 SHA-256")
        // req: ADR-LOGIN-011
    void legacyHashFormat() {
        String h = PasswordHasher.legacySha256Base64(PASSWORD);
        assertThat(h).hasSize(44).endsWith("=");
    }
}
