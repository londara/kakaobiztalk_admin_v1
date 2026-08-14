package com.webcash.iris.biztalk.infra.db;

import com.webcash.iris.biztalk.domain.MessageHistoryCriteria;
import com.webcash.iris.biztalk.domain.MessageHistoryRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 문자내역 조회 매퍼. / 문자내역 query mapper.
 *
 * <p>SQL 은 {@code mybatis/mapper/biztalk/MessageHistoryMapper.xml} 에 있으며 레거시
 * {@code IDO.KKB_MSG_L002.xml} 를 near-verbatim 으로 이식했다. 변경한 줄마다
 * {@code -- FIX Dn:} 주석이 있어 델타만 리뷰할 수 있다(RISK-001).</p>
 * <p>The SQL lives in the XML and is a near-verbatim port of the legacy IDO, with every
 * altered line annotated {@code -- FIX Dn:} so only the delta needs review (RISK-001).</p>
 *
 * <p><b>이용기관 조건은 조건부가 아니다.</b> {@link MessageHistoryCriteria#institutionCode()}
 * 가 {@code null} 이면 조건이 생성되지 않는데, 그 {@code null} 은 <b>운영자</b>에게만
 * 허용된다. 이용기관 담당자의 요청에서는 서비스 계층이 항상 코드를 채워 넣는다
 * (FR-TEN-001) — 매퍼는 그 불변식을 강제할 수 없으므로 서비스가 책임진다.</p>
 * <p><b>The 이용기관 predicate is not optional.</b> A {@code null} suppresses it, and that null
 * is permitted only for operators; for a client-company request the service layer always
 * populates the code (FR-TEN-001). The mapper cannot enforce that invariant, so the service
 * owns it.</p>
 *
 * // source: IDO.KKB_MSG_L002.xml, biztalk_admin_40_l001_act.jsp
 * // req: FR-MSG-003, FR-MSG-007, FR-MSG-008, FR-MSG-011, FR-MSG-015, FR-TEN-001
 */
@Mapper
public interface MessageHistoryMapper {

    /**
     * 조건에 맞는 한 페이지를 조회한다. / Returns one page of matching rows.
     *
     * @param criteria 조회 조건 / the search criteria
     * @return 이 페이지의 행 / the rows on this page
     */
    // req: FR-MSG-003, FR-MSG-007
    List<MessageHistoryRow> search(@Param("criteria") MessageHistoryCriteria criteria);

    /**
     * 조건에 맞는 전체 건수를 조회한다. / Returns the total matching count.
     *
     * <p>목록과 <b>동일한 UNION·필터</b>를 재사용한다. 별도 쿼리로 작성하면 두 결과가
     * 어긋날 수 있고, 그때 사용자는 존재하지 않는 페이지를 보게 된다.</p>
     * <p>Reuses the same union and filters: a separate query could drift, and the user would
     * then see pages that do not exist.</p>
     *
     * @param criteria 조회 조건 / the search criteria
     * @return 전체 건수 / the total count
     */
    // req: FR-MSG-007
    int count(@Param("criteria") MessageHistoryCriteria criteria);
}
