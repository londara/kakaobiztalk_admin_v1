package com.webcash.iris.biztalk.api;

import com.webcash.iris.biztalk.domain.InstitutionRow;
import com.webcash.iris.biztalk.domain.InstitutionService;
import com.webcash.iris.biztalk.domain.PagedResult;
import com.webcash.iris.biztalk.domain.SenderNumberRef;
import com.webcash.iris.biztalk.domain.SenderNumberRow;
import com.webcash.iris.biztalk.domain.SenderNumberService;
import com.webcash.iris.biztalk.domain.SenderNumberWriteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final SenderNumberWriteService writeService;
    private final InstitutionService institutions;

    /**
     * 컨트롤러를 생성한다. / Creates the controller.
     *
     * @param service      발신번호 조회 서비스 / the sender-number query service
     * @param writeService 발신번호 쓰기 서비스 / the sender-number write service
     * @param institutions 이용기관 조회 — 기관명 확보용 / the institution reader, for the name
     */
    public SenderNumberController(SenderNumberService service,
                                  SenderNumberWriteService writeService,
                                  InstitutionService institutions) {
        this.service = service;
        this.writeService = writeService;
        this.institutions = institutions;
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

    /**
     * 등록 화면이 열 이용기관 문맥을 반환한다. / Returns the institution context for the register form.
     *
     * <p>기관코드와 기관명 <b>둘만</b> 반환한다(FR-SNDC-002). 레거시 등록 팝업은 이름을 채우려고
     * 이용기관 <b>상세조회</b>({@code biztalk_admin_01_l002})를 호출했고, 그 서비스는 기관 레코드
     * 전체를 평문 인증키와 함께 반환했다(D-S18). 이름 하나가 필요한 화면이 살아 있는 자격증명을
     * 브라우저로 끌어온 셈이다.</p>
     * <p>Returns <b>only</b> the code and the name (FR-SNDC-002). The legacy popup called the
     * institution <em>detail</em> service to fill in a name, and that service returned the whole
     * record including the plaintext 인증키 (D-S18): a screen needing one name pulled a live
     * credential into the browser.</p>
     *
     * <p>여기서 {@code InstitutionService} 를 쓰는 것은 그 서비스가 이미 인증키를 마스킹해
     * 돌려주기 때문이고(이용기관 슬라이스 D-I20 의 수정), 그럼에도 <b>좁은 응답 타입</b>으로
     * 옮겨 담는다 — 마스킹된 값조차 이 화면에 갈 이유가 없다.</p>
     * <p>{@code InstitutionService} is used because it already returns the key masked (the fix for
     * D-I20 in the institution slice), and the result is still copied into a <b>narrow response
     * type</b>: not even the masked value has any business on this screen.</p>
     *
     * @param institution 이용기관 코드 / the institution code
     * @return 기관코드와 기관명 / the code and the name
     */
    // source: biztalk_admin_12.js — loadData()
    // req: FR-SNDC-002, FR-SNDC-012, NFR-SEC-PII-D02, FR-AZ-D01
    @GetMapping("/context")
    @PreAuthorize("hasRole('OPERATOR')")
    public SenderNumberContextResponse context(@RequestParam String institution) {
        InstitutionRow row = institutions.findByCode(institution);
        return new SenderNumberContextResponse(row.code(), row.name());
    }

    /**
     * 발신번호를 등록한다. / Registers a sender number.
     *
     * <p>본문에 이용기관이 없다 — 대상은 질의 문자열이 정하고, 서버는 그것을 신뢰하지 않고 세션
     * 권한으로 다시 판정한다(FR-SNDC-012, FR-AZ-D03). 레거시는 부모 창이 준 {@code IS_CD} 를
     * 그대로 insert 에 넣었다.</p>
     * <p>The body carries no institution: the query string names the target and the server re-decides
     * it from session entitlements rather than trusting it (FR-SNDC-012, FR-AZ-D03). The legacy put
     * the opener's {@code IS_CD} straight into the insert.</p>
     *
     * @param institution 이용기관 코드 / the institution code
     * @param request     등록 내용 / the registration
     * @param http        출처 IP 확보용 / for the source address
     * @return 등록 결과 / the outcome
     */
    // source: biztalk_admin_12.js — btn_save; WSVC.biztalk_admin_12_c001
    // req: FR-SNDC-001, FR-SNDC-003, FR-SNDC-011, FR-SNDC-012, FR-AZ-D01, FR-AZ-D05
    @PostMapping
    @PreAuthorize("hasRole('OPERATOR')")
    public SenderNumberWriteResponse register(@RequestParam String institution,
                                             @Valid @RequestBody SenderNumberRegisterRequest request,
                                             HttpServletRequest http) {

        SenderNumberRef ref = writeService.register(
                institution, request.toRegistration(), http.getRemoteAddr());
        return SenderNumberWriteResponse.registered(ref.token());
    }

    /**
     * 선택한 발신번호를 삭제한다. / Deletes the selected sender numbers.
     *
     * <p>{@code DELETE} 가 아니라 {@code POST} 인 이유는 <b>본문이 필요</b>하기 때문이다. 대상
     * 목록과 사유가 함께 와야 하고(FR-SNDD-006, FR-SNDD-009), {@code DELETE} 본문은 프록시와
     * 클라이언트에서 취급이 일정하지 않다. 대상을 URL 에 넣는 선택지는 없다 — 발신번호가 주소창과
     * 접근 로그에 남는다.</p>
     * <p>A {@code POST} rather than a {@code DELETE} because a <b>body is required</b>: the target set
     * and the reason travel together (FR-SNDD-006, FR-SNDD-009), and a {@code DELETE} body is handled
     * inconsistently by proxies and clients. Putting the targets in the URL is not an option — sender
     * numbers would land in address bars and access logs.</p>
     *
     * <p>응답의 {@code affected} 는 <b>언제나 1 이상</b>이다. 하나라도 살아 있는 행을 찾지 못하면
     * 서버가 예외로 거절하고 트랜잭션 전체가 되돌아간다(FR-SNDD-002, FR-SNDD-005) — 0건 성공은
     * 표현 불가능하며, 그것이 D-S1 에 대한 답이다.</p>
     * <p>The response's {@code affected} is <b>always at least 1</b>: if any target has no live row the
     * server refuses and the whole transaction rolls back (FR-SNDD-002, FR-SNDD-005). A zero-row
     * success is unrepresentable, which is the answer to D-S1.</p>
     *
     * @param request 삭제 내용 / the deletion
     * @param http    출처 IP 확보용 / for the source address
     * @return 삭제 결과 / the outcome
     */
    // source: biztalk_admin_10.js — btn_delete; WSVC.biztalk_admin_10_d001
    // req: FR-SNDD-001, FR-SNDD-002, FR-SNDD-005, FR-SNDD-006, FR-SNDD-009,
    //      FR-AZ-D01, FR-AZ-D04, FR-AZ-D05
    @PostMapping("/delete")
    @PreAuthorize("hasRole('OPERATOR')")
    public SenderNumberWriteResponse delete(@Valid @RequestBody SenderNumberDeleteRequest request,
                                            HttpServletRequest http) {

        return SenderNumberWriteResponse.deleted(
                writeService.delete(request.toDeletion(), http.getRemoteAddr()));
    }
}
