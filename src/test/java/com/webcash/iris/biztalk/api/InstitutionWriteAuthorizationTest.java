package com.webcash.iris.biztalk.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.webcash.iris.auth.config.SecurityConfig;
import com.webcash.iris.biztalk.domain.InstitutionEdit;
import com.webcash.iris.biztalk.domain.InstitutionNotFoundException;
import com.webcash.iris.biztalk.domain.InstitutionRow;
import com.webcash.iris.biztalk.domain.InstitutionService;
import com.webcash.iris.biztalk.domain.InstitutionValidationException;
import com.webcash.iris.biztalk.domain.InstitutionWriteService;
import com.webcash.iris.common.audit.AuditService;
import com.webcash.iris.common.logging.GlobalExceptionHandler;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.OrderUtils;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 이용기관 쓰기 경로의 HTTP 계약·인가 검증 — T-I2a-07 / T-I2a-11.
 * HTTP contract and authorization verification for the 이용기관 write path.
 *
 * <h2>이 시험이 없으면 무엇이 주장으로 남는가 / what stays a claim without this suite</h2>
 * <p>{@code InstitutionWriteServiceTest} 는 <b>서비스</b>가 비운영자를 거부함을 증명한다. 그것은
 * HTTP 계층에 대해 아무것도 말해 주지 않는다 — 경로 규칙, {@code @PreAuthorize}, CSRF, 그리고
 * 예외 어드바이스는 필터 체인과 디스패처를 거쳐야 비로소 관찰된다. 레거시의 최고 심각도 결함
 * (D-I2)이 정확히 그 계층에 있었으므로, 거부를 단언하는 시험이 없는 인가 통제는 통제가 아니라
 * 주장이다.</p>
 * <p>{@code InstitutionWriteServiceTest} proves the <b>service</b> refuses a non-operator. That says
 * nothing about the HTTP layer: the routing rule, {@code @PreAuthorize}, CSRF and the exception
 * advice are only observable through the filter chain and the dispatcher. The slice's
 * highest-severity legacy defect (D-I2) lived in exactly that layer, so an authorization control with
 * no test asserting the refusal is a claim rather than a control.</p>
 *
 * <p>{@code @WebMvcTest} 는 DataSource 없이 <b>실제 시큐리티 필터 체인</b>을 통과시킨다 — 스프린트
 * T1 회고의 정정(A5)이 그 근거다.</p>
 * <p>{@code @WebMvcTest} exercises the <b>real security filter chain</b> without a DataSource, per the
 * correction recorded as retrospective action A5 in Sprint T1.</p>
 *
 * // source: WSVC.biztalk_admin_01_c001.xml / _01_l002.xml — both {@code <login>Y</login>}, no role check
 * // req: FR-AZ-I01, FR-AZ-I02, FR-INSTC-003, FR-INSTC-004, FR-INSTC-011, NFR-SEC-AUTHZ-I01, NFR-SEC-CSRF
 */
@WebMvcTest(controllers = InstitutionAdminController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        // HTTPS 강제를 끈다. 켜두면 모든 요청이 302 로 리다이렉트되어 401/403/200 판정이 불가하다.
        // Disabled so requests are not redirected to HTTPS, which would mask the outcome.
        "iris.auth.require-https=false"
})
class InstitutionWriteAuthorizationTest {

    private static final String CODE = "K00001";
    private static final String DETAIL = "/api/admin/institutions/" + CODE;
    private static final String ROTATE = DETAIL + "/key/rotate";

    /** 유효한 저장 본문. / A valid save body. */
    private static final String BODY = """
            {"name":"쿠콘_마이데이터사업1본부","englishName":"COOCON_Business1",
             "businessNumber":"1234567890","status":"Y","description":"설명"}
            """;

    @Autowired private MockMvc mvc;

    @MockBean private InstitutionService service;
    @MockBean private InstitutionWriteService writeService;
    // @WebMvcTest 슬라이스는 Filter 빈을 포함하므로 TenantContextFilter 가 생성되고,
    // 그 의존성인 AuditService 와 Clock 이 필요하다.
    // The slice includes Filter beans, so TenantContextFilter is created and its dependencies must exist.
    @MockBean private AuditService audit;
    @MockBean private Clock clock;

    private static InstitutionRow row() {
        return new InstitutionRow(CODE, "쿠콘_마이데이터사업1본부", "COOCON_Business1", "1234567890",
                "****************LE01", "Y", "사용", "설명", "20210401120000", "20260820103000");
    }

    @BeforeEach
    void setUp() {
        given(service.findByCode(anyString())).willReturn(row());
        given(writeService.update(anyString(), any(), anyString())).willReturn(row());
        given(writeService.rotateAuthKey(anyString(), anyString()))
                .willReturn("AbCdEfGhIjKlMnOpQrStUvWxYz9");
    }

    @Nested
    @DisplayName("인증되지 않은 호출 / unauthenticated calls")
    class Unauthenticated {

        @Test
        @DisplayName("상세조회는 인증 없이 거부된다 / the detail read refuses an anonymous caller")
            // req: FR-AZ-I01, D-I20
        void detailRefusesAnonymous() throws Exception {
            mvc.perform(get(DETAIL)).andExpect(status().is4xxClientError());
            // 서비스에 닿지 않아야 한다. 닿으면 인증 없는 호출이 이미 데이터베이스를 읽은 것이다.
            // Must not reach the service: if it did, an unauthenticated call already read the database.
            verify(service, never()).findByCode(anyString());
        }

        @Test
        @DisplayName("수정은 인증 없이 거부된다 / the update refuses an anonymous caller")
            // req: FR-AZ-I01, D-I2
        void updateRefusesAnonymous() throws Exception {
            mvc.perform(put(DETAIL).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().is4xxClientError());
            verify(writeService, never()).update(anyString(), any(), anyString());
        }

        @Test
        @DisplayName("재발급은 인증 없이 거부된다 / rotation refuses an anonymous caller")
            // req: FR-AZ-I01, FR-ATK-005
        void rotationRefusesAnonymous() throws Exception {
            mvc.perform(post(ROTATE).with(csrf())).andExpect(status().is4xxClientError());
            verify(writeService, never()).rotateAuthKey(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("역할이 부족한 호출 / insufficient role")
    class WrongRole {

        @Test
        @WithMockUser(username = "u@client.example", roles = {"USER"})
        @DisplayName("이용기관 주체는 수정에서 403 이다 / a tenant principal gets 403 on the update")
            // req: FR-AZ-I01, FR-AZ-I02, FR-AZ-I03, TC-I002-12, D-I2
        void tenantIsForbiddenOnUpdate() throws Exception {
            // 레거시에서는 로그인한 누구나 임의 기관을 덮어쓸 수 있었다. 그 화면에서 버튼이
            // 보이지 않는다는 것은 통제가 아니다 — 서비스를 직접 부르면 아무도 막지 않았다.
            // In the legacy any authenticated user could overwrite any institution: a hidden button is
            // not a control, and calling the service directly met no barrier.
            mvc.perform(put(DETAIL).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isForbidden());
            verify(writeService, never()).update(anyString(), any(), anyString());
        }

        @Test
        @WithMockUser(username = "u@client.example", roles = {"USER"})
        @DisplayName("이용기관 주체는 재발급에서 403 이다 / a tenant principal gets 403 on rotation")
            // req: FR-AZ-I01, FR-ATK-005
        void tenantIsForbiddenOnRotation() throws Exception {
            mvc.perform(post(ROTATE).with(csrf())).andExpect(status().isForbidden());
            verify(writeService, never()).rotateAuthKey(anyString(), anyString());
        }

        @Test
        @WithMockUser(username = "u@client.example", roles = {"USER"})
        @DisplayName("이용기관 주체는 상세조회에서 403 이다 / a tenant principal gets 403 on the detail")
            // req: FR-AZ-I03, FR-INSTC-010
        void tenantIsForbiddenOnDetail() throws Exception {
            mvc.perform(get(DETAIL)).andExpect(status().isForbidden());
            verify(service, never()).findByCode(anyString());
        }
    }

    @Nested
    @DisplayName("CSRF / cross-site request forgery")
    class Csrf {

        @Test
        @WithMockUser(username = "op@webcash.co.kr", roles = {"OPERATOR"})
        @DisplayName("토큰 없는 수정은 거부된다 / an update without a token is refused")
            // req: NFR-SEC-CSRF, ADR-014
        void updateWithoutTokenIsRefused() throws Exception {
            // 레거시에는 CSRF 방어가 전혀 없었다(csrf.ts 참조). 이 시험이 없으면 헤더를
            // 붙이는 클라이언트 코드만 있고 서버가 요구한다는 증거는 없다.
            // The legacy had no CSRF defence at all. Without this test there is client code that sends
            // the header and no evidence that the server requires it.
            mvc.perform(put(DETAIL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isForbidden());
            verify(writeService, never()).update(anyString(), any(), anyString());
        }

        @Test
        @WithMockUser(username = "op@webcash.co.kr", roles = {"OPERATOR"})
        @DisplayName("토큰 없는 재발급은 거부된다 / a rotation without a token is refused")
            // req: NFR-SEC-CSRF, FR-ATK-005
        void rotationWithoutTokenIsRefused() throws Exception {
            mvc.perform(post(ROTATE)).andExpect(status().isForbidden());
            verify(writeService, never()).rotateAuthKey(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("운영자 호출 / operator calls")
    @WithMockUser(username = "op@webcash.co.kr", roles = {"OPERATOR"})
    class Operator {

        @Test
        @DisplayName("상세 응답에 평문 인증키가 없다 / the detail response carries no plaintext key")
            // req: FR-INSTC-010, FR-ATK-002, TC-I002-21, D-I20
        void detailReturnsMaskedKey() throws Exception {
            mvc.perform(get(DETAIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.authKeyMasked").value("****************LE01"))
                    // 계약에 authKey 라는 이름의 필드가 아예 없다 — 마스킹은 값의 문제가 아니라
                    // 타입의 문제다(InstitutionRow 는 마스킹된 값만 갖는다).
                    // The contract has no field named authKey at all: masking is a property of the type,
                    // not of a value (InstitutionRow holds only the masked form).
                    .andExpect(jsonPath("$.authKey").doesNotExist());
        }

        @Test
        @DisplayName("수정이 저장되고 저장된 행이 돌아온다 / the update saves and returns the stored row")
            // req: FR-INSTC-001, FR-INSTC-006
        void updateSaves() throws Exception {
            mvc.perform(put(DETAIL).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.lastModifiedAt").value("20260820103000"));

            verify(writeService).update(
                    org.mockito.ArgumentMatchers.eq(CODE),
                    org.mockito.ArgumentMatchers.eq(new InstitutionEdit(
                            "쿠콘_마이데이터사업1본부", "COOCON_Business1",
                            "1234567890", "Y", "설명")),
                    anyString());
        }

        @Test
        @DisplayName("본문의 인증키·기관코드는 계약에 없어 무시된다 / a body key or code is not in the contract")
            // req: FR-INSTC-002, FR-INSTC-011, TM-I022, TC-I002-29
        void bodyCannotCarryKeyOrCode() throws Exception {
            String crafted = """
                    {"code":"K09999","authKey":"MALICIOUSLYsuppliedKEY00001",
                     "name":"쿠콘_마이데이터사업1본부","englishName":"COOCON_Business1",
                     "businessNumber":"1234567890","status":"Y","description":"설명"}
                    """;

            mvc.perform(put(DETAIL).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(crafted))
                    .andExpect(status().isOk());

            // 대상은 경로가 정하고, 인증키는 이 경로로 바뀔 수 없다. 요청 레코드에 필드가
            // 없으므로 두 값은 도메인에 도달할 방법이 없다.
            // The path names the target and the key cannot change on this path: with no fields on the
            // request record, neither value has any way to reach the domain.
            verify(writeService).update(
                    org.mockito.ArgumentMatchers.eq(CODE),
                    org.mockito.ArgumentMatchers.eq(new InstitutionEdit(
                            "쿠콘_마이데이터사업1본부", "COOCON_Business1",
                            "1234567890", "Y", "설명")),
                    anyString());
        }

        @Test
        @DisplayName("재발급 응답은 새 키만 담는다 / the rotation response carries only the new key")
            // req: FR-ATK-001, FR-ATK-004, FR-INSTC-011
        void rotationReturnsTheKeyOnce() throws Exception {
            mvc.perform(post(ROTATE).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.authKey").value("AbCdEfGhIjKlMnOpQrStUvWxYz9"))
                    // 27자 Base62 — 레거시의 20자 Math.random() 이 아니다(D-I4).
                    // 27 Base62 characters, not the legacy's 20 from Math.random() (D-I4).
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("****"))));
        }
    }

    @Nested
    @DisplayName("검증과 오류 매핑 / validation and error mapping")
    @WithMockUser(username = "op@webcash.co.kr", roles = {"OPERATOR"})
    class Validation {

        @Test
        @DisplayName("기관명이 비면 400 VALIDATION_FAILED / a blank 기관명 is a 400")
            // req: FR-INSTC-003, TC-I002-07, D-I19
        void blankNameIsRejected() throws Exception {
            String body = """
                    {"name":"","englishName":"COOCON","businessNumber":"1234567890",
                     "status":"Y","description":""}
                    """;

            mvc.perform(put(DETAIL).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("name"));

            verify(writeService, never()).update(anyString(), any(), anyString());
        }

        @Test
        @DisplayName("사업자등록번호에 문자가 있으면 400 / letters in 사업자등록번호 are a 400")
            // req: FR-INSTC-009, TC-I002-09, D-I19
        void letteredBusinessNumberIsRejected() throws Exception {
            String body = """
                    {"name":"쿠콘","englishName":"COOCON","businessNumber":"12345abcde",
                     "status":"Y","description":""}
                    """;

            mvc.perform(put(DETAIL).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("businessNumber"));
        }

        @Test
        @DisplayName("사용여부 'D' 는 400 이다 / status 'D' is a 400")
            // req: FR-INSTC-015, TC-I002-28, TM-I023
        void deletedStatusIsRejected() throws Exception {
            String body = """
                    {"name":"쿠콘","englishName":"COOCON","businessNumber":"1234567890",
                     "status":"D","description":""}
                    """;

            mvc.perform(put(DETAIL).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("status"));

            // 논리 삭제가 수정 폼을 통해 일어나서는 안 된다 — 확인도 감사 기록도 없다.
            // A logical delete must not happen through the edit form: no confirmation, no audit entry.
            verify(writeService, never()).update(anyString(), any(), anyString());
        }

        @Test
        @DisplayName("도메인 검증 실패도 같은 형태로 나간다 / a domain violation answers in the same shape")
            // req: FR-INSTC-003, FR-INSTC-016
        void domainViolationUsesTheSameShape() throws Exception {
            // 애노테이션 검증과 서비스 검증이 서로 다른 모양으로 응답하면 화면은 같은 실패를
            // 두 가지 방법으로 해석해야 한다.
            // If annotation and service validation answered differently, the screen would need two ways
            // to read one kind of failure.
            willThrow(new InstitutionValidationException("businessNumber", "사업자등록번호는 숫자 10자리여야 합니다."))
                    .given(writeService).update(anyString(), any(), anyString());

            mvc.perform(put(DETAIL).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("businessNumber"));
        }

        @Test
        @DisplayName("없는 기관은 404 이며 코드를 되돌려주지 않는다 / a missing institution is a 404 that echoes no code")
            // req: FR-INSTC-004, FR-INSTC-005, TM-I002
        void missingInstitutionIsNotFound() throws Exception {
            willThrow(new InstitutionNotFoundException(CODE))
                    .given(writeService).update(anyString(), any(), anyString());

            mvc.perform(put(DETAIL).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                    // 어느 코드가 존재하고 어느 코드가 존재하지 않는지 알려주면 응답만으로
                    // 등록 기관을 열거할 수 있다(D-I3 이 정확히 그 창구였다).
                    // Telling a caller which codes exist would let the registry be enumerated from
                    // responses alone — which is exactly what D-I3 was.
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString(CODE))));
        }
    }

    /**
     * 예외 어드바이스가 포괄 처리자보다 앞선다. / The advice precedes the catch-all.
     *
     * <p>{@code GlobalExceptionHandler} 는 {@code @ExceptionHandler(Exception.class)} 를 갖는다.
     * 두 어드바이스의 우선순위가 같으면 Spring 은 어느 쪽이 이길지 보장하지 않으며, 그때 404 와
     * 400 이 500 으로 나간다 — 컴파일도 통과하고 다른 시험도 통과하므로 순서를 직접 단언하지
     * 않으면 아무것도 이 회귀를 잡지 못한다. 로그인 슬라이스가 같은 결함을 한 번 겪었다.</p>
     * <p>{@code GlobalExceptionHandler} declares {@code @ExceptionHandler(Exception.class)}. With equal
     * order Spring does not guarantee which advice wins, and 404s and 400s then leave as 500s. It
     * compiles and every other test passes, so nothing catches the regression unless the ordering is
     * asserted. The 로그인 slice hit exactly this defect once.</p>
     */
    // req: FR-INSTC-004, NFR-USE-D02
    @Test
    @DisplayName("이용기관 어드바이스가 전역 어드바이스보다 앞선다 / the institution advice precedes the global one")
    void adviceOutranksTheCatchAll() {
        Integer institution = OrderUtils.getOrder(InstitutionExceptionHandler.class);
        Integer global = OrderUtils.getOrder(GlobalExceptionHandler.class);

        assertThat(institution)
                .as("InstitutionExceptionHandler must declare an explicit order")
                .isNotNull();
        assertThat(global).isNotNull();
        // 낮은 값이 높은 우선순위다. / A lower value means higher precedence.
        assertThat(institution)
                .as("the specific advice must win, or 404/400 surface as 500")
                .isLessThan(global);
    }
}
