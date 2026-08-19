package com.webcash.iris.biztalk.domain;

/**
 * 발송 구분 — 집계의 출처. / Send source: which aggregate a figure came from.
 *
 * <p>레거시는 이 구분을 <b>환경 설정으로</b> 결정했다. {@code TSTCL_DV=REAL} 일 때만
 * 대량발송 DB 를 조회했고, 그 결과 운영이 아닌 어떤 환경도 운영과 같은 코드 경로를
 * 실행하지 못했다(D-R4, D-R6, D-R7). 여기서는 <b>사용자가 고르는 조회 조건</b>이며,
 * 어떤 설정값도 이 값을 바꾸지 않는다.</p>
 * <p>The legacy decided this <b>by configuration</b>: it queried the bulk database only when
 * {@code TSTCL_DV=REAL}, so no non-production environment ever ran production's code path
 * (D-R4, D-R6, D-R7). Here it is a <b>user-chosen filter</b>, and no setting varies it.</p>
 *
 * // source: biztalk_admin_20_l001_act.jsp — JexConst.getProperty("TSTCL_DV").equals("REAL")
 * // req: FR-RPTS-002, FR-RPTS-004, AMB-R01
 */
public enum SendSource {

    /** API 발송 — {@code BIZTALK_DB} / API sends, from {@code BIZTALK_DB}. */
    API("API발송"),

    /** 대량발송 — {@code BIZTALK_BULK_DB} / bulk sends, from {@code BIZTALK_BULK_DB}. */
    BULK("대량발송"),

    /**
     * 두 출처의 합계 / the sum of both sources.
     *
     * <p>행을 나열하는 것이 아니라 같은 일자·기관의 건수를 <b>더한다</b>(FR-RPTS-003).
     * 레거시는 두 결과를 단순히 이어 붙여 한 기관이 하루에 두 행으로 나타났다.</p>
     * <p>Counters for the same 일자 + 기관 are <b>summed</b>, not listed side by side
     * (FR-RPTS-003). The legacy concatenated the two result sets, so one institution appeared
     * twice for one day.</p>
     */
    ALL("전체");

    private final String label;

    SendSource(String label) {
        this.label = label;
    }

    /**
     * 화면에 표시할 한국어 이름을 반환한다. / Returns the Korean display label.
     *
     * @return 표시명 / the label
     */
    // req: FR-RPT-009
    public String label() {
        return label;
    }

    /**
     * 요청 문자열을 구분으로 해석한다. 값이 없거나 알 수 없으면 {@link #ALL}.
     * Parses a request value, defaulting to {@link #ALL} when absent or unrecognised.
     *
     * <p>알 수 없는 값을 거부하지 않고 기본값으로 두는 이유: 이 파라미터는 보안 경계가
     * 아니라 표시 필터다. 범위(기관)는 {@link ReportScope} 가 세션에서 따로 정한다.</p>
     * <p>An unknown value defaults rather than failing because this parameter is a display
     * filter, not a security boundary — scope is decided separately from the session by
     * {@link ReportScope}.</p>
     *
     * @param raw 요청 값 / the request value
     * @return 해석된 구분 / the parsed source
     */
    // req: FR-RPTS-002
    public static SendSource parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return ALL;
        }
        for (SendSource candidate : values()) {
            if (candidate.name().equalsIgnoreCase(raw.trim())) {
                return candidate;
            }
        }
        return ALL;
    }

    /**
     * 이 구분이 API 집계를 읽어야 하는지 반환한다. / Whether the API aggregate must be read.
     *
     * @return 필요 여부 / true when the API source is in scope
     */
    // req: FR-RPTS-003
    public boolean readsApi() {
        return this == API || this == ALL;
    }

    /**
     * 이 구분이 대량 집계를 읽어야 하는지 반환한다. / Whether the bulk aggregate must be read.
     *
     * @return 필요 여부 / true when the bulk source is in scope
     */
    // req: FR-RPTS-003
    public boolean readsBulk() {
        return this == BULK || this == ALL;
    }
}
