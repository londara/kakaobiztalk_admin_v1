package com.webcash.iris.biztalk.alimtalk.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.webcash.iris.biztalk.alimtalk.infra.db.TemplateMapper;
import com.webcash.iris.common.tenant.TenantContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link TemplateRegistry} 검증. / Verification for {@link TemplateRegistry}.
 *
 * <p>DB 를 필요로 하지 않는다 — 매퍼를 대역으로 두었다. Docker 금지 환경(RISK-A12)에서
 * 서비스 계층 로직은 이렇게만 온전히 검증할 수 있고, SQL 자체는
 * {@code TemplateMapperSqlTest} 가 별도로 고정한다.</p>
 * <p>Needs no database — the mapper is stubbed. With Docker prohibited (RISK-A12) this is the only way
 * service-layer logic gets full verification; the SQL itself is pinned separately by
 * {@code TemplateMapperSqlTest}.</p>
 *
 * // source: IDO.KKB_MSG_TMPL_L001/L002; biztalk_admin_61.js — validate-panel manual paste
 * // req: FR-ATV-001, FR-ATV-003, FR-ATT-001, FR-ATT-003, FR-ATT-004, FR-AZ-A02, NFR-PERF-A01
 */
class TemplateRegistryTest {

    /** 조회 횟수를 세는 매퍼 대역. / A stub mapper that counts lookups. */
    private static final class StubMapper implements TemplateMapper {
        private final Map<String, String> bodies;
        private final AtomicInteger bodyLookups = new AtomicInteger();

        StubMapper(Map<String, String> bodies) {
            this.bodies = bodies;
        }

        @Override
        public List<TemplateSummary> findByInstitution(String institutionCode) {
            return bodies.keySet().stream()
                    .filter(k -> k.startsWith(institutionCode + "|"))
                    .map(k -> new TemplateSummary(k.split("\\|")[1], "제목"))
                    .toList();
        }

        @Override
        public String findTemplateBody(String institutionCode, String templateCode) {
            bodyLookups.incrementAndGet();
            return bodies.get(institutionCode + "|" + templateCode);
        }
    }

    private static void bindSession(String institutionCode) {
        TenantContext.set(new TenantContext.TenantPrincipal(
                "operator@example.com", institutionCode, true));
    }

    @AfterEach
    void clearSession() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("FR-AZ-A02 — 세션이 범위를 정한다 / the session decides the scope")
    class Scoping {

        @Test
        @DisplayName("세션이 없으면 조회할 수 없다 — 닫힘으로 실패한다 / no lookup without a session; fails closed")
        // req: FR-AZ-A02, NFR-SEC-AUTHZ-A01
        void noLookupWithoutSession() {
            TemplateRegistry registry = new TemplateRegistry(new StubMapper(Map.of()));

            // 중요한 것은 예외의 종류가 아니라 방향이다: 범위를 판정할 수 없을 때 전체 조회로
            // 넓어지지 않고 거절한다. TenantContext.require() 가 그렇게 구현되어 있다.
            // What matters is not the exception type but the direction: when scope cannot be resolved it
            // refuses rather than widening to an unscoped query. TenantContext.require() is built that way.
            assertThatThrownBy(() -> registry.list("K00001"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("tenant");
        }

        @Test
        @DisplayName("본문 조회도 세션 범위를 지난다 / the body lookup passes through the scope too")
        // req: FR-ATT-004, FR-AZ-A02
        void bodyLookupIsScoped() {
            // 레거시 D-S3 의 형태: 본문의 IS_CD 를 질의에 그대로 넘기면 인증된 사용자가 아무
            // 기관의 데이터나 읽는다. 여기서는 요청이 요구할 뿐이고 세션이 허가한다.
            // The D-S3 shape: passing the body's IS_CD straight into a query lets any authenticated user
            // read any institution's data. Here the request asks and the session grants.
            bindSession("K00001");
            StubMapper mapper = new StubMapper(Map.of("K00001|T1", "#{a}원"));
            TemplateRegistry registry = new TemplateRegistry(mapper);

            assertThat(registry.matcherFor("K00001", "T1")).isNotNull();
        }
    }

    @Nested
    @DisplayName("FR-ATV-003 — 미등록과 불일치의 구분 / unregistered vs mismatched")
    class Distinction {

        @Test
        @DisplayName("등록되지 않은 코드는 미등록으로 보고된다 / an unregistered code reports as unregistered")
        // req: FR-ATV-003
        void unregisteredReportsAsUnregistered() {
            bindSession("K00001");
            TemplateRegistry registry = new TemplateRegistry(new StubMapper(Map.of()));

            TemplateRegistry.Outcome outcome = registry.validate("K00001", "NOPE", "아무 내용");

            assertThat(outcome.registered()).isFalse();
            assertThat(outcome.permitsSend()).isFalse();
            assertThat(outcome.match().firstDivergence().templatePart()).isEqualTo("NOPE");
        }

        @Test
        @DisplayName("등록되었지만 내용이 다르면 불일치다 / registered but divergent is a mismatch")
        // req: FR-ATV-002
        void registeredButDivergentIsMismatch() {
            bindSession("K00001");
            TemplateRegistry registry = new TemplateRegistry(
                    new StubMapper(Map.of("K00001|T1", "#{금액}원이 결제되었습니다.")));

            TemplateRegistry.Outcome outcome = registry.validate("K00001", "T1", "50,000원이 취소되었습니다.");

            assertThat(outcome.registered()).isTrue();
            assertThat(outcome.permitsSend()).isFalse();
            assertThat(outcome.match().matched()).isFalse();
        }

        @Test
        @DisplayName("부합하면 발송을 허용한다 / a conformant message permits a send")
        // req: FR-ATV-001, FR-ATV-002
        void conformantPermitsSend() {
            bindSession("K00001");
            TemplateRegistry registry = new TemplateRegistry(
                    new StubMapper(Map.of("K00001|T1", "#{금액}원이 결제되었습니다.")));

            TemplateRegistry.Outcome outcome = registry.validate("K00001", "T1", "50,000원이 결제되었습니다.");

            assertThat(outcome.permitsSend()).isTrue();
            assertThat(outcome.match().variableValues()).containsEntry("금액", "50,000");
        }

        @Test
        @DisplayName("D-A6 정정이 레지스트리 경로에서도 유지된다 / the D-A6 correction holds here too")
        // req: FR-ATV-004
        void correctedBehaviourHoldsThroughRegistry() {
            // 수동 탭과 자동 경로가 같은 매처를 쓴다는 것의 의미(FR-ATV-007): 레거시가 거절했던
            // 이 입력이 두 경로에서 똑같이 통과한다.
            // What sharing one matcher means (FR-ATV-007): this input, which the legacy rejected, passes
            // identically through both paths.
            bindSession("K00001");
            TemplateRegistry registry = new TemplateRegistry(
                    new StubMapper(Map.of("K00001|T1", "#{name}님 안녕")));

            assertThat(registry.validate("K00001", "T1", "김님철수님 안녕").permitsSend()).isTrue();
        }
    }

    @Nested
    @DisplayName("NFR-PERF-A01 — 캐시 / caching")
    class Caching {

        @Test
        @DisplayName("본문이 그대로면 다시 컴파일하지 않는다 / an unchanged body is not recompiled")
        // req: NFR-PERF-A01
        void unchangedBodyIsNotRecompiled() {
            bindSession("K00001");
            StubMapper mapper = new StubMapper(Map.of("K00001|T1", "#{a}원"));
            TemplateRegistry registry = new TemplateRegistry(mapper);

            TemplateMatcher first = registry.matcherFor("K00001", "T1");
            TemplateMatcher second = registry.matcherFor("K00001", "T1");

            assertThat(second).isSameAs(first);
        }

        @Test
        @DisplayName("본문은 매번 읽는다 — 캐시는 컴파일 결과만 / the body is re-read; only compilation is cached")
        // req: NFR-PERF-A01, AMB-A07
        void bodyIsAlwaysReRead() {
            // KKB_MSG_TMPL 에 쓰는 주체가 이 저장소에 없다(AMB-A07). 누가 언제 바꾸는지 모르는
            // 표를 권위로 삼았으므로, 본문을 기억해 두면 낡은 규칙으로 검증할 위험이 있다 —
            // 검증이 없는 것보다 나쁘다.
            // Nothing in this repository writes KKB_MSG_TMPL (AMB-A07). Having made a table we do not
            // control authoritative, memorising the body would risk validating against a stale rule —
            // worse than not validating.
            bindSession("K00001");
            StubMapper mapper = new StubMapper(Map.of("K00001|T1", "#{a}원"));
            TemplateRegistry registry = new TemplateRegistry(mapper);

            registry.matcherFor("K00001", "T1");
            registry.matcherFor("K00001", "T1");

            assertThat(mapper.bodyLookups.get()).isEqualTo(2);
        }

        @Test
        @DisplayName("해시가 충돌하는 본문도 다시 컴파일한다 / a body whose hash collides is still recompiled")
        // req: FR-ATV-001, FR-ATV-003, NFR-PERF-A01
        void collidingBodyIsRecompiled() {
            // "Aa" 와 "BB" 는 String.hashCode() 가 같다(둘 다 2112). 해시는 반복 곱셈이라 같은
            // 접미사를 붙여도 계속 같다. 캐시 키가 본문 해시였을 때, 템플릿 본문을 이렇게 바꾸면
            // 키가 변하지 않아 캐시가 <b>바뀌기 전 규칙</b>을 돌려주었다 — 이 클래스가 막으려던
            // 바로 그 상황이며, 32비트 해시로는 막을 수 없는 것이었다.
            //
            // "Aa" and "BB" share a String.hashCode() (2112 each), and because the hash is an iterative
            // multiplication they still agree once the same suffix is appended. While the cache key held
            // the body hash, changing the template this way left the key unchanged, so the cache handed
            // back the <b>pre-change rule</b> — exactly what this class set out to prevent, and not
            // something a 32-bit hash can prevent.
            assertThat("Aa".hashCode()).isEqualTo("BB".hashCode());

            bindSession("K00001");
            Map<String, String> bodies = new HashMap<>(Map.of("K00001|T1", "Aa#{금액}원"));
            StubMapper mapper = new StubMapper(bodies);
            TemplateRegistry registry = new TemplateRegistry(mapper);

            assertThat(registry.validate("K00001", "T1", "Aa1000원").match().matched()).isTrue();

            bodies.put("K00001|T1", "BB#{금액}원");

            assertThat(registry.validate("K00001", "T1", "BB1000원").match().matched())
                    .as("바뀐 본문으로 검증해야 한다 / must validate against the changed body")
                    .isTrue();
            assertThat(registry.validate("K00001", "T1", "Aa1000원").match().matched())
                    .as("옛 본문은 더 이상 통과하면 안 된다 / the old body must no longer pass")
                    .isFalse();
        }

        @Test
        @DisplayName("템플릿 하나에 항목 하나 / one entry per template, however often the body changes")
        // req: NFR-PERF-A01
        void cacheDoesNotGrowWithBodyChanges() {
            // 키에 본문 해시가 있으면 본문이 바뀔 때마다 새 항목이 생기고 옛 항목은 사라지지
            // 않는다. 템플릿을 자주 고치는 기관에서 맵이 무한히 자란다.
            // With the body hash in the key, every change minted a new entry and no old entry was ever
            // removed — an institution that edits templates often grew the map without bound.
            bindSession("K00001");
            Map<String, String> bodies = new HashMap<>(Map.of("K00001|T1", "v0 #{a}원"));
            TemplateRegistry registry = new TemplateRegistry(new StubMapper(bodies));

            for (int i = 0; i < 50; i++) {
                bodies.put("K00001|T1", "v" + i + " #{a}원");
                registry.matcherFor("K00001", "T1");
            }

            assertThat(registry.cacheSize()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("FR-ATT-001 — 목록 / listing")
    class Listing {

        @Test
        @DisplayName("이용기관의 템플릿만 돌려준다 / returns only this institution's templates")
        // req: FR-ATT-001, FR-ATT-003
        void returnsOnlyThisInstitution() {
            bindSession("K00001");
            TemplateRegistry registry = new TemplateRegistry(new StubMapper(Map.of(
                    "K00001|T1", "a", "K00001|T2", "b", "K00002|T9", "c")));

            assertThat(registry.list("K00001"))
                    .extracting(TemplateSummary::templateCode)
                    .containsExactlyInAnyOrder("T1", "T2");
        }
    }
}
