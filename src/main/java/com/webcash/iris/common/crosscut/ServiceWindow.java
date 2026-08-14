package com.webcash.iris.common.crosscut;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

/**
 * 서비스 허용 시간대. / A service's permitted time window.
 *
 * <p>Jex 런타임이 제공했던 시간대 게이팅을 대체한다. 레거시 WSVC 정의는 서비스마다
 * {@code tmUseYn}, {@code strTm}/{@code endTm} 과 <b>평일·토요일·일요일·공휴일 각각의
 * 별도 시간대</b>를 갖고 있었으며, 이 판정은 <b>애플리케이션 코드가 아니라 런타임</b>이
 * 수행했다. Jex 를 버리면 아무것도 컴파일 실패하지 않은 채 사라진다(RISK-002).</p>
 * <p>Replaces the time-window gating the Jex runtime supplied. Each legacy WSVC definition
 * carried {@code tmUseYn}, {@code strTm}/{@code endTm} and <b>separate windows for weekdays,
 * Saturdays, Sundays and holidays</b> — and the runtime, not the application, enforced them.
 * Discarding Jex removes this with no compile error (RISK-002).</p>
 *
 * <p>문자내역 두 서비스는 현재 {@code tmUseYn=N} 으로 게이팅이 <b>비활성</b>이다. 그럼에도
 * 구현하는 이유는 biztalk 모듈의 다른 서비스에서 활성 상태이고, 교차 관심사를 나중에
 * 끼워 넣는 것이 바로 누락을 만드는 방식이기 때문이다.</p>
 * <p>Both 문자내역 services currently have {@code tmUseYn=N}, so gating is inactive. It is built
 * anyway because other services in the module use it, and retrofitting a cross-cutting control
 * is precisely how gaps are created.</p>
 *
 * @param enabled       게이팅 사용 여부 ({@code tmUseYn}) / whether gating applies
 * @param weekdayStart  평일 시작 / weekday start
 * @param weekdayEnd    평일 종료 / weekday end
 * @param saturdayStart 토요일 시작. null 이면 평일과 동일 / Saturday start; null means same as weekday
 * @param saturdayEnd   토요일 종료 / Saturday end
 * @param sundayStart   일요일 시작 / Sunday start
 * @param sundayEnd     일요일 종료 / Sunday end
 * @param holidayStart  공휴일 시작 / holiday start
 * @param holidayEnd    공휴일 종료 / holiday end
 *
 * // source: WSVC.biztalk_admin_40.xml — tmUseYn / strTm / endTm / satStrTm / sunStrTm / holStrTm
 * // req: NFR-OPS-TIME, BR-003
 */
public record ServiceWindow(
        boolean enabled,
        LocalTime weekdayStart,
        LocalTime weekdayEnd,
        LocalTime saturdayStart,
        LocalTime saturdayEnd,
        LocalTime sundayStart,
        LocalTime sundayEnd,
        LocalTime holidayStart,
        LocalTime holidayEnd
) {

    /**
     * 게이팅이 비활성인 창을 만든다. / Creates a window with gating disabled.
     *
     * <p>문자내역 두 서비스의 현재 설정({@code tmUseYn=N}, {@code 000000}–{@code 240000})에
     * 해당한다.</p>
     * <p>Matches the current configuration of both 문자내역 services.</p>
     *
     * @return 항상 허용하는 창 / a window that always permits
     */
    // source: WSVC.biztalk_admin_40.xml / _40_l001.xml — tmUseYn=N, 000000~240000
    // req: NFR-OPS-TIME
    public static ServiceWindow disabled() {
        return new ServiceWindow(false, null, null, null, null, null, null, null, null);
    }

    /**
     * 지정 시각이 허용 범위인지 판정한다. / Whether the given instant falls inside the window.
     *
     * <p>레거시 {@code endTm} 은 {@code 240000} 을 사용했다 — {@link LocalTime} 으로 표현할
     * 수 없는 값이다. 종료 시각이 {@code 00:00} 인 경우를 <b>자정</b>(= 하루의 끝)으로
     * 해석하여 이 관용구를 보존한다. 그렇지 않으면 {@code 000000~240000} 설정이
     * "폭이 0인 창"으로 읽혀 모든 요청이 거절된다.</p>
     * <p>The legacy used {@code endTm = 240000}, which {@link LocalTime} cannot represent. An end
     * of {@code 00:00} is read as <b>midnight, end of day</b> to preserve that idiom; otherwise a
     * {@code 000000~240000} configuration would read as a zero-width window and refuse everything.</p>
     *
     * @param at        판정 시각 / the instant to test
     * @param isHoliday 공휴일 여부 (외부 달력에서 판정) / whether it is a holiday, decided externally
     * @return 허용 여부 / true when permitted
     */
    // source: WSVC endTm value 240000
    // req: NFR-OPS-TIME, BR-003
    public boolean permits(LocalDateTime at, boolean isHoliday) {
        if (!enabled) {
            return true;
        }
        LocalTime start;
        LocalTime end;

        if (isHoliday && holidayStart != null) {
            start = holidayStart;
            end = holidayEnd;
        } else if (at.getDayOfWeek() == DayOfWeek.SATURDAY && saturdayStart != null) {
            start = saturdayStart;
            end = saturdayEnd;
        } else if (at.getDayOfWeek() == DayOfWeek.SUNDAY && sundayStart != null) {
            start = sundayStart;
            end = sundayEnd;
        } else {
            start = weekdayStart;
            end = weekdayEnd;
        }

        if (start == null || end == null) {
            // 요일별 창이 설정되지 않았다 — 게이팅을 켜 두고 값을 비워 두면 의도가
            // 불분명하다. 거절이 아니라 허용으로 처리하되, 설정 오류로 보아야 한다.
            // A day's window is unset. Rather than refusing, it permits — but this should be
            // treated as a configuration error, not a working state.
            return true;
        }

        // endTm=240000 관용구 / the 240000 idiom
        if (end.equals(LocalTime.MIDNIGHT)) {
            return !at.toLocalTime().isBefore(start);
        }
        LocalTime now = at.toLocalTime();
        return !now.isBefore(start) && now.isBefore(end);
    }

    /**
     * {@code HHmmss} 형식 문자열을 파싱한다. / Parses an {@code HHmmss} string.
     *
     * <p>레거시 WSVC 는 시간을 {@code 000000} / {@code 240000} 형태의 6자리 문자열로
     * 저장했다. 빈 문자열은 "미설정"을 의미한다.</p>
     * <p>The legacy stored times as six-digit strings; an empty value means unset.</p>
     *
     * @param hhmmss 6자리 시각 문자열 / a six-digit time string
     * @return 시각, 미설정이면 null / the time, or null when unset
     */
    // source: WSVC strTm/endTm format
    // req: NFR-OPS-TIME
    public static LocalTime parse(String hhmmss) {
        if (hhmmss == null || hhmmss.isBlank()) {
            return null;
        }
        String value = hhmmss.trim();
        if (value.length() != 6 || !value.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("Service window time must be HHmmss: " + hhmmss);
        }
        int hour = Integer.parseInt(value.substring(0, 2));
        int minute = Integer.parseInt(value.substring(2, 4));
        int second = Integer.parseInt(value.substring(4, 6));

        // 레거시 관용구: 240000 = 하루의 끝. LocalTime 은 24시를 표현할 수 없으므로
        // 자정으로 정규화하고, permits() 가 그것을 하루의 끝으로 해석한다.
        // The legacy idiom 240000 means end of day; normalised to midnight, which permits()
        // interprets accordingly.
        if (hour == 24 && minute == 0 && second == 0) {
            return LocalTime.MIDNIGHT;
        }
        return LocalTime.of(hour, minute, second);
    }

    /** 게이팅이 활성인 서비스 식별자 집합은 설정에서 온다. / The set of gated services comes from configuration. */
    public static final Set<String> NO_GATED_SERVICES = Set.of();
}
