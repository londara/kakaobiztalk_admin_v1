package com.webcash.iris.auth.domain;

import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 비밀번호 강도 정책. / Password strength policy.
 *
 * <p><b>레거시 결함 L6 대응.</b> 레거시 강도 검사({@code apm_0001_01_r001_act.jsp})는
 * 실제 검사 라이브러리 호출({@code kisalib.Cracklib})이 주석 처리되어 있어 어떤
 * 비밀번호에도 빈 결과를 반환했다. 호출부만 보면 강도 검사가 동작하는 것처럼 보였다.
 * 이 클래스는 실제로 <b>거절</b>한다.</p>
 * <p><b>Fixes legacy defect L6.</b> The legacy strength check had its library call
 * ({@code kisalib.Cracklib}) commented out and returned an empty result for every
 * password. Reading the call site alone, it looked like strength checking worked.
 * This class actually <b>rejects</b>.</p>
 *
 * <p><b>레거시 결함 L9 대응.</b> 로그인 화면이 비밀번호를 15자로 제한하여 달성 가능한
 * 엔트로피에 상한을 두고 있었다. 최소 12자, 최대 128자로 넓힌다.</p>
 * <p><b>Fixes legacy defect L9.</b> The login screen capped passwords at 15
 * characters, capping achievable entropy. Widened to 12–128.</p>
 *
 * // source: apm_0001_01_r001_act.jsp (disabled Cracklib), apm_0001_01_view.jsp (maxlength=15)
 * // req: FR-PWD-003, FR-PWD-004, FR-PWD-005
 */
@Component
public class PasswordPolicy {

    /** 최소 길이. / Minimum length. */
    public static final int MIN_LENGTH = 12;
    /** 최대 길이. 64자 이상 요구를 충족한다. / Maximum length, satisfying the ≥64 requirement. */
    public static final int MAX_LENGTH = 128;
    /** 요구되는 문자 종류 수. / Number of distinct character classes required. */
    public static final int REQUIRED_CHARACTER_CLASSES = 3;

    /**
     * 명백히 취약한 비밀번호 목록. 운영에서는 외부 목록 파일로 대체한다.
     * Obviously weak passwords. In production this is replaced by an external list;
     * an inline set is not a substitute for a real dictionary, and is marked as such.
     */
    private static final Set<String> OBVIOUSLY_WEAK = Set.of(
            "password", "passw0rd", "qwertyuiop", "administrator",
            "letmein12345", "1q2w3e4r5t6y", "iris_admin_1", "webcash1234"
    );

    private final int historyDepth;

    /**
     * 이력 재사용 금지 깊이를 주입받아 생성한다. / Creates the policy with the history depth.
     *
     * @param historyDepth 재사용 금지 대상 과거 비밀번호 수 / number of previous passwords barred
     */
    public PasswordPolicy(@Value("${iris.auth.password-history-depth:3}") int historyDepth) {
        this.historyDepth = historyDepth;
    }

    /**
     * 비밀번호 정책 위반 사유를 반환한다. 빈 목록이면 통과.
     * Returns the policy violations for a candidate password; empty means acceptable.
     *
     * <p>예외를 던지지 않고 위반 목록을 반환하는 이유는, 사용자에게 무엇을 고쳐야
     * 하는지 한 번에 알려주기 위함이다. 다만 목록에 비밀번호 자체는 포함하지 않는다.</p>
     * <p>Returns a list rather than throwing so the user can be told everything to
     * fix at once. The password itself never appears in the returned messages.</p>
     *
     * @param candidate  검사할 비밀번호 / the candidate password
     * @param email      계정 이메일 (포함 여부 검사용) / account email, checked for inclusion
     * @param recentHashes 최근 비밀번호 해시 목록 / recent password hashes, newest first
     * @param hasher     이력 비교에 사용할 해시기 / hasher used for history comparison
     * @return 위반 사유 목록 / the list of violations, empty when acceptable
     */
    // req: FR-PWD-003, FR-PWD-004, FR-PWD-005
    public List<String> validate(String candidate,
                                 String email,
                                 List<String> recentHashes,
                                 PasswordMatcher hasher) {
        List<String> violations = new java.util.ArrayList<>();

        if (candidate == null || candidate.length() < MIN_LENGTH) {
            violations.add("비밀번호는 최소 " + MIN_LENGTH + "자 이상이어야 합니다.");
            return violations; // 길이 미달이면 이후 검사는 의미가 없다 / further checks are moot
        }
        if (candidate.length() > MAX_LENGTH) {
            violations.add("비밀번호는 최대 " + MAX_LENGTH + "자까지 허용됩니다.");
        }
        if (characterClasses(candidate) < REQUIRED_CHARACTER_CLASSES) {
            violations.add("영문 대문자·소문자·숫자·특수문자 중 "
                    + REQUIRED_CHARACTER_CLASSES + "종류 이상을 포함해야 합니다.");
        }
        if (isObviouslyWeak(candidate)) {
            violations.add("널리 알려진 취약한 비밀번호는 사용할 수 없습니다.");
        }
        if (containsEmailLocalPart(candidate, email)) {
            violations.add("비밀번호에 아이디를 포함할 수 없습니다.");
        }
        if (hasSequentialRun(candidate)) {
            violations.add("연속된 문자 또는 숫자를 4자 이상 사용할 수 없습니다.");
        }
        if (reusesRecent(candidate, recentHashes, hasher)) {
            violations.add("최근 사용한 " + historyDepth + "개의 비밀번호는 재사용할 수 없습니다.");
        }
        return violations;
    }

    /**
     * 사용된 문자 종류 수를 센다. / Counts the distinct character classes used.
     *
     * @param value 검사 대상 / the value to inspect
     * @return 문자 종류 수 (0~4) / class count, 0–4
     */
    // req: FR-PWD-003
    int characterClasses(String value) {
        boolean upper = false, lower = false, digit = false, special = false;
        for (char c : value.toCharArray()) {
            if (Character.isUpperCase(c)) {
                upper = true;
            } else if (Character.isLowerCase(c)) {
                lower = true;
            } else if (Character.isDigit(c)) {
                digit = true;
            } else {
                special = true;
            }
        }
        int count = 0;
        if (upper) count++;
        if (lower) count++;
        if (digit) count++;
        if (special) count++;
        return count;
    }

    private boolean isObviouslyWeak(String candidate) {
        String lower = candidate.toLowerCase();
        return OBVIOUSLY_WEAK.stream().anyMatch(lower::contains);
    }

    private boolean containsEmailLocalPart(String candidate, String email) {
        if (email == null || !email.contains("@")) {
            return false;
        }
        String local = email.substring(0, email.indexOf('@')).toLowerCase();
        return local.length() >= 4 && candidate.toLowerCase().contains(local);
    }

    /**
     * 4자 이상의 연속 문자/숫자 포함 여부. / Whether a run of 4+ sequential characters exists.
     *
     * <p>{@code abcd}, {@code 1234}, {@code dcba} 를 모두 잡는다.</p>
     * <p>Catches ascending and descending runs alike.</p>
     */
    boolean hasSequentialRun(String value) {
        int ascending = 1;
        int descending = 1;
        for (int i = 1; i < value.length(); i++) {
            int delta = value.charAt(i) - value.charAt(i - 1);
            ascending = (delta == 1) ? ascending + 1 : 1;
            descending = (delta == -1) ? descending + 1 : 1;
            if (ascending >= 4 || descending >= 4) {
                return true;
            }
        }
        return false;
    }

    private boolean reusesRecent(String candidate, List<String> recentHashes, PasswordMatcher hasher) {
        if (recentHashes == null || recentHashes.isEmpty() || hasher == null) {
            return false;
        }
        return recentHashes.stream()
                .limit(historyDepth)
                .anyMatch(hash -> hasher.matches(candidate, hash));
    }

    /**
     * 이력 비교를 위한 최소 인터페이스. / Minimal interface for history comparison.
     *
     * <p>정책이 해시 구현에 직접 의존하지 않도록 분리한다.</p>
     * <p>Keeps the policy independent of any particular hashing implementation.</p>
     */
    public interface PasswordMatcher {
        /**
         * 평문과 해시가 일치하는지 반환한다. / Whether the raw value matches the hash.
         *
         * @param raw  평문 비밀번호 / the raw password
         * @param hash 저장된 해시 / the stored hash
         * @return 일치 여부 / true when they match
         */
        boolean matches(String raw, String hash);
    }
}
