package com.webcash.iris.biztalk.api;

import com.webcash.iris.biztalk.domain.SenderNumberLimits;
import com.webcash.iris.biztalk.domain.SenderNumberRegistration;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 발신번호 등록 요청 본문. / The sender-number registration request body.
 *
 * <h2>이 레코드에 <b>없는</b> 필드가 계약이다 / the contract is what this record omits</h2>
 * <table border="1">
 *   <caption>의도적으로 받지 않는 값 / values deliberately not accepted</caption>
 *   <tr><th>없는 필드</th><th>이유</th></tr>
 *   <tr><td>{@code institution}</td>
 *       <td>대상 기관은 목록 화면이 고른 값이며 서버가 세션 권한으로 다시 판정한다
 *           (FR-SNDC-012, FR-AZ-D03). 레거시 팝업은 부모 창의 {@code IS_CD} 를 받아 그대로
 *           insert 에 넣었다 — 조회 경로에만 기록된 D-S3 의 쓰기 경로 쌍둥이다</td></tr>
 *   <tr><td>{@code registeredBy}, {@code registeredAt}</td>
 *       <td>행위자는 세션에서(FR-SNDC-009), 시각은 데이터베이스 시계에서 온다. 레거시 액션
 *           JSP 도 세션에서 가져왔고 그 점은 옳았다</td></tr>
 *   <tr><td>{@code authNo} (인증번호)</td>
 *       <td>소유 인증은 구현하지 않는다(PM 결정 AMB-S01, RESIDUAL-S01). 레거시 계약에는
 *           {@code AUTH_NO} 가 <b>선언되어 있었고</b> UI 와 서버 코드는 주석 처리되어 있었다 —
 *           선언만 남은 입력은 있는 통제로 오해된다(D-S4). 받지 않으면 오해가 없다</td></tr>
 * </table>
 *
 * <p>The institution is not accepted: the target is what the list selected and the server re-decides
 * it from session entitlements. Actor and timestamp come from the session and the database. And no
 * {@code AUTH_NO} is accepted — the legacy contract declared one while its UI and server code were
 * commented out, and a declared-but-dead input reads as a control that exists (D-S4).</p>
 *
 * <h2>검증이 두 곳에 있는 이유 / why validation exists twice</h2>
 * <p>여기의 Bean Validation 은 <b>빠른 거절</b>이고 진짜 방어선은 {@code SenderNumberWriteService}
 * 안에 있다. 둘은 {@link SenderNumberLimits} 의 <b>같은 상수</b>를 참조하므로 규칙이 두 벌이
 * 아니라 진입점이 두 개다 — HTTP 를 거치지 않는 호출자에게도 같은 규칙이 적용된다(FR-SNDC-003).
 * 레거시는 이 규칙 전부를 브라우저에 두었고, 게다가 존재하지 않는 요소를 검사했으므로 실제로는
 * <b>아무 규칙도 없었다</b>(D-S11).</p>
 * <p>The Bean Validation here is a <b>fast rejection</b>; the real barrier is inside
 * {@code SenderNumberWriteService}. Both reference the <b>same constants</b>, so there are two entry
 * points rather than two rule sets (FR-SNDC-003). The legacy kept every rule in the browser and
 * tested elements that did not exist, so in practice it had <b>no rules at all</b> (D-S11).</p>
 *
 * <p>발신번호 형식은 여기서 검사하지 <b>않는다</b>. 자릿수·접두어·특수번호 규칙은 도메인의
 * {@code SenderNumberValidator} 하나가 갖는다 — 애노테이션으로 정규식을 한 벌 더 두면 두 규칙이
 * 갈라지고, 갈라졌을 때 어느 쪽이 이기는지는 요청 경로에 따라 달라진다.</p>
 * <p>The number's format is <b>not</b> checked here: length, prefix and barred-number rules live in
 * the domain's {@code SenderNumberValidator} alone. A second copy as an annotation regex would drift,
 * and which copy wins would depend on the request path.</p>
 *
 * @param number      발신번호 / the sender number
 * @param description 설명 / the description
 * @param reason      사유 — 필수(PM 결정 AMB-S10) / the reason, mandatory per PM ruling AMB-S10
 *
 * // source: biztalk_admin_12_view.jsp — DP_NO, DSCP, REASON; WSVC.biztalk_admin_12_c001
 * // req: FR-SNDC-001, FR-SNDC-003, FR-SNDC-007, FR-SNDC-011, FR-SNDC-012
 */
public record SenderNumberRegisterRequest(

        @NotBlank(message = "발신번호를 입력해 주세요.")
        String number,

        @Size(max = SenderNumberLimits.DESCRIPTION_MAX, message = "설명이 너무 깁니다.")
        String description,

        /*
          사유가 @NotBlank 인 것이 레거시와의 의도된 차이다. 레거시 화면에도 칸은 있었으나
          클라이언트 검증이 존재하지 않는 요소를 검사했으므로(D-S11) 빈 값이 그대로 저장되었다.
          The @NotBlank is a deliberate difference from the legacy: the screen had the field, but its
          client validation tested non-existent elements (D-S11), so empty values were stored.
        */
        @NotBlank(message = "사유를 입력해 주세요.")
        @Size(max = SenderNumberLimits.REASON_MAX, message = "사유가 너무 깁니다.")
        String reason
) {

    /**
     * 도메인 명령으로 변환한다. / Converts to the domain command.
     *
     * <p>도메인이 API 레코드를 알지 못하게 하는 경계다. 의존이 이 방향으로만 흐르므로 요청
     * 형식이 바뀌어도 도메인 규칙은 그대로 남는다.</p>
     * <p>The boundary keeping the domain unaware of the API record; the dependency runs one way, so a
     * change of request shape leaves the domain rules untouched.</p>
     *
     * @return 도메인 명령 / the domain command
     */
    // req: FR-SNDC-001
    public SenderNumberRegistration toRegistration() {
        return new SenderNumberRegistration(number, description, reason);
    }
}
