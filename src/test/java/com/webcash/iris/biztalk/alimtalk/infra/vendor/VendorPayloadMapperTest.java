package com.webcash.iris.biztalk.alimtalk.infra.vendor;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webcash.iris.biztalk.alimtalk.domain.AlimTalkRequest;
import com.webcash.iris.biztalk.alimtalk.domain.ProfileKey;
import com.webcash.iris.biztalk.alimtalk.domain.RecipientNumber;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link VendorPayloadMapper} 검증 — 마스킹의 예외가 <b>정확히 여기서만</b> 성립하는지.
 * Verifies that the masking exception holds <b>here and only here</b>.
 *
 * <p>이 테스트 묶음이 지키는 성질은 두 방향이다. 벤더용 매퍼는 실제 값을 내보내야 하고, 기본
 * 매퍼는 <b>같은 객체</b>에 대해 가려진 값을 내보내야 한다. 한 방향만 검사하면 나머지 방향이
 * 조용히 무너진다 — 그리고 무너지는 쪽이 하필 노출 쪽이다.</p>
 * <p>Two directions are held: the vendor mapper must emit real values, and the default mapper must emit
 * masked ones for the <b>same object</b>. Checking one direction lets the other collapse quietly — and
 * the one that collapses is the exposing one.</p>
 *
 * // req: FR-ATS-004, FR-AZ-A05, NFR-SEC-PII-A01, NFR-SEC-CRED-A01
 */
class VendorPayloadMapperTest {

    private static final String RAW_NUMBER = "01011112222";
    private static final String RAW_KEY = "SYNTHETIC-PROFILE-KEY-FOR-TESTS-ONLY-0001";

    private static AlimTalkRequest payload() {
        // 계약 순서 그대로 / in contract order:
        // is_cd, tran_id, sender_number, receiver_number, reqdate, msg,
        // sender_key, template_code, template_title, button, failback_data
        return new AlimTalkRequest(
                "K00001",
                "T260819001",
                "0212345678",
                List.of(RecipientNumber.of(RAW_NUMBER)),
                null,
                "50,000원이 결제되었습니다.",
                ProfileKey.of(RAW_KEY),
                "TMPL_0001",
                "결제 안내",
                null,
                null);
    }

    @Test
    @DisplayName("벤더용 payload 는 실제 수신번호를 담는다 / the vendor payload carries the real recipient")
    // req: FR-ATS-004
    void vendorPayloadCarriesTheRealRecipient() throws Exception {
        // 가려진 번호를 벤더에 보내면 발송이 실패한다 — 또는 더 나쁘게, 존재하지 않는 번호로
        // 접수된다. 계약을 만족시키려면 예외가 한 곳 필요하고, 그 한 곳이 여기다.
        // Sending a masked number would fail — or worse, be accepted for a number that does not exist.
        // Satisfying the contract needs one exception, and this is it.
        String json = new VendorPayloadMapper().render(payload());

        assertThat(json).contains(RAW_NUMBER);
        assertThat(json).contains(RAW_KEY);
    }

    @Test
    @DisplayName("기본 매퍼는 같은 객체를 가려서 쓴다 / the default mapper masks the same object")
    // req: NFR-SEC-PII-A01, NFR-SEC-CRED-A01
    void defaultMapperStillMasks() {
        // 이 어서션이 무너지면 미리보기·응답·로그 어디로든 평문이 흐를 수 있다. 벤더용 매퍼를
        // 추가한 것이 기본 동작을 바꾸지 않았음을 여기서 고정한다.
        // If this assertion falls, plaintext can flow into previews, responses and logs. It pins that
        // adding the vendor mapper did not change the default behaviour.
        AlimTalkRequest same = payload();

        assertThat(same.receiverNumber().get(0).toString()).doesNotContain(RAW_NUMBER);
        assertThat(same.senderKey().toString()).doesNotContain(RAW_KEY);
    }

    @Test
    @DisplayName("기본 Jackson 직렬화는 여전히 가린다 / plain Jackson serialisation still masks")
    // req: NFR-SEC-PII-A01, NFR-SEC-CRED-A01, FR-AZ-A05
    void plainJacksonStillMasks() throws Exception {
        // 벤더용 모듈은 <b>그 매퍼 인스턴스에만</b> 등록된다. 전역으로 등록되면 미리보기
        // 응답까지 평문이 되고, D-A24·D-A30 이 형태만 바꿔 돌아온다.
        // The vendor module is registered on <b>that mapper instance only</b>. Registered globally, even
        // the preview response would carry plaintext and D-A24/D-A30 would return in another shape.
        String json = new ObjectMapper().writeValueAsString(payload());

        assertThat(json).doesNotContain(RAW_NUMBER);
        assertThat(json).doesNotContain(RAW_KEY);
        assertThat(json).contains("REDACTED");
    }

    @Test
    @DisplayName("벤더용 출력도 계약 필드명을 지킨다 / the vendor output keeps the contract field names")
    // req: CONST-DATA-A01, FR-ATC-001
    void vendorOutputKeepsContractFieldNames() throws Exception {
        // 마스킹만 바꾸고 필드명이 바뀌면 벤더가 거절한다. D-A1 이 바로 그 부류였다 —
        // 레거시가 failback_data 를 failback 으로 내보냈다.
        // Changing only the masking must not change field names, or the vendor rejects it. D-A1 was
        // exactly that class of defect: the legacy emitted failback where the contract said
        // failback_data.
        String json = new VendorPayloadMapper().render(payload());

        assertThat(json)
                .contains("\"is_cd\"")
                .contains("\"tran_id\"")
                .contains("\"receiver_number\"")
                .contains("\"sender_key\"")
                .contains("\"template_code\"");
    }

    @Test
    @DisplayName("들여쓰기를 넣지 않는다 / no pretty printing")
    // req: FR-ATS-004
    void noPrettyPrinting() throws Exception {
        // 미리보기는 사람이 읽으므로 들여쓰기가 값이지만, 벤더 본문에서는 바이트만 늘린다.
        // The preview is read by a person so indentation earns its keep; in a vendor body it only adds
        // bytes.
        String json = new VendorPayloadMapper().render(payload());

        assertThat(json).doesNotContain("\n");
    }
}
