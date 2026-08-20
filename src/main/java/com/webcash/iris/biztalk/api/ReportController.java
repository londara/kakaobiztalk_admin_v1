package com.webcash.iris.biztalk.api;

import com.webcash.iris.biztalk.domain.ReportPage;
import com.webcash.iris.biztalk.domain.ReportService;
import com.webcash.iris.biztalk.domain.SendSource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 이용기관 보고서 엔드포인트. / Institution usage report endpoints.
 *
 * <h2>레거시 결함 대응 / the legacy behaviour being fixed</h2>
 * <p>화면(20)과 다운로드(20_spreadsheet)는 {@code <login>Y</login>} 였는데, <b>실제로 숫자를
 * 돌려주는 서비스</b>인 {@code biztalk_admin_20_l001} 만 {@code <login>N</login>} 이었다
 * (D-R1). 여기에 빈 {@code IS_CD} 가 "전 기관"을 뜻했으므로(D-R2), <b>자격증명 없이 한 번의
 * 요청으로 모든 고객사의 일자별·채널별 발송량</b>을 가져갈 수 있었다. 사전 CVSS 약 9.1 로
 * 이 프로그램에서 발견된 가장 값비싼 노출이다(T-R10).</p>
 * <p>The screen and the download declared {@code <login>Y</login>}; only
 * {@code biztalk_admin_20_l001} — <b>the service that actually returns the figures</b> — declared
 * {@code <login>N</login>} (D-R1). Since an empty {@code IS_CD} meant "every institution" (D-R2),
 * <b>one unauthenticated request returned every customer's volumes by day and channel</b>. At an
 * estimated CVSS 9.1 it is the most valuable disclosure found in this programme (T-R10).</p>
 *
 * <p>개인정보가 없다는 점이 위험을 낮추지 않는다. 노출되는 것은 고객사별 영업 규모이며,
 * 통제 목표는 개인정보 보호가 아니라 <b>상업적 기밀 유지</b>다(CONST-LEGAL-R01).</p>
 * <p>The absence of personal data does not lower the risk: what is exposed is each customer's
 * commercial scale, and the control objective is <b>commercial confidentiality</b> rather than
 * privacy (CONST-LEGAL-R01).</p>
 *
 * <p>{@code /api/admin/**} 아래 두어 {@code SecurityConfig} 의 운영자 규칙을 받게 하고,
 * 컨트롤러 수준 {@code @PreAuthorize} 를 이중으로 둔다. 내보내기 엔드포인트는 스프린트 R2 에서
 * 추가되지만 <b>인가는 문을 만들 때 함께 건다</b> — 레거시의 실패는 "인가를 잊었다"가 아니라
 * "한쪽 문만 잠갔다"였다(D-R10).</p>
 * <p>Placed under {@code /api/admin/**} for the operator routing rule, with a controller-level
 * {@code @PreAuthorize} as defence in depth. The export endpoint arrives in Sprint R2, but
 * <b>authorization is fitted when the door is built</b> — the legacy's failure was not "we forgot
 * authorization" but "we locked one of two doors" (D-R10).</p>
 *
 * // source: biztalk_admin_20_view.jsp, biztalk_admin_20.js, WSVC.biztalk_admin_20_l001.xml
 * // req: FR-AZ-R01, FR-AZ-R02, FR-AZ-R03, FR-RPT-001, FR-RPT-005, FR-RPT-013
 */
@RestController
@RequestMapping("/api/admin/reports/usage")
public class ReportController {

    private final ReportService service;

    /**
     * 컨트롤러를 생성한다. / Creates the controller.
     *
     * @param service 보고서 조회 서비스 / the report query service
     */
    public ReportController(ReportService service) {
        this.service = service;
    }

    /**
     * 이용기관 보고서를 조회한다. / Queries the institution usage report.
     *
     * <p>{@code from}/{@code to} 는 필수다. 레거시는 기본값을 오늘로 두고 서버 검증을 두지
     * 않아, 화면을 열자마자 <b>집계된 적 없는 구간</b>을 조회했다(D-R25). 필수로 두면 값이
     * 없는 요청이 조용히 성공하는 일이 없다.</p>
     * <p>{@code from} and {@code to} are required. The legacy defaulted them to today with no
     * server-side validation, so opening the screen queried <b>a window that had never been
     * aggregated</b> (D-R25). Requiring them stops a value-less request from quietly succeeding.</p>
     *
     * @param institution 이용기관 코드. 운영자만 유효 / the institution code, honoured for operators only
     * @param source      발송 구분 API/BULK/ALL / the send-source filter
     * @param from        시작일자 {@code YYYYMMDD} / the start date
     * @param to          종료일자 {@code YYYYMMDD} / the end date
     * @param seekDate    이어보기 일자 / the seek date
     * @param seekInstitution 이어보기 기관코드 / the seek institution code
     * @param size        페이지 크기 / the page size
     * @param request     출처 IP 확보용 / for the source address
     * @return 한 페이지 / one page
     */
    // req: FR-RPT-001, FR-RPT-005, FR-RPT-007, FR-AZ-R01, FR-AZ-R03
    @GetMapping
    @PreAuthorize("hasRole('OPERATOR')")
    public ReportResponse query(
            @RequestParam(required = false) String institution,
            @RequestParam(required = false) String source,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) String seekDate,
            @RequestParam(required = false) String seekInstitution,
            @RequestParam(required = false) Integer size,
            HttpServletRequest request) {

        // 정규화·검증·범위 결정은 모두 도메인이 한다. 컨트롤러가 조금이라도 나눠 가지면
        // 내보내기 엔드포인트(R2)가 생겼을 때 규칙이 갈라진다 — 레거시 다운로드가 정확히
        // 그렇게 계약을 우회했다(D-R10).
        // Normalisation, validation and scoping all belong to the domain. Splitting any of it
        // into the controller lets the rules diverge once the export endpoint exists in R2 —
        // which is exactly how the legacy download bypassed its own contract (D-R10).
        ReportPage page = service.query(institution, source, from, to,
                seekDate, seekInstitution, size, request.getRemoteAddr());

        return ReportResponse.from(page, SendSource.parse(source));
    }

    /**
     * 집계 기준일만 조회한다. / Reads the aggregation watermark alone.
     *
     * <p>화면은 조회 전에 이 값을 먼저 보여준다. 사용자가 기간을 고르기 <b>전에</b> 데이터가
     * 어디까지 있는지 알아야, 오늘을 조회하고 빈 화면을 마주하는 일이 없다(D-R25).</p>
     * <p>The screen shows this before any query: knowing how far the data reaches <b>before</b>
     * choosing a period is what stops a user querying today and meeting an empty grid (D-R25).</p>
     *
     * @param source 발송 구분 / the source filter
     * @return 기준일 / the watermark
     */
    // req: FR-RPT-013, FR-RPT-015, NFR-USE-R01
    @GetMapping("/watermark")
    @PreAuthorize("hasRole('OPERATOR')")
    public ReportResponse.Watermark watermark(@RequestParam(required = false) String source) {
        SendSource requested = SendSource.parse(source);
        return ReportResponse.Watermark.from(service.watermark(requested), requested);
    }
}
