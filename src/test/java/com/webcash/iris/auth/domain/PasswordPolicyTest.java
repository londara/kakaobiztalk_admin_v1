package com.webcash.iris.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link PasswordPolicy} 단위 테스트 — 레거시 결함 L6·L9 회귀 방지.
 * Unit tests for {@link PasswordPolicy} — regression guards for legacy defects L6 and L9.
 *
 * <p><b>L6</b>: 레거시 강도 검사는 {@code kisalib.Cracklib} 호출이 주석 처리되어
 * 모든 비밀번호에 빈 결과를 반환했다. 즉 <b>어떤 비밀번호도 통과</b>했다.
 * 아래 {@code rejects*} 테스트들은 레거시 구현에 대해 전부 실패한다.</p>
 * <p><b>L6</b>: the legacy strength check had its {@code kisalib.Cracklib} call
 * commented out and returned an empty result for every input — meaning <b>every</b>
 * password passed. Every {@code rejects*} test below fails against the legacy.</p>
 *
 * <p><b>L9</b>: 로그인 화면이 {@code maxlength="15"} 로 비밀번호 길이를 제한했다.</p>
 * <p><b>L9</b>: the login screen capped passwords at 15 characters.</p>
 *
 * // source: apm_0001_01_r001_act.jsp, apm_0001_01_view.jsp
 * // req: FR-PWD-003, FR-PWD-004, FR-PWD-005
 */
class PasswordPolicyTest {

    private static final String EMAIL = "jaemin.nam@example.com";

    private final PasswordPolicy policy = new PasswordPolicy(3);

    /** 테스트용 해시 대역 — 평문 비교로 대체한다. / Test double: compares raw values. */
    private final PasswordPolicy.PasswordMatcher matcher =
            (raw, hash) -> ("hash::" + raw).equals(hash);

    private List<String> validate(String candidate) {
        return policy.validate(candidate, EMAIL, List.of(), matcher);
    }

    @Test
    @DisplayName("정책을 만족하는 비밀번호는 통과한다 / a compliant password passes")
    void compliantPasswordPasses() {
        assertThat(validate("Tr0ubled-Kettle!9")).isEmpty();
    }

    @Test
    @DisplayName("L9 회귀: 15자를 넘는 비밀번호를 허용한다 / L9 regression: accepts >15 characters")
        // req: FR-PWD-005
    void acceptsPasswordsLongerThanLegacyCap() {
        String seventyChars = "Tr0ubled-Kettle!9-Windward-Lantern#42-Quiet-Harbour~Bramble7-Ossify99";
        assertThat(seventyChars.length()).isGreaterThan(64);
        assertThat(validate(seventyChars)).isEmpty();
    }

    @Test
    @DisplayName("최소 길이 미달은 거절한다 / rejects passwords below the minimum length")
        // req: FR-PWD-005
    void rejectsTooShort() {
        assertThat(validate("Sh0rt!Pass")).isNotEmpty();   // 10 chars
    }

    @Test
    @DisplayName("최대 길이 초과는 거절한다 / rejects passwords above the maximum length")
        // req: FR-PWD-005
    void rejectsTooLong() {
        assertThat(validate("A1!" + "x".repeat(200))).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "onlylowercaseletters",      // 1 class
            "ONLYUPPERCASELETTERS",      // 1 class
            "123456789012345678",        // 1 class
            "lowercaseandnumbers12"      // 2 classes
    })
    @DisplayName("L6 회귀: 문자 종류가 부족하면 거절한다 / L6 regression: rejects insufficient character classes")
        // req: FR-PWD-003
    void rejectsInsufficientCharacterClasses(String weak) {
        assertThat(validate(weak)).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"MyPassword123!", "Passw0rd!Passw0rd", "Administrator99!"})
    @DisplayName("L6 회귀: 널리 알려진 취약 비밀번호는 거절한다 / L6 regression: rejects well-known weak passwords")
        // req: FR-PWD-003
    void rejectsObviouslyWeak(String weak) {
        assertThat(validate(weak)).isNotEmpty();
    }

    @Test
    @DisplayName("L6 회귀: 아이디를 포함하면 거절한다 / L6 regression: rejects a password containing the account id")
        // req: FR-PWD-003
    void rejectsPasswordContainingAccountId() {
        assertThat(validate("Jaemin.Nam-2026!")).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Qw3rty-abcd-Zx!", "Zephyr-4321-Kl!", "Marsh-wxyz-B7!!"})
    @DisplayName("L6 회귀: 4자 이상 연속 문자는 거절한다 / L6 regression: rejects runs of 4+ sequential characters")
        // req: FR-PWD-003
    void rejectsSequentialRuns(String weak) {
        assertThat(policy.hasSequentialRun(weak)).isTrue();
        assertThat(validate(weak)).isNotEmpty();
    }

    @Test
    @DisplayName("최근 3개 비밀번호 재사용을 거절한다 / rejects reuse of the last 3 passwords")
        // req: FR-PWD-004
    void rejectsRecentReuse() {
        List<String> history = List.of(
                "hash::Tr0ubled-Kettle!9",
                "hash::Windward-Lantern#42",
                "hash::Quiet-Harbour~7");
        assertThat(policy.validate("Tr0ubled-Kettle!9", EMAIL, history, matcher)).isNotEmpty();
        assertThat(policy.validate("Quiet-Harbour~7", EMAIL, history, matcher)).isNotEmpty();
    }

    @Test
    @DisplayName("이력 깊이를 넘어선 비밀번호는 재사용 가능하다 / a password beyond the history depth may be reused")
        // req: FR-PWD-004
    void allowsReuseBeyondHistoryDepth() {
        List<String> history = List.of(
                "hash::Aardvark-Tumble!1",
                "hash::Bramble-Ossify!2",
                "hash::Cormorant-Vex!3",
                "hash::Tr0ubled-Kettle!9");   // 4th — outside the depth of 3
        assertThat(policy.validate("Tr0ubled-Kettle!9", EMAIL, history, matcher)).isEmpty();
    }

    @Test
    @DisplayName("위반 메시지에 비밀번호가 노출되지 않는다 / violation messages never echo the password")
        // req: NFR-SEC-LOG-L01
    void violationMessagesDoNotEchoThePassword() {
        String secret = "sekrit";
        List<String> violations = validate(secret);
        assertThat(violations).isNotEmpty();
        assertThat(violations).noneMatch(v -> v.contains(secret));
    }

    @Test
    @DisplayName("null 비밀번호는 거절한다 / rejects a null password")
    void rejectsNull() {
        assertThat(validate(null)).isNotEmpty();
    }

    @Test
    @DisplayName("문자 종류 계수가 정확하다 / counts character classes correctly")
    void countsCharacterClasses() {
        assertThat(policy.characterClasses("abcdef")).isEqualTo(1);
        assertThat(policy.characterClasses("abcDEF")).isEqualTo(2);
        assertThat(policy.characterClasses("abcDEF1")).isEqualTo(3);
        assertThat(policy.characterClasses("abcDEF1!")).isEqualTo(4);
    }
}
