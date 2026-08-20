package com.webcash.iris.biztalk.api;

import com.webcash.iris.biztalk.domain.SenderNumberDuplicateException;
import com.webcash.iris.biztalk.domain.SenderNumberNotLiveException;
import com.webcash.iris.biztalk.domain.SenderNumberValidationException;
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
 * 발신번호 쓰기 경로의 예외 처리자. / Exception advice for the sender-number write path.
 *
 * <h2>왜 별도의 어드바이스인가 / why a separate advice</h2>
 * <p>{@code GlobalExceptionHandler} 는 {@link IllegalArgumentException} 을 "요청 값을 확인하세요"
 * 한 줄로 바꾼다. SQL 조각이나 파라미터 값이 응답으로 새지 않게 하려는 <b>옳은</b> 판단이지만, 세
 * 칸이 있는 폼에는 부족하다 — 운영자가 어느 칸을 고쳐야 하는지 알 수 없고, 레거시가 정확히 그
 * 상태였다({@code jex.alert('등록중 오류 발생.')}, NFR-USE-D02).</p>
 * <p>{@code GlobalExceptionHandler} collapses an {@link IllegalArgumentException} into one generic
 * line. That is the <b>right</b> call for keeping SQL fragments and parameter values out of responses,
 * but it is insufficient for a three-field form: the operator cannot tell which box to fix, which is
 * exactly the legacy state (NFR-USE-D02).</p>
 *
 * <p>응답 형태는 이용기관 슬라이스와 <b>같다</b>({@code code=VALIDATION_FAILED},
 * {@code errors[{field,message}]}). 두 화면이 같은 실패를 서로 다른 모양으로 받으면 클라이언트가
 * 두 벌의 해석기를 갖게 된다.</p>
 * <p>The response shape <b>matches</b> the institution slice's. Two screens receiving one kind of
 * failure in two shapes would give the client two parsers.</p>
 *
 * <h2>409 가 둘인 이유 / why there are two 409s</h2>
 * <p>중복(FR-SNDC-004)과 "살아 있는 행이 없음"(FR-SNDD-002) 은 둘 다 <b>상태 충돌</b>이며
 * 400 이 아니다 — 요청 자체는 형식상 올바르고, 데이터베이스의 현재 상태와 맞지 않을 뿐이다.
 * 400 으로 돌려주면 화면은 "입력을 고치세요" 를 보여 주게 되는데, 고칠 입력이 없다. 두 경우 모두
 * 필요한 행동은 <b>목록을 다시 조회하는 것</b>이다.</p>
 * <p>A duplicate (FR-SNDC-004) and "no live row" (FR-SNDD-002) are both <b>state conflicts</b>, not
 * 400s: the request is well-formed and merely disagrees with the database's current state. A 400
 * would make the screen say "fix your input" when there is no input to fix. In both cases the action
 * needed is <b>re-reading the list</b>.</p>
 *
 * <h2>우선순위 / ordering</h2>
 * <p>{@code GlobalExceptionHandler} 는 {@code @ExceptionHandler(Exception.class)} 를 갖고
 * {@code LOWEST_PRECEDENCE} 에 있다. 명시적 순위가 없으면 기본값이 같아지고, 그때 Spring 은
 * 어느 쪽이 이길지 보장하지 않는다 — 409 와 400 이 환경에 따라 500 으로 나간다. 로그인
 * 슬라이스에서 그 회귀를 한 번 겪었고, 이 {@code @Order} 가 그 교훈이다.</p>
 * <p>{@code GlobalExceptionHandler} declares {@code @ExceptionHandler(Exception.class)} at
 * {@code LOWEST_PRECEDENCE}. Without an explicit order the two tie and Spring does not guarantee
 * which wins — 409s and 400s surface as 500s in some environments. The 로그인 slice hit that
 * regression once; this {@code @Order} is the lesson.</p>
 *
 * // source: biztalk_admin_12.js — jex.alert('등록중 오류 발생.'); biztalk_admin_13.js
 * // req: FR-SNDC-003, FR-SNDC-004, FR-SNDD-002, FR-SNDC-014, NFR-USE-D02, NFR-SEC-LOG-D01
 */
/*
  이 컨트롤러에만 적용한다. 전역 어드바이스로 두면 세 예외가 이 슬라이스 밖의 어떤 컨트롤러에서
  발생해도 이 응답 모양으로 나가는데, 그 컨트롤러의 클라이언트는 이 모양을 해석하지 못한다.
  Scoped to this controller. As a global advice these three exceptions would take this response shape
  wherever they arose, including controllers whose clients cannot parse it.
*/
@RestControllerAdvice(assignableTypes = SenderNumberController.class)
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SenderNumberExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(SenderNumberExceptionHandler.class);

    /**
     * 검증 실패를 400 으로 변환한다. / Converts a validation failure to a 400.
     *
     * @param e 예외 / the exception
     * @return HTTP 400 응답 / a 400 response
     */
    // req: FR-SNDC-003, FR-SNDC-011, FR-SNDD-006, NFR-USE-D02
    @ExceptionHandler(SenderNumberValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(SenderNumberValidationException e) {
        // 필드 이름만 남긴다 — 제출된 값은 로그에도 응답에도 담지 않는다. 발신번호는 평문으로
        // 로그에 남아서는 안 된다(NFR-SEC-LOG-D01).
        // Only the field name is logged: the submitted value goes neither to the log nor to the
        // response. A sender number must not appear in a log in clear (NFR-SEC-LOG-D01).
        log.warn("BAD_REQUEST sender-number field '{}' failed server-side validation", e.field());
        List<Map<String, String>> errors =
                List.of(Map.of("field", e.field(), "message", e.getMessage()));
        return ResponseEntity.badRequest()
                .body(Map.of("code", "VALIDATION_FAILED", "errors", errors));
    }

    /**
     * 중복 등록을 409 로 변환한다. / Converts a duplicate registration to a 409.
     *
     * <p>어느 기관이 그 번호를 갖고 있는지는 <b>말하지 않는다.</b> 알려 주면 번호를 넣어 보는
     * 것만으로 다른 기관의 발신번호를 열거할 수 있고, 레거시 중복검사가 정확히 그런 창구였다.</p>
     * <p>The holder is <b>not disclosed</b>: telling the caller would turn registration into an
     * enumeration oracle over other institutions' numbers, which is what the legacy duplicate check
     * was.</p>
     *
     * @param e 예외 / the exception
     * @return HTTP 409 응답 / a 409 response
     */
    // req: FR-SNDC-004, CONST-BIZ-D01
    @ExceptionHandler(SenderNumberDuplicateException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicate(SenderNumberDuplicateException e) {
        log.warn("CONFLICT sender-number registration refused as a duplicate");
        List<Map<String, String>> errors =
                List.of(Map.of("field", "number", "message", e.getMessage()));
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("code", "DUPLICATE", "errors", errors));
    }

    /**
     * 살아 있는 행이 없는 삭제를 409 로 변환한다. / Converts a delete with no live row to a 409.
     *
     * <p><b>이 메서드가 D-S1 의 답이다.</b> 레거시는 이 자리에서 아무 일도 하지 않고
     * {@code "정상적으로 처리되었습니다"} 를 돌려주었다 — 0건을 지운 {@code DELETE} 는 SQL 오류가
     * 아니었기 때문이다. 성공 응답과 바뀌지 않은 데이터베이스가 공존했고, 번호는 계속 발송에
     * 쓸 수 있었다.</p>
     * <p><b>This method is the answer to D-S1.</b> The legacy did nothing here and returned a success
     * sentence, because a zero-row {@code DELETE} was not a SQL error. A success response coexisted
     * with an unchanged database, and the number remained usable for sending.</p>
     *
     * @param e 예외 / the exception
     * @return HTTP 409 응답 / a 409 response
     */
    // source: biztalk_admin_10_d001_act.jsp — no row-count check anywhere in the file
    // req: FR-SNDD-002, NFR-OPS-D02
    @ExceptionHandler(SenderNumberNotLiveException.class)
    public ResponseEntity<Map<String, Object>> handleNotLive(SenderNumberNotLiveException e) {
        log.warn("CONFLICT sender-number delete matched no live row; the transaction was rolled back");
        List<Map<String, String>> errors =
                List.of(Map.of("field", "refs", "message", e.getMessage()));
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("code", "NOT_LIVE", "errors", errors));
    }
}
