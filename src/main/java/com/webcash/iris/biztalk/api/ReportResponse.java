package com.webcash.iris.biztalk.api;

import com.webcash.iris.biztalk.domain.ChannelCounters;
import com.webcash.iris.biztalk.domain.MessageChannel;
import com.webcash.iris.biztalk.domain.ReportPage;
import com.webcash.iris.biztalk.domain.ReportRow;
import com.webcash.iris.biztalk.domain.SendSource;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 보고서 조회 응답. / The report query response.
 *
 * <h2>모양이 하나뿐인 것이 요점이다 / one shape, and that is the point</h2>
 * <p>레거시 응답은 환경에 따라 달랐다. 운영이 아닌 곳에서는 {@code REC}(레코드 목록)를,
 * 운영에서는 {@code REC2}(Java {@code Arrays.toString} 결과 문자열)를 돌려주었고, 브라우저는
 * {@code dat.REC2 === undefined} 로 분기했다. 게다가 그 문자열은 {@code JSON.parse} 로
 * 파싱되었으므로 기관명에 따옴표가 하나만 들어가도 화면이 깨졌다(D-R5, D-R6).</p>
 * <p>The legacy response differed by environment: {@code REC} (a record list) off production,
 * {@code REC2} (the output of Java's {@code Arrays.toString}) on it, with the browser branching
 * on {@code dat.REC2 === undefined}. That string was then fed to {@code JSON.parse}, so a single
 * quote in an institution name broke the screen (D-R5, D-R6).</p>
 *
 * <p>여기에는 분기가 없고, 파싱해야 하는 필드도 없다.</p>
 * <p>No branch here, and no field the client must parse.</p>
 *
 * @param rows           행 / the rows
 * @param columns        채널 컬럼 정의 / the channel column definitions
 * @param nextSeek       다음 페이지 키 / the next page's seek key
 * @param totalCount     전체 건수. 상한 초과 시 null / the exact total, null above the probe ceiling
 * @param hasMore        다음 페이지 존재 여부 / whether more rows follow
 * @param watermark      집계 기준일 / the aggregation watermark
 * @param incompleteNotes 부분 결과 안내. 완전하면 비어 있음 / partial-result notes, empty when complete
 *
 * // req: FR-RPT-007, FR-RPT-008, FR-RPT-009, FR-RPT-013, FR-RPT-016, FR-RPTS-005
 */
public record ReportResponse(
        List<Row> rows,
        List<Column> columns,
        Seek nextSeek,
        Long totalCount,
        boolean hasMore,
        Watermark watermark,
        List<String> incompleteNotes) {

    /**
     * 도메인 페이지를 응답으로 옮긴다. / Maps a domain page onto the response.
     *
     * @param page      도메인 페이지 / the domain page
     * @param requested 요청된 발송 구분 / the requested source filter
     * @return 응답 / the response
     */
    // req: FR-RPT-007
    public static ReportResponse from(ReportPage page, SendSource requested) {
        return new ReportResponse(
                page.rows().stream().map(Row::from).toList(),
                List.of(MessageChannel.values()).stream().map(Column::from).toList(),
                page.nextSeek() == null
                        ? null
                        : new Seek(page.nextSeek().tradeDate(), page.nextSeek().institutionCode()),
                page.totalCount(),
                page.hasMore(),
                Watermark.from(page.watermark(), requested),
                page.availability().incompleteNotes(requested));
    }

    /**
     * 채널 컬럼 정의. / A channel column definition.
     *
     * @param key   채널 식별자 / the channel key
     * @param label 표시명 / the display label
     */
    public record Column(String key, String label) {

        /**
         * 채널을 컬럼 정의로 옮긴다. / Maps a channel onto a column definition.
         *
         * @param channel 채널 / the channel
         * @return 컬럼 정의 / the column
         */
        static Column from(MessageChannel channel) {
            return new Column(channel.name(), channel.label());
        }
    }

    /**
     * 보고서 1행. / One report row.
     *
     * @param source          발송 구분 표시명 / the source label
     * @param tradeDate       일자 {@code YYYYMMDD} / the trade date
     * @param institutionCode 기관코드 / the institution code
     * @param institutionName 기관명. 미해결이면 null / the institution name, null when unresolved
     * @param counters        채널별 네 건수 / four counters per channel
     * @param grandTotal      총 건수 / the grand total
     * @param reconciles      산술 항등식 성립 여부 / whether the arithmetic identity holds
     */
    public record Row(
            String source,
            String tradeDate,
            String institutionCode,
            String institutionName,
            Map<String, Counters> counters,
            long grandTotal,
            boolean reconciles) {

        static Row from(ReportRow row) {
            Map<String, Counters> counters = new LinkedHashMap<>();
            for (Map.Entry<MessageChannel, ChannelCounters> entry : row.counters().entrySet()) {
                counters.put(entry.getKey().name(), Counters.from(entry.getValue()));
            }
            return new Row(
                    row.source().label(),
                    row.tradeDate(),
                    row.institutionCode(),
                    row.institutionName(),
                    counters,
                    row.grandTotal(),
                    row.reconciliationFailures().isEmpty());
        }
    }

    /**
     * 한 채널의 네 건수. / One channel's four counters.
     *
     * <p>넷을 모두 보낸다. 레거시는 처리중을 조회하고도 화면과 엑셀 어디에도 싣지 않아
     * 전체 = 성공 + 실패 가 성립하지 않았다(D-R14).</p>
     * <p>All four are sent. The legacy queried the in-flight count and put it in neither the
     * screen nor the export, so 전체 = 성공 + 실패 never held (D-R14).</p>
     *
     * @param total    전체 / total
     * @param success  성공 / success
     * @param failed   실패 / failed
     * @param inFlight 처리중 / in flight
     */
    // req: FR-RPT-009, FR-RPT-010, AMB-R02
    public record Counters(long total, long success, long failed, long inFlight) {

        static Counters from(ChannelCounters counters) {
            return new Counters(counters.total(), counters.success(),
                    counters.failed(), counters.inFlight());
        }
    }

    /**
     * 다음 페이지 요청에 그대로 실어 보낼 키. / The key to send back for the next page.
     *
     * @param tradeDate       일자 / the trade date
     * @param institutionCode 기관코드 / the institution code
     */
    public record Seek(String tradeDate, String institutionCode) {
    }

    /**
     * 집계 기준일. / The aggregation watermark.
     *
     * @param apiAsOf       API 집계 기준일 / the API watermark
     * @param bulkAsOf      대량 집계 기준일 / the bulk watermark
     * @param effectiveAsOf 요청 구분에 적용되는 기준일 / the watermark that applies to the request
     *
     * // req: FR-RPT-013, NFR-USE-R01
     */
    public record Watermark(LocalDate apiAsOf, LocalDate bulkAsOf, LocalDate effectiveAsOf) {

        static Watermark from(com.webcash.iris.biztalk.domain.ReportWatermark watermark,
                              SendSource requested) {
            return new Watermark(watermark.apiAsOf(), watermark.bulkAsOf(),
                    watermark.effectiveAsOf(requested));
        }
    }
}
