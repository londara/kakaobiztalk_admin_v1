package com.webcash.iris.biztalk.alimtalk.infra.vendor;

import static org.assertj.core.api.Assertions.assertThat;

import com.webcash.iris.biztalk.alimtalk.domain.OutboxStatus;
import com.webcash.iris.biztalk.alimtalk.infra.vendor.CooconAlertClient.CachingOAuthTokens;
import com.webcash.iris.biztalk.alimtalk.infra.vendor.CooconAlertClient.CachingOAuthTokens.CachedToken;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link CooconAlertClient} 검증 — 토큰 캐시와 결과 분류.
 * Verification for {@link CooconAlertClient}: the token cache and outcome classification.
 *
 * <p><b>이 테스트가 증명하지 못하는 것</b>: 실제 벤더가 이 요청을 받아들이는지. {@code RSMS}
 * 마샬링 형태와 {@code CSTM_RSMS} 응답 구조는 확인되지 않았다(RISK-A02). 여기서 고정하는 것은
 * <b>우리가 세운 가정</b>이며, 가정이 확정되면 이 테스트가 먼저 깨져 그 사실을 알린다.</p>
 * <p><b>What these tests cannot show</b>: that the real vendor accepts this request. The {@code RSMS}
 * marshalling shape and {@code CSTM_RSMS} structure are unverified (RISK-A02). What is pinned here is
 * <b>our assumption</b>, so that when it is settled these tests break first and say so.</p>
 *
 * // source: jex/impl/OAuthToken.java, jex/impl/OAuthHTTPConnection.java
 * // req: FR-ATS-004, RISK-A02, RISK-A07, D-A39
 */
class CooconAlertClientTest {

    private static Clock fixedAt(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneId.of("Asia/Seoul"));
    }

    @Nested
    @DisplayName("D-A39 — 토큰 캐시 / the token cache")
    class TokenCache {

        @Test
        @DisplayName("유효한 토큰은 재발급하지 않는다 / a usable token is not re-issued")
        // req: FR-ATS-004
        void usableTokenIsReused() {
            AtomicInteger issues = new AtomicInteger();
            Clock clock = fixedAt("2026-08-19T05:00:00Z");
            CachingOAuthTokens tokens = new CachingOAuthTokens(
                    (isCd, now) -> {
                        issues.incrementAndGet();
                        return new CachedToken("tok-" + isCd, "Bearer", now.plusSeconds(3600));
                    },
                    clock);

            assertThat(tokens.authorizationHeader("K00001")).isEqualTo("Bearer tok-K00001");
            assertThat(tokens.authorizationHeader("K00001")).isEqualTo("Bearer tok-K00001");

            assertThat(issues.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("기관마다 토큰이 따로다 / tokens are per institution")
        // req: FR-ATS-004, NFR-SEC-CRED-A01
        void tokensArePerInstitution() {
            // 레거시 D-A39 의 결과가 이것이다 — 동기화 없는 HashMap 에서 토큰이 뒤섞이면
            // 발송이 잘못된 기관 명의로 나간다. 조용히.
            // This is what D-A39 could produce: with an unsynchronised HashMap, crossed tokens send in
            // the wrong institution's name, silently.
            CachingOAuthTokens tokens = new CachingOAuthTokens(
                    (isCd, now) -> new CachedToken("tok-" + isCd, "Bearer", now.plusSeconds(3600)),
                    fixedAt("2026-08-19T05:00:00Z"));

            assertThat(tokens.authorizationHeader("K00001")).isEqualTo("Bearer tok-K00001");
            assertThat(tokens.authorizationHeader("K00002")).isEqualTo("Bearer tok-K00002");
        }

        @Test
        @DisplayName("만료가 가까운 토큰은 미리 바꾼다 / a token close to expiry is replaced early")
        // req: FR-ATS-004
        void tokenNearExpiryIsReplaced() {
            // 만료 30초 전 토큰으로 60초짜리 호출을 시작하면 호출 중에 만료된다. 레거시는
            // 여유 없이 만료 시각만 비교했다.
            // Starting a 60-second call with a token 30 seconds from expiry means it expires mid-call;
            // the legacy compared against the expiry with no margin.
            Instant now = Instant.parse("2026-08-19T05:00:00Z");
            CachedToken almostExpired = new CachedToken("old", "Bearer", now.plusSeconds(30));

            assertThat(almostExpired.isUsableAt(now)).isFalse();
        }

        @Test
        @DisplayName("빈 토큰은 쓰지 않는다 / an empty token is not usable")
        // req: FR-ATS-004
        void emptyTokenIsNotUsable() {
            // 레거시도 "".equals(accessToken) 을 검사했다 — 발급이 실패하면 빈 문자열이
            // 캐시에 들어갈 수 있었다는 뜻이다.
            // The legacy checked "".equals(accessToken) too, implying a failed issue could leave an
            // empty string in the cache.
            Instant now = Instant.parse("2026-08-19T05:00:00Z");
            assertThat(new CachedToken("", "Bearer", now.plusSeconds(3600)).isUsableAt(now)).isFalse();
        }

        @Test
        @DisplayName("동시 요청에도 기관별 발급은 한 번이다 / concurrent callers issue once per institution")
        // req: FR-ATS-004, D-A39
        void concurrentCallersIssueOnce() throws Exception {
            // 레거시는 ReentrantLock 을 두고도 읽는 쪽이 그것을 취하지 않았다. 여기서는
            // ConcurrentHashMap.compute 가 기관별로 원자적이므로 발급이 중복되지 않는다.
            // The legacy held a ReentrantLock that its readers never took. Here
            // ConcurrentHashMap.compute is atomic per key, so issuing cannot duplicate.
            AtomicInteger issues = new AtomicInteger();
            CachingOAuthTokens tokens = new CachingOAuthTokens(
                    (isCd, now) -> {
                        issues.incrementAndGet();
                        return new CachedToken("tok-" + isCd, "Bearer", now.plusSeconds(3600));
                    },
                    fixedAt("2026-08-19T05:00:00Z"));

            int threads = 50;
            ExecutorService pool = Executors.newFixedThreadPool(16);
            CountDownLatch start = new CountDownLatch(1);
            Set<String> seen = ConcurrentHashMap.newKeySet();
            try {
                for (int i = 0; i < threads; i++) {
                    pool.submit(() -> {
                        start.await();
                        seen.add(tokens.authorizationHeader("K00001"));
                        return null;
                    });
                }
                start.countDown();
                pool.shutdown();
                assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
            } finally {
                pool.shutdownNow();
            }

            assertThat(seen).containsExactly("Bearer tok-K00001");
            assertThat(issues.get()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("RISK-A07 — 결과 분류 / outcome classification")
    class OutcomeClassification {

        @Test
        @DisplayName("토큰을 얻지 못하면 확정 실패다 / no token means an established failure")
        // req: FR-ATS-004, RISK-A07
        void missingTokenIsEstablishedFailure() {
            // 토큰이 없으면 요청은 나가지 않았다 — 그러므로 재시도가 안전하다. 레거시는 이
            // 경우 null 을 돌려주고 로그만 남겨(OAuthHTTPConnection:89-91) 발송을 잃었다.
            // Without a token the request never left, so a retry is safe. The legacy returned null and
            // logged (OAuthHTTPConnection:89-91), losing the send.
            CooconAlertClient client = new CooconAlertClient(
                    java.net.http.HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(1))
                            .build(),
                    new com.fasterxml.jackson.databind.ObjectMapper(),
                    "http://localhost:1",
                    isCd -> {
                        throw new IllegalStateException("no credential for " + isCd);
                    });

            VendorSendResult result = client.send("K00001", "T260819001", "{}");

            assertThat(result.status()).isEqualTo(OutboxStatus.FAILED);
            assertThat(result.status().isSafeToRetry()).isTrue();
            assertThat(result.detail()).contains("token unavailable");
        }

        @Test
        @DisplayName("결과 요약에 자격증명이 없다 / no credential appears in the summary")
        // req: NFR-SEC-CRED-A01, D-A30
        void summaryCarriesNoCredential() {
            // 레거시는 data_log=true 로 payload 전체(발신프로필키·수신번호 포함)를 매 발송마다
            // 기록했다(D-A30). 결과 요약은 LAST_ERROR 컬럼에 저장되므로 같은 실수를 하면
            // 자격증명이 데이터베이스에 남는다.
            // The legacy logged the whole payload — profile key and recipient included — on every send
            // (D-A30). This summary is stored in LAST_ERROR, so repeating the mistake would put
            // credential material in the database.
            CooconAlertClient client = new CooconAlertClient(
                    java.net.http.HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(1))
                            .build(),
                    new com.fasterxml.jackson.databind.ObjectMapper(),
                    "http://localhost:1",
                    isCd -> "Bearer super-secret-token-value");

            VendorSendResult result =
                    client.send("K00001", "T260819001", "{\"receiver_number\":[\"01011112222\"]}");

            assertThat(result.detail()).doesNotContain("super-secret-token-value");
            assertThat(result.detail()).doesNotContain("01011112222");
        }

        @Test
        @DisplayName("연결 불가는 UNKNOWN 으로 보수적으로 다룬다 / an I/O failure is treated conservatively")
        // req: FR-ATS-005, RISK-A07
        void ioFailureIsTreatedAsUnknown() {
            // IOException 은 "연결되지 않았다" 와 "전송 중 끊겼다" 를 함께 담는다. 후자는
            // 사실 UNKNOWN 이고 둘을 구분할 정보가 없으므로, FAILED 로 낙관하지 않는다 —
            // FAILED 는 자동 재시도를 허용하고 그것이 중복 발송이 될 수 있다.
            // IOException covers both "never connected" and "cut off mid-transfer"; the latter is
            // genuinely UNKNOWN and the two are indistinguishable, so we do not optimistically call it
            // FAILED, which would permit an automatic retry and hence a possible duplicate.
            CooconAlertClient client = new CooconAlertClient(
                    java.net.http.HttpClient.newBuilder()
                            .connectTimeout(Duration.ofMillis(200))
                            .build(),
                    new com.fasterxml.jackson.databind.ObjectMapper(),
                    "http://127.0.0.1:1",
                    isCd -> "Bearer t");

            VendorSendResult result = client.send("K00001", "T260819001", "{}");

            assertThat(result.status()).isEqualTo(OutboxStatus.UNKNOWN);
            assertThat(result.status().isSafeToRetry()).isFalse();
        }
    }

    @Nested
    @DisplayName("레거시 실측값 / values measured from the legacy")
    class MeasuredValues {

        @Test
        @DisplayName("타임아웃과 경로가 레거시 설정과 같다 / timeouts and paths match the legacy configuration")
        // req: FR-ATS-004, ADR-ATK-025
        void timeoutsAndPathsMatchTheLegacy() {
            // jex.iris_admin.xml:135-137 — connectTimeout / readTimeout / waitTimeout = 60000
            // IMO.ADV_KKO_AT_SEND2 — _IMO_APPEND_URL = /advising/kakao/at_send
            // IMO.OAUTH_TOKEN_ISSUE — KEY_APPEND_URL = /oauth/2.0/token
            assertThat(CooconAlertClient.CONNECT_TIMEOUT).isEqualTo(Duration.ofSeconds(60));
            assertThat(CooconAlertClient.READ_TIMEOUT).isEqualTo(Duration.ofSeconds(60));
            assertThat(CooconAlertClient.SEND_PATH).isEqualTo("/advising/kakao/at_send");
            assertThat(CooconAlertClient.TOKEN_PATH).isEqualTo("/oauth/2.0/token");
        }

        @Test
        @DisplayName("클레임 최소값이 read 타임아웃을 넘는다 / the minimum claim exceeds the read timeout")
        // req: FR-ATS-005, RISK-A07
        void minimumClaimExceedsReadTimeout() {
            // 이 부등식이 깨지면 응답을 기다리는 행을 다른 인스턴스가 다시 집어 중복 발송한다.
            // 두 상수가 서로 다른 클래스에 있으므로, 한쪽만 바뀌는 것을 이 테스트가 막는다.
            // If this inequality breaks, another instance re-claims a row whose response is still
            // outstanding. The two constants live in different classes, so this test is what prevents
            // one being changed without the other.
            assertThat(com.webcash.iris.biztalk.alimtalk.domain.OutboxDispatcher.MINIMUM_CLAIM)
                    .isGreaterThan(CooconAlertClient.READ_TIMEOUT);
        }
    }
}
