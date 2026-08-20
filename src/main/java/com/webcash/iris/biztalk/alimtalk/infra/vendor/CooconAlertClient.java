package com.webcash.iris.biztalk.alimtalk.infra.vendor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coocon 알림톡 전송 클라이언트. / The Coocon AlimTalk send client.
 *
 * <h2>⚠ 이 클래스는 검증되지 않았다 / this class is NOT verified</h2>
 * <p>레거시 소스에서 전송 경로를 복원했으나(ANALYSIS-A2-05-vendor-transport.md) 세 가지가
 * 확인되지 않았다. 실제 벤더 호출이나 벤더 문서 없이는 확정할 수 없다.</p>
 *
 * <table border="1">
 *   <caption>미확인 항목 / unverified</caption>
 *   <tr><th>항목</th><th>여기서 쓰는 가정</th></tr>
 *   <tr><td>{@code RSMS} 마샬링</td><td>{@code {"RSMS": "<payload 문자열>"}} — jex IMO 가 단일
 *       필드를 JSON 객체로 감싼다고 본다</td></tr>
 *   <tr><td>{@code CSTM_RSMS} 응답 구조</td><td>HTTP 2xx 를 접수로 본다. 본문 안의 오류 코드는
 *       해석하지 않는다 — 코드 집합을 모르므로 <b>추측하지 않는다</b></td></tr>
 *   <tr><td>{@code (is_cd, tran_id)} 멱등성</td><td>가정하지 않는다. {@link OutboxStatus} 의
 *       {@code UNKNOWN} 정책이 이 미확인을 흡수한다</td></tr>
 * </table>
 *
 * <p>The transport path was recovered from the legacy source, but three things remain unverified and
 * cannot be settled without the vendor. They are confined here, so confirmation changes one class.</p>
 *
 * <p><b>2xx 를 접수로만 보고 본문을 해석하지 않는 이유</b>: 오류 코드 집합을 모르는 상태에서
 * 본문을 해석하면, 알아보지 못한 오류를 <b>성공으로</b> 읽을 수 있다. 그것은 조용한 미전달이며
 * 이 슬라이스가 없애려는 결함 그 자체다. 해석하지 않으면 적어도 틀린 방향으로 틀리지 않는다 —
 * 게이트웨이가 {@code KKO_MSG_LOG} 에 남기는 전달 상태가 별도로 있기 때문이다.</p>
 * <p><b>Why a 2xx is read as acceptance and the body is not interpreted</b>: interpreting it without
 * knowing the error-code set risks reading an unrecognised error as success — a silent non-delivery,
 * the very defect this slice exists to remove. Not interpreting fails in the safer direction, since the
 * gateway records delivery separately in {@code KKO_MSG_LOG}.</p>
 *
 * // source: jex/impl/OAuthHTTPConnection.java, jex/impl/OAuthToken.java
 * // source: IMO.ADV_KKO_AT_SEND2, IMO.OAUTH_TOKEN_ISSUE, jex.iris_admin.xml:134-145
 * // req: FR-ATS-004, ADR-ATK-025, RISK-A02, RISK-A07, AMB-A10
 */
public class CooconAlertClient implements AlimTalkVendorClient {

    private static final Logger log = LoggerFactory.getLogger(CooconAlertClient.class);

    /**
     * 레거시 채널 설정 실측값 / measured from the legacy channel configuration.
     *
     * <p>{@code jex.iris_admin.xml:135} — {@code <connectTimeout>60000</connectTimeout>}. 배선이
     * 이 값을 {@code HttpClient} 에 걸어야 하므로 공개한다 — 두 곳에 60초를 따로 적으면 한쪽만
     * 바뀐다.</p>
     * <p>Public because the wiring must apply it to the {@code HttpClient}: writing 60 seconds in two
     * places would let one of them change alone.</p>
     */
    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(60);

    /** 레거시 채널 설정 실측값 / measured from the legacy channel configuration. */
    static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

    /** 발송 경로 / the send path. */
    static final String SEND_PATH = "/advising/kakao/at_send";

    /** 토큰 발급 경로 / the token-issue path. */
    static final String TOKEN_PATH = "/oauth/2.0/token";

    private final HttpClient http;
    private final ObjectMapper json;
    private final String baseUrl;
    private final VendorOAuthTokens tokens;

    /**
     * 클라이언트를 만든다. / Creates the client.
     *
     * @param http    HTTP 클라이언트 / the HTTP client
     * @param json    JSON 매퍼 / the JSON mapper
     * @param baseUrl 벤더 기본 URL / the vendor base URL
     * @param tokens  기관별 토큰 공급원 / the per-institution token source
     *
     * // req: FR-ATS-004
     */
    public CooconAlertClient(
            HttpClient http, ObjectMapper json, String baseUrl, VendorOAuthTokens tokens) {
        this.http = http;
        this.json = json;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.tokens = tokens;
    }

    /**
     * payload 를 보낸다. / Sends the payload.
     *
     * @param isCd    이용기관코드 / institution code
     * @param tranId  거래고유번호 / transaction id
     * @param payload 계약 적합 JSON / the conforming JSON
     * @return 세 갈래 결과 / one of three outcomes
     *
     * // req: FR-ATS-004, ADR-ATK-025, RISK-A07
     */
    @Override
    public VendorSendResult send(String isCd, String tranId, String payload) {
        String authorization;
        try {
            authorization = tokens.authorizationHeader(isCd);
        } catch (RuntimeException e) {
            // 토큰을 얻지 못하면 요청은 <b>나가지 않았다</b> — 확정 실패다. 레거시는 이 경우
            // null 을 돌려주고 로그만 남겼다(OAuthHTTPConnection:89-91), 그래서 발송이 조용히
            // 사라졌다.
            // Without a token the request never left, so this is an established failure. The legacy
            // returned null and logged (OAuthHTTPConnection:89-91), and the send vanished quietly.
            return VendorSendResult.notDelivered("token unavailable: " + e.getMessage(), 0);
        }

        String body;
        try {
            // ⚠ 가정: 단일 계약 필드 RSMS 를 JSON 객체로 감싼다. 미확인(RISK-A02).
            //   Assumption: the single RSMS contract field is wrapped in a JSON object. Unverified.
            ObjectNode envelope = json.createObjectNode();
            envelope.put("RSMS", payload);
            body = json.writeValueAsString(envelope);
        } catch (Exception e) {
            return VendorSendResult.notDelivered("could not marshal request: " + e.getMessage(), 0);
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + SEND_PATH))
                .timeout(READ_TIMEOUT)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Authorization", authorization)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            if (code >= 200 && code < 300) {
                // 본문을 해석하지 않는다 — 위 Javadoc 의 이유.
                // The body is not interpreted; see the class Javadoc for why.
                return VendorSendResult.accepted("HTTP " + code);
            }
            // 4xx·5xx 는 요청이 처리되지 않았음을 응답이 말해 준다 — 재시도 안전.
            // A 4xx or 5xx says the request was not processed, so a retry is safe.
            return VendorSendResult.notDelivered("HTTP " + code, code);
        } catch (java.net.http.HttpTimeoutException e) {
            // 요청은 나갔고 응답은 오지 않았다. 전달 여부를 <b>모른다</b>.
            // The request left and no response came: delivery is unknown.
            //
            // 로그에 tran_id 만 남긴다 — payload 에는 수신번호가 평문으로 있고, 레거시는
            // data_log=true 로 그것을 매 발송마다 기록했다(D-A30).
            // Only the tran_id is logged: the payload holds the recipient in clear, and the legacy
            // recorded exactly that on every send via data_log=true (D-A30).
            log.warn("AlimTalk vendor read timeout, delivery unknown (tran_id={}, institution={})",
                    tranId, isCd);
            return VendorSendResult.unknown("read timeout after " + READ_TIMEOUT.toSeconds() + "s");
        } catch (java.io.IOException e) {
            // 연결 자체가 성립하지 않은 경우와 전송 중 끊긴 경우를 IOException 이 함께 담는다.
            // 후자는 사실 UNKNOWN 이지만 구분할 수 있는 정보가 없다 — 그래서 <b>보수적으로</b>
            // UNKNOWN 으로 둔다. FAILED 로 두면 자동 재시도가 중복을 만들 수 있다.
            //
            // IOException covers both "no connection was established" and "cut off mid-transfer".
            // The latter is genuinely UNKNOWN and the two are indistinguishable here, so the
            // conservative choice is UNKNOWN: calling it FAILED would let a retry duplicate a send.
            return VendorSendResult.unknown("I/O failure, delivery indeterminate: " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return VendorSendResult.unknown("interrupted, delivery indeterminate");
        }
    }

    /**
     * 기관별 OAuth 토큰. / Per-institution OAuth tokens.
     *
     * <p>레거시 {@code jex.impl.OAuthToken} 을 대체한다. 그 클래스에는 경쟁 조건이 있었다
     * (D-A39): {@code HashMap} 을 {@code issueToken} 이 락을 쥐고 쓰는데
     * {@code getAccessToken}·{@code isValidToken} 은 <b>락 없이</b> 읽었다. 토큰이 다른 기관의
     * 것으로 쓰이면 발송이 잘못된 기관 명의로 나간다 — 조용히.</p>
     * <p>Replaces the legacy {@code jex.impl.OAuthToken}, which had a race (D-A39): {@code issueToken}
     * wrote a {@code HashMap} under a lock while {@code getAccessToken} and {@code isValidToken} read it
     * <b>without one</b>. A token used under the wrong institution sends in that institution's name,
     * silently.</p>
     *
     * // source: jex/impl/OAuthToken.java
     * // req: FR-ATS-004, NFR-SEC-CRED-A01
     */
    public interface VendorOAuthTokens {

        /**
         * 유효한 {@code Authorization} 헤더 값을 돌려준다. / Returns a valid {@code Authorization} value.
         *
         * @param isCd 이용기관코드 / institution code
         * @return {@code <token_type> <access_token>}
         *
         * // req: FR-ATS-004
         */
        String authorizationHeader(String isCd);
    }

    /**
         * 토큰 캐시 — 기관별, 만료 인식. / A per-institution, expiry-aware token cache.
     *
     * <p>{@code ConcurrentHashMap} 과 {@code compute} 로 기관별 재발급을 원자적으로 한다.
     * 레거시의 D-A39 형태를 재현하지 않는다.</p>
     * <p>Uses {@code ConcurrentHashMap.compute} so re-issue is atomic per institution, and the legacy's
     * D-A39 shape cannot recur.</p>
     *
     * // req: FR-ATS-004, NFR-SEC-CRED-A01
     */
    public static final class CachingOAuthTokens implements VendorOAuthTokens {

        private final Map<String, CachedToken> cache = new ConcurrentHashMap<>();
        private final TokenIssuer issuer;
        private final java.time.Clock clock;

        /**
         * 캐시를 만든다. / Creates the cache.
         *
         * @param issuer 토큰 발급자 / the token issuer
         * @param clock  시계 / the clock
         *
         * // req: FR-ATS-004
         */
        public CachingOAuthTokens(TokenIssuer issuer, java.time.Clock clock) {
            this.issuer = issuer;
            this.clock = clock;
        }

        /**
         * 유효한 헤더 값을 돌려준다 — 필요하면 발급한다.
         * Returns a valid header value, issuing one when needed.
         *
         * @param isCd 이용기관코드 / institution code
         * @return {@code Authorization} 값 / the {@code Authorization} value
         *
         * // req: FR-ATS-004
         */
        @Override
        public String authorizationHeader(String isCd) {
            java.time.Instant now = clock.instant();
            CachedToken token = cache.compute(isCd, (key, existing) -> {
                if (existing != null && existing.isUsableAt(now)) {
                    return existing;
                }
                return issuer.issue(key, now);
            });
            return token.tokenType() + " " + token.accessToken();
        }

        /**
         * 토큰을 발급한다. / Issues a token.
         *
         * // req: FR-ATS-004
         */
        public interface TokenIssuer {
            /**
             * 기관의 토큰을 받아온다. / Obtains a token for an institution.
             *
             * @param isCd 이용기관코드 / institution code
             * @param now  현재 시각 / the current time
             * @return 발급된 토큰 / the issued token
             *
             * // req: FR-ATS-004
             */
            CachedToken issue(String isCd, java.time.Instant now);
        }

        /**
         * 캐시된 토큰. / A cached token.
         *
         * @param accessToken 액세스 토큰 / the access token
         * @param tokenType   토큰 타입 / the token type
         * @param expiresAt   만료 시각 / the expiry
         *
         * // req: FR-ATS-004
         */
        public record CachedToken(String accessToken, String tokenType, java.time.Instant expiresAt) {

            /**
             * 안전 여유 / the safety margin.
             *
             * <p>만료 직전의 토큰으로 60초짜리 호출을 시작하면 호출 중에 만료된다. 레거시는
             * 여유 없이 만료만 비교했다.</p>
             * <p>Starting a 60-second call with a token about to expire means it expires mid-call; the
             * legacy compared against the expiry with no margin.</p>
             */
            static final java.time.Duration MARGIN = java.time.Duration.ofSeconds(90);

            /**
             * 지금 써도 되는가. / Is this usable now?
             *
             * @param now 현재 시각 / the current time
             * @return 여유를 두고도 유효하면 {@code true} / {@code true} when valid with margin
             *
             * // req: FR-ATS-004
             */
            public boolean isUsableAt(java.time.Instant now) {
                return accessToken != null
                        && !accessToken.isEmpty()
                        && now.plus(MARGIN).isBefore(expiresAt);
            }
        }
    }
}
