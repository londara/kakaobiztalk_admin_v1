package com.webcash.iris.auth.config;

import com.webcash.iris.auth.domain.OtpReplayGuard;
import com.webcash.iris.auth.domain.RateLimiter;
import com.webcash.iris.auth.infra.db.SessionMapper;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 주기적 정리 작업. / Periodic maintenance tasks.
 *
 * <p>세 개의 상태 저장 통제가 정리 없이는 무한히 커진다. 통제 자체가 자원 소모 경로가
 * 되는 것을 막는다.</p>
 * <p>Three stateful controls grow without bound unless swept, which would turn the
 * controls themselves into resource-exhaustion paths.</p>
 *
 * <table>
 *   <caption>정리 대상 / what is swept</caption>
 *   <tr><th>대상</th><th>이유</th></tr>
 *   <tr><td>{@link RateLimiter}</td><td>계정·IP 별 타임스탬프 큐 (FR-LOGIN-025)</td></tr>
 *   <tr><td>{@link OtpReplayGuard}</td><td>사용된 OTP 코드 기록 (TM-L004)</td></tr>
 *   <tr><td>세션 레지스트리</td><td>비정상 종료로 남은 고아 세션 (ADR-LOGIN-012 §4.3)</td></tr>
 * </table>
 *
 * <p><b>다중 인스턴스 주의:</b> 세션 정리는 <b>공유 DB</b>를 대상으로 하므로 모든
 * 인스턴스가 동일 작업을 중복 수행한다. 멱등한 DELETE 이므로 정확성 문제는 없으나,
 * 인스턴스 수가 늘면 불필요한 쿼리가 비례하여 증가한다. 리더 선출 또는 외부 스케줄러로
 * 옮기는 것이 정석이다 — 현 규모(&lt;10k msg/day)에서는 수용한다.</p>
 * <p><b>Multi-instance note:</b> session reaping targets the <b>shared</b> database, so
 * every instance performs the same work. The DELETE is idempotent so correctness holds,
 * but redundant queries scale with instance count. Leader election or an external
 * scheduler is the proper answer; at this volume it is accepted.</p>
 *
 * // req: FR-LOGIN-025, TM-L004, ADR-LOGIN-012
 */
@Component
@EnableScheduling
public class MaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceScheduler.class);

    private final RateLimiter rateLimiter;
    private final OtpReplayGuard replayGuard;
    private final SessionMapper sessions;
    private final Clock clock;
    private final Duration sessionMaxAge;

    /**
     * 스케줄러 생성. / Creates the scheduler.
     *
     * @param rateLimiter      속도 제한 / the rate limiter
     * @param replayGuard      OTP 재사용 방지 / the replay guard
     * @param sessions         세션 매퍼 / the session mapper
     * @param clock            시각 공급자 / the clock
     * @param sessionMaxAgeHours 세션 최대 보존 시간 / maximum session age in hours
     */
    public MaintenanceScheduler(RateLimiter rateLimiter,
                                OtpReplayGuard replayGuard,
                                SessionMapper sessions,
                                Clock clock,
                                @Value("${iris.auth.session-max-age-hours:12}") long sessionMaxAgeHours) {
        this.rateLimiter = rateLimiter;
        this.replayGuard = replayGuard;
        this.sessions = sessions;
        this.clock = clock;
        this.sessionMaxAge = Duration.ofHours(sessionMaxAgeHours);
    }

    /**
     * 인메모리 통제의 만료 항목을 정리한다. / Sweeps expired entries from the in-memory controls.
     */
    // req: FR-LOGIN-025, TM-L004
    @Scheduled(fixedDelayString = "${iris.auth.sweep-interval-ms:60000}")
    public void sweepInMemoryState() {
        int rateKeys = rateLimiter.evictExpired();
        int otpCodes = replayGuard.evictExpired();
        if (rateKeys > 0 || otpCodes > 0) {
            log.debug("Swept {} rate-limit keys and {} spent OTP codes", rateKeys, otpCodes);
        }
    }

    /**
     * 고아 세션을 정리한다. / Reaps orphaned sessions.
     *
     * <p>세션 만료(30분 비활성)는 컨테이너가 처리하지만, 인스턴스가 비정상 종료되면
     * 레지스트리 행이 남는다. 그 상태에서는 해당 계정의 다음 로그인이 "기존 세션 종료"로
     * 처리되어 사용자에게 불필요한 신호를 준다.</p>
     * <p>Container sessions expire on inactivity, but a row survives if an instance dies
     * uncleanly. The account's next login is then reported as having displaced a session,
     * giving the user a misleading signal.</p>
     */
    // req: ADR-LOGIN-012 §4.3, FR-LOGIN-016
    @Scheduled(fixedDelayString = "${iris.auth.session-reap-interval-ms:900000}")
    public void reapStaleSessions() {
        long cutoff = clock.instant().minus(sessionMaxAge).getEpochSecond();
        int removed = sessions.deleteStale(cutoff);
        if (removed > 0) {
            log.info("Reaped {} stale session record(s)", removed);
        }
    }
}
