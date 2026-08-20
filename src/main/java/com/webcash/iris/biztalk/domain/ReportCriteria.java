package com.webcash.iris.biztalk.domain;

/**
 * 검증을 마친 보고서 조회 조건. / A validated report query.
 *
 * <p>매퍼는 요청 객체가 아니라 이 타입을 받는다. 레거시 다운로드 액션은 계약에 선언되지도
 * 않은 {@code IS_CD} 를 {@code request.getParameter} 로 직접 읽어 쿼리에 넣었고, 그 우회가
 * 응답 헤더 주입(D-R3)이 가능해진 경로였다(D-R10). 검증된 타입만 아래로 내려가면 그 우회는
 * 컴파일되지 않는다.</p>
 * <p>Mappers take this rather than a request object. The legacy download action read an
 * {@code IS_CD} its contract never declared straight from {@code request.getParameter} and put it
 * into the query; that bypass is how header injection became reachable (D-R3, D-R10). When only
 * validated types travel downward, the bypass does not compile.</p>
 *
 * @param scope      조회 범위 / the resolved scope
 * @param period     검증된 기간 / the validated period
 * @param source     발송 구분 / the send-source filter
 * @param seek       이어보기 키. 첫 페이지면 null / the seek key, null on the first page
 * @param size       페이지 크기 / the page size
 *
 * // req: FR-RPT-002, FR-RPT-005, FR-AZ-R03, FR-RPTS-002
 */
public record ReportCriteria(
        ReportScope scope,
        PeriodPolicy.ReportPeriod period,
        SendSource source,
        AggregateKey seek,
        int size) {

    /** 기본 페이지 크기. / The default page size. */
    public static final int DEFAULT_SIZE = 100;

    /** 최대 페이지 크기. / The maximum page size. */
    public static final int MAX_SIZE = 500;

    /**
     * 페이지 크기를 정규화하여 조건을 만든다.
     * Builds the criteria with a normalised page size.
     *
     * <p>크기를 무제한으로 두면 클라이언트가 {@code size=1000000} 으로 페이징을 무력화할 수
     * 있다 — 서버 페이징을 복원한 의미가 사라진다(D-R8).</p>
     * <p>An unbounded size lets a client defeat pagination with {@code size=1000000}, which
     * removes the point of having restored server-side paging (D-R8).</p>
     *
     * @param scope    범위 / the scope
     * @param period   기간 / the period
     * @param source   발송 구분 / the source filter
     * @param seek     이어보기 키 / the seek key
     * @param rawSize  요청 페이지 크기 / the requested size
     * @return 조건 / the criteria
     */
    // req: FR-RPT-005
    public static ReportCriteria of(ReportScope scope,
                                    PeriodPolicy.ReportPeriod period,
                                    SendSource source,
                                    AggregateKey seek,
                                    Integer rawSize) {
        int size = rawSize == null || rawSize <= 0 ? DEFAULT_SIZE : Math.min(rawSize, MAX_SIZE);
        return new ReportCriteria(scope, period, source, seek, size);
    }

    /**
     * 매퍼가 한 출처에서 가져올 행 수를 반환한다.
     * Returns how many rows to fetch from one source.
     *
     * <p>페이지 크기보다 <b>하나 더</b> 가져온다. 병합 후 다음 페이지가 있는지 알아내려면
     * 경계 너머의 존재를 확인해야 하고, 별도 count 질의보다 한 행을 더 읽는 편이 싸다.</p>
     * <p>Fetches <b>one more</b> than the page size: determining whether a further page exists
     * requires knowing something lies beyond the boundary, and reading one extra row is cheaper
     * than a second count query.</p>
     *
     * @return 가져올 행 수 / the fetch size
     */
    // req: FR-RPT-005, ADR-RPT-021
    public int fetchSize() {
        return size + 1;
    }
}
