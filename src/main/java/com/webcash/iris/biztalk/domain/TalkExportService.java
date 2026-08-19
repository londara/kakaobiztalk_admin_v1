package com.webcash.iris.biztalk.domain;

import com.webcash.iris.biztalk.infra.db.TalkHistoryMapper;
import com.webcash.iris.biztalk.infra.excel.StreamingWorkbookWriter;
import com.webcash.iris.common.audit.AuditEvent;
import com.webcash.iris.common.audit.AuditService;
import com.webcash.iris.common.tenant.TenantContext;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 톡전송 내역 내보내기 — 화면의 질의 경로를 그대로 쓴다.
 * The 톡전송 내역 export, running the screen's own query path.
 *
 * <h2>D-T1 — 이 슬라이스에서 가장 심각한 결함 / the slice's most serious defect</h2>
 * <p>레거시 다운로드 버튼은 화면을 내보내지 않았다. {@code biztalk_admin_30_spreadsheet} 는
 * {@code IDO.KKB_MSG_L001} 을 실행하는데, 그것은 그리드의 {@code FT_APITR_HSTR} 가 아니라
 * {@code KKO_MSG}/{@code KKF_MSG} 와 각 보관본을 조회한다 — <b>다른 테이블 집합, 다른 단위,
 * 공통 키 없음</b>.</p>
 * <p>The legacy download did not export the screen. {@code biztalk_admin_30_spreadsheet} runs
 * {@code IDO.KKB_MSG_L001}, which queries {@code KKO_MSG}/{@code KKF_MSG} and their archives rather than the
 * grid's {@code FT_APITR_HSTR} — <b>a different table set, a different grain, no key in common</b>.</p>
 *
 * <p>더 나쁜 것은 필터다. {@code fn_makeExcel()} 은 {@code #IS_LIST}, {@code #MSGKEY},
 * {@code #PHONE}, {@code #CALLBACK}, {@code #RSLT}, {@code #STATUS}, {@code #MSG_TYPE} 에서
 * 값을 읽는데 <b>그중 어느 것도 {@code biztalk_admin_30_view.jsp} 에 존재하지 않는다</b>.
 * 전부 빈 문자열이 되고, 질의의 모든 {@code CASE WHEN :X = '' THEN 1=1} 분기가 열리고,
 * 도착하는 파일은 <b>그 시간 창의 모든 이용기관의 모든 알림톡·친구톡 메시지</b>가
 * {@code decrypt(CALLBACK)} 와 {@code decrypt(PHONE)} 로, 마스킹 없이 담긴 것이다.
 * 사전 CVSS 8.6(TM-T02).</p>
 * <p>The filters are worse. {@code fn_makeExcel()} reads from {@code #IS_LIST}, {@code #MSGKEY},
 * {@code #PHONE}, {@code #CALLBACK}, {@code #RSLT}, {@code #STATUS} and {@code #MSG_TYPE} — <b>none of which
 * exist in {@code biztalk_admin_30_view.jsp}</b>. All resolve to the empty string, every
 * {@code CASE WHEN :X = '' THEN 1=1} branch opens, and the arriving file holds <b>every 알림톡 and 친구톡
 * message of every institution in the window</b> with {@code decrypt(CALLBACK)} and {@code decrypt(PHONE)},
 * unmasked. CVSS 8.6 pre-fix (TM-T02).</p>
 *
 * <h2>이 클래스가 그것을 구조적으로 불가능하게 만드는 방법 / how this makes that structurally impossible</h2>
 * <p><b>내보내기는 목록과 같은 {@link TalkHistoryCriteria} 를 받고 같은 매퍼 메서드를
 * 호출한다.</b> 두 경로가 갈라지려면 타입이나 매퍼를 바꿔야 하며, 그래서 FR-TLKX-001 이
 * "설명"이 아니라 <b>집합 동일성</b>으로 검증될 수 있다 — 같은 조건으로 내보낸 행 집합이 같은
 * 조건으로 조회한 행 집합과 같아야 한다.</p>
 * <p><b>The export takes the same {@link TalkHistoryCriteria} as the list and calls the same mapper
 * method.</b> Diverging would require changing the type or the mapper, which is what makes FR-TLKX-001
 * verifiable as a <b>set equality</b> rather than as prose: the rows exported for a set of filters must equal
 * the rows listed for those filters.</p>
 *
 * // source: biztalk_admin_30.js — fn_makeExcel(); biztalk_admin_30_spreadsheet_act.jsp; IDO.KKB_MSG_L001
 * // req: FR-TLKX-001, FR-TLKX-005, FR-TLKX-007, FR-TLKX-008, NFR-SCALE-T01
 */
@Service
public class TalkExportService {

    /**
     * 내보내기 행 상한. / The export row ceiling.
     *
     * <p>{@code ADR-RPT-023} 의 잠정값 100,000 을 쓴다. 이 슬라이스의 행은 9개 컬럼으로 그쪽보다
     * 좁으므로 상한은 보수적이다. 최종값은 NFR-PERF-T01 부하 시험이 G3 전에 확정한다
     * (RISK-T09).</p>
     * <p>{@code ADR-RPT-023}'s provisional 100,000. This slice's rows are narrower — nine columns — so the
     * ceiling is conservative. The final figure is fixed by the NFR-PERF-T01 load test before G3
     * (RISK-T09).</p>
     */
    public static final int ROW_CEILING = 100_000;

    /** 한 번에 읽어 오는 페이지 크기. / The page size read at a time. */
    private static final int FETCH_PAGE = 1_000;

    /** 시트 이름. / The sheet name. */
    static final String SHEET_NAME = "톡전송내역";

    /**
     * 헤더 — 화면 컬럼과 같은 순서. / The headers, in the screen's column order.
     *
     * <p>화면과 파일이 <b>같은 목록</b>을 쓴다. 레거시는 화면 그리드와 엑셀 헤더를 각자
     * 정의했고, 애초에 다른 테이블을 조회했으므로 컬럼이 일치할 수가 없었다.</p>
     * <p>The screen and the file use <b>one list</b>. The legacy defined the grid and the Excel header
     * separately, and since it queried a different table the columns could not have matched anyway.</p>
     */
    // source: biztalk_admin_30.js — drawGrid() colDefs
    // req: FR-TLKX-001
    static final List<String> HEADERS = List.of(
            "일자", "기관코드", "기관명", "거래고유번호", "API",
            "상태", "응답코드", "등록시각", "완료시각");

    private final TalkHistoryMapper mapper;
    private final BizTalkApiRegistry registry;
    private final StreamingWorkbookWriter writer;
    private final AuditService audit;

    /**
     * 서비스를 생성한다. / Creates the service.
     *
     * @param mapper   거래내역 매퍼 — 목록과 <b>같은</b> 매퍼 / the history mapper, the <b>same</b> one the list uses
     * @param registry BizTalk API 레지스트리 / the BizTalk API registry
     * @param writer   스트리밍 작성기 / the streaming writer
     * @param audit    감사 서비스 / the audit service
     */
    public TalkExportService(TalkHistoryMapper mapper,
                            BizTalkApiRegistry registry,
                            StreamingWorkbookWriter writer,
                            AuditService audit) {
        this.mapper = mapper;
        this.registry = registry;
        this.writer = writer;
        this.audit = audit;
    }

    /**
     * 조건에 맞는 행을 워크북으로 내보낸다. / Exports the matching rows as a workbook.
     *
     * @param criteria 목록이 만든 것과 <b>같은 타입</b>의 조건 / criteria of the <b>same type</b> the list builds
     * @param output   쓸 대상 / the destination
     * @return 실제로 쓴 행 수 / the number of rows actually written
     * @throws RowCeilingExceededException 상한 초과 / when the ceiling is exceeded
     * @throws IOException                 쓰기 실패 / on a write failure
     */
    // req: FR-TLKX-001, FR-TLKX-005, FR-TLKX-007, FR-TLKX-008
    @Transactional(readOnly = true)
    public int export(TalkHistoryCriteria criteria, OutputStream output) throws IOException {

        TenantContext.TenantPrincipal principal = TenantContext.require();

        int total = mapper.countAll(criteria);
        if (total > ROW_CEILING) {
            // 잘라내지 않는다. 조용히 짧은 파일은 내보내기 버전의 silent success 이며, 이
            // 프로그램이 여섯 슬라이스 연속으로 만난 실패 양식이다. 거부하고 맞는 범위를 알린다.
            // Never truncate. A quietly short file is the export's version of a silent success — the failure
            // mode this programme has met in six consecutive slices. Refuse, and name a range that fits.
            audit.recordAuth(principal.email(), AuditEvent.ACTION_TALK_HISTORY_EXPORT,
                    AuditEvent.Outcome.DENIED,
                    criteria.describe() + " refused total=" + total + " ceiling=" + ROW_CEILING,
                    null, null);
            throw new RowCeilingExceededException(total, ROW_CEILING);
        }

        try {
            int written = writer.write(output, SHEET_NAME, HEADERS,
                    new PagedRowIterable(criteria), TalkExportService::cellsOf);

            // 실제로 쓴 행 수를 기록한다. 누군가 화면을 열었다는 사실과 십만 행을 가져갔다는
            // 사실은 감사 관점에서 다른 사건이다(FR-TLKX-007).
            // The row count actually written is recorded: someone opening the screen and someone taking
            // 100,000 rows off the system are different events for audit (FR-TLKX-007).
            audit.recordAuth(principal.email(), AuditEvent.ACTION_TALK_HISTORY_EXPORT,
                    AuditEvent.Outcome.OK,
                    criteria.describe() + " written=" + written, null, null);

            return written;

        } catch (RuntimeException | IOException e) {
            audit.recordAuth(principal.email(), AuditEvent.ACTION_TALK_HISTORY_EXPORT,
                    AuditEvent.Outcome.ERROR, criteria.describe(), null, null);
            throw e;
        }
    }

    /**
     * 내보낼 행을 조건 그대로 읽어 반환한다 — 시험이 집합 동일성을 단언하기 위한 접근점.
     * Reads the rows to export for the given criteria — the access point a test needs to assert set equality.
     *
     * <p>FR-TLKX-001 의 시험은 이 메서드의 결과와 목록 서비스의 결과를 비교한다. 두 경로가 같은
     * 매퍼 메서드를 부르므로 <b>같을 수밖에 없고</b>, 시험은 그 사실을 고정한다.</p>
     * <p>FR-TLKX-001's test compares this method's output with the list service's. Both paths call the same
     * mapper method, so they <b>cannot differ</b>, and the test pins that.</p>
     *
     * @param criteria 조건 / the criteria
     * @return 모든 행 / every matching row
     */
    // req: FR-TLKX-001
    @Transactional(readOnly = true)
    public List<TalkHistoryRow> rowsFor(TalkHistoryCriteria criteria) {
        List<TalkHistoryRow> all = new ArrayList<>();
        new PagedRowIterable(criteria).forEach(all::add);
        return all;
    }

    private static List<String> cellsOf(TalkHistoryRow row) {
        return List.of(
                nullToEmpty(row.transactionDate()),
                nullToEmpty(row.institutionCode()),
                // 화면과 같은 표시 규칙. 미해석 기관은 코드와 표식이 함께 나간다(D-T26).
                // The screen's display rule: an unresolved institution ships the code plus a marker (D-T26).
                nullToEmpty(row.institutionDisplay()),
                nullToEmpty(row.transactionNo()),
                nullToEmpty(row.apiServiceCode()),
                // 라벨과 원값을 함께. 운영자가 제공업체에 코드를 인용할 수 있어야 한다.
                // Label and raw code together, so an operator can quote the code to a provider.
                row.statusLabel() + " (" + nullToEmpty(row.statusCode()) + ")",
                nullToEmpty(row.responseCode()),
                nullToEmpty(row.registeredAt()),
                nullToEmpty(row.completedAt()));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 조건에 맞는 행을 페이지 단위로 흘려보낸다. / Streams the matching rows a page at a time.
     *
     * <p>전체를 메모리에 만들지 않는 것이 요점이다. 레거시는 결과 집합 전체를 힙에 올린 뒤
     * 워크북을 만들었다(D-T12).</p>
     * <p>The point is not materialising everything: the legacy put the whole result set on the heap and then
     * built the workbook (D-T12).</p>
     */
    // req: FR-TLKX-005, NFR-SCALE-T01
    private final class PagedRowIterable implements Iterable<TalkHistoryRow> {

        private final TalkHistoryCriteria base;

        private PagedRowIterable(TalkHistoryCriteria base) {
            this.base = base;
        }

        @Override
        public java.util.Iterator<TalkHistoryRow> iterator() {
            return new java.util.Iterator<>() {
                private int page = 0;
                private List<TalkHistoryRow> buffer = List.of();
                private int index = 0;
                private boolean exhausted = false;

                @Override
                public boolean hasNext() {
                    while (index >= buffer.size() && !exhausted) {
                        fetch();
                    }
                    return index < buffer.size();
                }

                @Override
                public TalkHistoryRow next() {
                    if (!hasNext()) {
                        throw new java.util.NoSuchElementException();
                    }
                    return buffer.get(index++);
                }

                private void fetch() {
                    TalkHistoryCriteria pageCriteria = new TalkHistoryCriteria(
                            base.window(), base.scope(), base.serial(), base.statusCode(),
                            base.apiServiceCode(), base.inScopeApiCodes(), page++, FETCH_PAGE);

                    List<TalkHistoryMapper.TalkHistoryRowRecord> raw =
                            mapper.findPage(pageCriteria);
                    if (raw.isEmpty()) {
                        exhausted = true;
                        buffer = List.of();
                        index = 0;
                        return;
                    }
                    buffer = raw.stream()
                            .map(r -> new TalkHistoryRow(
                                    r.transactionDate(), r.institutionCode(), r.institutionName(),
                                    r.transactionNo(), r.apiServiceCode(), r.statusCode(),
                                    r.responseCode(), r.registeredAt(), r.completedAt(),
                                    registry.detailAvailable(r.apiServiceCode())))
                            .toList();
                    index = 0;
                    if (raw.size() < FETCH_PAGE) {
                        exhausted = true;
                    }
                }
            };
        }
    }

    /**
     * 내보낼 행이 상한을 넘을 때 던진다. / Thrown when the rows to export exceed the ceiling.
     *
     * <p>메시지가 <b>맞는 범위를 알려준다</b>. 상한을 넘었다는 사실만 알리면 사용자는 시행착오로
     * 범위를 좁혀야 한다.</p>
     * <p>The message <b>names a range that would fit</b>: reporting only that a ceiling was exceeded leaves the
     * user narrowing the range by trial and error.</p>
     */
    // req: FR-TLKX-005
    public static class RowCeilingExceededException extends RuntimeException {

        /**
         * 예외를 생성한다. / Creates the exception.
         *
         * @param actual  실제 건수 / the actual count
         * @param ceiling 상한 / the ceiling
         */
        public RowCeilingExceededException(int actual, int ceiling) {
            super("내보낼 건수가 상한을 초과했습니다 (" + actual + "건 / 상한 " + ceiling
                    + "건). 조회 기간이나 조건을 좁혀 주세요. / "
                    + "The export exceeds the row ceiling (" + actual + " rows, ceiling " + ceiling
                    + "). Narrow the period or the filters.");
        }
    }
}
