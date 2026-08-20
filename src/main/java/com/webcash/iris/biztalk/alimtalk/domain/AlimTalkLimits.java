package com.webcash.iris.biztalk.alimtalk.domain;

/**
 * 알림톡 요청 필드의 유효 길이 한계. / Effective length bounds for AlimTalk request fields.
 *
 * <p>모든 값은 <b>한 곳에서만</b> 정의된다. 레거시가 열두 개의 길이 제약을 어디에도 적지 않은
 * 것(D-A7)만이 문제가 아니었다 — 같은 숫자를 여러 곳에 손으로 옮겨 적는 방식 자체가 그 결함을
 * 만들어낸 조건이다. 애노테이션마다 상수를 베껴 넣는 대신, 검증은 이 클래스만 참조한다.</p>
 * <p>Every bound is defined in <b>one</b> place. The legacy's problem was not only that it wrote
 * none of the contract's twelve length constraints anywhere (D-A7) — transcribing the same number
 * into several places is the condition that produced the defect. Validation reads this class
 * rather than repeating the constants.</p>
 *
 * <h2>두 한계의 조정 / reconciling two sets of bounds</h2>
 * <p>Skill 2 에서 PM 은 "카카오 공개 한계를 따른다"(AMB-A02)고 결정했으나, 그 결정은 코드에
 * 한계가 <b>없다</b>는 전제 위에 있었다. 실제로는 {@code IMO.ADV_KKO_AT_SEND} 가 자체 한계를
 * 선언하고 있고, 두 값은 <b>양방향으로</b> 어긋난다 — {@code msg} 는 계약 4000 대 카카오 약
 * 1000, {@code button.name} 은 계약 28 대 카카오 약 14. 따라서 유효 한계는
 * <b>{@code min(계약, 업무)}</b> 이며, 계약 길이는 넘을 수 없다(CONST-DATA-A02, CONFLICT-A02).</p>
 * <p>PM ruled "Kakao's published limits govern" (AMB-A02), but on the premise that the code
 * carried <b>no</b> limits. It does: the IMO contract declares its own, and the two disagree
 * <b>in both directions</b>. The effective bound is therefore <b>{@code min(contract, business)}</b>,
 * with the contract length inviolable (CONST-DATA-A02, CONFLICT-A02).</p>
 *
 * <table>
 *   <caption>필드별 한계 / bounds per field</caption>
 *   <tr><th>필드 / field</th><th>계약 / contract</th><th>업무 / business</th><th>유효 / effective</th></tr>
 *   <tr><td>{@code is_cd}</td><td>6</td><td>—</td><td>6</td></tr>
 *   <tr><td>{@code tran_id}</td><td>10</td><td>—</td><td>10</td></tr>
 *   <tr><td>{@code sender_number}</td><td>24</td><td>—</td><td>24</td></tr>
 *   <tr><td>{@code msg}</td><td>4000</td><td>1000 †</td><td><b>1000</b></td></tr>
 *   <tr><td>{@code template_title}</td><td>200</td><td>50 †</td><td><b>50</b></td></tr>
 *   <tr><td>{@code button.name}</td><td>28</td><td>14 †</td><td><b>14</b></td></tr>
 *   <tr><td>{@code failback_data.subject}</td><td>50</td><td>—</td><td>50</td></tr>
 * </table>
 * <p>† {@code [ASSUMED-KAKAO-SPEC]} — G1 확인 대기(RISK-A09). 이 값들이 한 클래스에 모여 있는
 * 이유가 여기에 있다: 결정이 바뀌면 파일 하나와 테스트 데이터만 고치면 된다.</p>
 * <p>† {@code [ASSUMED-KAKAO-SPEC]} — pending G1 confirmation (RISK-A09). This is why the values
 * live in one class: a changed ruling is a one-file edit, not a code change.</p>
 *
 * // source: IMO.ADV_KKO_AT_SEND.xml, IMO.ADV_KKO_AT_SEND_M.xml — rule/in item length attributes
 * // req: FR-ATC-005, CONST-DATA-A02
 */
public final class AlimTalkLimits {

    // ---------------------------------------------------------------------
    // 계약 선언 길이 / lengths declared by the IMO contract
    // ContractConformanceTest 가 XML 과 아래 값의 일치를 검증한다.
    // ContractConformanceTest asserts these against the XML.
    // ---------------------------------------------------------------------

    /** {@code is_cd} 이용기관코드 / institution code. // source: contract length=6 */
    public static final int CONTRACT_IS_CD = 6;

    /** {@code tran_id} 거래고유번호 / transaction id. // source: contract length=10 */
    public static final int CONTRACT_TRAN_ID = 10;

    /** {@code sender_number} 콜백송신자번호 / caller ID. // source: contract length=24 */
    public static final int CONTRACT_SENDER_NUMBER = 24;

    /** {@code receiver_number} 수신폰번호 / recipients. // source: contract length=20000 */
    public static final int CONTRACT_RECEIVER_NUMBER = 20000;

    /** {@code reqdate} 발송일시 {@code yyyyMMddHHmmss}. // source: contract length=14 */
    public static final int CONTRACT_REQDATE = 14;

    /** {@code msg} 발송메시지 / message body. // source: contract length=4000 */
    public static final int CONTRACT_MSG = 4000;

    /** {@code sender_key} 프로파일키 / vendor profile key. // source: contract length=100 */
    public static final int CONTRACT_SENDER_KEY = 100;

    /** {@code template_code} 템플릿코드 / template code. // source: contract length=30 */
    public static final int CONTRACT_TEMPLATE_CODE = 30;

    /** {@code template_title} 강조표기제목 / emphasis title. // source: contract length=200 */
    public static final int CONTRACT_TEMPLATE_TITLE = 200;

    /** {@code button.name} 버튼제목 / button label. // source: contract length=28 */
    public static final int CONTRACT_BUTTON_NAME = 28;

    /** {@code button.type} 버튼타입 / button type. // source: contract length=2 */
    public static final int CONTRACT_BUTTON_TYPE = 2;

    /** 버튼 URL·스킴 / button URL and scheme fields. // source: contract length=240 */
    public static final int CONTRACT_BUTTON_LINK = 240;

    /**
     * {@code failback_data.type} 의 <b>선언된</b> 길이 — 계약이 틀린 자리다.
     * The <b>declared</b> length of {@code failback_data.type} — where the contract is wrong.
     *
     * <p><b>D-A36 (Sprint A1 발견).</b> 계약의 {@code failback_data} 하위 규칙은 {@code button}
     * 하위 규칙의 {@code type} 항목을 <b>그대로 복사</b>했다 — {@code length="2"} 뿐 아니라
     * {@code name="버튼타입"}(버튼 타입!)까지 동일하다. 그러나 대체 전송 유형의 유효 값은
     * {@code SMS}·{@code LMS}·{@code MMS} 로 <b>모두 3자</b>다. 선언된 2자를 그대로 강제하면
     * <b>유효한 대체 전송이 전부 거절된다.</b></p>
     * <p><b>D-A36, found in Sprint A1.</b> The contract's {@code failback_data} sub-rule is a
     * <b>verbatim copy</b> of the {@code button} sub-rule's {@code type} item — not only
     * {@code length="2"} but also {@code name="버튼타입"} ("button type"). Yet the valid fallback types are
     * {@code SMS}, {@code LMS} and {@code MMS} — <b>all three characters</b>. Enforcing the declared 2
     * would <b>reject every valid fallback.</b></p>
     *
     * <p>이것이 CONST-DATA-A02("계약 길이는 넘을 수 없다")의 <b>유일한 예외</b>다. 그 규칙은
     * 계약이 값보다 좁을 때 계약을 따르라는 뜻이었지만, 여기서는 계약이 <b>자신이 선언한 값</b>
     * 보다 좁다. 계약을 따르면 기능이 동작하지 않는다.</p>
     * <p>This is the <b>one exception</b> to CONST-DATA-A02 ("never exceed the contract length"). That rule
     * meant "the contract wins when it is narrower than the business limit"; here the contract is narrower
     * than <b>its own declared values</b>. Following it would stop the feature working.</p>
     *
     * <p><b>운영 함의 — 확인 필요.</b> 화면 50 은 {@code failbackObj.put("type", "SMS")} 로 3자를
     * 넣는다. jex IMO 계층이 {@code padding=" "} 속성대로 선언 길이에 맞춰 고정폭 처리를 한다면
     * 이 값은 {@code "SM"} 으로 <b>잘려 전송되어 왔을 수 있다</b> — 그렇다면 D-A1(잘못된 키 이름)과
     * <b>독립적으로</b> 대체 전송이 한 번도 동작하지 않았다는 뜻이다. 소스만으로는 확정할 수 없고
     * spike A1-02 의 {@code RSMS} 캡처가 답한다. RISK-A02 에 연결한다.</p>
     * <p><b>Operational implication — needs confirmation.</b> Screen 50 puts three characters via
     * {@code failbackObj.put("type", "SMS")}. If the jex IMO layer applies fixed-width handling to the
     * declared length (as {@code padding=" "} suggests), this may <b>have been transmitted truncated to
     * {@code "SM"} all along</b> — which would mean the fallback has never worked,
     * <b>independently of D-A1</b>. Source cannot settle it; spike A1-02's {@code RSMS} capture answers it.
     * Linked to RISK-A02.</p>
     *
     * // source: IMO.ADV_KKO_AT_SEND.xml rule_Sub_2 — item id="type" length="2" name="버튼타입"
     * // req: FR-ATC-002, FR-ATC-005, CONST-DATA-A02
     */
    public static final int CONTRACT_FAILBACK_TYPE_DECLARED = 2;

    /**
     * {@code failback_data.type} 에 실제로 필요한 길이. / The length {@code failback_data.type} actually needs.
     *
     * <p>{@code SMS}·{@code LMS}·{@code MMS} 는 3자다. D-A36 에 따라 선언값 2 대신 이 값을 쓴다.</p>
     * <p>{@code SMS}, {@code LMS} and {@code MMS} are three characters. Per D-A36 this is used instead of
     * the declared 2.</p>
     *
     * // req: FR-ATC-002, FR-ATC-005
     */
    public static final int CONTRACT_FAILBACK_TYPE = 3;

    /** {@code failback_data.subject} 제목 / fallback subject. // source: contract length=50 */
    public static final int CONTRACT_FAILBACK_SUBJECT = 50;

    /** {@code failback_data.msg} / fallback message. // source: contract length=4000 */
    public static final int CONTRACT_FAILBACK_MSG = 4000;

    /** {@code failback_data.img_id} 이미지ID / fallback image id. // source: contract length=256 */
    public static final int CONTRACT_FAILBACK_IMG_ID = 256;

    /** {@code msg_data.order} 순번 / batch item order. // source: _M contract length=6 */
    public static final int CONTRACT_ORDER = 6;

    // ---------------------------------------------------------------------
    // 업무 한계 / business limits — [ASSUMED-KAKAO-SPEC], RISK-A09
    // ---------------------------------------------------------------------

    /** 알림톡 본문 문자 수 / AlimTalk body characters. {@code [ASSUMED-KAKAO-SPEC]} */
    public static final int BUSINESS_MSG = 1000;

    /** 강조표기 제목 문자 수 / emphasis title characters. {@code [ASSUMED-KAKAO-SPEC]} */
    public static final int BUSINESS_TEMPLATE_TITLE = 50;

    /** 버튼명 문자 수 / button label characters. {@code [ASSUMED-KAKAO-SPEC]} */
    public static final int BUSINESS_BUTTON_NAME = 14;

    /** 메시지당 버튼 개수 / buttons per message. {@code [ASSUMED-KAKAO-SPEC]} */
    public static final int BUSINESS_MAX_BUTTONS = 5;

    /** SMS 대체 전송 바이트 / SMS fallback bytes. {@code [ASSUMED-KAKAO-SPEC]} */
    public static final int BUSINESS_SMS_BYTES = 90;

    /** LMS 대체 전송 바이트 / LMS fallback bytes. {@code [ASSUMED-KAKAO-SPEC]} */
    public static final int BUSINESS_LMS_BYTES = 2000;

    // ---------------------------------------------------------------------
    // 유효 한계 / effective bounds = min(contract, business)
    // ---------------------------------------------------------------------

    /** 본문 유효 한계 / effective message bound. */
    public static final int MSG = Math.min(CONTRACT_MSG, BUSINESS_MSG);

    /** 강조표기 제목 유효 한계 / effective emphasis-title bound. */
    public static final int TEMPLATE_TITLE = Math.min(CONTRACT_TEMPLATE_TITLE, BUSINESS_TEMPLATE_TITLE);

    /** 버튼명 유효 한계 / effective button-label bound. */
    public static final int BUTTON_NAME = Math.min(CONTRACT_BUTTON_NAME, BUSINESS_BUTTON_NAME);

    private AlimTalkLimits() {
    }

    /**
     * 값이 유효 한계 안에 있는지 판정한다. / Reports whether a value is within an effective bound.
     *
     * <p>{@code null} 은 통과시킨다 — 필수 여부는 길이와 별개의 규칙이며, 두 판정을 섞으면
     * 오류 메시지가 어느 규칙을 위반했는지 말할 수 없게 된다(NFR-USE-A03).</p>
     * <p>{@code null} passes: presence is a separate rule from length, and merging the two leaves
     * the error message unable to name which rule was broken (NFR-USE-A03).</p>
     *
     * @param value 검사할 값 / the value to check
     * @param bound 유효 한계 / the effective bound
     * @return 한계 이내이거나 {@code null} 이면 {@code true} / {@code true} when within bound or null
     *
     * // req: FR-ATC-005, NFR-USE-A03
     */
    public static boolean within(String value, int bound) {
        return value == null || value.length() <= bound;
    }
}
