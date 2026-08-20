package com.webcash.iris.biztalk.domain;

/**
 * 이용기관 필드 한계값 — 한 곳에서만 정의한다. / 이용기관 field limits, defined once.
 *
 * <h2>왜 이 클래스가 있는가 / why this class exists</h2>
 * <p>레거시에서 같은 필드의 한계값이 <b>세 곳에 서로 다르게</b> 있었다. 기관코드를 예로
 * 들면 화면은 {@code maxlength="6"} 에 {@code K0} 접두어를 요구했고, 서비스 계약
 * {@code WSVC.biztalk_admin_01_c001} 은 {@code length="16"} 이라고 선언했으며, 검증은
 * 브라우저에만 있었다(D-I19). 셋 중 어느 것이 진짜인지 코드로는 알 수 없었다.</p>
 * <p>In the legacy the same field had <b>three different limits in three places</b>: the form
 * demanded 6 characters with a {@code K0} prefix, the service contract declared
 * {@code length="16"}, and the enforcement lived only in the browser (D-I19). Nothing in the code
 * said which was authoritative.</p>
 *
 * <p>FR-INSTC-014 는 그 다툼을 <b>가장 좁은 값이 이긴다</b>로 정리했다(AMB-I06). 이 클래스는
 * 그 결정이 사는 유일한 장소다 — 요청 레코드의 Bean Validation 애노테이션과 서비스 계층의
 * 검증이 <b>같은 상수</b>를 참조하므로, 한쪽만 느슨해질 수 없다. 애노테이션은 상수만
 * 참조할 수 있으므로 값은 컴파일 시간 상수여야 한다.</p>
 * <p>FR-INSTC-014 settled it: <b>the narrowest source governs</b> (AMB-I06). This class is the
 * only place that decision lives — the request record's Bean Validation annotations and the
 * service-layer checks reference the <b>same constants</b>, so one cannot drift looser than the
 * other. Annotations accept constants only, which is why these are compile-time values.</p>
 *
 * // source: biztalk_admin_01_view.jsp (maxlength), WSVC.biztalk_admin_01_c001.xml (length),
 * //         biztalk_admin_01.js (fn_save, fn_iscdDupCheck)
 * // req: FR-INSTC-003, FR-INSTC-009, FR-INSTC-014, FR-INSTC-015, AMB-I06
 */
public final class InstitutionLimits {

    /**
     * 기관코드 형식 — {@code K0} + 영숫자 4자, 정확히 6자.
     * 기관코드 format: {@code K0} plus 4 alphanumerics, exactly 6 characters.
     *
     * <p>레거시 검증은 {@code val().length != 6} 과 {@code substring(0,2) != "K0"} 두 줄이었고
     * 둘 다 브라우저에만 있었다. 계약의 {@code length="16"} 은 전송 상한이며 도메인 형식이
     * 아니다(FR-INSTC-014).</p>
     * <p>The legacy check was two lines in the browser. The contract's {@code length="16"} is a
     * transport maximum, not the domain format (FR-INSTC-014).</p>
     */
    // source: biztalk_admin_01.js — fn_iscdDupCheck: length != 6, substring(0,2) != "K0"
    public static final String CODE_REGEX = "K0[A-Za-z0-9]{4}";

    /** 기관명 최대 길이. / Maximum 기관명 length. */
    // source: WSVC.biztalk_admin_01_c001.xml — item id="IS_NM" length="100"
    public static final int NAME_MAX = 100;

    /** 영문명 최대 길이. / Maximum 영문명 length. */
    // source: WSVC.biztalk_admin_01_c001.xml — item id="IS_ENGNM" length="100"
    public static final int ENGLISH_NAME_MAX = 100;

    /**
     * 사업자등록번호 형식 — 숫자 10자리.
     * 사업자등록번호 format: exactly 10 digits.
     *
     * <p>길이는 <b>가정</b>이었다가 PM 결정(AMB-I06)으로 확정되었다. 레거시 화면은
     * {@code oninput} 으로 숫자만 남겼을 뿐 길이는 보지 않았고 {@code maxlength="40"} 이었다.</p>
     * <p>The length was an assumption until PM ruling AMB-I06. The legacy screen stripped
     * non-digits with {@code oninput} but never checked the length, and allowed 40 characters.</p>
     */
    // source: biztalk_admin_01_view.jsp — BRNO oninput replace(/[^0-9.]/g,'')
    // req: FR-INSTC-009, FR-INSTC-016
    public static final String BUSINESS_NUMBER_REGEX = "[0-9]{10}";

    /** 설명 최대 길이. / Maximum 설명 length. */
    // source: biztalk_admin_01_view.jsp — textarea id="CMOP" maxlength="650"
    public static final int DESCRIPTION_MAX = 650;

    private InstitutionLimits() {
    }
}
