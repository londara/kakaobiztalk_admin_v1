package com.webcash.iris.common.logging;

import java.util.UUID;
import org.slf4j.MDC;

/**
 * 요청 상관 식별자. / The request correlation identifier.
 *
 * <p>한 요청이 남기는 애플리케이션 로그와 감사 기록을 하나로 묶는 값이다. 이것이 없으면
 * 장애 조사에서 "이 오류 로그가 저 감사 기록과 같은 요청인가"를 <b>시각 근접성으로 추측</b>해야
 * 하고, 동시 요청이 있는 순간 그 추측은 무너진다.</p>
 * <p>Ties the application logs and the audit records left by one request together. Without it an
 * investigation has to <b>guess from timestamps</b> whether an error log and an audit record
 * belong to the same request, and that guess fails the moment requests overlap.</p>
 *
 * <p>{@link MDC} 에 담기므로 로그 패턴이 자동으로 출력하고, 코드가 매 로그 호출마다
 * 명시적으로 넘길 필요가 없다. MDC 는 {@code ThreadLocal} 이므로 {@link TenantContext} 와
 * 같은 위험을 공유한다 — <b>정리하지 않으면 스레드 재사용 시 다음 요청에 남는다.</b>
 * 정리는 {@link RequestLoggingFilter} 의 {@code finally} 에서 무조건 수행한다.</p>
 * <p>Held in {@link MDC} so the log pattern emits it automatically rather than every call site
 * passing it. MDC is a {@code ThreadLocal} and carries the same hazard as the tenant context:
 * <b>not clearing it leaves the value on a reused thread</b>. Clearing happens unconditionally in
 * {@link RequestLoggingFilter}'s {@code finally}.</p>
 *
 * <p>레거시에는 대응물이 없다. Jex 런타임의 {@code mntLogYn=Y} 로그는 요청 단위로 묶이지
 * 않았고, 그래서 한 사용자의 한 동작이 남긴 흔적을 사후에 모으는 일이 사실상 불가능했다.</p>
 * <p>No legacy counterpart: the Jex runtime's log was not correlated per request, which made
 * reassembling the trace of a single user action after the fact effectively impossible.</p>
 *
 * // req: NFR-OPS-AUDIT-D01, NFR-OPS-AUDIT-L02, ADR-006
 */
public final class CorrelationId {

    /** MDC 키 — 로그 패턴({@code logback-spring.xml})이 참조한다. / MDC key, referenced by the log pattern. */
    public static final String MDC_KEY = "cid";

    private CorrelationId() {
    }

    /**
     * 새 상관 식별자를 생성하여 MDC 에 넣는다. / Generates a correlation id and binds it to the MDC.
     *
     * <p>클라이언트가 보낸 값을 <b>쓰지 않는다.</b> 헤더로 받으면 요청자가 임의의 값을 지정할
     * 수 있고, 그러면 서로 다른 요청이 같은 식별자를 갖도록 만들어 로그를 오염시키거나 다른
     * 사용자의 흔적에 자기 요청을 섞어 넣을 수 있다. 상관 식별자는 <b>서버가 발급</b>한다.</p>
     * <p>A client-supplied value is <b>not used.</b> Accepting one from a header would let a
     * caller choose it, and therefore make separate requests share an id — poisoning the log or
     * interleaving their own trace with another user's. The server issues it.</p>
     *
     * @return 생성된 식별자 / the generated identifier
     */
    // req: NFR-OPS-AUDIT-D01
    public static String begin() {
        String id = UUID.randomUUID().toString().substring(0, 8);
        MDC.put(MDC_KEY, id);
        return id;
    }

    /**
     * 현재 요청의 상관 식별자를 반환한다. / Returns the current request's correlation id.
     *
     * @return 식별자, 없으면 null / the identifier, or null when unbound
     */
    // req: NFR-OPS-AUDIT-D01
    public static String current() {
        return MDC.get(MDC_KEY);
    }

    /**
     * MDC 에서 식별자를 제거한다. / Removes the identifier from the MDC.
     *
     * <p>누락하면 스레드 풀 환경에서 다음 요청이 이전 요청의 식별자를 물려받는다 — 로그가
     * 서로 다른 두 요청을 하나로 보이게 만든다.</p>
     * <p>Omitting this lets the next request inherit the previous id on a pooled thread, making
     * the log present two distinct requests as one.</p>
     */
    // req: NFR-OPS-AUDIT-D01
    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
