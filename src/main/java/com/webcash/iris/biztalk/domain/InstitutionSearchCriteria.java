package com.webcash.iris.biztalk.domain;

/**
 * 이용기관 조회 조건. / 이용기관 search criteria.
 *
 * <p>레거시 화면 00 의 검색 폼(기관명 + 상태 라디오)에 대응한다. 레거시가 함께 전송하던
 * {@code PAGE_NO}·{@code INQ_TOTL_NCNT} 는 서비스 계약에 선언만 되어 있고 SQL 에는
 * 구현이 없었다(결함 D-I10) — 여기서는 실제 페이징 파라미터가 된다.</p>
 * <p>Corresponds to screen 00's search form (name plus a status radio). The legacy also sent
 * {@code PAGE_NO} and {@code INQ_TOTL_NCNT}, which were declared in the service contract but
 * never implemented in SQL (defect D-I10); here they are real paging parameters.</p>
 *
 * <h2>LIKE 이스케이프 / LIKE escaping</h2>
 * <p>레거시는 {@code ISNM LIKE '%' || :IS_NM || '%'} 로 입력을 그대로 이어붙였다. 사용자가
 * {@code %} 를 입력하면 와일드카드로 동작해 전체가 조회된다. 여기서는 {@code %}·{@code _}·
 * {@code \} 를 이스케이프해 <b>리터럴</b>로 취급한다(FR-INST-005).</p>
 * <p>The legacy concatenated input straight into {@code ISNM LIKE '%' || :IS_NM || '%'}, so a
 * typed {@code %} acted as a wildcard and matched everything. Here {@code %}, {@code _} and
 * {@code \} are escaped and treated as <b>literals</b> (FR-INST-005).</p>
 *
 * // source: biztalk_admin_00_view.jsp (검색 폼), IDO.KKB_FT_FTIS_INFO_L001
 * // req: FR-INST-001, FR-INST-003, FR-INST-005, NFR-PERF-I02
 */
public record InstitutionSearchCriteria(String name, String status, int page, int size) {

    /** 상태 필터 '전체'. / The 전체 status filter. */
    public static final String STATUS_ALL = "ALL";

    /** 기본 페이지 크기. / Default page size. */
    public static final int DEFAULT_SIZE = 20;

    /** 최대 페이지 크기. / Maximum page size. */
    public static final int MAX_SIZE = 200;

    /** 검색어 최대 길이 — 서비스 계약의 {@code IS_NM} 선언과 동일. / Max search length, per contract. */
    public static final int MAX_NAME_LENGTH = 100;

    /**
     * 조건을 정규화하여 생성한다. / Creates a normalised criteria.
     *
     * <p>페이지 크기를 {@link #MAX_SIZE} 로 <b>제한</b>한다. 레거시에는 상한이 없었고
     * {@code LIMIT} 자체가 없어 전체 레지스트리가 매 요청마다 직렬화되었다(D-I10, TM-I011).</p>
     * <p>The page size is <b>clamped</b> to {@link #MAX_SIZE}. The legacy had no cap and no
     * {@code LIMIT} at all, so the whole registry was serialised on every request (D-I10,
     * TM-I011).</p>
     *
     * @param name   기관명 검색어, 비어 있으면 필터 없음 / name fragment; no filter when blank
     * @param status 상태 필터 {@code ALL}/{@code Y}/{@code N} / status filter
     * @param page   0부터 시작하는 페이지 번호 / zero-based page index
     * @param size   페이지 크기 / page size
     * @return 정규화된 조건 / the normalised criteria
     */
    // req: FR-INST-001, NFR-PERF-I02
    public static InstitutionSearchCriteria of(String name, String status, Integer page, Integer size) {
        String trimmedName = name == null || name.isBlank() ? null : name.trim();
        if (trimmedName != null && trimmedName.length() > MAX_NAME_LENGTH) {
            trimmedName = trimmedName.substring(0, MAX_NAME_LENGTH);
        }

        String normalisedStatus = status == null || status.isBlank() || STATUS_ALL.equals(status)
                ? null
                : status;

        int normalisedPage = page == null || page < 0 ? 0 : page;
        int normalisedSize = size == null || size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);

        return new InstitutionSearchCriteria(trimmedName, normalisedStatus, normalisedPage, normalisedSize);
    }

    /**
     * SQL {@code OFFSET} 값을 반환한다. / Returns the SQL {@code OFFSET}.
     *
     * @return 건너뛸 행 수 / rows to skip
     */
    // req: FR-INST-003
    public int offset() {
        return page * size;
    }

    /**
     * {@code LIKE} 에 바인딩할 패턴을 반환한다. / Returns the pattern to bind into {@code LIKE}.
     *
     * <p>이스케이프 문자는 백슬래시이며 SQL 쪽에서 {@code ESCAPE '\'} 로 선언한다.
     * 백슬래시를 먼저 치환해야 한다 — 나중에 하면 {@code %} 치환이 만든 백슬래시까지
     * 다시 이스케이프해 버린다.</p>
     * <p>The escape character is a backslash, declared as {@code ESCAPE '\'} in the SQL. The
     * backslash must be replaced first: doing it later would re-escape the backslashes
     * introduced by the {@code %} replacement.</p>
     *
     * @return {@code LIKE} 패턴, 검색어가 없으면 {@code null} / the pattern, or {@code null}
     */
    // req: FR-INST-005
    public String namePattern() {
        if (name == null) {
            return null;
        }
        String escaped = name
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
