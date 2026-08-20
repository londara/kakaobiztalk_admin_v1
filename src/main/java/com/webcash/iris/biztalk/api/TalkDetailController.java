package com.webcash.iris.biztalk.api;

import com.webcash.iris.biztalk.domain.PagedResult;
import com.webcash.iris.biztalk.domain.TalkDetailService;
import com.webcash.iris.biztalk.domain.TalkMessageDetail;
import com.webcash.iris.biztalk.domain.TalkMessageRow;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 거래 상세내역과 메시지 상세 엔드포인트 — 화면 32 와 31.
 * Transaction-detail and message-detail endpoints: screens 32 and 31.
 *
 * <h2>경로가 계층을 강제한다 / the path enforces the hierarchy</h2>
 * <p>메시지 상세의 경로가 거래 아래에 있다:
 * {@code /{trdd}/{serial}/messages/{messageKey}}. 레거시는 메시지 상세를
 * {@code REQDATE} + {@code STATUS} + {@code MSGKEY} 로 <b>독립 조회</b>할 수 있게 했고, 기관
 * 조건이 없었으므로 메시지 키만 알면 다른 기관의 메시지 본문을 읽었다(D-T5).</p>
 * <p>The message-detail path sits beneath the transaction: {@code /{trdd}/{serial}/messages/{messageKey}}.
 * The legacy allowed message detail to be fetched <b>independently</b> by {@code REQDATE} + {@code STATUS} +
 * {@code MSGKEY}, and with no institution predicate a message key alone read another institution's message
 * body (D-T5).</p>
 *
 * <p>경로 형태로 강제하는 것은 <b>보조 통제</b>다. 실제 인가는 서비스가 원장에서 기관을 도출해
 * 수행하며, 거래 키가 경로에 있으므로 서비스가 그것을 할 수 있다 — 계층이 URL 의 관습이 아니라
 * 조회 방법 자체가 된다(CONST-BIZ-T01).</p>
 * <p>The path shape is a <b>secondary control</b>. The real authorization is the service deriving the
 * institution from the ledger, and the transaction key being in the path is what lets it — so the hierarchy
 * is the lookup mechanism rather than a URL convention (CONST-BIZ-T01).</p>
 *
 * // source: biztalk_admin_32_view.jsp, biztalk_admin_32.js, biztalk_admin_31.js
 * // req: FR-AZ-T02, FR-AZ-T03, FR-AZ-T04, FR-TLKD-001…009, FR-TLKM-001…007
 */
@RestController
@RequestMapping("/api/admin/biztalk/talk-history")
public class TalkDetailController {

    private final TalkDetailService service;

    /**
     * 컨트롤러를 생성한다. / Creates the controller.
     *
     * @param service 상세 조회 서비스 / the detail service
     */
    public TalkDetailController(TalkDetailService service) {
        this.service = service;
    }

    /**
     * 거래에 속한 메시지를 조회한다. / Reads the messages under a transaction.
     *
     * @param trdd        거래일자 / the transaction date
     * @param serial      거래고유번호 / the transaction serial
     * @param recipient   수신번호 부분 일치 / the recipient substring
     * @param status      상태 코드 / the status code
     * @param talkResult  톡결과 구분 {@code SUCCESS}/{@code FAILURE}/{@code PENDING} / the talk-result filter
     * @param smsResult   문자결과 구분 / the SMS-result filter
     * @param page        페이지 번호 / the page number
     * @param size        페이지 크기 / the page size
     * @param request     출처 IP 확보용 / for the source address
     * @return 한 페이지 / one page
     */
    // req: FR-TLKD-001, FR-TLKD-002, FR-TLKD-003, FR-TLKD-006, FR-TLKD-007
    @GetMapping("/{trdd}/{serial}/messages")
    @PreAuthorize("hasRole('OPERATOR')")
    public TalkMessageResponse messages(
            @PathVariable String trdd,
            @PathVariable String serial,
            @RequestParam(required = false) String recipient,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String talkResult,
            @RequestParam(required = false) String smsResult,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            HttpServletRequest request) {

        PagedResult<TalkMessageRow> result = service.messages(
                new TalkDetailService.MessageQueryRequest(
                        trdd, serial, recipient, status, talkResult, smsResult, page, size),
                request.getRemoteAddr());

        return TalkMessageResponse.from(result);
    }

    /**
     * 메시지 한 건의 상세를 조회한다. / Reads one message's detail.
     *
     * <p>{@code tableType} 은 필수다. 활성과 보관은 서로 다른 테이블이며, 어느 쪽인지는 목록
     * 행이 이미 알고 있다. 서버가 둘을 다 찾아보게 만들면 조회가 두 배가 되고, 어느 쪽에서
     * 찾았는지가 응답에 나타나지 않는다.</p>
     * <p>{@code tableType} is required: live and archive are different tables and the list row already knows
     * which. Making the server try both would double the lookups and hide which one answered.</p>
     *
     * @param trdd       거래일자 / the transaction date
     * @param serial     거래고유번호 / the transaction serial
     * @param messageKey 메시지키 / the message key
     * @param tableType  {@code QUE} 또는 {@code LOG} / live or archive
     * @param request    출처 IP 확보용 / for the source address
     * @return 상세 / the detail
     */
    // req: FR-AZ-T04, FR-TLKM-001…007
    @GetMapping("/{trdd}/{serial}/messages/{messageKey}")
    @PreAuthorize("hasRole('OPERATOR')")
    public TalkMessageDetailResponse detail(
            @PathVariable String trdd,
            @PathVariable String serial,
            @PathVariable String messageKey,
            @RequestParam String tableType,
            HttpServletRequest request) {

        TalkMessageDetail detail = service.detail(
                new TalkDetailService.DetailQueryRequest(trdd, serial, messageKey, tableType),
                request.getRemoteAddr());

        return TalkMessageDetailResponse.from(detail);
    }

    /**
     * 결과 구분 선택지를 반환한다. / Returns the result-filter options.
     *
     * <p><b>미수신이 선택지에 있는 것이 D-T22 의 수정이다.</b> 레거시 화면 32 의 톡결과 필터는
     * 성공과 실패뿐이었고 실패는 {@code AND RSLT != '0'} 이었다. {@code NULL != '0'} 은
     * UNKNOWN 이므로 결과가 아직 오지 않은 행이 <b>어느 선택지에도 나타나지 않았다</b>.</p>
     * <p><b>미수신 appearing in this list is the fix for D-T22.</b> The legacy screen-32 talk-result filter
     * offered only success and failure, and failure was {@code AND RSLT != '0'}. Since {@code NULL != '0'} is
     * UNKNOWN, a row with no result yet appeared under <b>neither option</b>.</p>
     *
     * @return 선택지 / the options
     */
    // req: FR-TLKD-006, NFR-USE-T01
    @GetMapping("/result-filters")
    @PreAuthorize("hasRole('OPERATOR')")
    public List<ResultFilterOption> resultFilters() {
        return List.of(
                new ResultFilterOption("SUCCESS", "성공"),
                new ResultFilterOption("FAILURE", "실패"),
                new ResultFilterOption("PENDING", "미수신"));
    }

    /**
     * 결과 구분 선택지 하나. / One result-filter option.
     *
     * @param value 요청에 담는 값 / the value sent in the request
     * @param label 표시명 / the display label
     */
    // req: FR-TLKD-006
    public record ResultFilterOption(String value, String label) {
    }
}
