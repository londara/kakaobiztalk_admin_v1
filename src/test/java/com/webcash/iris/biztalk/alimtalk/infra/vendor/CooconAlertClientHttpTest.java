package com.webcash.iris.biztalk.alimtalk.infra.vendor;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.webcash.iris.biztalk.alimtalk.domain.OutboxStatus;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link CooconAlertClient} 를 실제 HTTP 로 검증한다.
 * Verifies {@link CooconAlertClient} over real HTTP.
 *
 * <h2>왜 JDK 내장 서버를 쓰는가 / why the JDK's built-in server</h2>
 * <p>Docker 가 금지되어 있고(RISK-A12) Maven 이 없어 WireMock 같은 의존성을 새로 들일 수 없다.
 * {@code com.sun.net.httpserver.HttpServer} 는 JDK 에 들어 있으므로 <b>의존성 없이</b> 실제
 * 소켓·실제 HTTP 로 검증할 수 있다. 벤더 클라이언트에서 가장 위험한 부분은 분기 판정(2xx 대
 * 4xx 대 타임아웃)인데, 그것은 목(mock) 으로는 확인되지 않는다.</p>
 * <p>Docker is prohibited (RISK-A12) and Maven is unavailable, so a dependency like WireMock cannot be
 * added. {@code com.sun.net.httpserver.HttpServer} ships with the JDK, giving real sockets and real HTTP
 * with <b>no dependency</b>. The riskiest part of this client is its branch classification — 2xx versus
 * 4xx versus timeout — and a mock cannot establish that.</p>
 *
 * <p><b>여전히 증명하지 못하는 것</b>: 실제 Coocon 이 이 요청을 받아들이는지. 이 테스트는 우리가
 * 세운 가정({@code {"RSMS": …}})을 고정할 뿐이며, 가정이 확정되면 이 테스트가 먼저 깨져 그 사실을
 * 알린다 — 그것이 이 테스트의 값이다(RISK-A02).</p>
 * <p><b>Still unproven</b>: that the real Coocon accepts this request. These tests pin <b>our
 * assumption</b> ({@code {"RSMS": …}}) so that when it is settled they break first and say so — which is
 * the point of them (RISK-A02).</p>
 *
 * // source: IMO.ADV_KKO_AT_SEND2, jex/impl/OAuthHTTPConnection.java
 * // req: FR-ATS-004, ADR-ATK-025, RISK-A02, RISK-A07, RISK-A12
 */
class CooconAlertClientHttpTest {

    private HttpServer server;
    private String baseUrl;
    private final List<String> receivedBodies = new ArrayList<>();
    private final AtomicReference<String> receivedAuthorization = new AtomicReference<>();
    private final AtomicReference<String> receivedContentType = new AtomicReference<>();
    private final AtomicReference<String> receivedPath = new AtomicReference<>();
    private final AtomicInteger responseCode = new AtomicInteger(200);
    private final AtomicInteger delayMillis = new AtomicInteger(0);

    @BeforeEach
    void startServer() throws IOException {
        // 포트 0 — OS 가 빈 포트를 고른다. 고정 포트를 쓰면 병렬 실행에서 충돌한다.
        // Port 0 lets the OS pick a free port; a fixed port would collide under parallel execution.
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        receivedPath.set(exchange.getRequestURI().getPath());
        receivedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        receivedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
        receivedBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

        int delay = delayMillis.get();
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        byte[] body = "{\"CSTM_RSMS\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(responseCode.get(), body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private CooconAlertClient client() {
        return new CooconAlertClient(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                new ObjectMapper(),
                baseUrl,
                isCd -> "Bearer token-for-" + isCd);
    }

    @Test
    @DisplayName("RISK-A02 — payload 가 RSMS 로 감싸여 나간다 / the payload is wrapped as RSMS")
    // req: FR-ATS-004, RISK-A02
    void payloadIsWrappedAsRsms() throws Exception {
        // ⚠ 이것이 <b>가정</b>이다. 계약 IMO.ADV_KKO_AT_SEND2 는 단일 입력 필드 RSMS 만
        //   선언하고 마샬링 형태는 jex IMO 계층이 정하는데 그 코드가 여기 없다. 확정되면 이
        //   어서션이 먼저 깨진다 — 그것이 이 테스트를 둔 이유다.
        //   This is the ASSUMPTION. The contract declares only the single input field RSMS and the
        //   marshalling shape is decided by the jex IMO layer, whose code is not here. When it is
        //   settled this assertion breaks first, which is why the test exists.
        String payload = "{\"is_cd\":\"K00001\",\"tran_id\":\"T260819001\"}";

        VendorSendResult result = client().send("K00001", "T260819001", payload);

        assertThat(result.status()).isEqualTo(OutboxStatus.SENT);
        assertThat(receivedBodies).hasSize(1);

        JsonNode sent = new ObjectMapper().readTree(receivedBodies.get(0));
        assertThat(sent.fieldNames()).toIterable().containsExactly("RSMS");
        assertThat(sent.get("RSMS").asText()).isEqualTo(payload);
    }

    @Test
    @DisplayName("경로·헤더가 레거시 설정과 같다 / the path and headers match the legacy")
    // req: FR-ATS-004, ADR-ATK-025
    void pathAndHeadersMatchTheLegacy() {
        client().send("K00001", "T260819001", "{}");

        // IMO.ADV_KKO_AT_SEND2 — _IMO_APPEND_URL
        assertThat(receivedPath.get()).isEqualTo("/advising/kakao/at_send");
        // OAuthHTTPConnection:79 — Authorization: <token_type> <access_token>
        assertThat(receivedAuthorization.get()).isEqualTo("Bearer token-for-K00001");
        // jex.iris_admin.xml:143 — Content-Type: application/json, charSet utf8
        assertThat(receivedContentType.get()).startsWith("application/json");
    }

    @Test
    @DisplayName("2xx 는 접수다 / a 2xx is acceptance")
    // req: FR-ATS-004, FR-ATS-005
    void twoHundredIsAcceptance() {
        responseCode.set(202);

        VendorSendResult result = client().send("K00001", "T260819001", "{}");

        assertThat(result.status()).isEqualTo(OutboxStatus.SENT);
        assertThat(result.httpCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("4xx 는 확정 실패다 — 재시도 안전 / a 4xx is an established failure, safe to retry")
    // req: FR-ATS-005, RISK-A07
    void fourHundredIsEstablishedFailure() {
        responseCode.set(400);

        VendorSendResult result = client().send("K00001", "T260819001", "{}");

        assertThat(result.status()).isEqualTo(OutboxStatus.FAILED);
        assertThat(result.status().isSafeToRetry()).isTrue();
        assertThat(result.httpCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("5xx 는 확정 실패다 / a 5xx is an established failure")
    // req: FR-ATS-005, RISK-A07
    void fiveHundredIsEstablishedFailure() {
        responseCode.set(503);

        VendorSendResult result = client().send("K00001", "T260819001", "{}");

        assertThat(result.status()).isEqualTo(OutboxStatus.FAILED);
        assertThat(result.httpCode()).isEqualTo(503);
    }

    @Test
    @DisplayName("60초 안의 느린 응답은 접수다 / a slow response within 60s is still acceptance")
    // req: FR-ATS-004, ADR-ATK-025
    void slowResponseWithinTimeoutIsAcceptance() {
        // 이 테스트가 <b>하지 않는</b> 것을 먼저 적는다: read 타임아웃 분기를 태우지 않는다.
        // 클라이언트의 READ_TIMEOUT 은 60초 상수이므로 그 분기를 실제로 태우려면 서버를
        // 60초 넘게 지연시켜야 하고, 그런 테스트는 스위트를 쓸 수 없게 만든다.
        //
        // 타임아웃 분기(UNKNOWN)는 CooconAlertClientTest#ioFailureIsTreatedAsUnknown 이
        // 연결 실패 경로로 덮는다. 여기서 확인하는 것은 <b>느리지만 성공한</b> 응답이
        // 타임아웃으로 오해되지 않는다는 것뿐이다 — 이름이 그렇게 되어 있어야 한다.
        //
        // What this test does NOT do, stated first: it does not exercise the read-timeout branch. The
        // client's READ_TIMEOUT is a 60-second constant, so reaching that branch would need the server
        // to stall for over 60 seconds, and such a test would make the suite unusable.
        //
        // The UNKNOWN branch is covered via the connection-failure path in
        // CooconAlertClientTest#ioFailureIsTreatedAsUnknown. All that is checked here is that a
        // slow-but-successful response is not mistaken for a timeout — and the name should say so.
        delayMillis.set(1200);

        VendorSendResult result = client().send("K00001", "T260819001", "{}");

        assertThat(result.status()).isEqualTo(OutboxStatus.SENT);
    }

    @Test
    @DisplayName("NFR-SEC-PII-A01 — 결과 요약에 수신번호가 없다 / no recipient in the summary")
    // req: NFR-SEC-PII-A01, D-A30
    void summaryCarriesNoRecipient() {
        // 결과 요약은 LAST_ERROR 컬럼에 저장된다. 레거시는 data_log=true 로 payload 전체를
        // 로그에 남겼고(D-A30), 같은 실수를 하면 수신번호가 데이터베이스에 남는다.
        // The summary is stored in LAST_ERROR. The legacy logged the whole payload (D-A30); repeating
        // that would put recipient numbers in the database.
        responseCode.set(500);

        VendorSendResult result =
                client().send("K00001", "T260819001", "{\"receiver_number\":[\"01011112222\"]}");

        assertThat(result.detail()).doesNotContain("01011112222");
        assertThat(result.detail()).doesNotContain("Bearer");
        assertThat(result.detail()).isEqualTo("HTTP 500");
    }
}
