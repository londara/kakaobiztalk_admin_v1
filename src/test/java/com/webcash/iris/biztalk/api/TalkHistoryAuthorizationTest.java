package com.webcash.iris.biztalk.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.webcash.iris.auth.config.SecurityConfig;
import com.webcash.iris.biztalk.domain.PagedResult;
import com.webcash.iris.biztalk.domain.TalkHistoryRow;
import com.webcash.iris.biztalk.domain.TalkHistoryService;
import com.webcash.iris.common.audit.AuditService;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 톡전송 내역 인가 검증 — T1-16 의 부정 경로 시험.
 * 톡전송 내역 authorization verification: T1-16's negative-path suite.
 *
 * <h2>이 시험이 이 슬라이스에서 특별한 이유 / why this suite matters here</h2>
 * <p>이 슬라이스의 다섯 레거시 서비스는 <b>모두</b> {@code <login>Y</login>} 이었다 — 화면 20 이나
 * 40 과 달리 빠진 인증은 없었다. 없던 것은 <b>인가</b>다. 어떤 액션에도 역할 검사가 없었고
 * {@code IDO.KKB_APITR_HSTR_L001} 에는 기관 술어가 아예 없었으므로, 인증된 아무 주체나 모든
 * 고객사의 거래 내역을 한 그리드로 볼 수 있었다(D-T2, CVSS 7.7).</p>
 * <p><b>All five</b> legacy services declared {@code <login>Y</login>} — unlike screens 20 and 40, no
 * authentication was missing. What was missing is <b>authorization</b>: no action carried a role check
 * and the query had no institution predicate, so any authenticated principal saw every customer's
 * transactions in one grid (D-T2, CVSS 7.7).</p>
 *
 * <p>따라서 <b>거부를 단언하는 시험이 없는 인가 통제는 통제가 아니라 주장</b>이다. 스프린트 T1
 * 로그는 이 시험을 "DB 티어에 막혀 있다"고 기록했는데 그것은 잘못된 판단이었다 —
 * {@code @WebMvcTest} 는 DataSource 없이 <b>실제 시큐리티 필터 체인</b>을 통과시킨다. 회고 A5 의
 * 근거가 이 정정이다.</p>
 * <p>An authorization control with no test asserting the refusal is <b>a claim, not a control</b>. The
 * Sprint T1 log recorded this suite as blocked on a DB tier and that was wrong: {@code @WebMvcTest}
 * exercises the <b>real filter chain</b> without a DataSource. This correction is the basis of
 * retrospective action A5.</p>
 *
 * // source: WSVC.biztalk_admin_30.xml / _30_l001 / _30_l002 / _30_l003 / _30_spreadsheet — all <login>Y</login>, no role check
 * // req: FR-AZ-T01, FR-AZ-T02, NFR-SEC-AUTHZ-T01, NFR-SEC-TENANT-T01
 */
@WebMvcTest(controllers = TalkHistoryController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        // HTTPS 강제를 끈다. 켜두면 모든 요청이 302 로 리다이렉트되어 401/403/200 판정이 불가하다.
        // Disabled so requests are not redirected to HTTPS, which would mask the outcome.
        "iris.auth.require-https=false"
})
class TalkHistoryAuthorizationTest {

    private static final String LIST = "/api/admin/biztalk/talk-history";
    private static final String FILTERS = LIST + "/filters";

    @Autowired private MockMvc mvc;

    @MockBean private TalkHistoryService service;
    // @WebMvcTest 슬라이스는 Filter 빈을 포함하므로 TenantContextFilter 가 생성되고,
    // 그 의존성인 AuditService 와 Clock 이 필요하다.
    // The slice includes Filter beans, so TenantContextFilter is created and its dependencies must exist.
    @MockBean private AuditService audit;
    @MockBean private Clock clock;

    @BeforeEach
    void setUp() {
        PagedResult<TalkHistoryRow> empty = new PagedResult<>(List.of(), 0, 0, 100);
        given(service.search(any(), anyString())).willReturn(empty);
        given(service.apiServiceOptions()).willReturn(List.of());
        given(service.statusOptions()).willReturn(List.of());
    }

    @Nested
    @DisplayName("인증되지 않은 호출 / unauthenticated calls")
    class Unauthenticated {

        @Test
        @DisplayName("목록 조회는 인증 없이 거부된다 — FR-AZ-T01")
        void listRefusesAnonymous() throws Exception {
            mvc.perform(get(LIST).param("from", "20260819"))
                    .andExpect(status().is4xxClientError());
            // 서비스에 닿지 않아야 한다. 필터 체인에서 끝나야 감사·조회 부하가 발생하지 않는다.
            // Must not reach the service: stopping in the filter chain avoids both audit and query load.
            verify(service, never()).search(any(), anyString());
        }

        @Test
        @DisplayName("필터 선택지도 인증 없이 거부된다 — 부수 엔드포인트도 문이다")
        void filtersRefuseAnonymous() throws Exception {
            // 레거시의 실패는 "인가를 잊었다"가 아니라 "한쪽 문만 잠갔다"였다. 선택기 서비스
            // (biztalk_admin_30_l003)는 등록된 모든 API 를 반환했고 그 자체로 정보 노출이었다.
            // The legacy's failure was not "we forgot authorization" but "we locked one of two doors". The
            // selector service returned every registered API and was a disclosure in its own right.
            mvc.perform(get(FILTERS)).andExpect(status().is4xxClientError());
            verify(service, never()).apiServiceOptions();
        }
    }

    @Nested
    @DisplayName("역할이 부족한 호출 / insufficient role")
    class WrongRole {

        @Test
        @WithMockUser(username = "u@client.example", roles = {"USER"})
        @DisplayName("이용기관 주체는 목록 조회에서 403 이다 — CONFLICT-T01")
        void tenantIsForbiddenOnList() throws Exception {
            // PROJECT-PROPOSAL §5.1 은 이 화면을 [Tenant] 로 분류했으나, 화면에는 기관 조건이
            // 아예 없었고 테이블은 계좌·카드번호를 담은 전체 API 거래 로그다. PM 결정
            // CONFLICT-T01 로 운영자 전용이며 §5.1 의 분류가 정정되었다.
            // §5.1 classified this screen as [Tenant], but it had no institution filter at all and its
            // table is the whole API transaction log, carrying account and card numbers. Under
            // CONFLICT-T01 it is operator-only and §5.1's label is corrected.
            mvc.perform(get(LIST).param("from", "20260819"))
                    .andExpect(status().isForbidden());
            verify(service, never()).search(any(), anyString());
        }

        @Test
        @WithMockUser(username = "u@client.example", roles = {"USER"})
        @DisplayName("이용기관 주체는 필터 선택지에서도 403 이다")
        void tenantIsForbiddenOnFilters() throws Exception {
            mvc.perform(get(FILTERS)).andExpect(status().isForbidden());
            verify(service, never()).apiServiceOptions();
        }
    }

    @Nested
    @DisplayName("운영자 호출 / operator calls")
    class Operator {

        @Test
        @WithMockUser(username = "op@example.com", roles = {"OPERATOR"})
        @DisplayName("운영자는 목록을 조회할 수 있다")
        void operatorMayList() throws Exception {
            mvc.perform(get(LIST).param("from", "20260819")).andExpect(status().isOk());
            verify(service).search(any(), anyString());
        }

        @Test
        @WithMockUser(username = "op@example.com", roles = {"OPERATOR"})
        @DisplayName("운영자는 필터 선택지를 받을 수 있다")
        void operatorMayReadFilters() throws Exception {
            mvc.perform(get(FILTERS)).andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = "op@example.com", roles = {"OPERATOR"})
        @DisplayName("요청일자가 없으면 400 이다 — 값 없는 요청이 조용히 성공하지 않는다")
        void missingFromIsRejected() throws Exception {
            // 레거시는 요청일자를 오늘로 기본값 처리하고 서버 검증을 두지 않았다(D-T24).
            // The legacy defaulted the request date to today with no server-side validation (D-T24).
            mvc.perform(get(LIST)).andExpect(status().isBadRequest());
            verify(service, never()).search(any(), anyString());
        }
    }
}
