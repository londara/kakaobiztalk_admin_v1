package com.webcash.iris.biztalk.infra.db;

import com.webcash.iris.biztalk.domain.TalkHistoryCriteria;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 톡전송 거래내역 매퍼 — {@code BIZTALK_DB}. / The 톡전송 거래내역 mapper, on {@code BIZTALK_DB}.
 *
 * <p>{@code FT_APITR_HSTR} 를 읽는다. 쓰기 메서드를 <b>선언하지 않는다</b> — 이 테이블은 API
 * 게이트웨이가 쓰고 이 슬라이스는 읽기 전용이다(CONST-DATA-T01). 애플리케이션 경로에 존재하지
 * 않는 기능은 실수로 호출될 수도 없다.</p>
 * <p>Reads {@code FT_APITR_HSTR}. No write method is <b>declared</b>: the table is written by the API
 * gateway and this slice is read-only (CONST-DATA-T01). A capability absent from the application
 * cannot be invoked by mistake.</p>
 *
 * <p>행 프로젝션이 <b>9개 컬럼으로 닫혀</b> 있다. 이 테이블은 전체 핀테크 API 의 거래 로그이며
 * 계좌·카드·금액·전문 컬럼을 담고 있다(CONST-SEC-T01).</p>
 * <p>The row projection is <b>closed at nine columns</b>. This table is the whole fintech estate's
 * transaction log and carries account, card, amount and telegram columns (CONST-SEC-T01).</p>
 *
 * // source: IDO.KKB_APITR_HSTR_L001 — target BIZTALK_DB
 * // req: FR-TLK-003, FR-TLK-005, FR-TLK-006, FR-TLK-011, FR-TLK-012, CONST-DATA-T01, CONST-SEC-T01
 */
@Mapper
public interface TalkHistoryMapper {

    /**
     * 조건에 맞는 한 페이지를 조회한다. / Reads one page of matching rows.
     *
     * <p>정렬은 {@code (RGDT DESC, IS_TUNO DESC)} 로 <b>전순서</b>다. 레거시는
     * {@code ORDER BY RGDT DESC} 하나뿐이었고, 운영 화면 캡처에서 11개 행이 같은
     * {@code 11:25:04} 를 공유하므로 동시각은 이 화면에서 <b>예외가 아니라 정상</b>이다 —
     * 페이징과 결합되면 행이 두 페이지에 나오거나 어느 페이지에도 나오지 않는다(D-T10).</p>
     * <p>Ordered {@code (RGDT DESC, IS_TUNO DESC)}, which is <b>total</b>. The legacy had only
     * {@code ORDER BY RGDT DESC}, and the production screenshot shows eleven rows sharing
     * {@code 11:25:04} — so ties are <b>the normal case here, not an edge case</b>, and under paging
     * a row appears on two pages or on none (D-T10).</p>
     *
     * @param criteria 검증된 조건 / the validated criteria
     * @return 이 페이지의 행 / the rows on this page
     */
    // req: FR-TLK-003, FR-TLK-005, FR-TLK-006, FR-TLK-011
    List<TalkHistoryRowRecord> findPage(@Param("c") TalkHistoryCriteria criteria);

    /**
     * 조건에 맞는 전체 건수를 조회한다. / Counts all matching rows.
     *
     * <p>레거시는 {@code DomainUtil.setIDOPageInfo} 로 페이지 정보를 넘기고도 <b>건수를 되
     * 읽지 않았고</b>, {@code WSVC.biztalk_admin_30_l001} 의 {@code <out>} 에도 그런 필드가
     * 없었다. 같은 폴더의 형제 서비스 {@code biztalk_admin_32_l001} 은
     * {@code DomainUtil.getMaxResultCount} 로 {@code TOT_CNT} 를 정확히 반환한다 — 올바른
     * 방식이 같은 폴더에 있었고 적용되지 않았다(D-T11).</p>
     * <p>The legacy passed page info via {@code DomainUtil.setIDOPageInfo} and <b>never read the
     * count back</b>; {@code WSVC.biztalk_admin_30_l001} declared no such output. Its sibling in the
     * same folder, {@code biztalk_admin_32_l001}, does it correctly with
     * {@code DomainUtil.getMaxResultCount} into {@code TOT_CNT} — the right pattern was in the same
     * folder and was not applied (D-T11).</p>
     *
     * @param criteria 검증된 조건 / the validated criteria
     * @return 전체 건수 / the total count
     */
    // req: FR-TLK-005
    int countAll(@Param("c") TalkHistoryCriteria criteria);

    /**
     * 구간 안에서 실제로 나타난 API 서비스 코드와 그 건수를 조회한다.
     * Reads the API service codes actually observed in a window, with their counts.
     *
     * <p><b>대조 보고서(T1-14)의 질의다.</b> SCOPE-T01 이 화면을 좁히면서 새로운 실패 양식이
     * 생겼다 — 과소 포함은 <b>보이지 않는다</b>. 실제 거래에 나타난 코드 전체를 세어
     * 허용 목록과 비교하면, 분류가 놓친 코드가 건수와 함께 드러난다(RISK-T01).</p>
     * <p><b>This is the reconciliation report's query (T1-14).</b> Narrowing the screen under
     * SCOPE-T01 introduced a new failure mode: under-inclusion is <b>invisible</b>. Counting every
     * code that actually appears and comparing it to the allow-list surfaces what the classification
     * missed, with a number attached (RISK-T01).</p>
     *
     * @param fromDate 시작일자 {@code YYYYMMDD} / the start date
     * @param toDate   종료일자 {@code YYYYMMDD} / the end date
     * @return 코드와 건수 / codes with their counts
     */
    // req: FR-TLK-002, ADR-TLK-024
    List<ObservedApiService> findObservedApiServices(@Param("fromDate") String fromDate,
                                                    @Param("toDate") String toDate);

    /**
     * 매퍼가 반환하는 행 — 도메인 레코드로 조립되기 전의 형태.
     * The row as the mapper returns it, before assembly into the domain record.
     *
     * <p>{@code detailAvailable} 이 여기 없는 것이 의도다. 그 값은 <b>레지스트리가 결정</b>하며
     * 데이터베이스가 아는 것이 아니다 — SQL 이 그것을 계산하면 레지스트리와 어긋날 수 있는
     * 두 번째 구현이 생긴다(D-T13 이 정확히 그 형태였다).</p>
     * <p>The absence of {@code detailAvailable} here is deliberate: it is decided by <b>the
     * registry</b>, not something the database knows. Computing it in SQL would create a second
     * implementation able to disagree with the registry — which is exactly the shape of D-T13.</p>
     *
     * @param transactionDate 일자 {@code TRDD} / the transaction date
     * @param institutionCode 기관코드 {@code FINTECH_ISCD} / the institution code
     * @param institutionName 기관명 {@code ISNM}. 미해석 시 null / the institution name, null when unresolved
     * @param transactionNo   거래고유번호 {@code IS_TUNO} / the transaction serial
     * @param apiServiceCode  API {@code API_SVC_CD} / the API service code
     * @param statusCode      상태 {@code PRSU} / the status code
     * @param responseCode    응답코드 {@code FINTECH_RPCD} / the response code
     * @param registeredAt    등록시각 {@code RGDT} / when recorded
     * @param completedAt     완료시각 {@code LAST_AMDT} / when last changed
     */
    // req: FR-TLK-003, FR-TLK-012, CONST-SEC-T01
    record TalkHistoryRowRecord(
            String transactionDate,
            String institutionCode,
            String institutionName,
            String transactionNo,
            String apiServiceCode,
            String statusCode,
            String responseCode,
            String registeredAt,
            String completedAt
    ) {
    }

    /**
     * 실제로 관측된 API 서비스 코드와 건수. / An observed API service code and its count.
     *
     * @param apiServiceCode {@code API_SVC_CD}
     * @param transactionCount 해당 구간의 거래 건수 / the transaction count in the window
     */
    // req: FR-TLK-002
    record ObservedApiService(String apiServiceCode, long transactionCount) {
    }
}
