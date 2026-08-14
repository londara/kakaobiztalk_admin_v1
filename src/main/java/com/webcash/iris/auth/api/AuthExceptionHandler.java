package com.webcash.iris.auth.api;

import com.webcash.iris.auth.domain.AuthFailureReason;
import com.webcash.iris.auth.domain.AuthenticationException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 인증 예외를 HTTP 응답으로 변환한다. / Maps authentication exceptions to HTTP responses.
 *
 * <p>이 클래스가 계정 존재 여부 노출을 막는 마지막 지점이다.
 * {@link AuthFailureReason#INVALID_CREDENTIALS} 는 계정 미존재와 비밀번호 불일치를
 * 모두 포함하며, 두 경우는 여기서 <b>완전히 동일한 응답</b>이 된다 — 상태 코드, 코드값,
 * 메시지가 모두 같다.</p>
 * <p>This class is the last point at which account enumeration could leak.
 * {@link AuthFailureReason#INVALID_CREDENTIALS} covers both an unknown account and a
 * wrong password, and both produce an <b>identical</b> response here: same status,
 * same code, same message.</p>
 *
 * <p>메시지는 레거시 코드 체계를 그대로 노출하지 않는다. 레거시는 {@code WCI00018},
 * {@code ADM_00003} 같은 내부 코드를 클라이언트로 내보냈고, 이는 응답만 보고 어느
 * 검증에서 실패했는지 구분할 수 있게 한다.</p>
 * <p>Messages do not expose the legacy code scheme. The legacy returned internal codes
 * such as {@code WCI00018} and {@code ADM_00003} to the client, which let a caller tell
 * from the response alone which check had failed.</p>
 *
 * // source: apc_login_proc_act.jsp — JexBIZException codes
 * // req: FR-LOGIN-002, NFR-USE-L02, NFR-SEC-LOG-L01
 */
@RestControllerAdvice
public class AuthExceptionHandler {

    /**
     * 인증 실패를 응답으로 변환한다. / Converts an authentication failure to a response.
     *
     * @param e 인증 예외 / the authentication exception
     * @return HTTP 응답 / the HTTP response
     */
    // req: FR-LOGIN-002, NFR-USE-L02
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, String>> handle(AuthenticationException e) {
        return switch (e.reason()) {
            // 계정 미존재와 비밀번호 불일치 — 구분 불가능한 동일 응답.
            // Unknown account and wrong password — one indistinguishable response.
            case INVALID_CREDENTIALS -> unauthorized(
                    "INVALID_CREDENTIALS", "아이디 또는 비밀번호를 확인하세요.");

            // OTP 실패도 같은 계열의 메시지를 쓴다. 다만 사용자가 무엇을 다시 입력해야
            // 하는지 알아야 하므로 코드만 구분한다 — 계정 존재 여부는 이미 비밀번호
            // 단계를 통과했다는 사실로 드러나 있으므로 추가 노출이 아니다.
            // OTP failures share the message family. The code differs so the user knows
            // what to re-enter; account existence is already implied by having passed the
            // password step, so this discloses nothing further.
            case OTP_MISMATCH, OTP_MALFORMED -> unauthorized(
                    "OTP_INVALID", "OTP 코드를 확인하세요.");

            case OTP_NOT_REGISTERED -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("code", "OTP_NOT_REGISTERED",
                            "message", "OTP 미등록 계정입니다. OTP 등록이 필요합니다."));

            // 재등록 차단 (FR-OTP-006). 복구는 운영자 초기화만 가능하므로, 사용자가
            // 무엇을 해야 하는지 메시지로 안내한다 — 레거시 ADM_00026 은 코드만 반환했다.
            // Re-enrolment refused (FR-OTP-006). Recovery is operator-only, so the message
            // says what to do; the legacy ADM_00026 returned only a code.
            case OTP_ALREADY_REGISTERED -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("code", "OTP_ALREADY_REGISTERED",
                            "message", "이미 OTP가 등록되어 있습니다. 단말을 분실한 경우 운영자에게 초기화를 요청하세요."));

            case OPERATION_NOT_PERMITTED -> ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("code", "OPERATION_NOT_PERMITTED",
                            "message", "요청을 처리할 수 없습니다. 처음부터 다시 시도하세요."));

            case ACCOUNT_LOCKED -> ResponseEntity.status(HttpStatus.LOCKED)
                    .body(Map.of("code", "ACCOUNT_LOCKED",
                            "message", "계정이 잠겼습니다. 관리자에게 문의하세요."));

            case ACCOUNT_DORMANT -> ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("code", "ACCOUNT_DORMANT",
                            "message", "장기 미사용 계정입니다. 재활성화가 필요합니다."));

            case STATUS_BLOCKED -> ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("code", "STATUS_BLOCKED",
                            "message", "현재 계정 상태로는 로그인할 수 없습니다."));

            case RATE_LIMITED -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("code", "RATE_LIMITED",
                            "message", "요청이 너무 많습니다. 잠시 후 다시 시도하세요."));

            case IP_NOT_ALLOWED -> ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("code", "IP_NOT_ALLOWED",
                            "message", "허용되지 않은 접속 경로입니다."));
        };
    }

    /**
     * 입력 검증 실패를 응답으로 변환한다. / Converts a validation failure to a response.
     *
     * <p>필드명만 반환하고 제출된 값은 반환하지 않는다. 값을 되돌려주면 비밀번호나
     * OTP 코드가 응답 본문에 실린다.</p>
     * <p>Returns field names but never the submitted values: echoing them would put the
     * password or OTP code in the response body.</p>
     *
     * @param e 검증 예외 / the validation exception
     * @return HTTP 400 응답 / a 400 response
     */
    // req: NFR-SEC-LOG-L01
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        var messages = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(),
                        "message", String.valueOf(fe.getDefaultMessage())))
                .toList();
        return ResponseEntity.badRequest().body(Map.of("code", "VALIDATION_FAILED", "errors", messages));
    }

    private ResponseEntity<Map<String, String>> unauthorized(String code, String message) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("code", code, "message", message));
    }
}
