package com.webcash.iris.biztalk.alimtalk.domain;

import com.webcash.iris.biztalk.alimtalk.infra.db.OutboxMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 발송 접수. / Send acceptance.
 *
 * <h2>접수와 발송을 분리하는 이유 / why acceptance is separated from despatch</h2>
 * <p>레거시는 운영자의 요청 처리 안에서 벤더를 직접 호출했다. 그래서 벤더가 느리면 화면이
 * 느려지고, 벤더가 죽으면 발송이 <b>사라졌다</b> — 다시 시도할 기록이 어디에도 남지 않았기
 * 때문이다. 여기서는 접수가 <b>데이터베이스에 쓰는 것으로 끝난다</b>. 실제 호출은
 * {@link OutboxDispatcher} 가 별도로 한다.</p>
 * <p>The legacy called the vendor inside the operator's request, so a slow vendor made the screen slow
 * and a dead vendor made the send <b>vanish</b>: no record remained from which to retry. Here acceptance
 * ends when the row is written, and the call itself is made separately by {@link OutboxDispatcher}.</p>
 *
 * <p>그 대가를 분명히 적어 둔다: 운영자는 "발송했다" 는 확인을 받지 못하고 <b>"접수했다"</b> 는
 * 확인을 받는다. 두 문구는 다르며, 다르게 표시해야 한다(NFR-OPS-A02). 레거시는 접수를 발송으로
 * 표시했고, 그래서 전달되지 않은 통지가 전달된 것으로 보였다.</p>
 * <p>The cost, stated plainly: the operator is told <b>accepted</b>, not "sent". Those are different
 * and must be shown differently (NFR-OPS-A02). The legacy showed acceptance as delivery, so an
 * undelivered notification looked delivered.</p>
 *
 * <h2>한 트랜잭션에 무엇이 들어가는가 / what one transaction covers</h2>
 * <p>다건 접수는 <b>전부 또는 전무</b>다. 100건 중 60건만 아웃박스에 들어간 상태로 커밋되면
 * 운영자는 100건을 요청했는데 60건이 나가고, 그 사실을 알 방법이 없다. 부분 접수는 부분
 * 전달보다 나쁘다 — 부분 전달은 최소한 기록에 남는다.</p>
 * <p>A batch is accepted <b>all or nothing</b>. Committing 60 of 100 rows would send 60 while the
 * operator asked for 100, with no way to notice. Partial acceptance is worse than partial delivery,
 * because partial delivery at least leaves a record.</p>
 *
 * // source: biztalk_admin_50_s001_act.jsp:118-137 — vendor called inside the request, no outbox
 * // req: FR-ATS-001, FR-ATS-002, FR-ATC-004, ADR-ATK-023, NFR-OPS-A02
 */
@Service
public class AlimTalkSendService {

    private final OutboxMapper outbox;

    /**
     * 서비스를 만든다. / Creates the service.
     *
     * @param outbox 아웃박스 매퍼 / the outbox mapper
     *
     * // req: FR-ATS-001
     */
    public AlimTalkSendService(OutboxMapper outbox) {
        this.outbox = outbox;
    }

    /**
     * 메시지들을 접수한다. / Accepts messages for despatch.
     *
     * <p>{@code REQUIRED} 가 아니라 기본 전파를 쓰되, 이 메서드가 트랜잭션 경계임을 명시한다.
     * 여기서 예외가 나면 <b>아무 행도 남지 않는다</b>. 그것이 의도다.</p>
     * <p>An exception here leaves <b>no rows at all</b>, which is the intent.</p>
     *
     * <p>{@code tranId} 는 배치 전체에 하나이고 순번이 항목을 구분한다 — 계약
     * {@code ADV_KKO_AT_SEND_M} 의 구조이며, 순번을 <b>시스템이</b> 부여한다(FR-ATC-004).
     * 클라이언트가 보낸 순번을 믿으면 브라우저가 매긴 번호를 서버가 신뢰하는 셈이 된다.</p>
     * <p>One {@code tranId} per batch with the order distinguishing items — the structure of the
     * {@code ADV_KKO_AT_SEND_M} contract — and the order is assigned by the <b>system</b>
     * (FR-ATC-004); trusting a client-supplied order would mean trusting a number the browser chose.</p>
     *
     * @param isCd     이용기관코드 / institution code
     * @param tranId   거래고유번호 / transaction id
     * @param payloads 항목별 계약 적합 payload, 순서가 순번이 된다
     *                 / per-item conforming payloads; their order becomes the assigned order
     * @param dueAt    예약 발송 시각 또는 {@code null} / the scheduled time, or {@code null}
     * @return 접수 결과 / the acceptance result
     * @throws IllegalArgumentException payload 가 없으면 / when there is nothing to accept
     *
     * // req: FR-ATS-001, FR-ATS-002, FR-ATC-004
     */
    @Transactional
    public Acceptance accept(String isCd, String tranId, List<String> payloads, LocalDateTime dueAt) {
        if (payloads == null || payloads.isEmpty()) {
            // 빈 접수를 성공으로 돌려주지 않는다. 성공했다고 알리면 운영자는 무언가 나갔다고
            // 믿는다 — 아무것도 나가지 않았는데.
            // An empty acceptance is not reported as success: doing so would tell the operator
            // something went out when nothing did.
            throw new IllegalArgumentException("nothing to accept: payloads is empty");
        }

        // FR-ATS-009 — 이미 접수된 거래번호는 다시 보내지 않고 원래 결과를 돌려준다.
        //
        // UNIQUE 제약이 두 번째 삽입을 막지만 제약만으로는 충분하지 않다. 제약 위반은 예외로
        // 나타나고, 예외에는 "원래 결과" 가 없다 — 운영자는 자기 요청이 거절된 이유가 중복인지
        // 오류인지 알 수 없다. 그리고 레거시는 tran_id 를 시각으로만 만들어(D-A25) 이 충돌을
        // 구조적으로 유발하면서 감지하지 못했으므로, 여기서 명시적으로 다룬다.
        //
        // The UNIQUE constraint blocks the second insert but is not sufficient: a violation arrives as an
        // exception, and an exception carries no "original outcome", so the operator cannot tell a
        // duplicate from an error. And since the legacy derived tran_id from the clock alone (D-A25),
        // structurally inviting this collision without detecting it, it is handled explicitly here.
        List<OutboxEntry> existing = outbox.findByTranId(isCd, tranId);
        if (!existing.isEmpty()) {
            return Acceptance.duplicate(tranId, existing);
        }

        List<Long> accepted = new ArrayList<>(payloads.size());
        for (int i = 0; i < payloads.size(); i++) {
            OutboxEntry entry = OutboxEntry.pending(isCd, tranId, i + 1, payloads.get(i), dueAt);
            outbox.insert(entry);
            accepted.add(entry.outboxId());
        }
        return new Acceptance(tranId, accepted.size(), dueAt, false, List.of());
    }

    /**
     * 이 거래번호의 결말을 돌려준다. / Returns how this transaction ended.
     *
     * <p>FR-ATS-002 는 벤더 결과를 <b>운영자에게 제시</b>하도록 요구한다. 아웃박스가 접수와 발송을
     * 분리하는 대가로 접수 응답에는 그 결과가 없으므로, 이 조회가 그 요구를 이어받는다.</p>
     * <p>FR-ATS-002 requires the vendor outcome to be <b>presented to the operator</b>. Separating
     * acceptance from despatch means the acceptance response cannot carry it, so this lookup takes up
     * that requirement.</p>
     *
     * <p>⚠ 이것이 FR-ATS-002 를 <b>문자 그대로</b> 만족시키지는 않는다. 그 요구는 응답이 결과를
     * 담는 것으로 읽히고, 여기서는 두 번째 요청이 필요하다. 해석 변경이므로 PM 결재 대상이다 —
     * ADR-ATK-023 수정 2 에 기록했다.</p>
     * <p>⚠ This does not satisfy FR-ATS-002 <b>literally</b>: that requirement reads as the response
     * carrying the outcome, whereas this needs a second request. It is a change of interpretation and so
     * requires a PM ruling — recorded as ADR-ATK-023 amendment 2.</p>
     *
     * @param isCd   이용기관코드 / institution code
     * @param tranId 거래고유번호 / transaction id
     * @return 항목별 결말, 접수 기록이 없으면 빈 목록 / the per-item outcome, empty when unknown
     *
     * // req: FR-ATS-002, FR-ATS-009, FR-AZ-A02
     */
    public List<OutboxEntry> outcomeOf(String isCd, String tranId) {
        return outbox.findByTranId(isCd, tranId);
    }

    /**
     * 이 기관의 미완료 건수. / The unfinished count for this institution.
     *
     * @param isCd 이용기관코드 / institution code
     * @return 미완료 건수 / the unfinished count
     *
     * // req: FR-ATS-009, FR-AZ-A02
     */
    public int unfinishedCount(String isCd) {
        return outbox.countUnfinished(isCd);
    }

    /**
     * 접수 결과. / The result of an acceptance.
     *
     * <p>{@code accepted} 라는 이름을 쓰는 이유: {@code sent} 라고 부르면 화면과 로그가 결국
     * "발송됨" 이라고 쓰게 되고, 그것이 NFR-OPS-A02 가 금지하는 혼동이다. 이름이 경계를
     * 지킨다.</p>
     * <p>Named {@code accepted} rather than {@code sent} because the latter would eventually be
     * rendered as "delivered" on a screen or in a log — the conflation NFR-OPS-A02 forbids. The name
     * holds the boundary.</p>
     *
     * @param tranId        거래고유번호 / the transaction id
     * @param acceptedCount 접수된 건수 — 중복이면 <b>원래</b> 건수 / the number accepted; for a
     *                      duplicate, the <b>original</b> count
     * @param dueAt         예약 시각 또는 {@code null} / the scheduled time, or {@code null}
     * @param duplicate     이미 접수된 거래번호였는지 / whether the transaction id was already accepted
     * @param statuses      항목별 상태 — 중복일 때만 채워진다 / per-item statuses, populated only for a
     *                      duplicate
     *
     * // req: FR-ATS-002, FR-ATS-009, NFR-OPS-A02
     */
    public record Acceptance(
            String tranId,
            int acceptedCount,
            LocalDateTime dueAt,
            boolean duplicate,
            List<OutboxStatus> statuses) {

        /**
         * 이미 접수된 거래번호에 대한 결과를 만든다. / Builds the result for an already-accepted id.
         *
         * <p>{@code acceptedCount} 에 <b>원래</b> 건수를 담는다. 0 을 담으면 화면이 "아무것도
         * 접수되지 않았다" 고 표시하게 되고, 운영자는 아직 보내지 않았다고 믿어 다시 시도한다 —
         * 중복 거절의 목적이 정확히 그것을 막는 것인데.</p>
         * <p>Carries the <b>original</b> count: a zero would have the screen say nothing was accepted, so
         * the operator would believe it had not gone and try again — precisely what rejecting the
         * duplicate exists to prevent.</p>
         *
         * @param tranId   거래고유번호 / the transaction id
         * @param existing 이미 있는 행들 / the rows already present
         * @return 중복 결과 / a duplicate result
         *
         * // req: FR-ATS-009
         */
        static Acceptance duplicate(String tranId, List<OutboxEntry> existing) {
            return new Acceptance(
                    tranId,
                    existing.size(),
                    existing.get(0).dueAt(),
                    true,
                    existing.stream().map(OutboxEntry::status).toList());
        }

        /**
         * 예약 발송인가. / Is this a reservation?
         *
         * @return 예약이면 {@code true} / {@code true} when scheduled
         *
         * // req: FR-ATS-006
         */
        public boolean isScheduled() {
            return dueAt != null;
        }
    }
}
