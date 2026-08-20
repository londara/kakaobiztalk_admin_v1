package com.webcash.iris.biztalk.domain;

/**
 * 톡결과 / 문자결과 — 코드와 그 해석. / A talk or SMS result: the code and its interpretation.
 *
 * <h2>레거시가 이 값을 잃어버린 두 가지 방식 / two ways the legacy lost this value</h2>
 *
 * <p><b>첫째, NULL 전파.</b> 화면 31 은 결과를 이렇게 만들었다:</p>
 * <pre>RSLT || '(' || (SELECT ERR_MSG FROM KKB_ERRCD_INFO …) || ')'</pre>
 * <p>{@code KKB_ERRCD_INFO} 에 없는 코드는 서브쿼리가 NULL 을 반환하고, SQL 에서 NULL 과의
 * 연결은 전체를 NULL 로 만든다. 즉 <b>인식되지 않는 실패 코드일 때 화면이 비었다</b> —
 * 운영자가 그 값을 가장 필요로 하는 정확히 그 순간에(D-T20).</p>
 * <p><b>First, NULL propagation.</b> Screen 31 built the result as
 * {@code RSLT || '(' || (SELECT ERR_MSG …) || ')'}. A code absent from {@code KKB_ERRCD_INFO} makes the
 * subquery NULL, and concatenating with NULL in SQL nulls the whole expression. So <b>the field went
 * blank precisely for unrecognised failure codes</b> — the moment an operator most needs the value
 * (D-T20).</p>
 *
 * <p><b>둘째, 삼값 논리.</b> 화면 32 의 톡결과=실패 필터는 {@code AND RSLT != '0'} 이었다.
 * SQL 에서 {@code NULL != '0'} 은 참이 아니라 UNKNOWN 이므로 <b>아직 결과가 오지 않은 행이
 * 성공에도 실패에도 나타나지 않았다</b>(D-T22).</p>
 * <p><b>Second, three-valued logic.</b> Screen 32's 톡결과=실패 filter was {@code AND RSLT != '0'}, and
 * {@code NULL != '0'} is UNKNOWN rather than true, so <b>a row with no result yet appeared under neither
 * 성공 nor 실패</b> (D-T22).</p>
 *
 * <p>두 결함은 같은 원인의 두 얼굴이다: <b>"결과 없음"이 하나의 상태로 취급되지 않았다.</b>
 * 이 타입이 그것을 세 상태로 명시한다 — 성공, 실패, 미수신.</p>
 * <p>Both are one cause seen twice: <b>"no result yet" was never treated as a state.</b> This type makes it
 * one of three explicit states — success, failure, not-yet-received.</p>
 *
 * // source: IDO.KKO_MSG_L002 — RSLT || '(' || (SELECT ERR_MSG …) || ')'
 * // source: biztalk_admin_32_l001_act.jsp — dynamic.addNotBlankParameter("0", …, "AND RSLT !=?")
 * // req: FR-TLKD-006, FR-TLKM-005, NFR-USE-T01
 */
public record TalkResult(String code, String description, Outcome outcome) {

    /** 성공을 뜻하는 톡결과 코드. / The talk-result code meaning success. */
    public static final String TALK_SUCCESS = "0";

    /** 성공을 뜻하는 문자결과 코드. / The SMS-result code meaning success. */
    public static final String SMS_SUCCESS = "0";

    /** 미발송을 뜻하는 문자결과 코드. / The SMS-result code meaning not dispatched. */
    public static final String SMS_NOT_SENT = "00";

    /** 코드가 사전에 없을 때의 표식. / The marker for a code absent from the dictionary. */
    public static final String UNKNOWN_CODE_MARKER = "미등록 코드";

    /** 결과 구분. / The result classification. */
    public enum Outcome {
        /** 성공 / succeeded. */
        SUCCESS("성공"),
        /** 실패 / failed. */
        FAILURE("실패"),
        /**
         * 미수신 — 아직 결과가 도착하지 않았다. / Not received: no result has arrived yet.
         *
         * <p>레거시에는 이 상태가 없었다. 그래서 이런 행이 성공 필터에도 실패 필터에도
         * 걸리지 않고 조용히 사라졌다(D-T22).</p>
         * <p>The legacy had no such state, so these rows fell through both filters and vanished
         * silently (D-T22).</p>
         */
        PENDING("미수신");

        private final String label;

        Outcome(String label) {
            this.label = label;
        }

        /**
         * 화면 표시 라벨을 반환한다. / Returns the display label.
         *
         * @return 라벨 / the label
         */
        public String label() {
            return label;
        }
    }

    /**
     * 톡결과를 해석한다. / Interprets a talk result.
     *
     * @param code        {@code RSLT} 원값. null 이면 미수신 / the raw code, null meaning pending
     * @param description 사전에서 찾은 설명. 없으면 null / the dictionary description, null when absent
     * @return 해석된 결과 / the interpreted result
     */
    // req: FR-TLKD-006, FR-TLKM-005
    public static TalkResult ofTalk(String code, String description) {
        if (isAbsent(code)) {
            return new TalkResult(null, null, Outcome.PENDING);
        }
        Outcome outcome = TALK_SUCCESS.equals(code.trim()) ? Outcome.SUCCESS : Outcome.FAILURE;
        return new TalkResult(code.trim(), blankToNull(description), outcome);
    }

    /**
     * 문자결과를 해석한다. / Interprets an SMS result.
     *
     * <p>{@code 00} 은 <b>실패가 아니라 미발송</b>이다 — 알림톡이 성공했으므로 문자로 대체
     * 발송할 필요가 없었다는 뜻이다. 레거시 화면 32 의 렌더러는 이것을 {@code -(00)} 으로
     * 표시했으나 필터는 {@code MSG_RSLT NOT IN ('0','00')} 로 실패에서 제외했다 — 두 곳의
     * 판단이 일치한 드문 경우이므로 그대로 보존한다.</p>
     * <p>{@code 00} means <b>not dispatched rather than failed</b>: the 알림톡 succeeded, so no SMS
     * fallback was needed. The legacy screen-32 renderer showed it as {@code -(00)} and its filter
     * excluded it from failure with {@code MSG_RSLT NOT IN ('0','00')} — a rare case where the two
     * agreed, so the behaviour is preserved.</p>
     *
     * @param code        {@code MSG_RSLT} 원값 / the raw code
     * @param description 사전에서 찾은 설명 / the dictionary description
     * @return 해석된 결과 / the interpreted result
     */
    // req: FR-TLKD-006, FR-TLKM-005
    public static TalkResult ofSms(String code, String description) {
        if (isAbsent(code)) {
            return new TalkResult(null, null, Outcome.PENDING);
        }
        String trimmed = code.trim();
        if (SMS_NOT_SENT.equals(trimmed)) {
            // 미발송은 미수신과 다르다 — 결과가 아직 없는 것이 아니라 보낼 필요가 없었다.
            // 그러나 사용자 관점에서 둘 다 "실패가 아니다" 이므로 같은 구분에 넣는다.
            // Not-dispatched differs from not-received — the result is not pending, it was never needed.
            // From the user's point of view both are "not a failure", so they share the classification.
            return new TalkResult(trimmed, "미발송", Outcome.PENDING);
        }
        Outcome outcome = SMS_SUCCESS.equals(trimmed) ? Outcome.SUCCESS : Outcome.FAILURE;
        return new TalkResult(trimmed, blankToNull(description), outcome);
    }

    /**
     * 화면에 표시할 문자열을 반환한다. / Returns the string to display.
     *
     * <p>규칙은 하나다: <b>코드는 항상 보인다.</b> 설명이 있으면 덧붙이고, 없으면 미등록임을
     * 밝힌다. 어떤 경로로도 빈 문자열이 나오지 않는다 — 그것이 D-T20 이었다.</p>
     * <p>One rule: <b>the code is always visible.</b> A description is appended when known and marked as
     * unregistered when not. No path produces an empty string — that was D-T20.</p>
     *
     * @return 표시 문자열 / the display string
     */
    // req: FR-TLKM-005, NFR-USE-T01
    public String display() {
        if (outcome == Outcome.PENDING && code == null) {
            return Outcome.PENDING.label();
        }
        String text = (description == null) ? UNKNOWN_CODE_MARKER : description;
        return outcome.label() + " " + code + " (" + text + ")";
    }

    /**
     * 결과가 아직 도착하지 않았는지 반환한다. / Whether no result has arrived yet.
     *
     * @return 미수신이면 true / true when pending
     */
    // req: FR-TLKD-006
    public boolean pending() {
        return outcome == Outcome.PENDING;
    }

    private static boolean isAbsent(String code) {
        return code == null || code.isBlank();
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
