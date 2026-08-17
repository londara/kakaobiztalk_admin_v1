package com.webcash.iris.biztalk.api;

import com.webcash.iris.biztalk.domain.InstitutionRow;
import com.webcash.iris.biztalk.domain.InstitutionSearchCriteria;
import com.webcash.iris.biztalk.domain.InstitutionService;
import com.webcash.iris.biztalk.domain.PagedResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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

    /**
     * 컨트롤러를 생성한다. / Creates the controller.
     *
     * @param service 이용기관 조회 서비스 / the institution query service
     */
    public InstitutionAdminController(InstitutionService service) {
        this.service = service;
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
}
