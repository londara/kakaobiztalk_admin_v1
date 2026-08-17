package com.webcash.iris.biztalk.infra.db;

import com.webcash.iris.biztalk.domain.InstitutionSearchCriteria;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 이용기관 관리 매퍼 — 화면 00 목록 조회. / 이용기관 admin mapper, screen 00's list.
 *
 * <p>레거시 {@code IDO.KKB_FT_FTIS_INFO_L001} 을 이식한다. 이식 SQL 이므로 XML 에 두고
 * {@code FIX D-In:} 주석으로 델타를 표시한다.</p>
 * <p>Ports {@code IDO.KKB_FT_FTIS_INFO_L001}. Being ported SQL it lives in XML, with
 * {@code FIX D-In:} comments marking each delta.</p>
 *
 * <p>컬럼명은 계약 필드명이 아니라 <b>실제 테이블 컬럼명</b>을 쓴다. 레거시 IDO 는
 * {@code SELECT} 순서와 {@code <out>} 순서를 위치로 맞춰 두 이름 체계의 차이를 가렸고,
 * 그 때문에 {@link InstitutionMapper} 가 잘못된 이름으로 작성되었다(RISK-I05).</p>
 * <p>Column names are the <b>real table columns</b>, not contract field names. The legacy IDO
 * aligned {@code SELECT} order with {@code <out>} order positionally, hiding the difference
 * between the two naming schemes — which is how {@link InstitutionMapper} came to use the wrong
 * ones (RISK-I05).</p>
 *
 * // source: IDO.KKB_FT_FTIS_INFO_L001
 * // req: FR-INST-001, FR-INST-003, FR-INST-004, FR-INST-005, CONST-DATA-I04
 */
@Mapper
public interface InstitutionAdminMapper {

    /**
     * 조건에 맞는 이용기관 한 페이지를 반환한다. / Returns one page of matching institutions.
     *
     * @param criteria 조회 조건 / the search criteria
     * @return 이용기관 목록 / the institutions on this page
     */
    // source: IDO.KKB_FT_FTIS_INFO_L001
    // req: FR-INST-001, FR-INST-003
    List<InstitutionEntity> search(@Param("criteria") InstitutionSearchCriteria criteria);

    /**
     * 조건에 맞는 전체 건수를 반환한다. / Returns the total number of matching institutions.
     *
     * <p>레거시에는 이 쿼리가 없었다 — 전량을 반환하고 클라이언트가 세었다(D-I10).</p>
     * <p>The legacy had no such query: it returned everything and let the client count (D-I10).</p>
     *
     * @param criteria 조회 조건 / the search criteria
     * @return 전체 건수 / the total count
     */
    // req: FR-INST-003
    int count(@Param("criteria") InstitutionSearchCriteria criteria);

    /**
     * 매퍼가 반환하는 원본 행 — <b>평문 인증키를 포함한다</b>.
     * The raw mapper row — <b>it contains the plaintext 인증키</b>.
     *
     * <p>이 타입은 절대로 직접 직렬화하지 않는다. 서비스가
     * {@code com.webcash.iris.biztalk.domain.InstitutionRow} 로 변환하면서 인증키를
     * 마스킹한다(FR-ATK-002).</p>
     * <p>This type is never serialised directly. The service converts it to
     * {@code com.webcash.iris.biztalk.domain.InstitutionRow}, masking the key on the way
     * (FR-ATK-002).</p>
     *
     * @param code           기관코드 {@code FINTECH_ISCD} / institution code
     * @param name           기관명 {@code ISNM} / institution name
     * @param englishName    영문명 {@code ISENGNM} / english name
     * @param businessNumber 사업자등록번호 {@code BRNO} / business registration number
     * @param authKey        <b>평문</b> 인증키 {@code ATK} / the <b>plaintext</b> 인증키
     * @param status         사용여부 {@code IS_STTS} / status
     * @param description    설명 {@code CMOP} / description
     * @param registeredAt   등록일시 {@code RGDT} / registered timestamp
     * @param lastModifiedAt 수정일시 {@code LAST_AMDT} / last modified timestamp
     */
    // source: IDO.KKB_FT_FTIS_INFO_L001 — FINTECH_ISCD, ISNM, ISENGNM, BRNO, IS_STTS, ATK, RGDT, LAST_AMDT, CMOP
    // req: FR-INST-002
    record InstitutionEntity(
            String code,
            String name,
            String englishName,
            String businessNumber,
            String authKey,
            String status,
            String description,
            String registeredAt,
            String lastModifiedAt
    ) {
    }
}
