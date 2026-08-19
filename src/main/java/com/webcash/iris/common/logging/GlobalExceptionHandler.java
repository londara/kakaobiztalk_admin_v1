package com.webcash.iris.common.logging;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 처리되지 않은 예외의 최종 처리자. / Last-resort handler for unhandled exceptions.
 *
 * <p>{@code @Order(LOWEST_PRECEDENCE)} 로 가장 낮은 우선순위에 둔다. 인증 예외는
 * {@code AuthExceptionHandler} 가 먼저 처리해야 하며, 이 클래스가 그것을 가로채면 세심하게
 * 구분된 인증 실패 응답이 전부 뭉뚱그려진 500 이 된다.</p>
 * <p>Ordered last so {@code AuthExceptionHandler} keeps its exception types: intercepting them
 * here would collapse a carefully differentiated set of authentication responses into one
 * undifferentiated 500.</p>
 *
 * <h2>로그와 응답에 담지 않는 것 / what stays out of the log and the response</h2>
 * <p><b>예외 메시지를 클라이언트에 반환하지 않는다.</b> 예외 메시지에는 SQL 조각, 테이블명,
 * 파일 경로, 때로는 파라미터 값까지 담긴다. 레거시는 내부 코드({@code WCI00018} 등)를
 * 그대로 내보내 어느 검증에서 실패했는지 응답만으로 알 수 있게 했는데, 예외 메시지를 그대로
 * 반환하는 것은 그보다 더 많은 것을 준다.</p>
 * <p><b>The exception message is never returned to the client.</b> Such messages carry SQL
 * fragments, table names, file paths and sometimes parameter values. The legacy returned internal
 * codes that revealed which check had failed; echoing an exception message gives away more.</p>
 *
 * <p>대신 <b>상관 식별자를 반환</b>한다. 사용자는 그 값을 운영자에게 전달할 수 있고, 운영자는
 * 그것으로 로그와 감사 기록에서 정확히 그 요청을 찾는다 — 사용자에게 아무것도 노출하지 않으면서
 * 조사는 가능하게 만드는 교환이다.</p>
 * <p>It returns the <b>correlation id</b> instead. A user can quote it to an operator, who finds
 * exactly that request in the log and the audit trail: the exchange that keeps disclosure at zero
 * while keeping investigation possible.</p>
 *
 * <p>스택트레이스는 <b>로그에만</b> 남긴다. 그리고 스택트레이스 외에 요청 본문이나 파라미터를
 * 덧붙이지 않는다 — 오류 경로는 PII 가 로그로 새는 가장 흔한 통로다.</p>
 * <p>The stack trace goes to the log only, and nothing else is attached to it — no body, no
 * parameters. Error paths are the most common way PII reaches a log.</p>
 *
 * // req: NFR-SEC-LOG-L01, NFR-SEC-LOG-D01, NFR-USE-D02
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 인가 거부를 응답으로 변환한다. / Converts an authorization denial to a response.
     *
     * <p>거부 사유를 구체적으로 알려주지 않는다. "운영자 권한이 없다" 와 "해당 이용기관에
     * 대한 권한이 없다" 를 구분해 주면, 호출자가 응답만으로 어떤 이용기관이 존재하는지
     * 추론할 수 있다(FR-AZ-D03 의 열거 방지).</p>
     * <p>The reason is not disclosed: distinguishing "not an operator" from "not entitled to that
     * institution" would let a caller infer which institutions exist from responses alone
     * (the enumeration concern behind FR-AZ-D03).</p>
     *
     * @param e 인가 예외 / the authorization exception
     * @return HTTP 403 응답 / a 403 response
     */
    // req: FR-AZ-D01, FR-AZ-D02, FR-AZ-D03
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException e) {
        // 거부는 WARN 으로 남긴다 — 반복되는 거부는 탐색 행위의 신호다.
        // Denials are logged at WARN: repeated denials are a probing signal.
        log.warn("ACCESS_DENIED path-level authorization refused the request");
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(body("FORBIDDEN", "요청을 처리할 권한이 없습니다."));
    }

    /**
     * 잘못된 인자를 응답으로 변환한다. / Converts an illegal argument to a response.
     *
     * <p>도메인 검증 실패({@link IllegalArgumentException})는 400 이다. 손상된 발신번호
     * 식별자가 여기로 온다 — {@code SenderNumberRef.fromToken} 은 복원 불가 입력에
     * 예외를 던지며, 그것이 조용히 빈 결과가 되지 않게 하는 것이 FR-SNDD-002 다.</p>
     * <p>Domain validation failures are 400. A malformed sender-number reference arrives here:
     * {@code SenderNumberRef.fromToken} throws on unrestorable input, and keeping that from
     * degrading into a silent empty result is FR-SNDD-002.</p>
     *
     * @param e 예외 / the exception
     * @return HTTP 400 응답 / a 400 response
     */
    // req: FR-SNDD-002, NFR-USE-D02
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        // 예외 메시지는 로그에만. 응답에는 상관 식별자만 준다.
        // The message goes to the log only; the response carries the correlation id.
        log.warn("BAD_REQUEST rejected an invalid argument: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(body("BAD_REQUEST", "요청 값을 확인하세요."));
    }

    /**
     * 요청 바인딩 실패를 400 으로 변환한다. / Converts a request-binding failure to a 400.
     *
     * <h2>이 핸들러가 없어서 생긴 결함 / the defect its absence caused</h2>
     * <p>필수 {@code @RequestParam} 을 빼고 호출하면 Spring 이
     * {@link MissingServletRequestParameterException} 을 던진다. 그것이
     * {@link #handleUnexpected(Exception)} 까지 흘러가 <b>500 과 "요청을 처리할 수 없습니다"</b>
     * 가 되었다 — 클라이언트 오류가 서버 오류로 보고되고, 로그에는 {@code UNHANDLED} 로 남아
     * 운영에 잡음을 만들며, 호출자는 어느 파라미터가 빠졌는지 알 수 없다.</p>
     * <p>Omitting a required {@code @RequestParam} makes Spring throw
     * {@link MissingServletRequestParameterException}, which fell through to
     * {@link #handleUnexpected(Exception)} and became <b>a 500 with "요청을 처리할 수 없습니다"</b> —
     * a client error reported as a server error, logged as {@code UNHANDLED} so it pages operations,
     * and giving the caller no way to know which parameter was missing.</p>
     *
     * <p><b>이 슬라이스만의 문제가 아니었다.</b> 필수 파라미터를 가진 모든 엔드포인트가
     * 해당되며, {@code ReportController#query} 의 {@code from}/{@code to} 도 같다. 톡전송 내역의
     * 부정 경로 시험(T1-16)이 처음 이것을 드러냈다 — 결함 CR-T01.</p>
     * <p><b>This was never specific to this slice.</b> Every endpoint with a required parameter was
     * affected, including {@code ReportController#query}'s {@code from} and {@code to}. The 톡전송 내역
     * negative-path suite (T1-16) was the first thing to surface it — defect CR-T01.</p>
     *
     * <p>빠진 파라미터의 <b>이름</b>은 응답에 담는다. 이름은 API 계약의 일부이므로 비밀이
     * 아니며, 담지 않으면 호출자가 추측으로 고칠 수밖에 없다. 값은 담지 않는다.</p>
     * <p>The missing parameter's <b>name</b> is included: names are part of the published contract and
     * are not secrets, and withholding one leaves the caller guessing. Values are not included.</p>
     *
     * @param e 예외 / the exception
     * @return HTTP 400 응답 / a 400 response
     */
    // req: FR-TLK-014, NFR-USE-D02, CR-T01
    @ExceptionHandler({MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, String>> handleBindingFailure(Exception e) {
        String parameter = (e instanceof MissingServletRequestParameterException missing)
                ? missing.getParameterName()
                : ((MethodArgumentTypeMismatchException) e).getName();

        log.warn("BAD_REQUEST request binding failed for parameter '{}': {}",
                parameter, e.getMessage());

        return ResponseEntity.badRequest()
                .body(body("BAD_REQUEST",
                        "필수 요청 값이 없거나 형식이 올바르지 않습니다: " + parameter
                                + " / A required request value is missing or malformed: " + parameter));
    }

    /**
     * 상태 위반을 응답으로 변환한다. / Converts an illegal state to a response.
     *
     * <p>{@code TenantContext.require()} 가 컨텍스트 없이 호출되면 여기로 온다. 이는
     * <b>배선 오류</b>이지 사용자 입력 문제가 아니므로 500 이며, 조용히 넘기면 테넌트 격리가
     * 적용되지 않은 채 동작하는 경로가 생긴다.</p>
     * <p>Reached when {@code TenantContext.require()} is called with no context bound. That is a
     * <b>wiring fault</b> rather than bad input, so it is a 500: passing it over silently would
     * leave a path running without tenant isolation.</p>
     *
     * @param e 예외 / the exception
     * @return HTTP 500 응답 / a 500 response
     */
    // req: NFR-SEC-TENANT-D01
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException e) {
        log.error("INVALID_STATE request reached a component in an unusable state", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body("INTERNAL_ERROR", "요청을 처리할 수 없습니다."));
    }

    /**
     * 그 밖의 모든 예외를 응답으로 변환한다. / Converts every remaining exception to a response.
     *
     * @param e 예외 / the exception
     * @return HTTP 500 응답 / a 500 response
     */
    // req: NFR-SEC-LOG-L01, NFR-OPS-D02
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception e) {
        // 스택트레이스는 남기되 요청 본문·파라미터는 덧붙이지 않는다.
        // The stack trace is kept; the request body and parameters are not attached to it.
        log.error("UNHANDLED an unexpected error escaped the application", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body("INTERNAL_ERROR", "요청을 처리할 수 없습니다."));
    }

    /**
     * 오류 응답 본문을 만든다. / Builds the error response body.
     *
     * @param code    오류 코드 / the error code
     * @param message 사용자용 메시지 / the user-facing message
     * @return 응답 본문 / the response body
     */
    private static Map<String, String> body(String code, String message) {
        String correlationId = CorrelationId.current();
        return correlationId == null
                ? Map.of("code", code, "message", message)
                : Map.of("code", code, "message", message, "traceId", correlationId);
    }
}
