package com.webcash.iris.biztalk.domain;

/**
 * 삭제 대상이 살아 있는 행이 아니다. / The delete target is not a live row.
 *
 * <p><b>이 예외는 이 슬라이스가 존재하는 이유다.</b> D-S1 은 "삭제가 잘못되었다" 가 아니라
 * <b>"삭제가 잘못되었는데 잘 되었다고 말했다"</b> 였다. 레거시 삭제는
 * {@code DELETE ... WHERE IS_CD = :IS_CD AND decrypt(DP_NO) = :DP_NO} 였고, 그리드가 보낸 값이
 * 마스킹된 표시값({@code 01********8})이었으므로 어떤 행도 일치하지 않았다. 0건을 지운
 * {@code DELETE} 는 SQL 오류가 아니므로 예외가 발생하지 않았고, 이력 행은 그대로 기록되었으며,
 * 운영자는 {@code "정상적으로 처리되었습니다"} 를 보았다. 번호는 남았고 발송에도 계속 쓸 수
 * 있었다 — 아마 2025년 10월부터.</p>
 * <p><b>This exception is why the slice exists.</b> D-S1 was not "delete was wrong" but <b>"delete
 * was wrong and reported success"</b>. The legacy statement matched on the decrypted number, the
 * grid supplied a masked display value, and so nothing matched. A zero-row {@code DELETE} is not a
 * SQL error, so nothing raised, the history row was still written, and the operator was told it had
 * been processed normally. The number remained, and remained usable for sending — probably since
 * October 2025.</p>
 *
 * <p>그래서 이 예외를 던지는 자리에서 <b>절대</b> 하지 말아야 할 일은 조용히 0을 반환하는
 * 것이다. 반환값이 아니라 예외인 이유가 그것이다: 호출자가 무시할 수 없어야 한다
 * (FR-SNDD-002, NFR-OPS-D02).</p>
 * <p>The one thing the throwing site must <b>never</b> do is quietly return zero. That is why this is
 * an exception rather than a count: a caller must not be able to ignore it (FR-SNDD-002,
 * NFR-OPS-D02).</p>
 *
 * <p>대상 번호는 담지 않는다 — 메시지는 로그로도 나간다(NFR-SEC-LOG-D01).</p>
 * <p>The target number is not carried: the message also reaches the log (NFR-SEC-LOG-D01).</p>
 *
 * // source: biztalk_admin_10_d001_act.jsp — no check of the delete's row count
 * // req: FR-SNDD-002, NFR-OPS-D02, NFR-SEC-LOG-D01
 */
public class SenderNumberNotLiveException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 예외를 만든다. / Creates the exception.
     */
    // req: FR-SNDD-002
    public SenderNumberNotLiveException() {
        super("삭제 대상 발신번호를 찾을 수 없습니다. 목록을 다시 조회하세요.");
    }
}
