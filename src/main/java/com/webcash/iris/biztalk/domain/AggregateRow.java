package com.webcash.iris.biztalk.domain;

import java.util.EnumMap;
import java.util.Map;

/**
 * 한 출처에서 읽은 집계 1행. / One aggregate row as read from a single source.
 *
 * <p>매퍼가 그대로 채우는 형태이며, 채널마다 {@code columnPrefix} 로 묶인
 * {@link ChannelCounters} 를 하나씩 가진다. 두 출처의 매퍼가 <b>같은 형태</b>를 반환하는
 * 것이 병합의 전제다 — 형태가 갈리면 합칠 수 없다.</p>
 * <p>The shape the mappers populate directly, one {@link ChannelCounters} per channel bound by
 * {@code columnPrefix}. Both sources' mappers returning the <b>same shape</b> is the merge's
 * precondition; divergent shapes cannot be summed.</p>
 *
 * <p>{@code institutionName} 이 여기 있는 이유: 레거시는 이 값을 출처마다 다르게 구했다.
 * API 쿼리는 상관 서브쿼리로 인라인 해결했고, 대량 쿼리는 {@code '' AS IS_NM} 을 반환한 뒤
 * Java 가 {@code HashMap} 으로 덧칠했다 — 두 경로 모두 조회 실패 시 조용히 빈칸이 됐다
 * (D-R12). 이제 두 매퍼 모두 조인으로 해결하고, 해결 실패는 {@link ReportRow} 에서 명시
 * 표시된다.</p>
 * <p>Why {@code institutionName} lives here: the legacy resolved it differently per source —
 * inline correlated subquery for API, {@code '' AS IS_NM} plus a Java {@code HashMap} patch for
 * bulk — and both failed silently to a blank cell (D-R12). Both mappers now resolve it by join,
 * and a failure to resolve is marked explicitly in {@link ReportRow}.</p>
 *
 * @param tradeDate        거래일자 / the trade date
 * @param institutionCode  이용기관 코드 / the institution code
 * @param institutionName  기관명. 해결 실패 시 null / the institution name, null when unresolved
 * @param alimtalk         알림톡 / notification talk
 * @param friendText       친구톡 텍스트 / friend talk, text
 * @param friendImage      친구톡 일반 이미지 (파생) / friend talk, normal image (derived)
 * @param friendWideImage  친구톡 와이드 이미지 / friend talk, wide image
 * @param sms              SMS
 * @param lms              LMS
 * @param mms              MMS
 *
 * // source: IDO.KKB_APITR_SMTN_L001, IDO.BULK_KKB_APITR_SMTN_L001
 * // req: FR-RPT-009, FR-RPT-012, FR-RPTS-001
 */
public record AggregateRow(
        String tradeDate,
        String institutionCode,
        String institutionName,
        ChannelCounters alimtalk,
        ChannelCounters friendText,
        ChannelCounters friendImage,
        ChannelCounters friendWideImage,
        ChannelCounters sms,
        ChannelCounters lms,
        ChannelCounters mms) {

    /**
     * 정렬·병합에 쓰는 키를 반환한다. / Returns the sort and merge key.
     *
     * @return 집계 키 / the aggregate key
     */
    // req: FR-RPT-006, FR-RPTS-003
    public AggregateKey key() {
        return new AggregateKey(tradeDate, institutionCode);
    }

    /**
     * 채널별 건수를 열거 가능한 형태로 반환한다.
     * Returns the counters keyed by channel, for iteration.
     *
     * <p>{@link EnumMap} 이므로 순서는 {@link MessageChannel} 선언 순서 — 화면 컬럼 순서와
     * 같다.</p>
     * <p>An {@link EnumMap}, so iteration follows {@link MessageChannel}'s declaration order,
     * which is the screen's column order.</p>
     *
     * @return 채널별 건수 / counters by channel
     */
    // req: FR-RPT-009
    public Map<MessageChannel, ChannelCounters> counters() {
        EnumMap<MessageChannel, ChannelCounters> byChannel = new EnumMap<>(MessageChannel.class);
        byChannel.put(MessageChannel.ALIMTALK, nullToZero(alimtalk));
        byChannel.put(MessageChannel.FRIEND_TEXT, nullToZero(friendText));
        byChannel.put(MessageChannel.FRIEND_IMAGE, nullToZero(friendImage));
        byChannel.put(MessageChannel.FRIEND_WIDE_IMAGE, nullToZero(friendWideImage));
        byChannel.put(MessageChannel.SMS, nullToZero(sms));
        byChannel.put(MessageChannel.LMS, nullToZero(lms));
        byChannel.put(MessageChannel.MMS, nullToZero(mms));
        return byChannel;
    }

    private static ChannelCounters nullToZero(ChannelCounters value) {
        return value == null ? ChannelCounters.ZERO : value;
    }
}
