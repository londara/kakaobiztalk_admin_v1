package com.webcash.iris.biztalk.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.webcash.iris.biztalk.infra.db.TalkHistoryMapper;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 응답 계약 검증 — CONST-SEC-T01 과 TC-T001-12.
 * Response-contract verification: CONST-SEC-T01 and TC-T001-12.
 *
 * <h2>필드 집합이 보안 통제인 이유 / why the field set is a security control</h2>
 * <p>{@code FT_APITR_HSTR} 는 이 화면의 테이블이 아니다 — <b>전체 핀테크 API 의 거래 로그</b>이며
 * 25개 컬럼 중에는 {@code FIN_ACNO}, {@code ACNO}, {@code CANO}, {@code FIN_CARD}, {@code TRAM},
 * {@code BRNO}, {@code INTT_DMND_TTNO}, {@code RSPN_TLGR_CNTN} 이 있다 — 계좌번호, 카드번호,
 * 거래금액, 사업자번호, 응답 전문 원문이다. 이 화면은 그중 무엇과도 관계가 없다.</p>
 * <p>{@code FT_APITR_HSTR} is not this screen's table — it is <b>the transaction log for the entire
 * fintech API estate</b>, and among its 25 columns are account numbers, card numbers, amounts, business
 * numbers and raw response telegrams. This screen has business with none of them.</p>
 *
 * <p>CONST-SEC-T01 을 <b>리뷰가 아니라 빌드</b>가 강제하게 한다. 컬럼이 하나 추가되면 이 시험이
 * 실패하므로, 배제가 현재 SELECT 목록의 우연이 아니라 구조가 된다.</p>
 * <p>Makes <b>the build</b> enforce CONST-SEC-T01 rather than review: adding one column fails this test,
 * so the exclusion is structural rather than an accident of the current SELECT list.</p>
 *
 * <h2>PII 이름 검사가 여기 있는 이유 / why the PII-name check lives here</h2>
 * <p>화면 30 의 프로젝션에는 수신번호도 발신번호도 없다. 그러나 스프린트 T2 가 화면 32·31 의
 * 상세를 추가하면서 <b>같은 API 표면에</b> 마스킹 대상 필드를 들여온다. 지금 이 검사를 두면,
 * 그때 마스킹되지 않은 필드가 <b>실수로</b> 목록 응답에 섞이는 일이 빌드에서 걸린다 —
 * TC-T001-14 의 구조적 절반이다.</p>
 * <p>Screen 30's projection carries neither recipient nor sender number. But Sprint T2 adds the screen
 * 32/31 detail to <b>the same API surface</b>, bringing maskable fields with it. Having this check in
 * place now means an unmasked field <b>accidentally</b> joining the list response is caught by the build
 * — the structural half of TC-T001-14.</p>
 *
 * // source: IDO.FT_APITR_HSTR_C001 — the insert's 25-column list
 * // req: FR-TLK-003, FR-TLK-012, CONST-SEC-T01, NFR-SEC-PII-T01
 */
class TalkHistoryContractTest {

    /**
     * 화면이 바인딩하는 정확한 필드 이름. / The exact field names the screen binds.
     *
     * <p>레거시 그리드 {@code colDefs} 와 1:1 이다. 순서까지 고정하는 이유는 컬럼 추가가
     * <b>어디에 끼어들든</b> 실패하게 만들기 위해서다.</p>
     * <p>One-to-one with the legacy grid's {@code colDefs}. The order is fixed too, so an added column
     * fails <b>wherever</b> it is inserted.</p>
     */
    // source: biztalk_admin_30.js — drawGrid(): 일자,기관코드,기관명,거래고유번호,API,상태,응답코드,등록시각,완료시각
    private static final List<String> EXPECTED_ROW_FIELDS = List.of(
            "transactionDate",
            "institutionCode",
            "institutionName",
            "transactionNo",
            "apiServiceCode",
            "statusCode",
            "statusLabel",
            "responseCode",
            "registeredAt",
            "completedAt",
            "detailAvailable");

    /**
     * 이 API 표면에 나타나서는 안 되는 필드 이름 조각.
     * Field-name fragments that must not appear on this API surface.
     *
     * <p>이름으로 검사하는 것은 완벽하지 않다 — 다르게 이름 붙인 필드는 통과한다. 그러나
     * <b>실수로</b> 들어오는 필드는 거의 항상 원래 이름을 그대로 쓴다. 이 검사가 잡는 것은
     * 악의가 아니라 부주의이며, 이 슬라이스의 결함 서른넷 중 열한 개가 부주의였다.</p>
     * <p>Checking by name is not airtight — a differently-named field passes. But a field that arrives
     * <b>by accident</b> almost always keeps its original name. This catches carelessness rather than
     * malice, and eleven of this slice's thirty-four defects were carelessness.</p>
     */
    // req: CONST-SEC-T01, NFR-SEC-PII-T01
    private static final List<String> FORBIDDEN_FRAGMENTS = List.of(
            // 계좌·카드·금액·전문 — FT_APITR_HSTR 의 컬럼, 이 화면과 무관
            // account, card, amount, telegram — FT_APITR_HSTR columns, unrelated to this screen
            "acno", "cano", "card", "tram", "amount", "brno", "telegram", "tlgr",
            // 전화번호 — 화면 30 에는 없고, T2 에서 마스킹된 형태로만 들어온다
            // phone numbers — absent on screen 30, and only ever masked from T2 onward
            "phone", "callback", "recipient", "msisdn");

    private static List<String> componentNames(Class<?> record) {
        RecordComponent[] components = record.getRecordComponents();
        assertThat(components).as("%s must be a record", record.getSimpleName()).isNotNull();
        return Arrays.stream(components).map(c -> c.getName()).toList();
    }

    @Nested
    @DisplayName("응답 행 계약 / the response row contract")
    class ResponseRow {

        @Test
        @DisplayName("필드 집합과 순서가 정확히 일치한다 — TC-T001-12")
        void fieldSetIsExact() {
            assertThat(componentNames(TalkHistoryResponse.Row.class))
                    .as("컬럼을 추가하면 이 단언이 실패해야 한다 (CONST-SEC-T01) / "
                            + "adding a column must fail this assertion")
                    .containsExactlyElementsOf(EXPECTED_ROW_FIELDS);
        }

        @Test
        @DisplayName("페이지 봉투는 다섯 필드다")
        void pageEnvelopeIsFiveFields() {
            assertThat(componentNames(TalkHistoryResponse.class))
                    .containsExactly("rows", "totalCount", "page", "size", "totalPages");
        }

        @Test
        @DisplayName("금지된 이름이 응답 어디에도 없다 — CONST-SEC-T01")
        void noForbiddenFieldNames() {
            List<String> names = componentNames(TalkHistoryResponse.Row.class).stream()
                    .map(name -> name.toLowerCase(Locale.ROOT))
                    .toList();

            for (String fragment : FORBIDDEN_FRAGMENTS) {
                assertThat(names)
                        .as("'%s' 를 포함하는 필드가 응답에 있어서는 안 된다 / "
                                + "no response field may contain '%s'", fragment, fragment)
                        .noneMatch(name -> name.contains(fragment));
            }
        }
    }

    @Nested
    @DisplayName("매퍼 행 계약 / the mapper row contract")
    class MapperRow {

        @Test
        @DisplayName("매퍼 행은 9개 컬럼이다 — detailAvailable 은 여기 없다")
        void mapperRowIsNineColumns() {
            // detailAvailable 이 매퍼 행에 없는 것이 의도다. 그 값은 레지스트리가 결정하며
            // 데이터베이스가 아는 것이 아니다 — SQL 이 계산하면 레지스트리와 어긋날 수 있는
            // 두 번째 구현이 된다(D-T13 이 정확히 그 형태다).
            // Its absence is deliberate: the value is the registry's decision, not something the database
            // knows. Computing it in SQL would create a second implementation able to disagree — exactly
            // D-T13's shape.
            assertThat(componentNames(TalkHistoryMapper.TalkHistoryRowRecord.class))
                    .hasSize(9)
                    .doesNotContain("detailAvailable");
        }

        @Test
        @DisplayName("금지된 이름이 매퍼 행에도 없다")
        void mapperRowHasNoForbiddenNames() {
            List<String> names = componentNames(TalkHistoryMapper.TalkHistoryRowRecord.class)
                    .stream().map(name -> name.toLowerCase(Locale.ROOT)).toList();

            for (String fragment : FORBIDDEN_FRAGMENTS) {
                assertThat(names).noneMatch(name -> name.contains(fragment));
            }
        }
    }

    @Nested
    @DisplayName("엔드포인트 표면 / the endpoint surface")
    class EndpointSurface {

        @Test
        @DisplayName("컨트롤러는 두 개의 읽기 엔드포인트만 노출한다 — FR-AZ-T06")
        void onlyTwoReadEndpoints() {
            // 레거시는 세 화면에 대해 아홉 개 서비스를 노출했고, 그중 하나
            // (biztalk_admin_30_l002)는 호출자가 없으면서 마스킹 없는 전화번호를 임의 기간에
            // 대해 반환했다(D-T3, CVSS 7.7). 아홉을 다섯으로 줄이는 것 자체가 통제이며,
            // 스프린트 T1 이 그중 둘을 만든다.
            //
            // The legacy exposed nine services for three screens, one of which
            // (biztalk_admin_30_l002) had no caller and returned unmasked phone numbers over an
            // arbitrary period (D-T3, CVSS 7.7). Reducing nine to five is itself a control, and
            // Sprint T1 builds two of the five.
            long endpoints = Arrays.stream(TalkHistoryController.class.getDeclaredMethods())
                    .filter(method -> method.isAnnotationPresent(
                            org.springframework.web.bind.annotation.GetMapping.class))
                    .count();

            assertThat(endpoints)
                    .as("스프린트 T1 은 목록과 필터 두 엔드포인트만 만든다 / "
                            + "Sprint T1 builds exactly the list and the filters")
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("쓰기 엔드포인트가 없다 — CONST-DATA-T01")
        void noWriteEndpoints() {
            assertThat(Arrays.stream(TalkHistoryController.class.getDeclaredMethods())
                    .anyMatch(method ->
                            method.isAnnotationPresent(
                                    org.springframework.web.bind.annotation.PostMapping.class)
                            || method.isAnnotationPresent(
                                    org.springframework.web.bind.annotation.PutMapping.class)
                            || method.isAnnotationPresent(
                                    org.springframework.web.bind.annotation.DeleteMapping.class)
                            || method.isAnnotationPresent(
                                    org.springframework.web.bind.annotation.PatchMapping.class)))
                    .as("이 슬라이스는 읽기 전용이다 / this slice is read-only")
                    .isFalse();
        }

        @Test
        @DisplayName("모든 엔드포인트가 운영자 권한을 요구한다 — FR-AZ-T02")
        void everyEndpointRequiresOperator() {
            // 레거시의 실패는 "인가를 잊었다"가 아니라 "한쪽 문만 잠갔다"였다. 애노테이션
            // 존재를 구조적으로 단언하면, T2 에서 문을 하나 더 만들 때 잠그는 것을 잊을 수 없다.
            // The legacy's failure was not "we forgot authorization" but "we locked one of two doors".
            // Asserting the annotation structurally means a door added in T2 cannot be left unlocked.
            assertThat(Arrays.stream(TalkHistoryController.class.getDeclaredMethods())
                    .filter(method -> method.isAnnotationPresent(
                            org.springframework.web.bind.annotation.GetMapping.class))
                    .allMatch(method -> {
                        var preAuthorize = method.getAnnotation(
                                org.springframework.security.access.prepost.PreAuthorize.class);
                        return preAuthorize != null && preAuthorize.value().contains("OPERATOR");
                    }))
                    .as("모든 엔드포인트에 @PreAuthorize(OPERATOR) 가 있어야 한다 / "
                            + "every endpoint must carry @PreAuthorize(OPERATOR)")
                    .isTrue();
        }
    }
}
