package com.webcash.iris.biztalk.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 이번 조회에서 각 출처가 실제로 읽혔는지. / Whether each source was actually read.
 *
 * <h2>부분 결과를 완전한 결과처럼 보이게 두지 않는 이유 / why a partial result must say so</h2>
 * <p>배치는 대량 집계 실패를 {@code catch} 후 로그만 남기고 <b>성공을 보고한다</b>(D-R27).
 * 그래서 "그 날 대량 발송이 없었다"와 "그 날 대량 집계가 실패했다"는 데이터만으로는 구분되지
 * 않는다. 보고서도 구분할 수 없다 — 그러나 <b>구분할 수 없다는 사실을 말할 수는 있다.</b></p>
 * <p>The batch catches a failed bulk aggregation, logs it, and <b>reports success</b> (D-R27), so
 * "there was no bulk traffic that day" and "bulk aggregation failed that day" are
 * indistinguishable in the data. The report cannot tell them apart either — but it <b>can say
 * that it cannot</b>.</p>
 *
 * <p>같은 이유로 대량 데이터소스가 설정되어 있지 않거나 응답하지 않을 때, 결과를 API 분만
 * 담아 조용히 돌려주지 않는다. 조용한 부분 보고는 이 프로그램이 네 슬라이스 연속으로 만난
 * 실패 방식이다.</p>
 * <p>For the same reason, when the bulk datasource is unconfigured or unresponsive the result is
 * not quietly returned as API-only. Silent partial reporting is the failure mode this programme
 * has now met in four consecutive slices.</p>
 *
 * @param apiRead      API 집계를 읽었는지 / whether the API aggregate was read
 * @param bulkRead     대량 집계를 읽었는지 / whether the bulk aggregate was read
 * @param apiFailure   API 조회 실패 사유. 없으면 null / why the API read failed, null when it did not
 * @param bulkFailure  대량 조회 실패 사유. 없으면 null / why the bulk read failed, null when it did not
 *
 * // source: BATCH_BIZTALK_DAILY.java — catch (JexBIZException e) { LOG.error(e); }
 * // req: FR-RPTS-005, NFR-OPS-R01, ADR-RPT-022
 */
public record SourceAvailability(
        boolean apiRead,
        boolean bulkRead,
        String apiFailure,
        String bulkFailure) {

    /** 두 출처 모두 정상적으로 읽힘. / Both sources read successfully. */
    public static final SourceAvailability BOTH = new SourceAvailability(true, true, null, null);

    /**
     * 요청한 구분에 따라 읽지 <b>않아도 되는</b> 출처를 표시한 값을 만든다.
     * Builds a value marking the source that was deliberately not read.
     *
     * <p>발송구분을 좁혀서 읽지 않은 것은 <b>결손이 아니다.</b> 사용자가 그렇게 요청했다.</p>
     * <p>Not reading a source because the filter narrowed it is <b>not a gap</b> — the user asked
     * for that.</p>
     *
     * @param source 요청된 발송 구분 / the requested source filter
     * @return 가용성 / the availability
     */
    // req: FR-RPTS-002
    public static SourceAvailability forFilter(SendSource source) {
        return new SourceAvailability(source.readsApi(), source.readsBulk(), null, null);
    }

    /**
     * 대량 집계 조회 실패를 기록한 값을 반환한다.
     * Returns a copy recording a failed bulk read.
     *
     * @param reason 실패 사유 / the reason
     * @return 갱신된 가용성 / the updated availability
     */
    // req: FR-RPTS-005, NFR-OPS-R01
    public SourceAvailability withBulkFailure(String reason) {
        return new SourceAvailability(apiRead, false, apiFailure, reason);
    }

    /**
     * API 집계 조회 실패를 기록한 값을 반환한다.
     * Returns a copy recording a failed API read.
     *
     * @param reason 실패 사유 / the reason
     * @return 갱신된 가용성 / the updated availability
     */
    // req: FR-RPTS-005, NFR-OPS-R01
    public SourceAvailability withApiFailure(String reason) {
        return new SourceAvailability(false, bulkRead, reason, bulkFailure);
    }

    /**
     * 요청한 구분에 비해 결과가 불완전한지 반환한다.
     * Whether the result is incomplete relative to what was asked for.
     *
     * @param requested 요청된 발송 구분 / the requested source filter
     * @return 불완전 여부 / true when a requested source is missing
     */
    // req: FR-RPTS-005
    public boolean isIncomplete(SendSource requested) {
        return (requested.readsApi() && !apiRead) || (requested.readsBulk() && !bulkRead);
    }

    /**
     * 사용자에게 보일 결손 설명을 반환한다.
     * Returns user-facing notes describing what is missing.
     *
     * @param requested 요청된 발송 구분 / the requested source filter
     * @return 설명 목록. 완전하면 비어 있음 / the notes, empty when complete
     */
    // req: FR-RPTS-005, NFR-OPS-R01
    public List<String> incompleteNotes(SendSource requested) {
        List<String> notes = new ArrayList<>(2);
        if (requested.readsApi() && !apiRead) {
            notes.add("API발송 집계를 읽지 못했습니다"
                    + (apiFailure == null ? "" : " (" + apiFailure + ")")
                    + ". 표시된 수치는 불완전합니다.");
        }
        if (requested.readsBulk() && !bulkRead) {
            notes.add("대량발송 집계를 읽지 못했습니다"
                    + (bulkFailure == null ? "" : " (" + bulkFailure + ")")
                    + ". 표시된 수치는 불완전합니다.");
        }
        return notes;
    }
}
