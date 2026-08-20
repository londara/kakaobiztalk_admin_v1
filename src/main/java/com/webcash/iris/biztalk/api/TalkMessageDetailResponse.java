package com.webcash.iris.biztalk.api;

import com.webcash.iris.biztalk.domain.MessageStatus;
import com.webcash.iris.biztalk.domain.TalkMessageDetail;

/**
 * 메시지 상세 응답 — 화면 31. / The message-detail response: screen 31.
 *
 * <h2>모든 필드가 값 또는 표식을 갖는다 / every field carries a value or a marker</h2>
 * <p>레거시 화면 31 은 값이 없는 것과 조회가 실패한 것을 <b>같은 빈 칸</b>으로 그렸다. 그리고
 * D-T18 때문에 발신번호와 수신자번호는 <b>항상</b> 비어 있었으므로, 운영자는 그 두 칸이 왜
 * 비었는지 알 방법이 없었다 — 데이터가 없어서인지, 매핑이 실패해서인지.</p>
 * <p>Legacy screen 31 drew "no value" and "the lookup failed" as the <b>same empty cell</b>. And because of
 * D-T18 the sender and recipient numbers were <b>always</b> blank, so an operator had no way to tell why —
 * absent data, or a failed mapping.</p>
 *
 * <p>여기서는 값이 없으면 {@link TalkMessageDetail#ABSENT} 표식이 나간다. 빈 문자열이 응답에
 * 나타나지 않으므로, 화면에서 빈 칸을 보면 그것은 <b>렌더링 문제</b>이지 데이터 문제가 아니다 —
 * 두 원인이 구분 가능해진다.</p>
 * <p>Here an absent value ships the {@link TalkMessageDetail#ABSENT} marker. No empty string appears in the
 * response, so a blank cell on screen is a <b>rendering</b> problem rather than a data one: the two causes
 * become distinguishable.</p>
 *
 * @param messageKey       메시지키 / the message key
 * @param institutionCode  이용기관 / the institution code
 * @param channelCode      채널 코드 / the channel code
 * @param channelLabel     채널 표시명 / the channel label
 * @param statusDisplay    상태 표시 / the status display
 * @param profileKey       프로필 / the profile key
 * @param adFlag           광고여부 / the advertising flag
 * @param talkResult       톡결과 표시 / the talk-result display
 * @param smsResult        문자결과 표시 / the SMS-result display
 * @param templateCode     템플릿코드 / the template code
 * @param senderMasked     발신번호 — 마스킹됨 / the sender number, masked
 * @param recipientMasked  수신자번호 — 마스킹됨 / the recipient number, masked
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
 * @param hasAttachment    첨부에 표시할 내용이 있는지 / whether the attachment group has content
 * @param hasFailback      FailBack 에 표시할 내용이 있는지 / whether the failback group has content
 *
 * // req: FR-TLKM-001, FR-TLKM-002, FR-TLKM-005, FR-TLKM-007, NFR-SEC-PII-T01
 */
public record TalkMessageDetailResponse(
        String messageKey,
        String institutionCode,
        String channelCode,
        String channelLabel,
        String statusDisplay,
        String profileKey,
        String adFlag,
        String talkResult,
        String smsResult,
        String templateCode,
        String senderMasked,
        String recipientMasked,
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
        String failedMessage,
        boolean hasAttachment,
        boolean hasFailback
) {

    /**
     * 도메인 상세를 응답으로 변환한다. / Converts a domain detail into a response.
     *
     * @param detail 도메인 상세 / the domain detail
     * @return 응답 / the response
     */
    // req: FR-TLKM-001, FR-TLKM-007
    public static TalkMessageDetailResponse from(TalkMessageDetail detail) {
        return new TalkMessageDetailResponse(
                detail.messageKey(),
                detail.institutionCode(),
                detail.channel().code(),
                detail.channel().label(),
                MessageStatus.labelOrRaw(detail.statusCode()) + " (" + detail.statusCode() + ")",
                TalkMessageDetail.display(detail.profileKey()),
                TalkMessageDetail.display(detail.adFlag()),
                // 코드가 항상 보인다. 레거시는 사전에 없는 코드일 때 NULL 전파로 이 칸을 비웠고,
                // 그것은 운영자가 그 값을 가장 필요로 하는 경우였다(D-T20).
                // The code is always visible. The legacy blanked this cell for codes absent from the
                // dictionary via NULL propagation — the case an operator needs most (D-T20).
                detail.talkResult().display(),
                detail.smsResult().display(),
                TalkMessageDetail.display(detail.templateCode()),
                // D-T18: 레거시에서는 이 두 칸이 항상 비어 있었다 — 별칭 없는 decrypt() 두 개가
                // 같은 출력 컬럼 이름으로 충돌했다.
                // D-T18: these two cells were always blank in the legacy — two unaliased decrypt() calls
                // collided on one output column name.
                TalkMessageDetail.display(detail.senderNumber()),
                TalkMessageDetail.display(detail.recipientNumber()),
                TalkMessageDetail.display(detail.requestedAt()),
                TalkMessageDetail.display(detail.sentAt()),
                TalkMessageDetail.display(detail.carrierRepliedAt()),
                TalkMessageDetail.display(detail.reportedAt()),
                TalkMessageDetail.display(detail.message()),
                TalkMessageDetail.display(detail.imagePath()),
                TalkMessageDetail.display(detail.imageUrl()),
                TalkMessageDetail.display(detail.wideImageFlag()),
                TalkMessageDetail.display(detail.buttonJson()),
                TalkMessageDetail.display(detail.failedType()),
                TalkMessageDetail.display(detail.failedSubject()),
                TalkMessageDetail.display(detail.failedImage()),
                TalkMessageDetail.display(detail.failedMessage()),
                detail.hasAttachment(),
                detail.hasFailback());
    }
}
