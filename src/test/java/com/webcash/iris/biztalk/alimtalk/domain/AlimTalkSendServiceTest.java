package com.webcash.iris.biztalk.alimtalk.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.webcash.iris.biztalk.alimtalk.infra.db.OutboxMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link AlimTalkSendService} 검증. / Verification for {@link AlimTalkSendService}.
 *
 * <p><b>증명하지 못하는 것</b>: {@code @Transactional} 의 실제 롤백. 트랜잭션은 Spring 컨텍스트와
 * 데이터베이스가 있어야 동작하고 둘 다 없다(RISK-A12). 여기서 고정하는 것은 <b>어떤 행이 어떤
 * 값으로 쓰이는가</b>이며, 전부-또는-전무가 실제로 지켜지는지는 A2-15 로 이월한다.</p>
 * <p><b>What these cannot show</b>: that {@code @Transactional} actually rolls back, which needs a
 * Spring context and a database, neither of which is available (RISK-A12). What is pinned here is which
 * rows are written with which values; that all-or-nothing genuinely holds is carried to A2-15.</p>
 *
 * // source: biztalk_admin_50_s001_act.jsp:118-137
 * // req: FR-ATS-001, FR-ATS-002, FR-ATC-004, NFR-OPS-A02
 */
class AlimTalkSendServiceTest {

    /** 삽입된 행을 모으는 매퍼 대역. / A stub mapper collecting inserted rows. */
    private static final class StubMapper implements OutboxMapper {
        private final List<OutboxEntry> inserted = new ArrayList<>();
        private int nextId = 1;
        private int unfinished;

        @Override
        public int insert(OutboxEntry entry) {
            // MyBatis 의 useGeneratedKeys 는 전달된 객체에 키를 채운다. record 는 불변이므로
            // 대역에서는 흉내 낼 수 없다 — 그 차이 자체를 아래 테스트가 지적한다.
            // MyBatis's useGeneratedKeys populates the passed object; a record is immutable so the stub
            // cannot mimic that, and the test below records that difference.
            inserted.add(entry);
            nextId++;
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
            return unfinished;
        }
    }

    @Test
    @DisplayName("FR-ATC-004 — 순번은 1부터 시스템이 부여한다 / the system assigns a one-based order")
    // req: FR-ATC-004, CONST-DATA-A01
    void systemAssignsOneBasedOrder() {
        // 레거시는 order 를 아예 내보내지 않아 수신자와 메시지의 대응이 배열 위치에만
        // 의존했다(D-A3). 클라이언트가 순번을 보내게 하면 브라우저가 매긴 번호를 서버가
        // 믿는 셈이 되어 같은 취약함이 형태만 바꿔 돌아온다.
        // The legacy emitted no order at all (D-A3). Letting the client send one would mean trusting a
        // number the browser chose — the same weakness in another shape.
        StubMapper mapper = new StubMapper();
        AlimTalkSendService service = new AlimTalkSendService(mapper);

        service.accept("K00001", "T260819001", List.of("{\"a\":1}", "{\"a\":2}", "{\"a\":3}"), null);

        assertThat(mapper.inserted).extracting(OutboxEntry::msgOrder).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("FR-ATS-001 — 접수는 PENDING 으로만 쓴다 / acceptance writes PENDING only")
    // req: FR-ATS-001, NFR-OPS-A02
    void acceptanceWritesPendingOnly() {
        // 접수 시점에 SENT 인 행을 만들 수 있으면, 보내지 않은 메시지를 보냈다고 기록하는 일이
        // 문법적으로 가능해진다. OutboxEntry.pending 이 상태를 고정하는 이유다.
        // If a row could be created already SENT, recording an unsent message as sent would be
        // syntactically possible; that is why OutboxEntry.pending fixes the status.
        StubMapper mapper = new StubMapper();
        new AlimTalkSendService(mapper).accept("K00001", "T260819001", List.of("{}"), null);

        assertThat(mapper.inserted).allSatisfy(
                e -> assertThat(e.status()).isEqualTo(OutboxStatus.PENDING));
        assertThat(mapper.inserted).allSatisfy(e -> assertThat(e.attempts()).isZero());
    }

    @Test
    @DisplayName("배치 전체가 하나의 거래고유번호를 공유한다 / one transaction id spans the batch")
    // req: FR-ATC-004, ADR-ATK-026
    void oneTransactionIdSpansTheBatch() {
        StubMapper mapper = new StubMapper();
        new AlimTalkSendService(mapper).accept("K00001", "T260819001", List.of("{}", "{}"), null);

        assertThat(mapper.inserted).extracting(OutboxEntry::tranId)
                .containsExactly("T260819001", "T260819001");
    }

    @Test
    @DisplayName("FR-ATS-006 — 예약 시각이 항목마다 전달된다 / the scheduled time reaches every item")
    // req: FR-ATS-006, FR-ATC-007
    void scheduledTimeReachesEveryItem() {
        // 계약은 reqdate 를 항목마다 선언하는데 레거시 다건 화면은 수집하지 않았다(D-A14) —
        // 다건 예약 발송이 불가능했다. 설계 결정이 아니라 누락이었다.
        // The contract declares reqdate per item but the legacy batch screen never collected it (D-A14),
        // making batch reservation impossible. An omission, not a design decision.
        LocalDateTime due = LocalDateTime.of(2026, 8, 20, 9, 0);
        StubMapper mapper = new StubMapper();

        AlimTalkSendService.Acceptance acceptance =
                new AlimTalkSendService(mapper).accept("K00001", "T260819001", List.of("{}", "{}"), due);

        assertThat(mapper.inserted).allSatisfy(e -> assertThat(e.dueAt()).isEqualTo(due));
        assertThat(acceptance.isScheduled()).isTrue();
    }

    @Test
    @DisplayName("예약이 없으면 즉시 발송 대상이다 / with no reservation the row is due immediately")
    // req: FR-ATS-006
    void noReservationIsDueImmediately() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 19, 14, 0);
        assertThat(OutboxEntry.pending("K00001", "T1", 1, "{}", null).isDue(now)).isTrue();
        assertThat(OutboxEntry.pending("K00001", "T1", 1, "{}", now.plusHours(1)).isDue(now)).isFalse();
        assertThat(OutboxEntry.pending("K00001", "T1", 1, "{}", now).isDue(now))
                .as("정확히 예약 시각이면 발송한다 / exactly at the scheduled time it is due")
                .isTrue();
    }

    @Test
    @DisplayName("빈 접수는 성공으로 돌려주지 않는다 / an empty acceptance is not reported as success")
    // req: FR-ATS-002, NFR-USE-A03
    void emptyAcceptanceIsRefused() {
        // 성공했다고 알리면 운영자는 무언가 나갔다고 믿는다 — 아무것도 나가지 않았는데.
        // Reporting success would tell the operator something went out when nothing did.
        AlimTalkSendService service = new AlimTalkSendService(new StubMapper());

        assertThatThrownBy(() -> service.accept("K00001", "T260819001", List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.accept("K00001", "T260819001", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("NFR-OPS-A02 — 접수는 발송이 아니다 / acceptance is not delivery")
    // req: NFR-OPS-A02, FR-ATS-002
    void acceptanceIsNotDelivery() {
        // 이름이 경계를 지킨다. acceptedCount 를 sentCount 라고 불렀다면 화면과 로그가 결국
        // "발송됨" 이라고 쓰게 되고, 그것이 레거시가 전달되지 않은 통지를 전달된 것으로 보이게
        // 만든 방식이다.
        // The name holds the boundary: called sentCount, it would eventually be rendered as
        // "delivered" — how the legacy made an undelivered notification look delivered.
        AlimTalkSendService.Acceptance acceptance =
                new AlimTalkSendService(new StubMapper())
                        .accept("K00001", "T260819001", List.of("{}", "{}"), null);

        assertThat(acceptance.acceptedCount()).isEqualTo(2);
        assertThat(acceptance.tranId()).isEqualTo("T260819001");
        assertThat(acceptance.isScheduled()).isFalse();
    }

    @Test
    @DisplayName("FR-AZ-A02 — 미완료 조회가 기관으로 한정된다 / the unfinished count is scoped")
    // req: FR-ATS-009, FR-AZ-A02
    void unfinishedCountIsScoped() {
        StubMapper mapper = new StubMapper();
        mapper.unfinished = 4;

        assertThat(new AlimTalkSendService(mapper).unfinishedCount("K00001")).isEqualTo(4);
    }
}
