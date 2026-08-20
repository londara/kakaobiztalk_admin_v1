package com.webcash.iris.biztalk.domain;

/**
 * 톡전송 거래내역 한 행 — 화면이 바인딩하는 9개 컬럼만.
 * One 톡전송 거래내역 row: exactly the nine columns the screen binds.
 *
 * <h2>이 레코드의 필드 수가 보안 통제인 이유 / why the field count is a security control</h2>
 * <p>{@code FT_APITR_HSTR} 는 이 화면의 테이블이 아니다 — <b>전체 핀테크 API 의 거래
 * 로그</b>이며, 25개 컬럼 중에는 {@code FIN_ACNO}, {@code ACNO}, {@code CANO},
 * {@code FIN_CARD}, {@code TRAM}, {@code BRNO}, {@code INTT_DMND_TTNO},
 * {@code RSPN_TLGR_CNTN} 이 있다 — 계좌번호, 카드번호, 거래금액, 사업자번호, 응답 전문
 * 원문이다. 이 화면은 그중 무엇과도 관계가 없다.</p>
 * <p>{@code FT_APITR_HSTR} is not this screen's table — it is <b>the transaction log for the entire
 * fintech API estate</b>, and among its 25 columns are {@code FIN_ACNO}, {@code ACNO},
 * {@code CANO}, {@code FIN_CARD}, {@code TRAM}, {@code BRNO}, {@code INTT_DMND_TTNO} and
 * {@code RSPN_TLGR_CNTN}: account numbers, card numbers, amounts, business numbers and raw response
 * telegrams. This screen has business with none of them.</p>
 *
 * <p>CONST-SEC-T01 은 그 배제를 <b>규칙</b>으로 만든다. 이 레코드가 9개 필드인 것,
 * {@code resultMap} 이 닫혀 있는 것, 응답 필드 집합을 정확히 단언하는 계약 테스트가 있는 것,
 * 이 패키지에 {@code SELECT *} 가 금지된 것 — 네 가지가 함께 그것을 <b>현재 SELECT 목록의
 * 우연이 아니라 구조</b>로 만든다.</p>
 * <p>CONST-SEC-T01 makes that exclusion a <b>rule</b>. Four things enforce it together: this record
 * has nine components, the {@code resultMap} is closed, a contract test asserts the response's field
 * set exactly, and {@code SELECT *} is prohibited in this package. Structure, rather than an
 * accident of the current SELECT list.</p>
 *
 * <h2>{@code detailAvailable} 가 서버에서 오는 이유 / why {@code detailAvailable} is server-computed</h2>
 * <p>레거시는 브라우저가 {@code API_SVC_CD.indexOf("KKO") != -1 && PRSU == 1} 로 링크 여부를
 * 정했고, 서버는 네 개의 정확한 코드만 처리했다. 두 규칙이 어긋나 {@code ADV_KKO_AT_SEND2}
 * 행은 <b>링크는 걸리고 팝업은 비어</b> 있었다(D-T13). 이제 서버가 계산해 행에 담아 보내므로
 * 두 판단이 어긋날 수 없다.</p>
 * <p>The legacy let the browser decide with {@code API_SVC_CD.indexOf("KKO") != -1 && PRSU == 1}
 * while the server handled four exact codes. The two disagreed, so an {@code ADV_KKO_AT_SEND2} row
 * was <b>linked and its popup empty</b> (D-T13). The server now computes it and ships it on the row,
 * so the two cannot disagree.</p>
 *
 * @param transactionDate  일자 {@code TRDD} / the transaction date
 * @param institutionCode  기관코드 {@code FINTECH_ISCD} / the institution code
 * @param institutionName  기관명 — 조인으로 해석, 미해석 시 표식 / the institution name, resolved by join
 * @param transactionNo    거래고유번호 {@code IS_TUNO} / the transaction serial
 * @param apiServiceCode   API {@code API_SVC_CD} / the API service code
 * @param statusCode       상태 {@code PRSU} 원값 / the raw status code
 * @param responseCode     응답코드 {@code FINTECH_RPCD} / the response code
 * @param registeredAt     등록시각 {@code RGDT} / when the transaction was recorded
 * @param completedAt      완료시각 {@code LAST_AMDT} / when it last changed
 * @param detailAvailable  상세 조회 가능 여부 — 서버 계산 / whether detail is available, server-computed
 *
 * // source: biztalk_admin_30.js — drawGrid() colDefs: 일자,기관코드,기관명,거래고유번호,API,상태,응답코드,등록시각,완료시각
 * // source: IDO.KKB_APITR_HSTR_L001 — SELECT TRDD, A.FINTECH_ISCD, (…) AS ISNM, IS_TUNO, API_SVC_CD, PRSU, FINTECH_RPCD, RGDT, LAST_AMDT
 * // req: FR-TLK-003, FR-TLK-011, FR-TLK-012, FR-TLK-013, CONST-SEC-T01
 */
public record TalkHistoryRow(
        String transactionDate,
        String institutionCode,
        String institutionName,
        String transactionNo,
        String apiServiceCode,
        String statusCode,
        String responseCode,
        String registeredAt,
        String completedAt,
        boolean detailAvailable
) {

    /**
     * 기관명이 해석되지 않았음을 나타내는 표식. / The marker for an unresolved institution name.
     *
     * <p>레거시는 행마다 상관 서브쿼리로 기관명을 조회했고, 일치하지 않으면 NULL 이 되어
     * <b>빈 칸</b>으로 렌더링되었다 — 조회가 실패했다는 사실이 어디에도 드러나지 않았다
     * (D-T26). 빈 칸과 "이름 없는 기관"은 구분되어야 한다.</p>
     * <p>The legacy resolved the name with a per-row correlated subquery; an unmatched code yielded
     * NULL and rendered as a <b>blank cell</b>, with nothing indicating the lookup had failed
     * (D-T26). A blank and "an institution with no name" must be distinguishable.</p>
     */
    // req: FR-TLK-011
    public static final String UNRESOLVED_INSTITUTION = "(미등록 기관)";

    /**
     * 표시용 기관명을 반환한다. 해석되지 않으면 코드와 표식을 함께 반환한다.
     * Returns the display name, falling back to the code plus a marker when unresolved.
     *
     * @return 기관명 또는 "코드 (미등록 기관)" / the name, or "code (미등록 기관)"
     */
    // req: FR-TLK-011
    public String institutionDisplay() {
        if (institutionName == null || institutionName.isBlank()) {
            return institutionCode + " " + UNRESOLVED_INSTITUTION;
        }
        return institutionName;
    }

    /**
     * 표시용 상태 라벨을 반환한다. / Returns the display label for the status.
     *
     * <p>미인식 코드는 원값을 그대로 반환한다 — {@link TalkStatus#labelOf(String)} 참조.</p>
     * <p>An unrecognised code is returned verbatim; see {@link TalkStatus#labelOf(String)}.</p>
     *
     * @return 라벨 / the label
     */
    // req: FR-TLK-004, NFR-USE-T01
    public String statusLabel() {
        return TalkStatus.labelOf(statusCode);
    }
}
