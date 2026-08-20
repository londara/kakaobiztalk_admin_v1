package com.webcash.iris.biztalk.domain;

/**
 * 메시지 상세 — 화면 31 의 세 묶음 20개 필드.
 * One message's detail: screen 31's twenty fields in three groups.
 *
 * <h2>레거시가 이 화면에서 보여주지 못한 것 / what the legacy failed to show here</h2>
 * <p>발신번호와 수신자번호는 <b>항상 비어 있었다</b>. 질의가
 * {@code decrypt(CALLBACK), decrypt(PHONE)} 을 별칭 없이 선택했으므로 PostgreSQL 이 두 출력
 * 컬럼을 모두 {@code decrypt} 로 이름 붙였고, 계약은 {@code CALLBACK}/{@code PHONE} 을
 * 기대했다 — 팝업이 존재하는 이유인 두 필드가 매핑되지 않았다(D-T18).</p>
 * <p>The sender and recipient numbers were <b>always blank</b>: the query selected
 * {@code decrypt(CALLBACK), decrypt(PHONE)} unaliased, so PostgreSQL named both output columns
 * {@code decrypt} while the contract expected {@code CALLBACK}/{@code PHONE} — the two fields the popup
 * exists to show never mapped (D-T18).</p>
 *
 * <p>그리고 톡결과·문자결과는 사전에 없는 코드일 때 <b>비었다</b> — NULL 연결이 전체를 지웠기
 * 때문이며, 그것은 운영자가 그 값을 가장 필요로 하는 경우다(D-T20). 여기서는
 * {@link TalkResult} 가 코드를 항상 보이게 한다.</p>
 * <p>And the talk and SMS results went <b>blank</b> for codes absent from the dictionary, because NULL
 * concatenation erased the expression — precisely when an operator needs the value most (D-T20). Here
 * {@link TalkResult} keeps the code always visible.</p>
 *
 * @param messageKey       메시지키 / the message key
 * @param institutionCode  이용기관 / the institution code
 * @param channel          채널 — 레지스트리가 결정 / the channel, decided by the registry
 * @param statusCode       상태 원값 / the raw status code
 * @param profileKey       프로필 / the profile key
 * @param adFlag           광고여부 / the advertising flag
 * @param talkResult       톡결과 / the talk result
 * @param smsResult        문자결과 / the SMS result
 * @param templateCode     템플릿코드 / the template code
 * @param senderNumber     발신번호 — 마스킹됨 / the sender number, masked
 * @param recipientNumber  수신자번호 — 마스킹됨 / the recipient number, masked
 * @param requestedAt      요청시간 / when requested
 * @param sentAt           송신시간 / when dispatched
 * @param carrierRepliedAt 통신사응답시간 / when the carrier replied
 * @param reportedAt       결과수신시간 / when the receipt arrived
 * @param message          전송메시지 / the message text
 * @param imagePath        이미지경로 / the image path
 * @param imageUrl         이미지URL / the image URL
 * @param wideImageFlag    와이드 이미지 여부 / the wide-image flag
 * @param buttonJson       버튼JSON / the button JSON
 * @param failedType       문자전송타입 / the failback type
 * @param failedSubject    문자전송제목 / the failback subject
 * @param failedImage      문자내이미지주소 / the failback image address
 * @param failedMessage    문자내용 / the failback message
 *
 * // source: biztalk_admin_31_view.jsp — tbl1/tbl2/tbl3; WSVC.biztalk_admin_31_l001.xml <out>
 * // req: FR-TLKM-001, FR-TLKM-002, FR-TLKM-003, FR-TLKM-005, FR-TLKM-007
 */
public record TalkMessageDetail(
        String messageKey,
        String institutionCode,
        TalkChannel channel,
        String statusCode,
        String profileKey,
        String adFlag,
        TalkResult talkResult,
        TalkResult smsResult,
        String templateCode,
        String senderNumber,
        String recipientNumber,
        String requestedAt,
        String sentAt,
        String carrierRepliedAt,
        String reportedAt,
        String message,
        String imagePath,
        String imageUrl,
        String wideImageFlag,
        String buttonJson,
        String failedType,
        String failedSubject,
        String failedImage,
        String failedMessage
) {

    /**
     * 값이 없는 필드에 표시할 표식. / The marker shown for a field with no value.
     *
     * <p>빈 문자열을 쓰지 않는 이유는 <b>"값이 없다"와 "조회가 실패했다"를 구분</b>하기
     * 위해서다. 레거시 화면 31 은 두 경우를 같은 빈 칸으로 그렸고, D-T18 이 정확히 그
     * 구분 불가 상태였다 — 필드가 비어 있는 것이 데이터 때문인지 매핑 실패 때문인지 알 수
     * 없었다(FR-TLKM-007).</p>
     * <p>A blank is not used so that <b>"there is no value" and "the lookup failed" stay
     * distinguishable</b>. Legacy screen 31 drew both as the same empty cell, and D-T18 was exactly that
     * indistinguishable state: no way to tell whether a field was empty because of the data or because the
     * mapping had failed (FR-TLKM-007).</p>
     */
    // req: FR-TLKM-007
    public static final String ABSENT = "(값 없음)";

    /**
     * 첨부 묶음에 표시할 내용이 있는지 반환한다. / Whether the attachment group has anything to show.
     *
     * @return 하나라도 값이 있으면 true / true when any field is present
     */
    // req: FR-TLKM-001, FR-TLKM-007
    public boolean hasAttachment() {
        return present(imagePath) || present(imageUrl) || present(wideImageFlag)
                || present(buttonJson);
    }

    /**
     * FailBack 묶음에 표시할 내용이 있는지 반환한다. / Whether the failback group has anything to show.
     *
     * <p>비어 있다는 사실 자체가 정보다 — 알림톡이 성공했으면 문자 대체 발송이 없으므로 이
     * 묶음이 빈 것이 정상이다. 그래서 <b>탭을 숨기지 않고</b> 비어 있음을 표시한다.</p>
     * <p>Emptiness is itself informative: a successful 알림톡 needs no SMS failback, so an empty group is
     * the normal case. The tab is therefore <b>not hidden</b> but shown as empty.</p>
     *
     * @return 하나라도 값이 있으면 true / true when any field is present
     */
    // req: FR-TLKM-001, FR-TLKM-007
    public boolean hasFailback() {
        return present(failedType) || present(failedSubject) || present(failedImage)
                || present(failedMessage);
    }

    /**
     * 표시용 값을 반환한다. 값이 없으면 표식을 반환한다.
     * Returns a value for display, or the marker when absent.
     *
     * @param value 원값 / the raw value
     * @return 값 또는 표식 / the value, or the marker
     */
    // req: FR-TLKM-007
    public static String display(String value) {
        return present(value) ? value : ABSENT;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
