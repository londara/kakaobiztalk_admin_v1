package com.webcash.iris.biztalk.infra.db;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 이용기관 목록 매퍼. / 이용기관 list mapper.
 *
 * <p>레거시 {@code biztalk_admin_00_l001} 서비스에 대응한다. 그 서비스는 담당자관리
 * 화면(00)의 목록 조회용이었으나 문자내역 화면이 드롭다운 채우기에 재사용했다.</p>
 * <p>Corresponds to the legacy {@code biztalk_admin_00_l001} service, which existed for screen
 * 00's list but was reused by the 문자내역 screen to populate a dropdown.</p>
 *
 * <p><b>{@code @Select} 를 인라인으로 쓴 이유:</b> 이 쿼리는 레거시 IDO 를 이식한 것이
 * 아니라 신규 작성이다. 이식 SQL 은 XML 에 두고 {@code -- FIX Dn:} 주석으로 델타를
 * 표시하지만, 신규 SQL 은 비교 대상이 없으므로 그 규약이 적용되지 않는다.</p>
 * <p><b>Why an inline {@code @Select}:</b> this query is new, not a port. Ported SQL lives in XML
 * with {@code -- FIX Dn:} annotations marking the delta; new SQL has nothing to compare against,
 * so that convention does not apply.</p>
 *
 * <h2>테이블·컬럼명 정정 (RISK-I05) / Table and column correction (RISK-I05)</h2>
 * <p>이 매퍼는 원래 테이블 {@code BIZTALK_INSTITUTION} 과 컬럼
 * {@code IS_CD}/{@code IS_NM}/{@code USE_YN} 을 대상으로 작성되었고, 그 이름들은 화면 40 의
 * JS 에서 읽은 것이었다. Skill 2 의 화면 00 분석에서 <b>네 이름이 모두 틀렸음</b>이
 * 확인되었다 — JS 가 쓰던 이름은 <b>서비스 계약(WSVC)의 필드명</b>이었고, 실제 테이블
 * 컬럼명은 다르다. 레거시 IDO 가 {@code SELECT} 순서와 {@code <out>} 순서를
 * <b>위치 기반</b>으로 매핑해 이 차이를 가리고 있었다.</p>
 * <p>This mapper originally targeted {@code BIZTALK_INSTITUTION} with columns
 * {@code IS_CD}/{@code IS_NM}/{@code USE_YN}, taken from screen 40's JS. Skill 2's analysis of
 * screen 00 established that <b>all four names were wrong</b>: the JS used the
 * <b>service-contract field names</b>, not the table's. The legacy IDO hid the gap by mapping
 * {@code SELECT} order to {@code <out>} order <b>positionally</b>.</p>
 *
 * <table border="1">
 *   <caption>정정 내역 / corrections</caption>
 *   <tr><th>이전 (추정) / previous (guessed)</th><th>실제 / actual</th></tr>
 *   <tr><td>{@code BIZTALK_INSTITUTION}</td><td>{@code FT_FTIS_INFO}</td></tr>
 *   <tr><td>{@code IS_CD}</td><td>{@code FINTECH_ISCD}</td></tr>
 *   <tr><td>{@code IS_NM}</td><td>{@code ISNM}</td></tr>
 *   <tr><td>{@code USE_YN}</td><td>{@code IS_STTS}</td></tr>
 * </table>
 *
 * <p>정정 전 이 쿼리는 실제 데이터베이스에서 동작하지 않았다 — 문자내역 화면의 이용기관
 * 드롭다운은 테스트 밖에서 채워지지 않는다.</p>
 * <p>Before this correction the query could not run against the real database: the 문자내역
 * institution dropdown does not populate outside tests.</p>
 *
 * // source: IDO.KKB_FT_FTIS_INFO_L001 — SELECT FINTECH_ISCD, ISNM ... FROM FT_FTIS_INFO
 * // req: FR-TEN-004, CONST-DATA-I04, RISK-I05
 */
@Mapper
public interface InstitutionMapper {

    /**
     * 사용 중인 이용기관 목록을 반환한다. / Returns the active 이용기관 list.
     *
     * <p>레거시는 {@code USE_YN=ALL} 로 <b>사용 중지된 기관까지</b> 포함해 조회했다.
     * 여기서는 사용 중인 기관만 반환한다 — 중지된 기관을 조회 조건으로 고르는 것은
     * 의미가 없고, 목록이 짧을수록 노출도 적다.</p>
     * <p>The legacy passed {@code USE_YN=ALL}, including deactivated institutions. Only active
     * ones are returned here: selecting a deactivated institution as a filter serves no purpose,
     * and a shorter list exposes less.</p>
     *
     * <p>{@code IS_STTS = 'Y'} 는 중지({@code 'N'}) 와 논리삭제({@code 'D'}) 를 모두
     * 제외한다 — 논리삭제를 별도 컬럼이 아니라 상태값으로 둔 덕분에 이 필터에 별도
     * 조건을 더할 필요가 없다(ADR-INST-014).</p>
     * <p>{@code IS_STTS = 'Y'} excludes both suspended ({@code 'N'}) and logically deleted
     * ({@code 'D'}) institutions. Because deletion is a status value rather than a separate
     * column, this filter needs no extra clause (ADR-INST-014).</p>
     *
     * @return 이용기관 목록 / the institutions
     */
    // source: IDO.KKB_FT_FTIS_INFO_L001 — SELECT FINTECH_ISCD, ISNM FROM FT_FTIS_INFO
    // req: FR-TEN-004, RISK-I05, ADR-INST-014
    @Select("""
            SELECT FINTECH_ISCD AS code, ISNM AS name
              FROM FT_FTIS_INFO
             WHERE IS_STTS = 'Y'
             ORDER BY ISNM
            """)
    List<Institution> findAllActive();

    /**
     * 이용기관 1건. / One 이용기관.
     *
     * @param code 이용기관 코드 / the institution code
     * @param name 이용기관 명 / the institution name
     */
    // source: biztalk_admin_40.js — IS_CD / IS_NM
    // req: FR-TEN-004
    record Institution(String code, String name) {
    }
}
