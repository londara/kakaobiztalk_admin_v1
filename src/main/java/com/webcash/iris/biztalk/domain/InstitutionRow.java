package com.webcash.iris.biztalk.domain;

/**
 * 이용기관 목록 1행 — 클라이언트로 나가는 형태. / One 이용기관 list row, as sent to the client.
 *
 * <p>레거시 그리드의 8컬럼에 대응한다: 기관코드·기관명·영문명·사용여부·인증키·등록일자·
 * 수정일자·설명.</p>
 * <p>Corresponds to the legacy grid's eight columns.</p>
 *
 * <h2>왜 별도 타입인가 / Why this is a separate type</h2>
 * <p>매퍼가 반환하는 {@code InstitutionEntity} 는 <b>평문 인증키</b>를 담는다. 그 타입을
 * 그대로 직렬화하면 결함 D-I5 가 그대로 재현된다 — 레거시는 전 기관의 인증키를 목록
 * 응답에 실어 보냈다. 마스킹된 값만 갖는 별도 타입을 두면 <b>평문이 직렬화 경로에
 * 존재하지 않으므로</b> 실수로 노출될 수 없다.</p>
 * <p>The mapper's {@code InstitutionEntity} carries the <b>plaintext</b> 인증키. Serialising
 * that type directly would reproduce defect D-I5, where the legacy shipped every institution's
 * key in the list response. A separate type holding only the masked value means the plaintext
 * <b>is not present on the serialisation path</b> and cannot leak by accident.</p>
 *
 * @param code           기관코드 / institution code
 * @param name           기관명 / institution name
 * @param englishName    영문명 / english name
 * @param businessNumber 사업자등록번호 / business registration number
 * @param authKeyMasked  마스킹된 인증키 / the masked 인증키
 * @param status         사용여부 원본값 / raw status value
 * @param statusLabel    사용여부 표시 라벨 / display label for the status
 * @param description    설명 / description
 * @param registeredAt   등록일시 / registered timestamp
 * @param lastModifiedAt 수정일시 / last modified timestamp
 *
 * // source: biztalk_admin_00.js — drawGrid() gridColName 8 columns
 * // req: FR-INST-002, FR-INST-006, FR-ATK-002
 */
public record InstitutionRow(
        String code,
        String name,
        String englishName,
        String businessNumber,
        String authKeyMasked,
        String status,
        String statusLabel,
        String description,
        String registeredAt,
        String lastModifiedAt
) {
}
