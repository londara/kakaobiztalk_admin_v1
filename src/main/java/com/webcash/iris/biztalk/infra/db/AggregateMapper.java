package com.webcash.iris.biztalk.infra.db;

import com.webcash.iris.biztalk.domain.AggregateKey;
import com.webcash.iris.biztalk.domain.AggregateRow;
import com.webcash.iris.biztalk.domain.ReportCriteria;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 집계 조회의 공통 계약. / The shared contract for reading an aggregate.
 *
 * <p>두 출처가 <b>같은 인터페이스</b>를 구현한다. 이것이 우연이 아니라 설계다 — 병합은 두
 * 스트림의 형태와 정렬이 같을 때만 성립하고, 인터페이스를 공유하면 한쪽만 바뀌는 일이
 * 타입 수준에서 어려워진다(ADR-RPT-021).</p>
 * <p>Both sources implement the <b>same interface</b>, by design rather than coincidence: the
 * merge only works when both streams share a shape and an order, and a shared interface makes
 * changing one alone harder at the type level (ADR-RPT-021).</p>
 *
 * // source: IDO.KKB_APITR_SMTN_L001, IDO.BULK_KKB_APITR_SMTN_L001
 * // req: FR-RPTS-001, FR-RPTS-004, ADR-RPT-021
 */
public interface AggregateMapper {

    /**
     * 이어보기 키 이후의 한 페이지를 표시 순서로 조회한다.
     * Reads one page beyond the seek key, in display order.
     *
     * @param criteria 검증된 조회 조건 / the validated criteria
     * @return 집계 행 / the aggregate rows
     */
    // req: FR-RPT-005, FR-RPT-006
    List<AggregateRow> findPage(@Param("criteria") ReportCriteria criteria);

    /**
     * 기간·범위에 해당하는 <b>키만</b> 조회한다.
     * Reads <b>only the keys</b> matching the period and scope.
     *
     * <p>전체 건수는 {@code count(A) + count(B)} 로 구할 수 없다 — 두 출처 모두에 존재하는
     * 날짜가 두 번 세어진다. 대신 양쪽의 키 집합을 합집합하여 센다. 키는 두 컬럼뿐이므로
     * 366일 상한에서도 비용이 작다(ADR-RPT-021).</p>
     * <p>The total cannot be {@code count(A) + count(B)}: a day present in both sources would be
     * counted twice. The union of both key sets is counted instead. Keys are two columns, so this
     * stays cheap even at the 366-day cap (ADR-RPT-021).</p>
     *
     * @param criteria 검증된 조회 조건 / the validated criteria
     * @param limit    상한. 초과분은 읽지 않는다 / the ceiling; nothing beyond it is read
     * @return 키 목록 / the keys
     */
    // req: FR-RPT-005, ADR-RPT-021
    List<AggregateKey> findKeys(@Param("criteria") ReportCriteria criteria,
                                @Param("limit") int limit);

    /**
     * 이 출처에서 집계가 존재하는 가장 최근 일자를 반환한다.
     * Returns the latest date this source holds any aggregate for.
     *
     * @return {@code YYYYMMDD}. 데이터가 없으면 null / the date, null when the source is empty
     */
    // req: FR-RPT-013, ADR-RPT-022
    String findMaxTradeDate();
}
