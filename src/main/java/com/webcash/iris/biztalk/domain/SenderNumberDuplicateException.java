package com.webcash.iris.biztalk.domain;

/**
 * 이미 등록된 발신번호. / The sender number is already registered.
 *
 * <p>중복 판정은 <b>모든 이용기관</b>에 걸쳐 이루어진다(PM 결정 AMB-S03, FR-SNDC-004).
 * 레거시 {@code KKB_DPNO_LDGR_L001} 의 조건은 {@code IS_CD = :IS_CD AND decrypt(DP_NO) = :DP_NO}
 * 였으므로 중복검사가 요청 기관 자신의 번호만 보았고, 같은 번호를 여러 기관이 나란히 등록할 수
 * 있었다(D-S9).</p>
 * <p>Uniqueness is decided across <b>every institution</b> (PM ruling AMB-S03, FR-SNDC-004). The
 * legacy predicate filtered on {@code IS_CD} as well as the number, so the duplicate check only ever
 * saw the requesting institution's own rows and the same number could be held by many (D-S9).</p>
 *
 * <h2>어느 기관이 갖고 있는지는 말하지 않는다 / the holder is not disclosed</h2>
 * <p>"그 번호는 K00123 이 갖고 있습니다" 는 <b>다른 기관의 보유 사실</b>을 응답 하나로 알려
 * 주는 것이며, 그것을 반복하면 번호를 넣어 보는 것만으로 다른 기관의 발신번호를 열거할 수 있다.
 * 레거시 중복검사가 정확히 그런 창구였다(이용기관 슬라이스의 D-I3 와 같은 부류).</p>
 * <p>"That number belongs to K00123" discloses <b>another institution's holdings</b> in one response,
 * and repeating it turns registration into an enumeration oracle. The legacy duplicate check was
 * exactly that kind of window (the same class as D-I3 in the institution slice).</p>
 *
 * <p>번호 자체도 담지 않는다 — 이 메시지는 로그로도 나간다(NFR-SEC-LOG-D01).</p>
 * <p>The number is not carried either: this message also reaches the log (NFR-SEC-LOG-D01).</p>
 *
 * // source: IDO.KKB_DPNO_LDGR_L001 — WHERE IS_CD = :IS_CD AND decrypt(DP_NO) = :DP_NO
 * // req: FR-SNDC-004, CONST-BIZ-D01, NFR-SEC-LOG-D01
 */
public class SenderNumberDuplicateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 예외를 만든다. / Creates the exception.
     */
    // req: FR-SNDC-004
    public SenderNumberDuplicateException() {
        super("이미 등록된 발신번호입니다.");
    }
}
