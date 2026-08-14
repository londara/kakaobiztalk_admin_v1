import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * {@link ServiceWindow} 실행 검증 드라이버. / Execution driver for ServiceWindow.
 *
 * req: NFR-OPS-TIME, BR-003
 * source: WSVC.biztalk_admin_40.xml — tmUseYn / strTm=000000 / endTm=240000 / satStrTm / sunStrTm / holStrTm
 *
 * <p>가장 중요한 검증은 {@code endTm=240000} 관용구다. LocalTime 은 24시를 표현할 수 없어
 * 자정으로 정규화되는데, 이를 "폭 0인 창"으로 읽으면 {@code 000000~240000} 설정이 모든
 * 요청을 거절한다 — 레거시 설정 전체가 그 형태다.</p>
 */
public class ServiceWindowDriver {

    static int pass = 0;
    static int fail = 0;

    static void check(String name, boolean ok) {
        if (ok) {
            pass++;
            System.out.println("PASS " + name);
        } else {
            fail++;
            System.out.println("FAIL " + name);
        }
    }

    static LocalTime t(String hhmmss) {
        return ServiceWindow.parse(hhmmss);
    }

    /** 2026-08-14 는 금요일 / a Friday. */
    static LocalDateTime friday(int hour, int minute) {
        return LocalDateTime.of(2026, 8, 14, hour, minute);
    }

    /** 2026-08-15 는 토요일 / a Saturday. */
    static LocalDateTime saturday(int hour, int minute) {
        return LocalDateTime.of(2026, 8, 15, hour, minute);
    }

    /** 2026-08-16 는 일요일 / a Sunday. */
    static LocalDateTime sunday(int hour, int minute) {
        return LocalDateTime.of(2026, 8, 16, hour, minute);
    }

    public static void main(String[] args) {
        // ---- 파싱 / parsing ----
        check("parse 000000 = midnight", t("000000").equals(LocalTime.MIDNIGHT));
        check("parse 090000 = 09:00", t("090000").equals(LocalTime.of(9, 0)));
        check("parse 235959", t("235959").equals(LocalTime.of(23, 59, 59)));
        // 레거시 관용구 — LocalTime 은 24시를 표현할 수 없다
        check("parse 240000 -> midnight (legacy idiom)", t("240000").equals(LocalTime.MIDNIGHT));
        check("parse blank -> null", t("") == null);
        check("parse null -> null", t(null) == null);

        boolean threw = false;
        try {
            t("9999");
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check("malformed length rejected", threw);

        threw = false;
        try {
            t("ab0000");
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check("non-numeric rejected", threw);

        // ---- 게이팅 비활성 (문자내역의 현재 설정) ----
        ServiceWindow off = ServiceWindow.disabled();
        check("disabled permits any time", off.permits(friday(3, 0), false));
        check("disabled permits holiday", off.permits(friday(3, 0), true));

        // ---- 레거시 실제 설정: 000000 ~ 240000, 게이팅 켜짐 ----
        // 이 케이스가 핵심이다. 240000 을 자정으로 정규화한 뒤 "폭 0"으로 읽으면
        // 하루 종일 허용이어야 할 설정이 전부 거절로 뒤집힌다.
        ServiceWindow allDay = new ServiceWindow(true,
                t("000000"), t("240000"), null, null, null, null, null, null);
        check("000000~240000 permits 00:00", allDay.permits(friday(0, 0), false));
        check("000000~240000 permits 12:00", allDay.permits(friday(12, 0), false));
        check("000000~240000 permits 23:59", allDay.permits(friday(23, 59), false));

        // ---- 평일 업무시간 09:00 ~ 18:00 ----
        ServiceWindow business = new ServiceWindow(true,
                t("090000"), t("180000"), null, null, null, null, null, null);
        check("business rejects 08:59", !business.permits(friday(8, 59), false));
        check("business permits 09:00 (inclusive start)", business.permits(friday(9, 0), false));
        check("business permits 17:59", business.permits(friday(17, 59), false));
        check("business rejects 18:00 (exclusive end)", !business.permits(friday(18, 0), false));

        // ---- 요일별 창 / per-day windows ----
        ServiceWindow perDay = new ServiceWindow(true,
                t("090000"), t("180000"),   // weekday
                t("090000"), t("130000"),   // saturday
                null, null,                  // sunday unset -> falls back to weekday
                t("100000"), t("120000"));  // holiday
        check("saturday 12:00 permitted", perDay.permits(saturday(12, 0), false));
        check("saturday 14:00 rejected (weekday would allow)", !perDay.permits(saturday(14, 0), false));
        check("weekday 14:00 permitted", perDay.permits(friday(14, 0), false));
        // 일요일 창이 미설정이면 평일 창으로 대체된다
        check("sunday unset falls back to weekday", perDay.permits(sunday(14, 0), false));
        // 공휴일이 요일보다 우선한다
        check("holiday 11:00 permitted", perDay.permits(friday(11, 0), true));
        check("holiday 14:00 rejected even on a weekday", !perDay.permits(friday(14, 0), true));
        // 토요일이 공휴일이면 공휴일 창이 우선한다
        check("holiday overrides saturday", !perDay.permits(saturday(12, 30), true));

        // ---- 설정 누락 시 허용 (거절이 아니라) ----
        ServiceWindow incomplete = new ServiceWindow(true,
                null, null, null, null, null, null, null, null);
        check("enabled but unset permits (config error, not outage)",
                incomplete.permits(friday(3, 0), false));

        System.out.println();
        System.out.println("=== pass=" + pass + " fail=" + fail + " ===");
        if (fail > 0) {
            System.exit(1);
        }
    }
}
