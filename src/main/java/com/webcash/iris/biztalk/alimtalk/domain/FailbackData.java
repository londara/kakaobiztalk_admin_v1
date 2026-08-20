package com.webcash.iris.biztalk.alimtalk.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 알림톡 실패 시 대체 전송 — 계약 필드명은 {@code failback_data}. / AlimTalk fallback, contract key {@code failback_data}.
 *
 * <h2>이 클래스 이름이 중요한 이유 / why this class's name matters</h2>
 * <p>레거시 화면 61 은 이 블록을 {@code failback} 으로 내보냈다. 계약
 * {@code IMO.ADV_KKO_AT_SEND} 와 {@code _M} 은 둘 다 <b>{@code failback_data}</b> 를 선언한다.
 * 필드명이 어긋나면 인터페이스가 이 블록을 바인딩하지 못하고, <b>대체 전송이 조용히
 * 사라진다</b> — 알림톡이 실패했을 때 SMS/LMS 로 전달하는 장치가 없어지는 것이므로, 증상은
 * "고객이 알림을 받지 못했다"로만 나타난다(D-A1, Critical).</p>
 * <p>Legacy screen 61 emitted this block as {@code failback}. Both contracts declare
 * <b>{@code failback_data}</b>. A mismatched name means the interface cannot bind the block and the
 * <b>fallback disappears silently</b> — the mechanism that delivers the message as SMS/LMS when
 * AlimTalk fails is simply absent, so the only symptom is a notification the customer never got
 * (D-A1, Critical).</p>
 *
 * <p>흥미로운 사실은 이 결함이 1년 넘게 살아남은 이유다. 화면 61 의 출력은 어떤 코드도
 * 소비하지 않았다 — 운영자가 손으로 복사했다. 따라서 <b>눈에 보이게 실패할 경로가 아예
 * 없었다.</b> {@code ContractConformanceTest} 가 이 기능 역사상 처음으로 그 경로를 만든다.</p>
 * <p>What is interesting is why this survived over a year: screen 61's output was consumed by no
 * code at all — an operator copied it by hand. There was <b>no path by which it could fail
 * visibly.</b> {@code ContractConformanceTest} creates that path for the first time.</p>
 *
 * // source: IMO.ADV_KKO_AT_SEND.xml rule_Sub_2 id="failback_data"; biztalk_admin_61.js — data.failback
 * // req: FR-ATC-002, CONST-DATA-A01
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FailbackData(

        /** 대체 전송 유형 / fallback type. */
        @JsonProperty("type") FailbackType type,

        /** 제목 / subject — {@code LMS}·{@code MMS} 전용 / {@code LMS} and {@code MMS} only. 계약 50. */
        @JsonProperty("subject") String subject,

        /** 대체 전송 본문 / fallback body. 유형이 있으면 필수 / mandatory when a type is present. */
        @JsonProperty("msg") String msg,

        /** 이미지 ID / image id — {@code MMS} 전용 / {@code MMS} only. 계약 256. */
        @JsonProperty("img_id") String imgId) {

    /**
     * 대체 전송 유형. / Fallback types.
     *
     * <p>레거시 UI 의 {@code NO}(대체전송 없음)는 이 열거형에 없다 — 대체 전송을 하지 않는다는
     * 것은 {@code failback_data} 블록 자체가 <b>없다</b>는 뜻이며, {@code type="NO"} 를 담아
     * 보내는 것과 다르다.</p>
     * <p>The legacy UI's {@code NO} option is absent here: sending no fallback means the
     * {@code failback_data} block is <b>absent</b>, which is not the same as carrying
     * {@code type="NO"}.</p>
     *
     * // req: FR-ATC-002
     */
    public enum FailbackType {
        /** 단문 / short message. */
        SMS,
        /** 장문 / long message. */
        LMS,
        /** 이미지 첨부 / multimedia message. */
        MMS;

        /**
         * 계약에 실리는 코드. / The code carried on the wire.
         *
         * @return 코드 / the code
         */
        @JsonValue
        public String code() {
            return name();
        }

        /**
         * 제목을 허용하는 유형인지 판정한다. / Reports whether this type permits a subject.
         *
         * @return {@code LMS}·{@code MMS} 이면 {@code true} / {@code true} for {@code LMS} and {@code MMS}
         *
         * // source: biztalk_admin_61.js — failbackType === 'LMS' || failbackType === 'MMS'
         * // req: FR-ATC-002
         */
        public boolean allowsSubject() {
            return this == LMS || this == MMS;
        }

        /**
         * 이미지를 허용하는 유형인지 판정한다. / Reports whether this type permits an image.
         *
         * @return {@code MMS} 이면 {@code true} / {@code true} for {@code MMS}
         *
         * // req: FR-ATC-002
         */
        public boolean allowsImage() {
            return this == MMS;
        }
    }

    /**
     * 유형별 규칙을 만족하는지 판정한다. / Reports whether the type's rules are satisfied.
     *
     * <p>레거시는 대체 전송 항목을 <b>전혀</b> 검증하지 않았다. 유형을 고르고 본문을 비워 두면
     * 빈 값 제거 replacer 가 {@code msg} 를 지워 {@code {type}} 만 남은 객체를 만들었다(D-A17) —
     * 유형은 있으나 보낼 내용이 없는 대체 전송이다.</p>
     * <p>The legacy validated fallback fields <b>not at all</b>. Selecting a type and leaving the
     * body empty produced an object of {@code {type}} alone, because the empty-value replacer
     * stripped {@code msg} (D-A17) — a fallback with a type and nothing to send.</p>
     *
     * @return 규칙을 만족하면 {@code true} / {@code true} when valid
     *
     * // req: FR-ATC-002
     */
    // 직렬화 제외 — {@link AlimTalkButton#isComplete()} 와 같은 이유.
    // Excluded from serialisation for the same reason as {@link AlimTalkButton#isComplete()}.
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isValid() {
        if (type == null || msg == null || msg.isBlank()) {
            return false;
        }
        if (subject != null && !type.allowsSubject()) {
            return false;
        }
        return imgId == null || type.allowsImage();
    }
}
