package com.webcash.iris.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Spring Security 설정. / Spring Security configuration.
 *
 * <p><b>레거시 결함 D1/L5 대응.</b> 레거시는 서비스별 {@code <login>Y|N</login>} 플래그로
 * 인증 여부를 정했고, {@code biztalk_admin_40_l001} 은 {@code N} 으로 배포되어
 * 미인증 호출자에게 전화번호가 포함된 조회 결과를 반환했다. 신규 설계에는 그 플래그에
 * 상응하는 것이 없다 — 기본이 <b>인증 필수</b>이고, 예외는 아래 목록에 명시적으로
 * 열거된 것뿐이다.</p>
 * <p><b>Fixes legacy defects D1/L5.</b> The legacy decided authentication per service
 * with a {@code <login>Y|N</login>} flag, and {@code biztalk_admin_40_l001} shipped with
 * {@code N} — returning query results containing phone numbers to anonymous callers.
 * There is no equivalent flag here: authentication is <b>required by default</b> and the
 * only exceptions are those enumerated below.</p>
 *
 * <p>새 엔드포인트를 미인증으로 공개하려면 이 파일을 편집해야 하고, 그 변경은 코드
 * 리뷰에 남는다. 설정 파일의 플래그 한 글자를 바꾸는 것과는 다르다.</p>
 * <p>Exposing a new endpoint anonymously requires editing this file, and that change is
 * visible in review — unlike flipping one character in a configuration file.</p>
 *
 * // source: WSVC.biztalk_admin_40_l001.xml (&lt;login&gt;N&lt;/login&gt;), WSVC.apc_login_proc.xml
 * // req: FR-MSG-001, FR-LOGIN-001, NFR-SEC-AUTH, NFR-SEC-AUTH-L01, NFR-SEC-SESSION-L01
 */
@Configuration
@EnableWebSecurity
// D-A37 (Sprint A1 발견) — 이 한 줄이 없으면 여섯 컨트롤러의 @PreAuthorize 가 전부 무력하다.
// Spring Boot 3 에서 메서드 보안은 기본으로 꺼져 있고 @EnableMethodSecurity 로만 켜진다.
// 이 저장소 어디에도 그 애노테이션이 없었으므로, 설계 문서들이 "심층 방어"라고 적어 온
// 컨트롤러 수준 인가는 <b>존재하지 않았다</b> — 실제 방어선은 SecurityConfig 의
// /api/admin/** URL 규칙 하나뿐이었다. 문이 열려 있던 것은 아니지만(여섯 컨트롤러 모두 그
// URL 규칙 아래에 있다), 있다고 적힌 두 번째 층이 없었다. D-S2 와 같은 결함 유형이다 —
// 있어 보이지만 아무것도 지키지 않는 검사.
// D-A37, found in Sprint A1: without this line every @PreAuthorize on six controllers is inert.
// Method security is off by default in Spring Boot 3 and only @EnableMethodSecurity turns it on.
// The annotation appeared nowhere in this repository, so the controller-level authorization that the
// design documents call "defence in depth" <b>did not exist</b> — the only real barrier was the
// /api/admin/** URL rule. Not an open door (all six sit under that rule), but the second layer that
// was documented was absent. The same defect class as D-S2: a check that looks like a control and
// guards nothing.
//
// 활성화가 안전한 이유: @PreAuthorize 를 단 여섯 컨트롤러가 모두 이미
// /api/admin/** → hasRole("OPERATOR") 뒤에 있고 표현식도 동일하다. 지금 통과하는 호출자는
// 같은 역할 검사를 이미 통과한 사람이므로, 켜도 새로 거절되는 요청이 없다.
// Why enabling is safe: all six annotated controllers already sit behind
// /api/admin/** → hasRole("OPERATOR") with an identical expression, so no caller who is permitted
// today can be newly rejected.
//
// req: NFR-SEC-AUTHZ-A01, FR-AZ-A01, FR-AZ-A03
@EnableMethodSecurity
public class SecurityConfig {

    private final boolean requireHttps;
    private final long hstsMaxAgeSeconds;

    /**
     * 설정을 주입받아 생성한다. / Creates the configuration with injected settings.
     *
     * <p>{@code requireHttps} 기본값이 {@code true} 인 것은 의도적이다. 로컬 개발에서는
     * 명시적으로 끄고, <b>운영에서 잊어서 평문이 열리는</b> 방향의 기본값은 두지 않는다.</p>
     * <p>{@code requireHttps} defaults to {@code true} deliberately: local development turns
     * it off explicitly, and the default never fails open in production by omission.</p>
     *
     * @param requireHttps      HTTPS 강제 여부 / whether to require HTTPS
     * @param hstsMaxAgeSeconds HSTS 유효 기간(초) / the HSTS max age in seconds
     */
    // req: NFR-SEC-CHANNEL-L01
    public SecurityConfig(
            @Value("${iris.auth.require-https:true}") boolean requireHttps,
            @Value("${iris.auth.hsts-max-age-seconds:31536000}") long hstsMaxAgeSeconds) {
        this.requireHttps = requireHttps;
        this.hstsMaxAgeSeconds = hstsMaxAgeSeconds;
    }

    /**
     * 보안 필터 체인을 구성한다. / Configures the security filter chain.
     *
     * @param http HTTP 보안 빌더 / the HTTP security builder
     * @return 필터 체인 / the filter chain
     * @throws Exception 구성 실패 시 / when configuration fails
     */
    // req: FR-LOGIN-001, NFR-SEC-AUTH-L01, NFR-SEC-SESSION-L01, NFR-SEC-CHANNEL-L01
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // 정적 프론트엔드 자원. Spring 이 classpath:/static/ 에서 서빙한다. 이 규칙이
                // 없으면 anyRequest().authenticated() 가 index.html·JS·CSS 까지 인증 대상으로
                // 만들어, 로그인 화면 자체를 열 수 없다 — 인증하려면 화면이 필요한데 화면이
                // 인증을 요구하는 교착이다.
                // Static frontend assets served from classpath:/static/. Without this the
                // authenticated-by-default rule would also gate index.html/JS/CSS, so the login
                // screen could never load — reaching auth needs the page, the page needs auth.
                // req: FR-LOGIN-001, ADR-001
                .requestMatchers(HttpMethod.GET,
                        "/", "/index.html", "/favicon.ico", "/assets/**").permitAll()

                // 미인증 허용 — 진입점과 세션 이전 단계에 한정한다.
                // 각 엔드포인트는 요청 본문에 자격증명 2요소를 직접 요구하므로 인증을
                // 건너뛰는 것이 아니라 요청 단위로 수행한다.
                // Anonymous access, limited to the entry point and the pre-session steps.
                // Each of these requires both credential factors in the request body, so
                // authentication is performed per request rather than skipped.
                .requestMatchers("/api/auth/login", "/api/auth/logout").permitAll()
                // 강제 변경 시점에는 세션이 없다 (FR-LOGIN-014/015).
                // No session exists at forced-change time.
                .requestMatchers("/api/auth/password/change").permitAll()
                // OTP 미등록 계정은 로그인할 수 없으므로 등록도 세션 없이 시작된다.
                // An account without OTP cannot log in, so enrolment starts without a session.
                .requestMatchers("/api/auth/otp/registration/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()

                // 운영자 전용 — 이용기관 목록 열거 방지 (FR-TEN-004, TM-L011).
                // Operator-only: prevents enumeration of client companies.
                .requestMatchers("/api/admin/**").hasRole("OPERATOR")

                // 그 외 전부 인증 필수. 이 줄이 D1 의 재발을 막는다.
                // Everything else requires authentication. This line is what prevents D1.
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session
                // 세션은 필요할 때만 생성하고, 인증 시 식별자를 교체한다(컨트롤러에서 수행).
                // Sessions are created when needed; the id is replaced at authentication
                // (performed in the controller).
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .sessionFixation(fixation -> fixation.changeSessionId())
            )
            // CSRF 는 활성 상태를 유지한다. 세션 쿠키 기반 인증(ADR-LOGIN-012)을 택했으므로
            // CSRF 는 형식이 아니라 실제 위협이다.
            //
            // ▼ CR-01 수정 (ADR-014). 이전에는 CSRF 만 활성화하고 토큰을 클라이언트에
            //   전달하는 경로를 만들지 않았다. 기본 저장소는 HttpSessionCsrfTokenRepository
            //   이므로 토큰이 세션에만 존재하고 JS 는 읽을 수 없었다 — 그 결과 로그인을
            //   제외한 POST 9개가 전부 403 이었다.
            //
            // Fixes CR-01 (ADR-014): CSRF was enabled without any path for the client to obtain
            // the token. The default repository keeps it in the session where JS cannot read it,
            // so all nine POSTs other than login returned 403.
            //
            // req: NFR-SEC-CSRF, CR-01
            .csrf(csrf -> csrf
                // ① 토큰을 JS 가 읽을 수 있는 쿠키(XSRF-TOKEN)로 내보낸다.
                //    httpOnly=false 는 의도적이다 — SPA 가 값을 읽어 헤더에 실어야 한다.
                //    이것이 XSS 위험을 만들지는 않는다: XSS 가 이미 성립했다면 공격자는
                //    같은 출처에서 요청을 보낼 수 있으므로 CSRF 토큰은 애초에 방어선이 아니다.
                //    httpOnly=false is deliberate; it does not create an XSS risk, because an
                //    attacker with XSS can already issue same-origin requests.
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                // ② 평문 핸들러를 쓴다. Spring Security 6 의 기본값
                //    XorCsrfTokenRequestAttributeHandler 는 BREACH 대응으로 토큰을 마스킹하는데,
                //    쿠키에서 값을 그대로 읽어 보내는 SPA 는 그 마스킹을 재현할 수 없다.
                //    The default Xor handler masks the token for BREACH protection, which a SPA
                //    echoing the cookie value cannot reproduce.
                .csrfTokenRequestHandler(plainCsrfTokenHandler())
                // ③ 로그인만 면제한다. 로그인 시점에는 세션도 토큰도 없다.
                //    logout 은 면제하지 않는다 — 면제 목록이 길어지는 것 자체가 위험이다.
                .ignoringRequestMatchers(new AntPathRequestMatcher("/api/auth/login", "POST"))
            )
            // ④ 토큰을 <b>실제로 렌더링</b>하여 쿠키가 응답에 실리게 한다.
            //    Spring Security 6 은 CsrfToken 을 지연 로딩한다. 아무도 getToken() 을
            //    호출하지 않으면 값이 생성되지 않고, 따라서 Set-Cookie 도 나가지 않는다.
            //    ①~③ 만으로는 쿠키가 <b>영원히 설정되지 않아</b> 수정이 무효가 된다.
            //
            //    Spring Security 6 loads the token lazily: if nothing calls getToken() the value
            //    is never created and no Set-Cookie is emitted. Without this filter the first
            //    three steps leave the cookie permanently absent and the fix does nothing.
            .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable());

        // req: NFR-SEC-CHANNEL-L01 — TLS 강제.
        //
        // 인터넷에 노출된 인증 엔드포인트가 평문 HTTP 로 접근 가능하면 자격증명과 OTP
        // 코드가 그대로 노출된다. 두 가지를 함께 적용한다:
        //   1) requiresChannel — 평문 요청을 HTTPS 로 리다이렉트
        //   2) HSTS — 브라우저가 이후 평문 요청을 아예 보내지 않게 한다
        //
        // HSTS 가 필요한 이유: 리다이렉트만으로는 <b>첫</b> 평문 요청이 이미 네트워크에
        // 실린 뒤다. HSTS 는 그 첫 요청 자체를 없앤다.
        //
        // Both are applied: requiresChannel redirects plaintext to HTTPS, and HSTS stops the
        // browser sending plaintext at all. Redirection alone is insufficient because the
        // <b>first</b> plaintext request has already crossed the network; HSTS removes it.
        if (requireHttps) {
            http.requiresChannel(channel -> channel.anyRequest().requiresSecure());
        }
        http.headers(headers -> {
                // HSTS 는 HTTPS 를 강제할 때만 보낸다. 평문 HTTP 개발 서버가 HSTS 를 보내면
                // 브라우저가 이후 localhost 를 https 로 강제 업그레이드해 접속이 끊긴다.
                // HSTS is sent only when HTTPS is enforced; a plain-HTTP dev server advertising
                // HSTS would make the browser force-upgrade localhost to https and break access.
                if (requireHttps) {
                    headers.httpStrictTransportSecurity(hsts -> hsts
                            .includeSubDomains(true)
                            .maxAgeInSeconds(hstsMaxAgeSeconds));
                } else {
                    headers.httpStrictTransportSecurity(hsts -> hsts.disable());
                }
                // 로그인 화면이 프레임에 담기는 것을 막는다 (clickjacking).
                // Prevents the login screen being framed (clickjacking).
                headers
                .frameOptions(frame -> frame.deny())
                .contentSecurityPolicy(csp -> csp
                        // req: TM-L017 — SPA 의 XSS 노출면을 좁힌다. 인라인 스크립트를
                        // 허용하지 않으므로 Vite 빌드 산출물 형태에 맞춰 조정이 필요할 수 있다.
                        // Narrows the SPA's XSS surface (TM-L017). Inline script is not
                        // permitted, which may need tuning against the Vite output.
                        .policyDirectives(
                                "default-src 'self'; "
                                        + "script-src 'self'; "
                                        + "style-src 'self'; "
                                        + "img-src 'self' data:; "
                                        + "connect-src 'self'; "
                                        + "frame-ancestors 'none'; "
                                        + "base-uri 'self'; "
                                        + "form-action 'self'"));
        });

        return http.build();
    }

    /**
     * 마스킹하지 않는 CSRF 토큰 핸들러를 만든다.
     * Builds a CSRF token handler that does not mask the token.
     *
     * <p>{@code setCsrfRequestAttributeName(null)} 은 지연 로딩을 해제한다. 설정하지 않으면
     * 토큰이 요청 속성으로만 지연 등록되어, 이를 읽는 코드가 없는 요청에서는 값이 생성되지
     * 않는다.</p>
     * <p>{@code setCsrfRequestAttributeName(null)} opts out of deferred loading; otherwise the
     * token is registered lazily and never materialises on requests where nothing reads it.</p>
     *
     * @return 평문 토큰 핸들러 / a non-masking token handler
     */
    // req: NFR-SEC-CSRF, CR-01
    private static CsrfTokenRequestAttributeHandler plainCsrfTokenHandler() {
        CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
        handler.setCsrfRequestAttributeName(null);
        return handler;
    }

    /**
     * CSRF 토큰을 강제로 렌더링하여 쿠키가 응답에 실리게 하는 필터.
     * A filter that renders the CSRF token so the cookie is written to the response.
     *
     * <p><b>이 필터가 CR-01 수정의 핵심이다.</b> 저장소를 쿠키로 바꾸는 것만으로는 부족하다.
     * Spring Security 6 은 {@link CsrfToken} 을 지연 로딩하므로, 어떤 코드도
     * {@link CsrfToken#getToken()} 을 호출하지 않으면 토큰 값이 생성되지 않고
     * {@code Set-Cookie} 도 발행되지 않는다. 그 상태에서 SPA 는 읽을 쿠키가 없고, 결과는
     * 수정 전과 동일한 403 이다.</p>
     * <p><b>This filter is the heart of the CR-01 fix.</b> Switching the repository to a cookie is
     * not sufficient: Spring Security 6 loads the token lazily, so unless something calls
     * {@link CsrfToken#getToken()} no value is generated and no {@code Set-Cookie} is issued —
     * leaving the SPA with no cookie to read and the same 403 as before.</p>
     *
     * <p>{@link CsrfFilter} <b>뒤에</b> 배치해야 한다. 앞에 두면 요청 속성이 아직 없다.</p>
     * <p>Must run <b>after</b> {@link CsrfFilter}; before it, the request attribute is absent.</p>
     *
     * // req: NFR-SEC-CSRF, CR-01
     */
    static final class CsrfCookieFilter extends OncePerRequestFilter {

        /**
         * 토큰을 렌더링한 뒤 체인을 계속한다. / Renders the token, then continues the chain.
         *
         * @param request  요청 / the request
         * @param response 응답 / the response
         * @param chain    필터 체인 / the filter chain
         * @throws ServletException 서블릿 오류 / on servlet failure
         * @throws IOException      입출력 오류 / on I/O failure
         */
        // req: NFR-SEC-CSRF, CR-01
        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain chain)
                throws ServletException, IOException {

            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) {
                // 반환값을 쓰지 않는다. 호출 자체가 토큰을 생성하고 쿠키를 기록하게 만든다.
                // The return value is unused: the call itself materialises the token and causes
                // the cookie to be written.
                token.getToken();
            }
            chain.doFilter(request, response);
        }
    }
}
