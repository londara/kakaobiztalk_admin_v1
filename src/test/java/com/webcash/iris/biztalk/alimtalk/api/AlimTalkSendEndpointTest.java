package com.webcash.iris.biztalk.alimtalk.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.webcash.iris.biztalk.alimtalk.domain.AlimTalkSendService;
import com.webcash.iris.biztalk.alimtalk.domain.OutboxEntry;
import com.webcash.iris.biztalk.alimtalk.domain.TemplateRegistry;
import com.webcash.iris.biztalk.alimtalk.domain.TemplateSummary;
import com.webcash.iris.biztalk.alimtalk.domain.TranIdGenerator;
import com.webcash.iris.biztalk.alimtalk.infra.db.OutboxMapper;
import com.webcash.iris.biztalk.alimtalk.infra.db.TemplateMapper;
import com.webcash.iris.biztalk.alimtalk.infra.vendor.SenderProfileKeyResolver;
import com.webcash.iris.biztalk.alimtalk.infra.vendor.VendorPayloadMapper;
import com.webcash.iris.common.tenant.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * {@code POST /send} 검증 — 꺼진 상태와 켜진 상태 양쪽.
 * Verifies {@code POST /send} in both the disabled and enabled states.
 *
 * <p>꺼진 상태를 먼저 검증하는 이유: 그것이 <b>기본값</b>이고, 기본값이 안전하지 않으면 나머지
 * 검증은 의미가 없다. 애플리케이션을 그냥 띄웠을 때 아무것도 보내지 않아야 한다.</p>
 * <p>The disabled state is verified first because it is the <b>default</b>, and if the default is not
 * safe the rest does not matter: merely starting the application must send nothing.</p>
 *
 * // source: biztalk_admin_50_s001_act.jsp:114-137
 * // req: FR-ATS-001, FR-ATS-002, FR-ATS-003, NFR-OPS-A02, RISK-A13
 */
class AlimTalkSendEndpointTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final String FORM = """
            {"isCd":"K00001","tranId":"T260819001","recipients":"01011112222",
             "senderNumber":"0212345678","reqdate":"","templateCode":"TMPL_0001",
             "templateTitle":"결제 안내","msg":"50,000원이 결제되었습니다.",
             "buttons":[],"failback":{"type":"","subject":"","msg":"","imgId":""}}
            """;

    /** 삽입된 payload 를 모으는 매퍼. / A mapper collecting inserted payloads. */
    private static final class CapturingOutbox implements OutboxMapper {
        private final List<OutboxEntry> inserted = new ArrayList<>();

        @Override
        public int insert(OutboxEntry entry) {
            inserted.add(entry);
            return 1;
        }

        @Override
        public List<OutboxEntry> claim(List<String> statuses, LocalDateTime now, int limit) {
            return List.of();
        }

        @Override
        public int markClaimed(long outboxId, LocalDateTime claimedUntil) {
            return 1;
        }

        @Override
        public int recordOutcome(long outboxId, String status, String lastError) {
            return 1;
        }
        @Override
        public List<OutboxEntry> findByTranId(String isCd, String tranId) {
            return List.copyOf(inserted);
        }


        @Override
        public int countUnfinished(String isCd) {
            return 0;
        }
    }

    /** 템플릿 대역. / A stub template mapper. */
    private static final class StubTemplates implements TemplateMapper {
        @Override
        public List<TemplateSummary> findByInstitution(String institutionCode) {
            return List.of(new TemplateSummary("TMPL_0001", "결제 안내"));
        }

        @Override
        public String findTemplateBody(String institutionCode, String templateCode) {
            return "#{금액}원이 결제되었습니다.";
        }
    }

    private static MockMvc mvcWith(boolean dispatchEnabled, OutboxMapper outbox) {
        AtomicLong sequence = new AtomicLong();
        TranIdGenerator tranIds = new TranIdGenerator(
                'T',
                (institutionCode, date) -> sequence.getAndIncrement(),
                Clock.fixed(Instant.parse("2026-08-19T05:00:00Z"), KST));

        TenantContext.set(new TenantContext.TenantPrincipal("operator@example.com", "K00001", true));

        return MockMvcBuilders.standaloneSetup(new AlimTalkController(
                        tranIds,
                        new TemplateRegistry(new StubTemplates()),
                        new SenderProfileKeyResolver(Map.of("K00001", "test-profile-key"), null),
                        new AlimTalkSendService(outbox),
                        new VendorPayloadMapper(),
                        dispatchEnabled))
                .build();
    }

    @AfterEach
    void clearSession() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("RISK-A13 — 발송이 꺼진 상태 / with dispatch disabled")
    class Disabled {

        @Test
        @DisplayName("접수를 거절하고 아무것도 쓰지 않는다 / it refuses and writes nothing")
        // req: FR-ATS-003, RISK-A13
        void refusesAndWritesNothing() throws Exception {
            // 아웃박스에만 쌓아 두면 나중에 배선한 순간 오래된 메시지가 한꺼번에 나간다 —
            // 그중 어느 것이 예약으로 의도된 것인지 알 방법이 없다.
            // Merely accumulating rows means that the moment dispatch is wired, stale messages all go out
            // at once, with no way to tell which were meant as reservations.
            CapturingOutbox outbox = new CapturingOutbox();

            mvcWith(false, outbox)
                    .perform(post("/api/admin/alimtalk/send")
                            .contentType(MediaType.APPLICATION_JSON).content(FORM))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.acceptedCount").value(0))
                    .andExpect(jsonPath("$.problems").isNotEmpty());

            assertThat(outbox.inserted).isEmpty();
        }

        @Test
        @DisplayName("준비 상태가 사실을 말한다 / readiness reports the truth")
        // req: FR-ATS-003, NFR-USE-A04
        void readinessReportsTheTruth() throws Exception {
            // 고정 문구를 두면 상태가 바뀌어도 문구가 그대로 남아 거짓이 된다. 레거시 화면 61 은
            // "JSON 생성" 이 무엇을 했는지 끝내 말하지 않았다.
            // A fixed string would survive a change of state and become false; legacy screen 61 never said
            // what "JSON 생성" did.
            mvcWith(false, new CapturingOutbox())
                    .perform(get("/api/admin/alimtalk/send-readiness").param("institution", "K00001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dispatchWired").value(false))
                    .andExpect(jsonPath("$.blockers").isNotEmpty());
        }
    }

    @Nested
    @DisplayName("FR-ATS-001 — 발송이 켜진 상태 / with dispatch enabled")
    class Enabled {

        @Test
        @DisplayName("접수하고 202 를 돌려준다 / it accepts and returns 202")
        // req: FR-ATS-001, FR-ATS-002, NFR-OPS-A02
        void acceptsAndReturns202() throws Exception {
            // 200 을 돌려주면 화면이 "발송 완료" 라고 쓰게 되고, 그것은 아직 사실이 아니다.
            // 벤더 호출은 디스패처가 나중에 한다.
            // A 200 would have the screen say "sent", which is not yet true: the vendor call happens later,
            // in the dispatcher.
            CapturingOutbox outbox = new CapturingOutbox();

            mvcWith(true, outbox)
                    .perform(post("/api/admin/alimtalk/send")
                            .contentType(MediaType.APPLICATION_JSON).content(FORM))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.tranId").value("T260819001"))
                    .andExpect(jsonPath("$.acceptedCount").value(1))
                    .andExpect(jsonPath("$.scheduled").value(false));

            assertThat(outbox.inserted).hasSize(1);
        }

        @Test
        @DisplayName("아웃박스 payload 는 실제 수신번호를 담는다 / the outbox payload carries the real recipient")
        // req: FR-ATS-004, FR-AZ-A05
        void outboxPayloadCarriesTheRealRecipient() throws Exception {
            // 미리보기(/compose)는 마스킹된 값을 내보낸다. 발송 경로가 같은 것을 쓰면 벤더가
            // `010****2222` 를 받게 되고 발송은 실패한다 — 또는 더 나쁘게, 존재하지 않는
            // 번호로 접수된다. 두 경로가 다른 직렬화기를 쓰는 이유가 이것이다.
            // The preview (/compose) emits masked values. If the send path used the same, the vendor would
            // receive `010****2222` and the send would fail — or worse, be accepted for a number that does
            // not exist. That is why the two paths use different serialisers.
            CapturingOutbox outbox = new CapturingOutbox();

            mvcWith(true, outbox)
                    .perform(post("/api/admin/alimtalk/send")
                            .contentType(MediaType.APPLICATION_JSON).content(FORM))
                    .andExpect(status().isAccepted());

            assertThat(outbox.inserted).hasSize(1);
            String payload = outbox.inserted.get(0).payload();
            assertThat(payload).contains("01011112222");
            assertThat(payload).contains("test-profile-key");
            assertThat(payload).doesNotContain("REDACTED");
        }

        @Test
        @DisplayName("응답에는 수신번호도 자격증명도 없다 / the response carries neither recipient nor credential")
        // req: NFR-SEC-PII-A01, NFR-SEC-CRED-A01, FR-AZ-A05
        void responseCarriesNeitherRecipientNorCredential() throws Exception {
            // payload 는 아웃박스로 가고, 응답은 브라우저로 간다. 두 경로를 섞으면 마스킹이
            // 무의미해진다 — 레거시는 payload 를 그대로 화면에 뿌렸다.
            // The payload goes to the outbox and the response goes to the browser. Mixing them makes the
            // masking pointless; the legacy put the payload straight on the screen.
            mvcWith(true, new CapturingOutbox())
                    .perform(post("/api/admin/alimtalk/send")
                            .contentType(MediaType.APPLICATION_JSON).content(FORM))
                    .andExpect(content().string(
                            org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("01011112222"))))
                    .andExpect(content().string(
                            org.hamcrest.Matchers.not(
                                    org.hamcrest.Matchers.containsString("test-profile-key"))));
        }

        @Test
        @DisplayName("계약에 어긋나면 접수하지 않는다 / a non-conforming request is not accepted")
        // req: FR-ATS-001, FR-ATC-005
        void nonConformingRequestIsNotAccepted() throws Exception {
            // 레거시는 브라우저에서만 검사했고(D-A5) 서버는 무엇이든 받아 벤더에 넘겼다.
            // 검증이 발송 경로 안에 있어야 벤더 거절을 실제로 막는다.
            // The legacy checked only in the browser (D-A5) and the server passed anything to the vendor.
            // Validation must sit inside the send path to actually prevent a rejection.
            CapturingOutbox outbox = new CapturingOutbox();
            String tooLongTranId = FORM.replace("T260819001", "THIS_IS_FAR_TOO_LONG");

            mvcWith(true, outbox)
                    .perform(post("/api/admin/alimtalk/send")
                            .contentType(MediaType.APPLICATION_JSON).content(tooLongTranId))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.problems").isNotEmpty());

            assertThat(outbox.inserted).isEmpty();
        }

        @Test
        @DisplayName("수신번호가 없으면 접수하지 않는다 / no recipient means no acceptance")
        // req: FR-ATS-001, FR-ATC-010
        void noRecipientMeansNoAcceptance() throws Exception {
            CapturingOutbox outbox = new CapturingOutbox();

            mvcWith(true, outbox)
                    .perform(post("/api/admin/alimtalk/send")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(FORM.replace("01011112222", "")))
                    .andExpect(status().isBadRequest());

            assertThat(outbox.inserted).isEmpty();
        }

        @Test
        @DisplayName("FR-ATS-006 — 예약 시각이 아웃박스로 전달된다 / the scheduled time reaches the outbox")
        // req: FR-ATS-006, FR-ATC-007
        void scheduledTimeReachesTheOutbox() throws Exception {
            CapturingOutbox outbox = new CapturingOutbox();

            mvcWith(true, outbox)
                    .perform(post("/api/admin/alimtalk/send")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(FORM.replace("\"reqdate\":\"\"", "\"reqdate\":\"20260820090000\"")))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.scheduled").value(true));

            assertThat(outbox.inserted).hasSize(1);
            assertThat(outbox.inserted.get(0).dueAt())
                    .isEqualTo(LocalDateTime.of(2026, 8, 20, 9, 0));
        }

        @Test
        @DisplayName("FR-ATS-009 — 같은 거래번호를 다시 보내지 않는다 / a repeated transaction id is not re-sent")
        // req: FR-ATS-009, ADR-ATK-026
        void repeatedTransactionIdIsNotResent() throws Exception {
            // 레거시는 tran_id 를 시각으로만 만들어(D-A25) 이 충돌을 구조적으로 유발했고,
            // 감지하지도 않았다 — 같은 번호로 두 번 보내면 두 번 나갔다.
            // The legacy derived tran_id from the clock alone (D-A25), structurally inviting this collision
            // and never detecting it: sending twice under one id sent twice.
            CapturingOutbox outbox = new CapturingOutbox();
            MockMvc mvc = mvcWith(true, outbox);

            mvc.perform(post("/api/admin/alimtalk/send")
                            .contentType(MediaType.APPLICATION_JSON).content(FORM))
                    .andExpect(status().isAccepted());
            assertThat(outbox.inserted).hasSize(1);

            // 두 번째 요청 — 접수되지 않고 원래 결과를 돌려받는다.
            // The second request is not accepted and returns the original outcome.
            mvc.perform(post("/api/admin/alimtalk/send")
                            .contentType(MediaType.APPLICATION_JSON).content(FORM))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.tranId").value("T260819001"))
                    // 원래 건수를 돌려준다. 0 이면 화면이 "접수되지 않았다" 고 표시하고 운영자가
                    // 다시 시도한다 — 중복 거절의 목적이 정확히 그것을 막는 것인데.
                    // The original count: a zero would have the screen say nothing was accepted, so the
                    // operator would try again — exactly what this rejection exists to prevent.
                    .andExpect(jsonPath("$.acceptedCount").value(1))
                    .andExpect(jsonPath("$.problems").isNotEmpty());

            assertThat(outbox.inserted)
                    .as("두 번째 요청이 행을 더하지 않았다 / the second request added no row")
                    .hasSize(1);
        }

        @Test
        @DisplayName("FR-ATS-002 — 운영자가 결말을 조회할 수 있다 / the operator can read the outcome")
        // req: FR-ATS-002
        void operatorCanReadTheOutcome() throws Exception {
            // 접수와 발송을 분리한 대가로 접수 응답에는 벤더 결과가 없다. FR-ATS-002 는 그 결과를
            // 운영자에게 제시하도록 요구하므로, 이 조회가 그 요구를 이어받는다.
            // Separating acceptance from despatch means the acceptance response carries no vendor outcome.
            // FR-ATS-002 requires that outcome be presented, so this lookup takes up the requirement.
            CapturingOutbox outbox = new CapturingOutbox();
            MockMvc mvc = mvcWith(true, outbox);

            mvc.perform(post("/api/admin/alimtalk/send")
                            .contentType(MediaType.APPLICATION_JSON).content(FORM))
                    .andExpect(status().isAccepted());

            mvc.perform(get("/api/admin/alimtalk/send-status")
                            .param("institution", "K00001").param("tranId", "T260819001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tranId").value("T260819001"))
                    .andExpect(jsonPath("$.items[0].order").value(1))
                    .andExpect(jsonPath("$.items[0].status").value("PENDING"));
        }

        @Test
        @DisplayName("결말 조회에 payload 가 없다 / the outcome lookup carries no payload")
        // req: NFR-SEC-PII-A01, NFR-SEC-CRED-A01
        void outcomeLookupCarriesNoPayload() throws Exception {
            // 아웃박스의 PAYLOAD 에는 수신번호와 발신프로필키가 평문으로 있다. 상태를 알려주는
            // 것과 자격증명을 넘겨주는 것은 다른 일이다.
            // The outbox PAYLOAD holds the recipient and the profile key in clear; reporting a status is
            // not the same as handing over credentials.
            CapturingOutbox outbox = new CapturingOutbox();
            MockMvc mvc = mvcWith(true, outbox);

            mvc.perform(post("/api/admin/alimtalk/send")
                            .contentType(MediaType.APPLICATION_JSON).content(FORM))
                    .andExpect(status().isAccepted());

            mvc.perform(get("/api/admin/alimtalk/send-status")
                            .param("institution", "K00001").param("tranId", "T260819001"))
                    .andExpect(content().string(
                            org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("01011112222"))))
                    .andExpect(content().string(
                            org.hamcrest.Matchers.not(
                                    org.hamcrest.Matchers.containsString("test-profile-key"))));
        }

        @Test
        @DisplayName("접수 기록이 없으면 404 다 / no acceptance on record is a 404")
        // req: FR-ATS-002, FR-ATS-009
        void noAcceptanceOnRecordIsA404() throws Exception {
            // 없는 것과 실패한 것을 구분해야 운영자가 다시 보내도 되는지 판단할 수 있다.
            // 빈 목록에 200 을 주면 "조회는 됐고 결과가 없다" 로 읽혀 판단이 흐려진다.
            // Absent must be distinguished from failed so the operator can decide whether to resend; a 200
            // with an empty list reads as "found it, no outcome" and blurs that decision.
            mvcWith(true, new CapturingOutbox())
                    .perform(get("/api/admin/alimtalk/send-status")
                            .param("institution", "K00001").param("tranId", "T999999999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.items").isEmpty());
        }

        @Test
        @DisplayName("준비 상태가 배선되었다고 말한다 / readiness reports it is wired")
        // req: FR-ATS-003, NFR-USE-A04
        void readinessReportsItIsWired() throws Exception {
            mvcWith(true, new CapturingOutbox())
                    .perform(get("/api/admin/alimtalk/send-readiness").param("institution", "K00001"))
                    .andExpect(jsonPath("$.dispatchWired").value(true))
                    .andExpect(jsonPath("$.blockers").isEmpty());
        }
    }
}
