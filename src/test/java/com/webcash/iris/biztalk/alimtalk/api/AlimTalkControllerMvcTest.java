package com.webcash.iris.biztalk.alimtalk.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.webcash.iris.biztalk.alimtalk.domain.TemplateRegistry;
import com.webcash.iris.biztalk.alimtalk.domain.TemplateSummary;
import com.webcash.iris.biztalk.alimtalk.domain.TranIdGenerator;
import com.webcash.iris.biztalk.alimtalk.infra.db.TemplateMapper;
import com.webcash.iris.biztalk.alimtalk.infra.vendor.SenderProfileKeyResolver;
import com.webcash.iris.common.tenant.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * {@link AlimTalkController} 요청·응답 배선 검증. / Request and response wiring for {@link AlimTalkController}.
 *
 * <h2>standalone 인 이유와 그 한계 / why standalone, and its limit</h2>
 * <p>{@code MockMvcBuilders.standaloneSetup} 은 Spring Boot 자동설정 없이 컨트롤러 하나만 올린다.
 * 이 환경에서 실행 가능한 가장 높은 수준이며, 커버리지 0 % 였던 컨트롤러의 요청 처리 경로를
 * 실제로 실행한다.</p>
 * <p>{@code standaloneSetup} raises one controller with no Boot auto-configuration. It is the highest
 * level runnable in this environment and it actually executes the request-handling paths of a controller
 * that was at 0 % coverage.</p>
 *
 * <p><b>이것이 인가를 검증하지 않는다는 점이 중요하다.</b> standalone 설정에는 보안 프록시가
 * 없으므로 {@code @PreAuthorize} 가 적용되지 않는다. 미인증 호출이 403 을 받는지는 여전히
 * 검증되지 않았고(A1-19 미완), {@code AlimTalkControllerSecurityTest} 가 <b>선언의 존재</b>만
 * 확인한다. 두 테스트를 합쳐도 통합 보안 테스트 하나를 대체하지 못한다 — 그 사실을 코드에
 * 남겨 다음 사람이 오해하지 않게 한다.</p>
 * <p><b>It does not verify authorization.</b> A standalone setup has no security proxy, so
 * {@code @PreAuthorize} does not apply. Whether an anonymous call receives 403 is still unverified
 * (A1-19 unfinished); {@code AlimTalkControllerSecurityTest} checks only that the <b>declaration exists</b>.
 * Together they still do not replace one integration security test — recorded here so the next reader
 * does not assume otherwise.</p>
 *
 * // source: biztalk_admin_61_view.jsp, biztalk_admin_61.js
 * // req: FR-ATT-001, FR-ATV-001, FR-ATV-003, FR-ATV-007, FR-ATC-012, FR-ATS-006, FR-ATS-007
 */
class AlimTalkControllerMvcTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private MockMvc mvc;

    /** 템플릿 매퍼 대역. / A stub template mapper. */
    /**
     * 호출되면 실패하는 아웃박스 매퍼. / An outbox mapper that fails if it is called.
     *
     * <p>이 묶음에서는 발송이 꺼져 있으므로 아웃박스에 아무것도 쓰여서는 안 된다. 조용히
     * 성공하는 대역을 두면 그 성질이 검증되지 않는다 — 발송이 꺼진 상태에서 접수가 일어나도
     * 테스트가 통과해 버린다.</p>
     * <p>Dispatch is off in this group, so nothing may be written to the outbox. A stub that quietly
     * succeeds would leave that property unverified: acceptance while dispatch is off would still pass.</p>
     *
     * // req: FR-ATS-003, NFR-OPS-A02
     */
    private static final class UnusedOutboxMapper
            implements com.webcash.iris.biztalk.alimtalk.infra.db.OutboxMapper {

        @Override
        public int insert(com.webcash.iris.biztalk.alimtalk.domain.OutboxEntry entry) {
            throw new AssertionError(
                    "the outbox must not be written while dispatch is disabled");
        }

        @Override
        public java.util.List<com.webcash.iris.biztalk.alimtalk.domain.OutboxEntry> claim(
                java.util.List<String> statuses, java.time.LocalDateTime now, int limit) {
            throw new AssertionError("the dispatcher must not run from this test");
        }

        @Override
        public int markClaimed(long outboxId, java.time.LocalDateTime claimedUntil) {
            throw new AssertionError("the dispatcher must not run from this test");
        }

        @Override
        public int recordOutcome(long outboxId, String status, String lastError) {
            throw new AssertionError("the dispatcher must not run from this test");
        }
        @Override
        public java.util.List<com.webcash.iris.biztalk.alimtalk.domain.OutboxEntry> findByTranId(
                String isCd, String tranId) {
            return java.util.List.of();
        }


        @Override
        public int countUnfinished(String isCd) {
            return 0;
        }
    }

    private static final class StubMapper implements TemplateMapper {
        @Override
        public List<TemplateSummary> findByInstitution(String institutionCode) {
            return List.of(new TemplateSummary("TMPL_0001", "결제 안내"));
        }

        @Override
        public String findTemplateBody(String institutionCode, String templateCode) {
            return "TMPL_0001".equals(templateCode) ? "#{금액}원이 결제되었습니다." : null;
        }
    }

    @BeforeEach
    void setUp() {
        AtomicLong sequence = new AtomicLong();
        TranIdGenerator tranIds = new TranIdGenerator(
                'T',
                (institutionCode, date) -> sequence.getAndIncrement(),
                Clock.fixed(Instant.parse("2026-08-18T05:00:00Z"), KST));

        // 발송은 <b>꺼진</b> 상태로 세운다. 이 묶음이 검증하는 것은 작성·검증 경로이고,
        // 켜진 상태의 동작은 AlimTalkSendEndpointTest 가 따로 다룬다. 기본값이 꺼짐인 것을
        // 여기서도 그대로 쓰는 편이 낫다 — 테스트가 운영 기본값과 다른 전제 위에 서면,
        // 통과가 운영 동작을 말해 주지 않는다.
        // Dispatch is set up DISABLED. This group verifies the compose and validate paths; the enabled
        // behaviour is covered separately by AlimTalkSendEndpointTest. Keeping the production default here
        // matters: a suite standing on a different premise than production stops telling us about it.
        // 발송이 꺼져 있으므로 이 서비스는 호출되지 않는다. 그럼에도 실제 타입을 넣는 이유:
        // 생성자가 요구하는 것을 전부 채워야 배선 누락(D-A38 류)이 테스트에서도 드러난다.
        // Dispatch is off so this service is never called. A real instance is still supplied because
        // filling every constructor parameter is what makes a wiring omission visible in tests too.
        com.webcash.iris.biztalk.alimtalk.domain.AlimTalkSendService sends =
                new com.webcash.iris.biztalk.alimtalk.domain.AlimTalkSendService(
                        new UnusedOutboxMapper());
        mvc = MockMvcBuilders
                .standaloneSetup(new AlimTalkController(tranIds, new TemplateRegistry(new StubMapper()),
                        new SenderProfileKeyResolver(java.util.Map.of("K00001", "test-profile-key"), null),
                        sends,
                        new com.webcash.iris.biztalk.alimtalk.infra.vendor.VendorPayloadMapper(),
                        false))
                .build();

        TenantContext.set(new TenantContext.TenantPrincipal("operator@example.com", "K00001", true));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("템플릿 / templates")
    class Templates {

        @Test
        @DisplayName("이용기관의 템플릿 목록을 돌려준다 / returns the institution's templates")
        // req: FR-ATT-001, FR-ATT-002
        void returnsTemplates() throws Exception {
            mvc.perform(get("/api/admin/alimtalk/templates").param("institution", "K00001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].templateCode").value("TMPL_0001"))
                    .andExpect(jsonPath("$[0].templateTitle").value("결제 안내"));
        }

        @Test
        @DisplayName("등록 템플릿에 부합하면 발송을 허용한다 / a conformant message permits a send")
        // req: FR-ATV-001, FR-ATV-002
        void conformantMessagePermitsSend() throws Exception {
            mvc.perform(post("/api/admin/alimtalk/templates/validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"institutionCode":"K00001","templateCode":"TMPL_0001",
                                     "content":"50,000원이 결제되었습니다."}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.registered").value(true))
                    .andExpect(jsonPath("$.permitsSend").value(true))
                    .andExpect(jsonPath("$.variableValues.금액").value("50,000"));
        }

        @Test
        @DisplayName("FR-ATV-003 — 미등록 코드를 불일치와 구분한다 / unregistered is distinct from mismatched")
        // req: FR-ATV-003
        void unregisteredIsDistinct() throws Exception {
            mvc.perform(post("/api/admin/alimtalk/templates/validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"institutionCode":"K00001","templateCode":"NOPE","content":"아무 내용"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.registered").value(false))
                    .andExpect(jsonPath("$.permitsSend").value(false));
        }

        @Test
        @DisplayName("D-A6 정정이 HTTP 경로에서도 유지된다 / the D-A6 correction holds over HTTP")
        // req: FR-ATV-004
        void correctedBehaviourHoldsOverHttp() throws Exception {
            // 레거시가 거절했던 입력이 이 경로에서도 통과해야 한다. 매처가 한 곳이므로
            // 수동 탭·자동 검증·HTTP 응답이 어긋날 수 없다(FR-ATV-007).
            // An input the legacy rejected must pass here too. One matcher means the manual tab, the
            // automatic check and the HTTP response cannot disagree (FR-ATV-007).
            mvc.perform(post("/api/admin/alimtalk/validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"template":"#{name}님 안녕","content":"김님철수님 안녕"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.conformant").value(true))
                    .andExpect(jsonPath("$.variableValues.name").value("김님철수"));
        }

        @Test
        @DisplayName("FR-ATV-008 — ${...} 템플릿은 400 으로 거절한다 / a ${...} template is rejected")
        // req: FR-ATV-008
        void dollarSyntaxIsRejected() throws Exception {
            // 템플릿 자체의 문제이므로 내용 불일치(200 + conformant=false)와 구분해 400 으로
            // 돌려준다. 레거시는 ${...} 를 조용히 받아들여 벤더에서만 실패하게 했다.
            // A problem with the template itself, so it is a 400 rather than a content mismatch
            // (200 with conformant=false). The legacy accepted ${...} silently and failed only at the vendor.
            mvc.perform(post("/api/admin/alimtalk/validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"template":"${name}님","content":"김철수님"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.templateError").exists());
        }
    }

    @Nested
    @DisplayName("수신번호 / recipients")
    class Recipients {

        @Test
        @DisplayName("D-A26 — 제외 대상을 발송 전에 응답으로 돌려준다 / exclusions are returned before sending")
        // req: FR-ATS-007, FR-ATC-012
        void exclusionsAreReturnedBeforeSending() throws Exception {
            mvc.perform(post("/api/admin/alimtalk/recipients/preview")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"institutionCode":"K00001",
                                     "recipients":"01011112222, 01011112222, 0212345678"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.validCount").value(1))
                    .andExpect(jsonPath("$.duplicatesRemoved").value(1))
                    .andExpect(jsonPath("$.excluded[0]").value("0212345678"))
                    .andExpect(jsonPath("$.requiresConfirmation").value(true));
        }

        @Test
        @DisplayName("NFR-SEC-PII-A01 — 응답의 번호가 마스킹되어 있다 / numbers are masked in the response")
        // req: NFR-SEC-PII-A01, NFR-SEC-PII-A02
        void numbersAreMaskedInTheResponse() throws Exception {
            mvc.perform(post("/api/admin/alimtalk/recipients/preview")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"institutionCode":"K00001","recipients":"01012345678"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.maskedRecipients[0]").value("010****5678"))
                    // 평문이 응답 본문 어디에도 없어야 한다 — 화면이 가리는 것으로는 늦다.
                    // The clear value must appear nowhere in the body: hiding it in the screen is too late.
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("01012345678"))));
        }

        @Test
        @DisplayName("D-A31 — 유효 수신자가 없으면 거래고유번호를 발급하지 않는다 / no tran_id when nobody to send to")
        // req: FR-ATS-006
        void noTranIdWhenNobodyToSendTo() throws Exception {
            // 발급 자체가 순번을 소비하므로, 보낼 수 없는 요청에 번호를 낭비하지 않는다.
            // Issuing consumes a sequence value, so a request that cannot be sent does not spend one.
            mvc.perform(post("/api/admin/alimtalk/recipients/preview")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"institutionCode":"K00001","recipients":"abc, def"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.validCount").value(0))
                    .andExpect(jsonPath("$.tranId").doesNotExist());
        }

        @Test
        @DisplayName("유효한 수신자가 있으면 거래고유번호를 발급한다 / issues a tran_id when there is someone to send to")
        // req: FR-ATS-008
        void issuesTranIdWhenThereIsSomeoneToSendTo() throws Exception {
            mvc.perform(post("/api/admin/alimtalk/recipients/preview")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"institutionCode":"K00001","recipients":"01011112222"}
                                    """))
                    .andExpect(status().isOk())
                    // 환경 구분자 T = staging. 운영 형식의 값이 스테이징에서 나오지 않는다.
                    // Discriminator T = staging: a production-shaped value cannot come from staging.
                    .andExpect(jsonPath("$.tranId").value(org.hamcrest.Matchers.startsWith("T260818")));
        }
    }

    @Nested
    @DisplayName("JSON 생성 — 계약 적합 payload / compose a conforming payload")
    class Compose {

        private static final String FORM = """
                {"isCd":"K00001","tranId":"T260818001","recipients":"01011112222",
                 "senderNumber":"0212345678","reqdate":"20260819140000",
                 "templateCode":"TMPL_0001","templateTitle":"결제 안내",
                 "msg":"50,000원이 결제되었습니다.",
                 "buttons":[{"name":"자세히","type":"WL","urlMobile":"https://m","urlPc":"https://pc",
                             "schemeIos":"","schemeAndroid":""}],
                 "failback":{"type":"LMS","subject":"[안내]","msg":"결제 안내","imgId":""}}
                """;

        @Test
        @DisplayName("D-A1 — 생성된 payload 가 failback_data 를 쓴다 / the composed payload uses failback_data")
        // req: FR-ATC-001, FR-ATC-002, CONST-DATA-A01
        void composedPayloadUsesFailbackData() throws Exception {
            // 레거시는 브라우저에서 객체를 손으로 쌓았고 그래서 failback 을 잘못 썼다. 조립이
            // 서버로 오면서 payload 는 ContractConformanceTest 가 지키는 타입을 지나간다.
            // The legacy assembled by hand in the browser and got the key wrong. With assembly on the
            // server the payload passes through the type the conformance test guards.
            mvc.perform(post("/api/admin/alimtalk/compose")
                            .contentType(MediaType.APPLICATION_JSON).content(FORM))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.payload").value(org.hamcrest.Matchers.containsString("failback_data")))
                    .andExpect(jsonPath("$.payload").value(
                            org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("\"failback\":"))));
        }

        @Test
        @DisplayName("D-A2 — 계약에 없는 필드가 생성되지 않는다 / no undeclared field is composed")
        // req: FR-ATC-003
        void noUndeclaredFieldIsComposed() throws Exception {
            mvc.perform(post("/api/admin/alimtalk/compose")
                            .contentType(MediaType.APPLICATION_JSON).content(FORM))
                    .andExpect(jsonPath("$.payload").value(org.hamcrest.Matchers.allOf(
                            org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("msg_type")),
                            org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("kko_header")),
                            org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("highlight")),
                            org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("summary")))));
        }

        @Test
        @DisplayName("FR-AZ-A05 — 미리보기에 자격증명도 평문 번호도 없다 / neither credential nor clear number")
        // req: FR-AZ-A05, NFR-SEC-PII-A01
        void previewCarriesNeitherCredentialNorClearNumber() throws Exception {
            mvc.perform(post("/api/admin/alimtalk/compose")
                            .contentType(MediaType.APPLICATION_JSON).content(FORM))
                    // sender_key 는 계약 필드이므로 payload 에 있어야 하지만, 미리보기에서는
                    // ProfileKey 가 가려진 값으로 직렬화된다.
                    // sender_key is a contract field so it belongs in the payload, but ProfileKey
                    // serialises redacted in this preview.
                    .andExpect(jsonPath("$.payload").value(org.hamcrest.Matchers.containsString("REDACTED")))
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("test-profile-key"))))
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("01011112222"))));
        }

        @Test
        @DisplayName("D-A7 — 길이를 넘으면 어긋난 규칙을 이름으로 말한다 / an over-length value names the rule")
        // req: FR-ATC-005, NFR-USE-A03
        void overLengthNamesTheRule() throws Exception {
            String longTranId = """
                    {"isCd":"K00001","tranId":"TOOLONGTRANID","recipients":"01011112222",
                     "senderNumber":"0212345678","templateCode":"T","msg":"x","buttons":[],
                     "failback":{"type":"","subject":"","msg":"","imgId":""}}
                    """;

            mvc.perform(post("/api/admin/alimtalk/compose")
                            .contentType(MediaType.APPLICATION_JSON).content(longTranId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.problems", org.hamcrest.Matchers.hasItem(
                            org.hamcrest.Matchers.containsString("거래고유번호"))));
        }

        @Test
        @DisplayName("D-A9 — 불완전한 버튼을 조용히 버리지 않는다 / an incomplete button is not dropped silently")
        // req: FR-ATC-009
        void incompleteButtonIsNotDroppedSilently() throws Exception {
            // 레거시는 이름이 빈 버튼을 .filter(b => b.name) 으로 버렸다 — 운영자는 화면에
            // 설정된 버튼을 보고 있었지만 payload 에는 없었다.
            // The legacy dropped a nameless button via .filter(b => b.name): the operator saw one in the
            // form and none in the payload.
            String blankButton = """
                    {"isCd":"K00001","tranId":"T260818001","recipients":"01011112222",
                     "senderNumber":"0212345678","templateCode":"T","msg":"x",
                     "buttons":[{"name":"","type":"WL","urlMobile":"https://m","urlPc":"",
                                 "schemeIos":"","schemeAndroid":""}],
                     "failback":{"type":"","subject":"","msg":"","imgId":""}}
                    """;

            mvc.perform(post("/api/admin/alimtalk/compose")
                            .contentType(MediaType.APPLICATION_JSON).content(blankButton))
                    .andExpect(jsonPath("$.problems", org.hamcrest.Matchers.hasItem(
                            org.hamcrest.Matchers.containsString("버튼"))));
        }

        @Test
        @DisplayName("D-A17 — 대체 전송 규칙 위반을 보고한다 / a fallback rule violation is reported")
        // req: FR-ATC-002
        void fallbackRuleViolationIsReported() throws Exception {
            // SMS 는 제목을 허용하지 않는다. 레거시는 대체 전송 항목을 전혀 검증하지 않았다.
            // SMS permits no subject. The legacy validated fallback fields not at all.
            String smsWithSubject = """
                    {"isCd":"K00001","tranId":"T260818001","recipients":"01011112222",
                     "senderNumber":"0212345678","templateCode":"T","msg":"x","buttons":[],
                     "failback":{"type":"SMS","subject":"[안내]","msg":"본문","imgId":""}}
                    """;

            mvc.perform(post("/api/admin/alimtalk/compose")
                            .contentType(MediaType.APPLICATION_JSON).content(smsWithSubject))
                    .andExpect(jsonPath("$.problems", org.hamcrest.Matchers.hasItem(
                            org.hamcrest.Matchers.containsString("대체 전송"))));
        }
    }

    /**
     * 다건 작성 — 계약 {@code ADV_KKO_AT_SEND_M}. / batch composition against the batch contract.
     *
     * <p>이 묶음이 뒤늦게 추가된 이유를 적어 둔다. `/compose/batch` 를 만들 때 화면 테스트만
     * 붙였고, 화면 테스트는 서버를 대역으로 세운다 — 즉 이 엔드포인트는 <b>한 번도 실행되지
     * 않은 채</b> "테스트됨" 으로 보였다. 커버리지를 다시 재고 나서야 드러났다
     * ({@code AlimTalkBatchComposeRequest} 0 %, 컨트롤러 55.7 %). 반복 3에서 겪은 것과 같은
     * 종류의 착시다.</p>
     * <p>Why this group arrived late: when `/compose/batch` was written only screen tests were added, and
     * screen tests stub the server — so the endpoint looked "tested" while <b>never having executed</b>.
     * Re-measuring coverage revealed it ({@code AlimTalkBatchComposeRequest} at 0 %, the controller at
     * 55.7 %). The same illusion as in iteration 3.</p>
     */
    @Nested
    @DisplayName("FR-ATC-004 — 다건 작성 / batch composition")
    class ComposeBatch {

        private static final String TWO_ITEMS = """
                {"isCd":"K00001","tranId":"T260819001",
                 "items":[
                   {"recipient":"01011112222","senderNumber":"0212345678","reqdate":"20260820090000",
                    "templateCode":"TMPL_0001","templateTitle":"결제 안내","msg":"첫 번째",
                    "buttons":[],"failback":{"type":"","subject":"","msg":"","imgId":""}},
                   {"recipient":"01011113333","senderNumber":"0212345678","reqdate":"",
                    "templateCode":"TMPL_0001","templateTitle":"","msg":"두 번째",
                    "buttons":[],"failback":{"type":"","subject":"","msg":"","imgId":""}}]}
                """;

        @Test
        @DisplayName("D-A3 — 항목마다 순번이 붙고 시스템이 부여한다 / each item carries a system-assigned order")
        // req: FR-ATC-004, CONST-DATA-A01
        void eachItemCarriesASystemAssignedOrder() throws Exception {
            // 레거시는 order 를 아예 내보내지 않았다. 수신자와 메시지의 대응이 배열 위치에만
            // 의존했고, 그 위치는 시스템 경계를 넘으면 보장되지 않는다.
            // The legacy emitted no order at all, leaving recipient-to-message association to array
            // position — which is not guaranteed across a system boundary.
            mvc.perform(post("/api/admin/alimtalk/compose/batch")
                            .contentType(MediaType.APPLICATION_JSON).content(TWO_ITEMS))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.payload").value(org.hamcrest.Matchers.allOf(
                            org.hamcrest.Matchers.containsString("\"order\" : \"1\""),
                            org.hamcrest.Matchers.containsString("\"order\" : \"2\""))));
        }

        @Test
        @DisplayName("D-A14 — 항목마다 예약발송시간을 담을 수 있다 / a per-item reqdate is carried")
        // req: FR-ATC-004, FR-ATC-007
        void perItemReqdateIsCarried() throws Exception {
            // 계약은 reqdate 를 항목마다 선언하는데 레거시 화면은 수집하지 않았다 — 다건
            // 예약 발송이 불가능했다. 설계 결정이 아니라 누락이었다.
            // The contract declares reqdate per item but the legacy screen never collected it, so batch
            // reservation was impossible. An omission, not a design decision.
            mvc.perform(post("/api/admin/alimtalk/compose/batch")
                            .contentType(MediaType.APPLICATION_JSON).content(TWO_ITEMS))
                    .andExpect(jsonPath("$.payload").value(
                            org.hamcrest.Matchers.containsString("20260820090000")));
        }

        @Test
        @DisplayName("FR-AZ-A05 — 다건에서도 평문 번호도 자격증명도 나가지 않는다 / neither leaves in batch either")
        // req: FR-AZ-A05, NFR-SEC-PII-A01
        void neitherCredentialNorClearNumberLeaves() throws Exception {
            mvc.perform(post("/api/admin/alimtalk/compose/batch")
                            .contentType(MediaType.APPLICATION_JSON).content(TWO_ITEMS))
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("01011112222"))))
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("test-profile-key"))))
                    .andExpect(jsonPath("$.payload").value(
                            org.hamcrest.Matchers.containsString("REDACTED")));
        }

        @Test
        @DisplayName("항목마다 수신번호는 하나여야 한다 / one recipient per item")
        // req: FR-ATC-004
        void severalRecipientsInOneItemIsReported() throws Exception {
            // 계약은 msg_data 항목마다 수신번호 하나를 선언한다. 여럿을 넣으면 어느 것이 이
            // 메시지의 대상인지 알 수 없고, 조용히 첫 번째를 고르면 나머지는 사라진다.
            // The contract declares one recipient per item. Several leaves it unclear which the message is
            // for, and silently taking the first makes the rest vanish.
            String twoInOne = """
                    {"isCd":"K00001","tranId":"T260819001",
                     "items":[{"recipient":"01011112222,01011113333","senderNumber":"0212345678",
                               "reqdate":"","templateCode":"T","templateTitle":"","msg":"x",
                               "buttons":[],"failback":{"type":"","subject":"","msg":"","imgId":""}}]}
                    """;

            mvc.perform(post("/api/admin/alimtalk/compose/batch")
                            .contentType(MediaType.APPLICATION_JSON).content(twoInOne))
                    .andExpect(jsonPath("$.problems", org.hamcrest.Matchers.hasItem(
                            org.hamcrest.Matchers.containsString("수신번호는 하나"))));
        }

        @Test
        @DisplayName("빈 목록은 보고된다 / an empty item list is reported")
        // req: FR-ATC-004, NFR-USE-A03
        void emptyItemListIsReported() throws Exception {
            mvc.perform(post("/api/admin/alimtalk/compose/batch")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"isCd\":\"K00001\",\"tranId\":\"T260819001\",\"items\":[]}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.problems", org.hamcrest.Matchers.hasItem(
                            org.hamcrest.Matchers.containsString("메시지 데이터가 없습니다"))));
        }

        @Test
        @DisplayName("항목의 위반은 몇 번째인지와 함께 보고된다 / an item's violation names which item")
        // req: FR-ATC-005, NFR-USE-A03
        void itemViolationNamesTheItem() throws Exception {
            // 레거시는 네 개의 출구에서 모두 첫 오류 하나만 돌려주었고, 다건에서는 그것이
            // 몇 번째 메시지의 문제인지도 말하지 않았다.
            // The legacy returned only the first error from four exit paths, and in batch mode never said
            // which message it belonged to.
            String badSecondItem = """
                    {"isCd":"K00001","tranId":"T260819001",
                     "items":[
                       {"recipient":"01011112222","senderNumber":"0212345678","reqdate":"",
                        "templateCode":"T","templateTitle":"","msg":"정상",
                        "buttons":[],"failback":{"type":"","subject":"","msg":"","imgId":""}},
                       {"recipient":"02-1234-5678","senderNumber":"0212345678","reqdate":"",
                        "templateCode":"T","templateTitle":"","msg":"두 번째",
                        "buttons":[],"failback":{"type":"","subject":"","msg":"","imgId":""}}]}
                    """;

            mvc.perform(post("/api/admin/alimtalk/compose/batch")
                            .contentType(MediaType.APPLICATION_JSON).content(badSecondItem))
                    .andExpect(jsonPath("$.problems", org.hamcrest.Matchers.hasItem(
                            org.hamcrest.Matchers.containsString("메시지 2"))));
        }

        @Test
        @DisplayName("D-A9 — 다건에서도 불완전한 버튼을 조용히 버리지 않는다 / nor in batch")
        // req: FR-ATC-009
        void incompleteButtonInBatchIsNotDroppedSilently() throws Exception {
            // 레거시는 이 UI 를 두 번 만들었고 두 곳이 어긋났다. 단건만 검사하면 다건 쪽의
            // 같은 결함은 계속 남는다 — 실제로 레거시가 그랬다.
            // The legacy built this UI twice and the two drifted. Testing only the single-send path leaves
            // the same defect standing on the batch side — which is what happened in the legacy.
            String blankButton = """
                    {"isCd":"K00001","tranId":"T260819001",
                     "items":[{"recipient":"01011112222","senderNumber":"0212345678","reqdate":"",
                               "templateCode":"T","templateTitle":"","msg":"x",
                               "buttons":[{"name":"","type":"WL","urlMobile":"https://m","urlPc":"",
                                           "schemeIos":"","schemeAndroid":""}],
                               "failback":{"type":"","subject":"","msg":"","imgId":""}}]}
                    """;

            mvc.perform(post("/api/admin/alimtalk/compose/batch")
                            .contentType(MediaType.APPLICATION_JSON).content(blankButton))
                    .andExpect(jsonPath("$.problems", org.hamcrest.Matchers.hasItem(
                            org.hamcrest.Matchers.containsString("버튼"))));
        }

        @Test
        @DisplayName("D-A17 — 다건에서도 대체 전송 규칙을 검사한다 / the fallback rules apply in batch too")
        // req: FR-ATC-002
        void fallbackRuleViolationInBatchIsReported() throws Exception {
            String smsWithSubject = """
                    {"isCd":"K00001","tranId":"T260819001",
                     "items":[{"recipient":"01011112222","senderNumber":"0212345678","reqdate":"",
                               "templateCode":"T","templateTitle":"","msg":"x","buttons":[],
                               "failback":{"type":"SMS","subject":"[안내]","msg":"본문","imgId":""}}]}
                    """;

            mvc.perform(post("/api/admin/alimtalk/compose/batch")
                            .contentType(MediaType.APPLICATION_JSON).content(smsWithSubject))
                    .andExpect(jsonPath("$.problems", org.hamcrest.Matchers.hasItem(
                            org.hamcrest.Matchers.containsString("대체 전송"))));
        }

        @Test
        @DisplayName("A2-01 — 키가 없는 기관은 다건에서도 그 사실을 말한다 / an unconfigured institution says so here too")
        // req: FR-ATS-003, FR-AZ-A05
        void unconfiguredInstitutionIsReportedInBatch() throws Exception {
            // 조용한 공용 키 대체는 없다(T-A2). 레거시는 하나의 하드코딩된 키로 모든 기관을
            // 발송했고, 그래서 어느 기관 명의로 나갔는지 사후에 알 수 없었다.
            // No silent shared-key fallback (T-A2). The legacy sent for every institution under one
            // hardcoded key, so after the fact nobody could tell whose name a message went out under.
            String otherInstitution = """
                    {"isCd":"K99999","tranId":"T260819001",
                     "items":[{"recipient":"01011112222","senderNumber":"0212345678","reqdate":"",
                               "templateCode":"T","templateTitle":"","msg":"x","buttons":[],
                               "failback":{"type":"","subject":"","msg":"","imgId":""}}]}
                    """;

            mvc.perform(post("/api/admin/alimtalk/compose/batch")
                            .contentType(MediaType.APPLICATION_JSON).content(otherInstitution))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.problems", org.hamcrest.Matchers.hasItem(
                            org.hamcrest.Matchers.containsString("발신프로필키"))));
        }

        @Test
        @DisplayName("D-A7 — 다건에서도 어긋난 규칙을 이름으로 말한다 / an over-length value names its rule here too")
        // req: FR-ATC-005, NFR-USE-A03
        void overLengthInBatchNamesTheRule() throws Exception {
            String longTemplateCode = """
                    {"isCd":"K00001","tranId":"T260819001",
                     "items":[{"recipient":"01011112222","senderNumber":"0212345678","reqdate":"",
                               "templateCode":"THIS_TEMPLATE_CODE_IS_FAR_TOO_LONG_FOR_THE_CONTRACT_FIELD",
                               "templateTitle":"","msg":"x","buttons":[],
                               "failback":{"type":"","subject":"","msg":"","imgId":""}}]}
                    """;

            mvc.perform(post("/api/admin/alimtalk/compose/batch")
                            .contentType(MediaType.APPLICATION_JSON).content(longTemplateCode))
                    .andExpect(jsonPath("$.problems", org.hamcrest.Matchers.hasItem(
                            org.hamcrest.Matchers.allOf(
                                    org.hamcrest.Matchers.containsString("메시지 1"),
                                    org.hamcrest.Matchers.containsString("템플릿코드")))));
        }

        @Test
        @DisplayName("D-A2 — 다건 payload 에도 계약에 없는 필드가 없다 / no undeclared field in the batch payload")
        // req: FR-ATC-003, CONST-DATA-A01
        void noUndeclaredFieldInTheBatchPayload() throws Exception {
            mvc.perform(post("/api/admin/alimtalk/compose/batch")
                            .contentType(MediaType.APPLICATION_JSON).content(TWO_ITEMS))
                    .andExpect(jsonPath("$.payload").value(org.hamcrest.Matchers.allOf(
                            org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("complete")),
                            org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("valid")),
                            org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("msg_type")))));
        }
    }

    @Nested
    @DisplayName("A2-01 — 발송 준비 상태 / send readiness")
    class SendReadiness {

        @Test
        @DisplayName("키가 설정된 기관은 자격증명 준비 완료다 / a configured institution reports its credential ready")
        // req: FR-ATS-003, NFR-USE-A04
        void configuredInstitutionReportsCredentialReady() throws Exception {
            mvc.perform(get("/api/admin/alimtalk/send-readiness").param("institution", "K00001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.credentialConfigured").value(true))
                    // 발송 경로는 아직 배선되지 않았다 — outbox(A2-02)와 벤더 클라이언트(A2-05).
                    // The dispatch path is not wired yet — outbox (A2-02) and vendor client (A2-05).
                    .andExpect(jsonPath("$.dispatchWired").value(false))
                    .andExpect(jsonPath("$.blockers").isNotEmpty());
        }

        @Test
        @DisplayName("키가 없는 기관은 그 사실을 알린다 / an unconfigured institution says so")
        // req: FR-ATS-003, NFR-USE-A04
        void unconfiguredInstitutionSaysSo() throws Exception {
            mvc.perform(get("/api/admin/alimtalk/send-readiness").param("institution", "K99999"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.credentialConfigured").value(false))
                    .andExpect(jsonPath("$.blockers", org.hamcrest.Matchers.hasSize(2)));
        }

        @Test
        @DisplayName("FR-AZ-A05 — 준비 상태 응답에 키가 어떤 형태로도 없다 / no key material in the readiness response")
        // req: FR-AZ-A05, NFR-SEC-CRED-A01
        void noKeyMaterialInTheReadinessResponse() throws Exception {
            // "설정되어 있다" 는 운영자가 알아야 할 사실이고, 키 자체는 아니다. 마스킹된
            // 형태조차 담지 않는다 — 담을 이유가 없다.
            // "It is configured" is something an operator needs; the key is not. Not even a masked form is
            // included, because there is no reason to include one.
            mvc.perform(get("/api/admin/alimtalk/send-readiness").param("institution", "K00001"))
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("test-profile-key"))))
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("REDACTED"))));
        }
    }

    @Nested
    @DisplayName("FR-AZ-A05 — 자격증명은 응답에도 없다 / the credential is absent from responses too")
    class NoCredential {

        @Test
        @DisplayName("어떤 응답 본문에도 프로파일키가 없다 / no response body carries a profile key")
        // req: FR-AZ-A05, NFR-SEC-CRED-A01
        void noResponseBodyCarriesAProfileKey() throws Exception {
            mvc.perform(post("/api/admin/alimtalk/recipients/preview")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"institutionCode":"K00001","recipients":"01011112222"}
                                    """))
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("sender_key"))))
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("senderKey"))));
        }

        @Test
        @DisplayName("요청에 프로파일키를 넣어도 무시된다 / a profile key in the request is ignored")
        // req: FR-AZ-A05, T-A24
        void aProfileKeyInTheRequestIsIgnored() throws Exception {
            // 요청 타입에 필드가 없으므로 Jackson 이 버린다. 잊을 수 있는 필터가 아니라
            // 타입의 성질이다.
            // The request type has no such field, so Jackson discards it — a property of the type rather
            // than a filter someone must remember.
            mvc.perform(post("/api/admin/alimtalk/recipients/preview")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"institutionCode":"K00001","recipients":"01011112222",
                                     "senderKey":"attacker-supplied-key"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("attacker-supplied-key"))));
        }
    }
}
