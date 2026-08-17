package com.webcash.iris.biztalk.domain;

/**
 * 이용기관 생명주기 상태. / 이용기관 lifecycle state.
 *
 * <p>레거시 {@code FT_FTIS_INFO.IS_STTS} 컬럼에 대응한다. 레거시는 이 컬럼을 사실상
 * {@code 'Y'}/{@code 'N'} 두 값으로만 썼으나, 컬럼 자체는 상태 컬럼이며 목록 쿼리도
 * {@code IS_STTS = :USE_YN} 로 비교하므로 세 번째 값을 수용한다.</p>
 * <p>Maps the legacy {@code FT_FTIS_INFO.IS_STTS} column. The legacy used it as a
 * two-valued flag in practice, but it is a status column and the list query compares it
 * with {@code IS_STTS = :USE_YN}, so it already tolerates a third value.</p>
 *
 * <h2>왜 {@code 'D'} 를 추가했는가 / Why {@code 'D'} was added</h2>
 * <p>논리 삭제(ADR-INST-014)를 새 컬럼 대신 이 컬럼의 세 번째 값으로 구현한다. 새
 * 컬럼을 추가하면 <b>레거시 리더가 그 컬럼을 보지 못하므로</b> 삭제된 기관이 레거시
 * 발송 경로에서 계속 살아 있게 된다 — 결함 D-I1 과 정확히 같은 형태다. 상태 컬럼을
 * 재사용하면 {@code 'D'} 는 레거시의 {@code 'Y'}·{@code 'N'} 필터 어느 쪽에도 걸리지
 * 않아 <b>구조적으로 안전하게 탈락</b>한다.</p>
 * <p>Logical delete (ADR-INST-014) is a third value here rather than a new column. A new
 * column would be <b>invisible to legacy readers</b>, leaving a deleted institution fully
 * active on the legacy send path — precisely the shape of defect D-I1. Reusing the status
 * column means {@code 'D'} matches neither the legacy {@code 'Y'} nor {@code 'N'} filter,
 * so it <b>drops out by construction</b>.</p>
 *
 * <p>이 enum 은 {@code IS_STTS} 에 대한 <b>유일한 쓰기 경로</b>다. 임의 문자열로
 * 상태가 기록되는 일이 없도록 하기 위한 것이다(RISK-I07).</p>
 * <p>This enum is the <b>only write path</b> to {@code IS_STTS}, so no ad-hoc string can
 * introduce a state (RISK-I07).</p>
 *
 * // source: IDO.KKB_FT_FTIS_INFO_L001 / _U001 / _C001 — IS_STTS
 * // req: FR-INST-006, FR-INSTL-001, FR-INSTL-002, FR-INSTL-004, ADR-INST-014
 */
public enum InstitutionStatus {

    /** 사용 중. / In use. */
    ACTIVE("Y", "사용"),

    /** 사용 중지 — 재사용으로 복구 가능. / Suspended, recoverable via 재사용. */
    SUSPENDED("N", "미사용"),

    /** 논리 삭제 — 목록에서 제외되나 행은 보존된다. / Logically deleted; excluded from lists, row retained. */
    DELETED("D", "삭제");

    private final String code;
    private final String label;

    InstitutionStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 데이터베이스에 저장되는 코드값을 반환한다. / Returns the code stored in the database.
     *
     * @return {@code IS_STTS} 컬럼값 / the {@code IS_STTS} column value
     */
    // req: ADR-INST-014
    public String code() {
        return code;
    }

    /**
     * 화면 표시용 한국어 라벨을 반환한다. / Returns the Korean display label.
     *
     * @return 표시 라벨 / the display label
     */
    // req: FR-INST-006
    public String label() {
        return label;
    }

    /**
     * 코드값에 대응하는 상태를 반환한다. / Resolves a status from its code value.
     *
     * @param code {@code IS_STTS} 컬럼값 / the {@code IS_STTS} column value
     * @return 대응 상태, 없으면 {@code null} / the matching status, or {@code null} when unmapped
     */
    // req: FR-INST-006
    public static InstitutionStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (InstitutionStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 표시용 라벨로 변환하되, 매핑되지 않는 값은 원문 그대로 반환한다.
     * Converts to a display label, returning an unmapped value verbatim.
     *
     * <p>레거시 그리드는 {@code 'Y'} 가 아니면 무조건 '미사용' 으로 표시했다. 그 결과
     * 예상치 못한 값이 조용히 '미사용' 으로 둔갑했다 — 데이터에 이상이 있어도 화면에서는
     * 정상으로 보인다. 매핑되지 않는 값을 원문으로 노출하면 이상이 눈에 띈다.</p>
     * <p>The legacy grid rendered anything other than {@code 'Y'} as 미사용, so an
     * unexpected value silently became a normal-looking one — data trouble invisible on
     * screen. Showing an unmapped value verbatim makes the anomaly visible instead.</p>
     *
     * @param code {@code IS_STTS} 컬럼값 / the {@code IS_STTS} column value
     * @return 라벨 또는 원문 / the label, or the raw value when unmapped
     */
    // source: biztalk_admin_00.js — USE_YN renderer: 'Y'==value ? '사용' : '미사용'
    // req: FR-INST-006
    public static String labelOf(String code) {
        InstitutionStatus status = fromCode(code);
        return status != null ? status.label() : code;
    }
}
