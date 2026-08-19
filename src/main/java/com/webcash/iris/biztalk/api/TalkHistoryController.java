package com.webcash.iris.biztalk.api;

import com.webcash.iris.biztalk.domain.BizTalkApiRegistry;
import com.webcash.iris.biztalk.domain.PagedResult;
import com.webcash.iris.biztalk.domain.TalkHistoryRow;
import com.webcash.iris.biztalk.domain.TalkHistoryService;
import com.webcash.iris.biztalk.domain.TalkStatus;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 톡전송 내역 엔드포인트. / 톡전송 내역 endpoints.
 *
 * <h2>레거시 결함 대응 / the legacy behaviour being fixed</h2>
 * <p>이 슬라이스의 다섯 서비스는 <b>모두</b> {@code <login>Y</login>} 이었다 — 화면 20 이나
 * 40 과 달리 빠진 인증은 없었다. 없던 것은 <b>인가</b>다. 어떤 액션에도 역할 검사가 없었고
 * {@code IDO.KKB_APITR_HSTR_L001} 에는 기관 술어가 아예 없었으므로, 인증된 아무 주체나
 * <b>모든 고객사의 거래 내역</b>을 한 그리드로 볼 수 있었다(D-T2).</p>
 * <p><b>All five</b> of this slice's services declared {@code <login>Y</login>} — unlike screens 20 and
 * 40, no authentication was missing. What was missing is <b>authorization</b>: no action carried a role
 * check and {@code IDO.KKB_APITR_HSTR_L001} had no institution predicate at all, so any authenticated
 * principal saw <b>every customer's transactions</b> in one grid (D-T2).</p>
 *
 * <p>화면이 읽는 {@code FT_APITR_HSTR} 는 이 화면의 테이블이 아니다 — 전체 핀테크 API 의 거래
 * 로그이며 계좌번호·카드번호·거래금액·응답 전문을 담고 있다. 이 컨트롤러가 그중 무엇도
 * 반환하지 않는 것은 프로젝션이 9개 컬럼으로 닫혀 있기 때문이며, 계약 테스트가 응답 필드
 * 집합을 정확히 단언한다(CONST-SEC-T01).</p>
 * <p>The table behind this screen is not this screen's own: {@code FT_APITR_HSTR} is the whole fintech
 * estate's transaction log, carrying account numbers, card numbers, amounts and raw response telegrams.
 * This controller returns none of them because the projection is closed at nine columns, and a contract
 * test asserts the response's field set exactly (CONST-SEC-T01).</p>
 *
 * <p>{@code /api/admin/**} 아래 두어 {@code SecurityConfig} 의 운영자 규칙을 받게 하고,
 * 컨트롤러 수준 {@code @PreAuthorize} 를 이중으로 둔다 — PM 결정 CONFLICT-T01 로 이 화면은
 * <b>운영자 전용</b>이며, PROJECT-PROPOSAL §5.1 의 {@code [Tenant]} 분류가 정정되었다.
 * 내보내기 엔드포인트는 스프린트 T2 에서 추가되지만 <b>인가는 문을 만들 때 함께 건다</b> —
 * 레거시의 실패는 "인가를 잊었다"가 아니라 "한쪽 문만 잠갔다"였다.</p>
 * <p>Placed under {@code /api/admin/**} for the operator routing rule, with a controller-level
 * {@code @PreAuthorize} as defence in depth. Per PM ruling CONFLICT-T01 this screen is
 * <b>operator-only</b> and PROJECT-PROPOSAL §5.1's {@code [Tenant]} label is corrected. The export
 * endpoint arrives in Sprint T2, but <b>authorization is fitted when the door is built</b>.</p>
 *
 * // source: biztalk_admin_30_view.jsp, biztalk_admin_30.js, WSVC.biztalk_admin_30_l001.xml
 * // req: FR-AZ-T01, FR-AZ-T02, FR-AZ-T03, FR-TLK-001, FR-TLK-005, FR-TLK-013
 */
@RestController
@RequestMapping("/api/admin/biztalk/talk-history")
public class TalkHistoryController {

    private final TalkHistoryService service;

    /**
     * 컨트롤러를 생성한다. / Creates the controller.
     *
     * @param service 거래내역 조회 서비스 / the transaction-history query service
     */
    public TalkHistoryController(TalkHistoryService service) {
        this.service = service;
    }

    /**
     * 톡전송 거래내역을 조회한다. / Queries the 톡전송 transaction history.
     *
     * <p>{@code from} 은 필수다. 레거시는 요청일자를 오늘로 기본값 처리하고 서버 검증을 두지
     * 않았다(D-T24). 필수로 두면 값 없는 요청이 조용히 성공하지 않는다.</p>
     * <p>{@code from} is required. The legacy defaulted the request date to today with no server-side
     * validation (D-T24); requiring it stops a value-less request from quietly succeeding.</p>
     *
     * @param institution 이용기관 코드 / the institution code
     * @param from        시작일자 {@code YYYYMMDD} / the start date
     * @param to          종료일자 {@code YYYYMMDD}. 비우면 하루 / the end date; blank means one day
     * @param fromTime    시작시각 {@code HHMM}/{@code HHMMSS} / the start time
     * @param toTime      종료시각 {@code HHMM}/{@code HHMMSS} / the end time
     * @param serial      거래일련번호 / the transaction serial
     * @param status      상태 코드 / the status code
     * @param apiService  API 서비스 코드 / the API service code
     * @param page        0부터 시작하는 페이지 번호 / the zero-based page number
     * @param size        페이지 크기 / the page size
     * @param request     출처 IP 확보용 / for the source address
     * @return 한 페이지 / one page
     */
    // req: FR-TLK-001, FR-TLK-005, FR-TLK-013, FR-TLK-014, FR-AZ-T01, FR-AZ-T02
    @GetMapping
    @PreAuthorize("hasRole('OPERATOR')")
    public TalkHistoryResponse query(
            @RequestParam(required = false) String institution,
            @RequestParam String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String fromTime,
            @RequestParam(required = false) String toTime,
            @RequestParam(required = false) String serial,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String apiService,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            HttpServletRequest request) {

        // 정규화·검증·범위 결정은 모두 도메인이 한다. 컨트롤러가 조금이라도 나눠 가지면
        // 내보내기 엔드포인트(T2)가 생겼을 때 규칙이 갈라진다 — 레거시 다운로드가 정확히
        // 그렇게 계약을 우회했고(D-T14), 결국 화면과 다른 테이블을 조회했다(D-T1).
        // Normalisation, validation and scoping all belong to the domain. Splitting any of it into the
        // controller lets the rules diverge once the export exists (T2) — which is exactly how the
        // legacy download bypassed its contract (D-T14) and ended up querying different tables (D-T1).
        PagedResult<TalkHistoryRow> result = service.search(
                new TalkHistoryService.TalkQueryRequest(
                        institution, from, to, fromTime, toTime,
                        serial, status, apiService, page, size),
                request.getRemoteAddr());

        return TalkHistoryResponse.from(result);
    }

    /**
     * 화면이 제시할 필터 선택지를 반환한다. / Returns the filter options the screen offers.
     *
     * <p>API 선택기와 상태 선택기를 <b>한 번에</b> 돌려준다. 화면이 조회 전에 이 값을 먼저
     * 받아야 하는 이유는 레거시가 그러지 않았기 때문이다 — {@code onload} 가
     * {@code getDat()} 를 먼저 부르고 {@code fn_fintechSvcSel()} 을 나중에 불러, 선택기가
     * 채워지기 전에 질의가 나갔다(D-T28).</p>
     * <p>Returns the API and status selectors <b>together</b>. The screen needs them before querying
     * because the legacy did the opposite: {@code onload} called {@code getDat()} first and
     * {@code fn_fintechSvcSel()} afterwards, so the query left before the selector was filled (D-T28).</p>
     *
     * @return 선택지 / the options
     */
    // req: FR-TLK-002, FR-TLK-004, FR-TLK-012, FR-TLK-015
    @GetMapping("/filters")
    @PreAuthorize("hasRole('OPERATOR')")
    public FilterOptions filters() {
        return new FilterOptions(service.apiServiceOptions(), service.statusOptions());
    }

    /**
     * 필터 선택지 응답. / The filter-options response.
     *
     * @param apiServices API 선택기 항목 / the API selector's options
     * @param statuses    상태 선택기 항목 / the status selector's options
     */
    // req: FR-TLK-004, FR-TLK-012
    public record FilterOptions(List<BizTalkApiRegistry.Option> apiServices,
                                List<TalkStatus.FilterOption> statuses) {
    }
}
