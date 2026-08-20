package com.webcash.iris.biztalk.alimtalk.config;

import com.webcash.iris.biztalk.alimtalk.domain.OutboxDispatcher;
import com.webcash.iris.biztalk.alimtalk.domain.OutboxStatus;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 디스패처를 주기적으로 돌린다. / Runs the dispatcher periodically.
 *
 * <p>{@code @ConditionalOnProperty} 로 게이트한 이유는 {@link AlimTalkDispatchConfig} 와 같다:
 * 이 빈이 등록되는 순간 애플리케이션은 기동만으로 실제 발송을 시작한다. 기본값은 꺼짐이다.</p>
 * <p>Gated for the same reason as {@link AlimTalkDispatchConfig}: registering this bean makes the
 * application start sending merely by starting. The default is off.</p>
 *
 * <h2>{@code fixedDelay} 를 쓰는 이유 / why fixedDelay</h2>
 * <p>{@code fixedRate} 는 <b>이전 실행이 끝나기를 기다리지 않는다</b>. 벤더가 느려 한 주기가
 * 60초 걸리면 주기가 겹치고, 겹친 두 실행이 같은 행을 잡으려 경쟁한다.
 * {@code SKIP LOCKED} 가 대부분을 막지만, 겹침을 애초에 만들지 않는 편이 낫다 —
 * {@code fixedDelay} 는 이전 실행이 <b>끝난 뒤</b>부터 센다.</p>
 * <p>{@code fixedRate} does not wait for the previous run: if the vendor is slow and a cycle takes 60
 * seconds, cycles overlap and the two compete for the same rows. {@code SKIP LOCKED} handles most of
 * that, but not creating the overlap is better — {@code fixedDelay} counts from when the previous run
 * <b>finished</b>.</p>
 *
 * // source: com.webcash.iris.auth.config.MaintenanceScheduler — the established pattern here
 * // req: FR-ATS-004, FR-ATS-005, ADR-ATK-023
 */
@Component
@ConditionalOnProperty(name = "iris.alimtalk.dispatch.enabled", havingValue = "true")
public class OutboxDispatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatchScheduler.class);

    private final OutboxDispatcher dispatcher;

    /**
     * 스케줄러를 만든다. / Creates the scheduler.
     *
     * @param dispatcher 디스패처 / the dispatcher
     *
     * // req: FR-ATS-004
     */
    public OutboxDispatchScheduler(OutboxDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    /**
     * 한 주기를 돈다. / Runs one cycle.
     *
     * <p>예외를 밖으로 내보내지 않는다. {@code @Scheduled} 메서드가 예외를 던지면 스프링은 그
     * 작업을 <b>다시 스케줄하지 않는다</b> — 한 번의 실패가 발송을 영구히 멈춘다. 그것은 조용한
     * 미전달이며 이 슬라이스가 없애려는 결함이다.</p>
     * <p>Exceptions are not allowed to escape: when a {@code @Scheduled} method throws, Spring
     * <b>stops rescheduling</b> it, so one failure halts sending permanently — a silent non-delivery,
     * the defect this slice exists to remove.</p>
     */
    // req: FR-ATS-004, FR-ATS-005, NFR-OPS-A02
    @Scheduled(fixedDelayString = "${iris.alimtalk.dispatch.interval-ms:30000}")
    public void dispatch() {
        try {
            List<OutboxDispatcher.DispatchOutcome> outcomes = dispatcher.runOnce();
            if (outcomes.isEmpty()) {
                return;
            }

            long sent = outcomes.stream().filter(o -> o.status() == OutboxStatus.SENT).count();
            long failed = outcomes.stream().filter(o -> o.status() == OutboxStatus.FAILED).count();
            long unknown = outcomes.stream().filter(o -> o.status() == OutboxStatus.UNKNOWN).count();
            long dead = outcomes.stream().filter(o -> o.status() == OutboxStatus.DEAD).count();

            // 건수만 기록한다 — payload 에는 수신번호가 평문으로 있고, 레거시는 data_log=true 로
            // 그것을 매 발송마다 로그에 남겼다(D-A30).
            // Counts only: the payload holds recipients in clear, and the legacy wrote exactly that to
            // the log on every send via data_log=true (D-A30).
            log.info("AlimTalk dispatch cycle: {} handled (sent={}, failed={}, unknown={}, dead={})",
                    outcomes.size(), sent, failed, unknown, dead);

            if (unknown > 0) {
                // UNKNOWN 은 사람이 봐야 한다. 기본 정책이 재시도하지 않는 것이므로, 알리지
                // 않으면 그 행은 아무도 모르게 남는다.
                // UNKNOWN needs a human. Since the default policy is not to retry, staying quiet would
                // leave those rows unnoticed by anyone.
                log.warn("AlimTalk: {} row(s) have UNKNOWN delivery and are not retried by default"
                        + " (RISK-A07). An operator must decide.", unknown);
            }
            if (dead > 0) {
                log.error("AlimTalk: {} row(s) reached the retry ceiling and are DEAD.", dead);
            }
        } catch (RuntimeException e) {
            // 여기서 삼키지 않으면 스케줄이 멈춘다. 삼키되 <b>반드시</b> 기록한다.
            // Swallowing here is what keeps the schedule alive; it is swallowed but always recorded.
            log.error("AlimTalk dispatch cycle failed; the schedule continues. Cause: {}",
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
