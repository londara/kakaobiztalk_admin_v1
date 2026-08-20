package com.webcash.iris.biztalk.alimtalk.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.webcash.iris.biztalk.alimtalk.infra.db.OutboxMapper;
import com.webcash.iris.biztalk.alimtalk.infra.vendor.AlimTalkVendorClient;
import com.webcash.iris.biztalk.alimtalk.infra.vendor.VendorSendResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link OutboxDispatcher} 검증. / Verification for {@link OutboxDispatcher}.
 *
 * <p>DB 없이 검증한다 — 매퍼를 대역으로 둔다(RISK-A12). 그러므로 이 테스트가 증명하는 것은
 * <b>정책</b>이고, 증명하지 못하는 것은 {@code SKIP LOCKED} 의 실제 동시성 동작이다. 후자는
 * PostgreSQL 없이 검증할 방법이 없어 A2-15 로 이월했다.</p>
 * <p>Verified without a database, the mapper being stubbed (RISK-A12). These tests establish the
 * <b>policy</b>; they cannot establish {@code SKIP LOCKED}'s real concurrency behaviour, which has no
 * verification path without PostgreSQL and is carried to A2-15.</p>
 *
 * // source: biztalk_admin_50_s001_act.jsp:118-137
 * // req: FR-ATS-004, FR-ATS-005, ADR-ATK-023, ADR-ATK-025, RISK-A07
 */
class OutboxDispatcherTest {

    private static final Duration CLAIM = Duration.ofSeconds(120);

    private static Clock fixedAt(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneId.of("Asia/Seoul"));
    }

    /** 호출을 기록하는 매퍼 대역. / A stub mapper that records calls. */
    private static final class StubMapper implements OutboxMapper {
        private final List<OutboxEntry> claimable = new ArrayList<>();
        private final List<String> claimedStatuses = new ArrayList<>();
        private final Map<Long, String> outcomes = new LinkedHashMap<>();
        private final Map<Long, LocalDateTime> claims = new LinkedHashMap<>();
        private final List<String> callOrder = new ArrayList<>();
        private final Map<Long, String> errors = new LinkedHashMap<>();

        @Override
        public int insert(OutboxEntry entry) {
            return 1;
        }

        @Override
        public List<OutboxEntry> claim(List<String> statuses, LocalDateTime now, int limit) {
            claimedStatuses.addAll(statuses);
            return claimable.stream().limit(limit).toList();
        }

        @Override
        public int markClaimed(long outboxId, LocalDateTime claimedUntil) {
            callOrder.add("claim:" + outboxId);
            claims.put(outboxId, claimedUntil);
            return 1;
        }

        @Override
        public int recordOutcome(long outboxId, String status, String lastError) {
            callOrder.add("outcome:" + outboxId);
            outcomes.put(outboxId, status);
            errors.put(outboxId, lastError);
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

    /** 지정한 결과를 돌려주는 벤더 대역. / A stub vendor returning a prescribed result. */
    private static final class StubVendor implements AlimTalkVendorClient {
        private final Function<String, VendorSendResult> behaviour;
        private final List<String> sent = new ArrayList<>();

        StubVendor(Function<String, VendorSendResult> behaviour) {
            this.behaviour = behaviour;
        }

        @Override
        public VendorSendResult send(String isCd, String tranId, String payload) {
            sent.add(tranId);
            return behaviour.apply(tranId);
        }
    }

    private static OutboxEntry entry(long id, OutboxStatus status, int attempts) {
        return new OutboxEntry(
                id, "K00001", "T26081900" + id, 1, "{}", status, attempts, null, null, null, null, null);
    }

    private static OutboxDispatcher dispatcher(
            StubMapper mapper, StubVendor vendor, boolean retryUnknown, int maxAttempts) {
        return new OutboxDispatcher(
                mapper, vendor, fixedAt("2026-08-19T05:00:00Z"), 10, maxAttempts, retryUnknown, CLAIM);
    }

    @Nested
    @DisplayName("RISK-A07 — 알 수 없음과 실패의 구분 / unknown versus failed")
    class UnknownVersusFailed {

        @Test
        @DisplayName("기본값은 UNKNOWN 을 다시 집지 않는다 / by default UNKNOWN is not re-claimed")
        // req: FR-ATS-005, RISK-A07
        void unknownIsNotRetriedByDefault() {
            // 벤더 멱등성이 확인되지 않았다(spike A1-03). 확인되지 않은 전제 위에서 자동
            // 재시도를 하면 중복 발송이 되고, 금융 통지에서 중복은 되돌릴 수 없다.
            // Vendor idempotency is unverified (spike A1-03). Retrying on an unverified premise
            // produces duplicate sends, and for a financial notification a duplicate cannot be undone.
            StubMapper mapper = new StubMapper();
            OutboxDispatcher dispatcher =
                    dispatcher(mapper, new StubVendor(t -> VendorSendResult.accepted("ok")), false, 5);

            dispatcher.runOnce();

            assertThat(mapper.claimedStatuses)
                    .containsExactly(OutboxStatus.PENDING.name(), OutboxStatus.FAILED.name())
                    .doesNotContain(OutboxStatus.UNKNOWN.name());
        }

        @Test
        @DisplayName("멱등성이 확인되면 UNKNOWN 도 집는다 / with idempotency confirmed, UNKNOWN is claimed")
        // req: FR-ATS-005, RISK-A07
        void unknownIsRetriedWhenEnabled() {
            StubMapper mapper = new StubMapper();
            OutboxDispatcher dispatcher =
                    dispatcher(mapper, new StubVendor(t -> VendorSendResult.accepted("ok")), true, 5);

            dispatcher.runOnce();

            assertThat(mapper.claimedStatuses).contains(OutboxStatus.UNKNOWN.name());
        }

        @Test
        @DisplayName("타임아웃은 UNKNOWN 으로 기록된다 / a timeout records as UNKNOWN, never FAILED")
        // req: FR-ATS-005, RISK-A07
        void timeoutRecordsAsUnknown() {
            StubMapper mapper = new StubMapper();
            mapper.claimable.add(entry(1, OutboxStatus.PENDING, 0));
            OutboxDispatcher dispatcher = dispatcher(
                    mapper, new StubVendor(t -> VendorSendResult.unknown("read timeout after 60s")), false, 5);

            dispatcher.runOnce();

            assertThat(mapper.outcomes).containsEntry(1L, OutboxStatus.UNKNOWN.name());
        }

        @Test
        @DisplayName("UNKNOWN 은 상한에 도달해도 DEAD 가 되지 않는다 / UNKNOWN never becomes DEAD by the ceiling")
        // req: FR-ATS-005, RISK-A07
        void unknownIsNotKilledByTheAttemptCeiling() {
            // DEAD 는 "더 보내지 않는다" 는 뜻이고, UNKNOWN 은 "보냈는지 모른다" 는 뜻이다.
            // 모르는 것을 상한으로 종결하면 전달되었을 수도 있는 메시지가 실패로 집계된다.
            // DEAD means "no further attempt"; UNKNOWN means "we do not know whether it went". Closing
            // an unknown by the ceiling would count a possibly-delivered message as a failure.
            StubMapper mapper = new StubMapper();
            mapper.claimable.add(entry(1, OutboxStatus.PENDING, 9));
            OutboxDispatcher dispatcher = dispatcher(
                    mapper, new StubVendor(t -> VendorSendResult.unknown("read timeout")), true, 3);

            dispatcher.runOnce();

            assertThat(mapper.outcomes).containsEntry(1L, OutboxStatus.UNKNOWN.name());
        }
    }

    @Nested
    @DisplayName("FR-ATS-005 — 재시도 상한 / the retry ceiling")
    class RetryCeiling {

        @Test
        @DisplayName("확정 실패가 상한에 이르면 DEAD 다 / an established failure at the ceiling becomes DEAD")
        // req: FR-ATS-005, NFR-OPS-A02
        void establishedFailureAtCeilingBecomesDead() {
            StubMapper mapper = new StubMapper();
            mapper.claimable.add(entry(1, OutboxStatus.FAILED, 2));
            OutboxDispatcher dispatcher = dispatcher(
                    mapper, new StubVendor(t -> VendorSendResult.notDelivered("HTTP 500", 500)), false, 3);

            dispatcher.runOnce();

            assertThat(mapper.outcomes).containsEntry(1L, OutboxStatus.DEAD.name());
        }

        @Test
        @DisplayName("상한 전에는 FAILED 로 남아 다시 시도된다 / below the ceiling it stays FAILED")
        // req: FR-ATS-005
        void belowCeilingStaysFailed() {
            StubMapper mapper = new StubMapper();
            mapper.claimable.add(entry(1, OutboxStatus.FAILED, 0));
            OutboxDispatcher dispatcher = dispatcher(
                    mapper, new StubVendor(t -> VendorSendResult.notDelivered("HTTP 503", 503)), false, 3);

            dispatcher.runOnce();

            assertThat(mapper.outcomes).containsEntry(1L, OutboxStatus.FAILED.name());
        }
    }

    @Nested
    @DisplayName("ADR-ATK-023 — 클레임 / claiming")
    class Claiming {

        @Test
        @DisplayName("클레임은 벤더 호출보다 먼저 세운다 / the claim is set before the vendor call")
        // req: FR-ATS-005
        void claimPrecedesTheVendorCall() {
            // 호출 뒤에 세우면 그 사이에 죽었을 때 다른 인스턴스가 같은 행을 즉시 집는다 —
            // 요청은 이미 나갔는데. 순서 자체가 중복 발송을 막는 장치다.
            // Setting it afterwards lets another instance take the row immediately if we die in
            // between, though the request has already gone. The ordering is the safeguard.
            StubMapper mapper = new StubMapper();
            mapper.claimable.add(entry(7, OutboxStatus.PENDING, 0));
            OutboxDispatcher dispatcher =
                    dispatcher(mapper, new StubVendor(t -> VendorSendResult.accepted("ok")), false, 3);

            dispatcher.runOnce();

            assertThat(mapper.callOrder).containsExactly("claim:7", "outcome:7");
        }

        @Test
        @DisplayName("클레임이 벤더 타임아웃보다 짧으면 생성을 거부한다 / a claim shorter than the vendor timeout is refused")
        // req: FR-ATS-005, RISK-A07
        void shortClaimIsRefused() {
            // 벤더 read 타임아웃은 60초다. 클레임이 그보다 짧으면 응답을 기다리는 행을 다른
            // 인스턴스가 다시 집는다 — 눈에 보이는 오류 없이 중복 발송만 생긴다. 그래서
            // 설정 시점에 거부한다.
            // The vendor read timeout is 60s; a shorter claim lets another instance re-take a row whose
            // response is outstanding, producing duplicates with no visible error. Refused at wiring.
            assertThatThrownBy(() -> new OutboxDispatcher(
                            new StubMapper(),
                            new StubVendor(t -> VendorSendResult.accepted("ok")),
                            fixedAt("2026-08-19T05:00:00Z"),
                            10,
                            3,
                            false,
                            Duration.ofSeconds(30)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("60s");
        }

        @Test
        @DisplayName("클레임 만료는 현재 시각에 유지 시간을 더한 값이다 / the expiry is now plus the hold")
        // req: FR-ATS-005
        void claimExpiryIsNowPlusHold() {
            StubMapper mapper = new StubMapper();
            mapper.claimable.add(entry(1, OutboxStatus.PENDING, 0));
            dispatcher(mapper, new StubVendor(t -> VendorSendResult.accepted("ok")), false, 3).runOnce();

            LocalDateTime expected =
                    LocalDateTime.now(fixedAt("2026-08-19T05:00:00Z")).plus(CLAIM);
            assertThat(mapper.claims).containsEntry(1L, expected);
        }
    }

    @Nested
    @DisplayName("FR-ATS-005 — 결과 기록 / recording outcomes")
    class RecordingOutcomes {

        @Test
        @DisplayName("긴 오류 메시지가 결과 기록을 잃게 하지 않는다 / a long error must not lose the outcome")
        // req: FR-ATS-005
        void longErrorIsTruncatedNotDropped() {
            // LAST_ERROR 는 1000자다. 자르지 않으면 UPDATE 가 실패하고, 그 실패는 상태 자체를
            // 잃게 만든다 — 보냈는지 모르는 행이 남는다.
            // LAST_ERROR is 1000 characters; without truncation the UPDATE fails and the status is lost,
            // leaving a row whose fate is unknown.
            StubMapper mapper = new StubMapper();
            mapper.claimable.add(entry(1, OutboxStatus.PENDING, 0));
            String huge = "x".repeat(5000);
            dispatcher(mapper, new StubVendor(t -> VendorSendResult.notDelivered(huge, 500)), false, 9)
                    .runOnce();

            assertThat(mapper.outcomes).containsEntry(1L, OutboxStatus.FAILED.name());
            assertThat(mapper.errors.get(1L)).hasSize(1000).endsWith("...");
        }

        @Test
        @DisplayName("한 행의 실패가 다른 행을 막지 않는다 / one row's failure does not block another")
        // req: FR-ATS-005, NFR-OPS-A02
        void oneFailureDoesNotBlockOthers() {
            // 레거시 다건은 부분 실패를 전체 실패로 보고했다. 행마다 독립적으로 결과를
            // 기록하면 그 형태가 재현되지 않는다.
            // The legacy batch reported partial failure as total failure; recording per row prevents it.
            StubMapper mapper = new StubMapper();
            mapper.claimable.add(entry(1, OutboxStatus.PENDING, 0));
            mapper.claimable.add(entry(2, OutboxStatus.PENDING, 0));
            mapper.claimable.add(entry(3, OutboxStatus.PENDING, 0));

            OutboxDispatcher dispatcher = dispatcher(
                    mapper,
                    new StubVendor(tranId -> tranId.endsWith("2")
                            ? VendorSendResult.notDelivered("HTTP 400", 400)
                            : VendorSendResult.accepted("ok")),
                    false,
                    3);

            List<OutboxDispatcher.DispatchOutcome> outcomes = dispatcher.runOnce();

            assertThat(outcomes).hasSize(3);
            assertThat(mapper.outcomes)
                    .containsEntry(1L, OutboxStatus.SENT.name())
                    .containsEntry(2L, OutboxStatus.FAILED.name())
                    .containsEntry(3L, OutboxStatus.SENT.name());
        }
    }
}
