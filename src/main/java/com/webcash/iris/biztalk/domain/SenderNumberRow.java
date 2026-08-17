package com.webcash.iris.biztalk.domain;

/**
 * 목록 화면에 표시되는 발신번호 한 행. / One sender-number row as shown on the list.
 *
 * <p>레거시 응답은 화면이 쓰지 않는 필드까지 실어 보냈다. {@code WSVC.biztalk_admin_10_l001}
 * 은 {@code RGSR_ID} 와 {@code UDT_ID} 를 반환했지만 그리드 정의
 * ({@code biztalk_admin_10.js} 의 {@code colDefs}) 에는 두 컬럼이 없다 — 운영자 이메일이
 * 표시되지도 않을 브라우저까지 매 조회마다 전달된 것이다(D-S21).</p>
 * <p>The legacy response carried fields the screen never used: the contract returned
 * {@code RGSR_ID} and {@code UDT_ID}, neither of which appears in the grid's {@code colDefs}.
 * Operator email addresses travelled to the browser on every query without ever being
 * displayed (D-S21).</p>
 *
 * <p>여기에는 <b>표시되는 것만</b> 담는다. 응답 형태가 화면 정의와 일치하면 과다 반환은
 * 실수가 아니라 눈에 띄는 추가가 된다.</p>
 * <p>This record holds <b>only what is displayed</b>. When the response shape matches the screen
 * definition, over-fetching becomes a visible addition rather than an oversight.</p>
 *
 * @param ref              행 식별자 — 표시값과 분리된 불투명 토큰 / opaque row identifier, separate from display
 * @param institutionName  기관명 / the institution's name
 * @param number           발신번호 — 전체 표시 / the sender number, shown in full
 * @param registeredBy     등록자명 — 마스킹됨 / the registering operator's name, masked
 * @param registeredAt     등록일시 / when it was registered
 * @param updatedBy        수정자명 — 마스킹됨 / the last editor's name, masked
 * @param updatedAt        수정일시 / when it was last changed
 * @param description      설명 / the description
 *
 * // source: biztalk_admin_10.js — drawGrid() colDefs: IS_NM, DP_NO, RGSR_NM, RGDT, UDT_NM, UDDT, DSCP
 * // req: FR-SND-005, FR-SND-006, FR-SND-008, FR-SND-009, NFR-SEC-PII-D02
 */
public record SenderNumberRow(
        String ref,
        String institutionName,
        String number,
        String registeredBy,
        String registeredAt,
        String updatedBy,
        String updatedAt,
        String description
) {

    /**
     * 매퍼 원본 행을 표시용 행으로 변환한다.
     * Converts a raw mapper row into its display representation.
     *
     * <p>발신번호는 <b>마스킹하지 않는다</b>(PM 결정 AMB-S04). 레거시는 목록에서만
     * {@code RegexNameMasking.maskName()} 을 적용하고 상세에서는 원본을 반환했는데, 그
     * 불일치가 삭제 기능을 망가뜨린 직접 원인이었다(D-S1). 노출 통제는 마스킹이 아니라
     * 조회 감사로 한다(FR-SND-011).</p>
     * <p>The number is <b>not masked</b> (PM ruling AMB-S04). The legacy masked it on the list
     * but not on the detail view, and that inconsistency is what broke deletion (D-S1). Exposure
     * is controlled by auditing reads instead (FR-SND-011).</p>
     *
     * <p>식별자는 원본 번호에서 파생하되 표시값과 같은 값이 되지 않는다 —
     * {@link SenderNumberRef} 참고.</p>
     * <p>The identifier derives from the real number but never equals the displayed value; see
     * {@link SenderNumberRef}.</p>
     *
     * @param entity 매퍼 원본 행 / the raw mapper row
     * @return 표시용 행 / the display row
     */
    // source: biztalk_admin_10_l001_act.jsp — RegexNameMasking.maskName(DP_NO)  [removed, AMB-S04]
    // req: FR-SND-006, FR-SND-007
    public static SenderNumberRow from(SenderNumberEntity entity) {
        SenderNumberRef ref = new SenderNumberRef(entity.institutionCode(), entity.number());
        return new SenderNumberRow(
                ref.token(),
                entity.institutionName(),
                entity.number(),
                entity.registeredBy(),
                entity.registeredAt(),
                entity.updatedBy(),
                entity.updatedAt(),
                entity.description());
    }
}
