package com.webcash.iris.biztalk.domain;

/**
 * 발신번호 쓰기 경로의 길이 제한. / Length limits on the sender-number write path.
 *
 * <p>제한이 한 곳에 모여 있는 이유는 <b>진입점이 둘</b>이기 때문이다 — API 레코드의 Bean
 * Validation 과 도메인 서비스의 검증. 두 곳이 각자 숫자를 적으면 규칙이 두 벌이 되고, 갈라졌을
 * 때 <b>느슨한 쪽이 이긴다</b>. 레거시가 그 상태였다: 화면의 {@code maxlength} 는 200/100 이었고
 * 서비스 계약에는 길이 선언이 아예 없었으므로, HTTP 를 직접 부르면 제한이 없었다(D-S15).</p>
 * <p>The limits sit in one place because there are <b>two entry points</b> — the API record's Bean
 * Validation and the domain service's own check. Numbers written separately in both become two rule
 * sets, and when they drift <b>the looser one wins</b>. That was the legacy state: the form's
 * {@code maxlength} said 200/100 and the service contract declared no length at all, so a direct
 * call had no limit (D-S15).</p>
 *
 * // source: biztalk_admin_12_view.jsp — maxlength; WSVC.biztalk_admin_12_c001 (no length declared)
 * // req: FR-SNDC-007, FR-SNDD-006, FR-SNDU-006
 */
public final class SenderNumberLimits {

    /** 설명 최대 길이 / maximum 설명 length. */
    // source: biztalk_admin_12_view.jsp — <textarea id="DSCP" maxlength="200">
    // req: FR-SNDC-007, FR-SNDU-006
    public static final int DESCRIPTION_MAX = 200;

    /** 사유 최대 길이 / maximum 사유 length. */
    // source: biztalk_admin_12_view.jsp / biztalk_admin_13_view.jsp — maxlength="100"
    // req: FR-SNDC-007, FR-SNDD-006
    public static final int REASON_MAX = 100;

    /**
     * 한 번에 삭제할 수 있는 최대 건수. / Cap on numbers deleted in one request.
     *
     * <p>NFR-PERF-D03 이 100건 삭제를 5초 안에 요구하므로 상한은 그보다 낮을 수 없다. 상한이
     * <b>있어야</b> 하는 이유는 다르다: 삭제는 한 트랜잭션이며(FR-SNDD-005) 요청이 크기를
     * 정하게 두면 요청 하나가 트랜잭션 수명을 정한다(T-D1 계열).</p>
     * <p>NFR-PERF-D03 requires a 100-number delete within 5 s, so the cap cannot be lower. The
     * reason a cap must <b>exist</b> is different: the delete is one transaction (FR-SNDD-005), and
     * letting the request decide its size lets one request decide the transaction's lifetime.</p>
     */
    // req: FR-SNDD-005, NFR-PERF-D03
    public static final int DELETE_BATCH_MAX = 100;

    private SenderNumberLimits() {
    }
}
