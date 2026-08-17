package com.webcash.iris.biztalk.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.webcash.iris.biztalk.infra.db.SenderNumberMapper;
import com.webcash.iris.common.audit.AuditEvent;
import com.webcash.iris.common.audit.AuditService;
import com.webcash.iris.common.tenant.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * {@link SenderNumberService} 검증. / Verification for {@link SenderNumberService}.
 *
 * <p>DB 없이 실행된다 — 매퍼는 대역이다. Docker 사용이 금지되어 실제 SQL 실행은 검증할 수
 * 없으므로(RISK-S13), 여기서 검증하는 것은 <b>범위 결정·감사·페이징 조립 로직</b>이며 SQL
 * 자체가 아니다. 그 한계는 TEST-PLAN §2 에 명시되어 있다.</p>
 * <p>Runs without a database — the mapper is a double. With Docker prohibited, real SQL
 * execution cannot be verified (RISK-S13), so what is verified here is the <b>scoping, auditing
 * and paging assembly</b>, not the SQL. That limit is stated in TEST-PLAN §2.</p>
 *
 * // req: FR-SND-001, FR-SND-002, FR-AZ-D03, FR-SND-011
 */
class SenderNumberServiceTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-08-17T09:00:00Z"), ZoneOffset.UTC);
    private static final String IP = "10.1.2.3";

    private SenderNumberMapper mapper;
    private AuditService audit;
    private SenderNumberService service;

    @BeforeEach
    void setUp() {
        mapper = Mockito.mock(SenderNumberMapper.class);
        audit = Mockito.mock(AuditService.class);
        service = new SenderNumberService(mapper, audit, FIXED);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private static void bindOperator() {
        TenantContext.set(new TenantContext.TenantPrincipal("op@example.com", null, true));
    }

    private static void bindClientUser(String ownCode) {
        TenantContext.set(new TenantContext.TenantPrincipal("user@example.com", ownCode, false));
    }

    private static SenderNumberEntity entity(String code, String number) {
        return new SenderNumberEntity(code, "○○기관", number, "김*수", "20260817090000", "김*수",
                "20260817090000", "대표번호");
    }

    @Nested
    @DisplayName("FR-AZ-D03 — 범위는 세션이 정한다 / the session decides the scope")
    class Scoping {

        @Test
        @DisplayName("이용기관 담당자의 요청 값은 무시된다 / a client user's requested code is ignored")
        // req: FR-AZ-D03
        void clientUserCannotChooseAnotherInstitution() {
            bindClientUser("K0MINE");
            given(mapper.count(any())).willReturn(1);
            given(mapper.findPage(any())).willReturn(List.of(entity("K0MINE", "0212345678")));

            service.list("K0THEIRS", 0, 20, IP);

            // 레거시는 요청 본문의 IS_CD 를 그대로 쿼리에 넣었다(D-S3).
            // The legacy placed the body's IS_CD straight into the query (D-S3).
            ArgumentCaptor<SenderNumberCriteria> captor = ArgumentCaptor.forClass(SenderNumberCriteria.class);
            verify(mapper).count(captor.capture());
            assertThat(captor.getValue().institutionCode()).isEqualTo("K0MINE");
        }

        @Test
        @DisplayName("범위를 벗어난 요청 시도는 감사된다 / an out-of-scope request is audited")
        // req: FR-AZ-D03, T-I1
        void overrideAttemptIsAudited() {
            bindClientUser("K0MINE");
            given(mapper.count(any())).willReturn(0);

            service.list("K0THEIRS", 0, 20, IP);

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(audit, Mockito.atLeastOnce()).record(captor.capture());

            assertThat(captor.getAllValues())
                    .as("probing must be visible after the fact")
                    .anyMatch(e -> AuditEvent.ACTION_TENANT_OVERRIDE_ATTEMPT.equals(e.action())
                            && e.outcome() == AuditEvent.Outcome.DENIED);
        }

        @Test
        @DisplayName("운영자는 기관을 지정할 수 있다 / an operator may name an institution")
        // req: FR-AZ-D03
        void operatorMayChoose() {
            bindOperator();
            given(mapper.count(any())).willReturn(1);
            given(mapper.findPage(any())).willReturn(List.of(entity("K0ABCD", "0212345678")));

            service.list("K0ABCD", 0, 20, IP);

            ArgumentCaptor<SenderNumberCriteria> captor = ArgumentCaptor.forClass(SenderNumberCriteria.class);
            verify(mapper).count(captor.capture());
            assertThat(captor.getValue().institutionCode()).isEqualTo("K0ABCD");
        }
    }

    @Nested
    @DisplayName("D-S19 — 기관 미선택 / no institution selected")
    class NoInstitution {

        @Test
        @DisplayName("기관을 고르지 않으면 조회하지 않는다 / no query runs when none is chosen")
        // req: FR-SND-002
        void doesNotQuery() {
            bindOperator();

            PagedResult<SenderNumberRow> result = service.list(null, 0, 20, IP);

            verify(mapper, never()).count(any());
            verify(mapper, never()).findPage(any());
            assertThat(result.rows()).isEmpty();
            assertThat(result.totalCount()).isZero();
        }

        @Test
        @DisplayName("빈 결과는 오류와 구분된다 / an empty result is distinguishable from an error")
        // req: FR-SND-002
        void emptyIsNotAnError() {
            bindOperator();
            assertThat(service.list(null, 0, 20, IP).isEmpty()).isTrue();
        }
    }

    @Nested
    @DisplayName("FR-SND-011 — 조회 감사 / read auditing")
    class ReadAudit {

        @Test
        @DisplayName("조회마다 감사 기록을 남긴다 / every read is recorded")
        // req: FR-SND-011, NFR-OPS-AUDIT-D01
        void recordsEveryRead() {
            bindOperator();
            given(mapper.count(any())).willReturn(1);
            given(mapper.findPage(any())).willReturn(List.of(entity("K0ABCD", "0212345678")));

            service.list("K0ABCD", 0, 20, IP);

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(audit, Mockito.atLeastOnce()).record(captor.capture());

            assertThat(captor.getAllValues())
                    .anyMatch(e -> AuditEvent.ACTION_SENDER_NUMBER_LIST.equals(e.action()));
        }

        @Test
        @DisplayName("감사 기록에 발신번호가 담기지 않는다 / no sender number reaches the audit store")
        // req: ADR-SND-019, T-I4
        void neverRecordsTheNumber() {
            // 이 단정이 ADR-SND-019 의 핵심이다. 마스킹을 걷어낸 대가로 조회를 기록하는데,
            // 그 기록에 번호를 담으면 보존 기간이 더 긴 2차 PII 저장소를 새로 만드는 셈이다.
            //
            // The essence of ADR-SND-019: reads are recorded to compensate for removing masking,
            // and putting the numbers into that record would create a second PII store with
            // longer retention — the control increasing the exposure it exists to reduce.
            bindOperator();
            String number = "01012345678";
            given(mapper.count(any())).willReturn(1);
            given(mapper.findPage(any())).willReturn(List.of(entity("K0ABCD", number)));

            service.list("K0ABCD", 0, 20, IP);

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(audit, Mockito.atLeastOnce()).record(captor.capture());

            for (AuditEvent event : captor.getAllValues()) {
                assertThat(String.valueOf(event.detail())).doesNotContain(number);
                assertThat(String.valueOf(event.targetAccount())).doesNotContain(number);
            }
        }
    }

    @Nested
    @DisplayName("페이징 조립 / paging assembly")
    class Paging {

        @Test
        @DisplayName("전체 건수가 0 이면 목록 쿼리를 실행하지 않는다 / skips the row query when the count is zero")
        // req: FR-SND-003
        void skipsRowQueryWhenEmpty() {
            bindOperator();
            given(mapper.count(any())).willReturn(0);

            PagedResult<SenderNumberRow> result = service.list("K0ABCD", 0, 20, IP);

            verify(mapper, never()).findPage(any());
            assertThat(result.totalCount()).isZero();
        }

        @Test
        @DisplayName("전체 건수를 함께 반환한다 / returns the total alongside the page")
        // req: FR-SND-003
        void returnsTotal() {
            bindOperator();
            given(mapper.count(any())).willReturn(27);
            given(mapper.findPage(any())).willReturn(List.of(entity("K0ABCD", "0212345678")));

            PagedResult<SenderNumberRow> result = service.list("K0ABCD", 0, 20, IP);

            assertThat(result.totalCount()).isEqualTo(27);
            assertThat(result.totalPages()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("응답 형태 / response shape")
    class ResponseShape {

        @Test
        @DisplayName("발신번호를 전체로 반환한다 / returns the number in full")
        // req: FR-SND-006
        void returnsNumberInFull() {
            bindOperator();
            given(mapper.count(any())).willReturn(1);
            given(mapper.findPage(any())).willReturn(List.of(entity("K0ABCD", "01012345678")));

            SenderNumberRow row = service.list("K0ABCD", 0, 20, IP).rows().get(0);

            assertThat(row.number()).isEqualTo("01012345678");
        }

        @Test
        @DisplayName("식별자는 표시되는 번호와 다르다 / the identifier differs from the displayed number")
        // req: FR-SND-007
        void refIsNotTheNumber() {
            bindOperator();
            given(mapper.count(any())).willReturn(1);
            given(mapper.findPage(any())).willReturn(List.of(entity("K0ABCD", "01012345678")));

            SenderNumberRow row = service.list("K0ABCD", 0, 20, IP).rows().get(0);

            assertThat(row.ref()).isNotEqualTo(row.number());
            assertThat(SenderNumberRef.fromToken(row.ref()).number()).isEqualTo("01012345678");
        }
    }
}
