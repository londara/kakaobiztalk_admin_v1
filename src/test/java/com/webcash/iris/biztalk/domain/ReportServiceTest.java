package com.webcash.iris.biztalk.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.webcash.iris.biztalk.infra.db.ApiAggregateMapper;
import com.webcash.iris.biztalk.infra.db.bulk.BulkAggregateMapper;
import com.webcash.iris.common.audit.AuditEvent;
import com.webcash.iris.common.audit.AuditService;
import com.webcash.iris.common.tenant.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * {@link ReportService} 검증. / Verification for {@link ReportService}.
 *
 * <p>DB 없이 실행된다 — 두 매퍼 모두 대역이다. Docker 사용이 금지되어(RISK-S13, RISK-R01)
 * 실제 SQL 실행은 여기서 검증되지 않는다. 여기서 검증하는 것은 <b>범위 결정·병합 조립·감사·
 * 부분 결과 표시</b>이며, SQL 자체는 TEST-PLAN §2 의 티어 2 에서 다룬다.</p>
 * <p>Runs without a database — both mappers are doubles. With Docker prohibited (RISK-S13,
 * RISK-R01) real SQL execution is not verified here; what is verified is <b>scoping, merge
 * assembly, auditing and partial-result reporting</b>, with the SQL itself covered at tier 2 of
 * TEST-PLAN §2.</p>
 *
 * // req: FR-AZ-R03, FR-AZ-R05, FR-RPT-005, FR-RPT-013, FR-RPTS-003, FR-RPTS-005
 */
class ReportServiceTest {

    private static final Clock FIXED =
            Clock.fixed(Instant.parse("2026-08-18T09:00:00Z"), ZoneOffset.UTC);
    private static final String IP = "10.1.2.3";

    private ApiAggregateMapper apiMapper;
    private BulkAggregateMapper bulkMapper;
    private AuditService audit;

    @BeforeEach
    void setUp() {
        apiMapper = Mockito.mock(ApiAggregateMapper.class);
        bulkMapper = Mockito.mock(BulkAggregateMapper.class);
        audit = Mockito.mock(AuditService.class);
        given(apiMapper.findMaxTradeDate()).willReturn("20260814");
        given(bulkMapper.findMaxTradeDate()).willReturn("20260814");
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private ReportService withBulk() {
        return new ReportService(apiMapper, Optional.of(bulkMapper), audit, FIXED);
    }

    private ReportService withoutBulk() {
        return new ReportService(apiMapper, Optional.empty(), audit, FIXED);
    }

    private static void bindOperator() {
        TenantContext.set(new TenantContext.TenantPrincipal("op@example.com", null, true));
    }

    private static void bindTenant(String institution) {
        TenantContext.set(
                new TenantContext.TenantPrincipal("user@client.example", institution, false));
    }

    private static AggregateRow row(String tradeDate, String institution, String name, long n) {
        ChannelCounters counters = new ChannelCounters(n * 3, n * 2, n, 0);
        return new AggregateRow(tradeDate, institution, name,
                counters, counters, counters, counters, counters, counters, counters);
    }

    private ReportPage query(ReportService service) {
        return service.query(null, "ALL", "20260701", "20260731", null, null, 100, IP);
    }

    @Nested
    @DisplayName("범위와 인가 / scope and authorization")
    class Scope {

        @Test
        @DisplayName("컨텍스트가 없으면 조회를 거부한다")
        void refusesWithoutTenantContext() {
            assertThatThrownBy(() -> query(withBulk()))
                    .isInstanceOf(IllegalStateException.class);
        }

        /**
         * D-R2 회귀. 이용기관 주체가 기관을 비워 보내도 전체 조회가 되지 않는다.
         * D-R2 regression: a tenant principal sending no institution does not get 전체.
         */
        // req: FR-AZ-R03, T-R10
        @Test
        @DisplayName("이용기관 주체는 자기 기관으로 좁혀진다")
        void tenantIsNarrowed() {
            bindTenant("K0001");
            given(apiMapper.findPage(any())).willReturn(List.of());
            given(bulkMapper.findPage(any())).willReturn(List.of());
            given(apiMapper.findKeys(any(), anyInt())).willReturn(List.of());
            given(bulkMapper.findKeys(any(), anyInt())).willReturn(List.of());

            withBulk().query("K9999", "ALL", "20260701", "20260731", null, null, 100, IP);

            ArgumentCaptor<ReportCriteria> captor = ArgumentCaptor.forClass(ReportCriteria.class);
            verify(apiMapper).findPage(captor.capture());
            assertThat(captor.getValue().scope().institutionCode()).isEqualTo("K0001");
            assertThat(captor.getValue().scope().allInstitutions()).isFalse();
        }

        /**
         * 데이터는 새지 않았지만 시도는 기록한다 — 열거 행위는 성공한 조회보다 값진 증적이다.
         * Nothing leaked, but the attempt is recorded: probing is more valuable evidence than a
         * successful read.
         */
        // req: FR-AZ-R05
        @Test
        @DisplayName("범위 우회 시도를 감사에 남긴다")
        void auditsAnOverrideAttempt() {
            bindTenant("K0001");
            given(apiMapper.findPage(any())).willReturn(List.of());
            given(bulkMapper.findPage(any())).willReturn(List.of());
            given(apiMapper.findKeys(any(), anyInt())).willReturn(List.of());
            given(bulkMapper.findKeys(any(), anyInt())).willReturn(List.of());

            withBulk().query("K9999", "ALL", "20260701", "20260731", null, null, 100, IP);

            ArgumentCaptor<AuditEvent> events = ArgumentCaptor.forClass(AuditEvent.class);
            verify(audit, Mockito.atLeastOnce()).record(events.capture());
            assertThat(events.getAllValues())
                    .anyMatch(e -> AuditEvent.ACTION_TENANT_OVERRIDE_ATTEMPT.equals(e.action())
                            && e.outcome() == AuditEvent.Outcome.DENIED);
        }

        /**
         * D-R9 회귀: 기간 검증이 매퍼 호출보다 먼저 일어나야 한다. 순서가 뒤바뀌면 잘못된
         * 요청이 이미 데이터베이스를 때린 뒤에 거부된다.
         * D-R9 regression: validation must precede the mapper call, or a bad request reaches the
         * database before being refused.
         */
        // req: FR-RPT-002, T-R16
        @Test
        @DisplayName("기간이 잘못되면 매퍼를 호출하지 않는다")
        void invalidPeriodNeverReachesTheDatabase() {
            bindOperator();

            assertThatThrownBy(() -> withBulk()
                    .query(null, "ALL", "00000000", "99999999", null, null, 100, IP))
                    .isInstanceOf(PeriodPolicy.InvalidPeriodException.class);

            verify(apiMapper, never()).findPage(any());
            verify(bulkMapper, never()).findPage(any());
        }
    }

    @Nested
    @DisplayName("병합 / merging")
    class Merging {

        /** FR-RPTS-003: 같은 일자·기관은 한 행으로 합쳐진다. / shared keys collapse into one row. */
        // req: FR-RPTS-003
        @Test
        @DisplayName("두 출처의 같은 키를 한 행으로 합친다")
        void sumsSharedKeys() {
            bindOperator();
            given(apiMapper.findPage(any())).willReturn(List.of(row("20260701", "K0001", "기관A", 2)));
            given(bulkMapper.findPage(any())).willReturn(List.of(row("20260701", "K0001", null, 3)));
            given(apiMapper.findKeys(any(), anyInt()))
                    .willReturn(List.of(new AggregateKey("20260701", "K0001")));
            given(bulkMapper.findKeys(any(), anyInt()))
                    .willReturn(List.of(new AggregateKey("20260701", "K0001")));

            ReportPage page = query(withBulk());

            assertThat(page.rows()).hasSize(1);
            ReportRow only = page.rows().get(0);
            assertThat(only.source()).isEqualTo(SendSource.ALL);
            assertThat(only.counters().get(MessageChannel.ALIMTALK).total()).isEqualTo(6 + 9);
            assertThat(only.institutionName())
                    .describedAs("the resolved side supplies the name")
                    .isEqualTo("기관A");
        }

        /**
         * 전체 건수는 {@code count(A) + count(B)} 가 아니다 — 두 출처 모두에 있는 날짜가 두 번
         * 세어진다. 키 집합의 합집합을 센다.
         * The total is not {@code count(A) + count(B)}: a day in both sources would count twice.
         * The union of key sets is counted.
         */
        // req: FR-RPT-005, ADR-RPT-021
        @Test
        @DisplayName("전체 건수는 두 키 집합의 합집합이다")
        void totalIsTheUnionOfKeySets() {
            bindOperator();
            given(apiMapper.findPage(any())).willReturn(List.of());
            given(bulkMapper.findPage(any())).willReturn(List.of());
            given(apiMapper.findKeys(any(), anyInt())).willReturn(List.of(
                    new AggregateKey("20260702", "K0001"),
                    new AggregateKey("20260701", "K0001")));
            given(bulkMapper.findKeys(any(), anyInt())).willReturn(List.of(
                    new AggregateKey("20260701", "K0001"),
                    new AggregateKey("20260701", "K0002")));

            // 합집합 = {0702/K1, 0701/K1, 0701/K2} = 3. 단순 덧셈이면 4 가 된다.
            // The union is three keys; naive addition would report four.
            assertThat(query(withBulk()).totalCount()).isEqualTo(3L);
        }

        /**
         * D-R12 회귀: 대량 집계에는 기관 마스터가 없으므로 이름을 API 쪽에서 채운다.
         * 레거시도 같은 우회를 했지만 실패하면 조용히 빈칸이 됐다.
         * D-R12 regression: the bulk aggregate has no institution master, so names come from the
         * API side. The legacy did the same and failed silently to a blank cell.
         */
        // req: FR-RPT-012
        @Test
        @DisplayName("대량에만 있는 행의 기관명을 채운다")
        void fillsNamesForBulkOnlyRows() {
            bindOperator();
            given(apiMapper.findPage(any())).willReturn(List.of());
            given(bulkMapper.findPage(any())).willReturn(List.of(row("20260701", "K0007", null, 1)));
            given(apiMapper.findKeys(any(), anyInt())).willReturn(List.of());
            given(bulkMapper.findKeys(any(), anyInt()))
                    .willReturn(List.of(new AggregateKey("20260701", "K0007")));
            given(apiMapper.findInstitutionNames(any()))
                    .willReturn(List.of(new InstitutionName("K0007", "대량기관")));

            ReportPage page = query(withBulk());

            assertThat(page.rows()).hasSize(1);
            assertThat(page.rows().get(0).institutionName()).isEqualTo("대량기관");
            assertThat(page.rows().get(0).institutionUnresolved()).isFalse();
        }

        @Test
        @DisplayName("이름을 못 찾으면 미해결로 남긴다 — 빈칸으로 두지 않는다")
        void leavesUnresolvedRowsMarked() {
            bindOperator();
            given(apiMapper.findPage(any())).willReturn(List.of());
            given(bulkMapper.findPage(any())).willReturn(List.of(row("20260701", "K0099", null, 1)));
            given(apiMapper.findKeys(any(), anyInt())).willReturn(List.of());
            given(bulkMapper.findKeys(any(), anyInt()))
                    .willReturn(List.of(new AggregateKey("20260701", "K0099")));
            given(apiMapper.findInstitutionNames(any())).willReturn(List.of());

            assertThat(query(withBulk()).rows().get(0).institutionUnresolved()).isTrue();
        }
    }

    @Nested
    @DisplayName("부분 결과 / partial results")
    class PartialResults {

        /**
         * FR-RPTS-005. 대량 데이터소스가 설정되지 않은 환경에서 조용히 API 분만 돌려주면,
         * 사용자는 고객사 발송량을 실제보다 적게 본다. 그것이 이 프로그램이 네 슬라이스
         * 연속으로 만난 실패 방식이다.
         * FR-RPTS-005. Quietly returning API-only figures where the bulk datasource is
         * unconfigured under-reports a customer's volume — the failure mode this programme has
         * met in four consecutive slices.
         */
        // req: FR-RPTS-005, NFR-OPS-R01
        @Test
        @DisplayName("대량 데이터소스가 없으면 결과를 불완전으로 표시한다")
        void marksTheResultIncompleteWhenBulkIsUnconfigured() {
            bindOperator();
            given(apiMapper.findPage(any())).willReturn(List.of(row("20260701", "K0001", "기관A", 1)));
            given(apiMapper.findKeys(any(), anyInt()))
                    .willReturn(List.of(new AggregateKey("20260701", "K0001")));

            ReportPage page = query(withoutBulk());

            assertThat(page.availability().isIncomplete(SendSource.ALL)).isTrue();
            assertThat(page.availability().incompleteNotes(SendSource.ALL))
                    .anyMatch(note -> note.contains("대량발송"));
            assertThat(page.rows())
                    .describedAs("the API figures are still returned, but labelled")
                    .hasSize(1);
        }

        // req: FR-RPTS-005, NFR-OPS-R01
        @Test
        @DisplayName("대량 조회가 실패해도 API 결과를 돌려주고 결손을 알린다")
        void degradesWhenBulkThrows() {
            bindOperator();
            given(apiMapper.findPage(any())).willReturn(List.of(row("20260701", "K0001", "기관A", 1)));
            given(apiMapper.findKeys(any(), anyInt()))
                    .willReturn(List.of(new AggregateKey("20260701", "K0001")));
            given(bulkMapper.findPage(any()))
                    .willThrow(new IllegalStateException("bulk datasource unreachable"));

            ReportPage page = query(withBulk());

            assertThat(page.rows()).hasSize(1);
            assertThat(page.availability().bulkRead()).isFalse();
            assertThat(page.availability().isIncomplete(SendSource.ALL)).isTrue();
        }

        @Test
        @DisplayName("발송구분을 API 로 좁히면 대량 부재는 결손이 아니다")
        void narrowingTheFilterIsNotAGap() {
            bindOperator();
            given(apiMapper.findPage(any())).willReturn(List.of());
            given(apiMapper.findKeys(any(), anyInt())).willReturn(List.of());

            ReportPage page = withoutBulk()
                    .query(null, "API", "20260701", "20260731", null, null, 100, IP);

            assertThat(page.availability().isIncomplete(SendSource.API)).isFalse();
            assertThat(page.availability().incompleteNotes(SendSource.API)).isEmpty();
            verify(bulkMapper, never()).findPage(any());
        }
    }

    @Nested
    @DisplayName("기준일과 감사 / watermark and auditing")
    class WatermarkAndAudit {

        // req: FR-RPT-013
        @Test
        @DisplayName("집계 기준일을 함께 돌려준다")
        void returnsTheWatermark() {
            bindOperator();
            given(apiMapper.findPage(any())).willReturn(List.of());
            given(bulkMapper.findPage(any())).willReturn(List.of());
            given(apiMapper.findKeys(any(), anyInt())).willReturn(List.of());
            given(bulkMapper.findKeys(any(), anyInt())).willReturn(List.of());

            ReportPage page = query(withBulk());

            assertThat(page.watermark().apiAsOf()).isEqualTo(java.time.LocalDate.of(2026, 8, 14));
            assertThat(page.watermark().effectiveAsOf(SendSource.ALL))
                    .isEqualTo(java.time.LocalDate.of(2026, 8, 14));
        }

        /**
         * D-R17 회귀. 감사 기록에는 행위자·범위·기간·건수만 남고 <b>수치 자체는 남지 않는다</b>
         * — 감사 저장소는 보존 기간이 길고 접근 모델이 다르다(T-R15).
         * D-R17 regression. The record carries actor, scope, period and counts and <b>never the
         * figures</b>: the audit store has longer retention and different access (T-R15).
         */
        // req: FR-AZ-R05, T-R15
        @Test
        @DisplayName("조회를 감사에 남기되 수치는 남기지 않는다")
        void auditsTheReadWithoutTheFigures() {
            bindOperator();
            given(apiMapper.findPage(any()))
                    .willReturn(List.of(row("20260701", "K0001", "기관A", 12345)));
            given(bulkMapper.findPage(any())).willReturn(List.of());
            given(apiMapper.findKeys(any(), anyInt()))
                    .willReturn(List.of(new AggregateKey("20260701", "K0001")));
            given(bulkMapper.findKeys(any(), anyInt())).willReturn(List.of());

            query(withBulk());

            ArgumentCaptor<AuditEvent> events = ArgumentCaptor.forClass(AuditEvent.class);
            verify(audit, Mockito.atLeastOnce()).record(events.capture());

            AuditEvent read = events.getAllValues().stream()
                    .filter(e -> AuditEvent.ACTION_REPORT_QUERY.equals(e.action()))
                    .findFirst()
                    .orElseThrow();

            assertThat(read.actor()).isEqualTo("op@example.com");
            assertThat(read.targetAccount()).isEqualTo("ALL");
            assertThat(read.detail()).contains("20260701~20260731", "rows=1");
            assertThat(read.detail())
                    .describedAs("volumes must never be copied into the audit store")
                    .doesNotContain("12345", "37035");
        }

        // req: FR-AZ-R05, FR-RPTS-005
        @Test
        @DisplayName("부분 결과임을 감사에도 남긴다")
        void auditRecordsIncompleteness() {
            bindOperator();
            given(apiMapper.findPage(any())).willReturn(List.of());
            given(apiMapper.findKeys(any(), anyInt())).willReturn(List.of());

            query(withoutBulk());

            ArgumentCaptor<AuditEvent> events = ArgumentCaptor.forClass(AuditEvent.class);
            verify(audit, Mockito.atLeastOnce()).record(events.capture());
            assertThat(events.getAllValues())
                    .filteredOn(e -> AuditEvent.ACTION_REPORT_QUERY.equals(e.action()))
                    .anyMatch(e -> e.detail().contains("INCOMPLETE"));
        }
    }
}
