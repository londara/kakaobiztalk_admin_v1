package com.webcash.iris.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link ActorRef} 검증. / Verification for {@link ActorRef}.
 *
 * // req: NFR-SEC-LOG-L01, NFR-SEC-LOG-D01
 */
class ActorRefTest {

    @Test
    @DisplayName("이메일이 그대로 남지 않는다 / the email does not survive")
    // req: NFR-SEC-LOG-L01
    void doesNotLeakEmail() {
        // 레거시는 중복 로그인마다 이메일을 debug 로 남겼고, NFR-SEC-LOG-L01 은 그 실패를
        // 막기 위해 존재한다. 가명값이 원본을 포함하면 규칙이 무의미해진다.
        // The legacy logged email at debug on every duplicate login; NFR-SEC-LOG-L01 exists to
        // prevent that. A pseudonym containing the original would make the rule pointless.
        String email = "operator@example.com";
        String ref = ActorRef.of(email);

        assertThat(ref).doesNotContain(email);
        assertThat(ref).doesNotContain("operator");
        assertThat(ref).doesNotContain("example.com");
        assertThat(ref).doesNotContain("@");
    }

    @Test
    @DisplayName("같은 이메일은 같은 참조가 된다 / the same email yields the same reference")
    // req: NFR-SEC-LOG-L01
    void isStable() {
        // 안정적이지 않으면 한 사람의 여러 요청을 로그에서 이어 볼 수 없고, 가명화의
        // 실용적 가치가 사라진다.
        // Without stability one person's requests cannot be followed through the log, which is
        // the practical value the pseudonym is meant to retain.
        assertThat(ActorRef.of("a@example.com")).isEqualTo(ActorRef.of("a@example.com"));
    }

    @Test
    @DisplayName("다른 이메일은 다른 참조가 된다 / different emails yield different references")
    // req: NFR-SEC-LOG-L01
    void distinguishes() {
        assertThat(ActorRef.of("a@example.com")).isNotEqualTo(ActorRef.of("b@example.com"));
    }

    @Test
    @DisplayName("대소문자와 공백을 정규화한다 / normalises case and surrounding space")
    // req: NFR-SEC-LOG-L01
    void normalises() {
        // 같은 사람이 다른 참조를 갖게 되면 로그에서 두 사람처럼 보인다.
        // The same person getting two references would read as two people in the log.
        assertThat(ActorRef.of("  Operator@Example.COM  ")).isEqualTo(ActorRef.of("operator@example.com"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("빈 값은 anon 이다 / blank input is anon")
    // req: NFR-SEC-LOG-L01
    void blankIsAnonymous(String input) {
        assertThat(ActorRef.of(input)).isEqualTo(ActorRef.ANONYMOUS);
    }

    @Test
    @DisplayName("null 은 anon 이다 / null is anon")
    // req: NFR-SEC-LOG-L01
    void nullIsAnonymous() {
        assertThat(ActorRef.of(null)).isEqualTo(ActorRef.ANONYMOUS);
    }

    @Test
    @DisplayName("참조는 짧고 고정 길이다 / the reference is short and fixed-length")
    // req: NFR-SEC-LOG-L01
    void isShortAndFixedLength() {
        assertThat(ActorRef.of("someone.with.a.very.long.address@a-long-domain.example.com"))
                .hasSize(8);
    }
}
