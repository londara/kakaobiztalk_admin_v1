package com.webcash.iris.common.logging;

import com.webcash.iris.common.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청 단위 로깅 필터. / Per-request logging filter.
 *
 * <p>모든 요청에 상관 식별자를 부여하고, 완료 시 한 줄을 남긴다. 형식은
 * {@code EVENT key=value} — 이벤트명이 앞에 오므로 {@code grep REQ} 한 번으로 접근 로그만
 * 추려낼 수 있고, key=value 는 파싱하기 쉽다.</p>
 * <p>Assigns a correlation id to every request and emits one line on completion, in
 * {@code EVENT key=value} form: the event name leads so {@code grep REQ} isolates the access log,
 * and key=value parses easily.</p>
 *
 * <h2>기록하지 않는 것 / what is deliberately not recorded</h2>
 * <table>
 *   <caption>제외 항목과 이유 / exclusions and their reasons</caption>
 *   <tr><th>항목</th><th>이유</th></tr>
 *   <tr><td>쿼리 문자열 / query string</td>
 *       <td>조회 조건에 개인정보가 들어갈 수 있다. 문자내역이 {@code GET} 이 아니라
 *           {@code POST} 인 이유가 바로 조건에 전화번호가 포함되기 때문이다. 지금은 안전한
 *           파라미터만 있더라도, 파라미터가 추가되는 날 로그가 조용히 PII 저장소가 된다.</td></tr>
 *   <tr><td>요청·응답 본문 / bodies</td>
 *       <td>비밀번호·OTP 코드·전화번호가 모두 본문으로 오간다.</td></tr>
 *   <tr><td>이메일 / email</td>
 *       <td>NFR-SEC-LOG-L01. {@link ActorRef} 가 가명으로 대체한다.</td></tr>
 *   <tr><td>쿠키·인증 헤더 / cookies and auth headers</td>
 *       <td>세션 식별자가 로그에 남으면 로그 열람 권한이 세션 탈취 수단이 된다.</td></tr>
 * </table>
 *
 * <p>남기는 것은 <b>메서드·경로·상태·소요시간·가명 행위자</b>다. 이 다섯 가지로 "언제
 * 무엇이 느렸는가", "어떤 엔드포인트가 403 을 쏟아내는가" 를 답할 수 있고, 그 이상이
 * 필요하면 상관 식별자로 감사 기록에 간다.</p>
 * <p>What it does record — method, path, status, duration and pseudonymous actor — answers "what
 * was slow" and "which endpoint is emitting 403s". Anything further is reached through the
 * correlation id into the audit record.</p>
 *
 * <p>{@code @Order(10)} 으로 {@link com.webcash.iris.common.tenant.TenantContextFilter}(20)
 * 보다 <b>먼저</b> 실행한다. 테넌트 확립 과정에서 예외가 나더라도 요청 로그는 남아야 하기
 * 때문이다.</p>
 * <p>Ordered before the tenant filter so a request is still logged when establishing the tenant
 * context is itself what fails.</p>
 *
 * // req: NFR-SEC-LOG-L01, NFR-SEC-LOG-D01, NFR-OPS-AUDIT-D01
 */
@Component
@Order(10)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    /** 느린 요청 경계(ms) — 이 값을 넘으면 WARN. / Slow-request threshold in ms; above this it is a WARN. */
    // req: NFR-PERF-D01, NFR-PERF-D02 (P95 < 1s)
    private static final long SLOW_REQUEST_MS = 1_000L;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        CorrelationId.begin();
        long startedAt = System.nanoTime();

        try {
            chain.doFilter(request, response);
        } finally {
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;

            // 행위자는 체인 실행 뒤에 읽는다. 테넌트 컨텍스트는 뒤에 오는 필터가
            // 확립하므로, 진입 시점에는 아직 비어 있다.
            // The actor is read after the chain: the tenant context is established by a later
            // filter, so it is still empty on the way in.
            String actor = TenantContext.isBound()
                    ? ActorRef.of(TenantContext.require().email())
                    : ActorRef.ANONYMOUS;

            int status = response.getStatus();

            if (status >= 500) {
                log.error("REQ method={} path={} status={} ms={} actor={}",
                        request.getMethod(), request.getRequestURI(), status, elapsedMs, actor);
            } else if (status == 401 || status == 403) {
                // 거부는 INFO 가 아니라 WARN 이다. 한 행위자가 짧은 시간에 403 을 반복하는
                // 것은 탐색 행위의 신호이며, 그 패턴이 정상 트래픽에 묻히면 보이지 않는다.
                // Denials are WARN, not INFO: one actor repeating 403s in a short window is a
                // probing signal, and it disappears if buried in ordinary traffic.
                log.warn("REQ method={} path={} status={} ms={} actor={}",
                        request.getMethod(), request.getRequestURI(), status, elapsedMs, actor);
            } else if (elapsedMs >= SLOW_REQUEST_MS) {
                log.warn("REQ_SLOW method={} path={} status={} ms={} actor={}",
                        request.getMethod(), request.getRequestURI(), status, elapsedMs, actor);
            } else {
                log.info("REQ method={} path={} status={} ms={} actor={}",
                        request.getMethod(), request.getRequestURI(), status, elapsedMs, actor);
            }

            CorrelationId.clear();
        }
    }
}
