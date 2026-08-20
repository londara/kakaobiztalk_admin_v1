package com.webcash.iris.biztalk.alimtalk.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webcash.iris.biztalk.alimtalk.domain.AlimTalkBatchRequest.MsgDataItem;
import com.webcash.iris.biztalk.alimtalk.domain.AlimTalkButton.ButtonType;
import com.webcash.iris.biztalk.alimtalk.domain.FailbackData.FailbackType;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * 발신 계약 적합성 검증 — 계약 XML 을 <b>테스트 픽스처로</b> 사용한다.
 * Outbound contract conformance, using the contract XML <b>as the test fixture</b>.
 *
 * <h2>이 테스트가 이 슬라이스에서 가장 중요한 이유 / why this is the slice's most important test</h2>
 * <p>Critical 결함 셋 — D-A1({@code failback} vs {@code failback_data}),
 * D-A2(계약에 없는 다섯 필드), D-A3(빠진 {@code order}) — 는 모두 "우리가 만든 payload 가
 * 계약과 다르다"는 하나의 결함이다. 흥미로운 사실은 이 결함이 존재했다는 것이 아니라
 * <b>드러날 경로가 없었다</b>는 것이다: {@code ADV_KKO_AT_SEND_M} 을 호출하는 코드가 저장소에
 * 없고, 화면 61 의 출력은 운영자가 손으로 복사했다. 1년 넘게 코드 리뷰만이 유일한 방어였고,
 * 리뷰는 네 개의 결함을 잡지 못했다.</p>
 * <p>The three Critical defects are one defect: our payload does not match the contract. What is
 * interesting is not that they existed but that <b>nothing could detect them</b> — no code calls the
 * batch contract, and screen 61's output was copied out by hand. For over a year code review was the
 * only control, and it caught none of the four.</p>
 *
 * <p>그래서 이 테스트는 손으로 쓴 기대값을 쓰지 않는다. <b>계약 XML 을 읽어</b> 비교한다.
 * 벤더 계약이 바뀌면 다음 빌드에서 실패하고, 다음 고객 알림에서 실패하지 않는다.</p>
 * <p>So this test uses no hand-written expectations: it <b>reads the contract XML</b>. If the vendor
 * contract changes, the next build fails rather than the next customer notification.</p>
 *
 * <h2>양방향이어야 하는 이유 / why it must be bidirectional</h2>
 * <p>"계약의 모든 필드가 우리 DTO 에 있는가"만 검사하면 <b>레거시 payload 도 통과한다</b> —
 * {@code failback} 과 {@code msg_type} 은 <i>빠진</i> 필드가 아니라 <i>남는</i> 필드다.
 * 역방향 검사가 D-A1 과 D-A2 를 잡는 유일한 장치다.</p>
 * <p>Checking only "does every contract field exist in our DTO" <b>passes on the legacy payload</b>:
 * {@code failback} and {@code msg_type} are <i>extra</i>, not <i>missing</i>. The reverse direction is the
 * only thing that catches D-A1 and D-A2.</p>
 *
 * <p>Docker 가 금지된 환경에서(RISK-A12) 이 검증은 <b>인프라를 전혀 요구하지 않는다</b> —
 * 순수 직렬화 비교다. 발신번호 슬라이스는 핵심 결함이 DB 함수 상호작용이어서 검증할 수
 * 없었지만(RISK-S13), 이 슬라이스의 핵심 결함은 tier 1 에서 온전히 덮인다.</p>
 * <p>With Docker prohibited (RISK-A12) this verification needs <b>no infrastructure at all</b> — it is
 * pure serialisation comparison. The 발신번호 slice could not verify its headline defect because it was a
 * DB-function interaction (RISK-S13); this slice's headline defects are fully covered at tier 1.</p>
 *
 * // source: IMO.ADV_KKO_AT_SEND.xml, IMO.ADV_KKO_AT_SEND_M.xml (src/test/resources/contracts/imo)
 * // req: FR-ATC-001, FR-ATC-002, FR-ATC-003, FR-ATC-004, FR-ATC-005, CONST-DATA-A01, CONST-DATA-A02
 */
class ContractConformanceTest {

    private static final String SINGLE = "/contracts/imo/IMO.ADV_KKO_AT_SEND.xml";
    private static final String BATCH = "/contracts/imo/IMO.ADV_KKO_AT_SEND_M.xml";

    private final ObjectMapper mapper = new ObjectMapper();

    // =====================================================================
    // 단건 계약 / the single-send contract
    // =====================================================================

    @Nested
    @DisplayName("단건 계약 ADV_KKO_AT_SEND / single-send contract")
    class SingleSend {

        @Test
        @DisplayName("계약이 선언한 모든 필드가 payload 에 있다 — D-A3 방향 / every declared field is present")
        // req: FR-ATC-001, FR-ATC-004
        void everyContractFieldIsEmitted() {
            Contract contract = Contract.load(SINGLE);
            JsonNode payload = mapper.valueToTree(fullyPopulatedRequest());

            assertThat(fieldNames(payload))
                    .as("계약 in 블록의 모든 item id / every item id in the contract's in block")
                    .containsAll(contract.topLevelIds());
        }

        @Test
        @DisplayName("계약에 없는 필드는 payload 에 없다 — D-A1·D-A2 방향 / no undeclared field is emitted")
        // req: FR-ATC-002, FR-ATC-003
        void noUndeclaredFieldIsEmitted() {
            Contract contract = Contract.load(SINGLE);
            JsonNode payload = mapper.valueToTree(fullyPopulatedRequest());

            // 이 방향이 없으면 레거시 payload 도 통과한다.
            // Without this direction the legacy payload passes.
            assertThat(fieldNames(payload))
                    .as("계약이 선언하지 않은 필드 / fields the contract does not declare")
                    .isSubsetOf(contract.topLevelIds());
        }

        @Test
        @DisplayName("D-A1 회귀 — 대체 전송 키는 failback_data 다 / the fallback key is failback_data")
        // req: FR-ATC-002, CONST-DATA-A01
        void fallbackKeyIsFailbackData() {
            JsonNode payload = mapper.valueToTree(fullyPopulatedRequest());

            assertThat(payload.has("failback_data"))
                    .as("계약이 선언한 이름 / the name the contract declares")
                    .isTrue();
            assertThat(payload.has("failback"))
                    .as("레거시가 내보낸 이름 — 이 필드가 있으면 대체 전송이 조용히 사라진다 "
                            + "/ the legacy's name; its presence loses the fallback silently")
                    .isFalse();
            assertThat(Contract.load(SINGLE).topLevelIds()).contains("failback_data").doesNotContain("failback");
        }

        @Test
        @DisplayName("D-A2 회귀 — 계약에 없는 다섯 필드가 없다 / the five undeclared fields are absent")
        // req: FR-ATC-003
        void legacyExtraFieldsAreAbsent() {
            JsonNode payload = mapper.valueToTree(fullyPopulatedRequest());

            // 화면 61 이 내보냈던 필드들. 아이템리스트형 UI 를 가득 채워도 벤더에는 아무것도
            // 도달하지 않았다. 벤더 명세 확보 후(AMB-A05, spike A1-01) 계약을 확장해 추가한다.
            // The fields screen 61 emitted. Filling the item-list form sent nothing to the vendor.
            // They are added by extending the contract once the spec arrives (AMB-A05, spike A1-01).
            assertThat(fieldNames(payload))
                    .doesNotContain("msg_type", "kko_header", "highlight", "items", "summary");
        }

        @Test
        @DisplayName("중첩 구조도 계약과 일치한다 / nested structures conform too")
        // req: FR-ATC-001, FR-ATC-002
        void nestedStructuresConform() {
            Contract contract = Contract.load(SINGLE);
            JsonNode payload = mapper.valueToTree(fullyPopulatedRequest());

            assertThat(fieldNames(payload.get("button").get(0)))
                    .as("button 하위 규칙 / the button sub-rule")
                    .containsExactlyInAnyOrderElementsOf(contract.subRuleIds("button"));
            assertThat(fieldNames(payload.get("failback_data")))
                    .as("failback_data 하위 규칙 / the failback_data sub-rule")
                    .containsExactlyInAnyOrderElementsOf(contract.subRuleIds("failback_data"));
        }
    }

    // =====================================================================
    // 다건 계약 / the batch contract
    // =====================================================================

    @Nested
    @DisplayName("다건 계약 ADV_KKO_AT_SEND_M / batch contract")
    class BatchSend {

        @Test
        @DisplayName("계약이 선언한 모든 필드가 있고 없는 필드는 없다 / conforms in both directions")
        // req: FR-ATC-001, FR-ATS-013
        void conformsInBothDirections() {
            Contract contract = Contract.load(BATCH);
            JsonNode payload = mapper.valueToTree(fullyPopulatedBatchRequest());

            assertThat(fieldNames(payload))
                    .containsExactlyInAnyOrderElementsOf(contract.topLevelIds());
        }

        @Test
        @DisplayName("D-A3 회귀 — 모든 msg_data 항목에 order 가 있다 / every item carries order")
        // req: FR-ATC-004
        void everyItemCarriesOrder() {
            Contract contract = Contract.load(BATCH);
            JsonNode payload = mapper.valueToTree(fullyPopulatedBatchRequest());

            assertThat(contract.subRuleIds("msg_data"))
                    .as("계약이 order 를 선언한다 / the contract declares order")
                    .contains("order");
            for (JsonNode item : payload.get("msg_data")) {
                assertThat(item.has("order"))
                        .as("항목마다 order / order on each item")
                        .isTrue();
                assertThat(fieldNames(item))
                        .containsExactlyInAnyOrderElementsOf(contract.subRuleIds("msg_data"));
            }
        }

        @Test
        @DisplayName("D-A14 — 계약은 항목별 reqdate 를 선언한다 / the contract declares per-item reqdate")
        // req: FR-ATC-007
        void contractDeclaresPerItemReqdate() {
            // AMB-A04 를 사실상 계약이 결정한다: 다건 예약 발송은 원래 가능했고, 화면 61 이
            // 수집하지 않았을 뿐이다. 설계 결정이 아니라 누락이었다.
            // The contract effectively settles AMB-A04: batch reservation was always available and
            // screen 61 simply never collected it. An omission, not a design decision.
            assertThat(Contract.load(BATCH).subRuleIds("msg_data")).contains("reqdate");
        }
    }

    // =====================================================================
    // 길이 제약 / length bounds
    // =====================================================================

    @Nested
    @DisplayName("D-A7 회귀 — 계약 길이가 상수와 일치한다 / contract lengths match the constants")
    class Lengths {

        @Test
        @DisplayName("단건 계약의 선언 길이 / declared lengths, single contract")
        // req: FR-ATC-005, CONST-DATA-A02
        void singleContractLengths() {
            Map<String, Integer> declared = Contract.load(SINGLE).topLevelLengths();

            assertThat(declared).containsEntry("is_cd", AlimTalkLimits.CONTRACT_IS_CD);
            assertThat(declared).containsEntry("tran_id", AlimTalkLimits.CONTRACT_TRAN_ID);
            assertThat(declared).containsEntry("sender_number", AlimTalkLimits.CONTRACT_SENDER_NUMBER);
            assertThat(declared).containsEntry("receiver_number", AlimTalkLimits.CONTRACT_RECEIVER_NUMBER);
            assertThat(declared).containsEntry("reqdate", AlimTalkLimits.CONTRACT_REQDATE);
            assertThat(declared).containsEntry("msg", AlimTalkLimits.CONTRACT_MSG);
            assertThat(declared).containsEntry("sender_key", AlimTalkLimits.CONTRACT_SENDER_KEY);
            assertThat(declared).containsEntry("template_code", AlimTalkLimits.CONTRACT_TEMPLATE_CODE);
            assertThat(declared).containsEntry("template_title", AlimTalkLimits.CONTRACT_TEMPLATE_TITLE);
        }

        @Test
        @DisplayName("하위 규칙의 선언 길이 / declared lengths, sub-rules")
        // req: FR-ATC-005, CONST-DATA-A02
        void subRuleLengths() {
            Contract contract = Contract.load(SINGLE);

            assertThat(contract.subRuleLengths("button"))
                    .containsEntry("name", AlimTalkLimits.CONTRACT_BUTTON_NAME)
                    .containsEntry("type", AlimTalkLimits.CONTRACT_BUTTON_TYPE)
                    .containsEntry("url_pc", AlimTalkLimits.CONTRACT_BUTTON_LINK)
                    .containsEntry("url_mobile", AlimTalkLimits.CONTRACT_BUTTON_LINK);
            assertThat(contract.subRuleLengths("failback_data"))
                    .containsEntry("type", AlimTalkLimits.CONTRACT_FAILBACK_TYPE_DECLARED)
                    .containsEntry("subject", AlimTalkLimits.CONTRACT_FAILBACK_SUBJECT)
                    .containsEntry("msg", AlimTalkLimits.CONTRACT_FAILBACK_MSG)
                    .containsEntry("img_id", AlimTalkLimits.CONTRACT_FAILBACK_IMG_ID);
            assertThat(Contract.load(BATCH).subRuleLengths("msg_data"))
                    .containsEntry("order", AlimTalkLimits.CONTRACT_ORDER);
        }

        @Test
        @DisplayName("유효 한계는 계약 길이를 넘지 않는다 / effective bounds never exceed the contract")
        // req: CONST-DATA-A02, CONFLICT-A02
        void effectiveBoundsNeverExceedContract() {
            // CONFLICT-A02: 업무 한계가 계약보다 느슨하면 계약이 이긴다. 이 불변식이 깨지면
            // 우리가 계약이 받을 수 없는 값을 통과시키게 된다.
            // CONFLICT-A02: where the business limit is looser, the contract wins. Breaking this
            // invariant means we would accept values the contract cannot carry.
            assertThat(AlimTalkLimits.MSG).isLessThanOrEqualTo(AlimTalkLimits.CONTRACT_MSG);
            assertThat(AlimTalkLimits.TEMPLATE_TITLE).isLessThanOrEqualTo(AlimTalkLimits.CONTRACT_TEMPLATE_TITLE);
            assertThat(AlimTalkLimits.BUTTON_NAME).isLessThanOrEqualTo(AlimTalkLimits.CONTRACT_BUTTON_NAME);
        }
    }

    // =====================================================================
    // 계약 동일성 / contract identity
    // =====================================================================

    @Nested
    @DisplayName("계약 사본이 바뀌면 소리가 난다 / a substituted contract copy is loud")
    class Identity {

        @Test
        @DisplayName("버전과 해시가 고정되어 있다 / version and hash are pinned")
        // req: CONST-DATA-A01
        void versionAndHashArePinned() {
            // 사본이 배포된 정의와 어긋날 수 있다는 것이 이 설계의 잔여 위험이다(RISK-A02).
            // 사본이 조용히 교체되는 경우만이라도 잡는다.
            // The copy can drift from the deployed definition — the design's residual risk (RISK-A02).
            // This at least catches a silently substituted copy.
            Contract single = Contract.load(SINGLE);
            assertThat(single.id()).isEqualTo("ADV_KKO_AT_SEND");
            assertThat(single.version()).isEqualTo("20210615132642");
            assertThat(single.hash()).isEqualTo("BZfzx1dJ2+wEtsMYkR9Dyg==");

            Contract batch = Contract.load(BATCH);
            assertThat(batch.id()).isEqualTo("ADV_KKO_AT_SEND_M");
            assertThat(batch.version()).isEqualTo("20241008174211");
            assertThat(batch.hash()).isEqualTo("rlS2oHs/5G0TFOSymraIQg==");
        }
    }

    // =====================================================================
    // 자격증명·개인정보 직렬화 / credential and PII serialisation
    // =====================================================================

    @Nested
    @DisplayName("D-A30 회귀 — 직렬화가 자격증명·개인정보를 노출하지 않는다 / serialisation leaks neither")
    class Redaction {

        @Test
        @DisplayName("payload 직렬화에 원문 키가 없다 / no raw key in the serialised payload")
        // req: NFR-SEC-CRED-A01
        void serialisedPayloadHasNoRawKey() {
            String json = mapper.valueToTree(fullyPopulatedRequest()).toString();

            assertThat(json).doesNotContain("SYNTHETIC-PROFILE-KEY-FOR-TESTS-ONLY-0001");
            assertThat(json).contains(ProfileKey.REDACTED);
        }

        @Test
        @DisplayName("payload 직렬화에 평문 수신번호가 없다 / no clear recipient number either")
        // req: NFR-SEC-PII-A02
        void serialisedPayloadHasNoClearRecipient() {
            String json = mapper.valueToTree(fullyPopulatedRequest()).toString();

            assertThat(json).doesNotContain("01012345678");
            assertThat(json).contains("010****5678");
        }

        @Test
        @DisplayName("toString 도 마찬가지다 / toString behaves the same")
        // req: NFR-SEC-CRED-A01, NFR-SEC-PII-A02
        void toStringDoesNotLeak() {
            String text = fullyPopulatedRequest().toString();

            assertThat(text).doesNotContain("SYNTHETIC-PROFILE-KEY-FOR-TESTS-ONLY-0001");
            assertThat(text).doesNotContain("01012345678");
        }
    }

    // =====================================================================
    // 픽스처 / fixtures
    // =====================================================================

    /**
     * 모든 필드가 채워진 단건 요청. / A single-send request with every field populated.
     *
     * <p>{@code @JsonInclude(NON_NULL)} 때문에 비어 있는 필드는 직렬화되지 않으므로, 계약 비교를
     * 위해서는 전부 채워야 한다.</p>
     * <p>Because of {@code @JsonInclude(NON_NULL)} empty fields are not serialised, so the comparison
     * requires all of them populated.</p>
     *
     * @return 완전히 채운 요청 / a fully populated request
     */
    private static AlimTalkRequest fullyPopulatedRequest() {
        return new AlimTalkRequest(
                "K00001",
                "A26081800A1",
                "0212345678",
                List.of(RecipientNumber.of("01012345678")),
                "20260819140000",
                "결제가 완료되었습니다.",
                ProfileKey.of("SYNTHETIC-PROFILE-KEY-FOR-TESTS-ONLY-0001"),
                "TMPL_0001",
                "결제 안내",
                List.of(new AlimTalkButton("자세히", ButtonType.WL, "https://pc.example",
                        "https://m.example", "app://a", "app://i")),
                new FailbackData(FailbackType.MMS, "[안내]", "결제가 완료되었습니다.", "IMG_0001"));
    }

    /**
     * 모든 필드가 채워진 다건 요청. / A batch request with every field populated.
     *
     * @return 완전히 채운 배치 요청 / a fully populated batch request
     */
    private static AlimTalkBatchRequest fullyPopulatedBatchRequest() {
        MsgDataItem item = new MsgDataItem(
                "1",
                RecipientNumber.of("01012345678"),
                "0212345678",
                "20260819140000",
                "결제가 완료되었습니다.",
                ProfileKey.of("SYNTHETIC-PROFILE-KEY-FOR-TESTS-ONLY-0001"),
                "TMPL_0001",
                "결제 안내",
                new FailbackData(FailbackType.LMS, "[안내]", "결제가 완료되었습니다.", null),
                List.of(new AlimTalkButton("자세히", ButtonType.WL, "https://pc.example",
                        "https://m.example", null, null)));
        return new AlimTalkBatchRequest("K00001", "A26081800A2", List.of(item));
    }

    /**
     * JSON 객체의 필드명을 모은다. / Collects an object's field names.
     *
     * @param node 대상 노드 / the node
     * @return 필드명 / the field names
     */
    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    /**
     * IMO 계약 XML 을 읽는 최소 파서. / A minimal reader for an IMO contract XML.
     *
     * <p>DOM 은 JDK 에 있으므로 새 의존성이 없다. 계약을 <b>파싱해서</b> 비교한다는 점이
     * 중요하다 — 손으로 옮겨 적은 기대값은 옮겨 적는 순간 원본과 갈라진다.</p>
     * <p>DOM is in the JDK, so no new dependency. What matters is that the contract is <b>parsed</b>:
     * hand-transcribed expectations diverge from the original the moment they are written.</p>
     *
     * // req: FR-ATC-001
     */
    private record Contract(String id, String version, String hash,
                            List<String> topLevelIds, Map<String, Integer> topLevelLengths,
                            Map<String, List<String>> subRules,
                            Map<String, Map<String, Integer>> subRuleLengths) {

        static Contract load(String resource) {
            try (InputStream in = ContractConformanceTest.class.getResourceAsStream(resource)) {
                assertThat(in).as("계약 리소스 / contract resource " + resource).isNotNull();
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                Document document = factory.newDocumentBuilder().parse(in);

                Element rule = (Element) document.getElementsByTagName("rule").item(0);
                Element inBlock = (Element) rule.getElementsByTagName("in").item(0);

                List<String> ids = new ArrayList<>();
                Map<String, Integer> lengths = new LinkedHashMap<>();
                collectItems(inBlock, ids, lengths);

                Map<String, List<String>> subs = new LinkedHashMap<>();
                Map<String, Map<String, Integer>> subLengths = new LinkedHashMap<>();
                Element subRule = (Element) rule.getElementsByTagName("subRule").item(0);
                NodeList children = subRule.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    if (!(children.item(i) instanceof Element element)) {
                        continue;
                    }
                    String subId = element.getAttribute("id");
                    // rule_Sub_3 (단건) / rule_Sub_4 (다건) 은 id="BUTTON" 으로 button 과 같은 항목을
                    // 중복 선언한 계약 자체의 잔재다. 대문자 키는 무시한다.
                    // The BUTTON sub-rule duplicates button; it is an artefact of the contract itself.
                    // Upper-case keys are ignored.
                    if (subId.equals(subId.toUpperCase()) && !subId.equals(subId.toLowerCase())) {
                        continue;
                    }
                    List<String> subIds = new ArrayList<>();
                    Map<String, Integer> subLen = new LinkedHashMap<>();
                    collectItems(element, subIds, subLen);
                    subs.put(subId, subIds);
                    subLengths.put(subId, subLen);
                }

                return new Contract(
                        text(document, "id"), text(document, "version"), text(document, "hash"),
                        ids, lengths, subs, subLengths);
            } catch (Exception e) {
                throw new IllegalStateException("failed to read contract " + resource, e);
            }
        }

        private static void collectItems(Element parent, List<String> ids, Map<String, Integer> lengths) {
            NodeList items = parent.getElementsByTagName("item");
            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);
                String id = item.getAttribute("id");
                ids.add(id);
                String length = item.getAttribute("length");
                if (!length.isBlank()) {
                    lengths.put(id, Integer.parseInt(length));
                }
            }
        }

        private static String text(Document document, String tag) {
            return document.getElementsByTagName(tag).item(0).getTextContent().trim();
        }

        List<String> subRuleIds(String name) {
            return subRules.get(name);
        }

        Map<String, Integer> subRuleLengths(String name) {
            return subRuleLengths.get(name);
        }
    }
}
