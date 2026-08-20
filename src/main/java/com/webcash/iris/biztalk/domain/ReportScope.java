package com.webcash.iris.biztalk.domain;

import com.webcash.iris.common.tenant.PrincipalScope;
import com.webcash.iris.common.tenant.TenantContext;

/**
 * 보고서 조회 범위 — {@link PrincipalScope} 로 위임하는 슬라이스 별칭.
 * The report's query scope — a slice-local alias delegating to {@link PrincipalScope}.
 *
 * <h2>이 클래스에 로직이 없는 이유 / why this class holds no logic</h2>
 * <p>규칙 자체는 톡전송 내역 슬라이스에서 {@link PrincipalScope} 로 옮겼다(작업 T1-04).
 * 운영자/이용기관 구분은 <b>하나의 인가 규칙</b>이고, 두 슬라이스가 각자 복사본을 갖는 것은
 * 이 프로그램에서 용납할 수 없는 유일한 중복이다 — 인가 규칙이 두 벌이면 한쪽만 고쳐지고,
 * 어느 쪽이 고쳐졌는지는 사고가 난 뒤에 알게 된다(RISK-T06).</p>
 * <p>The rule itself moved to {@link PrincipalScope} in the 톡전송 내역 slice (task T1-04). The
 * operator/tenant distinction is <b>one authorization rule</b>, and two slices each holding a copy
 * is the single duplication this programme cannot afford: with two copies only one gets fixed, and
 * which one that was becomes clear after the incident (RISK-T06).</p>
 *
 * <p>타입 자체는 남긴다. 이 슬라이스의 {@code ReportCriteria} 와 {@code ReportService} 가
 * 이 이름으로 서명되어 있고, <b>G1 을 통과한 인가 테스트가 이 이름으로 작성되어 있다.</b>
 * T1-04 의 종료 조건은 그 테스트 파일을 <b>한 줄도 고치지 않고</b> 통과하는 것이다 —
 * 리팩터링이 인가 테스트를 수정하게 되는 순간, 그 리팩터링은 검증 대상이 아니라 검증 도구를
 * 바꾼 것이 된다.</p>
 * <p>The type stays. This slice's {@code ReportCriteria} and {@code ReportService} are signed with
 * this name, and <b>the G1-approved authorization tests are written against it.</b> T1-04's exit
 * condition is that those tests pass <b>without a single edit</b>: the moment a refactor edits an
 * authorization test, it has changed the instrument rather than the thing measured.</p>
 *
 * @param institutionCode   적용할 이용기관 코드. 전체 조회면 null / the code to apply, null for all
 * @param allInstitutions   전 기관 조회 여부 / whether the query spans all institutions
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
     * @param principal            인증된 주체 / the authenticated principal
     * @param requestedInstitution 요청에 담긴 기관코드 / the institution code from the request
     * @return 결정된 범위 / the resolved scope
     * @throws TenantContext.TenantScopeUnavailableException 소속을 알 수 없는 비운영자 / for a
     *         non-operator whose institution is unknown
     */
    // req: FR-AZ-R03, CONFLICT-R01
    public static ReportScope resolve(TenantContext.TenantPrincipal principal,
                                      String requestedInstitution) {
        PrincipalScope scope = PrincipalScope.resolve(principal, requestedInstitution);
        return new ReportScope(
                scope.institutionCode(), scope.allInstitutions(), scope.overrideAttempted());
    }

    /**
     * 감사 기록에 남길 범위 설명을 반환한다.
     * Returns a description of the scope for the audit record.
     *
     * @return 설명 / the description
     */
    // req: FR-AZ-R05
    public String describe() {
        return new PrincipalScope(institutionCode, allInstitutions, overrideAttempted).describe();
    }
}
