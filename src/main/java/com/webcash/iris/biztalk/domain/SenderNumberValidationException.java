package com.webcash.iris.biztalk.domain;

/**
 * 발신번호 쓰기 경로의 검증 실패. / A validation failure on the sender-number write path.
 *
 * <p>어느 <b>칸</b>이 어떤 <b>규칙</b>을 어겼는지 담는다. 레거시는 규칙과 무관하게
 * {@code jex.alert("등록중 오류 발생.")} 한 문장만 보여 주었고, 게다가 클라이언트 검증이
 * 존재하지 않는 요소를 검사했으므로 그 문장조차 거의 나오지 않았다(D-S11, NFR-USE-D02).</p>
 * <p>Carries which <b>field</b> broke which <b>rule</b>. The legacy showed one sentence regardless
 * of the rule — and since its client validation tested non-existent elements, even that sentence
 * rarely appeared (D-S11, NFR-USE-D02).</p>
 *
 * <p><b>제출된 값은 담지 않는다.</b> 메시지는 로그와 응답 양쪽으로 나가고, 발신번호는 로그에
 * 평문으로 남아서는 안 된다(NFR-SEC-LOG-D01). 규칙을 말하는 데 값은 필요하지 않다.</p>
 * <p><b>The submitted value is never carried.</b> The message reaches both the log and the response,
 * and a sender number must not appear in a log in clear (NFR-SEC-LOG-D01). Stating the rule does not
 * require the value.</p>
 *
 * // source: biztalk_admin_12.js — jex.alert('등록중 오류 발생.')
 * // req: FR-SNDC-003, FR-SNDC-011, FR-SNDD-006, NFR-USE-D02, NFR-SEC-LOG-D01
 */
public class SenderNumberValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 계약상의 필드 이름 / the contract field name. */
    private final String field;

    /**
     * 예외를 만든다. / Creates the exception.
     *
     * @param field   위반된 필드 / the offending field
     * @param message 위반된 규칙을 설명하는 문장 / a sentence describing the rule
     */
    // req: NFR-USE-D02
    public SenderNumberValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    /**
     * 위반된 필드를 반환한다. / Returns the offending field.
     *
     * @return 필드 이름 / the field name
     */
    // req: NFR-USE-D02
    public String field() {
        return field;
    }
}
