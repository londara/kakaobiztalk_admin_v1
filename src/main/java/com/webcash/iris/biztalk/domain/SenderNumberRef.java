package com.webcash.iris.biztalk.domain;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/**
 * 발신번호 행 식별자. / Sender-number row identifier.
 *
 * <p><b>이 타입은 D-S1 의 구조적 수정이다.</b> 결함 자체보다 결함을 가능하게 한 구조를
 * 없애는 것이 목적이다.</p>
 * <p><b>This type is the structural fix for D-S1</b>, aimed at the structure that permitted the
 * defect rather than the defect itself.</p>
 *
 * <h2>무슨 일이 있었나 / what happened</h2>
 * <p>세 계층이 각각 옳았고, 합쳐졌을 때 틀렸다.</p>
 * <ol>
 *   <li>목록 조회가 {@code RegexNameMasking.maskName()} 으로 발신번호를 마스킹했다 —
 *       개인정보 관점에서 타당한 변경이다.</li>
 *   <li>그리드는 목록이 준 값을 그대로 삭제 요청에 실어 보냈다 — 일반적인 동작이다.</li>
 *   <li>삭제 쿼리는 {@code WHERE decrypt(DP_NO) = :DP_NO} 로 행을 찾았다 — 2021년 작성
 *       시점에는 옳았다.</li>
 * </ol>
 * <p>합치면 삭제 조건이 {@code decrypt(DP_NO) = '01********8'} 이 되어 <b>0건</b>이 지워진다.
 * 0건 삭제는 오류가 아니므로 예외도 없고, 이력 행은 그대로 기록되며, 운영자는
 * {@code "정상적으로 처리되었습니다"} 를 본다. 번호는 남아 있고 발송에도 계속 쓸 수 있다.</p>
 * <p>Composed, the delete matches nothing. A zero-row {@code DELETE} is not an error, so no
 * exception is raised, the history row is still written, and the operator is told it succeeded.
 * The number remains, and remains usable for sending.</p>
 *
 * <h2>이 타입이 강제하는 성질 / the property this type enforces</h2>
 * <p><b>행을 식별하는 값과 사람에게 보여 주는 값은 결코 같은 값이 아니다.</b> 표시 형식이
 * 바뀌어도 식별이 깨지지 않아야 한다 — 레거시에 없던 것이 바로 이 분리다.</p>
 * <p><b>The value that identifies a row is never the value shown to a human.</b> A change to
 * display formatting must not break identification; that separation is what the legacy lacked.</p>
 *
 * <h2>이것은 인가 수단이 아니다 / this is not an authorization mechanism</h2>
 * <p>토큰은 서명되지 않으며 클라이언트가 복호할 수 있다. 그래도 되는 이유는 두 가지다.
 * 첫째, 발신번호는 AMB-S04 결정에 따라 <b>이미 화면에 전체가 표시</b>되므로 토큰이 응답에
 * 없던 정보를 추가로 드러내지 않는다. 둘째, 조작된 토큰으로 다른 기관의 번호를 지목하더라도
 * 조회 범위는 {@code TenantContext} 가 세션 권한으로 별도 검증한다(FR-AZ-D03) — 식별과 인가는
 * 분리된 관심사다.</p>
 * <p>The token is unsigned and client-decodable. That is acceptable because, first, the sender
 * number is <b>already displayed in full</b> per ruling AMB-S04, so the token reveals nothing the
 * response does not already carry; and second, a tampered token naming another institution's
 * number is still rejected by the tenant scope check (FR-AZ-D03). Identity and authorization are
 * separate concerns and are enforced separately.</p>
 *
 * @param institutionCode 이용기관 코드 / the institution code
 * @param number          발신번호 원본값 / the sender number, unformatted
 *
 * // source: biztalk_admin_10.js — _gu.getCheckData() → checkedItem.DP_NO (the masked value)
 * // req: FR-SND-007, FR-SNDD-002
 */
public record SenderNumberRef(String institutionCode, String number) {

    /**
     * 토큰 내부 구분자. / The in-token separator.
     *
     * <p>단위 구분자(U+001F)를 쓴다. 발신번호는 숫자만, 기관코드는 영숫자만 담기므로 이
     * 문자는 어느 쪽에도 나타날 수 없어 분해가 모호해지지 않는다.</p>
     * <p>The unit separator (U+001F): it cannot occur in a digits-only number or an alphanumeric
     * institution code, so decomposition is never ambiguous.</p>
     */
    private static final String SEPARATOR = "\u001F";

    /**
     * 식별자를 생성한다. / Creates an identifier.
     *
     * @param institutionCode 이용기관 코드 / the institution code
     * @param number          발신번호 / the sender number
     */
    // req: FR-SND-007
    public SenderNumberRef {
        Objects.requireNonNull(institutionCode, "institutionCode");
        Objects.requireNonNull(number, "number");
    }

    /**
     * 클라이언트에 전달할 불투명 토큰을 반환한다.
     * Returns the opaque token handed to the client.
     *
     * @return 토큰 / the token
     */
    // req: FR-SND-007
    public String token() {
        String joined = institutionCode + SEPARATOR + number;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(joined.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 토큰을 식별자로 복원한다. / Restores an identifier from its token.
     *
     * <p>복원할 수 없는 입력은 <b>예외</b>다. 조용히 {@code null} 이나 빈 값을 돌려주면 상위
     * 계층이 그것을 "일치하는 행이 없음"으로 해석할 수 있고, 그 해석이 정확히 D-S1 —
     * 아무것도 하지 않고 성공을 보고하는 상태 — 로 이어진다.</p>
     * <p>Unrestorable input <b>throws</b>. Returning {@code null} or a blank would let a caller
     * read it as "no matching row", and that reading is precisely D-S1: doing nothing and
     * reporting success.</p>
     *
     * @param token 토큰 / the token
     * @return 식별자 / the identifier
     * @throws IllegalArgumentException 토큰이 손상되었거나 형식이 아닐 때 / when the token is malformed
     */
    // req: FR-SND-007, FR-SNDD-002
    public static SenderNumberRef fromToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("sender number reference is required");
        }
        String decoded;
        try {
            decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("malformed sender number reference", e);
        }
        String[] parts = decoded.split(SEPARATOR, -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("malformed sender number reference");
        }
        return new SenderNumberRef(parts[0], parts[1]);
    }

    /**
     * 표시용 문자열이 식별자로 쓰였는지 판정한다.
     * Whether a display-formatted value has been used as an identifier.
     *
     * <p>D-S1 회귀 방지용이다. 마스킹된 값({@code 01********8})이나 콤마로 이어붙인 목록
     * ({@code 010...,010...}) 이 식별자 자리에 들어오면 조회는 0건이 되고, 그 0건이 성공으로
     * 보고되는 것이 원래 결함이었다. 여기서 <b>거절</b>하면 그 경로가 닫힌다.</p>
     * <p>Guards the D-S1 regression. A masked value ({@code 01********8}) or a comma-joined list
     * ({@code 010...,010...}) in the identifier position makes the lookup match nothing, and that
     * nothing being reported as success was the original defect. <b>Rejecting</b> here closes the
     * path.</p>
     *
     * @param candidate 검사할 값 / the value to inspect
     * @return 표시용 형식이면 true / true when it is a display format
     */
    // source: biztalk_admin_10_l001_act.jsp — RegexNameMasking.maskName(DP_NO)
    // source: biztalk_admin_10_d001_act.jsp — input.getString("DP_NO").split(",")
    // req: FR-SND-007, FR-SNDD-002, FR-SNDD-004
    public static boolean looksLikeDisplayValue(String candidate) {
        if (candidate == null) {
            return false;
        }
        return candidate.indexOf('*') >= 0 || candidate.indexOf(',') >= 0;
    }
}
