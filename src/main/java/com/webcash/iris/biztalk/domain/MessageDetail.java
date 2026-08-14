package com.webcash.iris.biztalk.domain;

/**
 * 문자상세내역. / Message detail.
 *
 * <h2>레거시 결함 D9 대응 / Fixes legacy defect D9</h2>
 * <p>레거시 {@code WSVC.biztalk_admin_41_l001.xml} 의 out 규칙은 <b>19개 필드</b>를
 * 선언했으나, 4개 IDO 는 모두 <b>8개만</b> 반환했다. 나머지 11개
 * ({@code PROFILE_KEY}, {@code AD_FLAG}, {@code TEMPLATE_CODE}, {@code IMG_PATH},
 * {@code IMG_URL}, {@code WI_FLAG}, {@code BUTTON_JSON}, {@code FAILED_TYPE},
 * {@code FAILED_SUBJECT}, {@code FAILED_IMG}, {@code FAILED_MSG})는 화면에 빈 칸으로
 * 남았고, 그것을 표시할 탭({@code tbl2}/{@code tbl3})의 핸들러는 주석 처리되어 있었다.</p>
 * <p>The legacy WSVC out-rule declared <b>19 fields</b> while all four IDOs returned only
 * <b>eight</b>. The other eleven stayed blank on screen, and the handlers for the tabs meant to
 * display them were commented out.</p>
 *
 * <p>PM 결정 AMB-04: <b>19개 전부 구현</b>한다. 템플릿 코드와 실패 사유는 지원 담당자가
 * 가장 필요로 하는 필드다 — 발송이 실패했을 때 "왜"를 답할 수 있는 유일한 정보다.</p>
 * <p>PM decision AMB-04: implement all 19. Template code and failure reason are what support
 * staff need most — the only information that answers "why" when a send fails.</p>
 *
 * @param resultCode      톡결과 / the delivery result code
 * @param senderNumber    발신번호, 마스킹됨 / the sender number, masked
 * @param recipientNumber 수신번호, 마스킹됨 / the recipient number, masked
 * @param requestDate     요청일시 / the request timestamp
 * @param sentDate        발송일시 / the sent timestamp
 * @param resultDate      결과일시 / the result timestamp
 * @param reportDate      응답일시 / the report timestamp
 * @param message         메시지 본문 / the message body
 * @param profileKey      발신 프로필 키 / the sender profile key
 * @param adFlag          광고 여부 / the advertising flag
 * @param templateCode    알림톡 템플릿 코드 / the 알림톡 template code
 * @param imagePath       이미지 경로 / the image path
 * @param imageUrl        이미지 URL / the image URL
 * @param wideImageFlag   와이드 이미지 여부 / the wide-image flag
 * @param buttonJson      버튼 정의 JSON / the button definition JSON
 * @param failedType      실패 유형 / the failure type
 * @param failedSubject   실패 제목 / the failure subject
 * @param failedImage     실패 이미지 / the failure image
 * @param failedMessage   실패 메시지 / the failure message
 *
 * // source: WSVC.biztalk_admin_41_l001.xml — out rule, 19 items
 * // source: IDO.KKO_SMS_MSG_L001 etc. — returned only 8
 * // req: FR-MSGD-004, FR-MSGD-006, NFR-SEC-PII
 */
public record MessageDetail(
        String resultCode,
        String senderNumber,
        String recipientNumber,
        String requestDate,
        String sentDate,
        String resultDate,
        String reportDate,
        String message,
        String profileKey,
        String adFlag,
        String templateCode,
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
     * 실패 정보가 존재하는지 반환한다. / Whether failure information is present.
     *
     * <p>실패 섹션을 표시할지 결정한다. 레거시는 이 필드들을 채우지 않았으므로 판단
     * 자체가 불가능했다(D9).</p>
     * <p>Decides whether to show the failure section. The legacy never populated these fields,
     * so the question could not be asked (D9).</p>
     *
     * @return 실패 정보 존재 여부 / true when any failure field is populated
     */
    // req: FR-MSGD-006
    public boolean hasFailureInfo() {
        return notBlank(failedType) || notBlank(failedSubject)
                || notBlank(failedMessage) || notBlank(failedImage);
    }

    /**
     * 이미지 정보가 존재하는지 반환한다. / Whether image information is present.
     *
     * @return 이미지 존재 여부 / true when an image is present
     */
    // req: FR-MSGD-006
    public boolean hasImage() {
        return notBlank(imagePath) || notBlank(imageUrl);
    }

    /**
     * 버튼 정의가 존재하는지 반환한다. / Whether button definitions are present.
     *
     * @return 버튼 존재 여부 / true when buttons are defined
     */
    // req: FR-MSGD-006
    public boolean hasButtons() {
        return notBlank(buttonJson);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
