package com.webcash.iris.biztalk.alimtalk.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link TranIdGenerator} 검증. / Verification for {@link TranIdGenerator}.
 *
 * <p>레거시의 {@code "33" + hh24miss} 는 초 단위였으므로 같은 초의 두 발송이 같은 값을 냈다.
 * 그 값이 {@code KKB_ADMIN_SEND_HIS} 주키의 절반이었고, 트랜잭션이 없어(D-A27) 삽입 실패가
 * 조용히 지나갔다 — 그래서 4년 넘게 드러나지 않았다(D-A25).</p>
 * <p>The legacy's {@code "33" + hh24miss} had second precision, so two sends in one second produced the
 * same value. That value was half the primary key of {@code KKB_ADMIN_SEND_HIS}, and with no transaction
 * (D-A27) the failed insert passed unnoticed — which is why it went undetected for over four years
 * (D-A25).</p>
 *
 * // source: biztalk_admin_50_s001_act.jsp — "33" + hh24miss; hh24miss + apiNumber++
 * // req: FR-ATS-008, FR-ATS-010, CONST-DATA-A04
 */
class TranIdGeneratorTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /**
     * 메모리 순번 공급원 — DB 시퀀스 대역. / An in-memory sequence source standing in for the DB sequence.
     *
     * <p>실제 구현은 DB 시퀀스이며 A2-02 에 속한다. 이 대역은 동시성 동작이 같도록
     * {@link AtomicLong} 을 쓴다.</p>
     * <p>The real implementation is a DB sequence and belongs to A2-02. This stand-in uses
     * {@link AtomicLong} so the concurrency behaviour matches.</p>
     */
    private static final class MemorySequences implements TranIdGenerator.SequenceSource {
        private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();
        private long start;

        @Override
        public long next(String institutionCode, LocalDate date) {
            return counters.computeIfAbsent(institutionCode + "|" + date, k -> new AtomicLong(start))
                    .getAndIncrement();
        }
    }

    private static Clock fixedAt(String isoInstant) {
        return Clock.fixed(Instant.parse(isoInstant), KST);
    }

    @Nested
    @DisplayName("D-A25 — 충돌 / collisions")
    class Collisions {

        @Test
        @DisplayName("TC-A002-03: 같은 초의 두 발송이 다른 값을 받는다 / two sends in the same second differ")
        // req: FR-ATS-008
        void sameSecondProducesDistinctValues() {
            // 시계를 고정했으므로 레거시 방식이라면 두 값이 반드시 같다.
            // The clock is fixed, so under the legacy scheme the two values would necessarily be equal.
            TranIdGenerator generator = new TranIdGenerator('A', new MemorySequences(),
                    fixedAt("2026-08-18T05:00:00Z"));

            String first = generator.next("K00001");
            String second = generator.next("K00001");

            assertThat(first).isNotEqualTo(second);
        }

        @Test
        @DisplayName("TC-A002-04: 다른 날 같은 시각이 다른 값을 받는다 / same clock time on different days differs")
        // req: FR-ATS-008, D-A25, D-A38
        void differentDaysProduceDistinctValues() {
            // 레거시 값에는 날짜가 없었으므로 어제 값과 오늘 값이 충돌했다.
            // The legacy value carried no date, so yesterday's collided with today's.
            //
            // D-A38 이 그 충돌의 <b>결과</b>를 특정한다. 이 값은 KKB_ADMIN_SEND_HIS.SERIALNUM 으로
            // 저장되고, IDO.KKB_ADMIN_SEND_HIS_L001 은 여러 날을 한 번에 조회한 뒤
            // `A.SERIALNUM = B.SERIALNUM` 으로 KKO_MSG_LOG 와 조인한다. 날짜가 없으면 그 조인은
            // 카테시안 곱이 되고 발송 통계가 부풀려진다 — 지저분한 ID 가 아니라 틀린 숫자다.
            //
            // D-A38 identifies what that collision <b>causes</b>. The value is stored as
            // KKB_ADMIN_SEND_HIS.SERIALNUM, and IDO.KKB_ADMIN_SEND_HIS_L001 queries a multi-day range
            // before joining `A.SERIALNUM = B.SERIALNUM` against KKO_MSG_LOG. Without a date component
            // that join becomes a cartesian product and the send statistics inflate — not untidy ids,
            // but wrong numbers.
            MemorySequences sequences = new MemorySequences();
            String day1 = new TranIdGenerator('A', sequences, fixedAt("2026-08-18T05:00:00Z")).next("K00001");
            String day2 = new TranIdGenerator('A', sequences, fixedAt("2026-08-19T05:00:00Z")).next("K00001");

            assertThat(day1).isNotEqualTo(day2);
            assertThat(day1).startsWith("A260818");
            assertThat(day2).startsWith("A260819");
        }

        @Test
        @DisplayName("기관이 다르면 순번이 독립적이다 / sequences are per institution")
        // req: FR-ATS-008
        void sequencesArePerInstitution() {
            // 주키가 (IS_CD, SERIALNUM) 이므로 기관 간 같은 순번은 충돌이 아니다.
            // The key is (IS_CD, SERIALNUM), so the same sequence across institutions is not a collision.
            MemorySequences sequences = new MemorySequences();
            TranIdGenerator generator = new TranIdGenerator('A', sequences, fixedAt("2026-08-18T05:00:00Z"));

            assertThat(generator.next("K00001")).isEqualTo(generator.next("K00002"));
        }

        @Test
        @DisplayName("500건 동시 발급에 중복이 없다 / 500 concurrent issues produce no duplicate")
        // req: FR-ATS-008
        void concurrentIssuesAreUnique() throws Exception {
            TranIdGenerator generator = new TranIdGenerator('A', new MemorySequences(),
                    fixedAt("2026-08-18T05:00:00Z"));
            Set<String> issued = Collections.newSetFromMap(new ConcurrentHashMap<>());
            int count = 500;
            CountDownLatch ready = new CountDownLatch(count);
            CountDownLatch go = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(16);

            for (int i = 0; i < count; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    issued.add(generator.next("K00001"));
                    return null;
                });
            }
            ready.await(5, TimeUnit.SECONDS);
            go.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

            assertThat(issued).hasSize(count);
        }
    }

    @Nested
    @DisplayName("계약 형식 / contract format")
    class Format {

        @Test
        @DisplayName("정확히 10자다 / exactly ten characters")
        // req: FR-ATC-005, CONST-DATA-A02
        void exactlyTenCharacters() {
            String value = new TranIdGenerator('A', new MemorySequences(),
                    fixedAt("2026-08-18T05:00:00Z")).next("K00001");

            assertThat(value).hasSize(AlimTalkLimits.CONTRACT_TRAN_ID);
            assertThat(TranIdGenerator.isWellFormed(value)).isTrue();
        }

        @Test
        @DisplayName("순번이 커져도 10자를 유지한다 / stays ten characters as the sequence grows")
        // req: FR-ATC-005
        void staysTenCharactersAsSequenceGrows() {
            MemorySequences sequences = new MemorySequences();
            // 상한(46,656) 바로 아래를 쓴다. 3자 base-36 최대값 부근에서도 자릿수가 늘지 않아야
            // 한다 — 늘어나면 계약의 10자를 넘긴다.
            // Just below the ceiling of 46,656. Even near the maximum three-character base-36 value the
            // width must not grow; if it did, the value would exceed the contract's ten characters.
            sequences.start = 40_000;
            TranIdGenerator generator = new TranIdGenerator('A', sequences, fixedAt("2026-08-18T05:00:00Z"));

            String value = generator.next("K00001");

            assertThat(value).hasSize(AlimTalkLimits.CONTRACT_TRAN_ID);
            assertThat(TranIdGenerator.isWellFormed(value)).isTrue();
        }

        @Test
        @DisplayName("환경 구분자가 앞에 온다 / the environment discriminator leads")
        // req: FR-ATS-008
        void environmentDiscriminatorLeads() {
            // 스테이징 발송이 운영 형식의 tran_id 로 실제 벤더에 도달하는 사고를 막는다.
            // Prevents a staging send reaching the real vendor with a production-shaped tran_id.
            MemorySequences sequences = new MemorySequences();
            Clock clock = fixedAt("2026-08-18T05:00:00Z");

            assertThat(new TranIdGenerator('A', sequences, clock).next("K1")).startsWith("A");
            assertThat(new TranIdGenerator('T', sequences, clock).next("K2")).startsWith("T");
        }

        @Test
        @DisplayName("환경 구분자는 대문자여야 한다 / the discriminator must be upper case")
        // req: FR-ATS-008
        void discriminatorMustBeUpperCase() {
            assertThatThrownBy(() -> new TranIdGenerator('1', new MemorySequences(), Clock.systemUTC()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("RESIDUAL-A02 — 운영자 입력값 / operator-supplied values")
    class OperatorSupplied {

        @Test
        @DisplayName("TC-A001-04: 길이를 넘으면 형식 위반이다 / an over-length value is malformed")
        // req: FR-ATC-005, RESIDUAL-A02
        void overLengthIsMalformed() {
            assertThat(TranIdGenerator.isWellFormed("A26081800011")).isFalse();
            assertThat(TranIdGenerator.isWellFormed("A260818001")).isTrue();
        }

        @Test
        @DisplayName("빈 값과 null 은 형식 위반이다 / blank and null are malformed")
        // req: FR-ATC-005
        void blankAndNullAreMalformed() {
            assertThat(TranIdGenerator.isWellFormed(null)).isFalse();
            assertThat(TranIdGenerator.isWellFormed("")).isFalse();
            assertThat(TranIdGenerator.isWellFormed("          ")).isFalse();
        }

        @Test
        @DisplayName("레거시 형식은 새 형식으로 통과하지 않는다 / the legacy format is not well-formed")
        // req: FR-ATS-008
        void legacyFormatIsNotWellFormed() {
            // 화면 50 이 만든 "33"+hh24miss 는 8자이므로 형식 검사를 통과하지 못한다. 과거 이력
            // 행에는 이 형식이 남아 있으므로, 대조 질의가 새 형식을 가정하면 안 된다
            // (명세 §6.4 조치 2).
            // Screen 50's "33"+hh24miss is eight characters and fails the check. Historical rows carry
            // this format, so reconciliation queries must not assume the new shape (spec §6.4 action 2).
            assertThat(TranIdGenerator.isWellFormed("33050000")).isFalse();
        }
    }

    @Nested
    @DisplayName("RISK-A04 — 순번 고갈 / sequence exhaustion")
    class Exhaustion {

        @Test
        @DisplayName("고갈되면 조용히 넘기지 않고 실패한다 / exhaustion fails loudly")
        // req: FR-ATS-008
        void exhaustionFailsLoudly() {
            // 1,679,616건/기관/일 은 현실적 물량보다 훨씬 높지만 상한은 상한이다. 넘기면
            // 값이 11자가 되어 계약을 위반하므로, 조용히 감싸는 것보다 실패가 옳다.
            // 1,679,616 per institution per day is far above plausible volume, but a ceiling is a ceiling.
            // Wrapping would produce an eleven-character value violating the contract, so failing is right.
            MemorySequences sequences = new MemorySequences();
            sequences.start = TranIdGenerator.SEQUENCE_CEILING;
            TranIdGenerator generator = new TranIdGenerator('A', sequences, fixedAt("2026-08-18T05:00:00Z"));

            assertThatThrownBy(() -> generator.next("K00001"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("exhausted")
                    .hasMessageContaining("K00001");
        }

        @Test
        @DisplayName("상한 직전 값은 여전히 유효하다 / the value just below the ceiling is valid")
        // req: FR-ATS-008
        void justBelowCeilingIsValid() {
            MemorySequences sequences = new MemorySequences();
            sequences.start = TranIdGenerator.SEQUENCE_CEILING - 1;
            TranIdGenerator generator = new TranIdGenerator('A', sequences, fixedAt("2026-08-18T05:00:00Z"));

            String value = generator.next("K00001");

            assertThat(value).hasSize(AlimTalkLimits.CONTRACT_TRAN_ID);
            assertThat(TranIdGenerator.isWellFormed(value)).isTrue();
        }
    }

    @Nested
    @DisplayName("FR-ATS-010 — 단건·다건 동일 방식 / one scheme for both")
    class SingleScheme {

        @Test
        @DisplayName("같은 생성기가 단건과 다건에 쓰인다 / the same generator serves both")
        // req: FR-ATS-010
        void sameGeneratorServesBoth() {
            // 레거시는 분기마다 형식이 달라 두 분기가 서로 충돌할 수 있었다.
            // The legacy used a different format per branch, so the branches could collide with each other.
            TranIdGenerator generator = new TranIdGenerator('A', new MemorySequences(),
                    fixedAt("2026-08-18T05:00:00Z"));
            Map<String, String> issued = new HashMap<>();

            for (int i = 0; i < 100; i++) {
                String value = generator.next("K00001");
                assertThat(issued.put(value, "single")).as("중복 발급 / duplicate issue").isNull();
                assertThat(TranIdGenerator.isWellFormed(value)).isTrue();
            }
        }
    }
}
