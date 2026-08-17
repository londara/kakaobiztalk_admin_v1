package com.webcash.iris.biztalk.infra.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link InstitutionMapper} SQL 식별자 검증 — 결함 RISK-I05 회귀.
 * SQL identifier verification for {@link InstitutionMapper} — RISK-I05 regression.
 *
 * <h2>이 테스트가 통합 테스트가 아닌 이유 / Why this is not an integration test</h2>
 * <p>본래 이 검증은 실제 PostgreSQL 에 대한 통합 테스트여야 한다(TEST-PLAN-INSTITUTION §5,
 * 작업 T-I1-02). 그러나 <b>현재 환경에 Docker 가 설치되어 있지 않아</b> Testcontainers 를
 * 기동할 수 없다(RISK-I09). 이 테스트는 그 <b>대체물이며 동등물이 아니다</b> — 테이블이
 * 실제로 존재하는지, 컬럼 타입이 맞는지는 검증하지 못한다.</p>
 * <p>This check should be an integration test against a real PostgreSQL (TEST-PLAN-INSTITUTION
 * §5, task T-I1-02). <b>Docker is not installed in this environment</b>, so Testcontainers
 * cannot start (RISK-I09). This test is a <b>substitute, not an equivalent</b>: it cannot
 * verify that the table exists or that column types match.</p>
 *
 * <p>그럼에도 가치가 있는 이유는, 원래 결함이 정확히 <b>식별자 이름</b>의 문제였기
 * 때문이다. 매퍼는 서비스 계약의 필드명({@code IS_CD}/{@code IS_NM}/{@code USE_YN})을
 * 테이블 컬럼명으로 오인해 작성되었고, 추측된 테이블명({@code BIZTALK_INSTITUTION})은
 * 존재하지 않았다. 이 테스트는 그 회귀를 막는다.</p>
 * <p>It still earns its place because the original defect was precisely a <b>naming</b> problem:
 * the mapper mistook service-contract field names ({@code IS_CD}/{@code IS_NM}/{@code USE_YN})
 * for table columns, against a guessed table ({@code BIZTALK_INSTITUTION}) that does not exist.
 * This test prevents that regression.</p>
 *
 * // source: IDO.KKB_FT_FTIS_INFO_L001 — SELECT FINTECH_ISCD, ISNM ... FROM FT_FTIS_INFO
 * // req: FR-TEN-004, CONST-DATA-I04, RISK-I05, RISK-I09
 */
class InstitutionMapperSqlTest {

    private String findAllActiveSql() throws NoSuchMethodException {
        Method method = InstitutionMapper.class.getMethod("findAllActive");
        Select select = method.getAnnotation(Select.class);
        assertThat(select)
                .as("findAllActive must carry an @Select annotation")
                .isNotNull();
        return String.join(" ", select.value());
    }

    @Test
    @DisplayName("실제 테이블 FT_FTIS_INFO 를 조회한다 / queries the real table FT_FTIS_INFO")
        // req: RISK-I05
    void queriesRealTable() throws Exception {
        assertThat(findAllActiveSql()).contains("FT_FTIS_INFO");
    }

    @ParameterizedTest
    @ValueSource(strings = {"FINTECH_ISCD", "ISNM", "IS_STTS"})
    @DisplayName("실제 컬럼명을 사용한다 / uses the real column names")
        // req: CONST-DATA-I04
    void usesRealColumnNames(String column) throws Exception {
        assertThat(findAllActiveSql()).contains(column);
    }

    @ParameterizedTest
    @ValueSource(strings = {"BIZTALK_INSTITUTION", "IS_CD", "IS_NM", "USE_YN"})
    @DisplayName("계약 필드명이나 추측 테이블명을 사용하지 않는다 / uses no contract field or guessed table name")
        // req: RISK-I05
    void usesNoGuessedIdentifier(String wrongIdentifier) throws Exception {
        // IS_CD 는 FINTECH_ISCD 의 부분 문자열이 아니고, IS_NM 도 ISNM 과 다르다.
        // 따라서 단순 contains 검사로 충분히 구분된다.
        // IS_CD is not a substring of FINTECH_ISCD, and IS_NM differs from ISNM, so a plain
        // contains check distinguishes them adequately.
        assertThat(findAllActiveSql()).doesNotContain(wrongIdentifier);
    }

    @Test
    @DisplayName("활성 기관만 조회한다 / selects active institutions only")
        // req: FR-TEN-004, ADR-INST-014
    void filtersToActiveOnly() throws Exception {
        // IS_STTS = 'Y' 는 중지('N')와 논리삭제('D')를 함께 제외한다.
        // IS_STTS = 'Y' excludes suspended ('N') and logically deleted ('D') alike.
        assertThat(findAllActiveSql().replaceAll("\\s+", " "))
                .contains("IS_STTS = 'Y'");
    }
}
