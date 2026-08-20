package com.webcash.iris.biztalk.alimtalk.infra.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link TemplateMapper} SQL 식별자 검증. / SQL identifier verification for {@link TemplateMapper}.
 *
 * <h2>이 테스트가 통합 테스트가 아닌 이유 / why this is not an integration test</h2>
 * <p>본래 실제 PostgreSQL 통합 테스트여야 한다(TEST-PLAN-ALIMTALK tier 3). Docker 가 금지되어
 * (RISK-A12) Testcontainers 를 기동할 수 없고, 도달 가능한 PostgreSQL 인스턴스가 있는지는
 * 발신번호 슬라이스에서 <b>미해결로 이월된</b> 질문이다(RISK-S13). 이 테스트는 그
 * <b>대체물이며 동등물이 아니다</b> — 테이블이 실재하는지, 컬럼 타입이 맞는지 증명하지 못한다.</p>
 * <p>This should be an integration test against a real PostgreSQL (TEST-PLAN-ALIMTALK tier 3). Docker is
 * prohibited (RISK-A12), and whether any PostgreSQL instance is reachable is a question <b>carried open</b>
 * from the 발신번호 slice (RISK-S13). This test is a <b>substitute, not an equivalent</b>: it cannot prove the
 * table exists or that column types match.</p>
 *
 * <p>그럼에도 값이 있는 이유는 이 프로그램이 이미 같은 종류의 결함을 겪었기 때문이다.
 * {@code InstitutionMapper} 는 서비스 계약의 필드명을 테이블 컬럼명으로 오인해 작성되었고,
 * 추측된 테이블명은 존재하지 않았다(RISK-I05). 이 슬라이스에서는 컬럼명이
 * {@code TEMPLATE_MSG}·{@code TEMPLATE_CODE}·{@code TEMPLATE_TITLE}·{@code IS_CD} 로 IDO SQL 에서
 * 직접 확인되므로, 그 확인을 고정한다.</p>
 * <p>It earns its place because this programme has already had this exact defect: {@code InstitutionMapper}
 * mistook service-contract field names for table columns against a guessed table that does not exist
 * (RISK-I05). Here the column names are confirmed directly from the IDO SQL, and this test pins that
 * confirmation.</p>
 *
 * // source: IDO.KKB_MSG_TMPL_L001, IDO.KKB_MSG_TMPL_L002, IDO.KKB_MSG_TMPL_L003
 * // req: FR-ATV-001, FR-ATT-001, FR-ATT-003, CONST-DATA-A03, RISK-A12
 */
class TemplateMapperSqlTest {

    private static String sqlOf(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = TemplateMapper.class.getMethod(methodName, parameterTypes);
        Select select = method.getAnnotation(Select.class);
        assertThat(select).as("%s must carry an @Select annotation", methodName).isNotNull();
        return String.join(" ", select.value());
    }

    private static String listSql() throws Exception {
        return sqlOf("findByInstitution", String.class);
    }

    private static String bodySql() throws Exception {
        return sqlOf("findTemplateBody", String.class, String.class);
    }

    @Nested
    @DisplayName("실제 식별자 / real identifiers")
    class Identifiers {

        @Test
        @DisplayName("실제 테이블 KKB_MSG_TMPL 을 조회한다 / queries the real table")
        // req: CONST-DATA-A03
        void queriesRealTable() throws Exception {
            assertThat(listSql()).contains("KKB_MSG_TMPL");
            assertThat(bodySql()).contains("KKB_MSG_TMPL");
        }

        @ParameterizedTest
        @ValueSource(strings = {"IS_CD", "TEMPLATE_CODE", "TEMPLATE_TITLE"})
        @DisplayName("목록 조회가 실제 컬럼명을 쓴다 / the list query uses real column names")
        // req: FR-ATT-001, CONST-DATA-A03
        void listUsesRealColumns(String column) throws Exception {
            assertThat(listSql()).contains(column);
        }

        @Test
        @DisplayName("본문 조회가 TEMPLATE_MSG 를 읽는다 — 슬라이스 방향을 바꾼 컬럼 / reads TEMPLATE_MSG")
        // req: FR-ATV-001
        void bodyQueryReadsTemplateMsg() throws Exception {
            // 이 컬럼의 존재가 D-A16 의 근거다. 레지스트리가 본문을 갖고 있으므로 운영자가
            // 손으로 붙여넣을 이유가 없고, 검증이 발송 경로로 옮겨갈 수 있다.
            // The existence of this column is the basis of D-A16: the registry holds the body, so an
            // operator need not paste it and validation can move into the send path.
            assertThat(bodySql()).contains("TEMPLATE_MSG");
        }
    }

    @Nested
    @DisplayName("FR-ATT-003 — 이용기관 범위 / institution scoping")
    class Scoping {

        @Test
        @DisplayName("모든 조회가 IS_CD 로 한정된다 / every query is bounded by IS_CD")
        // req: FR-ATT-003, FR-AZ-A02
        void everyQueryIsBoundedByInstitution() throws Exception {
            // KKB_MSG_TMPL_L003 은 운영자 범위 없이 활성 기관 전체의 템플릿을 한 번에 돌려준다.
            // 그 형태를 포팅하면 FR-ATT-003 위반이며, 다른 기관이 고객에게 무엇을 보내는지
            // 드러난다(T-A16).
            // KKB_MSG_TMPL_L003 returns every active institution's templates unscoped. Porting that shape
            // would violate FR-ATT-003 and reveal what other institutions send to their customers (T-A16).
            assertThat(listSql()).contains("IS_CD");
            assertThat(bodySql()).contains("IS_CD");
        }

        @Test
        @DisplayName("본문 조회는 코드만으로 하지 않는다 / the body is never looked up by code alone")
        // req: FR-ATT-004, FR-AZ-A02
        void bodyIsNeverLookedUpByCodeAlone() throws Exception {
            String sql = bodySql();

            // IS_CD 조건이 인가의 일부다. AND 로 두 조건이 함께 걸려 있어야 한다.
            // The IS_CD predicate is part of the authorization: both conditions must be ANDed.
            assertThat(sql).contains("IS_CD").contains("TEMPLATE_CODE").containsIgnoringCase("AND");
        }

        @Test
        @DisplayName("바인딩 파라미터만 쓴다 — 문자열 연결 없음 / bound parameters only")
        // req: NFR-SEC-INJ-A01
        void usesBoundParametersOnly() throws Exception {
            // ADR-003: 명명 바인딩. ${} 는 MyBatis 에서 문자열 치환이므로 주입 경로가 된다.
            // ADR-003 mandates named binding. ${} is string substitution in MyBatis and is an injection path.
            assertThat(listSql()).contains("#{institutionCode}").doesNotContain("${");
            assertThat(bodySql()).contains("#{institutionCode}").contains("#{templateCode}").doesNotContain("${");
        }
    }

    @Nested
    @DisplayName("결정적 순서 / deterministic order")
    class Ordering {

        @Test
        @DisplayName("목록에 ORDER BY 가 있다 / the list carries an ORDER BY")
        // req: FR-ATT-001
        void listHasOrderBy() throws Exception {
            // KKB_MSG_TMPL_L001 에는 ORDER BY 가 없었다. 순서가 요청마다 달라지면 운영자가 방금
            // 본 항목을 다시 찾지 못한다 — 발신번호 슬라이스 FR-SND-004 와 같은 이유(D-S14).
            // KKB_MSG_TMPL_L001 had no ORDER BY. A varying order loses the operator the item they just saw
            // — the same reason as FR-SND-004 in the 발신번호 slice (D-S14).
            assertThat(listSql()).containsIgnoringCase("ORDER BY");
        }
    }

    @Nested
    @DisplayName("읽기 전용 / read-only")
    class ReadOnly {

        @Test
        @DisplayName("매퍼에 쓰기 연산이 없다 / the mapper declares no write")
        // req: CONST-DATA-A03
        void mapperDeclaresNoWrite() {
            // CONST-DATA-A03: 이 슬라이스에서 KKB_MSG_TMPL 은 읽기 전용이다. 템플릿이 이 표에
            // 어떻게 들어오는지는 미해결이며(AMB-A07), 우리가 쓰기 시작하면 그 미해결을
            // 조용히 결정해 버리는 셈이 된다.
            // CONST-DATA-A03: KKB_MSG_TMPL is read-only in this slice. How templates arrive in it is open
            // (AMB-A07), and writing to it would quietly settle that open question.
            assertThat(TemplateMapper.class.getMethods())
                    .allSatisfy(m -> assertThat(m.getAnnotation(Select.class))
                            .as("%s must be a @Select — the table is read-only in this slice", m.getName())
                            .isNotNull());
        }
    }
}
