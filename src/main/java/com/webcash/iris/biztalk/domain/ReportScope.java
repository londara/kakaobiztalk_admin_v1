package com.webcash.iris.biztalk.domain;

import com.webcash.iris.common.tenant.TenantContext;

/**
 * 보고서 조회 범위 — 세션과 역할에서 결정된다.
 * The report's query scope, decided from the session and role.
 *
 * <h2>왜 {@code effectiveInstitutionCode} 를 그대로 쓰지 않는가 / why not reuse it directly</h2>
 * <p>{@link TenantContext.TenantPrincipal#effectiveInstitutionCode(String)} 은 문자내역
 * 슬라이스가 AMB-02("범위는 세션에서 결정, 클라이언트 값은 무시")를 구현한 것이다. 화면 20 은
 * 그 규칙과 정면으로 충돌했다 — <b>전 기관 비교가 이 화면의 존재 이유</b>이고 기본값이
 * 전체다(CONFLICT-R01).</p>
 * <p>{@link TenantContext.TenantPrincipal#effectiveInstitutionCode(String)} implements the
 * 문자내역 slice's AMB-02 ruling — scope comes from the session, a client value is ignored.
 * Screen 20 collided with it head-on: <b>cross-institution comparison is the screen's whole
 * purpose</b> and its default is 전체 (CONFLICT-R01).</p>
 *
 * <p>PM 결정으로 AMB-02 는 <b>폐기가 아니라 정제</b>되었다. 그 규칙은 <b>이용기관 주체</b>를
 * 규율하고, 운영자에게 전체는 <b>요청 파라미터가 아니라 권한</b>이다. 이 클래스가 그 구분이
 * 사는 곳이며, 두 경로가 같은 코드에서 갈라지므로 한쪽만 고쳐지는 일이 없다.</p>
 * <p>The PM <b>refined rather than overturned</b> AMB-02: it governs <b>tenant principals</b>, and
 * for an operator 전체 is <b>a permission, not a request parameter</b>. This class is where that
 * distinction lives, and both paths branch in one place so neither can be fixed alone.</p>
 *
 * @param institutionCode 적용할 이용기관 코드. 전체 조회면 null / the code to apply, null for all
 * @param allInstitutions 전 기관 조회 여부 / whether the query spans all institutions
 * @param overrideAttempted 요청 값이 무시되었는지 / whether a supplied value was ignored
 *
 * // source: IDO.KKB_APITR_SMTN_L001 — AND (:IS_CD = '' OR IS_CD = :IS_CD)
 * // req: FR-AZ-R03, FR-AZ-R04, CONFLICT-R01
 */
public record ReportScope(String institutionCode, boolean allInstitutions, boolean overrideAttempted) {

    /**
     * 세션 주체와 요청 값에서 범위를 결정한다.
     * Resolves the scope from the session principal and the requested value.
     *
     * <p><b>운영자</b>는 특정 기관을 지목하거나 비워서 전체를 조회할 수 있다.
     * <b>이용기관 주체</b>는 요청 값과 무관하게 자기 기관으로 좁혀지며, 비워 보내도 전체가
     * 되지 않는다 — 레거시에서 빈 {@code IS_CD} 가 곧 "전 기관"이었고, 그것이 인증 없는
     * 서비스(D-R1)와 결합해 <b>한 번의 요청으로 모든 고객사의 발송량이 노출되는</b>
     * 경로였다(D-R2, T-R10).</p>
     * <p>An <b>operator</b> may name an institution or leave it blank for all. A <b>tenant
     * principal</b> is narrowed to their own regardless of what they sent, and a blank value does
     * not mean "all" — in the legacy an empty {@code IS_CD} meant exactly that, and combined with
     * an unauthenticated service (D-R1) it was the path by which <b>one request exposed every
     * customer's send volumes</b> (D-R2, T-R10).</p>
     *
     * @param principal          인증된 주체 / the authenticated principal
     * @param requestedInstitution 요청에 담긴 기관코드 / the institution code from the request
     * @return 결정된 범위 / the resolved scope
     * @throws TenantContext.TenantScopeUnavailableException 소속을 알 수 없는 비운영자 / for a
     *         non-operator whose institution is unknown
     */
    // req: FR-AZ-R03, CONFLICT-R01
    public static ReportScope resolve(TenantContext.TenantPrincipal principal,
                                      String requestedInstitution) {

        String requested = normalise(requestedInstitution);

        if (principal.operator()) {
            // 운영자만 전체를 볼 수 있다. 비워 두면 전체, 지목하면 그 기관.
            // Only an operator may see 전체: blank means all, a value means that institution.
            if (requested == null) {
                return new ReportScope(null, true, false);
            }
            return new ReportScope(requested, false, false);
        }

        // ── 이용기관 주체 / tenant principal ───────────────────────────────────────
        // 소속을 알 수 없으면 예외로 거부한다. null 을 반환하면 매퍼가 기관 조건을 생성하지
        // 않아 전 기관이 노출된다 — 격리 통제가 조용히 정반대로 뒤집히는 경로다.
        // A principal whose institution is unknown is refused. Returning null would suppress the
        // mapper's predicate and expose every institution — the isolation control inverting.
        String own = principal.effectiveInstitutionCode(null);
        if (own == null) {
            throw new TenantContext.TenantScopeUnavailableException(principal.email());
        }

        // 요청 값은 검증하지 않고 <b>무시</b>한다. 검증 후 거부하면 오류 메시지가 "그 기관은
        // 존재한다/하지 않는다"를 알려주는 열거 창구가 된다.
        // The requested value is <b>ignored</b>, not validated-then-rejected: rejecting would turn
        // the error message into an enumeration oracle for which institutions exist.
        boolean overrideAttempted = requested != null && !requested.equals(own);
        return new ReportScope(own, false, overrideAttempted);
    }

    private static String normalise(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 감사 기록에 남길 범위 설명을 반환한다.
     * Returns a description of the scope for the audit record.
     *
     * @return 설명 / the description
     */
    // req: FR-AZ-R05
    public String describe() {
        return allInstitutions ? "ALL" : institutionCode;
    }
}
