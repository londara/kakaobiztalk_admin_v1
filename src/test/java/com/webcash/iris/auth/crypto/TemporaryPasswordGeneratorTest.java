package com.webcash.iris.auth.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import com.webcash.iris.auth.domain.PasswordPolicy;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link TemporaryPasswordGenerator} 단위 테스트. / Unit tests for {@link TemporaryPasswordGenerator}.
 *
 * <p>임시 비밀번호는 <b>구두로 전달</b>되고, 사용자는 그것을 강제 변경 화면에 입력해야 한다.
 * 따라서 두 가지가 동시에 성립해야 한다: 전달 가능해야 하고(혼동 문자 배제),
 * {@link PasswordPolicy} 를 통과해야 한다. 후자가 깨지면 운영자가 발급한 비밀번호를
 * 사용자가 입력했는데 시스템이 거절하는, 복구 경로 자체가 막히는 상태가 된다.</p>
 * <p>A temporary password is <b>read aloud</b> and then typed into the forced-change screen, so two
 * things must hold at once: it must survive transcription (no ambiguous characters) and it must
 * satisfy {@link PasswordPolicy}. If the second breaks, the operator issues a password that the
 * system then refuses — the recovery path itself is blocked.</p>
 *
 * <p><b>무작위 출력의 시험 방식.</b> {@link java.security.SecureRandom} 이 내부에 있어 주입할 수
 * 없으므로, 이 시험은 <b>구성상 보장되는 성질</b>만 단정한다. 확률적으로만 성립하는 성질을
 * 단정하면 시험 자체가 간헐적으로 실패한다 — 그 경계는 아래 마지막 시험이 명시한다.</p>
 * <p><b>How random output is tested.</b> {@link java.security.SecureRandom} is internal and cannot be
 * injected, so these tests assert only what construction guarantees. Asserting a merely probabilistic
 * property would make the test itself intermittent — the last test states where that boundary lies.</p>
 *
 * // req: FR-PWD-007, FR-PWD-003, FR-PWD-005
 */
class TemporaryPasswordGeneratorTest {

    private final TemporaryPasswordGenerator generator = new TemporaryPasswordGenerator();

    /** 정책 이력 깊이는 기본값 3 을 쓴다. / The policy uses the default history depth of 3. */
    private final PasswordPolicy policy = new PasswordPolicy(3);

    /** 표본 수. 구성상 보장되는 성질만 검사하므로 간헐 실패 없이 크게 잡을 수 있다. */
    /** Sample size; safe to keep large because only construction-guaranteed properties are asserted. */
    private static final int SAMPLES = 500;

    @Test
    @DisplayName("길이는 16자다 / the length is 16")
    // req: FR-PWD-007
    void lengthIsSixteen() {
        // 정책 최소값은 12 이지만 문자 집합을 축소했으므로 길이로 엔트로피를 보상한다.
        // The policy minimum is 12; the reduced alphabet is compensated for with length.
        assertThat(generator.generate()).hasSize(16);
        assertThat(16).isGreaterThan(PasswordPolicy.MIN_LENGTH);
    }

    @Test
    @DisplayName("혼동되는 문자를 포함하지 않는다 / contains no ambiguous characters")
    // req: FR-PWD-007
    void excludesAmbiguousCharacters() {
        // 0/O 와 1/l/I 는 구두 전달에서 오기의 주 원인이다. 이 시험이 실패하면 임시 비밀번호는
        // 여전히 정책을 통과하지만 전화로 전달되지 않는다 — 조용히 운영 부담으로만 나타난다.
        // 0/O and 1/l/I are the main transcription failures. If this breaks, the password still
        // satisfies the policy but cannot be dictated — the cost appears only as operator load.
        for (int i = 0; i < SAMPLES; i++) {
            assertThat(generator.generate())
                    .as("ambiguous characters must never appear")
                    .doesNotContain("0", "O", "1", "l", "I", "o");
        }
    }

    @Test
    @DisplayName("4종류 문자를 모두 포함한다 / includes all four character classes")
    // req: FR-PWD-003, FR-PWD-007
    void includesEveryCharacterClass() {
        // 생성기는 종류별 1자를 먼저 배치하므로 이것은 구성상 보장이다 — 확률이 아니다.
        // The generator seeds one character per class, so this is guaranteed by construction.
        for (int i = 0; i < SAMPLES; i++) {
            String pw = generator.generate();
            assertThat(pw).as("uppercase").matches(".*[A-Z].*");
            assertThat(pw).as("lowercase").matches(".*[a-z].*");
            assertThat(pw).as("digit").matches(".*[0-9].*");
            assertThat(pw).as("special").matches(".*[!#$%&*+\\-=?@].*");
        }
    }

    @Test
    @DisplayName("문자 종류 요구를 정책 기준으로 만족한다 / satisfies the policy's character-class rule")
    // req: FR-PWD-003
    void satisfiesPolicyCharacterClassRule() {
        // 정책이 요구하는 3종류를 4종류로 초과 충족한다. 정책 상수를 직접 참조하므로
        // REQUIRED_CHARACTER_CLASSES 가 올라가면 이 시험이 먼저 알려준다.
        //
        // characterClasses() 는 package-private 이므로 여기서 세어 상수와 비교한다 — 정책 내부
        // 구현이 아니라 <b>상수와 생성기 출력</b>의 관계를 고정하는 것이 목적이다.
        //
        // Exceeds the required three classes with four. characterClasses() is package-private, so
        // the count is done here and compared with the public constant: what matters is the relation
        // between the requirement and the generator's output, not the policy's internals.
        for (int i = 0; i < SAMPLES; i++) {
            String pw = generator.generate();
            int classes = 0;
            if (pw.matches(".*[A-Z].*")) {
                classes++;
            }
            if (pw.matches(".*[a-z].*")) {
                classes++;
            }
            if (pw.matches(".*[0-9].*")) {
                classes++;
            }
            if (pw.matches(".*[^A-Za-z0-9].*")) {
                classes++;
            }
            assertThat(classes).isGreaterThanOrEqualTo(PasswordPolicy.REQUIRED_CHARACTER_CLASSES);
        }
    }

    @Test
    @DisplayName("길이·이메일·취약목록 규칙을 위반하지 않는다 / violates no length, email or weak-list rule")
    // req: FR-PWD-003, FR-PWD-004, FR-PWD-005
    void violatesNoDeterministicPolicyRule() {
        // 이력 비교는 빈 목록으로 비활성화한다. 임시 비밀번호는 발급 시점에 이력이 없다.
        // History comparison is disabled with an empty list: a temporary password has no history.
        for (int i = 0; i < SAMPLES; i++) {
            String pw = generator.generate();
            List<String> violations =
                    policy.validate(pw, "operator@webcash.co.kr", List.of(), (raw, hash) -> false);

            // 연속문자 규칙만 확률적이므로 제외하고 단정한다 — 그 규칙은 아래 시험이 다룬다.
            // Only the sequential-run rule is probabilistic, so it is excluded here and covered below.
            assertThat(violations)
                    .filteredOn(v -> !v.contains("연속된"))
                    .as("temporary password must not violate any deterministic policy rule")
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("호출마다 다른 값을 반환한다 / returns a different value on each call")
    // req: FR-PWD-007
    void returnsDistinctValues() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < SAMPLES; i++) {
            seen.add(generator.generate());
        }
        // 16자 · 약 67자 알파벳이므로 500회 내 충돌 확률은 사실상 0 이다. 중복이 나오면
        // 무작위원이 고정된 것이며, 그것은 임시 비밀번호 전체를 예측 가능하게 만든다.
        // With 16 characters over a ~67-character alphabet a collision in 500 draws is effectively
        // impossible; a duplicate would mean the source is fixed, making every issue predictable.
        assertThat(seen).hasSize(SAMPLES);
    }

    @Test
    @DisplayName("종류가 앞 4자리에 고정되지 않는다 / classes are not pinned to the first four positions")
    // req: FR-PWD-007
    void shuffleRemovesPositionalPattern() {
        // 생성기는 종류별 1자를 0~3 위치에 넣은 뒤 Fisher-Yates 로 섞는다. 섞기가 빠지면
        // 첫 4자리가 항상 대문자·소문자·숫자·특수문자 순이 되어, 공격자가 탐색 공간을 줄인다.
        // The generator seeds positions 0–3 then shuffles. Without the shuffle the first four
        // characters would always be upper, lower, digit, special — shrinking the search space.
        boolean sawNonUpperFirst = false;
        boolean sawNonSpecialFourth = false;
        for (int i = 0; i < SAMPLES && !(sawNonUpperFirst && sawNonSpecialFourth); i++) {
            String pw = generator.generate();
            if (!Character.isUpperCase(pw.charAt(0))) {
                sawNonUpperFirst = true;
            }
            if (String.valueOf(pw.charAt(3)).matches("[^!#$%&*+\\-=?@]")) {
                sawNonSpecialFourth = true;
            }
        }
        assertThat(sawNonUpperFirst)
                .as("position 0 must not always hold the seeded uppercase character")
                .isTrue();
        assertThat(sawNonSpecialFourth)
                .as("position 3 must not always hold the seeded special character")
                .isTrue();
    }

    @Test
    @DisplayName("연속문자 규칙은 호출자가 강제한다 / the sequential-run rule is enforced by the caller")
    // req: FR-PWD-003, FR-PWD-007
    void sequentialRunRuleIsEnforcedByTheCaller() {
        // 이 시험은 <b>책임의 경계</b>를 고정한다.
        //
        // 생성기는 문자 종류 4종과 혼동문자 배제를 구성으로 보장하지만, 연속열(예: abcd)은
        // 검사하지 않는다 — 16자를 무작위로 뽑으면 약 10^-4 확률로 포함될 수 있다.
        // 그 규칙을 강제하는 곳은 유일한 호출자인
        // {@code PasswordChangeService} 다: 정책 검증을 통과할 때까지 최대 20회 재생성하고,
        // 실패하면 IllegalStateException 으로 <b>큰 소리로</b> 실패한다.
        //
        // 강제 위치가 호출자인 것은 타당하다 — 정책의 이메일 포함 검사와 이력 재사용 검사는
        // 이메일과 해시 이력을 알아야 하고, 생성기는 둘 다 모른다. 생성기 안에 루프를 또
        // 두면 규칙이 두 곳에 생기고 crypto → domain 패키지 순환이 만들어진다.
        //
        // This test pins the <b>responsibility boundary</b>. The generator guarantees character
        // classes and the exclusion of ambiguous characters by construction, but does not check for
        // sequential runs (~10^-4 per draw). That rule is enforced by the sole caller,
        // {@code PasswordChangeService}, which regenerates up to 20 times and then fails loudly.
        // The caller is the right place: the policy's email-containment and history-reuse checks
        // need an email and a hash history, and the generator knows neither. A second loop inside
        // the generator would duplicate the rule and create a crypto → domain package cycle.
        List<String> violations =
                policy.validate("Xy!abcd7Qmn23", "operator@webcash.co.kr", List.of(),
                        (raw, hash) -> false);

        assertThat(violations)
                .as("the policy rejects a four-character run; PasswordChangeService is what "
                        + "regenerates until a candidate passes")
                .anyMatch(v -> v.contains("연속된"));
    }
}
