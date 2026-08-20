package com.webcash.iris.biztalk.domain;

/**
 * 대상 이용기관이 없을 때 던진다. / Thrown when the target 이용기관 does not exist.
 *
 * <p>수정이 <b>등록으로 바뀌지 않게</b> 하는 예외다. 레거시는 이 자리에 UPSERT 를 두어
 * 대상이 없으면 조용히 새 행을 만들었다(D-I6). 갱신 행 수가 0이면 그것은 성공이 아니라
 * "그런 기관이 없다" 이며, 그 사실이 호출자에게 도달해야 한다.</p>
 * <p>The exception that keeps an update from <b>becoming a create</b>. The legacy had an upsert
 * here and silently inserted when the target was absent (D-I6). Zero rows updated is not success;
 * it means no such institution, and the caller has to learn that.</p>
 *
 * <p>논리 삭제된 기관({@code IS_STTS='D'})도 이 예외를 받는다 — 삭제된 기관은 수정
 * 대상이 아니며, 수정으로 되살아나서도 안 된다(ADR-INST-014).</p>
 * <p>A logically deleted institution ({@code IS_STTS='D'}) raises this too: a deleted institution
 * is not editable and must not be resurrected by an edit (ADR-INST-014).</p>
 *
 * <p>비검사 예외다. 검사 예외로 만들면 호출부가 {@code catch} 후 무시하기 쉬워지고, 이
 * 예외가 무시되면 "저장했다" 는 응답과 아무 일도 일어나지 않은 데이터베이스가 공존한다.</p>
 * <p>Unchecked deliberately: a checked exception invites a swallowing {@code catch}, and
 * swallowing this one leaves a "saved" response beside a database where nothing happened.</p>
 *
 * // source: IDO.KKB_FT_FTIS_INFO_C001 — INSERT … WHERE NOT EXISTS (SELECT * FROM UPSERT)
 * // req: FR-INSTC-004, ADR-INST-014, D-I6
 */
public class InstitutionNotFoundException extends RuntimeException {

    private final String code;

    /**
     * 예외를 생성한다. / Creates the exception.
     *
     * @param code 찾지 못한 기관코드 / the institution code that was not found
     */
    // req: FR-INSTC-004, D-I6
    public InstitutionNotFoundException(String code) {
        super("No active 이용기관 with code '" + code + "'");
        this.code = code;
    }

    /**
     * 대상 기관코드를 반환한다. / Returns the institution code.
     *
     * <p>로그와 감사 기록용이다. <b>응답 본문에는 담지 않는다</b> — 어느 코드가 존재하고
     * 어느 코드가 존재하지 않는지 알려주는 것은 열거 창구가 된다(TM-I002).</p>
     * <p>For the log and the audit record. <b>Not for the response body</b>: telling a caller
     * which codes exist and which do not is an enumeration oracle (TM-I002).</p>
     *
     * @return 기관코드 / the institution code
     */
    // req: TM-I002, FR-INSTC-005
    public String code() {
        return code;
    }
}
