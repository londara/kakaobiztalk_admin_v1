package com.webcash.iris.biztalk.api;

import com.webcash.iris.biztalk.domain.MessageDetail;
import com.webcash.iris.biztalk.domain.MessageDetailKey;
import com.webcash.iris.biztalk.domain.MessageDetailService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 문자상세내역 엔드포인트. / Message detail endpoint.
 *
 * <p>레거시는 이 화면을 팝업으로 열었다({@code ap.openPop({href: "biztalk_admin_41.act", ...})})
 * 며 목록 그리드에서 6개 값을 hidden form 으로 전달했다. 신규 구현은 같은 6개 값을 JSON
 * 본문으로 받는다 — 팝업 여부는 프론트엔드의 표현 선택이고, API 계약은 동일하다.</p>
 * <p>The legacy opened this as a popup, passing six values through a hidden form. The new
 * implementation takes the same six in a JSON body: whether it is a popup is a frontend
 * presentation choice, and the API contract is unchanged.</p>
 *
 * // source: biztalk_admin_40.js — fn_getDetail(), biztalk_admin_41.js — fn_loadData()
 * // req: FR-MSGD-001, FR-MSGD-002, FR-MSGD-008, FR-MSG-014
 */
@RestController
@RequestMapping("/api/message-history")
public class MessageDetailController {

    private final MessageDetailService service;

    /**
     * 컨트롤러를 생성한다. / Creates the controller.
     *
     * @param service 상세 조회 서비스 / the detail service
     */
    public MessageDetailController(MessageDetailService service) {
        this.service = service;
    }

    /**
     * 상세내역을 조회한다. / Looks up a detail record.
     *
     * <p>존재하지 않거나 접근 권한이 없으면 <b>동일하게 404</b> 를 반환한다. 403 과 404 를
     * 구분하면 "그 메시지키는 존재한다"는 사실이 드러난다(TM-009).</p>
     * <p>Not-found and not-permitted both return <b>404</b>. Distinguishing 403 from 404 would
     * reveal that a given message key exists (TM-009).</p>
     *
     * @param body    조회 요청 / the lookup request
     * @param request HTTP 요청 / the HTTP request
     * @return 상세내역 또는 404 / the detail, or 404
     */
    // req: FR-MSGD-001, FR-MSGD-008, TM-009
    @PostMapping("/detail")
    public ResponseEntity<?> detail(@Valid @RequestBody DetailRequest body,
                                    HttpServletRequest request) {
        MessageDetailKey key = MessageDetailKey.of(
                body.messageType(), body.tableType(), body.messageKey(),
                body.requestDate(), body.status());

        return service.find(key, request.getRemoteAddr())
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("code", "NOT_FOUND",
                                "message", "해당 내역을 찾을 수 없습니다.")));
    }

    /**
     * 상세 조회 요청. / The detail lookup request.
     *
     * <p>목록 그리드의 행에서 그대로 전달되는 값들이다. {@code requestDate} 는 그리드가
     * 표시용으로 잘라낸 8자리가 아니라 <b>원본 14자리</b>여야 한다 — 레거시도
     * {@code rowdata.REQDATE} 원값을 넘겼다.</p>
     * <p>These come straight from a grid row. {@code requestDate} must be the <b>full 14-digit</b>
     * value, not the 8-digit form the grid displays; the legacy also passed the raw value.</p>
     *
     * @param messageType 유형 코드 / the type code
     * @param tableType   문자타입 코드 / the table type code
     * @param messageKey  메시지키 / the message key
     * @param requestDate 요청일시 14자리 / the 14-digit request timestamp
     * @param status      상태 코드 / the status code
     *
     * // source: biztalk_admin_40.js — fn_getDetail() sets KEY/ID/TABLE_TYPE/MSGTYPE/REQDATE/STS
     * // req: FR-MSGD-002, FR-MSGD-003
     */
    public record DetailRequest(
            @NotBlank(message = "유형 코드는 필수입니다.") String messageType,
            @NotBlank(message = "문자타입 코드는 필수입니다.") String tableType,
            @NotNull(message = "메시지키는 필수입니다.") Long messageKey,
            @NotBlank(message = "요청일시는 필수입니다.") String requestDate,
            @NotBlank(message = "상태 코드는 필수입니다.") String status
    ) {
    }

    /**
     * 상세 응답 변환기. / Detail response mapping.
     *
     * <p>도메인 레코드를 그대로 직렬화하되, 표시 여부 판정 세 개를 함께 보낸다. 프론트엔드가
     * 필드마다 공백 검사를 반복하지 않도록 서버가 한 번 판단한다.</p>
     * <p>The domain record is serialised as-is plus three presence flags, so the frontend does
     * not repeat blank-checks per field.</p>
     *
     * @param detail      상세내역 / the detail
     * @param hasImage    이미지 존재 / whether an image is present
     * @param hasButtons  버튼 존재 / whether buttons are present
     * @param hasFailure  실패 정보 존재 / whether failure info is present
     *
     * // req: FR-MSGD-004, FR-MSGD-006
     */
    public record DetailResponse(
            MessageDetail detail, boolean hasImage, boolean hasButtons, boolean hasFailure) {

        /**
         * 도메인 상세를 응답으로 변환한다. / Converts a domain detail to a response.
         *
         * @param detail 상세내역 / the detail
         * @return 응답 / the response
         */
        public static DetailResponse from(MessageDetail detail) {
            return new DetailResponse(detail, detail.hasImage(),
                    detail.hasButtons(), detail.hasFailureInfo());
        }
    }
}
