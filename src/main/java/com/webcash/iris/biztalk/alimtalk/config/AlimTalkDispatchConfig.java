package com.webcash.iris.biztalk.alimtalk.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webcash.iris.biztalk.alimtalk.domain.OutboxDispatcher;
import com.webcash.iris.biztalk.alimtalk.infra.db.OutboxMapper;
import com.webcash.iris.biztalk.alimtalk.infra.vendor.AlimTalkVendorClient;
import com.webcash.iris.biztalk.alimtalk.infra.vendor.CooconAlertClient;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 발송 경로의 배선. / Wiring for the dispatch path.
 *
 * <h2>기본값이 꺼짐인 이유 / why this is disabled by default</h2>
 * <p>이 설정이 켜지면 애플리케이션은 <b>기동하는 것만으로</b> 실제 벤더에 실제 메시지를 보내기
 * 시작한다. 개발자가 로컬에서 앱을 띄웠을 때 그런 일이 일어나서는 안 된다 — 되돌릴 수 없는
 * 행위이고, 수신자는 실제 사람이다.</p>
 * <p>Enabled, the application begins sending real messages to the real vendor <b>merely by
 * starting</b>. That must not happen because a developer ran the app locally: the action is
 * irreversible and the recipients are real people.</p>
 *
 * <p>그래서 세 가지가 <b>모두</b> 명시되어야 발송이 배선된다. 하나라도 없으면 접수는 되고
 * (아웃박스에 쌓이고) 발송은 되지 않는다 — 그 상태가 안전한 기본값이다.</p>
 * <p>Three things must therefore <b>all</b> be stated before dispatch is wired. With any one missing,
 * acceptance still works — rows accumulate in the outbox — and nothing is sent. That state is the safe
 * default.</p>
 *
 * <ol>
 *   <li>{@code iris.alimtalk.dispatch.enabled=true} — 명시적 의사표시 / an explicit intent</li>
 *   <li>{@code iris.alimtalk.vendor.base-url} — 기본값 없음 / no default</li>
 *   <li>A2-02 DDL 적용 — 코드로 확인할 수 없다 / cannot be checked from code</li>
 * </ol>
 *
 * <p>세 번째는 코드가 확인할 수 없다. {@code KKB_ATK_SEND_OUTBOX} 가 없으면 디스패처의 첫 질의가
 * 실패하며, 그 실패는 기동이 아니라 <b>첫 주기</b>에 나타난다. 그것을 미리 막을 방법이 없으므로
 * 기동 시 로그로 분명히 알린다 — 조용히 실패하는 것보다 시끄럽게 알리는 편이 낫다.</p>
 * <p>The third cannot be verified from code: without {@code KKB_ATK_SEND_OUTBOX} the dispatcher's first
 * query fails, and that surfaces on the <b>first cycle</b> rather than at startup. Since it cannot be
 * pre-empted, startup logs it plainly — noisy beats silent.</p>
 *
 * <h2>{@code retry-unknown} 을 설정으로 둔 이유 / why retry-unknown is configuration</h2>
 * <p>{@code UNKNOWN} 을 재시도해도 되는지는 <b>벤더의 멱등성</b>에 달려 있고, 그것은 확인되지
 * 않았다(spike A1-03, RISK-A07). 코드가 그 답을 아는 척하면 안 된다. 기본값은 재시도하지 않는
 * 쪽이다 — 중복 발송보다 사람이 확인하는 목록이 낫다.</p>
 * <p>Whether {@code UNKNOWN} may be retried depends on <b>vendor idempotency</b>, which is unverified
 * (spike A1-03, RISK-A07); the code must not pretend to know. The default is not to retry: a list a
 * human reviews beats a duplicate send.</p>
 *
 * // source: jex.iris_admin.xml:134-145 — the legacy channel configuration
 * // source: mapping/analysis/ANALYSIS-A2-05-vendor-transport.md
 * // req: FR-ATS-004, FR-ATS-005, ADR-ATK-023, ADR-ATK-025, RISK-A07, RISK-A13
 */
@Configuration
public class AlimTalkDispatchConfig {

    private static final Logger log = LoggerFactory.getLogger(AlimTalkDispatchConfig.class);

    /**
     * 벤더 OAuth 토큰 공급원을 등록한다. / Registers the vendor OAuth token source.
     *
     * <p>⚠ <b>미완</b>: 실제 토큰 발급은 {@code POST <base>/oauth/2.0/token} 이며 클라이언트
     * 자격증명이 레거시에서는 DB 채널 테이블({@code FINChannel})에 있다 — 그 표의 스키마와
     * 소유자를 아직 조사하지 않았다(AMB-A10). 그래서 여기서는 발급자를 등록하지 않고,
     * <b>토큰을 요구하는 순간 예외</b>를 던지는 구현을 둔다.</p>
     * <p>⚠ <b>Incomplete</b>: real issuance is {@code POST <base>/oauth/2.0/token}, and in the legacy
     * the client credential lives in a database channel table ({@code FINChannel}) whose schema and
     * owner are not yet examined (AMB-A10). No issuer is registered here; instead the implementation
     * <b>throws when a token is demanded</b>.</p>
     *
     * <p>빈 문자열을 돌려주거나 요청을 인증 없이 보내지 않는 이유: 둘 다 벤더에서 거절되지만
     * <b>거절의 이유가 우리 로그에 드러나지 않는다</b>. 예외를 던지면 아웃박스의
     * {@code LAST_ERROR} 에 "token unavailable" 이 남고, 운영자가 무엇이 빠졌는지 안다.</p>
     * <p>It does not return an empty string or send unauthenticated: both would be rejected by the
     * vendor, but <b>the reason would not appear in our logs</b>. Throwing records "token unavailable"
     * in the outbox's {@code LAST_ERROR}, so an operator can see what is missing.</p>
     *
     * @return 토큰 공급원 / the token source
     *
     * // req: FR-ATS-004, AMB-A10
     */
    @Bean
    public CooconAlertClient.VendorOAuthTokens vendorOAuthTokens() {
        return isCd -> {
            throw new IllegalStateException(
                    "vendor OAuth token issuance is not implemented (AMB-A10): the client credential"
                            + " lives in the FINChannel table, whose schema is not yet known."
                            + " Institution=" + isCd);
        };
    }

    /**
     * 벤더 클라이언트를 등록한다. / Registers the vendor client.
     *
     * @param baseUrl 벤더 기본 URL — 기본값 없음 / the vendor base URL, no default
     * @param tokens  토큰 공급원 / the token source
     * @return 클라이언트 / the client
     *
     * // req: FR-ATS-004, ADR-ATK-025
     */
    @Bean
    @ConditionalOnProperty(name = "iris.alimtalk.dispatch.enabled", havingValue = "true")
    public AlimTalkVendorClient alimTalkVendorClient(
            @Value("${iris.alimtalk.vendor.base-url}") String baseUrl,
            CooconAlertClient.VendorOAuthTokens tokens) {
        // connectTimeout 은 레거시 채널 설정과 같은 60초다. 요청별 read 타임아웃은
        // CooconAlertClient 가 건다.
        // The connect timeout matches the legacy channel configuration at 60 s; the per-request read
        // timeout is applied by CooconAlertClient.
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(CooconAlertClient.CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        log.info("AlimTalk vendor client wired to {} (dispatch enabled)", baseUrl);
        return new CooconAlertClient(http, new ObjectMapper(), baseUrl, tokens);
    }

    /**
     * 디스패처를 등록한다. / Registers the dispatcher.
     *
     * @param outbox       아웃박스 매퍼 / the outbox mapper
     * @param vendor       벤더 클라이언트 / the vendor client
     * @param clock        시계 / the clock
     * @param batchSize    한 주기 최대 행 수 / rows per cycle
     * @param maxAttempts  재시도 상한 / the retry ceiling
     * @param retryUnknown {@code UNKNOWN} 재시도 여부 / whether to retry UNKNOWN
     * @param claimSeconds 클레임 유지 시간(초) / the claim hold in seconds
     * @return 디스패처 / the dispatcher
     *
     * // req: FR-ATS-005, ADR-ATK-023, RISK-A07
     */
    @Bean
    @ConditionalOnProperty(name = "iris.alimtalk.dispatch.enabled", havingValue = "true")
    public OutboxDispatcher outboxDispatcher(
            OutboxMapper outbox,
            AlimTalkVendorClient vendor,
            Clock clock,
            @Value("${iris.alimtalk.dispatch.batch-size:50}") int batchSize,
            @Value("${iris.alimtalk.dispatch.max-attempts:5}") int maxAttempts,
            @Value("${iris.alimtalk.dispatch.retry-unknown:false}") boolean retryUnknown,
            @Value("${iris.alimtalk.dispatch.claim-seconds:120}") long claimSeconds) {

        if (retryUnknown) {
            // 이 설정을 켠 것은 벤더 멱등성이 확인되었다는 주장이다. 주장이 로그에 남아야
            // 한다 — 나중에 중복 발송을 조사할 때 이 한 줄이 출발점이 된다.
            // Enabling this asserts that vendor idempotency was confirmed. The assertion must appear in
            // the log: when duplicate sends are later investigated, this line is where it starts.
            log.warn("AlimTalk dispatch will RETRY 'UNKNOWN' rows. This is safe only if the vendor"
                    + " de-duplicates on (is_cd, tran_id) — see RISK-A07 / spike A1-03. If it does not,"
                    + " a retry is a duplicate send.");
        }

        // A2-02 DDL 이 적용되었는지는 코드로 확인할 수 없다. 첫 주기에 질의가 실패하므로
        // 기동 시 분명히 알린다.
        // Whether the A2-02 DDL is applied cannot be checked from code; the query fails on the first
        // cycle, so this is stated plainly at startup.
        log.info("AlimTalk outbox dispatcher registered (batch={}, maxAttempts={}, claim={}s,"
                        + " retryUnknown={}). Requires KKB_ATK_SEND_OUTBOX to exist (A2-02 DDL);"
                        + " without it the first cycle fails.",
                batchSize, maxAttempts, claimSeconds, retryUnknown);

        return new OutboxDispatcher(
                outbox, vendor, clock, batchSize, maxAttempts, retryUnknown, Duration.ofSeconds(claimSeconds));
    }
}
