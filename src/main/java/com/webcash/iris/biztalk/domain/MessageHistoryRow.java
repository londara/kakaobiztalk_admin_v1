package com.webcash.iris.biztalk.domain;

/**
 * 문자내역 목록 1행. / One row of the 문자내역 list.
 *
 * <p>레거시 그리드 12개 컬럼에 대응한다: 유형·테이블·메시지키·이용기관·상태·톡결과·
 * 발송번호·수신번호·요청일자·요청시간·발송시간·응답시간.</p>
 * <p>Maps onto the legacy grid's twelve columns.</p>
 *
 * <p><b>전화번호 두 필드는 이미 마스킹된 상태로 도착한다.</b> DB 가
 * {@code masking(decrypt(...))} 를 적용하므로 애플리케이션은 평문을 보유하지 않는다
 * (ADR-005). 이 레코드에 원본 값을 담을 필드는 존재하지 않는다 — 담을 수 없으면
 * 유출될 수도 없다.</p>
 * <p><b>Both phone fields arrive already masked</b>: the database applies
 * {@code masking(decrypt(...))}, so the application never holds plaintext (ADR-005). This
 * record has no field for the raw value — what cannot be held cannot leak.</p>
 *
 * @param institutionCode 이용기관 코드 (레거시 컬럼 ID) / the 이용기관 code, legacy column ID
 * @param messageKey      메시지키 / the message key
 * @param statusCode      상태 코드 / the status code
 * @param resultCode      톡결과 / the delivery result code
 * @param senderNumber    발송번호, 마스킹됨 / the sender number, masked
 * @param recipientNumber 수신번호, 마스킹됨 / the recipient number, masked
 * @param requestDate     요청일시 {@code YYYYMMDDHH24MISS} / request timestamp
 * @param requestTime     요청시간 {@code HH24MISS} / request time-of-day
 * @param sentTime        발송시간 {@code HH24MISS} / sent time-of-day
 * @param reportTime      응답시간 {@code HH24MISS} / report time-of-day
 * @param messageTypeCode 유형 코드 / the message type code
 * @param tableTypeCode   문자타입 코드 / the table type code
 *
 * // source: biztalk_admin_40.js — drawGrid() colDefs, IDO.KKB_MSG_L002 out rule
 * // req: FR-MSG-004, NFR-SEC-PII
 */
public record MessageHistoryRow(
        String institutionCode,
        Long messageKey,
        String statusCode,
        String resultCode,
        String senderNumber,
        String recipientNumber,
        String requestDate,
        String requestTime,
        String sentTime,
        String reportTime,
        String messageTypeCode,
        String tableTypeCode
) {

    /**
     * 상태 표시 라벨을 반환한다. / Returns the display label for the status.
     *
     * <p>미인식 코드는 원값을 그대로 반환한다. 레거시 그리드는 빈 칸을 표시했다.</p>
     * <p>An unrecognised code returns the raw value; the legacy grid showed a blank cell.</p>
     *
     * @return 라벨 또는 원값 / the label, or the raw code
     */
    // source: biztalk_admin_40.js — STATUS renderer
    // req: FR-MSG-005
    public String statusLabel() {
        return MessageStatus.labelOrRaw(statusCode);
    }

    /**
     * 유형 표시 라벨을 반환한다. / Returns the display label for the message type.
     *
     * @return 라벨 또는 원값 / the label, or the raw code
     */
    // req: FR-MSG-006
    public String messageTypeLabel() {
        return MessageType.fromCode(messageTypeCode)
                .map(MessageType::label)
                .orElse(messageTypeCode == null ? "" : messageTypeCode);
    }
}
