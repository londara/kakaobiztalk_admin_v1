package com.webcash.iris.biztalk.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * 메시지 유형. / Message type.
 *
 * <p>레거시 상세조회는 {@code MSG_TYPE} 이 {@code "AT"} 인지만 검사하고 <b>그 외 모든 값을
 * {@code else} 로 친구톡(KKF) 테이블로 라우팅</b>했다. 알 수 없는 값이 조용히 잘못된
 * 테이블을 조회하게 되는 구조였다. 열거형으로 고정하여 미인식 값은 거절한다.</p>
 * <p>The legacy detail lookup tested only whether {@code MSG_TYPE} was {@code "AT"} and routed
 * <b>every other value</b> through a bare {@code else} to the 친구톡 (KKF) tables — so an
 * unrecognised value silently queried the wrong table. Fixing the values in an enum means an
 * unrecognised one is refused.</p>
 *
 * // source: biztalk_admin_41_l001_act.jsp — `if(msgType.equals("AT")) ... else ...`
 * // source: biztalk_admin_40_view.jsp — select options 알림톡(AT) / 친구톡(FT)
 * // req: FR-MSG-006, FR-MSGD-002, FR-MSGD-003
 */
public enum MessageType {

    /** 알림톡 / Kakao 알림톡 — KKO_* tables. */
    ALIMTALK("AT", "알림톡", "KKO"),
    /** 친구톡 / Kakao 친구톡 — KKF_* tables. */
    FRIENDTALK("FT", "친구톡", "KKF");

    private final String code;
    private final String label;
    private final String tablePrefix;

    MessageType(String code, String label, String tablePrefix) {
        this.code = code;
        this.label = label;
        this.tablePrefix = tablePrefix;
    }

    /**
     * DB 컬럼값을 반환한다. / Returns the database column value.
     *
     * @return 유형 코드 / the type code
     */
    public String code() {
        return code;
    }

    /**
     * 화면 표시 라벨을 반환한다. / Returns the display label.
     *
     * @return 라벨 / the label
     */
    public String label() {
        return label;
    }

    /**
     * 테이블 접두어를 반환한다. / Returns the table prefix.
     *
     * @return {@code KKO} 또는 {@code KKF} / either {@code KKO} or {@code KKF}
     */
    // source: IDO.KKO_*/KKF_* naming
    // req: FR-MSGD-002
    public String tablePrefix() {
        return tablePrefix;
    }

    /**
     * 코드를 열거형으로 변환한다. 인식할 수 없으면 empty.
     * Resolves a code, returning empty when unrecognised.
     *
     * <p>{@code Optional} 을 반환하는 이유는 호출자가 <b>거절을 선택으로 다루도록</b>
     * 강제하기 위함이다. 기본값을 반환하면 레거시의 {@code else} 분기가 되살아난다.</p>
     * <p>Returning {@code Optional} forces the caller to handle refusal explicitly; returning
     * a default would resurrect the legacy's {@code else} branch.</p>
     *
     * @param code 유형 코드 / the type code
     * @return 해당 유형 또는 empty / the matching type, or empty
     */
    // req: FR-MSGD-003
    public static Optional<MessageType> fromCode(String code) {
        return Arrays.stream(values()).filter(t -> t.code.equals(code)).findFirst();
    }
}
