package com.webcash.iris.biztalk.infra.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 집계 매퍼 XML 의 형태 검증 — 2026-08-19 결함 회귀.
 * Shape verification for the aggregate mapper XML — the 2026-08-19 defect's regression.
 *
 * <h2>이 테스트가 통합 테스트가 아닌 이유 / why this is not an integration test</h2>
 * <p>본래 이 검증은 실제 PostgreSQL 두 곳에 대한 통합 테스트여야 한다(TEST-PLAN-REPORT §2,
 * 티어 1~2). Docker 사용이 금지되어 있어(RISK-R01) 여기서는 <b>대체물</b>로 XML 자체를
 * 읽는다 — 동등물이 아니다. 컬럼이 실제로 존재하는지, 타입이 맞는지는 검증하지 못한다.</p>
 * <p>This should be an integration test against two real PostgreSQL schemas (TEST-PLAN-REPORT §2,
 * tiers 1–2). Docker is prohibited (RISK-R01), so the XML itself is read as a <b>substitute, not
 * an equivalent</b>: it cannot verify that the columns exist or that their types match.</p>
 *
 * <h2>그럼에도 가치가 있는 이유 / why it still earns its place</h2>
 * <p>실제로 발생한 결함이 정확히 <b>타입 별칭 이름</b>의 문제였기 때문이다. MyBatis 에서
 * {@code javaType="long"} 은 {@code java.lang.Long} 이고 원시형은 {@code _long} 이다.
 * {@link com.webcash.iris.biztalk.domain.ChannelCounters} 는 원시형 {@code long} 을 쓰는
 * 레코드이므로, 별칭 하나 때문에 생성자를 찾지 못해 조회 전체가 실패했다 — 질의는 데이터를
 * 정상적으로 가져온 뒤였다.</p>
 * <p>The defect that actually occurred was precisely a <b>type-alias naming</b> problem: in
 * MyBatis {@code javaType="long"} means {@code java.lang.Long} and the primitive is
 * {@code _long}. {@link com.webcash.iris.biztalk.domain.ChannelCounters} is a record over
 * primitive {@code long}, so one alias made the constructor unfindable and failed the whole
 * read — after the query had already returned its data correctly.</p>
 *
 * // req: FR-RPT-009, FR-RPT-011, FR-RPTS-004, ADR-RPT-021, RISK-R01
 */
class AggregateMapperXmlTest {

    private static final String API_XML = "mybatis/mapper/biztalk/ApiAggregateMapper.xml";
    private static final String BULK_XML = "mybatis/mapper/biztalk/bulk/BulkAggregateMapper.xml";

    private static String read(String resource) throws IOException {
        try (InputStream in = AggregateMapperXmlTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            assertThat(in).as("%s must be on the classpath", resource).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * XML 주석을 걷어낸 본문을 반환한다. / Returns the file with XML comments removed.
     *
     * <p>이 매퍼들은 <b>왜 그렇게 쓰지 않았는지</b>를 주석으로 길게 남긴다 — 예컨대 잘못된
     * 행 값 비교 형태를 그대로 인용해 둔다. 주석을 포함한 채로 "없어야 한다" 를 단언하면
     * 설명문에 걸려 <b>거짓 실패</b>가 난다. 실제로 이 테스트를 처음 실행했을 때 그랬다.</p>
     * <p>These mappers carry long comments about <b>what was not done and why</b>, quoting the
     * wrong row-value form verbatim. Asserting absence against the raw file trips over the
     * explanation and produces a <b>false failure</b> — as this test did on its first run.</p>
     */
    private static String readSql(String resource) throws IOException {
        return read(resource).replaceAll("(?s)<!--.*?-->", "");
    }

    @Nested
    @DisplayName("생성자 인자 타입 / constructor argument types")
    class ConstructorTypes {

        /**
         * 2026-08-19 회귀. {@code javaType="long"} 은 {@code java.lang.Long} 로 해석되어
         * 원시형 레코드 생성자를 찾지 못한다.
         * Regression: {@code javaType="long"} resolves to {@code java.lang.Long} and cannot find
         * the primitive record constructor.
         */
        @Test
        @DisplayName("건수 인자는 원시형 별칭 _long 을 쓴다")
        void counterArgsUseThePrimitiveAlias() throws IOException {
            for (String resource : new String[] {API_XML, BULK_XML}) {
                String xml = readSql(resource);

                assertThat(xml)
                        .as("%s must use the primitive alias for counter args", resource)
                        .contains("javaType=\"_long\"");

                assertThat(xml)
                        .as("%s must not use javaType=\"long\", which MyBatis reads as "
                                + "java.lang.Long and cannot bind to a primitive record", resource)
                        .doesNotContain("javaType=\"long\"");
            }
        }
    }

    @Nested
    @DisplayName("두 매퍼의 동형성 / the two mappers must stay identical in shape")
    class Symmetry {

        /**
         * 병합은 두 스트림의 형태와 정렬이 같을 때만 성립한다(ADR-RPT-021). 한쪽만 바뀌면
         * 오류가 아니라 <b>그럴듯한 틀린 합계</b>가 나온다.
         * The merge only works when both streams share a shape and an order (ADR-RPT-021);
         * changing one alone yields <b>plausible wrong sums</b>, not an error.
         */
        @Test
        @DisplayName("두 매퍼의 ORDER BY 가 같다")
        void bothMappersOrderIdentically() throws IOException {
            for (String resource : new String[] {API_XML, BULK_XML}) {
                assertThat(readSql(resource))
                        .as("%s must order by the merge's sort key", resource)
                        .contains("ORDER BY A.TRDD DESC, A.IS_CD ASC");
            }
        }

        @Test
        @DisplayName("두 매퍼가 같은 채널 접두어를 쓴다")
        void bothMappersUseTheSameColumnPrefixes() throws IOException {
            String api = readSql(API_XML);
            String bulk = readSql(BULK_XML);

            for (String prefix : new String[] {
                    "AT_", "FTTXT_", "FTIMG_", "FTIMGWI_", "SMS_", "LMS_", "MMS_"}) {
                assertThat(api).contains("columnPrefix=\"" + prefix + "\"");
                assertThat(bulk).contains("columnPrefix=\"" + prefix + "\"");
            }
        }

        /**
         * 정렬 방향이 섞여 있어 행 값 비교를 쓸 수 없다 — 스프린트 R1 에서 ADR-RPT-021 의
         * 명세를 고친 지점이다.
         * The mixed sort directions rule out row-value comparison — the point at which Sprint R1
         * corrected ADR-RPT-021's own specification.
         */
        @Test
        @DisplayName("이어보기 술어는 행 값 비교가 아니라 명시적 OR 이다")
        void seekPredicateIsExpandedNotRowValued() throws IOException {
            for (String resource : new String[] {API_XML, BULK_XML}) {
                String xml = readSql(resource);

                assertThat(xml)
                        .as("%s must expand the seek predicate", resource)
                        .contains("A.TRDD &lt; #{criteria.seek.tradeDate}")
                        .contains("A.IS_CD &gt; #{criteria.seek.institutionCode}");

                assertThat(xml)
                        .as("%s must not use row-value comparison, which is wrong for a "
                                + "mixed-direction sort", resource)
                        .doesNotContain("(TRDD, IS_CD) <");
            }
        }
    }

    @Nested
    @DisplayName("결함 수정의 흔적 / the corrections must stay applied")
    class Corrections {

        /** D-R11: COALESCE 없이는 NULL 하나가 행 전체를 무효화한다. */
        @Test
        @DisplayName("모든 건수 컬럼이 COALESCE 된다")
        void everyCounterIsCoalesced() throws IOException {
            for (String resource : new String[] {API_XML, BULK_XML}) {
                String xml = readSql(resource);
                assertThat(xml).contains("COALESCE(A.AT_PCSNG_CNT");
                assertThat(xml).contains("COALESCE(A.MMS_PCSNG_CNT");
            }
        }

        /** D-R8: LIMIT 이 없으면 전체 결과가 매 요청 전송된다. */
        @Test
        @DisplayName("페이지 질의에 LIMIT 이 있다")
        void pageQueryIsBounded() throws IOException {
            for (String resource : new String[] {API_XML, BULK_XML}) {
                assertThat(readSql(resource)).contains("LIMIT #{criteria.fetchSize}");
            }
        }

        /** D-R24: RGDT 와 FT_CNT 는 쓰이지 않으므로 반환하지 않는다. */
        @Test
        @DisplayName("사용하지 않는 컬럼을 반환하지 않는다")
        void unusedColumnsAreNotSelected() throws IOException {
            for (String resource : new String[] {API_XML, BULK_XML}) {
                String xml = readSql(resource);
                assertThat(xml).doesNotContain("AS RGDT");
                assertThat(xml).doesNotContain("AS FT_TOTAL");
            }
        }
    }
}
