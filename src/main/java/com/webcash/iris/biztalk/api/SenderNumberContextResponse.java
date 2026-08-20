package com.webcash.iris.biztalk.api;

/**
 * 등록 화면이 여는 이용기관 문맥 — 코드와 이름만. / The institution context the register form opens with.
 *
 * <p><b>필드가 두 개인 것이 이 타입의 전부다.</b> 레거시 등록 팝업은 이용기관 이름을 채우려고
 * {@code biztalk_admin_01_l002}(이용기관 상세조회)를 호출했고, 그 서비스는 기관 레코드 전체를
 * — <b>평문 인증키를 포함해</b> — 반환했다(D-S18, 이용기관 슬라이스의 D-I3). 이름 하나가 필요한
 * 화면이 살아 있는 자격증명을 브라우저 DOM 으로 끌어온 것이다.</p>
 * <p><b>Having two fields is the whole point of this type.</b> The legacy register popup called the
 * institution <em>detail</em> service to fill in a name, and that service returned the full record —
 * <b>including the plaintext 인증키</b> (D-S18; D-I3 in the institution slice). A screen that needed
 * one name pulled a live credential into the browser DOM.</p>
 *
 * <p>넓은 응답에서 필드를 <b>가리는</b> 방식으로는 이 결함이 재발한다 — 다음 사람이 편의를 위해
 * 필드를 하나 더 열면 된다. 좁은 타입을 두면 자격증명이 실릴 자리 자체가 없다(위협 T-I2).</p>
 * <p>Masking fields out of a wide response would let the defect return: the next person only has to
 * expose one more field for convenience. A narrow type leaves the credential nowhere to travel
 * (threat T-I2).</p>
 *
 * @param institution     이용기관 코드 / the institution code
 * @param institutionName 이용기관명 / the institution name
 *
 * // source: biztalk_admin_12.js — loadData() calling biztalk_admin_01_l002 (returns 인증키)
 * // req: FR-SNDC-002, FR-SNDC-012, NFR-SEC-PII-D02
 */
public record SenderNumberContextResponse(String institution, String institutionName) {
}
