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
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 REST 엔드포인트. / Authentication REST endpoints.
 *
 * <p>이 컨트롤러의 두 엔드포인트는 시스템의 유일한 미인증 진입점이며, 인터넷에
 * 직접 노출된다. 레거시에서 가장 많이 공격받는 표면이었고 신규에서도 그렇다.</p>
 * <p>The two endpoints here are the system's only unauthenticated entry point and are
 * directly internet-facing — the most-probed surface in the legacy and in the
 * replacement alike.</p>
 *
 * // source: apc_login_proc_act.jsp, apm_0001_01.js
 * // req: FR-LOGIN-001, FR-LOGIN-016, FR-LOGIN-017, FR-LOGIN-019, FR-LOGIN-023
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
