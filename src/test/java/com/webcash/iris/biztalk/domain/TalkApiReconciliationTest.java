package com.webcash.iris.biztalk.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.webcash.iris.biztalk.infra.db.TalkHistoryMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * {@link TalkApiReconciliation} 검증 — TC-REG-03.
 * Verification for {@link TalkApiReconciliation}: TC-REG-03.
 *
 * <h2>이 대조가 존재하는 이유 / why the reconciliation exists</h2>
 * <p>PM 결정 SCOPE-T01 이 화면을 BizTalk API 로 좁혔다. 그 결정은 레거시에 없던 실패 양식을
 * 하나 만들고, <b>그 양식은 이전 것보다 조용하다</b>.</p>
 * <ul>
 *   <li><b>과대 포함</b>은 레거시의 동작이고 보인다 — 운영자가 자기 화면에 있을 이유가 없는
 *       행을 보고 말한다.</li>
 *   <li><b>과소 포함</b>은 보이지 않는다 — 실제 BizTalk 거래가 그냥 없고, 필터가 그것을
 *       제거했다는 표시가 어디에도 없다.</li>
 * </ul>
 * <p>PM ruling SCOPE-T01 narrowed the screen to BizTalk APIs, creating a failure mode the legacy did
 * not have — and <b>a quieter one than it replaced</b>. Over-inclusion is the legacy's behaviour and is
 * visible; under-inclusion is not: a real transaction is simply absent with nothing saying a filter
 * removed it.</p>
 *
 * <p>이 대조가 그 조용한 양식을 이전 것만큼 시끄럽게 만든다. 그것이 허용 목록을 쓸 수 있게 하는
 * 조건이며(ADR-TLK-024, RISK-T01), 따라서 <b>이 시험은 편의가 아니라 결정의 절반</b>이다.</p>
 * <p>The reconciliation makes the quiet mode as loud as the one it replaced. That is the condition under
 * which an allow-list is safe to use (ADR-TLK-024, RISK-T01), so <b>this test covers half the decision,
 * not a convenience</b>.</p>
 *
 * <p>데이터베이스가 필요하지 않다 — 매퍼를 모킹하면 대조 논리 전체가 검증 가능하다. 스프린트 T1
 * 로그는 이 시험을 "DB 티어에 막혀 있다"고 기록했고 그것은 잘못된 판단이었다(회고 A5).</p>
 * <p>No database is needed: with the mapper mocked the whole reconciliation is verifiable. The Sprint T1
 * log recorded this as blocked on a DB tier and that was wrong (retrospective A5).</p>
 *
 * // req: FR-TLK-002, ADR-TLK-024, RISK-T01
 */
class TalkApiReconciliationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 19);

    private TalkHistoryMapper mapper;
    private TalkApiReconciliation reconciliation;

    private static TalkHistoryMapper.ObservedApiService observed(String code, long count) {
        return new TalkHistoryMapper.ObservedApiService(code, count);
    }

    @BeforeEach
    void setUp() {
        mapper = Mockito.mock(TalkHistoryMapper.class);
        reconciliation = new TalkApiReconciliation(
                mapper,
                BizTalkApiRegistry.withDefaults(),
                Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    }

    @Nested
    @DisplayName("과소 포함 탐지 / detecting under-inclusion")
    class UnderInclusion {

        @Test
        @DisplayName("분류에 없는 관측 코드를 건수와 함께 보고한다 — TC-REG-03")
        void reportsUnclassifiedCodesWithCounts() {
            // ADV_COM_GET_STATUS 는 운영 화면 캡처에 있고 어떤 소스 파일에도 없다. 허용 목록만
            // 으로는 이 코드의 존재를 알 방법이 없으므로, 대조가 그것을 드러내는 유일한 장치다.
            // ADV_COM_GET_STATUS is in the production screenshot and in no source file. The allow-list
            // alone cannot know it exists, so the reconciliation is the only thing that surfaces it.
            given(mapper.findObservedApiServices(anyString(), anyString())).willReturn(List.of(
                    observed("ADV_KKO_AT_SEND", 1_200),
                    observed("ADV_COM_GET_STATUS", 8_431),
                    observed("ADV_KKO_NEW_THING", 12)));

            TalkApiReconciliation.Result result = reconciliation.reconcile();

            assertThat(result.unclassified())
                    .extracting(TalkApiReconciliation.Unclassified::apiServiceCode)
                    .containsExactly("ADV_COM_GET_STATUS", "ADV_KKO_NEW_THING");
            assertThat(result.unclassified())
                    .extracting(TalkApiReconciliation.Unclassified::transactionCount)
                    .containsExactly(8_431L, 12L);
            assertThat(result.clean()).isFalse();
        }

        @Test
        @DisplayName("건수가 함께 나와야 심각도를 판단할 수 있다")
        void countsMakeSeverityJudgeable() {
            // 12건 누락과 8천건 누락은 같은 발견이 아니다. 코드만 보고하면 우선순위를 정할 수
            // 없고, 대조 보고서는 읽히지 않는 목록이 된다.
            // Missing 12 transactions and missing 8,000 are not the same finding. Reporting codes alone
            // would leave no way to prioritise, and the report would become an unread list.
            given(mapper.findObservedApiServices(anyString(), anyString()))
                    .willReturn(List.of(observed("ADV_COM_GET_STATUS", 8_431)));

            assertThat(reconciliation.reconcile().unclassified())
                    .singleElement()
                    .satisfies(row -> assertThat(row.transactionCount()).isEqualTo(8_431L));
        }
    }

    @Nested
    @DisplayName("설정 오류 탐지 / detecting configuration error")
    class ConfiguredButUnseen {

        @Test
        @DisplayName("설정에는 있으나 거래에 나타나지 않은 코드를 보고한다")
        void reportsConfiguredButUnseen() {
            // 오타이거나 폐기된 코드다. 설정된 코드가 조용히 아무것도 매치하지 않는 상태를
            // 드러내는 것이 시동 시 존재 검증을 대체한다 — 그리고 API 마스터가 아니라 실제
            // 거래에 대해 검증하므로 더 강한 신호다(스프린트 T1 로그 §4).
            // Either a typo or a retired code. Surfacing a configured code that quietly matches nothing
            // is what replaces the startup existence check — and it is the stronger signal, because it
            // validates against transactions that happened rather than against the API master.
            given(mapper.findObservedApiServices(anyString(), anyString()))
                    .willReturn(List.of(observed("ADV_KKO_AT_SEND", 5)));

            TalkApiReconciliation.Result result = reconciliation.reconcile();

            assertThat(result.configuredButUnseen()).containsExactly(
                    "ADV_KKO_AT_SEND2", "ADV_KKO_AT_SEND_M",
                    "ADV_KKO_FT_SEND", "ADV_KKO_FT_SEND_M");
        }
    }

    @Nested
    @DisplayName("일치 / clean")
    class Clean {

        @Test
        @DisplayName("양방향 모두 비면 불일치 없음이다")
        void bothDirectionsEmptyIsClean() {
            given(mapper.findObservedApiServices(anyString(), anyString())).willReturn(
                    BizTalkApiRegistry.withDefaults().codes().stream()
                            .map(code -> observed(code, 1))
                            .toList());

            TalkApiReconciliation.Result result = reconciliation.reconcile();

            assertThat(result.unclassified()).isEmpty();
            assertThat(result.configuredButUnseen()).isEmpty();
            assertThat(result.clean()).isTrue();
        }
    }

    @Nested
    @DisplayName("구간 / the window")
    class Window {

        @Test
        @DisplayName("구간은 항상 유계다 — 이 테이블에 무한정 GROUP BY 는 허용되지 않는다")
        void windowIsAlwaysBounded() {
            // FT_APITR_HSTR 는 전체 핀테크 API 의 거래 로그이며 이 프로젝트의 것이 아니다
            // (RISK-T07). 경계 없는 집계는 다른 소비자에게 영향을 준다.
            // FT_APITR_HSTR is the whole estate's transaction log and does not belong to this project
            // (RISK-T07); an unbounded aggregate would affect other consumers.
            given(mapper.findObservedApiServices(anyString(), anyString())).willReturn(List.of());

            reconciliation.reconcile();

            ArgumentCaptor<String> from = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> to = ArgumentCaptor.forClass(String.class);
            Mockito.verify(mapper).findObservedApiServices(from.capture(), to.capture());

            assertThat(from.getValue()).isEqualTo("20260812");
            assertThat(to.getValue()).isEqualTo("20260819");
        }

        @Test
        @DisplayName("구간을 명시할 수 있다")
        void windowCanBeGiven() {
            given(mapper.findObservedApiServices(anyString(), anyString())).willReturn(List.of());

            reconciliation.reconcile(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

            Mockito.verify(mapper).findObservedApiServices("20260701", "20260731");
        }

        @Test
        @DisplayName("기본 구간은 7일이다")
        void defaultWindowIsSevenDays() {
            assertThat(TalkApiReconciliation.DEFAULT_WINDOW_DAYS).isEqualTo(7);
            assertThat(Instant.now(Clock.fixed(
                    TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)))
                    .isNotNull();
        }
    }
}
