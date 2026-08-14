package com.webcash.iris.biztalk.domain;

import com.webcash.iris.biztalk.infra.db.InstitutionAdminMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이용기관 조회 서비스. / 이용기관 query service.
 *
 * <p>화면 00 의 목록 조회를 담당한다. 조회 조건 정규화는
 * {@link InstitutionSearchCriteria#of} 가, 인증키 마스킹은 {@link AtkMasker} 가,
 * 상태 라벨 변환은 {@link InstitutionStatus#labelOf} 가 맡는다.</p>
 * <p>Serves screen 00's list. Criteria normalisation lives in
 * {@link InstitutionSearchCriteria#of}, key masking in {@link AtkMasker}, and status labelling
 * in {@link InstitutionStatus#labelOf}.</p>
 *
 * // source: biztalk_admin_00.js — getData(), IDO.KKB_FT_FTIS_INFO_L001
 * // req: FR-INST-001, FR-INST-002, FR-INST-003, FR-INST-006, FR-ATK-002
 */
@Service
public class InstitutionService {

    private final InstitutionAdminMapper mapper;

    /**
     * 서비스를 생성한다. / Creates the service.
     *
     * @param mapper 이용기관 매퍼 / the institution mapper
     */
    public InstitutionService(InstitutionAdminMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 조건에 맞는 이용기관을 페이지 단위로 조회한다.
     * Searches institutions and returns one page.
     *
     * <p>전체 건수를 먼저 세고 0 이면 목록 쿼리를 실행하지 않는다. 레지스트리가 크지 않아
     * 성능상 이득은 작지만, 결과가 없을 때 빈 페이지를 <b>확정적으로</b> 반환하게 되어
     * 호출자가 빈 목록과 오류를 구분하기 쉬워진다.</p>
     * <p>The total is counted first and the row query is skipped when it is zero. The registry is
     * small so the saving is minor, but it makes the empty case <b>deterministic</b>, which is
     * what lets a caller tell "no results" apart from "something went wrong".</p>
     *
     * @param criteria 조회 조건 / the search criteria
     * @return 한 페이지 분량의 결과와 전체 건수 / one page plus the total count
     */
    // source: biztalk_admin_00.js — getData(): _gu.setData(dat.REC)
    // req: FR-INST-001, FR-INST-003
    @Transactional(readOnly = true)
    public PagedResult<InstitutionRow> search(InstitutionSearchCriteria criteria) {
        int total = mapper.count(criteria);
        if (total == 0) {
            return new PagedResult<>(List.of(), 0, criteria.page(), criteria.size());
        }

        List<InstitutionRow> rows = mapper.search(criteria).stream()
                .map(InstitutionService::toRow)
                .toList();

        return new PagedResult<>(rows, total, criteria.page(), criteria.size());
    }

    /**
     * 매퍼 원본 행을 클라이언트 표현으로 변환한다.
     * Converts a raw mapper row into its client representation.
     *
     * <p><b>인증키는 여기서 마스킹된다.</b> 변환 이후에는 평문이 존재하지 않으므로 상위
     * 계층에서 실수로 노출될 수 없다(D-I5, TM-I003).</p>
     * <p><b>This is where the 인증키 is masked.</b> No plaintext exists past this point, so no
     * upper layer can leak it by accident (D-I5, TM-I003).</p>
     *
     * @param entity 매퍼 원본 행 / the raw mapper row
     * @return 클라이언트 표현 / the client representation
     */
    // req: FR-ATK-002, FR-INST-006
    private static InstitutionRow toRow(InstitutionAdminMapper.InstitutionEntity entity) {
        return new InstitutionRow(
                entity.code(),
                entity.name(),
                entity.englishName(),
                entity.businessNumber(),
                AtkMasker.mask(entity.authKey()),
                entity.status(),
                InstitutionStatus.labelOf(entity.status()),
                entity.description(),
                entity.registeredAt(),
                entity.lastModifiedAt());
    }
}
