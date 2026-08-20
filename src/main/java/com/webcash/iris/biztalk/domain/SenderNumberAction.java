package com.webcash.iris.biztalk.domain;

/**
 * {@code KKB_DPNO_HIS.ACN} 행위 코드. / The {@code KKB_DPNO_HIS.ACN} action code.
 *
 * <p>레거시는 이 값을 호출 지점마다 문자열 리터럴로 넘겼고, 실제로 쓰인 값은 {@code 'C'} 와
 * {@code 'D'} 둘뿐이었다. 설명 수정은 이력을 <b>아예 쓰지 않았다</b> — 그래서 세 번째 값이
 * 존재하지 않는다(D-S10). 열거형으로 두면 세 번째 값을 추가하는 일이 한 곳에서 끝나고,
 * 오타가 컴파일 시점에 드러난다.</p>
 * <p>The legacy passed this as a string literal at each call site, and only {@code 'C'} and
 * {@code 'D'} were ever used: a description change wrote <b>no history at all</b>, which is why
 * there is no third value (D-S10). An enum keeps adding that third value to one place and makes a
 * typo a compile error.</p>
 *
 * // source: biztalk_admin_12_c001_act.jsp — put("ACN","C"); biztalk_admin_10_d001_act.jsp — put("ACN","D")
 * // req: FR-SNDH-001, FR-SNDH-003
 */
public enum SenderNumberAction {

    /** 등록 / registered. */
    // source: biztalk_admin_12_c001_act.jsp
    // req: FR-SNDC-001, FR-SNDH-001
    CREATE("C"),

    /** 삭제 / deleted. */
    // source: biztalk_admin_10_d001_act.jsp
    // req: FR-SNDD-004, FR-SNDH-001
    DELETE("D");

    private final String code;

    SenderNumberAction(String code) {
        this.code = code;
    }

    /**
     * 저장되는 코드를 반환한다. / Returns the stored code.
     *
     * @return {@code ACN} 값 / the {@code ACN} value
     */
    // req: FR-SNDH-001
    public String code() {
        return code;
    }
}
