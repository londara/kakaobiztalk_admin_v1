package com.webcash.iris.biztalk.domain;

import java.util.Comparator;
import java.util.Objects;

/**
 * 집계 행의 식별자이자 <b>정렬 키</b>. / An aggregate row's identity and <b>sort key</b>.
 *
 * <p>{@code KKB_APITR_SMTN} 의 기본키({@code TRDD} + {@code IS_CD})이며, 두 출처에서
 * 동일하다. 그 동일성이 병합을 가능하게 한다 — 같은 키로 정렬된 두 스트림은 각 스트림의
 * 머리만 들고도 합칠 수 있다(ADR-RPT-021).</p>
 * <p>The aggregate's primary key, identical in both sources. That sameness is what makes the
 * merge possible: two streams ordered by one key can be combined while holding only each
 * stream's head (ADR-RPT-021).</p>
 *
 * <h2>정렬이 표시가 아니라 정확성인 이유 / why ordering is correctness, not presentation</h2>
 * <p>레거시에서 정렬은 장식이었고 환경마다 달랐다 — 운영은 Java 비교자로 내림차순, 그 외
 * 환경은 SQL {@code ORDER BY TRDD} 오름차순이었으며 같은 날짜 안의 순서는 어느 쪽도
 * 정의하지 않았다(D-R7). 병합에서는 <b>두 스트림이 같은 순서로 도착해야만 합계가
 * 맞는다.</b> 순서가 어긋나면 오류가 아니라 <b>그럴듯한 틀린 숫자</b>가 나온다.</p>
 * <p>In the legacy, ordering was cosmetic and differed by environment — production sorted
 * descending in Java, everything else ascending in SQL, and neither defined the order within a
 * date (D-R7). In a merge, <b>the sums are only right if both streams arrive in the same
 * order.</b> A mismatch produces plausible wrong numbers rather than an error.</p>
 *
 * @param tradeDate       거래일자 {@code YYYYMMDD} / the trade date
 * @param institutionCode 이용기관 코드 / the institution code
 *
 * // source: IDO.KKB_APITR_SMTN_L001 — ORDER BY TRDD
 * // req: FR-RPT-006, FR-RPTS-003, ADR-RPT-021
 */
public record AggregateKey(String tradeDate, String institutionCode)
        implements Comparable<AggregateKey> {

    /**
     * 표시 순서 비교자 — 일자 내림차순, 기관코드 오름차순.
     * The display-order comparator: date descending, institution code ascending.
     *
     * <p>이 순서는 매퍼의 {@code ORDER BY TRDD DESC, IS_CD ASC} 와 <b>반드시</b> 일치해야
     * 한다. 둘이 어긋나면 병합이 조용히 틀린다.</p>
     * <p>This must match the mapper's {@code ORDER BY TRDD DESC, IS_CD ASC} exactly; a
     * divergence makes the merge silently wrong.</p>
     */
    // req: FR-RPT-006
    public static final Comparator<AggregateKey> DISPLAY_ORDER =
            Comparator.comparing(AggregateKey::tradeDate, Comparator.reverseOrder())
                    .thenComparing(AggregateKey::institutionCode);

    /**
     * 키를 생성한다. / Creates the key.
     *
     * @param tradeDate       거래일자 / the trade date
     * @param institutionCode 이용기관 코드 / the institution code
     */
    public AggregateKey {
        Objects.requireNonNull(tradeDate, "tradeDate");
        Objects.requireNonNull(institutionCode, "institutionCode");
    }

    /**
     * 표시 순서로 비교한다. / Compares in display order.
     *
     * <p>{@code compareTo < 0} 은 "먼저 표시된다"를 뜻한다.</p>
     * <p>{@code compareTo < 0} means "displayed first".</p>
     *
     * @param other 비교 대상 / the other key
     * @return 음수면 먼저 / negative when this sorts first
     */
    // req: FR-RPT-006
    @Override
    public int compareTo(AggregateKey other) {
        return DISPLAY_ORDER.compare(this, other);
    }
}
