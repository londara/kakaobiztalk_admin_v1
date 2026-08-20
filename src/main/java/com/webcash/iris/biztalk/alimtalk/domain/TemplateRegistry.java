package com.webcash.iris.biztalk.alimtalk.domain;

import com.webcash.iris.biztalk.alimtalk.infra.db.TemplateMapper;
import com.webcash.iris.common.tenant.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * 템플릿 레지스트리 — 조회·캐시·검증의 단일 창구. / The template registry: one door for lookup, caching and validation.
 *
 * <h2>세션이 범위를 정한다 / the session decides the scope</h2>
 * <p>모든 조회는 {@link TenantContext} 에서 도출한 이용기관으로 한정된다. 요청 본문의
 * {@code is_cd} 를 그대로 쓰지 않는 이유는 발신번호 슬라이스가 D-S3 에서 배운 것과 같다 —
 * 레거시는 본문의 {@code IS_CD} 를 질의에 곧바로 넘겨, 인증된 사용자가 아무 기관의 데이터나
 * 읽을 수 있었다. 여기서는 요청이 기관을 <b>요구</b>할 수 있을 뿐이고 세션이 <b>허가</b>한다
 * (FR-AZ-A02).</p>
 * <p>Every lookup is bounded by the institution derived from {@link TenantContext}. The request body's
 * {@code is_cd} is never used directly, for the reason the 발신번호 slice learned as D-S3: the legacy passed
 * the body's {@code IS_CD} straight into its query, letting any authenticated user read any institution's
 * data. Here a request may <b>ask</b> for an institution; the session <b>grants</b> it (FR-AZ-A02).</p>
 *
 * <h2>컴파일 결과를 캐시하는 이유 / why compiled patterns are cached</h2>
 * <p>{@link TemplateMatcher#compile} 은 토큰화와 정규식 컴파일을 수행한다. 같은 템플릿이 발송마다
 * 다시 컴파일되면 NFR-PERF-A01(P95 &lt; 300 ms)이 템플릿 길이에 좌우된다. 다만 캐시가 본문 변경을
 * 놓치면 <b>낡은 규칙으로 검증</b>하게 되고, 그것은 검증이 없는 것보다 나쁘다. 그래서 항목마다
 * 컴파일에 쓴 본문을 함께 보관하고 조회할 때마다 대조한다 ({@code CachedMatcher} 참조).</p>
 * <p>{@link TemplateMatcher#compile} tokenizes and compiles a pattern. Recompiling per send would make
 * NFR-PERF-A01 depend on template length. But a cache that misses a body change would <b>validate
 * against the old rule</b>, which is worse than not validating at all — so each entry keeps the body it
 * was compiled from and compares it on every lookup (see {@code CachedMatcher}).</p>
 *
 * <p>본문이 바뀔 수 있다는 전제는 추측이 아니다 — {@code KKB_MSG_TMPL} 에 쓰는 주체가 이
 * 저장소 어디에도 없다(AMB-A07). 누가 언제 바꾸는지 모르는 표를 권위로 삼았으므로, 캐시는
 * 그 표를 신뢰하되 기억하지는 않는다.</p>
 * <p>That the body may change is not speculation: nothing in this repository writes
 * {@code KKB_MSG_TMPL} (AMB-A07). Having made a table we do not control authoritative, the cache trusts
 * it without memorising it.</p>
 *
 * // source: IDO.KKB_MSG_TMPL_L001/L002; biztalk_admin_61.js — validate-panel manual paste
 * // req: FR-ATV-001, FR-ATV-003, FR-ATV-007, FR-ATT-001, FR-ATT-003, FR-AZ-A02, NFR-PERF-A01
 */
@Service
public class TemplateRegistry {

    private final TemplateMapper mapper;

    /**
     * 컴파일된 매처 캐시 — 키는 {@code (기관, 코드, 본문해시)}.
     * Compiled-matcher cache keyed by {@code (institution, code, body hash)}.
     */
    private final Map<CacheKey, CachedMatcher> compiled = new ConcurrentHashMap<>();

    /**
     * 레지스트리를 생성한다. / Creates the registry.
     *
     * @param mapper 템플릿 조회 매퍼 / the template mapper
     */
    public TemplateRegistry(TemplateMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 운영자가 볼 수 있는 템플릿 목록을 돌려준다. / Lists the templates the operator may see.
     *
     * @param requestedInstitutionCode 요청된 이용기관코드 / the requested institution code
     * @return 템플릿 요약 목록 / template summaries
     *
     * // req: FR-ATT-001, FR-ATT-002, FR-ATT-003, FR-AZ-A02
     */
    public List<TemplateSummary> list(String requestedInstitutionCode) {
        return mapper.findByInstitution(scopeOf(requestedInstitutionCode));
    }

    /**
     * 등록된 템플릿에 대한 매처를 돌려준다. / Returns a matcher for a registered template.
     *
     * @param requestedInstitutionCode 요청된 이용기관코드 / the requested institution code
     * @param templateCode             템플릿코드 / the template code
     * @return 매처, 등록되지 않았으면 {@code null} / the matcher, or {@code null} when not registered
     *
     * // req: FR-ATV-001, FR-ATV-003, NFR-PERF-A01
     */
    public TemplateMatcher matcherFor(String requestedInstitutionCode, String templateCode) {
        String institution = scopeOf(requestedInstitutionCode);
        String body = mapper.findTemplateBody(institution, templateCode);
        if (body == null) {
            // 등록되지 않은 코드는 내용 불일치와 구분해서 보고해야 한다(FR-ATV-003). null 을
            // 돌려주고 호출부가 그 구분을 하도록 둔다 — 예외로 만들면 두 경우가 같은 catch 로
            // 흘러들어 구분이 사라진다.
            // An unregistered code must be reported distinctly from a content mismatch (FR-ATV-003).
            // Returning null lets the caller draw that distinction; an exception would funnel both cases
            // into one catch and lose it.
            return null;
        }
        // 본문을 함께 보관하고 매번 대조한다 — 일치하면 재사용, 아니면 다시 컴파일한다.
        // Holds the body alongside the matcher and compares it on every lookup: reuse on a match,
        // recompile otherwise.
        CacheKey key = new CacheKey(institution, templateCode);
        CachedMatcher hit = compiled.get(key);
        if (hit != null && hit.body().equals(body)) {
            return hit.matcher();
        }
        TemplateMatcher matcher = TemplateMatcher.compile(body);
        compiled.put(key, new CachedMatcher(body, matcher));
        return matcher;
    }

    /**
     * 메시지를 등록된 템플릿에 대해 검증한다. / Validates a message against its registered template.
     *
     * <p>발송 전에 서버에서 실행되는 검증이다(FR-ATV-001). 레거시가 사람에게 맡겼던 비교가
     * 벤더 거절을 실제로 막을 수 있는 지점으로 옮겨온 것이다.</p>
     * <p>The server-side, pre-despatch validation (FR-ATV-001): the comparison the legacy left to a
     * person, moved to where it can actually prevent a vendor rejection.</p>
     *
     * @param requestedInstitutionCode 요청된 이용기관코드 / the requested institution code
     * @param templateCode             템플릿코드 / the template code
     * @param message                  발송할 본문 / the body to be sent
     * @return 검증 결과 / the validation outcome
     *
     * // req: FR-ATV-001, FR-ATV-002, FR-ATV-003, FR-ATT-004
     */
    public Outcome validate(String requestedInstitutionCode, String templateCode, String message) {
        TemplateMatcher matcher = matcherFor(requestedInstitutionCode, templateCode);
        if (matcher == null) {
            return Outcome.notRegistered(templateCode);
        }
        return new Outcome(true, matcher.match(message));
    }

    /**
     * 세션이 허가하는 이용기관을 판정한다. / Resolves the institution the session permits.
     *
     * @param requestedInstitutionCode 요청된 코드 / the requested code
     * @return 유효한 이용기관코드 / the effective institution code
     *
     * // req: FR-AZ-A02
     */
    private String scopeOf(String requestedInstitutionCode) {
        return TenantContext.require().effectiveInstitutionCode(requestedInstitutionCode);
    }

    /**
     * 검증 결과 — 미등록과 불일치를 구분한다. / A validation outcome distinguishing unregistered from mismatched.
     *
     * @param registered 템플릿이 등록되어 있으면 {@code true} / {@code true} when the template is registered
     * @param match      등록된 경우의 일치 결과 / the match result when registered
     *
     * // req: FR-ATV-002, FR-ATV-003
     */
    public record Outcome(boolean registered, TemplateMatchResult match) {

        /**
         * 미등록 결과를 만든다. / Creates an unregistered outcome.
         *
         * @param templateCode 조회한 코드 / the code that was looked up
         * @return 미등록 결과 / an unregistered outcome
         *
         * // req: FR-ATV-003
         */
        static Outcome notRegistered(String templateCode) {
            return new Outcome(false, TemplateMatchResult.mismatch(List.of(
                    new TemplateMatchResult.Divergence(0, templateCode,
                            "이 이용기관에 등록되지 않은 템플릿코드다 / template code is not registered to this institution"))));
        }

        /**
         * 발송을 허용할 수 있는지 판정한다. / Reports whether a send may proceed.
         *
         * @return 등록되어 있고 일치하면 {@code true} / {@code true} when registered and conformant
         *
         * // req: FR-ATV-002, FR-ATT-004
         */
        public boolean permitsSend() {
            return registered && match.matched();
        }
    }

    /**
     * 캐시에 든 항목 수. / The number of cached entries.
     *
     * <p>테스트 전용 접근점이다. 캐시가 <b>자라지 않는다</b>는 성질은 밖에서 관찰할 수 없고,
     * 관찰할 수 없는 성질은 회귀해도 아무도 모른다 — 실제로 그렇게 회귀했었다.</p>
     * <p>A test-only seam. That the cache <b>does not grow</b> is not observable from outside, and an
     * unobservable property regresses unnoticed — as this one did.</p>
     *
     * @return 항목 수 / the entry count
     *
     * // req: NFR-PERF-A01
     */
    int cacheSize() {
        return compiled.size();
    }

    /**
     * 캐시 키. / The cache key.
     *
     * @param institutionCode 이용기관코드 / the institution code
     * @param templateCode    템플릿코드 / the template code
     *
     * // req: NFR-PERF-A01
     */
    private record CacheKey(String institutionCode, String templateCode) {
    }

    /**
     * 캐시 항목 — 컴파일에 쓴 본문을 함께 보관한다.
     * A cache entry, holding the body the matcher was compiled from.
     *
     * <h2>본문 해시를 키에 넣지 않는 이유 / why the body hash is not part of the key</h2>
     * <p>이전 구현은 키에 {@code body.hashCode()} 를 넣어 "본문이 바뀌면 키도 바뀐다"고 보았다.
     * 두 가지가 틀렸다. 첫째, {@code String.hashCode()} 는 32비트 비암호학적 해시라 서로 다른
     * 본문이 같은 값을 가질 수 있고 — 충돌 문자열은 쉽게 만들어진다 — 그때 캐시는 <b>바뀌기 전
     * 규칙으로 검증</b>한다. 이 클래스가 막으려던 바로 그 상황이다. 둘째, 본문이 바뀔 때마다
     * 키가 새로 생기므로 옛 항목이 영원히 남아 맵이 무한히 자란다.</p>
     * <p>The previous version put {@code body.hashCode()} in the key, reasoning that a changed body
     * changes the key. Two things were wrong. First, {@code String.hashCode()} is a 32-bit
     * non-cryptographic hash: different bodies can share a value — colliding strings are easy to
     * construct — and the cache then <b>validates against the pre-change rule</b>, precisely the
     * situation this class set out to prevent. Second, every body change minted a new key, so old
     * entries were never removed and the map grew without bound.</p>
     *
     * <p>본문을 그대로 보관하고 대조하면 두 문제가 함께 사라진다. 템플릿 하나당 항목 하나이고,
     * 비교는 정확하다. {@code KKB_MSG_TMPL} 에 쓰는 주체를 우리가 통제하지 못한다는 사실
     * (AMB-A07)을 생각하면 해시의 근사(近似)에 기댈 자리가 아니다.</p>
     * <p>Holding and comparing the body itself removes both problems: one entry per template, and an
     * exact comparison. Given that nothing here controls who writes {@code KKB_MSG_TMPL} (AMB-A07),
     * this is not a place to rely on a hash's approximation.</p>
     *
     * @param body    컴파일 시점의 본문 / the body at compile time
     * @param matcher 컴파일된 매처 / the compiled matcher
     *
     * // req: FR-ATV-001, FR-ATV-003, NFR-PERF-A01
     */
    private record CachedMatcher(String body, TemplateMatcher matcher) {
    }
}
