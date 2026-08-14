package com.webcash.iris.biztalk.api;

import com.webcash.iris.biztalk.domain.MessageHistoryCriteria;
import com.webcash.iris.biztalk.domain.MessageHistoryRow;
import com.webcash.iris.biztalk.domain.MessageHistoryService;
import com.webcash.iris.biztalk.domain.MessageType;
import com.webcash.iris.biztalk.domain.PagedResult;
import com.webcash.iris.biztalk.domain.TableType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 문자내역 조회 엔드포인트. / 문자내역 query endpoint.
 *
 * <p><b>레거시 결함 D1 대응.</b> 레거시 목록 서비스는
 * {@code WSVC.biztalk_admin_40_l001.xml} 에 {@code <login>N</login>} 으로 배포되어
 * <b>미인증 호출자에게 전화번호를 포함한 조회 결과를 반환</b>했다. 신규 시스템에는 그
 * 플래그에 상응하는 것이 없다 — {@code SecurityConfig} 의 {@code anyRequest().authenticated()}
 * 가 기본이고, 이 엔드포인트는 예외 목록에 없다.</p>
 * <p><b>Fixes legacy defect D1.</b> The legacy list service shipped with
 * {@code <login>N</login>}, returning results containing phone numbers to anonymous callers.
 * There is no equivalent flag here: authentication is the default and this endpoint is not on
 * the exception list.</p>
 *
 * <p>{@code POST} 를 쓰는 이유: 조회 조건에 전화번호가 포함될 수 있다. {@code GET} 이면
 * 번호가 URL 에 실려 접근 로그·리퍼러·브라우저 히스토리에 남는다(NFR-SEC-PII).</p>
 * <p>{@code POST} because the criteria may contain a phone number: with {@code GET} it would
 * appear in the URL and thus in access logs, referrers and browser history.</p>
 *
 * // source: biztalk_admin_40.js — jex.createAjaxUtil('biztalk_admin_40_l001')
 * // req: FR-MSG-001, FR-MSG-002, FR-MSG-004, FR-MSG-007, FR-TEN-001
 */
@RestController
@RequestMapping("/api/message-history")
public class MessageHistoryController {

    private final MessageHistoryService service;

    /**
     * 컨트롤러를 생성한다. / Creates the controller.
     *
     * @param service 조회 서비스 / the query service
     */
    public MessageHistoryController(MessageHistoryService service) {
        this.service = service;
    }

    /**
     * 문자내역을 조회한다. / Searches 문자내역.
     *
     * @param body    조회 요청 / the search request
     * @param request HTTP 요청 / the HTTP request
     * @return 페이지 결과 / the page of results
     */
    // req: FR-MSG-002, FR-MSG-004, FR-MSG-007
    @PostMapping("/search")
    public ResponseEntity<MessageHistoryResponse> search(
            @Valid @RequestBody MessageHistorySearchRequest body,
            HttpServletRequest request) {

        MessageHistoryCriteria criteria = body.toCriteria();
        PagedResult<MessageHistoryRow> result =
                service.search(criteria, body.institutionCode(), request.getRemoteAddr());

        return ResponseEntity.ok(MessageHistoryResponse.from(result));
    }

    /**
     * 조회 조건 검증 실패를 400 으로 변환한다.
     * Maps criteria validation failures to 400.
     *
     * <p>위반 목록 전체를 반환한다. 레거시 화면은 시각 비교 실패 시
     * {@code alert("시작시간이 종료시간보다 클 수 없습니다.")} 하나만 보여주었고, 그 판정
     * 자체가 잘못되어 정상 범위를 거절했다(D8).</p>
     * <p>Every violation is returned. The legacy showed a single alert, and its comparison was
     * itself wrong — refusing legitimate ranges (D8).</p>
     *
     * @param e 검증 예외 / the validation exception
     * @return 400 응답 / a 400 response
     */
    // source: biztalk_admin_40.js — alert("시작시간이 종료시간보다 클 수 없습니다.")
    // req: FR-MSG-012, FR-MSG-013
    @ExceptionHandler(MessageHistoryCriteria.CriteriaException.class)
    public ResponseEntity<Map<String, Object>> handleCriteria(
            MessageHistoryCriteria.CriteriaException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("code", "INVALID_CRITERIA", "violations", e.violations()));
    }

    /**
     * 조회 응답. / The search response.
     *
     * @param rows        이 페이지의 행 / the rows on this page
     * @param totalCount  전체 건수 / the total matching count
     * @param page        페이지 번호 / the page number
     * @param size        페이지 크기 / the page size
     * @param totalPages  전체 페이지 수 / the page count
     *
     * // req: FR-MSG-004, FR-MSG-007
     */
    public record MessageHistoryResponse(
            List<Row> rows, int totalCount, int page, int size, int totalPages) {

        /**
         * 도메인 결과를 응답으로 변환한다. / Converts a domain result to a response.
         *
         * @param result 도메인 결과 / the domain result
         * @return 응답 / the response
         */
        public static MessageHistoryResponse from(PagedResult<MessageHistoryRow> result) {
            return new MessageHistoryResponse(
                    result.rows().stream().map(Row::from).toList(),
                    result.totalCount(), result.page(), result.size(), result.totalPages());
        }

        /**
         * 응답 1행 — 레거시 그리드 12개 컬럼에 대응한다.
         * One response row, matching the legacy grid's twelve columns.
         *
         * <p>전화번호 두 필드는 <b>DB 에서 이미 마스킹된 값</b>이다. 서버는 평문을 보유하지
         * 않으므로 이 응답에도 평문이 존재할 수 없다(ADR-005, NFR-SEC-PII).</p>
         * <p>Both phone fields are already masked by the database; the server never holds
         * plaintext, so none can appear here.</p>
         *
         * @param messageType     유형 코드 / the type code
         * @param messageTypeLabel 유형 라벨 / the type label
         * @param tableType       문자타입 / the table type
         * @param messageKey      메시지키 / the message key
         * @param institutionCode 이용기관 / the 이용기관 code
         * @param status          상태 코드 / the status code
         * @param statusLabel     상태 라벨 / the status label
         * @param resultCode      톡결과 / the delivery result
         * @param senderNumber    발송번호, 마스킹됨 / the sender number, masked
         * @param recipientNumber 수신번호, 마스킹됨 / the recipient number, masked
         * @param requestDate     요청일시 / the request timestamp
         * @param requestTime     요청시간 / the request time-of-day
         * @param sentTime        발송시간 / the sent time-of-day
         * @param reportTime      응답시간 / the report time-of-day
         */
        // source: biztalk_admin_40.js — gridColName
        // req: FR-MSG-004, FR-MSG-005, NFR-SEC-PII
        public record Row(
                String messageType,
                String messageTypeLabel,
                String tableType,
                Long messageKey,
                String institutionCode,
                String status,
                String statusLabel,
                String resultCode,
                String senderNumber,
                String recipientNumber,
                String requestDate,
                String requestTime,
                String sentTime,
                String reportTime
        ) {
            /**
             * 도메인 행을 응답 행으로 변환한다. / Converts a domain row.
             *
             * @param row 도메인 행 / the domain row
             * @return 응답 행 / the response row
             */
            public static Row from(MessageHistoryRow row) {
                return new Row(
                        row.messageTypeCode(), row.messageTypeLabel(), row.tableTypeCode(),
                        row.messageKey(), row.institutionCode(),
                        row.statusCode(), row.statusLabel(), row.resultCode(),
                        row.senderNumber(), row.recipientNumber(),
                        row.requestDate(), row.requestTime(), row.sentTime(), row.reportTime());
            }
        }
    }

    /**
     * 조회 요청. / The search request.
     *
     * <p>레거시 JS 가 보낸 페이로드와 1:1 로 대응하되, <b>필드명이 실제 컬럼 의미를
     * 반영</b>한다. 레거시는 화면 라벨과 컬럼이 어긋나 있었다:</p>
     * <table>
     *   <caption>레거시 라벨 vs 실제 컬럼 (결함 D3)</caption>
     *   <tr><th>화면 라벨</th><th>전송 필드</th><th>실제 컬럼 의미</th></tr>
     *   <tr><td>발신번호</td><td>{@code PHONE}</td><td><b>수신</b>번호</td></tr>
     *   <tr><td>수신번호</td><td>{@code CALLBACK}</td><td><b>발신</b>번호</td></tr>
     * </table>
     * <p>게다가 서버는 {@code PHONE} 을 아예 사용하지 않았으므로(D4), "수신번호"에 입력한
     * 값이 실제로는 <b>발신</b>번호를 필터링했다.</p>
     * <p>The legacy form labels and columns were crossed (D3), and since the server ignored
     * {@code PHONE} entirely (D4), a value typed into 수신번호 actually filtered the
     * <b>sender</b> column.</p>
     *
     * @param from            조회 시작 일시 / the window start
     * @param to              조회 종료 일시 / the window end
     * @param institutionCode 이용기관 코드 (운영자만 유효) / the 이용기관 code, operators only
     * @param messageKey      메시지키 / the message key
     * @param senderNumber    발송번호 (컬럼 CALLBACK) / the sender number, column CALLBACK
     * @param recipientNumber 수신번호 (컬럼 PHONE) / the recipient number, column PHONE
     * @param status          상태 코드 / the status code
     * @param messageType     유형 코드 / the type code
     * @param tableType       문자타입 코드 / the table type code
     * @param resultCode      결과 코드 / the result code
     * @param page            페이지 번호 / the page number
     * @param size            페이지 크기 / the page size
     *
     * // source: biztalk_admin_40.js — getDat() jexAjax.set(...) payload
     * // source: biztalk_admin_40_view.jsp — 발신번호→id="PHONE", 수신번호→id="CALLBACK"
     * // req: FR-MSG-002, FR-MSG-009, FR-MSG-010
     */
    public record MessageHistorySearchRequest(
            @jakarta.validation.constraints.NotNull(message = "조회 시작 일시는 필수입니다.")
            java.time.LocalDateTime from,

            @jakarta.validation.constraints.NotNull(message = "조회 종료 일시는 필수입니다.")
            java.time.LocalDateTime to,

            String institutionCode,
            Long messageKey,
            String senderNumber,
            String recipientNumber,
            String status,
            String messageType,
            String tableType,
            String resultCode,
            Integer page,
            Integer size
    ) {

        /**
         * 도메인 조회 조건으로 변환한다. / Converts to the domain criteria.
         *
         * <p>인식할 수 없는 유형·문자타입 코드는 <b>거절</b>한다. 레거시는 {@code MSG_TYPE}
         * 이 {@code "AT"} 가 아니면 무조건 친구톡 테이블을 조회했으므로, 오타 하나가 조용히
         * 잘못된 테이블을 읽게 만들었다(D-routing, FR-MSGD-003).</p>
         * <p>Unrecognised type codes are <b>refused</b>. The legacy routed anything that was not
         * {@code "AT"} to the 친구톡 tables, so a typo silently read the wrong table.</p>
         *
         * @return 조회 조건 / the criteria
         * @throws MessageHistoryCriteria.CriteriaException 검증 실패 시 / when validation fails
         */
        // req: FR-MSG-002, FR-MSG-006, FR-MSGD-003
        public MessageHistoryCriteria toCriteria() {
            var builder = MessageHistoryCriteria.builder()
                    .from(from)
                    .to(to)
                    .messageKey(messageKey)
                    .senderNumber(senderNumber)
                    .recipientNumber(recipientNumber)
                    .statusCode(status)
                    .resultCode(resultCode);

            if (page != null) {
                builder.page(page);
            }
            if (size != null) {
                builder.size(size);
            }

            if (messageType != null && !messageType.isBlank()) {
                builder.messageType(MessageType.fromCode(messageType).orElseThrow(
                        () -> new MessageHistoryCriteria.CriteriaException(
                                List.of("유형 코드가 올바르지 않습니다: " + messageType))));
            }
            if (tableType != null && !tableType.isBlank()) {
                builder.tableType(TableType.fromCode(tableType).orElseThrow(
                        () -> new MessageHistoryCriteria.CriteriaException(
                                List.of("문자타입 코드가 올바르지 않습니다: " + tableType))));
            }

            // institutionCode 는 여기서 설정하지 않는다. 서비스 계층이 TenantContext 에서
            // 도출한 값으로 채운다 — 요청 값을 조건에 넣으면 레거시 결함이 재현된다(TM-004).
            // institutionCode is deliberately not set here: the service fills it from
            // TenantContext. Placing the request value into the criteria would reproduce the
            // legacy defect (TM-004).
            return builder.build();
        }
    }
}
