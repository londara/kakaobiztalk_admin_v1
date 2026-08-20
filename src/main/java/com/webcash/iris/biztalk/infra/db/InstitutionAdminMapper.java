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
     * 기관코드로 한 건을 조회한다. / Reads a single institution by code.
     *
     * <p>수정 화면이 여는 행이다. 논리 삭제된 기관({@code IS_STTS='D'})은 조회되지 않는다 —
     * 목록에서 빠진 기관을 수정 화면에서는 열 수 있다면 삭제가 <b>목록에만</b> 적용된 것이
     * 된다(ADR-INST-014).</p>
     * <p>The row the edit screen opens. A logically deleted institution ({@code IS_STTS='D'}) is
     * not returned: if a row absent from the list could still be opened for editing, the delete
     * would apply <b>only to the list</b> (ADR-INST-014).</p>
     *
     * @param code 기관코드 {@code FINTECH_ISCD} / the institution code
     * @return 해당 행, 없으면 {@code null} / the row, or {@code null} when absent
     */
    // source: IDO.KKB_FT_FTIS_INFO_L002
    // req: FR-INSTC-001, FR-INSTC-010, FR-INST-007
    InstitutionEntity findByCode(@Param("code") String code);

    /**
     * 이용기관을 수정한다. / Updates an institution.
     *
     * <p>레거시는 이 자리에 <b>UPSERT 하나</b>를 두었다({@code IDO.KKB_FT_FTIS_INFO_C001}):
     * {@code WITH UPSERT AS (UPDATE … RETURNING *) INSERT … WHERE NOT EXISTS (SELECT * FROM
     * UPSERT)}. 등록과 수정이 같은 문장이었으므로 이미 있는 기관코드로 등록을 호출하면 그
     * 기관과 <b>그 기관의 인증키까지</b> 조용히 덮어썼다(D-I6). 이것은 {@code UPDATE} 이므로
     * 대상이 없으면 0을 반환할 뿐 <b>새 행을 만들 수 없다</b>.</p>
     * <p>The legacy had <b>one upsert</b> here, so calling create with an existing code silently
     * overwrote that institution <b>and its 인증키</b> (D-I6). This is an {@code UPDATE} and
     * nothing else: a missing target yields 0 rows and <b>cannot become an insert</b>.</p>
     *
     * <p>인증키는 <b>이 문장에 없다.</b> 재발급은 {@link #rotateAuthKey} 만이 수행한다.
     * 화면은 인증키를 마스킹된 상태로 받으므로(FR-INSTC-010), 만약 이 문장에
     * {@code ATK} 컬럼이 있었다면 그 마스킹 문자열이 그대로 저장되어 <b>고객사 연동이 즉시
     * 끊기는</b> 경로가 열린다. 컬럼을 두지 않는 것이 그 경로를 <b>표현 불가능하게</b>
     * 만드는 방법이다(TM-I022).</p>
     * <p>The 인증키 is <b>absent from this statement</b>; only {@link #rotateAuthKey} writes it.
     * The screen holds the key masked (FR-INSTC-010), so an {@code ATK} column here would open a
     * path for those mask characters to be stored as the credential and <b>cut off the customer
     * immediately</b>. Omitting the column makes that path <b>unrepresentable</b> (TM-I022).</p>
     *
     * @param command 수정 내용과 행위자 / the new values and the acting principal
     * @return 수정된 행 수 — 0 이면 대상이 없다 / rows updated; 0 means no such institution
     */
    // source: IDO.KKB_FT_FTIS_INFO_C001 — the UPDATE half of the upsert
    // req: FR-INSTC-002, FR-INSTC-004, FR-INSTC-006, FR-INSTC-007, FR-INSTC-013
    int update(@Param("command") InstitutionUpdate command);

    /**
     * 인증키를 재발급한다. / Rotates the 인증키.
     *
     * <p>수정과 <b>별도의 문장</b>이다. 키 교체는 고객사 연동을 즉시 끊는 조작이므로 일반
     * 필드 수정과 같은 경로에 두지 않는다(FR-ATK-005, FR-INSTC-011). 새 값은 서버가
     * 만들며({@code AtkGenerator}) 호출자가 준 값은 쓰지 않는다.</p>
     * <p>A <b>separate statement</b> from the update: rotating a key breaks the customer's live
     * integration at once, so it does not share a path with ordinary field edits (FR-ATK-005,
     * FR-INSTC-011). The value is generated on the server and never taken from the caller.</p>
     *
     * @param code    기관코드 / the institution code
     * @param authKey 새 인증키 / the new key
     * @param actorId 행위자 식별자 / the acting principal's identifier
     * @return 수정된 행 수 / rows updated
     */
    // source: IDO.KKB_FT_FTIS_INFO_C001 — ATK = :ATK
    // req: FR-ATK-001, FR-ATK-005, FR-INSTC-011, FR-INSTC-013
    int rotateAuthKey(@Param("code") String code,
                      @Param("authKey") String authKey,
                      @Param("actorId") String actorId);

    /**
     * 수정 문장에 바인딩되는 값. / The values bound into the update statement.
     *
     * <p>기관코드는 <b>대상 식별자로만</b> 쓰이고 {@code SET} 절에는 나타나지 않는다 —
     * 기관코드는 생성 후 불변이며(FR-INSTC-002), 컬럼을 바꿀 수 있게 두면 한 기관을 다른
     * 기관의 코드로 덮어쓰는 경로가 열린다.</p>
     * <p>The code is <b>only the target identifier</b> and never appears in the {@code SET}
     * clause: it is immutable after creation (FR-INSTC-002), and a settable column would open a
     * path to overwriting one institution onto another's code.</p>
     *
     * <p>{@code actorId} 는 요청이 아니라 <b>세션</b>에서 온다(FR-INSTC-007). 포털의 신원은
     * 이메일이므로 {@code LSED_ID} 와 {@code LSED_NM} 에 같은 값이 들어간다 — 레거시는
     * {@code SessionManager.getFlnm()} 으로 성명을 따로 넣었으나 포털 세션에는 성명이
     * 없다(FR-INSTC-012). 시각은 <b>이 레코드에 없다</b>: {@code LAST_AMDT} 는 SQL 안에서
     * 데이터베이스 시계로 만든다(ADR-INST-017).</p>
     * <p>{@code actorId} comes from <b>the session</b>, not the request (FR-INSTC-007). The
     * portal's identity is the email, so {@code LSED_ID} and {@code LSED_NM} receive the same
     * value — the legacy filled the latter from {@code SessionManager.getFlnm()}, which the portal
     * session has no counterpart for (FR-INSTC-012). There is <b>no timestamp field here</b>:
     * {@code LAST_AMDT} is produced by the database clock inside the SQL (ADR-INST-017).</p>
     *
     * @param code           대상 기관코드 / the institution to update
     * @param name           기관명 {@code ISNM} / institution name
     * @param englishName    영문명 {@code ISENGNM} / english name
     * @param businessNumber 사업자등록번호 {@code BRNO} / business registration number
     * @param status         사용여부 {@code IS_STTS} / status
     * @param description    설명 {@code CMOP} / description
     * @param actorId        최종수정자 {@code LSED_ID}/{@code LSED_NM} / the last modifier
     */
    // req: FR-INSTC-002, FR-INSTC-007, FR-INSTC-012, FR-INSTC-013
    record InstitutionUpdate(
            String code,
            String name,
            String englishName,
            String businessNumber,
            String status,
            String description,
            String actorId
    ) {
    }

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
