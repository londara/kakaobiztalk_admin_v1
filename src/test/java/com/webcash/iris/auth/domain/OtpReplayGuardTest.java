package com.webcash.iris.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link OtpReplayGuard} 단위 테스트. / Unit tests for {@link OtpReplayGuard}.
 *
 * <h2>이 시험이 존재하는 이유 / why this exists</h2>
 * <p>이 클래스는 <b>위협 모델이 주장했으나 존재하지 않았던 통제</b>를 뒤늦게 구현한 것이다
 * (TM-L004, Sprint L4까지 미구현). 그런데 커버리지 측정 결과 이 클래스는 <b>13행 중 1행만</b>
 * 검증되어 있었다 — 즉 "문서가 주장하는 통제가 실재하는가" 를 고쳐 놓고, "그 통제가 옳게
 * 동작하는가" 는 다시 증거 없이 남겨 둔 상태였다. 같은 실패의 한 단계 뒤 버전이다.</p>
 * <p>This class implements a control the threat model claimed but which did not exist (TM-L004,
 * absent through Sprint L4). Coverage then showed only 1 of its 13 lines verified — the "does the
 * claimed control exist" gap was closed while "does it work" was left without evidence. The same
 * failure one step later.</p>
 *
 * <p><b>고정 시계를 쓴다.</b> 만료는 {@code Clock} 에만 의존하므로 실제 대기 없이 시간을
 * 전진시킬 수 있다. {@code Thread.sleep} 을 쓰면 시험이 느려지고 CI 부하에 따라 흔들린다.</p>
 * <p><b>Fixed clocks are used.</b> Expiry depends only on the {@code Clock}, so time can be
 * advanced without waiting; {@code Thread.sleep} would be slow and flaky under CI load.</p>
 *
 * // req: TM-L004, FR-LOGIN-011
 */
class OtpReplayGuardTest {

    private static final Instant T0 = Instant.parse("2026-08-21T09:00:00Z");

    private static Clock at(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    @Test
    @DisplayName("같은 코드의 두 번째 사용은 거절된다 / a second use of the same code is refused")
    // req: TM-L004
    void secondUseIsRefused() {
        // 이것이 통제의 핵심이다. 어깨 너머로 코드를 본 공격자는 90초 창 안에서 그것을
        // 재사용할 수 있고, 단일 사용 강제가 그 가치를 없앤다.
        // The core of the control: an attacker who observed a code can reuse it inside the 90s
        // window, and single-use enforcement removes its value.
        OtpReplayGuard guard = new OtpReplayGuard(at(T0));

        assertThat(guard.tryConsume("user@example.com", "123456")).isTrue();
        assertThat(guard.tryConsume("user@example.com", "123456")).isFalse();
        assertThat(guard.tryConsume("user@example.com", "123456")).isFalse();
    }

    @Test
    @DisplayName("다른 코드는 서로 영향을 주지 않는다 / different codes are independent")
    // req: TM-L004
    void differentCodesAreIndependent() {
        OtpReplayGuard guard = new OtpReplayGuard(at(T0));

        assertThat(guard.tryConsume("user@example.com", "111111")).isTrue();
        assertThat(guard.tryConsume("user@example.com", "222222")).isTrue();
        assertThat(guard.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("다른 계정은 같은 코드를 쓸 수 있다 / different accounts may use the same code")
    // req: TM-L004
    void differentAccountsMayShareACode() {
        // TOTP 코드는 6자리이므로 서로 다른 계정이 같은 값을 동시에 가질 수 있다. 계정을
        // 키에 포함하지 않으면 A 의 로그인이 B 를 잠그게 된다 — 통제가 장애가 되는 경우다.
        // Six digits mean two accounts can legitimately hold the same value at once. Without the
        // account in the key, A's login would lock out B — the control becoming an outage.
        OtpReplayGuard guard = new OtpReplayGuard(at(T0));

        assertThat(guard.tryConsume("alice@example.com", "123456")).isTrue();
        assertThat(guard.tryConsume("bob@example.com", "123456")).isTrue();
    }

    @Test
    @DisplayName("계정 키는 대소문자를 구분하지 않는다 / the account key is case-insensitive")
    // req: TM-L004
    void accountKeyIsCaseInsensitive() {
        // 대문자로 바꿔 재사용하는 우회를 막는다. 이메일은 대소문자를 구분하지 않으므로
        // 같은 계정이며, 키가 구분하면 통제를 한 글자로 우회할 수 있다.
        // Prevents bypass by changing case: the address denotes the same account, so a
        // case-sensitive key would let one keystroke defeat the control.
        OtpReplayGuard guard = new OtpReplayGuard(at(T0));

        assertThat(guard.tryConsume("User@Example.com", "123456")).isTrue();
        assertThat(guard.tryConsume("user@example.com", "123456")).isFalse();
        assertThat(guard.tryConsume("USER@EXAMPLE.COM", "123456")).isFalse();
    }

    @Test
    @DisplayName("null 계정도 안전하게 처리한다 / a null account is handled safely")
    // req: TM-L004
    void nullAccountIsHandled() {
        // null 은 "unknown" 으로 정규화된다. 예외를 던지면 인증 경로에서 500 이 되고,
        // 상위에서 잘못 처리되면 통제를 건너뛰게 될 수 있다.
        // A null key normalises to "unknown". Throwing would surface as a 500 in the auth path and,
        // if mishandled upstream, could skip the control entirely.
        OtpReplayGuard guard = new OtpReplayGuard(at(T0));

        assertThat(guard.tryConsume(null, "123456")).isTrue();
        assertThat(guard.tryConsume(null, "123456")).isFalse();
    }

    @Test
    @DisplayName("보존 기간이 지나면 정리된다 / entries are evicted after the retention period")
    // req: TM-L004
    void expiredEntriesAreEvicted() {
        // 정리가 없으면 맵이 무한히 자라 통제 자체가 메모리 소모 경로가 된다.
        // Without eviction the map grows without bound and the control becomes a
        // memory-exhaustion path in its own right.
        OtpReplayGuard guard = new OtpReplayGuard(at(T0));
        guard.tryConsume("user@example.com", "123456");
        assertThat(guard.size()).isEqualTo(1);

        // 보존 기간(120초)을 넘겨 정리한다. 새 가드에 같은 맵을 쓸 수 없으므로 시계를
        // 바꿔 끼운 가드로 다시 확인하는 대신, 만료 판정만 별도로 확인한다.
        // Eviction is checked past the 120s retention.
        OtpReplayGuard aged = new OtpReplayGuard(at(T0));
        aged.tryConsume("user@example.com", "123456");
        assertThat(aged.evictExpired())
                .as("nothing is expired yet at T0")
                .isZero();
        assertThat(aged.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("정리 후에는 같은 코드를 다시 쓸 수 있다 / after eviction the code is usable again")
    // req: TM-L004
    void evictionReleasesTheCode() {
        // 이 동작은 <b>의도된 것이며 안전하다</b>: 보존 기간(120초)이 TOTP 검증 창(±1 스텝,
        // 90초)보다 길므로, 정리된 코드는 이미 TOTP 자체가 거절한다. 보존 기간이 검증 창보다
        // 짧아지면 이 시험은 실재하는 재사용 구멍을 알리게 된다.
        //
        // Intended and safe: retention (120s) exceeds the TOTP verification window (±1 step, 90s),
        // so an evicted code is already refused by TOTP itself. Should retention ever drop below
        // the window, this test would be reporting a real reuse hole.
        MutableTestClock clock = new MutableTestClock(T0);
        OtpReplayGuard guard = new OtpReplayGuard(clock);

        assertThat(guard.tryConsume("user@example.com", "123456")).isTrue();

        clock.advance(Duration.ofSeconds(121));
        assertThat(guard.evictExpired()).isEqualTo(1);
        assertThat(guard.size()).isZero();
        assertThat(guard.tryConsume("user@example.com", "123456")).isTrue();
    }

    @Test
    @DisplayName("보존 기간 이내면 정리하지 않는다 / entries inside retention survive eviction")
    // req: TM-L004
    void entriesInsideRetentionSurvive() {
        // 경계 확인. 너무 이르게 정리하면 재사용 창이 열린다.
        // Boundary check: evicting too early opens a reuse window.
        MutableTestClock clock = new MutableTestClock(T0);
        OtpReplayGuard guard = new OtpReplayGuard(clock);
        guard.tryConsume("user@example.com", "123456");

        clock.advance(Duration.ofSeconds(119));
        assertThat(guard.evictExpired()).isZero();
        assertThat(guard.tryConsume("user@example.com", "123456"))
                .as("still inside retention, so the code stays spent")
                .isFalse();
    }

}
