package com.webcash.iris.biztalk.api;

import com.webcash.iris.biztalk.domain.TalkExportService;
import com.webcash.iris.biztalk.domain.TalkHistoryCriteria;
import com.webcash.iris.biztalk.domain.TalkHistoryService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 톡전송 내역 내보내기 엔드포인트. / The 톡전송 내역 export endpoint.
 *
 * <h2>레거시 내보내기의 네 가지 결함이 모두 이 클래스 하나에 대응한다 / four legacy defects, one class</h2>
 *
 * <p><b>D-T1 — 화면과 다른 것을 내보냈다.</b> 조건 조립을 이 컨트롤러가 하지 않고
 * {@link TalkHistoryService} 에 위임하므로, 내보내기와 목록이 <b>같은
 * {@link TalkHistoryCriteria}</b> 를 쓴다. 레거시는 다운로드 액션이 파라미터 열 개를 전부
 * {@code request.getParameter} 로 직접 읽었고(D-T14), 그래서 계약과 무관해진 끝에 화면과 아예
 * 다른 테이블을 조회하게 되었다.</p>
 * <p><b>D-T1 — it exported something other than the screen.</b> This controller does not assemble criteria; it
 * delegates to {@link TalkHistoryService}, so the export and the list share <b>one
 * {@link TalkHistoryCriteria}</b>. The legacy download read all ten parameters straight from
 * {@code request.getParameter} (D-T14), and having escaped its contract it ended up querying a different table
 * altogether.</p>
 *
 * <p><b>D-T4 — 응답 분할.</b> 레거시는 {@code startDt}/{@code endDt} 를 검증 없이 파일명에
 * 이어 붙이고 그 파일명을 {@code Content-Disposition} 에 넣었으며, 비-IE 분기는 바이트를 다시
 * 인코딩만 했다({@code getBytes("UTF-8"), "ISO-8859-1"}) — 인코딩도, CR/LF 거부도 없었다.
 * 여기서는 파일명이 <b>서버가 만든 상수와 검증을 통과한 일자</b>로만 조립되고, Spring 의
 * {@link ContentDisposition} 이 RFC 6266/5987 로 인코딩한다.</p>
 * <p><b>D-T4 — response splitting.</b> The legacy concatenated unvalidated dates into the filename and wrote it
 * into {@code Content-Disposition}, with the non-IE branch merely recoding the bytes — no encoding, no CR/LF
 * rejection. Here the filename is composed only from <b>a server constant and dates that have passed
 * validation</b>, and Spring's {@link ContentDisposition} encodes it per RFC 6266/5987.</p>
 *
 * <p><b>D-T23 — 실패가 보이지 않았다.</b> 레거시는 {@code frm0} 를 {@code ifrmFileProc} 라는
 * 대상으로 제출했는데, 그 이름의 프레임은 이 슬라이스의 어떤 뷰에도 없고
 * {@code fintech.common.submit} 이 만들어 주지도 않는다. 여기서는 일반 HTTP 응답이므로 오류가
 * 상태 코드와 본문으로 돌아가고, 브라우저는 {@code fetch} 로 그것을 읽는다.</p>
 * <p><b>D-T23 — failure was invisible.</b> The legacy submitted {@code frm0} to a target named
 * {@code ifrmFileProc}, a frame no view in this slice declares and which {@code fintech.common.submit} does not
 * create. Here it is an ordinary HTTP response, so an error returns as a status and a body that the browser
 * reads via {@code fetch}.</p>
 *
 * <p><b>D-T34 — 콘텐츠 타입을 네 번 설정했다.</b> 페이지 지시자, 비-IE 분기의
 * {@code application/vnd.ms-excel}, {@code application/download; UTF-8},
 * {@code application/octet-stream} — 그중 어느 것도 실제로 만들어지는 {@code .xlsx} 의 타입이
 * 아니었다. 여기서는 한 번, 올바른 값으로 설정된다.</p>
 * <p><b>D-T34 — the content type was set four times</b>, none of them the type of the {@code .xlsx} actually
 * produced. Here it is set once, correctly.</p>
 *
 * // source: biztalk_admin_30.js — fn_makeExcel(); biztalk_admin_30_spreadsheet_view.jsp
 * // req: FR-TLKX-001…008, NFR-SEC-HDR-T01, NFR-COMPAT-T01
 */
@RestController
@RequestMapping("/api/admin/biztalk/talk-history")
public class TalkExportController {

    /** xlsx 의 정확한 미디어 타입. / The correct media type for xlsx. */
    static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** 파일명 접두사 — 서버 상수다. / The filename prefix, a server constant. */
    static final String FILENAME_PREFIX = "톡전송내역";

    private final TalkHistoryService historyService;
    private final TalkExportService exportService;

    /**
     * 컨트롤러를 생성한다. / Creates the controller.
     *
     * @param historyService 조건 조립을 담당 — 목록과 <b>같은</b> 서비스 / assembles criteria, the <b>same</b> service the list uses
     * @param exportService  내보내기 서비스 / the export service
     */
    public TalkExportController(TalkHistoryService historyService,
                                TalkExportService exportService) {
        this.historyService = historyService;
        this.exportService = exportService;
    }

    /**
     * 조건에 맞는 행을 xlsx 로 내보낸다. / Exports the matching rows as xlsx.
     *
     * <p>파라미터가 목록 엔드포인트와 <b>같다</b>. 하나라도 다르면 화면에 걸린 조건이 파일에
     * 반영되지 않을 수 있고, 그것이 D-T1 의 시작이었다.</p>
     * <p>The parameters are <b>identical</b> to the list endpoint's. Any difference would let a filter set on
     * the screen miss the file, which is where D-T1 began.</p>
     *
     * @param institution 이용기관 코드 / the institution code
     * @param from        시작일자 / the start date
     * @param to          종료일자 / the end date
     * @param fromTime    시작시각 / the start time
     * @param toTime      종료시각 / the end time
     * @param serial      거래일련번호 / the transaction serial
     * @param status      상태 코드 / the status code
     * @param apiService  API 서비스 코드 / the API service code
     * @param request     출처 IP 확보용 / for the source address
     * @return xlsx 본문 / the xlsx body
     * @throws IOException 쓰기 실패 / on a write failure
     */
    // req: FR-TLKX-001, FR-TLKX-002, FR-TLKX-003, FR-TLKX-004, FR-TLKX-008
    @GetMapping("/export")
    @PreAuthorize("hasRole('OPERATOR')")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String institution,
            @RequestParam String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String fromTime,
            @RequestParam(required = false) String toTime,
            @RequestParam(required = false) String serial,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String apiService,
            HttpServletRequest request) throws IOException {

        // 조건 조립은 목록과 같은 서비스가 한다. 컨트롤러가 조금이라도 나눠 가지면 두 경로의
        // 규칙이 갈라지고, 레거시 다운로드가 정확히 그렇게 계약을 우회했다(D-T14 → D-T1).
        // The same service assembles the criteria. Splitting any of it into the controller lets the two paths
        // diverge, which is exactly how the legacy download escaped its contract (D-T14 → D-T1).
        TalkHistoryCriteria criteria = historyService.criteriaFor(
                new TalkHistoryService.TalkQueryRequest(
                        institution, from, to, fromTime, toTime, serial, status, apiService, 0, null));

        // 상한 검사가 스트림 쓰기보다 먼저 일어나므로, 거부는 부분 파일을 남기지 않는다.
        // 잘라낸 파일은 조용히 틀린 답이며, 이 프로그램의 silent-success 양식이다.
        // The ceiling check precedes any stream write, so a refusal leaves no partial file. A truncated file
        // is a quietly wrong answer — this programme's silent-success shape.
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int written = exportService.export(criteria, buffer);

        return ResponseEntity.ok()
                .headers(headers(criteria, written))
                .body(buffer.toByteArray());
    }

    /**
     * 응답 헤더를 만든다. / Builds the response headers.
     *
     * <p>파일명에 들어가는 값은 <b>서버 상수와 검증된 일자</b>뿐이다. 사용자 입력이 헤더에
     * 도달하는 경로가 존재하지 않으므로, CR/LF 를 거부할 필요조차 없다 — 거부보다 강한 성질이며
     * NFR-SEC-HDR-T01 이 요구하는 것이다.</p>
     * <p>Only <b>a server constant and validated dates</b> enter the filename. No path exists by which user
     * input reaches a header, so there is nothing to reject — a stronger property than rejection, and what
     * NFR-SEC-HDR-T01 asks for.</p>
     */
    // req: FR-TLKX-003, FR-TLKX-004, NFR-SEC-HDR-T01
    private static HttpHeaders headers(TalkHistoryCriteria criteria, int written) {
        String filename = FILENAME_PREFIX + "_"
                + criteria.window().fromDateYyyymmdd() + "-"
                + criteria.window().toDateYyyymmdd() + ".xlsx";

        HttpHeaders headers = new HttpHeaders();
        // 한 번만 설정한다. 레거시는 네 번 설정했고 그중 어느 것도 xlsx 의 타입이 아니었다(D-T34).
        // Set once. The legacy set it four times and none of them was the xlsx type (D-T34).
        headers.setContentType(MediaType.parseMediaType(XLSX_MEDIA_TYPE));
        headers.setContentDisposition(ContentDisposition.attachment()
                // RFC 6266 + RFC 5987. ASCII 대체 파일명과 UTF-8 filename* 을 모두 낸다.
                // RFC 6266 + RFC 5987: both an ASCII fallback and a UTF-8 filename* are emitted.
                .filename(filename, StandardCharsets.UTF_8)
                .build());
        // 실제로 쓴 행 수를 헤더로도 알린다. 사용자가 파일을 열기 전에 건수를 확인할 수 있고,
        // 감사 기록의 건수와 대조할 수 있다(FR-TLKX-007).
        // The row count is also surfaced as a header, so a user can check it before opening the file and it
        // can be reconciled against the audit record (FR-TLKX-007).
        headers.set("X-Talk-Export-Rows", Integer.toString(written));
        return headers;
    }

    /*
     * 레거시가 파일명을 헤더에 넣던 방식은 재현하지 않는다 — 그리고 그것을 설명하는 죽은
     * 메서드도 두지 않는다. 이 슬라이스는 죽은 코드를 결함으로 셌다(D-T31).
     *
     * 레거시는 비-IE 분기에서 new String(FILE_NM.getBytes("UTF-8"), "ISO-8859-1") 로 바이트만
     * 다시 해석했다. 그것은 인코딩이 아니므로 값에 CR/LF 가 있으면 헤더가 분할된다. IE 분기가
     * 안전했던 것은 우연히 URLEncoder.encode 를 호출했기 때문이며, 설계된 통제가 아니었다.
     *
     * The legacy filename handling is not reproduced — and neither is a dead method explaining it: this
     * slice counted dead code as a defect (D-T31).
     *
     * Its non-IE branch merely reinterpreted bytes with getBytes("UTF-8"), "ISO-8859-1". That is not
     * encoding, so a CR/LF in the value splits the header. The IE branch was safe only because it happened
     * to call URLEncoder.encode — an accident rather than a designed control.
     *
     * source: biztalk_admin_30_spreadsheet_view.jsp — zipFileName / miseFileName
     * req: NFR-SEC-HDR-T01, FR-TLKX-003
     */
}
