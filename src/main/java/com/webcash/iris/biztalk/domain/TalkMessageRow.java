package com.webcash.iris.biztalk.domain;

/**
 * 거래 상세내역 한 행 — 화면 32 가 바인딩하는 14개 컬럼.
 * One 거래 상세내역 row: the fourteen columns screen 32 binds.
 *
 * <h2>{@code channel} 이 행에서 오지 않는 이유 / why {@code channel} does not come from the row</h2>
 * <p>레거시 {@code IDO.KKB_FT_MSG_L001} 은 알림톡 질의를 복사하고
 * {@code 'AT' AS MSG_TYPE} 를 고치지 않아 <b>모든 친구톡 행이 자신을 알림톡이라고
 * 보고</b>했다(D-T7). 화면 32 의 유형 컬럼이 틀린 것은 보이는 절반이고, 화면 31 이
 * <b>그 값으로 테이블을 골랐기</b> 때문에 친구톡 메시지 상세가 {@code KKO_MSG} 를 조회하고
 * 아무것도 반환하지 않은 것이 보이지 않는 절반이다.</p>
 * <p>The legacy {@code IDO.KKB_FT_MSG_L001} was copied from the 알림톡 query with
 * {@code 'AT' AS MSG_TYPE} left unchanged, so <b>every 친구톡 row reported itself as 알림톡</b> (D-T7).
 * The wrong 유형 column on screen 32 is the visible half; the invisible half is that screen 31
 * <b>chose its table from that value</b>, so 친구톡 message detail queried {@code KKO_MSG} and returned
 * nothing.</p>
 *
 * <p>여기서 {@code channel} 은 <b>레지스트리가 결정</b>해 서비스가 채운다. 행이 스스로 무엇이라
 * 말하든 어떤 분기도 그것을 읽지 않는다.</p>
 * <p>Here {@code channel} is <b>decided by the registry</b> and filled in by the service. Whatever a row
 * says about itself, nothing branches on it.</p>
 *
 * @param channel         채널 — 레지스트리가 결정 / the channel, decided by the registry
 * @param transactionNo   거래번호 / the transaction serial
 * @param messageKey      메시지키 / the message key
 * @param institutionCode 이용기관 / the institution code
 * @param statusCode      상태 {@code STATUS} 원값 / the raw status code
 * @param talkResult      톡결과 / the talk result
 * @param smsResult       문자결과 / the SMS result
 * @param senderNumber    발송번호 — 마스킹됨 / the sender number, masked
 * @param recipientNumber 수신번호 — 마스킹됨 / the recipient number, masked
 * @param requestDate     요청일자 / the request date
 * @param requestTime     요청시간 / the request time
 * @param sentTime        발송시간 / the dispatch time
 * @param reportTime      응답시간 / the receipt time
 * @param tableType       테이블 {@code QUE}/{@code LOG} / live or archive
 *
 * // source: biztalk_admin_32.js — drawGrid() 14 colDefs
 * // req: FR-TLKD-001, FR-TLKD-004, FR-TLKD-008
 */
public record TalkMessageRow(
        TalkChannel channel,
        String transactionNo,
        String messageKey,
        String institutionCode,
        String statusCode,
        TalkResult talkResult,
        TalkResult smsResult,
        String senderNumber,
        String recipientNumber,
        String requestDate,
        String requestTime,
        String sentTime,
        String reportTime,
        String tableType
) {

    /**
     * 상태 라벨을 반환한다. / Returns the status label.
     *
     * <p>레거시 화면 32 는 이미 {@code 라벨(코드)} 형태로 보여주었다 — 이 슬라이스에서 드물게
     * 레거시가 옳았던 자리이므로 그대로 유지한다.</p>
     * <p>Legacy screen 32 already showed {@code label(code)} — a rare place where the legacy was right,
     * so it is kept.</p>
     *
     * @return 라벨과 코드 / the label with the code
     */
    // req: NFR-USE-T01
    public String statusDisplay() {
        return MessageStatus.labelOrRaw(statusCode) + " (" + statusCode + ")";
    }

    /**
     * 아직 어떤 결과도 도착하지 않았는지 반환한다. / Whether no result of either kind has arrived.
     *
     * @return 둘 다 미수신이면 true / true when both results are pending
     */
    // req: FR-TLKD-006
    public boolean fullyPending() {
        return talkResult.pending() && smsResult.pending();
    }
}
