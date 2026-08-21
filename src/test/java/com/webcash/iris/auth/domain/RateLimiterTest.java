package com.webcash.iris.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link RateLimiter} 단위 테스트. / Unit tests for {@link RateLimiter}.
 *
 * <h2>이 시험이 존재하는 이유 / why this exists</h2>
 * <p>속도 제한은 자격증명 대입(credential stuffing)의 <b>유일한</b> 방어선이다 —
 * 계정 잠금은 잠금 자체가 서비스 거부가 되므로 한도를 낮게 잡을 수 없다. 그런데 커버리지
 * 측정 결과 이 클래스는 <b>32행 전부 미검증</b>이었다. 즉 TM-L001·TM-L016 이 완화책으로
 * 지목한 통제에 그것이 옳다는 증거가 하나도 없었다.</p>
 * <p>Rate limiting is the <b>only</b> defence against credential stuffing here, since account
 * lockout is itself a denial-of-service and so cannot be set aggressively. Coverage showed all 32
 * lines untested: the control named as the mitigation for TM-L001 and TM-L016 had no evidence
 * behind it.</p>
 *
 * <p><b>고정 한도로 시험한다.</b> 운영 기본값(계정 5 / 출처 20)이 아니라 작은 값을 주입해
 * 경계를 명확히 본다. 기본값을 그대로 쓰면 시험이 길어지고 어느 한도가 걸렸는지 흐려진다.</p>
 * <p><b>Small injected limits are used</b> rather than the production defaults (5 per account, 20
 * per source), so the boundary is unambiguous and it stays clear which limit fired.</p>
 *
 * // req: FR-LOGIN-025, RISK-L07, TM-L001, TM-L016
 */
class RateLimiterTest {

    private static final Instant T0 = Instant.parse("2026-08-21T09:00:00Z");
    private static final String IP = "10.1.1.1";

    @Nested
    @DisplayName("계정 한도 / per-account limit")
    class AccountLimit {

        @Test
        @DisplayName("한도까지는 통과한다 / attempts up to the limit are allowed")
        // req: FR-LOGIN-025
        void allowsUpToTheLimit() {
            // 한도가 3이면 3회는 통과해야 한다. off-by-one 으로 2회에서 막으면 정상 사용자가
            // 오타 두 번에 차단된다 — 통제가 장애가 되는 경우다.
            // With a limit of 3, three attempts must pass. Blocking at two would lock out a user
            // after two typos: the control becoming an outage.
            RateLimiter limiter = new RateLimiter(new MutableTestClock(T0), 3, 100);

            for (int i = 1; i <= 3; i++) {
                int attempt = i;
                assertThatCode(() -> limiter.checkAndRecord("user@example.com", IP))
                        .as("attempt %d of 3 must be allowed", attempt)
                        .doesNotThrowAnyException();
            }
        }

        @Test
        @DisplayName("한도를 넘으면 RATE_LIMITED 로 거절한다 / exceeding the limit raises RATE_LIMITED")
        // req: FR-LOGIN-025, TM-L001
        void refusesBeyondTheLimit() {
            RateLimiter limiter = new RateLimiter(new MutableTestClock(T0), 3, 100);
            for (int i = 0; i < 3; i++) {
                limiter.checkAndRecord("user@example.com", IP);
            }

            assertThatThrownBy(() -> limiter.checkAndRecord("user@example.com", IP))
                    .isInstanceOf(AuthenticationException.class)
                    .extracting(e -> ((AuthenticationException) e).reason())
                    .isEqualTo(AuthFailureReason.RATE_LIMITED);
        }

        @Test
        @DisplayName("대소문자를 바꿔도 한도를 우회할 수 없다 / changing case cannot multiply the allowance")
        // req: FR-LOGIN-025
        void caseVariationDoesNotMultiplyTheAllowance() {
            // 이메일은 대소문자를 구분하지 않으므로 같은 계정이다. 키가 구분하면 대입 공격자가
            // 대문자 조합만 바꿔 한도를 사실상 무한히 늘릴 수 있다.
            // The address denotes one account. A case-sensitive key would let an attacker multiply
            // the allowance almost without bound by varying capitalisation alone.
            RateLimiter limiter = new RateLimiter(new MutableTestClock(T0), 2, 100);

            limiter.checkAndRecord("user@example.com", IP);
            limiter.checkAndRecord("USER@EXAMPLE.COM", IP);

            assertThatThrownBy(() -> limiter.checkAndRecord("User@Example.com", IP))
                    .isInstanceOf(AuthenticationException.class);
        }

        @Test
        @DisplayName("다른 계정은 각자 한도를 갖는다 / each account has its own allowance")
        // req: FR-LOGIN-025
        void accountsAreIndependent() {
            RateLimiter limiter = new RateLimiter(new MutableTestClock(T0), 1, 100);

            limiter.checkAndRecord("alice@example.com", IP);
            assertThatCode(() -> limiter.checkAndRecord("bob@example.com", IP))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("null 계정도 안전하게 처리한다 / a null account is handled safely")
        // req: FR-LOGIN-025
        void nullAccountIsHandled() {
            // "unknown" 버킷으로 합산된다. 예외를 던지면 인증 경로가 500 이 되고, 상위에서
            // 잘못 처리되면 속도 제한을 건너뛰는 경로가 된다.
            // Folded into an "unknown" bucket. Throwing would surface as a 500 and, if mishandled
            // upstream, could become a path that skips rate limiting entirely.
            RateLimiter limiter = new RateLimiter(new MutableTestClock(T0), 1, 100);

            limiter.checkAndRecord(null, IP);
            assertThatThrownBy(() -> limiter.checkAndRecord(null, IP))
                    .isInstanceOf(AuthenticationException.class);
        }
    }

    @Nested
    @DisplayName("출처 한도 / per-source limit")
    class SourceLimit {

        @Test
        @DisplayName("출처 한도는 계정과 독립적으로 걸린다 / the source limit fires independently of the account")
        // req: FR-LOGIN-025, TM-L016
        void sourceLimitFiresIndependently() {
            // 이것이 분산 대입의 반대 축이다: 공격자가 계정을 매번 바꾸면 계정 한도는 걸리지
            // 않는다. 출처 한도가 없으면 한 IP 에서 무제한 계정을 시도할 수 있다.
            // The other axis: rotating the account defeats the per-account limit, so without a
            // per-source limit one address could try unlimited accounts.
            RateLimiter limiter = new RateLimiter(new MutableTestClock(T0), 100, 2);

            limiter.checkAndRecord("a@example.com", IP);
            limiter.checkAndRecord("b@example.com", IP);

            assertThatThrownBy(() -> limiter.checkAndRecord("c@example.com", IP))
                    .isInstanceOf(AuthenticationException.class)
                    .extracting(e -> ((AuthenticationException) e).reason())
                    .isEqualTo(AuthFailureReason.RATE_LIMITED);
        }

        @Test
        @DisplayName("다른 출처는 각자 한도를 갖는다 / each source has its own allowance")
        // req: FR-LOGIN-025
        void sourcesAreIndependent() {
            RateLimiter limiter = new RateLimiter(new MutableTestClock(T0), 100, 1);

            limiter.checkAndRecord("a@example.com", "10.1.1.1");
            assertThatCode(() -> limiter.checkAndRecord("b@example.com", "10.2.2.2"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("null 출처도 안전하게 처리한다 / a null source is handled safely")
        // req: FR-LOGIN-025
        void nullSourceIsHandled() {
            RateLimiter limiter = new RateLimiter(new MutableTestClock(T0), 100, 1);

            limiter.checkAndRecord("a@example.com", null);
            assertThatThrownBy(() -> limiter.checkAndRecord("b@example.com", null))
                    .isInstanceOf(AuthenticationException.class);
        }
    }

    @Nested
    @DisplayName("슬라이딩 윈도우 / sliding window")
    class Window {

        @Test
        @DisplayName("창을 벗어나면 허용량이 회복된다 / the allowance recovers once the window passes")
        // req: FR-LOGIN-025
        void allowanceRecoversAfterTheWindow() {
            MutableTestClock clock = new MutableTestClock(T0);
            RateLimiter limiter = new RateLimiter(clock, 2, 100);

            limiter.checkAndRecord("user@example.com", IP);
            limiter.checkAndRecord("user@example.com", IP);
            assertThatThrownBy(() -> limiter.checkAndRecord("user@example.com", IP))
                    .isInstanceOf(AuthenticationException.class);

            // 1분을 넘기면 이전 시도는 창 밖이다. 회복되지 않으면 정상 사용자가 영구 차단된다.
            // Past one minute the earlier attempts fall outside the window; without recovery a
            // legitimate user would be blocked permanently.
            clock.advance(Duration.ofSeconds(61));
            assertThatCode(() -> limiter.checkAndRecord("user@example.com", IP))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("고정 버킷이 아니라 슬라이딩이다 / it slides rather than resetting on a boundary")
        // req: FR-LOGIN-025
        void windowSlidesRatherThanResetting() {
            // 고정 버킷의 결함: 경계 직전과 직후에 각각 한도만큼 몰아치면 짧은 구간에 두 배가
            // 통과한다. 슬라이딩이면 30초 전 시도가 아직 창 안이므로 그 두 배 버스트가 막힌다.
            //
            // The fixed-bucket flaw: bursting the full allowance either side of a boundary passes
            // double in a short span. With a sliding window the attempt from 30s ago is still in
            // the window, so that double burst is refused.
            MutableTestClock clock = new MutableTestClock(T0);
            RateLimiter limiter = new RateLimiter(clock, 2, 100);

            limiter.checkAndRecord("user@example.com", IP);
            clock.advance(Duration.ofSeconds(30));
            limiter.checkAndRecord("user@example.com", IP);

            clock.advance(Duration.ofSeconds(31));   // T0+61s: 첫 시도만 창을 벗어났다
            assertThatCode(() -> limiter.checkAndRecord("user@example.com", IP))
                    .as("the first attempt has aged out, so one slot is free")
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> limiter.checkAndRecord("user@example.com", IP))
                    .as("the T0+30s attempt is still inside the window, so the next is refused")
                    .isInstanceOf(AuthenticationException.class);
        }
    }

    @Nested
    @DisplayName("정리 / eviction")
    class Eviction {

        @Test
        @DisplayName("만료된 키를 제거하고 개수를 반환한다 / expired keys are removed and counted")
        // req: FR-LOGIN-025
        void evictsExpiredKeys() {
            // 정리가 없으면 맵이 무한히 커져 속도 제한 자체가 메모리 소모 경로가 된다.
            // 계정 키와 출처 키가 각각 제거되므로 1회 시도에서 2가 반환된다.
            // Without eviction the maps grow without bound and the limiter becomes a
            // memory-exhaustion path. One attempt creates an account key and a source key, so a
            // single expired attempt evicts two.
            MutableTestClock clock = new MutableTestClock(T0);
            RateLimiter limiter = new RateLimiter(clock, 5, 20);
            limiter.checkAndRecord("user@example.com", IP);

            clock.advance(Duration.ofSeconds(61));
            assertThat(limiter.evictExpired()).isEqualTo(2);
        }

        @Test
        @DisplayName("창 안의 키는 유지한다 / keys inside the window are kept")
        // req: FR-LOGIN-025
        void keepsKeysInsideTheWindow() {
            // 너무 이르게 정리하면 한도가 초기화되어 통제가 사라진다.
            // Evicting too early would reset the allowance and remove the control.
            MutableTestClock clock = new MutableTestClock(T0);
            RateLimiter limiter = new RateLimiter(clock, 1, 20);
            limiter.checkAndRecord("user@example.com", IP);

            clock.advance(Duration.ofSeconds(30));
            assertThat(limiter.evictExpired()).isZero();
            assertThatThrownBy(() -> limiter.checkAndRecord("user@example.com", IP))
                    .as("the allowance must survive an eviction pass inside the window")
                    .isInstanceOf(AuthenticationException.class);
        }
    }
}
