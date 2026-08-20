package com.webcash.iris.biztalk.alimtalk.config;

import com.webcash.iris.biztalk.alimtalk.domain.TranIdGenerator;
import com.webcash.iris.biztalk.alimtalk.infra.vendor.SenderProfileKeyResolver;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 알림톡 슬라이스의 스프링 배선. / Spring wiring for the AlimTalk slice.
 *
 * <h2>왜 이 클래스가 필요했는가 / why this class exists</h2>
 * <p>{@link TranIdGenerator} 는 순번 공급원을 {@link TranIdGenerator.SequenceSource} 로 추상화해
 * 두었고, 그 구현(DB 시퀀스)은 A2-02(DDL)에 속한다. 그 결과 컨테이너가 올라올 때 주입할 빈이
 * 없어 애플리케이션이 기동에 실패했다:</p>
 * <p>{@link TranIdGenerator} abstracts its sequence behind {@link TranIdGenerator.SequenceSource},
 * whose real implementation (a DB sequence) belongs to A2-02. With no bean to inject, the container
 * failed to start:</p>
 * <pre>
 *   Parameter 0 of constructor in ...AlimTalkController required a bean of type
 *   '...TranIdGenerator' that could not be found.
 * </pre>
 *
 * <p>인터페이스 뒤로 미루는 것은 옳았지만 <b>배선을 함께 남기지 않은 것이 결함</b>이었다.
 * 단위 테스트는 생성자를 직접 호출하므로 통과했고, 애플리케이션을 실제로 기동해 보기
 * 전까지는 드러나지 않았다 — 이 슬라이스가 반복해서 마주친 유형이다: <b>실행되지 않는 것은
 * 검증되지 않는다.</b></p>
 * <p>Deferring behind an interface was right; <b>not leaving the wiring behind was the defect</b>.
 * The unit tests call the constructor directly so they passed, and nothing surfaced until the
 * application was actually started — the pattern this slice keeps meeting: <b>what does not run is
 * not verified.</b></p>
 *
 * // source: biztalk_admin_50_s001_act.jsp — "33" + hh24miss (D-A25)
 * // req: FR-ATS-008, FR-ATS-010, CONST-DATA-A04
 */
@Configuration
public class AlimTalkConfig {

    private static final Logger log = LoggerFactory.getLogger(AlimTalkConfig.class);

    /** 운영 환경 구분자. / The production environment discriminator. */
    static final char PRODUCTION = 'A';

    /**
     * 거래고유번호 생성기를 등록한다. / Registers the transaction-id generator.
     *
     * @param environment 환경 구분자 / the environment discriminator
     * @param sequences   순번 공급원 / the sequence source
     * @param clock       {@code ClockConfig} 가 제공하는 시계 / the clock provided by {@code ClockConfig}
     * @return 생성기 / the generator
     *
     * // req: FR-ATS-008, FR-ATS-010
     */
    @Bean
    public TranIdGenerator tranIdGenerator(
            @Value("${iris.alimtalk.environment:T}") char environment,
            TranIdGenerator.SequenceSource sequences,
            Clock clock) {
        return new TranIdGenerator(environment, sequences, clock);
    }

    /**
     * 발신프로필키 해결기를 등록한다. / Registers the sender profile key resolver.
     *
     * <p>값은 환경에서만 온다. {@code application.yml} 에 기본값을 두지 않는 이유는 그 파일의
     * 머리말이 스스로 적어 둔 그대로다 — 체크인된 기본값이 레거시를 자격증명 유출로
     * 데려갔다(D-A24).</p>
     * <p>Values come only from the environment. No default sits in {@code application.yml}, for the reason
     * that file's own header states: a checked-in default is how the legacy leaked credentials (D-A24).</p>
     *
     * <p>설정 예 / configuration example:</p>
     * <pre>
     *   IRIS_ATK_PROFILE_KEYS_K00001=…      # 기관별 / per institution
     *   IRIS_ATK_SHARED_PROFILE_KEY=…       # A1-04 가 "공유" 로 답한 경우에만 / only if A1-04 says shared
     * </pre>
     *
     * @param perInstitution 이용기관별 키 / per-institution keys
     * @param shared         공유 키 / the shared key
     * @return 해결기 / the resolver
     *
     * // req: FR-ATS-003, FR-AZ-A05, NFR-SEC-CRED-A01
     */
    /**
     * 벤더용 직렬화기를 등록한다. / Registers the vendor serialiser.
     *
     * <p>발송 설정과 <b>무관하게</b> 등록한다. 컨트롤러 생성자가 이 타입을 요구하므로, 발송이
     * 꺼져 있을 때 빈이 없으면 애플리케이션이 기동조차 하지 못한다 — 그것이 이 슬라이스에서
     * 이미 한 번 일어난 결함이다({@link TranIdGenerator} 빈 누락).</p>
     * <p>Registered <b>regardless</b> of the dispatch setting: the controller's constructor requires the
     * type, so a missing bean would stop the application from starting at all when dispatch is off —
     * the defect that already occurred once in this slice (the missing {@link TranIdGenerator} bean).</p>
     *
     * <p>이 빈이 존재한다는 것이 마스킹 우회 경로를 여는 것은 아니다. CI 규칙
     * {@code Confine the raw PII accessor to the vendor boundary} 가
     * {@code exposeForVendorCall()} 을 {@code infra/vendor/} 안으로 묶어 두므로, 이 매퍼의 출력을
     * 다른 곳에서 흉내 낼 수는 없다.</p>
     * <p>Its existence does not open a masking bypass: the CI rule
     * {@code Confine the raw PII accessor to the vendor boundary} keeps
     * {@code exposeForVendorCall()} inside {@code infra/vendor/}, so this mapper's output cannot be
     * reproduced elsewhere.</p>
     *
     * @return 직렬화기 / the serialiser
     *
     * // req: FR-ATS-004, FR-AZ-A05, NFR-SEC-PII-A01
     */
    @Bean
    public com.webcash.iris.biztalk.alimtalk.infra.vendor.VendorPayloadMapper vendorPayloadMapper() {
        return new com.webcash.iris.biztalk.alimtalk.infra.vendor.VendorPayloadMapper();
    }

    @Bean
    public SenderProfileKeyResolver senderProfileKeyResolver(
            @Value("#{${iris.alimtalk.profile-keys:{:}}}") Map<String, String> perInstitution,
            @Value("${iris.alimtalk.shared-profile-key:}") String shared) {
        return new SenderProfileKeyResolver(perInstitution, shared);
    }

    /**
     * Sprint A1 임시 순번 공급원 — <b>운영에서는 기동을 거부한다</b>.
     * The Sprint A1 stand-in sequence source, which <b>refuses to start in production</b>.
     *
     * <p>진짜 구현은 {@code (is_cd, date)} 별 DB 시퀀스이며 A2-02 에 속한다. 제약이 있는 곳에서
     * 동시성을 처리하려는 의도이고, 화면 50 과의 공존 구간에서 두 작성자가 같은 값을 내지 않게
     * 하려면 그래야 한다(ADR-ATK-026).</p>
     * <p>The real implementation is a DB sequence per {@code (is_cd, date)} and belongs to A2-02, so that
     * concurrency is handled where the constraint lives and two writers cannot collide during the
     * screen-50 coexistence window (ADR-ATK-026).</p>
     *
     * <p><b>메모리 카운터는 그 성질을 갖지 못한다.</b> 인스턴스마다 0 부터 다시 세므로 두 인스턴스가
     * 같은 {@code tran_id} 를 발급한다 — 정확히 D-A25 다. 그래서 이 빈은 환경 구분자가 운영
     * ({@value #PRODUCTION})이면 <b>기동을 실패시킨다.</b> 조용히 운영 경로가 되는 것이 이 설계에서
     * 가장 위험한 결말이고, 실패는 그것을 불가능하게 만든다.</p>
     * <p><b>An in-memory counter cannot have that property</b>: each instance restarts at zero, so two
     * instances issue the same {@code tran_id} — precisely D-A25. This bean therefore <b>fails startup</b>
     * when the discriminator is production ({@value #PRODUCTION}). Silently becoming the production path is
     * the worst outcome available here, and failing makes it impossible.</p>
     *
     * @param environment 환경 구분자 / the environment discriminator
     * @return 임시 순번 공급원 / the stand-in sequence source
     * @throws IllegalStateException 운영 구분자로 기동하면 / when started with the production discriminator
     *
     * // req: FR-ATS-008, RISK-A04
     */
    @Bean
    public TranIdGenerator.SequenceSource inMemoryDailySequence(
            @Value("${iris.alimtalk.environment:T}") char environment) {
        if (environment == PRODUCTION) {
            throw new IllegalStateException(
                    "The in-memory tran_id sequence is a Sprint A1 stand-in and must not run in production: "
                            + "it restarts at zero per instance, so two instances would issue the same "
                            + "(is_cd, tran_id) — defect D-A25. Implement the database sequence (task A2-02, "
                            + "ADR-ATK-026) before deploying with iris.alimtalk.environment=" + PRODUCTION + ".");
        }
        log.warn("AlimTalk tran_id sequence is the in-memory Sprint A1 stand-in (environment={}). "
                + "It is not safe across instances and must be replaced by the database sequence in A2-02.",
                environment);
        return new InMemoryDailySequence();
    }

    /**
     * 기관·일자별 메모리 순번. / An in-memory per-institution, per-date sequence.
     *
     * <p>{@link AtomicLong} 을 쓰는 것은 단일 인스턴스 안에서의 동시성만 보장한다. 인스턴스 간
     * 보장은 DB 시퀀스만이 줄 수 있다.</p>
     * <p>{@link AtomicLong} guarantees concurrency within one instance only; across instances, only a
     * database sequence can.</p>
     *
     * // req: FR-ATS-008
     */
    static final class InMemoryDailySequence implements TranIdGenerator.SequenceSource {

        private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();

        /**
         * 다음 순번을 돌려준다. / Returns the next sequence value.
         *
         * @param institutionCode 이용기관코드 / the institution code
         * @param date            대상 일자 / the date
         * @return 0 부터 시작하는 순번 / a zero-based sequence value
         */
        @Override
        public long next(String institutionCode, LocalDate date) {
            return counters.computeIfAbsent(institutionCode + "|" + date, key -> new AtomicLong())
                    .getAndIncrement();
        }
    }
}
