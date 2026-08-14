package com.webcash.iris.biztalk.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * 문자타입 (테이블 종류). / Message table type.
 *
 * <p>레거시 WSVC 규칙은 이 필드를 한글 이름 {@code 테이블종류}, id {@code TABLE_TYPE} 으로
 * 정의했다. {@link MessageType} 과 조합하여 4개 테이블 쌍 중 하나를 결정한다.</p>
 * <p>The legacy WSVC rule named this field {@code 테이블종류} with id {@code TABLE_TYPE}.
 * Combined with {@link MessageType} it selects one of four table pairs.</p>
 *
 * // source: WSVC.biztalk_admin_41_l001.xml — item name="테이블종류" id="TABLE_TYPE" length="10"
 * // source: biztalk_admin_40_view.jsp — select options SMS / MMS
 * // req: FR-MSG-006, FR-MSGD-002
 */
public enum TableType {

    /** SMS / short message tables. */
    SMS("SMS"),
    /** MMS / multimedia message tables. */
    MMS("MMS");

    private final String code;

    TableType(String code) {
        this.code = code;
    }

    /**
     * DB 컬럼값을 반환한다. / Returns the database column value.
     *
     * @return 타입 코드 / the type code
     */
    public String code() {
        return code;
    }

    /**
     * 코드를 열거형으로 변환한다. 인식할 수 없으면 empty.
     * Resolves a code, returning empty when unrecognised.
     *
     * @param code 타입 코드 / the type code
     * @return 해당 타입 또는 empty / the matching type, or empty
     */
    // req: FR-MSGD-003
    public static Optional<TableType> fromCode(String code) {
        return Arrays.stream(values()).filter(t -> t.code.equals(code)).findFirst();
    }
}
