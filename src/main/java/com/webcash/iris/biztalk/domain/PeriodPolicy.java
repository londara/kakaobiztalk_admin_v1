package com.webcash.iris.biztalk.domain;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * 조회 기간 규칙 — 서버에서만 강제된다. / The query-period rules, enforced server-side only.
 *
 * <h2>레거시가 검증한 것 / what the legacy validated</h2>
 * <p>브라우저의 {@code Number(startDt) > Number(endDt)} 한 줄이 전부였다. 서비스 계약은
 * {@code START_DT}/{@code END_DT} 를 <b>길이도 타입도 없이</b> 선언했고, 액션 JSP 는 아무
 * 검증도 하지 않았다. {@code START_DT=00000000&END_DT=99999999} 한 번이면 두 테이블을
 * 끝에서 끝까지 훑었다(D-R9).</p>
 * <p>One line of browser JavaScript. The service contract declared the two dates with
 * <b>neither length nor type</b>, and the action JSP validated nothing, so a single request
 * carrying {@code START_DT=00000000&END_DT=99999999} scanned both tables end to end (D-R9).</p>
 *
 * <h2>366 일인 이유 / why 366 days</h2>
 * <p>문자내역 슬라이스는 31 일로 정해졌다(AMB-06). 여기가 더 긴 것은 <b>행의 단위가
 * 다르기</b> 때문이다 — 저기는 메시지 1건이 1행이고, 여기는 일자 × 기관이 1행이다. 366 일은
 * 윤년을 포함한 1년 보고서를 한 번에 뽑기 위한 값이며, PM 결정 AMB-R03 이다.</p>
 * <p>The 문자내역 slice was capped at 31 days (AMB-06). This is longer because <b>the row grain
 * differs</b>: there one message is one row, here one 일자 × 기관 is one row. 366 covers a
 * full year including a leap day, per PM ruling AMB-R03.</p>
 *
 * // source: biztalk_admin_20.js — getDat(); WSVC.biztalk_admin_20_l001.xml
 * // req: FR-RPT-002, FR-RPT-003, FR-RPT-004, AMB-R03
 */
public final class PeriodPolicy {

    /** 허용 최대 기간(일). PM 결정 AMB-R03. / Maximum span in days, per PM ruling AMB-R03. */
    public static final int MAX_SPAN_DAYS = 366;

    /** 레거시가 쓰던 일자 형식. / The date format the legacy used. */
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

    private PeriodPolicy() {
    }

    /**
     * 요청 일자 두 개를 검증하고 정규화한다.
     * Validates and normalises the requested date pair.
     *
     * @param rawFrom 시작일자 {@code YYYYMMDD} / the start date
     * @param rawTo   종료일자 {@code YYYYMMDD} / the end date
     * @return 검증된 기간 / the validated period
     * @throws InvalidPeriodException 형식·순서·기간 위반 / on a malformed, inverted or over-long period
     */
    // req: FR-RPT-002, FR-RPT-003, FR-RPT-004
    public static ReportPeriod validate(String rawFrom, String rawTo) {
        return validate(rawFrom, rawTo, MAX_SPAN_DAYS);
    }

    /**
     * 상한을 지정하여 요청 일자 두 개를 검증하고 정규화한다.
     * Validates and normalises the requested date pair against an explicit cap.
     *
     * <h2>상한이 인자인 이유 / why the cap is a parameter</h2>
     * <p>상한은 <b>행의 단위</b>에 따라 다르다. 이용기관 보고서는 일자 × 기관이 1행이므로
     * 366일이고(AMB-R03), 문자내역과 톡전송 내역은 메시지·거래 1건이 1행이므로 31일이다
     * (AMB-06, AMB-T02). 다른 것은 그 숫자 하나뿐이며, 형식·순서·달력 검증은 같다 —
     * 그래서 클래스를 복제하지 않고 상한만 넘긴다(ADR-TLK-027).</p>
     * <p>The cap depends on <b>row grain</b>: the institution report is one 일자 × 기관 per row, so
     * 366 days (AMB-R03); 문자내역 and 톡전송 내역 are one message or transaction per row, so 31
     * (AMB-06, AMB-T02). Only that number differs — format, ordering and calendar validity are
     * identical — so the cap is passed rather than the class duplicated (ADR-TLK-027).</p>
     *
     * @param rawFrom     시작일자 {@code YYYYMMDD} / the start date
     * @param rawTo       종료일자 {@code YYYYMMDD} / the end date
     * @param maxSpanDays 양 끝을 포함한 허용 최대 일수 / the inclusive maximum span in days
     * @return 검증된 기간 / the validated period
     * @throws InvalidPeriodException 형식·순서·기간 위반 / on a malformed, inverted or over-long period
     */
    // req: FR-RPT-002, FR-RPT-003, FR-RPT-004, FR-TLK-007, ADR-TLK-027
    public static ReportPeriod validate(String rawFrom, String rawTo, int maxSpanDays) {
        LocalDate from = parse(rawFrom, "시작일자");
        LocalDate to = parse(rawTo, "종료일자");

        if (from.isAfter(to)) {
            throw new InvalidPeriodException(
                    "시작일자가 종료일자보다 늦습니다. / The start date is after the end date.");
        }

        // 양 끝을 포함하므로 +1. 366 일 조회는 허용, 367 일은 거부한다.
        // Inclusive of both ends, hence +1: 366 days is allowed, 367 refused.
        long span = ChronoUnit.DAYS.between(from, to) + 1;
        if (span > maxSpanDays) {
            throw new InvalidPeriodException(
                    "조회 기간은 최대 " + maxSpanDays + "일입니다 (요청: " + span + "일). / "
                            + "The query period is capped at " + maxSpanDays
                            + " days; " + span + " were requested.");
        }

        return new ReportPeriod(from, to);
    }

    /**
     * {@code YYYYMMDD} 문자열을 달력 일자로 해석한다.
     * Parses a {@code YYYYMMDD} string as a calendar date.
     *
     * <p>길이만 보지 않고 <b>실재하는 날짜인지</b>까지 확인한다. {@code 20261332} 는 8자리
     * 숫자지만 존재하지 않는 날짜이며, 레거시는 이것을 그대로 바인딩했다.</p>
     * <p>Checks that the value is <b>a real date</b>, not merely eight digits: {@code 20261332}
     * is eight digits and not a date, and the legacy bound it verbatim.</p>
     */
    private static LocalDate parse(String raw, String fieldLabel) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidPeriodException(
                    fieldLabel + "는 필수입니다. / " + fieldLabel + " is required.");
        }
        String trimmed = raw.trim();
        if (trimmed.length() != 8) {
            throw new InvalidPeriodException(
                    fieldLabel + "는 YYYYMMDD 8자리여야 합니다. / "
                            + fieldLabel + " must be 8 digits in YYYYMMDD form.");
        }
        try {
            return LocalDate.parse(trimmed, YYYYMMDD);
        } catch (DateTimeParseException e) {
            throw new InvalidPeriodException(
                    fieldLabel + "가 유효한 날짜가 아닙니다: " + trimmed + " / "
                            + fieldLabel + " is not a valid calendar date: " + trimmed);
        }
    }

    /**
     * 검증을 통과한 조회 기간. / A query period that has passed validation.
     *
     * <p>이 타입이 존재하는 이유는 <b>검증되지 않은 일자가 매퍼에 도달할 수 없게</b> 하기
     * 위해서다. 매퍼는 문자열 두 개가 아니라 이 타입을 받는다.</p>
     * <p>This type exists so that <b>an unvalidated date cannot reach a mapper</b>: mappers take
     * this rather than two strings.</p>
     *
     * @param from 시작일자 / the start date
     * @param to   종료일자 / the end date
     */
    // req: FR-RPT-002, FR-RPT-004
    public record ReportPeriod(LocalDate from, LocalDate to) {

        /**
         * 매퍼 바인딩용 시작일자를 반환한다. / Returns the start date for mapper binding.
         *
         * @return {@code YYYYMMDD}
         */
        public String fromYyyymmdd() {
            return from.format(YYYYMMDD);
        }

        /**
         * 매퍼 바인딩용 종료일자를 반환한다. / Returns the end date for mapper binding.
         *
         * @return {@code YYYYMMDD}
         */
        public String toYyyymmdd() {
            return to.format(YYYYMMDD);
        }

        /**
         * 양 끝을 포함한 일수를 반환한다. / Returns the inclusive span in days.
         *
         * @return 일수 / the number of days
         */
        public long spanDays() {
            return ChronoUnit.DAYS.between(from, to) + 1;
        }
    }

    /**
     * 조회 기간이 규칙을 위반할 때 던진다. / Thrown when the period violates the rules.
     *
     * <p>비검사 예외이며 {@code GlobalExceptionHandler} 가 400 으로 변환한다. 레거시처럼
     * 조용히 통과시키면 그 요청은 곧바로 전체 테이블 스캔이 된다.</p>
     * <p>Unchecked; the global handler renders it as 400. Letting it pass silently, as the legacy
     * did, turns the request into a full table scan.</p>
     */
    // req: FR-RPT-002, FR-RPT-003, FR-RPT-004
    public static class InvalidPeriodException extends IllegalArgumentException {

        /**
         * 예외를 생성한다. / Creates the exception.
         *
         * @param message 사용자에게 보일 수 있는 설명 / a user-safe explanation
         */
        public InvalidPeriodException(String message) {
            super(message);
        }
    }
}
