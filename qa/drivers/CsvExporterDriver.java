import java.util.List;

/**
 * {@link CsvExporter} 실행 검증 드라이버. / Execution driver for CsvExporter.
 *
 * req: FR-MSG-017, NFR-SEC-PII-02
 * source: biztalk_admin_20_spreadsheet_view.jsp (POI 3.9)
 *
 * <p>핵심 검증은 <b>CSV 수식 주입</b>이다. 발송 내역에는 외부에서 들어온 값이 포함되므로,
 * {@code =}·{@code +}·{@code -}·{@code @} 로 시작하는 셀이 그대로 기록되면 파일을 여는
 * 사람의 스프레드시트에서 수식이 실행된다.</p>
 */
public class CsvExporterDriver {

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

    static CsvExporter ex = new CsvExporter();

    static MessageHistoryRow row(String sender, String recipient, String result) {
        return new MessageHistoryRow("0001", 12345L, "3", result,
                sender, recipient, "20260814093000", "093000", "093001", "093002",
                "AT", "K");
    }

    public static void main(String[] args) {
        // ---- 수식 주입 방어 / formula injection ----
        // 스프레드시트가 수식으로 해석하는 다섯 가지 선행 문자를 전부 확인한다.
        check("= prefixed", ex.cell("=1+1").equals("'=1+1"));
        check("+ prefixed", ex.cell("+1").equals("'+1"));
        check("- prefixed", ex.cell("-1").equals("'-1"));
        check("@ prefixed", ex.cell("@SUM(A1)").equals("'@SUM(A1)"));
        check("tab prefixed", ex.cell("\tx").startsWith("'"));

        // 실제 공격 페이로드 — 명령 실행을 시도하는 DDE 형태
        String payload = "=cmd|'/c calc'!A1";
        String encoded = ex.cell(payload);
        check("DDE payload neutralised", encoded.startsWith("'="));
        // 값 자체는 보존되어야 한다 — 마스킹이 아니라 무해화다
        check("DDE payload value preserved", encoded.contains("cmd|'/c calc'!A1"));

        // ---- 정상 값은 변형되지 않는다 / benign values untouched ----
        check("plain text unchanged", ex.cell("전송완료").equals("전송완료"));
        check("masked phone unchanged", ex.cell("010****5678").equals("010****5678"));
        check("digits unchanged", ex.cell("20260814").equals("20260814"));
        check("null -> empty", ex.cell(null).isEmpty());
        check("empty -> empty", ex.cell("").isEmpty());

        // ---- CSV 이스케이프 / CSV escaping ----
        check("comma quoted", ex.cell("a,b").equals("\"a,b\""));
        check("quote doubled", ex.cell("say \"hi\"").equals("\"say \"\"hi\"\"\""));
        check("newline quoted", ex.cell("a\nb").equals("\"a\nb\""));

        // 순서가 중요하다: 수식 방어가 먼저, 이스케이프가 나중.
        // "=a,b" 는 아포스트로피가 붙은 뒤 쉼표 때문에 따옴표로 감싸져야 한다.
        // Order matters: the apostrophe is applied before quoting.
        check("formula + comma both handled", ex.cell("=a,b").equals("\"'=a,b\""));

        // 개행으로 시작하는 값은 두 규칙이 함께 적용된다
        check("newline-leading gets both", ex.cell("\na").equals("\"'\na\""));

        // ---- 전체 문서 / whole document ----
        String csv = ex.toCsv(List.of(
                row("010****1111", "010****2222", "1000"),
                row("=HYPERLINK(\"http://evil\")", "010****4444", null)));

        check("has BOM", csv.charAt(0) == '﻿');
        check("has 12 headers", ex.headers().size() == 12);
        check("header line present", csv.contains("유형,테이블,메시지키"));
        check("CRLF line endings", csv.contains("\r\n"));
        // 헤더 1줄 + 데이터 2줄 = 3개의 CRLF
        check("three CRLF (header + 2 rows)", csv.split("\r\n", -1).length == 4);
        // 상태코드 "3" 은 톡결과수신이다. "전송완료"(코드 2)로 단정했던 최초 어서션이
        // 실패하여 코드가 아니라 어서션을 고쳤다 — 드라이버가 제 역할을 한 사례.
        // Code "3" is 톡결과수신; the first assertion wrongly expected 전송완료 (code 2).
        check("status label resolved", csv.contains("톡결과수신"));
        check("type label resolved", csv.contains("알림톡"));
        check("masked phone present", csv.contains("010****1111"));
        // 두 번째 행의 공격 페이로드가 무해화된 상태로 존재해야 한다
        check("row payload neutralised in document", csv.contains("'=HYPERLINK"));
        check("no raw leading = in any cell", !csv.contains(",=HYPERLINK"));
        // null 결과코드는 빈 칸
        check("null cell renders empty", csv.contains(",,"));

        // ---- 빈 결과 / empty result ----
        String empty = ex.toCsv(List.of());
        check("empty export still has header", empty.contains("유형"));
        check("empty export has one line", empty.split("\r\n", -1).length == 2);

        System.out.println();
        System.out.println("=== pass=" + pass + " fail=" + fail + " ===");
        if (fail > 0) {
            System.exit(1);
        }
    }
}
