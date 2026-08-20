package com.webcash.iris.common.tenant;

/**
 * 인증 주체의 조회 범위 — 세션과 역할에서 결정된다.
 * An authenticated principal's query scope, decided from the session and role.
 *
 * <h2>왜 {@code common.tenant} 로 옮겼는가 / why this moved here</h2>
 * <p>이 클래스는 이용기관 보고서 슬라이스에서 {@code ReportScope} 로 태어났다. 톡전송 내역
 * 슬라이스가 <b>같은 규칙</b>을 필요로 하면서(CONFLICT-T01 — 운영자 전용 화면) 선택지는 두
 * 개였다: 복사하거나, 옮기거나. 복사하면 <b>하나의 인가 규칙에 두 개의 구현</b>이 생긴다 —
 * 이 프로그램에서 용납할 수 없는 유일한 중복이다. 인가 규칙이 두 벌이면 한쪽만 고쳐지고,
 * 어느 쪽이 고쳐졌는지는 사고가 난 뒤에 알게 된다.</p>
 * <p>This class was born as {@code ReportScope} in the 이용기관 보고서 slice. When 톡전송 내역
 * needed the <b>same rule</b> (CONFLICT-T01 — an operator-only screen) there were two options:
 * copy it, or move it. Copying would put <b>two implementations of one authorization rule</b> in
 * the codebase — the single duplication this programme cannot afford. With two copies only one
 * gets fixed, and which one that was becomes clear after the incident.</p>
 *
 * <h2>이 클래스가 담고 있는 두 가지 성질 / the two properties this class carries</h2>
 * <p>둘 다 보안 성질이며, 둘 다 레거시가 반대로 하고 있었다.</p>
 * <ol>
 *   <li><b>이용기관 주체의 빈 값은 "전체"가 아니다.</b> 레거시는 빈 {@code IS_CD} 를 곧
 *       "전 기관"으로 해석했고, 인증 없는 서비스와 결합해 <b>파라미터 하나를 빼면 모든
 *       고객사의 데이터가 나오는</b> 경로가 되었다(D-R2, D-T2).</li>
 *   <li><b>범위를 벗어난 요청 값은 무시하고, 검증 후 거부하지 않는다.</b> 거부하면 오류
 *       메시지가 "그 기관은 존재한다/하지 않는다"를 알려주는 <b>열거 창구</b>가 된다
 *       (TM-T10).</li>
 * </ol>
 * <ol>
 *   <li><b>A blank value from a tenant principal does not mean "all".</b> The legacy read an
 *       empty {@code IS_CD} as every institution, which — combined with an unauthenticated
 *       service — made <b>omitting one parameter</b> return every customer's data (D-R2, D-T2).</li>
 *   <li><b>An out-of-scope requested value is ignored, not validated-then-rejected.</b>
 *       Rejecting turns the error message into an <b>enumeration oracle</b> for which
 *       institutions exist (TM-T10).</li>
 * </ol>
 *
 * <p>AMB-02 는 PM 결정으로 <b>폐기가 아니라 정제</b>되었다: 그 규칙은 <b>이용기관 주체</b>를
 * 규율하고, 운영자에게 전체는 <b>요청 파라미터가 아니라 권한</b>이다. 이 클래스가 그 구분이
 * 사는 유일한 곳이며, 두 경로가 같은 코드에서 갈라지므로 한쪽만 고쳐질 수 없다.</p>
 * <p>The PM <b>refined rather than overturned</b> AMB-02: it governs <b>tenant principals</b>, and
 * for an operator 전체 is <b>a permission, not a request parameter</b>. This class is the only
 * place that distinction lives, and both paths branch in one statement so neither can be fixed
 * alone.</p>
 *
 * @param institutionCode   적용할 이용기관 코드. 전체 조회면 null / the code to apply, null for all
 * @param allInstitutions   전 기관 조회 여부 / whether the query spans all institutions
 * @param overrideAttempted 요청 값이 무시되었는지 / whether a supplied value was ignored
 *
 * // source: IDO.KKB_APITR_SMTN_L001 — AND (:IS_CD = '' OR IS_CD = :IS_CD)
 * // source: IDO.KKB_APITR_HSTR_L001 — no institution predicate at all (D-T2)
 * // req: FR-AZ-R03, FR-AZ-R04, FR-AZ-T02, FR-AZ-T03, CONFLICT-R01, CONFLICT-T01
 */
public record PrincipalScope(String institutionCode, boolean allInstitutions, boolean overrideAttempted) {

    /**
     * 세션 주체와 요청 값에서 범위를 결정한다.
     * Resolves the scope from the session principal and the requested value.
     *
     * <p><b>운영자</b>는 특정 기관을 지목하거나 비워서 전체를 조회할 수 있다.
     * <b>이용기관 주체</b>는 요청 값과 무관하게 자기 기관으로 좁혀지며, 비워 보내도 전체가
     * 되지 않는다.</p>
     * <p>An <b>operator</b> may name an institution or leave it blank for all. A <b>tenant
     * principal</b> is narrowed to their own regardless of what they sent, and a blank value does
     * not mean "all".</p>
     *
     * @param principal            인증된 주체 / the authenticated principal
     * @param requestedInstitution 요청에 담긴 기관코드 / the institution code from the request
     * @return 결정된 범위 / the resolved scope
     * @throws TenantContext.TenantScopeUnavailableException 소속을 알 수 없는 비운영자 / for a
     *         non-operator whose institution is unknown
     */
    // req: FR-AZ-R03, FR-AZ-T03, CONFLICT-R01, CONFLICT-T01
    public static PrincipalScope resolve(TenantContext.TenantPrincipal principal,
                                         String requestedInstitution) {

        String requested = normalise(requestedInstitution);

        if (principal.operator()) {
            // 운영자만 전체를 볼 수 있다. 비워 두면 전체, 지목하면 그 기관.
            // Only an operator may see 전체: blank means all, a value means that institution.
            if (requested == null) {
                return new PrincipalScope(null, true, false);
            }
            return new PrincipalScope(requested, false, false);
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

        // 요청 값은 검증하지 않고 무시한다. 검증 후 거부하면 오류 메시지가 "그 기관은
        // 존재한다/하지 않는다"를 알려주는 열거 창구가 된다.
        // The requested value is ignored, not validated-then-rejected: rejecting would turn the
        // error message into an enumeration oracle for which institutions exist.
        boolean overrideAttempted = requested != null && !requested.equals(own);
        return new PrincipalScope(own, false, overrideAttempted);
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
    // req: FR-AZ-R05, FR-AZ-T05
    public String describe() {
        return allInstitutions ? "ALL" : institutionCode;
    }
}
