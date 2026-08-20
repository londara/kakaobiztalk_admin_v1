package com.webcash.iris.biztalk.alimtalk.api;

import java.util.List;

/**
 * 다건 작성 요청. / A batch composition request.
 *
 * <h2>순번을 클라이언트가 보내지 않는 이유 / why the client does not send order</h2>
 * <p>FR-ATC-004 는 순번을 <b>시스템이</b> 부여하도록 정한다. 레거시가 {@code order} 를 아예
 * 내보내지 않아(D-A3) 수신자와 메시지의 대응이 <b>배열 위치</b>에 의존했고, 그 위치는 시스템
 * 경계를 넘어가면 보장되지 않는다. 클라이언트가 순번을 보내게 하면 같은 취약함이 형태만
 * 바꿔 돌아온다 — 브라우저가 매긴 번호를 서버가 믿어야 하기 때문이다.</p>
 * <p>FR-ATC-004 requires the <b>system</b> to assign the order. The legacy emitted no {@code order} at
 * all (D-A3), so recipient-to-message association depended on <b>array position</b>, which is not
 * guaranteed across a system boundary. Letting the client send the order returns the same weakness in
 * another shape: the server would have to trust a number the browser chose.</p>
 *
 * @param isCd  이용기관코드 / institution code
 * @param tranId 거래고유번호 — 배치 전체에 하나 / transaction id, one per batch
 * @param items 메시지 데이터 / the message items
 *
 * // source: biztalk_admin_61_view.jsp — multi-panel; biztalk_admin_61.js — addMsgData
 * // req: FR-ATC-001, FR-ATC-004, FR-ATC-007, FR-AZ-A05
 */
public record AlimTalkBatchComposeRequest(String isCd, String tranId, List<ItemInput> items) {

    /**
     * 메시지 데이터 한 건. / One message item.
     *
     * <p>레거시 다건 폼과 같은 입력을 갖되 두 가지가 다르다. {@code sender_key} 는 없고
     * (FR-AZ-A05), {@code reqdate} 는 <b>있다</b> — 계약이 항목마다 선언하는데도 레거시 화면이
     * 수집하지 않아 다건 예약 발송이 불가능했다(D-A14). 설계 결정이 아니라 누락이었다.</p>
     * <p>The same inputs as the legacy batch form with two differences: no {@code sender_key}
     * (FR-AZ-A05), and {@code reqdate} <b>is</b> present — the contract declares it per item, but the
     * legacy screen never collected it, so batch reservation was impossible (D-A14). An omission, not a
     * design decision.</p>
     *
     * @param recipient     수신번호 / recipient
     * @param senderNumber  발신번호 / caller ID
     * @param reqdate       예약발송시간 / scheduled despatch time
     * @param templateCode  템플릿코드 / template code
     * @param templateTitle 강조표기 제목 / emphasis title
     * @param msg           메시지 / message body
     * @param buttons       버튼 / buttons
     * @param failback      실패 시 대체 전송 / fallback
     *
     * // req: FR-ATC-004, FR-ATC-007
     */
    public record ItemInput(
            String recipient,
            String senderNumber,
            String reqdate,
            String templateCode,
            String templateTitle,
            String msg,
            List<AlimTalkComposeRequest.ButtonInput> buttons,
            AlimTalkComposeRequest.FailbackInput failback) {
    }
}
