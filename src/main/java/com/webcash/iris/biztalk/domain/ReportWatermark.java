package com.webcash.iris.biztalk.domain;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 집계 기준일 — 이 보고서가 얼마나 최신인지. / The aggregation watermark: how current the report is.
 *
 * <h2>이 클래스가 답하는 질문 / the question this answers</h2>
 * <p>운영 화면은 열자마자 "조회된 내용이 없습니다"를 보여준다. 화면이 고장 난 것이 아니라,
 * <b>날짜 입력의 기본값이 오늘</b>인데 집계 배치의 기본 실행은 <b>4일 전 하루</b>만 처리하기
 * 때문이다({@code LocalDate.now().minusDays(4)}, D-R26). 게다가 한 날짜만 다시 집계하는 것도
 * 불가능하다 — {@code START_DT=END_DT} 는 {@code INPUT ERROR} 로 거부된다.</p>
 * <p>The production screen opens on "조회된 내용이 없습니다". The screen is not broken: its date
 * fields <b>default to today</b> while the batch's default run covers <b>a single day four days
 * back</b> ({@code LocalDate.now().minusDays(4)}, D-R26). Re-aggregating one day is impossible
 * too — {@code START_DT=END_DT} is refused with {@code INPUT ERROR}.</p>
 *
 * <p>그래서 이 화면은 "데이터가 없다"가 아니라 <b>"아직 집계되지 않았다"</b>를 말해야 한다.</p>
 * <p>So the screen must say <b>"not yet aggregated"</b> rather than "no data".</p>
 *
 * <h2>이 값이 증명하지 <b>못</b>하는 것 / what this does <b>not</b> prove</h2>
 * <p>{@code max(TRDD)} 는 <b>행이 존재하는 가장 최근 일자</b>만 알려준다. 중간의 어떤 날이
 * 집계되었는지는 알 수 없다 — 배치가 지우고 다시 넣지 못한 날(D-R27)은 기준일 <b>아래</b>에
 * 있으므로 조용한 날과 구분되지 않는다. 이 한계는 ADR-RPT-022 에 명시되어 있으며, 완전한
 * 해소는 배치가 실행 이력을 남겨야 가능하다(OI-R01).</p>
 * <p>{@code max(TRDD)} reports only <b>the latest date carrying any row</b>. It says nothing about
 * interior days: a day the batch deleted and failed to reinsert (D-R27) sits <b>below</b> the
 * watermark and is indistinguishable from a quiet one. The limitation is stated in ADR-RPT-022;
 * closing it requires the batch to record what it did (OI-R01).</p>
 *
 * @param apiAsOf  API 집계 기준일. 데이터가 없으면 null / the API watermark, null when no data
 * @param bulkAsOf 대량 집계 기준일. 데이터가 없으면 null / the bulk watermark, null when no data
 *
 * // source: BATCH_BIZTALK_DAILY.java — LocalDate.now().minusDays(4)
 * // req: FR-RPT-013, FR-RPT-014, NFR-USE-R01, ADR-RPT-022
 */
public record ReportWatermark(LocalDate apiAsOf, LocalDate bulkAsOf) {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

    /** 두 출처 모두 알 수 없음. / Neither watermark is known. */
    public static final ReportWatermark UNKNOWN = new ReportWatermark(null, null);

    /**
     * {@code YYYYMMDD} 문자열 두 개로 기준일을 만든다.
     * Builds the watermark from two {@code YYYYMMDD} strings.
     *
     * @param apiMax  API 집계의 {@code max(TRDD)} / the API aggregate's max
     * @param bulkMax 대량 집계의 {@code max(TRDD)} / the bulk aggregate's max
     * @return 기준일 / the watermark
     */
    // req: FR-RPT-013
    public static ReportWatermark of(String apiMax, String bulkMax) {
        return new ReportWatermark(parseOrNull(apiMax), parseOrNull(bulkMax));
    }

    private static LocalDate parseOrNull(String raw) {
        if (raw == null || raw.isBlank() || raw.trim().length() != 8) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim(), YYYYMMDD);
        } catch (java.time.format.DateTimeParseException e) {
            // 집계 테이블에 형식이 깨진 TRDD 가 있어도 화면 전체를 실패시키지 않는다.
            // 기준일을 모른다고 말하는 편이, 보고서를 못 여는 것보다 낫다.
            // A malformed TRDD in the aggregate must not fail the whole screen; saying the
            // watermark is unknown beats refusing to open the report.
            return null;
        }
    }

    /**
     * 요청한 구분에서 신뢰할 수 있는 가장 최근 일자를 반환한다.
     * Returns the most recent date trustworthy for the requested source filter.
     *
     * <p>전체 조회일 때는 <b>둘 중 이른 쪽</b>이다. 한쪽이 뒤처져 있다면 합계도 그만큼
     * 뒤처져 있기 때문이다 — 늦은 쪽을 기준으로 말하면 합산 결과가 실제보다 최신인 것처럼
     * 보인다.</p>
     * <p>For 전체 it is <b>the earlier of the two</b>: if one source lags, so does the sum, and
     * quoting the later one would make the merged figures look fresher than they are.</p>
     *
     * @param source 요청된 발송 구분 / the requested source filter
     * @return 기준일. 알 수 없으면 null / the watermark, null when unknown
     */
    // req: FR-RPT-013, FR-RPTS-003
    public LocalDate effectiveAsOf(SendSource source) {
        return switch (source) {
            case API -> apiAsOf;
            case BULK -> bulkAsOf;
            case ALL -> earlier(apiAsOf, bulkAsOf);
        };
    }

    private static LocalDate earlier(LocalDate left, LocalDate right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isBefore(right) ? left : right;
    }

    /**
     * 주어진 일자가 아직 집계되지 않았는지 반환한다.
     * Whether the given date has not yet been aggregated.
     *
     * <p>기준일을 모르면 {@code false} 를 돌려준다 — 알 수 없음을 미집계로 단정하면 정상적인
     * 빈 결과까지 전부 미집계로 표시된다.</p>
     * <p>Returns {@code false} when the watermark is unknown: treating "unknown" as "not
     * aggregated" would label every legitimately empty result as such.</p>
     *
     * @param date   판정할 일자 / the date in question
     * @param source 요청된 발송 구분 / the requested source filter
     * @return 미집계 여부 / true when the date lies beyond the watermark
     */
    // req: FR-RPT-013, FR-RPT-014
    public boolean isNotYetAggregated(LocalDate date, SendSource source) {
        LocalDate asOf = effectiveAsOf(source);
        return asOf != null && date.isAfter(asOf);
    }
}
