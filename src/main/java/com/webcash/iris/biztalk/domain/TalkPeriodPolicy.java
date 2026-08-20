package com.webcash.iris.biztalk.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 톡전송 내역의 조회 기간 규칙 — 일자 범위 + 시각 경계.
 * The 톡전송 내역 query-period rules: a date range plus time-of-day bounds.
 *
 * <h2>레거시가 검증한 것 / what the legacy validated</h2>
 * <p>브라우저에서 시작시각과 종료시각을 <b>초 단위로 환산해 비교한 것</b>이 전부다. 서비스
 * 계약 {@code WSVC.biztalk_admin_30_l001} 은 {@code TRDD}·{@code START_TIME}·{@code END_TIME}
 * 을 길이도 타입도 없이 선언했고, 액션 JSP 는 아무 검증도 하지 않았다(D-T24).</p>
 * <p>Converting both times to seconds-of-day and comparing them, in the browser. The contract
 * declared {@code TRDD}, {@code START_TIME} and {@code END_TIME} with neither length nor type, and
 * the action JSP validated nothing (D-T24).</p>
 *
 * <h2>{@code 999999} 라는 값 / the {@code 999999} value</h2>
 * <p>종료시각을 비우면 자바스크립트가 {@code "999999"} 를 채웠다. 이것은 <b>시각이
 * 아니다</b> — 99시 99분 99초다. 질의가 동작한 이유는 {@code RGDT} 가 문자 컬럼이어서
 * {@code BETWEEN} 이 <b>사전순 비교</b>로 처리되었기 때문이고, 즉 컬럼 타입이 바뀌면 조용히
 * 깨지는 암묵적 계약에 기대고 있었다. 여기서는 <b>생략된 경계가 그 날의 처음/끝</b>을
 * 뜻하고, 실제 시각만 바인딩된다(FR-TLK-008).</p>
 * <p>Leaving 종료시각 blank made the JavaScript send {@code "999999"} — <b>not a time</b>, but 99
 * hours 99 minutes 99 seconds. The query worked only because {@code RGDT} is a character column, so
 * {@code BETWEEN} did a <b>lexical</b> comparison: an implicit contract that breaks silently if the
 * column type changes. Here an <b>omitted bound means the start or end of the day</b> and only real
 * times are bound (FR-TLK-008).</p>
 *
 * <h2>31일인 이유 / why 31 days</h2>
 * <p>레거시 술어는 {@code TRDD = :TRDD} 로 <b>단 하루</b>만 허용했다. 범위를 열면서 상한이
 * 필요해졌고, 행의 단위가 거래 1건이므로 문자내역과 같은 31일이다(AMB-T02, AMB-06 선례).
 * 일자 검증 자체는 {@link PeriodPolicy} 가 한다 — 규칙을 복제하지 않고 상한만 넘긴다.</p>
 * <p>The legacy predicate {@code TRDD = :TRDD} allowed <b>exactly one day</b>. Opening it to a range
 * required a cap, and since the grain is one transaction per row it is 31 days, as in 문자내역
 * (AMB-T02, following AMB-06). Date validation itself is {@link PeriodPolicy}'s — the cap is passed
 * rather than the rules duplicated.</p>
 *
 * // source: biztalk_admin_30_view.jsp — single START_DT input, startTime/endTime maxlength=6
 * // source: biztalk_admin_30.js — getDat(): endTime = "999999" when blank; seconds-of-day compare
 * // source: IDO.KKB_APITR_HSTR_L001 — WHERE TRDD = :TRDD AND RGDT BETWEEN :START_TIME AND :END_TIME
 * // req: FR-TLK-007, FR-TLK-008, FR-TLK-014, AMB-T02, ADR-TLK-027
 */
public final class TalkPeriodPolicy {

    /** 허용 최대 기간(일). 작업 가정 AMB-T02. / Maximum span in days, per working assumption AMB-T02. */
    public static final int MAX_SPAN_DAYS = 31;

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter HHMMSS = DateTimeFormatter.ofPattern("HHmmss");

    private TalkPeriodPolicy() {
    }

    /**
     * 일자 범위와 시각 경계를 검증하고 정규화한다.
     * Validates and normalises the date range and the time bounds.
     *
     * <p>시각은 <b>선택</b>이다. 비우면 해당 날짜의 처음({@code 000000}) 또는
     * 끝({@code 235959})으로 해석한다 — 값이 없다는 것과 잘못된 값은 다르게 다룬다.</p>
     * <p>The times are <b>optional</b>: blank means the start ({@code 000000}) or the end
     * ({@code 235959}) of the day. An absent value and an invalid one are treated differently.</p>
     *
     * @param rawFrom     시작일자 {@code YYYYMMDD} / the start date
     * @param rawTo       종료일자 {@code YYYYMMDD}. 비우면 시작일자와 같다 / the end date; defaults to the start date
     * @param rawFromTime 시작시각 {@code HHMMSS} 또는 {@code HHMM}, 생략 가능 / the start time, optional
     * @param rawToTime   종료시각 {@code HHMMSS} 또는 {@code HHMM}, 생략 가능 / the end time, optional
     * @return 검증된 기간 / the validated window
     * @throws PeriodPolicy.InvalidPeriodException 형식·순서·기간 위반 / on a malformed, inverted or over-long window
     */
    // req: FR-TLK-007, FR-TLK-008, FR-TLK-014
    public static TalkWindow validate(String rawFrom, String rawTo,
                                      String rawFromTime, String rawToTime) {

        // 종료일자를 비우면 하루 조회다 — 레거시의 유일한 형태이며 기본값으로 유지한다.
        // A blank end date means a single day: the legacy's only shape, kept as the default.
        String effectiveTo = (rawTo == null || rawTo.isBlank()) ? rawFrom : rawTo;

        PeriodPolicy.ReportPeriod dates =
                PeriodPolicy.validate(rawFrom, effectiveTo, MAX_SPAN_DAYS);

        LocalTime fromTime = parseTime(rawFromTime, LocalTime.MIN, "시작시각");
        LocalTime toTime = parseTime(rawToTime, LocalTime.of(23, 59, 59), "종료시각");

        // 하루 조회일 때만 시각 순서가 의미를 갖는다. 여러 날에 걸친 조회에서 09:00~08:00 은
        // "매일 09시부터 다음날 08시까지"가 아니라 그냥 각 날의 09:00~08:00 이므로 빈 결과가
        // 된다 — 레거시 D8(문자내역)이 시각만 비교해 정상 범위를 거부한 것의 반대 실수다.
        // 그래서 여러 날 조회에서는 시각 역전을 거부하지 않고, 하루 조회에서만 거부한다.
        //
        // Time ordering only means something for a single-day query. Across several days,
        // 09:00–08:00 is not "09:00 each day to 08:00 the next" but an empty window per day — the
        // mirror image of 문자내역's D8, which compared times alone and refused valid multi-day
        // ranges. So inversion is refused for a one-day query and permitted (as empty) beyond it.
        if (dates.from().equals(dates.to()) && fromTime.isAfter(toTime)) {
            throw new PeriodPolicy.InvalidPeriodException(
                    "시작시각이 종료시각보다 늦습니다. / The start time is after the end time.");
        }

        return new TalkWindow(dates.from(), dates.to(), fromTime, toTime);
    }

    /**
     * {@code HHMMSS} 또는 {@code HHMM} 을 시각으로 해석한다. 비어 있으면 기본값을 쓴다.
     * Parses {@code HHMMSS} or {@code HHMM}; a blank value takes the default.
     *
     * <p>레거시 입력은 {@code maxlength="6"} 이지만 화면 기본값은 {@code HHMM} + {@code "00"}
     * 형태로 만들어졌다. 네 자리 입력도 받아들이되 <b>서버에서</b> 정규화한다.</p>
     * <p>The legacy input is {@code maxlength="6"} while its default was composed as {@code HHMM}
     * plus {@code "00"}. Four digits are accepted and normalised <b>on the server</b>.</p>
     */
    private static LocalTime parseTime(String raw, LocalTime fallback, String fieldLabel) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String trimmed = raw.trim();
        if (!trimmed.chars().allMatch(Character::isDigit)) {
            throw new PeriodPolicy.InvalidPeriodException(
                    fieldLabel + "은 숫자여야 합니다: " + trimmed + " / "
                            + fieldLabel + " must be numeric: " + trimmed);
        }
        String padded = switch (trimmed.length()) {
            case 4 -> trimmed + "00";
            case 6 -> trimmed;
            default -> throw new PeriodPolicy.InvalidPeriodException(
                    fieldLabel + "은 HHMM 또는 HHMMSS 형식이어야 합니다: " + trimmed + " / "
                            + fieldLabel + " must be HHMM or HHMMSS: " + trimmed);
        };
        try {
            return LocalTime.parse(padded, HHMMSS);
        } catch (Exception e) {
            // ⚠ 레거시의 999999 가 여기서 걸린다. 사전순 비교에 기대던 센티널은 이제
            // 존재하지 않는 시각으로 명시적으로 거부된다(D-T24).
            // The legacy's 999999 lands here: the sentinel that relied on lexical comparison is
            // now explicitly refused as a non-existent time (D-T24).
            throw new PeriodPolicy.InvalidPeriodException(
                    fieldLabel + "이 유효한 시각이 아닙니다: " + trimmed + " / "
                            + fieldLabel + " is not a valid time: " + trimmed);
        }
    }

    /**
     * 검증을 통과한 조회 구간. / A query window that has passed validation.
     *
     * <p>이 타입이 존재하는 이유는 <b>검증되지 않은 값이 매퍼에 도달할 수 없게</b> 하기
     * 위해서다. 매퍼는 문자열 네 개가 아니라 이 타입을 받는다.</p>
     * <p>This type exists so <b>no unvalidated value can reach a mapper</b>: mappers take this
     * rather than four strings.</p>
     *
     * @param fromDate 시작일자 / the start date
     * @param toDate   종료일자 / the end date
     * @param fromTime 시작시각 / the start time
     * @param toTime   종료시각 / the end time
     */
    // req: FR-TLK-007, FR-TLK-008
    public record TalkWindow(LocalDate fromDate, LocalDate toDate,
                             LocalTime fromTime, LocalTime toTime) {

        /**
         * {@code TRDD} 하한을 반환한다. / Returns the {@code TRDD} lower bound.
         *
         * @return {@code YYYYMMDD}
         */
        public String fromDateYyyymmdd() {
            return fromDate.format(YYYYMMDD);
        }

        /**
         * {@code TRDD} 상한을 반환한다. / Returns the {@code TRDD} upper bound.
         *
         * @return {@code YYYYMMDD}
         */
        public String toDateYyyymmdd() {
            return toDate.format(YYYYMMDD);
        }

        /**
         * {@code RGDT} 하한을 반환한다. / Returns the {@code RGDT} lower bound.
         *
         * <p>{@code RGDT} 는 {@code YYYYMMDDHH24MISS} 문자열이므로 일자와 시각을 이어
         * 붙인다. 레거시도 같은 형태로 만들었으나, 시각 부분이 실제 시각이라는 보장이
         * 없었다.</p>
         * <p>{@code RGDT} is a {@code YYYYMMDDHH24MISS} string, so the date and time are
         * concatenated. The legacy built the same shape without any guarantee that the time part
         * was a real time.</p>
         *
         * @return {@code YYYYMMDDHHMMSS}
         */
        // req: FR-TLK-008
        public String fromTimestamp() {
            return fromDate.format(YYYYMMDD) + fromTime.format(HHMMSS);
        }

        /**
         * {@code RGDT} 상한을 반환한다. / Returns the {@code RGDT} upper bound.
         *
         * @return {@code YYYYMMDDHHMMSS}
         */
        // req: FR-TLK-008
        public String toTimestamp() {
            return toDate.format(YYYYMMDD) + toTime.format(HHMMSS);
        }

        /**
         * 하루 조회인지 반환한다. / Whether this is a single-day window.
         *
         * @return 하루면 true / true for one day
         */
        public boolean singleDay() {
            return fromDate.equals(toDate);
        }

        /**
         * 감사 기록에 남길 설명을 반환한다. / Returns a description for the audit record.
         *
         * @return 설명 / the description
         */
        // req: FR-AZ-T05
        public String describe() {
            return fromTimestamp() + "~" + toTimestamp();
        }
    }
}
