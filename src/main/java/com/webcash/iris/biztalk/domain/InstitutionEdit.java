package com.webcash.iris.biztalk.domain;

/**
 * 이용기관 수정 내용 — 검증 전의 입력. / The requested 이용기관 changes, before validation.
 *
 * <p>기관코드가 <b>없다.</b> 수정 대상은 경로가 지정하며, 이 레코드에 코드가 있으면
 * "어느 기관을 고치는가" 에 두 개의 답이 생긴다 — 그중 하나는 요청 본문에서 오므로
 * 신뢰할 수 없다(FR-INSTC-002).</p>
 * <p>There is <b>no 기관코드 here.</b> The path names the target; a code in this record would give
 * two answers to "which institution is being changed", one of them supplied by the request body
 * and therefore untrusted (FR-INSTC-002).</p>
 *
 * <p>인증키도 없다. 재발급은 자기 자신의 조작이며 저장 경로를 지나지 않는다
 * (FR-INSTC-011).</p>
 * <p>No 인증키 either: rotation is its own operation and does not travel the save path
 * (FR-INSTC-011).</p>
 *
 * <p>등록자·최종수정자·시각도 없다. 행위자는 세션에서(FR-INSTC-007), 시각은 데이터베이스
 * 시계에서(ADR-INST-017) 온다. 레거시는 이 값들을 요청에 담을 수 있는 구조였고, 서버는
 * {@code input} 을 그대로 {@code putAll} 했다.</p>
 * <p>No actor and no timestamp: the actor comes from the session (FR-INSTC-007) and the timestamp
 * from the database clock (ADR-INST-017). The legacy accepted them in the request and the server
 * {@code putAll}-ed the input straight through.</p>
 *
 * @param name           기관명 / institution name
 * @param englishName    영문명 / english name
 * @param businessNumber 사업자등록번호 / business registration number
 * @param status         사용여부 {@code Y}/{@code N} / status
 * @param description    설명, {@code null} 허용 / description, nullable
 *
 * // source: biztalk_admin_01.js — fn_save(): IS_NM, IS_ENGNM, BRNO, USE_YN, CMOP
 * // req: FR-INSTC-001, FR-INSTC-002, FR-INSTC-007, FR-INSTC-011
 */
public record InstitutionEdit(
        String name,
        String englishName,
        String businessNumber,
        String status,
        String description
) {
}
