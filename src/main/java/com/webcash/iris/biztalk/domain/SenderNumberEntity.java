package com.webcash.iris.biztalk.domain;

/**
 * {@code KKB_DPNO_LDGR} 한 행의 원본 표현. / The raw representation of one {@code KKB_DPNO_LDGR} row.
 *
 * <p>매퍼가 채우는 타입이며 클라이언트에 그대로 나가지 않는다. 표시용 변환은
 * {@link SenderNumberRow#from} 이 담당한다.</p>
 * <p>Populated by the mapper and never returned to a client directly; conversion for display is
 * {@link SenderNumberRow#from}'s job.</p>
 *
 * <p>{@code registeredById}·{@code updatedById} 는 <b>의도적으로 담지 않는다.</b> 레거시는
 * 운영자 이메일을 이 두 컬럼에 평문으로 저장하면서 이름({@code RGSR_NM}) 은 암호화했고,
 * 조회 시에는 둘 다 브라우저로 보냈다. 담지 않으면 상위 계층이 실수로 노출할 수 없다
 * (D-S16, D-S21).</p>
 * <p>{@code registeredById}/{@code updatedById} are <b>deliberately absent.</b> The legacy stored
 * operator email in those columns in clear while encrypting the name, then shipped both to the
 * browser. What the type does not carry, no upper layer can leak (D-S16, D-S21).</p>
 *
 * @param institutionCode 이용기관 코드 / the institution code
 * @param institutionName 기관명 / the institution's name
 * @param number          복호화된 발신번호 / the decrypted sender number
 * @param registeredBy    등록자명 — DB 에서 마스킹되어 옴 / registering operator, masked in the DB
 * @param registeredAt    등록일시 / when it was registered
 * @param updatedBy       수정자명 — DB 에서 마스킹되어 옴 / last editor, masked in the DB
 * @param updatedAt       수정일시 / when it was last changed
 * @param description     설명 / the description
 *
 * // source: IDO.KKB_DPNO_LDGR_L002 — SELECT ... FROM KKB_DPNO_LDGR A WHERE IS_CD = :IS_CD
 * // req: FR-SND-005, NFR-SEC-PII-D02
 */
public record SenderNumberEntity(
        String institutionCode,
        String institutionName,
        String number,
        String registeredBy,
        String registeredAt,
        String updatedBy,
        String updatedAt,
        String description
) {
}
