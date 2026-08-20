package com.webcash.iris.biztalk.alimtalk.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 단건 알림톡 전송 요청 — {@code IMO.ADV_KKO_AT_SEND} 대응. / Single AlimTalk send request.
 *
 * <h2>이 타입이 <b>외부</b> DTO 인 이유 / why this is the <b>outbound</b> DTO</h2>
 * <p>API 로 들어오는 요청은 별도 타입({@code AlimTalkComposeRequest})이며, 이 타입과 합치지
 * 않는다. 두 타입을 유지하는 이유는 두 가지다.</p>
 * <p>The inbound API request is a separate type and is deliberately not merged with this one, for
 * two reasons.</p>
 * <ul>
 *   <li><b>자격증명</b> — {@code sender_key} 는 이 타입에만 있다. 합쳐 두면 클라이언트가 채울 수
 *       있는 필드가 되고, FR-AZ-A05 가 요구하는 "선택 불가"가 성립하지 않는다.
 *       <br><b>Credential</b> — {@code sender_key} exists only here. Merged, it would be a field a
 *       client could populate, and FR-AZ-A05's "never selectable" would not hold.</li>
 *   <li><b>계약 변경 격리</b> — 벤더 필드명이 바뀌어도 공개 API 가 깨지지 않는다.
 *       <br><b>Contract isolation</b> — a vendor field rename does not become a breaking API change.</li>
 * </ul>
 *
 * <h2>계약에 없는 필드는 담지 않는다 / no field the contract does not declare</h2>
 * <p>레거시 화면 61 은 {@code msg_type}·{@code kko_header}·{@code highlight}·{@code items}·
 * {@code summary} 다섯 필드를 내보냈으나 계약에는 그중 어느 것도 없다(D-A2, Critical).
 * 아이템리스트형 UI 를 가득 채워도 벤더에 도달하는 것은 아무것도 없었다. 그 필드들은 벤더
 * 명세를 확보한 뒤(AMB-A05, spike A1-01) 계약을 확장해서 추가한다 — 그때까지
 * {@code ContractConformanceTest} 가 추가를 거절한다.</p>
 * <p>Legacy screen 61 emitted five fields the contract does not declare (D-A2, Critical): filling
 * the item-list form completely sent nothing to the vendor. They are added by extending the
 * contract once the vendor specification is obtained (AMB-A05, spike A1-01); until then
 * {@code ContractConformanceTest} refuses them.</p>
 *
 * <h2>직렬화와 자격증명 / serialisation and the credential</h2>
 * <p>{@link ProfileKey} 와 {@link RecipientNumber} 는 Jackson 으로 직렬화될 때 <b>가려진 값</b>을
 * 내놓는다. 따라서 이 레코드를 실수로 JSON 으로 덤프해도 D-A30 은 재현되지 않는다. 실제 전송
 * payload 는 {@code RsmsEnvelope} 가 {@code exposeForVendorCall()} 을 명시적으로 호출해
 * 조립한다 — 원문이 필요한 곳이 <b>한 군데</b>로 모인다.</p>
 * <p>{@link ProfileKey} and {@link RecipientNumber} serialise to their <b>redacted</b> forms, so an
 * accidental JSON dump of this record cannot reproduce D-A30. The real wire payload is assembled by
 * {@code RsmsEnvelope}, which calls {@code exposeForVendorCall()} explicitly — concentrating the
 * places that need the raw value into <b>one</b>.</p>
 *
 * // source: IMO.ADV_KKO_AT_SEND.xml — rule/in; biztalk_admin_61.js — generateBtn data assembly
 * // req: FR-ATC-001, FR-ATC-002, FR-ATC-003, FR-ATC-005, FR-ATC-006, CONST-DATA-A01
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AlimTalkRequest(

        /** 이용기관코드 / institution code. 계약 6 / contract 6. */
        @JsonProperty("is_cd") String isCd,

        /** 거래고유번호 / transaction id. 계약 10 / contract 10. */
        @JsonProperty("tran_id") String tranId,

        /** 콜백송신자번호 / caller ID. 계약 24. 등록된 발신번호여야 한다 / must be a registered number. */
        @JsonProperty("sender_number") String senderNumber,

        /**
         * 수신폰번호 / recipients.
         *
         * <p>레거시에는 이 한 필드에 <b>네 가지 모양</b>이 있었다(D-A10): 화면 61 단건은 배열,
         * 다건은 항목별 스칼라, 화면 50 은 한 분기에서 배열을 문자열화({@code jArray.toString()}),
         * 다른 분기에서 배열 그대로. 계약은 길이 20000 의 단일 필드를 선언한다. 여기서는
         * 배열 하나로 통일한다(AMB-A06 작업 가정 A).</p>
         * <p>The legacy carried <b>four shapes</b> for this one field (D-A10). The contract declares a
         * single field of length 20000. One shape is used here (AMB-A06 working assumption A).</p>
         */
        @JsonProperty("receiver_number") List<RecipientNumber> receiverNumber,

        /** 발송일시 {@code yyyyMMddHHmmss} / despatch time. 계약 14. 예약 발송에만 쓴다. */
        @JsonProperty("reqdate") String reqdate,

        /** 발송메시지 / message body. 계약 4000, 유효 1000 / contract 4000, effective 1000. */
        @JsonProperty("msg") String msg,

        /** 프로파일키 / vendor profile key. 서버에서만 채운다 / server-resolved only. */
        @JsonProperty("sender_key") ProfileKey senderKey,

        /** 템플릿코드 / template code. 계약 30. 등록된 템플릿이어야 한다 / must be registered. */
        @JsonProperty("template_code") String templateCode,

        /** 강조표기제목 / emphasis title. 계약 200, 유효 50. 강조표기형에만 / emphasis form only. */
        @JsonProperty("template_title") String templateTitle,

        /** 버튼 / buttons. */
        @JsonProperty("button") List<AlimTalkButton> button,

        /**
         * 실패 시 대체 전송 / fallback.
         *
         * <p><b>{@code failback} 이 아니라 {@code failback_data}</b> — D-A1 의 전부가 이 이름에
         * 달려 있다.</p>
         * <p><b>{@code failback_data}, not {@code failback}</b> — the whole of D-A1 is this name.</p>
         */
        @JsonProperty("failback_data") FailbackData failbackData) {
}
