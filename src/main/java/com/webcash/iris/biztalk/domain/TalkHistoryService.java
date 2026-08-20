package com.webcash.iris.biztalk.domain;

import com.webcash.iris.biztalk.infra.db.TalkHistoryMapper;
import com.webcash.iris.common.audit.AuditEvent;
import com.webcash.iris.common.audit.AuditService;
import com.webcash.iris.common.tenant.PrincipalScope;
import com.webcash.iris.common.tenant.TenantContext;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 톡전송 거래내역 조회 서비스. / The 톡전송 transaction-history query service.
 *
 * <h2>이 클래스가 유일한 진입점인 이유 / why this is the single entry point</h2>
 * <p>정규화·검증·범위 결정이 <b>전부</b> 여기서 일어난다. 컨트롤러가 조금이라도 나눠 가지면
 * 내보내기 엔드포인트(스프린트 T2)가 생겼을 때 규칙이 갈라진다 — 레거시가 정확히 그렇게
 * 되었다. 다운로드 액션은 계약에 선언된 열 개의 파라미터를 전부
 * {@code request.getParameter} 로 직접 읽었고(D-T14), <b>화면과 다른 테이블</b>을 조회했으며
 * (D-T1), 그 결과 화면에 걸린 어떤 조건도 파일에 반영되지 않았다.</p>
 * <p>Normalisation, validation and scoping happen <b>entirely</b> here. Splitting any of it into the
 * controller lets the rules diverge once the export endpoint exists (Sprint T2) — which is exactly
 * what happened in the legacy. Its download action read all ten declared parameters straight from
 * {@code request.getParameter} (D-T14) and queried <b>different tables than the screen</b> (D-T1), so
 * no filter set on the screen reached the file.</p>
 *
 * <p>내보내기는 {@link #search(TalkQueryRequest, String)} 가 만드는 <b>같은
 * {@link TalkHistoryCriteria}</b> 를 소비한다. FR-TLKX-001 을 <b>집합 동일성</b>으로 검증
 * 가능하게 만드는 것이 그 공유다 — 둘이 어긋나려면 타입을 바꿔야 한다.</p>
 * <p>The export consumes the <b>same {@link TalkHistoryCriteria}</b> this method builds. That sharing
 * is what makes FR-TLKX-001 verifiable as a <b>set equality</b>: diverging would require changing the
 * type.</p>
 *
 * // source: biztalk_admin_30_l001_act.jsp, biztalk_admin_30.js — getDat()
 * // req: FR-AZ-T02, FR-AZ-T03, FR-AZ-T05, FR-TLK-001…015
 */
@Service
public class TalkHistoryService {

    private final TalkHistoryMapper mapper;
    private final BizTalkApiRegistry registry;
    private final AuditService audit;

    /**
     * 서비스를 생성한다. / Creates the service.
     *
     * @param mapper   거래내역 매퍼 / the transaction-history mapper
     * @param registry BizTalk API 레지스트리 / the BizTalk API registry
     * @param audit    감사 서비스 / the audit service
     */
    public TalkHistoryService(TalkHistoryMapper mapper,
                              BizTalkApiRegistry registry,
                              AuditService audit) {
        this.mapper = mapper;
        this.registry = registry;
        this.audit = audit;
    }

    /**
     * 거래내역을 조회한다. / Queries the transaction history.
     *
     * @param request  요청 값 / the request values
     * @param sourceIp 감사용 출처 주소 / the source address, for audit
     * @return 한 페이지와 전체 건수 / one page plus the total count
     */
    // req: FR-TLK-001, FR-TLK-005, FR-TLK-013, FR-AZ-T03, FR-AZ-T05
    @Transactional(readOnly = true)
    public PagedResult<TalkHistoryRow> search(TalkQueryRequest request, String sourceIp) {

        TenantContext.TenantPrincipal principal = TenantContext.require();
        TalkHistoryCriteria criteria = toCriteria(request, principal);

        try {
            int total = mapper.countAll(criteria);
            List<TalkHistoryMapper.TalkHistoryRowRecord> raw = mapper.findPage(criteria);

            // detailAvailable 은 여기서 붙는다 — 레지스트리가 결정하고 매퍼는 모른다.
            // 링크 표시와 상세 조회가 같은 레지스트리를 읽으므로 두 판단이 어긋날 수 없다(D-T13).
            // detailAvailable is attached here: the registry decides and the mapper does not know.
            // The link and the lookup read one registry, so they cannot disagree (D-T13).
            List<TalkHistoryRow> rows = raw.stream()
                    .map(r -> new TalkHistoryRow(
                            r.transactionDate(),
                            r.institutionCode(),
                            r.institutionName(),
                            r.transactionNo(),
                            r.apiServiceCode(),
                            r.statusCode(),
                            r.responseCode(),
                            r.registeredAt(),
                            r.completedAt(),
                            registry.detailAvailable(r.apiServiceCode())))
                    .toList();

            // 질의 뒤에 기록해 건수를 담는다. 실패 경로는 아래에서 따로 기록한다 — 실패한
            // 조회가 자기 자신의 감사 흔적을 남기지 않는 것은 허용되지 않는다.
            // Audited after the query so the row count is included; the failure path is audited
            // separately — a failed query leaving no trace of itself is not acceptable.
            audit.recordAuth(principal.email(), AuditEvent.ACTION_TALK_HISTORY_QUERY,
                    AuditEvent.Outcome.OK,
                    criteria.describe() + " rows=" + rows.size() + " total=" + total,
                    sourceIp, null);

            return new PagedResult<>(rows, total, criteria.page(), criteria.size());

        } catch (RuntimeException e) {
            audit.recordAuth(principal.email(), AuditEvent.ACTION_TALK_HISTORY_QUERY,
                    AuditEvent.Outcome.ERROR, criteria.describe(), sourceIp, null);
            throw e;
        }
    }

    /**
     * API 선택기에 제시할 항목을 반환한다. / Returns the options for the API selector.
     *
     * <p>데이터베이스를 읽지 않는다 — 범위는 설정이 정하고, 표시명도 그 항목에 함께 있다.
     * 레거시 {@code KKB_OPENAPI_INFO_L002} 는 드롭다운 하나를 채우려고 API 당 21개 컬럼을
     * 반환했고, 그중에는 API 를 등록·수정한 운영자의 ID 와 이름이 있었다(D-T27).</p>
     * <p>Reads no database: the scope is configuration and the display names travel with it. The
     * legacy {@code KKB_OPENAPI_INFO_L002} returned 21 columns per API to fill one dropdown, among
     * them the ids and names of the operators who registered and edited each API (D-T27).</p>
     *
     * @return 코드와 표시명 / codes with display labels
     */
    // req: FR-TLK-002, FR-TLK-012
    public List<BizTalkApiRegistry.Option> apiServiceOptions() {
        return registry.options();
    }

    /**
     * 상태 필터의 선택지를 반환한다. / Returns the status filter's options.
     *
     * <p>컬럼 라벨과 <b>같은 출처</b>에서 나온다(FR-TLK-004). 레거시는 필터를 코드 테이블에서
     * 생성하고 컬럼 라벨을 자바스크립트에 하드코딩해, 코드가 하나 추가되면 같은 릴리스에서
     * 필터는 가능해지고 컬럼은 {@code 알수없음} 이 되었다(D-T29).</p>
     * <p>Comes from the <b>same source</b> as the column labels (FR-TLK-004). The legacy generated the
     * filter from a code table and hardcoded the column labels in JavaScript, so adding one code made
     * it filterable and rendered it as {@code 알수없음} in the same release (D-T29).</p>
     *
     * @return 코드와 라벨 / codes with labels
     */
    // req: FR-TLK-004
    public List<TalkStatus.FilterOption> statusOptions() {
        return TalkStatus.filterOptions();
    }

    /**
     * 요청 값을 검증된 조건으로 변환한다 — 내보내기가 목록과 같은 조건을 쓰게 하는 접점.
     * Converts request values into validated criteria: the seam that makes the export use the list's criteria.
     *
     * <p><b>이 메서드가 공개인 이유가 FR-TLKX-001 이다.</b> 내보내기 컨트롤러가 조건을 스스로
     * 조립하면 두 경로가 갈라질 수 있고, 레거시 다운로드가 정확히 그렇게 계약을 우회한 끝에
     * 화면과 다른 테이블을 조회하게 되었다(D-T14 → D-T1). 조건을 만드는 곳이 하나뿐이면 두
     * 경로가 같은 것을 볼 수밖에 없고, FR-TLKX-001 이 설명이 아니라 <b>집합 동일성</b>으로
     * 검증될 수 있다.</p>
     * <p><b>FR-TLKX-001 is why this is public.</b> If the export controller assembled its own criteria the two
     * paths could diverge, and that is exactly how the legacy download escaped its contract and ended up
     * querying a different table than the screen (D-T14 → D-T1). With one place building criteria both paths
     * must see the same thing, which is what lets FR-TLKX-001 be verified as a <b>set equality</b> rather than
     * as prose.</p>
     *
     * @param request 요청 값 / the request values
     * @return 검증된 조건 / the validated criteria
     */
    // req: FR-TLKX-001, FR-TLKX-002
    @Transactional(readOnly = true)
    public TalkHistoryCriteria criteriaFor(TalkQueryRequest request) {
        return toCriteria(request, TenantContext.require());
    }

    /**
     * 요청 값을 검증된 조건으로 변환한다. / Converts request values into validated criteria.
     *
     * <p>순서에 의미가 있다. 범위를 <b>먼저</b> 결정하므로, 이후 어떤 검증 실패도 기관 범위가
     * 확정된 뒤에 일어난다 — 감사 기록에 범위가 항상 담긴다.</p>
     * <p>The order matters: the scope is resolved <b>first</b>, so any later validation failure happens
     * with the institution scope already fixed and the audit record always carries it.</p>
     */
    // req: FR-TLK-007, FR-TLK-008, FR-TLK-009, FR-TLK-010, FR-TLK-014, FR-AZ-T03
    private TalkHistoryCriteria toCriteria(TalkQueryRequest request,
                                           TenantContext.TenantPrincipal principal) {

        PrincipalScope scope = PrincipalScope.resolve(principal, request.institution());

        TalkPeriodPolicy.TalkWindow window = TalkPeriodPolicy.validate(
                request.fromDate(), request.toDate(), request.fromTime(), request.toTime());

        TransactionSerial serial = TransactionSerial.parse(request.serial()).orElse(null);

        String status = blankToNull(request.status());
        if (status != null && TalkStatus.fromCode(status).isEmpty()) {
            // 미인식 상태 코드는 <b>거부하지 않고</b> 그대로 술어에 쓴다. 코드 테이블에
            // 값이 추가되면 화면은 그것을 필터할 수 있어야 하며, 거부하면 새 코드가 추가된
            // 날 화면이 멈춘다 — TalkStatus 는 라벨의 출처이지 허용 목록이 아니다.
            // An unrecognised status is used as a predicate rather than refused. If a value is added
            // to the code table the screen must be able to filter it; refusing would break the screen
            // the day a code is added. TalkStatus is the source of labels, not an allow-list.
            status = status.trim();
        }

        String requestedApi = blankToNull(request.apiService());
        if (requestedApi != null && !registry.contains(requestedApi)) {
            // 범위 밖의 API 를 지목하면 <b>무시</b>한다. 거부하면 오류 메시지가 "그 코드는
            // BizTalk 이다/아니다"를 알려주는 열거 창구가 된다 — PrincipalScope 가 기관
            // 코드에 대해 같은 이유로 같은 선택을 한다(TM-T10).
            // A named API outside the scope is <b>ignored</b>. Refusing would turn the error message
            // into an oracle for whether a code is BizTalk — the same choice PrincipalScope makes for
            // institution codes, for the same reason (TM-T10).
            requestedApi = null;
        }

        return new TalkHistoryCriteria(
                window,
                scope,
                serial,
                status,
                requestedApi,
                registry.codes(),
                Math.max(0, request.page()),
                TalkHistoryCriteria.normaliseSize(request.size()));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /**
     * 컨트롤러에서 넘어오는 원시 요청 값. / The raw request values arriving from the controller.
     *
     * <p><b>검증되지 않은 값의 유일한 형태</b>다. 이 타입 밖으로 나가는 것은 모두
     * {@link TalkHistoryCriteria} 이며, 매퍼는 후자만 받는다.</p>
     * <p>The <b>only</b> shape unvalidated values take. Everything leaving is a
     * {@link TalkHistoryCriteria}, and the mapper accepts only the latter.</p>
     *
     * @param institution 이용기관 코드. 운영자만 유효 / the institution code, honoured for operators only
     * @param fromDate    시작일자 {@code YYYYMMDD} / the start date
     * @param toDate      종료일자 {@code YYYYMMDD}. 비우면 하루 조회 / the end date; blank means one day
     * @param fromTime    시작시각 {@code HHMM} 또는 {@code HHMMSS} / the start time
     * @param toTime      종료시각 {@code HHMM} 또는 {@code HHMMSS} / the end time
     * @param serial      거래일련번호 / the transaction serial
     * @param status      상태 코드 {@code PRSU} / the status code
     * @param apiService  API 서비스 코드 {@code API_SVC_CD} / the API service code
     * @param page        0부터 시작하는 페이지 번호 / the zero-based page number
     * @param size        페이지 크기 / the page size
     */
    // req: FR-TLK-001, FR-TLK-014
    public record TalkQueryRequest(
            String institution,
            String fromDate,
            String toDate,
            String fromTime,
            String toTime,
            String serial,
            String status,
            String apiService,
            int page,
            Integer size
    ) {
    }
}
