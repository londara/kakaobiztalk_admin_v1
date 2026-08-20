import { ReportPage } from '../../features/biztalk/ReportPage';

/**
 * 이용기관 보고서 경로. / The institution usage report route.
 *
 * req: FR-AZ-R01, FR-AZ-R03, FR-AZ-R04
 *
 * <p>{@code RequireOperator} 아래에 놓인다. 레거시에서 이 화면의 데이터 서비스
 * ({@code biztalk_admin_20_l001})는 {@code <login>N</login>} 이어서 <b>세션조차 요구하지
 * 않았고</b>, 빈 기관코드가 전 기관을 뜻했으므로 자격증명 없는 한 번의 요청으로 모든
 * 고객사의 발송량이 나갔다(D-R1, D-R2, T-R10).</p>
 * <p>Placed under {@code RequireOperator}. In the legacy this screen's data service
 * ({@code biztalk_admin_20_l001}) declared {@code <login>N</login>} and so <b>required no session
 * at all</b>; with a blank institution code meaning every institution, one unauthenticated request
 * returned every customer's volumes (D-R1, D-R2, T-R10).</p>
 *
 * <p>다시 강조하면 이 가드는 편의이며 인가는 서버가 한다. 이용기관 주체가 이 경로에 직접
 * 접근하더라도 서버가 범위를 세션에서 다시 판정한다(FR-AZ-R03).</p>
 * <p>Again, the guard is a convenience and the server authorizes: a tenant principal reaching this
 * route directly still has its scope re-derived from the session (FR-AZ-R03).</p>
 */
export function ReportRoute() {
  return <ReportPage />;
}
