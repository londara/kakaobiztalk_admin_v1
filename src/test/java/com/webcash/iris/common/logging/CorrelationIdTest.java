package com.webcash.iris.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * {@link CorrelationId} 검증. / Verification for {@link CorrelationId}.
 *
 * // req: NFR-OPS-AUDIT-D01
 */
class CorrelationIdTest {

    @AfterEach
    void tearDown() {
        CorrelationId.clear();
    }

    @Test
    @DisplayName("begin() 은 식별자를 MDC 에 넣는다 / begin() binds an id to the MDC")
    // req: NFR-OPS-AUDIT-D01
    void bindsToMdc() {
        String id = CorrelationId.begin();

        assertThat(id).isNotBlank();
        assertThat(MDC.get(CorrelationId.MDC_KEY)).isEqualTo(id);
        assertThat(CorrelationId.current()).isEqualTo(id);
    }

    @Test
    @DisplayName("요청마다 다른 식별자를 만든다 / a different id per request")
    // req: NFR-OPS-AUDIT-D01
    void isUniquePerRequest() {
        String first = CorrelationId.begin();
        String second = CorrelationId.begin();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("clear() 후에는 남지 않는다 / nothing remains after clear()")
    // req: NFR-OPS-AUDIT-D01
    void clearsCompletely() {
        // 정리 누락은 스레드 풀에서 다음 요청이 이전 식별자를 물려받게 만든다 — 로그가
        // 서로 다른 두 요청을 하나로 보이게 하고, 그 상태는 조사에서 알아채기 어렵다.
        // Failing to clear lets the next request inherit the previous id on a pooled thread,
        // making the log present two requests as one — hard to notice during an investigation.
        CorrelationId.begin();
        CorrelationId.clear();

        assertThat(CorrelationId.current()).isNull();
        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("바인딩 전에는 null 이다 / current() is null before binding")
    // req: NFR-OPS-AUDIT-D01
    void nullBeforeBinding() {
        // 요청 밖(스케줄러 등)에서 도는 코드가 여기에 해당한다. null 이 정상이며, 감사
        // 기록의 correlationId 가 비어 있는 것은 "요청 맥락이 아니다" 라는 정보다.
        // Code running outside a request — a scheduled job — sits here. Null is correct, and an
        // empty correlationId on an audit record carries the information that there was no
        // request context.
        assertThat(CorrelationId.current()).isNull();
    }
}
