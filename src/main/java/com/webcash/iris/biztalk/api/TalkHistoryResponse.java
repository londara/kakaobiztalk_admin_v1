package com.webcash.iris.biztalk.api;

import com.webcash.iris.biztalk.domain.PagedResult;
import com.webcash.iris.biztalk.domain.TalkHistoryRow;
import java.util.List;

/**
 * 톡전송 거래내역 조회 응답. / The 톡전송 transaction-history query response.
 *
 * <h2>이 타입이 도메인 레코드를 그대로 내보내지 않는 이유 / why the domain record is not returned directly</h2>
 * <p>응답의 필드 집합이 <b>정확히</b> 무엇인지가 이 슬라이스의 보안 통제 하나이기 때문이다.
 * {@code FT_APITR_HSTR} 는 전체 핀테크 API 의 거래 로그이며 {@code FIN_ACNO}, {@code ACNO},
 * {@code CANO}, {@code FIN_CARD}, {@code TRAM}, {@code BRNO}, {@code INTT_DMND_TTNO},
 * {@code RSPN_TLGR_CNTN} 을 담고 있다. 이 경계를 명시적인 타입으로 두면 계약 테스트가 필드
 * 집합을 정확히 단언할 수 있고, 컬럼이 하나 추가되는 순간 <b>빌드가 실패</b>한다 — 리뷰가
 * 잡아주기를 기다리지 않는다(CONST-SEC-T01).</p>
 * <p>Because <b>exactly</b> which fields the response carries is one of this slice's security controls.
 * {@code FT_APITR_HSTR} is the whole fintech estate's transaction log and holds account numbers, card
 * numbers, amounts, business numbers and raw response telegrams. Making the boundary an explicit type
 * lets a contract test assert the field set exactly, so an added column <b>fails the build</b> rather
 * than waiting for review to catch it (CONST-SEC-T01).</p>
 *
 * <p>상태와 기관명은 <b>원값과 표시값을 함께</b> 보낸다. 라벨만 보내면 운영자가 제공업체에
 * 코드를 그대로 인용할 수 없고, 원값만 보내면 화면이 라벨을 다시 만들어야 한다 — 후자가
 * 레거시의 D-T29 다(코드 테이블은 필터를, 하드코딩된 분기는 컬럼을 만들었다).</p>
 * <p>Status and institution carry <b>both the raw and the displayed value</b>. Labels alone would stop
 * an operator quoting a code to a provider; raw values alone would make the screen rebuild the labels —
 * and that is the legacy's D-T29, where a code table drove the filter and a hardcoded branch the
 * column.</p>
 *
 * @param rows       이 페이지의 행 / the rows on this page
 * @param totalCount 전체 건수 / the total matching count
 * @param page       0부터 시작하는 페이지 번호 / the zero-based page number
 * @param size       페이지 크기 / the page size
 * @param totalPages 전체 페이지 수 / the total page count
 *
 * // req: FR-TLK-003, FR-TLK-005, FR-TLK-012, NFR-USE-T01, CONST-SEC-T01
 */
public record TalkHistoryResponse(
        List<Row> rows,
        int totalCount,
        int page,
        int size,
        int totalPages
) {

    /**
     * 도메인 결과를 응답으로 변환한다. / Converts a domain result into a response.
     *
     * @param result 도메인 결과 / the domain result
     * @return 응답 / the response
     */
    // req: FR-TLK-003, FR-TLK-005
    public static TalkHistoryResponse from(PagedResult<TalkHistoryRow> result) {
        List<Row> rows = result.rows().stream().map(Row::from).toList();
        return new TalkHistoryResponse(
                rows, result.totalCount(), result.page(), result.size(), result.totalPages());
    }

    /**
     * 응답 행 하나 — 화면이 바인딩하는 필드만.
     * One response row: exactly the fields the screen binds.
     *
     * @param transactionDate   일자 / the transaction date
     * @param institutionCode   기관코드 / the institution code
     * @param institutionName   표시용 기관명. 미해석 시 코드와 표식 / the display name, code plus marker when unresolved
     * @param transactionNo     거래고유번호 / the transaction serial
     * @param apiServiceCode    API 서비스 코드 / the API service code
     * @param statusCode        상태 원값 / the raw status code
     * @param statusLabel       상태 표시 라벨 / the status display label
     * @param responseCode      응답코드 / the response code
     * @param registeredAt      등록시각 / when recorded
     * @param completedAt       완료시각 / when last changed
     * @param detailAvailable   상세 조회 가능 여부 / whether detail is available
     *
     * // req: FR-TLK-003, FR-TLK-004, FR-TLK-011, FR-TLK-013, CONST-SEC-T01
     */
    public record Row(
            String transactionDate,
            String institutionCode,
            String institutionName,
            String transactionNo,
            String apiServiceCode,
            String statusCode,
            String statusLabel,
            String responseCode,
            String registeredAt,
            String completedAt,
            boolean detailAvailable
    ) {

        /**
         * 도메인 행을 응답 행으로 변환한다. / Converts a domain row into a response row.
         *
         * @param row 도메인 행 / the domain row
         * @return 응답 행 / the response row
         */
        // req: FR-TLK-004, FR-TLK-011, FR-TLK-013
        public static Row from(TalkHistoryRow row) {
            return new Row(
                    row.transactionDate(),
                    row.institutionCode(),
                    // 미해석 기관은 코드와 표식을 함께 보낸다 — 빈 칸이 아니다(D-T26).
                    // An unresolved institution ships the code plus a marker, never a blank (D-T26).
                    row.institutionDisplay(),
                    row.transactionNo(),
                    row.apiServiceCode(),
                    row.statusCode(),
                    row.statusLabel(),
                    row.responseCode(),
                    row.registeredAt(),
                    row.completedAt(),
                    row.detailAvailable());
        }
    }
}
