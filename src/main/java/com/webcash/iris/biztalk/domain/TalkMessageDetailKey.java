package com.webcash.iris.biztalk.domain;

/**
 * 메시지 상세를 지목하는 키 — 기관을 포함하고 가변 컬럼을 포함하지 않는다.
 * The key identifying one message for detail lookup: institution-qualified, and free of mutable columns.
 *
 * <h2>레거시 키가 틀린 두 가지 방식 / two ways the legacy key was wrong</h2>
 *
 * <p><b>첫째, 기관이 없었다.</b> {@code IDO.KKO_MSG_L002} 의 술어는
 * {@code REQDATE} + {@code STATUS} + {@code MSGKEY} 뿐이다. 인증된 아무 주체나 메시지 키를
 * 알거나 추측하면 <b>다른 기관의 메시지 본문·템플릿코드·발신·수신번호</b>를 읽었다
 * (D-T5, CVSS 6.5). 이 슬라이스에서 가장 민감한 노출이다 — 메시지 본문은 특정 개인에게
 * 실제로 보낸 문장이다.</p>
 * <p><b>First, no institution.</b> {@code IDO.KKO_MSG_L002}'s predicate is {@code REQDATE} +
 * {@code STATUS} + {@code MSGKEY} alone, so any authenticated principal holding or guessing a message key
 * read <b>another institution's message body, template code, sender and recipient number</b> (D-T5,
 * CVSS 6.5). It is the slice's most sensitive disclosure: a message body is the text actually sent to a
 * named individual.</p>
 *
 * <p><b>둘째, 가변 컬럼이 키에 있었다.</b> {@code AND STATUS = :STATUS} 때문에, 목록을 그린
 * 뒤 상태가 진행되면 상세 조회가 <b>0건</b>을 반환하고 팝업이 아무 설명 없이 비었다(D-T19).
 * 발송 파이프라인이 상태를 바꾸는 것은 정상 동작이므로, 이 결함은 <b>시스템이 정상일 때</b>
 * 발생한다.</p>
 * <p><b>Second, a mutable column was part of the key.</b> {@code AND STATUS = :STATUS} meant that if the
 * status advanced after the list was drawn, the detail lookup returned <b>zero rows</b> and the popup went
 * blank with no explanation (D-T19). The send pipeline advancing a status is normal operation, so this
 * defect fires <b>when the system is working</b>.</p>
 *
 * @param institutionCode 서버가 도출한 이용기관 / the institution, derived on the server
 * @param messageKey      메시지키 / the message key
 * @param channel         레지스트리가 결정한 채널 / the channel, decided by the registry
 * @param tableType       {@code QUE} 또는 {@code LOG} / live or archive
 *
 * // source: IDO.KKO_MSG_L002 — WHERE to_char(REQDATE,…) = :REQDATE AND STATUS = :STATUS AND MSGKEY = …
 * // req: FR-AZ-T04, FR-TLKM-004, FR-TLKM-006, CONST-BIZ-T01
 */
public record TalkMessageDetailKey(
        String institutionCode,
        String messageKey,
        TalkChannel channel,
        String tableType
) {

    /** 활성 테이블. / The live table. */
    public static final String TABLE_LIVE = "QUE";

    /** 보관 테이블. / The archive table. */
    public static final String TABLE_ARCHIVE = "LOG";

    /**
     * 키를 만든다. / Creates the key.
     *
     * @param institutionCode 서버가 도출한 이용기관 / the server-derived institution
     * @param messageKey      메시지키 / the message key
     * @param channel         채널 / the channel
     * @param tableType       {@code QUE}/{@code LOG} / live or archive
     * @return 검증된 키 / the validated key
     * @throws IllegalArgumentException 필수 값 누락 또는 알 수 없는 테이블 구분 / on a missing value or unknown table type
     */
    // req: FR-AZ-T04, FR-TLKM-004
    public static TalkMessageDetailKey of(String institutionCode, String messageKey,
                                          TalkChannel channel, String tableType) {
        if (institutionCode == null || institutionCode.isBlank()) {
            // 서버가 도출하지 못했으면 조회하지 않는다. null 을 그대로 넘기면 매퍼가 기관
            // 조건을 만들지 않아 D-T5 가 그대로 재현된다 — 통제가 조용히 뒤집히는 경로다.
            // Without a server-derived institution there is no query. Passing null would suppress the
            // mapper's predicate and reproduce D-T5 exactly — the control inverting silently.
            throw new IllegalArgumentException(
                    "이용기관을 확정할 수 없어 메시지 상세를 조회하지 않습니다. / "
                            + "The institution could not be established; refusing the message detail.");
        }
        if (messageKey == null || messageKey.isBlank()) {
            throw new IllegalArgumentException(
                    "메시지키는 필수입니다. / The message key is required.");
        }
        if (channel == null) {
            throw new IllegalArgumentException(
                    "채널은 레지스트리가 결정해야 합니다. / The channel must come from the registry.");
        }
        String table = (tableType == null) ? "" : tableType.trim().toUpperCase();
        if (!TABLE_LIVE.equals(table) && !TABLE_ARCHIVE.equals(table)) {
            throw new IllegalArgumentException(
                    "테이블 구분은 QUE 또는 LOG 여야 합니다: '" + tableType + "' / "
                            + "The table type must be QUE or LOG: '" + tableType + "'");
        }
        return new TalkMessageDetailKey(
                institutionCode.trim(), messageKey.trim(), channel, table);
    }

    /**
     * 보관 테이블을 가리키는지 반환한다. / Whether this key points at the archive.
     *
     * @return 보관이면 true / true for the archive
     */
    // req: FR-TLKM-006
    public boolean archive() {
        return TABLE_ARCHIVE.equals(tableType);
    }

    /**
     * 감사 기록에 담을 설명을 반환한다. / Returns a description for the audit record.
     *
     * @return 설명 / the description
     */
    // req: FR-AZ-T05
    public String describe() {
        return institutionCode + "/" + channel.code() + "/" + tableType + "/" + messageKey;
    }
}
