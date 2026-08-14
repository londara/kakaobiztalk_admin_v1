import java.time.LocalDateTime;
import java.util.List;

/**
 * 문자내역 도메인 로직 실행 검증 드라이버.
 * Execution driver for the 문자내역 domain logic.
 *
 * req: FR-MSG-005/006/007/012/013/015, FR-MSGD-003, FR-TEN-001/002/003
 * 용도: Maven 부재 상태에서 JDK 만으로 실제 실행 검증 (qa/verify-without-maven.sh)
 */
public class MessageHistoryDriver {

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

    static LocalDateTime dt(String s) {
        return LocalDateTime.parse(s);
    }

    static List<String> violationsOf(Runnable r) {
        try {
            r.run();
            return List.of();
        } catch (MessageHistoryCriteria.CriteriaException e) {
            return e.violations();
        }
    }

    public static void main(String[] args) {
        // ---- D8 회귀: 시각만이 아니라 날짜+시각을 비교한다 ----
        // 레거시: if (Number(sTime) > Number(eTime)) alert(...) — 다일 범위를 거절했다
        List<String> multiDay = violationsOf(() -> MessageHistoryCriteria.builder()
                .from(dt("2026-01-01T18:00:00")).to(dt("2026-01-05T09:00:00")).build());
        check("D8: multi-day range with later start-time ACCEPTED", multiDay.isEmpty());

        List<String> reversed = violationsOf(() -> MessageHistoryCriteria.builder()
                .from(dt("2026-01-05T09:00:00")).to(dt("2026-01-01T18:00:00")).build());
        check("D8: genuinely reversed range rejected", !reversed.isEmpty());

        List<String> zeroWidth = violationsOf(() -> MessageHistoryCriteria.builder()
                .from(dt("2026-01-01T09:00:00")).to(dt("2026-01-01T09:00:00")).build());
        check("zero-width range rejected", !zeroWidth.isEmpty());

        // ---- FR-MSG-013: 31일 상한 (레거시에는 상한이 없었다) ----
        List<String> exactly31 = violationsOf(() -> MessageHistoryCriteria.builder()
                .from(dt("2026-01-01T00:00:00")).to(dt("2026-02-01T00:00:00")).build());
        check("31 days exactly ACCEPTED", exactly31.isEmpty());

        List<String> over31 = violationsOf(() -> MessageHistoryCriteria.builder()
                .from(dt("2026-01-01T00:00:00")).to(dt("2026-02-01T00:00:01")).build());
        check("31 days + 1 second rejected", !over31.isEmpty());
        check("cap message names the limit", over31.stream().anyMatch(v -> v.contains("31")));

        // ---- 필수 범위 ----
        check("null from rejected", !violationsOf(() -> MessageHistoryCriteria.builder()
                .to(dt("2026-01-02T00:00:00")).build()).isEmpty());
        check("null to rejected", !violationsOf(() -> MessageHistoryCriteria.builder()
                .from(dt("2026-01-01T00:00:00")).build()).isEmpty());

        // ---- FR-MSG-015: 빈 선택 조건은 null(조건 없음)로 정규화 ----
        MessageHistoryCriteria c = MessageHistoryCriteria.builder()
                .from(dt("2026-01-01T00:00:00")).to(dt("2026-01-02T00:00:00"))
                .senderNumber("  ").recipientNumber("").statusCode(null).resultCode("   ")
                .institutionCode("").build();
        check("blank sender -> null", c.senderNumber() == null);
        check("empty recipient -> null", c.recipientNumber() == null);
        check("blank result -> null", c.resultCode() == null);
        check("empty institution -> null", c.institutionCode() == null);

        MessageHistoryCriteria trimmed = MessageHistoryCriteria.builder()
                .from(dt("2026-01-01T00:00:00")).to(dt("2026-01-02T00:00:00"))
                .senderNumber("  15883987  ").build();
        check("value trimmed", "15883987".equals(trimmed.senderNumber()));

        // ---- FR-MSG-007 / NFR-PERF-02: 페이지 크기 보정 ----
        MessageHistoryCriteria def = base().build();
        check("default size 50", def.size() == 50);
        check("size clamped to 500", base().size(10000).build().size() == 500);
        check("size 0 -> default", base().size(0).build().size() == 50);
        check("negative page -> 0", base().page(-5).build().page() == 0);
        check("offset = page*size", base().page(3).size(25).build().offset() == 75);

        // ---- FR-MSGD-003: 미인식 유형은 거절 (레거시는 else 로 KKF 조회) ----
        check("AT resolves", MessageType.fromCode("AT").isPresent());
        check("FT resolves", MessageType.fromCode("FT").isPresent());
        check("XX rejected", MessageType.fromCode("XX").isEmpty());
        check("null type rejected", MessageType.fromCode(null).isEmpty());
        check("AT -> KKO prefix", "KKO".equals(MessageType.fromCode("AT").get().tablePrefix()));
        check("FT -> KKF prefix", "KKF".equals(MessageType.fromCode("FT").get().tablePrefix()));
        check("SMS resolves", TableType.fromCode("SMS").isPresent());
        check("MMS resolves", TableType.fromCode("MMS").isPresent());
        check("bad table type rejected", TableType.fromCode("XLS").isEmpty());

        // ---- FR-MSG-005: 상태 라벨, 그리고 미매핑 값의 처리 ----
        check("status 1", "미전송".equals(MessageStatus.labelOrRaw("1")));
        check("status 2", "전송완료".equals(MessageStatus.labelOrRaw("2")));
        check("status 3", "톡결과수신".equals(MessageStatus.labelOrRaw("3")));
        check("status 4", "문자결과수신".equals(MessageStatus.labelOrRaw("4")));
        check("status 6", "큐입력".equals(MessageStatus.labelOrRaw("6")));
        // 레거시 그리드는 여기서 빈 칸을 표시했다 (AMB-05)
        check("status 5 shown verbatim, not blank", "5".equals(MessageStatus.labelOrRaw("5")));
        check("status null -> empty string", "".equals(MessageStatus.labelOrRaw(null)));

        // ---- FR-TEN-001/002/003: 테넌트 범위 ----
        TenantContext.TenantPrincipal tenant =
                new TenantContext.TenantPrincipal("u@x.com", "IS001", false);
        check("tenant ignores requested code", "IS001".equals(tenant.effectiveInstitutionCode("IS999")));
        check("tenant ignores blank", "IS001".equals(tenant.effectiveInstitutionCode("")));
        check("tenant ignores null", "IS001".equals(tenant.effectiveInstitutionCode(null)));

        TenantContext.TenantPrincipal op =
                new TenantContext.TenantPrincipal("op@x.com", null, true);
        check("operator may select", "IS999".equals(op.effectiveInstitutionCode("IS999")));
        check("operator blank = all", op.effectiveInstitutionCode("") == null);
        check("operator null = all", op.effectiveInstitutionCode(null) == null);

        boolean threw = false;
        try {
            TenantContext.require();
        } catch (IllegalStateException e) {
            threw = true;
        }
        check("unbound context throws (fails closed)", threw);
        TenantContext.set(tenant);
        check("bound context returns", "IS001".equals(TenantContext.require().institutionCode()));
        TenantContext.clear();
        check("cleared context not bound", !TenantContext.isBound());

        // ---- FR-MSG-007: 페이징 산술 ----
        PagedResult<String> p = new PagedResult<>(List.of("a", "b"), 105, 0, 50);
        check("totalPages 105/50 = 3", p.totalPages() == 3);
        check("hasNext on page 0", p.hasNext());
        check("no next on last page", !new PagedResult<>(List.of("a"), 105, 2, 50).hasNext());
        PagedResult<String> exact = new PagedResult<>(List.of(), 100, 0, 50);
        check("totalPages 100/50 = 2", exact.totalPages() == 2);
        check("empty result flagged", exact.isEmpty());

        System.out.println();
        System.out.println("=== pass=" + pass + " fail=" + fail + " ===");
        if (fail > 0) {
            System.exit(1);
        }
    }

    static MessageHistoryCriteria.Builder base() {
        return MessageHistoryCriteria.builder()
                .from(dt("2026-01-01T00:00:00")).to(dt("2026-01-02T00:00:00"));
    }
}
