package com.webcash.iris.biztalk.alimtalk.infra.db;

import com.webcash.iris.biztalk.alimtalk.domain.OutboxEntry;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 아웃박스 읽기·쓰기. / Outbox reads and writes.
 *
 * <h2>{@code SKIP LOCKED} 를 쓰는 이유 / why {@code SKIP LOCKED}</h2>
 * <p>여러 인스턴스가 같은 표를 보면서 같은 행을 두 번 보내지 않아야 한다. 선택지는 둘이었다 —
 * 애플리케이션 수준 리더 선출, 또는 데이터베이스의 행 잠금. 후자를 쓴다: 조정 서비스(ZooKeeper
 * 등)를 새로 들이지 않아도 되고, 리더가 죽었는지 판단하는 로직 자체가 또 하나의 결함 표면이
 * 되기 때문이다.</p>
 * <p>Several instances read this table and must not send the same row twice. The choice was
 * application-level leader election or database row locking; the latter avoids introducing a
 * coordination service, and avoids the logic for deciding whether a leader has died — itself another
 * surface for defects.</p>
 *
 * <p>{@code FOR UPDATE SKIP LOCKED} 는 다른 트랜잭션이 잡고 있는 행을 <b>기다리지 않고
 * 건너뛴다</b>. 기다리면(기본 동작) 인스턴스들이 한 줄로 서서 처리량이 하나만큼으로 줄어든다.</p>
 * <p>{@code FOR UPDATE SKIP LOCKED} passes over rows another transaction holds rather than waiting;
 * waiting — the default — would queue the instances up and reduce throughput to that of one.</p>
 *
 * <h2>시간 기반 만료도 함께 두는 이유 / why there is also a time-based expiry</h2>
 * <p>행 잠금은 트랜잭션이 끝나면 풀린다. 그런데 인스턴스가 <b>응답을 기다리는 중에</b> 죽으면
 * 트랜잭션이 롤백되어 잠금이 즉시 풀리고, 그 행은 벤더에 이미 도달했을 수 있다. 그래서
 * {@code CLAIMED_UNTIL} 을 함께 본다 — 잠금은 동시성을, 만료 시각은 "아직 응답을 기다리는 중일
 * 수 있음" 을 표현한다.</p>
 * <p>A row lock releases when its transaction ends. But if an instance dies <b>while awaiting a
 * response</b>, the rollback frees the lock immediately even though the row may already have reached
 * the vendor. {@code CLAIMED_UNTIL} covers that: the lock expresses concurrency, the expiry expresses
 * "a response may still be outstanding".</p>
 *
 * <p>⚠ {@code CLAIMED_UNTIL} 은 벤더 read 타임아웃(60초)보다 길어야 한다 — 짧으면 응답을 기다리는
 * 행을 다시 집어 중복 발송한다. ANALYSIS-A2-05-vendor-transport.md §2③.</p>
 *
 * <p><b>검증 한계</b>: Docker 금지(RISK-A12)로 실제 PostgreSQL 통합 테스트를 돌릴 수 없다.
 * {@code OutboxMapperSqlTest} 는 <b>대체물이며 동등물이 아니다</b> — SQL 식별자 회귀는 막지만
 * {@code SKIP LOCKED} 의 실제 동시성 동작은 증명하지 못한다. 그 성질은 DB 없이 검증할 방법이
 * 없으므로 A2-15 통합 시험으로 이월한다.</p>
 * <p><b>Verification limit</b>: with Docker prohibited (RISK-A12) there is no real PostgreSQL test.
 * {@code OutboxMapperSqlTest} is a substitute, not an equivalent: it catches identifier regressions
 * but cannot demonstrate {@code SKIP LOCKED}'s concurrency behaviour, which is carried to A2-15.</p>
 *
 * // source: mapping/analysis/ANALYSIS-A2-02-existing-schema.md
 * // req: FR-ATS-001, FR-ATS-005, ADR-ATK-023, RISK-A12
 */
@Mapper
public interface OutboxMapper {

    /**
     * 접수 행을 넣는다. / Inserts an accepted row.
     *
     * <p>{@code UQ_KKB_ATK_SEND_OUTBOX (IS_CD, TRAN_ID, MSG_ORDER)} 위반은 <b>예외로 올라온다</b>.
     * 조용히 무시하지 않는 이유: 같은 거래번호·순번이 두 번 접수되는 것은 호출부의 결함이거나
     * 운영자가 같은 값을 다시 입력한 것이며, 어느 쪽이든 알려야 한다. 레거시는
     * {@code tran_id} 를 시각으로만 만들어(D-A25) 이 충돌을 구조적으로 유발하면서도 감지하지
     * 못했다.</p>
     * <p>A unique-constraint violation <b>propagates</b>. Swallowing it would hide either a caller
     * defect or an operator re-entering the same value, and both need to be known. The legacy derived
     * {@code tran_id} from the clock alone (D-A25), structurally inviting this collision while being
     * unable to detect it.</p>
     *
     * @param entry 접수할 행 / the row to accept
     * @return 삽입된 행 수 / rows inserted
     *
     * // req: FR-ATS-001, ADR-ATK-026
     */
    @Insert("""
            INSERT INTO KKB_ATK_SEND_OUTBOX
                (IS_CD, TRAN_ID, MSG_ORDER, PAYLOAD, STATUS, ATTEMPTS, DUE_AT, CREATED_AT, UPDATED_AT)
            VALUES
                (#{isCd}, #{tranId}, #{msgOrder}, #{payload}, #{status}, #{attempts}, #{dueAt}, now(), now())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "outboxId", keyColumn = "OUTBOX_ID")
    int insert(OutboxEntry entry);

    /**
     * 보낼 행을 잡는다. / Claims rows to send.
     *
     * <p>{@code UNKNOWN} 을 포함할지는 호출부가 정한다 — 벤더 멱등성이 확인되지 않았으므로
     * (RISK-A07) 이 매퍼가 그 정책을 품지 않는다. 상태 목록을 인자로 받는 이유가 그것이다.</p>
     * <p>Whether {@code UNKNOWN} is included is the caller's decision: with vendor idempotency
     * unverified (RISK-A07) this mapper does not embed that policy, which is why the status list is a
     * parameter.</p>
     *
     * <p>{@code CLAIMED_UNTIL} 이 아직 지나지 않은 행은 제외한다 — 다른 인스턴스가 응답을
     * 기다리는 중일 수 있다.</p>
     * <p>Rows whose {@code CLAIMED_UNTIL} has not elapsed are excluded: another instance may still be
     * awaiting a response for them.</p>
     *
     * @param statuses 대상 상태 / the statuses to consider
     * @param now      현재 시각 / the current time
     * @param limit    한 번에 잡을 최대 행 수 / the maximum rows to claim at once
     * @return 잡힌 행 / the claimed rows
     *
     * // req: FR-ATS-005, ADR-ATK-023, RISK-A07
     */
    @Select("""
            <script>
            SELECT OUTBOX_ID, IS_CD, TRAN_ID, MSG_ORDER, PAYLOAD, STATUS, ATTEMPTS,
                   DUE_AT, CLAIMED_UNTIL, LAST_ERROR, CREATED_AT, UPDATED_AT
              FROM KKB_ATK_SEND_OUTBOX
             WHERE STATUS IN
                   <foreach item="s" collection="statuses" open="(" separator="," close=")">#{s}</foreach>
               AND (DUE_AT IS NULL OR DUE_AT &lt;= #{now})
               AND (CLAIMED_UNTIL IS NULL OR CLAIMED_UNTIL &lt;= #{now})
             ORDER BY OUTBOX_ID
             LIMIT #{limit}
             FOR UPDATE SKIP LOCKED
            </script>
            """)
    List<OutboxEntry> claim(
            @Param("statuses") List<String> statuses,
            @Param("now") LocalDateTime now,
            @Param("limit") int limit);

    /**
     * 클레임 만료 시각을 세운다. / Sets the claim expiry.
     *
     * @param outboxId     대상 행 / the row
     * @param claimedUntil 만료 시각 / the expiry
     * @return 변경된 행 수 / rows updated
     *
     * // req: FR-ATS-005
     */
    @Update("""
            UPDATE KKB_ATK_SEND_OUTBOX
               SET CLAIMED_UNTIL = #{claimedUntil}, ATTEMPTS = ATTEMPTS + 1, UPDATED_AT = now()
             WHERE OUTBOX_ID = #{outboxId}
            """)
    int markClaimed(
            @Param("outboxId") long outboxId, @Param("claimedUntil") LocalDateTime claimedUntil);

    /**
     * 결과를 기록한다. / Records an outcome.
     *
     * <p>{@code CLAIMED_UNTIL} 을 {@code NULL} 로 되돌리는 이유: 결과가 정해졌으면 더 이상
     * "응답을 기다리는 중" 이 아니다. 남겨 두면 재시도 가능한 행이 만료 시각까지 잡히지 않는다.</p>
     * <p>{@code CLAIMED_UNTIL} is cleared because once an outcome is known the row is no longer
     * awaiting a response; leaving it set would keep a retryable row invisible until it elapsed.</p>
     *
     * @param outboxId  대상 행 / the row
     * @param status    새 상태 / the new status
     * @param lastError 오류 요약 또는 {@code null} / an error summary, or {@code null}
     * @return 변경된 행 수 / rows updated
     *
     * // req: FR-ATS-005, NFR-OPS-A02
     */
    @Update("""
            UPDATE KKB_ATK_SEND_OUTBOX
               SET STATUS = #{status}, LAST_ERROR = #{lastError},
                   CLAIMED_UNTIL = NULL, UPDATED_AT = now()
             WHERE OUTBOX_ID = #{outboxId}
            """)
    int recordOutcome(
            @Param("outboxId") long outboxId,
            @Param("status") String status,
            @Param("lastError") String lastError);

    /**
     * 거래고유번호로 접수된 행을 찾는다. / Finds the rows accepted under one transaction id.
     *
     * <p>두 가지에 쓰인다.</p>
     * <ol>
     *   <li><b>FR-ATS-009 중복 거절</b> — 이미 접수된 {@code (is_cd, tran_id)} 를 다시 보내면
     *       발송하지 않고 <b>원래 결과를 돌려준다</b>. {@code UNIQUE} 제약이 두 번째 삽입을 막지만,
     *       제약 위반 예외만으로는 "원래 결과" 를 알 수 없다 — 그래서 조회가 필요하다.</li>
     *   <li><b>FR-ATS-002 결과 제시</b> — 운영자가 자기 발송의 결말을 볼 수 있어야 한다. 아웃박스가
     *       접수와 발송을 분리하는 대가로 응답에는 벤더 결과가 없으므로, 이 조회가 그것을
     *       대신한다.</li>
     * </ol>
     * <p>Used for two things: <b>FR-ATS-009</b> duplicate rejection, where a repeat of an accepted
     * {@code (is_cd, tran_id)} returns the <b>original outcome</b> rather than sending again — the
     * {@code UNIQUE} constraint blocks the second insert, but a constraint violation alone cannot tell us
     * what the original outcome was; and <b>FR-ATS-002</b>, where the operator must be able to see how
     * their send ended, which the acceptance response cannot carry.</p>
     *
     * <p>{@code IS_CD} 로 함께 한정하는 이유: {@code tran_id} 만으로 조회하면 한 기관의 운영자가
     * 다른 기관의 거래번호를 추측해 그 발송 상태를 읽을 수 있다. 거래번호는 비밀이 아니다.</p>
     * <p>Bounded by {@code IS_CD} as well: querying on {@code tran_id} alone would let an operator at one
     * institution guess another's transaction id and read its send status. A transaction id is not a
     * secret.</p>
     *
     * @param isCd   이용기관코드 / institution code
     * @param tranId 거래고유번호 / transaction id
     * @return 순번 순서의 행들, 없으면 빈 목록 / the rows in order, empty when none
     *
     * // req: FR-ATS-002, FR-ATS-009, FR-AZ-A02
     */
    @Select("""
            SELECT OUTBOX_ID, IS_CD, TRAN_ID, MSG_ORDER, PAYLOAD, STATUS, ATTEMPTS,
                   DUE_AT, CLAIMED_UNTIL, LAST_ERROR, CREATED_AT, UPDATED_AT
              FROM KKB_ATK_SEND_OUTBOX
             WHERE IS_CD = #{isCd}
               AND TRAN_ID = #{tranId}
             ORDER BY MSG_ORDER
            """)
    List<OutboxEntry> findByTranId(@Param("isCd") String isCd, @Param("tranId") String tranId);

    /**
     * 기관별 미완료 건수. / The unfinished count for one institution.
     *
     * <p>모든 조회가 {@code IS_CD} 로 한정된다(FR-AZ-A02). 범위 없는 집계를 만들면 그것이 곧
     * 테넌트 경계를 넘는 조회가 된다 — 레거시 {@code KKB_MSG_TMPL_L003} 이 그런 형태였다.</p>
     * <p>Every query is bounded by {@code IS_CD} (FR-AZ-A02): an unscoped aggregate would itself be a
     * cross-tenant read, which is the shape of the legacy {@code KKB_MSG_TMPL_L003}.</p>
     *
     * @param isCd 이용기관코드 / institution code
     * @return 미완료 건수 / the unfinished count
     *
     * // req: FR-ATS-009, FR-AZ-A02, NFR-OPS-A03
     */
    @Select("""
            SELECT count(*)
              FROM KKB_ATK_SEND_OUTBOX
             WHERE IS_CD = #{isCd}
               AND STATUS IN ('PENDING', 'FAILED', 'UNKNOWN')
            """)
    int countUnfinished(@Param("isCd") String isCd);
}
