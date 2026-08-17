package com.webcash.iris.biztalk.api;

import com.webcash.iris.biztalk.domain.PagedResult;
import com.webcash.iris.biztalk.domain.SenderNumberRow;
import com.webcash.iris.biztalk.domain.SenderNumberService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 발신번호 관리 엔드포인트 — 운영자 전용. / Sender-number endpoints, operators only.
 *
 * <h2>레거시 결함 대응 / the legacy behaviour being fixed</h2>
 * <p>화면 10 의 여섯 서비스는 모두 {@code <login>Y</login>} 하나만 걸려 있었다. 등록과 수정
 * 버튼은 브라우저에서 {@code biztalk_admin_00_l003}(매니저 여부) 을 호출하고 결과에 따라
 * {@code alert('권한 없음')} 을 띄웠을 뿐이며, <b>삭제 버튼에는 그 검사조차 없었다</b> —
 * 즉 이 화면에서 가장 파괴적인 동작이 가장 덜 보호되어 있었다(D-S2).</p>
 * <p>All six of screen 10's services carried {@code <login>Y</login>} and nothing else. The
 * register and edit buttons called a manager-check service <b>from the browser</b> and raised
 * {@code alert('권한 없음')}; the delete button had no check at all — the most destructive action
 * on the screen was the least protected (D-S2).</p>
 *
 * <p>여기에 더해, 발신번호 등록은 단순한 데이터 입력이 아니라 <b>발송 권한 부여</b>다.
 * {@code KAKAOTALK} 은 {@code KKB_DPNO_LDGR} 에 행이 있는지로 발신 가능 여부를 판단한다.
 * 소유 인증이 없는 상태(AMB-S01, RESIDUAL-S01)에서는 이 인가 검사가 <b>유일한</b> 방어선이다.</p>
 * <p>Registering a sender number is not data entry but a <b>grant of sending capability</b>:
 * {@code KAKAOTALK} decides whether a send may proceed by whether a row exists in
 * {@code KKB_DPNO_LDGR}. With ownership verification declined (AMB-S01, RESIDUAL-S01), this
 * authorization check is the <b>only</b> barrier.</p>
 *
 * <p>{@code /api/admin/**} 아래 두어 {@code SecurityConfig} 의 운영자 규칙을 받게 하고,
 * 컨트롤러 수준 {@code @PreAuthorize} 를 이중으로 둔다.</p>
 * <p>Placed under {@code /api/admin/**} for the operator routing rule, with a controller-level
 * {@code @PreAuthorize} as defence in depth.</p>
 *
 * // source: biztalk_admin_10_view.jsp, biztalk_admin_10.js, WSVC.biztalk_admin_10_l001.xml
 * // req: FR-AZ-D01, FR-AZ-D02, FR-AZ-D03, FR-SND-001, FR-SND-003
 */
@RestController
@RequestMapping("/api/admin/sender-numbers")
public class SenderNumberController {

    private final SenderNumberService service;

    /**
     * 컨트롤러를 생성한다. / Creates the controller.
     *
     * @param service 발신번호 조회 서비스 / the sender-number query service
     */
    public SenderNumberController(SenderNumberService service) {
        this.service = service;
    }

    /**
     * 이용기관의 발신번호를 조회한다. / Lists an institution's sender numbers.
     *
     * <p>{@code GET} 인 이유: 조회 조건이 이용기관 코드와 페이지뿐이며 개인정보를 담지
     * 않는다. 응답에는 발신번호가 전체로 담기지만(AMB-S04), 그것은 <b>조건</b>이 아니라
     * 결과이므로 URL 에 남지 않는다.</p>
     * <p>{@code GET} because the criteria are an institution code and a page, with no personal
     * data. The response carries numbers in full (AMB-S04), but as results rather than
     * <b>criteria</b>, so nothing sensitive lands in a URL.</p>
     *
     * @param institution 이용기관 코드 / the institution code
     * @param page        0부터 시작하는 페이지 번호 / zero-based page index
     * @param size        페이지 크기 / page size
     * @param request     출처 IP 확보용 / for the source address
     * @return 한 페이지 분량의 발신번호 / one page of sender numbers
     */
    // req: FR-SND-001, FR-SND-003, FR-AZ-D01, FR-AZ-D03
    @GetMapping
    @PreAuthorize("hasRole('OPERATOR')")
    public PagedResult<SenderNumberRow> list(
            @RequestParam(required = false) String institution,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            HttpServletRequest request) {

        // 정규화와 범위 결정은 도메인이 담당한다. 컨트롤러가 직접 하면 다른 진입점이
        // 생겼을 때 규칙이 갈라진다.
        // Normalisation and scoping belong to the domain; doing them here would let the rules
        // diverge as soon as a second entry point exists.
        return service.list(institution, page, size, request.getRemoteAddr());
    }
}
