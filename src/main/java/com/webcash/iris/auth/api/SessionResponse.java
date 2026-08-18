package com.webcash.iris.auth.api;

/**
 * 세션 확인 응답. / Session probe response.
 *
 * <p>{@link LoginResponse} 와 달리 {@code passwordChangeRequired} 도
 * {@code displacedSession} 도 없다. 둘은 <b>로그인이라는 사건</b>에 대한 사실이며 세션의
 * 상태가 아니다 — 비밀번호 변경이 필요한 계정은 애초에 세션을 얻지 못하고, 기존 세션 종료는
 * 이미 그 시점에 사용자에게 알렸다. 새로고침마다 그 경고를 다시 띄우면 사용자는 매번 새로운
 * 침해가 일어난 것으로 읽는다.</p>
 * <p>Unlike {@link LoginResponse} this carries neither {@code passwordChangeRequired} nor
 * {@code displacedSession}: both are facts about the <b>act of logging in</b>, not about the state
 * of a session. An account needing a password change never obtains a session in the first place,
 * and a displacement was already reported when it happened — repeating that warning on every
 * refresh would read as a fresh compromise each time.</p>
 *
 * <p>운영자 여부만 담는 이유는 {@link LoginResponse} 와 같다: 응답 본문에 개인정보나 권한
 * 상세를 싣지 않는다. 클라이언트는 이 한 값으로 어떤 메뉴를 보일지만 정하고, 실제 인가는
 * 서버가 요청마다 다시 판정한다({@code /api/admin/** → hasRole('OPERATOR')}).</p>
 * <p>Only the operator flag is included, for the same reason as {@link LoginResponse}: no personal
 * data and no authorization detail in the body. The client uses it to decide which menus to show;
 * authorization itself is re-decided by the server on every request.</p>
 *
 * @param operator 운영자 여부 / whether the principal is an operator
 *
 * // req: FR-LOGIN-018, ADR-001
 */
public record SessionResponse(boolean operator) {
}
