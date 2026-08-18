package com.webcash.iris.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import com.webcash.iris.auth.api.AuthExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.OrderUtils;

/**
 * 예외 어드바이스 우선순위 회귀. / Exception advice ordering regression.
 *
 * <p>{@link GlobalExceptionHandler} 는 {@code @ExceptionHandler(Exception.class)} 를 갖는다.
 * 이런 포괄 처리자를 추가할 때의 위험은 그것이 <b>구체적인 처리자를 가려 버리는</b> 것이며,
 * 두 어드바이스의 우선순위가 같으면 Spring 은 어느 쪽이 이길지 보장하지 않는다.</p>
 * <p>{@link GlobalExceptionHandler} declares {@code @ExceptionHandler(Exception.class)}. The
 * hazard of adding such a catch-all is that it <b>shadows the specific handlers</b>, and with
 * equal order Spring does not guarantee which advice wins.</p>
 *
 * <p>가려졌을 때의 증상이 이 테스트가 존재하는 이유다: 401 INVALID_CREDENTIALS 나
 * 409 OTP_NOT_REGISTERED 가 500 INTERNAL_ERROR 로 나가고, 로그인 실패가 서버 장애처럼
 * 보인다. 컴파일도 통과하고 다른 단위 테스트도 통과하므로, 순서를 직접 단정하지 않으면
 * 아무것도 이 회귀를 잡지 못한다.</p>
 * <p>The symptom when it does shadow is why this test exists: 401 INVALID_CREDENTIALS or 409
 * OTP_NOT_REGISTERED leave as 500 INTERNAL_ERROR and a failed login looks like a server fault.
 * It compiles and the other unit tests pass, so nothing catches the regression unless the
 * ordering is asserted directly.</p>
 *
 * // req: FR-LOGIN-002, NFR-USE-L02, NFR-USE-D02
 */
class ExceptionHandlerOrderTest {

    private static Integer orderOf(Class<?> adviceType) {
        return OrderUtils.getOrder(adviceType);
    }

    @Test
    @DisplayName("인증 처리자가 전역 처리자보다 앞선다 / the auth advice precedes the global advice")
    // req: FR-LOGIN-002
    void authAdvicePrecedesGlobalAdvice() {
        Integer authOrder = orderOf(AuthExceptionHandler.class);
        Integer globalOrder = orderOf(GlobalExceptionHandler.class);

        assertThat(authOrder)
                .as("AuthExceptionHandler must declare an explicit order")
                .isNotNull();
        assertThat(globalOrder)
                .as("GlobalExceptionHandler must declare an explicit order")
                .isNotNull();

        // 낮은 값이 높은 우선순위다.
        // A lower value means higher precedence.
        assertThat(authOrder)
                .as("the specific auth advice must win over the catch-all, or authentication "
                        + "failures surface as 500 instead of 401/409")
                .isLessThan(globalOrder);
    }

    @Test
    @DisplayName("두 처리자의 우선순위가 같지 않다 / the two advices do not share an order")
    // req: FR-LOGIN-002
    void ordersAreNotEqual() {
        // 같은 값이면 Spring 의 정렬이 불안정해져 환경마다 다르게 동작한다 — 로컬에서는
        // 재현되지 않고 배포된 곳에서만 나타나는 종류의 결함이다.
        // Equal values make Spring's sort unstable, so behaviour differs between environments —
        // the kind of defect that does not reproduce locally and appears only once deployed.
        assertThat(orderOf(AuthExceptionHandler.class))
                .isNotEqualTo(orderOf(GlobalExceptionHandler.class));
    }
}
