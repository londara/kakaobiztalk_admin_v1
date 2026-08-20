package com.webcash.iris.biztalk.alimtalk.infra.vendor;

import com.webcash.iris.biztalk.alimtalk.domain.OutboxStatus;

/**
 * 벤더 호출의 결과. / The outcome of a vendor call.
 *
 * <h2>이 타입의 요점 / the point of this type</h2>
 * <p>성공·실패의 두 갈래가 아니라 <b>세 갈래</b>다. 전달되지 않았음이 <b>확인된</b> 실패와,
 * 전달 여부를 <b>알 수 없는</b> 실패를 구분한다. 그 구분이 재시도해도 되는지를 정한다.</p>
 * <p>Three outcomes, not two: a failure where non-delivery is <b>established</b> is distinguished from
 * one where delivery is <b>unknown</b>, and that distinction is what decides whether a retry is
 * permissible.</p>
 *
 * <p>레거시는 이 구분을 하지 않았다. {@code DomainUtil.isError(imoOut)} 이 참이면
 * {@code throw new JexWebBIZException("오류 발생")} — 문자열 하나로 모든 실패를 같게 만들었고,
 * 그래서 타임아웃과 4xx 가 구분되지 않았다. 재시도 정책을 세울 근거가 애초에 없었던 것이다.</p>
 * <p>The legacy made no such distinction: any error became
 * {@code throw new JexWebBIZException("오류 발생")}, one string collapsing every failure, so a timeout
 * and a 4xx were indistinguishable. There was no basis on which a retry policy could have been
 * built.</p>
 *
 * @param status    이 결과가 뜻하는 아웃박스 상태 / the outbox status this outcome implies
 * @param detail    운영자와 로그를 위한 요약 — <b>자격증명과 수신번호를 담지 않는다</b>
 *                  / a summary for operators and logs, carrying <b>no credential and no recipient</b>
 * @param httpCode  HTTP 상태 코드, 없으면 0 / the HTTP status, or 0 when there was none
 *
 * // source: jex/impl/OAuthHTTPConnection.java; biztalk_admin_50_s001_act.jsp:133-137
 * // req: FR-ATS-005, ADR-ATK-025, RISK-A07
 */
public record VendorSendResult(OutboxStatus status, String detail, int httpCode) {

    /**
     * 벤더가 접수를 확인했다. / The vendor acknowledged acceptance.
     *
     * @param detail 응답 요약 / a summary of the response
     * @return 접수 결과 / an accepted result
     *
     * // req: FR-ATS-005
     */
    public static VendorSendResult accepted(String detail) {
        return new VendorSendResult(OutboxStatus.SENT, detail, 200);
    }

    /**
     * 전달되지 않았음이 확인되었다 — 재시도 안전.
     * Non-delivery is established; retrying is safe.
     *
     * @param detail   실패 요약 / a summary of the failure
     * @param httpCode HTTP 상태 코드, 없으면 0 / the HTTP status, or 0
     * @return 확정 실패 / an established failure
     *
     * // req: FR-ATS-005, ADR-ATK-025
     */
    public static VendorSendResult notDelivered(String detail, int httpCode) {
        return new VendorSendResult(OutboxStatus.FAILED, detail, httpCode);
    }

    /**
     * 전달 여부를 알 수 없다 — 재시도는 벤더 멱등성에 달려 있다.
     * Delivery is unknown; retrying depends on vendor idempotency.
     *
     * <p>read 타임아웃이 대표적이다. 요청은 나갔고 응답은 오지 않았다. 이것을
     * {@link #notDelivered} 로 적으면 재시도가 중복 발송이 되고, {@link #accepted} 로 적으면
     * 조용한 미전달이 된다.</p>
     * <p>Typically a read timeout: the request left and nothing came back. Recording it as
     * {@link #notDelivered} makes a retry a duplicate; recording it as {@link #accepted} makes it a
     * silent non-delivery.</p>
     *
     * @param detail 요약 / a summary
     * @return 불확정 결과 / an indeterminate result
     *
     * // req: FR-ATS-005, RISK-A07
     */
    public static VendorSendResult unknown(String detail) {
        return new VendorSendResult(OutboxStatus.UNKNOWN, detail, 0);
    }
}
