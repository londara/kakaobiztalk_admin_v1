import { TalkHistoryPage } from '../../features/biztalk/TalkHistoryPage';

/**
 * 톡전송 내역 경로. / The 톡전송 내역 route.
 *
 * req: FR-AZ-T01, FR-AZ-T02, CONFLICT-T01
 *
 * <p>{@code RequireOperator} 아래에 놓인다. PROJECT-PROPOSAL §5.1 은 레거시 화면 30 을
 * {@code [Tenant]} 로 분류했으나, 정적 분석 결과 이 화면에는 <b>이용기관 조건이 아예
 * 없었고</b> 질의가 모든 기관의 거래를 반환했다 — 게다가 그 테이블
 * ({@code FT_APITR_HSTR})은 계좌번호·카드번호·거래금액을 담은 전체 핀테크 API 거래
 * 로그다. PM 결정 CONFLICT-T01 로 이 화면은 <b>운영자 전용</b>이며 §5.1 의 분류가
 * 정정되었다.</p>
 * <p>Placed under {@code RequireOperator}. PROJECT-PROPOSAL §5.1 classified legacy screen 30 as
 * {@code [Tenant]}, but static analysis found the screen has <b>no institution filter at all</b> and
 * its query returned every institution's transactions — and its table ({@code FT_APITR_HSTR}) is the
 * whole fintech estate's API transaction log, carrying account numbers, card numbers and amounts.
 * Under PM ruling CONFLICT-T01 the screen is <b>operator-only</b> and §5.1's label is corrected.</p>
 *
 * <p>다시 강조하면 이 가드는 편의이며 인가는 서버가 한다. 레거시의 다섯 서비스는 모두
 * {@code <login>Y</login>} 이었다 — 빠진 것은 인증이 아니라 <b>인가</b>였고, 어떤 액션에도
 * 역할 검사가 없었다(D-T2).</p>
 * <p>Again, the guard is a convenience and the server authorizes. All five legacy services declared
 * {@code <login>Y</login>}: what was missing was not authentication but <b>authorization</b>, and no
 * action carried a role check (D-T2).</p>
 */
export function TalkHistoryRoute() {
  return <TalkHistoryPage />;
}
