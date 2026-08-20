package com.webcash.iris.biztalk.domain;

import java.util.List;
import java.util.Map;

/**
 * 화면과 엑셀이 공유하는 보고서 1행. / One report row, shared by the screen and the export.
 *
 * <p>병합 결과이므로 출처가 둘일 수 있다. {@code source} 가 {@link SendSource#ALL} 이면
 * 이 행은 두 집계의 <b>합</b>이며(FR-RPTS-003), {@link SendSource#API} 나
 * {@link SendSource#BULK} 이면 한쪽에만 존재하던 행이다.</p>
 * <p>A merge result, so it may come from two sources. {@link SendSource#ALL} means this row is
 * the <b>sum</b> of both aggregates (FR-RPTS-003); {@link SendSource#API} or
 * {@link SendSource#BULK} means it existed in only one.</p>
 *
 * @param source           발송 구분 / the send source
 * @param tradeDate        거래일자 {@code YYYYMMDD} / the trade date
 * @param institutionCode  이용기관 코드 / the institution code
 * @param institutionName  기관명. 해결 실패 시 null / the institution name, null when unresolved
 * @param counters         채널별 건수 / counters by channel
 *
 * // req: FR-RPT-009, FR-RPT-010, FR-RPT-012, FR-RPT-016, FR-RPTS-003
 */
public record ReportRow(
        SendSource source,
        String tradeDate,
        String institutionCode,
        String institutionName,
        Map<MessageChannel, ChannelCounters> counters) {

    /**
     * 한 출처의 집계 행을 보고서 행으로 만든다.
     * Builds a report row from a single source's aggregate row.
     *
     * @param row    집계 행 / the aggregate row
     * @param source 출처 / the source it came from
     * @return 보고서 행 / the report row
     */
    // req: FR-RPTS-003
    public static ReportRow of(AggregateRow row, SendSource source) {
        return new ReportRow(source, row.tradeDate(), row.institutionCode(),
                row.institutionName(), row.counters());
    }

    /**
     * 같은 키의 두 행을 더해 한 행으로 만든다.
     * Sums two rows carrying the same key into one.
     *
     * <p>{@link SendSource#ALL} 로 표시되며, 기관명은 <b>해결된 쪽</b>을 취한다. 대량 집계
     * 데이터베이스에는 기관 마스터가 없으므로(AMB-R04) 대량 쪽 이름이 비어 있는 것은
     * 정상이며, API 쪽 값이 그 자리를 채운다.</p>
     * <p>Marked {@link SendSource#ALL}; the institution name is taken from <b>whichever side
     * resolved it</b>. The bulk database holds no institution master (AMB-R04), so a blank name
     * on the bulk side is expected and the API side fills it.</p>
     *
     * @param api  API 집계 행 / the API row
     * @param bulk 대량 집계 행 / the bulk row
     * @return 합산된 행 / the summed row
     * @throws IllegalArgumentException 키가 다르면 / when the keys differ
     */
    // req: FR-RPTS-003, FR-RPT-012
    public static ReportRow merged(AggregateRow api, AggregateRow bulk) {
        if (!api.key().equals(bulk.key())) {
            // 서로 다른 키를 더하면 조용히 틀린 숫자가 나온다. 병합기의 계약 위반이므로
            // 방어적으로 즉시 실패한다.
            // Summing different keys yields silently wrong figures. This is a broken contract in
            // the merger, so it fails immediately rather than defensively continuing.
            throw new IllegalArgumentException(
                    "Cannot merge rows with different keys: " + api.key() + " vs " + bulk.key());
        }

        Map<MessageChannel, ChannelCounters> apiCounters = api.counters();
        Map<MessageChannel, ChannelCounters> bulkCounters = bulk.counters();
        Map<MessageChannel, ChannelCounters> summed = new java.util.EnumMap<>(MessageChannel.class);
        for (MessageChannel channel : MessageChannel.values()) {
            summed.put(channel,
                    apiCounters.getOrDefault(channel, ChannelCounters.ZERO)
                            .plus(bulkCounters.getOrDefault(channel, ChannelCounters.ZERO)));
        }

        String name = api.institutionName() != null && !api.institutionName().isBlank()
                ? api.institutionName()
                : bulk.institutionName();

        return new ReportRow(SendSource.ALL, api.tradeDate(), api.institutionCode(), name, summed);
    }

    /**
     * 정렬·병합 키를 반환한다. / Returns the sort and merge key.
     *
     * @return 집계 키 / the aggregate key
     */
    // req: FR-RPT-006
    public AggregateKey key() {
        return new AggregateKey(tradeDate, institutionCode);
    }

    /**
     * 기관명이 해결되지 않았는지 반환한다. / Whether the institution name failed to resolve.
     *
     * <p>레거시는 이 경우 빈칸을 그렸고, 조회가 실패했다는 사실은 어디에도 남지 않았다
     * (D-R12). 화면은 이 값이 참이면 기관코드와 함께 미해결 표시를 그린다.</p>
     * <p>The legacy drew a blank cell and recorded nothing about the failed lookup (D-R12). The
     * screen renders the code plus an unresolved marker when this is true.</p>
     *
     * @return 미해결 여부 / true when unresolved
     */
    // req: FR-RPT-012
    public boolean institutionUnresolved() {
        return institutionName == null || institutionName.isBlank();
    }

    /**
     * 산술 항등식을 위반하는 채널을 반환한다.
     * Returns the channels whose arithmetic identity does not hold.
     *
     * <p>비어 있지 않다면 이 행은 <b>사실로 표시되어서는 안 된다</b>(FR-RPT-010). 이 슬라이스는
     * 집계를 고칠 수 없으므로(CONST-DATA-R01) 고치는 대신 알린다.</p>
     * <p>A non-empty result means the row <b>must not be presented as fact</b> (FR-RPT-010). This
     * slice cannot repair the aggregate (CONST-DATA-R01), so it reports instead.</p>
     *
     * @return 위반 채널 목록 / the offending channels
     */
    // req: FR-RPT-010
    public List<MessageChannel> reconciliationFailures() {
        return counters.entrySet().stream()
                .filter(entry -> !entry.getValue().reconciles() || entry.getValue().hasNegative())
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * 총 건수를 반환한다. / Returns the grand total.
     *
     * <p>{@code AT + FT + SMS + LMS + MMS} 이며, 친구톡은 텍스트·일반이미지·와이드이미지의
     * 합으로 <b>한 번만</b> 계산된다. 레거시 SQL 은 저장된 {@code FT_CNT} 를 썼고 그 하위
     * 채널을 다시 더하지 않았다 — 여기서도 이중 계상하지 않는다(CONST-BIZ-R02).</p>
     * <p>{@code AT + FT + SMS + LMS + MMS}, where the friend-talk part is the sum of text, normal
     * image and wide image counted <b>once</b>. The legacy SQL used the stored {@code FT_CNT} and
     * did not re-add its components; this does not double-count either (CONST-BIZ-R02).</p>
     *
     * @return 총 건수 / the grand total
     */
    // source: IDO.KKB_APITR_SMTN_L002 — sum(AT_CNT + FT_CNT + MMS_CNT + SMS_CNT + LMS_CNT)
    // req: CONST-BIZ-R02
    public long grandTotal() {
        return counters.values().stream().mapToLong(ChannelCounters::total).sum();
    }
}
