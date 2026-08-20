package com.webcash.iris.biztalk.api;

import com.webcash.iris.biztalk.domain.InstitutionEdit;
import com.webcash.iris.biztalk.domain.InstitutionLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 이용기관 수정 요청 본문. / The 이용기관 update request body.
 *
 * <h2>이 레코드에 <b>없는</b> 필드가 계약이다 / the contract is what this record omits</h2>
 * <table border="1">
 *   <caption>의도적으로 받지 않는 값 / values deliberately not accepted</caption>
 *   <tr><th>없는 필드</th><th>이유</th></tr>
 *   <tr><td>{@code code}</td>
 *       <td>수정 대상은 경로가 정한다. 본문에도 코드가 있으면 "어느 기관을 고치는가" 에 두
 *           개의 답이 생기고, 그중 하나는 신뢰할 수 없다(FR-INSTC-002)</td></tr>
 *   <tr><td>{@code authKey}</td>
 *       <td>화면은 인증키를 <b>마스킹된 상태</b>로 갖는다(FR-INSTC-010). 이 필드가 있으면
 *           별표가 그대로 자격증명이 되어 고객사 연동이 즉시 끊긴다. 필드를 두지 않는 것이
 *           그 사고를 <b>표현 불가능</b>하게 만드는 방법이다(TM-I022)</td></tr>
 *   <tr><td>{@code lastModifiedBy}, {@code lastModifiedAt}</td>
 *       <td>행위자는 세션에서(FR-INSTC-007), 시각은 데이터베이스 시계에서(ADR-INST-017)
 *           온다. 레거시는 {@code putAll(input)} 이었으므로 본문이 이 값들을 덮어쓸 수
 *           있었다</td></tr>
 * </table>
 *
 * <p>The path names the target, so no {@code code} is accepted; the screen holds the key masked so
 * no {@code authKey} is accepted — the field's absence makes writing a mask as the credential
 * unrepresentable; and actor and timestamp come from the session and the database, not the body,
 * which the legacy's {@code putAll(input)} allowed.</p>
 *
 * <h2>왜 검증이 두 곳에 있는가 / why validation exists twice</h2>
 * <p>여기의 Bean Validation 은 <b>빠른 거절</b>을 위한 것이고, 진짜 방어선은
 * {@code InstitutionWriteService} 안에 있다. 둘은 {@link InstitutionLimits} 의 <b>같은
 * 상수</b>를 참조하므로 규칙이 두 벌이 아니라 진입점이 두 개다 — HTTP 를 거치지 않는
 * 호출자에게도 같은 규칙이 적용된다(FR-INSTC-003). 레거시는 이 규칙 전부를 브라우저에만
 * 두었다(D-I19).</p>
 * <p>The Bean Validation here is for a <b>fast rejection</b>; the real barrier is inside
 * {@code InstitutionWriteService}. Both reference the <b>same constants</b> in
 * {@link InstitutionLimits}, so there are two entry points rather than two rule sets, and a caller
 * that bypasses HTTP meets the same rules (FR-INSTC-003). The legacy kept every one of them in the
 * browser (D-I19).</p>
 *
 * @param name           기관명 / institution name
 * @param englishName    영문명 / english name
 * @param businessNumber 사업자등록번호 — 숫자 10자리 / business registration number, 10 digits
 * @param status         사용여부 {@code Y}/{@code N} / status
 * @param description    설명 / description
 *
 * // source: biztalk_admin_01.js — fn_save(): IS_NM, IS_ENGNM, BRNO, USE_YN, CMOP
 * // req: FR-INSTC-002, FR-INSTC-003, FR-INSTC-009, FR-INSTC-011, FR-INSTC-014, FR-INSTC-015
 */
public record InstitutionUpdateRequest(

        @NotBlank(message = "이용기관명은 필수입니다.")
        @Size(max = InstitutionLimits.NAME_MAX, message = "이용기관명이 너무 깁니다.")
        String name,

        @NotBlank(message = "이용기관영문명은 필수입니다.")
        @Size(max = InstitutionLimits.ENGLISH_NAME_MAX, message = "이용기관영문명이 너무 깁니다.")
        String englishName,

        @NotBlank(message = "사업자등록번호는 필수입니다.")
        @Pattern(regexp = InstitutionLimits.BUSINESS_NUMBER_REGEX,
                message = "사업자등록번호는 숫자 10자리여야 합니다.")
        String businessNumber,

        /*
          'D' 를 받지 않는다. 논리 삭제는 자기 자신의 조작이며(FR-INSTL-004), 수정 폼으로
          그 상태에 닿을 수 있으면 확인도 감사 기록도 없는 삭제가 된다(FR-INSTC-015).
          'D' is not accepted: logical delete is its own operation (FR-INSTL-004), and reaching
          that state through the edit form would be a delete with no confirmation and no deletion
          audit entry (FR-INSTC-015).
        */
        @Pattern(regexp = "[YN]", message = "사용 여부는 사용 또는 미사용이어야 합니다.")
        String status,

        @Size(max = InstitutionLimits.DESCRIPTION_MAX, message = "설명이 너무 깁니다.")
        String description
) {

    /**
     * 도메인 명령으로 변환한다. / Converts to the domain command.
     *
     * <p>도메인이 API 레코드를 알지 못하게 하기 위한 경계다. 이 방향으로만 의존하므로
     * 요청 형식이 바뀌어도 도메인 규칙은 그대로 남는다.</p>
     * <p>The boundary that keeps the domain unaware of the API record. The dependency runs only
     * this way, so a change of request shape leaves the domain rules untouched.</p>
     *
     * @return 도메인 명령 / the domain command
     */
    // req: FR-INSTC-002, FR-INSTC-011
    public InstitutionEdit toEdit() {
        return new InstitutionEdit(name, englishName, businessNumber, status, description);
    }
}
