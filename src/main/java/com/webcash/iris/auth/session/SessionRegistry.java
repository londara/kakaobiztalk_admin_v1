package com.webcash.iris.auth.session;

import com.webcash.iris.auth.infra.db.SessionMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 동시 로그인 제어를 위한 공유 세션 레지스트리. / Shared session registry for concurrent-session control.
 *
 * <p><b>정책: 최신 로그인 우선(newest-wins).</b> 동일 계정으로 새 로그인이 발생하면
 * 기존 세션을 강제 종료한다. 레거시와 동일한 정책이다.</p>
 * <p><b>Policy: newest-login-wins.</b> A new login for the same account terminates
 * the existing session. This matches the legacy behaviour.</p>
 *
 * <p>대안(새 로그인을 거부)과 비교하면 트레이드오프는 대칭적이다 — 한쪽은 공격자가
 * 사용자를 밀어낼 수 있고, 다른 쪽은 공격자가 사용자를 잠글 수 있다. 밀려남은
 * 사용자에게 <b>보이는</b> 침해 신호이지만, 잠김은 일반적인 장애처럼 보인다.
 * 그래서 이 정책을 유지한다(ADR-LOGIN-012 §3).</p>
 * <p>Against the alternative — rejecting the new login — the trade is symmetrical:
 * one lets an attacker displace a user, the other lets an attacker lock one out.
 * Displacement is a compromise signal the legitimate user can <b>see</b>, whereas a
 * lockout looks like an ordinary fault. Hence this policy (ADR-LOGIN-012 §3).</p>
 *
 * // source: apc_login_proc_act.jsp — checkDuplicateLogin / removeSessionByUserId / registerSession
 * // req: FR-LOGIN-016, FR-LOGIN-017, FR-LOGIN-023, ADR-LOGIN-012
 */
@Service
public class SessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(SessionRegistry.class);

    private final SessionMapper mapper;
    private final Clock clock;
    private final String serverName;

    /**
     * 세션 레지스트리 생성. / Creates the session registry.
     *
     * @param mapper     세션 저장소 매퍼 / the session store mapper
     * @param clock      시각 공급자 / the clock
     * @param serverName 이 인스턴스의 식별자 / this instance's identifier
     */
    public SessionRegistry(SessionMapper mapper,
                           Clock clock,
                           @org.springframework.beans.factory.annotation.Value("${iris.instance-name:local}") String serverName) {
        this.mapper = mapper;
        this.clock = clock;
        this.serverName = serverName;
    }

    /**
     * 기존 세션을 종료하고 새 세션을 등록한다.
     * Terminates any existing session for the account and registers the new one.
     *
     * <p>로그에는 이메일·세션 식별자를 남기지 않는다. 레거시는 중복 로그인 감지 시
     * 이메일·세션ID·IP 를 debug 로 출력했다 — 그대로 옮기면 NFR-SEC-LOG-L01 위반이다.</p>
     * <p>Neither email nor session id is logged. The legacy printed email, session id
     * and IP at debug level on every duplicate-login detection; carrying that across
     * would violate NFR-SEC-LOG-L01.</p>
     *
     * @param email     사용자 이메일 / the user's email
     * @param sessionId 새 세션 식별자 / the new session id
     * @param sourceIp  신뢰 가능한 출처 IP / trusted source address
     * @param userAgent User-Agent / the user agent
     * @return 종료된 기존 세션 (없으면 empty) / the displaced session, empty when none existed
     */
    // req: FR-LOGIN-016, FR-LOGIN-017, NFR-SEC-LOG-L01
    @Transactional
    public Optional<SessionRecord> register(String email, String sessionId, String sourceIp, String userAgent) {
        Optional<SessionRecord> existing = Optional.ofNullable(mapper.findByEmail(email));
        existing.ifPresent(previous -> {
            mapper.deleteByEmail(email);
            log.info("Displaced an existing session for an account on instance {}", previous.serverName());
        });
        mapper.insert(new SessionRecord(
                email, sessionId, serverName, sourceIp, userAgent, Instant.now(clock)));
        return existing;
    }

    /**
     * 세션을 무효화한다. / Invalidates a session.
     *
     * @param sessionId 세션 식별자 / the session id
     */
    // req: FR-LOGIN-023
    @Transactional
    public void invalidate(String sessionId) {
        mapper.deleteBySessionId(sessionId);
    }

    /**
     * 세션이 레지스트리에 유효하게 존재하는지 확인한다.
     * Whether the session is still valid in the registry.
     *
     * <p>레지스트리에 도달할 수 없을 때는 <b>실패를 닫는 방향</b>으로 처리해야 한다
     * (RISK-L11). 호출자가 예외를 삼켜 통과시키면 안 된다.</p>
     * <p>When the registry is unreachable the behaviour must <b>fail closed</b>
     * (RISK-L11); callers must not swallow the exception and proceed.</p>
     *
     * @param sessionId 세션 식별자 / the session id
     * @return 유효 여부 / true when present
     */
    // req: NFR-SEC-SESSION-L01
    public boolean isActive(String sessionId) {
        return mapper.findBySessionId(sessionId) != null;
    }
}
