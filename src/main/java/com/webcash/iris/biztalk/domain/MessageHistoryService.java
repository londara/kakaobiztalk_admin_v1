package com.webcash.iris.biztalk.domain;

import com.webcash.iris.biztalk.infra.db.MessageHistoryMapper;
import com.webcash.iris.common.audit.AuditEvent;
import com.webcash.iris.common.audit.AuditService;
import com.webcash.iris.common.tenant.TenantContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 문자내역 조회 서비스. / 문자내역 query service.
 *
 * <p><b>이 클래스가 테넌트 격리의 단일 강제 지점이다.</b> 매퍼는 {@code institutionCode} 가
 * {@code null} 이면 조건을 생성하지 않으므로, 그 {@code null} 이 운영자에게만 허용된다는
 * 불변식은 여기서 지켜져야 한다. 조회 조건에 담긴 이용기관 코드는 <b>항상</b>
 * {@link TenantContext} 에서 도출하며, 요청에 담긴 값은 운영자에게만 의미가 있다.</p>
 * <p><b>This class is the single enforcement point for tenant isolation.</b> The mapper omits
 * the predicate when {@code institutionCode} is {@code null}, so the invariant that such a null
 * is permitted only for operators must hold here. The code placed in the criteria is
 * <b>always</b> derived from {@link TenantContext}; a value from the request means something
 * only for an operator.</p>
 *
 * // source: biztalk_admin_40_l001_act.jsp, IDO.KKB_MSG_L002.xml
 * // req: FR-MSG-003, FR-MSG-007, FR-TEN-001, FR-TEN-002, FR-TEN-003, NFR-SEC-TENANT
 */
@Service
public class MessageHistoryService {

    private final MessageHistoryMapper mapper;
    private final AuditService audit;

    /**
     * 서비스를 생성한다. / Creates the service.
     *
     * @param mapper 조회 매퍼 / the query mapper
     * @param audit  감사 서비스 / the audit service
     */
    public MessageHistoryService(MessageHistoryMapper mapper, AuditService audit) {
        this.mapper = mapper;
        this.audit = audit;
    }

    /**
     * 테넌트 범위를 적용하여 문자내역을 조회한다.
     * Searches 문자내역 within the caller's tenant scope.
     *
     * <p>읽기 전용 트랜잭션이다(ADR-002). 이 슬라이스는 업무 데이터를 쓰지 않으며,
     * 읽기 전용 표시는 실수로 쓰기가 발생하면 즉시 실패하게 만든다.</p>
     * <p>A read-only transaction (ADR-002): this slice writes no business data, and the marking
     * turns an accidental write into an immediate error.</p>
     *
     * @param requested          클라이언트가 보낸 조건 / the criteria as submitted
     * @param requestedInstitution 클라이언트가 보낸 이용기관 코드 (운영자만 유효) / the requested 이용기관, honoured for operators only
     * @param sourceIp           신뢰 가능한 출처 IP / the trusted source address
     * @return 페이지 결과 / the page of results
     */
    // req: FR-MSG-003, FR-MSG-007, FR-TEN-001, NFR-OPS-AUDIT
    @Transactional(readOnly = true)
    public PagedResult<MessageHistoryRow> search(MessageHistoryCriteria requested,
                                                 String requestedInstitution,
                                                 String sourceIp) {
        TenantContext.TenantPrincipal principal = TenantContext.require();

        // 테넌트 범위를 강제한다. 이용기관 담당자의 요청 값은 무시되고, 운영자만 다른
        // 이용기관 또는 전체를 선택할 수 있다.
        // Tenant scope enforced: a tenant user's requested value is ignored; only an operator
        // may select another institution or all of them.
        String effective = principal.effectiveInstitutionCode(requestedInstitution);

        MessageHistoryCriteria scoped = rebuildWithInstitution(requested, effective);

        // 조회 전 감사. 결과 건수는 조회 후에야 알 수 있으므로 2회 기록하지 않고,
        // 조회 후 한 번만 남긴다 — 다만 예외가 발생하면 시도가 기록되지 않는다는 뜻이므로
        // 실패 경로를 명시적으로 감사한다.
        // Audited after the query so the row count can be included; the failure path is audited
        // explicitly, because otherwise an exception would leave no record of the attempt.
        try {
            int total = mapper.count(scoped);
            List<MessageHistoryRow> rows = total == 0 ? List.of() : mapper.search(scoped);

            audit.recordAuth(principal.email(), AuditEvent.ACTION_MESSAGE_HISTORY_SEARCH,
                    AuditEvent.Outcome.OK, describe(scoped, total), sourceIp, null);

            return new PagedResult<>(rows, total, scoped.page(), scoped.size());
        } catch (RuntimeException e) {
            audit.recordAuth(principal.email(), AuditEvent.ACTION_MESSAGE_HISTORY_SEARCH,
                    AuditEvent.Outcome.ERROR, "query-failed", sourceIp, null);
            throw e;
        }
    }

    /**
     * 내보내기 최대 행 수. / Maximum rows in one export.
     *
     * <p>페이지 크기 상한(500)과 <b>다른</b> 값이다. 내보내기는 화면 표시가 아니므로
     * 페이지 크기에 묶일 이유가 없지만, 상한이 없으면 31일 전체를 한 번에 요청해 메모리를
     * 소진시킬 수 있다 — CSV 를 문자열로 조립하기 때문이다. 5,000 건은 CSV 로 약 500 kB
     * 수준이다.</p>
     * <p>Deliberately different from the page-size cap of 500: an export is not a screen, but
     * without a ceiling a caller could request an entire 31-day window and exhaust memory, since
     * the CSV is assembled as a string. 5,000 rows is roughly 500 kB.</p>
     */
    // req: FR-MSG-017, NFR-PERF-04
    public static final int EXPORT_MAX_ROWS = 5_000;

    /**
     * 조회 결과를 내보내기 위해 조회한다. / Queries rows for export.
     *
     * <p>{@link #search} 와 <b>같은 테넌트 강제 경로</b>를 통과한다. 내보내기용으로 별도의
     * 조회 경로를 만들면 격리 강제가 두 곳에 존재하게 되고, 한쪽만 고쳐지는 것이 바로
     * 레거시가 D1·D4 를 갖게 된 방식이다.</p>
     * <p>Goes through the <b>same tenant enforcement path</b> as {@link #search}. A separate query
     * path for exports would put isolation in two places, and one of them being fixed while the
     * other is not is exactly how the legacy acquired D1 and D4.</p>
     *
     * <p>상한을 초과하면 잘라내지 않고 <b>거절</b>한다. 조용히 잘라낸 파일은 완전한 것으로
     * 보이며, 감사·정산 목적으로 쓰이면 잘못된 결론을 만든다.</p>
     * <p>Exceeding the cap <b>refuses</b> rather than truncates: a silently truncated file looks
     * complete, and used for audit or reconciliation it produces wrong conclusions.</p>
     *
     * @param requested            클라이언트가 보낸 조건 / the criteria as submitted
     * @param requestedInstitution 클라이언트가 보낸 이용기관 코드 / the requested 이용기관
     * @param sourceIp             출처 IP / the source address
     * @return 내보낼 행 / the rows to export
     * @throws MessageHistoryCriteria.CriteriaException 상한 초과 시 / when the cap is exceeded
     */
    // source: biztalk_admin_20_spreadsheet_view.jsp (POI 3.9, screens 20/30)
    // req: FR-MSG-017, FR-TEN-001, NFR-OPS-AUDIT
    @Transactional(readOnly = true)
    public List<MessageHistoryRow> export(MessageHistoryCriteria requested,
                                          String requestedInstitution,
                                          String sourceIp) {
        TenantContext.TenantPrincipal principal = TenantContext.require();
        String effective = principal.effectiveInstitutionCode(requestedInstitution);
        MessageHistoryCriteria scoped = rebuildWithInstitution(requested, effective);

        try {
            int total = mapper.count(scoped);
            if (total > EXPORT_MAX_ROWS) {
                // 거절도 감사한다 — 대량 반출 시도 자체가 기록되어야 하는 사건이다.
                // The refusal is audited too: the attempt is itself a recordable event.
                audit.recordAuth(principal.email(), AuditEvent.ACTION_MESSAGE_HISTORY_EXPORT,
                        AuditEvent.Outcome.DENIED,
                        describe(scoped, total) + " reason=over-limit", sourceIp, null);
                throw new MessageHistoryCriteria.CriteriaException(List.of(
                        "내보낼 수 있는 최대 건수(" + EXPORT_MAX_ROWS + "건)를 초과했습니다. "
                                + "조회 결과는 " + total + "건입니다. 기간을 좁혀 주십시오."));
            }

            List<MessageHistoryRow> rows = total == 0
                    ? List.of()
                    : mapper.export(scoped, EXPORT_MAX_ROWS);

            audit.recordAuth(principal.email(), AuditEvent.ACTION_MESSAGE_HISTORY_EXPORT,
                    AuditEvent.Outcome.OK, describe(scoped, total), sourceIp, null);
            return rows;
        } catch (MessageHistoryCriteria.CriteriaException e) {
            throw e;
        } catch (RuntimeException e) {
            audit.recordAuth(principal.email(), AuditEvent.ACTION_MESSAGE_HISTORY_EXPORT,
                    AuditEvent.Outcome.ERROR, "export-failed", sourceIp, null);
            throw e;
        }
    }

    /**
     * 이용기관 코드를 서버 도출값으로 교체한 조건을 만든다.
     * Rebuilds the criteria with the server-derived 이용기관 code.
     *
     * <p>조건 객체는 불변이므로 교체가 아니라 재생성한다. 가변 setter 를 두면 조회 계층에
     * 도달한 뒤에도 범위가 바뀔 수 있고, 그 가능성 자체가 격리의 약점이 된다.</p>
     * <p>The criteria is immutable, so it is rebuilt rather than mutated. A setter would let the
     * scope change after reaching the query layer, and that possibility is itself a weakness.</p>
     */
    // req: FR-TEN-001, NFR-SEC-TENANT
    private MessageHistoryCriteria rebuildWithInstitution(MessageHistoryCriteria source,
                                                          String institutionCode) {
        return MessageHistoryCriteria.builder()
                .from(source.from())
                .to(source.to())
                .institutionCode(institutionCode)
                .messageKey(source.messageKey())
                .senderNumber(source.senderNumber())
                .recipientNumber(source.recipientNumber())
                .statusCode(source.statusCode())
                .messageType(source.messageType())
                .tableType(source.tableType())
                .resultCode(source.resultCode())
                .page(source.page())
                .size(source.size())
                .build();
    }

    /**
     * 감사 기록용 조건 요약을 만든다. 전화번호 검색어는 해시한다.
     * Builds an audit description of the criteria, hashing phone search terms.
     *
     * <p>ADR-006 에 따라 전화번호를 그대로 기록하지 않는다. 기록하면 감사 저장소가 2차
     * PII 저장소가 되고, 보존 기간 5년 동안 그 상태가 유지된다.</p>
     * <p>Per ADR-006 phone numbers are not recorded verbatim: doing so would make the audit store
     * a secondary PII repository, and keep it that way for the five-year retention.</p>
     *
     * @param criteria 조회 조건 / the criteria
     * @param total    전체 건수 / the total count
     * @return 비민감 요약 / a non-sensitive description
     */
    // req: NFR-OPS-AUDIT-L02, ADR-006, NFR-SEC-PII
    private String describe(MessageHistoryCriteria criteria, int total) {
        StringBuilder sb = new StringBuilder();
        sb.append("from=").append(criteria.from())
          .append(" to=").append(criteria.to())
          .append(" inst=").append(criteria.institutionCode() == null ? "ALL" : criteria.institutionCode())
          .append(" rows=").append(total);
        if (criteria.messageKey() != null) {
            sb.append(" msgkey=").append(criteria.messageKey());
        }
        if (criteria.statusCode() != null) {
            sb.append(" status=").append(criteria.statusCode());
        }
        // 전화번호 검색어는 해시만 남긴다 — 특정 번호로 조회했는지 사후 확인은 가능하되
        // 감사 기록에서 번호를 읽어낼 수는 없다.
        // Only a hash is kept: an investigator can confirm a suspected number was searched but
        // cannot read numbers out of the audit trail.
        if (criteria.senderNumber() != null) {
            sb.append(" senderHash=").append(shortHash(criteria.senderNumber()));
        }
        if (criteria.recipientNumber() != null) {
            sb.append(" recipientHash=").append(shortHash(criteria.recipientNumber()));
        }
        return sb.toString();
    }

    private String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 은 모든 JVM 에 존재한다. 그래도 감사 기록이 실패해서는 안 되므로
            // 평문 대신 고정 문자열을 남긴다.
            // SHA-256 exists on every JVM; even so, auditing must not fail, so a fixed marker is
            // recorded instead of the plaintext.
            return "unavailable";
        }
    }
}
