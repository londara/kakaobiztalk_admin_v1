package com.webcash.iris.biztalk.alimtalk.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 다건 알림톡 전송 요청 — {@code IMO.ADV_KKO_AT_SEND_M} 대응. / Batch AlimTalk send request.
 *
 * <h2>이 계약은 지금까지 <b>한 번도 호출되지 않았다</b> / this contract has <b>never been called</b></h2>
 * <p>저장소 전체에서 {@code ADV_KKO_AT_SEND_M} 을 호출하는 코드는 없다. 화면 50 의 대량 분기는
 * 단건 인터페이스({@code ADV_KKO_AT_SEND})에 같은 요청 객체를 재사용·변형하며 1000건씩 반복
 * 호출했다(D-A33). 그 결과 이 계약이 요구하는 {@code order} 필드의 부재(D-A3)가 드러날 경로가
 * 없었다.</p>
 * <p>No code in the repository calls {@code ADV_KKO_AT_SEND_M}. Screen 50's high-volume branch
 * looped over the <b>single-send</b> interface, mutating and re-executing one request object 1000
 * recipients at a time (D-A33). Consequently the absence of the {@code order} field this contract
 * requires (D-A3) had no path by which it could surface.</p>
 *
 * <p>두 결함은 서로를 설명한다: 호출자가 없으니 검증이 없었고, 검증이 없으니 빠진 필드가
 * 드러나지 않았다. 이것이 {@code ContractConformanceTest} 가 이 슬라이스에서 가장 값싼 동시에
 * 가장 중요한 장치인 이유다.</p>
 * <p>The two defects explain each other: no caller meant no validation, and no validation meant the
 * missing field never surfaced. This is why {@code ContractConformanceTest} is simultaneously the
 * cheapest and the most important mechanism in this slice.</p>
 *
 * // source: IMO.ADV_KKO_AT_SEND_M.xml — rule/in, rule_Sub_1 id="msg_data"
 * // req: FR-ATC-001, FR-ATC-004, FR-ATS-013, CONST-DATA-A01
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AlimTalkBatchRequest(

        /** 이용기관코드 / institution code. 계약 6 / contract 6. */
        @JsonProperty("is_cd") String isCd,

        /** 거래고유번호 / transaction id. 계약 10. 배치 전체에 하나 / one per batch. */
        @JsonProperty("tran_id") String tranId,

        /** 메시지 데이터 / message items. */
        @JsonProperty("msg_data") List<MsgDataItem> msgData) {

    /**
     * 다건 전송의 개별 메시지 — 계약 {@code rule_Sub_1 id="msg_data"}. / One batch message item.
     *
     * // source: IMO.ADV_KKO_AT_SEND_M.xml rule_Sub_1; biztalk_admin_61.js — msgData assembly
     * // req: FR-ATC-004, FR-ATC-007
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MsgDataItem(

            /**
             * 순번 / item order. 계약 6 / contract 6.
             *
             * <p>레거시 화면 61 은 이 필드를 <b>전혀 내보내지 않았다</b>(D-A3, Critical).
             * 계약은 모든 항목에 이것을 선언한다. 순번이 없으면 어느 수신자가 어느 메시지를
             * 받는지가 <b>시스템 경계를 넘어 배열 위치에 의존</b>하게 된다 — 부분 실패를
             * 보고할 방법도 사라진다(NFR-OPS-A02).</p>
             * <p>Legacy screen 61 <b>never emitted</b> this field (D-A3, Critical) though the contract
             * declares it on every item. Without it, which recipient gets which message
             * <b>depends on array position across a system boundary</b> — and there is no way to report
             * a partial failure (NFR-OPS-A02).</p>
             */
            @JsonProperty("order") String order,

            /** 수신폰번호 / recipient. 단건과 달리 항목별 하나 / one per item, unlike single send. */
            @JsonProperty("receiver_number") RecipientNumber receiverNumber,

            /** 콜백송신자번호 / caller ID. 계약 24. */
            @JsonProperty("sender_number") String senderNumber,

            /**
             * 발송일시 / despatch time. 계약 14.
             *
             * <p><b>계약은 이 필드를 항목마다 선언한다.</b> 화면 61 의 다건 탭은 이를 수집하지
             * 않았으므로 예약 발송이 다건에서 불가능했다(D-A14) — 설계 결정이 아니라 누락이다.
             * AMB-A04 를 계약이 사실상 결정한 셈이다.</p>
             * <p><b>The contract declares this per item.</b> Screen 61's batch tab did not collect it,
             * so batch reservation was impossible (D-A14) — an omission, not a design decision. The
             * contract effectively settles AMB-A04.</p>
             */
            @JsonProperty("reqdate") String reqdate,

            /** 발송메시지 / message body. 계약 4000, 유효 1000. */
            @JsonProperty("msg") String msg,

            /** 프로파일키 / profile key. 서버에서만 채운다 / server-resolved only. */
            @JsonProperty("sender_key") ProfileKey senderKey,

            /** 템플릿코드 / template code. 계약 30. */
            @JsonProperty("template_code") String templateCode,

            /** 강조표기제목 / emphasis title. 계약 200, 유효 50. */
            @JsonProperty("template_title") String templateTitle,

            /** 실패 시 대체 전송 / fallback. {@code failback_data} — D-A1 참조 / see D-A1. */
            @JsonProperty("failback_data") FailbackData failbackData,

            /** 버튼 / buttons. */
            @JsonProperty("button") List<AlimTalkButton> button) {
    }
}
