package com.webcash.iris.biztalk.api;

import com.webcash.iris.biztalk.domain.InstitutionRow;
import com.webcash.iris.biztalk.domain.InstitutionSearchCriteria;
import com.webcash.iris.biztalk.domain.InstitutionService;
import com.webcash.iris.biztalk.domain.InstitutionWriteService;
import com.webcash.iris.biztalk.domain.PagedResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 이용기관 관리 엔드포인트 — 운영자 전용. / 이용기관 admin endpoints, operators only.
 *
 * <h2>레거시 결함 대응 / The legacy behaviour being fixed</h2>
 * <p>레거시 화면 00 의 여덟 개 서비스는 모두 {@code <login>Y</login>} 하나만 걸려
 * 있었다. 역할 검사는 <b>어디에도 없었고</b>, 담당자관리 탭의 '권한 없음' 경고는
 * 브라우저 안의 {@code alert()} 였다. 결과적으로 <b>로그인한 사용자라면 누구나</b>
 * 임의의 이용기관을 삭제하거나 덮어쓸 수 있었다(결함 D-I2).</p>
 * <p>All eight of screen 00's legacy services carried {@code <login>Y</login>} and nothing
 * else. There was <b>no role check anywhere</b>, and the manager tab's "권한 없음" warning was
 * an {@code alert()} inside the browser. In effect <b>any authenticated user</b> could delete
 * or overwrite any institution (defect D-I2).</p>
 *
 * <p>경로를 {@code /api/admin/**} 아래 두어 {@code SecurityConfig} 의 운영자 규칙이
 * 적용되게 하고, 컨트롤러 수준 {@code @PreAuthorize} 를 이중으로 둔다 — 라우팅 규칙이
 * 실수로 완화되어도 남는다.</p>
 * <p>Placed under {@code /api/admin/**} so the operator rule applies, with a controller-level
 * {@code @PreAuthorize} as defence in depth that survives an accidental loosening of routing.</p>
 *
 * <p>{@code GET} 을 쓰는 이유: 조회 조건이 기관명과 상태뿐이며 개인정보를 포함하지
 * 않는다. 문자내역이 {@code POST} 를 쓰는 것은 조건에 전화번호가 들어가기 때문이고,
 * 여기에는 해당 사유가 없다.</p>
 * <p>{@code GET} is used because the criteria are only a name and a status, with no personal
 * data. 문자내역 uses {@code POST} because its criteria can contain a phone number; that
 * reason does not apply here.</p>
 *
 * // source: biztalk_admin_00_view.jsp, biztalk_admin_00.js, WSVC.biztalk_admin_00_l001.xml
 * // req: FR-AZ-I01, FR-AZ-I02, FR-AZ-I03, FR-INST-001, FR-INST-003, TM-I001
 */
@RestController
@RequestMapping("/api/admin/institutions")
public class InstitutionAdminController {

    private final InstitutionService service;
    private final InstitutionWriteService writeService;

    /**
     * 컨트롤러를 생성한다. / Creates the controller.
     *
     * @param service      이용기관 조회 서비스 / the institution query service
     * @param writeService 이용기관 쓰기 서비스 / the institution write service
     */
    public InstitutionAdminController(InstitutionService service,
                                      InstitutionWriteService writeService) {
        this.service = service;
        this.writeService = writeService;
    }

    /**
     * 이용기관을 조회한다. / Searches institutions.
     *
     * <p>이용기관 담당자에게는 이 엔드포인트가 존재하지 않는 것과 같다 — 역할 검사에서
     * 거부되므로 다른 고객사를 열거할 수 없다(FR-AZ-I03).</p>
     * <p>For a client-company user this endpoint is effectively absent: the role check refuses
     * them, so other client companies cannot be enumerated (FR-AZ-I03).</p>
     *
     * @param name   기관명 검색어 / name fragment
     * @param status 상태 필터 {@code ALL}/{@code Y}/{@code N} / status filter
     * @param page   0부터 시작하는 페이지 번호 / zero-based page index
     * @param size   페이지 크기 / page size
     * @return 한 페이지 분량의 이용기관 / one page of institutions
     */
    // req: FR-INST-001, FR-INST-003, FR-AZ-I01
    @GetMapping("/search")
    @PreAuthorize("hasRole('OPERATOR')")
    public PagedResult<InstitutionRow> search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false, defaultValue = InstitutionSearchCriteria.STATUS_ALL) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        // 정규화는 도메인이 담당한다. 컨트롤러가 상한을 직접 적용하면 다른 진입점이
        // 생겼을 때 그 상한이 빠질 수 있다.
        // Normalisation belongs to the domain: clamping here would let a future entry point
        // bypass the cap.
        return service.search(InstitutionSearchCriteria.of(name, status, page, size));
    }

    /**
     * 이용기관 한 건을 조회한다 — 수정 팝업이 여는 값.
     * Reads one institution, as opened by the edit popup.
     *
     * <p><b>인증키는 마스킹되어 나간다.</b> 레거시 상세조회({@code biztalk_admin_01_l002})는
     * 계약의 {@code out} 규칙에 {@code ATK} 를 선언하고 평문을 돌려주었으며, 팝업은 그 값을
     * 입력칸에 넣었다 — 목록(D-I5)과 중복검사(D-I3)에 이은 세 번째 노출 경로이고 첫 분석에서
     * 기록되지 않았다(D-I20). 전체 값을 보는 것은 별도로 인가·감사되는 조작이다
     * (FR-ATK-003).</p>
     * <p><b>The 인증키 leaves masked.</b> The legacy detail service declared {@code ATK} in its
     * contract and returned the plaintext, and the popup put it into a field — the third exposure
     * path after the list (D-I5) and the duplicate check (D-I3), unrecorded by the first analysis
     * (D-I20). Seeing the full value is a separately authorized and audited operation
     * (FR-ATK-003).</p>
     *
     * <p>경로가 {@code /search} 와 겹치지 않는 이유: Spring 은 리터럴 경로를 템플릿보다 먼저
     * 맞춘다. 그럼에도 형식은 서비스가 검사한다 — 라우팅 우선순위에 정확성을 의존하지
     * 않는다.</p>
     * <p>This does not collide with {@code /search}: Spring matches a literal path ahead of a
     * template. The format is still checked in the service — correctness does not rest on routing
     * precedence.</p>
     *
     * @param code 기관코드 / the institution code
     * @return 마스킹된 이용기관 / the institution, key masked
     */
    // source: biztalk_admin_01.js — loadData(), biztalk_admin_01_l002
    // req: FR-INSTC-001, FR-INSTC-010, FR-ATK-002, FR-AZ-I01, D-I20
    @GetMapping("/{code}")
    @PreAuthorize("hasRole('OPERATOR')")
    public InstitutionRow detail(@PathVariable String code) {
        return service.findByCode(code);
    }

    /**
     * 이용기관을 수정한다. / Updates an institution.
     *
     * <p>{@code PUT} 이며 <b>등록으로 바뀌지 않는다.</b> 레거시는 등록과 수정을 하나의
     * UPSERT 로 처리해, 이미 있는 기관코드로 등록을 호출하면 그 기관과 인증키까지 조용히
     * 덮어썼다(D-I6). 대상이 없으면 여기서는 404 이며 새 행은 생기지 않는다.</p>
     * <p>A {@code PUT} that <b>cannot become a create</b>. The legacy served both from one upsert,
     * so a create with an existing code silently overwrote that institution and its credential
     * (D-I6). A missing target is a 404 here and no row is created.</p>
     *
     * <p>본문에 기관코드가 없다 — 대상은 경로가 정한다(FR-INSTC-002). 인증키도 없다 — 재발급은
     * 아래의 별도 조작이다(FR-INSTC-011).</p>
     * <p>The body carries no code — the path names the target (FR-INSTC-002) — and no key:
     * rotation is the separate operation below (FR-INSTC-011).</p>
     *
     * @param code    기관코드 / the institution code
     * @param request 수정 내용 / the requested changes
     * @param http    출처 IP 확보용 / for the source address
     * @return 수정된 이용기관 — 인증키는 마스킹된다 / the updated institution, key masked
     */
    // source: biztalk_admin_01.js — fn_save(), biztalk_admin_01_c001
    // req: FR-INSTC-002, FR-INSTC-003, FR-INSTC-004, FR-INSTC-006, FR-INSTC-007,
    //      FR-INSTC-012, FR-INSTC-013, FR-AZ-I01, FR-AZ-I04
    @PutMapping("/{code}")
    @PreAuthorize("hasRole('OPERATOR')")
    public InstitutionRow update(@PathVariable String code,
                                 @Valid @RequestBody InstitutionUpdateRequest request,
                                 HttpServletRequest http) {

        return writeService.update(code, request.toEdit(), http.getRemoteAddr());
    }

    /**
     * 인증키를 재발급한다. / Rotates the 인증키.
     *
     * <p>확인한 시점에 <b>즉시 확정</b>되며 저장과 무관하다(FR-INSTC-011, PM 결정 AMB-I13).
     * 레거시는 브라우저에서 {@code Math.random()} 으로 만든 값을 폼에 담아 두었다가 저장할 때
     * 기록했으므로, 닫기를 누르면 사라졌고 시도한 기록도 남지 않았다(D-I4).</p>
     * <p>Committed <b>at once</b> on confirmation, independent of the save (FR-INSTC-011, PM ruling
     * AMB-I13). The legacy generated the value with {@code Math.random()} in the browser and held
     * it in the form until 저장, so 닫기 discarded it and left no record of the attempt (D-I4).</p>
     *
     * <p>응답은 새 키를 <b>한 번</b> 담는다 — 운영자가 고객사에 전달해야 하기 때문이다. 서버는
     * 다시 보여주지 않고 로그에도 남기지 않는다(FR-ATK-004). 그래서 {@code POST} 다: 응답에
     * 자격증명이 담기는 요청은 URL 이나 브라우저 이력에 남아서는 안 된다.</p>
     * <p>The response carries the new key <b>once</b>, because the operator has to pass it to the
     * customer. The server will not show it again and never logs it (FR-ATK-004). Hence
     * {@code POST}: a request whose response carries a credential must not sit in a URL or in
     * browser history.</p>
     *
     * @param code 기관코드 / the institution code
     * @param http 출처 IP 확보용 / for the source address
     * @return 새 인증키 / the newly issued key
     */
    // source: biztalk_admin_01.js — btn_generate_code
    // req: FR-ATK-001, FR-ATK-004, FR-ATK-005, FR-INSTC-011, FR-AZ-I01, FR-AZ-I04
    @PostMapping("/{code}/key/rotate")
    @PreAuthorize("hasRole('OPERATOR')")
    public AuthKeyResponse rotateAuthKey(@PathVariable String code, HttpServletRequest http) {
        return new AuthKeyResponse(writeService.rotateAuthKey(code, http.getRemoteAddr()));
    }
}
