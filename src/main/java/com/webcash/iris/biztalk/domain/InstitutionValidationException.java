package com.webcash.iris.biztalk.domain;

/**
 * 서버 측 필드 검증 실패. / A server-side field validation failure.
 *
 * <h2>왜 필드 이름을 담는가 / why it carries the field name</h2>
 * <p>레거시의 모든 검증은 브라우저에 있었다(D-I19). 서버로 옮기면서 가장 잃기 쉬운 것이
 * <b>어느 필드가 잘못되었는지</b>다 — {@code GlobalExceptionHandler} 는
 * {@link IllegalArgumentException} 을 "요청 값을 확인하세요" 한 줄로 바꾸며, 그것은
 * 예외 메시지에 SQL 조각이 섞이는 것을 막기 위해 <b>옳은</b> 선택이다. 그러나 폼 검증에는
 * 부족하다: 운영자가 다섯 칸 중 어디를 고쳐야 하는지 알 수 없다.</p>
 * <p>Every legacy rule lived in the browser (D-I19). The thing most easily lost in moving them
 * server-side is <b>which field</b> was wrong: {@code GlobalExceptionHandler} turns an
 * {@link IllegalArgumentException} into one generic line, which is <b>right</b> — it keeps SQL
 * fragments out of responses — but insufficient for a form, where the operator needs to know which
 * of five boxes to fix.</p>
 *
 * <p>담는 것은 <b>필드 이름과 규칙</b>뿐이고 <b>제출된 값은 담지 않는다.</b> 계약의 필드명은
 * 비밀이 아니지만 값은 그럴 수 있다 — 인증 경로가 같은 이유로 값을 되돌려주지 않는다.</p>
 * <p>It carries the <b>field name and the rule</b>, and <b>never the submitted value</b>. Field
 * names are part of the published contract; values may not be, which is why the authentication
 * path declines to echo them either.</p>
 *
 * // source: biztalk_admin_01.js — fn_save() alert chain, all client-side
 * // req: FR-INSTC-003, FR-INSTC-014, FR-INSTC-015, FR-INSTC-016, NFR-SEC-LOG-I01, D-I19
 */
public class InstitutionValidationException extends RuntimeException {

    private final String field;

    /**
     * 예외를 생성한다. / Creates the exception.
     *
     * @param field   계약상의 필드 이름 / the contract's field name
     * @param message 운영자에게 보일 메시지 — 값을 포함하지 않는다 / operator-facing message, no values
     */
    // req: FR-INSTC-003, D-I19
    public InstitutionValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    /**
     * 문제가 된 필드 이름을 반환한다. / Returns the offending field name.
     *
     * @return 필드 이름 / the field name
     */
    // req: FR-INSTC-003, NFR-USE-D02
    public String field() {
        return field;
    }
}
