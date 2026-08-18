package com.webcash.iris.auth.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.webcash.iris.auth.config.SecurityConfig;
import com.webcash.iris.auth.domain.AuthenticationService;
import com.webcash.iris.auth.domain.RateLimiter;
import com.webcash.iris.auth.session.SessionRegistry;
import com.webcash.iris.common.audit.AuditService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 세션 확인 엔드포인트 검증. / Session probe endpoint verification.
 *
 * req: FR-LOGIN-018, ADR-001
 *
 * <h2>이 테스트가 지키는 것 / what this pins</h2>
 * <p>이 엔드포인트의 값은 프론트엔드가 "새로고침 후에도 로그인 상태를 유지해도 되는가" 를
 * 판단하는 <b>유일한</b> 근거다. 따라서 두 가지가 절대 어긋나면 안 된다.</p>
 * <ol>
 *   <li>세션이 없으면 세션이 있다고 답하지 않는다 — 그러면 클라이언트는 로그인된 화면을
 *       띄우고 모든 조회가 403 이 된다.</li>
 *   <li>운영자 여부가 실제 권한과 일치한다 — 어긋나면 비운영자에게 운영자 메뉴가 보이고,
 *       눌렀을 때 403 만 돌아온다(레거시 D-I2·D-S2 가 정확히 그 모양이었다).</li>
 * </ol>
 * <p>This endpoint is the frontend's only basis for deciding whether it may stay signed in across
 * a refresh, so two things must never drift: it must not claim a session that does not exist, and
 * its operator flag must match the real authority — a mismatch shows operator menus to a
 * non-operator that answer 403 when pressed, which is the exact shape of legacy D-I2 and D-S2.</p>
 *
 * <p>{@code @WebMvcTest} 를 쓰는 이유는 {@code CsrfIntegrationTest} 와 같다: 전체 컨텍스트는
 * DataSource 를 요구하지만 이 환경에는 PostgreSQL 이 없고, 웹 계층만 올려도 <b>실제 시큐리티
 * 필터 체인</b>을 통과시킬 수 있다. 미인증 요청이 컨트롤러에 도달하지 못한다는 사실은 그
 * 체인을 실제로 통과시켜야만 확인된다.</p>
 * <p>{@code @WebMvcTest} for the same reason as {@code CsrfIntegrationTest}: a full context needs a
 * DataSource and there is no PostgreSQL here, while the web slice still exercises the <b>real
 * filter chain</b> — and only the real chain can show that an unauthenticated request never
 * reaches the controller.</p>
 */
@WebMvcTest(controllers = AuthenticationController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        // HTTPS 강제를 끄지 않으면 모든 요청이 302 로 바뀌어 403/200 판정이 불가하다.
        // Without this every request becomes a 302 and the 403/200 outcome is unobservable.
        "iris.auth.require-https=false"
})
class SessionEndpointTest {

    private static final String SESSION = "/api/auth/session";

    @Autowired private MockMvc mvc;

    @MockBean private AuthenticationService authentication;
    @MockBean private SessionRegistry sessions;
    @MockBean private RateLimiter rateLimiter;
    // TenantContextFilter 가 웹 슬라이스에 포함되므로 그 의존성이 필요하다.
    // The web slice includes TenantContextFilter, so its dependency must be present.
    @MockBean private AuditService audit;

    @Test
    @DisplayName("세션이 없으면 거절한다 / refuses when there is no session")
    void refusesWithoutSession() throws Exception {
        // 이 경로는 어떤 permitAll() 규칙에도 없으므로 anyRequest().authenticated() 에 걸린다.
        // 즉 컨트롤러 코드가 아니라 필터 체인이 먼저 막는다.
        // The path is in no permitAll() rule, so anyRequest().authenticated() catches it: the
        // filter chain refuses before any controller code runs.
        mvc.perform(get(SESSION)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    @DisplayName("운영자에게는 operator=true 를 알린다 / reports operator=true for an operator")
    void reportsOperator() throws Exception {
        mvc.perform(get(SESSION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operator").value(true));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("비운영자에게는 operator=false 를 알린다 / reports operator=false otherwise")
    void reportsNonOperator() throws Exception {
        mvc.perform(get(SESSION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operator").value(false));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("응답에 계정 정보를 담지 않는다 / the response carries no account detail")
    void carriesNoAccountDetail() throws Exception {
        // LoginResponse 와 같은 원칙 — 응답 본문에 개인정보나 권한 상세를 싣지 않는다.
        // 필드가 하나뿐임을 고정해 두면, 나중에 편의로 이메일이나 기관코드를 얹는 변경이
        // 이 테스트에서 걸린다.
        // The same principle as LoginResponse: no personal data and no authorization detail in the
        // body. Pinning the single field means a later convenience addition — an email, an
        // institution code — fails here first.
        mvc.perform(get(SESSION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
