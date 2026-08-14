package com.webcash.iris.biztalk.infra.db;

import com.webcash.iris.biztalk.domain.MessageDetail;
import com.webcash.iris.biztalk.domain.MessageDetailKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 문자상세내역 조회 매퍼. / Message detail mapper.
 *
 * <p>레거시는 4개의 별도 IDO({@code KKO_SMS_MSG_L001} 등)로 나뉘어 있었고, 어느 것을
 * 호출할지는 action JSP 의 {@code if/else} 가 결정했다. 여기서는 하나의 statement 안에서
 * {@code <choose>} 로 결정한다 — 라우팅 규칙이 SQL 과 같은 파일에 있어 함께 리뷰된다.</p>
 * <p>The legacy split this across four IDOs, with an action JSP's {@code if/else} choosing
 * between them. Here one statement decides with {@code <choose>}, so the routing rule sits in
 * the same file as the SQL and is reviewed alongside it.</p>
 *
 * // source: IDO.KKO_SMS_MSG_L001 / KKO_MMS_MSG_L001 / KKF_SMS_MSG_L001 / KKF_MMS_MSG_L001
 * // source: biztalk_admin_41_l001_act.jsp — idoName if/else selection
 * // req: FR-MSGD-001, FR-MSGD-002, FR-MSGD-004, FR-TEN-001
 */
@Mapper
public interface MessageDetailMapper {

    /**
     * 상세내역 1건을 조회한다. / Returns one detail record.
     *
     * <p>{@code institutionCode} 가 {@code null} 이면 이용기관 조건이 생성되지 않는다.
     * 그 {@code null} 은 <b>운영자에게만</b> 허용되며, 서비스 계층이 그 불변식을 지킨다
     * (FR-MSGD-001).</p>
     * <p>A {@code null} {@code institutionCode} omits the predicate, and that null is permitted
     * only for operators — an invariant the service layer maintains.</p>
     *
     * @param key             조회 키 / the lookup key
     * @param institutionCode 서버 도출 이용기관 코드. 운영자는 null / server-derived 이용기관, null for operators
     * @return 상세내역, 없으면 null / the detail, or null when not found
     */
    // req: FR-MSGD-001, FR-MSGD-002, FR-TEN-001
    MessageDetail findDetail(@Param("key") MessageDetailKey key,
                             @Param("institutionCode") String institutionCode);
}
