package com.webcash.iris.biztalk.api;

import com.webcash.iris.biztalk.domain.PagedResult;
import com.webcash.iris.biztalk.domain.TalkMessageRow;
import java.util.List;

/**
 * 거래 상세내역 응답 — 화면 32. / The 거래 상세내역 response: screen 32.
 *
 * <p>필드 이름에 {@code phone} 이나 {@code callback} 을 쓰지 않는다.
 * {@code TalkHistoryContractTest} 가 이 API 표면 전체에 대해 금지된 이름 조각을 훑으며,
 * 마스킹되지 않은 필드가 <b>실수로</b> 섞이는 일을 빌드에서 막는다 — 이름을 바꾼 필드는
 * 통과하므로 완벽하지 않지만, 실수로 들어오는 필드는 거의 항상 원래 이름을 그대로 쓴다.</p>
 * <p>No field is named {@code phone} or {@code callback}. {@code TalkHistoryContractTest} scans this whole
 * API surface for forbidden name fragments, so a field arriving <b>by accident</b> fails the build. It is
 * not airtight — a renamed field passes — but a field that arrives by accident almost always keeps its
 * original name.</p>
 *
 * @param rows       이 페이지의 행 / the rows on this page
 * @param totalCount 전체 건수 / the total count
 * @param page       페이지 번호 / the page number
 * @param size       페이지 크기 / the page size
 * @param totalPages 전체 페이지 수 / the total page count
 *
 * // req: FR-TLKD-001, FR-TLKD-007, FR-TLKD-008, NFR-SEC-PII-T01
 */
public record TalkMessageResponse(
        List<Row> rows,
        int totalCount,
        int page,
        int size,
        int totalPages
) {

    /**
     * 도메인 결과를 응답으로 변환한다. / Converts a domain result into a response.
     *
     * @param result 도메인 결과 / the domain result
     * @return 응답 / the response
     */
    // req: FR-TLKD-001, FR-TLKD-007
    public static TalkMessageResponse from(PagedResult<TalkMessageRow> result) {
        return new TalkMessageResponse(
                result.rows().stream().map(Row::from).toList(),
                result.totalCount(), result.page(), result.size(), result.totalPages());
    }

    /**
     * 응답 행 하나 — 화면 32 의 컬럼. / One response row: screen 32's columns.
     *
     * @param channelCode     채널 코드 — 레지스트리가 결정 / the channel code, from the registry
     * @param channelLabel    채널 표시명 / the channel label
     * @param transactionNo   거래번호 / the transaction serial
     * @param messageKey      메시지키 / the message key
     * @param institutionCode 이용기관 / the institution code
     * @param statusCode      상태 원값 / the raw status code
     * @param statusDisplay   상태 표시 / the status display
     * @param talkResult      톡결과 표시 / the talk-result display
     * @param talkOutcome     톡결과 구분 / the talk-result classification
     * @param smsResult       문자결과 표시 / the SMS-result display
     * @param smsOutcome      문자결과 구분 / the SMS-result classification
     * @param senderMasked    발송번호 — 마스킹됨 / the sender number, masked
     * @param recipientMasked 수신번호 — 마스킹됨 / the recipient number, masked
     * @param requestDate     요청일자 / the request date
     * @param requestTime     요청시간 / the request time
     * @param sentTime        발송시간 / the dispatch time
     * @param reportTime      응답시간 / the receipt time
     * @param tableType       활성/보관 / live or archive
     * @param detailAvailable 메시지 상세 가능 여부 / whether message detail can be opened
     */
    // req: FR-TLKD-001, FR-TLKD-004, FR-TLKD-006, FR-TLKD-008
    public record Row(
            String channelCode,
            String channelLabel,
            String transactionNo,
            String messageKey,
            String institutionCode,
            String statusCode,
            String statusDisplay,
            String talkResult,
            String talkOutcome,
            String smsResult,
            String smsOutcome,
            String senderMasked,
            String recipientMasked,
            String requestDate,
            String requestTime,
            String sentTime,
            String reportTime,
            String tableType,
            boolean detailAvailable
    ) {

        /**
         * 도메인 행을 응답 행으로 변환한다. / Converts a domain row into a response row.
         *
         * @param row 도메인 행 / the domain row
         * @return 응답 행 / the response row
         */
        // req: FR-TLKD-004, FR-TLKD-006
        public static Row from(TalkMessageRow row) {
            return new Row(
                    // 채널은 레지스트리가 결정한 값이다. 레거시는 친구톡 행이 스스로 알림톡이라
                    // 보고했고, 화면 31 이 그 값으로 테이블을 골라 두 화면이 함께 틀렸다(D-T7).
                    // The channel is the registry's. In the legacy a 친구톡 row reported itself as 알림톡 and
                    // screen 31 chose its table from that value, so two screens were wrong together (D-T7).
                    row.channel().code(),
                    row.channel().label(),
                    row.transactionNo(),
                    row.messageKey(),
                    row.institutionCode(),
                    row.statusCode(),
                    row.statusDisplay(),
                    row.talkResult().display(),
                    row.talkResult().outcome().name(),
                    row.smsResult().display(),
                    row.smsResult().outcome().name(),
                    row.senderNumber(),
                    row.recipientNumber(),
                    row.requestDate(),
                    row.requestTime(),
                    row.sentTime(),
                    row.reportTime(),
                    row.tableType(),
                    // 메시지 상세는 항상 열 수 있다 — 이 행이 존재한다는 것 자체가 메시지가
                    // 있다는 뜻이다. 레거시가 링크를 상태로 제한한 것이 D-T13 의 절반이었다.
                    // Message detail is always available: this row existing means the message exists.
                    // Gating the link on status was half of D-T13.
                    true);
        }
    }
}
