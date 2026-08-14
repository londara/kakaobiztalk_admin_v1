package com.webcash.iris.auth.domain;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * OTP 코드 단일 사용 강제. / Enforces single use of an OTP code.
 *
 * <p><b>위협 모델 불일치 해소.</b> {@code threat-model-LOGIN.md} TM-L004 는 완화책으로
 * "단일 사용 강제(single-use enforcement per (account, step))"를 기재했으나 Sprint L4
 * 시점까지 <b>구현되지 않은 상태</b>였다. 문서가 존재하지 않는 통제를 주장하는 것은
 * 레거시의 실패 유형(L3·L5·L6 — 주석 처리된 통제)과 동일하므로, 기재를 지우는 대신
 * 구현한다.</p>
 * <p><b>Closes a threat-model inconsistency.</b> TM-L004 listed single-use enforcement as
 * a mitigation, but through Sprint L4 it did not exist. A document asserting an absent
 * control is the legacy's own failure pattern (L3, L5, L6 — controls commented out), so
 * the answer is to implement it rather than delete the claim.</p>
 *
 * <p><b>왜 필요한가:</b> TOTP 코드는 시간 창(±1 스텝, 최대 90초) 안에서 유효하다. 어깨
 * 너머로 코드를 관찰하거나 네트워크에서 획득한 공격자는 그 창 안에서 같은 코드를 다시
 * 쓸 수 있다. 단일 사용을 강제하면 관찰된 코드의 가치가 사라진다.</p>
 * <p><b>Why it matters:</b> a TOTP code is valid across its window (±1 step, up to 90
 * seconds). An attacker who observes a code — over someone's shoulder or on the wire —
 * can reuse it inside that window. Single-use enforcement removes the value of an
 * observed code.</p>
 *
 * <p><b>인메모리 한계:</b> {@link RateLimiter} 와 동일하게 인스턴스별 상태다. 다중
 * 인스턴스에서는 인스턴스 A 에서 쓴 코드를 인스턴스 B 에서 재사용할 수 있으므로, 공유
 * 저장소로 대체해야 완전해진다. 이 한계는 위협 모델에 잔여 위험으로 남는다.</p>
 * <p><b>In-memory limitation:</b> as with {@link RateLimiter}, state is per instance, so a
 * code spent on instance A could be reused on instance B. A shared store is required for
 * completeness; until then this remains a residual risk in the threat model.</p>
 *
 * // req: TM-L004, FR-LOGIN-011
 */
@Component
public class OtpReplayGuard {

    /**
     * 기억 기간. 검증 창(±1 스텝 = 90초)보다 넉넉하게 잡는다.
     * Retention period, set comfortably above the verification window of ±1 step (90s).
     */
    private static final Duration RETENTION = Duration.ofSeconds(120);

    private final Clock clock;
    private final Map<String, Instant> spent = new ConcurrentHashMap<>();

    /**
     * 가드를 생성한다. / Creates the guard.
     *
     * @param clock 시각 공급자 / the clock
     */
    public OtpReplayGuard(Clock clock) {
        this.clock = clock;
    }

    /**
     * 코드를 소비 처리한다. 이미 사용된 코드면 {@code false} 를 반환한다.
     * Marks a code as spent, returning {@code false} when it was already used.
     *
     * <p>{@code putIfAbsent} 로 원자적으로 판정한다. 검사 후 등록하는 방식이면 동시
     * 요청 두 건이 모두 통과할 수 있고, 그것이 바로 막으려는 재사용이다.</p>
     * <p>Decided atomically with {@code putIfAbsent}. A check-then-put would let two
     * concurrent requests both pass — which is exactly the reuse being prevented.</p>
     *
     * @param accountKey 계정 식별자 / the account identifier
     * @param code       OTP 코드 / the OTP code
     * @return 최초 사용이면 true / true when this is the first use
     */
    // req: TM-L004
    public boolean tryConsume(String accountKey, String code) {
        String key = normalise(accountKey) + ":" + code;
        Instant previous = spent.putIfAbsent(key, Instant.now(clock));
        return previous == null;
    }

    /**
     * 만료된 항목을 정리한다. / Evicts expired entries.
     *
     * @return 제거된 항목 수 / the number of entries removed
     */
    // req: TM-L004
    public int evictExpired() {
        Instant cutoff = Instant.now(clock).minus(RETENTION);
        int before = spent.size();
        spent.entrySet().removeIf(e -> e.getValue().isBefore(cutoff));
        return before - spent.size();
    }

    /**
     * 현재 기억 중인 항목 수. 운영 모니터링과 테스트용.
     * The number of entries currently retained, for monitoring and tests.
     *
     * @return 항목 수 / the entry count
     */
    public int size() {
        return spent.size();
    }

    private String normalise(String key) {
        return key == null ? "unknown" : key.toLowerCase();
    }
}
