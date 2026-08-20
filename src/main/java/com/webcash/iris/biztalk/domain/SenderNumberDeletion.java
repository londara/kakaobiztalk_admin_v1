package com.webcash.iris.biztalk.domain;

import java.util.List;

/**
 * 발신번호 삭제 명령. / The sender-number deletion command.
 *
 * <p>대상은 <b>{@link SenderNumberRef} 의 목록</b>이며 표시 문자열이 아니다. 이 한 가지가
 * D-S1 의 구조적 수정이다 — 레거시는 그리드가 가진 값을 콤마로 이어 {@code DP_NO} 하나에 담아
 * 보냈고, 2025-10 에 그 값이 마스킹되기 시작하자 {@code decrypt(DP_NO) = '01********8'} 이
 * 되어 <b>0건</b>이 지워졌다. 0건 삭제는 오류가 아니므로 운영자는 성공을 보았다.</p>
 * <p>The target is a <b>list of {@link SenderNumberRef}</b>, never a display string. That single
 * choice is D-S1's structural fix: the legacy joined the grid's values with commas into one
 * {@code DP_NO}, and once that value became masked in 2025-10 the predicate matched <b>nothing</b>
 * — which is not an error, so the operator was shown success.</p>
 *
 * <p>목록이 <b>집합으로</b> 오는 것도 의미가 있다. 확인 화면이 열거한 집합과 삭제되는 집합이
 * 같아야 하며(FR-SNDD-009), 그것은 서버가 "몇 건" 이 아니라 "어느 것" 을 받을 때만 성립한다.</p>
 * <p>Receiving a <b>set</b> matters too: the set the confirmation enumerated and the set deleted must
 * be the same (FR-SNDD-009), and that only holds if the server is told <em>which</em> rather than
 * <em>how many</em>.</p>
 *
 * @param refs   삭제할 행 식별자 / the rows to delete
 * @param reason 사유 — 필수 / the reason, mandatory
 *
 * // source: biztalk_admin_10.js — _gu.getCheckData(grid1,"DP_NO").join(","); biztalk_admin_13_view.jsp
 * // req: FR-SNDD-004, FR-SNDD-006, FR-SNDD-009, FR-SND-007
 */
public record SenderNumberDeletion(List<SenderNumberRef> refs, String reason) {

    /**
     * 명령을 만든다. / Creates the command.
     *
     * <p>목록을 방어적으로 복사한다. 호출자가 나중에 리스트를 바꾸면 <b>확인 화면이 보여 준
     * 집합과 실제로 지워진 집합이 달라진다</b> — FR-SNDD-009 가 요구하는 등식이 깨지는 가장
     * 조용한 방법이다.</p>
     * <p>The list is copied defensively. A caller mutating it afterwards would make <b>the set shown
     * in the confirmation differ from the set deleted</b> — the quietest possible way to break the
     * equality FR-SNDD-009 requires.</p>
     *
     * @param refs   삭제할 행 식별자 / the rows to delete
     * @param reason 사유 / the reason
     */
    // req: FR-SNDD-009
    public SenderNumberDeletion(List<SenderNumberRef> refs, String reason) {
        this.refs = refs == null ? List.of() : List.copyOf(refs);
        this.reason = reason;
    }
}
