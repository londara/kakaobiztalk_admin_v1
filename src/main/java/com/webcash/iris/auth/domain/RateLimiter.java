package com.webcash.iris.auth.domain;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 인증 요청 속도 제한. / Rate limiting for authentication requests.
 *
 * <p><b>계정 잠금과는 다른 통제다.</b> 잠금(FR-LOGIN-003/010)은 <b>한 계정</b>에 대한
 * 반복 시도를 막고, 속도 제한은 <b>출처</b> 단위로 넓은 범위의 시도를 막는다. 잠금만
 * 있으면 공격자가 계정 1,000개에 각 4회씩 시도하여 어떤 잠금도 유발하지 않고
 * credential stuffing 을 수행할 수 있다.</p>
 * <p><b>A different control from account lockout.</b> Lockout stops repeated attempts
 * against <b>one account</b>; rate limiting stops broad attempts from <b>one source</b>.
 * With lockout alone, an attacker can try four passwords against a thousand accounts and
 * trigger no lockout at all.</p>
 *
 * <h2>해싱 앞단에 위치해야 하는 이유 / Why this must precede hashing</h2>
 * <p>Argon2id 는 의도적으로 비싸다(FR-LOGIN-005). 미인증 엔드포인트에서 요청마다 해싱을
 * 수행하면 그 비용이 그대로 CPU 소모 벡터가 된다(RISK-L07, TM-L016). 따라서 속도 제한은
 * 자격증명 검증 <b>이전에</b> 판정되어야 하며, 순서가 뒤바뀌면 통제가 무의미하다.</p>
 * <p>Argon2id is deliberately expensive. Performing it per request on an unauthenticated
 * endpoint turns that cost into a CPU-exhaustion vector (RISK-L07, TM-L016), so the limit
 * must be evaluated <b>before</b> credential verification. Reversed, the control is
 * pointless.</p>
 *
 * <p><b>인메모리 구현의 한계:</b> 인스턴스별로 독립 카운트를 유지하므로, N개 인스턴스
 * 환경에서 실효 한도는 N배가 된다. 다중 인스턴스 배포 시에는 공유 저장소 또는 프록시
 * 계층의 제한으로 대체해야 한다 — 이 클래스는 그 전제 위에 있다.</p>
 * <p><b>Limitation of the in-memory implementation:</b> counts are per instance, so with N
 * instances the effective limit is N times higher. A multi-instance deployment needs a
 * shared store or a proxy-tier limit instead; this class is written on that understanding.</p>
 *
 * // req: FR-LOGIN-025, RISK-L07, TM-L001, TM-L016
 */
@Component
public class RateLimiter {

    private final Clock clock;
    private final int perAccountPerMinute;
    private final int perSourcePerMinute;
    private final Duration window = Duration.ofMinutes(1);

    private final Map<String, Deque<Instant>> accountHits = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> sourceHits = new ConcurrentHashMap<>();

    /**
     * 한도를 주입받아 생성한다. / Creates the limiter with configured limits.
     *
     * @param clock               시각 공급자 / the clock
     * @param perAccountPerMinute 계정당 분간 허용 횟수 / attempts allowed per account per minute
     * @param perSourcePerMinute  출처당 분간 허용 횟수 / attempts allowed per source per minute
     */
    public RateLimiter(Clock clock,
                       @Value("${iris.auth.rate-limit.per-account-per-minute:5}") int perAccountPerMinute,
                       @Value("${iris.auth.rate-limit.per-source-per-minute:20}") int perSourcePerMinute) {
        this.clock = clock;
        this.perAccountPerMinute = perAccountPerMinute;
        this.perSourcePerMinute = perSourcePerMinute;
    }

    /**
     * 요청을 기록하고 한도 초과 시 예외를 던진다.
     * Records the attempt and raises when either limit is exceeded.
     *
     * <p>계정 한도와 출처 한도를 <b>모두</b> 검사한다. 계정 식별자는 소문자로 정규화하여
     * 대소문자를 바꿔 한도를 우회하는 것을 막는다.</p>
     * <p>Both limits are checked. The account key is lower-cased so that varying the case
     * of an email cannot multiply the allowance.</p>
     *
     * @param accountKey 계정 식별자 (이메일) / the account identifier
     * @param sourceIp   신뢰 가능한 출처 IP / the trusted source address
     * @throws AuthenticationException 한도 초과 시 / when a limit is exceeded
     */
    // req: FR-LOGIN-025
    public void checkAndRecord(String accountKey, String sourceIp) {
        Instant now = Instant.now(clock);
        boolean accountOk = record(accountHits, normalise(accountKey), now, perAccountPerMinute);
        boolean sourceOk = record(sourceHits, normalise(sourceIp), now, perSourcePerMinute);

        if (!accountOk || !sourceOk) {
            throw new AuthenticationException(AuthFailureReason.RATE_LIMITED);
        }
    }

    /**
     * 만료된 항목을 정리한다. / Evicts expired entries.
     *
     * <p>주기적으로 호출되지 않으면 맵이 무한히 커진다 — 속도 제한 자체가 메모리
     * 소모 경로가 되는 것을 막기 위한 것이다.</p>
     * <p>Without periodic invocation the maps grow without bound, which would turn the
     * rate limiter itself into a memory-exhaustion path.</p>
     *
     * @return 제거된 키 수 / the number of keys removed
     */
    // req: FR-LOGIN-025
    public int evictExpired() {
        Instant cutoff = Instant.now(clock).minus(window);
        return evict(accountHits, cutoff) + evict(sourceHits, cutoff);
    }

    private boolean record(Map<String, Deque<Instant>> hits, String key, Instant now, int limit) {
        Deque<Instant> timestamps = hits.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        Instant cutoff = now.minus(window);

        // 창을 벗어난 항목 제거 — 슬라이딩 윈도우.
        // Drop entries outside the window: a sliding window, not a fixed bucket. A fixed
        // bucket allows a double burst across the boundary.
        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
            timestamps.pollFirst();
        }

        timestamps.addLast(now);
        return timestamps.size() <= limit;
    }

    private int evict(Map<String, Deque<Instant>> hits, Instant cutoff) {
        int removed = 0;
        for (var entry : hits.entrySet()) {
            Deque<Instant> timestamps = entry.getValue();
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
                timestamps.pollFirst();
            }
            if (timestamps.isEmpty()) {
                hits.remove(entry.getKey());
                removed++;
            }
        }
        return removed;
    }

    private String normalise(String key) {
        return key == null ? "unknown" : key.toLowerCase();
    }
}
