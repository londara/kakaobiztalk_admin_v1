package com.webcash.iris.biztalk.alimtalk.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.webcash.iris.biztalk.alimtalk.domain.AlimTalkButton.ButtonType;
import com.webcash.iris.biztalk.alimtalk.domain.FailbackData.FailbackType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * payload 구성요소의 검증 규칙. / Validation rules on the payload's component types.
 *
 * <p>이 테스트는 <b>커버리지 측정이 찾아낸 공백</b>을 메운다. Sprint A1 의 첫 측정에서 라인
 * 77.3 % / 브랜치 58.7 % 가 나왔고, 미달의 대부분이 여기 있는 네 곳이었다 —
 * {@link AlimTalkButton#isComplete()}, {@link FailbackData#isValid()},
 * {@link AlimTalkLimits#within}, {@link ProfileKey#of}. 모두 <b>내가 작성한 검증 로직인데 한 번도
 * 실행하지 않은 것들</b>이다. 계약 적합성 테스트가 필드 <i>이름</i>을 지켰을 뿐, 필드 <i>규칙</i>은
 * 아무도 확인하지 않았다.</p>
 * <p>This test fills the gap <b>coverage measurement found</b>. Sprint A1's first measurement returned
 * 77.3 % line / 58.7 % branch, and most of the shortfall was in four places here — all
 * <b>validation logic I wrote and never executed</b>. The conformance test guarded field <i>names</i>;
 * nothing checked field <i>rules</i>.</p>
 *
 * <p>기록할 만한 점: 이 공백은 테스트 수(84건)로는 보이지 않았다. 측정을 하지 않으면 "테스트가
 * 많다"가 "검증되었다"로 읽힌다.</p>
 * <p>Worth recording: the gap was invisible in the test count (84). Without measurement, "many tests"
 * reads as "verified".</p>
 *
 * // source: IMO.ADV_KKO_AT_SEND.xml rule_Sub_1/rule_Sub_2; biztalk_admin_61.js — button/failback assembly
 * // req: FR-ATC-002, FR-ATC-005, FR-ATC-009, NFR-SEC-CRED-A01
 */
class PayloadValidationTest {

    @Nested
    @DisplayName("D-A9 — 버튼 완전성 / button completeness")
    class ButtonCompleteness {

        @Test
        @DisplayName("웹링크는 모바일 URL 이 있어야 완전하다 / WL needs a mobile URL")
        // req: FR-ATC-009
        void webLinkNeedsMobileUrl() {
            // 레거시는 이름이 있으면 통과시켰으므로 URL 없는 웹링크 버튼이 그대로 전송될 수 있었다.
            // The legacy passed any named button, so a web-link button with no URL could be despatched.
            assertThat(new AlimTalkButton("자세히", ButtonType.WL, null, "https://m", null, null).isComplete())
                    .isTrue();
            assertThat(new AlimTalkButton("자세히", ButtonType.WL, "https://pc", null, null, null).isComplete())
                    .isFalse();
            assertThat(new AlimTalkButton("자세히", ButtonType.WL, null, "  ", null, null).isComplete())
                    .isFalse();
        }

        @Test
        @DisplayName("앱링크는 스킴 하나 이상이 있어야 완전하다 / AL needs at least one scheme")
        // req: FR-ATC-009
        void appLinkNeedsAScheme() {
            assertThat(new AlimTalkButton("열기", ButtonType.AL, null, null, "app://a", null).isComplete()).isTrue();
            assertThat(new AlimTalkButton("열기", ButtonType.AL, null, null, null, "app://i").isComplete()).isTrue();
            assertThat(new AlimTalkButton("열기", ButtonType.AL, null, null, null, null).isComplete()).isFalse();
            assertThat(new AlimTalkButton("열기", ButtonType.AL, null, null, "  ", "  ").isComplete()).isFalse();
        }

        @ParameterizedTest
        @EnumSource(value = ButtonType.class, names = {"DS", "BK", "MD"})
        @DisplayName("배송조회·봇키워드·메시지전달은 추가 항목이 없다 / DS, BK and MD need nothing further")
        // req: FR-ATC-009
        void typesWithoutExtraFields(ButtonType type) {
            assertThat(new AlimTalkButton("확인", type, null, null, null, null).isComplete()).isTrue();
        }

        @ParameterizedTest
        @CsvSource(value = {"NULL", "''", "'   '"}, nullValues = "NULL")
        @DisplayName("이름이 없으면 어떤 유형도 완전하지 않다 / no type is complete without a name")
        // req: FR-ATC-009
        void nameIsAlwaysRequired(String name) {
            assertThat(new AlimTalkButton(name, ButtonType.DS, null, null, null, null).isComplete()).isFalse();
        }

        @Test
        @DisplayName("유형이 없으면 완전하지 않다 / a missing type is incomplete")
        // req: FR-ATC-009
        void typeIsRequired() {
            assertThat(new AlimTalkButton("확인", null, null, null, null, null).isComplete()).isFalse();
        }

        @Test
        @DisplayName("유형 코드는 계약의 2자다 / the type code is the contract's two characters")
        // req: FR-ATC-005
        void typeCodeIsTwoCharacters() {
            for (ButtonType type : ButtonType.values()) {
                assertThat(type.code()).hasSize(AlimTalkLimits.CONTRACT_BUTTON_TYPE);
            }
        }
    }

    @Nested
    @DisplayName("D-A17 — 대체 전송 검증 / fallback validation")
    class FailbackValidation {

        @Test
        @DisplayName("본문이 없으면 유효하지 않다 / a fallback with no body is invalid")
        // req: FR-ATC-002
        void bodyIsRequired() {
            // 레거시는 유형만 담긴 {type} 객체를 만들어 보냈다 — 유형은 있으나 보낼 내용이 없는
            // 대체 전송이며, 빈 값 제거 replacer 가 msg 를 지운 결과다.
            // The legacy emitted a {type}-only object: a fallback with a type and nothing to send, produced
            // by its empty-value replacer stripping msg.
            assertThat(new FailbackData(FailbackType.SMS, null, null, null).isValid()).isFalse();
            assertThat(new FailbackData(FailbackType.SMS, null, "  ", null).isValid()).isFalse();
            assertThat(new FailbackData(FailbackType.SMS, null, "본문", null).isValid()).isTrue();
        }

        @Test
        @DisplayName("유형이 없으면 유효하지 않다 / a missing type is invalid")
        // req: FR-ATC-002
        void typeIsRequired() {
            assertThat(new FailbackData(null, null, "본문", null).isValid()).isFalse();
        }

        @Test
        @DisplayName("SMS 는 제목을 허용하지 않는다 / SMS permits no subject")
        // req: FR-ATC-002
        void smsPermitsNoSubject() {
            assertThat(new FailbackData(FailbackType.SMS, "[안내]", "본문", null).isValid()).isFalse();
            assertThat(FailbackType.SMS.allowsSubject()).isFalse();
        }

        @ParameterizedTest
        @EnumSource(value = FailbackType.class, names = {"LMS", "MMS"})
        @DisplayName("LMS·MMS 는 제목을 허용한다 / LMS and MMS permit a subject")
        // req: FR-ATC-002
        void lmsAndMmsPermitSubject(FailbackType type) {
            assertThat(type.allowsSubject()).isTrue();
            assertThat(new FailbackData(type, "[안내]", "본문", null).isValid()).isTrue();
        }

        @Test
        @DisplayName("이미지는 MMS 만 허용한다 / only MMS permits an image")
        // req: FR-ATC-002
        void onlyMmsPermitsImage() {
            assertThat(new FailbackData(FailbackType.MMS, "[안내]", "본문", "IMG_1").isValid()).isTrue();
            assertThat(new FailbackData(FailbackType.LMS, "[안내]", "본문", "IMG_1").isValid()).isFalse();
            assertThat(new FailbackData(FailbackType.SMS, null, "본문", "IMG_1").isValid()).isFalse();
            assertThat(FailbackType.MMS.allowsImage()).isTrue();
            assertThat(FailbackType.LMS.allowsImage()).isFalse();
        }

        @Test
        @DisplayName("D-A36 — 유형 코드는 3자이며, 계약의 선언 2자는 틀렸다 / codes are 3 chars; the declared 2 is wrong")
        // req: FR-ATC-002, FR-ATC-005, CONST-DATA-A02
        void typeCodeIsThreeCharactersAndTheContractIsWrong() {
            // 이 단언은 원래 "계약의 2자와 같다"였고, 그렇게 썼을 때 실패했다 — 코드가 아니라
            // 계약이 틀렸기 때문이다. failback_data 하위 규칙은 button 하위 규칙의 type 항목을
            // 그대로 복사했고, name="버튼타입" 까지 동일하다. 유효 값은 모두 3자이므로 선언된
            // 2자를 강제하면 유효한 대체 전송이 전부 거절된다.
            // This assertion originally read "equals the contract's 2" and failed when written that way —
            // because the contract is wrong, not the code. The failback_data sub-rule copies the button
            // sub-rule's type item verbatim, name="버튼타입" included. All valid values are three characters,
            // so enforcing the declared 2 would reject every valid fallback.
            for (FailbackType type : FailbackType.values()) {
                assertThat(type.code())
                        .as("대체 전송 유형 코드 / fallback type code")
                        .hasSize(AlimTalkLimits.CONTRACT_FAILBACK_TYPE);
            }

            // 불일치 자체를 고정한다. 계약이 고쳐지면 이 단언이 실패해 우리에게 알린다.
            // The discrepancy itself is pinned: if the contract is corrected, this assertion fails and
            // tells us.
            assertThat(AlimTalkLimits.CONTRACT_FAILBACK_TYPE)
                    .as("D-A36: 필요한 길이가 선언된 길이보다 크다 / needed length exceeds the declared length")
                    .isGreaterThan(AlimTalkLimits.CONTRACT_FAILBACK_TYPE_DECLARED);
        }

        @Test
        @DisplayName("NO 는 열거형에 없다 — 대체전송 없음은 블록 부재다 / there is no NO value")
        // req: FR-ATC-002
        void thereIsNoNoValue() {
            // 레거시 UI 의 NO 는 type="NO" 를 보내는 것이 아니라 failback_data 블록 자체가 없다는
            // 뜻이다. 열거형에 NO 를 두면 그 구분이 흐려진다.
            // The legacy UI's NO means the failback_data block is absent, not that type="NO" is sent.
            // Having a NO value would blur that distinction.
            assertThat(FailbackType.values()).extracting(Enum::name).doesNotContain("NO");
        }
    }

    @Nested
    @DisplayName("D-A7 — 길이 한계 / length bounds")
    class Limits {

        @Test
        @DisplayName("한계 이내와 초과를 구분한다 / distinguishes within from over")
        // req: FR-ATC-005
        void distinguishesWithinFromOver() {
            assertThat(AlimTalkLimits.within("123456", AlimTalkLimits.CONTRACT_IS_CD)).isTrue();
            assertThat(AlimTalkLimits.within("1234567", AlimTalkLimits.CONTRACT_IS_CD)).isFalse();
        }

        @Test
        @DisplayName("null 은 통과시킨다 — 필수 여부는 별개 규칙 / null passes; presence is a separate rule")
        // req: FR-ATC-005, NFR-USE-A03
        void nullPasses() {
            // 두 판정을 섞으면 오류 메시지가 어느 규칙을 위반했는지 말할 수 없게 된다.
            // Merging the two leaves the error message unable to name which rule was broken.
            assertThat(AlimTalkLimits.within(null, 1)).isTrue();
        }

        @Test
        @DisplayName("유효 한계는 계약과 업무 중 작은 값이다 / the effective bound is the lower of the two")
        // req: CONST-DATA-A02, CONFLICT-A02
        void effectiveBoundIsTheLower() {
            assertThat(AlimTalkLimits.MSG).isEqualTo(AlimTalkLimits.BUSINESS_MSG);
            assertThat(AlimTalkLimits.TEMPLATE_TITLE).isEqualTo(AlimTalkLimits.BUSINESS_TEMPLATE_TITLE);
            assertThat(AlimTalkLimits.BUTTON_NAME).isEqualTo(AlimTalkLimits.BUSINESS_BUTTON_NAME);
        }
    }

    @Nested
    @DisplayName("NFR-SEC-CRED-A01 — 프로파일키 / profile key")
    class ProfileKeyGuards {

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        @DisplayName("빈 값은 거절한다 / a blank key is rejected")
        // req: FR-ATS-003
        void blankIsRejected(String value) {
            assertThatThrownBy(() -> ProfileKey.of(value)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("null 은 거절한다 / null is rejected")
        // req: FR-ATS-003
        void nullIsRejected() {
            assertThatThrownBy(() -> ProfileKey.of(null)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("계약 길이를 넘으면 거절하고, 메시지에 값을 담지 않는다 / over-length rejected without echoing the value")
        // req: FR-ATC-005, NFR-SEC-CRED-A01
        void overLengthRejectedWithoutEchoingValue() {
            String tooLong = "k".repeat(AlimTalkLimits.CONTRACT_SENDER_KEY + 1);

            assertThatThrownBy(() -> ProfileKey.of(tooLong))
                    .isInstanceOf(IllegalArgumentException.class)
                    // 예외 메시지도 로그로 흘러간다 — 값을 담으면 D-A30 이 예외 경로로 재발한다.
                    // Exception text reaches logs too: echoing the value would reproduce D-A30 via the
                    // exception path.
                    .hasMessageNotContaining(tooLong);
        }

        @Test
        @DisplayName("계약 길이 경계는 허용한다 / the contract length itself is accepted")
        // req: FR-ATC-005
        void boundaryIsAccepted() {
            assertThat(ProfileKey.of("k".repeat(AlimTalkLimits.CONTRACT_SENDER_KEY))).isNotNull();
        }

        @Test
        @DisplayName("값 동등성이 성립한다 / value equality holds")
        // req: FR-ATS-003
        void valueEqualityHolds() {
            ProfileKey a = ProfileKey.of("same-key");
            ProfileKey b = ProfileKey.of("same-key");

            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
            assertThat(a).isNotEqualTo(ProfileKey.of("other-key"));
            assertThat(a).isNotEqualTo("same-key");
        }

        @Test
        @DisplayName("원문은 명시적 호출로만 얻는다 / the raw value needs an explicit call")
        // req: FR-ATS-003, NFR-SEC-CRED-A01
        void rawValueNeedsExplicitCall() {
            ProfileKey key = ProfileKey.of("raw-value");

            assertThat(key.exposeForVendorCall()).isEqualTo("raw-value");
            assertThat(key.toString()).isEqualTo(ProfileKey.REDACTED);
            assertThat(key.jsonValue()).isEqualTo(ProfileKey.REDACTED);
        }
    }

    @Nested
    @DisplayName("NFR-SEC-PII — 수신번호 래퍼 / recipient wrapper")
    class RecipientGuards {

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        @DisplayName("빈 값은 거절한다 / a blank number is rejected")
        // req: FR-ATS-005
        void blankIsRejected(String value) {
            assertThatThrownBy(() -> RecipientNumber.of(value)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("null 은 거절한다 / null is rejected")
        // req: FR-ATS-005
        void nullIsRejected() {
            assertThatThrownBy(() -> RecipientNumber.of(null)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("동등성이 표기와 무관하게 성립한다 / equality is independent of the wrapper identity")
        // req: FR-ATC-012
        void equalityHolds() {
            RecipientNumber a = RecipientNumber.of("01012345678");

            assertThat(a).isEqualTo(RecipientNumber.of("01012345678"))
                    .hasSameHashCodeAs(RecipientNumber.of("01012345678"));
            assertThat(a).isNotEqualTo(RecipientNumber.of("01099998888"));
            assertThat(a).isNotEqualTo("01012345678");
        }
    }
}
