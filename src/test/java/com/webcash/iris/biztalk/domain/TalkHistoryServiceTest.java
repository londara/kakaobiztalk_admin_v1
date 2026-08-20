package com.webcash.iris.biztalk.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.webcash.iris.biztalk.infra.db.TalkHistoryMapper;
import com.webcash.iris.common.audit.AuditEvent;
import com.webcash.iris.common.audit.AuditService;
import com.webcash.iris.common.tenant.TenantContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * {@link TalkHistoryService} 검증 — 조건 조립·범위·감사의 회귀 테스트.
 * Verification for {@link TalkHistoryService}: criteria assembly, scoping and audit.
 *
 * <h2>데이터베이스가 필요하지 않은 이유 / why no database is needed</h2>
 * <p>이 클래스가 검증하는 것은 <b>서비스가 매퍼에게 무엇을 넘기는가</b>이며, 매퍼가 그것으로
 * 무엇을 하는지는 여기서 다루지 않는다. 스프린트 T1 로그는 이 검증들을 "DB 티어에 막혀 있다"고
 * 기록했는데 <b>그것은 잘못된 판단이었다</b> — 매퍼를 모킹하면 조건 조립과 감사 기록은 전부
 * 검증 가능하다. 회고 A5 의 근거가 이 정정이다.</p>
 * <p>What this class verifies is <b>what the service hands the mapper</b>, not what the mapper does
 * with it. The Sprint T1 log recorded these as blocked on a DB tier and <b>that was wrong</b>: with the
 * mapper mocked, criteria assembly and audit are entirely verifiable. This correction is the basis of
 * retrospective action A5.</p>
 *
 * // req: FR-AZ-T03, FR-AZ-T05, FR-TLK-002, FR-TLK-010, FR-TLK-013, FR-TLK-014
 */
class TalkHistoryServiceTest {

    private TalkHistoryMapper mapper;
    private AuditService audit;
    private TalkHistoryService service;

    private static TalkHistoryMapper.TalkHistoryRowRecord record(String apiServiceCode) {
        return new TalkHistoryMapper.TalkHistoryRowRecord(
                "20260819", "K00011", "비즈플레이_법인카드", "00000026081900142813",
                apiServiceCode, "0", null, "20260819112504", "20260819112504");
    }

    private static TalkHistoryService.TalkQueryRequest request() {
        return new TalkHistoryService.TalkQueryRequest(
                null, "20260819", null, null, null, null, null, null, 0, null);
    }

    @BeforeEach
    void setUp() {
        mapper = Mockito.mock(TalkHistoryMapper.class);
        audit = Mockito.mock(AuditService.class);
        service = new TalkHistoryService(mapper, BizTalkApiRegistry.withDefaults(), audit);
        TenantContext.set(new TenantContext.TenantPrincipal("op@example.com", null, true));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("상세 가능 여부 부착 / attaching detail availability")
    class DetailAvailability {

        @Test
        @DisplayName("레지스트리가 채널을 아는 행에 상세를 표시한다 — D-T13")
        void mappedCodeGetsDetail() {
            given(mapper.countAll(any())).willReturn(1);
            given(mapper.findPage(any())).willReturn(List.of(record("ADV_KKO_AT_SEND")));

            PagedResult<TalkHistoryRow> result = service.search(request(), "127.0.0.1");

            assertThat(result.rows()).singleElement()
                    .satisfies(row -> assertThat(row.detailAvailable()).isTrue());
        }

        @Test
        @DisplayName("레거시가 빠뜨린 ADV_KKO_AT_SEND2 도 상세가 표시된다 — D-T13")
        void theCodeTheLegacyOmittedGetsDetail() {
            // ⚠ 레거시 그리드는 "KKO" 부분 일치로 링크를 걸었고 상세 액션에는 이 코드의 분기가
            // 없었다. 링크는 있고 서비스는 못 하니 팝업이 조용히 비었다.
            // The legacy grid linked it on a "KKO" substring match while the detail action had no branch:
            // linked but unservable, so the popup was quietly empty.
            given(mapper.countAll(any())).willReturn(1);
            given(mapper.findPage(any())).willReturn(List.of(record("ADV_KKO_AT_SEND2")));

            assertThat(service.search(request(), "127.0.0.1").rows())
                    .singleElement()
                    .satisfies(row -> assertThat(row.detailAvailable()).isTrue());
        }

        @Test
        @DisplayName("매퍼가 반환한 행에 상세 없는 코드가 섞여 있으면 그 행만 false 다")
        void unmappedCodeGetsNoDetail() {
            given(mapper.countAll(any())).willReturn(1);
            given(mapper.findPage(any())).willReturn(List.of(record("ADV_COM_GET_STATUS")));

            assertThat(service.search(request(), "127.0.0.1").rows())
                    .singleElement()
                    .satisfies(row -> assertThat(row.detailAvailable()).isFalse());
        }
    }

    @Nested
    @DisplayName("조건 조립 / criteria assembly")
    class CriteriaAssembly {

        private TalkHistoryCriteria captureCriteria() {
            ArgumentCaptor<TalkHistoryCriteria> captor =
                    ArgumentCaptor.forClass(TalkHistoryCriteria.class);
            verify(mapper).findPage(captor.capture());
            return captor.getValue();
        }

        @Test
        @DisplayName("범위 밖 API 를 지목하면 거부하지 않고 무시한다 — TM-T10")
        void outOfScopeApiIsIgnoredNotRejected() {
            // 거부하면 오류 메시지가 "그 코드는 BizTalk 이다/아니다"를 알려주는 열거 창구가
            // 된다. PrincipalScope 가 기관 코드에 대해 같은 이유로 같은 선택을 한다.
            // Refusing would turn the error into an oracle for whether a code is BizTalk. PrincipalScope
            // makes the same choice for institution codes, for the same reason.
            given(mapper.countAll(any())).willReturn(0);
            given(mapper.findPage(any())).willReturn(List.of());

            service.search(new TalkHistoryService.TalkQueryRequest(
                    null, "20260819", null, null, null, null, null,
                    "ADV_COM_GET_STATUS", 0, null), "127.0.0.1");

            assertThat(captureCriteria().apiServiceCode())
                    .as("범위 밖 코드는 술어에 실리지 않아야 한다 / must not become a predicate")
                    .isNull();
        }

        @Test
        @DisplayName("범위 안 API 는 그대로 술어가 된다 — FR-TLK-010")
        void inScopeApiBecomesAPredicate() {
            given(mapper.countAll(any())).willReturn(0);
            given(mapper.findPage(any())).willReturn(List.of());

            service.search(new TalkHistoryService.TalkQueryRequest(
                    null, "20260819", null, null, null, null, null,
                    "ADV_KKO_FT_SEND", 0, null), "127.0.0.1");

            assertThat(captureCriteria().apiServiceCode()).isEqualTo("ADV_KKO_FT_SEND");
        }

        @Test
        @DisplayName("범위 집합이 항상 술어에 실린다 — SCOPE-T01")
        void scopeSetIsAlwaysApplied() {
            given(mapper.countAll(any())).willReturn(0);
            given(mapper.findPage(any())).willReturn(List.of());

            service.search(request(), "127.0.0.1");

            assertThat(captureCriteria().inScopeApiCodes())
                    .containsExactlyElementsOf(BizTalkApiRegistry.withDefaults().codes());
        }

        @Test
        @DisplayName("미인식 상태 코드는 거부하지 않고 술어에 쓴다 — FR-TLK-004")
        void unrecognisedStatusIsStillFiltered() {
            // TalkStatus 는 라벨의 출처이지 허용 목록이 아니다. 코드 테이블에 값이 추가되면
            // 화면은 그것을 필터할 수 있어야 하며, 거부하면 새 코드가 추가된 날 화면이 멈춘다.
            // TalkStatus is the source of labels, not an allow-list. If a value is added to the code
            // table the screen must filter it; refusing would break the screen the day it is added.
            given(mapper.countAll(any())).willReturn(0);
            given(mapper.findPage(any())).willReturn(List.of());

            service.search(new TalkHistoryService.TalkQueryRequest(
                    null, "20260819", null, null, null, null, "7", null, 0, null), "127.0.0.1");

            assertThat(captureCriteria().statusCode()).isEqualTo("7");
        }

        @Test
        @DisplayName("거래일련번호는 저장 폭으로 정규화되어 넘어간다 — D-T25")
        void serialIsNormalisedForTheMapper() {
            given(mapper.countAll(any())).willReturn(0);
            given(mapper.findPage(any())).willReturn(List.of());

            service.search(new TalkHistoryService.TalkQueryRequest(
                    null, "20260819", null, null, null, "26081900142813", null, null, 0, null),
                    "127.0.0.1");

            assertThat(captureCriteria().serialForMapper()).isEqualTo("00000026081900142813");
        }

        @Test
        @DisplayName("페이지 크기는 상한으로 잘린다")
        void pageSizeIsCapped() {
            given(mapper.countAll(any())).willReturn(0);
            given(mapper.findPage(any())).willReturn(List.of());

            service.search(new TalkHistoryService.TalkQueryRequest(
                    null, "20260819", null, null, null, null, null, null, 0, 100_000),
                    "127.0.0.1");

            assertThat(captureCriteria().size()).isEqualTo(TalkHistoryCriteria.MAX_SIZE);
        }
    }

    @Nested
    @DisplayName("범위 결정 / scope resolution")
    class ScopeResolution {

        @Test
        @DisplayName("운영자가 기관을 비우면 전 기관 조회다")
        void operatorBlankMeansAll() {
            given(mapper.countAll(any())).willReturn(0);
            given(mapper.findPage(any())).willReturn(List.of());

            service.search(request(), "127.0.0.1");

            ArgumentCaptor<TalkHistoryCriteria> captor =
                    ArgumentCaptor.forClass(TalkHistoryCriteria.class);
            verify(mapper).findPage(captor.capture());
            assertThat(captor.getValue().scope().allInstitutions()).isTrue();
            assertThat(captor.getValue().scope().institutionCode()).isNull();
        }

        @Test
        @DisplayName("이용기관 주체는 요청 값과 무관하게 자기 기관으로 좁혀진다 — D-T2")
        void tenantIsNarrowedRegardlessOfRequest() {
            // ⚠ 레거시 질의에는 기관 술어가 <b>아예 없었다</b>. 이 화면은 PM 결정 CONFLICT-T01 로
            // 운영자 전용이지만, 범위 결정은 그 결정과 독립적으로 올바르게 동작해야 한다 —
            // 인가가 한 겹이면 그 한 겹이 실수될 때 남는 것이 없다.
            // The legacy query had <b>no institution predicate at all</b>. This screen is operator-only
            // under CONFLICT-T01, but scoping must be correct independently of that ruling: with one
            // layer of authorization, a mistake in that layer leaves nothing.
            TenantContext.set(new TenantContext.TenantPrincipal("u@client.example", "K00011", false));
            given(mapper.countAll(any())).willReturn(0);
            given(mapper.findPage(any())).willReturn(List.of());

            service.search(new TalkHistoryService.TalkQueryRequest(
                    "K99999", "20260819", null, null, null, null, null, null, 0, null),
                    "127.0.0.1");

            ArgumentCaptor<TalkHistoryCriteria> captor =
                    ArgumentCaptor.forClass(TalkHistoryCriteria.class);
            verify(mapper).findPage(captor.capture());
            assertThat(captor.getValue().scope().institutionCode()).isEqualTo("K00011");
            assertThat(captor.getValue().scope().allInstitutions()).isFalse();
            assertThat(captor.getValue().scope().overrideAttempted()).isTrue();
        }

        @Test
        @DisplayName("컨텍스트가 없으면 조회하지 않는다")
        void noContextMeansNoQuery() {
            // null 을 "테넌트 없음 = 전체 조회"로 해석할 여지를 주지 않는다.
            // No room to read "no tenant" as "query everything".
            TenantContext.clear();

            assertThatThrownBy(() -> service.search(request(), "127.0.0.1"))
                    .isInstanceOf(IllegalStateException.class);
            verify(mapper, never()).findPage(any());
        }
    }

    @Nested
    @DisplayName("감사 / audit")
    class Audit {

        @Test
        @DisplayName("조회 성공은 건수와 함께 기록된다 — FR-AZ-T05")
        void successIsAuditedWithRowCount() {
            given(mapper.countAll(any())).willReturn(250);
            given(mapper.findPage(any())).willReturn(List.of(record("ADV_KKO_AT_SEND")));

            service.search(request(), "10.0.0.9");

            ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
            verify(audit).recordAuth(eq("op@example.com"),
                    eq(AuditEvent.ACTION_TALK_HISTORY_QUERY),
                    eq(AuditEvent.Outcome.OK), detail.capture(), eq("10.0.0.9"), any());

            assertThat(detail.getValue())
                    .contains("rows=1")
                    .contains("total=250")
                    .contains("scope=ALL")
                    .contains("20260819000000~20260819235959");
        }

        @Test
        @DisplayName("무시된 기관 지정 시도도 기록된다 — 탐색 행위의 증적")
        void ignoredOverrideIsAudited() {
            TenantContext.set(new TenantContext.TenantPrincipal("u@client.example", "K00011", false));
            given(mapper.countAll(any())).willReturn(0);
            given(mapper.findPage(any())).willReturn(List.of());

            service.search(new TalkHistoryService.TalkQueryRequest(
                    "K99999", "20260819", null, null, null, null, null, null, 0, null),
                    "127.0.0.1");

            ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
            verify(audit).recordAuth(anyString(), anyString(), any(), detail.capture(),
                    anyString(), any());
            assertThat(detail.getValue()).contains("overrideAttempted=true");
        }

        @Test
        @DisplayName("조회 실패도 기록된다 — 실패한 조회가 자기 흔적을 지워서는 안 된다")
        void failureIsAudited() {
            given(mapper.countAll(any())).willThrow(new IllegalStateException("db down"));

            assertThatThrownBy(() -> service.search(request(), "127.0.0.1"))
                    .isInstanceOf(IllegalStateException.class);

            verify(audit).recordAuth(eq("op@example.com"),
                    eq(AuditEvent.ACTION_TALK_HISTORY_QUERY),
                    eq(AuditEvent.Outcome.ERROR), anyString(), eq("127.0.0.1"), any());
        }

        @Test
        @DisplayName("검증 실패는 매퍼에 닿지 않는다 — FR-TLK-014")
        void validationFailureNeverReachesTheMapper() {
            // 32일 요청. 레거시는 브라우저에서만 검사했으므로 서비스를 직접 호출하면 통과했다.
            // A 32-day request. The legacy checked in the browser only, so a direct call passed.
            assertThatThrownBy(() -> service.search(new TalkHistoryService.TalkQueryRequest(
                    null, "20260801", "20260901", null, null, null, null, null, 0, null),
                    "127.0.0.1"))
                    .isInstanceOf(PeriodPolicy.InvalidPeriodException.class);

            verify(mapper, never()).findPage(any());
            verify(mapper, never()).countAll(any());
        }
    }

    @Nested
    @DisplayName("선택기 / selectors")
    class Selectors {

        @Test
        @DisplayName("API 선택지는 데이터베이스를 읽지 않는다 — D-T27")
        void apiOptionsTouchNoDatabase() {
            assertThat(service.apiServiceOptions()).hasSize(5);
            Mockito.verifyNoInteractions(mapper);
        }

        @Test
        @DisplayName("상태 선택지는 컬럼 라벨과 같은 출처다 — D-T29")
        void statusOptionsShareOneSourceWithLabels() {
            assertThat(service.statusOptions())
                    .allSatisfy(option ->
                            assertThat(TalkStatus.labelOf(option.code())).isEqualTo(option.label()));
        }
    }
}
