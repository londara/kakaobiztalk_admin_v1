package com.webcash.iris.biztalk.alimtalk.infra.db;

import com.webcash.iris.biztalk.alimtalk.domain.TemplateSummary;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 알림톡 템플릿 조회 — {@code KKB_MSG_TMPL} 읽기 전용. / AlimTalk template reads, {@code KKB_MSG_TMPL} read-only.
 *
 * <h2>이 매퍼가 슬라이스의 방향을 바꾼 발견이다 / the finding that redirected the slice</h2>
 * <p>{@code KKB_MSG_TMPL} 은 템플릿 <b>본문</b>({@code TEMPLATE_MSG})을
 * {@code (IS_CD, TEMPLATE_CODE)} 로 이미 보관하고 있다 — 화면 61 의 작성 폼이 수집하는 바로 그
 * 두 값이다. 그런데 레거시 검증 탭은 운영자에게 템플릿 본문을 <b>손으로 붙여넣게</b> 했다
 * (D-A16). 즉 DB 가 이미 갖고 있는 것을 사람이 옮겨 적고 있었고, 옮겨 적은 것이 현재 본문과
 * 같은지 확인할 방법도 없었다.</p>
 * <p>{@code KKB_MSG_TMPL} already stores the template <b>body</b> ({@code TEMPLATE_MSG}) keyed by
 * {@code (IS_CD, TEMPLATE_CODE)} — precisely the two values screen 61's form collects. Yet the legacy
 * validation tab asked the operator to <b>paste the body in by hand</b> (D-A16): a human transcribing
 * what the database already held, with no way to confirm the transcription matched the current body.</p>
 *
 * <p>그래서 검증이 발송 경로로 옮겨간다(FR-ATV-001) — 벤더 거절을 실제로 막을 수 있는 유일한
 * 지점이다.</p>
 * <p>So validation moves into the send path (FR-ATV-001), the only place it can actually prevent a
 * vendor rejection.</p>
 *
 * <h2>레거시 조회를 그대로 포팅하지 않는다 / the legacy queries are not ported verbatim</h2>
 * <p>{@code KKB_MSG_TMPL_L003} 은 <b>운영자 범위 없이</b> 활성 이용기관 전체의 템플릿을 한 번에
 * 돌려준다. 그 형태를 포팅하면 FR-ATT-003 을 위반한다. 여기서는 모든 조회가
 * {@code IS_CD} 로 한정되며, 그 {@code IS_CD} 는 세션에서 나온다(FR-AZ-A02).</p>
 * <p>{@code KKB_MSG_TMPL_L003} returns every active institution's templates in one query with
 * <b>no operator scoping</b>. Porting that shape would violate FR-ATT-003. Every query here is bounded
 * by {@code IS_CD}, and that {@code IS_CD} comes from the session (FR-AZ-A02).</p>
 *
 * <p><b>검증 한계</b>: Docker 가 금지되어(RISK-A12) 실제 PostgreSQL 통합 테스트를 돌릴 수 없다.
 * {@code TemplateMapperSqlTest} 는 <b>대체물이며 동등물이 아니다</b> — 식별자 회귀는 막지만
 * 테이블·컬럼의 실재를 증명하지 못한다. 발신번호·이용기관 슬라이스와 같은 처지다.</p>
 * <p><b>Verification limit</b>: with Docker prohibited (RISK-A12) no real PostgreSQL integration test
 * can run. {@code TemplateMapperSqlTest} is a <b>substitute, not an equivalent</b>: it prevents identifier
 * regressions but cannot prove the table or columns exist — the same position as the two prior slices.</p>
 *
 * // source: IDO.KKB_MSG_TMPL_L001, IDO.KKB_MSG_TMPL_L002, IDO.KKB_MSG_TMPL_L003
 * // req: FR-ATV-001, FR-ATV-003, FR-ATT-001, FR-ATT-002, FR-ATT-003, CONST-DATA-A03
 */
@Mapper
public interface TemplateMapper {

    /**
     * 이용기관의 템플릿 목록을 조회한다. / Lists an institution's templates.
     *
     * <p>{@code KKB_MSG_TMPL_L001} 은 {@code ORDER BY} 가 없었다. 목록 순서가 요청마다 달라지면
     * 운영자가 방금 본 항목을 다시 찾지 못하므로, 발신번호 슬라이스의 FR-SND-004 와 같은 이유로
     * 결정적 순서를 둔다.</p>
     * <p>{@code KKB_MSG_TMPL_L001} carried no {@code ORDER BY}. A list whose order varies between
     * requests loses the operator the item they just saw, so a deterministic order is applied for the
     * same reason as FR-SND-004 in the 발신번호 slice.</p>
     *
     * @param institutionCode 이용기관코드 — 세션에서 도출된 값 / the institution code, derived from the session
     * @return 템플릿 요약 목록 / template summaries
     *
     * // source: IDO.KKB_MSG_TMPL_L001 — SELECT TEMPLATE_CODE, TEMPLATE_TITLE FROM KKB_MSG_TMPL WHERE IS_CD = :IS_CD
     * // req: FR-ATT-001, FR-ATT-002, FR-ATT-003
     */
    @Select("""
            SELECT TEMPLATE_CODE AS templateCode,
                   TEMPLATE_TITLE AS templateTitle
              FROM KKB_MSG_TMPL
             WHERE IS_CD = #{institutionCode}
             ORDER BY TEMPLATE_CODE
            """)
    List<TemplateSummary> findByInstitution(@Param("institutionCode") String institutionCode);

    /**
     * 템플릿 본문을 조회한다. / Reads a template body.
     *
     * <p>{@code IS_CD} 를 조건에 포함하는 것이 <b>인가</b>의 일부다. 코드만으로 조회하면 다른
     * 이용기관의 템플릿 본문을 읽을 수 있고, 그 본문은 그 기관이 무엇을 고객에게 보내는지를
     * 드러낸다(T-A16).</p>
     * <p>Including {@code IS_CD} in the predicate is part of the <b>authorization</b>: looking up by code
     * alone would read another institution's template body, which reveals what that institution sends to
     * its customers (T-A16).</p>
     *
     * @param institutionCode 이용기관코드 / the institution code
     * @param templateCode    템플릿코드 / the template code
     * @return 템플릿 본문, 등록되지 않았으면 {@code null} / the body, or {@code null} when not registered
     *
     * // source: IDO.KKB_MSG_TMPL_L002 — SELECT TEMPLATE_MSG FROM KKB_MSG_TMPL WHERE IS_CD = :IS_CD AND TEMPLATE_CODE = :TEMPLATE_CODE
     * // req: FR-ATV-001, FR-ATV-003, FR-ATT-004, CONST-DATA-A03
     */
    @Select("""
            SELECT TEMPLATE_MSG
              FROM KKB_MSG_TMPL
             WHERE IS_CD = #{institutionCode}
               AND TEMPLATE_CODE = #{templateCode}
            """)
    String findTemplateBody(@Param("institutionCode") String institutionCode,
                            @Param("templateCode") String templateCode);
}
