package com.webcash.iris.biztalk.domain;

/**
 * 한 채널의 네 가지 건수. / One channel's four counters.
 *
 * <p>레거시는 이 넷 중 셋만 보여줬다. 그리드는 전체·성공·실패를, 엑셀 총합 시트는
 * 전체·성공만 표시했고 <b>처리중은 조회되고 계약에 선언되었으나 어디에도 나타나지
 * 않았다</b>(D-R14). 그래서 어떤 화면에서도 전체 = 성공 + 실패 가 성립하지 않았고, 숫자를
 * 더해 본 사람에게 보고서는 틀려 보였다.</p>
 * <p>The legacy showed three of these four: the grid displayed 전체/성공/실패 and the Excel
 * summary sheet only 전체/성공, while <b>처리중 was queried, declared on the contract, and
 * rendered nowhere</b> (D-R14). 전체 = 성공 + 실패 therefore never held, and to anyone who
 * added the columns up the report looked wrong.</p>
 *
 * @param total    전체 / total requested
 * @param success  성공 / succeeded
 * @param failed   실패 / failed
 * @param inFlight 처리중 / still in flight
 *
 * // source: KKB_APITR_SMTN — *_CNT, *_SCS_CNT, *_FAIL_CNT, *_PCSNG_CNT
 * // req: FR-RPT-009, FR-RPT-010, FR-RPT-011, AMB-R02
 */
public record ChannelCounters(long total, long success, long failed, long inFlight) {

    /** 모두 0 인 값. 병합의 항등원. / All zeroes; the merge identity. */
    public static final ChannelCounters ZERO = new ChannelCounters(0, 0, 0, 0);

    /**
     * NULL 을 0 으로 바꾸어 생성한다. / Creates the value, mapping NULL to zero.
     *
     * <p>레거시 SQL 에는 {@code COALESCE} 가 한 군데도 없었다. 컬럼 하나가 NULL 이면
     * {@code (FTIMG_CNT - FTIMGWI_CNT)} 전체가 NULL 이 되고,
     * {@code sum(AT_CNT + FT_CNT + ...)} 는 <b>그 기관의 총계 전체</b>를 NULL 로 만들었다.
     * 셀은 0 이 아니라 빈칸으로 보였다(D-R11).</p>
     * <p>The legacy SQL had no {@code COALESCE} anywhere. One NULL column nulls the whole
     * {@code (FTIMG_CNT - FTIMGWI_CNT)} expression, and {@code sum(AT_CNT + FT_CNT + ...)} nulls
     * <b>an institution's entire total</b>. The cell rendered blank, not zero (D-R11).</p>
     *
     * <p>매퍼가 SQL 에서 이미 {@code COALESCE} 하지만 여기서도 방어한다 — 두 출처의 매퍼가
     * 두 벌이므로 한쪽만 고치는 실수가 가능하다.</p>
     * <p>The mappers already {@code COALESCE} in SQL; this guards again because there are two of
     * them and fixing only one is a possible mistake.</p>
     *
     * @param total    전체 / total
     * @param success  성공 / success
     * @param failed   실패 / failed
     * @param inFlight 처리중 / in flight
     * @return 생성된 값 / the value
     */
    // req: FR-RPT-011
    public static ChannelCounters of(Long total, Long success, Long failed, Long inFlight) {
        return new ChannelCounters(
                total == null ? 0L : total,
                success == null ? 0L : success,
                failed == null ? 0L : failed,
                inFlight == null ? 0L : inFlight);
    }

    /**
     * 두 출처의 건수를 더한다. / Adds another source's counters.
     *
     * <p>발송구분이 전체일 때 같은 일자·기관의 API 집계와 대량 집계를 합치는 연산이다
     * (FR-RPTS-003). 레거시는 두 결과를 이어 붙이기만 해 한 기관이 하루에 두 행으로
     * 나타났다.</p>
     * <p>The operation that combines the API and bulk aggregates for one 일자 + 기관 when the
     * source filter is 전체 (FR-RPTS-003). The legacy merely concatenated them, so one
     * institution appeared twice for one day.</p>
     *
     * @param other 더할 값 / the other counters
     * @return 합계 / the sum
     */
    // req: FR-RPTS-003
    public ChannelCounters plus(ChannelCounters other) {
        if (other == null) {
            return this;
        }
        return new ChannelCounters(
                total + other.total,
                success + other.success,
                failed + other.failed,
                inFlight + other.inFlight);
    }

    /**
     * 전체 = 성공 + 실패 + 처리중 이 성립하는지 반환한다.
     * Whether 전체 = 성공 + 실패 + 처리중 holds.
     *
     * <p>이 항등식은 표시 규칙이 아니라 <b>데이터 품질 탐지기</b>다. 이 슬라이스는 집계를
     * 스스로 만들지 않고(CONST-DATA-R01) 배치가 만든 값을 읽기만 하는데, 그 배치는 실패를
     * 삼키고 성공을 보고한다(D-R27). 우리가 가진 가장 값싼 검사가 이 덧셈이다.</p>
     * <p>The identity is a <b>data-quality detector</b>, not a display rule. This slice does not
     * produce the aggregate (CONST-DATA-R01); it reads what the batch wrote, and that batch
     * swallows failures while reporting success (D-R27). This addition is the cheapest check
     * available to us.</p>
     *
     * @return 성립 여부 / true when the identity holds
     */
    // req: FR-RPT-010
    public boolean reconciles() {
        return total == success + failed + inFlight;
    }

    /**
     * 음수 건수가 있는지 반환한다. / Whether any counter is negative.
     *
     * <p>친구톡 일반 이미지는 {@code FTIMG − FTIMGWI} 로 계산되므로, 와이드 건수가 전체
     * 이미지 건수보다 크면 음수가 된다 — 배치가 만든 데이터가 스스로 모순된다는 신호다.</p>
     * <p>The normal-image figure is {@code FTIMG − FTIMGWI}, so it goes negative when the wide
     * count exceeds the total image count — a signal that the batch's data contradicts itself.</p>
     *
     * @return 음수 포함 여부 / true when any counter is negative
     */
    // req: FR-RPT-010, CONST-BIZ-R01
    public boolean hasNegative() {
        return total < 0 || success < 0 || failed < 0 || inFlight < 0;
    }
}
