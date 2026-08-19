package com.webcash.iris.biztalk.domain;

import com.webcash.iris.biztalk.infra.db.AggregateMapper;
import com.webcash.iris.biztalk.infra.db.ApiAggregateMapper;
import com.webcash.iris.biztalk.infra.db.bulk.BulkAggregateMapper;
import com.webcash.iris.common.audit.AuditEvent;
import com.webcash.iris.common.audit.AuditService;
import com.webcash.iris.common.logging.CorrelationId;
import com.webcash.iris.common.tenant.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이용기관 보고서 조회 서비스. / Institution usage report query service.
 *
 * <p>화면 20 의 조회를 담당한다. 레거시에 없던 것이 다섯 가지 있다 — 서버 측 범위 결정,
 * 조회 감사, 서버 페이징, 집계 기준일 표시, 그리고 <b>부분 결과를 부분이라고 말하는 것</b>.</p>
 * <p>Serves screen 20's query. Five things absent from the legacy live here: server-side scoping,
 * read auditing, server-side paging, the freshness watermark, and <b>saying so when a result is
 * partial</b>.</p>
 *
 * <h2>두 출처를 읽는다는 것 / what reading two sources means</h2>
 * <p>레거시는 대량 집계를 {@code TSTCL_DV=REAL} 일 때만 읽었다. 그 결과 <b>운영이 아닌 어떤
 * 환경도 운영과 같은 코드 경로를 실행하지 못했고</b>, 응답의 모양·정렬·엑셀 시트 수가 모두
 * 환경마다 달랐다(D-R4, D-R6, D-R7). 여기에는 환경 분기가 없다. 대량 데이터소스를 읽을 수
 * 없으면 그것은 <b>설정이 아니라 장애</b>로 취급되어 결과에 표시된다.</p>
 * <p>The legacy read the bulk aggregate only when {@code TSTCL_DV=REAL}, so <b>no non-production
 * environment ever ran production's code path</b> and the response shape, ordering and sheet
 * count all varied by environment (D-R4, D-R6, D-R7). There is no environment branch here: an
 * unreadable bulk source is treated as <b>a fault, not a setting</b>, and is reported.</p>
 *
 * // source: biztalk_admin_20_l001_act.jsp, biztalk_admin_20.js — getDat()
 * // req: FR-RPT-001, FR-RPT-005, FR-RPT-007, FR-RPT-012, FR-RPT-013, FR-AZ-R03, FR-AZ-R05,
 * //      FR-RPTS-001, FR-RPTS-003, FR-RPTS-005
 */
@Service
public class ReportService {

    private static final Logger LOG = LoggerFactory.getLogger(ReportService.class);

    /**
     * 전체 건수 산정을 위해 읽을 키의 상한.
     * The ceiling on keys read for the total count.
     *
     * <p>366일 × 기관 수가 이 값을 넘으면 정확한 전체 건수 대신 "더 있음"만 돌려준다. 상한을
     * 두지 않으면 건수 산정 자체가 이 설계가 없애려던 무한정 조회가 된다(ADR-RPT-021).</p>
     * <p>Above this, an exact total is replaced by a "more exists" flag. Without a ceiling the
     * counting probe would itself become the unbounded fetch this design removes (ADR-RPT-021).</p>
     */
    // req: FR-RPT-005, ADR-RPT-021
    static final int MAX_KEY_PROBE = 500_000;

    private final ApiAggregateMapper apiMapper;
    private final Optional<BulkAggregateMapper> bulkMapper;
    private final AuditService audit;
    private final Clock clock;

    /**
     * 서비스를 생성한다. / Creates the service.
     *
     * @param apiMapper  API 집계 매퍼 / the API aggregate mapper
     * @param bulkMapper 대량 집계 매퍼. 미설정 환경에서는 비어 있다 / the bulk mapper, empty when unconfigured
     * @param audit      감사 서비스 / the audit service
     * @param clock      시각 공급자 / the clock
     */
    public ReportService(ApiAggregateMapper apiMapper,
                         Optional<BulkAggregateMapper> bulkMapper,
                         AuditService audit,
                         Clock clock) {
        this.apiMapper = apiMapper;
        this.bulkMapper = bulkMapper;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * 보고서 한 페이지를 조회한다. / Reads one page of the report.
     *
     * @param requestedInstitution 요청에 담긴 기관코드 / the institution code from the request
     * @param rawSource            요청에 담긴 발송구분 / the send-source filter from the request
     * @param rawFrom              시작일자 {@code YYYYMMDD} / the start date
     * @param rawTo                종료일자 {@code YYYYMMDD} / the end date
     * @param seekTradeDate        이어보기 일자 / the seek date
     * @param seekInstitution      이어보기 기관코드 / the seek institution code
     * @param rawSize              페이지 크기 / the page size
     * @param sourceIp             출처 IP / the source address
     * @return 한 페이지 / one page
     */
    // req: FR-RPT-001, FR-RPT-005, FR-RPT-007, FR-AZ-R03, FR-AZ-R05
    @Transactional(readOnly = true)
    public ReportPage query(String requestedInstitution,
                            String rawSource,
                            String rawFrom,
                            String rawTo,
                            String seekTradeDate,
                            String seekInstitution,
                            Integer rawSize,
                            String sourceIp) {

        TenantContext.TenantPrincipal principal = TenantContext.require();

        // ── 범위 결정 / scope ──────────────────────────────────────────────────────
        // 세션과 역할이 정한다. 요청 값은 운영자에게만 의미가 있고, 이용기관 주체에게는
        // 무시된다. 레거시에서는 빈 IS_CD 가 곧 "전 기관"이었고, 인증 없는 서비스(D-R1)와
        // 합쳐져 한 번의 요청으로 모든 고객사의 발송량이 나가는 경로였다(D-R2, T-R10).
        // Decided by session and role. The requested value means something only for an operator
        // and is ignored for a tenant principal. In the legacy an empty IS_CD meant "every
        // institution", and combined with an unauthenticated service (D-R1) that was the path by
        // which one request exposed every customer's volumes (D-R2, T-R10).
        ReportScope scope = ReportScope.resolve(principal, requestedInstitution);
        if (scope.overrideAttempted()) {
            // 데이터는 새지 않았지만 시도는 남긴다 — 열거 행위는 성공한 조회보다 값진 증적이다.
            // Nothing leaked, but the attempt is recorded: probing is more valuable evidence
            // than a successful read.
            audit.record(new AuditEvent(
                    Instant.now(clock), principal.email(), null,
                    AuditEvent.ACTION_TENANT_OVERRIDE_ATTEMPT, AuditEvent.Outcome.DENIED,
                    "requested a 이용기관 outside the session's scope", sourceIp,
                    CorrelationId.current()));
        }

        // ── 기간 검증 / period ─────────────────────────────────────────────────────
        // 서버에서만 강제된다. 레거시의 유일한 검사는 브라우저 한 줄이었고, 계약은 길이도
        // 타입도 선언하지 않았다(D-R9).
        // Enforced server-side only; the legacy's sole check was one line of browser JavaScript
        // and its contract declared neither length nor type (D-R9).
        PeriodPolicy.ReportPeriod period = PeriodPolicy.validate(rawFrom, rawTo);

        SendSource source = SendSource.parse(rawSource);
        AggregateKey seek = toSeek(seekTradeDate, seekInstitution);
        ReportCriteria criteria = ReportCriteria.of(scope, period, source, seek, rawSize);

        // ── 두 출처 읽기 / read both sources ───────────────────────────────────────
        SourceAvailability availability = SourceAvailability.forFilter(source);

        List<AggregateRow> apiRows = List.of();
        if (source.readsApi()) {
            try {
                apiRows = apiMapper.findPage(criteria);
            } catch (RuntimeException e) {
                availability = availability.withApiFailure(shortReason(e));
                LOG.warn("API aggregate unavailable for report query; result marked incomplete", e);
            }
        }

        List<AggregateRow> bulkRows = List.of();
        if (source.readsBulk()) {
            if (bulkMapper.isEmpty()) {
                availability = availability.withBulkFailure("대량 집계 데이터소스가 설정되지 않았습니다");
            } else {
                try {
                    bulkRows = bulkMapper.get().findPage(criteria);
                } catch (RuntimeException e) {
                    availability = availability.withBulkFailure(shortReason(e));
                    LOG.warn("Bulk aggregate unavailable for report query; result marked incomplete", e);
                }
            }
        }

        // ── 병합 / merge ──────────────────────────────────────────────────────────
        // 같은 일자·기관은 더해 한 행이 된다. 레거시는 Stream.concat 으로 이어 붙이기만 해
        // 한 기관이 하루에 두 행으로 나타났다(FR-RPTS-003).
        // Rows sharing a 일자 + 기관 are summed into one. The legacy used Stream.concat, so one
        // institution appeared twice for one day (FR-RPTS-003).
        List<ReportRow> rows = switch (source) {
            case API -> SourceMerger.single(apiRows, SendSource.API, criteria.size());
            case BULK -> SourceMerger.single(bulkRows, SendSource.BULK, criteria.size());
            case ALL -> SourceMerger.merge(apiRows, bulkRows, criteria.size());
        };

        boolean hasMore = apiRows.size() > criteria.size()
                || bulkRows.size() > criteria.size()
                || moreBeyondPage(rows, apiRows, bulkRows);

        rows = resolveInstitutionNames(rows);

        AggregateKey nextSeek = rows.isEmpty() || !hasMore
                ? null
                : rows.get(rows.size() - 1).key();

        Long totalCount = countTotal(criteria);
        ReportWatermark watermark = watermark(source);

        recordRead(principal, scope, criteria, rows.size(), totalCount, availability, sourceIp);

        return new ReportPage(rows, nextSeek, totalCount, hasMore, watermark, availability);
    }

    /**
     * 집계 기준일을 조회한다. / Reads the aggregation watermark.
     *
     * <p>화면이 처음 열릴 때도 필요하므로 공개되어 있다. 배치가 실행 이력을 남기지 않으므로
     * {@code max(TRDD)} 가 우리가 가진 전부다(ADR-RPT-022).</p>
     * <p>Public because the screen needs it on first load. The batch records no run history, so
     * {@code max(TRDD)} is all we have (ADR-RPT-022).</p>
     *
     * @param source 발송 구분 / the source filter
     * @return 기준일 / the watermark
     */
    // req: FR-RPT-013, ADR-RPT-022
    @Transactional(readOnly = true)
    public ReportWatermark watermark(SendSource source) {
        String apiMax = null;
        String bulkMax = null;
        if (source.readsApi()) {
            apiMax = safeMaxTradeDate(apiMapper, "API");
        }
        if (source.readsBulk() && bulkMapper.isPresent()) {
            bulkMax = safeMaxTradeDate(bulkMapper.get(), "BULK");
        }
        return ReportWatermark.of(apiMax, bulkMax);
    }

    private String safeMaxTradeDate(AggregateMapper mapper, String label) {
        try {
            return mapper.findMaxTradeDate();
        } catch (RuntimeException e) {
            // 기준일을 못 읽는다고 화면 전체를 실패시키지 않는다. 모른다고 말하는 편이 낫다.
            // A watermark we cannot read must not fail the whole screen; saying it is unknown is
            // better than refusing to open the report.
            LOG.warn("Could not read the {} watermark; reporting it as unknown", label, e);
            return null;
        }
    }

    /**
     * 전체 건수를 산정한다. / Computes the total row count.
     *
     * <p>{@code count(A) + count(B)} 는 틀린다 — 두 출처 모두에 있는 날짜가 두 번 세어진다.
     * 양쪽의 키 집합을 <b>합집합</b>하여 센다(ADR-RPT-021).</p>
     * <p>{@code count(A) + count(B)} is wrong: a day present in both sources counts twice. The
     * <b>union</b> of both key sets is counted instead (ADR-RPT-021).</p>
     */
    // req: FR-RPT-005, ADR-RPT-021
    private Long countTotal(ReportCriteria criteria) {
        try {
            Set<AggregateKey> keys = new LinkedHashSet<>();

            if (criteria.source().readsApi()) {
                keys.addAll(apiMapper.findKeys(criteria, MAX_KEY_PROBE + 1));
            }
            if (criteria.source().readsBulk() && bulkMapper.isPresent()) {
                keys.addAll(bulkMapper.get().findKeys(criteria, MAX_KEY_PROBE + 1));
            }

            if (keys.size() > MAX_KEY_PROBE) {
                // 정확한 값을 모르면서 아는 척하지 않는다. 화면은 "더 있음"만 표시한다.
                // Rather than assert a number we do not know, the screen shows "more exists".
                return null;
            }
            return (long) keys.size();
        } catch (RuntimeException e) {
            LOG.warn("Could not compute the report total; returning an unknown count", e);
            return null;
        }
    }

    /**
     * 기관명을 채운다. / Fills in institution names.
     *
     * <p>대량 집계 데이터베이스에는 기관 마스터가 없다(AMB-R04). 레거시도 Java 에서 이름을
     * 덧칠했지만, 조회 실패는 조용히 빈칸이 됐다(D-R12). 여기서는 채우지 못한 행이
     * {@link ReportRow#institutionUnresolved()} 로 드러난다.</p>
     * <p>The bulk database holds no institution master (AMB-R04). The legacy patched names in
     * Java too, but a miss became a blank cell (D-R12). Here an unfilled row is visible through
     * {@link ReportRow#institutionUnresolved()}.</p>
     */
    // req: FR-RPT-012
    private List<ReportRow> resolveInstitutionNames(List<ReportRow> rows) {
        Set<String> missing = new LinkedHashSet<>();
        for (ReportRow row : rows) {
            if (row.institutionUnresolved()) {
                missing.add(row.institutionCode());
            }
        }
        if (missing.isEmpty()) {
            return rows;
        }

        Map<String, String> resolved = new HashMap<>();
        try {
            for (InstitutionName name : apiMapper.findInstitutionNames(missing)) {
                if (name.name() != null && !name.name().isBlank()) {
                    resolved.put(name.code(), name.name());
                }
            }
        } catch (RuntimeException e) {
            // 이름을 못 채워도 숫자는 옳다. 미해결 표시로 화면에 나간다.
            // Failing to resolve names does not make the figures wrong; the rows go out marked
            // unresolved instead.
            LOG.warn("Could not resolve institution names; affected rows show the code only", e);
            return rows;
        }

        List<ReportRow> filled = new ArrayList<>(rows.size());
        for (ReportRow row : rows) {
            String name = row.institutionUnresolved() ? resolved.get(row.institutionCode()) : null;
            filled.add(name == null
                    ? row
                    : new ReportRow(row.source(), row.tradeDate(), row.institutionCode(),
                            name, row.counters()));
        }
        return filled;
    }

    /**
     * 조회 사건을 기록한다. / Records the read event.
     *
     * <p>행위자·범위·기간·건수만 남기고 <b>수치 자체는 남기지 않는다.</b> 감사 저장소는
     * 보존 기간이 길고 접근 모델이 다르므로, 거기에 발송량을 복제하면 노출을 줄이려 만든
     * 통제가 노출을 늘리게 된다(T-R15).</p>
     * <p>Records actor, scope, period and counts, and <b>never the figures themselves</b>. The
     * audit store has longer retention and a different access model, so copying volumes into it
     * would make a control meant to reduce exposure increase it (T-R15).</p>
     */
    // req: FR-AZ-R05, NFR-OPS-AUDIT-R01, T-R15
    private void recordRead(TenantContext.TenantPrincipal principal,
                            ReportScope scope,
                            ReportCriteria criteria,
                            int returnedRows,
                            Long totalCount,
                            SourceAvailability availability,
                            String sourceIp) {

        StringBuilder detail = new StringBuilder()
                .append(criteria.period().fromYyyymmdd()).append('~')
                .append(criteria.period().toYyyymmdd())
                .append(", source=").append(criteria.source())
                .append(", rows=").append(returnedRows)
                .append(", total=").append(totalCount == null ? "unknown" : totalCount);

        if (availability.isIncomplete(criteria.source())) {
            detail.append(", INCOMPLETE");
        }

        audit.record(new AuditEvent(
                Instant.now(clock),
                principal.email(),
                scope.describe(),
                AuditEvent.ACTION_REPORT_QUERY,
                AuditEvent.Outcome.OK,
                detail.toString(),
                sourceIp,
                CorrelationId.current()));
    }

    private static AggregateKey toSeek(String tradeDate, String institutionCode) {
        if (tradeDate == null || tradeDate.isBlank()
                || institutionCode == null || institutionCode.isBlank()) {
            return null;
        }
        return new AggregateKey(tradeDate.trim(), institutionCode.trim());
    }

    /**
     * 병합 후에도 소비하지 못한 행이 남았는지 확인한다.
     * Whether the merge left rows unconsumed.
     *
     * <p>두 출처를 합치면 병합 결과가 각 입력보다 짧을 수 있으므로(같은 키가 하나로 합쳐지므로),
     * 입력 크기만으로 다음 페이지 존재를 판단할 수 없다.</p>
     * <p>Because equal keys collapse into one row, the merged output can be shorter than either
     * input, so input sizes alone cannot decide whether a further page exists.</p>
     */
    // req: FR-RPT-005
    private static boolean moreBeyondPage(List<ReportRow> merged,
                                          List<AggregateRow> apiRows,
                                          List<AggregateRow> bulkRows) {
        int consumed = 0;
        for (ReportRow row : merged) {
            consumed += row.source() == SendSource.ALL ? 2 : 1;
        }
        return consumed < apiRows.size() + bulkRows.size();
    }

    private static String shortReason(RuntimeException e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return message.length() <= 120 ? message : message.substring(0, 117) + "...";
    }
}
