package com.webcash.iris.biztalk.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.webcash.iris.biztalk.infra.db.TalkHistoryMapper;
import com.webcash.iris.biztalk.infra.excel.StreamingWorkbookWriter;
import com.webcash.iris.common.audit.AuditEvent;
import com.webcash.iris.common.audit.AuditService;
import com.webcash.iris.common.tenant.TenantContext;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;

/**
 * D-T1 의 회귀 테스트 — 내보내기가 화면과 같은 것을 내보내는가.
 * D-T1's regression test: does the export export what the screen shows?
 *
 * <h2>왜 <b>집합 동일성</b>인가 / why a <b>set equality</b></h2>
 * <p>D-T1 은 개별 부품이 모두 정상인데 <b>조합</b>이 결함인 종류다. {@code IDO.KKB_MSG_L001} 은
 * 유효한 질의이고, {@code fn_makeExcel()} 은 동작하는 함수이고, 계약은 파라미터 열두 개를
 * 선언했다. 어느 계층도 이웃과 모순되지 않는다 — 그럼에도 그 조합이 <b>모든 이용기관의
 * 평문 전화번호 반출</b>이었다. 매퍼 시험도, 계약 시험도, 컴포넌트 시험도 그것을 찾지
 * 못한다.</p>
 * <p>D-T1 is the kind of defect where every part works and the <b>composition</b> is the fault.
 * {@code IDO.KKB_MSG_L001} is a valid query, {@code fn_makeExcel()} is a working function, and the contract
 * declared twelve parameters. No layer contradicts its neighbour — and yet the composition was <b>a plaintext
 * extraction of every institution's phone numbers</b>. No mapper test, contract test or component test finds
 * that.</p>
 *
 * <p>찾는 방법은 <b>의미를 단언</b>하는 것뿐이다: 같은 조건으로 내보낸 행 집합이 같은 조건으로
 * 조회한 행 집합과 같아야 한다. 성질 목록이 아니라 등식이며, 그래서 FR-TLKX-001 의 검증 방법이
 * "집합 동일성"으로 적혀 있다.</p>
 * <p>The only way to find it is to <b>assert meaning</b>: the rows exported for a set of filters must equal the
 * rows listed for those filters. An equation rather than a list of properties, which is why FR-TLKX-001's
 * verification method is written as a set equality.</p>
 *
 * // source: biztalk_admin_30.js — fn_makeExcel() reads #IS_LIST/#MSGKEY/#PHONE/#CALLBACK/#RSLT/#STATUS/#MSG_TYPE
 * // source: biztalk_admin_30_spreadsheet_act.jsp — IDO.KKB_MSG_L001, a different table family
 * // req: FR-TLKX-001, FR-TLKX-005, FR-TLKX-007, FR-TLKX-008
 */
class TalkExportParityTest {

    private TalkHistoryMapper mapper;
    private AuditService audit;
    private BizTalkApiRegistry registry;
    private TalkHistoryService historyService;
    private TalkExportService exportService;

    /** 조건에 실린 값을 그대로 되비추는 가짜 매퍼가 만드는 행. / Rows a fake mapper echoes back from the criteria. */
    private final List<TalkHistoryMapper.TalkHistoryRowRecord> world = new ArrayList<>();

    private static TalkHistoryMapper.TalkHistoryRowRecord row(int i, String api, String status,
                                                              String institution) {
        return new TalkHistoryMapper.TalkHistoryRowRecord(
                "20260819", institution, "기관" + institution,
                String.format("%020d", i), api, status, null,
                "20260819112504", "20260819112504");
    }

    /**
     * 조건을 실제로 적용하는 가짜 매퍼. / A fake mapper that actually applies the criteria.
     *
     * <p>단순히 고정 목록을 돌려주면 이 시험은 아무것도 증명하지 못한다 — 두 경로가 <b>조건을
     * 다르게 해석하는지</b>가 요점이므로, 가짜 매퍼도 조건을 해석해야 한다.</p>
     * <p>Returning a fixed list would prove nothing: the point is whether the two paths <b>interpret the
     * criteria differently</b>, so the fake must interpret them too.</p>
     */
    private List<TalkHistoryMapper.TalkHistoryRowRecord> apply(InvocationOnMock invocation) {
        return apply((TalkHistoryCriteria) invocation.getArgument(0));
    }

    private List<TalkHistoryMapper.TalkHistoryRowRecord> apply(TalkHistoryCriteria c) {
        List<TalkHistoryMapper.TalkHistoryRowRecord> matched = world.stream()
                .filter(r -> c.inScopeApiCodes().contains(r.apiServiceCode()))
                .filter(r -> c.apiServiceCode() == null
                        || c.apiServiceCode().equals(r.apiServiceCode()))
                .filter(r -> c.statusCode() == null || c.statusCode().equals(r.statusCode()))
                .filter(r -> c.scope().institutionCode() == null
                        || c.scope().institutionCode().equals(r.institutionCode()))
                .filter(r -> c.serialForMapper() == null
                        || c.serialForMapper().equals(r.transactionNo()))
                .toList();

        int from = Math.min(c.offset(), matched.size());
        int to = Math.min(from + c.size(), matched.size());
        return matched.subList(from, to);
    }

    @BeforeEach
    void setUp() {
        world.clear();
        for (int i = 1; i <= 25; i++) {
            world.add(row(i, "ADV_KKO_AT_SEND", "1", "K00011"));
        }
        for (int i = 26; i <= 40; i++) {
            world.add(row(i, "ADV_KKO_FT_SEND", "9", "K00022"));
        }
        // 범위 밖 API — 두 경로 모두에서 제외되어야 한다.
        // An out-of-scope API: both paths must exclude it.
        world.add(row(99, "ADV_COM_GET_STATUS", "1", "K00011"));

        mapper = Mockito.mock(TalkHistoryMapper.class);
        given(mapper.findPage(any())).willAnswer(this::apply);
        given(mapper.countAll(any())).willAnswer(inv -> {
            TalkHistoryCriteria c = inv.getArgument(0);
            // 건수는 페이징을 제거한 조건으로 센다 — 실제 매퍼의 countAll 이 LIMIT/OFFSET 없이
            // 같은 술어를 쓰는 것과 같은 형태다. 그 대응이 맞지 않으면 이 가짜 매퍼는 두 경로의
            // 불일치가 아니라 자기 자신의 버그를 보여준다.
            // The count uses the criteria with paging removed, mirroring the real mapper's countAll sharing the
            // predicate without LIMIT/OFFSET. If that correspondence were wrong the fake would surface its own
            // bug rather than any divergence between the two paths.
            TalkHistoryCriteria unpaged = new TalkHistoryCriteria(
                    c.window(), c.scope(), c.serial(), c.statusCode(), c.apiServiceCode(),
                    c.inScopeApiCodes(), 0, Integer.MAX_VALUE);
            return apply(unpaged).size();
        });

        audit = Mockito.mock(AuditService.class);
        registry = BizTalkApiRegistry.withDefaults();
        historyService = new TalkHistoryService(mapper, registry, audit);
        exportService = new TalkExportService(
                mapper, registry, new StreamingWorkbookWriter(), audit);

        TenantContext.set(new TenantContext.TenantPrincipal("op@example.com", null, true));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static TalkHistoryService.TalkQueryRequest request(String institution, String status,
                                                              String api, String serial) {
        return new TalkHistoryService.TalkQueryRequest(
                institution, "20260819", null, null, null, serial, status, api, 0, 1_000);
    }

    @Nested
    @DisplayName("집합 동일성 / set equality")
    class SetEquality {

        private List<String> listed(TalkHistoryService.TalkQueryRequest request) {
            return historyService.search(request, "127.0.0.1").rows().stream()
                    .map(r -> r.transactionNo()).toList();
        }

        private List<String> exported(TalkHistoryService.TalkQueryRequest request) {
            return exportService.rowsFor(historyService.criteriaFor(request)).stream()
                    .map(r -> r.transactionNo()).toList();
        }

        @Test
        @DisplayName("조건 없이: 내보낸 행 집합 = 조회한 행 집합 — FR-TLKX-001")
        void unfilteredSetsAreEqual() {
            TalkHistoryService.TalkQueryRequest request = request(null, null, null, null);

            assertThat(exported(request))
                    .as("내보내기가 화면과 다른 것을 내보내면 이 단언이 실패한다 (D-T1) / "
                            + "if the export exports something other than the screen, this fails")
                    .containsExactlyElementsOf(listed(request));
        }

        @Test
        @DisplayName("모든 필터에 대해 두 집합이 같다 — TC-T004-01")
        void everyFilterCombinationAgrees() {
            // ⚠ 이것이 D-T1 을 잡는 형태다. 레거시에서 fn_makeExcel() 은 화면에 없는 DOM 요소
            // 일곱 개에서 값을 읽었으므로 <b>모든 필터가 빈 문자열</b>이 되었고, 파일에는 조건과
            // 무관하게 모든 것이 담겼다. 필터를 하나씩 바꿔 두 집합을 비교하면 그 상태가 드러난다.
            //
            // This is the shape that catches D-T1. In the legacy, fn_makeExcel() read from seven DOM elements
            // the screen does not have, so <b>every filter became the empty string</b> and the file contained
            // everything regardless of the criteria. Varying one filter at a time and comparing the two sets
            // exposes that state.
            List<TalkHistoryService.TalkQueryRequest> combinations = List.of(
                    request(null, null, null, null),
                    request("K00011", null, null, null),
                    request("K00022", null, null, null),
                    request(null, "1", null, null),
                    request(null, "9", null, null),
                    request(null, null, "ADV_KKO_AT_SEND", null),
                    request(null, null, "ADV_KKO_FT_SEND", null),
                    request("K00011", "1", "ADV_KKO_AT_SEND", null),
                    request(null, null, null, "7"));

            for (TalkHistoryService.TalkQueryRequest request : combinations) {
                assertThat(exported(request))
                        .as("filters institution=%s status=%s api=%s serial=%s",
                                request.institution(), request.status(),
                                request.apiService(), request.serial())
                        .containsExactlyElementsOf(listed(request));
            }
        }

        @Test
        @DisplayName("모든 필터가 파일을 실제로 바꾼다 — TC-T004-02")
        void everyFilterChangesTheFile() {
            // 조용히 무시되는 필터를 잡는다. 두 집합이 같기만 하면 <b>둘 다 필터를 무시</b>하는
            // 경우를 통과시킬 수 있으므로, 필터가 결과를 실제로 좁히는지 따로 단언한다.
            // Catches a silently ignored filter: equality alone would pass a case where <b>both</b> paths ignore
            // it, so this asserts separately that a filter actually narrows the result.
            int all = exportService.rowsFor(
                    historyService.criteriaFor(request(null, null, null, null))).size();

            assertThat(exportService.rowsFor(
                    historyService.criteriaFor(request("K00011", null, null, null))).size())
                    .isLessThan(all);
            assertThat(exportService.rowsFor(
                    historyService.criteriaFor(request(null, "9", null, null))).size())
                    .isLessThan(all);
            assertThat(exportService.rowsFor(
                    historyService.criteriaFor(request(null, null, "ADV_KKO_FT_SEND", null))).size())
                    .isLessThan(all);
            assertThat(exportService.rowsFor(
                    historyService.criteriaFor(request(null, null, null, "7"))).size())
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("범위 밖 API 는 두 경로 모두에서 빠진다 — SCOPE-T01")
        void outOfScopeApiIsAbsentFromBoth() {
            TalkHistoryService.TalkQueryRequest request = request(null, null, null, null);

            assertThat(exported(request)).doesNotContain(String.format("%020d", 99));
            assertThat(listed(request)).doesNotContain(String.format("%020d", 99));
        }
    }

    @Nested
    @DisplayName("워크북 / the workbook")
    class WorkbookContent {

        private Workbook export(TalkHistoryService.TalkQueryRequest request) throws Exception {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            exportService.export(historyService.criteriaFor(request), out);
            return new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()));
        }

        @Test
        @DisplayName("헤더가 화면 컬럼과 같고 셀이 한 번만 만들어진다 — D-T34")
        void headerMatchesTheScreenAndIsWrittenOnce() throws Exception {
            try (Workbook workbook = export(request(null, null, null, null))) {
                Sheet sheet = workbook.getSheetAt(0);
                Row header = sheet.getRow(0);

                assertThat(header.getPhysicalNumberOfCells())
                        .as("레거시는 같은 인덱스에 createCell 을 두 번 호출했다 / "
                                + "the legacy called createCell twice at the same index")
                        .isEqualTo(TalkExportService.HEADERS.size());

                for (int i = 0; i < TalkExportService.HEADERS.size(); i++) {
                    assertThat(header.getCell(i).getStringCellValue())
                            .isEqualTo(TalkExportService.HEADERS.get(i));
                }
            }
        }

        @Test
        @DisplayName("행 수가 조회 결과와 같다")
        void rowCountMatchesTheQuery() throws Exception {
            TalkHistoryService.TalkQueryRequest request = request(null, null, null, null);
            int expected = historyService.search(request, "127.0.0.1").rows().size();

            try (Workbook workbook = export(request)) {
                // 헤더 한 줄을 빼면 본문 행 수다.
                // Minus the single header row gives the body row count.
                assertThat(workbook.getSheetAt(0).getLastRowNum()).isEqualTo(expected);
            }
        }

        @Test
        @DisplayName("상태 셀이 라벨과 원값을 함께 담는다 — NFR-USE-T01")
        void statusCellCarriesLabelAndCode() throws Exception {
            try (Workbook workbook = export(request(null, "9", null, null))) {
                Row first = workbook.getSheetAt(0).getRow(1);
                assertThat(first.getCell(5).getStringCellValue()).isEqualTo("오류 (9)");
            }
        }

        @Test
        @DisplayName("시트가 하나다 — 레거시의 환경별 시트 집합이 없다")
        void oneSheetOnly() throws Exception {
            try (Workbook workbook = export(request(null, null, null, null))) {
                assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
                assertThat(workbook.getSheetName(0)).isEqualTo(TalkExportService.SHEET_NAME);
            }
        }
    }

    @Nested
    @DisplayName("상한과 감사 / ceiling and audit")
    class CeilingAndAudit {

        @Test
        @DisplayName("상한을 넘으면 잘라내지 않고 거부한다 — D-T12")
        void overCeilingIsRefusedNotTruncated() {
            // 조용히 짧은 파일은 내보내기 버전의 silent success 다. 거부는 맞는 범위를 알려준다.
            // A quietly short file is the export's silent success. A refusal names a range that fits.
            // given(...) 로 다시 스텁하면 Mockito 가 기록 과정에서 <b>기존 answer 를 실제로 호출</b>하고,
            // 그때 인자가 null 이어서 NPE 가 난다. doReturn 은 호출하지 않는다.
            // Re-stubbing with given(...) makes Mockito <b>invoke the existing answer</b> while recording, with a
            // null argument, which NPEs. doReturn does not invoke it.
            Mockito.doReturn(TalkExportService.ROW_CEILING + 1).when(mapper).countAll(any());

            assertThatThrownBy(() -> exportService.export(
                    historyService.criteriaFor(request(null, null, null, null)),
                    new ByteArrayOutputStream()))
                    .isInstanceOf(TalkExportService.RowCeilingExceededException.class)
                    .hasMessageContaining("좁혀");
        }

        @Test
        @DisplayName("거부는 한 행도 쓰지 않는다")
        void refusalWritesNothing() {
            // given(...) 로 다시 스텁하면 Mockito 가 기록 과정에서 <b>기존 answer 를 실제로 호출</b>하고,
            // 그때 인자가 null 이어서 NPE 가 난다. doReturn 은 호출하지 않는다.
            // Re-stubbing with given(...) makes Mockito <b>invoke the existing answer</b> while recording, with a
            // null argument, which NPEs. doReturn does not invoke it.
            Mockito.doReturn(TalkExportService.ROW_CEILING + 1).when(mapper).countAll(any());
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            assertThatThrownBy(() -> exportService.export(
                    historyService.criteriaFor(request(null, null, null, null)), out))
                    .isInstanceOf(TalkExportService.RowCeilingExceededException.class);

            assertThat(out.size())
                    .as("부분 파일이 남으면 사용자는 완전한 파일을 받았다고 생각한다 / "
                            + "a partial file would read as a complete one")
                    .isZero();
        }

        @Test
        @DisplayName("내보내기는 쓴 행 수와 함께 감사된다 — FR-TLKX-007")
        void exportIsAuditedWithRowCount() throws Exception {
            exportService.export(historyService.criteriaFor(request(null, null, null, null)),
                    new ByteArrayOutputStream());

            org.mockito.ArgumentCaptor<String> detail =
                    org.mockito.ArgumentCaptor.forClass(String.class);
            verify(audit).recordAuth(anyString(),
                    org.mockito.ArgumentMatchers.eq(AuditEvent.ACTION_TALK_HISTORY_EXPORT),
                    org.mockito.ArgumentMatchers.eq(AuditEvent.Outcome.OK),
                    detail.capture(), any(), any());

            assertThat(detail.getValue()).contains("written=40");
        }

        @Test
        @DisplayName("거부도 감사된다 — 시도 자체가 증적이다")
        void refusalIsAudited() {
            // given(...) 로 다시 스텁하면 Mockito 가 기록 과정에서 <b>기존 answer 를 실제로 호출</b>하고,
            // 그때 인자가 null 이어서 NPE 가 난다. doReturn 은 호출하지 않는다.
            // Re-stubbing with given(...) makes Mockito <b>invoke the existing answer</b> while recording, with a
            // null argument, which NPEs. doReturn does not invoke it.
            Mockito.doReturn(TalkExportService.ROW_CEILING + 1).when(mapper).countAll(any());

            assertThatThrownBy(() -> exportService.export(
                    historyService.criteriaFor(request(null, null, null, null)),
                    new ByteArrayOutputStream()))
                    .isInstanceOf(TalkExportService.RowCeilingExceededException.class);

            verify(audit).recordAuth(anyString(),
                    org.mockito.ArgumentMatchers.eq(AuditEvent.ACTION_TALK_HISTORY_EXPORT),
                    org.mockito.ArgumentMatchers.eq(AuditEvent.Outcome.DENIED),
                    anyString(), any(), any());
        }

        @Test
        @DisplayName("검증 실패는 매퍼에 닿지 않는다")
        void validationFailureNeverReachesTheMapper() {
            Mockito.reset(mapper);

            assertThatThrownBy(() -> historyService.criteriaFor(
                    new TalkHistoryService.TalkQueryRequest(
                            null, "20260801", "20260901", null, null, null, null, null, 0, null)))
                    .isInstanceOf(PeriodPolicy.InvalidPeriodException.class);

            verify(mapper, never()).findPage(any());
            verify(mapper, never()).countAll(any());
        }
    }
}
