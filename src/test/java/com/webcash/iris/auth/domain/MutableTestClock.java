package com.webcash.iris.auth.domain;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * 전진 가능한 시험용 시계. / A test clock that can be advanced.
 *
 * <p>{@link Clock#fixed} 는 불변이라 만료·슬라이딩 윈도우를 시험할 수 없고,
 * {@code Thread.sleep} 은 시험을 느리게 하고 CI 부하에 따라 흔들리게 만든다.
 * 시간 의존 로직은 시각을 <b>주입</b>받아야 결정적으로 검증할 수 있다.</p>
 * <p>{@code Clock.fixed} is immutable, so expiry and sliding windows cannot be exercised with it,
 * and {@code Thread.sleep} makes tests slow and flaky under CI load. Time-dependent logic is only
 * deterministically testable when the clock is injected.</p>
 *
 * <p>{@link RateLimiter}·{@link OtpReplayGuard} 가 공유한다.</p>
 *
 * // req: FR-LOGIN-025, TM-L004
 */
final class MutableTestClock extends Clock {

    private Instant now;

    MutableTestClock(Instant start) {
        this.now = start;
    }

    /**
     * 시각을 전진시킨다. / Advances the clock.
     *
     * @param amount 전진량 / how far to advance
     */
    void advance(Duration amount) {
        this.now = this.now.plus(amount);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
