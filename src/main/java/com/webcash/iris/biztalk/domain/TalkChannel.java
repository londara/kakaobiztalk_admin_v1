package com.webcash.iris.biztalk.domain;

/**
 * 톡 채널 — 거래의 API 서비스로부터 결정된다. / The talk channel, decided from a transaction's API service.
 *
 * <h2>왜 행이 스스로 말하는 값을 쓰지 않는가 / why the row's own claim is not used</h2>
 * <p>레거시는 채널을 <b>메시지 행이 스스로 보고한 {@code MSG_TYPE}</b> 으로 판단했다. 그리고
 * {@code IDO.KKB_FT_MSG_L001} 은 알림톡 질의를 복사한 뒤 {@code 'AT' AS MSG_TYPE} 을 고치지
 * 않아, <b>모든 친구톡 행이 자신을 알림톡이라고 보고</b>했다. 화면 32 의 유형 컬럼이 틀린
 * 것은 눈에 보이는 절반이고, 보이지 않는 절반이 더 나쁘다 —
 * {@code biztalk_admin_31_l001_act.jsp} 가 <b>그 값으로 테이블을 골랐기 때문에</b> 친구톡
 * 메시지 상세는 {@code KKO_MSG} 를 조회하고 아무것도 반환하지 않았다(D-T7).</p>
 * <p>The legacy decided the channel from <b>the message row's own {@code MSG_TYPE}</b>. And
 * {@code IDO.KKB_FT_MSG_L001} was copied from the 알림톡 query with {@code 'AT' AS MSG_TYPE} left
 * unchanged, so <b>every 친구톡 row reported itself as 알림톡</b>. The wrong 유형 column on screen
 * 32 is the visible half; the invisible half is worse — {@code biztalk_admin_31_l001_act.jsp}
 * <b>chose its table from that value</b>, so 친구톡 message detail queried {@code KKO_MSG} and
 * returned nothing (D-T7).</p>
 *
 * <p>하나의 고쳐지지 않은 리터럴이 두 화면을 틀리게 만들었다. 그래서 이 열거형은
 * {@link TalkDetailRegistry} 를 통해 <b>거래의 API 서비스 코드에서만</b> 얻어진다. 행이
 * 스스로 보고하는 유형은 표시 데이터일 뿐이고(FR-TLKD-004), 어떤 분기도 그것을 읽지
 * 않는다(FR-TLKM-006). 리터럴이 다시 돌아와도 2차 효과는 재현될 수 없다.</p>
 * <p>One unchanged literal made two screens wrong. This enum is therefore only ever obtained from
 * the transaction's API service code, through {@link TalkDetailRegistry}. A row's self-reported
 * type is display data (FR-TLKD-004) and nothing branches on it (FR-TLKM-006), so even if the
 * literal returns, the second-order effect cannot.</p>
 *
 * // source: IDO.KKB_AT_MSG_L001 / IDO.KKB_FT_MSG_L001 — SELECT 'AT' AS MSG_TYPE in both
 * // source: biztalk_admin_31_l001_act.jsp — if (msgType.equals("AT")) … else …
 * // req: FR-TLKD-004, FR-TLKM-006, ADR-TLK-026
 */
public enum TalkChannel {

    /** 알림톡 — {@code KKO_MSG} 계열 / 알림톡, the {@code KKO_MSG} family. */
    ALIMTALK("AT", "알림톡", "KKO"),

    /** 친구톡 — {@code KKF_MSG} 계열 / 친구톡, the {@code KKF_MSG} family. */
    FRIENDTALK("FT", "친구톡", "KKF");

    private final String code;
    private final String label;
    private final String tablePrefix;

    TalkChannel(String code, String label, String tablePrefix) {
        this.code = code;
        this.label = label;
        this.tablePrefix = tablePrefix;
    }

    /**
     * 표시·전송용 채널 코드를 반환한다. / Returns the channel code for display and transport.
     *
     * @return {@code AT} 또는 {@code FT} / {@code AT} or {@code FT}
     */
    // req: FR-TLKD-004
    public String code() {
        return code;
    }

    /**
     * 화면 표시 라벨을 반환한다. / Returns the display label.
     *
     * @return 라벨 / the label
     */
    // req: FR-TLKD-004
    public String label() {
        return label;
    }

    /**
     * 이 채널의 테이블 접두사를 반환한다. / Returns this channel's table prefix.
     *
     * <p>매퍼가 테이블 이름을 문자열로 조립하지 않도록, 접두사는 여기서만 정의한다.
     * 매퍼는 채널로 분기하고 이름 자체는 XML 에 문자 그대로 쓴다 — 동적 테이블명은
     * SQL 주입 표면이며, 네 개뿐인 테이블에 그 위험을 감수할 이유가 없다.</p>
     * <p>Defined here so nothing assembles a table name by concatenation. Mappers branch on the
     * channel and spell the names out literally in XML: a dynamic table name is an injection
     * surface, and there is no reason to accept one for four tables.</p>
     *
     * @return {@code KKO} 또는 {@code KKF} / {@code KKO} or {@code KKF}
     */
    // req: FR-TLKM-006
    public String tablePrefix() {
        return tablePrefix;
    }
}
