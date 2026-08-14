package com.webcash.iris.biztalk.domain;

import com.webcash.iris.biztalk.infra.db.MessageDetailMapper;
import com.webcash.iris.common.audit.AuditEvent;
import com.webcash.iris.common.audit.AuditService;
import com.webcash.iris.common.tenant.TenantContext;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 문자상세내역 조회 서비스. / Message detail service.
 *
 * <p>목록 조회와 동일하게 <b>테넌트 범위를 여기서 강제</b>한다. 상세조회는 목록보다 위험이
 * 크다 — 메시지 본문({@code MSG})을 반환하므로, 범위가 잘못되면 다른 고객사가 보낸 메시지의
 * <b>내용 전체</b>가 노출된다.</p>
 * <p>Tenant scope is enforced here as in the list service. Detail carries more risk: it returns
 * the message body, so a wrong scope exposes the <b>entire content</b> of another client's
 * message rather than only its metadata.</p>
 *
 * // source: biztalk_admin_41_l001_act.jsp
 * // req: FR-MSGD-001, FR-MSGD-002, FR-MSGD-008, FR-TEN-001, NFR-SEC-TENANT
 */
@Service
public class MessageDetailService {

    private final MessageDetailMapper mapper;
    private final AuditService audit;

    /**
     * 서비스를 생성한다. / Creates the service.
     *
     * @param mapper 상세 조회 매퍼 / the detail mapper
     * @param audit  감사 서비스 / the audit service
     */
    public MessageDetailService(MessageDetailMapper mapper, AuditService audit) {
        this.mapper = mapper;
        this.audit = audit;
    }

    /**
     * 테넌트 범위를 적용하여 상세내역을 조회한다.
     * Looks up a detail record within the caller's tenant scope.
     *
     * <p>존재하지 않는 경우와 <b>다른 테넌트의 것인 경우</b>를 구분하지 않고 모두
     * {@code empty} 로 반환한다. 구분하면 "그 메시지키는 존재한다"는 사실이 드러나
     * 열거 공격의 단서가 된다(TM-009).</p>
     * <p>Not-found and belongs-to-another-tenant both return {@code empty}. Distinguishing them
     * would reveal that a given message key exists, which is the foothold for enumeration
     * (TM-009).</p>
     *
     * @param key      조회 키 / the lookup key
     * @param sourceIp 신뢰 가능한 출처 IP / the trusted source address
     * @return 상세내역 또는 empty / the detail, or empty
     */
    // req: FR-MSGD-001, FR-MSGD-008, NFR-SEC-TENANT, TM-009
    @Transactional(readOnly = true)
    public Optional<MessageDetail> find(MessageDetailKey key, String sourceIp) {
        TenantContext.TenantPrincipal principal = TenantContext.require();

        // 운영자는 null(전체), 이용기관 담당자는 자신의 코드. 요청에 담긴 값은 쓰지 않는다.
        // Operator gets null (unrestricted); a tenant user gets their own code. Nothing from the
        // request is used.
        String institutionCode = principal.effectiveInstitutionCode(null);

        MessageDetail detail = mapper.findDetail(key, institutionCode);

        if (detail == null) {
            // 조회 실패도 기록한다. 존재하지 않는 키에 대한 반복 조회는 열거 시도의 신호다.
            // A miss is recorded too: repeated lookups of non-existent keys signal enumeration.
            audit.recordAuth(principal.email(), AuditEvent.ACTION_MESSAGE_DETAIL_VIEW,
                    AuditEvent.Outcome.DENIED,
                    "not-found-or-not-owned msgkey=" + key.messageKey(), sourceIp, null);
            return Optional.empty();
        }

        audit.recordAuth(principal.email(), AuditEvent.ACTION_MESSAGE_DETAIL_VIEW,
                AuditEvent.Outcome.OK,
                "msgkey=" + key.messageKey() + " type=" + key.messageType().code()
                        + "/" + key.tableType().code(), sourceIp, null);
        return Optional.of(detail);
    }
}
