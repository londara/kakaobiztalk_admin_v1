package com.webcash.iris.biztalk.alimtalk.api;

import java.util.List;

/**
 * 화면이 보내는 작성 요청 — <b>발신프로필키가 없다</b>.
 * The composition request the screen sends, <b>carrying no profile key</b>.
 *
 * <h2>레거시 화면과 필드가 다른 이유 / why the fields differ from the legacy screen</h2>
 * <p>레거시 화면 61 의 단건 발송 폼은 열여섯 개 입력을 갖고 있었고, 그중 셋은 그대로 옮길 수
 * 없다.</p>
 * <p>The legacy single-send form had sixteen inputs; three of them cannot be carried across as they
 * were.</p>
 *
 * <table>
 *   <caption>옮기지 않은 입력 / inputs not carried across</caption>
 *   <tr><th>레거시 입력</th><th>이유</th></tr>
 *   <tr><td>{@code sender_key} (발신프로필키)</td>
 *       <td><b>이 타입에 필드가 없다.</b> 프로파일키는 기관을 대신해 발송할 권한 그 자체이고,
 *           화면에 입력란이 있다는 것은 그 권한이 사람들 사이에서 복사·붙여넣기로 돌아다닌다는
 *           뜻이다(D-A24). 서버가 이용기관으로부터 해결한다 — 잊을 수 있는 규칙이 아니라 타입이
 *           막는다(FR-AZ-A05, T-A24)</td></tr>
 *   <tr><td>{@code msg_type} (메시지타입)</td>
 *       <td>계약에 존재하지 않는 필드다(D-A2). 화면에는 남기되 payload 에는 담기지 않는다</td></tr>
 *   <tr><td>{@code kko_header}·{@code highlight}·{@code items}·{@code summary}</td>
 *       <td>아이템리스트형 필드. 계약이 선언하지 않으므로 벤더 명세 확보 전까지 담을 수 없다
 *           (D-A2, AMB-A05)</td></tr>
 * </table>
 *
 * <p>나머지 열두 개는 그대로 옮긴다 — 레거시 화면의 입력 순서와 이름도 유지한다.</p>
 * <p>The remaining twelve are carried across unchanged, keeping the legacy screen's order and names.</p>
 *
 * @param isCd           이용기관코드 / institution code
 * @param tranId         거래고유번호 / transaction id
 * @param recipients     수신번호 원문 입력 / raw recipient input
 * @param senderNumber   발신번호 / caller ID
 * @param reqdate        예약발송시간 / scheduled despatch time
 * @param templateCode   템플릿코드 / template code
 * @param templateTitle  강조표기 제목 / emphasis title
 * @param msg            메시지 / message body
 * @param buttons        버튼 / buttons
 * @param failback       실패 시 대체 전송 / fallback
 *
 * // source: biztalk_admin_61_view.jsp — single-panel form-group inputs
 * // req: FR-ATC-001, FR-ATC-002, FR-ATC-003, FR-AZ-A05
 */
public record AlimTalkComposeRequest(
        String isCd,
        String tranId,
        String recipients,
        String senderNumber,
        String reqdate,
        String templateCode,
        String templateTitle,
        String msg,
        List<ButtonInput> buttons,
        FailbackInput failback) {

    /**
     * 버튼 입력. / A button input.
     *
     * @param name          버튼명 / label
     * @param type          버튼 유형 / type
     * @param urlMobile     모바일 URL / mobile URL
     * @param urlPc         PC URL / PC URL
     * @param schemeIos     iOS 스킴 / iOS scheme
     * @param schemeAndroid Android 스킴 / Android scheme
     *
     * // source: biztalk_admin_61.js — button-group header + updateButtonDetailSection
     * // req: FR-ATC-002, FR-ATC-009
     */
    public record ButtonInput(
            String name,
            String type,
            String urlMobile,
            String urlPc,
            String schemeIos,
            String schemeAndroid) {
    }

    /**
     * 대체 전송 입력. / A fallback input.
     *
     * <p>{@code type} 이 비어 있으면 대체 전송을 쓰지 않는다는 뜻이다 — 레거시 UI 의
     * {@code NO} 에 해당하며, 그 경우 payload 에 {@code failback_data} 블록 자체가 없다.</p>
     * <p>A blank {@code type} means no fallback — the legacy UI's {@code NO} — and the payload then
     * carries no {@code failback_data} block at all.</p>
     *
     * @param type    대체 전송 유형 / fallback type
     * @param subject 제목 / subject
     * @param msg     대체 전송 메시지 / fallback body
     * @param imgId   이미지 ID / image id
     *
     * // source: biztalk_admin_61.js — failback assembly
     * // req: FR-ATC-002
     */
    public record FailbackInput(String type, String subject, String msg, String imgId) {
    }
}
