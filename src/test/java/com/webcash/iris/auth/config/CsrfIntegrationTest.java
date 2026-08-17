package com.webcash.iris.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.webcash.iris.biztalk.api.MessageHistoryController;
import com.webcash.iris.biztalk.domain.CsvExporter;
import com.webcash.iris.biztalk.domain.MessageHistoryCriteria;
import com.webcash.iris.biztalk.domain.MessageHistoryRow;
import com.webcash.iris.biztalk.domain.MessageHistoryService;
import com.webcash.iris.biztalk.domain.PagedResult;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * CSRF 배선 통합 테스트. / CSRF wiring integration test.
 *
 * req: NFR-SEC-CSRF, CR-01 · ADR: ADR-014
 *
 * <h2>이 테스트가 존재하는 이유 / why this exists</h2>
 * <p><b>CR-01 을 잡았을 유일한 검증이다.</b> 프론트엔드 테스트 68건은 {@code fetch} 를
 * stub 하므로 서버 응답을 보지 못하고, {@code verify-without-maven.sh} 는 Spring 애노테이션을
 * 제거하므로 보안 설정을 보지 못한다. 두 검증 <b>사이의 공간</b>에 CR-01 이 있었다.</p>
 * <p><b>This is the verification that would have caught CR-01.</b> The 68 frontend tests stub
 * {@code fetch} and never see a server response; the JDK-only harness strips Spring annotations
 * and never sees the security configuration. CR-01 lived in the gap between them.</p>
 *
 * <p>{@code @WebMvcTest} 를 쓰는 이유: 전체 컨텍스트({@code @SpringBootTest})는 DataSource 를
 * 요구하고 이 환경에는 PostgreSQL 이 없다. 웹 계층만 올리면 <b>실제 시큐리티 필터 체인</b>을
 * 통과시키면서 DB 없이 실행할 수 있다.</p>
 * <p>{@code @WebMvcTest} is used because a full context needs a DataSource and there is no
 * PostgreSQL here; loading only the web layer still exercises the <b>real filter chain</b>.</p>
 */
@WebMvcTest(controllers = MessageHistoryController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        // HTTPS 강제를 끈다. 켜두면 모든 요청이 302 로 리다이렉트되어 403/200 판정이 불가하다.
        // Disabled so requests are not redirected to HTTPS, which would mask the 403/200 outcome.
        "iris.auth.require-https=false"
})
class CsrfIntegrationTest {

    @Autowired private MockMvc mvc;

    @MockBean private MessageHistoryService service;
    @MockBean private CsvExporter exporter;
    @MockBean private Clock clock;
    // @WebMvcTest 슬라이스는 Filter 빈을 포함하므로 TenantContextFilter 가 생성되고,
    // 그 의존성인 AuditService 가 필요하다.
    // The @WebMvcTest slice includes Filter beans, so TenantContextFilter is created and its
    // AuditService dependency must be present.
    @MockBean private com.webcash.iris.common.audit.AuditService audit;

    private static final String SEARCH = "/api/message-history/search";
    private static final String BODY =
            "{\"from\":\"2026-08-13T00:00:00\",\"to\":\"2026-08-14T00:00:00\"}";

    /**
     * 모의 서비스가 빈 페이지를 반환하도록 고정한다. / Stubs the mocked service to return an empty page.
     *
     * <p>이 테스트의 관심사는 <b>CSRF 필터 체인</b>이지 조회 결과가 아니다. 다만 토큰이 통과한
     * 요청은 컨트롤러까지 도달하므로, 모의 객체가 기본값 {@code null} 을 반환하면
     * {@code MessageHistoryResponse.from()} 이 {@code result.rows()} 에서
     * {@code NullPointerException} 을 던지고, 그 예외가 MockMvc 밖으로 전파되어 상태 코드
     * 판정 자체가 불가능해진다. 즉 <b>CSRF 가 정상 동작할 때만 실패하는</b> 테스트가 된다.</p>
     * <p>This test is about the <b>CSRF filter chain</b>, not the search result. But a request
     * that passes the token check reaches the controller, and an unstubbed mock returns
     * {@code null}, so {@code MessageHistoryResponse.from()} throws a
     * {@code NullPointerException} on {@code result.rows()}. That exception propagates out of
     * MockMvc and makes the status assertion impossible — the test fails <b>precisely when CSRF
     * works</b>, which is the opposite of what it is checking.</p>
     */
    @BeforeEach
    void stubSearch() {
        given(service.search(any(MessageHistoryCriteria.class), any(), any()))
                .willReturn(new PagedResult<MessageHistoryRow>(List.of(), 0, 0, 50));
    }

    // -------------------------------------------------------------------------
    // CR-01 회귀 방지 — 토큰 없는 요청은 거절된다
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("CSRF 토큰 없는 POST 는 403 이다 / a POST without a CSRF token is 403")
    // req: NFR-SEC-CSRF
    void postWithoutTokenIsForbidden() throws Exception {
        // 이것이 CR-01 의 증상이었다. 다만 여기서는 <b>의도된</b> 동작이다 —
        // 토큰이 없으면 거절되어야 한다. 결함은 "토큰을 얻을 방법이 없었다"는 것이었다.
        // This was CR-01's symptom, but here it is the intended behaviour; the defect was that
        // there was no way to obtain a token at all.
        mvc.perform(post(SEARCH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // CR-01 수정 검증 — 토큰을 얻을 수 있고, 그 토큰이 통과한다
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("응답이 XSRF-TOKEN 쿠키를 발행한다 / the response issues an XSRF-TOKEN cookie")
    // req: NFR-SEC-CSRF, CR-01
    void responseIssuesCsrfCookie() throws Exception {
        // ★ 이것이 수정의 핵심 검증이다.
        //
        // CookieCsrfTokenRepository 로 저장소를 바꾸는 것만으로는 부족하다. Spring Security 6 은
        // 토큰을 지연 로딩하므로 CsrfCookieFilter 가 getToken() 을 호출하지 않으면 쿠키가
        // <b>발행되지 않는다</b>. 그 경우 증상은 수정 전과 완전히 동일하다.
        //
        // 이 어서션이 CsrfCookieFilter 의 존재와 필터 순서를 동시에 검증한다 — 런타임에서만
        // 확인 가능한 두 가지다.
        //
        // Switching the repository alone is insufficient: Spring Security 6 defers token loading,
        // so without CsrfCookieFilter calling getToken() no cookie is issued and the symptom is
        // identical to the unfixed state. This assertion verifies both the filter's presence and
        // its position — neither of which is checkable without running.
        MvcResult result = mvc.perform(post(SEARCH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andReturn();

        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");

        assertThat(cookie)
                .as("XSRF-TOKEN cookie must be issued; without it the SPA has nothing to send")
                .isNotNull();
        assertThat(cookie.getValue()).isNotBlank();
        // JS 가 읽어야 하므로 httpOnly 여서는 안 된다. httpOnly=true 면 프론트엔드는
        // 토큰을 읽을 수 없고 CR-01 이 그대로 남는다.
        // Must not be httpOnly, or the frontend cannot read it and CR-01 persists.
        assertThat(cookie.isHttpOnly())
                .as("cookie must be readable by JS, or the token cannot be echoed")
                .isFalse();
    }

    @Test
    @DisplayName("발행된 쿠키 값을 헤더로 보내면 통과한다 / echoing the cookie value in the header passes")
    @WithMockUser
    // req: NFR-SEC-CSRF, CR-01
    void echoingCookieValueInHeaderPasses() throws Exception {
        // 프론트엔드가 실제로 하는 일을 그대로 재현한다: 쿠키를 읽어 X-XSRF-TOKEN 으로 보낸다.
        // 이 테스트가 통과하면 평문 핸들러(②) 선택이 옳았다는 뜻이다 — 기본 Xor 핸들러라면
        // 마스킹되지 않은 값이 거절되어 403 이 된다.
        //
        // Reproduces exactly what the frontend does. Passing here confirms the plain handler was
        // the right choice: with the default Xor handler an unmasked value would be rejected.
        MvcResult first = mvc.perform(post(SEARCH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andReturn();

        Cookie issued = first.getResponse().getCookie("XSRF-TOKEN");
        assertThat(issued)
                .as("an authenticated request must also receive an XSRF-TOKEN cookie; "
                        + "without one a logged-in SPA has no token to echo — see CR-02")
                .isNotNull();

        mvc.perform(post(SEARCH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY)
                        .cookie(issued)
                        .header("X-XSRF-TOKEN", issued.getValue()))
                // 200 이 아니라 "403 이 아님"을 단정한다. 인증(401)·검증(400) 등 다른 결과는
                // 이 테스트의 관심사가 아니다 — CSRF 계층을 통과했는지만 본다.
                // Asserts "not 403" rather than 200: other outcomes are not this test's concern.
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("CSRF layer must accept the echoed token")
                        .isNotEqualTo(403));
    }

    @Test
    @DisplayName("잘못된 토큰은 거절된다 / a wrong token is rejected")
    // req: NFR-SEC-CSRF
    void wrongTokenIsRejected() throws Exception {
        // 쿠키만 있고 헤더 값이 다르면 거절되어야 한다. 서버가 값을 <b>검증</b>한다는 증거다
        // (단순 double-submit 이 아니라 저장소 대조).
        // A mismatched header must be refused, proving the server validates the value rather than
        // merely observing that a cookie exists.
        MvcResult first = mvc.perform(post(SEARCH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andReturn();
        Cookie issued = first.getResponse().getCookie("XSRF-TOKEN");

        mvc.perform(post(SEARCH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY)
                        .cookie(issued)
                        .header("X-XSRF-TOKEN", "not-the-real-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Spring 의 csrf() 후처리기로도 통과한다 / passes with Spring csrf() post-processor")
    @WithMockUser
    // req: NFR-SEC-CSRF
    void passesWithFrameworkCsrfPostProcessor() throws Exception {
        mvc.perform(post(SEARCH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY)
                        .with(csrf()))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .isNotEqualTo(403));
    }

    @Test
    @DisplayName("내보내기도 같은 보호를 받는다 / export is protected identically")
    // req: NFR-SEC-CSRF, FR-MSG-017
    void exportIsProtectedToo() throws Exception {
        // 내보내기는 프론트엔드에서 공용 post 를 우회하는 경로다. 서버 측 보호가 동일한지
        // 별도로 확인한다.
        mvc.perform(post("/api/message-history/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());
    }
}
