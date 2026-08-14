package com.webcash.iris.common.audit;

import java.time.Clock;
import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 감사 기록 작성. / Writes audit records.
 *
 * <p>업무 트랜잭션과 <b>독립된 트랜잭션</b>으로 기록한다({@code REQUIRES_NEW}).
 * 업무 처리가 실패하여 롤백되어도 "시도되었다"는 증적은 남아야 하기 때문이다.
 * 실패한 조회가 자기 자신의 감사 흔적을 지우는 것은 전자금융감독규정 관점에서
 * 허용되지 않는다.</p>
 * <p>Written in a transaction <b>independent</b> of the business transaction
 * ({@code REQUIRES_NEW}). If the business operation fails and rolls back, the
 * evidence that it was attempted must survive: a failed query erasing its own
 * audit trail is not acceptable under 전자금융감독규정.</p>
 *
 * <p>거부(DENIED)도 반드시 기록한다. 교차 테넌트 접근 시도나 잠긴 계정 접근 시도는
 * 성공한 조회보다 더 중요한 증적이다.</p>
 * <p>Denials are recorded, not only successes. An attempted cross-tenant read or an
 * attempt against a locked account is more valuable evidence than a successful one.</p>
 *
 * // req: NFR-OPS-AUDIT-L01, ADR-006, ADR-002
 */
@Service
public class AuditService {

    private final AuditMapper mapper;
    private final Clock clock;

    /**
     * 감사 서비스 생성. / Creates the audit service.
     *
     * @param mapper 감사 저장소 매퍼 / the audit store mapper
     * @param clock  시각 공급자 / the clock
     */
    public AuditService(AuditMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * 감사 기록을 저장한다. / Persists an audit record.
     *
     * @param event 기록할 사건 / the event to record
     */
    // req: NFR-OPS-AUDIT-L01
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEvent event) {
        mapper.insert(event);
    }

    /**
     * 인증 사건을 기록하는 편의 메서드. / Convenience method for authentication events.
     *
     * @param actor         행위자 이메일 / the acting principal's email
     * @param action        행위 코드 / the action code
     * @param outcome       결과 / the outcome
     * @param detail        비민감 부가정보 / non-sensitive detail
     * @param sourceIp      신뢰 가능한 출처 IP / trusted source address
     * @param correlationId 요청 상관 식별자 / request correlation id
     */
    // req: NFR-OPS-AUDIT-L01, NFR-OPS-AUDIT-L02
    public void recordAuth(String actor,
                           String action,
                           AuditEvent.Outcome outcome,
                           String detail,
                           String sourceIp,
                           String correlationId) {
        record(new AuditEvent(
                Instant.now(clock), actor, null, action, outcome, detail, sourceIp, correlationId));
    }

    /**
     * 감사 저장소 매퍼. 추가(insert)만 노출한다.
     * Audit store mapper. Exposes insert only.
     *
     * <p>수정·삭제 메서드를 <b>정의하지 않는다.</b> 애플리케이션 경로에 존재하지 않는
     * 기능은 실수로 호출될 수도 없다 — 추가 전용 성질을 코드 구조로 보장한다.</p>
     * <p>Update and delete are <b>not declared.</b> A capability absent from the
     * application cannot be invoked by mistake, which makes the append-only
     * property structural rather than a matter of discipline.</p>
     */
    @Mapper
    public interface AuditMapper {
        /**
         * 감사 기록을 추가한다. / Appends an audit record.
         *
         * @param event 기록 / the record
         */
        void insert(@Param("e") AuditEvent event);
    }
}
