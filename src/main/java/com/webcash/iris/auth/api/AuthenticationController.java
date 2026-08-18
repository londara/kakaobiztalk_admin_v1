package com.webcash.iris.auth.api;

import com.webcash.iris.auth.domain.AuthenticationService;
import com.webcash.iris.auth.domain.RateLimiter;
import com.webcash.iris.auth.session.SessionRegistry;
import com.webcash.iris.common.audit.AuditEvent;
import com.webcash.iris.common.audit.AuditService;
import com.webcash.iris.common.tenant.TenantContextFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 REST 엔드포인트. / Authentication REST endpoints.
 *
 * <p>이 컨트롤러의 <b>미인증</b> 엔드포인트(로그인·로그아웃)는 시스템의 유일한 미인증
 * 진입점이며, 인터넷에 직접 노출된다. 레거시에서 가장 많이 공격받는 표면이었고 신규에서도
 * 그렇다. {@code /session} 은 그 둘과 달리 인증을 요구하며, 세션이 없으면 컨트롤러에
 * 도달하지 못한다.</p>
 * <p>The <b>unauthenticated</b> endpoints here — login and logout — are the system's only
 * unauthenticated entry point and are directly internet-facing, the most-probed surface in the
 * legacy and in the replacement alike. {@code /session} differs: it requires authentication and is
 * refused before reaching the controller when no session exists.</p>
 *
 * // source: apc_login_proc_act.jsp, apm_0001_01.js
 * // req: FR-LOGIN-001, FR-LOGIN-016, FR-LOGIN-017, FR-LOGIN-018, FR-LOGIN-019, FR-LOGIN-023
 */
@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {

    private final AuthenticationService authentication;
    private final SessionRegistry sessions;
    private final RateLimiter rateLimiter;
    private final AuditService audit;

    /**
     * Spring Security 컨텍스트 저장소. / Spring Security context store.
     *
     * <p>커스텀 로그인은 자격증명·OTP 를 자체 검증한 뒤 여기서 Spring Security 의
     * {@link org.springframework.security.core.Authentication} 을 세션에 저장한다. 이것이
     * 없으면 이후 요청은 익명으로 취급되어 {@code anyRequest().authenticated()} 와
     * {@code /api/admin/** → hasRole('OPERATOR')} 가 전부 403 을 반환한다(실측: 로그인 성공
     * 후 InstitutionPage 의 /api/admin/institutions/search 가 403).</p>
     * <p>The custom login verifies credentials and OTP itself, then stores a Spring Security
     * {@code Authentication} in the session here. Without it, later requests are anonymous and
     * {@code anyRequest().authenticated()} / {@code /api/admin/** hasRole('OPERATOR')} all return
     * 403 — the exact symptom after a successful login.</p>
     *
     * // req: FR-LOGIN-018, FR-TEN-004
     */
    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    /**
     * 컨트롤러 생성. / Creates the controller.
     *
     * @param authentication 인증 서비스 / the authentication service
     * @param sessions       세션 레지스트리 / the session registry
     * @param rateLimiter    속도 제한 / the rate limiter
     * @param audit          감사 서비스 / the audit service
     */
    public AuthenticationController(AuthenticationService authentication,
                                    SessionRegistry sessions,
                                    RateLimiter rateLimiter,
                                    AuditService audit) {
        this.authentication = authentication;
        this.sessions = sessions;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
    }

    /**
     * 로그인. / Logs in.
     *
     * <p>인증 성공 후 <b>세션 식별자를 재생성</b>한다. 레거시는
     * {@code request.getSession()} 을 그대로 사용해 인증 전 세션을 승격시켰고, 이는
     * 세션 고정(session fixation) 공격 경로였다(TM-L003).</p>
     * <p>The session id is <b>regenerated</b> after authentication succeeds. The legacy
     * reused {@code request.getSession()}, promoting the pre-authentication session —
     * a session-fixation opening (TM-L003).</p>
     *
     * @param body    로그인 요청 / the login request
     * @param request HTTP 요청 / the HTTP request
     * @return 로그인 결과 / the login result
     */
    // req: FR-LOGIN-001, FR-LOGIN-016, FR-LOGIN-017, NFR-SEC-SESSION-L01
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest body,
                                               HttpServletRequest request,
                                               HttpServletResponse response) {
        String sourceIp = trustedSourceIp(request);
        String correlationId = UUID.randomUUID().toString();

        // 속도 제한을 가장 먼저 판정한다. Argon2id 해싱보다 <b>앞</b>에 있어야 의미가 있다 —
        // 순서가 뒤바뀌면 요청 폭주가 그대로 CPU 소모로 이어진다(RISK-L07, TM-L016).
        // The rate limit is evaluated first. It is only meaningful <b>ahead</b> of Argon2id
        // hashing: reversed, a request flood turns straight into CPU exhaustion.
        rateLimiter.checkAndRecord(body.email(), sourceIp);

        var result = authentication.authenticate(
                body.email(), body.password(), body.otpCode(), sourceIp, correlationId);

        if (result.passwordChangeRequired()) {
            // 세션을 확립하지 않는다. 비밀번호 변경 전에는 어떤 자원에도 접근할 수 없다.
            // No session is established: nothing is reachable until the password changes.
            return ResponseEntity.ok(new LoginResponse(true, false, false));
        }

        // 세션 고정 방지 — 인증 성공 시점에 식별자를 교체한다.
        //
        // 순서가 중요하다: changeSessionId() 는 <b>기존 세션이 있어야</b> 동작하고, 없으면
        // "Cannot change session ID. There is no session associated with this request." 로
        // 실패한다. 레거시는 request.getSession() 으로 세션을 먼저 생성했다. 따라서 세션을
        // 먼저 확보한 뒤 식별자를 교체한다. (이 경로는 인증 성공 시에만 도달하므로 실패
        // 로그인이 세션을 만들지는 않는다.)
        //
        // Order matters: changeSessionId() requires an existing session and throws otherwise.
        // The session is obtained first (as the legacy did with getSession()), then its id is
        // rotated. Only a successful authentication reaches this code, so failed logins create
        // no session.
        var session = request.getSession();
        request.changeSessionId();
        String sessionId = session.getId();

        // 테넌트 컨텍스트의 근거를 세션에 심는다. TenantContextFilter 가 매 요청에서 이
        // 값들을 읽어 조회 범위를 결정한다(FR-TEN-001).
        //
        // 이용기관 코드를 <b>세션에</b> 두는 이유: 매 요청마다 DB 를 다시 읽으면 조회
        // 경로에 왕복이 추가되고, 요청 파라미터에서 받으면 그것이 곧 레거시의 결함이다
        // (TM-004 — 클라이언트가 보낸 ID 를 그대로 사용).
        //
        // The tenant context's basis is placed in the session; TenantContextFilter reads these
        // on every request to determine query scope. Re-reading the database per request would
        // add a round trip to the query path, and taking it from a request parameter is exactly
        // the legacy defect (TM-004).
        session.setAttribute(TenantContextFilter.SESSION_USER_ID, body.email());
        session.setAttribute(TenantContextFilter.SESSION_INSTITUTION,
                result.account().institutionCode());
        session.setAttribute(TenantContextFilter.SESSION_OPERATOR, result.operator());

        // Spring Security 컨텍스트를 확립한다. 이것이 있어야 이후 요청에서
        // anyRequest().authenticated() 와 /api/admin/** hasRole('OPERATOR') 가 통과한다.
        // 운영자는 ROLE_OPERATOR, 그 외는 ROLE_USER 권한을 갖는다(FR-LOGIN-018).
        // Establishes the Spring Security context so later requests pass authorization; operators
        // get ROLE_OPERATOR, others ROLE_USER.
        var authority = result.operator()
                ? new SimpleGrantedAuthority("ROLE_OPERATOR")
                : new SimpleGrantedAuthority("ROLE_USER");
        var authToken = UsernamePasswordAuthenticationToken.authenticated(
                body.email(), null, List.of(authority));
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authToken);
        SecurityContextHolder.setContext(securityContext);
        // 세션에 저장한다 — 저장하지 않으면 컨텍스트가 이 요청 한 번만 유효하다.
        // Persist to the session; without saving, the context lives only for this one request.
        securityContextRepository.saveContext(securityContext, request, response);

        Optional<?> displaced = sessions.register(
                body.email(), sessionId, sourceIp, request.getHeader("User-Agent"));

        return ResponseEntity.ok(
                new LoginResponse(false, result.operator(), displaced.isPresent()));
    }

    /**
     * 현재 세션을 확인한다. / Reports the current session.
     *
     * <h2>이 엔드포인트가 필요한 이유 / why this endpoint exists</h2>
     * <p>세션 쿠키는 {@code HttpOnly} 다(ADR-LOGIN-012). 그래서 브라우저의 JS 는 자기가
     * 로그인되어 있는지 <b>알 수 없다</b> — 쿠키를 읽을 수 없기 때문이다. SPA 를 새로
     * 고치면 자바스크립트 상태는 전부 사라지지만 쿠키와 서버 세션은 그대로 남는다. 물어볼
     * 곳이 없으면 클라이언트는 "로그인 안 된 것으로 간주" 밖에 할 수 없고, 사용자는 세션이
     * 멀쩡한데도 로그인 화면으로 되돌아간다.</p>
     * <p>The session cookie is {@code HttpOnly}, so browser JS cannot tell whether it is signed in
     * — it cannot read the cookie. Refreshing the SPA discards all JavaScript state while the
     * cookie and the server session survive. With nowhere to ask, the client can only assume
     * signed-out, and the user lands back on the login screen with a perfectly good session.</p>
     *
     * <p>대안은 운영자 여부를 {@code sessionStorage} 같은 곳에 클라이언트가 보관하는 것이지만,
     * 그 값은 <b>거짓말을 할 수 있다</b>: 서버 세션이 만료된 뒤에도 화면은 로그인된 것처럼
     * 보이고 모든 요청은 403 이 된다. 서버에 묻는 편이 그 상태를 애초에 만들지 않는다.</p>
     * <p>The alternative — the client keeping the role in {@code sessionStorage} — can lie: after
     * the server session expires the screen still looks signed in while every request returns 403.
     * Asking the server never creates that state.</p>
     *
     * <p>보안 설정을 바꾸지 않는다. 이 경로는 어떤 {@code permitAll()} 규칙에도 없으므로
     * {@code anyRequest().authenticated()} 에 걸리고, 세션이 없으면 컨트롤러에 도달하기 전에
     * 거절된다. 아래의 익명 검사는 그 위의 이중 확인이다 — 앞으로 누군가 이 경로를
     * {@code permitAll} 로 옮기더라도 "세션이 없는데 세션이 있다고 답하는" 일은 없어야 한다.</p>
     * <p>No security configuration changes: the path appears in no {@code permitAll()} rule, so it
     * falls under {@code anyRequest().authenticated()} and is refused before reaching the
     * controller when there is no session. The anonymous check below is a second line — should
     * anyone later move this path to {@code permitAll}, it must still never claim a session that
     * does not exist.</p>
     *
     * <p>감사 기록을 남기지 않는다. 이것은 인증 사건이 아니라 <b>상태 조회</b>이며, 새로고침
     * 한 번마다 감사 로그가 한 줄 늘면 실제 로그인·로그아웃 기록이 그 소음에 묻힌다.</p>
     * <p>Not audited: this is a state read rather than an authentication event, and one audit row
     * per page refresh would bury the real login and logout records in noise.</p>
     *
     * @param authentication 현재 인증 / the current authentication
     * @return 운영자 여부, 세션이 없으면 403 / the operator flag, or 403 when there is no session
     */
    // req: FR-LOGIN-018, ADR-001
    @GetMapping("/session")
    public ResponseEntity<SessionResponse> session(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // 로그인 시 심은 권한을 그대로 읽는다. 세션 속성(SESSION_OPERATOR)에도 같은 값이
        // 있지만, 클라이언트가 이 값으로 판단하려는 것은 "/api/admin/** 에 들어갈 수 있는가"
        // 이므로 그 판정에 실제로 쓰이는 권한을 근거로 삼는 것이 맞다.
        // Reads the authority granted at login. The session attribute carries the same value, but
        // what the client decides with it is "will /api/admin/** admit me", so the authority that
        // actually decides that is the right basis.
        boolean operator = authentication.getAuthorities().stream()
                .anyMatch(granted -> "ROLE_OPERATOR".equals(granted.getAuthority()));

        return ResponseEntity.ok(new SessionResponse(operator));
    }

    /**
     * 로그아웃. / Logs out.
     *
     * @param request HTTP 요청 / the HTTP request
     * @return 204 응답 / a 204 response
     */
    // req: FR-LOGIN-023
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) {
            String sessionId = session.getId();
            sessions.invalidate(sessionId);
            session.invalidate();
            audit.recordAuth(currentPrincipal(request), AuditEvent.ACTION_LOGOUT,
                    AuditEvent.Outcome.OK, null, trustedSourceIp(request), null);
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * 신뢰 가능한 출처 IP 를 반환한다. / Returns the trusted source address.
     *
     * <p><b>레거시 결함 L7 대응.</b> 레거시 {@code getClientIpAddress()} 는
     * {@code X-Forwarded-For}, {@code HTTP_VIA} 등 11개 헤더 중 처음 값이 있는 것을
     * 그대로 사용했다. 모두 클라이언트가 임의로 설정할 수 있는 값이므로, 로그인 이력과
     * IP 기반 통제가 위조 가능했다.</p>
     * <p><b>Fixes legacy defect L7.</b> The legacy took the first non-empty value from
     * 11 request headers including {@code X-Forwarded-For} and {@code HTTP_VIA} — all
     * client-settable, making login history and any IP-based control forgeable.</p>
     *
     * <p>여기서는 소켓 주소만 사용한다. 프록시 뒤에서 실제 클라이언트 IP 가 필요하면
     * Spring 의 {@code forward-headers-strategy} 를 <b>신뢰된 프록시에 한해</b> 활성화한다
     * — 애플리케이션이 헤더를 직접 읽는 일은 없다.</p>
     * <p>Only the socket address is used. Where the real client address is needed behind
     * a proxy, Spring's {@code forward-headers-strategy} is enabled <b>for trusted
     * proxies only</b> — the application never reads the header itself.</p>
     *
     * @param request HTTP 요청 / the HTTP request
     * @return 출처 IP / the source address
     */
    // source: apc_login_proc_act.jsp — getClientIpAddress(), 11 client-controlled headers
    // req: FR-LOGIN-019
    private String trustedSourceIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private String currentPrincipal(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null) {
            return "anonymous";
        }
        Object userId = session.getAttribute("userId");
        return userId == null ? "anonymous" : userId.toString();
    }
}
