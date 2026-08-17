package com.webcash.iris.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

/**
 * {@link OtpDevBypass} 검증. / Verification for {@link OtpDevBypass}.
 *
 * <p>이 테스트의 목적은 우회가 <b>동작하는 것</b>이 아니라 <b>탈출하지 못하는 것</b>을
 * 확인하는 데 있다. 개발 편의 기능이 운영에 새어 나가는 경로는 대체로 "기본값이 꺼져
 * 있으니 괜찮다" 는 가정 위에 만들어지며, 그 가정은 설정 파일 한 줄로 무너진다.</p>
 * <p>The point of these tests is not that the bypass <b>works</b> but that it <b>cannot
 * escape</b>. Development conveniences reach production through the assumption that being
 * off by default is sufficient, and that assumption falls to a single configuration line.</p>
 *
 * // req: FR-LOGIN-010, NFR-SEC-AUTH-L01, CONST-SEC-01
 */
class OtpDevBypassTest {

    private static MockEnvironment env(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return environment;
    }

    @Nested
    @DisplayName("안전장치 — 로컬 밖에서는 기동을 거부한다 / interlock: refuses to start outside local")
    class Interlock {

        @ParameterizedTest
        @ValueSource(strings = {"prod", "staging", "dev", "test"})
        @DisplayName("운영 계열 프로필에서 켜져 있으면 기동에 실패한다 / enabled outside local fails startup")
        // req: NFR-SEC-AUTH-L01, CONST-SEC-01
        void refusesToStartOutsideLocal(String profile) {
            OtpDevBypass bypass = new OtpDevBypass(true, env(profile));

            // 경고 후 계속 진행하지 않는다. 경고는 무시되고, 무시된 경고의 대가가
            // 인터넷에 노출된 포털의 단일 요소 인증이다.
            // It does not warn and continue: warnings get ignored, and the cost of this one
            // being ignored is single-factor auth on an internet-facing portal.
            assertThatThrownBy(bypass::verifyConfiguration)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("only permitted under the 'local' profile");
        }

        @Test
        @DisplayName("프로필이 없어도 기동에 실패한다 / no active profile also fails startup")
        // req: NFR-SEC-AUTH-L01
        void refusesToStartWithNoProfile() {
            // 프로필을 지정하지 않은 실행은 흔하며, 그것이 곧 로컬이라는 보장은 없다.
            // 여기서 허용하면 "프로필을 깜빡한 운영 배포" 가 우회 경로가 된다.
            // Running with no profile is common and is no guarantee of being local. Allowing it
            // would make "a production deploy that forgot its profile" a bypass route.
            OtpDevBypass bypass = new OtpDevBypass(true, env());

            assertThatThrownBy(bypass::verifyConfiguration)
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("local 이 포함되면 기동한다 / starts when local is among the profiles")
        // req: FR-LOGIN-010
        void startsUnderLocal() {
            OtpDevBypass bypass = new OtpDevBypass(true, env("local"));

            assertThatCode(bypass::verifyConfiguration).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("꺼져 있으면 어떤 프로필에서도 기동한다 / disabled starts under any profile")
        // req: FR-LOGIN-010
        void disabledStartsAnywhere() {
            OtpDevBypass bypass = new OtpDevBypass(false, env("prod"));

            assertThatCode(bypass::verifyConfiguration).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("활성 판정 / activation")
    class Activation {

        @Test
        @DisplayName("기본값은 비활성이다 / inactive by default")
        // req: NFR-SEC-AUTH-L01
        void inactiveByDefault() {
            assertThat(new OtpDevBypass(false, env("local")).isActive()).isFalse();
        }

        @Test
        @DisplayName("local 에서 켜면 활성이다 / active when enabled under local")
        // req: FR-LOGIN-010
        void activeUnderLocal() {
            assertThat(new OtpDevBypass(true, env("local")).isActive()).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"prod", "staging", "dev"})
        @DisplayName("기동 검증을 건너뛰어도 로컬 밖에서는 비활성이다 / inactive outside local even if startup validation is skipped")
        // req: NFR-SEC-AUTH-L01
        void inactiveOutsideLocalEvenWithoutStartupCheck(String profile) {
            // 이중 방어다. 기동 검증이 어떤 이유로든 실행되지 않은 인스턴스 — 테스트 배선,
            // 수동 생성, @PostConstruct 를 처리하지 않는 컨텍스트 — 가 인증 경로에 닿더라도
            // 여기서 닫힌다. 안전장치가 하나뿐이면 그 하나가 빠진 경로가 곧 취약점이 된다.
            //
            // Defence in depth. An instance whose startup validation never ran — a test wiring, a
            // hand-constructed object, a context that does not process @PostConstruct — is closed
            // here if it reaches the authentication path. With a single interlock, any path that
            // misses it is the vulnerability.
            OtpDevBypass bypass = new OtpDevBypass(true, env(profile));

            assertThat(bypass.isActive()).isFalse();
        }

        @Test
        @DisplayName("여러 프로필 중 local 이 있으면 활성이다 / active when local is one of several profiles")
        // req: FR-LOGIN-010
        void activeWhenLocalAmongSeveral() {
            assertThat(new OtpDevBypass(true, env("local", "mock-mail")).isActive()).isTrue();
        }
    }
}
