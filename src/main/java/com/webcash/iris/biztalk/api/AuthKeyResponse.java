package com.webcash.iris.biztalk.api;

/**
 * 재발급된 인증키 응답 — <b>일회성 공개</b>. / A rotated 인증키, disclosed <b>once</b>.
 *
 * <p>이 레코드는 이 프로그램에서 <b>평문 자격증명을 담는 유일한 응답 타입</b>이다. 그 사실이
 * 별도의 타입으로 존재하는 이유다 — 목록 응답이나 상세 응답에 인증키가 실리는 일은
 * {@code InstitutionRow} 가 마스킹된 값만 갖기 때문에 구조적으로 불가능하고, 평문이 필요한
 * 한 곳은 이렇게 이름으로 드러나 있다. 어떤 응답이 자격증명을 노출하는지 찾을 때 이 타입의
 * 사용처를 보면 된다.</p>
 * <p>This is the <b>only response type in the programme that carries a plaintext credential</b>,
 * which is why it exists as a type of its own: a key cannot reach a list or detail response because
 * {@code InstitutionRow} holds only the masked value, and the one place plaintext is required is
 * named. To find which responses disclose a credential, look at this type's usages.</p>
 *
 * <h2>왜 한 번만 보여주는가 / why it is shown once</h2>
 * <p>운영자는 새 키를 고객사에 전달해야 하므로 <b>한 번은</b> 봐야 한다. 그러나 저장된 키를
 * 다시 보는 것은 별도로 인가되고 개별 감사되는 조작이며(FR-ATK-003), 이 응답은 그 경로가
 * 아니다. 서버는 이 값을 로그에도 남기지 않는다(FR-ATK-004).</p>
 * <p>The operator must see it <b>once</b> to pass it to the customer. Re-reading a stored key is a
 * separately authorized, individually audited operation (FR-ATK-003) and this is not that path. The
 * server does not log the value either (FR-ATK-004).</p>
 *
 * <p>운영상의 잔여 위험이 하나 있고 감춰 두지 않는다: 운영자가 전달 전에 이 값을 잃으면 —
 * reveal 이 아직 없는 I2a 에서는 — 복구 방법이 <b>또 한 번의 재발급</b>뿐이다(RISK-I15).</p>
 * <p>One operational residual is not hidden: if the operator loses the value before distributing
 * it, the only recovery while reveal is unbuilt (Sprint I2a) is <b>another rotation</b>
 * (RISK-I15).</p>
 *
 * @param authKey 새로 발급된 인증키 / the newly issued 인증키
 *
 * // source: biztalk_admin_01.js — btn_generate_code: $("#ATK").val(randomGenerator(20))
 * // req: FR-ATK-001, FR-ATK-004, FR-ATK-005, FR-INSTC-011
 */
public record AuthKeyResponse(String authKey) {
}
