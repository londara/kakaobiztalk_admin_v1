package com.webcash.iris.biztalk.domain;

import com.webcash.iris.biztalk.infra.db.TalkHistoryMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API 분류 대조 — 좁힌 화면이 무엇을 빠뜨렸는지 드러낸다.
 * API classification reconciliation: surfacing what the narrowed screen missed.
 *
 * <h2>이 클래스가 존재하는 이유 / why this class exists</h2>
 * <p>PM 결정 SCOPE-T01 이 화면을 BizTalk API 로 좁혔다. 그 결정은 레거시에 없던 실패 양식을
 * 하나 만든다 — 그리고 그 양식은 <b>이전 것보다 조용하다</b>.</p>
 * <ul>
 *   <li><b>과대 포함</b>은 레거시의 동작이고 <b>보인다</b>: 운영자가 자기 화면에 있을 이유가
 *       없는 행을 보고 말한다.</li>
 *   <li><b>과소 포함</b>은 <b>보이지 않는다</b>: 실제 BizTalk 거래가 그냥 없고, 필터가
 *       그것을 제거했다는 표시가 어디에도 없다.</li>
 * </ul>
 * <p>PM ruling SCOPE-T01 narrowed the screen to BizTalk APIs. That decision creates a failure mode the
 * legacy did not have — and it is <b>quieter</b> than the one it replaces.</p>
 * <ul>
 *   <li><b>Over-inclusion</b> is the legacy's behaviour and it is <b>visible</b>: an operator sees a row
 *       that has no business being there and says so.</li>
 *   <li><b>Under-inclusion</b> is <b>invisible</b>: a real BizTalk transaction is simply absent, with
 *       nothing indicating a filter removed it.</li>
 * </ul>
 *
 * <p>이 대조가 그 조용한 양식을 <b>이전 것만큼 시끄럽게</b> 만든다. 그것이 허용 목록을 쓸 수
 * 있게 하는 조건이며, ADR-TLK-024 결정의 절반이다 — 나머지 절반인 허용 목록만으로는
 * 안전하지 않다(RISK-T01).</p>
 * <p>This reconciliation makes the quiet mode <b>as loud as the one it replaced</b>. That is the
 * condition under which an allow-list is safe to use, and it is half of ADR-TLK-024's decision — the
 * allow-list alone is not sufficient (RISK-T01).</p>
 *
 * <h2>양방향으로 본다 / it looks both ways</h2>
 * <p>설정에는 있으나 거래에 나타나지 않은 코드는 <b>오타 또는 폐기</b>일 수 있고, 거래에는
 * 나타나나 설정에 없는 코드는 <b>분류 누락</b> 후보다. 둘 다 같은 한 번의 질의로 나온다.</p>
 * <p>A code configured but never observed may be <b>a typo or a retirement</b>; a code observed but not
 * configured is a <b>missing-classification</b> candidate. One query answers both.</p>
 *
 * // source: IDO.KKB_APITR_HSTR_L001 — no channel predicate; IDO.KKB_OPENAPI_INFO_L002 — WHERE 1=1
 * // req: FR-TLK-002, ADR-TLK-024, RISK-T01
 */
@Service
public class TalkApiReconciliation {

    private static final Logger log = LoggerFactory.getLogger(TalkApiReconciliation.class);
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

    /**
     * 기본 관측 구간(일). / The default observation window, in days.
     *
     * <p>구간을 <b>반드시</b> 두는 이유는 {@code FT_APITR_HSTR} 가 전체 핀테크 API 의 거래
     * 로그이기 때문이다 — 무한정 {@code GROUP BY} 를 뜨는 것은 이 테이블에 허용되지 않는다
     * (RISK-T07: 이 테이블은 이 프로젝트의 것이 아니다).</p>
     * <p>A window is <b>mandatory</b> because {@code FT_APITR_HSTR} is the whole fintech estate's
     * transaction log: an unbounded {@code GROUP BY} is not acceptable on it (RISK-T07 — the table does
     * not belong to this project).</p>
     */
    public static final int DEFAULT_WINDOW_DAYS = 7;

    private final TalkHistoryMapper mapper;
    private final BizTalkApiRegistry registry;
    private final Clock clock;

    /**
     * 대조기를 생성한다. / Creates the reconciler.
     *
     * @param mapper   거래내역 매퍼 / the transaction-history mapper
     * @param registry BizTalk API 레지스트리 / the BizTalk API registry
     * @param clock    시각 공급자 / the clock
     */
    public TalkApiReconciliation(TalkHistoryMapper mapper,
                                 BizTalkApiRegistry registry,
                                 Clock clock) {
        this.mapper = mapper;
        this.registry = registry;
        this.clock = clock;
    }

    /**
     * 기본 구간에 대해 대조를 수행한다. / Reconciles over the default window.
     *
     * @return 대조 결과 / the reconciliation result
     */
    // req: FR-TLK-002
    @Transactional(readOnly = true)
    public Result reconcile() {
        LocalDate today = LocalDate.now(clock);
        return reconcile(today.minusDays(DEFAULT_WINDOW_DAYS), today);
    }

    /**
     * 지정한 구간에 대해 대조를 수행한다. / Reconciles over the given window.
     *
     * @param from 시작일자 / the start date
     * @param to   종료일자 / the end date
     * @return 대조 결과 / the reconciliation result
     */
    // req: FR-TLK-002, RISK-T01
    @Transactional(readOnly = true)
    public Result reconcile(LocalDate from, LocalDate to) {

        List<TalkHistoryMapper.ObservedApiService> observed =
                mapper.findObservedApiServices(from.format(YYYYMMDD), to.format(YYYYMMDD));

        Set<String> configured = new HashSet<>(registry.codes());
        Set<String> seen = new HashSet<>();
        List<Unclassified> unclassified = new ArrayList<>();

        for (TalkHistoryMapper.ObservedApiService row : observed) {
            seen.add(row.apiServiceCode());
            if (!configured.contains(row.apiServiceCode())) {
                unclassified.add(new Unclassified(row.apiServiceCode(), row.transactionCount()));
            }
        }

        List<String> configuredButUnseen = registry.codes().stream()
                .filter(code -> !seen.contains(code))
                .toList();

        Result result = new Result(from, to, unclassified, configuredButUnseen);
        report(result);
        return result;
    }

    /**
     * 결과를 로그로 남긴다. / Logs the result.
     *
     * <p>과소 포함 후보를 <b>WARN</b> 으로 남긴다 — 보고서가 존재하기만 하고 아무도 읽지
     * 않으면, 조용한 실패 양식을 조용한 보고서로 바꾼 것에 불과하다.</p>
     * <p>Under-inclusion candidates are logged at <b>WARN</b>: a report that merely exists and is never
     * read would have replaced a quiet failure mode with a quiet report.</p>
     */
    private void report(Result result) {
        if (!result.unclassified().isEmpty()) {
            log.warn("BizTalk 분류에 없는 API 서비스 {}건이 {}~{} 구간 거래에 나타났습니다 — "
                            + "분류 누락 후보입니다(RISK-T01, AMB-T03): {} / "
                            + "{} API services absent from the BizTalk classification appeared in "
                            + "transactions between {} and {} — missing-classification candidates: {}",
                    result.unclassified().size(), result.from(), result.to(), result.unclassified(),
                    result.unclassified().size(), result.from(), result.to(), result.unclassified());
        }
        if (!result.configuredButUnseen().isEmpty()) {
            // 오타이거나 폐기된 코드다. 시동 시 존재 검증을 대체하지 않고 보완한다 —
            // 설정된 코드가 조용히 아무것도 매치하지 않는 상태를 드러낸다.
            // Either a typo or a retired code. This complements rather than replaces startup
            // validation: it surfaces a configured code that quietly matches nothing.
            log.info("설정된 BizTalk API 서비스 {}건이 {}~{} 구간 거래에 나타나지 않았습니다 "
                            + "(오타 또는 폐기 가능): {} / "
                            + "{} configured BizTalk API services did not appear between {} and {} "
                            + "(possible typo or retirement): {}",
                    result.configuredButUnseen().size(), result.from(), result.to(),
                    result.configuredButUnseen(),
                    result.configuredButUnseen().size(), result.from(), result.to(),
                    result.configuredButUnseen());
        }
        if (result.clean()) {
            log.info("BizTalk API 분류 대조 결과 불일치 없음 ({}~{}). / "
                    + "BizTalk API classification reconciled clean ({} to {}).",
                    result.from(), result.to(), result.from(), result.to());
        }
    }

    /**
     * 대조 결과. / The reconciliation result.
     *
     * @param from                시작일자 / the start date
     * @param to                  종료일자 / the end date
     * @param unclassified        거래에는 있으나 분류에 없는 코드 / codes observed but not classified
     * @param configuredButUnseen 분류에는 있으나 거래에 없는 코드 / codes classified but not observed
     */
    // req: FR-TLK-002
    public record Result(LocalDate from,
                         LocalDate to,
                         List<Unclassified> unclassified,
                         List<String> configuredButUnseen) {

        /**
         * 불일치가 없는지 반환한다. / Whether the reconciliation found nothing.
         *
         * @return 불일치가 없으면 true / true when both lists are empty
         */
        public boolean clean() {
            return unclassified.isEmpty() && configuredButUnseen.isEmpty();
        }
    }

    /**
     * 분류에 없는 관측 코드와 그 건수. / An observed-but-unclassified code and its count.
     *
     * @param apiServiceCode   {@code API_SVC_CD}
     * @param transactionCount 해당 구간의 거래 건수 / the transaction count in the window
     */
    // req: FR-TLK-002
    public record Unclassified(String apiServiceCode, long transactionCount) {

        @Override
        public String toString() {
            return apiServiceCode + "(" + transactionCount + ")";
        }
    }
}
