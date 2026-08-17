package com.webcash.iris.biztalk.domain;

/**
 * 발신번호 조회 조건. / Sender-number search criteria.
 *
 * <p>레거시 화면 10 은 이용기관 하나를 고른 뒤 그 기관의 발신번호를 나열했다. 조회 조건은
 * 사실상 {@code IS_CD} 하나뿐이다.</p>
 * <p>Legacy screen 10 selected one institution and listed its sender numbers. The only real
 * criterion is {@code IS_CD}.</p>
 *
 * <h2>페이징 — D-S14 / paging — D-S14</h2>
 * <p>레거시는 페이징을 <b>세 계층에서 각각 다르게</b> 다루었다. 클라이언트
 * ({@code biztalk_admin_10.js}) 는 {@code PAGE_NO} 와 {@code INQ_TOTL_NCNT} 를 전송했고,
 * 서비스 계약({@code WSVC.biztalk_admin_10_l001}) 은 둘 다 선언하지 않았으며, 쿼리
 * ({@code IDO.KKB_DPNO_LDGR_L002}) 에는 {@code LIMIT} 도 {@code OFFSET} 도 없었다. 게다가
 * JSP 는 페이징 위젯을 {@code display:none} 으로 숨겨 두었다. 결과적으로 전체 목록이 매번
 * 전송되어 브라우저에서 잘렸다.</p>
 * <p>The legacy handled paging <b>differently at three layers</b>: the client sent
 * {@code PAGE_NO}/{@code INQ_TOTL_NCNT}, the contract declared neither, and the query had no
 * {@code LIMIT} or {@code OFFSET} — while the JSP hid the paging widget with
 * {@code display:none}. The whole list was shipped every time and truncated in the browser.</p>
 *
 * <h2>정렬 — D-S14 / ordering — D-S14</h2>
 * <p>레거시 쿼리에는 {@code ORDER BY} 가 <b>없었다.</b> 페이징이 없을 때는 눈에 띄지 않지만,
 * 페이징을 복원하면 정렬 없는 결과는 즉시 문제가 된다 — PostgreSQL 은 순서를 보장하지 않으므로
 * 같은 조건의 2페이지를 두 번 요청하면 다른 행이 나올 수 있고, 어떤 행은 모든 페이지에서
 * 누락될 수 있다. 정렬은 페이징의 부가 기능이 아니라 <b>전제</b>다.</p>
 * <p>The legacy query had <b>no {@code ORDER BY}.</b> Invisible without paging, but restoring
 * paging makes it immediate: PostgreSQL guarantees no order, so requesting page 2 twice can
 * return different rows and some rows can be missed by every page. Ordering is a
 * <b>precondition</b> of paging, not an adjunct to it.</p>
 *
 * @param institutionCode 조회 대상 이용기관 코드 / the institution being listed
 * @param page            0부터 시작하는 페이지 번호 / zero-based page index
 * @param size            페이지 크기 / page size
 *
 * // source: biztalk_admin_10.js — getDat(); IDO.KKB_DPNO_LDGR_L002
 * // req: FR-SND-003, FR-SND-004, NFR-PERF-D01
 */
public record SenderNumberCriteria(String institutionCode, int page, int size) {

    /** 기본 페이지 크기. / Default page size. */
    public static final int DEFAULT_SIZE = 20;

    /** 최대 페이지 크기 — TM-D1(무제한 조회) 대응. / Maximum page size, mitigating T-D1. */
    public static final int MAX_SIZE = 200;

    /**
     * 조건을 정규화하여 생성한다. / Creates a normalised criteria.
     *
     * <p>이용기관 코드는 <b>정규화하지 않고 그대로 보존한다.</b> 이 값은 호출자가 보낸 값이며,
     * 신뢰 여부는 이 타입이 판단할 문제가 아니다 — {@code TenantContext} 가 세션 권한과 대조한
     * 결과만이 조회에 쓰인다(FR-AZ-D03). 여기서 조용히 손질하면 그 대조가 무엇을 검사하는지
     * 흐려진다.</p>
     * <p>The institution code is <b>preserved verbatim.</b> It is caller-supplied, and whether to
     * trust it is not this type's decision — only the value {@code TenantContext} has reconciled
     * against the session's rights reaches the query (FR-AZ-D03). Quietly adjusting it here would
     * blur what that reconciliation checks.</p>
     *
     * @param institutionCode 이용기관 코드 / the institution code
     * @param page            페이지 번호 / the page index
     * @param size            페이지 크기 / the page size
     * @return 정규화된 조건 / the normalised criteria
     */
    // req: FR-SND-003, NFR-PERF-D01
    public static SenderNumberCriteria of(String institutionCode, Integer page, Integer size) {
        int normalisedPage = page == null || page < 0 ? 0 : page;
        int normalisedSize = size == null || size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return new SenderNumberCriteria(institutionCode, normalisedPage, normalisedSize);
    }

    /**
     * 조회 시작 위치를 반환한다. / Returns the row offset.
     *
     * <p>{@code long} 으로 반환한다. {@code page * size} 는 두 값이 모두 {@code int} 일 때
     * 오버플로가 가능하며, 오버플로된 음수 오프셋은 예외가 아니라 <b>조용히 잘못된 페이지</b>를
     * 낳는다.</p>
     * <p>Returned as {@code long}: {@code page * size} can overflow when both are {@code int},
     * and a wrapped negative offset yields a <b>silently wrong page</b> rather than an error.</p>
     *
     * @return 건너뛸 행 수 / the number of rows to skip
     */
    // req: FR-SND-003
    public long offset() {
        return (long) page * (long) size;
    }

    /**
     * 이용기관이 지정되었는지 반환한다. / Whether an institution has been chosen.
     *
     * <p>지정되지 않았으면 조회를 <b>실행하지 않는다</b>(FR-SND-002). 레거시는 {@code onload}
     * 에서 {@code getDat()} 를 먼저 부르고 그 뒤에 {@code fn_getIsList()} 로 콤보를 채웠기
     * 때문에, 매 페이지 로드마다 빈 {@code IS_CD} 로 한 번씩 조회했다(D-S19).</p>
     * <p>When absent the query is <b>not run</b> (FR-SND-002). The legacy called {@code getDat()}
     * in {@code onload} before {@code fn_getIsList()} populated the combo, so every page load
     * issued one query with a blank {@code IS_CD} (D-S19).</p>
     *
     * @return 지정되었으면 true / true when an institution is set
     */
    // source: biztalk_admin_10.js — onload(): getDat() before fn_getIsList()
    // req: FR-SND-002
    public boolean hasInstitution() {
        return institutionCode != null && !institutionCode.isBlank();
    }
}
