package com.webcash.iris.auth.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 시각 공급자 설정. / Clock configuration.
 *
 * <p>{@link Clock} 을 빈으로 노출하여 휴면·비밀번호 주기 같은 날짜 판정을 테스트에서
 * 고정할 수 있게 한다. {@code LocalDate.now()} 를 직접 호출하면 90일 경계 같은 조건은
 * 사실상 테스트할 수 없다.</p>
 * <p>Exposing {@link Clock} as a bean lets date-based decisions — dormancy, password
 * age — be pinned in tests. Calling {@code LocalDate.now()} directly would make
 * boundary conditions such as the 90-day threshold effectively untestable.</p>
 *
 * // req: FR-LOGIN-012, FR-LOGIN-014
 */
@Configuration
public class ClockConfig {

    /**
     * 시스템 UTC 시각을 제공한다. / Provides the system UTC clock.
     *
     * <p>서버 로컬 시간대가 아니라 UTC 를 사용한다. 감사 기록과 세션 시각이 인스턴스
     * 시간대에 따라 달라지면 다중 인스턴스 환경에서 사건 순서를 재구성할 수 없다.</p>
     * <p>UTC rather than the server's local zone: if audit and session timestamps
     * varied by instance timezone, the order of events could not be reconstructed
     * across a multi-instance deployment.</p>
     *
     * @return UTC 시각 공급자 / the UTC clock
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
