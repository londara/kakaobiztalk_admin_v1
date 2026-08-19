package com.webcash.iris.biztalk.infra.db.bulk;

import com.webcash.iris.biztalk.infra.db.AggregateMapper;

/**
 * 대량 발송 집계 매퍼 — {@code BIZTALK_BULK_DB}.
 * The bulk-send aggregate mapper, on {@code BIZTALK_BULK_DB}.
 *
 * <h2>{@code @Mapper} 가 <b>없는</b> 이유 / why there is no {@code @Mapper}</h2>
 * <p>이 애노테이션이 있으면 MyBatis 자동 설정이 이 인터페이스를 <b>기본 데이터소스</b>에
 * 등록한다. 그러면 대량 질의가 API 데이터베이스로 날아가고, 두 출처가 사실은 하나가 된다 —
 * 합계는 두 배가 되고 오류는 나지 않는다. 대신
 * {@code ReportDataSourceConfig} 가 전용 {@code SqlSessionFactory} 에 명시적으로 등록한다.</p>
 * <p>With the annotation, MyBatis auto-configuration would bind this interface to the
 * <b>primary datasource</b>, sending bulk queries to the API database and quietly collapsing two
 * sources into one — doubled sums, no error. {@code ReportDataSourceConfig} registers it
 * explicitly against its own {@code SqlSessionFactory} instead.</p>
 *
 * <h2>이 빈이 없을 수 있다 / this bean may be absent</h2>
 * <p>대량 데이터소스가 설정되지 않은 환경에서는 이 매퍼가 아예 생성되지 않는다. 서비스는
 * {@code Optional} 로 받아 <b>부분 결과임을 명시</b>한다(FR-RPTS-005) — 조용히 API 분만
 * 돌려주지 않는다.</p>
 * <p>Where the bulk datasource is not configured this mapper is not created at all. The service
 * takes it as an {@code Optional} and <b>marks the result incomplete</b> (FR-RPTS-005) rather
 * than quietly returning API figures alone.</p>
 *
 * // source: IDO.BULK_KKB_APITR_SMTN_L001 — target BIZTALK_BULK_DB
 * // req: FR-RPTS-001, FR-RPTS-005, ADR-RPT-021
 */
public interface BulkAggregateMapper extends AggregateMapper {
}
