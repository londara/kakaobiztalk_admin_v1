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

    /**
     * 이 번호를 보유한 <b>모든 기관</b>의 행 수를 센다.
     * Counts rows holding this number across <b>every institution</b>.
     *
     * <p>기관 술어가 <b>없는 것이 요점이다.</b> 레거시 중복검사는
     * {@code KKB_DPNO_LDGR_L001} 을 그대로 썼고 그 조건은
     * {@code IS_CD = :IS_CD AND decrypt(DP_NO) = :DP_NO} 였다 — 요청 기관 자신의 번호만 보았으므로
     * 같은 발신번호를 여러 기관이 나란히 등록할 수 있었다(D-S9). PM 결정 AMB-S03 은 발신번호를
     * 전역 유일로 정했다(FR-SNDC-004).</p>
     * <p>The <b>absence</b> of an institution predicate is the point. The legacy duplicate check
     * reused {@code KKB_DPNO_LDGR_L001}, whose predicate filtered on {@code IS_CD} as well, so it
     * only ever saw the requesting institution's own rows and the same number could be registered by
     * many (D-S9). PM ruling AMB-S03 makes a sender number globally unique (FR-SNDC-004).</p>
     *
     * <p>살아 있는 행만 센다 — 아카이브된 번호는 중복이 아니다. 이 성질 하나가 FR-SNDD-008
     * (삭제한 번호의 재등록)을 특별 취급 없이 성립시킨다(ADR-SND-017).</p>
     * <p>Counts live rows only: an archived number is not a duplicate. That single property makes
     * FR-SNDD-008 — re-registering a deleted number — work with no special case (ADR-SND-017).</p>
     *
     * @param number 발신번호 / the sender number
     * @return 보유 행 수 / the number of holding rows
     */
    // source: IDO.KKB_DPNO_LDGR_L001 — the legacy duplicate check, with its IS_CD predicate removed
    // req: FR-SNDC-004, CONST-BIZ-D01
    int countAnywhere(@Param("number") String number);

    /**
     * 발신번호를 원장에 추가한다. / Inserts a sender number into the ledger.
     *
     * @param command 바인딩 값 / the bound values
     * @return 추가된 행 수 / rows inserted
     */
    // source: IDO.KKB_DPNO_LDGR_C001
    // req: FR-SNDC-001, FR-SNDC-009, NFR-SEC-PII-D01
    int insertLedger(@Param("command") LedgerInsert command);

    /**
     * 이력을 한 건 기록한다. / Writes one history record.
     *
     * <p><b>한 번호에 한 건이다.</b> 레거시 다중 삭제는 번호마다 delete 를 실행하면서 이력은
     * {@code putAll(input)} 으로 만들었고, 그 {@code DP_NO} 는 클라이언트가 보낸 <b>콤마로
     * 이어붙인 목록</b>이었다 — 3건을 지우면 목록 전체를 하나의 "번호" 로 암호화한 행이 3개
     * 생겼다(D-S5). 이 메서드가 번호 하나만 받는 것이 그 구조적 방지책이다.</p>
     * <p><b>One record per number.</b> The legacy multi-delete ran a delete per number but built the
     * history insert with {@code putAll(input)}, where {@code DP_NO} was still the client's
     * <b>comma-joined list</b>: deleting three numbers produced three rows each encrypting the whole
     * list as one "number" (D-S5). This method taking a single number is the structural guard.</p>
     *
     * @param command 바인딩 값 / the bound values
     * @return 추가된 행 수 / rows inserted
     */
    // source: IDO.KKB_DPNO_HIS_C001
    // req: FR-SNDD-004, FR-SNDH-001, FR-SNDH-003
    int insertHistory(@Param("command") HistoryInsert command);

    /**
     * 원장의 행을 아카이브 테이블로 복사한다. / Copies a ledger row into the archive table.
     *
     * <p>PM 결정 AMB-S02 는 논리 삭제를 정했고, [ADR-SND-017]은 그것을 <b>상태 컬럼이 아니라
     * 행 이동</b>으로 구현한다. 이유는 하나다: {@code KAKAOTALK} 은 발송 권한을
     * {@code KKB_DPNO_LDGR} 에 행이 있는지로만 판단하며 어떤 상태 컬럼도 읽지 않는다. 상태
     * 컬럼을 두면 "삭제된" 번호가 계속 발송 가능하다 — 이용기관 슬라이스의 D-I1 을 의도적으로
     * 다시 만드는 셈이다.</p>
     * <p>PM ruling AMB-S02 chose logical delete and ADR-SND-017 implements it as a <b>row move
     * rather than a status column</b>, for one reason: {@code KAKAOTALK} decides sending rights by
     * whether a row exists in {@code KKB_DPNO_LDGR} and reads no status column. A status column would
     * leave "deleted" numbers fully sendable — D-I1 rebuilt on purpose.</p>
     *
     * @param institutionCode 이용기관 코드 / the institution code
     * @param number          발신번호 / the sender number
     * @param actorId         행위자 / the acting principal
     * @param reason          사유 / the reason
     * @return 복사된 행 수 / rows copied
     */
    // req: FR-SNDD-001, FR-SNDD-003, ADR-SND-017, CONST-DATA-D04
    int archive(@Param("institutionCode") String institutionCode,
                @Param("number") String number,
                @Param("actorId") String actorId,
                @Param("reason") String reason);

    /**
     * 원장에서 행을 지운다. / Removes the row from the ledger.
     *
     * <p>반환값을 <b>반드시</b> 확인해야 한다. 0을 무시한 것이 D-S1 이다 — 0건을 지운
     * {@code DELETE} 는 SQL 오류가 아니므로 레거시는 예외를 보지 못하고 이력을 쓰고 성공을
     * 보고했다(FR-SNDD-002, NFR-OPS-D02).</p>
     * <p>The return value <b>must</b> be checked. Ignoring a zero is D-S1: a zero-row
     * {@code DELETE} is not a SQL error, so the legacy saw no exception, wrote the history row and
     * reported success (FR-SNDD-002, NFR-OPS-D02).</p>
     *
     * @param institutionCode 이용기관 코드 / the institution code
     * @param number          발신번호 / the sender number
     * @return 지워진 행 수 / rows deleted
     */
    // source: IDO.KKB_DPNO_LDGR_D001
    // req: FR-SNDD-001, FR-SNDD-002, NFR-OPS-D02
    int deleteLive(@Param("institutionCode") String institutionCode,
                   @Param("number") String number);

    /**
     * 원장 추가 문장에 바인딩되는 값. / The values bound into the ledger insert.
     *
     * <p>{@code actorId} 는 요청이 아니라 <b>세션</b>에서 온다(FR-SNDC-009). 레거시 액션 JSP 는
     * {@code SessionManager.getEml()} / {@code getFlnm()} 로 같은 판단을 했고, 그 점은 옳았다.
     * 포털의 신원은 이메일 하나이므로({@code TenantPrincipal}) 이름 자리에도 같은 값이 들어간다 —
     * 이용기관 슬라이스가 {@code LSED_NM} 에서 내린 것과 같은 결정이다(FR-INSTC-012).</p>
     * <p>{@code actorId} comes from the <b>session</b>, not the request (FR-SNDC-009). The legacy
     * action JSPs made the same judgement via {@code SessionManager}, and were right to. The portal's
     * identity is a single email ({@code TenantPrincipal}), so the same value fills the name column —
     * the decision the institution slice made for {@code LSED_NM} (FR-INSTC-012).</p>
     *
     * @param institutionCode 이용기관 코드 / the institution code
     * @param number          발신번호 — 문장이 {@code ENCRYPT()} 를 씌운다 / the number; the statement wraps it in {@code ENCRYPT()}
     * @param description     설명 / the description
     * @param actorId         행위자 / the acting principal
     */
    // source: IDO.KKB_DPNO_LDGR_C001 in-items
    // req: FR-SNDC-001, FR-SNDC-009, NFR-SEC-PII-D01
    record LedgerInsert(
            String institutionCode,
            String number,
            String description,
            String actorId
    ) {
    }

    /**
     * 이력 추가 문장에 바인딩되는 값. / The values bound into the history insert.
     *
     * @param institutionCode 이용기관 코드 / the institution code
     * @param number          발신번호 — <b>한 건</b>이다 / the number, <b>exactly one</b>
     * @param action          행위 코드 / the action code
     * @param reason          사유 / the reason
     * @param actorId         행위자 / the acting principal
     */
    // source: IDO.KKB_DPNO_HIS_C001 in-items
    // req: FR-SNDD-004, FR-SNDH-001, FR-SNDH-003
    record HistoryInsert(
            String institutionCode,
            String number,
            String action,
            String reason,
            String actorId
    ) {
    }
}
