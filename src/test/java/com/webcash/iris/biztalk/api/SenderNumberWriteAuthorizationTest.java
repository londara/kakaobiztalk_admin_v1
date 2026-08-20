package com.webcash.iris.biztalk.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.webcash.iris.auth.config.SecurityConfig;
import com.webcash.iris.biztalk.domain.InstitutionRow;
import com.webcash.iris.biztalk.domain.InstitutionService;
import com.webcash.iris.biztalk.domain.SenderNumberDuplicateException;
import com.webcash.iris.biztalk.domain.SenderNumberNotLiveException;
import com.webcash.iris.biztalk.domain.SenderNumberRef;
import com.webcash.iris.biztalk.domain.SenderNumberService;
import com.webcash.iris.biztalk.domain.SenderNumberValidationException;
import com.webcash.iris.biztalk.domain.SenderNumberWriteService;
import com.webcash.iris.common.audit.AuditService;
import java.time.Clock;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 발신번호 쓰기 경로의 HTTP 계약·인가 검증 — S2a-13.
 * HTTP contract and authorization verification for the sender-number write path.
 *
 * <h2>이 시험이 없으면 무엇이 주장으로 남는가 / what stays a claim without this suite</h2>
 * <p>{@code SenderNumberWriteServiceTest} 는 <b>서비스</b>가 비운영자를 거부함을 증명한다. 그것은
 * HTTP 계층에 대해 아무것도 말해 주지 않는다 — 경로 규칙, {@code @PreAuthorize}, CSRF, 예외
 * 어드바이스의 상태 코드는 필터 체인과 디스패처를 거쳐야 관찰된다. 직전 스프린트(I2a)의 HIGH
 * 발견 중 하나가 정확히 이것이었다(SI2a-02: API 계층 커버리지 0%, 서비스 시험이 마치 FR-AZ 를
 * 덮는 것처럼 읽혔다).</p>
 * <p>{@code SenderNumberWriteServiceTest} proves the <b>service</b> refuses a non-operator, which says
 * nothing about the HTTP layer: the routing rule, {@code @PreAuthorize}, CSRF and the advice's status
 * codes are only observable through the filter chain. One of the previous sprint's HIGH findings was
 * exactly this (SI2a-02).</p>
 *
 * <p>이 슬라이스에는 이유가 하나 더 있다. 레거시에서 <b>삭제가 가장 덜 보호된 조작</b>이었다 —
 * 등록·수정은 브라우저에서 매니저 여부를 물었지만 삭제 버튼에는 그 검사조차 없었고, 서비스는
 * {@code <login>Y</login>} 하나였다(D-S2). 가장 파괴적인 동작이 가장 덜 보호되어 있었으므로,
 * 삭제의 거부를 HTTP 계층에서 단언하는 것이 이 시험의 핵심이다.</p>
 * <p>This slice adds a second reason: in the legacy <b>delete was the least protected operation</b> —
 * register and edit asked the browser about manager status, the delete button asked nothing, and the
 * service carried only a login check (D-S2). The most destructive action was the least guarded, so
 * asserting delete's refusal at the HTTP layer is this suite's core.</p>
 *
 * // source: WSVC.biztalk_admin_10_d001.xml / _12_c001.xml — both login=Y, no role check
 * // req: FR-AZ-D01, FR-AZ-D02, FR-AZ-D04, FR-SNDC-002, FR-SNDC-004, FR-SNDC-011,
 * //      FR-SNDD-002, NFR-SEC-AUTHZ-D01, NFR-SEC-PII-D02
 */
@WebMvcTest(controllers = SenderNumberController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        // HTTPS 강제를 끈다. 켜두면 모든 요청이 302 로 리다이렉트되어 401/403/200 판정이 불가하다.
        // Disabled so requests are not redirected to HTTPS, which would mask the outcome.
        "iris.auth.require-https=false"
})
class SenderNumberWriteAuthorizationTest {

    private static final String CODE = "K0ABCD";
    private static final String LIST = "/api/admin/sender-numbers";
    private static final String CONTEXT = LIST + "/context?institution=" + CODE;
    private static final String REGISTER = LIST + "?institution=" + CODE;
    private static final String DELETE = LIST + "/delete";

    /** 유효한 등록 본문. / A valid registration body. */
    private static final String REGISTER_BODY = """
            {"number":"0212345678","description":"대표번호","reason":"고객사 요청"}
            """;

    /** 유효한 삭제 본문 — ref 는 서버가 발급한 토큰이다. / A valid deletion body; the ref is a server-issued token. */
    private static final String DELETE_BODY = """
            {"refs":["SzBBQkNEHzAyMTIzNDU2Nzg"],"reason":"해지"}
            """;

    @Autowired private MockMvc mvc;

    @MockBean private SenderNumberService service;
    @MockBean private SenderNumberWriteService writeService;
    @MockBean private InstitutionService institutions;
    // @WebMvcTest 슬라이스는 Filter 빈을 포함하므로 TenantContextFilter 가 생성되고,
    // 그 의존성인 AuditService 와 Clock 이 필요하다.
    // The slice includes Filter beans, so TenantContextFilter is created and its dependencies must exist.
    @MockBean private AuditService audit;
    @MockBean private Clock clock;

    @BeforeEach
    void setUp() {
        given(writeService.register(anyString(), any(), anyString()))
                .willReturn(new SenderNumberRef(CODE, "0212345678"));
        given(writeService.delete(any(), anyString())).willReturn(1);
        given(institutions.findByCode(anyString())).willReturn(new InstitutionRow(
                CODE, "○○기관", "COOCON", "1234567890", "****************LE01",
                "Y", "사용", "설명", "20210401120000", "20260820103000"));
    }

    @Nested
    @DisplayName("인증되지 않은 호출 / unauthenticated calls")
    class Unauthenticated {

        @Test
        @DisplayName("등록은 인증 없이 거부된다 / register refuses an anonymous caller")
            // req: FR-AZ-D01, D-S2
        void registerRefusesAnonymous() throws Exception {
            mvc.perform(post(REGISTER).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY))
                    .andExpect(status().is4xxClientError());
            // 서비스에 닿지 않아야 한다. 닿으면 인증 없는 호출이 이미 원장에 쓴 것이다.
            // Must not reach the service: if it did, an unauthenticated call already wrote the ledger.
            verify(writeService, never()).register(anyString(), any(), anyString());
        }

        @Test
        @DisplayName("삭제는 인증 없이 거부된다 / delete refuses an anonymous caller")
            // req: FR-AZ-D01, FR-AZ-D04, D-S2
        void deleteRefusesAnonymous() throws Exception {
            mvc.perform(post(DELETE).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(DELETE_BODY))
                    .andExpect(status().is4xxClientError());
            verify(writeService, never()).delete(any(), anyString());
        }

        @Test
        @DisplayName("이용기관 문맥도 인증 없이 거부된다 / the institution context refuses an anonymous caller")
            // req: FR-AZ-D01, FR-SNDC-002
        void contextRefusesAnonymous() throws Exception {
            mvc.perform(get(CONTEXT)).andExpect(status().is4xxClientError());
            verify(institutions, never()).findByCode(anyString());
        }
    }

    @Nested
    @DisplayName("역할이 부족한 호출 / insufficient role")
    class WrongRole {

        @Test
        @WithMockUser(username = "u@client.example", roles = {"USER"})
        @DisplayName("이용기관 주체는 등록에서 403 이다 / a tenant principal gets 403 on register")
            // req: FR-AZ-D01, FR-AZ-D02, D-S2
        void tenantIsForbiddenOnRegister() throws Exception {
            mvc.perform(post(REGISTER).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY))
                    .andExpect(status().isForbidden());
            verify(writeService, never()).register(anyString(), any(), anyString());
        }

        @Test
        @WithMockUser(username = "u@client.example", roles = {"USER"})
        @DisplayName("이용기관 주체는 삭제에서 403 이다 / a tenant principal gets 403 on delete")
            // req: FR-AZ-D04, D-S2
        void tenantIsForbiddenOnDelete() throws Exception {
            // 레거시에서 삭제는 <b>어떤</b> 검사도 없었다 — 등록에는 브라우저 측 매니저 검사가
            // 있었지만 삭제 버튼에는 그것조차 없었다. 이 단정이 그 상태의 반대다.
            // In the legacy delete had <b>no</b> check: register at least had a browser-side manager
            // check, the delete button had none. This assertion is the inverse of that state.
            mvc.perform(post(DELETE).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(DELETE_BODY))
                    .andExpect(status().isForbidden());
            verify(writeService, never()).delete(any(), anyString());
        }

        @Test
        @WithMockUser(username = "u@client.example", roles = {"USER"})
        @DisplayName("이용기관 주체는 문맥 조회에서 403 이다 / a tenant principal gets 403 on the context read")
            // req: FR-AZ-D01, FR-SNDC-002
        void tenantIsForbiddenOnContext() throws Exception {
            mvc.perform(get(CONTEXT)).andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("CSRF / CSRF")
    class Csrf {

        @Test
        @WithMockUser(username = "op@example.com", roles = {"OPERATOR"})
        @DisplayName("토큰 없는 등록은 거부된다 / register without a token is refused")
            // req: ADR-014
        void registerRequiresCsrf() throws Exception {
            mvc.perform(post(REGISTER)
                            .contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY))
                    .andExpect(status().isForbidden());
            verify(writeService, never()).register(anyString(), any(), anyString());
        }

        @Test
        @WithMockUser(username = "op@example.com", roles = {"OPERATOR"})
        @DisplayName("토큰 없는 삭제는 거부된다 / delete without a token is refused")
            // req: ADR-014
        void deleteRequiresCsrf() throws Exception {
            mvc.perform(post(DELETE)
                            .contentType(MediaType.APPLICATION_JSON).content(DELETE_BODY))
                    .andExpect(status().isForbidden());
            verify(writeService, never()).delete(any(), anyString());
        }
    }

    @Nested
    @DisplayName("운영자 호출 / operator calls")
    class Operator {

        @Test
        @WithMockUser(username = "op@example.com", roles = {"OPERATOR"})
        @DisplayName("등록은 식별자를 돌려준다 / register returns the identifier")
            // req: FR-SNDC-001, FR-SND-007
        void registerReturnsRef() throws Exception {
            mvc.perform(post(REGISTER).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.affected").value(1))
                    .andExpect(jsonPath("$.ref").exists());
        }

        @Test
        @WithMockUser(username = "op@example.com", roles = {"OPERATOR"})
        @DisplayName("삭제는 실제로 바뀐 건수를 돌려준다 / delete returns the rows actually changed")
            // req: FR-SNDD-002, NFR-OPS-D02
        void deleteReturnsAffected() throws Exception {
            given(writeService.delete(any(), anyString())).willReturn(3);
            mvc.perform(post(DELETE).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(DELETE_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.affected").value(3));
        }

        @Test
        @WithMockUser(username = "op@example.com", roles = {"OPERATOR"})
        @DisplayName("D-S18 회귀 — 문맥 응답에 인증키가 없다 / no 인증키 in the context response")
            // req: FR-SNDC-002, NFR-SEC-PII-D02
        void contextCarriesNoCredential() throws Exception {
            // 레거시 등록 팝업은 이름을 채우려고 이용기관 상세조회를 불렀고, 그 서비스는 기관
            // 레코드 전체를 <b>평문 인증키와 함께</b> 반환했다(D-S18). 좁은 응답 타입이므로
            // 마스킹된 값조차 실릴 자리가 없다.
            // The legacy popup called the institution detail service to fill in a name, and it
            // returned the whole record <b>including the plaintext 인증키</b> (D-S18). The narrow
            // response type leaves nowhere for even the masked value to travel.
            mvc.perform(get(CONTEXT))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.institution").value(CODE))
                    .andExpect(jsonPath("$.institutionName").value("○○기관"))
                    .andExpect(jsonPath("$.authKey").doesNotExist())
                    .andExpect(jsonPath("$.authKeyMasked").doesNotExist())
                    .andExpect(jsonPath("$.businessNumber").doesNotExist());
        }
    }

    @Nested
    @DisplayName("실패 응답의 상태 코드 / failure status codes")
    class Failures {

        @Test
        @WithMockUser(username = "op@example.com", roles = {"OPERATOR"})
        @DisplayName("본문 검증 실패는 400 이다 / a body validation failure is a 400")
            // req: FR-SNDC-003, FR-SNDC-011, NFR-USE-D02
        void beanValidationIsFourHundred() throws Exception {
            // 사유가 빈 요청. 레거시는 이 요청을 받아들였다 — 클라이언트 검증이 무력했고(D-S11)
            // 서버에는 규칙이 없었다. 이제 400 이다(AMB-S10).
            // A request with an empty reason. The legacy accepted it: its client validation was
            // vacuous (D-S11) and the server had no rule. It is now a 400 (AMB-S10).
            mvc.perform(post(REGISTER).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"number\":\"0212345678\",\"description\":\"대표번호\",\"reason\":\"\"}"))
                    .andExpect(status().isBadRequest());
            verify(writeService, never()).register(anyString(), any(), anyString());
        }

        @Test
        @WithMockUser(username = "op@example.com", roles = {"OPERATOR"})
        @DisplayName("서버 검증 실패는 400 이며 칸을 지목한다 / a server validation failure is a 400 naming the field")
            // req: FR-SNDC-003, FR-SNDC-014, NFR-USE-D02
        void serverValidationNamesTheField() throws Exception {
            willThrow(new SenderNumberValidationException("number", "발신번호는 숫자만 입력할 수 있습니다."))
                    .given(writeService).register(anyString(), any(), anyString());

            mvc.perform(post(REGISTER).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("number"))
                    .andExpect(jsonPath("$.errors[0].message").exists());
        }

        @Test
        @WithMockUser(username = "op@example.com", roles = {"OPERATOR"})
        @DisplayName("중복은 409 이며 보유 기관을 밝히지 않는다 / a duplicate is a 409 that names no holder")
            // req: FR-SNDC-004, CONST-BIZ-D01
        void duplicateIsConflictWithoutDisclosure() throws Exception {
            willThrow(new SenderNumberDuplicateException())
                    .given(writeService).register(anyString(), any(), anyString());

            mvc.perform(post(REGISTER).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DUPLICATE"))
                    // 보유 기관을 알려 주면 번호를 넣어 보는 것만으로 다른 기관의 발신번호를
                    // 열거할 수 있다 — 레거시 중복검사가 정확히 그런 창구였다.
                    // Naming the holder would turn registration into an enumeration oracle, which is
                    // what the legacy duplicate check was.
                    .andExpect(jsonPath("$.errors[0].message")
                            .value(Matchers.not(Matchers.containsString("K0"))));
        }

        @Test
        @WithMockUser(username = "op@example.com", roles = {"OPERATOR"})
        @DisplayName("D-S1 — 살아 있는 행이 없으면 409 이며 200 이 아니다 / no live row is a 409, never a 200")
            // req: FR-SNDD-002, NFR-OPS-D02
        void noLiveRowIsConflictNotSuccess() throws Exception {
            willThrow(new SenderNumberNotLiveException())
                    .given(writeService).delete(any(), anyString());

            // 레거시는 이 경우에 200 과 "정상적으로 처리되었습니다" 를 돌려주었다. 이 단정 하나가
            // 이 스프린트의 수락 기준이다 — 결함은 삭제가 틀렸다는 것이 아니라 <b>틀렸는데 잘
            // 되었다고 말했다</b>는 것이었다.
            // The legacy returned 200 with a success sentence here. This single assertion is the
            // sprint's acceptance criterion: the defect was not that delete was wrong but that it was
            // wrong <b>and said it was fine</b>.
            mvc.perform(post(DELETE).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(DELETE_BODY))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("NOT_LIVE"));
        }

        @Test
        @WithMockUser(username = "op@example.com", roles = {"OPERATOR"})
        @DisplayName("D-S1 — 표시용 값을 식별자로 보내면 400 이다 / a display value as an identifier is a 400")
            // req: FR-SND-007, FR-SNDD-002
        void displayValueAsIdentifierIsRejected() throws Exception {
            // 레거시가 실제로 보낸 값이다: 마스킹된 표시값과 콤마로 이어붙인 목록. base64 복원이
            // 실패하므로 서비스에 닿기 전에 걸린다(TC-S004-03).
            // Exactly what the legacy sent: a masked display value and a comma-joined list. Base64
            // restoration fails, so it is caught before the service (TC-S004-03).
            mvc.perform(post(DELETE).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refs\":[\"01********8,15881234\"],\"reason\":\"해지\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("refs"));
            verify(writeService, never()).delete(any(), anyString());
        }

        @Test
        @WithMockUser(username = "op@example.com", roles = {"OPERATOR"})
        @DisplayName("빈 선택은 400 이며 삭제가 시작되지 않는다 / an empty selection is a 400 and starts no delete")
            // req: FR-SNDD-010, FR-SNDD-002
        void emptySelectionIsRejected() throws Exception {
            mvc.perform(post(DELETE).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refs\":[],\"reason\":\"해지\"}"))
                    .andExpect(status().isBadRequest());
            verify(writeService, never()).delete(any(), anyString());
        }

        @Test
        @WithMockUser(username = "op@example.com", roles = {"OPERATOR"})
        @DisplayName("범위를 벗어난 이용기관은 403 이다 / an out-of-scope institution is a 403")
            // req: FR-AZ-D03, FR-SNDC-012, NFR-SEC-TENANT-D01
        void outOfScopeInstitutionIsForbidden() throws Exception {
            willThrow(new AccessDeniedException("해당 이용기관에 대한 권한이 없습니다."))
                    .given(writeService).register(anyString(), any(), anyString());

            mvc.perform(post(REGISTER).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY))
                    .andExpect(status().isForbidden());
        }
    }
}
