package com.webcash.iris.biztalk.alimtalk.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.webcash.iris.biztalk.alimtalk.domain.TranIdGenerator;
import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Collections;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;

/**
 * {@link AlimTalkConfig} 배선 검증. / Wiring verification for {@link AlimTalkConfig}.
 *
 * <h2>이 테스트가 존재하는 이유 / why this test exists</h2>
 * <p>{@code AlimTalkController} 가 {@link TranIdGenerator} 빈을 요구했지만 그 빈을 제공하는 설정이
 * 없어 애플리케이션이 기동에 실패했다. <b>154개의 테스트가 모두 통과한 상태에서였다.</b>
 * 단위 테스트는 생성자를 직접 호출하므로 배선의 부재를 볼 수 없었고, standalone MockMvc 도
 * 컨트롤러를 직접 {@code new} 하므로 마찬가지였다.</p>
 * <p>The application failed to start because {@code AlimTalkController} required a
 * {@link TranIdGenerator} bean that no configuration provided — <b>with all 154 tests passing.</b> Unit
 * tests call the constructor directly and cannot see missing wiring; standalone MockMvc constructs the
 * controller itself and equally cannot.</p>
 *
 * <p>커버리지도 잡지 못했다. 99.2 % 는 <b>작성된 코드가 실행되었는지</b>를 재고,
 * <b>작성되지 않은 코드</b>에 대해서는 아무 말도 하지 않는다. 이 슬라이스가 세 번째로 만난 같은
 * 교훈이다 — 통과하는 지표가 없는 것을 감춘다.</p>
 * <p>Coverage did not catch it either: 99.2 % measures whether <b>written</b> code ran and says nothing
 * about code that was never <b>written</b>. The same lesson this slice has now met three times — a passing
 * metric conceals an absence.</p>
 *
 * // req: FR-ATS-008, FR-ATS-010
 */
class AlimTalkConfigTest {

    private static final Clock FIXED =
            Clock.fixed(Instant.parse("2026-08-18T05:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Nested
    @DisplayName("배선 / wiring")
    class Wiring {

        @Test
        @DisplayName("컨트롤러가 요구하는 모든 타입에 @Bean 이 있다 / every type the controller needs has a @Bean")
        // req: FR-ATS-008
        void everyTypeTheControllerNeedsHasABean() throws Exception {
            // 이 단언이 기동 실패를 테스트로 옮긴다. 컨트롤러 생성자 파라미터 타입 중
            // 스프링이 스스로 만들 수 없는 것(도메인 타입)은 누군가 @Bean 으로 제공해야 한다.
            // This assertion moves the startup failure into a test: any controller constructor parameter
            // Spring cannot create on its own must be provided by someone's @Bean.
            Class<?> controller = Class.forName("com.webcash.iris.biztalk.alimtalk.api.AlimTalkController");
            Constructor<?> constructor = controller.getDeclaredConstructors()[0];

            Set<Class<?>> provided = Arrays.stream(AlimTalkConfig.class.getDeclaredMethods())
                    .filter(m -> m.isAnnotationPresent(Bean.class))
                    .map(java.lang.reflect.Method::getReturnType)
                    .collect(java.util.stream.Collectors.toSet());

            for (Parameter parameter : constructor.getParameters()) {
                Class<?> type = parameter.getType();

                // @Value 로 주입되는 파라미터는 빈이 필요하지 않다 — 설정 값이지 협력 객체가
                // 아니다. 이것을 걸러내지 않으면 이 테스트는 `boolean` 에 @Bean 을 요구하게 되고,
                // 그 요구는 틀렸다. 테스트가 틀린 것을 요구하기 시작하면 사람이 테스트를
                // 무시하는 법을 배우고, 그때부터 테스트는 아무것도 지키지 못한다.
                //
                // A @Value-injected parameter needs no bean: it is a configuration value, not a
                // collaborator. Without this filter the test would demand a @Bean for `boolean`, which is
                // simply wrong — and once a test demands wrong things, people learn to ignore it, after
                // which it guards nothing.
                if (parameter.isAnnotationPresent(
                        org.springframework.beans.factory.annotation.Value.class)) {
                    continue;
                }

                boolean springManaged = type.isAnnotationPresent(org.springframework.stereotype.Service.class)
                        || type.isAnnotationPresent(org.springframework.stereotype.Component.class);
                assertThat(springManaged || provided.contains(type))
                        .as("%s 는 @Bean 도 @Service 도 아니다 — 기동 시 주입할 수 없다 "
                                + "/ %s is neither a @Bean nor a @Service, so it cannot be injected at startup",
                                type.getSimpleName(), type.getSimpleName())
                        .isTrue();
            }
        }

        @Test
        @DisplayName("생성기를 조립할 수 있다 / the generator can be assembled")
        // req: FR-ATS-008
        void generatorCanBeAssembled() {
            AlimTalkConfig config = new AlimTalkConfig();

            TranIdGenerator generator =
                    config.tranIdGenerator('T', config.inMemoryDailySequence('T'), FIXED);

            assertThat(generator.next("K00001")).startsWith("T260818");
        }
    }

    @Nested
    @DisplayName("운영 보호 / production guard")
    class ProductionGuard {

        @Test
        @DisplayName("운영 구분자로는 기동을 거부한다 / refuses to start with the production discriminator")
        // req: FR-ATS-008, RISK-A04
        void refusesProduction() {
            // 메모리 카운터는 인스턴스마다 0 부터 다시 세므로 두 인스턴스가 같은 tran_id 를
            // 발급한다 — 정확히 D-A25 다. 조용히 운영 경로가 되는 것을 불가능하게 만든다.
            // An in-memory counter restarts at zero per instance, so two instances issue the same
            // tran_id — precisely D-A25. Becoming the production path silently is made impossible.
            AlimTalkConfig config = new AlimTalkConfig();

            assertThatThrownBy(() -> config.inMemoryDailySequence(AlimTalkConfig.PRODUCTION))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("D-A25")
                    .hasMessageContaining("A2-02");
        }

        @Test
        @DisplayName("비운영 구분자는 허용한다 / non-production discriminators are allowed")
        // req: FR-ATS-008
        void allowsNonProduction() {
            AlimTalkConfig config = new AlimTalkConfig();

            assertThat(config.inMemoryDailySequence('T')).isNotNull();
            assertThat(config.inMemoryDailySequence('L')).isNotNull();
        }
    }

    @Nested
    @DisplayName("임시 구현의 성질 / the stand-in's properties")
    class StandIn {

        @Test
        @DisplayName("한 인스턴스 안에서는 충돌하지 않는다 / no collision within one instance")
        // req: FR-ATS-008
        void noCollisionWithinOneInstance() throws Exception {
            TranIdGenerator.SequenceSource source = new AlimTalkConfig.InMemoryDailySequence();
            Set<Long> issued = Collections.newSetFromMap(new ConcurrentHashMap<>());
            int count = 200;
            CountDownLatch go = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(8);

            for (int i = 0; i < count; i++) {
                pool.submit(() -> {
                    go.await();
                    issued.add(source.next("K00001", LocalDate.of(2026, 8, 18)));
                    return null;
                });
            }
            go.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

            assertThat(issued).hasSize(count);
        }

        @Test
        @DisplayName("두 인스턴스 사이에서는 충돌한다 — 그래서 운영 금지다 / it DOES collide across instances")
        // req: FR-ATS-008, RISK-A04
        void itDoesCollideAcrossInstances() {
            // 이 테스트는 결함을 고정하는 것이 아니라 <b>한계를 증명</b>한다. 임시 구현이 왜
            // 운영에 나갈 수 없는지를 코드로 남겨, 나중에 "그냥 두면 되지 않나" 라는 판단이
            // 나오지 않게 한다.
            // This test pins a <b>limitation</b> rather than a defect: it records in code why the stand-in
            // cannot ship, so that "could we just leave it?" is answered before it is asked.
            TranIdGenerator.SequenceSource first = new AlimTalkConfig.InMemoryDailySequence();
            TranIdGenerator.SequenceSource second = new AlimTalkConfig.InMemoryDailySequence();
            LocalDate date = LocalDate.of(2026, 8, 18);

            assertThat(first.next("K00001", date))
                    .as("두 인스턴스가 같은 순번을 낸다 / two instances issue the same value")
                    .isEqualTo(second.next("K00001", date));
        }
    }
}
