package com.webcash.iris.biztalk.infra.db;

import com.webcash.iris.biztalk.domain.SenderNumberCriteria;
import com.webcash.iris.biztalk.domain.SenderNumberEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 발신번호 원장 매퍼. / Sender-number ledger mapper.
 *
 * <p>SQL 은 {@code mybatis/mapper/biztalk/SenderNumberMapper.xml} 에 있다. 레거시
 * {@code IDO.KKB_DPNO_LDGR_L002} 를 옮기되 세 가지를 고친다 — 이름 기반 컬럼 매핑,
 * {@code ORDER BY}, {@code LIMIT}/{@code OFFSET}.</p>
 * <p>The SQL lives in {@code mybatis/mapper/biztalk/SenderNumberMapper.xml}. It ports
 * {@code IDO.KKB_DPNO_LDGR_L002} with three corrections: name-based column mapping,
 * an {@code ORDER BY}, and {@code LIMIT}/{@code OFFSET}.</p>
 *
 * <h2>조회 형태에 대한 메모 / a note on the lookup form</h2>
 * <p>번호 조회는 {@code decrypt(DP_NO) = :number} 형태를 쓴다. 컬럼에 함수를 씌우므로
 * 인덱스를 타지 못하지만, {@code ENCRYPT} 의 결정성 여부와 <b>무관하게 옳다</b>. 결정적이라면
 * {@code DP_NO = ENCRYPT(:number)} 로 바꿔 인덱스를 태울 수 있고 그것이 ADR-SND-018 의
 * 선택지이지만, 아직 확인되지 않았다(S1-01). 확인 전에 인덱스 형태를 먼저 쓰면 비결정적일
 * 경우 <b>조회가 조용히 0건</b>이 된다 — 정확히 D-S1 의 실패 방식이므로, 느리고 옳은 쪽을
 * 택한다.</p>
 * <p>Number lookups use {@code decrypt(DP_NO) = :number}. That applies a function to the column
 * and so cannot use an index, but it is <b>correct regardless of whether {@code ENCRYPT} is
 * deterministic</b>. If it is, the form becomes {@code DP_NO = ENCRYPT(:number)} and indexes
 * apply — that is ADR-SND-018's option, still unconfirmed (S1-01). Adopting the indexed form
 * before confirmation would make lookups <b>silently return nothing</b> if it is
 * non-deterministic, which is exactly D-S1's failure mode. Slow and correct wins.</p>
 *
 * // source: IDO.KKB_DPNO_LDGR_L002, IDO.KKB_DPNO_LDGR_L001
 * // req: FR-SND-003, FR-SND-004, CONST-DATA-D03, ADR-SND-018
 */
@Mapper
public interface SenderNumberMapper {

    /**
     * 이용기관의 발신번호 건수를 센다. / Counts an institution's sender numbers.
     *
     * @param criteria 조회 조건 / the criteria
     * @return 전체 건수 / the total count
     */
    // req: FR-SND-003
    int count(@Param("criteria") SenderNumberCriteria criteria);

    /**
     * 이용기관의 발신번호를 페이지 단위로 조회한다.
     * Returns one page of an institution's sender numbers.
     *
     * @param criteria 조회 조건 / the criteria
     * @return 원본 행 목록 / the raw rows
     */
    // source: IDO.KKB_DPNO_LDGR_L002
    // req: FR-SND-001, FR-SND-003, FR-SND-004
    List<SenderNumberEntity> findPage(@Param("criteria") SenderNumberCriteria criteria);

    /**
     * 발신번호 한 건을 조회한다. / Loads a single sender number.
     *
     * <p>{@code null} 을 반환할 수 있으며, 호출자는 그것을 <b>실패로</b> 다뤄야 한다
     * (FR-SNDD-002). 레거시는 일치하는 행이 없어도 삭제를 성공으로 보고했다.</p>
     * <p>May return {@code null}, and callers must treat that as a <b>failure</b>
     * (FR-SNDD-002): the legacy reported success even when nothing matched.</p>
     *
     * @param institutionCode 이용기관 코드 / the institution code
     * @param number          발신번호 / the sender number
     * @return 원본 행, 없으면 null / the raw row, or null
     */
    // source: IDO.KKB_DPNO_LDGR_L001
    // req: FR-SNDU-002, FR-SNDD-002
    SenderNumberEntity findOne(@Param("institutionCode") String institutionCode,
                               @Param("number") String number);
}
