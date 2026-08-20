package com.webcash.iris.biztalk.domain;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * 인증키(ATK) 발급. / 인증키 (ATK) generation.
 *
 * <h2>레거시 결함 대응 / the legacy behaviour being fixed</h2>
 * <p>레거시는 인증키를 <b>브라우저에서</b> 만들었다. 핸들러 이름은
 * {@code generate random 32 byte} 였지만 실제로는 62자 알파벳에서
 * {@code Math.random()} 으로 20자를 뽑았다(D-I4). {@code Math.random()} 은 CSPRNG 이
 * 아니며 V8 의 xorshift128+ 상태는 짧은 출력 열로 복원된다 — 한 기관의 키를 관찰하면
 * <b>다음에 발급될 키를 예측</b>할 수 있다. 발급이 서버에 있으면 그 예측 경로 자체가
 * 없어진다.</p>
 * <p>The legacy generated the key <b>in the browser</b>: a handler named
 * {@code generate random 32 byte} that actually drew 20 characters from a 62-character alphabet
 * with {@code Math.random()} (D-I4). That is not a CSPRNG — V8's xorshift128+ state is
 * recoverable from a short output sequence, so observing one institution's key lets the
 * <b>next key issued be predicted</b>. Moving generation to the server removes the path.</p>
 *
 * <h2>왜 27자 Base62 인가 / why 27 Base62 characters</h2>
 * <p>62자 알파벳에서 27자를 뽑으면 27 × log₂62 ≈ <b>160.7비트</b>로,
 * NFR-SEC-CRED-I01 의 128비트 하한을 넉넉히 넘긴다. 레거시 입력칸의
 * {@code maxlength="32"} 안에 들어가므로 두 시스템이 같은 컬럼을 쓰는 동안에도
 * 안전하다(ADR-INST-016). 영숫자만 쓰는 것은 고객사가 이 값을 설정 파일이나 URL 에
 * 넣을 때 인코딩·인용 문제를 만들지 않기 위한 것이며, 레거시 알파벳이 유일하게
 * 옳게 한 선택이었다(ADR-INST-015 §2.2).</p>
 * <p>27 characters over a 62-character alphabet is 27 × log₂62 ≈ <b>160.7 bits</b>, comfortably
 * above the 128-bit floor in NFR-SEC-CRED-I01, and it fits the legacy field's
 * {@code maxlength="32"} so both systems can keep writing the same column (ADR-INST-016).
 * Staying alphanumeric keeps the value free of encoding and quoting hazards where customers
 * embed it in configuration — the one thing the legacy alphabet got right (ADR-INST-015 §2.2).</p>
 *
 * <p>{@link SecureRandom#nextInt(int)} 를 쓰는 이유는 <b>편향</b>이다.
 * {@code nextInt() % 62} 는 62 가 2의 거듭제곱이 아니므로 앞쪽 문자가 미세하게 더 자주
 * 나온다. {@code nextInt(bound)} 는 거부 표집으로 그 편향을 제거한다.</p>
 * <p>{@link SecureRandom#nextInt(int)} is used because of <b>bias</b>: {@code nextInt() % 62}
 * favours the earlier characters slightly, since 62 is not a power of two. {@code nextInt(bound)}
 * removes that with rejection sampling.</p>
 *
 * // source: biztalk_admin_01.js — btn_generate_code: randomGenerator(20) over Math.random()
 * // req: FR-ATK-001, FR-ATK-005, NFR-SEC-CRED-I01, ADR-INST-015
 */
@Component
public class AtkGenerator {

    /**
     * 발급 알파벳 — Base62. / The generation alphabet, Base62.
     *
     * <p>레거시와 같은 62자다. 문자 집합을 바꾸면 기존 키와 새 키를 값만으로 구분할 수
     * 있게 되는데, 그것은 "어느 기관이 약한 키를 쓰는가" 를 외부에서 판별할 수 있게
     * 만드는 것이므로 바꾸지 않는다.</p>
     * <p>The same 62 characters as the legacy. Changing the set would make old and new keys
     * distinguishable from the value alone, which would let an outsider tell which institutions
     * still hold a weak key, so it is left as it is.</p>
     */
    private static final String ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    /** 발급 길이 — 160비트 이상. / Generated length, carrying more than 160 bits. */
    public static final int LENGTH = 27;

    private final SecureRandom random;

    /**
     * 기본 난수원으로 발급기를 만든다. / Creates the generator with the default entropy source.
     */
    // req: FR-ATK-001
    public AtkGenerator() {
        this(new SecureRandom());
    }

    /**
     * 난수원을 지정해 발급기를 만든다. / Creates the generator with a given entropy source.
     *
     * <p>시험에서 난수원을 고정하기 위한 생성자다. 운영 배선은 인자 없는 생성자를 쓴다.</p>
     * <p>For pinning the source in tests; the production wiring uses the no-argument
     * constructor.</p>
     *
     * @param random 난수원 / the entropy source
     */
    // req: FR-ATK-001
    AtkGenerator(SecureRandom random) {
        this.random = random;
    }

    /**
     * 새 인증키를 발급한다. / Issues a new 인증키.
     *
     * @return 27자 Base62 인증키 / a 27-character Base62 key
     */
    // req: FR-ATK-001, NFR-SEC-CRED-I01
    public String generate() {
        StringBuilder key = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            key.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return key.toString();
    }

    /**
     * 이 발급기가 만들 수 있는 형태인지 검사한다. / Whether a value has the generated shape.
     *
     * <p>쓰기 경로에서 <b>마스킹된 값이 되돌아오는 것</b>을 막는 데 쓴다. 화면은 인증키를
     * 마스킹된 상태로 받으므로({@link AtkMasker}), 그 문자열이 저장 요청에 실려 돌아오면
     * 별표가 그대로 키가 되어 <b>고객사 연동이 즉시 끊긴다</b>. 별표는 이 알파벳에 없으므로
     * 여기서 걸린다.</p>
     * <p>Used on the write path to stop a <b>masked value being written back</b>. The screen
     * receives the key masked ({@link AtkMasker}), and if that string returned in a save request
     * the asterisks would become the key and <b>break the customer's integration immediately</b>.
     * Asterisks are not in this alphabet, so they are refused here.</p>
     *
     * @param candidate 검사할 값 / the value to check
     * @return 형태가 맞으면 true / true when the shape matches
     */
    // req: FR-ATK-002, FR-ATK-006
    public static boolean isWellFormed(String candidate) {
        if (candidate == null || candidate.length() != LENGTH) {
            return false;
        }
        for (int i = 0; i < candidate.length(); i++) {
            if (ALPHABET.indexOf(candidate.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }
}
