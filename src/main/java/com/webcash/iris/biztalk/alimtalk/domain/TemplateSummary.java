package com.webcash.iris.biztalk.alimtalk.domain;

/**
 * 템플릿 선택 목록의 한 항목. / One entry in the template selection list.
 *
 * <p>레거시는 {@code template_code} 를 <b>자유 입력</b>으로 두었다(D-A15). 그런데
 * {@code KKB_MSG_TMPL} 은 정확히 {@code (IS_CD, TEMPLATE_CODE)} 로 키가 잡힌 레지스트리이고,
 * 화면 50 은 이미 그 표를 조회하고 있었다 — 즉 선택 목록을 만들 재료가 있는데도 손으로 적게
 * 했다. 등록되지 않은 코드로 발송하면 벤더가 거절하므로, 자유 입력은 <b>실패를 나중으로
 * 미루는</b> 선택이었다.</p>
 * <p>The legacy left {@code template_code} as <b>free text</b> (D-A15), though {@code KKB_MSG_TMPL} is a
 * registry keyed by exactly {@code (IS_CD, TEMPLATE_CODE)} and screen 50 already queried it — the
 * material for a selection list existed and was not used. Sending an unregistered code is rejected by
 * the vendor, so free text merely <b>deferred the failure</b>.</p>
 *
 * <p>본문({@code TEMPLATE_MSG})은 이 요약에 담지 않는다. 목록 조회에 본문을 실으면 필요하지
 * 않은 데이터를 브라우저로 보내게 되고(NFR-SEC-PII-A03), 본문은 선택된 하나에 대해서만
 * {@link TemplateRegistry} 를 통해 가져온다.</p>
 * <p>The body ({@code TEMPLATE_MSG}) is deliberately absent: shipping it with the list would send data
 * the screen does not need (NFR-SEC-PII-A03). It is fetched for the selected template alone, through
 * {@link TemplateRegistry}.</p>
 *
 * @param templateCode  템플릿코드 / the template code
 * @param templateTitle 강조표기제목의 기본값 / the default emphasis title
 *
 * // source: IDO.KKB_MSG_TMPL_L001 — SELECT TEMPLATE_CODE, TEMPLATE_TITLE
 * // req: FR-ATT-001, FR-ATT-002, NFR-SEC-PII-A03
 */
public record TemplateSummary(String templateCode, String templateTitle) {
}
