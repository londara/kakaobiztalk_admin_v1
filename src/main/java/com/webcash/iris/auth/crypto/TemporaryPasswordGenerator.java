package com.webcash.iris.auth.crypto;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * 운영자 초기화용 임시 비밀번호 생성. / Generates temporary passwords for operator resets.
 *
 * <p>생성된 비밀번호는 {@link com.webcash.iris.auth.domain.PasswordPolicy} 를 반드시
 * 통과해야 한다. 정책을 만족하지 않는 임시 비밀번호를 발급하면, 사용자가 강제 변경
 * 화면에서 그것을 입력해야 하는데 정책이 거절하는 모순이 생긴다.</p>
 * <p>The generated password must satisfy {@link
 * com.webcash.iris.auth.domain.PasswordPolicy}. Issuing one that does not creates a
 * contradiction: the user must enter it on the forced-change screen, where the policy
 * would reject it.</p>
 *
 * <p><b>혼동되는 문자를 제외한다.</b> 임시 비밀번호는 전화나 대면으로 구두 전달되는
 * 경우가 많아 {@code 0/O}, {@code 1/l/I} 를 포함하면 전달 실패가 잦다. 문자 집합을
 * 줄이는 만큼 엔트로피가 감소하므로 길이로 보상한다.</p>
 * <p><b>Ambiguous characters are excluded.</b> Temporary passwords are often read aloud,
 * and {@code 0/O} or {@code 1/l/I} cause transcription failures. Shrinking the alphabet
 * costs entropy, so length compensates.</p>
 *
 * // req: FR-PWD-007, FR-PWD-003, FR-PWD-005
 */
@Component
public class TemporaryPasswordGenerator {

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";   // I, O 제외 / excluding I, O
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";   // l, o 제외 / excluding l, o
    private static final String DIGIT = "23456789";                   // 0, 1 제외 / excluding 0, 1
    private static final String SPECIAL = "!#$%&*+-=?@";              // 셸에서 안전한 집합 / shell-safe set

    /**
     * 길이. 축소된 문자 집합을 보상하기 위해 정책 최소값(12)보다 길게 잡는다.
     * Length, set above the policy minimum of 12 to compensate for the reduced alphabet.
     */
    private static final int LENGTH = 16;

    private final SecureRandom random = new SecureRandom();

    /**
     * 임시 비밀번호를 생성한다. / Generates a temporary password.
     *
     * <p>각 문자 종류에서 최소 1자를 먼저 확보한 뒤 나머지를 채우고 섞는다.
     * 무작위로만 채우면 4종류 요구를 만족하지 못하는 결과가 나올 수 있다.</p>
     * <p>One character from each class is placed first, then the remainder is filled and
     * shuffled. Filling purely at random can yield a result that fails the
     * character-class requirement.</p>
     *
     * @return 정책을 만족하는 임시 비밀번호 / a temporary password satisfying the policy
     */
    // req: FR-PWD-007, FR-PWD-003
    public String generate() {
        String all = UPPER + LOWER + DIGIT + SPECIAL;
        char[] out = new char[LENGTH];

        // 4종류를 각각 1자 이상 보장한다 / guarantee at least one of each class
        out[0] = pick(UPPER);
        out[1] = pick(LOWER);
        out[2] = pick(DIGIT);
        out[3] = pick(SPECIAL);
        for (int i = 4; i < LENGTH; i++) {
            out[i] = pick(all);
        }

        // Fisher-Yates. 앞 4자리에 종류가 고정되어 있으면 패턴이 노출된다.
        // Fisher-Yates: leaving the classes fixed in the first four positions would
        // expose a predictable pattern.
        for (int i = out.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char tmp = out[i];
            out[i] = out[j];
            out[j] = tmp;
        }
        return new String(out);
    }

    private char pick(String alphabet) {
        return alphabet.charAt(random.nextInt(alphabet.length()));
    }
}
