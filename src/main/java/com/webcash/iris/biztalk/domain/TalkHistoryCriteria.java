package com.webcash.iris.biztalk.domain;

import com.webcash.iris.common.tenant.PrincipalScope;
import java.util.Set;

/**
 * 검증을 통과한 톡전송 내역 조회 조건. / 톡전송 내역 search criteria that have passed validation.
 *
 * <h2>이 타입이 존재하는 이유 / why this type exists</h2>
 * <p>매퍼가 <b>검증되지 않은 값을 볼 수 없게</b> 하기 위해서다. 레거시에서 이 경계가 없었던
 * 결과가 두 가지다. 목록 서비스는 브라우저가 만든 {@code 999999} 를 그대로 받았고(D-T24),
 * 다운로드 액션은 계약에 선언된 열 개의 파라미터를 <b>전부 {@code request.getParameter} 로
 * 직접 읽어</b> 선언된 길이와 문자 규칙을 통째로 우회했다(D-T14) — 그 우회가
 * {@code Content-Disposition} 응답 분할(D-T4)이 도달 가능해진 경로이기도 하다.</p>
 * <p>So that <b>a mapper cannot see an unvalidated value</b>. The absence of this boundary produced
 * two things in the legacy. The list service accepted the browser's {@code 999999} verbatim (D-T24),
 * and the download action read all ten of its declared parameters <b>straight from
 * {@code request.getParameter}</b>, bypassing every declared length and character rule (D-T14) —
 * which is also how the {@code Content-Disposition} response splitting (D-T4) became reachable.</p>
 *
 * <p>내보내기(스프린트 T2)는 <b>같은 타입</b>을 받는다. FR-TLKX-001 이 "내보내기 결과가 화면
 * 결과와 같다"를 <b>집합 동일성</b>으로 검증 가능하게 만드는 것이 바로 이 공유다 — 조건이
 * 같은 타입이고 질의가 같은 경로이므로, 둘이 어긋나려면 타입을 바꿔야 한다.</p>
 * <p>The export (Sprint T2) takes <b>this same type</b>. That sharing is what makes FR-TLKX-001 — the
 * export equals the screen — verifiable as a <b>set equality</b>: the criteria are one type and the
 * query is one path, so diverging would require changing the type.</p>
 *
 * @param window            검증된 일자·시각 구간 / the validated date and time window
 * @param scope             세션에서 결정된 기관 범위 / the institution scope, decided from the session
 * @param serial            거래일련번호. 조건 없으면 null / the transaction serial, null when unfiltered
 * @param statusCode        상태 코드. 조건 없으면 null / the status code, null when unfiltered
 * @param apiServiceCode    API 서비스 코드. 조건 없으면 null / the API service code, null when unfiltered
 * @param inScopeApiCodes   조회 범위에 포함되는 API 서비스 코드 집합 / the API service codes within scope
 * @param page              0부터 시작하는 페이지 번호 / the zero-based page number
 * @param size              페이지 크기 / the page size
 *
 * // source: biztalk_admin_30_view.jsp / biztalk_admin_30.js — getDat()
 * // req: FR-TLK-001, FR-TLK-002, FR-TLK-005, FR-TLK-009, FR-TLK-010, FR-TLK-014
 */
public record TalkHistoryCriteria(
        TalkPeriodPolicy.TalkWindow window,
        PrincipalScope scope,
        TransactionSerial serial,
        String statusCode,
        String apiServiceCode,
        Set<String> inScopeApiCodes,
        int page,
        int size
) {

    /** 기본 페이지 크기. NFR-PERF-T01 이 측정하는 크기와 같다. / Default page size, as measured by NFR-PERF-T01. */
    public static final int DEFAULT_SIZE = 100;

    /** 최대 페이지 크기. / Maximum page size. */
    public static final int MAX_SIZE = 500;

    /**
     * 매퍼에 바인딩할 거래일련번호를 반환한다. / Returns the serial to bind for the mapper.
     *
     * <p>{@code null} 이면 매퍼가 술어를 만들지 않는다. 조건이 <b>없는 것</b>과 <b>빈
     * 문자열</b>은 다르게 다뤄야 한다 — 레거시는 빈 문자열을 술어에 넣고 SQL 쪽
     * {@code CASE WHEN :x = '' THEN 1=1} 로 무력화했는데, 그 형태는 인덱스를 쓸 수 없게
     * 만든다.</p>
     * <p>{@code null} means the mapper builds no predicate. The <b>absence</b> of a filter and an
     * <b>empty string</b> must be handled differently: the legacy bound the empty string and
     * neutralised it in SQL with {@code CASE WHEN :x = '' THEN 1=1}, a shape that prevents index
     * use.</p>
     *
     * @return 0 으로 채운 거래일련번호 또는 null / the padded serial, or null
     */
    // req: FR-TLK-009
    public String serialForMapper() {
        return serial == null ? null : serial.transactionForm();
    }

    /**
     * 매퍼에 바인딩할 행 오프셋을 반환한다. / Returns the row offset to bind for the mapper.
     *
     * <p>SQL 안에서 {@code OFFSET #{page} * #{size}} 로 계산하지 않는다. 산술을 SQL 로
     * 넘기면 데이터베이스가 바인딩 파라미터 곱셈을 어떻게 다루는지에 의존하게 되고, 그것은
     * 이 슬라이스가 이미 한 번 대가를 치른 종류의 암묵적 계약이다 — 레거시의
     * {@code RGDT BETWEEN '…999999'} 는 컬럼이 문자형이라는 가정에 기대고 있었다(D-T24).</p>
     * <p>Not computed in SQL as {@code OFFSET #{page} * #{size}}: pushing the arithmetic down makes
     * the query depend on how the database handles multiplication of bind parameters, and that is the
     * class of implicit contract this slice has already paid for once — the legacy's
     * {@code RGDT BETWEEN '…999999'} rested on the column being character-typed (D-T24).</p>
     *
     * @return 0 이상의 오프셋 / a non-negative offset
     */
    // req: FR-TLK-005
    public int offset() {
        return page * size;
    }

    /**
     * 감사 기록에 남길 조건 설명을 반환한다. / Builds an audit description of the criteria.
     *
     * <p>거래일련번호와 기관코드는 PII 가 아니므로 그대로 남긴다. 이 화면의 조건에는
     * 전화번호가 없다 — 있는 화면(문자내역)은 해시해서 남긴다.</p>
     * <p>Serials and institution codes are not PII and are recorded verbatim. This screen's criteria
     * contain no phone number; the screen that has one (문자내역) hashes it.</p>
     *
     * @return 설명 / the description
     */
    // req: FR-AZ-T05
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append("scope=").append(scope.describe());
        sb.append(" window=").append(window.describe());
        if (serial != null) {
            sb.append(" serial=").append(serial.canonical());
        }
        if (statusCode != null) {
            sb.append(" status=").append(statusCode);
        }
        if (apiServiceCode != null) {
            sb.append(" api=").append(apiServiceCode);
        }
        sb.append(" page=").append(page).append(" size=").append(size);
        if (scope.overrideAttempted()) {
            // 무시된 기관 지정 시도도 기록한다 — 탐색 행위를 사후에 식별할 수 있다.
            // A rejected institution override is recorded too, so probing is identifiable later.
            sb.append(" overrideAttempted=true");
        }
        return sb.toString();
    }

    /**
     * 페이지 크기를 정규화한다. / Normalises a requested page size.
     *
     * @param requested 요청 크기. null 이면 기본값 / the requested size, null for the default
     * @return 1 이상 {@link #MAX_SIZE} 이하의 크기 / a size between 1 and {@link #MAX_SIZE}
     */
    // req: FR-TLK-005
    public static int normaliseSize(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(requested, MAX_SIZE);
    }
}
