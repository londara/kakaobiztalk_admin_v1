package com.webcash.iris.biztalk.alimtalk.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * {@link OutboxStatus} 검증. / Verification for {@link OutboxStatus}.
 *
 * <p>열거형 하나에 테스트 클래스를 두는 이유: 이 타입의 두 술어가 <b>발송 정책 그 자체</b>다.
 * {@code isSafeToRetry} 가 {@code UNKNOWN} 에 참을 돌려주는 순간 자동 재시도가 중복 발송이 되고,
 * 그 변경은 한 글자로 가능하다. 술어가 정책이면 술어에 테스트가 있어야 한다.</p>
 * <p>Why an enum gets its own test class: these two predicates <b>are</b> the send policy. The moment
 * {@code isSafeToRetry} returns true for {@code UNKNOWN}, automatic retry becomes duplicate sending — a
 * one-character change. A predicate that carries policy needs tests.</p>
 *
 * // req: FR-ATS-005, RISK-A07
 */
class OutboxStatusTest {

    @Test
    @DisplayName("RISK-A07 — UNKNOWN 은 무조건 안전한 재시도가 아니다 / UNKNOWN is never unconditionally retryable")
    // req: FR-ATS-005, RISK-A07
    void unknownIsNotUnconditionallyRetryable() {
        // 이 어서션이 이 슬라이스의 안전 성질 하나를 혼자 지킨다. 벤더 멱등성이 확인되지
        // 않았으므로(spike A1-03) "알 수 없음" 을 무조건 재시도 가능으로 두면 중복 발송을
        // 코드가 허락하는 셈이다. 재시도 여부는 설정이 정해야 하고, 이 술어가 정해서는 안 된다.
        // This single assertion holds one of the slice's safety properties on its own. With vendor
        // idempotency unverified (spike A1-03), treating "unknown" as unconditionally retryable would be
        // the code permitting duplicates. That decision belongs to configuration, not to this predicate.
        assertThat(OutboxStatus.UNKNOWN.isSafeToRetry()).isFalse();
    }

    @Test
    @DisplayName("전달되지 않음이 확인된 상태만 재시도 안전이다 / only established non-delivery is retry-safe")
    // req: FR-ATS-005
    void onlyEstablishedNonDeliveryIsRetrySafe() {
        assertThat(OutboxStatus.PENDING.isSafeToRetry()).isTrue();
        assertThat(OutboxStatus.FAILED.isSafeToRetry()).isTrue();

        assertThat(OutboxStatus.SENT.isSafeToRetry()).isFalse();
        assertThat(OutboxStatus.UNKNOWN.isSafeToRetry()).isFalse();
        assertThat(OutboxStatus.DEAD.isSafeToRetry()).isFalse();
    }

    @Test
    @DisplayName("종착 상태는 SENT 와 DEAD 뿐이다 / only SENT and DEAD are terminal")
    // req: FR-ATS-005, NFR-OPS-A02
    void onlySentAndDeadAreTerminal() {
        // UNKNOWN 이 종착이 아닌 것이 중요하다. 종착으로 두면 운영자가 확인해야 할 목록에서
        // 사라지고, 전달되었는지 모르는 통지가 조용히 잊힌다.
        // That UNKNOWN is not terminal matters: were it terminal it would drop off the list an operator
        // must review, and a notification of unknown fate would be quietly forgotten.
        assertThat(OutboxStatus.SENT.isTerminal()).isTrue();
        assertThat(OutboxStatus.DEAD.isTerminal()).isTrue();

        assertThat(OutboxStatus.PENDING.isTerminal()).isFalse();
        assertThat(OutboxStatus.FAILED.isTerminal()).isFalse();
        assertThat(OutboxStatus.UNKNOWN.isTerminal()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(OutboxStatus.class)
    @DisplayName("종착이면서 재시도 안전인 상태는 없다 / no status is both terminal and retry-safe")
    // req: FR-ATS-005
    void noStatusIsBothTerminalAndRetrySafe(OutboxStatus status) {
        // 두 술어가 동시에 참이면 디스패처가 이미 끝난 행을 다시 보낸다. 상태를 새로 추가할 때
        // 이 불변식을 깨기 쉬우므로 열거형 전체에 대해 검사한다.
        // Both true at once would have the dispatcher resend a finished row. Adding a status makes this
        // invariant easy to break, so it is checked across the whole enum.
        assertThat(status.isTerminal() && status.isSafeToRetry())
                .as("%s must not be both terminal and retry-safe", status)
                .isFalse();
    }

    @ParameterizedTest
    @EnumSource(OutboxStatus.class)
    @DisplayName("DDL 의 CHECK 제약과 이름이 일치한다 / every name matches the DDL CHECK constraint")
    // req: FR-ATS-005, CONST-DATA-A01
    void everyNameMatchesTheDdlConstraint(OutboxStatus status) {
        // V2__alimtalk_outbox.sql 의 CK_KKB_ATK_SEND_OUTBOX_STATUS 가 이 다섯 값을 열거한다.
        // 열거형에 값을 더하고 DDL 을 잊으면 INSERT 가 실행 시점에 실패하고, 그 실패는
        // 접수 자체를 잃게 만든다. 여기서 잡는다.
        // CK_KKB_ATK_SEND_OUTBOX_STATUS in V2__alimtalk_outbox.sql enumerates these five. Adding a value
        // and forgetting the DDL makes the INSERT fail at runtime, losing the acceptance. Caught here.
        assertThat(status.name())
                .isIn("PENDING", "SENT", "FAILED", "UNKNOWN", "DEAD");
        assertThat(status.name()).hasSizeLessThanOrEqualTo(8);
    }
}
