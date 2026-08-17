package com.webcash.iris.biztalk.domain;

import com.webcash.iris.biztalk.infra.db.SenderNumberMapper;
import com.webcash.iris.common.audit.AuditEvent;
import com.webcash.iris.common.audit.AuditService;
import com.webcash.iris.common.logging.CorrelationId;
import com.webcash.iris.common.tenant.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 발신번호 조회 서비스. / Sender-number query service.
 *
 * <p>화면 10 의 목록을 담당한다. 레거시에 없던 세 가지가 여기에 있다 — 서버 측 테넌트
 * 범위 결정, 조회 감사, 그리고 기관 미선택 시 조회하지 않는 것.</p>
 * <p>Serves screen 10's list. Three things absent from the legacy live here: server-side tenant
 * scoping, read auditing, and not querying at all when no institution is chosen.</p>
 *
 * // source: biztalk_admin_10.js — getDat(), IDO.KKB_DPNO_LDGR_L002
 * // req: FR-SND-001, FR-SND-002, FR-SND-003, FR-AZ-D03, FR-SND-011
 */
@Service
public class SenderNumberService {

    private final SenderNumberMapper mapper;
    private final AuditService audit;
    private final Clock clock;

    /**
     * 서비스를 생성한다. / Creates the service.
     *
     * @param mapper 발신번호 매퍼 / the sender-number mapper
     * @param audit  감사 서비스 / the audit service
     * @param clock  시각 공급자 / the clock
     */
    public SenderNumberService(SenderNumberMapper mapper, AuditService audit, Clock clock) {
        this.mapper = mapper;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * 이용기관의 발신번호를 페이지 단위로 조회한다.
     * Returns one page of an institution's sender numbers.
     *
     * <p>조회 범위는 <b>요청이 아니라 세션</b>에서 결정된다. 레거시는 요청 본문의
     * {@code IS_CD} 를 그대로 쿼리에 넣어, 인증된 사용자라면 누구나 임의 기관의 발신번호를
     * 읽을 수 있었다(D-S3). 여기서는 {@link TenantContext} 가 세션 권한과 대조한 값만 쓴다.</p>
     * <p>The scope comes from <b>the session, not the request</b>. The legacy placed the body's
     * {@code IS_CD} straight into the query, letting any authenticated user read any
     * institution's numbers (D-S3). Only the value {@link TenantContext} has reconciled against
     * the session's rights is used.</p>
     *
     * @param requestedInstitutionCode 요청에 담긴 이용기관 코드 / the requested institution code
     * @param page                     페이지 번호 / the page index
     * @param size                     페이지 크기 / the page size
     * @param sourceIp                 출처 IP / the source address
     * @return 한 페이지 분량의 결과 / one page of results
     */
    // source: biztalk_admin_10_l001_act.jsp — idoIn1.putAll(input)
    // req: FR-SND-001, FR-SND-002, FR-SND-003, FR-AZ-D03, FR-SND-011
    @Transactional(readOnly = true)
    public PagedResult<SenderNumberRow> list(String requestedInstitutionCode,
                                             Integer page,
                                             Integer size,
                                             String sourceIp) {

        TenantContext.TenantPrincipal principal = TenantContext.require();
        String scoped = principal.effectiveInstitutionCode(requestedInstitutionCode);

        // 운영자가 기관을 고르지 않으면 전체(null)가 되지만, 이 화면은 기관 단위 화면이므로
        // 전체 조회라는 개념이 없다. 조회하지 않고 빈 결과를 돌려준다.
        // An operator who names no institution yields null (unrestricted), but this screen is
        // per-institution and has no "all institutions" mode, so nothing is queried.
        SenderNumberCriteria criteria = SenderNumberCriteria.of(scoped, page, size);
        if (!criteria.hasInstitution()) {
            recordRead(principal, requestedInstitutionCode, AuditEvent.Outcome.OK, "no institution selected", sourceIp);
            return new PagedResult<>(List.of(), 0, criteria.page(), criteria.size());
        }

        // 이용기관 담당자가 남의 기관을 지목했다면 effectiveInstitutionCode 가 자기 코드로
        // 바꿔치기하므로 데이터는 새지 않는다. 그러나 시도 자체는 기록한다 — 열거 행위는
        // 성공한 조회보다 값진 증적이다(T-I1).
        // A client-company user naming someone else's institution has it replaced with their own,
        // so nothing leaks. The attempt is still recorded: probing is more valuable evidence than
        // a successful read (T-I1).
        boolean overrideAttempted = requestedInstitutionCode != null
                && !requestedInstitutionCode.isBlank()
                && !requestedInstitutionCode.equals(scoped);
        if (overrideAttempted) {
            audit.record(new AuditEvent(
                    Instant.now(clock), principal.email(), null,
                    AuditEvent.ACTION_TENANT_OVERRIDE_ATTEMPT, AuditEvent.Outcome.DENIED,
                    "requested a 이용기관 outside the session's scope", sourceIp,
                    CorrelationId.current()));
        }

        int total = mapper.count(criteria);
        if (total == 0) {
            recordRead(principal, scoped, AuditEvent.Outcome.OK, "0 rows", sourceIp);
            return new PagedResult<>(List.of(), 0, criteria.page(), criteria.size());
        }

        List<SenderNumberRow> rows = mapper.findPage(criteria).stream()
                .map(SenderNumberRow::from)
                .toList();

        recordRead(principal, scoped, AuditEvent.Outcome.OK,
                rows.size() + " of " + total + " rows, page " + criteria.page(), sourceIp);

        return new PagedResult<>(rows, total, criteria.page(), criteria.size());
    }

    /**
     * 조회 사건을 기록한다. / Records the read event.
     *
     * <p>기관과 건수만 남기고 <b>발신번호는 남기지 않는다.</b> 번호를 감사 저장소에 쓰면
     * 그 저장소가 보존 기간이 더 길고 접근 모델이 다른 2차 PII 저장소가 된다 — 노출을 줄이려
     * 만든 통제가 노출을 늘리는 결과가 된다(T-I4, ADR-SND-019).</p>
     * <p>Records the institution and a count, and <b>never the numbers</b>. Writing them into the
     * audit store would make it a secondary PII repository with longer retention and a different
     * access model: a control meant to reduce exposure increasing it (T-I4, ADR-SND-019).</p>
     *
     * @param principal       행위자 / the acting principal
     * @param institutionCode 대상 이용기관 / the institution read
     * @param outcome         결과 / the outcome
     * @param detail          비민감 부가정보 / non-sensitive detail
     * @param sourceIp        출처 IP / the source address
     */
    // req: FR-SND-011, NFR-OPS-AUDIT-D01, ADR-SND-019
    private void recordRead(TenantContext.TenantPrincipal principal,
                            String institutionCode,
                            AuditEvent.Outcome outcome,
                            String detail,
                            String sourceIp) {
        audit.record(new AuditEvent(
                Instant.now(clock),
                principal.email(),
                institutionCode,
                AuditEvent.ACTION_SENDER_NUMBER_LIST,
                outcome,
                detail,
                sourceIp,
                // 애플리케이션 로그와 이 감사 기록을 잇는 값. 조사에서 "이 오류 로그가 저
                // 감사 기록과 같은 요청인가" 를 시각으로 추측하지 않아도 된다.
                // Ties the application log to this audit record, so an investigation need not
                // guess from timestamps whether an error line and a record share a request.
                CorrelationId.current()));
    }
}
