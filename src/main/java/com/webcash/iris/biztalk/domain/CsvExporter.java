package com.webcash.iris.biztalk.domain;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 조회 결과 CSV 내보내기. / CSV export of search results.
 *
 * <h2>Excel 대신 CSV 를 택한 이유 / why CSV rather than Excel</h2>
 * <p>레거시는 POI 3.9 로 xls 를 생성했다({@code *_spreadsheet_view.jsp}, 화면 20·30).
 * POI 3.9 는 2012 년 버전으로 지원이 끝났고, xls 생성을 위해 의존성을 추가하면 SBOM 과
 * 취약점 스캔 범위가 함께 늘어난다. CSV 는 의존성이 없고 Excel·Numbers·구글 스프레드시트가
 * 모두 열 수 있다.</p>
 * <p>The legacy generated xls with POI 3.9 — a 2012 release, unsupported. Adding a dependency to
 * produce xls would expand the SBOM and vulnerability surface; CSV needs none and opens in every
 * spreadsheet application.</p>
 *
 * <h2>CSV 수식 주입 방어 / CSV formula injection</h2>
 * <p><b>이것이 이 클래스의 핵심이다.</b> 스프레드시트는 {@code =}, {@code +}, {@code -},
 * {@code @}, 탭·개행으로 시작하는 셀을 <b>수식으로 해석</b>한다. 발송 내역에는 외부에서
 * 입력된 메시지 본문·결과 코드가 포함되므로, 그 값이 그대로 CSV 에 들어가면 파일을 여는
 * 사람의 컴퓨터에서 수식이 실행될 수 있다 — 내보내기가 공격 경로가 된다.</p>
 * <p><b>This is the point of the class.</b> Spreadsheets interpret cells beginning with
 * {@code =}, {@code +}, {@code -}, {@code @}, tab or newline <b>as formulas</b>. Send history
 * contains externally-supplied content, so writing it verbatim would let a formula execute on the
 * machine of whoever opens the file — turning an export into an attack path.</p>
 *
 * <p>방어는 위험 문자로 시작하는 값 앞에 아포스트로피를 붙이는 것이다. 값을 <b>변경하지
 * 않고</b> 스프레드시트가 텍스트로 읽게 만든다.</p>
 * <p>The defence prefixes an apostrophe, which makes the spreadsheet read the value as text
 * <b>without altering</b> it.</p>
 *
 * <p>전화번호는 <b>이미 DB 에서 마스킹된 값</b>이 도착한다(ADR-005). 내보내기 파일에도
 * 평문은 존재할 수 없다(NFR-SEC-PII-02).</p>
 * <p>Phone numbers arrive already masked from the database (ADR-005), so no plaintext can exist
 * in the exported file either (NFR-SEC-PII-02).</p>
 *
 * // source: biztalk_admin_20_spreadsheet_view.jsp / _30_spreadsheet_view.jsp (POI 3.9, screens 20/30)
 * // req: FR-MSG-017, NFR-SEC-PII-02, AMB-07
 */
@Component
public class CsvExporter {

    /** 스프레드시트가 수식으로 해석하는 선행 문자. / Characters a spreadsheet treats as starting a formula. */
    private static final String FORMULA_TRIGGERS = "=+-@\t\r\n";

    /** 헤더 — 레거시 그리드 12컬럼과 동일한 순서. / Header, in the legacy grid's column order. */
    // source: biztalk_admin_40.js — gridColName
    // req: FR-MSG-004, FR-MSG-017
    private static final List<String> HEADERS = List.of(
            "유형", "테이블", "메시지키", "이용기관", "상태", "톡결과",
            "발송번호", "수신번호", "요청일자", "요청시간", "발송시간", "응답시간");

    /**
     * 행 목록을 CSV 문자열로 변환한다. / Renders rows as CSV.
     *
     * <p>BOM 을 앞에 붙인다. Excel 은 BOM 이 없는 UTF-8 CSV 를 시스템 코드페이지로 읽어
     * 한글이 깨진다 — 레거시가 EUC-KR 환경이었으므로 이 문제가 드러나지 않았다.</p>
     * <p>A BOM is prefixed: without it Excel reads UTF-8 CSV in the system codepage and Korean
     * text is mangled. The legacy ran in an EUC-KR environment, so this never surfaced.</p>
     *
     * @param rows 내보낼 행 / the rows to export
     * @return CSV 본문 / the CSV body
     */
    // req: FR-MSG-017
    public String toCsv(List<MessageHistoryRow> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append('﻿'); // UTF-8 BOM

        sb.append(String.join(",", HEADERS)).append("\r\n");

        for (MessageHistoryRow row : rows) {
            sb.append(cell(row.messageTypeLabel())).append(',')
              .append(cell(row.tableTypeCode())).append(',')
              .append(cell(row.messageKey() == null ? "" : row.messageKey().toString())).append(',')
              .append(cell(row.institutionCode())).append(',')
              .append(cell(row.statusLabel())).append(',')
              .append(cell(row.resultCode())).append(',')
              .append(cell(row.senderNumber())).append(',')
              .append(cell(row.recipientNumber())).append(',')
              .append(cell(row.requestDate())).append(',')
              .append(cell(row.requestTime())).append(',')
              .append(cell(row.sentTime())).append(',')
              .append(cell(row.reportTime())).append("\r\n");
        }
        return sb.toString();
    }

    /**
     * 한 셀을 CSV 로 인코딩한다. / Encodes one cell.
     *
     * <p>두 가지를 함께 처리한다: 수식 주입 방어(선행 아포스트로피)와 CSV 이스케이프
     * (쉼표·따옴표·개행이 있으면 따옴표로 감싸고 내부 따옴표를 두 번 반복).</p>
     * <p>Two things at once: formula-injection defence and CSV escaping.</p>
     *
     * @param value 원본 값 / the raw value
     * @return 인코딩된 셀 / the encoded cell
     */
    // req: FR-MSG-017, NFR-SEC-PII-02
    String cell(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String safe = value;

        // 수식 주입 방어를 <b>먼저</b> 적용한다. 이스케이프 후에 적용하면 따옴표 안쪽의
        // 선행 문자를 놓친다.
        // Applied first: doing it after escaping would miss a trigger inside the quotes.
        if (FORMULA_TRIGGERS.indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }

        if (safe.indexOf(',') >= 0 || safe.indexOf('"') >= 0
                || safe.indexOf('\n') >= 0 || safe.indexOf('\r') >= 0) {
            safe = "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    /**
     * 헤더 목록을 반환한다. 테스트와 문서화용.
     * Returns the header list, for tests and documentation.
     *
     * @return 헤더 / the headers
     */
    public List<String> headers() {
        return HEADERS;
    }
}
