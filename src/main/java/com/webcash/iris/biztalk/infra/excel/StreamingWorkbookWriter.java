package com.webcash.iris.biztalk.infra.excel;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.function.Function;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * 한 시트를 스트리밍으로 쓰는 워크북 작성기. / A workbook writer that streams one sheet.
 *
 * <h2>왜 이 클래스가 이 슬라이스에 있는가 / why this class is in this slice</h2>
 * <p>{@code ADR-RPT-023} 이 프로그램 차원에서 SXSSF 스트리밍을 선택했으나, 그 작성기는
 * 이용기관 보고서 슬라이스의 스프린트 R2 에 계획되었고 <b>그 스프린트는 아직 실행되지
 * 않았다</b>. {@code ADR-TLK-027} §2 는 그것을 재사용한다고 적었는데 사실이 아니었고,
 * §5 에 정정을 남겼다. 따라서 이 슬라이스가 만들고, 보고서 R2 가 소비한다 — 의존 방향이
 * 뒤집힌다.</p>
 * <p>{@code ADR-RPT-023} chose SXSSF streaming programme-wide, but the writer was scheduled for the
 * 이용기관 보고서 slice's Sprint R2 and <b>that sprint has not run</b>. {@code ADR-TLK-027} §2 stated it
 * would be reused, which was untrue; §5 records the correction. So this slice builds it and 보고서 R2
 * consumes it — the dependency direction reverses.</p>
 *
 * <h2>레거시가 메모리에서 했던 것 / what the legacy did in memory</h2>
 * <p>{@code biztalk_admin_30_spreadsheet_view.jsp} 는 {@code XSSFWorkbook} 을 써서 <b>전체
 * 결과를 첫 바이트를 쓰기 전에 힙에 올렸다</b>. 그 결과 집합은 네 테이블의
 * {@code UNION ALL} 이고 행마다 {@code decrypt()} 를 두 번 호출하며, 기간 상한도 행 상한도
 * 페이징도 없었다(D-T12). OOM 의 가능성이 D-T1(모든 기관·모든 메시지)과 D-T24(기간 무제한)에
 * 정비례했다.</p>
 * <p>The legacy used {@code XSSFWorkbook}, materialising <b>the entire result on the heap before writing the
 * first byte</b> — over a four-table {@code UNION ALL} calling {@code decrypt()} twice per row, with no
 * period cap, no row cap and no pagination (D-T12). The OOM likelihood scaled directly with D-T1 (every
 * institution, every message) and D-T24 (an unbounded period).</p>
 *
 * <p>{@code SXSSFWorkbook} 은 창(window) 밖의 행을 임시 파일로 내보내므로 힙 사용량이 <b>행
 * 수와 무관</b>하다(NFR-SCALE-T01). 임시 파일은 {@link SXSSFWorkbook#dispose()} 로 반드시
 * 지운다 — 지우지 않으면 마스킹된 내용이라도 디스크에 남는다.</p>
 * <p>{@code SXSSFWorkbook} flushes rows outside its window to temporary files, so heap usage is
 * <b>independent of row count</b> (NFR-SCALE-T01). Those files must be removed via
 * {@link SXSSFWorkbook#dispose()}: leaving them behind leaves content on disk, masked or not.</p>
 *
 * // source: biztalk_admin_30_spreadsheet_view.jsp — XSSFWorkbook, no cap, no streaming
 * // req: FR-TLKX-005, FR-TLKX-009, NFR-SCALE-T01, ADR-RPT-023
 */
@Component
public class StreamingWorkbookWriter {

    /**
     * 메모리에 유지하는 행 수. / Rows kept in memory.
     *
     * <p>{@code ADR-RPT-023} 이 정한 100 을 그대로 쓴다. 이 값이 힙 사용량의 상한을 정하고,
     * 행 수는 정하지 않는다.</p>
     * <p>The 100 that {@code ADR-RPT-023} specified. This value bounds heap usage; the row count does
     * not.</p>
     */
    public static final int WINDOW_ROWS = 100;

    /**
     * 한 시트를 쓴다. / Writes one sheet.
     *
     * <p>행은 {@code List} 가 아니라 {@code Iterable} 로 받는다. 목록을 요구하면 호출자가 전체
     * 결과를 먼저 메모리에 만들어야 하고, 그러면 스트리밍 작성기를 쓰는 의미가 없어진다 —
     * 레거시의 실패가 정확히 그것이었다.</p>
     * <p>Rows arrive as an {@code Iterable} rather than a {@code List}: demanding a list would make the
     * caller materialise the whole result first, which defeats the point of a streaming writer — precisely
     * the legacy's failure.</p>
     *
     * @param <T>         행 타입 / the row type
     * @param output      쓸 대상 / the destination
     * @param sheetName   시트 이름 / the sheet name
     * @param headers     헤더 / the header labels
     * @param rows        행 / the rows
     * @param cellsOf     한 행을 셀 문자열로 변환 / maps a row to its cell strings
     * @return 실제로 쓴 행 수 / the number of rows actually written
     * @throws IOException 쓰기 실패 / on a write failure
     */
    // req: FR-TLKX-005, FR-TLKX-009, NFR-SCALE-T01
    public <T> int write(OutputStream output,
                         String sheetName,
                         List<String> headers,
                         Iterable<T> rows,
                         Function<T, List<String>> cellsOf) throws IOException {

        SXSSFWorkbook workbook = new SXSSFWorkbook(WINDOW_ROWS);
        try {
            workbook.setCompressTempFiles(true);
            Sheet sheet = workbook.createSheet(sheetName);
            CellStyle headerStyle = headerStyle(workbook);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                // 셀을 <b>한 번만</b> 만든다. 레거시는 같은 인덱스에 createCell 을 두 번 호출해
                // 첫 번째를 버렸다 — 모든 시트에서 반복되었다(D-T34).
                // Each cell is created <b>once</b>. The legacy called createCell twice at the same index,
                // discarding the first, repeated across every sheet (D-T34).
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }

            int written = 0;
            int rowIndex = 1;
            for (T row : rows) {  
                Row bodyRow = sheet.createRow(rowIndex++);
                List<String> cells = cellsOf.apply(row);
                for (int i = 0; i < cells.size(); i++) {
                    String value = cells.get(i);
                    bodyRow.createCell(i).setCellValue(value == null ? "" : value);
                }
                written++;
            }

            workbook.write(output);
            return written;

        } finally {
            // ⚠ dispose() 를 빠뜨리면 창 밖으로 밀려난 행의 임시 파일이 디스크에 남는다.
            // 마스킹된 내용이라도 남겨 둘 이유가 없다.
            // ⚠ Omitting dispose() leaves the temporary files holding flushed rows on disk. There is no
            // reason to leave that behind, masked content or not.
            workbook.dispose();
            workbook.close();
        }
    }

    private static CellStyle headerStyle(SXSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }
}
