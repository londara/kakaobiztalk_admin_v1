package com.webcash.iris.biztalk.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link BizTalkApiRegistry} 검증 — D-T13 의 회귀 테스트.
 * Verification for {@link BizTalkApiRegistry}: the regression test for D-T13.
 *
 * <p>레거시는 "이 행에 상세가 있는가"를 두 곳에서 따로 판단했다. 그리드는
 * {@code API_SVC_CD.indexOf("KKO") != -1 && PRSU == 1} 로, 액션은 네 개의 정확한 코드로.
 * {@code ADV_KKO_AT_SEND2} 는 {@code "KKO"} 를 포함하므로 링크가 걸렸고 액션에는 분기가 없어
 * <b>팝업이 빈 그리드로 열렸다</b> — 오류도 메시지도 없이. 반대 방향으로는 처리중·오류 행에
 * 링크가 아예 없었다.</p>
 * <p>The legacy answered "does this row have detail?" in two places: the grid with
 * {@code API_SVC_CD.indexOf("KKO") != -1 && PRSU == 1}, the action with four exact codes.
 * {@code ADV_KKO_AT_SEND2} contains {@code "KKO"} so it was linked, and the action had no branch for it,
 * so <b>the popup opened on an empty grid</b> — no error, no message. In the other direction, 처리중 and
 * 오류 rows had no link at all.</p>
 *
 * // source: biztalk_admin_30.js — drawGrid() IS_TUNO renderer
 * // source: biztalk_admin_32_l001_act.jsp — four ADV_KKO_* branches, ADV_KKO_AT_SEND2 absent
 * // req: FR-TLK-002, FR-TLK-012, FR-TLK-013, FR-TLKD-005, ADR-TLK-024, ADR-TLK-026
 */
class BizTalkApiRegistryTest {

    @Nested
    @DisplayName("범위 / scope")
    class Scope {

        @Test
        @DisplayName("기본 항목은 소스에서 확인된 다섯 개다")
        void defaultsAreTheFiveSourceLiterals() {
            // 소스 전체 스캔 결과. ADV_COM_GET_STATUS 는 운영에 존재하지만 어떤 소스 파일에도
            // 없으므로 여기에 없다 — 그래서 목록이 설정이고, 대조 보고서가 필요하다(AMB-T03).
            // The full source scan. ADV_COM_GET_STATUS exists in production and in no source file, so it
            // is absent here — which is why the list is configuration and the reconciliation report is
            // needed (AMB-T03).
            assertThat(BizTalkApiRegistry.withDefaults().codes())
                    .containsExactly(
                            "ADV_KKO_AT_SEND",
                            "ADV_KKO_AT_SEND2",
                            "ADV_KKO_AT_SEND_M",
                            "ADV_KKO_FT_SEND",
                            "ADV_KKO_FT_SEND_M");
        }

        @Test
        @DisplayName("범위 밖 코드는 포함되지 않는다 — SCOPE-T01")
        void outOfScopeCodeIsNotContained() {
            // 운영 화면 캡처의 코드. 톡 발송 서비스가 아니며, PM 결정 SCOPE-T01 로 화면에서
            // 사라진다 — 의도된, 눈에 보이는 파리티 이탈이다.
            // The code from the production screenshot. Not a talk-send service, and it disappears from
            // the screen under SCOPE-T01 — a deliberate, visible deviation from parity.
            assertThat(BizTalkApiRegistry.withDefaults().contains("ADV_COM_GET_STATUS")).isFalse();
        }

        @Test
        @DisplayName("설정이 비면 기본값을 쓴다")
        void emptyConfigurationFallsBackToDefaults() {
            assertThat(new BizTalkApiRegistry(List.of()).codes())
                    .isEqualTo(BizTalkApiRegistry.withDefaults().codes());
            assertThat(new BizTalkApiRegistry(null).codes())
                    .isEqualTo(BizTalkApiRegistry.withDefaults().codes());
        }

        @Test
        @DisplayName("중복 코드는 거부된다 — 승자가 줄 순서에 달리면 안 된다")
        void duplicateCodeIsRefused() {
            // 같은 코드에 두 채널이 설정되면 어느 쪽이 이겼는지가 설정 파일의 줄 순서에
            // 달리게 된다. 그것은 D-T7 과 같은 "어느 쪽이 맞는지 모르는" 상태다.
            // Two channels for one code would make the winner depend on line order in a config file —
            // the same "which one is right?" state as D-T7.
            assertThatThrownBy(() -> new BizTalkApiRegistry(List.of(
                    new BizTalkApiRegistry.Entry("X", TalkChannel.ALIMTALK, "a"),
                    new BizTalkApiRegistry.Entry("X", TalkChannel.FRIENDTALK, "b"))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("중복");
        }
    }

    @Nested
    @DisplayName("채널 라우팅 / channel routing")
    class ChannelRouting {

        @Test
        @DisplayName("알림톡과 친구톡이 각자의 채널로 라우팅된다")
        void routesEachChannel() {
            BizTalkApiRegistry registry = BizTalkApiRegistry.withDefaults();

            assertThat(registry.channelOf("ADV_KKO_AT_SEND")).contains(TalkChannel.ALIMTALK);
            assertThat(registry.channelOf("ADV_KKO_AT_SEND_M")).contains(TalkChannel.ALIMTALK);
            assertThat(registry.channelOf("ADV_KKO_FT_SEND")).contains(TalkChannel.FRIENDTALK);
            assertThat(registry.channelOf("ADV_KKO_FT_SEND_M")).contains(TalkChannel.FRIENDTALK);
        }

        @Test
        @DisplayName("ADV_KKO_AT_SEND2 도 라우팅된다 — 레거시가 빠뜨린 코드")
        void theCodeTheLegacyOmittedIsRouted() {
            // ⚠ D-T13 의 정확한 형태. 레거시 그리드는 "KKO" 부분 일치로 링크를 걸었고 액션에는
            // 이 코드의 분기가 없었다 — 링크는 있고 서비스는 못 하니 팝업이 조용히 비었다.
            // The exact shape of D-T13. The legacy grid linked it on a "KKO" substring match while the
            // action had no branch for it: linked but unservable, so the popup was quietly empty.
            assertThat(BizTalkApiRegistry.withDefaults().channelOf("ADV_KKO_AT_SEND2"))
                    .as("레거시 상세 액션이 빠뜨린 코드도 라우팅되어야 한다 / "
                            + "the code the legacy detail action omitted must route")
                    .contains(TalkChannel.ALIMTALK);
        }

        @Test
        @DisplayName("채널이 없는 항목은 상세를 지원하지 않는다")
        void entryWithoutChannelHasNoDetail() {
            // 범위에는 있으나 메시지가 없는 호출(예: 상태 조회)을 표현한다. 이 상태가
            // 표현 가능하다는 것이 두 레지스트리를 하나로 합칠 수 있었던 이유다.
            // Expresses a call that is in scope but has no messages (a status poll). That this state is
            // representable is why the two registries could become one.
            BizTalkApiRegistry registry = new BizTalkApiRegistry(List.of(
                    new BizTalkApiRegistry.Entry("ADV_KKO_STATUS", null, "상태조회")));

            assertThat(registry.contains("ADV_KKO_STATUS")).isTrue();
            assertThat(registry.channelOf("ADV_KKO_STATUS")).isEmpty();
            assertThat(registry.detailAvailable("ADV_KKO_STATUS")).isFalse();
        }

        @Test
        @DisplayName("null 과 미등록 코드는 상세가 없다")
        void unknownAndNullHaveNoDetail() {
            BizTalkApiRegistry registry = BizTalkApiRegistry.withDefaults();

            assertThat(registry.channelOf(null)).isEmpty();
            assertThat(registry.channelOf("NOPE")).isEmpty();
            assertThat(registry.detailAvailable(null)).isFalse();
            assertThat(registry.contains(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("링크와 조회의 일치 / link matches lookup")
    class LinkMatchesLookup {

        @Test
        @DisplayName("detailAvailable 은 channelOf 의 존재와 정확히 같다 — TC-T001-13")
        void detailAvailableEqualsChannelPresence() {
            // TEST-PLAN-TALK TC-T001-13 의 핵심을 도메인 수준에서 고정한다: 링크 표시와 상세
            // 조회가 하나의 판단이므로 어긋날 수 없다. 사례별 테스트는 레거시에서도 네 개의
            // 매핑된 코드에 대해 통과했고 ADV_KKO_AT_SEND2 를 놓쳤다 — 그래서 집합 동일성이다.
            //
            // Pins TC-T001-13's core at the domain level: the link and the lookup are one decision and
            // cannot disagree. Case-by-case tests passed on the legacy for all four mapped codes and
            // missed ADV_KKO_AT_SEND2 — hence a set equality.
            BizTalkApiRegistry registry = new BizTalkApiRegistry(List.of(
                    new BizTalkApiRegistry.Entry("A", TalkChannel.ALIMTALK, "a"),
                    new BizTalkApiRegistry.Entry("B", TalkChannel.FRIENDTALK, "b"),
                    new BizTalkApiRegistry.Entry("C", null, "c")));

            for (String code : List.of("A", "B", "C", "UNKNOWN")) {
                assertThat(registry.detailAvailable(code))
                        .as("code=%s", code)
                        .isEqualTo(registry.channelOf(code).isPresent());
            }
        }

        @Test
        @DisplayName("처리 상태는 상세 가능 여부에 영향을 주지 않는다 — FR-TLK-013")
        void statusDoesNotAffectAvailability() {
            // 레거시는 PRSU == 1 인 행만 링크했다. 그 결과 처리중과 오류 행에 링크가 없었고,
            // 그것은 실패를 조사하는 운영자가 가장 필요로 하는 행들이다. 이 메서드는 상태를
            // 인자로 받지 않으므로 그 결합이 구조적으로 불가능하다.
            //
            // The legacy linked only PRSU == 1, so 처리중 and 오류 rows had no link — the rows an
            // operator investigating a failure most needs. This method takes no status argument, so the
            // coupling is structurally impossible.
            assertThat(BizTalkApiRegistry.class.getDeclaredMethods())
                    .filteredOn(m -> m.getName().equals("detailAvailable"))
                    .allSatisfy(m -> assertThat(m.getParameterCount()).isEqualTo(1));
        }
    }

    @Nested
    @DisplayName("선택기 항목 / selector options")
    class SelectorOptions {

        @Test
        @DisplayName("코드와 표시명 두 필드만 나간다 — D-T27")
        void optionsCarryTwoFieldsOnly() {
            // 레거시 KKB_OPENAPI_INFO_L002 는 드롭다운 하나를 채우려고 API 당 21개 컬럼을
            // 반환했고, 그중에는 API 를 등록·수정한 운영자의 ID 와 이름이 있었다.
            // The legacy KKB_OPENAPI_INFO_L002 returned 21 columns per API to fill one dropdown, among
            // them the ids and names of the operators who registered and edited it.
            assertThat(BizTalkApiRegistry.Option.class.getRecordComponents())
                    .as("선택기 항목은 두 필드여야 한다 / a selector option must carry two fields")
                    .hasSize(2);

            assertThat(BizTalkApiRegistry.withDefaults().options())
                    .hasSize(5)
                    .allSatisfy(option -> {
                        assertThat(option.code()).isNotBlank();
                        assertThat(option.label()).isNotBlank();
                    });
        }
    }
}
