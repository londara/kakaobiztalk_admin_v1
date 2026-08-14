package com.webcash.iris.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

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
            // CSRF 는 활성 상태를 유지한다. SPA 는 토큰을 쿠키에서 읽어 헤더로 보낸다.
            // 세션 쿠키 기반 인증(ADR-LOGIN-012)을 택했으므로 CSRF 는 실제 위협이다.
            // CSRF stays enabled: having chosen cookie-based session authentication
            // (ADR-LOGIN-012), CSRF is a real threat rather than a formality.
            .csrf(csrf -> csrf
                .ignoringRequestMatchers(new AntPathRequestMatcher("/api/auth/login", "POST"))
            )
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
        http.headers(headers -> headers
                .httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .maxAgeInSeconds(hstsMaxAgeSeconds))
                // 로그인 화면이 프레임에 담기는 것을 막는다 (clickjacking).
                // Prevents the login screen being framed (clickjacking).
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
                                        + "form-action 'self'")));

        return http.build();
    }
}
