package com.webcash.iris.biztalk.api;

import com.webcash.iris.biztalk.domain.SenderNumberDeletion;
import com.webcash.iris.biztalk.domain.SenderNumberLimits;
import com.webcash.iris.biztalk.domain.SenderNumberRef;
import com.webcash.iris.biztalk.domain.SenderNumberValidationException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 발신번호 삭제 요청 본문. / The sender-number deletion request body.
 *
 * <h2>{@code refs} 가 문자열 목록인 것이 D-S1 의 구조적 수정이다 / the list of refs is D-S1's fix</h2>
 * <p>레거시는 그리드가 가진 값을 콤마로 이어 <b>하나의 {@code DP_NO}</b> 로 보냈다:</p>
 * <pre>
 *   DP_NO = "01********8,15881234"      ← 표시값 + 콤마 결합
 * </pre>
 * <p>그 값이 {@code DELETE ... WHERE decrypt(DP_NO) = :DP_NO} 에 들어가 어떤 행과도 일치하지
 * 않았고, 0건 삭제는 SQL 오류가 아니므로 예외 없이 이력이 쓰이고 성공이 보고되었다(D-S1). 여기서는
 * 서버가 발급한 불투명 토큰의 <b>목록</b>을 받으므로 두 가지가 표현 불가능해진다 — 표시값이 식별자
 * 자리에 오는 것, 그리고 여러 번호가 하나의 필드로 뭉치는 것.</p>
 * <p>The legacy joined the grid's values with commas into <b>one {@code DP_NO}</b>. That value went
 * into a predicate on the decrypted column, matched nothing, and — a zero-row {@code DELETE} not
 * being an error — the history was still written and success reported (D-S1). Taking a <b>list</b> of
 * server-issued opaque tokens makes two things unrepresentable: a display value in the identifier
 * position, and several numbers collapsed into one field.</p>
 *
 * <p>토큰은 <b>인가 수단이 아니다.</b> 조작된 토큰으로 다른 기관의 번호를 지목해도 서버가 각
 * 대상의 기관을 세션 권한으로 다시 판정한다(FR-AZ-D03, 위협 T-T8).</p>
 * <p>A token is <b>not a capability</b>: a tampered one naming another institution's number is still
 * refused, because the server re-decides each target's institution from session entitlements
 * (FR-AZ-D03, threat T-T8).</p>
 *
 * <p>이용기관을 별도로 받지 않는다 — 각 토큰이 자기 기관을 담고 있고, 서버는 그것을 신뢰하지
 * 않고 다시 판정한다. 기관을 따로 받으면 "어느 기관의 번호인가" 에 두 개의 답이 생기고, 그중
 * 하나는 반드시 신뢰할 수 없다.</p>
 * <p>No separate institution is accepted: each token carries its own and the server re-decides it.
 * Accepting one separately would create two answers to "whose number is this", one of which must be
 * untrusted.</p>
 *
 * @param refs   삭제할 행 식별자 토큰 / the row-identifier tokens to delete
 * @param reason 사유 — 필수 / the reason, mandatory
 *
 * // source: biztalk_admin_10.js — _gu.getCheckData(grid1,"DP_NO").join(","); biztalk_admin_13_view.jsp
 * // req: FR-SNDD-002, FR-SNDD-004, FR-SNDD-006, FR-SNDD-009, FR-SND-007
 */
public record SenderNumberDeleteRequest(

        @NotEmpty(message = "삭제할 발신번호를 선택해 주세요.")
        @Size(max = SenderNumberLimits.DELETE_BATCH_MAX,
                message = "한 번에 삭제할 수 있는 발신번호 수를 초과했습니다.")
        List<String> refs,

        @NotBlank(message = "사유를 입력해 주세요.")
        @Size(max = SenderNumberLimits.REASON_MAX, message = "사유가 너무 깁니다.")
        String reason
) {

    /**
     * 도메인 명령으로 변환한다. / Converts to the domain command.
     *
     * <p>토큰 복원이 실패하면 {@link SenderNumberRef#fromToken} 이 <b>예외</b>를 던진다. 조용히
     * 건너뛰면 확인 화면이 열거한 집합보다 적게 지워지고, 그 차이는 아무 데도 드러나지 않는다 —
     * FR-SNDD-009 의 등식이 깨지는 가장 조용한 방법이다.</p>
     * <p>A token that cannot be restored makes {@link SenderNumberRef#fromToken} <b>throw</b>.
     * Skipping it quietly would delete fewer numbers than the confirmation enumerated, with nothing
     * showing the difference — the quietest way to break FR-SNDD-009's equality.</p>
     *
     * @return 도메인 명령 / the domain command
     */
    // req: FR-SNDD-009, FR-SND-007
    public SenderNumberDeletion toDeletion() {
        try {
            List<SenderNumberRef> resolved = refs == null
                    ? List.of()
                    : refs.stream().map(SenderNumberRef::fromToken).toList();
            return new SenderNumberDeletion(resolved, reason);
        } catch (IllegalArgumentException e) {
            /*
              복원 불가능한 토큰은 <b>필드를 지목한 400</b> 으로 바꾼다. 그대로 던지면
              GlobalExceptionHandler 가 "요청 값을 확인하세요" 한 줄로 만드는데, 이 경우 운영자가
              고칠 입력이 없다 — 실제로 필요한 행동은 목록을 다시 조회하는 것이다. 표시용 값
              (마스킹된 번호, 콤마 목록)이 식별자 자리에 오면 base64 복원이 실패하므로 D-S1 의
              흔적이 여기서 걸린다(TC-S004-03).
              An unrestorable token becomes a <b>field-named 400</b>. Left alone, GlobalExceptionHandler
              would collapse it into one generic line, and here the operator has no input to fix — the
              action needed is re-reading the list. A display value (a masked number, a comma list) in
              the identifier position fails base64 restoration, so D-S1's fingerprint is caught here
              (TC-S004-03).
            */
            throw new SenderNumberValidationException("refs",
                    "삭제 대상 식별자가 올바르지 않습니다. 목록을 다시 조회하세요.");
        }
    }
}
