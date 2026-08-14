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
 * <p>⚠ 컬럼명 {@code IS_CD}/{@code IS_NM}/{@code USE_YN} 은 화면 00 의 JS 에서 확인했으나
 * 테이블명은 미확인이다(AMB-M01 과 동일 계열) — DBA 확인 필요.</p>
 * <p>The column names come from screen 00's JS but the table name is unconfirmed (same family as
 * AMB-M01) — needs DBA confirmation.</p>
 *
 * // source: biztalk_admin_40.js — fn_getIsList(): rec[i].IS_CD, rec[i].IS_NM; USE_YN=ALL
 * // req: FR-TEN-004
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
     * @return 이용기관 목록 / the institutions
     */
    // source: biztalk_admin_40.js — jexAjax.set("USE_YN", "ALL")
    // req: FR-TEN-004
    @Select("""
            SELECT IS_CD AS code, IS_NM AS name
              FROM BIZTALK_INSTITUTION
             WHERE USE_YN = 'Y'
             ORDER BY IS_NM
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
