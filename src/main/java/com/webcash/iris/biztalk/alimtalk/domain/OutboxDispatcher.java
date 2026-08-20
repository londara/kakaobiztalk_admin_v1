package com.webcash.iris.biztalk.alimtalk.domain;

import com.webcash.iris.biztalk.alimtalk.infra.db.OutboxMapper;
import com.webcash.iris.biztalk.alimtalk.infra.vendor.AlimTalkVendorClient;
import com.webcash.iris.biztalk.alimtalk.infra.vendor.VendorSendResult;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 아웃박스를 비운다. / Drains the outbox.
 *
 * <h2>한 행에 한 트랜잭션 / one transaction per row</h2>
 * <p>배치 하나를 한 트랜잭션으로 감싸면, 열 번째 행에서 실패했을 때 앞의 아홉 건은 <b>이미
 * 벤더에 도달했는데</b> 상태 기록만 롤백된다. 다음 주기에 그 아홉 건을 다시 보낸다. 트랜잭션이
 * 롤백할 수 있는 것은 우리 데이터베이스뿐이고 벤더에 보낸 요청은 되돌릴 수 없다 — 그러므로
 * 트랜잭션 경계는 벤더 호출 <b>하나</b>를 넘어서면 안 된다.</p>
 * <p>Wrapping a whole batch in one transaction means that a failure on the tenth row rolls back the
 * status of the first nine, which have <b>already reached the vendor</b>, and the next cycle sends them
 * again. A transaction can roll back only our database, never a request already sent — so the
 * transaction boundary must not span more than <b>one</b> vendor call.</p>
 *
 * <h2>재시도 정책과 미확인 전제 / the retry policy and its unverified premise</h2>
 * <p>{@link OutboxStatus#FAILED} 는 전달되지 않았음이 확인된 상태이므로 재시도가 안전하다.
 * {@link OutboxStatus#UNKNOWN} 은 다르다 — 재시도가 안전한지가 <b>벤더의 멱등성</b>에 달려
 * 있고 그것은 확인되지 않았다(spike A1-03, RISK-A07). 그래서 기본값은 <b>재시도하지 않는다</b>.</p>
 * <p>{@link OutboxStatus#FAILED} is safe to retry because non-delivery is established.
 * {@link OutboxStatus#UNKNOWN} is not: whether a retry is safe depends on <b>vendor idempotency</b>,
 * which is unverified (spike A1-03, RISK-A07). The default is therefore <b>not to retry</b>.</p>
 *
 * <p>그 기본값의 대가를 분명히 적어 둔다: {@code UNKNOWN} 행은 사람이 볼 때까지 남는다. 자동
 * 재시도로 <b>중복 발송</b>을 만드는 것보다, 사람이 확인할 목록에 남기는 편이 금융 통지에서는
 * 낫다고 판단했다. 벤더 멱등성이 확인되면 설정 한 줄로 바뀐다.</p>
 * <p>The cost of that default, stated plainly: {@code UNKNOWN} rows wait for a human. For a financial
 * notification, leaving them on a list someone must look at was judged better than automatically
 * producing a <b>duplicate send</b>. Once idempotency is confirmed this is one setting.</p>
 *
 * // source: biztalk_admin_50_s001_act.jsp:118-137 — insert then send, nothing spanning them
 * // req: FR-ATS-004, FR-ATS-005, ADR-ATK-023, ADR-ATK-025, RISK-A07
 */
public class OutboxDispatcher {

    /**
     * 클레임 만료의 최소값. / The minimum claim expiry.
     *
     * <p>벤더 read 타임아웃이 60초이므로(레거시 채널 설정 실측) 클레임은 그보다 길어야 한다.
     * 짧으면 아직 응답을 기다리는 행을 다른 인스턴스가 다시 집어 <b>중복 발송</b>한다. 설계
     * 문서에 이 제약이 없었다 — ANALYSIS-A2-05-vendor-transport.md §2③ 에서 도출했다.</p>
     * <p>The vendor read timeout is 60 seconds, so a claim must outlast it; shorter, and another
     * instance re-claims a row whose response is still outstanding and <b>sends it twice</b>. The
     * design did not state this constraint.</p>
     *
     * // req: FR-ATS-005, RISK-A07
     */
    public static final Duration MINIMUM_CLAIM = Duration.ofSeconds(90);

    private final OutboxMapper outbox;
    private final AlimTalkVendorClient vendor;
    private final Clock clock;
    private final int batchSize;
    private final int maxAttempts;
    private final boolean retryUnknown;
    private final Duration claimFor;

    /**
     * 디스패처를 만든다. / Creates the dispatcher.
     *
     * @param outbox       아웃박스 매퍼 / the outbox mapper
     * @param vendor       벤더 클라이언트 / the vendor client
     * @param clock        시계 / the clock
     * @param batchSize    한 주기에 처리할 최대 행 수 / rows per cycle
     * @param maxAttempts  이 횟수에 도달하면 {@link OutboxStatus#DEAD} / attempts before DEAD
     * @param retryUnknown {@link OutboxStatus#UNKNOWN} 을 재시도할지 — 벤더 멱등성 확인 후에만 참
     *                     / whether to retry UNKNOWN; true only once idempotency is confirmed
     * @param claimFor     클레임 유지 시간 / how long a claim is held
     * @throws IllegalArgumentException 클레임이 {@link #MINIMUM_CLAIM} 보다 짧으면
     *                                 / when the claim is shorter than {@link #MINIMUM_CLAIM}
     *
     * // req: FR-ATS-005
     */
    public OutboxDispatcher(
            OutboxMapper outbox,
            AlimTalkVendorClient vendor,
            Clock clock,
            int batchSize,
            int maxAttempts,
            boolean retryUnknown,
            Duration claimFor) {
        if (claimFor.compareTo(MINIMUM_CLAIM) < 0) {
            // 설정 실수를 조용히 받아들이지 않는다. 짧은 클레임은 눈에 보이는 오류를 내지 않고
            // 중복 발송만 만든다 — 그것은 발견하기 어렵고 되돌릴 수 없다.
            // A configuration mistake is not accepted quietly: a short claim produces no visible
            // error, only duplicate sends, which are hard to notice and impossible to undo.
            throw new IllegalArgumentException(
                    "claimFor must be at least " + MINIMUM_CLAIM.toSeconds()
                            + "s because the vendor read timeout is 60s; a shorter claim re-sends rows"
                            + " whose response is still outstanding. Given: " + claimFor.toSeconds() + "s");
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be positive, was " + batchSize);
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive, was " + maxAttempts);
        }
        this.outbox = outbox;
        this.vendor = vendor;
        this.clock = clock;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.retryUnknown = retryUnknown;
        this.claimFor = claimFor;
    }

    /**
     * 한 주기를 돈다. / Runs one cycle.
     *
     * @return 이 주기에 처리한 행의 결과 / the outcome of each row handled
     *
     * // req: FR-ATS-004, FR-ATS-005
     */
    public List<DispatchOutcome> runOnce() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<String> statuses = new ArrayList<>();
        statuses.add(OutboxStatus.PENDING.name());
        statuses.add(OutboxStatus.FAILED.name());
        if (retryUnknown) {
            statuses.add(OutboxStatus.UNKNOWN.name());
        }

        List<OutboxEntry> claimed = outbox.claim(statuses, now, batchSize);
        List<DispatchOutcome> outcomes = new ArrayList<>(claimed.size());
        for (OutboxEntry entry : claimed) {
            outcomes.add(dispatch(entry, now));
        }
        return outcomes;
    }

    /**
     * 한 행을 보낸다. / Sends one row.
     *
     * @param entry 대상 행 / the row
     * @param now   현재 시각 / the current time
     * @return 결과 / the outcome
     *
     * // req: FR-ATS-004, FR-ATS-005
     */
    private DispatchOutcome dispatch(OutboxEntry entry, LocalDateTime now) {
        // 벤더 호출 <b>전에</b> 클레임을 세운다. 호출 뒤에 세우면 그 사이에 죽었을 때 다른
        // 인스턴스가 같은 행을 즉시 집는다 — 요청은 이미 나갔는데.
        // The claim is set BEFORE the call: setting it after would let another instance take the row
        // immediately if we die in between, though the request has already gone out.
        outbox.markClaimed(entry.outboxId(), now.plus(claimFor));

        VendorSendResult result = vendor.send(entry.isCd(), entry.tranId(), entry.payload());
        OutboxStatus next = result.status();

        // 상한 판정은 이번 시도를 포함해서 센다 — markClaimed 가 ATTEMPTS 를 이미 올렸으므로
        // entry.attempts() 는 이 시도 <b>전</b>의 값이다.
        // The ceiling counts this attempt: markClaimed already incremented ATTEMPTS, so
        // entry.attempts() is the value from BEFORE this attempt.
        int attemptsNow = entry.attempts() + 1;
        if (next == OutboxStatus.FAILED && attemptsNow >= maxAttempts) {
            next = OutboxStatus.DEAD;
        }

        outbox.recordOutcome(entry.outboxId(), next.name(), truncate(result.detail()));
        return new DispatchOutcome(entry.outboxId(), entry.isCd(), next, result.detail(), attemptsNow);
    }

    /**
     * 오류 요약을 컬럼 길이에 맞춘다. / Fits an error summary to the column width.
     *
     * <p>{@code LAST_ERROR} 는 1000자다. 잘라내지 않으면 삽입이 실패하고, 그 실패는 <b>결과
     * 기록 자체를 잃게</b> 만든다 — 보냈는지 아닌지 모르는 행이 남는다. 긴 오류 메시지 때문에
     * 발송 상태를 잃는 것은 명백히 잘못된 교환이다.</p>
     * <p>{@code LAST_ERROR} is 1000 characters. Without truncation the update fails, and that failure
     * <b>loses the outcome record itself</b>, leaving a row whose fate is unknown. Losing send state to
     * a long error message is plainly the wrong trade.</p>
     *
     * @param detail 원래 요약 / the raw summary
     * @return 잘린 요약 / the fitted summary
     *
     * // req: FR-ATS-005
     */
    private static String truncate(String detail) {
        if (detail == null) {
            return null;
        }
        return detail.length() <= 1000 ? detail : detail.substring(0, 997) + "...";
    }

    /**
     * 한 행의 처리 결과. / The result of handling one row.
     *
     * @param outboxId 행 식별자 / the row id
     * @param isCd     이용기관코드 / institution code
     * @param status   기록된 상태 / the recorded status
     * @param detail   요약 / the summary
     * @param attempts 이 시도까지의 횟수 / attempts including this one
     *
     * // req: FR-ATS-005, NFR-OPS-A02
     */
    public record DispatchOutcome(
            long outboxId, String isCd, OutboxStatus status, String detail, int attempts) {
    }
}
