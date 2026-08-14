package com.webcash.iris.biztalk.api;

import com.webcash.iris.biztalk.infra.db.InstitutionMapper;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 이용기관 목록 엔드포인트 — 운영자 전용. / 이용기관 list endpoint, operators only.
 *
 * <h2>레거시 결함 대응 / The legacy behaviour being fixed</h2>
 * <p>레거시 {@code biztalk_admin_40.js} 의 {@code fn_getIsList()} 는 화면을 열 때마다
 * {@code biztalk_admin_00_l001} 을 {@code USE_YN=ALL} 로 호출해 <b>모든 이용기관 목록을
 * 드롭다운에 채웠다</b> — 로그인한 사용자가 누구든 상관없이. 사내 콘솔에서는 무해했지만,
 * 외부 고객사에 노출되는 포털에서는 <b>전체 고객사 명단</b>이 유출된다.</p>
 * <p>The legacy populated a dropdown with <b>every</b> 이용기관 on every screen load, regardless
 * of who was logged in. Harmless on an intranet console; on a portal exposed to client companies
 * it leaks the <b>entire customer list</b>.</p>
 *
 * <p>경로를 {@code /api/admin/**} 아래 두어 {@code SecurityConfig} 의 운영자 규칙이 적용되게
 * 하고, 컨트롤러 수준 {@code @PreAuthorize} 를 이중으로 둔다 — 라우팅 규칙이 실수로
 * 완화되어도 남는다.</p>
 * <p>Placed under {@code /api/admin/**} so the operator rule applies, with a controller-level
 * {@code @PreAuthorize} as defence in depth that survives an accidental loosening of routing.</p>
 *
 * // source: biztalk_admin_40.js — fn_getIsList() calling biztalk_admin_00_l001 with USE_YN=ALL
 * // req: FR-TEN-004, NFR-SEC-TENANT, TM-011
 */
@RestController
@RequestMapping("/api/admin/institutions")
public class InstitutionController {

    private final InstitutionMapper mapper;

    /**
     * 컨트롤러를 생성한다. / Creates the controller.
     *
     * @param mapper 이용기관 매퍼 / the institution mapper
     */
    public InstitutionController(InstitutionMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 이용기관 목록을 반환한다. / Returns the 이용기관 list.
     *
     * <p>이용기관 담당자에게는 이 엔드포인트가 존재하지 않는 것과 같다 — 역할 검사에서
     * 거부되므로 목록을 열거할 수 없다.</p>
     * <p>For a client-company user this endpoint is effectively absent: the role check refuses
     * them, so the list cannot be enumerated.</p>
     *
     * @return 이용기관 목록 / the institutions
     */
    // req: FR-TEN-004, TM-011
    @GetMapping
    @PreAuthorize("hasRole('OPERATOR')")
    public List<InstitutionMapper.Institution> list() {
        return mapper.findAllActive();
    }
}
