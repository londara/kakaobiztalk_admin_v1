package com.webcash.iris.auth.session;

import java.time.Instant;

/**
 * 공유 세션 레지스트리 항목. / An entry in the shared session registry.
 *
 * <p>레거시는 컨테이너 {@code HttpSession} 과 DB 세션 레지스트리
 * ({@code UserSessionDAO}/{@code UserSessionVO})를 함께 유지했다. 다중 인스턴스에서
 * 동시 로그인 제어를 하려면 인스턴스 간 공유 상태가 필요하기 때문이다 — 컨테이너
 * 세션은 인스턴스 로컬이다.</p>
 * <p>The legacy maintained both a container {@code HttpSession} and a database
 * session registry. Concurrent-session control across instances requires shared
 * state, because container sessions are per-instance.</p>
 *
 * @param email      사용자 이메일 / the user's email
 * @param sessionId  세션 식별자 / the session id
 * @param serverName 인스턴스 식별자 / the serving instance
 * @param sourceIp   신뢰 가능한 출처 IP / trusted source address
 * @param userAgent  User-Agent 헤더 / the user agent
 * @param loginAt    로그인 시각 / login timestamp
 *
 * // source: apc_login_proc_act.jsp — UserSessionVO(eml, sessionId, serName, ClientIp, userAgent)
 * // req: FR-LOGIN-017, ADR-LOGIN-012
 */
public record SessionRecord(
        String email,
        String sessionId,
        String serverName,
        String sourceIp,
        String userAgent,
        Instant loginAt
) {
}
