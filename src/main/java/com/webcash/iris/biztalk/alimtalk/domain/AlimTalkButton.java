package com.webcash.iris.biztalk.alimtalk.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 알림톡 버튼 — 계약 {@code rule_Sub_1 id="button"} 대응. / An AlimTalk button, per contract sub-rule.
 *
 * <p>레거시는 버튼명이 빈 버튼을 {@code .filter(button => button.name)} 으로 <b>조용히
 * 버렸다</b>(D-A9). 운영자는 화면에 설정된 버튼을 보고 있었지만 payload 에는 없었다. 여기서는
 * 이름이 필수이며, 불완전한 버튼은 검증에서 거절되어 운영자에게 보고된다.</p>
 * <p>The legacy dropped buttons with a blank name via {@code .filter(button => button.name)}
 * <b>silently</b> (D-A9) — the operator saw a configured button in the form and none in the
 * payload. Here the name is mandatory and an incomplete button is rejected and reported.</p>
 *
 * // source: IMO.ADV_KKO_AT_SEND.xml rule_Sub_1; biztalk_admin_61.js — button assembly
 * // req: FR-ATC-002, FR-ATC-005, FR-ATC-009
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AlimTalkButton(

        /** 버튼명 / button label. 계약 28, 유효 14 / contract 28, effective 14. */
        @JsonProperty("name") String name,

        /** 버튼 유형 / button type. */
        @JsonProperty("type") ButtonType type,

        /** PC 웹 URL / PC web URL — {@code WL} 전용 / {@code WL} only. */
        @JsonProperty("url_pc") String urlPc,

        /** 모바일 웹 URL / mobile web URL — {@code WL} 전용 / {@code WL} only. */
        @JsonProperty("url_mobile") String urlMobile,

        /** Android 스킴 / Android scheme — {@code AL} 전용 / {@code AL} only. */
        @JsonProperty("scheme_android") String schemeAndroid,

        /** iOS 스킴 / iOS scheme — {@code AL} 전용 / {@code AL} only. */
        @JsonProperty("scheme_ios") String schemeIos) {

    /**
     * 버튼 유형 코드. / Button type codes.
     *
     * <p>계약이 {@code length="2"} 를 선언하므로 두 글자 코드로 고정된다.</p>
     * <p>The contract declares {@code length="2"}, fixing these at two characters.</p>
     *
     * // source: biztalk_admin_61.js — button-type select options
     * // req: FR-ATC-002
     */
    public enum ButtonType {
        /** 웹링크 / web link. */
        WL,
        /** 앱링크 / app link. */
        AL,
        /** 배송조회 / delivery tracking. */
        DS,
        /** 봇키워드 / bot keyword. */
        BK,
        /** 메시지전달 / message forward. */
        MD;

        /**
         * 계약에 실리는 코드. / The code carried on the wire.
         *
         * @return 두 글자 코드 / the two-character code
         */
        @JsonValue
        public String code() {
            return name();
        }
    }

    /**
     * 유형에 필요한 항목이 모두 채워졌는지 판정한다. / Reports whether the type's required fields are present.
     *
     * <p>{@code WL} 은 모바일 URL 이, {@code AL} 은 스킴 하나 이상이 필요하다. {@code DS}·
     * {@code BK}·{@code MD} 는 추가 항목이 없다. 레거시는 이 구분을 하지 않았고, 그래서
     * URL 없는 웹링크 버튼이 그대로 전송될 수 있었다.</p>
     * <p>{@code WL} needs a mobile URL, {@code AL} needs at least one scheme, and {@code DS},
     * {@code BK}, {@code MD} need nothing further. The legacy drew no such distinction, so a web-link
     * button with no URL could be despatched as-is.</p>
     *
     * @return 완전하면 {@code true} / {@code true} when complete
     *
     * // req: FR-ATC-009
     */
    // 직렬화 제외 필수 / must not serialise. 이 애노테이션이 없으면 Jackson 이 이 메서드를
    // "complete" 프로퍼티로 내보내고, 그것은 계약에 없는 필드 — 즉 D-A2 를 우리 손으로 재현하는
    // 것이 된다. ContractConformanceTest 가 작성 직후 실제로 이 결함을 잡아냈다.
    // Without this, Jackson emits this method as a "complete" property — a field the contract does
    // not declare, which is D-A2 reproduced by our own hand. ContractConformanceTest caught exactly
    // this defect within minutes of being written.
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isComplete() {
        if (name == null || name.isBlank() || type == null) {
            return false;
        }
        return switch (type) {
            case WL -> urlMobile != null && !urlMobile.isBlank();
            case AL -> (schemeIos != null && !schemeIos.isBlank())
                    || (schemeAndroid != null && !schemeAndroid.isBlank());
            case DS, BK, MD -> true;
        };
    }
}
