package com.webcash.iris.biztalk.infra.db;

import com.webcash.iris.biztalk.domain.InstitutionName;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * API 발송 집계 매퍼 — {@code BIZTALK_DB}. / The API-send aggregate mapper, on {@code BIZTALK_DB}.
 *
 * <p>기본 데이터소스에 붙는다. {@code @Mapper} 가 붙어 있으므로 MyBatis 자동 설정이
 * 등록한다 — 대량 쪽 매퍼는 <b>일부러 이 애노테이션을 달지 않는다</b>(그쪽 설명 참조).</p>
 * <p>Bound to the primary datasource and registered by MyBatis auto-configuration through
 * {@code @Mapper}. The bulk mapper <b>deliberately omits that annotation</b> — see its Javadoc.</p>
 *
 * // source: IDO.KKB_APITR_SMTN_L001 — target BIZTALK_DB
 * // req: FR-RPTS-001
 */
@Mapper
public interface ApiAggregateMapper extends AggregateMapper {

    /**
     * 기관코드에 대응하는 기관명을 조회한다. / Resolves institution names for the given codes.
     *
     * <p>기관 마스터는 이 데이터베이스에만 있으므로, 대량 집계에만 존재하는 행의 이름도
     * 여기서 채운다. 한 페이지 분량의 코드만 넘기므로 조회 크기에 상한이 있다.</p>
     * <p>The institution master lives only in this database, so names for rows that exist only in
     * the bulk aggregate are resolved here too. Only one page's worth of codes is passed, so the
     * lookup is bounded.</p>
     *
     * @param codes 조회할 기관코드. 비어 있으면 호출하지 않는다 / the codes; never called with an empty set
     * @return 코드와 이름의 짝 / the code-name pairs
     */
    // req: FR-RPT-012
    List<InstitutionName> findInstitutionNames(@Param("codes") Collection<String> codes);
}
