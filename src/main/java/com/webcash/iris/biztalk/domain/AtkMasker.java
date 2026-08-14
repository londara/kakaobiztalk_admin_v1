package com.webcash.iris.biztalk.domain;

/**
 * 인증키(ATK) 마스킹. / 인증키 (ATK) masking.
 *
 * <p>{@code ATK} 는 고객사가 발송 API 를 호출할 때 제시하는 <b>운영 자격증명</b>이다.
 * 레거시 이용기관 목록 화면은 이 값을 전 기관에 대해 <b>평문 컬럼으로 그대로
 * 노출</b>했다(결함 D-I5) — 화면 캡처 한 장이면 모든 고객사의 API 키가 함께 나간다.</p>
 * <p>{@code ATK} is the <b>live credential</b> a client company presents when calling the
 * send API. The legacy list screen rendered it <b>unmasked, for every institution</b>
 * (defect D-I5): one screenshot exposes every customer's key at once.</p>
 *
 * <p>뒤 4자리만 남기는 이유는 운영 지원 때문이다. 고객사가 "우리 키가 안 먹는다" 고
 * 말할 때 운영자가 어느 키를 말하는지 대조할 수 있어야 하며, 전체 마스킹은 그때마다
 * 전체 조회(FR-ATK-003)를 강제해 오히려 노출을 늘린다(ADR-INST-015 §3.2).</p>
 * <p>Last four characters remain visible for support: when a customer reports "our key
 * isn't working", an operator needs to confirm which key they mean. Full masking would
 * force a reveal (FR-ATK-003) on every such call, increasing exposure rather than
 * reducing it (ADR-INST-015 §3.2).</p>
 *
 * // source: biztalk_admin_00.js — drawGrid() colDef { key:"ATK", width:200 }
 * // req: FR-ATK-002, NFR-SEC-CRED-I01, TM-I003
 */
public final class AtkMasker {

    /** 노출되는 뒤 자릿수. / Number of trailing characters left visible. */
    private static final int VISIBLE_SUFFIX = 4;

    /** 마스킹 문자. / The masking character. */
    private static final char MASK = '*';

    private AtkMasker() {
    }

    /**
     * 인증키를 마스킹한다. / Masks an 인증키.
     *
     * <p>길이가 노출 자릿수 이하인 값은 <b>전부</b> 마스킹한다. 짧은 키에서 뒤 4자리를
     * 남기면 값 전체가 드러나기 때문이다.</p>
     * <p>A value no longer than the visible suffix is masked <b>entirely</b>: keeping four
     * trailing characters of a short key would disclose the whole thing.</p>
     *
     * @param atk 원본 인증키, {@code null} 허용 / the raw 인증키, {@code null} permitted
     * @return 마스킹된 값. 입력이 {@code null} 이면 {@code null}
     *         / the masked value, or {@code null} when the input is {@code null}
     */
    // req: FR-ATK-002
    public static String mask(String atk) {
        if (atk == null) {
            return null;
        }
        if (atk.isEmpty()) {
            return atk;
        }
        if (atk.length() <= VISIBLE_SUFFIX) {
            return String.valueOf(MASK).repeat(atk.length());
        }
        int hidden = atk.length() - VISIBLE_SUFFIX;
        return String.valueOf(MASK).repeat(hidden) + atk.substring(hidden);
    }
}
