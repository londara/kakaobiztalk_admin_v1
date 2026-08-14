package com.webcash.iris.common.tenant;

/**
 * 요청 범위 테넌트 컨텍스트. / Request-scoped tenant context.
 *
 * <p>이 클래스는 <b>레거시에 대응물이 없다.</b> 레거시 IRIS_ADMIN 은 사내 전용 콘솔로
 * "운영자는 모든 이용기관을 본다"를 전제했고, 이용기관 식별자({@code ID})는 클라이언트가
 * 선택적으로 보내는 조회 조건이었다. 신규 포털은 외부 이용기관에 직접 노출되므로
 * 테넌트 격리가 <b>기능이 아니라 정확성 요구</b>가 된다 — 한 건이라도 누락되면 한
 * 고객사가 다른 고객사의 발송 내역을 보게 되고, 이는 신고 대상 사고다.</p>
 * <p>This class has <b>no legacy counterpart.</b> The legacy console was intranet-only and
 * assumed "an operator sees every 이용기관", with the institution id an optional
 * client-supplied filter. The new portal is exposed directly to client companies, which
 * makes tenant isolation a <b>correctness requirement rather than a feature</b>: a single
 * miss lets one client read another's send history — a reportable incident.</p>
 *
 * <p><b>ThreadLocal 을 쓰는 이유와 그 위험:</b> 컨텍스트를 메서드 인자로 넘기면 모든
 * 호출 경로가 그것을 전달해야 하고, 하나만 빠뜨려도 격리가 깨진다. ThreadLocal 은 그
 * 누락을 구조적으로 막지만, <b>정리하지 않으면 스레드 재사용 시 이전 요청의 테넌트가
 * 남는다</b> — 그것이 바로 교차 테넌트 유출이다. 정리는 필터의 {@code finally} 에서
 * 예외 발생 여부와 무관하게 수행한다.</p>
 * <p><b>Why ThreadLocal, and its hazard:</b> passing the context as a parameter means every
 * call path must carry it, and one omission breaks isolation. ThreadLocal removes that
 * class of omission — but <b>failing to clear it leaves the previous request's tenant on a
 * reused thread</b>, which is precisely a cross-tenant leak. Clearing happens in the
 * filter's {@code finally}, unconditionally.</p>
 *
 * // req: FR-TEN-001, FR-TEN-002, NFR-SEC-TENANT
 */
public final class TenantContext {

    private static final ThreadLocal<TenantPrincipal> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    /**
     * 현재 요청의 주체를 설정한다. / Binds the principal for the current request.
     *
     * @param principal 주체 / the principal
     */
    // req: FR-TEN-001
    public static void set(TenantPrincipal principal) {
        CURRENT.set(principal);
    }

    /**
     * 현재 요청의 주체를 반환한다. / Returns the principal for the current request.
     *
     * <p>설정되지 않은 상태에서 호출하면 예외를 던진다. {@code null} 을 반환하면 호출자가
     * "테넌트 없음 = 전체 조회"로 해석할 여지가 생기고, 그 해석이 곧 격리 실패다.</p>
     * <p>Throws when unset rather than returning {@code null}: a null would let a caller read
     * "no tenant" as "query everything", and that interpretation is the isolation failure.</p>
     *
     * @return 주체 / the principal
     * @throws IllegalStateException 컨텍스트가 없을 때 / when no context is bound
     */
    // req: FR-TEN-001, NFR-SEC-TENANT
    public static TenantPrincipal require() {
        TenantPrincipal principal = CURRENT.get();
        if (principal == null) {
            throw new IllegalStateException(
                    "No tenant context bound to this request. Every data access must be "
                            + "tenant-scoped (NFR-SEC-TENANT); failing closed rather than "
                            + "querying across tenants.");
        }
        return principal;
    }

    /**
     * 컨텍스트가 설정되어 있는지 반환한다. / Whether a context is bound.
     *
     * @return 설정 여부 / true when bound
     */
    public static boolean isBound() {
        return CURRENT.get() != null;
    }

    /**
     * 컨텍스트를 제거한다. / Clears the context.
     *
     * <p>스레드 풀 환경에서 이 호출이 누락되면 다음 요청이 이전 테넌트의 컨텍스트를
     * 물려받는다.</p>
     * <p>Omitting this in a pooled-thread environment lets the next request inherit the
     * previous tenant.</p>
     */
    // req: NFR-SEC-TENANT
    public static void clear() {
        CURRENT.remove();
    }

    /**
     * 인증된 주체와 그 테넌트 범위. / An authenticated principal and its tenant scope.
     *
     * @param email           사용자 이메일 / the user's email
     * @param institutionCode 소속 이용기관 코드. 운영자는 null 가능 / the 이용기관 code; may be null for operators
     * @param operator        운영자 여부 / whether the principal is an operator
     *
     * // source: apc_login_proc_act.jsp — USER_DSNC 'A'(admin) / 'U'(user), GRP_0 / GRP_1
     * // req: FR-TEN-001, FR-TEN-003, FR-LOGIN-018
     */
    public record TenantPrincipal(String email, String institutionCode, boolean operator) {

        /**
         * 조회에 적용할 이용기관 코드를 반환한다.
         * Returns the 이용기관 code the query must be scoped to.
         *
         * <p>운영자는 요청 값을 사용할 수 있고 비우면 전체({@code null})를 의미한다.
         * 이용기관 담당자는 <b>요청 값을 무시하고</b> 자신의 코드만 사용한다 — 레거시는
         * 클라이언트가 보낸 {@code ID} 를 그대로 조회 조건에 넣었다(TM-004).</p>
         * <p>An operator may use the requested value, blank meaning unrestricted. A
         * client-company user's requested value is <b>ignored</b> in favour of their own code:
         * the legacy placed the client-supplied {@code ID} straight into the query (TM-004).</p>
         *
         * @param requestedInstitutionCode 요청에 담긴 코드 (운영자만 유효) / the requested code, honoured for operators only
         * @return 적용할 코드, 전체 조회면 null / the code to apply, or null for unrestricted
         */
        // req: FR-TEN-001, FR-TEN-002, FR-TEN-003
        public String effectiveInstitutionCode(String requestedInstitutionCode) {
            if (operator) {
                return (requestedInstitutionCode == null || requestedInstitutionCode.isBlank())
                        ? null
                        : requestedInstitutionCode;
            }
            return institutionCode;
        }
    }
}
