package com.webcash.iris.biztalk.alimtalk.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.webcash.iris.biztalk.alimtalk.domain.OutboxDispatcher;
import com.webcash.iris.biztalk.alimtalk.domain.OutboxEntry;
import com.webcash.iris.biztalk.alimtalk.domain.OutboxStatus;
import com.webcash.iris.biztalk.alimtalk.infra.db.OutboxMapper;
import com.webcash.iris.biztalk.alimtalk.infra.vendor.AlimTalkVendorClient;
import com.webcash.iris.biztalk.alimtalk.infra.vendor.CooconAlertClient;
import com.webcash.iris.biztalk.alimtalk.infra.vendor.VendorSendResult;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 발송 배선 검증. / Verification for the dispatch wiring.
 *
 * <p>배선을 테스트하는 이유는 이 슬라이스에서 이미 한 번 데었기 때문이다. {@code TranIdGenerator}
 * 를 인터페이스 뒤로 미루면서 {@code @Bean} 을 남기지 않아 애플리케이션이 기동에 실패했고, 단위
 * 테스트는 생성자를 직접 부르므로 전부 통과했다. <b>실행되지 않는 것은 검증되지 않는다.</b></p>
 * <p>The wiring is tested because this slice was burned once already: deferring
 * {@code TranIdGenerator} behind an interface without leaving a {@code @Bean} stopped the application
 * from starting, while every unit test passed because they call constructors directly. <b>What does not
 * run is not verified.</b></p>
 *
 * // req: FR-ATS-004, FR-ATS-005, RISK-A07, RISK-A13
 */
class AlimTalkDispatchConfigTest {

    private static final Clock FIXED =
            Clock.fixed(Instant.parse("2026-08-19T05:00:00Z"), ZoneId.of("Asia/Seoul"));

    /** 아무것도 하지 않는 매퍼. / A mapper that does nothing. */
    private static class NoopOutbox implements OutboxMapper {
        @Override
        public int insert(OutboxEntry entry) {
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
        public java.util.List<com.webcash.iris.biztalk.alimtalk.domain.OutboxEntry> findByTranId(
                String isCd, String tranId) {
            return java.util.List.of();
        }


        @Override
        public int countUnfinished(String isCd) {
            return 0;
        }
    }

    @Nested
    @DisplayName("RISK-A13 — 기본값은 꺼짐이다 / the default is off")
    class DisabledByDefault {

        @Test
        @DisplayName("발송 빈은 조건부로만 등록된다 / the dispatch beans are conditional")
        // req: RISK-A13
        void dispatchBeansAreConditional() throws Exception {
            // 이 어서션이 지키는 것: 애플리케이션을 그냥 띄웠을 때 발송이 시작되지 않는다.
            // 애노테이션을 지우면 조용히 켜지므로, 애노테이션 자체를 검사한다.
            // What this holds: merely starting the application does not begin sending. Removing the
            // annotation would enable it silently, so the annotation itself is asserted.
            Method vendor = AlimTalkDispatchConfig.class
                    .getDeclaredMethod("alimTalkVendorClient", String.class,
                            CooconAlertClient.VendorOAuthTokens.class);
            ConditionalOnProperty condition = vendor.getAnnotation(ConditionalOnProperty.class);

            assertThat(condition).isNotNull();
            assertThat(condition.name()).containsExactly("iris.alimtalk.dispatch.enabled");
            assertThat(condition.havingValue()).isEqualTo("true");
        }

        @Test
        @DisplayName("스케줄러도 조건부다 / the scheduler is conditional too")
        // req: RISK-A13
        void schedulerIsConditionalToo() {
            // 디스패처 빈만 막고 스케줄러를 남기면 기동 시 주입 실패로 <b>앱 전체가</b> 죽는다.
            // 둘이 같은 조건을 써야 한다.
            // Gating only the dispatcher bean while leaving the scheduler would kill the whole application
            // at startup on a failed injection; both must share the condition.
            ConditionalOnProperty condition =
                    OutboxDispatchScheduler.class.getAnnotation(ConditionalOnProperty.class);

            assertThat(condition).isNotNull();
            assertThat(condition.name()).containsExactly("iris.alimtalk.dispatch.enabled");
            assertThat(condition.havingValue()).isEqualTo("true");
        }
    }

    @Nested
    @DisplayName("AMB-A10 — 토큰 발급은 아직 없다 / token issuance is not implemented")
    class TokenIssuance {

        @Test
        @DisplayName("토큰을 요구하면 예외를 던진다 / demanding a token throws")
        // req: FR-ATS-004, AMB-A10
        void demandingATokenThrows() {
            // 빈 문자열을 돌려주거나 인증 없이 보내지 않는 이유: 둘 다 벤더에서 거절되지만
            // <b>거절의 이유가 우리 로그에 드러나지 않는다</b>. 예외를 던지면 아웃박스의
            // LAST_ERROR 에 "token unavailable" 이 남고 운영자가 무엇이 빠졌는지 안다.
            // Returning an empty string or sending unauthenticated would both be rejected by the vendor,
            // but the reason would not appear in our logs. Throwing records "token unavailable" in the
            // outbox's LAST_ERROR, so an operator can see what is missing.
            CooconAlertClient.VendorOAuthTokens tokens =
                    new AlimTalkDispatchConfig().vendorOAuthTokens();

            assertThatThrownBy(() -> tokens.authorizationHeader("K00001"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("AMB-A10")
                    .hasMessageContaining("K00001");
        }

        @Test
        @DisplayName("그 실패는 확정 실패로 분류된다 / that failure classifies as established")
        // req: FR-ATS-005, RISK-A07
        void thatFailureClassifiesAsEstablished() {
            // 토큰이 없으면 요청은 나가지 않았으므로 재시도가 안전하다. UNKNOWN 으로 분류하면
            // 기본 정책상 재시도되지 않아, 설정만 고치면 나갈 수 있는 발송이 멈춰 있게 된다.
            // Without a token the request never left, so a retry is safe. Classifying it UNKNOWN would
            // leave it unretried by default, stalling a send that a configuration fix would release.
            AlimTalkVendorClient client = new CooconAlertClient(
                    java.net.http.HttpClient.newHttpClient(),
                    new com.fasterxml.jackson.databind.ObjectMapper(),
                    "http://127.0.0.1:1",
                    new AlimTalkDispatchConfig().vendorOAuthTokens());

            VendorSendResult result = client.send("K00001", "T260819001", "{}");

            assertThat(result.status()).isEqualTo(OutboxStatus.FAILED);
            assertThat(result.status().isSafeToRetry()).isTrue();
        }
    }

    @Nested
    @DisplayName("FR-ATS-005 — 디스패처 조립 / assembling the dispatcher")
    class Assembly {

        @Test
        @DisplayName("기본 설정으로 조립된다 / it assembles with the defaults")
        // req: FR-ATS-005
        void assemblesWithTheDefaults() {
            assertThatCode(() -> new AlimTalkDispatchConfig().outboxDispatcher(
                            new NoopOutbox(),
                            (isCd, tranId, payload) -> VendorSendResult.accepted("ok"),
                            FIXED, 50, 5, false, 120))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("클레임이 벤더 타임아웃보다 짧으면 조립을 거부한다 / a short claim refuses assembly")
        // req: FR-ATS-005, RISK-A07
        void shortClaimRefusesAssembly() {
            // 설정 실수가 조용히 통과하면 중복 발송만 생긴다 — 눈에 보이는 오류가 없다.
            // 그래서 기동 시점에 거부한다. 기본값 120초는 안전하지만 사람이 바꿀 수 있다.
            // A configuration mistake passing quietly produces only duplicate sends with no visible error,
            // so it is refused at startup. The 120 s default is safe, but a person can change it.
            assertThatThrownBy(() -> new AlimTalkDispatchConfig().outboxDispatcher(
                            new NoopOutbox(),
                            (isCd, tranId, payload) -> VendorSendResult.accepted("ok"),
                            FIXED, 50, 5, false, 45))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("60s");
        }

        @Test
        @DisplayName("retry-unknown 을 켜도 조립된다 / it assembles with retry-unknown on")
        // req: FR-ATS-005, RISK-A07
        void assemblesWithRetryUnknownOn() {
            // 켜는 것 자체는 막지 않는다 — 벤더 멱등성이 확인되면 정당한 설정이다. 막는 대신
            // 경고를 남긴다: 나중에 중복 발송을 조사할 때 그 한 줄이 출발점이 된다.
            // Enabling it is not blocked: once vendor idempotency is confirmed it is a legitimate setting.
            // Instead a warning is logged, so a later duplicate-send investigation has a starting point.
            assertThatCode(() -> new AlimTalkDispatchConfig().outboxDispatcher(
                            new NoopOutbox(),
                            (isCd, tranId, payload) -> VendorSendResult.accepted("ok"),
                            FIXED, 10, 3, true, 90))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("NFR-OPS-A02 — 스케줄러는 멈추지 않는다 / the scheduler does not stop")
    class SchedulerResilience {

        @Test
        @DisplayName("주기 실패가 스케줄을 멈추지 않는다 / a failed cycle does not stop the schedule")
        // req: FR-ATS-004, NFR-OPS-A02
        void failedCycleDoesNotStopTheSchedule() {
            // @Scheduled 메서드가 예외를 던지면 스프링은 그 작업을 <b>다시 스케줄하지 않는다</b>.
            // 한 번의 실패가 발송을 영구히 멈추고, 그것은 조용한 미전달이다 — 이 슬라이스가
            // 없애려는 결함 그 자체다. A2-02 DDL 이 없을 때 첫 주기가 바로 이렇게 실패한다.
            //
            // When a @Scheduled method throws, Spring stops rescheduling it: one failure halts sending
            // permanently, a silent non-delivery — the very defect this slice exists to remove. Without the
            // A2-02 DDL the first cycle fails in exactly this way.
            AtomicInteger calls = new AtomicInteger();
            OutboxDispatcher throwing = new OutboxDispatcher(
                    new NoopOutbox() {
                        @Override
                        public List<OutboxEntry> claim(
                                List<String> statuses, LocalDateTime now, int limit) {
                            calls.incrementAndGet();
                            throw new IllegalStateException(
                                    "relation \"kkb_atk_send_outbox\" does not exist");
                        }
                    },
                    (isCd, tranId, payload) -> VendorSendResult.accepted("ok"),
                    FIXED, 50, 5, false, java.time.Duration.ofSeconds(120));

            OutboxDispatchScheduler scheduler = new OutboxDispatchScheduler(throwing);

            assertThatCode(scheduler::dispatch).doesNotThrowAnyException();
            assertThatCode(scheduler::dispatch).doesNotThrowAnyException();
            assertThat(calls.get())
                    .as("두 번째 주기도 실제로 돌았다 / the second cycle actually ran")
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("빈 주기는 조용하다 / an empty cycle is quiet")
        // req: NFR-OPS-A03
        void emptyCycleIsQuiet() {
            // 30초마다 "0건 처리" 를 남기면 로그가 그것으로 가득 차고, 진짜 신호가 묻힌다.
            // Logging "handled 0" every 30 seconds would fill the log and bury the real signals.
            OutboxDispatcher idle = new OutboxDispatcher(
                    new NoopOutbox(),
                    (isCd, tranId, payload) -> VendorSendResult.accepted("ok"),
                    FIXED, 50, 5, false, java.time.Duration.ofSeconds(120));

            assertThatCode(new OutboxDispatchScheduler(idle)::dispatch).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("처리된 주기도 예외를 내보내지 않는다 / a productive cycle also throws nothing")
        // req: FR-ATS-004, NFR-OPS-A02
        void productiveCycleThrowsNothing() {
            OutboxEntry pending = new OutboxEntry(
                    1L, "K00001", "T260819001", 1, "{}", OutboxStatus.PENDING, 0,
                    null, null, null, null, null);

            OutboxDispatcher busy = new OutboxDispatcher(
                    new NoopOutbox() {
                        private boolean served;

                        @Override
                        public List<OutboxEntry> claim(
                                List<String> statuses, LocalDateTime now, int limit) {
                            if (served) {
                                return List.of();
                            }
                            served = true;
                            return List.of(pending);
                        }
                    },
                    (isCd, tranId, payload) -> VendorSendResult.unknown("read timeout"),
                    FIXED, 50, 5, false, java.time.Duration.ofSeconds(120));

            assertThatCode(new OutboxDispatchScheduler(busy)::dispatch).doesNotThrowAnyException();
        }
    }
}
