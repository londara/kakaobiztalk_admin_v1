package com.webcash.iris.biztalk.alimtalk.domain;

/**
 * 아웃박스 행의 상태. / The state of an outbox row.
 *
 * <h2>{@link #UNKNOWN} 이 있는 이유 / why {@link #UNKNOWN} exists</h2>
 * <p>벤더 호출의 read 타임아웃은 60초다(레거시 채널 설정 실측). 그 시간이 지나 응답이 없을 때
 * 우리가 아는 것은 "응답을 받지 못했다" 이고, 모르는 것은 "메시지가 전달되었는가" 다. 그 둘은
 * 다르다.</p>
 * <p>The vendor read timeout is 60 seconds (measured from the legacy channel configuration). When it
 * elapses, what we know is that no response arrived; what we do not know is whether the message was
 * delivered. Those are different things.</p>
 *
 * <p>모르는 것을 {@link #FAILED} 로 적으면 재시도가 <b>중복 발송</b>이 된다. {@link #SENT} 로
 * 적으면 <b>조용한 미전달</b>이 된다. 둘 다 틀렸으므로 세 번째 상태가 필요하다 — 레거시에는
 * 이 구분이 없었고, 그래서 부분 실패를 전체 실패로 보고했다.</p>
 * <p>Recording the unknown as {@link #FAILED} makes a retry a <b>duplicate send</b>; recording it as
 * {@link #SENT} makes it a <b>silent non-delivery</b>. Both are wrong, so a third state is required.
 * The legacy had no such distinction, which is why it reported partial failure as total failure.</p>
 *
 * <p>{@link #UNKNOWN} 의 재시도 여부는 <b>벤더의 멱등성에 달려 있다</b>. 벤더가
 * {@code (is_cd, tran_id)} 로 중복을 거른다면 재시도가 안전하고, 그렇지 않다면 재시도가 곧
 * 중복이다. 그 전제는 아직 확인되지 않았다(spike A1-03, RISK-A07) — 그래서 이 상태의 처리
 * 정책은 설정으로 두고 기본값을 <b>보수적으로</b> 잡는다.</p>
 * <p>Whether {@link #UNKNOWN} may be retried depends on <b>vendor idempotency</b>: safe if the vendor
 * de-duplicates on {@code (is_cd, tran_id)}, a duplicate if not. That premise is unverified (spike
 * A1-03, RISK-A07), so the policy is configurable and its default is conservative.</p>
 *
 * // source: biztalk_admin_50_s001_act.jsp:118-137 — insert then send, no spanning transaction
 * // source: jex.iris_admin.xml:135-137 — connectTimeout / readTimeout / waitTimeout 60000
 * // req: FR-ATS-001, FR-ATS-005, ADR-ATK-023, RISK-A07
 */
public enum OutboxStatus {

    /**
     * 접수되었고 아직 보내지 않았다. / Accepted, not yet sent.
     *
     * // req: FR-ATS-001
     */
    PENDING,

    /**
     * 벤더가 접수를 확인했다. / The vendor acknowledged acceptance.
     *
     * <p><b>전달되었다는 뜻이 아니다.</b> 전달 상태는 게이트웨이가 {@code KKO_MSG_LOG} 에
     * 기록하며, 이 표는 접수까지만 말한다. 화면이 이 둘을 섞어 표시하면 운영자는 전달되지 않은
     * 메시지를 전달된 것으로 읽는다 — NFR-OPS-A02 가 그것을 금지한다.</p>
     * <p><b>Not the same as delivered.</b> Delivery is recorded by the gateway in
     * {@code KKO_MSG_LOG}; this table speaks only of acceptance. Conflating them on screen would show
     * an undelivered message as delivered, which NFR-OPS-A02 forbids.</p>
     *
     * // req: FR-ATS-005, NFR-OPS-A02
     */
    SENT,

    /**
     * 전달되지 <b>않았음이 확인된</b> 실패. 재시도해도 안전하다.
     * A failure where non-delivery is <b>established</b>; safe to retry.
     *
     * <p>연결 거부, DNS 실패, 4xx·5xx 응답 등 — 요청이 처리되지 않았음을 응답 자체가 말해 주는
     * 경우다. 재시도가 중복을 만들지 않는다.</p>
     * <p>Connection refused, DNS failure, a 4xx or 5xx response — cases where the response itself
     * establishes that the request was not processed, so a retry cannot duplicate.</p>
     *
     * // req: FR-ATS-005, ADR-ATK-025
     */
    FAILED,

    /**
     * 전달 여부를 알 수 없다. / Delivery is unknown.
     *
     * <p>read 타임아웃이 대표적이다 — 요청은 나갔고 응답은 오지 않았다. 벤더가 받아서 처리했을
     * 수도, 받지 못했을 수도 있다. {@link #FAILED} 와 <b>반드시</b> 구분해야 한다.</p>
     * <p>Typically a read timeout: the request left and no response came. The vendor may or may not
     * have processed it, and this <b>must</b> be distinguished from {@link #FAILED}.</p>
     *
     * // req: FR-ATS-005, RISK-A07
     */
    UNKNOWN,

    /**
     * 재시도 상한에 도달했다. 더 보내지 않고 운영자를 기다린다.
     * The retry ceiling was reached; no further attempt is made and an operator must look.
     *
     * <p>무한 재시도를 하지 않는 이유: 실패가 영구적이면 무한 재시도는 벤더를 두드리는 것 말고
     * 하는 일이 없고, 그동안 진짜 문제는 아무에게도 보고되지 않는다.</p>
     * <p>Retrying forever would do nothing but hammer the vendor while the real problem goes
     * unreported.</p>
     *
     * // req: FR-ATS-005, NFR-OPS-A02
     */
    DEAD;

    /**
     * 디스패처가 다시 집어야 하는 상태인가. / Should the dispatcher pick this row up again?
     *
     * <p>{@link #UNKNOWN} 이 여기 포함되는지는 <b>정책</b>이며 이 메서드가 정하지 않는다 —
     * 벤더 멱등성이 확인되지 않았으므로(RISK-A07) 호출부가 설정을 보고 결정한다. 이 메서드는
     * 재시도가 <b>무조건 안전한</b> 경우만 참을 돌려준다.</p>
     * <p>Whether {@link #UNKNOWN} belongs here is a <b>policy</b> decision this method does not make:
     * with vendor idempotency unverified (RISK-A07) the caller decides from configuration. This
     * method returns true only where a retry is <b>unconditionally</b> safe.</p>
     *
     * @return 재시도가 안전하면 {@code true} / {@code true} when retrying is safe
     *
     * // req: FR-ATS-005, RISK-A07
     */
    public boolean isSafeToRetry() {
        return this == PENDING || this == FAILED;
    }

    /**
     * 더 손댈 필요가 없는 종착 상태인가. / Is this a terminal state?
     *
     * @return 종착이면 {@code true} / {@code true} when terminal
     *
     * // req: FR-ATS-005
     */
    public boolean isTerminal() {
        return this == SENT || this == DEAD;
    }
}
