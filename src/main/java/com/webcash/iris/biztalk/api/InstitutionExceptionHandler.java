package com.webcash.iris.biztalk.api;

import com.webcash.iris.biztalk.domain.InstitutionNotFoundException;
import com.webcash.iris.biztalk.domain.InstitutionValidationException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 이용기관 쓰기 경로의 예외 처리자. / Exception advice for the 이용기관 write path.
 *
 * <h2>왜 별도의 어드바이스인가 / why a separate advice</h2>
 * <p>{@code GlobalExceptionHandler} 는 {@link IllegalArgumentException} 을 "요청 값을
 * 확인하세요" 한 줄로 바꾼다. 그것은 예외 메시지에 SQL 조각이나 파라미터 값이 섞여 나가는
 * 것을 막기 위한 <b>옳은</b> 판단이지만, 다섯 칸이 있는 폼에는 부족하다 — 운영자가 어느 칸을
 * 고쳐야 하는지 알 수 없다. 여기서는 <b>필드 이름과 규칙</b>만 돌려주고 제출된 값은 돌려주지
 * 않는다.</p>
 * <p>{@code GlobalExceptionHandler} collapses an {@link IllegalArgumentException} into one generic
 * line. That is the <b>right</b> call for keeping SQL fragments and parameter values out of
 * responses, but it is insufficient for a five-field form: the operator cannot tell which box to
 * fix. This advice returns the <b>field name and the rule</b>, and never the submitted value.</p>
 *
 * <p>응답 형태는 {@code AuthExceptionHandler} 의 Bean Validation 응답과 <b>같다</b>
 * ({@code code=VALIDATION_FAILED}, {@code errors[{field,message}]}). 서버 검증과 애노테이션
 * 검증이 서로 다른 모양으로 나가면 화면은 같은 실패를 두 가지 방법으로 해석해야 한다.</p>
 * <p>The response shape <b>matches</b> the Bean Validation response in
 * {@code AuthExceptionHandler} ({@code code=VALIDATION_FAILED}, {@code errors[{field,message}]}).
 * If server-side and annotation-driven validation answered differently, the screen would need two
 * ways to read one kind of failure.</p>
 *
 * <h2>우선순위 / ordering</h2>
 * <p>{@code GlobalExceptionHandler} 는 {@code @ExceptionHandler(Exception.class)} 를 갖고
 * {@code LOWEST_PRECEDENCE} 에 있다. 이 어드바이스가 명시적 순위를 갖지 않으면 기본값이
 * 같아지고, 그때 Spring 은 어느 쪽이 이길지 보장하지 않는다 — 404 와 400 이 500 으로 나가는
 * 결함이 환경에 따라 나타난다. {@code ExceptionHandlerOrderTest} 가 로그인 슬라이스에서 이
 * 회귀를 한 번 잡았고, 그 교훈이 여기의 명시적 {@code @Order} 다.</p>
 * <p>{@code GlobalExceptionHandler} declares {@code @ExceptionHandler(Exception.class)} at
 * {@code LOWEST_PRECEDENCE}. Without an explicit order here the two would tie, and Spring does not
 * guarantee which advice wins — 404s and 400s would surface as 500s in some environments and not
 * others. {@code ExceptionHandlerOrderTest} caught exactly that regression once in the 로그인
 * slice; this explicit {@code @Order} is that lesson.</p>
 *
 * // source: biztalk_admin_01.js — fn_save() alert chain; biztalk_admin_01_c001_act.jsp
 * // req: FR-INSTC-003, FR-INSTC-004, NFR-USE-D02, NFR-SEC-LOG-I01
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class InstitutionExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(InstitutionExceptionHandler.class);

    /**
     * 대상 없음을 404 로 변환한다. / Converts a missing institution to a 404.
     *
     * <p>응답에 기관코드를 담지 않는다. "그 코드는 없다" 와 "그 코드는 있다" 를 구분해 주면
     * 응답만으로 등록된 기관을 열거할 수 있다 — 레거시 중복검사가 정확히 그 창구였고, 게다가
     * 인증키까지 함께 돌려주었다(D-I3, TM-I002).</p>
     * <p>The code is not echoed. Distinguishing "no such code" from "that code exists" would let a
     * caller enumerate the registry from responses alone — which is exactly what the legacy
     * duplicate check was, and it returned the 인증키 too (D-I3, TM-I002).</p>
     *
     * @param e 예외 / the exception
     * @return HTTP 404 응답 / a 404 response
     */
    // req: FR-INSTC-004, FR-INSTC-005
    @ExceptionHandler(InstitutionNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(InstitutionNotFoundException e) {
        // 기관코드는 로그에만 남긴다 — 조사에는 필요하고 응답에는 불필요하다.
        // The code goes to the log only: needed for investigation, not for the response.
        log.warn("NOT_FOUND institution write path found no active row for '{}'", e.code());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("code", "NOT_FOUND", "message", "이용기관을 찾을 수 없습니다."));
    }

    /**
     * 검증 실패를 400 으로 변환한다. / Converts a validation failure to a 400.
     *
     * @param e 예외 / the exception
     * @return HTTP 400 응답 / a 400 response
     */
    // req: FR-INSTC-003, FR-INSTC-014, FR-INSTC-015, FR-INSTC-016
    @ExceptionHandler(InstitutionValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(InstitutionValidationException e) {
        log.warn("BAD_REQUEST institution field '{}' failed server-side validation", e.field());
        List<Map<String, String>> errors =
                List.of(Map.of("field", e.field(), "message", e.getMessage()));
        return ResponseEntity.badRequest()
                .body(Map.of("code", "VALIDATION_FAILED", "errors", errors));
    }
}
